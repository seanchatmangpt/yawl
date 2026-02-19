# Migration Guide: Legacy to Generic Autonomous Agents

## Overview

This guide walks you through migrating from the **deprecated** `orderfulfillment` package to the new **generic** `autonomous` framework in YAWL v6.0.0.

## Deprecated Classes

The following classes are deprecated and will be removed in YAWL v6.0:

| Deprecated Class | Replacement | Migration Effort |
|------------------|-------------|------------------|
| `org.yawlfoundation.yawl.integration.orderfulfillment.OrderfulfillmentLauncher` | `org.yawlfoundation.yawl.integration.autonomous.launcher.GenericWorkflowLauncher` | Low |
| `org.yawlfoundation.yawl.integration.orderfulfillment.PartyAgent` | `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent` | Medium |
| `org.yawlfoundation.yawl.integration.orderfulfillment.EligibilityWorkflow` | `org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiEligibilityReasoner` | Low |
| `org.yawlfoundation.yawl.integration.orderfulfillment.DecisionWorkflow` | `org.yawlfoundation.yawl.integration.autonomous.reasoners.ZaiDecisionReasoner` | Low |

## Why Migrate?

The new autonomous framework provides:

✅ **Generic Workflow Support**: Works with ANY YAWL specification, not just orderfulfillment
✅ **YAML Configuration**: No code changes required to create new agents
✅ **Pluggable Strategies**: Swap discovery, eligibility, and decision logic
✅ **Production-Ready Resilience**: Circuit breakers, retries, health monitoring
✅ **Better Testing**: Decoupled components, easier to test
✅ **Active Maintenance**: Ongoing support and feature development

## Migration Steps

### Step 1: Create Agent Configuration YAML

Replace hardcoded agent logic with YAML configuration.

#### Before (Legacy Code)

```java
// Hardcoded in PartyAgent.java
private static final String AGENT_ID = "shipper-001";
private static final int PORT = 8081;
private static final String[] SKILLS = {"shipping", "route-planning"};
```

#### After (YAML Configuration)

Create `/home/user/yawl/build/autonomous-config/shipper-agent.yaml`:

```yaml
agent:
  id: "shipper-001"
  name: "Shipper Agent"
  port: 8081
  capability:
    domain: "logistics"
    skills:
      - "shipping"
      - "route-planning"

engine:
  url: "http://yawl-engine:8080/yawl"
  username: "admin"
  password: "YAWL"
  pollInterval: 5000

strategies:
  discovery: "a2a"
  eligibility: "zai"
  decision: "zai"
  output: "zai"

zai:
  apiKey: "${ZHIPU_API_KEY}"
  model: "glm-4-flash"
  temperature: 0.1
```

### Step 2: Update Java Launcher Code

#### Before (Legacy Launcher)

```java
import org.yawlfoundation.yawl.integration.orderfulfillment.OrderfulfillmentLauncher;

public class Main {
    public static void main(String[] args) throws Exception {
        String specPath = "exampleSpecs/orderfulfillment.yawl";
        OrderfulfillmentLauncher launcher = new OrderfulfillmentLauncher();
        launcher.uploadAndLaunchCase(specPath);
    }
}
```

#### After (Generic Launcher)

```java
import org.yawlfoundation.yawl.integration.autonomous.launcher.GenericWorkflowLauncher;
import org.yawlfoundation.yawl.engine.YSpecificationID;

public class Main {
    public static void main(String[] args) throws Exception {
        String specPath = "exampleSpecs/orderfulfillment.yawl";

        GenericWorkflowLauncher launcher = new GenericWorkflowLauncher(
            "http://localhost:8080/yawl",
            "admin",
            "YAWL"
        );

        YSpecificationID specId = launcher.uploadSpecification(specPath);
        String caseId = launcher.launchCase(specId, null);

        launcher.waitForCompletion(caseId, 300000);
    }
}
```

### Step 3: Update Agent Instantiation

#### Before (Legacy Agent)

```java
import org.yawlfoundation.yawl.integration.orderfulfillment.PartyAgent;
import org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability;

public class Main {
    public static void main(String[] args) throws Exception {
        AgentCapability capability = new AgentCapability(
            "shipper-001",
            "logistics",
            new String[]{"shipping", "route-planning"}
        );

        PartyAgent agent = new PartyAgent(
            "http://localhost:8080/yawl",
            "admin",
            "YAWL",
            capability,
            8081
        );

        agent.start();
    }
}
```

