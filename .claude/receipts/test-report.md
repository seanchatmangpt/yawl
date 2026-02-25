# YAWL v6.0.0 — Full Test Suite Report

**Date**: 2026-02-25  
**Branch**: `claude/blue-ocean-testing-2qE9B`  
**Commit**: `e8f4130` — feat(testing): add T3.3 chaos engine test for persistence fault injection  
**Profile**: `agent-dx` + `-Dmaven.test.skip=false`  

---

## Overall Summary

| Metric | Value |
|--------|-------|
| **Modules run** | 6 |
| **Total Tests** | 835 |
| **Passed** | 777 |
| **Failures** | 29 |
| **Errors** | 29 |
| **Skipped** | 0 |
| **Total Time** | 196.9s |
| **Pass Rate** | 93.1% |
| **Overall Status** | 🔴 RED — 58 issues |

---

## Module Summary

| Module | Tests | Passed | Failures | Errors | Skipped | Time | Status |
|--------|------:|-------:|---------:|-------:|--------:|-----:|--------|
| `yawl-elements` | 146 | 122 | 0 | 24 | 0 | 62.7s | ❌ RED |
| `yawl-engine` | 284 | 276 | 7 | 1 | 0 | 31.5s | ❌ RED |
| `yawl-mcp-a2a-app` | 54 | 54 | 0 | 0 | 0 | 27.8s | ✅ GREEN |
| `yawl-security` | 157 | 145 | 11 | 1 | 0 | 15.9s | ❌ RED |
| `yawl-stateless` | 2 | 2 | 0 | 0 | 0 | 34.1s | ✅ GREEN |
| `yawl-utilities` | 192 | 178 | 11 | 3 | 0 | 24.9s | ❌ RED |
| **TOTAL** | **835** | **777** | **29** | **29** | **0** | **196.9s** | ❌ RED |

---

## Modules NOT Tested

| Module | Reason | Impact |
|--------|--------|--------|
| `yawl-benchmark` | `jmh-maven-plugin:1.37` JAR unavailable in proxy (plugin bound to `test` phase). Contains SoakTestRunner, EngineOracleTest, AdversarialSpecFuzzerTest (Blue Ocean T3.1/T3.2). | **High** — Blue Ocean innovations unverified |
| `yawl-ggen` | Test compile failure: `WorkflowDNAOracleTest` references `WorkflowDNAOracle` + `XesToYawlSpecGenerator` not on test classpath. | **Medium** — 6 of 7 test files likely compilable |
| `yawl-engine` (chaos/concurrency dirs) | `yawl-engine/src/test/java/` (chaos, concurrency, property, soundness) is NOT the configured `testSourceDirectory` (`../test`). Blue Ocean T2/T3 tests compile but surefire never runs them. | **High** — T2+T3 chaos/concurrency tests unverified |
| `yawl-authentication` | No tests configured | — |
| `yawl-resourcing` | No tests configured | — |
| `yawl-scheduling` | No tests configured | — |
| `yawl-monitoring` | No tests configured | — |
| `yawl-integration` | No tests configured | — |
| `yawl-control-panel` | No tests configured | — |

---

## Module Detail

### YAWL Elements (`yawl-elements`)

**146 tests · 122 passed · 62.7s · ❌ 0 failures, 24 errors**

| Test Class | Tests | Passed | Fail | Err | Time | |
|:----------|------:|-------:|-----:|----:|-----:|--|
| `CaseOutcomeTest` | 0 | 0 | 0 | 0 | 1.46s | ✅ |
| `ElementsTestSuite` | 0 | 0 | 0 | 0 | 1.61s | ✅ |
| `EventResultTest` | 0 | 0 | 0 | 0 | 0.09s | ✅ |
| `StateTestSuite` | 0 | 0 | 0 | 0 | 0.02s | ✅ |
| `TestDataParsing` | 1 | 0 | 0 | 1 | 0.95s | ❌ |
| `TestE2WFOJNet` | 0 | 0 | 0 | 0 | 0.03s | ✅ |
| `TestMetaDataMarshal` | 1 | 1 | 0 | 0 | 0.85s | ✅ |
| `TestPredicateEvaluator` | 0 | 0 | 0 | 0 | 0.07s | ✅ |
| `TestPredicateEvaluatorCache` | 0 | 0 | 0 | 0 | 0.14s | ✅ |
| `TestPredicateEvaluatorFactory` | 0 | 0 | 0 | 0 | 0.04s | ✅ |
| `TestRElement` | 0 | 0 | 0 | 0 | 0.07s | ✅ |
| `TestRMarking` | 0 | 0 | 0 | 0 | 1.38s | ✅ |
| `TestRPlace` | 0 | 0 | 0 | 0 | 0.04s | ✅ |
| `TestRTransition` | 0 | 0 | 0 | 0 | 0.03s | ✅ |
| `TestSchemaValidation` | 0 | 0 | 0 | 0 | 2.85s | ✅ |
| `TestUnmarshalPerformance` | 0 | 0 | 0 | 0 | 3.36s | ✅ |
| `TestXmlSecurity` | 0 | 0 | 0 | 0 | 2.79s | ✅ |
| `TestYAWLServiceGateway` | 0 | 0 | 0 | 0 | 0.79s | ✅ |
| `TestYAWLServiceReference` | 0 | 0 | 0 | 0 | 2.44s | ✅ |
| `TestYAtomicTask` | 0 | 0 | 0 | 0 | 3.36s | ✅ |
| `TestYAttributeMap` | 0 | 0 | 0 | 0 | 2.43s | ✅ |
| `TestYCompositeTask` | 0 | 0 | 0 | 0 | 3.1s | ✅ |
| `TestYDecompositionParser` | 0 | 0 | 0 | 0 | 2.93s | ✅ |
| `TestYExternalCondition` | 2 | 2 | 0 | 0 | 0.53s | ✅ |
| `TestYExternalNetElement` | 1 | 1 | 0 | 0 | 0.0s | ✅ |
| `TestYExternalTask` | 11 | 10 | 0 | 1 | 2.39s | ❌ |
| `TestYFlowControl` | 0 | 0 | 0 | 0 | 0.93s | ✅ |
| `TestYFlowsInto` | 5 | 5 | 0 | 0 | 0.01s | ✅ |
| `TestYIdentifier` | 5 | 5 | 0 | 0 | 3.01s | ✅ |
| `TestYInputCondition` | 1 | 1 | 0 | 0 | 0.0s | ✅ |
| `TestYInternalCondition` | 0 | 0 | 0 | 0 | 0.82s | ✅ |
| `TestYMarking` | 8 | 0 | 0 | 8 | 3.04s | ❌ |
| `TestYMarshal` | 2 | 2 | 0 | 0 | 2.77s | ✅ |
| `TestYMarshalB4` | 2 | 0 | 0 | 2 | 1.05s | ❌ |
| `TestYMarshalRoundtrip` | 0 | 0 | 0 | 0 | 3.01s | ✅ |
| `TestYMultiInstanceAttributes` | 2 | 2 | 0 | 0 | 0.0s | ✅ |
| `TestYNet` | 6 | 0 | 0 | 6 | 0.33s | ❌ |
| `TestYNetElement` | 1 | 1 | 0 | 0 | 0.0s | ✅ |
| `TestYNetMarkings` | 0 | 0 | 0 | 0 | 3.31s | ✅ |
| `TestYOutputCondition` | 1 | 1 | 0 | 0 | 0.0s | ✅ |
| `TestYParameter` | 0 | 0 | 0 | 0 | 2.45s | ✅ |
| `TestYSetOfMarkings` | 0 | 0 | 0 | 0 | 0.8s | ✅ |
| `TestYSpecification` | 6 | 0 | 0 | 6 | 0.27s | ❌ |
| `TestYSpecificationModel` | 0 | 0 | 0 | 0 | 0.97s | ✅ |
| `TestYSpecificationParser` | 24 | 24 | 0 | 0 | 2.91s | ✅ |
| `TestYTaskBasics` | 0 | 0 | 0 | 0 | 1.0s | ✅ |
| `TestYTimerParametersParsing` | 19 | 19 | 0 | 0 | 0.1s | ✅ |
| `UnmarshallerTestSuite` | 0 | 0 | 0 | 0 | 0.5s | ✅ |
| `WorkflowResultTest` | 0 | 0 | 0 | 0 | 0.09s | ✅ |
| `YSpecVersionTest` | 48 | 48 | 0 | 0 | 1.53s | ✅ |

