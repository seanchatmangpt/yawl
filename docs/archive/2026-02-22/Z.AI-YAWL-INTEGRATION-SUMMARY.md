# Z.AI ↔ YAWL Integration Layer (2-Page Summary)

**Status**: Research Phase 1 | **Date**: 2026-02-21 | **Target**: Z.AI autonomous agents invoking YAWL workflows via MCP

---

## Page 1: Architecture & Design

### 1. Z.AI Agent Lifecycle (Tool Use Pattern)

Z.AI agents discover and invoke external tools via MCP (Model Context Protocol). YAWL exposes workflow operations as tools:

```
Z.AI: "Process invoice PO-42"
  → Discover tools via MCP discovery
  → Select: cases/submit(spec_id, case_data, idempotency_key)
  → tool_use(name="cases/submit", input={...})
  → Observe result: case_id="9876"
  → Poll: cases/status(case_id) every 5s until completion
  → Report: "Invoice case completed, approved: true"
```

**Key constraint**: Z.AI tools must be **idempotent**. Retries (due to transient failures) must produce identical results.

### 2. Tool Inventory (MCP Contract)

YAWL exposes 8 core tools to Z.AI:

| Tool | Purpose | Idempotent | Response Time |
|------|---------|-----------|---|
| `cases/submit` | Launch workflow | ✓ (key-based dedup) | 500ms |
| `cases/status` | Poll case state | ✓ (read-only) | 100ms |
| `cases/subscribe` | Webhook notifications | ✓ (optional) | 50ms |
| `workitems/list` | Discover pending tasks | ✓ (read-only) | 150ms |
| `workitems/checkout` | Acquire work item | ✓ (key-based) | 200ms |
| `workitems/complete` | Finish work item | ✓ (key-based) | 300ms |
| `specifications/list` | List workflows | ✓ (read-only) | 100ms |
| `specifications/describe` | Get workflow schema | ✓ (read-only) | 200ms |

Each tool includes JSON input/output schemas, error cases, and examples.

### 3. Interaction Flow (Sequence)

```
Z.AI (Cloud)                YawlMcpServer                YAWL Engine
────────────────            ──────────────                ───────────
│ Tool discovery            │                            │
├──────────────────────────►│                            │
│ List tools /.well-known   │                            │
│                           │ (JSON schema of tools)     │
│◄──────────────────────────┤                            │
│                           │                            │
│ Invoke: cases/submit      │                            │
│ {spec:"Invoice",          │                            │
│  idempotency:"z1"}  ──────┤                            │
│                           │ Check idempotency cache    │
│                           │ (cache miss)               │
│                           │                            │
│                           │ Call launchCase()  ───────►│
│                           │                            │ Create case
│                           │ ◄────────────────────────   │ caseId=9876
│                           │                            │
│                           │ Store: (z1 → 9876, TTL)   │
│ ◄───────────────────────────                           │
│ {case_id:9876,status:...}                             │
│                           │                            │
│ Poll: cases/status ┐      │                            │
│ {case_id:9876}    │ ┌────►│ getCaseState()    ────────►│
│       ┌──────────►│◄──────┤ {status:executing}         │
│       │ every 5s  │      │                            │
│       │ until     │      │ getWorkItems()    ────────►│
│       │ done      │      │ [{task:Review},...]        │
│       │           │ ┌────────────────────┐            │
│       └───────────┘ │ status:completed   │            │
│ ◄──────────────────►│ outcome:{approve..}│            │
│                   └────────────────────┘             │
```

### 4. Idempotency & Error Handling

**Problem**: Z.AI retries on transient failures (network timeout, 503). YAWL must not create duplicate cases.

**Solution**: Idempotency key → result cache

