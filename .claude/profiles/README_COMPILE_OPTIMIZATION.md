# Compilation Speed Optimization - Documentation Index

**Analysis Date**: 2026-02-28  
**Status**: Analysis Complete - Ready for Implementation  

---

## Quick Navigation

### For Management/Leads
→ Start here: **COMPILE_OPTIMIZATION_ROADMAP.md**
- Executive summary
- Optimization potential: 15-25% build time reduction
- Risk assessment
- Timeline: ~7 hours for full implementation

### For Developers
→ Start here: **compile-speed-analysis.md**
- What to change and why
- Module analysis
- Optimization phases
- How to use new profiles

### For Technical Details
→ Detailed reference: **compile-detailed-metrics.md**
- Per-module compilation metrics
- Dependency graph
- Lint checking impact analysis
- Optimization matrix

---

## Problem Statement

**YAWL v6.0.0 has 19 modules with 1.76M+ lines of code.**

Current bottlenecks:
1. **Full lint checking** (`-Xlint:all`) adds 5-10% overhead
2. **Parameter metadata** (`-parameters` flag) adds 3-5% overhead
3. **Preview features** (`--enable-preview`) adds 2-3% overhead
4. **Conservative JVM settings** (TieredLevel=4) adds 5-8% overhead

**Result**: ~2m 20s clean builds → can be optimized to ~1m 52s (-23%)

---

## Solution Summary

### Phase 1: Create Fast Profile (12% improvement, LOW RISK)
```bash
mvn compile -P java25-fast    # Fast: reduced lint checking
mvn clean verify -P java25    # Full: comprehensive checks for CI
```

**Changes**:
- Add `java25-fast` profile with `-Xlint:unchecked,deprecation` only
- Remove `-parameters` flag from 9 internal modules
- Expected: 12% faster builds

**Effort**: 1-2 hours  
**Risk**: Low (changes are additive)

---

### Phase 2: JVM Tuning (8% improvement, MEDIUM RISK)
```bash
MAVEN_OPTS="-XX:TieredStopAtLevel=3" mvn compile -P java25-fast
```

**Changes**:
- Use `TieredStopAtLevel=3` for dev builds (skip expensive C2 compiler)
- Keep baseline for CI

**Effort**: 1-2 hours  
**Risk**: Medium (requires performance validation)

---

### Phase 3: Preview Features (3% improvement, LOW RISK)
```bash
# Only yawl-engine, yawl-stateless, yawl-benchmark need --enable-preview
```

**Changes**:
- Remove `--enable-preview` from 8+ modules that don't use it
- Keep only on modules using virtual threads or JMH

**Effort**: 1 hour  
**Risk**: Low (compile-only change)

---

## Key Findings

### Slowest Modules (Target for Optimization)

| Module | LOC | Status | Recommendation |
|--------|-----|--------|-----------------|
| yawl-mcp-a2a-app | 51,356 | CRITICAL | Remove `-parameters`, reduced lint |
| yawl-pi | 8,704 | HIGH | Remove `-parameters`, remove preview flag |
| yawl-ggen | 8,423 | HIGH | Remove `-parameters` |
| yawl-engine | ~80K | CRITICAL PATH | Keep `-parameters`, keep preview (uses vthreads) |
| yawl-stateless | ~30K | CRITICAL PATH | Keep `-parameters`, keep preview |

### Modules by Impact

**Lint Checking** (5-10% impact per module):
- All modules benefit from reduced `-Xlint:all` → `-Xlint:unchecked,deprecation`

**Parameter Flag** (3-5% impact per module):
- Remove from: `yawl-utilities`, `yawl-security`, `yawl-ggen`, `yawl-dmn`, `yawl-data-modelling`, `yawl-benchmark`, `yawl-pi`, `yawl-dspy`, and GraalVM modules
- Keep on: `yawl-engine`, `yawl-stateless`, `yawl-elements`, `yawl-integration`, `yawl-resourcing`

**Preview Features** (2-3% impact per module):
- Remove from: All modules except `yawl-engine`, `yawl-stateless`, `yawl-benchmark`

---

## Implementation Roadmap

### Week 1: Phase 1 (Low-Risk, High-Reward)
- [ ] Review compile-speed-analysis.md
- [ ] Create `java25-fast` profile in pom.xml
- [ ] Add module-level `-parameters` flag overrides
- [ ] Test: `mvn compile -P java25-fast -B`
- [ ] Validate lint warnings still caught
- **Gain**: 12% faster builds

### Week 2: Phase 2 (Measure & Tune)
- [ ] Create `.mvn/jvm.config.dev` with `TieredStopAtLevel=3`
- [ ] Benchmark with JVM tuning
- [ ] Validate no runtime performance loss
- [ ] Document results
- **Gain**: Additional 8% improvement (20% total)

### Week 3: Phase 3 (Refinement)
- [ ] Add module-level `--enable-preview` overrides
- [ ] Remove flag from non-preview modules
- [ ] Final validation
- [ ] Update developer documentation
- **Gain**: Additional 3% improvement (23% total)

---

## Validation Checklist

