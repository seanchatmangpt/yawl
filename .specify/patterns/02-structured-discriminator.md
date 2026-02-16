# Structured Discriminator Pattern Specification

## Pattern Overview

**Pattern ID**: `structured-discriminator`
**Category**: Workflow Control Pattern (Advanced Branching)
**Workflow Patterns Reference**: WCP-9 (Discriminator)

### Description

The Structured Discriminator is a join pattern where the first incoming token activates the downstream flow, while subsequent tokens are consumed without effect. After all incoming branches have completed, the discriminator resets for the next cycle. This pattern is useful for racing scenarios where only the first result matters.

### State Machine Definition

```
States (per discriminator instance):
  - WAITING: No tokens received yet
  - ACTIVATED: First token received, downstream activated
  - CONSUMING: Subsequent tokens being consumed
  - RESET_READY: All branches completed, ready to reset

Transitions:
  WAITING -> ACTIVATED (on first token arrival, activate downstream)
  ACTIVATED -> CONSUMING (on subsequent token arrival)
  CONSUMING -> CONSUMING (on more tokens arriving)
  CONSUMING -> RESET_READY (on all branches arrived)
  RESET_READY -> WAITING (on reset, ready for next cycle)
```

### Class Hierarchy

```
YTask
  └── YDiscriminatorTask (NEW)
        ├── YDiscriminatorJoinType (NEW) - enum for discriminator variants
        └── YDiscriminatorState (NEW) - tracks tokens per cycle
```

### Integration Points

| Existing Class | Integration Point | Purpose |
|----------------|-------------------|---------|
| `YTask._joinType` | New constant `_DISCRIMINATOR = 64` | Join type identifier |
| `YTask.t_enabled()` | Override logic | First token enables, others do not |
| `YTask.t_fire()` | Override logic | Consume tokens appropriately |
| `YInternalCondition` | Token tracking | Track which branches arrived |
| `YNet` | Reset coordination | Trigger discriminator reset |
| `E2WFOJNet` | Structural analysis | Discriminator-aware marking |

### Required Methods

#### YDiscriminatorTask (extends YTask)

```java
public class YDiscriminatorTask extends YTask {

    // Discriminator-specific constants
    public static final int _DISCRIMINATOR = 64;
    public static final int _DISCRIMINATOR_N_OUT_OF_M = 65;
    public static final int _DISCRIMINATOR_WITH_MEMORY = 66;

    // State tracking
    private YDiscriminatorState _discriminatorState;
    private int _requiredBranches;  // Number of incoming branches
    private int _nValue;  // For N-out-of-M variant

    // Core discriminator methods
    public boolean isFirstToken();
    public void consumeSubsequentTokens(YPersistenceManager pmgr)
        throws YPersistenceException;
    public boolean isReadyToReset();
    public void reset(YPersistenceManager pmgr)
        throws YPersistenceException;
    public Set<YExternalNetElement> getArrivedBranches();
    public int getArrivedCount();

    // Override from YTask
    @Override
    public synchronized boolean t_enabled(YIdentifier id);

    @Override
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr)
        throws YStateException, YDataStateException, YQueryException,
               YPersistenceException;

    // Configuration
    public void setRequiredBranches(int count);
    public void setNValue(int n);  // For N-out-of-M
    public int getRequiredBranches();
    public int getNValue();

    // Variant support
    public boolean isStructuredDiscriminator();
    public boolean isNOutOfMDiscriminator();
    public boolean isDiscriminatorWithMemory();
}
```

#### YDiscriminatorState

```java
public class YDiscriminatorState {
    private String _discriminatorId;
    private Set<String> _arrivedBranchIds;
    private boolean _activated;
    private int _cycleCount;

    public void recordArrival(String branchId);
    public boolean hasArrived(String branchId);
    public int getArrivalCount();
    public boolean isActivated();
    public void setActivated(boolean activated);
    public void reset();
    public int getCycleCount();
    public Set<String> getArrivedBranchIds();
}
```

#### YDiscriminatorJoinType (Enum)

```java
public enum YDiscriminatorJoinType {
    STRUCTURED(64),           // First passes, rest consumed, auto-reset
    N_OUT_OF_M(65),          // Nth token passes, rest consumed
    WITH_MEMORY(66);         // Remembers activations across cycles

    private final int _code;

    YDiscriminatorJoinType(int code) {
        _code = code;
    }

    public int getCode() {
        return _code;
    }
}
```

### Example Usage

```xml
<!-- YAWL Specification Extension -->
<task id="RaceHandler">
  <join code="discriminator"/>
  <split code="xor"/>

  <!-- Incoming branches from parallel split -->
  <flowsInto>
    <condition id="c1"/>  <!-- From fast service -->
    <condition id="c2"/>  <!-- From medium service -->
    <condition id="c3"/>  <!-- From slow service -->
  </flowsInto>

  <!-- First to arrive triggers execution, others discarded -->
</task>
```

### Runtime Behavior

```
Scenario: 3 parallel services racing (Fast, Medium, Slow)

1. All three services start in parallel
2. Fast service completes first
   - Token arrives at discriminator
   - Discriminator state: WAITING -> ACTIVATED
   - Downstream flow activated
   - Work item created
3. Medium service completes
   - Token arrives at discriminator
   - Discriminator state: ACTIVATED -> CONSUMING
   - Token consumed, no downstream effect
4. Slow service completes
   - Token arrives at discriminator
   - Discriminator state: CONSUMING -> RESET_READY
   - Token consumed, no downstream effect
   - All branches arrived, ready to reset
5. Reset triggered
   - Discriminator state: RESET_READY -> WAITING
   - Ready for next cycle
```

### N-out-of-M Variant

```
For N=2, M=3:
- First token: consumed, wait for more
- Second token: activates downstream
- Third token: consumed, reset

This variant is useful when you need quorum-based decisions.
```

### Discriminator with Memory

```
Remembers which branches activated across cycles:
- Cycle 1: Branch A activates (B, C consumed)
- Cycle 2: Branch B activates (A, C consumed)
- Cycle 3: Branch C activates (A, B consumed)

Useful for fairness and rotation scenarios.
```

### Reset Mechanisms

| Reset Type | Trigger | Behavior |
|------------|---------|----------|
| Automatic | All branches arrived | Immediately reset to WAITING |
| Manual | Explicit API call | Reset on demand |
| Timeout-based | No activity for N seconds | Force reset |
| Cycle-based | After N activations | Reset for fairness |

### Thread Safety

- Token arrival tracking must be atomic
- State transitions must be synchronized
- Multiple concurrent tokens handled in order

### Interaction with Other Patterns

| Pattern | Compatibility | Notes |
|---------|---------------|-------|
| AND Split | Full | Discriminator joins AND splits perfectly |
| XOR Split | Full | Works with conditional branching |
| OR Split | Partial | Complex interaction, careful design needed |
| Multi-Instance | Limited | Not recommended combination |

### Verification Rules

1. Discriminator must have at least 2 incoming branches
2. All incoming branches must originate from same parallel split (structured)
3. N-out-of-M requires N <= M
4. Discriminator with memory requires state persistence
