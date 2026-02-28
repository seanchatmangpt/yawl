# How-To: Configure Process Intelligence

This guide covers production setup of the YAWL Process Intelligence (PI) module with real models, caching, and monitoring.

---

## Quick Setup (5 minutes)

For a minimal PI deployment with defaults:

```java
PIFacadeConfig config = PIFacadeConfig.builder()
    .predictiveEnabled(true)
    .prescriptiveEnabled(true)
    .modelPath("/opt/yawl/models")
    .cacheEnabled(true)
    .build();

ProcessIntelligenceFacade pi = new ProcessIntelligenceFacade(
    config.createCaseOutcomePredictor(),
    config.createPrescriptiveEngine(),
    config.createResourceOptimizer(),
    config.createNaturalLanguageQueryEngine()
);
```

---

## Full Configuration Reference

### 1. Create a PI Configuration File

Create `application-pi.yml`:

```yaml
yawl:
  pi:
    # Core services
    predictive:
      enabled: true
      model-type: ONNX
      model-path: /opt/yawl/models
      cache-enabled: true
      cache-ttl-minutes: 60
      confidence-threshold: 0.75

    prescriptive:
      enabled: true
      rule-engine: DROOLS
      auto-execute: false
      escalation-enabled: true
      escalation-timeout-minutes: 30

    optimization:
      enabled: true
      algorithm: HUNGARIAN
      constraint-enforcement: STRICT
      max-iterations: 1000

    rag:
      enabled: true
      vector-db: WEAVIATE
      vector-db-url: http://localhost:8080
      embeddings-model: SENTENCE_TRANSFORMERS
      similarity-threshold: 0.8
      context-window: 5

    # Data preparation
    bridge:
      enabled: true
      ocel2-validation: STRICT
      auto-normalize: true

    # Observability
    observability:
      metrics-enabled: true
      tracing-enabled: true
      logging-level: DEBUG
      otel-endpoint: http://localhost:4317

    # Performance
    performance:
      max-case-history-size: 10000
      batch-prediction-enabled: true
      batch-size: 100
      worker-threads: 8
```

### 2. Load Configuration in Java

```java
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PIProperties.class)
public class PIConfig {

    private final PIProperties properties;

    public PIConfig(PIProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ProcessIntelligenceFacade piService() {
        PIFacadeConfig config = PIFacadeConfig.builder()
            .predictiveEnabled(properties.getPredictive().isEnabled())
            .modelPath(properties.getPredictive().getModelPath())
            .cacheEnabled(properties.getPredictive().isCacheEnabled())
            .cacheTtlMinutes(properties.getPredictive().getCacheTtlMinutes())
            .confidenceThreshold(properties.getPredictive().getConfidenceThreshold())
            .prescriptiveEnabled(properties.getPrescriptive().isEnabled())
            .ragEnabled(properties.getRag().isEnabled())
            .vectorDbUrl(properties.getRag().getVectorDbUrl())
            .observabilityEnabled(properties.getObservability().isMetricsEnabled())
            .otelEndpoint(properties.getObservability().getOtelEndpoint())
            .build();

        return new ProcessIntelligenceFacade(
            config.createCaseOutcomePredictor(),
            config.createPrescriptiveEngine(),
            config.createResourceOptimizer(),
            config.createNaturalLanguageQueryEngine()
        );
    }
}

@ConfigurationProperties(prefix = "yawl.pi")
public class PIProperties {
    private PredictiveProperties predictive;
    private PrescriptiveProperties prescriptive;
    private OptimizationProperties optimization;
    private RagProperties rag;
    private BridgeProperties bridge;
    private ObservabilityProperties observability;
    private PerformanceProperties performance;

    // Getters and setters...
}
```

---

## Feature Configuration

### Predictive Intelligence

#### Enable Predictive Models

