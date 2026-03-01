# Milestone Pattern Implementation Guide

## Overview

This guide provides the user-facing documentation for implementing the Milestone pattern (WCP-18) in YAWL workflows.

## What is a Milestone?

A milestone is a condition that controls task execution based on the state of another part of the workflow. Tasks guarded by a milestone can only execute when the milestone condition is satisfied (i.e., when the milestone is "reached").

### Key Characteristics

- **State-based**: Determined by data in the workflow, not just task completion
- **Reachable**: Can transition from NOT_REACHED to REACHED
- **Expirable**: Can expire based on time or data changes
- **Guards Tasks**: One or more tasks may depend on a milestone being reached

## Creating a Milestone Condition

A milestone is defined as a special condition element in your workflow net:

```xml
<milestone id="PaymentApproved">
  <name>Payment Approved</name>
  <documentation>Tracks when payment has been approved</documentation>
  <expression>//orderData/status = 'APPROVED'</expression>
  <expiryType>TIME_BASED</expiryType>
  <expiryTimeout>3600000</expiryTimeout>
  <flowsInto>
    <nextElementRef id="ShipOrder"/>
  </flowsInto>
</milestone>
```

### Milestone Elements Explained

| Element | Required | Type | Purpose |
|---------|----------|------|---------|
| id | Yes | Identifier | Unique milestone name in the net |
| name | Yes (recommended) | Text | Human-readable label |
| documentation | No | Text | Description of when milestone is reached |
| expression | Yes | XPath/XQuery | Condition to evaluate (boolean result) |
| expiryType | No | Enum | How milestone expires (TIME_BASED, DATA_BASED, NEVER) |
| expiryTimeout | No | Long | Timeout in milliseconds (0 = no timeout) |
| flowsInto | No | Flow reference | Where milestone leads in the net |

### Expression Syntax

Expressions are evaluated against the net's XML data:

```xpath
/* Valid expressions */
//orderData/status = 'APPROVED'
/invoice/amount > 1000
/workflow/stage = 'VALIDATED' AND /workflow/reviewer != ''
count(//tasks/completed) >= 3
```

The expression must evaluate to a boolean (true or false).

## Guarding Tasks with Milestones

Once a milestone is defined, you can guard task execution using milestoneGuards:

```xml
<task id="ShipOrder">
  <name>Ship Order</name>
  <milestoneGuards operator="AND">
    <guard>
      <milestoneRef id="PaymentApproved"/>
    </guard>
  </milestoneGuards>
  <!-- ... rest of task definition ... -->
</task>
```

### Guard Operators

| Operator | Meaning | Example |
|----------|---------|---------|
| AND | All milestones must be reached | Ship only when both payment AND inventory verified |
| OR | Any milestone must be reached | Proceed when fast-track OR normal approval received |
| XOR | Exactly one milestone must be reached | Use single approval path |

## Milestone States and Lifecycle

### Milestone States

```
NOT_REACHED → REACHED → EXPIRED → REACHED (cycle)
```

| State | Description |
|-------|-------------|
| NOT_REACHED | Milestone condition is false; guarded tasks cannot execute |
| REACHED | Milestone condition is true; guarded tasks are enabled |
| EXPIRED | Milestone was reached but condition became false again |

## Expiry Configuration

### TIME_BASED Expiry

Milestone expires after a specified time:

```xml
<expiryType>TIME_BASED</expiryType>
<expiryTimeout>86400000</expiryTimeout>  <!-- 24 hours in milliseconds -->
```

### DATA_BASED Expiry

Milestone expires when condition changes:

```xml
<expiryType>DATA_BASED</expiryType>
<expiryTimeout>0</expiryTimeout>
```

### NEVER Expiry

Milestone never expires once reached:

```xml
<expiryType>NEVER</expiryType>
<expiryTimeout>0</expiryTimeout>
```

## Validation Rules

When defining milestones:

1. Expression must be valid XPath/XQuery that evaluates to boolean
2. Milestone ID must be unique within the net
3. Guarded task must reference existing milestone in same net
4. Operator must be AND, OR, or XOR
5. Timeout must be non-negative integer (milliseconds)
6. Expiry type must be TIME_BASED, DATA_BASED, or NEVER

## See Also

- YAWL Specification Format
- Workflow Patterns (http://www.workflowpatterns.com/)
- WCP-18: Milestone (http://www.workflowpatterns.com/patterns/control/18.php)
- YAWL Schema 4.0 Reference