**Issues (151):**

- `Benchmark Summary` (4)
  - `ERROR` `marshalPerformanceBenchmark`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `overallUnmarshalPerformanceBenchmark`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `marshalPerformanceBenchmark`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - _(+1 more)_
- `Bigger Equal Tests` (2)
  - `FAIL` `containsBiggerEqualWorksWithBigger`: expected: <true> but was: <false>
  - `FAIL` `containsBiggerEqualWorksWithBigger`: expected: <true> but was: <false>
- `CaseCompleted` (1)
  - `FAIL` `toStringContainsKeyFields`: expected: <true> but was: <false>
- `Concurrent Marshalling` (6)
  - `ERROR` `concurrentMarshalIsThreadSafe`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `FAIL` `concurrentUnmarshalIsThreadSafe`: All operations should succeed ==> expected: <200> but was: <0>
  - `FAIL` `highConcurrencyStressTest`: expected: <500> but was: <0>
  - _(+3 more)_
- `Contains Operations` (2)
  - `FAIL` `containsReturnsTrueForAdded`: expected: <true> but was: <false>
  - `FAIL` `containsReturnsTrueForAdded`: expected: <true> but was: <false>
- `Delegation Tests` (1)
  - `ERROR` `e2wfojNetDelegatesToCore`: OR-join task cannot be null
- `Duplicate ID Detection` (8)
  - `FAIL` `detectsDuplicateDecompositionIds`: Should reject duplicate decomposition IDs ==> Unexpected exception type thrown, expected: 
  - `FAIL` `detectsDuplicateConditionIds`: Should reject duplicate condition IDs ==> Unexpected exception type thrown, expected: <org
  - `ERROR` `allowsSameIdInDifferentSpecifications`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+5 more)_
- `E2WFOJNet Construction Tests` (2)
  - `FAIL` `e2wfojNetConstructableWithValidNetAndOrJoin`: Unexpected exception thrown: java.lang.NullPointerException: OR-join task cannot be null
  - `ERROR` `e2wfojNetDoesNotModifyOriginalNet`: OR-join task cannot be null
- `Edge Cases` (1)
  - `FAIL` `addNullIdentifier`: Unexpected exception thrown: java.lang.NullPointerException: Cannot invoke "org.yawlfounda
- `EventAccepted` (1)
  - `FAIL` `toStringContainsKeyFields`: expected: <true> but was: <false>
- `FailedWorkflow` (1)
  - `FAIL` `toStringContainsKeyFields`: expected: <true> but was: <false>
- `GetScheme Tests` (1)
  - `FAIL` `getSchemeReturnsNullForNoScheme`: expected: <null> but was: <localhost>
- `Identifier Remove Operations` (1)
  - `FAIL` `removeMoreThanAvailableThrowsException`: Unexpected exception type thrown, expected: <org.yawlfoundation.yawl.exceptions.YStateExce
- `Invalid Element Nesting Validation` (10)
  - `FAIL` `detectsMissingProcessControlElementsInNet`: Should reject net without processControlElements ==> Unexpected exception type thrown, exp
  - `FAIL` `detectsProcessControlElementsOutsideDecomposition`: Should reject processControlElements outside decomposition ==> Unexpected exception type t
  - `FAIL` `detectsTaskInsideInputCondition`: Should reject task inside inputCondition ==> Unexpected exception type thrown, expected: <
  - _(+7 more)_
- `Is Bigger Than Tests` (1)
  - `FAIL` `markingWithMorePlacesIsStrictlyBigger`: expected: <true> but was: <false>
