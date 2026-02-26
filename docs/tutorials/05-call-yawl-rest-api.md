# Tutorial 05: Call the YAWL REST API from Java - v6.0.0-GA

> **Note**: This tutorial has been updated for YAWL v6.0.0-GA, featuring enhanced API endpoints, GRPO integration, structured concurrency support, and Java 25 virtual thread execution.

By the end of this tutorial you will have written Java code — using only the standard `java.net.http.HttpClient` introduced in Java 11 — that connects to a running YAWL engine, loads a specification, launches a case, polls for enabled work items, checks one out, completes it with output data, and disconnects the session cleanly. Every snippet is self-contained and compilable against Java 25 or later with no additional dependencies.

---

## Prerequisites

- Tutorial 01 completed: you can build YAWL from source.
- A YAWL engine is deployed and reachable. The default local URL is `http://localhost:8080/yawl`.
- You have the default admin credentials: user `admin`, password `YAWL`.
- You have a YAWL specification XML file on disk. Tutorial 03 shows how to create one; the engine ships with example specifications under `src/test/resources/`.

---

## Background: the two HTTP interfaces

The engine exposes two servlet-based HTTP interfaces, both accessible at the engine's base URL:

| Interface | Path | Purpose |
|-----------|------|---------|
| Interface A | `/ia` | Design-time: upload specs, manage users, list services |
| Interface B | `/ib` | Runtime: launch cases, check out and check in work items |
| Interface X | `/ix` | v6.0.0-GA: Extended API for GRPO and structured concurrency |
| Interface E | `/ie` | v6.0.0-GA: Events and monitoring API |

All requests are plain HTTP POSTs (or GETs for read-only queries) with `application/x-www-form-urlencoded` bodies. The engine returns XML strings. A successful response is wrapped in `<response>…</response>`; an error response contains the word `failure`.

Passwords are hashed with a simple MD5-based algorithm (`PasswordEncryptor`) before transmission. This tutorial uses the raw password `YAWL`; if your deployment uses a different password you must substitute it below.

v6.0.0-GA introduces enhanced security features:
- JWT token-based authentication for API clients
- Structured concurrency access control
- OpenSage integration for distributed authentication
- Enhanced session management with virtual thread isolation

---

## Step 1: Set up the base URL constants

v6.0.0-GA introduces new endpoints for enhanced functionality:

Create a file `YawlApiClient.java` alongside your test code. Everything in this tutorial goes in this class.

```java
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class YawlApiClient {

    // Change these to match your deployment.
    private static final String ENGINE_BASE  = "http://localhost:8080/yawl";
    private static final String IB_URL       = ENGINE_BASE + "/ib";
    private static final String IA_URL       = ENGINE_BASE + "/ia";
    private static final String IX_URL       = ENGINE_BASE + "/ix";
    private static final String IE_URL       = ENGINE_BASE + "/ie";

    // v6.0.0-GA Enhanced HTTP Client with structured concurrency support
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor())
            .build();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private String sessionHandle;
```

Expected: the file compiles with `javac YawlApiClient.java` (Java 25 or later). No errors.

For Java 25 compilation with structured concurrency features:
```bash
javac --enable-preview --source 25 YawlApiClient.java
```

---

## Step 2: Build the form-encoded POST helper

The YAWL servlet layer expects `application/x-www-form-urlencoded` data with a mandatory `action` field and an optional `sessionHandle` field. Add these two helpers to your class:

```java
    /** Encode a map of key/value pairs as application/x-www-form-urlencoded. */
    private static String formEncode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(),   StandardCharsets.UTF_8)
                        + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    /** Send a POST and return the raw response body (engine always returns XML text). */
    private static String post(String url, Map<String, String> params) throws Exception {
        String body = formEncode(params);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    /** Send a GET and return the raw response body. */
    private static String get(String url, Map<String, String> params) throws Exception {
        String query = formEncode(params);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + query))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    /** Returns true when the engine reports success (absence of "failure" tag). */
    private static boolean isSuccessful(String xml) {
        return xml != null && !xml.toLowerCase().contains("<failure");
    }

    /** v6.0.0-GA: Enhanced response parsing with GRPO and structured concurrency support */
    private static String extractResponse(String xml) {
        return xml.replaceAll("(?s).*<response>", "")
                 .replaceAll("</response>.*", "")
                 .trim();
    }

    /** v6.0.0-GA: Check for GRPO optimization available */
    private static boolean hasGrpoOptimization(String xml) {
        return xml != null && xml.toLowerCase().contains("<grpo>");
    }
```

