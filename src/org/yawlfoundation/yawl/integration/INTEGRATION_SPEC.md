# YAWL MCP & A2A Integration Specification

Based on official implementations:
- https://github.com/modelcontextprotocol/java-sdk
- https://github.com/a2aproject/a2a-java

---

## MCP (Model Context Protocol) Java SDK Requirements

### Architecture
- **JSON Processing**: Jackson 3 for serialization/deserialization
- **Programming Model**: Reactive Streams API with Project Reactor
- **Logging**: SLF4J facade
- **Transport**: JDK HttpClient (client), Jakarta Servlet (server)
- **Protocol**: JSON-RPC 2.0

### Module Structure
```
org.yawlfoundation.yawl.integration.mcp/
  ├── YawlMcpServer.java          # Server with Tools, Resources, Prompts
  ├── YawlMcpClient.java          # Client for calling MCP servers
  ├── YawlMcpToolProvider.java    # Tool implementations
  ├── YawlMcpResourceProvider.java # Resource implementations
  ├── YawlMcpPromptProvider.java  # Prompt implementations
  └── transport/
      ├── McpHttpTransport.java   # HTTP transport
      └── McpStdioTransport.java  # STDIO transport
```

### MCP Server Implementation

**Tools** (10 required):
1. `launch_case` - Launch workflow case
2. `get_case_status` - Get case status
3. `get_enabled_work_items` - List enabled items
4. `checkout_work_item` - Checkout item
5. `checkin_work_item` - Complete item
6. `get_work_item_data` - Get item data
7. `cancel_case` - Cancel case
8. `get_specification_list` - List specs
9. `upload_specification` - Upload spec
10. `get_case_data` - Get case data

**Resources** (URI-based access):
- `specification://{spec_id}` - Spec details
- `case://{case_id}` - Case data
- `workitem://{work_item_id}` - Work item data
- `task://{task_id}` - Task definition
- `schema://{spec_id}/{task_id}` - Task schema
- `cases://running` - Running cases list
- `cases://completed` - Completed cases
- `specifications://loaded` - Loaded specs

**Prompts** (AI assistance):
- `workflow-design` - Design assistance
- `case-debugging` - Debug cases
- `data-mapping` - Map data between tasks
- `exception-handling` - Handle exceptions
- `resource-allocation` - Resource strategies
- `process-optimization` - Optimize workflows
- `task-completion` - Complete work items

### MCP Client Implementation

**Requirements**:
- JSON-RPC 2.0 client
- Tool calling with argument serialization
- Resource fetching with URI parsing
- Prompt usage for AI assistance
- Streaming response support
- Session management
- Error handling with JSON-RPC error codes

**Key Classes**:
```java
public class YawlMcpClient {
    // Connect to server
    public void connect(String serverUrl);

    // Discover capabilities
    public List<Tool> listTools();
    public List<Resource> listResources();
    public List<Prompt> listPrompts();

    // Call operations
    public <T> T callTool(String name, Map<String, Object> args, Class<T> returnType);
    public String fetchResource(URI resourceUri);
    public String usePrompt(String name, Map<String, String> arguments);
}
```

---

## A2A (Agent-to-Agent) Protocol Requirements

### Architecture
- **Message Format**: Parts-based (TextPart, ImagePart, etc.)
- **Task States**: SUBMITTED, WORK_IN_PROGRESS, COMPLETED, CANCELED
- **Transport**: JSON-RPC 2.0, gRPC, HTTP+REST
- **Configuration**: MicroProfile Config
- **DI**: CDI for Quarkus (optional, not required for standalone)

### Module Structure
```
org.yawlfoundation.yawl.integration.a2a/
  ├── YawlA2aServer.java         # Server for agent registration
  ├── YawlA2aClient.java         # Client for agents
  ├── AgentRegistry.java         # Agent capability registry
  ├── TaskDelegator.java         # Work item delegation
  ├── models/
  │   ├── AgentCard.java         # Agent metadata
  │   ├── Task.java              # Task representation
  │   ├── Message.java           # Message with parts
  │   └── Part.java              # TextPart, ImagePart, etc.
  └── transport/
      ├── JsonRpcTransport.java  # JSON-RPC 2.0
      └── HttpRestTransport.java # HTTP+JSON REST
```

### A2A Server Implementation

