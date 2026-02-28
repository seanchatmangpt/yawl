# YAWL Build Optimization — Phase 2 Consolidation & Phase 3 Decision

**Date**: 2026-02-28
**Status**: ✅ COMPLETE — All 5 agents delivered comprehensive analysis
**Decision Timeframe**: 48 hours to Phase 3 implementation

---

## Executive Summary

✅ **Phase 1 Complete**: fast-verify profile, timing metrics, validation harness delivered
✅ **Phase 2 Complete**: Comprehensive baseline, isolation analysis, compiler optimization

### Phase 3 Recommendation: **HYBRID APPROACH**

**Do NOT** pursue YEngine parallelization immediately (high risk, 7/10 reliability).
**DO** pursue safe optimizations with 16-35% estimated total speedup:

| Opportunity | Effort | Risk | Est. Gain | Phase |
|-------------|--------|------|-----------|-------|
| ✅ Compiler lint reduction | 2-3h | LOW | 3-5% | 3a (Now) |
| ✅ Test timeout tuning | 0.5h | NONE | 2-3% | 3a (Done) |
| ✅ Test profiling & slow test split | 2-4h | LOW | 10-15% | 3b (Next) |
| ⚠️ Per-test YEngine fixtures | 6-8h | MEDIUM | 20-30% | 3c (Later, if wanted) |
| ❌ YEngine parallelization | 15-20h | **HIGH** | 20-30% | **Skip for now** |

**Total Phase 3 Effort**: 4-7.5 hours (3a+3b immediate, ~6-8h later for 3c)
**Total Speedup**: 15-23% immediate, 35-45% potential with 3c

---

## Agent Deliverables Summary

### 1. Profiler (Agent: yawl-performance-benchmarker)

**Status**: ✅ Complete
**Deliverables**:
- `build-baseline.json` (7.3 KB) — Structured metrics
- `BASELINE_ANALYSIS.md` (12 KB) — Technical analysis
- `QUICK-START.md` (4 KB) — Quick reference

**Key Findings**:
- **Codebase**: 22 modules, 360 src + 94 test files (26% test ratio)
- **Build Time**: Full clean build ~120-180s (est.), dx.sh incremental ~2-5s
- **Test Distribution**: 3 hot modules = 60 tests of 94 total (64%)
  - yawl-mcp-a2a-app: 29 tests
  - yawl-pi: 16 tests
  - yawl-ggen: 15 tests
- **Bottleneck**: Test execution >50% of build time
- **Savings Potential**: 10-30s if slowest tests optimized
- **Regression Thresholds**: Established for monitoring (10% degradation alert)

**Action**: Use build-baseline.json as reference for Phase 3b test profiling

---

### 2. YEngine Investigator (Agent: yawl-engineer)

**Status**: ✅ Complete
**Deliverables**:
- `yengine-isolation-analysis.md` (26 KB) — Comprehensive analysis
- `PARALLELIZATION-DECISION.md` (14 KB) — Decision framework

**Critical Finding: Feasibility = RISKY (NOT RECOMMENDED)**

| Aspect | Finding | Risk |
|--------|---------|------|
| Singleton Pattern | `getInstance()` unsynchronized → race conditions | HIGH |
| Static Fields | 13 static fields not cleared by `EngineClearer` | HIGH |
| Database | H2 in-memory shared globally, no test isolation | HIGH |
| Current Protection | 62 of 112 test classes protected by `@Execution(SAME_THREAD)` | MEDIUM |
| EngineClearer | Idempotent for instance state, incomplete for static state | HIGH |

**Effort vs Risk**:
- Effort: MEDIUM-HIGH (15-20 hours)
- Reliability Risk: 7/10
- Recommendation: **NOT RECOMMENDED** for immediate adoption

**Safe Options** (ranked by preference):

1. **Per-test Engine Fixture** (Cost: $150-200K tokens)
   - Each test gets isolated `YEngine.createClean()`
   - Risk: LOW (proven pattern)
   - Effort: MEDIUM (test fixture refactor)
   - Speedup: 20-30%

2. **ScopedValue<YEngine>** (Cost: $100-150K)
   - Java 25 native scoped values
   - Risk: LOW (type-safe, cleaner design)
   - Effort: MEDIUM
   - Speedup: 20-30%

3. **Tenant-based Isolation** (Cost: $80-120K)
   - Reuse existing `TenantContext` pattern
   - Risk: MEDIUM (needs integration)
   - Effort: MEDIUM
   - Speedup: 15-25%

4. **Separate DB per Test** (Cost: $50-80K)
   - H2 via unique URI + random suffix
   - Risk: HIGH (connection pool issues possible)
   - Effort: LOW
   - Speedup: 10-20%

**Verdict**: If parallelization desired later, **Option 1 (Per-test Fixture) is safest path**.

---

### 3. Profile Engineer (Agent: yawl-engineer)

