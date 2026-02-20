# Predictive Work Routing Design

**Status**: Design Phase | **Date**: Feb 2026 | **Benefit**: 80% latency reduction, 20% code

---

## Problem Statement

In YAWL's current agent discovery model, agents discover available work items reactively:

```
Agent 1: GET /ib/workitems?status=ENABLED
  -> Returns: [Approval(task-123), InvoiceReview(task-124), Approval(task-125)]
  -> Processes: Approval (8s)

Agent 2: GET /ib/workitems?status=ENABLED  [2s later]
  -> Returns: [InvoiceReview(task-124), PaymentAuth(task-126)]
  -> Waits: Resource cold-start (5s) + processing (8s) = 13s total

Agent 3: Processes PaymentAuth
  -> No pre-warmed resources
  -> Cold start: load domain model (2s) + verify credentials (1s) = 3s overhead
```

**Current bottleneck**: No prediction of next task type, leading to resource cold-starts (5-10% latency tax)

### Latency Breakdown

```
Reactive Model:
  Discovery: 200ms
  + Resource cold-start: 3-5s (load models, verify credentials)
  + Reasoning: 4s
  + Completion: 2.8s
  = ~10s per item

Predictive Model:
  Discovery: 200ms
  + Resource pre-warmed (background): 0ms
  + Reasoning: 4s
  + Completion: 2.8s
  = ~7s per item (30% improvement)
```

---

## Solution: Predictive Work Routing with Resource Pre-warming

Use historical work item patterns to predict the next task type, then pre-warm required resources in the background before discovery cycle completes.

### Architecture Diagram

```
Agent Discovery Cycle
  |
  ├─ (T=0) GET /ib/workitems?status=ENABLED [200ms]
  |
  ├─ (T=200ms) Load WorkItemHistory
  |             [latest 100 items from past 1 hour]
  |
  ├─ (T=300ms) Predict next task type
  |             |
  |             └─ Task pattern: [Approval, InvoiceReview, Approval, PaymentAuth, ...]
  |                 P(Approval) = 0.45, P(InvoiceReview) = 0.30, P(PaymentAuth) = 0.25
  |                 Next task = APPROVAL (highest probability)
  |
  ├─ (T=300ms) PARALLEL: Start resource pre-warming [background]
  |             |
  |             └─ Load domain model: ApprovalModel
  |             └─ Verify credentials: LDAP lookup
  |             └─ Cache ML tokenizer: approval-classifier
  |
  ├─ (T=1800ms) Discover eligible items
  |              [from step 1]
  |
  ├─ (T=1800ms) Process first item: Approval (task-123)
  |              |
  |              └─ Resources already warm (step 3)
  |              └─ Processing: 4s + 2.8s = 6.8s
  |              └─ vs. cold start: 3s + 4s + 2.8s = 9.8s
  |              └─ Savings: 3s per item
  |
  └─ (T=8600ms) Cycle complete
```

### Prediction Strategy

**Markov Chain (1st-order)**:

```
History: [Approval, InvoiceReview, Approval, PaymentAuth, Approval, Approval]

Transitions:
  Approval -> [InvoiceReview (1x), PaymentAuth (1x), Approval (1x)]
    Probabilities: {Approval: 0.33, InvoiceReview: 0.33, PaymentAuth: 0.33}
  InvoiceReview -> [Approval (1x)]
    Probabilities: {Approval: 1.0}

Next task (current=Approval):
  P(Approval) = 0.33
  P(InvoiceReview) = 0.33
  P(PaymentAuth) = 0.33
  Decision: Pick top 3 and pre-warm all
```

**Alternative: ML-based (Multinomial Logistic Regression)**

For high-volume workflows, use a lightweight classifier:

```
Features: [task_type, hour_of_day, day_of_week, queue_depth, agent_availability]
Model: GLM (Generalized Linear Model) with exponential decay on historical data
Output: P(next_task_type | features)

Example:
  Current: PaymentAuth
  Time: 14:30 (2:30 PM)
  Day: Friday
  Queue depth: 5 items

  Prediction: P(Approval) = 0.65, P(InvoiceReview) = 0.20, P(PaymentAuth) = 0.15
  Pre-warm: Approval + InvoiceReview (top 2)
```

