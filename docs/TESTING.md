# YAWL v6.0.0 Testing Guide

## Testing Philosophy

YAWL follows a zero-tolerance quality standard with comprehensive testing at multiple levels:

| Level | Type | Tools | Location |
|-------|------|-------|----------|
| Unit | White-box | JUnit 5, Hamcrest | `test/` per module |
| Architecture | Structural | ArchUnit | `test/org/yawlfoundation/yawl/quality/architecture/` |
| Integration | Black-box | Shell scripts | `test/shell/` |
| Performance | Benchmarking | JMH | `test/org/yawlfoundation/yawl/performance/jmh/` |

**Core Principle**: No mocks, no stubs, no placeholders. All tests verify real behavior.

---

## Quick Reference

```bash
# Run all unit tests
mvn clean test

# Run specific module tests
mvn -pl yawl-engine clean test

# Run architecture tests
mvn test -Dtest="org.yawlfoundation.yawl.quality.architecture.*"

# Run shell tests (integration)
./scripts/shell-test/runner.sh

# Run performance benchmarks
mvn test -Dbenchmark=true -Dtest="*Benchmark"

# Fast feedback loop (changed modules only)
bash scripts/dx.sh test
```

---

## Testing Framework

### Core Testing Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| JUnit 5 API | 5.12.0 | Test framework (declarative) |
| JUnit 5 Engine | 5.12.0 | Test execution engine |
| JUnit 5 Params | 5.12.0 | Parameterized tests |
| Hamcrest | 3.0 | Assertion matchers |
| XMLUnit | 1.6 | XML comparison testing |
| ArchUnit | 1.4.1 | Architecture validation |
| JMH | 1.37 | Microbenchmarking |

---

## Test Hierarchy

```
test/
├── org/yawlfoundation/yawl/
│   ├── engine/                     # Unit tests (mirror src structure)
│   │   ├── YEngineTest.java
│   │   ├── YNetRunnerTest.java
│   │   └── YWorkItemTest.java
│   ├── elements/
│   │   ├── YTaskTest.java
│   │   └── YConditionTest.java
│   ├── quality/
│   │   └── architecture/           # ArchUnit architecture tests
│   │       ├── YawlLayerArchitectureTest.java
│   │       ├── YawlCycleDetectionTest.java
│   │       └── YawlPackageBoundaryTest.java
│   ├── performance/
│   │   └── jmh/                    # JMH benchmarks
│   │       ├── WorkflowExecutionBenchmark.java
│   │       ├── StructuredConcurrencyBenchmark.java
│   │       ├── InterfaceBClientBenchmark.java
│   │       ├── EventLoggerBenchmark.java
│   │       └── IOBoundBenchmark.java
│   └── integration/                # Integration tests
│       └── *IntegrationTest.java
└── shell/                          # Shell-based black-box tests
    ├── 01-schema-validation/
    ├── 02-stub-detection/
    ├── 03-build-verification/
    ├── 04-engine-lifecycle/
    ├── 05-a2a-protocol/
    ├── 06-mcp-protocol/
    ├── 07-workflow-patterns/
    └── 08-integration/
```

---

## Unit Tests

### Running Unit Tests

```bash
# All tests
mvn clean test

# Specific module
mvn -pl yawl-engine clean test

# Specific test class
mvn test -Dtest=YWorkItemTest

# Specific test method
mvn test -Dtest=YWorkItemTest#testInitialization

# Pattern matching
mvn test -Dtest=Y*Test

# Parallel execution
mvn -T 1.5C clean test
```

### Unit Test Template

```java
package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("YWorkItem Tests")
class YWorkItemTest {

    private YWorkItem workItem;

    @BeforeEach
    void setUp() {
        workItem = new YWorkItem("test-id");
    }

    @Nested
    @DisplayName("Initialization")
    class InitializationTests {

        @Test
        @DisplayName("should initialize with provided ID")
        void testInitialization() {
            assertThat(workItem.getID(), is("test-id"));
        }

        @Test
        @DisplayName("should start in ENABLED status")
        void testInitialStatus() {
            assertThat(workItem.getStatus(), is(YWorkItemStatus.ENABLED));
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class LifecycleTests {

        @Test
        @DisplayName("should transition to EXECUTING when started")
        void testStartTransition() {
            workItem.start();
            assertThat(workItem.getStatus(), is(YWorkItemStatus.EXECUTING));
        }

        @Test
        @DisplayName("should throw exception when starting completed item")
        void testInvalidStartTransition() {
            workItem.start();
            workItem.complete();
            assertThrows(IllegalStateException.class, () -> workItem.start());
        }
    }
}
```

