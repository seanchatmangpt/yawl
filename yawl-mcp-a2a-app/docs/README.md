# YAWL MCP-A2A Application

## Overview

The YAWL MCP-A2A Application is a Spring Boot application that provides a comprehensive integration between the YAWL workflow engine and modern AI agent protocols. It combines:

- **Model Context Protocol (MCP)** server for exposing YAWL workflow tools to AI agents
- **Agent-to-Agent (A2A)** protocol support for inter-agent communication
- **REST endpoints** for health checks, monitoring, and management
- **GregVerse integration** with specialized AI agents for workflow assistance

## Architecture

### Core Components

```
┌─────────────────────────────────────────────────────────────┐
│                    YawlMcpA2aApplication (Spring Boot)     │
├─────────────────────────────────────────────────────────────┤
│  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │
▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼  ▼
┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐
│config│ │mcp   │ │a2a   │ │demo  │ │gregverse│ │service│ │example│
└─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘
```

### Key Packages and Their Purposes

#### `config` - Spring Configuration
- `ResilienceConfiguration` - Circuit breaker and retry policies for MCP clients
- `VirtualThreadConfig` - Java virtual thread configuration for scalable I/O operations
- Configuration for bean definitions and Spring Boot auto-configuration

#### `mcp` - MCP Server Implementation
- `YawlMcpHttpServer` - Main MCP server with STDIO and HTTP/SSE transport support
- `McpHealthEndpoint` - REST health checks and metrics for load balancers
- `McpTransportConfig` - Transport configuration settings
- MCP tools for YAWL workflow operations (launch cases, complete work items, etc.)

#### `a2a` - A2A Protocol Implementation
- `YawlA2AExecutor` - A2A message executor that delegates to YAWL engine
- `YawlA2AConfiguration` - A2A protocol configuration
- `YawlA2AAgentCard` - Agent card for A2A discovery

#### `demo` - Pattern Demos
- `PatternDemoRunner` - Run workflow pattern demonstrations
- `AutoTaskHandler` - Automated task processing
- `PatternRegistry` - Registry of demo patterns
- `ExecutionHarness` - Execute workflow scenarios

#### `gregverse` - Multi-Agent Integration
- `GregVerseAgent` - Specialized AI agents for workflow assistance
- `GregVerseSimulation` - Multi-agent simulation scenarios
- `GregVerseConfiguration` - GregVerse agent configuration
- Implemented agents:
  - Greg Isenberg (strategy, product vision)
  - James (SEO, marketing, positioning)
  - Nicolas Cole (content creation, personal branding)
  - Dickie Bush (newsletter strategy, monetization)
  - Leo Leojrr (rapid prototyping, app development)
  - Justin Welsh (solopreneurship, LinkedIn strategy)
  - Dan Romero (API design, infrastructure)
  - Blake Anderson (gamification, life philosophy)

#### `service` - Business Services
- `MetricsService` - Metrics collection and reporting
- `ResilientMcpClientWrapper` - Resilient MCP client with circuit breakers
- `YawlConnectionPool` - Connection pooling for YAWL engine
- `McpRetryWithJitter` - Retry mechanism with jitter

#### `example` - Usage Examples
- `A2AClientExample` - A2A client usage patterns
- `A2AServerExample` - A2A server setup examples
- `A2AMultiAgentDemo` - Multi-agent A2A demonstrations
- `WorkflowSoundnessVerifier` - Workflow validation utilities

## Configuration

### Application Properties (`application.yml`)

```yaml
# YAWL Engine Configuration
yawl:
  engine:
    url: ${YAWL_ENGINE_URL:http://localhost:8080/yawl}
    username: ${YAWL_USERNAME:admin}
    password: ${YAWL_PASSWORD:YAWL}
    connection:
      timeout-ms: 5000
      retry-attempts: 3
      retry-delay-ms: 1000

# MCP Server Configuration
yawl:
  mcp:
    enabled: ${MCP_ENABLED:true}
    transport: ${MCP_TRANSPORT:stdio}
    http:
      enabled: ${MCP_HTTP_ENABLED:false}
      port: ${MCP_HTTP_PORT:8081}
      path: ${MCP_HTTP_PATH:/mcp}

# A2A Agent Configuration
yawl:
  a2a:
    enabled: ${A2A_ENABLED:true}
    agent-name: ${A2A_AGENT_NAME:yawl-workflow-agent}
    transport:
      rest:
        enabled: ${A2A_REST_ENABLED:true}
        port: ${A2A_REST_PORT:8082}
      jsonrpc:
        enabled: ${A2A_JSONRPC_ENABLED:false}
        port: ${A2A_JSONRPC_PORT:8083}

# GregVerse Configuration
yawl:
  gregverse:
    enabled: ${GREGVERSE_ENABLED:true}
    api-key: ${ZAI_API_KEY:}
    max-concurrency: 4
    topology: ${GREGVERSE_TOPOLOGY:hierarchical}
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `YAWL_ENGINE_URL` | `http://localhost:8080/yawl` | YAWL engine base URL |
| `YAWL_USERNAME` | `admin` | YAWL admin username |
| `YAWL_PASSWORD` | `YAWL` | YAWL admin password |
| `MCP_ENABLED` | `true` | Enable MCP server |
| `MCP_TRANSPORT` | `stdio` | Transport mode (stdio/http) |
| `A2A_ENABLED` | `true` | Enable A2A protocol |
| `ZAI_API_KEY` | - | ZAI API key for GregVerse agents |

## Running the Application

### As a Spring Boot Application

