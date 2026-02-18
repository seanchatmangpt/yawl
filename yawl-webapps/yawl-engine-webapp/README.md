# yawl-engine-webapp

**Artifact:** `org.yawlfoundation:yawl-engine-webapp:6.0.0-Alpha` | `packaging: war`
**Parent:** `yawl-webapps`
**Output:** `yawl.war`

## Purpose

Primary deployable web artifact — packages the engine, authentication, and resourcing
modules into a single WAR file deployable on any Jakarta EE servlet container
(Apache Tomcat, Eclipse Jetty, WildFly, GlassFish, etc.).

Exposes the YAWL engine over HTTP via Jersey JAX-RS REST endpoints (Interface B, Interface E,
Interface X) for interaction with YAWL worklist handlers, editors, and external services.

## Internal Dependencies

| Module | Role in WAR |
|--------|-------------|
| `yawl-engine` | Core stateful workflow engine |
| `yawl-authentication` | Session management, JWT filter |
| `yawl-resourcing` | Resource service (participant queues, role management) |

## Key Third-Party Dependencies

| Artifact | Scope | Purpose |
|----------|-------|---------|
| `jakarta.servlet-api` | provided | Servlet API (supplied by container) |
| `jersey-container-servlet` | compile | JAX-RS REST endpoint registration |

All transitive dependencies from `yawl-engine`, `yawl-authentication`, and
`yawl-resourcing` (Hibernate, H2/PostgreSQL/MySQL, Log4j 2, etc.) are bundled into
the WAR's `WEB-INF/lib/`.

## Build Configuration Notes

- **No Java compilation** — both `default-compile` and `default-testCompile` Maven phases
  are bound to `none`. This is a **packaging-only module**: it contains no source code of
  its own, only WAR assembly configuration.
- WAR output name: `yawl` → deployed as `yawl.war` (context path `/yawl`)
- `failOnMissingWebXml=false` — uses programmatic or annotation-based servlet registration;
  no `web.xml` descriptor is required

## Deployment

```bash
# Build the WAR
mvn -pl yawl-utilities,yawl-elements,yawl-authentication,yawl-engine,yawl-resourcing,\
yawl-webapps,yawl-webapps/yawl-engine-webapp clean package

# Deploy to a running Tomcat
cp yawl-webapps/yawl-engine-webapp/target/yawl.war $CATALINA_HOME/webapps/
```
