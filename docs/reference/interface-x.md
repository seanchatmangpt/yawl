# Interface X Protocol Reference

## Overview

Interface X provides advanced engine operations for exception handling, work item lifecycle control, and data management. It extends the standard Interface B operations with capabilities needed by exception handling services and administrative tools.

Key capabilities:
- Work item cancellation with exception data
- Work item suspension and resumption
- Work item data updates without completion
- Interface X listener registration for event notifications

This document describes the complete Interface X protocol for YAWL v6.0.0.

## Protocol Details

| Property | Value |
|----------|-------|
| Base Path | `/ix` |
| Default Format | XML |
| Authentication | Session handle via query parameter |

## Implementation Classes

| Class | Purpose |
|-------|---------|
| `InterfaceXRestResource` | JAX-RS REST resource |
| `InterfaceX_EngineSideServer` | Legacy servlet handler |
| `InterfaceX_EngineSideClient` | Client implementation |
| `EngineGateway` | Facade delegating to YEngine |

---

## Endpoints

### POST /ix/workitems/{itemId}/cancel

Cancels a work item with optional exception data.

**Request Format:**
```
POST /ix/workitems/{itemId}/cancel?sessionHandle={handle}[&fail={true|false}]
Content-Type: application/xml

<exceptionData>
  <reason>Business rule violation</reason>
  <code>BIZ001</code>
  <details>Amount exceeds approval threshold</details>
</exceptionData>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| itemId | string | Yes | Work item identifier |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| sessionHandle | string | Yes | - | Valid session handle |
| fail | string | No | "false" | Mark cancellation as failure |

**Request Body (Optional):**

XML data to capture with the cancellation. Useful for recording exception details.

**Response (Success):**
```xml
<success>Work item cancelled successfully</success>
```

**Response (Failure - Invalid Session):**
```xml
<failure>Session handle is required</failure>
```

**Response (Failure - Invalid Item):**
```xml
<failure>Work item not found: {itemId}</failure>
```

**HTTP Status Codes:**

| Status | Meaning |
|--------|---------|
| 200 | Success (includes both success and failure responses) |
| 400 | Missing required parameter |
| 401 | Invalid or missing session handle |
| 500 | Server error |

**Java Implementation:**
```java
@POST
@Path("/workitems/{itemId}/cancel")
public Response cancelWorkItem(
        @PathParam("itemId") String itemId,
        @QueryParam("sessionHandle") String sessionHandle,
        @QueryParam("fail") @DefaultValue("false") String fail,
        String data) {

    // Validate parameters
    if (sessionHandle == null || sessionHandle.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("<failure>Session handle is required</failure>")
                .build();
    }
    if (itemId == null || itemId.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Work item ID is required</failure>")
                .build();
    }

    try {
        String result = getEngine().cancelWorkItem(itemId, data, fail, sessionHandle);
        return Response.ok(result).build();
    } catch (RemoteException e) {
        throw new IllegalStateException("Engine call failed: " + e.getMessage(), e);
    }
}
```

---

### POST /ix/workitems/{itemId}/suspend

Suspends a work item, pausing its execution.

**Request Format:**
```
POST /ix/workitems/{itemId}/suspend?sessionHandle={handle}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| itemId | string | Yes | Work item identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Response (Success):**
```xml
<success>Work item suspended</success>
```

**Response (Failure):**
```xml
<failure>Work item cannot be suspended in current state: {status}</failure>
```

**Valid States for Suspension:**
- Enabled
- Fired
- Executing

**Java Implementation:**
```java
@POST
@Path("/workitems/{itemId}/suspend")
public Response suspendWorkItem(
        @PathParam("itemId") String itemId,
        @QueryParam("sessionHandle") String sessionHandle) {

    if (sessionHandle == null || sessionHandle.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("<failure>Session handle is required</failure>")
                .build();
    }
    if (itemId == null || itemId.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Work item ID is required</failure>")
                .build();
    }

    try {
        String result = getEngine().suspendWorkItem(itemId, sessionHandle);
        return Response.ok(result).build();
    } catch (RemoteException e) {
        throw new IllegalStateException("Engine call failed: " + e.getMessage(), e);
    }
}
```

---

### POST /ix/workitems/{itemId}/resume

Resumes a suspended work item.

