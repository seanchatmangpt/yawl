# YAWL v6.0.0 Integration Architecture Reference

**Version**: 6.0.0-Beta
**Date**: February 22, 2026
**Audience**: Integration engineers, DevOps, LLM developers

---

## 1. Integration Architecture Overview

YAWL v6.0.0 implements a **three-layer integration architecture** enabling seamless workflow automation across LLM systems, autonomous agents, and enterprise applications.

### 1.1 Integration Layers

**Layer 1: LLM Integration (MCP)**
- Protocol: Model Context Protocol v5.2.0
- Server: YawlMcpServer (STDIO transport)
- Use case: Claude Desktop, web-based LLMs, browser extensions
- Latency: <500ms per tool call
- Throughput: 100+ concurrent LLMs

**Layer 2: Workflow Engine (Core)**
- Classes: YEngine, YNetRunner, YWorkItem
- Persistence: JDBC (Hibernate ORM)
- Concurrency: Virtual threads (21+ services)
- Memory model: Dual (stateful + stateless)

**Layer 3: Agent Coordination (A2A)**
- Protocol: Agent-to-Agent REST v5.2.0
- Server: YawlA2AServer (port 8081)
- Use case: Multiple autonomous agents, microservices, event pipelines
- Latency: <250ms per skill call
- Throughput: 1,000+ concurrent agent connections

### 1.2 Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                      LLM / Claude Desktop                           │
│                    (MCP Client, STDIO pipes)                        │
└────────────────────────────┬────────────────────────────────────────┘
                             │ (STDIO)
                             │
        ┌────────────────────▼───────────────────┐
        │    YawlMcpServer (Layer 1: LLM)       │
        │  ┌─────────────────────────────────┐  │
        │  │  Tools (6):                     │  │
        │  │  • launch_case                  │  │
        │  │  • cancel_case                  │  │
        │  │  • get_case_state               │  │
        │  │  • list_specifications          │  │
        │  │  • get_workitems                │  │
        │  │  • complete_workitem            │  │
        │  └─────────────────────────────────┘  │
        │  35 classes | v5.2.0                  │
        └────────────────────┬───────────────────┘
                             │ (Java RPC)
                             │
    ┌────────────────────────▼─────────────────────┐
    │  YAWL Core Engine (Layer 2: Stateful)       │
    │ ┌────────────────────────────────────────┐  │
    │ │ YEngine / YNetRunner / YWorkItem       │  │
    │ │ • Task execution                       │  │
    │ │ • State transitions                    │  │
    │ │ • Event dispatch                       │  │
    │ │ • Virtual threads (21+ services)       │  │
    │ └────────────────────────────────────────┘  │
    │ ┌────────────────────────────────────────┐  │
    │ │ YStatelessEngine (Cloud-native)       │  │
    │ │ • Event-sourced persistence           │  │
    │ │ • Horizontal scaling (K8s pods)        │  │
    │ │ • Stateless per request                │  │
    │ └────────────────────────────────────────┘  │
    │         13 Maven modules                    │
    └────────────────┬────────────────────────────┘
                     │ (Event bus + JDBC)
    ┌────────────────▼────────────────────┐
    │     Workflow Database (Persistent)  │
    │  • Cases (CaseID, State, Data)      │
    │  • Work Items (TaskID, Status)      │
    │  • Specification (XSD, YAWL)        │
    └─────────────────────────────────────┘
                     │ (Event stream)
                     │
    ┌────────────────▼──────────────────────────┐
    │  YawlA2AServer (Layer 3: Agent Coord)    │
    │  Port 8081 (REST API)                    │
    │  ┌──────────────────────────────────┐    │
    │  │  Skills (4):                     │    │
    │  │  • launch_workflow               │    │
    │  │  • query_workflows               │    │
    │  │  • manage_workitems              │    │
    │  │  • cancel_workflow               │    │
    │  └──────────────────────────────────┘    │
    │  51 classes | v5.2.0                     │
    └────────────────┬───────────────────────────┘
                     │ (HTTP REST)
                     │
┌────────────────────▼───────────────────────────┐
│  Autonomous Agents / External Services        │
│  • LLM workflow orchestrators                 │
│  • Human task monitors                        │
│  • Compliance checkers                        │
│  • Analytics services                         │
└────────────────────────────────────────────────┘
```

---

## 2. Layer 1: MCP Integration (LLM-Driven Automation)

### 2.1 YawlMcpServer Overview

**Component**: `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer`

**Transport**: STDIO (standard input/output pipes)

**Version**: MCP v5.2.0 (Model Context Protocol)

**Supporting Classes**: 35 (protocol handlers, tool serializers, error translators)

**Latency SLA**: <500ms per tool invocation (99th percentile)

**Concurrency**: 100+ simultaneous LLM clients

### 2.2 MCP Tool Reference

#### Tool 1: launch_case

**Purpose**: Instantiate a new workflow case from a specification.

**Operation**: Create workflow instance, initialize data attributes, emit case created event.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "specification_id": {
      "type": "string",
      "description": "Unique identifier of workflow specification (e.g., 'PurchaseOrder_v1.0')"
    },
    "case_data": {
      "type": "object",
      "description": "Input data attributes (keys and values used in workflow tasks)",
      "additionalProperties": true
    },
    "launch_id": {
      "type": "string",
      "description": "Optional identifier for tracking this launch (auto-generated if omitted)"
    }
  },
  "required": ["specification_id"]
}
```

