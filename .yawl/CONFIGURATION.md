# YAWL Build & Performance Configuration Reference

**Date**: 2026-02-28
**Version**: 1.0
**Scope**: Maven, JUnit, JVM, Build Cache, CDS

---

## Quick Configuration Lookup

### "I just want it to work (recommended defaults)"

Nothing required. Defaults are production-ready:
- Parallel compilation: 1.5C
- Parallel tests: 1.5C threads, 2 JVM forks
- GC: ZGC with compact headers
- Caching: Automatic (Maven 4.0+)

Verify:
```bash
bash scripts/dx.sh all                # Should complete in 45-60s
```

---

### "I want to customize for my environment"

See sections below for hardware-specific configurations.

---

## Section 1: Maven Configuration

### 1.1 File Locations

| File | Purpose | Scope |
|------|---------|-------|
| `.mvn/maven.config` | Global Maven settings | All builds |
| `.mvn/jvm.config` | JVM settings | All builds |
| `pom.xml` (root) | Project-wide properties | Project builds |
| `.mvn/settings.xml` | Repository settings | Optional |

### 1.2 `.mvn/maven.config` (Global)

**Current default**:
```
-T 1.5C
```

**Options**:

| Setting | Value | When to Use |
|---------|-------|------------|
| `-T 1C` | Single-threaded | Resource constrained, debugging |
| `-T 1.5C` | 1.5 × CPU cores | **RECOMMENDED** (default) |
| `-T 2C` | 2.0 × CPU cores | 16+ core machines |
| `-T 3C` | 3.0 × CPU cores | 32+ core machines (aggressive) |
| `-T auto` | Auto-detect | Let Maven decide |

**Additional common settings**:
```
-T 1.5C
--no-snapshot-updates
-X                              # Debug (verbose)
--show-version
```

**Full example** (production):
```properties
-T 1.5C
--no-snapshot-updates
-Dmaven.wagon.http.ssl.insecure=false
-Dmaven.wagon.http.ssl.allowall=false
```

---

### 1.3 `.mvn/jvm.config` (JVM)

**Current default**:
```properties
-Xmx2G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
```

**Memory settings**:

| Machine | Setting | Rationale |
|---------|---------|-----------|
| 4GB RAM | `-Xmx1G` | Conservative, limited parallelism |
| 8GB RAM | `-Xmx2G` | Balanced, good parallelism (RECOMMENDED) |
| 16GB RAM | `-Xmx4G` | Aggressive, high parallelism |
| 32GB+ RAM | `-Xmx8G` | Maximum, extreme parallelism |

**Memory breakdown**:
```
Total available RAM - OS/services - Slack = Heap
Example: 16GB - 2GB - 2GB = 12GB free → -Xmx4G safe
```

**Garbage collection**:

```properties
# ZGC (default, recommended)
-XX:+UseZGC
-XX:InitiatingHeapOccupancyPercent=35
-XX:+UseStringDeduplication

# G1GC (if ZGC unavailable)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=100

# Serial GC (minimal, constrained)
-XX:+UseSerialGC
```

**Performance features**:

```properties
# Compact object headers (Java 25)
-XX:+UseCompactObjectHeaders

# Virtual thread scheduling
-Djdk.virtualThreadScheduler.parallelism=auto
-Djdk.virtualThreadScheduler.maxPoolSize=256

# Compilation (default: tiered)
-XX:+TieredCompilation
-XX:CompileThreshold=10000
```

**Debug/monitoring (optional)**:

```properties
# Enable GC logging
-Xlog:gc*:file=gc.log:time,uptime,level,tags

# Monitor virtual threads
-Djdk.virtualThreadScheduler.debug=true

# Show compilation
-XX:+PrintCompilation
-XX:+PrintInlining

# Native memory tracking
-XX:NativeMemoryTracking=detail
```

---

### 1.4 `pom.xml` Properties

**Surefire (unit tests)**:

```xml
<properties>
  <maven.surefire.version>3.5.4</maven.surefire.version>
  <surefire.parallel>methods</surefire.parallel>
  <surefire.threadCount>1.5C</surefire.threadCount>
  <surefire.timeout>600</surefire.timeout>
  <surefire.reuseForks>true</surefire.reuseForks>
  <surefire.argLine>${argLine}</surefire.argLine>
  <skipAfterFailureCount>0</skipAfterFailureCount>
</properties>
```

