# ADR-014: Clustering and Horizontal Scaling Architecture

## Status
**ACCEPTED**

## Context

YAWL v5.x runs as a single stateful instance. The `YEngine.getInstance()` singleton
pattern and the assumption of a single process owning all in-memory state mean:

1. **No horizontal scaling**: only one JVM may own the engine state at a time
2. **Single point of failure**: engine restart interrupts all running cases
3. **Vertical scaling ceiling**: memory and CPU are bounded by a single node
4. **Maintenance windows required**: no rolling restart is possible

For enterprise deployment targets (see ADR-009: Multi-Cloud Strategy), the following
scaling requirements have been identified:

| Requirement | Current Capability | v6.0 Target |
|-------------|-------------------|-------------|
| Concurrent cases | ~1,000 (single node) | 50,000+ (cluster) |
| Work item throughput | ~200/sec | 5,000+/sec |
| Engine availability | 99.5% (restart ~4h/year) | 99.95% (rolling restart) |
| Recovery point objective | Last DB checkpoint | Zero (all ops are durable) |

YAWL already externalises persistent state to a relational database (Hibernate/PostgreSQL).
This is the key insight enabling clustering: the database is the shared state store.

## Decision

**YAWL v6.0.0 adopts a shared-database clustering model with active-active engine nodes
and a distributed lease mechanism for case ownership.**

### Architecture

```
                    ┌─────────────────────────────┐
                    │     Load Balancer / Ingress  │
                    │    (session-affinity: none)  │
                    └──────┬──────────┬────────────┘
                           │          │
              ┌────────────▼──┐  ┌────▼────────────┐
              │  Engine Node 1 │  │  Engine Node 2   │
              │  YEngine (v6) │  │  YEngine (v6)    │
              │  Port 8080    │  │  Port 8080        │
              └───────┬───────┘  └────────┬──────────┘
                      │                   │
              ┌───────▼───────────────────▼──────────┐
              │     PostgreSQL Primary (shared state) │
              │   + Read Replica (query offloading)   │
              └──────────────────────────────────────┘
                      │
              ┌───────▼──────────────────────────────┐
              │     Redis Cluster (lease registry,    │
              │     session cache, work item locks)   │
              └──────────────────────────────────────┘
```

### Case Ownership and Lease Protocol

Each running case is owned by exactly one engine node at a time via a distributed lease.

**Lease acquisition:**
```
1. Engine node A attempts SET case:{caseId}:owner nodeA EX 30 NX  (Redis SETNX)
2. If SET succeeds → node A owns the case for 30 seconds
3. Node A must renew the lease every 15 seconds (heartbeat)
4. If renewal fails → node A relinquishes ownership
5. Another node acquires the lease and resumes the case from DB state
```

**Work item assignment:**
- Work item checkout requires the node to hold the case lease
- OR the node acquires a work-item-level lock (shorter TTL, 10 seconds)
- Prevents dual checkout across nodes

**Session handles:**
- Session handles are JWT tokens (HMAC-SHA256, see ADR-017: Auth)
- Any engine node can validate a session handle independently (no sticky sessions required)
- Session metadata (user, expiry) encoded in the JWT payload

### State Synchronisation

The database remains the canonical state store. Nodes never cache case state in heap
beyond the active processing window. At the end of each work item execution:

1. All case state changes are written to the database in a single transaction
2. The lease heartbeat confirms the transaction committed successfully
3. Other nodes reading the case will see current state from DB

### Stateless Engine Nodes

When using `YStatelessEngine` (see ADR-001), no lease protocol is needed. Each stateless
invocation is fully independent and can be routed to any node.

### Split-Brain Prevention

If Redis becomes unavailable:
- New case launches are rejected with `503 Service Unavailable`
- In-flight cases continue processing on their current node until they reach a safe
  persistence point, then halt
- No two nodes can simultaneously own the same case because the Redis write is required
  before any state-mutating operation

### Health Check and Node Discovery

