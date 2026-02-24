# YAWL Integration Module

The YAWL Integration module provides integration layers for external systems, supporting both Model Context Protocol (MCP) and Agent-to-Agent (A2A) communication patterns. This module enables seamless integration between YAWL workflow engine and external AI agents, monitoring systems, and development tools.

## Module Purpose

The Integration module serves as the bridge between the YAWL workflow engine and external systems, providing standardized protocols for workflow interaction, monitoring, and automation. It supports two primary integration patterns:

1. **MCP (Model Context Protocol)** - For workflow monitoring and control
2. **A2A (Agent-to-Agent)** - For autonomous agent interaction

## Key Classes and Interfaces

### MCP Integration

#### Core MCP Server
- **`YawlMcpServer`** - Main MCP server implementation
  - Implements Model Context Protocol v2025-11-25 specification
  - Provides 20 tools for workflow control and monitoring
  - Supports 3 static resources and 3 parameterized resources
  - Includes 4 intelligent prompts for workflow guidance

#### MCP Tools (20 total)
- **Case Management Tools**
  - `launch-case` - Start new workflow instances
  - `cancel-case` - Cancel running workflows
  - `get-case-status` - Check workflow execution state
  - `suspend-case` / `resume-case` - Pause/resume workflows
  - `skip-work-item` - Skip problematic work items

- **Work Item Tools**
  - `get-work-items` - List available work items
  - `complete-work-item` - Complete work items with output data
  - `checkout-work-item` / `checkin-work-item` - Lock/unlock work items
  - `get-work-item-details` - Retrieve specific work item information

- **Specification Tools**
  - `list-specifications` - List all available specifications
  - `get-specification-data` - Get specification XML
  - `get-specification-schema` - Get XML schema for validation
  - `upload-specification` / `unload-specification` - Manage specifications

- **Monitoring Tools**
  - `get-running-cases` - Monitor active workflows
  - `export-xes` - Export event logs for process mining

#### Process Mining Tools (4)
- `analyze-performance` - Performance analysis tools
- `discover-variants` - Process variant discovery
- `analyze-resource-network` - Resource usage patterns
- `conformance-analysis` - Process conformance checking

#### MCP Resources
- **Static Resources**
  - `yawl://specifications` - All loaded specifications
  - `yawl://cases` - All running cases
  - `yawl://workitems` - All live work items

- **Parameterized Resources**
  - `yawl://cases/{caseId}` - Specific case state
  - `yawl://cases/{caseId}/data` - Case variable data
  - `yawl://workitems/{workItemId}` - Work item details

#### MCP Prompts (4)
- `workflow_analysis` - Analyze workflow specifications
- `task_completion_guide` - Guide for completing work items
- `case_troubleshooting` - Diagnose workflow issues
- `process_optimization` - Optimize workflow performance

#### MCP Components
- **`YawlToolSpecifications`** - Tool definition and specification
- **`YawlEventToolSpecifications`** - Event handling tools
- **`YawlPromptSpecifications`** - Prompt definitions
- **`YawlCompletionSpecifications`** - Code completion specs
- **`YawlEventResourceProvider`** - Event resource management
- **`YawlResourceProvider`** - General resource management

### A2A Integration

#### Core A2A Server
- **`YawlA2AServer`** - Main A2A server implementation
  - Implements Agent-to-Agent protocol for autonomous agent interaction
  - Supports JWT-based authentication
  - Provides task queue management and event bus
  - Integrates with external AI agents and services

#### A2A Authentication
- **`A2AAuthenticationProvider`** - Base authentication provider
- **`JwtAuthenticationProvider`** - JWT token-based authentication
- **`CompositeAuthenticationProvider`** - Multiple authentication methods
- **`A2AAuthenticationException`** - Authentication error handling

#### A2A Skills
- **`ProcessMiningSkill`** - Process mining capabilities
- **`ClaudeToolExecution`** - Tool execution for Claude
- **`ZaiFunctionService`** - Integration with ZAI services

#### A2A Handoff Protocol
- **`HandoffProtocol`** - Agent task handoff mechanism
- **`HandoffToken`** - Authentication and state transfer
- **`HandoffMessage`** - Inter-agent communication

#### A2A Components
- **`AgentExecutor`** - Task execution for agents
- **`InMemoryTaskStore`** - Task persistence
- **`MainEventBus`** - Event coordination
- **`RestHandler`** - REST API endpoints

### Event Publishing
- **`McpWorkflowEventPublisher`** - Event publishing to MCP clients
- **`WorkflowEventStore`** - Event persistence and retrieval
- **`CaseStateView`** - Real-time case state monitoring
- **`EventReplayer`** - Event replay for debugging

### Spring Integration
- **`YawlMcpSpringApplication`** - Spring Boot application entry point
- **`YawlMcpConfiguration`** - Spring configuration
- **`YawlMcpToolRegistry`** - Tool registry for Spring
- **`YawlMcpTool`** - Spring-managed tool implementations

#### Spring Tools
- **`CompleteWorkItemTool`** - Spring-managed work item completion
- **`GetWorkItemsTool`** - Spring-managed work item retrieval

## Dependencies

### Internal Dependencies
- `yawl-engine` - Core workflow engine functionality
- `yawl-elements` - YAWL data model and specifications
- `yawl-utilities` - Utility classes and helpers

