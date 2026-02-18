# Interface E Protocol Reference

## Overview

Interface E provides event subscription and process log queries for workflow monitoring and auditing. It enables external systems to:

- Query historical process execution data
- Monitor case and work item lifecycle events
- Access the YAWL process log repository

This document describes the complete Interface E protocol for YAWL v6.0.0.

## Protocol Details

| Property | Value |
|----------|-------|
| Base Path | `/ie` |
| Default Format | XML |
| Authentication | Session handle via query parameter |

## Implementation Classes

| Class | Purpose |
|-------|---------|
| `InterfaceERestResource` | JAX-RS REST resource |
| `YLogServer` | Process log server implementation |
| `YEventLogger` | Event logging singleton |

---

## Endpoints

### GET /ie/specifications

Retrieves all specifications with their logged execution data.

**Request Format:**
```
GET /ie/specifications?sessionHandle={handle}
```

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Response (Success):**
```xml
<specifications>
  <specification>
    <id>12345</id>
    <specid>ProcurementProcess</specid>
    <version>1.0</version>
    <uri>http://example.com/specs/procurement</uri>
    <casecount>42</casecount>
    <created>2026-01-15T10:00:00Z</created>
  </specification>
  <specification>
    <id>12346</id>
    <specid>OrderFulfillment</specid>
    <version>2.0</version>
    <uri>http://example.com/specs/orders</uri>
    <casecount>128</casecount>
    <created>2026-02-01T08:30:00Z</created>
  </specification>
</specifications>
```

**Response (Unauthorized):**
```xml
<failure>Invalid or expired session handle</failure>
```

**HTTP Status Codes:**

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 401 | Invalid or missing session handle |
| 500 | Server error |

**Java Implementation:**
```java
@GET
@Path("/specifications")
public Response getLoggedSpecifications(@QueryParam("sessionHandle") String sessionHandle) {
    // Validates session, then delegates to YLogServer
    synchronized (YLogServer.getInstance().getPersistenceManager()) {
        boolean isLocalTransaction = YLogServer.getInstance().startTransaction();
        try {
            String result = YLogServer.getInstance().getAllSpecifications();
            return Response.ok(result).build();
        } finally {
            if (isLocalTransaction) {
                YLogServer.getInstance().commitTransaction();
            }
        }
    }
}
```

---

### GET /ie/specifications/{specKey}/cases

Retrieves all case instances for a specific specification.

**Request Format:**
```
GET /ie/specifications/{specKey}/cases?sessionHandle={handle}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| specKey | long | Yes | Numeric specification log key |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Response (Success):**
```xml
<cases>
  <case>
    <id>CASE.12345</id>
    <caseid>CASE001</caseid>
    <specid>12345</specid>
    <starttime>2026-02-15T10:00:00Z</starttime>
    <endtime>2026-02-15T14:30:00Z</endtime>
    <status>Completed</status>
    <duration>16200000</duration>
  </case>
  <case>
    <id>CASE.12346</id>
    <caseid>CASE002</caseid>
    <specid>12345</specid>
    <starttime>2026-02-16T09:00:00Z</starttime>
    <endtime>null</endtime>
    <status>Running</status>
    <duration>null</duration>
  </case>
