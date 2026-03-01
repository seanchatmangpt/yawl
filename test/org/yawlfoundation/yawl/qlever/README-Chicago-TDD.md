# Chicago TDD Tests for QLeverFfiBindings

## Overview

This directory contains Chicago TDD (Test-Driven Development) tests for the `QLeverFfiBindings` class. The tests implement the Chicago School TDD methodology with real integrations and comprehensive coverage.

## Test Suite

### Main Test File
- `QLeverFfiBindingsChicagoTest.java` - Main Chicago TDD test suite
- `QLeverTestUtils.java` - Utility methods for test setup and validation

### Test Coverage

The test suite covers all public methods in `QLeverFfiBindings`:

| Method | Coverage Areas | Test Cases |
|--------|---------------|------------|
| `indexCreate` | Valid path, null path, invalid path, permission denied | 5 test cases |
| `indexDestroy` | Normal, null handle, double-destroy | 3 test cases |
| `indexIsLoaded` | After create, after destroy, null handle | 3 test cases |
| `indexTripleCount` | Empty index, populated index, invalid handle | 3 test cases |
| `queryExec` | Valid query, malformed query, null parameters | 5 test cases |
| `resultHasNext/resultNext` | Iteration, empty result, null handle | 4 test cases |
| `resultDestroy` | Normal, null handle | 2 test cases |
| `resultError` | With error, without error, null handle | 4 test cases |

### Test Structure

The tests are organized in nested classes by functionality:

```java
QLeverFfiBindingsChicagoTest.java
├── IndexCreateTests          - indexCreate operations
├── IndexDestroyTests         - indexDestroy operations
├── IndexIsLoadedTests        - indexIsLoaded operations
├── IndexTripleCountTests     - indexTripleCount operations
├── QueryExecTests            - queryExec operations
├── ResultIterationTests      - resultHasNext/resultNext operations
├── ResultDestroyTests        - resultDestroy operations
├── ResultErrorTests          - resultError operations
├── PerformanceResourceTests  - Performance and resource management
└── IntegrationTests          - End-to-end workflows
```

## Running the Tests

### From YAWL Root Directory
```bash
cd /Users/sac/yawl
mvn test -Dtest=QLeverFfiBindingsChicagoTest -pl yawl-qlever -q
```

### From YAWL-Qlever Directory
```bash
cd /Users/sac/yawl/yawl-qlever
mvn test -Dtest=QLeverFfiBindingsChicagoTest -q
```

### Running Specific Test Methods
```bash
mvn test -Dtest=QLeverFfiBindingsChicagoTest#indexCreateValidPath -pl yawl-qlever -q
mvn test -Dtest=QLeverFfiBindingsChicagoTest#resultIterationSuccess -pl yawl-qlever -q
```

## Test Philosophy

### Chicago TDD Principles

1. **Test Real Objects**: All tests interact with actual QLever native library, not mocks
2. **Red-Green-Refactor**: Full test-driven cycle with real feedback
3. **Chicago School Approach**: Tests define expected behavior first, then implementation
4. **Real Performance Assertions**: Timing measurements based on actual native library performance
5. **Resource Management**: Comprehensive cleanup of all native resources

### Key Features

- **Real Integrations**: Tests use actual QLever native library calls
- **Performance Assertions**: All operations include timing measurements in nanoseconds
- **Error Coverage**: Comprehensive testing of error conditions and edge cases
- **Memory Safety**: Validation of memory limits and handle management
- **Resource Cleanup**: Proper verification of resource management

### Sample Test Data

Tests use sample SPARQL data:
- **Triples**: 4 sample triples with various data types
- **Schema**: Simple RDF schema with prefixes and class definitions
- **Directory Structure**: Clean test directory creation and cleanup

## Test Utilities

### QLeverTestUtils

Provides helper methods for test setup and validation:

- `setupTestEnvironment()` - Creates test directory and sample data
- `createTestIndex()` - Creates a test index with sample data
- `assertOperationWithinBounds()` - Verifies performance thresholds
- `assertValidSparqlJson()` - Validates JSON result structure
- `assertErrorContains()` - Validates error messages

### Performance Thresholds

| Operation | Threshold (µs) | Description |
|----------|---------------|-------------|
| indexCreate | 1000 | Index directory scanning |
| indexDestroy | 500 | Handle cleanup |
| indexIsLoaded | 100 | Simple validity check |
| indexTripleCount | 200 | Metadata access |
| queryExec | 50000 | Query execution |
| resultHasNext | 100 | Cursor advancement |
| resultNext | 200 | Data retrieval |
| resultDestroy | 500 | Result cleanup |

## Prerequisites

1. **Native Library**: QLever FFI library must be available
2. **Java 21+**: Required for Foreign Function & Memory API
3. **Maven**: Maven 3.8+ for build and test execution

## Environment Setup

The tests will automatically skip if the native library is not available. To run the tests:

1. Ensure the QLever FFI library is installed in your system library path
2. Set the `java.library.path` system property if needed
3. Run the tests using Maven

## Troubleshooting

### Common Issues

1. **Native Library Not Found**
   ```
   Solution: Ensure libqlever_ffi.so/dylib/dll is in java.library.path
   ```

2. **Test Failures**
   ```
   Solution: Run with verbose output to see specific failure reasons
   ```

3. **Performance Issues**
   ```
   Solution: Adjust thresholds based on your system performance
   ```

### Debug Commands

```bash
# Run with verbose output
mvn test -Dtest=QLeverFfiBindingsChicagoTest -pl yawl-qlever -X

# Check native library paths
echo $LD_LIBRARY_PATH
echo $DYLD_LIBRARY_PATH
echo $java.library.path
```

## Documentation

### Detailed Documentation
- See `README-ChicagoTDD.md` for comprehensive documentation
- Contains implementation details, best practices, and troubleshooting

### API Documentation
- Javadoc comments in `QLeverFfiBindings.java`
- Test method documentation for usage examples

## Contributing

### Adding New Tests

1. Follow the Chicago TDD methodology
2. Add tests to appropriate nested class
3. Include performance assertions for new operations
4. Verify resource cleanup
5. Update documentation

### Test Best Practices

1. **Test First**: Write tests before implementation
2. **Real Integration**: Use actual native library calls
3. **Performance**: Include meaningful timing assertions
4. **Coverage**: Test both happy paths and error conditions
5. **Cleanup**: Ensure proper resource management

## Continuous Integration

### CI Configuration
Tests can be integrated into CI pipelines with:
- Performance baseline tracking
- Code coverage reporting
- Native library availability checks
- Test execution time monitoring

### Quality Gates
- Test Coverage: 80%+ line coverage
- Performance: All operations within timing thresholds
- Resource Cleanup: No memory leaks
- Error Handling: All edge cases covered