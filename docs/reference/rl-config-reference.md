# RlConfig Parameter Reference

**Document type:** Configuration Reference
**Audience:** System administrators, RL developers
**Purpose:** Complete reference for RlConfig parameters in YAWL v6.0.0-GA
**Version:** v6.0.0-GA

---

## Overview

The RlConfig class configures reinforcement learning optimization parameters for YAWL workflow specifications. This reference provides detailed information about all available parameters, their defaults, validation rules, and performance implications.

---

## Parameter Table

### Core RL Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `iterations` | int | 100 | 10-1000 | iterations ≥ 10 | Number of RL optimization iterations to perform |
| `populationSize` | int | 50 | 10-200 | populationSize ≥ 10 | Size of the population for genetic algorithm |
| `mutationRate` | double | 0.1 | 0.01-1.0 | mutationRate > 0 | Probability of mutation for genetic operations |
| `crossoverRate` | double | 0.7 | 0.1-1.0 | crossoverRate > 0 | Probability of crossover for genetic operations |
| `eliteSize` | int | 5 | 1-50 | eliteSize ≥ 1 | Number of elite individuals to preserve each generation |
| `selectionPressure` | double | 2.0 | 1.0-10.0 | selectionPressure > 1.0 | Selection pressure for tournament selection |

### Control Flow Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `maxConcurrentCases` | int | 1000 | 1-10000 | maxConcurrentCases ≥ 1 | Maximum number of workflow cases running concurrently |
| `workQueueCapacity` | int | 500 | 100-5000 | workQueueCapacity ≥ 100 | Size of the work item queue before rejection |
| `timeoutSeconds` | int | 300 | 60-3600 | timeoutSeconds ≥ 60 | Timeout for individual workflow executions |

### Performance Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `useVirtualThreads` | boolean | true | - | - | Enable virtual threads for parallel execution |
| `carrierThreadCount` | int | 200 | 1-512 | carrierThreadCount ≤ 512 | Number of carrier threads for virtual thread scheduler |
| `executorQueueSize` | int | 10000 | 1000-50000 | executorQueueSize ≥ 1000 | Size of the virtual thread executor queue |
| `parallelismThreshold` | int | 100 | 10-1000 | parallelismThreshold ≥ 10 | Minimum tasks for parallel execution |

### Search Strategy Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `searchStrategy` | enum | `GENETIC` | GENETIC, SIMULATED_ANNEALING, RANDOM_SEARCH | - | RL search algorithm to use |
| `neighborhoodSize` | int | 10 | 5-50 | neighborhoodSize ≥ 5 | Size of neighborhood for local search |
| `temperature` | double | 1000.0 | 100.0-10000.0 | temperature > 100 | Initial temperature for simulated annealing |
| `coolingRate` | double | 0.95 | 0.8-0.99 | coolingRate < 1.0 | Cooling rate for simulated annealing |

### Evaluation Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `evaluationTimeout` | int | 5000 | 1000-30000 | evaluationTimeout ≥ 1000 | Timeout for individual workflow evaluations in ms |
| `numEvaluationsPerCandidate` | int | 5 | 1-10 | numEvaluationsPerCandidate ≥ 1 | Number of evaluations per candidate for statistical significance |
| `scoreAggregation` | enum | `MEAN` | MEAN, MEDIAN, MAX | - | Method to aggregate multiple evaluation scores |
| `earlyStoppingRounds` | int | 20 | 5-100 | earlyStoppingRounds ≥ 5 | Rounds without improvement before early stopping |
| `earlyStoppingThreshold` | double | 0.001 | 0.0001-0.01 | earlyStoppingThreshold > 0 | Minimum score improvement threshold |

### Memory Management Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `maxMemoryPerGeneration` | int | 1024 | 256-4096 | maxMemoryPerGeneration ≥ 256 | Maximum memory per generation in MB |
| `crossoverCacheSize` | int | 1000 | 100-10000 | crossoverCacheSize ≥ 100 | Size of crossover cache |
| `mutationCacheSize` | int | 1000 | 100-10000 | mutationCacheSize ≥ 100 | Size of mutation cache |
| `specificationCacheSize` | int | 100 | 10-500 | specificationCacheSize ≥ 10 | Size of specification XML cache |