**AgentCard** (metadata):
```java
public class AgentCard {
    String name;           // Agent name
    String description;    // What agent does
    String url;            // Agent endpoint
    String version;        // Agent version
    List<String> capabilities;  // What it can do
    List<String> skills;   // Specific skills
    String protocolVersion; // A2A protocol version
}
```

**AgentExecutor** (task processing):
```java
public interface AgentExecutor {
    void execute(RequestContext context, AgentEmitter emitter);
    void cancel(String taskId, AgentEmitter emitter);
}
```

**AgentEmitter** (state transitions):
```java
public interface AgentEmitter {
    void submitted(Task task);
    void workInProgress(String taskId, String status);
    void artifact(String taskId, Object artifact);
    void completed(String taskId, Object result);
    void canceled(String taskId);
    void error(String taskId, Throwable error);
}
```

**Task States**:
- `SUBMITTED` - Task received
- `WORK_IN_PROGRESS` - Agent processing
- `COMPLETED` - Task finished successfully
- `CANCELED` - Task canceled
- `FAILED` - Task failed with error

### A2A Client Implementation

**Requirements**:
- Register with server using AgentCard
- Advertise capabilities and skills
- Receive task assignments
- Execute work items
- Report progress and completion
- Handle cancellation requests
- Send heartbeat messages

**Key Classes**:
```java
public class YawlA2aClient {
    // Register agent
    public void register(AgentCard card);

    // Receive assignments
    public void onTaskAssigned(Consumer<Task> handler);

    // Report progress
    public void submitProgress(String taskId, String status);
    public void submitArtifact(String taskId, Object artifact);
    public void submitCompletion(String taskId, Object result);
    public void reportError(String taskId, Throwable error);

    // Heartbeat
    public void sendHeartbeat();
    public void deregister();
}
```

---

## Order Fulfillment Integration Example

Based on `/home/user/yawl/exampleSpecs/orderfulfillment/_examples/orderfulfillment.yawl`

### Workflow Tasks
1. **Order Entry** - Capture purchase order
2. **Order Approval** - Approve/reject order
3. **Payment Processing** - Handle payment
4. **Freight Booking** - Book carrier
5. **Route Planning** - Plan delivery route
6. **Freight Delivery** - Track delivery
7. **Invoice Generation** - Create invoice

### Data Types
- `PurchaseOrderType` - Main order structure
- `CompanyType` - Customer details
- `OrderType` - Order lines and terms
- `RouteGuideType` - Delivery route
- `TransportationQuoteType` - Shipping quote
- `PickupInstructionsType` - Pickup details
- `DeliveryInstructionsType` - Delivery details

### Agent Delegation Examples

**Order Approval Agent**:
```java
AgentCard approvalAgent = AgentCard.builder()
    .name("OrderApprovalAgent")
    .description("AI-powered order approval agent")
    .capabilities(List.of("order-analysis", "risk-assessment"))
    .skills(List.of("approve-order", "reject-order"))
    .build();
```

**Payment Agent**:
```java
AgentCard paymentAgent = AgentCard.builder()
    .name("PaymentProcessingAgent")
    .description("Payment gateway integration")
    .capabilities(List.of("credit-card", "bank-transfer"))
    .skills(List.of("process-payment", "refund"))
    .build();
```

**Freight Agent**:
```java
AgentCard freightAgent = AgentCard.builder()
    .name("FreightBookingAgent")
    .description("Carrier booking and tracking")
    .capabilities(List.of("carrier-integration", "route-optimization"))
    .skills(List.of("book-shipment", "track-delivery"))
    .build();
```

---

## Implementation Requirements

### Fortune 5 Standards
✅ **DO**:
- Use real InterfaceB/InterfaceA clients for YAWL integration
- Implement actual JSON-RPC 2.0 protocol
- Use Jackson for JSON processing
- Implement real HTTP transports
- Handle errors with proper exception types
- Validate all inputs
- Log all operations

❌ **DO NOT**:
- Create mock/stub implementations
- Use placeholder constants
- Return fake data
- Silent failures
- TODO comments

### Testing
- Integration tests with real YAWL engine
- JSON-RPC protocol compliance tests
- Agent registration/deregistration tests
- Task lifecycle tests (submit → work → complete)
- Error handling tests
- Concurrency tests

---

**Last Updated**: 2026-02-14
**YAWL Version**: 5.2
**MCP Protocol**: JSON-RPC 2.0
**A2A Protocol**: 1.0
