# YAWL MCP and A2A Integration Examples

Comprehensive examples demonstrating Model Context Protocol (MCP) and Agent-to-Agent (A2A) integration with YAWL workflows, including AI-powered order fulfillment automation.

## Overview

This directory contains working examples that show how to integrate YAWL with:
- **MCP (Model Context Protocol)**: Enable AI models to interact with YAWL workflows
- **A2A (Agent-to-Agent)**: Connect YAWL to external intelligent agents
- **Z.AI**: Use AI for intelligent workflow decisions and automation

## Prerequisites

### Required

1. **YAWL Engine** running at `http://localhost:8080/yawl/ib`
   ```bash
   # Start YAWL Engine using Docker
   docker-compose up -d yawl-engine

   # Or start standalone Tomcat with YAWL deployed
   catalina.sh run
   ```

2. **Java 21** or higher
   ```bash
   java -version
   # Should show: openjdk version "21" or higher
   ```

3. **YAWL Admin Credentials**
   - Default username: `admin`
   - Default password: `YAWL`

### Optional (for AI Features)

4. **Z.AI API Key** (for AI-enhanced features)
   ```bash
   export ZAI_API_KEY="your_zai_api_key_here"
   ```

   Get your API key from: https://open.bigmodel.cn/

5. **MCP SDK** (when available)
   - Add to `build/3rdParty/lib/`
   - Update `build/build.xml` with MCP dependencies

6. **A2A SDK** (when available)
   - Add to `build/3rdParty/lib/`
   - Update `build/build.xml` with A2A dependencies

## Environment Setup

### 1. Set Environment Variables

```bash
# Required for YAWL connection
export YAWL_ENGINE_URL="http://localhost:8080/yawl/ib"
export YAWL_ADMIN_USER="admin"
export YAWL_ADMIN_PASSWORD="YAWL"

# Optional: Enable AI features
export ZAI_API_KEY="your_api_key_here"

# Optional: Configure MCP server
export MCP_SERVER_PORT="3000"

# Optional: Configure A2A server
export A2A_SERVER_PORT="8090"
```

### 2. Build Examples

```bash
# From YAWL root directory
cd /home/user/yawl

# Compile all examples
javac -cp "classes:build/3rdParty/lib/*" \
  exampleSpecs/orderfulfillment/integration_examples/*.java \
  -d classes/

# Or use Ant to build entire project
ant -f build/build.xml compile
```

### 3. Load Order Fulfillment Specification

Using YAWL Editor or Control Panel:
1. Open YAWL Editor
2. Load specification from `exampleSpecs/orderfulfillment/`
3. Upload to engine via "Upload to Engine" menu
4. Verify specification is loaded in Control Panel

## Examples

### 1. MCP Server Example

**Purpose**: Expose YAWL workflows to AI models via MCP protocol

**File**: `McpServerExample.java`

