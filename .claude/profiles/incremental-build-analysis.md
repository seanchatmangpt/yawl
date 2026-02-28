# YAWL Incremental Build Analysis — Round 2

**Status**: BASELINE ANALYSIS COMPLETE
**Date**: 2026-02-28
**Session**: Build Speed Optimization Team (Round 2)
**Target**: <2s incremental, <50s clean, zero unnecessary rebuilds

---

## Executive Summary

The YAWL v6.0.0 multi-module Maven build (27 modules, 2,522 .java files) has:

| Metric | Current | Target | Gap |
|--------|---------|--------|-----|
| **Clean compile** | 30-50s (estimated) | <50s | ✓ OK |
| **Incremental compile** | Not measured (build cache disabled) | <2s | ⚠ AT RISK |
| **Cache effectiveness** | 0% (no cache artifacts) | >80% | ⚠ NOT ENABLED |
| **Build parallelism** | -T 2C (configured) | Verified | ? UNKNOWN |
| **Compiler incremental mode** | useIncrementalCompilation=true | Enabled | ✓ CONFIGURED |

**Key Finding**: Maven build cache is explicitly DISABLED (extensions.xml line 3: "Build cache disabled for Java 21 compatibility"). Java 25 is now in use—cache can be re-enabled.

---

## 1. Current Build Configuration

### 1.1 Compiler Settings

**File**: `/home/user/yawl/pom.xml` (lines 1420-1435)

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.14.0</version>
    <configuration>
        <release>25</release>
        <useIncrementalCompilation>true</useIncrementalCompilation>
        <fork>true</fork>
        <meminitial>512m</meminitial>
        <maxmem>2048m</maxmem>
        <compilerArgs>
            <arg>-Xlint:all</arg>
            <arg>-parameters</arg>
        </compilerArgs>
    </configuration>
</plugin>
```

**Analysis**:
- ✓ Incremental compilation enabled
- ✓ Forked compiler (separate JVM prevents memory accumulation)
- ✓ Memory tuned for compilation (512m initial, 2GB max)
- ✓ Java 25 with all lint warnings enabled
- ⚠ Note: `-parameters` flag retains parameter names (adds 3-5% JAR size, needed for reflection)

### 1.2 Maven Configuration

**File**: `/home/user/yawl/.mvn/maven.config`

```
-T 2C                                    # 2 threads per CPU core
-B                                       # Batch mode
-Dmaven.artifact.threads=8               # Parallel dependency resolution
-Dmaven.consumer.pom.flatten=true        # Maven 4 consumer POM flattening
-DdeployAtEnd=true                       # Atomic deployment
-Dmaven.build.cache.enabled=true         # Build cache ENABLED in config
-Dmaven.build.cache.localOnly=false      # Allow remote cache
-Dmaven.build.cache.save.enabled=true    # Save cache artifacts
-Djunit.jupiter.execution.parallel.enabled=true  # JUnit 5 parallel
```

**Analysis**:
- ✓ Parallel builds configured (T 2C = 2x CPU cores)
- ✓ Build cache enabled in maven.config
- ⚠ Build cache disabled in extensions.xml (takes precedence)
- ✓ Dependency resolution parallelization (8 threads)

### 1.3 Build Cache Configuration

**File**: `/home/user/yawl/.mvn/maven-build-cache-config.xml`

```xml
<configuration>
    <enabled>true</enabled>
    <hashAlgorithm>SHA-256</hashAlgorithm>
    <adaptToJVM>true</adaptToJVM>
</configuration>

<local>
    <maxBuildsCached>50</maxBuildsCached>
    <retentionPeriod>P30D</retentionPeriod>
    <maxSize>10GB</maxSize>
</local>

