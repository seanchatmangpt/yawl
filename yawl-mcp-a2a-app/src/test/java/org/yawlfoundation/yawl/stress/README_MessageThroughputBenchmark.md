# YAWL MCP A2A Message Throughput Benchmark

## Overview

The `MessageThroughputBenchmark` class provides comprehensive performance testing for message throughput in the YAWL MCP A2A application. It measures message processing performance across different communication patterns, message sizes, and threading models.

## Features

### Communication Patterns Tested
- **1:1** - Single producer, single consumer
- **1:N** - Single producer, multiple consumers
- **N:1** - Multiple producers, single consumer
- **N:M** - Multiple producers, multiple consumers

### Message Sizes Tested
- **Small** - 128 bytes JSON message
- **Medium** - 4KB JSON message
- **Large** - 64KB JSON message

### Threading Models Compared
- **Virtual Threads** - `Thread.ofVirtual().start(runnable)`
- **Platform Threads** - Traditional `Thread` class

## Performance Metrics

The benchmark collects and reports:
- **Throughput** - Messages processed per second
- **Latency Percentiles** - P50, P90, P95, P99 (in microseconds)
- **Total Messages Processed**
- **Test Duration**

## Usage

### Running the Full Benchmark

```bash
# Compile and run the full benchmark
mvn compile exec:java -Dexec.mainClass="org.yawlfoundation.yawl.stress.MessageThroughputBenchmark" -q

# Or using the runner for quick tests
mvn compile exec:java -Dexec.mainClass="org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner" -q
```

### Expected Output

```
=== YAWL MCP A2A Message Throughput Benchmark ===

=== 1:1 Pattern (Single Producer, Single Consumer) ===

Small Messages (128 bytes):
  Messages sent: 10000
  Duration: 5.234 seconds
  Throughput: 1910.23 messages/sec
  Latency P50: 123.45 μs
  Latency P90: 234.56 μs
  Latency P95: 345.67 μs
  Latency P99: 456.78 μs

Medium Messages (4KB):
  Messages sent: 10000
  Duration: 8.456 seconds
  Throughput: 1182.45 messages/sec
  Latency P50: 234.56 μs
  Latency P90: 345.67 μs
  Latency P95: 456.78 μs
  Latency P99: 567.89 μs

Large Messages (64KB):
  Messages sent: 10000
  Duration: 12.789 seconds
  Throughput: 782.34 messages/sec
  Latency P50: 345.67 μs
  Latency P90: 456.78 μs
  Latency P95: 567.89 μs
  Latency P99: 678.90 μs
```

## Implementation Details

### Timing Method
The benchmark uses `System.nanoTime()` for precise timing measurements, which provides nanosecond precision for performance measurements.

### Thread Management
- **Virtual Threads**: Uses `Executors.newVirtualThreadPerTaskExecutor()` for lightweight concurrency
- **Platform Threads**: Uses `Executors.newFixedThreadPool()` for traditional thread management

### Message Generation
Messages are generated as JSON with realistic content:
- Small: Simple JSON with id, timestamp, and payload
- Medium: Complex workflow definition with nested objects
- Large: Batch of 1000 items with detailed metadata

### Latency Measurement
Latency is measured for each message from receipt to completion and stored for percentile calculation.

## Performance Considerations

1. **Warmup Period**: The benchmark performs warmup iterations to avoid JVM startup overhead
2. **Memory Management**: Large message tests may impact memory usage - monitor heap usage
3. **Thread Pool Sizing**: Virtual threads can handle millions of concurrent operations
4. **Network Simulation**: Messages are simulated rather than sent over actual network

## Integration with CI/CD

The benchmark can be integrated into your CI/CD pipeline:

```yaml
# Example GitHub Actions
- name: Run Message Throughput Benchmark
  run: |
    mvn compile test-compile
    java -cp test-classes:target/classes org.yawlfoundation.yawl.stress.MessageThroughputBenchmarkRunner
```

## Troubleshooting

### Common Issues

1. **No Java Runtime**: Ensure Java 17+ is installed
2. **Compilation Errors**: Check that all dependencies are available
3. **Memory Issues**: Increase JVM heap size for large message tests
4. **Timeout Issues**: Adjust `THREAD_TIMEOUT_SECONDS` as needed

### Performance Tips

1. **Use Virtual Threads**: Typically provide better throughput for I/O-bound workloads
2. **Monitor Heap Usage**: Large messages can cause GC pressure
3. **Run Multiple Times**: JVM warmup can affect first-run results
4. **Isolate Tests**: Run patterns independently for accurate comparisons

## Extending the Benchmark

### Adding New Patterns

1. Add a new method following the existing pattern (e.g., `testNewPattern()`)
2. Call it from `run()` with appropriate threading models
3. Update the main method to include the new pattern

### Adding New Message Sizes

1. Define new message templates in the constants
2. Update the `generateMessage()` method
3. Add test cases for the new size

### Adding New Metrics

1. Add new tracking variables (e.g., `AtomicLong` counters)
2. Update the `recordLatency()` method
3. Extend the `printResults()` method to display new metrics

## License

This benchmark is part of the YAWL project and is distributed under the GNU Lesser General Public License. See the LICENSE file for details.