# Getting Started with YAWL Worklet Service

Learn how to use Ripple Down Rules (RDR) to dynamically adapt workflows at runtime.

## What You'll Learn

In this tutorial, you'll:
1. Understand the Worklet Service concept
2. Create your first Ripple Down Rule (RDR) tree
3. Deploy worklet specifications
4. Select worklets based on work item context
5. Handle exceptions with worklets

**Time to complete:** 35 minutes
**Prerequisites:** YAWL v6.0+ running, understanding of YAWL specifications

---

## Part 1: Understanding Worklets

### What is a Worklet?

A **worklet** is a small reusable workflow that substitutes a single task in the main workflow. Instead of hardcoding task logic, you define rules that choose which worklet to execute based on the current context.

### Example: Invoice Processing

```
Main Workflow: ProcessInvoice
  └── Task: ReviewAndApprove
      └── Worklet Selection (based on context):
          ├── If invoiceAmount > $10,000 → ExecutiveApprovalWorklet
          ├── If invoiceAmount > $1,000 → ManagerApprovalWorklet
          └── If invoiceAmount <= $1,000 → AutoApprovalWorklet
```

### Benefits

- **Flexibility:** Change business rules without redeploying the main workflow
- **Maintainability:** Separate concerns (main process vs. variations)
- **Scalability:** Add new worklets without modifying existing logic
- **Adaptability:** Rules can be updated at runtime

---

## Part 2: Create Your First RDR Tree

### Step 1: Understand RDR Algorithm

Ripple Down Rules use a simple decision tree:

```
START at root node
  │
  ├─ Evaluate condition
  │
  ├─ If TRUE → follow true-child (more specific rule)
  │
  └─ If FALSE → follow false-child (alternative)
      │
      └─ Remember last satisfied node's conclusion
```

### Step 2: Define RDR Rules via API

Create rules for invoice approval:

```bash
# Create a new RDR tree for the "ReviewAndApprove" task
curl -X POST http://localhost:8080/yawl/api/v1/worklets/rules \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "taskId": "ReviewAndApprove",
    "rootRule": {
      "id": 1,
      "condition": "invoiceAmount > 10000",
      "conclusion": "ExecutiveApprovalWorklet",
      "trueChild": {
        "id": 2,
        "condition": "customerType == \"VIP\"",
        "conclusion": "VIPExecutiveApprovalWorklet"
      },
      "falseChild": {
        "id": 3,
        "condition": "invoiceAmount > 1000",
        "conclusion": "ManagerApprovalWorklet",
        "trueChild": {
          "id": 4,
          "condition": "isUrgent == true",
          "conclusion": "UrgentApprovalWorklet"
        },
        "falseChild": {
          "id": 5,
          "condition": null,
          "conclusion": "AutoApprovalWorklet"
        }
      }
    }
  }'
```

### Step 3: View the RDR Tree

```bash
# Get the rule tree for a task
curl -X GET http://localhost:8080/yawl/api/v1/worklets/rules/ReviewAndApprove \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

## Part 3: Deploy Worklet Specifications

Worklets are just small YAWL specifications. Deploy them like any other spec:

### Example: AutoApprovalWorklet.yawl

```xml
<?xml version="1.0" encoding="UTF-8"?>
<YAWL version="6.0">
  <specification uri="AutoApprovalWorklet" name="Auto Approval">
    <documentation>Auto-approve invoices under $1,000</documentation>

    <net id="AutoApprovalNet">
      <task id="LogApproval">
        <documentation>Log automatic approval</documentation>
        <decomposition id="LogApprovalTask" />
      </task>

      <inputCondition id="InputCondition" />
      <outputCondition id="OutputCondition" />

      <flow source="InputCondition" target="LogApproval" />
      <flow source="LogApproval" target="OutputCondition" />
    </net>
  </specification>
</YAWL>
```

Deploy it:

```bash
curl -X POST http://localhost:8080/yawl/api/v1/specifications \
  -H "Content-Type: application/xml" \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @AutoApprovalWorklet.yawl