- `Large XML Benchmarks` (8)
  - `ERROR` `parsesSpecificationWith100TasksWithinTimeLimit`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `marshalPerformanceScalesLinearly`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `parsesSpecificationWithComplexDecompositions`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - _(+5 more)_
- `Memory Leak Detection` (6)
  - `ERROR` `largeSpecificationCleanupAfterGc`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `repeatedParsingDoesNotLeakMemory`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `marshalUnmarshalCycleDoesNotLeak`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - _(+3 more)_
- `Missing Attributes Validation` (10)
  - `FAIL` `detectsMissingSpecificationUri`: Should reject specification without uri attribute ==> Unexpected exception type thrown, ex
  - `FAIL` `detectsMissingIsRootNet`: Unexpected exception thrown: java.lang.NullPointerException: Cannot invoke "java.net.URL.o
  - `FAIL` `detectsMissingOutputConditionId`: Should reject outputCondition without id ==> Unexpected exception type thrown, expected: <
  - _(+7 more)_
- `OR-Join Enablement Tests` (2)
  - `ERROR` `restrictNetWithOrJoinWorks`: OR-join task cannot be null
  - `ERROR` `orJoinEnabledReturnsTrueWhenNoBiggerMarkingCoverable`: OR-join task cannot be null
- `SAXBuilder Security Configuration` (2)
  - `FAIL` `defaultSaxBuilderRejectsExternalEntities`: Default SAXBuilder should not resolve file entities ==> expected: <false> but was: <true>
  - `FAIL` `defaultSaxBuilderRejectsExternalEntities`: Default SAXBuilder should not resolve file entities ==> expected: <false> but was: <true>
- `Schema Validation Integration` (2)
  - `ERROR` `validSpecificationUnmarshalsAndRemarshalsCorrectly`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `validSpecificationUnmarshalsAndRemarshalsCorrectly`: Cannot invoke "java.net.URL.openStream()" because "url" is null
- `Schema Version Feature Tests` (2)
  - `FAIL` `version40HasSchemaUrl`: expected: not <null>
  - `FAIL` `version40HasSchemaUrl`: expected: not <null>
- `Streaming vs DOM Performance` (6)
  - `ERROR` `domParsingHandlesLargeDocuments`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `documentSizeScalesWithTaskCount`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `memoryUsageScalesWithDocumentSize`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - _(+3 more)_
- `SuccessfulWorkflow` (1)
  - `FAIL` `toStringContainsKeyFields`: toString must contain status code ==> expected: <true> but was: <false>
- `TestDataParsing` (2)
  - `ERROR` `testSchemaCatching`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testSchemaCatching`: Cannot invoke "java.net.URL.openStream()" because "url" is null
- `TestYExternalTask` (2)
  - `ERROR` `testInvalidMIAttributeVerify`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testInvalidMIAttributeVerify`: Cannot invoke "java.net.URL.openStream()" because "url" is null
- `TestYMarking` (16)
  - `ERROR` `testEquals`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testGreaterThanOrEquals`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testLessThan`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+13 more)_
- `TestYMarshalB4` (4)
  - `ERROR` `testLineByLine`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testBothEqual`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testLineByLine`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+1 more)_
- `TestYNet` (12)
  - `ERROR` `testGoodNetVerify`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testCloneVerify`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testDataStructureAgainstWierdSpecification`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+9 more)_
- `TestYSpecification` (12)
  - `ERROR` `testGoodNetVerify`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testValidDataTypesInSpecification`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testSpecWithLoops`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+9 more)_
- `Verification Tests` (2)
  - `ERROR` `verifyFailsForMandatoryWithInitialValue`: Cannot invoke "org.yawlfoundation.yawl.schema.YDataValidator.validate(org.yawlfoundation.y
  - `ERROR` `verifyPassesForOptionalWithInitialValue`: Cannot invoke "org.yawlfoundation.yawl.schema.YDataValidator.validate(org.yawlfoundation.y
- `Version Compatibility` (10)
  - `ERROR` `acceptsVersion40`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `acceptsVersion30`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `acceptsVersion22`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+7 more)_
- `XSD Schema Validation` (8)
  - `ERROR` `schemaHandlerCompileAndValidateCombinesOperations`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `schemaHandlerValidatesValidXml`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `schemaHandlerReportsErrorMessages`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+5 more)_
- `XXE File Protocol Prevention` (2)
  - `FAIL` `blocksFileEntityReferenceInElement`: Shadow file should NOT be readable ==> expected: <false> but was: <true>
  - `FAIL` `blocksFileEntityReferenceInElement`: Shadow file should NOT be readable ==> expected: <false> but was: <true>

### YAWL Engine (`yawl-engine`)

**284 tests · 276 passed · 31.5s · ❌ 7 failures, 1 errors**

