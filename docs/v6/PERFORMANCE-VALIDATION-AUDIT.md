# YAWL v6.0.0 Performance & Benchmark Documentation Audit

**Date:** 2026-02-20
**Auditor:** Performance Validation Agent
**Status:** CRITICAL ISSUES FOUND

## EXECUTIVE SUMMARY

The performance documentation and benchmark reports contain **significant discrepancies** between documented claims and actual capabilities:

1. **Java Version Mismatch**: Documentation claims Java 25 support, but actual environment is Java 21
2. **JVM Flag Validity**: Some recommended flags are Java 25-specific and unavailable in Java 21
3. **AOT Cache Feature**: Documented AOT caching is Java 25 exclusive, not available in current system
4. **Benchmark Environment**: Performance report documents Java 25 test environment, but may not reflect actual Java 21 performance
5. **GC Configuration**: Some GC tuning recommendations are suboptimal for Java 21

## CRITICAL FINDINGS

### 1. Java Version Discrepancy

**Documentation Claims:**
- File: `/home/user/yawl/docs/v6/performance/PERFORMANCE_REPORT_20260219_101532.md`
- States: "Java | 25 (preview enabled)"
- References: `-XX:AOTCacheFile`, `-XX:UseAOTCache` (Java 25 exclusive)

**Actual System:**
```
openjdk version "21.0.10" 2026-01-20
OpenJDK 64-Bit Server VM (build 21.0.10+7-Ubuntu-124.04, mixed mode, sharing)
```

**Impact:** 
- AOT Cache features are NOT AVAILABLE in Java 21
- Startup improvement claims (section 6.3: "25% faster startup 3.2s -> 2.4s") are UNVERIFIABLE
- Virtual thread performance characteristics differ between Java 21 and Java 25

### 2. Java 25-Specific Flags in Production Configuration

**File:** `/home/user/yawl/docs/v6/upgrade/PERFORMANCE-GUIDELINES.md` (line 460-470)

**Problematic Recommendations:**

```
AOT Cache (Java 25):
java -XX:StartFlightRecording=filename=startup.jfr,duration=30s \
     -XX:+TieredCompilation \
     -jar yawl-engine.jar &

java -XX:+UseAOTCache \
     -XX:AOTCacheFile=startup.jfr \
     -jar yawl-engine.jar
```

**Issue:** `-XX:+UseAOTCache` and `-XX:AOTCacheFile` are Java 25 exclusive and will fail silently or be ignored in Java 21.

**Consequence:** Documented 25% startup improvement will NOT be achieved in Java 21 deployments.

### 3. Compact Object Headers Flag

**Documentation:** Lines 136, 174-192
**Flag:** `-XX:+UseCompactObjectHeaders`

**Status Check:**
- Java 21: PARTIALLY SUPPORTED (experimental)
- Java 25: SUPPORTED (stable)
- Impact: 5-10% throughput improvement is possible in Java 21, but flag may be unstable

### 4. GC Configuration Recommendations

**Issue:** GC selection matrix (lines 165-170) recommends G1GC as default for < 4GB heaps, which is correct for Java 21. However:

1. ZGC flag `-XX:ZGenerational=true` is Java 25 exclusive
2. ZGC in Java 21 doesn't support generational mode
3. Shenandoah flag usage is correct but outdated

**Recommendation Validity:**
- G1GC: ✅ VALID (recommended)
- Shenandoah: ✅ VALID (but less preferred than ZGC in modern builds)
- ZGC with ZGenerational: ❌ INVALID for Java 21

### 5. Benchmark Report Validity

**File:** `/home/user/yawl/docs/v6/performance/PERFORMANCE_REPORT_20260219_101532.md`

**Claims Made:**
- Memory per session: 24.93 KB (WARN - target was <10KB)
- MCP Tool call latency P95: 0.146 ms (PASS)
- Concurrent throughput: 42,017 ops/sec (WARN - target was >50,000)

**Issues:**
1. Test environment claims Java 25 but may have been run on Java 21
2. AOT cache performance claims cannot be verified without Java 25
3. Compact object headers benefit (5-10% throughput) may not be realized
4. Virtual thread memory optimization claims unverified

## BENCHMARK METHODOLOGY ISSUES

### Issue 1: Test Conditions Not Fully Documented

**File:** `IntegrationBenchmarks.java` (line 79)

```java
@Fork(value = 1, jvmArgs = {
    "-Xms2g", "-Xmx4g", "-XX:+UseG1GC", "-XX:+UseCompactObjectHeaders"
})
```

**Problems:**
- No warmup GC explicitly specified
- Compact object headers may not be stable in Java 21
- Heap sizing (2g min / 4g max) may not represent production configurations

### Issue 2: Memory Measurements in Report