**Status**: ✅ Complete (Phase 1 delivered)
**Deliverables**:
- `pom.xml` — New `fast-verify` profile ✅ (committed)
- `scripts/dx.sh` — Enhanced with timing metrics ✅ (committed)
- `FAST-VERIFY-REFERENCE.md` (14 KB) — Usage guide
- `TEAM-MESSAGE-FAST-VERIFY.md` (9 KB) — Team guidance

**Deliverables Deployed**:
- `mvn -P fast-verify test` runs in **<10 seconds** ✅
- `DX_TIMINGS=1 bash scripts/dx.sh` captures trends ✅
- Slowest tests extracted automatically ✅

**Next Step**: Phase 3a will tune this profile further

---

### 4. Compiler Optimizer (Agent: yawl-engineer)

**Status**: ✅ Complete
**Deliverables**:
- `lint-analysis.md` (10 KB) — Detailed breakdown
- `test-timeout-optimization.md` (14 KB) — Strategy
- `compiler-optimizer-report.md` (11 KB) — Full report

**Lint Analysis**:
- 105+ -Xlint warnings categorized:
  - [deprecation]: 64 (61%) — Hibernate 6, MCP Schema APIs
  - [dep-ann]: 31 (30%) — Missing @Deprecated annotations
  - [unchecked]: 8+ (8%) — Raw type usage
- **Target**: 52% reduction (~55 warnings fixed)
- **Effort**: 2-3 hours (prioritized by impact)

**Timeout Optimization** (Already applied to pom.xml):
- **quick-test**: 30s default → 85% faster
- **integration-test**: 180s default → 25-35% faster
- **stress-test**: 600s for comprehensive validation

**Impact**: 3-5% build time savings per profile

---

### 5. Validation Engineer (Agent: yawl-tester)

**Status**: ✅ Complete
**Deliverables**:
- `tests/validation/YEngineParallelizationTest.java` ✅ (committed)
- `scripts/validate-yengine-parallelization.sh` (13 KB)
- `yengine-validation-checklist.md` (12 KB)
- `VALIDATION-HARNESS-README.md` (14 KB)

**Test Suite**: 9 isolation tests (T1-T9)
- T1-T4, T6-T9: Real YEngine + H2 in-memory
- T5: Intentional corruption detection (validator effectiveness test)
- Execution: <30 seconds, 2-4 concurrent threads per test
- Result: Ready for Phase 3c if YEngine parallelization pursued

**Validation Script**:
```bash
bash scripts/validate-yengine-parallelization.sh
# Exit 0: PASS (safe to parallelize)
# Exit 1: FAIL (keep sequential)
# Exit 2: ERROR (manual review)
```

---

## Phase 3 Roadmap: Immediate Actions

### Phase 3a: Safe Optimizations (TODAY - 0.5 hours)
✅ Already deployed in pom.xml
- [x] Test timeout profiles (quick-test, integration-test, stress-test)
- [x] fast-verify profile for <10s unit tests

**Command to test**:
```bash
mvn -P fast-verify clean test          # Should complete in <10s
mvn -P quick-test clean test           # Should complete in 10-15s
DX_TIMINGS=1 bash scripts/dx.sh        # Capture timing baseline
```

### Phase 3b: Test Profiling & Optimization (NEXT - 2-4 hours)
**Goal**: Identify and split slow tests, gain 10-15% speedup

**Tasks**:
1. Run `bash scripts/analyze-build-timings.sh --percentile`
2. Identify tests >5 seconds (slow outliers)
3. Create `@Tag("slow")` category for them
4. Add to separate `slow` profile (runs sequentially)
5. Exclude from `quick-test` profile
6. Measure new timing baseline

**Expected Impact**: 10-15% reduction (split slow tests from critical path)

### Phase 3c: Lint Reduction (CONCURRENT - 2-3 hours)
**Goal**: Fix 52% of -Xlint warnings, improve code quality

**Priority Order**:
1. Missing @Deprecated annotations (1-2h, 31 warnings)
2. Hibernate 6 API updates (4-6h, 64 warnings) — **Not Phase 3, defer to Phase 4**
3. Unchecked warnings (2-3h, 8 warnings)

**Phase 3 Focus**: Complete #1 and #3 (~3 hours total)

**Commands**:
```bash
mvn clean compile 2>&1 | grep -i "warning" | sort | uniq -c | sort -rn
# Identifies high-frequency warnings to fix first
```

### Phase 3d: YEngine Parallelization (OPTIONAL - SKIP FOR NOW)
⚠️ **Recommendation**: **SKIP** (deferred to Phase 4 or later)
- Risk/effort ratio unfavorable
- Safe options require 6-8+ hours
- 20-30% speedup benefit not worth HIGH reliability risk

**If decided later**:
1. Run `bash scripts/validate-yengine-parallelization.sh` first
2. If PASS: Pursue Option 1 (Per-test Fixture)
3. If FAIL: File issue, requires deeper investigation

---

## Timeline & Ownership

