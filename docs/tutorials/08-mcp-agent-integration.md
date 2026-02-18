# Tutorial: MCP Agent Integration with YAWL

By the end of this tutorial you will have the YAWL MCP server running, a Claude agent connected to it via the Model Context Protocol, and a workflow case launched in the YAWL engine through a natural language request. You will also see how the Agent-to-Agent (A2A) protocol provides a second integration path for agent-to-agent workflow invocation.

---

## Prerequisites

You need a running YAWL engine. Complete [Tutorial 7: Docker Development Environment](07-docker-dev-environment.md) first, or start the engine manually (see Step 0 below). You also need:

```bash
java -version
```

Expected: `openjdk version "25.x.x"` (JDK, not JRE).

```bash
mvn -version
```

Expected: `Apache Maven 3.9.x` or higher.

A YAWL admin password. The default development password is `YAWL`.

---

## Step 0: Verify the YAWL engine is running

The MCP server connects to the YAWL engine at startup and fails immediately if the engine is not reachable.

```bash
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect&userid=admin&password=YAWL" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

Expected: a response containing `<success>` with a session handle token. If you see `Connection refused`, start the engine first:

```bash
docker compose up -d
```

Wait until `docker compose ps` shows `healthy`, then retry the `curl` above.

---

## Step 1: Build the integration module

The MCP server lives in `yawl-integration`. Build it and its transitive dependencies:

```bash
mvn -T 1.5C clean package -pl yawl-integration \
  -am -DskipTests
```

The `-am` flag builds all modules that `yawl-integration` depends on. The resulting JAR is at:

```
yawl-integration/target/yawl-integration-6.0.0-Alpha.jar
```

However, `YawlMcpServer.main()` needs all its runtime dependencies on the classpath. The easiest way to run it is from the reactor root with the full classpath assembled by Maven:

```bash
mvn -pl yawl-integration dependency:build-classpath \
  -Dmdep.outputFile=target/integration-cp.txt -q
```

This writes the full dependency classpath to `yawl-integration/target/integration-cp.txt`. You will use it in Step 3.

---

## Step 2: Understand the MCP server configuration

`YawlMcpServer` (in `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`) reads three mandatory environment variables at startup:

| Variable | Required | Description |
|---|---|---|
| `YAWL_ENGINE_URL` | Yes | Base URL of the YAWL engine, e.g. `http://localhost:8080/yawl` |
| `YAWL_USERNAME` | Yes | YAWL admin username, e.g. `admin` |
| `YAWL_PASSWORD` | Yes | YAWL admin password |
| `ZAI_API_KEY` | No | Z.AI API key; when set adds a 16th natural-language tool (`yawl_natural_language`) |
| `ZHIPU_API_KEY` | No | Alias for `ZAI_API_KEY` |

When `ZAI_API_KEY` is absent the server starts with 15 tools (case management, work item management, specification management). Setting `ZAI_API_KEY` adds the `yawl_natural_language` tool which accepts free-form text and routes it through Z.AI function calling to YAWL operations.

The server uses **STDIO transport** (the official MCP Java SDK 0.17.2 `StdioServerTransportProvider`). The MCP client (Claude desktop or another MCP host) launches the server process and communicates over stdin/stdout. The server writes status messages to stderr.

Capabilities registered on startup:

- **Tools (15):** `yawl_launch_case`, `yawl_cancel_case`, `yawl_get_case_status`, `yawl_list_specifications`, `yawl_get_work_items`, `yawl_complete_work_item`, `yawl_checkout_work_item`, `yawl_checkin_work_item`, `yawl_get_specification_data`, `yawl_get_specification_xml`, `yawl_get_specification_schema`, `yawl_get_running_cases`, `yawl_upload_specification`, `yawl_unload_specification`, `yawl_get_work_item_data`
- **Resources (3):** `yawl://specifications`, `yawl://cases`, `yawl://workitems`
- **Resource templates (3):** `yawl://cases/{caseId}`, `yawl://cases/{caseId}/data`, `yawl://workitems/{workItemId}`
- **Prompts (4):** `workflow_analysis`, `task_completion_guide`, `case_troubleshooting`, `workflow_design_review`
- **Completions (3):** Auto-complete for spec identifiers, work item IDs, and case IDs

---

## Step 3: Start the MCP server

Set the required environment variables, then run `YawlMcpServer.main()` directly from Maven using the full classpath:

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL

CLASSPATH="yawl-integration/target/yawl-integration-6.0.0-Alpha.jar:$(cat yawl-integration/target/integration-cp.txt)"

java -cp "$CLASSPATH" \
  org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
