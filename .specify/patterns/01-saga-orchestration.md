# Saga Orchestration Pattern Specification

## Pattern Overview

**Pattern ID**: `saga-orchestration`
**Category**: Workflow Control Pattern (Advanced)
**Workflow Patterns Reference**: Not in original 43 patterns - modern distributed transaction pattern

### Description

The Saga Orchestration pattern implements distributed transactions with compensating actions. When a sequence of tasks fails, compensating actions are executed in reverse order to undo partial work. This is essential for microservices architectures and distributed workflows.

### State Machine Definition

```
States:
  - IDLE: Saga not started
  - FORWARD: Executing forward tasks
  - COMPENSATING: Executing compensating actions (reverse order)
  - COMPLETED: All forward tasks completed successfully
  - COMPENSATED: All compensating actions completed
  - FAILED: Compensating actions failed (requires manual intervention)

Transitions:
  IDLE -> FORWARD (on startSaga)
  FORWARD -> FORWARD (on taskComplete, more tasks remain)
  FORWARD -> COMPLETED (on taskComplete, no more tasks)
  FORWARD -> COMPENSATING (on taskFailure)
  COMPENSATING -> COMPENSATING (on compensateComplete, more to compensate)
  COMPENSATING -> COMPENSATED (on compensateComplete, all compensated)
  COMPENSATING -> FAILED (on compensateFailure)
```

### Class Hierarchy

```
YTask
  └── YCompositeTask
        └── YSagaOrchestrationTask (NEW)
              ├── YSagaStep (NEW) - individual step definition
              ├── YCompensatingAction (NEW) - compensating action wrapper
              └── YSagaState (NEW) - saga execution state tracker
```

### Integration Points

| Existing Class | Integration Point | Purpose |
|----------------|-------------------|---------|
| `YTask` | Extend `_removeSet` | Track steps for compensation |
| `YTask._mi_active` | Read/Write | Track active saga steps |
| `YTask._mi_complete` | Read/Write | Track completed steps |
| `YNetRunner` | Event listener | Step completion/failure events |
| `YWorkItem` | Status callback | Work item completion triggers |
| `E2WFOJNet` | Reset net extension | Saga state in marking |

### Required Methods

#### YSagaOrchestrationTask

```java
public class YSagaOrchestrationTask extends YCompositeTask {

    // Saga configuration
    private List<YSagaStep> _forwardSteps;
    private Map<String, YCompensatingAction> _compensatingActions;
    private YSagaState _sagaState;

    // Core saga methods
    public void addSagaStep(YSagaStep step);
    public void addCompensatingAction(String stepId, YCompensatingAction action);
    public void startSaga(YPersistenceManager pmgr) throws YStateException;
    public void onStepComplete(YPersistenceManager pmgr, String stepId)
        throws YStateException, YPersistenceException;
    public void onStepFailure(YPersistenceManager pmgr, String stepId, Throwable cause)
        throws YStateException, YPersistenceException;
    public void compensate(YPersistenceManager pmgr)
        throws YStateException, YPersistenceException;

    // State queries
    public YSagaState getSagaState();
    public List<String> getCompletedSteps();
    public List<String> getPendingCompensations();
    public boolean isCompensating();

    // Override from YTask
    @Override
    protected void startOne(YPersistenceManager pmgr, YIdentifier id)
        throws YPersistenceException;
}
```

#### YSagaStep

```java
public class YSagaStep {
    private String _stepId;
    private YTask _task;
    private String _compensatingActionRef;
    private int _order;
    private boolean _isCritical;  // If true, failure triggers immediate compensation

    public String getStepId();
    public YTask getTask();
    public String getCompensatingActionRef();
    public int getOrder();
    public boolean isCritical();
}
```

#### YCompensatingAction

```java
public class YCompensatingAction {
    private String _actionId;
    private YDecomposition _decomposition;
    private Map<String, String> _inputMappings;  // From original task output
    private String _xqueryExpression;  // For dynamic compensation logic

    public void execute(YPersistenceManager pmgr, Element inputData)
        throws YStateException, YQueryException;
    public String getActionId();
    public YDecomposition getDecomposition();
}
```

#### YSagaState

```java
public class YSagaState {
    private String _sagaId;
    private SagaPhase _phase;  // IDLE, FORWARD, COMPENSATING, COMPLETED, COMPENSATED, FAILED
    private List<String> _completedStepIds;
    private String _failedStepId;
    private Throwable _failureCause;
    private int _currentCompensationIndex;

    public enum SagaPhase {
        IDLE, FORWARD, COMPENSATING, COMPLETED, COMPENSATED, FAILED
    }
}
```

### Example Usage

```xml
<!-- YAWL Specification Extension -->
<sagaOrchestration id="OrderProcessing">
  <forwardSteps>
    <step id="ReserveInventory" order="1" critical="true">
      <task ref="InventoryService"/>
      <compensatingAction ref="ReleaseInventory"/>
    </step>
    <step id="ProcessPayment" order="2" critical="true">
      <task ref="PaymentService"/>
      <compensatingAction ref="RefundPayment"/>
    </step>
    <step id="ShipOrder" order="3" critical="false">
      <task ref="ShippingService"/>
      <compensatingAction ref="CancelShipment"/>
    </step>
  </forwardSteps>

  <compensatingActions>
    <action id="ReleaseInventory" decomposition="InventoryReleaseService"/>
    <action id="RefundPayment" decomposition="PaymentRefundService"/>
    <action id="CancelShipment" decomposition="ShippingCancelService"/>
  </compensatingActions>
</sagaOrchestration>
```

### Runtime Behavior

```
1. Saga starts in FORWARD phase
2. Steps execute in order (1, 2, 3...)
3. On step success: Record completion, proceed to next step
4. On step failure:
   a. Record failed step
   b. Switch to COMPENSATING phase
   c. Execute compensating actions in reverse order (3, 2, 1)
5. All compensations complete: COMPENSATED state
6. All forward steps complete: COMPLETED state
```

### Data Flow

```
Forward Step Output -> Stored in Saga Context
Saga Context -> Input to Compensating Action

Example:
  ReserveInventory outputs: {reservationId: "R123", items: [...]}
  ReleaseInventory receives: {reservationId: "R123"}
```

### Error Handling

| Error Scenario | Behavior |
|----------------|----------|
| Forward step fails | Start compensation from last successful step |
| Compensation fails | Enter FAILED state, raise alert, require manual intervention |
| Timeout on step | Treat as failure, trigger compensation |
| Network partition | Retry with exponential backoff, then compensate |

### Thread Safety

- All state mutations must be synchronized
- Compensation execution is sequential (not parallel)
- Forward steps may execute in parallel if marked non-critical

### Persistence Requirements

- Saga state must be persisted after each state transition
- Step completion data must be persisted for compensation
- Support recovery from crash during compensation
