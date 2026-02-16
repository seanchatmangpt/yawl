# JUnit 5 Quick Reference for YAWL Developers

## Writing New Tests

### Basic Test Class Structure

```java
package org.yawlfoundation.yawl.elements;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestMyFeature {

    private YSpecification spec;

    @BeforeEach
    void setUp() {
        spec = new YSpecification("test-spec");
    }

    @AfterEach
    void tearDown() {
        // cleanup if needed
    }

    @Test
    void testFeatureWorks() {
        assertNotNull(spec);
        assertEquals("test-spec", spec.getName());
    }
}
```

## Common Annotations

| Annotation | Purpose | JUnit 3 Equivalent |
|-----------|---------|-------------------|
| `@Test` | Marks a test method | `public void testX()` |
| `@BeforeEach` | Setup before each test | `protected void setUp()` |
| `@AfterEach` | Cleanup after each test | `protected void tearDown()` |
| `@BeforeAll` | One-time setup (static) | `setUpBeforeClass()` |
| `@AfterAll` | One-time cleanup (static) | `tearDownAfterClass()` |
| `@Disabled` | Skip a test | Commenting out |
| `@DisplayName` | Custom test name | N/A |

## Assertions

### Basic Assertions

```java
// JUnit 5: message comes LAST
assertEquals(expected, actual, "optional message");
assertNotEquals(value1, value2, "optional message");
assertTrue(condition, "optional message");
assertFalse(condition, "optional message");
assertNull(object, "optional message");
assertNotNull(object, "optional message");
assertSame(expected, actual, "optional message");
assertNotSame(object1, object2, "optional message");
```

### Array and Collection Assertions

```java
assertArrayEquals(expectedArray, actualArray);
assertIterableEquals(expectedList, actualList);
```

### Exception Assertions

```java
// JUnit 5 way (preferred)
assertThrows(YPersistenceException.class, () -> {
    engine.loadSpec("invalid");
});

// Get exception for further assertions
YPersistenceException ex = assertThrows(YPersistenceException.class, () -> {
    engine.loadSpec("invalid");
});
assertEquals("Expected error message", ex.getMessage());
```

### Timeout Assertions

```java
assertTimeout(Duration.ofSeconds(1), () -> {
    // code that should complete within 1 second
    engine.initialize();
});
```

### Combined Assertions

```java
assertAll("specification",
    () -> assertNotNull(spec.getId()),
    () -> assertEquals("test", spec.getName()),
    () -> assertTrue(spec.isValid())
);
```

## Conditional Test Execution

### Operating System Specific

```java
@Test
@EnabledOnOs(OS.LINUX)
void testLinuxOnly() {
    // only runs on Linux
}

@Test
@DisabledOnOs(OS.WINDOWS)
void testNotOnWindows() {
    // runs everywhere except Windows
}
```

### JRE Version Specific

```java
@Test
@EnabledOnJre(JRE.JAVA_11)
void testJava11Only() {
    // only on Java 11
}
```

### System Property Specific

```java
@Test
@EnabledIfSystemProperty(named = "database.type", matches = "h2")
void testH2Database() {
    // only when H2 is configured
}
```

### Environment Variable Specific

```java
@Test
@EnabledIfEnvironmentVariable(named = "ENV", matches = "test")
void testInTestEnvironment() {
    // only in test environment
}
```

## Parameterized Tests

### Value Source

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ParameterizedTest
@ValueSource(strings = {"spec1.xml", "spec2.xml", "spec3.xml"})
void testMultipleSpecs(String specFile) {
    YSpecification spec = unmarshal(specFile);
    assertNotNull(spec);
}
```

### CSV Source

```java
@ParameterizedTest
@CsvSource({
    "apple, 1",
    "banana, 2",
    "cherry, 3"
})
void testWithCsvSource(String fruit, int rank) {
    assertNotNull(fruit);
    assertTrue(rank > 0);
}
```

### Method Source

```java
@ParameterizedTest
@MethodSource("specificationProvider")
void testWithMethodSource(YSpecification spec) {
    assertNotNull(spec.getRootNet());
}

static Stream<YSpecification> specificationProvider() {
    return Stream.of(
        new YSpecification("spec1"),
        new YSpecification("spec2"),
        new YSpecification("spec3")
    );
}
```

## Test Suites

### Creating a Test Suite

```java
package org.yawlfoundation.yawl.mypackage;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    TestFeatureA.class,
    TestFeatureB.class,
    TestFeatureC.class
})
public class MyPackageTestSuite {
}
```

### Package-Based Suite

```java
@Suite
@SelectPackages("org.yawlfoundation.yawl.engine")
public class EngineTestSuite {
}
```

## Test Lifecycle

```
@BeforeAll (once, static)
  ↓
  For each test method:
    @BeforeEach
      ↓
    @Test
      ↓
    @AfterEach
  ↓
