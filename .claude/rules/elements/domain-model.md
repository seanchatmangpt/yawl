---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/elements/**"
  - "*/src/test/java/org/yawlfoundation/yawl/elements/**"
---

# Elements (Domain Model) Rules

## Core Types
- `YSpecification` — Top-level workflow definition (contains nets, decompositions)
- `YNet` — A Petri net within a specification (contains tasks, conditions, flows)
- `YTask` — A transition in the Petri net (atomic or composite)
- `YCondition` — A place in the Petri net (holds tokens)
- `YFlow` — Directed edge connecting tasks and conditions
- `YDecomposition` — Reusable task definition (web service, manual, code)

## Petri Net Semantics
- Tokens flow through conditions (places) via tasks (transitions)
- Split/join types: AND, OR, XOR — each has precise firing rules
- OR-join has non-local semantics (requires global state check)
- Cancellation regions must be explicitly declared in the specification
- Multiple instances: static (fixed count), dynamic (runtime-determined)

## Sealed Hierarchies
- Use sealed classes for element type hierarchies
- Pattern matching must be exhaustive on sealed types (no default case)
- `YElement` is the base — all elements extend it

## Immutability
- Specification elements should be immutable after loading
- Use records for data transfer between elements and engine
- Deep-copy any mutable state before returning from getters

## Data Handling
- XML Schema for type definitions
- XPath for data extraction
- XQuery for data transformation
- All data operations validate against the specification's schema
