# Tutorial: MCP/A2A Integration Getting Started

Welcome to YAWL Integration. By the end of this tutorial, you will have:
- Set up the MCP (Model Context Protocol) server
- Connected an AI agent to YAWL
- Built custom MCP tools for workflow operations
- Implemented A2A (Agent-to-Agent Protocol) skill orchestration
- Deployed a distributed agent network

This is a **learning-by-doing** guide. You'll integrate Claude or GPT with YAWL in 30 minutes.

---

## What is Integration in YAWL v6?

YAWL v6 supports two protocols for agent integration:

1. **MCP (Model Context Protocol)** — Claude/GPT calls YAWL as "tools"
   - Language agnostic
   - Works with any LLM (Claude, GPT, Llama, etc.)
   - Request-response semantics

2. **A2A (Agent-to-Agent Protocol)** — Anthropic's protocol for multi-agent orchestration
   - Purpose-built for autonomous agent teams
   - Structured skill definitions
   - Delegation and coordination semantics

Use **MCP** for connecting existing LLMs. Use **A2A** when orchestrating autonomous agents.

---

## Prerequisites

- YAWL 6.0.0 built from source (Tutorial 01)
- Java 21+ (Java 25 recommended)
- Maven 3.9+
- A running YAWL engine (Tutorial 03)
- For MCP: Claude API key (or local Ollama endpoint)
- For A2A: Anthropic A2A SDK installed in Maven (see yawl-integration/README.md)

### Quick Check

```bash
# Verify yawl-integration module
ls -la yawl-integration/pom.xml

# Build the module (may skip A2A classes if SDK unavailable)
mvn -pl yawl-integration clean package -DskipTests
```

---

## Part 1: Set Up the MCP Server

### Overview

The MCP server exposes YAWL engine operations as **tools** that an LLM can call:

```
AI Agent (Claude/GPT)
    ↓
HTTP (MCP Protocol)
    ↓
YawlMcpServer (Spring Boot)
    ↓
YAWL Engine (Local or Remote)
```

### Step 1: Configure the MCP Server

Create a Spring Boot application that hosts the MCP server:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServerConfig;

@SpringBootApplication
public class YawlMcpApplication {

    /**
     * Create and configure the MCP server.
     */
    @Bean
    public YawlMcpServer yawlMcpServer(YawlMcpServerConfig config) {
        return new YawlMcpServer(config);
    }

    /**
     * Configure MCP server options.
     */
    @Bean
    public YawlMcpServerConfig yawlMcpServerConfig() {
        return YawlMcpServerConfig.builder()
            .engineBaseUrl("http://localhost:8080/yawl")
            .engineUser("mcp-service")
            .enginePassword("secure-password")
            .mcpServerName("YAWL MCP Server")
            .mcpServerVersion("6.0.0")
            .enableStatelessEngine(true)
            .enableObservability(true)
            .build();
    }

    public static void main(String[] args) {
        SpringApplication.run(YawlMcpApplication.class, args);
    }
}
```

### Step 2: Create application.yml

```yaml
spring:
  application:
    name: yawl-mcp-server
  boot:
    admin:
      client:
        enabled: false

server:
  port: 9090
  servlet:
    context-path: /mcp

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.yawlfoundation.yawl: DEBUG
    org.springframework: INFO
```

### Step 3: Register Built-in MCP Tools

The MCP server automatically registers these tools:

```
yawl_list_specifications
├─ Lists all deployed YAWL specifications
└─ Returns: [{ uri, name, version, deployedAt }]

yawl_launch_case
├─ Launch a new case from a specification
├─ Params: specURI, caseParams
└─ Returns: caseID

yawl_get_work_items
├─ Get all enabled work items
├─ Params: caseID (optional)
└─ Returns: [{ workItemID, taskID, status, data }]

yawl_checkout_work_item
├─ Check out a work item for processing
├─ Params: workItemID
└─ Returns: checkedOutData

yawl_checkin_work_item
├─ Complete a work item
├─ Params: workItemID, outputData
└─ Returns: status

