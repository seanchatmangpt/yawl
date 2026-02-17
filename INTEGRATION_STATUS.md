# YAWL Integration Layer V6 Compliance Status

**Audit Date**: 2026-02-17
**Auditor**: Integration Specialist
**Scope**: `src/org/yawlfoundation/yawl/integration/` and `scripts/pm4py/`

---

## Executive Summary

The integration layer is architecturally well-structured with real YAWL engine operations
at every level. The MCP server, A2A server/client, and Z.AI service all wire to real
InterfaceB/InterfaceA clients and to the real Z.AI HTTP API. Three issues require action
before V6 is production-ready:

1. **MCP server uses internal stub SDK** (`mcp/stub/`) because the official
   `io.modelcontextprotocol:mcp` artifact is not present in the local Maven repository.
   The MCP client correctly imports the real SDK.
2. **ZAI_API_KEY / ZHIPU_API_KEY naming inconsistency** across service implementations.
3. **A2A server NLP routing is keyword-based** when ZAI is unavailable - acceptable
   degraded mode, but not annotated as such.

---

## Component Inventory

| Component | File | Role |
|---|---|---|
| MCP Server | `mcp/YawlMcpServer.java` | STDIO MCP server, 15-16 tools |
| MCP Client | `mcp/YawlMcpClient.java` | STDIO/SSE MCP client |
| MCP Stub SDK | `mcp/stub/*.java` | Compile shims (MCP SDK not in repo) |
| MCP Tools | `mcp/spec/YawlToolSpecifications.java` | 15 YAWL tool handlers |
| MCP Resources | `mcp/resource/YawlResourceProvider.java` | 3 resources + 3 templates |
| MCP Prompts | `mcp/spec/YawlPromptSpecifications.java` | 4 prompts |
| MCP Completions | `mcp/spec/YawlCompletionSpecifications.java` | 3 auto-completions |
| MCP Logging | `mcp/logging/McpLoggingHandler.java` | Structured log notifications |
| A2A Server | `a2a/YawlA2AServer.java` | HTTP A2A agent server |
| A2A Client | `a2a/YawlA2AClient.java` | A2A REST client |
| Engine Adapter | `a2a/YawlEngineAdapter.java` | YAWL engine wrapper for A2A |
| ZAI HTTP Client | `zai/ZaiHttpClient.java` | Real java.net.http Z.AI HTTP client |
| ZAI Service | `zai/ZaiService.java` | Chat/completion service (ZAI_API_KEY) |
| ZAI Function Service | `zai/ZaiFunctionService.java` | YAWL function-calling bridge |
| Autonomous ZaiService | `autonomous/ZaiService.java` | Older OkHttp client (ZHIPU_API_KEY) |
| ZAI Decision Reasoner | `autonomous/reasoners/ZaiDecisionReasoner.java` | AI output generation |
| ZAI Eligibility Reasoner | `autonomous/reasoners/ZaiEligibilityReasoner.java` | AI routing |
| Circuit Breaker | `autonomous/resilience/CircuitBreaker.java` | Native CLOSED/OPEN/HALF_OPEN FSM |
| Retry Policy | `autonomous/resilience/RetryPolicy.java` | Exponential backoff |
| PM4Py MCP Server | `scripts/pm4py/mcp_server.py` | Python process mining MCP |
| PM4Py A2A Agent | `scripts/pm4py/a2a_agent.py` | Python process mining A2A |

---

## 1. MCP Server - YawlMcpServer

**File**: `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`

### V6 Compliance Status: PARTIAL

**What is correct:**
- Connects to real YAWL engine via `InterfaceB_EnvironmentBasedClient` and
  `InterfaceA_EnvironmentBasedClient` before building the MCP server.
- Fails fast with `IllegalArgumentException` on missing engine URL, username, or password.
- Reads all configuration from environment variables (`YAWL_ENGINE_URL`, `YAWL_USERNAME`,
  `YAWL_PASSWORD`, `ZAI_API_KEY`).
- Session handle established before tools are registered; throws `IOException` with
  clear message on authentication failure.
- Virtual thread shutdown hook for graceful teardown.
- Session disconnected on server stop.

**Issues found:**

