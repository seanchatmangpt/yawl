# WCP-24 through WCP-28 Test Execution Guide

Quick reference for running and debugging the new workflow pattern iteration tests.

---

## Quick Start

### Run All New Tests

```bash
# Full suite (both test classes)
mvn test -Dtest=WorkflowPatternIterationTest,AdvancedIterationScenariosTest

# With fast compilation
bash scripts/dx.sh

# Or target just stateless module
bash scripts/dx.sh -pl yawl-stateless
```

### Run Specific Test Class

```bash
# Core integration tests only
mvn test -Dtest=WorkflowPatternIterationTest

# Advanced scenarios (edge cases, performance, cleanup)
mvn test -Dtest=AdvancedIterationScenariosTest
```

---

## Test Groupings by Pattern

### WCP-25: Cancel and Complete MI

```bash
mvn test -Dtest=WorkflowPatternIterationTest#Wcp25CancelCompleteMITests
```

**Tests:**
- `specificationLoadsSuccessfully` - XML unmarshalling
- `specContainsMultiInstanceTask` - Structure validation
- `caseExecutesToCompletion` - Basic execution
- `caseCompletesWithinPerformanceTarget` - Performance (<5s)
- `multipleInstancesCreated` - Dynamic MI creation
- `allInitiatedWorkItemsComplete` - Completion tracking

**Expected Outcome:** 6 tests pass, all within timeout

---

### WCP-26: Sequential MI Without A Priori Knowledge

```bash
mvn test -Dtest=WorkflowPatternIterationTest#Wcp26SequentialMITests
```

**Tests:**
- `specLoadsSuccessfully` - Loading
- `netContainsSequentialMITask` - Structure
- `caseExecutesSequentially` - Execution order
- `multipleInstancesSequential` - Instance counting
- `finalizeExecutesAfterInstances` - Task ordering
- `performanceTarget` - <8s completion

**Expected Outcome:** 6 tests pass, linear scaling observed

---

### WCP-27: Concurrent MI Without A Priori Knowledge

```bash
mvn test -Dtest=WorkflowPatternIterationTest#Wcp27ConcurrentMITests
```

**Tests:**
- `specLoadsSuccessfully` - Loading
- `netContainsConcurrentMITask` - Structure with AND
- `caseExecutesConcurrently` - Parallel execution
- `multipleConcurrentInstances` - Instance creation
- `aggregateExecutesAfterConcurrent` - Proper sequencing
- `performanceTarget` - <8s completion

**Expected Outcome:** 6 tests pass, efficient concurrent execution

---

### WCP-28: Structured Loop (Control Flow)

```bash
mvn test -Dtest=WorkflowPatternIterationTest#Wcp28StructuredLoopTests
```

**Tests:**
- `specLoadsSuccessfully` - Loading
- `netContainsLoopStructure` - Check and body tasks
- `loopStructureExecutes` - Basic loop execution
- `loopBodyExecutesMultipleTimes` - Multiple iterations
- `loopCheckExecutesPerIteration` - Control flow gating
- `loopTerminatesViaExit` - Proper exit
- `finalizeAfterExit` - Post-loop cleanup
- `performanceTarget` - <5s completion
- `iterationCountTracked` - Metrics capture

**Expected Outcome:** 9 tests pass, iterations tracked

---

### WCP-29: Loop with Cancel Task

```bash
mvn test -Dtest=WorkflowPatternIterationTest#Wcp29LoopWithCancelTests
```

**Tests:**
- `specLoadsSuccessfully` - Loading
- `netContainsLoopWithCancel` - 3-way XOR split
- `loopWithCancelExecutes` - Basic loop operation
- `loopTerminatesNormally` - Normal exit path
- `loopCanBeCancelled` - Cancel branch
- `finalizeExecutesAfterLoop` - Cleanup after any exit
- `loopBodyExecutesBeforeTermination` - Pre-termination execution
- `performanceTarget` - <5s completion

