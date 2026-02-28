# Reference: Process Intelligence API

Complete API reference for the YAWL Process Intelligence module.

---

## ProcessIntelligenceFacade

Main entry point for all PI operations.

```java
public class ProcessIntelligenceFacade {
    public ProcessIntelligenceFacade(
        CaseOutcomePredictor predictor,
        PrescriptiveEngine prescriptive,
        ResourceOptimizer optimizer,
        NaturalLanguageQueryEngine nlEngine);

    // Predictive
    public CaseOutcomePrediction predictCaseOutcome(String caseId, Map<String, Object> data)
        throws PIException;

    // Prescriptive
    public PrescriptiveEngine getPrescriptiveEngine();

    // Optimization
    public ResourceOptimizer getResourceOptimizer();

    // RAG
    public NaturalLanguageQueryEngine getNaturalLanguageQueryEngine();
}
```

---

## Predictive API

### CaseOutcomePredictor

```java
public interface CaseOutcomePredictor {
    CaseOutcomePrediction predictOutcome(String caseId, Map<String, Object> features)
        throws PIException;

    List<CaseOutcomePrediction> predictOutcomeBatch(List<CaseWithFeatures> cases)
        throws PIException;

    void registerModel(String modelName, Path modelPath) throws PIException;

    void unregisterModel(String modelName);

    List<String> listAvailableModels();
}
```

### CaseOutcomePrediction

```java
public record CaseOutcomePrediction(
    String caseId,
    String predictedOutcome,
    double confidence,
    Map<String, Double> outcomeScores,
    List<String> featureImportance,
    Instant predictionTime
) {
    public double probability(String outcome);
    public List<String> topK(int k);
}
```

**Example:**

```java
CaseOutcomePrediction pred = pi.predictCaseOutcome("case_123", Map.of(
    "amount", 5000.0,
    "priority", "high"
));

System.out.println(pred.probability("delayed"));  // 0.75
System.out.println(pred.confidence());  // 0.88
System.out.println(pred.featureImportance());  // ["amount", "priority"]
```

---

## Prescriptive API

### PrescriptiveEngine

```java
public interface PrescriptiveEngine {
    List<ProcessAction> recommendActions(RecommendationContext context)
        throws PIException;

    ProcessActionResult executeAction(ProcessAction action)
        throws PIException;

    void registerRule(ProcessRule rule);

    void unregisterRule(String ruleName);
}
```

### RecommendationContext

```java
public class RecommendationContext {
    public static Builder builder();

    public String caseId();
    public String currentTask();
    public String predictedOutcome();
    public double riskScore();
    public int availableResources();
    public String escalationPath();
    public Map<String, Object> customAttributes();
}
```

### ProcessAction

```java
public record ProcessAction(
    String actionId,
    String actionType,  // ESCALATE, REROUTE, REALLOCATE, etc.
    String targetResource,
    double confidence,
    String explanation,
    Map<String, Object> parameters
) {}
```

**Action Types:**

| Type | Description | Parameters |
|------|-------------|-----------|
| ESCALATE | Route to manager | `escalation_level`, `manager_id` |
| REROUTE | Change workflow path | `new_task`, `reason` |
| REALLOCATE | Assign to different resource | `new_resource_id` |
| EXTEND_DEADLINE | Add time buffer | `extension_minutes` |
| PAUSE | Suspend case temporarily | `pause_reason` |

---

## Optimization API

### ResourceOptimizer

```java
public interface ResourceOptimizer {
    AssignmentSolution solve(AssignmentProblem problem)
        throws PIException;

    ValidationResult validate(AssignmentProblem problem);
}
```

### AssignmentProblem

```java
public class AssignmentProblem {
    public static Builder builder();

    public List<Task> tasks();
    public List<Resource> resources();
    public double[][] costMatrix();
    public List<Constraint> constraints();
    public int maxIterations();
    public Duration timeout();
}
```

### AssignmentSolution

```java
public record AssignmentSolution(
    List<TaskAssignment> assignments,
    double totalCost,
    double utilization,
    List<String> violations
) {}

public record TaskAssignment(
    String taskId,
    String resourceId,
    double cost,
    double fitness
) {}
```

**Example:**

```java
AssignmentProblem problem = AssignmentProblem.builder()
    .tasks(tasks)
    .resources(resources)
    .costMatrix(matrix)
    .constraint(new CapacityConstraint())
    .constraint(new SkillConstraint())
    .maxIterations(1000)
    .timeout(Duration.ofSeconds(30))
    .build();

AssignmentSolution solution = optimizer.solve(problem);

for (TaskAssignment assign : solution.assignments()) {
    System.out.println(assign.taskId() + " → " + assign.resourceId());
}
```

---

## RAG API

### NaturalLanguageQueryEngine

```java
public interface NaturalLanguageQueryEngine {
    NlQueryResponse query(NlQueryRequest request)
        throws PIException;

    void indexEventLog(String caseId, List<WorkflowEvent> events)
        throws PIException;

    void reindexAll() throws PIException;

    EmbeddingStats getEmbeddingStats();
}
```

### NlQueryRequest

```java
public class NlQueryRequest {
    public static Builder builder();

    public String question();
    public String context();  // workflow name/spec URI
    public int maxResults();
    public String retrievalStrategy();  // VECTOR, BM25, HYBRID
    public String rerankingModel();  // CROSS_ENCODER, etc.
}
```

### NlQueryResponse