```

---

## Part 4: Select Worklets at Runtime

### Step 1: Create Main Workflow with Worklet Task

Main workflow file (`ProcessInvoice.yawl`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<YAWL version="6.0">
  <specification uri="ProcessInvoice" name="Invoice Processing">

    <net id="ProcessInvoiceNet">
      <task id="ReceiveInvoice">
        <decomposition id="ReceiveInvoiceTask" />
      </task>

      <!-- This task will be substituted by a worklet -->
      <task id="ReviewAndApprove">
        <documentation>Review and approve invoice (worklet-enabled)</documentation>
        <workletEnabled>true</workletEnabled>
      </task>

      <task id="PayInvoice">
        <decomposition id="PayInvoiceTask" />
      </task>

      <inputCondition id="InputCondition" />
      <outputCondition id="OutputCondition" />

      <flow source="InputCondition" target="ReceiveInvoice" />
      <flow source="ReceiveInvoice" target="ReviewAndApprove" />
      <flow source="ReviewAndApprove" target="PayInvoice" />
      <flow source="PayInvoice" target="OutputCondition" />
    </net>
  </specification>
</YAWL>
```

Deploy it:

```bash
curl -X POST http://localhost:8080/yawl/api/v1/specifications \
  -H "Content-Type: application/xml" \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @ProcessInvoice.yawl
```

### Step 2: Start a Case and Observe Worklet Selection

```bash
# Start case with invoice data
CASE_ID=$(curl -X POST http://localhost:8080/yawl/api/v1/cases \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "specificationId": "ProcessInvoice",
    "data": {
      "invoiceAmount": 5500.00,
      "customerType": "Standard",
      "isUrgent": false
    }
  }' | jq -r '.caseId')

echo "Case ID: $CASE_ID"
```

### Step 3: Check Selected Worklet

When the `ReviewAndApprove` task is enabled, the Worklet Service:
1. Evaluates RDR conditions against the case data
2. Selects the appropriate worklet: `ManagerApprovalWorklet` (since 5500 > 1000)
3. Launches the worklet as a subcase

```bash
# Get active worklets for a case
curl -X GET http://localhost:8080/yawl/api/v1/cases/$CASE_ID/worklets \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Response:

```json
{
  "worklets": [
    {
      "taskId": "ReviewAndApprove",
      "selectedWorklet": "ManagerApprovalWorklet",
      "parentCaseId": "$CASE_ID",
      "subcaseId": "i42bf8c7d2dd4f456",
      "selectedAt": "2026-02-28T14:35:00Z"
    }
  ]
}
```

---

## Part 5: Handle Exceptions with Worklets

Worklets can also handle exceptions (timeouts, constraint violations, etc.):

### Step 1: Define Exception Rules

```bash
curl -X POST http://localhost:8080/yawl/api/v1/worklets/exception-rules \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "taskId": "ReviewAndApprove",
    "exceptionRules": {
      "TIMEOUT": {
        "conclusion": "ApprovalTimeoutWorklet",
        "description": "Escalate if approval takes > 24 hours"
      },
      "CONSTRAINT_VIOLATION": {
        "conclusion": "ConstraintViolationWorklet",
        "description": "Handle constraint violations"
      },
      "EXTERNAL_SERVICE_FAILURE": {
        "conclusion": "CompensationWorklet",
        "description": "Compensate if external service fails"
      }
    }
  }'
```

### Step 2: Worklet Handles Exception

When an exception occurs in the main task, YAWL automatically launches the corresponding exception worklet:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<YAWL version="6.0">
  <specification uri="ApprovalTimeoutWorklet" name="Approval Timeout Handler">
    <net id="TimeoutHandlerNet">
      <task id="EscalateToDirector">
        <decomposition id="SendEscalationEmailTask" />
      </task>

      <inputCondition id="InputCondition" />
      <outputCondition id="OutputCondition" />

      <flow source="InputCondition" target="EscalateToDirector" />
      <flow source="EscalateToDirector" target="OutputCondition" />
    </net>
  </specification>
</YAWL>
```

---

## Part 6: Update Rules at Runtime

Add new rules without restarting:

### Step 1: Add a New Rule

