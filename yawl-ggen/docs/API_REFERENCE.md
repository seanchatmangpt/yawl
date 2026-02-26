# YAWL ggen v6.0.0-GA API Reference

**Status**: GA-Ready | **Java 25+** | **February 2026**

---

## Table of Contents

1. [REST API](#1-rest-api)
2. [Java API - Core Classes](#2-java-api---core-classes)
3. [Java API - RL Engine](#3-java-api---rl-engine)
4. [Java API - POWL Models](#4-java-api---powl-models)
5. [Java API - Memory System](#5-java-api---memory-system)
6. [Java API - Polyglot](#6-java-api---polyglot)

---

## 1. REST API

### Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/process/convert` | Submit process conversion job |
| GET | `/api/v1/process/jobs/{jobId}` | Get job status |
| GET | `/api/v1/health` | Health check |

### POST /api/v1/process/convert

Submit a process conversion job.

**Request Body:**

```json
{
  "description": "string (required)",
  "format": "yawl | bpel | bpmn (default: yawl)",
  "rlConfig": {
    "k": 4,
    "stage": "VALIDITY_GAP | BEHAVIORAL_CONSOLIDATION",
    "maxValidations": 3,
    "ollamaBaseUrl": "string",
    "ollamaModel": "string",
    "timeoutSecs": 60
  }
}
```

**Response (202 Accepted):**

```json
{
  "jobId": "string",
  "status": "QUEUED",
  "createdAt": 1708934400000
}
```

**Example:**

```bash
curl -X POST http://localhost:8080/api/v1/process/convert \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Order processing: receive order, check inventory, ship",
    "format": "yawl",
    "rlConfig": {
      "k": 4,
      "stage": "VALIDITY_GAP"
    }
  }'
```

### GET /api/v1/process/jobs/{jobId}

Get job status.

**Response (200 OK):**

```json
{
  "jobId": "string",
  "status": "QUEUED | PROCESSING | COMPLETE | FAILED",
  "createdAt": 1708934400000,
  "completedAt": 1708934405000,
  "outputContent": "string (XML)",
  "errorMessage": "string (if failed)"
}
```

**Example:**

```bash
curl http://localhost:8080/api/v1/process/jobs/job-abc123
```

### GET /api/v1/health

Health check endpoint.

**Response (200 OK):**

```json
{
  "status": "UP",
  "version": "1.0.0"
}
```

---

## 2. Java API - Core Classes

### RlConfig

Configuration record for the RL engine.

```java
public record RlConfig(
    int k,                    // 1-16, default 4
    CurriculumStage stage,    // VALIDITY_GAP or BEHAVIORAL_CONSOLIDATION
    int maxValidations,       // 1-10, default 3
    String ollamaBaseUrl,     // LLM API URL
    String ollamaModel,       // Model name
    int timeoutSecs           // HTTP timeout
) {
    // Factory method with defaults
    public static RlConfig defaults() {
        return new RlConfig(
            4,
            CurriculumStage.VALIDITY_GAP,
            3,
            System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434"),
            System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder"),
            60
        );
    }
}
```

**Usage:**

```java
// Default configuration
RlConfig config = RlConfig.defaults();

// Custom configuration
RlConfig custom = new RlConfig(
    8,                                  // K=8 candidates
    CurriculumStage.BEHAVIORAL_CONSOLIDATION,
    5,                                  // 5 retries
    "https://open.bigmodel.cn/api/v1",
    "glm-4.7-flash",
    120                                 // 2-minute timeout
);
```

### CurriculumStage

Enum for curriculum learning stages.

```java
public enum CurriculumStage {
    VALIDITY_GAP,              // Stage A: LLM judge scoring
    BEHAVIORAL_CONSOLIDATION   // Stage B: Footprint scoring
}
```

---

## 3. Java API - RL Engine

### GrpoOptimizer

Core GRPO optimizer for POWL model generation.

```java
public class GrpoOptimizer {

    // Constructor without shared memory
    public GrpoOptimizer(
        CandidateSampler sampler,
        RewardFunction rewardFunction,
        RlConfig config
    );

    // Constructor with shared memory (OpenSage)
    public GrpoOptimizer(
        CandidateSampler sampler,
        RewardFunction rewardFunction,
        RlConfig config,
        ProcessKnowledgeGraph knowledgeGraph
    );

    // Main optimization method
    public PowlModel optimize(String processDescription)
        throws IOException, PowlParseException;

    // Get all candidates with scores (for diagnostics)
    public CandidateSet evaluateCandidates(String processDescription)
        throws IOException, PowlParseException;
}
```

**Usage:**

```java
// Basic usage
RlConfig config = RlConfig.defaults();
ProcessKnowledgeGraph memory = new ProcessKnowledgeGraph();

GrpoOptimizer optimizer = new GrpoOptimizer(
    new OllamaCandidateSampler(config, memory),
    new FootprintScorer(),
    config,
    memory
);

PowlModel model = optimizer.optimize("Order processing workflow");
```

### GroupAdvantage

GRPO advantage computation.

```java
public record GroupAdvantage(
    List<Double> advantages,  // Per-candidate advantage
    double mean,              // Group mean reward
    double std                // Group std deviation
) {
    // Compute from rewards
    public static GroupAdvantage compute(List<Double> rewards);

    // Get index of best candidate
    public int bestIndex();
}
```

### CandidateSet

Container for evaluated candidates.

```java
public record CandidateSet(
    List<PowlModel> candidates,
    List<Double> rewards
) {
    // Get the best candidate (highest reward)
    public PowlModel best();

    // Get the GroupAdvantage for this set
    public GroupAdvantage advantage();
}
```

### CandidateSampler

Interface for candidate sampling.

```java
@FunctionalInterface
public interface CandidateSampler {
    /**
     * Sample K candidate POWL models.
     * @param description Process description
     * @param k Number of candidates
     * @return List of K candidate models
     */
    List<PowlModel> sample(String description, int k) throws IOException;
}
```

### OllamaCandidateSampler

LLM-backed candidate sampler.

```java
public class OllamaCandidateSampler implements CandidateSampler {

    // Constructor with memory integration
    public OllamaCandidateSampler(
        RlConfig config,
        ProcessKnowledgeGraph memory
    );

    // Constructor without memory
    public OllamaCandidateSampler(RlConfig config);

    @Override
    public List<PowlModel> sample(String description, int k) throws IOException;
}
```

---

## 4. Java API - POWL Models

### PowlModel

Complete POWL model container.

```java
public record PowlModel(
    String id,
    PowlNode root,
    Instant generatedAt
) {
    // Factory method with current timestamp
    public static PowlModel of(String id, PowlNode root);
}
```

### PowlNode (Sealed Interface)

Base interface for POWL nodes.

```java
public sealed interface PowlNode permits PowlActivity, PowlOperatorNode {}
```

### PowlActivity

Leaf node representing an atomic activity.

```java
public record PowlActivity(
    String id,
    String label
) implements PowlNode {}
```

### PowlOperatorNode

Operator node for control flow.

```java
public record PowlOperatorNode(
    String id,
    PowlOperatorType operator,
    List<PowlNode> children
) implements PowlNode {}
```

### PowlOperatorType

Control flow operators.

```java
public enum PowlOperatorType {
    SEQ,   // Sequential execution
    XOR,   // Exclusive choice
    AND,   // Parallel execution
    LOOP   // Iteration
}
```

### PowlToYawlConverter

Converts POWL models to YAWL specifications.

```java
public class PowlToYawlConverter {

    public YawlSpecification convert(PowlModel powl);

    // With custom configuration
    public YawlSpecification convert(PowlModel powl, ConversionConfig config);
}
```

### PowlValidator

Validates POWL model structure.

```java
public class PowlValidator {

    public ValidationReport validate(PowlModel model);
}
```

### ValidationReport

Validation result.

```java
public record ValidationReport(
    boolean valid,
    List<String> errors,
    List<String> warnings
) {}
```

---

## 5. Java API - Memory System

### ProcessKnowledgeGraph

OpenSage memory system for pattern storage.

```java
public class ProcessKnowledgeGraph {

    public ProcessKnowledgeGraph();

    // Store high-reward patterns from a GRPO round
    public synchronized void remember(CandidateSet candidateSet);

    // Get bias hints for LLM prompt
    public synchronized String biasHint(String description, int k);

    // Get number of stored patterns
    public synchronized int size();

    // Compute structural fingerprint of a model
    public static String fingerprint(PowlModel model);
}
```

**Usage:**

```java
ProcessKnowledgeGraph memory = new ProcessKnowledgeGraph();

// After GRPO round
CandidateSet evaluated = optimizer.evaluateCandidates(description);
memory.remember(evaluated);

// Before next round, get bias hints
String hints = memory.biasHint(description, 10);
// Inject hints into LLM prompt...
```

### PatternNode

Vertex in the knowledge graph.

```java
public record PatternNode(
    String fingerprint,
    double averageReward,
    int visitCount
) {
    public static PatternNode of(String fingerprint);

    public PatternNode withReward(double reward);
}
```

---

## 6. Java API - Polyglot

### PowlPythonBridge

Java-Python bridge for POWL generation.

```java
public class PowlPythonBridge implements PowlGenerator, AutoCloseable {

    // Default constructor with sandbox and pool size 4
    public PowlPythonBridge();

    // Raw JSON methods
    public String generatePowlJson(String description);
    public String mineFromXes(String xesContent);

    // Parsed model methods
    public PowlModel generate(String processDescription) throws PowlParseException;
    public PowlModel mineFromLog(String xesContent) throws PowlParseException;

    @Override
    public void close();
}
```

**Usage:**

```java
try (PowlPythonBridge bridge = new PowlPythonBridge()) {
    PowlModel model = bridge.generate("Order workflow");
    YawlSpecification yawl = new PowlToYawlConverter().convert(model);

} catch (PythonException e) {
    if (e.getErrorKind() == PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE) {
        // Fall back to OllamaCandidateSampler
    }
}
```

### PowlGenerator Interface

```java
public interface PowlGenerator {
    String generatePowlJson(String description);
    String mineFromXes(String xesContent);
}
```

### RewardFunction Interface

```java
@FunctionalInterface
public interface RewardFunction {
    /**
     * Score a candidate model.
     * @return Score in [0.0, 1.0]
     */
    double score(PowlModel candidate, String processDescription);
}
```

### CompositeRewardFunction

Weighted combination of reward functions.

```java
public record CompositeRewardFunction(
    RewardFunction universal,
    RewardFunction verifiable,
    double universalWeight,
    double verifiableWeight
) implements RewardFunction {

    @Override
    public double score(PowlModel candidate, String processDescription);

    // Factory for Stage A (universal only)
    public static CompositeRewardFunction stageA(RewardFunction universal);

    // Factory for Stage B (verifiable only)
    public static CompositeRewardFunction stageB(RewardFunction verifiable);
}
```

**Usage:**

```java
// Balanced composite
RewardFunction balanced = new CompositeRewardFunction(
    new FootprintScorer(),    // Structural
    new LlmJudgeScorer(),     // Semantic
    0.5, 0.5                  // Equal weights
);

// Production (structural focus)
RewardFunction production = new CompositeRewardFunction(
    new FootprintScorer(),
    new LlmJudgeScorer(),
    0.7, 0.3                  // 70% structural
);
```

---

## Error Types

### PowlParseException

Thrown when POWL parsing fails.

```java
public class PowlParseException extends Exception {
    public PowlParseException(String message);
    public PowlParseException(String message, Throwable cause);
}
```

### PythonException

Thrown for polyglot errors.

```java
public class PythonException extends RuntimeException {
    public enum ErrorKind {
        RUNTIME_NOT_AVAILABLE,
        RUNTIME_ERROR,
        TIMEOUT,
        MEMORY_LIMIT,
        SANDBOX_VIOLATION,
        SYNTAX_ERROR
    }

    public ErrorKind getErrorKind();
}
```

### ValidationExhaustedException

Thrown when self-correction retries are exhausted.

```java
public class ValidationExhaustedException extends Exception {
    public ValidationExhaustedException(String message);
}
```

---

## Related Documentation

- [Architecture Overview](./ARCHITECTURE.md)
- [RL Engine Documentation](./RL_ENGINE.md)
- [Configuration Guide](./CONFIGURATION.md)
- [Polyglot Integration](./POLYGLOT.md)

---

*Last Updated: February 26, 2026*
*Version: YAWL ggen v6.0.0-GA*
