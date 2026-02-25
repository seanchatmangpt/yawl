# YAWL Core Concepts Explained

> Understand the WHY. Deep knowledge for confident workflow design. 🧠

---

## Foundation: Petri Nets and Tokens

### What is a Petri Net?

A **Petri net** is a mathematical model for describing workflows as graphs:

```
┌──────────────┐
│  Place (●)   │ ← Can hold tokens (marks progress)
└──────────────┘

┌──────────────┐
│ Transition (█)│ ← Rules for token movement
└──────────────┘

    ●(token) → [█ rule] → ● ← Tokens flow through transitions
```

**In YAWL terms**:
- **Place** = Condition (merge point, holds tokens)
- **Transition** = Task (processes tokens)
- **Token** = Control point (shows where execution is)

### Why Petri Nets Matter

Petri nets give YAWL **mathematical soundness**:
- ✅ No deadlocks (all paths have exits)
- ✅ No live-locks (tasks always eventually complete)
- ✅ No orphaned tokens (tokens never lost)
- ✅ Proven correctness (unlike ad-hoc workflow tools)

**Example**:
```
Input → [Task A] → Condition → [Task B] → Output
                        ↓
                   [Task C]
                        ↓
                    Condition
```

Tokens flow: Input → A → Condition (1 token) → B AND C run parallel → merge → Output

---

## Architecture: YEngine vs YStatelessEngine

### YEngine (Stateful)

```
┌─────────────────────────────┐
│  YAWL Engine (Stateful)     │
│  • Database persistence     │
│  • Long-running cases       │
│  • Multi-day workflows      │
│  • State recovery on crash  │
└─────────────────────────────┘
```

**Use when**:
- Cases must survive server restarts
- Multiple server cluster needed
- Workflows run hours/days/weeks
- Audit trail required

**Drawback**: Slower (DB I/O)

### YStatelessEngine (Stateless)

```
┌─────────────────────────────┐
│  Stateless Engine           │
│  • Memory only              │
│  • In-process execution     │
│  • Sub-second workflows     │
│  • No persistence           │
└─────────────────────────────┘
```

**Use when**:
- Fast request-response needed
- Workflow completes in seconds
- No long-term state needed
- Embedded in microservice

**Benefit**: Fast, simple, no DB required

**Key insight**: Both use same Petri net semantics. Just different persistence models.

---

## Work Items: The Core Concept

### What is a Work Item?

A **work item** is:
- A task instance waiting to be done
- An assignment to a user/system
- The "to-do" list entry

**Lifecycle**:
```
1. ENABLED     ← Task is ready (inputs satisfied)
2. ALLOCATED  ← Assigned to person/system
3. STARTED    ← Person opens it
4. EXECUTING  ← Work in progress
5. COMPLETED  ← Task done, outputs captured
6. [SKIPPED]  ← Task cancelled
```

### Why Work Items Matter

Work items are the **contract** between:
- Workflow (demands work be done)
- System/Human (receives assignment)

**Example**:
```java
// From workflow's view:
YWorkItem item = new YWorkItem(caseID, "ApproveOrder", "user@company.com");

// From person's view:
// "I have a task: ApproveOrder for case CASE-001"
// [They click "I'm doing this"]
// [They review order]
// [They click "Approved" — completes work item]
```

When you call `completeWorkItem()`, you're saying: "I finished the task, here's the result."

---

## Specifications: The Blueprint

### YAWL Specification Structure

```xml
<specification>
  <decomposition id="root" isRootNet="true">
    <!-- Process definition -->
    <variable><!-- Data variables --></variable>
    <processControlElements>
      <condition id="start"/>
      <task id="work"/>
      <condition id="end"/>
      <flow source="start" target="work"/>
      <flow source="work" target="end"/>
    </processControlElements>
  </decomposition>

  <decomposition id="subprocess">
    <!-- Sub-workflow definition (optional) -->
  </decomposition>
</specification>
```

### Why Specification Matters

The specification is the **single source of truth**:
- Humans read it (diagram)
- YAWL engine executes it (XML)
- Tests verify it (executable specs)
- Auditors check it (compliance)

