# ENT-PARALLEL-APPROVAL: Parallel Multi-Approver Workflow

## Pattern ID
`ENT-PARALLEL-APPROVAL`

## Category
Enterprise — Approval

## Complexity
INTERMEDIATE

## Intent

Route a request to multiple approvers simultaneously. The workflow advances only when
all approvers have completed their review. Any rejection can optionally short-circuit
remaining approvals.

## Petri Net Semantics

An AND-split fires tokens to all approver lanes simultaneously. An AND-join waits for
all tokens to arrive before continuing. If any approver rejects, an XOR-split routes
to an early-termination path that cancels remaining approver tasks (WCP-38).

```
              ┌──────────────────────────────┐
              │        Submit Request         │
              └──────────┬───────────────────┘
                         │  AND-Split
              ┌──────────┼──────────┐
              ▼          ▼          ▼
          [Approver1] [Approver2] [Approver3]
              │          │          │
              └──────────┼──────────┘
                         │  AND-Join (all approved)
                         │
              ┌──────────▼───────────────────┐
              │         ProcessApproved       │
              └──────────────────────────────┘
```

When any approver rejects:
```
[ApproverN: REJECTED]
    │  XOR-Split
    ├──→ CancelOtherApprovers  (cancellation set)
    └──→ NotifyRejection
```

## YAWL Specification Elements

### Key Configuration

| Element | Value | Description |
|---------|-------|-------------|
| Fork task split type | `AND` | Activates all approver tasks in parallel |
| Join task join type | `AND` | Waits for all approver completions |
| Rejection handling | `CANCEL` | WCP-38: cancels remaining approvers on first rejection |
| Approver tasks | Atomic, human-resourced | Each has individual data scope |

### Data Binding Pattern

The parent net passes the request data to each approver task via input data binding.
Each approver task outputs a `decision` (APPROVED/REJECTED) and optional `comments`.
The AND-join aggregates all decisions before advancing.

```xml
<!-- Input binding for each approver task -->
<inputBinding>
  <expression query="/data/requestAmount"/>
  <mapsTo>requestAmount</mapsTo>
</inputBinding>
<inputBinding>
  <expression query="/data/vendor"/>
  <mapsTo>vendor</mapsTo>
</inputBinding>

<!-- Output binding: collect approver decision -->
<outputBinding>
  <expression query="/approverDecision/decision"/>
  <mapsTo>approver1Decision</mapsTo>
</outputBinding>
```

## Template File

[template.yawl](template.yawl) — Parameterisable template with 3 approver slots.

Template parameters (customise via specification variables):
- `NUM_APPROVERS`: number of parallel approver lanes (2-8)
- `REQUIRE_ALL`: true = all must approve; false = majority rule
- `ON_REJECTION`: `CANCEL_OTHERS` | `ALLOW_COMPLETION` | `IMMEDIATE_REJECTION`

## Example: Purchase Order Approval

[example.yawl](example.yawl) — Procurement process requiring Finance Manager + Department Head + Legal approval for orders over $50,000.

### Example Data Flow

**Launch case:**
```json
{
  "specIdentifier": "PurchaseOrderApproval",
  "caseData": "<data><orderId>PO-2026-042</orderId><amount>75000.00</amount><vendor>Acme Corp</vendor><requester>eng-team</requester></data>"
}
```

**Case launches 3 work items simultaneously:**
```
ITEM-001: FinanceApproval    (assigned to finance-manager group)
ITEM-002: DeptHeadApproval   (assigned to dept-head-eng group)
ITEM-003: LegalApproval      (assigned to legal-team group)
```

**All approve:**
```
→ ProcessApprovalNotify work item enabled
→ Vendor notified, PO issued
```

**Legal rejects:**
```
→ ITEM-001 (FinanceApproval) cancelled  [still pending]
→ ITEM-002 (DeptHeadApproval) cancelled [already complete — no effect]
→ RejectNotify work item enabled
→ Requester notified of rejection with legal's reason
```

## Guard Condition Examples

### Route based on approval count (majority rule variant)

