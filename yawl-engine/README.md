# yawl-engine

**Artifact:** `org.yawlfoundation:yawl-engine:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

The stateful, persistent workflow engine — the central runtime for YAWL:

- **`YEngine`** — lifecycle management, case creation, work item routing, Interface B/E/X
- **`YNetRunner`** — Petri net token propagation and firing rules
- **`YWorkItem`** — work item state machine (enabled → executing → completed / failed)
- **`YPersistenceManager`** — Hibernate-based state persistence across restarts
- **`YEventLogger`** + supporting logging classes — structured workflow event log (XES-compatible)
- **`swingWorklist`** — Swing-based worklist UI (bundled with engine JAR)
- **`engine.time`** — timer management, deadline tracking
- **`engine.core`** — case monitor, instance control

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-elements` | Petri net model (transitively pulls `yawl-utilities`) |

## Key Third-Party Dependencies

| Artifact | Scope | Purpose |
|----------|-------|---------|
| `jakarta.persistence-api` | compile | JPA annotations |
| `jakarta.servlet-api` | provided | Servlet container integration |
| `hibernate-core` | compile | ORM / session factory |
| `hibernate-hikaricp` | compile | HikariCP connection pool integration |
| `h2` | compile | Embedded DB for development and tests |
| `postgresql` | runtime | Production relational database |
| `mysql-connector-j` | runtime | Alternative production database |
| `HikariCP` | compile | High-performance connection pool |
| `commons-lang3` | compile | Utilities |
| `commons-dbcp2` | compile | Apache DBCP connection pool (alternative) |
| `jdom2` | compile | XML processing |
| `jakarta.xml.bind-api` | compile | JAXB |
| `log4j-api` + `log4j-core` | compile | Logging |

Test dependencies: JUnit 4, Hamcrest.

## Build Configuration Notes

- **Source directory:** `../src`; compiler filter includes `engine/**`, `swingWorklist/**`, plus the
  six engine-coupled logging classes excluded from `yawl-utilities`:
  `YEventLogger`, `YEventKeyCache`, `YLogPredicate`, `YLogPredicateWorkItemParser`, `YLogServer`, `YXESBuilder`
- **Schema XSD files** are copied to `org/yawlfoundation/yawl/unmarshal/` on the classpath,
  as required by `YSchemaVersion` for runtime schema validation
- **Test resources** include Hibernate configuration (`hibernate.cfg.xml`) from `../build/properties`
- Tests cover `engine/**`, `swingWorklist/**`, `patternmatching/**`
- Log4j-to-SLF4J bridge excluded from test classpath

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine clean package
```

## Test Coverage

### Core Engine (`engine/`)

| Test Class | Tests | Focus |
|------------|-------|-------|
| `EngineIntegrationTest` | 11 | End-to-end case creation and completion |
| `NetRunnerBehavioralTest` | 19 | Petri net firing, token propagation, split/join |
| `TaskLifecycleBehavioralTest` | 14 | Work item state machine transitions |
| `TestCaseCancellation` | 3 | Case abort and cleanup |
| `TestDeadlockingWorkflows` | 1 | Deadlock detection |
| `TestEngineAgainstABeta4Spec` | 1 | Legacy spec backward compatibility |
| `TestEngineSystem1` | 1 | System 1 end-to-end |
| `TestEngineSystem2` | 3 | System 2 end-to-end |
| `TestImproperCompletion` | 1 | Improper completion handling |
| `TestMediumViolationFixes` | 14 | Soundness violation regression tests |
| `TestOrJoin` | 2 | OR-join activation semantics |
| `TestPersistence` | 1 | Engine restart with persisted state |
| `TestRestServiceMethods` | 2 | Interface B REST method behaviour |
| `TestSimpleExecutionUseCases` | 1 | Basic case execution use cases |
| `TestYEngineInit` | 1 | Engine initialisation sequence |
| `TestYNetRunner` | 2 | `YNetRunner` direct unit tests |
| `TestYSpecificationID` | 27 | Spec ID parsing, equality, ordering |
| `TestYWorkItem` | 2 | Work item construction and equality |
| `TestYWorkItemID` | 1 | Work item ID parsing |
| `TestYWorkItemRepository` | 2 | Repository CRUD operations |
| `VirtualThreadPinningTest` | 3 | Virtual thread pinning regression |
| `Interface_ClientVirtualThreadsTest` | 9 | Concurrent Interface B under virtual threads |

### Pattern Matching (`patternmatching/`)

| Test Class | Tests | Focus |
|------------|-------|-------|
| `EnumExhaustivenessTest` | 11 | Exhaustive switch coverage on sealed enums |
| `InstanceofPatternTest` | 15 | `instanceof` pattern matching chains |
| `YSpecificationPatternTest` | 14 | Pattern matching over spec elements |

**Total: ~157 tests across 25 test classes**

Run with: `mvn -pl yawl-utilities,yawl-elements,yawl-engine test`

Coverage gaps:
- `YEventLogger` / `YXESBuilder` — log output format not unit-tested
- `swingWorklist` package — no Swing UI tests
- `engine.time` timer callbacks — not directly exercised in isolation

## Roadmap

- **Virtual thread per case** — refactor `YNetRunner.continueIfPossible()` to execute on a dedicated virtual thread via `Thread.ofVirtual().start()`, eliminating platform thread blocking
- **Structured concurrency for AND-splits** — use `StructuredTaskScope.ShutdownOnFailure` to fan out parallel branches and collect results with automatic cancellation on failure
- **Scoped values for workflow context** — replace any remaining `ThreadLocal<WorkflowContext>` with `ScopedValue<WorkflowContext>` for safe virtual thread propagation
- **CQRS split on Interface B** — separate `InterfaceBClient` into a read-side query object and a write-side command object to improve testability and caching opportunities
- **PostgreSQL CI job** — add a GitHub Actions job running the full engine test suite against a Testcontainers-managed PostgreSQL instance
- **`TestPersistence` expansion** — extend from one scenario to cover partial completion, multi-case restart, and Hibernate schema migration paths
