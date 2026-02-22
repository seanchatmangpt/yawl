# YAWL v6.0.0 | Controlled Library Update Procedure

## Overview

This document provides the step-by-step procedure for updating dependencies while maintaining code stability and monitoring test impacts.

**Baseline Established**: 2026-02-16
**Previous Pass Rate**: 98.9% (174/176 tests)
**Previous Coverage**: 85%

## Pre-Update Checklist

- [ ] Read baseline report: `BASELINE_TEST_STATUS_2026-02-16.txt`
- [ ] Network connection available (can access Maven Central)
- [ ] Java 25 installed and available
- [ ] Git branch ready (no uncommitted changes)
- [ ] Working directory: `/home/user/yawl`

## Step-by-Step Update Procedure

### Phase 1: Preparation (Before Update)

1. **Verify Current State**
   ```bash
   cd /home/user/yawl
   git status
   git log --oneline | head -5
   ```

2. **Document Baseline** (if not done)
   ```bash
   mvn clean test 2>&1 | tee UPDATE_BASELINE_$(date +%Y%m%d).txt
   # Count: grep -c "testcase:" UPDATE_BASELINE_*.txt
   ```

3. **Create Feature Branch**
   ```bash
   git checkout -b update/library-<name>-v<version>
   ```

4. **Create Update Log**
   ```bash
   cat > UPDATE_REPORT_$(date +%Y%m%d_%H%M%S).md << 'REPORT'
   # Library Update Report
   Date: $(date)
   Updated By: [Your Name]
   
   ## Updates Applied
   - [ ] Dependency 1
   - [ ] Dependency 2
   
   ## Pre-Update Metrics
   - Tests: 174/176 (pre-existing: testImproperCompletion2)
   - Coverage: 85%
   - Build Time: ~5min
   
   ## Results
   REPORT
   ```

### Phase 2: Update Single Dependency

1. **Select One Dependency**
   - Update ONLY one version at a time
   - Example: `spring-boot.version` from 3.4.3 to 3.4.4

2. **Modify pom.xml**
   ```xml
   <!-- OLD -->
   <spring-boot.version>3.4.3</spring-boot.version>
   
   <!-- NEW -->
   <spring-boot.version>3.4.4</spring-boot.version>
   ```

3. **Compile Check (Early Failure Detection)**
   ```bash
   mvn clean compile 2>&1 | tee COMPILE_$(date +%s).log
   ```
   
   Expected: SUCCESS or actionable errors
   
   **STOP if**:
   - Compilation error (not warning)
   - Resolution failure
   - Missing symbol errors

4. **Test Execution**
   ```bash
   mvn clean test 2>&1 | tee TEST_RESULT_$(date +%s).txt
   ```

5. **Analyze Results**
   ```bash
   # Extract summary
   grep -E "Tests run:|Failures:|Errors:|Pass Rate:" TEST_RESULT_*.txt
   
   # Check for new failures
   diff <(grep "Testcase:" BASELINE_TEST.txt) \
        <(grep "Testcase:" TEST_RESULT_*.txt)
   ```

### Phase 3: Failure Analysis

#### Scenario A: All Tests Pass

```
✓ No new failures
✓ Pass rate maintained or improved
✓ Code coverage maintained (>= 80%)
```

**Action**: Continue to next dependency

#### Scenario B: New Test Failures

```
✗ New test(s) failing
✗ Pass rate dropped
✗ Different error than baseline
```

**Action**:

1. Identify affected tests
   ```bash
   grep -A5 "FAILURE\|ERROR" TEST_RESULT_*.txt
   ```

2. Check if related to dependency
   ```bash
   # Review error messages
   # Check stack traces
   # Verify it's not pre-existing issue (testImproperCompletion2)
   ```

3. Options:
   - **Option A**: Fix code to adapt to new library version
   - **Option B**: Investigate library breaking changes
   - **Option C**: Rollback to previous version
   ```bash
   # Rollback
   git checkout pom.xml
   mvn clean test
   ```

#### Scenario C: Compilation Error

```
✗ Compilation fails
✗ Resolution error
✗ Missing dependency
```

**Action**:

1. Review error message
   ```bash
   # Check COMPILE_*.log for details
   grep -B2 "ERROR\|error:" COMPILE_*.log
   ```

2. Possible causes:
   - Removed library version
   - API change in dependency
   - Transitive dependency conflict

3. Resolution:
   ```bash
   # Check dependency tree
   mvn dependency:tree | grep -i "<library-name>"
   
   # Try alternative version
   # Or add explicit transitive dependency
   # Or rollback
   ```

#### Scenario D: Performance Regression

```
✗ Tests pass but slower
✗ Build time increased >20%
✗ Response time metrics worse
```

**Action**:

1. Compare performance
   ```bash
   # Load test
   k6 run validation/performance/load-test.js
   
   # Compare to baseline: 1,450 ms P95
   ```

2. Options:
   - Accept if < 10% regression
   - Investigate if > 10% regression
   - Rollback if > 20% regression

### Phase 4: Security Verification

After successful test, check security:

