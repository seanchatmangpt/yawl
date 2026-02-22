# A2A Protocol Documentation Index

**Scope**: YAWL v6.0.0 Agent-to-Agent (A2A) Integration
**Date**: 2026-02-21
**Status**: Complete (2-page research + comprehensive diagrams)

---

## Documents Overview

### 1. A2A_PROTOCOL_RESEARCH.md (31 KB, 1015 lines)

**Main specification document** covering all aspects of the A2A protocol as implemented in YAWL v6.

Sections:
1. **A2A Protocol Specification** — Transport (HTTP REST), message format (JSON), HTTP endpoints
2. **Core Message Types** — launch_case, subscribe_events, query_case, complete_task, get_resource_available
3. **Message Routing** — Agent registry pattern (DNS SRV), agent-to-agent discovery, routing within YAWL
4. **Security Model** — JWT (HS256), mTLS SPIFFE, API Key HMAC, permission model (5 levels)
5. **Reliability Guarantees** — Exactly-once delivery via idempotency keys, exponential backoff (3 retries), message ordering (FIFO per-client)
6. **Proof of Concept** — Happy path (multi-agent procurement), error cases (idempotent retry, auth failure)
7. **A2A vs MCP vs gRPC vs REST** — Comparison table (peer-to-peer, auth, latency, scalability)
8. **State Machine** — A2A message lifecycle (idle → authenticate → authorize → dispatch → execute → cache)
9. **Handoff Protocol** — Atomic work item transfer via JWT (60s TTL), 7-step choreography
10. **Conclusion & Future Work** — What works today, gaps for v6.1, recommended multi-org architecture

**Key Findings**:
- A2A is **peer-to-peer** (unlike MCP which is client-server)
- Enables **cross-org workflow coordination** without LLM-specific coupling
- Handoff via **JWT tokens** (non-repudiation, auditable)
- Idempotent via **message ID + timestamp** caching (24h TTL)
- 3 auth schemes: JWT, mTLS SPIFFE, API Key HMAC
- Virtual thread support: 1000 agents = ~1MB memory

---

### 2. A2A_PROTOCOL_DIAGRAMS.md (29 KB, 672 lines)

**Visual reference guide** with ASCII diagrams and flow charts for implementation.

Sections:
1. **Error Handling Decision Tree** — HTTP status code handling (2xx/3xx/4xx/5xx), retry logic, caching
2. **Authentication & Permission Check Flow** — Parse → Validate → Check expiry → Validate issuer → Check permissions
3. **Idempotency & Caching Architecture** — Request flow with cache hits/misses, 24h TTL, optimization example
4. **Handoff Protocol State Machine** — 7-step detailed state machine (generate token → send message → verify → checkout → confirm)
5. **Retry Backoff Algorithm** — Exponential backoff with jitter (100ms → 200ms → 400ms, ±25% random)
6. **Multi-Agent Choreography** — Procurement workflow timeline (4.2 seconds, 4 agents, 12 events)
7. **Performance Characteristics** — Latency breakdown (p50=70ms, p99=150ms), throughput (5600 req/sec), memory (120MB heap)

**Key Visuals**:
- Decision trees for error handling and auth
- State machines for message lifecycle and handoff
- Timeline diagrams for multi-agent workflows
- Performance metrics and memory footprint

---

## Quick Reference

### Message Types (6 total)

| Skill | Purpose | Auth | Response |
|-------|---------|------|----------|
| `launch_workflow` | Start new case | PERM_WORKFLOW_LAUNCH | case_id |
| `query_case` | Get state snapshot | PERM_WORKFLOW_QUERY | pending_tasks, completed_tasks |
| `complete_task` | Return control flow | PERM_WORKITEM_MANAGE | next_tasks |
| `cancel_case` | Stop running case | PERM_WORKFLOW_CANCEL | status |
| `subscribe_events` | Stream state changes | PERM_WORKFLOW_QUERY | chunked HTTP events |
| `check_resource_availability` | Query agent capacity | PERM_WORKFLOW_QUERY | utilization % |

### Authentication Schemes (3 total)

| Scheme | Header Format | Validation | TTL | Use Case |
|--------|---------------|------------|-----|----------|
| **JWT (HS256)** | `Authorization: Bearer <JWT>` | HMAC signature + expiry | 1h | Internal org, LLM agents |
| **mTLS SPIFFE** | X.509 cert in TLS handshake | SPIFFE subject + chain | cert notAfter | Kubernetes clusters, service mesh |
| **API Key** | `Authorization: ApiKey <id>:<sig>` | HMAC-SHA256(body) | rotating | External integrations, webhooks |

### Error Codes & Retry Strategy

| Code | Meaning | Retry? | Max Attempts |
|------|---------|--------|--------------|
| 2xx | Success | No | 1 |
| 3xx | Redirect | Yes | 5 |
| 400 | Bad Request | No | 1 (client error) |
| 401 | Unauthorized | No | 1 (auth error) |
| 403 | Forbidden | No | 1 (permission error) |
| 404 | Not Found | Maybe | 1 (after 5s) |
| 409 | Conflict | No | Return cached response |
| 500 | Internal Error | Yes | 3 (exponential backoff) |
| 503 | Service Unavailable | Yes | 3 (exponential backoff) |
| 504 | Gateway Timeout | Yes | 1 (then return 504) |

### Key Design Patterns