### Genetic Algorithm Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `tournamentSize` | int | 3 | 2-10 | tournamentSize ≥ 2 | Size of tournament selection |
| `uniformCrossoverProbability` | double | 0.5 | 0.1-0.9 | uniformCrossoverProbability > 0 | Probability of uniform crossover vs. two-point crossover |
| `adaptiveMutation` | boolean | true | - | - | Enable adaptive mutation rates based on diversity |
| `diversityThreshold` | double | 0.3 | 0.1-0.5 | diversityThreshold > 0 | Threshold for triggering diversity-based mutation |
| `injectionRate` | double | 0.05 | 0.01-0.1 | injectionRate > 0 | Rate of random injection into population |

### Fitness Function Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `executionWeight` | double | 0.4 | 0.0-1.0 | executionWeight ≥ 0 | Weight for execution time in fitness function |
| `resourceWeight` | double | 0.3 | 0.0-1.0 | resourceWeight ≥ 0 | Weight for resource utilization in fitness function |
| `errorWeight` | double | 0.2 | 0.0-1.0 | errorWeight ≥ 0 | Weight for error rate in fitness function |
| `complianceWeight` | double | 0.1 | 0.0-1.0 | complianceWeight ≥ 0 | Weight for compliance in fitness function |

### Parallelism Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `parallelEvaluation` | boolean | true | - | - | Enable parallel evaluation of candidates |
| `evaluationThreadPoolSize` | int | 50 | 10-200 | evaluationThreadPoolSize ≤ 200 | Size of evaluation thread pool |
| `batchSize` | int | 10 | 1-50 | batchSize ≥ 1 | Batch size for parallel evaluation |

### Logging and Monitoring Parameters

| Parameter | Type | Default | Range | Validation | Description |
|-----------|------|---------|-------|------------|-------------|
| `logLevel` | enum | `INFO` | TRACE, DEBUG, INFO, WARN, ERROR | - | RL optimization logging level |
| `logProgressInterval` | int | 10 | 1-100 | logProgressInterval ≥ 1 | Interval for logging progress in iterations |
| `enableMetrics` | boolean | true | - | - | Enable metrics collection |
| `metricsUpdateInterval` | int | 50 | 10-1000 | metricsUpdateInterval ≥ 10 | Interval for updating metrics |

---

## Detailed Parameter Explanations

### Core RL Parameters

#### `iterations`
- **Purpose**: Number of complete generations to run in the RL optimization
- **Performance Impact**: Higher values = better results but longer execution time
- **Best Practices**: Start with 100 for most use cases; increase to 500 for complex specifications
- **Example**: For a specification with 50+ activities, consider setting iterations to 200

#### `populationSize`
- **Purpose**: Number of candidate workflows evaluated per generation
- **Performance Impact**: Larger populations = better exploration but more memory usage
- **Best Practices**: 50-100 for most cases; 200 for highly complex problems
- **Memory Usage**: Approximately 1MB per specification in memory

#### `mutationRate` and `crossoverRate`
- **Purpose**: Genetic operation probabilities
- **Interaction**: mutationRate + crossoverRate should typically sum ≤ 1.0
- **Adaptive Strategy**: Enable `adaptiveMutation` to let these rates vary based on diversity
- **Guidelines**:
  - Low mutation (0.05) + high crossover (0.8): Good for refining good solutions
  - High mutation (0.2) + low crossover (0.5): Good for exploring new areas

### Control Flow Parameters

#### `maxConcurrentCases`
- **Purpose**: Hard limit on simultaneous workflow executions
- **Resource Consideration**: This is the primary bottleneck in parallel execution
- **Calculation**:
  ```
  Total concurrent operations = maxConcurrentCases * populationSize
  ```
- **Recommendation**: Set to 1000 for production environments with ample resources

#### `workQueueCapacity`
- **Purpose**: Buffer size for incoming work items
- **Overflow Behavior**: Items rejected when queue is full (HTTP 429 response)
- **Tuning**: Set based on expected request burst patterns
- **Monitoring**: Track queue utilization to detect bottlenecks

### Performance Parameters

#### `useVirtualThreads`
- **Purpose**: Enable virtual threads for parallel execution
- **Benefits**: 10-100x improvement for I/O-bound operations
- **Requirements**: Java 21+ with virtual thread support enabled
- **Thread Config**:
  ```java
  // Recommended configuration
  -XX:+UseG1GC
  -XX:G1NewCollectionHeapPercent=30
  -XX:VirtualThreadStackSize=256k
  -Djdk.virtualThreadScheduler.parallelism=200
  ```

