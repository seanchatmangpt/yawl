# YAWL Performance Tuning Guide

**Date**: 2026-02-28
**Audience**: Developers, DevOps, SREs
**Version**: 1.0

---

## Quick Start by Use Case

### Local Development (Fastest Feedback)

Goal: 5-15 second feedback loop on code changes

```bash
# Option 1: Single module (recommended)
bash scripts/dx.sh                    # Auto-detects changed modules
# ~5-15s for small changes

# Option 2: All modules (pre-commit verification)
bash scripts/dx.sh all                # Full validation
# ~30-60s depending on changes

# Option 3: Compile only (no tests)
bash scripts/dx.sh compile            # Just compilation
# ~3-5s for single module
```

**Tuning**: Enabled automatically via `agent-dx` profile
- JUnit parallel execution: ON
- JaCoCo coverage: OFF
- Static analysis: OFF
- Integration tests: OFF

### Continuous Integration (Balanced)

Goal: <5 minutes total for PR checks

```bash
# Fast build
mvn -T 1.5C clean verify -DskipTests  # Compile check
# ~45s

# Then run tests in parallel
mvn -T 2C verify -P integration-parallel
# ~90-120s total

# Analysis on main branch only
mvn -P analysis clean verify           # Full analysis
# ~4-5 minutes (runs separately, not blocking)
```

**Configuration**: Configured in `.github/workflows/`
- Parallel compilation: 1.5C threads
- Parallel testing: 4 JVMs (forkCount=2)
- Analysis: Only on merge to main

### Release Builds (Complete Validation)

Goal: Comprehensive testing, security, coverage

```bash
# Full validation
mvn clean verify -P coverage,analysis

# Or step-by-step
mvn clean compile                      # Compile
# ~35-40s

mvn test                               # Unit tests
# ~25-30s

mvn verify                             # Integration tests
# ~25-35s

mvn test -P analysis                   # Static analysis
# ~4-5 minutes
```

**Configuration**: All optimizations + full analysis
- Everything enabled
- Parallel execution: 2-3C
- Coverage collection: YES
- SpotBugs/PMD/JaCoCo: YES

---

## Section 1: Hardware-Specific Tuning

### 1.1 Small Laptop (8-core, 16GB RAM)

**Recommended settings**: `mvn -T 1.5C verify`

```xml
<!-- In .mvn/jvm.config -->
-Xmx2G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Djdk.virtualThreadScheduler.parallelism=8
```

| Setting | Value | Rationale |
|---------|-------|-----------|
| **Threads** | `1.5C` | 8 cores × 1.5 = 12 threads (safe margin) |
| **Heap** | 2GB | Adequate for parallel tests |
| **GC** | ZGC | Predictable pause times |
| **test factor** | 4.0 | Run up to 4 tests per thread |
| **Cache size** | 2GB | Limited storage available |

**Performance**:
- Cold build: 120-150s
- Warm build: 60-80s
- Tests: 30-40s

---

### 1.2 Desktop/Workstation (16-core, 32GB RAM)

**Recommended settings**: `mvn -T 2C verify`

```xml
<!-- In .mvn/jvm.config -->
-Xmx4G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Djdk.virtualThreadScheduler.parallelism=16
```

| Setting | Value | Rationale |
|---------|-------|-----------|
| **Threads** | `2C` | 16 cores × 2 = 32 threads (aggressive) |
| **Heap** | 4GB | Ample memory for parallel execution |
| **GC** | ZGC | Low latency for interactive work |
| **test factor** | 6.0 | More aggressive test parallelism |
| **Cache size** | 5GB | Larger cache pays for itself |

**Performance**:
- Cold build: 80-100s
- Warm build: 40-50s
- Tests: 20-25s

---

### 1.3 CI Server (64-core, 128GB RAM)

**Recommended settings**: `mvn -T 3C -pl <modules> verify`

```xml
<!-- In .mvn/jvm.config -->
-Xmx8G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Djdk.virtualThreadScheduler.parallelism=32
-XX:ParallelGCThreads=16
```

