# A2A Communication Benchmarks

This directory contains comprehensive benchmarks for measuring and validating A2A (Agent-to-Agent) communication performance in the YAWL workflow engine.

## Overview

The A2A communication benchmarks validate the following performance aspects:

1. **Message Latency** - Point-to-point message delay
2. **Message Throughput** - Messages per second between agents  
3. **Concurrent Message Handling** - Performance under concurrent load
4. **Message Serialization Overhead** - Message encoding/decoding cost
5. **Network Partition Resilience** - Performance during network issues

## Performance Targets

| Metric | Target | Validation |
|--------|--------|------------|
| Message Latency p95 | < 100ms | ✓ |
| Message Throughput | > 500 ops/sec | ✓ |
| Concurrent Handling | Linear scaling up to 1000 requests | ✓ |
| Serialization Overhead | < 10% of total time | ✓ |
| Partition Resilience | > 95% success rate | ✓ |

## Test Environment Requirements

### Minimum Requirements
- Java 25+ with virtual threads support
- 8GB RAM minimum (16GB recommended)
- YAWL engine running on `http://localhost:8080/yawl`
- A2A server running on `http://localhost:8081`

### Environment Variables
```bash
export A2A_SERVER_URL=http://localhost:8081
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export JAVA_HOME=/path/to/java25
```

## Running Benchmarks

### Quick Test (Development)
```bash
# Build and run quick benchmarks
./scripts/run_a2a_benchmarks.sh --jmh-only --target-name quick

# Run only integration tests
./scripts/run_a2a_benchmarks.sh --integration-only
```

### Full Benchmark Suite
```bash
# Run complete benchmark suite
./scripts/run_a2a_benchmarks.sh

# Run with custom output directory
./scripts/run_a2a_benchmarks.sh --output-dir ./custom-results
```

### Stress Testing
```bash
# Run stress test with high concurrency
./scripts/run_a2a_benchmarks.sh --target-name stress
```

## Benchmark Components

### 1. JMH Microbenchmarks (`A2ACommunicationBenchmarks.java`)
- **Message Latency Tests**: Measures end-to-end message delivery time
- **Throughput Tests**: Measures messages per second under load
- **Concurrent Tests**: Measures performance with multiple concurrent requests
- **Serialization Tests**: Measures JSON encoding/decoding overhead
- **Resilience Tests**: Measures behavior during network partitions

### 2. Integration Tests (`A2ACommunicationBenchmarksIntegrationTest.java`)
- **End-to-End Workflow Tests**: Complete workflow execution performance
- **Real-world Scenarios**: Actual YAWL workflow operations via A2A
- **Connection Validation**: Ensures test environment is properly configured

### 3. Test Data Generator (`A2ATestDataGenerator.java`)
- **Realistic Message Patterns**: Generates various types of A2A messages
- **Large Message Generation**: Creates large payloads for serialization testing
- **Concurrent Data**: Generates data for stress testing scenarios
- **Partition Simulation**: Simulates network failure scenarios

## Build Configuration

### Using Ant
```bash
# Build benchmark JARs
ant build-jmh

# Run all benchmarks
ant run-all

# Quick benchmark for development
ant quick-benchmark

# Stress testing
ant stress-test
```

### Maven Alternative
```bash
# Build and test using Maven
mvn clean package
mvn exec:java -Dexec.mainClass="org.openjdk.jmh.Main" -Dexec.args="-rf json -rff target/benchmarks.json"
```

## Test Data Structure

### Simple Message
```json
{
  "type": "ping",
  "testCaseId": "test-case-123",
  "timestamp": 1641234567890,
  "message": "ping"
}
```

### Workflow Launch Message
```json
{
  "type": "workflow_launch",
  "testCaseId": "test-case-123",
  "specificationId": "OrderProcessing:2.1",
  "caseData": {
    "requester": "agent-1",
    "priority": "high",
    "metadata": {
      "department": "engineering",
      "project": "alpha"
    }
  }
}
```

### Large Message (Serialization Testing)
```json
{
  "type": "large_message",
  "testCaseId": "test-case-123",
  "largeData": {
    "field_0": "value_0",
    "field_1": 42,
    "field_2": true,
    ...
  },
  "largeArray": [
    {"id": 0, "name": "item_0", "value": 123.45},
    {"id": 1, "name": "item_1", "value": 678.90},
    ...
  ]
}
```

## Performance Metrics

### Message Latency
- **Average**: Time from message send to receive
- **P95**: 95th percentile latency
- **P99**: 99th percentile latency
- **Min/Max**: Best and worst case scenarios

### Throughput
- **Ops/sec**: Operations per second
- **Total Operations**: Successful operations count
- **Success Rate**: Percentage of successful operations

### Memory Usage
- **Heap Usage**: Memory consumption during benchmarks
- **Garbage Collection**: GC frequency and pause times
- **Memory Per Operation**: Average memory per message

## Results Analysis

### JMH Results Format
```json
{
  "benchmark": "A2ACommunicationBenchmarks.benchmarkMessageLatency",
  "mode": "thrpt",
  "primaryMetric": {
    "score": 456.78,
    "scoreError": 23.45,
    "scoreConfidence": [433.33, 480.23]
  }
}
```

### Expected Results
- **Message Latency**: < 100ms average, < 200ms p95
- **Throughput**: > 500 ops/sec for simple messages
- **Serialization**: < 1ms per message for small payloads
- **Concurrent Linear Scaling**: Throughput should scale linearly with thread count
- **Error Rate**: < 5% for normal operation, graceful degradation during partitions

## Troubleshooting

### Common Issues

1. **A2A Server Not Responding**
   ```bash
   curl -s http://localhost:8081/.well-known/agent.json
   # Should return JSON agent card
   ```

2. **JMH Memory Issues**
   - Increase JVM heap: `-Xms4g -Xmx8g`
   - Use ZGC: `-XX:+UseZGC`
   - Enable compact object headers: `-XX:+UseCompactObjectHeaders`

3. **Virtual Thread Issues**
   - Ensure Java 21+ with virtual threads
   - Use `Executors.newVirtualThreadPerTaskExecutor()`

4. **Network Partition Simulation**
   - Verify firewall settings for unreachable-server.invalid
   - Check network timeout values

### Debug Mode
```bash
# Run with verbose output
./scripts/run_a2a_benchmarks.sh --verbose

# Run single benchmark for debugging
java -jar lib/a2a-benchmarks.jar -t debug -rf json -rff debug-results.json -wi 1 -i 1 -f 1
```

## Continuous Integration

### GitHub Actions Example
```yaml
name: A2A Benchmarks
on: [push, pull_request]

jobs:
  benchmark:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'
          distribution: 'adopt'
      - name: Run benchmarks
        run: ./scripts/run_a2a_benchmarks.sh
      - name: Upload results
        uses: actions/upload-artifact@v3
        with:
          name: benchmark-results
          path: benchmark-results/
```

## Contributing

1. Follow existing code patterns and naming conventions
2. Add appropriate assertions for performance targets
3. Update README when adding new benchmarks
4. Test with real A2A server integration
5. Ensure benchmarks are reproducible

## References

- [JMH Benchmarking Guide](https://openjdk.java.net/projects/code-tools/jmh/)
- [YAWL A2A Server Documentation](../docs/A2A.md)
- [Java Virtual Threads](https://openjdk.org/jeps/444)
- [ZGC Garbage Collector](https://openjdk.java.net/jeps/826)
