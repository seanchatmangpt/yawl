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

## Test Coverage

This module contains no source code and no unit tests (compilation is disabled).

Integration coverage comes from:

| Test Class | Module | Tests | Focus |
|------------|--------|-------|-------|
| `EndToEndWorkflowExecutionTest` | `yawl-integration` | 4 | Full case execution through the deployed engine |
| `MultiModuleIntegrationTest` | `yawl-integration` | 6 | Cross-module assertions including authentication and resourcing |

**Total integration tests exercising this WAR: 10**

Coverage gaps:
- HTTP endpoint contract testing (status codes, response bodies, content-type) — not tested
- Authentication filter chain (JWT validation on protected endpoints) — not tested in isolation
- Jersey JAX-RS resource class registration — not verified by assertion

## Roadmap

- **OpenAPI / Swagger spec generation** — integrate `swagger-core` or `smallrye-open-api` to auto-generate an `openapi.yaml` from Jersey JAX-RS annotations at build time
- **Health readiness probes** — implement `/yawl/actuator/health/readiness` and `/yawl/actuator/health/liveness` endpoints for Kubernetes pod lifecycle management
- **Docker image publishing** — add a `Dockerfile` and GitHub Actions workflow to build and push `ghcr.io/yawlfoundation/yawl-engine:latest` on every merge to `master`
- **Testcontainers WAR smoke test** — add `YawlEngineWebappIT` using Testcontainers + Tomcat to assert HTTP 200 from health endpoint after WAR deployment
- **TLS 1.3 enforcement** — configure the Tomcat `SSLHostConfig` in the Docker image to disable TLS 1.0/1.1/1.2 and allow only TLS 1.3 in production deployments
- **Graceful shutdown** — implement a `ContextListener` that drains in-flight cases before Tomcat stops, preventing work item loss during rolling restarts
