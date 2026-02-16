# YAWL REST API Reference

## Overview

The YAWL REST API provides comprehensive workflow management capabilities for the YAWL (Yet Another Workflow Language) v5.2 Workflow Engine. This API enables client applications to interact with the engine for:

- Session management and authentication
- Work item lifecycle operations
- Case management and control
- Workflow specification management
- Event subscriptions and notifications
- Extended operations (exception handling, task suspension)

## Base URL

```
http://localhost:8080/yawl/api
```

## Authentication

All API endpoints require a valid session handle obtained through the `/ib/connect` endpoint. The session handle must be passed as a query parameter in all subsequent requests.

### Example Authentication Flow

1. **Connect to engine** (POST `/ib/connect`)
   ```json
   {
     "userid": "admin",
     "password": "admin"
   }
   ```

2. **Receive session handle**
   ```json
   {
     "sessionHandle": "H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHdRYXVKZnFGZl5aZl1iUmlyRGlyRklySkllZkBOVoFGQWZxZklmcWlxSV5RZmlyTmZJZnFqUUVRZXFBSV1+SWlxSWZRSWVJUlFiSWlSaHF+SUpKZX1NZUlrUWlyCUVFWSmpxSWZxcWlySkxySk1mRSllSWrNJUVEqSX5+YUlySllQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlxSXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA"
   }
   ```

3. **Use session handle in subsequent requests**
   ```
   GET /ib/workitems?sessionHandle=H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlySV5RZmlyTmZJZnFqUUVRZXFASV1+SWlyRWZRSWVJUlRiSWlSaHl+SUpKZX1NZUlrUWlyClVFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlyRWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA
   ```

4. **Disconnect when done** (POST `/ib/disconnect`)

## Interface B: Client Operations

Interface B provides standard operations for work item and case management by client applications.

### Session Management

#### Connect to Engine

**Endpoint:** `POST /ib/connect`

Authenticates a user and establishes a session with the engine.

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

**Example:**
```bash
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{
    "userid": "admin",
    "password": "admin"
  }'
```

---

#### Disconnect from Engine

**Endpoint:** `POST /ib/disconnect`

Terminates the session with the engine.

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```json
{
  "status": "disconnected"
}
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Disconnect failed

**Example:**
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/disconnect?sessionHandle=SESSION_HANDLE"
```

---

### Work Item Operations

#### Get All Live Work Items

**Endpoint:** `GET /ib/workitems`

Retrieves all active work items in the engine.

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
    "enablementTimeMs": "2026-02-16 14:30:00",
    "startTimeMs": "2026-02-16 14:35:00"
  }
]
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Failed to get work items

**Example:**
```bash
curl -X GET "http://localhost:8080/yawl/api/ib/workitems?sessionHandle=SESSION_HANDLE" \
  -H "Accept: application/json"
```

---

#### Get Specific Work Item

**Endpoint:** `GET /ib/workitems/{itemId}`

Retrieves detailed information about a specific work item.

**Path Parameters:**
- `itemId` (required): Work item ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<workitem id="ITEM001">
  <taskid>ApproveRequest</taskid>
  <taskname>Approve Purchase Request</taskname>
  <caseid>CASE001</caseid>
  <status>Executing</status>
  <data>
    <amount>5000.00</amount>
    <vendor>Acme Corp</vendor>
    <description>Office Supplies</description>
  </data>
</workitem>
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing session handle
- `404 Not Found`: Work item not found
- `500 Internal Server Error`: Failed to get work item

**Example:**
```bash
curl -X GET "http://localhost:8080/yawl/api/ib/workitems/ITEM001?sessionHandle=SESSION_HANDLE" \
  -H "Accept: application/xml"
```

---

#### Check Out Work Item

**Endpoint:** `POST /ib/workitems/{itemId}/checkout`

Starts execution of a work item. Changes the work item status to "Started".

**Path Parameters:**
- `itemId` (required): Work item ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<workitem id="ITEM001">
  <taskid>ApproveRequest</taskid>
  <taskname>Approve Purchase Request</taskname>
  <caseid>CASE001</caseid>
  <status>Executing</status>
  <data>
    <amount>5000.00</amount>
    <vendor>Acme Corp</vendor>
    <description>Office Supplies</description>
  </data>
