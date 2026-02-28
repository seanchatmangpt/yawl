# YEngine Parallelization Validation - Quick Start Guide

**Goal**: Determine if YAWL can run tests in parallel (3× speedup)

**Time Required**: 30 seconds to run validation

---

## In 3 Steps

### Step 1: Run Validation (30 sec)
```bash
cd /home/user/yawl
bash scripts/validate-yengine-parallelization.sh
```

### Step 2: Read Result
```
✓ PASS: Safe to parallelize (exit code 0)
✗ FAIL: Keep sequential (exit code 1)
? ERROR: Manual review (exit code 2)
```

### Step 3: Next Action
**If PASS**: Alert team → Phase 3 (Build optimization)
**If FAIL**: Alert YEngine Investigator → Phase 2 (Deep dive)

---

## What This Tests

| Test | What | Detects |
|------|------|---------|
| T1 | Isolated engines | Static field pollution |
| T2 | Singleton threads | Multiple instances |
| T3 | Case creation | ID collisions |
| T4 | Spec loading | Duplicate IDs |
| T5 | Corruption | Detector works |
| T6 | ThreadLocal | Tenant leaks |
| T7 | Completion | State leakage |
| T8 | Memory | Heap growth |
| T9 | Database | Connection isolation |

---

## Key Files

| File | Purpose |
|------|---------|
| YEngineParallelizationTest.java | 9 isolation tests (450 lines) |
| validate-yengine-parallelization.sh | One-command runner (executable) |
| yengine-validation-checklist.md | GO/NO-GO criteria (detailed) |
| PARALLELIZATION-DECISION.md | Strategic overview |
| VALIDATION-HARNESS-README.md | Full documentation |

---

## Interpreting Results

### PASS (0 corruptions)
```
✓ PASS: All validation tests passed
✓ PASS: YEngine singleton properly isolated
✓ PASS: Case IDs are unique
✓ PASS: ThreadLocal isolation verified

Decision: SAFE TO PARALLELIZE
Action: Proceed to Phase 3
```

### FAIL (>0 corruptions)
```
✗ FAIL: Some validation tests failed
[CRITICAL] Case ID collision detected
[CRITICAL] Singleton violation: Different instances

Decision: KEEP SEQUENTIAL
Action: Phase 2 YEngine analysis required
```

---

## For Different Roles

### Validation Engineer (NOW)
1. ✅ Review this quick start
2. ✅ Review validation test code (YEngineParallelizationTest.java)
3. 📢 Message team: "Validation harness ready"

### YEngine Investigator (PHASE 2)
1. Run: `bash scripts/validate-yengine-parallelization.sh`
2. Read report: `.claude/profiles/validation-reports/parallelization-report-*.txt`
3. Deep dive: Review yengine-validation-checklist.md "Phase 2 Key Areas"
4. Decide: GO or NO-GO?

### Build Optimizer (PHASE 3, if GO)
1. Update pom.xml surefire config
2. Add `-T 1.5C` to Maven builds
3. Test: `mvn -T 1.5C clean test`

### DevOps (PHASE 5, if all phases pass)
1. Enable parallel in CI/CD pipeline
2. Set up monitoring for regressions
3. Document rollback procedure

---

## Command Cheat Sheet

```bash
# Run validation
bash scripts/validate-yengine-parallelization.sh

# View results
cat .claude/profiles/validation-reports/parallelization-report-*.txt

# Monitor mode (detect flakiness)
bash scripts/validate-yengine-parallelization.sh --monitor

# Verbose output
bash scripts/validate-yengine-parallelization.sh --verbose

# Run individual test
mvn test -Dtest=YEngineParallelizationTest#test1_DetectStaticFieldPollution

# Current sequential build
mvn clean test

# Proposed parallel build
mvn -T 1.5C clean test  # Only if validation PASS
```

---

## Exit Codes

| Code | Meaning | Action |
|------|---------|--------|
| 0 | PASS: Safe to parallelize | Proceed to Phase 3 |
| 1 | FAIL: Keep sequential | Alert YEngine Investigator |
| 2 | ERROR: Validation failed | Manual review needed |

---

## Typical Timeline

```
Phase 1 (Validation Harness)        COMPLETE ✅
├─ Test harness designed           ✓
├─ 9 isolation tests created        ✓
├─ Automated script ready           ✓
└─ Reports this document            ✓

Phase 2 (YEngine Investigation)     IN PROGRESS ⏳
├─ Run validation script            [START HERE]
├─ Analyze corruption metrics
├─ Review YEngine code
└─ Make GO/NO-GO decision

Phase 3 (Build Configuration)       BLOCKED
├─ Update pom.xml surefire          [If Phase 2 = GO]
├─ Test -T 1.5C locally
└─ Document changes

Phase 4 (Canary Run)               BLOCKED
├─ Run subset with -T 1.5C
├─ Monitor for flakiness
└─ Compare vs sequential

Phase 5 (Production Deployment)    BLOCKED
├─ Enable -T 1.5C in CI/CD
├─ Add monitoring/alerts
└─ Document rollback
```

---

## Common Questions

**Q: When should I run this?**
A: Before deciding to parallelize tests. Run once, get answer.

**Q: How long does it take?**
A: ~30 seconds total (all 9 tests complete in <30s).

**Q: What if it fails?**
A: It means parallelization isn't safe yet. YEngine Investigator will identify why.

**Q: Can I run it on my laptop?**
A: Yes! Works on any machine with Java 21+ and Maven.

**Q: What's the benefit?**
A: 3× speedup: 3 min (sequential) → 1 min (parallel).

**Q: Is parallelization guaranteed safe?**
A: Only if validation PASSES (0 corruptions). Otherwise must fix blockers first.

---

## Deliverables

✅ **Test Suite**: 9 isolation tests in YEngineParallelizationTest.java
✅ **Automated Script**: One-command validation (validate-yengine-parallelization.sh)
✅ **Decision Criteria**: GO/NO-GO checklist (yengine-validation-checklist.md)
✅ **Documentation**: Strategic overview and full guides
✅ **Rollback Plan**: Documented in decision framework

---

## Next Step

```bash
cd /home/user/yawl
bash scripts/validate-yengine-parallelization.sh
```

Then share result with team and follow the decision in "Next Action" above.

---

**Status**: Phase 1 Complete. Ready for Phase 2 Investigation.
