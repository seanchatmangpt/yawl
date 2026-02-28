# Explanation: External Integration Architecture

Understanding how YAWL connects to AI agents and external systems.

---

## The Integration Problem

Traditional workflow systems are **islands**:
- Workflow runs inside the system
- Humans manually execute tasks
- External systems integrated via brittle middleware

Modern workflows need:
- **Agent-first design** — AI agents are first-class users
- **Protocol standards** — Not vendor lock-in
- **Real-time connectivity** — Low latency
- **Observability** — See what agents are doing

YAWL integration solves this with MCP and A2A.

---

## MCP: Model Context Protocol

### Philosophy

MCP treats the workflow engine as a **tool provider**:

```
LLM Agent (Claude, GPT, etc.)
    ↓ (calls)
MCP Server (YAWL)
    ↓ (exposes as tools)
YAWL Engine
```

The agent has a **toolkit**:

```
Claude: "I need to approve an order"
    ↓
Looks in toolkit: [yawl_list_cases, yawl_checkout_item, yawl_checkin_item, ...]
    ↓
Calls: yawl_checkin_item(workItemID, outputData)
    ↓
YAWL Engine executes workflow transition
```

### Key Design Decisions

#### Decision 1: Stateless Tools

Each tool is **independent**. No session state:

```
Bad:
    Agent calls: yawl_start_session() → session_id
    Agent calls: yawl_list_cases(session_id) → cases
    Problem: session_id expires, agent confused

Good:
    Agent calls: yawl_list_cases() → cases
    Agent calls: yawl_checkout_item(workItemID) → item
    No session needed
```

**Benefit:** Resilient to timeouts and agent crashes.

#### Decision 2: Request-Response (Not Event-Driven)

Tools follow request-response semantics:

```
Request: yawl_checkout_item(workItemID="42:review")
    ↓
Server: Process checkout
    ↓
Response: {workItemID, checkedOutData}
    ↓
Agent: Proceed
```

Not pub-sub or streaming:

```
Bad: Agent subscribes to case updates
Problem: Agent expects real-time updates, but disconnects
Problem: Server has dangling subscriptions
```

**Benefit:** Matches agent expectations (LLMs are request-response).

#### Decision 3: Explicit Tool Registration

Tools must be registered (no auto-discovery):

```java
@Component
public class ApproveOrderTool implements YawlMcpTool {
    @Override
    public String toolName() { return "approve_order"; }
    // ...
}
```

**Benefit:** Security (can whitelist what agents see).

### Tool Design Pattern

Every tool follows:

```java
public interface YawlMcpTool {
    String toolName();              // Tool identifier
    String toolDescription();       // What it does (for LLM)
    McpToolOutput execute(input);   // Execute it
    Map<String, String> schema();   // Input schema
}
```

**Why?**
- Declarative: describe input/output for LLM
- Type-safe: schema prevents invalid calls
- Auditable: every call is logged
- Idempotent: calling twice = same result

---

## A2A: Agent-to-Agent Protocol

### Philosophy

A2A enables **agent orchestration**:

```
Agent 1: Order Reviewer
    ↓ (delegates to)
Agent 2: Compliance Checker
    ↓ (delegates to)
Agent 3: Final Approver
    ↓
Result
```

Unlike MCP (agent ↔ system), A2A is (agent ↔ agent).

### Key Design

#### Skill Definition

Agents expose **skills** (not tools):

```java
public interface YawlA2ASkill {
    String skillName();             // e.g. "compliance_review"
    String skillDescription();      // "Review order for compliance"
    SkillOutput execute(input);     // Execute
    Map<String, String> schema();   // Input/output
}
```

**Difference from MCP tools:**
- Tools are stateless operations (approve, reject)
- Skills are workflows (review → evaluate → report)
- Agents coordinate via skills

#### Delegation Model

```
Agent A: "Please review this order"
    ↓
Agent B: "I need compliance info"
    ↓
Agent B: calls skill("compliance_review")
    ↓
Agent C: executes compliance_review skill
    ↓
Agent C: returns results to Agent B
    ↓
Agent B: synthesizes and returns to Agent A
    ↓
Agent A: makes decision
```

**Benefit:** Specialized agents can compose complex workflows.

---

## Observability & Tracing

### Event Tracing

Every MCP tool call and A2A skill execution is traced:

```
Span: mcp_tool_execution
  ├─ tool_name: "yawl_checkout_item"
  ├─ tool_input: {workItemID: "42:review"}
  ├─ duration_ms: 150
  ├─ status: success
  └─ tags:
     └─ agent: "order_approver"
     └─ case_id: "42"
     └─ engine_version: "6.0.0"
```

### Metrics

```
integration.mcp.tool_invocation{tool="yawl_checkout_item"} 1542
integration.mcp.tool_duration{tool="yawl_checkout_item", percentile="0.95"} 180
integration.a2a.agent_utilization{agent="compliance_checker"} 0.75
integration.dedup.cache_hit_rate 0.92
```

### Logging

Every interaction logged to structured log:

```json
{
  "timestamp": "2026-02-28T10:05:42Z",
  "event": "tool_invocation",
  "tool_name": "yawl_checkout_item",
  "caller": "agent_order_approver",
  "duration_ms": 150,
  "result": "success",
  "case_id": "42",
  "trace_id": "f8d7c2e1-9abc-4def-8901-234567890abc"
}
```

---

## Security Model

### Authentication

#### Option 1: OAuth2 (Recommended)

```
Agent sends: Authorization: Bearer <JWT>

Server verifies:
  1. JWT signature (HS256 or RS256)
  2. Expiration time
  3. Scopes (yawl:agent, yawl:operator, ...)

If valid: Grant access
If invalid: Return 401 Unauthorized
```