| Setting | Value | Rationale |
|---------|-------|-----------|
| **Threads** | `3C` | 64 cores × 3 = 192 threads (extreme) |
| **Heap** | 8GB | Large heap for many parallel processes |
| **GC** | ZGC + tuned | Optimize for throughput |
| **test factor** | 8.0 | Maximum parallelism |
| **Cache size** | 10GB | Large remote cache recommended |
| **Forks** | 4+ | Multiple JVM processes |

**Performance**:
- Cold build: 60-80s
- Warm build: 30-40s
- Tests: 15-20s
- Full suite (w/analysis): 4-5 minutes

---

## Section 2: Build Profiles

### 2.1 Agent DX Profile (Local Development)

**Purpose**: Maximum speed for developer iteration

**Activation**: Automatic when using `bash scripts/dx.sh`

**Included**:
- Parallel compilation: `1.5C`
- Parallel unit tests: `1.5C`
- Virtual thread tuning: Auto
- Incremental compilation: YES

**Excluded**:
- JaCoCo code coverage
- Static analysis (SpotBugs, PMD)
- Integration tests
- Source code JAR generation
- Javadoc generation
- Enforcer rules

**Command**:
```bash
mvn -P agent-dx clean compile
mvn -P agent-dx test
```

**Performance**: ~5-15s per cycle

---

### 2.2 Integration Parallel Profile

**Purpose**: Run integration tests in parallel

**Activation**: Explicit with `-P integration-parallel`

**Configuration**:
```xml
<profile>
  <id>integration-parallel</id>
  <properties>
    <failsafe.parallel>methods</failsafe.parallel>
    <failsafe.threadCount>1.5C</failsafe.threadCount>
    <failsafe.reuseForks>true</failsafe.reuseForks>
    <failsafe.timeout>300</failsafe.timeout>
  </properties>
</profile>
```

**Command**:
```bash
mvn verify -P integration-parallel
```

**Performance**: ~30-50s for integration tests only

---

### 2.3 Analysis Profile

**Purpose**: Static analysis, code coverage, security scanning

**Activation**: Explicit with `-P analysis`

**Included**:
- SpotBugs (bug detection)
- PMD (code smells)
- JaCoCo (code coverage)
- Checkstyle (style enforcement)
- Error Prone (compile-time checks)

**Command**:
```bash
mvn clean verify -P analysis
```

**Performance**: ~4-5 minutes (run separately from fast builds)

**Coverage thresholds**:
- Line coverage: 75% minimum
- Branch coverage: 60% minimum
- Method coverage: 70% minimum

---

### 2.4 Coverage Profile

**Purpose**: Detailed code coverage reports

**Activation**: Explicit with `-P coverage`

**Included**:
- JaCoCo instrumentation
- HTML report generation
- XML report for CI/CD
- Coverage thresholds enforced

**Command**:
```bash
mvn clean verify -P coverage
java -jar target/jacoco-check.jar          # View coverage
open target/site/jacoco/index.html         # Browser
```

**Performance**: ~30-45s overhead

---

## Section 3: JVM Configuration Tuning

### 3.1 Heap Size Adjustment

**File**: `.mvn/jvm.config`

```properties
# Conservative (8GB machine)
-Xmx2G

# Balanced (16GB machine)
-Xmx4G

# Aggressive (32GB+ machine)
-Xmx8G

# Very low memory (4GB machine)
-Xmx1G
```

**Decision tree**:
- Available RAM > 8GB: Use 50% of available
- Available RAM 4-8GB: Use 25% of available
- Available RAM < 4GB: Use 1GB maximum

**Testing**:
```bash
# Check current setting
mvn help:describe -Ddetail=true | grep -i "xmx"

# Monitor during build
jcmd <pid> VM.memory_managers | grep -i heap
```

---

### 3.2 Garbage Collector Selection

