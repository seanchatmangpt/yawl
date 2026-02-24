# YAWL MCP Server Guide

**Quick integration with Claude Desktop, Claude CLI, and custom AI applications**

## Quick Start (3 Commands)

```bash
# 1. Set environment variables
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=your_password

# 2. Build the MCP server
mvn -pl yawl-integration clean package -DskipTests

# 3. Start the MCP server
java -jar yawl-integration/target/yawl-mcp-server.jar
```

Expected output:
```
Starting YAWL MCP Server v6.0.0
Engine URL: http://localhost:8080/yawl
Transport: STDIO (official MCP SDK 0.17.2)
Connected to YAWL engine (session established)
YAWL MCP Server v6.0.0 started
Capabilities: 15 tools, 6 resources, 4 prompts, 3 completions
```

## Configuration

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `YAWL_ENGINE_URL` | Yes | - | YAWL engine base URL |
| `YAWL_USERNAME` | Yes | - | YAWL admin username |
| `YAWL_PASSWORD` | Yes | - | YAWL admin password |
| `YAWL_MCP_TRANSPORT` | No | `stdio` | Transport: `stdio` or `http` |
| `YAWL_MCP_HTTP_PORT` | No | `8081` | HTTP port (if transport=http) |
| `ZAI_API_KEY` | No | - | Z.AI API key for natural language |

### application.yml

```yaml
yawl:
  mcp:
    enabled: true
    engine-url: ${YAWL_ENGINE_URL}
    username: ${YAWL_USERNAME}
    password: ${YAWL_PASSWORD}
    transport: stdio
    http:
      enabled: false
      port: 8081
      path: /mcp
    zai:
      enabled: ${ZAI_API_KEY:false}
      api-key: ${ZAI_API_KEY}
    connection:
      retry-attempts: 3
      retry-delay-ms: 1000
      timeout-ms: 5000
```

## Claude Desktop Integration

### macOS Configuration

Edit `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "yawl": {
      "command": "java",
      "args": ["-jar", "/path/to/yawl-mcp-server.jar"],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl",
        "YAWL_USERNAME": "admin",
        "YAWL_PASSWORD": "your_password"
      }
    }
  }
}
```

### Windows Configuration

Edit `%APPDATA%\Claude\claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "yawl": {
      "command": "java",
      "args": ["-jar", "C:\\path\\to\\yawl-mcp-server.jar"],
      "env": {
        "YAWL_ENGINE_URL": "http://localhost:8080/yawl",
        "YAWL_USERNAME": "admin",
        "YAWL_PASSWORD": "your_password"
      }
    }
  }
}
```

### Restart Claude Desktop

After updating the configuration:
1. Quit Claude Desktop completely
2. Restart Claude Desktop
3. Look for the MCP icon (hammer) in the bottom-right corner
4. Verify YAWL tools are available

## Claude CLI Integration

### Add MCP Server

```bash
# Add YAWL MCP server to Claude CLI
claude mcp add yawl java -jar /path/to/yawl-mcp-server.jar

# With environment variables
claude mcp add yawl java -jar /path/to/yawl-mcp-server.jar \
  --env YAWL_ENGINE_URL=http://localhost:8080/yawl \
  --env YAWL_USERNAME=admin \
  --env YAWL_PASSWORD=your_password
```

### Verify Connection

```bash
# List configured MCP servers
claude mcp list

# Test YAWL MCP server
claude "List all running YAWL workflows using the yawl_get_running_cases tool"
```

## Custom Tools Development

### Create a Custom Tool

```java
@Component
public class CustomYawlTool implements McpServerTool {

    private final InterfaceB_EnvironmentBasedClient client;

    @Override
    public String getName() {
        return "yawl_custom_action";
    }

    @Override
    public String getDescription() {
        return "Perform custom action on YAWL workflow";
    }

    @Override
    public JsonSchema getInputSchema() {
        return JsonSchema.builder()
            .type("object")
            .properties(Map.of(
                "caseId", Map.of("type", "string", "description", "Case ID"),
                "action", Map.of("type", "string", "description", "Action to perform")
            ))
            .required(List.of("caseId", "action"))
            .build();
    }

    @Override
    public ToolResponse execute(Map<String, Object> arguments) {
        String caseId = (String) arguments.get("caseId");
        String action = (String) arguments.get("action");

        // Implement custom logic
        String result = performAction(caseId, action);

        return ToolResponse.builder()
            .content(List.of(TextContent.of(result)))
            .build();
    }

    private String performAction(String caseId, String action) {
        // Custom implementation
        return "Action '" + action + "' completed for case " + caseId;
    }
}
```