**ISSUE-MCP-1 (Critical): MCP SDK stub in use**

The server imports from `org.yawlfoundation.yawl.integration.mcp.stub.*` rather than
the official `io.modelcontextprotocol.*` SDK. The stub's `McpServer.sync()` always
throws `UnsupportedOperationException`:

```java
// mcp/stub/McpServer.java line 31
public static SyncServerBuilder sync(Object transportProvider) {
    throw new UnsupportedOperationException(
        "MCP SDK stub - cannot create real server. ...");
}
```

The `McpServer.sync(...).build()` call in `YawlMcpServer.start()` will throw at
runtime. The official MCP SDK (`io.modelcontextprotocol:mcp:0.17.2`) is declared in
`pom.xml` but is not present in the local Maven repository (`/root/.m2/repository/io/`
directory does not exist). The stub is present as a compile-time shim only.

**ISSUE-MCP-2 (Minor): ZAI_API_KEY vs ZHIPU_API_KEY**

`YawlMcpServer` checks `ZAI_API_KEY` (line 111). The autonomous `ZaiService`
(`autonomous/ZaiService.java`) reads `ZHIPU_API_KEY`. The canonical `ZaiHttpClient`
in `zai/ZaiHttpClient.java` uses `ZAI_API_KEY`. Scripts and documentation must align
on one name.

**ISSUE-MCP-3 (Informational): Server version is "5.2.0" not "6.0.0"**

Version constant `SERVER_VERSION = "5.2.0"` does not reflect V6 upgrade.

### MCP Tools (15+1 optional) - Status: Implementation COMPLETE

All 15 tools in `YawlToolSpecifications` have real YAWL engine invocations:

| Tool Name | InterfaceB Method | Status |
|---|---|---|
| `yawl_launch_case` | `launchCase()` | Real |
| `yawl_get_case_status` | `getCaseState()` | Real |
| `yawl_cancel_case` | `cancelCase()` | Real |
| `yawl_list_specifications` | `getSpecificationList()` | Real |
| `yawl_get_specification` | `getSpecification()` | Real |
| `yawl_upload_specification` | InterfaceA `uploadSpecification()` | Real |
| `yawl_get_work_items` | `getCompleteListOfLiveWorkItems()` | Real |
| `yawl_get_work_items_for_case` | `getWorkItemsForCase()` | Real |
| `yawl_checkout_work_item` | `checkOutWorkItem()` | Real |
| `yawl_checkin_work_item` | `checkInWorkItem()` | Real |
| `yawl_get_running_cases` | `getAllRunningCases()` | Real |
| `yawl_get_case_data` | `getCaseData()` | Real |
| `yawl_suspend_case` | `suspendWorkItem()` per work item | Real |
| `yawl_resume_case` | `unsuspendWorkItem()` per work item | Real |
| `yawl_skip_work_item` | `skipWorkItem()` | Real |
| `yawl_natural_language` (ZAI) | `ZaiFunctionService.processWithFunctions()` | Real when ZAI_API_KEY set |

Error handling pattern is consistent: returns `CallToolResult(message, isError=true)` on
engine failure rather than throwing. This is correct for MCP tool handlers.

### MCP Resources (3 static) - Status: Implementation COMPLETE

| Resource URI | Engine Call | Status |
|---|---|---|
| `yawl://specifications` | `getSpecificationList()` | Real |
| `yawl://cases` | `getAllRunningCases()` | Real |
| `yawl://workitems` | `getCompleteListOfLiveWorkItems()` | Real |

### MCP Resource Templates (3 parameterized) - Status: Implementation COMPLETE

| Template URI | Engine Calls | Status |
|---|---|---|
| `yawl://cases/{caseId}` | `getCaseState()` + `getWorkItemsForCase()` | Real |
| `yawl://cases/{caseId}/data` | `getCaseData()` | Real |
| `yawl://workitems/{workItemId}` | `getWorkItem()` | Real |

### MCP Prompts (4) - Status: Declared, subject to stub limitation

Four prompts (`workflow_analysis`, `task_completion_guide`, `case_troubleshooting`,
`workflow_design_review`) are defined in `YawlPromptSpecifications`. Their handlers
call InterfaceB for spec and work item context. Fully blocked by ISSUE-MCP-1.