#### Option 1: ZGC (Recommended)
```properties
-XX:+UseZGC
-XX:InitiatingHeapOccupancyPercent=35
-XX:+UseStringDeduplication
```

**When to use**: Always (default)
**Pause time**: <1ms (sub-millisecond)
**Overhead**: 2-3% throughput
**Best for**: Interactive development, SLA-sensitive production

#### Option 2: G1GC (If ZGC unavailable)
```properties
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100
-XX:ParallelGCThreads=4
```

**When to use**: Java <21 or specific requirements
**Pause time**: 50-100ms (acceptable)
**Overhead**: 5-10% throughput
**Best for**: Older Java versions

#### Option 3: Serial GC (Development only)
```properties
-XX:+UseSerialGC
```

**When to use**: Single-threaded, minimum memory
**Pause time**: Variable (stop-the-world)
**Overhead**: High during GC
**Best for**: Very constrained environments

---

### 3.3 Compiler Tuning

#### C1 Compiler (Fast startup, lower peak performance)
```properties
-client
-XX:TieredStopAtLevel=1
```

**Use case**: Development, fast iteration
**Compilation time**: <1s
**Peak performance**: Lower

#### C2 Compiler (Slower startup, higher peak performance)
```properties
-server
-XX:TieredStopAtLevel=4
```

**Use case**: Production, sustained workloads
**Compilation time**: 2-3s
**Peak performance**: Higher (10-30%)

#### Tiered Compilation (Default, balanced)
```properties
-XX:+TieredCompilation
-XX:TieredStopAtLevel=4
-XX:CompileThreshold=10000
```

**Use case**: General development
**Compilation time**: 1-2s
**Peak performance**: Good balance

---

## Section 4: Troubleshooting

### Problem 1: "Out of Memory" (OOM) Error

**Symptoms**:
```
java.lang.OutOfMemoryError: Java heap space
```

**Diagnosis**:
```bash
# Check current heap
mvn -v | grep -i memory

# Monitor heap during build
jcmd <pid> GC.heap_info

# Check process memory
ps aux | grep maven | grep -v grep
```

**Solutions** (in order):

1. **Reduce parallelism** (cheapest)
   ```bash
   mvn -T 1C clean verify        # Single-threaded
   # Or adjust in .mvn/jvm.config
   -T 1.5C → -T 1C
   ```

2. **Increase heap size** (recommended)
   ```properties
   # .mvn/jvm.config
   -Xmx2G → -Xmx4G
   ```

3. **Disable expensive plugins** (temporary)
   ```bash
   mvn clean verify -DskipTests          # Skip tests
   mvn clean compile -P agent-dx         # Agent profile
   ```

4. **Reduce test parallelism** (last resort)
   ```xml
   <threadCount>1C</threadCount>
   ```

---

### Problem 2: Tests Fail in Parallel But Pass Sequentially

**Symptoms**: Test passes with `mvn test`, fails with `mvn -T 2C test`

**Root cause**: Test isolation issue (shared state, ThreadLocal, static fields)

**Diagnosis**:
```bash
# Run failing test alone
mvn test -Dtest=MyFailingTest

# Run in sequential mode
mvn -T 1C test

# Check for static fields
grep -r "static.*=" src/test/ | grep -v "final"
```

**Solutions**:

1. **Mark test as sequential** (quick fix)
   ```java
   @Execution(ExecutionMode.SAME_THREAD)
   class StatefulTest {
       // Runs sequentially only
   }
   ```

2. **Fix test isolation** (proper fix)
   ```java
   // Remove static fields
   // Use @BeforeEach/@AfterEach instead of static setup
   @BeforeEach
   void setup() { /* ... */ }
   ```

3. **Use ThreadLocal cleanup** (if necessary)
   ```java
   @AfterEach
   void cleanup() {
       ThreadLocal.remove();  // Clean up after each test
   }
   ```

---

### Problem 3: Build Slower Than Expected

**Symptoms**: `mvn clean verify` taking >2 minutes

