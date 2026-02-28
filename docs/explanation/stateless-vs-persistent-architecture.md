# Explanation: Stateless vs Persistent Engine Architecture

This document explains the architectural differences between YAWL's two execution models and when to use each.

---

## Overview

YAWL 6.0.0 provides two distinct execution engines with fundamentally different approaches to state management:

| Aspect | Persistent Engine | Stateless Engine |
|--------|------------------|-----------------|
| **State Storage** | Database (PostgreSQL, H2, etc.) | Event stream (Kafka, RabbitMQ, S3) |
| **Scalability** | Vertical (single DB bottleneck) | Horizontal (stateless pods) |
| **Deployment Model** | Traditional servers, containers | Cloud-native, serverless |
| **Consistency Model** | Strong (ACID) | Eventual (Event-sourced) |
| **Failover** | Requires DB failover | Stateless - instant recovery |
| **Audit Trail** | Derived from case state | Native - events are truth |
| **Latency** | Low (direct DB access) | Variable (event replay) |

---

## Persistent Engine Architecture

### Overview

The traditional YAWL engine (`YEngine`) manages case state through direct database persistence.

```
┌──────────────┐
│ Work Item    │
│ Completion   │
└──────┬───────┘
       │
       ↓
┌──────────────────────────────────────┐
│  YEngine                             │
│  - Interpret specification           │
│  - Manage case state                 │
│  - Evaluate guards/joins             │
│  - Enable/complete tasks             │
└──────────┬───────────────────────────┘
           │
           ↓
    ┌──────────────┐
    │ PostgreSQL   │
    │ Database     │
    │              │
    │ Case State   │
    │ Work Items   │
    │ Audit Log    │
    └──────────────┘
```

### State Model

All case data is stored in normalized database tables:

```sql
-- Cases table
CREATE TABLE YAWL_CASE (
    CaseID VARCHAR(255) PRIMARY KEY,
    SpecURI VARCHAR(255),
    Status VARCHAR(50),
    StartTime TIMESTAMP,
    EndTime TIMESTAMP
);

-- Work items table
CREATE TABLE YAWL_WORK_ITEM (
    WorkItemID VARCHAR(255) PRIMARY KEY,
    CaseID VARCHAR(255),
    TaskID VARCHAR(255),
    Status VARCHAR(50),
    InputData XML,
    OutputData XML
);

-- Case data (variables)
CREATE TABLE YAWL_CASE_DATA (
    CaseID VARCHAR(255),
    VarName VARCHAR(255),
    VarValue XML,
    PRIMARY KEY (CaseID, VarName)
);
```

### Execution Flow

1. **Task Enabled**: Engine inserts row in YAWL_WORK_ITEM with Status='enabled'
2. **User Completes**: Updates YAWL_WORK_ITEM with Status='completed' and OutputData
3. **Engine Processes**: Reads updated row, evaluates flows/joins, enables next tasks
4. **Persists State**: All changes written atomically to database

### Consistency Guarantees

- **ACID transactions**: Atomic work item completion
- **Serializable isolation**: Concurrent case execution safe
- **Durability**: Case state survives server crash
- **Audit trail**: All changes recorded in YAWL_AUDIT table

---

## Stateless Engine Architecture

### Overview

The stateless engine (`YStatelessEngine`) reconstructs case state from an event stream. No persistent case storage.

```
┌──────────────┐
│ Work Item    │
│ Completion   │
└──────┬───────┘
       │
       ↓
┌──────────────────────────────────────┐
│  YStatelessEngine                    │
│  - Replay events to reconstruct state│
│  - Apply event and evaluate flows    │
│  - Generate enabled task events      │
│  - Append new event to stream        │
└──────────┬───────────────────────────┘
           │
           ↓
    ┌──────────────────┐
    │ Event Stream     │
    │ (Kafka/RabbitMQ) │
    │                  │
    │ WorkItemEnabled  │
    │ WorkItemCompleted│
    │ CaseStarted      │
    │ CaseCompleted    │
    └──────────────────┘
```

### State Model

No case state tables. Single "event log" table:

```sql
-- Single event stream
CREATE TABLE YAWL_EVENT_LOG (
    EventID BIGSERIAL PRIMARY KEY,
    CaseID VARCHAR(255),
    EventType VARCHAR(50),
    EventData JSON,
    Timestamp TIMESTAMP,
    INDEX(CaseID)
);

-- Event types:
-- - case.started
-- - task.enabled
-- - task.completed
-- - case.completed
```

### Execution Flow

1. **Task Enabled**: Engine appends `{type: "task.enabled", taskID: "...", inputData: {...}}` to event stream
2. **User Completes**: Appends `{type: "task.completed", workItemID: "...", outputData: {...}}`
3. **State Reconstruction**: Engine replays events since last snapshot to recover case state
4. **Flow Evaluation**: Applies net logic to determine next enabled tasks
5. **Persist Event**: Appends new `{type: "task.enabled", ...}` to stream

### Consistency Model

- **Eventual consistency**: Events processed asynchronously
- **At-least-once**: Idempotent event processing
- **Event ordering**: Per-case FIFO, cross-case parallel
- **Distributed audit**: Events are native audit trail

---

## Comparison: Key Scenarios

### Scenario 1: High-Volume Case Processing (100k+ concurrent)

**Persistent**: Scales to ~1000 cases/sec before DB becomes bottleneck.

```
Client Load    Database Connections    Throughput
─────────────  ─────────────────────   ──────────
10k clients         Pool Exhausted      Blocked, Queued
100k clients        Connection Limit    Degraded
1M clients          Out of Memory       Failed
```