**Success Response**:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Case launched successfully: case_20260222_abc123"
    }
  ]
}
```

**Error Response** (specification not found):
```json
{
  "content": [
    {
      "type": "text",
      "text": "Error: Specification 'InvalidSpec_v1.0' not found. Use list_specifications to view available specifications."
    }
  ],
  "isError": true
}
```

**Example Workflow**:
```
LLM: "Launch a purchase order workflow for vendor V-12345, amount 50000, priority HIGH"
     ↓
Tool: launch_case(
  specification_id="PurchaseOrder_v1.0",
  case_data={
    "vendorId": "V-12345",
    "amount": 50000,
    "priority": "HIGH"
  }
)
     ↓
Response: "Case launched successfully: case_20260222_POA001"
     ↓
LLM: "I've initiated the purchase order. It's now with the approvals team."
```

---

#### Tool 2: cancel_case

**Purpose**: Terminate an active workflow case and suspend all pending work items.

**Operation**: Mark case as cancelled, suspend active work items, emit case cancelled event, notify assigned users.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "case_id": {
      "type": "string",
      "description": "Unique case identifier to cancel (e.g., 'case_20260222_abc123')"
    },
    "reason": {
      "type": "string",
      "description": "Reason for cancellation (stored in audit trail)"
    }
  },
  "required": ["case_id"]
}
```

**Success Response**:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Case case_20260222_POA001 cancelled. 3 work items suspended. Assigned users notified."
    }
  ]
}
```

**Error Response** (case not found):
```json
{
  "content": [
    {
      "type": "text",
      "text": "Error: Case 'invalid_case_id' not found or already completed."
    }
  ],
  "isError": true
}
```

**Example**:
```
LLM: "The purchase order for V-12345 is no longer needed. Cancel it."
     ↓
Tool: cancel_case(
  case_id="case_20260222_POA001",
  reason="Vendor no longer available, project delayed"
)
     ↓
Response: "Case case_20260222_POA001 cancelled. 3 work items suspended."
     ↓
LLM: "Cancellation complete. All pending approvals have been suspended."
```

---

#### Tool 3: get_case_state

**Purpose**: Retrieve current state, attributes, and progress of a case.

**Operation**: Query case metadata, current work items, data attributes, event history.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "case_id": {
      "type": "string",
      "description": "Unique case identifier"
    }
  },
  "required": ["case_id"]
}
```

**Success Response**:
```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"case_id\": \"case_20260222_POA001\", \"specification\": \"PurchaseOrder_v1.0\", \"state\": \"EXECUTING\", \"created_at\": \"2026-02-22T10:30:00Z\", \"attributes\": {\"vendorId\": \"V-12345\", \"amount\": 50000, \"priority\": \"HIGH\"}, \"active_workitems\": 2, \"progress_percent\": 35}"
    }
  ]
}
```

**Example**:
```
LLM: "What's the status of purchase order POA001?"
     ↓
Tool: get_case_state(case_id="case_20260222_POA001")
     ↓
Response: {state: EXECUTING, progress: 35%, active_workitems: 2, assigned_to: ["manager@acme.com", "director@acme.com"]}
     ↓
LLM: "The purchase order is 35% complete. It's currently awaiting manager and director approvals."
```

---

#### Tool 4: list_specifications

**Purpose**: Enumerate available workflow specifications for case creation.

**Operation**: Query all registered workflow specifications, filter by name, return summary info.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "filter": {
      "type": "string",
      "description": "Optional regex filter on specification name (e.g., 'Purchase.*')"
    }
  }
}
```

**Success Response**:
```json
{
  "content": [
    {
      "type": "text",
      "text": "[{\"id\": \"PurchaseOrder_v1.0\", \"name\": \"Purchase Order\", \"version\": \"1.0\", \"tasks\": 8, \"created_at\": \"2026-01-15T09:00:00Z\"}, {\"id\": \"PurchaseOrder_v1.1\", \"name\": \"Purchase Order\", \"version\": \"1.1\", \"tasks\": 9, \"created_at\": \"2026-02-10T14:22:00Z\"}]"
    }
  ]
}
```

**Example**:
```
LLM: "What workflows can I use to create a purchase request?"
     ↓
Tool: list_specifications(filter="Purchase.*")
     ↓
Response: [{id: "PurchaseOrder_v1.0", tasks: 8}, {id: "PurchaseOrder_v1.1", tasks: 9}]
     ↓
LLM: "You have two purchase order workflows available: v1.0 (8 tasks) and v1.1 (9 tasks, newer). Which would you prefer?"
```

---

#### Tool 5: get_workitems

**Purpose**: Retrieve work items (tasks) for a case or assigned to a user.

**Operation**: Query work item queue, filter by case/user/status, return assignment info and deadlines.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "case_id": {
      "type": "string",
      "description": "Case ID to filter by (mutually exclusive with user_id)"
    },
    "user_id": {
      "type": "string",
      "description": "User ID to filter by (mutually exclusive with case_id)"
    },
    "status": {
      "type": "string",
      "enum": ["ENABLED", "EXECUTING", "SUSPENDED", "FIRED"],
      "description": "Filter by work item status"
    }
  }
}
```