</cases>
```

**Response (Invalid Key):**
```xml
<failure>Specification key must be a numeric log key</failure>
```

**HTTP Status Codes:**

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 400 | Invalid specification key format |
| 401 | Invalid or missing session handle |
| 500 | Server error |

**Java Implementation:**
```java
@GET
@Path("/specifications/{specKey}/cases")
public Response getCasesForSpecification(
        @PathParam("specKey") String specKey,
        @QueryParam("sessionHandle") String sessionHandle) {
    long key = Long.parseLong(specKey);
    synchronized (YLogServer.getInstance().getPersistenceManager()) {
        boolean isLocalTransaction = YLogServer.getInstance().startTransaction();
        try {
            String result = YLogServer.getInstance().getNetInstancesOfSpecification(key);
            return Response.ok(result).build();
        } finally {
            if (isLocalTransaction) {
                YLogServer.getInstance().commitTransaction();
            }
        }
    }
}
```

---

### GET /ie/listeners

Retrieves the active InterfaceX listeners (event observers) registered with the engine.

**Request Format:**
```
GET /ie/listeners?sessionHandle={handle}
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Response (Success):**
```xml
<services>
  <service>
    <uri>http://listener1.example.com:8080/yawl/ix</uri>
    <name>Exception Handler</name>
    <documentation>Handles workflow exceptions</documentation>
  </service>
  <service>
    <uri>http://monitor.example.com:8080/yawl/events</uri>
    <name>Event Monitor</name>
    <documentation>Real-time event monitoring</documentation>
  </service>
</services>
```

**HTTP Status Codes:**

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 401 | Invalid or missing session handle |
| 500 | Server error |

---

## YLogServer Methods

Interface E delegates to `YLogServer` for process log queries. Key methods include:

| Method | Description |
|--------|-------------|
| `getAllSpecifications()` | Get all logged specifications |
| `getNetInstancesOfSpecification(long specKey)` | Get cases for a specification |
| `getNetInstanceEvents(long caseKey)` | Get events for a case |
| `getTaskInstances(long caseKey)` | Get task instances for a case |
| `getTaskInstanceEvents(long taskKey)` | Get events for a task instance |

---

## Event Types

The YAWL process log captures the following event types:

### Case Events

| Event | Description |
|-------|-------------|
| `NetStarted` | Case instance created |
| `NetCompleted` | Case finished normally |
| `NetCancelled` | Case was cancelled |
| `NetSuspended` | Case suspended |
| `NetResumed` | Case resumed |

### Work Item Events

| Event | Description |
|-------|-------------|
| `WorkItemCreated` | Work item enabled |
| `WorkItemFired` | Work item claimed |
| `WorkItemStarted` | Work item execution started |
| `WorkItemCompleted` | Work item finished |
| `WorkItemSuspended` | Work item suspended |
| `WorkItemResumed` | Work item resumed |
| `WorkItemCancelled` | Work item cancelled |
| `WorkItemDeleted` | Work item deleted |
| `WorkItemWithdrawn` | Enabled work item withdrawn |

### Data Events

| Event | Description |
|-------|-------------|
| `DataValueChange` | Work item or case data updated |

---

## Log Predicate Support

Interface E supports log predicates for custom event data logging:

### Input Predicate Example
```xml
<logpredicate>
  <onStart>
    <dataquery>
      <name>RequestAmount</name>
      <query>/caseData/requestAmount</query>
      <type>decimal</type>
    </dataquery>
  </onStart>
</logpredicate>
```

### Output Predicate Example
```xml
<logpredicate>
  <onCompletion>
    <dataquery>
      <name>ApprovalStatus</name>
      <query>/outputData/status</query>
      <type>string</type>
    </dataquery>
  </onCompletion>
</logpredicate>
```

---

## Transaction Management

Interface E uses transaction management for log queries:

```java
// Transaction pattern used in all Interface E methods
synchronized (YLogServer.getInstance().getPersistenceManager()) {
    boolean isLocalTransaction = YLogServer.getInstance().startTransaction();
    try {
        // Perform log query
        String result = YLogServer.getInstance().someQuery(...);
        return Response.ok(result).build();
    } finally {
        if (isLocalTransaction) {
            YLogServer.getInstance().commitTransaction();
        }
    }
}
```

---

## REST vs Legacy Access

### REST API (Recommended)

The REST endpoints at `/ie/*` provide clean HTTP access with proper status codes.

### Legacy Servlet Access

For backward compatibility, process logs can also be accessed through:
- `YLogGateway` servlet

---

## Code Examples

### Java Client - Query Specifications

