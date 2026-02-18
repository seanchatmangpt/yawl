# Interface B Protocol Reference

## Overview

Interface B is the primary client-facing API for YAWL, implementing WfMC (Workflow Management Coalition) interfaces 2 and 3:
- **Interface 2**: Workflow client applications
- **Interface 3**: Invoked applications

This document describes the complete Interface B protocol for YAWL v6.0.0.

## Protocol Details

| Property | Value |
|----------|-------|
| Base Path | `/ib` |
| Default Format | XML |
| Alternative Format | JSON |
| Authentication | Session handle via query parameter |

## Implementation Classes

| Class | Purpose |
|-------|---------|
| `InterfaceB_EngineBasedServer` | Servlet handling POST requests |
| `InterfaceBClient` | Java interface definition |
| `EngineGateway` | Facade delegating to YEngine |
| `EngineGatewayImpl` | Gateway implementation |

---

## Session Management

### connect

Authenticates a user and establishes a session with the engine.

**Request Format:**
```
POST /ib
Content-Type: application/x-www-form-urlencoded

action=connect&userid={userid}&password={password}[&encrypt={true|false}]
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "connect" |
| userid | string | Yes | User identifier |
| password | string | Yes | User password (plain or encrypted) |
| encrypt | boolean | No | If true, password is encrypted |

**Response (Success):**
```xml
<response>
  <sessionHandle>H4sIAAAAAAAAA...</sessionHandle>
</response>
```

**Response (Failure):**
```xml
<response>
  <failure>Invalid credentials</failure>
</response>
```

**Java Method:** `EngineGateway.connect(String userID, String password, long timeOutSeconds)`

---

### disconnect

Terminates the session with the engine.

**Request Format:**
```
POST /ib?action=disconnect&sessionHandle={handle}
```

**Response (Success):**
```xml
<response>
  <success>Session terminated</success>
</response>
```

**Java Method:** `EngineGateway.disconnect(String sessionHandle)`

---

### checkConnection

Validates a session handle.

**Request Format:**
```
POST /ib?action=checkConnection&sessionHandle={handle}
```

**Response (Valid):**
```xml
<response>
  <success>Connection valid</success>
</response>
```

**Response (Invalid):**
```xml
<response>
  <failure>Invalid or expired session</failure>
</response>
```

---

## Work Item Operations

### getLiveItems / getAvailableWorkItemIDs

Retrieves all active work items.

**Request Format:**
```
POST /ib?action=getLiveItems&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <workItems>
    <workItem>
      <id>12345</id>
      <specidentifier>ProcurementProcess</specidentifier>
      <specversion>1.0</specversion>
      <specuri>http://example.com/specs/procurement</specuri>
      <caseid>CASE001</caseid>
      <taskid>ApproveRequest</taskid>
      <taskname>Approve Purchase Request</taskname>
      <status>Executing</status>
      <resourcestatus>Started</resourcestatus>
      <enablementTimeMs>1739725800000</enablementTimeMs>
      <startTimeMs>1739726100000</startTimeMs>
      <allowsdynamiccreation>false</allowsdynamiccreation>
      <requiresmanualresourcing>true</requiresmanualresourcing>
    </workItem>
  </workItems>
</response>
```

**Java Method:** `EngineGateway.describeAllWorkItems(String sessionHandle)`

---

### getWorkItem

Retrieves a specific work item by ID.

**Request Format:**
```
POST /ib?action=getWorkItem&workItemID={itemId}&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <workItem id="ITEM001">
    <taskid>ApproveRequest</taskid>
    <taskname>Approve Purchase Request</taskname>
    <caseid>CASE001</caseid>
    <status>Executing</status>
    <data>
      <amount>5000.00</amount>
      <vendor>Acme Corp</vendor>
    </data>
  </workItem>
</response>
```

**Java Method:** `EngineGateway.getWorkItem(String workItemID, String sessionHandle)`

---

### checkout / startWorkItem

Starts execution of a work item. Changes status from Fired to Executing.

**Request Format:**
```
POST /ib?action=checkout&workItemID={itemId}&sessionHandle={handle}[&logPredicate={xml}]
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "checkout" |
| workItemID | string | Yes | Work item identifier |
| sessionHandle | string | Yes | Valid session handle |
| logPredicate | string | No | XML log predicate for starting |