</workitem>
```

**Error Responses:**
- `400 Bad Request`: Failed to checkout work item
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Checkout failed

**Example:**
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/checkout?sessionHandle=SESSION_HANDLE"
```

---

#### Check In Work Item

**Endpoint:** `POST /ib/workitems/{itemId}/checkin`

Updates work item data without completing it. Useful for saving intermediate progress.

**Path Parameters:**
- `itemId` (required): Work item ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Request Body:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <description>Office Supplies - Approved</description>
  <approver>John Smith</approver>
</data>
```

**Response (200 OK):**
```json
{
  "status": "checked-in"
}
```

**Error Responses:**
- `400 Bad Request`: Failed to checkin work item
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Checkin failed

**Example:**
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/checkin?sessionHandle=SESSION_HANDLE" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <description>Office Supplies - Approved</description>
  <approver>John Smith</approver>
</data>'
```

---

#### Complete Work Item

**Endpoint:** `POST /ib/workitems/{itemId}/complete`

Completes a work item with final output data. Changes the work item status to "Complete".

**Path Parameters:**
- `itemId` (required): Work item ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Request Body:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <description>Office Supplies - Approved</description>
  <approver>John Smith</approver>
  <approvalDate>2026-02-16</approvalDate>
  <status>APPROVED</status>
</data>
```

**Response (200 OK):**
```json
{
  "status": "completed"
}
```

**Error Responses:**
- `400 Bad Request`: Failed to complete work item
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Completion failed

**Example:**
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/complete?sessionHandle=SESSION_HANDLE" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <description>Office Supplies - Approved</description>
  <approver>John Smith</approver>
  <approvalDate>2026-02-16</approvalDate>
  <status>APPROVED</status>
</data>'
```

---

### Case Operations

#### Get Work Items for Case

**Endpoint:** `GET /ib/cases/{caseId}/workitems`

Retrieves all work items associated with a particular case instance.

**Path Parameters:**
- `caseId` (required): Case ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```json
[
  {
    "id": 12345,
    "specIdentifier": "ProcurementProcess",
    "caseID": "CASE001",
    "taskID": "ApproveRequest",
    "taskName": "Approve Purchase Request",
    "status": "Executing",
    "resourceStatus": "Started"
  },
  {
    "id": 12346,
    "specIdentifier": "ProcurementProcess",
    "caseID": "CASE001",
    "taskID": "OrderItems",
    "taskName": "Order Items",
    "status": "Enabled",
    "resourceStatus": "Unresourced"
  }
]
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Failed to get work items

**Example:**
```bash
curl -X GET "http://localhost:8080/yawl/api/ib/cases/CASE001/workitems?sessionHandle=SESSION_HANDLE" \
  -H "Accept: application/json"
```

---

#### Get Case Data

**Endpoint:** `GET /ib/cases/{caseId}`

Retrieves detailed data for a specific case instance, including case variables and current state.

**Path Parameters:**
- `caseId` (required): Case ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<case id="CASE001">
  <specid>ProcurementProcess</specid>
  <status>Executing</status>
  <createtime>2026-02-15 10:00:00</createtime>
  <data>
    <requestAmount>10000.00</requestAmount>
    <vendor>Acme Corp</vendor>
    <approver>John Smith</approver>
    <status>PENDING_APPROVAL</status>
  </data>
  <workitems>
    <workitem id="ITEM001" status="Executing"/>
    <workitem id="ITEM002" status="Enabled"/>
  </workitems>
</case>
```

**Error Responses:**
- `401 Unauthorized`: Invalid or missing session handle
- `404 Not Found`: Case not found
- `500 Internal Server Error`: Failed to get case data

**Example:**
```bash
curl -X GET "http://localhost:8080/yawl/api/ib/cases/CASE001?sessionHandle=SESSION_HANDLE" \
  -H "Accept: application/xml"
```

---

#### Cancel Case

**Endpoint:** `POST /ib/cases/{caseId}/cancel`

Cancels an active case instance. All work items in the case will be terminated.

**Path Parameters:**
- `caseId` (required): Case ID

**Query Parameters:**
- `sessionHandle` (required): Valid session handle

**Response (200 OK):**
```json
{
  "status": "cancelled"
}
```

