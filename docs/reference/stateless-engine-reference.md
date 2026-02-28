# Reference: YAWL Stateless Engine API

Complete reference for the YStatelessEngine class and related APIs.

---

## Package Overview

```
org.yawlfoundation.yawl.stateless
├── engine
│   ├── YStatelessEngine
│   ├── YNetRunner
│   └── YWorkItemID
├── elements
│   ├── YSpecification (stateless variant)
│   └── YNet
├── event
│   ├── WorkItemEnabledEvent
│   ├── WorkItemCompletedEvent
│   └── CaseCompletedEvent
└── listener
    └── YStatelessListener
```

---

## YStatelessEngine

### Constructor

```java
public YStatelessEngine()
```

Create a new stateless engine instance. No database initialization required.

### Key Methods

#### newNetRunner()

```java
public YNetRunner newNetRunner(YNet net)
```

Create a new net runner for executing a net in stateless mode.

**Parameters:**
- `net`: The YAWL net to execute

**Returns:** YNetRunner instance for this net

**Example:**
```java
YStatelessEngine engine = new YStatelessEngine();
YNetRunner runner = engine.newNetRunner(specification.getNet("MyNet"));
```

#### getSpecification()

```java
public YSpecification getSpecification(String specURI)
```

Load a specification from the event store.

**Parameters:**
- `specURI`: Specification URI

**Returns:** YSpecification or null if not found

---

## YNetRunner

Primary interface for case execution in stateless mode.

### Constructor

```java
public YNetRunner(YNet net)
```

### Key Methods

#### notifyEngineStartup()

```java
public void notifyEngineStartup()
```

Notify the engine that execution is starting. Activates input conditions.

**Example:**
```java
runner.notifyEngineStartup();
```

#### getEnabledWorkItems()

```java
public Set<YWorkItem> getEnabledWorkItems()
```

Get all enabled work items (ready for execution).

**Returns:** Set of YWorkItem instances

**Example:**
```java
Set<YWorkItem> items = runner.getEnabledWorkItems();
items.forEach(item -> System.out.println(item.getTaskName()));
```

#### completeWorkItem()

```java
public void completeWorkItem(String workItemID, String outputData)
```

Complete a work item with output data.

**Parameters:**
- `workItemID`: Unique work item identifier
- `outputData`: XML data output from task

**Throws:** YStateException if work item not found

**Example:**
```java
runner.completeWorkItem("item-12345", "<result>Approved</result>");
```

#### getCaseState()

```java
public YCaseState getCaseState()
```

Get current case state (all enabled items, completed items, case data).

**Returns:** YCaseState object

**Example:**
```java
YCaseState state = runner.getCaseState();
System.out.println("Case variables: " + state.getCaseData());
```

#### getCaseDataList()

```java
public List<YParameter> getCaseDataList()
```

Get case-level data parameters.

**Returns:** List of YParameter objects

#### getNet()

```java
public YNet getNet()
```

Get the net being executed.

**Returns:** YNet instance

---

## YWorkItem

Represents a single executable task in a case.

### Key Properties

```java
public String getID()              // Unique work item ID
public String getTaskID()          // Task definition ID
public String getTaskName()        // Human-readable task name
public String getCaseID()          // Case ID this item belongs to
public String getStatus()          // Current status (enabled, executing, etc.)
public Map<String, String> getData()  // Task input data
```

### Methods

#### setData()

```java
public void setData(String xmlData)
```

Set output data for this work item before completion.

#### getCreationTime()

```java
public LocalDateTime getCreationTime()
```

Get when this work item was enabled.

---

## YCaseState

Represents the complete state of a case at a point in time.

### Key Methods

```java
public String getCaseID()
public Set<YWorkItem> getEnabledItems()
public Set<YWorkItem> getCompletedItems()
public Map<String, String> getCaseData()
public LocalDateTime getStartTime()
public LocalDateTime getLastModified()
```

---

## Event Types

### WorkItemEnabledEvent

Fired when a task becomes ready for execution.

```java
public class WorkItemEnabledEvent extends YStatelessEvent {
    public String getWorkItemID()
    public String getTaskName()
    public String getCaseID()
    public Map<String, String> getInputData()
}
```

### WorkItemCompletedEvent

Fired when a task is completed.

```java
public class WorkItemCompletedEvent extends YStatelessEvent {
    public String getWorkItemID()
    public String getTaskName()
    public String getCaseID()
    public Map<String, String> getOutputData()
}
```

### CaseCompletedEvent

Fired when case execution finishes.

```java
public class CaseCompletedEvent extends YStatelessEvent {
    public String getCaseID()
    public LocalDateTime getCompletionTime()
    public boolean isSuccessful()
    public String getCompletionStatus()
}
```

---

## YStatelessListener

Listener interface for engine events.

### Methods to Implement

```java
public interface YStatelessListener {
    void onWorkItemEnabled(WorkItemEnabledEvent event);
    void onWorkItemCompleted(WorkItemCompletedEvent event);
    void onCaseCompleted(CaseCompletedEvent event);
    void onExceptionOccurred(YStatelessException exception);
}
```

### Example Implementation

