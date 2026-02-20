---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/integration/**"
  - "*/src/test/java/org/yawlfoundation/yawl/integration/**"
  - "**/yawl-integration/**"
  - "**/yawl-mcp-a2a-app/**"
---

# MCP & A2A Integration Rules

## MCP Server (6 Tools)
- Tools: launch_case, cancel_case, get_case_state, list_specifications, get_workitems, complete_workitem
- Protocol version: 2024-11-05 | SDK: 1.0.0-RC1
- Transport: STDIO (default) or HTTP (via HttpTransportProvider)
- Entry: `org.yawlfoundation.yawl.integration.mcp.YawlMcpServer`

## A2A Server (6 Skills)
- Skills: introspect, generate, build, test, commit, upgrade
- Entry: `org.yawlfoundation.yawl.integration.a2a.YawlA2AServer`
- Virtual thread variant: `VirtualThreadYawlA2AServer` (1000 agents = ~1MB vs 2GB)
- Authentication: JWT tokens with 60-second TTL for handoff

## Z.AI Bridge
- Requires `ZHIPU_API_KEY` environment variable
- HTTP transport via `HttpZaiMcpBridge`
- GLM-4 model integration
- Fail fast if API key missing (no silent fallback)

## Protocol Rules
- All API payloads use Java records (immutable)
- Virtual threads for all I/O-bound operations
- ScopedValue for workflow context propagation
- Handoff protocol uses JWT with automatic retry (max 3, exponential backoff)
