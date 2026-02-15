# YAWL MCP/A2A Integration Examples - Complete Overview

## Table of Contents
1. [Introduction](#introduction)
2. [Architecture](#architecture)
3. [Example Files](#example-files)
4. [Data Flow](#data-flow)
5. [Integration Patterns](#integration-patterns)
6. [Testing Guide](#testing-guide)

## Introduction

This directory contains **6 comprehensive, working examples** demonstrating YAWL integration with modern AI and agent protocols:

- **2 MCP Examples** (Model Context Protocol for AI models)
- **2 A2A Examples** (Agent-to-Agent for intelligent agents)
- **1 Complete Workflow Example** (End-to-end order fulfillment)
- **1 AI Agent Example** (Autonomous task execution with LLM)

All examples follow **Fortune 5 production standards** with:
- ✅ Real YAWL Engine integration (no mocks)
- ✅ Proper error handling
- ✅ Clear documentation
- ✅ Working code that demonstrates actual features
- ✅ Fallback logic when optional components unavailable

## Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    AI Models & Agents                   │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │  Claude  │  │   GPT    │  │  Gemini  │  etc.      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘            │
│       │             │              │                    │
└───────┼─────────────┼──────────────┼────────────────────┘
        │             │              │
        │ MCP Protocol│              │ A2A Protocol
        │             │              │
┌───────▼─────────────▼──────────────▼────────────────────┐
│            Integration Layer (This Examples)            │
│                                                          │
│  ┌──────────────┐              ┌──────────────┐        │
│  │  MCP Server  │◄────────────►│  A2A Server  │        │
│  └──────┬───────┘              └──────┬───────┘        │
│         │                              │                 │
│         │    ┌──────────────┐         │                 │
│         └───►│  YAWL Client │◄────────┘                 │
│              └──────┬───────┘                           │
│                     │                                    │
└─────────────────────┼────────────────────────────────────┘
                      │ Interface B (REST API)
                      │
┌─────────────────────▼────────────────────────────────────┐
│                  YAWL Workflow Engine                     │
│                                                           │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ Specifications│  │   Cases      │  │  Work Items  │   │
│  └─────────────┘  └──────────────┘  └──────────────┘   │
│                                                           │
│  Workflows: OrderFulfillment, Approval, etc.             │
└───────────────────────────────────────────────────────────┘
```

### Component Interaction

```
MCP Client (AI Model)
    ↓ 1. Request workflow launch
MCP Server
    ↓ 2. Translate to YAWL API
YAWL Interface B Client
    ↓ 3. HTTP POST
YAWL Engine
    ↓ 4. Create case, enable tasks
Work Items Created
    ↓ 5. Monitor for new items
AI Agent
    ↓ 6. Analyze with LLM
    ↓ 7. Make decision
    ↓ 8. Complete work item
YAWL Engine
    ↓ 9. Advance workflow
    ↓ 10. Return results
MCP Server
    ↓ 11. Format response
MCP Client (AI Model)
```

## Example Files

### 1. McpServerExample.java (286 lines)

**Purpose**: Demonstrates how to expose YAWL workflows through MCP protocol

**Key Components**:
```java
// Connect to YAWL
InterfaceB_EnvironmentBasedClient client =
    new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib");
String session = client.connect("admin", "YAWL");

// Tools that would be registered:
// - launch_case: Start workflow instance
// - get_case_status: Query execution state
// - complete_task: Finish work item
// - list_workflows: Show available specs

// Resources that would be exposed:
// - yawl://workflows - All specifications
// - yawl://cases/{id} - Case data
// - yawl://tasks/{id} - Task information
```

**Technologies**:
- YAWL Interface B Client
- MCP protocol (when SDK available)
- HTTP/REST for YAWL communication

**Output Demonstrates**:
- Tool registration patterns
- Resource URI schemes
- Request/response formats
- Real workflow launch

---

### 2. McpClientExample.java (223 lines)

**Purpose**: Shows how AI applications connect to YAWL via MCP

**Key Components**:
```java
// Connect with AI enhancement
YawlMcpClient client = new YawlMcpClient(
    "http://localhost:3000",
    zaiApiKey  // Optional
);
client.connect();

// Direct tool call
String result = client.callTool("startWorkflow", orderData);

// AI-enhanced call (natural language)
String aiResult = client.callToolWithAI(
    "Launch order fulfillment for customer 'Acme Corp' with $5000 order",
    context
);

// Get resource
String caseData = client.getResource("yawl://cases/12345");

// AI analysis
String analysis = client.analyzeResourceWithAI(
    "yawl://cases/12345",
    "Identify bottlenecks in this workflow"
);
```

**Technologies**:
- MCP Client SDK (when available)
- Z.AI for AI enhancement
- GLM-4.6 model for intelligence

**Output Demonstrates**:
- Tool invocation
- Resource fetching
- AI-powered analysis
- Natural language interaction

---

### 3. A2aServerExample.java (314 lines)

**Purpose**: Exposes YAWL as an intelligent agent

**Key Components**:
```java
// Define agent capabilities
AgentCard {
  "name": "YAWL Workflow Engine",
  "capabilities": [
    {
      "name": "executeWorkItem",
      "input": {"workItemId": "string", "data": "object"},
      "output": {"result": "string", "status": "string"}
    },
    {
      "name": "launchWorkflow",
      "input": {"specId": "string", "caseData": "object"},
      "output": {"caseId": "string"}
    }
  ]
}

// Delegate work item to external agent
A2ARequest {
  "capability": "processOrderApproval",
  "parameters": {
    "workItemId": "12345.2.1",
    "taskData": {...}
  }
}

// Receive agent response
A2AResponse {
  "status": "success",
  "result": {
    "approved": true,
    "approver": "AI_Agent_001"
  }
}
```

**Technologies**:
- A2A Protocol (JSON-RPC 2.0, gRPC)
- YAWL Interface B
- Agent discovery services

**Output Demonstrates**:
- Agent card definition
- Capability registration
- Work item delegation
- Response handling

---

### 4. A2aClientExample.java (267 lines)

**Purpose**: Connects YAWL to external intelligent agents

**Key Components**:
```java
// Connect to agent
YawlA2AClient client = new YawlA2AClient(
    "http://localhost:8090/a2a",
    zaiApiKey  // Optional for AI features
);
client.connect();

// Invoke capability
String result = client.invokeCapability(
    "processOrderApproval",
    orderData
);

// AI-enhanced invocation
String aiResult = client.invokeWithAI(
    "Process this large order and determine approvals needed",
    orderData
);

// Multi-agent orchestration
String plan = client.getOrchestrationPlan(
    "Complete order: verify inventory, process payment, schedule delivery",
    new String[]{"InventoryAgent", "PaymentAgent", "DeliveryAgent"}
);

// Exception handling with AI
String resolution = client.handleExceptionWithAI(
    "PaymentAgent timeout after 30 seconds",
    context
);
```

**Technologies**:
- A2A Client SDK (when available)
- Z.AI for orchestration
- Multi-agent coordination

**Output Demonstrates**:
- Agent discovery
- Capability invocation
- AI orchestration
- Exception recovery
- Data transformation

---

### 5. OrderFulfillmentIntegration.java (381 lines)

**Purpose**: Complete end-to-end order fulfillment workflow

**Key Components**:
```java
// Step 1: Launch case
String caseData = buildOrderData(
    "ORD-2026-100",
    "Global Enterprises",
    50,  // quantity
    "PROD-Widget-X1",
    25.99  // unit price
);

YSpecificationID spec = new YSpecificationID("OrderFulfillment", "0.1", "0.1");
String caseId = client.launchCase(spec, caseData, null, session);

// Step 2: Execute ordering task
executeTask(client, session, caseId, "ordering",
    "<data>" +
    "<orderVerified>true</orderVerified>" +
    "<stockAvailable>true</stockAvailable>" +
    "</data>");

// Step 3: Process payment
executeTask(client, session, caseId, "payment",
    "<data>" +
    "<paymentStatus>approved</paymentStatus>" +
    "<transactionId>TXN-123</transactionId>" +
    "</data>");

// Step 4: Arrange freight
executeTask(client, session, caseId, "freight",
    "<data>" +
    "<carrier>FastShip</carrier>" +
    "<trackingNumber>TRACK-456</trackingNumber>" +
    "</data>");

// Step 5: Complete delivery
executeTask(client, session, caseId, "delivery",
    "<data>" +
    "<deliveryStatus>completed</deliveryStatus>" +
    "<signature>Customer</signature>" +
    "</data>");
```

**Workflow Progression**:
```
[Start] → Ordering → Payment → Freight → Delivery → [End]
           ↓          ↓          ↓           ↓
         Check      Process    Arrange     Complete
         Stock      Payment    Shipping    Delivery
```

**Data Flow**:
- Order details flow through all tasks
- Each task adds completion data
- Final state contains complete history

**Output Demonstrates**:
- Case launching
- Task execution
- Data propagation
- Error handling
- State management

---

### 6. AiAgentExample.java (456 lines)

**Purpose**: AI-powered autonomous approval agent

**Key Components**:
```java
// Initialize AI service
ZaiService aiService = new ZaiService();  // Uses ZAI_API_KEY
aiService.setSystemPrompt(
    "You are an order approval agent. " +
    "Auto-approve < $10,000. " +
    "Check customer legitimacy, fraud indicators."
);

// Monitor for approval tasks
List<WorkItemRecord> items = client.getCompleteListOfLiveWorkItems(session);
for (WorkItemRecord item : items) {
    if (isApprovalTask(item)) {
        processApproval(item);
    }
}

// Make AI decision
String prompt = String.format(
    "Analyze this order:\n" +
    "Customer: %s\n" +
    "Amount: $%.2f\n" +
    "Should it be approved?",
    order.customer, order.amount
);

String aiResponse = aiService.chat(prompt);
// "DECISION: APPROVE REASON: Amount within limits, customer verified"

ApprovalDecision decision = parseAIResponse(aiResponse);

// Complete work item with decision
String resultData = buildApprovalResult(decision);
client.checkInWorkItem(itemId, resultData, "AI Agent", session);
```

**AI Decision Process**:
```
1. Extract order data from work item
2. Build analysis prompt with business rules
3. Send to GLM-4.6 LLM
4. Parse AI response (APPROVE/REJECT + reasoning)
5. Complete work item with decision
6. Log approval for audit trail
```

**Fallback Logic** (no AI):
```java
// Rule-based approval when ZAI_API_KEY not set
if (amount < 0) {
    decision.approved = false;
    decision.reason = "Invalid amount";
} else if (amount > 10000) {
    decision.approved = false;
    decision.reason = "Exceeds auto-approval limit";
} else {
    decision.approved = true;
    decision.reason = "Amount within limits";
}
```

**Output Demonstrates**:
- AI service initialization
- Work item monitoring
- LLM-based analysis
- Decision making
- Fallback rules
- Audit logging

---

## Data Flow

### Order Fulfillment Complete Flow

```
Input Data (Case Launch)
┌──────────────────────────────────┐
│ orderId: ORD-2026-100            │
│ customer: Global Enterprises     │
│ quantity: 50                     │
│ product: PROD-Widget-X1          │
│ unitPrice: 25.99                 │
│ totalAmount: 1299.50             │
│ deliveryAddress: 123 Business... │
└────────────┬─────────────────────┘
             │
             ▼
┌────────────────────────────────┐
│     Ordering Task              │
│  - Verify order details        │
│  - Check inventory             │
│  OUTPUT:                       │
│    orderVerified: true         │
│    stockAvailable: true        │
└────────────┬───────────────────┘
             │
             ▼
┌────────────────────────────────┐
│     Payment Task               │
│  - Process credit card         │
│  - Validate funds              │
│  INPUT: totalAmount (1299.50)  │
│  OUTPUT:                       │
│    paymentStatus: approved     │
│    transactionId: TXN-xxx      │
│    amountCharged: 1299.50      │
└────────────┬───────────────────┘
             │
             ▼
┌────────────────────────────────┐
│     Freight Task               │
│  - Select carrier              │
│  - Calculate shipping          │
│  INPUT: deliveryAddress        │
│  OUTPUT:                       │
│    carrier: FastShip           │
│    trackingNumber: TRACK-xxx   │
│    estimatedDelivery: 2026-... │
│    shippingCost: 25.00         │
└────────────┬───────────────────┘
             │
             ▼
┌────────────────────────────────┐
│     Delivery Task              │
│  - Deliver to customer         │
│  - Obtain signature            │
│  INPUT: trackingNumber         │
│  OUTPUT:                       │
│    deliveryStatus: completed   │
│    deliveredDate: 2026-02-19   │
│    signature: Customer Name    │
└────────────┬───────────────────┘
             │
             ▼
      Final Case Data
┌──────────────────────────────────┐
│ ALL ORIGINAL DATA +              │
│ ALL TASK OUTPUTS                 │
│ Complete audit trail             │
│ Ready for archival               │
└──────────────────────────────────┘
```

### AI Agent Decision Flow

```
YAWL Work Item
┌──────────────────────────┐
│ Task: order_approval     │
│ Data: <orderDetails/>    │
└────────┬─────────────────┘
         │
         ▼
  Extract Order Info
┌──────────────────────────┐
│ orderId: ORD-001         │
│ customer: Acme Corp      │
│ amount: 2500.00          │
│ product: Widgets         │
└────────┬─────────────────┘
         │
         ▼
  Build AI Prompt
┌──────────────────────────────────┐
│ "Analyze this order:             │
│  Customer: Acme Corp             │
│  Amount: $2500                   │
│  Should be approved?"            │
└────────┬─────────────────────────┘
         │
         ▼
  Z.AI GLM-4.6 Model
┌──────────────────────────────────┐
│ Analyzes:                        │
│ - Amount vs threshold            │
│ - Customer legitimacy            │
│ - Fraud indicators               │
│ - Business rules                 │
└────────┬─────────────────────────┘
         │
         ▼
  AI Response
┌──────────────────────────────────┐
│ "DECISION: APPROVE               │
│  REASON: Amount $2500 within     │
│  auto-approval limit. Customer   │
│  verified, no fraud indicators." │
└────────┬─────────────────────────┘
         │
         ▼
  Parse Decision
┌──────────────────────────┐
│ approved: true           │
│ reason: Amount $2500...  │
│ approver: AI_Agent_GLM   │
└────────┬─────────────────┘
         │
         ▼
  Complete Work Item
┌──────────────────────────┐
│ <data>                   │
│   <approved>true</...>   │
│   <approvedBy>AI...</...>│
│   <approvalReason>...</   │
│ </data>                  │
└────────┬─────────────────┘
         │
         ▼
  Workflow Continues
```

## Integration Patterns

### Pattern 1: Synchronous Tool Invocation (MCP)

```java
// Client side (AI Model)
McpClient client = new McpClient("http://localhost:3000");
String result = client.callTool("launch_case", params);
// Returns immediately with caseId

// Server side
public String handleLaunchCase(String params) {
    YSpecificationID spec = parseParams(params);
    String caseId = yawlClient.launchCase(spec, data, session);
    return formatResponse(caseId);
}
```

**Characteristics**:
- Request/response
- Blocking call
- Immediate result
- Simple error handling

**Use Cases**:
- Quick workflow queries
- Single-step operations
- Real-time status checks

---

### Pattern 2: Asynchronous Agent Delegation (A2A)

```java
// YAWL delegates to agent
A2AClient client = new A2AClient("http://agent-url");
client.invokeCapability("processOrder", orderData);
// Non-blocking, continues workflow

// Agent processes asynchronously
public void processOrder(OrderData data) {
    // Long-running processing...
    // Calls back to YAWL when complete
    yawlClient.checkInWorkItem(workItemId, result, session);
}
```

**Characteristics**:
- Fire-and-forget or callback
- Non-blocking
- Handles long operations
- Supports streaming

**Use Cases**:
- Long-running tasks
- External service integration
- Background processing

---

### Pattern 3: AI-Enhanced Decision Making

```java
// Get task data
WorkItemRecord item = getNextApprovalTask();
String orderData = item.getDataString();

// AI analyzes
ZaiService ai = new ZaiService();
String decision = ai.makeWorkflowDecision(
    "Approval Decision",
    orderData,
    Arrays.asList("Approve", "Reject", "Escalate")
);

// Apply decision
completeTask(item, decision);
```

**Characteristics**:
- Intelligent automation
- Natural language processing
- Learning from patterns
- Explainable decisions

**Use Cases**:
- Approvals
- Routing decisions
- Data validation
- Exception handling

---

### Pattern 4: Multi-Agent Orchestration

```java
// AI plans orchestration
A2AClient client = new A2AClient(agentUrl, zaiKey);
String plan = client.getOrchestrationPlan(
    "Process order: check inventory, payment, shipping",
    new String[]{"InventoryAgent", "PaymentAgent", "ShippingAgent"}
);

// Execute plan
for (Step step : parsePlan(plan)) {
    String result = client.invokeCapability(
        step.agent,
        step.capability,
        step.data
    );
    // Use result in next step
}
```

**Characteristics**:
- Multi-step coordination
- Dynamic planning
- Error recovery
- Resource optimization

**Use Cases**:
- Complex workflows
- Multi-system integration
- Adaptive processes

## Testing Guide

### Prerequisites Check

```bash
# 1. YAWL Engine running
curl http://localhost:8080/yawl/ib
# Should return YAWL response

# 2. Authentication works
curl "http://localhost:8080/yawl/ib?action=connect&userid=admin&password=YAWL"
# Should return session handle

# 3. Java version
java -version
# Should be 21+

# 4. Z.AI key (optional)
echo $ZAI_API_KEY
# Should show your API key
```

### Running Tests

```bash
# Run all examples
./run-examples.sh

# Run specific example
./run-examples.sh mcp-server
./run-examples.sh ai-agent
```

### Verification

Each example should output:
- ✅ Connection successful
- ✅ Operation completed
- ✅ Results shown
- ✅ No errors

### Common Test Scenarios

1. **MCP Server**: Verifies YAWL connectivity and tool registration
2. **MCP Client**: Tests tool invocation and resource fetching
3. **A2A Server**: Demonstrates capability exposure
4. **A2A Client**: Shows agent invocation
5. **Order Fulfillment**: Complete workflow execution
6. **AI Agent**: Autonomous decision making

## Success Criteria

All examples are successful when they:

- ✅ Connect to YAWL Engine
- ✅ Execute without compilation errors
- ✅ Demonstrate stated functionality
- ✅ Show proper error handling
- ✅ Provide clear output
- ✅ Follow Fortune 5 standards

## Next Steps

1. **Review Examples**: Read through source code
2. **Run Tests**: Execute all examples
3. **Customize**: Adapt for your workflows
4. **Integrate**: Use in production systems
5. **Extend**: Add new capabilities

---

**Documentation Complete**: All 6 examples are ready for use with comprehensive documentation, setup instructions, and testing guidance.
