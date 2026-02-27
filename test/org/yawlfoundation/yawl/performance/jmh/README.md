# YAWL MCP Performance Benchmarks

This directory contains JMH (Java Microbenchmark Harness) benchmarks for measuring Model Context Protocol (MCP) tool performance in YAWL.

## Overview

The MCP Performance Benchmarks measure the performance characteristics of MCP tools with targets:
- Tool latency: < 100ms (p95)
- Throughput: > 50 tools/sec
- Memory footprint: Stable under load

## Benchmarks

### 1. toolExecutionLatency
Measures individual tool execution latency.
- Simulates real tool calls with realistic timing
- Measures end-to-end execution time
- Reports p95, average, and sample times

### 2. toolThroughput
Measures tools executed per second.
- Executes tools sequentially to measure throughput
- Includes realistic delays between calls
- Reports operations per second

### 3. concurrentToolExecution
Measures performance under concurrent load.
- Uses virtual threads for high concurrency
- Simulates multiple clients calling tools simultaneously
- Measures throughput under load

### 4. toolResultProcessing
Measures result processing overhead.
- Simulates deserialization and validation
- Measures time spent processing tool results
- Includes formatting and transformation steps

### 5. memoryFootprint
Measures memory usage with MCP integration.
- Tracks heap memory before/after operations
- Periodic GC to measure stable usage
- Reports memory per operation

## Running the Benchmarks

### Prerequisites
- Java 21+ with JMH dependencies
- Maven or Gradle for dependency management
- YAWL MCP tools (YawlYamlConverter, WorkflowSoundnessVerifier)

### Running Individual Benchmarks

```bash
# Run a specific benchmark
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.MCPPerformanceBenchmarks" \
    -Dexec.args="-rf json -r target/mcp-results.json"

# Run with specific parameters
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.MCPPerformanceBenchmarks" \
    -Dexec.args="-rf json -r target/mcp-throughput.json -p toolCallCount=50,p1 concurrentClients=5"
```

### Running All Benchmarks

```bash
# Run the complete benchmark suite
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner"
```

### Running Tests

```bash
# Run the unit tests
mvn test -Dtest=McpPerformanceTest

# Run with verbose output
mvn test -Dtest=McpPerformanceTest -Dmaven.test.showStackTraces=true
```

## Configuration

### JVM Arguments
The benchmarks use the following JVM arguments:
```
-Xms2g -Xmx4g -XX:+UseG1GC -XX:+UseCompactObjectHeaders -XX:MaxGCPauseMillis=200
```

### JMH Parameters
- `toolCallCount`: Number of tool calls (10, 50, 100)
- `concurrentClients`: Number of concurrent clients (1, 5, 10)
- `toolType`: Tool type to test (yaml-converter, soundness-verifier, both)

## Test Data

The benchmarks use realistic test data including:
- Simple single-task workflows
- Complex parallel workflows
- Multi-step sequential workflows
- Conditional logic workflows
- Invalid patterns for error handling

## Expected Results

### Performance Targets
| Metric | Target | Notes |
|--------|--------|-------|
| Tool latency | < 100ms (p95) | Individual tool execution |
| Throughput | > 50 tools/sec | Overall operations per second |
| Memory usage | Stable | No memory leaks under load |
| Concurrent throughput | Scales with threads | Virtual thread efficiency |

### Sample Output
```
Tool yaml-converter executed in 23 ms
Throughput: 52.34 tools/sec (100 tools in 1911 ms)
Concurrent execution: 156.78 tools/sec (5 clients, 100 calls)
Result processing took 3 ms
Memory usage: 2048 KB total, 20 KB per call
```

## Integration

### With A2A Protocol
The benchmarks integrate with the A2A (Agent-to-Agent) protocol infrastructure:
- Uses RestTransport for HTTP communication
- Simulates real MCP tool calls
- Measures integration overhead

### With YAWL Tools
Benchmarks test real YAWL tools:
- YawlYamlConverter: YAML to XML conversion
- WorkflowSoundnessVerifier: Workflow validation
- No mocks or stubs - all tests use real implementations

## Troubleshooting

### Common Issues

1. **OutOfMemoryError**
   - Increase heap size with `-Xmx8g`
   - Check for memory leaks in tool implementations

2. **Benchmark Timeout**
   - Increase timeout with `-t 300000` (5 minutes)
   - Check tool implementations for infinite loops

3. **Inconsistent Results**
   - Use `-jvmArgs "-XX:+AggressiveOpts"` for stable results
   - Increase warmup iterations

### Debug Mode
Run with debug output:
```bash
mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.MCPPerformanceBenchmarks" \
    -Dexec.args="-v -rf json -r target/mcp-debug.json"
```

## Contributing

When adding new benchmarks:
1. Follow the existing pattern with JMH annotations
2. Include realistic test data
3. Add appropriate performance assertions
4. Update the README with new metrics

## License
Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