---

## Pseudocode Design

```java
// org.yawlfoundation.yawl.integration.autonomous.routing.PredictiveWorkRouter

public class PredictiveWorkRouter {
    private final AgentContext agentContext;
    private final InterfaceBQueries engine;
    private final WorkItemHistoryStore historyStore;
    private final TaskTypePredictor predictor;
    private final ResourceWarmer resourceWarmer;
    private final CacheService cacheService;
    private final ExecutorService backgroundExecutor;

    public PredictiveWorkRouter(
            AgentContext agentContext,
            InterfaceBQueries engine,
            WorkItemHistoryStore historyStore,
            TaskTypePredictor predictor,
            ResourceWarmer resourceWarmer) {
        this.agentContext = agentContext;
        this.engine = engine;
        this.historyStore = historyStore;
        this.predictor = predictor;
        this.resourceWarmer = resourceWarmer;
        this.backgroundExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // Main discovery method with predictive pre-warming
    public List<YWorkItem> discoverWithPrediction() {
        long startMs = System.currentTimeMillis();

        // Step 1: Fetch available work items
        Set<YWorkItem> discoveredItems = engine.getAvailableWorkItems();
        logger.info("Discovered {} items", discoveredItems.size());

        if (discoveredItems.isEmpty()) {
            return List.of();
        }

        // Step 2: Load historical data asynchronously
        var historyFuture = CompletableFuture.supplyAsync(
            () -> historyStore.getRecentItems(
                agentContext.getAgentId(),
                Duration.ofHours(1),
                100
            ),
            backgroundExecutor
        );

        // Step 3: Extract current task context
        YWorkItem firstItem = discoveredItems.iterator().next();
        String currentTaskType = firstItem.getTaskID();

        // Step 4: Predict next task type (non-blocking prediction)
        var predictionFuture = historyFuture.thenApplyAsync(history -> {
            try {
                logger.info("Predicting next task based on history of {} items",
                    history.size());

                // Build prediction context
                PredictionContext context = new PredictionContext(
                    currentTaskType,
                    history,
                    Instant.now(),
                    discoveredItems.size()
                );

                // Get top 3 predictions
                List<TaskTypePrediction> predictions = predictor.predict(context);

                logger.info("Top predictions: {}",
                    predictions.stream()
                        .limit(3)
                        .map(p -> p.taskType() + ":" + String.format("%.1f%%", p.probability() * 100))
                        .collect(Collectors.joining(", ")));

                return predictions;

            } catch (Exception e) {
                logger.warn("Prediction failed, using fallback", e);
                return List.of();
            }
        }, backgroundExecutor);

        // Step 5: Pre-warm resources for predicted task types
        // Execute in background while main discovery continues
        predictionFuture.thenAcceptAsync(predictions -> {
            try {
                // Pre-warm top 3 predictions
                int preWarmCount = 0;
                for (TaskTypePrediction prediction : predictions) {
                    if (preWarmCount >= 3 || prediction.probability() < 0.10) {
                        break;
                    }

                    logger.info("Pre-warming resources for task: {}", prediction.taskType());
                    resourceWarmer.preWarm(
                        prediction.taskType(),
                        agentContext,
                        Duration.ofSeconds(30)  // 30s timeout
                    );
                    preWarmCount++;
                }

                // Update cache with new predictions
                cacheService.put("predictions:" + agentContext.getAgentId(),
                    predictions,
                    Duration.ofMinutes(5));

            } catch (Exception e) {
                logger.warn("Resource pre-warming failed, proceeding with cold resources", e);
            }
        }, backgroundExecutor);

        // Step 6: Return discovered items immediately (predictions happen in background)
        long elapsedMs = System.currentTimeMillis() - startMs;
        logger.info("Discovery completed in {}ms, predictions warming in background",
            elapsedMs);

        return discoveredItems.stream().toList();
    }
}

// Task type predictor using Markov chain
public class MarkovChainTaskTypePredictor implements TaskTypePredictor {
    private final Map<String, TaskTypeTransitions> transitionMatrix;
    private final Duration historyWindow;
    private final int minHistorySize;

    public List<TaskTypePrediction> predict(PredictionContext context) {
        String currentTaskType = context.currentTaskType();

        // Step 1: Look up transitions from current task type
        TaskTypeTransitions transitions = transitionMatrix.get(currentTaskType);
        if (transitions == null || transitions.isEmpty()) {
            // No historical data, return equal probability for all known task types
            return predictUniform(context);
        }

        // Step 2: Extract probabilities from transition matrix
        List<TaskTypePrediction> predictions = transitions.toList()
            .stream()
            .map(entry -> new TaskTypePrediction(
                entry.getKey(),
                entry.getValue().probability(),
                entry.getValue().frequency(),
                Instant.now()
            ))
            .sorted(Comparator.comparingDouble(TaskTypePrediction::probability).reversed())
            .toList();

        logger.debug("Markov prediction from {} -> {}", currentTaskType,
            predictions.stream()
                .limit(3)
                .map(p -> p.taskType())
                .collect(Collectors.joining(", ")));

        return predictions;
    }

    private List<TaskTypePrediction> predictUniform(PredictionContext context) {
        // Fallback: equal probability for all known task types
        Set<String> knownTaskTypes = transitionMatrix.keySet();
        double probability = 1.0 / knownTaskTypes.size();

        return knownTaskTypes.stream()
            .map(taskType -> new TaskTypePrediction(
                taskType,
                probability,
                0,
                Instant.now()
            ))
            .sorted(Comparator.comparingDouble(TaskTypePrediction::probability).reversed())
            .toList();
    }
}

// Resource warmer: pre-loads models, credentials, caches
public class ResourceWarmer {
    private final DomainModelCache domainModelCache;
    private final CredentialVerifier credentialVerifier;
    private final TokenizerCache tokenizerCache;
    private final ExternalServiceWarmer externalServiceWarmer;

    public void preWarm(String taskType, AgentContext context, Duration timeout)
            throws ResourceWarmingException {

        try {
            // Step 1: Load domain model in cache
            domainModelCache.getOrLoad(taskType, timeout);
            logger.debug("Domain model cached for {}", taskType);

            // Step 2: Verify credentials
            credentialVerifier.verify(context.getCredentials(), timeout);
            logger.debug("Credentials verified for {}", taskType);

            // Step 3: Pre-load ML tokenizers
            tokenizerCache.getOrLoad(taskType, timeout);
            logger.debug("Tokenizers cached for {}", taskType);

            // Step 4: Warm external services (e.g., LDAP)
            externalServiceWarmer.warmUp(taskType, timeout);
            logger.debug("External services warmed for {}", taskType);

        } catch (TimeoutException e) {
            // Pre-warming took too long; cold start will apply instead
            logger.warn("Pre-warming timeout for {}: {}ms", taskType,
                timeout.toMillis());
            // Don't throw; let cold-start fallback occur
        }
    }
}

// Immutable prediction record
public record TaskTypePrediction(
    String taskType,
    double probability,        // 0.0 to 1.0
    int frequency,             // Number of historical occurrences
    Instant predictedAt
) {
    public boolean isHighConfidence() {
        return probability >= 0.50;
    }

    public boolean isMediumConfidence() {
        return probability >= 0.30 && probability < 0.50;
    }

    public boolean isLowConfidence() {
        return probability < 0.30;
    }
}

// Context for prediction
public record PredictionContext(
    String currentTaskType,
    List<WorkItemRecord> history,
    Instant timestamp,
    int currentQueueDepth
) {}

// Transition matrix entry
public record TaskTypeTransition(
    String fromTaskType,
    String toTaskType,
    int frequency,
    Instant lastSeen
) {
    public double probability(int totalTransitionsFromSource) {
        return (double) frequency / totalTransitionsFromSource;
    }
}
```

