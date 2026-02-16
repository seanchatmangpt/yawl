# YAWL REST API Integration Testing Guide

## Quick Start

### Run All Tests
```bash
ant -f build/build.xml unitTest
```

### Run Smoke Tests (No Engine Required)
```bash
./scripts/smoke-test.sh
```

### Run Specific Integration Test Suite
```bash
java -cp classes:lib/* org.junit.platform.console.ConsoleLauncher \
  --scan-classpath-root classes \
  --select-class org.yawlfoundation.yawl.integration.rest.RestIntegrationTestSuite
```

## Test Files Created

### Integration Test Classes
1. **RestApiIntegrationTest.java** (11 tests)
   - Location: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/`
   - Focus: Core API operations (connect, disconnect, sessions)
   - Coverage: Session lifecycle, authentication, concurrency

2. **RestResourceCaseManagementTest.java** (10 tests)
   - Location: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/`
   - Focus: Case operations (launch, query, cancel)
   - Coverage: Case retrieval, filtering, error handling

3. **RestResourceWorkItemTest.java** (14 tests)
   - Location: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/`
   - Focus: Work item operations (get, start, complete)
   - Coverage: Item retrieval, state transitions, data management

4. **RestResourceErrorHandlingTest.java** (18 tests)
   - Location: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/`
   - Focus: Error scenarios and edge cases
   - Coverage: Null handling, injection prevention, recovery

5. **RestIntegrationTestSuite.java** (Test Suite)
   - Location: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/`
   - Orchestrates all REST API integration tests

### Documentation Files
1. **README.md** - Comprehensive test documentation
2. **TESTING_GUIDE.md** - This file

### Smoke Test Script
1. **smoke-test.sh** - Basic API health check without running engine
   - Location: `/home/user/yawl/scripts/`
   - Tests endpoint accessibility, auth, and basic operations
   - Gracefully skips when engine not running

## Test Coverage Summary

**Total Tests: 53 integration tests**

| Component | Tests | Coverage |
|-----------|-------|----------|
| Session Management | 11 | Connect, disconnect, concurrency |
| Case Management | 10 | Query, launch, cancel, filtering |
| Work Items | 14 | Retrieve, start, complete, state |
| Error Handling | 18 | Null, injection, recovery, resilience |

## Architecture

All tests follow **Chicago TDD** (Detroit School) methodology:

```
Real YAWL Engine
    ↓
YEngine Instance
    ↓
InterfaceBInterop Operations
    ↓
Test Assertions
```

No mocks. Real integrations. Real data flows.

### Key Features

1. **Session Isolation**
   - Each test gets fresh session
   - Session cleanup after each test
   - No shared state between tests

2. **Real Objects**
   - Actual YEngine instance
   - Real session handles
   - Actual XML data processing

3. **Error Scenarios**
   - Invalid credentials
   - Null parameters
   - Malformed data
   - Concurrent errors
   - Resource exhaustion

4. **Security Testing**
   - SQL injection prevention
   - Session validation
   - Authentication enforcement
   - Input validation

## Test Execution Flow

### RestApiIntegrationTest
```
setUp() → Create Session
  ├─ testConnect
  ├─ testConnectWithInvalidCredentials
  ├─ testGetLiveWorkItems
  ├─ ... (8 more tests)
  └─ testSessionWithTimeout
tearDown() → Disconnect Session
```

### RestResourceCaseManagementTest
```
setUp() → Create Session
  ├─ testGetCasesForSpecification
  ├─ testGetAllRunningCases
  ├─ testGetCaseData
  ├─ ... (7 more tests)
  └─ testCaseOperationResilience
tearDown() → Disconnect Session
```

### RestResourceWorkItemTest
```
setUp() → Create Session
  ├─ testGetLiveWorkItems
  ├─ testGetWorkItemsForCase
  ├─ testStartWorkItem
  ├─ ... (11 more tests)
  └─ testWorkItemStateTransitions
tearDown() → Disconnect Session
```

### RestResourceErrorHandlingTest
```
setUp() → Create Session
  ├─ testNullSessionHandleHandling
  ├─ testExtremelyLongSessionHandle
  ├─ testSpecialCharactersInCredentials
  ├─ ... (15 more tests)
  └─ testResourceCleanupOnError
