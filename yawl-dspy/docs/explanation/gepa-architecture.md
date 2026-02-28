# GEPA Architecture

## What is GEPA?

GEPA (Gradient Estimation for Prompt Architecture) is an optimization technique for DSPy programs that iteratively improves prompt quality through behavioral analysis and performance metrics.

## Why GEPA?

Traditional DSPy optimization (BootstrapFewShot, MIPRO) focuses on accuracy but doesn't consider:

- **Behavioral correctness** - Does the output match expected workflow patterns?
- **Performance constraints** - Is the program fast enough for production?
- **Resource utilization** - Are we using tokens efficiently?

GEPA addresses all three through multi-objective optimization.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     GEPA Optimization Pipeline                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────────┐   │
│  │ Historical  │───▶│  Footprint   │───▶│  Optimization    │   │
│  │ Workflows   │    │  Extraction  │    │  Engine          │   │
│  └─────────────┘    └──────────────┘    └──────────────────┘   │
│         │                  │                     │              │
│         ▼                  ▼                     ▼              │
│  ┌─────────────┐    ┌──────────────┐    ┌──────────────────┐   │
│  │ Training    │    │ Behavioral   │    │ Performance      │   │
│  │ Examples    │    │ Patterns     │    │ Metrics          │   │
│  └─────────────┘    └──────────────┘    └──────────────────┘   │
│                                                 │               │
│         ┌───────────────────────────────────────┤               │
│         ▼                                       ▼               │
│  ┌─────────────────┐              ┌────────────────────────┐   │
│  │ BEHAVIORAL      │              │ PERFORMANCE            │   │
│  │ Target          │              │ Target                 │   │
│  │                 │              │                        │   │
│  │ • Footprint     │              │ • Execution time       │   │
│  │   agreement     │              │ • Token efficiency     │   │
│  │ • Pattern       │              │ • Resource usage       │   │
│  │   matching      │              │ • Throughput           │   │
│  └─────────────────┘              └────────────────────────┘   │
│         │                                       │               │
│         └───────────────────┬───────────────────┘               │
│                             ▼                                   │
│                   ┌─────────────────┐                           │
│                   │ BALANCED        │                           │
│                   │ Target          │                           │
│                   │                 │                           │
│                   │ 70% behavioral  │                           │
│                   │ 30% performance │                           │
│                   └─────────────────┘                           │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Optimization Targets

### Behavioral Target

Optimizes for **correctness** of workflow behavior:

```java
GepaOptimizationResult result = enhancer.enhanceWithGEPA(
    program,
    GepaOptimizationResult.OptimizationTarget.BEHAVIORAL,
    trainingExamples
);
```

**Focus:**
- Footprint agreement score (0.0 - 1.0)
- Pattern matching accuracy
- Control flow correctness

**Use when:**
- Workflow correctness is critical
- Regulatory compliance required
- Safety-critical applications

### Performance Target

Optimizes for **speed and efficiency**:

```java
GepaOptimizationResult result = enhancer.enhanceWithGEPA(
    program,
    GepaOptimizationResult.OptimizationTarget.PERFORMANCE,
    trainingExamples
);
```

**Focus:**
- Execution latency
- Token efficiency
- Resource utilization

**Use when:**
- High throughput required
- Real-time processing needed
- Cost optimization important

### Balanced Target

Optimizes for **both** with configurable weights:

```java
GepaOptimizationResult result = enhancer.enhanceWithGEPA(
    program,
    GepaOptimizationResult.OptimizationTarget.BALANCED,
    trainingExamples
);
```

**Focus:**
- 70% behavioral weight
- 30% performance weight

**Use when:**
- General-purpose optimization
- Production deployments
- Uncertain requirements

## Behavioral Footprints

A **behavioral footprint** captures the execution patterns of a workflow:

```java
public record BehavioralFootprint(
    Set<ActivityRelation> directSuccession,    // A → B
    Set<ActivityRelation> concurrency,          // A || B
    Set<ActivityRelation> exclusivity           // A × B
) {
    public double agreementWith(BehavioralFootprint other) {
        // Calculate Jaccard similarity
    }
}
```

### Footprint Relations

| Relation | Symbol | Meaning |
|----------|--------|---------|
| Direct Succession | A → B | A always precedes B |
| Concurrency | A ∥ B | A and B can execute in parallel |
| Exclusivity | A × B | A and B never both execute |

### Perfect Footprint Agreement

A workflow has **perfect footprint agreement** when its footprint exactly matches the reference:

```java
if (footprintAgreement == 1.0) {
    // Perfect generation - all patterns match
}
```

## Training Data Extraction

GEPA learns from historical workflow executions:

```java
HistoricalWorkflowExtractor extractor = new HistoricalWorkflowExtractor();

List<DspyTrainingExample> examples = extractor.extractPerfectWorkflowExamples(
    completedWorkflow,
    "behavioral"  // optimization target
);

// Filter for high quality
List<DspyTrainingExample> highQuality = extractor.filterByQuality(examples, 0.95);
```

### Quality Metrics

| Metric | Description | Threshold |
|--------|-------------|-----------|
| Footprint Score | Behavioral correctness | ≥ 0.95 |
| Performance Score | Execution efficiency | ≥ 0.80 |
| Throughput Score | Tasks per second | ≥ 10 |

## Configuration

GEPA configuration in `gepa-optimization.toml`:

```toml
[gepa]
max_iterations = 100
convergence_threshold = 0.001
learning_rate = 0.01

[targets.behavioral]
weight = 1.0
footprint_agreement_threshold = 1.0

[targets.performance]
weight = 1.0
max_execution_time_ms = 5000
max_tokens = 4000

[targets.balanced]
behavioral_weight = 0.7
performance_weight = 0.3
```

## Integration Points

### With YAWL Engine

```java
// Optimize based on actual engine execution
YNetRunner runner = new YNetRunner();
runner.start(workflow);

PerformanceMetrics metrics = runner.getMetrics();
GepaOptimizationResult result = gepaOptimizer.optimize(
    program,
    trainingExamples,
    metrics
);
```

### With MCP

```java
// Expose GEPA via MCP tools
List<SyncToolSpecification> tools = GepaMcpTools.createAll();
server.addTools(tools);
```

### With A2A

```java
// Enable autonomous agents to optimize
GepaA2ASkill skill = new GepaA2ASkill("gepa_optimizer", registry);
agentRegistry.register(skill);
```

## Best Practices

1. **Start with behavioral** - Ensure correctness before optimizing performance
2. **Use historical data** - At least 100 high-quality training examples
3. **Monitor convergence** - Stop when improvement < 0.1%
4. **Validate results** - Always run PerfectWorkflowValidator
5. **Cache optimizations** - Store optimized programs to disk

## Performance Characteristics

| Metric | Behavioral | Performance | Balanced |
|--------|------------|-------------|----------|
| Optimization time | ~30s | ~15s | ~25s |
| Footprint agreement | 1.0 | 0.85 | 0.95 |
| Execution speed | 1.0x | 2.5x | 1.8x |
| Token efficiency | 1.0x | 1.5x | 1.3x |
