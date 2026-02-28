# Tutorial: YAWL MCP/A2A Getting Started

By the end of this tutorial you will have the YAWL MCP/A2A application running, understand how AI agents integrate with YAWL via Model Context Protocol (MCP) and Agent-to-Agent (A2A) protocols, and execute workflow tasks through an autonomous agent.

---

## What is MCP/A2A?

The YAWL MCP/A2A Application is a Spring Boot service that enables autonomous AI agents to interact with YAWL workflows through two protocols:

### Model Context Protocol (MCP)
Allows language models (Claude, GPT, etc.) to:
- List available YAWL tools (case creation, task execution)
- Query workflow specifications and case state
- Execute workflow operations with natural language understanding

### Agent-to-Agent (A2A) Protocol
Enables peer-to-peer agent communication:
- Agent-initiated service discovery
- Asynchronous message passing
- Workflow delegation between agents
- Event-driven callbacks

---

## Key Use Cases

1. **AI-Powered Task Execution**: Use LLMs to intelligently decide on task outcomes
2. **Workflow Automation**: Trigger workflows based on external events via A2A
3. **Intelligent Case Routing**: Route cases to appropriate agents based on ML models
4. **Process Mining Integration**: Feed workflow data to ML pipelines for predictions

---

## Prerequisites

```bash
java -version
```

Expected: Java 25+ (JDK required).

```bash
mvn -version
```

Expected: Maven 3.9+.

A running YAWL Engine Webapp:

```bash
curl -I http://localhost:8080/yawl-engine
# Expected: HTTP/1.1 200 OK
```

API key for language model (Claude API, OpenAI, etc.):

```bash
export ANTHROPIC_API_KEY="sk-ant-v0-..."
# or
export OPENAI_API_KEY="sk-..."
```

---

## Step 1: Build the MCP/A2A Application

Build the Spring Boot application:

```bash
cd /path/to/yawl
mvn clean package -DskipTests -T 1.5C -pl yawl-mcp-a2a-app
```

The executable JAR is created at:

```
yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-GA.jar
```

---

## Step 2: Configure Application Properties

Create `application.yml` in the working directory:

```yaml
spring:
  application:
    name: yawl-mcp-a2a
  config:
    activate:
      on-profile: default

# Server Configuration
server:
  port: 8888
  servlet:
    context-path: /api
  shutdown: graceful

# YAWL Engine Configuration
yawl:
  engine:
    url: http://localhost:8080/yawl-engine
    username: admin
    password: YAWL
    reconnect-interval: 30s
    connection-timeout: 10s

# MCP Server Configuration
mcp:
  server:
    enabled: true
    port: 3000
    name: YAWL-MCP-Server
    version: 1.0.0
  tools:
    enabled: true
    auto-discover: true

# A2A Configuration
a2a:
  server:
    enabled: true
    port: 8889
    bind-address: 0.0.0.0
  discovery:
    enabled: true
    registry-url: http://agent-registry:8090
    announce-interval: 60s

# Logging
logging:
  level:
    root: INFO
    org.yawlfoundation.yawl: DEBUG
    org.springframework: INFO
  file:
    name: logs/yawl-mcp-a2a.log
    max-size: 10MB
    max-history: 10
```

---

## Step 3: Run the Application

Start the application with configuration:

```bash
java -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-GA.jar \
  --spring.config.location=file:./application.yml
```

Expected startup output:

```
[INFO] YAWL MCP/A2A Application v6.0.0-GA
[INFO] Spring Boot version: 3.x.x
[INFO] Starting on Java 25
[INFO] Connecting to YAWL Engine at http://localhost:8080/yawl-engine...
[INFO] Connected successfully. Engine version: 6.0.0-GA
[INFO] MCP Server listening on port 3000
[INFO] A2A Server listening on port 8889
[INFO] Application ready in X.XXX seconds
```

---

## Step 4: Verify MCP Server is Running

The MCP server is now available for language model integration:

```bash
# List available MCP tools
curl http://localhost:8888/api/mcp/tools

# Expected response:
# [
#   {"name": "create_case", "description": "Create a new workflow case", ...},
#   {"name": "complete_task", "description": "Complete a work item", ...},
#   ...
# ]
```

---

## Step 5: Connect Claude (or other LLM) to MCP Server

### Using Claude Desktop Client

Create `~/.claude/resources/config.json`:

```json
{
  "mcpServers": {
    "yawl": {
      "command": "http",
      "url": "http://localhost:8888/api/mcp",
      "auth": {
        "type": "bearer",
        "token": "your-bearer-token"
      }
    }
  }
}
```

Or using environment variables in Claude:

```bash
export MCP_YAWL_URL=http://localhost:8888/api/mcp
export MCP_YAWL_AUTH_TOKEN=<your-token>
```

### Using Claude API Directly