#### After (Generic Agent)

```java
import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        // Load configuration from YAML
        AgentConfiguration config = AgentConfiguration.fromYaml(
            new File("build/autonomous-config/shipper-agent.yaml")
        );

        // Create and start agent
        GenericPartyAgent agent = new GenericPartyAgent(config);
        agent.start();

        // Agent will run until JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            agent.stop();
        }));
    }
}
```

### Step 4: Update Docker Compose

#### Before (Legacy Docker Compose)

```yaml
services:
  shipper-agent:
    build: .
    command: java -cp yawl.jar org.yawlfoundation.yawl.integration.orderfulfillment.PartyAgent
    environment:
      - AGENT_ID=shipper-001
      - YAWL_ENGINE_URL=http://yawl-engine:8080/yawl
      - AGENT_PORT=8081
```

#### After (Generic Docker Compose)

```yaml
services:
  shipper-agent:
    build: .
    command: java -cp yawl.jar org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent /config/shipper-agent.yaml
    volumes:
      - ./build/autonomous-config/shipper-agent.yaml:/config/shipper-agent.yaml
    environment:
      - ZHIPU_API_KEY=${ZHIPU_API_KEY}
    ports:
      - "8081:8081"
```

### Step 5: Replace Custom Eligibility Logic

If you customized `EligibilityWorkflow`, you have two options:

#### Option A: Use Z.AI Reasoning (Recommended)

No code changes needed! Just configure in YAML:

```yaml
strategies:
  eligibility: "zai"
```

The Z.AI reasoner will intelligently match work items to agent capabilities based on context.

#### Option B: Implement Custom Reasoner

```java
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import java.util.List;

public class MyCustomEligibilityReasoner implements EligibilityReasoner {

    @Override
    public boolean isEligible(WorkItemRecord wir,
                              AgentCapability capability,
                              List<AgentInfo> peers) throws Exception {

        String taskName = wir.getTaskName();

        // Custom logic: match task name to skills
        for (String skill : capability.getSkills()) {
            if (taskName.toLowerCase().contains(skill.toLowerCase())) {
                return true;
            }
        }

        return false;
    }
}
```

Register in configuration:
```yaml
strategies:
  eligibility: "custom"
  customEligibilityClass: "com.mycompany.MyCustomEligibilityReasoner"
```

### Step 6: Replace Custom Decision Logic

#### Before (DecisionWorkflow)

```java
public class MyDecisionWorkflow extends DecisionWorkflow {
    @Override
    public String decide(WorkItemRecord wir) {
        // Custom decision logic
        if (wir.getTaskName().equals("SelectShipper")) {
            return "accept-ground-shipping";
        }
        return "reject";
    }
}
```

#### After (Custom DecisionReasoner)

```java
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import java.util.List;

public class MyDecisionReasoner implements DecisionReasoner {

    @Override
    public String decide(WorkItemRecord wir,
                        AgentCapability capability,
                        List<AgentInfo> peers) throws Exception {

        String taskName = wir.getTaskName();

        if (taskName.equals("SelectShipper")) {
            return "accept-ground-shipping";
        }

        return "reject";
    }
}
```

Configure:
```yaml
strategies:
  decision: "custom"
  customDecisionClass: "com.mycompany.MyDecisionReasoner"
```

### Step 7: Verify Migration

Run the automated migration verification script:

```bash
cd /home/user/yawl
./scripts/verify-migration.sh
```

This will check:
- ✅ All agent configurations are valid YAML
- ✅ No references to deprecated classes in active code
- ✅ Environment variables are set
- ✅ Agents can connect to YAWL Engine
- ✅ Z.AI API is accessible (if used)

## Common Migration Issues

### Issue 1: Missing Environment Variables

**Error:**
```
Configuration error: Z.AI API key not found: ${ZHIPU_API_KEY}
```

**Solution:**
```bash
export ZHIPU_API_KEY="your-api-key-here"
```

Or set in Docker Compose:
```yaml
environment:
  - ZHIPU_API_KEY=${ZHIPU_API_KEY}
```

### Issue 2: Hardcoded Task Names

**Before:**
```java
if (taskName.equals("SelectShipper")) {
    // Hardcoded logic
}
```

**After:**
Use capability-based matching:
```yaml
agent:
  capability:
    skills:
      - "shipper-selection"
```

Let Z.AI match tasks to skills automatically.

### Issue 3: Port Conflicts

**Error:**
```
java.net.BindException: Address already in use
```

