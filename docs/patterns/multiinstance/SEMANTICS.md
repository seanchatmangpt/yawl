# WCP-13 to WCP-18: Multi-Instance & Deferred Choice Semantics

This document defines the execution semantics and guarantees for WCP-13 through WCP-18 workflow patterns in YAWL.

---

## WCP-13: Multiple Instances with A Priori Design-Time Knowledge

**Formal Definition:**  
A task instance is created once for each element in a fixed-size collection determined at design time.

**Execution Model:**

1. **Enablement:** Task transitions to enabled state
2. **Instance Creation:** Exactly N instances created (N specified in YAML `min`/`max`)
3. **Parallel Execution:** All N instances enabled concurrently; each processes independently
4. **Join:** Each instance produces a completion token; join waits for threshold (typically all N)
5. **Continuation:** Single token flows downstream

**Timing Model:**  
- Completion time = max(duration of all instances)
- Total work = sum(duration of all instances) ÷ num_workers (parallelism)

**YAWL Mechanisms:**
- Split: XOR (single token enters, spawns via multiInstance)
- Join: AND (all instances must complete)
- Data: miDataInput specifies per-instance variables

**Example:**
```yaml
tasks:
  - id: ReviewDocuments
    multiInstance:
      min: 3
      max: 3
      mode: static
      threshold: all
    split: xor
    join: and
    description: "Review 3 documents in parallel"
```

**Guarantees:**
- ✅ Exactly 3 instances enabled
- ✅ All 3 complete before continuing
- ✅ No tokens lost or duplicated
- ✅ Deterministic behavior (same input, same instance count)

**Use Cases:**
- Fixed approval panels (always 3 reviewers)
- Parallel batch processing with known item count
- Copy tasks to fixed number of recipients

---

## WCP-14: Multiple Instances with A Priori Runtime Knowledge

**Formal Definition:**  
Instance count determined from case data at the moment task is enabled, before instances are created.

**Key Distinction from WCP-13:**  
Cardinality is dynamic (read from `/net/data/itemCount`) but frozen at task enablement. Changing the variable later does NOT create new instances.

**Execution Model:**

1. **Task Enablement:** Task transitions to enabled state
2. **Cardinality Evaluation:** Engine evaluates XPath expression `/net/data/itemCount`
3. **Instance Creation:** N instances created (N = value read in step 2)
4. **Execution:** Same as WCP-13
5. **Frozen Cardinality:** If itemCount changes during execution, no new instances added

**YAWL Mechanisms:**
- Converter must emit `<maximum query="/net/data/itemCount"/>` instead of literal
- Engine must evaluate query at task enablement, cache result

**Example:**
```yaml
variables:
  - name: itemCount
    type: xs:integer
    default: 5

tasks:
  - id: ProcessItems
    multiInstance:
      min: 1
      max: itemCount  # XPath reference, not literal
      mode: dynamic
      threshold: all
    split: xor
    join: and
    description: "Process N items (N = itemCount at task enable time)"
```

**Guarantees:**
- ✅ Instance count = value of itemCount at enablement
- ✅ If itemCount is 5 at enable, exactly 5 instances created (regardless of later changes)
- ✅ All instances wait for all-join before continuing
- ✅ Result data aggregated via miDataOutput

**Scenarios:**

| itemCount @ Enable | Expected Behavior |
|---|---|
| 1 | Single instance (no parallelism) |
| 5 | 5 instances in parallel |
| 100 | 100 instances (performance depends on engine) |
| 0 | UNDEFINED (document as: raise exception or skip task) |

**Use Cases:**
- Process variable number of line items (count from data)
- Parallel review of N submitted documents (count from database query result)
- Multi-recipient notification (sender list size from config)

---

## WCP-15: Multiple Instances Without A Priori Runtime Knowledge (Continuation)

**Formal Definition:**  
Instance count not fully known at task enablement. Instances can be added incrementally during execution.

**Key Distinction:**  
Unlike WCP-14, this pattern allows **new instances to be created mid-execution** via external signal or task output.

**Execution Model:**

