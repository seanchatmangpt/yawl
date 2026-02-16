# Developer Guide: YAWL Autonomous Agent Configuration

**Quick reference for creating and deploying autonomous agents.**

## Quick Start

### 1. Create Agent Configuration

Create a YAML file in `config/agents/{domain}/{agent-name}.yaml`:

```yaml
agent:
  name: "My Agent"
  capability:
    domain: "MyDomain"
    description: "what this agent does, keywords for semantic matching"

  discovery:
    strategy: "polling"
    interval_ms: 3000

  reasoning:
    eligibility_engine: "zai"      # or "static"
    decision_engine: "zai"          # or "template"

    eligibility_prompt: |
      You are an eligibility analyzer...
      Task: {taskName}
      Input: {inputData}
      Respond with JSON: {"eligible": true/false, "confidence": 0.0-1.0, "reason": "..."}

    decision_prompt: |
      You are a decision maker...
      Generate output JSON for the task.

  output:
    format: "xml"

  server:
    port: 8091                      # Must be unique!

yawl:
  engine_url: "${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
  username: "${YAWL_USERNAME:-admin}"
  password: "${YAWL_PASSWORD:-YAWL}"

zai:
  api_key: "${ZAI_API_KEY}"
  model: "${ZAI_MODEL:-GLM-4-Flash}"
```

### 2. Set Environment Variables

```bash
export ZAI_API_KEY="your-zai-api-key"
export YAWL_ENGINE_URL="http://localhost:8080/yawl"  # optional
export YAWL_USERNAME="admin"                          # optional
export YAWL_PASSWORD="YAWL"                          # optional
```

### 3. Load and Run Agent

```java
// Load configuration
AgentConfigLoader loader = AgentConfigLoader.fromFile(
    "/home/user/yawl/config/agents/mydomain/my-agent.yaml");
AgentConfiguration config = loader.build();

// Create and start agent
GenericPartyAgent agent = new GenericPartyAgent(config);
agent.start();
```

## Configuration Patterns

### Pattern 1: ZAI + ZAI (Fully Intelligent)

**Best for:** Complex, variable tasks requiring reasoning.

```yaml
reasoning:
  eligibility_engine: "zai"
  decision_engine: "zai"
  eligibility_prompt: |
    Analyze if this agent should handle the task...
  decision_prompt: |
    Generate intelligent output based on context...
```

**Examples:** ordering-agent.yaml, carrier-agent.yaml

### Pattern 2: Static + ZAI (Fast Routing, Smart Decisions)

**Best for:** Known task names, complex output generation.

```yaml
reasoning:
  eligibility_engine: "static"
  decision_engine: "zai"
  mapping_file: "config/agents/mappings/mydomain-static.json"
  decision_prompt: |
    Generate output for this task...
```

**Examples:** payment-agent.yaml, sms-agent.yaml

### Pattern 3: ZAI + Template (Smart Routing, Fast Output)

**Best for:** Variable task names, structured output.

```yaml
reasoning:
  eligibility_engine: "zai"
  decision_engine: "template"
  eligibility_prompt: |
    Should this agent handle the task?
  template_file: "config/agents/templates/my-output.xml"
```

**Examples:** freight-agent.yaml, email-agent.yaml

### Pattern 4: Static + Template (Maximum Performance)

**Best for:** Well-known workflows, simple data structures.

```yaml
reasoning:
  eligibility_engine: "static"
  decision_engine: "template"
  mapping_file: "config/agents/mappings/mydomain-static.json"
  template_file: "config/agents/templates/my-output.xml"
```

**Examples:** delivered-agent.yaml, alert-agent.yaml

## Static Mapping Files

### Create Mapping File

File: `config/agents/mappings/{domain}-static.json`

```json
{
  "description": "Task-to-agent mappings for MyDomain",
  "version": "1.0",
  "taskMappings": {
    "Exact_Task_Name_1": "MyAgentDomain",
    "Exact_Task_Name_2": "MyAgentDomain",
    "Another_Task": "MyAgentDomain"
  },
  "defaultAgent": "MyAgentDomain",
  "comments": [
    "Task names must match YAWL specification exactly",
    "Task names are case-sensitive"
  ]
}
```

