# Z.AI ↔ YAWL Integration Layer: Complete Research Package

**Status**: Phase 1 Complete (Research & Design)
**Date**: 2026-02-21
**Author**: YAWL Integration Specialist
**Scope**: Z.AI autonomous agents invoking YAWL workflows via MCP

---

## 📚 Deliverables (This Package)

This research package contains **3 comprehensive documents + implementation guidance**:

### Document 1: **Z.AI-YAWL-INTEGRATION-SUMMARY.md** (2 pages, exec summary)
- **Best for**: Decision makers, ops teams, quick reference
- **Contents**:
  - Z.AI agent lifecycle & tool use pattern
  - 8 core tools exposed to Z.AI (specification, error handling)
  - Interaction flow (architecture sequence diagram)
  - Idempotency & error classification model
  - Authentication (JWT service account)
  - Code structure (new modules to build)
  - Test plan & 8.5-day effort estimate
  - Deployment & operations checklist
  - Webhook architecture (optional, advanced)
  - Success criteria & timeline

### Document 2: **Z.AI-YAWL-INTEGRATION-DESIGN.md** (full technical spec, ~4000 words)
- **Best for**: Architects, engineers, PoC planning
- **Contents**:
  - Deep dive: Z.AI agent lifecycle + tool contract
  - Tool inventory with JSON schemas
  - Complete interaction flow (Z.AI → MCP → YAWL Engine)
  - Idempotency implementation (cache store, TTL)
  - Backoff & timeout strategy
  - YawlMcpServer extension architecture
  - Tool handler pseudocode (CaseSubmitTool, IdempotencyStore)
  - Event subscription & webhook publisher
  - ASCII architecture diagrams
  - Error handling examples (invalid spec, timeout, idempotent retry)
  - Environment variables (Z.AI side, YAWL side)
  - Docker/Kubernetes deployment example
  - Module dependencies & PoC estimate (detailed)
  - Known constraints & mitigations
  - References & further reading

### Document 3: **Z.AI-YAWL-INTEGRATION-EXAMPLES.md** (runnable code examples)
- **Best for**: Developers, integration engineers
- **Contents**:
  - **Example 1**: Invoice agent (full Python MCP loop)
  - **Example 2**: Idempotency test (retry behavior)
  - **Example 3**: Error handling (transient vs permanent)
  - **Example 4**: Status polling loop (5s intervals until completion)
  - **Example 5**: Work item handling (checkout, complete workflow)
  - **Example 6**: MCP tool discovery (how Z.AI finds YAWL)
  - Output traces showing agent behavior

---

## 🎯 Key Design Principles

### 1. Idempotency (Correctness Under Retry)
- Z.AI retries on transient failures (network timeout, 503).
- YAWL uses **idempotency key → result cache** to ensure same case_id returned on retry.
- No duplicate workflows created.
- Cache TTL: 3600s (1 hour).

### 2. Async-First (Non-Blocking)
- Tool calls return immediately with case_id.
- Z.AI polls `cases/status` every 5s (or subscribes via webhook).
- No blocking waits; agent can handle other tasks in parallel.

### 3. Auth Reuse (Existing Infrastructure)
- Leverage `JwtAuthenticationProvider` from YAWL A2A auth stack.
- Service account JWT (32+ char secret, HMAC-SHA256).
- Permissions: `workflows:launch`, `workflows:query`, `workitems:manage`.

### 4. Error Clarity (Intelligent Retry Logic)
- **Transient (503)**: Z.AI retries with exponential backoff.
- **Permanent (400/403/404)**: Z.AI fails immediately (no retry).
- Clear HTTP status codes + error types enable Z.AI decision logic.

### 5. Event-Driven (Optional Webhooks)
- Polling: Default, always works.
- Webhooks: Requires outbound connectivity, enables real-time notifications.
- HMAC-SHA256 signature for security.

---

## 📋 Implementation Roadmap (Phase 2)

### Week 1: Core Tools
```
Day 1-2: IdempotencyStore (interface + InMemory) + unit tests
Day 2-3: CaseSubmitTool with idempotency + error handling
Day 3-4: CaseStatusTool + polling support
Day 4: Integration test: Z.AI submit → poll loop
```

### Week 2: Work Item & Advanced
```
Day 1-2: WorkItemListTool, CheckoutTool, CompleteTool
Day 2: Extend SpecificationsTool with schema endpoints
Day 3-4: E2E integration tests (agent simulation)
Day 5: Performance benchmarks
```