```java
public class MyListener implements YStatelessListener {
    @Override
    public void onWorkItemEnabled(WorkItemEnabledEvent event) {
        System.out.println("Task enabled: " + event.getTaskName());
    }

    @Override
    public void onWorkItemCompleted(WorkItemCompletedEvent event) {
        System.out.println("Task completed: " + event.getTaskName());
    }

    @Override
    public void onCaseCompleted(CaseCompletedEvent event) {
        System.out.println("Case finished: " + event.getCaseID());
    }

    @Override
    public void onExceptionOccurred(YStatelessException exception) {
        System.err.println("Error: " + exception.getMessage());
    }
}

// Register listener
runner.addListener(new MyListener());
```

---

## Configuration Properties

### Core Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `yawl.engine.mode` | `persistent` | Set to `stateless` for event-sourced mode |
| `yawl.event.store` | `memory` | Event store: `memory`, `kafka`, `s3` |
| `yawl.snapshot.interval` | `1000` | Snapshot after N events |
| `yawl.persistence.enabled` | `true` | Enable event persistence |

### Performance

| Property | Default | Description |
|----------|---------|-------------|
| `yawl.event.batch.size` | `100` | Events per batch |
| `yawl.event.buffer.size` | `10000` | In-memory event buffer |
| `yawl.execution.timeout` | `3600000` | Task timeout (ms) |
| `yawl.cache.enabled` | `true` | Enable specification caching |

### Kafka-Specific

| Property | Default | Description |
|----------|---------|-------------|
| `kafka.bootstrap.servers` | (required) | Broker addresses |
| `kafka.topic.events` | `yawl-events` | Event topic |
| `kafka.group.id` | `yawl-engine` | Consumer group |
| `kafka.num.partitions` | `10` | Event partitions |

---

## Exception Hierarchy

```
YStatelessException (abstract)
├── YNetExecutionException      // Error during net execution
├── YWorkItemException          // Error with work item
├── YSpecificationException     // Invalid specification
├── YEventStoreException        // Event persistence failure
└── YTimeoutException           // Execution timeout
```

### Common Exceptions

#### YSpecificationException

Thrown when specification is invalid or cannot be loaded.

```java
try {
    YNetRunner runner = engine.newNetRunner(net);
} catch (YSpecificationException e) {
    System.err.println("Invalid specification: " + e.getMessage());
}
```

#### YEventStoreException

Thrown when event store operations fail.

```java
try {
    runner.completeWorkItem(itemID, data);
} catch (YEventStoreException e) {
    System.err.println("Failed to persist event: " + e.getMessage());
    // Retry logic or fallback
}
```

---

## Threading Model

The stateless engine is **thread-safe for concurrent case execution**:

```java
// Safe: Multiple threads can execute different cases
ExecutorService executor = Executors.newFixedThreadPool(10);

for (int i = 0; i < 100; i++) {
    final int caseNum = i;
    executor.submit(() -> {
        YNetRunner runner = engine.newNetRunner(net);
        runner.notifyEngineStartup();
        // Execute case...
    });
}
```

However, a single `YNetRunner` instance is **NOT thread-safe**. For multi-threaded access to same case:

```java
// Use thread-local runners or synchronize
synchronized (runner) {
    Set<YWorkItem> items = runner.getEnabledWorkItems();
    // ...
}
```

---

## Best Practices

1. **Create one YStatelessEngine per application**
   ```java
   public class Application {
       private static final YStatelessEngine ENGINE = new YStatelessEngine();
   }
   ```

2. **Register listeners early**
   ```java
   runner.addListener(auditListener);
   runner.addListener(metricsListener);
   runner.notifyEngineStartup();
   ```

3. **Always provide output data**
   ```java
   item.setData(outputXML); // Before completion
   runner.completeWorkItem(item.getID(), item.getData());
   ```

4. **Handle exceptions gracefully**
   ```java
   try {
       runner.completeWorkItem(itemID, data);
   } catch (YEventStoreException e) {
       // Retry with exponential backoff
   }
   ```

5. **Monitor event store**
   ```java
   long eventCount = engine.getEventStore().getEventCount();
   long lag = engine.getEventStore().getReplicationLag();
   ```

---

## Integration Examples

### REST API Wrapper

```java
@RestController
@RequestMapping("/api/cases")
public class CaseController {
    private final YStatelessEngine engine;

    @PostMapping
    public ResponseEntity<String> createCase(@RequestBody CreateCaseRequest req) {
        YNetRunner runner = engine.newNetRunner(spec);
        runner.notifyEngineStartup();
        return ResponseEntity.ok(runner.getCaseState().getCaseID());
    }

    @GetMapping("/{caseId}/tasks")
    public ResponseEntity<List<TaskDTO>> getEnabledTasks(@PathVariable String caseId) {
        YNetRunner runner = engine.getNetRunner(caseId);
        return ResponseEntity.ok(
            runner.getEnabledWorkItems().stream()
                .map(TaskDTO::from)
                .collect(Collectors.toList())
        );
    }
}
```

### Message Queue Integration

```java
public class KafkaWorkItemListener implements YStatelessListener {
    @Override
    public void onWorkItemEnabled(WorkItemEnabledEvent event) {
        kafkaTemplate.send("task-enabled", new TaskMessage(
            event.getWorkItemID(),
            event.getTaskName(),
            event.getInputData()
        ));
    }
}
```

---

## See Also

- [Stateless Architecture](../explanation/stateless-architecture.md)
- [Event-Sourced Patterns](../reference/event-sourcing-patterns.md)
- [API Reference](../reference/api-reference.md)
