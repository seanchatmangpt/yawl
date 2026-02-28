# IDE Setup Guide: IntelliJ IDEA — Parallel Integration Tests

**Version**: 1.0
**Date**: February 2026
**IDE Version**: IntelliJ IDEA 2024.1 or later
**Maven**: 3.9+
**Java**: 25+

---

## Table of Contents

1. [Quick Setup (2 minutes)](#quick-setup)
2. [Create Run Configuration (Recommended)](#create-run-configuration)
3. [Set Default Profile](#set-default-profile)
4. [Using Parallel Tests](#using-parallel-tests)
5. [Troubleshooting](#troubleshooting)
6. [Advanced Configuration](#advanced-configuration)

---

## Quick Setup

### Option 1: Command-Line in Terminal (30 seconds)

**Fastest way to try parallel tests:**

1. **Open Terminal in IntelliJ**: View → Tool Windows → Terminal
2. **Type this command**:
   ```bash
   mvn clean verify -P integration-parallel
   ```
3. **Press Enter**
4. **Wait ~85 seconds for results**

That's it! Watch the build progress in the terminal.

**Result**: Tests complete in ~85 seconds (vs 150s sequential)

---

### Option 2: Maven Tool Window (1 minute)

**Best for quick testing:**

1. **Open Maven Tool**: View → Tool Windows → Maven
   - (If not visible, try: View → Tool Windows → Maven or use View → Tool Windows → Show All)

2. **Right-click `yawl-parent`** (the root project)

3. **Select "Run Maven Goal"**

4. **Type**: `clean verify -P integration-parallel`

5. **Press Enter**

6. **Watch tests run in IDE** (console output shows progress)

**Result**: Tests complete with full IDE integration (click on failing tests, etc.)

---

## Create Run Configuration (Recommended)

**Best for regular use: Create a button you can click anytime**

### Step 1: Open Run Configurations Dialog

**Menu**: Run → Edit Configurations

(Or press `Ctrl+Alt+Shift+D` on Windows/Linux, or `Cmd+Shift+D` on macOS)

### Step 2: Create New Maven Configuration

1. **Click "+" button** in top-left
2. **Select "Maven"** from dropdown
3. You'll see a blank Maven configuration form

### Step 3: Fill In Configuration Details

| Field | Value |
|-------|-------|
| **Name** | `Parallel Integration Tests` |
| **Working directory** | `/home/user/yawl` (default, your YAWL root) |
| **Command line** | `clean verify -P integration-parallel` |
| **Profiles** | `integration-parallel` (or blank—it's in command line) |
| **Properties** | Leave blank |
| **Skip tests** | ✗ Unchecked (we want to run tests!) |
| **Run tests** | ✓ Checked |
| **Resolve workspace artifacts** | ✓ Checked |
| **JRE** | Default (or Java 25 if available) |

### Step 4: Save Configuration

Click **Apply** then **OK**

---

## Using Parallel Tests

### Method 1: Click Run Button (Easiest)

Now you have a new run configuration:

1. **Look at top-right of IDE** (near the green play button)
2. **Click the dropdown** that says "Parallel Integration Tests"
3. **Click green play button** (▶)

**Result**: IntelliJ runs tests in parallel. Watch output in the IDE's "Run" tool window.

### Method 2: Use Run Menu

1. **Run** → **Parallel Integration Tests** (it'll be in the menu)

### Method 3: Keyboard Shortcut

1. **Run** → **Edit Configurations** → Select "Parallel Integration Tests" → Note the assigned shortcut
2. (Or right-click configuration → Create Shortcut)
3. Use the keyboard shortcut whenever you want

---

## Set Default Profile

**Optional: Make parallel your default build profile**

### In IntelliJ's Maven Properties

1. **Open Maven tool**: View → Tool Windows → Maven
2. **Click the settings icon** (gear) in Maven tool window
3. **Maven Settings** dialog opens
4. **Look for "Profiles"** section
5. **Check "integration-parallel"** to make it default

Now `mvn` commands automatically use this profile (optional).

---

## Using Parallel Tests in Different Scenarios

### Scenario 1: Running Full Test Suite

```
Click: "Parallel Integration Tests" button
Result: Full test suite runs in parallel (~85 seconds)
```

### Scenario 2: Running Specific Test Class

**With parallel profile still active**:

1. **Navigate to test class**: Navigate → Class → Search for test (e.g., `YEngineIT`)
2. **Right-click class name** in editor
3. **Select "Run ClassName"** (or press `Ctrl+Shift+F10`)

**Result**: Single test class runs (might use parallel forks for its tests)

### Scenario 3: Running Specific Test Method

1. **Navigate to test method** in the file
2. **Right-click method name**
3. **Select "Run methodName"** (or press `Ctrl+F5`)

**Result**: Single test method runs

### Scenario 4: Debug Mode (with parallel)

1. **Click dropdown** next to "Parallel Integration Tests" button
2. **Select "Debug Parallel Integration Tests"** (or press `Shift+F9`)

**Result**: Tests run with debugger attached (note: harder to debug with parallel forks)

**Recommendation**: For debugging, switch to sequential:
- **Run** → **Edit Configurations** → Create new config "Sequential Tests" with command: `clean verify` (no profile)
- Then switch between "Parallel Integration Tests" and "Sequential Tests" as needed

---

## Troubleshooting

### Problem 1: Maven Tool Window Doesn't Show

**Symptom**: Can't find Maven tool window

**Solution**:
1. **View** → **Tool Windows** → **Maven**
2. Or: **View** → **Tool Windows** → **Show All**
3. Or: **Search everywhere** (`Cmd+K` on Mac, `Shift+Shift` on Windows) → type "Maven"

---

### Problem 2: "Run Maven Goal" Option Not Visible

**Symptom**: Right-click on yawl-parent doesn't show "Run Maven Goal"

**Solution**:
1. **Make sure you right-click the right object**: Right-click the **blue `yawl-parent` folder** in Maven tool
2. Not the project in Project tool (different icon)
3. If still not visible: IntelliJ → Plugins → Search "Maven" → Make sure Maven plugin is enabled

---

### Problem 3: Configuration Won't Save

**Symptom**: Click OK but configuration disappears

**Solution**:
1. **Check "Name" field**: Must not be empty
2. **Check "Command line"**: Must not be empty
3. **Click Apply first**, then OK
4. **Verify**: Run → Edit Configurations → Should see your config in the list

---

### Problem 4: Tests Timeout

**Symptom**: "Timeout executing command" error

**Solution in IntelliJ**:
1. **Run** → **Edit Configurations** → Select "Parallel Integration Tests"
2. **Scroll down** to find timeout settings (might be in "Other options")
3. **Add to "Command line"**: `-DforkedProcessTimeoutInSeconds=180`
4. **Full command line**: `clean verify -P integration-parallel -DforkedProcessTimeoutInSeconds=180`
5. **Click Apply** → **OK**

---

### Problem 5: OutOfMemory Error

**Symptom**: "OutOfMemoryError: Java heap space"

**Solution**:
1. **Run** → **Edit Configurations** → Select "Parallel Integration Tests"
2. **Find "VM options"** (or scroll in "Command line")
3. **Add JVM options**: `-Xmx2g` (2GB per fork)
4. **Or modify command line**: `clean verify -P integration-parallel -DforkCount=2`
5. **Click Apply** → **OK**

---

### Problem 6: Tests Pass Sequentially, Fail in Parallel

**Symptom**: Test passes with default config, fails with parallel

**Solution**:
1. **Create a sequential run config** to debug:
   - Run → Edit Configurations → New Maven
   - Name: "Sequential Tests (Debug)"
   - Command: `clean verify` (no -P flag)
2. **Run test sequentially** to identify the issue
3. **Common causes**:
   - Test has timing dependency (use `await()` instead of `Thread.sleep()`)
   - Test uses static state (use instance variables)
   - Port conflicts (use random ports in @SpringBootTest)

---

## Advanced Configuration

### Multiple Parallel Configurations

**Create different profiles for different scenarios**:

**Config 1: Maximum Parallelism (for powerful machines)**
```
Name: Parallel Tests (Max)
Command: clean verify -P integration-parallel -DforkCount=4C
```

**Config 2: Conservative (for slower machines)**
```
Name: Parallel Tests (Safe)
Command: clean verify -P integration-parallel -DforkCount=2
```

**Config 3: Sequential (for debugging)**
```
Name: Sequential Tests
Command: clean verify
```

Now you can switch between them in the dropdown.

---

### Custom VM Options

**If you need to tweak JVM memory/performance**:

1. **Run** → **Edit Configurations** → Select your parallel config
2. **Find "VM options"** field (might need to scroll)
3. **Add options** like:
   ```
   -Xmx2g -Xms1g -XX:+UseParallelGC
   ```

---

### Environment Variables

**If you need to set environment variables for tests**:

1. **Run** → **Edit Configurations** → Select your config
2. **Click "Environment variables"** (might be hidden, check "..." button)
3. **Add variable**: `PARALLEL_TESTS=true` (or whatever your test needs)

---

### Maven Command Line Parameters

**Advanced: Pass additional Maven parameters**

In the "Command line" field, you can add:

| Parameter | What It Does |
|-----------|-------------|
| `-DforkCount=2` | Limit to 2 JVM forks |
| `-DargLine="-Xmx1024m"` | Heap per fork |
| `-DforkedProcessTimeoutInSeconds=180` | Timeout (3 min) |
| `-X` | Debug output (verbose) |
| `-o` | Offline (no network) |
| `-Dgroups="unit"` | Run only unit tests |

**Example**:
```
clean verify -P integration-parallel -DforkCount=2 -DforkedProcessTimeoutInSeconds=180
```

---

## Tips and Tricks

### Tip 1: Quick Toggle Between Sequential and Parallel

**Create both configs**, then:
1. **Sequential**: Command: `clean verify`
2. **Parallel**: Command: `clean verify -P integration-parallel`

Switch between them in the dropdown (top-right of IDE).

---

### Tip 2: Run Only Integration Tests

**Create a config**:
```
Name: Integration Tests Only
Command: verify -P integration-parallel
```

(Skip `clean` and skip unit tests by using just `verify`)

---

### Tip 3: Run Specific Module

**To test just one module** (e.g., `yawl-engine`):
```
Name: Parallel Tests - Engine Module
Command: -f yawl-engine/pom.xml clean verify -P integration-parallel
```

---

### Tip 4: Skip Tests in Build

**If you just want to compile**:
```
Name: Compile Only
Command: clean compile
```

---

### Tip 5: Custom Icons for Configurations

1. Right-click the run configuration → Edit
2. Change the **icon** (dropdown in configuration dialog)
3. Use different icons for "Sequential", "Parallel", "Debug" configs

Makes it easier to visually identify which config you're using!

---

## Integration with Code Review

### When Running Tests in PR Review

**Scenario**: You're reviewing a PR and want to verify tests pass

1. **Checkout the branch**: VCS → Git → Checkout
2. **Click "Parallel Integration Tests"** button
3. **Tests run in ~85 seconds** (vs 150s sequential)
4. **Faster PR feedback!**

---

### Continuous Testing (Auto-run on Save)

**IntelliJ can auto-run tests when you save**:

1. **Run** → **Toggle auto-run tests on save**
2. Or: Create a File Watcher (Settings → Tools → File Watchers)

**Note**: Works best with sequential tests (parallel is overkill for auto-run). Set up two configs:
- **Auto-run**: Sequential (fast feedback, frequent)
- **Full build**: Parallel (thorough, when you want full validation)

---

## Keyboard Shortcuts

### Create Custom Keyboard Shortcut

1. **Run** → **Edit Configurations** → Select "Parallel Integration Tests"
2. **Right-click** on the config name
3. **Select "Create Shortcut"**
4. **Assign key combo** (e.g., `Ctrl+Alt+P` for Parallel)
5. Click OK

Now: Press `Ctrl+Alt+P` to run parallel tests anytime!

---

## Integration with IntelliJ Terminal

**Quick approach: Use built-in Terminal**:

1. **View** → **Tool Windows** → **Terminal**
2. **Type**: `mvn clean verify -P integration-parallel`
3. **Press Enter**

Terminal tab shows output just like running from command line.

---

## Performance Tips

### Make IntelliJ Maven Resolution Faster

1. **Settings** → **Build, Execution, Deployment** → **Maven**
2. **JVM for importer**: Increase heap (e.g., `-Xmx1024m`)
3. **Importing...** → Check "Work offline"
4. **Plugins**: Disable unnecessary Maven plugins

---

## Summary: Quick Reference

| Task | How |
|------|-----|
| **Try parallel tests NOW** | Terminal: `mvn clean verify -P integration-parallel` |
| **Create run config** | Run → Edit Config → "+" → Maven → Fill form → OK |
| **Run tests** | Click "Parallel Integration Tests" button |
| **Switch to sequential** | Create another config with `clean verify` (no -P) |
| **Debug mode** | Right-click config → "Debug" |
| **Single test** | Right-click test class → Run |
| **Timeout issues?** | Edit config → Add `-DforkedProcessTimeoutInSeconds=180` |
| **Memory issues?** | Edit config → Add `-DforkCount=2` |

---

## Next Steps

1. **Create run configuration** (2 min) — Use "Method 2" above
2. **Run your first parallel build** (1 min) — Click the button
3. **Note the time** — Should be ~85 seconds
4. **Try sequential** (1 min) — Create second config, compare
5. **Share results** — Post in #yawl-dev Slack

**Total setup time: ~5 minutes. Enjoy 65 seconds saved per build!**

---

## Questions?

- **FAQ**: See `/home/user/yawl/.claude/PHASE5-TEAM-FAQ.md`
- **Quick Reference**: See `/home/user/yawl/.claude/PHASE5-QUICK-START-CHEAT-SHEET.md`
- **Troubleshooting**: See `/home/user/yawl/.claude/PHASE5-TEAM-TRAINING.md` (Section 7)
- **Post in Slack**: #yawl-dev

---

**Ready? Click "Run" and enjoy 1.77× faster tests!**
