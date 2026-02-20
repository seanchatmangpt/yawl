---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/resourcing/**"
  - "*/src/test/java/org/yawlfoundation/yawl/resourcing/**"
  - "**/yawl-resourcing/**"
---

# Resourcing Rules

## Resource Service Architecture
- `ResourceManager` — Central coordinator for resource allocation
- Work items flow through queues: Offered → Allocated → Started → Completed
- Resources are Participants (humans) or NonHumanResources (services, agents)

## Allocation Strategies
- **Offer**: Broadcast to all eligible participants
- **Allocate**: Direct assignment to specific participant
- **Start**: Participant begins work on allocated item
- Strategies are pluggable — use strategy pattern for custom allocators

## Work Queue Model
- Each participant has: Offered, Allocated, Started, Suspended queues
- Administrator queue for escalation and reallocation
- Work items can be delegated, reallocated, or piled (batch-assigned)

## Filters and Constraints
- Organizational filters: Role, Position, Capability, OrgGroup
- Runtime filters: familiar task, round-robin, shortest queue
- Constraints are composable (AND/OR logic)
- Four-eyes principle: separation of duties between tasks
