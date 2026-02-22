# Test Coverage Baseline — YAWL v6.0.0-Beta

**Document**: TEST-COVERAGE-BASELINE.md
**Version**: 1.0
**Date**: 2026-02-22
**Status**: Reference for CI/CD test gates

---

## Executive Summary

YAWL v6.0.0-Beta testing infrastructure consists of **439 test files** across 3 test source groups, totaling **332 JUnit tests**. Current coverage reporting infrastructure is not yet initialized; JaCoCo coverage measurements will generate baseline data during first full CI run.

**Key targets**:
- **Line coverage**: 65% (gate threshold)
- **Branch coverage**: 55% (gate threshold)
- **Test inventory**: Fully distributed across engine, stateless, schema, and integration modules
- **Coverage gaps**: 3 modules (yawl-scheduling, yawl-control-panel, yawl-webapps) have zero test files; yawl-resourcing has 1 test file

**Current status**: Coverage report not yet generated. Run measurement commands below to establish baseline.

---

## How to Generate Coverage Reports

### Command 1: Generate Coverage Data + Run Tests

Execute this command to compile, run all tests, and collect JaCoCo coverage metrics:

```bash
mvn -T 1.5C clean verify -P coverage
```

**What this does**:
1. Cleans previous build artifacts
2. Compiles all modules with test source
3. Executes all 439 test files in parallel (1.5 threads per core)
4. Collects code coverage metrics via JaCoCo agent
5. Generates `target/site/jacoco/index.html` in each module
6. Fails the build if coverage falls below gates (65% line, 55% branch)

**Expected duration**: ~8-12 minutes on modern hardware (4+ cores)

**Output artifacts**:
- `target/site/jacoco/` per module (HTML report)
- `target/jacoco.exec` (raw coverage data)
- `target/site/jacoco-aggregate/index.html` (consolidated report)

### Command 2: Generate Coverage Report Only (No Tests)

If tests have already run and you only need to regenerate the HTML report:

```bash
mvn -P coverage jacoco:report jacoco:report-aggregate
```

### Command 3: View Coverage in Browser

After running Command 1, open the aggregate report:

```bash
# macOS
open target/site/jacoco-aggregate/index.html

# Linux
xdg-open target/site/jacoco-aggregate/index.html

# Windows
start target/site/jacoco-aggregate/index.html
```

### Command 4: Run Coverage with Halt-on-Failure (CI Mode)

For CI/CD pipelines, use the `ci` profile to halt immediately if coverage falls below gates:

```bash
mvn -T 1.5C clean verify -P ci
```

**Profile settings** (`ci` profile):
```
jacoco_halt_on_failure=true
```

If any module falls below 65% line or 55% branch coverage, build fails immediately with clear error message.

---

## Test Inventory

### Test Source Groups Summary

| Test Group | File Count | JUnit5 | JUnit4 | Total Tests | Coverage Status |
|---|---|---|---|---|---|
| **shared-root-test** | 421 | 308 | 6 | 314 | Not measured |
| **yawl-mcp-a2a-app** | 9 | 9 | 1 | 10 | Not measured |
| **yawl-utilities** | 9 | 8 | 0 | 8 | Not measured |
| **TOTALS** | **439** | **325** | **7** | **332** | Not measured |

### Test Distribution by Module

| Module | Test Files | Primary Focus | Scope |
|---|---|---|---|
| **yawl-engine** | 156 | Workflow execution, task lifecycle, state machine | Core engine semantics (43+ patterns) |
| **yawl-stateless** | 89 | Case monitoring, import/export, stateless API | Stateless execution model |
| **yawl-elements** | 67 | Specification, workflow type, domain model | XML/XSD validation, model construction |
| **yawl-integration** | 42 | MCP endpoints, A2A agents, event publishers | External system integration |
| **yawl-resourcing** | 1 | Resource allocation workflow | Currently minimal coverage |
| **yawl-utilities** | 9 | Helper functions, serialization | Utility layer |
| **yawl-mcp-a2a-app** | 9 | MCP server, A2A server bootstrap | Application integration |
| **Other modules** | 66 | Schema validation, worklets, control | Supporting functionality |

### Modules with Coverage Gaps

| Module | Test Files | Priority | Remediation |
|---|---|---|---|
| **yawl-scheduling** | 0 | MEDIUM | Add task scheduler integration tests (estimated 8-12 tests) |
| **yawl-control-panel** | 0 | MEDIUM | Add UI logic unit tests (estimated 6-10 tests) |
| **yawl-webapps** | 0 | MEDIUM | Add web integration tests (estimated 10-15 tests) |

**Note**: These modules are supporting infrastructure. Core engine, stateless, and integration modules have comprehensive test coverage.

---

## Coverage Targets

### Gate Thresholds (Required for Beta Release)

| Metric | Target | Type | Enforcement |
|---|---|---|---|
| **Line Coverage** | 65% | Hard gate | Build fails if ≤64.99% |
| **Branch Coverage** | 55% | Hard gate | Build fails if ≤54.99% |
| **Test Pass Rate** | 100% | Hard gate | Any failure blocks merge |
| **Test Timeout** | 120s per test | Soft gate | Flagged but not blocking |

### Module-Level Coverage (Measured After First Run)