```java
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;

public class LogQueryClient {

    private static final String BASE_URL = "http://localhost:8080/yawl/api/ie";
    private final Client client = ClientBuilder.newClient();
    private String sessionHandle;

    public String getSpecifications() {
        WebTarget target = client.target(BASE_URL)
            .path("specifications")
            .queryParam("sessionHandle", sessionHandle);

        Response response = target.request()
            .accept(MediaType.APPLICATION_XML)
            .get();

        if (response.getStatus() == 200) {
            return response.readEntity(String.class);
        } else if (response.getStatus() == 401) {
            throw new RuntimeException("Invalid session");
        } else {
            throw new RuntimeException("Query failed: " + response.getStatus());
        }
    }

    public String getCasesForSpecification(long specKey) {
        WebTarget target = client.target(BASE_URL)
            .path("specifications/{specKey}/cases")
            .resolveTemplate("specKey", specKey)
            .queryParam("sessionHandle", sessionHandle);

        Response response = target.request()
            .accept(MediaType.APPLICATION_XML)
            .get();

        response.close();
        return response.readEntity(String.class);
    }
}
```

### Python Client - Query Cases

```python
import requests
from typing import List, Dict

class LogQueryClient:
    BASE_URL = "http://localhost:8080/yawl/api/ie"

    def __init__(self, session_handle: str):
        self.session_handle = session_handle

    def get_specifications(self) -> str:
        """Get all logged specifications."""
        response = requests.get(
            f"{self.BASE_URL}/specifications",
            params={"sessionHandle": self.session_handle}
        )
        response.raise_for_status()
        return response.text

    def get_cases_for_specification(self, spec_key: int) -> str:
        """Get all cases for a specification."""
        response = requests.get(
            f"{self.BASE_URL}/specifications/{spec_key}/cases",
            params={"sessionHandle": self.session_handle}
        )
        response.raise_for_status()
        return response.text

    def get_listeners(self) -> str:
        """Get registered event listeners."""
        response = requests.get(
            f"{self.BASE_URL}/listeners",
            params={"sessionHandle": self.session_handle}
        )
        response.raise_for_status()
        return response.text
```

---

## Integration Patterns

### Event-Driven Monitoring

Combine Interface E with Interface X for real-time monitoring:

1. **Interface X**: Register listener for event notifications
2. **Interface E**: Query historical data for analysis
3. **Correlation**: Match real-time events with historical patterns

### Audit Trail Generation

Use Interface E to generate compliance audit trails:

```java
public class AuditTrailGenerator {

    private final LogQueryClient logClient;

    public String generateAuditReport(String caseId, String sessionHandle) {
        StringBuilder report = new StringBuilder();

        // Get specification key for case
        long specKey = getSpecKeyForCase(caseId);

        // Get case events
        String caseEvents = logClient.getCasesForSpecification(specKey);

        // Parse and format audit report
        report.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        report.append("<auditTrail caseId=\"").append(caseId).append("\">\n");
        report.append(caseEvents);
        report.append("</auditTrail>");

        return report.toString();
    }
}
```

---

## Error Handling

| Error | HTTP Status | Response Body |
|-------|-------------|---------------|
| Invalid session | 401 | `<failure>Invalid or expired session handle</failure>` |
| Missing session | 401 | `<failure>Session handle is required</failure>` |
| Invalid spec key | 400 | `<failure>Specification key must be a numeric log key</failure>` |
| Server error | 500 | `<failure>Engine call failed: {message}</failure>` |

---

## References

- **InterfaceERestResource**: `src/org/yawlfoundation/yawl/engine/interfce/rest/InterfaceERestResource.java`
- **YLogServer**: `src/org/yawlfoundation/yawl/logging/YLogServer.java`
- **YEventLogger**: `src/org/yawlfoundation/yawl/logging/YEventLogger.java`
- **YLogDataItem**: `src/org/yawlfoundation/yawl/logging/YLogDataItem.java`

---

**Document Version**: 1.0
**YAWL Version**: 6.0.0
**Last Updated**: 2026-02-18
