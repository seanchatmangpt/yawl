# YAWL Elements Domain Model — Design Philosophy

Understand why YAWL's domain model is built on Petri nets and what it means for workflow design.

## The Petri Net Foundation

### Why Not Use Simple Flowcharts?

**Simple flowchart:**
```
┌─────┐
│Start│
└──┬──┘
   │
  [TaskA]
   │
  [TaskB]
   │
┌──▼──┐
│ End │
└─────┘
```

**Problem**: Ambiguous when multiple paths exist

```
      ┌─[TaskB]─┐
      │         │
[TaskA]         [TaskD]
      │         │
      └─[TaskC]─┘

What happens when TaskA completes?
- Do B and C execute simultaneously?
- Or sequentially?
- How does D know when to start?
```

### Petri Net Solution: Tokens and Places

```
        ◯ (token)
        │
      (place)
        │
        ▼
    [transition]
        │
        ├─→ (place) ─→ [transition]
        └─→ (place) ─→ [transition]
```

**Key insight:** Tokens flow through the graph; a transition fires when all inputs have tokens.

```
Flowchart problem solved:

    (B input) ◯
       │      ◯ (C input)
       └─[Join]─┘
          │
         ◯ (D input)
          │
       [D executes]
```

**Rules:**
1. Transition fires **only when all input places have tokens**
2. Firing **removes** input tokens, **creates** output tokens
3. **Concurrency is explicit**: parallel places contain separate tokens
4. **Synchronization is explicit**: join transitions require multiple input tokens

## Domain Model Mapping

### Petri Net → YAWL Elements

```
Petri Net Concept      →    YAWL Element
─────────────────────────────────────────
Place                  →    YCondition
Transition             →    YTask
Token                  →    Implicit (marked by enabled state)
Arc (Place→Transition) →    YFlow
Arc (Transition→Place) →    YFlow
Marking (token dist.)  →    YMarking
```

### Example: Order Processing

**Petri Net:**
```
        ◯ Input
        │
    [ReceiveOrder]
        │
        ├─→ ◯ Pending ────→ [ProcessPayment]
        │                        │
        └──┐                      ├─→ ◯ Paid ──────→ [Fulfill]
           │                      │                     │
           └─[CheckCredit]────────┼────→ ◯ Failed     ◯ Shipped
                                  │
                                  └─→ [Reject]
                                          │
                                          ▼
                                      ◯ Rejected
```

**YAWL Elements:**
```java
// Input place: input condition
YInputCondition input = new YInputCondition("Input");

// Transition: task
YTask receiveOrder = new YTask("ReceiveOrder", YTask._AND, YTask._AND);

// Arc: control flow
net.addFlow(new YFlow(input, receiveOrder));

// AND-split: outputs to multiple tasks
YTask checkCredit = new YTask("CheckCredit", YTask._AND, YTask._AND);
YTask processPayment = new YTask("ProcessPayment", YTask._AND, YTask._AND);

net.addFlow(new YFlow(receiveOrder, checkCredit));
net.addFlow(new YFlow(receiveOrder, processPayment));

// Places for intermediate synchronization
YCondition creditApproved = new YCondition("CreditApproved");
YCondition paymentDone = new YCondition("PaymentDone");

// AND-join: synchronize multiple tasks
YTask fulfill = new YTask("Fulfill", YTask._AND, YTask._AND);

net.addFlow(new YFlow(checkCredit, creditApproved));
net.addFlow(new YFlow(processPayment, paymentDone));
net.addFlow(new YFlow(creditApproved, fulfill));
net.addFlow(new YFlow(paymentDone, fulfill));
```

## Control Flow Patterns

### 1. Sequential (Sequence)
```
Task A → Task B → Task C
```

**When to use:** Steps that must happen in order, dependencies between steps

### 2. Parallel AND-Split
```
        ┌─→ Task A ─┐
Task Root           ├─→ Sync ─→ Task Final
        └─→ Task B ─┘
```

**When to use:** Independent work that must be done concurrently

### 3. Conditional XOR-Split
```
             ┌─→ [Approved Path]   ─┐
Task Check ──┤                       ├─→ Continue
             └─→ [Rejected Path]    ─┘
```

**When to use:** Decision point; exactly one path taken

### 4. Multi-Choice OR-Split
```
              ┌─→ [Email Notification]
Task Complete─┼─→ [SMS Alert]
              └─→ [Push Notification]
```

**When to use:** Multiple independent actions, some or all executed

### 5. OR-Join (Non-Local Synchronization)
```
Task A ──┐
         ├─→ [OR-Join] ──→ Continue
Task B ──┘

Semantics:
- Continue when A OR B completes
- But NOT if A has completed and B could still happen
```

**When to use:** Approval workflows where optional reviewers exist

