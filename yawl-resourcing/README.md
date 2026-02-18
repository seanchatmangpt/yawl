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

## Test Coverage

| Test Class | Tests | Focus |
|------------|-------|-------|
| `ResourceLogicUnitTest` | 15 | Allocator strategy selection, queue routing, role membership |

**Total: 15 tests across 1 test class**

Run with: `mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-resourcing test`

Coverage gaps:
- Individual allocator implementations (random, round-robin, shortest queue) — not unit-tested in isolation
- Distributor strategies — not tested
- Hibernate participant persistence (CRUD operations on `YParticipant`, `YRole`) — not tested
- Work queue lifecycle transitions (offered → allocated → started → suspended) — tested only end-to-end via `EngineIntegrationTest` in `yawl-engine`

## Roadmap

- **Allocator unit tests** — add `TestRandomAllocator`, `TestRoundRobinAllocator`, `TestShortestQueueAllocator` with mock work queues
- **LDAP / Active Directory participant import** — implement `LDAPParticipantImporter` to synchronise organisational model from corporate directory
- **REST API for queue management** — expose work queues over a dedicated JAX-RS endpoint for browser-based worklist clients and integration tests
- **Role-based task constraints** — enforce four-eyes and Chinese-wall separation-of-duty constraints at allocation time, with unit test coverage
- **Participant cache** — add a Caffeine or JCache second-level cache for the organisational model to reduce DB round-trips in high-participant deployments
- **Queue persistence migration** — migrate Hibernate mapping files (`*.hbm.xml`) to JPA annotations for consistency with the rest of the codebase