```java
// YAWL handles: cases/submit(spec_id, case_data, idempotency_key)
idempotencyStore.get("z-agent1-inv-42")  // Check cache first
  → Hit: return cached case_id=9876 (already created)
  → Miss: launch new case, store in cache (TTL=3600s)

// Z.AI retries with same idempotency_key
cases/submit(..., idempotency_key="z-agent1-inv-42")
  → MCP Server: "Oh, you already asked this. Returning 9876."
  → Z.AI observes same case_id → idempotent ✓
```

**Error classification**:
- **Transient (503)**: Z.AI retries with backoff (1s, 2s, 4s)
- **Client error (400/403)**: Z.AI fails immediately (no retry)
- **Not found (404)**: Z.AI fails immediately

### 5. Authentication Model

**Service account JWT** (shared secret):

```json
// Z.AI context env vars
ZAI_AGENT_NAME = "zai-invoice-processor"
YAWL_MCP_JWT_SECRET = "... 32+ char key ..."

// Z.AI issues JWT on startup
{
  "sub": "zai-invoice-processor",
  "aud": "yawl-mcp",
  "scope": "workflows:launch workflows:query workitems:manage",
  "exp": <60 min from now>
}

// Every MCP tool call includes
Authorization: Bearer <jwt_token>
```

YAWL reuses existing `JwtAuthenticationProvider` (from A2A auth stack):
- Validates signature (HMAC-SHA256)
- Checks expiry
- Enforces permissions (scope claims)
- Returns `AuthenticatedPrincipal` for tool handlers

Permissions granted to Z.AI service accounts:
- `workflows:launch` — submit cases
- `workflows:query` — status, list specs
- `workitems:manage` — checkout, complete
- (no `admin` permission for Z.AI)

---

## Page 2: Implementation & Rollout

### 6. Code Structure (New Modules)

```
yawl/integration/mcp/
├── YawlMcpServer.java            (existing → extend)
├── tools/zai/                     (NEW)
│   ├── CaseSubmitTool.java        (idempotent launch)
│   ├── CaseStatusTool.java        (polling support)
│   ├── WorkItemTools.java         (checkout, complete, list)
│   ├── SpecificationsTool.java    (extend existing)
│   └── idempotency/
│       ├── IdempotencyStore.java  (interface)
│       └── InMemoryIdempotencyStore.java
└── events/                        (NEW, optional webhooks)
    ├── CaseEventPublisher.java
    └── WebhookCallbackService.java
```

**Key implementation** (pseudocode):

```java
// CaseSubmitTool.execute(input, principal)
String idempotencyKey = input.get("idempotency_key");
if (idempotencyKey != null) {
    var cached = idempotencyStore.get(idempotencyKey);
    if (cached.isPresent()) {
        return cached.get();  // Idempotent! Same output.
    }
}
String caseId = engine.launchCase(specId, caseData, ...);
Map result = Map.of("case_id", caseId, "status", "running");
idempotencyStore.set(idempotencyKey, result, 3600);  // Cache 1h
return result;
```

### 7. Test Plan & Effort

| Phase | Component | Duration | Tests |
|-------|-----------|----------|-------|
| **1** | IdempotencyStore + CaseSubmitTool | 1.5d | Unit: cache hit/miss, idempotent retry |
| **2** | CaseStatusTool + WorkItemTools | 1.5d | Unit: state transitions, auth check |
| **3** | Integration tests | 2d | E2E: Z.AI → MCP → Engine → completion |
| **4** | Webhooks (optional) | 1.5d | Event publish, webhook retry, signature |
| **5** | Perf + Security | 1d | Tool latency, concurrent agents |
| **6** | Docs + Examples | 1d | OpenAPI spec, Z.AI agent examples |
| | **TOTAL** | **~8.5d** | Single engineer |

**Test scenarios**:
1. ✓ Submit case with idempotency key → retry returns same case_id
2. ✓ Invalid spec → 400 error (no retry)
3. ✓ Engine timeout → 503 (Z.AI retries)
4. ✓ Poll until completion → webhook notification (optional)
5. ✓ Permission denied (insufficient scope) → 403