```java
// Load a pre-trained ONNX model
PredictiveModelRegistry registry = PredictiveModelRegistry.getInstance();
registry.registerModel("case_delay_predictor",
    Paths.get("/opt/yawl/models/delay_predictor.onnx"));

// Or load from S3
registry.registerModelFromS3(
    "case_delay_predictor",
    "s3://my-bucket/models/delay_predictor.onnx",
    awsCredentials
);
```

#### Configure Prediction Caching

```java
PIFacadeConfig config = PIFacadeConfig.builder()
    .cacheEnabled(true)
    .cacheBackend(CacheBackend.REDIS)
    .cacheHost("redis-cache.internal")
    .cachePort(6379)
    .cacheTtlMinutes(60)
    .build();
```

#### Set Confidence Thresholds

```java
CaseOutcomePrediction prediction = pi.predictCaseOutcome(caseId, data);

// Only act if confidence is high enough
if (prediction.confidence() >= 0.8) {
    // Confident in prediction
    executeAction(prediction);
} else {
    // Confidence too low, wait for more data
    logForReview(prediction);
}
```

### Prescriptive Intelligence

#### Configure Automated Execution

```yaml
yawl:
  pi:
    prescriptive:
      auto-execute: true
      confidence-threshold: 0.85
      max-actions-per-case: 3
      actions:
        escalate:
          enabled: true
          escalation-path: linear
          max-escalation-levels: 3
        reroute:
          enabled: true
          reroute-cost-threshold: 1000  # Only reroute high-cost cases
        reallocate:
          enabled: true
          reallocation-cooldown-minutes: 15
```

#### Manual Recommendation Execution

```java
List<ProcessAction> recommendations = pi.getPrescriptiveEngine()
    .recommendActions(context);

for (ProcessAction action : recommendations) {
    System.out.println("Recommended: " + action.actionType());
    System.out.println("Confidence: " + action.confidence());
    System.out.println("Rationale: " + action.explanation());

    // Get approval before executing (e.g., from a human supervisor)
    if (needsApproval(action)) {
        waitForApproval(action);
    }

    // Execute
    pi.getPrescriptiveEngine().executeAction(action);
}
```

### Resource Optimization

#### Configure the Hungarian Algorithm

```yaml
yawl:
  pi:
    optimization:
      algorithm: HUNGARIAN
      constraints:
        - type: CAPACITY
          strict: true
        - type: SKILL_MATCH
          strict: true
        - type: AVAILABILITY
          strict: false
      cost-function: EUCLIDEAN
      max-iterations: 1000
      timeout-seconds: 30
```

#### Programmatic Configuration

```java
AssignmentProblem problem = AssignmentProblem.builder()
    .resources(listOfResources)
    .tasks(listOfTasks)
    .costMatrix(costMatrix)
    .constraints(constraints)
    .build();

ResourceOptimizer optimizer = config.createResourceOptimizer();
AssignmentSolution solution = optimizer.solve(problem);

for (var assignment : solution.assignments()) {
    System.out.println("Assign task " + assignment.taskId() +
        " to resource " + assignment.resourceId() +
        " (cost: " + assignment.cost() + ")");
}
```

### RAG (Natural Language Queries)

#### Configure Vector Database

```yaml
yawl:
  pi:
    rag:
      vector-db: WEAVIATE
      vector-db-url: http://weaviate.internal:8080
      embeddings-model: SENTENCE_TRANSFORMERS
      embeddings-cache-enabled: true
      similarity-threshold: 0.8
      max-context-items: 5
      rerank-enabled: true
      rerank-model: CROSS_ENCODER
```

#### Query Workflow Data

```java
NaturalLanguageQueryEngine nlEngine = pi.getNaturalLanguageQueryEngine();

NlQueryRequest query = NlQueryRequest.builder()
    .question("Which orders were delayed in January?")
    .context("orders")  // Which workflow event log
    .maxResults(10)
    .build();

NlQueryResponse response = nlEngine.query(query);

System.out.println("Answer: " + response.answer());
System.out.println("Evidence:");
for (var cite : response.citations()) {
    System.out.println("  - " + cite.source());
}
```