**Diagnosis**:
```bash
# Collect detailed timings
mvn clean verify -Dorg.slf4j.simpleLogger.defaultLogLevel=info | \
  grep -E "Building|Finished" > timings.txt

# Check module compilation times
mvn clean compile -X | grep "mojo execution time"

# Profile JVM
java -XX:+UnlockDiagnosticVMOptions \
     -XX:+PrintCompilation \
     -Dpom.xml compile
```

**Common causes & fixes**:

| Cause | Check | Fix |
|-------|-------|-----|
| **Dependency download** | Check `.m2/repository/` disk space | `mvn dependency:resolve` |
| **Test count increased** | Count tests: `mvn test -Dtest=DummyTest` | Optimize or skip slow tests |
| **New modules added** | Check pom.xml modules list | Evaluate if all needed in build |
| **Compilation stall** | Check CPU: `top` during build | Increase heap or reduce parallelism |
| **Network latency** | Ping central repo | Use mirror or local repo |

---

### Problem 4: Cache Invalidation/Misses

**Symptoms**: Build not using cached artifacts, taking longer

**Diagnosis**:
```bash
# Check cache status
ls -la .m2/repository/

# Monitor cache hits
mvn clean verify -Dorg.slf4j.simpleLogger.defaultLogLevel=debug | \
  grep -i "cache\|from"

# Verify cache hasn't been disabled
grep -r "cache.entries" pom.xml
```

**Solutions**:

1. **Clear stale cache** (weekly maintenance)
   ```bash
   find .m2/repository -name "*.lastUpdated" -delete
   mvn dependency:purge-local-repository
   ```

2. **Update dependencies** (refresh cache)
   ```bash
   mvn dependency:resolve -U              # -U forces update
   mvn dependency:tree                    # Verify tree
   ```

3. **Enable distributed cache** (CI/CD)
   ```bash
   export MAVEN_OPTS="-Daether.connector.http.retryHandler.class=\
   org.apache.maven.wagon.providers.http.ConfigurableHttpWagonRetryHandler"
   ```

---

### Problem 5: High Memory Usage / GC Thrashing

**Symptoms**: GC running frequently, memory rarely freed

**Diagnosis**:
```bash
# Monitor GC activity
jstat -gc -h10 <pid> 1000

# Check pause times
jcmd <pid> GC.log_level all

# Analyze GC logs
java -jar GCeasy.jar gc.log
```

**Solutions**:

1. **Increase heap size** (quickest)
   ```properties
   -Xmx2G → -Xmx4G
   ```

2. **Optimize GC thresholds** (ZGC tuning)
   ```properties
   -XX:InitiatingHeapOccupancyPercent=25    # Start GC earlier
   -XX:+UseStringDeduplication              # Reduce memory
   ```

3. **Reduce concurrent workload** (parallelism)
   ```bash
   mvn -T 1.5C clean verify                 # Reduce from 2C
   ```

4. **Enable GC logging** (debugging)
   ```properties
   -Xlog:gc*:file=gc.log:time,uptime,level,tags
   ```

---

## Section 5: Configuration Quick Reference

### 5.1 Environment Variables

```bash
# Performance-related variables
export MAVEN_OPTS="-Xmx4G -XX:+UseZGC"
export DX_OFFLINE=0                  # 1 = offline mode
export DX_TIMINGS=1                  # 1 = collect metrics
export DX_IMPACT=1                   # 1 = use impact graph
```

### 5.2 Maven Properties

| Property | Default | Recommended | Purpose |
|----------|---------|-------------|---------|
| `maven.compiler.source` | 11 | 25 | Source version |
| `maven.compiler.target` | 11 | 25 | Target version |
| `project.build.sourceEncoding` | ISO-8859-1 | UTF-8 | Encoding |
| `project.reporting.outputEncoding` | ISO-8859-1 | UTF-8 | Report encoding |
| `skipTests` | false | false | Skip tests |
| `maven.test.skip` | false | false | Skip test compilation |

