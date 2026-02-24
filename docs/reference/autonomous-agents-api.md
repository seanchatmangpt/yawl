# Autonomous Agents API Documentation

## Overview

This document provides comprehensive API documentation for the YAWL autonomous agent framework.

## Core Interfaces

### AutonomousAgent

**Package:** `org.yawlfoundation.yawl.integration.autonomous`

**Description:** Core interface for autonomous YAWL agents that discover work items, reason about eligibility, produce output, and complete work items without central orchestration.

```java
public interface AutonomousAgent {

    /**
     * Start the agent: HTTP server for discovery and work item processing loop.
     *
     * @throws IOException if server cannot be started
     */
    void start() throws IOException;

    /**
     * Stop the agent: cleanup resources, disconnect from engine, stop server.
     */
    void stop();

    /**
     * Get the agent's capability descriptor for eligibility reasoning.
     *
     * @return the capability descriptor
     */
    AgentCapability getCapability();

    /**
     * Get the agent's A2A discovery card as JSON.
     *
     * @return JSON string representing the agent's discovery card
     */
    String getDiscoveryCard();
}
```

**Implementation:** `GenericPartyAgent`

**Example Usage:**
```java
AgentConfiguration config = AgentConfiguration.fromYaml(
    new File("config/agent.yaml")
);

AutonomousAgent agent = new GenericPartyAgent(config);
agent.start();

// Agent runs until stopped
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    agent.stop();
}));
```

---

### DiscoveryStrategy

**Package:** `org.yawlfoundation.yawl.integration.autonomous.strategies`

**Description:** Strategy interface for discovering peer agents in the system.

```java
public interface DiscoveryStrategy {

    /**
     * Discover available agents in the system.
     *
     * @return list of discovered agent information
     * @throws Exception if discovery fails
     */
    List<AgentInfo> discoverAgents() throws Exception;

    /**
     * Refresh the agent registry (optional operation).
     *
     * @throws Exception if refresh fails
     */
    default void refresh() throws Exception {
        // Optional: override if periodic refresh needed
    }
}
```

**Built-in Implementations:**
- `StaticDiscoveryStrategy`: Uses hardcoded list from configuration
- `A2ADiscoveryStrategy`: Fetches `/.well-known/agent.json` from peer URLs
- `NoneDiscoveryStrategy`: No discovery (single agent mode)

**Custom Implementation Example:**
```java
public class DnsDiscoveryStrategy implements DiscoveryStrategy {

    private final String serviceName;

    public DnsDiscoveryStrategy(String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    public List<AgentInfo> discoverAgents() throws Exception {
        List<AgentInfo> agents = new ArrayList<>();

        // DNS-SD lookup
        Set<String> hostnames = lookupService(serviceName);

        for (String hostname : hostnames) {
            String url = "http://" + hostname;
            AgentInfo info = fetchAgentInfo(url);
            agents.add(info);
        }

        return agents;
    }

    private Set<String> lookupService(String name) {
        // DNS-SD implementation
    }

    private AgentInfo fetchAgentInfo(String url) {
        // Fetch /.well-known/agent.json
    }
}
```

---

### EligibilityReasoner

**Package:** `org.yawlfoundation.yawl.integration.autonomous.strategies`

**Description:** Strategy interface for determining if an agent is eligible to handle a work item.

```java
public interface EligibilityReasoner {

    /**
     * Determine if the agent is eligible to handle this work item.
     *
     * @param wir the work item record
     * @param capability the agent's capability descriptor
     * @param peers list of peer agents (may be null)
     * @return true if eligible, false otherwise
     * @throws Exception if reasoning fails
     */
    boolean isEligible(WorkItemRecord wir,
                      AgentCapability capability,
                      List<AgentInfo> peers) throws Exception;
}
```

**Built-in Implementations:**
- `DefaultEligibilityReasoner`: Simple substring match on task name vs. skills
- `ZaiEligibilityReasoner`: AI-powered reasoning using Z.AI

**Custom Implementation Example:**
```java
public class RuleBasedEligibilityReasoner implements EligibilityReasoner {

    private final Map<String, Predicate<WorkItemRecord>> rules;

    public RuleBasedEligibilityReasoner(Map<String, Predicate<WorkItemRecord>> rules) {
        this.rules = rules;
    }

    @Override
    public boolean isEligible(WorkItemRecord wir,
                              AgentCapability capability,
                              List<AgentInfo> peers) throws Exception {

        String taskName = wir.getTaskName();

        // Check if any capability skill has a matching rule
        for (String skill : capability.getSkills()) {
            Predicate<WorkItemRecord> rule = rules.get(skill);
            if (rule != null && rule.test(wir)) {
                return true;
            }
        }

        return false;
    }
}

// Usage
Map<String, Predicate<WorkItemRecord>> rules = new HashMap<>();
rules.put("shipping", wir ->
    wir.getTaskName().contains("Ship") &&
    parseWeight(wir) < 500
);

EligibilityReasoner reasoner = new RuleBasedEligibilityReasoner(rules);
```

