# Post-Fix Test Plan - Record Conversion Validation

**Date**: 2026-02-16
**Agent**: 8 (Regression Tester)
**Status**: READY TO EXECUTE (pending compilation fix)

---

## Prerequisites

Before executing this plan, ensure:
- [ ] All 4 files with setter issues have been fixed
- [ ] `ant -f build/build.xml compile` succeeds with zero errors
- [ ] No new warnings introduced

---

## Phase 1: Core Compilation & Syntax

### Test 1.1: Clean Compilation
```bash
ant -f build/build.xml clean
ant -f build/build.xml compile
```

**Expected**:
- BUILD SUCCESSFUL
- Zero compilation errors
- Warning count ≤ 105 (baseline)

**Success Criteria**: ✅ Clean build

---

## Phase 2: Unit Test Suite

### Test 2.1: Full Unit Test Suite
```bash
ant unitTest
```

**Expected**:
- 106+ tests run
- ≥102 tests pass (96%+ success rate)
- ≤4 failures (baseline from previous run)

**Key Test Areas**:
- Elements tests (20+ tests)
- Engine tests (30+ tests)
- Stateless tests (10+ tests)
- Integration tests (20+ tests)
- Autonomous agent tests (5+ tests)

### Test 2.2: Record-Specific Tests

#### YSpecificationID Tests
```bash
# Find all tests using YSpecificationID
grep -r "YSpecificationID" test/ --include="*.java" | cut -d: -f1 | sort -u

# Run specific test classes
java -cp classes:lib/* junit.textui.TestRunner \
  org.yawlfoundation.yawl.engine.TestYWorkItem
```

**Expected**: All YSpecificationID-related tests pass

#### WorkItem Tests
```bash
java -cp classes:lib/* junit.textui.TestRunner \
  org.yawlfoundation.yawl.engine.TestYWorkItemRepository
```

**Expected**: All WorkItem-related tests pass

---

## Phase 3: Integration Tests

### Test 3.1: Maven Integration Tests
```bash
mvn clean verify -DskipUnitTests=false
```

**Expected**:
- All module builds succeed
- Integration tests pass
- No regression in inter-module communication

### Test 3.2: Module-Specific Tests

```bash
# Test each module independently
mvn test -pl yawl-elements
mvn test -pl yawl-engine
mvn test -pl yawl-stateless
mvn test -pl yawl-integration
```

**Expected**: Each module's tests pass independently

---

## Phase 4: Coverage Analysis

### Test 4.1: Generate Coverage Report
```bash
mvn clean test jacoco:report
cat target/site/jacoco/index.html
```

**Coverage Targets**:
- Overall: ≥65%
- YSpecificationID: ≥80%
- WorkItemIdentity: ≥80%
- WorkItemMetadata: ≥70%
- WorkItemTiming: ≥70%
- AgentCapability: ≥80%

### Test 4.2: Identify Coverage Gaps
```bash
# Check specific classes
mvn jacoco:report
find target/site/jacoco -name "*.html" | grep -E "(YSpecificationID|WorkItem)"
```

**Action**: Document any classes with <65% coverage for future improvement

---

## Phase 5: Behavior Regression Tests

### Test 5.1: equals() and hashCode() Behavior

**Test**: Verify record equals/hashCode matches previous behavior

```java
// Manual verification (if needed)
YSpecificationID id1 = new YSpecificationID("id", "1.0", "uri");
YSpecificationID id2 = new YSpecificationID("id", "1.0", "uri");
assert id1.equals(id2);
assert id1.hashCode() == id2.hashCode();
```

**Expected**: Consistent behavior with previous class implementation

### Test 5.2: Serialization/Deserialization

**Test**: Verify records serialize correctly (Coordinate with Agent 7)

```bash
# Run serialization tests
mvn test -Dtest="*Serialization*"
```

**Expected**: All serialization tests pass

### Test 5.3: toString() Output