#### `carrierThreadCount`
- **Purpose**: Number of platform threads for virtual thread scheduling
- **Guideline**: Should match available CPU cores
- **Formula**: carrierThreadCount ≤ Runtime.getRuntime().availableProcessors()
- **Default**: 200 (suitable for systems with 16+ cores)

### Search Strategy Parameters

#### `searchStrategy`
- **GENETIC**: Best for structured problems with clear patterns
- **SIMULATED_ANNEALING**: Good for large search spaces with many local optima
- **RANDOM_SEARCH**: Use for small specifications or quick testing

#### `temperature` and `coolingRate`
- **Simulated Annealing Specific**:
  - Higher temperature = more exploration
  - Lower temperature = more exploitation
  - Cooling rate controls how quickly the temperature decreases
- **Recommended**: Start with temperature=1000, coolingRate=0.95

### Evaluation Parameters

#### `evaluationTimeout`
- **Purpose**: Timeout for evaluating a single workflow candidate
- **Safety Net**: Prevents runaway evaluations of complex workflows
- **Guideline**: Should be 5-10x the expected execution time of a typical case

#### `earlyStoppingRounds` and `earlyStoppingThreshold`
- **Purpose**: Stop optimization when convergence is detected
- **Efficiency**: Saves significant computation time for converged problems
- **Tuning**: Lower values = faster but potential premature stopping
- **Monitoring**: Track `improvement_history` to detect convergence

### Memory Management Parameters

#### `maxMemoryPerGeneration`
- **Purpose**: Memory limit per RL generation
- **Prevention**: Prevents OOM errors during large-scale optimization
- **Calculation**:
  ```
  Memory per generation ≈ populationSize * specSizeInKB * 1MB
  ```
- **Recommendation**: 1024MB for most configurations

#### `crossoverCacheSize` and `mutationCacheSize`
- **Purpose**: Cache frequently used crossover/mutation operations
- **Performance**: Reduces computation overhead by 10-30%
- **Memory Trade-off**: Larger caches = better performance but more memory usage
- **Guideline**: Set to 5-10x the expected number of unique operations

### Genetic Algorithm Parameters

#### `tournamentSize`
- **Purpose**: Size of selection tournaments
- **Impact**:
  - Smaller (2-3): More diversity, slower convergence
  - Larger (7-10): Less diversity, faster convergence
- **Recommendation**: 3-5 for most problems

#### `adaptiveMutation`
- **Purpose**: Dynamically adjust mutation rates based on population diversity
- **Benefit**: Maintains exploration capability throughout optimization
- **Diversity Metric**: Based on Hamming distance between candidates
- **Trigger**: When diversity < diversityThreshold, increase mutationRate

### Fitness Function Parameters

#### Weight Configuration
- **Sum Constraint**: executionWeight + resourceWeight + errorWeight + complianceWeight = 1.0
- **Business Alignment**: Adjust weights based on business priorities
- **Common Configurations**:
  ```java
  // High throughput
  executionWeight = 0.6, resourceWeight = 0.3, errorWeight = 0.1, complianceWeight = 0.0

  // High reliability
  executionWeight = 0.2, resourceWeight = 0.3, errorWeight = 0.5, complianceWeight = 0.0
  ```

### Parallelism Parameters

#### `parallelEvaluation`
- **Purpose**: Evaluate multiple candidates concurrently
- **Performance Impact**: Near-linear speedup with virtual threads
- **Limiting Factor**: Often I/O or resource contention
- **Recommendation**: Always enable for production deployments

#### `batchSize`
- **Purpose**: Number of candidates evaluated in parallel batches
- **Overhead**: Too small = context switching overhead
- **Guideline**: 10-20 candidates per batch for most systems
- **Memory Impact**: batchSize * specSize * concurrentBatches

---

## Stage Selection Criteria

### Optimization Stage

- **Use Case**: Creating new workflow specifications from scratch
- **Parameters**:
  ```java
  new RlConfig()
    .setIterations(500)         // More iterations for exploration
    .setPopulationSize(100)     // Larger population for diversity
    .setMutationRate(0.15)     // Higher mutation for exploration
    .setCrossoverRate(0.7)     // Moderate crossover
    .setSearchStrategy(SearchStrategy.GENETIC)
    .setExecutionWeight(0.5)   // Balance execution time
  ```

