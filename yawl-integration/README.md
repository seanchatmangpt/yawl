# yawl-integration

**Artifact:** `org.yawlfoundation:yawl-integration:6.0.0-Alpha` | `packaging: jar`
**Parent:** `yawl-parent`

## Purpose

External connectivity and AI agent integration layer:

- **MCP (Model Context Protocol)** — Spring-based MCP server exposing YAWL engine operations
  as tools to AI agents (Claude, GPT, etc.)
- **A2A (Agent-to-Agent Protocol)** — Anthropic A2A server for multi-agent workflow orchestration
  _(currently excluded from compilation; install A2A SDK from Maven Central when available)_
- **Observability bridges** — OpenTelemetry integration hooks for distributed tracing
- **SPIFFE/SVID** — workload identity for zero-trust service mesh deployments
- **Deduplication** — idempotency infrastructure for at-least-once message delivery

## Internal Dependencies

| Module | Reason |
|--------|--------|
| `yawl-engine` | engine APIs exposed as MCP/A2A tools |
| `yawl-stateless` | stateless engine for event-driven agent workflows |

## Key Third-Party Dependencies

| Artifact | Version | Purpose |
|----------|---------|---------|
| `mcp` + `mcp-core` + `mcp-json` + `mcp-json-jackson2` | `0.17.2` | MCP SDK (installed from local JARs) |
| `spring-boot-starter-web` | `3.5.10` | Spring MVC HTTP transport for MCP |
| `spring-boot-starter-actuator` | `3.5.10` | Health and metrics endpoints |
| `okhttp` | `5.1.0` | HTTP client for outbound connections |
| `jackson-databind` + `jackson-datatype-jdk8` | `2.19.4` | JSON serialisation |
| `gson` | `2.13.2` | Secondary JSON serialiser |
| `jspecify` | `1.0.0` | Null-safety annotations |
| `commons-lang3` | `3.20.0` | Utilities |
| `log4j-api` | `2.25.3` | Logging |

### A2A SDK (commented out)

The A2A SDK (`io.anthropic:a2a-java-sdk-*`) is declared in comments in the POM.
To enable it, install from `https://github.com/anthropics/a2a-java-sdk` into your
local Maven repository, then uncomment the dependency declarations.

## Classes Excluded from Compilation

| Package / File | Reason |
|----------------|--------|
| `a2a/**` | A2A SDK not on Maven Central |
| `orderfulfillment/**` | Depends on A2A |
| `autonomous/**` | Depends on A2A |
| `zai/**` | Inter-dependency on excluded packages |
| `processmining/**` | Inter-dependency on excluded packages |
| `mcp/YawlMcpClient.java` | MCP client-side transport not in SDK `0.17.2` bridge |
| `mcp/sdk/McpServer.java` et al. | Local bridge files using outdated API vs SDK `0.17.2` |
| `spiffe/SpiffeEnabledZaiService.java` | Depends on excluded `zai` package |

## Build Configuration Notes

- **Source directory:** `../src/org/yawlfoundation/yawl/integration` (scoped)
- **Test directory:** `../test/org/yawlfoundation/yawl/integration`
- Active test packages: `observability/**`, `mcp/**`, `spiffe/**` (workload identity), `dedup/**`
- `SpiffeWorkloadIdentityTest` excluded — uses `sun.security.x509` not exported in Java 21+
- `OpenTelemetryConfigTest` excluded — uses internal OTel API changed in `1.59.0`

## Quick Build

```bash
mvn -pl yawl-utilities,yawl-elements,yawl-engine,yawl-stateless,yawl-integration clean package
```