### Using Hamcrest Matchers

```java
import static org.hamcrest.Matchers.*;

// Equality
assertThat(actual, is(expected));
assertThat(actual, equalTo(expected));
assertThat(actual, not(expected));

// Null checks
assertThat(actual, nullValue());
assertThat(actual, notNullValue());

// Type checking
assertThat(actual, instanceOf(String.class));
assertThat(actual, sameInstance(expected));

// Collections
assertThat(list, hasItems("a", "b"));
assertThat(list, hasSize(3));
assertThat(list, contains("a", "b", "c"));
assertThat(list, containsInAnyOrder("b", "a", "c"));
assertThat(map, hasKey("key"));
assertThat(map, hasValue("value"));

// Strings
assertThat(string, containsString("substring"));
assertThat(string, startsWith("prefix"));
assertThat(string, endsWith("suffix"));
assertThat(string, matchesRegex("pattern"));

// Numbers
assertThat(number, greaterThan(5));
assertThat(number, lessThanOrEqualTo(10));
assertThat(number, closeTo(10.0, 0.1));

// Combined
assertThat(actual, allOf(notNullValue(), instanceOf(String.class), containsString("expected")));
assertThat(actual, anyOf(equalTo("a"), equalTo("b")));
```

### Parameterized Tests

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

@ParameterizedTest
@ValueSource(strings = {"input1", "input2", "input3"})
@DisplayName("should handle multiple string inputs")
void testMultipleStrings(String input) {
    assertThat(processor.process(input), notNullValue());
}

@ParameterizedTest
@CsvSource({
    "input1, OUTPUT1",
    "input2, OUTPUT2",
    "input3, OUTPUT3"
})
@DisplayName("should transform inputs to expected outputs")
void testTransformation(String input, String expected) {
    assertThat(processor.process(input), is(expected));
}

@ParameterizedTest
@EnumSource(YWorkItemStatus.class)
@DisplayName("should handle all status values")
void testAllStatuses(YWorkItemStatus status) {
    assertThat(status, notNullValue());
}

@ParameterizedTest
@MethodSource("provideTestCases")
@DisplayName("should process complex test cases")
void testComplexCases(TestCase testCase) {
    assertThat(processor.process(testCase.input()), is(testCase.expected()));
}

static Stream<TestCase> provideTestCases() {
    return Stream.of(
        new TestCase("input1", "output1"),
        new TestCase("input2", "output2")
    );
}

record TestCase(String input, String expected) {}
```

---

## Architecture Tests (ArchUnit)

### Overview

Architecture tests enforce structural invariants at compile time. They prevent architectural erosion by validating layer boundaries, naming conventions, and dependency rules.

**See**: [docs/quality/ARCHITECTURE-TESTS.md](quality/ARCHITECTURE-TESTS.md) for full documentation.

### Running Architecture Tests

```bash
# Run all ArchUnit tests
mvn test -Dtest="org.yawlfoundation.yawl.quality.architecture.*"

# Run specific test class
mvn test -Dtest="YawlLayerArchitectureTest"
```

### The 8 Architecture Rules

| Rule | Purpose |
|------|---------|
| Layer Isolation | Enforce downward-only dependencies |
| Elements Independence | Elements must not depend on engine |
| Infrastructure Isolation | Util/schema/exceptions must not import domain |
| Stateless Isolation | Stateless must not import persistence |
| No Cycles | No cyclic package dependencies |
| Security Isolation | UI must not access security internals |
| Auth Independence | Auth must not depend on persistence |
| Naming Conventions | Enforce naming and visibility rules |

### Example Rule

```java
@ArchTest
public static final ArchRule elementsMustNotDependOnEngine =
    noClasses().that().resideInAPackage("org.yawlfoundation.yawl.elements..")
        .should().dependOnClassesThat()
            .resideInAPackage("org.yawlfoundation.yawl.engine..")
        .as("Elements package must not depend on Engine package.");
