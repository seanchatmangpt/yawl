# Event Horizon Cloud — MCP Gateway API Reference

## Overview

The **Event Horizon Gateway** exposes YAWL coordination through the Model Context Protocol (MCP), enabling AI agents and developer tools to invoke workflows, query state, and access case data through standard tool interfaces.

**Endpoint**: `https://gateway.{region}.eventhorizoncloud.io/mcp`

## Authentication

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://gateway.us-central1.eventhorizoncloud.io/mcp/tools/list
```

Obtain API keys from the [Event Horizon Console](https://console.eventhorizoncloud.io).

## Core Interfaces

### 1. Tool Interface

Tools are MCP-standard invocables for workflow operations.

#### `workflow.start`

Start a new case (process instance).

```json
{
  "jsonrpc": "2.0",
  "id": "request-123",
  "method": "tools/call",
  "params": {
    "name": "workflow.start",
    "arguments": {
      "specification": "order-fulfillment",
      "caseData": {
        "orderId": "ORD-2025-001",
        "customerId": "CUST-555",
        "items": [
          {"sku": "WIDGET-A", "qty": 5, "price": 29.99}
        ]
      }
    }
  }
}
```

**Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "request-123",
  "result": {
    "caseId": "case-abc123xyz789",
    "specId": "order-fulfillment",
    "status": "active",
    "createdAt": "2025-02-14T10:30:00Z",
    "receipt": {
      "ledgerId": "receipt-001",
      "hash": "sha256:abc123...",
      "timestamp": "2025-02-14T10:30:00Z"
    }
  }
}
```

#### `workflow.enableTask`

Enable a task for resource execution.

```json
{
  "jsonrpc": "2.0",
  "id": "request-124",
  "method": "tools/call",
  "params": {
    "name": "workflow.enableTask",
    "arguments": {
      "caseId": "case-abc123xyz789",
      "taskId": "task-validate-order",
      "resourceUri": "agent://order-validator:8080"
    }
  }
}
```

#### `workflow.completeTask`

Complete an enabled task (task output).

```json
{
  "jsonrpc": "2.0",
  "id": "request-125",
  "method": "tools/call",
  "params": {
    "name": "workflow.completeTask",
    "arguments": {
      "caseId": "case-abc123xyz789",
      "taskId": "task-validate-order",
      "outputData": {
        "validationResult": "approved",
        "riskScore": 0.05
      }
    }
  }
}
```

#### `workflow.cancelCase`

Cancel a case with reason.

```json
{
  "jsonrpc": "2.0",
  "id": "request-126",
  "method": "tools/call",
  "params": {
    "name": "workflow.cancelCase",
    "arguments": {
      "caseId": "case-abc123xyz789",
      "reason": "customer_requested_cancellation"
    }
  }
}
```

### 2. Resource Interface

Resources are query endpoints for state and data access.

#### `workflow.cases`

List all cases matching filters.

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  'https://gateway.us-central1.eventhorizoncloud.io/mcp/resources/workflow.cases?specId=order-fulfillment&status=active'
```

**Response**:
```json
{
  "cases": [
    {
      "caseId": "case-abc123xyz789",
      "specId": "order-fulfillment",
      "status": "active",
      "createdAt": "2025-02-14T10:30:00Z",
      "currentTasks": ["task-payment-processing", "task-inventory-check"],
      "caseData": { ... }
    }
  ],
  "pageToken": "...",
  "totalCount": 1524
}
```

#### `workflow.case`

Get details for a single case.

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  'https://gateway.us-central1.eventhorizoncloud.io/mcp/resources/workflow.case/case-abc123xyz789'
```

#### `workflow.receipts`

Query the receipt ledger for compliance + audit trail.

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  'https://gateway.us-central1.eventhorizoncloud.io/mcp/resources/workflow.receipts?caseId=case-abc123xyz789'
```

**Response** (cryptographic proof of execution):
```json
{
  "receipts": [
    {
      "ledgerId": "receipt-001",
      "caseId": "case-abc123xyz789",
      "event": "case_created",
      "timestamp": "2025-02-14T10:30:00Z",
      "hash": "sha256:abc123...",
      "previousHash": "sha256:xyz789...",
      "signature": "ed25519:...",
      "actor": "system"
    },
    {
      "ledgerId": "receipt-002",
      "caseId": "case-abc123xyz789",
      "event": "task_enabled",
      "taskId": "task-validate-order",
      "timestamp": "2025-02-14T10:31:15Z",
      "hash": "sha256:def456...",
      "previousHash": "sha256:abc123...",
      "signature": "ed25519:...",
      "actor": "resource-manager"
    }
  ]
}
```

### 3. Prompt Interface

Prompts are MCP-standard instructions for AI agents.

#### `workflow.orchestrate`

Prompt template for multi-task orchestration.

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  'https://gateway.us-central1.eventhorizoncloud.io/mcp/prompts/workflow.orchestrate'
```

