# YAWL v6.0.0 REST API Reference

## Overview

The YAWL REST API provides comprehensive workflow management capabilities for the YAWL (Yet Another Workflow Language) Workflow Engine. This API enables client applications to interact with the engine for:

- Session management and authentication
- Work item lifecycle operations (checkout, checkin, complete, suspend)
- Case management and control (launch, cancel, state queries)
- Workflow specification management (load, unload, query)
- Event subscriptions and notifications (Interface E)
- Extended operations - exception handling, task suspension (Interface X)

## Version Information

| Component | Version |
|-----------|---------|
| YAWL Engine | 6.0.0 |
| Java Runtime | Java 25 LTS |
| API Protocol | REST over HTTP/1.1 |
| Data Formats | JSON, XML |

## Base URL

```
http://localhost:8080/yawl/api
```

## Java 25 Performance Features

YAWL v6.0.0 leverages Java 25 features for optimal performance:

| Feature | Benefit | JVM Flag |
|---------|---------|----------|
| Compact Object Headers | 5-10% throughput gain, -4-8 bytes/object | `-XX:+UseCompactObjectHeaders` |
| Virtual Threads | Thousands of concurrent cases without thread pool exhaustion | Automatic (StructuredTaskScope) |
| Generational ZGC | Ultra-low pause times for large heaps | `-XX:+UseZGC -XX:ZGenerational=true` |
| Scoped Values | Context propagation replaces ThreadLocal | Internal use |
| Pattern Matching | Exhaustive switch on sealed event types | Language feature |

## Authentication

All API endpoints require a valid session handle obtained through the `/ib/connect` endpoint. The session handle must be passed as a query parameter in all subsequent requests.

### Authentication Flow

1. **Connect to engine** (POST `/ib/connect`)
2. **Receive session handle** - Base64-encoded session token
3. **Use session handle** in subsequent requests via `sessionHandle` query parameter
4. **Disconnect when done** (POST `/ib/disconnect`)

### Example Authentication

```bash
# Connect
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"userid": "admin", "password": "admin"}'

# Response: {"sessionHandle": "H4sIAAAAAAAAA..."}
```

---

## Interface B: Client Operations

Interface B provides standard operations for work item and case management by client applications. Implements WfMC interfaces 2+3 (Workflow client applications and invoked applications).

**Reference**: See `/docs/reference/interface-b.md` for complete protocol documentation.

### Session Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ib/connect` | POST | Authenticate and establish session |
| `/ib/disconnect` | POST | Terminate session |
| `/ib/checkConnection` | GET | Validate session handle |

#### Connect to Engine

**Endpoint:** `POST /ib/connect`

**Request Body:**
```json
{
  "userid": "string",
  "password": "string"
}
```

**Response (200 OK):**
```json
{
  "sessionHandle": "string"
}
```

**Error Responses:**
- `400 Bad Request`: Missing userid or password
- `401 Unauthorized`: Invalid credentials
- `500 Internal Server Error`: Connection failed

---

### Work Item Operations

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ib/workitems` | GET | Get all live work items |
| `/ib/workitems/{itemId}` | GET | Get specific work item |
| `/ib/workitems/{itemId}/checkout` | POST | Start work item execution |
| `/ib/workitems/{itemId}/checkin` | POST | Update work item data |
| `/ib/workitems/{itemId}/complete` | POST | Complete work item |
| `/ib/workitems/{itemId}/suspend` | POST | Suspend work item |
| `/ib/workitems/{itemId}/unsuspend` | POST | Resume suspended work item |
| `/ib/workitems/{itemId}/rollback` | POST | Roll back to fired state |

#### Get All Live Work Items

**Endpoint:** `GET /ib/workitems`

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```json
[
  {
    "id": 12345,
    "specIdentifier": "ProcurementProcess",
    "specVersion": "1.0",
    "caseID": "CASE001",
    "taskID": "ApproveRequest",
    "taskName": "Approve Purchase Request",
    "status": "Executing",
    "resourceStatus": "Started",
    "enablementTimeMs": "2026-02-16T14:30:00Z",
    "startTimeMs": "2026-02-16T14:35:00Z",
    "timerStatus": "Active",
    "timerExpiry": 1739726100000
  }
]
```

#### Complete Work Item

**Endpoint:** `POST /ib/workitems/{itemId}/complete`

**Request Body (XML):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <status>APPROVED</status>
</data>
```