**Stateless**: Scales linearly with pod count. 10 pods → 10× throughput.

```
Client Load    Event Stream Throughput    Throughput
─────────────  ─────────────────────────   ──────────
10k clients         < 1% capacity           Linear
100k clients        < 10% capacity          Linear
1M clients          < 100% capacity         Linear
```

### Scenario 2: Case State Query (GET case/{id})

**Persistent**: O(1) - single database query.

```java
SELECT * FROM YAWL_CASE WHERE CaseID = '12345'
// Response: 1-5ms
```

**Stateless**: O(n) where n = number of events in case.

```
Replay events for case '12345' from event stream
// Response: 50-500ms depending on case complexity
```

### Scenario 3: Engine Failover

**Persistent**:
- Pod crashes
- Connection lost
- New pod must reconnect to DB
- State is safe in DB
- Recovery time: 10-30 seconds (reconnect + warm-up)

**Stateless**:
- Pod crashes
- Instant recovery: Any pod can restart from same case
- No reconnect/warmup needed
- Recovery time: <1 second (already distributed)

### Scenario 4: Complete Audit Trail

**Persistent**:
```sql
-- Audit table needed for full history
SELECT * FROM YAWL_AUDIT
WHERE CaseID = '12345'
ORDER BY Timestamp
```
Changes are logged separately from case state.

**Stateless**:
```sql
-- Events ARE the audit trail
SELECT * FROM YAWL_EVENT_LOG
WHERE CaseID = '12345'
ORDER BY EventID
```
Native immutable audit trail.

---

## Deployment Pattern Differences

### Persistent Engine Deployment

**Single-Region Database Setup**:
```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ YAWL Pod 1  │  │ YAWL Pod 2  │  │ YAWL Pod 3  │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       └────────────────┼────────────────┘
                        ↓
                  ┌──────────────┐
                  │ PostgreSQL   │
                  │ Primary      │
                  └──────────────┘
```

All pods compete for database connections. Scaling is limited by DB capacity.

**With Replication**:
```
┌─────────────┐  ┌─────────────┐
│ YAWL Pod 1  │  │ YAWL Pod 2  │
└──────┬──────┘  └──────┬──────┘
       │ (write)         │ (read)
       │                 │
       ↓                 ↓
┌──────────────┐  ┌──────────────┐
│ Primary DB   │→→│ Replica DB   │
└──────────────┘  └──────────────┘
```

Reads can scale, but writes still bottleneck at primary.

### Stateless Engine Deployment

**Multi-Pod Setup**:
```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ YAWL Pod 1  │  │ YAWL Pod 2  │  │ YAWL Pod 3  │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │
       └────────────────┼────────────────┘
                        ↓
                  ┌──────────────────┐
                  │ Kafka Topic      │
                  │ yawl-events      │
                  │ (3 replicas)     │
                  └──────────────────┘
```

All pods are stateless. Scale by adding pods. Event stream is replicated.

**Multi-Region Setup**:
```
Region A                          Region B
┌─────────────┐                ┌─────────────┐
│ YAWL Pod 1  │                │ YAWL Pod 4  │
│ YAWL Pod 2  │                │ YAWL Pod 5  │
│ YAWL Pod 3  │                │ YAWL Pod 6  │
└──────┬──────┘                └──────┬──────┘
       │                              │
       └──────────────┬───────────────┘
                      ↓
       ┌──────────────────────────────┐
       │ Global Event Stream (Kafka)  │
       │ Geo-replicated               │
       └──────────────────────────────┘
```

Cases can be executed in any region. No region lock-in.

---

## Trade-Offs Summary

### Choose Persistent If:

1. **Sub-10ms latency required**: Persistent is faster for state queries
2. **Complex joins**: Easier to implement with direct DB access
3. **Existing database**: Already invested in DB infrastructure
4. **Vertical scalability sufficient**: < 10k concurrent cases
5. **Strong consistency critical**: ACID guarantees required

### Choose Stateless If:

1. **Horizontal scaling needed**: 10k+ concurrent cases
2. **Cloud-native deployment**: Kubernetes, serverless
3. **Multi-region required**: Global case distribution
4. **Fault tolerance critical**: Instant pod recovery
5. **Native audit trail**: Events as immutable history
6. **Cost sensitive**: No expensive database licensing

---

## Hybrid Approach

YAWL 6.0.0 supports **hybrid deployments**:

```yaml
# application.yml
yawl:
  engine:
    mode: hybrid

  persistent:
    enabled: true
    database: postgresql
    pool-size: 20
    hot-cases: true  # Keep recent cases in DB

  stateless:
    enabled: true
    event-store: kafka
    archive: true   # Move old cases to event store
```

In hybrid mode:
- **Active cases** (< 30 days): Stored in database (fast)
- **Archived cases** (> 30 days): Stored in event stream (cheap)
- **Queries**: Check DB first, fall back to event stream
- **Snapshots**: Periodic snapshots minimize replay

---

## Migration Path: Persistent → Stateless

YAWL provides migration tools:

```bash
# 1. Export all cases from persistent engine
yawl-cli export --engine persistent --format events \
  --output cases-export.json

# 2. Load into stateless engine
yawl-cli import --engine stateless --input cases-export.json \
  --event-store kafka

# 3. Verify (compare case states)
yawl-cli verify --source persistent --target stateless
```

---

## See Also

- [Stateless Engine Getting Started](../tutorials/yawl-stateless-getting-started.md)
- [Persistent Engine Best Practices](../how-to/deployment/production.md)
- [Event-Sourced Architecture](../explanation/event-sourced-architecture.md)
- [Architecture Decision Records](../explanation/decisions/)
