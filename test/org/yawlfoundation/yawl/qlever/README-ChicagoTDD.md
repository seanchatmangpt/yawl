# Chicago TDD Tests for QLeverFfiBindings

## Overview

This document describes the Chicago TDD (Test-Driven Development) test suite for the QLeverFfiBindings class. The tests implement the Chicago School TDD methodology with real integrations and comprehensive coverage.

## Test Philosophy

### Chicago TDD Principles Applied

1. **Test Real Objects**: All tests interact with actual QLever native library, not mocks
2. **Red-Green-Refactor**: Full test-driven cycle with real feedback
3. **Chicago School Approach**: Tests define expected behavior first, then implementation
4. **Real Performance Assertions**: Timing measurements based on actual native library performance
5. **Resource Management**: Comprehensive cleanup of all native resources

### Test Coverage Targets

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

### Test Suite Structure

```
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

### Prerequisites

1. **Native Library**: QLever FFI library must be available in the Java library path
2. **Java 21+**: Requires Java 21 or later with Foreign Function & Memory API
3. **Maven**: Maven 3.8+ for build and test execution

### Test Execution

```bash
# Build and run all Chicago TDD tests
./scripts/run-chicago-tests.sh

# Verify test compilation and configuration
./scripts/verify-chicago-tests.sh

# Run specific test method
mvn test -Dtest=QLeverFfiBindingsChicagoTest#indexCreateValidPath

# Run with verbose output
mvn test -Dtest=QLeverFfiBindingsChicagoTest -Dmaven.test.failure.ignore=true -X

# Generate coverage report
mvn clean test jacoco:report
```

### Test Configuration

```java
// Performance thresholds (in microseconds)
INDEX_CREATE_THRESHOLD = 1000;      // 1ms for index creation
INDEX_DESTROY_THRESHOLD = 500;       // 500µs for index destruction
TRIPLE_COUNT_THRESHOLD = 200;       // 200µs for triple count
QUERY_EXEC_THRESHOLD = 50000;      // 50ms for query execution
RESULT_ITERATION_THRESHOLD = 10000; // 10ms for result iteration
ERROR_RETRIEVAL_THRESHOLD = 100;    // 100µs for error retrieval
```

## Test Implementation Details

### 1. Index Operations Testing

#### indexCreate
- **Valid Path**: Creates index and verifies it's loaded
- **Null Path**: Verifies NullPointerException
- **Invalid Path**: Returns error status with HTTP 500
- **Permission Denied**: Creates read-only directory and tests failure

```java
@Test
@DisplayName("✅ indexCreate - Valid path creates index successfully")
void indexCreateValidPath() throws IOException {
    // Arrange
    createTestFiles();

    // Act & Measure Performance
    long startTime = System.nanoTime();
    QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
    long endTime = System.nanoTime();

    // Assert
    long durationMicros = TimeUnit.MICROSECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
    assertTrue(durationMicros < 1000, "Index creation took too long: " + durationMicros + "µs");
    assertTrue(status.isSuccess());
}
```

#### indexDestroy
- **Normal Destroy**: Cleans up and verifies no longer loaded
- **Null Handle**: Safe to call with null
- **Double Destroy**: Idempotent operation

### 2. Query Execution Testing

#### queryExec
- **Valid Query**: Executes successfully and returns result handle
- **Malformed Query**: Returns error status with HTTP 400/500
- **Null Parameters**: Verifies NullPointerException for each parameter

```java
@Test
@DisplayName("❌ queryExec - Null index throws NullPointerException")
void queryExecNullIndex() {
    // Arrange
    String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";

    // Act & Assert
    assertThrows(NullPointerException.class,
                () -> bindings.queryExec(null, query, QLeverMediaType.JSON),
                "queryExec should throw NullPointerException for null index");
}
```

### 3. Result Handling Testing

#### Result Iteration
- **Valid Iteration**: Processes all results and verifies count
- **Empty Result**: Handles empty result sets correctly
- **Null Handle**: Returns false/null for null handles

```java
@Test
@DisplayName("✅ resultHasNext/resultNext - Iterates through results successfully")
void resultIterationSuccess() throws IOException {
    // Arrange
    createTestFilesWithContent();
    QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
    QLeverStatus queryStatus = bindings.queryExec(status.result(),
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);

    // Act
    int count = 0;
    while (bindings.resultHasNext(queryStatus.result())) {
        String result = bindings.resultNext(queryStatus.result());
        assertNotNull(result);
        count++;
    }

    // Assert
    assertEquals(2, count, "Should have processed exactly 2 results");
}
```

### 4. Error Handling Testing

#### resultError
- **With Error**: Returns error message for failed queries
- **Without Error**: Returns null for successful queries
- **Null Handle**: Returns appropriate error message

## Performance Assertions

### Timing Measurements

All operations include performance assertions with real timing:

```java
// Example performance measurement
long startTime = System.nanoTime();
// Operation being tested
long endTime = System.nanoTime();
long durationMicros = TimeUnit.MICROSECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
assertTrue(durationMicros < THRESHOLD, "Operation took too long: " + durationMicros + "µs");
```

### Performance Thresholds

| Operation | Threshold | Justification |
|----------|-----------|---------------|
| indexCreate | 1000µs | Index directory scanning and validation |
| indexDestroy | 500µs | Handle cleanup and resource release |
| indexIsLoaded | 100µs | Simple handle validity check |
| indexTripleCount | 200µs | Metadata access from index |
| queryExec | 50000µs | Query parsing and execution |
| resultHasNext | 100µs | Simple cursor advancement |
| resultNext | 200µs | Memory allocation and data copy |
| resultDestroy | 500µs | Result cleanup |

### Memory Safety

- **Line Length**: Results validated to be < 64KB (MAX_LINE_LENGTH)
- **Error Length**: Errors validated to be < 16KB (MAX_ERROR_LENGTH)
- **Handle Validation**: All handles checked for null/NULL before use

## Test Data

### Sample Test Data

```turtle
# Sample triples (4 triples)
<http://example.org/s1> <http://example.org/p1> <http://example.org/o1> .
<http://example.org/s1> <http://example.org/p2> "object value 1" .
<http://example.org/s2> <http://example.org/p1> <http://example.org/o2> .
<http://example.org/s2> <http://example.org/p2> "object value 2" .

