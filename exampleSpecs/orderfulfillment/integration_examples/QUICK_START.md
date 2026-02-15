# Quick Start Guide - YAWL MCP/A2A Integration

Get started with YAWL integration examples in 5 minutes.

## 🚀 Quick Setup

### 1. Start YAWL Engine

```bash
# Option A: Using Docker (recommended)
docker-compose up -d

# Option B: Using existing Tomcat
$TOMCAT_HOME/bin/catalina.sh run
```

Verify: http://localhost:8080/yawl/controlpanel

### 2. Set Environment Variables

```bash
# Required
export YAWL_ENGINE_URL="http://localhost:8080/yawl/ib"

# Optional: Enable AI features
export ZAI_API_KEY="your_zai_api_key"
```

### 3. Run Examples

```bash
cd /home/user/yawl/exampleSpecs/orderfulfillment/integration_examples

# Run all examples
./run-examples.sh

# Or run specific example
./run-examples.sh mcp-server
./run-examples.sh ai-agent
```

## 📋 Example Descriptions

### MCP Server Example
**Purpose**: Expose YAWL workflows to AI models
**Run**: `./run-examples.sh mcp-server`
**Duration**: ~10 seconds
**Output**: Shows how to register YAWL tools for AI access

### MCP Client Example
**Purpose**: Connect AI applications to YAWL
**Run**: `./run-examples.sh mcp-client`
**Duration**: ~15 seconds
**Output**: Demonstrates calling workflow tools from AI

### A2A Server Example
**Purpose**: Expose YAWL as an intelligent agent
**Run**: `./run-examples.sh a2a-server`
**Duration**: ~10 seconds
**Output**: Shows agent capability registration

### A2A Client Example
**Purpose**: Connect YAWL to external agents
**Run**: `./run-examples.sh a2a-client`
**Duration**: ~15 seconds
**Output**: Demonstrates multi-agent orchestration

### Order Fulfillment Integration
**Purpose**: Complete end-to-end workflow execution
**Run**: `./run-examples.sh order-fulfillment`
**Duration**: ~30 seconds
**Output**: Executes full order fulfillment workflow
**Requires**: OrderFulfillment specification loaded

### AI Agent Example
**Purpose**: AI-powered autonomous task execution
**Run**: `./run-examples.sh ai-agent`
**Duration**: ~20 seconds
**Output**: Shows AI making approval decisions
**Requires**: ZAI_API_KEY environment variable

## 🔧 Common Commands

### Check YAWL Connection
```bash
curl http://localhost:8080/yawl/ib
```

### Test Authentication
```bash
curl "http://localhost:8080/yawl/ib?action=connect&userid=admin&password=YAWL"
```

### List Loaded Specifications
```bash
curl "http://localhost:8080/yawl/ib?action=getSpecificationPrototypesList&userID=admin&password=YAWL"
```

### Compile Examples Manually
```bash
cd /home/user/yawl
javac -cp "classes:build/3rdParty/lib/*" \
  exampleSpecs/orderfulfillment/integration_examples/*.java \
  -d classes/
```

### Run Specific Example
```bash
java -cp "classes:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.examples.integration.McpServerExample
```

## ⚡ Quick Test

Test YAWL connectivity and run first example:

```bash
#!/bin/bash

# Test YAWL Engine
echo "Testing YAWL Engine..."
curl -s http://localhost:8080/yawl/ib && echo "✓ Engine OK" || echo "✗ Engine not running"

# Set environment
export YAWL_ENGINE_URL="http://localhost:8080/yawl/ib"

# Run MCP Server example
cd /home/user/yawl/exampleSpecs/orderfulfillment/integration_examples
./run-examples.sh mcp-server
```

## 🐛 Troubleshooting

### Engine Not Running
```bash
# Check if Tomcat is running
ps aux | grep tomcat

# Check if port 8080 is in use
netstat -tulpn | grep 8080

# Start engine
docker-compose up -d yawl-engine
```

### Compilation Errors
```bash
# Check Java version (need 21+)
java -version

# Rebuild YAWL
cd /home/user/yawl
ant -f build/build.xml clean
ant -f build/build.xml compile
```

### AI Features Not Working
```bash
# Check if API key is set
echo $ZAI_API_KEY

# Test Z.AI connection
curl https://open.bigmodel.cn/api/paas/v4/chat/completions \
  -H "Authorization: Bearer $ZAI_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"glm-4.6","messages":[{"role":"user","content":"test"}]}'
```

### Specification Not Found
```bash
# Load OrderFulfillment via YAWL Editor
# Or use Control Panel to upload

# Check loaded specs
curl "http://localhost:8080/yawl/ib?action=getSpecificationPrototypesList&userID=admin&password=YAWL"
```

## 📊 Expected Output Samples

### Successful MCP Server Example
```
=== YAWL MCP Server Example ===

Step 1: Connecting to YAWL Engine...
Connected with session: abc123...

Step 2: Initializing MCP Server...
Tools to be registered:
  - launch_case
  - get_case_status
  ...

=== MCP Server Example Complete ===
```

### Successful AI Agent Run
```
=== AI Agent for Order Approval ===

Connecting to YAWL Engine...
Connected with session: xyz789...
AI service initialized: true

Using AI to analyze order...
AI Response: DECISION: APPROVE
✓ Approval task completed successfully

=== AI Agent Example Complete ===
```

## 🎯 Next Steps

1. **Review Examples**: Read through example source code
2. **Customize**: Modify for your specific workflows
3. **Load Specifications**: Upload your YAWL specifications
4. **Test Integration**: Run examples with real workflows
5. **Deploy Production**: Use as templates for production code

## 📚 Documentation

- **Full README**: `README.md` in this directory
- **YAWL Docs**: http://yawlfoundation.org/
- **Z.AI API**: https://open.bigmodel.cn/dev/api
- **CLAUDE.md**: Project coding standards

## 💡 Tips

- Start with MCP Server example (simplest)
- AI Agent requires API key but shows powerful capabilities
- Order Fulfillment demonstrates complete workflow
- Check logs in `$TOMCAT_HOME/logs/` for issues
- Use `-verbose` flag for detailed Java output

## 🆘 Support

If examples don't work:

1. Check prerequisites in README.md
2. Verify YAWL Engine is running
3. Review error messages carefully
4. Check environment variables
5. Examine YAWL logs

---

**Ready to Go?**

```bash
cd /home/user/yawl/exampleSpecs/orderfulfillment/integration_examples
./run-examples.sh
```

**Questions?** See README.md for comprehensive documentation.
