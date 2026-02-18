# yawl-control-panel

**Artifact:** `org.yawlfoundation:yawl-control-panel:6.0.0-Alpha` | `packaging: jar`
**Main-Class:** `org.yawlfoundation.yawl.controlpanel.YControlPanel`
**Parent:** `yawl-parent`

## Purpose

Swing-based desktop management application for YAWL:

- Start, stop, and restart the YAWL engine and supporting services
- Deploy and undeploy workflow specifications
- Monitor running cases and work item queues
- Configure engine connection settings (host, port, credentials)
- Install and update YAWL components

Can be launched directly as an executable JAR (`java -jar yawl-control-panel-*.jar`).

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-engine` | Engine APIs for status monitoring and control |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `commons-lang3` | Utilities |
| `commons-io` | File handling (specification upload, log file reading) |
| `log4j-api` + `log4j-core` | Logging |
| `slf4j-api` | Logging facade |

No ORM, no servlet, no XML parser dependencies beyond what is transitively
provided by `yawl-engine`.

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/controlpanel` (scoped to single package)
- **Test directory:** `../test/org/yawlfoundation/yawl/controlpanel`
- **Resources:** image assets (`.png`, `.gif`, `.jpg`) and `.properties` files are included
  from the source directory and bundled into the JAR
- `maven-jar-plugin` sets `addClasspath=true` and `mainClass=YControlPanel` for executable JAR launch
- The parent POM's global JAR plugin configuration also sets `mainClass` to `YControlPanel`,
  making this the default executable across all YAWL JARs

## Launch

```bash
# Build
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-control-panel clean package

# Run
java -jar yawl-control-panel/target/yawl-control-panel-6.0.0-Alpha.jar
```
