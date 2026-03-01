# Phase 3: Virtual Thread Optimization for YAWL Actor Model

## Overview

Phase 3 implements comprehensive analysis and optimization tools for carrier thread utilization and virtual thread behavior in the YAWL actor model. This phase focuses on measuring and optimizing the performance characteristics of virtual thread-based workflow execution.

## Components Implemented

### 1. CarrierThreadOptimizer

**Purpose**: Test carrier thread counts (1, 2, 4, 8, 16, 32) and measure impact on p99 latency vs carrier utilization.

**Key Features**:
- Tests different carrier thread configurations
- Measures p99 latency for each configuration
- Tracks carrier utilization metrics
- Identifies optimal configuration for different scales
- Simulates realistic YAWL workload patterns

**Usage**:
```java
CarrierThreadOptimizer optimizer = new CarrierThreadOptimizer(meterRegistry);
OptimizationReport report = optimizer.runOptimizationTests();
int optimalCarriers = report.optimalConfig();
```

### 2. VirtualThreadProfiler

**Purpose**: Monitor virtual thread unmounting/parking efficiency and analyze virtual thread behavior patterns.

**Key Features**:
- Tracks virtual thread lifecycle events
- Monitors park/unmount rates and durations
- Detects thread pinning issues
- Analyzes carrier thread utilization patterns
- Provides comprehensive behavioral analysis

**Usage**:
```java
VirtualThreadProfiler profiler = new VirtualThreadProfiler();
profiler.startProfiling();
// ... run workload ...
ProfileReport report = profiler.stopProfiling();
```

### 3. StructuredTaskScopeIntegrationTester

**Purpose**: Test Java 25 StructuredTaskScope integration for coordinated operations.

**Key Features**:
- Tests ShutdownOnFailure and ShutdownOnSuccess modes
- Validates timeout handling
- Tests context propagation across scopes
- Measures performance impact of structured concurrency
- Validates automatic task cancellation

**Usage**:
```java
StructuredTaskScopeIntegrationTester tester = new StructuredTaskScopeIntegrationTester(tracer);
IntegrationTestResults results = tester.runIntegrationTests();
```

### 4. StackDepthAnalyzer

**Purpose**: Analyze virtual thread stack depth usage patterns and validate lifecycle management.

**Key Features**:
- Measures stack depth for virtual threads
- Detects stack overflow risks
- Analyzes stack usage patterns
- Tests recursion and nesting scenarios
- Provides memory pressure correlation

**Usage**:
```java
StackDepthAnalyzer analyzer = new StackDepthAnalyzer();
analyzer.startAnalysis();
// ... run workload ...
StackDepthAnalysisReport report = analyzer.stopAnalysis();
```

### 5. VirtualThreadLifecycleManager

**Purpose**: Validate virtual thread lifecycle management and resource cleanup.

**Key Features**:
- Tracks thread lifecycle states (NEW, RUNNABLE, BLOCKED, WAITING, TERMINATED)
- Detects resource leaks and memory leaks
- Monitors graceful shutdown coordination
- Validates ScopedValue context propagation
- Provides lifecycle event correlation

**Usage**:
```java
VirtualThreadLifecycleManager manager = new VirtualThreadLifecycleManager();
manager.startLifecycleValidation();
// ... run workload ...
LifecycleManagementReport report = manager.stopLifecycleValidation();
```

### 6. PerformanceBenchmarkSuite

**Purpose**: Comprehensive benchmarking for p99 latency vs carrier utilization analysis.

**Key Features**:
- Tests different workload patterns (CPU, IO, mixed, bursty, steady state)
- Measures throughput and latency at various scales
- Provides scalability analysis
- Compares virtual vs platform thread performance
- Generates detailed performance metrics

**Usage**:
```java
PerformanceBenchmarkSuite suite = new PerformanceBenchmarkSuite(meterRegistry);
BenchmarkReport report = suite.runBenchmarkSuite();
```

### 7. AnalysisReportGenerator

**Purpose**: Consolidates and visualizes optimization results from all components.

**Key Features**:
- Combines results from all validation components
- Generates HTML, CSV, and JSON reports
- Provides configuration recommendations
- Identifies performance bottlenecks
- Creates actionable insights

**Usage**:
```java
AnalysisReportGenerator generator = new AnalysisReportGenerator();
AnalysisReport report = generator.generateReport(carrierReport, profileReport, ...);
```

### 8. ActorModelOptimizer

**Purpose**: Main orchestrator that integrates all validation components.

**Key Features**:
- Orchestrates complete optimization workflow
- Runs all analysis components in parallel
- Provides optimization status tracking
- Generates configuration recommendations
- Handles error recovery and timeouts

**Usage**:
```java
ActorModelOptimizer optimizer = new ActorModelOptimizer(meterRegistry, tracer);
OptimizationResult result = optimizer.optimizeVirtualThreadConfiguration();
```

