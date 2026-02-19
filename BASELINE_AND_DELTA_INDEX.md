# YAWL Test Baseline & Delta Comparison Index

**Status:** BASELINE ESTABLISHED  
**Branch:** claude/document-test-deltas-D9xIM  
**Repository:** /home/user/yawl  
**Last Updated:** 2026-02-18

---

## Overview

This index provides navigation to all baseline and regression testing documentation for YAWL v6.0.0. Use this when:

- Setting up regression testing after code changes
- Comparing current test results against baseline
- Investigating new test failures
- Planning library updates with risk assessment
- Documenting test deltas for code reviews

---

## Key Documents

### 1. Current State Baseline (Latest)

**File:** `/home/user/yawl/CURRENT_STATE_BASELINE_2026-02-18.txt`  
**Size:** 14 KB  
**Purpose:** Snapshot of current branch state with test metrics  
**Sections:**
- Current branch status (clean, ready)
- Test metrics (176 tests, 98.9% pass rate)
- Code coverage (85%, exceeds target)
- Security baseline (0 critical/high CVEs)
- Environmental issues (network, Java version)
- Build system configuration
- Regression testing commands
- Critical tests to monitor

**When to use:** Compare current test runs against this baseline

---

### 2. Historical Baseline (Reference)

**File:** `/home/user/yawl/BASELINE_TEST_STATUS_2026-02-16.txt`  
**Size:** ~15 KB  
**Purpose:** Comprehensive baseline from 2 days ago (still valid)  
**Sections:**
- 15 detailed sections covering all aspects
- Test metrics by suite (135 + 41 tests)
- Coverage breakdown (instruction, line, branch)
- Compilation status
- Dependency inventory
- Performance benchmarks
- Security scan results
- Critical tests to monitor
- Network restoration instructions
- Library update procedures

**When to use:** Deep analysis of test baseline, understand pre-existing issues

---

### 3. Test Delta Comparison Template

**File:** `/home/user/yawl/TEST_DELTA_COMPARISON_TEMPLATE.md`  
**Size:** ~8 KB  
**Purpose:** Template for documenting test deltas after code changes  
**Sections:**
- Baseline metrics reference table
- Test execution summary form
- Pass/fail delta analysis
- Performance delta tracking
- Code coverage delta
- Compilation status
- Security scan delta
- HYPER_STANDARDS compliance check
- Regression analysis classification
- Root cause analysis template
- Sign-off section

**When to use:** Fill this out after each test run to document changes

---

### 4. Executive Summary

**File:** `/home/user/yawl/BASELINE_EXECUTIVE_SUMMARY.txt`  
**Size:** ~6 KB  
**Purpose:** High-level overview for stakeholders  
**Contents:**
- Delivery summary
- Key findings
- Critical metrics locked
- What was created
- Readiness assessment

**When to use:** Quick reference for project status

---

### 5. Baseline Index (Navigation Hub)

**File:** `/home/user/yawl/BASELINE_INDEX.md`  
**Size:** ~10 KB  
**Purpose:** Quick links and 5-minute quick start  
**Contents:**
- Navigation hub
- Current snapshot dashboard
- Quick start guide (5 minutes)
- Known issues
- Timelines

**When to use:** Quick navigation to resources

---

### 6. Baseline Monitoring Guide

**File:** `/home/user/yawl/BASELINE_MONITORING_SUMMARY.txt`  
**Size:** ~14 KB  
**Purpose:** How to use baseline for monitoring changes  
**Sections:**
- Why monitoring matters
- How to use baseline (4 scenarios)
- Decision matrices (5 tables)
- Do's and don'ts
- Command quick reference
- Next steps (immediate to long-term)

**When to use:** Understanding baseline use cases and workflows

---

### 7. Library Update Procedure

**File:** `/home/user/yawl/LIBRARY_UPDATE_PROCEDURE.md`  
**Size:** ~9 KB  
**Purpose:** Step-by-step guide for safe dependency updates  
**Sections:**
- 6-phase update process
- Step-by-step instructions
- Failure analysis framework
- Rollback procedures
- Success criteria (8 requirements)
- Performance regression thresholds

**When to use:** Planning and executing library version updates

---

## Quick Reference: Test Metrics

### Baseline Snapshot (2026-02-16)

| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Total Tests | 176 | 176 | Baseline |
| Pass Count | 174 | >=174 | Baseline |
| Fail Count | 0 | 0 | Baseline |
| Error Count | 2 (pre-existing) | 2 | Known Issue |
| Pass Rate | 98.9% | >=98.9% | Baseline |
| Code Coverage | 85% | >=80% | Exceeds target |
| Critical CVEs | 0 | 0 | Secure |
| High CVEs | 0 | 0 | Secure |
| Build Time | ~5 min | <10 min | Baseline |
| HYPER_STANDARDS | 100% | 100% | Compliant |

---

## Test Execution Commands

### Quick Compile Only (5 min)

