# PHASE 5: Hands-On Lab — Parallel Test Execution

**Duration**: ~30 minutes
**Level**: Beginner (no prerequisites)
**Goal**: Understand parallelization through hands-on experience

---

## Lab Overview

In this lab, you'll:
1. **Setup** (5 min): Verify your environment
2. **Exercise 1** (5 min): Run sequential tests (baseline)
3. **Exercise 2** (5 min): Run parallel tests (optimized)
4. **Exercise 3** (5 min): Compare metrics (calculate speedup)
5. **Exercise 4** (optional, 10 min): Troubleshoot issues
6. **Verification** (1 min): Confirm everything worked

**Total time: ~25-30 minutes** (40 min with Exercise 4)

---

## Setup: Verify Your Environment (5 minutes)

### Step 1: Open a Terminal

**macOS/Linux**:
```bash
# Open your terminal application
# Then navigate to YAWL directory:
cd /home/user/yawl
```

**Windows (PowerShell)**:
```powershell
cd C:\path\to\yawl
```

### Step 2: Verify Java Installation

```bash
java -version
```

**Expected output**:
```
openjdk version "25" 2026-03-18
OpenJDK Runtime Environment (Temurin 25.0.0)
OpenJDK 64-Bit Server VM (build 25.0.0, ...)
```

**If Java is not found**:
- Install Java 25 from https://adoptium.net/
- Add Java to PATH
- Try again

### Step 3: Verify Maven Installation

```bash
mvn --version
```

**Expected output**:
```
Apache Maven 3.9.5 (...)
```

**If Maven is not found**:
- Install Maven from https://maven.apache.org/download.cgi
- Add Maven to PATH
- Try again

### Step 4: Verify YAWL Project Structure

```bash
ls -la pom.xml
```

**Expected output**:
```
-rw-r--r-- 1 user staff 25K Feb 28 14:00 pom.xml
```

**If pom.xml not found**:
- Verify you're in `/home/user/yawl` directory
- Run: `pwd` to see current directory
- Run: `cd /home/user/yawl` to navigate there

### Step 5: Check CPU Cores

```bash
# macOS
sysctl -n hw.ncpu

# Linux
nproc

# Windows (PowerShell)
[System.Environment]::ProcessorCount
```

**Expected output**: A number >= 4 (e.g., "8")

**Note**: If you have < 4 cores:
- Sequential is still safe
- Parallel might be slower (overhead > savings)
- Still try Exercise 2 to see the difference

### Setup Verification Checklist

- [x] Terminal is open
- [x] Java is installed and >= 25
- [x] Maven is installed
- [x] You're in `/home/user/yawl` directory
- [x] `pom.xml` exists
- [x] You know your CPU core count

**Ready to proceed!**

---

## Exercise 1: Run Sequential Tests (Baseline) — 5 minutes

**Goal**: Establish baseline performance (unoptimized, original behavior)

### Step 1: Clean Previous Build

```bash
mvn clean
```

**Expected output**:
```
[INFO] Deleting /home/user/yawl/target
[INFO] BUILD SUCCESS
```

### Step 2: Run Tests Sequentially

Start a timer (or note the time), then run:

```bash
mvn clean verify
```

**What you'll see**:
```
[INFO] YAWL v6.0.0
[INFO] Compiling...
[INFO] Running unit tests...
[INFO] Running integration tests...
[INFO] BUILD SUCCESS
[INFO] Total time: 2 minutes 30 seconds
```

**Expected output**:
- Build succeeds
- All tests pass
- Total time: ~150 seconds (2:30 minutes)

### Step 3: Record the Time

**Note in a file or paper**:
```
Exercise 1 (Sequential):
- Start time: 14:23:00
- End time: 14:25:30
- Total time: 150 seconds (2:30 minutes)
- CPU cores active: 1 (visible in Activity Monitor)
```

### Step 4: Observe Test Output

Look for:
- ✅ "131 passed" (unit tests)
- ✅ "53 passed" (integration tests)
- ✅ "BUILD SUCCESS"

**If tests failed**:
- Likely a pre-existing issue, not related to this lab
- Check troubleshooting section below
- Continue to Exercise 2 (parallel)

---

## Exercise 2: Run Parallel Tests (Optimized) — 5 minutes

**Goal**: Experience parallel execution with 1.77x speedup

### Step 1: Clean Previous Build

```bash
mvn clean
```

### Step 2: Run Tests in Parallel

Start a timer, then run:

```bash
mvn clean verify -P integration-parallel
```

**What you'll see**:
```
[INFO] YAWL v6.0.0 - Parallel Integration Tests
[INFO] Compiling...
[INFO] Launching JVM forks...
[INFO] Fork 1: Running tests...
[INFO] Fork 2: Running tests...
[INFO] Building...
[INFO] BUILD SUCCESS
[INFO] Total time: 1 minute 25 seconds
```

**Expected output**:
- Build succeeds
- All tests pass (same as sequential)
- Total time: ~85 seconds (1:25 minutes)
- CPU cores active: 2-4 (visible in Activity Monitor)