### Data Preparation

#### OCEL2 Conversion with Validation

```java
OcedSchema schema = OcedSchema.builder()
    .eventIdColumn("case_id")
    .timestampColumn("timestamp")
    .activityColumn("activity")
    .objectTypeColumn("object_type")
    .validationLevel(OcedValidationLevel.STRICT)
    .autoNormalize(true)
    .build();

OcedBridge bridge = OcedBridgeFactory.createCsvBridge(schema);

Path inputCsv = Paths.get("events.csv");
Path outputOcel2 = Paths.get("events.ocel2.json");

ConversionReport report = bridge.convertFromCsv(inputCsv, outputOcel2);

if (!report.isValid()) {
    System.err.println("Validation failed:");
    for (var error : report.errors()) {
        System.err.println("  " + error);
    }
}
```

---

## Integration Points

### Wire PI into the Engine

```java
@Configuration
public class EngineWithPIConfiguration {

    @Bean
    public YEngine yawlEngine() {
        return new YEngine();
    }

    @Bean
    public ProcessIntelligenceFacade piService(PIFacadeConfig config) {
        return new ProcessIntelligenceFacade(
            config.createCaseOutcomePredictor(),
            config.createPrescriptiveEngine(),
            config.createResourceOptimizer(),
            config.createNaturalLanguageQueryEngine()
        );
    }

    @Bean
    public PIIntegrationListener piListener(
            ProcessIntelligenceFacade pi,
            YEngine engine) {
        return new PIIntegrationListener(pi, engine);
    }
}

@Component
public class PIIntegrationListener {
    private final ProcessIntelligenceFacade pi;
    private final YEngine engine;

    public PIIntegrationListener(ProcessIntelligenceFacade pi, YEngine engine) {
        this.pi = pi;
        this.engine = engine;
        registerEventListeners();
    }

    private void registerEventListeners() {
        // On work item enabled
        engine.getEventBus().subscribe(event -> {
            if (event.type() == EventType.WORK_ITEM_ENABLED) {
                onWorkItemEnabled(event);
            }
        });

        // On case completed
        engine.getEventBus().subscribe(event -> {
            if (event.type() == EventType.CASE_COMPLETION) {
                onCaseCompleted(event);
            }
        });
    }

    private void onWorkItemEnabled(YawlEvent event) {
        try {
            String caseId = event.getCaseID();

            // Make prediction
            CaseOutcomePrediction pred = pi.predictCaseOutcome(caseId, null);

            // Get recommendations if risk is high
            if (pred.probability("delayed") > 0.7) {
                List<ProcessAction> actions = pi.getPrescriptiveEngine()
                    .recommendActions(buildContext(caseId));

                // Log recommendation for potential execution
                logRecommendation(actions);
            }
        } catch (Exception e) {
            logger.error("PI prediction failed", e);
        }
    }

    private void onCaseCompleted(YawlEvent event) {
        // Record case outcome for model retraining
        String caseId = event.getCaseID();
        boolean delayed = event.getDurationMinutes() > 1440;

        // Store for future model training
        recordOutcome(caseId, delayed);
    }
}
```

---

## Performance Tuning

### Optimize Prediction Latency

```yaml
yawl:
  pi:
    performance:
      batch-prediction-enabled: true
      batch-size: 100
      worker-threads: 8
      prediction-cache-enabled: true
      cache-ttl-minutes: 60
      max-concurrent-predictions: 50
```

### Configure Model Loading

```java
// Pre-load all models on startup
@PostConstruct
public void preloadModels() throws Exception {
    PredictiveModelRegistry registry = PredictiveModelRegistry.getInstance();

    List<String> modelNames = List.of(
        "case_delay_predictor",
        "cost_estimator",
        "risk_scorer"
    );

    for (String modelName : modelNames) {
        Path modelPath = Paths.get("/opt/yawl/models", modelName + ".onnx");
        if (Files.exists(modelPath)) {
            registry.registerModel(modelName, modelPath);
            logger.info("Preloaded model: " + modelName);
        }
    }
}
```