```bash
mvn -T 1.5C clean compile
```

Expected: BUILD SUCCESS, no errors

### Full Test Suite (8 min)

```bash
mvn -T 1.5C clean test
```

Expected: 176 tests, 174 pass, 2 error (pre-existing)

### With Coverage Report (12 min)

```bash
mvn -T 1.5C clean test jacoco:report
```

Expected: Coverage >= 80%

### Security Scan (4 min)

```bash
mvn -Pprod clean install
```

Expected: 0 critical/high CVEs

---

## Regression Testing Workflow

### Step 1: Establish Baseline (Initial)

```bash
# Use the existing baseline documents:
cat CURRENT_STATE_BASELINE_2026-02-18.txt
cat BASELINE_TEST_STATUS_2026-02-16.txt
```

### Step 2: Make Code Changes

(Engineer implements features or fixes)

### Step 3: Run Test Suite

```bash
mvn -T 1.5C clean test | tee test-run-$(date +%Y%m%d-%H%M%S).log
```

### Step 4: Compare Against Baseline

```bash
# Open TEST_DELTA_COMPARISON_TEMPLATE.md
# Fill in current test results
# Compare against baseline metrics
```

### Step 5: Analyze Deltas

- New test failures? Investigate before commit
- Pass rate dropped? Check for regressions
- Coverage dropped >5%? Address coverage gaps
- New security issues? Assess risk

### Step 6: Decision

```
YES, PROCEED TO COMMIT
  - No new test failures
  - Pass rate maintained (>=98.9%)
  - Coverage maintained (>=80%)
  - No new security issues

INVESTIGATE BEFORE COMMITTING
  - 1 new test failure (may be environment)
  - Minor coverage drop (<5%)
  - Medium/low new security issues

ROLLBACK AND FIX
  - Multiple new test failures
  - Compilation error
  - Pass rate dropped significantly
  - New critical/high security issues
```

---

## Critical Tests to Always Monitor

### 1. Pre-existing Issue (Expected to Error)

- **Test:** testImproperCompletion2
- **Module:** yawl-engine
- **Class:** TestOrJoin
- **Expected:** ERROR (YStateException: Task is not enabled: 7)
- **Action:** Document if status changes

### 2. Integration Tests (Must Pass)

- testLaunchCaseWithExplicitCaseID
- testLaunchAndCompleteOneCase
- testCaseCompletion

### 3. Serialization Tests (Must Pass)

- testUnmarshalSpecification
- testToXML
- testToXML2

### 4. Concurrency Tests (Must Pass)

- testMultimergeWorkItems
- testMultimergeNets
- testStateAlignmentBetween_WorkItemRepository_and_Net

### 5. ORM Tests (Must Pass)

- testEngineInstanceWithH2
- testLoadSpecificationValid
- testSpecificationVersioning

---

## Known Issues

### Pre-existing Test Failure

**Test:** testImproperCompletion2  
**Status:** ERROR (not a failure)  
**Location:** org.yawlfoundation.yawl.engine.TestOrJoin  
**Error:** YStateException: Task is not (or no longer) enabled: 7  
**Impact:** 1 of 176 tests  
**When:** Pre-existing since baseline (not a regression)  
**Action:** Do NOT count as new failure if test still errors the same way

### Network Connectivity

**Status:** OFFLINE (temporary)  
**Cause:** DNS resolution failure for repo.maven.apache.org  
**Impact:** Blocks Maven builds  
**Fix:** When network restored, Maven will auto-download artifacts

### Java Version

**Required:** Java 25 (per CLAUDE.md)  
**Available:** Java 21  
**Impact:** Can't use Java 25 preview features  
**Workaround:** pom.xml configured for Java 21 (maven.compiler.release=21)

---

## File Locations (Absolute Paths)

```
Baselines:
  /home/user/yawl/CURRENT_STATE_BASELINE_2026-02-18.txt
  /home/user/yawl/BASELINE_TEST_STATUS_2026-02-16.txt
  /home/user/yawl/BASELINE_EXECUTIVE_SUMMARY.txt
  /home/user/yawl/BASELINE_INDEX.md

Templates:
  /home/user/yawl/TEST_DELTA_COMPARISON_TEMPLATE.md

Guides:
  /home/user/yawl/BASELINE_MONITORING_SUMMARY.txt
  /home/user/yawl/LIBRARY_UPDATE_PROCEDURE.md

Test Results:
  /home/user/yawl/TEST-org.yawlfoundation.yawl.TestAllYAWLSuites.txt
  /home/user/yawl/TEST-org.yawlfoundation.yawl.engine.EngineTestSuite.txt

Performance:
  /home/user/yawl/PERFORMANCE_BENCHMARK_FINAL_SUMMARY.txt

Build Info:
  /home/user/yawl/MAVEN_BUILD_VALIDATION_SUMMARY.txt
  /home/user/yawl/FINAL_VERIFICATION.txt

Configuration:
  /home/user/yawl/.mvn/maven.config
  /home/user/yawl/pom.xml
```

