# Z.AI ↔ YAWL Integration Layer Design

**Status**: Research Document (Phase 1)
**Date**: 2026-02-21
**Scope**: Architecture, Z.AI agent lifecycle, tool contract, authentication, idempotency
**Target**: Z.AI agents invoking YAWL workflows as external tools via MCP

---

## 1. Z.AI Agent Lifecycle & Tool Use Pattern

Z.AI is Anthropic's autonomous agent framework with a **functional tool pattern**:

```
Z.AI Agent Loop:
  1. Agent receives task (e.g., "Process invoice for PO-42")
  2. Agent examines available tools (via MCP)
  3. Agent invokes tool: tool_use(name, arguments)
  4. Tool executes (blocking or async callback)
  5. Tool returns result_json or error
  6. Agent observes result
  7. Agent decides: retry, invoke another tool, or report done
  8. Loop until task complete or timeout
```

**Key constraint**: Z.AI tools **must be idempotent**. If Z.AI retries due to transient failure, the tool must produce the same observable result.

**Example Z.AI interaction**:
```json
Z.AI message: {
  "type": "tool_use",
  "id": "call_123",
  "name": "cases_submit",
  "input": {
    "spec_id": "InvoiceApproval",
    "case_data": {
      "po_number": "PO-42",
      "vendor_id": "V-001",
      "amount": 5000.00
    }
  }
}

Tool response: {
  "case_id": "9876",
  "status": "running",
  "created_at": "2026-02-21T10:30:45Z"
}

Z.AI observes result, logs state, may invoke another tool or report done.
```

---

## 2. YAWL as Tool Provider: MCP Tool Contract

### 2.1 Tool Inventory for Z.AI

YAWL exposes these tools via MCP to Z.AI agents:

| Tool | Input | Output | Idempotent? | Use Case |
|------|-------|--------|-------------|----------|
| **cases/submit** | spec_id, case_data (optional) | case_id, status | ✓ Idempotency key required | Z.AI submits invoice, launches workflow |
| **cases/status** | case_id | state, running_tasks, progress | ✓ (read-only) | Z.AI polls for completion |
| **cases/subscribe** | case_id, callback_url, events | subscription_id, expires_at | ✓ Idempotency key | Z.AI gets webhook for completion (optional) |
| **workitems/list** | case_id (optional) | [workitem_id, task_name, status][] | ✓ (read-only) | Z.AI discovers pending work |
| **workitems/checkout** | workitem_id | workitem_data | ✓ Idempotency key | Z.AI acquires work item (lock) |
| **workitems/complete** | workitem_id, output_data | status, next_tasks | ✓ Idempotency key | Z.AI completes work item |
| **specifications/list** | (none) | [spec_id, name, version][] | ✓ (read-only) | Z.AI discovers available workflows |
| **specifications/describe** | spec_id | spec_xml, tasks, data_schema | ✓ (read-only) | Z.AI understands workflow structure |

### 2.2 Tool Specification (MCP Format)

Each tool includes:
- **Input schema** (JSON Schema) — constraints on arguments
- **Output schema** (JSON Schema) — response structure
- **Description** (human-readable) — when and why to use
- **Error cases** — transient vs permanent failures

**Example: cases/submit**

```json
{
  "name": "cases/submit",
  "description": "Submit a case to a workflow specification. Idempotent: use the same idempotency_key to safely retry.",
  "inputSchema": {
    "type": "object",
    "required": ["spec_id", "idempotency_key"],
    "properties": {
      "spec_id": {
        "type": "string",
        "description": "Workflow spec identifier (e.g., 'InvoiceApproval')"
      },
      "case_data": {
        "type": "object",
        "description": "Optional case variables (JSON object, matches spec schema)"
      },
      "idempotency_key": {
        "type": "string",
        "description": "Unique identifier for this submission. If repeated, returns same case_id."
      }
    }
  },
  "outputSchema": {
    "type": "object",
    "properties": {
      "case_id": {
        "type": "string",
        "description": "Unique case identifier"
      },
      "status": {
        "type": "string",
        "enum": ["created", "running", "completed", "failed"]
      },
      "created_at": {
        "type": "string",
        "format": "date-time"
      }
    }
  }
}
```

---

## 3. Interaction Flow: Z.AI → MCP → YAWL → Engine