---

### DecisionReasoner

**Package:** `org.yawlfoundation.yawl.integration.autonomous.strategies`

**Description:** Strategy interface for deciding what action to take for an eligible work item.

```java
public interface DecisionReasoner {

    /**
     * Decide what action to take for an eligible work item.
     *
     * @param wir the work item record
     * @param capability the agent's capability descriptor
     * @param peers list of peer agents (may be null)
     * @return decision string (interpretation depends on output generator)
     * @throws Exception if decision fails
     */
    String decide(WorkItemRecord wir,
                 AgentCapability capability,
                 List<AgentInfo> peers) throws Exception;
}
```

**Built-in Implementations:**
- `DefaultDecisionReasoner`: Always returns "accept"
- `ZaiDecisionReasoner`: AI-powered decision making using Z.AI

**Return Value Interpretation:**
- The decision string is passed to `OutputGenerator`
- Common patterns: `"accept"`, `"reject"`, `"delegate:agent-id"`, `"priority:high"`
- Application-specific: Can encode any decision logic

**Custom Implementation Example:**
```java
public class PriorityDecisionReasoner implements DecisionReasoner {

    @Override
    public String decide(WorkItemRecord wir,
                        AgentCapability capability,
                        List<AgentInfo> peers) throws Exception {

        String inputData = wir.getDataString();

        // Parse priority from input
        int priority = extractPriority(inputData);

        if (priority >= 8) {
            return "accept-high-priority";
        } else if (priority >= 5) {
            return "accept-normal-priority";
        } else {
            // Delegate to peer if available
            if (peers != null && !peers.isEmpty()) {
                return "delegate:" + peers.get(0).getId();
            }
            return "accept-low-priority";
        }
    }

    private int extractPriority(String xml) {
        // Parse <priority>N</priority> from XML
    }
}
```

---

### OutputGenerator

**Package:** `org.yawlfoundation.yawl.integration.autonomous.strategies`

**Description:** Strategy interface for generating output data for work item completion.

```java
public interface OutputGenerator {

    /**
     * Generate output data for work item completion.
     *
     * @param wir the work item record
     * @param decision the decision from DecisionReasoner
     * @return XML string of output data
     * @throws Exception if generation fails
     */
    String generateOutput(WorkItemRecord wir, String decision) throws Exception;
}
```

**Built-in Implementations:**
- `ZaiOutputGenerator`: AI-powered output generation using Z.AI
- `PassthroughOutputGenerator`: Copies input to output
- `TemplateOutputGenerator`: Uses predefined XML templates

**Custom Implementation Example:**
```java
public class CalculationOutputGenerator implements OutputGenerator {

    @Override
    public String generateOutput(WorkItemRecord wir, String decision) throws Exception {

        String inputXml = wir.getDataString();

        // Parse input
        double quantity = parseDouble(inputXml, "quantity");
        double pricePerUnit = parseDouble(inputXml, "pricePerUnit");

        // Calculate total
        double total = quantity * pricePerUnit;

        // Generate output XML
        return String.format(
            "<output>\n" +
            "  <total>%.2f</total>\n" +
            "  <decision>%s</decision>\n" +
            "</output>",
            total,
            decision
        );
    }

    private double parseDouble(String xml, String fieldName) {
        // Extract value from XML
    }
}
```

---

## Data Classes

### AgentConfiguration

**Package:** `org.yawlfoundation.yawl.integration.autonomous`

**Description:** Configuration container for autonomous agents, loaded from YAML.

```java
public class AgentConfiguration {

    private String id;
    private String name;
    private int port;
    private AgentCapability capability;
    private EngineConfig engine;
    private StrategyConfig strategies;
    private ZaiConfig zai;
    private ResilienceConfig resilience;
    private DiscoveryConfig discovery;

    // Factory method
    public static AgentConfiguration fromYaml(File yamlFile) throws IOException {
        // Load and parse YAML
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public int getPort() { return port; }
    public AgentCapability getCapability() { return capability; }
    public EngineConfig getEngine() { return engine; }
    public StrategyConfig getStrategies() { return strategies; }
    public ZaiConfig getZai() { return zai; }
    public ResilienceConfig getResilience() { return resilience; }
    public DiscoveryConfig getDiscovery() { return discovery; }
}
```

**Nested Classes:**

