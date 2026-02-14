# Event Horizon Cloud — A2A Mesh API Reference

## Overview

The **Event Horizon Mesh** enables Agent-to-Agent (A2A) coordination through a standardized discovery, messaging, and task lifecycle protocol. Agents register themselves, discover other agents, post tasks, and receive results—all coordinated through the YAWL ontology.

**Endpoint**: `wss://mesh.{region}.eventhorizoncloud.io/a2a`

## Core Concepts

### Agent Card

An agent's public identity and capabilities.

```json
{
  "agentId": "agent-order-validator:8080",
  "name": "Order Validator",
  "description": "Validates orders for fraud and inventory",
  "version": "1.0.0",
  "capabilities": [
    {
      "name": "validate_order",
      "description": "Check order validity",
      "inputSchema": {
        "type": "object",
        "properties": {
          "orderId": { "type": "string" },
          "items": { "type": "array" }
        },
        "required": ["orderId"]
      },
      "outputSchema": {
        "type": "object",
        "properties": {
          "validationResult": { "enum": ["approved", "rejected"] },
          "riskScore": { "type": "number", "minimum": 0, "maximum": 1 }
        }
      }
    }
  ],
  "endpoint": "http://order-validator:8080/api",
  "maxConcurrentTasks": 10,
  "healthCheckUrl": "http://order-validator:8080/health",
  "ttl": 3600,
  "tags": ["order-processing", "validation"]
}
```

### Task

Work unit posted to an agent or agent pool.

```json
{
  "taskId": "task-abc123xyz789",
  "caseId": "case-def456jkl012",
  "workTokenId": "wtoken-ghi789mno345",
  "capability": "validate_order",
  "input": {
    "orderId": "ORD-2025-001",
    "items": [
      {"sku": "WIDGET-A", "qty": 5}
    ]
  },
  "deadline": "2025-02-14T12:00:00Z",
  "priority": "normal",
  "retryPolicy": {
    "maxRetries": 3,
    "backoffMs": [1000, 5000, 30000]
  },
  "idempotencyKey": "idem-xyz123",
  "requester": {
    "agentId": "workflow-orchestrator",
    "caseId": "case-def456jkl012"
  }
}
```

### Result

Completion result returned by agent.

```json
{
  "taskId": "task-abc123xyz789",
  "status": "completed",
  "output": {
    "validationResult": "approved",
    "riskScore": 0.05
  },
  "executionTime": 234,
  "completedAt": "2025-02-14T11:45:30Z",
  "resultToken": "rtoken-jkl789pqr456",
  "receipt": {
    "ledgerId": "receipt-003",
    "hash": "sha256:ghi789...",
    "signature": "ed25519:..."
  }
}
```

## Connection Protocol

### 1. Agent Registration

```json
{
  "type": "register",
  "payload": {
    "agentCard": {
      "agentId": "agent-order-validator:8080",
      "name": "Order Validator",
      "capabilities": [...],
      "endpoint": "http://order-validator:8080/api",
      "maxConcurrentTasks": 10
    }
  }
}
```

**Response**:
```json
{
  "type": "registered",
  "payload": {
    "agentId": "agent-order-validator:8080",
    "sessionId": "sess-abc123",
    "registeredAt": "2025-02-14T11:00:00Z",
    "ttl": 3600
  }
}
```

### 2. Capability Discovery

```json
{
  "type": "discover",
  "payload": {
    "query": {
      "capability": "validate_*",
      "tags": ["validation"]
    },
    "limit": 10
  }
}
```

**Response**:
```json
{
  "type": "discovery_result",
  "payload": {
    "agents": [
      {
        "agentId": "agent-order-validator:8080",
        "capabilities": ["validate_order"],
        "endpoint": "http://order-validator:8080/api",
        "healthStatus": "healthy",
        "loadFactor": 0.3
      },
      {
        "agentId": "agent-fraud-validator:9090",
        "capabilities": ["validate_fraud"],
        "endpoint": "http://fraud-validator:9090/api",
        "healthStatus": "healthy",
        "loadFactor": 0.1
      }
    ]
  }
}
```

### 3. Task Dispatch

```json
{
  "type": "dispatch_task",
  "payload": {
    "task": {
      "taskId": "task-abc123xyz789",
      "caseId": "case-def456jkl012",
      "capability": "validate_order",
      "input": {
        "orderId": "ORD-2025-001",
        "items": [...]
      },
      "deadline": "2025-02-14T12:00:00Z",
      "priority": "normal",
      "idempotencyKey": "idem-xyz123"
    },
    "targetAgentId": "agent-order-validator:8080"
  }
}
```

**Response** (immediate ack):
```json
{
  "type": "task_accepted",
  "payload": {
    "taskId": "task-abc123xyz789",
    "workTokenId": "wtoken-ghi789mno345",
    "acceptedAt": "2025-02-14T11:30:00Z"
  }
}
```

### 4. Task Result

```json
{
  "type": "result_posted",
  "payload": {
    "result": {
      "taskId": "task-abc123xyz789",
      "status": "completed",
      "output": {
        "validationResult": "approved",
        "riskScore": 0.05
      },
      "executionTime": 234,
      "completedAt": "2025-02-14T11:45:30Z"
    }
  }
}
```

### 5. Heartbeat

```json
{
  "type": "heartbeat",
  "payload": {
    "agentId": "agent-order-validator:8080",
    "sessionId": "sess-abc123",
    "loadFactor": 0.25,
    "tasksActive": 2,
    "timestamp": "2025-02-14T11:50:00Z"
  }
}
```

