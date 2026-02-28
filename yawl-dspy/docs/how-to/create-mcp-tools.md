# How to Create MCP Tools for DSPy

## Problem

You want to expose your DSPy programs as MCP (Model Context Protocol) tools for LLM clients.

## Solution

Use `DspyMcpTools` to create MCP tool specifications for your DSPy programs.

### Step 1: Create Program Registry

First, create a `DspyProgramRegistry` with your saved programs:

```java
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramRegistry;
import java.nio.file.Path;

// Initialize registry with program directory
Path programsDir = Path.of("/var/lib/yawl/dspy/programs");
DspyProgramRegistry registry = new DspyProgramRegistry(programsDir, pythonEngine);

// Verify programs are loaded
for (String name : registry.listProgramNames()) {
    System.out.println("Loaded: " + name);
}
```

### Step 2: Create MCP Tools

Create tool specifications for all registered programs:

```java
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.yawlfoundation.yawl.dspy.mcp.DspyMcpTools;

// Create all DSPy MCP tools
List<SyncToolSpecification> tools = DspyMcpTools.createAll(registry);

// Add to your MCP server
for (SyncToolSpecification tool : tools) {
    mcpServer.addTool(tool);
}
```

### Step 3: Register with MCP Server

Integrate with your MCP server implementation:

```java
import io.modelcontextprotocol.server.McpServer;

public class YawlMcpServer {
    private final McpServer server;
    private final DspyProgramRegistry dspyRegistry;

    public YawlMcpServer(DspyProgramRegistry registry) {
        this.dspyRegistry = registry;
        this.server = McpServer.create("yawl-dspy-server", "6.0.0");

        // Register all DSPy tools
        registerDspyTools();
    }

    private void registerDspyTools() {
        List<SyncToolSpecification> tools = DspyMcpTools.createAll(dspyRegistry);
        for (SyncToolSpecification tool : tools) {
            server.addTool(tool);
        }
    }
}
```

### Step 4: Test Tool Execution

Test the tools using an MCP client:

```json
{
  "jsonrpc": "2.0",
  "method": "tools/call",
  "params": {
    "name": "dspy_execute_program",
    "arguments": {
      "program": "worklet_selector",
      "inputs": {
        "workflow_description": "Process customer order",
        "case_context": "VIP customer, priority shipping"
      }
    }
  },
  "id": 1
}
```

### Available Tools

| Tool | Description |
|------|-------------|
| `dspy_execute_program` | Execute a DSPy program with inputs |
| `dspy_list_programs` | List all available programs |
| `dspy_get_program_info` | Get detailed program information |
| `dspy_reload_program` | Hot-reload a program from disk |

### Step 5: Add Custom Tools

Create custom MCP tools for GEPA:

```java
import org.yawlfoundation.yawl.dspy.mcp.GepaMcpTools;

// Add GEPA-specific tools
List<SyncToolSpecification> gepaTools = GepaMcpTools.createAll();
for (SyncToolSpecification tool : gepaTools) {
    server.addTool(tool);
}
```

#### GEPA Tools

| Tool | Description |
|------|-------------|
| `gepa_optimize_workflow` | Generate optimized workflow |
| `gepa_validate_footprint` | Validate behavioral footprint |
| `gepa_score_workflow` | Score against reference patterns |

### Best Practices

1. **Cache registry lookups** - Avoid repeated disk reads
2. **Validate inputs early** - Check required parameters before execution
3. **Return structured errors** - Use JSON error format for client handling
4. **Log all executions** - Track tool usage for monitoring
5. **Handle timeouts** - Set appropriate execution timeouts

### Error Handling

```java
try {
    DspyExecutionResult result = registry.execute(programName, inputs);
    return createSuccessResult(result);
} catch (DspyProgramNotFoundException e) {
    return createErrorResult("Program not found: " + programName);
} catch (Exception e) {
    return createErrorResult("Execution failed: " + e.getMessage());
}
```