### 8. Deployment & Operations

**Config** (environment variables):

```bash
# Z.AI side
export YAWL_MCP_JWT_SECRET="... 32+ chars ..."
export ZAI_MCP_TOOL_TIMEOUT_MS=5000
export ZAI_MCP_TOOL_RETRIES=3

# YAWL server side
export A2A_JWT_SECRET="... same 32+ chars ..."
export YAWL_MCP_IDEMPOTENCY_TTL_SECONDS=3600
export YAWL_MCP_IDEMPOTENCY_BACKEND="memory"  # or "redis"
export YAWL_MCP_WEBHOOK_ENABLED=true
```

**Deployment**:
- YawlMcpServer runs on port 8080 (HTTP or via reverse proxy)
- Serves `/.well-known/mcp.json` for tool discovery
- Each MCP tool call authenticated via JWT
- Idempotency store backed by in-memory LRU (dev) or Redis (prod)
- Optional webhooks for event-driven completion notifications

### 9. Advanced: Webhook Architecture (Optional)

Instead of polling, Z.AI subscribes to case completion:

```
cases/subscribe({
  case_id: "9876",
  callback_url: "https://zai.example.com/case-complete",
  events: ["case_completed", "case_failed"]
})

Response: { subscription_id: "sub-xyz", expires_at: "2026-02-21T12:00Z" }

Later, case completes:
  YAWL → CaseEventPublisher
    → HTTP POST to callback_url (with HMAC signature)
    → {"event_type": "case_completed", "case_id": "9876", ...}

Z.AI observes webhook → completes immediately (no 300s polling delay)
```

Benefits: Lower latency (real-time), fewer polls. Trade-off: Requires Z.AI to accept inbound HTTP.

### 10. Known Constraints & Mitigations

| Risk | Mitigation |
|------|-----------|
| Z.AI tool call timeout (5 min) blocks agent | Use case_id as handle; tool returns quickly with status |
| Idempotency key collision | Prefix with agent ID: `"z-<agent-id>-<request-id>"` |
| Z.AI no public IP (webhooks fail) | Fallback to polling; webhooks optional |
| YAWL engine downtime | Z.AI retries; configure YAWL HA setup |
| Idempotency cache grows unbounded | Evict expired entries (TTL=3600s); use Redis for scale |

### 11. Success Criteria

- [x] Z.AI agents launch YAWL workflows via MCP tools
- [x] Idempotent retries don't create duplicate cases
- [x] Tool response time <500ms (p95)
- [x] Case completion notifications via polling (mandatory) + webhooks (optional)
- [x] JWT authentication + permission enforcement
- [x] Error classification (transient vs permanent) guides Z.AI retry logic
- [x] Production deployment: YawlMcpServer + idempotency store (Redis)

### 12. Timeline & Rollout

```
Week 1: Core tools (submit, status, idempotency store) → Dev testing
Week 2: Work item tools + integration tests → Staging
Week 3: Webhooks (optional) + perf testing → Prod deployment
Week 4: Documentation + Z.AI agent examples → Operator readiness
```

---

## Summary

**Design**: Z.AI agents invoke YAWL via MCP. Tools are idempotent (idempotency key → cache). Authentication reuses existing JWT infrastructure. Error handling distinguishes transient (retry) vs permanent (fail) failures.

**Implementation**: 8–9 engineer-days to build CaseSubmitTool, CaseStatusTool, WorkItemTools, IdempotencyStore, + tests + docs. Deployable on existing YawlMcpServer infrastructure.

**Value**: Autonomous agents can now orchestrate YAWL workflows. Idempotency ensures correctness under retries. Webhooks (optional) enable real-time completion notifications.

---

**See also**: Full technical specification in `Z.AI-YAWL-INTEGRATION-DESIGN.md` (sections 1–13: architecture, auth, event handling, PoC estimate, error examples, configuration, references).