**Success Response**:
```json
{
  "content": [
    {
      "type": "text",
      "text": "[{\"id\": \"wi_20260222_001\", \"task_name\": \"Approve Request\", \"case_id\": \"case_20260222_POA001\", \"status\": \"ENABLED\", \"assigned_to\": \"manager@acme.com\", \"deadline\": \"2026-02-23T18:00:00Z\"}, {\"id\": \"wi_20260222_002\", \"task_name\": \"Process Payment\", \"case_id\": \"case_20260222_POA001\", \"status\": \"ENABLED\", \"deadline\": \"2026-02-25T17:00:00Z\"}]"
    }
  ]
}
```

**Example**:
```
LLM: "Show me the pending tasks for the purchase order POA001"
     ↓
Tool: get_workitems(case_id="case_20260222_POA001", status="ENABLED")
     ↓
Response: [{task: "Approve Request", assigned_to: "manager@acme.com", deadline: "2026-02-23"}, {task: "Process Payment", deadline: "2026-02-25"}]
     ↓
LLM: "Two tasks are pending: 1) Manager approval (due Feb 23), 2) Payment processing (due Feb 25). Should I send a reminder?"
```

---

#### Tool 6: complete_workitem

**Purpose**: Mark a work item complete, advance the workflow, and enable downstream tasks.

**Operation**: Validate output data, update work item state, trigger downstream task enablement, emit completion event.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "work_item_id": {
      "type": "string",
      "description": "Work item identifier (e.g., 'wi_20260222_001')"
    },
    "output_data": {
      "type": "object",
      "description": "Output data attributes from task completion",
      "additionalProperties": true
    }
  },
  "required": ["work_item_id"]
}
```

**Success Response**:
```json
{
  "content": [
    {
      "type": "text",
      "text": "Work item wi_20260222_001 completed. Case advanced: Process Payment now enabled. Case progress: 60%."
    }
  ]
}
```

**Error Response** (output validation failed):
```json
{
  "content": [
    {
      "type": "text",
      "text": "Error: Output validation failed. Expected 'approval_status' field. Work item not completed."
    }
  ],
  "isError": true
}
```

**Example**:
```
LLM: "The manager has approved the purchase. Mark wi_001 complete with approval=true and approval_comment='Budget OK'"
     ↓
Tool: complete_workitem(
  work_item_id="wi_20260222_001",
  output_data={
    "approval_status": true,
    "approver_comment": "Budget OK"
  }
)
     ↓
Response: "Work item wi_20260222_001 completed. Process Payment now enabled. Progress: 60%."
     ↓
LLM: "Great! The approval is recorded. The payment processing task is now ready."
```

---

### 2.3 MCP Configuration

**Server startup** (`application.yml`):

```yaml
yawl:
  mcp:
    # Enable MCP server
    enabled: true

    # Transport
    transport: stdio  # STDIO for Claude Desktop, others if needed

    # Tool definitions (JSON schema auto-generated)
    tools:
      launch_case:
        enabled: true
        timeout_ms: 5000
        max_concurrent: 50
      cancel_case:
        enabled: true
        timeout_ms: 3000
      get_case_state:
        enabled: true
        timeout_ms: 2000
        cache_ttl_seconds: 10
      list_specifications:
        enabled: true
        timeout_ms: 1000
        cache_ttl_seconds: 300
      get_workitems:
        enabled: true
        timeout_ms: 3000
        cache_ttl_seconds: 5
      complete_workitem:
        enabled: true
        timeout_ms: 5000

    # Logging
    logging:
      level: INFO
      log_payloads: false  # Don't log sensitive case data

    # Protocol compliance
    protocol_version: "5.2.0"
    strict_validation: true
```

**Startup command**:

```bash
# Start with MCP server
java -jar yawl-mcp-server.jar --config application.yml

# Output: MCP server listening on STDIO
# Waiting for Claude Desktop client...
```

### 2.4 MCP Error Handling

**Error types** and recovery:

| Error | Cause | Response | Recovery |
|-------|-------|----------|----------|
| `SPECIFICATION_NOT_FOUND` | launch_case with invalid spec_id | Error message + suggestion (list_specifications) | Retry with valid spec |
| `CASE_NOT_FOUND` | cancel_case with invalid case_id | Error message + context (what cases exist?) | Retry with valid case_id |
| `OUTPUT_VALIDATION_FAILED` | complete_workitem with missing fields | Error message + schema of required fields | Retry with valid output_data |
| `TIMEOUT` | Tool execution >limit (5000ms) | Error message + timeout threshold | Retry (may succeed on retry) |
| `UNAUTHORIZED` | Authentication failed (if enabled) | Error message + re-auth prompt | Provide credentials |
| `INTERNAL_ERROR` | Unexpected exception in engine | Generic error (no sensitive details) | Check server logs, retry |

**Example error flow**:

```json
LLM sends: complete_workitem(wi_001, output_data={})
           ↓
