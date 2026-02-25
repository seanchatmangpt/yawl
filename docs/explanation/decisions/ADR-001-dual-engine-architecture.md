# ADR-001: Dual Engine Architecture (Stateful + Stateless)

## Status
**ACCEPTED**

## Context

YAWL traditionally provided a single stateful engine (YEngine) that persists all workflow state to a relational database. This design supports long-running workflows and complex state management but has limitations:

### Business Drivers

1. **Enterprise Requirements (Stateful)**
   - Long-running workflows (days, weeks, months)
   - Persistent state required for auditing and compliance
   - Complex state transitions and rollback scenarios
   - Multi-user coordination and work distribution

2. **Cloud-Native Requirements (Stateless)**
   - Ephemeral workflows (seconds, minutes)
   - Horizontal scalability without shared state
   - Function-as-a-Service (FaaS) compatibility
   - Serverless deployment models (AWS Lambda, Cloud Functions)

3. **Hybrid Use Cases**
   - Same workflow definition executed in different contexts
   - Development/testing with stateless, production with stateful
   - Cost optimization: stateless for high-volume simple workflows

### Technical Constraints

1. **State Management Complexity**
   - Stateful engine requires database infrastructure
   - Stateless engine requires complete state in every invocation
   - Dual APIs complicate integration

2. **Performance Trade-offs**
   - Stateful: Database overhead, vertical scaling limits
   - Stateless: Serialization overhead, no cross-invocation state

3. **Code Duplication Risk**
   - Core workflow execution logic shared
   - Persistence and recovery logic differs
   - Maintenance burden increases

## Decision

**We will maintain both stateful and stateless engines with a shared core execution layer.**

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    YSpecification                       │
│              (Workflow Definition Layer)                │
└─────────────────┬───────────────────────┬───────────────┘
                  │                       │
      ┌───────────▼──────────┐   ┌───────▼──────────────┐
      │    YEngine (Stateful) │   │ YStatelessEngine     │
      ├───────────────────────┤   ├──────────────────────┤
      │ - Persistent state    │   │ - Ephemeral state    │
      │ - Hibernate ORM       │   │ - In-memory only     │
      │ - Transaction mgmt    │   │ - Serializable state │
      │ - Work item queues    │   │ - Function context   │
      │ - Long-running cases  │   │ - Short-lived cases  │
      └───────────┬───────────┘   └──────┬───────────────┘
                  │                      │
      ┌───────────▼──────────────────────▼───────────────┐
      │          YNetRunner (Shared Core)                │
      │   - Petri net execution semantics                │
      │   - Condition evaluation                         │
      │   - Data binding and transformation              │
      │   - OR-join synchronization                      │
      │   - Workflow patterns (89 patterns)              │
      └──────────────────────────────────────────────────┘
