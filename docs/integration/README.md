# YAWL Integration Documentation

Integration guides for connecting YAWL v6.0.0 with external systems, AI assistants, and multi-agent frameworks.

## Documentation Index

| Document | Description | Status |
|----------|-------------|--------|
| [MCP-SERVER.md](MCP-SERVER.md) | Model Context Protocol server for AI assistant integration | Complete |
| [A2A-SERVER.md](A2A-SERVER.md) | Agent-to-Agent server for multi-agent orchestration | Planned v6.1.0 |

## Quick Links

### MCP Server (AI Assistant Integration)

The MCP Server enables AI assistants like Claude to interact with YAWL workflows through standardized tools, resources, and prompts.

**Key Features**:
- 15 workflow tools (launch, cancel, monitor cases, manage work items, specifications)
- 6 resources (specifications, cases, work items - static and parameterized)
- 4 prompts (workflow analysis, task completion, troubleshooting, design review)
- STDIO transport with official MCP Java SDK 0.17.2

[Read MCP Server Guide](MCP-SERVER.md)

### A2A Server (Multi-Agent Orchestration)

The A2A Server provides a skill-based interface for agents to coordinate workflow operations.

**Key Features**:
- 4 core skills (workflow, task, spec, monitor)
- Agent registration and authentication
- Event subscription and notification
- Multi-agent orchestration patterns

[Read A2A Server Guide](A2A-SERVER.md)

## Integration Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        External Systems                              │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   │
│  │  AI Assistant │  │ Agent Swarm   │  │ Enterprise Systems    │   │
│  │   (Claude)    │  │ (Multi-agent) │  │ (ERP, CRM, etc.)      │   │
│  └───────────────┘  └───────────────┘  └───────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
          │                     │                      │
          │ MCP/STDIO           │ A2A Protocol         │ REST/HTTP
          ▼                     ▼                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      YAWL Integration Layer                          │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   │
│  │  MCP Server   │  │  A2A Server   │  │  Interface A/B        │   │
│  │  (v5.2.0)     │  │  (v6.1.0)     │  │  (REST APIs)          │   │
│  │               │  │               │  │                       │   │
│  │ - 15 Tools    │  │ - 4 Skills    │  │ - Design-time (IA)    │   │
│  │ - 6 Resources │  │ - Agent Mgmt  │  │ - Runtime (IB)        │   │
│  │ - 4 Prompts   │  │ - Events      │  │                       │   │
│  └───────────────┘  └───────────────┘  └───────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        YAWL Engine                                   │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────────────┐   │
│  │  Workflow     │  │  Work Item    │  │  Specification        │   │
│  │  Engine       │  │  Manager      │  │  Repository           │   │
│  └───────────────┘  └───────────────┘  └───────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

## Getting Started

### 1. MCP Server Setup

```bash
# Set environment variables
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=your_password

# Start MCP server
java -cp "yawl-engine.jar:yawl-integration.jar:mcp-sdk.jar" \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

### 2. Configure AI Assistant

Add to Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "yawl": {
      "command": "java",
      "args": [
        "-cp", "yawl-engine.jar:yawl-integration.jar:mcp-sdk.jar",
        "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"
      ],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl",
        "YAWL_USERNAME": "admin",
        "YAWL_PASSWORD": "your_password"
      }
    }
  }
}
```

### 3. Verify Integration

```bash
# List loaded specifications
# In your AI assistant:
"Use the yawl_list_specifications tool to show me loaded workflows"

# Launch a case
"Launch an OrderProcessing case with order ID 12345"
```

## Capabilities Summary

### MCP Server Capabilities

| Category | Count | Examples |
|----------|-------|----------|
| Tools | 15 | `yawl_launch_case`, `yawl_get_work_items`, `yawl_checkin_work_item` |
| Resources | 6 | `yawl://specifications`, `yawl://cases/{caseId}`, `yawl://workitems/{id}` |
| Prompts | 4 | `workflow_analysis`, `task_completion_guide`, `case_troubleshooting` |
| Completions | 3 | Auto-complete for spec IDs, work item IDs, case IDs |

### A2A Server Capabilities (Planned)

| Category | Count | Examples |
|----------|-------|----------|
| Skills | 4 | `/yawl-workflow`, `/yawl-task`, `/yawl-spec`, `/yawl-monitor` |
| Operations | 20+ | `launch`, `checkout`, `complete`, `upload`, `health` |
| Events | 5+ | `case_started`, `case_completed`, `workitem_enabled` |

## Version Compatibility

| YAWL Version | MCP Server | A2A Server | Java | MCP SDK |
|--------------|------------|------------|------|---------|
| 5.2.x | 5.2.0 | N/A | 21+ | 0.17.2 |
| 6.0.x | 5.2.0 | Planned | 25+ | 0.17.2 |
| 6.1.x | 6.1.0 | 1.0.0 | 25+ | 0.17.2+ |

## Related Documentation

- [API Reference](../api/README.md) - REST API documentation
- [Architecture](../architecture/README.md) - System architecture overview
- [Deployment](../deployment/README.md) - Deployment guides
- [Security Checklist](../SECURITY-CHECKLIST-JAVA25.md) - Security requirements
- [Troubleshooting](../TROUBLESHOOTING.md) - Common issues and solutions

## Support

- **Issues**: [GitHub Issues](https://github.com/yawlfoundation/yawl/issues)
- **Discussions**: [GitHub Discussions](https://github.com/yawlfoundation/yawl/discussions)
- **Documentation**: [YAWL Wiki](https://github.com/yawlfoundation/yawl/wiki)