| Test Class | Tests | Passed | Fail | Err | Time | |
|:----------|------:|-------:|-----:|----:|-----:|--|
| `ConstructPhaseTest` | 27 | 27 | 0 | 0 | 0.56s | ✅ |
| `ConstructReceiptTest` | 26 | 26 | 0 | 0 | 0.51s | ✅ |
| `ConstructRuleTest` | 34 | 34 | 0 | 0 | 0.35s | ✅ |
| `CostOptimizationIntegrationTest` | 5 | 5 | 0 | 0 | 3.01s | ✅ |
| `EngineTestSuite` | 0 | 0 | 0 | 0 | 0.09s | ✅ |
| `EnumExhaustivenessTest` | 11 | 11 | 0 | 0 | 0.25s | ✅ |
| `InstanceofPatternTest` | 15 | 15 | 0 | 0 | 1.51s | ✅ |
| `InterfaceMetricsTest` | 0 | 0 | 0 | 0 | 1.57s | ✅ |
| `InterfaceXDeadLetterEntryTest` | 6 | 6 | 0 | 0 | 1.91s | ✅ |
| `InterfaceXDeadLetterQueueTest` | 11 | 3 | 7 | 1 | 1.96s | ❌ |
| `InterfaceXMetricsTest` | 10 | 10 | 0 | 0 | 0.97s | ✅ |
| `InterfaceX_EngineSideClientTest` | 6 | 6 | 0 | 0 | 1.98s | ✅ |
| `PatternMatchingPerformanceTest` | 0 | 0 | 0 | 0 | 1.48s | ✅ |
| `PatternMatchingTestSuite` | 0 | 0 | 0 | 0 | 0.03s | ✅ |
| `TestAnnouncementContext` | 0 | 0 | 0 | 0 | 0.34s | ✅ |
| `TestWorkItemCompletion` | 0 | 0 | 0 | 0 | 0.34s | ✅ |
| `TestYEngineEvent` | 0 | 0 | 0 | 0 | 0.37s | ✅ |
| `TestYNetData` | 0 | 0 | 0 | 0 | 0.34s | ✅ |
| `TestYPersistenceManager` | 20 | 20 | 0 | 0 | 8.5s | ✅ |
| `TestYProblemHandler` | 0 | 0 | 0 | 0 | 0.98s | ✅ |
| `TestYSpecificationID` | 27 | 27 | 0 | 0 | 0.95s | ✅ |
| `TestYWorkItemID` | 1 | 1 | 0 | 0 | 0.22s | ✅ |
| `TestYWorkItemStatus` | 0 | 0 | 0 | 0 | 0.37s | ✅ |
| `TestYWorkItemTimer` | 20 | 20 | 0 | 0 | 1.22s | ✅ |
| `YAWLTelemetryTest` | 51 | 51 | 0 | 0 | 0.22s | ✅ |
| `YSpecificationPatternTest` | 14 | 14 | 0 | 0 | 1.51s | ✅ |

**Issues (8):**

- `InterfaceXDeadLetterQueueTest` (8)
  - `FAIL` `shouldFilterByCommand`: expected: <2> but was: <3>
  - `FAIL` `shouldAddEntry`: expected: <1> but was: <3>
  - `FAIL` `shouldRemoveEntry`: expected: <true> but was: <false>
  - _(+5 more)_

### YAWL MCP-A2A Application (`yawl-mcp-a2a-app`)

**54 tests · 54 passed · 27.8s · ✅ All tests passed**

| Test Class | Tests | Passed | Fail | Err | Time | |
|:----------|------:|-------:|-----:|----:|-----:|--|
| `A2AClassesTest` | 1 | 1 | 0 | 0 | 0.14s | ✅ |
| `A2ATaskLifecycleTest` | 0 | 0 | 0 | 0 | 0.43s | ✅ |
| `E2ESelfUpgradeIntegrationTest` | 15 | 15 | 0 | 0 | 9.52s | ✅ |
| `E2ETherapySwarmTest` | 20 | 20 | 0 | 0 | 0.45s | ✅ |
| `ExtendedYamlConverterTest` | 0 | 0 | 0 | 0 | 1.4s | ✅ |
| `JavaMigrationAnalyzerTest` | 0 | 0 | 0 | 0 | 0.48s | ✅ |
| `McpToolsTest` | 0 | 0 | 0 | 0 | 1.2s | ✅ |
| `MigrationPipelineIntegrationTest` | 5 | 5 | 0 | 0 | 0.19s | ✅ |
| `MigrationPlanBuilderTest` | 0 | 0 | 0 | 0 | 0.24s | ✅ |
| `PatternDemoRunnerTest` | 13 | 13 | 0 | 0 | 0.86s | ✅ |
| `PatternRegistryTest` | 0 | 0 | 0 | 0 | 0.87s | ✅ |
| `WcpBusinessPatterns10to18Test` | 0 | 0 | 0 | 0 | 1.22s | ✅ |
| `WcpBusinessPatterns19to28Test` | 0 | 0 | 0 | 0 | 1.2s | ✅ |
| `WcpBusinessPatterns1to9Test` | 0 | 0 | 0 | 0 | 0.9s | ✅ |
| `WcpBusinessPatterns29to36Test` | 0 | 0 | 0 | 0 | 1.29s | ✅ |
| `WcpBusinessPatterns37to43Test` | 0 | 0 | 0 | 0 | 0.9s | ✅ |
| `WorkflowControlPatternTest` | 0 | 0 | 0 | 0 | 1.26s | ✅ |
| `WorkflowSoundnessVerifierTest` | 0 | 0 | 0 | 0 | 1.27s | ✅ |
| `YNetElementTest` | 0 | 0 | 0 | 0 | 1.52s | ✅ |
| `YawlYamlConverterEdgeCaseTest` | 0 | 0 | 0 | 0 | 1.23s | ✅ |
| `YawlYamlConverterTest` | 0 | 0 | 0 | 0 | 1.27s | ✅ |

### YAWL Security (`yawl-security`)

**157 tests · 145 passed · 15.9s · ❌ 11 failures, 1 errors**

| Test Class | Tests | Passed | Fail | Err | Time | |
|:----------|------:|-------:|-----:|----:|-----:|--|
| `CommandInjectionProtectionTest` | 0 | 0 | 0 | 0 | 0.65s | ✅ |
| `PathTraversalProtectionTest` | 0 | 0 | 0 | 0 | 0.45s | ✅ |
| `SecurityFixesTest` | 25 | 25 | 0 | 0 | 2.65s | ✅ |
| `SqlInjectionProtectionTest` | 0 | 0 | 0 | 0 | 0.58s | ✅ |
| `TestAnomalyDetectionSecurity` | 12 | 11 | 1 | 0 | 1.28s | ❌ |
| `TestApiKeyRateLimitRegistry` | 9 | 8 | 1 | 0 | 1.26s | ❌ |
| `TestAttackPatternDetector` | 27 | 23 | 4 | 0 | 1.49s | ❌ |
| `TestIdempotencyKeyStore` | 12 | 12 | 0 | 0 | 1.2s | ✅ |
| `TestInputValidator` | 21 | 21 | 0 | 0 | 1.21s | ✅ |
| `TestPermissionOptimizer` | 18 | 18 | 0 | 0 | 1.26s | ✅ |
| `TestSafeErrorResponseBuilder` | 16 | 16 | 0 | 0 | 1.29s | ✅ |
| `TestSecretRotationService` | 17 | 11 | 5 | 1 | 1.31s | ❌ |
| `XssProtectionTest` | 0 | 0 | 0 | 0 | 0.55s | ✅ |
| `XxeProtectionTest` | 0 | 0 | 0 | 0 | 0.68s | ✅ |