**Claims:**
- Memory per session: 24.93 KB (2.5x higher than 10KB target)

**Root Causes Unexplained:**
- MeterRegistry lazy loading not mentioned as optimization
- Session construction memory profile not broken down by component

### Issue 3: Virtual Thread Performance Baselines Missing

**Expected in Documentation:**
- Platform threads vs virtual threads throughput comparison
- Virtual thread memory overhead (expected 1KB vs 1MB for platform threads)
- Structured concurrency benefits quantified

**Actual in Documentation:**
- Recommendations provided (section 3) but no benchmark results provided

## RECOMMENDATIONS

### Immediate Actions (Critical)

1. **Update documentation to reflect Java 21 reality**
   - Change all references from "Java 25 exclusive" to "Java 21 compatible"
   - Add explicit notes for Java 25-only features (AOT cache, ZGenerational)
   - Create Java 25 preview roadmap section

2. **Validate benchmark report environment**
   - Confirm benchmarks were actually run on Java 25 or Java 21
   - If Java 21: remove Java 25-specific claims from results
   - Retag report with actual test conditions

3. **Create Java 21 production configuration**
   - Remove `-XX:+UseAOTCache` and `-XX:AOTCacheFile`
   - Use `-XX:-UseAOTCache` to disable if present
   - Update container startup examples

### Medium Priority (High Impact)

4. **Performance regression baseline**
   - Measure actual startup time on Java 21 (not 3.2s -> 2.4s projection)
   - Measure actual throughput with CompactObjectHeaders on Java 21
   - Document memory footprint of real MCP sessions

5. **Enhanced benchmark documentation**
   - Add virtual thread micro-benchmarks with actual results
   - Document platform thread vs virtual thread trade-offs
   - Provide GC tuning guidance specific to Java 21

6. **JVM flag validation**
   - Test `-XX:+UseCompactObjectHeaders` stability on Java 21
   - Verify GC flag compatibility matrix
   - Document known issues and workarounds

### Lower Priority (Future Work)

7. **Java 25 preparation**
   - Create separate Java 25 tuning guide
   - Document value types benefits (when available)
   - Benchmark structured concurrency benefits

8. **Build performance claims**
   - Verify "clean build < 90s" claim (line 17)
   - Measure actual parallel build performance
   - Document system configuration assumptions

## VERIFICATION CHECKLIST

**Before committing updated documentation:**

- [ ] Confirm all Java version references are accurate
- [ ] Test all JVM flags with actual Java 21 version
- [ ] Remove or clearly mark Java 25-exclusive features
- [ ] Re-run critical benchmarks on Java 21
- [ ] Update performance claims with Java 21 actual results
- [ ] Document benchmark test environment precisely
- [ ] Cross-check all metrics against observable behavior
- [ ] Validate startup time claim (currently 3.2s -> 2.4s unverified)

## FILES REQUIRING UPDATES

### Critical Updates Required
1. `/home/user/yawl/docs/v6/upgrade/PERFORMANCE-GUIDELINES.md`
   - Section 6.3 (AOT Cache) - Java 25 only, mark clearly
   - Section 2.3 (Compact Object Headers) - clarify Java 21 limitations
   - GC selection matrix - remove Java 25-only flag combinations

2. `/home/user/yawl/docs/v6/performance/PERFORMANCE_REPORT_20260219_101532.md`
   - Section 9 (Test Environment) - verify Java version used
   - Section 8 (Recommendations) - remove Java 25 assumptions

3. `/home/user/yawl/docs/v6/THESIS-YAWL-V6-COMPETITIVE-ADVANTAGE-2026.md`
   - All Java 25 claims should be marked "planned" or "expected"
   - Verify actual performance advantages with Java 21

### Updates for Clarity
4. `/home/user/yawl/docs/v6/performance/README.md`
   - Add Java version requirements section
   - Link to specific tuning guides per Java version

## SUMMARY OF VALIDATION RESULTS

| Aspect | Status | Severity | Action |
|--------|--------|----------|--------|
| Java version documentation | FAIL | CRITICAL | Update to reflect Java 21 |
| AOT cache claims | FAIL | CRITICAL | Remove or mark Java 25 only |
| Compact object headers | WARN | HIGH | Test stability on Java 21 |
| GC configuration | PASS | - | Valid but could be optimized |
| Build performance | UNVERIFIED | MEDIUM | Measure on actual system |
| Benchmark validity | WARN | HIGH | Confirm test environment |
| Virtual thread claims | UNVERIFIED | MEDIUM | Add benchmark results |
| Memory optimization | WARN | MEDIUM | Explain session overhead |

---

**Audit Completed:** 2026-02-20
**Confidence Level:** HIGH (clear documentation vs. system discrepancies)
**Recommended Action:** Hold release until Java version claims are corrected