**Important:**
- Task names must match YAWL decomposition names EXACTLY
- Task names are case-sensitive
- Use `defaultAgent` as fallback

## XML Output Templates

### Create Template File

File: `config/agents/templates/{purpose}-output.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- My Output Template -->
<data>
    <Field1>${Field1:-DefaultValue1}</Field1>
    <Field2>${Field2:-DefaultValue2}</Field2>
    <Status>${Status:-Completed}</Status>
    <Date>${Date:-2026-02-16}</Date>
</data>
```

**Variable Syntax:**
- `${VariableName}` - Substitute from input data (error if missing)
- `${VariableName:-Default}` - Substitute or use default value

**Input Data Extraction:**
Template engine extracts variables from:
1. WorkItem input data
2. Task-level data
3. Case-level data

## Port Allocation Guidelines

**Reserved Ranges:**
- 8090: Framework/launcher
- 8091-8095: OrderFulfillment agents
- 8096-8098: Notification agents
- 8099: Reserved
- 9090: Agent registry

**Your Ports:**
- Choose unused port in 8100-8999 range
- Document in agent config
- Ensure unique across all agents

## Prompt Engineering Tips

### Eligibility Prompts

```yaml
eligibility_prompt: |
  You are an eligibility analyzer for a {AGENT_DOMAIN} Agent.

  Given:
  - Task Name: {taskName}
  - Task Description: {taskDescription}
  - Input Data: {inputData}

  Determine if this agent should handle this task.

  The {AGENT_DOMAIN} Agent handles: {LIST_CAPABILITIES}.

  Respond with ONLY a JSON object:
  {
    "eligible": true/false,
    "confidence": 0.0-1.0,
    "reason": "brief explanation"
  }
```

**Best Practices:**
- Be specific about agent capabilities
- Request structured JSON output
- Ask for confidence and reason
- Keep prompt focused and concise

### Decision Prompts

```yaml
decision_prompt: |
  You are the {AGENT_DOMAIN} Agent making decisions for YAWL tasks.

  Task: {taskName}
  Description: {taskDescription}
  Input Data: {inputData}

  Based on the input, make a decision and produce output data.

  {DOMAIN-SPECIFIC INSTRUCTIONS}

  Respond with ONLY a JSON object containing output fields:
  {
    "Field1": "value",
    "Field2": "value",
    "Status": "Approved/Rejected/Completed/etc"
  }
```

**Best Practices:**
- Provide domain-specific decision logic
- Specify expected output fields
- Give examples if complex
- Request JSON for easy parsing

## Environment Variables

### Required
```bash
export ZAI_API_KEY="your-api-key-here"
```

### Optional (with defaults)
```bash
export YAWL_ENGINE_URL="http://localhost:8080/yawl"
export YAWL_USERNAME="admin"
export YAWL_PASSWORD="YAWL"
export ZAI_MODEL="GLM-4-Flash"
export ZAI_BASE_URL="https://open.bigmodel.cn/api/paas/v4/chat/completions"
```

### Usage in Config
```yaml
yawl:
  engine_url: "${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
  username: "${YAWL_USERNAME:-admin}"
  password: "${YAWL_PASSWORD:-YAWL}"

zai:
  api_key: "${ZAI_API_KEY}"
  model: "${ZAI_MODEL:-GLM-4-Flash}"
```

**Syntax:**
- `${VAR}` - Required, error if not set
- `${VAR:-default}` - Optional, use default if not set

## Agent Registry Usage

### Start Registry

```bash
java -cp "build/jar/yawl-lib-5.2.jar:build/3rdParty/lib/*" \
  org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistry 9090
```

### Register Agent