Kubernetes-native:
```yaml
readinessProbe:
  httpGet:
    path: /admin/health
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

livenessProbe:
  httpGet:
    path: /admin/health
    port: 8080
  failureThreshold: 3
  periodSeconds: 10
```

Nodes register in a `yawl_cluster_nodes` database table at startup and deregister
on clean shutdown. This allows the admin API to enumerate cluster topology.

## Consequences

### Positive

1. True horizontal scaling without code-level changes to the execution core
2. Rolling restarts: remove one node, deploy, re-add — zero downtime
3. Lease protocol provides automatic failover for crashed nodes (max 30s delay)
4. Stateless engine nodes scale independently of stateful nodes

### Negative

1. Redis becomes an infrastructure dependency (was previously optional)
2. Lease protocol adds ~2ms latency to case-mutating operations (Redis round-trip)
3. Distributed debugging is harder: a case's log entries may span multiple nodes
4. Split-brain protection is conservative: Redis unavailability blocks new launches

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Redis cluster failure | LOW | HIGH | Redis Sentinel with 3 replicas; circuit breaker (ADR-008) falls back to degraded single-node mode |
| Lease expiry during slow DB write | LOW | MEDIUM | Lease TTL is 3x the P99 DB write latency |
| Network partition between nodes and DB | LOW | HIGH | PostgreSQL sync replication; primary promotion automated via Patroni |
| Clock skew affecting lease TTLs | LOW | LOW | Use Redis server time (not client clock) for all TTL calculations |

## Alternatives Considered

### Hazelcast In-Memory Data Grid
Rejected. Hazelcast provides distributed state but requires complex topology management.
The YAWL engine already externalises persistent state to the database; using Hazelcast
as a second state layer would create synchronisation complexity without clear benefit.

### Database-Level Locking (SELECT FOR UPDATE)
Rejected as primary mechanism. Database-level row locks for case ownership work but
degrade performance under high concurrency. Redis SETNX provides sub-millisecond
lease acquisition at 10,000+ ops/sec versus database lock contention at 500+ concurrent cases.

### Actor Model (Akka/Pekko)
Rejected for now. An actor-per-case model would be architecturally elegant and provide
natural failover via actor migration. However, it requires significant engine refactoring
(ADR-001's shared-core model would need redesign) and is deferred to v7.0.

### Saga/Event Sourcing Architecture
Considered. Full event sourcing would eliminate the need for distributed locks entirely.
Deferred to a future ADR — the migration cost from the current Hibernate ORM model is
too high for v6.0 scope.

## Related ADRs

- ADR-001: Dual Engine Architecture (stateless nodes need no lease protocol)
- ADR-002: Singleton vs Instance YEngine (singleton deprecated by this ADR)
- ADR-007: Repository Pattern for Caching (cache invalidation in clustered mode)
- ADR-008: Resilience4j Circuit Breaking (Redis unavailability handling)
- ADR-009: Multi-Cloud Strategy (cluster deployment across availability zones)
- ADR-010: Virtual Threads (lease renewal heartbeats use virtual thread executor)

## Implementation Notes

### Lease Manager Interface

```java
// org.yawlfoundation.yawl.engine.cluster.CaseLease
public interface CaseLease {
    boolean acquire(String caseId, String nodeId, Duration ttl);
    boolean renew(String caseId, String nodeId, Duration ttl);
    void release(String caseId, String nodeId);
    Optional<String> currentOwner(String caseId);
}

// Redis implementation
// org.yawlfoundation.yawl.engine.cluster.RedisCaseLease
```

### Engine Node Configuration

```properties
# application.properties (v6)
yawl.cluster.enabled=true
yawl.cluster.node-id=${HOSTNAME}
yawl.cluster.lease-ttl=PT30S
yawl.cluster.lease-renewal-interval=PT15S
yawl.cluster.redis.url=${REDIS_URL}
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** PLANNED (v6.1.0 — clustering is post-v6.0.0 GA)
**Review Date:** 2026-05-17

---

**Revision History:**
- 2026-02-17: Initial version
