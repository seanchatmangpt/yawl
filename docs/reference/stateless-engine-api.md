# Stateless Engine API Reference

**Version**: 6.0.0
**Last Updated**: 2026-02-28
**Status**: Production

## Overview

The `YStatelessEngine` provides a simplified, stateless API for executing YAWL workflows in modern deployment scenarios (Kubernetes, Lambda, containerized environments). Unlike traditional stateful engines, YStatelessEngine is designed to work with externally managed state through case marshaling and restoration.

This reference documents all public methods, configuration options, interfaces, and integration patterns.

---

## Quick Comparison: YStatelessEngine vs YEngine

| Aspect | YStatelessEngine | YEngine |
|--------|------------------|---------|
| **State Management** | Externally managed (marshal/restore) | Internal persistence |
| **Deployment** | Cloud-native, stateless | Traditional server |
| **Memory Profile** | Low (cases unloaded between requests) | High (all cases in memory) |
| **Scalability** | Horizontal (stateless replicas) | Vertical (single server) |
| **Case Persistence** | Explicit (unloadCase/restoreCase) | Automatic (database) |
| **Event Listeners** | Full support | Full support |
| **Multi-threading** | Virtual threads (Java 21+) | Virtual threads (Java 21+) |

---

## Table of Contents

1. [Core Classes Reference](#core-classes-reference)
2. [API Methods](#api-methods)
3. [Configuration Options](#configuration-options)
4. [State Management](#state-management)
5. [Listener Architecture](#listener-architecture)
6. [Integration Patterns](#integration-patterns)
7. [Error Handling](#error-handling)
8. [Performance Characteristics](#performance-characteristics)

---

## Core Classes Reference

### YStatelessEngine

The primary facade for stateless workflow execution.

**Package**: `org.yawlfoundation.yawl.stateless`

**Instantiation**:

```java
// Minimal (no case monitoring)
YStatelessEngine engine = new YStatelessEngine();

// With idle case timeout (60 seconds)
YStatelessEngine engine = new YStatelessEngine(60000);

// With dynamic idle timeout configuration
YStatelessEngine engine = new YStatelessEngine();
engine.setIdleCaseTimer(120000); // 120 second idle timeout
```

**Inheritance**: None (final facade class)

**Thread Safety**: Thread-safe for all case operations. Uses `ReentrantLock` for case state synchronization.

---

### YNetRunner

Encapsulates the runtime state of a case instance.

**Package**: `org.yawlfoundation.yawl.stateless.engine`

**Key Properties**:

| Property | Type | Description |
|----------|------|-------------|
| `getCaseID()` | `YIdentifier` | Unique case identifier |
| `getSpecificationID()` | `YSpecificationID` | Specification this case instantiates |
| `getEnabledWorkItems()` | `Set<YWorkItem>` | Currently enabled (not yet started) work items |
| `getBusyWorkItems()` | `Set<YWorkItem>` | Currently executing work items |
| `getNetData()` | `YNetData` | Case data variables and state |
| `getTopRunner()` | `YNetRunner` | Root net runner (for nested cases) |
| `getAllRunnersForCase()` | `Set<YNetRunner>` | All runners in case hierarchy |

**Usage Context**:

```java
YNetRunner runner = engine.launchCase(spec, "case-001");
Set<YWorkItem> items = runner.getEnabledWorkItems();
System.out.println("Case " + runner.getCaseID() + " has " + items.size() + " enabled items");
```

---

### YWorkItem

Represents a task instance within a case.

**Package**: `org.yawlfoundation.yawl.stateless.engine`

**Key Properties**:

| Property | Type | Description |
|----------|------|-------------|
| `getWorkItemID()` | `YWorkItemID` | Unique work item identifier |
| `getStatus()` | `YWorkItemStatus` | Current status (Enabled, Executing, Completed, etc.) |
| `getTaskID()` | `String` | ID of task this work item is derived from |
| `getEnablementTime()` | `Instant` | When work item was enabled |
| `getStartTime()` | `Instant` | When work item was started |
| `getData()` | `Element` | XML data associated with work item |
| `getNetRunner()` | `YNetRunner` | Owning case runner |
| `getDocumentation()` | `String` | Task documentation |

**Work Item Status Enum**:

```java
public enum YWorkItemStatus {
    Enabled,       // Ready to start
    Executing,     // Executing
    Suspended,     // Paused (can resume)
    Completed,     // Finished successfully
    Deadlocked,    // Cannot progress
    Cancelled,     // Cancelled by user
    Failed         // Failed during execution
}
```

---

### YSpecification

Represents a loaded YAWL workflow specification.

**Package**: `org.yawlfoundation.yawl.stateless.elements`

**Key Methods**:

```java
YSpecificationID getSpecificationID();
String getURI();
YNet getRootNet();
Set<YDecomposition> getDecompositions();
```

**Unmarshaling**:

```java
String specXML = /* XML from file or service */;
YSpecification spec = engine.unmarshalSpecification(specXML);
```

---

### YIdentifier

Wrapper for case and net identifiers.

**Package**: `org.yawlfoundation.yawl.stateless.elements.marking`

**Usage**:

```java
YIdentifier caseID = runner.getCaseID();
String idString = caseID.toString();    // Serialize
YIdentifier restored = new YIdentifier(idString); // Deserialize
```

---

## API Methods

### Specification Loading

#### `unmarshalSpecification(String xml) : YSpecification`

Parse YAWL specification XML and return a populated YSpecification object.

**Parameters**:
- `xml` (String): XML representation of YAWL specification

**Returns**: `YSpecification` object ready for case launching

**Throws**:
- `YSyntaxException`: If XML is malformed or invalid

**Example**:

```java
try {
    String specXML = Files.readString(Paths.get("workflow.yawl"));
    YSpecification spec = engine.unmarshalSpecification(specXML);
    System.out.println("Loaded: " + spec.getSpecificationID());
} catch (YSyntaxException e) {
    System.err.println("Invalid specification: " + e.getMessage());
}
```

---

### Case Lifecycle: Launching

#### `launchCase(YSpecification spec) : YNetRunner`

Launch a case with auto-generated UUID as case ID.

**Parameters**:
- `spec` (YSpecification): Loaded specification

**Returns**: `YNetRunner` encapsulating initial case state

**Throws**:
- `YStateException`: If case state cannot be created
- `YDataStateException`: If case data cannot be initialized
- `YEngineStateException`: If engine is not running
- `YQueryException`: If data mapping query is malformed

**Example**:

```java
YSpecification spec = engine.unmarshalSpecification(specXML);
YNetRunner runner = engine.launchCase(spec);
System.out.println("Case " + runner.getCaseID() + " launched");
```

---

#### `launchCase(YSpecification spec, String caseID) : YNetRunner`

Launch a case with explicit case identifier.

**Parameters**:
- `spec` (YSpecification): Loaded specification
- `caseID` (String): Case identifier to assign

**Returns**: `YNetRunner` encapsulating initial case state

**Example**:

```java
YNetRunner runner = engine.launchCase(spec, "acme-claim-2024-001");
```

---

#### `launchCase(YSpecification spec, String caseID, String caseParams) : YNetRunner`

Launch a case with initial data parameters.

**Parameters**:
- `spec` (YSpecification): Loaded specification
- `caseID` (String): Case identifier
- `caseParams` (String): XML string with initial variable values

**XML Format** (caseParams):

```xml
<specification_uri>
  <variable_name>value</variable_name>
  <!-- More variables -->
</specification_uri>
```

**Example**:

```java
String initData = """
    <acme_claim_process>
        <claimID>CLM-2024-0001</claimID>
        <amount>50000.00</amount>
        <claimType>property_damage</claimType>
    </acme_claim_process>
    """;
YNetRunner runner = engine.launchCase(spec, "claim-001", initData);
```

---

#### `launchCase(YSpecification spec, String caseID, String caseParams, YLogDataItemList logItems) : YNetRunner`

Launch a case with initial data and audit log items.

**Parameters**:
- `spec` (YSpecification): Loaded specification
- `caseID` (String): Case identifier
- `caseParams` (String): Initial variable values (XML)
- `logItems` (YLogDataItemList): Audit trail entries

**Example**:

```java
YLogDataItemList logItems = new YLogDataItemList();
logItems.add(new YLogDataItem("system", "Case initiated via API", null));

YNetRunner runner = engine.launchCase(spec, "case-001", initData, logItems);
```

---

#### `launchCasesParallel(YSpecification spec, List<String> caseParams) : List<YNetRunner>`

Launch multiple cases concurrently using virtual threads.

**Parameters**:
- `spec` (YSpecification): Specification common to all cases
- `caseParams` (List<String>): Per-case XML parameter strings (use null for defaults)

**Returns**: Ordered list of YNetRunner objects (one per parameter entry)

**Throws**:
- `YStateException`: If any case launch fails; all previously launched cases are rolled back
- `IllegalArgumentException`: If spec is null or caseParams is empty

**Failure Semantics**: All launches are awaited; first failure is re-thrown after cleanup.

**Example**:

```java
List<String> paramsList = Arrays.asList(
    "<root><id>1</id></root>",
    "<root><id>2</id></root>",
    null  // Use defaults
);

try {
    List<YNetRunner> runners = engine.launchCasesParallel(spec, paramsList);
    System.out.println("Launched " + runners.size() + " cases in parallel");
    runners.forEach(r -> System.out.println("  - Case: " + r.getCaseID()));
} catch (YStateException e) {
    System.err.println("Parallel launch failed: " + e.getMessage());
}
```

---

### Work Item Operations

#### `startWorkItem(YWorkItem workItem) : YWorkItem`

Begin executing an enabled or fired work item.

**Parameters**:
- `workItem` (YWorkItem): Work item to start (must be in Enabled or Fired state)

**Returns**: Updated YWorkItem with status = Executing

**Throws**:
- `YStateException`: If case is unknown or work item cannot be started
- `YEngineStateException`: If engine not running
- `YQueryException`: If data mapping query malformed
- `YDataStateException`: If data state error

**Example**:

```java
Set<YWorkItem> enabled = runner.getEnabledWorkItems();
for (YWorkItem item : enabled) {
    YWorkItem executing = engine.startWorkItem(item);
    System.out.println("Started: " + executing.getWorkItemID());
}
```

---

#### `completeWorkItem(YWorkItem workItem, String data, String logPredicate) : YWorkItem`

Complete an executing work item with output data.

**Parameters**:
- `workItem` (YWorkItem): Work item to complete (must be in Executing state)
- `data` (String): XML string with output variable values
- `logPredicate` (String): Log predicate string (can be null)

**Returns**: Updated YWorkItem with status = Completed

**Example**:

```java
String outputData = """
    <acme_claim_process>
        <decision>approved</decision>
        <approvalDate>2024-02-28</approvalDate>
        <approvedAmount>45000.00</approvedAmount>
    </acme_claim_process>
    """;

YWorkItem completed = engine.completeWorkItem(item, outputData, null);
```

---

#### `completeWorkItem(YWorkItem workItem, String data, String logPredicate, WorkItemCompletion type) : YWorkItem`

Complete with explicit completion type.

**Parameters**:
- `workItem` (YWorkItem): Work item to complete
- `data` (String): Output XML
- `logPredicate` (String): Log predicate (can be null)
- `type` (WorkItemCompletion): Completion mode (Normal, Force, Fail)

**Completion Types**:

| Type | Behavior |
|------|----------|
| `Normal` | Standard completion, triggers downstream tasks |
| `Force` | Force completion, bypassing validations |
| `Fail` | Mark as failed, case may enter exception handling |

**Example**:

```java
YWorkItem completed = engine.completeWorkItem(
    item,
    outputData,
    null,
    WorkItemCompletion.Force  // Force complete
);
```

---

#### `skipWorkItem(YWorkItem workItem) : YWorkItem`

Skip an enabled work item without executing it.

**Parameters**:
- `workItem` (YWorkItem): Enabled work item to skip

**Returns**: Updated YWorkItem with status = Completed

**Throws**: `YStateException` if work item cannot be skipped

**Example**:

```java
YWorkItem item = runner.getEnabledWorkItems().iterator().next();
YWorkItem skipped = engine.skipWorkItem(item);
System.out.println("Skipped: " + skipped.getWorkItemID());
```

---

#### `suspendWorkItem(YWorkItem workItem) : YWorkItem`

Pause an executing work item.

**Parameters**:
- `workItem` (YWorkItem): Executing work item

**Returns**: Updated YWorkItem with status = Suspended

**Example**:

```java
YWorkItem item = /* get executing item */;
YWorkItem suspended = engine.suspendWorkItem(item);
System.out.println("Suspended: " + suspended.getWorkItemID());
```

---

#### `unsuspendWorkItem(YWorkItem workItem) : YWorkItem`

Resume a suspended work item.

**Parameters**:
- `workItem` (YWorkItem): Suspended work item

**Returns**: Updated YWorkItem with status = Executing

**Example**:

```java
YWorkItem suspended = /* get suspended item */;
YWorkItem resumed = engine.unsuspendWorkItem(suspended);
```

---

#### `rollbackWorkItem(YWorkItem workItem) : YWorkItem`

Roll back an executing work item to enabled state.

**Parameters**:
- `workItem` (YWorkItem): Executing work item

**Returns**: Updated YWorkItem with status = Enabled

**Example**:

```java
YWorkItem item = /* get executing item */;
YWorkItem rolledBack = engine.rollbackWorkItem(item);
```

---

#### `createNewInstance(YWorkItem workItem, String paramValueForMICreation) : YWorkItem`

Create a new instance in a multi-instance dynamic task.

**Parameters**:
- `workItem` (YWorkItem): Multi-instance work item
- `paramValueForMICreation` (String): Input parameter XML

**XML Format**:

```xml
<data>
  <InputParam>value</InputParam>
</data>
```

**Returns**: New YWorkItem instance

**Throws**: `YStateException` if task doesn't support dynamic instance creation

**Example**:

```java
String params = """
    <data>
        <EmployeeID>EMP-0042</EmployeeID>
    </data>
    """;
YWorkItem newInstance = engine.createNewInstance(item, params);
```

---

#### `checkEligibilityToAddInstances(YWorkItem workItem) : void`

Verify that a work item can have new instances created.

**Parameters**:
- `workItem` (YWorkItem): Multi-instance work item

**Throws**: `YStateException` if ineligible

**Example**:

```java
try {
    engine.checkEligibilityToAddInstances(item);
    // Can safely call createNewInstance
} catch (YStateException e) {
    System.err.println("Cannot add more instances: " + e.getMessage());
}
```

---

### Case Lifecycle: Control

#### `suspendCase(YNetRunner runner) : void`

Pause all execution in a case.

**Parameters**:
- `runner` (YNetRunner): Case runner

**Throws**: `YStateException` if case state out-of-sync

**Example**:

```java
engine.suspendCase(runner);
System.out.println("Case suspended");
```

---

#### `resumeCase(YNetRunner runner) : void`

Resume a suspended case.

**Parameters**:
- `runner` (YNetRunner): Suspended case runner

**Throws**:
- `YStateException`: If case state out-of-sync
- `YQueryException`: If data mapping query malformed
- `YDataStateException`: If data state error

**Example**:

```java
engine.resumeCase(runner);
System.out.println("Case resumed");
```

---

#### `cancelCase(YNetRunner runner) : void`

Terminate a case immediately.

**Parameters**:
- `runner` (YNetRunner): Case runner

**Throws**: `YStateException` if case unknown

**Example**:

```java
engine.cancelCase(runner);
System.out.println("Case cancelled");
```

---

### Case Persistence

#### `unloadCase(YIdentifier caseID) : String`

Export case state to XML (requires case monitoring enabled).

**Parameters**:
- `caseID` (YIdentifier): Case to export

**Returns**: Complete case state as XML string

**Throws**:
- `YStateException`: If case monitoring disabled or case not found
- Thread-safe: Uses `ReentrantLock` to prevent race conditions

**Use Case**: Persist case to Redis, PostgreSQL, or cloud storage for later restoration.

**Example**:

```java
YStatelessEngine engine = new YStatelessEngine(60000);  // Enable monitoring
YNetRunner runner = engine.launchCase(spec, "case-001");

// Process work items...
Set<YWorkItem> items = runner.getEnabledWorkItems();
for (YWorkItem item : items) {
    engine.startWorkItem(item);
    engine.completeWorkItem(item, outputData, null);
}

// Persist case state
String caseXML = engine.unloadCase(runner.getCaseID());
persistToDatabase(caseXML);  // Your persistence layer
```

---

#### `marshalCase(YNetRunner runner) : String`

Export active case state without removing from engine.

**Parameters**:
- `runner` (YNetRunner): Case runner

**Returns**: Complete case state as XML string

**Throws**: `YStateException` if runner is null

**Difference from unloadCase**: Does not remove case from case monitor; case remains active in engine.

**Example**:

```java
String snapshot = engine.marshalCase(runner);
// Case still running, can continue processing
```

---

#### `restoreCase(String caseXML) : YNetRunner`

Import case state from XML (requires case monitoring enabled).

**Parameters**:
- `caseXML` (String): XML previously exported from `unloadCase()`

**Returns**: Restored YNetRunner at same execution point

**Throws**:
- `YSyntaxException`: If embedded specification XML malformed
- `YStateException`: If case state cannot be restored

**Use Case**: Restore case from external storage (Redis, database, S3) to continue processing.

**Example**:

```java
YStatelessEngine engine = new YStatelessEngine(60000);

// Retrieve persisted case from storage
String caseXML = retrieveFromDatabase("case-001");

// Restore to running state
YNetRunner runner = engine.restoreCase(caseXML);

// Continue processing
Set<YWorkItem> items = runner.getEnabledWorkItems();
for (YWorkItem item : items) {
    // Process work items...
}
```

---

### Case Monitoring

#### `setCaseMonitoringEnabled(boolean enable) : void`

Enable or disable case monitoring without idle timeout.

**Parameters**:
- `enable` (Boolean): true = enable, false = disable

**Effect**: When enabled, cases are tracked in memory; when disabled, case tracking is discarded.

**Example**:

```java
engine.setCaseMonitoringEnabled(true);  // Track cases
```

---

#### `setCaseMonitoringEnabled(boolean enable, long idleTimeout) : void`

Enable case monitoring with idle timeout configuration.

**Parameters**:
- `enable` (Boolean): true = enable, false = disable
- `idleTimeout` (Long): Milliseconds before idle timeout event; use ≤0 to disable timeout

**Timeout Announcement**: When a case remains idle (no work item progress) for the configured duration, a `CASE_IDLE_TIMEOUT` event is announced to all registered `YCaseEventListener` instances.

**Example**:

```java
// Enable case monitoring with 5-minute idle timeout
engine.setCaseMonitoringEnabled(true, 300000);

// Cases idle >5 min will trigger listeners
```

---

#### `setIdleCaseTimer(long msecs) : void`

Update idle timeout for all monitored cases.

**Parameters**:
- `msecs` (Long): New timeout in milliseconds; ≤0 disables timeout

**Effect**: If monitoring enabled, updates timeout; if disabled and msecs > 0, starts monitoring.

**Example**:

```java
// Change timeout to 2 minutes
engine.setIdleCaseTimer(120000);
```

---

#### `isCaseMonitoringEnabled() : boolean`

Query if case monitoring is currently enabled.

**Returns**: true if monitoring enabled, false otherwise

**Example**:

```java
if (engine.isCaseMonitoringEnabled()) {
    System.out.println("Case monitoring is active");
}
```

---

#### `isIdleCase(YIdentifier caseID) : boolean`

Check if a case is currently idle (not executing).

**Parameters**:
- `caseID` (YIdentifier): Case to check

**Returns**: true if case idle, false if executing

**Throws**: `YStateException` if case unknown or monitoring disabled

**Example**:

```java
if (engine.isIdleCase(runner.getCaseID())) {
    System.out.println("Case is idle, safe to unload");
} else {
    System.out.println("Case is executing, wait before unload");
}
```

---

#### `isIdleCase(YNetRunner runner) : boolean`

Check if a case is idle (overload using runner).

**Parameters**:
- `runner` (YNetRunner): Case runner

**Returns**: true if idle, false if executing

---

#### `isIdleCase(YWorkItem workItem) : boolean`

Check if a case is idle (overload using work item).

**Parameters**:
- `workItem` (YWorkItem): Work item to check parent case

**Returns**: true if idle, false if executing

---

### Event Listener Management

#### `addCaseEventListener(YCaseEventListener listener) : void`

Register a listener for case lifecycle events.

**Parameters**:
- `listener` (YCaseEventListener): Callback object implementing `handleCaseEvent(YCaseEvent)`

**Events Announced**:
- `CASE_STARTED`: Case initialized
- `CASE_COMPLETED`: Case finished
- `CASE_CANCELLED`: Case cancelled
- `CASE_SUSPENDED`: Case paused
- `CASE_RESUMED`: Case resumed
- `CASE_IDLE_TIMEOUT`: Case idle duration exceeded
- `CASE_UNLOADED`: Case exported via unloadCase()
- `CASE_RESTORED`: Case imported via restoreCase()

**Example**:

```java
engine.addCaseEventListener(event -> {
    if (event.getEventType() == YEventType.CASE_COMPLETED) {
        System.out.println("Case " + event.getRunner().getCaseID() + " completed");
    }
});
```

---

#### `addWorkItemEventListener(YWorkItemEventListener listener) : void`

Register a listener for work item lifecycle events.

**Parameters**:
- `listener` (YWorkItemEventListener): Callback object implementing `handleWorkItemEvent(YWorkItemEvent)`

**Events Announced**:
- `ITEM_ENABLED`: Work item enabled
- `ITEM_STARTED`: Work item execution started
- `ITEM_COMPLETED`: Work item completed
- `ITEM_SUSPENDED`: Work item paused
- `ITEM_RESUMED`: Work item resumed
- `ITEM_CANCELLED`: Work item cancelled
- `ITEM_DEADLOCKED`: Work item deadlocked
- `ITEM_FAILED`: Work item failed

**Example**:

```java
engine.addWorkItemEventListener(event -> {
    if (event.getEventType() == YEventType.ITEM_ENABLED) {
        System.out.println("Item enabled: " + event.getWorkItem().getTaskID());
    }
});
```

---

#### `addExceptionEventListener(YExceptionEventListener listener) : void`

Register a listener for exception and error events.

**Parameters**:
- `listener` (YExceptionEventListener): Callback implementing `handleException(...)`

**Example**:

```java
engine.addExceptionEventListener((caseID, taskID, itemID, exception) -> {
    System.err.println("Exception in case " + caseID + ": " + exception.getMessage());
});
```

---

#### `addLogEventListener(YLogEventListener listener) : void`

Register a listener for audit log events.

**Parameters**:
- `listener` (YLogEventListener): Callback implementing `handleLogEvent(...)`

---

#### `addTimerEventListener(YTimerEventListener listener) : void`

Register a listener for timer expiry events.

**Parameters**:
- `listener` (YTimerEventListener): Callback implementing `handleTimerExpiry(...)`

---

#### Removal Methods

```java
void removeCaseEventListener(YCaseEventListener listener);
void removeWorkItemEventListener(YWorkItemEventListener listener);
void removeExceptionEventListener(YExceptionEventListener listener);
void removeLogEventListener(YLogEventListener listener);
void removeTimerEventListener(YTimerEventListener listener);
```

All removal methods throw `IllegalArgumentException` if listener not found.

---

### Event Announcement Configuration

#### `enableMultiThreadedAnnouncements(boolean enable) : void`

Enable or disable multi-threaded event delivery.

**Parameters**:
- `enable` (Boolean): true = deliver events on thread pool, false = deliver synchronously

**Default**: Synchronous (false)

**Use**: Enable for high-throughput scenarios where listener latency would block case execution.

**Example**:

```java
engine.enableMultiThreadedAnnouncements(true);  // Async event delivery
```

---

#### `isMultiThreadedAnnouncementsEnabled() : boolean`

Query current announcement mode.

**Returns**: true if multi-threaded, false if synchronous

---

### Event Store Integration

#### `wireEventStore(WorkflowEventStore eventStore) : void`

Connect an event store to receive all case and work item events.

**Parameters**:
- `eventStore` (WorkflowEventStore): Event store implementation (e.g., Redis, PostgreSQL)

**Behavior**: Automatically registers a `WorkflowEventStoreListener` that appends immutable events to the store.

**Idempotency**: Multiple calls with same store instance only register once; different stores register multiple listeners.

**Use Case**: Stream case events to observability platform (Datadog, New Relic, etc.).

**Example**:

```java
// Custom event store implementation
WorkflowEventStore eventStore = new RedisEventStore(redisClient);

// Wire to engine
engine.wireEventStore(eventStore);

// All case/item events automatically recorded
```

---

### Engine Management

#### `getEngineNbr() : int`

Get the internal engine instance number.

**Returns**: Sequential number assigned at engine creation

**Use**: For distinguishing multiple engine instances in logs.

**Example**:

```java
System.out.println("Engine #" + engine.getEngineNbr());
```

---

## Configuration Options

### Construction-Time Configuration

| Option | Method | Type | Default | Purpose |
|--------|--------|------|---------|---------|
| Idle Timeout | `YStatelessEngine(long msecs)` | Long | N/A | Enable case monitoring with timeout |
| No Timeout | `YStatelessEngine()` | N/A | N/A | Minimal construction, no monitoring |

### Runtime Configuration

| Option | Method | Type | Default | Notes |
|--------|--------|------|---------|-------|
| Case Monitoring | `setCaseMonitoringEnabled(bool)` | Boolean | false | Track active cases in memory |
| Idle Timeout | `setIdleCaseTimer(long)` | Long | -1 (disabled) | Idle detection in milliseconds |
| Multi-threaded Events | `enableMultiThreadedAnnouncements(bool)` | Boolean | false | Async event delivery |
| Event Store | `wireEventStore(...)` | Object | None | External event persistence |

### Environment Variables (Optional)

| Variable | Type | Example | Effect |
|----------|------|---------|--------|
| `YAWL_ENGINE_TELEMETRY` | Boolean | `true` | Enable OpenTelemetry tracing |
| `YAWL_ENGINE_LOGLEVEL` | String | `DEBUG` | Log level for engine operations |
| `YAWL_VIRTUAL_THREAD_PREFIX` | String | `case-` | Prefix for virtual thread names |

---

## State Management

### Case State Lifecycle

```
┌──────────┐
│ LAUNCHED │
└────┬─────┘
     │
     ▼
┌──────────────┐
│ PROCESSING   │ (Work items enabled/executing)
│ (In Memory)  │
└─────┬────────┘
      │
      ├─────────────────────┐
      │                     │
      ▼                     ▼
┌──────────────┐    ┌──────────────┐
│ UNLOADED     │    │ COMPLETED    │
│ (Persisted)  │    └──────────────┘
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ RESTORED     │
│ (In Memory)  │
└──────────────┘
```

### Export (Unload)

```java
// Case is running
YNetRunner runner = engine.launchCase(spec, "case-001");
engine.startWorkItem(item);

// Capture state before completing
String caseXML = engine.unloadCase(runner.getCaseID());

// XML contains:
// - Specification (embedded)
// - All case variables
// - All work item states
// - Timer information
// - Marking (net conditions, task states)
```

### Import (Restore)

```java
// Later or on different engine instance
YStatelessEngine engine2 = new YStatelessEngine(60000);
YNetRunner restored = engine2.restoreCase(caseXML);

// Case continues at same execution point
// All listeners receive CASE_RESTORED event
```

---

## Listener Architecture

### Listener Interfaces

**YCaseEventListener**:

```java
public interface YCaseEventListener {
    void handleCaseEvent(YCaseEvent event);
}
```

**YWorkItemEventListener**:

```java
public interface YWorkItemEventListener {
    void handleWorkItemEvent(YWorkItemEvent event);
}
```

**YExceptionEventListener**:

```java
public interface YExceptionEventListener {
    void handleException(YIdentifier caseID, String taskID,
                        String workItemID, Throwable exception);
}
```

### Event Object Structure

**YCaseEvent**:

```java
public class YCaseEvent {
    YEventType getEventType();        // CASE_STARTED, etc.
    YNetRunner getRunner();            // Case runner
    Instant getTimestamp();            // Event time
}
```

**YWorkItemEvent**:

```java
public class YWorkItemEvent {
    YEventType getEventType();        // ITEM_ENABLED, etc.
    YWorkItem getWorkItem();           // Work item
    Instant getTimestamp();            // Event time
}
```

### Listener Registration Pattern

```java
// Implement listener
public class MyListener implements YCaseEventListener {
    @Override
    public void handleCaseEvent(YCaseEvent event) {
        switch (event.getEventType()) {
            case CASE_STARTED -> System.out.println("Case started");
            case CASE_COMPLETED -> System.out.println("Case completed");
            case CASE_IDLE_TIMEOUT -> System.out.println("Case idle");
            default -> {}
        }
    }
}

// Register with engine
MyListener listener = new MyListener();
engine.addCaseEventListener(listener);

// Clean up
engine.removeCaseEventListener(listener);
```

---

## Integration Patterns

### Pattern 1: Kubernetes Stateless Deployment

**Architecture**: Multiple pod replicas, each with fresh engine instance. Case state persisted to PostgreSQL.

```java
// Pod startup
public class KubernetesController {
    private final YStatelessEngine engine;
    private final CaseRepository caseRepo;

    public KubernetesController(CaseRepository repo) {
        this.engine = new YStatelessEngine(120000);  // 2 min idle timeout
        this.caseRepo = repo;

        // Auto-save idle cases
        engine.addCaseEventListener(event -> {
            if (event.getEventType() == YEventType.CASE_IDLE_TIMEOUT) {
                try {
                    String xml = engine.unloadCase(event.getRunner().getCaseID());
                    caseRepo.save(xml);
                } catch (Exception e) {
                    log.error("Failed to unload case", e);
                }
            }
        });
    }

    public void handleRequest(WorkflowRequest req) throws Exception {
        // Load spec
        YSpecification spec = engine.unmarshalSpecification(req.specXML);

        // Launch or restore
        YNetRunner runner;
        if (req.caseID != null && caseRepo.exists(req.caseID)) {
            String xml = caseRepo.get(req.caseID);
            runner = engine.restoreCase(xml);
        } else {
            runner = engine.launchCase(spec, req.caseID, req.initData);
        }

        // Process work items
        processWorkItems(runner, req);

        // For long-running cases, unload and save
        if (!isComplete(runner)) {
            String xml = engine.unloadCase(runner.getCaseID());
            caseRepo.save(xml);
        }
    }
}
```

---

### Pattern 2: AWS Lambda Stateless Execution

**Architecture**: Lambda functions process one work item per invocation; state passed via event payload.

```java
public class LambdaHandler implements RequestHandler<WorkflowEvent, WorkflowResponse> {
    private final YStatelessEngine engine = new YStatelessEngine();

    @Override
    public WorkflowResponse handleRequest(WorkflowEvent event, Context context) {
        try {
            // Deserialize case state from event
            String caseXML = event.caseState;
            YSpecification spec = engine.unmarshalSpecification(event.specXML);

            // Restore case
            YNetRunner runner = engine.restoreCase(caseXML);

            // Find next enabled item
            YWorkItem item = runner.getEnabledWorkItems().stream()
                .filter(it -> it.getTaskID().equals(event.taskID))
                .findFirst()
                .orElseThrow();

            // Execute work item
            engine.startWorkItem(item);
            String result = processItem(item, event.itemData);
            engine.completeWorkItem(item, result, null);

            // Serialize updated state
            String updatedXML = engine.marshalCase(runner);

            return new WorkflowResponse(
                runner.getCaseID().toString(),
                updatedXML,
                isComplete(runner)
            );
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return new WorkflowResponse(null, null, false);
        }
    }
}
```

---

### Pattern 3: Spring Boot REST API

**Architecture**: REST endpoints for case operations; in-memory engine with database persistence.

```java
@RestController
@RequestMapping("/api/cases")
public class CaseController {
    private final YStatelessEngine engine;
    private final CaseService caseService;

    @PostMapping
    public ResponseEntity<CaseResponse> launchCase(@RequestBody CaseRequest req) {
        try {
            YSpecification spec = engine.unmarshalSpecification(req.specXML);
            YNetRunner runner = engine.launchCase(spec, req.caseID, req.initData);

            return ResponseEntity.ok(new CaseResponse(
                runner.getCaseID().toString(),
                runner.getEnabledWorkItems().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{caseID}")
    public ResponseEntity<CaseResponse> getCase(@PathVariable String caseID) {
        try {
            String xml = caseService.getCaseXML(caseID);
            YStatelessEngine tempEngine = new YStatelessEngine();
            YNetRunner runner = tempEngine.restoreCase(xml);

            return ResponseEntity.ok(new CaseResponse(
                caseID,
                runner.getEnabledWorkItems().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{caseID}/items/{itemID}/complete")
    public ResponseEntity<Void> completeItem(
            @PathVariable String caseID,
            @PathVariable String itemID,
            @RequestBody WorkItemData data) {
        try {
            String xml = caseService.getCaseXML(caseID);
            YNetRunner runner = engine.restoreCase(xml);

            YWorkItem item = findItem(runner, itemID);
            engine.startWorkItem(item);
            engine.completeWorkItem(item, data.outputXML, null);

            // Persist updated state
            String updated = engine.marshalCase(runner);
            caseService.saveCaseXML(caseID, updated);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}
```

---

## Error Handling

### Exception Hierarchy

```
YAWLException (root)
├── YStateException
│   └── (case/work item state errors)
├── YEngineStateException
│   └── (engine not running, etc.)
├── YDataStateException
│   └── (variable initialization failures)
├── YQueryException
│   └── (data mapping query errors)
├── YSyntaxException
│   └── (specification XML parsing)
└── YConnectivityException
    └── (service availability errors)
```

### Exception Recovery Patterns

**Pattern 1: Graceful Degradation**

```java
try {
    YWorkItem completed = engine.completeWorkItem(item, outputData, null);
} catch (YDataStateException e) {
    // Data validation failed
    log.warn("Completing with empty data", e);
    engine.completeWorkItem(item, "<data/>", null);
}
```

**Pattern 2: Explicit Rollback**

```java
YNetRunner runner = engine.launchCase(spec, caseID, initData);
try {
    processCase(runner, engine);
} catch (YStateException e) {
    log.error("Case processing failed, rolling back", e);
    engine.cancelCase(runner);
    throw e;
}
```

**Pattern 3: Idempotent Completion**

```java
public YWorkItem completeItemIdempotently(
        YNetRunner runner,
        String itemID,
        String outputData) throws Exception {

    YWorkItem item = findItem(runner, itemID);

    return switch (item.getStatus()) {
        case Executing -> engine.completeWorkItem(item, outputData, null);
        case Completed -> item;  // Already done
        case Suspended -> {
            engine.unsuspendWorkItem(item);
            yield engine.completeWorkItem(item, outputData, null);
        }
        default -> throw new IllegalStateException("Cannot complete: " + item.getStatus());
    };
}
```

---

### Error Codes and Solutions

| Code | Message | Cause | Recovery |
|------|---------|-------|----------|
| `CASE_UNKNOWN` | Case not found | Unloaded or never existed | Check case ID, restore from storage |
| `ITEM_INVALID_STATE` | Work item cannot transition | Already completed/cancelled | Query current state, don't retry |
| `DATA_VALIDATION_FAILED` | Variable fails schema validation | Invalid output data format | Correct data XML, reattempt |
| `QUERY_MALFORMED` | XPath query invalid | Specification has syntax error | Validate spec, rebuild |
| `ENGINE_NOT_RUNNING` | Engine stopped | Engine shutdown | Create new engine instance |
| `SPEC_PARSE_ERROR` | XML is malformed | Invalid specification XML | Validate XML, check syntax |

---

## Performance Characteristics

### Memory Profile

**Per-Case (Loaded)**:
- Base: ~2-5 KB
- Plus variables: ~variable size
- Plus work items: ~1 KB per enabled/executing item
- **Total for 100-item case**: ~50-150 KB

**Per-Case (Unloaded)**:
- Persisted as XML: 10-100 KB (gzip: 1-10 KB)
- Out-of-process storage (Redis/DB)

### Throughput

| Scenario | Latency | Throughput |
|----------|---------|-----------|
| Case launch (cold) | 50-200 ms | 5-20 cases/sec/engine |
| Work item complete | 10-50 ms | 20-100 items/sec/engine |
| Case unload | 20-100 ms | 10-50 cases/sec/engine |
| Case restore | 30-150 ms | 7-30 cases/sec/engine |

**Scaling**: Horizontal via stateless engines + external case storage.

### Virtual Thread Performance

**Parallel Case Launch** (launchCasesParallel):
- 10 cases: ~500 ms (vs. 1000-2000 ms sequential)
- 100 cases: ~2-3 seconds (vs. 10-20 seconds sequential)
- Scales to thousands of concurrent operations per engine

**Virtual Thread Overhead**: ~100 μs per thread creation (vs. ~1000 μs for platform threads).

---

## Complete Working Example

```java
import org.yawlfoundation.yawl.stateless.*;
import org.yawlfoundation.yawl.stateless.engine.*;
import org.yawlfoundation.yawl.stateless.elements.*;
import org.yawlfoundation.yawl.stateless.listener.*;
import org.yawlfoundation.yawl.stateless.listener.event.*;
import org.yawlfoundation.yawl.exceptions.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

public class StatelessEngineExample {

    public static void main(String[] args) throws Exception {
        // 1. Create engine with case monitoring
        YStatelessEngine engine = new YStatelessEngine(60000);

        // 2. Register listeners
        engine.addCaseEventListener(event -> {
            System.out.println("[CASE] " + event.getEventType() +
                             ": " + event.getRunner().getCaseID());
        });

        engine.addWorkItemEventListener(event -> {
            System.out.println("[ITEM] " + event.getEventType() +
                             ": " + event.getWorkItem().getTaskID());
        });

        // 3. Load specification
        String specXML = Files.readString(Paths.get("workflow.yawl"));
        YSpecification spec = engine.unmarshalSpecification(specXML);
        System.out.println("Loaded: " + spec.getSpecificationID());

        // 4. Launch case with initial data
        String initData = """
            <root>
                <variable>initial value</variable>
            </root>
            """;
        YNetRunner runner = engine.launchCase(spec, "example-001", initData);
        System.out.println("Launched case: " + runner.getCaseID());

        // 5. Process all enabled work items
        processAllItems(engine, runner);

        // 6. Export case state
        String caseXML = engine.unloadCase(runner.getCaseID());
        System.out.println("Unloaded case (length: " + caseXML.length() + " bytes)");

        // 7. Restore case from XML
        YNetRunner restored = engine.restoreCase(caseXML);
        System.out.println("Restored case: " + restored.getCaseID());

        System.out.println("Complete");
    }

    private static void processAllItems(YStatelessEngine engine, YNetRunner runner)
            throws Exception {

        Set<YWorkItem> items = runner.getEnabledWorkItems();
        while (!items.isEmpty()) {
            for (YWorkItem item : items) {
                System.out.println("Processing: " + item.getTaskID());

                // Start item
                YWorkItem executing = engine.startWorkItem(item);

                // Complete with output data
                String output = """
                    <data>
                        <result>processed</result>
                    </data>
                    """;
                engine.completeWorkItem(executing, output, null);
            }

            // Refresh enabled items for next iteration
            items = runner.getEnabledWorkItems();
        }

        System.out.println("All items processed");
    }
}
```

---

## Summary

The **YStatelessEngine** API provides a complete, production-ready interface for executing YAWL workflows in modern distributed systems. Key strengths:

- **Stateless**: External state management enables horizontal scaling
- **Simple**: Intuitive API mirrors traditional engine with fewer operations
- **Observable**: Comprehensive listener architecture for monitoring
- **Resilient**: Full case state marshaling for durability
- **Performant**: Virtual thread support for high concurrency

For migration from stateful engines or adoption in new Kubernetes deployments, this reference provides all necessary method signatures, configuration options, and integration patterns.

---

**Document Version**: 6.0.0
**Last Updated**: 2026-02-28
**Approver**: YAWL Foundation Engineering