yawl_get_case_status
├─ Get the current state of a case
├─ Params: caseID
└─ Returns: { status, currentTasks, progress }

yawl_cancel_case
├─ Cancel a running case
├─ Params: caseID
└─ Returns: status
```

---

## Part 2: Call YAWL from an AI Agent

### Step 1: Start the MCP Server

```bash
# Build the MCP server
mvn clean package

# Run it
java -Xmx1g -jar target/yawl-mcp-app.jar &

# Wait for startup
sleep 5

# Verify it's running
curl -s http://localhost:9090/mcp/health | jq .
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "mcp-server": { "status": "UP" }
  }
}
```

### Step 2: Call MCP Tools from Claude

Using the Anthropic Python SDK:

```python
import anthropic
import json

client = anthropic.Anthropic(api_key="your-api-key")

# Define YAWL MCP tools
mcp_tools = [
    {
        "name": "yawl_list_specifications",
        "description": "List all deployed YAWL workflow specifications",
        "input_schema": {
            "type": "object",
            "properties": {},
            "required": []
        }
    },
    {
        "name": "yawl_launch_case",
        "description": "Launch a new case from a specification",
        "input_schema": {
            "type": "object",
            "properties": {
                "specURI": {"type": "string"},
                "caseParams": {"type": "string"}
            },
            "required": ["specURI"]
        }
    },
    {
        "name": "yawl_get_work_items",
        "description": "Get all enabled work items",
        "input_schema": {
            "type": "object",
            "properties": {
                "caseID": {"type": "string"}
            }
        }
    },
    {
        "name": "yawl_checkin_work_item",
        "description": "Complete a work item with output data",
        "input_schema": {
            "type": "object",
            "properties": {
                "workItemID": {"type": "string"},
                "outputData": {"type": "string"}
            },
            "required": ["workItemID", "outputData"]
        }
    }
]

# First, get the list of specs
response = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    max_tokens=1024,
    tools=mcp_tools,
    messages=[
        {
            "role": "user",
            "content": "What YAWL workflow specifications are available?"
        }
    ]
)

# Check if Claude wants to use a tool
for content_block in response.content:
    if content_block.type == "tool_use":
        tool_name = content_block.name
        tool_input = content_block.input

        # Call the tool
        print(f"Claude is calling: {tool_name}")
        print(f"Input: {json.dumps(tool_input, indent=2)}")

        # In a real system, forward this to the MCP server
        # result = call_mcp_tool(tool_name, tool_input)
        # print(f"Result: {result}")
```

### Step 3: Full Agent Loop

Here's a complete agent loop that processes work items:

```python
import anthropic
import json
import requests

def call_mcp_tool(tool_name: str, tool_input: dict) -> str:
    """Forward a tool call to the YAWL MCP server."""
    response = requests.post(
        f"http://localhost:9090/mcp/tools/{tool_name}",
        json=tool_input,
        timeout=30
    )
    response.raise_for_status()
    return json.dumps(response.json())

client = anthropic.Anthropic(api_key="your-api-key")

# Define tools
mcp_tools = [
    # ... (as defined above) ...
]

# Agent loop
messages = [
    {
        "role": "user",
        "content": """You are an order processing agent. Your job is to:
1. List available YAWL specifications
2. Launch a case for order processing
3. Check for enabled work items
4. Process them automatically
5. Check in with appropriate output

Start by listing specifications."""
    }
]

max_iterations = 10
iteration = 0