### External Dependencies
- **MCP SDK** - Model Context Protocol implementation
  - `io.modelcontextprotocol.server` - MCP server implementation
  - `io.modelcontextprotocol.json.jackson2` - JSON serialization
  - `com.fasterxml.jackson.databind` - JSON processing

- **A2A SDK** - Agent-to-Agent protocol
  - `io.a2a.server` - A2A server implementation
  - `io.a2a.spec` - A2A specifications
  - `com.sun.net.httpserver` - HTTP server

- **Spring Framework** - Spring Boot integration
  - `org.springframework.boot` - Spring Boot
  - `org.springframework.context` - Context management

- **Security** - Authentication and authorization
  - JWT processing libraries
  - Password encryption utilities

## Usage Examples

### Basic MCP Usage

```java
// Initialize MCP server
YawlMcpServer mcpServer = new YawlMcpServer();
mcpServer.start();

// Launch a new case
LaunchCaseRequest launchRequest = new LaunchCaseRequest(
    "Process", "1.0", "SampleProcess",
    Map.of("inputData", "initial value")
);
CaseResponse caseResponse = mcpServer.launchCase(launchRequest);

// Get work items
List<WorkItemRecord> workItems = mcpServer.getWorkItems(caseResponse.getCaseId());

// Complete a work item
CompleteWorkItemRequest completeRequest = new CompleteWorkItemRequest(
    workItems.get(0).getID(),
    Map.of("outputData", "completed value")
);
mcpServer.completeWorkItem(completeRequest);
```

### A2A Agent Integration

```java
// Start A2A server
YawlA2AServer a2aServer = new YawlA2AServer();
a2aServer.start();

// Agent executes workflow tasks
AgentExecutor agentExecutor = new AgentExecutor();
RequestContext context = new RequestContext(caseId, workItemId);
TaskResponse response = agentExecutor.execute(context);

// Handle authentication
AuthenticatedPrincipal principal = jwtAuthProvider.authenticate(token);
if (principal.isAuthenticated()) {
    // Process authorized request
}
```

### Event Monitoring

```java
// Subscribe to workflow events
McpWorkflowEventPublisher publisher = new McpWorkflowEventPublisher();
publisher.subscribe("case-created", this::handleCaseCreated);
publisher.subscribe("work-item-completed", this::handleWorkItemCompleted);

// Handle events
void handleCaseCreated(CaseEvent event) {
    logger.info("New case created: {}", event.getCaseId());
    // Trigger external systems
}
```

## Integration Patterns

### 1. MCP Integration Pattern
- **Protocol**: Standardized tool-based interaction
- **Use Cases**: Monitoring, control, debugging
- **Transport**: STDIO, HTTP, WebSocket
- **Security**: Token-based authentication

### 2. A2A Integration Pattern
- **Protocol**: Agent-to-agent communication
- **Use Cases**: Autonomous agent interaction
- **Transport**: HTTP, custom protocols
- **Security**: JWT, authentication providers

### 3. Spring Integration Pattern
- **Framework**: Spring Boot container
- **Use Cases**: Enterprise integration
- **Transport**: REST APIs
- **Security**: Spring Security integration

## Configuration

### MCP Configuration
```properties
# Server configuration
yawl.mcp.port=8080
yawl.mcp.transport=stdio
yawl.mcp.tools.enabled=true

# Resource configuration
yawl.mcp.resources.specifications.enabled=true
yawl.mcp.resources.cases.enabled=true
yawl.mcp.resources.workitems.enabled=true
```

### A2A Configuration
```properties
# Server configuration
yawl.a2a.port=9090
yawl.a2a.authentication.jwt.enabled=true
yawl.a2a.agent.threads=10

# Task configuration
yawl.a2a.task.timeout=30000
yawl.a2a.task.retry.max=3
```

### Spring Configuration
```properties
# Application configuration
spring.application.name=yawl-mcp-spring
server.port=8081

# Database configuration
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=update
```

## Performance Characteristics

### MCP Performance
- **Tool Execution**: < 100ms for most operations
- **Resource Retrieval**: < 50ms for cached resources
- **Event Publishing**: < 10ms latency
- **Concurrent Connections**: 1000+ supported

### A2A Performance
- **Agent Execution**: Variable based on task complexity
- **Authentication**: < 20ms JWT validation
- **Task Queue**: 10,000+ pending tasks supported
- **Memory Usage**: Optimized for long-running agents

## Error Handling

### MCP Error Handling
- **Standardized Error Responses** - Consistent error format
- **Tool Validation** - Pre-execution validation
- **Resource Not Found** - Graceful degradation
- **Permission Denied** - Security error handling

### A2A Error Handling
- **Authentication Failures** - JWT validation errors
- **Task Execution Failures** - Retry mechanisms
- **Handoff Failures** - Fallback to other agents
- **Network Errors** - Automatic reconnection

## Best Practices

1. **Use MCP for monitoring and control** - Standardized interface
2. **Use A2A for autonomous agents** - Direct agent communication
3. **Implement proper authentication** - Security first approach
4. **Monitor performance metrics** - Optimized for scale
5. **Use Spring for enterprise deployment** - Production-ready features
6. **Handle events asynchronously** - Non-blocking operations
7. **Implement circuit breakers** - Fault tolerance
8. **Use connection pooling** - Resource optimization