Server response: {
  "isError": true,
  "content": [{
    "type": "text",
    "text": "Error: OUTPUT_VALIDATION_FAILED - Missing required field 'approval_status'. Expected: {approval_status: boolean, approver_comment: string}"
  }]
}
           ↓
LLM: "I need to provide approval_status. Let me retry with the correct fields."
     complete_workitem(wi_001, output_data={approval_status: true, approver_comment: "OK"})
           ↓
Success
```

---

## 3. Layer 2: YAWL Core Engine (Workflow Execution)

### 3.1 YEngine (Stateful)

**Component**: `org.yawlfoundation.yawl.engine.YEngine`

**Architecture**: In-memory + persistent database

**Concurrency Model**: Virtual threads (21+ services converted from platform threads)

**Key Classes**:
- `YNetRunner`: Petri net execution engine
- `YWorkItem`: Task instance state machine
- `YSpecification`: Workflow definition holder
- `YCase`: Case instance container

**Performance**:
- Task throughput: 3,350 tasks/sec (with virtual threads)
- Max concurrent cases: 10,000+ (memory-permitting)
- State transition latency: <50ms p99

### 3.2 YStatelessEngine (Cloud-Native)

**Component**: `org.yawlfoundation.yawl.stateless.YStatelessEngine`

**Architecture**: Stateless per request, event-sourced persistence

**Concurrency Model**: Horizontal scaling (load-balance across pods)

**Key Classes**:
- `YCaseMonitor`: Query case state from event log
- `YCaseImporter`: Reconstruct case from events
- `YCaseExporter`: Serialize case to event stream

**Performance**:
- Cold start (replay events): <500ms
- Query latency: <100ms p99
- Horizontal scale: 10+ pods (50,000+ concurrent cases)

**Kubernetes deployment** (see section 9):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-engine
spec:
  replicas: 3
  selector:
    matchLabels:
      app: yawl-engine
  template:
    metadata:
      labels:
        app: yawl-engine
    spec:
      containers:
      - name: yawl-engine
        image: yawlfoundation/yawl:6.0.0
        env:
        - name: ENGINE_MODE
          value: "STATELESS"
        - name: EVENT_STORE_URL
          value: "jdbc:postgresql://event-store:5432/yawl"
        ports:
        - containerPort: 8080
```

---

## 4. Layer 3: Agent-to-Agent (A2A) Integration

### 4.1 YawlA2AServer Overview

**Component**: `org.yawlfoundation.yawl.integration.autonomous.YawlA2AServer`

**Transport**: HTTP/REST (port 8081, configurable)

**Version**: A2A Protocol v5.2.0

**Supporting Classes**: 51 (REST controllers, skill serializers, event publishers)

**Latency SLA**: <250ms per skill call (99th percentile)

**Concurrency**: 1,000+ simultaneous agent connections

**Authentication**: Optional JWT (configurable)

### 4.2 A2A Skill Reference

#### Skill 1: launch_workflow

**Purpose**: Initiate a new workflow instance from an external agent.

**Endpoint**: `POST /yawl/a2a/v1/workflows/launch`

**Request Body**:
```json
{
  "workflow_id": "DocumentReview",
  "workflow_version": "2.1",
  "input_parameters": {
    "document_id": "doc_12345",
    "reviewers": ["reviewer1@acme.com", "reviewer2@acme.com"]
  },
  "agent_id": "agent-doc-orchestrator-001"
}
```

**Response** (201 Created):
```json
{
  "status": "LAUNCHED",
  "workflow_instance_id": "wi_20260222_DOC001",
  "initial_task_id": "task_doc_review",
  "agent_message": "Document review workflow launched. Awaiting reviewer assignments.",
  "task_count": 4,
  "estimated_duration_hours": 24
}
```

**Example Workflow**:
```
Agent (external LLM orchestrator):
  POST /yawl/a2a/v1/workflows/launch
  {workflow_id: "DocumentReview", document_id: "doc_12345"}
     ↓
Engine: Creates workflow instance, assigns reviewers
     ↓
Agent receives: workflow_instance_id = "wi_20260222_DOC001"
     ↓
Agent: "Document review initiated. Reviewers notified. Monitoring for completion..."
```

---

#### Skill 2: query_workflows

**Purpose**: Search for active, completed, and suspended workflows.

**Endpoint**: `GET /yawl/a2a/v1/workflows`

**Query Parameters**:
```
status=ACTIVE|COMPLETED|SUSPENDED|FAILED
agent_id=<agent-id>
specification_id=<spec-id>
limit=<1..1000, default=50>
```

**Example Request**:
```
GET /yawl/a2a/v1/workflows?status=ACTIVE&agent_id=agent-doc-orchestrator&limit=10
```

**Response** (200 OK):
```json
{
  "workflows": [
    {
      "instance_id": "wi_20260222_DOC001",
      "specification_id": "DocumentReview",
      "status": "ACTIVE",
      "initiated_by_agent": "agent-doc-orchestrator-001",
      "created_at": "2026-02-22T10:30:00Z",
      "active_tasks": 2,
      "progress_percent": 35,
      "assigned_agents": ["agent-reviewer-001", "agent-reviewer-002"]
    },
    {
      "instance_id": "wi_20260221_APP001",
      "specification_id": "ApprovalChain",
      "status": "ACTIVE",
      "initiated_by_agent": "agent-approval-automator-001",
      "created_at": "2026-02-21T14:22:00Z",
      "active_tasks": 1,
      "progress_percent": 60
    }
  ],
  "total_count": 2,
  "query_time_ms": 145
}
```