while iteration < max_iterations:
    iteration += 1

    # Call Claude
    response = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=4096,
        tools=mcp_tools,
        messages=messages
    )

    # Check if we're done
    if response.stop_reason == "end_turn":
        print("Agent completed task")
        for content_block in response.content:
            if hasattr(content_block, "text"):
                print(f"Final response: {content_block.text}")
        break

    # Process tool uses
    if response.stop_reason == "tool_use":
        # Add Claude's response to message history
        messages.append({
            "role": "assistant",
            "content": response.content
        })

        # Process each tool call
        tool_results = []
        for content_block in response.content:
            if content_block.type == "tool_use":
                tool_name = content_block.name
                tool_input = content_block.input

                print(f"\nCalling tool: {tool_name}")
                print(f"Input: {json.dumps(tool_input, indent=2)}")

                # Call the tool
                result = call_mcp_tool(tool_name, tool_input)
                print(f"Result: {result}")

                tool_results.append({
                    "type": "tool_result",
                    "tool_use_id": content_block.id,
                    "content": result
                })

        # Add tool results to message history
        messages.append({
            "role": "user",
            "content": tool_results
        })

print(f"Agent finished after {iteration} iterations")
```

---

## Part 3: Build Custom MCP Tools

### Scenario

You want to add a custom MCP tool for "ApproveOrder" that enforces business logic.

### Step 1: Create a Custom Tool

```java
import org.springframework.stereotype.Component;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpTool;
import org.yawlfoundation.yawl.integration.mcp.McpToolInput;
import org.yawlfoundation.yawl.integration.mcp.McpToolOutput;

import java.util.Map;

/**
 * Custom MCP tool for approving orders with business rule validation.
 */
@Component
public class ApproveOrderTool implements YawlMcpTool {

    @Override
    public String toolName() {
        return "approve_order";
    }

    @Override
    public String toolDescription() {
        return "Approve an order if amount <= $10,000 or escalate for review";
    }

    @Override
    public McpToolOutput execute(McpToolInput input) throws Exception {
        // Extract parameters
        String workItemID = input.getParameter("workItemID", String.class);
        Double orderAmount = input.getParameter("amount", Double.class);
        String reason = input.getParameter("reason", String.class);

        // Business logic: auto-approve small orders
        if (orderAmount <= 10_000) {
            String outputData = String.format(
                "<ApprovalData>" +
                "  <decision>APPROVED</decision>" +
                "  <reason>%s</reason>" +
                "  <amount>%.2f</amount>" +
                "</ApprovalData>",
                reason, orderAmount
            );

            return McpToolOutput.success(Map.of(
                "status", "approved",
                "workItemID", workItemID,
                "outputData", outputData
            ));

        } else {
            // Escalate for manual review
            return McpToolOutput.success(Map.of(
                "status", "escalated",
                "workItemID", workItemID,
                "reason", "Order exceeds auto-approval limit ($10,000)"
            ));
        }
    }

    @Override
    public Map<String, String> toolInputSchema() {
        return Map.of(
            "workItemID", "string",
            "amount", "number",
            "reason", "string"
        );
    }
}
```

### Step 2: Register the Custom Tool

The tool is automatically discovered via Spring component scanning:

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "org.yawlfoundation.yawl.integration.mcp",
    "your.package.name"  // Your custom tools
})
public class YawlMcpApplication {
    // ... rest of configuration ...
}
```

### Step 3: Use the Custom Tool from an Agent

Now Claude can call your custom tool:

```python
# The approve_order tool is automatically added to the tool list
custom_tools = [
    {
        "name": "approve_order",
        "description": "Approve an order if amount <= $10,000 or escalate",
        "input_schema": {
            "type": "object",
            "properties": {
                "workItemID": {"type": "string"},
                "amount": {"type": "number"},
                "reason": {"type": "string"}
            },
            "required": ["workItemID", "amount", "reason"]
        }
    }
]

# Agent uses it
response = client.messages.create(
    model="claude-3-5-sonnet-20241022",
    max_tokens=1024,
    tools=mcp_tools + custom_tools,
    messages=[
        {
            "role": "user",
            "content": "Approve the order for $5,000 (workItemID: 42:review)"
        }
    ]
)
```

---

## Part 4: A2A Agent Orchestration (Optional)

### What is A2A?

A2A (Agent-to-Agent) enables autonomous agents to coordinate with each other:

