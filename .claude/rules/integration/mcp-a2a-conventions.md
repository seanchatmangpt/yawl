---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/integration/**"
  - "*/src/test/java/org/yawlfoundation/yawl/integration/**"
  - "**/yawl-integration/**"
  - "**/yawl-mcp-a2a-app/**"
---

# MCP & A2A Integration Rules

## MCP Server (6 Tools)

| Tool | Description | Key Parameters |
|------|-------------|----------------|
| `launch_case` | Start a new workflow case | `specificationId`, `caseData` (JSON) |
| `cancel_case` | Cancel a running case | `caseId` |
| `get_case_state` | Retrieve current case state | `caseId` |
| `list_specifications` | List available workflow specs | *(none)* |
| `get_workitems` | Get enabled work items | `caseId?` (optional filter) |
| `complete_workitem` | Complete a checked-out work item | `workItemId`, `outputData` (JSON) |

- Protocol version: `2024-11-05` | SDK: `1.0.0-RC1`
- Transport: STDIO (default for Claude Desktop) or HTTP+SSE (server deployments)
- Entry: `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer`

## MCP Response Conventions
```java
// CORRECT — always return structured content, not raw strings
return CallToolResult.builder()
    .content(List.of(TextContent.of(
        JsonWriter.toJson(CaseStateResponse.of(caseId, state))
    )))
    .build();

// VIOLATION — raw string response (unparseable by LLM tool consumer)
return CallToolResult.builder()
    .content(List.of(TextContent.of("Case " + caseId + " is running")))
    .build();
```

Error responses must use `isError: true` + structured JSON error body — never throw from a tool handler.

## A2A Server (6 Skills)

| Skill | Description |
|-------|-------------|
| `introspect` | Return agent capabilities and supported specifications |
| `generate` | Generate YAWL specification from process description |
| `build` | Compile and validate a specification |
| `test` | Run test suite for a specification |
| `commit` | Commit validated specification to registry |
| `upgrade` | Upgrade specification to new schema version |

- Entry: `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer`
- Virtual thread variant: `VirtualThreadYawlA2AServer` — 1000 agents ≈ 1 MB vs 2 GB platform threads
- Authentication: JWT tokens with 60-second TTL for handoff

## A2A Handoff Protocol
```java
// CORRECT — validate JWT before processing handoff
HandoffToken token = HandoffProtocol.parse(request.getToken());
if (!token.isValid(Instant.now())) {
    throw new HandoffAuthException("Token expired or invalid");
}
HandoffRequestService.process(token, request.getPayload());

// VIOLATION — process handoff without auth check
HandoffRequestService.process(null, request.getPayload());
```

Retry on transient failure (max 3, exponential backoff: 1s, 2s, 4s). Escalate to human on persistent failure.

## Z.AI Bridge
- Requires `ZHIPU_API_KEY` environment variable — fail fast if missing:
  ```java
  String apiKey = Objects.requireNonNull(
      System.getenv("ZHIPU_API_KEY"),
      "ZHIPU_API_KEY environment variable must be set"
  );
  ```
- HTTP transport: `HttpZaiMcpBridge` (not STDIO)
- GLM-4 model; configure model ID via `ZAI_MODEL_ID` env var (default: `glm-4`)

## Protocol Rules
- All API payloads use Java records (immutable, auto-serialised to JSON)
- Virtual threads for all I/O-bound operations (HTTP calls, engine queries)
- `ScopedValue<WorkflowContext>` for request context propagation across virtual threads
- Never `synchronized` in handlers — use `ReentrantLock` or lock-free structures
- Idempotency keys on all mutating operations (`launch_case`, `complete_workitem`)

## Testing
- `YawlMcpServerTest` must test tool registration, valid/invalid argument handling, and error responses
- Use real engine instances (H2 in-memory) for integration tests — no mocking of YAWL engine calls
- A2A tests excluded until SDK available on Maven Central (see `yawl-integration/README.md`)
