# YAWL v6 Architecture Trade-Off Guide

**Status**: Production Ready | **Last Updated**: February 2026 | **Comprehensive Analysis**

Detailed analysis of architectural decisions in YAWL v6, with pros/cons of each choice and guidance on when to use each option.

---

## 1. Persistent vs Stateless: Complete Analysis

### Decision Matrix

| Aspect | Persistent (YEngine) | Stateless (YStatelessEngine) | Hybrid |
|--------|-------------------|---------------------------|-------|
| **Persistence Model** | Database | Event sourcing | Both |
| **Throughput** | 100-300/sec | 1K-5K/sec | 500-2K/sec |
| **Latency P99** | 50-200ms | 10-100ms | 30-150ms |
| **Memory/Case** | 1-2 MB | 100-500 KB | 500 KB-1 MB |
| **Scalability** | Vertical | Horizontal | Hybrid |
| **Case History** | Complete | Events | Complete (slow) |
| **Cost/K cases** | $50-100 | $10-20 | $30-50 |
| **Setup Complexity** | Low | High | Very High |
| **Ops Complexity** | Medium | High | Very High |

---

## 2. Persistent Engine (YEngine)

### 2.1 Architecture Overview

```
┌──────────────────────────────────────┐
│     REST API / WebSocket / MCP        │
└────────────────┬─────────────────────┘
                 │
         ┌───────▼────────┐
         │    YEngine     │
         │  (Persistent)  │
         ├─────────────────┤
         │ Case State      │
         │ Execution Stack │
         │ Resource Info   │
         └───────┬────────┘
                 │ (JDBC/JPA)
         ┌───────▼──────────────┐
         │  Database (Full)      │
         │ ├─ Cases              │
         │ ├─ Tasks              │
         │ ├─ Work Items         │
         │ ├─ History            │
         │ ├─ Resources          │
         │ └─ Audit Trail        │
         └───────────────────────┘
```

### 2.2 When to Choose Persistent

**Use Persistent if**:

✓ You need complete audit trail (compliance, legal)
✓ Cases run for hours/days/weeks (long-lived state)
✓ You have <100K total cases
✓ Throughput requirement <500 cases/sec
✓ You want integrated resource management
✓ You use worklet service (subprocess delegation)
✓ You need human task coordination
✓ Crash recovery is critical
✓ Team familiar with relational databases
✓ Budget <$50K for infrastructure

**Example Workloads**:
- Insurance claims processing (audit required, long duration)
- Loan approval workflow (compliance, human review)
- Government permits (audit trail mandatory)
- HR onboarding (human tasks, long timeline)
- Order management with inventory (integrated resources)

### 2.3 Persistent Advantages

```
1. Audit & Compliance
   ✓ Every action logged in database
   ✓ Immutable history (legal requirement for some industries)
   ✓ Exact reconstruction of past states
   ✓ Regulatory compliance (GDPR, HIPAA, SOX)

2. Long-Running Workflows
   ✓ Cases persist indefinitely (weeks/months/years)
   ✓ Server crash recovery (resume from last checkpoint)
   ✓ Workflow versioning (cases track version executed)
   ✓ Deadline management (timers survive restarts)

3. Integrated Services
   ✓ Built-in resource allocation
   ✓ Worklet service (process delegation)
   ✓ Human task management
   ✓ Scheduling and priority management
   ✓ Status monitoring dashboard

4. Operational Familiarity
   ✓ DBA can tune/backup/restore
   ✓ SQL-based monitoring
   ✓ Standard HA options (replication, failover)
   ✓ Proven in production (15+ years)

5. Troubleshooting
   ✓ Can examine case state at any point
   ✓ Can manually intervene (SQL updates)
   ✓ Can replay historical data
   ✓ Historical analytics easy
```

### 2.4 Persistent Disadvantages