```java
public static class EngineConfig {
    private String url;
    private String username;
    private String password;
    private long pollInterval = 5000;
    private boolean enabledOnly = true;
    // Getters...
}

public static class StrategyConfig {
    private String discovery = "none";
    private String eligibility = "default";
    private String decision = "default";
    private String output = "zai";
    // Getters...
}

public static class ZaiConfig {
    private String apiKey;
    private String model = "glm-4-flash";
    private double temperature = 0.1;
    private int maxTokens = 1000;
    // Getters...
}

public static class ResilienceConfig {
    private int retryAttempts = 3;
    private long retryBackoffMs = 1000;
    private int circuitBreakerThreshold = 5;
    private long circuitBreakerTimeoutMs = 30000;
    // Getters...
}

public static class DiscoveryConfig {
    private List<StaticAgent> staticAgents = new ArrayList<>();
    private long healthCheckInterval = 60000;
    // Getters...
}
```

---

### AgentCapability

**Package:** `org.yawlfoundation.yawl.integration.autonomous`

**Description:** Describes an agent's capabilities for eligibility matching.

```java
public class AgentCapability {

    private final String id;
    private final String domain;
    private final String[] skills;
    private final Map<String, Object> constraints;

    public AgentCapability(String id, String domain, String[] skills) {
        this(id, domain, skills, new HashMap<>());
    }

    public AgentCapability(String id, String domain, String[] skills,
                          Map<String, Object> constraints) {
        this.id = id;
        this.domain = domain;
        this.skills = skills;
        this.constraints = constraints;
    }

    public String getId() { return id; }
    public String getDomain() { return domain; }
    public String[] getSkills() { return skills; }
    public Map<String, Object> getConstraints() { return constraints; }

    public String toJson() {
        // Serialize to JSON for A2A discovery
    }
}
```

**Example:**
```java
AgentCapability capability = new AgentCapability(
    "shipper-001",
    "logistics",
    new String[]{"ground-shipping", "air-freight"},
    Map.of(
        "maxWeight", 500,
        "regions", List.of("US-WEST", "US-EAST")
    )
);
```

---

### AgentInfo

**Package:** `org.yawlfoundation.yawl.integration.autonomous.registry`

**Description:** Represents discovered agent information from A2A protocol.

```java
public class AgentInfo {

    private final String id;
    private final String name;
    private final String url;
    private final AgentCapability capability;
    private final long discoveredAt;

    public AgentInfo(String id, String name, String url,
                    AgentCapability capability) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.capability = capability;
        this.discoveredAt = System.currentTimeMillis();
    }

    public static AgentInfo fromJson(String json) {
        // Parse A2A discovery card JSON
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public AgentCapability getCapability() { return capability; }
    public long getDiscoveredAt() { return discoveredAt; }
}
```

---

## Resilience Components

### CircuitBreaker

**Package:** `org.yawlfoundation.yawl.integration.autonomous.resilience`

**Description:** Prevents cascading failures by failing fast when error threshold exceeded.

```java
public class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /**
     * Execute operation with circuit breaker protection.
     *
     * @param operation the operation to execute
     * @param <T> return type
     * @return result of operation
     * @throws Exception if operation fails or circuit is open
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        // Circuit breaker logic
    }

    public State getState() {
        // Get current state
    }

    public void reset() {
        // Manually reset circuit breaker
    }
}
```

**Example:**
```java
CircuitBreaker breaker = new CircuitBreaker(
    5,      // threshold: 5 failures
    30000   // timeout: 30 seconds
);

try {
    String result = breaker.execute(() -> {
        return callExternalService();
    });
} catch (Exception e) {
    // Circuit may be open
}
```

---

### RetryPolicy

**Package:** `org.yawlfoundation.yawl.integration.autonomous.resilience`

**Description:** Configurable retry with exponential backoff.

```java
public class RetryPolicy {

    /**
     * Execute operation with retry policy.
     *
     * @param operation the operation to execute
     * @param <T> return type
     * @return result of operation
     * @throws Exception if all retry attempts fail
     */
    public <T> T execute(Callable<T> operation) throws Exception {
        // Retry logic with exponential backoff
    }
}
```

**Example:**
```java
RetryPolicy retry = new RetryPolicy(
    3,      // attempts
    1000    // initial backoff (exponential)
);

String result = retry.execute(() -> {
    return fetchDataFromEngine();
});
```

---

## Launcher API

### GenericWorkflowLauncher

**Package:** `org.yawlfoundation.yawl.integration.autonomous.launcher`

**Description:** Launches workflow cases and waits for completion (for testing/simulation).