## Multi-Instance Semantics

### Static Multi-Instance

**Pattern:** Fixed number of repetitions

```
Input: [1, 2, 3, 4, 5]
        ↓
    Task (MI)
    ├─ Instance 1: Process(1) → output 1'
    ├─ Instance 2: Process(2) → output 2'
    ├─ Instance 3: Process(3) → output 3'
    ├─ Instance 4: Process(4) → output 4'
    └─ Instance 5: Process(5) → output 5'
        ↓
Output: [1', 2', 3', 4', 5']
```

### Dynamic Multi-Instance

**Pattern:** Number determined at runtime

```java
YMultiInstanceAttributes mi = new YMultiInstanceAttributes();
mi.setCreationMode(YMultiInstanceAttributes.CREATION_MODE_DYNAMIC);
```

**Use case:** "Process all items in queue" (count unknown at start)

## Decomposition Hierarchy

### Single-Level Net (Flat)

```
┌─────────────────────────┐
│     Main Net            │
│  ┌─────┐  ┌─────┐     │
│  │Task1│→ │Task2│     │
│  └─────┘  └─────┘     │
└─────────────────────────┘
```

### Hierarchical Nets (Decomposition)

```
┌─────────────────────────────┐
│      Main Net               │
│  ┌──────────────┐           │
│  │ CompositeTask│           │
│  │ (Decomposed) │           │
│  └──────────────┘           │
└─────────────────────────────┘
         │
         ├─ DecompositionA
         │  ┌─────────────────┐
         │  │   SubNet 1      │
         │  │ ┌─────┐┌─────┐ │
         │  │ │SubT1││SubT2│ │
         │  │ └─────┘└─────┘ │
         │  └─────────────────┘
         │
         └─ DecompositionB
            ┌─────────────────┐
            │   SubNet 2      │
            │ ┌───┐┌───┐┌───┐ │
            │ │T1 ││T2 ││T3 │ │
            │ └───┘└───┘└───┘ │
            └─────────────────┘
```

**Benefits:**
1. **Abstraction** — Hide complexity behind single task node
2. **Reuse** — Same subnet can be decomposed multiple ways
3. **Modularity** — Design workflows independently
4. **Scalability** — Manage large workflows as collections of smaller ones

## Soundness Property

**Definition:** A workflow net is **sound** if:

1. **Proper completion** — Every case eventually terminates
2. **No deadlock** — Case cannot get stuck indefinitely
3. **No livelock** — Case cannot loop infinitely without progress
4. **Proper termination** — Output condition reached, all tokens consumed

**Example: Unsound workflow**

```
        ┌─[TaskA]─┐
Input ──┤          ├─→ [OR-Join] ──→ Output
        └─[TaskB]─┘

Problem: If TaskA completes first, OR-join fires immediately
But TaskB might never be reached or completed
→ Case completes with token still in TaskB (improper termination)
```

**Fix:**

```
        ┌─[TaskA]─┐
Input ──┤          ├─→ [AND-Join] ──→ Output
        └─[TaskB]─┘

Now both must complete before continuing (sound)
```

## Data Flow vs Control Flow

### Control Flow
**Defines:** Which tasks execute and in what order

```
Task A → Task B → Task C
```

### Data Flow
**Defines:** What information flows through tasks

```java
task.setDataInput(requestSchema);
task.setDataOutput(approvalSchema);
```

### Complete Specification

```
YTask approveTask = new YTask("Approve", ...);

// What data does it receive?
approveTask.setDataInput(XML schema for application);

// What data does it produce?
approveTask.setDataOutput(XML schema for approval decision);

// Where does it appear in control flow?
net.addFlow(submitTask, approveTask);
net.addFlow(approveTask, notifyTask);
```

## Why This Design Matters

### 1. Executability
Petri net semantics are formally defined. No ambiguity.

### 2. Analyzability
We can verify properties before execution:
- Will case always terminate?
- Can this task be reached?
- Is there a deadlock?

### 3. Testability
Formal model enables comprehensive test generation.

### 4. Interoperability
YAWL specs can be transformed to/from BPMN (both based on Petri nets)

## Comparison to BPMN

| Aspect | YAWL | BPMN |
|--------|------|------|
| Foundation | Formal Petri nets | Informal notation |
| OR-Join | Native (non-local sync) | Simulated (awkward) |
| Multi-Instance | First-class | Workaround (loops) |
| Soundness | Definable, verifiable | Informally defined |
| Executable | Yes, direct | Via translation |

---

**See also:**
- [Petri Net Foundations](./petri-net-foundations.md)
- [Workflow Patterns](../reference/workflow-patterns.md)
- [Elements Getting Started](../tutorials/yawl-elements-getting-started.md)
