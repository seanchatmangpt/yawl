# Autonomous Agent Migration Guide: PartyAgent → GenericPartyAgent

**Status**: READY FOR IMPLEMENTATION
**Scope**: Complete migration from deprecated PartyAgent to GenericPartyAgent

---

## 1. Why Migrate from PartyAgent to GenericPartyAgent

### Problems with PartyAgent (Legacy)

```java
// PartyAgent.java - Legacy implementation
@Deprecated
public final class PartyAgent {
    private final EligibilityWorkflow eligibilityWorkflow;  // Hardcoded dependency
    private final DecisionWorkflow decisionWorkflow;        // Hardcoded dependency
    private final ZaiService zaiService;                   // Static ZAI-only
}
```

**Limitations**:
- **Tight Coupling**: Hardcoded dependencies on specific workflow implementations
- **Domain-Specific**: Limited to order fulfillment use cases
- **Limited Extensibility**: Cannot easily add new discovery, eligibility, or decision strategies
- **No Handoff Support**: Cannot coordinate with other agents
- **Inflexible Configuration**: Requires code changes for new capabilities

### Benefits of GenericPartyAgent

```java
// GenericPartyAgent.java - Modern implementation
public class GenericPartyAgent {
    private final AgentConfiguration config;  // Config-driven, dependency injection
    // Pluggable strategies via configuration:
    private final DiscoveryStrategy discoveryStrategy;
    private final EligibilityReasoner eligibilityReasoner;
    private final DecisionReasoner decisionReasoner;
}
```

**Advantages**:
- **Strategy Pattern**: Pluggable discovery, eligibility, and decision strategies
- **Configuration-Driven**: YAML-based configuration, no code changes needed
- **Agent Coordination**: Built-in handoff support via ADR-025 protocol
- **Virtual Threads**: Modern Java 25 concurrency with virtual threads
- **Extensibility**: Easy to add new strategies and capabilities
- **Testability**: Dependency injection enables easy unit testing

---

## 2. Architecture Overview

### Core Design Patterns

#### 1. Strategy Pattern (3 Pluggable Components)

```java
// Functional interfaces for strategy injection
@FunctionalInterface
public interface DiscoveryStrategy {
    List<WorkItemRecord> discoverWorkItems(
        InterfaceB_EnvironmentBasedClient client,
        String sessionHandle
    ) throws IOException;
}

@FunctionalInterface
public interface EligibilityReasoner {
    boolean isEligible(WorkItemRecord workItem);
}

@FunctionalInterface
public interface DecisionReasoner {
    String produceOutput(WorkItemRecord workItem);
}
```

#### 2. Agent Configuration (Java 25 Record)

```java
public record AgentConfiguration(
    String id,
    String engineUrl,
    String username,
    String password,
    AgentCapability capability,
    DiscoveryStrategy discoveryStrategy,
    EligibilityReasoner eligibilityReasoner,
    DecisionReasoner decisionReasoner,
    AgentRegistryClient registryClient,
    HandoffProtocol handoffProtocol,
    HandoffRequestService handoffService,
    ConflictResolver conflictResolver,
    YawlA2AClient a2aClient,
    PartitionConfig partitionConfig,
    int port,
    String version,
    long pollIntervalMs) {

    // Immutable with canonical constructor validation
}
```

#### 3. Virtual Thread Architecture

```java
// Discovery loop runs on virtual thread
private void startDiscoveryLoop() {
    discoveryThread = Thread.ofVirtual()
        .name("discovery-" + config.getAgentName())
        .start(() -> {
            while (running.get()) {
                // Work item discovery and processing
                runDiscoveryCycle();
                // Sleep with backoff
                TimeUnit.MILLISECONDS.sleep(sleepMs);
            }
        });
}

// HTTP server uses virtual thread per task executor
httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
```

### Component Relationships

```
┌─────────────────────────────────────────────────────────────┐
│                    GenericPartyAgent                         │
│                   (Main Agent Class)                        │
└─────────────────────┬─────────────────────┬───────────────────┘
                      │                     │
┌─────────────────────▼─────────────────────▼─────────────────┐
│                AgentConfiguration                            │
│           (Immutable Configuration Record)                  │
└─────────────────────┬─────────────────────┬───────────────────┘
                      │                     │
    ┌─────────────────┴─────────────────────┴─────────────────┐
    │                     │                     │           │
┌───▼────┐          ┌───▼────┐          ┌───▼────┐      ┌───▼────┐
│Polling │          │Static  │          │Template│      │ZAI     │
│Discovery│        │Mapping │          │Decision │      │Decision │
│Strategy│         │Eligibility│        │Reasoner │      │Reasoner │
└────────┘         │Reasoner │         └────────┘       └────────┘
                   └─────────┘
```