**Failsafe (integration tests)**:

```xml
<properties>
  <maven.failsafe.version>3.5.4</maven.failsafe.version>
  <failsafe.parallel>methods</failsafe.parallel>
  <failsafe.threadCount>1.5C</failsafe.threadCount>
  <failsafe.forkCount>2</failsafe.forkCount>
  <failsafe.timeout>300</failsafe.timeout>
  <failsafe.reuseForks>true</failsafe.reuseForks>
</properties>
```

**Compiler**:

```xml
<properties>
  <maven.compiler.version>3.15.0</maven.compiler.version>
  <maven.compiler.source>25</maven.compiler.source>
  <maven.compiler.target>25</maven.compiler.target>
  <maven.compiler.release>25</maven.compiler.release>
</properties>
```

**JaCoCo (code coverage)**:

```xml
<properties>
  <jacoco.maven.plugin.version>0.8.15</jacoco.maven.plugin.version>
  <jacoco.aggregate.report>true</jacoco.aggregate.report>
  <jacoco.coverage.target>0.75</jacoco.coverage.target>
</properties>
```

**SpotBugs (static analysis)**:

```xml
<properties>
  <spotbugs.maven.plugin.version>4.8.2.0</spotbugs.maven.plugin.version>
  <spotbugs.effort>more</spotbugs.effort>
  <spotbugs.threshold>medium</spotbugs.threshold>
</properties>
```

---

## Section 2: JUnit Configuration

### 2.1 JUnit 5 Parallel Execution

**Enable in test class**:

```java
@Execution(ExecutionMode.CONCURRENT)
class ConcurrentTests {
    // Methods run in parallel
}
```

**Disable for specific test**:

```java
@Execution(ExecutionMode.SAME_THREAD)
class SequentialTests {
    // Methods run sequentially
}
```

**Global configuration** (pom.xml):

```xml
<properties>
  <junit.jupiter.execution.parallel.enabled>true</junit.jupiter.execution.parallel.enabled>
  <junit.jupiter.execution.parallel.mode.default>concurrent</junit.jupiter.execution.parallel.mode.default>
  <junit.jupiter.execution.parallel.mode.classes.default>concurrent</junit.jupiter.execution.parallel.mode.classes.default>
  <junit.jupiter.execution.parallel.strategy>fixed</junit.jupiter.execution.parallel.strategy>
  <junit.jupiter.execution.parallel.fixed.parallelism>1.5C</junit.jupiter.execution.parallel.fixed.parallelism>
</properties>
```

### 2.2 Test Timeouts

**Per method**:

```java
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void shouldCompleteQuickly() {
    // ...
}
```

**Global** (pom.xml):

```xml
<properties>
  <junit.jupiter.execution.timeout.default>600s</junit.jupiter.execution.timeout.default>
  <junit.jupiter.execution.timeout.testable.method.default>60s</junit.jupiter.execution.timeout.testable.method.default>
</properties>
```

---

## Section 3: Build Cache Configuration

### 3.1 Local Cache (Automatic)

**Default behavior**: Maven 4.0+ automatically caches compilation results

**Location**: `.m2/repository/` (per-artifact cache)

**Cache invalidation triggers**:
- Source file modification
- Compiler version change
- Compiler arguments change
- Dependency version change

**Monitoring**:

```bash
# Cache size
du -sh .m2/repository/

# Cache age
find .m2/repository -name ".lastUpdated" | wc -l

# Recent updates
find .m2/repository -name ".lastUpdated" -mtime -1
```

**Maintenance**:

```bash
# Clear stale entries (weekly)
find .m2/repository -name ".lastUpdated" -mtime +30 -delete

# Full purge (when corrupted)
mvn dependency:purge-local-repository
```

### 3.2 Distributed Cache (Optional, CI/CD)

**Purpose**: Share cache across CI/CD machines

**Configuration** (in CI pipeline):

```xml
<extension>
  <groupId>org.apache.maven.extensions</groupId>
  <artifactId>maven-build-cache-extension</artifactId>
  <version>1.1.1</version>
</extension>
```

**Settings** (`.mvn/maven-build-cache-config.xml`):

```xml
<cache>
  <enabled>true</enabled>
  <local>
    <location>.m2/build-cache</location>
  </local>
  <remote>
    <url>http://cache-server:8080/</url>
  </remote>
  <hash>
    <algorithm>SHA256</algorithm>
  </hash>
</cache>
```