```
1. Scalability Ceiling
   ✗ Single instance bottleneck at ~300 cases/sec
   ✗ Database becomes constraint (not linearly scalable)
   ✗ Cluster deployment complex, still DB-limited
   ✗ Practical limit: ~1K cases/sec (with clustering)

2. Infrastructure Costs
   ✗ Large database (1-2 GB per 1K cases)
   ✗ Database redundancy (replication, failover)
   ✗ Regular backups (full + incremental)
   ✗ Larger VM instances to cache data
   ✗ Cost: $50-100K/year for 100K cases

3. Memory Footprint
   ✗ Loads entire case into memory
   ✗ Execution stack + data + metadata
   ✗ Case cache bloats quickly
   ✗ Difficult to support >100K cases in cluster

4. Operational Burden
   ✗ Database tuning required
   ✗ Connection pool management
   ✗ Index strategy (complex for YAWL schema)
   ✗ Lock contention on hot cases
   ✗ Deadlock potential (complex queries)

5. Deployment Constraints
   ✗ Tightly coupled to database
   ✗ Can't easily multi-tenant (data isolation)
   ✗ Cloud-native challenges (stateful)
   ✗ Container orchestration complex
   ✗ Not suitable for serverless (stateful)
```

### 2.5 Persistent Configuration

```yaml
# application.yml
spring:
  application:
    name: yawl-persistent
  datasource:
    url: jdbc:postgresql://db:5432/yawl
    username: yawl_user
    password: secret
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.batch_size: 50
      hibernate.jdbc.fetch_size: 50
  jackson:
    serialization:
      write-dates-as-timestamps: false

engine:
  mode: persistent
  case:
    persistence:
      full: true
      cache:
        enabled: true
        max-size: 10000
  resource:
    service:
      enabled: true
      cache-size: 500
  worklet:
    service:
      enabled: true
  scheduling:
    timer-threads: 4
    max-concurrent-timers: 1000

jvm:
  args: >-
    -XX:+UseZGC
    -XX:+UseCompactObjectHeaders
    -Xmx8g -Xms8g
```

---

## 3. Stateless Engine (YStatelessEngine)

### 3.1 Architecture Overview

```
┌────────────────────────────────────────────────┐
│  Load Balancer (Round-robin across instances)  │
└─────────┬────────────┬────────────┬────────────┘
          │            │            │
    ┌─────▼──┐    ┌────▼──┐    ┌───▼───┐
    │Instance │    │Instance│    │Instance│
    │   1     │    │   2    │    │   3    │
    └─────┬──┘    └────┬──┘    └───┬───┘
          │            │            │
    ┌─────▼────────────▼────────────▼────┐
    │    Message Queue / Event Stream     │
    │    (Kafka / AWS Kinesis)            │
    └─────────────┬──────────────────────┘
                  │
          ┌───────▼────────┐
          │  Event Store   │
          │  (Immutable)   │
          └────────────────┘

Per-Instance Memory:
  - Active cases: 5K (in memory only)
  - Event cache: 10MB
  - Session state: minimal
  Total: ~2GB per instance
```

### 3.2 When to Choose Stateless

**Use Stateless if**:

✓ You need high throughput (1K+ cases/sec)
✓ Cases are short-lived (<1 hour)
✓ Horizontal scaling is priority
✓ Cloud deployment desired (Kubernetes)
✓ Cost-sensitive (pay per execution)
✓ You can tolerate eventual consistency
✓ Global distribution needed (multi-region)
✓ Rapid auto-scaling required
✓ Event-driven architecture preferred
✓ Budget allows $500/instance (stateless scales better)

**Example Workloads**:
- IoT event processing (1M events/sec, short workflows)
- Mobile app workflows (variable load, auto-scaling)
- Microservice orchestration (distributed, stateless)
- Real-time fraud detection (high throughput, short TTL)
- Batch processing (periodic, large volume)
- Cloud functions / serverless (trigger-based)

### 3.3 Stateless Advantages