---

## 3. Step-by-Step Migration

### Phase 1: Implement DiscoveryStrategy

#### Options for Discovery Strategy

```java
// 1. Simple Polling Strategy (Default)
DiscoveryStrategy pollingStrategy = new PollingDiscoveryStrategy();

// 2. Custom Strategy
DiscoveryStrategy customStrategy = (client, session) -> {
    // Query engine with custom filters
    List<WorkItemRecord> allItems = client.getWorkItems(session);
    // Filter by agent capabilities
    return allItems.stream()
        .filter(item -> item.getTaskName().startsWith("MY_DOMAIN_"))
        .collect(Collectors.toList());
};

// 3. SPARQL-Enhanced Strategy
DiscoveryStrategy sparqlStrategy = (client, session) -> {
    QueryEngine queryEngine = config.getQueryEngine();
    List<WorkItemRecord> items = queryEngine.queryWorkItems(session);
    return items;
};
```

#### Implementation Example

```java
public class SmartDiscoveryStrategy implements DiscoveryStrategy {
    private final AgentCapability capability;
    private final long maxAgeMs = TimeUnit.MINUTES.toMillis(5);

    @Override
    public List<WorkItemRecord> discoverWorkItems(
        InterfaceB_EnvironmentBasedClient client,
        String sessionHandle) throws IOException {

        // Get all enabled work items
        List<WorkItemRecord> allItems = client.getWorkItems(sessionHandle);

        // Filter by:
        // 1. Agent capability domain
        // 2. Item age (not too old)
        // 3. Current agent load
        return allItems.stream()
            .filter(this::isDomainMatch)
            .filter(this::isRecent)
            .filter(this::supportsLoad)
            .collect(Collectors.toList());
    }

    private boolean isDomainMatch(WorkItemRecord item) {
        return item.getTaskName().startsWith(capability.domainName());
    }

    private boolean isRecent(WorkItemRecord item) {
        long age = System.currentTimeMillis() - item.getTimestamp();
        return age < maxAgeMs;
    }
}
```

### Phase 2: Implement EligibilityReasoner

#### Static Mapping Strategy

```java
public class StaticMappingEligibilityReasoner implements EligibilityReasoner {
    private final Map<String, Pattern> taskMappings;

    public StaticMappingEligibilityReasoner(String mappingFile) throws IOException {
        this.taskMappings = loadMappings(mappingFile);
    }

    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        String taskName = workItem.getTaskName();
        // Check if task matches any configured pattern
        return taskMappings.entrySet().stream()
            .anyMatch(entry -> entry.getValue().matcher(taskName).matches());
    }

    private Map<String, Pattern> loadMappings(String filePath) throws IOException {
        // Load JSON mapping file with task name patterns
        String json = Files.readString(Paths.get(filePath));
        // Parse and compile regex patterns
        return parseJsonMappings(json);
    }
}
```

#### ZAI-Enhanced Strategy

```java
public class ZaiEligibilityReasonerStrategy implements EligibilityReasoner {
    private final ZaiService zaiService;
    private final String eligibilityPrompt;

    @Override
    public boolean isEligible(WorkItemRecord workItem) {
        // Prepare work item data for ZAI
        String workItemJson = serializeWorkItem(workItem);

        // Query ZAI for eligibility assessment
        String response = zaiService.query(
            eligibilityPrompt + "\n\n" + workItemJson
        );

        // Parse boolean response
        return parseBooleanResponse(response);
    }
}
```

### Phase 3: Implement DecisionReasoner

#### Template-Based Decision Reasoner

```java
public class TemplateDecisionReasoner implements DecisionReasoner {
    private final Path templateFile;
    private final TemplateEngine templateEngine;

    @Override
    public String produceOutput(WorkItemRecord workItem) {
        // Load XML template
        String template = Files.readString(templateFile);

        // Extract work item data
        Map<String, Object> data = extractWorkItemData(workItem);

        // Apply template with data
        return templateEngine.render(template, data);
    }

    private Map<String, Object> extractWorkItemData(WorkItemRecord workItem) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", workItem.getID());
        data.put("taskName", workItem.getTaskName());
        data.put("data", workItem.getData());
        return data;
    }
}
```

