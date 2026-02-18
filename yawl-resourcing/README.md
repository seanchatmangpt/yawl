# yawl-resourcing

**Artifact:** `org.yawlfoundation:yawl-resourcing:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Resource management service for YAWL:

- Participant and role management (organisational model)
- Work queue lifecycle (offered → allocated → started → suspended → completed)
- Allocator and distributor strategies (random, round-robin, shortest queue, etc.)
- Hibernate-backed participant and queue persistence
- Interface between the engine (work item events) and human resources

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-engine` | engine APIs, work item state machine |

## Key Third-Party Dependencies

| Artifact | Purpose |
|----------|---------|
| `jakarta.persistence-api` | JPA annotations on resource entities |
| `jakarta.servlet-api` | Servlet API (provided by container) |
| `hibernate-core` | ORM for participant / queue persistence |
| `commons-lang3` | Utilities |
| `commons-collections4` | Extended collection types |
| `jdom2` | XML DOM |
| `jackson-databind` | JSON serialisation for resource data |
| `log4j-api` | Logging |

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/resourcing` (scoped to single package)
- **Test directory:** `../test/org/yawlfoundation/yawl/resourcing`
- Hibernate mapping files (`*.hbm.xml`) and XML/properties resources included on classpath

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-resourcing clean package
```
