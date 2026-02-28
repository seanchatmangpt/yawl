# YAWL Build Optimization Guide — Best Practices

**Date**: 2026-02-28
**Audience**: Developers, DevOps, Team Leads
**Quick Reference**: 5-10 minute read

---

## Quick Decision Guide

### "I want the fastest possible feedback loop"

**Time to feedback**: 5-15 seconds

```bash
bash scripts/dx.sh                    # Recommended: auto-detects changed modules
# Typical: 7-12 seconds
```

**Why**:
- Only compiles/tests changed modules
- Skips expensive checks (JaCoCo, analysis)
- Enables parallel test execution
- Incremental compilation

---

### "I need to verify my code before commit"

**Time to feedback**: 30-60 seconds

```bash
bash scripts/dx.sh all                # Compiles and tests all modules
# Typical: 45-60 seconds
```

**Why**:
- Comprehensive local validation
- Catches module interaction issues
- Same optimizations as single module
- Still much faster than CI pipeline

---

### "My CI is slow and I need to optimize it"

**Time to feedback**: 2-5 minutes

```bash
# Fast path (default on most CI)
mvn -T 1.5C clean verify              # 90-120s

# Or with explicit profiles
mvn -T 2C verify -P integration-parallel  # 80-100s

# Add analysis on main branch only
if [ "$GITHUB_REF" = "refs/heads/main" ]; then
  mvn verify -P analysis              # +4-5 minutes
fi
```

**Optimizations enabled**:
- Parallel module compilation: `-T 1.5C`
- Parallel test execution: JUnit 5 concurrency
- Parallel integration tests: Failsafe forking
- Incremental compilation (warm builds)
- Build cache (distributed optional)

---

### "I want the most comprehensive build possible (release)"

**Time to feedback**: 5-10 minutes

```bash
# Full validation with analysis and coverage
mvn clean verify -P coverage,analysis

# Or step by step
mvn clean compile
mvn test
mvn verify
mvn test -P analysis
```

**Includes**:
- Full compilation
- All unit tests
- All integration tests
- Code coverage (JaCoCo)
- Static analysis (SpotBugs, PMD)
- Security scanning

---

## Section 1: Common Optimization Patterns

### Pattern 1: Rapid Development Loop

**Goal**: Minimize feedback time while developing

**Usage**:
```bash
# Edit code
# Run fast feedback
bash scripts/dx.sh

# Verify before commit
bash scripts/dx.sh all

# Push with confidence
git push
```

**Performance**: 5-15s per cycle

**When to use**: Daily development, debugging, spike work

**Cost**: Lightweight CI only, no deep analysis

---

### Pattern 2: Pull Request Validation

**Goal**: Balance speed and coverage for PRs

**CI Configuration**:
```yaml
# .github/workflows/pr.yml
- name: Build & Test
  run: mvn -T 1.5C clean verify

- name: Coverage Report
  run: mvn test -P coverage
  if: always()
```

**Performance**: 3-5 minutes

**When to use**: Automated PR checks, before merge

**Coverage**: All tests, incremental analysis

---

### Pattern 3: Main Branch Quality Gate

**Goal**: Comprehensive validation before release

**CI Configuration**:
```yaml
# .github/workflows/main.yml
jobs:
  fast-build:
    runs-on: ubuntu-latest
    steps:
      - run: mvn -T 2C clean verify

  analysis:
    needs: fast-build
    runs-on: ubuntu-latest
    steps:
      - run: mvn verify -P analysis
      - run: mvn test -P coverage
```

**Performance**: 5-10 minutes (parallel jobs)

**When to use**: After merge to main, before release

**Coverage**: Tests + analysis + coverage + security scanning

---

### Pattern 4: Performance-Critical Optimization

**Goal**: Focus on throughput when performance matters

**Usage**:
```bash
# Profile the build
mvn clean verify -X | grep "mojo execution time" | sort -k NF -n

# Identify bottlenecks (yawl-engine compile time, YNetRunner tests)

# Optimize:
# 1. Modularize heavy modules
# 2. Parallelize slow tests
# 3. Use remote cache (CI)
# 4. Monitor trends

# Verify improvement
mvn clean verify -Dorg.slf4j.simpleLogger.defaultLogLevel=info
```

**When to use**: After build times exceed targets (>150s cold, >90s warm)

**Tools**: Profiling, metrics collection, trend analysis

---

## Section 2: When to Use Each Optimization

### Parallel Compilation (`-T 1.5C`)

**When to enable**: Always
**Cost**: Minimal (marginal CPU/memory increase)
**Benefit**: +30-50% speedup
**Risk**: None (Maven handles correctly)

**Enable in**:
- `.mvn/maven.config`: `-T 1.5C`
- Or per-command: `mvn -T 2C clean verify`

```bash
# Check current setting
mvn help:describe | grep threads
```

---

### Semantic Caching

**When to enable**: When code changes frequently (daily development)
**Cost**: Disk space (~2GB for cache)
**Benefit**: +50-200% speedup on warm builds
**Risk**: Cache invalidation issues (rare)

