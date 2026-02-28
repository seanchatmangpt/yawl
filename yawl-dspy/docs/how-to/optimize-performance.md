# How to Optimize DSPy Performance

## Problem

Your DSPy programs are running slowly and you need to optimize them for production workloads.

## Solution

Use GEPA optimization with the PERFORMANCE target and implement caching strategies.

### Step 1: Configure Performance Optimization

Create or update `gepa-optimization.toml`:

```toml
[optimization_targets]
default = "performance"

[targets.performance]
name = "Optimized Performance"
weight = 1.0
metrics = ["execution_time", "resource_utilization", "throughput"]
target_value = "minimize"

[performance]
max_execution_time_ms = 5000
cache_size = 1000
cache_ttl_seconds = 3600
parallel_execution = true
max_concurrent = 4
```

### Step 2: Enable Caching

```java
import org.yawlfoundation.yawl.dspy.DspyProgramCache;

// Create cache with 1000 entries, 1-hour TTL
DspyProgramCache cache = new DspyProgramCache(1000, Duration.ofHours(1));

// Configure bridge with cache
PythonDspyBridge bridge = PythonDspyBridge.builder()
    .cache(cache)
    .warmCacheOnStartup(true)
    .build();
```

### Step 3: Use Batch Execution

For multiple predictions, use batch execution:

```java
List<Map<String, Object>> inputsList = List.of(
    Map.of("workflow_description", "Process order A"),
    Map.of("workflow_description", "Process order B"),
    Map.of("workflow_description", "Process order C")
);

List<DspyExecutionResult> results = program.executeBatch(inputsList);
```

### Step 4: Profile Performance

```java
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;

DspyExecutionResult result = program.execute(inputs);
DspyExecutionMetrics metrics = result.metrics();

System.out.println("Execution time: " + metrics.executionTimeMs() + "ms");
System.out.println("Compilation time: " + metrics.compilationTimeMs() + "ms");
System.out.println("Cache hit: " + metrics.cacheHit());
System.out.println("Input tokens: " + metrics.inputTokens());
System.out.println("Output tokens: " + metrics.outputTokens());
```

### Step 5: Apply GEPA Performance Optimization

```java
GepaProgramEnhancer enhancer = new GepaProgramEnhancer(bridge);

GepaOptimizationResult result = enhancer.enhanceWithGEPA(
    program,
    GepaOptimizationResult.OptimizationTarget.PERFORMANCE,
    trainingExamples
);

// Performance optimization focuses on:
// - Reducing token usage
// - Minimizing execution time
// - Optimizing resource utilization
```

## Performance Targets

| Metric | Target | Action |
|--------|--------|--------|
| Execution time | < 500ms | Enable caching |
| Memory usage | < 100MB | Reduce cache size |
| Throughput | > 100 req/s | Enable parallel execution |
| P99 latency | < 2s | Optimize prompts |

## Verification

Run the performance benchmark:

```bash
mvn test -Dtest=GepaPerformanceBenchmark -pl yawl-dspy
```

## Related

- **[Create Training Data](extract-training-data.md)** - Build training examples
- **[Configure Caching](configure-caching.md)** - Advanced cache configuration