```java
public record NlQueryResponse(
    String answer,
    double confidence,
    List<Citation> citations,
    List<String> rawDocuments,
    QueryMetrics metrics
) {}

public record Citation(
    String source,  // case ID
    String content,
    double relevance,
    String eventType
) {}
```

**Example:**

```java
NlQueryResponse resp = nlEngine.query(NlQueryRequest.builder()
    .question("Which orders were delayed in January?")
    .context("order_workflow")
    .maxResults(10)
    .build());

System.out.println(resp.answer());

for (Citation cite : resp.citations()) {
    System.out.println("  " + cite.source() + ": " + cite.relevance());
}
```

---

## Data Preparation API

### OcedBridge

```java
public interface OcedBridge {
    ConversionReport convertFromCsv(Path inputCsv, Path outputOcel2)
        throws PIException;

    ConversionReport convertFromJson(Path inputJson, Path outputOcel2)
        throws PIException;

    ConversionReport convertFromXml(Path inputXml, Path outputOcel2)
        throws PIException;

    ValidationReport validate(Path ocel2File)
        throws PIException;
}
```

### OcedSchema

```java
public class OcedSchema {
    public static Builder builder();

    public String eventIdColumn();
    public String timestampColumn();
    public String activityColumn();
    public List<AttributeMapping> attributes();
    public OcedValidationLevel validationLevel();
}

public enum OcedValidationLevel {
    RELAXED,    // Ignore errors, best effort
    STANDARD,   // Warn on errors
    STRICT      // Fail on any error
}
```

---

## Configuration API

### PIFacadeConfig

```java
public class PIFacadeConfig {
    public static Builder builder();

    // Predictive
    public Builder predictiveEnabled(boolean enabled);
    public Builder modelPath(String path);
    public Builder cacheEnabled(boolean enabled);
    public Builder cacheTtlMinutes(int minutes);
    public Builder confidenceThreshold(double threshold);

    // Prescriptive
    public Builder prescriptiveEnabled(boolean enabled);
    public Builder autoExecuteEnabled(boolean enabled);

    // RAG
    public Builder ragEnabled(boolean enabled);
    public Builder vectorDbUrl(String url);
    public Builder similarityThreshold(double threshold);

    // Observability
    public Builder observabilityEnabled(boolean enabled);
    public Builder otelEndpoint(String endpoint);

    public CaseOutcomePredictor createCaseOutcomePredictor();
    public PrescriptiveEngine createPrescriptiveEngine();
    public ResourceOptimizer createResourceOptimizer();
    public NaturalLanguageQueryEngine createNaturalLanguageQueryEngine();
}
```

---

## Exception Handling

### PIException

```java
public class PIException extends Exception {
    public PIException(String message);
    public PIException(String message, Throwable cause);

    public PIExceptionType getType();
    public String getContext();
}

public enum PIExceptionType {
    MODEL_NOT_FOUND,
    PREDICTION_FAILED,
    CONFIGURATION_ERROR,
    VECTOR_DB_UNAVAILABLE,
    INVALID_FEATURES,
    TIMEOUT,
    UNKNOWN
}
```

**Usage:**

```java
try {
    CaseOutcomePrediction pred = pi.predictCaseOutcome(caseId, data);
} catch (PIException e) {
    if (e.getType() == PIExceptionType.MODEL_NOT_FOUND) {
        logger.warn("Model missing, training new model");
        trainNewModel();
    } else if (e.getType() == PIExceptionType.TIMEOUT) {
        logger.error("Prediction timeout, using fallback");
        useFallbackPrediction();
    }
}
```

---

## Session API

### PISession

```java
public record PISession(
    String sessionId,
    String userId,
    Instant createdAt,
    Duration ttl,
    Map<String, Object> attributes
) {
    public boolean isExpired();
    public void setAttribute(String key, Object value);
    public Object getAttribute(String key);
}
```

---

## Model Registry

### PredictiveModelRegistry

```java
public class PredictiveModelRegistry {
    public static PredictiveModelRegistry getInstance();

    public void registerModel(String name, Path modelPath)
        throws PIException;

    public void registerModelFromUrl(String name, String url)
        throws PIException;

    public void registerModelFromS3(String name, String s3Uri, AwsCredentials creds)
        throws PIException;

    public Model getModel(String name) throws PIException;

    public List<String> listAvailableModels();

    public void unregisterModel(String name);

    public ModelMetadata getMetadata(String name);
}
```

---

## Metrics & Monitoring

### PI Metrics

| Metric | Description |
|--------|-------------|
| `pi.prediction.duration` | Latency of prediction (timer) |
| `pi.prediction.count` | Number of predictions (counter) |
| `pi.prediction.confidence.avg` | Average confidence score (gauge) |
| `pi.cache.hit.rate` | Cache hit ratio (gauge) |
| `pi.action.executed` | Actions executed (counter) |
| `pi.nlquery.latency` | RAG query latency (timer) |

**Collect Metrics:**

```java
@Component
public class PIMetricsCollector {
    private final MeterRegistry meterRegistry;

    public void recordPrediction(String modelName, long durationMs, double confidence) {
        Timer.builder("pi.prediction.duration")
            .tag("model", modelName)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);

        Gauge.builder("pi.prediction.confidence", () -> confidence)
            .register(meterRegistry);
    }
}
```

---

## See Also

- **Tutorial:** `docs/tutorials/pi-getting-started.md`
- **How-To:** `docs/how-to/pi-configuration.md`
- **Explanation:** `docs/explanation/pi-architecture.md`