**Request Format:**
```
POST /ix/workitems/{itemId}/resume?sessionHandle={handle}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| itemId | string | Yes | Work item identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Response (Success):**
```xml
<success>Work item resumed</success>
```

**Response (Failure):**
```xml
<failure>Work item is not in suspended state</failure>
```

**Java Implementation:**
```java
@POST
@Path("/workitems/{itemId}/resume")
public Response resumeWorkItem(
        @PathParam("itemId") String itemId,
        @QueryParam("sessionHandle") String sessionHandle) {

    if (sessionHandle == null || sessionHandle.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("<failure>Session handle is required</failure>")
                .build();
    }
    if (itemId == null || itemId.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Work item ID is required</failure>")
                .build();
    }

    try {
        String result = getEngine().unsuspendWorkItem(itemId, sessionHandle);
        return Response.ok(result).build();
    } catch (RemoteException e) {
        throw new IllegalStateException("Engine call failed: " + e.getMessage(), e);
    }
}
```

---

### PUT /ix/workitems/{itemId}/data

Updates work item data without completing the item. Useful for intermediate saves.

**Request Format:**
```
PUT /ix/workitems/{itemId}/data?sessionHandle={handle}
Content-Type: application/xml

<data>
  <lastModified>2026-02-18T10:00:00Z</lastModified>
  <modifiedBy>admin</modifiedBy>
  <progress>50</progress>
  <notes>Work in progress - pending review</notes>
</data>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| itemId | string | Yes | Work item identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Request Body:**

XML data to merge with existing work item data.

**Response (Success):**
```xml
<success>Data updated successfully</success>
```

**Response (Failure):**
```xml
<failure>Data XML is required</failure>
```

**HTTP Status Codes:**

| Status | Meaning |
|--------|---------|
| 200 | Success |
| 400 | Missing required parameter or data |
| 401 | Invalid or missing session handle |
| 500 | Server error |

**Java Implementation:**
```java
@PUT
@Path("/workitems/{itemId}/data")
public Response updateWorkItemData(
        @PathParam("itemId") String itemId,
        @QueryParam("sessionHandle") String sessionHandle,
        String data) {

    if (sessionHandle == null || sessionHandle.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("<failure>Session handle is required</failure>")
                .build();
    }
    if (itemId == null || itemId.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Work item ID is required</failure>")
                .build();
    }
    if (data == null || data.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Data XML is required</failure>")
                .build();
    }

    String result = getEngine().updateWorkItemData(itemId, data, sessionHandle);
    return Response.ok(result).build();
}
```

---

### POST /ix/listeners

Registers an Interface X listener URI to receive event notifications.

**Request Format:**
```
POST /ix/listeners?sessionHandle={handle}
Content-Type: text/plain

http://listener.example.com:8080/yawl/ix/callback
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |

**Request Body:**

Plain text URI of the listener endpoint.

**Response (Success):**
```xml
<success>Listener registered successfully</success>
```

**Response (Failure):**
```xml
<failure>Listener URI is required</failure>
```

**Java Implementation:**
```java
@POST
@Path("/listeners")
@Consumes(MediaType.TEXT_PLAIN)
public Response addListener(
        @QueryParam("sessionHandle") String sessionHandle,
        String listenerUri) {

    if (sessionHandle == null || sessionHandle.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("<failure>Session handle is required</failure>")
                .build();
    }
    if (listenerUri == null || listenerUri.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Listener URI is required</failure>")
                .build();
    }

    String result = getEngine().addInterfaceXListener(listenerUri);
    return Response.ok(result).build();
}
```

---

### DELETE /ix/listeners

Removes a registered Interface X listener.

**Request Format:**
```
DELETE /ix/listeners?sessionHandle={handle}&uri={listenerUri}
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionHandle | string | Yes | Valid session handle |
| uri | string | Yes | Listener URI to remove |

**Response (Success):**
```xml
<success>Listener removed successfully</success>
```

**Response (Failure):**
```xml
<failure>Listener URI is required</failure>
```

**Java Implementation:**
```java
@DELETE
@Path("/listeners")
public Response removeListener(
        @QueryParam("sessionHandle") String sessionHandle,
        @QueryParam("uri") String listenerUri) {

    if (sessionHandle == null || sessionHandle.isEmpty()) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity("<failure>Session handle is required</failure>")
                .build();
    }
    if (listenerUri == null || listenerUri.isEmpty()) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity("<failure>Listener URI is required</failure>")
                .build();
    }

    String result = getEngine().removeInterfaceXListener(listenerUri);
    return Response.ok(result).build();
}
```

---

## EngineGateway Methods

Interface X delegates to the following `EngineGateway` methods:

| Method | Description |
|--------|-------------|
| `cancelWorkItem(String id, String data, String fail, String sessionHandle)` | Cancel with exception data |
| `suspendWorkItem(String workItemID, String sessionHandle)` | Suspend work item |
| `unsuspendWorkItem(String workItemID, String sessionHandle)` | Resume work item |
| `updateWorkItemData(String workItemID, String data, String sessionHandle)` | Update data |
| `addInterfaceXListener(String observerURI)` | Register listener |
| `removeInterfaceXListener(String observerURI)` | Remove listener |
| `updateCaseData(String caseID, String data, String sessionHandle)` | Update case data |
| `restartWorkItem(String workItemID, String sessionHandle)` | Restart work item |

