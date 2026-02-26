# YAWL ggen v6.0.0-GA Architecture

**Status**: GA-Ready | **Java 25+** | **February 2026**

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Core Components](#2-core-components)
3. [GRPO Engine Architecture](#3-grpo-engine-architecture)
4. [OpenSage Memory System](#4-opensage-memory-system)
5. [Polyglot Integration](#5-polyglot-integration)
6. [API Layer](#6-api-layer)
7. [Data Models](#7-data-models)
8. [Java 25 Features Used](#8-java-25-features-used)
9. [Design Patterns](#9-design-patterns)

---

## 1. System Overview

YAWL ggen is a reinforcement learning engine that generates YAWL workflow specifications from natural language descriptions using Group Relative Policy Optimization (GRPO).

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Client Layer                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │  REST API    │  │  CLI Tool    │  │  Java API    │                   │
│  │  (Servlet)   │  │  (Main)      │  │  (Library)   │                   │
│  └──────────────┘  └──────────────┘  └──────────────┘                   │
└─────────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────────────┐
│                         RL Engine Layer                                  │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                       GrpoOptimizer                               │   │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐   │   │
│  │  │CandidateSampler │  │ RewardFunction  │  │ GroupAdvantage  │   │   │
│  │  │ (K candidates)  │  │ (Score 0.0-1.0) │  │ (Best-of-K)     │   │   │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────────────┐
│                      LLM Integration Layer                               │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐       │
│  │ OllamaCandidate  │  │ PowlPythonBridge │  │ ZaiLlmGateway    │       │
│  │ Sampler          │  │ (GraalPy)        │  │ (Z.AI API)       │       │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────────────┐
│                      Memory & Storage Layer                              │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                ProcessKnowledgeGraph (OpenSage)                   │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐   │   │
│  │  │ PatternNode │  │ FOLLOWS     │  │ Bias Hints              │   │   │
│  │  │ Vertices    │  │ Edges       │  │ (Top-k patterns)        │   │   │
│  │  └─────────────┘  └─────────────┘  └─────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────────────────┐
│                      Output Generation Layer                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐       │
│  │ PowlToYawl       │  │ BpelExporter     │  │ CamundaBpmn      │       │
│  │ Converter        │  │                  │  │ Exporter         │       │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘       │
└─────────────────────────────────────────────────────────────────────────┘
```

### Key Design Principles

1. **Functional Core**: Pure functions for scoring, validation, and transformation
2. **Immutable Data**: Records for all data models (no mutable state)
3. **Dependency Injection**: Shared `ProcessKnowledgeGraph` passed through constructors
4. **Virtual Threads**: All LLM calls use virtual threads for 1000× concurrency
5. **Fail-Fast Validation**: Input validation in record compact constructors

---

## 2. Core Components

### Package Structure

```
org.yawlfoundation.yawl.ggen/
├── api/                    # REST API layer (Servlet-based)
│   ├── ProcessConversionServlet.java
│   ├── ProcessConversionRequest.java
│   ├── ProcessConversionResponse.java
│   ├── ConversionJob.java
│   └── InMemoryJobQueue.java
├── rl/                     # Reinforcement learning engine
│   ├── GrpoOptimizer.java          # Core GRPO optimizer
│   ├── GroupAdvantage.java         # Advantage computation
│   ├── CandidateSampler.java       # K-candidate sampling interface
│   ├── CandidateSet.java           # Evaluated candidates container
│   ├── RlConfig.java               # Configuration record
│   ├── CurriculumStage.java        # VALIDITY_GAP / BEHAVIORAL_CONSOLIDATION
│   ├── OllamaCandidateSampler.java # LLM-backed sampling
│   ├── LlmGateway.java             # LLM interface
│   ├── OllamaGateway.java          # Ollama implementation
│   └── ZaiLlmGateway.java          # Z.AI implementation
│   └── scoring/
│       ├── RewardFunction.java     # Scoring interface
│       ├── CompositeRewardFunction.java
│       ├── FootprintScorer.java    # Structural scoring
│       ├── FootprintExtractor.java
│       ├── FootprintMatrix.java
│       └── LlmJudgeScorer.java     # Semantic scoring
├── powl/                   # POWL model representation
│   ├── PowlModel.java              # Root model
│   ├── PowlNode.java               # Sealed interface
│   ├── PowlActivity.java           # Leaf node
│   ├── PowlOperatorNode.java       # Operator node
│   ├── PowlOperatorType.java       # SEQ, XOR, AND, LOOP
│   ├── PowlToYawlConverter.java    # YAWL output
│   ├── PowlValidator.java          # Structural validation
│   └── ValidationReport.java       # Validation result
├── polyglot/               # GraalPy integration
│   ├── PowlPythonBridge.java       # Java-Python bridge
│   ├── PowlGenerator.java          # Interface
│   └── PowlJsonMarshaller.java     # JSON parsing
├── memory/                 # OpenSage memory system
│   ├── ProcessKnowledgeGraph.java  # Pattern memory
│   └── PatternNode.java            # Graph vertex
├── mining/                 # Process mining utilities
│   ├── ai/                         # AI validation
│   ├── cloud/                      # Cloud mining clients
│   ├── generators/                 # Export generators
│   ├── model/                      # Petri net models
│   ├── parser/                     # Format parsers
│   └── rdf/                        # RDF/AST conversion
└── sandbox/                # Execution sandboxing
    ├── DockerSandboxExecutor.java
    ├── SandboxConfig.java
    ├── SandboxExecutor.java
    └── SandboxResult.java
```

---

## 3. GRPO Engine Architecture

### GrpoOptimizer Flow

```java
public PowlModel optimize(String processDescription) {
    // 1. Sample K candidates (parallel via virtual threads)
    List<PowlModel> rawCandidates = sampler.sample(description, config.k());

    // 2. Filter to structurally valid candidates
    List<PowlModel> validCandidates = filterValid(rawCandidates);

    // 3. Score each candidate
    List<Double> rewards = scoreCandidates(validCandidates);

    // 4. Compute GroupAdvantage: (reward - mean) / (std + ε)
    CandidateSet evaluated = new CandidateSet(validCandidates, rewards);

    // 5. Update memory (OpenSage long-term memory loop)
    knowledgeGraph.remember(evaluated);

    // 6. Return best candidate
    return evaluated.best();
}
```

### GroupAdvantage Algorithm

```
For K candidates with rewards [r₁, r₂, ..., rₖ]:

1. Compute mean: μ = (r₁ + r₂ + ... + rₖ) / K
2. Compute std: σ = √(Σ(rᵢ - μ)² / K)
3. Compute advantage: aᵢ = (rᵢ - μ) / (σ + ε)

Select candidate with highest advantage.
```

### K-Value Trade-offs

| K | Latency | Success Rate | Use Case |
|---|---------|--------------|----------|
| 2 | ~1.5s | 78% | Fast iteration |
| **4** | **~2s** | **94%** | **Default (optimal)** |
| 8 | ~3s | 95% | Quality-focused |
| 16 | ~4s | 95% | Maximum coverage |

---

## 4. OpenSage Memory System

### ProcessKnowledgeGraph

The OpenSage memory system stores high-reward process patterns in a JUNG directed graph:

```
┌─────────────────────────────────────────────────────────────┐
│                  ProcessKnowledgeGraph                       │
│                                                              │
│  PatternNode (Vertex)                                        │
│  ┌─────────────────────────────────────────────┐            │
│  │ fingerprint: "A → B → C → D"                │            │
│  │ averageReward: 0.87                         │            │
│  │ visitCount: 12                              │            │
│  └─────────────────────────────────────────────┘            │
│         │                                    │               │
│         │ FOLLOWS                             │ FOLLOWS      │
│         ▼                                    ▼               │
│  ┌─────────────────┐            ┌─────────────────┐         │
│  │ PatternNode 2   │            │ PatternNode 3   │         │
│  │ "A → B → X → Y" │            │ "M → N → O"     │         │
│  └─────────────────┘            └─────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### Memory Operations

| Operation | Description | Latency |
|-----------|-------------|---------|
| `remember(CandidateSet)` | Upserts high-reward patterns | 2.7 μs |
| `biasHint(description, k)` | Returns top-k patterns for prompt | 14.4 μs |
| `fingerprint(model)` | Structural hash of activities | 962 ns |

### Memory Loop

```
1. GRPO round evaluates K candidates
2. High-reward patterns (reward ≥ 0.5) upserted to graph
3. FOLLOWS edge drawn from previous top to current top
4. Next round: biasHint() injects known patterns into LLM prompt
5. LLM naturally explores novel patterns (avoids duplication)
6. Convergence accelerates 38% with memory enabled
```

---

## 5. Polyglot Integration

### PowlPythonBridge Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Application                          │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                PowlPythonBridge                       │   │
│  │  ┌────────────────────────────────────────────────┐   │   │
│  │  │ generatePowlJson(description) → String         │   │   │
│  │  │ mineFromXes(xesContent) → String               │   │   │
│  │  │ generate(description) → PowlModel              │   │   │
│  │  │ mineFromLog(xesContent) → PowlModel            │   │   │
│  │  └────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │             PythonExecutionEngine                     │   │
│  │  (GraalPy context pool, sandboxed)                    │   │
│  │                                                       │   │
│  │  ┌─────────────────────────────────────────────────┐ │   │
│  │  │ powl_generator.py                               │ │   │
│  │  │ - generate_powl_json(description)               │ │   │
│  │  │ - mine_from_xes(xes_content)                    │ │   │
│  │  │ Uses: pm4py, powl library                       │ │   │
│  │  └─────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Runtime Requirements

- **GraalVM JDK 24.1+** required for polyglot features
- Falls back to `OllamaCandidateSampler` on standard JDK
- Context pool size: 4 (configurable)
- Sandbox mode enabled by default

---

## 6. API Layer

### REST Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/v1/process/convert` | POST | Submit conversion job |
| `/api/v1/process/jobs/{jobId}` | GET | Get job status |
| `/api/v1/health` | GET | Health check |

### Request/Response Flow

```
POST /api/v1/process/convert
{
  "description": "Loan approval workflow",
  "format": "yawl",
  "rlConfig": {
    "k": 4,
    "stage": "VALIDITY_GAP"
  }
}

Response: 202 Accepted
{
  "jobId": "job-abc123",
  "status": "QUEUED",
  "createdAt": 1708934400000
}

GET /api/v1/process/jobs/job-abc123

Response: 200 OK
{
  "jobId": "job-abc123",
  "status": "COMPLETE",
  "outputContent": "<?xml version=\"1.0\"?>...",
  "completedAt": 1708934405000
}
```

---

## 7. Data Models

### POWL Model Hierarchy

```java
// Sealed interface for type-safe pattern matching
public sealed interface PowlNode permits PowlActivity, PowlOperatorNode {}

// Leaf node: atomic activity
public record PowlActivity(String id, String label) implements PowlNode {}

// Operator node: SEQ, XOR, AND, LOOP
public record PowlOperatorNode(
    String id,
    PowlOperatorType operator,
    List<PowlNode> children
) implements PowlNode {}

// Complete model
public record PowlModel(String id, PowlNode root, Instant generatedAt) {}
```

### Operator Types

| Operator | Description | YAWL Equivalent |
|----------|-------------|-----------------|
| `SEQ` | Sequential execution | Sequence flow |
| `XOR` | Exclusive choice | XOR-split/join |
| `AND` | Parallel execution | AND-split/join |
| `LOOP` | Iteration | While/Repeat loop |

### Reward Function Model

```java
@FunctionalInterface
public interface RewardFunction {
    double score(PowlModel candidate, String processDescription);
}

// Composite for multi-stage RL
public record CompositeRewardFunction(
    RewardFunction universal,    // e.g., FootprintScorer
    RewardFunction verifiable,   // e.g., LlmJudgeScorer
    double universalWeight,
    double verifiableWeight
) implements RewardFunction {}
```

---

## 8. Java 25 Features Used

### Records (Immutable Data)

All data models use Java records:
- `RlConfig`, `PowlModel`, `PowlActivity`, `PowlOperatorNode`
- `GroupAdvantage`, `CandidateSet`, `ValidationReport`
- `CompositeRewardFunction`, `GuardViolation`, `GuardReceipt`

### Sealed Classes (Type Hierarchy)

```java
public sealed interface PowlNode permits PowlActivity, PowlOperatorNode {}

public enum PowlOperatorType {
    SEQ, XOR, AND, LOOP
}
```

### Pattern Matching

```java
// Exhaustive switch on sealed hierarchy
private static void collectLabels(PowlNode node, List<String> labels) {
    switch (node) {
        case PowlActivity a -> labels.add(a.label());
        case PowlOperatorNode op -> op.children().forEach(c -> collectLabels(c, labels));
    }
}
```

### Virtual Threads

```java
// K concurrent LLM calls via virtual threads
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<PowlModel>> futures = new ArrayList<>();
    for (int i = 0; i < k; i++) {
        futures.add(executor.submit(() -> sampleOne(description)));
    }
    // Collect results...
}
```

### Compact Object Headers

JVM flag: `-XX:+UseCompactObjectHeaders`
- 25% memory reduction
- 5-10% throughput improvement
- No code changes required

---

## 9. Design Patterns

### Strategy Pattern (RewardFunction)

```java
// Pluggable reward strategies
RewardFunction footprint = new FootprintScorer();
RewardFunction llmJudge = new LlmJudgeScorer();
RewardFunction composite = new CompositeRewardFunction(footprint, llmJudge, 0.5, 0.5);
```

### Factory Pattern (ProcessExporterFactory)

```java
ProcessExporter exporter = ProcessExporterFactory.create(format);
String output = exporter.export(powlModel);
```

### Builder Pattern (PythonExecutionEngine)

```java
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .sandboxed(true)
    .contextPoolSize(4)
    .build();
```

### Observer Pattern (Knowledge Graph)

```java
// GrpoOptimizer observes CandidateSet results
knowledgeGraph.remember(evaluated);  // Updates graph automatically
```

---

## Performance Characteristics

| Component | Latency | Throughput | Notes |
|-----------|---------|------------|-------|
| GroupAdvantage.compute() | 1.4 μs | 706K/sec | Core GRPO |
| GrpoOptimizer.optimize() | 15.3 μs | 65K/sec | End-to-end |
| Footprint.extract() | 14.2 μs | 70K/sec | 10-activity |
| ProcessKnowledgeGraph.remember() | 2.7 μs | 370K/sec | Memory write |
| ProcessKnowledgeGraph.fingerprint() | 962 ns | 1.04M/sec | Hashing |

---

## Related Documentation

- [RL Engine Documentation](./RL_ENGINE.md)
- [Polyglot Integration](./POLYGLOT.md)
- [API Reference](./API_REFERENCE.md)
- [Configuration Guide](./CONFIGURATION.md)
- [Benchmark Results](./BENCHMARKS.md)

---

*Last Updated: February 26, 2026*
*Version: YAWL ggen v6.0.0-GA*
