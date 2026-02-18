# ADR-015: Persistence Layer Architecture for v6.0.0

## Status
**ACCEPTED**

## Context

YAWL's persistence layer uses Hibernate ORM to map engine objects (`YCase`, `YWorkItem`,
`YSpecification`, etc.) to relational tables. The v5.x persistence layer has accumulated
technical debt:

1. **Hibernate 5.x / `javax.persistence`**: migrated to Hibernate 6.6 / `jakarta.persistence`
   in v6.0 (see ADR-011), but the schema design predates modern Hibernate features
2. **No connection pooling**: `YPersistenceManager` creates connections per-request without
   a pool, causing exhaustion under load
3. **Monolithic schema**: all tables in a single schema, making multi-tenancy impossible
4. **No schema migration tooling**: DDL changes require manual SQL scripts with no
   versioning or rollback capability
5. **Session-per-request not enforced**: some code paths open Hibernate sessions and never
   close them, causing connection leaks
6. **No read replica support**: all queries hit the primary, even for reporting queries

For v6.0.0, the following additional requirements drive design changes:

- **Multi-tenancy**: a single YAWL deployment must support multiple isolated organisational units
- **Audit trail**: all state changes must be recorded with `who`, `when`, `what` for
  compliance (ISO 9001, SOX requirements from enterprise customers)
- **Schema migrations**: zero-downtime schema changes during rolling upgrades

## Decision

**YAWL v6.0.0 restructures the persistence layer around five principles:**
HikariCP connection pooling, Flyway schema migrations, Hibernate Envers auditing,
schema-based multi-tenancy, and read/write query separation.

### 1. Connection Pooling with HikariCP

HikariCP replaces the current connection-per-request pattern:

```xml
<!-- hibernate.cfg.xml (v6) -->
<property name="hibernate.connection.provider_class">
    org.hibernate.hikaricp.internal.HikariCPConnectionProvider
</property>
<property name="hibernate.hikari.maximumPoolSize">50</property>
<property name="hibernate.hikari.minimumIdle">10</property>
<property name="hibernate.hikari.idleTimeout">300000</property>
<property name="hibernate.hikari.connectionTimeout">30000</property>
<property name="hibernate.hikari.maxLifetime">1800000</property>
<property name="hibernate.hikari.leakDetectionThreshold">60000</property>
```

The `leakDetectionThreshold` of 60 seconds logs a warning if any connection is held
for longer than that, catching the session-leak bugs from v5.x.

### 2. Schema Migrations with Flyway

All DDL changes are managed by Flyway, applied automatically at engine startup:

```
src/main/resources/db/migration/
  V6__initial_schema.sql         ← baseline migration for v6.0.0
  V6_1__add_agent_binding.sql    ← agent task annotations table
  V6_2__add_audit_tables.sql     ← Envers audit tables
  V6_3__add_cluster_nodes.sql    ← cluster topology table
```

Migration naming convention: `V{major}_{minor}__{description}.sql`

Flyway is configured in `application.properties`:
```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=5.2
```

### 3. Audit Trail with Hibernate Envers

All mutable engine entities are audited using Hibernate Envers:

```java
@Entity
@Audited
@Table(name = "yawl_work_item")
public class YWorkItemEntity {
    @Id @GeneratedValue
    private Long id;

    @NotAudited  // current status is in main table; transitions in audit table
    private String status;

    @Audited     // data changes are tracked
    @Column(columnDefinition = "TEXT")
    private String dataPayload;
    // ...
}
```

The Envers audit tables record every state transition with:
- `REV`: revision number (global sequence)
- `REVTYPE`: 0=INSERT, 1=UPDATE, 2=DELETE
- `REVTSTMP`: timestamp (nanosecond precision via `@RevisionTimestamp`)
- `MODIFIED_BY`: session user ID (populated via `RevisionListener`)

### 4. Schema-Based Multi-Tenancy

Each tenant (organisational unit) gets its own PostgreSQL schema:

```
public schema: shared tables (users, tenants, schema_version)
tenant_acme schema: YAWL tables for Acme Corp
tenant_globex schema: YAWL tables for Globex Inc
```

Tenant resolution from the incoming request:

```java
// org.yawlfoundation.yawl.persistence.TenantSchemaResolver
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {
    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getCurrentTenant()
            .orElseThrow(() -> new IllegalStateException(
                "No tenant context established for this request"
            ));
    }
}
```

Single-tenant deployments use the `public` schema — multi-tenancy is opt-in.

### 5. Read/Write Query Separation

Reporting and audit queries are routed to a read replica:

```java
@Repository
public class WorkItemQueryRepository {

    @Transactional(readOnly = true)  // routes to read replica via AbstractRoutingDataSource
    public List<WorkItemRecord> findByCase(String caseId) {
        return entityManager.createSelectionQuery(
            "FROM YWorkItemEntity WHERE caseId = :caseId", YWorkItemEntity.class)
            .setParameter("caseId", caseId)
            .getResultList();
    }

    @Transactional  // routes to primary
    public void save(YWorkItemEntity item) {
        entityManager.persist(item);
    }
}
```

The `readOnly = true` transaction annotation signals Spring's `AbstractRoutingDataSource`
to direct the query to the configured read replica.

### Entity Model Changes

New entities in v6.0:

| Entity | Table | Purpose |
|--------|-------|---------|
| `YClusterNodeEntity` | `yawl_cluster_node` | Engine node registry (ADR-014) |
| `YAgentBindingEntity` | `yawl_agent_binding` | Agent task assignments |
| `YAuditRevision` | `revinfo` | Envers revision metadata |
| `YEventSubscriptionEntity` | `yawl_event_subscription` | Webhook subscriptions |

Deprecated entities removed in v6.0:

| Entity | Reason |
|--------|--------|
| `YSoapCodeletEntity` | SOAP codelets removed (see ADR-021) |
| `YLegacySessionEntity` | Replaced by JWT sessions (ADR-017) |

## Consequences

### Positive

1. HikariCP eliminates connection leak and exhaustion issues from v5.x
2. Flyway provides auditable, reversible schema evolution
3. Envers delivers compliance-grade audit trail with zero application code changes
4. Multi-tenancy enables SaaS deployment model
5. Read replica routing reduces load on primary for reporting queries

### Negative

1. Flyway requires baseline migration for existing databases (one-time migration step)
2. Envers doubles the number of rows written for audited entities (~10% write overhead)
3. Multi-tenancy complicates cross-tenant admin queries (require elevated role)
4. Read replica lag (typically <1s) means recently committed data may not be immediately visible on read queries

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Flyway migration failure during upgrade | LOW | HIGH | Migrations tested on production DB copy; rollback scripts for every migration |
| Envers performance impact under high write load | MEDIUM | MEDIUM | Disable per-entity audit via `@NotAudited` for non-compliance-critical entities |
| HikariCP pool exhaustion during connection surge | LOW | MEDIUM | Pool size tuned with `leakDetectionThreshold`; Micrometer pool metrics alerted |
| Schema isolation breach (tenant data leak) | LOW | CRITICAL | Row-level security as additional defence (PostgreSQL RLS policies) |

## Alternatives Considered

### Event Sourcing for Audit Trail
Full event sourcing would provide the audit trail without Envers overhead. Rejected for
v6.0: the migration cost from the current entity model is too high. Envers is a
well-understood, Hibernate-native approach with lower adoption risk. Event sourcing
is the v7.0 candidate architecture.

### Liquibase instead of Flyway
Both are mature schema migration tools. Flyway was selected over Liquibase because:
- SQL-native migrations (no XML/YAML indirection)
- Spring Boot autoconfiguration is simpler
- Smaller dependency footprint
- YAWL team has more Flyway experience

### Separate Audit Database
Routing all audit writes to a separate audit database was considered. Rejected because
it would require a two-phase commit protocol for atomicity (case state change + audit
record must be atomic). Envers writes in the same transaction as the main entity,
guaranteeing consistency.

## Related ADRs

- ADR-004: Spring Boot 3.4 + Java 25 (Spring Data, Spring Flyway autoconfiguration)
- ADR-007: Repository Pattern for Caching (repository pattern references)
- ADR-011: Jakarta EE Migration (Hibernate 6 + `jakarta.persistence`)
- ADR-014: Clustering (HikariCP pool shared across nodes via same DB)

## Implementation Notes

### Migration from v5.x Baseline

```sql
-- V6__initial_schema.sql (excerpt)
-- Baseline the existing v5.x schema, then apply additive changes

-- Add tenant_id column to all YAWL tables (nullable for single-tenant compat)
ALTER TABLE yawl_case ADD COLUMN tenant_id VARCHAR(255);
ALTER TABLE yawl_work_item ADD COLUMN tenant_id VARCHAR(255);

-- Create cluster node registry
CREATE TABLE yawl_cluster_node (
    node_id     VARCHAR(255) PRIMARY KEY,
    host        VARCHAR(255) NOT NULL,
    port        INTEGER NOT NULL,
    started_at  TIMESTAMP NOT NULL,
    last_seen   TIMESTAMP NOT NULL,
    status      VARCHAR(50) NOT NULL
);

-- Create event subscription table
CREATE TABLE yawl_event_subscription (
    id              UUID PRIMARY KEY,
    callback_url    VARCHAR(2048) NOT NULL,
    event_types     TEXT NOT NULL,  -- JSON array
    spec_identifier VARCHAR(255),
    tenant_id       VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL,
    last_delivery   TIMESTAMP
);
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** IN PROGRESS (v6.0.0)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version