```

### Implementation Strategy

#### Stateful Engine (YEngine)
- **Persistence:** Hibernate 6.5 with Jakarta Persistence 3.0
- **Database:** PostgreSQL 13+, MySQL 8.0+, Oracle 19c+
- **State Storage:** All cases, work items, and data persisted
- **Recovery:** Point-in-time recovery, transaction rollback
- **Scalability:** Vertical scaling, read replicas for queries

#### Stateless Engine (YStatelessEngine)
- **Persistence:** None (in-memory only)
- **State Transfer:** Case state serialized between invocations
- **Recovery:** Idempotent execution, retry with full state
- **Scalability:** Horizontal scaling, unlimited parallelism

#### Shared Core (YNetRunner)
- **Execution Logic:** Identical Petri net semantics
- **Pattern Support:** All 89 workflow patterns
- **Data Binding:** Same XPath and JEXL expression evaluation
- **Condition Evaluation:** Identical boolean expression handling

### Migration Path

**Phase 1 (v5.2):** Both engines coexist, stateful primary
**Phase 2 (v5.3):** Equal support, usage patterns documented
**Phase 3 (v6.0):** Automatic engine selection based on specification hints

## Consequences

### Positive

1. **Flexibility**
   - Choose appropriate engine for each use case
   - Test with stateless, deploy with stateful
   - Migrate between engines with same specification

2. **Cloud-Native Support**
   - Deploy stateless engine to serverless platforms
   - Horizontal auto-scaling without coordination
   - Pay-per-invocation cost model

3. **Performance Options**
   - Stateless: sub-second execution for simple workflows
   - Stateful: minutes/hours for complex workflows
   - Match engine to workload characteristics

4. **Operational Simplicity**
   - Stateless: No database maintenance
   - Stateless: No backup/recovery procedures
   - Stateless: Simpler deployment pipeline

### Negative

1. **Code Duplication**
   - Two engine implementations to maintain
   - Duplicate interface implementations (A, B, X)
   - Increased testing surface area

2. **Cognitive Overhead**
   - Developers must understand both engines
   - Choose appropriate engine for each specification
   - Different operational considerations

3. **Feature Parity Challenges**
   - Some features easier in stateful (work lists, history)
   - Some features easier in stateless (parallelism)
   - Must maintain feature parity or document differences

4. **Integration Complexity**
   - Two sets of APIs to integrate
   - Client libraries must support both
   - Documentation must cover both paths

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Feature drift between engines | MEDIUM | HIGH | Shared test suite, common abstractions |
| Developer confusion | MEDIUM | MEDIUM | Clear documentation, decision flowchart |
| Performance regression | LOW | MEDIUM | Continuous benchmarking |
| State serialization bugs | MEDIUM | HIGH | Comprehensive serialization tests |

## Alternatives Considered

### Alternative 1: Stateful Only
**Rejected:** Does not support serverless or extreme horizontal scaling.

**Pros:**
- Single codebase to maintain
- No feature parity concerns
- Simpler documentation

**Cons:**
- Cannot deploy to FaaS platforms
- Vertical scaling limits
- Database overhead for simple workflows

### Alternative 2: Stateless Only
**Rejected:** Cannot support long-running workflows or complex state management.

**Pros:**
- Extreme scalability
- No database infrastructure
- Simpler operations

**Cons:**
- Cannot persist state between invocations
- No work lists or human tasks
- Large state serialization overhead

### Alternative 3: Adapter Layer
**Rejected:** Too complex, performance overhead, does not solve fundamental trade-offs.

**Pros:**
- Single logical engine
- Unified API

**Cons:**
- Adapter complexity
- Performance overhead
- Still requires two persistence strategies

## Related ADRs

- ADR-002: Singleton vs Instance-based YEngine (vertical vs horizontal scaling)
- ADR-010: Virtual Threads for Scalability (stateful engine concurrency)
- ADR-009: Multi-Cloud Strategy (deployment targets for both engines)

## Implementation Notes

### When to Use Each Engine

**Use Stateful Engine (YEngine) when:**
- Workflow duration > 5 minutes
- Human tasks or work lists required
- Case history and audit trail needed
- Complex state management
- Rollback or compensation required
- Multi-user coordination
- Example: Loan approval, order fulfillment

**Use Stateless Engine (YStatelessEngine) when:**
- Workflow duration < 5 minutes
- No human interaction
- High throughput required (>100 req/sec)
- Serverless deployment preferred
- Cost optimization important
- Example: Data validation, API orchestration

### Decision Flowchart

```
Is workflow duration > 5 minutes? ───YES──> Stateful
    │
    NO
    │
    ▼
Are human tasks required? ───YES──> Stateful
    │
    NO
    │
    ▼
Is audit trail mandatory? ───YES──> Stateful
    │
    NO
    │
    ▼
Throughput > 100 req/sec? ───YES──> Stateless
    │
    NO
    │
    ▼
Default: Stateful (safer choice)
```

### Code Example

```java
// Stateful engine usage
YEngine engine = YEngine.getInstance();
String caseId = engine.createCase(specId, data);
engine.startCase(caseId);
// State persisted to database

// Stateless engine usage
YStatelessEngine stateless = new YStatelessEngine();
YSpecification spec = loadSpec(specId);
YCaseState initialState = YCaseState.create(spec, data);
YCaseState finalState = stateless.executeToCompletion(initialState);
// No persistence, all state in-memory
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-10
**Implementation Status:** COMPLETE (v5.2)
**Review Date:** 2026-08-01 (6 months)

---

**Revision History:**
- 2026-02-10: Initial version approved
- 2026-02-16: Updated with deployment examples and decision flowchart