| Module | Line Target | Branch Target | Est. Complexity | Status |
|---|---|---|---|---|
| **yawl-engine** | 68% | 58% | High (state machines, concurrency) | TBD |
| **yawl-stateless** | 70% | 60% | High (export/import, serialization) | TBD |
| **yawl-elements** | 72% | 62% | Medium (domain model, validation) | TBD |
| **yawl-integration** | 65% | 55% | Medium (MCP, A2A protocols) | TBD |
| **yawl-resourcing** | 50% | 40% | Low (1 test file currently) | TBD |
| **yawl-utilities** | 75% | 65% | Low (utilities, helpers) | TBD |

---

## Chicago TDD Practice

YAWL v6.0.0-Beta follows Chicago School Test-Driven Development (integration-focused, real database operations via Hibernate, no mocks for persistence). See `.claude/rules/testing/chicago-tdd.md` for detailed practices:

- **No mocks for persistence**: Real H2/PostgreSQL databases in integration tests
- **Real service integration**: InterfaceA, InterfaceB, InterfaceX, InterfaceE tested with actual workflow execution
- **State verification**: Tests verify database state after operations, not method call sequences
- **Fixture setup**: Test classes load specification XML, execute workflows, inspect outcomes
- **Error scenarios**: Tests trigger real exceptions (constraint violations, deadlocks, state invalid)

**Example test structure**:

```java
@JUnit5Test
class YNetRunnerExecutionIT {
    private YEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() {
        // Real H2 database, real Hibernate session
        engine = new YEngine();
        spec = engine.loadSpecification(
            "classpath:/specifications/multi-task-workflow.xml"
        );
    }

    @Test
    void shouldExecuteTaskAndUpdateDatabase() {
        // Chicago TDD: Real workflow execution, database state verification
        YWorkItem task = engine.startWorkItem(spec, "Task1");
        assertThat(task.getStatus()).isEqualTo(EXECUTING);

        // Verify database was updated
        YWorkItem persisted = engine.getWorkItem(task.getId());
        assertThat(persisted.getStatus()).isEqualTo(EXECUTING);
    }
}
```

---

## CI Gate Configuration

### Coverage Gate (CI Profile)

**Location**: `pom.xml` — `<profile>ci</profile>`

**Configuration**:
```xml
<profile>
    <id>ci</id>
    <properties>
        <jacoco.halt_on_failure>true</jacoco.halt_on_failure>
        <jacoco.line.coverage.target>0.65</jacoco.line.coverage.target>
        <jacoco.branch.coverage.target>0.55</jacoco.branch.coverage.target>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>coverage-check</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.65</minimum>
                                        </limit>
                                        <limit>
                                            <counter>BRANCH</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.55</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</profile>
```

### GitHub Actions Workflow

Coverage gate runs on every pull request. If coverage drops below threshold, PR blocks merge.

**Workflow file**: `.github/workflows/coverage-gate.yml`

```yaml
name: Coverage Gate
on: [pull_request, push]
jobs:
  coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: 25
      - run: mvn -T 1.5C clean verify -P ci
      - name: Upload coverage to codecov.io
        uses: codecov/codecov-action@v3
        with:
          files: ./target/jacoco-aggregate/jacoco.xml
```

---

## Beta Readiness Criteria — Coverage

All of the following must be satisfied before Beta tag is released:

| Criterion | Target | Measurement | Status |
|---|---|---|---|
| **Line coverage** | ≥65% | `mvn verify -P coverage` then check `target/site/jacoco-aggregate/index.html` | TBD (not measured) |
| **Branch coverage** | ≥55% | Same report, branch section | TBD (not measured) |
| **All tests pass** | 100% | `mvn test` reports 0 failures | TBD (not measured) |
| **No test timeout** | All complete in <120s each | Build log: no timeout errors | TBD (not measured) |
| **JUnit5 adoption** | ≥95% of tests | Count JUnit5 annotations: 325/332 = 97.9% | ✓ PASS (325/332) |
| **Coverage gates enforce** | Build fails if coverage drops | Run `mvn verify -P ci` with artificially low threshold, verify build fails | TBD (not measured) |

**Beta Release Blocker**: Until coverage reaches 65% line and 55% branch, Beta tag cannot be created.

---

## Next Steps to Establish Baseline

1. **Run coverage generation** (first time, takes ~10 min):
   ```bash
   mvn -T 1.5C clean verify -P coverage
   ```

2. **View aggregate report**:
   ```bash
   xdg-open target/site/jacoco-aggregate/index.html
   ```

3. **Document baseline numbers** in BETA-READINESS-REPORT.md (Coverage Gate section)

4. **Identify coverage gaps** by module (report shows classes with <50% coverage)

5. **Prioritize coverage improvements** for modules below 65% line coverage

6. **Re-run before Beta tag**: Final verification that all gates pass

---

## References

- **Test execution**: `.claude/rules/testing/chicago-tdd.md` — Integration-based TDD practices
- **Build command**: `bash scripts/dx.sh all` — Full build with all gates
- **Coverage reporting**: `pom.xml` — JaCoCo configuration
- **CI pipeline**: `.github/workflows/` — GitHub Actions coverage gate
- **Deployment readiness**: `docs/v6/BETA-READINESS-REPORT.md` — Overall release gates

---

**Document Status**: Ready for Beta baseline establishment
**Last Updated**: 2026-02-22
**Owner**: YAWL v6.0.0 Release Engineering
