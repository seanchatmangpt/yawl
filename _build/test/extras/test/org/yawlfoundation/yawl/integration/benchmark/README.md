# YAWL Integration Performance Benchmark Suite

A comprehensive performance benchmark suite for YAWL integration components, including A2A (Agent-to-Agent), MCP (Model Context Protocol), and Z.ai service benchmarks.

## Overview

This benchmark suite provides:
- **A2A Throughput Benchmarks**: Measure workflow launch, work item management, and query performance
- **MCP Latency Benchmarks**: Evaluate tool execution and resource access performance
- **Z.ai Generation Benchmarks**: Assess AI service response times and throughput
- **Stress Testing**: Identify breaking points and performance limits
- **Regression Detection**: Compare current performance against baselines

## Quick Start

### Running Benchmarks

```bash
# Run complete benchmark suite
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite run

# Run specific component benchmarks
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite component a2a
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite component mcp
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite component zai

# Run individual benchmark classes
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.StressTestBenchmarks
```

### Generating Reports

```bash
# Validate benchmark results
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite validate results/

# Generate consolidated report
java -cp test-classes org.yawlfoundation.yawl.integration.benchmark.BenchmarkSuite report
```

## Benchmark Classes

### 1. IntegrationBenchmarks

The main benchmark class measuring:
- A2A throughput with virtual vs platform threads
- MCP latency for different operation types
- Z.ai generation times with various models
- Error rates under load

**Configuration:**
```java
@Param({"1", "10", "50", "100", "500", "1000"})  // Concurrent requests
@Param({"10", "50", "100", "200"})                // Request delay (ms)
```

### 2. StressTestBenchmarks

Extreme load testing with:
- 10,000+ concurrent requests
- Rapid request storms
- Mixed workloads
- Resource monitoring (CPU, memory, errors)

**Configuration:**
```java
@Param({"100", "500", "1000", "5000", "10000"})  // Extreme load
@Param({"1", "5", "10", "20"})                     // Ramp-up time (seconds)
```

### 3. PerformanceRegressionDetector

Compare current results against baselines to detect:
- Latency regressions > 20%
- Throughput decreases > 15%
- Error rate increases > 1%
- Memory usage growth > 25%

## Key Metrics

### A2A Benchmarks
- **Throughput**: Requests per second
- **Latency**: p50, p95, p99 response times
- **Error Rate**: Failed operations percentage
- **Memory Usage**: Heap consumption patterns

### MCP Benchmarks
- **Tool Execution**: End-to-end latency
- **Resource Access**: Response times for static/template resources
- **JSON Overhead**: Serialization/deserialization costs
- **Connection Management**: Setup and tear-down times

### Z.ai Benchmarks
- **Time to First Token**: Initial response latency
- **Completion Time**: Full response generation
- **Token Generation Rate**: Throughput for large responses
- **Cache Effectiveness**: Cached vs uncached performance

## Test Data

The `TestDataGenerator` class provides realistic synthetic data:
- Workflow specifications (XML format)
- Work item records with metadata
- A2A request payloads
- Z.ai prompts and expected responses

Example usage:
```java
// Generate test data
List<Map<String, Object>> workItems = TestDataGenerator.generateWorkItems(100);
List<Map<String, Object>> a2aRequests = TestDataGenerator.generateA2ARequests("launch_workflow", 50);
List<String> zaiPrompts = TestDataGenerator.generateZaiPrompts(20);
```

## Running with JMH

For advanced JMH configuration:

```bash
# Run with custom JVM options
java -jar jmh.jar -rf json -rf json -rff results.json \
  -jvmArgs "-Xms2g -Xmx4g -XX:+UseG1GC" \
  -wi 5 -i 10 -t 8 -f 1 \
  org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks
```

## Expected Performance Targets

### A2A Server
- **Throughput**: > 1000 requests/sec (virtual threads)
- **p95 Latency**: < 200ms for simple operations
- **Error Rate**: < 0.5% under normal load

### MCP Server
- **Tool Execution**: < 100ms p95
- **Resource Access**: < 50ms p95
- **JSON Serialization**: < 10ms overhead

### Z.ai Service
- **Fast Models**: < 100ms response time
- **Analysis Tasks**: < 500ms response time
- **Concurrent Requests**: Support 100+ simultaneous requests

## Integration with Build System

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
        <version>1.37</version>
        <executions>
          <execution>
            <phase>test</phase>
            <goals>
              <goal>benchmark</goal>
            </goals>
            <configuration>
              <resultFormat>json</resultFormat>
              <resultFile>target/benchmarks.json</resultFile>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

## File Structure

```
benchmark/
├── IntegrationBenchmarks.java      # Main benchmark suite
├── StressTestBenchmarks.java      # Extreme load testing
├── PerformanceRegressionDetector.java # Regression analysis
├── TestDataGenerator.java          # Synthetic data generator
├── BenchmarkRunner.java           # Command-line interface
├── BenchmarkSuite.java            # Comprehensive test runner
└── README.md                      # This file
```

## Troubleshooting

### Common Issues

1. **Out of Memory**: Increase JVM heap size (`-Xms4g -Xmx8g`)
2. **Slow Execution**: Reduce concurrent requests or increase delays
3. **JVM Startup Time**: Use JMH's `-prof perfnorm` for warmup tuning

### Debug Mode

```bash
# Enable debug logging
java -Djava.util.logging.config.file=logging.properties \
     -cp test-classes \
     org.yawlfoundation.yawl.integration.benchmark.IntegrationBenchmarks
```

## Contributing

When adding new benchmarks:

1. Follow existing naming conventions
2. Include comprehensive JMH annotations
3. Add realistic test data scenarios
4. Document performance expectations
5. Add regression detection thresholds

## License

Copyright (c) 2004-2026 The YAWL Foundation. Licensed under the LGPL License.