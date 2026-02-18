# YAWL A2A Server Guide

**Agent-to-Agent communication for autonomous workflow orchestration**

## Architecture Overview

```
+------------------------------------------------------------------+
|                     Agent Orchestrator                            |
|  +-------------+  +-------------+  +-------------+               |
|  |  Manager    |  | Coordinator |  | Task Router |               |
|  |  Agent      |  |  Agent      |  |             |               |
|  +------+------+  +------+------+  +------+------+               |
|         |                |                |                      |
+---------+----------------+----------------+----------------------+
          | A2A Protocol (JSON-RPC 2.0)
+---------v--------------------------------------------------------+
|                     YAWL A2A Server                               |
|  +------------------------------------------------------------+  |
|  |                    Skills Registry                          |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |  |/yawl-workflow| |/yawl-task  |  |/yawl-spec  |         |  |
|  |  +-------------+  +-------------+  +-------------+         |  |
|  |  +-------------+                                             |  |
|  |  |/yawl-monitor|                                             |  |
|  |  +-------------+                                             |  |
|  +------------------------------------------------------------+  |
+------------------------------------------------------------------+
          |
+---------v--------------------------------------------------------+
|                     YAWL Engine (Interface B)                     |
+------------------------------------------------------------------+
```

## Quick Start (3 Commands)

```bash
# 1. Set environment variables
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=your_password
export YAWL_A2A_API_KEY=your-secure-api-key-here

# 2. Build the A2A server
mvn -pl yawl-integration clean package -DskipTests

# 3. Start the A2A server
java -jar yawl-integration/target/yawl-a2a-server.jar
```

Expected output:
```
Starting YAWL A2A Server v6.0.0
Engine URL: http://localhost:8080/yawl
A2A Port: 9090
Authentication: API Key required
Connected to YAWL engine
YAWL A2A Server v6.0.0 started on port 9090
Skills registered: /yawl-workflow, /yawl-task, /yawl-spec, /yawl-monitor
```

## Agent Discovery

### Well-Known Endpoint

Agents discover the A2A server via `/.well-known/agent.json`:

```bash
curl http://localhost:9090/.well-known/agent.json
```

Response:
```json
{
  "agent": {
    "name": "YAWL A2A Server",
    "version": "6.0.0",
    "description": "YAWL workflow orchestration via A2A protocol",
    "url": "http://localhost:9090",
    "capabilities": {
      "skills": [
        "/yawl-workflow",
        "/yawl-task",
        "/yawl-spec",
        "/yawl-monitor"
      ],
      "authentication": {
        "schemes": ["bearer"]
      }
    }
  }
}
```

## Authentication

### API Key Authentication

All requests require a Bearer token:

```http
POST /a2a/skill HTTP/1.1
Host: localhost:9090
Authorization: Bearer your-secure-api-key-here
Content-Type: application/json
```

### Generate Secure API Key

```bash
# Generate a secure API key
openssl rand -hex 32
# Output: 1a2b3c4d5e6f... (64 hex characters)
```

### Configure API Keys

```yaml
# a2a-config.yml
yawl:
  a2a:
    enabled: true
    port: 9090
    security:
      auth-required: true
      api-keys:
        - ${YAWL_A2A_API_KEY}
        - ${AGENT_API_KEY_2}
      rate-limit:
        requests-per-minute: 100
```

## Skills Reference

### /yawl-workflow

Manage complete workflow lifecycle.

| Operation | Description | Parameters |
|-----------|-------------|------------|
| `launch` | Start a new case | `specIdentifier`, `caseData` |
| `status` | Get case status | `caseId` |
| `data` | Get/set case data | `caseId`, `data` (optional) |
| `suspend` | Suspend a case | `caseId` |
| `resume` | Resume a case | `caseId` |
| `cancel` | Cancel a case | `caseId` |

**Example: Launch Case**

