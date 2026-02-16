# JaCoCo Code Coverage Configuration Guide

## Overview
JaCoCo (Java Code Coverage) has been configured for YAWL v5.2 with the following targets:
- **Overall Coverage Target**: 75%+
- **Minimum Threshold (enforced in CI)**: 65%
- **Branch Coverage**: 60%+

## Configuration Details

### Maven Plugin Configuration
The JaCoCo Maven plugin (`0.8.11`) is configured in the parent `pom.xml` with:

1. **prepare-agent**: Instruments classes during test execution
2. **report**: Generates HTML/XML/CSV coverage reports after tests
3. **report-aggregate**: Combines coverage data across all modules
4. **check**: Enforces minimum coverage thresholds

### Coverage Thresholds

#### Bundle (Overall Project) Level:
- Line Coverage: ≥ 65%
- Branch Coverage: ≥ 60%

#### Package Level:
- Line Coverage: ≥ 60% (excluding test packages)

#### Class Level:
- Line Coverage: ≥ 50% (excluding test classes)

### Excluded from Coverage:
- Test classes (*Test, *TestCase, *TestSuite)
- Test packages (*.test.*, *.tests.*)
- package-info.java files

## Module-Specific Targets

### High Priority (75%+ coverage target):
1. **yawl-elements**: Core data types and workflow elements
2. **yawl-engine**: Workflow engine logic
3. **yawl-stateless**: Stateless API operations

### Medium Priority (70%+ coverage target):
4. **yawl-integration**: MCP and A2A integration
5. **yawl-utilities**: Helper classes and utilities

### Standard Priority (65%+ coverage target):
6. **yawl-resourcing**: Resource allocation
7. **yawl-worklet**: Worklet service
8. **yawl-scheduling**: Scheduling service
9. **yawl-monitoring**: Monitoring service
10. **yawl-control-panel**: Control panel UI

## Running Coverage Analysis

### Generate Coverage Report:
```bash
mvn clean test jacoco:report
```

### View HTML Report:
```bash
# Open in browser:
# - Linux: xdg-open target/site/jacoco/index.html
# - macOS: open target/site/jacoco/index.html
# - Windows: start target/site/jacoco/index.html
```

### Verify Coverage Meets Thresholds:
```bash
mvn verify jacoco:check
```

### Generate Aggregate Report (All Modules):
```bash
mvn clean verify
# Report location: target/site/jacoco-aggregate/index.html
```

## CI/CD Integration

### GitHub Actions Workflow
Added to `.github/workflows/build-test-deploy.yml`:

```yaml
- name: Generate Coverage Report
  run: mvn jacoco:report
  
- name: Verify Coverage Threshold
  run: mvn jacoco:check

- name: Upload Coverage to Codecov
  uses: codecov/codecov-action@v3
  with:
    files: ./target/site/jacoco/jacoco.xml
    flags: unittests
    name: codecov-yawl
```

## Coverage Improvement Strategy

### Phase 1: Establish Baseline
1. Run initial coverage analysis
2. Document current coverage by module
3. Identify low-coverage areas

### Phase 2: Prioritize Critical Paths
Focus test writing on:
- Exception handling
- Edge cases (null, empty, invalid input)
- Complex conditionals
- State transitions
- Boundary conditions

### Phase 3: Write Missing Tests
Target uncovered code:
- Public methods without tests
- Exception branches
- Error handling paths
- Complex business logic

### Phase 4: Continuous Monitoring
- Coverage reports generated on every PR
- Build fails if coverage drops below 65%
- Monthly coverage review meetings

## Test Writing Guidelines

### Test Template:
```java
@Test
void testMethodName_condition_expectedBehavior() {
    // Arrange: Set up test data
    YSpecification spec = TestDataFactory.createTestSpecification();
    
    // Act: Execute the method
    YSpecificationID id = new YSpecificationID(spec);
    
    // Assert: Verify results
    assertEquals("expected", id.getName());
    assertTrue(id.isValid());
}
```

### Parameterized Tests for Multiple Cases:
```java
@ParameterizedTest
@CsvSource({
    "value1, expected1",
    "value2, expected2",
    "null, false"
})
void testVariousCases(String input, String expected) {
    // Test implementation
}
```

### Exception Testing:
```java
@Test
void testInvalidInput_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> {
        new YSpecificationID(null);
    });
}
```

## Coverage Analysis Tools

### JaCoCo Report Structure:
```
target/site/jacoco/
├── index.html              # Overall summary
├── jacoco.xml              # Machine-readable (for CI tools)
├── jacoco.csv              # Spreadsheet format
└── [package]/
    └── [class].html        # Class-level detail
```

### Coverage Metrics:
- **Instructions (C0)**: Individual Java bytecode instructions
- **Branches (C1)**: if/switch decision branches
- **Lines**: Source code lines
- **Methods**: Individual methods
- **Classes**: Class-level coverage

### Color Coding:
- 🟢 **Green**: Fully covered
- 🟡 **Yellow**: Partially covered
- 🔴 **Red**: Not covered

## Offline Mode Configuration

For environments without internet access, ensure JaCoCo plugin is pre-cached:

```bash
# Download JaCoCo plugin to local Maven repository
mvn dependency:get \
  -DgroupId=org.jacoco \
  -DartifactId=jacoco-maven-plugin \
  -Dversion=0.8.11
```

## Troubleshooting

### Issue: Plugin not found
**Solution**: Verify Maven can access JaCoCo in local repository or Maven Central

### Issue: Coverage too low
**Solution**: Add tests for uncovered lines (check report's red-highlighted code)

### Issue: False positives
**Solution**: Add exclusions in pom.xml for generated code, constants, etc.

### Issue: Aggregate report missing data
**Solution**: Ensure all modules have run tests before aggregate goal

## Performance Impact

JaCoCo has minimal performance impact:
- Test execution: ~5-10% slower
- Build time: +10-30 seconds for report generation
- Disk usage: ~50-100 MB for coverage data

## References

- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [Maven JaCoCo Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html)
- [Coverage Best Practices](https://martinfowler.com/bliki/TestCoverage.html)

---

**Last Updated**: 2026-02-16
**YAWL Version**: 5.2
**JaCoCo Version**: 0.8.11