#### Option 2: SPIFFE/SVID (Zero-Trust)

```
Agent certificates itself with: /var/run/secrets/workload-api/socket

Server verifies:
  1. SVID signature
  2. Trust domain (same organization)
  3. Workload name (e.g. "order-approver")

If valid: Grant access (mTLS)
If invalid: Connection rejected
```

### Authorization

After authentication, check scopes:

```java
@RequiresScope(YawlOAuth2Scopes.AGENT)
public McpToolOutput execute(McpToolInput input) {
    // Only agents can call this
}

@RequiresScope(YawlOAuth2Scopes.OPERATOR)
public void checkInWorkItem(String workItemID, String data) {
    // Only operators and agents can check in
}
```

---

## Idempotency & Deduplication

### Problem: Duplicate Requests

```
Agent: checkInWorkItem(workItemID="42:review", data=...)
    ↓
Network timeout (no response)
    ↓
Agent retries: checkInWorkItem(workItemID="42:review", data=...)
    ↓
System processes twice!
    ↓
Result: Duplicate completion
```

### Solution: Idempotency Keys

```
Agent sends:
  X-Idempotency-Key: f8d7c2e1-9abc-4def-8901-234567890abc
  Request: checkInWorkItem(...)

Server:
  1. Check if idempotency key seen before
  2. If yes: return cached result (exact same response)
  3. If no: process, cache result, return

Retry:
  Agent retries with same X-Idempotency-Key
  Server: "Oh, I already did this. Here's the result."
```

**Guarantee:** Exactly-once semantics (at-least-once API + idempotency).

---

## Scalability Patterns

### Pattern 1: Single MCP Server

```
Agents (10s)
    ↓
MCP Server (Spring Boot, 1 instance)
    ↓
YAWL Engine (local)
```

**Use when:** <100 agents, <10 req/sec

**Pros:** Simple
**Cons:** Single point of failure

### Pattern 2: Scaled MCP Servers

```
Agents (100s)
    ↓
Load Balancer
├─ MCP Server 1 (8 cores)
├─ MCP Server 2 (8 cores)
└─ MCP Server 3 (8 cores)
    ↓
Shared Cache (Redis)
    ↓
YAWL Engine
```

**Use when:** 100-1000 agents, 10-100 req/sec

**Pros:** High availability
**Cons:** Cache consistency, session affinity

### Pattern 3: Regional Distribution

```
Region A:
├─ Agents (1000s)
├─ MCP Servers (3)
└─ Local YAWL Engine

Region B:
├─ Agents (1000s)
├─ MCP Servers (3)
└─ Local YAWL Engine

Central:
└─ Global Case Registry (for cross-region workflows)
```

**Use when:** 1000+ agents across multiple regions

**Pros:** Low latency per region
**Cons:** Cross-region coordination complexity

---

## Error Handling Patterns

### Error 1: Tool Timeout

**Cause:** YAWL engine slow or unresponsive

**Agent behavior:**
```
tool_timeout_seconds = 5
wait_for_response()
    ↓
Timeout!
    ↓
Retry once
    ↓
Still timeout?
    ↓
Fail gracefully, log error
```

### Error 2: Tool Not Found

**Cause:** Tool name typo or not registered

**Server behavior:**
```
toolName = "yawl_checkout_workitem"  // Missing underscore
Lookup tool_registry[toolName]
    ↓
Not found!
    ↓
Return: error 404 with message "Tool yawl_checkout_workitem not found"
    ↓
Agent: "I made a typo. Fix code and retry."
```

### Error 3: Agent Crashes

**Cause:** Agent process dies mid-operation

**System behavior:**
```
Idempotency key still in cache:
  Key: f8d7c2e1-9abc-4def
  Value: {status: success, result: {...}}

New agent connects:
  Resends same idempotency key
  Server: "I already processed this. Here's the result."
  New agent continues as if no crash
```

---

## Design Principles

### 1. Tools are Dumb

Tools don't reason. They execute exactly what's requested:

```
Good:
    input: {caseID: "42"}
    output: {workItems: [...]}
    (tool just returns data)

Bad:
    input: {caseID: "42"}
    tool logic: "Hmm, this is urgent. Escalate?"
    (tool is reasoning)
```

**Why:** Keep reasoning in agents, execution in tools.

### 2. Agents are Smart

Agents decide what to do based on tool results:

```
Agent: "I got these work items. Which should I prioritize?"
    ↓
Agent checks: urgency, deadline, cost
    ↓
Agent decides: Process high-cost items first
    ↓
Agent calls: yawl_checkout_item(workItemID="urgent_one")
```

### 3. Observability First

Every interaction logged and traceable:

```
Debug a problem:
  1. Agent says: "I tried to approve but failed"
  2. Check logs: grep trace_id=... logs/
  3. See: Tool returned error 503 (engine down)
  4. Fix: Restart engine
  5. Retry: Agent continues
```

---

## Trade-Offs

| Approach | Complexity | Latency | Reliability | Cost |
|----------|-----------|---------|------------|------|
| MCP Only | Low | 100ms | Medium | Low |
| A2A Only | High | 500ms | High | High |
| MCP + A2A | Medium | 300ms | High | Medium |

**Recommendation:**
1. Start with **MCP** (simple, fast)
2. Add **A2A** when you have 2+ autonomous agents needing coordination
3. Run both in production (MCP for simple ops, A2A for complex orchestration)

---

## See Also

- **Tutorial:** `docs/tutorials/integration-getting-started.md`
- **Reference:** `docs/reference/integration-api.md`
- **How-To:** `docs/how-to/configure-agents.md`