**Returns** a system prompt that instructs an AI agent to:
1. Query enabled tasks for a case
2. Determine next steps based on workflow logic
3. Invoke tasks via the tool interface
4. Handle task outputs and transitions

## Advanced Patterns

### Pattern: Parallel XOR (Exclusive Choice)

One of the 43 YAWL patterns. Example use case: Order fulfillment (standard processing OR express processing).

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "workflow.enableTask",
    "arguments": {
      "caseId": "case-abc123xyz789",
      "taskId": "task-choose-processing",
      "promptForChoice": true,
      "choices": [
        {
          "taskId": "task-standard-processing",
          "condition": "outputData.shippingMethod == 'standard'"
        },
        {
          "taskId": "task-express-processing",
          "condition": "outputData.shippingMethod == 'express'"
        }
      ]
    }
  }
}
```

### Pattern: AND-Split (Synchronization)

Multiple tasks in parallel, rejoin when all complete.

```json
{
  "caseId": "case-abc123xyz789",
  "taskId": "task-parallel-checks",
  "parallelTasks": [
    "task-credit-check",
    "task-inventory-check",
    "task-fraud-check"
  ],
  "joinCondition": "all_complete"
}
```

### Pattern: OR-Join (Deferred Merge)

Re-join when at least one branch signals completion.

```json
{
  "caseId": "case-abc123xyz789",
  "taskId": "task-approval",
  "joinCondition": "any_complete",
  "timeout": "24h"
}
```

## Rate Limiting

- **Starter**: 10 requests/second
- **Professional**: 100 requests/second
- **Enterprise**: 1000 requests/second

Rate limit headers:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 1707897000
```

## Error Handling

```json
{
  "jsonrpc": "2.0",
  "id": "request-123",
  "error": {
    "code": -32603,
    "message": "Internal error",
    "data": {
      "errorCode": "CASE_NOT_FOUND",
      "details": "Case abc123xyz789 does not exist",
      "timestamp": "2025-02-14T10:30:00Z"
    }
  }
}
```

Common errors:
- `CASE_NOT_FOUND` (404)
- `TASK_NOT_ENABLED` (400)
- `INVALID_OUTPUT_DATA` (400)
- `AUTH_FAILED` (401)
- `RATE_LIMIT_EXCEEDED` (429)
- `INTERNAL_ERROR` (500)

## Security

- **Transport**: TLS 1.3+
- **Authentication**: OAuth 2.0 Bearer tokens
- **Authorization**: RBAC (resource-level access control)
- **Audit**: All operations logged with receipts
- **Secrets**: No sensitive data in logs; encrypted at rest

## SDKs

- Python: `pip install event-horizon-client`
- JavaScript/TypeScript: `npm install event-horizon-client`
- Go: `go get github.com/eventhorizoncloud/go-client`
- Java: Maven Central (coming soon)

## Examples

### Python

```python
from event_horizon import WorkflowClient

client = WorkflowClient(api_key="your_api_key", region="us-central1")

# Start a case
case = client.start_case(
    specification="order-fulfillment",
    case_data={
        "orderId": "ORD-2025-001",
        "customerId": "CUST-555"
    }
)
print(f"Case created: {case.id}")

# Get case state
case_state = client.get_case(case.id)
print(f"Current tasks: {case_state.current_tasks}")

# Complete a task
client.complete_task(
    case_id=case.id,
    task_id="task-validate-order",
    output_data={"validationResult": "approved"}
)
```

### JavaScript

```javascript
const { WorkflowClient } = require('event-horizon-client');

const client = new WorkflowClient({
  apiKey: process.env.HORIZON_API_KEY,
  region: 'us-central1'
});

// Start case
const caseObj = await client.startCase({
  specification: 'order-fulfillment',
  caseData: {
    orderId: 'ORD-2025-001',
    customerId: 'CUST-555'
  }
});

// Query enabled tasks
const enabledTasks = await client.getCaseTasks(caseObj.id);
console.log('Enabled tasks:', enabledTasks);

// Complete task
await client.completeTask(caseObj.id, 'task-validate-order', {
  validationResult: 'approved'
});
```

## Testing

```bash
# Test tool availability
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://gateway.us-central1.eventhorizoncloud.io/mcp/tools/list

# Test resource access
curl -H "Authorization: Bearer YOUR_API_KEY" \
  https://gateway.us-central1.eventhorizoncloud.io/mcp/resources/workflow.cases?limit=1
```

## Support

- Documentation: https://docs.eventhorizoncloud.io/gateway
- Slack: #event-horizon-gateway (eventhorizoncloud.slack.com)
- Email: gateway-support@eventhorizoncloud.io
