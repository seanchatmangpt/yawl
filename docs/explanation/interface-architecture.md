# Interface Architecture: A, B, E, and X

> YAWL's four named interfaces (A, B, E, X) define the complete contract between the engine and the outside world; every external integration, every custom service, and every administrative tool communicates through exactly one of these interfaces — knowing which interface applies to a given change is the first thing to determine before writing any code.

## The Four Interfaces at a Glance

| Interface | Direction | Purpose |
|-----------|-----------|---------|
| **A** | External tools -> Engine | Design-time: load specifications, register services, manage users |
| **B** | Services <-> Engine | Runtime: launch cases, execute work items, query state |
| **E** | Engine -> Clients | Observability: query process logs and audit history |
| **X** | Engine <-> Exception Service | Extended: exception handling, constraint checking, resource alerts |

These interfaces are not abstract in the Java sense of the word. Each is a collection of related HTTP endpoints, client-side HTTP helpers, and server-side servlet handlers. The naming follows the WfMC (Workflow Management Coalition) interface numbering scheme that YAWL adopted as its starting point, though YAWL's implementations depart from the WfMC specifications significantly.

## Interface A: Design-Time Operations

**One-sentence purpose**: Interface A is how external tools submit workflow specifications to the engine and configure the engine's registry of services and users.

### What it contains

Interface A lives in `org.yawlfoundation.yawl.engine.interfce.interfaceA`. The package-info describes it as: "Client and server-side interfaces for specification loading, and service and application registration, information and removal."

The key classes:

- `InterfaceADesign` — a marker interface acknowledging WfMC Interface 1. It is intentionally empty; YAWL's actual design-time API uses its own XML format rather than WfMC APIs.
- `InterfaceA_EngineBasedServer` — the servlet that receives HTTP POST requests from design-time tools. Deployed at `/yawl/ia`. It wraps `EngineGatewayImpl` and dispatches on the `action` parameter.
- `InterfaceA_EnvironmentBasedClient` — the HTTP client used by external tools to call the engine. It provides typed Java methods over the raw HTTP protocol:

```java
// Specification management
client.uploadSpecification(String specXML, String sessionHandle)
client.unloadSpecification(YSpecificationID specID, String sessionHandle)
client.getLoadedSpecifications(String sessionHandle)

// Service registry
client.addYAWLService(YAWLServiceReference service, String sessionHandle)
client.removeYAWLService(String serviceURI, String sessionHandle)
client.getRegisteredYAWLServices(String sessionHandle)

// Session management
client.connect(String userID, String password)
client.disconnect(String handle)
```

- `InterfaceAManagement` — an interface (Java interface) defining the management operations that `EngineGatewayImpl` must implement.
- `InterfaceAManagementObserver` — a callback interface for receiving notifications after specification load.
- `InterfaceA_EnvironmentBasedClient` — the HTTP client with default endpoint `http://localhost:8080/yawl/ia`.

### Who calls Interface A and when

Interface A is called at design time and at engine startup. The YAWL Editor uploads specifications via `InterfaceA_EnvironmentBasedClient.uploadSpecification()`. Automated deployment scripts and CI pipelines use the same client. On engine startup, the engine replays specifications from its database — this is internal and does not go through the HTTP interface.

### What agents should know

If you are adding a new operation that configures the engine or manages the specification repository (loading, unloading, listing, versioning), that operation belongs in Interface A. The implementation goes in `EngineGatewayImpl` and the HTTP dispatch in `InterfaceA_EngineBasedServer`. The client-side method goes in `InterfaceA_EnvironmentBasedClient`.

If you are adding a new REST endpoint for the same design-time concern, the REST resource class `InterfaceARestResource` at `org.yawlfoundation.yawl.engine.interfce.rest` is the appropriate location.

## Interface B: Runtime Operations

**One-sentence purpose**: Interface B is how custom services and client applications interact with running cases — launching cases, receiving work items, completing work items, and querying live state.

### What it contains

Interface B lives in `org.yawlfoundation.yawl.engine.interfce.interfaceB`. The package-info describes it as handling "workflow client applications and invoked applications."

The core Java interface is `InterfaceBClient`, which declares the operations the engine must support at runtime:

```java
// Case management
String launchCase(YSpecificationID specID, String caseParams,
                  URI completionObserver, YLogDataItemList logData)
String allocateCaseID()

// Work item operations
YWorkItem startWorkItem(YWorkItem workItem, YClient client)
void completeWorkItem(YWorkItem workItem, String data,
                      String logPredicate, WorkItemCompletion flag)
void rollbackWorkItem(String workItemID)
YWorkItem suspendWorkItem(String workItemID)

// Queries
Set<YWorkItem> getAvailableWorkItems()
Set<YWorkItem> getAllWorkItems()
YWorkItem getWorkItem(String workItemID)
String getCaseData(String caseID)
YTask getTaskDefinition(YSpecificationID specificationID, String taskID)
```

The key classes implementing and using this interface:

- `InterfaceB_EngineBasedServer` — the servlet that receives callbacks from custom services. Deployed at `/yawl/ib`. Receives work item completions, case launch requests, and work item start notifications from external services.
- `InterfaceB_EnvironmentBasedClient` — the HTTP client that custom services use to call the engine. Enhanced in v5.2 with virtual thread-backed HTTP communication:

```java
// A custom service calls these to progress workflow
client.launchCase(specID, params, observer, handle)
client.startWorkItem(workItemID, handle)
client.completeWorkItem(workItemID, data, handle)
client.getCompleteListOfLiveWorkItems(handle)
```

- `InterfaceBWebsideController` — the abstract base class for all custom YAWL services. Every custom service extends this class. It holds a reference to `InterfaceB_EnvironmentBasedClient` as `_interfaceBClient`, and provides the abstract method that services must implement to handle work item announcements. The engine announces work items to services; services call back via the client to start and complete them.

- `InterfaceBClientObserver` — the callback interface. Custom services implement this to receive `workItemsForService()` callbacks when new work items are available.

- `InterfaceB_EngineBasedClient` — the engine's own client for calling back to registered services (the reverse direction: engine pushing announcements to services).

### The push/pull model

Interface B supports both push and pull:

- **Push**: The engine calls `InterfaceB_EngineBasedClient` to push new work item announcements to each registered service. This happens inside `YNetRunner.fireAtomicTask()` when a `YAnnouncement` is created and delivered.
- **Pull**: A service can call `InterfaceB_EnvironmentBasedClient.getCompleteListOfLiveWorkItems()` at any time to fetch all live work items. The Resource Service uses this on startup to re-synchronise after a restart.

### What agents should know

Any change that affects how cases are launched, how work items are offered to services, how work items are started and completed, or how live case state is queried belongs in Interface B. This includes:

- New case control operations (pause a case, set case priority).
- Changes to the work item announcement mechanism.
- New query operations over live work items.
- Changes to multi-instance work item handling.

The implementation lands in `EngineGatewayImpl` (which also implements `InterfaceBClient`), dispatched through `InterfaceB_EngineBasedServer`. The client-side method belongs in `InterfaceB_EnvironmentBasedClient`.

## Interface E: Log Queries

**One-sentence purpose**: Interface E provides read-only access to the engine's process event logs, enabling external tools to query case history, task execution records, and audit trails.

### What it contains

Interface E lives in `org.yawlfoundation.yawl.engine.interfce.interfaceE`. The package-info describes it as "Client and server-side interfaces for the engine process logs."

The key classes:

- `YLogGateway` — the servlet that handles log queries. Deployed at its own servlet endpoint. It wraps `YLogServer.getInstance()` and dispatches on the `action` parameter. Operations include retrieving net logs, task instance logs, case-level summaries, and XES-format exports.
- `YLogGatewayClient` — the HTTP client for querying logs. External tools such as the ProM process mining plugin use this client to retrieve logs in XES format.

Interface E is strictly read-only. It does not modify engine state. All operations are GET-equivalent: they take a session handle and return XML.

### What agents should know

If you are adding log query operations — new queries against the `YEventLogger` output, new export formats, new filtering criteria — the implementation belongs in `YLogGateway` and the client method in `YLogGatewayClient`. Do not add log write operations here; logging is driven internally by the engine and recorded through `YEventLogger.getInstance()`.

## Interface X: Exception Handling

**One-sentence purpose**: Interface X is the bidirectional channel between the engine and an external exception-handling service that intercepts case and work item events to enforce process-level constraints and handle exceptions.

### What it contains

Interface X lives in `org.yawlfoundation.yawl.engine.interfce.interfaceX`. The package-info describes it as "Client and server-side interfaces for exception handling."

The `InterfaceX_Service` interface defines what an exception service must implement. The engine calls these methods when specific events occur:

```java
// Called before and after case execution
void handleCheckCaseConstraintEvent(YSpecificationID specID, String caseID,
                                     String data, boolean precheck);

// Called before and after work item execution
void handleCheckWorkItemConstraintEvent(WorkItemRecord wir, String data, boolean precheck);

// Exception handlers — called when exceptions occur
String handleWorkItemAbortException(WorkItemRecord wir, String caseData);
void handleTimeoutEvent(WorkItemRecord wir, String taskList);
void handleResourceUnavailableException(String resourceID, WorkItemRecord wir,
                                         String caseData, boolean primary);
String handleConstraintViolationException(WorkItemRecord wir, String caseData);
void handleCaseCancellationEvent(String caseID);
```

The four infrastructure classes form a bidirectional channel:

- `InterfaceX_EngineSideClient` — the engine uses this to push exception events to the exception service.
- `InterfaceX_EngineSideServer` — the servlet on the engine side that receives responses from the exception service.
- `InterfaceX_ServiceSideServer` — the servlet on the exception service side that receives pushes from the engine.
- `InterfaceX_ServiceSideClient` — the client the exception service uses to call back to the engine.

`ExceptionGateway` is the servlet handling the engine side of Interface X. The announcer in `YNetRunner` checks `_announcer.hasInterfaceXListeners()` before calling Interface X methods:

```java
// YNetRunner.completeWorkItemInTask()
if (_announcer.hasInterfaceXListeners()) {
    _announcer.announceCheckWorkItemConstraints(
            workItem, _net.getInternalDataDocument(), false);
}
```

Interface X is also where case-level constraint checking occurs, called at case start and case end from `YNetRunner.announceCaseCompletion()`.

### What agents should know

Interface X is relevant whenever you need to intercept engine events for constraint enforcement, exception handling, or policy decisions. The Worklet Service (dynamic sub-process invocation for exception handling) uses Interface X to intercept work item exceptions and dynamically select an appropriate worklet specification to handle them.

If you are adding a new category of engine event that external services should be able to react to (a new exception type, a new pre/post condition checkpoint), it belongs in Interface X. This requires:

1. Adding the method to `InterfaceX_Service`.
2. Adding the announcement call in the appropriate engine method.
3. Adding the HTTP dispatch in `InterfaceX_EngineSideServer`.
4. Adding the client-side call in `InterfaceX_EngineSideClient`.

## The Boundary Between Interfaces

Agents frequently need to determine which interface applies to a proposed change. The decision tree:

1. **Does the change configure the engine or manage specifications?** -> Interface A.
2. **Does the change affect how running cases progress or how work items are processed?** -> Interface B.
3. **Does the change read or export event log data?** -> Interface E.
4. **Does the change intercept engine events for constraint checking or exception handling?** -> Interface X.

A change that spans interfaces (for example, launching a case — Interface B — while also applying a constraint check — Interface X) should implement each concern in the appropriate interface and coordinate through the engine's announcer mechanism, not by mixing interface concerns in a single endpoint.

## REST Layer

All four interfaces also expose REST endpoints via the classes in `org.yawlfoundation.yawl.engine.interfce.rest`:

- `InterfaceARestResource` — REST API for specification and service management.
- `InterfaceERestResource` — REST API for log queries.
- `InterfaceXRestResource` — REST API for exception handling queries.

Interface B does not have a dedicated REST resource class in this package because the existing `InterfaceB_EngineBasedServer` servlet handles the full protocol. The REST resources use the same `EngineGatewayImpl` backend as the servlet-based interfaces and enforce authentication through `YawlSecurityRestResource`.

## Class Reference Summary

| Interface | Server (engine-side servlet) | Client (caller-side HTTP client) | Key Java Interface/Abstract Class |
|-----------|------------------------------|----------------------------------|-----------------------------------|
| A | `InterfaceA_EngineBasedServer` | `InterfaceA_EnvironmentBasedClient` | `InterfaceADesign`, `InterfaceAManagement` |
| B (engine) | `InterfaceB_EngineBasedServer` | `InterfaceB_EnvironmentBasedClient` | `InterfaceBClient` |
| B (service) | `InterfaceBWebsideController` (abstract) | `InterfaceB_EngineBasedClient` | `InterfaceBClientObserver` |
| E | `YLogGateway` | `YLogGatewayClient` | (no Java interface; action-dispatched) |
| X | `InterfaceX_EngineSideServer`, `InterfaceX_ServiceSideServer` | `InterfaceX_EngineSideClient`, `InterfaceX_ServiceSideClient` | `InterfaceX_Service` |