```

---

## Shell Tests (Integration)

### Overview

Shell tests provide black-box testing for end-to-end scenarios. They verify real system behavior through HTTP requests, process management, and file operations.

**See**: [docs/quality/SHELL-TESTS.md](quality/SHELL-TESTS.md) for full documentation.

### Running Shell Tests

```bash
# Run all 8 phases
./scripts/shell-test/runner.sh

# Quick tests (phases 1-3)
./scripts/shell-test/runner.sh -q

# Specific phase
./scripts/shell-test/runner.sh -p 04

# Stop on first failure
./scripts/shell-test/runner.sh --stop-on-failure
```

### Test Phases

| Phase | Name | Purpose |
|-------|------|---------|
| 01 | Schema Validation | Validate XML against XSD |
| 02 | Stub Detection | Scan for forbidden patterns |
| 03 | Build Verification | Verify compilation and artifacts |
| 04 | Engine Lifecycle | Test startup/health/shutdown |
| 05 | A2A Protocol | Test agent-to-agent protocol |
| 06 | MCP Protocol | Test model context protocol |
| 07 | Workflow Patterns | Test pattern implementations |
| 08 | Integration Report | Generate comprehensive reports |

---

## Performance Benchmarks (JMH)

### Overview

JMH (Java Microbenchmark Harness) provides rigorous performance measurement for critical code paths.

**Location**: `test/org/yawlfoundation/yawl/performance/jmh/`

### Available Benchmarks

| Benchmark | Purpose | Target |
|-----------|---------|--------|
| `WorkflowExecutionBenchmark` | Multi-stage workflow execution | > 200 tasks/sec |
| `StructuredConcurrencyBenchmark` | StructuredTaskScope vs CompletableFuture | StructuredTaskScope faster for cancellation |
| `InterfaceBClientBenchmark` | Interface B REST API calls | < 50ms per call |
| `EventLoggerBenchmark` | Event logging throughput | > 10K events/sec |
| `IOBoundBenchmark` | I/O-bound task handling | Virtual threads advantage |

### Running Benchmarks

```bash
# Run all benchmarks
mvn test -Dbenchmark=true -Dtest="*Benchmark"

# Run specific benchmark
mvn test -Dbenchmark=true -Dtest="WorkflowExecutionBenchmark"

# Direct JMH execution
java -jar target/benchmarks.jar WorkflowExecutionBenchmark
```

### Benchmark Template

```java
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx4g", "-XX:+UseG1GC"})
public class MyBenchmark {

    @Param({"10", "50", "100"})
    private int iterations;

    @Benchmark
    public void benchmarkMethod(Blackhole bh) {
        // Benchmark code here
        bh.consume(result);
    }
}
```

### Target Metrics

| Operation | Target | Critical |
|-----------|--------|----------|
| Workflow start | < 100ms | Yes |
| Work item checkout | < 50ms | Yes |
| Work item complete | < 50ms | Yes |
| Case cancellation | < 200ms | Yes |
| Specification load | < 500ms | No |
| Event logging | > 10K/sec | No |

---

## Code Coverage

### JaCoCo Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.15</version>
    <configuration>
        <rules>
            <rule>
                <element>BUNDLE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.70</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

### Coverage Targets

| Module | Line Coverage | Branch Coverage |
|--------|---------------|-----------------|
| yawl-engine | 85%+ | 75%+ |
| yawl-elements | 80%+ | 70%+ |
| yawl-stateless | 85%+ | 75%+ |
| yawl-utilities | 90%+ | 80%+ |
| yawl-integration | 75%+ | 65%+ |
| **Overall** | **80%+** | **70%+** |

### Generating Coverage Reports

```bash
# Run tests with coverage
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html

# Check coverage thresholds
mvn verify
```

---

## Test Configuration

### Surefire Plugin

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
        </includes>

        <!-- Parallel execution -->
        <parallel>classes</parallel>
        <threadCount>4</threadCount>

        <!-- JVM settings -->
        <argLine>--enable-preview</argLine>

        <!-- System properties -->
        <systemPropertyVariables>
            <database.type>h2</database.type>
            <hibernate.dialect>org.hibernate.dialect.H2Dialect</hibernate.dialect>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

### Test Resources

```
src/test/resources/
├── db.properties           # Test database configuration
├── log4j2-test.xml         # Test logging configuration
├── specifications/         # YAWL spec files for tests
│   ├── simple.ywl
│   ├── complex.ywl
│   └── invalid.ywl
└── sql/
    ├── init.sql            # Test data setup
    └── teardown.sql        # Test data cleanup