### Week 3: Webhooks & Deployment
```
Day 1-2: CaseEventPublisher + WebhookCallbackService
Day 2-3: Webhook retry + signature validation
Day 3-4: Production deployment config (Redis idempotency backend)
Day 5: Operational runbook
```

### Week 4: Documentation & Rollout
```
Day 1-2: OpenAPI 3.0 spec for all tools
Day 2-3: Z.AI agent examples (Python, JavaScript)
Day 3-4: Troubleshooting guide
Day 5: Operator training + go-live readiness
```

**Total**: 8.5 engineer-days for core PoC (submit + status + workitems).
**Extend**: +2 days for webhooks, +1 day for perf/security hardening.

---

## 🛠️ Tools Exposed to Z.AI

| Tool | Latency | Idempotent | Use Case |
|------|---------|-----------|----------|
| **cases/submit** | 500ms | ✓ | Launch workflow case |
| **cases/status** | 100ms | ✓ | Poll case state |
| **cases/subscribe** | 50ms | ✓ | Webhook notifications |
| **workitems/list** | 150ms | ✓ | Discover pending tasks |
| **workitems/checkout** | 200ms | ✓ | Acquire work item |
| **workitems/complete** | 300ms | ✓ | Finish work item |
| **specifications/list** | 100ms | ✓ | List workflows |
| **specifications/describe** | 200ms | ✓ | Get workflow schema |

---

## 🔐 Authentication Flow

```
Z.AI Context                    YAWL MCP Server
─────────────────────────────────────────────────
YAWL_MCP_JWT_SECRET             A2A_JWT_SECRET
  = "abc123...xyz" (32+ chars)    = "abc123...xyz" (shared)

Z.AI generates JWT on startup:
  {sub: "zai-invoice-processor", aud: "yawl-mcp",
   scope: "workflows:launch workflows:query ...", exp: <60min>}

Z.AI tool call:
  POST /mcp/tools/invoke
  Authorization: Bearer <jwt>
  {tool: "cases/submit", input: {...}}
        ↓
YAWL validates signature (HMAC-SHA256)
YAWL verifies expiry + audience + scope claims
YAWL → AuthenticatedPrincipal (subject, permissions, expiry)
YAWL checks permission: "workflows:launch" ∈ scopes
       ↓
✓ Tool executes
✗ 403 Forbidden (insufficient permission)
✗ 401 Unauthorized (invalid/expired token)
```

---

## 📊 Interaction Diagram (High-Level)

```
Z.AI Agent (Cloud)
    │
    ├─ Discover tools: GET /.well-known/mcp.json
    │  Response: tool catalog (JSON schemas)
    │
    ├─ Submit: POST /tools/invoke (cases/submit)
    │  Input: {spec_id, case_data, idempotency_key}
    │  Auth: Bearer <jwt>
    │  Response: {case_id, status, created_at}
    │
    ├─ Poll: POST /tools/invoke (cases/status) [every 5s]
    │  Input: {case_id}
    │  Response: {status, progress, running_tasks, output}
    │
    └─ Repeat until status ∈ {completed, failed, error}

         ↓ MCP (HTTP)

YawlMcpServer (port 8080)
    │
    ├─ JwtAuthenticationProvider validates bearer token
    ├─ ToolRegistry dispatches to handlers
    ├─ CaseSubmitHandler checks IdempotencyStore
    │  ├─ Cache hit → return cached case_id (idempotent!)
    │  └─ Cache miss → call engine.launchCase(), store result
    ├─ CaseStatusHandler calls engine.getCaseState()
    └─ [Optional] WebhookCaseEventPublisher notifies Z.AI on completion

         ↓ InterfaceB (existing)

YAWL Engine (YStatelessEngine)
    │
    ├─ launchCase(spec_id, caseData)
    │  ├─ Load specification
    │  ├─ Create case instance
    │  ├─ Set variables
    │  └─ Fire enabled tasks → case executing
    │
    ├─ EventBus publishes:
    │  ├─ CaseCreatedEvent
    │  ├─ WorkItemCreatedEvent
    │  └─ CaseStateChangeEvent
    │
    └─ getCaseState(), getWorkItemsForCase() [polling support]
```

---

## ✅ Success Criteria (Phase 2 Testing)