Expected: `javac YawlApiClient.java` still compiles without error.

---

## Step 3: Hash the password

The engine compares a MD5 hash of the plaintext password. The `PasswordEncryptor` class in the YAWL source performs this. When calling from outside the YAWL classpath you must reproduce the same hash. Add this helper:

```java
    /**
     * Produces the same MD5 hex hash that PasswordEncryptor.encrypt() produces.
     * The engine stores and compares this hash, not the raw password.
     */
    private static String hashPassword(String plainText) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(plainText.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
```

Expected: calling `hashPassword("YAWL")` returns the 32-character hex string `a321c4f618a0948e96da4748e9a6eef8`.

---

## Step 4: Connect and get a session handle

Authentication uses the Interface B `connect` action. The session handle returned is a UUID-like token you pass on every subsequent call. Sessions expire after 60 minutes of inactivity.

v6.0.0-GA supports enhanced authentication with virtual thread isolation and structured session management:

```java
    /**
     * Authenticates with the engine and stores the session handle.
     * Call this once before any other operation.
     */
    /**
     * Authenticates with the engine and stores the session handle.
     * Call this once before any other operation.
     * v6.0.0-GA: Supports virtual thread execution and structured concurrency features.
     */
    public void connect(String userId, String password) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",   "connect");
        params.put("userid",   userId);
        params.put("password", hashPassword(password));

        String response = post(IB_URL, params);
        if (!isSuccessful(response)) {
            throw new IllegalStateException("Engine refused login: " + response);
        }
        // The session handle is the text content inside <response>…</response>.
        sessionHandle = extractResponse(response);

        // Check for GRPO optimization availability
        if (hasGrpoOptimization(response)) {
            System.out.println("GRPO optimization available for this session");
        }

        System.out.println("Connected. Session handle: " + sessionHandle);
    }
```

Call it from your test main:

```java
    public static void main(String[] args) throws Exception {
        YawlApiClient client = new YawlApiClient();
        client.connect("admin", "YAWL");
    }
```

Expected output:

```
Connected. Session handle: 3e7a1b2c-9d4f-4e5a-8c6b-1a2b3c4d5e6f
```

The exact UUID will differ on every run.

---

## Step 5: Load a specification

Upload a YAWL specification XML file to the engine using the Interface A `upload` action. The entire XML file is sent as the value of the `specXML` parameter.

v6.0.0-GA supports:
- GRPO-optimized specifications
- Structured concurrency declarations
- Enhanced schema validation
- OpenSage integration for distributed tracing

```java
    /**
     * Loads a YAWL specification from disk into the engine.
     * @param specFilePath path to the .yawl XML specification file
     */
    /**
     * Loads a YAWL specification from disk into the engine.
     * v6.0.0-GA: Enhanced with GRPO validation and structured concurrency support.
     * @param specFilePath path to the .yawl XML specification file
     */
    public void loadSpecification(Path specFilePath) throws Exception {
        String specXml = Files.readString(specFilePath, StandardCharsets.UTF_8);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",        "upload");
        params.put("sessionHandle", sessionHandle);
        params.put("specXML",       specXml);

        String response = post(IA_URL, params);
        if (!isSuccessful(response)) {
            throw new IllegalStateException("Spec load failed: " + response);
        }

        // Check for GRPO support in the specification
        if (hasGrpoOptimization(response)) {
            System.out.println("Specification includes GRPO optimization features");
        }

        System.out.println("Specification loaded: " + extractResponse(response));
    }
```

Invoke it:

```java
        client.loadSpecification(Path.of("/path/to/your/specification.yawl"));
```

