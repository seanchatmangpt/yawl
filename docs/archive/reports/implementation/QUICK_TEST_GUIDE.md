# Quick Test Guide - YAWL 5.2

**For**: Developers who need to run tests quickly
**Status**: Tests work once compilation is fixed
**Framework**: JUnit 5 (Jupiter)

---

## Prerequisites

✅ Fix compilation errors first (see `TEST_SUITE_BLOCKING_ISSUES.md`)
✅ Ensure H2 database is configured
✅ Java 25 installed

---

## Run All Tests (2 minutes)

```bash
cd /home/user/yawl
ant -f build/build.xml clean compile unitTest
```

**Expected output**:
```
Tests run: 106
Failures: 4 or less
Errors: 0
Time elapsed: ~12 seconds
```

---

## Run Specific Test Suite

### Engine Tests Only
```bash
ant -f build/build.xml unitTest \
  -Dtest.class=org.yawlfoundation.yawl.engine.EngineTestSuite
```

### Elements Tests Only
```bash
ant -f build/build.xml unitTest \
  -Dtest.class=org.yawlfoundation.yawl.elements.ElementsTestSuite
```

### Authentication Tests Only
```bash
ant -f build/build.xml unitTest \
  -Dtest.class=org.yawlfoundation.yawl.authentication.AuthenticationTestSuite
```

### Autonomous Agent Tests Only
```bash
ant -f build/build.xml unitTest \
  -Dtest.class=org.yawlfoundation.yawl.integration.autonomous.AutonomousTestSuite
```

---

## Run Single Test Class

```bash
# Run just one test class
ant -f build/build.xml unitTest \
  -Dtest.class=org.yawlfoundation.yawl.engine.TestYEngineInit
```

---

## Understand Test Results

### Test Output Files

After running tests, check:
- `TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt` (human-readable)
- `TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.xml` (XML format)

### Read Results
```bash
# Summary
head -5 TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt

# Failures only
grep -A 10 "FAILED" TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt
```

---

## Expected Warnings (Safe to Ignore)

These warnings are normal in the test environment:

### 1. Resource Service Offline
```
[ERROR] InterfaceB_EngineBasedClient :- Could not announce enabled workitem
        to default worklist handler at URL http://localhost:8080/resourceService/ib
```
**Why**: Tests run without resource service deployed
**Impact**: None (expected in test mode)

### 2. Logging Disabled
```
[WARN] YEventLogger :- Process logging disabled because Engine persistence is disabled
```
**Why**: Tests run with persistence disabled
**Impact**: None (expected in test mode)

### 3. Net Soundness Warnings
```
[WARN] YNetRunner :- Net has successfully completed, but tokens remaining in the net
```
**Why**: Some test specs are deliberately unsound (to test error handling)
**Impact**: None (expected behavior)

---

## Common Issues

### Issue 1: Compilation Errors

**Symptom**:
```
BUILD FAILED
Compile failed; see the compiler error output for details
```

**Fix**:
See `TEST_SUITE_BLOCKING_ISSUES.md` for detailed fix guide.

### Issue 2: H2 Database Not Found

**Symptom**:
```
[ERROR] Could not connect to H2 database
```

**Fix**:
```bash
# Check H2 is configured
cat build/properties/hibernate.properties | grep h2

# Should see:
# hibernate.dialect=org.hibernate.dialect.H2Dialect
# hibernate.connection.driver_class=org.h2.Driver
```

### Issue 3: Tests Hang

**Symptom**: Tests run but never complete

**Fix**:
```bash
# Kill hung process
pkill -f "java.*junit"

# Check for port conflicts
netstat -tuln | grep 8080

# Run tests again
ant -f build/build.xml unitTest
```

---

## Test Statistics (Last Successful Run)

- **Total tests**: 106
- **Passing**: 102 (96.2%)
- **Failing**: 4 (3.8%)
- **Execution time**: 12.159 seconds
- **Average per test**: 0.11 seconds

---

## Writing New Tests (Chicago TDD)

### Template

```java
package org.yawlfoundation.yawl.mypackage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestMyFeature {

    private MyFeature feature;

    @BeforeEach
    void setUp() {
        // Create REAL objects (no mocks!)
        feature = new MyFeature();
    }

    @AfterEach
    void tearDown() {
        // Clean up resources
        feature = null;
    }

    @Test
    void testFeatureBehavior() {
        // Arrange
        String input = "test input";

        // Act
        String result = feature.process(input);

        // Assert
        assertNotNull(result);
        assertEquals("expected output", result);
    }

    @Test
    void testFeatureErrorHandling() {
        // Test error case
        assertThrows(IllegalArgumentException.class, () -> {
            feature.process(null);
        });
    }
}
```

### Key Principles

1. **No Mocks**: Use real YAWL objects, real database (H2)
2. **Real Integration**: Test actual behavior, not mocked responses
3. **Clear Names**: `testFeatureBehavior()` not `test1()`
4. **One Assertion Focus**: Each test verifies one specific behavior
5. **Clean Up**: Use `@AfterEach` to release resources

### Add to Test Suite

```java
@Suite
@SelectClasses({
    TestExisting.class,
    TestMyFeature.class  // Add your new test here
})
public class MyPackageTestSuite {
}
```

---

## CI/CD Integration (Future)

### GitHub Actions (Proposed)

```yaml
name: YAWL Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 25
        uses: actions/setup-java@v3
        with:
          java-version: '25'
      - name: Run tests
        run: ant -f build/build.xml unitTest
      - name: Upload results
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: TEST-*.xml
```

---

## Quick Reference

| Command | Purpose | Time |
|---------|---------|------|
| `ant compile` | Compile only | ~15 sec |
| `ant unitTest` | Run all tests | ~12 sec |
| `ant clean compile unitTest` | Full rebuild + test | ~30 sec |

---

## Need Help?

- **Test Suite Status**: See `TEST_SUITE_STATUS.md`
- **Blocking Issues**: See `TEST_SUITE_BLOCKING_ISSUES.md`
- **Session Summary**: See `TEST_VALIDATION_SESSION_SUMMARY.md`
- **JUnit 5 Migration**: See `JUNIT5_MIGRATION_SUMMARY.md`
- **Build Health**: See `BUILD_HEALTH_REPORT.md`

---

**Last Updated**: 2026-02-16
**Test Framework**: JUnit 5 (Jupiter)
**Test Count**: 106 tests
**Pass Rate**: 96.2%