1. **Task Enablement:** Create min instances (e.g., 1 or 2)
2. **Parallel Execution:** Initial batch executes
3. **Continuation Signal:** External event or prior task output signals creation of new instances
4. **New Instance Creation:** Engine spawns additional instances with new data
5. **Join:** Waits for ALL (initial + added) to complete
6. **Continuation Loop:** Process repeats until completion signal received

**Mechanisms:**
- Converter emits `<creationMode code="continuation"/>`
- Engine exposes API: `addMultiInstanceItem(taskId, caseId, itemData)`
- Join waits for threshold of dynamically-growing set

**Example:**
```yaml
tasks:
  - id: ProcessItems
    multiInstance:
      min: 1
      max: 999999  # Effectively unbounded
      mode: continuation
      threshold: all
    split: xor
    join: and
    description: "Process items added incrementally"
```

**Guarantees:**
- ✅ At least min instances always created
- ✅ New instances can be added anytime before task completion
- ✅ Join waits for all (including newly added)
- ✅ Each instance scoped to unique data
- ✅ Completion semantics: task completes when threshold of ALL instances satisfied

**Signals for Adding Instances:**

```
Option A: External API
  engine.addMultiInstanceItem(taskId, caseId, itemData)

Option B: Task Output
  Prior task writes new items to /net/data/queue
  Task periodically polls and creates instances

Option C: Timer-Triggered
  Periodic timer queries backend system for new work
```

**Use Cases:**
- Batch processing with items arriving over time (queue processing)
- Escalating approval chain (add higher authority if lower rejects)
- Dynamic resource allocation (add workers as load increases)

---

## WCP-16: Multiple Instances Without A Priori Knowledge (Discriminator Variant)

**Formal Definition:**  
Multiple instances compete to completion. First instance to complete triggers join; remaining instances cancelled.

**Semantics:**  
Also called "threshold-based partial join" or "competitive first-past-post". Often used in fallback/escalation patterns.

**Execution Model:**

1. **Task Enablement:** Create min instances (e.g., 2 or 3)
2. **Parallel Execution:** All instances execute concurrently
3. **First Completion:** First instance to complete satisfies threshold = 1
4. **Join Activation:** Single token flows downstream
5. **Cancellation:** All other instances cancelled (if in-progress) or removed (if pending)
6. **Guarantee:** Exactly one instance's result used

**YAWL Mechanisms:**
- `join: or` (not `and`)
- `threshold: 1` (not `all`)
- Auto-cancellation via `<removesTokens id="TaskId"/>`

**Example:**
```yaml
tasks:
  - id: QueryBackends
    multiInstance:
      min: 2
      max: 3  # Query up to 3 backend services
      mode: dynamic
      threshold: 1  # Accept first response
      completionStrategy: cancelRemaining
    join: or
    cancels: [QueryBackends]
    description: "Race 3 backend queries, use first response"
```

**Guarantees:**
- ✅ At least 2 instances start (min=2)
- ✅ Task continues as soon as 1 instance completes
- ✅ Other instances cancelled (no wasted work)
- ✅ No duplicate processing (exactly 1 result taken)
- ✅ Deterministic: whoever finishes first wins

**Timing:**  
- Completion time = min(duration of all instances)
- Fairness: all instances get equal resources; fastest/luckiest wins

**Race Condition Handling:**

| Scenario | Behavior |
|---|---|
| Instance A finishes at 1s, B at 1.1s | A's result used; B cancelled |
| A and B finish simultaneously | First to mark output wins; system-dependent |
| All instances fail | Task fails (join never fires) |
| Instance cancelled mid-execution | Treated as incomplete; does not count |

**Use Cases:**
- Parallel SLA-based queries (use fastest backend)
- Fallback escalation (manager → director → VP, stop at first acceptance)
- Competitive bidding (accept first bid meeting criteria)
- Failover (primary, secondary, tertiary; use first success)

---

## WCP-17: Interleaved Parallel Routing

**Formal Definition:**  
Multiple tasks available for execution, but **at most one can execute at any time**. Execution order is not predetermined; any available task can execute next.

**Key Constraint:**  
**No concurrency.** Although multiple tasks are enabled, only one resource can claim and execute a task at a time.

**Execution Model:**