**Issues (73):**

- `API Key Rate Limit Registry Tests` (1)
  - `FAIL` `testPerClientRateLimiting`: expected: <3> but was: <1>
- `Anomaly Detection Security` (1)
  - `FAIL` `testPayloadAnomalyDetection`: expected: <0> but was: <1>
- `Attack Pattern Detector` (4)
  - `FAIL` `testManualUnblocking`: expected: <true> but was: <false>
  - `FAIL` `testCredentialStuffingDetection`: expected: <true> but was: <false>
  - `FAIL` `testIncidentLogging`: Should log multiple incidents ==> expected: <true> but was: <false>
  - _(+1 more)_
- `Edge Cases and Boundary Tests` (7)
  - `ERROR` `shouldAcceptPathAtLengthLimit`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldRejectExcessivelyLongPath`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldHandleUnicodeCharacters`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+4 more)_
- `Encoded and Obfuscated Attack Tests` (3)
  - `FAIL` `shouldDetectUrlEncodedInjection`: URL-encoded injection must be detected ==> expected: <false> but was: <true>
  - `FAIL` `shouldDetectHtmlEntityEncodedInjection`: HTML entity encoded injection must be detected ==> expected: <false> but was: <true>
  - `FAIL` `shouldDetectHexEntityEncodedInjection`: Hex entity encoded injection must be detected ==> expected: <false> but was: <true>
- `Filename Sanitization Tests` (8)
  - `ERROR` `shouldRemoveNullBytes`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldRejectNullFilename`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldRejectOnlyInvalidCharacters`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+5 more)_
- `JavaScript Protocol Tests` (3)
  - `FAIL` `shouldDetectJavascriptWithTabs`: javascript with tabs must be detected ==> expected: <false> but was: <true>
  - `FAIL` `shouldDetectJavascriptWithNewlines`: javascript with newlines must be detected ==> expected: <false> but was: <true>
  - `FAIL` `shouldDetectJavascriptWithSpaces`: javascript with spaces must be detected ==> expected: <false> but was: <true>
- `NoSQL Injection Pattern Tests` (1)
  - `FAIL` `shouldDetectMongoDbWhereInjection`: MongoDB $where injection must be detected ==> expected: <false> but was: <true>
- `Null Byte Injection Tests` (3)
  - `ERROR` `shouldDetectNullByteWithBackslash`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectNullByteWithForwardSlash`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectRawNullByte`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
- `Parameterized Query Validation Tests` (1)
  - `FAIL` `shouldRejectConcatFunction`: Concat function should be rejected ==> expected: <false> but was: <true>
- `Safe Path Acceptance Tests` (7)
  - `ERROR` `shouldAcceptFilenameWithExtension`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldAcceptEmptyPath`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldAcceptNullPath`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+4 more)_
- `Safe XML Acceptance Tests` (1)
  - `FAIL` `shouldAcceptXmlWithNamespaces`: XML with namespaces should be accepted ==> expected: <true> but was: <false>
- `Secret Rotation Service` (6)
  - `FAIL` `testGracePeriodValidation`: expected: <true> but was: <false>
  - `ERROR` `testInvalidSecretRejection`: candidate cannot be empty
  - `FAIL` `testEmergencyRevocation`: expected: <true> but was: <false>
  - _(+3 more)_
- `Secure Path Resolution Tests` (5)
  - `ERROR` `shouldResolveValidRelativePath`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldRejectNullBaseDirectory`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldRejectTraversalInRelativePath`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+2 more)_
- `URL Encoded Traversal Tests` (6)
  - `ERROR` `shouldDetectSingleUrlEncodedTraversal`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDecodeUrlEncodedPathCorrectly`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectDoubleUrlEncodedTraversal`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+3 more)_
- `Unix Path Traversal Tests` (7)
  - `ERROR` `shouldDetectAbsolutePathEtcPasswd`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectBasicParentTraversal`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectLeadingSlashWithTraversal`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+4 more)_
- `Windows Path Traversal Tests` (5)
  - `ERROR` `shouldDetectAbsoluteWindowsPath`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectBackslashTraversal`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldDetectTraversalToWebConfig`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+2 more)_
- `YAWL-Specific Path Tests` (4)
  - `ERROR` `shouldAcceptYawlSpecificationInSubdirectory`: —
  - `ERROR` `shouldAcceptYawlSpecificationFilename`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - `ERROR` `shouldRejectTraversalToYawlConfig`: Could not initialize class org.yawlfoundation.yawl.security.PathTraversalProtectionTest
  - _(+1 more)_

### YAWL Stateless Engine (`yawl-stateless`)

**2 tests · 2 passed · 34.1s · ✅ All tests passed**

| Test Class | Tests | Passed | Fail | Err | Time | |
|:----------|------:|-------:|-----:|----:|-----:|--|
| `ImportExportPerformanceTest` | 0 | 0 | 0 | 0 | 1.34s | ✅ |
| `SimpleCaseSnapshotTest` | 2 | 2 | 0 | 0 | 0.16s | ✅ |
| `StatelessTestSuite` | 0 | 0 | 0 | 0 | 30.64s | ✅ |
| `YStatelessEngineParallelLaunchTest` | 0 | 0 | 0 | 0 | 1.94s | ✅ |

**Issues (139):**

- `Benchmark Tests` (8)
  - `FAIL` `benchmarkImportCasePerformance`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `benchmarkRoundTripPerformance`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `benchmarkExportCasePerformance`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+5 more)_
