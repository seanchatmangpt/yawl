# QLeveldb Benchmark Suite - Created Files

This directory contains comprehensive performance benchmarks for the YAWL QLeveldb integration.

## Created Files

### Core Benchmark Files

1. **`QLeverBenchmarkTest.java`** - Full comprehensive benchmark suite with all scenarios
   - Cold start vs warm query latency
   - Query complexity scaling (1 triple, 10, 100, 1000)
   - Result set size impact (10, 100, 1000, 10000 results)
   - Memory allocation per query
   - GC pressure measurement
   - Thread contention analysis
   - Format serialization overhead (JSON vs TSV vs CSV)
   - Performance targets validation

2. **`QuickBenchmarkTest.java`** - Quick smoke test for basic functionality
   - Basic query performance
   - Different query complexities
   - Memory usage
   - Concurrent queries
   - Format serialization

3. **`BenchmarkRunner.java`** - Standalone benchmark runner
   - Runs all benchmark scenarios
   - Generates comprehensive reports
   - Saves results in CSV, JSON, and console formats
   - Includes detailed analysis and statistics

4. **`BenchmarkUtils.java`** - Utility methods for performance measurement
   - GC counting and timing
   - Memory usage calculation
   - Statistical calculations (mean, median, std dev, p95)
   - Performance target validation

5. **`BenchmarkDataGenerator.java`** - Generates realistic test data
   - Simple, medium, and complex query patterns
   - Large UNION queries for scaling tests
   - Random query generation for stress testing
   - Configurable benchmark settings

### Configuration and Documentation

6. **`BenchmarkConfig.properties`** - Configuration properties
   - Default benchmark parameters
   - Performance targets
   - Output settings
   - Concurrency settings

7. **`README.md`** - Comprehensive documentation
   - Overview of all benchmarks
   - Running instructions
   - Performance targets
   - Sample output examples
   - Troubleshooting guide

8. **`run-benchmarks.sh`** - Shell script for running benchmarks
   - Automated benchmark execution
   - Configuration validation
   - Results reporting
   - Performance target validation

## Running the Benchmarks

### Quick Start

```bash
# Run quick smoke test
./run-benchmarks.sh /path/to/qlever-index

# Run full benchmark suite with custom parameters
./run-benchmarks.sh /path/to/qlever-index results-dir 20 200 16 60
```

### Via Maven

```bash
# Run specific test
mvn test -Dtest=QLeverBenchmarkTest -Dqlever.test.index=/path/to/index

# Run quick test
mvn test -Dtest=QuickBenchmarkTest -Dqlever.test.index=/path/to/index
```

### Standalone Runner

```java
java -cp /path/to/classes:target/test-classes \
    org.yawlfoundation.yawl.qlever.BenchmarkRunner \
    /path/to/results/directory
```

## Performance Targets

The benchmarks validate against these targets:

| Metric | Target | Validation Method |
|--------|--------|-------------------|
| Cold Start | < 60 seconds | `testColdStartVsWarmQueryLatency` |
| Query Response (p95) | < 500ms | `validatePerformanceTargets` |
| Memory Usage | < 2GB | `validatePerformanceTargets` |
| GC Pressure | < 500ms total | `testMemoryAllocationAndGcPressure` |
| Memory Allocation | < 100MB per batch | `testMemoryAllocationAndGcPressure` |

## Expected Output

### Example Statistics

```
Cold Start - Statistics:
  Duration: 5,000,000.00 μs mean, 4,900,000.00 μs median, 500,000.00 μs std dev
  Memory: 1024.00 KB avg, Total: 102.40 MB
  GC Pause: 500 ms total (5.00 ms avg)
  Throughput: 20 ops/sec
  Success Rate: 100.00%

WarmQuery - Statistics:
  Duration: 50.00 μs mean, 45.00 μs median, 10.00 μs std dev
  Memory: 0.10 KB avg, Total: 10.00 MB
  GC Pause: 0 ms total (0.00 ms avg)
  Throughput: 20,000 ops/sec
  Success Rate: 100.00%

Cold vs Warm Performance Ratio: 100.0x
```

### Generated Files

- **CSV Summary**: Machine-readable format for analysis
- **JSON Detailed**: Complete results with metadata
- **Console Summary**: Human-readable overview
- **Configuration**: Used benchmark settings

## Integration

The benchmarks can be integrated into:
- CI/CD pipelines for regression testing
- Performance monitoring systems
- Load testing scenarios
- Hardware capacity planning

## Dependencies

- Java 25+ with JMH-style warmup
- Maven for test execution
- QLeveldb native library
- Pre-built test index
