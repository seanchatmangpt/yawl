# Fortune 5 SAFe Scale Testing - Quick Start Guide

## 1-Minute Setup

```bash
# Clone/navigate to YAWL directory
cd /home/user/yawl

# Verify Java 25+
java -version

# Compile test classes
mvn clean compile -pl yawl-safe -DskipTests
```

## Run Tests (Choose One)

### Option A: Baseline (Local, 5 minutes)
```bash
# Single-ART sanity check (1 ART, 6 teams, 30 stories)
FORTUNE5_SCALE_LEVEL=1 mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest -T 1
```

Expected output:
```
T1: PASS - Single ART PI planning completed in 300 seconds
T2: PASS - Story flow completed in 300 seconds with 4 transitions
T3: PASS - Dependency resolved in 100 seconds
```

### Option B: Medium Scale (CI, 30 minutes)
```bash
# 5 ARTs, 30 teams, 150 stories, multi-ART coordination
FORTUNE5_SCALE_LEVEL=5 mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest -T 1
```

Expected output:
```
T4: PASS - 5-ART PI planning completed in 900 seconds with 45 dependencies
T5: PASS - 5-ART dependency resolution completed in 300 seconds
T6: PASS - 30 stories completed in 1800 seconds (avg 60 sec/story)
```

### Option C: Full Scale (Main branch, 4+ hours)
```bash
# 30 ARTs, 180+ teams, 3,000+ stories, CRITICAL SLA TESTS
FORTUNE5_SCALE_LEVEL=30 mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest -T 1 -DfailIfNoTests=false
```

⚠️ **Hardware Requirements**:
- RAM: 32 GB minimum
- CPU: 16+ cores
- Disk: 500 GB
- Time: 4-5 hours

Expected output:
```
T7: PASS - Full-scale PI planning completed in 180 minutes
T7: 3000 stories assigned, 5000 dependencies discovered
T8: PASS - Full-scale dependency resolution completed in 15 minutes
T9: PASS - Portfolio allocation completed in 8 minutes
T10: PASS - Data consistency verified (no lost updates)
T11: PASS - M&A integration completed in 900 seconds
T12: PASS - Disruption response completed in 1800 seconds
T13: PASS - PI planning with 15% failure completed: 27 successful (90 %)
```

### Option D: Cross-ART Coordination (15 minutes)
```bash
# Dedicated cross-ART tests (dependency chains, bottlenecks, circular deps)
FORTUNE5_SCALE_LEVEL=5 mvn test -pl yawl-safe -Dtest=CrossARTCoordinationTest -T 1
```

Expected output:
```
C1: PASS - Two-ART dependency resolved
C2: PASS - Linear chain resolved in 180 seconds
C3: PASS - Bottleneck detected with 4 dependencies on provider
C4: PASS - Circular dependency correctly detected
C5: PASS - Parallel dependencies resolved in 120 seconds
...
C9: PASS - 30-ART simultaneous submission completed in 240 seconds
C10: PASS - Causality verified: NEGOTIATE → CONFIRM
```

### Option E: Chaos Engineering (2 hours)
```bash
# Test resilience: 5%, 10%, 15%, 20% failure rates
FORTUNE5_SCALE_LEVEL=30 mvn test -pl yawl-safe \
  -Dtest=FortuneFiveScaleTest#testPIPlanningWithAgentFailures -T 1
```

Expected output:
```
T13: PASS - PI planning with 5% failure completed: 29 successful (97 %)
T13: PASS - PI planning with 10% failure completed: 28 successful (93 %)
T13: PASS - PI planning with 15% failure completed: 27 successful (90 %)
T13: PASS - PI planning with 20% failure completed: 25 successful (83 %)
```

## Troubleshooting

### Out of Memory
```bash
export MAVEN_OPTS="-Xmx32g"
FORTUNE5_SCALE_LEVEL=30 mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest
```

### Test Hangs
```bash
# Increase timeout
mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest \
  -DargLine="-Djunit.jupiter.execution.parallel.mode.default=same_thread"
```

### Database Locks
```bash
# Run sequentially (not in parallel)
FORTUNE5_SCALE_LEVEL=30 mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest -T 1
```

### Compilation Errors
```bash
# Clean rebuild
mvn clean compile -pl yawl-safe -DskipTests -U
```

## Key Test Classes

| Class | File | Tests | Purpose |
|-------|------|-------|---------|
| **FortuneFiveScaleTest** | `FortuneFiveScaleTest.java` | 13 | Main scale tests (PI, deps, portfolio) |
| **CrossARTCoordinationTest** | `CrossARTCoordinationTest.java` | 10 | Cross-ART coordination & bottlenecks |

## Test Results Format

Each test outputs:
```
T<N>: PASS/FAIL - <Description>
T<N>: <Key Metrics> (e.g., "180 minutes", "5000 dependencies")
```

### Example: Full-Scale PI Planning
```
T7: PASS - Full-scale PI planning completed in 180 minutes
T7: 3000 stories assigned, 5000 dependencies discovered
```

✓ = SLA met
✗ = SLA exceeded (test fails)

## Performance Baselines

| Test | Scale | Expected Time | SLA |
|------|-------|---------------|-----|
| T1 | 1 ART | 5 min | <10 min |
| T4 | 5 ARTs | 15 min | <30 min |
| T7 | 30 ARTs | 180 min | <240 min |
| T8 | 5,000 deps | 15 min | <30 min |
| T9 | Portfolio | 8 min | <15 min |

## Debug Output

Enable verbose logging:
```bash
export MAVEN_OPTS="-Dorg.slf4j.simpleLogger.defaultLogLevel=debug"
FORTUNE5_SCALE_LEVEL=5 mvn test -pl yawl-safe -Dtest=FortuneFiveScaleTest
```

Output includes:
```
=== FORTUNE 5 SCALE TEST SETUP ===
Scale Level: 5 ARTs
Total Teams: 30
Total Stories: 150+
Setup complete: engine ready, 5 business units created

T4: Starting 5-ART parallel PI planning ceremony
T4: Executing 5 ARTs with 150 total stories
T4: PASS - 5-ART PI planning completed in 900 seconds with 45 dependencies
```

## Next Steps

1. ✅ Run baseline locally (Option A)
2. ✅ Integrate into CI/CD
3. ✅ Run medium scale nightly (Option B)
4. ✅ Run full scale on main branch (Option C)
5. ✅ Monitor SLA compliance

## Documentation

- **Full Strategy**: `/home/user/yawl/.claude/FORTUNE5_TEST_STRATEGY.md`
- **Test Suite README**: `README.md` (in this directory)
- **Delivery Summary**: `/home/user/yawl/.claude/FORTUNE5_DELIVERY_SUMMARY.md`

## Support

For issues or questions:
1. Check troubleshooting section above
2. Review test logs in `target/surefire-reports/`
3. Consult strategy document for test design rationale
4. Contact YAWL Foundation team

---

**Last Updated**: 2026-02-28
**Status**: Ready for Production Testing
