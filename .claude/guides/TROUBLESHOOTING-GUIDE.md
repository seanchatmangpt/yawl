# Troubleshooting Guide: Parallel Test Execution

**Diagnose and fix common issues with YAWL's parallel test feature.**

---

## Table of Contents

1. [Tests Fail in Parallel but Pass Sequentially](#tests-fail-in-parallel-but-pass-sequentially)
2. [Out of Memory (OOM) Errors](#out-of-memory-oom-errors)
3. [Builds Are Slower Than Expected](#builds-are-slower-than-expected)
4. [Tests Timeout During Parallel Execution](#tests-timeout-during-parallel-execution)
5. [Flaky Tests (Intermittent Failures)](#flaky-tests-intermittent-failures)
6. [State Corruption or Deadlocks](#state-corruption-or-deadlocks)
7. [Thread Interrupt Errors](#thread-interrupt-errors)
8. [Database Lock Errors](#database-lock-errors)
9. [Profile Not Activating](#profile-not-activating)
10. [Coverage Reports Missing](#coverage-reports-missing)

---

## Tests Fail in Parallel but Pass Sequentially

**Symptom**:
```
✓ mvn test                    # PASS
✓ mvn test -Dtest=MyTest     # PASS (single test)
✗ mvn verify -P integration-parallel  # FAIL
```

**Root Cause Analysis**:

This indicates **state leakage** between test classes. Even with separate JVMs, some state is escaping isolation. Common causes:

1. **Shared mutable static fields** (most common)
2. **System property pollution**
3. **File system side effects**
4. **Thread-local variable leakage**
5. **Database state contamination**

### Diagnosis Steps

**Step 1: Identify the failing test**

```bash
# Run with verbose output to see which test fails
mvn verify -P integration-parallel -X 2>&1 | grep FAILURE

# Example output:
# [ERROR] testComplexWorkflow(YNetRunnerIT) Time elapsed: 2.34 s  <<< FAILURE!
# [ERROR] Expected engine status COMPLETE, got SUSPENDED
```

**Step 2: Run test in isolation**

```bash
# Does it pass alone?
mvn test -Dtest=YNetRunnerIT#testComplexWorkflow

# If it passes alone but fails in parallel,
# state leakage is the root cause
```

**Step 3: Identify the culprit**

Check the failing test class for:

```java
// ANTI-PATTERN 1: Static mutable state
public class YNetRunnerIT {
    private static YEngine engine;  // ← SHARED ACROSS TESTS

    @BeforeAll
    static void setup() {
        engine = YEngine.getInstance();
    }
}

// ANTI-PATTERN 2: Static collections
public class DataTest {
    private static Map<String, Data> cache = new HashMap<>();
    // ↑ Shared across all tests in the class
}

// ANTI-PATTERN 3: System.setProperty (global pollution)
@Test
void testDebugMode() {
    System.setProperty("debug", "true");
    // ↑ Affects ALL subsequent tests
}

// ANTI-PATTERN 4: Shared temp files
public class FileTest {
    private static File tempDir = new File("/tmp/test-data");
    // ↑ All tests use same directory, race conditions
}
```

### Solutions

#### Solution 1: Replace Static with Instance Fields

```java
// BEFORE (broken in parallel)
public class YNetRunnerIT {
    private static YEngine engine;

    @BeforeAll
    static void setup() {
        engine = YEngine.getInstance();
    }

    @Test
    void testWorkflow() {
        engine.execute(workflow);
        // ↑ Shared engine, tests interfere
    }
}

// AFTER (safe in parallel)
public class YNetRunnerIT {
    private YEngine engine;  // Instance, not static

    @BeforeEach
    void setup() {
        engine = YEngine.getInstance();
        engine.clear();  // Fresh state per test
    }

    @Test
    void testWorkflow() {
        engine.execute(workflow);
        // ↑ Fresh engine per test
    }
}
```

**Key difference**:
- `@BeforeAll` (static): runs ONCE before all tests in class → state shared
- `@BeforeEach`: runs before EACH test → state isolated

#### Solution 2: Use @TempDir (JUnit 5)

```java
// BEFORE (race conditions)
public class FileTest {
    private static File tempDir = new File("/tmp/test-data");
}

// AFTER (isolated)
public class FileTest {
    @TempDir
    Path tempDir;  // Fresh directory per test

    @Test
    void testFileWrite() {
        Files.write(tempDir.resolve("data.txt"), "test".getBytes());
        // ✓ No conflicts with other tests
    }
}
```

#### Solution 3: Clean System Properties

```java
// BEFORE (pollutes global state)
@Test
void testDebugMode() {
    System.setProperty("debug", "true");
    // ... test code ...
    // ↑ Property lingers, affects other tests
}

// AFTER (restore after test)
@Test
void testDebugMode() {
    String oldValue = System.getProperty("debug");
    try {
        System.setProperty("debug", "true");
        // ... test code ...
    } finally {
        if (oldValue != null) {
            System.setProperty("debug", oldValue);
        } else {
            System.clearProperty("debug");
        }
    }
}

// OR use JUnit 5 annotation
@Test
@EnableSystemProperty(key = "debug", value = "true")
void testDebugMode() {
    assertEquals(System.getProperty("debug"), "true");
    // ✓ Auto-restored after test
}
```

#### Solution 4: Clear ThreadLocal Storage

```java
// If using ThreadLocal<YEngine>
@AfterEach
void cleanup() {
    ThreadLocalYEngineManager.clearCurrent();
}
```

#### Solution 5: Check for Singleton Misuse

```java
// BEFORE (dangerous singleton pattern)
public class ConfigManager {
    private static ConfigManager instance;
    private Map<String, String> config;

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void setConfig(Map<String, String> cfg) {
        this.config = cfg;  // ← Shared across tests
    }
}

// AFTER (inject config per test)
public class ConfigManager {
    private final Map<String, String> config;

    public ConfigManager(Map<String, String> config) {
        this.config = new HashMap<>(config);  // Copy, don't share
    }
}

// In test
@Test
void testWithConfig() {
    Map<String, String> config = Map.of("key", "value");
    ConfigManager manager = new ConfigManager(config);
    // ✓ Each test gets fresh instance
}
```

### Verification

After fixing, confirm the test passes in parallel:

```bash
# Test in isolation (should pass)
mvn test -Dtest=YNetRunnerIT#testComplexWorkflow

# Test with parallelization (should also pass)
mvn verify -P integration-parallel -Dtest=YNetRunnerIT

# Run full suite
mvn verify -P integration-parallel
```

---

## Out of Memory (OOM) Errors

**Symptom**:
```
[ERROR] Exception in thread "main" java.lang.OutOfMemoryError: Java heap space
[ERROR]   at java.util.HashMap.newNode(HashMap.java:1645)
```

**Root Cause**:

Too many JVM processes launched simultaneously, consuming all available memory.

**Example**: With `forkCount=3C` on a 4-core machine:
- Each fork: 1GB heap
- 12 forks launched: 12GB total (if you only have 8GB)
- Result: OOM

### Diagnosis

**Check memory pressure**:

```bash
# Linux
free -h
# Should show available RAM > (forkCount × 1GB)

# macOS
vm_stat
# Look at "free pages"

# Windows
Get-WmiObject -Class Win32_ComputerSystem | Select TotalPhysicalMemory
```

**During parallel build**:

```bash
# In another terminal, monitor memory
watch -n 1 'ps aux | grep java | wc -l'
# Count: number of Java processes

ps aux | grep java | awk '{sum+=$6} END {print sum / 1024 "MB"}'
# Total: memory used by Java processes
```

### Solutions

#### Solution 1: Reduce Fork Count (Most Common)

```bash
# Default (too aggressive on low-end machines)
mvn verify -P integration-parallel
# Uses: forkCount=2C

# For low-memory machines (reduce forks)
mvn verify -P integration-parallel -Dfailsafe.forkCount=1.5C
# Uses: 6 forks on 4-core machine

# Even safer
mvn verify -P integration-parallel -Dfailsafe.forkCount=1C
# Uses: 4 forks on 4-core machine (sequential-ish but optimized)
```

**Math**:
- Available RAM: 8GB
- Per-fork heap: 1GB (default)
- Max forks: 8 / 1 = 8 safe
- Safe forkCount: 1.5C to 2C

#### Solution 2: Increase Heap Size Per Fork

```bash
# Increase total available memory to JVM
export MAVEN_OPTS="-Xmx4g"
mvn verify -P integration-parallel

# Or set in .mvn/maven.config
# -Xmx2g
# -XX:+UseZGC
```

#### Solution 3: Disable Thread-Local Isolation (Saves Memory)

```bash
# ThreadLocal storage uses extra memory
mvn verify -P integration-parallel \
  -Dyawl.test.threadlocal.isolation=false
# Saves ~200MB of memory per fork
```

#### Solution 4: Run Tests in Batches

```bash
# Split test execution
# Run unit tests only
mvn test -P quick-test

# Run integration tests with lower parallelism
mvn verify -P integration-parallel \
  -DskipUnitTests=true \
  -Dfailsafe.forkCount=1.5C
```

### Verification

After fixing, re-run the build:

```bash
# Monitor memory usage
watch -n 1 free -h
# In another terminal
mvn verify -P integration-parallel -Dfailsafe.forkCount=1.5C
```

---

## Builds Are Slower Than Expected

**Symptom**:
```
# Expected: 85s
# Actual: 130s
# Speedup: only 1.15x (should be 1.77x)
```

**Root Causes**:

1. **Not enough parallelizable tests** (some large, some small)
2. **I/O contention** (all tests hitting same database)
3. **Thread pool overhead** (too many forks, too much context switching)
4. **Memory pressure** (swapping, GC pauses)
5. **Suboptimal fork count** for your hardware

### Diagnosis

**Check test distribution**:

```bash
# See which tests are slowest
mvn verify -P integration-parallel -X 2>&1 | \
  grep "Time elapsed" | sort -t: -k3 -nr | head -10

# Example:
# YNetRunnerIT (45s)
# DataModellingIT (35s)
# YWorkItemIT (20s)
```

**Identify bottleneck**:

```bash
# Test with fewer forks to see baseline
mvn verify -P integration-parallel -Dfailsafe.forkCount=1

# If much slower (~150s), tests are sequential
# If close to current time (~120s), I/O contention is issue
```

**Check system load**:

```bash
# During build, monitor CPU usage
top -b | head -20
# Look for: load average
# If < 4 on 4-core machine, parallelism not maxed out
```

### Solutions

#### Solution 1: Check if Tests are Actually Running in Parallel

```bash
# Run with explicit parallelism visibility
mvn verify -P integration-parallel \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=debug 2>&1 | grep -i "parallel\|fork"

# Count active JVMs during build
while true; do
  ps aux | grep "[j]ava" | wc -l
  sleep 1
done
# Should see multiple java processes, not just 1
```

#### Solution 2: Increase Parallel Factor

```bash
# Default is 2.0, increase to 2.5 or 3.0
mvn verify -P integration-parallel \
  -Djunit.jupiter.execution.parallel.config.dynamic.factor=2.5
```

#### Solution 3: Investigate I/O Contention

```bash
# Check if all tests use same database
grep -r "jdbc:h2" test/**/*.java

# If all use same file-based H2 database:
# - Move to in-memory: jdbc:h2:mem:test
# - Or use separate databases per fork:
#   jdbc:h2:~/test-${fork-number}.h2
```

**Example fix** (in test properties):

```properties
# BEFORE (all tests share same database)
spring.datasource.url=jdbc:h2:~/yawl-test.h2db

# AFTER (separate database per fork)
spring.datasource.url=jdbc:h2:~/yawl-test-${java.util.Random}.h2db
```

#### Solution 4: Check Test Size Distribution

```bash
# Tests should be roughly balanced
#
# GOOD: All tests 5-15s each → parallelism works well
# BAD: One test 60s, others 1s each → slowest test dominates
```

**If imbalanced, split large tests**:

```java
// BEFORE (one slow test)
public class ComplexWorkflowIT {
    @Test
    void testCompleteWorkflow() {
        // 45 seconds of tests
    }
}

// AFTER (split into multiple classes)
public class ComplexWorkflowPart1IT {
    @Test
    void testInitialization() { /* 15s */ }

    @Test
    void testExecution() { /* 15s */ }
}

public class ComplexWorkflowPart2IT {
    @Test
    void testCompletion() { /* 15s */ }
}
```

#### Solution 5: Optimize Your Hardware Usage

```bash
# High-end machines (8+ cores, 16GB+ RAM)
mvn verify -P integration-parallel \
  -Dfailsafe.forkCount=3C \
  -Dfailsafe.threadCount=12

# Low-end machines (2 cores, 4GB RAM)
mvn verify -P integration-parallel \
  -Dfailsafe.forkCount=1C \
  -Dfailsafe.reuseForks=true

# Check actual speedup
# Time parallel build
time mvn verify -P integration-parallel

# Time sequential build
time mvn verify -Dfailsafe.forkCount=1 -Dfailsafe.reuseForks=true
```

---

## Tests Timeout During Parallel Execution

**Symptom**:
```
[ERROR] testComplexWorkflow(YNetRunnerIT) timed out after 120 seconds
[ERROR] Tests run: 5, Failures: 1 (timeout)
```

**Root Cause**:

Tests are I/O bound (database access, network calls, disk). Parallel execution increases contention for shared resources, slowing down individual tests.

### Diagnosis

**Check which test times out**:

```bash
# Run with timeout debugging
mvn verify -P integration-parallel \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=debug 2>&1 | grep -i timeout

# Run just that test sequentially
mvn test -Dtest=YNetRunnerIT#testComplexWorkflow
# Usually passes when run alone
```

### Solutions

#### Solution 1: Increase Timeout (Simplest)

```bash
# Default: 120s
# Increase to 180s
mvn verify -P integration-parallel \
  -Dintegration.test.timeout.default='180 s'
```

#### Solution 2: Profile the Slow Test

```java
@Test
@Timeout(value = 10, unit = TimeUnit.SECONDS)
void testComplexWorkflow() {
    // Measure each part
    long t1 = System.currentTimeMillis();
    setup();
    long t2 = System.currentTimeMillis();
    System.out.println("Setup: " + (t2 - t1) + "ms");

    execute();
    long t3 = System.currentTimeMillis();
    System.out.println("Execute: " + (t3 - t2) + "ms");

    verify();
    long t4 = System.currentTimeMillis();
    System.out.println("Verify: " + (t4 - t3) + "ms");
}
```

#### Solution 3: Replace Waits with Proper Fixtures

```java
// BEFORE (fragile timing, slow in parallel)
@Test
void testWorkflowCompletion() {
    engine.execute(workflow);
    Thread.sleep(2000);  // ← Arbitrary wait
    assertEquals(engine.getStatus(), COMPLETE);
}

// AFTER (proper synchronization)
@Test
void testWorkflowCompletion() {
    engine.execute(workflow);
    assertTrue(
        engine.waitForStatus(COMPLETE, Duration.ofSeconds(5)),
        "Engine did not complete within 5 seconds"
    );
}
```

#### Solution 4: Reduce I/O Contention

```bash
# Use in-memory database instead of file-based
mvn verify -P integration-parallel \
  -Dspring.datasource.url=jdbc:h2:mem:test

# Or separate databases per fork
mvn verify -P integration-parallel \
  -Dspring.datasource.url=jdbc:h2:~/test-${RANDOM}.h2db
```

#### Solution 5: Exclude Slow Tests

```bash
# Mark test with @Tag("slow")
@Tag("slow")
public class HeavyIntegrationIT {
    @Test
    void testHeavyWorkload() { /* 45s */ }
}

# Exclude slow tests during parallel run
mvn verify -P integration-parallel \
  -DexcludedGroups=slow
```

---

## Flaky Tests (Intermittent Failures)

**Symptom**:
```
# Test fails sometimes:
✓ Run 1: PASS
✗ Run 2: FAIL (race condition?)
✓ Run 3: PASS
✗ Run 4: FAIL
```

**Root Cause**:

Race condition or timing issue exposed by parallel execution.

### Solutions

#### Solution 1: Explicit Waits Instead of Sleep

```java
// BEFORE (flaky - timing-dependent)
Thread.sleep(100);
assertEquals(engine.getStatus(), COMPLETE);

// AFTER (robust - waits until ready)
assertTrue(
    engine.waitForStatus(COMPLETE, Duration.ofSeconds(5)),
    "Engine did not complete within 5s"
);
```

#### Solution 2: Synchronization Primitives

```java
// Use CountDownLatch for coordination
CountDownLatch latch = new CountDownLatch(1);

engine.onComplete(() -> latch.countDown());
engine.execute(workflow);

assertTrue(
    latch.await(5, TimeUnit.SECONDS),
    "Workflow did not complete"
);
```

#### Solution 3: Isolate Shared State

```java
// Use fixtures to isolate each test
@Test
void test1() {
    YEngine engine = new YEngine();  // Fresh instance
    engine.execute(workflow1);
    // ...
}

@Test
void test2() {
    YEngine engine = new YEngine();  // Fresh instance
    engine.execute(workflow2);
    // ...
}
```

#### Solution 4: Run Test in Loop to Reproduce

```bash
# Run test 10 times to catch flakiness
for i in {1..10}; do
    mvn test -Dtest=YNetRunnerIT#testFlaky -P integration-parallel || break
done

# If it fails on 5th iteration, there's an issue
```

---

## State Corruption or Deadlocks

**Symptom**:
```
[ERROR] testDanglingReference(YNetRunnerIT) FAIL
[ERROR] Expected engine in state READY, got state SUSPENDED
[ERROR] This suggests test A modified state that test B expected
```

**Root Cause**:

Despite isolation mechanisms, state is leaking between test classes.

### Solutions

#### Solution 1: Verify Isolation is Enabled

```bash
# Check system properties
mvn verify -P integration-parallel -X | grep "threadlocal.isolation"

# Should show:
# -Dyawl.test.threadlocal.isolation=true
```

#### Solution 2: Manual Cleanup in @AfterEach

```java
@AfterEach
void cleanup() {
    // Clear all thread-local state
    ThreadLocalYEngineManager.clearCurrent();

    // Reset static fields (if unavoidable)
    YEngine.getInstance().reset();

    // Close resources
    dataSource.close();
}
```

#### Solution 3: Disable Parallelization to Verify Fix

```bash
# Run with forkCount=1 (sequential)
mvn verify -P integration-parallel -Dfailsafe.forkCount=1

# If test passes, state leakage confirmed
# Fix by reviewing "Tests Fail in Parallel" section above
```

---

## Thread Interrupt Errors

**Symptom**:
```
[ERROR] java.lang.InterruptedException: sleep interrupted
[ERROR] at java.lang.Thread.sleep(Thread.java:1234)
[ERROR] at org.yawlfoundation.yawl.engine.YNetRunner.waitForCompletion
```

**Root Cause**:

Parallel execution interrupts threads more aggressively due to timeout enforcement.

### Solutions

#### Solution 1: Respect Interruption

```java
// BEFORE (ignores interruption, dangerous)
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    // Ignore - BAD
}

// AFTER (respects interruption)
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    throw new RuntimeException("Interrupted", e);
}
```

#### Solution 2: Use Timeout Utilities

```java
// Use JUnit 5 @Timeout annotation
@Test
@Timeout(value = 5, unit = TimeUnit.SECONDS)
void testWithTimeout() {
    engine.execute(workflow);
    // ✓ Automatically interrupted after 5s
}
```

#### Solution 3: Increase Timeout

```bash
mvn verify -P integration-parallel \
  -Dintegration.test.timeout.default='180 s'
```

---

## Database Lock Errors

**Symptom**:
```
[ERROR] java.sql.SQLException: database is locked
[ERROR] Timeout trying to lock table APP.WORKFLOW
```

**Root Cause**:

Multiple test classes access the same H2 database file simultaneously, causing lock contention.

### Solutions

#### Solution 1: Use In-Memory Database

```properties
# In test/resources/application-test.properties
spring.datasource.url=jdbc:h2:mem:test
spring.datasource.driver-class-name=org.h2.Driver
```

#### Solution 2: Per-Fork Database

```properties
# Separate database file per fork
spring.datasource.url=jdbc:h2:~/yawl-test-${java.util.Random}.h2db
```

#### Solution 3: Reduce Parallelism

```bash
mvn verify -P integration-parallel \
  -Dfailsafe.forkCount=1.5C
```

---

## Profile Not Activating

**Symptom**:
```
# This should use parallelization but doesn't
mvn verify -P integration-parallel
# Takes 150s (sequential time) instead of 85s (parallel time)
```

**Root Cause**:

Profile not properly recognized by Maven.

### Solutions

#### Solution 1: Verify Profile Exists

```bash
mvn help:active-profiles

# Should show: integration-parallel

# If not listed, try:
mvn help:describe -Ddetail=true | grep integration-parallel
```

#### Solution 2: Explicit Activation

```bash
# Make sure you're in the right directory
cd /home/user/yawl

# Try explicit profile activation
mvn verify -P integration-parallel

# Check verbose output
mvn verify -P integration-parallel -X | grep -i profile
```

#### Solution 3: Check for Profile Typos

```bash
# Common typos:
mvn verify -P integration_parallel  # Wrong: underscore
mvn verify -P IntegrationParallel   # Wrong: case sensitivity

# Correct:
mvn verify -P integration-parallel  # Correct: hyphen, lowercase
```

---

## Coverage Reports Missing

**Symptom**:
```
# Expected: target/site/jacoco/index.html
# Actual: File doesn't exist
```

**Root Cause**:

JaCoCo disabled by default in most profiles (for speed).

### Solutions

#### Solution 1: Use CI Profile

```bash
mvn verify -P ci

# Generates: target/site/jacoco/index.html
open target/site/jacoco/index.html
```

#### Solution 2: Enable JaCoCo Explicitly

```bash
mvn verify -Djacoco.skip=false

# Generates coverage report
# But slower (~120s instead of 85s)
```

#### Solution 3: Check in Prod Profile

```bash
mvn verify -P prod

# Also generates coverage
# (along with CVE scanning)
```

---

## Quick Reference: Symptom → Solution

| Symptom | Check Command | Solution |
|---------|---------------|----------|
| Tests fail in parallel | `mvn test -Dtest=Failing`Test` | Check static fields, use @BeforeEach |
| Out of memory | `free -h` | Reduce `forkCount` or increase heap |
| Too slow | `ps aux \| grep java \| wc -l` | Increase `forkCount` if cores available |
| Timeout | `mvn verify ... -Dintegration.test.timeout.default='180 s'` | Increase timeout |
| Flaky tests | `for i in {1..10}; do mvn test ...; done` | Use proper waits, not `sleep()` |
| Database locked | Check H2 URL | Use in-memory or separate databases |
| Profile not found | `mvn help:active-profiles` | Check spelling |
| No coverage report | `ls target/site/jacoco/` | Use `-P ci` profile |

---

## Getting Help

If your issue isn't listed here:

1. **Check logs**:
   ```bash
   cat /tmp/dx-build-log.txt | tail -50
   ```

2. **Run with debug output**:
   ```bash
   mvn verify -P integration-parallel -X 2>&1 | tee debug.log
   ```

3. **Isolate the failing test**:
   ```bash
   mvn test -Dtest=YourFailingTest
   ```

4. **Check the developer guide**:
   See `DEVELOPER-GUIDE-PARALLELIZATION.md` (15 pages of detailed reference)

---

**Version**: 1.0
**Status**: Production Ready
**Last Updated**: February 28, 2026