@AfterAll (once, static)
```

## Best Practices for YAWL Tests

### 1. Chicago TDD (Real Integrations)

```java
// GOOD: Use real YAWL objects
@Test
void testCaseCreation() {
    YSpecificationID specId = new YSpecificationID(...);
    InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient();
    String caseId = client.launchCase(...);
    assertNotNull(caseId);
}

// BAD: Don't use mocks in production tests
@Test
void testWithMock() {
    MockClient client = new MockClient();  // ❌ NO MOCKS
    client.setCannedResponse("fake-case-id");
}
```

### 2. Test Naming

```java
// GOOD: Descriptive names
@Test
void testCaseCompletesSuccessfullyWithValidInput() { }

@Test
void testInvalidSpecificationThrowsYSyntaxException() { }

// OK: Traditional naming
@Test
void testCaseCompletion() { }

// BETTER: Custom display names
@Test
@DisplayName("Case completes successfully with valid input")
void testCaseCompletion() { }
```

### 3. Test Organization

```java
class TestYEngine {

    // Group 1: Initialization tests
    @Test void testEngineInitialization() { }
    @Test void testEngineWithH2Database() { }

    // Group 2: Case lifecycle tests
    @Test void testCaseLaunch() { }
    @Test void testCaseCompletion() { }
    @Test void testCaseCancellation() { }

    // Group 3: Error handling tests
    @Test void testInvalidSpecThrowsException() { }
    @Test void testDatabaseFailureHandling() { }
}
```

### 4. Setup and Teardown

```java
@BeforeEach
void setUp() throws YPersistenceException {
    // Create real test fixtures
    specId = loadTestSpecification("minimal.yawl");
    client = new InterfaceB_EnvironmentBasedClient();
    sessionHandle = client.connect("admin", "YAWL");
}

@AfterEach
void tearDown() {
    // Clean up real resources
    if (sessionHandle != null) {
        client.disconnect(sessionHandle);
    }
}
```

### 5. Exception Testing

```java
@Test
void testInvalidSpecThrowsYSyntaxException() {
    assertThrows(YSyntaxException.class, () -> {
        unmarshalSpecification("<invalid>xml</invalid>");
    });
}

@Test
void testExceptionMessage() {
    YSyntaxException ex = assertThrows(YSyntaxException.class, () -> {
        unmarshalSpecification("<invalid>xml</invalid>");
    });
    assertTrue(ex.getMessage().contains("malformed"));
}
```

## Running Tests

### Command Line

```bash
# Run all tests
ant -f build/build.xml unitTest

# Run engine tests only
ant -f build/build.xml unitTest-engine-only

# Run specific test class (requires IDE or Maven)
# In IntelliJ: Right-click test class → Run 'TestClassName'
```

### IntelliJ IDEA

1. Right-click on test class or package
2. Select "Run 'TestClassName'" or "Run tests in 'packagename'"
3. View results in test runner panel

## Migration Notes

If you have old JUnit 3/4 tests to migrate:

### Quick Migration Checklist

1. ✅ Remove `extends TestCase`
2. ✅ Remove constructor `public TestX(String name)`
3. ✅ Change `public class` to just `class`
4. ✅ Add `@Test` to all test methods
5. ✅ Change `public void test*()` to `@Test void test*()`
6. ✅ Change `setUp()` to `@BeforeEach void setUp()`
7. ✅ Change `tearDown()` to `@AfterEach void tearDown()`
8. ✅ Remove `super.setUp()` and `super.tearDown()` calls
9. ✅ Move assertion messages to last parameter
10. ✅ Import static assertions: `import static org.junit.jupiter.api.Assertions.*;`

## Resources

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [JUnit 5 API Documentation](https://junit.org/junit5/docs/current/api/)
- YAWL Migration Summary: `/home/user/yawl/JUNIT5_MIGRATION_SUMMARY.md`

## Common Errors and Solutions

### Error: "Cannot resolve symbol 'Test'"

**Solution**: Add JUnit 5 imports:
```java
import org.junit.jupiter.api.Test;
```

### Error: "Assertions not found"

**Solution**: Add static import:
```java
import static org.junit.jupiter.api.Assertions.*;
```

### Error: "Test method must not be private"

**Solution**: Test methods can be package-private (no modifier) or public:
```java
@Test
void testSomething() { }  // ✅ package-private OK

@Test
public void testSomething() { }  // ✅ public OK
```

### Error: "No tests found"

**Solution**: Ensure:
1. Test class name matches pattern `Test*.java`
2. Test methods have `@Test` annotation
3. Build includes JUnit 5 JARs in classpath

## Contact

For questions about JUnit 5 testing in YAWL:
- Check YAWL documentation
- Review existing test examples in `/test/org/yawlfoundation/yawl/`
- Follow Chicago TDD principles: real integrations, no mocks
