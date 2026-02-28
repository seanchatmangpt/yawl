# YAWL Worklet Architecture

Understanding Ripple Down Rules, adaptive workflows, and runtime rule evaluation.

---

## Problem: Static Workflows

Traditional workflow engines hardcode all variations:

```xml
<!-- Hardcoded Invoice Processing Workflow -->
<task id="ReviewAndApprove">
  <condition>
    <if invoiceAmount > 10000>
      <then> delegate to Executive </then>
    </if>
    <if invoiceAmount > 1000>
      <then> delegate to Manager </then>
    </if>
    <else> Auto-approve </else>
  </condition>
</task>
```

**Problems:**
1. Changes require workflow redevelopment
2. All variations must be known upfront
3. Hard to maintain (spaghetti logic)
4. Business rules buried in code

---

## Solution: Worklets + RDR

Instead of hardcoding conditions, **select worklets at runtime** based on rules:

```
Task: ReviewAndApprove
  │
  ├─ RDR Tree (rule set)
  │  ├─ Rule 1: amount > 10000 → ExecutiveApprovalWorklet
  │  ├─ Rule 2: amount > 1000 → ManagerApprovalWorklet
  │  └─ Rule 3: default → AutoApprovalWorklet
  │
  └─ When enabled:
     1. Evaluate RDR rules against work item context
     2. Select appropriate worklet (e.g., ManagerApprovalWorklet)
     3. Launch worklet as subcase
     4. Wait for completion
     5. Continue parent case
```

**Benefits:**
- Change rules without redeploying workflow
- Business analysts can define rules
- Clear separation of concerns
- Easy A/B testing

---

## Ripple Down Rules Algorithm

### RDR Tree Structure

```
          Root Node (Condition 1)
          /                    \
       TRUE                    FALSE
       /                         \
   Conclusion A            Child Node (Condition 2)
   (Remember A)           /               \
                       TRUE              FALSE
                       /                 \
                   Conclusion B      Child Node (Condition 3)
                   (Forget A → B)    /        \
                                  TRUE      FALSE
                                  /          \
                           Conclusion C   Conclusion D
                           (Forget B→C)  (Keep D)
```

### Evaluation Algorithm

```
1. Start at root node
2. Evaluate condition
3. If TRUE: Move to true-child, remember conclusion
4. If FALSE: Move to false-child
5. If no children: Stop, use last conclusion
6. Repeat until leaf node reached
7. Return final conclusion
```

### Example: Invoice Amount = $5000

```
Step 1: Root (amount > 10000?)
  5000 > 10000 = FALSE
  → Move to false-child
  → Remember: nothing yet

Step 2: Child (amount > 1000?)
  5000 > 1000 = TRUE
  → Move to true-child
  → Remember: ManagerApprovalWorklet

Step 3: No more children
  → Return: ManagerApprovalWorklet
```

### Example: Invoice Amount = $500

```
Step 1: Root (amount > 10000?)
  500 > 10000 = FALSE
  → Move to false-child

Step 2: Child (amount > 1000?)
  500 > 1000 = FALSE
  → Move to false-child
  → No children
  → Return: AutoApprovalWorklet
```

---

## Worklet Execution Flow

```
Parent Workflow
│
├─ Task enabled: ReviewAndApprove
│  ├─ RDR evaluation
│  │  └─ Selected: ManagerApprovalWorklet
│  │
│  └─ Launch worklet subcase
│     ├─ Subcase created
│     │  └─ Worklet specification: ManagerApprovalWorklet
│     ├─ Worklet executes
│     │  ├─ Participant assigned
│     │  ├─ Work item created
│     │  └─ Participant completes work
│     └─ Subcase completes
│        ├─ Output data flows back
│        └─ Parent task marked complete
│
└─ Continue with next task
```

---

## Condition Language Options

### Simple (Comparisons Only)

```
amount > 10000
customerType == "VIP"
country NOT IN ("US", "EU")
```

**Pros:** Fast, secure, simple
**Cons:** Limited expressiveness

### SpEL (Spring Expression Language)

```
amount > 10000 && customer.status == 'active'
amount > 10000 || country == 'US' && urgency == 'high'
getCustomerRiskScore(customerId) > 0.8
```

**Pros:** Powerful, can call methods
**Cons:** Needs sandboxing (security risk)

### Groovy (Full Programming Language)

```groovy
amount > 10000 && {
  customer.lastOrderDate.plusDays(30).isAfter(now)
}.call()
```

