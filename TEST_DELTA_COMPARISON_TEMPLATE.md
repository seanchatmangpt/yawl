# YAWL Test Delta Comparison Template

**Branch:** claude/document-test-deltas-D9xIM  
**Baseline Date:** 2026-02-16  
**Current State File:** /home/user/yawl/CURRENT_STATE_BASELINE_2026-02-18.txt  
**Baseline State File:** /home/user/yawl/BASELINE_TEST_STATUS_2026-02-16.txt

---

## Quick Summary

Use this template after each code change to document test deltas and identify regressions.

### Test Execution Template

```bash
# Step 1: Restore network (if needed)
# Step 2: Run full test suite
mvn -T 1.5C clean test

# Step 3: Capture output
mvn -T 1.5C clean test | tee test-run-$(date +%Y%m%d-%H%M%S).log

# Step 4: Compare against baseline
```

---

## Baseline Metrics (Reference)

| Metric | Baseline (2026-02-16) | Target | Status |
|--------|---|---|---|
| **Total Tests** | 176 | 176 | - |
| **Pass Count** | 174 | >=174 | - |
| **Fail Count** | 0 | 0 | - |
| **Error Count** | 2 (pre-existing) | 2 | - |
| **Pass Rate** | 98.9% | >=98.9% | - |
| **Code Coverage** | 85% | >=80% | - |
| **Execution Time** | ~23 sec | <30 sec | - |

---

## Current Test Run Results (to be completed)

### Test Execution Summary

```
Date Run:               YYYYMMDD HH:MM UTC
Total Tests:            ___ (baseline: 176)
Pass:                   ___ (baseline: 174)
Fail:                   ___ (baseline: 0)
Error:                  ___ (baseline: 2)
Skip:                   ___ (baseline: 0)
Execution Time:         ___ sec (baseline: ~23 sec)
Pass Rate:              ___ % (baseline: 98.9%)
```

### Test Suite Breakdown

**Suite 1: TestAllYAWLSuites**
```
Tests:      ___ (baseline: 135)
Pass:       ___ (baseline: 134)
Fail:       ___ (baseline: 0)
Error:      ___ (baseline: 1)
Time:       ___ sec (baseline: 11.507 sec)
Pass Rate:  ___ % (baseline: 99.3%)
```

**Suite 2: EngineTestSuite**
```
Tests:      ___ (baseline: 41)
Pass:       ___ (baseline: 40)
Fail:       ___ (baseline: 0)
Error:      ___ (baseline: 1)
Time:       ___ sec (baseline: 11.614 sec)
Pass Rate:  ___ % (baseline: 97.6%)
```

---

## Delta Analysis

### Pass/Fail Delta

| Test | Baseline | Current | Delta | Status |
|------|----------|---------|-------|--------|
| **Pass** | 174 | ___ | +/- ___ | OK / REGRESSION |
| **Fail** | 0 | ___ | +/- ___ | OK / REGRESSION |
| **Error (known)** | 2 | ___ | +/- ___ | OK / REGRESSION |

### New Failures (if any)

List any NEW test failures not in the baseline:

```
Test Name:          
Location:           
Error Type:         
Error Message:      
Classification:     NEW / KNOWN
```

### Performance Delta

| Metric | Baseline | Current | Delta | Status |
|--------|----------|---------|-------|--------|
| **Suite 1 Time** | 11.507 s | ___ s | +/- ___ % | OK / SLOW |
| **Suite 2 Time** | 11.614 s | ___ s | +/- ___ % | OK / SLOW |
| **Total Time** | ~23 sec | ___ sec | +/- ___ % | OK / SLOW |

---

## Code Coverage Delta

```
Instruction Coverage:  ___ % (baseline: 70%, target: 70%)
Line Coverage:         ___ % (baseline: 65%, target: 65%)
Branch Coverage:       ___ % (baseline: 60%, target: 60%)
Overall Coverage:      ___ % (baseline: 85%, target: 80%)

Status: MEETS / EXCEEDS / BELOW TARGET
```

---

## Compilation Status

```
Maven Compile:        SUCCESS / FAILED
Exit Code:            ___
Build Time:           ___ sec
Warnings:             ___ (baseline: none)
Errors:               ___ (baseline: none)
```

---

## Security Scan Delta

```
Critical CVEs:        ___ (baseline: 0)
High CVEs:            ___ (baseline: 0)
Medium CVEs:          ___ (baseline: 20)

New Vulnerabilities:  YES / NO
```

---

## HYPER_STANDARDS Compliance Check

```
TODO/FIXME/XXX:       ___ (baseline: none)
Mock patterns:        ___ (baseline: none)
Stub code:            ___ (baseline: none)
Silent fallbacks:     ___ (baseline: none)

Compliance Status:    PASS / FAIL
```

---

## Regression Analysis

### Classification

- [ ] NO REGRESSION - All metrics within acceptable ranges
- [ ] MINOR DELTA - Small changes, no action required
- [ ] ATTENTION NEEDED - Review new failures before commit
- [ ] ROLLBACK RECOMMENDED - Revert changes and investigate

### Details

```
Analysis:
- Pass rate change: +/- ___ %
- New test failures: ___ (acceptable threshold: 0)
- Performance regression: +/- ___ % (acceptable threshold: 10%)
- Coverage loss: ___ % (acceptable threshold: -5%)
- New security issues: ___ (acceptable threshold: 0 critical/high)

Recommendation:
[PROCEED / INVESTIGATE / ROLLBACK]

Rationale:
```

---

## Root Cause Analysis (if regressions found)

### Failed Test Investigation

For each new failure, document:

```
Test: 
Module: 
Class: 
Method: 

Error Stack Trace:
[paste full stack trace]

Probable Cause:
[analyze the error]

Fix Required:
[describe needed fix]

PR/Issue Reference:
```

---

## Sign-off

```
Validated By:         [agent name]
Validation Date:      [YYYYMMDD HH:MM UTC]
Baseline Reference:   BASELINE_TEST_STATUS_2026-02-16.txt
Current State Ref:    CURRENT_STATE_BASELINE_2026-02-18.txt
Recommendation:       [PROCEED / INVESTIGATE / ROLLBACK]
```

---

## Notes for Future Runs

- **If network down:** See section 13 in BASELINE_TEST_STATUS_2026-02-16.txt for restoration
- **Offline mode:** mvn -o test (requires cached artifacts)
- **Quick compile:** mvn -T 1.5C clean compile (skip tests, ~30 sec)
- **Full build:** mvn -T 1.5C clean package (includes all checks, ~5 min)
- **Parallel tests:** Enabled by default (24 threads) via .mvn/maven.config

---

## Critical Tests to Monitor

Always check these after changes:

1. **testImproperCompletion2** - Pre-existing error, should stay ERROR
2. **testLaunchCaseWithExplicitCaseID** - Integration test, must pass
3. **testUnmarshalSpecification** - Serialization, must pass
4. **testMultimergeWorkItems** - Concurrency, must pass
5. **testEngineInstanceWithH2** - ORM, must pass

---

**Template Location:** /home/user/yawl/TEST_DELTA_COMPARISON_TEMPLATE.md  
**Last Updated:** 2026-02-18  
**Validator:** YAWL Validation Agent
