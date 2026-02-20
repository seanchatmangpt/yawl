# ADR-020: Workflow Pattern Library Architecture

## Status
**ACCEPTED**

## Context

YAWL is grounded in the formal Workflow Patterns framework (van der Aalst et al.),
which catalogues 89 workflow patterns across six dimensions: control flow, data,
resource, exception, service interaction, and cancellation. YAWL's Petri net engine
supports all 89 patterns, but:

1. **No pattern library for practitioners**: users must know Petri net theory to
   design complex flows. Patterns are not available as reusable specification templates.
2. **No searchable registry**: there is no way to discover which patterns are relevant
   to a given use case (e.g., "how do I model a parallel approval with timeout?").
3. **No pattern documentation in-engine**: the engine can execute any pattern but
   provides no guidance to designers building specifications.
4. **Enterprise patterns undocumented**: common real-world patterns (approval workflows,
   escalation chains, SLA enforcement) are not captured in reusable form.

For v6.0.0, a pattern library addresses this gap by:
- Providing 20 documented, executable specification templates
- Making patterns searchable by name, category, and use-case keyword
- Enabling pattern composition (combine two patterns into a new specification)

## Decision

**YAWL v6.0.0 introduces a Pattern Library: a versioned repository of documented,
executable workflow pattern templates with a searchable registry.**

### Pattern Library Structure

```
docs/patterns/
  registry.json          ← machine-readable index of all patterns
  README.md              ← pattern library overview

  control-flow/
    WCP-01-sequence/
      README.md          ← pattern documentation
      template.yawl      ← executable YAWL specification template
      example.yawl       ← real-world example (e.g., loan approval sequence)
      test-case.json     ← test scenario for automated validation
    WCP-02-parallel-split/
    WCP-03-synchronisation/
    WCP-04-exclusive-choice/
    WCP-05-simple-merge/
    WCP-06-multi-choice/
    WCP-07-structured-synchronisation/
    WCP-21-critical-section/
    WCP-38-cancelling-task/

  enterprise/
    approval-workflow/
    parallel-approval/
    escalation-chain/
    sla-timer-enforcement/
    conditional-routing/
    compensating-transaction/
    looping-review/
    multi-instance-review/

  agent-patterns/
    agent-assisted-task/
    llm-decision-point/
    human-agent-handoff/
```

### Pattern Documentation Template

Each pattern directory contains a `README.md` with standardised sections:

```markdown
# WCP-04: Exclusive Choice (XOR Split)

## Pattern ID
WCP-04

## Category
Control Flow — Branching

## Intent
Route a case to exactly one of N alternative paths based on a condition
evaluated against case data.

## Petri Net Semantics
A place with N output transitions, exactly one of which fires based on
the guard expression. Remaining transitions have inhibitor arcs.

## YAWL Specification

### Key Elements
- Task with XOR-split join semantics
- Guard conditions on each outgoing flow
- No synchronisation required at convergence (simple merge)

### Template
[link to template.yawl]

## Example: Loan Approval Routing

A loan application is routed to:
- Fast-track approval if amount < $10,000 AND credit_score > 750
- Standard review if amount in [$10,000, $100,000]
- Committee review if amount > $100,000

[link to example.yawl]

## Guard Condition Syntax (JEXL)

```xml
<condition>
  <predicate>
    /data/loanAmount &lt; 10000 and /data/creditScore &gt; 750
  </predicate>
</condition>
```

## Common Pitfalls
1. Non-exhaustive guards: if no guard evaluates to true, the case deadlocks
2. Overlapping guards: if multiple guards are true, only the first matching
   transition fires (engine-defined ordering)

## Related Patterns
- WCP-05: Simple Merge (convergence after exclusive choice)
- WCP-06: Multi-Choice (route to multiple paths)
- WCP-16: Deferred Choice (choice made at runtime by resource)

## Enterprise Use Cases
- Document routing (approval type selection)
- Exception handling (severity-based routing)
- SLA violation routing (breach → escalation)

## Workflow Pattern Reference
van der Aalst, W.M.P., et al. (2003). Workflow Patterns. LNCS 2626.
https://www.workflowpatterns.com/patterns/control/basic/wcp4.php
```