**Expected improvement**:
- PR builds: 20-30% speedup
- Feature branch builds: 10-20% speedup
- Cold CI start: 40-50% speedup

---

## Section 4: CDS (Class Data Sharing)

### 4.1 AppCDS Configuration

**Purpose**: Reduce startup time by pre-loading commonly used classes

**Current status**: Enabled by default in JVM config

**Check if enabled**:

```bash
# During build, should see:
# [... CDS archive used ...]

# Verify in JVM args
grep -i "appcds\|sharedarchivefile" .mvn/jvm.config
```

**Manual setup** (if needed):

```bash
# Generate archive
java -Xshare:dump -XX:+UseAppCDS \
     -XX:SharedArchiveFile=app-cds.jsa \
     -cp target/classes

# Use archive
java -Xshare:on -XX:SharedArchiveFile=app-cds.jsa ...
```

**Benefits**:
- Startup time: -10-20%
- Memory: -5-10%
- GC pause time: -5%

---

## Section 5: CMS (Code Memory System) / JIT Compilation

### 5.1 Compilation Thresholds

**Default (tiered compilation)**:

```properties
-XX:CompileThreshold=10000
-XX:TieredStopAtLevel=4
```

**Tuning options**:

| Setting | Value | Impact |
|---------|-------|--------|
| `CompileThreshold=5000` | Lower | Compile faster, higher C2 overhead |
| `CompileThreshold=10000` | Default | Balanced |
| `CompileThreshold=20000` | Higher | Compile slower, lower C2 overhead |
| `TieredStopAtLevel=1` | C1 only | Fast startup, lower peak perf |
| `TieredStopAtLevel=4` | C1+C2 | Balanced |

---

## Section 6: Virtual Thread Configuration

### 6.1 Scheduler Tuning

**Current default**:

```properties
-Djdk.virtualThreadScheduler.parallelism=auto
-Djdk.virtualThreadScheduler.maxPoolSize=256
```

**Manual tuning**:

```properties
# CPU cores (default auto)
-Djdk.virtualThreadScheduler.parallelism=8

# Maximum carriers
-Djdk.virtualThreadScheduler.maxPoolSize=256

# Debug
-Djdk.virtualThreadScheduler.debug=false
```

---

## Section 7: Environment Variables

### 7.1 Build Environment

```bash
# Enable verbose Maven output
export MAVEN_VERBOSE=true

# Disable offline mode (force repo check)
export DX_OFFLINE=0

# Collect build metrics
export DX_TIMINGS=1

# Use impact graph (future)
export DX_IMPACT=1
```

### 7.2 JVM Environment

```bash
# Additional JVM options
export MAVEN_OPTS="-Xmx4G -XX:+UseZGC"

# Proxy settings (if behind corporate proxy)
export MAVEN_OPTS="$MAVEN_OPTS -Dhttp.proxyHost=proxy.example.com"
export MAVEN_OPTS="$MAVEN_OPTS -Dhttp.proxyPort=3128"
```

---

## Section 8: Profile-Based Configuration

### 8.1 agent-dx Profile

**Enabled by**: `bash scripts/dx.sh`

**Configuration**:
```xml
<profile>
  <id>agent-dx</id>
  <properties>
    <surefire.threadCount>2C</surefire.threadCount>
    <skipTests>false</skipTests>
    <skipJacoco>true</skipJacoco>
    <skipAnalysis>true</skipAnalysis>
  </properties>
</profile>
```

**Speed**: 5-15s per cycle

---

### 8.2 integration-parallel Profile

**Enabled by**: `-P integration-parallel`

**Configuration**:
```xml
<profile>
  <id>integration-parallel</id>
  <properties>
    <failsafe.parallel>methods</failsafe.parallel>
    <failsafe.threadCount>1.5C</failsafe.threadCount>
    <failsafe.forkCount>2</failsafe.forkCount>
  </properties>
</profile>
```

**Speed**: 25-35s for integration tests

---

### 8.3 analysis Profile

**Enabled by**: `-P analysis`

**Configuration**:
```xml
<profile>
  <id>analysis</id>
  <build>
    <!-- SpotBugs, PMD, JaCoCo, etc. -->
  </build>
</profile>
```