1. **Multiple Tasks Enabled:** Tasks A, B, C all enabled simultaneously
2. **Resource Claims Task:** Resource/user claims one task (e.g., A)
3. **Task Execution:** A executes exclusively
4. **Completion:** A completes; may enable additional tasks or loop back
5. **Next Claim:** Resource claims next available task (could be B, A again, etc.)
6. **Repeat:** Until all required tasks complete

**Mutual Exclusion:**  
Enforced via state variables and guard conditions, not engine-level locks.

**Example:**
```yaml
variables:
  - name: taskA_done
    type: xs:boolean
    default: false
  - name: taskB_done
    type: xs:boolean
    default: false
  - name: taskC_done
    type: xs:boolean
    default: false

tasks:
  - id: Start
    flows: [TaskA, TaskB, TaskC]
    split: and  # All enabled
    join: xor

  - id: TaskA
    flows: [TaskB, TaskC, Complete]
    condition: taskA_done == false -> TaskA  # Re-entrant if not done
    split: xor
    join: xor

  - id: TaskB
    flows: [TaskA, TaskC, Complete]
    condition: taskB_done == false -> TaskB
    split: xor
    join: xor

  - id: TaskC
    flows: [TaskA, TaskB, Complete]
    condition: taskC_done == false -> TaskC
    split: xor
    join: xor

  - id: Complete
    flows: [end]
    split: xor
    join: xor
```

**Guarantees:**
- ✅ All three tasks must eventually complete (at least once each)
- ✅ Execution order arbitrary (user/resource chooses)
- ✅ No two tasks executing simultaneously
- ✅ Deterministic termination (all complete exactly once)

**Execution Sequences (Examples):**

```
Sequence 1: A → B → C → done
  Task A executes, sets taskA_done=true, loops back via condition
  Next, user claims TaskB (or TaskC if A can't re-enter)
  ...

Sequence 2: C → A → B → done
  Order completely different, but same constraint: one at a time

Sequence 3: A → B → A → C → done
  A executes twice (first incomplete check allows re-entry)
  Then B, then A again, then C
```

**Use Cases:**
- Assembly line with multiple stations (worker rotates through stations serially)
- Form completion (user fills sections in any order)
- Inspection checklist (inspector marks items, any order)
- Iterative review (reviewer makes passes, revisits sections as needed)

---

## WCP-18: Deferred Choice

**Formal Definition:**  
Path selection determined by external events, not process logic. Environment chooses which path to activate; unchosen paths cancelled.

**Contrast to WCP-04 (Exclusive Choice):**
- **WCP-04 (XOR Split):** Process logic evaluates a condition; one path selected deterministically
- **WCP-18 (Deferred Choice):** Environment/external agent selects; process has no control

**Execution Model:**

1. **Multiple Paths Enabled:** All possible paths (Timeout, Message, Signal) enabled concurrently
2. **Environment Event:** One external event occurs (timer fires, message arrives, signal sent)
3. **Path Selection:** Path corresponding to event fires; unchosen paths cancelled
4. **Execution:** Selected path executes
5. **Downstream:** Single token continues (XOR join guarantees)

**YAWL Mechanisms:**
- `deferredChoice: true` annotation (converter generates this)
- All outgoing flows from WaitForEvent task
- Each flow corresponds to possible event
- Implicit cancellation of unchosen paths

**Example:**
```yaml
tasks:
  - id: WaitForEvent
    flows: [HandleTimeout, HandleMessage, HandleSignal]
    split: xor
    join: xor
    deferredChoice: true
    description: "Wait for external event"

  - id: HandleTimeout
    flows: [ProcessResult]
    split: xor
    join: xor
    timer:
      trigger: onEnabled
      duration: PT5M
    description: "5-minute timeout"

  - id: HandleMessage
    flows: [ProcessResult]
    split: xor
    join: xor
    description: "Message received"

  - id: HandleSignal
    flows: [ProcessResult]
    split: xor
    join: xor
    description: "Signal received"

  - id: ProcessResult
    flows: [end]
    split: xor
    join: xor
    description: "Process based on which path taken"
```

**Guarantees:**
- ✅ Exactly one path executes (enforced by environment)
- ✅ Unchosen paths are cancelled (no orphaned work items)
- ✅ Deterministic continuation (single token downstream)
- ✅ No deadlock (timer ensures eventual completion)