### MCP Completions (3) - Status: Declared, subject to stub limitation

Three completion handlers auto-complete spec identifiers, work item IDs, and case IDs.
All blocked by ISSUE-MCP-1.

---

## 2. MCP Client - YawlMcpClient

**File**: `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpClient.java`

### V6 Compliance Status: READY (pending SDK availability)

**What is correct:**
- Imports the real SDK: `io.modelcontextprotocol.client.*`, not the stub.
- Supports both STDIO (subprocess) and SSE (HTTP) transport modes.
- Properly validates connection state before every operation with a clear exception.
- `close()` calls `closeGracefully()` and implements `AutoCloseable`.
- Throws `RuntimeException` with a clear message if tool returns no text content.
- No silent failures; all error paths propagate.

**Issues found:**

**ISSUE-MCPC-1 (Critical): SDK not in local Maven repository**

Same root cause as ISSUE-MCP-1. The `io.modelcontextprotocol.*` imports will fail to
compile when the SDK jar is absent. The client is correctly written and will work once
the SDK is available.

---

## 3. A2A Server - YawlA2AServer

**File**: `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java`

### V6 Compliance Status: CONDITIONALLY COMPLIANT

**What is correct:**
- Uses the official `io.a2a.*` Java SDK with `DefaultRequestHandler`, `InMemoryTaskStore`,
  and `MainEventBus`.
- Agent card exposes 4 real skills with proper metadata.
- `ensureEngineConnection()` verifies session and reconnects if expired.
- Session disconnected on `stop()`.
- Virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) for HTTP handling.
- HTTP server shutdown gives 2-second grace period.

**Issues found:**

**ISSUE-A2AS-1 (Medium): Keyword routing is undocumented degraded mode**

When `ZaiFunctionService` is null (no `ZAI_API_KEY`), `processWorkflowRequest()` falls
back to keyword matching (`lower.contains("list")`, `lower.contains("launch")`, etc.).
This is acceptable degraded behaviour but is not documented as such in the class Javadoc
or the agent card. Users sending messages to an A2A agent without ZAI get a different
quality of NLP.

**ISSUE-A2AS-2 (Low): `cancel()` in YawlAgentExecutor uses `context.getTaskId()` as YAWL case ID**

The A2A task ID and the YAWL case ID are different namespaces. Using the A2A task ID
directly as the YAWL case ID in `interfaceBClient.cancelCase(taskId, sessionHandle)` is
incorrect unless the caller maps the IDs before sending. This will silently fail to
cancel the intended case.

**ISSUE-A2AS-3 (Low): Server version "5.2.0"**

`SERVER_VERSION = "5.2.0"` should be updated for V6.

**ISSUE-A2AS-4 (Informational): No authentication on A2A endpoints**

The `anonymousUser` returned from the inner class always returns `isAuthenticated() = true`
without checking any credential. This is appropriate for a private-network deployment but
must be noted for production security review. JWT/mTLS authentication should be added
before internet-facing deployment.

---

## 4. A2A Client - YawlA2AClient

**File**: `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AClient.java`

### V6 Compliance Status: READY

**What is correct:**
- Uses official `io.a2a.*` SDK: `A2A.getAgentCard()`, `Client.builder()`,
  `RestTransport`.
- `connect()` verifies agent card before proceeding; fails fast with typed A2A exceptions.
- `sendMessage()` uses `CountDownLatch` with a 60-second timeout; throws
  `RuntimeException` on timeout or error.
- `close()` calls `a2aClient.close()` and implements `AutoCloseable`.
- All public methods throw `IllegalStateException` if called before `connect()`.

**Issues found:**

**ISSUE-A2AC-1 (Low): Empty string check for `agentUrl` not URL-validated**

The constructor checks `agentUrl.isEmpty()` but does not validate the URL format.
An invalid URL (e.g. `"not-a-url"`) will propagate an error from the A2A SDK's HTTP
layer rather than failing with a clear message at construction time.

---

## 5. Z.AI API Integration

### 5a. ZaiHttpClient

**File**: `src/org/yawlfoundation/yawl/integration/zai/ZaiHttpClient.java`