**Error Responses:**
- `400 Bad Request`: Failed to cancel case
- `401 Unauthorized`: Invalid or missing session handle
- `500 Internal Server Error`: Cancel failed

**Example:**
```bash
curl -X POST "http://localhost:8080/yawl/api/ib/cases/CASE001/cancel?sessionHandle=SESSION_HANDLE"
```

---

## Interface A: Design Operations

Interface A provides workflow specification management capabilities.

**Note:** Interface A endpoints are currently not implemented as REST endpoints. Use the `InterfaceA_EngineBasedServer` servlet instead.

### Planned Operations

- Upload specifications (`POST /ia/specifications`)
- Get loaded specifications (`GET /ia/specifications`)
- Unload specification (`DELETE /ia/specifications/{specId}`)

---

## Interface X: Extended Operations

Interface X provides advanced engine operations.

**Note:** Interface X endpoints are currently not implemented as REST endpoints. Use the `InterfaceX_EngineSideServer` servlet instead.

### Planned Operations

- Handle work item exceptions (`POST /ix/workitems/{itemId}/exceptions`)
- Suspend work item (`POST /ix/workitems/{itemId}/suspend`)
- Resume suspended work item (`POST /ix/workitems/{itemId}/resume`)

---

## Interface E: Event Operations

Interface E provides event subscription and notification capabilities.

**Note:** Interface E endpoints are currently not implemented as REST endpoints. Use the `YLogGateway` servlet instead.

### Planned Operations

- Subscribe to events (`POST /ie/subscriptions`)
- Get active subscriptions (`GET /ie/subscriptions`)
- Unsubscribe from events (`DELETE /ie/subscriptions/{subscriptionId}`)

---

## Error Handling

All error responses follow a consistent format:

```json
{
  "error": "Error description"
}
```

### Common HTTP Status Codes

| Status | Meaning | Typical Cause |
|--------|---------|---------------|
| 200 | OK | Successful operation |
| 400 | Bad Request | Invalid request parameters or data |
| 401 | Unauthorized | Invalid or missing session handle |
| 404 | Not Found | Resource (work item, case) does not exist |
| 500 | Internal Server Error | Server error or operation failure |
| 501 | Not Implemented | Feature not yet implemented |

---

## Data Types

### WorkItemRecord

Represents a task instance in a workflow case.

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
| allowsDynamicCreation | string | Whether task allows dynamic work item creation |
| requiresManualResourcing | string | Whether task requires manual resource allocation |
| codelet | string | Codelet implementation (if applicable) |
| status | string | Execution status (Enabled, Fired, Executing, Complete, etc.) |
| resourceStatus | string | Resourcing status (Offered, Allocated, Started, Suspended, etc.) |
| enablementTimeMs | string | Timestamp when work item was enabled |
| firingTimeMs | string | Timestamp when work item was fired |
| startTimeMs | string | Timestamp when work item execution started |
| completionTimeMs | string | Timestamp when work item completed |
| timerTrigger | string | Timer trigger value (if enabled) |
| timerExpiry | string | Timer expiry timestamp (if enabled) |

---

## Complete Example: Procurement Workflow

This example demonstrates a typical workflow execution through the REST API.

### 1. Connect to the Engine

```bash
curl -X POST http://localhost:8080/yawl/api/ib/connect \
  -H "Content-Type: application/json" \
  -d '{
    "userid": "approver",
    "password": "password123"
  }'
```

Response:
```json
{
  "sessionHandle": "H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlyRV5RZmiyTmZJZnFqUUVRZXFASV1+SWlxRWZRSWVIUlFiSWlSaHl+SUpKZX1NZUlrUWlyClUFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlySWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA"
}
```

### 2. Get All Available Work Items

```bash
curl -X GET "http://localhost:8080/yawl/api/ib/workitems?sessionHandle=H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlyRV5RZmiyTmZJZnFqUUVRZXFASV1+SWlxRWZRSWVIUlRiSWlSaHl+SUpKZX1NZUlrUWlyClUFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlySWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA" \
  -H "Accept: application/json"
```

Response:
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
    "resourceStatus": "Offered",
    "enablementTimeMs": "2026-02-16 14:30:00"
  }
]
```

### 3. Check Out the Work Item

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/checkout?sessionHandle=H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlyRV5RZmiyTmZJZnFqUUVRZXFASV1+SWlxRWZRSWVIUlRiSWlSaHl+SUpKZX1NZUlrUWlyClUFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlySWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA"
```

