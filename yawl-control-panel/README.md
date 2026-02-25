# yawl-control-panel

**Artifact:** `org.yawlfoundation:yawl-control-panel:6.0.0-Beta` | `packaging: jar`
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
java -jar yawl-control-panel/target/yawl-control-panel-6.0.0-Beta.jar
```

## Test Coverage

**No tests exist.** The test directory `../test/org/yawlfoundation/yawl/controlpanel/` does not exist.

Coverage gaps (entire module):
- Engine start/stop command invocation — no unit tests
- Specification deployment logic — no unit tests
- HTTP connection to the engine — no unit tests
- UI component rendering — no tests (would require Swing/TestFX harness)

## Roadmap

- **TestFX UI test suite** — add `YControlPanelTest` using the TestFX library to drive the Swing UI programmatically; assert button states, table population, and status label updates
- **Engine connection unit tests** — add `TestEngineConnector` mocking the HTTP layer to verify connection establishment, retry logic, and error presentation in the UI
- **Specification deployment tests** — add `TestSpecificationDeployer` verifying file selection, upload progress, and success/failure feedback flow
- **JavaFX migration** — evaluate migrating from Swing to JavaFX 21 (LTS) for a modern UI toolkit with CSS theming and better HiDPI support
- **Dark mode** — implement a theme toggle (light/dark) using the system `LookAndFeel` or a JavaFX CSS theme
- **Auto-update mechanism** — implement a background version check against the GitHub Releases API and present an in-app upgrade prompt when a newer version is available
- **Modular JAR (JPMS)** — declare a `module-info.java` for the control panel to reduce the distribution size via `jlink` by including only the required JDK modules