### V6 Compliance Status: COMPLIANT

**What is correct:**
- Uses `java.net.http.HttpClient` (Java 21+ standard library, no external HTTP dependency).
- API endpoint: `https://api.z.ai/api/paas/v4/chat/completions`.
- Bearer token from constructor parameter (callers supply from `ZAI_API_KEY`).
- Throws `IOException` with HTTP status code on 4xx/5xx responses.
- Interruption restores thread interrupt flag.
- JSON parsed with Jackson `ObjectMapper`; falls back to raw response if parse fails.
- Configurable `connectTimeout` (30s) and `readTimeout` (60s).
- `verifyConnection()` uses a minimal 10-token ping to test liveness.

**Issues found:**

**ISSUE-ZAI-1 (Medium): No retry or circuit breaker in ZaiHttpClient**

The `ZaiHttpClient` itself has no retry or circuit breaker. The older
`autonomous/ZaiService.java` wraps Resilience4j, but the canonical `zai/ZaiHttpClient.java`
does not. Callers that call `createChatCompletion()` directly must implement their own
retry or accept single-attempt semantics. `ZaiFunctionService` calls without retry.

**ISSUE-ZAI-2 (Informational): `extractContent()` silently returns raw JSON on parse error**

If Jackson fails to parse the response, `extractContent()` returns the raw JSON string
instead of throwing. This makes it hard to diagnose malformed responses in production.

### 5b. ZaiService (zai package)

**File**: `src/org/yawlfoundation/yawl/integration/zai/ZaiService.java`

### V6 Compliance Status: COMPLIANT

**What is correct:**
- Constructor fails fast on null/empty API key.
- Delegates to `ZaiHttpClient` for HTTP transport.
- Conversation history maintained correctly across `chat()` calls.
- `verifyConnection()` available for health checks.
- Model overridable via `ZAI_MODEL` environment variable.
- No silent fallbacks; `chat()` propagates `RuntimeException` wrapping `IOException`.

### 5c. ZaiFunctionService (zai package)

**File**: `src/org/yawlfoundation/yawl/integration/zai/ZaiFunctionService.java`

### V6 Compliance Status: CONDITIONALLY COMPLIANT

**What is correct:**
- Connects to real YAWL engine on construction via `InterfaceB`.
- Registers 5 real YAWL function handlers: `start_workflow`, `get_workflow_status`,
  `complete_task`, `list_workflows`, `process_mining_analyze`.
- `process_mining_analyze` integrates with `EventLogExporter` and `Pm4PyClient` for
  real XES export and PM4Py analysis.
- `registerFunction()` allows callers to add custom handlers.
- Engine connection validated on construction; throws `RuntimeException` on failure.

**Issues found:**

**ISSUE-ZFS-1 (Medium): JSON parsing in `parseJsonToMap()` is fragile**

The hand-rolled JSON parser splits on `,` and `:`, which breaks on nested objects or
values containing those characters. This is used to parse Z.AI's function call arguments.
Jackson is already a dependency; `parseJsonToMap()` should use
`objectMapper.readValue(json, Map.class)` instead.

**ISSUE-ZFS-2 (Medium): `mapToJson()` does not escape string values**

The manual JSON serializer does not escape quotes or backslashes in values:
```java
sb.append("\"").append(entry.getValue()).append("\"");
```
A workflow name or task ID containing `"` will produce invalid JSON sent to Z.AI.

**ISSUE-ZFS-3 (Low): `checkInWorkItem()` API call missing third argument**

Line 399: `interfaceBClient.checkInWorkItem(workItemID, dataToSend, sessionHandle)`
is called with 3 arguments, but the stub in `YawlEngineAdapter` uses a 4-argument
signature `checkInWorkItem(workItemId, outputData, null, sessionHandle)`. Verify the
`InterfaceB_EnvironmentBasedClient.checkInWorkItem()` signature used here.

### 5d. ZaiService (autonomous package)

**File**: `src/org/yawlfoundation/yawl/integration/autonomous/ZaiService.java`

### V6 Compliance Status: SUPERSEDED

