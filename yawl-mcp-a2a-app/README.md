# yawl-mcp-a2a-app

**Artifact:** `org.yawlfoundation:yawl-mcp-a2a-app:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

Spring Boot application providing unified MCP (Model Context Protocol) server and A2A (Agent-to-Agent) capabilities for YAWL workflow engine integration with AI agents and multi-agent systems.

- **`YawlMcpA2aApplication`** - Spring Boot entry point with startup metrics
- **`YawlMcpHttpServer`** - MCP server with STDIO and HTTP/SSE transport support
- **`YawlA2AConfiguration`** - A2A server configuration with REST transport
- **`ResilientMcpClientWrapper`** - Circuit breaker and retry patterns for MCP clients
- **`VirtualThreadConfig`** - Java 25 virtual thread executor configuration
- **`gregverse/`** - Greg-Verse A2A multi-agent simulation framework

## Features

### MCP Server (Model Context Protocol)

Exposes YAWL workflow engine operations as tools for AI agents (Claude, GPT, etc.):

- **15 Tools** - Case launch, work item management, specification queries
- **3 Resources** - Specifications, cases, work items (read-only)
- **4 Prompts** - Workflow analysis, troubleshooting, design review
- **Dual Transport** - STDIO for CLI integration, HTTP/SSE for cloud deployment
- **Health Endpoints** - `/mcp/health` for load balancer probes

### A2A Server (Agent-to-Agent Protocol)

Enables inter-agent communication for multi-agent workflow orchestration:

- **Agent Card Discovery** - `/.well-known/agent.json` endpoint
- **Task Management** - Submit, start, complete, cancel lifecycle
- **REST Transport** - JSON-RPC 2.0 over HTTP
- **Event Bus** - In-memory event processing for task state changes

### Resilience Patterns

Production-grade fault tolerance for distributed deployments:

- **Circuit Breaker** - Prevents cascade failures with CLOSED/OPEN/HALF_OPEN states
- **Retry with Jitter** - Exponential backoff with randomized delays
- **Fallback Handling** - Stale-while-revalidate caching patterns
- **Metrics Export** - Prometheus-compatible via Micrometer

### Observability

Full observability stack for production monitoring:

- **OpenTelemetry** - Distributed tracing with OTLP export
- **Micrometer** - Metrics registry with Prometheus endpoint
- **Spring Actuator** - Health, info, metrics endpoints

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-integration` | MCP tool definitions, A2A adapters |
| `yawl-engine` | Workflow engine APIs |
| `yawl-stateless` | Stateless engine for event-driven agents |
| `yawl-elements` | Petri net model |
| `yawl-utilities` | Utilities and exceptions |

## Key Third-Party Dependencies

| Artifact | Version | Purpose |
|----------|---------|---------|
| `spring-boot-starter-web` | 3.5.10 | Spring MVC HTTP transport |
| `spring-boot-starter-actuator` | 3.5.10 | Health and metrics endpoints |
| `mcp` + `mcp-core` + `mcp-spring-webmvc` | 0.17.2 | MCP SDK |
| `a2a-java-sdk-*` | 1.0.0.Alpha2 | A2A SDK (10 modules) |
| `resilience4j-circuitbreaker` | 2.3.0 | Circuit breaker patterns |
| `resilience4j-retry` | 2.3.0 | Retry with backoff |
| `micrometer-registry-prometheus` | - | Prometheus metrics export |
| `opentelemetry-sdk` + `exporter-otlp` | 1.59.0+ | Distributed tracing |
| `jackson-databind` + `jackson-dataformat-yaml` | 2.19.4 | JSON/YAML processing |
| `commons-pool2` | 2.12.0 | Connection pooling |
| `jspecify` | 1.0.0 | Null-safety annotations |

Test dependencies: JUnit 5, Spring Boot Test, Hamcrest.

## Build Configuration Notes

- **Source directory:** `src/main/java` (standard Maven layout)
- **Test directory:** `src/test/java`
- **Compiler:** Java 25 with `--enable-preview` for compact object headers
- **Main class:** `org.yawlfoundation.yawl.mcp.a2a.YawlMcpA2aApplication`
- **Spring Boot plugin:** Creates executable JAR with repackage goal

## Quick Build

```bash
# Build this module only (requires dependencies)
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration,yawl-mcp-a2a-app clean package

# Using dx.sh for faster feedback
bash scripts/dx.sh -pl yawl-mcp-a2a-app
```

## Running

### As Spring Boot Application

