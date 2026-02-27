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

## Parameterized Tests
- Use `@ParameterizedTest` + `@MethodSource` / `@CsvSource` for data-driven tests
- Prefer `@MethodSource` for complex YAWL test cases (returns `Stream<Arguments>`):
  ```java
  @ParameterizedTest
  @MethodSource("workflowPatterns")
  void testNetExecution(YNetRunner runner, String expectedState) { ... }

  static Stream<Arguments> workflowPatterns() {
      return Stream.of(
          Arguments.of(buildSequential(), "completed"),
          Arguments.of(buildParallelSplit(), "running")
      );
  }
  ```
- Use `@CsvSource` for simple value pairs (primitive inputs/outputs only)
- Name test display with `@ParameterizedTest(name = "{index}: {0}")`

## @Disabled Policy
- `@Disabled` MUST include a reason and a linked issue: `@Disabled("YAWL-123: flaky on CI")`
- Never `@Disabled` without a linked issue — hyper-validate.sh will block it
- `@Disabled` tests must be fixed or deleted within one sprint

## Temporary Directory and File Tests
- Use `@TempDir Path tempDir` injection for file system tests (JUnit 5 auto-cleanup)
- Never create temp files in `/tmp` manually — `@TempDir` ensures cleanup on test failure

## Architecture Tests (ArchUnit)
- Validate package dependencies don't violate layering:
  ```java
  @ArchTest
  ArchRule no_integration_in_engine =
      noClasses().that().resideInPackage("..engine..")
                 .should().dependOnClassesThat()
                 .resideInPackage("..integration..");
  ```
- Check HYPER_STANDARDS rules structurally (no mock imports in src/):
  ```java
  @ArchTest
  ArchRule no_mockito_in_src =
      noClasses().that().resideInPackage("..yawl..")
                 .and().resideOutsideOfPackage("..test..")
                 .should().dependOnClassesThat()
                 .resideInPackage("org.mockito..");
  ```
- Run ArchUnit tests in `yawl-engine` module's test suite

## Test Naming
- Format: `methodUnderTest_condition_expectedBehavior`
- Example: `executeNet_withMissingToken_throwsYStateSpaceException`
- JUnit 5 `@DisplayName` for human-readable reports when method name is insufficient

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
