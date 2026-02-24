# YAWL Web Applications

**Artifact:** `org.yawlfoundation:yawl-webapps:6.0.0-Alpha` | `packaging: pom`

## Module Purpose

Aggregator module for all WAR-packaged YAWL web applications. This module serves as the parent for all YAWL web applications, providing common Maven WAR plugin configuration and dependency management for Jakarta EE servlet containers.

The primary web application (`yawl-engine-webapp`) packages the YAWL workflow engine, authentication, and resourcing services into a single deployable WAR that exposes REST APIs for workflow interaction.

## Web Application Architecture

### Component Layers

```
┌─────────────────────────────────────────────────────────┐
│                     Web Layer                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │  Jersey    │  │ Authentication│ │   Audit     │    │
│  │  JAX-RS    │  │    Filter    │ │   Logger    │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                    Engine Layer                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   YEngine   │  │YStatelessEngine│ │  Resourcing │    │
│  │  (Stateful) │  │ (Stateless) │ │   Service   │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   Persistence Layer                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   H2/PostgreSQL│ │   Log4j 2   │ │   Hibernate │    │
│  │   Database  │  │   Logging   │ │    ORM      │    │
│  └─────────────┘  └─────────────┘  └─────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Deployment Architecture

The WAR file (`yawl.war`) is a self-contained web application that includes:

- **Servlet Container Integration**: Jakarta EE compliant (Tomcat, Jetty, WildFly)
- **REST Endpoints**: Exposed via Jersey JAX-RS implementation
- **Engine Services**: Core workflow engine with stateful and stateless variants
- **Authentication**: JWT-based session management and authorization
- **Resource Management**: Participant queues and role-based access control
- **Logging**: Comprehensive audit logging with Log4j 2

## Key REST Endpoints

### Interface A (Specification Management)
- **Base Path**: `/yawl/ia`
- **Purpose**: Specification creation, validation, and versioning
- **Key Operations**:
  - `GET /specifications` - List all specifications
  - `POST /specifications` - Upload new specification
  - `PUT /specifications/{id}/version` - Create new version

### Interface B (Client API)
- **Base Path**: `/yawl/ib`
- **Purpose**: Work item lifecycle management and case data access
- **Key Operations**:
  - `POST /workitems/checkout` - Claim work items
  - `POST /workitems/checkin` - Complete work items
  - `GET /cases/{caseId}/data` - Access case data
  - `POST /cases/{caseId}/data` - Update case data

### Interface E (Event/Log Operations)
- **Base Path**: `/yawl/ie`
- **Purpose**: Process monitoring and event querying
- **Key Operations**:
  - `GET /specifications` - Get specifications with logged data
  - `GET /cases/{caseId}/log` - Case execution log
  - `GET /events` - Query YAWL engine events

### Interface X (Extended Operations)
- **Base Path**: `/yawl/ix`
- **Purpose**: Advanced workflow operations and exception handling
- **Key Operations**:
  - `POST /workitems/{id}/cancel` - Cancel work item
  - `POST /workitems/{id}/suspend` - Suspend work item
  - `POST /workitems/{id}/resume` - Resume work item

### Security Endpoints
- **Base Path**: `/yawl/security`
- **Purpose**: Authentication and session management
- **Key Operations**:
  - `POST /login` - JWT token acquisition
  - `POST /logout` - Session invalidation
  - `GET /validate` - Token validation

## Module Dependencies

### Internal Dependencies

| Module | Role in WAR | Maven Scope |
|--------|-------------|-------------|
| `yawl-engine` | Core workflow engine | compile |
| `yawl-authentication` | Session management, JWT | compile |
| `yawl-resourcing` | Resource service, queues | compile |

### External Dependencies

| Artifact | Scope | Purpose |
|----------|-------|---------|
| `jakarta.servlet-api` | provided | Servlet API (container-supplied) |
| `jakarta.ws.rs-api` | provided | JAX-RS API (container-supplied) |
| `jersey-container-servlet` | compile | Jersey JAX-RS implementation |
| `jersey-server` | compile | Jersey server components |
| `jersey-hk2` | runtime | HK2 dependency injection |
| `jackson-databind` | runtime | JSON serialization |

## Deployment Configuration

### Servlet Container Requirements

- **Jakarta EE 9+** compliant container
- **Java 21+** runtime
- **Minimum Memory**: 2GB heap (4GB recommended for production)
- **Database**: H2 (development), PostgreSQL/MySQL (production)

### Build and Package

```bash
# Build the WAR
mvn -pl yawl-utilities,yawl-elements,yawl-authentication,yawl-engine,yawl-resourcing,\
yawl-webapps,yawl-webapps/yawl-engine-webapp clean package