# Sample schema
@prefix : <http://example.org/> .
:s1 a :Type1 .
:s2 a :Type2 .
:s1 :p1 ?o1 .
:s1 :p2 ?o2 .
:s2 :p1 ?o3 .
:s2 :p2 ?o4 .
```

### Test Directory Structure

```
test_qlever_index/
├── triples.nq    # N-Quads triple data
└── schema.nq    # Schema definitions
```

## Integration Tests

### End-to-End Workflows

1. **Complete Workflow**: Index creation → Query execution → Result iteration → Cleanup
2. **Concurrent Operations**: Multiple queries on same index handle
3. **Resource Management**: Proper cleanup verification

### Scenario Testing

- **Empty Index**: Behavior with no data
- **Populated Index**: Behavior with sample data
- **Error Scenarios**: Malformed queries, invalid paths
- **Edge Cases**: Null handles, double destruction, permission issues

## Test Best Practices

### Chicago TDD Implementation

1. **Test First**: Write tests before implementation
2. **Red-Green-Refactor**: Full cycle for each feature
3. **Real Integration**: Use actual native library, not mocks
4. **Performance**: Real timing assertions based on actual performance
5. **Resource Cleanup**: Verify proper resource management

### Test Organization

- **Nested Classes**: Related tests grouped by functionality
- **Clear Naming**: Descriptive test names with emojis for visual distinction
- **Setup/Teardown**: Consistent initialization and cleanup
- **Assumptions**: Skip tests if native library unavailable

### Assertions

- **Performance**: Timing assertions with meaningful thresholds
- **Resource Validation**: Handle validation and cleanup verification
- **Error Handling**: Proper error message validation
- **Memory Safety**: Size limits and boundary conditions

## Troubleshooting

### Common Issues

1. **Native Library Not Found**
   ```
   Solution: Ensure libqlever_ffi.so/dylib/dll is in java.library.path
   ```

2. **Test Failures**
   ```
   Solution: Run with verbose output: mvn test -X
   Check test logs for specific failure reasons
   ```

3. **Performance Issues**
   ```
   Solution: Adjust thresholds based on your system
   Check system load and resource availability
   ```

### Debug Commands

```bash
# Check native library paths
echo $LD_LIBRARY_PATH
echo $DYLD_LIBRARY_PATH
echo $java.library.path

# Test individual operations
mvn test -Dtest=QLeverFfiBindingsChicagoTest#indexCreateValidPath -DfailIfNoTests=false

# Generate detailed reports
mvn surefire-report:report-only
mvn site
```

## Continuous Integration

### CI Configuration

```yaml
# Example GitHub Actions configuration
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '21'
      - name: Run tests
        run: ./scripts/run-chicago-tests.sh
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

### Quality Gates

- **Test Coverage**: 80%+ line coverage
- **Performance**: All operations within timing thresholds
- **Resource Cleanup**: No memory leaks or dangling handles
- **Error Handling**: All edge cases covered

## Documentation

### API Documentation

- Javadoc comments for all public methods
- Usage examples in test methods
- Performance expectations documented

### Test Data

- Sample data files committed to repository
- Data schema documented
- Test data generation utilities provided

### Performance Baselines

- Timing measurements established for each operation
- System-specific thresholds documented
- Continuous performance monitoring enabled