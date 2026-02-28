# YAWL Engine API Reference

Complete API documentation for the YAWL stateful workflow engine.

## Core Classes

### YEngine

The main orchestrator for workflow execution.

**Lifecycle:**
```java
YEngine engine = YEngine.getInstance();  // Singleton access
engine.startup();                        // Initialize
// ... use engine ...
engine.shutdown();                       // Cleanup
```

**Case Management:**
| Method | Returns | Description |
|--------|---------|-------------|
| `createCase(String specID, Document data)` | String (caseID) | Create new case |
| `getCaseStatus(String caseID)` | String | Get case state (Running/Suspended/Completed/Failed) |
| `getCaseData(String caseID)` | Document | Retrieve current case data |
| `cancelCase(String caseID)` | boolean | Cancel case gracefully |
| `removeCaseFromEngine(String caseID)` | void | Forcefully remove case |

**Work Item Operations:**
| Method | Returns | Description |
|--------|---------|-------------|
| `getEnabledWorkItems(String caseID)` | Set<YWorkItem> | Get all ready tasks |
| `checkoutWorkItem(String caseID, String taskID)` | YWorkItem | Lock task for execution |
| `completeWorkItem(YWorkItem item, Document output, String user, boolean force)` | boolean | Mark task done |
| `getWorkItem(String workItemID)` | YWorkItem | Fetch specific task |
| `getCompletedWorkItems(String caseID)` | List<YWorkItem> | History of completed tasks |

**Specification Management:**
| Method | Returns | Description |
|--------|---------|-------------|
| `loadSpecification(YSpecification spec)` | void | Register workflow spec |
| `getSpecification(String specID)` | YSpecification | Get loaded spec |
| `removeSpecification(String specID)` | void | Unregister workflow spec |

### YWorkItem

Represents a single task instance.

**Properties:**
| Property | Type | Description |
|----------|------|-------------|
| `ID` | String | Unique task instance ID |
| `TaskName` | String | Human-readable task name |
| `Status` | YWorkItemStatus | State (enabled/executing/completed/failed) |
| `CaseID` | String | Parent case ID |
| `CaseData` | Document | Input data for task |
| `OutputData` | Document | Result data from task |

**Status Values:**
```java
YWorkItemStatus.statusEnabled      // Ready for execution
YWorkItemStatus.statusExecuting    // Currently being worked on
YWorkItemStatus.statusCompleted    // Successfully finished
YWorkItemStatus.statusFailed       // Execution failed
```

### YNetRunner

Petri net execution engine (typically used internally).

**Key Methods:**
| Method | Returns | Description |
|--------|---------|-------------|
| `continueIfPossible()` | void | Fire enabled transitions |
| `getEnabledTasks()` | Set<YTask> | Get ready task nodes |
| `getMarking()` | YMarking | Current token distribution |

## Configuration Properties

**Hibernate Persistence:**
```properties
hibernate.dialect=org.hibernate.dialect.PostgreSQL10Dialect
hibernate.connection.driver_class=org.postgresql.Driver
hibernate.connection.url=jdbc:postgresql://localhost:5432/yawl
hibernate.connection.username=yawl
hibernate.connection.password=secret

hibernate.hikaricp.maximumPoolSize=20
hibernate.hikaricp.minimumIdle=5
hibernate.hikaricp.idleTimeout=300000
```

**JPA Dialect:**
```properties
javax.persistence.jdbc.driver=org.postgresql.Driver
javax.persistence.jdbc.url=jdbc:postgresql://localhost:5432/yawl
javax.persistence.jdbc.user=yawl
javax.persistence.jdbc.password=secret
```

## Error Codes

| Code | Exception | Meaning |
|------|-----------|---------|
| `SPEC_NOT_FOUND` | YEngineException | Specification not loaded |
| `CASE_NOT_FOUND` | YEngineException | Case doesn't exist |
| `ITEM_NOT_FOUND` | YEngineException | Work item not found |
| `INVALID_STATE` | YDataStateException | Object in wrong state |
| `DATA_MISMATCH` | YException | Input/output schema mismatch |
| `PERSISTENCE_ERROR` | YEngineException | Database operation failed |

## Interfaces

### Interface B (Client-Engine)

REST API for external systems to interact with engine.

