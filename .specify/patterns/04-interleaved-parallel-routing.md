# Interleaved Parallel Routing Pattern Specification

## Pattern Overview

**Pattern ID**: `interleaved-parallel-routing`
**Category**: Workflow Control Pattern (Concurrency Control)
**Workflow Patterns Reference**: WCP-17 (Interleaved Parallel Routing)

### Description

The Interleaved Parallel Routing pattern allows a set of tasks to be enabled concurrently, but only one task can execute at a time (mutual exclusion). Once a task completes, another enabled task may begin. This continues until all enabled tasks have completed. This is useful for tasks that share a resource or must not run in parallel due to conflicts.

### State Machine Definition

```
Router States:
  - IDLE: No tasks enabled
  - ENABLING: Tasks being enabled, waiting for mutex
  - EXECUTING_ONE: One task executing, others waiting
  - ALL_COMPLETE: All enabled tasks completed

Task States (within interleaved set):
  - NOT_ENABLED: Not yet activated by router
  - ENABLED_WAITING: Enabled but waiting for mutex
  - ENABLED_EXECUTING: Currently executing (holds mutex)
  - COMPLETED: Finished execution

Transitions:
  IDLE -> ENABLING (on router activation)
  ENABLING -> EXECUTING_ONE (on first task acquiring mutex)
  EXECUTING_ONE -> EXECUTING_ONE (on task complete, another starts)
  EXECUTING_ONE -> ALL_COMPLETE (on last task complete)
  ALL_COMPLETE -> IDLE (on router reset)
```

### Class Hierarchy

```
YTask
  └── YInterleavedRouterTask (NEW)
        ├── YInterleavedSet (NEW) - collection of interleaved tasks
        ├── YMutexLock (NEW) - mutual exclusion primitive
        └── YInterleavedState (NEW) - router state tracker
```

### Integration Points

| Existing Class | Integration Point | Purpose |
|----------------|-------------------|---------|
| `YTask` | New task type | Interleaved routing task |
| `YInternalCondition` | Mutex tracking | Track which task holds mutex |
| `YNetRunner` | Event coordination | Task completion triggers next |
| `YWorkItemRepository` | Work item queue | Queue enabled waiting items |
| `E2WFOJNet` | Structural analysis | Interleaved-aware analysis |

### Required Methods

#### YInterleavedRouterTask (extends YTask)

```java
public class YInterleavedRouterTask extends YTask {

    // Router-specific constants
    public static final int _INTERLEAVED_JOIN = 70;
    public static final int _INTERLEAVED_SPLIT = 71;

    // State tracking
    private YInterleavedSet _interleavedSet;
    private YMutexLock _mutexLock;
    private YInterleavedState _routerState;

    // Core router methods
    public void enableAllTasks(YPersistenceManager pmgr)
        throws YPersistenceException;
    public boolean tryAcquireMutex(String taskId, YPersistenceManager pmgr)
        throws YPersistenceException;
    public void releaseMutex(YPersistenceManager pmgr)
        throws YPersistenceException;
    public void onTaskComplete(String taskId, YPersistenceManager pmgr)
        throws YPersistenceException;

    // Task management
    public void addInterleavedTask(YTask task);
    public void removeInterleavedTask(String taskId);
    public Set<YTask> getInterleavedTasks();
    public Set<YTask> getEnabledWaitingTasks();
    public YTask getExecutingTask();

    // State queries
    public YInterleavedState getRouterState();
    public boolean isMutexHeld();
    public String getMutexHolder();
    public int getCompletedCount();
    public int getTotalCount();
    public boolean isAllComplete();

    // Selection strategy
    public void setSelectionStrategy(InterleavedSelectionStrategy strategy);
    public YTask selectNextTask();  // Based on strategy

    // Override from YTask
    @Override
    public synchronized boolean t_enabled(YIdentifier id);

    @Override
    public synchronized List<YIdentifier> t_fire(YPersistenceManager pmgr)
        throws YStateException, YDataStateException, YQueryException,
               YPersistenceException;
}
```

#### YInterleavedSet

```java
public class YInterleavedSet {
    private String _setId;
    private Set<YTask> _tasks;
    private Map<String, TaskState> _taskStates;
    private Queue<String> _waitingQueue;  // FIFO for waiting tasks

    public enum TaskState {
        NOT_ENABLED, ENABLED_WAITING, ENABLED_EXECUTING, COMPLETED
    }

    // Set operations
    public void addTask(YTask task);
    public void removeTask(String taskId);
    public boolean containsTask(String taskId);

    // State management
    public void setTaskState(String taskId, TaskState state);
    public TaskState getTaskState(String taskId);
    public void markCompleted(String taskId);

    // Queue operations
    public void enqueue(String taskId);
    public String dequeue();
    public int getQueueSize();
    public boolean isQueueEmpty();

    // Queries
    public Set<YTask> getAllTasks();
    public Set<YTask> getWaitingTasks();
    public Set<YTask> getCompletedTasks();
    public YTask getExecutingTask();
    public int getCompletedCount();
    public int getTotalCount();

    // Reset
    public void reset();
}
```