**Example Usage**:
```
Agent: "Give me all workflows I initiated that are still active"
     ↓
GET /yawl/a2a/v1/workflows?status=ACTIVE&agent_id=agent-doc-orchestrator&limit=10
     ↓
Response: 2 active workflows (35%, 60% progress)
     ↓
Agent: "Both workflows are running. Checking status on highest-priority one..."
```

---

#### Skill 3: manage_workitems

**Purpose**: Perform operations on work items (claim, update, complete, suspend).

**Endpoints**:
- `POST /yawl/a2a/v1/workitems/claim`
- `POST /yawl/a2a/v1/workitems/update`
- `POST /yawl/a2a/v1/workitems/complete`
- `POST /yawl/a2a/v1/workitems/suspend`

**Action: Claim (assign to agent)**

```json
{
  "work_item_id": "wi_task_review_001",
  "agent_id": "agent-reviewer-001",
  "action": "claim"
}
```

**Response**:
```json
{
  "status": "CLAIMED",
  "work_item_id": "wi_task_review_001",
  "claimed_by_agent": "agent-reviewer-001",
  "claimed_at": "2026-02-22T11:05:30Z",
  "deadline": "2026-02-23T18:00:00Z"
}
```

**Action: Complete (finish task, pass output)**

```json
{
  "work_item_id": "wi_task_review_001",
  "agent_id": "agent-reviewer-001",
  "action": "complete",
  "data": {
    "review_status": "APPROVED",
    "review_comment": "Document meets all compliance requirements",
    "review_timestamp": "2026-02-22T11:35:22Z"
  }
}
```

**Response**:
```json
{
  "status": "COMPLETED",
  "work_item_id": "wi_task_review_001",
  "completed_by_agent": "agent-reviewer-001",
  "completed_at": "2026-02-22T11:35:22Z",
  "next_workitems": ["wi_task_archive_001"],
  "workflow_progress_percent": 75,
  "enabled_agents": ["agent-archive-001"]
}
```

**Example Multi-Agent Flow**:
```
Agent 1 (Document Router): "Review task ready. Claiming for agent-reviewer-001"
     POST /yawl/a2a/v1/workitems/claim {work_item_id: wi_task_review_001}
     ↓
Engine: wi_task_review_001 claimed by agent-reviewer-001

Agent 2 (Reviewer): "Task claimed. Starting review..."
     (performs review logic)
     ↓
Agent 2: "Review complete. Submitting APPROVED status"
     POST /yawl/a2a/v1/workitems/complete
       {work_item_id: wi_task_review_001,
        data: {review_status: "APPROVED", comment: "OK"}}
     ↓
Engine: Task completed, next task (archive) enabled
     ↓
Agent 3 (Archiver): "Archive task now enabled. Processing..."
```

---

#### Skill 4: cancel_workflow

**Purpose**: Terminate a workflow and suspend all pending tasks.

**Endpoint**: `POST /yawl/a2a/v1/workflows/{workflow_id}/cancel`

**Request Body**:
```json
{
  "workflow_id": "wi_20260222_DOC001",
  "reason": "Project cancelled due to budget constraints",
  "agent_id": "agent-financial-orchestrator-001"
}
```

**Response** (200 OK):
```json
{
  "status": "CANCELLED",
  "workflow_id": "wi_20260222_DOC001",
  "cancelled_by_agent": "agent-financial-orchestrator-001",
  "cancelled_at": "2026-02-22T12:00:00Z",
  "suspended_workitems": 3,
  "affected_agents_notified": 2,
  "cancellation_reason": "Project cancelled due to budget constraints"
}
```

**Example**:
```
Agent (Budget Manager): "Project approval denied. Cancelling all related workflows."
     POST /yawl/a2a/v1/workflows/wi_20260222_DOC001/cancel
       {reason: "Budget denied"}
     ↓
Engine: Workflow cancelled, 3 tasks suspended, 2 agents notified
     ↓
Agent (Document Reviewer): Receives event "workflow_cancelled"
     Cleans up local resources, stops processing
```

---

### 4.3 A2A Configuration

**Server startup** (`application.yml`):

```yaml
yawl:
  a2a:
    # Enable A2A server
    enabled: true

    # REST endpoint
    server_port: 8081
    context_path: /yawl/a2a

    # Authentication
    auth:
      enabled: true
      type: JWT
      jwt_secret: ${A2A_JWT_SECRET}
      jwt_algorithm: HS256
      jwt_expiration_hours: 24

    # Skills
    skills:
      launch_workflow:
        enabled: true
        timeout_ms: 5000
        max_concurrent: 100
      query_workflows:
        enabled: true
        timeout_ms: 3000
      manage_workitems:
        enabled: true
        timeout_ms: 4000
        max_concurrent: 500
      cancel_workflow:
        enabled: true
        timeout_ms: 3000

    # Event notifications
    events:
      enabled: true
      publish_on:
        - workflow_launched
        - workflow_cancelled
        - task_enabled
        - task_completed
      event_queue: kafka  # or rabbitmq, file

    # Logging
    logging:
      level: INFO
      log_payloads: true
      log_file: /var/log/yawl/a2a.log
```