```bash
# Run with Maven
mvn -pl yawl-mcp-a2a-app spring-boot:run

# Run executable JAR
java --enable-preview -jar yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Alpha.jar

# With custom configuration
java --enable-preview -jar yawl-mcp-a2a-app.jar --spring.config.location=classpath:/application-prod.yml
```

### Configuration (application.yml)

```yaml
yawl:
  engine:
    url: http://localhost:8080/yawl
    username: admin
    password: YAWL

  mcp:
    enabled: true
    transport:
      http:
        enabled: true
        port: 8081
        sse-path: /mcp/sse
        message-path: /mcp/message
      stdio:
        enabled: false
    resilience:
      enabled: true
      circuit-breaker:
        failure-rate-threshold: 50
        wait-duration-open-state: 30s
      retry:
        max-attempts: 3
        jitter-factor: 0.5

  a2a:
    enabled: true
    transport:
      rest:
        enabled: true
        port: 8082
        path: /a2a
    virtual-threads:
      enabled: true
      graceful-shutdown-seconds: 30
      http-client-timeout-seconds: 60

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

### Docker

```bash
# Build Docker image
docker build -t yawl-mcp-a2a-app:6.0.0 -f yawl-mcp-a2a-app/Dockerfile .

# Run container
docker run -p 8081:8081 -p 8082:8082 \
  -e YAWL_ENGINE_URL=http://host.docker.internal:8080/yawl \
  yawl-mcp-a2a-app:6.0.0
```

## Endpoints

### MCP Server (Port 8081)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/mcp/sse` | GET | SSE connection for server-to-client messages |
| `/mcp/message` | POST | Client-to-server JSON-RPC messages |
| `/mcp/health` | GET | Health check for load balancers |

### A2A Server (Port 8082)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/.well-known/agent.json` | GET | Agent card discovery |
| `/a2a` | POST | Send message to agent |
| `/a2a/tasks/{id}` | GET | Get task status |
| `/a2a/tasks/{id}/cancel` | POST | Cancel task |

### Spring Actuator (Port 8081)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Application health |
| `/actuator/info` | GET | Application info |
| `/actuator/metrics` | GET | Metrics list |
| `/actuator/prometheus` | GET | Prometheus metrics |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    yawl-mcp-a2a-app                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────────────┐    ┌────────────────┐    ┌────────────────┐ │
│  │   MCP Server   │    │   A2A Server   │    │   GregVerse    │ │
│  │  (Port 8081)   │    │  (Port 8082)   │    │  Simulation    │ │
│  │                │    │                │    │                │ │
│  │  - 15 Tools    │    │  - Agent Card  │    │  - Multi-Agent │ │
│  │  - 3 Resources │    │  - Task Mgmt   │    │  - Scenarios   │ │
│  │  - 4 Prompts   │    │  - REST API    │    │  - Orchestrate │ │
│  └───────┬────────┘    └───────┬────────┘    └───────┬────────┘ │
│          │                     │                     │          │
│          └─────────────────────┼─────────────────────┘          │
│                                │                                │
│  ┌─────────────────────────────┴─────────────────────────────┐ │
│  │                    Service Layer                           │ │
│  │  - ResilientMcpClientWrapper (Circuit Breaker + Retry)    │ │
│  │  - McpCircuitBreakerRegistry                              │ │
│  │  - MetricsService (Micrometer)                            │ │
│  │  - YawlConnectionPool (Apache Commons Pool)               │ │
│  └─────────────────────────────┬─────────────────────────────┘ │
│                                │                                │
│  ┌─────────────────────────────┴─────────────────────────────┐ │
│  │                    Configuration                           │ │
│  │  - VirtualThreadConfig (Java 25)                          │ │
│  │  - ResilienceConfiguration (Resilience4j)                 │ │
│  │  - McpTransportConfig                                     │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                  │
└──────────────────────────────────┬──────────────────────────────┘
                                   │
                                   ▼