- [ ] Z.AI agent successfully submits YAWL case via MCP tool
- [ ] Idempotent retry: same idempotency_key → same case_id (no duplicates)
- [ ] Status polling: agent receives case state updates every 5s
- [ ] Case completion: status changes from "executing" → "completed" or "failed"
- [ ] Work items: agent can list, checkout, and complete tasks
- [ ] Error handling: 503 triggers retry; 400/403 fails immediately
- [ ] Auth enforcement: missing/expired JWT returns 401; insufficient scope returns 403
- [ ] Performance: tool response time <500ms (p95)
- [ ] Concurrency: multiple Z.AI agents invoking in parallel (no resource contention)
- [ ] Webhooks (optional): case completion triggers HTTP POST to Z.AI callback_url
- [ ] Documentation: API spec, examples, troubleshooting guide complete

---

## 🚀 Deployment Checklist

### Pre-Deployment
- [ ] YawlMcpServer built with new tool handlers
- [ ] IdempotencyStore configured (memory for dev, Redis for prod)
- [ ] JWT secret shared between Z.AI and YAWL (32+ chars, HMAC-SHA256)
- [ ] YAWL engine version 6.0.0+ running
- [ ] Network: Z.AI can reach YAWL MCP server (port 8080)
- [ ] Logging: MCP server logs tool invocations (debug mode)

### Production Deployment
- [ ] YAWL MCP server: 3+ replicas (HA)
- [ ] Idempotency store: Redis cluster (durable, distributed)
- [ ] Webhooks: enabled with retry + HMAC signature
- [ ] Monitoring: tool response time, error rates, case completion times
- [ ] Alerting: 503 errors, idempotency store connection failure, webhook delivery failures
- [ ] Capacity planning: estimate Z.AI agents × tools/sec → thread pool sizing

### Operational Readiness
- [ ] Runbook: how to debug failed tool calls
- [ ] Playbook: what to do if YAWL engine is down (Z.AI waits for recovery)
- [ ] Escalation: who to contact if MCP server is down (ops team)
- [ ] Metrics: dashboard showing case submission rate, completion rate, latency

---

## 📖 Document Index

| File | Pages | Audience | Key Sections |
|------|-------|----------|--------------|
| `Z.AI-YAWL-INTEGRATION-SUMMARY.md` | 2 | Execs, ops, quick ref | Architecture, tools, auth, effort estimate, timeline |
| `Z.AI-YAWL-INTEGRATION-DESIGN.md` | ~4K words | Architects, engineers | Technical deep-dive, implementation pseudocode, error examples, PoC roadmap |
| `Z.AI-YAWL-INTEGRATION-EXAMPLES.md` | 6 examples | Developers, integrators | Runnable Python code, test scenarios, output traces |
| `Z.AI-INTEGRATION-INDEX.md` | (this file) | Everyone | Overview, roadmap, checklist, quick reference |

---

## 🔗 Related YAWL Components

**Existing Infrastructure (Reuse)**:
- `/yawl/integration/a2a/auth/JwtAuthenticationProvider.java` — JWT validation
- `/yawl/integration/a2a/auth/AuthenticatedPrincipal.java` — Principal + permissions
- `/yawl/integration/mcp/YawlMcpServer.java` — MCP server base
- `/yawl/engine/interfce/interfaceB/InterfaceB_EnvironmentBasedClient.java` — engine API

**New Modules (To Build)**:
- `/yawl/integration/mcp/tools/zai/CaseSubmitTool.java`
- `/yawl/integration/mcp/tools/zai/CaseStatusTool.java`
- `/yawl/integration/mcp/tools/zai/idempotency/IdempotencyStore.java`
- `/yawl/integration/mcp/events/CaseEventPublisher.java`

---

## 💡 Design Highlights

### Idempotency: The Secret Sauce
Z.AI retries are inevitable (network hiccups, timeouts). YAWL's idempotency key → cache pattern ensures:
- Same input → same output (no duplicate cases)
- Clients can safely retry without side effects
- Redis backend scales to 1000s of concurrent agents

### Authentication: Minimal Friction
Reusing existing YAWL JWT infrastructure means:
- No new auth layer to build
- Z.AI gets JWT from environment (no interactive login)
- Permissions enforced at tool level (least privilege)
- Standard HMAC-SHA256 (no exotic crypto)

