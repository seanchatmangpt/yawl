# Test Coverage Improvement Plan for YAWL v5.2

## Current Status
- **Total Test Files**: 176
- **JaCoCo Plugin**: Configured with 65% minimum threshold
- **CI/CD Integration**: GitHub Actions with Codecov reporting

## Coverage Targets by Module

### Module Priority Matrix

| Module | Source Files | Current Tests | Target Coverage | Priority |
|--------|--------------|---------------|-----------------|----------|
| yawl-elements | 51 | TBD | 75% | HIGH |
| yawl-engine | ~100 | 12 | 80% | HIGH |
| yawl-stateless | ~30 | 2 | 75% | HIGH |
| yawl-integration | ~50 | 28 | 70% | MEDIUM |
| yawl-utilities | ~40 | TBD | 70% | MEDIUM |
| yawl-resourcing | ~80 | 1 | 65% | STANDARD |
| yawl-worklet | ~60 | 0 | 65% | STANDARD |
| yawl-scheduling | ~40 | 0 | 65% | STANDARD |
| yawl-monitoring | ~30 | 0 | 65% | STANDARD |
| yawl-control-panel | ~20 | 1 | 65% | STANDARD |

## Test Implementation Progress

### Completed Tests (100% Coverage)
1. ✅ **YSpecVersionTest** - 36 test methods covering all branches
   - Constructors (default, int, string)
   - Version parsing (valid/invalid formats)
   - Version manipulation (increment/rollback)
   - Comparison operations
   - Edge cases (null, invalid, negative)
   - Integration scenarios

### High Priority Tests to Add

#### yawl-elements Package (51 classes)
Target: 75% coverage

**Core Classes (Must have tests):**
1. YSpecification - Workflow specification root
2. YNet - Workflow net representation
3. YTask (YAtomicTask, YCompositeTask) - Task implementations
4. YCondition (YInputCondition, YOutputCondition) - Condition nodes
5. YFlow - Workflow flow connections
6. YDecomposition - Task decomposition
7. YMultiInstanceAttributes - Multiple instance handling
8. YNetElement - Base workflow element

**Test Strategy:**
- Constructor tests (valid/invalid parameters)
- State transition tests
- Validation tests (verify(), validate())
- Serialization/deserialization tests
- Edge cases (null, empty, circular references)

**Example Test Template:**
```java
@Test
void testYSpecification_creation_valid() {
    YSpecification spec = new YSpecification("spec_id");
    assertEquals("spec_id", spec.getSpecID());
    assertNotNull(spec.getRootNet());
}

@Test
void testYSpecification_nullId_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> {
        new YSpecification(null);
    });
}
```

#### yawl-engine Package (~100 classes)
Target: 80% coverage

**Critical Classes:**
1. YEngine - Main engine controller
2. YNetRunner - Net execution engine
3. YWorkItem - Work item lifecycle
4. YCase - Case instance management
5. YPersistenceManager - Data persistence
6. YEngineLifecycleManager - Lifecycle management

**Existing Tests to Enhance:**
- EngineIntegrationTest
- VirtualThreadMigrationTest
- Hibernate6MigrationTest
- YEngineHealthIndicatorTest

**Missing Tests:**
- YNetRunner execution paths
- YWorkItem state transitions
- YCase cancellation scenarios
- Error recovery paths

#### yawl-stateless Package (~30 classes)
Target: 75% coverage

**Key Classes:**
1. YStatelessEngine - Stateless execution
2. YCaseMonitor - Case monitoring
3. YCaseImporter/YCaseExporter - Case import/export

**Existing Tests:**
- StatelessEngineIntegrationTest
- YSRestoreTest (in src/)

**Additional Tests Needed:**
- YCaseMonitor lifecycle
- Import/export edge cases
- Concurrent access scenarios

### Coverage Improvement Strategies

#### 1. Prioritize Business Logic
Focus on:
- Exception handling paths
- Complex conditionals
- State machine transitions
- Validation logic

