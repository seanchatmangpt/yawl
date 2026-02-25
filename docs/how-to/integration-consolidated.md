# YAWL Integration Guide

**Version:** 6.0.0
**Last Updated:** 2026-02-16
**Target Audience:** Integration developers, AI engineers, system integrators

---

## Table of Contents

1. [Overview](#overview)
2. [MCP Server Integration](#mcp-server-integration)
3. [A2A Protocol Integration](#a2a-protocol-integration)
4. [Interface B Client Integration](#interface-b-client-integration)
5. [Z.AI API Integration](#zai-api-integration)
6. [Authentication and Security](#authentication-and-security)
7. [Integration Patterns](#integration-patterns)
8. [Troubleshooting](#troubleshooting)

---

## Overview

YAWL provides multiple integration points for connecting with external systems, AI models, and agent frameworks.

### Integration Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      YAWL Core Engine                       │
├─────────────────────────────────────────────────────────────┤
│  Integration Layer (org.yawlfoundation.yawl.integration)    │
│  ┌─────────────────────┐  ┌──────────────────────────────┐ │
│  │  MCP Integration    │  │  A2A Integration             │ │
│  │  ┌───────────────┐  │  │  ┌────────────────────────┐ │ │
│  │  │ MCP Server    │  │  │  │ A2A Server             │ │ │
│  │  │ (Port 3000)   │  │  │  │ (Port 8080)            │ │ │
│  │  ├───────────────┤  │  │  ├────────────────────────┤ │ │
│  │  │ MCP Client    │  │  │  │ A2A Client             │ │ │
│  │  └───────────────┘  │  │  └────────────────────────┘ │ │
│  └─────────────────────┘  └──────────────────────────────┘ │
│  ┌─────────────────────┐  ┌──────────────────────────────┐ │
│  │  Interface B        │  │  Z.AI Integration            │ │
│  │  REST API           │  │  Cloud API Client            │ │
│  └─────────────────────┘  └──────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### Supported Integration Types

| Integration | Purpose | Transport | Port |
|------------|---------|-----------|------|
| **MCP Server** | Expose YAWL to AI models | SSE/HTTP | 3000 |
| **A2A Server** | Expose YAWL to agents | JSON-RPC/gRPC | 8080 |
| **Interface B** | REST API client access | HTTP/REST | 8080 |
| **Z.AI API** | Cloud AI services | HTTPS/REST | 443 |

---

## MCP Server Integration

### What is MCP?

Model Context Protocol (MCP) enables AI models to use YAWL workflows as tools. MCP provides:
- **Tools** - Operations AI models can invoke
- **Resources** - Data AI models can access
- **Prompts** - Pre-defined interactions

### MCP Server Setup

#### 1. Start MCP Server

```bash
# Standalone
java -cp "yawl-integration/target/yawl-integration-5.2.0.jar" \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer

# Via Docker
docker run -p 3000:3000 yawl/mcp-server:5.2

# Via Kubernetes
kubectl apply -f k8s/mcp-server.yaml
```

#### 2. Verify Server

```bash
# Health check
curl http://localhost:3000/health

# List available tools
curl http://localhost:3000/tools

# Expected response:
{
  "tools": [
    {
      "name": "startWorkflow",
      "description": "Start a new workflow instance",
      "inputSchema": {...}
    },
    {
      "name": "getWorkflowStatus",
      "description": "Get workflow status",
      "inputSchema": {...}
    },
    ...
  ]
}
```

### Available MCP Tools

#### 1. startWorkflow

**Purpose:** Launch a new workflow case

**Input:**
```json
{
  "workflowId": "OrderProcessing",
  "version": "1.0",
  "inputData": {
    "orderId": "12345",
    "customer": "Acme Corp",
    "amount": 1500.00
  }
}
```

**Output:**
```json
{
  "caseId": "case_abc123def456",
  "status": "running",
  "startTime": "2026-02-16T14:30:00Z"
}
```

#### 2. getWorkflowStatus

**Purpose:** Query workflow case status

**Input:**
```json
{
  "caseId": "case_abc123def456"
}
```

**Output:**
```json
{
  "caseId": "case_abc123def456",
  "status": "running",
  "currentTasks": ["approve", "validate"],
  "completedTasks": ["submit"],
  "progress": 0.33,
  "startTime": "2026-02-16T14:30:00Z"
}
```

#### 3. listWorkflows

**Purpose:** List available workflow specifications

**Input:**
```json
{}
```

**Output:**
```json
{
  "workflows": [
    {
      "id": "OrderProcessing",
      "version": "1.0",
      "description": "Order fulfillment workflow",
      "status": "active"
    },
    {
      "id": "LeaveApproval",
      "version": "2.1",
      "description": "Employee leave approval",
      "status": "active"
    }
  ]
}
```

#### 4. executeTask

**Purpose:** Complete a workflow task

**Input:**
```json
{
  "workItemId": "item_xyz789",
  "outputData": {
    "approved": true,
    "comments": "Approved for processing"
  }
}
```

**Output:**
```json
{
  "workItemId": "item_xyz789",
  "status": "completed",
  "completionTime": "2026-02-16T14:45:00Z"
}
```

### MCP Resources

Resources provide read-only access to YAWL data:

#### 1. yawl://workflows

**Description:** List of workflow specifications

**Access:**
```
GET yawl://workflows
```

**Response:**
```json
[
  {
    "uri": "yawl://workflows/OrderProcessing/1.0",
    "name": "Order Processing",
    "version": "1.0",
    "description": "..."
  }
]
```

#### 2. yawl://cases

**Description:** Running workflow cases

**Access:**
```
GET yawl://cases
GET yawl://cases/{caseId}
```

#### 3. yawl://tasks

**Description:** Available work items

**Access:**
```
GET yawl://tasks
GET yawl://tasks/{participantId}
```

### MCP Client Usage

#### From Python (AI Application)

```python
from mcp import Client

# Connect to YAWL MCP server
client = Client("http://localhost:3000")

# Start workflow
result = client.call_tool("startWorkflow", {
    "workflowId": "OrderProcessing",
    "version": "1.0",
    "inputData": {
        "orderId": "12345",
        "customer": "Acme Corp"
    }
})

case_id = result["caseId"]
print(f"Started workflow case: {case_id}")

# Check status
status = client.call_tool("getWorkflowStatus", {
    "caseId": case_id
})
print(f"Status: {status['status']}")

# Access resources
workflows = client.get_resource("yawl://workflows")
print(f"Available workflows: {len(workflows)}")
```

#### From Claude Desktop

**Configuration** (`claude_desktop_config.json`):
```json
{
  "mcpServers": {
    "yawl": {
      "command": "java",
      "args": [
        "-cp", "yawl-integration.jar",
        "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"
      ],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl/ia"
      }
    }
  }
}
```

**Usage in Claude:**
```
User: Start an order processing workflow for order #12345

Claude: I'll start the workflow for you.
<uses startWorkflow tool>
I've started workflow case_abc123def456 for order #12345.
```

### MCP Configuration

**Environment Variables:**
```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl/ia
export YAWL_USERNAME=admin
export YAWL_PASSWORD=<from_vault>
export MCP_SERVER_PORT=3000
export MCP_LOG_LEVEL=INFO
```

**Configuration File** (`mcp-server-config.yaml`):
```yaml
server:
  port: 3000
  host: 0.0.0.0

yawl:
  engineUrl: http://localhost:8080/yawl/ia
  username: admin
  passwordEnvVar: YAWL_PASSWORD

logging:
  level: INFO
  format: json

security:
  enableAuth: true
  authMethod: bearer
```

---

## A2A Protocol Integration

### What is A2A?

Agent-to-Agent (A2A) protocol enables autonomous agents to discover and invoke YAWL's capabilities.

### A2A Server Setup

#### 1. Start A2A Server

```bash
# Standalone
java -cp "yawl-integration/target/yawl-integration-5.2.0.jar" \
  org.yawlfoundation.yawl.integration.a2a.YawlA2AServer

# With configuration
java -cp "yawl-integration/target/yawl-integration-5.2.0.jar" \
  org.yawlfoundation.yawl.integration.a2a.YawlA2AServer \
  --port 8080 \
  --engine-url http://localhost:8080/yawl/ia
```

#### 2. Discover Capabilities

```bash
# Get agent card
curl http://localhost:8080/a2a/card

# Expected response (JSON-RPC 2.0):
{
  "jsonrpc": "2.0",
  "id": "yawl-agent",
  "capabilities": [
    {
      "name": "launchWorkflow",
      "description": "Launch a workflow case",
      "parameters": {...}
    },
    {
      "name": "getWorkItems",
      "description": "Get available work items",
      "parameters": {...}
    }
  ]
}
```

### A2A Transports

A2A server supports multiple transport protocols:

1. **JSON-RPC 2.0 over HTTP** (default)
2. **gRPC** (high-performance)
3. **HTTP+JSON REST** (simple)

#### JSON-RPC Example

```bash
# Invoke capability via JSON-RPC
curl -X POST http://localhost:8080/a2a/rpc \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "launchWorkflow",
    "params": {
      "specId": "OrderProcessing",
      "version": "1.0",
      "data": {
        "orderId": "12345"
      }
    },
    "id": 1
  }'

# Response:
{
  "jsonrpc": "2.0",
  "result": {
    "caseId": "case_abc123",
    "status": "running"
  },
  "id": 1
}
```

#### gRPC Example

```protobuf
// yawl.proto
service YawlAgent {
  rpc LaunchWorkflow(LaunchRequest) returns (LaunchResponse);
  rpc GetWorkItems(GetWorkItemsRequest) returns (GetWorkItemsResponse);
}

message LaunchRequest {
  string spec_id = 1;
  string version = 2;
  map<string, string> data = 3;
}

message LaunchResponse {
  string case_id = 1;
  string status = 2;
}
```

```python
# Python gRPC client
import grpc
import yawl_pb2
import yawl_pb2_grpc

channel = grpc.insecure_channel('localhost:8080')
stub = yawl_pb2_grpc.YawlAgentStub(channel)

request = yawl_pb2.LaunchRequest(
    spec_id="OrderProcessing",
    version="1.0",
    data={"orderId": "12345"}
)

response = stub.LaunchWorkflow(request)
print(f"Case ID: {response.case_id}")
```

### A2A Client Usage

#### Connect to External Agent

```java
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;

// Connect to external agent
YawlA2AClient agent = new YawlA2AClient("http://document-processor:8080");
agent.connect();

// Invoke agent capability from YAWL workflow task
String result = agent.invokeCapability("extractText", documentData);

// Use result in workflow
updateCaseData(result);

agent.disconnect();
```

#### Workflow Task Integration

```xml
<!-- YAWL specification with A2A task -->
<task id="processDocument">
  <externalInteraction>
    <a2aAgent>
      <agentUrl>http://document-processor:8080</agentUrl>
      <capability>extractText</capability>
      <input>
        <parameter name="documentUrl">{/data/documentUrl}</parameter>
      </input>
      <output>
        <parameter name="extractedText" />
      </output>
    </a2aAgent>
  </externalInteraction>
</task>
```

---

## Interface B Client Integration

### What is Interface B?

Interface B is YAWL's primary REST API for client applications. It provides programmatic access to all workflow operations.

### Authentication

```bash
# Authenticate to get session handle
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect" \
  -d "userID=admin" \
  -d "password=YAWL"

# Response:
<response>
  <sessionHandle>abc123def456...</sessionHandle>
</response>
```

### Core Operations

#### 1. Upload Specification

```bash
curl -X POST "http://localhost:8080/yawl/ia" \
  -H "sessionHandle: YOUR_SESSION_HANDLE" \
  -F "action=upload" \
  -F "specFile=@OrderProcessing.yawl"
```

#### 2. Launch Case

```bash
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=launchCase" \
  -d "sessionHandle=YOUR_SESSION_HANDLE" \
  -d "specID=OrderProcessing" \
  -d "specVersion=1.0" \
  -d "caseParams=<data><orderId>12345</orderId></data>"

# Response:
<response>
  <caseID>case_abc123def456</caseID>
</response>
```

#### 3. Get Work Items

```bash
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=getWorkItemsForParticipant" \
  -d "sessionHandle=YOUR_SESSION_HANDLE" \
  -d "participantID=admin"

# Response:
<response>
  <workItem>
    <workItemID>item_xyz789</workItemID>
    <taskID>approve</taskID>
    <caseID>case_abc123def456</caseID>
    <status>Enabled</status>
  </workItem>
</response>
```

#### 4. Check Out Work Item

```bash
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=checkOutWorkItem" \
  -d "sessionHandle=YOUR_SESSION_HANDLE" \
  -d "workItemID=item_xyz789"
```

#### 5. Complete Work Item

```bash
curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=checkInWorkItem" \
  -d "sessionHandle=YOUR_SESSION_HANDLE" \
  -d "workItemID=item_xyz789" \
  -d "data=<output><approved>true</approved></output>"
```

### Java Client Library

```java
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

// Create client
InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
    "http://localhost:8080/yawl/ib"
);

// Connect
String sessionHandle = client.connect("admin", "YAWL");

// Launch case
String caseID = client.launchCase(
    sessionHandle,
    "OrderProcessing",
    "1.0",
    "<data><orderId>12345</orderId></data>"
);

// Get work items
List<WorkItemRecord> items = client.getWorkItemsForParticipant(
    "admin",
    sessionHandle
);

// Check out work item
String workItemID = items.get(0).getID();
client.checkOutWorkItem(workItemID, sessionHandle);

// Complete work item
client.checkInWorkItem(
    workItemID,
    "<output><approved>true</approved></output>",
    sessionHandle
);

// Disconnect
client.disconnect(sessionHandle);
```

---

## Z.AI API Integration

### What is Z.AI?

Z.AI integration enables YAWL to invoke cloud-based AI services for intelligent workflow processing.

### Configuration

```yaml
# zai-config.yaml
zai:
  apiUrl: https://api.zhipuai.cn/v1
  apiKeyEnvVar: ZHIPU_API_KEY
  model: glm-4-flash
  timeout: 30000
  retries: 3

capabilities:
  - name: analyzeDocument
    endpoint: /chat/completions
    systemPrompt: "Extract key information from documents"

  - name: classifyRequest
    endpoint: /chat/completions
    systemPrompt: "Classify request type and priority"
```

### Usage in Workflows

```xml
<!-- YAWL task with Z.AI integration -->
<task id="analyzeContract">
  <externalInteraction>
    <zaiService>
      <capability>analyzeDocument</capability>
      <input>
        <parameter name="documentText">{/data/contractText}</parameter>
      </input>
      <output>
        <parameter name="analysis" />
        <parameter name="keyTerms" />
        <parameter name="riskScore" />
      </output>
    </zaiService>
  </externalInteraction>
</task>
```

### Java API

```java
import org.yawlfoundation.yawl.integration.zai.ZaiClient;

// Create Z.AI client
ZaiClient zai = new ZaiClient(
    "https://api.zhipuai.cn/v1",
    System.getenv("ZHIPU_API_KEY")
);

// Invoke AI capability
Map<String, Object> input = Map.of(
    "documentText", contractText
);

Map<String, Object> result = zai.invokeCapability("analyzeDocument", input);

String analysis = (String) result.get("analysis");
List<String> keyTerms = (List<String>) result.get("keyTerms");
Double riskScore = (Double) result.get("riskScore");
```

---

## Authentication and Security

### Session Management

```java
// Get session handle
String session = client.connect(username, password);

// Use session for all operations
client.launchCase(session, ...);

// Disconnect when done
client.disconnect(session);
```

### API Key Authentication (MCP/A2A)

```bash
# Environment variable
export YAWL_API_KEY=your_api_key_here

# HTTP header
curl -H "Authorization: Bearer your_api_key_here" \
  http://localhost:3000/tools
```

### OAuth2 Integration

```yaml
# oauth2-config.yaml
oauth2:
  provider: auth0
  clientId: YOUR_CLIENT_ID
  clientSecret: YOUR_CLIENT_SECRET
  authorizationUrl: https://your-domain.auth0.com/authorize
  tokenUrl: https://your-domain.auth0.com/oauth/token
  scopes:
    - yawl:workflows:read
    - yawl:workflows:execute
```

### SPIFFE/SPIRE Integration

```bash
# Start SPIRE server
spire-server run -config spire-server.conf

# Start SPIRE agent
spire-agent run -config spire-agent.conf

# YAWL engine uses SPIFFE workload API
export SPIFFE_ENDPOINT_SOCKET=/run/spire/sockets/agent.sock
```

---

## Integration Patterns

### Pattern 1: AI-Enhanced Workflow

**Use Case:** Use AI to make intelligent decisions in workflows

**Architecture:**
```
User Request → YAWL Workflow → MCP Tool Call → AI Model
                     ↓                              ↓
                  Continue Workflow ← Decision ←────┘
```

**Implementation:**
```python
# AI application with MCP client
mcp = Client("http://localhost:3000")

# Start workflow
case = mcp.call_tool("startWorkflow", {
    "workflowId": "LoanApproval",
    "inputData": {"amount": 50000, "applicant": "John Doe"}
})

# AI analyzes case data
analysis = ai_model.analyze(case["data"])

# AI completes decision task
mcp.call_tool("executeTask", {
    "workItemId": case["currentTask"],
    "outputData": {
        "approved": analysis["recommendation"],
        "riskScore": analysis["risk"]
    }
})
```

### Pattern 2: Multi-Agent Orchestration

**Use Case:** Coordinate multiple AI agents in complex workflows

**Architecture:**
```
YAWL Orchestrator
    ├─ Agent A (Document Processing) via A2A
    ├─ Agent B (Data Validation) via A2A
    └─ Agent C (Risk Assessment) via A2A
```

**Implementation:**
```java
// YAWL workflow coordinates agents
YawlA2AClient agentA = new YawlA2AClient("http://agent-a:8080");
YawlA2AClient agentB = new YawlA2AClient("http://agent-b:8080");
YawlA2AClient agentC = new YawlA2AClient("http://agent-c:8080");

// Task 1: Process document
String processedDoc = agentA.invokeCapability("processDocument", doc);

// Task 2: Validate data
boolean valid = agentB.invokeCapability("validateData", processedDoc);

// Task 3: Assess risk
double risk = agentC.invokeCapability("assessRisk", processedDoc);

// Continue workflow with results
```

### Pattern 3: Event-Driven Integration

**Use Case:** React to external events and trigger workflows

**Architecture:**
```
External System → Event Bus → YAWL Event Listener → Workflow Launch
```

**Implementation:**
```java
// Subscribe to events
EventBus bus = EventBus.getInstance();
bus.subscribe("order.created", event -> {
    // Launch workflow when order created
    client.launchCase(
        sessionHandle,
        "OrderFulfillment",
        "1.0",
        event.getData()
    );
});
```

---

## Troubleshooting

### MCP Server Issues

**Problem:** Server fails to start
```bash
# Check port availability
netstat -an | grep 3000

# Check logs
tail -f logs/mcp-server.log

# Verify YAWL engine connection
curl http://localhost:8080/yawl/ia
```

**Problem:** Tools not appearing in AI client
```bash
# Verify tools endpoint
curl http://localhost:3000/tools

# Check server configuration
cat mcp-server-config.yaml
```

### A2A Integration Issues

**Problem:** Agent connection timeout
```bash
# Check network connectivity
ping agent-hostname

# Verify agent is running
curl http://agent-hostname:8080/a2a/card

# Check firewall rules
iptables -L
```

**Problem:** Capability invocation fails
```bash
# Check capability name
curl http://agent:8080/a2a/card | jq '.capabilities[].name'

# Verify parameters
# Review agent documentation for required parameters
```

### Interface B Issues

**Problem:** Authentication fails
```bash
# Verify credentials
# Default: admin/YAWL

# Check user exists in database
psql -d yawl -c "SELECT userid FROM ib_orgdata_participant;"

# Reset password if needed
# Use admin interface or SQL update
```

**Problem:** Session handle expired
```bash
# Sessions timeout after inactivity
# Re-authenticate to get new session handle

curl -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect" \
  -d "userID=admin" \
  -d "password=YAWL"
```

### Common Error Codes

| Code | Meaning | Solution |
|------|---------|----------|
| **401** | Unauthorized | Check credentials/session handle |
| **404** | Not found | Verify URL and resource ID |
| **500** | Server error | Check server logs |
| **503** | Service unavailable | Ensure YAWL engine is running |

---

## Additional Resources

- **MCP Specification:** https://modelcontextprotocol.io
- **A2A Protocol:** https://github.com/a2aproject
- **Interface B API:** https://yawlfoundation.github.io/interfaceB.html
- **YAWL Examples:** `examples/` directory
- **Integration Tests:** `test/org/yawlfoundation/yawl/integration/`

---

**Total Lines:** ~800 (condensed to 300 lines of core content)
**Estimated Reading Time:** 20 minutes
**Difficulty:** Intermediate to Advanced
