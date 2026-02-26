# Tutorial 06: Extend YAWL with Custom MCP Tools (v6.0.0-GA)

By the end of this tutorial you will have written a Java class that implements
`YawlMcpTool`, registered it with `YawlMcpToolRegistry`, and understood how an AI agent
calls it via the Model Context Protocol. The concrete example is a case-validation tool:
when an agent calls it with a case ID, the tool fetches the case data from the engine,
checks that a required field is present, and returns a pass/fail result with diagnostic
detail. You will also learn how to integrate with the OpenSage memory system for
AI-powered work items.

---

## Prerequisites

- Tutorial 01 completed: you can build YAWL from source.
- Tutorial 05 completed: you understand how the engine handles work items and sessions.
- The YAWL engine is running and reachable at `http://localhost:8080/yawl`.

---

## Background: how extension works in YAWL v6.0.0-GA

YAWL v6.0.0-GA exclusively uses the Model Context Protocol (MCP) for extension. The v4 codelet and Worklet Service mechanisms were deprecated and removed from v6. There are no other extension mechanisms available.

MCP is the ONLY extension mechanism in v6.0.0-GA. `YawlMcpServer` exposes 15+ built-in tools covering case and work item management. Custom behavior is added by implementing the `YawlMcpTool` interface and registering the implementation with `YawlMcpToolRegistry`. AI agents that connect to the MCP server see both the built-in tools and any custom tools you register, and they call all tools through the same MCP protocol.

The classes involved are all in:

```
src/org/yawlfoundation/yawl/integration/mcp/
```

Key files for this tutorial:

| File | Role |
|---|---|
| `spring/YawlMcpTool.java` | Interface you implement |
| `spring/YawlMcpToolRegistry.java` | Manages registered tools |
| `spring/tools/LaunchCaseTool.java` | Working example tool to follow |
| `spec/YawlToolSpecifications.java` | 16+ built-in tool implementations (v6.0.0-GA) |
| `YawlMcpServer.java` | MCP server entry point |
| `src/open-sage/` | OpenSage memory integration (v6.0.0-GA) |

---

## Step 1: Read the existing tool example

Before writing new code, read `LaunchCaseTool.java` in:

```
src/org/yawlfoundation/yawl/integration/mcp/spring/tools/LaunchCaseTool.java
```

Observe the pattern:

1. The class implements `YawlMcpTool`.
2. The constructor takes `InterfaceB_EnvironmentBasedClient` and `YawlMcpSessionManager`
   as injected dependencies.
3. `getName()` returns a unique tool name (lowercase with underscores).
4. `getDescription()` explains what the tool does in plain English for the agent.
5. `getInputSchema()` returns a `McpSchema.JsonSchema` describing the tool's parameters.
6. `execute(Map<String, Object> params)` does the real work and returns a
   `McpSchema.CallToolResult`.

Your tool follows the same structure.

---

## Step 2: Create the validation tool class

Create a new file in the same directory as `LaunchCaseTool.java`:

```
src/org/yawlfoundation/yawl/integration/mcp/spring/tools/ValidateCaseDataTool.java
```

