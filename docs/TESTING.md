# YAWL v6.0.0 Testing Guide

## Testing Framework

YAWL uses **JUnit 5** (Jupiter) with **Hamcrest** matchers and **XMLUnit** for specification testing.

### Core Testing Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 API | 6.0.3 | Test framework (declarative) |
| JUnit 5 Engine | 6.0.3 | Test execution engine |
| JUnit 5 Params | 6.0.3 | Parameterized tests |
| Hamcrest | 3.0 | Assertion matchers |
| XMLUnit | 1.6 | XML comparison testing |
| JMH | 1.37 | Microbenchmarking |

## Test Structure

### Directory Layout

```
yawl-{module}/
├── src/
│   └── org/yawlfoundation/...
│       └── *.java               # Production code
├── test/
│   └── org/yawlfoundation/...
│       └── *Test.java           # Unit tests
└── pom.xml
```

### Test File Naming

- **Unit tests**: `*Test.java` (e.g., `YWorkItemTest.java`)
- **Integration tests**: `*IntegrationTest.java`
- **Test suites**: `*TestSuite.java`

## Running Tests

### Run All Tests

```bash
# Entire project
mvn clean test

# Single module
mvn -pl yawl-engine clean test

# Specific package
mvn -DuseFile=false -Dtest=org.yawlfoundation.yawl.engine.* test
```

### Run Specific Tests

```bash
# Single test class
mvn test -Dtest=YWorkItemTest

# Single test method
mvn test -Dtest=YWorkItemTest#testInitialization

# Multiple test classes
mvn test -Dtest=YWorkItemTest,YNetTest

# Pattern matching
mvn test -Dtest=Y*Test
```

### Test Execution Options

```bash
# Run in parallel (override default 4 threads)
mvn test -T 4

# Skip tests
mvn install -DskipTests

# Skip compilation, run existing tests
mvn test -Dmaven.test.skip.compile=true

# Run tests with debug output
mvn test -Dmaven.surefire.debug

# Run tests with logging
mvn test -l target/test.log
```

## Writing Tests

### Basic Unit Test Template

```java
package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("YWorkItem Tests")
public class YWorkItemTest {

    private YWorkItem workItem;

    @BeforeEach
    void setUp() {
        // Initialize test fixtures
        workItem = new YWorkItem("test-id");
    }

    @Test
    @DisplayName("should initialize with provided ID")
    void testInitialization() {
        assertThat(workItem.getID(), is("test-id"));
    }

    @Test
    @DisplayName("should set and get data")
    void testDataHandling() {
        workItem.setData("key", "value");
        assertThat(workItem.getData("key"), equalTo("value"));
    }
}
```

### Using Hamcrest Matchers

```java
// Common assertions
assertThat(actual, is(expected));
assertThat(actual, equalTo(expected));
assertThat(actual, notNullValue());
assertThat(actual, nullValue());
assertThat(actual, instanceOf(String.class));
assertThat(list, hasItems("a", "b"));
assertThat(list, hasSize(3));
assertThat(string, containsString("substring"));
assertThat(string, startsWith("prefix"));
```

### Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = { "test1", "test2", "test3" })
void testMultipleInputs(String input) {
    assertThat(input, notNullValue());
}

@ParameterizedTest
@CsvSource({
    "test1, 10",
    "test2, 20"
})
void testFromCsv(String name, int count) {
    assertThat(count, greaterThan(0));
}
```

### Testing XML Specifications

```java
import org.custommonkey.xmlunit.XMLTestCase;
import org.jdom2.Document;

public class YawlSpecTest extends XMLTestCase {

    @Test
    void testSpecValidity() throws Exception {
        Document spec = parseSpec("src/test/resources/spec.ywl");
        assertThat(spec, notNullValue());
    }

    @Test
    void testXmlComparison() throws Exception {
        assertXMLEqual("expected.xml", "actual.xml");
    }
}
```

## Test Categories

### Unit Tests

**Purpose**: Test individual classes in isolation

**Characteristics**:
- No external dependencies
- Run in < 100ms per test
- Use mock/stub objects if needed
- Test single responsibility principle

**Location**: `test/org/yawlfoundation/yawl/{module}/*Test.java`

**Example**:
```java
@Test
void testYWorkItemLifecycle() {
    // Arrange
    YWorkItem item = new YWorkItem("id");
    
    // Act
    item.start();
    
    // Assert
    assertThat(item.getStatus(), is(Status.RUNNING));
}
```

### Integration Tests

**Purpose**: Test multiple components working together

**Characteristics**:
- Use real dependencies or test containers
- May access database (H2 by default)
- Run in 100ms - 5 seconds
- Test component interactions

**Location**: `test/org/yawlfoundation/yawl/{module}/*IntegrationTest.java`

**Example**:
```java
@Test
void testWorkflowExecution() {
    // Arrange: Load specification
    YSpecification spec = loadSpecification("workflow.ywl");
    YEngine engine = new YEngine();
    
    // Act: Execute workflow
    String caseID = engine.launchCase(spec);
    
    // Assert: Verify execution
    assertThat(caseID, notNullValue());
    assertThat(engine.getCaseStatus(caseID), 
               is(CaseStatus.EXECUTING));
}
```

### Specification Tests

**Purpose**: Validate YAWL workflow specifications against schema

**Characteristics**:
- Test against YAWL_Schema4.0.xsd
- Validate XML structure
- Test workflow patterns
- Verify compliance

**Location**: `test/org/yawlfoundation/yawl/schema/`

**Example**:
```java
@Test
void testSpecCompliesWithSchema() {
    XMLValidator validator = new XMLValidator(
        "schema/YAWL_Schema4.0.xsd");
    ValidationResult result = 
        validator.validate("test-spec.ywl");
    
    assertThat(result.isValid(), is(true));
}
```

## Test Configuration

### Surefire Plugin Configuration

In `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.5.4</version>
    <configuration>
        <!-- Test file patterns -->
        <includes>
            <include>**/*Test.java</include>
            <include>**/*Tests.java</include>
            <include>**/*TestSuite.java</include>
        </includes>
        
        <!-- Parallel execution -->
        <parallel>classes</parallel>
        <threadCount>4</threadCount>
        
        <!-- JVM settings -->
        <argLine>--enable-preview</argLine>
        
        <!-- System properties for tests -->
        <systemPropertyVariables>
            <database.type>h2</database.type>
            <hibernate.dialect>
                org.hibernate.dialect.H2Dialect
            </hibernate.dialect>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