**Pros:** Maximum flexibility
**Cons:** Performance, security risk

---

## Tree Management

### Add Rules at Runtime

```bash
# Add new rule to existing tree
POST /api/v1/worklets/rules/ReviewAndApprove/add
{
  "parentNodeId": 2,
  "position": "true-child",  // Or false-child
  "newRule": {
    "condition": "customerType == \"GoldPartner\"",
    "conclusion": "GoldPartnerApprovalWorklet"
  }
}
```

Workflow continues using new rules immediately (for new cases).

### Version Control

```
Version 1 (2026-02-01):
  ├─ amount > 10000 → ExecutiveApprovalWorklet
  └─ amount > 1000 → ManagerApprovalWorklet

Version 2 (2026-02-15):  // Changed threshold
  ├─ amount > 50000 → ExecutiveApprovalWorklet  // Changed
  └─ amount > 1000 → ManagerApprovalWorklet

Rollback to V1 if V2 causes issues
```

---

## Exception Handling

Worklets can handle exceptions from the parent task:

### Exception Types

```
TIMEOUT
  ├─ Parent task took too long
  ├─ Trigger: TimeoutHandlerWorklet
  └─ Action: Escalate or retry

CONSTRAINT_VIOLATION
  ├─ Invalid data or state
  ├─ Trigger: ConstraintHandlerWorklet
  └─ Action: Fix and retry

MANUAL_SUSPENSION
  ├─ User suspended task
  ├─ Trigger: SuspensionHandlerWorklet
  └─ Action: Handle suspension reason

MAX_REASSIGNMENTS_EXCEEDED
  ├─ Task reassigned too many times
  ├─ Trigger: EscalationWorklet
  └─ Action: Escalate to manager
```

---

## Performance Considerations

### RDR Evaluation Time

Typical evaluation times:
- **Simple rule** (amount > 10000): ~1 ms
- **5-level tree**: ~5 ms
- **20-level tree** (deep nesting): ~20 ms

**Best practice**: Keep tree depth < 10 levels.

### Caching

```
First evaluation:
  Load RDR tree from DB → evaluate → cache

Subsequent evaluations:
  Use cached tree → evaluate → return
  (no DB roundtrip)

Cache invalidation:
  Rule changes → invalidate cache
  TTL expires → reload from DB
```

Cache default TTL: 30 minutes

### Virtual Threads

```
Each worklet selection runs on virtual thread:
  RDR evaluation → Launch worklet → Wait for completion
  All on same virtual thread (minimal overhead)
```

Scales to thousands of concurrent worklets.

---

## Decision: RDR Over Decision Tables

### RDR Advantages

```
Ripple Down Rules:
  ├─ Incremental learning (add rules one at a time)
  ├─ Explicit contradiction handling
  ├─ Captures domain expert reasoning process
  └─ Natural for "exception-driven" domains
```

### DMN Decision Tables

```
DMN (Decision Model & Notation):
  ├─ Matrix-based (easier to visualize)
  ├─ Declarative (no order dependency)
  ├─ BPMN integration
  └─ Standard (OASIS)
```

**Decision**: YAWL uses RDR for workflows because:
1. Easier to maintain incrementally
2. Handles "exception rules" naturally
3. Proven in production
4. Not every rule is a matrix

---

## Adaptive Workflow Benefits

### Before: Static Workflow

```
Deployed spec: v1.0
Rules hardcoded: amount > 10000
Issues found: Threshold too high
Action: Rewrite workflow → redeploy → restart cases
Time: 2+ hours
Risk: High (might break existing logic)
```

### After: Worklet-Based

```
Deployed spec: v1.0 (rules separate)
Current rule: amount > 10000
Issues found: Threshold too high
Action: Update rule: amount > 50000
Time: 1 minute (edit rule)
Risk: Low (only affects new cases; easy rollback)
```

---

## Related Architecture

- **[Authentication](yawl-authentication-architecture.md)** — Context for RDR conditions
- **[Scheduling](yawl-scheduling-architecture.md)** — Schedule worklet execution
- **[Monitoring](yawl-monitoring-architecture.md)** — Trace RDR evaluation
- **[Worklets](yawl-worklet-architecture.md)** ← (you are here)

---

## See Also

- [How-To: Implement Worklet Service (Advanced)](../how-to/implement-worklet-service-advanced.md)
- [Reference: Configuration Options](../reference/yawl-worklet-config.md)
- [Tutorial: Getting Started](../tutorials/yawl-worklet-getting-started.md)