#### SPARQL-Enhanced Decision Reasoner

```java
public class SparqlDecisionReasoner implements DecisionReasoner {
    private final QueryEngine queryEngine;
    private final String decisionQuery;

    @Override
    public String produceOutput(WorkItemRecord workItem) {
        // Query knowledge graph for decision logic
        QueryResult result = queryEngine.executeSparql(
            decisionQuery, workItem.getID()
        );

        // Apply decision rules
        if (result.getBooleanValue("is_approved")) {
            return generateApprovedResponse(result);
        } else {
            return generateRejectedResponse(result);
        }
    }
}
```

### Phase 4: Configure Handoff Protocol

```java
// In agent configuration
HandoffProtocol protocol = new StandardHandoffProtocol(
    registryClient,
    timeoutService,
    handoffMetrics
);

HandoffRequestService handoffService = new HandoffRequestService(
    protocol,
    jwtAuthProvider,
    notificationService
);
```

---

## 4. YAML Configuration Format

### Basic Configuration

```yaml
# agent-config.yaml
agent:
  id: "loan-review-agent"
  name: "Loan Review Agent"
  capability:
    domain: "loan_review"
    description: "Reviews and approves loan applications"
  server:
    port: 8091
    base_url: "http://localhost:${PORT:-8091}"
  poll_interval_ms: 5000

yawl:
  engine_url: "${YAWL_ENGINE_URL:-http://localhost:8080/yawl}"
  username: "${YAWL_USERNAME:-admin}"
  password: "${YAWL_PASSWORD}"

discovery:
  strategy: "polling"
  interval_ms: 3000
  max_batch_size: 10
  retry_attempts: 3
  retry_delay_ms: 1000

reasoning:
  eligibility:
    engine: "static"  # or "zai"
    mapping_file: "configs/eligibility-mappings.json"
    # For ZAI:
    # engine: "zai"
    # eligibility_prompt: "Given the work item data, determine if this agent can process it. Return true/false."

  decision:
    engine: "template"  # or "zai" or "sparql"
    template_file: "templates/loan-approval-template.xml"
    # For ZAI:
    # engine: "zai"
    # decision_prompt: "Based on the loan application data, make a decision to approve or reject. Provide detailed reasoning."
    # For SPARQL:
    # engine: "sparql"
    # query_file: "queries/loan-decision.sparql"

  output:
    format: "xml"
    pretty_print: true
    encoding: "UTF-8"

registry:
  enabled: true
  url: "${REGISTRY_URL:-http://localhost:8090}"
  refresh_interval_ms: 30000
  health_check_interval_ms: 15000

handoff:
  enabled: true
  timeout_ms: 30000
  retry_attempts: 2
  fallback_strategy: "error_log"

zai:
  api_key: "${ZAI_API_KEY}"
  model: "${ZAI_MODEL:-GLM-4.7-Flash}"
  temperature: 0.1
  max_tokens: 2000

monitoring:
  metrics_enabled: true
  log_level: "INFO"
  health_endpoint: "/health"
  capacity_endpoint: "/capacity"
```

### Advanced Configuration with SPARQL Integration

```yaml
# advanced-config.yaml
agent:
  id: "analytics-agent"
  capability:
    domain: "analytics"
    description: "Advanced analytics and insights"
  partitioning:
    strategy: "workitem_hash"
    partitions: 4

discovery:
  strategy: "sparql"
  endpoint: "${SPARQL_ENDPOINT:-http://localhost:9090}"
  query_file: "queries/analytics-workitems.rq"
  query_timeout_ms: 10000

reasoning:
  eligibility:
    engine: "sparql"
    query_file: "queries/eligibility-check.rq"
    cache_enabled: true
    cache_ttl_ms: 300000

  decision:
    engine: "sparql"
    query_file: "queries/decision-making.rq"
    output_format: "json"
    reasoning_enabled: true

sparql:
  endpoint: "${SPARQL_ENDPOINT}"
  auth:
    type: "bearer"
    token: "${SPARQL_TOKEN}"

  query_timeout: 10
  connection_pool_size: 10
  retry_attempts: 3

a2a:
  enabled: true
  protocol: "http"
  port: 8092
  auth:
    type: "jwt"
    key_file: "keys/jwt.key"

  discovery:
    auto_discovery: true
    registry_url: "${REGISTRY_URL}"
    heartbeat_interval_ms: 10000
```

