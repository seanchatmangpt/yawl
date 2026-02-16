# Generic Autonomous Agent Framework - Quick Start Guide

## 5-Minute Quick Start

### Step 1: Create an Agent from Environment Variables

Set environment variables:
```bash
export AGENT_CAPABILITY="Ordering: procurement, purchase orders, approvals"
export YAWL_ENGINE_URL="http://localhost:8080/yawl"
export YAWL_USERNAME="admin"
export YAWL_PASSWORD="YAWL"
export ZAI_API_KEY="your-zai-api-key"
export AGENT_PORT="8091"
```

Create and start agent:
```java
import org.yawlfoundation.yawl.integration.autonomous.*;

public class MyAgentApp {
    public static void main(String[] args) throws IOException {
        AutonomousAgent agent = AgentFactory.fromEnvironment();
        agent.start();

        // Agent runs autonomously
        Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));

        Thread.currentThread().join(); // Keep running
    }
}
```

### Step 2: Test Agent Discovery

```bash
curl http://localhost:8091/.well-known/agent.json
```

Expected output:
```json
{
  "name": "Generic Agent - Ordering",
  "description": "Autonomous agent for procurement, purchase orders, approvals. Discovers work items, reasons about eligibility, produces output dynamically.",
  "version": "5.2.0",
  "capabilities": {
    "domain": "Ordering"
  },
  "skills": [
    {
      "id": "complete_work_item",
      "name": "Complete Work Item",
      "description": "Discover and complete workflow tasks in this agent's domain"
    }
  ]
}
```

### Step 3: Launch a Workflow Case

The agent will automatically discover and complete work items matching its capability.

---

## Custom Configuration (No Environment Variables)

### Example 1: ZAI-based Agent (AI Reasoning)

```java
import org.yawlfoundation.yawl.integration.autonomous.*;
import org.yawlfoundation.yawl.integration.autonomous.strategies.*;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.*;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

public class CustomAgentApp {
    public static void main(String[] args) throws IOException {
        // 1. Define capability
        AgentCapability capability = new AgentCapability(
            "Ordering",
            "procurement, purchase orders, approvals"
        );

        // 2. Initialize ZAI service
        ZaiService zaiService = new ZaiService("your-zai-api-key");

        // 3. Create strategies
        DiscoveryStrategy discovery = new PollingDiscoveryStrategy();
        EligibilityReasoner eligibility = new ZaiEligibilityReasoner(capability, zaiService);
        DecisionReasoner decision = new ZaiDecisionReasoner(zaiService);

        // 4. Build configuration
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(8091)
            .pollIntervalMs(3000)
            .discoveryStrategy(discovery)
            .eligibilityReasoner(eligibility)
            .decisionReasoner(decision)
            .build();

        // 5. Create and start agent
        AutonomousAgent agent = AgentFactory.create(config);
        agent.start();

        System.out.println("Agent started: " + agent.getAgentCard());

        Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));
        Thread.currentThread().join();
    }
}
```

### Example 2: Static Mapping Agent (No AI)

```java
import org.yawlfoundation.yawl.integration.autonomous.*;
import org.yawlfoundation.yawl.integration.autonomous.strategies.*;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.*;

public class StaticMappingAgentApp {
    public static void main(String[] args) throws IOException {
        AgentCapability capability = new AgentCapability("Email", "email delivery, SMTP");

        // Static mapping: task name → agent domain
        // mapping.json: { "Send_Email": "Email", "Send_SMS": "SMS" }
        EligibilityReasoner eligibility = StaticMappingReasoner.fromFile(
            "config/mappings/notification-static.json",
            "Email"
        );

        // Template-based decision (no AI)
        DecisionReasoner decision = new TemplateDecisionReasoner();

        AgentConfiguration config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(8092)
            .pollIntervalMs(5000)
            .discoveryStrategy(new PollingDiscoveryStrategy())
            .eligibilityReasoner(eligibility)
            .decisionReasoner(decision)
            .build();

        AutonomousAgent agent = AgentFactory.create(config);
        agent.start();

        Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));
        Thread.currentThread().join();
    }
}
```

---

## Common Configuration Patterns

### Pattern 1: AI-Powered Complex Workflows
**Use Case:** Order fulfillment, loan approval, incident management
- **Eligibility:** ZaiEligibilityReasoner (LLM analyzes task vs. capability)
- **Decision:** ZaiDecisionReasoner (LLM generates XML)
- **Discovery:** Polling (3-5s interval)

**Why:** Flexible reasoning over unstructured task descriptions

### Pattern 2: Rule-Based Simple Workflows
**Use Case:** Notifications, document routing, approvals
- **Eligibility:** StaticMappingReasoner (task name → agent)
- **Decision:** TemplateDecisionReasoner (predefined templates)
- **Discovery:** Polling (5-10s interval)

**Why:** No AI costs, deterministic, fast

