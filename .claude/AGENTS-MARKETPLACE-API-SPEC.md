# Agents Marketplace — REST API Specification

**Version**: 1.0.0
**Status**: MVP (Week 2-3 implementation)
**Base URL**: `http://localhost:8080` (default, configurable)
**Content-Type**: `application/json`

---

## Table of Contents

1. [Agent Discovery](#agent-discovery)
2. [Agent Profiles](#agent-profiles)
3. [Agent Health](#agent-health)
4. [Orchestration](#orchestration)
5. [Errors](#errors)
6. [Rate Limiting](#rate-limiting)

---

## Agent Discovery

### Discover Agents by Capability

Finds agents that can perform a specific skill.

**Request**
```
POST /agents/discover
Content-Type: application/json

{
  "capability": "approve-expense",
  "limit": 10
}
```

**Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| capability | string | Yes | Skill ID to search for (e.g., "approve-expense") |
| limit | integer | No | Max results (default: 10, max: 100) |
| tag | string | No | Filter by tag (e.g., "approval") |
| minSuccessRate | number | No | Minimum success rate (0.0-1.0, default: 0.0) |
| maxLatency | integer | No | Maximum avg latency in ms (default: no limit) |

**Response (200 OK)**
```json
{
  "results": [
    {
      "id": "approval-agent",
      "name": "Approval Agent",
      "version": "1.0.0",
      "successRate": 0.985,
      "avgLatency": 250,
      "p99Latency": 450,
      "costPerInvocation": 0.01,
      "uptime": 0.9998,
      "invocationsThisMonth": 12450
    },
    {
      "id": "smart-approval-agent",
      "name": "Smart Approval Agent",
      "version": "2.1.0",
      "successRate": 0.98,
      "avgLatency": 500,
      "p99Latency": 1200,
      "costPerInvocation": 0.05,
      "uptime": 0.999,
      "invocationsThisMonth": 5000
    }
  ],
  "count": 2,
  "timestamp": "2026-02-21T14:30:45Z",
  "executionTimeMs": 45
}
```

**Response (400 Bad Request)**
```json
{
  "error": "INVALID_CAPABILITY",
  "message": "Capability not found: invalid-skill",
  "timestamp": "2026-02-21T14:30:45Z"
}
```

**Response (504 Gateway Timeout)**
```json
{
  "error": "DISCOVERY_TIMEOUT",
  "message": "Discovery query exceeded 5 second timeout",
  "timestamp": "2026-02-21T14:30:45Z"
}
```

---

## Agent Profiles

### Get Agent Profile

Retrieves full metadata and capabilities for an agent.

**Request**
```
GET /agents/{agentId}/profile
```

**Parameters**

| Parameter | Type | Location | Required | Description |
|-----------|------|----------|----------|-------------|
| agentId | string | path | Yes | Unique agent ID (e.g., "approval-agent") |

**Response (200 OK)**
```json
{
  "id": "approval-agent",
  "name": "Approval Agent",
  "version": "1.0.0",
  "author": "acme-team",
  "created": "2026-02-21T00:00:00Z",
  "description": "Auto-approves expenses under delegation limit",

  "capabilities": [
    {
      "id": "approve-expense",
      "skillId": "yawl.skills.approval.approve",
      "description": "Approves an expense request",
      "params": {
        "amount": {
          "type": "number",
          "description": "Expense amount in USD",
          "required": true,
          "constraints": {
            "minimum": 0,
            "maximum": 999999
          }
        },
        "requestor": {
          "type": "string",
          "description": "Employee ID",
          "required": true
        },
        "category": {
          "type": "string",
          "enum": ["travel", "meals", "supplies", "training", "equipment"],
          "required": true
        }
      },
      "returns": {
        "approved": {
          "type": "boolean"
        },
        "confidence": {
          "type": "number",
          "constraints": { "minimum": 0, "maximum": 1 }
        },
        "reason": {
          "type": "string"
        }
      },
      "timeout": "10s",
      "maxRetries": 2
    }
  ],

  "deployment": {
    "type": "docker",
    "image": "registry.acme.com/agents/approval-agent:1.0.0",
    "port": 9001,
    "resources": {
      "memory": "512Mi",
      "cpu": "250m"
    }
  },

  "metrics": {
    "successRate": 0.985,
    "avgLatency": 250,
    "p50Latency": 180,
    "p99Latency": 450,
    "uptime": 0.9998,
    "costPerInvocation": 0.01,
    "invocationsThisMonth": 12450
  },

  "reputation": {
    "score": 4.8,
    "reviews": 120,
    "trustLevel": "high"
  }
}
```

**Response (404 Not Found)**
```json
{
  "error": "AGENT_NOT_FOUND",
  "message": "Agent not found: approval-agent",
  "timestamp": "2026-02-21T14:30:45Z"
}
```

### List All Agents

Returns all registered agents in the marketplace.

**Request**
```
GET /agents
```

**Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| status | string | No | Filter by status: "active", "degraded", "offline" |
| limit | integer | No | Max results (default: 100) |
| offset | integer | No | Pagination offset (default: 0) |

**Response (200 OK)**
```json
{
  "agents": [
    {
      "id": "approval-agent",
      "name": "Approval Agent",
      "version": "1.0.0",
      "status": "active",
      "successRate": 0.985
    },
    {
      "id": "validation-agent",
      "name": "Validation Agent",
      "version": "1.0.0",
      "status": "active",
      "successRate": 0.990
    }
  ],
  "total": 2,
  "limit": 100,
  "offset": 0,
  "timestamp": "2026-02-21T14:30:45Z"
}
```

---

## Agent Health

### Check Agent Health

Determines if an agent is healthy and responsive.

**Request**
```
GET /agents/{agentId}/health
```

**Parameters**

| Parameter | Type | Location | Required |
|-----------|------|----------|----------|
| agentId | string | path | Yes |

**Response (200 OK)**
```json
{
  "agentId": "approval-agent",
  "status": "healthy",
  "latency": 45,
  "checkedAt": "2026-02-21T14:30:45Z",
  "details": {
    "containerRunning": true,
    "portAccessible": true,
    "healthEndpointResponse": 200,
    "dependencies": {
      "approvals-db": "connected",
      "skills-api": "connected"
    }
  }
}
```

**Response (503 Service Unavailable)**
```json
{
  "agentId": "approval-agent",
  "status": "offline",
  "latency": null,
  "checkedAt": "2026-02-21T14:30:45Z",
  "details": {
    "containerRunning": false,
    "reason": "Container exited with code 1"
  }
}
```

### Get Agent Metrics

Returns performance metrics for an agent.

**Request**
```
GET /agents/{agentId}/metrics
```

**Response (200 OK)**
```json
{
  "agentId": "approval-agent",
  "period": "30d",
  "metrics": {
    "successRate": {
      "value": 0.985,
      "trend": "stable",
      "change": 0.005,
      "samples": 12450
    },
    "latency": {
      "p50": 180,
      "p99": 450,
      "p999": 850,
      "avg": 250,
      "trend": "improving"
    },
    "uptime": {
      "value": 0.9998,
      "downtime": 86,
      "incidents": 1
    },
    "cost": {
      "perInvocation": 0.01,
      "thisMonth": 124.50,
      "trend": "stable"
    }
  },
  "timestamp": "2026-02-21T14:30:45Z"
}
```

---

## Orchestration

### Deploy Orchestration Template

Compiles and deploys an orchestration template.

**Request**
```
POST /orchestrate/deploy
Content-Type: application/json

{
  "template": {
    "apiVersion": "orchestration/v1",
    "kind": "OrchestrationTemplate",
    "metadata": {
      "id": "approval-workflow",
      "name": "Approval Workflow",
      "version": "1.0.0"
    },
    "spec": {
      "pattern": "sequential",
      "agents": [
        {
          "id": "validator",
          "agentId": "validation-agent",
          "capability": "validate-expense",
          "dependsOn": [],
          "timeout": "10s"
        },
        {
          "id": "approver",
          "agentId": "approval-agent",
          "capability": "approve-expense",
          "dependsOn": ["validator"],
          "timeout": "30s"
        }
      ]
    }
  }
}
```

**Response (200 OK)**
```json
{
  "templateId": "approval-workflow",
  "workflowId": "approval-workflow-yawl",
  "status": "deployed",
  "agents": [
    {
      "id": "validator",
      "agentId": "validation-agent",
      "status": "ready"
    },
    {
      "id": "approver",
      "agentId": "approval-agent",
      "status": "ready"
    }
  ],
  "deployedAt": "2026-02-21T14:30:45Z",
  "specUrl": "http://localhost:8080/orchestrate/approval-workflow/spec",
  "timestamp": "2026-02-21T14:30:45Z"
}
```

**Response (400 Bad Request)**
```json
{
  "error": "INVALID_TEMPLATE",
  "message": "Circular dependency detected: approver → validator → approver",
  "timestamp": "2026-02-21T14:30:45Z"
}
```

**Response (404 Not Found)**
```json
{
  "error": "AGENT_NOT_FOUND",
  "message": "Agent not found in registry: unknown-agent",
  "timestamp": "2026-02-21T14:30:45Z"
}
```

### Compile Template (No Deploy)

Validates and compiles a template without deploying agents.

**Request**
```
POST /orchestrate/compile
Content-Type: application/json

{
  "template": { ... }
}
```

**Response (200 OK)**
```json
{
  "templateId": "approval-workflow",
  "status": "valid",
  "workflowSpec": {
    "id": "approval-workflow-yawl",
    "nets": [...],
    "decompositions": [...],
    "dataTypes": [...]
  },
  "compilationTimeMs": 45,
  "timestamp": "2026-02-21T14:30:45Z"
}
```

### Get Template

Retrieves a deployed template.

**Request**
```
GET /orchestrate/{templateId}
```

**Response (200 OK)**
```json
{
  "id": "approval-workflow",
  "name": "Approval Workflow",
  "version": "1.0.0",
  "status": "deployed",
  "pattern": "sequential",
  "agents": [...],
  "deployedAt": "2026-02-21T14:30:45Z",
  "executionCount": 150,
  "avgExecutionTime": 5000,
  "successRate": 0.98
}
```

### List Templates

Returns all orchestration templates.

**Request**
```
GET /orchestrate
```

**Response (200 OK)**
```json
{
  "templates": [
    {
      "id": "approval-workflow",
      "name": "Approval Workflow",
      "version": "1.0.0",
      "pattern": "sequential",
      "agentCount": 2,
      "deployedAt": "2026-02-21T14:30:45Z",
      "executionCount": 150
    }
  ],
  "total": 1,
  "timestamp": "2026-02-21T14:30:45Z"
}
```

---

## Errors

### Standard Error Response

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": "2026-02-21T14:30:45Z",
  "details": {
    "field": "capability",
    "reason": "not found"
  }
}
```

### Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| AGENT_NOT_FOUND | 404 | Agent not found in registry |
| INVALID_CAPABILITY | 400 | Capability not found |
| INVALID_TEMPLATE | 400 | Template syntax or logic error |
| CIRCULAR_DEPENDENCY | 400 | Template has circular agent dependency |
| AGENT_OFFLINE | 503 | Agent is not responding to health checks |
| DISCOVERY_TIMEOUT | 504 | Discovery query exceeded timeout |
| UNAUTHORIZED | 401 | Missing or invalid authentication |
| RATE_LIMITED | 429 | Rate limit exceeded |
| INTERNAL_ERROR | 500 | Internal server error |

---

## Rate Limiting

Rate limits are applied per client (by API key or IP).

**Default Limits**:
- Discovery: 1000 requests/hour
- Profile: 5000 requests/hour
- Orchestration: 100 requests/hour

**Headers**:
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1645457445
```

**Response (429 Too Many Requests)**
```json
{
  "error": "RATE_LIMITED",
  "message": "Discovery limit exceeded: 1000/hour",
  "retryAfter": 3600,
  "timestamp": "2026-02-21T14:30:45Z"
}
```

---

## Authentication (Future)

Authentication via JWT (Phase 2):

```
Authorization: Bearer <jwt_token>
```

Required for production deployment.

---

## Example Workflows

### Workflow 1: Discover and Profile

```bash
# 1. Discover agents that can approve
curl -X POST "http://localhost:8080/agents/discover" \
  -H "Content-Type: application/json" \
  -d '{
    "capability": "approve-expense",
    "minSuccessRate": 0.95
  }'

# 2. Get full profile of top agent
curl "http://localhost:8080/agents/approval-agent/profile"

# 3. Check health
curl "http://localhost:8080/agents/approval-agent/health"
```

### Workflow 2: Deploy Orchestration

```bash
# 1. Compile template
curl -X POST "http://localhost:8080/orchestrate/compile" \
  -H "Content-Type: application/json" \
  -d @approval-workflow.json

# 2. Deploy template
curl -X POST "http://localhost:8080/orchestrate/deploy" \
  -H "Content-Type: application/json" \
  -d @approval-workflow.json

# 3. Monitor template execution
curl "http://localhost:8080/orchestrate/approval-workflow"
```

---

## Implementation Notes

### Week 2-3: REST Controller

Will be implemented in `org.yawlfoundation.yawl.integration.marketplace.api`:
- `DiscoveryController.java` (discovery endpoints)
- `AgentProfileController.java` (profile endpoints)
- `HealthController.java` (health endpoints)
- `OrchestratorController.java` (orchestration endpoints)

### Response Times (Targets)

| Endpoint | Target | Notes |
|----------|--------|-------|
| POST /agents/discover | <100ms p99 | SPARQL index query |
| GET /agents/{id}/profile | <50ms | Memory cache lookup |
| GET /agents/{id}/health | <5s | HTTP health check |
| POST /orchestrate/compile | <200ms | DAG compilation |
| POST /orchestrate/deploy | <1s | Compilation + agent deployment |

### Error Handling

All endpoints return consistent error responses:
- 4xx: Client error (invalid input)
- 5xx: Server error (internal failure)
- Always include: error code, message, timestamp

### Versioning

API version is optional in requests. Default: v1

```
GET /agents/v1/discovery  (explicit)
GET /agents/discovery     (uses default v1)
```

Phase 2 can add v2 endpoints without breaking v1.

---

## OpenAPI/Swagger Generation

Generate from annotations at runtime:

```bash
# Swagger UI: http://localhost:8080/swagger-ui.html
# OpenAPI spec: http://localhost:8080/v3/api-docs
```

**For development**: Use Springdoc-OpenAPI (auto-generation from REST controllers).

---

## Summary

**Core endpoints** (8 total):
1. POST /agents/discover (search by capability)
2. GET /agents (list all)
3. GET /agents/{id}/profile (full metadata)
4. GET /agents/{id}/health (liveness check)
5. GET /agents/{id}/metrics (performance)
6. POST /orchestrate/compile (validate template)
7. POST /orchestrate/deploy (deploy + start agents)
8. GET /orchestrate/{id} (template status)

**Response time targets**:
- Discovery: <100ms p99
- Profile: <50ms
- Orchestration: <200ms (compile), <1s (deploy)

**Error handling**: Consistent JSON responses with error codes, suitable for client-side handling.

---

**Status**: Ready for implementation in Week 2-3
**Next**: Code REST controllers + integration tests
