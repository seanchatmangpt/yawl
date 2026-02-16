# Milestone Pattern Specification

## Pattern Overview

**Pattern ID**: `milestone`
**Category**: Workflow Control Pattern (State-Based)
**Workflow Patterns Reference**: WCP-18 (Milestone)

### Description

The Milestone pattern enables a task based on the state of another part of the process. A task guarded by a milestone can only execute while the milestone condition is achieved (the milestone is "reached"). This is a context-dependent enablement pattern where the execution of one task depends on the state of another task or condition in the workflow.

### State Machine Definition

```
Milestone States:
  - NOT_REACHED: Milestone condition not satisfied
  - REACHED: Milestone condition satisfied, dependent tasks may execute
  - EXPIRED: Milestone was reached but has been invalidated

Task States (for milestone-guarded task):
  - BLOCKED: Milestone not reached, cannot execute
  - ENABLED: Milestone reached, may execute
  - EXECUTING: Task is running
  - COMPLETED: Task finished (milestone no longer relevant)

Transitions:
  NOT_REACHED -> REACHED (on milestone condition becoming true)
  REACHED -> EXPIRED (on milestone condition becoming false)
  EXPIRED -> REACHED (on milestone condition becoming true again)
```

### Class Hierarchy

```
YExternalNetElement
  └── YCondition
        ├── YInputCondition
        ├── YOutputCondition
        └── YMilestoneCondition (NEW) - milestone marker

YTask
  └── YMilestoneGuardedTask (NEW) - task with milestone guard
```

### Integration Points

| Existing Class | Integration Point | Purpose |
|----------------|-------------------|---------|
| `YCondition` | New subclass | Represent milestone marker |
| `YTask.t_enabled()` | Override | Check milestone state |
| `YNetRunner` | Event listener | Milestone state changes |
| `YInternalCondition` | State tracking | Milestone reached/expired |
| `YNet` | Milestone registry | Lookup milestones by ID |
| `YMarking` | Milestone presence | Check milestone in marking |

### Required Methods

#### YMilestoneCondition (extends YCondition)

```java
public class YMilestoneCondition extends YCondition {

    private boolean _isReached;
    private String _milestoneExpression;  // XQuery/XPath to evaluate
    private long _reachedTimestamp;
    private long _expiryTimeout;  // milliseconds, 0 = no expiry

    // Core milestone methods
    public boolean isReached();
    public void setReached(boolean reached, YPersistenceManager pmgr)
        throws YPersistenceException;
    public void evaluateAndSetReached(YPersistenceManager pmgr)
        throws YPersistenceException, YQueryException;

    // Configuration
    public void setMilestoneExpression(String xquery);
    public String getMilestoneExpression();
    public void setExpiryTimeout(long timeoutMs);
    public long getExpiryTimeout();

    // State queries
    public long getReachedTimestamp();
    public boolean isExpired();
    public long getTimeSinceReached();

    // Override
    @Override
    public String toXML();
}
```

#### YMilestoneGuardedTask (extends YTask)

```java
public class YMilestoneGuardedTask extends YTask {

    private Set<YMilestoneCondition> _milestoneGuards;
    private MilestoneGuardOperator _guardOperator;  // AND, OR
    private Map<String, Boolean> _milestoneStateCache;

    // Core guarded task methods
    public void addMilestoneGuard(YMilestoneCondition milestone);
    public void removeMilestoneGuard(String milestoneId);
    public Set<YMilestoneCondition> getMilestoneGuards();

    // Guard evaluation
    public boolean areAllMilestonesReached();
    public boolean isAnyMilestoneReached();
    public boolean canExecute();  // Based on guard operator

    // Configuration
    public void setGuardOperator(MilestoneGuardOperator operator);
    public MilestoneGuardOperator getGuardOperator();

    // Override from YTask
    @Override
    public synchronized boolean t_enabled(YIdentifier id);

    @Override
    protected void startOne(YPersistenceManager pmgr, YIdentifier id)
        throws YPersistenceException;

    // State change handlers
    public void onMilestoneReached(String milestoneId);
    public void onMilestoneExpired(String milestoneId);
}
```

#### MilestoneGuardOperator (Enum)

