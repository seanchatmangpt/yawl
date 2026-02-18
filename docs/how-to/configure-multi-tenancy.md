# How to Configure Multi-Tenancy

## Prerequisites

- YAWL v6.0 or later (schema-based multi-tenancy was introduced in ADR-015 for v6.0)
- PostgreSQL 13+ as the engine database (schema-based isolation requires PostgreSQL's
  per-schema DDL support; MySQL requires the alternative instance-per-tenant approach)
- Flyway applied to the database (`spring.flyway.enabled=true`)
- Understanding of the isolation model you need (schema-per-tenant vs. instance-per-tenant)

## Isolation Models

YAWL v6.0 supports two multi-tenancy patterns. Choose based on your security and
operational requirements:

| Model | Isolation | Overhead | Use when |
|---|---|---|---|
| Schema-per-tenant (PostgreSQL) | Strong logical isolation | Single deployment, multiple schemas | SaaS with moderate tenant count (<100) |
| Instance-per-tenant | Full process isolation | Separate JVM, DB, and port per tenant | Regulatory/compliance requirements, unlimited tenants |

The remainder of this guide covers both models.

---

## Model A: Schema-Per-Tenant (PostgreSQL, v6.0+)

### 1. Create a Tenant Schema

Each tenant gets a dedicated PostgreSQL schema that contains all YAWL tables:

```sql
-- Run as a PostgreSQL superuser or DBA
CREATE SCHEMA tenant_acme;
CREATE SCHEMA tenant_globex;

-- Grant the YAWL application user access to each schema
GRANT ALL PRIVILEGES ON SCHEMA tenant_acme TO yawl_app;
GRANT ALL PRIVILEGES ON SCHEMA tenant_globex TO yawl_app;
```

The `public` schema holds shared tables: `yawl_users`, `yawl_tenants`, and Flyway's
`flyway_schema_history`.

### 2. Apply Schema Migrations to the Tenant Schema

Flyway applies baseline migrations per-schema. Set `defaultSchema` at migration time
to target the correct schema:

```bash
# Apply initial schema for tenant_acme
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/yawl \
  -Dflyway.user=postgres \
  -Dflyway.password=yawl \
  -Dflyway.defaultSchema=tenant_acme \
  -Dflyway.schemas=tenant_acme

# Apply for tenant_globex
mvn flyway:migrate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/yawl \
  -Dflyway.user=postgres \
  -Dflyway.password=yawl \
  -Dflyway.defaultSchema=tenant_globex \
  -Dflyway.schemas=tenant_globex
```

All migration scripts in `src/main/resources/db/migration/` are applied to each
schema independently. Adding a new tenant requires only running `flyway:migrate` with
the new schema name.

### 3. Register Tenants in the Shared Schema

Add each tenant to the shared `yawl_tenants` table so the `TenantSchemaResolver` can
map an incoming request to the correct schema:

```sql
INSERT INTO yawl_tenants (tenant_id, schema_name, display_name, active)
VALUES ('acme',   'tenant_acme',   'Acme Corp',  true),
       ('globex', 'tenant_globex', 'Globex Inc', true);
```

The `tenant_id` value is what arrives in the HTTP request (from a JWT claim, subdomain,
or header — see step 4).

### 4. Configure Tenant Resolution

`TenantSchemaResolver` (referenced in ADR-015) implements
`CurrentTenantIdentifierResolver<String>`. It reads the current tenant from
`TenantContext`, a thread-local (or `ScopedValue` in Java 25) set by an incoming
request filter:

```java
// Incoming request filter — sets tenant context from JWT sub-claim or X-Tenant-ID header
@Component
@Order(0)
public class TenantResolutionFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;

        String tenantId = extractTenantId(httpReq);
        if (tenantId == null || tenantId.isBlank()) {
            ((HttpServletResponse) response).sendError(400, "Tenant identity required");
            return;
        }

        TenantContext.setCurrentTenant(tenantId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();  // always clear after request completes
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        // Option 1: explicit header
        String header = request.getHeader("X-Tenant-ID");
        if (header != null) return header;

        // Option 2: subdomain (acme.yawl.example.com → "acme")
        String host = request.getServerName();
        int dot = host.indexOf('.');
        if (dot > 0) return host.substring(0, dot);

        return null;
    }
}
```

`TenantContext` is a simple holder:

```java
public final class TenantContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void setCurrentTenant(String tenantId) { CURRENT.set(tenantId); }
    public static Optional<String> getCurrentTenant()    { return Optional.ofNullable(CURRENT.get()); }
    public static void clear()                           { CURRENT.remove(); }
}
```

### 5. Configure Hibernate for Schema-Based Routing

In `application.properties`, enable multi-tenancy and point Hibernate at the resolver:

```properties
spring.jpa.properties.hibernate.multiTenancy=SCHEMA
spring.jpa.properties.hibernate.tenant_identifier_resolver=\
    org.yawlfoundation.yawl.persistence.TenantSchemaResolver
spring.jpa.properties.hibernate.multi_tenant_connection_provider=\
    org.yawlfoundation.yawl.persistence.SchemaBasedConnectionProvider
```

`SchemaBasedConnectionProvider` acquires a connection from HikariCP and calls
`SET search_path = <schema>` before handing it to Hibernate:

```java
public class SchemaBasedConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final HikariDataSource dataSource;

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection conn = dataSource.getConnection();
        conn.createStatement().execute("SET search_path = " + tenantIdentifier + ", public");
        return conn;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection)
            throws SQLException {
        connection.createStatement().execute("SET search_path = public");
        connection.close();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }
}
```

### 6. Add PostgreSQL Row-Level Security (defence in depth)

Schema isolation prevents accidental cross-tenant queries via the ORM. RLS adds a
database-enforced second layer, catching any direct SQL that bypasses the schema search
path:

```sql
-- In each tenant schema, enable RLS on the case table
ALTER TABLE tenant_acme.yawl_case ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON tenant_acme.yawl_case
    USING (tenant_id = current_setting('app.current_tenant'));

-- The application sets this session variable on every connection
-- (done inside SchemaBasedConnectionProvider.getConnection())
-- SET app.current_tenant = 'acme'
```

---

## Model B: Instance-Per-Tenant

YAWL does not have native process-level multi-tenancy. If schema isolation is
insufficient (regulatory isolation, full credential separation, or independent upgrade
schedules), run one complete YAWL deployment per tenant.

### 1. Assign Separate Ports and Database Names

| Tenant | Engine port | Resource service port | Database |
|---|---|---|---|
| acme | 8080 | 8180 | yawl_acme |
| globex | 8090 | 8190 | yawl_globex |

### 2. Configure Each Instance with a Tenant-Scoped Database

Copy `build/properties/hibernate.properties` for each tenant and set the connection URL:

```properties
# hibernate-acme.properties
hibernate.connection.url=jdbc:postgresql://localhost:5432/yawl_acme
hibernate.connection.username=yawl_acme
hibernate.connection.password=<acme-password>
```

Pass the properties file at startup:

```bash
java -Dyawl.hibernate.config=hibernate-acme.properties -jar yawl-engine.jar
```

### 3. Use a Reverse Proxy to Route Tenants

Route subdomain traffic to the correct engine instance via nginx:

```nginx
# Acme tenant
server {
    server_name acme.yawl.example.com;
    location / {
        proxy_pass http://localhost:8080;
    }
}

# Globex tenant
server {
    server_name globex.yawl.example.com;
    location / {
        proxy_pass http://localhost:8090;
    }
}
```

Each tenant has fully isolated processes, data, and credentials. Upgrades, backups, and
incident response are performed per tenant independently.

---

## Verify

**Schema-per-tenant:**

```bash
# Verify acme's schema contains YAWL tables
psql -U postgres -d yawl -c "\dt tenant_acme.*"
# Expected: list of yawl_case, yawl_work_item, etc. tables

# Launch a case for acme and confirm it lands in the correct schema
curl -H "X-Tenant-ID: acme" \
  "http://localhost:8080/yawl/ib?action=launchCase&specID=...&sessionHandle=..."

psql -U postgres -d yawl \
  -c "SELECT case_id FROM tenant_acme.yawl_case ORDER BY started_at DESC LIMIT 1;"
# Expect the case ID from the curl call — NOT visible in tenant_globex.yawl_case
```

**Instance-per-tenant:**

```bash
# Confirm each engine is responding independently
curl http://localhost:8080/yawl/ib?action=isRunning   # acme engine
curl http://localhost:8090/yawl/ib?action=isRunning   # globex engine
```

## Troubleshooting

**`IllegalStateException: No tenant context established for this request`**
The `TenantResolutionFilter` did not set `TenantContext` before the Hibernate session
opened. Ensure the filter's `@Order` value is lower than any filter that triggers a
database call. Also verify the `X-Tenant-ID` header or subdomain is present in every
request.

**Cross-tenant data visible in queries**
The `SET search_path` call must happen on every connection checkout, not once at pool
creation. Verify `SchemaBasedConnectionProvider.getConnection()` runs `SET search_path`
before returning the connection. Enable `hibernate.show_sql=true` and confirm the SQL
executes against the correct schema prefix.

**Flyway migration fails for second tenant schema**
Flyway maintains its history table per schema. If you run migration against a schema
that already has a different version, Flyway throws `FlywayException: Schema history
table schema mismatch`. Create each tenant schema fresh, or use `flyway:baseline` to
mark an existing schema at the correct version before migrating.

**Instance-per-tenant: wrong engine serves a request**
Check the reverse proxy routing and confirm each engine listens on the expected port.
The engine logs its bound port on startup: `YEngine started on port <n>`.