### Monitor PI Performance

```java
@Component
public class PIMetricsCollector {

    private final MeterRegistry meterRegistry;
    private final ProcessIntelligenceFacade pi;

    @PostConstruct
    public void initMetrics() {
        // Prediction latency
        Timer.builder("pi.prediction.duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        // Cache hit rate
        AtomicInteger cacheHits = new AtomicInteger(0);
        AtomicInteger cacheMisses = new AtomicInteger(0);

        Gauge.builder("pi.cache.hit.rate", () -> {
            int total = cacheHits.get() + cacheMisses.get();
            return total == 0 ? 0 : (double) cacheHits.get() / total;
        }).register(meterRegistry);

        // Prediction confidence
        Gauge.builder("pi.prediction.confidence.avg", () -> {
            // Average confidence across recent predictions
            return 0.85;
        }).register(meterRegistry);
    }
}
```

---

## Monitoring & Alerting

### Health Check Endpoint

```java
@RestController
@RequestMapping("/health/pi")
public class PIHealthController {

    private final ProcessIntelligenceFacade pi;

    @GetMapping
    public ResponseEntity<?> piHealth() {
        try {
            // Test predictive
            pi.predictCaseOutcome("test", Map.of());

            // Test prescriptive
            pi.getPrescriptiveEngine().recommendActions(null);

            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "predictive", "healthy",
                "prescriptive", "healthy",
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                "status", "DOWN",
                "error", e.getMessage()
            ));
        }
    }
}
```

### Alerting Rules (Prometheus)

```yaml
groups:
  - name: yawl_pi
    rules:
      - alert: PIPredictionLatencyHigh
        expr: histogram_quantile(0.95, pi_prediction_duration) > 5
        for: 5m
        annotations:
          summary: "PI prediction latency exceeds 5 seconds"

      - alert: PICacheHitRateLow
        expr: pi_cache_hit_rate < 0.5
        for: 10m
        annotations:
          summary: "PI cache hit rate below 50%"

      - alert: PIServiceDown
        expr: up{job="pi-service"} == 0
        for: 1m
        annotations:
          summary: "PI service is down"
```

---

## Production Checklist

- [ ] Models are pre-trained and located in persistent storage
- [ ] Redis or similar cache backend is configured
- [ ] OTEL tracing endpoint is configured and tested
- [ ] Health check endpoints are monitored
- [ ] Prometheus metrics are scraped
- [ ] AlertManager is configured with notification channels
- [ ] Model retraining pipeline is scheduled (daily/weekly)
- [ ] Prediction cache TTL is tuned for your workload
- [ ] Confidence thresholds match your risk appetite
- [ ] Prescriptive auto-execution is reviewed and approved by stakeholders

---

## Troubleshooting

### "Model not found" error

```java
// Check what models are registered
PredictiveModelRegistry registry = PredictiveModelRegistry.getInstance();
registry.listAvailableModels().forEach(System.out::println);

// Manually register missing model
registry.registerModel("my_model",
    Paths.get("/opt/yawl/models/my_model.onnx"));
```

### "Prediction latency exceeds timeout"

- Increase `worker-threads` in configuration
- Enable `batch-prediction-enabled`
- Check cache hit rate (low cache hit = high latency)
- Consider using a faster model or splitting into microservices

### "RAG queries return empty results"

- Verify vector database is running: `curl http://weaviate:8080/v1/meta`
- Re-index your case data: `nlEngine.reindexEventLog(caseId)`
- Lower `similarity-threshold` if appropriate
- Check that embeddings model matches configuration

---

## See Also

- **Tutorial:** `docs/tutorials/pi-getting-started.md`
- **Reference:** `docs/reference/pi-*.md`
- **Explanation:** `docs/explanation/pi-architecture.md`