```
1. Unlimited Horizontal Scaling
   ✓ Add more instances = more throughput (linear)
   ✓ No database bottleneck (sharded event store)
   ✓ Can handle 1M+ cases with enough instances
   ✓ Throughput: 1K-5K cases/sec per instance

2. Low Memory Footprint
   ✓ Only active cases in memory (~2GB per instance)
   ✓ Completed cases dropped (events stored externally)
   ✓ Cache misses = replay from event log
   ✓ Support 10-100x more cases per GB

3. Cloud-Native Design
   ✓ No local state (pods are interchangeable)
   ✓ Kubernetes-friendly (no session affinity needed)
   ✓ Auto-scaling trivial (add/remove pods)
   ✓ Surviving pod crash simple (replay events)
   ✓ Multi-region deployment easy

4. Cost Efficiency
   ✓ Pay only for what you use
   ✓ Smaller instances (2-4GB vs 8-16GB)
   ✓ Serverless option possible
   ✓ Event store cheaper than DB replicas
   ✓ 80% cost reduction for variable workload

5. Event Sourcing Benefits
   ✓ Complete event history (more granular than DB audit)
   ✓ Temporal query (what was state at time T?)
   ✓ Event replay (reprocess workflow)
   ✓ Easy audit trail
   ✓ Debugging via event timeline
```

### 3.4 Stateless Disadvantages

```
1. Complexity
   ✗ Event-driven architecture (steep learning curve)
   ✗ Eventual consistency (harder to reason about)
   ✗ Distributed tracing essential (not optional)
   ✗ More moving parts (instances, queue, store)
   ✗ Debugging more difficult (non-local state)

2. Features Not Yet Available
   ✗ Worklet service (coming in v6.1)
   ✗ Resource allocation (partial support)
   ✗ Long-term case history (events archived)
   ✗ Some legacy integrations (need adapter)

3. Limited Case Duration
   ✗ In-memory state lost on crash (if not persisted)
   ✗ "Default" 24-hour case window (configurable)
   ✗ Long-running cases (>24h) need special handling
   ✗ Periodic checkpointing overhead

4. Event Store Complexity
   ✗ Must choose event store (Kafka, S3, DynamoDB)
   ✗ Event schema versioning required
   ✗ Event replay semantics (idempotency)
   ✗ Storage costs can add up
   ✗ Debugging event store failures

5. Operations Learning Curve
   ✗ Monitoring more complex (distributed)
   ✗ Troubleshooting requires event log analysis
   ✗ Team must learn event-driven patterns
   ✗ Can't simply query "case state" (no DB)
   ✗ Distributed consensus for lifecycle
```

### 3.5 Stateless Configuration

```yaml
# application-stateless.yml
spring:
  application:
    name: yawl-stateless
  kafka:
    bootstrap-servers: kafka:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  cache:
    type: redis
    redis:
      host: redis
      port: 6379
      time-to-live: 3600000  # 1 hour

engine:
  mode: stateless
  event-sourcing:
    enabled: true
    backend: kafka                    # or postgres-json, s3, dynamodb
    topic: yawl.case.events
    partition-strategy: case-id       # Route same case to same partition
    event-retention: 2592000          # 30 days
  case:
    persistence: none                 # No database persistence
    memory-only:
      enabled: true
      cache-ttl-minutes: 60
      cache-size: 5000
  executor:
    type: virtual-thread-pool
    parallelism: auto
    queue-size: 10000

jvm:
  args: >-
    -XX:+UseZGC
    -XX:+UseCompactObjectHeaders
    -Xmx4g -Xms2g
```

---

## 4. Hybrid Architecture (Persistent + Stateless)

### 4.1 When to Use Hybrid

**Use Hybrid if**:

✓ Some workflows need full persistence (compliance)
✓ Some workflows are high-volume (optimization)
✓ Gradual migration from persistent to stateless
✓ Team wants to evaluate stateless before full commit
✓ Mixed workload (audit + real-time)

**Architecture**:
```
┌─────────────────────────────────────────────┐
│  Router (directs by workflow type)          │
├────────────────┬──────────────────────────┤
│                                            │
│  Persistent Engine                Stateless Engine
│  (Audit-required workflows)       (High-throughput)
│  - Loan approval                  - IoT processing
│  - Insurance claims               - Real-time fraud
│  - Compliance workflows           - Batch processing
│                                   │
│  Database (Full persistence)      Event Store (Events)
```