```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "method": "skill.invoke",
  "params": {
    "skill": "/yawl-workflow",
    "operation": "launch",
    "arguments": {
      "specIdentifier": "OrderProcessing",
      "caseData": {
        "orderId": "ORD-12345",
        "customerId": "CUST-001",
        "amount": 1500.00
      }
    }
  }
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "req-001",
  "result": {
    "caseId": "42.0",
    "status": "running",
    "specIdentifier": "OrderProcessing",
    "launchedAt": "2026-02-18T10:30:00Z"
  }
}
```

### /yawl-task

Handle work item execution.

| Operation | Description | Parameters |
|-----------|-------------|------------|
| `list` | List available work items | `caseId` (optional) |
| `checkout` | Claim a work item | `workItemId` |
| `complete` | Complete with output | `workItemId`, `outputData` |
| `skip` | Skip optional task | `workItemId` |
| `delegate` | Delegate to agent | `workItemId`, `delegateTo` |

**Example: Complete Work Item**

```json
{
  "jsonrpc": "2.0",
  "id": "req-002",
  "method": "skill.invoke",
  "params": {
    "skill": "/yawl-task",
    "operation": "complete",
    "arguments": {
      "workItemId": "42.0:ReviewOrder",
      "outputData": {
        "approved": true,
        "reviewerNotes": "All checks passed",
        "reviewedAt": "2026-02-18T10:35:00Z"
      }
    }
  }
}
```

### /yawl-spec

Manage workflow specifications.

| Operation | Description | Parameters |
|-----------|-------------|------------|
| `list` | List loaded specs | - |
| `get` | Get spec XML | `specIdentifier`, `version` (optional) |
| `upload` | Upload new spec | `specXml`, `validate` (optional) |
| `validate` | Validate without loading | `specXml` |
| `unload` | Remove spec | `specIdentifier`, `version` |

**Example: List Specifications**

```json
{
  "jsonrpc": "2.0",
  "id": "req-003",
  "method": "skill.invoke",
  "params": {
    "skill": "/yawl-spec",
    "operation": "list",
    "arguments": {}
  }
}
```

### /yawl-monitor

Monitor engine health and metrics.

| Operation | Description | Parameters |
|-----------|-------------|------------|
| `health` | Engine health check | - |
| `metrics` | Performance metrics | - |
| `events` | Subscribe to events | `eventType`, `callbackUrl` |
| `throughput` | Case throughput stats | `period` (optional) |

**Example: Health Check**

```json
{
  "jsonrpc": "2.0",
  "id": "req-004",
  "method": "skill.invoke",
  "params": {
    "skill": "/yawl-monitor",
    "operation": "health",
    "arguments": {}
  }
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "req-004",
  "result": {
    "status": "healthy",
    "engine": {
      "uptime": "5d 12h 30m",
      "activeCases": 42,
      "loadedSpecs": 15,
      "version": "6.0.0"
    },
    "database": {
      "status": "connected",
      "latencyMs": 5
    }
  }
}
```

## Message Protocol (JSON-RPC 2.0)

### Request Format

```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "method": "skill.invoke",
  "params": {
    "skill": "/yawl-workflow",
    "operation": "launch",
    "arguments": {
      "key": "value"
    }
  }
}
```

### Success Response

```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "result": {
    // Operation result
  }
}
```

### Error Response

```json
{
  "jsonrpc": "2.0",
  "id": "unique-request-id",
  "error": {
    "code": -32602,
    "message": "Invalid params",
    "data": {
      "field": "specIdentifier",
      "reason": "required field missing"
    }
  }
}
```

### Error Codes

| Code | Message | Description |
|------|---------|-------------|
| -32700 | Parse error | Invalid JSON |
| -32600 | Invalid Request | Missing required fields |
| -32601 | Method not found | Unknown method |
| -32602 | Invalid params | Missing or invalid parameters |
| -32603 | Internal error | Server-side error |
| -32001 | Skill not found | Unknown skill name |
| -32002 | Operation invalid | Operation not supported |
| -32003 | Auth failed | Invalid or missing API key |
| -32004 | Engine error | YAWL engine error |

## Client Implementation

### Java Client