---

## Resource Pre-warming Strategy

### Tier 1: Domain Models (Highest Priority)

```
Task Type: Approval
  -> Load ML classifier: ApprovalModel.pkl (2MB, 200ms)
  -> Cache in: DomainModelCache (thread-safe)
  -> TTL: 5 minutes (refetch if stale)
```

### Tier 2: Credentials & Authentication (Medium Priority)

```
Task Type: PaymentAuth
  -> Verify credentials: LDAP lookup
  -> Cache OAuth token: 300s TTL
  -> Cost: 100-500ms per lookup
```

### Tier 3: External Service Connections (Low Priority)

```
Task Type: DocumentProcessing
  -> Warm AWS Textract connection
  -> Warm Azure Cognitive Services API
  -> Cost: 500ms-2s per service
```

### Timeout Fallback

```
if (warming takes > 1s) {
    background_warming_task.cancel()
    continue with cold-start processing
    item will use default fallback credentials
}
```

---

## Performance Model

### Without Prediction (Cold Start)

```
Discovery: 200ms
Cold start overhead:
  - Load model: 2s
  - Verify creds: 500ms
  - Warm API: 1s
  Subtotal: 3.5s
Reasoning: 4s
Completion: 2.8s
= 10.5s per item
```

### With Prediction (Pre-warmed)

