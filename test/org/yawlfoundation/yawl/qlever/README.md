# QLeveldb Performance Benchmarks

This directory contains comprehensive performance benchmarks for the YAWL QLeveldb integration.

## Overview

The benchmarks measure key performance characteristics of the QLeveldb SPARQL engine:

1. **Cold Start vs Warm Query Latency** - Time to start engine vs time to execute pre-warmed queries
2. **Query Complexity Scaling** - Performance impact of query complexity (simple, medium, complex)
3. **Result Set Size Impact** - How result set size affects performance
4. **Memory Allocation & GC Pressure** - Memory usage patterns and garbage collection impact
5. **Thread Contention Analysis** - Performance under concurrent load
6. **Format Serialization Overhead** - JSON, TSV, CSV serialization performance

## Test Files

### Core Tests

- **`QLeverBenchmarkTest.java`** - Full comprehensive benchmark suite with all scenarios
- **`QuickBenchmarkTest.java`** - Quick smoke test for basic functionality validation
- **`BenchmarkRunner.java`** - Standalone benchmark runner with detailed reporting
- **`BenchmarkUtils.java`** - Utility methods for performance measurement
- **`BenchmarkDataGenerator.java`** - Generates realistic test data and queries

### Running Benchmarks

#### Via Maven (Recommended)

```bash
# Run quick test (smoke test)
mvn test -Dtest=QuickBenchmarkTest -Dqlever.test.index=/path/to/your/qlever-index

# Run full benchmark suite
mvn test -Dtest=QLeverBenchmarkTest -Dqlever.test.index=/path/to/your/qlever-index

# Run specific benchmark
mvn test -Dtest=QLeverBenchmarkTest#testColdStartVsWarmQueryLatency -Dqlever.test.index=/path/to/your/qlever-index
```

#### Via Standalone Runner

```bash
# Run all benchmarks and save results
java -cp /path/to/yawl/classes:target/test-classes \
    org.yawlfoundation.yawl.qlever.BenchmarkRunner \
    /path/to/results/directory
```

## Configuration

### Required Properties

- `qlever.test.index` - Path to QLeveldb index directory (required for all tests)

### Environment Variables

- `BENCHMARK_WARMUP_ITERATIONS` - Number of warmup iterations (default: 10)
- `BENCHMARK_MEASUREMENT_ITERATIONS` - Number of measurement iterations (default: 100)
- `BENCHMARK_THREAD_COUNT` - Number of threads for concurrency tests (default: 8)
- `BENCHMARK_TIMEOUT_SECONDS` - Timeout for individual tests (default: 30)

## Performance Targets

The benchmarks validate against these performance targets:

| Metric | Target | Validation Method |
|--------|--------|-------------------|
| Cold Start | < 60 seconds | `testColdStartVsWarmQueryLatency` |
| Query Response (p95) | < 500ms | `validatePerformanceTargets` |
| Memory Usage | < 2GB | `validatePerformanceTargets` |
| GC Pressure | < 500ms total | `testMemoryAllocationAndGcPressure` |
| Memory Allocation | < 100MB per batch | `testMemoryAllocationAndGcPressure` |

## Benchmark Results

Results are saved in multiple formats:

1. **CSV Summary** - Machine-readable format for analysis
2. **JSON Detailed** - Complete results with metadata
3. **Console Output** - Human-readable summary

Example output structure:
```
benchmark-results/
├── benchmark-summary-2024-01-15_14-30-00.csv
├── benchmark-results-2024-01-15_14-30-00.json
└── benchmark-summary.txt
```

## Sample Output

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

## Query Patterns Used

The benchmarks use realistic SPARQL queries:

### Simple Queries
- Basic SELECT with single triple pattern
- Simple property queries
- Basic type queries

### Complex Queries
- Multi-pattern queries with joins
- Queries with filters and ORDER BY
- Complex property paths
- UNION queries for large result sets

### Stress Test Queries
- Large UNION queries (1-100 parts)
- Queries with variable result sizes
- Randomized parameter combinations

## Memory and GC Analysis

The benchmarks track:

- **Memory allocation** - Bytes allocated per operation
- **GC count** - Number of garbage collection cycles
- **GC pause time** - Time spent in garbage collection
- **Memory growth** - Long-term memory usage patterns

## Concurrency Testing

Tests measure performance with varying thread counts:
- Single thread baseline
- Moderate concurrency (4-8 threads)
- High concurrency (16-32 threads)

Metrics include:
- Throughput per thread
- Response time stability
- Error rates under load

## Format Serialization

Compares serialization performance for different output formats:
- **JSON** - Standard SPARQL Results JSON format
- **TSV** - Tab-separated values
- **CSV** - Comma-separated values

Metrics include:
- Serialization time
- Output size
- Memory overhead

## Troubleshooting

### Common Issues

1. **Engine not starting**
   - Verify `qlever.test.index` points to valid QLeveldb index
   - Check native library dependencies

2. **Out of memory errors**
   - Reduce `BENCHMARK_MEASUREMENT_ITERATIONS`
   - Increase JVM heap size

3. **Test timeouts**
   - Increase `BENCHMARK_TIMEOUT_SECONDS`
   - Check if index is too large for test data

4. **Inconsistent results**
   - Ensure system is not under heavy load
   - Run with fixed seed in `BenchmarkDataGenerator`

### Performance Tips

1. **Warmup** - Always run warmup iterations to account for JIT compilation
2. **Isolation** - Run tests on dedicated systems for best accuracy
3. **Multiple runs** - Run benchmarks multiple times for statistical significance
4. **System monitoring** - Monitor system resources during tests

## Integration with CI/CD

The benchmarks can be integrated into CI/CD pipelines:

```yaml
- name: Run QLever Benchmarks
  run: |
    mvn test -Dtest=QLeverBenchmarkTest \
      -Dqlever.test.index=${{ secrets.QLever_INDEX_PATH }}
    # Check performance targets
    grep "Cold start:" target/surefire-reports/* | grep "< 60" || exit 1
```

## Contributing

When adding new benchmarks:

1. Follow JUnit 5 conventions
2. Include proper warmup and measurement phases
3. Add meaningful scenario descriptions
4. Include performance targets
5. Save results in multiple formats
6. Document expected performance characteristics

## References

- [JMH (Java Microbenchmark Harness)](https://openjdk.org/projects/code-tools/jmh/)
- [Java Performance Testing Best Practices](https://shipilev.net/blog/2016/06-on-jmh/)
- [QLever Documentation](https://github.com/ad-freiburg/qlever)