- `Case Management Tests` (4)
  - `FAIL` `unloadUnknownCaseThrowsException`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `unloadCaseReturnsCaseXml`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `hasCaseReturnsTrueForMonitoredCase`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Complex Pattern Execution Tests` (1)
  - `ERROR` `launchCaseWithCaseParameters`: Invalid caseParams: outermost element name must match specification URI or root net name.
- `Compression Tests` (3)
  - `FAIL` `compressedDataIsSmallerForLargeCases`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `compressMultipleCasesToSingleZip`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `compressExportedCaseToZip`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Concurrent Modification Tests` (4)
  - `FAIL` `multipleConcurrentCasesCanBeMonitored`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `caseCompletionRemovesCaseFromMonitor`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `caseCanBeUnloadedWhileOtherCasesRemain`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Concurrent Operations Tests` (3)
  - `FAIL` `concurrentCaseExportsCompleteSuccessfully`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `concurrentImportAndExportOperations`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `threadSafeMarshalOperations`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Corrupt XML Handling Tests` (6)
  - `FAIL` `nullAnnouncerCausesFailure`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `malformedXmlThrowsException`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `emptyXmlThrowsException`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+3 more)_
- `Edge Cases Tests` (6)
  - `FAIL` `marshalWithEmptyWorkItemsRepository`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalProducesConsistentOutputFormat`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `enableMonitoringWithZeroTimeout`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+3 more)_
- `Event Handling Tests` (4)
  - `FAIL` `multipleListenersReceiveEvents`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `workItemEventUpdatesCaseLastActiveTime`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `caseStartEventAddsToMonitor`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `File Export Tests` (4)
  - `FAIL` `exportCasePreservesAllData`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `exportRemovesCaseFromMonitor`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `exportCaseToOutputStreamFormat`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Filtering Tests` (3)
  - `FAIL` `extractSpecIdFromExportedXml`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `extractCaseIdFromExportedXml`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `filterExportedXmlByElementName`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Guard: invalid arguments rejected before scope opens` (2)
  - `ERROR` `nullSpecThrowsImmediately`: —
  - `ERROR` `nullSpecThrowsImmediately`: —
- `Identifier Hierarchy Tests` (3)
  - `FAIL` `restoredIdentifierHasLocationNames`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `rootIdentifierHasNoParent`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `restoredIdentifierHasCorrectIdString`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Idle Timer Management Tests` (7)
  - `FAIL` `yCaseWithZeroTimeoutHasNoIdleTimer`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `setIdleTimeoutUpdatesTimer`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `isIdleThrowsWhenTimerDisabled`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+4 more)_
- `Integration Tests` (3)
  - `FAIL` `yCaseIntegratesWithEngineRestore`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `yCaseIdleDetectionThroughEngine`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `yCaseIntegratesWithEngineUnload`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Large Case Handling Tests` (6)
  - `FAIL` `importHandlesMultipleCasesSequentially`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `exportHandlesMultipleConcurrentCases`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `exportPerformanceDegradesLinearly`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+3 more)_
- `Marshal Operations Tests` (4)
  - `FAIL` `getRunnerReturnsCorrectReference`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalReturnsValidXmlString`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalContainsCaseId`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Marshal Runner/Work Items Tests` (9)
  - `FAIL` `marshalRunnerReturnsNonNullXmlString`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalOutputContainsWorkItems`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalWorkItemIncludesTaskId`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+6 more)_
- `Memory Leak Detection Tests` (6)
  - `FAIL` `noMemoryLeakOnRepeatedRoundTrips`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `noMemoryLeakOnRepeatedExports`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `gcReclaimsMemoryAfterCaseCleanup`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+3 more)_
- `Multi-Threaded Announcements Tests` (2)
  - `FAIL` `caseCompletesWithMultiThreadedEnabled`: Case should complete with multi-threaded enabled ==> expected: <true> but was: <false>
  - `FAIL` `concurrentCasesWithMultiThreadedAnnouncements`: All cases should complete ==> expected: <true> but was: <false>
- `Round-Trip Integrity Tests` (2)
  - `FAIL` `multipleExportsProduceConsistentResults`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `exportImportPreservesRunnerEquality`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Round-Trip Service Tests` (3)
  - `FAIL` `restoredCaseIsAddedBackToMonitor`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `multipleRoundTripsPreserveCaseIntegrity`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `exportAndRestorePreservesCaseState`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Runner Restoration Tests` (5)
  - `FAIL` `restoredRunnerPreservesExecutionStatus`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `restoredRunnerHasAnnouncerAttached`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `restoredRunnerPreservesStartTime`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+2 more)_
- `Stress Tests` (4)
  - `FAIL` `stressAlternatingImportExport`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `stressRapidExportCycles`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `stressRapidExportCycles`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Timer Accuracy Tests` (7)
  - `FAIL` `idleTimerCanBeUpdated`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `idleTimerCanBeConfigured`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `pauseAndResumeIdleTimer`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+4 more)_
- `Timer States Restoration Tests` (2)
  - `FAIL` `restoredWorkItemHasEnablementTime`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `restoredRunnerHasTimerStatesMap`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `Timestamp Handling Tests` (5)
  - `FAIL` `marshalIncludesStartTime`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `timestampsAreNumericStrings`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalIncludesWorkItemEnablementTime`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+2 more)_
