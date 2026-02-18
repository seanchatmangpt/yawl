# How to Implement Dynamic Task Routing (Worklet Service Alternative)

The YAWL v4 Worklet Service is not available in YAWL v6. This guide shows the actual
v6 mechanism for achieving the same result: adding a custom MCP tool to `YawlMcpServer`
that implements conditional workflow routing logic.

## What the Worklet Service Used to Do

In YAWL v4, the Worklet Service evaluated Ripple Down Rule (RDR) trees to decide which
sub-specification to launch when a task fired. The v4 pattern was:

1. A placeholder task fires in the main case.
2. The Worklet Service evaluates a rule tree against live case data.
3. The Worklet Service launches the matching sub-specification as a new case.
4. When the sub-case completes, the Worklet Service checks in the original work item.

## The v6 Approach: A Custom MCP Tool

In YAWL v6, this logic lives in a custom MCP tool. An AI agent calls the tool with case
data; the tool applies its routing rules and interacts with the engine directly via the
existing 15 MCP tools (`yawl_launch_case`, `yawl_checkout_work_item`, etc.).

Adding a custom tool requires two steps:

1. Implement `YawlMcpTool` in the `yawl-integration` module.
2. Register the tool with `YawlMcpToolRegistry`.

## Step 1: Implement the Routing Tool

The relevant source directory is:

```
src/org/yawlfoundation/yawl/integration/mcp/spring/tools/
```

The existing `LaunchCaseTool` in that directory is a working example to follow. Create a
new file `ConditionalRoutingTool.java` in the same package:

```java
package org.yawlfoundation.yawl.integration.mcp.spring.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpTool;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool that routes a work item to one of several sub-specifications based
 * on case data, providing the same conditional routing that the YAWL v4 Worklet
 * Service delivered via RDR trees.
 *
 * The caller (an AI agent) supplies the current case data and a routing key.
 * The tool selects the appropriate sub-specification, launches it as a new case,
 * and returns the sub-case ID. The agent can then monitor the sub-case via
 * yawl_get_case_status and check in the original work item when it completes.
 */
public class ConditionalRoutingTool implements YawlMcpTool {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    /** Maps routing keys to specification identifiers. */
    private static final Map<String, String> ROUTE_MAP = Map.of(
        "high-value",    "HighValueApproval",
        "standard",      "StandardProcess",
        "expedited",     "ExpeditedProcess"
    );

    public ConditionalRoutingTool(InterfaceB_EnvironmentBasedClient interfaceBClient,
                                  YawlMcpSessionManager sessionManager) {
        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager is required");
        }
        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getName() {
        return "yawl_route_work_item";
    }

    @Override
    public String getDescription() {
        return "Select and launch the appropriate sub-workflow for a work item based on " +
               "a routing key derived from case data. Returns the sub-case ID. " +
               "Valid routing keys: high-value, standard, expedited.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("routingKey", Map.of(
            "type", "string",
            "description", "Routing key derived from case data (high-value, standard, expedited)"
        ));
        props.put("caseData", Map.of(
            "type", "string",
            "description", "XML input data to pass to the launched sub-specification"
        ));

        return new McpSchema.JsonSchema(
            "object", props, List.of("routingKey"), false, null, null);
    }

    @Override
    public McpSchema.CallToolResult execute(Map<String, Object> params) {
        Object keyObj = params.get("routingKey");
        if (keyObj == null) {
            return new McpSchema.CallToolResult("Required parameter missing: routingKey", true);
        }
        String routingKey = keyObj.toString();

        String specIdentifier = ROUTE_MAP.get(routingKey);
        if (specIdentifier == null) {
            return new McpSchema.CallToolResult(
                "Unknown routing key '" + routingKey + "'. " +
                "Valid keys: " + String.join(", ", ROUTE_MAP.keySet()), true);
        }

        Object dataObj = params.get("caseData");
        String caseData = dataObj != null ? dataObj.toString() : null;

        try {
            String sessionHandle = sessionManager.getSessionHandle();
            YSpecificationID specId = new YSpecificationID(specIdentifier, "0.1", specIdentifier);
            String subCaseId = interfaceBClient.launchCase(specId, caseData, null, sessionHandle);

            if (subCaseId == null || subCaseId.contains("<failure>")) {
                return new McpSchema.CallToolResult(
                    "Failed to launch sub-case for routing key '" + routingKey +
                    "' (spec: " + specIdentifier + "): " + subCaseId, true);
            }

            return new McpSchema.CallToolResult(
                "Routed to '" + specIdentifier + "'. Sub-case ID: " + subCaseId +
                "\nMonitor progress with yawl_get_case_status, caseId=" + subCaseId, false);

        } catch (IllegalStateException e) {
            return new McpSchema.CallToolResult("YAWL connection error: " + e.getMessage(), true);
        } catch (Exception e) {
            return new McpSchema.CallToolResult("Routing error: " + e.getMessage(), true);
        }
    }

    @Override
    public int getPriority() {
        return 200;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

## Step 2: Register the Tool with the Registry

The `YawlMcpToolRegistry` (`src/org/yawlfoundation/yawl/integration/mcp/spring/YawlMcpToolRegistry.java`)
manages all MCP tools. Register the new tool by passing it to the registry's
`registerCustomTool` method. In a Spring application this is done in a `@Configuration`
class:

```java
@Configuration
public class CustomToolConfiguration {

