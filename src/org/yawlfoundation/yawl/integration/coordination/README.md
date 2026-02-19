# Coordination Event Logging (ADR-025)

This package implements event logging for coordination scenarios based on ADR-025. All coordination events are properly logged with traceability for audit and debugging purposes.

## Event Types

### 1. ConflictEvent (`events/ConflictEvent.java`)
- **Purpose**: Track conflict detection and resolution
- **Types**: RESOURCE, PRIORITY, TIMING, POLICY, DEPENDENCY, AUTHORITY, DATA, COMMUNICATION
- **Severity**: LOW, MEDIUM, HIGH, CRITICAL
- **Resolution Strategies**: PRIORITY_BASED, FIFO, ROUND_ROBIN, CONSENSUS, MANUAL, ESCALATION, TIMEOUT, RANDOM

### 2. HandoffEvent (`events/HandoffEvent.java`)
- **Purpose**: Track handoff transfers between agents/systems
- **Types**: AGENT, SYSTEM, STATE, AUTHORITY, EMERGENCY, SCHEDULED, LOAD_BALANCING
- **Status**: INITIATED, OFFERED, ACCEPTED, TRANSFERRING, COMPLETED, FAILED, CANCELLED, REJECTED

### 3. ResolutionEvent (`events/ResolutionEvent.java`)
- **Purpose**: Track resolution outcomes of coordination conflicts
- **Types**: AUTOMATIC, CONSENSUS, ARBITRATION, VOTING, ESCALATION, NEGOTIATION, TIMEOUT, MANUAL
- **Status**: INITIATED, VOTING, CONSENSUS, PENDING, COMPLETED, FAILED, TIMEOUT, CANCELLED
- **Decisions**: APPROVE, REJECT, APPROVE_CONDITIONAL, ESCALATE, DEFER, RETRY

### 4. AgentDecisionEvent (`events/AgentDecisionEvent.java`)
- **Purpose**: Track agent decisions and their context
- **Types**: RESOURCE_ALLOCATION, TASK_ASSIGNMENT, PRIORITY_ORDERING, CONFLICT_RESOLUTION, etc.
- **Includes**: Options considered, decision factors, final decision, execution plan

## Service: ConflictEventLogger

### Features
- **Event Correlation**: Automatically correlates related events using trace IDs
- **Batch Logging**: Supports batch logging for performance optimization
- **Event Filtering**: Configurable filtering based on severity and type
- **Traceability**: Maintains complete audit trails for compliance
- **Performance Monitoring**: Tracks logging performance and metrics

### Usage Example

```java
// Create logger
DataSource dataSource = ...; // JDBC data source
ConflictEventLogger logger = new ConflictEventLogger(dataSource);

// Log a detected conflict
ConflictEvent conflict = ConflictEvent.detected(
    ConflictEvent.ConflictType.RESOURCE,
    ConflictEvent.Severity.HIGH,
    "Multiple agents competing for server-1",
    new String[]{"agent-1", "agent-2"},
    new String[]{"wi-123", "wi-456"},
    new String[]{"YAWL-POLICY-001"},
    Map.of("resource", "server-1", "requestedAt", Instant.now().toString()),
    Instant.now()
);
logger.logConflictDetected(conflict);

// Log a resolution
ResolutionEvent resolution = conflict.resolved(
    ResolutionEvent.ResolutionStrategy.PRIORITY_BASED,
    "coordinator-service",
    Instant.now()
);
logger.logResolution(resolution);
```

## Integration with WorkflowEventStore

All coordination events extend the existing `WorkflowEvent` system:
- New event types added to `WorkflowEvent.EventType` enum
- Events are stored in the same `workflow_events` table
- Maintain backward compatibility with existing event processing

## Event Schema

All coordination events follow a consistent JSON schema with:
- Unique event ID (UUID)
- Event type and timestamp
- Case and work item context
- Type-specific payload data
- Correlation trace IDs

## Monitoring

The `ConflictEventLogger` provides metrics:
- Total logged events
- Filtered events
- Failed events
- Success rate
- Active traces for monitoring

## Testing

See `ConflictEventLoggerTest` for comprehensive test coverage including:
- Event creation and serialization
- Logging with real WorkflowEventStore
- Batch processing
- Metrics tracking
- Error handling