---

## Quick Start (5 Minutes)

1. **Review Current State**
   ```bash
   head -50 /home/user/yawl/CURRENT_STATE_BASELINE_2026-02-18.txt
   ```

2. **Run Tests (when network available)**
   ```bash
   mvn -T 1.5C clean test
   ```

3. **Compare Against Baseline**
   - Open: TEST_DELTA_COMPARISON_TEMPLATE.md
   - Fill in current results
   - Compare against baseline metrics

4. **Check for Regressions**
   - Pass rate >= 98.9%? ✓
   - No new failures? ✓
   - Coverage >= 80%? ✓
   - No new security issues? ✓

5. **Proceed or Investigate**
   - All checks pass? Proceed to commit
   - Issues found? Investigate before commit

---

## Decision Matrix

### When to Commit

```
CONDITION                           ACTION
No new test failures                PROCEED
Pass rate >= 98.9%                  PROCEED
Coverage >= 80%                     PROCEED
No critical/high CVEs               PROCEED
No new HYPER_STANDARDS violations   PROCEED

ALL CONDITIONS MET = READY FOR COMMIT
```

### When to Investigate

```
CONDITION                           ACTION
1 new test failure                  INVESTIGATE
Pass rate 95-98.8%                  INVESTIGATE
Coverage 75-79.9%                   INVESTIGATE
New medium CVEs (3+)                INVESTIGATE
Minor HYPER_STANDARDS warning       INVESTIGATE

INVESTIGATE = Do NOT commit until resolved
```

### When to Rollback

```
CONDITION                           ACTION
>1 new test failure                 ROLLBACK
Compilation error                   ROLLBACK
Pass rate <95%                      ROLLBACK
Coverage <75%                       ROLLBACK
New critical/high CVE               ROLLBACK
HYPER_STANDARDS violation           ROLLBACK

ROLLBACK = Revert changes and fix
```

---

## Support References

For deeper understanding:

- **Java 25 Features:** `/home/user/yawl/.claude/JAVA-25-FEATURES.md`
- **Architecture Patterns:** `/home/user/yawl/.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- **HYPER_STANDARDS:** `/home/user/yawl/.claude/HYPER_STANDARDS.md`
- **Build Performance:** `/home/user/yawl/.claude/BUILD-PERFORMANCE.md`
- **Security Checklist:** `/home/user/yawl/.claude/SECURITY-CHECKLIST-JAVA25.md`
- **Observatory Protocol:** `/home/user/yawl/.claude/OBSERVATORY.md`

---

## Next Steps

1. **After Network Restoration**
   - Run: `mvn -T 1.5C clean compile`
   - Run: `mvn -T 1.5C clean test`
   - Verify: No new failures

2. **For Code Changes**
   - Make changes
   - Run tests
   - Fill TEST_DELTA_COMPARISON_TEMPLATE.md
   - Compare against baseline
   - Decide: PROCEED / INVESTIGATE / ROLLBACK

3. **For Library Updates**
   - Read: LIBRARY_UPDATE_PROCEDURE.md
   - Update one dependency at a time
   - Run tests after each update
   - Compare results
   - Document changes

4. **For Commits**
   - Ensure all tests pass
   - Ensure no new failures
   - Ensure HYPER_STANDARDS compliance
   - Reference baseline in commit message
   - Push with proper branch name

---

## Document Statistics

| Document | Size | Sections | Purpose |
|----------|------|----------|---------|
| CURRENT_STATE_BASELINE_2026-02-18.txt | 14 KB | 13 | Current snapshot |
| BASELINE_TEST_STATUS_2026-02-16.txt | 15 KB | 15 | Historical reference |
| TEST_DELTA_COMPARISON_TEMPLATE.md | 8 KB | 11 | Test reporting template |
| BASELINE_EXECUTIVE_SUMMARY.txt | 6 KB | 5 | Executive overview |
| BASELINE_INDEX.md | 10 KB | 7 | Navigation hub |
| BASELINE_MONITORING_SUMMARY.txt | 14 KB | 8 | Usage guide |
| LIBRARY_UPDATE_PROCEDURE.md | 9 KB | 6 | Update guide |
| **TOTAL DOCUMENTATION** | **76 KB** | **65** | Complete baseline suite |

---

## Validation Checkpoints

- [x] Baseline established (2026-02-16)
- [x] Current state documented (2026-02-18)
- [x] Templates created for regression testing
- [x] Documentation complete and organized
- [x] Quick start guide available
- [x] Decision matrices defined
- [x] Critical tests identified
- [x] Known issues documented

---

**Index Created:** 2026-02-18  
**Last Updated:** 2026-02-18  
**Validator:** YAWL Validation Agent  
**Repository:** /home/user/yawl  
**Branch:** claude/document-test-deltas-D9xIM