**Event Priority (Race Conditions):**

| Events | First Event Wins |
|---|---|
| Timeout fires @ 5min | HandleTimeout executes |
| Message arrives @ 2min, Timeout @ 5min | HandleMessage executes (arrives first); timer cancelled |
| Message + Signal both @ 1s | First to claim work item; other cancelled |
| No events (case abandoned) | Case waits indefinitely (or timeout eventually fires) |

**Use Cases:**
- Order confirmation (wait for customer confirmation within 24h, else cancel)
- Escalation with timeout (wait for response; escalate if no response within deadline)
- Multi-channel decision (wait for call, email, or web approval; whichever comes first)
- Circuit breaker (wait for success; if timeout, switch to fallback)

---

## Cross-Pattern Comparison

| Pattern | Cardinality | Instance Timing | Join Strategy | Cancellation |
|---|---|---|---|---|
| **WCP-13** | Fixed (design-time) | All created at once | AND (all) | N/A |
| **WCP-14** | Dynamic (read at enable) | All created at once | AND (all) | N/A |
| **WCP-15** | Incremental | Created over time | AND (all dynamically) | Via API |
| **WCP-16** | Unknown (competitive) | All created at once | OR (first=1) | Auto on 1st completion |
| **WCP-17** | N/A (interleaved tasks) | Sequential | XOR (one at a time) | N/A |
| **WCP-18** | N/A (external choice) | Parallel paths | XOR (environment decides) | Auto on selection |

---

## Implementation Notes for YAWL Engine

### XPath Evaluation (WCP-14)

```java
// At task enablement:
String query = task.getMultiInstanceSpec().getMaximumQuery();  // "/net/data/itemCount"
int count = evaluateXPath(query, caseData);
createInstances(task, count);
```

### Continuation Support (WCP-15)

```java
// Engine API:
public void addMultiInstanceItem(String taskId, String caseId, Object itemData) {
    Task task = getTask(taskId, caseId);
    if (!task.supportsContiation()) throw new UnsupportedOperationException();
    
    WorkItem newItem = createWorkItem(task, itemData);
    updateJoinCondition(task, newItem);  // Recalculate threshold
}
```

### Cancellation (WCP-16)

```java
// On first instance completion with threshold=1:
if (task.getThreshold() == 1 && completionCount >= 1) {
    cancelOtherInstances(task, caseId);
    fireJoin(task);
}
```

### Deferred Choice Handling (WCP-18)

```java
// On WaitForEvent task enablement:
List<String> flows = task.getOutgoingFlows();
for (String flow : flows) {
    addCancellationSet(flow, "if " + flow + " not selected");
}

// When one path selected:
cancelOtherFlows(task, selectedFlow);
```

---

## Testing Recommendations

### Test Patterns per WCP

**WCP-13:**
- [ ] Verify exactly N instances created
- [ ] Verify all N complete before join
- [ ] Verify execution trace shows N tasks
- [ ] Performance: measure parallel speedup

**WCP-14:**
- [ ] Test with itemCount = 1, 5, 100
- [ ] Verify XPath evaluation at enable time
- [ ] Test changing itemCount mid-execution (should be ignored)

**WCP-15:**
- [ ] Start with min=1, add 4 via API
- [ ] Verify join waits for all 5
- [ ] Test concurrent addInstance calls

**WCP-16:**
- [ ] Verify first completion wins
- [ ] Verify others cancelled
- [ ] Measure cleanup time (should be O(1))

**WCP-17:**
- [ ] Verify only one task executes at a time
- [ ] Test all 6 orderings (A-B-C, A-C-B, etc.)
- [ ] Verify no deadlock

**WCP-18:**
- [ ] Verify timeout cancels message/signal
- [ ] Verify message cancels timeout/signal
- [ ] Test simultaneous events (race)

---

## References

- Workflow Patterns: https://www.workflowpatterns.com/
- YAWL Book: "Modern Business Process Automation" (Springer, 2009)
- YAWL Schema 6.0: `/home/user/yawl/schema/YAWL_Schema6.0.xsd`