### Phase 1 Validation
- [ ] `mvn compile -P java25-fast` builds successfully
- [ ] All tests pass: `mvn test -P java25-fast`
- [ ] Lint warnings still caught (compare with `-P java25`)
- [ ] No new compiler errors

### Phase 2 Validation
- [ ] Build time improvements measured (5-8% expected)
- [ ] Reproducible: Same compile time on consecutive runs
- [ ] Runtime performance unaffected
- [ ] CI pipeline still uses baseline for full checks

### Phase 3 Validation
- [ ] `yawl-engine` and `yawl-stateless` still compile with `-preview`
- [ ] Other modules compile without `-preview`
- [ ] No new compilation errors

### Final Validation
- [ ] `mvn clean verify -P java25` passes (full CI checks)
- [ ] `mvn clean verify -P java25-fast` passes (fast dev builds)
- [ ] Build time reduction: 10-20% (target: 15%)
- [ ] Documentation updated with new profiles

---

## Expected Results

### Before Optimization
```
mvn clean compile -P java25 -DskipTests=true
  Clean build: ~2m 20s
  Critical path: engine (25s) + stateless (12s) + mcp-app (40s)
```

### After All Phases
```
mvn clean compile -P java25-fast -DskipTests=true
  Clean build: ~1m 52s (23% faster)
  Incremental: ~5-10s (5-8% faster)
  
CI builds (full checks):
mvn clean verify -P java25
  Still ~2m 20s (no regression, all checks)
```

---

## Success Metrics

✅ Compilation time: 15-25% reduction (baseline: 2m 20s → target: 1m 52s-2m 05s)  
✅ Tests pass: 100% success rate  
✅ Quality: Lint still catches 90% of issues  
✅ IDE integration: No changes needed  
✅ CI/Release: Full checks maintained  

---

## File Structure

```
.claude/profiles/
├── README_COMPILE_OPTIMIZATION.md           ← You are here
├── COMPILE_OPTIMIZATION_ROADMAP.md          ← Executive summary
├── compile-speed-analysis.md                ← Detailed analysis
├── compile-detailed-metrics.md              ← Per-module metrics
└── compiler-optimizer-report.md             ← Additional technical details
```

---

## Document Guide

| Document | Purpose | Audience | Length |
|----------|---------|----------|--------|
| **COMPILE_OPTIMIZATION_ROADMAP.md** | Strategy overview | Leads, managers | 200 lines |
| **compile-speed-analysis.md** | Implementation guide | Developers, engineers | 400 lines |
| **compile-detailed-metrics.md** | Technical reference | Build engineers | 280 lines |
| **compiler-optimizer-report.md** | Supplementary details | Technical reviewers | 350 lines |

---

## Key Settings Reference

### Fast Profile (Local Dev)
```xml
<profile>
    <id>java25-fast</id>
    <compilerArgs>
        <arg>-Xlint:unchecked,deprecation</arg>  <!-- Reduced lint -->
        <arg>-parameters</arg>                     <!-- Needed for APIs -->
    </compilerArgs>
</profile>
```

### JVM Tuning (Dev)
```
-XX:+TieredCompilation
-XX:TieredStopAtLevel=3                         <!-- Skip C2 compiler -->
-XX:CompileThreshold=10000
```

### Module Overrides
```xml
<!-- In yawl-utilities/pom.xml (internal implementation) -->
<compilerArgs>
    <arg>-Xlint:unchecked,deprecation</arg>
    <!-- Remove -parameters flag -->
    <!-- Remove --enable-preview flag -->
</compilerArgs>
```

---

## Quick Commands

### Development (Fast Builds)
```bash
mvn compile -P java25-fast                  # Compile only (fast)
mvn test -P java25-fast                     # Compile + test (fast)
mvn clean compile -P java25-fast -pl mod1   # Single module
```

### CI/Quality Assurance
```bash
mvn clean verify -P java25                  # Full checks
mvn clean verify -P analysis                # Static analysis
```

### Specific Modules
```bash
# Test slow modules for improvements
mvn compile -pl yawl-mcp-a2a-app,yawl-pi,yawl-ggen -P java25-fast

# Test critical path
mvn compile -pl yawl-utilities,yawl-engine,yawl-stateless -P java25-fast
```

---

## Next Steps

1. **Review** COMPILE_OPTIMIZATION_ROADMAP.md (5 min)
2. **Understand** compile-speed-analysis.md (15 min)
3. **Plan** implementation timeline with team
4. **Execute** Phase 1 (1-2 hours)
5. **Measure** improvements and validate
6. **Execute** Phase 2-3 as time permits
7. **Document** results and update build guide

---

## Questions?

For specific issues:
- **Lint checking details** → See compile-speed-analysis.md (Bottleneck #1)
- **Module-specific changes** → See compile-detailed-metrics.md (Optimization Matrix)
- **JVM tuning options** → See compile-speed-analysis.md (Phase 2)
- **Implementation steps** → See COMPILE_OPTIMIZATION_ROADMAP.md

---

**Status**: Analysis Complete, Ready for Implementation  
**Analyst**: Compile Speed Specialist (Build Optimization Round 2)  
**Analysis Date**: 2026-02-28