┌──────────────────────────────────────────────────────────────────┐
│                        YAWL Engine                               │
│  (yawl-engine, yawl-stateless, yawl-integration)                │
└──────────────────────────────────────────────────────────────────┘
```

## Test Coverage

| Test Class | Package | Tests | Focus |
|------------|---------|-------|-------|
| `SimpleHealthTest` | `a2a` | 1 | Spring Boot application starts |
| `YawlMcpA2aApplicationContextTest` | `a2a` | 1 | Context loads with all beans |
| `ActuatorHealthEndpointTest` | `a2a` | 1 | Actuator health endpoint |
| `A2AClassesTest` | `a2a` | 1 | A2A classes available |
| `PatternDemoRunnerTest` | `demo` | 1 | Pattern demo execution |
| `PatternRegistryTest` | `demo.config` | 1 | Pattern registration |
| `YawlYamlConverterTest` | `example` | 4 | YAML to YAWL conversion |
| `ExtendedYamlConverterTest` | `example` | 1 | Extended YAML conversion |
| `WorkflowSoundnessVerifierTest` | `example` | 1 | Soundness verification |

**Total: ~13 tests across 9 test classes**

Run with: `mvn -pl yawl-mcp-a2a-app test`

## Packages

| Package | Purpose |
|---------|---------|
| `org.yawlfoundation.yawl.mcp.a2a` | Main application and package-info |
| `org.yawlfoundation.yawl.mcp.a2a.a2a` | A2A server configuration and executor |
| `org.yawlfoundation.yawl.mcp.a2a.mcp` | MCP HTTP server and transport config |
| `org.yawlfoundation.yawl.mcp.a2a.config` | Spring configurations (virtual threads, resilience) |
| `org.yawlfoundation.yawl.mcp.a2a.service` | Business services (circuit breaker, metrics, pooling) |
| `org.yawlfoundation.yawl.mcp.a2a.service.metrics` | AOP metrics aspects |
| `org.yawlfoundation.yawl.mcp.a2a.service.pool` | Connection pooling |
| `org.yawlfoundation.yawl.mcp.a2a.example` | Example code (A2A client/server, YAML converter) |
| `org.yawlfoundation.yawl.mcp.a2a.demo` | Pattern demo runner |
| `org.yawlfoundation.yawl.mcp.a2a.gregverse` | Greg-Verse multi-agent simulation |
| `org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl` | Agent implementations |
| `org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario` | Scenario definitions |
| `org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation` | Simulation orchestration |
| `org.yawlfoundation.yawl.resilience.observability` | Retry observability |

## Examples

### MCP Tool Invocation

```java
// Create resilient MCP client
CircuitBreakerProperties props = CircuitBreakerProperties.defaults();
ResilientMcpClientWrapper wrapper = new ResilientMcpClientWrapper(props);

// Connect to MCP server
wrapper.connectSse("yawl-server", "http://localhost:8081/mcp");

// Call tool with automatic retry and circuit breaker
Map<String, Object> args = Map.of(
    "specId", "OrderFulfillment",
    "caseParams", "<data><customer>C123</customer></data>"
);
McpSchema.CallToolResult result = wrapper.callTool("yawl-server", "launch_case", args);

// Check circuit breaker state
McpCircuitBreakerState state = wrapper.getCircuitBreakerState("yawl-server");
```

### A2A Agent Card

```java
// Create agent card for YAWL workflow agent
AgentCard card = AgentCard.builder()
    .name("yawl-workflow-agent")
    .description("YAWL workflow execution agent")
    .version("6.0.0")
    .provider(new AgentProvider("YAWL Foundation", "https://yawlfoundation.github.io"))
    .capabilities(AgentCapabilities.builder()
        .streaming(false)
        .pushNotifications(true)
        .build())
    .skills(List.of(
        AgentSkill.builder()
            .id("launch_workflow")
            .name("Launch Workflow")
            .description("Launch a new workflow case")
            .tags(List.of("workflow", "case"))
            .build()
    ))
    .build();
```

### Virtual Thread Configuration

```java
// Configure virtual threads via application.yml
// yawl.a2a.virtual-threads.enabled: true

// Or programmatically
@Configuration
public class MyConfig implements AsyncConfigurer {
    @Override
    public AsyncTaskExecutor getAsyncExecutor() {
        return new TaskExecutorAdapter(
            Executors.newVirtualThreadPerTaskExecutor()
        );
    }
}
```

## Roadmap

- **Kubernetes deployment** - Helm charts and K8s manifests in `k8s/mcp-a2a-app/`
- **GraalVM native image** - Cold-start optimization for serverless
- **OAuth2/OIDC integration** - Authentication for MCP and A2A endpoints
- **Redis-backed circuit breaker** - Distributed state for multi-instance deployments
- **Event sourcing** - Event store for A2A task state persistence
- **Process mining export** - XES export for workflow analytics
- **Webhook notifications** - Push notifications for task completion events

## Related Documentation

- [Parent README](../README.md) - YAWL project overview
- [yawl-integration README](../yawl-integration/README.md) - MCP/A2A integration layer
- [yawl-engine README](../yawl-engine/README.md) - Core workflow engine
- [k8s/mcp-a2a-app README](../k8s/mcp-a2a-app/README.md) - Kubernetes deployment
- [Integration docs](../docs/integration/README.md) - Integration patterns