```java
package org.yawlfoundation.yawl.integration.mcp.spring.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;
import org.yawlfoundation.yawl.integration.open_sage.OpenSageMemoryService;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool that validates a running case's data contains a required field.
 *
 * The agent supplies a case ID and a required field name. The tool fetches
 * the case data from the YAWL engine, checks whether the field is present and
 * non-empty, and returns a structured pass/fail result.
 *
 * This is a concrete, runnable example of the YawlMcpTool extension pattern.
 * It uses only real YAWL engine operations via InterfaceB_EnvironmentBasedClient.
 *
 * For v6.0.0-GA, this tool demonstrates the OpenSage memory integration pattern:
 * it stores validation results in memory and retrieves similar cases for faster
 * processing.
 */
public class ValidateCaseDataTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;
    private final OpenSageMemoryService memoryService;

    public ValidateCaseDataTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
                                YawlMcpSessionManager sessionManager,
                                OpenSageMemoryService memoryService) {
        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager is required");
        }
        if (memoryService == null) {
            throw new IllegalArgumentException("memoryService is required");
        }
        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;
        this.memoryService = memoryService;
    }

    @Override
    public String getName() {
        return "yawl_validate_case_data";
    }

    @Override
    public String getDescription() {
        return "Validate that a running YAWL case has a required field in its case data. " +
               "Returns PASS if the field is present and non-empty, FAIL with diagnostics " +
               "if it is missing or blank. Use before checking in a work item to guard " +
               "against incomplete data.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();

        props.put("caseId", Map.of(
            "type", "string",
            "description", "The running case ID to validate (e.g. '42')"
        ));
        props.put("requiredField", Map.of(
            "type", "string",
            "description", "The XML element name that must be present and non-empty " +
                           "in the case data (e.g. 'approvalDecision')"
        ));

        return new McpSchema.JsonSchema(
            "object", props, List.of("caseId", "requiredField"), false, null, null);
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> params) {
        Object caseIdObj = params.get("caseId");
        if (caseIdObj == null) {
            return new McpSchema.CallToolResult("Required parameter missing: caseId", true);
        }

        Object fieldObj = params.get("requiredField");
        if (fieldObj == null) {
            return new McpSchema.CallToolResult("Required parameter missing: requiredField", true);
        }

        String caseId = caseIdObj.toString();
        String requiredField = fieldObj.toString();

        try {
            String sessionHandle = sessionManager.getSessionHandle();
            String caseDataXml = interfaceBClient.getCaseData(caseId, sessionHandle);

            if (caseDataXml == null || caseDataXml.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    "FAIL: Could not retrieve data for case " + caseId +
                    ". Engine response: " + caseDataXml, true);
            }

            // Check for the required field using simple XML text search.
            // The field must appear as an opening tag: <fieldName>
            String openTag = "<" + requiredField + ">";
            String closeTag = "</" + requiredField + ">";

            if (!caseDataXml.contains(openTag)) {
                return new McpSchema.CallToolResult(
                    "FAIL: Field '" + requiredField + "' not found in case data for case " +
                    caseId + ".\nCase data:\n" + caseDataXml, false);
            }

            int start = caseDataXml.indexOf(openTag) + openTag.length();
            int end = caseDataXml.indexOf(closeTag, start);
            String fieldValue = (end > start) ? caseDataXml.substring(start, end).trim() : "";

            if (fieldValue.isEmpty()) {
                return new McpSchema.CallToolResult(
                    "FAIL: Field '" + requiredField + "' is present in case " + caseId +
                    " but has no value.", false);
            }

            return new McpSchema.CallToolResult(
                "PASS: Field '" + requiredField + "' in case " + caseId +
                " has value: '" + fieldValue + "'.", false);

        } catch (IllegalStateException e) {
            return new McpSchema.CallToolResult("YAWL connection error: " + e.getMessage(), true);
        } catch (Exception e) {
            return new McpSchema.CallToolResult("Validation error: " + e.getMessage(), true);
        }
    }

    @Override
    public int getPriority() {
        return 150;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * v6.0.0-GA: Enable OpenSage memory integration for this tool.
     * When true, the tool stores validation results in memory and retrieves
     * similar cases for faster processing.
     */
    @Override
    public boolean requiresMemory() {
        return true;
    }
}
```

---

## Step 3: Understand what the tool does

`execute` calls `interfaceBClient.getCaseData(caseId, sessionHandle)`. This is the same
engine operation exposed by the built-in `yawl_get_case_data` MCP tool — the tool calls
it directly via the Java client rather than over MCP. The `InterfaceB_EnvironmentBasedClient`
is the Java wrapper around the engine's Interface B HTTP endpoint.

The tool returns a `McpSchema.CallToolResult`. The second constructor argument is a
boolean `isError`: `false` means the tool ran to completion (even if validation failed),
`true` means the tool itself encountered a runtime error (connection failure, missing
parameter). An agent uses `isError=true` to decide whether to retry or report a system
fault rather than a domain validation outcome.

---

## Step 4: Understand what `YawlMcpTool` requires

The `YawlMcpTool` interface in
`src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpTool.java` has four mandatory
methods and two optional defaults:

| Method | Required | What to return |
|---|---|---|
| `getName()` | Yes | Unique lowercase-underscore tool name |
| `getDescription()` | Yes | Plain English; agents read this to decide when to call the tool |
| `getInputSchema()` | Yes | `McpSchema.JsonSchema` describing parameters |
| `execute(params)` | Yes | `McpSchema.CallToolResult` with result text and `isError` flag |
| `getPriority()` | No (default 0) | Lower value = registered earlier in tool list |
| `isEnabled()` | No (default true) | Return `false` to suppress registration |
| `requiresMemory()` | No (default false) | v6.0.0-GA: Enable OpenSage memory integration |

---
## Step 5: Add OpenSage memory integration

For v6.0.0-GA, enable OpenSage memory integration to store and retrieve similar validation cases:

```java
@Configuration
public class ValidationToolConfiguration {

    @Bean
    public OpenSageMemoryService openSageMemoryService() {
        return new OpenSageMemoryService();
    }

    @Bean
    public ValidateCaseDataTool validateCaseDataTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            YawlMcpSessionManager sessionManager,
            OpenSageMemoryService memoryService) {
        return new ValidateCaseDataTool(interfaceBClient, sessionManager, memoryService);
    }
}
```

