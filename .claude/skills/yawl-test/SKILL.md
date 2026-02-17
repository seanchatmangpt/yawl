# YAWL Test Skill (Maven)

**Command**: `/yawl-test`

**Description**: Run YAWL test suites using Maven with optional coverage analysis.

## Examples

```bash
# Run all tests
/yawl-test

# Run tests for specific module
/yawl-test --module=yawl-engine

# Run tests with JaCoCo coverage report
/yawl-test --coverage

# Run tests with verbose output
/yawl-test --verbose

# Run specific module tests with coverage
/yawl-test --module=yawl-elements --coverage
```

## Parameters

- `--module=MODULE` - Run tests for specific Maven module only
- `--coverage` - Generate JaCoCo code coverage report
- `--verbose` - Enable verbose output

## Test Suites

The YAWL project includes comprehensive test coverage across 10+ modules:

- `yawl-elements` - Workflow element tests
- `yawl-engine` - Core engine tests (YEngine, YNetRunner)
- `yawl-stateless` - Stateless engine tests
- `yawl-resourcing` - Resource management tests
- `yawl-worklet` - Worklet service tests
- `yawl-scheduling` - Scheduling service tests
- `yawl-integration` - MCP/A2A integration tests
- `yawl-monitoring` - Monitoring service tests

## Coverage Analysis

When using `--coverage`, the skill generates a JaCoCo coverage report:
- Report location: `target/site/jacoco/index.html`
- Requires JaCoCo plugin configuration in pom.xml
- Shows line and branch coverage metrics

## Equivalent Commands

Instead of using the skill, you can run Maven directly:

```bash
# Instead of /yawl-test
mvn clean test

# Instead of /yawl-test --module=yawl-engine
mvn -pl yawl-engine clean test

# Instead of /yawl-test --coverage
mvn clean test jacoco:report
```

## Test Results

After running tests, results are available in:
- `target/surefire-reports/` - JUnit test results
- `target/site/jacoco/` - Coverage report (if `--coverage` used)

## Troubleshooting

**Tests failing due to database:**
- The test environment uses H2 in-memory database
- No external database setup required
- Database is configured via Maven properties in pom.xml

**Tests failing due to network:**
- Some integration tests may require external connectivity
- Run individual module tests to isolate failures
- Check module-specific SKILL.md for additional setup

**Out of memory errors:**
- Increase heap size: `export MAVEN_OPTS="-Xmx2g"`