---

## 5. SPARQL Engine Integration

### SPARQL Query Examples

#### Work Item Discovery Query

```sparql
# queries/analytics-workitems.rq
PREFIX yawl: <http://yawl.org/ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX schema: <http://schema.org/>

SELECT ?workItem ?taskName ?created ?data
WHERE {
  ?workItem a yawl:WorkItem ;
            yawl:taskName ?taskName ;
            yawl:created ?created ;
            yawl:data ?data .

  # Filter for analytics domain
  ?workItem yawl:domain "analytics" .

  # Filter for ready status
  ?workItem yawl:status "ready" .

  # Date filter - created in last 24 hours
  FILTER(?created >= NOW() - "24 hours"^^xsd:duration)
}
ORDER BY DESC(?created)
LIMIT 100
```

#### Eligibility Check Query

```sparql
# queries/eligibility-check.rq
PREFIX yawl: <http://yawl.org/ns#>
PREFIX analytics: <http://analytics.org/ns#>

ASK {
  ?workItem a yawl:WorkItem ;
            yawl:taskName ?taskName ;
            yawl:data ?data .

  # Check task is in analytics domain
  ?workItem yawl:domain "analytics" .

  # Check agent has required skills
  ?workItem yawl:requiresSkill ?skill .
  ?agent analytics:hasSkill ?skill .
  ?agent analytics:domain "analytics" .
}
```

#### Decision Making Query

```sparql
# queries/decision-making.rq
PREFIX yawl: <http://yawl.org/ns#>
PREFIX analytics: <http://analytics.org/ns#>
PREFIX schema: <http://schema.org/>

CONSTRUCT {
  ?decision a yawl:Decision ;
            yawl:workItemId ?workItemId ;
            yawl:decisionType ?decisionType ;
            yawl:confidence ?confidence ;
            yawl:reasoning ?reasoning ;
            yawl:output ?output .
}
WHERE {
  ?workItem a yawl:WorkItem ;
            yawl:id ?workItemId ;
            yawl:data ?data .

  # Get analytics insights from data
  ?data analytics:hasInsight ?insight .
  ?insight analytics:prediction ?prediction ;
           analytics:confidence ?confidence .

  # Generate decision based on prediction
  BIND(CONCAT("decision-", ?prediction) AS ?decisionType)

  # Generate reasoning
  ?insight analytics:reasoning ?reasoning .

  # Generate XML output
  BIND(generateOutput(?workItemId, ?decisionType, ?confidence, ?reasoning) AS ?output)
}

# Function to generate XML output
FUNCTION generateOutput(?workItemId, ?decisionType, ?confidence, ?reasoning) {
  XML {
    <decision>
      <workItemId>{?workItemId}</workItemId>
      <type>{?decisionType}</type>
      <confidence>{?confidence}</confidence>
      <reasoning>{?reasoning}</reasoning>
      <timestamp>NOW()</timestamp>
    </decision>
  }
}
```

### SPARQL Integration Implementation

```java
public class SparqlDiscoveryStrategy implements DiscoveryStrategy {
    private final SparqlClient sparqlClient;
    private final Path queryFile;
    private final WorkItemConverter converter;

    @Override
    public List<WorkItemRecord> discoverWorkItems(
        InterfaceB_EnvironmentBasedClient client,
        String sessionHandle) throws IOException {

        // Load and execute SPARQL query
        String query = Files.readString(queryFile);
        QueryResult result = sparqlClient.execute(query, sessionHandle);

        // Convert SPARQL results to WorkItemRecord
        return result.getBindings().stream()
            .map(converter::convertToWorkItem)
            .collect(Collectors.toList());
    }
}

public class SparqlDecisionReasoner implements DecisionReasoner {
    private final SparqlClient sparqlClient;
    private final Path queryFile;
    private final DecisionOutputGenerator generator;

    @Override
    public String produceOutput(WorkItemRecord workItem) {
        // Execute decision SPARQL query
        QueryResult result = sparqlClient.execute(
            queryFile,
            Map.of("workItemId", workItem.getID())
        );

        // Generate output from query results
        return generator.generate(result);
    }
}
```