```
Agent 1 (Order Reviewer)
    ↓ (delegates task)
Agent 2 (Compliance Checker)
    ↓ (reports back)
Agent 1 (makes decision)
```

### Step 1: Install the A2A SDK

```bash
# Clone the A2A SDK
git clone https://github.com/anthropics/a2a-java-sdk.git
cd a2a-java-sdk

# Install to local Maven repository
mvn clean install

# In your yawl-integration pom.xml, uncomment:
# <dependency>
#     <groupId>io.anthropic</groupId>
#     <artifactId>a2a-java-sdk</artifactId>
#     <version>1.0.0</version>
# </dependency>
```

### Step 2: Define Agent Skills

Skills are functions an agent can perform:

```java
import org.yawlfoundation.yawl.integration.a2a.YawlA2ASkill;
import org.yawlfoundation.yawl.integration.a2a.SkillInput;
import org.yawlfoundation.yawl.integration.a2a.SkillOutput;

/**
 * Skill: Review an order for compliance.
 */
@Component
public class ComplianceReviewSkill implements YawlA2ASkill {

    @Override
    public String skillName() {
        return "compliance_review";
    }

    @Override
    public String skillDescription() {
        return "Review an order for compliance with regulations";
    }

    @Override
    public SkillOutput execute(SkillInput input) throws Exception {
        String orderID = input.getParameter("orderID", String.class);
        Double amount = input.getParameter("amount", Double.class);

        // Check compliance rules
        boolean isCompliant = checkCompliance(amount);

        return SkillOutput.success(Map.of(
            "orderID", orderID,
            "isCompliant", isCompliant,
            "reason", isCompliant ? "Passed all checks" : "Failed OFAC screening"
        ));
    }

    private boolean checkCompliance(Double amount) {
        // Simulate OFAC/AML screening
        return amount < 100_000;
    }
}
```

### Step 3: Create a Multi-Agent Workflow

```java
import org.yawlfoundation.yawl.integration.a2a.YawlA2AServer;
import org.yawlfoundation.yawl.integration.a2a.AgentOrchestrator;

@SpringBootApplication
public class YawlA2AApplication {

    @Bean
    public AgentOrchestrator agentOrchestrator() {
        return new AgentOrchestrator()
            .addAgent("order_reviewer", "Claude as Reviewer")
            .addAgent("compliance_checker", "Claude as Compliance")
            .addAgent("final_approver", "Claude as Approver");
    }

    @Bean
    public YawlA2AServer yawlA2AServer(AgentOrchestrator orchestrator) {
        return new YawlA2AServer(orchestrator);
    }

    public static void main(String[] args) {
        SpringApplication.run(YawlA2AApplication.class, args);
    }
}
```

---

## Part 5: Test Your Integration End-to-End

### Step 1: Deploy Everything

```bash
# Terminal 1: Start YAWL engine
cd /path/to/yawl
java -Xmx2g -jar yawl-engine.jar &

# Terminal 2: Start MCP server
cd /path/to/yawl-mcp-app
mvn spring-boot:run

# Wait for both to start (about 30 seconds)
sleep 30

# Verify both are running
curl -s http://localhost:8080/yawl/health | head -5
curl -s http://localhost:9090/mcp/health | head -5
```

### Step 2: Run an End-to-End Test

