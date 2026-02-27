# DMN Performance Benchmarks

This directory contains JMH (Java Microbenchmark Harness) benchmarks for the YAWL DMN (Decision Model and Notation) evaluation engine.

## Overview

The benchmarks measure the performance of various DMN evaluation scenarios:

1. **Simple Decision Evaluation** - Single rule evaluation with 3 rules
2. **Complex Decision Table** - Large decision table with 100+ rules
3. **First Hit Policy** - Performance with FIRST hit policy
4. **Collect Hit Policy** - Performance with COLLECT hit policy
5. **WASM Bridge Latency** - GraalWasm infrastructure overhead
6. **Schema Validation** - Performance impact of DataModel validation
7. **Collect Aggregation** - SUM, MIN, MAX, COUNT operations
8. **Sequential Evaluations** - Multiple evaluations with different contexts

## Requirements

- Java 25+ with GraalVM Polyglot API
- Maven 3.8+
- The `dmn_feel_engine.wasm` binary must be available on the classpath

## Running Benchmarks

### Run all benchmarks:
```bash
mvn test -Dtest=DmnEvaluationBenchmark
```

### Run specific benchmark:
```bash
mvn test -Dtest=DmnEvaluationBenchmark#benchmarkSimpleDecisionEvaluation
```

### Run with JMH profiler:
```bash
mvn test -Dtest=DmnEvaluationBenchmark -Djmh.output=/path/to/results
```

### Run with verbose output:
```bash
mvn test -Dtest=DmnEvaluationBenchmark -Djmh.verbose=true
```

## Benchmark Configuration

All benchmarks use the following configuration:
```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
```

## Test Data

### Simple Decision Model
- 3 rules for eligibility assessment
- Inputs: age (integer), income (double)
- Output: eligible (boolean)

### Complex Decision Model
- 100 rules for risk assessment
- Input: score (integer)
- Outputs: riskLevel (string), action (string)

### Hit Policy Models
- FIRST: Returns first matching rule
- COLLECT: Aggregates all matching rules

## Expected Performance Results

| Benchmark | Expected Latency | Notes |
|-----------|------------------|-------|
| Simple Evaluation | < 1ms | Baseline performance |
| Complex Table | 1-10ms | Depends on rule count |
| FIRST Hit Policy | < 1ms | Fast matching |
| COLLECT Policy | 1-5ms | Aggregation overhead |
| WASM Bridge | < 0.5ms | Infrastructure cost |
| Schema Validation | +1-2ms | Validation overhead |

## Handling GraalWasm Unavailability

The benchmarks gracefully handle cases where GraalWasm is unavailable:

1. **Setup Phase**: If `DmnException` is thrown during setup, benchmarks skip gracefully
2. **Execution Phase**: Benchmarks check for null service before execution
3. **Integration Tests**: Verify basic functionality without requiring full WASM support

## Troubleshooting

### Classpath Issues
Ensure `dmn_feel_engine.wasm` is in the JAR at `wasm/dmn_feel_engine.wasm`.

### WASM Loading Errors
Check that:
- GraalVM JDK 24.1+ is used
- The WASM binary is not corrupted
- Required permissions are granted

### Performance Issues
- Use `-XX:+UseCompactObjectHeaders` for better performance
- Consider increasing JVM memory with `-Xmx4G`
- Run on warm JVM (ignore first run results)

## Integration with CI/CD

To include benchmarks in your CI pipeline:

```yaml
- name: Run DMN Benchmarks
  run: mvn test -Dtest=DmnEvaluationBenchmark -Djmh.output=benchmarks/
  continue-on-error: true
```

The benchmarks will fail if GraalWasm is unavailable but won't break the build process.