**Response (200 OK):**
```json
{
  "status": "completed"
}
```

---

### Case Operations

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ib/cases` | GET | Get all running cases |
| `/ib/cases/{caseId}` | GET | Get case data |
| `/ib/cases/{caseId}/cancel` | POST | Cancel case |
| `/ib/cases/{caseId}/state` | GET | Get case state |
| `/ib/cases/{caseId}/workitems` | GET | Get work items for case |
| `/ib/specifications/{specId}/cases` | POST | Launch new case |

#### Launch Case

**Endpoint:** `POST /ib/specifications/{specIdentifier}/versions/{specVersion}/cases`

**Path Parameters:**
- `specIdentifier`: Specification identifier
- `specVersion`: Specification version

**Query Parameters:**
- `specuri` (required): Specification URI
- `sessionHandle` (required): Valid session handle
- `caseid` (optional): Custom case ID
- `completionObserverURI` (optional): Callback URL for case completion

**Request Body (XML):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<caseParams>
  <requestAmount>10000.00</requestAmount>
  <requestor>John Smith</requestor>
</caseParams>
```

**Response (200 OK):**
```json
{
  "caseID": "CASE001"
}
```

---

### Specification Operations

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ib/specifications` | GET | List loaded specifications |
| `/ib/specifications/{specId}` | GET | Get specification definition |
| `/ib/specifications/{specId}/data` | GET | Get specification data schema |
| `/ib/specifications/{specId}/tasks/{taskId}` | GET | Get task information |

---

## Interface E: Event Operations

Interface E provides event subscription and process log queries for workflow monitoring and auditing.

**Reference**: See `/docs/reference/interface-e.md` for complete protocol documentation.

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ie/specifications` | GET | Get all logged specifications |
| `/ie/specifications/{specKey}/cases` | GET | Get cases for specification |
| `/ie/listeners` | GET | Get registered event listeners |

#### Get Logged Specifications

**Endpoint:** `GET /ie/specifications`

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```xml
<specifications>
  <specification>
    <specid>ProcurementProcess</specid>
    <version>1.0</version>
    <uri>http://example.com/specs/procurement</uri>
    <casecount>42</casecount>
  </specification>
</specifications>
```

#### Get Cases for Specification

**Endpoint:** `GET /ie/specifications/{specKey}/cases`

**Path Parameters:**
- `specKey`: Numeric specification log key

**Response (200 OK):**
```xml
<cases>
  <case>
    <caseid>CASE001</caseid>
    <starttime>2026-02-16T10:00:00Z</starttime>
    <status>Completed</status>
  </case>
</cases>
```

---

## Interface X: Extended Operations

Interface X provides advanced engine operations including exception handling, work item suspension, and data updates.

**Reference**: See `/docs/reference/interface-x.md` for complete protocol documentation.

### Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/ix/workitems/{itemId}/cancel` | POST | Cancel work item with exception |
| `/ix/workitems/{itemId}/suspend` | POST | Suspend work item |
| `/ix/workitems/{itemId}/resume` | POST | Resume suspended work item |
| `/ix/workitems/{itemId}/data` | PUT | Update work item data |
| `/ix/listeners` | POST | Register Interface X listener |
| `/ix/listeners` | DELETE | Remove Interface X listener |

#### Cancel Work Item

**Endpoint:** `POST /ix/workitems/{itemId}/cancel`

**Query Parameters:**
- `sessionHandle` (required): Valid session handle
- `fail` (optional): Mark as failure ("true"/"false", default: "false")

**Request Body (XML, optional):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<exceptionData>
  <reason>Business rule violation</reason>
  <code>BIZ001</code>
</exceptionData>
```

**Response (200 OK):**
```xml
<success>Work item cancelled successfully</success>
```

#### Suspend Work Item

**Endpoint:** `POST /ix/workitems/{itemId}/suspend`

**Response (200 OK):**
```xml
<success>Work item suspended</success>
```

#### Update Work Item Data

**Endpoint:** `PUT /ix/workitems/{itemId}/data`

**Request Body (XML):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<data>
  <lastModified>2026-02-18T10:00:00Z</lastModified>
  <modifiedBy>admin</modifiedBy>
</data>
```

**Response (200 OK):**
```xml
<success>Data updated successfully</success>
```