**Test**: Verify toString() output is usable (doesn't need exact match)

**Expected**: toString() produces readable output for debugging

---

## Phase 6: Performance Regression Tests

### Test 6.1: Baseline Performance
```bash
# Measure test execution time
time ant unitTest > test-results/performance-post-fix.txt
```

**Compare to Baseline**:
- Previous: ~12 seconds (from TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt)
- Expected: 11-13 seconds (±10% acceptable)

**Red Flag**: >10% slower (>13.2 seconds)

### Test 6.2: Memory Usage
```bash
# Run tests with memory monitoring
ant unitTest -Xmx512m -Xms256m
```

**Expected**:
- No OutOfMemoryError
- Similar heap usage to baseline

### Test 6.3: Record Creation Microbenchmark (if needed)

```java
// Quick benchmark of record creation
long start = System.nanoTime();
for (int i = 0; i < 100000; i++) {
    new YSpecificationID("id", "1.0", "uri");
}
long end = System.nanoTime();
System.out.println("Time: " + (end - start) / 1_000_000 + "ms");
```

**Expected**: <50ms for 100k creations (records should be fast)

---

## Phase 7: Immutability Verification

### Test 7.1: Verify withX() Methods Create New Instances

```java
// Manual verification
YSpecificationID original = new YSpecificationID("id1", "1.0", "uri1");
YSpecificationID modified = original.withIdentifier("id2");

assert !original.equals(modified);
assert original.identifier().equals("id1");
assert modified.identifier().equals("id2");
```

**Expected**: Original unchanged, new instance returned

### Test 7.2: Thread Safety Smoke Test

```java
// Verify immutability enables safe concurrent access
YSpecificationID shared = new YSpecificationID("id", "1.0", "uri");
// Pass to multiple threads - no synchronization needed
// All threads can safely read without locks
```

**Expected**: No concurrency issues (records are immutable)

---

## Phase 8: Integration with External Components

### Test 8.1: Database Persistence
```bash
# Test Hibernate/JPA integration
mvn test -Dtest="*Persistence*"
```

**Expected**: Records persist correctly to database

### Test 8.2: XML Serialization
```bash
# Test XML marshalling/unmarshalling
mvn test -Dtest="*Unmarshal*,*Marshal*"
```

**Expected**: Records convert to/from XML correctly

### Test 8.3: REST API Integration
```bash
# Test REST endpoints using WorkItemRecord
mvn test -Dtest="*Rest*"
```

**Expected**: Records serialize to JSON correctly

---

## Phase 9: Smoke Tests (Production-like)

### Test 9.1: End-to-End Workflow
```bash
# Run production test suite (if available)
bash run-production-tests.sh
```

**Expected**: Full workflow execution succeeds

### Test 9.2: MCP Server Integration
```bash
# Test MCP server with records
bash run-mcp-server.sh &
MCP_PID=$!
sleep 5
# Execute MCP commands
kill $MCP_PID
```

**Expected**: MCP server operates normally with record types

### Test 9.3: A2A Server Integration
```bash
# Test agent-to-agent with records
bash run-a2a-server.sh &
A2A_PID=$!
sleep 5
# Execute A2A commands
kill $A2A_PID
```

**Expected**: A2A communication works with record types

---

## Phase 10: Documentation & Reporting

### Test 10.1: Generate Final Report
```bash
cat > test-results/final-regression-report.txt << EOF
RECORD CONVERSION - FINAL REGRESSION REPORT
===========================================

Date: $(date)
Agent: 8 (Regression Tester)

COMPILATION: [PASS/FAIL]
UNIT TESTS: [X/Y passed]
INTEGRATION TESTS: [PASS/FAIL]
COVERAGE: [X%]
PERFORMANCE: [No regression/X% improvement/X% degradation]

CONCLUSION: [READY FOR NEXT PHASE / NEEDS WORK]
EOF
```

### Test 10.2: Update TEST_REGRESSION_REPORT.md

Update with:
- Final test counts
- Coverage numbers
- Performance measurements
- Any remaining issues

---

## Success Criteria Summary

| Category | Criterion | Target |
|----------|-----------|--------|
| Compilation | Zero errors | ✅ Required |
| Unit Tests | Pass rate | ≥96% |
| Integration Tests | All pass | ✅ Required |
| Coverage | Overall | ≥65% |
| Coverage | New records | ≥80% |
| Performance | Test execution | ±10% |
| Performance | Memory usage | No increase |
| Behavior | equals/hashCode | Consistent |
| Behavior | Serialization | Working |
| Immutability | withX() creates new | ✅ Required |

---

## Red Flags - Escalate Immediately

1. **Compilation fails** after fixes applied
2. **Test pass rate drops** below 90%
3. **Coverage drops** below 60%
4. **Performance degrades** by >10%
5. **Serialization breaks** (cannot persist/restore)
6. **Integration tests fail** unexpectedly

---

## Rollback Plan (if needed)

If critical issues found:

```bash
# Revert record conversions
git log --oneline | grep -i record
git revert <commit-hash>

# Or restore from backup
cp src/org/yawlfoundation/yawl/engine/YSpecificationID.java.bak \
   src/org/yawlfoundation/yawl/engine/YSpecificationID.java
```

**Decision Criteria**:
- >5 critical test failures
- >20% performance degradation
- Data corruption risk

---

## Post-Validation Actions

Once all tests pass:

1. [ ] Commit fixes with proper message
2. [ ] Update CHANGELOG.md with record conversion notes
3. [ ] Tag release (if applicable)
4. [ ] Notify other agents (6, 7) that testing can proceed
5. [ ] Document lessons learned
6. [ ] Create pre-commit hook to prevent future setter usage on records

---

## Test Execution Log Template

```
Date: ______________________
Executor: ___________________

Phase 1: Compilation
  ☐ Clean build: [PASS/FAIL]
  ☐ Zero errors: [PASS/FAIL]

Phase 2: Unit Tests
  ☐ All tests run: [PASS/FAIL]
  ☐ Pass rate ≥96%: [PASS/FAIL]
  ☐ Tests run: _____ / Passed: _____ / Failed: _____

Phase 3: Integration Tests
  ☐ Maven verify: [PASS/FAIL]
  ☐ Module tests: [PASS/FAIL]

Phase 4: Coverage
  ☐ Overall ≥65%: [PASS/FAIL]
  ☐ Records ≥80%: [PASS/FAIL]
  ☐ Actual coverage: _____%

Phase 5: Behavior
  ☐ equals/hashCode: [PASS/FAIL]
  ☐ Serialization: [PASS/FAIL]
  ☐ toString: [PASS/FAIL]

Phase 6: Performance
  ☐ Test time: _____ seconds (baseline: ~12s)
  ☐ Memory usage: [OK/HIGH]
  ☐ Performance: [IMPROVED/NEUTRAL/DEGRADED]

Phase 7: Immutability
  ☐ withX() methods: [PASS/FAIL]
  ☐ Thread safety: [PASS/FAIL]

Phase 8: External Integration
  ☐ Database: [PASS/FAIL]
  ☐ XML: [PASS/FAIL]
  ☐ REST: [PASS/FAIL]

Phase 9: Smoke Tests
  ☐ End-to-end: [PASS/FAIL]
  ☐ MCP: [PASS/FAIL]
  ☐ A2A: [PASS/FAIL]

OVERALL RESULT: [PASS/FAIL]
READY FOR NEXT PHASE: [YES/NO]

Issues Found:
_______________________________________
_______________________________________

Recommendations:
_______________________________________
_______________________________________
```

---

**Next Agent**: Agent 6 (Test Creator) after Phase 2 completes
**Next Agent**: Agent 7 (Serialization) after Phase 5 completes

**Estimated Total Execution Time**: 30-45 minutes

---

Generated by: Agent 8 (Regression Tester)
Session: https://claude.ai/code/session_01Jy5VPT7aRfKskDEcYVSXDq
