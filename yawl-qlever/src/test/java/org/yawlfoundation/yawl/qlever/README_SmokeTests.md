# QLever Integration Smoke Tests

## Overview

The smoke tests in `QLeverSmokeTest.java` verify that the basic functionality of the QLever integration works correctly and provides performance metrics to ensure the integration is operating properly.

## Smoke Tests Purpose

Smoke tests are the first line of defense against integration issues. They:

1. **Verify Basic Functionality** - Confirm that the most critical features work
2. **Performance Benchmarking** - Measure response times and throughput
3. **Error Detection** - Catch major issues before deeper testing
4. **CI Integration** - Fast tests that can run in CI/CD pipelines

## Test Coverage

The smoke tests cover:

### Core Tests (8)
1. **Library Loads Successfully** - Engine initialization and startup
2. **Index Creation from Test Fixture** - Data loading and index creation
3. **Simple SELECT Query Returns Results** - Most common query type
4. **Simple CONSTRUCT Query Returns Triples** - Graph query functionality
5. **Engine Reports Available** - Status reporting and configuration
6. **Close and Re-open Works** - Engine restart capability
7. **Multiple Sequential Queries Work** - Consistent query handling
8. **All Media Types Produce Output** - Multiple output format support

### Performance Metrics (9 real numbers)
- **Load Time in milliseconds** - Engine startup time
- **First Query Latency** - Initial query response time
- **Sequential Query Average Latency** - Average of multiple queries

## Running Smoke Tests

### Command Line
```bash
# Run all smoke tests
mvn test -Dtest=QLeverSmokeTest

# Run with verbose output
mvn test -Dtest=QLeverSmokeTest -Dmaven.test.failure.ignore=true

# Run specific test method
mvn test -Dtest=QLeverSmokeTest#testLibraryLoadsSuccessfully
```

### IDE
- Run as JUnit test in your IDE (IntelliJ, Eclipse, etc.)
- The `@Tag("smoke")` allows filtering test runs

## Performance Thresholds

The smoke tests include performance assertions with reasonable thresholds:

| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| Load Time | < 10 seconds | Engine should start quickly |
| First Query | < 2 seconds | Initial query should be responsive |
| Sequential Query Avg | < 1 second | Subsequent queries should be fast |
| Individual Query | < 3 seconds | No single query should time out |

## Test Data

Tests use the `smoke-test-data.ttl` resource which contains:
- 2 Work Items
- 2 Cases
- 3 Users
- Basic YAWL schema and properties

This provides enough data to test queries without being too heavy.

## Integration with CI/CD

### GitHub Actions
```yaml
- name: Run Smoke Tests
  run: mvn test -Dtest=QLeverSmokeTest
```

### Jenkins
```groovy
stage('Smoke Tests') {
    steps {
        sh 'mvn test -Dtest=QLeverSmokeTest'
    }
}
```

### Performance Monitoring
Smoke test results can be used to:
- Detect performance regressions
- Monitor system health over time
- Set alert thresholds for production

## Adding New Smoke Tests

When adding new smoke tests, follow these guidelines:

1. **Keep them fast** - Should complete in < 30 seconds
2. **Test critical paths** - Focus on the most important functionality
3. **Include performance metrics** - Measure relevant times
4. **Use real assertions** - Not just null checks
5. **Tag with @Tag("smoke")** - For easy filtering
6. **Clean up resources** - Ensure proper cleanup in @AfterEach

## Troubleshooting

If smoke tests fail:

1. **Check Environment**
   - Ensure QLever dependencies are available
   - Verify Java version compatibility
   - Check disk space for temporary files

2. **Review Logs**
   - Look at test output for error messages
   - Check SLF4J logs for debug information

3. **Performance Issues**
   - Monitor CPU/memory usage
   - Check for I/O bottlenecks
   - Verify network connectivity if using remote resources

4. **Data Issues**
   - Verify test data files are accessible
   - Check file permissions
   - Ensure data format is correct

## Maintenance

- Update performance thresholds as system evolves
- Add new tests when critical functionality changes
- Remove obsolete tests when features are deprecated
- Monitor test execution times in CI/CD

## Related Documentation

- [QLever Integration Examples](QLeverExamplesTest.java)
- [QLever Integration Tests](QLeverIntegrationTest.java)
- [QLever Test Utilities](QLeverTestUtils.java)