```bash
# Full security scan (requires network + OWASP)
mvn -Psecurity-audit clean install

# Alternative: Check vulnerabilities
mvn dependency-check:check

# Verify no secrets committed
git diff HEAD~1 | grep -i "password\|key\|token" || echo "OK"
```

### Phase 5: Code Coverage Verification

Ensure coverage stays at 80% or above:

```bash
# Run with coverage
mvn clean test jacoco:report

# Check target/site/jacoco/index.html
# Extract from console output
grep -E "LINE|BRANCH|INSTRUCTION" target/site/jacoco/*.xml
```

### Phase 6: Commit Changes

```bash
# Stage specific files
git add pom.xml
git add pom.xml.backup  # Optional: backup of old pom

# Commit with clear message
git commit -m "deps: Update spring-boot from 3.4.3 to 3.4.4

Reason: Bug fix for ClassLoader issue
Tests: 174/176 pass (no new failures)
Coverage: 85% (maintained)
Build time: ~5min (unchanged)

Session: YAWL baseline update 2026-02-16
https://claude.ai/code/session_XXXX"
```

## Template: Test Comparison Report

Create this after each update:

```markdown
# Test Comparison: [Library Name] v[OLD] → v[NEW]

## Executive Summary
- Status: [PASS|FAIL]
- Test Trend: [IMPROVED|UNCHANGED|REGRESSED]
- Coverage Trend: [UP|STABLE|DOWN]

## Metrics Comparison

| Metric | Before | After | Change | Status |
|--------|--------|-------|--------|--------|
| Tests Passed | 174 | ??? | ±? | ? |
| Pass Rate | 98.9% | ???% | ±?% | ? |
| Coverage | 85% | ???% | ±?% | ? |
| Build Time | 5min | ???min | ±?s | ? |

## Pre-Existing Issues
- testImproperCompletion2: STILL FAILING (expected)

## New Issues
- [If any]

## Recommendations
- [ ] Accept change
- [ ] Investigate further
- [ ] Rollback and try different version
- [ ] Mark as blocker
```

## Handling Multiple Updates

If updating multiple dependencies:

1. Update in order of criticality
2. Test after EACH update
3. Create separate commits for each
4. Document cumulative impact

Example workflow:
```bash
# Update 1
mvn versions:update-property -Dproperty=spring-boot.version
mvn clean test
git commit -m "Update Spring Boot"

# Update 2
mvn versions:update-property -Dproperty=jackson.version
mvn clean test
git commit -m "Update Jackson"

# Continue...
```

## Rollback Procedure

If something goes wrong:

```bash
# Option 1: Revert last commit
git revert HEAD
mvn clean test

# Option 2: Checkout previous version
git checkout HEAD~1 pom.xml
mvn clean test

# Option 3: Manual pom.xml edit
# Edit version back to baseline
mvn clean test
```

## Monitoring Test Degradation

### If 1-2 tests fail:
- Investigate cause
- Check if pre-existing (testImproperCompletion2)
- Determine if dependency-related

### If 3+ tests fail:
- Likely library compatibility issue
- Create issue tracker ticket
- Consider alternative version
- May need code changes

### If compilation fails:
- Immediate rollback
- Check library release notes
- Try next version down

## Performance Monitoring

After update, verify:

```bash
# 1. Compilation speed
time mvn clean compile

# 2. Test speed
time mvn clean test

# 3. Load test performance
k6 run --vus 100 --duration 5m validation/performance/load-test.js
```

Target: No more than 10% slower than baseline

## Integration Tests

Don't forget:

```bash
# Run integration tests
mvn verify

# Full security check
mvn -Psecurity-audit clean install

# Check with multiple Java versions
mvn -Pjava24 clean test
mvn -Pjava25 clean test
```

## Documentation Updates

Update these after successful update:

1. Update baseline report version
2. Document any code changes needed
3. Note any new known issues
4. Update dependency compatibility matrix

## Success Criteria

All of the following must be true:

- [ ] mvn clean compile succeeds
- [ ] mvn clean test: 100% pass rate (excluding pre-existing failures)
- [ ] Code coverage >= 80%
- [ ] No new security vulnerabilities
- [ ] Build time < 20% slower
- [ ] Load test performance < 10% regression
- [ ] All commits follow commit guidelines
- [ ] No HYPER_STANDARDS violations

## Emergency Procedures

### Restore Baseline
```bash
git reset --hard origin/main
mvn clean test
```

### Emergency Rollback
```bash
git revert HEAD
git push origin claude/revert-bad-update
# Create PR with explanation
```

## References

- Baseline Report: `/home/user/yawl/BASELINE_TEST_STATUS_2026-02-16.txt`
- Maven Build Validation: `/home/user/yawl/MAVEN_BUILD_VALIDATION_SUMMARY.txt`
- Performance Baseline: `/home/user/yawl/PERFORMANCE_BENCHMARK_FINAL_SUMMARY.txt`
- CLAUDE.md: Project instructions and standards

---

**Last Updated**: 2026-02-16
**Baseline Date**: 2026-02-16
**Valid For**: YAWL v6.0.0+