## Test Suite

### CarrierThreadUtilizationTest
- Tests different carrier thread configurations
- Validates p99 latency measurements
- Verifies optimal configuration selection
- Tests scaling behavior and performance scores

### ActorModelValidationSuite
- Comprehensive integration test for all components
- Tests complete optimization workflow
- Validates error handling and resilience
- Verifies report generation and recommendations

## Key Metrics Tracked

### Carrier Thread Optimization
- Carrier utilization percentage
- p99 latency (milliseconds)
- Operations throughput per second
- Success rate percentage
- Memory usage patterns

### Virtual Thread Behavior
- Virtual thread park/unmount rates
- Average park duration
- Thread pinning incidents
- Carrier utilization correlation
- Memory usage correlation

### Structured Concurrency
- Task success rate
- Average latency per operation
- Throughput operations per second
- Context propagation efficiency
- Timeout handling accuracy

### Stack Depth Analysis
- Average stack depth
- Maximum stack depth observed
- Stack overflow incidents
- Stack depth standard deviation
- Memory pressure correlation

### Lifecycle Management
- Thread creation/termination rates
- Resource leak detection
- Context leakage detection
- State transition efficiency
- Graceful shutdown success

### Performance Benchmarking
- p50, p90, p95, p99 latency percentiles
- Operations throughput per second
- CPU utilization percentage
- Memory usage patterns
- Scaling efficiency

## Configuration Recommendations

The optimizer analyzes all metrics and provides recommendations for:

### Carrier Thread Configuration
- Optimal carrier thread count
- Scaling recommendations for different loads
- Utilization target ranges
- Contention mitigation strategies

### Virtual Thread Settings
- Recommended virtual thread pool sizing
- Memory allocation strategies
- Park/unmount optimization
- Thread pinning prevention

### Performance Optimizations
- I/O operation improvements
- CPU-intensive operation handling
- Memory usage optimization
- Context propagation best practices

## Output Files

Generated reports include:

1. **HTML Report**: Comprehensive visualization with charts and recommendations
2. **CSV Export**: Raw data for further analysis
3. **JSON Summary**: Structured data for programmatic processing
4. **Performance Metrics**: Detailed timing and utilization data

## Best Practices

### For Carrier Threads
- Monitor utilization to avoid over-provisioning
- Test with realistic workload patterns
- Consider peak load requirements
- Use auto-scaling for variable workloads

### For Virtual Threads
- Prefer non-blocking I/O operations
- Use ScopedValue for context propagation
- Avoid synchronized blocks in virtual threads
- Monitor stack depth for recursive operations

### For Structured Concurrency
- Use ShutdownOnFailure for error-tolerant operations
- Use ShutdownOnSuccess for any-must-complete operations
- Implement proper timeout handling
- Leverage context propagation for tracing

## Error Handling

### Common Issues and Solutions

1. **High Carrier Utilization**
   - Solution: Increase carrier thread count or optimize workload distribution

2. **Stack Overflows**
   - Solution: Reduce recursion depth or increase stack size

3. **Thread Pinning**
   - Solution: Avoid blocking operations in virtual threads

4. **Resource Leaks**
   - Solution: Implement proper lifecycle management and cleanup

5. **High Latency**
   - Solution: Optimize I/O operations and reduce contention

## Performance Monitoring

### Key Monitoring Points
- Carrier thread utilization
- Virtual thread park/unmount rates
- Stack depth usage patterns
- Memory allocation trends
- Task execution latency

### Alert Thresholds
- Carrier utilization > 80%
- p99 latency > 100ms
- Stack depth > 1000 frames
- Memory usage > 90%
- Task success rate < 95%

## Integration with YAWL

### VirtualThreadPool Integration
The optimizer provides recommendations for the existing VirtualThreadPool class:

```java
// Apply optimizer recommendations
VirtualThreadPool pool = new VirtualThreadPool(
    "workflow-executor",
    optimizer.getRecommendedConfiguration().carrierThreadCount(),
    10  // sampling interval
);
```

### YNetRunner Integration
Optimize YNetRunner thread management based on findings:

```java
// Apply optimized thread configuration
ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
// Use recommended lock configuration
```

## Next Steps

1. **Phase 4**: Production deployment with optimized configuration
2. **Phase 5**: Continuous monitoring and adaptive optimization
3. **Phase 6**: Advanced features like predictive scaling

## Contributing

When contributing to Phase 3:

1. Follow Java 25 conventions for virtual threads
2. Implement comprehensive error handling
3. Add appropriate performance monitoring
4. Include thorough test coverage
5. Document all configuration options
6. Provide clear recommendations in reports

## License

This implementation is part of the YAWL Foundation and is subject to the GNU Lesser General Public License.