---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/resourcing/**"
  - "*/src/test/java/org/yawlfoundation/yawl/resourcing/**"
  - "**/yawl-resourcing/**"
---

# Resourcing Rules

## Resource Service Architecture
- `ResourceManager` — Central coordinator: allocation, queue management, participant lookup
- `YParticipant` — A human resource; has roles, positions, capabilities, and four queues
- `NonHumanResource` — A service or agent resource (no user interaction)
- Work items flow through queues: **Offered → Allocated → Started → (Suspended →) Completed**
- Hibernate-backed persistence for participants, roles, and queue state

## Work Queue Model

Each `YParticipant` owns four queues:

| Queue | Semantics |
|-------|-----------|
| **Offered** | Broadcast to eligible participants; anyone may accept |
| **Allocated** | Directly assigned; participant must start it |
| **Started** | Participant is actively working on the item |
| **Suspended** | Work paused; item may be resumed or reallocated |

Administrator queue holds items requiring escalation, reallocation, or four-eyes review.

Work items support: **delegate** (pass to another), **reallocate** (admin moves), **pile** (batch-assign multiple).

## Allocation Strategies (Pluggable)
```java
// Strategy interface — use this pattern for custom allocators
public interface ResourceAllocator {
    YParticipant allocate(YWorkItem item, List<YParticipant> eligiblePool);
}

// Built-in strategies
RandomAllocator       — random selection from eligible pool
RoundRobinAllocator   — rotate through eligible participants
ShortestQueueAllocator — assign to participant with fewest started items
FamiliarTaskAllocator  — prefer participant who completed this task type before
```

## Filters and Constraints
- **Organisational filters** (apply first): Role, Position, Capability, OrgGroup membership
- **Runtime filters** (apply after): familiar task, round-robin, shortest queue
- Filters are composable with AND/OR logic: `FilterChain.and(roleFilter, capabilityFilter)`
- **Four-eyes principle**: configure task pair (A, B) so participant who completed A cannot start B
- **Chinese wall**: OrgGroup separation — participants in Group X cannot see items from Group Y

## Concurrency Rules
- Use virtual threads for participant availability queries (I/O-bound LDAP/DB lookups)
- Never hold participant queue lock across I/O: `ReentrantLock` not `synchronized`
- Queue state transitions must be atomic: use Hibernate transactions, not in-memory state

## Integration with Engine
- Engine fires `YWorkItemEvent.ENABLED` → ResourceManager offers to eligible participants
- ResourceManager calls Interface B `checkOut()` when participant starts work
- ResourceManager calls Interface B `checkIn()` with output data on completion
- Failure to check in → item returns to Offered queue after configurable timeout

## Error Handling
```java
// CORRECT — reallocation failure must be explicit
try {
    resourceManager.reallocate(workItemId, newParticipantId);
} catch (ResourceException e) {
    throw new WorkQueueException("Reallocation failed for " + workItemId, e);
}

// VIOLATION — silent fallback on allocation failure
YParticipant p = allocator.allocate(item, pool);
if (p == null) return;  // item silently disappears from queue
```

## Testing Patterns
- Use `ResourceLogicUnitTest` pattern: construct real `YParticipant` with synthetic org data
- Test allocator strategies with fixed eligible pools (no randomness in deterministic tests)
- Verify four-eyes: confirm that completing task A removes participant from B's eligible pool
- H2 in-memory DB for Hibernate persistence tests (no mocking of EntityManager)