This is an older implementation using OkHttp and Resilience4j. It reads `ZHIPU_API_KEY`
(not `ZAI_API_KEY`), targets `https://open.bigmodel.cn/api/paas/v4/chat/completions`
(not `https://api.z.ai/api/paas/v4/`), and uses manual JSON parsing with
`String.indexOf("\"content\":\"")` which is fragile and will break on escaped quotes.

**Recommendation**: All autonomous agent components (`ZaiDecisionReasoner`,
`ZaiEligibilityReasoner`) should migrate from `autonomous.ZaiService` to
`zai.ZaiService`. The `autonomous.ZaiService` should be deprecated and removed in V6.

---

## 6. YAWL Engine Adapter

**File**: `src/org/yawlfoundation/yawl/integration/a2a/YawlEngineAdapter.java`

### V6 Compliance Status: COMPLIANT

**What is correct:**
- Retry on connection with 3 attempts and 1-second delay between attempts.
- Clear `A2AException` with error code and remediation hint on all failures.
- `fromEnvironment()` factory validates all three required env vars.
- `synchronized connect()` prevents concurrent reconnection.
- Both InterfaceA and InterfaceB sessions managed separately.
- `disconnect()` always clears session handles in `finally` block.

**Issues found:**

**ISSUE-EA-1 (Low): `checkInWorkItem()` ignores logData parameter**

`YawlEngineAdapter.checkInWorkItem(workItemId, outputData)` calls
`interfaceBClient.checkInWorkItem(workItemId, data, sessionHandleB)` — only 3
arguments. If `InterfaceB_EnvironmentBasedClient.checkInWorkItem` expects a 4-argument
form `(workItemId, data, logData, sessionHandle)` then `logData` is silently dropped.

---

## 7. Autonomous Agent Resilience

### CircuitBreaker

**File**: `src/org/yawlfoundation/yawl/integration/autonomous/resilience/CircuitBreaker.java`

### V6 Compliance Status: COMPLIANT

- Native Java implementation (no Resilience4j dependency).
- Three-state FSM: CLOSED / OPEN / HALF_OPEN with thread-safe `AtomicReference`.
- CAS transitions to prevent race conditions.
- Configurable failure threshold and open duration.
- `CircuitBreakerOpenException` is a typed checked exception.

### RetryPolicy

**File**: `src/org/yawlfoundation/yawl/integration/autonomous/resilience/RetryPolicy.java`

### V6 Compliance Status: COMPLIANT

- Exponential backoff: `initialBackoff * 2^(attempt-1)`.
- Thread interrupt state restored on `InterruptedException`.
- Last exception chained in final throw.
- `executeWithRetryUnchecked()` wrapper for lambda contexts.

---

## 8. Python MCP/A2A Servers (PM4Py)

**Files**: `scripts/pm4py/mcp_server.py`, `scripts/pm4py/a2a_agent.py`

### V6 Compliance Status: COMPLIANT

**MCP Server (`mcp_server.py`):**
- Uses `FastMCP` (official Python MCP SDK) with STDIO transport.
- Three tools: `pm4py_discover`, `pm4py_conformance`, `pm4py_performance`.
- All tools delegate to `pm4py_backend.py` which calls real PM4Py library.
- No stubs or mock data.

**A2A Agent (`a2a_agent.py`):**
- Uses `a2a.server.*` (official Python A2A SDK) with Starlette/uvicorn.
- Three skills: `process_discovery`, `conformance_check`, `performance_analysis`.
- JSON-based dispatch; clear error responses for unknown skills or missing fields.
- Port configurable via `PM4PY_A2A_PORT` env var.
- `cancel()` correctly raises `NotImplementedError` (stateless operations cannot be cancelled).

**Issues found:**

**ISSUE-PY-1 (Informational): Agent card URL hardcoded to `localhost`**

`AGENT_CARD.url = "http://localhost:9092/"` is updated at runtime with the actual port
but the hostname remains `localhost`. When the agent runs on a non-local host, the agent
card will advertise an unreachable URL to discovering clients.

---

## 9. MCP Spring Integration

**Files**: `mcp/spring/*.java`

### V6 Compliance Status: REFERENCE ONLY