### 4.2 Hybrid Configuration

```yaml
spring:
  profiles:
    active: hybrid
  kafka:
    bootstrap-servers: kafka:9092
  datasource:
    url: jdbc:postgresql://db:5432/yawl
    username: yawl_user
    password: secret

engine:
  routing:
    enabled: true
    rules:
      - pattern: "loan.*"
        engine: persistent          # Use persistent
      - pattern: "iot.*"
        engine: stateless           # Use stateless
      - default: persistent         # Default to persistent

  # Persistent config
  persistent:
    enabled: true
    cache-size: 10000

  # Stateless config
  stateless:
    enabled: true
    event-store: kafka
    cache-size: 5000
```

---

## 5. Single Instance vs Clustered

### 5.1 Decision Matrix

| Aspect | Single Instance | Clustered | Cloud |
|--------|---|---|---|
| **Setup Time** | 1 hour | 4 hours | 2 hours |
| **Throughput** | 300/sec | 1K/sec | 5K+/sec |
| **Availability** | 99% | 99.95% | 99.99% |
| **Cost** | $5K/mo | $20K/mo | $3K-10K/mo |
| **Ops Complexity** | Low | High | Medium |
| **Scaling** | Vertical only | Horizontal | Horizontal |

### 5.2 Single Instance

**Advantages**:
✓ Simple to set up and operate
✓ Low cost ($5K/month)
✓ Easy to debug (single place to look)
✓ No inter-node complexity
✓ Good for dev/test

**Disadvantages**:
✗ No fault tolerance (downtime = loss)
✗ Limited throughput (~300 cases/sec)
✗ Vertical scaling expensive/limited
✗ Not suitable for production SLA >99.9%

**When to Use**:
- Development environment
- Small deployments (<1K cases total)
- Acceptable downtime (dev/test)
- Proof of concept

### 5.3 Clustered (High Availability)

**Architecture**:
```
┌─────────────────────────────────┐
│  Load Balancer (Active/Active)  │
├─────────────┬────────────────────┤
│             │                    │
├─────────┐   ├────────────┐   ┌──┴──────┐
│ Node 1  │   │   Node 2   │   │  Node 3 │
├─────────┘   └────────────┘   └─────────┘
│             │                    │
└─────────────┼────────────────────┘
              │
        ┌─────▼──────────────┐
        │  Shared Database   │
        │  (HA Replication)  │
        └────────────────────┘

Each node:
  - Independent process
  - Accesses shared DB
  - Elected leader for distributed tasks
  - Automatic failover if one node dies
```

**Advantages**:
✓ Fault tolerance (one node down = service continues)
✓ Higher throughput (add nodes)
✓ Load balancing across instances
✓ Scheduled maintenance without downtime
✓ Meets 99.95% SLA

**Disadvantages**:
✗ Complex setup (load balancer, consensus, replication)
✗ Database still bottleneck (not truly horizontal)
✗ Higher cost ($20K+/month)
✗ Difficult debugging (state spread across nodes)
✗ Not truly unlimited scaling (DB limit remains)

**When to Use**:
- Production deployments
- SLA >99.9% required
- Can tolerate deployment complexity
- <1K cases/sec throughput

### 5.4 Cloud-Native (Kubernetes)

**Architecture**:
```
Kubernetes Cluster
├─ Service (LoadBalancer)
│  ├─ Pod 1 (StatelessEngine)
│  ├─ Pod 2 (StatelessEngine)
│  ├─ Pod N (Auto-scaled)
│
├─ ConfigMap (App config)
├─ Secret (Credentials)
│
├─ Kafka (Event stream)
├─ Persistent Volume (Event log)
│
└─ Horizontal Pod Autoscaler
   └─ Scale 1-100 pods based on load
```

**Advantages**:
✓ Unlimited horizontal scaling
✓ Auto-scaling (no manual intervention)
✓ High availability (multi-zone)
✓ Cost-efficient (pay per execution)
✓ Global deployment (multi-region)
✓ Rolling updates (zero downtime)

