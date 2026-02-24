# YAWL Benchmark Suite

A comprehensive performance benchmark suite for the YAWL workflow engine v6.0.0, built with JMH (Java Microbenchmark Harness).

## Overview

This benchmark suite provides comprehensive performance testing for YAWL engine components:

### Core Benchmarks
- **YAWLEngineBenchmarks**: Core engine performance (startup, task execution, work item operations)
- **WorkflowPatternBenchmarks**: Control flow pattern performance (sequential, parallel, multi-choice, cancel regions)
- **ConcurrencyBenchmarks**: Multi-threaded performance and scalability
- **MemoryBenchmarks**: Memory usage patterns and GC behavior

### Integration Benchmarks
Located in `test/org/yawlfoundation/yawl/integration/benchmark/`:
- **IntegrationBenchmarks**: A2A, MCP, and Z.ai performance
- **StressTestBenchmarks**: Extreme load testing
- **PerformanceRegressionDetector**: Regression analysis against baselines

## Quick Start

### Prerequisites
- Java 25+
- Maven 3.9+
- 4GB+ available memory

### Building
```bash
mvn clean install
```

### Running Benchmarks
```bash
# Run from project root
./scripts/benchmark.sh

# Run specific benchmarks
./scripts/benchmark.sh YAWLEngineBenchmarks
./scripts/benchmark.sh ConcurrencyBenchmarks
./scripts/benchmark.sh MemoryBenchmarks

# Custom configuration
./scripts/benchmark.sh -n 50 -w 10 -t 8 ConcurrencyBenchmarks
```

## Benchmark Classes

### YAWLEngineBenchmarks
Measures fundamental YAWL engine performance:
- `netRunnerTaskExecutionLatency`: Task execution latency
- `workItemCheckoutLatency`: Work item operations
- `statelessEngineCaseCreationThroughput`: Case creation throughput
- `engineStartupPerformance`: Engine initialization time
- `taskTransitionPerformance`: Task state transitions
- `concurrentTaskExecution`: Multi-threaded task processing

### WorkflowPatternBenchmarks
Tests specific YAWL control flow patterns:
- `sequentialWorkflowPerformance`: Linear workflow execution
- `parallelSplitSyncPerformance`: Parallel execution with synchronization
- `multiChoiceMergePerformance`: Conditional branching
- `cancelRegionPerformance`: Task cancellation
- `nOutOfMPatternPerformance`: N-out-of-M patterns
- `mixedPatternComplexityPerformance`: Combined patterns

### ConcurrencyBenchmarks
Evaluates multi-threaded performance:
- `virtualVsPlatformThreadPerformance`: Thread model comparison
- `threadScalingPerformance`: Scaling with thread count
- `concurrentCaseCreationThroughput`: Concurrent case creation
- `resourceContentionUnderLoad`: Resource contention scenarios
- `contextSwitchingOverhead`: Context switching impact

### MemoryBenchmarks
Measures memory usage patterns:
- `heapUsageDuringWorkflowExecution`: Heap consumption
- `gcPressureUnderLoad`: GC activity under load
- `memoryScalabilityWithCaseCounts`: Memory scaling
- `memoryLeakDetection`: Leak detection
- `memoryRecoveryAfterLoad`: Memory recovery

## Performance Targets

### Engine Performance
- Startup: < 60 seconds
- Case creation (P95): < 500ms
- Work item checkout (P95): < 200ms
- Work item checkin (P95): < 300ms
- Task transition: < 100ms
- DB query (P95): < 50ms

### Concurrency
- Virtual threads: 1200+ cases/second
- Platform threads: 800 cases/second
- Scaling efficiency: 85% at 16 threads

### Memory
- GC time: < 5% of total time
- Memory per case: < 2MB
- Memory leaks: < 5MB growth at 10,000 cases

## Test Data Generation

Use `TestDataGenerator` to create synthetic test data:
```java
TestDataGenerator generator = new TestDataGenerator();

// Generate workflow specifications
Map<String, String> specs = generator.generateWorkflowSpecifications();

// Generate work items
List<Map<String, Object>> workItems = generator.generateWorkItems(100);

// Generate performance scenarios
List<Map<String, Object>> scenarios = generator.generatePerformanceScenarios();
```

## Results Format

Benchmarks output results in JSON format:
```json
{
  "benchmark": "org.yawlfoundation.yawl.benchmark.YAWLEngineBenchmarks",
  "mode": "thrpt",
  "threads": 1,
  "forks": 3,
  "warmupIterations": 10,
  "measurementIterations": 50,
  "results": [
    {
      "params": {
        "threadCount": "1"
      },
      "primaryMetric": {
        "score": 1250.0,
        "error": "+/- 45.2",
        "scoreError": "+/- 45.2",
        "unit": "ops/ms"
      }
    }
  ]
}
```

## Baseline Metrics

Baseline metrics are documented in `docs/v6/latest/performance/baseline-metrics.md`. These serve as references for regression detection.

### Regression Thresholds
- Latency: > 20% increase triggers warning
- Throughput: > 15% decrease triggers warning
- Memory: > 25% increase triggers warning
- Errors: > 0.5% error rate triggers warning

## Integration with CI/CD

Add to your Maven profile for automated benchmarking:
```xml
<profile>
  <id>benchmark</id>
  <properties>
    <skipTests>false</skipTests>
  </properties>
  <build>
    <plugins>
      <plugin>
        <groupId>org.openjdk.jmh</groupId>
        <artifactId>jmh-maven-plugin</artifactId>
        <version>${jmh.version}</version>
        <executions>
          <execution>
            <phase>test</phase>
            <goals>
              <goal>benchmark</goal>
            </goals>
            <configuration>
              <resultFormat>JSON</resultFormat>
              <resultFile>target/benchmarks.json</resultFile>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

## Running in Docker

For consistent benchmarking environments:
```bash
docker buildx bake --load
docker run --rm -v $(pwd):/work -w /work cre:0.3.0 ./scripts/benchmark.sh
```

## Troubleshooting

### Common Issues
1. **Out of Memory**: Increase heap size with `-Xms4g -Xmx8g`
2. **Slow Execution**: Reduce iterations or use fewer forks
3. **JVM Startup Time**: Use JMH's `-prof perfnorm` for warmup tuning

### Debug Mode
```bash
./scripts/benchmark.sh -d
```

## Contributing

When adding new benchmarks:
1. Follow JMH best practices
2. Include comprehensive annotations
3. Add to appropriate benchmark class
4. Update baseline metrics
5. Add regression detection thresholds

## License

Copyright (c) 2004-2026 The YAWL Foundation. Licensed under the LGPL License.

## References

- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [YAWL Foundation](https://www.yawlfoundation.org/)
- [Performance Regression Detection](docs/v6/latest/performance/baseline-metrics.md)
