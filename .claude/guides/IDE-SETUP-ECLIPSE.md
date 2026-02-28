# IDE Setup Guide: Eclipse — Parallel Integration Tests

**Version**: 1.0
**Date**: February 2026
**Eclipse Version**: 2024-03 or later (with Maven support)
**Maven**: 3.9+
**Java**: 25+

---

## Table of Contents

1. [Quick Start (1 minute)](#quick-start)
2. [Method 1: Maven Tool (5 minutes)](#method-1-maven-tool)
3. [Method 2: External Tools Configuration (10 minutes)](#method-2-external-tools)
4. [Method 3: Command Line in Eclipse (2 minutes)](#method-3-command-line)
5. [Troubleshooting](#troubleshooting)
6. [Advanced Configuration](#advanced-configuration)

---

## Quick Start

### Try It Right Now in Terminal

Open Eclipse's integrated terminal and run:

```bash
cd /path/to/yawl
mvn clean verify -P integration-parallel
```

**That's it!** Tests complete in ~85 seconds.

---

## Method 1: Maven Tool

**Best for: Quick access to Maven goals**

### Step 1: Open Maven Tool

1. **Window** → **Show View** → **Other**
2. **Search** for "Maven"
3. **Select** "Maven Repositories" or "Maven Build Configurations"
4. **Click Open**

(Or try: Window → Show View → Maven → Maven Repositories)

### Step 2: Right-Click on Project

1. **In Maven view**, right-click on `yawl-parent` (the root pom.xml)
2. **Select "Run Maven Goal"**

### Step 3: Enter Maven Command

```
clean verify -P integration-parallel
```

### Step 4: Run

**Click Run** (or press Enter)

**Result**: Tests execute in the Eclipse console (85 seconds)

---

## Method 2: External Tools Configuration

**Best for: Creating persistent buttons in Eclipse UI**

### Step 1: Open External Tools Configuration

1. **Run** → **External Tools** → **External Tools Configurations...**
   (Or: Toolbar → down arrow next to External Tools button → Organize Favorites...)

### Step 2: Create New Configuration

1. **Click "New" button** (or green "+" icon)
2. **Select "Program"** (not "Ant" or "Maven Builder")

### Step 3: Fill in Configuration

| Field | Value |
|-------|-------|
| **Name** | `Parallel Tests (Parallel)` |
| **Location** | `${system_path:mvn}` (or full path: `/usr/local/bin/mvn`) |
| **Working Directory** | `${project_loc}` |
| **Arguments** | `clean verify -P integration-parallel` |

### Step 4: Configure I/O

1. **Click "I/O" tab**
2. ✓ **Check "Capture output"** (see test output in Eclipse console)
3. ✓ **Check "Show in Console"** (display in build console)

### Step 5: Configure Environment

1. **Click "Environment" tab** (optional)
2. **Add any JVM options** (if needed):
   - Variable: `MAVEN_OPTS`
   - Value: `-Xmx2g` (2GB heap per fork)

### Step 6: Save Configuration

1. **Click "Apply"** then **"Run"** (or just "Close" to save for later)

### Step 7: Add to Toolbar (Optional)

1. **Window** → **Customize Perspective...**
2. **Click "Tool Bar Visibility"** tab
3. **Find "Run" section**, check "External Tools"
4. **Click OK**

Now "Parallel Tests" button appears in toolbar!

---

## Method 3: Command Line in Eclipse

**Best for: Quick testing without configuration**

### Step 1: Open Terminal in Eclipse

1. **Window** → **Show View** → **Terminal**
   (Or: Window → Show View → Other → Search "Terminal")

### Step 2: Navigate to Project

```bash
cd /path/to/yawl
```

### Step 3: Run Tests

```bash
mvn clean verify -P integration-parallel
```

**Result**: Tests run in Eclipse's integrated terminal (~85 seconds)

---

## Using Parallel Tests

### Once You've Set Up (Any Method)

**To run parallel tests**:
1. Right-click project (Method 1) OR
2. Click External Tools button (Method 2) OR
3. Type command in terminal (Method 3)

**Result**: Tests run in ~85 seconds (vs 150s sequential)

---

## Creating Multiple Configurations

**Set up both parallel and sequential**:

### Sequential Configuration

1. **Run** → **External Tools** → **External Tools Configurations...**
2. **Click "New"** → **"Program"**
3. **Fill in**:
   - Name: `Tests (Sequential - Safe)`
   - Location: `${system_path:mvn}`
   - Arguments: `clean verify`
4. **Click "Apply"** → **"Close"**

### Parallel Configuration (as above)

Now you have two external tools:
- **Parallel Tests** (85 seconds)
- **Sequential Tests** (150 seconds)

**Use whichever you need!**

---

## Troubleshooting

### Problem 1: Maven Not Found

**Error**: `mvn: command not found`

**Solution**:

1. **Find Maven location**:
   ```bash
   which mvn
   ```

2. **If not found, install Maven**:
   ```bash
   # macOS
   brew install maven

   # Linux
   sudo apt-get install maven
   ```

3. **In Eclipse configuration**, use full path:
   ```
   /usr/local/maven/bin/mvn
   ```
   (instead of `${system_path:mvn}`)

---

### Problem 2: Tests Timeout

**Error**: `Timeout executing command`

**Solution**:

Update External Tools configuration:

1. **Run** → **External Tools** → **External Tools Configurations...**
2. **Select** "Parallel Tests"
3. **Update Arguments**:
   ```
   clean verify -P integration-parallel -DforkedProcessTimeoutInSeconds=180
   ```
4. **Click "Apply"**

---

### Problem 3: OutOfMemory Error

**Error**: `OutOfMemoryError: Java heap space`

**Solution 1: Reduce fork count**

1. Update External Tools Arguments:
   ```
   clean verify -P integration-parallel -DforkCount=2
   ```

**Solution 2: Increase heap**

1. **Run** → **External Tools** → **External Tools Configurations...**
2. **Click "Environment" tab**
3. **New variable**:
   - Name: `MAVEN_OPTS`
   - Value: `-Xmx2g`
4. **Click "Apply"**

---

### Problem 4: Tests Pass Sequential, Fail Parallel

**Cause**: Test has race condition

**Solution**:
1. Create sequential configuration (see above)
2. Run tests sequentially to debug
3. Fix test and retry parallel

---

### Problem 5: Output Not Showing

**Symptom**: Run external tool but no output in console

**Solution**:

1. **Run** → **External Tools** → **External Tools Configurations...**
2. **Select your configuration**
3. **Click "I/O" tab**
4. ✓ **Check "Capture output"**
5. ✓ **Check "Show in Console"**
6. **Click "Apply"**

---

## Advanced Configuration

### Advanced: Custom Maven Arguments

**For different scenarios**:

**Config 1: Maximum Parallelism**
```
Arguments: clean verify -P integration-parallel -DforkCount=4C
```

**Config 2: Conservative (Safe)**
```
Arguments: clean verify -P integration-parallel -DforkCount=2
```

**Config 3: Debug Output**
```
Arguments: clean verify -P integration-parallel -X
```

Create separate External Tools configurations for each scenario.

---

### Advanced: Linking to Errors

**Make test failures clickable**:

1. **Run** → **External Tools** → **External Tools Configurations...**
2. **Click "Console Output Parsers"**
3. **Add Java Error Parser** (usually enabled by default)

Now when tests fail, stack traces become clickable links to source files!

---

## IDE Integration Tips

### Tip 1: Keyboard Shortcut

**Create keyboard shortcut for External Tools**:

1. **Window** → **Preferences** → **Keys** (search for "Keys")
2. **Search** for "Run" in the keys list
3. **Find "Run External Tools..."**
4. **Bind to key** (e.g., `Ctrl+Alt+P` for Parallel)
5. **Click "Apply"**

Now press `Ctrl+Alt+P` to run parallel tests!

---

### Tip 2: Launch Configuration Groups

**Run multiple tools in sequence**:

1. **Run** → **Run Configurations...**
2. **New "Launch Group"**
3. **Add both** "Parallel Tests" and "Code Analysis"
4. Click to run both together

---

### Tip 3: Organize in Dropdown Menu

**If you create many External Tools**:

1. **Window** → **Customize Perspective...**
2. **Click "Tool Bar Visibility"**
3. **Organize tools into groups**
4. **Make favorites visible** in the toolbar

---

## Maven Plugin Configuration

### Alternative: Maven Plugin in Eclipse

**If you prefer Eclipse's native Maven integration**:

1. **Right-click project** → **Run As** → **Run Configurations**
2. **Double-click "Maven Build"**
3. **Fill in**:
   - Name: `Parallel Tests`
   - Base Directory: `${project_loc}`
   - Goals: `clean verify -P integration-parallel`
4. **Click "Run"**

---

## Integration with Source Control

### Using Parallel Tests in Git Workflow

**Before committing**:
```bash
# Run parallel tests
mvn clean verify -P integration-parallel

# If tests pass
git add .
git commit -m "Feature: ..."
git push
```

**In Eclipse**: Use Team → Synchronize or Git Staging view alongside External Tools.

---

## Performance Comparison

**In Eclipse console, you'll see**:

**Sequential**:
```
[INFO] Running TestA...  [6s]
[INFO] Running TestB...  [6s]
[INFO] Running TestC...  [6s]
[INFO] Total time: 2 minutes 30 seconds
```

**Parallel**:
```
[INFO] Fork 1: Running TestA, TestC... [6s]
[INFO] Fork 2: Running TestB... [6s]
[INFO] Total time: 1 minute 25 seconds
```

---

## Debugging with Parallel Tests

### Switch to Sequential for Debugging

**For detailed debugging**:

1. **Create Sequential configuration** (see Method 2)
2. **Switch to Sequential** for debugging (easier to follow)
3. **Use Parallel** for regular testing (faster)

### Debug Single Test

1. **Right-click test method** → **Debug As** → **JUnit Test**
2. (This always runs in sequential mode within Eclipse)

---

## Clean Build

**If you need a complete rebuild**:

1. **Project** → **Clean...**
2. **Select "Clean all projects"**
3. **Click OK**
4. **Then run** Parallel Tests

---

## View Test Results

**After tests complete**:

1. **Window** → **Show View** → **Other** → **JUnit**
2. **View** → **Show Test Results**

Test results appear in a tree view (clickable, navigable).

---

## Preferences for Maven

### Configure Eclipse Maven Settings

1. **Window** → **Preferences** → **Maven**
2. **Set JRE**: Java 25+
3. **Set Offline if needed** (for offline development)
4. **Check "Download sources"** (optional, for Javadoc)

---

## Quick Reference

| Task | How |
|------|-----|
| **Run parallel tests NOW** | Terminal: `mvn clean verify -P integration-parallel` |
| **Create External Tool** | Run → External Tools → New Program → Fill form |
| **Sequential tests** | External Tool with: `clean verify` (no -P) |
| **Single test** | Right-click test → Run As → JUnit Test |
| **Debug test** | Right-click test → Debug As → JUnit Test |
| **Compare times** | Have two configs: Parallel + Sequential |
| **Timeout issues** | Add: `-DforkedProcessTimeoutInSeconds=180` |
| **Memory issues** | Add: `-DforkCount=2` |

---

## Step-by-Step Summary

### Quickest Setup (2 minutes)

1. **Window** → **Show View** → **Terminal**
2. Type: `mvn clean verify -P integration-parallel`
3. **Done!**

### Recommended Setup (5 minutes)

1. **Run** → **External Tools** → **External Tools Configurations...**
2. **New Program**
   - Name: `Parallel Tests`
   - Location: `${system_path:mvn}`
   - Arguments: `clean verify -P integration-parallel`
3. **I/O tab** → Check "Capture output"
4. **Apply** → **Run**
5. **Done!** Now you have a button in the toolbar

### Full Setup (10 minutes)

1. Create two External Tool configurations:
   - Parallel: `clean verify -P integration-parallel`
   - Sequential: `clean verify`
2. Add to toolbar (Window → Customize Perspective)
3. Create keyboard shortcuts (Window → Preferences → Keys)
4. **Done!** Professional setup complete

---

## Comparison with Other IDEs

| Feature | Eclipse | IntelliJ | VS Code |
|---------|---------|----------|---------|
| **Setup difficulty** | Medium | Easy | Easy |
| **Configuration method** | External Tools | Run Config | Tasks |
| **Ease of switching** | Good | Good | Good |
| **Output visibility** | Good | Good | Good |
| **Learning curve** | Medium | Easy | Easy |

---

## Tips for Success

1. **Start with Terminal** (fastest, simplest)
2. **Graduate to External Tools** (more integrated)
3. **Create both Parallel + Sequential** (flexibility)
4. **Use keyboard shortcuts** (speed)
5. **Monitor in console** (verify it's working)

---

## Still Having Issues?

### Check Maven Installation

```bash
mvn --version
# Should show Maven 3.9+ and Java 25+
```

### Check Java Installation

```bash
java -version
# Should show Java 25
```

### Run from Eclipse Terminal (Troubleshooting)

```bash
# In Eclipse Terminal, navigate to project
cd /path/to/yawl

# Try the command directly
mvn clean verify -P integration-parallel

# If it works here, then the issue is Eclipse configuration
# If it fails, the issue is Maven/Java installation
```

---

## Next Steps

1. **Try Terminal method RIGHT NOW** (1 min): Window → Show View → Terminal → type command
2. **Create External Tool configuration** (5 min): Run → External Tools → Setup
3. **Add to toolbar** (2 min): Window → Customize Perspective
4. **Create keyboard shortcut** (2 min): Window → Preferences → Keys

**Total: 10 minutes to full integration**

---

## FAQ for Eclipse Users

**Q: Can I run a single test with parallelization?**
A: Yes, right-click the test → Run As → JUnit Test. It'll use parallel mode.

**Q: How do I see detailed test output?**
A: Window → Show View → Console (shows Maven output). Or: Window → Show View → JUnit (shows test tree).

**Q: Can I use keyboard shortcut to run tests?**
A: Yes! Window → Preferences → Keys → Search "External" → Bind shortcut.

**Q: What if tests fail?**
A: Click failing test in JUnit view to jump to source code.

---

## Resources

- **Training**: `/home/user/yawl/.claude/PHASE5-TEAM-TRAINING.md`
- **FAQ**: `/home/user/yawl/.claude/PHASE5-TEAM-FAQ.md`
- **Quick Reference**: `/home/user/yawl/.claude/PHASE5-QUICK-START-CHEAT-SHEET.md`
- **Hands-On Lab**: `/home/user/yawl/.claude/PHASE5-HANDS-ON-LAB.md`

---

**Ready? Open Terminal and run `mvn clean verify -P integration-parallel` to see 1.77× faster tests!**