### 4. Get Work Item Details

```bash
curl -X GET "http://localhost:8080/yawl/api/ib/workitems/ITEM001?sessionHandle=H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlyRV5RZmiyTmZJZnFqUUVRZXFASV1+SWlxRWZRSWVIUlRiSWlSaHl+SUpKZX1NZUlrUWlyClUFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlySWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA" \
  -H "Accept: application/xml"
```

### 5. Complete the Work Item

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/workitems/ITEM001/complete?sessionHandle=H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlyRV5RZmiyTmZJZnFqUUVRZXFASV1+SWlxRWZRSWVIUlRiSWlSaHl+SUpKZX1NZUlrUWlyClUFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlySWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA" \
  -H "Content-Type: application/xml" \
  -d '<?xml version="1.0" encoding="UTF-8"?>
<data>
  <amount>5000.00</amount>
  <vendor>Acme Corp</vendor>
  <description>Office Supplies - Approved</description>
  <approver>John Smith</approver>
  <approvalDate>2026-02-16</approvalDate>
  <status>APPROVED</status>
</data>'
```

### 6. Disconnect from the Engine

```bash
curl -X POST "http://localhost:8080/yawl/api/ib/disconnect?sessionHandle=H4sIAAAAAAAAAKtWSkksSVSyUkopLUsB0oosSixJVLJSSiiNL8nPzy_JBKkpqMxLySwBsVNSUkpRSWIVoFNKUUlqUWpxZklJZkZqUWpxSWZxcWZRSklOTklKZm5ySmZiVmZOSlFiUmlyRnFxSklRUWpRSmZxSWZRSWVBeXlBUXVCUHwRYXVKZnFGZl5aZl1iUmiyRGiyRklySkllZkBOVoFGQWZxZklmcWlyRV5RZmiyTmZJZnFqUUVRZXFASV1+SWlxRWZRSWVIUlRiSWlSaHl+SUpKZX1NZUlrUWlyClUFWSmpxSWZxcWlySkzySk1mRSllSWrNJUVEqSX5+YUlySWlQWZ1QmZxZmpJZn5BaklmSXFBZmiyYkJKZnFxZkliSWlyYljpYkliSWpNJUVEqSX5+YUliSWpReUViSWlyRXlJS--9OLS0KDgxJBKsAgNg89CyAAAAA"
```

---

## Integration with Swagger UI

The OpenAPI specification can be viewed and tested interactively using Swagger UI:

1. Copy the `openapi.yaml` file to your web server
2. Access Swagger UI at: `http://localhost:8080/yawl/swagger-ui/`
3. Paste the OpenAPI spec URL when prompted
4. Use the interactive interface to test endpoints

---

## SDK/Client Library Examples

### Java Client Example

```java
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

public class YAWLClient {
    private static final String BASE_URL = "http://localhost:8080/yawl/api";
    private final Client client;
    private String sessionHandle;

    public YAWLClient() {
        this.client = ClientBuilder.newClient();
    }

    public void connect(String userid, String password) {
        WebTarget target = client.target(BASE_URL).path("ib/connect");
        String credentials = "{\"userid\":\"" + userid + "\",\"password\":\"" + password + "\"}";
        Response response = target.request().post(Entity.json(credentials));
        // Parse response to get sessionHandle
    }

    public List<WorkItemRecord> getWorkItems() {
        WebTarget target = client.target(BASE_URL)
            .path("ib/workitems")
            .queryParam("sessionHandle", sessionHandle);
        return target.request().get(new GenericType<List<WorkItemRecord>>() {});
    }

    public void disconnect() {
        WebTarget target = client.target(BASE_URL)
            .path("ib/disconnect")
            .queryParam("sessionHandle", sessionHandle);
        target.request().post(null);
    }
}
```

---

## Support and Further Information

For more information about YAWL and its capabilities, visit:
- **YAWL Foundation**: https://www.yawlfoundation.org/
- **Documentation**: https://www.yawlfoundation.org/documentation
- **Community**: https://www.yawlfoundation.org/community

---

**Document Version**: 1.0
**YAWL Version**: 5.2.0
**Last Updated**: 2026-02-16