### Refinement Stage

- **Use Case**: Improving existing workflow specifications
- **Parameters**:
  ```java
  new RlConfig()
    .setIterations(200)         // Moderate iterations
    .setPopulationSize(50)      // Smaller population for refinement
    .setMutationRate(0.05)      // Lower mutation for exploitation
    .setCrossoverRate(0.8)     // Higher crossover for recombination
    .setEarlyStoppingRounds(15) // Early stopping for convergence
    .setExecutionWeight(0.7)   // Focus on execution efficiency
  ```

### Validation Stage

- **Use Case**: Validating against business requirements and SLAs
- **Parameters**:
  ```java
  new RlConfig()
    .setIterations(50)          // Fewer iterations
    .setPopulationSize(30)      // Small validation population
    .setEvaluationTimeout(10000) // Longer evaluation for validation
    .setComplianceWeight(0.6)   // High focus on compliance
    .setErrorWeight(0.3)        // Low tolerance for errors
  ```

---

## Performance Impact Analysis

### Memory Usage

| Component | Base Usage | Per Candidate | Total for PopulationSize=100 |
|-----------|------------|---------------|------------------------------|
| Specification XML | 1KB | 1KB | 100KB |
| Fitness Evaluation | 2KB | 2KB | 200KB |
| Crossover Cache | 500KB | - | 500KB |
| Generation Data | 1MB | - | 1MB |
| **Total** | **~2MB** | **~3KB** | **~1.8MB** |

### CPU Usage

| Operation | Complexity per Operation | Total for PopulationSize=100 |
|-----------|-------------------------|------------------------------|
| Fitness Evaluation | O(n) where n=activities | O(n*100) |
| Crossover | O(m) where m=edges | O(m*50) |
| Mutation | O(k) where k=elements | O(k*10) |
| Selection | O(p*log(p)) | O(100*log(100)) |

### Network I/O

| Operation | Data Size | Frequency |
|-----------|-----------|-----------|
| Work Item Dispatch | ~100KB | Continuous |
| Status Updates | ~1KB | Every 10 iterations |
| Result Collection | ~50KB | Every iteration |

---

## Validation Rules

### Parameter Validation

```java
// Valid configuration
RlConfig config = new RlConfig()
    .setIterations(100)
    .setPopulationSize(50)
    .setMutationRate(0.1)
    .setCrossoverRate(0.7)
    .validate();  // Returns success

// Invalid configuration - would throw RlConfigException
RlConfig invalid = new RlConfig()
    .setIterations(5)  // Below minimum
    .setMutationRate(1.5);  // Above maximum
invalid.validate();  // throws RlConfigException
```

### Constraint Validation

The following constraints are enforced:

1. **Weight Constraint**: All fitness weights must sum to 1.0 (±0.001)
2. **Population Balance**: populationSize must be divisible by 10
3. **Time Consistency**: evaluationTimeout > timeoutSeconds * 10
4. **Memory Constraint**: totalMemoryUsage ≤ maxMemoryPerGeneration

---

## Configuration Examples

### Production Configuration

```java
RlConfig productionConfig = new RlConfig()
    .setIterations(300)
    .setPopulationSize(100)
    .setMutationRate(0.1)
    .setCrossoverRate(0.7)
    .setEliteSize(10)
    .setSearchStrategy(SearchStrategy.GENETIC)
    .setUseVirtualThreads(true)
    .setCarrierThreadCount(200)
    .setMaxMemoryPerGeneration(2048)
    .setExecutionWeight(0.6)
    .setResourceWeight(0.3)
    .setErrorWeight(0.1)
    .setComplianceWeight(0.0)
    .setParallelEvaluation(true)
    .setLogLevel(LogLevel.INFO);
```

### Development Configuration

```java
RlConfig devConfig = new RlConfig()
    .setIterations(50)  // Quick iterations for development
    .setPopulationSize(20)
    .setMutationRate(0.15)
    .setCrossoverRate(0.7)
    .setUseVirtualThreads(false)  // Disable for easier debugging
    .setLogLevel(LogLevel.DEBUG)
    .setLogProgressInterval(5)  // More frequent logging
    .setEnableMetrics(true);
```

### High-Performance Configuration

