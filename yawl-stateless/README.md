# yawl-stateless

**Artifact:** `org.yawlfoundation:yawl-stateless:6.0.0-Beta` | `packaging: jar`
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

## Test Coverage

| Test Class | Tests | Focus |
|------------|-------|-------|
| `StatelessEngineCaseMonitorTest` | 17 | In-memory case monitor: create, advance, complete, query |
| `TestStatelessEngine` | 3 | Engine construction, spec loading, case launch |
| `YStatelessEngineSuspendResumeTest` | 0 | Suspend/resume lifecycle — placeholder, no assertions yet |
| `TestYCaseMonitoringService` | 0 | Monitoring service — placeholder, no assertions yet |

**Total active tests: 20** (placeholders excluded)

Run with: `mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-stateless test`

Coverage gaps:
- `YCaseImporter` / `YCaseExporter` JSON round-trip — untested
- Suspend and resume state transitions — placeholder only
- Concurrent case execution under virtual threads — not exercised

## Roadmap

- **Reactive streams bridge** — expose `YStatelessEngine` events as a `Flow.Publisher<YWorkflowEvent>` for Project Reactor / RxJava consumers
- **JSON case state schema** — formalise the `YCaseImporter` / `YCaseExporter` JSON format as a published JSON Schema; add round-trip property-based tests
- **Suspend / resume test suite** — implement `YStatelessEngineSuspendResumeTest` covering pause, serialise-to-JSON, restore, and resume execution
- **GraalVM native-image profile** — provide a `-P graalvm` build profile for cold-start-optimised serverless deployments
- **`YCaseMonitoringService` completion** — implement the monitoring service and wire its metrics into `yawl-monitoring`
- **Concurrent execution JMH benchmark** — measure throughput for N simultaneous stateless case executions under virtual threads
