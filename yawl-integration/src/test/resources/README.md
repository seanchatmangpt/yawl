# Chaos Engineering Tests for YAWL Java-Python Integration

This directory contains chaos engineering tests designed to validate the resilience of the YAWL Java-Python integration under various failure conditions.

## Overview

Chaos engineering is the discipline of experimenting on a system in order to build confidence in its capability to withstand turbulent conditions in production. These tests simulate real-world failure scenarios and verify that the system can recover, degrade gracefully, and maintain stability.

## Test Structure

```
test/org/yawlfoundation/yawl/integration/java_python/chaos/
├── ChaosEngineeringTest.java          # Main chaos engineering test class
└── README.md                        # This file

validation/
├── ValidationTestBase.java          # Abstract base class for validation tests
└── ResourceLoader.java               # Utility for loading test resources

resources/
├── chaos/
│   ├── workflows/                   # Workflow definitions for testing
│   │   └── ChaosTestWorkflow.yawl
│   ├── data/                        # Test data files
│   │   └── sample-chaos-data.json
│   └── chaos-config.properties       # Configuration for chaos tests
└── scripts/
    └── run_chaos_tests.sh           # Script to run all chaos tests
```

## Tests Included

### 1. Failure Recovery Test
- **Purpose**: Verify that the system can recover from transient failures
- **Scenario**: Simulates transient failures that occur randomly
- **Verification**: Ensures all work items are eventually processed despite failures

### 2. Circuit Breaker Test
- **Purpose**: Test circuit breaker behavior under persistent failures
- **Scenario**: Simulates persistent failures that exceed the threshold
- **Verification**: Verifies circuit breaker trips and recovers correctly

### 3. Graceful Degradation Test
- **Purpose**: Validate graceful degradation under heavy load
- **Scenario**: Simulates high request rate that exceeds capacity
- **Verification**: Ensures system remains stable while degrading service quality

### 4. Timeout Handling Test
- **Purpose**: Test timeout handling without resource leaks
- **Scenario**: Simulates operations that exceed timeout thresholds
- **Verification**: Verifies timeouts are handled gracefully without leaks

### 5. Resource Exhaustion Test
- **Purpose**: Test behavior under resource constraints
- **Scenario**: Simulates CPU and memory intensive operations
- **Verification**: Ensures system remains stable and recovers when resources free up

### 6. Chaos Monkey Test
- **Purpose**: Test resilience against random, unpredictable failures
- **Scenario**: Simulates random failures across different components
- **Verification**: Ensures system maintains stability despite chaos

### 7. Cascade Failure Prevention Test
- **Purpose**: Validate failure isolation and cascade prevention
- **Scenario**: Simulates multiple failures simultaneously
- **Verification**: Ensures failures don't cascade and system remains operational

## Running the Tests

### Using Maven
```bash
# Run all chaos tests
mvn test -Dtest=ChaosEngineeringTest

# Run specific test
mvn test -Dtest=ChaosEngineeringTest#testFailureRecovery
```

### Using the Test Runner Script
```bash
# Run all chaos tests with reporting
./test/scripts/run_chaos_tests.sh

# The script will:
# - Run all chaos tests with timeouts
# - Generate reports in target/chaos-reports/
# - Provide a summary of results
```

### Running with IDE
- Open the project in your IDE (IntelliJ, Eclipse, etc.)
- Run `ChaosEngineeringTest` as a JUnit 5 test
- Ensure JUnit 5 is configured in your project

## Configuration

### Chaos Configuration (`chaos-config.properties`)
```properties
# Circuit Breaker Settings
circuit.breaker.threshold=5
circuit.breaker.timeout.seconds=30

# Timeout Settings
timeout.default.ms=5000
timeout.max.ms=30000

# Resource Limits
resource.max.threads=100
resource.max.memory.mb=512

# Failure Injection Probabilities
failure.transient.probability=0.3
failure.persistent.probability=0.5
```

### Environment Variables
- `CHAOS_TEST_DURATION_SECONDS`: Duration of chaos tests (default: 60)
- `CHAOS_WORKLOAD_COUNT`: Number of work items to test (default: 100)
- `LOG_LEVEL`: Logging level (DEBUG, INFO, WARN, ERROR)

## Test Data

### Sample Chaos Data (`sample-chaos-data.json`)
Contains test cases with:
- Unique identifiers for tracking
- Payload data for processing
- Expected outcomes (success, recovery, timeout)
- Metadata for test configuration

### Workflow Definitions (`ChaosTestWorkflow.yawl`)
A comprehensive workflow used for chaos testing featuring:
- Multiple execution paths (success, failure, recovery)
- Complex branching logic
- Loop mechanisms for retry scenarios
- Proper constraints for validation

## Monitoring and Metrics

The tests include built-in monitoring for:
- Thread count and memory usage
- Circuit breaker trip counts
- Success/failure rates
- Timeout occurrences
- Resource utilization

### Monitoring Integration
```java
// Example monitoring in tests
logBaselineMetrics();        // Record before test
logFinalMetrics();          // Record after test
getActiveThreadCount();      // Check thread leaks
getMemoryLeakCount();        // Check memory leaks
```

## Best Practices

### When Writing Chaos Tests
1. **Start small**: Begin with simple failure scenarios
2. **Control the blast radius**: Limit the scope of failure injection
3. **Run in production-like environments**: Use similar configuration
4. **Measure everything**: Capture metrics before, during, and after
5. **Automate everything**: Include in CI/CD pipeline

### When Running Chaos Tests
1. **Schedule maintenance windows**: Avoid business-critical periods
2. **Notify stakeholders**: Inform teams about test activities
3. **Have rollback plans**: Be ready to abort if issues arise
4. **Monitor production**: Watch for real-world impact
5. **Document findings**: Capture lessons learned

## Troubleshooting

### Common Issues

#### Test Failures
- **Resource leaks**: Check thread and memory counts
- **Timeout errors**: Verify timeout configuration
- **Circuit breaker stuck**: Reset between tests

#### Environment Issues
- **Missing dependencies**: Ensure all required libraries are available
- **Port conflicts**: Check if services are already running
- **Permissions**: Verify file system access

### Debug Mode
Run tests with debug logging:
```bash
mvn test -Dtest=ChaosEngineeringTest -Dlogging.level.org.yawlfoundation.yawl.integration.java_python.chaos=DEBUG
```

## Future Enhancements

1. **More complex scenarios**: Add network partition tests
2. **Distributed chaos**: Test across multiple services
3. **Automated chaos pipeline**: Integrate with CI/CD
4. **Real-time dashboard**: Visualize chaos test results
5. **Historical analysis**: Track system resilience over time

## References

- [Chaos Engineering Principles](https://principlesofchaos.org/)
- [Chaos Monkey from Netflix](https://github.com Netflix/chaosmonkey)
- [Failure Injection Patterns](https://docs.microsoft.com/en-us/azure/architecture/patterns/category/failure-handling)
- [Resilience Engineering](https://resilience4j.readme.io/)

## Support

For issues or questions:
- Check the [YAWL documentation](https://yawl.sourceforge.net)
- Open an issue in the [YAWL GitHub repository](https://github.com/yawlfoundation/yawl)
- Contact the YAWL development team