**Response (Success):**
```xml
<response>
  <workItem id="ITEM001">
    <status>Executing</status>
    <starttime>1739726100000</starttime>
    <startedBy>admin</startedBy>
  </workItem>
</response>
```

**Response (Failure):**
```xml
<response>
  <failure>Work item is not in Fired status</failure>
</response>
```

**Java Method:** `EngineGateway.startWorkItem(String workItemID, String logPredicate, String sessionHandle)`

---

### checkin / completeWorkItem

Completes a work item with output data.

**Request Format:**
```
POST /ib?action=checkin&workItemID={itemId}&sessionHandle={handle}[&logPredicate={xml}][&data={xml}]
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| action | string | Yes | Must be "checkin" |
| workItemID | string | Yes | Work item identifier |
| sessionHandle | string | Yes | Valid session handle |
| data | string | No | XML output data |
| logPredicate | string | No | XML log predicate for completion |

**Response (Success):**
```xml
<response>
  <success>Work item completed</success>
</response>
```

**Java Method:** `EngineGateway.completeWorkItem(String workItemID, String data, String logPredicate, boolean force, String sessionHandle)`

---

### suspend

Suspends a work item.

**Request Format:**
```
POST /ib?action=suspend&workItemID={itemId}&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>Work item suspended</success>
</response>
```

**Java Method:** `EngineGateway.suspendWorkItem(String workItemID, String sessionHandle)`

---

### unsuspend

Resumes a suspended work item.

**Request Format:**
```
POST /ib?action=unsuspend&workItemID={itemId}&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>Work item resumed</success>
</response>
```

**Java Method:** `EngineGateway.unsuspendWorkItem(String workItemID, String sessionHandle)`

---

### rollback

Rolls back an executing work item to fired state.

**Request Format:**
```
POST /ib?action=rollback&workItemID={itemId}&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>Work item rolled back</success>
</response>
```

**Java Method:** `EngineGateway.rollbackWorkItem(String workItemID, String sessionHandle)`

---

### skip

Skips a work item (auto-completes with empty output).

**Request Format:**
```
POST /ib?action=skip&workItemID={itemId}&sessionHandle={handle}
```

**Java Method:** `EngineGateway.skipWorkItem(String workItemID, String sessionHandle)`

---

### createInstance

Creates a new instance of a multi-instance work item.

**Request Format:**
```
POST /ib?action=createInstance&workItemID={itemId}&paramValueForMICreation={value}&sessionHandle={handle}
```

**Java Method:** `EngineGateway.createNewInstance(String workItemID, String paramValueForMICreation, String sessionHandle)`

---

### getChildren

Gets child work items of a parent multi-instance item.

**Request Format:**
```
POST /ib?action=getChildren&workItemID={itemId}&sessionHandle={handle}
```

**Java Method:** `EngineGateway.getChildrenOfWorkItem(String workItemID, String sessionHandle)`

---

## Case Operations

### launchCase

Launches a new case instance from a specification.

**Request Format:**
```
POST /ib?action=launchCase
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &sessionHandle={handle}
     [&caseParams={xml}]
     [&caseid={customCaseId}]
     [&completionObserverURI={callbackUrl}]
     [&logData={xml}]
     [&mSec={delayMs} | start={timestamp} | wait={duration}]
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| specidentifier | string | Yes | Specification identifier |
| specversion | string | Yes | Specification version |
| specuri | string | Yes | Specification URI |
| sessionHandle | string | Yes | Valid session handle |
| caseParams | string | No | XML case input parameters |
| caseid | string | No | Custom case ID (non-persisting only) |
| completionObserverURI | string | No | Callback URL for completion |
| logData | string | No | XML log data |
| mSec | long | No | Delay launch by milliseconds |
| start | long | No | Start at epoch timestamp |
| wait | string | No | Duration string (e.g., "1h30m") |