```bash
python3 << 'EOF'
import anthropic
import requests
import json
import time

# Initialize Claude
client = anthropic.Anthropic(api_key="your-api-key")

# Define available tools
tools = [
    {
        "name": "yawl_list_specifications",
        "description": "List all deployed workflow specifications",
        "input_schema": {"type": "object", "properties": {}, "required": []}
    },
    {
        "name": "yawl_launch_case",
        "description": "Launch a new workflow case",
        "input_schema": {
            "type": "object",
            "properties": {
                "specURI": {"type": "string"},
                "caseParams": {"type": "string"}
            },
            "required": ["specURI"]
        }
    },
    {
        "name": "yawl_get_work_items",
        "description": "Get work items",
        "input_schema": {
            "type": "object",
            "properties": {"caseID": {"type": "string"}},
            "required": ["caseID"]
        }
    }
]

# Simulate MCP tool execution
def execute_tool(name, input):
    try:
        response = requests.post(
            f"http://localhost:9090/mcp/tools/{name}",
            json=input,
            timeout=10
        )
        return json.dumps(response.json())
    except Exception as e:
        return json.dumps({"error": str(e)})

# Run agent loop
messages = [
    {
        "role": "user",
        "content": """Launch an order workflow and tell me what happened.
        Use specURI "OrderApproval" and create a case with order amount $5000."""
    }
]

for i in range(5):
    response = client.messages.create(
        model="claude-3-5-sonnet-20241022",
        max_tokens=1024,
        tools=tools,
        messages=messages
    )

    if response.stop_reason == "end_turn":
        print("Agent completed!")
        for block in response.content:
            if hasattr(block, "text"):
                print(block.text)
        break

    if response.stop_reason == "tool_use":
        messages.append({"role": "assistant", "content": response.content})

        tool_results = []
        for block in response.content:
            if block.type == "tool_use":
                print(f"Executing: {block.name}")
                result = execute_tool(block.name, block.input)
                print(f"Result: {result}")
                tool_results.append({
                    "type": "tool_result",
                    "tool_use_id": block.id,
                    "content": result
                })

        messages.append({"role": "user", "content": tool_results})

    time.sleep(1)
EOF
```

---

## Verification Checklist

Run this comprehensive test:

```bash
#!/bin/bash

echo "=== YAWL Integration Verification ==="
echo ""

# 1. Check YAWL engine
echo "1. Checking YAWL engine..."
if curl -s http://localhost:8080/yawl/health > /dev/null 2>&1; then
    echo "   ✓ Engine running"
else
    echo "   ✗ Engine not running"
    exit 1
fi

# 2. Check MCP server
echo "2. Checking MCP server..."
if curl -s http://localhost:9090/mcp/health | grep -q "UP"; then
    echo "   ✓ MCP server running"
else
    echo "   ✗ MCP server not running"
    exit 1
fi

# 3. Check MCP tools available
echo "3. Checking available MCP tools..."
TOOLS=$(curl -s http://localhost:9090/mcp/tools | jq '.tools | length')
echo "   ✓ Found $TOOLS tools"

# 4. Test tool invocation
echo "4. Testing tool invocation..."
RESULT=$(curl -s -X POST http://localhost:9090/mcp/tools/yawl_list_specifications \
    -H "Content-Type: application/json" \
    -d '{}')
echo "   ✓ Spec listing: $(echo $RESULT | jq '.count') specs available"

echo ""
echo "=== All checks passed! ==="
```

---

## Next Steps

1. **Advanced MCP Patterns** — Read `docs/how-to/configure-agents.md` for multi-agent coordination
2. **A2A Integration** — Read `docs/reference/a2a-server.md` for complete A2A reference
3. **Observability** — Read `docs/how-to/deployment/docker.md` for containerized deployment
4. **API Reference** — Read `docs/reference/api-reference.md` for all available operations

---

## Troubleshooting

### "Connection refused" to MCP server
**Cause:** MCP server not running
**Fix:** `mvn spring-boot:run` from the yawl-mcp-app directory

### "Tool not found" error
**Cause:** Tool name mismatch or server not up
**Fix:** Verify tool name and check server logs: `curl http://localhost:9090/mcp/health`

### "No credentials" error
**Cause:** MCP server can't authenticate to YAWL engine
**Fix:** Verify `engineUser` and `enginePassword` in YawlMcpServerConfig

---

## Success Criteria

By the end of this tutorial, you should:
- [ ] Start the MCP server successfully
- [ ] Call YAWL tools from Claude or an equivalent LLM
- [ ] Create a custom MCP tool
- [ ] Launch a workflow case via agent
- [ ] Check in a work item via agent
- [ ] Understand A2A basics

Congratulations! You've integrated YAWL with AI agents!
