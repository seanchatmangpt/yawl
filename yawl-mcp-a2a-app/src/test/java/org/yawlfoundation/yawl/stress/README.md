# YAWL Mailbox Overflow Stress Tests

This directory contains comprehensive stress test implementations for validating mailbox overflow behavior in the YAWL engine.

## Test Classes

### 1. MailboxOverflowStressTest.java
**Purpose**: Comprehensive stress testing of queue overflow scenarios using Java's built-in queue implementations.

**Key Features**:
- Tests both bounded (ArrayBlockingQueue) and unbounded (LinkedBlockingQueue) mailboxes
- Validates backpressure behavior when producers outpace consumers
- Measures queue overflow patterns and recovery behaviors
- Tests different queue capacities and load patterns

**Test Methods**:
- `testBoundedMailbackBlockingQueueOverflow()`: Tests blocking behavior when queue is full
- `testLinkedBlockingQueueOverflow()`: Tests unbounded queue memory growth
- `testDifferentCapacities()`: Tests various queue sizes under load
- `testSendVsSendBlocking()`: Compares non-blocking vs blocking send operations
- `testRecoveryAfterOverflow()`: Tests queue recovery after overflow conditions
- `testMemoryUsageDuringOverflow()`: Measures memory consumption patterns

### 2. AgentMailboxStressTest.java
**Purpose**: Stress tests specifically for the YAWL Agent mailbox implementation.

**Key Features**:
- Uses the same Agent mailbox patterns as the actual YAWL engine
- Tests both bounded and unbounded Agent configurations
- Validates concurrent access patterns
- Simulates realistic actor communication scenarios

**Test Methods**:
- `testBoundedAgentMailboxBlocking()`: Tests Agent with bounded mailbox
- `testUnboundedAgentMailbox()`: Tests Agent with unbounded mailbox
- `testAgentSendVsSendBlockingComparison()`: Compares send methods
- `testAgentConcurrentAccess()`: Tests multiple actors communicating
- `testAgentMailboxStressSpike()`: Tests sudden load spikes

### 3. StressTestMetrics.java
**Purpose**: Utility class for collecting and analyzing stress test metrics.

**Key Features**:
- Real-time metrics collection during stress tests
- Comprehensive reporting on throughput, latency, and backpressure
- Memory usage tracking
- Queue size monitoring
- Performance comparison utilities

**Classes**:
- `StressTestMetrics`: Main metrics collection class
- `StressTestMetrics.StressReport`: Comprehensive test report structure
- `StressTestMetrics.StressTestUtils`: Utility methods for running tests

### 4. MailboxOverflowIntegrationTest.java
**Purpose**: Integration tests combining multiple stress scenarios to validate realistic patterns.

**Key Features**:
- Producer-consumer backpressure scenarios
- Multi-actor communication patterns
- Spike load and recovery testing
- Queue type benchmarks
- Memory-aware stress testing

**Test Methods**:
- `testProducerConsumerBackpressureScenario()`: Classic producer-consumer pattern
- `testMultipleActorsCommunicationPattern()`: Multi-actor workflow simulation
- `testSpikeLoadRecoveryScenario()`: Load spike testing
- `testQueueComparisonBenchmark()`: Performance comparison
- `testMemoryAwareStressTest()`: Memory consumption validation

### 5. StressTestDemo.java
**Purpose**: Standalone demonstration of stress testing patterns.

**Key Features**:
- Runnable demonstration of core stress testing scenarios
- No external dependencies for easy execution
- Shows real-time queue behavior and backpressure
- Memory usage monitoring
- Performance metrics reporting

## Running the Tests

### Individual Test Classes
```bash
# Run specific test class
mvn test -Dtest=MailboxOverflowStressTest

# Run all stress tests
mvn test -Dtest="**/stress/*"
```

### Standalone Demo
```bash
# Compile and run the demo
javac StressTestDemo.java
java StressTestDemo
```

## Test Scenarios

### 1. Backpressure Validation
- **Bounded Queues**: Tests blocking behavior when full
- **Unbounded Queues**: Tests memory growth without blocking
- **Drop vs Block**: Compares `offer()` (drop) vs `put()` (block) behavior

### 2. Performance Metrics
- **Throughput**: Messages per second processed
- **Latency**: Average message processing time
- **Queue Utilization**: Peak and average queue sizes
- **Memory Usage**: Heap consumption patterns

### 3. Stress Patterns
- **Gradual Load**: Increasing load over time
- **Spike Load**: Sudden bursts of messages
- **Sustained Load**: High load for extended periods
- **Recovery**: Behavior after load removal

### 4. Configuration Testing
- **Different Capacities**: Small (10), medium (100), large (1000)
- **Variable Producer/Consumer Ratios**: 1:1, 2:1, 4:1, 4:2
- **Message Sizes**: Small (1B), medium (1KB), large (10KB)

## Expected Behaviors

### Bounded Mailboxes (ArrayBlockingQueue)
- **Send Operation**: `offer()` returns false when full, `put()` blocks
- **Backpressure**: Producers wait when queue is full
- **Memory**: Fixed maximum size based on capacity
- **Recovery**: Normal operation after consumers catch up

### Unbounded Mailboxes (LinkedBlockingQueue)
- **Send Operation**: Always succeeds, never blocks
- **Backpressure**: No backpressure, memory grows indefinitely
- **Memory**: Unbounded growth potential
- **Recovery**: Depends on consumer processing rate

### Critical Metrics to Monitor
1. **Dropped Messages**: Indicates capacity exceeded
2. **Blocked Messages**: Indicates backpressure active
3. **Queue Size**: Should not exceed capacity for bounded queues
4. **Memory Usage**: Should grow predictably
5. **Throughput**: Should stabilize under load

## Real-World Applications

These tests help validate:
1. **YAWL Engine Performance** under heavy message loads
2. **Backpressure Effectiveness** in preventing system overload
3. **Memory Management** in long-running workflows
4. **Recovery Capabilities** after extreme conditions
5. **Resource Contention** in multi-actor scenarios

## Integration with YAWL

The test patterns used here mirror the actual YAWL Agent implementation:
- `ArrayBlockingQueue` for bounded mailboxes with backpressure
- `LinkedTransferQueue` for unbounded mailboxes
- Matching send/receive semantics
- Same threading patterns for virtual threads

This ensures test results accurately reflect production behavior.