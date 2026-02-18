# yawl-worklet

**Artifact:** `org.yawlfoundation:yawl-worklet:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Dynamic worklet selection and exception handling service:

- **Ripple-Down Rules (RDR)** engine for case-level and task-level exception handling
- Worklet repository management (select and launch replacement sub-workflows at runtime)
- Exception types handled: item abort, time-out, constraint violation, pre/post condition failure
- Adaptive workflow: cases evolve at runtime without redeployment
- Integration with the resourcing service for task-level exception routing

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-engine` | engine APIs, work item events |
| `yawl-resourcing` | resource service integration for exception routing |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `jakarta.persistence-api` | JPA annotations on worklet data |
| `jakarta.servlet-api` | Servlet API (provided by container) |
| `hibernate-core` | ORM for worklet rule persistence |
| `commons-lang3` | Utilities |
| `jdom2` | XML DOM (worklet specs are XML) |
| `log4j-api` | Logging |

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/worklet` (scoped to single package)
- **Test directory:** `../test/org/yawlfoundation/yawl/worklet`

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-resourcing,yawl-worklet clean package
```

## Test Coverage

**No tests exist.** The test directory `../test/org/yawlfoundation/yawl/worklet/` does not exist.

All worklet behaviour is validated only through manual integration testing.

Coverage gaps (entire module):
- RDR rule evaluation logic — no unit tests
- Worklet selection from repository — no unit tests
- Exception type handlers (abort, time-out, constraint violation) — no unit tests
- Worklet repository persistence (Hibernate round-trip) — no unit tests
- Resourcing integration (task-level exception routing) — no unit tests

## Roadmap

- **RDR unit test suite** — add `TestRDRCondition`, `TestRDRTree`, `TestRDRNode` covering rule evaluation, tree traversal, and conclusion selection
- **Worklet selection tests** — add `TestWorkletSelector` with a test worklet repository covering single-match, multi-match, and no-match scenarios
- **Exception handler tests** — add tests for each `YExceptionHandler` subtype: item abort, time-out, pre/post condition failure, constraint violation
- **Repository persistence tests** — add Hibernate round-trip tests using an in-memory H2 database for `WorkletRecord` and `RDRSet`
- **Online RDR learning** — implement the Ripple-Down Rules induction algorithm so the worklet service can propose new rules from past exception resolutions
- **REST API for worklet management** — expose worklet repository CRUD and rule editing over a JAX-RS endpoint to enable GUI-free administration
- **Testcontainers integration test** — add a full end-to-end test that launches a case, triggers a time-out exception, verifies worklet selection, and confirms case resumption