**Startup command**:

```bash
java -jar yawl-a2a-server.jar --config application.yml --port 8081

# Output:
# A2A Server started on port 8081
# Endpoints:
#   POST /yawl/a2a/v1/workflows/launch
#   GET /yawl/a2a/v1/workflows
#   POST /yawl/a2a/v1/workitems/claim
#   POST /yawl/a2a/v1/workitems/complete
#   POST /yawl/a2a/v1/workflows/{id}/cancel
```

### 4.4 A2A Authentication

**JWT Token Generation**:

```bash
# Generate JWT token for agent
curl -X POST http://localhost:8081/yawl/a2a/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "agent_id": "agent-doc-orchestrator-001",
    "client_secret": "secret-from-config"
  }'

# Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "expires_at": "2026-02-23T12:00:00Z"
}
```

**Using JWT in requests**:

```bash
curl -X POST http://localhost:8081/yawl/a2a/v1/workflows/launch \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{workflow_id: "DocumentReview", ...}'
```

---

## 5. ZAI Integration (Zhipu AI Function Calling)

### 5.1 ZaiFunctionService

**Component**: `org.yawlfoundation.yawl.integration.zai.ZaiFunctionService`

**AI Model**: GLM-4 (Zhipu AI)

**Supporting Classes**: 8 (model adapters, function definition translators, response handlers)

**Use Case**: Function calling for LLM-driven task completion (e.g., LLM generates SQL, YAWL executes it)

**Function Registration**:

```java
ZaiFunctionService zai = new ZaiFunctionService();

// Register YAWL tools as GLM-4 functions
zai.registerFunction(new GLMFunction(
  name = "launch_case",
  description = "Launch a new workflow case",
  parameters = {
    specification_id: {type: "string", required: true},
    case_data: {type: "object"}
  }
));

// Send to GLM-4
String response = zai.callGlm4(
  model="glm-4",
  messages=[
    {role: "user", content: "Launch a purchase order workflow..."}
  ],
  functions=[registeredFunctions]
);
```

---

## 6. Integration Test Commands

### 6.1 MCP Integration Tests

**Test script**: `bash scripts/test-mcp-integration.sh`

**Tests**:
1. Server startup (STDIO listening)
2. Tool discovery (list_specifications)
3. Tool invocation (launch_case → case created)
4. Tool error handling (invalid specification → error message)
5. Concurrent tool calls (10 agents simultaneously)
6. Tool timeout handling (>5 sec → error)

**Expected output**:
```
[TEST 1] MCP server startup... PASS
[TEST 2] Tool discovery... PASS (6 tools exposed)
[TEST 3] launch_case tool... PASS (case_20260222_001 created)
[TEST 4] Error handling... PASS (invalid spec detected)
[TEST 5] Concurrent calls (10 agents)... PASS (all completed <500ms)
[TEST 6] Timeout handling... PASS (error on timeout)

All 6 MCP tests passed.
```

### 6.2 A2A Integration Tests

**Test script**: `bash scripts/test-e2e-mcp-a2a`

