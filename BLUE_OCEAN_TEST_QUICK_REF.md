# Blue Ocean Test Suite - Quick Reference Card

**Status**: Ready for execution
**Framework**: JUnit 5 (Chicago TDD, No mocks)
**Execution**: `mvn clean test -pl yawl-integration -k blue_ocean`

---

## Test Suite Overview

| Suite | File | Tests | Time | Focus |
|-------|------|-------|------|-------|
| **Scenarios** | BlueOceanScenarioTest | 6 | 5-10m | Real workflows |
| **Predictive** | PredictiveAccuracyTest | 8 | 3-5m | Forecast accuracy |
| **Scaling** | AsyncStandupScaleTest | 7 | 10-15m | Performance SLAs |
| **Integration** | BlueOceanIntegrationTest | 4 | 15-20m | End-to-end orchestration |

---

## Quick Commands

```bash
# All tests
mvn clean test -pl yawl-integration -k blue_ocean

# One suite
mvn clean test -pl yawl-integration -k BlueOceanScenarioTest
mvn clean test -pl yawl-integration -k PredictiveAccuracyTest
mvn clean test -pl yawl-integration -k AsyncStandupScaleTest
mvn clean test -pl yawl-integration -k BlueOceanIntegrationTest

# Coverage
mvn clean test -pl yawl-integration -k blue_ocean jacoco:report

# Debug
mvn clean test -pl yawl-integration -k BlueOceanScenarioTest#testFiveTeamTrainPIPlanning -X
```

---

## Test Scenarios

### 1. BlueOceanScenarioTest (6 tests)

**Scenario 1: 5-Team Train (100 people)**
- `testFiveTeamTrainPIPlanning()`
- Tests: PI planning, story estimation, team commitment
- SLA: <500ms execution, >90% decision accuracy

**Scenario 2: Distributed (3 time zones)**
- `testDistributedTrainAsyncStandup()`
- Tests: Async ceremonies across regions
- SLA: 0 sync meetings, <30s blocker latency

**Scenario 3: Complex Dependencies (100+ stories)**
- `testComplexDependencyNetworkResolution()`
- Tests: Dependency detection, validation
- SLA: 95%+ recall, <5% false positives

**Scenario 4: High-Velocity Team (120%+)**
- `testHighVelocityTeamPrediction()`
- Tests: Velocity prediction, optimization
- SLA: Trend detected, >80% confidence

**Scenario 5: Low-Velocity Team (60%)**
- `testLowVelocityTeamRiskDetection()`
- Tests: Risk detection, mitigation
- SLA: Risk flagged, >3 improvements

**Scenario 6: Full PI Cycle (3 sprints)**
- `testFullPICycleOrchestration()`
- Tests: Complete ceremony orchestration
- SLA: >90% objectives, auto escalation

### 2. PredictiveAccuracyTest (8 tests)

**Test 1: Story Completion (±10%)**
- `testStoryCompletionForecastAccuracy()`
- SLA: Prediction within ±10% of actual

**Test 2: Velocity Trend (±5%)**
- `testVelocityTrendDetection()`
- SLA: Trend within ±5% of actual

**Test 3: Risk Detection (90%+ sensitivity)**
- `testRiskDetectionSensitivity()`
- SLA: Detect 90%+ at-risk teams, <5% FP

**Test 4: Confidence Calibration (85%+ correlation)**
- `testConfidenceCalibration()`
- SLA: 50 predictions, >0.85 correlation

**Test 5: Multi-Sprint Stability**
- `testMultiSprintForecastStability()`
- SLA: <15% sprint-to-sprint change

**Test 6: Parametrized Trend Strength**
- `testVelocityPredictionByTrendStrength(int)`
- SLA: Accuracy improves with trend

### 3. AsyncStandupScaleTest (7 tests)

**Test 1: Baseline (100 participants)**
- `testAsyncStandupBaseline100()`
- SLA: <500ms, <50ms per participant

**Test 2: Scale (500 participants)**
- `testAsyncStandupAt500Scale()`
- SLA: <2s, <50ms per participant, <500MB