**Solution:**
Change port in configuration:
```yaml
agent:
  port: 8081  # Change to available port
```

### Issue 4: Specification Not Found

**Before:**
```java
launcher.uploadAndLaunchCase("orderfulfillment.yawl");
```

**After:**
Use absolute or relative path:
```java
launcher.uploadSpecification("exampleSpecs/orderfulfillment.yawl");
```

## Testing Your Migration

### Unit Tests

Create tests for custom strategies:

```java
import org.junit.Test;
import static org.junit.Assert.*;

public class MyEligibilityReasonerTest {

    @Test
    public void testShippingTaskEligibility() throws Exception {
        MyEligibilityReasoner reasoner = new MyEligibilityReasoner();

        WorkItemRecord wir = createMockWorkItem("SelectShipper");
        AgentCapability capability = new AgentCapability(
            "test-agent",
            "logistics",
            new String[]{"shipping"}
        );

        assertTrue(reasoner.isEligible(wir, capability, null));
    }
}
```

### Integration Tests

Test end-to-end workflow:

```java
@Test
public void testAgentCompletesWorkItem() throws Exception {
    // Start YAWL Engine (test instance)
    YawlEngine engine = startTestEngine();

    // Load agent configuration
    AgentConfiguration config = AgentConfiguration.fromYaml(
        new File("test/config/test-agent.yaml")
    );

    // Start agent
    GenericPartyAgent agent = new GenericPartyAgent(config);
    agent.start();

    // Launch case
    String caseId = engine.launchCase(specId, null);

    // Wait for work item completion
    waitForWorkItemCompletion(caseId, "SelectShipper", 30000);

    // Verify output
    String output = engine.getWorkItemOutput(caseId, "SelectShipper");
    assertNotNull(output);

    // Cleanup
    agent.stop();
    engine.stop();
}
```

## Backward Compatibility

The deprecated classes will remain functional until **YAWL v6.0** (expected Q4 2026).

**Deprecation Timeline:**
- **v5.2** (Current): Legacy classes marked `@Deprecated`, warnings issued
- **v5.3** (Q2 2026): Runtime warnings when using deprecated classes
- **v6.0** (Q4 2026): Legacy classes removed

**Recommendation:** Migrate now to avoid disruption.

## Migration Checklist

- [ ] Create YAML configuration for each agent
- [ ] Update launcher code to use `GenericWorkflowLauncher`
- [ ] Update agent instantiation to use `GenericPartyAgent`
- [ ] Migrate custom eligibility logic (if any)
- [ ] Migrate custom decision logic (if any)
- [ ] Update Docker Compose configurations
- [ ] Set required environment variables
- [ ] Run migration verification script
- [ ] Test agents with actual workflows
- [ ] Update documentation and runbooks
- [ ] Remove deprecated imports
- [ ] Deploy to staging environment
- [ ] Monitor for errors
- [ ] Deploy to production

## Getting Help

If you encounter issues during migration:

1. **Check logs**: Look for detailed error messages
2. **Review examples**: See `build/autonomous-config/` for working configurations
3. **Consult documentation**: [Configuration Guide](configuration-guide.md)
4. **Run verification**: `./scripts/verify-migration.sh`
5. **Contact support**: yawl-support@yawlfoundation.org

## Additional Resources

- [Configuration Guide](configuration-guide.md): Complete YAML reference
- [API Documentation](api-documentation.md): Interface specifications
- [Architecture Overview](README.md): System design and patterns
- [YAWL Manual](https://yawlfoundation.github.io/): Official documentation

## FAQ

**Q: Can I mix legacy and generic agents in the same deployment?**
A: Yes, they can coexist. The YAWL Engine doesn't distinguish between them.

**Q: Do I need Z.AI for generic agents?**
A: No, you can use `"default"` or `"simple"` strategies without Z.AI.

**Q: Will my existing orderfulfillment specifications work?**
A: Yes! The generic framework works with ANY YAWL specification, including orderfulfillment.

**Q: How do I migrate if I heavily customized PartyAgent?**
A: Implement custom strategy interfaces (`EligibilityReasoner`, `DecisionReasoner`, `OutputGenerator`) and register them in configuration.

**Q: What if I don't want to use YAML?**
A: You can create `AgentConfiguration` objects programmatically in Java.

**Q: Is there a performance difference?**
A: Generic agents have similar performance. Z.AI strategies add latency (~100-500ms per API call) but can be mitigated with caching.
