# DataModelling Performance Benchmarks

This document describes the JMH benchmarks for the YAWL DataModelling WASM bridge module.

## Overview

The DataModellingBenchmark class provides performance measurements for the WASM bridge interface that enables schema operations through WebAssembly. These benchmarks help identify performance bottlenecks and track improvements over time.

## Benchmarks

### 1. benchmarkSchemaInference
**Purpose**: Measures schema inference from JSON data
**Operations**:
- Parses JSON document
- Infers schema structure
- Returns schema as JSON string

**What it tests**: WASM bridge performance for schema extraction tasks

### 2. benchmarkDataValidation
**Purpose**: Measures data validation performance
**Operations**:
- Validates JSON data against predefined rules
- Applies field-level validation (required, type, pattern)
- Returns validation result with errors

**What it tests**: Validation throughput and rule processing

### 3. benchmarkWasmCallLatency
**Purpose**: Measures WASM bridge call overhead
**Operations**:
- Simple ping operation through WASM
- Measures JNI-like transition costs

**What it tests**: Bridge latency and interop overhead

### 4. benchmarkBatchProcessing
**Purpose**: Measures batch processing performance
**Operations**:
- Processes 1000 records sequentially
- Applies validation to each record
- Measures total batch throughput

**What it tests**: Scalability and memory efficiency with large datasets

### 5. benchmarkMemoryUsage
**Purpose**: Measures memory efficiency during operations
**Operations**:
- Allocates and processes 1000 data structures
- Tracks memory pressure during WASM operations

**What it tests**: Memory management and garbage collection impact

## Fallback Handling

The benchmarks handle WASM unavailability gracefully:

1. **When WASM is available**: Actual WASM operations are measured
2. **When WASM is unavailable**: Simulated workloads use Thread.sleep() to approximate real work

This ensures the benchmark suite can run in development environments where WASM might not be available.

## Running Benchmarks

### Via Maven
```bash
# Build the project
mvn clean compile test-compile -pl yawl-data-modelling

# Run benchmarks (requires JMH Maven plugin)
mvn jmh:benchmark -pl yawl-data-modelling -Dbenchmark=".*DataModellingBenchmark.*"
```

### Direct Execution
```bash
# Use the provided script
./run-benchmarks.sh

# Or run directly:
java -cp "test/classes:target/classes:$(mvn dependency:build-classpath -pl yawl-data-modelling -Dmdep.outputFile=/dev/stdout)" \
    org.yawlfoundation.yawl.datamodelling.BenchmarkRunner
```

## Expected Results

Based on the benchmark types, you should see:

1. **Schema Inference**: Should scale with JSON size
2. **Data Validation**: Should be linear with rule complexity
3. **WASM Call Latency**: Should be consistently low (<1ms)
4. **Batch Processing**: Should show sub-linear scaling due to overhead
5. **Memory Usage**: Should remain stable without leaks

## Troubleshooting

### ClassNotFoundException
Ensure all dependencies are available:
```bash
mvn dependency:resolve -pl yawl-data-modelling
```

### WASM Not Available
The benchmarks will continue with fallback implementations. To debug:
- Check if `data_modelling_wasm_bg.wasm` exists in `src/main/resources/wasm/`
- Verify GraalVM is properly configured
- Check JVM arguments for GraalVM compatibility

### Performance Issues
- Ensure sufficient memory is allocated (-Xmx2G recommended)
- Run multiple forks for more stable results
- Increase measurement iterations for more accurate results

## Integration Notes

- Benchmarks are integrated into the Maven test suite
- Can be run as part of the standard test lifecycle
- Results can be exported to JSON for further analysis