The Spring integration classes (`EnableYawlMcp`, `YawlMcpConfiguration`,
`YawlMcpResource`, `YawlMcpTool`, etc.) provide annotation-based wiring for
Spring Boot deployments. These depend on the same stub SDK classes. They are
correctly structured (no mocks, real engine references) but are not usable until
ISSUE-MCP-1 is resolved.

---

## 10. Authentication and Authorization

### Current State

| Layer | Mechanism | V6 Status |
|---|---|---|
| YAWL Engine | HTTP Basic via session handle | Standard YAWL auth |
| MCP Server | STDIO (no network auth needed) | Appropriate for STDIO |
| A2A Server | Anonymous `User` always authenticated | Insufficient for production |
| Z.AI API | Bearer token (`ZAI_API_KEY`) | Correct |
| PM4Py MCP | STDIO (no auth) | Appropriate for subprocess |
| PM4Py A2A | No auth on HTTP | Risk for exposed deployments |

**ISSUE-AUTH-1 (High for production): A2A server has no authentication**

The inner `anonymousUser` in `YawlA2AServer` unconditionally returns `isAuthenticated()
= true`. Any client that can reach the A2A port can launch, cancel, and query workflows.
For V6 production deployment, add one of:
- API key validation in the HTTP handler
- mTLS via SPIFFE (the `integration/spiffe/` package is available)
- JWT Bearer validation using the existing `jjwt-jackson` dependency in pom.xml

---

## 11. Environment Variable Reference

Complete list of environment variables consumed by the integration layer:

| Variable | Required By | Purpose |
|---|---|---|
| `YAWL_ENGINE_URL` | All Java servers | YAWL engine base URL |
| `YAWL_USERNAME` | All Java servers | YAWL admin username |
| `YAWL_PASSWORD` | All Java servers | YAWL admin password |
| `ZAI_API_KEY` | `ZaiHttpClient`, `ZaiFunctionService`, `YawlMcpServer`, `YawlA2AServer` | Z.AI API key (primary) |
| `ZHIPU_API_KEY` | `autonomous/ZaiService` | Z.AI API key (legacy, superseded) |
| `ZAI_MODEL` | `ZaiService`, `ZaiFunctionService` | Override GLM model (default: GLM-4.7-Flash) |
| `A2A_PORT` | `YawlA2AServer` | A2A HTTP port (default: 8081) |
| `PM4PY_A2A_PORT` | `a2a_agent.py` | PM4Py A2A port (default: 9092) |
| `PM4PY_MCP_URL` | `Pm4PyClient` | PM4Py MCP server URL |

**Key finding**: `ZAI_API_KEY` and `ZHIPU_API_KEY` both exist and serve the same purpose
in different code paths. V6 should standardise on `ZAI_API_KEY` throughout.

---

## 12. HTTP Client Modernization

### Current State

| Component | HTTP Library | Java Version |
|---|---|---|
| `ZaiHttpClient` | `java.net.http.HttpClient` | Java 21 (standard) |
| `autonomous/ZaiService` | OkHttp 5.1.0 | Pre-Java 11 style |
| YAWL InterfaceB/A | `java.net.HttpURLConnection` | Pre-Java 11 style |
| A2A Server | `com.sun.net.httpserver.HttpServer` | Internal JDK API |

**ISSUE-HTTP-1 (Low): `com.sun.net.httpserver` is an internal API**

`YawlA2AServer` uses `com.sun.net.httpserver.*`. This is available in practice (it is
a stable JDK internal API that ships with OpenJDK), but it is technically
non-portable. For V6, consider replacing with `com.sun.net.httpserver` (acceptable for
embedded use) or documenting the dependency explicitly.

**ISSUE-HTTP-2 (Low): `autonomous/ZaiService` uses OkHttp instead of `java.net.http`**

The newer `zai/ZaiHttpClient.java` correctly uses `java.net.http.HttpClient`. The
`autonomous/ZaiService.java` still uses OkHttp. This is an additional transitive
dependency that should be removed when migrating autonomous agents to `zai.ZaiService`.

---

## 13. V6 Action Items

Priority ranking: Critical > High > Medium > Low