**Response:**
```xml
<response>
  <CASE.12345</caseid>
</response>
```

**Java Method:** `EngineGateway.launchCase(YSpecificationID specID, String caseParams, URI completionObserverURI, ...)`

---

### cancelCase

Cancels a running case.

**Request Format:**
```
POST /ib?action=cancelCase&caseID={caseId}&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <success>Case cancelled</success>
</response>
```

**Java Method:** `EngineGateway.cancelCase(String caseID, String sessionHandle)`

---

### getAllRunningCases

Gets all running cases.

**Request Format:**
```
POST /ib?action=getAllRunningCases&sessionHandle={handle}
```

**Java Method:** `EngineGateway.getAllRunningCases(String sessionHandle)`

---

### getCaseState

Gets the current state of a case.

**Request Format:**
```
POST /ib?action=getCaseState&caseID={caseId}&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <casestate>
    <caseid>CASE001</caseid>
    <status>Running</status>
    <tasks>
      <task id="ApproveRequest" status="Executing"/>
      <task id="OrderItems" status="Enabled"/>
    </tasks>
    <netdata>
      <requestAmount>10000.00</requestAmount>
    </netdata>
  </casestate>
</response>
```

**Java Method:** `EngineGateway.getCaseState(String caseID, String sessionHandle)`

---

### getCaseData

Gets the net data of a case.

**Request Format:**
```
POST /ib?action=getCaseData&caseID={caseId}&sessionHandle={handle}
```

**Java Method:** `EngineGateway.getCaseData(String caseID, String sessionHandle)`

---

### getCasesForSpecification

Gets all cases for a specification.

**Request Format:**
```
POST /ib?action=getCasesForSpecification
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &sessionHandle={handle}
```

**Java Method:** `EngineGateway.getCasesForSpecification(YSpecificationID specID, String sessionHandle)`

---

### exportCaseState / importCases

Export/import case state for migration or backup.

**Export:**
```
POST /ib?action=exportCaseState&caseID={caseId}&sessionHandle={handle}
POST /ib?action=exportAllCaseStates&sessionHandle={handle}
```

**Import:**
```
POST /ib?action=importCases&xml={caseStateXml}&sessionHandle={handle}
```

---

## Specification Operations

### getSpecificationPrototypesList

Lists all loaded specifications.

**Request Format:**
```
POST /ib?action=getSpecificationPrototypesList&sessionHandle={handle}
```

**Response:**
```xml
<response>
  <specifications>
    <specification>
      <id>ProcurementProcess</id>
      <version>1.0</version>
      <uri>http://example.com/specs/procurement</uri>
    </specification>
  </specifications>
</response>
```

**Java Method:** `EngineGateway.getSpecificationList(String sessionHandle)`

---

### getSpecification

Gets the full specification definition.

**Request Format:**
```
POST /ib?action=getSpecification
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &sessionHandle={handle}
```

**Java Method:** `EngineGateway.getProcessDefinition(YSpecificationID specID, String sessionHandle)`

---

### getSpecificationData

Gets specification data schema.

**Request Format:**
```
POST /ib?action=getSpecificationData
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &sessionHandle={handle}
```

**Java Method:** `EngineGateway.getSpecificationData(YSpecificationID specID, String sessionHandle)`

---

### getSpecificationDataSchema

Gets the XSD schema for specification data.

**Request Format:**
```
POST /ib?action=getSpecificationDataSchema
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &sessionHandle={handle}
```

**Java Method:** `EngineGateway.getSpecificationDataSchema(YSpecificationID specID, String sessionHandle)`

---

### taskInformation

Gets task definition information.

**Request Format:**
```
POST /ib?action=taskInformation
     &specidentifier={identifier}
     &specversion={version}
     &specuri={uri}
     &taskID={taskId}
     &sessionHandle={handle}
```

**Java Method:** `EngineGateway.getTaskInformation(YSpecificationID specificationID, String taskID, String sessionHandle)`

---

## Query Operations

### getWorkItemsWithIdentifier

Gets work items by identifier (case or task).

