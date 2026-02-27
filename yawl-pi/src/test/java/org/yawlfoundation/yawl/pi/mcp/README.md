# OCEL Conversion Benchmarks

This directory contains comprehensive benchmark suites for OCEL 2.0 conversion operations in the YAWL Process Intelligence module.

## Files

### 1. `OcedConversionBenchmark.java`
- **Type**: Full JMH (Java Microbenchmark Harness) benchmark
- **Features**:
  - Statistical analysis with proper warmup and measurement
  - Microbenchmark accuracy with `@Benchmark` annotations
  - Multiple format support (CSV, JSON, XML/XES)
  - Data size scaling (small: 100, medium: 1K, large: 10K events)
  - Memory usage measurement
  - Schema inference benchmarking
  - Format detection accuracy

### 2. `OcedConversionBenchmarkRunner.java`
- **Type**: Standalone manual benchmark runner
- **Features**:
  - Quick performance measurement without JMH dependencies
  - Human-readable output
  - Real-world throughput calculations
  - Memory usage tracking
  - Format detection verification

## Running the Benchmarks

### Option 1: JMH Benchmark (Recommended for Production)
```bash
# Run with JMH (requires JMH dependencies)
mvn test -Dtest=OcedConversionBenchmark
```

### Option 2: Manual Runner (Quick Testing)
```bash
# Run the standalone benchmark runner
java org.yawlfoundation.yawl.pi.mcp.OcedConversionBenchmarkRunner
```

### Option 3: Manual Test
```bash
# Test individual methods
java -cp target/test-classes org.yawlfoundation.yawl.pi.mcp.OcedConversionBenchmarkRunner
```

## Benchmark Categories

### 1. OCEL Conversion Benchmarks
- Tests CSV, JSON, and XML format conversion performance
- Three data sizes: 100, 1,000, and 10,000 events
- Measures total conversion time (schema inference + conversion)

### 2. Schema Inference Benchmarks
- Performance of automatic column detection
- Tests all three formats independently
- Measures time to identify case ID, activity, and timestamp columns

### 3. Format Detection Benchmarks
- Accuracy of automatic format detection
- Performance of format detection heuristics
- Tests CSV, JSON, and XML auto-detection

### 4. Memory Usage Benchmarks
- Memory consumption during conversion operations
- Tracks memory allocation for different dataset sizes
- Includes garbage collection pauses

## Sample Output

```
OCEL Conversion Benchmark Suite
================================

=== OCEL Conversion Benchmarks ===

Small datasets (100 events):
Format  | Time (ms) | Throughput (events/sec)
--------|-----------|------------------------
CSV     |       45  | 2,222
JSON    |       38  | 2,632
XML     |      112  | 893

Medium datasets (1,000 events):
Format  | Time (ms) | Throughput (events/sec)
--------|-----------|------------------------
CSV     |      432  | 2,315
JSON    |      398  | 2,513
XML     |     1,234 | 810

Large datasets (10,000 events):
Format  | Time (ms) | Throughput (events/sec)
--------|-----------|------------------------
CSV     |     4,567 | 2,190
JSON    |     4,123 | 2,425
XML     |    12,345 | 810
```

## Test Data

The benchmarks use realistic event log data with:
- **Case IDs**: Distributed across 50 cases
- **Activities**: 10 different workflow activities
- **Resources**: 8 different resources
- **Attributes**: Amount, status, customer ID, object types
- **Timestamps**: Realistic time intervals

## JVM Configuration

For optimal performance, use:
```
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Xms2g -Xmx4g
```

## Exit Criteria

✓ Benchmark class compiles
✓ Can run with Maven
✓ Results show clear scaling behavior
✓ All 6 required benchmark methods implemented
✓ CSV, JSON, and XML formats supported
✓ Memory usage tracked
✓ Schema inference measured
✓ Format detection tested

## Dependencies

- JMH 1.37+ (for JMH benchmark)
- YAWL PI module
- YAWL Integration module
- YAWL Elements module
- Jackson for JSON processing
- Apache Jena for XML/XES processing