**Expected Outcome:** 8 tests pass, both exit paths valid

---

### Stress and Edge Cases

```bash
mvn test -Dtest=WorkflowPatternIterationTest#StressAndEdgeCaseTests
```

**Tests:**
- `loopSingleIteration` - WCP-28 edge case
- `sequentialSingleItem` - WCP-26 edge case
- `concurrentSingleItem` - WCP-27 edge case
- `multipleConcurrentCases` - Parallel execution
- `resourceCleanupSuccessiveCases` - Leak detection

**Expected Outcome:** 5 tests pass, no leaks detected

---

## Advanced Scenarios

### Loop Termination Tests

```bash
mvn test -Dtest=AdvancedIterationScenariosTest#LoopTerminationTests
```

**Coverage:**
- Loop exits when condition false
- Loop check gates body execution
- Multiple exit paths (normal/cancel)
- Maximum iteration bounds respected

---

### Multi-Instance Completion Tests

```bash
mvn test -Dtest=AdvancedIterationScenariosTest#MultiInstanceCompletionTests
```

**Coverage:**
- Sequential ordering verification
- Concurrent instance aggregation
- Dynamic instance creation tracking

---

### Sequential vs Concurrent Semantics

```bash
mvn test -Dtest=AdvancedIterationScenariosTest#ExecutionSemanticsTests
```

**Coverage:**
- Sequential one-at-a-time execution
- Concurrent non-deterministic ordering
- Loop iteration sequencing

---

### Performance Tests

```bash
mvn test -Dtest=AdvancedIterationScenariosTest#PerformanceTests
```

**Benchmarks:**
- Single iteration: <2 seconds
- Sequential scaling: linear with instance count
- Concurrent scaling: parallelization efficiency
- Per-iteration overhead: <1 second average

---

### Resource Cleanup Tests

```bash
mvn test -Dtest=AdvancedIterationScenariosTest#ResourceCleanupTests
```

**Verification:**
- Cleanup after loop completion
- Rapid successive cases (5x)
- Concurrent case independence

---

## Individual Test Execution

### Run Single Test

```bash
# WCP-25 basic execution
mvn test -Dtest=WorkflowPatternIterationTest#Wcp25CancelCompleteMITests#caseExecutesToCompletion

# WCP-28 loop iterations
mvn test -Dtest=WorkflowPatternIterationTest#Wcp28StructuredLoopTests#loopBodyExecutesMultipleTimes

# Performance test
mvn test -Dtest=AdvancedIterationScenariosTest#PerformanceTests#singleIterationFast
```

---

## Debugging and Diagnostics

### Run Tests with Verbose Output

```bash
mvn test -Dtest=WorkflowPatternIterationTest -v 2>&1 | tee test-output.log
```

### Check Test Results Summary

```bash
# After test run, check:
cat target/surefire-reports/TEST-*.txt | grep -A5 -B5 "FAILURE\|ERROR"
```

### Capture Execution Timeline

```bash
# Run with timestamps
mvn test -Dtest=WorkflowPatternIterationTest -v 2>&1 | grep -E "^\[|elapsed|ms"
```

### Check for Timeout Issues

```bash
# Look for timeout-related failures
mvn test -Dtest=WorkflowPatternIterationTest -v 2>&1 | grep -i "timeout\|await\|latch"
```

### Monitor Memory During Tests

```bash
# With memory tracking
mvn test -Dtest=WorkflowPatternIterationTest \
    -XX:+PrintGCDetails \
    -XX:+PrintGCTimeStamps \
    -Xloggc:gc.log
```

---

## Continuous Integration

### Add to GitHub Actions Workflow