```java
public class A2AClient {

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public A2AClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    public String invokeSkill(String skill, String operation, Map<String, Object> args) {
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", UUID.randomUUID().toString(),
            "method", "skill.invoke",
            "params", Map.of(
                "skill", skill,
                "operation", operation,
                "arguments", args
            )
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/a2a/skill"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(request)))
            .build();

        HttpResponse<String> response = httpClient.send(
            httpRequest, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
```

### Python Client

```python
import requests
import json
import uuid

class A2AClient:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url
        self.api_key = api_key
        self.headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }

    def invoke_skill(self, skill: str, operation: str, arguments: dict) -> dict:
        request = {
            "jsonrpc": "2.0",
            "id": str(uuid.uuid4()),
            "method": "skill.invoke",
            "params": {
                "skill": skill,
                "operation": operation,
                "arguments": arguments
            }
        }

        response = requests.post(
            f"{self.base_url}/a2a/skill",
            headers=self.headers,
            json=request
        )

        result = response.json()
        if "error" in result:
            raise Exception(result["error"]["message"])
        return result["result"]

# Usage
client = A2AClient("http://localhost:9090", "your-api-key")
case = client.invoke_skill("/yawl-workflow", "launch", {
    "specIdentifier": "OrderProcessing",
    "caseData": {"orderId": "ORD-123"}
})
print(f"Launched case: {case['caseId']}")
```

## Event Subscription

### Subscribe to Workflow Events

```json
{
  "jsonrpc": "2.0",
  "id": "req-005",
  "method": "skill.invoke",
  "params": {
    "skill": "/yawl-monitor",
    "operation": "events",
    "arguments": {
      "eventType": "case_completed",
      "callbackUrl": "http://my-agent:8080/events"
    }
  }
}
```

### Event Payload

```json
{
  "event": "case_completed",
  "timestamp": "2026-02-18T10:45:00Z",
  "data": {
    "caseId": "42.0",
    "specIdentifier": "OrderProcessing",
    "durationMs": 900000,
    "status": "completed"
  }
}
```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `YAWL_ENGINE_URL` | Yes | - | YAWL engine URL |
| `YAWL_USERNAME` | Yes | - | YAWL username |
| `YAWL_PASSWORD` | Yes | - | YAWL password |
| `YAWL_A2A_PORT` | No | `9090` | A2A server port |
| `YAWL_A2A_API_KEY` | Yes | - | Primary API key |
| `YAWL_A2A_MAX_AGENTS` | No | `100` | Max concurrent agents |
| `YAWL_A2A_TIMEOUT_MS` | No | `30000` | Request timeout |

### application.yml

```yaml
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
        - ${YAWL_A2A_API_KEY}
      rate-limit:
        requests-per-minute: 100
    retry:
      max-attempts: 3
      backoff-ms: 1000
```

## Monitoring

### Health Endpoint

```bash
curl http://localhost:9090/health
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
curl http://localhost:9090/metrics
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

## Troubleshooting

### Authentication Failed

**Error**: `{"error": {"code": -32003, "message": "Auth failed"}}`

**Solutions**:
1. Verify API key is correct
2. Check Authorization header format: `Bearer <key>`
3. Ensure API key is in configuration

### Skill Not Found

**Error**: `{"error": {"code": -32001, "message": "Skill not found"}}`

**Solutions**:
1. Check skill name spelling (include leading `/`)
2. Verify skill is registered in configuration
3. Check server logs for skill loading errors

### Connection Refused

**Error**: Connection refused to localhost:9090

**Solutions**:
1. Verify A2A server is running
2. Check port is not blocked by firewall
3. Verify YAWL_A2A_PORT matches actual port

## Version Compatibility

| YAWL A2A Server | YAWL Engine | Java | Protocol |
|-----------------|-------------|------|----------|
| 6.0.0 | 6.0+ | 25+ | JSON-RPC 2.0 |

## References

- [A2A Protocol Specification](https://google.github.io/A2A/)
- [JSON-RPC 2.0 Specification](https://www.jsonrpc.org/specification)
- [MCP Server Guide](MCP-SERVER-GUIDE.md)
- [YAWL Documentation](https://yawlfoundation.github.io)