tearDown() → Disconnect Session
```

## Key Test Scenarios

### 1. Authentication & Sessions
- Valid credentials → Success
- Invalid credentials → Rejection
- Null credentials → Graceful handling
- Session timeouts → Timeout enforcement
- Concurrent sessions → Independence maintained
- Session isolation → Errors don't leak between sessions

### 2. Case Operations
- Query by specification
- Query by status
- Case data retrieval
- Case cancellation
- Non-existent case handling

### 3. Work Items
- Live work item retrieval
- Work items by case
- Work items by task
- Work items by specification
- Item checkout/start
- Item data update
- Item completion
- Complex XML handling
- State transitions

### 4. Error Handling
- Null parameter handling
- Empty parameter handling
- Special character injection
- Malformed XML detection
- Large payload handling
- Rapid request handling
- Concurrent error scenarios
- Resource cleanup verification

## Running Specific Tests

### Run Single Test Class
```bash
java -cp classes:lib/* org.junit.platform.console.ConsoleLauncher \
  --select-class org.yawlfoundation.yawl.integration.rest.RestApiIntegrationTest
```

### Run Single Test Method
```bash
java -cp classes:lib/* org.junit.platform.console.ConsoleLauncher \
  --select-class org.yawlfoundation.yawl.integration.rest.RestApiIntegrationTest \
  --select-method testConnect
```

### Run with Maven/Gradle (if configured)
```bash
mvn test -Dtest=RestApiIntegrationTest
mvn test -Dtest=RestApiIntegrationTest#testConnect
```

### Run Smoke Tests
```bash
./scripts/smoke-test.sh
./scripts/smoke-test.sh --engine-url http://custom-host:8080/yawl/api
```

## Test Assertions

All tests use JUnit 5 assertions:

```java
assertNotNull(result)              // Verify not null
assertFalse(result.contains("fail")) // Verify success
assertTrue(engine.isRunning())      // Verify state
assertEquals(expected, actual)      // Verify equality
```

## Integration Points

### YAWL Engine Interface (InterfaceBInterop)

Tests interact with engine via:

```java
// Session Management
String sessionHandle = engine.connect(userid, password);
Object result = engine.disconnect(sessionHandle);

// Work Items
Object items = engine.getLiveWorkItems(sessionHandle);
Object result = engine.startWorkItem(itemId, sessionHandle);
Object result = engine.completeWorkItem(itemId, data, sessionHandle);

// Cases
Object cases = engine.getRunningCases(sessionHandle);
Object data = engine.getCaseData(caseId, sessionHandle);
Object result = engine.cancelCase(caseId, sessionHandle);
```

## Fixtures and Setup

### Test Isolation
Each test runs in isolation:

```java
@BeforeEach
public void setUp() throws Exception {
    engine = YEngine.getInstance();
    sessionHandle = engine.connect(ADMIN_USER, ADMIN_PASSWORD);
}

@AfterEach
public void tearDown() throws Exception {
    if (sessionHandle != null) {
        engine.disconnect(sessionHandle);
    }
}
```

### Credentials
- Default Username: `admin`
- Default Password: `YAWL`

## Performance Expectations

| Operation | Time | Notes |
|-----------|------|-------|
| Connect | <100ms | Fast authentication |
| Get work items | <50ms | Empty case typically |
| Get case data | <50ms | Depends on case data |
| Disconnect | <50ms | Fast cleanup |
| Suite run | <30s | All 53 tests |

## Troubleshooting

### Tests Skip with "Engine not running"
**Expected behavior** - Tests gracefully skip if YAWL engine not available
- Run: `ant -f build/build.xml buildAll`
- Then: `ant -f build/build.xml run` (starts engine)
- Finally: Run tests again

### Compilation Fails
Check:
1. Java version: `java -version` (should be Java 11+)
2. Classpath: `echo $CLASSPATH`
3. Dependencies: `ant -f build/build.xml buildAll`

### Tests Timeout
- Engine may be slow starting
- Increase timeout: `-Dtimeout=60000`
- Run one test class at a time

### Session Won't Disconnect
- Expected in error scenarios
- Tests verify cleanup still occurs
- Check logs for disconnect errors

## Coverage Metrics

Target Coverage:
- **Line Coverage**: 80%+
- **Branch Coverage**: 75%+
- **Critical Paths**: 100%
- **Error Paths**: 85%+

## CI/CD Integration

For continuous integration:

```yaml
# Run smoke tests first (quick check)
- name: Smoke Tests
  run: ./scripts/smoke-test.sh

# Run full test suite
- name: Integration Tests
  run: ant -f build/build.xml unitTest

# Verify code quality
- name: Code Coverage
  run: ant -f build/build.xml coverage
```

## Adding New Tests

### Template for New Test
```java
package org.yawlfoundation.yawl.integration.rest;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MyNewTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASSWORD = "YAWL";

    private YEngine engine;
    private String sessionHandle;

    @BeforeEach
    public void setUp() throws Exception {
        engine = YEngine.getInstance();
        sessionHandle = engine.connect(ADMIN_USER, ADMIN_PASSWORD);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (sessionHandle != null && !sessionHandle.isEmpty()) {
            engine.disconnect(sessionHandle);
        }
    }

    @Test
    @Order(1)
    public void testNewFeature() {
        Object result = engine.someMethod(sessionHandle);
        assertNotNull(result);
    }
}
```

### Register in Test Suite
Update `RestIntegrationTestSuite.java`:
```java
@SelectClasses({
    RestApiIntegrationTest.class,
    RestResourceCaseManagementTest.class,
    RestResourceWorkItemTest.class,
    RestResourceErrorHandlingTest.class,
    MyNewTest.class,  // Add here
})
```

## References

- Test Files: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/`
- Smoke Script: `/home/user/yawl/scripts/smoke-test.sh`
- Documentation: `/home/user/yawl/test/org/yawlfoundation/yawl/integration/rest/README.md`
- Project Standards: `/home/user/yawl/CLAUDE.md`
- YAWL Engine: `/home/user/yawl/src/org/yawlfoundation/yawl/engine/YEngine.java`