**Speed**: 4-5 minutes (run separately from fast builds)

---

## Section 9: Common Configuration Scenarios

### Scenario 1: 8-core Laptop with 16GB RAM

**.mvn/maven.config**:
```
-T 1.5C
```

**.mvn/jvm.config**:
```properties
-Xmx2G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Djdk.virtualThreadScheduler.parallelism=8
```

**Command**:
```bash
bash scripts/dx.sh              # Fast development loop
```

---

### Scenario 2: 16-core Desktop with 32GB RAM

**.mvn/maven.config**:
```
-T 2C
```

**.mvn/jvm.config**:
```properties
-Xmx4G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-Djdk.virtualThreadScheduler.parallelism=16
```

**Command**:
```bash
mvn -T 2C clean verify         # Aggressive parallelism
```

---

### Scenario 3: CI/CD Server (64-core, 128GB RAM)

**.mvn/maven.config**:
```
-T 3C
--no-snapshot-updates
```

**.mvn/jvm.config**:
```properties
-Xmx8G
-XX:+UseCompactObjectHeaders
-XX:+UseZGC
-XX:ParallelGCThreads=16
-Djdk.virtualThreadScheduler.parallelism=32
```

**.github/workflows/ci.yml**:
```yaml
- run: mvn -T 3C clean verify
- run: mvn verify -P analysis (on main only)
```

---

### Scenario 4: Minimal Resource (4GB RAM, 2 cores)

**.mvn/maven.config**:
```
-T 1C
```

**.mvn/jvm.config**:
```properties
-Xmx1G
-XX:+UseG1GC
-XX:MaxGCPauseMillis=150
```

**Command**:
```bash
mvn clean compile              # Skip tests to save resources
mvn test                       # Run tests separately
```

---

## Section 10: Monitoring Configuration

### 10.1 GC Logging

```properties
# .mvn/jvm.config (optional)
-Xlog:gc*:file=gc.log:time,uptime,level,tags
```

**Analysis**:
```bash
# Generate GC report
java -jar GCeasy.jar gc.log

# Key metrics
grep "Pause" gc.log | head -20
```

### 10.2 Compilation Logging

```properties
# Monitor compilation time
-XX:+PrintCompilation
-XX:+PrintInlining
```

### 10.3 Memory Monitoring

```bash
# During build
jstat -gc -h10 <pid> 1000

# Monitor native memory
jcmd <pid> VM.native_memory summary
```

---

## Section 11: Troubleshooting Configuration

### "Build runs out of memory"

**Check current setting**:
```bash
grep -i "xmx" .mvn/jvm.config
```

**Increase heap**:
```properties
-Xmx2G → -Xmx4G
```

---

### "Build is slow (>150s)"

**Check parallelism**:
```bash
grep -i "\-T" .mvn/maven.config
```

**Increase threads**:
```properties
-T 1.5C → -T 2C
```

---

### "Tests fail in parallel"

**Reduce parallelism** (temporary):
```bash
mvn -T 1C test
```

**Fix root cause**: Test isolation issue

---

## Section 12: Configuration Validation

```bash
# Verify Maven version
mvn -v

# Check effective POM
mvn help:effective-pom | head -50

# List active profiles
mvn help:active-profiles

# Show resolved properties
mvn help:describe -Ddetail=true | grep -i property
```

---

## Reference Tables

### Maven Configuration Summary

| File | Property | Default | Min | Max |
|------|----------|---------|-----|-----|
| `.mvn/maven.config` | `-T` | 1.5C | 1C | 3C+ |
| `.mvn/jvm.config` | `-Xmx` | 2G | 1G | 8G+ |
| `pom.xml` | `surefire.threadCount` | 1.5C | 1C | 2C |
| `pom.xml` | `failsafe.forkCount` | 2 | 1 | 4+ |

### JVM Flags Summary

| Flag | Default | Options |
|------|---------|---------|
| GC | ZGC | ZGC, G1GC, Serial |
| Heap | 2G | 1-8G+ |
| Compact headers | ON | ON/OFF |
| Virtual threads | auto | auto, 1-256 |
| Tiered compilation | ON | ON/OFF |

---

**Last Updated**: 2026-02-28
**Maintained By**: YAWL Performance Team
**Questions?**: See `.yawl/PERFORMANCE-TUNING.md` for practical examples