```xml
<!-- In the AND-join task's XOR-split successor -->
<condition>
  <predicate>
    number(/data/approvedCount) &gt;= number(/data/requiredCount)
  </predicate>
</condition>
```

### Amount-based approver selection

```xml
<!-- Only activate legal review if amount > $50,000 -->
<condition>
  <predicate>
    number(/data/amount) &gt; 50000
  </predicate>
</condition>
```

## Common Pitfalls

### 1. Deadlock on AND-Join with Cancelled Tasks

If an approver task is cancelled (via WCP-38 cancellation set) after the cancelling
approver completes, the AND-join never receives a token from the cancelled lane.

**Solution**: Use a dedicated "approval aggregator" subnet that handles partial
completion. See [template.yawl](template.yawl) for the implementation.

### 2. Data Binding Collisions in AND-Split

All approver tasks write to different output variable names. If two approver tasks
write to the same output variable, the last writer wins.

**Solution**: Name output variables `approver1Decision`, `approver2Decision`, etc.
The post-AND-join task reads all variables and aggregates the result.

### 3. No Maximum Approver Wait Time

Without a timer, one unresponsive approver blocks the entire AND-join indefinitely.

**Solution**: Add WCP-38 + timer escalation (see [ENT-ESCALATION](../escalation-chain/README.md)).

## Test Case

[test-case.json](test-case.json):

```json
{
  "pattern": "ENT-PARALLEL-APPROVAL",
  "scenarios": [
    {
      "name": "All approve",
      "input": {"amount": 75000, "vendor": "Acme"},
      "steps": [
        {"task": "FinanceApproval", "output": {"decision": "APPROVED"}},
        {"task": "DeptHeadApproval", "output": {"decision": "APPROVED"}},
        {"task": "LegalApproval", "output": {"decision": "APPROVED"}}
      ],
      "expectedFinalTask": "ProcessApprovalNotify",
      "expectedCaseStatus": "Complete"
    },
    {
      "name": "Legal rejects",
      "input": {"amount": 75000, "vendor": "Acme"},
      "steps": [
        {"task": "LegalApproval", "output": {"decision": "REJECTED", "reason": "Vendor not on approved list"}},
        {"task": "FinanceApproval", "expectStatus": "Cancelled"},
        {"task": "DeptHeadApproval", "expectStatus": "Cancelled"}
      ],
      "expectedFinalTask": "RejectNotify",
      "expectedCaseStatus": "Complete"
    }
  ]
}
```

## Related Patterns

| Pattern | Relationship |
|---------|-------------|
| [WCP-02 Parallel Split](../../control-flow/WCP-02-parallel-split/README.md) | Core AND-split semantics |
| [WCP-03 Synchronisation](../../control-flow/WCP-03-synchronisation/README.md) | Core AND-join semantics |
| [WCP-38 Cancelling Task](../../control-flow/WCP-38-cancelling-task/README.md) | Rejection cancels peers |
| [ENT-APPROVAL](../approval-workflow/README.md) | Simpler single-approver variant |
| [ENT-ESCALATION](../escalation-chain/README.md) | Add timeout escalation to approvers |
| [ENT-CONDITIONAL-ROUTING](../conditional-routing/README.md) | Route to different approver sets based on amount |

## Enterprise Use Cases

| Use Case | Approvers | Rejection Policy |
|----------|-----------|-----------------|
| Purchase order (>$50K) | Finance, DeptHead, Legal | Immediate rejection on any |
| Software vendor contract | Procurement, Security, Legal | All must approve |
| Employee promotion | HR, Manager, VP | Majority rule (2 of 3) |
| Infrastructure change | SRE Lead, Architect, CISO | Any can veto |

## Workflow Pattern Reference

- WCP-02: van der Aalst et al. (2003), Workflow Patterns, LNCS 2626, Pattern 2
- WCP-03: van der Aalst et al. (2003), Workflow Patterns, LNCS 2626, Pattern 3
- https://www.workflowpatterns.com/patterns/control/basic/wcp2.php
- https://www.workflowpatterns.com/patterns/control/basic/wcp3.php

---

**Pattern Version**: 6.0.0
**Last Updated**: 2026-02-17
**Maintained by**: YAWL Architecture Team
