# YAWL v6.0.0 Workflow Pattern Library

The YAWL Pattern Library is a curated collection of documented, executable workflow
templates grounded in the formal Workflow Patterns framework (van der Aalst et al.)
and extended with enterprise-grade real-world patterns.

## Pattern Categories

| Category | Count | Description |
|----------|-------|-------------|
| [Control Flow](#control-flow-patterns) | 9 | Petri net control flow patterns (WCP-01 through WCP-38) |
| [Enterprise](#enterprise-patterns) | 8 | Real-world business workflow templates |
| [Agent Patterns](#agent-patterns) | 3 | AI and autonomous agent integration patterns |

**Total: 20 patterns**

---

## Control Flow Patterns

These patterns correspond directly to the van der Aalst Workflow Control Flow Patterns (WCP).

| ID | Name | Complexity | Petri Net Elements |
|----|------|-----------|-------------------|
| [WCP-01](control-flow/WCP-01-sequence/README.md) | Sequence | BASIC | SEQUENCE |
| [WCP-02](control-flow/WCP-02-parallel-split/README.md) | Parallel Split | BASIC | AND_SPLIT |
| [WCP-03](control-flow/WCP-03-synchronisation/README.md) | Synchronisation | BASIC | AND_JOIN |
| [WCP-04](control-flow/WCP-04-exclusive-choice/README.md) | Exclusive Choice | BASIC | XOR_SPLIT |
| [WCP-05](control-flow/WCP-05-simple-merge/README.md) | Simple Merge | BASIC | XOR_JOIN |
| [WCP-06](control-flow/WCP-06-multi-choice/README.md) | Multi-Choice | INTERMEDIATE | OR_SPLIT |
| [WCP-07](control-flow/WCP-07-structured-sync-merge/README.md) | Structured Synchronising Merge | INTERMEDIATE | OR_JOIN |
| [WCP-21](control-flow/WCP-21-critical-section/README.md) | Critical Section | ADVANCED | CRITICAL_SECTION |
| [WCP-38](control-flow/WCP-38-cancelling-task/README.md) | Cancelling Task | INTERMEDIATE | CANCELLATION_SET |

---

## Enterprise Patterns

Pre-built templates for common business workflow scenarios.

| ID | Name | Complexity | Typical Use Case |
|----|------|-----------|-----------------|
| [ENT-APPROVAL](enterprise/approval-workflow/README.md) | Single Approver Workflow | BASIC | Expense approval, leave requests |
| [ENT-PARALLEL-APPROVAL](enterprise/parallel-approval/README.md) | Parallel Multi-Approver | INTERMEDIATE | Purchase orders, contract review |
| [ENT-CONDITIONAL-ROUTING](enterprise/conditional-routing/README.md) | Conditional Routing | INTERMEDIATE | Tiered approvals, ticket routing |
| [ENT-ESCALATION](enterprise/escalation-chain/README.md) | Escalation Chain | INTERMEDIATE | SLA-bound escalations |
| [ENT-SLA-ENFORCEMENT](enterprise/sla-timer-enforcement/README.md) | SLA Timer Enforcement | INTERMEDIATE | Regulatory deadlines |
| [ENT-COMPENSATION](enterprise/compensating-transaction/README.md) | Compensating Transaction | ADVANCED | Saga / multi-system rollback |
| [ENT-LOOPING-REVIEW](enterprise/looping-review/README.md) | Looping Review Cycle | INTERMEDIATE | Document revision cycles |
| [ENT-MULTI-INSTANCE](enterprise/multi-instance-review/README.md) | Multi-Instance Review | ADVANCED | Batch item processing |

---

## Agent Patterns

Patterns for integrating autonomous agents and LLMs into YAWL workflows.
These patterns require YAWL Schema 6.0 and the `<agentBinding>` element.

| ID | Name | Complexity | Typical Use Case |
|----|------|-----------|-----------------|
| [AGT-AGENT-ASSISTED](agent-patterns/agent-assisted-task/README.md) | Agent-Assisted Task | INTERMEDIATE | AI draft + human review |
| [AGT-LLM-DECISION](agent-patterns/llm-decision-point/README.md) | LLM Decision Point | ADVANCED | AI-driven routing |
| [AGT-HUMAN-AGENT-HANDOFF](agent-patterns/human-agent-handoff/README.md) | Human-Agent Handoff | INTERMEDIATE | Agent failure fallback |

---

## Using Patterns

### Browse by Use Case

**Approval workflows:**
- Start with [ENT-APPROVAL](enterprise/approval-workflow/README.md) for single approver
- Use [ENT-PARALLEL-APPROVAL](enterprise/parallel-approval/README.md) for multiple approvers
- Add [ENT-ESCALATION](enterprise/escalation-chain/README.md) for deadline-driven escalation

**Data routing workflows:**
- Use [WCP-04 Exclusive Choice](control-flow/WCP-04-exclusive-choice/README.md) for if/else routing
- Use [WCP-06 Multi-Choice](control-flow/WCP-06-multi-choice/README.md) for route-to-multiple
- Use [ENT-CONDITIONAL-ROUTING](enterprise/conditional-routing/README.md) for tiered routing

**AI-augmented workflows:**
- Start with [AGT-AGENT-ASSISTED](agent-patterns/agent-assisted-task/README.md)
- Use [AGT-LLM-DECISION](agent-patterns/llm-decision-point/README.md) for AI-driven routing
- Add [AGT-HUMAN-AGENT-HANDOFF](agent-patterns/human-agent-handoff/README.md) for fallback

**Long-running workflows with SLAs:**
- Use [ENT-SLA-ENFORCEMENT](enterprise/sla-timer-enforcement/README.md) for hard deadlines
- Use [ENT-LOOPING-REVIEW](enterprise/looping-review/README.md) for iterative review
- Use [ENT-COMPENSATION](enterprise/compensating-transaction/README.md) for rollback

### Download a Template

Templates are available via the engine admin API (requires `engine-admin` role):

```bash
# Get all patterns
curl -s "http://localhost:8080/yawl/api/admin/patterns" \
  -H "sessionHandle: $SESSION"

# Search by tag
curl -s "http://localhost:8080/yawl/api/admin/patterns?q=approval&category=enterprise"

# Download a template specification
curl -s "http://localhost:8080/yawl/api/admin/patterns/ENT-PARALLEL-APPROVAL/template" \
  -o parallel-approval-template.yawl
```

### Complexity Guide

| Level | Description | Suitable For |
|-------|-------------|-------------|
| BASIC | Single join/split type, no timers | First YAWL specification |
| INTERMEDIATE | Multiple join types, timers, data routing | Standard enterprise workflows |
| ADVANCED | Multi-instance, critical sections, compensation | Complex orchestration |

---

## Pattern Documentation Standard

Each pattern directory follows this structure:

```
pattern-name/
  README.md          — documentation (this format)
  template.yawl      — parameterisable YAWL specification
  example.yawl       — concrete real-world example
  test-case.json     — automated test scenario
```

For the documentation standard used in each `README.md`, see
[PATTERN-TEMPLATE.md](PATTERN-TEMPLATE.md).

---

## Contributing Patterns

New patterns must:
1. Demonstrate a distinct workflow concern not covered by existing patterns
2. Include a complete `template.yawl` that validates against `YAWL_Schema6.0.xsd`
3. Include at least one `example.yawl` and a `test-case.json`
4. Be added to `registry.json` with all required fields
5. Pass `mvn test -Dtest=PatternLibraryTest` before merging

---

## References

- **Workflow Patterns Website**: https://www.workflowpatterns.com/
- **van der Aalst et al. (2003)**: Workflow Patterns. LNCS 2626.
- **YAWL Book (2009)**: Modern Business Process Automation. Springer.
- **YAWL Schema 6.0**: [schema/YAWL_Schema6.0.xsd](../../schema/)

---

**Library Version**: 6.0.0
**Last Updated**: 2026-02-17
**Maintained by**: YAWL Architecture Team