```java
public class GenericWorkflowLauncher {

    public GenericWorkflowLauncher(String engineUrl, String username, String password) {
        // Initialize Interface A and B clients
    }

    /**
     * Upload a workflow specification to the engine.
     *
     * @param specPath path to YAWL specification file
     * @return specification ID
     * @throws IOException if upload fails
     */
    public YSpecificationID uploadSpecification(String specPath) throws IOException {
        // Upload via Interface A
    }

    /**
     * Launch a new case of the specification.
     *
     * @param specId specification ID
     * @param caseData optional case data (XML string, may be null)
     * @return case ID
     * @throws IOException if launch fails
     */
    public String launchCase(YSpecificationID specId, String caseData) throws IOException {
        // Launch via Interface B
    }

    /**
     * Wait for case completion (polling).
     *
     * @param caseId the case ID
     * @param timeoutMs maximum wait time in milliseconds
     * @return true if completed, false if timeout
     * @throws IOException if polling fails
     */
    public boolean waitForCompletion(String caseId, long timeoutMs) throws IOException {
        // Poll case status
    }
}
```

**Example:**
```java
GenericWorkflowLauncher launcher = new GenericWorkflowLauncher(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL"
);

YSpecificationID specId = launcher.uploadSpecification(
    "exampleSpecs/myworkflow.yawl"
);

String caseId = launcher.launchCase(specId, null);

boolean completed = launcher.waitForCompletion(caseId, 300000);
if (completed) {
    System.out.println("Case completed successfully");
}
```

---

## A2A Discovery Protocol

### Endpoint: `/.well-known/agent.json`

**Method:** `GET`

**Response:**
```json
{
  "id": "shipper-001",
  "name": "West Coast Shipper",
  "capability": {
    "domain": "logistics",
    "skills": ["ground-shipping", "air-freight", "route-optimization"],
    "constraints": {
      "maxWeight": 500,
      "regions": ["CA", "OR", "WA"]
    }
  },
  "endpoints": {
    "discovery": "http://shipper-agent:8081/.well-known/agent.json",
    "health": "http://shipper-agent:8081/health"
  },
  "version": "5.2",
  "type": "yawl-autonomous-agent"
}
```

**HTTP Status Codes:**
- `200 OK`: Agent is healthy and available
- `503 Service Unavailable`: Agent is not accepting work
- `404 Not Found`: Not a YAWL agent

---

## Error Handling

All strategy methods may throw `Exception`. Agents should handle errors gracefully:

```java
try {
    boolean eligible = eligibilityReasoner.isEligible(wir, capability, peers);
} catch (Exception e) {
    logger.error("Eligibility reasoning failed for {}: {}",
                wir.getID(), e.getMessage());
    // Skip this work item, continue processing
}
```

Common exceptions:
- `IOException`: Network/file errors
- `IllegalArgumentException`: Invalid configuration
- `UnsupportedOperationException`: Feature not implemented
- `Exception`: Generic failures (Z.AI API errors, parsing failures, etc.)

---

## Thread Safety

- `GenericPartyAgent`: Thread-safe
- `CircuitBreaker`: Thread-safe
- `RetryPolicy`: Thread-safe
- Strategy implementations: **Not guaranteed thread-safe** (document per implementation)

---

## Performance Considerations

### Z.AI API Latency

Z.AI strategies add ~100-500ms per API call. For high-throughput scenarios:

- Cache eligibility decisions for identical tasks
- Use batch processing where possible
- Consider custom reasoners for time-critical paths

### Polling Interval

Default `pollInterval` is 5000ms (5 seconds). Adjust based on:

- Workflow latency requirements
- YAWL Engine load capacity
- Number of agents polling

**Recommendation:** 3000-10000ms for production

### Circuit Breaker Tuning

- `threshold`: Number of consecutive failures before opening circuit
- `timeout`: How long to wait before testing recovery

**Recommendation:**
- Development: threshold=3, timeout=10000ms
- Production: threshold=5, timeout=30000ms

---

## Logging

All components use SLF4J. Configure logging in `logback.xml`:

```xml
<logger name="org.yawlfoundation.yawl.integration.autonomous" level="INFO"/>
<logger name="org.yawlfoundation.yawl.integration.autonomous.resilience" level="DEBUG"/>
```

**Log Levels:**
- `ERROR`: Failures that prevent agent from functioning
- `WARN`: Recoverable errors (retries, circuit breaker opens)
- `INFO`: Normal operations (work item handling, agent discovery)
- `DEBUG`: Detailed traces (API calls, decision reasoning)

---

## Further Reading

- [Configuration Guide](configuration-guide.md): YAML configuration reference
- [Migration Guide](migration-guide.md): Upgrading from legacy agents
- [Architecture Overview](README.md): System design
