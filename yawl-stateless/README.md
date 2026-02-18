# yawl-stateless

**Artifact:** `org.yawlfoundation:yawl-stateless:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Event-driven workflow execution engine with no persistent database state:

- **`YStatelessEngine`** — stateless workflow execution (no Hibernate, no DB connection)
- **`YCaseMonitor`** — in-memory case state tracking
- **`YCaseImporter`** / **`YCaseExporter`** — serialise and restore case state to/from JSON

Designed for serverless, reactive, and agent-based deployments where persistent state is
managed externally or is not required. Reuses the core engine model from `yawl-engine`
but strips all persistence infrastructure.

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-utilities` | util, exceptions |
| `yawl-elements` | Petri net model |
| `yawl-engine` | reuses engine APIs (YNetRunner, YWorkItem, etc.) without persistence |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `commons-lang3` | Utilities |
| `commons-collections4` | Extended collection types |
| `jdom2` | XML DOM |
| `jaxen` | XPath over JDOM |
| `jakarta.xml.bind-api` | JAXB |
| `jackson-databind` + `jackson-core` | JSON serialisation for case state |
| `log4j-api` | Logging |

Test dependencies: JUnit 4, Hamcrest, XMLUnit.

## Build Configuration Notes

- **Source directory:** `../src`; compiler filter scoped to `stateless/**` only
- **Test directory:** `../test`; test filter scoped to `stateless/**` only
- Log4j-to-SLF4J bridge excluded from test classpath

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-stateless clean package
```