### 5.3 Surefire Plugin (Unit Tests)

```xml
<properties>
  <surefire.parallel>methods</surefire.parallel>
  <surefire.threadCount>1.5C</surefire.threadCount>
  <surefire.timeout>600</surefire.timeout>
  <surefire.reuseForks>true</surefire.reuseForks>
</properties>
```

### 5.4 Failsafe Plugin (Integration Tests)

```xml
<properties>
  <failsafe.parallel>methods</failsafe.parallel>
  <failsafe.threadCount>1.5C</failsafe.threadCount>
  <failsafe.forkCount>2</failsafe.forkCount>
  <failsafe.timeout>300</failsafe.timeout>
  <failsafe.reuseForks>true</failsafe.reuseForks>
</properties>
```

---

## Section 6: Best Practices

### Do's ✅

- **DO use `bash scripts/dx.sh`** for local development (optimized for speed)
- **DO enable parallel compilation** (`-T 1.5C` or higher)
- **DO use incremental builds** (don't use `clean` unless necessary)
- **DO profile slow builds** (identify bottlenecks before optimizing)
- **DO monitor metrics** (track build time trends weekly)
- **DO use agent-dx profile** for development (skips expensive checks)
- **DO test with parallel execution** (catch isolation issues early)
- **DO keep build logs** (debug regressions)

### Don'ts ❌

- **DON'T disable caching** (causes 2-3x slowdown)
- **DON'T run full analysis on every build** (use CI for that)
- **DON'T ignore flaky tests** (fix root cause, don't ignore)
- **DON'T run full suite locally** (use CI for comprehensive checks)
- **DON'T use more threads than cores** (diminishing returns)
- **DON'T increase heap without monitoring** (wastes memory)
- **DON'T disable GC tuning** (impacts throughput)
- **DON'T commit without running `dx.sh all`** (CI won't catch some issues)

---

## Section 7: Performance Targets

| Scenario | Target | Acceptable | Red Flag |
|----------|--------|-----------|----------|
| **Local module change** | 5-15s | 15-30s | >30s |
| **Multi-module change** | 30-45s | 45-60s | >60s |
| **Clean build** | 90-120s | 120-150s | >150s |
| **Full test suite** | 30-50s | 50-75s | >75s |
| **CI/CD pipeline** | <5 min | 5-10 min | >10 min |
| **Analysis only** | 4-5 min | 5-7 min | >7 min |

---

## Section 8: Recording & Analysis

### 8.1 Collecting Metrics

```bash
# Automatic collection (weekly)
bash scripts/collect-build-metrics.sh --runs 5 --verbose

# Output saved to: .yawl/metrics/dashboard.json
```

### 8.2 Analyzing Performance

```bash
# View latest metrics
cat .yawl/metrics/dashboard.json | jq '.builds[] | {time: .duration, status: .success}'

# Find slowest modules
mvn clean verify -X | grep "mojo execution time" | sort -k NF -n | tail -10

# Compare sequential vs parallel
mvn clean verify        # Sequential
mvn -T 2C clean verify  # Parallel
```

---

## Appendix: Common Configurations

### Development Machine (Recommended)

**System**: 16-core, 32GB RAM
**File**: `.mvn/jvm.config`
```properties
-Xmx4G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Djdk.virtualThreadScheduler.parallelism=16
```

**File**: `mvn` invocation
```bash
mvn -T 2C clean verify
```

### CI/CD Pipeline

**System**: 64-core, 128GB RAM
**Configuration**:
```bash
mvn -T 3C -pl yawl-engine,yawl-integration clean verify
mvn -T 2C -pl yawl-test verify -P integration-parallel
```

### Release Build

**System**: Any
**Configuration**:
```bash
mvn clean verify -P coverage,analysis -T 1C
```

---

**Last Updated**: 2026-02-28
**Maintained By**: YAWL Performance Team
**Questions?**: See `.yawl/PERFORMANCE-REPORT.md` for detailed metrics