1. **Idempotency**: Message ID + timestamp → 24h response cache
2. **Retry**: 3 attempts with exponential backoff (100ms → 200ms → 400ms)
3. **Handoff**: JWT token signed by YAWL → atomic work item transfer
4. **Permission Model**: Fine-grained (5 permission levels), checked on every request
5. **Message Ordering**: FIFO per-client (determined by JWT subject claim)
6. **Streaming**: Chunked HTTP for event subscriptions (optional)
7. **Virtual Threads**: All I/O-bound operations on virtual threads (1000 agents = ~1MB)

---

## Implementation Checklist

**For A2A Clients (external agents)**:

- [ ] Implement agent card discovery (GET /.well-known/agent.json)
- [ ] Parse agent card (skills, auth schemes)
- [ ] Select authentication scheme (JWT, mTLS, or API Key)
- [ ] Prepare credentials (JWT token, client cert, or API key + HMAC)
- [ ] Send A2A message with Idempotency-Key header
- [ ] Implement retry logic (3 attempts, exponential backoff)
- [ ] Cache responses for idempotency verification
- [ ] Handle streaming for subscribe_events (chunked HTTP)
- [ ] Implement message correlation via correlation_id in context

**For YAWL A2A Server Operations**:

- [ ] Enable authentication (set A2A_JWT_SECRET or A2A_API_KEY_MASTER)
- [ ] Configure virtual thread executor (Executors.newVirtualThreadPerTaskExecutor)
- [ ] Set up response cache (24h TTL, Redis or in-process)
- [ ] Monitor metrics (request latency, auth failures, handoff success rate)
- [ ] Enable OpenTelemetry tracing (for observability)
- [ ] Test handoff protocol (JWT token generation, expiry, revocation)
- [ ] Set up DNS SRV records for agent discovery (if using service mesh)
- [ ] Configure SPIFFE trust domain (if using mTLS)

---

## Performance Expectations

**Latency (single A2A call)**:
- p50: 70ms
- p99: 150ms
- max: 500ms (timeout)

**Throughput**:
- Simple queries (query_case): ~14,000 req/sec per core → 112,000 req/sec (8 cores)
- Complex operations (launch_workflow): ~700 req/sec per core → 5,600 req/sec (8 cores)

**Memory Footprint**:
- 1000 agents idle: ~120MB heap + 1MB virtual threads = 121MB total
- Comparison: Platform threads would require ~2GB (16× more)

**Handoff Latency**: ~100-200ms (JWT generation + network + checkout)

---

## Testing Recommendations

### Unit Tests
- [ ] JWT signature validation (valid, expired, wrong secret)
- [ ] mTLS SPIFFE subject extraction (valid cert chain, expired cert)
- [ ] API Key HMAC verification (valid, tampered body)
- [ ] Idempotency key deduplication (cache hit, TTL expiry)
- [ ] Retry backoff calculation (exponential growth, jitter range)
- [ ] Permission checking (all 5 levels)
- [ ] Message format validation (invalid JSON, missing fields)

### Integration Tests
- [ ] End-to-end launch case → query → complete (Happy path)
- [ ] Idempotent retry of launch (should return same case_id)
- [ ] Handoff from Agent A to Agent B (JWT validation, checkout)
- [ ] Error handling (5xx retry, 4xx no-retry)
- [ ] Concurrent requests from multiple agents (1000 parallel)
- [ ] Event streaming (subscribe_events with chunked HTTP)
- [ ] Permission denial (403 Forbidden)
- [ ] Auth failure (401 Unauthorized)

### Load Tests
- [ ] 1000 concurrent agents with idle connections
- [ ] Burst of 5000 req/sec (verify queue behavior)
- [ ] Long-running case (30+ min) with polling
- [ ] Stress handoff protocol (100 handoffs/sec)

---

## References

**Source Code**:
- `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java` — Main server (entry point)
- `src/org/yawlfoundation/yawl/integration/a2a/VirtualThreadYawlA2AServer.java` — Virtual thread variant
- `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AClient.java` — Client for invoking remote agents
- `src/org/yawlfoundation/yawl/integration/a2a/handoff/HandoffProtocol.java` — JWT handoff implementation
- `src/org/yawlfoundation/yawl/integration/a2a/auth/` — Auth providers (JWT, mTLS, API Key)

**Rules**:
- `.claude/rules/integration/mcp-a2a-conventions.md` — YAWL integration conventions

**Tests**:
- `test/org/yawlfoundation/yawl/integration/a2a/A2AProtocolTest.java` — Protocol-level tests
- `test/org/yawlfoundation/yawl/integration/a2a/HandoffIntegrationTest.java` — Handoff tests
- `test/org/yawlfoundation/yawl/integration/a2a/VirtualThreadYawlA2AServerTest.java` — Virtual thread tests

**Related Documentation**:
- YAWL v6.0.0 release notes (https://yawlfoundation.github.io)
- A2A specification (https://www.a2a.org/spec)
- Java 25 Virtual Threads (JEP 444)
- SPIFFE standard (https://spiffe.io)

---

## How to Use These Documents

1. **For architects/designers**: Start with **A2A_PROTOCOL_RESEARCH.md** sections 1-4 (protocol spec, message types, routing, security)
2. **For implementers**: Use **A2A_PROTOCOL_DIAGRAMS.md** for state machines and error handling
3. **For troubleshooting**: Refer to "Error Handling Decision Tree" in diagrams for specific HTTP status codes
4. **For performance tuning**: See "Performance Characteristics" section for latency breakdown
5. **For testing**: Use "Testing Recommendations" checklist above

---

**Document prepared by**: YAWL Foundation Integration Specialist
**Review status**: Ready for production use
**Next review**: 2026-06-21 (or after A2A v1.1 release)