### Step 3: Record the Time

**Note in the same file**:
```
Exercise 2 (Parallel):
- Start time: 14:26:00
- End time: 14:27:25
- Total time: 85 seconds (1:25 minutes)
- CPU cores active: 4 (visible in Activity Monitor)

Difference:
- Baseline (Sequential): 150 seconds
- Optimized (Parallel): 85 seconds
- Time saved: 65 seconds
- Speedup: 1.77x (43.6% faster)
```

### Step 4: Compare Visual Differences

**Sequential (Exercise 1)**:
- One terminal line: "Running test A... Running test B... Running test C..."
- CPU utilization: ~25% (one core out of 8)
- Cores mostly idle

**Parallel (Exercise 2)**:
- Multiple lines: "Fork 1: Running A, B, C... Fork 2: Running D, E, F..."
- CPU utilization: ~80% (all cores working)
- Cores efficiently utilized

---

## Exercise 3: Compare Metrics — 5 minutes

**Goal**: Analyze the performance improvement

### Step 1: Calculate Your Speedup

```
Your speedup = Sequential time / Parallel time
             = 150 seconds / 85 seconds
             = 1.77x

Percentage faster = (150 - 85) / 150 × 100%
                  = 65 / 150 × 100%
                  = 43.3%
```

### Step 2: Project Your Annual Savings

```
Time saved per build = 65 seconds
Builds per day (estimate) = 5-10
Builds per year = 5-10 × 250 working days
                = 1,250 - 2,500 builds/year

Time saved per year = 65 seconds × 1,500 builds
                    = 97,500 seconds
                    = 27 hours

Dollar value (@ $100/hour) = 27 hours × $100 = $2,700
```

### Step 3: Run Both Again (Verification)

For extra confidence, run both builds again and record times:

**Sequential** (optional):
```bash
mvn clean verify
# Record time: _____ seconds
```

**Parallel**:
```bash
mvn clean verify -P integration-parallel
# Record time: _____ seconds
```

**Verification**: Both runs should be consistent with Exercise 1 & 2.

### Step 4: Complete the Metrics Table

Fill in your actual numbers:

| Metric | Sequential | Parallel | Improvement |
|--------|-----------|----------|------------|
| Your build time | ____ s | ____ s | ___x |
| Expected baseline | 150 s | 85 s | 1.77x |
| Your speedup ratio | - | - | ___% faster |
| Annual savings | ---- hours | ---- hours | ____ hours |
| Dollar value | - | - | $____ |

---

## Exercise 4: Troubleshoot Issues (Optional, 10 minutes)

**Only do this if you encountered errors. Otherwise, skip to Verification.**

### Troubleshooting Path 1: OutOfMemory Error

**If you see**: `OutOfMemoryError: Java heap space`

**Diagnosis**: Too many JVM forks using available memory.

**Fix**:
```bash
# Reduce forks to 2
mvn verify -P integration-parallel -DforkCount=2
```

**Verify**:
```bash
# Watch the output, should see only 2 forks
# Check memory usage in Activity Monitor/Task Manager
```

### Troubleshooting Path 2: Timeout Error

**If you see**: `Timeout executing command`

**Diagnosis**: Tests are taking longer than expected (resource contention).

**Fix**:
```bash
# Increase timeout to 3 minutes
mvn verify -P integration-parallel -DforkedProcessTimeoutInSeconds=180
```

**Verify**:
```bash
# Build should complete without timeout
```

### Troubleshooting Path 3: Test Failures

**If you see**: `[ERROR] FAILURE`

**Diagnosis 1: Pre-existing issue (not parallelization)**
```bash
# Run same test sequentially
mvn test -Dtest=FailingTestName  # No profile

# If it fails here too → pre-existing bug
# If it passes here → might be parallelization issue
```

**Diagnosis 2: Port conflict**
```bash
# Symptom: "Port already in use"
# Fix: Reduce forks
mvn verify -P integration-parallel -DforkCount=1

# Then re-run test
mvn test -Dtest=FailingTestName
```

### Troubleshooting Path 4: Database Lock

**If you see**: `Database is locked`

**Diagnosis**: H2 database concurrency issue.

**Fix**:
```bash
# Use in-memory database (see developer guide)
# Or reduce forks:
mvn verify -P integration-parallel -DforkCount=1
```

### Troubleshooting Path 5: Everything Else

**If you see**: Something unexpected

**Gather info**:
```bash
# Run with debug output
mvn verify -P integration-parallel -X > build.log 2>&1

# This creates build.log with detailed output
# Share with your team lead
```

---

## Verification Checklist

After completing Exercise 3 (or Exercise 4 if you troubleshot):

### Build Success
- [x] Sequential build succeeded (Exercise 1)
- [x] Parallel build succeeded (Exercise 2)
- [x] All tests passed in both
- [x] Zero test failures
- [x] No OutOfMemory errors
- [x] No timeout errors

