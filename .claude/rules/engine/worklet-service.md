---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/worklet/**"
  - "*/src/test/java/org/yawlfoundation/yawl/worklet/**"
---

# Worklet Service Rules

## Purpose
Worklets enable dynamic workflow: runtime selection of sub-workflows based on case data and rules.
YAWL's unique approach to adaptive process management — substitute tasks at runtime without
modifying the original specification.

## Core Classes
- `WorkletService` — Coordinates worklet selection, sub-case launch, and completion callbacks
- `RdrTree` — Binary decision tree; traversal returns the last satisfied conclusion (spec URI)
- `RdrNode` — One rule: `condition (XPath/boolean) → conclusion (worklet spec URI)`; has trueChild + falseChild
- `RdrSet` — Map of `taskName → RdrTree`; each task gets its own independent rule set
- `WorkletRecord` — Runtime state: specId, parentCaseId, taskId, selectedAt (→ record type)
- `RdrCondition` — Evaluates XPath boolean expression against work item data map

## RDR Algorithm (Preserved Invariant)
```
1. Start at root node
2. Evaluate condition against work item context (XPath over case data variables)
3. If condition true  → traverse to trueChild  (more specific exception refinement)
4. If condition false → traverse to falseChild (sibling alternative)
5. Remember conclusion of last satisfied node
6. Return conclusion (spec URI) or null when no rule matched
```
- Non-monotonic knowledge: new exceptions always add child nodes, never modify existing ones
- `null` conclusion = no rule matched → fall through to default engine handling (not an error)
- Traversal is deterministic and side-effect-free

## Interface Integration
- **Receives** work items via Interface X (`InterfaceX_EnvBasedClient`) — exception channel
- **Launches** worklet sub-cases via Interface B (`InterfaceB_EnvironmentBasedClient`)
- **Completion** reported back via Interface B check-in on the suspended parent work item
- Rule sets stored as XML; loaded at startup, support hot-reload on file change

## Error Handling Requirements
```java
// CORRECT — null conclusion is valid; handle it
String workletUri = rdrTree.evaluate(caseData);
if (workletUri == null) {
    return DefaultHandling.COMPLETE_NORMALLY;
}
workletService.launchSubCase(workletUri, parentWorkItemId);

// VIOLATION — silent fallback on evaluation error
try {
    return rdrTree.evaluate(caseData);
} catch (XPathException e) {
    return null;  // hides the failure
}

// CORRECT — propagate evaluation failures
try {
    return rdrTree.evaluate(caseData);
} catch (XPathException e) {
    throw new WorkletSelectionException(
        "RDR evaluation failed for task " + taskId, e);
}
```

## Data and Immutability
- `WorkletRecord` → use Java record (immutable, auto equals/hashCode)
- `RdrNode` fields are set at tree construction time; effectively final after load
- Deep-copy rule sets before adding/removing nodes (tree is mutable during knowledge acquisition)
- `RdrCondition` results must be deterministic for the same input (no side effects)

## Testing Patterns
- Construct real `RdrTree` instances with synthetic case data (`Map<String, Object>`)
- Cover: empty tree, root-only, single branch, multi-level, no-match path
- Verify non-monotonicity: adding a child node does not change evaluation of existing paths
- Never mock `RdrNode` or `RdrTree` — these are domain model, not infrastructure
