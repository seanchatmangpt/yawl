# yawl-mcp-a2a-app — MCP/A2A Application

Spring Boot application that exposes the YAWL workflow engine to AI agents via two protocols: the **Model Context Protocol (MCP)** for tool-calling LLMs, and the **Agent-to-Agent (A2A)** protocol for autonomous agent coordination.

## Purpose

Acts as the external connectivity layer between YAWL's internal execution services and the AI agent ecosystem. An LLM using MCP can launch workflow cases, query work items, and drive task completion through structured tool calls. An A2A-speaking agent can coordinate workflow steps as part of a multi-agent pipeline.

## Key Classes

| Class | Role |
|-------|------|
| `YawlMcpA2aApplication` | Spring Boot entry point |
| `YawlA2AAgentCard` | Publishes the agent card (capabilities advertisement) for A2A discovery |
| `YawlA2AConfiguration` | Spring config for A2A server — endpoint binding, SPIFFE identity |
| `YawlA2AExecutor` | Handles incoming A2A task requests, delegates to engine |
| `ResilienceConfiguration` | Resilience4j circuit-breaker and retry configuration |
| `VirtualThreadConfig` | Java 21+ virtual thread executor for high-concurrency request handling |
| `PatternDemoRunner` | Demonstrates WfMC workflow patterns via programmatic execution |

## Build

```bash
# Compile only
bash scripts/dx.sh -pl yawl-mcp-a2a-app

# Full module build with tests
bash scripts/dx.sh -pl yawl-mcp-a2a-app all
```

## Test

```bash
mvn test -pl yawl-mcp-a2a-app
```

## Configuration

Key `application.yml` properties:

```yaml
yawl:
  mcp:
    port: 8090          # MCP server port
    transport: stdio    # or http
  a2a:
    port: 8091          # A2A server port
    agent-id: yawl-engine
```

## Dependencies

- **yawl-integration** — `YawlMcpServer`, `YawlA2AServer` core implementations
- **yawl-engine** — stateful engine access
- **Spring Boot 3.x** — application container, dependency injection
- **Resilience4j** — circuit breaking and retry

## Protocols

| Protocol | Port | Spec |
|----------|------|------|
| MCP | 8090 | [Model Context Protocol](https://modelcontextprotocol.io) |
| A2A | 8091 | [Agent-to-Agent Protocol](https://google.github.io/A2A/) |

See [docs/how-to/integration/mcp-server.md](../docs/how-to/integration/mcp-server.md) and [docs/how-to/integration/a2a-server.md](../docs/how-to/integration/a2a-server.md) for setup guides.