**Disadvantages**:
✗ Requires Kubernetes knowledge
✗ Need external event store
✗ Debugging in distributed system
✗ Vendor lock-in (cloud provider)
✗ More moving parts to monitor

**When to Use**:
- Cloud-first organizations
- Variable/bursty workload
- Need to scale to 1K+ cases/sec
- Global presence required
- Cost-sensitive at scale

---

## 6. Monolithic vs Microservices

### 6.1 Decision: Keep YAWL Monolithic or Break Apart?

**Monolithic Approach** (Current v6):
```
┌─────────────────────────────────────────┐
│  Monolithic YAWL                        │
├──────┬──────┬────────┬──────┬───────────┤
│Engine│Auth  │Monitor │Works │Integr.   │
└──────┴──────┴────────┴──────┴───────────┘
       │
       └─ Single Process
          - Single deployment
          - Shared database
          - Tight coupling
```

**Microservices Approach** (Optional v6+):
```
┌────────────────────────────────────────┐
│           API Gateway                  │
├────┬─────┬──────┬──────┬──────┬────────┤
│    │     │      │      │      │        │
v    v     v      v      v      v        v
Engine  Auth  Monitor Worklet Resource Integr
Service Service Service Service Service Service
```

### 6.2 When to Choose Monolithic (Current)

**Advantages**:
✓ Simpler deployment (one artifact)
✓ Simpler operations (one process)
✓ Shared resources (no duplication)
✓ Easier debugging (single logs)
✓ Transactions across services (in-process)

**Disadvantages**:
✗ Can't scale individual services
✗ One service down = all down
✗ Harder to update (deploy all)
✗ Technology tied to Java
✗ Team must know entire codebase

**Best For**:
- <1K concurrent users
- <1K cases/sec throughput
- Single team (not large org)
- Standard workflow (no exotic integrations)

### 6.3 When to Choose Microservices (Future)

**Advantages**:
✓ Scale each service independently
✓ Technology choice per service
✓ Separate teams per service
✓ Resilience (one service down ≠ all down)
✓ Easy A/B testing

**Disadvantages**:
✗ Distributed transactions (eventual consistency)
✗ Network latency between services
✗ Operational complexity (many services)
✗ Data consistency challenges
✗ Requires mature DevOps

**Best For**:
- >1K concurrent users
- >1K cases/sec
- Multiple teams
- Complex, diverse requirements
- Budget for infrastructure

**YAWL Recommendation**: Stay monolithic for now (v6.0-6.3). Microservices evolution possible in v7+.

---

## 7. Comparison Summary

```
┌─────────────────────────────────────────────────────────────┐
│            YAWL v6 Architecture Decision Matrix             │
├──────────────────┬───────────────────┬─────────────────────┤
│  Dimension       │  Best Choice      │  Use When           │
├──────────────────┼───────────────────┼─────────────────────┤
│ Persistence      │ Persistent        │ Audit required      │
│                  │ Stateless         │ High throughput     │
│                  │ Hybrid            │ Mixed workload      │
├──────────────────┼───────────────────┼─────────────────────┤
│ Scale            │ Single instance   │ Dev/test, small     │
│                  │ Clustered         │ Production HA       │
│                  │ Cloud-native      │ High scale, cloud   │
├──────────────────┼───────────────────┼─────────────────────┤
│ Distribution     │ Monolithic        │ <1K users, simple   │
│                  │ Microservices     │ >1K users, complex  │
├──────────────────┼───────────────────┼─────────────────────┤
│ Database         │ PostgreSQL        │ Production primary  │
│                  │ MySQL             │ Cost-sensitive      │
│                  │ Event store       │ Stateless           │
└──────────────────┴───────────────────┴─────────────────────┘
```

---

## 8. Decision Framework (Visual)