---

## Data Types

### WorkItemRecord

| Field | Type | Description |
|-------|------|-------------|
| id | integer | Internal Hibernate primary key |
| specIdentifier | string | Workflow specification identifier |
| specVersion | string | Workflow specification version |
| specURI | string | Specification URI |
| caseID | string | Case instance ID |
| taskID | string | Task definition ID |
| uniqueID | string | Unique work item identifier |
| taskName | string | Human-readable task name |
| documentation | string | Task documentation |
| status | string | Execution status (Enabled, Fired, Executing, Complete, etc.) |
| resourceStatus | string | Resourcing status (Offered, Allocated, Started, Suspended, etc.) |
| enablementTimeMs | string | ISO-8601 timestamp when work item was enabled |
| firingTimeMs | string | ISO-8601 timestamp when work item was fired |
| startTimeMs | string | ISO-8601 timestamp when work item execution started |
| completionTimeMs | string | ISO-8601 timestamp when work item completed |
| timerTrigger | string | Timer trigger type (OnEnabled, OnExecuting) |
| timerExpiry | long | Timer expiry epoch milliseconds |
| timerStatus | string | Timer status (Dormant, Active, Expired, Nil) |
| codelet | string | Codelet implementation class (if applicable) |
| customFormURL | string | Custom form URL (if applicable) |
| allowsDynamicCreation | boolean | Multi-instance dynamic creation allowed |
| requiresManualResourcing | boolean | Task requires manual resource allocation |

### Work Item Status Values

| Status | Description |
|--------|-------------|
| Enabled | Task is ready to be started |
| Fired | Task has been claimed but not yet started |
| Executing | Task is currently being performed |
| Complete | Task has finished normally |
| Failed | Task has finished with failure |
| ForcedComplete | Task was forcibly completed |
| Suspended | Task execution is paused |
| Deadlocked | Task cannot proceed due to net state |
| IsParent | Multi-instance parent work item |
| Deleted | Work item has been deleted |
| Discarded | Work item left in net when case completed |
| Withdrawn | Enabled work item was withdrawn |

### Case State Values

| State | Description |
|-------|-------------|
| Running | Case is actively executing |
| Completed | Case has finished normally |
| Cancelled | Case was cancelled |
| Suspended | Case execution is paused |
| Deadlocked | Case cannot proceed |

---

## Error Handling

All error responses follow a consistent format:

```json
{
  "error": "Error description",
  "code": "ERROR_CODE"
}
```

### HTTP Status Codes

| Status | Meaning | Typical Cause |
|--------|---------|---------------|
| 200 | OK | Successful operation |
| 400 | Bad Request | Invalid request parameters or data |
| 401 | Unauthorized | Invalid or missing session handle |
| 404 | Not Found | Resource (work item, case, specification) does not exist |
| 409 | Conflict | State conflict (e.g., invalid status transition) |
| 500 | Internal Server Error | Server error or operation failure |
| 501 | Not Implemented | Feature not yet implemented |
| 503 | Service Unavailable | Engine in redundant mode or initializing |

### Exception Types

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| YStateException | 400 | Invalid workflow state for operation |
| YDataStateException | 400 | Data validation or mapping error |
| YQueryException | 400 | XQuery evaluation error |
| YPersistenceException | 500 | Database persistence failure |
| YEngineStateException | 503 | Engine not in running state |
| YLogException | 500 | Logging system error |

---

## Java 25 Code Examples

### Virtual Thread Batch Processing

Process multiple work items concurrently using Java 25 virtual threads:

```java
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.Future;

public class BatchWorkItemProcessor {

    private final YawlClient client;

    public List<WorkItemResult> processBatch(List<String> workItemIds, String sessionHandle)
            throws Exception {

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            // Spawn virtual thread for each work item
            List<StructuredTaskScope.Subtask<WorkItemResult>> tasks = workItemIds.stream()
                .map(itemId -> scope.fork(() -> processItem(itemId, sessionHandle)))
                .toList();

            // Wait for all tasks and propagate first failure
            scope.join().throwIfFailed();

            // Collect results
            return tasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();
        }
    }

    private WorkItemResult processItem(String itemId, String sessionHandle) {
        // Each runs on its own virtual thread
        WorkItem item = client.getWorkItem(itemId, sessionHandle);
        client.checkout(itemId, sessionHandle);
        // ... process work item ...
        client.complete(itemId, outputData, sessionHandle);
        return new WorkItemResult(itemId, "completed");
    }
}
```