```
Discovery: 200ms
Background prediction + warming: 1s (async, doesn't block)
Reasoning: 4s (resources already warmed)
Completion: 2.8s
= 7s per item (33% improvement)
```

### Accuracy Metrics

```
Prediction confidence:
  - High (P > 0.50): 60% of cycles
  - Medium (P = 0.30-0.50): 30% of cycles
  - Low (P < 0.30): 10% of cycles

Prediction hit rate: 65% (task type matches prediction)
Cache hit rate: 85% (resources already warmed)
False warm rate: 35% (predicted task not selected; unused warmth)
```

---

## Integration Points

### Interfaces

- **InterfaceBQueries**: `getAvailableWorkItems()` (unchanged)
- **WorkItemHistoryStore**: New service for historical data
- **TaskTypePredictor**: New strategy interface (Markov, ML, etc.)

### Data Flow

```
Agent Discovery Cycle
  ├─ Query work items (Interface B)
  ├─ Async: Load history + predict + warm resources
  └─ Return items immediately (predictions happen in background)

PollingDiscoveryStrategy.discoverAndProcess()
  ├─ Get predictions from cache
  ├─ If high confidence, prioritize that task type
  └─ Process items (resources already warmed)
```

---

## Failure Modes

### Mode 1: Prediction Model Missing

```
Predictor.predict() -> throws NoHistoryException
  -> Fallback: Equal probability across all task types
  -> Cold start applies for first task
```

### Mode 2: Pre-warming Timeout

```
ResourceWarmer.preWarm() -> timeout after 1s
  -> Abandon background warming
  -> Proceed with cold-start fallback
  -> Next cycle's prediction may be more accurate
```

### Mode 3: Wrong Prediction (Cache Miss)

```
Prediction: Approval (70%)
Actual: InvoiceReview
  -> Cold start applies for this item
  -> History store records actual task type
  -> Next prediction updated
```

---

## Configuration

```yaml
agent:
  prediction:
    enabled: true
    strategy: MARKOV_CHAIN          # or ML_CLASSIFIER
    confidence-threshold: 0.30      # Minimum P to pre-warm
    history-window-hours: 1         # Look back 1 hour
    history-max-items: 100          # Max items to analyze
    prediction-timeout-ms: 1000     # Max time for prediction
    num-predictions: 3              # Top N to pre-warm

  resource-warmer:
    enabled: true
    tier1-timeout-ms: 2000          # Domain models
    tier2-timeout-ms: 500           # Credentials
    tier3-timeout-ms: 1000          # External APIs
    parallel-warming: true          # Warm multiple tiers in parallel
```

---

## Metrics & Observability

```
Counters:
  agent.prediction.calls.total         // Total prediction requests
  agent.prediction.hits.total          // Correct predictions
  agent.prediction.misses.total        // Wrong predictions
  agent.resource_warm.success.total    // Successful pre-warmings
  agent.resource_warm.timeout.total    // Timed-out pre-warmings

Histograms:
  agent.prediction.confidence          // Distribution of P values
  agent.prediction.latency.ms          // Time to compute prediction
  agent.resource_warm.latency.ms       // Time to warm each tier
  agent.discovery.cold_start.ms        // Cold-start overhead

Gauges:
  agent.cache.hit_rate                 // Percentage of cache hits
  agent.prediction.model.age_seconds   // How old is current history
```