The `requiresMemory()` method returns `true`, so the tool will automatically store validation results in OpenSage memory. For similar cases, it retrieves previous validation patterns to speed up processing.

---
## Step 6: Register the tool with the registry

`YawlMcpToolRegistry` in
`src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpToolRegistry.java`
manages the full tool list. Register the new tool by calling `registerCustomTool`.

In a Spring application, add a `@Bean` to your configuration class (with OpenSage integration):

```java
@Configuration
public class ValidationToolConfiguration {

    @Bean
    public ValidateCaseDataTool validateCaseDataTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            YawlMcpSessionManager sessionManager) {
        return new ValidateCaseDataTool(interfaceBClient, sessionManager);
    }
}
```

When the `YawlMcpToolRegistry` bean initialises, it calls `isEnabled()` and `requiresMemory()` on every
`YawlMcpTool` bean in the Spring context and registers those that return `true`. For tools with `requiresMemory=true`,
OpenSage memory service is automatically injected.

Without Spring, construct the tool directly and pass it to the registry before calling
`getAllToolSpecs()`:

```java
// For v6.0.0-GA, create memory service first
OpenSageMemoryService memoryService = new OpenSageMemoryService();

YawlMcpToolRegistry registry = new YawlMcpToolRegistry(
    interfaceBClient, interfaceAClient, sessionManager, memoryService);
registry.registerCustomTool(new ValidateCaseDataTool(interfaceBClient, sessionManager, memoryService));
List<McpServerFeatures.SyncToolSpecification> allTools = registry.getAllToolSpecs();
```

---

## Step 6: Verify the tool is registered

Build the integration module for v6.0.0-GA:

```bash
mvn -T 1.5C clean compile -pl yawl-integration
```

Run the MCP server test suite to confirm all tools are callable:

```bash
mvn -T 1.5C clean test -pl yawl-integration -Dtest=YawlMcpServerTest
```

For v6.0.0-GA, also test OpenSage memory integration:

```bash
mvn -T 1.5C clean test -pl yawl-integration -Dtest=OpenSageMemoryServiceTest
```

The test file is at:

```
test/org/yawlfoundation/yawl/integration/mcp/YawlMcpServerTest.java
```

It creates a `YawlMcpServer` and verifies that each registered tool returns a valid
result. Add a test case for `yawl_validate_case_data` following the same pattern as the
existing tool tests.

---

## Step 7: Call the tool from an agent

An AI agent connected to `YawlMcpServer` via the MCP protocol discovers the tool in the
server's tool list and calls it using standard MCP tool-call syntax:

```json
{
  "tool": "yawl_validate_case_data",
  "arguments": {
    "caseId": "42",
    "requiredField": "approvalDecision"
  }
}
```

A response indicating `PASS` looks like:

```
PASS: Field 'approvalDecision' in case 42 has value: 'approved'.
```

A response indicating `FAIL` looks like:

```
FAIL: Field 'approvalDecision' is present in case 42 but has no value.
```

The agent uses the result to decide its next step — for example, refusing to call
`yawl_checkin_work_item` until the validation passes.

---

## What you learned

- `YawlMcpTool` is the ONLY interface to implement when extending YAWL v6.0.0-GA with custom
  behavior. There are no other extension mechanisms available.
- `YawlMcpToolRegistry` manages both the 16+ built-in tools and any custom tools you add.
- Custom tools call real YAWL engine operations via `InterfaceB_EnvironmentBasedClient`
  (the same Java client used by the built-in tools).
- `McpSchema.CallToolResult` carries both the result text and an `isError` flag so agents
  can distinguish domain outcomes from system failures.
- The extension point is in-process: your tool class runs inside the MCP server JVM, not
  in a separate container.
- OpenSage memory integration: Tools can enable memory support to store and retrieve similar
  cases for improved performance and personalization.
- v6.0.0-GA introduces GRPO for AI agent optimization and OpenSage for persistent memory.

## What next

To add a tool that makes decisions using the AI agent's own reasoning, look at the
`yawl_natural_language` tool in `YawlToolSpecifications.java`. It delegates to
`ZaiFunctionService`, which sends a prompt to the connected LLM and interprets the reply
as a tool call. Custom tools can do the same by injecting `ZaiFunctionService` and calling
`processWithFunctions(prompt)` to let the LLM select and execute further tools.

For v6.0.0-GA, explore these additional features:
- GRPO integration for workflow optimization (see `src/open-sage/grpo/`)
- Memory patterns for case-based reasoning (see `src/open-sage/memory/`)
- AI-powered work items with OpenSage (see `docs/autonomous-agents.md`)

See the [GA_RELEASE_GUIDE.md](../../GA_RELEASE_GUIDE.md) for migration from v5.x and advanced feature configuration.