```java
public enum MilestoneGuardOperator {
    AND,   // All milestones must be reached
    OR,    // Any milestone must be reached
    XOR;   // Exactly one milestone must be reached

    public boolean evaluate(Set<Boolean> milestoneStates) {
        switch (this) {
            case AND: return milestoneStates.stream().allMatch(b -> b);
            case OR:  return milestoneStates.stream().anyMatch(b -> b);
            case XOR: return milestoneStates.stream().filter(b -> b).count() == 1;
            default: return false;
        }
    }
}
```

### Example Usage

```xml
<!-- YAWL Specification Extension -->
<net id="OrderProcessing">
  <!-- Task that sets the milestone -->
  <task id="PaymentApproved">
    <flowsInto>
      <condition id="c1"/>
    </flowsInto>
  </task>

  <!-- Milestone condition (becomes reached when PaymentApproved completes) -->
  <milestone id="PaymentMilestone">
    <expression>//PaymentStatus = 'APPROVED'</expression>
    <expiryTimeout>3600000</expiryTimeout>  <!-- 1 hour -->
  </milestone>

  <!-- Task guarded by milestone -->
  <task id="ShipOrder" milestoneGuard="PaymentMilestone">
    <join code="and"/>
    <split code="xor"/>
  </task>

  <!-- Alternative: multiple milestone guards -->
  <task id="ProcessOrder" milestoneGuards="m1,m2,m3" guardOperator="AND">
    <!-- Can only execute when all milestones are reached -->
  </task>
</net>
```

### Runtime Behavior

```
Scenario: Order processing with payment milestone

1. Order created, ShipOrder task exists but is BLOCKED
   - PaymentMilestone state: NOT_REACHED
   - ShipOrder: BLOCKED (milestone not reached)

2. PaymentApproved task completes
   - PaymentMilestone expression evaluates to true
   - PaymentMilestone state: NOT_REACHED -> REACHED
   - ShipOrder: BLOCKED -> ENABLED
   - reachedTimestamp set

3. ShipOrder executes while milestone is REACHED
   - ShipOrder: ENABLED -> EXECUTING
   - Milestone still valid

4. If payment is cancelled during shipping:
   - PaymentMilestone expression evaluates to false
   - PaymentMilestone state: REACHED -> EXPIRED
   - ShipOrder: EXECUTING (continues, already started)

5. If another ShipOrder is attempted:
   - Milestone is EXPIRED
   - ShipOrder: BLOCKED (cannot start)
```

### Milestone Expression Evaluation

```java
// Expressions are evaluated against net data
String expression = "//PaymentStatus = 'APPROVED'";

// Evaluation triggers:
// 1. Data change in net
// 2. Task completion that affects data
// 3. Timer-based polling (optional)
// 4. Explicit evaluation request

// Expression context:
// - Net variables
// - Task outputs
// - External data (via ExternalDataGateway)
```

### Expiry Handling

| Expiry Type | Configuration | Behavior |
|-------------|---------------|----------|
| Time-based | expiryTimeout=N ms | Expire after N milliseconds |
| Data-based | expression changes | Expire when expression becomes false |
| Manual | API call | Expire on explicit request |
| Never | expiryTimeout=0 | Milestone never expires |

### Multiple Milestone Guards

```
AND: Task requires all milestones
  - Use case: Ship only when payment AND inventory confirmed

OR: Task requires any milestone
  - Use case: Proceed when fast-track OR normal approval received

XOR: Task requires exactly one milestone
  - Use case: Process with single approval path
```

### Interaction with Other Patterns

| Pattern | Interaction | Notes |
|---------|-------------|-------|
| Parallel Split | Full | Multiple branches can set milestones |
| Deferred Choice | Full | Milestone can influence choice |
| Cancel Region | Complex | Cancelling may expire milestones |
| Timer | Full | Timer can set or expire milestones |

### Thread Safety

- Milestone state changes must be atomic
- Guard evaluation must be consistent
- Cache invalidation on state change

### Verification Rules

1. Milestone expression must be valid XQuery/XPath
2. Guarded task must reference existing milestone
3. Milestone must be in same net as guarded task
4. Expiry timeout must be positive or zero