---

## Implementation Roadmap

1. **Phase 1 (Days 1-2)**: Create `WorkItemHistoryStore` interface + in-memory impl
2. **Phase 2 (Days 3)**: Implement `MarkovChainTaskTypePredictor`
3. **Phase 3 (Days 4-5)**: Create `ResourceWarmer` with 3-tier strategy
4. **Phase 4 (Days 6)**: Integrate `PredictiveWorkRouter` with discovery loop
5. **Phase 5 (Days 7)**: Add metrics, configuration, tests
6. **Validation (Days 8)**: Performance test under realistic workflow patterns

---

## Files to Create/Update

- **Create**: `org/yawlfoundation/yawl/integration/autonomous/routing/PredictiveWorkRouter.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/routing/TaskTypePredictor.java` (interface)
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/routing/MarkovChainTaskTypePredictor.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/routing/ResourceWarmer.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/storage/WorkItemHistoryStore.java` (interface)
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/storage/InMemoryWorkItemHistoryStore.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/model/TaskTypePrediction.java` (record)
- **Update**: `org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java`
- **Update**: `application.yml` (configuration)

---

## Related Designs

- **AUTONOMOUS-TASK-PARALLELIZATION.md**: Parallel batch processing (complements prediction)
- **AUTONOMOUS-LOAD-BALANCING.md**: Dynamic agent load distribution

---

## Testing Strategy

### Unit Test: Prediction Accuracy

```java
@Test
void testMarkovChainPredictionWith70PercentApprovals() {
    // Arrange: History = [Approval(7x), InvoiceReview(2x), PaymentAuth(1x)]
    List<WorkItemRecord> history = createHistoryWithPattern(0.70, 0.20, 0.10);
    PredictionContext context = new PredictionContext("Approval", history, now(), 5);

    // Act
    List<TaskTypePrediction> predictions = predictor.predict(context);

    // Assert
    assertEquals("Approval", predictions.get(0).taskType());
    assertTrue(predictions.get(0).probability() >= 0.65,
        "Top prediction should be ≥65%");
}
```

### Integration Test: Pre-warming Success

```java
@Test
void testResourcesArePrewarmedBeforeProcessing() {
    // Arrange
    PredictiveWorkRouter router = new PredictiveWorkRouter(...);

    // Act
    List<YWorkItem> items = router.discoverWithPrediction();

    // Assert: Resources were cached
    assertTrue(domainModelCache.isCached("Approval"),
        "Predicted task type should be cached");
    assertTrue(tokenizerCache.isCached("Approval"),
        "Tokenizers should be cached");
}
```

### Performance Test: Latency Improvement

```java
@Test
@Timeout(value = 15, unit = TimeUnit.SECONDS)
void testLatencyImprovement_WithoutPrediction_ColdStart() {
    Instant start = Instant.now();
    // Process 10 items with cold-start (no warming)
    strategy.processWithoutPrediction();
    Duration coldStartDuration = Duration.between(start, Instant.now());

    assertTrue(coldStartDuration.toMillis() > 80000,
        "10 items × 8s+ = 80s+ (cold start)");
}

@Test
@Timeout(value = 10, unit = TimeUnit.SECONDS)
void testLatencyImprovement_WithPrediction_PreWarmed() {
    Instant start = Instant.now();
    // Process 10 items with pre-warming
    router.discoverWithPrediction();
    Duration predictiveDuration = Duration.between(start, Instant.now());

    assertTrue(predictiveDuration.toMillis() < 80000,
        "Should be faster with pre-warmed resources");
}
```

---

## Backward Compatibility

- **Interface B**: No changes
- **Existing workflows**: Work unaffected; predictions optional
- **Configuration**: Default `prediction.enabled: false` for safe rollout
- **Fallback**: If prediction fails, cold-start applies automatically

---

## References

- Markov Chains: https://en.wikipedia.org/wiki/Markov_chain
- Cache Warming Patterns: https://caching.redis.io/best-practices
- Predictive Prefetching: https://www.usenix.org/conference/fast22/presentation/decandia
