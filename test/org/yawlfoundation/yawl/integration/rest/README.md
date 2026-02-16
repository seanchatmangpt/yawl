# YAWL REST API Integration Tests

Comprehensive integration test suite for YAWL REST API (Interface B).

## Overview

This test suite validates YAWL REST API operations using real YAWL Engine instances (Chicago TDD approach).

**Key Characteristics:**
- Real integrations - no mocks
- Full session lifecycle testing
- Error handling and edge cases
- Concurrent operation scenarios
- Security boundary testing

## Test Structure

```
test/org/yawlfoundation/yawl/integration/rest/
├── RestApiIntegrationTest.java          # Core session and connection tests
├── RestResourceCaseManagementTest.java  # Case operations (launch, query, cancel)
├── RestResourceWorkItemTest.java        # Work item operations (get, start, complete)
├── RestResourceErrorHandlingTest.java   # Error handling and edge cases
└── RestIntegrationTestSuite.java        # Test suite runner
```

## Test Coverage

### RestApiIntegrationTest (11 tests)
Core API operations:
1. `testConnect` - Valid user authentication
2. `testConnectWithInvalidCredentials` - Credential validation
3. `testConnectWithMissingCredentials` - Null/missing parameter handling
4. `testGetLiveWorkItems` - Work item retrieval
5. `testCheckValidSession` - Session validity verification
6. `testCheckInvalidSession` - Invalid session rejection
7. `testDisconnect` - Session cleanup
8. `testDisconnectInvalidSession` - Invalid session disconnect
9. `testMultipleConcurrentSessions` - Concurrent session support
10. `testEngineStateConsistency` - System state integrity
11. `testSessionWithTimeout` - Session timeout handling

### RestResourceCaseManagementTest (10 tests)
Case operations:
1. `testGetCasesForSpecification` - Query cases by spec ID
2. `testGetAllRunningCases` - Retrieve all active cases
3. `testGetCaseData` - Case data retrieval
4. `testCancelNonExistentCase` - Cancel invalid case (graceful)
5. `testCaseOperationsWithInvalidSession` - Security validation
6. `testGetCaseMetadata` - Case metadata retrieval
7. `testGetCasesByStatus` - Case filtering by status
8. `testLaunchCaseWithoutSpecification` - Invalid case launch
9. `testConcurrentCaseOperations` - Concurrent case queries
10. `testCaseOperationResilience` - System stability after errors

### RestResourceWorkItemTest (14 tests)
Work item operations:
1. `testGetLiveWorkItems` - Live work item retrieval
2. `testGetWorkItemsForCase` - Work items by case ID
3. `testGetWorkItemsForTask` - Work items by task
4. `testGetWorkItemById` - Specific work item retrieval
5. `testStartWorkItem` - Work item checkout
6. `testUpdateWorkItemData` - Data update
7. `testCompleteWorkItem` - Work item completion
8. `testWorkItemOperationsWithInvalidSession` - Security
9. `testGetWorkItemsForSpecification` - Work items by spec
10. `testConcurrentWorkItemQueries` - Concurrent operations
11. `testWorkItemDataWithComplexXml` - Complex XML handling
12. `testWorkItemOperationResilience` - Error recovery
13. `testEmptyWorkItemListHandling` - Empty result handling
14. `testWorkItemStateTransitions` - State management

### RestResourceErrorHandlingTest (18 tests)
Error handling and edge cases:
1. `testNullSessionHandleHandling` - Null session handling
2. `testEmptySessionHandleHandling` - Empty session handling
3. `testExtremelyLongSessionHandle` - Resource exhaustion prevention
4. `testSpecialCharactersInCredentials` - SQL injection prevention
5. `testNullUsernameConnection` - Null username
6. `testNullPasswordConnection` - Null password
7. `testNullCaseIdHandling` - Null case ID
8. `testEmptyCaseIdHandling` - Empty case ID
9. `testNullWorkItemIdHandling` - Null work item ID
10. `testNullXmlDataHandling` - Null XML data
11. `testMalformedXmlHandling` - Malformed XML detection
12. `testLargeXmlDataHandling` - Large payload handling
13. `testRapidFireRequests` - Rate limiting check
14. `testConcurrentErrorScenarios` - Concurrent error handling
15. `testSystemRecoveryAfterError` - Recovery capability
16. `testErrorIsolationBetweenSessions` - Session isolation
17. `testArithmeticErrorPrevention` - Calculation safety
18. `testResourceCleanupOnError` - Resource leak prevention

**Total Coverage: 53 integration tests**

## Running Tests

### Run All Integration Tests
```bash
# Run from project root
ant unitTest

# Or run specific suite
ant -f build/build.xml test -Dtest.class=org.yawlfoundation.yawl.integration.rest.RestIntegrationTestSuite
```

### Run Specific Test Class
```bash
java -cp classes:lib/* junit.textui.TestRunner \
  org.yawlfoundation.yawl.integration.rest.RestApiIntegrationTest
```

### Run Individual Test
```bash
# Using JUnit 5 with Gradle or Maven (if configured)
./gradlew test --tests RestApiIntegrationTest.testConnect

# Or with Ant (custom configuration)
ant test -Dtest.method=testConnect
```

