---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/worklet/**"
  - "*/src/test/java/org/yawlfoundation/yawl/worklet/**"
---

# Worklet Service Rules

## Purpose
Worklets enable dynamic workflow: runtime selection of sub-workflows based on case data and rules. This is YAWL's unique approach to adaptive process management.

## Architecture
- `WorkletService` — Main service coordinating worklet selection and execution
- `RdrTree` — Ripple-Down Rules tree for case-based reasoning
- `RdrNode` — Individual rule node (condition → conclusion pairs)
- Rules are hierarchical: cornerstone cases with exception refinements

## Rule Evaluation
- Rules evaluated top-down through RDR tree
- Each node: condition (XPath expression) → conclusion (worklet specification URI)
- Cornerstone cases anchor rule branches
- New exceptions add refinement nodes without modifying existing rules

## Integration Points
- Receives work items via Interface X (exception handling)
- Launches sub-cases via Interface B
- Reports completion back to parent case
- Rules stored in persistent rule sets (XML format)