```

### H2 Test Database

```properties
# test/resources/db.properties
database.type=h2
hibernate.dialect=org.hibernate.dialect.H2Dialect
hibernate.connection.driver_class=org.h2.Driver
hibernate.connection.url=jdbc:h2:mem:testdb
hibernate.hbm2ddl.auto=create-drop
```

---

## Quality Gates

### Pre-Commit Requirements

All tests must pass before committing:

```bash
# Fast verification (changed modules)
bash scripts/dx.sh all

# Full verification
mvn -T 1.5C clean compile && mvn -T 1.5C clean test
```

### CI/CD Requirements

| Check | Requirement |
|-------|-------------|
| Unit Tests | 100% pass rate |
| Architecture Tests | 100% pass rate |
| Shell Tests | 100% pass rate |
| Code Coverage | 80%+ line, 70%+ branch |
| No Flaky Tests | Zero tolerance |

### Test Stability Rules

1. **No timing dependencies**: Tests must not rely on sleep or timeouts
2. **No external services**: Tests must work offline (use H2, mocks only in test scope)
3. **Idempotent**: Running tests multiple times produces same result
4. **Isolated**: Tests must not affect each other
5. **Deterministic**: Same input always produces same output

---

## Test Writing Guide

### Best Practices

1. **One assertion per test** (or related assertions)
   ```java
   @Test
   void testStatusTransition() {
       workItem.start();
       assertThat(workItem.getStatus(), is(RUNNING));
   }
   ```

2. **Descriptive test names**
   ```java
   @Test
   @DisplayName("should throw exception when starting completed work item")
   void testInvalidStartTransition() { }
   ```

3. **Use AAA pattern** (Arrange, Act, Assert)
   ```java
   @Test
   void testWorkflow() {
       // Arrange
       YWorkItem item = new YWorkItem("id");

       // Act
       item.start();

       // Assert
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
       engine = new YEngine();
       spec = loadSpecification("test.ywl");
   }
   ```

6. **Group related tests**
   ```java
   @Nested
   @DisplayName("Lifecycle operations")
   class LifecycleTests {
       @Test void testStart() { }
       @Test void testComplete() { }
       @Test void testCancel() { }
   }
   ```

### Anti-Patterns to Avoid

| Anti-Pattern | Problem | Solution |
|--------------|---------|----------|
| Multiple unrelated assertions | Hard to diagnose failures | Split into separate tests |
| Test interdependence | Cascading failures | Use `@BeforeEach` for setup |
| Hard-coded delays | Flaky tests | Use awaitility or events |
| Catching and swallowing exceptions | False positives | Let test fail or re-throw |
| Mocking everything | Tests nothing real | Mock only external dependencies |
| Asserting on implementation details | Brittle tests | Assert on behavior, not state |

---

## Debugging Tests

### Run with Debug Output

```bash
# Maven debug mode
mvn test -Dmaven.surefire.debug

# Attach IDE debugger to port 5005
```

### Increase Logging

```xml
<!-- src/test/resources/log4j2-test.xml -->
<Loggers>
    <Logger name="org.yawlfoundation.yawl" level="DEBUG"/>
</Loggers>
```

### Run Single Test Verbosely

```bash
mvn test -Dtest=YWorkItemTest -X
```

---

## CI/CD Integration

### GitHub Actions

```yaml
- name: Run Tests
  run: mvn -T 1.5C clean test

- name: Run Architecture Tests
  run: mvn test -Dtest="org.yawlfoundation.yawl.quality.architecture.*"

- name: Run Shell Tests
  run: ./scripts/shell-test/runner.sh --no-color

- name: Upload Coverage
  uses: codecov/codecov-action@v4
  with:
    files: target/site/jacoco/jacoco.xml
```

---

## Related Documentation

- **Architecture Tests**: [docs/quality/ARCHITECTURE-TESTS.md](quality/ARCHITECTURE-TESTS.md)
- **Shell Tests**: [docs/quality/SHELL-TESTS.md](quality/SHELL-TESTS.md)
- **Build Guide**: [docs/BUILD.md](BUILD.md)
- **Contributing**: [docs/CONTRIBUTING.md](CONTRIBUTING.md)