```java
RlConfig highPerfConfig = new RlConfig()
    .setIterations(1000)  // Maximum iterations
    .setPopulationSize(200)
    .setMutationRate(0.05)  // Low mutation for exploitation
    .setCrossoverRate(0.85)
    .setEliteSize(20)  // Strong elitism
    .setEvaluationTimeout(2000)  // Fast evaluations
    .setEarlyStoppingRounds(10)  // Fast convergence
    .setUseVirtualThreads(true)
    .setCarrierThreadCount(400)  // More carrier threads
    .setParallelEvaluation(true)
    .setBatchSize(20);
```

---

## Monitoring and Tuning

### Key Metrics to Monitor

| Metric | Target Range | Action if Out of Range |
|--------|--------------|------------------------|
| `average_score` | >0.8 | Increase iterations or adjust weights |
| `improvement_rate` | >0.01 per iteration | Check for stagnation, increase mutation |
| `convergence_time` | <iterations * 0.5 | Consider early stopping |
| `memory_usage` | <maxMemoryPerGeneration * 0.8 | Increase maxMemoryPerGeneration |
| `evaluation_time` | <evaluationTimeout * 0.5 | Reduce complexity or increase timeout |

### Tuning Workflow

1. **Baseline**: Run with default parameters
2. **Measure**: Collect performance metrics
3. **Adjust**: Based on bottlenecks
4. **Validate**: Compare against baseline
5. **Iterate**: Repeat as needed

### Common Tuning Scenarios

#### Scenario 1: Slow Convergence
- **Problem**: Improvements stop early
- **Solution**: Increase iterations, adjust mutation rate
  ```java
  .setIterations(500)
  .setMutationRate(0.2)
  .setEarlyStoppingRounds(10)
  ```

#### Scenario 2: High Resource Usage
- **Problem**: Memory or CPU exhausted
- **Solution**: Reduce population size, enable virtual threads
  ```java
  .setPopulationSize(50)
  .setUseVirtualThreads(true)
  .setMaxMemoryPerGeneration(1024)
  ```

#### Scenario 3: Poor Quality Results
- **Problem**: Generated workflows don't meet requirements
- **Solution**: Adjust fitness weights, increase diversity
  ```java
  .setExecutionWeight(0.7)
  .setErrorWeight(0.2)
  .setAdaptiveMutation(true)
  ```

---

## Error Handling

### Validation Errors

```java
try {
    config.validate();
} catch (RlConfigException e) {
    // Handle specific validation errors
    switch (e.getErrorCode()) {
        case INVALID_ITERATIONS:
            // Adjust iteration count
            break;
        case INVALID_WEIGHTS:
            // Normalize weights
            break;
        case MEMORY_EXCEEDED:
            // Reduce population size
            break;
    }
}
```

### Runtime Errors

```java
try {
    RlOptimizer optimizer = new RlOptimizer(config);
    List<WorkflowCandidate> results = optimizer.optimize(specification);
} catch (RlOptimizationException e) {
    // Handle optimization failures
    switch (e.getErrorCode()) {
        case CONVERGENCE_FAILED:
            // Try with different parameters
            break;
        case TIMEOUT_EXCEEDED:
            // Increase timeout or reduce complexity
            break;
        case OUT_OF_MEMORY:
            // Reduce population size
            break;
    }
}
```

---

## Best Practices

### 1. Parameter Configuration

- **Start Conservative**: Begin with default parameters, then optimize
- **Monitor Trends**: Track improvement over multiple runs
- **Document Changes**: Keep a log of parameter modifications

### 2. Resource Management

- **Memory First**: Monitor memory usage closely
- **Virtual Threads**: Always use virtual threads for I/O-bound work
- **Batch Processing**: Process in batches to manage memory

### 3. Quality Assurance

- **Validation**: Always validate configurations before use
- **Testing**: Test with small specifications first
- **Rollback**: Keep previous configurations for rollback

### 4. Performance Optimization

- **Parallel Processing**: Maximize parallel execution
- **Caching**: Use appropriate caching strategies
- **Early Stopping**: Enable early stopping when possible

---

**Related Documentation:**
- [GRPO API Reference](../api/grpo-endpoints.md)
- [YAWL Virtual Threads](../virtual-threads.md)
- [Environment Variables](../environment-variables.md)
- [Performance Baselines](../performance-baselines.md)

**Support:**
For configuration assistance, contact the YAWL engineering team or check the community forums.