```
┌─────────────────────────────────────────────────────────────────────┐
│ Z.AI Agent (Cloud, Autonomous)                                      │
│                                                                     │
│  Task: "Process invoice PO-42 for vendor V-001"                   │
│    ↓                                                               │
│  [1] Discover: call tools/specifications/list                      │
│    ↓                                                               │
│  Response: ["InvoiceApproval", "PurchaseOrder", ...]              │
│    ↓                                                               │
│  [2] Decide: InvoiceApproval matches PO task                      │
│    ↓                                                               │
│  [3] Invoke: cases/submit({                                        │
│        spec_id: "InvoiceApproval",                                │
│        case_data: {po: "PO-42", vendor: "V-001", amt: 5000},      │
│        idempotency_key: "zai-agent-1-inv-42"                      │
│      })                                                             │
│    ↓                                                               │
│  Response: case_id="9876", status="running"                       │
│    ↓                                                               │
│  [4] Poll: cases/status(case_id="9876") [every 5s]               │
│    ↓                                                               │
│  Response: status="executing", tasks=["Review", "Approve"]        │
│    ↓                                                               │
│  [5] Wait loop until status="completed" or timeout                │
│    ↓                                                               │
│  Response: status="completed", result={approved: true}            │
│    ↓                                                               │
│  [6] Report: "Invoice processed. Approved: true"                  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
           ↓ MCP (Model Context Protocol)
┌─────────────────────────────────────────────────────────────────────┐
│ YAWL MCP Server (HTTP or STDIO)                                      │
│                                                                     │
│  Tool Handler Registry:                                            │
│    - CaseSubmitHandler                                            │
│    - CaseStatusHandler                                            │
│    - CaseSubscribeHandler (optional)                              │
│    - WorkItemHandlers (checkout, complete, list)                 │
│    - SpecificationHandlers (list, describe)                      │
│                                                                     │
│  [7] Dispatch: CaseSubmitHandler(spec_id, case_data, idempotency) │
│    ↓ check idempotency store                                      │
│    ↓ if seen before → return cached case_id (idempotent!)         │
│    ↓ else → delegate to engine adapter                            │
└─────────────────────────────────────────────────────────────────────┘
           ↓ InterfaceB_EnvironmentBasedClient
┌─────────────────────────────────────────────────────────────────────┐
│ YAWL Engine (YStatelessEngine, Case Manager)                        │
│                                                                     │
│  [8] launchCase(spec_id, caseData)                                 │
│    ↓                                                               │
│  [9] Create case instance, set variables, start enabled tasks     │
│    ↓                                                               │
│  [10] Fire net transitions, case enters "executing" state         │
│    ↓ (work items created for manual/automated tasks)             │
│    ↓                                                               │
│  [11] Return case_id="9876"                                       │
│                                                                     │
│  EventBus fires:                                                  │
│    - CaseCreatedEvent                                             │
│    - WorkItemCreatedEvent (for Review, Approve tasks)            │
│    - CaseStateChangeEvent (running)                              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
           ↓ Event callback or polling
┌─────────────────────────────────────────────────────────────────────┐
│ Z.AI Status Polling Loop (or Webhook Callback)                      │
│                                                                     │
│  Polling: Every 5s call cases/status(case_id="9876")              │
│    ↓                                                               │
│  Response: CaseStateSnapshot {                                    │
│    case_id: "9876",                                               │
│    state: "executing",                                            │
│    progress: 0.33,  // 1 of 3 tasks complete                     │
│    running_tasks: ["Review", "Approve"],                         │
│    output_data: {/* case variables */}                           │
│  }                                                                 │
│    ↓                                                               │
│  Loop until state="completed" or state="failed" (max 300s)       │
│    ↓                                                               │
│  Final: state="completed", output_data={approved: true}          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.1 Sequence: Case Submission with Idempotency

```
Time  Z.AI                       MCP Server                  YAWL Engine
─────────────────────────────────────────────────────────────────────────
 0s   cases/submit({
      spec_id: "Invoice",
      idempotency: "z1"}  ──────────→  Check idempotency store (miss)
                                       ↓ Call engine.launchCase(...)
                                       ↓ Case created: 9876
 1s                                    Store (idempotency="z1", case=9876)
                                       ←───────  Return case_id=9876
      Observe case_id=9876, state=running, save to context

 [Network hiccup, Z.AI retries...]

 3s   cases/submit({
      spec_id: "Invoice",  ──────────→  Check idempotency store (HIT)
      idempotency: "z1"}              ←───────  Return case_id=9876 (cached!)

      Observe same case_id=9876 → idempotent! No new case created.
```

---

## 4. Authentication & Authorization

### 4.1 Service Account Model

Z.AI agents authenticate to YAWL via **service account JWT**:

```
Environment (Z.AI context):
  ZAI_AGENT_NAME = "z.ai-invoice-processor"
  ZAI_AGENT_ID = "agent-uuid-001"
  YAWL_MCP_JWT_SECRET = "... 32+ char key ..."  (shared with YAWL server)

Z.AI issue JWT on startup:
  {
    "sub": "z.ai-invoice-processor",
    "aud": "yawl-mcp",
    "iss": "zai-controller",
    "scope": "workflows:launch workflows:query workitems:manage",
    "exp": <60 min from now>,
    "iat": <now>
  }

Include in all MCP tool calls:
  Authorization: Bearer <jwt_token>
```

### 4.2 YAWL Authentication Provider

Reuse existing YAWL auth stack:

```java
// YawlMcpServer.start()
JwtAuthenticationProvider jwtAuth =
    JwtAuthenticationProvider.fromEnvironment();

ApiKeyAuthenticationProvider apiKeyAuth =
    ApiKeyAuthenticationProvider.fromEnvironment();

A2AAuthenticationProvider authProvider =
    CompositeAuthenticationProvider.builder()
        .add(jwtAuth)
        .add(apiKeyAuth)
        .build();

// Every MCP tool call validated:
AuthenticatedPrincipal principal = authProvider.authenticate(exchange);
if (!principal.hasPermission("workflows:launch")) {
    throw new ToolExecutionException("Insufficient permission");
}
```

### 4.3 Permission Model

Permissions exposed to Z.AI service accounts:

```
workflows:launch    - can submit cases
workflows:query     - can list specs, get case status
workflows:cancel    - can cancel cases
workitems:manage    - can checkout, complete, skip workitems
specs:read          - can describe specifications
(reserved) admin    - (not granted to Z.AI; YAWL ops only)
```

---

## 5. Error Handling & Idempotency

### 5.1 Transient vs Permanent Failures

Z.AI tool must distinguish:

| Error | HTTP Code | Z.AI Action | YAWL Response |
|-------|-----------|-------------|---------------|
| Network timeout | 503/504 | Retry with backoff | `{"error": "engine_unavailable"}` |
| Tool input invalid | 400 | Fail (don't retry) | `{"error": "invalid_spec_id"}` |
| Case not found | 404 | Fail | `{"error": "case_not_found"}` |
| Permission denied | 403 | Fail | `{"error": "unauthorized"}` |
| Duplicate idempotency key | 409 | Return same case_id | (see 5.2) |
| Internal server error | 500 | Retry, then fail | `{"error": "internal_error"}` |

### 5.2 Idempotency Implementation

**Requirement**: Z.AI retries must produce identical outcomes.

**Solution**: Idempotency key → result cache in YAWL

```java
// In YawlMcpServer.handleCaseSubmit()
String idempotencyKey = input.get("idempotency_key");
if (idempotencyKey != null) {
    // Check cache (Redis or in-memory)
    Optional<CaseSubmitResult> cached =
        idempotencyStore.get(idempotencyKey);
    if (cached.isPresent()) {
        // Seen before → return same result
        return cached.get();
    }
}

// First time → execute
String caseId = interfaceBClient.launchCase(...);
CaseSubmitResult result = new CaseSubmitResult(caseId, "running");

// Store for future retries
if (idempotencyKey != null) {
    idempotencyStore.set(idempotencyKey, result, ttl=3600s);
}
return result;
```

**Cache TTL**: 1 hour (agent retries complete within minutes, human review window).

### 5.3 Backoff & Timeout Strategy

Z.AI built-in retry logic:

```
Z.AI tool invocation:
  timeout: 5 minutes (per tool call)
  max_retries: 3
  backoff: exponential (1s, 2s, 4s)

Transient failure (503):
  [1] Wait 1s, retry
  [2] Wait 2s, retry
  [3] Wait 4s, retry
  [4] Fail: "Tool unavailable after 3 attempts"

Tool success (HTTP 200): return result immediately
Tool client error (400/403/404): fail immediately (no retry)
```

**For cases/status polling**:
- Interval: 5 seconds
- Timeout: 300 seconds (5 min)
- Max polls: 60
- Exit condition: status in {completed, failed, error}

---

## 6. Implementation: YawlMcpServer Extensions

### 6.1 New Tool Handlers

Add to `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/tools/`:

```
YawlMcpServer/
├── tools/
│   ├── CaseSubmitTool.java       (new)
│   ├── CaseStatusTool.java       (new)
│   ├── CaseSubscribeTool.java    (new)
│   ├── WorkItemListTool.java     (new)
│   ├── WorkItemCheckoutTool.java (new)
│   ├── WorkItemCompleteTool.java (new)
│   └── SpecificationsTool.java   (existing → extend)
├── idempotency/
│   ├── IdempotencyKey.java       (new)
│   ├── IdempotencyStore.java     (new)
│   └── InMemoryIdempotencyStore.java (new)
└── events/
    ├── CaseEventPublisher.java   (new)
    └── WebhookCallbackService.java (new, optional)
```

### 6.2 CaseSubmitTool Pseudocode

```java
public class CaseSubmitTool implements YawlMcpTool {
    private final IdempotencyStore idempotencyStore;
    private final InterfaceB_EnvironmentBasedClient engine;
    private final JwtAuthenticationProvider authProvider;

    @Override
    public ToolResult execute(ToolInput input, AuthenticatedPrincipal principal)
            throws ToolExecutionException {
        // Validate permission
        if (!principal.hasPermission("workflows:launch")) {
            throw new ToolExecutionException("Permission denied", 403);
        }

        String specId = input.get("spec_id");
        String idempotencyKey = input.get("idempotency_key");
        Map<String, Object> caseData = input.getMap("case_data", null);

        // Check idempotency cache
        if (idempotencyKey != null) {
            var cached = idempotencyStore.get(idempotencyKey);
            if (cached.isPresent()) {
                // Idempotent: same input → same output
                return ToolResult.success(cached.get());
            }
        }

        // First time: launch case
        try {
            String caseId = engine.launchCase(
                new YSpecificationID(specId, "1.0", specId),
                caseData,
                null,
                sessionHandle
            );

            if (caseId == null || caseId.contains("<failure>")) {
                throw new ToolExecutionException(
                    "Failed to launch: " + caseId, 400);
            }

            // Store result for idempotency
            Map<String, Object> result = Map.of(
                "case_id", caseId,
                "status", "running",
                "created_at", Instant.now().toString()
            );
            if (idempotencyKey != null) {
                idempotencyStore.set(idempotencyKey, result, 3600);
            }

            return ToolResult.success(result);
        } catch (IOException e) {
            if (isTransient(e)) {
                throw new ToolExecutionException(e.getMessage(), 503);
            }
            throw new ToolExecutionException(e.getMessage(), 500);
        }
    }

    private boolean isTransient(IOException e) {
        return e.getMessage().contains("timeout") ||
               e.getMessage().contains("connection");
    }
}
```

### 6.3 CaseStatusTool

Returns case state snapshot:

```java
public class CaseStatusTool implements YawlMcpTool {
    @Override
    public ToolResult execute(ToolInput input, AuthenticatedPrincipal principal) {
        String caseId = input.get("case_id");

        String state = engine.getCaseState(caseId, sessionHandle);
        List<WorkItemRecord> tasks =
            engine.getWorkItemsForCase(caseId, sessionHandle);

        return ToolResult.success(Map.of(
            "case_id", caseId,
            "state", state,  // "created", "executing", "completed", "failed"
            "progress", calculateProgress(state, tasks),
            "running_tasks", tasks.stream()
                .map(WorkItemRecord::getTaskID)
                .toList(),
            "output_data", getCaseVariables(caseId, sessionHandle)
        ));
    }
}
```

### 6.4 IdempotencyStore Interface

```java
public interface IdempotencyStore {
    /**
     * Get cached result for idempotency key.
     * @return Optional containing result if key exists and not expired
     */
    Optional<Map<String, Object>> get(String key);

    /**
     * Store result with TTL.
     * @param key idempotency key (must be unique)
     * @param result tool result to cache
     * @param ttlSeconds expiry time
     */
    void set(String key, Map<String, Object> result, int ttlSeconds);

    /**
     * Clear expired entries (called periodically).
     */
    void evictExpired();
}

// InMemory impl: ConcurrentHashMap<String, CacheEntry<Map>>
// Redis impl: for production clustering
```

### 6.5 Integration with YawlMcpServer.start()

```java
// In YawlMcpServer.start()
IdempotencyStore idempotencyStore = new InMemoryIdempotencyStore();

// Register Z.AI-specific tools
toolRegistry.register(
    new CaseSubmitTool(idempotencyStore, interfaceBClient, authProvider)
);
toolRegistry.register(
    new CaseStatusTool(interfaceBClient, authProvider)
);
toolRegistry.register(
    new CaseSubscribeTool(eventPublisher, authProvider)
);
toolRegistry.register(
    new WorkItemListTool(interfaceBClient, authProvider)
);
// ... etc

// Start periodic idempotency eviction
scheduler.scheduleAtFixedRate(
    idempotencyStore::evictExpired,
    60, 60, TimeUnit.SECONDS
);

mcpServer.tools(toolRegistry.all()).build();
```

---

## 7. Event Subscription (Optional, Advanced)

### 7.1 Webhook Pattern (cases/subscribe)

Instead of polling, Z.AI subscribes to case completion events:

```
Z.AI calls:
  cases/subscribe({
    case_id: "9876",
    callback_url: "https://zai-webhook.example.com/case-complete",
    events: ["case_completed", "case_failed"],
    idempotency_key: "sub-z1"
  })

Response: {
  "subscription_id": "sub-abc123",
  "expires_at": "2026-02-21T12:00:00Z",  // 2 hours
  "polling_fallback": "https://yawl.example.com/v1/cases/9876/status"
}

Later, case completes:
  YAWL fires event → CaseCompletionPublisher
  CaseCompletionPublisher → HTTP POST to Z.AI webhook:

    POST https://zai-webhook.example.com/case-complete
    Content-Type: application/json
    X-YAWL-Signature: sha256=<hmac>

    {
      "event_type": "case_completed",
      "case_id": "9876",
      "outcome": {approved: true},
      "timestamp": "2026-02-21T10:35:10Z"
    }

Z.AI receives notification → observes completion → reports done.
```

**Benefits**: Lower latency, fewer polls, better for production.
**Trade-off**: Requires outbound connectivity from YAWL to Z.AI.

### 7.2 Event Publisher Architecture

```java
// In yawl/integration/events/
public interface CaseEventPublisher {
    void publishCaseCompleted(String caseId, Map<String, Object> outcome);
    void publishCaseFailed(String caseId, String reason);
    void subscribe(String caseId, String webhookUrl, String[] events);
}

// Implementation: WebhookCaseEventPublisher
public class WebhookCaseEventPublisher implements CaseEventPublisher {
    private final HttpClient httpClient;
    private final SubscriptionStore subscriptionStore;
    private final HmacSigner signer;  // X-YAWL-Signature

    @Override
    public void publishCaseCompleted(String caseId, Map<String, Object> outcome) {
        List<Subscription> subs = subscriptionStore.findByCaseId(caseId);
        for (Subscription sub : subs) {
            if (sub.events().contains("case_completed")) {
                sendWebhook(sub.callbackUrl(), Map.of(
                    "event_type", "case_completed",
                    "case_id", caseId,
                    "outcome", outcome,
                    "timestamp", Instant.now()
                ));
            }
        }
    }

    private void sendWebhook(String url, Map<String, Object> payload) {
        String json = objectMapper.writeValueAsString(payload);
        String signature = signer.sign(json);

        httpClient.post(url)
            .header("X-YAWL-Signature", "sha256=" + signature)
            .body(json)
            .timeout(10, TimeUnit.SECONDS)
            .retryOnTransient(3)
            .execute();
    }
}
```

---

## 8. Architecture Diagram (ASCII)

```
┌────────────────────────────────────────────────────────────────────┐
│ Z.AI Agent (Cloud)                                                 │
│                                                                   │
│  "Process invoice"                                               │
│    ↓                                                             │
│  MCP Client (tool_use)                                          │
│    ├─ Discovery: list tools via /.well-known/mcp.json          │
│    ├─ Invocation: POST /mcp/tools/invoke                       │
│    │   Body: {tool: "cases/submit", input: {...}}              │
│    │   Auth: Bearer <jwt_token>                                │
│    └─ Polling: periodic /mcp/tools/invoke (cases/status)       │
│                                                                   │
└────────────────────────────────────────────────────────────────────┘
        ↓ HTTP (or STDIO)
┌────────────────────────────────────────────────────────────────────┐
│ YawlMcpServer (port 8080+)                                          │
│                                                                   │
│  Endpoint: POST /mcp/tools/invoke                               │
│    ↓                                                             │
│  JwtAuthenticationProvider.authenticate(bearer_token)           │
│    ├─ Validate signature (HMAC-SHA256)                         │
│    ├─ Check expiry                                             │
│    └─ Extract permissions (scope claim)                        │
│    ↓ AuthenticatedPrincipal {sub, perms, exp}                │
│    ↓                                                             │
│  ToolRegistry.dispatch(tool_name, input, principal)            │
│    ├─ CaseSubmitTool                                           │
│    │  ├─ Check idempotency cache                              │
│    │  ├─ Call InterfaceB.launchCase()                        │
│    │  └─ Store result in cache (TTL: 3600s)                  │
│    ├─ CaseStatusTool                                          │
│    │  └─ Call InterfaceB.getCaseState()                      │
│    ├─ WorkItemListTool                                        │
│    │  └─ Call InterfaceB.getWorkItemsForCase()               │
│    └─ ... (6 more tools)                                       │
│    ↓                                                             │
│  Response: {case_id, status, created_at} or error             │
│    ↓                                                             │
│  [Webhook Event (optional)]                                    │
│  CaseEventPublisher publishes completion                       │
│    ↓ HTTP POST to Z.AI callback_url                           │
│                                                                   │
└────────────────────────────────────────────────────────────────────┘
        ↓ InterfaceB (existing)
┌────────────────────────────────────────────────────────────────────┐
│ YAWL Engine (YStatelessEngine)                                      │
│                                                                   │
│  launchCase(spec_id, caseData, ...)                             │
│    ├─ YSpecificationID lookup                                 │
│    ├─ YStatelessEngine.launchCase()                           │
│    ├─ YNetRunner creates case instance                        │
│    ├─ Enabled tasks fire → WorkItems created                 │
│    └─ Return case_id="9876"                                   │
│    ↓                                                             │
│  EventBus.publish(CaseCreatedEvent)                           │
│    └─ Propagated to WebhookCaseEventPublisher (optional)      │
│                                                                   │
└────────────────────────────────────────────────────────────────────┘
```

---

## 9. Error Handling Examples

### 9.1 Invalid Specification

```json
Z.AI invokes:
  cases/submit({spec_id: "NonExistent", idempotency_key: "z1"})

MCP Server response:
  {
    "error": "invalid_specification",
    "message": "Specification 'NonExistent' not found in engine",
    "http_status": 400
  }

Z.AI action:
  → Fail immediately (client error, no retry)
  → Report to user: "Workflow not available. Available specs: ..."
```

### 9.2 Network Timeout

```json
Z.AI invokes:
  cases/status({case_id: "9876"})

MCP Server times out (internal engine call hangs):
  {
    "error": "engine_timeout",
    "message": "YAWL engine did not respond within 10s",
    "http_status": 503
  }

Z.AI action:
  → Retry with backoff (1s, 2s, 4s)
  → After 3 failed retries: report timeout
```

### 9.3 Idempotent Retry

```json
Z.AI invokes (attempt 1):
  cases/submit({
    spec_id: "Invoice",
    idempotency_key: "z-inv-42",
    case_data: {po: "PO-42"}
  })

Response (HTTP 200):
  {case_id: "9876", status: "running"}

[Network hiccup, Z.AI retries...]

Z.AI invokes (attempt 2, SAME idempotency_key):
  cases/submit({
    spec_id: "Invoice",
    idempotency_key: "z-inv-42",
    case_data: {po: "PO-42"}
  })

Response (HTTP 200, from cache):
  {case_id: "9876", status: "running"}  ← Same as before!

Z.AI observes idempotent behavior ✓
No duplicate cases created ✓
```

---

## 10. PoC Implementation Estimate

### 10.1 Module Dependencies

```
yawl-mcp-server/
├── yawl-engine (existing)       ← YStatelessEngine, InterfaceB
├── yawl-authentication/*        ← JWT, API Key, SPIFFE
├── yawl-integration/a2a/*       ← A2A auth provider infrastructure
├── yawl-integration/mcp/*       ← MCP server base
└── [NEW] yawl-integration/mcp/tools/zai/**
    ├── CaseSubmitTool.java
    ├── CaseStatusTool.java
    ├── idempotency/IdempotencyStore.java
    └── events/CaseEventPublisher.java
```

**No new external dependencies** (use existing JWT libs, engine, auth).

### 10.2 Effort Estimate

| Component | File(s) | LOC | Effort | Notes |
|-----------|---------|-----|--------|-------|
| IdempotencyStore interface + in-memory impl | 2 | 150 | 1 day | Simple LRU cache |
| CaseSubmitTool | 1 | 80 | 1 day | Error handling, auth checks |
| CaseStatusTool | 1 | 60 | 0.5 day | Read-only, straightforward |
| WorkItemTools (checkout, complete, list) | 3 | 200 | 1.5 days | Integrate with YWorkItem |
| SpecificationsTool (extend) | 1 | 100 | 0.5 day | Reuse existing list/describe |
| CaseEventPublisher (webhook) | 2 | 180 | 1.5 days | HTTP client, signature, retry |
| Integration tests | 5 | 400 | 2 days | End-to-end Z.AI simulation |
| Documentation | - | - | 1 day | API spec, examples |
| **TOTAL** | **15** | **~1170** | **~8.5 days** | Single engineer |

### 10.3 Test Plan

```
Unit Tests:
  ✓ IdempotencyStore: get/set/evict
  ✓ CaseSubmitTool: valid spec, invalid spec, idempotent retry
  ✓ CaseStatusTool: state transitions, case not found
  ✓ WorkItemTools: checkout permissions, completion with output

Integration Tests:
  ✓ E2E: Z.AI submits case → polls status → completion → webhook
  ✓ Idempotency: retry with same key returns same case_id
  ✓ Error handling: transient retry, permanent failure
  ✓ Authentication: JWT validation, permission enforcement
  ✓ Concurrent tools: multiple Z.AI agents invoking in parallel

Performance Tests:
  ✓ Tool invocation latency: <100ms (p95)
  ✓ Idempotency cache hit: <5ms
  ✓ Case submission throughput: >100 cases/sec

Security Tests:
  ✓ Expired JWT rejected
  ✓ Invalid signature rejected
  ✓ Permission-denied tools return 403
  ✓ SQL injection via case_data (schema validation)
```

---

## 11. Configuration & Deployment

### 11.1 Environment Variables (Z.AI Context)

```bash
# Z.AI Agent Configuration
export ZAI_AGENT_NAME="zai-invoice-processor"
export ZAI_AGENT_ID="agent-uuid-001"

# YAWL MCP Server (where Z.AI connects)
export YAWL_MCP_URL="https://yawl-prod.example.com:8080/mcp"
export YAWL_MCP_JWT_SECRET="... 32+ chars ..."  # Shared with YAWL
export YAWL_MCP_JWT_ISSUER="zai-controller"

# Z.AI MCP Tool Timeouts
export ZAI_MCP_TOOL_TIMEOUT_MS=5000
export ZAI_MCP_TOOL_RETRIES=3
export ZAI_MCP_TOOL_BACKOFF_MS=1000
```

### 11.2 Environment Variables (YAWL Server Context)

```bash
# YAWL Engine
export YAWL_ENGINE_URL="http://localhost:8080/yawl"
export YAWL_USERNAME="admin"
export YAWL_PASSWORD="secret"

# MCP Server
export YAWL_MCP_PORT=8080
export YAWL_MCP_ENABLE_ZAI_TOOLS=true

# Authentication
export A2A_JWT_SECRET="... 32+ chars ..."
export A2A_JWT_ISSUER="yawl-auth"

# Idempotency Storage
export YAWL_MCP_IDEMPOTENCY_TTL_SECONDS=3600
export YAWL_MCP_IDEMPOTENCY_BACKEND="memory"  # or "redis"
export YAWL_MCP_IDEMPOTENCY_REDIS_URL="redis://localhost:6379"

# Webhooks (optional)
export YAWL_MCP_WEBHOOK_ENABLED=true
export YAWL_MCP_WEBHOOK_TIMEOUT_MS=10000
export YAWL_MCP_WEBHOOK_RETRIES=3
export YAWL_MCP_WEBHOOK_SIGNER_KEY="... webhook HMAC key ..."
```

### 11.3 Docker / Kubernetes (Example)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-mcp-server
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: yawl-mcp
        image: yawlfoundation/yawl:6.0.0
        ports:
        - containerPort: 8080
        env:
        - name: YAWL_ENGINE_URL
          value: "http://yawl-engine:8080/yawl"
        - name: YAWL_USERNAME
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: username
        - name: YAWL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: password
        - name: A2A_JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: yawl-jwt
              key: secret
        # ... more env vars
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
```

---

## 12. Conclusion & Next Steps

### 12.1 Design Principles Realized

1. **Idempotency by default**: Z.AI retries don't create duplicate cases (via idempotency key store).
2. **Async-first**: No blocking calls; polling + optional webhooks for notifications.
3. **Auth reuse**: Leverage existing YAWL JWT/API-key auth infrastructure.
4. **MCP protocol**: Standard interface means Z.AI uses YAWL tools just like other MCP servers.
5. **Error clarity**: Transient vs permanent failures clearly distinguished for intelligent retry logic.
6. **Scale-ready**: Idempotency store can be memory (dev), Redis (prod); webhooks enable event-driven architecture.

### 12.2 Phase 2: Implementation Tasks

1. **Core Tools** (3-4 days)
   - [ ] IdempotencyStore (interface + InMemory)
   - [ ] CaseSubmitTool (with idempotency)
   - [ ] CaseStatusTool (polling support)
   - [ ] Unit tests

2. **Work Item Tools** (2-3 days)
   - [ ] WorkItemListTool, CheckoutTool, CompleteTool
   - [ ] Integration tests with mock engine

3. **Advanced** (2-3 days)
   - [ ] CaseEventPublisher (webhooks)
   - [ ] Concurrent Z.AI agent simulation
   - [ ] Performance benchmarks

4. **Documentation** (1 day)
   - [ ] API specification (OpenAPI 3.0)
   - [ ] Z.AI agent examples (Python, JavaScript)
   - [ ] Operational runbook

### 12.3 Known Constraints & Mitigations

| Constraint | Impact | Mitigation |
|-----------|--------|-----------|
| Z.AI tools must be stateless (per invocation) | Can't maintain context between calls | Use case_id as context key; store state in case variables |
| MCP transport is synchronous | Long case execution blocks Z.AI | Use async polling + webhooks; tool returns quickly with case_id |
| Idempotency key collision risk | Multiple agents same key by accident | Prefix with agent ID: "z-agent1-inv-42" |
| Webhook delivery to Z.AI | Z.AI may not have public IP | Fallback to polling; webhooks optional |
| YAWL engine downtime | Z.AI tools fail with 503 | Z.AI retries; consider YAWL HA setup |

---

## 13. References

- **MCP Specification**: https://modelcontextprotocol.io/
- **Z.AI Framework**: Anthropic internal documentation
- **YAWL MCP Server**: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/mcp/`
- **YAWL A2A Auth**: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/a2a/auth/`
- **InterfaceB Client**: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/interfce/interfaceB/`
- **JWT RFC 7519**: https://tools.ietf.org/html/rfc7519
- **Idempotency RFC 9110 (Section 9.2.2)**: https://tools.ietf.org/html/rfc9110#section-9.2.2

---

## Document Metadata

**Author**: YAWL Integration Specialist
**Status**: Complete (Research Phase 1)
**Last Updated**: 2026-02-21
**Version**: 1.0
**Pages**: 2 (condensed; full technical depth provided)
**Code Examples**: 8 (pseudocode + architecture)
**Estimated PoC**: 8.5 engineer-days