# Deploy to Tomcat
cp yawl-webapps/yawl-engine-webapp/target/yawl.war $CATALINA_HOME/webapps/

# Deploy to Jetty
cp yawl-webapps/yawl-engine-webapp/target/yawl.war $JETTY_HOME/webapps/
```

### Configuration Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `EnablePersistence` | `true` | Enable/disable database persistence |
| `DatabaseType` | `H2` | Database driver (H2, PostgreSQL, MySQL) |
| `ConnectionURL` | - | JDBC connection string |
| `DatabaseUser` | `sa` | Database username |
| `DatabasePassword` | - | Database password |

### Configuration File Example (`web.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_5_0.xsd"
         version="5.0">

    <!-- Servlet context parameters -->
    <context-param>
        <param-name>EnablePersistence</param-name>
        <param-value>true</param-value>
    </context-param>

    <!-- Jersey Servlet -->
    <servlet>
        <servlet-name>Jersey</servlet-name>
        <servlet-class>org.glassfish.jersey.servlet.ServletContainer</servlet-class>
        <init-param>
            <param-name>jersey.config.server.provider.packages</param-name>
            <param-value>org.yawlfoundation.yawl.engine.interfce.rest</param-value>
        </init-param>
        <load-on-startup>1</load-on-startup>
    </servlet>

    <servlet-mapping>
        <servlet-name>Jersey</servlet-name>
        <url-pattern>/yawl/*</url-pattern>
    </servlet-mapping>

    <!-- Character encoding filter -->
    <filter>
        <filter-name>encoding</filter-name>
        <filter-class>org.yawlfoundation.yawl.engine.interfce.rest.CharacterEncodingFilter</filter-class>
        <init-param>
            <param-name>encoding</param-name>
            <param-value>UTF-8</param-value>
        </init-param>
    </filter>

    <filter-mapping>
        <filter-name>encoding</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>
</web-app>
```

## Security Configuration

### JWT Authentication Flow

1. **Login**: `POST /yawl/security/login` with credentials
2. **Token**: Receive JWT token in response
3. **Access**: Include token in Authorization header for protected endpoints
4. **Validation**: Token validated on each request (exp, signature)

### Security Filters

- **JWT Filter**: Validates JWT tokens for protected endpoints
- **Character Encoding**: Ensures UTF-8 encoding for all requests
- **CORS**: Configured for cross-origin requests (if needed)

## Logging Configuration

The WAR includes Log4j 2 configuration with the following loggers:

| Logger | Level | Purpose |
|--------|-------|---------|
| `org.yawlfoundation.yawl.engine` | INFO | Engine operations |
| `org.yawlfoundation.yawl.auth` | INFO | Authentication events |
| `org.yawlfoundation.yawl.resourcing` | INFO | Resource allocation |
| `org.yawlfoundation.yawl.audit` | DEBUG | Audit trails |

## Testing

### Integration Tests

The module is tested via integration tests in `yawl-integration`:

- `EndToEndWorkflowExecutionTest`: 4 tests for full case execution
- `MultiModuleIntegrationTest`: 6 tests for cross-module assertions

### Test Coverage

Current coverage includes:
- ✅ Full workflow execution through deployed engine
- ✅ Authentication and session management
- ❌ HTTP endpoint contract testing (status codes, responses)
- ❌ Authentication filter chain isolation
- ❌ Jersey resource registration verification

## Performance Considerations

### Memory Usage

- **Engine Heap**: 512MB-1GB (depending on workflow complexity)
- **Database**: 256MB connection pool
- **Logging**: Async logging with 100ms buffer

### Concurrency

- **Virtual Threads**: Enabled for task execution (Java 21+)
- **HTTP Workers**: Configured based on container settings
- **Database Connections**: HikariCP connection pool

## Future Enhancements

- **OpenAPI/Swagger**: Auto-generated API documentation
- **Health Probes**: `/yawl/actuator/health` endpoints for Kubernetes
- **Docker Images**: Multi-arch container images
- **TLS 1.3**: Enforced TLS 1.3 for production deployments
- **Graceful Shutdown**: Case drain during container shutdown

## References

- [YAWL Engine Documentation](../../docs/explanation/dual-engine-architecture.md)
- [Interface Architecture](../../docs/explanation/interface-architecture.md)
- [YAWL REST API Examples](../../docs/tutorials/05-call-yawl-rest-api.md)
- [YAWL Deployment Guide](../../docs/deployment/deployment-guide.md)