### Error Handling: Agent-Friendly
Clear distinction between error types:
- **503 Service Unavailable** → "Try again, engine recovering"
- **400 Bad Request** → "Your input is wrong, fix and retry"
- **403 Forbidden** → "You don't have permission, contact admin"
- **404 Not Found** → "Case doesn't exist, check ID"

Z.AI's built-in retry logic automatically handles transient failures.

### Async-First: No Blocking Calls
Case submission returns immediately with case_id. Z.AI can:
- Monitor multiple cases in parallel
- Switch context while waiting for completion
- Not block on long-running workflows
- Retry polling with sensible intervals

### Webhooks: Optional, Not Required
Polling works everywhere. Webhooks add latency benefits:
- Real-time completion notifications (no 5s poll delay)
- Better for time-sensitive use cases
- Requires Z.AI to accept inbound HTTP (may not always possible)
- Falls back to polling if webhook delivery fails

---

## 🎓 Learning Path

**For Decision Makers**:
1. Read `Z.AI-YAWL-INTEGRATION-SUMMARY.md` (2 pages)
2. Review implementation roadmap (above, ~1 page)
3. Approve effort estimate (8.5 days core) + timeline (4 weeks)

**For Architects**:
1. Read `Z.AI-YAWL-INTEGRATION-SUMMARY.md`
2. Study `Z.AI-YAWL-INTEGRATION-DESIGN.md` (sections 1–5: design principles)
3. Review error handling (section 9) + architecture diagram (section 8)
4. Check deployment config (section 11)

**For Engineers (PoC)**:
1. Read `Z.AI-YAWL-INTEGRATION-DESIGN.md` (full)
2. Study `Z.AI-YAWL-INTEGRATION-EXAMPLES.md` (runnable code)
3. Clone YAWL repo, examine existing `/integration/mcp/` and `/integration/a2a/auth/`
4. Start with idempotency store + CaseSubmitTool (minimal viable PoC)

**For Integration Partners**:
1. Read `Z.AI-YAWL-INTEGRATION-SUMMARY.md`
2. Study `Z.AI-YAWL-INTEGRATION-EXAMPLES.md` (agent behavior)
3. Review deployment section (env vars, Docker setup)
4. Implement Z.AI agent against running YAWL MCP server

---

## 📞 Next Steps

### Immediate (Week 1)
- [ ] Review documents (decision checkpoint)
- [ ] Assign PoC engineer
- [ ] Provision dev environment (YAWL + Redis)
- [ ] Set up JWT secrets (shared between Z.AI and YAWL)

### Short-Term (Weeks 2–3)
- [ ] Build core tools (submit + status + workitems)
- [ ] Write integration tests (agent simulation)
- [ ] Demo to stakeholders

### Medium-Term (Weeks 4–6)
- [ ] Add webhooks (optional)
- [ ] Performance testing (concurrent agents)
- [ ] Operational readiness (monitoring, runbooks)

### Long-Term (Post-PoC)
- [ ] Production deployment (HA, Redis backend)
- [ ] Z.AI agent examples (invoice, PO, claims)
- [ ] Operator training + support

---

## 📄 Document Metadata

| Aspect | Value |
|--------|-------|
| **Title** | Z.AI ↔ YAWL Integration Layer Design |
| **Version** | 1.0 (Research Phase 1 Complete) |
| **Date** | 2026-02-21 |
| **Author** | YAWL Integration Specialist |
| **Status** | Ready for PoC Planning |
| **Audience** | Execs, architects, engineers, integrators |
| **Total Pages** | 2 (summary) + ~4K words (design) + 6 examples |
| **Code Examples** | 8 (pseudocode + runnable Python) |
| **Diagrams** | 5 (architecture, sequence, error handling) |
| **PoC Estimate** | 8.5 engineer-days (core) |
| **References** | RFC 7519 (JWT), RFC 9110 (HTTP), MCP 2025-11-25 spec |

---

## 🎯 Bottom Line

**What**: Z.AI agents invoke YAWL workflows via MCP tools.
**How**: Idempotent case submission + async polling (or webhooks).
**Why**: Enterprise automation + autonomous agents = business value.
**When**: Phase 2 PoC starts next week (~8.5 days).
**Cost**: Minimal (reuse auth + MCP infrastructure).

---

**Questions?** See full design doc (`Z.AI-YAWL-INTEGRATION-DESIGN.md` sections 12–13) for FAQs, constraints, and detailed references.