    @Bean
    public ConditionalRoutingTool conditionalRoutingTool(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            YawlMcpSessionManager sessionManager) {
        return new ConditionalRoutingTool(interfaceBClient, sessionManager);
    }

    @Bean
    public YawlMcpToolRegistry toolRegistry(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            InterfaceA_EnvironmentBasedClient interfaceAClient,
            YawlMcpSessionManager sessionManager,
            ZaiFunctionService zaiFunctionService,
            ConditionalRoutingTool conditionalRoutingTool) {
        YawlMcpToolRegistry registry = new YawlMcpToolRegistry(
            interfaceBClient, interfaceAClient, sessionManager, zaiFunctionService);
        registry.registerCustomTool(conditionalRoutingTool);
        return registry;
    }
}
```

Without Spring, pass the tool directly to the registry's `registerCustomTool` method
before calling `getAllToolSpecs()` to build the MCP server's tool list.

## How an AI Agent Uses This Tool

An agent connected to `YawlMcpServer` via the MCP protocol calls the tool as it would
call any other MCP tool:

```json
{
  "tool": "yawl_route_work_item",
  "arguments": {
    "routingKey": "high-value",
    "caseData": "<data><orderValue>15000</orderValue></data>"
  }
}
```

The tool returns the sub-case ID. The agent then polls `yawl_get_case_status` until
the sub-case completes, then calls `yawl_checkin_work_item` on the original work item
with the sub-case's output data, completing the routing cycle.

## Adapting the Routing Logic

The `ROUTE_MAP` constant in `ConditionalRoutingTool` is intentionally simple. Replace it
with any logic you need:

- A database lookup mapping case attribute values to specification identifiers
- A call to an external rules engine or ML classifier
- A chain of `if`/`else` conditions equivalent to an RDR tree
- A call to the `yawl_natural_language` MCP tool to let the AI agent itself decide

The key point is that the routing decision is made in Java code (or agent reasoning)
rather than in an RDR tree stored in the Worklet Service database.

## Verify

```bash
# Build the integration module
mvn -T 1.5C clean compile -pl yawl-integration

# Confirm the tool compiles and the tool name appears in the tool list
mvn -T 1.5C clean test -pl yawl-integration -Dtest=YawlMcpServerTest
```

The `YawlMcpServerTest` (`test/org/yawlfoundation/yawl/integration/mcp/YawlMcpServerTest.java`)
verifies that all registered tools are callable. Add a test case for the new tool
following the same pattern as the existing tests.