**Tests**:
1. Server startup (port 8081 listening)
2. Skill invocation (launch_workflow)
3. Query workflows (all agents' workflows)
4. Manage work items (claim → complete)
5. Workflow cancellation
6. Event delivery (task enabled event to agent)
7. Concurrent agents (50 agents simultaneously)

**Expected output**:
```
[TEST 1] A2A server startup on :8081... PASS
[TEST 2] launch_workflow skill... PASS (wi_20260222_001 created)
[TEST 3] query_workflows skill... PASS (correct status returned)
[TEST 4] Claim work item... PASS
[TEST 5] Complete work item... PASS (next task enabled)
[TEST 6] Cancel workflow... PASS (3 tasks suspended)
[TEST 7] Event delivery... PASS (agent received notification)
[TEST 8] Concurrent agents (50)... PASS (all active)

All 8 A2A tests passed.
```

### 6.3 Docker Integration Tests

**Command**: `docker-compose -f docker-compose.a2a-mcp-test.yml up`

**Services spun up**:
- `yawl-engine` (main workflow engine)
- `yawl-mcp-a2a` (MCP + A2A server)
- `postgres` (database)
- `test-runner` (integration test harness)

**Test flow**:
1. Wait for services healthy (health checks)
2. Initialize database schema
3. Load sample workflows (PurchaseOrder, DocumentReview)
4. Launch case via MCP
5. Claim task via A2A
6. Complete task via A2A
7. Query case state via MCP
8. Verify case completion
9. Report results

**Expected output**:
```
Creating yawl-engine ... done
Creating yawl-mcp-a2a ... done
Creating postgres ... done
Creating test-runner ... done

test-runner | Waiting for services healthy... (10s)
test-runner | [PASS] launch_case (MCP) → case created
test-runner | [PASS] launch_workflow (A2A) → instance created
test-runner | [PASS] manage_workitems (A2A) → task claimed
test-runner | [PASS] complete_workitem (MCP) → task completed
test-runner | [PASS] get_case_state (MCP) → 100% progress
test-runner |
test-runner | All 5 integration tests passed.
test-runner |
Stopping yawl-mcp-a2a ... done
Stopping yawl-engine ... done
```

---

## 7. Kubernetes Deployment Reference

### 7.1 Deployment Architecture

```
┌─────────────────────────────────────────────┐
│         Kubernetes Cluster                  │
├─────────────────────────────────────────────┤
│  Namespace: yawl-production                 │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │ Deployment: yawl-engine             │   │
│  │ Replicas: 3                         │   │
│  │ Pod: yawl-engine-0, -1, -2         │   │
│  │ Container: YStatelessEngine         │   │
│  │ Port: 8080                          │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │ Deployment: yawl-mcp-a2a           │   │
│  │ Replicas: 2                         │   │
│  │ Containers:                         │   │
│  │  - MCP server (STDIO, internal)    │   │
│  │  - A2A server (REST :8081)         │   │
│  │ Port: 8081                          │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │ StatefulSet: postgres              │   │
│  │ Replicas: 1                         │   │
│  │ PersistentVolume: 100Gi            │   │
│  │ Port: 5432                          │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │ ConfigMap: yawl-config             │   │
│  │ - application.yml                  │   │
│  │ - log4j.properties                 │   │
│  │ - db.properties                    │   │
│  └─────────────────────────────────────┘   │
│  ┌─────────────────────────────────────┐   │
│  │ Secret: yawl-secrets               │   │
│  │ - JWT_SECRET (A2A auth)            │   │
│  │ - DB_PASSWORD                      │   │
│  │ - TLS_CERT, TLS_KEY                │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### 7.2 Helm Chart (kubernetes/helm/yawl/)

**Directory structure**:
```
kubernetes/helm/yawl/
├── Chart.yaml
├── values.yaml
├── templates/
│   ├── deployment-engine.yaml
│   ├── deployment-mcp-a2a.yaml
│   ├── statefulset-postgres.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── service-engine.yaml
│   ├── service-a2a.yaml
│   ├── ingress.yaml
│   ├── networkpolicy.yaml
│   └── hpa.yaml
```

**Deploy to cluster**:

```bash
helm repo add yawlfoundation https://charts.yawlfoundation.org
helm repo update

# Install YAWL v6.0.0 to production namespace
helm install yawl yawlfoundation/yawl \
  --namespace yawl-production \
  --create-namespace \
  --values production-values.yaml

# Verify deployment
kubectl get pods -n yawl-production
kubectl logs -n yawl-production deployment/yawl-engine
```

### 7.3 Horizontal Pod Autoscaling

**HPA config** (`templates/hpa.yaml`):

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: yawl-engine-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: yawl-engine
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Behavior**: Auto-scales 3 → 10 pods based on CPU/memory utilization.

---

## 8. Configuration Reference

### 8.1 Key Property Files

**Location**: `/config/` or `src/main/resources/`

| File | Purpose | Key Properties |
|------|---------|---|
| `application.yml` | Spring Boot config | server.port, spring.datasource, yawl.* |
| `db.properties` | Database connection | jdbc_url, username, password, pool_size |
| `mcp-config.toml` | MCP server | transport, tools.*, protocol_version |
| `a2a-config.toml` | A2A server | server_port, auth.*, skills.*, events.* |
| `log4j.properties` | Logging | log_level, appender, file_path |
| `kubernetes/values.yaml` | Helm defaults | image.tag, replicas, resources.* |

### 8.2 Environment Variables

**Required**:
```bash
# Database
export DB_HOST=postgres.yawl-production.svc.cluster.local
export DB_PORT=5432
export DB_NAME=yawl
export DB_USER=yawl
export DB_PASSWORD=<secure-password>

# A2A Authentication
export A2A_JWT_SECRET=<long-random-string>

# Logging
export LOG_LEVEL=INFO
export LOG_FILE=/var/log/yawl/yawl.log
```

**Optional**:
```bash
# MCP
export MCP_TRANSPORT=stdio  # or http
export MCP_TIMEOUT_MS=5000

# A2A
export A2A_PORT=8081
export A2A_AUTH_ENABLED=true

# Kubernetes
export K8S_NAMESPACE=yawl-production
export K8S_NODE_SELECTOR=node-type=compute
```

---

## 9. Monitoring and Observability

### 9.1 Prometheus Metrics

**Metrics endpoint**: `GET /actuator/prometheus`

**Key metrics**:
```
yawl_cases_total{status="ACTIVE"}
yawl_cases_total{status="COMPLETED"}
yawl_workitems_total{status="ENABLED"}
yawl_workitems_total{status="EXECUTING"}
yawl_execution_duration_seconds{task_name="..."}
yawl_mcp_tool_calls_total{tool="launch_case"}
yawl_mcp_tool_duration_seconds{tool="launch_case"}
yawl_a2a_skill_calls_total{skill="launch_workflow"}
yawl_a2a_skill_duration_seconds{skill="launch_workflow"}
jvm_memory_used_bytes
jvm_threads_live
process_cpu_usage
```

### 9.2 OpenTelemetry Tracing

**Distributed tracing** across MCP → Engine → A2A calls.

**Sample trace** (JSON):
```json
{
  "trace_id": "abc123def456",
  "spans": [
    {
      "span_id": "mcp_001",
      "operation_name": "mcp.launch_case",
      "start_time": "2026-02-22T12:00:00Z",
      "duration_ms": 145,
      "attributes": {
        "specification_id": "PurchaseOrder_v1.0",
        "case_id": "case_20260222_001"
      },
      "child_spans": ["engine_001"]
    },
    {
      "span_id": "engine_001",
      "operation_name": "engine.create_case",
      "parent_span_id": "mcp_001",
      "duration_ms": 120
    }
  ]
}
```

---

## 10. Troubleshooting Guide

### 10.1 MCP Tool Timeout

**Symptom**: Tool calls return timeout error after 5 seconds.

**Diagnosis**:
```bash
# Check engine logs
kubectl logs deployment/yawl-engine | grep -i timeout

# Check database connectivity
kubectl exec pod/yawl-engine-0 -- curl http://postgres:5432

# Monitor engine CPU/memory
kubectl top pod yawl-engine-0
```

**Resolution**:
- Increase `mcp.tools.*.timeout_ms` in config
- Scale up engine replicas
- Check database load

### 10.2 A2A Agent Connection Lost

**Symptom**: Agent receives "connection reset" or timeout.

**Diagnosis**:
```bash
# Check A2A server logs
kubectl logs deployment/yawl-mcp-a2a -c a2a

# Check network policies
kubectl get networkpolicy -n yawl-production

# Monitor A2A port
kubectl port-forward service/yawl-a2a 8081:8081
curl -X GET http://localhost:8081/actuator/health
```

**Resolution**:
- Increase A2A server replicas
- Review firewall/network policies
- Check agent JWT token expiration

### 10.3 Case Stuck in EXECUTING State

**Symptom**: Work items never complete, case doesn't progress.

**Diagnosis**:
```bash
# Query case state via MCP
curl -X POST http://localhost:8081/yawl/mcp \
  -d 'tool=get_case_state&case_id=case_20260222_001'

# Check work items
curl -X POST http://localhost:8081/yawl/a2a/v1/workitems?case_id=case_20260222_001

# Review engine logs
kubectl logs deployment/yawl-engine | grep case_20260222_001
```

**Resolution**:
- Check if required work items are assigned to agents
- Verify agent is processing assigned tasks
- Cancel and restart case if necessary

---

## 11. Security Best Practices

### 11.1 API Authentication

**MCP**: STDIO transport is internal (no auth needed)

**A2A**: Enable JWT authentication
```yaml
yawl:
  a2a:
    auth:
      enabled: true
      jwt_secret: ${A2A_JWT_SECRET}  # 256+ bit random
      jwt_algorithm: HS256
```

### 11.2 TLS/HTTPS

**Kubernetes Ingress** with TLS:
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: yawl-ingress
spec:
  tls:
  - hosts:
    - api.yawl.example.com
    secretName: yawl-tls-cert
  rules:
  - host: api.yawl.example.com
    http:
      paths:
      - path: /yawl/a2a
        pathType: Prefix
        backend:
          service:
            name: yawl-a2a
            port:
              number: 8081
```

### 11.3 Network Policies

**Restrict A2A access**:
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: yawl-a2a-ingress
spec:
  podSelector:
    matchLabels:
      app: yawl-mcp-a2a
  policyTypes:
  - Ingress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: agents
```

---

## 12. Appendix: API Reference Summary

### MCP Tools (6 total)

| Tool | Endpoint | Method | Timeout |
|------|----------|--------|---------|
| launch_case | (STDIO) | RPC | 5s |
| cancel_case | (STDIO) | RPC | 3s |
| get_case_state | (STDIO) | RPC | 2s |
| list_specifications | (STDIO) | RPC | 1s |
| get_workitems | (STDIO) | RPC | 3s |
| complete_workitem | (STDIO) | RPC | 5s |

### A2A Skills (4 total)

| Skill | Endpoint | Method | Timeout |
|-------|----------|--------|---------|
| launch_workflow | POST /yawl/a2a/v1/workflows/launch | REST | 5s |
| query_workflows | GET /yawl/a2a/v1/workflows | REST | 3s |
| manage_workitems | POST /yawl/a2a/v1/workitems/{action} | REST | 4s |
| cancel_workflow | POST /yawl/a2a/v1/workflows/{id}/cancel | REST | 3s |

### Configuration Locations

| Component | Config File | Path |
|-----------|------------|------|
| MCP | mcp-config.toml | /config/ |
| A2A | a2a-config.toml | /config/ |
| Database | db.properties | /config/ |
| Kubernetes | values.yaml | kubernetes/helm/yawl/ |
| Logging | log4j.properties | src/main/resources/ |

---

**Document Status**: PRODUCTION-READY
**Last Updated**: February 22, 2026
**Maintainer**: Integration Engineering Team