### Register Custom Tool

```java
@Configuration
public class CustomToolConfiguration {

    @Bean
    public CustomYawlTool customYawlTool(InterfaceB_EnvironmentBasedClient client) {
        return new CustomYawlTool(client);
    }
}
```

## Custom Resources Development

### Create a Custom Resource

```java
@Component
public class CaseMetricsResource implements McpServerResource {

    @Override
    public String getUri() {
        return "yawl://metrics/cases";
    }

    @Override
    public String getName() {
        return "Case Metrics";
    }

    @Override
    public String getDescription() {
        return "Aggregate metrics for workflow cases";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public ResourceResponse read() {
        Map<String, Object> metrics = calculateCaseMetrics();
        return ResourceResponse.builder()
            .contents(List.of(ResourceContents.builder()
                .uri(getUri())
                .mimeType(getMimeType())
                .text(objectMapper.writeValueAsString(metrics))
                .build()))
            .build();
    }

    private Map<String, Object> calculateCaseMetrics() {
        // Calculate and return metrics
        return Map.of(
            "totalCases", 1000,
            "activeCases", 42,
            "completedToday", 15,
            "averageDurationMs", 45000
        );
    }
}
```

## Troubleshooting

### Server Fails to Start

**Error**: `Failed to connect to YAWL engine`

**Solutions**:
1. Verify YAWL engine is running: `curl http://localhost:8080/yawl/api/ib/workitems`
2. Check credentials are correct
3. Ensure network connectivity between MCP server and engine
4. Check firewall rules

### Tool Returns Failure

**Error**: `<failure>Unable to complete operation</failure>`

**Solutions**:
1. Check YAWL engine logs for detailed errors
2. Verify session handle is still valid
3. Validate input parameters match expected formats
4. Check database connectivity

### Claude Desktop Not Finding Tools

**Error**: Tools not appearing in Claude Desktop

**Solutions**:
1. Verify configuration file path is correct
2. Ensure JSON is valid (use a JSON validator)
3. Check file permissions on the JAR file
4. Restart Claude Desktop completely
5. Check Claude Desktop logs: `~/Library/Logs/Claude/`

### Session Timeout

**Error**: `Session has expired`

**Solutions**:
1. The MCP server auto-reconnects on session expiry
2. If persistent, increase session timeout in YAWL engine
3. Check for network instability

### Z.AI Integration Not Working

**Error**: `Z.AI integration requires the Z.AI SDK`

**Solutions**:
1. Add Z.AI SDK dependency to `yawl-integration/pom.xml`
2. Set `ZAI_API_KEY` environment variable
3. Verify API key is valid and has credits

## Available Tools (15)

| Tool | Description |
|------|-------------|
| `yawl_launch_case` | Launch a new workflow case |
| `yawl_get_case_status` | Get case status and state |
| `yawl_cancel_case` | Cancel a running case |
| `yawl_suspend_case` | Suspend a running case |
| `yawl_resume_case` | Resume a suspended case |
| `yawl_get_case_data` | Get case data variables |
| `yawl_get_running_cases` | List all running cases |
| `yawl_get_work_items` | List all live work items |
| `yawl_get_work_items_for_case` | List work items for a case |
| `yawl_checkout_work_item` | Claim a work item |
| `yawl_checkin_work_item` | Complete a work item |
| `yawl_skip_work_item` | Skip an optional work item |
| `yawl_list_specifications` | List loaded specifications |
| `yawl_get_specification` | Get specification XML |
| `yawl_upload_specification` | Upload a new specification |

## Version Compatibility

| YAWL MCP Server | MCP SDK | YAWL Engine | Java |
|-----------------|---------|-------------|------|
| 6.0.0 | 0.17.2 | 6.0+ | 25+ |
| 5.2.0 | 0.17.2 | 5.2+ | 21+ |

## References

- [MCP Specification](https://modelcontextprotocol.io/)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
- [YAWL Documentation](https://yawlfoundation.github.io)
- [Claude Desktop MCP](https://docs.anthropic.com/claude/docs/mcp)