---

## Listener Callback Protocol

When an Interface X listener is registered, the engine sends HTTP POST requests to the listener URI for various events.

### Event Notification Format

```xml
<event>
  <type>WorkItemException</type>
  <timestamp>2026-02-18T10:00:00Z</timestamp>
  <workitemid>ITEM001</workitemid>
  <caseid>CASE001</caseid>
  <taskid>ApproveRequest</taskid>
  <data>
    <exception>Business rule violation</exception>
  </data>
</event>
```

### Event Types

| Event Type | Description |
|------------|-------------|
| `WorkItemException` | Work item raised an exception |
| `WorkItemTimeout` | Work item timer expired |
| `CaseException` | Case-level exception occurred |
| `ConstraintViolation` | Data constraint check failed |

### Listener Response

Listeners should respond with:
- HTTP 200 for successful processing
- HTTP 500 for errors (engine may retry)

---

## Code Examples

### Java Client - Exception Handling

```java
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

public class ExceptionHandlingClient {

    private static final String BASE_URL = "http://localhost:8080/yawl/api/ix";
    private final Client client = ClientBuilder.newClient();
    private String sessionHandle;

    public void cancelWorkItemWithException(String itemId, String reason, String code) {
        WebTarget target = client.target(BASE_URL)
            .path("workitems/{id}/cancel")
            .resolveTemplate("id", itemId)
            .queryParam("sessionHandle", sessionHandle)
            .queryParam("fail", "true");

        String exceptionData = """
            <exceptionData>
              <reason>%s</reason>
              <code>%s</code>
              <timestamp>%s</timestamp>
            </exceptionData>
            """.formatted(reason, code, Instant.now().toString());

        Response response = target.request()
            .post(Entity.entity(exceptionData, MediaType.APPLICATION_XML));

        if (response.getStatus() != 200) {
            throw new RuntimeException("Cancel failed: " + response.readEntity(String.class));
        }
    }

    public void suspendWorkItem(String itemId) {
        WebTarget target = client.target(BASE_URL)
            .path("workitems/{id}/suspend")
            .resolveTemplate("id", itemId)
            .queryParam("sessionHandle", sessionHandle);

        Response response = target.request().post(null);
        response.close();
    }

    public void resumeWorkItem(String itemId) {
        WebTarget target = client.target(BASE_URL)
            .path("workitems/{id}/resume")
            .resolveTemplate("id", itemId)
            .queryParam("sessionHandle", sessionHandle);

        Response response = target.request().post(null);
        response.close();
    }

    public void updateWorkItemData(String itemId, Map<String, String> data) {
        WebTarget target = client.target(BASE_URL)
            .path("workitems/{id}/data")
            .resolveTemplate("id", itemId)
            .queryParam("sessionHandle", sessionHandle);

        StringBuilder xml = new StringBuilder("<data>");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            xml.append("<%s>%s</%s>".formatted(entry.getKey(), entry.getValue(), entry.getKey()));
        }
        xml.append("</data>");

        Response response = target.request()
            .put(Entity.entity(xml.toString(), MediaType.APPLICATION_XML));
        response.close();
    }

    public void registerListener(String listenerUri) {
        WebTarget target = client.target(BASE_URL)
            .path("listeners")
            .queryParam("sessionHandle", sessionHandle);

        Response response = target.request()
            .post(Entity.entity(listenerUri, MediaType.TEXT_PLAIN));
        response.close();
    }
}
```

### Python Client - Work Item Control