**Test 3: Extreme (1000 participants)**
- `testAsyncStandupAt1000Scale()`
- SLA: <5s, <50ms per participant, <1GB, >99%

**Test 4: Concurrent Ceremonies**
- `testConcurrentCeremonies()`
- SLA: 5 ceremonies in parallel time

**Test 5: Blocker Latency**
- `testBlockerDetectionLatencyAt500Scale()`
- SLA: <5s detect, <30s escalate

**Test 6: Parametrized Scaling (100-1000)**
- `testLatencyScaling(int)`
- SLA: <50ms per participant across scales

### 4. BlueOceanIntegrationTest (4 tests)

**Test 1: Full PI Cycle (3 sprints)**
- `testFullPICycleIntegration()`
- SLA: >90% objectives, <30s escalation, >85% accuracy

**Test 2: Cross-Ceremony Dependencies**
- `testCrossCeremonyDependencies()`
- SLA: All dependencies tracked, 0 missed issues

**Test 3: Predictive Insights**
- `testPredictiveInsightsDrivingDecisions()`
- SLA: Prediction drives decision, validated against actual

**Test 4: Automated Escalation**
- `testAutomatedBlockerEscalationWorkflow()`
- SLA: <30s escalation, correct routing, forecast updated

---

## Success Criteria by Feature

### Async Standup Ceremony
- **Execution time**: <2s for 500 people, <5s for 1000 people
- **Per-participant latency**: <50ms
- **Blocker detection**: 95%+ true positives, <5% false positives
- **Escalation latency**: <30 seconds
- **Memory footprint**: <1GB for 1000 concurrent

### Predictive Planning
- **Story completion forecast**: ±10% accuracy
- **Velocity trend**: ±5% accuracy
- **Risk detection**: 90%+ sensitivity, <5% false positives
- **Confidence calibration**: 85%+ correlation
- **Statistical significance**: p < 0.05

### Blocker Detection & Escalation
- **Detection rate**: 95%+ of actual blockers
- **False positive rate**: <5% of detected blockers
- **Escalation time**: <30 seconds
- **Correct routing**: 100% to appropriate person/team
- **Resolution tracking**: All escalations followed up

### Cross-Ceremony Orchestration
- **Dependency detection**: 95%+ recall
- **False positives**: <5%
- **PI objectives achieved**: >90%
- **Decision accuracy**: >85%
- **Team satisfaction**: >90%

---

## Helper Classes & Setup

```java
// In setUp() method
@BeforeEach
void setUp() {
    engine = YEngine.getInstance();
    assertNotNull(engine, "YEngine should be available");

    // Choose helper for test class
    scenarioBuilder = new SAFEScenarioBuilder(engine);        // BlueOceanScenario
    predictiveHelper = new PredictiveEngineTestHelper(engine); // PredictiveAccuracy
    standupHelper = new AsyncStandupTestHelper(engine);        // AsyncStandupScale
    orchestrator = new BlueOceanCeremonyOrchestrator(engine);  // Integration
}
```

---

## Common Test Patterns

### Building Test Data
```java
// Build train with N participants
SAFETrain train = helper.buildTrainWithParticipants(100);

// Generate backlog with dependencies
List<UserStory> stories = builder.generatePIBacklogWithDependencies(100, 50);

// Team with historical velocity
SAFETrain team = helper.buildTrainWithVelocitySeries(
    List.of(100, 103, 105, 108, 110, 113, 115, 118)
);

// Execute async standup
AsyncStandupResult result = helper.executeAsyncStandup(train, reports);
```

### Validating Results
```java
// Performance SLA
assertThat(elapsed).isLessThan(Duration.ofSeconds(2));

// Accuracy
double error = Math.abs(actual - predicted) / predicted;
assertThat(error).isLessThan(0.10);  // ±10%

// Latency
assertThat(blocker.escalationTime()).isLessThan(Duration.ofSeconds(30));

// Detection rate
double sensitivity = detectedCount / totalCount;
assertThat(sensitivity).isGreaterThan(0.90);
```