**Request Format:**
```
POST /ib?action=getWorkItemsWithIdentifier
     &idType={case|task}
     &id={identifier}
     &sessionHandle={handle}
```

---

### getWorkItemsForService

Gets work items assigned to a service.

**Request Format:**
```
POST /ib?action=getWorkItemsForService
     &serviceuri={serviceUri}
     &sessionHandle={handle}
```

---

## Instance Summary Operations

### getCaseInstanceSummary

Gets summary of all case instances.

**Request Format:**
```
POST /ib?action=getCaseInstanceSummary&sessionHandle={handle}
```

---

### getWorkItemInstanceSummary

Gets work item instances for a case.

**Request Format:**
```
POST /ib?action=getWorkItemInstanceSummary
     &caseID={caseId}
     &sessionHandle={handle}
```

---

### getParameterInstanceSummary

Gets parameter instances for a work item.

**Request Format:**
```
POST /ib?action=getParameterInstanceSummary
     &caseID={caseId}
     &workItemID={itemId}
     &sessionHandle={handle}
```

---

## Error Handling

All Interface B errors follow the same response format:

**Error Response:**
```xml
<response>
  <failure>Error message describing the failure</failure>
</response>
```

### Common Error Conditions

| Condition | HTTP Status | Response |
|-----------|-------------|----------|
| Invalid session | 200 | `<failure>Invalid session handle</failure>` |
| Missing parameters | 200 | `<failure>Missing required parameter: X</failure>` |
| Invalid work item | 200 | `<failure>No work item found with id: X</failure>` |
| Invalid state transition | 200 | `<failure>Work item is not in correct status</failure>` |
| Data validation error | 200 | `<failure>Data validation failed: X</failure>` |

---

## Java API Reference

### InterfaceBClient Interface

```java
public interface InterfaceBClient {
    // Observer registration
    void registerInterfaceBObserver(InterfaceBClientObserver observer);
    void registerInterfaceBObserverGateway(ObserverGateway gateway) throws YAWLException;

    // Work item queries
    Set<YWorkItem> getAvailableWorkItems();
    Set<YWorkItem> getAllWorkItems();
    YWorkItem getWorkItem(String workItemID);

    // Work item operations
    YWorkItem startWorkItem(YWorkItem workItem, YClient client)
        throws YStateException, YDataStateException, YQueryException,
               YPersistenceException, YEngineStateException;
    void completeWorkItem(YWorkItem workItem, String data, String logPredicate,
                          WorkItemCompletion flag)
        throws YStateException, YDataStateException, YQueryException,
               YPersistenceException, YEngineStateException;
    void rollbackWorkItem(String workItemID)
        throws YStateException, YPersistenceException, YLogException;
    YWorkItem suspendWorkItem(String workItemID)
        throws YStateException, YPersistenceException, YLogException;

    // Case operations
    String launchCase(YSpecificationID specID, String caseParams,
                      URI completionObserver, YLogDataItemList logData)
        throws YStateException, YDataStateException, YPersistenceException,
               YEngineStateException, YLogException, YQueryException;
    String getCaseData(String caseID) throws YStateException;

    // Multi-instance
    void checkElegibilityToAddInstances(String workItemID) throws YStateException;
    YWorkItem createNewInstance(YWorkItem workItem, String paramValueForMICreation)
        throws YStateException, YPersistenceException;
    Set getChildrenOfWorkItem(YWorkItem workItem);

    // Task definition
    YTask getTaskDefinition(YSpecificationID specificationID, String taskID);
}
```

---

## References

- **YEngine Implementation**: `src/org/yawlfoundation/yawl/engine/YEngine.java`
- **EngineGateway**: `src/org/yawlfoundation/yawl/engine/interfce/EngineGateway.java`
- **InterfaceB_EngineBasedServer**: `src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceB_EngineBasedServer.java`
- **YWorkItem**: `src/org/yawlfoundation/yawl/engine/YWorkItem.java`
- **YNetRunner**: `src/org/yawlfoundation/yawl/engine/YNetRunner.java`

---

**Document Version**: 1.0
**YAWL Version**: 6.0.0
**Last Updated**: 2026-02-18