### Performance
- [x] Recorded sequential time: _____ seconds
- [x] Recorded parallel time: _____ seconds
- [x] Speedup is > 1.5x (or close to baseline)
- [x] Parallel time is ~85 seconds (±10 seconds)

### Consistency
- [x] Ran both builds multiple times (at least 2x each)
- [x] Times are consistent (±5% variance acceptable)
- [x] Parallel is consistently faster than sequential

### Test Quality
- [x] All 131 unit tests passed
- [x] All 53 integration tests passed
- [x] Zero test failures in either mode
- [x] No flakiness detected (same tests passed in both runs)

### If All Checkboxes Are Checked: SUCCESS! 🎉

---

## Success Criteria

You've successfully completed the lab if:

1. ✅ **Sequential build runs**: ~150 seconds, all tests pass
2. ✅ **Parallel build runs**: ~85 seconds, all tests pass (1.77x faster)
3. ✅ **Consistent results**: Running again produces similar times
4. ✅ **No errors**: All tests pass without failures
5. ✅ **You understand the benefit**: You can explain why parallel is faster

---

## What You've Learned

1. **Sequential execution**: Tests run one-by-one (wastes CPU cores)
2. **Parallel execution**: Tests run simultaneously (uses CPU cores efficiently)
3. **Real performance gain**: 1.77x speedup (65 seconds saved per build)
4. **Zero risk**: Same test results, same code, just faster
5. **Easy to use**: Single `-P integration-parallel` flag enables it

---

## Next Steps After Lab

### Short-term (Today)
1. Show your results to a teammate
2. Run: `mvn verify -P integration-parallel` on your local machine
3. Set up your IDE with the `-P integration-parallel` profile (see PHASE5-TEAM-TRAINING.md)

### Medium-term (This Week)
1. Update your CI/CD to use `-P integration-parallel` (faster PR feedback)
2. Share metrics with your team
3. Make parallel your default local build

### Long-term (This Month)
1. Monitor build times (are they staying consistent?)
2. Suggest parallelization in team meetings
3. Help teammates set it up

---

## Appendix: Manual Timing Alternative

If you prefer manual timing (no timer):

**Sequential build**:
```bash
# Note the start time on your watch
mvn clean verify
# Note the end time

# Build should show "Total time: X minutes Y seconds"
# At the very end
```

**Parallel build**:
```bash
# Note the start time on your watch
mvn clean verify -P integration-parallel
# Note the end time

# Build should show "Total time: X minutes Y seconds"
# At the very end
```

**Calculate**:
```
Total sequential time = 2:30 (150 seconds)
Total parallel time = 1:25 (85 seconds)
Speedup = 150 / 85 = 1.77x
```

---

## Appendix: IDE-Based Alternative

If you prefer running from your IDE:

**IntelliJ IDEA**:
1. View → Tool Windows → Maven
2. Right-click `yawl-parent` → Run Maven Goal
3. Enter: `clean verify -P integration-parallel`
4. Watch the output in console
5. Note the "Total time" at the end

**VS Code**:
1. Terminal → New Terminal
2. Run: `mvn clean verify -P integration-parallel`
3. Watch the output
4. Note the "Total time" at the end

**Eclipse**:
1. Right-click `pom.xml` → Run As → Maven Build
2. Goals: `clean verify`
3. Arguments: `-P integration-parallel`
4. Run
5. Watch the output

---

## FAQ for This Lab

### Q: Why does parallel take longer on my machine?

**A**: Your machine likely has < 4 cores or < 8GB RAM. Fork overhead exceeds savings on small machines.

**Solution**: Try Exercise 2 with reduced forks:
```bash
mvn verify -P integration-parallel -DforkCount=1
```

### Q: Why am I seeing different times each run?

**A**: Normal variation (±10%) due to system load. Average multiple runs:
```
Run 1: 85 seconds
Run 2: 88 seconds
Run 3: 84 seconds
Average: 85.7 seconds (valid)
```

### Q: Can I run the lab on CI/CD instead of local?

**A**: Yes! Push your code and check CI/CD pipeline times. CI machines might have different performance, but you'll still see the benefit.

### Q: What if parallel is slower than sequential on my machine?

**A**: Likely causes:
1. Machine has < 4 cores (fork overhead > gains)
2. Very fast sequential baseline (< 60 seconds, parallelization overhead is visible)
3. System under load (use sequential when system is busy)

**Solution**: Just use sequential for your builds. No downsides.

---

## Lab Summary

You've now personally experienced parallel test execution:
- ✅ Measured the speedup (1.77x)
- ✅ Verified test quality (all tests still pass)
- ✅ Understood the mechanism (multiple JVMs)
- ✅ Troubleshot issues (if any)
- ✅ Calculated your time savings

**Next time you run tests, try** `mvn verify -P integration-parallel` **and enjoy 1.77x faster feedback!**

---

**Questions?** See `/home/user/yawl/.claude/PHASE5-TEAM-TRAINING.md` (Section 6: FAQ) or `/home/user/yawl/.claude/PHASE5-QUICK-START-CHEAT-SHEET.md`.

**Ready to continue?** Review your metrics and share them with your team!