**Enable automatically**:
- Default in Maven 4.0+
- Compares source file timestamps + compiler config

**Troubleshoot**:
```bash
# Clear cache if corrupted
mvn dependency:purge-local-repository

# Monitor cache
ls -lh .m2/repository/ | tail -20
```

---

### Fail-Fast Pipeline

**When to enable**: When fast feedback is priority
**Cost**: May stop at first failure
**Benefit**: Fail 10-30s earlier
**Risk**: May not catch all issues

**Enable in Surefire/Failsafe**:
```xml
<skipAfterFailureCount>1</skipAfterFailureCount>
```

**Or at command line**:
```bash
mvn test -Dorg.apache.maven.surefire.skipAfterFailureCount=1
```

---

### Warm Cache Strategy

**When to enable**: Between local builds, especially in CI
**Cost**: Initial setup, disk monitoring
**Benefit**: +30-50% speedup on warm builds
**Risk**: Cache misses if dependencies change

**Warm cache practice**:
```bash
# Before intensive development
mvn clean install                 # First build (cold)

# Later builds (warm)
mvn clean verify                  # Uses cache

# After dependency update
mvn clean install -U              # Force refresh
```

---

### JUnit Parallel Execution

**When to enable**: When test suite is large (>50 tests)
**Cost**: May reveal test isolation bugs
**Benefit**: +50-200% speedup (depends on isolation)
**Risk**: Flaky tests, thread safety issues

**Enable in pom.xml**:
```xml
<properties>
  <parallel>methods</parallel>
  <threadCount>1.5C</threadCount>
</properties>
```

**Verify**:
```bash
# Run tests multiple times
for i in {1..5}; do mvn test -T 2C; done

# All should pass 100%
```

---

### TIP Test Predictions

**When to enable**: Large test suites with CI constraints
**Cost**: Setup time, prediction model training
**Benefit**: +20-40% speedup (run most likely failures first)
**Risk**: Prediction accuracy <100%

**Status**: Available in Phase 4+ (not default yet)

**Future use**:
```bash
mvn test -P tip-predictions        # When available
```

---

## Section 3: Anti-Patterns to Avoid

### Anti-Pattern 1: "Disable caching to force rebuild"

❌ **Bad**:
```bash
mvn clean install -DskipTests     # Disables cache
# Time: 150-180s
```

✅ **Good**:
```bash
mvn install                        # Uses cache
# Time: 30-50s (warm)
```

**Why**: Caching provides 2-3x speedup with zero risk

---

### Anti-Pattern 2: "Run full suite for every change"

❌ **Bad**:
```bash
for every commit:
  mvn clean verify -P analysis    # Full suite, 5-10 min
```

✅ **Good**:
```bash
# Local development: fast feedback
bash scripts/dx.sh               # 5-15s

# Before push: verify
bash scripts/dx.sh all           # 30-60s

# CI/CD: comprehensive only on main
if main_branch:
  mvn verify -P analysis         # 5-10 min
```

**Why**: Fast feedback on local development, comprehensive validation in CI

---

### Anti-Pattern 3: "Ignore cache invalidation"

❌ **Bad**:
```bash
# Work for a week, cache becomes stale
mvn install                      # Using old cache
# Test runs in 10s (old results!)
```

✅ **Good**:
```bash
# Refresh cache after dependency updates
mvn clean install -U             # -U forces update

# Monitor cache health
find .m2/repository -name ".lastUpdated" -mtime +7 -delete
```

**Why**: Stale cache can hide real failures

---

### Anti-Pattern 4: "Use more threads than CPU cores"

❌ **Bad**:
```bash
# 8-core machine, but...
mvn -T 4C clean verify          # 32 threads (context switching overhead)
# Time: 150s (slower than -T 2C)
```

✅ **Good**:
```bash
# Match hardware
mvn -T 1.5C clean verify        # 12 threads (8 cores × 1.5)
# Time: 85-90s (optimal)
```

**Why**: Diminishing returns after CPU count; scheduling overhead increases

---

### Anti-Pattern 5: "Skip tests to speed up locally"

❌ **Bad**:
```bash
mvn clean compile -DskipTests   # Compiles, no tests
# Discover failures in CI
```

✅ **Good**:
```bash
bash scripts/dx.sh              # Compiles + tests locally
# Catch failures early, fix locally
```

**Why**: Tests catch bugs before they reach CI

---

## Section 4: Decision Tree for Different Scenarios

### "Tests are flaky in parallel, stable in sequential"

**Diagnosis**: Test isolation issue (shared state)

**Steps**:
1. Mark test as `@Execution(ExecutionMode.SAME_THREAD)`
2. Identify shared state (static fields, singletons)
3. Fix isolation: use `@BeforeEach` instead of static setup
4. Re-enable parallel execution
5. Verify >5 runs without flakiness

**Prevention**:
```java
// Good
@BeforeEach
void setup() { this.counter = 0; }

// Bad
static int counter = 0;  // Shared across tests!
```

---

### "Build suddenly got slower"

**Diagnosis tree**:

1. **Did dependencies change?**
   ```bash
   git diff pom.xml | grep -E "version|dependency"
   ```
   → Dependency version larger: recompile cascade
   → Fix: Semantic versioning, dependency convergence

2. **Did code change significantly?**
   ```bash
   git log --oneline -20 | head -5
   mvn clean compile -X | grep "mojo execution time"
   ```
   → New code is larger: expected
   → Fix: Monitor trend, profil if >5% slower

3. **Did system resources change?**
   ```bash
   free -h              # Memory available
   lscpu | grep core    # CPU cores
   ```
   → Less memory: reduce parallelism
   → Fix: Increase heap or reduce `-T` threads

4. **Is cache corrupted?**
   ```bash
   find .m2/repository -name ".lastUpdated" | wc -l
   ```
   → Many stale entries: clear cache
   → Fix: `mvn dependency:purge-local-repository`

---

### "Can't decide between options"

**Decision criteria** (in order of importance):

1. **Speed matter most?** → Use `bash scripts/dx.sh` (always fastest locally)
2. **Reliability matter most?** → Use `bash scripts/dx.sh all` (comprehensive)
3. **Resource constrained?** → Use sequential mode (`mvn clean verify`)
4. **Need analysis/coverage?** → Use `-P analysis` profile (on CI/CD only)

---

## Section 5: Monitoring & Metrics

### Key Metrics to Track

**Weekly**:
- Average build time (target: 85-95s)
- Test pass rate (target: 100%)
- Flakiness incidents (target: 0)

**Monthly**:
- Build time trend (should be stable)
- Cache hit rate (target: >85%)
- Resource utilization (CPU 60-75%, mem <1.2GB)

**Quarterly**:
- Slowest modules (identify optimization opportunities)
- Test execution time (identify slow tests)
- Developer satisfaction (survey)

### Tools

**Collection**:
```bash
bash scripts/collect-build-metrics.sh --runs 5
```

**Monitoring**:
```bash
bash scripts/monitor-build-performance.sh
```

**Dashboard**: `.yawl/metrics/dashboard.json`

---

## Section 6: Training Checklist

### For Developers

- [ ] Understand `bash scripts/dx.sh` (local development)
- [ ] Know when to use `-T 1.5C` (parallel compilation)
- [ ] Can identify slow modules (profiling)
- [ ] Know how to clear cache (`mvn dependency:purge-local-repository`)
- [ ] Understand fail-fast benefits and tradeoffs
- [ ] Can interpret build metrics dashboard

**Training time**: 15-30 minutes

---

### For DevOps

- [ ] Configured Maven parallel execution (CI/CD)
- [ ] Set up distributed cache (optional, recommended for CI)
- [ ] Configured GC optimization (ZGC)
- [ ] Monitoring build performance (weekly)
- [ ] Can respond to performance regression alerts
- [ ] Documented hardware-specific tuning

**Training time**: 1-2 hours

---

### For Tech Leads

- [ ] Understand performance baseline and targets
- [ ] Know ROI calculation ($2,130/dev/year)
- [ ] Can explain optimization rationale
- [ ] Review quarterly performance report
- [ ] Make decisions on next optimizations
- [ ] Mentor team on best practices

**Training time**: 2-3 hours

---

## Section 7: Troubleshooting Quick Reference

| Symptom | Probable Cause | Solution |
|---------|---|---|
| `OutOfMemoryError` | Heap too small | Increase `-Xmx` in `.mvn/jvm.config` |
| Tests fail parallel, pass sequential | Test isolation | Mark `@Execution(SAME_THREAD)`, fix state |
| Build suddenly slower | Dependency change or cache corruption | Check git diff, clear cache |
| High CPU, slow build | Too many threads | Reduce `-T` from 2C to 1.5C |
| Test timeouts | Heavy tests + parallelism | Increase timeout or reduce parallelism |
| "Out of memory" on CI | CI machine smaller than local | Check `-Xmx` config, reduce `-T` |
| Cache misses increasing | Old cache entries | Run `mvn dependency:purge-local-repository` |
| Tests hang/deadlock | Thread safety issue | Add timeout, investigate logs |

---

## Appendix: Command Reference

```bash
# Fast local iteration
bash scripts/dx.sh                      # Single module

# Comprehensive local verification
bash scripts/dx.sh all                  # All modules

# CI/CD pipeline
mvn -T 1.5C clean verify                # Fast

# With analysis (main branch only)
mvn verify -P analysis                  # Slower, comprehensive

# Profile build
mvn clean verify -X | grep "mojo execution time"

# Monitor during build
jstat -gc -h10 <pid> 1000              # GC stats
top -b -n 1 | head -n 3                 # CPU/Memory

# Clear cache
mvn dependency:purge-local-repository

# Collect metrics
bash scripts/collect-build-metrics.sh --runs 5

# View dashboard
cat .yawl/metrics/dashboard.json | jq '.'
```

---

**Last Updated**: 2026-02-28
**Maintained By**: YAWL Performance Team
**Questions?**: See `.yawl/PERFORMANCE-REPORT.md` for detailed metrics
**Next Review**: 2026-03-28 (monthly)