#### YMutexLock

```java
public class YMutexLock {
    private String _lockId;
    private String _holderTaskId;  // null if unlocked
    private long _acquiredTimestamp;
    private long _timeout;  // 0 = no timeout

    // Lock operations
    public boolean tryAcquire(String taskId)
        throws MutexAcquisitionException;
    public void release(String taskId)
        throws MutexReleaseException;
    public boolean isLocked();
    public String getHolder();

    // Timeout handling
    public void setTimeout(long timeoutMs);
    public boolean isTimedOut();
    public long getHoldDuration();

    // Force release (for recovery)
    public void forceRelease();
}
```

#### InterleavedSelectionStrategy (Enum/Interface)

```java
public enum InterleavedSelectionStrategy {
    FIFO,           // First enabled, first to execute
    PRIORITY,       // Based on task priority
    RANDOM,         // Random selection
    SHORTEST_FIRST, // Estimate shortest duration
    ROUND_ROBIN;    // Fair rotation

    public YTask select(Set<YTask> waitingTasks, YInterleavedState state);
}
```

### Example Usage

```xml
<!-- YAWL Specification Extension -->
<net id="DocumentProcessing">
  <!-- Input condition -->
  <inputCondition id="input"/>

  <!-- Interleaved router -->
  <interleavedRouter id="InterleavedProcessing"
                     selectionStrategy="FIFO"
                     mutexTimeout="300000">

    <!-- Tasks that must execute one at a time -->
    <interleavedTasks>
      <task ref="SpellCheck"/>
      <task ref="GrammarCheck"/>
      <task ref="FormatCheck"/>
      <task ref="PlagiarismCheck"/>
    </interleavedTasks>
  </interleavedRouter>

  <!-- Output -->
  <outputCondition id="output"/>

  <!-- Flows -->
  <flow source="input" target="InterleavedProcessing"/>
  <flow source="InterleavedProcessing" target="output"/>
</net>
```

### Runtime Behavior

```
Scenario: Document processing with 4 checks

1. Document arrives at router
   - All 4 tasks enabled concurrently
   - Router state: IDLE -> ENABLING
   - Task states: all NOT_ENABLED -> ENABLED_WAITING

2. First task acquires mutex (FIFO: SpellCheck)
   - Router state: ENABLING -> EXECUTING_ONE
   - SpellCheck: ENABLED_WAITING -> ENABLED_EXECUTING
   - Mutex holder: SpellCheck

3. SpellCheck completes
   - SpellCheck: ENABLED_EXECUTING -> COMPLETED
   - Mutex released
   - Next task selected: GrammarCheck

4. GrammarCheck acquires mutex
   - GrammarCheck: ENABLED_WAITING -> ENABLED_EXECUTING
   - Mutex holder: GrammarCheck

5. Continue until all complete...
   - FormatCheck: ENABLED_EXECUTING -> COMPLETED
   - PlagiarismCheck: ENABLED_EXECUTING -> COMPLETED

6. All tasks complete
   - Router state: EXECUTING_ONE -> ALL_COMPLETE
   - All task states: COMPLETED
   - Mutex released, router ready to fire

7. Router fires to downstream
   - Router state: ALL_COMPLETE -> IDLE
   - Token moves to output condition
```

### Selection Strategies

| Strategy | Selection Logic | Use Case |
|----------|-----------------|----------|
| FIFO | First enabled, first executed | Fair ordering |
| PRIORITY | Highest priority task first | Critical tasks first |
| RANDOM | Random selection | Load distribution |
| SHORTEST_FIRST | Estimated shortest duration | Minimize wait time |
| ROUND_ROBIN | Rotate through tasks | Fair over multiple cycles |

### Mutex Timeout Handling

```
If a task holds mutex beyond timeout:
1. Log warning with task ID and duration
2. Option A: Force release mutex (configurable)
3. Option B: Alert for manual intervention
4. Option C: Continue waiting (no timeout behavior)

Default: Log warning, continue waiting
```

### Cancellation Support

```
Cancel all enabled tasks:
- Release mutex if held
- Mark all as NOT_ENABLED
- Reset router to IDLE

Cancel single task:
- If executing: force complete or rollback
- If waiting: remove from queue
- Update task state
```

### Interaction with Other Patterns

| Pattern | Interaction | Notes |
|---------|-------------|-------|
| Parallel Split | Precedes | Split feeds interleaved router |
| AND Join | Follows | All interleaved tasks must complete |
| Multi-Instance | Complex | MI within interleaved needs care |
| Cancel Region | Full | Cancel releases mutex |

### Thread Safety

- Mutex acquisition/release must be atomic
- State transitions synchronized
- Queue operations thread-safe

### Verification Rules

1. Interleaved router must have at least 2 tasks
2. All interleaved tasks must be in same net
3. Selection strategy must be valid
4. Mutex timeout must be positive or zero
5. No circular dependencies within interleaved set