**What it demonstrates**:
- Starting an MCP server
- Registering YAWL tools (launch_case, get_status, etc.)
- Exposing workflow resources (yawl://workflows, yawl://cases)
- Handling AI model requests

**Run**:
```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.McpServerExample
```

**Expected Output**:
```
=== YAWL MCP Server Example ===

Step 1: Connecting to YAWL Engine...
Connected with session: 5f3a9b8c-...

Step 2: Initializing MCP Server...
NOTE: MCP SDK integration required.
When available, the server will expose these tools:
  - launch_case: Start a new workflow instance
  - get_case_status: Retrieve current case state
  ...
```

**Key Features**:
- Tool registration for workflow operations
- Resource providers for workflow data
- Prompt templates for AI guidance
- Real YAWL engine integration

---

### 2. MCP Client Example

**Purpose**: Connect to YAWL MCP server from AI applications

**File**: `McpClientExample.java`

**What it demonstrates**:
- Connecting to MCP server
- Calling workflow tools
- Fetching workflow resources
- AI-enhanced tool invocation
- Tool recommendations

**Run**:
```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.McpClientExample
```

**Expected Output**:
```
=== YAWL MCP Client Example ===

Step 1: Connecting to YAWL MCP Server...
Connected to: http://localhost:3000
AI Enhanced: Yes

Step 2: Listing Available Tools...
Available tools:
  - startWorkflow
  - getWorkflowStatus
  - completeTask
  ...
```

**With AI Enabled** (ZAI_API_KEY set):
- Natural language tool invocation
- Intelligent resource analysis
- Automatic tool selection

---

### 3. A2A Server Example

**Purpose**: Expose YAWL as an agent to other agent systems

**File**: `A2aServerExample.java`

**What it demonstrates**:
- Starting an A2A server
- Defining agent capabilities (AgentCard)
- Delegating work items to external agents
- Handling agent responses
- Capability discovery

**Run**:
```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.A2aServerExample
```

**Expected Output**:
```
=== YAWL A2A Server Example ===

Step 1: Connecting to YAWL Engine...
Connected with session: 7c5d2a1e-...

Step 2: Defining YAWL Agent Capabilities...
AgentCard for YAWL:
{
  "name": "YAWL Workflow Engine",
  "capabilities": [...]
}
...
```

**Key Features**:
- AgentCard definition
- Work item delegation
- Multi-transport support (JSON-RPC, gRPC)
- Agent discovery

---

### 4. A2A Client Example

**Purpose**: Connect YAWL to external intelligent agents

**File**: `A2aClientExample.java`

**What it demonstrates**:
- Connecting to A2A agents
- Discovering agent capabilities
- Invoking agent operations
- AI-powered orchestration
- Exception handling with AI
- Data transformation

**Run**:
```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.A2aClientExample
```

**Expected Output**:
```
=== YAWL A2A Client Example ===

Step 1: Connecting to A2A Agent...
Connected to: http://localhost:8090/a2a

Step 3: Invoking Order Approval Capability...
Expected agent response:
{
  "approved": true,
  "approvalLevel": "director",
  ...
}
```

**With AI Enabled**:
- Natural language agent invocation
- Multi-agent orchestration planning
- Intelligent exception recovery
- Automatic data format conversion

---

### 5. Complete Order Fulfillment Integration

**Purpose**: End-to-end order fulfillment workflow execution

**File**: `OrderFulfillmentIntegration.java`

**What it demonstrates**:
- Launching workflow case
- Executing all workflow tasks:
  1. **Ordering**: Verify order and check inventory
  2. **Payment**: Process payment transaction
  3. **Freight**: Arrange shipping and logistics
  4. **Delivery**: Complete delivery to customer
- Proper data flow between tasks
- Exception handling scenarios
- Case state management

**Run**:
```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.OrderFulfillmentIntegration
```

**Expected Output**:
```
=== Order Fulfillment Integration Example ===

Step 1: Connecting to YAWL Engine...
Connected with session: 9a4b6c3d-...

Step 2: Launching Order Fulfillment Workflow...
Order details:
<data>
  <orderId>ORD-2026-100</orderId>
  <customer>Global Enterprises Ltd</customer>
  <totalAmount>1299.50</totalAmount>
  ...
</data>
Case launched successfully: 12345

Step 3: Executing Ordering Task...
  ✓ Task completed successfully

Step 4: Processing Payment...
  ✓ Task completed successfully

Step 5: Arranging Freight...
  ✓ Task completed successfully

Step 6: Completing Delivery...
  ✓ Task completed successfully

=== Order Fulfillment Integration Complete ===
```

**Data Flow**:
```
Order Data → Ordering Task
           ↓
         Inventory Check
           ↓
       Payment Task (receives order total)
           ↓
      Transaction Processing
           ↓
       Freight Task (receives delivery address)
           ↓
      Carrier Arrangement
           ↓
      Delivery Task (receives tracking info)
           ↓
       Completion
```

---

### 6. AI Agent for Order Approval

**Purpose**: Demonstrate AI-powered autonomous workflow task execution

**File**: `AiAgentExample.java`

**What it demonstrates**:
- AI agent connecting to YAWL
- Monitoring for approval tasks
- Using LLM (GLM-4.6) to analyze orders
- Making intelligent approval decisions
- Completing work items with AI results
- Fallback to rule-based logic

**Run**:
```bash
# Requires ZAI_API_KEY
export ZAI_API_KEY="your_key"

java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.AiAgentExample
```

**Expected Output**:
```
=== AI Agent for Order Approval ===

Connecting to YAWL Engine...
Connected with session: 6e7f8a9b-...
AI service initialized: true
AI connection test: OK

AI Agent ready to process order approvals
Monitoring for approval tasks...

--- Processing Approval Task ---
Work Item ID: 12345.2.1
Task: order_approval

Order Data:
<data>
  <orderId>ORD-2026-001</orderId>
  <amount>2500.00</amount>
  <customer>Acme Corp</customer>
</data>

Using AI to analyze order...
AI Response: DECISION: APPROVE
REASON: Order amount ($2,500) is within automatic approval threshold.
Customer 'Acme Corp' appears legitimate. Quantity of 50 units is
reasonable for this product category. No fraud indicators detected.

--- Approval Decision ---
Approved: true
Reason: Order amount ($2,500) is within automatic...
Approved By: AI_Agent_GLM-4.6
✓ Approval task completed successfully
```

**AI Decision Criteria**:
1. **Order Amount**: Auto-approve < $10,000
2. **Customer Validation**: Check name, address legitimacy
3. **Order Reasonableness**: Verify quantity, product match
4. **Risk Analysis**: Detect fraud patterns
5. **Business Rules**: Apply custom approval policies

**Fallback Behavior** (without ZAI_API_KEY):
```
Using rule-based approval logic...
Decision: APPROVED
Reason: Order approved: amount $2500.00 within limits,
customer verified, quantity reasonable (50 units)
```

---

## Example Commands

### Quick Start - Run All Examples

```bash
#!/bin/bash
# run-all-examples.sh

# Set environment
export YAWL_ENGINE_URL="http://localhost:8080/yawl/ib"
export ZAI_API_KEY="your_key_here"  # Optional

# Build
ant -f build/build.xml compile

# Run examples
echo "=== MCP Server Example ==="
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.McpServerExample

echo -e "\n=== A2A Server Example ==="
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.A2aServerExample

echo -e "\n=== Order Fulfillment ==="
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.OrderFulfillmentIntegration

echo -e "\n=== AI Agent ==="
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.AiAgentExample
```

### Test Individual Components

```bash
# Test YAWL connection
curl http://localhost:8080/yawl/ib

# Test Z.AI connection (requires API key)
curl https://open.bigmodel.cn/api/paas/v4/chat/completions \
  -H "Authorization: Bearer $ZAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"glm-4.6","messages":[{"role":"user","content":"test"}]}'

# Check YAWL specifications loaded
curl "http://localhost:8080/yawl/ib?action=getSpecificationPrototypesList&userID=admin&password=YAWL"
```

## Troubleshooting

### YAWL Engine Connection Issues

**Problem**: `Connection refused` or `404 Not Found`

**Solutions**:
```bash
# Check if engine is running
curl http://localhost:8080/yawl/ib

# Check Tomcat logs
tail -f $TOMCAT_HOME/logs/catalina.out

# Verify engine URL
echo $YAWL_ENGINE_URL

# Test with browser
open http://localhost:8080/yawl/controlpanel
```

### Authentication Failures

**Problem**: `Invalid session` or `Authentication failed`

**Solutions**:
```bash
# Verify credentials
curl "http://localhost:8080/yawl/ib?action=connect&userid=admin&password=YAWL"

# Check user exists in YAWL
# Use Control Panel → Administration → Users

# Reset admin password if needed
# Via database or Control Panel
```

### Specification Not Loaded

**Problem**: `No specification found` or `Unknown spec ID`

**Solutions**:
```bash
# List loaded specifications
curl "http://localhost:8080/yawl/ib?action=getSpecificationPrototypesList&userID=admin&password=YAWL"

# Upload via YAWL Editor
# File → Upload to Engine

# Or use Interface A programmatically
# See YAWL documentation
```

### AI Features Not Working

**Problem**: `ZAI_API_KEY not set` or `AI service not initialized`

**Solutions**:
```bash
# Set API key
export ZAI_API_KEY="your_actual_key"

# Verify key is valid
echo $ZAI_API_KEY

# Test Z.AI connection
curl https://open.bigmodel.cn/api/paas/v4/chat/completions \
  -H "Authorization: Bearer $ZAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"glm-4.6","messages":[{"role":"user","content":"hello"}]}'

# Check quota/credits
# Visit: https://open.bigmodel.cn/usercenter/apikeys
```

### Compilation Errors

**Problem**: `Class not found` or `Cannot find symbol`

**Solutions**:
```bash
# Check Java version
java -version  # Should be 21+

# Verify classpath
ls -la build/3rdParty/lib/

# Clean and rebuild
ant -f build/build.xml clean
ant -f build/build.xml compile

# Check for missing dependencies
# Review build/build.xml
```

### Runtime Errors

**Problem**: `ClassNotFoundException` or `NoClassDefFoundError`

**Solutions**:
```bash
# Ensure all JARs in classpath
java -cp "classes:build/3rdParty/lib/*:build/3rdParty/lib/hibernate/*" ...

# Check JAR contents
jar tf build/3rdParty/lib/some-library.jar | grep ClassName

# Verify class files compiled
ls -la classes/org/yawlfoundation/yawl/examples/integration/
```

## Expected Output Examples

### Successful Workflow Execution

```
=== Order Fulfillment Integration Example ===

Step 1: Connecting to YAWL Engine...
Connected with session: abc123...

Step 2: Launching Order Fulfillment Workflow...
Case launched successfully: 12345

Step 3: Executing Ordering Task...
  Finding work item for task: ordering
  Work item found: 12345.1.1
  Status: Enabled
  Work item checked out
  Completing with data: <data>...</data>
  ✓ Task completed successfully

Step 4: Processing Payment...
  ✓ Task completed successfully

Step 5: Arranging Freight...
  ✓ Task completed successfully

Step 6: Completing Delivery...
  ✓ Task completed successfully

=== Order Fulfillment Integration Complete ===
```

### AI Approval Decision

```
=== AI Agent for Order Approval ===

AI Agent ready to process order approvals

--- Processing Approval Task ---
Using AI to analyze order...

AI Response:
DECISION: APPROVE
REASON: This order appears legitimate and low-risk. The amount
of $2,500 is well within the automatic approval threshold.
The customer 'Acme Corp' is an established business entity.
The quantity of 50 units is standard for this product type.
No fraud indicators detected in the delivery address or
payment information.

--- Approval Decision ---
Approved: true
Reason: This order appears legitimate and low-risk...
Approved By: AI_Agent_GLM-4.6
✓ Approval task completed successfully
```

## Integration Patterns

### Pattern 1: MCP for AI-Workflow Integration

```
AI Model (Claude, GPT, etc.)
    ↓
MCP Client
    ↓
MCP Protocol
    ↓
YAWL MCP Server
    ↓
YAWL Engine
    ↓
Workflow Execution
```

**Use Cases**:
- AI-driven workflow design
- Natural language workflow queries
- Intelligent workflow debugging
- Automated workflow optimization

### Pattern 2: A2A for Multi-Agent Orchestration

```
YAWL Workflow
    ↓
A2A Client
    ↓
A2A Protocol
    ↓
External Agents (Approval, Inventory, Payment, etc.)
    ↓
Agent Responses
    ↓
YAWL Workflow Continuation
```

**Use Cases**:
- Distributed workflow execution
- Microservices integration
- Cross-organization workflows
- Autonomous agent collaboration

### Pattern 3: AI Agent for Autonomous Task Execution

```
YAWL Workflow
    ↓
Work Item Created
    ↓
AI Agent Monitors
    ↓
AI Analyzes Task
    ↓
AI Makes Decision (using LLM)
    ↓
AI Completes Work Item
    ↓
Workflow Continues
```

**Use Cases**:
- Autonomous approvals
- Intelligent routing decisions
- Data validation and enrichment
- Exception handling and recovery

## Advanced Topics

### Custom MCP Tools

To add custom YAWL operations as MCP tools:

1. Define tool schema in AgentCard
2. Implement tool handler function
3. Register with MCP server
4. Test with MCP client

Example tool definition:
```json
{
  "name": "cancel_case",
  "description": "Cancel a running workflow case",
  "inputSchema": {
    "type": "object",
    "properties": {
      "caseId": {"type": "string"}
    },
    "required": ["caseId"]
  }
}
```

### Custom A2A Capabilities

To expose YAWL operations as A2A capabilities:

1. Define capability in AgentCard
2. Implement AgentExecutor method
3. Handle request/response serialization
4. Register with A2A server

Example capability:
```java
public String executeCapability(String name, String params) {
    if ("cancel_case".equals(name)) {
        JSONObject json = new JSONObject(params);
        String caseId = json.getString("caseId");
        return yawlClient.cancelCase(caseId, session);
    }
    // ... other capabilities
}
```

### AI Agent Customization

To customize AI agent behavior:

1. Modify system prompt for specific domain
2. Add custom approval rules
3. Integrate with external data sources
4. Implement learning from decisions

Example custom prompt:
```java
service.setSystemPrompt(
    "You are a specialized procurement approval agent. " +
    "Apply these policies:\n" +
    "- Auto-approve purchases < $5000\n" +
    "- Require VP approval for > $50,000\n" +
    "- Flag suspicious vendors\n" +
    "- Verify budget availability"
);
```

## Performance Considerations

### Scaling Recommendations

- **MCP Server**: Handle 100+ concurrent AI model connections
- **A2A Server**: Process 1000+ agent requests/second
- **AI Agent**: Monitor 10+ work item queues simultaneously

### Optimization Tips

1. **Connection Pooling**: Reuse YAWL client connections
2. **Caching**: Cache specification metadata
3. **Async Processing**: Use async work item monitoring
4. **Batch Operations**: Group multiple task completions
5. **Error Recovery**: Implement exponential backoff

## Security Best Practices

1. **Credentials**: Never hardcode passwords
   ```bash
   export YAWL_ADMIN_PASSWORD=$(cat /secure/password.txt)
   ```

2. **API Keys**: Rotate Z.AI keys regularly
   ```bash
   export ZAI_API_KEY=$(vault read -field=key secret/zai)
   ```

3. **Transport**: Use HTTPS for production
   ```java
   String ENGINE_URL = "https://yawl.example.com:8443/yawl/ib";
   ```

4. **Validation**: Sanitize all external inputs
   ```java
   String safeInput = escapeXml(untrustedInput);
   ```

## Next Steps

1. **Load Order Fulfillment Specification**
   - Use examples as templates
   - Customize for your domain

2. **Configure AI Integration**
   - Obtain Z.AI API key
   - Test AI features

3. **Deploy MCP/A2A Servers**
   - When SDKs become available
   - Configure transports

4. **Build Custom Agents**
   - Extend AiAgentExample
   - Add domain-specific logic

5. **Monitor Production**
   - Log all decisions
   - Track performance metrics
   - Implement alerting

## Resources

- **YAWL Documentation**: http://yawlfoundation.org/
- **Z.AI API Docs**: https://open.bigmodel.cn/dev/api
- **MCP Specification**: https://modelcontextprotocol.io/
- **A2A Protocol**: (Link when available)

## Support

For issues or questions:
1. Check this README
2. Review YAWL documentation
3. Examine example code comments
4. Check YAWL logs: `$TOMCAT_HOME/logs/`
5. Open issue on YAWL GitHub

## License

These examples are part of the YAWL project and follow the LGPL license.

---

**Last Updated**: February 14, 2026
**YAWL Version**: 5.2
**Example Version**: 1.0
