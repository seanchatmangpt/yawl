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