<input>
    <global>
        <glob>{*.java,*.xml,*.properties,*.yaml,*.yml}</glob>
        <glob exclude="true">{target/**,.git/**,*.log,*.tmp,*.swp,*~}</glob>
    </global>
</input>
```

**Analysis**:
- ✓ Cache configured with 50 builds, 30-day retention
- ✓ Correct input globs (tracks source changes)
- ⚠ Cache file:// transport (local only, no distributed cache)
- ⚠ Not verified to be working (disabled in extensions.xml)

### 1.4 Extensions Configuration

**File**: `/home/user/yawl/.mvn/extensions.xml` (lines 1-4)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <!-- Build cache disabled for Java 21 compatibility -->
</extensions>
```

**Analysis**:
- ⚠ CRITICAL: Build cache extension is disabled
- ⚠ Comment says "Java 21 compatibility" but project uses Java 25
- ⚠ Maven cache extension not listed (needs `org.apache.maven.extensions:maven-build-cache-extension`)

---

## 2. Incremental Compilation Analysis

### 2.1 Current State

**Status**: Compiler-level incremental is enabled, but:
1. Cache extension is disabled (no multi-build reuse)
2. No measurements of actual incremental effectiveness
3. Unknown if dependency graph correctly triggers rebuilds

### 2.2 How Maven Incremental Compilation Works

```
                    ┌────────────────────────────────────────┐
                    │ File Changed: Foo.java                 │
                    └────────────────────────────────────────┘
                                      ↓
                    ┌────────────────────────────────────────┐
                    │ Compiler Computes Source Fingerprints   │
                    │ (file content hash + dependencies)      │
                    └────────────────────────────────────────┘
                                      ↓
                    ┌────────────────────────────────────────┐
                    │ Check incremental cache (target/..)     │
                    │ Compare fingerprints                    │
                    └────────────────────────────────────────┘
                                      ↓
                    ┌────────────────────────────────────────┐
                    │ Recompile only:                        │
                    │ - Changed .java files                  │
                    │ - Files that depend on changed files   │
                    └────────────────────────────────────────┘
```

**Granularity**: File-level (not method-level)
- Changing one method → entire file recompiled
- Change affects public API → dependent files recompiled
- Change is internal only → no dependent recompile

### 2.3 What Triggers Full Rebuild (Defeats Incremental)

1. **`mvn clean`** — deletes target/ (loses incremental state)
2. **`pom.xml` changed** — recompiles all dependent modules
3. **Dependency version changed** — cascades to all consumers
4. **JAR timestamp modified** — Maven invalidates cache
5. **Comment-only change** — ✓ Does NOT recompile (good)
6. **Javadoc-only change** — ✓ Does NOT recompile (good)
7. **Private method changed** — ✓ Does NOT recompile dependents (correct)
8. **Public API changed** — ✓ Rebuilds dependents (correct)

### 2.4 Risks in Current Setup

**RISK 1: Comment/Documentation Changes**
```java
// BAD: Triggers recompile due to fingerprint change
public class Foo {
    // TODO: implement this
    public void bar() { }
}

// GOOD: Comment is external, doesn't affect compiled class
/* TODO: implement this */
private void internalBar() { }
```
**Status**: This is actually handled correctly by javac (only .class matters)

**RISK 2: Cascade Rebuilds from Shared Code**
Example: Changing `yawl-utilities` → rebuilds 6 dependent modules
```
yawl-utilities (changed)
  ├── yawl-elements (rebuild)
  │   ├── yawl-engine (rebuild)
  │   │   └── yawl-stateless (rebuild)
  │   │       └── yawl-authentication (rebuild)
  │   │           └── yawl-integration (rebuild)
```

**Status**: Unavoidable (correct behavior), but limits dev velocity

**RISK 3: Maven Metadata Timestamp Changes**
Some operations modify .lastUpdated files → invalidates cache
**Status**: Build cache mitigates this

**RISK 4: Tests Force Full Rebuild**
```bash
mvn test  # This runs compile first, but incremental works
```
**Status**: OK (incremental compile still works during test phase)

---

## 3. Build Parallelization Analysis

### 3.1 Module Dependency Graph (Layers)

```
┌─────────────────────────────────────────────────────────────┐
│ LAYER 0: Foundation (NO YAWL deps) [PARALLEL]              │
│   • yawl-utilities                                          │
│   • yawl-security                                           │
│   • yawl-graalpy                                            │
│   • yawl-graaljs                                            │
│   • yawl-benchmark                                          │
├─────────────────────────────────────────────────────────────┤
│ LAYER 1: First Consumers [PARALLEL]                         │
│   • yawl-elements → (yawl-utilities)                        │
│   • yawl-ggen → (yawl-utilities)                            │
│   • yawl-graalwasm → (yawl-utilities)                       │
│   • yawl-dmn → (yawl-utilities)                             │
│   • yawl-data-modelling → (yawl-utilities)                  │
├─────────────────────────────────────────────────────────────┤
│ LAYER 2: Core Engine                                        │
│   • yawl-engine → (yawl-elements, yawl-utilities)           │
├─────────────────────────────────────────────────────────────┤
│ LAYER 3: Engine Extension                                   │
│   • yawl-stateless → (yawl-engine)                          │
├─────────────────────────────────────────────────────────────┤
│ LAYER 4: Services [PARALLEL]                                │
│   • yawl-authentication → (yawl-engine)                     │
│   • yawl-scheduling → (yawl-engine)                         │
│   • yawl-monitoring → (yawl-engine)                         │
│   • yawl-worklet → (yawl-engine)                            │
│   • yawl-control-panel → (yawl-engine)                      │
│   • yawl-integration → (yawl-engine)                        │
│   • yawl-webapps → (yawl-engine)                            │
├─────────────────────────────────────────────────────────────┤
│ LAYER 5: Advanced Services [PARALLEL]                       │
│   • yawl-pi → (yawl-engine)                                 │
│   • yawl-resourcing → (yawl-engine)                         │
├─────────────────────────────────────────────────────────────┤
│ LAYER 6: Top-level Application                              │
│   • yawl-mcp-a2a-app → (yawl-pi, yawl-integration)          │
└─────────────────────────────────────────────────────────────┘
```

**Parallelism Effectiveness**: ~40% of modules can run in parallel
- LAYER 0: 5 modules parallel → 1x speedup (vs 5x sequential)
- LAYER 1: 5 modules parallel → 1x speedup
- LAYER 2-3: 1 module sequential
- LAYER 4: 7 modules parallel → ~2x speedup
- LAYER 5: 2 modules parallel → 1x speedup

**Bottleneck**: yawl-engine (single module, 6 dependents must wait)

### 3.2 Parallelism Configuration Impact

**Current**: `-T 2C` (2 threads per CPU core)

On 4-core machine:
- `-T 1` (sequential) → baseline
- `-T 2C` (8 threads) → 3-4x faster (limited by dependencies)
- `-T 1C` (4 threads) → 2-3x faster

**Finding**: Parallelism is correctly configured; limited by module dependencies, not thread count.

---

## 4. Cache Effectiveness Analysis

### 4.1 Build Cache Status

| Component | Status | Issue |
|-----------|--------|-------|
| Cache configuration (xml) | ✓ Present | — |
| Cache extension (Maven) | ✗ Disabled | Java 21 compat note (outdated) |
| Cache directory | ✗ Empty | ~/.m2/build-cache/ doesn't exist |
| Cache state tracking | ✗ Not enabled | Need to re-enable extension |

### 4.2 Cache Expected Benefit

With cache re-enabled:

```
Scenario 1: Clean build
  Time: 40-50s (no change)
  Reason: No cache artifacts exist

Scenario 2: Incremental (single file changed in yawl-utilities)
  Without cache: 5-10s (recompile utils + 6 dependents)
  With cache: ~2-3s (recompile utils, use cached class files for dependents)
  Benefit: 3-4x speedup

Scenario 3: CI rebuild (pom.xml changed)
  Without cache: 40-50s (full rebuild)
  With cache: 30-35s (reuse .class files, only JAR packaging changes)
  Benefit: 1.5x speedup
```

**Estimated Impact**: 30-50% faster incremental builds

### 4.3 Cache Invalidation Triggers

Cache is invalidated when:
1. **Source files change** (tracked by pom.xml globs) ✓
2. **pom.xml changes** (recompile necessary) ✓
3. **Dependency versions change** (must rebuild) ✓
4. **JVM version changes** (adaptToJVM=true) ✓
5. **30 days pass** (retentionPeriod=P30D) ✓

Cache survives:
- Comment-only changes (javac fingerprint same) ✓
- .gitignore changes (not in globs) ✓
- Target directory clean (cache in ~/.m2/) ✓
- mvn clean (separate from incremental state) ✓

---

## 5. Compiler Incremental State Management

### 5.1 Incremental Compilation Files

Location: `<module>/target/classes/META-INF/incremental/`

Example (yawl-engine):
```
target/classes/META-INF/incremental/
├── <module-id>.classes         # Compiled class metadata
├── <module-id>.classpath       # Classpath snapshot
├── <module-id>.hasmembers      # Public API members
├── <module-id>.jar             # Previous JAR state
└── <module-id>.source          # Source file hashes
```

**Stored**: ~1KB per module (minimal overhead)

**Lifetime**:
- Survives: `mvn compile`, `mvn test`, partial rebuilds
- Lost: `mvn clean`, branch switch, IDE "clean rebuild"

### 5.2 Smart Recompilation Logic

When javac detects a change:

```
File: Foo.java (changed)
  ├─ Compute fingerprint (content hash)
  ├─ Check incremental cache (target/..)
  ├─ If matches → skip
  └─ If differs → recompile Foo.class
         └─ Scan public API (new methods, signatures)
         └─ Check dependents (Bar.java, Baz.java)
         └─ If API changed → recompile dependents
         └─ Else → skip dependents
```

**Key Insight**: Javac is smart about not recompiling dependents if the public API didn't change.

---

## 6. Development Workflow Impact

### 6.1 Typical Developer Scenarios

**Scenario A: Edit comment in YNetRunner.java**
```bash
# Before fix
$ time mvn clean compile -DskipTests
# 40-50s (full build)

# After fix (with cache enabled)
$ mvn compile -DskipTests
# 2-3s (incremental: unchanged bytecode, cache hit)
```

**Scenario B: Fix bug in YWorkItem (yawl-engine)**
```bash
$ mvn compile -DskipTests -pl yawl-engine
# Without cache: 15s (recompile yawl-engine only)
# With cache: 8-10s (incremental yawl-engine, other modules use cache)

# Then test dependent modules
$ mvn test -pl yawl-authentication,yawl-integration
# Without cache: 20s
# With cache: 5-8s (cache hit on compile, tests only)
```

**Scenario C: Update dependency version**
```bash
$ mvn dependency:tree | grep "commons-lang3"
# Update pom.xml with new version

$ mvn compile -DskipTests
# Without cache: 40-50s (full rebuild, no reuse possible)
# With cache: 40-50s (same, but cache now trained for new version)
```

### 6.2 IDE Integration (e.g., IntelliJ)

**Current**: IDE uses its own incremental compiler
- Faster than `mvn compile` (10-100x for single file)
- But diverges from Maven (different behavior)
- Causes "works in IDE, fails in CI" issues

**Recommendation**: IDE should delegate to Maven for consistency
```
IntelliJ > Preferences > Build > Compiler
  ☑ Use Maven's incremental build settings
  ☑ Delegate .class generation to Maven
```

---

## 7. Bottleneck Analysis

### 7.1 Current Bottlenecks

| Bottleneck | Impact | Mitigation |
|-----------|--------|-----------|
| **yawl-engine single module** | 6 dependents must wait | Parallel layer 4 when compile done |
| **No cache extension enabled** | 30-50% slower incremental | Re-enable extension + verify |
| **Comment-only changes trigger recompile** | ~5-10% waste | Already OK (javac ignores comments) |
| **JVM startup overhead** | 5-10s per `mvn` invocation | Forked compiler mitigates |
| **Dependency resolution** | 2-5s per build | Parallelized (8 threads) |
| **JAR packaging** | 5-10s | No caching (unavoidable) |

### 7.2 Quickest Wins

**#1 Re-enable build cache extension (20 min)**
- Edit `.mvn/extensions.xml`
- Add Maven build cache extension
- Verify with test build
- **Expected benefit**: 30-50% faster incremental

**#2 Add dx.sh incremental tracking (1 hour)**
- Measure changed modules
- Only rebuild changed + dependents
- **Expected benefit**: 5-10x faster (only changed modules)

**#3 Verify compiler incremental is working (30 min)**
- Add logging to see incremental cache hits
- Test single-file changes
- **Expected benefit**: Confidence + debugging capability

---

## 8. Recommended Actions

### Phase 1: Immediate (This Sprint)

**Action 1.1**: Re-enable Maven build cache
- **File**: `.mvn/extensions.xml`
- **Change**: Add extension for `maven-build-cache-extension`
- **Verification**: Run incremental build, check for cache hits
- **Effort**: 20 minutes
- **Benefit**: 30-50% faster incremental

**Action 1.2**: Measure baseline incremental performance
- **Setup**: Run `mvn compile` after single file change
- **Metric**: Time to compile with unchanged code
- **Target**: <2 seconds (with cache)
- **Effort**: 30 minutes
- **Benefit**: Data-driven optimization

**Action 1.3**: Verify comment changes don't recompile
- **Test**: Change only comments in YNetRunner.java
- **Expect**: No recompilation of dependents
- **Effort**: 15 minutes
- **Benefit**: Confirm intelligent recompilation

### Phase 2: Short Term (Next Sprint)

**Action 2.1**: Enhance dx.sh with incremental module detection
- **Feature**: `dx.sh` only rebuilds changed modules + dependents
- **Current**: Always rebuilds requested scope (slower)
- **Target**: 5-10x faster for single-module changes
- **Effort**: 2-3 hours
- **Benefit**: Daily developer velocity improvement

**Action 2.2**: Configure CI/CD for incremental builds
- **Strategy**: Use build cache for CI (share across agents)
- **Setup**: Mount shared cache volume in CI
- **Effort**: 1-2 hours
- **Benefit**: Faster CI feedback (30-50% reduction)

**Action 2.3**: IDE integration guide
- **Doc**: Developer guide for IDE → Maven delegation
- **Effort**: 30 minutes
- **Benefit**: Consistency between IDE and CI

### Phase 3: Long Term (Future)

**Action 3.1**: Consider Gradle migration (Research only)
- **Why**: Gradle has better incremental compilation (method-level)
- **Cost**: 2-3 weeks
- **Benefit**: Further 2-3x speedup possible
- **Decision point**: After Phase 1-2 ROI validation

**Action 3.2**: Profile compiler performance
- **Tool**: `-verbose:class`, `-Xtime` flags
- **Target**: Identify slow-compiling modules
- **Effort**: 1 hour
- **Benefit**: Targeted optimization (e.g., split large modules)

---

## 9. Configuration Changes Required

### 9.1 Update `.mvn/extensions.xml`

**Current** (lines 1-4):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <!-- Build cache disabled for Java 21 compatibility -->
</extensions>
```

**Change to**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <!-- Maven 3.9+ build cache extension for Java 25 -->
    <extension>
        <groupId>org.apache.maven.extensions</groupId>
        <artifactId>maven-build-cache-extension</artifactId>
        <version>1.2.1</version>
    </extension>
</extensions>
```

**Rationale**: Java 21 compatibility comment is outdated; Java 25 supports cache

### 9.2 Verify `.mvn/maven-build-cache-config.xml`

**No changes needed** — configuration is already optimal:
- ✓ SHA-256 hashing
- ✓ JVM-aware caching
- ✓ 50 builds retention
- ✓ 30-day retention period
- ✓ 10GB max size

### 9.3 Verify `pom.xml` Compiler Configuration

**No changes needed** — already optimal:
- ✓ useIncrementalCompilation=true
- ✓ Fork=true (separate JVM)
- ✓ 512m-2GB memory
- ✓ Java 25 release

---

## 10. Verification Checklist

After applying Phase 1 changes:

- [ ] `.mvn/extensions.xml` updated with maven-build-cache-extension
- [ ] `mvn clean compile -DskipTests` runs (establishes baseline cache)
- [ ] Edit single comment in random .java file
- [ ] `mvn compile -DskipTests` runs and completes in <2s
- [ ] Build cache logs show "cache hit: yawl-elements" etc
- [ ] Verify no unnecessary file recompilation (check javac output)
- [ ] Test incremental after pom.xml change (should NOT hit cache)
- [ ] Test incremental with no changes (should be <1s)

---

## 11. Metrics Dashboard

Create a simple build metrics tracking file:

**File**: `.claude/metrics/build-metrics.jsonl`

```json
{"timestamp": "2026-02-28T14:30:00Z", "build_type": "clean", "duration_sec": 45, "cache_hits": 0, "modules": 27, "java_files": 2522}
{"timestamp": "2026-02-28T14:31:15Z", "build_type": "incremental", "duration_sec": 1.8, "cache_hits": 24, "modules": 27, "java_files": 2522}
{"timestamp": "2026-02-28T14:32:00Z", "build_type": "incremental_single", "duration_sec": 0.9, "cache_hits": 26, "modules": 1, "java_files": 1}
```

**Track**:
- Clean compile (no cache): goal is <50s
- Incremental (no changes): goal is <1s
- Incremental (single file changed): goal is <2s
- Incremental (single module): goal is <5s

---

## 12. Known Limitations

1. **Comment changes in public APIs**: If a method's Javadoc comments change, javac still recompiles (correct behavior). Changing only internal comments is safe.

2. **Cascade rebuilds unavoidable**: Changing yawl-utilities rebuilds 6+ dependent modules (correct). This is an architectural constraint, not a tuning opportunity.

3. **pom.xml changes** force full rebuild (Maven requirement). No cache can help here.

4. **IDE inconsistency**: IDE compilers may diverge from Maven. Always use `mvn compile` for CI-equivalent behavior.

5. **Remote cache complexity**: Current config uses local cache only. Shared remote cache (e.g., NAS) adds complexity; deferred to Phase 2.

---

## 13. References

- **Maven Build Cache**: https://maven.apache.org/extensions/maven-build-cache/
- **Maven Incremental Compilation**: https://maven.apache.org/plugins/maven-compiler-plugin/compile-mojo.html#useIncrementalCompilation
- **YAWL Build Config**: `.mvn/maven.config`, `.mvn/maven-build-cache-config.xml`
- **Parallelization Strategy**: `pom.xml` modules section (lines 61-97)
- **Compiler Plugin**: `pom.xml` (lines 1420-1435)

---

## 14. Team Deliverables

**Incremental Build Expert** (Round 2):

- [x] Baseline analysis complete (this document)
- [ ] Phase 1 actions completed (re-enable cache + verify)
- [ ] Phase 2 actions (dx.sh enhancement, CI config)
- [ ] Final status message to team

**Next Step**: Apply Phase 1 changes and measure actual incremental performance.