```
START: Architecture Planning

What's your throughput requirement?
├─ <100 cases/sec
│  └─ Persistent Single Instance
│     ├─ Setup time: 1 hour
│     ├─ Cost: $5K/month
│     └─ Suitable for: Proof of concept, dev
│
├─ 100-500 cases/sec
│  └─ Persistent Single Instance + Backup
│     ├─ Setup time: 2 hours
│     ├─ Cost: $10K/month
│     └─ Suitable for: Small production (SLA 99.9%)
│
├─ 500-2K cases/sec
│  ├─ Option A: Clustered Persistent (3 nodes)
│  │  ├─ Setup time: 4 hours
│  │  ├─ Cost: $20K/month
│  │  └─ Suitable for: Medium enterprise
│  │
│  └─ Option B: Stateless + Event Store
│     ├─ Setup time: 6 hours
│     ├─ Cost: $15K/month
│     └─ Suitable for: Microservices, cloud-ready
│
└─ >2K cases/sec
   └─ Stateless + Cloud (Kubernetes)
      ├─ Setup time: 8 hours
      ├─ Cost: $3K-10K/month (variable)
      └─ Suitable for: High-scale, cloud-native

Does audit trail matter?
├─ YES (compliance required)
│  └─ Use Persistent (even if slow)
│
└─ NO (event history ok)
   └─ Use Stateless (faster, cheaper)

Budget constraint?
├─ <$50K/year
│  └─ Single instance persistent
│
├─ $50K-200K/year
│  └─ Clustered persistent OR stateless single region
│
└─ >$200K/year
   └─ Stateless multi-region cloud
```

---

## 9. Migration Paths

### Path 1: Start Small → Scale Persistent

```
Month 1-3:   Persistent Single Instance
             - Low cost ($5K/month)
             - Simple operations
             - Proof of concept

Month 4-6:   Add database replicas
             - Improves read performance
             - Still persistent

Month 7-12:  Clustered persistent (if needed)
             - Better availability
             - Still database-limited

Future:      Migrate to stateless (if throughput needed)
             - Complete rewrite of state management
             - Worth it only if >1K cases/sec
```

### Path 2: Start Cloud → Stateless

```
Month 1:     Stateless prototype
             - Single instance on Kubernetes
             - Kafka for events
             - Evaluate cloud costs

Month 2-3:   Scale to 5-10 pods
             - Test auto-scaling
             - Verify performance

Month 4+:    Global deployment
             - Multi-region
             - Auto-scale 1-100 pods
```

### Path 3: Hybrid → Eventual Single Engine

```
Month 1:     Hybrid deployment
             - Persistent for audit workflows
             - Stateless for high-volume
             - Separate databases

Month 6:     Evaluate costs/complexity
             - If persistent still 80%: consolidate to persistent
             - If stateless still 80%: migrate all to stateless
             - If truly 50/50: keep hybrid

Year 1:      Standardize on single engine
             - Remove hybrid layer
             - Consistent operations model
```

---

## 10. Quick Reference Card

```
╔════════════════════════════════════════════════════════════╗
║              YAWL Architecture Quick Picker                ║
╠════════════════════════════════════════════════════════════╣
║                                                            ║
║ Compliance required?           → Persistent               ║
║ High throughput (>1K/sec)?     → Stateless               ║
║ Long-running cases (>1 day)?   → Persistent               ║
║ Auto-scaling needed?           → Stateless + Cloud       ║
║ Limited ops team?              → Single instance          ║
║ 99.99% uptime required?        → Cloud-native            ║
║ Unknown requirements?          → Start persistent, migrate ║
║ Want to try before commit?     → Hybrid                   ║
║                                                            ║
║ Java 25 optimizations enabled? YES ✓ (all architectures)  ║
║                                                            ║
╚════════════════════════════════════════════════════════════╝
```

---

## References

- [Performance Matrix](PERFORMANCE_MATRIX.md) — Detailed benchmarks
- [Feature Matrix](FEATURE_MATRIX.md) — What's available where
- [Migration Planner](MIGRATION_PLANNER.md) — How to change
- [Scaling Decisions](SCALING_DECISIONS.md) — When to scale
- [Dual Engine Architecture](explanation/dual-engine-architecture.md) — Deep dive

---

**The best architecture is the one that solves your problem with minimal complexity. Start simple, evolve as needed.**
