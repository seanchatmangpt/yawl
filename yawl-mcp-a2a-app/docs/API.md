# YAWL MCP-A2A Application API Reference

## Table of Contents

1. [MCP Server API](#mcp-server-api)
2. [A2A Protocol API](#a2a-protocol-api)
3. [REST API](#rest-api)
4. [Tool Specifications](#tool-specifications)
5. [Resource Templates](#resource-templates)
6. [Prompts and Completions](#prompts-and-completions)

---

## MCP Server API

### Overview
The MCP (Model Context Protocol) server provides programmatic access to YAWL workflow capabilities. It supports both STDIO and HTTP/SSE transports.

### JSON-RPC Methods

#### Basic Methods

##### `initialize`
Initialize the MCP session.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {},
      "resources": {},
      "prompts": {}
    },
    "clientInfo": {
      "name": "client-name",
      "version": "1.0.0"
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {},
      "resources": {},
      "prompts": {}
    },
    "serverInfo": {
      "name": "yawl-mcp-server",
      "version": "6.0.0-Beta"
    }
  }
}
```

##### `list_tools`
List available YAWL workflow tools.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "list_tools",
  "params": {}
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "tools": [
      {
        "name": "launch-case",
        "description": "Launch a new workflow case with the given specification",
        "inputSchema": {
          "type": "object",
          "properties": {
            "specificationId": {
              "type": "string",
              "description": "ID of the specification to launch"
            },
            "caseData": {
              "type": "object",
              "description": "Optional case data variables"
            }
          },
          "required": ["specificationId"]
        }
      },
      {
        "name": "cancel-case",
        "description": "Cancel an existing workflow case",
        "inputSchema": {
          "type": "object",
          "properties": {
            "caseId": {
              "type": "string",
              "description": "ID of the case to cancel"
            }
          },
          "required": ["caseId"]
        }
      }
    ]
  }
}
```

##### `tools/call`
Execute a YAWL workflow tool.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "launch-case",
    "arguments": {
      "specificationId": "simple-process",
      "caseData": {
        "user": "john.doe",
        "priority": "high"
      }
    }
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Workflow case launched successfully:\n  Specification: simple-process\n  Case ID: case-abc123\n  Status: Running"
      }
    ]
  }
}
```

---

## A2A Protocol API

### Overview
The A2A (Agent-to-Agent) protocol provides a simpler interface for AI agent integration with YAWL workflows using natural language commands.

### Message Format

#### User to Agent
```json
{
  "messageId": "msg-123",
  "timestamp": "2024-01-01T12:00:00Z",
  "sender": "user",
  "recipient": "yawl-workflow-agent",
  "content": {
    "type": "text",
    "text": "Launch the simple process workflow"
  }
}
```

#### Agent to User
```json
{
  "messageId": "msg-124",
  "timestamp": "2024-01-01T12:00:01Z",
  "sender": "yawl-workflow-agent",
  "recipient": "user",
  "content": {
    "type": "text",
    "text": "Workflow launched successfully. Case ID: case-abc123"
  }
}
```

### Supported Commands

#### Launch Case
```json
{
  "message": {
    "parts": [
      {
        "type": "text",
        "text": "Launch workflow case for specification: simple-process"
      }
    ]
  }
}
```

#### List Specifications
```json
{
  "message": {
    "parts": [
      {
        "type": "text",
        "text": "List available workflow specifications"
      }
    ]
  }
}
```

#### Get Case Status
```json
{
  "message": {
    "parts": [
      {
        "type": "text",
        "text": "Show status for case case-abc123"
      }
    ]
  }
}
```

#### Cancel Case
```json
{
  "message": {
    "parts": [
      {
        "type": "text",
        "text": "Cancel case case-abc123"
      }
    ]
  }
}
```

---

## REST API

### Health Endpoints

#### `GET /mcp/health`
Basic health check.

**Response:**
```json
{
  "status": "healthy",
  "server": "yawl-mcp-http-server",
  "version": "6.0.0",
  "activeConnections": 3,
  "maxConnections": 100,
  "uptimeSeconds": 3600,
  "engineStatus": "connected",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### `GET /mcp/health/live`
Kubernetes liveness probe.

**Response:**
```json
{
  "status": "alive",
  "server": "yawl-mcp-http-server",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### `GET /mcp/health/ready`
Kubernetes readiness probe.

**Response:**
```json
{
  "status": "ready",
  "engineConnected": true,
  "connectionsAvailable": true,
  "timestamp": "2024-01-01T12:00:00Z"
}
```

#### `GET /mcp/metrics`
Detailed connection metrics.

**Response:**
```json
{
  "server": "yawl-mcp-http-server",
  "version": "6.0.0",
  "connections": {
    "active": 3,
    "total": 150,
    "max": 100
  },
  "messages": {
    "received": 1250,
    "sent": 1248
  },
  "errors": 2,
  "uptime": {
    "startTime": "2024-01-01T11:00:00Z",
    "uptimeSeconds": 3600,
    "lastActivity": "2024-01-01T11:59:59Z"
  }
}
```

### MCP SSE Endpoint

#### `GET /mcp/sse`
Server-Sent Events for real-time updates.

**Example Event:**
```json
data: {
  "type": "tool_execution",
  "tool": "launch-case",
  "status": "completed",
  "result": {
    "caseId": "case-abc123",
    "timestamp": "2024-01-01T12:00:00Z"
  }
}
```

### MCP Message Endpoint

#### `POST /mcp/message`
HTTP POST for JSON-RPC messages.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": "msg-123",
  "method": "tools/call",
  "params": {
    "name": "list-specifications",
    "arguments": {}
  }
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "msg-123",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Loaded specifications:\n- simple-process (Simple Process v1.0)\n- order-processing (Order Processing v2.1)"
      }
    ]
  }
}
```

### A2A Endpoint

#### `POST /a2a`
A2A message processing.

**Request:**
```json
{
  "messageId": "a2a-msg-456",
  "sender": "user-agent",
  "recipient": "yawl-workflow-agent",
  "content": {
    "type": "text",
    "text": "List all available workflows"
  }
}
```

**Response:**
```json
{
  "messageId": "a2a-msg-457",
  "sender": "yawl-workflow-agent",
  "recipient": "user-agent",
  "content": {
    "type": "text",
    "text": "Available workflows:\n1. simple-process - Simple Process workflow\n2. order-processing - Order Processing workflow\n3. document-approval - Document Approval workflow"
  }
}
```

---

## Tool Specifications

### Workflow Management Tools

#### `launch-case`
Launch a new workflow case.

**Schema:**
```json
{
  "type": "object",
  "properties": {
    "specificationId": {
      "type": "string",
      "description": "ID of the YAWL specification to launch"
    },
    "caseData": {
      "type": "object",
      "description": "Optional case data variables"
    }
  },
  "required": ["specificationId"]
}
```

**Example:**
```json
{
  "name": "launch-case",
  "arguments": {
    "specificationId": "simple-process",
    "caseData": {
      "initiator": "john.doe@example.com",
      "priority": "high",
      "metadata": {
        "department": "finance"
      }
    }
  }
}
```

#### `cancel-case`
Cancel an existing workflow case.

**Schema:**
```json
{
  "type": "object",
  "properties": {
    "caseId": {
      "type": "string",
      "description": "ID of the case to cancel"
    }
  },
  "required": ["caseId"]
}
```

### Work Item Management Tools

#### `checkout-workitem`
Check out a work item for processing.

**Schema:**
```json
{
  "type": "object",
  "properties": {
    "workitemId": {
      "type": "string",
      "description": "ID of the work item"
    },
    "participantId": {
      "type": "string",
      "description": "Participant checking out the item"
    }
  },
  "required": ["workitemId", "participantId"]
}
```

#### `checkin-workitem`
Complete and check in a work item.

**Schema:**
```json
{
  "type": "object",
  "properties": {
    "workitemId": {
      "type": "string",
      "description": "ID of the work item"
    },
    "participantId": {
      "type": "string",
      "description": "Participant completing the item"
    },
    "results": {
      "type": "object",
      "description": "Work item completion results"
    }
  },
  "required": ["workitemId", "participantId"]
}
```

### Specification Management Tools

#### `upload-specification`
Upload a YAWL specification.

**Schema:**
```json
{
  "type": "object",
  "properties": {
    "specification": {
      "type": "string",
      "description": "YAWL specification XML content"
    },
    "specificationId": {
      "type": "string",
      "description": "Unique ID for the specification"
    }
  },
  "required": ["specification", "specificationId"]
}
```

#### `list-specifications`
List available specifications.

**Schema:**
```json
{
  "type": "object",
  "properties": {
    "includeInactive": {
      "type": "boolean",
      "description": "Include inactive specifications",
      "default": false
    }
  }
}
```

---

## Resource Templates

### YAWL Case Resource
Read-only access to workflow case information.

**Resource URI:** `yawl:///case/{caseId}`

**Fields:**
```json
{
  "caseId": "case-abc123",
  "specificationId": "simple-process",
  "status": "running",
  "created": "2024-01-01T12:00:00Z",
  "workItems": [
    {
      "workitemId": "wi-123",
      "taskId": "task-1",
      "status": "offered",
      "participant": "john.doe"
    }
  ]
}
```

### YAWL Specification Resource
Read-only access to workflow specifications.

**Resource URI:** `yawl:///specification/{specId}`

**Fields:**
```json
{
  "specId": "simple-process",
  "name": "Simple Process",
  "version": "1.0",
  "status": "active",
  "description": "A simple workflow example",
  "author": "YAWL Foundation",
  "created": "2024-01-01T10:00:00Z"
}
```

### Work Item Resource
Read-only access to work item details.

**Resource URI:** `yawl:///workitem/{workitemId}`

**Fields:**
```json
{
  "workitemId": "wi-123",
  "caseId": "case-abc123",
  "taskId": "task-1",
  "status": "offered",
  "participant": "john.doe",
  "data": {
    "customerName": "John Doe",
    "amount": 1000
  },
  "offeredTime": "2024-01-01T12:00:00Z"
}
```

---

## Prompts and Completions

### Prompts

#### Workflow Analysis Prompt
**Purpose:** Guide analysis of YAWL workflows and suggest improvements.

**Context:**
```json
{
  "workflowId": "simple-process",
  "description": "A simple approval workflow with 3 tasks",
  "currentIssues": ["Deadlock in task 2", "Missing timeout"]
}
```

**Completion:**
```
Based on your YAWL workflow specification, here are recommendations:

1. **Deadlock Resolution**: Add a transition from Task 2 to Task 3 to prevent the current deadlock scenario.

2. **Timeout Handling**: Implement timeout logic for Task 1 to handle cases where approval is delayed.

3. **Error States**: Consider adding error handling tasks for common failure scenarios.

Would you like me to generate an updated specification with these improvements?
```

#### Task Completion Prompt
**Purpose:** Guide human users through completing work items.

**Context:**
```json
{
  "workitemId": "wi-123",
  "taskType": "approval",
  "requiredFields": ["amount", "description", "approver"],
  "data": {
    "amount": 1000,
    "description": "Software purchase request"
  }
}
```

**Completion:**
```
To complete this approval task, please provide:

1. **Approver Name**: Who needs to approve this request?
2. **Budget Code**: Which budget category should this be allocated to?
3. **Comments**: Any additional notes about this approval?

Please respond with the missing information to complete the task.
```

### Completions

#### YAWL XML Specification Completion
**Purpose:** Complete partial YAWL specifications using AI assistance.

**Input:**
```xml
<YAWL xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification id="simple-process">
    <name>Simple Process</name>
```

**Completion:**
```xml
<YAWL xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification id="simple-process">
    <name>Simple Process</name>
    <description>A simple approval workflow</description>
    <process id="process-1" name="Simple Process">
      <inputCondition id="input-1" name="Start"/>
      <task id="task-1" name="Request Review"/>
      <task id="task-2" name="Approval"/>
      <outputCondition id="output-1" name="Complete"/>
      <flow id="flow-1" from="input-1" to="task-1"/>
      <flow id="flow-2" from="task-1" to="task-2"/>
      <flow id="flow-3" from="task-2" to="output-1"/>
    </process>
  </specification>
</YAWL>
```

#### Task Configuration Completion
**Purpose:** Complete task configurations with default settings.

**Input:**
```json
{
  "taskId": "approval-task",
  "name": "Approval Task",
  "implementation": "Manual"
}
```

**Completion:**
```json
{
  "taskId": "approval-task",
  "name": "Approval Task",
  "implementation": "Manual",
  "parameters": {
    "deadline": "P3D",
    "retryCount": 3,
    "escalation": true,
    "requiredRole": "approver",
    "dataElements": [
      {"name": "amount", "type": "decimal"},
      {"name": "description", "type": "string"},
      {"name": "approver", "type": "string"}
    ]
  }
}
```

---

## Error Responses

### JSON-RPC Error Codes

| Code | Message | Description |
|------|--------|-------------|
| -32601 | Method not found | The requested method does not exist |
| -32602 | Invalid params | Invalid method parameters |
| -32603 | Internal error | Internal JSON-RPC error |
| -32000 | YAWL engine error | Error from YAWL engine |
| -32001 | Authentication failed | Invalid YAWL credentials |
| -32002 | Resource not found | Requested resource not found |
| -32003 | Permission denied | Insufficient permissions |

### Example Error Response

```json
{
  "jsonrpc": "2.0",
  "id": 123,
  "error": {
    "code": -32001,
    "message": "Authentication failed",
    "data": {
      "details": "Invalid username or password",
      "timestamp": "2024-01-01T12:00:00Z"
    }
  }
}
```

---

## Authentication

### YAWL Engine Authentication
All MCP and A2A interactions require YAWL engine authentication:

```yaml
yawl:
  engine:
    username: ${YAWL_USERNAME}
    password: ${YAWL_PASSWORD}
```

### HTTP Authentication (Optional)
For HTTP transport, basic authentication is supported:

```yaml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
```

---

## Rate Limiting

### Request Limits
- Maximum 100 requests per minute per client
- Burst rate of 10 requests per second
- Connection limit of 100 concurrent connections

### Response Headers
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1640995200
```