```bash
# Add rule for a new customer type
curl -X POST http://localhost:8080/yawl/api/v1/worklets/rules/ReviewAndApprove/add \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "parentNodeId": 2,
    "position": "true-child",
    "newRule": {
      "id": 6,
      "condition": "customerType == \"GoldPartner\"",
      "conclusion": "GoldPartnerApprovalWorklet"
    }
  }'
```

### Step 2: Update an Existing Rule

```bash
curl -X PATCH http://localhost:8080/yawl/api/v1/worklets/rules/ReviewAndApprove/2 \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "condition": "customerType == \"PremiumVIP\"",
    "conclusion": "PremiumVIPApprovalWorklet"
  }'
```

### Step 3: Remove a Rule

```bash
curl -X DELETE http://localhost:8080/yawl/api/v1/worklets/rules/ReviewAndApprove/5 \
  -H "Authorization: Bearer $TOKEN"
```

---

## Complete Example: Order Processing

Here's a full example with Java code:

```java
import org.yawlfoundation.yawl.worklet.*;
import java.util.*;

public class OrderProcessingExample {
    private RdrSet rdrSet;
    private WorkletService workletService;

    public void setupOrderRules() {
        // Create RDR tree for ProcessOrder task
        RdrNode root = new RdrNode(
            1,
            "orderAmount > 10000",
            "HighValueOrderWorklet"
        );

        RdrNode vipNode = new RdrNode(
            2,
            "customerType == \"VIP\"",
            "VIPOrderWorklet"
        );

        RdrNode mediumNode = new RdrNode(
            3,
            "orderAmount > 1000",
            "StandardOrderWorklet"
        );

        root.setTrueChild(vipNode);
        root.setFalseChild(mediumNode);

        RdrTree orderTree = new RdrTree(root);
        rdrSet.setTreeForTask("ProcessOrder", orderTree);
    }

    public void processOrder(Map<String, Object> orderData) {
        String selectedWorklet = rdrSet
            .getTreeForTask("ProcessOrder")
            .evaluate(orderData);

        System.out.println("Selected worklet: " + selectedWorklet);
        // Worklet Service now launches the selected worklet
    }
}
```

---

## Troubleshooting

### Worklet not selected

**Problem:** Task completes without launching a worklet

**Solution:**
1. Verify task has `<workletEnabled>true</workletEnabled>`
2. Check RDR tree exists: `GET /api/v1/worklets/rules/{taskId}`
3. Verify condition evaluation: Add logging to condition expressions

### Wrong worklet selected

**Problem:** Different worklet than expected is launched

**Solution:**
1. Trace RDR evaluation with debug logs
2. Check data passed to conditions: `GET /api/v1/cases/{caseId}`
3. Verify condition syntax (case-sensitive)

### Worklet completion doesn't mark parent task complete

**Problem:** Parent case waits indefinitely

**Solution:**
1. Ensure worklet has proper output condition
2. Check worklet completion is being tracked: logs for "WorkletCompletion"
3. Verify parent case not in error state

---

## What's Next?

- **[Worklet Configuration Reference](../reference/yawl-worklet-config.md)** — All RDR options
- **[How-To: Implement Worklet Service](../how-to/implement-worklet-service.md)** — Production setup
- **[How-To: Advanced RDR Patterns](../how-to/yawl-worklet-patterns.md)** — Complex rules
- **[Architecture: RDR Design](../explanation/yawl-worklet-architecture.md)** — Theory and design

---

## Quick Reference

| Task | Endpoint |
|------|----------|
| Create RDR tree | `POST /api/v1/worklets/rules` |
| Get RDR tree | `GET /api/v1/worklets/rules/{taskId}` |
| Add rule | `POST /api/v1/worklets/rules/{taskId}/add` |
| Update rule | `PATCH /api/v1/worklets/rules/{taskId}/{nodeId}` |
| Delete rule | `DELETE /api/v1/worklets/rules/{taskId}/{nodeId}` |
| Get active worklets | `GET /api/v1/cases/{caseId}/worklets` |
| Deploy worklet spec | `POST /api/v1/specifications` |

---

**Next:** [How-To: Implement Worklet Service in Production](../how-to/implement-worklet-service.md)