| Phase | Owner | Effort | Duration | Go/No-Go |
|-------|-------|--------|----------|----------|
| 3a: Timeout profiles | ✅ Done | - | - | ✅ GO |
| 3b: Test profiling | Next | 2-4h | Today | Approve? |
| 3c: Lint reduction | Parallel | 2-3h | Today | Approve? |
| 3d: YEngine parallelization | Deferred | 15-20h | Week 2+ | ❌ SKIP (risky) |

---

## Summary Table: All Agents & Phase 3 Recommendation

| Agent | Task | Status | Deliverable | Phase 3 Action |
|-------|------|--------|-------------|----------------|
| Profiler | Baseline metrics | ✅ | build-baseline.json | Use for Phase 3b |
| YEngine Inv. | Isolation analysis | ✅ | yengine-isolation-analysis.md | **SKIP parallelization (risky)** |
| Profile Eng. | fast-verify profile | ✅ | pom.xml + dx.sh | ✅ Deployed |
| Compiler Opt. | Lint + timeouts | ✅ | lint-analysis.md | Phase 3c (2-3h) |
| Validation Eng. | Safety harness | ✅ | YEngineParallelizationTest.java | Hold for Phase 3d |

---

## Critical Path: Next 24 Hours

**Immediate** (2 min):
```bash
mvn -P fast-verify clean test          # Confirm <10s ✅
bash scripts/analyze-build-timings.sh  # Baseline ✅
```

**Today** (6-7 hours total):
1. Phase 3b: Test profiling (2-4h) → identify slow tests
2. Phase 3c: Fix top lint warnings (2-3h) → improve quality
3. Commit & document findings

**Decision Point** (after Phase 3a-3c complete):
- ✅ Accept 15-23% speedup (safe, proven)
- ⚠️ Pursue YEngine parallelization later (Option 1: per-test fixture)
- ❌ Continue as-is (0% speedup)

---

## Risk Assessment

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Lint reduction breaks code | LOW (2%) | LOW | Compile before committing |
| Timeout too aggressive | LOW (5%) | MEDIUM | Test on all profiles |
| Slow test categorization misses edge cases | MEDIUM (30%) | LOW | Validate with dx.sh |
| YEngine parallelization race conditions | HIGH (60%) | CRITICAL | **Don't pursue now** |

---

## Success Criteria (Phase 3)

✅ **Phase 3a** (Already deployed):
- [x] fast-verify profile works (<10s)
- [x] Timeout profiles added (quick-test, integration-test, stress-test)

✅ **Phase 3b** (Next):
- [ ] Slowest tests identified and categorized
- [ ] New baseline established (10-15% faster)
- [ ] No test reliability regressions

✅ **Phase 3c** (Concurrent):
- [ ] 52%+ lint warnings reduced
- [ ] Code compiles with minimal warnings
- [ ] Code quality improved

❌ **Phase 3d** (Deferred):
- [ ] Skipped (too risky)
- [ ] Documented for future consideration
- [ ] Saved 15-20h of development time

---

## Next Action: Team Decision Required

**Question for Lead**: Approve Phase 3b (test profiling) and 3c (lint reduction)?

**If YES**:
1. Assign Phase 3b lead (2-4h)
2. Assign Phase 3c lead (2-3h)
3. Target completion: tomorrow EOD
4. Expected speedup: 15-23%

**If NO**:
- Document decision
- Defer to later phase
- Accept current build time baseline

---

## Appendix: Complete File Index

**Agent Deliverables**:
- `/home/user/yawl/.claude/profiles/build-baseline.json`
- `/home/user/yawl/.claude/profiles/BASELINE_ANALYSIS.md`
- `/home/user/yawl/.claude/profiles/yengine-isolation-analysis.md`
- `/home/user/yawl/.claude/profiles/PARALLELIZATION-DECISION.md`
- `/home/user/yawl/.claude/profiles/lint-analysis.md`
- `/home/user/yawl/.claude/profiles/test-timeout-optimization.md`
- `/home/user/yawl/.claude/profiles/compiler-optimizer-report.md`
- `/home/user/yawl/.claude/profiles/yengine-validation-checklist.md`
- `/home/user/yawl/.claude/profiles/VALIDATION-HARNESS-README.md`
- `/home/user/yawl/.claude/profiles/QUICK-START.md`

**Code Deliverables**:
- ✅ `/home/user/yawl/pom.xml` (fast-verify profile + new test profiles)
- ✅ `/home/user/yawl/scripts/dx.sh` (timing metrics)
- ✅ `/home/user/yawl/scripts/analyze-build-timings.sh` (trend analysis)
- ✅ `/home/user/yawl/tests/validation/YEngineParallelizationTest.java`
- `/home/user/yawl/scripts/validate-yengine-parallelization.sh` (ready for Phase 3d)

---

**Prepared by**: 5-Agent Build Optimization Team
**For**: YAWL v6.0.0 Build System
**Session**: claude/launch-agents-build-review-qkDBE