### Pattern Registry (`registry.json`)

```json
{
  "version": "6.0.0",
  "patterns": [
    {
      "id": "WCP-04",
      "name": "Exclusive Choice",
      "aliases": ["XOR Split", "Conditional Routing", "If-Else"],
      "category": "control-flow",
      "subcategory": "branching",
      "path": "control-flow/WCP-04-exclusive-choice",
      "tags": ["routing", "conditional", "branching", "xor"],
      "useCases": ["document routing", "exception routing", "approval routing"],
      "complexity": "BASIC",
      "petriNetElements": ["XOR_SPLIT"],
      "enterpriseExamples": ["approval-workflow", "conditional-routing"],
      "since": "6.0.0"
    },
    {
      "id": "ENT-APPROVAL",
      "name": "Parallel Approval Workflow",
      "aliases": ["Multi-Approver", "Concurrent Review"],
      "category": "enterprise",
      "subcategory": "approval",
      "path": "enterprise/parallel-approval",
      "tags": ["approval", "parallel", "multi-user", "and-split"],
      "useCases": ["purchase orders", "contract review", "leave requests"],
      "complexity": "INTERMEDIATE",
      "petriNetElements": ["AND_SPLIT", "AND_JOIN"],
      "composedFrom": ["WCP-02", "WCP-03"],
      "since": "6.0.0"
    }
  ]
}
```

### Pattern Search API

The pattern registry is exposed via the engine admin API:

```
GET /admin/patterns?q=approval&category=enterprise&complexity=BASIC
GET /admin/patterns/{patternId}
GET /admin/patterns/{patternId}/template   (downloads template.yawl)
```

### Pattern Composition

Patterns can be declared as compositions of other patterns in `registry.json`:

```json
{
  "id": "ENT-PARALLEL-APPROVAL",
  "composedFrom": ["WCP-02", "WCP-03", "ENT-TIMEOUT-ESCALATION"]
}
```

The Pattern Library documentation page for composed patterns references all
component patterns, making the relationship explicit.

## Consequences

### Positive

1. Practitioners can bootstrap workflow designs from proven templates
2. Pattern registry enables IDE and designer tooling integration (pattern picker)
3. Enterprise patterns capture institutional knowledge (approval, escalation) in
   reusable, version-controlled form
4. Test cases for each pattern validate template correctness on every build

### Negative

1. Pattern documentation is a maintenance commitment — patterns must be updated
   when the schema changes (ADR-013)
2. Practitioners may misapply patterns to inappropriate use cases without understanding
   the underlying Petri net semantics
3. Template `.yawl` files must be validated against current schema on every build

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Template drift (schema evolves, templates not updated) | MEDIUM | MEDIUM | CI validates all templates against current schema |
| Pattern naming conflicts with existing YAWL terminology | LOW | LOW | Prefix enterprise patterns with `ENT-`, WC patterns with `WCP-` |
| Registry API becomes stale | LOW | LOW | `registry.json` is regenerated from pattern directories during build |

## Alternatives Considered

### BPMN Pattern Import
BPMN 2.0 has its own pattern vocabulary. Providing BPMN-to-YAWL pattern mappings was
considered. This is valuable documentation but is deferred to a separate companion guide
rather than being part of the core pattern library.

### Visual Pattern Designer
A graphical pattern browser integrated into a web UI was considered. Deferred to v6.2.
The file-based pattern library with REST search API provides the data foundation that
a visual designer could build on.

## Related ADRs

- ADR-022: OpenAPI-First Design (pattern search API described in openapi-v6.yaml)
- ADR-013: Schema Versioning (pattern templates must be updated for schema changes)
- ADR-019: Autonomous Agent Framework (agent-patterns subcategory)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** IN PROGRESS (v6.0.0)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version