---

## 6. Virtual Thread Usage Patterns

### Virtual Thread Discovery Loop

```java
private void startDiscoveryLoop() {
    discoveryThread = Thread.ofVirtual()
        .name("discovery-" + config.getAgentName())
        .start(() -> {
            while (running.get()) {
                try {
                    runDiscoveryCycle();

                    // Exponential backoff with jitter
                    if (hasItems) {
                        backoffMs = baseIntervalMs;
                        emptyResultsCount = 0;
                    } else {
                        emptyResultsCount++;
                        backoffMs = Math.min(
                            baseIntervalMs * (1L << Math.min(emptyResultsCount - 1, 6)),
                            maxBackoffMs
                        );
                    }

                    // Add jitter to prevent thundering herd
                    long jitter = ThreadLocalRandom.current().nextLong(
                        -backoffMs / 10,
                        backoffMs / 10
                    );
                    long sleepTime = Math.max(0, backoffMs + jitter);

                    // Virtual thread sleep doesn't block OS thread
                    TimeUnit.MILLISECONDS.sleep(sleepTime);

                } catch (Exception e) {
                    logger.error("Discovery cycle failed: {}", e.getMessage(), e);
                    // Reset backoff on error
                    backoffMs = baseIntervalMs;
                }
            }
        });
}
```

### Virtual Thread HTTP Server

```java
private void startHttpServer() throws IOException {
    httpServer = HttpServer.create(
        new InetSocketAddress(config.port()), 0
    );

    // Virtual thread per task executor for HTTP
    httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

    // Endpoints
    httpServer.createContext("/.well-known/agent.json", exchange -> {
        String response = buildAgentCardJson();
        sendJsonResponse(exchange, response);
    });

    httpServer.createContext("/health", exchange -> {
        String response = """
            {"status":"ok","agent":"%s","lifecycle":"%s"}"""
            .formatted(config.getAgentName(), lifecycle.get());
        sendJsonResponse(exchange, response);
    });
}

private void sendJsonResponse(HttpExchange exchange, String response)
    throws IOException {
    byte[] body = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
        os.write(body);
    }
}
```

### Virtual Thread Work Item Processing

```java
private void processWorkItem(WorkItemRecord workItem) {
    String workItemId = workItem.getID();

    try {
        // Check eligibility on virtual thread
        boolean eligible = config.eligibilityReasoner().isEligible(workItem);

        if (!eligible) {
            logger.debug("[{}] Work item {} not eligible",
                config.getAgentName(), workItemId);
            return;
        }

        // Checkout on same virtual thread (non-blocking)
        String checkoutResult = ibClient.checkOutWorkItem(
            workItemId, sessionHandle
        );

        if (checkoutResult == null || checkoutResult.contains("failure")) {
            logger.warn("[{}] Checkout failed for work item {}: {}",
                config.getAgentName(), workItemId, checkoutResult);
            return;
        }

        // Produce output on same virtual thread
        String output = config.decisionReasoner().produceOutput(workItem);

        // Check in result
        String checkinResult = ibClient.checkInWorkItem(
            workItemId, output, null, sessionHandle
        );

        if (checkinResult != null && checkinResult.contains("success")) {
            logger.info("[{}] Completed work item {}",
                config.getAgentName(), workItemId);
        }

    } catch (Exception e) {
        logger.error("[{}] Processing failed for work item {}: {}",
            config.getAgentName(), workItemId, e.getMessage(), e);

        // Handoff to another agent (blocking operation)
        try {
            classifyHandoffIfNeeded(workItemId, sessionHandle);
        } catch (HandoffException he) {
            logger.error("[{}] Handoff failed: {}",
                config.getAgentName(), he.getMessage(), he);
        }
    }
}
```

### Virtual Thread Best Practices

1. **Don't Use `synchronized`**: Use `ReentrantLock` instead
   ```java
   private final ReentrantLock processingLock = new ReentrantLock();

   if (processingLock.tryLock()) {
       try {
           // Critical section
       } finally {
           processingLock.unlock();
       }
   }
   ```

2. **Use Virtual-Friendly Libraries**: Ensure libraries don't block OS threads