**Key principle**: If it's not in the spec, it doesn't execute.

---

## Conditions: More Than Merge Points

### What is a Condition?

A **condition** is:
- A place where tokens wait
- A synchronization point (for parallel tasks)
- A decision point (for branching)
- A data store (holds variables)

### Why Conditions Matter

Conditions enforce **Petri net semantics**:

```
       [Task A]
          ↓
        (Condition) ← Can hold multiple tokens
       ↙      ↘
    [B]        [C]    ← Both run in parallel

       ↘      ↙
        (Condition) ← Waits for all tokens (join)
          ↓
       [Task D]
```

**Without conditions** → Can't model parallel flows properly
**With conditions** → Petri net guarantees correctness

### Types of Conditions

| Type | Purpose | Example |
|------|---------|---------|
| InputCondition | Starts workflow | Generate initial token |
| OutputCondition | Ends workflow | Accept final token → complete |
| InternalCondition | Merge/join point | Synchronize parallel tasks |
| ExternalCondition | Sub-workflow input | Pass data to subprocess |

---

## Decompositions: Nesting and Modularity

### What is a Decomposition?

A **decomposition** is:
- A self-contained workflow fragment
- Can be reused in multiple places
- Can be a task or a sub-net
- Encapsulates complexity

### Why Decompositions Matter

Real workflows get complex:
```
Main Workflow
  ├─ [Invoice Task] → Decomposition: invoice-subprocess
  │    ├─ Validate amounts
  │    ├─ Check budget
  │    ├─ Route for approval
  │    └─ Record payment
  └─ [Ship Task] → Decomposition: shipping-subprocess
       ├─ Pick items
       ├─ Pack box
       ├─ Print label
       └─ Queue for truck
```

**Benefits**:
- ✅ Reuse workflows (call invoice-subprocess from 10 places)
- ✅ Easier to understand (focus on one piece)
- ✅ Independent testing (test subprocess in isolation)
- ✅ Version independently (update invoice logic without touching order logic)

---

## Variables and Data Flow

### How Data Moves Through Workflows

```
Case Variables (shared state)
  ↓
Input → [Task reads input] → [Task writes output] → Output
              ↓                       ↓
         Local input            Local output
         (copy of var)           (updated var)
```

### Example: Order Processing

```xml
<variable>
  <name>orderTotal</name>
  <type>decimal</type>
</variable>
```

**Flow**:
1. Order arrives: `orderTotal = 1000.00`
2. Task "ApplyDiscount" reads `orderTotal`, outputs `discountedTotal = 900.00`
3. Variable updated: `orderTotal = 900.00`
4. Task "CheckBudget" reads new `orderTotal`

### Why This Matters

- ✅ Variables are **case-global** (all tasks see same values)
- ✅ **Sequential tasks** can only run if they have input data ready
- ✅ **Parallel tasks** might deadlock if they depend on each other's outputs
- ⚠️ **Data dependencies** create implicit sequencing

---

## Synchronization and Deadlocks

### The Parallel Task Problem

```
        [Task A]
           ↓
       (Condition) ← One token here
       ↙      ↘
    [B]        [C]  ← Both wait for input
    ↓          ↓
   (Output)   (Output)
```

**What happens?**
- Token reaches Condition
- Splits to B AND C (both enabled)
- Both run in parallel
- BOTH must complete before moving forward

**If B gets stuck** → C waits forever → Deadlock!

### How YAWL Prevents This

```xml
<!-- Join semantics -->
<condition id="join">
  <joinSemantics>and</joinSemantics> <!-- Wait for ALL inputs -->
</condition>
```

**YAWL's guarantee**: If you have a join, it will eventually complete (assuming tasks complete).

### Why This Matters

- ✅ You can safely use parallel tasks
- ✅ YAWL handles the coordination
- ❌ But YOU must ensure all paths can execute
- ❌ Example deadlock: Task B needs output from Task C, but Task C needs output from Task B

---

## Execution Semantics: The Full Story

### Creating a Case