**Endpoints:**
```
POST   /yawl/resource/cases              # Create case
GET    /yawl/resource/cases/{caseID}     # Get case data
DELETE /yawl/resource/cases/{caseID}     # Cancel case

GET    /yawl/resource/worklist           # Get work items
POST   /yawl/resource/worklist/{itemID}  # Complete work item
```

### Interface E (Event Subscription)

Subscribe to workflow execution events.

```java
engine.getEventBus().subscribe(
    YWorkflowEventBus.EVENT_CASE_CREATED,
    (event) -> System.out.println("Case created: " + event.getCaseID())
);
```

**Event Types:**
- `EVENT_CASE_CREATED`
- `EVENT_CASE_COMPLETED`
- `EVENT_CASE_FAILED`
- `EVENT_WORK_ITEM_ENABLED`
- `EVENT_WORK_ITEM_COMPLETED`
- `EVENT_WORK_ITEM_FAILED`

## Common Patterns

### Sequential Case Execution
```java
while (!"Completed".equals(engine.getCaseStatus(caseID))) {
    Set<YWorkItem> items = engine.getEnabledWorkItems(caseID);
    for (YWorkItem item : items) {
        YWorkItem checked = engine.checkoutWorkItem(caseID, item.getID());
        engine.completeWorkItem(checked, null, null, true);
    }
}
```

### Monitor Event Stream
```java
engine.getEventBus().subscribe(
    YWorkflowEventBus.EVENT_WORK_ITEM_ENABLED,
    (event) -> {
        System.out.println("Task ready: " + event.getTaskName());
        YWorkItem item = event.getWorkItem();
        // ... handle task
    }
);
```

### Handle Multiple Cases
```java
ExecutorService executor = Executors.newFixedThreadPool(8);
for (String specID : specs) {
    executor.submit(() -> {
        try {
            String caseID = engine.createCase(specID, null);
            executeCase(caseID);
        } catch (Exception e) {
            logger.error("Case execution failed", e);
        }
    });
}
executor.shutdown();
```

## Dependencies

**Core:**
- `yawl-elements` — Domain model
- `yawl-utilities` — Base utilities
- `yawl-stateless` — Stateless engine (optional)

**Persistence:**
- `hibernate-core`
- `hibernate-hikaricp`
- `jakarta.persistence-api`

**Databases (runtime):**
- `postgresql` — Primary production database
- `mysql-connector-j` — Alternative database
- `h2` — Development/testing

**Logging:**
- `log4j-api`, `log4j-core`
- `slf4j-api`

## Performance Characteristics

| Operation | Latency (P95) | Notes |
|-----------|---------------|-------|
| Case creation | < 500ms | Includes DB persist |
| Work item checkout | < 200ms | Acquires lock |
| Task completion | < 300ms | Triggers successor task enabling |
| Token propagation (AND-split) | < 100ms | Per split branch |
| OR-join synchronization | < 500ms | May require state comparison |

## Scalability Limits

| Metric | Limit | Configuration |
|--------|-------|---------------|
| Concurrent cases | 10,000 | Platform threads |
| Concurrent cases (virtual threads) | 1,000,000 | Java 21+ with ZGC |
| Case data size | 100MB | Per document |
| Work items per case | 10,000 | Before performance degradation |
| Nested nets (depth) | 50 | Practical limit |

## Thread Safety

- **YEngine**: Thread-safe singleton
- **YWorkItem**: Not thread-safe (acquire lock via checkout)
- **YNetRunner**: Not thread-safe (per-case instance)
- **Document (case data)**: Not thread-safe (use copies)

## Concurrency Model

**Platform Threads (Java 8-20):**
- One OS thread per concurrent case
- Limited to ~10,000 cases due to memory
- High context switching overhead

**Virtual Threads (Java 21+):**
- Millions of lightweight threads
- One OS thread per core
- Minimal overhead, high throughput

**Structured Concurrency (Java 21+):**
- Use `StructuredTaskScope` for AND-splits
- Automatic exception handling and cancellation
- Better deadlock prevention

---

See also:
- [YAWL Elements Reference](./yawl-elements-api.md)
- [Engine Getting Started](../tutorials/yawl-engine-getting-started.md)
- [Case Execution How-To](../how-to/yawl-engine-case-execution.md)