#### 2. Use Parameterized Tests
Reduce duplication with data-driven tests:
```java
@ParameterizedTest
@CsvSource({
    "valid_id, true",
    "null, false",
    "'', false"
})
void testValidation(String input, boolean expected) {
    assertEquals(expected, validator.isValid(input));
}
```

#### 3. Test Edge Cases
- Null inputs
- Empty collections
- Boundary values (0, -1, MAX_VALUE)
- Concurrent modifications
- Resource exhaustion

#### 4. Integration Tests
- Database transactions
- Multi-threaded scenarios
- Network failures
- Configuration changes

### Weekly Targets

#### Week 1: Foundation (Feb 16-23)
- ✅ Configure JaCoCo
- ✅ Add YSpecVersionTest (36 tests)
- Add YAttributeMapTest (15 tests)
- Add YFlowTest (20 tests)
- **Target**: Establish 40% baseline

#### Week 2: Core Elements (Feb 24-Mar 2)
- Add YSpecificationTest (30 tests)
- Add YNetTest (25 tests)
- Add YTaskTest (40 tests)
- Add YConditionTest (25 tests)
- **Target**: 55% coverage

#### Week 3: Engine Core (Mar 3-9)
- Enhance YWorkItemTest (50 tests)
- Add YNetRunnerTest (40 tests)
- Add YCaseTest (35 tests)
- **Target**: 65% coverage (meets minimum)

#### Week 4: Integration & Stateless (Mar 10-16)
- Add YStatelessEngineTest (30 tests)
- Add YCaseMonitorTest (20 tests)
- Enhance integration tests (25 tests)
- **Target**: 70% coverage

#### Week 5: Polish & Optimize (Mar 17-23)
- Fill coverage gaps identified by JaCoCo
- Add missing edge case tests
- Optimize slow tests
- **Target**: 75% coverage (meets goal)

### Test Quality Metrics

**Required for All Tests:**
- Clear, descriptive test names
- Arrange-Act-Assert structure
- No test interdependencies
- Fast execution (< 100ms per test)
- Deterministic (no random failures)

**Coverage Metrics to Track:**
- Line coverage: 75%+
- Branch coverage: 70%+
- Method coverage: 80%+
- Class coverage: 85%+

### CI/CD Integration

**GitHub Actions Checks:**
1. Run tests on every PR
2. Generate coverage report
3. Enforce 65% minimum (fail build if below)
4. Upload to Codecov
5. Comment coverage delta on PR

**Coverage Badges:**
```markdown
[![codecov](https://codecov.io/gh/yawl/yawl/branch/main/graph/badge.svg)](https://codecov.io/gh/yawl/yawl)
```

### Tools & Resources

**Local Development:**
```bash
# Run tests with coverage
mvn clean test jacoco:report

# View HTML report
xdg-open target/site/jacoco/index.html

# Check threshold
mvn jacoco:check

# Run specific test
mvn test -Dtest=YSpecVersionTest
```

**IDE Integration:**
- IntelliJ IDEA: Built-in coverage runner (Run with Coverage)
- Eclipse: EclEmma plugin
- VS Code: Java Test Runner with coverage

### Continuous Improvement

**Monthly Reviews:**
- Identify low-coverage modules
- Review coverage trends
- Update test priorities
- Celebrate milestones

**Quarterly Goals:**
- Q1 2026: 75% overall coverage
- Q2 2026: 80% overall coverage
- Q3 2026: 85% overall coverage
- Q4 2026: 90% overall coverage

### Blockers & Risks

**Identified Challenges:**
1. Legacy code without clear specs
2. Complex state machines hard to test
3. Database-dependent tests slow
4. Concurrent code non-deterministic

**Mitigation Strategies:**
1. Add documentation while writing tests
2. Use state machine test libraries
3. Use in-memory H2 for tests
4. Use deterministic test frameworks (Awaitility)

---

**Last Updated**: 2026-02-16
**Status**: In Progress
**Next Review**: 2026-02-23