```python
import anthropic
import json

client = anthropic.Anthropic()

# Available tools via MCP
response = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    max_tokens=1024,
    tools=[
        {
            "name": "create_case",
            "description": "Create a new workflow case in YAWL",
            "input_schema": {
                "type": "object",
                "properties": {
                    "spec_uri": {"type": "string"},
                    "case_label": {"type": "string"},
                    "input_data": {"type": "string", "description": "XML data"}
                },
                "required": ["spec_uri", "case_label"]
            }
        }
    ],
    messages=[
        {
            "role": "user",
            "content": "Create a workflow case for invoice processing"
        }
    ]
)

print(response.content)
```

---

## Step 6: Execute a Workflow Task via Agent

Create a Python script to demonstrate agent-driven task execution:

```python
#!/usr/bin/env python3

import requests
import json

MCP_SERVER_URL = "http://localhost:8888/api"
YAWL_ENGINE_URL = "http://localhost:8080/yawl-engine"

def list_tools():
    """Fetch available MCP tools"""
    response = requests.get(f"{MCP_SERVER_URL}/mcp/tools")
    return response.json()

def call_tool(tool_name, **kwargs):
    """Call an MCP tool"""
    payload = {
        "tool": tool_name,
        "input": kwargs
    }
    response = requests.post(f"{MCP_SERVER_URL}/mcp/call", json=payload)
    return response.json()

def main():
    print("Available tools:")
    tools = list_tools()
    for tool in tools:
        print(f"  - {tool['name']}: {tool['description']}")

    # Create a case
    print("\nCreating a test case...")
    result = call_tool(
        "create_case",
        spec_uri="TestProcess",
        case_label="AI-Generated-Case-001",
        input_data="<data></data>"
    )
    print(f"Case created: {result}")

    # Get enabled work items
    case_id = result['caseID']
    print(f"\nFetching enabled tasks for case {case_id}...")
    tasks = call_tool("get_enabled_work_items", case_id=case_id)
    print(f"Enabled tasks: {tasks}")

    # Complete a task
    if tasks:
        task = tasks[0]
        print(f"\nCompleting task: {task['taskName']}...")
        complete_result = call_tool(
            "complete_work_item",
            work_item_id=task['workItemID'],
            output_data="<output>Task completed by AI agent</output>"
        )
        print(f"Task completed: {complete_result}")

if __name__ == "__main__":
    main()
```

Run the script:

```bash
python3 agent-task-execution.py
```

Expected output:

```
Available tools:
  - create_case: Create a new workflow case in YAWL
  - get_enabled_work_items: Get enabled work items for a case
  - complete_work_item: Complete a work item
  ...

Creating a test case...
Case created: {'caseID': 'case-12345', 'specURI': 'TestProcess', ...}

Fetching enabled tasks for case case-12345...
Enabled tasks: [{'taskName': 'Process Application', 'workItemID': 'item-67890', ...}]

Completing task: Process Application...
Task completed: {'success': true, 'timestamp': '2026-02-28T14:30:00Z'}
```

---

## Step 7: Enable A2A Agent-to-Agent Communication

### 7.1: Configure Agent Service

In `application.yml`, configure A2A service:

```yaml
a2a:
  server:
    enabled: true
    port: 8889
    host: agent-1.example.com
    bind-address: 0.0.0.0

  discovery:
    enabled: true
    registry-url: http://agent-registry:8090
    service-name: yawl-workflow-agent
    announce-interval: 60s

  security:
    enabled: true
    auth-type: bearer
    token: ${A2A_AUTH_TOKEN}
```

### 7.2: Register with Agent Registry

```bash
curl -X POST http://agent-registry:8090/agents/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "yawl-workflow-agent",
    "url": "http://agent-1.example.com:8889",
    "capabilities": [
      "execute_workflow",
      "query_case_state",
      "complete_task"
    ],
    "version": "1.0.0"
  }'
```

### 7.3: Send Message from Another Agent

```python
import requests

def send_a2a_message(target_agent, message):
    """Send A2A message to another agent"""
    response = requests.post(
        f"http://agent-registry:8090/agents/{target_agent}/message",
        json={
            "sender": "process-mining-agent",
            "action": "execute_workflow",
            "payload": message
        }
    )
    return response.json()

# Request another agent to execute a workflow
result = send_a2a_message(
    "yawl-workflow-agent",
    {
        "spec_uri": "InvoiceProcessing",
        "case_label": "INV-12345",
        "urgent": True
    }
)

print(f"Workflow execution result: {result}")
```

---

## Step 8: Monitor Agent Activity

Access the monitoring dashboard:

```
http://localhost:8888/api/actuator
```

Key endpoints:

```bash
# Health check
curl http://localhost:8888/api/actuator/health

# Active agents
curl http://localhost:8888/api/agents/connected

# MCP tool metrics
curl http://localhost:8888/api/actuator/metrics/mcp.tool.invocations

# A2A message statistics
curl http://localhost:8888/api/actuator/metrics/a2a.messages.sent
```

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│  Language Model (Claude, GPT, etc.)                      │
└────────────┬─────────────────────────────────────────────┘
             │
             │ (HTTP Client → MCP Protocol)
             ↓
