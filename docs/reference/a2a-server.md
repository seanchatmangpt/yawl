# YAWL A2A Server Integration Guide

Agent-to-Agent (A2A) integration for YAWL v6.0.0, enabling multi-agent orchestration and autonomous workflow management.

## Overview

The YAWL A2A Server provides a framework for agents to coordinate workflow operations through a standardized skill-based interface. This enables complex multi-agent scenarios where different agents specialize in different workflow tasks.

**Status**: Planned for v6.1.0
**Protocol**: A2A (Agent-to-Agent Communication)
**Integration**: Works alongside MCP server for full agent capabilities

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                     Agent Orchestrator                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │  Manager     │  │  Coordinator │  │  Task Router         │  │
│  │  Agent       │  │  Agent       │  │                      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
                              │
                              │ A2A Protocol
                              ▼
┌────────────────────────────────────────────────────────────────┐
│                     YawlA2AServer                               │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Skills Registry                       │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐        │   │
│  │  │ /yawl-build │ │ /yawl-test  │ │ /yawl-deploy│ ...    │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘        │   │
│  └─────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────┘
                              │
                              │ YAWL Engine Interface
                              ▼
┌────────────────────────────────────────────────────────────────┐
│                     YAWL Engine                                 │
└────────────────────────────────────────────────────────────────┘
```

## Skills (4 Core)

### 1. `/yawl-workflow`

Manage complete workflow lifecycle: launch, monitor, and complete cases.

**Use Cases**:
- Launch new workflow instances from specifications
- Monitor running cases for completion or errors
- Suspend, resume, or cancel cases as needed
- Query case state and data at any point

**Example Invocation**:
```json
{
  "skill": "/yawl-workflow",
  "operation": "launch",
  "params": {
    "specIdentifier": "OrderProcessing",
    "caseData": {
      "orderId": "12345",
      "customerId": "CUST-001"
    }
  }
}
```

**Operations**:
| Operation | Description |
|-----------|-------------|
| `launch` | Start a new workflow case |
| `status` | Get case status and state |
| `data` | Get/set case variables |
| `suspend` | Suspend a running case |
| `resume` | Resume a suspended case |
| `cancel` | Cancel a running case |

---

### 2. `/yawl-task`

Handle work item execution: checkout, complete, skip, or delegate.

**Use Cases**:
- Claim work items for execution
- Complete work items with output data
- Skip optional work items
- Query available work items

**Example Invocation**:
```json
{
  "skill": "/yawl-task",
  "operation": "complete",
  "params": {
    "workItemId": "42.0:ReviewOrder",
    "outputData": {
      "approved": true,
      "reviewerNotes": "All checks passed"
    }
  }
}
```

**Operations**:
| Operation | Description |
|-----------|-------------|
| `list` | List available work items |
| `checkout` | Claim a work item |
| `complete` | Complete with output data |
| `skip` | Skip an optional task |
| `delegate` | Delegate to another agent |

---

### 3. `/yawl-spec`

Manage workflow specifications: upload, validate, query, and version.

**Use Cases**:
- Upload new workflow definitions
- Validate specifications before deployment
- Query specification metadata
- Manage specification versions

**Example Invocation**:
```json
{
  "skill": "/yawl-spec",
  "operation": "upload",
  "params": {
    "specXml": "<?xml version=\"1.0\"?>...",
    "validate": true
  }
}
```

**Operations**:
| Operation | Description |
|-----------|-------------|
| `list` | List loaded specifications |
| `get` | Get specification XML |
| `upload` | Upload new specification |
| `validate` | Validate without loading |
| `unload` | Remove specification |

---

### 4. `/yawl-monitor`

Monitor engine health, metrics, and events.

**Use Cases**:
- Track engine health and uptime
- Monitor case throughput
- Subscribe to workflow events
- Generate performance reports

**Example Invocation**:
```json
{
  "skill": "/yawl-monitor",
  "operation": "health",
  "params": {}
}
```

**Operations**:
| Operation | Description |
|-----------|-------------|
| `health` | Engine health check |
| `metrics` | Performance metrics |
| `events` | Subscribe to events |
| `throughput` | Case throughput stats |

---

## Configuration

### Environment Variables

```bash
# YAWL Engine Connection
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=your_password

# A2A Server Configuration
export YAWL_A2A_PORT=9090
export YAWL_A2A_AGENTS_MAX=100
export YAWL_A2A_TIMEOUT_MS=30000
```

### Properties File

```yaml
# a2a-config.yml
yawl:
  a2a:
    enabled: true
    port: 9090
    max-agents: 100
    timeout-ms: 30000
    skills:
      - /yawl-workflow
      - /yawl-task
      - /yawl-spec
      - /yawl-monitor
    security:
      auth-required: true
      api-keys:
        - ${AGENT_API_KEY_1}
        - ${AGENT_API_KEY_2}
```

---

## Agent Integration

### Agent Registration

```java
// Register an agent with the A2A server
AgentRegistration registration = AgentRegistration.builder()
    .agentId("order-processor-001")
    .agentType("task-executor")
    .capabilities(List.of("/yawl-task", "/yawl-workflow"))
    .callbackUrl("http://agent-host:8080/callback")
    .build();