```
Specification (blueprint) + Variables (data)
        ↓
        ↓  engine.createCase(spec, vars)
        ↓
Case (instance)
  ├─ CaseID (unique identifier)
  ├─ Specification (which workflow)
  ├─ Variables (current state)
  ├─ Markings (where tokens are)
  └─ Work Items (tasks to do)
```

### Completing a Task

```
Work Item (task waiting)
  ├─ CaseID (which case)
  ├─ TaskID (which task in workflow)
  ├─ OutputData (results from task)
  └─ Timestamp (when done)

        ↓
        ↓  engine.completeWorkItem(item, outputData, ...)
        ↓

Update Case
  ├─ Merge outputData into variables
  ├─ Move token from task → next condition
  ├─ Enable next tasks (if inputs ready)
  └─ Update work items list
```

### Full Execution Flow

```
1. createCase(spec)
   → Generate initial token at InputCondition
   → Enable first task

2. getWorkItems(caseID)
   → Return all currently enabled tasks
   → User sees: "What can I do now?"

3. completeWorkItem(item, outputs)
   → Record task completion
   → Update case variables
   → Move token to next condition
   → Check if next tasks can run (inputs satisfied?)
   → Enable next work items

4. Repeat steps 2-3 until...

5. Last token reaches OutputCondition
   → Case complete
   → No more work items
```

---

## Key Invariants (The Guarantees)

| Invariant | Meaning | Why |
|-----------|---------|-----|
| **No orphaned tokens** | Every token eventually reaches output | Soundness proof |
| **No deadlocks** | Tasks don't wait forever | Workflow properties |
| **Deterministic** | Same spec + data = same flow | Reproducible |
| **Atomic** | Task completion is all-or-nothing | Consistency |

---

## Mental Models

### Model 1: Token Flow (Think Like Petri Net)
```
Tokens flow: Input → A → Condition → [B, C parallel] → Join → D → Output
             1 token   consumed    created 2 tokens  waits  consumed
```

### Model 2: State Machine (Think Like Case)
```
Case: {
  state: EXECUTING,
  variables: {orderID: "123", total: 1000},
  markings: {conditionA: 1, conditionB: 2},
  workItems: [TaskX, TaskY]
}
```

### Model 3: To-Do List (Think Like Human)
```
My tasks today:
  ☐ Review Order (OrderID 123)
  ☐ Approve Budget (Amount 10000)
  ☐ Schedule Shipment

I complete "Review Order" → system tells me what's next
```

**All 3 views are the same thing**, just different perspectives!

---

## Design Patterns

### Pattern: Sequential Workflow
```
A → B → C
```
Simple: do A, then B, then C.

### Pattern: Parallel Workflows
```
     B
    / \
A ↗   ↘ D
    \ /
     C
```
A, then do B and C in parallel, then D.

### Pattern: Decision Tree
```
      B
     / \
A ↗   ↘ D
     \ /
      C
```
A, then either B or C (not both), then D.

### Pattern: Subprocess Reuse
```
[Main] calls [Invoice] calls [Approve] → [Record]
```
Decompositions let you call workflows like functions.

---

## Common Misconceptions

| Myth | Reality |
|------|---------|
| "Workflows must complete fast" | No — can run for years. State is persistent. |
| "Parallel = concurrent execution" | No — YAWL is single-threaded. Parallel = potential concurrency, but we execute one step at a time. |
| "Conditions are just connectors" | No — conditions are full Petri net places. They hold state! |
| "Work items are tasks" | No — work items are task INSTANCES. One task can have many instances. |
| "Complete work item = mark task done" | Close, but not exact — completing outputs data, which triggers downstream logic. |

---

## Further Study

When ready:
1. **Petri Net Theory** → Wikipedia (30 min)
2. **YAWL Paper** → Research article (1 hour)
3. **Workflow Patterns** → `.claude/rules/engine/workflow-patterns.md`
4. **Architecture** → `docs/v6/architecture/`

---

**You now understand YAWL at first principles.** Design workflows with confidence! 🚀

---

Last updated: 2026-02-20