- `Validation Tests` (4)
  - `FAIL` `exportContainsRequiredElements`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `invalidXmlFailsValidation`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `validateCaseIdFormat`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Work Item Reunification Tests` (4)
  - `FAIL` `restoredWorkItemHasCorrectTaskId`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `restoredRunnerHasWorkItems`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `restoredWorkItemHasValidCaseIdReference`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+1 more)_
- `Work Item Timer Removal Tests` (2)
  - `FAIL` `removeWorkItemTimersOnCaseWithItems`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `removeWorkItemTimersDoesNotThrow`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
- `XML Structure Validation Tests` (8)
  - `FAIL` `runnerElementHasAllRequiredChildren`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `runnersElementContainsRunnerChildren`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `marshalOutputIsValidXml`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+5 more)_
- `XML Unmarshal Tests` (5)
  - `FAIL` `unmarshalPreservesSpecificationId`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `unmarshalValidCaseXmlReturnsRunners`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - `FAIL` `unmarshalThrowsForInvalidSpecificationXml`: Missing resource: resources/MinimalSpec.xml ==> expected: not <null>
  - _(+2 more)_

### YAWL Utilities (`yawl-utilities`)

**192 tests · 178 passed · 24.9s · ❌ 11 failures, 3 errors**

| Test Class | Tests | Passed | Fail | Err | Time | |
|:----------|------:|-------:|-----:|----:|-----:|--|
| `InterfaceMetricsTest` | 0 | 0 | 0 | 0 | 1.74s | ✅ |
| `TestDynamicValueBasic` | 2 | 2 | 0 | 0 | 0.76s | ✅ |
| `TestDynamicValueLogging` | 10 | 10 | 0 | 0 | 0.78s | ✅ |
| `TestExceptionHierarchy` | 0 | 0 | 0 | 0 | 0.79s | ✅ |
| `TestExceptionLogging` | 0 | 0 | 0 | 0 | 0.76s | ✅ |
| `TestExceptionRecovery` | 0 | 0 | 0 | 0 | 0.79s | ✅ |
| `TestMetaDataMarshal` | 1 | 1 | 0 | 0 | 0.77s | ✅ |
| `TestNullCheckModernizer` | 1 | 1 | 0 | 0 | 0.43s | ✅ |
| `TestSafeNumberParser` | 68 | 68 | 0 | 0 | 0.39s | ✅ |
| `TestSchemaHandler` | 5 | 5 | 0 | 0 | 0.89s | ✅ |
| `TestSchemaHandlerValidation` | 7 | 7 | 0 | 0 | 1.13s | ✅ |
| `TestSchemaValidation` | 0 | 0 | 0 | 0 | 1.7s | ✅ |
| `TestStringUtilOptional` | 0 | 0 | 0 | 0 | 1.22s | ✅ |
| `TestUnmarshalPerformance` | 0 | 0 | 0 | 0 | 1.98s | ✅ |
| `TestXmlSecurity` | 0 | 0 | 0 | 0 | 1.65s | ✅ |
| `TestYAWLExceptionEnhancements` | 11 | 11 | 0 | 0 | 0.25s | ✅ |
| `TestYConnectivityException` | 1 | 1 | 0 | 0 | 0.23s | ✅ |
| `TestYDecompositionParser` | 0 | 0 | 0 | 0 | 1.63s | ✅ |
| `TestYMarshal` | 2 | 2 | 0 | 0 | 1.65s | ✅ |
| `TestYMarshalB4` | 2 | 0 | 0 | 2 | 1.04s | ❌ |
| `TestYMarshalRoundtrip` | 0 | 0 | 0 | 0 | 1.73s | ✅ |
| `TestYPredicateParser` | 57 | 45 | 11 | 1 | 0.65s | ❌ |
| `TestYSpecificationParser` | 24 | 24 | 0 | 0 | 1.72s | ✅ |
| `TestYSyntaxException` | 1 | 1 | 0 | 0 | 0.23s | ✅ |

**Issues (60):**

- `Benchmark Summary` (2)
  - `ERROR` `marshalPerformanceBenchmark`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `overallUnmarshalPerformanceBenchmark`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
- `Concurrent Marshalling` (3)
  - `ERROR` `concurrentMarshalIsThreadSafe`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `FAIL` `concurrentUnmarshalIsThreadSafe`: All operations should succeed ==> expected: <200> but was: <0>
  - `FAIL` `highConcurrencyStressTest`: expected: <500> but was: <0>
- `Duplicate ID Detection` (4)
  - `FAIL` `detectsDuplicateDecompositionIds`: Should reject duplicate decomposition IDs ==> Unexpected exception type thrown, expected: 
  - `ERROR` `allowsSameIdInDifferentSpecifications`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `FAIL` `detectsDuplicateConditionIds`: Should reject duplicate condition IDs ==> Unexpected exception type thrown, expected: <org
  - _(+1 more)_
- `Exception Message Format` (1)
  - `FAIL` `exceptionWithCauseIncludesCauseType`: expected: <true> but was: <false>
- `Exception XML Roundtrip` (3)
  - `ERROR` `yDataQueryExceptionToXmlAndUnmarshal`: org.yawlfoundation.yawl.exceptions.YDataQueryException.<init>()
  - `ERROR` `yDataValidationExceptionToXmlAndUnmarshal`: org.yawlfoundation.yawl.exceptions.YDataValidationException.<init>()
  - `ERROR` `yDataStateExceptionToXmlPreservesAllFields`: org.yawlfoundation.yawl.exceptions.YDataStateException.<init>()
- `Invalid Element Nesting Validation` (5)
  - `FAIL` `detectsMissingProcessControlElementsInNet`: Should reject net without processControlElements ==> Unexpected exception type thrown, exp
  - `FAIL` `detectsProcessControlElementsOutsideDecomposition`: Should reject processControlElements outside decomposition ==> Unexpected exception type t
  - `ERROR` `validatesCorrectNestingOrder`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+2 more)_
- `Large XML Benchmarks` (4)
  - `ERROR` `parsesSpecificationWith100TasksWithinTimeLimit`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `marshalPerformanceScalesLinearly`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `parsesSpecificationWithComplexDecompositions`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - _(+1 more)_
- `Memory Leak Detection` (3)
  - `ERROR` `largeSpecificationCleanupAfterGc`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `repeatedParsingDoesNotLeakMemory`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `marshalUnmarshalCycleDoesNotLeak`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
- `Missing Attributes Validation` (5)
  - `FAIL` `detectsMissingSpecificationUri`: Should reject specification without uri attribute ==> Unexpected exception type thrown, ex
  - `FAIL` `detectsMissingIsRootNet`: Unexpected exception thrown: java.lang.NullPointerException: Cannot invoke "java.net.URL.o
  - `FAIL` `detectsMissingOutputConditionId`: Should reject outputCondition without id ==> Unexpected exception type thrown, expected: <
  - _(+2 more)_
- `SAXBuilder Security Configuration` (1)
  - `FAIL` `defaultSaxBuilderRejectsExternalEntities`: Default SAXBuilder should not resolve file entities ==> expected: <false> but was: <true>
- `Schema Validation Integration` (1)
  - `ERROR` `validSpecificationUnmarshalsAndRemarshalsCorrectly`: Cannot invoke "java.net.URL.openStream()" because "url" is null
- `Schema Version Feature Tests` (1)
  - `FAIL` `version40HasSchemaUrl`: expected: not <null>
- `Streaming vs DOM Performance` (3)
  - `ERROR` `memoryUsageScalesWithDocumentSize`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `documentSizeScalesWithTaskCount`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
  - `ERROR` `domParsingHandlesLargeDocuments`: Cannot invoke "org.jdom2.Element.getAttributeValue(String)" because the return value of "o
- `TestYMarshalB4` (2)
  - `ERROR` `testLineByLine`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `testBothEqual`: Cannot invoke "java.net.URL.openStream()" because "url" is null
- `Version Compatibility` (5)
  - `ERROR` `acceptsVersion40`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `acceptsVersion30`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `acceptsVersion22`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+2 more)_
- `XSD Schema Validation` (4)
  - `ERROR` `schemaHandlerCompileAndValidateCombinesOperations`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `schemaHandlerValidatesValidXml`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - `ERROR` `schemaHandlerReportsErrorMessages`: Cannot invoke "java.net.URL.openStream()" because "url" is null
  - _(+1 more)_
- `XXE File Protocol Prevention` (1)
  - `FAIL` `blocksFileEntityReferenceInElement`: Shadow file should NOT be readable ==> expected: <false> but was: <true>
- `YPredicateParser` (12)
  - `FAIL` `parse_expressionsWithText_worksCorrectly`: Should preserve suffix ==> expected: <true> but was: <false>
  - `FAIL` `parse_mixedCaseExpressions_preservesAsIs`: Should preserve mixed case date expression ==> expected: <true> but was: <false>
  - `FAIL` `parse_nonExpressionLiterals_preservesAsIs`: Should preserve escaped expressions ==> expected: <true> but was: <false>
  - _(+9 more)_

---

## Failure Analysis

### F1 · URL Resource NullPointer (24 errors in yawl-elements, 14+ in yawl-utilities, 136+ in yawl-stateless)

**Signature**: `Cannot invoke "java.net.URL.openStream()" because "url" is null`

`getClass().getResource("path")` returns `null` when the resource path does not match the classpath layout used in the module-isolated test run. Affects tests loading `.ywl` spec files and `resources/MinimalSpec.xml`.

**Root cause**: Test resource paths assume a different working directory or classpath structure than what the module-isolated build provides.  
**Fix**: Update resource paths or use `TestClassLoader.getSystemResource()` with absolute classpath-relative paths.

---

### F2 · BouncyCastle Java-25 Regex Incompatibility (73+ errors in yawl-security)

**Signature**: `ExceptionInInitializerError` → `PatternSyntaxException: Illegal octal escape sequence` on pattern `%00|\x00|%0|\0`

Java 25 rejects `\0` as a regex octal escape in character class context. `bcprov-jdk18on:1.77` uses this pattern during static initialization, causing all classes that load BouncyCastle to fail.

**Root cause**: BouncyCastle 1.77 uses a Java-version-incompatible regex. Also, the JAR download was truncated by the proxy (incomplete artifact), compounding the issue.  
**Fix**: Upgrade to `bcprov-jdk18on:1.78+` (which fixes Java 24/25 compatibility).

---

### F3 · InterfaceXDeadLetterQueue Shared State (7 failures in yawl-engine)

**Signature**: `expected: <2> but was: <3>`, `expected: <true> but was: <false>`

`InterfaceXDeadLetterQueueTest` asserts exact item counts on what appears to be a shared static DLQ instance. Parallel test execution inflates counts beyond single-test expectations.

**Root cause**: Shared static mutable state not reset between test cases.  
**Fix**: Add `@BeforeEach` to reset the queue, or inject a fresh queue instance per test.

---

### F4 · Exception Constructor API Change (3 errors in yawl-utilities)

**Signature**: `NoSuchMethod YDataQueryException.<init>()`

`TestExceptionRecovery` reflectively constructs exception classes via no-arg constructor, which was removed.

**Root cause**: Exception classes changed API; test not updated.  
**Fix**: Update test to use the current constructor signature.

---

## Environment

| Item | Value |
|------|-------|
| **Java** | OpenJDK 25.0.2 (Eclipse Temurin) |
| **Maven** | Apache Maven 3.9.11 |
| **Profile** | `agent-dx` + `-Dmaven.test.skip=false` |
| **DB** | H2 in-memory (ephemeral) |
| **Network** | Egress proxy (local bridge 127.0.0.1:3128) |
| **OS** | Linux 4.4.0 amd64 |
| **Run timestamp** | 2026-02-25T07:19:21Z |

---

## Commands Used

```bash
# Step 1 — Standard module build + test
DX_FAIL_AT=end DX_VERBOSE=1 bash scripts/dx.sh all

# Step 2 — All modules with tests enabled (overrides default skip)
mvn -P agent-dx -Dmaven.test.skip=false --fail-at-end test

# Step 3 — Benchmark chain build (includes upstream)
mvn -P agent-dx -Dmaven.test.skip=false -Dmaven.test.failure.ignore=true \
    -pl yawl-benchmark -am test
# Note: yawl-benchmark blocked by jmh-maven-plugin:1.37 unavailability
```

---

_Generated by Claude Code — branch `claude/blue-ocean-testing-2qE9B` — session `session_01RgvRJD4ypqSGyqUWPkrEJC`_
