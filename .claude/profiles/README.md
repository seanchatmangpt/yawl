# YAWL Build Performance Baseline — Profiler Report

**Date**: 2026-02-28 03:02:57 UTC  
**Status**: BASELINE ESTABLISHED  
**Version**: YAWL v6.0.0-GA  

---

## Quick Summary

Comprehensive baseline performance analysis for YAWL multi-module Maven build system.

**Key Metrics**:
- **22 modules** organized in 6 build layers
- **360 source files** + **94 test files**
- **Largest module**: yawl-mcp-a2a-app (198 src, 29 test)
- **Regression threshold**: 10% per profile
- **Estimated full build**: 120-180 seconds

**Top Bottleneck**: Test execution time (>50% of build duration)

---

## Files in This Directory

### 1. `build-baseline.json` — Structured Metrics
Machine-readable baseline data for automated comparison and regression detection.

**Contents**:
- Environment (Java 25, Maven 4.0.0, ZGC config)
- Codebase metrics (modules, sources, tests by size/layer)
- Build configuration (parallelism, compiler, test settings)
- Optimization opportunities with ROI ranking
- Target metrics and regression thresholds

**Usage**: Parse with `jq` or Python `json` module for CI integration.

```bash
jq '.codebase_metrics.largest_modules_by_source' build-baseline.json
```

---

### 2. `BASELINE_ANALYSIS.md` — Detailed Report
Comprehensive analysis document for the optimization team.

**Sections**:
- Executive summary
- Module inventory (22 modules by layer)
- Build configuration deep-dive
- dx.sh optimization script analysis
- Identified bottlenecks (prioritized)
- Optimization opportunities with effort/impact
- Regression detection strategy
- Week-by-week optimization roadmap

**Target Audience**: Engineers, optimization team, build system maintainers

---

## How to Use This Baseline

### For Regression Detection (CI Integration)

```bash
#!/bin/bash
# Compare current build time against baseline
BASELINE_FILE=".claude/profiles/build-baseline.json"

START=$(date +%s)
bash scripts/dx.sh compile > /tmp/dx.log 2>&1
END=$(date +%s)
ELAPSED=$((END - START))

THRESHOLD=$(jq -r '.target_metrics.compile_target_seconds' "$BASELINE_FILE")
PCT_CHANGE=$(( (ELAPSED - THRESHOLD) * 100 / THRESHOLD ))

if [ "$PCT_CHANGE" -gt 10 ]; then
  echo "REGRESSION: dx.sh compile took ${ELAPSED}s (${PCT_CHANGE}% above baseline)"
  exit 2
fi
echo "OK: Build time within baseline tolerance"
```

### For Optimization Tracking

Create a performance log to track improvements over time:

```bash
# Append to .claude/profiles/build-metrics.jsonl
echo "{\"date\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\", \"compile_s\": 15, \"test_s\": 28, \"total_s\": 43}" >> build-metrics.jsonl

# Analyze trends
jq -s 'map(.total_s)' build-metrics.jsonl | python3 -c "import sys, json; times = json.load(sys.stdin); print(f'Avg: {sum(times)/len(times):.1f}s, Min: {min(times)}s, Max: {max(times)}s')"
```

### For Manual Investigation

Generate detailed test reports:

```bash
# Extract slowest tests
mvn surefire-report:report
grep -r "time=" target/surefire-reports/*.xml | \
  sed 's/.*name="//' | sed 's/" time="/: /' | sed 's/".*/s/' | \
  sort -t: -k2 -nr | head -20
```

---

## Optimization Roadmap

### WEEK 1: Measurement Phase
High-effort, high-return activities:

1. **Profile test execution times** (2 hours)
   - Generate Surefire reports
   - Identify slowest 20 tests
   - Classify by type (unit, integration, I/O, CPU)

2. **Verify JUnit Platform parallelism** (1 hour)
   - Run single module with logging
   - Measure single-threaded vs default parallel
   - Verify virtual thread utilization

### WEEK 2: Quick Wins
Lower-effort optimizations:

3. **Optimize slowest tests** (variable)
   - Parallelization (testng groups)
   - Fixture optimization (mocking, caching)
   - Database connection pooling

4. **Profile compiler memory** (1 hour)
   - Add GC logging during yawl-mcp-a2a-app build
   - Measure GC time percentage
   - Decide: keep 2GB or increase to 3-4GB

### WEEK 3: Deeper Optimization
Platform-level improvements:

5. **Virtual thread pools for tests** (4+ hours)
   - Implement for I/O-bound tests
   - Evaluate scoped values for context
   - Measure speedup

6. **JVM forking analysis** (3 hours)
   - Log fork timestamps
   - Quantify startup cost vs parallelism benefit
   - Tune fork count strategy

---

## Success Criteria

All items below must be satisfied for optimization work to be approved:

- [ ] Baseline metrics established and validated
- [ ] Slowest tests identified (top 10-20)
- [ ] JUnit Platform parallelism verified
- [ ] Compiler memory profiled
- [ ] Regression detection integrated into CI
- [ ] Team has 3-week optimization plan

---

## Monitoring & Alerts

### Regression Detection

**Trigger**: Any profile degrades >10% from baseline

**Profiles Monitored**:
- `dx.sh compile`: <15s target
- `dx.sh test`: <30s target
- `dx.sh all`: <150s target (estimated)

**Action**: Investigate root cause and revert if improvement cannot be achieved.

### Monthly Review

Last Friday of each month:
1. Collect build metrics from previous month
2. Compare against baseline
3. Identify trends (improving/degrading)
4. Report to team lead

---

## References

**Build System Files**:
- `/home/user/yawl/scripts/dx.sh` — Fast build script (change detection)
- `/home/user/yawl/pom.xml` — Maven configuration (plugins, parallelism)
- `/home/user/yawl/.claude/profiles/` — This baseline directory

**Key Configuration**:
- Java 25 with compact object headers (`-XX:+UseCompactObjectHeaders`)
- ZGC garbage collector (`-XX:+UseZGC`)
- Maven Surefire 3.5.4 with dynamic JUnit Platform
- Incremental compilation enabled
- 1.5C forked JVMs with reuseForks=true

---

## Contact & Questions

For questions about this baseline:
- Review BASELINE_ANALYSIS.md (detailed explanations)
- Check build-baseline.json (raw metrics)
- Run dx.sh with `DX_VERBOSE=1` for build logs
- Inspect POM plugin configurations

---

**Baseline Established**: 2026-02-28  
**Next Review**: 2026-03-31 (post-optimization Week 3)