### Smoke Test (No Running Engine Required)
```bash
# Basic API health check
./scripts/smoke-test.sh

# With custom engine URL
./scripts/smoke-test.sh --engine-url http://myhost:8080/yawl/api
```

## Test Methodology

### Chicago TDD (Detroit School)

Tests use real YAWL objects and integrations:

```java
// Real integration - NOT a mock
private YEngine engine;
private String sessionHandle;

@BeforeEach
public void setUp() throws Exception {
    engine = YEngine.getInstance();
    sessionHandle = engine.connect(ADMIN_USER, ADMIN_PASSWORD);
}

@Test
public void testRealOperation() {
    // Actual operation, not mocked
    Object result = engine.getLiveWorkItems(sessionHandle);
    assertNotNull(result);
}
```

### Key Principles

1. **No Mocks** - Tests interact with real YEngine instance
2. **Full Lifecycle** - setUp/tearDown for complete isolation
3. **Real Data** - Tests use actual XML and engine state
4. **Error Paths** - Tests verify both success and failure paths
5. **Concurrency** - Tests verify thread safety

## Test Fixtures

### Session Fixture
```java
@BeforeEach
void setUp() throws Exception {
    engine = YEngine.getInstance();
    sessionHandle = engine.connect(ADMIN_USER, ADMIN_PASSWORD);
}

@AfterEach
void tearDown() throws Exception {
    if (sessionHandle != null) {
        engine.disconnect(sessionHandle);
    }
}
```

### Isolation Strategy
- Each test gets a fresh session
- Sessions are disconnected after each test
- No shared state between tests
- Concurrent sessions use separate handles

## API Endpoints Tested

### Session Management
- `POST /ib/connect` - Authenticate and get session
- `POST /ib/disconnect` - End session

### Work Items
- `GET /ib/workitems` - Get all live work items
- `GET /ib/workitems/{itemId}` - Get specific work item
- `GET /ib/cases/{caseId}/workitems` - Get work items for case
- `POST /ib/workitems/{itemId}/checkout` - Start work item
- `POST /ib/workitems/{itemId}/checkin` - Update data
- `POST /ib/workitems/{itemId}/complete` - Complete work item

### Cases
- `GET /ib/cases` - Get all running cases
- `GET /ib/cases/{caseId}/data` - Get case data
- `POST /ib/cases/{caseId}/cancel` - Cancel case

### Queries
- `GET /ib/cases?spec={specId}` - Cases by specification
- `GET /ib/tasks/{taskName}/workitems` - Work items by task
- `GET /ib/specifications/{specId}/workitems` - Work items by spec

## Error Handling Tests

Tests verify proper handling of:
- **Authentication**: Invalid credentials, null credentials
- **Session**: Invalid/expired sessions, concurrent sessions
- **Input Validation**: Null IDs, empty strings, SQL injection attempts
- **Resource Exhaustion**: Large payloads, rapid-fire requests
- **Data Format**: Malformed XML, special characters
- **Concurrency**: Race conditions, deadlocks
- **Recovery**: System stability after errors

## Coverage Goals

Target coverage for REST API integration:
- **Line Coverage**: 80%+
- **Branch Coverage**: 75%+
- **Critical Paths**: 100% (authentication, case execution)
- **Error Paths**: 85%+

## Configuration

### Default Credentials
```
Username: admin
Password: YAWL
```

### Test Timeouts
- Per-test timeout: 30 seconds
- Suite timeout: 10 minutes
- Concurrent test timeout: 60 seconds

### Resource Limits
- Session handle length: 10,000+ characters tested
- XML payload size: 1MB+ tested
- Concurrent sessions: 100+ tested
- Rapid requests: 1000+ per second tested

## Dependencies

Required libraries:
- JUnit 5 (Jupiter)
- Jackson (JSON processing)
- YAWL Engine (version 5.2)
- H2 (in-memory database for tests)

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>5.9.0</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.14.0</version>
    <scope>test</scope>
</dependency>
```

## Maintenance

### Adding New Tests

1. Create test in appropriate class:
   - `RestApiIntegrationTest` for core API
   - `RestResourceCaseManagementTest` for case operations
   - `RestResourceWorkItemTest` for work items
   - `RestResourceErrorHandlingTest` for error scenarios

2. Follow naming convention: `test<Operation><Scenario>`

3. Use @Order annotation for test sequencing

4. Always implement setUp/tearDown for isolation

### Updating Test Suite

To add new test class:

1. Create test class with @TestMethodOrder annotation
2. Add to RestIntegrationTestSuite:
   ```java
   @SelectClasses({
       RestApiIntegrationTest.class,
       MyNewTest.class,  // Add here
   })
   ```

## Troubleshooting

### Tests Fail with "Engine not running"
- Tests skip gracefully when engine unavailable
- Run `ant buildAll` first to initialize engine
- Check port 8080 is accessible

### Session Connection Timeout
- Increase timeout in test configuration
- Verify YAWL engine is started: `ant run`
- Check database is initialized

### Memory Issues with Large Tests
- Increase JVM heap: `-Xmx512m`
- Reduce concurrent test count
- Run error handling tests separately

## See Also

- [YAWL Architecture](../../README.md)
- [Engine Design](../engine/)
- [Interface B Documentation](../engine/interfce/)
- [CLAUDE.md](../../../../CLAUDE.md) - Project standards
