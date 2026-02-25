# YAWL Testing Guide

**Version**: 6.0.0-Beta | **Framework**: JUnit 5 | **Coverage Target**: 65% line / 55% branch

This guide explains the Chicago TDD approach used in YAWL v6.0.0, how to run tests, and how to interpret coverage reports.

---

## Table of Contents

1. [Chicago TDD Approach](#chicago-tdd-approach)
2. [Quick Commands](#quick-commands)
3. [Test Organization](#test-organization)
4. [Running Tests](#running-tests)
5. [Coverage Reports](#coverage-reports)
6. [Writing Tests](#writing-tests)
7. [Common Issues](#common-issues)

---

## Chicago TDD Approach

**Chicago School** (outside-in) emphasizes:
- Start with the **outer loop** (integration/system tests)
- Drive down to **inner loops** (unit tests)
- Each layer validates the one below

**YAWL Test Pyramid**:
```
                   System Tests (5%)
                  /              \
            Integration Tests (20%)
            /                      \
        Unit Tests (75%)
```

### Example: Workflow Case Execution

**Outer Loop** (System Test):
```java
@Test
void testCaseExecutionToCompletion() {
    // Deploy specification
    engine.loadSpecification(spec);

    // Launch case
    CaseID caseId = engine.launchCase(spec.getID());

    // Complete work items
    engine.completeWorkItem(workItem, caseData);

    // Assert case completed
    CaseState state = engine.getCaseState(caseId);
    assertEquals(COMPLETED, state.getStatus());
}
```

**Middle Loop** (Integration Test):
```java
@Test
void testWorkItemCompletion() {
    // Focus: work item lifecycle
    YWorkItem item = new YWorkItem(...);
    item.complete(outcomeData);
    assertEquals(COMPLETED, item.getStatus());
}
```

**Inner Loop** (Unit Test):
```java
@Test
void testWorkItemStatusTransition() {
    // Focus: single method behavior
    YWorkItem item = new YWorkItem();
    item.start();
    assertTrue(item.isStarted());
}
```

---

## Quick Commands

### Run Tests

```bash
# Unit tests only (fastest, typical feedback loop)
mvn -T 1.5C clean test                           # ~60s (all modules)
mvn -pl yawl-engine clean test                   # ~15s (single module)

# All tests with coverage report
mvn -T 1.5C clean verify -P coverage             # ~90s
open target/site/jacoco/index.html               # View HTML report

# Specific test class
mvn -T 1.5C test -Dtest=YEngineTest              # Run one test class
mvn -T 1.5C test -Dtest=YEngine*                 # Pattern match

# Specific test method
mvn -T 1.5C test -Dtest=YEngineTest#testLaunchCase

# Skip tests in build
mvn clean compile -DskipTests
```

### Coverage Analysis

```bash
# Generate JaCoCo report
mvn clean test jacoco:report

# View coverage report (HTML)
open target/site/jacoco/index.html

# Export as XML (CI/CD integration)
cat target/site/jacoco/jacoco.xml

# Check coverage meets gates (65% line / 55% branch)
mvn verify -P coverage     # Fails if gates not met
```

---

## Test Organization

### Module Structure

Each module follows this pattern:

```
yawl-engine/
├── src/main/java/...
├── src/test/java/...
│   └── org/yawlfoundation/yawl/engine/
│       ├── *Test.java           (Unit tests)
│       ├── integration/
│       │   └── *IntegrationTest.java
│       └── system/
│           └── *SystemTest.java
└── pom.xml
```

### Test Naming Convention

| Pattern | Purpose | Scope |
|---------|---------|-------|
| `*Test.java` | Unit test (no suffix) | Single class, fast |
| `*IntegrationTest.java` | Integration test | Multi-component, uses Docker |
| `*SystemTest.java` | System test | Full workflow, end-to-end |

### Test File Count (377 total)

| Module | Test Files | Count | Type |
|--------|------------|-------|------|
| `yawl-engine` | YEngineTest, YNetRunnerTest, etc. | ~120 | Unit |
| `yawl-elements` | YNetTest, YTaskTest, YSpecTest, etc. | ~80 | Unit |
| `yawl-stateless` | YStatelessEngineTest, etc. | ~40 | Unit |
| `yawl-integration` | YawlA2AServerTest, MCP tests, etc. | ~35 | Integration |
| `yawl-mcp-a2a-app` | Spring Boot integration tests | ~5 | Integration |
| **Total** | - | **377** | - |

---

## Running Tests

### 1. Unit Tests (Fast Feedback)

```bash
# All unit tests across modules
mvn clean test

# Single module
mvn -pl yawl-engine clean test

# Single test class
mvn test -Dtest=YEngineTest

# Single test method
mvn test -Dtest=YEngineTest#testLaunchCase

# Pattern match (all tests ending in "Test")
mvn test -Dtest=Y*Test
```

**Expected Time**: ~60s (all modules), ~15s (single module)

### 2. Integration Tests

```bash
# Run integration tests (includes Docker containers)
mvn -pl yawl-integration clean verify -P docker

# Integration tests in specific module
mvn -pl yawl-mcp-a2a-app clean verify

# Skip integration tests (unit only)
mvn clean test -DskipITs
```

**Expected Time**: ~90s (with container startup)

### 3. Full Validation

```bash
# Compile + Unit Tests + Integration Tests + Coverage
mvn clean verify -P coverage

# Add static analysis (SpotBugs, PMD, Checkstyle)
mvn clean verify -P analysis

# Add security audit (OWASP CVE check)
mvn clean verify -P security-audit

# Production build with all gates
mvn clean package -P prod
```

**Expected Time**: ~5m (full validation)

---

## Coverage Reports

### Generate JaCoCo Report

```bash
# Run tests and generate coverage report
mvn clean test jacoco:report

# Report location
target/site/jacoco/index.html

# View in browser
open target/site/jacoco/index.html
```

### Coverage Format

```
YAWL Instrumentation Report
├── Line Coverage: 65.3% (Target: 65%)
├── Branch Coverage: 55.7% (Target: 55%)
├── Method Coverage: 78.2%
└── Complexity: 2.3
```

### Coverage Gates (JaCoCo)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>LINE</element>
                        <minimum>0.65</minimum>  <!-- 65% -->
                    </rule>
                    <rule>
                        <element>BRANCH</element>
                        <minimum>0.55</minimum>  <!-- 55% -->
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Interpreting Coverage Reports

**Good Coverage** (> 65% line / 55% branch):
```
✅ Most code paths tested
✅ Build passes JaCoCo gates
✅ Ready for production
```

**Low Coverage** (< 65% line / 55% branch):
```
❌ Missing test paths
❌ Build fails verification
❌ Add tests before committing
```

**Zero-Coverage Modules** (Need attention):
- `yawl-worklet` — Dynamic workflow invocation
- `yawl-scheduling` — Timer and calendar services
- `yawl-security` — Digital signatures and PKI
- `yawl-control-panel` — Desktop Swing UI

---

## Writing Tests

### Unit Test Template (JUnit 5)

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class YEngineTest {

    private YEngine engine;

    @BeforeEach
    void setUp() {
        engine = new YEngine();
    }

    @Test
    void testLaunchCaseWithValidSpecification() {
        // Arrange
        YSpecification spec = createTestSpec();

        // Act
        CaseID caseId = engine.launchCase(spec.getID());

        // Assert
        assertNotNull(caseId);
        CaseState state = engine.getCaseState(caseId);
        assertEquals(CaseStatus.ACTIVE, state.getStatus());
    }

    @Test
    void testLaunchCaseWithNullSpecificationThrows() {
        // Act & Assert
        assertThrows(YPersistenceException.class, () -> {
            engine.launchCase(null);
        });
    }

    private YSpecification createTestSpec() {
        YSpecification spec = new YSpecification();
        spec.setID("test-spec");
        return spec;
    }
}
```

### Integration Test Template

```java
import org.testcontainers.containers.PostgreSQLContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Container;
import static org.junit.jupiter.api.Assertions.*;

class YEngineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>()
        .withDatabaseName("yawl_test")
        .withUsername("yawl")
        .withPassword("test");

    private YEngine engine;

    @BeforeEach
    void setUp() {
        // Initialize with test database
        engine = new YEngine();
        engine.setDataSource(postgres.getJdbcUrl(), "yawl", "test");
    }

    @Test
    void testCaseExecutionWithPersistence() {
        // Arrange
        YSpecification spec = loadTestSpec();
        engine.loadSpecification(spec);

        // Act
        CaseID caseId = engine.launchCase(spec.getID());
        YWorkItem item = engine.getWorkItems(caseId).get(0);
        engine.completeWorkItem(item, createOutcomeData());

        // Assert
        CaseState state = engine.getCaseState(caseId);
        assertEquals(CaseStatus.COMPLETED, state.getStatus());

        // Verify persistence: reload from DB
        YEngine engine2 = new YEngine();
        engine2.setDataSource(postgres.getJdbcUrl(), "yawl", "test");
        CaseState reloaded = engine2.getCaseState(caseId);
        assertEquals(CaseStatus.COMPLETED, reloaded.getStatus());
    }

    private YSpecification loadTestSpec() {
        // Load from src/test/resources/...
        // or create programmatically
    }

    private Map<String, Object> createOutcomeData() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "approved");
        return data;
    }
}
```

### Test Data Factory

```java
public class TestDataFactory {

    public static YSpecification createSimpleWorkflow() {
        YSpecification spec = new YSpecification();
        spec.setID("test-spec");
        spec.setVersion("1.0");

        YNet net = new YNet();

        // Add start task
        YTask startTask = new YTask("StartTask");
        net.addTask(startTask);

        // Add end condition
        YCondition endCondition = new YCondition("Done");
        net.addCondition(endCondition);

        return spec;
    }

    public static Map<String, Object> createTestCaseData() {
        Map<String, Object> data = new HashMap<>();
        data.put("case_name", "Test Case");
        data.put("owner", "test@example.com");
        return data;
    }
}
```

---

## Common Issues

### Test Fails with "Cannot Find Specification"

**Problem**: Specification file not found in test resources

**Solution**:
```bash
# Place test specs in:
src/test/resources/specifications/

# Load in test:
YSpecification spec = YSpecificationFactory.load(
    "src/test/resources/specifications/test-spec.yawl"
);
```

### Test Passes Locally, Fails in CI

**Problem**: Test depends on local file paths or timing

**Solution**:
```java
// ❌ Bad: hardcoded path
String path = "/Users/alice/specs/test.yawl";

// ✅ Good: classpath resource
Path path = Paths.get(
    YEngineTest.class.getResource("/specifications/test.yawl").toURI()
);
```

### Docker Container Not Cleaning Up

**Problem**: TestContainers leave containers behind

**Solution**:
```bash
# Clean up orphaned containers
docker container prune -f

# Or set cleanup policy in test
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>()
    .withReuse(false);  // Force cleanup
```

### Coverage Report Shows 0%

**Problem**: JaCoCo not instrumenting code

**Solution**:
```bash
# Ensure jacoco-maven-plugin is in build
mvn clean test jacoco:report

# Check target/site/jacoco/index.html exists
ls -la target/site/jacoco/index.html

# Verify execution goals are bound
mvn help:describe -Dplugin=jacoco
```

---

## Performance Targets

All tests must complete within these times:

| Category | Time | Command |
|----------|------|---------|
| Unit Tests | <60s | `mvn clean test` |
| Integration | <90s | `mvn verify` |
| With Coverage | <120s | `mvn verify -P coverage` |
| With Analysis | <300s | `mvn verify -P analysis` |

**If slower**: Use `-T 1.5C` parallelization

```bash
mvn -T 1.5C clean verify -P coverage  # ~90s instead of 120s
```

---

## Debugging Tests

### Run Single Test with Output

```bash
# Verbose output
mvn test -Dtest=YEngineTest#testLaunchCase -X

# Print stdout
mvn test -Dtest=YEngineTest -Dgroups=slow -q

# Debug mode (attach IDE debugger)
mvn test -Dtest=YEngineTest -Dmaven.surefire.debug
```

### View Test Logs

```bash
# Test output
target/surefire-reports/TEST-org.yawlfoundation.yawl.engine.YEngineTest.txt

# Gradle test report (if using Gradle)
open build/reports/tests/test/index.html
```

---

## Next Steps

1. **Read**: [DEVELOPER-BUILD-GUIDE.md](DEVELOPER-BUILD-GUIDE.md) for build commands
2. **Read**: [CLAUDE.md](CLAUDE.md) for development principles
3. **Run**: `bash scripts/dx.sh all` to validate full workflow
4. **Check**: `open target/site/jacoco/index.html` to view coverage

---

**Last Updated**: February 2026 | **Coverage Target**: 65% line / 55% branch | **Test Count**: 377