```bash
# Run with default configuration
mvn spring-boot:run

# Or run the JAR directly
java -jar target/yawl-mcp-a2a-app.jar

# With custom configuration
java -jar target/yawl-mcp-a2a-app.jar --spring.config.location=classpath:/application-prod.yml

# With JVM tuning
java -Xmx2g -Xms1g -jar target/yawl-mcp-a2a-app.jar
```

### With Docker

```bash
# Build and run
docker build -t yawl-mcp-a2a-app .
docker run -p 8080:8080 -p 8081:8081 -p 8082:8082 \
  -e YAWL_ENGINE_URL=http://host.docker.internal:8080/yawl \
  yawl-mcp-a2a-app
```

## API Endpoints

### Health and Metrics
```
GET /actuator/health              - Application health status
GET /actuator/metrics              - Application metrics
GET /mcp/health                    - MCP server health
GET /mcp/health/live              - Kubernetes liveness probe
GET /mcp/health/ready             - Kubernetes readiness probe
GET /mcp/metrics                  - MCP server connection metrics
```

### MCP Endpoints (when HTTP transport enabled)
```
GET /mcp/sse                      - Server-Sent Events endpoint
POST /mcp/message                 - JSON-RPC message endpoint
```

### A2A Endpoints (when REST transport enabled)
```
POST /a2a                        - A2A message endpoint
```

## MCP Tools Available

The MCP server provides 15+ tools for YAWL workflow management:

### Workflow Case Management
- `launch-case` - Launch a new workflow case
- `cancel-case` - Cancel an existing case
- `monitor-case` - Monitor case status

### Work Item Management
- `checkout-workitem` - Check out work item for processing
- `checkin-workitem` - Complete and check in work item
- `list-workitems` - List available work items

### Specification Management
- `upload-specification` - Upload YAWL specification
- `list-specifications` - List available specifications
- `validate-specification` - Validate specification syntax

### Data Management
- `upload-data` - Upload data specifications
- `list-dataspecs` - List data specifications

### Engine Administration
- `shutdown-yawl` - Shutdown YAWL engine
- `engine-status` - Get engine status

## A2A Protocol Support

### Supported Commands
- `launch-case <spec>` - Launch workflow case
- `list specifications` - List available workflows
- `status case <id>` - Check case status
- `cancel case <id>` - Cancel case
- `work items` - List work items

### Agent Capabilities
```json
{
  "agent-name": "yawl-workflow-agent",
  "capabilities": [
    "workflow-management",
    "task-handling",
    "data-transformation",
    "process-introspection"
  ]
}
```

## Monitoring and Observability

### Metrics
The application collects metrics on:
- Connection counts (active, total, max)
- Message throughput (received, sent)
- Error counts
- Application uptime
- Engine connection status
- Response times for MCP tools

### Distributed Tracing
OpenTelemetry integration for distributed tracing:
```yaml
otel:
  service:
    name: yawl-mcp-a2a-app
  traces:
    exporter: ${OTEL_TRACES_EXPORTER:logging}
  metrics:
    exporter: ${OTEL_METRICS_EXPORTER:prometheus}
```

### Health Checks
Kubernetes-ready health endpoints:
- **Liveness**: Checks if the process is running
- **Readiness**: Checks if connected to YAWL engine and accepting connections
- **Metrics**: Detailed runtime statistics

## Development

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test classes
mvn test -Dtest=YawlMcpA2aApplicationTest

# Run with coverage
mvn clean jacoco:report
```

### Code Quality
```bash
# Check code style
mvn checkstyle:check

# Run static analysis
mvn spotbugs:check
mvn pmd:check
```

## Performance Tuning

### Virtual Threads
Application uses Java virtual threads for optimal I/O performance:
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 10
        max-size: 1000
        allow-core-thread-timeout: true
```

### Connection Pooling
Connection pooling for YAWL engine connections:
```yaml
yawl:
  engine:
    connection-pool:
      max-connections: 20
      idle-timeout-ms: 30000
```

### Circuit Breakers
Resilience patterns for fault tolerance:
```yaml
yawl:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 50
      wait-duration-in-open-state: 5s
      sliding-window-type: COUNT_BASED
      sliding-window-size: 10
```

## Troubleshooting

### Common Issues

1. **YAWL Engine Connection Failed**
   - Verify YAWL engine is running
   - Check URL and credentials
   - Ensure network connectivity

2. **MCP Server Won't Start**
   - Check port conflicts (8081 for HTTP MCP)
   - Verify transport configuration
   - Review logging for startup errors

3. **A2A Messages Not Processing**
   - Check A2A agent configuration
   - Verify REST transport is enabled
   - Review message format compliance

### Debug Logging
```yaml
logging:
  level:
    org.yawlfoundation.yawl.mcp.a2a: DEBUG
    io.modelcontextprotocol: DEBUG
    io.anthropic.a2a: DEBUG
```

## Integration Examples

### CLI Integration with MCP
```bash
# Connect via stdio
nc localhost 8081

# Send MCP tool call
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "launch-case",
    "arguments": {
      "specificationId": "simple-process"
    }
  }
}
```

### A2A Agent Integration
```java
// Create A2A client
AgentCard agentCard = AgentCard.builder()
    .name("yawl-workflow-agent")
    .version("6.0.0")
    .build();

// Send workflow command
Message message = A2A.toAgentMessage("list specifications");
```

## License

This project is part of the YAWL Foundation and is licensed under the GNU Lesser General Public License (LGPL). See the LICENSE file for details.