```

Expected output on stderr:

```
Starting YAWL MCP Server v5.2.0
Engine URL: http://localhost:8080/yawl
Transport: STDIO (official MCP SDK 0.17.2)
Connected to YAWL engine (session established)
YAWL MCP Server v5.2.0 started on STDIO transport
Capabilities: 15 tools, 3 resources, 3 resource templates, 4 prompts, 3 completions, logging
```

The process now waits on stdin for JSON-RPC 2.0 messages from an MCP client. Do not type into the terminal; the MCP client manages that channel. Press Ctrl+C to stop the server later.

For production use or to run alongside a Claude agent, keep this terminal open (or use a process supervisor). The server shuts down cleanly on SIGINT or SIGTERM via the registered shutdown hook.

---

## Step 4: Connect a Claude agent via MCP

Claude desktop and Claude CLI support MCP servers via a JSON configuration file. Create or edit the MCP configuration at the path your Claude host expects.

**For Claude Desktop (`~/Library/Application Support/Claude/claude_desktop_config.json` on macOS, `%APPDATA%\Claude\claude_desktop_config.json` on Windows):**

```json
{
  "mcpServers": {
    "yawl": {
      "command": "java",
      "args": [
        "-cp",
        "/absolute/path/to/yawl/yawl-integration/target/yawl-integration-6.0.0-Alpha.jar:/absolute/path/to/dependencies/*",
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

Replace `/absolute/path/to/yawl` with the actual absolute path to your YAWL checkout. The `command` and `args` array is how Claude Desktop launches the MCP server process; it manages the stdin/stdout channel automatically.

After saving this file, restart Claude Desktop. You will see a hammer icon in the Claude chat interface confirming that YAWL tools are available.

**For Claude CLI (claude-code or claude-cli) using the `--mcp-config` flag:**

```bash
claude --mcp-config /path/to/mcp-servers.json
```

Where `mcp-servers.json` uses the same format as the desktop config above.

---

## Step 5: Invoke a workflow via natural language

With Claude connected to the YAWL MCP server, open a new conversation and type a natural language request. Claude routes it to the appropriate YAWL tool.

**Request:**

```
List all workflow specifications loaded in the YAWL engine.
```

Claude internally calls the `yawl_list_specifications` tool. The tool invocation looks like this in Claude's tool-use trace:

```json
{
  "name": "yawl_list_specifications",
  "input": {}
}
```

The tool returns a JSON array of `SpecificationData` objects from the engine. Claude presents them as a readable list.

**Request:**

```
Launch a new case of the OrderProcessing workflow.
```

Claude calls `yawl_launch_case`:

```json
{
  "name": "yawl_launch_case",
  "input": {
    "specIdentifier": "OrderProcessing",
    "specVersion": "0.1",
    "specUri": "OrderProcessing"
  }
}
```

The tool calls `InterfaceB_EnvironmentBasedClient.launchCase(YSpecificationID, null, null, sessionHandle)` and returns the new case ID.

**Request:**

```
Show me the work items for case 1.
```

Claude calls `yawl_get_work_items`:

```json
{
  "name": "yawl_get_work_items",
  "input": {
    "caseId": "1"
  }
}
```

---

## Step 6: Observe the result in the engine

Confirm the case was launched by querying the engine directly:

```bash
# First get a session handle
SESSION=$(curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=connect&userid=admin&password=YAWL" \
  -H "Content-Type: application/x-www-form-urlencoded" | \
  grep -oP '(?<=<success>).*(?=</success>)')

# List running cases
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=getAllRunningCases&sessionHandle=$SESSION" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

Expected: an XML response listing the active case IDs. You should see the case ID that Claude reported when it launched the workflow.

```xml
<response>
  <success>
    <case id="1">
      <specification>OrderProcessing</specification>
      <startTime>2026-02-18T10:23:45Z</startTime>
    </case>
  </success>
</response>
```

List work items for the case:

```bash
curl -s -X POST "http://localhost:8080/yawl/ib" \
  -d "action=getWorkItemsForCase&caseID=1&sessionHandle=$SESSION" \
  -H "Content-Type: application/x-www-form-urlencoded"
```

Each `<workItem>` element is a `WorkItemRecord`. The `status` field shows `Enabled` for tasks waiting to be claimed by a participant or agent.

---

## Step 7: Use the Spring-managed MCP application (alternative entry point)

The repository also includes `YawlMcpSpringApplication` in `src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpSpringApplication.java`. This entry point demonstrates the Spring DI integration pattern: it wires `InterfaceB_EnvironmentBasedClient`, `InterfaceA_EnvironmentBasedClient`, `YawlMcpSessionManager`, and `YawlMcpToolRegistry` as Spring-managed beans, and registers the `LaunchCaseTool` and `SpecificationsResource` as custom extensions.

Run it the same way as the standalone server, substituting the main class:

```bash
java -cp "$CLASSPATH" \
  org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSpringApplication
```

Expected startup log:

```
INFO: Starting YAWL MCP Spring Application
INFO: Configuration loaded:
INFO:   Engine URL: http://localhost:8080/yawl
INFO:   Username: admin
INFO:   Transport: STDIO
INFO:   Z.AI enabled: false
INFO: Registered custom tool: yawl_launch_case_spring
INFO: Registered custom resource: yawl://custom/specifications
INFO: Starting MCP server...
INFO: YAWL MCP Spring Server started successfully!
INFO: Total tools: 16 (1 custom)
INFO: Total resources: 4 (1 custom)
INFO: Total resource templates: 3 (0 custom)
INFO: YAWL MCP Server is now running. Press Ctrl+C to stop.
```

The Spring application demonstrates the extension points: implement `YawlMcpTool` to add custom tools, implement `YawlMcpResource` to add custom resources, then register them via `YawlMcpToolRegistry.registerTool()` and `YawlMcpResourceRegistry.registerResource()`.

---

## Step 8 (Optional): Agent-to-Agent (A2A) protocol

The A2A protocol provides a second integration path. `YawlA2AServer` exposes YAWL as an A2A agent over HTTP/REST so that other AI agents can discover its capabilities via the agent card and invoke workflow operations by sending natural language messages.

The A2A server runs independently of the MCP server. It needs one authentication scheme configured: JWT, API key, or SPIFFE mTLS. For local development, use API key authentication.

Set environment variables:

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
export A2A_PORT=8082
export A2A_API_KEY_MASTER=my-hmac-master-secret-16chars
export A2A_API_KEY=my-development-api-key
```

Run the A2A server:

```bash
java -cp "$CLASSPATH" \
  org.yawlfoundation.yawl.integration.a2a.YawlA2AServer
```

Expected output:

```
Starting YAWL A2A Server v5.2.0
Engine URL: http://localhost:8080/yawl
A2A Port: 8082
Auth schemes: api-key
YAWL A2A Server v5.2.0 started on port 8082
Agent card: http://localhost:8082/.well-known/agent.json
Authentication: api-key
```

Fetch the agent card (no authentication required for this endpoint):

```bash
curl -s http://localhost:8082/.well-known/agent.json | python3 -m json.tool
```

The agent card lists YAWL's four skills: `launch_workflow`, `query_workflows`, `manage_workitems`, `cancel_workflow`. Another A2A-compatible agent can read this card and start sending messages.

Send a message to launch a workflow:

```bash
curl -s -X POST "http://localhost:8082/" \
  -H "Authorization: Bearer my-development-api-key" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "id": 1,
    "params": {
      "message": {
        "role": "user",
        "parts": [
          {
            "kind": "text",
            "text": "Launch the OrderProcessing workflow"
          }
        ]
      }
    }
  }'
```

`YawlA2AServer` receives the message, passes the text through `YawlAgentExecutor.processWorkflowRequest()`, which detects the `launch` intent, calls `InterfaceB_EnvironmentBasedClient.launchCase()`, and returns the result as an A2A agent message.

Expected response body:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "status": {"state": "completed"},
    "message": {
      "role": "agent",
      "parts": [
        {
          "kind": "text",
          "text": "Workflow launched successfully.\n  Specification: OrderProcessing\n  Case ID: 1\n  Status: Running\nUse 'status case 1' to check progress."
        }
      ]
    }
  }
}
```

Note: The A2A module source is currently excluded from the default Maven build (`**/a2a/**` is in the `<excludes>` list in `yawl-integration/pom.xml`) because the A2A Java SDK is not yet published to Maven Central. To compile `YawlA2AServer`, install the A2A SDK locally first, then remove the A2A exclude entries from `yawl-integration/pom.xml`.

---

## What happened

- `YawlMcpServer.start()` calls `InterfaceB_EnvironmentBasedClient.connect(username, password)` to authenticate with the YAWL engine and obtain a `sessionHandle`.
- All 15 tool specifications are created by `YawlToolSpecifications.createAll()` with the live `interfaceBClient`, `interfaceAClient`, and `sessionHandle` captured in lambdas.
- The `McpServer.sync()` builder from the official MCP Java SDK 0.17.2 registers these tools along with resources, resource templates, prompts, and completions, then starts the `StdioServerTransportProvider`.
- When Claude sends a tool invocation (e.g., `yawl_launch_case`), the SDK deserializes the JSON parameters, calls the registered tool handler, which calls the real YAWL engine API, and returns the result as an MCP `CallToolResult`.
- The shutdown hook (registered via `Runtime.getRuntime().addShutdownHook()` with a virtual thread) calls `McpSyncServer.closeGracefully()` and `InterfaceB_EnvironmentBasedClient.disconnect(sessionHandle)` on process exit.

---

## Next steps

- To add a custom MCP tool backed by YAWL business logic, implement `YawlMcpTool` and register it with `YawlMcpToolRegistry` as shown in `LaunchCaseTool`.
- To add a custom MCP resource, implement `YawlMcpResource` and register it with `YawlMcpResourceRegistry` as shown in `SpecificationsResource`.
- To enable natural language routing through Z.AI, set `ZAI_API_KEY` before starting the server. The `yawl_natural_language` tool will appear and accept free-form workflow requests.
- To deploy the MCP server alongside the engine in Docker, add a second service to `docker-compose.yml` using the same `yawl-engine:6.0.0-alpha` image with `ENTRYPOINT` overriding to run `YawlMcpServer.main()` instead of the Spring Boot application.
- For production A2A deployments, see `YawlA2AServer` and `CompositeAuthenticationProvider.production()`, which selects JWT, API key, and SPIFFE mTLS schemes from environment variables.
