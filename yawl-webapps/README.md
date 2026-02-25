# yawl-webapps

**Artifact:** `org.yawlfoundation:yawl-webapps:6.0.0-Beta` | `packaging: pom`
**Parent:** `yawl-parent`

## Purpose

Aggregator module for all WAR-packaged YAWL web applications. Does not contain source
code itself — it exists to group WAR sub-modules under a single Maven parent so that
common WAR plugin configuration is inherited in one place.

**Current sub-modules:**

| Module | Artifact |
|--------|----------|
| `yawl-engine-webapp` | `yawl-engine-webapp` — primary engine WAR |

Additional web application modules (resourcing webapp, worklet webapp, etc.) will be
added here as they are migrated to the Maven build.

## Configuration

- `maven-war-plugin` configured with `failOnMissingWebXml=false` to support annotation-based
  or programmatic servlet registration (no `web.xml` required)
- `jakarta.servlet-api` declared as `provided` scope — inherited by all child WARs

## Quick Build

```bash
# Build the WAR aggregator and all sub-modules
mvn -pl yawl-utilities,yawl-elements,yawl-authentication,yawl-engine,yawl-resourcing,\
yawl-webapps,yawl-webapps/yawl-engine-webapp clean package
```

## Test Coverage

This is an aggregator POM with no source code. There are no tests at this level.

Integration tests for the deployed WAR are executed via `yawl-engine-webapp` sub-module and
the `yawl-integration` module's `MultiModuleIntegrationTest` suite (4 + 6 tests).

## Roadmap

- **`yawl-resourcing-webapp`** — package the resource service as a separate `yawl-resource.war` for independent deployment and horizontal scaling
- **`yawl-worklet-webapp`** — package the worklet service as `yawl-worklet.war`
- **`yawl-scheduling-webapp`** — package the scheduling service as `yawl-scheduling.war`
- **Docker Compose definition** — add a `docker-compose.yml` at this level that starts all YAWL WAR containers alongside PostgreSQL and a reverse proxy
- **Testcontainers smoke test** — add a `YawlWebappsSmokeTest` that launches `yawl.war` in an embedded Tomcat (via Testcontainers `TomcatContainer`) and asserts HTTP health endpoints respond correctly
- **OpenAPI aggregation** — aggregate the OpenAPI specs from all child WARs into a single `yawl-api.yaml` at build time
