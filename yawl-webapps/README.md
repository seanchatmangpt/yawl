# yawl-webapps

**Artifact:** `org.yawlfoundation:yawl-webapps:6.0.0-Alpha` | `packaging: pom`
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