```python
import requests
from typing import Optional
from datetime import datetime

class InterfaceXClient:
    BASE_URL = "http://localhost:8080/yawl/api/ix"

    def __init__(self, session_handle: str):
        self.session_handle = session_handle
        self.session = requests.Session()

    def cancel_work_item(
        self,
        item_id: str,
        reason: Optional[str] = None,
        code: Optional[str] = None,
        fail: bool = False
    ) -> bool:
        """Cancel a work item with optional exception data."""
        url = f"{self.BASE_URL}/workitems/{item_id}/cancel"
        params = {
            "sessionHandle": self.session_handle,
            "fail": str(fail).lower()
        }

        if reason or code:
            data = f"""<?xml version="1.0" encoding="UTF-8"?>
            <exceptionData>
              <reason>{reason or ''}</reason>
              <code>{code or ''}</code>
              <timestamp>{datetime.utcnow().isoformat()}Z</timestamp>
            </exceptionData>"""
        else:
            data = None

        response = self.session.post(
            url,
            params=params,
            data=data,
            headers={"Content-Type": "application/xml"}
        )
        response.raise_for_status()
        return "<success>" in response.text

    def suspend_work_item(self, item_id: str) -> bool:
        """Suspend a work item."""
        url = f"{self.BASE_URL}/workitems/{item_id}/suspend"
        params = {"sessionHandle": self.session_handle}

        response = self.session.post(url, params=params)
        response.raise_for_status()
        return "<success>" in response.text

    def resume_work_item(self, item_id: str) -> bool:
        """Resume a suspended work item."""
        url = f"{self.BASE_URL}/workitems/{item_id}/resume"
        params = {"sessionHandle": self.session_handle}

        response = self.session.post(url, params=params)
        response.raise_for_status()
        return "<success>" in response.text

    def update_work_item_data(self, item_id: str, data: dict) -> bool:
        """Update work item data without completing."""
        url = f"{self.BASE_URL}/workitems/{item_id}/data"
        params = {"sessionHandle": self.session_handle}

        # Build XML from dict
        import xml.etree.ElementTree as ET
        root = ET.Element("data")
        for key, value in data.items():
            child = ET.SubElement(root, key)
            child.text = str(value)
        data_xml = ET.tostring(root, encoding="unicode")

        response = self.session.put(
            url,
            params=params,
            data=data_xml,
            headers={"Content-Type": "application/xml"}
        )
        response.raise_for_status()
        return "<success>" in response.text

    def register_listener(self, listener_uri: str) -> bool:
        """Register an Interface X listener."""
        url = f"{self.BASE_URL}/listeners"
        params = {"sessionHandle": self.session_handle}

        response = self.session.post(
            url,
            params=params,
            data=listener_uri,
            headers={"Content-Type": "text/plain"}
        )
        response.raise_for_status()
        return "<success>" in response.text

    def remove_listener(self, listener_uri: str) -> bool:
        """Remove a registered listener."""
        url = f"{self.BASE_URL}/listeners"
        params = {
            "sessionHandle": self.session_handle,
            "uri": listener_uri
        }

        response = self.session.delete(url, params=params)
        response.raise_for_status()
        return "<success>" in response.text
```

---

## Integration Patterns

### Exception Handler Service

Create a service that handles work item exceptions:

```java
public class ExceptionHandlerService implements InterfaceXListener {

    private final ExceptionHandlingClient client;
    private final NotificationService notifier;

    public void handleException(String itemId, String reason, String code) {
        // Log the exception
        logger.warn("Work item {} exception: {} ({})", itemId, reason, code);

        // Cancel the work item
        client.cancelWorkItemWithException(itemId, reason, code);

        // Notify stakeholders
        notifier.sendAlert(itemId, reason);

        // Optionally restart or route to alternate path
        if (isRecoverable(code)) {
            client.suspendWorkItem(itemId);
            scheduleRecovery(itemId);
        }
    }
}
```

### Progress Tracking

Track work item progress with intermediate saves:

```java
public class ProgressTracker {

    private final InterfaceXClient client;

    public void saveProgress(String itemId, int progress, String notes) {
        Map<String, String> data = new HashMap<>();
        data.put("progress", String.valueOf(progress));
        data.put("notes", notes);
        data.put("lastSaved", Instant.now().toString());

        client.updateWorkItemData(itemId, data);
    }
}
```

---

## Error Handling

| Error | HTTP Status | Response Body |
|-------|-------------|---------------|
| Invalid session | 401 | `<failure>Session handle is required</failure>` |
| Missing item ID | 400 | `<failure>Work item ID is required</failure>` |
| Missing data | 400 | `<failure>Data XML is required</failure>` |
| Missing listener URI | 400 | `<failure>Listener URI is required</failure>` |
| Invalid state | 200 | `<failure>Work item cannot be {action} in current state</failure>` |
| Server error | 500 | `IllegalStateException` |

---

## References

- **InterfaceXRestResource**: `src/org/yawlfoundation/yawl/engine/interfce/rest/InterfaceXRestResource.java`
- **InterfaceX_EngineSideServer**: `src/org/yawlfoundation/yawl/engine/interfce/interfaceX/InterfaceX_EngineSideServer.java`
- **EngineGateway**: `src/org/yawlfoundation/yawl/engine/interfce/EngineGateway.java`
- **YWorkItem**: `src/org/yawlfoundation/yawl/engine/YWorkItem.java`

---

**Document Version**: 1.0
**YAWL Version**: 6.0.0
**Last Updated**: 2026-02-18