### Test Resource Files

```
src/test/resources/
├── db.properties
├── log4j2-test.xml
├── specifications/
│   ├── simple.ywl
│   ├── complex.ywl
│   └── invalid.ywl
└── sql/
    ├── init.sql
    └── teardown.sql
```

## Database Testing

### H2 In-Memory Database

Default for all tests:

```properties
# In test environment
database.type=h2
hibernate.dialect=org.hibernate.dialect.H2Dialect
hibernate.connection.driver_class=org.h2.Driver
hibernate.connection.url=jdbc:h2:mem:testdb
hibernate.hbm2ddl.auto=create-drop
```

### Test Data Setup

```java
@BeforeEach
void setupDatabase() {
    // Initialize schema
    DatabaseInitializer.init();
    
    // Insert test data
    TestDataFactory.createWorkflows();
}

@AfterEach
void cleanupDatabase() {
    // Clean up (H2 in-memory auto-cleanup)
}
```

## Performance Testing with JMH

### Benchmark Template

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class YWorkItemBenchmark {

    private YWorkItem workItem;

    @Setup
    public void setup() {
        workItem = new YWorkItem("id");
    }

    @Benchmark
    public void testWorkItemCreation() {
        new YWorkItem("test");
    }

    @Benchmark
    public void testDataAccess() {
        workItem.setData("key", "value");
        workItem.getData("key");
    }
}
```

### Running Benchmarks

```bash
mvn clean test -Dbenchmark=true
```

## Code Coverage

### JaCoCo Configuration

```xml
<!-- Note: Disabled in offline mode, enable with network -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.13</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Generate Coverage Report

```bash
# Requires network (JaCoCo plugin download)
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

## Test Organization Best Practices

1. **One assertion per test** (or related assertions)
   ```java
   @Test
   void testStatusTransition() {
       // Good: Single concept
       assertThat(workItem.getStatus(), is(RUNNING));
   }
   ```

2. **Descriptive test names**
   ```java
   @Test
   @DisplayName("should execute task when all inputs ready")
   void testTaskExecutionWithAllInputs() { }
   ```

3. **Use AAA pattern** (Arrange, Act, Assert)
   ```java
   @Test
   void testWorkflow() {
       // Arrange: Setup
       YWorkItem item = new YWorkItem("id");
       
       // Act: Execute
       item.start();
       
       // Assert: Verify
       assertThat(item.isStarted(), is(true));
   }
   ```

4. **Test edge cases**
   ```java
   @Test
   void testWithNullInput() {
       assertThat(validator.validate(null), is(false));
   }
   
   @Test
   void testWithEmptyCollection() {
       assertThat(processor.process(emptyList()), notNullValue());
   }
   ```

5. **Use test fixtures**
   ```java
   @BeforeEach
   void setUp() {
       // Reusable test data
   }
   ```

## Skipping Tests

### Skip All Tests

```bash
mvn install -DskipTests
```

### Skip Specific Test Classes

```xml
<!-- In pom.xml -->
<configuration>
    <excludes>
        <exclude>**/*LongRunningTest.java</exclude>
    </excludes>
</configuration>
```

### Skip at Test Method Level

```java
@Test
@Disabled("WIP: Not yet implemented")
void testFeatureUnderDevelopment() {
    fail("Test not implemented");
}
```

## Test Debugging

### Run Single Test with Debug

```bash
# Listen on port 5005
mvn test -Dmaven.surefire.debug

# In IDE: Run > Debug > Attach to Process, select Maven
```

### Increase Logging

```bash
# In test resource log4j2-test.xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration packages="org.yawlfoundation.yawl">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d %-5p %c: %m%n" />
        </Console>
    </Appenders>
    <Loggers>
        <Logger name="org.yawlfoundation.yawl" level="DEBUG" />
        <Root level="INFO">
            <AppenderRef ref="Console" />
        </Root>
    </Loggers>
</Configuration>
```

## Continuous Integration

### Test Requirements for CI/CD

1. **All tests must pass**
   ```bash
   mvn clean test
   ```

2. **No test failures or errors**
   ```bash
   # Exit code must be 0
   echo $?
   ```

3. **Code coverage target**: 80%+ (when enabled)

4. **No flaky tests**
   - Tests should pass consistently
   - No race conditions
   - No timing dependencies

## Test Coverage Target

| Category | Target |
|----------|--------|
| Engine | 85%+ |
| Elements | 80%+ |
| Utilities | 90%+ |
| Integration | 75%+ |
| Overall | 80%+ |

## Next Steps

1. Review test examples in `test/` directory
2. Run `mvn clean test` to verify setup
3. Write tests for new features (TDD recommended)
4. Check **BUILD.md** for compilation
5. See **CONTRIBUTING.md** for code review process