3. **Structured Concurrency**: For parallel processing
   ```java
   try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
       StructuredTaskScope.Subtask<String> result1 = scope.fork(() -> task1());
       StructuredTaskScope.Subtask<String> result2 = scope.fork(() -> task2());

       scope.join(); // Throws exception if any task fails

       String r1 = result1.get();
       String r2 = result2.get();
   }
   ```

4. **Virtual Thread Pool Usage**:
   ```java
   // For CPU-intensive work, use platform threads
   ExecutorService cpuExecutor = Executors.newFixedThreadPool(
       Runtime.getRuntime().availableProcessors()
   );

   // For I/O work, use virtual threads
   ExecutorService ioExecutor = Executors.newVirtualThreadPerTaskExecutor();
   ```

---

## 7. Testing Strategies

### Unit Testing with Mocks

```java
public class GenericPartyAgentTest {
    private GenericPartyAgent agent;
    private MockInterfaceBClient mockClient;
    private MockDiscoveryStrategy mockDiscovery;
    private MockEligibilityReasoner mockEligibility;
    private MockDecisionReasoner mockDecision;

    @BeforeEach
    void setUp() {
        mockClient = new MockInterfaceBClient();
        mockDiscovery = new MockDiscoveryStrategy();
        mockEligibility = new MockEligibilityReasoner();
        mockDecision = new MockDecisionReasoner();

        AgentCapability capability = new AgentCapability(
            "test_domain", "Test Domain"
        );

        AgentConfiguration config = AgentConfiguration.builder(
            "test-agent",
            "http://localhost:8080/yawl",
            "admin",
            "password"
        )
        .capability(capability)
        .discoveryStrategy(mockDiscovery)
        .eligibilityReasoner(mockEligibility)
        .decisionReasoner(mockDecision)
        .port(8091)
        .build();

        agent = new GenericPartyAgent(config);
    }

    @Test
    void testWorkItemProcessing() throws IOException {
        // Arrange
        WorkItemRecord workItem = createTestWorkItem();
        mockDiscovery.setWorkItems(List.of(workItem));
        mockEligibility.setEligible(true);
        mockDecision.setOutput("test-output");
        mockClient.setSession("test-session");

        // Act
        agent.start();

        // Assert
        assertTrue(mockDiscovery.wasCalled());
        assertTrue(mockEligibility.wasCalled());
        assertTrue(mockDecision.wasCalled());
    }
}
```

### Integration Testing with Real Engine

```java
@ExtendWith(YawEngineExtension.class)
public class GenericPartyAgentIntegrationTest {
    @Test
    void testEndToEndWorkflow(YawEngine engine) throws Exception {
        // Setup YAWL engine with test workflow
        engine.deployWorkflow("loan-review.yawl");

        // Configure agent with real strategies
        AgentConfiguration config = createTestConfiguration(engine);
        GenericPartyAgent agent = new GenericPartyAgent(config);

        // Start agent and submit work item
        agent.start();

        // Submit test work item
        WorkItemRecord submittedItem = engine.submitWorkItem(
            "loan-review", "submit-application"
        );

        // Wait for processing
        await().atMost(30, SECONDS)
            .untilAsserted(() -> {
                WorkItemRecord completedItem = engine.getWorkItem(
                    submittedItem.getID()
                );
                assertEquals("completed", completedItem.getStatus());
            });

        agent.stop();
    }
}
```

### Performance Testing

```java
public class GenericPartyAgentPerformanceTest {
    @Test
    void testConcurrentProcessing() throws InterruptedException {
        // Setup multiple agents
        List<GenericPartyAgent> agents = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            AgentConfiguration config = createConfig("agent-" + i);
            agents.add(new GenericPartyAgent(config));
        }

        // Start all agents
        for (GenericPartyAgent agent : agents) {
            agent.start();
        }

        // Submit load
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 1000; i++) {
            int taskId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                submitTestWorkItem("test-" + taskId);
            }, executor);
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .join(30, TimeUnit.SECONDS);

        // Stop agents and verify
        for (GenericPartyAgent agent : agents) {
            agent.stop();
        }

        // Verify no work items stuck
        assertEquals(0, getStuckWorkItemsCount());
    }
}
```

### SPARQL Integration Testing