### Pattern 3: Hybrid (AI Eligibility + Template Output)
**Use Case:** Approval workflows with standardized outputs
- **Eligibility:** ZaiEligibilityReasoner (context-dependent routing)
- **Decision:** TemplateDecisionReasoner (fixed output format)
- **Discovery:** Polling (3-5s interval)

**Why:** Flexible routing, predictable outputs

---

## Strategy Selection Guide

### When to Use ZAI Reasoning
✅ Tasks have natural-language descriptions (not just task names)
✅ Routing logic is complex or context-dependent
✅ Output structure varies by task
✅ Willing to accept LLM latency and costs

❌ Avoid if:
- Tasks are deterministic (Send_Email → Email agent)
- High throughput required (LLM adds ~500ms-2s latency)
- Budget constraints (ZAI calls cost money)

### When to Use Static Mapping
✅ Task names are deterministic (Send_Email, Send_SMS)
✅ One-to-one task → agent mapping
✅ High throughput required
✅ No AI budget

❌ Avoid if:
- Task names change frequently (requires config updates)
- Routing depends on input data (not just task name)
- Complex eligibility logic (use ZAI or rules engine)

### When to Use Template Output
✅ Output structure is fixed and known
✅ Output can be generated via simple substitution
✅ Need fast, predictable output generation

❌ Avoid if:
- Output structure varies significantly by task
- Complex output logic (use ZAI or custom reasoner)

---

## Multi-Agent Deployment

### Docker Compose Example

```yaml
version: '3.8'
services:
  ordering-agent:
    image: yawl-agent:5.2
    environment:
      AGENT_CAPABILITY: "Ordering: procurement, purchase orders"
      YAWL_ENGINE_URL: "http://yawl-engine:8080/yawl"
      YAWL_USERNAME: "admin"
      YAWL_PASSWORD: "YAWL"
      ZAI_API_KEY: "${ZAI_API_KEY}"
      AGENT_PORT: "8091"
    ports:
      - "8091:8091"
    depends_on:
      - yawl-engine

  carrier-agent:
    image: yawl-agent:5.2
    environment:
      AGENT_CAPABILITY: "Carrier: transportation, quotes, shipping"
      YAWL_ENGINE_URL: "http://yawl-engine:8080/yawl"
      YAWL_USERNAME: "admin"
      YAWL_PASSWORD: "YAWL"
      ZAI_API_KEY: "${ZAI_API_KEY}"
      AGENT_PORT: "8092"
    ports:
      - "8092:8092"
    depends_on:
      - yawl-engine

  yawl-engine:
    image: yawl-engine:5.2
    ports:
      - "8080:8080"
    environment:
      DB_HOST: "postgres"
      DB_NAME: "yawl"
      DB_USER: "yawl"
      DB_PASSWORD: "yawl"
    depends_on:
      - postgres

  postgres:
    image: postgres:14
    environment:
      POSTGRES_DB: "yawl"
      POSTGRES_USER: "yawl"
      POSTGRES_PASSWORD: "yawl"
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

Launch:
```bash
export ZAI_API_KEY="your-zai-key"
docker-compose up -d
```

---

## Troubleshooting

### Agent not discovering work items
**Check:**
1. Agent connected to engine? (`curl http://localhost:8091/health`)
2. Work items exist? (YAWL Editor → Running Cases)
3. Poll interval too long? (default 3000ms)
4. Eligibility reasoning failing? (check logs for ZAI errors)

**Fix:**
- Verify `YAWL_ENGINE_URL` is correct
- Check ZAI API key is valid
- Reduce poll interval: `POLL_INTERVAL_MS=1000`

### Agent claims wrong work items
**Check:**
1. Capability description matches tasks?
2. ZAI prompt too broad?

**Fix:**
- Refine `AGENT_CAPABILITY` description
- Use static mapping instead of ZAI for deterministic routing

### Output XML invalid
**Check:**
1. ZAI response contains well-formed XML?
2. Root element matches decomposition ID?

**Fix:**
- Improve ZAI prompt (see `DecisionWorkflow.buildPrompt()`)
- Use template-based output for fixed schemas

### Agent crashes on startup
**Check:**
1. Missing environment variables? (AGENT_CAPABILITY, YAWL_ENGINE_URL, ZAI_API_KEY)
2. Engine unreachable? (connection timeout)

**Fix:**
- Set all required environment variables
- Verify engine is running: `curl http://localhost:8080/yawl/ib`

---

## Next Steps

- **Configuration Guide:** See `docs/autonomous-agents/configuration-guide.md` for YAML reference
- **Migration Guide:** See `docs/autonomous-agents/migration-guide.md` for upgrading from PartyAgent
- **API Documentation:** See `docs/autonomous-agents/api-documentation.md` for interface specs
- **Architecture:** See `docs/autonomous-agents/architecture-diagram.md` for system design
- **Thesis:** See `docs/THESIS_Autonomous_Workflow_Agents.md` for research background

---

## Support

- YAWL Manual: https://yawlfoundation.github.io/
- GitHub Issues: https://github.com/yawlfoundation/yawl/issues
- Forum: https://yawlfoundation.org/forum