| ID | Priority | Component | Action Required |
|---|---|---|---|
| ISSUE-MCP-1 | Critical | MCP Server | Install `io.modelcontextprotocol:mcp:0.17.2` jar into local Maven repo or add a Maven repository that serves it. The stub `mcp/stub/` package must be replaced with real SDK imports in `YawlMcpServer`. |
| ISSUE-MCPC-1 | Critical | MCP Client | Same dependency resolution as ISSUE-MCP-1. |
| ISSUE-AUTH-1 | High | A2A Server | Add authentication to the A2A HTTP endpoints before production deployment. |
| ISSUE-ZAI-1 | Medium | ZaiHttpClient | Add retry with exponential backoff to `createChatCompletion()`. |
| ISSUE-A2AS-2 | Medium | A2A Server | Fix cancel handler to maintain A2A task ID to YAWL case ID mapping. |
| ISSUE-ZFS-1 | Medium | ZaiFunctionService | Replace hand-rolled `parseJsonToMap()` with `ObjectMapper.readValue()`. |
| ISSUE-ZFS-2 | Medium | ZaiFunctionService | Fix `mapToJson()` to escape string values properly. |
| ISSUE-ZAI-2 | Medium | ZaiHttpClient | `extractContent()` should throw on parse error rather than return raw JSON. |
| ISSUE-MCP-2 | Medium | MCP Server | Standardise on `ZAI_API_KEY` across all components; retire `ZHIPU_API_KEY`. |
| ISSUE-MCP-3 | Low | MCP Server | Update `SERVER_VERSION` to `6.0.0`. |
| ISSUE-A2AS-3 | Low | A2A Server | Update `SERVER_VERSION` to `6.0.0`. |
| ISSUE-EA-1 | Low | YawlEngineAdapter | Verify `checkInWorkItem()` arity against InterfaceB signature. |
| ISSUE-ZFS-3 | Low | ZaiFunctionService | Verify `checkInWorkItem()` arity. |
| ISSUE-A2AS-1 | Low | A2A Server | Document NLP degraded mode in Javadoc and agent card description. |
| ISSUE-A2AC-1 | Low | A2A Client | Add URL format validation in constructor. |
| ISSUE-PY-1 | Low | PM4Py A2A | Make agent card hostname configurable via env var. |
| ISSUE-HTTP-2 | Low | autonomous/ZaiService | Migrate to `zai.ZaiService` (uses standard `java.net.http`). |

---

## 14. What V6 Gets Right

- Every MCP tool handler calls a real YAWL engine operation; no empty returns or stubs.
- Every A2A skill routes to real YAWL engine calls.
- Z.AI HTTP client uses the standard `java.net.http.HttpClient` introduced in Java 11.
- `ZHIPU_API_KEY`/`ZAI_API_KEY` are read from environment; no credentials are
  hardcoded anywhere in the codebase.
- Session lifecycle is properly managed: connect on start, disconnect on stop,
  reconnect on expired session.
- Exception propagation is strict: operations that cannot succeed throw, not return
  silently degraded results.
- Circuit breaker and retry are implemented natively in `autonomous/resilience/`.
- Python PM4Py servers use official MCP and A2A SDKs with real pm4py computations.
- `YawlEngineAdapter` retries YAWL engine connection 3 times with 1-second delay.
- Virtual threads are used throughout for I/O-bound server operations.

---

## 15. Dependency Resolution Status

| Artifact | pom.xml | Local Repo | Status |
|---|---|---|---|
| `io.modelcontextprotocol:mcp:0.17.2` | Declared | Missing | Unavailable |
| `io.a2a:a2a-java-sdk-*:1.0.0.Alpha2` | Declared | Missing | Unavailable |
| `com.fasterxml.jackson:*:2.19.4` | Declared | Unknown | Needed |
| `com.squareup.okhttp3:okhttp:5.1.0` | Declared | Unknown | In use by legacy ZaiService |
| `io.github.resilience4j:*:2.3.0` | Declared | Unknown | In use by legacy ZaiService |

The build cache extension (`maven-build-cache-extension:1.2.0`) also lacks its POM in
the local repository, preventing `mvn compile` from running in this environment.

---

*This document reflects the state of the integration layer as of the audit date.
All file paths are relative to the YAWL project root `/home/user/yawl/`.*