```java
public class SparqlStrategyTest {
    private SparqlClient sparqlClient;
    private SparqlDiscoveryStrategy strategy;
    private InMemorySparqlStore testStore;

    @BeforeEach
    void setUp() {
        testStore = new InMemorySparqlStore();
        loadTestData();

        sparqlClient = new SparqlClient(testStore);
        strategy = new SparqlDiscoveryStrategy(sparqlClient, "queries/test.rq");
    }

    @Test
    void testDiscoveryQuery() throws IOException {
        InterfaceB_EnvironmentBasedClient mockClient = createMockClient();
        String session = "test-session";

        List<WorkItemRecord> items = strategy.discoverWorkItems(
            mockClient, session
        );

        assertEquals(3, items.size());
        assertTrue(items.stream().allMatch(
            item -> item.getTaskName().startsWith("test-")
        ));
    }
}
```

### Error Scenarios Testing

```java
public class GenericPartyAgentErrorHandlingTest {
    @Test
    void testEngineConnectionFailure() {
        // Setup configuration with invalid engine URL
        AgentConfiguration config = AgentConfiguration.builder(
            "test-agent",
            "http://localhost:9999/yawl",
            "admin",
            "password"
        )
        .capability(new AgentCapability("test", "Test"))
        .discoveryStrategy(new SafeDiscoveryStrategy())
        .eligibilityReasoner(item -> true)
        .decisionReasoner(item -> "output")
        .build();

        // Verify exception is thrown
        assertThrows(IOException.class, () -> {
            GenericPartyAgent agent = new GenericPartyAgent(config);
            agent.start();
        });
    }

    @Test
    void testHandoffFailure() throws Exception {
        // Setup scenario where handoff should trigger but fails
        WorkItemRecord workItem = createProblematicWorkItem();
        mockDiscovery.setWorkItems(List.of(workItem));
        mockEligibility.setEligible(true);
        mockDecision.setOutput("error");
        mockClient.simulateCheckoutSuccess();
        mockClient.simulateCheckinFailure();
        mockHandoff.simulateFailure();

        // Execute and verify error handling
        assertDoesNotThrow(() -> {
            agent.start();
            Thread.sleep(1000); // Wait for processing
        });

        // Verify error was logged but didn't crash agent
        assertTrue(mockErrorLogger.hasErrors());
    }
}
```

---

## Migration Checklist

### Pre-Migration

- [ ] Audit existing PartyAgent usage in codebase
- [ ] Identify custom strategies in existing PartyAgent implementations
- [ ] Map existing workflow logic to new strategy interfaces
- [ ] Set up YAWL test environment with test workflows
- [ ] Install required dependencies (SPARQL engine, etc.)

### Migration Steps

- [ ] Create GenericPartyAgent configuration YAML
- [ ] Implement DiscoveryStrategy (polling or custom)
- [ ] Implement EligibilityReasoner (static or ZAI)
- [ ] Implement DecisionReasoner (template or SPARQL)
- [ ] Configure handoff protocol and registry
- [ ] Set up monitoring and logging
- [ ] Update deployment scripts and Docker configs

### Testing

- [ ] Unit test all strategies with mocks
- [ ] Integration test with YAWL engine
- [ ] Performance test with concurrent load
- [ ] Error scenario testing (engine down, handoff failures)
- [ ] Load test with virtual thread concurrency

### Deployment

- [ ] Deploy to staging environment
- [ ] Monitor metrics and performance
- [ ] Validate handoff coordination with other agents
- [ ] Document configuration and monitoring setup
- [ ] Create migration guide for other teams

---

## References

- [GenericPartyAgent JavaDoc](../src/org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java)
- [AgentConfiguration JavaDoc](../src/org/yawlfoundation/yawl/integration/autonomous/AgentConfiguration.java)
- [Strategy Interfaces](../src/org/yawlfoundation/yawl/integration/autonomous/strategies/)
- [YAML Configuration Loader](../src/org/yawlfoundation/yawl/integration/autonomous/YamlAgentConfigLoader.java)
- [SPARQL Engine Integration](../src/org/yawlfoundation/yawl/integration/autonomous/sparql/)
- [Java 25 Virtual Threads Guide](../docs/how-to/deployment/virtual-threads.md)
- [ADR-025 Agent Coordination Protocol](../docs/explanation/decisions/ADR-025-agent-coordination-protocol.md)

**Status**: READY FOR IMPLEMENTATION
**Last Updated**: 2026-02-21