```java
AgentRegistryClient client = new AgentRegistryClient("localhost", 9090);

AgentInfo info = new AgentInfo(
    "agent-unique-id",
    "Agent Display Name",
    agentCapability,
    "agent-host",
    agentPort
);

client.register(info);
```

### Send Heartbeat

```java
// Send periodically (every 10-15 seconds recommended)
client.sendHeartbeat("agent-unique-id");
```

### Query Agents

```java
// Get all agents
List<AgentInfo> allAgents = client.listAll();

// Find agents by capability
List<AgentInfo> orderingAgents = client.queryByCapability("Ordering");
```

### Unregister Agent

```java
client.unregister("agent-unique-id");
```

## Testing Your Configuration

### Quick Test

```java
public class MyAgentTest {
    public static void main(String[] args) throws Exception {
        AgentConfigLoader loader = AgentConfigLoader.fromFile(
            "config/agents/mydomain/my-agent.yaml");
        AgentConfiguration config = loader.build();

        System.out.println("Agent: " + config.getAgentName());
        System.out.println("Port: " + config.getPort());
        System.out.println("Capability: " + config.getCapability());
    }
}
```

### Compile and Run

```bash
javac -d /tmp/test -cp "build/3rdParty/lib/*:classes" MyAgentTest.java
java -cp "/tmp/test:build/3rdParty/lib/*:classes" MyAgentTest
```

## Common Errors

### "ZAI API key is required"
**Solution:** Set `ZAI_API_KEY` environment variable

### "eligibility_engine is required"
**Solution:** Add `reasoning.eligibility_engine` to config

### "port must be between 1 and 65535"
**Solution:** Check `agent.server.port` value

### "Unsupported file format"
**Solution:** Ensure file ends with .yaml, .yml, or .properties

### "Template file does not exist"
**Solution:** Check path in `reasoning.template_file` is correct

### "Mapping file does not exist"
**Solution:** Check path in `reasoning.mapping_file` is correct

## File Locations

All paths are relative to YAWL root (`/home/user/yawl/`):

```
config/agents/
├── schema.yaml                    # Configuration documentation
├── {domain}/
│   └── {agent-name}.yaml          # Your agent configs
├── mappings/
│   └── {domain}-static.json       # Static mapping files
└── templates/
    └── {purpose}-output.xml       # Output templates
```

## Best Practices

### 1. Configuration
- ✓ Use descriptive agent names
- ✓ Include comprehensive capability descriptions
- ✓ Document prompts with comments
- ✓ Test configs before deployment
- ✗ Never hardcode API keys
- ✗ Don't duplicate ports

### 2. Prompts
- ✓ Be specific and focused
- ✓ Request structured output (JSON)
- ✓ Include examples in prompts
- ✓ Test with actual YAWL data
- ✗ Don't make prompts too long
- ✗ Don't ask for multiple formats

### 3. Templates
- ✓ Provide sensible defaults
- ✓ Use `${var:-default}` syntax
- ✓ Match YAWL output schema
- ✓ Test with various inputs
- ✗ Don't assume variables exist
- ✗ Don't use complex logic in templates

### 4. Static Mappings
- ✓ Use exact YAWL task names
- ✓ Provide defaultAgent fallback
- ✓ Document mapping rationale
- ✓ Keep mappings up-to-date
- ✗ Task names are case-sensitive
- ✗ Don't use wildcards or regex

## Getting Help

1. **Schema Documentation:** `config/agents/schema.yaml`
2. **Examples:** `config/agents/{orderfulfillment,notification}/`
3. **Test Program:** `test/org/yawlfoundation/yawl/integration/autonomous/config/YamlConfigTest.java`
4. **Implementation Summary:** `config/agents/IMPLEMENTATION_SUMMARY.md`

## References

- [YAWL Manual](https://yawlfoundation.github.io/yawl/)
- [Jackson YAML](https://github.com/FasterXML/jackson-dataformats-text/tree/master/yaml)
- [Z.AI API](https://open.bigmodel.cn/)

---

**Last Updated:** 2026-02-16
**Version:** 5.2