### Scoped Values for Context Propagation

Use Scoped Values instead of ThreadLocal for workflow context:

```java
import java.util.concurrent.ScopedValue;

public class WorkflowContext {

    // Define scoped values for context propagation
    public static final ScopedValue<String> WORKFLOW_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> CASE_ID = ScopedValue.newInstance();
    public static final ScopedValue<String> SECURITY_CONTEXT = ScopedValue.newInstance();

    public void executeInContext(String workflowId, String caseId, Runnable task) {
        // Bind values for the scope of this execution
        ScopedValue.where(WORKFLOW_ID, workflowId)
                   .where(CASE_ID, caseId)
                   .run(task);
    }
}
```

### Pattern Matching on Sealed Events

Exhaustive pattern matching on YAWL event types:

```java
sealed interface YEvent permits CaseStarted, CaseCompleted, WorkItemEnabled, WorkItemCompleted {
    String getCaseId();
    Instant getTimestamp();
}

record CaseStarted(String caseId, String specId, Instant timestamp) implements YEvent {}
record CaseCompleted(String caseId, String outputData, Instant timestamp) implements YEvent {}
record WorkItemEnabled(String caseId, String taskId, Instant timestamp) implements YEvent {}
record WorkItemCompleted(String caseId, String taskId, Instant timestamp) implements YEvent {}

public void handleEvent(YEvent event) {
    String logMessage = switch (event) {
        case CaseStarted cs -> "Case %s started for spec %s".formatted(cs.caseId(), cs.specId());
        case CaseCompleted cc -> "Case %s completed".formatted(cc.caseId());
        case WorkItemEnabled we -> "Task %s enabled in case %s".formatted(we.taskId(), we.caseId());
        case WorkItemCompleted wc -> "Task %s completed in case %s".formatted(wc.taskId(), wc.caseId());
    };
    logger.info(logMessage);
}
```

---

## Complete Example: Procurement Workflow

This example demonstrates a typical workflow execution through the REST API.

### 1. Connect to the Engine

```bash
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{"userid": "approver", "password": "password123"}'
```

### 2. Launch a New Case

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/specifications/ProcurementProcess/versions/1.0/cases?specuri=http://example.com/procurement&sessionHandle=SESSION_HANDLE" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<caseParams>
  <requestAmount>10000.00</requestAmount>
  <requestor>John Smith</requestor>
  <department>Finance</department>
</caseParams>'
```

### 3. Get Available Work Items

```bash
curl -X GET "http://localhost:8080/yawl/api/ib/workitems?sessionHandle=SESSION_HANDLE" \
  -H "Accept: application/json"
```

### 4. Check Out Work Item

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/checkout?sessionHandle=SESSION_HANDLE"
```

### 5. Complete Work Item

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/complete?sessionHandle=SESSION_HANDLE" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <status>APPROVED</status>
  <approver>Jane Doe</approver>
  <approvalDate>2026-02-18</approvalDate>
</data>'
```

### 6. Get Case State

```bash
curl -X GET "http://localhost:8080/yawl/api/ib/cases/CASE001/state?sessionHandle=SESSION_HANDLE"
```

### 7. Disconnect

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/disconnect?sessionHandle=SESSION_HANDLE"
```

---

## Client SDK Examples

### Java Client (JAX-RS)