┌────────────────────────────────────────────────────────────┐
│  YAWL MCP/A2A Application (Spring Boot)                   │
├────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────┐  │
│ │  MCP Server                                          │  │
│ │  - Tool discovery                                    │  │
│ │  - Tool invocation                                   │  │
│ │  - Result marshalling                                │  │
│ └──────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────┐  │
│ │  A2A Server                                          │  │
│ │  - Agent registration                                │  │
│ │  - Message routing                                   │  │
│ │  - Event callbacks                                   │  │
│ └──────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────┐  │
│ │  YAWL Integration Layer                              │  │
│ │  - Case management                                   │  │
│ │  - Task execution                                    │  │
│ │  - Event notification                                │  │
│ └──────────────────────────────────────────────────────┘  │
│ ┌──────────────────────────────────────────────────────┐  │
│ │  Observability & Monitoring                          │  │
│ │  - Prometheus metrics                                │  │
│ │  - Distributed tracing                               │  │
│ │  - Structured logging                                │  │
│ └──────────────────────────────────────────────────────┘  │
└────────────┬─────────────────────────────────────────────┬──┘
             │                                             │
             ↓                                             ↓
    ┌────────────────────┐                   ┌─────────────────────┐
    │  YAWL Engine       │                   │  Agent Registry     │
    │  (Workflow Core)   │                   │  (Discovery Service)│
    └────────────────────┘                   └─────────────────────┘
             │
             ↓
    ┌────────────────────┐
    │  PostgreSQL        │
    │  (Case Storage)    │
    └────────────────────┘
```

---

## Configuration Options

### MCP Tool Filtering

Only expose certain tools to language models:

```yaml
mcp:
  tools:
    enabled: true
    included-tools:
      - create_case
      - get_case_state
      - complete_work_item
      - query_specifications
```

### A2A Message Encryption

Enable TLS for A2A communication:

```yaml
a2a:
  security:
    tls:
      enabled: true
      key-store: /etc/yawl/keystore.p12
      key-store-password: ${KEYSTORE_PASSWORD}
      trust-store: /etc/yawl/truststore.p12
```

### Rate Limiting

Protect the service from agent abuse:

```yaml
management:
  endpoints:
    ratelimit:
      enabled: true
      max-requests: 1000
      window-size: 60s
```

---

## Integration with Process Mining

Use A2A to send workflow data to ML pipelines:

```python
def notify_process_mining_agent(case_id, case_data):
    """Send case data to process mining agent for prediction"""
    message = {
        "case_id": case_id,
        "events": case_data['events'],
        "requested_analysis": "predict_remaining_time"
    }

    response = requests.post(
        "http://agent-registry:8090/agents/process-mining/message",
        json=message
    )

    # Process mining agent returns prediction
    prediction = response.json()
    return prediction['estimated_time']
```

---

## Performance Tuning

### Increase Connection Pool Size

```yaml
yawl:
  engine:
    connection-pool-size: 20
    queue-capacity: 100
```

### Enable MCP Tool Caching

```yaml
mcp:
  cache:
    enabled: true
    ttl: 300s  # 5 minutes
    max-size: 1000
```

### A2A Message Batching

```yaml
a2a:
  batching:
    enabled: true
    batch-size: 10
    flush-interval: 1s
```

---

## Troubleshooting

### MCP Server Not Accessible

**Problem**: `curl http://localhost:8888/api/mcp/tools` returns 404

**Solution**: Verify MCP is enabled in config:

```yaml
mcp:
  server:
    enabled: true
```

And check logs:

```bash
tail -f logs/yawl-mcp-a2a.log | grep MCP
```

### Agent Cannot Connect to YAWL Engine

**Problem**: "Connection refused" error in logs

**Solution**: Verify engine is running and URL is correct:

```bash
curl -I http://localhost:8080/yawl-engine
```

### A2A Discovery Not Working

**Problem**: Agents cannot find each other

**Solution**: Verify registry is running and accessible:

```bash
curl http://agent-registry:8090/health
```

---

## Security Considerations

### API Key Management

Store secrets in environment variables or secret manager:

```bash
export YAWL_ENGINE_PASSWORD=$(aws secretsmanager get-secret-value --secret-id yawl/engine --query 'SecretString' --output text)
export A2A_AUTH_TOKEN=$(vault kv get -field=token secret/yawl/a2a)
```

### HTTPS/TLS Configuration

```yaml
server:
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
```

---

## Next Steps

1. **Deploy to Production**: [how-to/deployment/mcp-a2a-production.md](../how-to/deployment/mcp-a2a-production.md)
2. **Integrate Multiple Agents**: [how-to/integration/multi-agent-orchestration.md](../how-to/integration/multi-agent-orchestration.md)
3. **Enable Process Mining**: [how-to/integration/process-mining.md](../how-to/integration/process-mining.md)
4. **Custom Tool Development**: [how-to/mcp-custom-tools.md](../how-to/mcp-custom-tools.md)

---

## Further Reading

- **[MCP/A2A Reference](../reference/mcp-a2a-reference.md)**: Complete API documentation
- **[Autonomous Agents Architecture](../explanation/autonomous-agents.md)**: Design patterns
- **[MCP Protocol Specification](../reference/mcp-specification.md)**: Protocol details