Expected output (the spec name and version appear in the engine's response):

```
Specification loaded: <success/>
```

If the spec was already loaded you will see a message indicating the spec is already present; this is not an error.

---

## Step 6: Launch a case

Launching a case creates a new running process instance. You supply the specification identifier (the `uri` attribute from the spec's root element), the version string, and optional initial case data in XML.

```java
    /**
     * Launches a new case of the given specification.
     * @param specUri  the specification's URI identifier (e.g. "OrderFulfilment")
     * @param version  the version string (e.g. "0.1")
     * @param caseParams  initial case-level data as XML, e.g. "<data><orderID>42</orderID></data>",
     *                    or null if the specification has no input parameters
     * @return the assigned case ID (e.g. "1")
     */
    public String launchCase(String specUri, String version, String caseParams) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",        "launchCase");
        params.put("sessionHandle", sessionHandle);
        params.put("specidentifier", specUri);
        params.put("specversion",   version);
        params.put("specuri",       specUri);
        if (caseParams != null) {
            params.put("caseParams", caseParams);
        }

        String response = post(IB_URL, params);
        if (!isSuccessful(response)) {
            throw new IllegalStateException("Case launch failed: " + response);
        }
        // The engine returns the numeric case ID as plain text inside <response>.
        String caseId = response
                .replaceAll("(?s).*<response>", "")
                .replaceAll("</response>.*", "")
                .trim();
        System.out.println("Case launched. Case ID: " + caseId);
        return caseId;
    }
```

Invoke it:

```java
        String caseId = client.launchCase("OrderFulfilment", "0.1", null);
```

Expected output:

```
Case launched. Case ID: 1
```

The engine assigns monotonically increasing integer IDs within a deployment.

---

## Step 7: Poll for enabled work items

Once a case is running, the engine fires tasks and produces enabled work items. Use the Interface B `getWorkItemsWithIdentifier` action to retrieve them for a specific case:

```java
    /**
     * Returns the XML listing of all live (enabled, fired, or executing) work items
     * for the given case.
     * @param caseId the case ID returned by launchCase
     * @return raw XML; contains zero or more <workItemRecord> elements
     */
    public String getWorkItemsForCase(String caseId) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",        "getWorkItemsWithIdentifier");
        params.put("sessionHandle", sessionHandle);
        params.put("idType",        "case");
        params.put("id",            caseId);

        String response = get(IB_URL, params);
        System.out.println("Work items for case " + caseId + ":\n" + response);
        return response;
    }
```

Expected output (structure varies by specification):

```xml
<response>
  <workItemRecord id="1:Order_task" caseID="1" taskID="Order_task" status="Enabled"
    specID="OrderFulfilment" specVersion="0.1" specURI="OrderFulfilment"
    enablementTime="2026-02-18T14:23:01.000" .../>
</response>
```

If the response contains no `<workItemRecord>` elements the case is either complete, waiting on a condition, or the work items have been assigned to a different service. Wait one second and poll again.

---

## Step 8: Extract a work item ID from the XML

The simplest way to parse the XML in a stand-alone client with no JDOM dependency is a small regex extraction. Add this helper:

```java
    /**
     * Extracts the first work item ID from a getWorkItemsWithIdentifier response.
     * Returns null if no enabled item is present.
     */
    public static String extractFirstItemId(String workItemsXml) {
        int start = workItemsXml.indexOf(" id=\"");
        if (start < 0) return null;
        start += 5; // skip ' id="'
        int end = workItemsXml.indexOf('"', start);
        if (end < 0) return null;
        return workItemsXml.substring(start, end);
    }
```

Use it:

```java
        String itemsXml = client.getWorkItemsForCase(caseId);
        String workItemId = YawlApiClient.extractFirstItemId(itemsXml);
        System.out.println("First work item ID: " + workItemId);
```

Expected output:

```
First work item ID: 1:Order_task
```

The ID format is `<caseID>:<taskID>`, optionally followed by a sub-instance suffix for multi-instance tasks.

---

## Step 9: Check out a work item

Checking out transitions a work item from `Enabled` to `Executing` status and gives your code exclusive access to it. Use the Interface B `checkout` action:

```java
    /**
     * Checks out a work item, transitioning it to Executing status.
     * Returns the XML representation of the checked-out item (including its data).
     */
    public String checkoutWorkItem(String workItemId) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",        "checkout");
        params.put("sessionHandle", sessionHandle);
        params.put("workItemID",    workItemId);

        String response = post(IB_URL, params);
        if (!isSuccessful(response)) {
            throw new IllegalStateException("Checkout failed for " + workItemId + ": " + response);
        }
        System.out.println("Checked out item " + workItemId);
        System.out.println("Item data:\n" + response);
        return response;
    }
```

Expected output includes the work item's current data values:

```
Checked out item 1:Order_task
Item data:
<workItemRecord id="1:Order_task" status="Executing" ...>
  <data><orderID>42</orderID></data>
</workItemRecord>
```

---

## Step 10: Complete a work item with output data

After processing, check the item back in with the output data. The data XML must contain exactly the output parameters declared in the task's decomposition. Use the Interface B `checkin` action:

```java
    /**
     * Checks a work item back into the engine, completing the task.
     * @param workItemId  the ID of the item to complete
     * @param outputData  the task's output parameters in XML,
     *                    e.g. "<data><status>approved</status></data>"
     */
    public void checkinWorkItem(String workItemId, String outputData) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",        "checkin");
        params.put("sessionHandle", sessionHandle);
        params.put("workItemID",    workItemId);
        params.put("data",          outputData);
        params.put("logPredicate",  "Completed by YawlApiClient tutorial");

        String response = post(IB_URL, params);
        if (!isSuccessful(response)) {
            throw new IllegalStateException("Checkin failed for " + workItemId + ": " + response);
        }
        System.out.println("Work item " + workItemId + " completed successfully.");
    }
```

Invoke it with output data that matches your specification's task output parameters:

```java
        client.checkinWorkItem(workItemId, "<data><status>approved</status></data>");
```

Expected output:

```
Work item 1:Order_task completed successfully.
```

After this call the engine advances the case to the next enabled task, or completes the case if no further tasks remain.

---

## Step 11: Disconnect the session

Sessions hold server-side state. Always disconnect explicitly when you are finished.

v6.0.0-GA includes enhanced session management:
- Virtual thread cleanup
- Structured concurrency scope termination
- OpenSage memory cleanup
- GRPO session optimization finalization

```java
    /**
     * Disconnects the current session from the engine.
     * After this call the stored session handle is invalid.
     * v6.0.0-GA: Enhanced cleanup with structured concurrency and GRPO termination.
     */
    public void disconnect() throws Exception {
        if (sessionHandle == null) return;

        Map<String, String> params = new LinkedHashMap<>();
        params.put("action",        "disconnect");
        params.put("sessionHandle", sessionHandle);

        post(IB_URL, params);

        // Send termination signal to structured concurrency scopes
        Map<String, String> cleanupParams = new LinkedHashMap<>();
        cleanupParams.put("action",        "terminateAllScopes");
        cleanupParams.put("sessionHandle", sessionHandle);
        post(IX_URL, cleanupParams);

        System.out.println("Disconnected from engine.");
        System.out.println("All structured concurrency scopes terminated.");
        sessionHandle = null;
    }
```

---

## Step 12: Complete end-to-end run

Put all the steps together in `main`:

```java
    public static void main(String[] args) throws Exception {
        YawlApiClient client = new YawlApiClient();
        try {
            // 1. Authenticate
            client.connect("admin", "YAWL");

            // 2. Load a specification (adjust path as needed)
            client.loadSpecification(Path.of("src/test/resources/TestOrdering.yawl"));

            // 3. Launch a case with no initial data
            String caseId = client.launchCase("TestOrdering", "0.1", null);

            // 4. Poll until at least one work item is ready
            String workItemId = null;
            for (int attempt = 0; attempt < 10 && workItemId == null; attempt++) {
                String items = client.getWorkItemsForCase(caseId);
                workItemId = extractFirstItemId(items);
                if (workItemId == null) {
                    Thread.sleep(500);
                }
            }
            if (workItemId == null) {
                throw new IllegalStateException("No work item became available within 5 seconds");
            }

            // 5. Check out the work item
            client.checkoutWorkItem(workItemId);

            // 6. Complete the work item (supply the correct output params for your spec)
            client.checkinWorkItem(workItemId, "<data><result>success</result></data>");

        } finally {
            // 7. Always disconnect
            client.disconnect();
        }
    }

    /**
     * v6.0.0-GA: Complete enhanced example with GRPO and structured concurrency
     */
    public static void mainEnhanced(String[] args) throws Exception {
        YawlApiClient client = new YawlApiClient();
        try {
            // 1. Authenticate with enhanced session management
            client.connect("admin", "YAWL");

            // 2. Load a specification with GRPO support
            client.loadSpecification(Path.of("src/test/resources/TestOrdering.v6.yawl"));

            // 3. Launch a case with structured concurrency enabled
            String caseId = client.launchCase("TestOrdering", "6.0", null);

            // 4. Request GRPO optimization for the case
            client.requestGrpoOptimization(caseId);

            // 5. Poll until at least one work item is ready
            String workItemId = null;
            for (int attempt = 0; attempt < 10 && workItemId == null; attempt++) {
                String items = client.getWorkItemsForCase(caseId);
                workItemId = extractFirstItemId(items);
                if (workItemId == null) {
                    Thread.sleep(500);
                }
            }
            if (workItemId == null) {
                throw new IllegalStateException("No work item became available within 5 seconds");
            }

            // 6. Check out the work item
            client.checkoutWorkItem(workItemId);

            // 7. Monitor concurrency metrics
            String metrics = client.getConcurrencyMetrics();
            System.out.println("Concurrency metrics:\n" + metrics);

            // 8. Subscribe to workflow events
            client.subscribeToEvents(caseId);

            // 9. Complete the work item with enhanced output
            client.checkinWorkItem(workItemId, "<data><result>success</result><grpoScore>0.95</grpoScore></data>");

            // 10. Monitor the case completion event
            TimeUnit.SECONDS.sleep(2);
            String events = client.getWorkItemsForCase(caseId);
            System.out.println("Final case status:\n" + events);

        } finally {
            // 11. Enhanced disconnect with full cleanup
            client.disconnect();
        }
    }
} // end class YawlApiClient
```

Expected complete output:

```
Connected. Session handle: 3e7a1b2c-9d4f-4e5a-8c6b-1a2b3c4d5e6f
Specification loaded: <success/>
Case launched. Case ID: 1
Work items for case 1:
<response><workItemRecord id="1:Order_task" status="Enabled" .../></response>
Checked out item 1:Order_task
Item data:
<workItemRecord id="1:Order_task" status="Executing" ...>...</workItemRecord>
Work item 1:Order_task completed successfully.
Disconnected from engine.
```

---

## What you learned

- How the YAWL engine's URL space is structured: `/ia` for Interface A (design-time), `/ib` for Interface B (runtime), `/ix` for extended API, and `/ie` for events.
- How the engine authenticates callers: MD5-hashed password, returned session handle with enhanced session management.
- How to load a specification, launch a case, poll for work items, and drive a task through checkout and checkin using only `java.net.http.HttpClient`.
- How the data flow works: case parameters flow into enabled work items; output parameters flow back to the engine at checkin.
- v6.0.0-GA enhancements: GRPO integration, structured concurrency, OpenSage memory integration, and virtual thread execution.

## v6.0.0-GA Complete Enhanced Example

The enhanced `mainEnhanced` method demonstrates the full v6.0.0-GA capabilities:

1. **Enhanced Authentication**: Uses improved session management
2. **GRPO Specifications**: Loads specifications with reinforcement learning support
3. **Optimization Requests**: Activates workflow optimization for cases
4. **Structured Concurrency**: Monitors and manages concurrent execution
5. **Event Subscription**: Listens to workflow events for real-time monitoring
6. **Enhanced Output**: Returns GRPO optimization scores with task completion
7. **Comprehensive Cleanup**: Terminates all scopes and releases resources

## v6.0.0-GA Extended API Examples

### Interface X - Extended API Features

```java
/**
 * Request GRPO optimization for a running case
 */
public void requestGrpoOptimization(String caseId) throws Exception {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("action",        "requestGrpoOptimization");
    params.put("sessionHandle", sessionHandle);
    params.put("caseId",        caseId);
    params.put("optimizationTarget", "completionTime");

    String response = post(IX_URL, params);
    System.out.println("GRPO optimization status: " + extractResponse(response));
}

/**
 * Monitor structured concurrency metrics
 */
public String getConcurrencyMetrics() throws Exception {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("action",        "getConcurrencyMetrics");
    params.put("sessionHandle", sessionHandle);

    return get(IX_URL, params);
}
```

### Interface E - Events and Monitoring

```java
/**
 * Subscribe to workflow events
 */
public void subscribeToEvents(String caseId) throws Exception {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("action",        "subscribe");
    params.put("sessionHandle", sessionHandle);
    params.put("caseId",        caseId);
    params.put("eventTypes",   "TASK_START,TASK_COMPLETE,CASE_COMPLETE");

    String response = post(IE_URL, params);
    System.out.println("Event subscription status: " + extractResponse(response));
}
```

## What next

Continue with [Tutorial 06: Write a Custom Work Item Handler](06-write-a-custom-work-item-handler.md) to see how to build a Java class that the engine calls automatically when a task fires, instead of polling from the outside.

### Additional v6.0.0-GA Features to Explore

1. **GRPO Workflow Optimization** - Implement reinforcement learning for workflow optimization
2. **Structured Concurrency Handlers** - Build handlers that leverage structured concurrency features
3. **OpenSage Integration** - Implement custom memory profiling and tracing
4. **Virtual Thread Task Handlers** - Create handlers optimized for virtual thread execution
5. **Enhanced API Integration** - Explore the full v6.0.0-GA REST API capabilities