```yaml
name: Test WCP Patterns

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: 21

      - name: Run WCP Pattern Tests
        run: >
          mvn test
          -Dtest=WorkflowPatternIterationTest,AdvancedIterationScenariosTest
          -DthreadCount=4
          -Dparallel=methods
        timeout-minutes: 10

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: target/surefire-reports/

      - name: Generate Coverage Report
        run: mvn jacoco:report

      - name: Upload Coverage to Codecov
        uses: codecov/codecov-action@v2
        with:
          file: ./target/site/jacoco/jacoco.xml
```

---

## Test Data and Resources

### Required XML Specifications

All specifications are located in:
```
/home/user/yawl/test/org/yawlfoundation/yawl/stateless/resources/
```

Files loaded by tests:

```
Wcp25CancelCompleteMI.xml      - WCP-25: Dynamic MI, min:1, max:5, threshold:3
Wcp26SequentialMI.xml          - WCP-26: Sequential MI, min:1, max:4
Wcp27ConcurrentMI.xml          - WCP-27: Concurrent MI, AND semantics
Wcp28StructuredLoop.xml        - WCP-28: While-do loop with XOR-split
Wcp29LoopWithCancelTask.xml    - WCP-29: Loop with 3-way cancel branch
```

### Verifying Specifications

```bash
# List all WCP specifications
ls -la /home/user/yawl/test/org/yawlfoundation/yawl/stateless/resources/Wcp*.xml

# Validate XML against YAWL schema
xmllint --schema schema/YAWL_Schema4.0.xsd \
    test/org/yawlfoundation/yawl/stateless/resources/Wcp28StructuredLoop.xml
```

---

## Common Issues and Solutions

### Issue: Tests Timeout

**Symptom:** `java.util.concurrent.TimeoutException`

**Cause:** Case execution exceeds 15-20 second window

**Solutions:**
```bash
# Increase timeout in test code (currently 15s, can extend to 30s)
# Or verify engine is responsive:
mvn test -Dtest=StatelessEngineCaseMonitorTest

# Check for hung cases:
mvn test -Dtest=WorkflowPatternIterationTest -v | grep "await\|latch"
```

### Issue: Work Items Not Completing

**Symptom:** `allInitiatedWorkItemsComplete` assertion fails

**Cause:** Engine not completing work items automatically

**Solutions:**
```bash
# Verify event listener is registered:
mvn test -Dtest=StatelessEngineCaseMonitorTest#testCaseMonitoringEnabled

# Check listener exception handling:
mvn test -v 2>&1 | grep -A3 "ITEM_STARTED\|ITEM_COMPLETED"
```

### Issue: Performance Tests Failing

**Symptom:** Elapsed time exceeds target (e.g., >5 seconds)

**Cause:** System load, JVM startup, or engine overhead

**Solutions:**
```bash
# Warm up JVM with first test run
mvn test -Dtest=WorkflowPatternIterationTest#Wcp28StructuredLoopTests#loopSingleIteration

# Then run performance test
mvn test -Dtest=AdvancedIterationScenariosTest#PerformanceTests#singleIterationFast

# Check CPU load
top -b -n 1 | head -20
```

### Issue: Resource Cleanup Tests Failing

**Symptom:** Work item counts differ between cases

**Cause:** Event listeners not properly cleared

**Solutions:**
```bash
# Verify listeners cleared in @AfterEach:
grep -A10 "@AfterEach" test/org/yawlfoundation/yawl/stateless/WorkflowPatternIterationTest.java

# Check for event listener leaks:
mvn test -Dtest=AdvancedIterationScenariosTest#ResourceCleanupTests -v
```

---

## Test Coverage Analysis

### Generate JaCoCo Report

```bash
mvn clean test jacoco:report \
    -Dtest=WorkflowPatternIterationTest,AdvancedIterationScenariosTest

# Open report
open target/site/jacoco/index.html
```

### Check Coverage by Module

```bash
# Coverage for stateless module
mvn jacoco:report -DskipTests=false -pl yawl-stateless

# View coverage summary
cat target/site/jacoco/index.html | grep -A5 "Instructions"
```