---

## Performance SLA Reference

| Metric | 100p | 500p | 1000p | SLA Test |
|--------|------|------|-------|----------|
| **Total time** | <500ms | <2s | <5s | AsyncStandupScale |
| **Per-participant** | <50ms | <50ms | <50ms | AsyncStandupScale |
| **Blocker detection** | - | <5s | <5s | AsyncStandupScale |
| **Escalation** | <30s | <30s | <30s | All tests |
| **Memory** | <100MB | <500MB | <1GB | AsyncStandupScale |

---

## Accuracy Validation Reference

| Metric | Target | Test |
|--------|--------|------|
| **Story completion** | ±10% | PredictiveAccuracyTest |
| **Velocity forecast** | ±5% | PredictiveAccuracyTest |
| **Risk sensitivity** | 90%+ | PredictiveAccuracyTest |
| **Risk FP rate** | <5% | PredictiveAccuracyTest |
| **Confidence correlation** | 85%+ | PredictiveAccuracyTest |
| **Dependency recall** | 95%+ | BlueOceanScenarioTest |
| **Dependency FP rate** | <5% | BlueOceanScenarioTest |
| **PI objectives** | >90% | All scenario tests |

---

## Debugging Tips

### Enable Detailed Logging
```bash
mvn clean test -pl yawl-integration -k BlueOceanScenarioTest \
  -Dlogback.configurationFile=/path/to/logback-debug.xml
```

### Run Single Test
```bash
mvn test -pl yawl-integration \
  -Dtest=BlueOceanScenarioTest#testFiveTeamTrainPIPlanning
```

### Profile Memory
```bash
mvn clean test -pl yawl-integration -k AsyncStandupScaleTest \
  -Xmx2g -XX:+UnlockDiagnosticVMOptions -XX:+PrintGCDetails
```

### Disable Parallel Execution
```bash
mvn clean test -pl yawl-integration -k blue_ocean \
  -Dorg.junit.jupiter.execution.parallel.enabled=false
```

---

## Test Execution Checklist

Before running tests:
- [ ] Java 25+ installed (`java -version`)
- [ ] Maven 3.9+ installed (`mvn -version`)
- [ ] YAWL engine can start (check logs)
- [ ] H2 database available (in-memory)
- [ ] Sufficient heap memory (2GB+ recommended)
- [ ] No other tests running (to avoid conflicts)

During test execution:
- [ ] Check test output for assertion failures
- [ ] Verify performance SLAs met (watch latency logs)
- [ ] Monitor memory usage (check GC patterns)
- [ ] Note any flaky tests (run 2-3 times if suspicious)

After test execution:
- [ ] Review coverage report (target: 80%+ line)
- [ ] Check all 25+ tests passed
- [ ] Validate SLA metrics met
- [ ] Compare against baseline (if re-running)

---

## Troubleshooting

| Issue | Cause | Fix |
|-------|-------|-----|
| Tests fail with "YEngine not available" | Engine not initialized | Check YEngine singleton |
| Timeout on large scale test | Virtual thread pool exhausted | Increase heap, check system resources |
| Blocker detection FP high | Sensitivity threshold too low | Adjust pattern regex, retrain model |
| Forecast accuracy <80% | Insufficient historical data | Use 8+ sprints for training |
| Memory usage >1GB | Many concurrent threads | Reduce participant count or batch |

---

## References

- **Full Strategy**: `/docs/BLUE_OCEAN_TEST_STRATEGY.md`
- **Test Guide**: `/src/test/java/org/yawlfoundation/yawl/blue_ocean/README.md`
- **Delivery Summary**: `/BLUE_OCEAN_TEST_DELIVERY_SUMMARY.md`
- **SAFe Integration**: `/docs/SAFe_Integration_Guide.md`
- **Chicago TDD**: `/docs/CHICAGO_TDD.md`

---

**Quick Reference Version**: 1.0
**Last Updated**: 2026-02-28
**Print this card and keep it handy during test execution!**