A2AClient client = new A2AClient("http://yawl-a2a:9090");
client.registerAgent(registration);
```

### Skill Invocation

```java
// Invoke a skill
SkillRequest request = SkillRequest.builder()
    .skill("/yawl-workflow")
    .operation("launch")
    .param("specIdentifier", "OrderProcessing")
    .param("caseData", caseDataXml)
    .build();

SkillResponse response = client.invokeSkill(request);
String caseId = response.getResult("caseId");
```

### Event Subscription

```java
// Subscribe to workflow events
EventSubscription subscription = EventSubscription.builder()
    .eventType("case_completed")
    .filter("specIdentifier=OrderProcessing")
    .callbackUrl("http://agent-host:8080/events")
    .build();

client.subscribeToEvents(subscription);
```

---

## Multi-Agent Orchestration Patterns

### Pattern 1: Supervisor-Worker

```
┌─────────────┐
│  Supervisor │ ──────► Monitors case progress
│    Agent    │ ──────► Delegates work items
└─────────────┘
       │
       │ Assigns tasks
       ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   Worker    │  │   Worker    │  │   Worker    │
│   Agent 1   │  │   Agent 2   │  │   Agent 3   │
└─────────────┘  └─────────────┘  └─────────────┘
       │                │                │
       └────────────────┴────────────────┘
                        │
                  Complete tasks
```

### Pattern 2: Specialist Pipeline

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ Validation  │───►│  Approval   │───►│ Fulfillment │
│  Specialist │    │  Specialist │    │  Specialist │
└─────────────┘    └─────────────┘    └─────────────┘
      │                  │                  │
  Validates data    Approves/rejects   Executes order
```

### Pattern 3: Parallel Processing

```
                    ┌─────────────┐
              ┌────►│  Processor  │────┐
              │     │    Agent A  │    │
┌─────────────┤     └─────────────┘    │
│  Dispatcher │                        │     ┌─────────────┐
│    Agent    │     ┌─────────────┐    ├────►│  Aggregator │
└─────────────┤────►│  Processor  │────┤     │    Agent    │
              │     │    Agent B  │    │     └─────────────┘
              │     └─────────────┘    │
              │     ┌─────────────┐    │
              └────►│  Processor  │────┘
                    │    Agent C  │
                    └─────────────┘
```

---

## Error Handling

### Skill Execution Errors

| Error Code | Description | Resolution |
|------------|-------------|------------|
| `SKILL_NOT_FOUND` | Requested skill not registered | Check skill name spelling |
| `OPERATION_INVALID` | Operation not supported by skill | Use valid operation for skill |
| `PARAM_MISSING` | Required parameter not provided | Include all required params |
| `ENGINE_ERROR` | YAWL engine returned error | Check engine logs |
| `AUTH_FAILED` | Agent authentication failed | Verify API key |

### Retry Configuration

```yaml
yawl:
  a2a:
    retry:
      max-attempts: 3
      backoff-ms: 1000
      retryable-errors:
        - ENGINE_ERROR
        - TIMEOUT
```

---

## Security

### Authentication

All A2A requests require API key authentication:

```http
POST /a2a/skill HTTP/1.1
Host: yawl-a2a:9090
Authorization: Bearer <API_KEY>
Content-Type: application/json
```

### Authorization

Skills can be restricted per agent:

```yaml
yawl:
  a2a:
    authorization:
      order-processor-001:
        - /yawl-task
        - /yawl-workflow
      spec-manager-001:
        - /yawl-spec
        - /yawl-monitor
```

---

## Troubleshooting

### Agent Registration Fails

**Error**: `Agent registration rejected`

**Solutions**:
1. Verify API key is valid
2. Check callback URL is reachable
3. Ensure agent ID is unique

### Skill Invocation Timeout

**Error**: `Skill invocation timed out`

**Solutions**:
1. Increase timeout in configuration
2. Check YAWL engine responsiveness
3. Verify network connectivity

### Event Delivery Fails

**Error**: `Event callback failed`

**Solutions**:
1. Verify callback URL is accessible
2. Check callback server health
3. Review event payload size

---

## Monitoring

### Health Endpoint

```bash
curl http://yawl-a2a:9090/health
```

Response:
```json
{
  "status": "healthy",
  "agents": 5,
  "activeSkills": 12,
  "uptimeMs": 3600000
}
```

### Metrics Endpoint

```bash
curl http://yawl-a2a:9090/metrics
```

Response:
```json
{
  "invocations": {
    "total": 1500,
    "success": 1485,
    "failed": 15
  },
  "latency": {
    "p50": 45,
    "p95": 120,
    "p99": 350
  }
}
```

---

## Roadmap

| Version | Feature |
|---------|---------|
| v6.0.0 | MCP Server (complete) |
| v6.1.0 | A2A Server core implementation |
| v6.2.0 | Multi-agent orchestration engine |
| v6.3.0 | Agent learning and optimization |

---

## References

- [MCP Server Guide](MCP-SERVER.md)
- [YAWL Documentation](../README.md)
- [Agent Development Guide](../autonomous-agents/README.md)
- [Multi-Agent Patterns](../patterns/README.md)
