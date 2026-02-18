# MCP Quickstart Guide

**30-Second Setup for YAWL MCP Server**

---

## What is MCP?

The Model Context Protocol (MCP) enables AI assistants (Claude, ChatGPT, etc.) to interact with YAWL workflows through standardized tools and resources.

---

## Prerequisites

- Java 25+ installed
- YAWL Engine running on `http://localhost:8080`
- Admin credentials for YAWL Engine

---

## Quick Start (3 Steps)

### 1. Build YAWL

```bash
cd /path/to/yawl
bash scripts/dx.sh all
```

### 2. Configure Environment

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
```

### 3. Start MCP Server

```bash
java -cp target/yawl.jar \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

You should see:
```
YAWL MCP Server started on STDIO transport
Available tools: launch_case, query_workitems, complete_workitem, ...
```

---

## Testing with Claude Desktop

### Configuration

Add to Claude Desktop config (`~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "yawl": {
      "command": "java",
      "args": [
        "-cp", "/path/to/yawl/target/yawl.jar",
        "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"
      ],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl",
        "YAWL_USERNAME": "admin",
        "YAWL_PASSWORD": "YAWL"
      }
    }
  }
}
```

### Usage

Restart Claude Desktop, then:

```
You: Launch the InvoiceProcessing workflow for invoice #12345

Claude: I'll launch the InvoiceProcessing workflow with the invoice data.

[Uses launch_case tool]

Claude: The workflow has been launched. Case ID: case-67890
```

---

## Available Tools

| Tool | Description |
|------|-------------|
| `launch_case` | Start a new workflow case |
| `query_workitems` | List available work items |
| `checkout_workitem` | Claim a work item for processing |
| `complete_workitem` | Finish a work item with data |
| `cancel_case` | Cancel a running case |
| `query_case_state` | Get current case status |
| `list_specifications` | List available workflow definitions |

---

## Example Tool Calls

### Launch a Case

```json
{
  "name": "launch_case",
  "arguments": {
    "specification_id": "InvoiceProcessing-1.0",
    "case_data": {
      "invoice_id": "12345",
      "amount": 1500.00,
      "vendor": "Acme Corp"
    }
  }
}
```

### Query Work Items

```json
{
  "name": "query_workitems",
  "arguments": {
    "status": "Enabled",
    "specification_id": "InvoiceProcessing-1.0"
  }
}
```

### Complete a Work Item

```json
{
  "name": "complete_workitem",
  "arguments": {
    "workitem_id": "wi-12345:review",
    "output_data": {
      "approved": true,
      "reviewer_notes": "Invoice verified"
    }
  }
}
```

---

## Production Deployment

### SSE Transport (HTTP)

For multi-client production use, switch to SSE transport:

```bash
export MCP_TRANSPORT=sse
export MCP_PORT=3000

java -cp target/yawl.jar \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

### Docker

```dockerfile
FROM eclipse-temurin:25-jre

ENV YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
ENV YAWL_USERNAME=admin
ENV YAWL_PASSWORD=${YAWL_PASSWORD}

COPY target/yawl.jar /app/yawl.jar

EXPOSE 3000

CMD ["java", "-cp", "/app/yawl.jar", \
     "org.yawlfoundation.yawl.integration.mcp.YawlMcpServer"]
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: yawl-mcp-server
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: mcp-server
        image: yawl-mcp-server:6.0.0
        env:
        - name: YAWL_ENGINE_URL
          value: "http://yawl-engine:8080/yawl"
        - name: YAWL_USERNAME
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: username
        - name: YAWL_PASSWORD
          valueFrom:
            secretKeyRef:
              name: yawl-credentials
              key: password
        - name: MCP_TRANSPORT
          value: "sse"
        ports:
        - containerPort: 3000
```

---

## Troubleshooting

### "Connection refused to localhost:8080"

**Cause**: YAWL Engine not running

**Solution**: Start the engine first
```bash
# Check engine health
curl http://localhost:8080/yawl/api/ib/health
```

### "Authentication failed"

**Cause**: Invalid credentials

**Solution**: Verify credentials in environment
```bash
echo $YAWL_USERNAME
echo $YAWL_PASSWORD
```

### "Specification not found"

**Cause**: Workflow not loaded

**Solution**: Upload specification via YAWL Editor or API
```bash
# List loaded specifications
curl -u admin:YAWL http://localhost:8080/yawl/api/ia/specifications
```

---

## Related Documentation

- [ADR-023: MCP/A2A CI/CD Deployment](../docs/architecture/decisions/ADR-023-mcp-a2a-cicd-deployment.md)
- [ARCHITECTURE-PATTERNS-JAVA25.md](../.claude/ARCHITECTURE-PATTERNS-JAVA25.md) - Pattern 9: MCP Server CI/CD
- [API-REFERENCE.md](../docs/API-REFERENCE.md) - Full API documentation

---

**Last Updated**: 2026-02-18
**Maintainer**: YAWL Integration Team