## Work Tokens (Consumption-Based Execution)

A **WorkToken** represents capacity on the Event Horizon Tokens service (stateless worker pool). Agents can consume tokens to offload compute.

```json
{
  "type": "consume_token",
  "payload": {
    "workTokenId": "wtoken-ghi789mno345",
    "capability": "heavy_computation",
    "input": {
      "dataset": "large_dataset_uri",
      "algorithm": "monte_carlo_simulation"
    },
    "estimatedCost": 0.50
  }
}
```

**Response**:
```json
{
  "type": "token_execution_result",
  "payload": {
    "workTokenId": "wtoken-ghi789mno345",
    "resultToken": "rtoken-jkl789pqr456",
    "executionTime": 5432,
    "tokensConsumed": 542,
    "costUSD": 0.0542,
    "output": {
      "simulationResult": {...}
    }
  }
}
```

## Subscription & Resubscription

Agents can subscribe to task types and automatically receive matching work.

```json
{
  "type": "subscribe",
  "payload": {
    "agentId": "agent-order-validator:8080",
    "subscriptions": [
      {
        "capability": "validate_order",
        "priority": "high",
        "autoAccept": true,
        "maxQueueSize": 100
      },
      {
        "capability": "validate_order",
        "filter": {
          "caseId": "case-def456jkl012"
        }
      }
    ]
  }
}
```

Tasks matching subscriptions are immediately pushed:

```json
{
  "type": "task_pushed",
  "payload": {
    "task": {
      "taskId": "task-abc123xyz789",
      "capability": "validate_order",
      "input": {...},
      "deadline": "2025-02-14T12:00:00Z"
    }
  }
}
```

## Error Handling

If an agent fails to complete a task:

```json
{
  "type": "task_failed",
  "payload": {
    "taskId": "task-abc123xyz789",
    "error": {
      "code": "VALIDATION_ERROR",
      "message": "Invalid order items: missing SKU",
      "details": "Item at index 0 has no SKU"
    },
    "failedAt": "2025-02-14T11:35:00Z",
    "retryable": true
  }
}
```

The mesh automatically retries up to the policy's `maxRetries` limit, then reports final failure to the requesting agent.

## Multi-Agent Orchestration Example

**Scenario**: Order fulfillment requires parallel validation (fraud, inventory, payment).

### Workflow

```
1. Orchestrator queries mesh for validators
2. Orchestrator dispatches 3 tasks in parallel
3. Validators accept and execute
4. Orchestrator waits for all results
5. Orchestrator transitions workflow based on outcomes
```

### Message Flow

**Step 1: Discovery**
```json
{
  "type": "discover",
  "payload": {
    "query": {
      "capability": "validate_*",
      "minHealthy": 1
    }
  }
}
```

**Step 2: Dispatch (3 parallel tasks)**
```json
{
  "type": "dispatch_task",
  "payload": {
    "task": {
      "taskId": "task-fraud-check",
      "caseId": "case-def456jkl012",
      "capability": "validate_fraud",
      "input": {"orderId": "ORD-2025-001"}
    },
    "targetAgentId": "agent-fraud-validator:9090"
  }
}
```

(Repeat for `validate_inventory` and `validate_payment`.)

**Step 3: Results (aggregated by orchestrator)**
```json
{
  "type": "result_posted",
  "payload": {
    "results": [
      {
        "taskId": "task-fraud-check",
        "status": "completed",
        "output": {"riskScore": 0.05}
      },
      {
        "taskId": "task-inventory-check",
        "status": "completed",
        "output": {"available": true, "qty": 50}
      },
      {
        "taskId": "task-payment-check",
        "status": "completed",
        "output": {"authorized": true}
      }
    ]
  }
}
```

## Authentication & Security

- **TLS 1.3+**: All connections encrypted
- **mTLS**: Agent identity verification (optional for enterprise)
- **API Keys**: Session-based access tokens
- **Signed Messages**: Optional cryptographic signing of task results
- **Rate Limiting**: Per-agent quotas (tasks/sec, concurrent tasks)
- **Audit Trail**: All task dispatch, completion, and failures logged to receipts

## SDKs

- Python: `pip install event-horizon-mesh`
- JavaScript/TypeScript: `npm install event-horizon-mesh`
- Go: `go get github.com/eventhorizoncloud/mesh-go`

## Python Example

```python
import asyncio
from event_horizon import MeshClient, AgentCard, Task

async def main():
    # Connect to mesh
    client = MeshClient(api_key="your_api_key", region="us-central1")

    # Register agent
    card = AgentCard(
        agent_id="agent-order-validator:8080",
        name="Order Validator",
        capabilities=[
            {
                "name": "validate_order",
                "input_schema": {...},
                "output_schema": {...}
            }
        ],
        endpoint="http://localhost:8080/api"
    )

    await client.register(card)

    # Subscribe to tasks
    async for task in client.subscribe(capability="validate_order"):
        print(f"Received task: {task.id}")

        # Process task
        result = await validate_order(task.input)

        # Post result
        await client.post_result(task.id, result)

if __name__ == "__main__":
    asyncio.run(main())
```

## Monitoring

Event Horizon Console provides real-time visibility into:
- Agent registration and health
- Task throughput and latency
- Failure rates and retry behavior
- Work token consumption and cost
- Receipt ledger and audit trail

Access: https://console.eventhorizoncloud.io

## Support

- Documentation: https://docs.eventhorizoncloud.io/mesh
- Slack: #event-horizon-mesh (eventhorizoncloud.slack.com)
- Email: mesh-support@eventhorizoncloud.io
