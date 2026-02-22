# Test Coverage Analysis Summary

## Overview

Comprehensive test coverage analysis performed on YAWL CLI (`yawl_cli/` module) using pytest and coverage.py.

**Report Date**: 2026-02-22  
**Test Count**: 384 tests (382 passed, 2 failed, 5 skipped)  
**Overall Coverage**: 63% line coverage  
**Target Coverage**: 80% line, 90% branch

## Quick Results

```
Test Results:      382 PASSED, 2 FAILED, 5 SKIPPED (99.5% pass rate)
Line Coverage:     882 / 1,402 statements covered (63%)
Branch Coverage:   Not measured (recommend enabling)
Duration:          86.79 seconds
```

## Coverage by Module

| Module | Coverage | Status | Priority |
|--------|----------|--------|----------|
| observatory.py | 93% | ✓ PASS | - |
| utils.py | 84% | ✓ PASS | - |
| godspeed.py | 72% | ⚠️ Needs improvement | MEDIUM |
| build.py | 66% | ⚠️ Needs improvement | HIGH |
| gregverse.py | 63% | ⚠️ Needs improvement | HIGH |
| team.py | 50% | ❌ Below target | HIGH |
| ggen.py | 47% | ❌ Below target | CRITICAL |
| config_cli.py | 29% | ❌ Below target | CRITICAL |

## Critical Gaps

### Completely Untested Functions (0% coverage)

1. **ggen.py::round_trip()** - 49 statements
   - Round-trip YAWL→JSON→YAWL conversion
   - No test coverage at all

2. **team.py::consolidate()** - 13 statements
   - Team consolidation workflow
   - No test coverage at all

3. **config_cli.py** - Multiple functions (109 lines)
   - `show()` - 0%
   - `get()` - 0%
   - `reset()` - 0%
   - `locations()` - 0%
   - `_print_config_dict()` - 0%

### High-Priority Gaps (< 50% coverage)

- **config_cli.py**: 29% overall - 109 missing lines
- **ggen.py**: 47% overall - 109 missing lines
- **team.py**: 50% overall - 84 missing lines

## Test Failures Analysis

**2 tests failed** out of 384:

1. `test_consolidate_command_success` - team.py consolidation mock issue
2. `test_create_resume_and_consolidate` - team creation validation error

Both failures are in team.py, which has the lowest coverage at 50%.

## HTML Coverage Report

Detailed line-by-line coverage report available at:
```
htmlcov/index.html
```

Features:
- Color-coded coverage (green=covered, red=uncovered, yellow=partial)
- Function-level breakdown
- Missing line identification
- Interactive highlighting

## Recommendations

### Phase 1: Critical (3-4 hours)
- [ ] Add tests for ggen.round_trip() (20+ test cases)
- [ ] Add tests for team.consolidate() (15+ test cases)
- [ ] Add tests for config_cli (30+ test cases)

### Phase 2: High Priority (5-6 hours)
- [ ] Add error path tests across all modules
- [ ] Improve team.list() coverage (39 missing lines)
- [ ] Improve ggen.export() coverage (28 missing lines)

### Phase 3: Medium Priority (4-5 hours)
- [ ] Add edge case handling tests
- [ ] Un-skip performance tests
- [ ] Add integration test suite

## Fixed Issues

Fixed syntax errors in team.py that prevented test execution:
- Removed invalid function calls from parameter lists (lines 34, 226-228)
- Fixed escaped quote characters (\" → ")
- Moved validation logic into function bodies

## Next Steps

1. Run coverage report: `pytest --cov=yawl_cli --cov-report=html test/`
2. View HTML report: Open `htmlcov/index.html` in browser
3. Prioritize fixes by coverage gaps listed above
4. Add integration tests for multi-command workflows
5. Set up CI/CD enforcement: `pytest --cov-fail-under=80`

---

**Files Modified**: 
- `/home/user/yawl/cli/yawl_cli/team.py` - Fixed syntax errors

**Reports Generated**:
- `COVERAGE_ANALYSIS.md` - Detailed gap analysis
- `htmlcov/` - Interactive coverage report
- `coverage.json` - Machine-readable coverage data

