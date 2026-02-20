---
paths:
  - "**/src/test/**"
  - "**/test/**/*.java"
  - "test/**"
---

# Testing Rules — Chicago TDD (Detroit School)

## Real Integrations Only
- Test real YAWL objects: YSpecificationID, InterfaceB clients, YWorkItem, WorkItemRecord
- Use H2 in-memory database for persistence tests (auto-configured by session-start hook)
- Never use Mockito, EasyMock, or any mocking framework in tests that exercise production paths
- Create real test fixtures with actual YAWL Engine instances

## Framework
- **JUnit 5** for new tests (JUnit Jupiter, `@Test`, `@BeforeEach`, `@AfterEach`)
- **JUnit 4** for legacy tests (junit.framework.TestCase, `setUp()`/`tearDown()`)
- Parallel execution: `@Execution(ExecutionMode.CONCURRENT)` at method level
- Run: `bash scripts/dx.sh` (fast) or `mvn -T 1.5C clean test` (full)

## Coverage Targets
- 80%+ line coverage on new code
- 70%+ branch coverage
- 100% on critical paths (engine execution, case management)
- JaCoCo enforced in CI profile: `mvn -P ci clean verify`

## Test Data
- Use Java records for test data objects (immutable, no builders)
- Load test specifications from `exampleSpecs/` or `src/test/resources/`
- Clean up resources in `@AfterEach` / `tearDown()`

## Anti-Patterns (blocked by hyper-validate.sh)
- No mock/stub/fake classes in test/ that leak into production patterns
- No `@Disabled` or `@Ignore` without a linked issue number
- No empty test methods or assertions that always pass