### Generate XML Coverage Report

```bash
mvn test jacoco:report
ls target/site/jacoco/jacoco.xml
```

---

## Performance Profiling

### Measure Execution Time

```bash
# Time full test suite
time mvn test -Dtest=WorkflowPatternIterationTest

# Expected: ~30-45 seconds
```

### Profile with Java Flight Recorder

```bash
mvn test -Dtest=WorkflowPatternIterationTest \
    -XX:StartFlightRecording=filename=wcp-tests.jfr,duration=60s

# Analyze with JMC (Java Mission Control)
```

### Memory Analysis

```bash
# Heap dump on OutOfMemory
mvn test -Dtest=WorkflowPatternIterationTest \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=./heapdumps/
```

---

## Test Results Interpretation

### Successful Execution

```
Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
Time elapsed: 45.234 s

✓ All tests passed
✓ All cleanup verified
✓ Performance targets met
```

### Common Failures

```
FAILURE: loopBodyExecutesMultipleTimes
  Expected: loopBody count >= 1
  Actual: count = 0
  Cause: Loop condition false on first check

FAILURE: caseCompletesWithinPerformanceTarget
  Expected: elapsed < 5000ms
  Actual: elapsed = 6234ms
  Cause: System load or JVM GC
```

---

## Maintenance and Updates

### Add New Test

```java
// In WorkflowPatternIterationTest or AdvancedIterationScenariosTest

@Test
@DisplayName("Description of what is tested")
@Timeout(20)  // 20 seconds maximum
void testName() throws Exception {
    YSpecification spec = loadSpecification("Wcp28StructuredLoop.xml");
    ExecutionResult result = driveToCompletion(spec, "test-case-id");

    assertTrue(result.caseTerminatedCleanly(), "Case must complete");
    // Additional assertions...
}
```

### Update Performance Target

```java
// Change target time
assertTrue(result.elapsedMs < 3000L,  // Changed from 5000
    "Should complete within 3 seconds; took " + result.elapsedMs + "ms");
```

### Debug Specific Test

```bash
# Run with minimal output
mvn test -Dtest=TestClassName#testMethodName -q

# Run with full stack traces
mvn test -Dtest=TestClassName#testMethodName -e
```

---

## Resources and Documentation

- **Test Improvement Report:** `/home/user/yawl/TEST_IMPROVEMENT_REPORT_WCP24-28.md`
- **CLAUDE.md Project Guide:** `/home/user/yawl/CLAUDE.md`
- **Chicago TDD Rules:** `/home/user/yawl/.claude/rules/testing/chicago-tdd.md`
- **Test Specifications:** `/home/user/yawl/test/org/yawlfoundation/yawl/stateless/resources/`

---

**Quick Reference Card**

```
┌─────────────────────────────────────────┐
│ WCP Pattern Test Execution              │
├─────────────────────────────────────────┤
│ All Tests                               │
│ mvn test -Dtest=WorkflowPattern*        │
│                                         │
│ WCP-25: mvn test -D...#Wcp25*           │
│ WCP-26: mvn test -D...#Wcp26*           │
│ WCP-27: mvn test -D...#Wcp27*           │
│ WCP-28: mvn test -D...#Wcp28*           │
│ WCP-29: mvn test -D...#Wcp29*           │
│ Stress: mvn test -D...#StressAnd*       │
│                                         │
│ Advanced Scenarios                      │
│ mvn test -Dtest=AdvancedIteration*      │
├─────────────────────────────────────────┤
│ Expected Results                        │
│ • 44 tests total                        │
│ • <60 seconds execution time            │
│ • 0 failures, 0 errors                  │
│ • All cleanup verified                  │
│ • Performance targets met               │
└─────────────────────────────────────────┘
```

---

**Session ID:** https://claude.ai/code/session_01S7entRkBwDKBU1vdApktHj