```java
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import java.util.List;

public class YawlClient implements AutoCloseable {

    private static final String BASE_URL = "http://localhost:8080/yawl/api";
    private final Client client = ClientBuilder.newClient();
    private String sessionHandle;

    public void connect(String userid, String password) {
        WebTarget target = client.target(BASE_URL).path("ib/connect");
        String credentials = "{\"userid\":\"%s\",\"password\":\"%s\"}".formatted(userid, password);
        Response response = target.request()
            .post(Entity.json(credentials));

        if (response.getStatus() == 200) {
            JsonObject result = response.readEntity(JsonObject.class);
            this.sessionHandle = result.getString("sessionHandle");
        } else {
            throw new RuntimeException("Connection failed: " + response.getStatus());
        }
    }

    public List<WorkItemRecord> getWorkItems() {
        WebTarget target = client.target(BASE_URL)
            .path("ib/workitems")
            .queryParam("sessionHandle", sessionHandle);
        return target.request()
            .accept(MediaType.APPLICATION_JSON)
            .get(new GenericType<>() {});
    }

    public void checkout(String itemId) {
        WebTarget target = client.target(BASE_URL)
            .path("ib/workitems/{id}/checkout")
            .resolveTemplate("id", itemId)
            .queryParam("sessionHandle", sessionHandle);
        Response response = target.request().post(null);
        validateResponse(response);
    }

    public void complete(String itemId, String dataXml) {
        WebTarget target = client.target(BASE_URL)
            .path("ib/workitems/{id}/complete")
            .resolveTemplate("id", itemId)
            .queryParam("sessionHandle", sessionHandle);
        Response response = target.request()
            .post(Entity.entity(dataXml, MediaType.APPLICATION_XML));
        validateResponse(response);
    }

    public void disconnect() {
        if (sessionHandle != null) {
            WebTarget target = client.target(BASE_URL)
                .path("ib/disconnect")
                .queryParam("sessionHandle", sessionHandle);
            target.request().post(null);
            sessionHandle = null;
        }
    }

    @Override
    public void close() {
        disconnect();
        client.close();
    }

    private void validateResponse(Response response) {
        if (response.getStatus() >= 400) {
            throw new RuntimeException("Request failed: " + response.getStatus());
        }
    }
}
```

### Python Client

```python
import requests
from typing import Optional, List
from dataclasses import dataclass

@dataclass
class WorkItem:
    id: int
    case_id: str
    task_id: str
    task_name: str
    status: str

class YawlClient:
    BASE_URL = "http://localhost:8080/yawl/api"

    def __init__(self):
        self.session_handle: Optional[str] = None
        self.session = requests.Session()

    def connect(self, userid: str, password: str) -> bool:
        response = self.session.post(
            f"{self.BASE_URL}/ib/connect",
            json={"userid": userid, "password": password}
        )
        if response.status_code == 200:
            self.session_handle = response.json().get("sessionHandle")
            return True
        return False

    def get_work_items(self) -> List[WorkItem]:
        response = self.session.get(
            f"{self.BASE_URL}/ib/workitems",
            params={"sessionHandle": self.session_handle}
        )
        response.raise_for_status()
        return [WorkItem(**item) for item in response.json()]

    def checkout(self, item_id: str) -> None:
        response = self.session.post(
            f"{self.BASE_URL}/ib/workitems/{item_id}/checkout",
            params={"sessionHandle": self.session_handle}
        )
        response.raise_for_status()

    def complete(self, item_id: str, data: dict) -> None:
        import xml.etree.ElementTree as ET
        root = ET.Element("data")
        for key, value in data.items():
            child = ET.SubElement(root, key)
            child.text = str(value)
        data_xml = ET.tostring(root, encoding="unicode")

        response = self.session.post(
            f"{self.BASE_URL}/ib/workitems/{item_id}/complete",
            params={"sessionHandle": self.session_handle},
            data=data_xml,
            headers={"Content-Type": "application/xml"}
        )
        response.raise_for_status()

    def disconnect(self) -> None:
        if self.session_handle:
            self.session.post(
                f"{self.BASE_URL}/ib/disconnect",
                params={"sessionHandle": self.session_handle}
            )
            self.session_handle = None
```

---

## Integration with Swagger UI

The OpenAPI specification can be viewed and tested interactively using Swagger UI:

1. Access Swagger UI at: `http://localhost:8080/yawl/swagger-ui/`
2. Use the interactive interface to test endpoints
3. Authorize using the session handle

---

## References

- **Interface B Protocol**: `/docs/reference/interface-b.md`
- **Interface E Protocol**: `/docs/reference/interface-e.md`
- **Interface X Protocol**: `/docs/reference/interface-x.md`
- **YAWL Foundation**: https://www.yawlfoundation.org/
- **Documentation**: https://www.yawlfoundation.org/documentation
- **Java 25 Features**: `/docs/JAVA-25-FEATURES.md`
- **Architecture Patterns**: `/.claude/ARCHITECTURE-PATTERNS-JAVA25.md`

---

**Document Version**: 2.0
**YAWL Version**: 6.0.0
**Last Updated**: 2026-02-18
