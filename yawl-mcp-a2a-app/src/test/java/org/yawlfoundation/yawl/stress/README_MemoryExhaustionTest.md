# MemoryExhaustionTest - Actor Spawn Stress Testing

## Overview

`MemoryExhaustionTest` is a comprehensive stress testing suite that determines the maximum number of concurrent actors that can be spawned before approaching the OutOfMemoryError condition. The test uses `MemoryMXBean` for real-time heap monitoring and implements graceful shutdown mechanisms to prevent actual OOM crashes.

## Features

### Memory Monitoring
- **Real-time heap tracking** using `MemoryMXBean`
- **Multi-pool monitoring** (PS Old Gen and Heap pools)
- **Configurable thresholds** (85% warning, 95% critical)
- **Memory usage reporting** in MB with percentages

### Actor Testing
- **Virtual thread vs platform thread comparison**
- **Memory-consumption simulation** per actor
- **Concurrent actor spawning** with controlled rates
- **Task execution simulation** with memory allocation

### Test Scenarios
1. **Default Heap Testing** - Virtual threads with default JVM heap
2. **Platform Thread Comparison** - Native thread performance
3. **Threshold Testing** - Different memory limits (75%, 85%, 95%)
4. **Memory Recovery** - Post-shutdown memory deallocation
5. **Sustainability Calculation** - Maximum actor capacity
6. **Performance Benchmarking** - Spawning speed analysis
7. **Leak Detection** - Multi-cycle memory growth analysis

## Test Methods

### Core Tests
- `testVirtualThreadsWithDefaultHeap()` - Tests virtual thread spawning with default heap
- `testPlatformThreadsWithDefaultHeap()` - Compares with platform threads
- `testDifferentHeapThresholds()` - Parameterized testing with varying thresholds
- `testMemoryRecovery()` - Verifies memory deallocation after shutdown
- `testCalculateSustainableActorCount()` - Determines maximum capacity
- `testActorSpawningBenchmark()` - Performance measurement
- `testMemoryLeakDetection()` - Multi-cycle leak detection

### Integration Tests
- `testWithRealZaiAgents()` - Tests with real ZAI API if available

## Architecture

### TestMemoryAgent
Simulates actor memory consumption with:
- Configurable memory buffer (default 10MB)
- Task execution simulation
- Cleanup and memory deallocation
- Unique agent identification

### ActorSpawner
Controls actor lifecycle with:
- Thread pool management (virtual vs platform)
- Memory threshold enforcement
- Graceful shutdown mechanisms
- Performance metrics collection

## Memory Management

### Key Constants
```java
private static final long MB = 1024 * 1024;
private static final double WARNING_HEAP_PERCENTAGE = 0.85;  // 85%
private static final double CRITICAL_HEAP_PERCENTAGE = 0.95; // 95%
private static final long ACTOR_MEMORY_SIZE = 10 * MB;        // 10MB per actor
```

### Monitoring Strategy
1. **Pre-spawn**: Calculate safe memory threshold
2. **During-spawn**: Monitor heap usage continuously
3. **Critical threshold**: Stop spawning at 95% usage
4. **Post-shutdown**: Verify memory recovery
5. **Leak detection**: Track growth across cycles

## Usage

### Running Tests
```bash
# Run all memory exhaustion tests
mvn test -Dtest=MemoryExhaustionTest

# Run specific test
mvn test -Dtest=MemoryExhaustionTest#testVirtualThreadsWithDefaultHeap

# Run with verbose output
mvn test -Dtest=MemoryExhaustionTest -Dmaven.test.failure.ignore=true
```

### Test Output
Tests log detailed information:
- Actor spawn counts
- Memory usage in MB
- Heap usage percentages
- Performance metrics
- Leak detection results

## Integration with YAWL

### Virtual Threads
The test leverages Java 25 virtual threads:
```java
ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
```

### YAWL Agent Pattern
Test agents simulate YAWL agent behavior:
- Memory consumption patterns
- Concurrent task execution
- Resource cleanup protocols

### Metrics Collection
Compatible with YAWL observability:
- Memory usage metrics
- Actor spawn rates
- Performance benchmarks
- Leak detection analysis

## Best Practices

### Memory Safety
- **Never reach OOM** - Tests stop at 95% heap usage
- **Graceful shutdown** - Proper cleanup of all resources
- **Garbage collection** - Explicit GC calls for leak detection
- **Memory bounds** - Capped actor memory allocation

### Performance Considerations
- **Thread pool sizing** - Dynamic based on available processors
- **Spawn rate control** - Delayed spawning to monitor memory
- **Timeout handling** - 5-minute maximum spawn duration
- **Resource cleanup** - Proper executor shutdown

### Monitoring Integration
- **MemoryMXBean** - Standard JVM memory monitoring
- **Custom thresholds** - Configurable warning/critical levels
- **Real-time reporting** - Detailed memory usage logs
- **Trend analysis** - Multi-cycle leak detection

## Configuration

### Environment Variables
- `ZAI_API_KEY` - Optional: Enables real ZAI agent testing

### JVM Parameters
For optimal testing performance:
```bash
# Set heap size for testing
java -Xmx2g -Xms1g -XX:+UseG1GC ...

# Enable verbose GC for debugging
java -Xmx2g -Xms1g -XX:+PrintGCDetails ...
```

## Reporting

### Test Results
Each test reports:
- Maximum sustainable actor count
- Memory usage statistics
- Performance metrics
- Leak detection results

### Critical Metrics
- `heapUsagePercentage` - Current heap utilization
- `maxConcurrentActors` - Peak concurrent actor count
- `totalMemoryUsed` - Cumulative memory consumption
- `spawnDuration` - Time to spawn target actors

## Troubleshooting

### Common Issues
1. **Test timeout** - Increase timeout in `spawnLatch.await()`
2. **Memory insufficient** - Increase JVM heap size
3. **Thread limits** - Check virtual thread configuration
4. **GC interference** - Use `-XX:+DisableExplicitGC` for cleaner tests

### Debug Mode
Enable debug logging:
```java
Logger logger = LoggerFactory.getLogger(MemoryExhaustionTest.class);
logger.setLevel(Level.DEBUG);
```

## Safety Features

### OOM Prevention
- **Early termination** at 95% heap usage
- **Memory monitoring** every 5ms during spawning
- **Graceful degradation** when limits approached
- **Automatic cleanup** on test completion

### Resource Management
- **Thread pool shutdown** - Proper executor termination
- **Memory deallocation** - Explicit agent cleanup
- **File handles** - No file operations in test
- **Network connections** - No external dependencies

This test suite provides comprehensive validation of YAWL actor spawn limits while maintaining safety guarantees against actual memory exhaustion.