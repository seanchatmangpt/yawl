# IDE Setup Guide: VS Code & Command Line — Parallel Integration Tests

**Version**: 1.0
**Date**: February 2026
**Target**: VS Code, Terminal, Command Line
**Maven**: 3.9+
**Java**: 25+

---

## Table of Contents

1. [Quick Start (1 minute)](#quick-start)
2. [VS Code Setup](#vs-code-setup)
3. [Command-Line Shortcuts](#command-line-shortcuts)
4. [Workflow Integration](#workflow-integration)
5. [Troubleshooting](#troubleshooting)
6. [Advanced Tips](#advanced-tips)

---

## Quick Start

### Try It Right Now (30 seconds)

Open a terminal and run:

```bash
cd /home/user/yawl
mvn clean verify -P integration-parallel
```

**Expected output**:
```
[INFO] Building YAWL...
[INFO] Compiling...
[INFO] Running tests in parallel...
[INFO] BUILD SUCCESS
[INFO] Total time: 1 minute 25 seconds
```

**Done!** That's it. Tests ran 1.77× faster.

---

## VS Code Setup

### Method 1: Integrated Terminal (Recommended)

**Open VS Code with your project**:

```bash
code /home/user/yawl
```

**Or in VS Code**:
1. File → Open Folder → Select `/home/user/yawl`

### Method 2: Built-in Maven Extension

**Install Maven extension** (optional, for GUI):

1. **View** → **Extensions** (or `Ctrl+Shift+X`)
2. **Search** for "Maven for Java"
3. **Install** the Microsoft/Redhat Maven extension
4. **Reload VS Code**

Now you can right-click `pom.xml` and run Maven goals.

### Method 3: Tasks (Create Custom Command)

**Create a VS Code Task for quick access**:

1. **Terminal** → **Configure Tasks** (or `Ctrl+Shift+B`)
2. **Select "Create tasks.json"** → **Maven**
3. **Edit the file** (`.vscode/tasks.json`):

```json
{
    "version": "2.0.0",
    "tasks": [
        {
            "label": "Test: Parallel (Fast - 85s)",
            "type": "shell",
            "command": "mvn",
            "args": ["clean", "verify", "-P", "integration-parallel"],
            "group": {
                "kind": "test",
                "isDefault": true
            },
            "presentation": {
                "echo": true,
                "reveal": "always",
                "focus": false,
                "panel": "shared"
            }
        },
        {
            "label": "Test: Sequential (Safe - 150s)",
            "type": "shell",
            "command": "mvn",
            "args": ["clean", "verify"],
            "group": {
                "kind": "test"
            }
        }
    ]
}
```

Now you can:
1. **Terminal** → **Run Task**
2. **Select** "Test: Parallel (Fast - 85s)"
3. **Tests run** in integrated terminal

---

## Command-Line Shortcuts

### macOS/Linux: Shell Aliases

**Add to your shell config** (`~/.bashrc`, `~/.zshrc`, etc.):

```bash
# Quick parallel tests
alias mvnp='mvn clean verify -P integration-parallel'

# Quick sequential tests
alias mvns='mvn clean verify'

# Compile only
alias mvnc='mvn clean compile'

# Run just one test
alias mvnt='mvn test -Dtest='

# Maven with all output
alias mvnx='mvn -X clean verify -P integration-parallel'
```

**Reload shell**:
```bash
source ~/.zshrc    # For zsh
source ~/.bashrc   # For bash
```

**Now use**:
```bash
mvnp              # Runs parallel tests
mvns              # Runs sequential tests
mvnc              # Compiles only
```

### Windows PowerShell: Aliases & Functions

**Edit PowerShell profile**:

```powershell
# Open notepad
notepad $PROFILE

# Or create if doesn't exist
New-Item -Path $PROFILE -Type File -Force
notepad $PROFILE
```

**Add these to your profile**:

```powershell
# Quick parallel tests
Set-Alias mvnp 'mvn clean verify -P integration-parallel'

# Quick sequential tests
Set-Alias mvns 'mvn clean verify'

# Compile only
Set-Alias mvnc 'mvn clean compile'

# Function for running specific test
function mvnt {
    param($testName)
    mvn test -Dtest=$testName
}
```

**Save and reload PowerShell**.

**Now use**:
```powershell
mvnp           # Parallel tests
mvns           # Sequential tests
mvnc           # Compile
mvnt MyTest    # Single test
```

### Windows Command Prompt (CMD): Batch Scripts

**Create a batch file** (e.g., `C:\scripts\mvnp.bat`):

```batch
@echo off
cd /home/user/yawl
mvn clean verify -P integration-parallel
```

Or as a one-liner:

```batch
cd C:\path\to\yawl && mvn clean verify -P integration-parallel
```

---

## Workflow Integration

### Workflow 1: Development Loop

```bash
# 1. Make code changes
# 2. Save file (auto-save or Ctrl+S)
# 3. Run tests
mvnp

# 4. If tests pass, commit
git add .
git commit -m "Feature: ..."

# 5. If tests fail, fix and go to step 2
```

### Workflow 2: Before Pushing

```bash
# Full validation before pushing
mvnp

# Check if it compiled
[ $? -eq 0 ] && echo "✅ Tests passed!" || echo "❌ Tests failed"

# If passed, push
git push origin main
```

### Workflow 3: Code Review

```bash
# Reviewer: Checkout PR branch
git checkout feature-branch

# Run tests on that branch
mvnp

# If tests pass → approve PR
# If tests fail → request changes
```

### Workflow 4: CI/CD Pipeline (GitHub Actions Example)

**.github/workflows/test.yml**:

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up Java
        uses: actions/setup-java@v3
        with:
          java-version: '25'
          distribution: 'temurin'

      - name: Run parallel tests
        run: mvn clean verify -P integration-parallel
```

---

## Troubleshooting

### Problem 1: "mvnp: command not found"

**Cause**: Alias not loaded

**Solution**:
1. **Verify alias exists**:
   ```bash
   alias | grep mvnp
   ```

2. **If not found**, add it:
   ```bash
   # For zsh
   echo "alias mvnp='mvn clean verify -P integration-parallel'" >> ~/.zshrc
   source ~/.zshrc
   ```

3. **Or try full command**:
   ```bash
   mvn clean verify -P integration-parallel
   ```

---

### Problem 2: "mvn: command not found"

**Cause**: Maven not in PATH

**Solution**:
1. **Check if Maven installed**:
   ```bash
   which mvn
   ```

2. **If not found, install Maven**:
   ```bash
   # macOS (Homebrew)
   brew install maven

   # Linux (apt)
   sudo apt-get install maven

   # Windows: Download from https://maven.apache.org/download.cgi
   ```

3. **Add to PATH** (if needed):
   ```bash
   # macOS/Linux
   export PATH="/usr/local/maven/bin:$PATH"
   echo 'export PATH="/usr/local/maven/bin:$PATH"' >> ~/.zshrc
   ```

---

### Problem 3: Tests Timeout

**Symptom**:
```
[ERROR] Timeout executing command
```

**Solution**:
```bash
mvn verify -P integration-parallel -DforkedProcessTimeoutInSeconds=180
```

Or create an alias:
```bash
alias mvnpx='mvn verify -P integration-parallel -DforkedProcessTimeoutInSeconds=180'
```

---

### Problem 4: OutOfMemory Error

**Symptom**:
```
[ERROR] OutOfMemoryError: Java heap space
```

**Solution**:
```bash
mvn verify -P integration-parallel -DforkCount=2
```

Or export Java options:
```bash
export MAVEN_OPTS="-Xmx2g"
mvnp
```

---

### Problem 5: Tests Pass Sequentially, Fail in Parallel

**Cause**: Test has race condition or hidden dependency

**Solution**:
```bash
# Run sequential to debug
mvns

# Or run specific test
mvn test -Dtest=FailingTest
```

---

## Advanced Tips

### Tip 1: Create Custom Maven Wrapper Script

**Create `./mvnp` in your project root**:

```bash
#!/bin/bash
# Fast parallel tests script
mvn clean verify -P integration-parallel "$@"
```

**Make executable**:
```bash
chmod +x mvnp
```

**Use**:
```bash
./mvnp              # Run parallel tests
./mvnp -X           # Parallel + debug output
./mvnp -DforkCount=1  # Parallel + fewer forks
```

---

### Tip 2: Conditional Shell Script

**Advanced workflow script** (`test-smart.sh`):

```bash
#!/bin/bash

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Running tests...${NC}"

# Run parallel tests
mvn clean verify -P integration-parallel

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ All tests passed!${NC}"
    echo "Ready to commit."
    exit 0
else
    echo -e "${RED}❌ Tests failed!${NC}"
    echo "Fix issues and retry."
    exit 1
fi
```

**Use**:
```bash
chmod +x test-smart.sh
./test-smart.sh
```

---

### Tip 3: Pre-commit Hook

**Prevent committing broken code** (`.git/hooks/pre-commit`):

```bash
#!/bin/bash

echo "Running tests before commit..."
mvn clean verify -P integration-parallel

if [ $? -ne 0 ]; then
    echo "Tests failed! Commit aborted."
    exit 1
fi

echo "Tests passed! Proceeding with commit."
exit 0
```

**Install**:
```bash
cp pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

Now tests run automatically before every commit!

---

### Tip 4: Performance Profiling

**Compare sequential vs parallel**:

```bash
#!/bin/bash

echo "Sequential build..."
time mvn clean verify

echo ""
echo "Parallel build..."
time mvn clean verify -P integration-parallel

echo ""
echo "✅ Comparison complete"
```

---

### Tip 5: Docker-based Development

**Run tests in Docker** (if you prefer isolation):

```dockerfile
FROM maven:3.9-eclipse-temurin-25

WORKDIR /workspace
COPY . .

CMD ["mvn", "clean", "verify", "-P", "integration-parallel"]
```

**Build and run**:
```bash
docker build -t yawl-tests .
docker run --rm yawl-tests
```

---

## VS Code Extensions for Maven

### Recommended Extensions

1. **Maven for Java** (Microsoft/Redhat)
   - Right-click `pom.xml` → Run Maven Goal
   - Right-click module → Favorite Maven Goals

2. **Project Manager**
   - Save your YAWL project as favorite
   - Quick switch between projects

3. **GitLens**
   - Track which commits broke tests
   - Blame view on test failures

4. **Test Explorer UI**
   - Visual test runner interface
   - Click to run/debug tests

---

## CI/CD Integration Examples

### GitHub Actions

```yaml
name: Tests (Parallel)

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '25'
      - run: mvn clean verify -P integration-parallel
```

### Jenkins

```groovy
pipeline {
    agent any

    stages {
        stage('Test') {
            steps {
                sh 'mvn clean verify -P integration-parallel'
            }
        }
    }
}
```

### GitLab CI

```yaml
test:
  stage: test
  script:
    - mvn clean verify -P integration-parallel
  artifacts:
    reports:
      junit: target/failsafe-reports/*.xml
```

---

## Performance Tips

### Tip 1: Use Maven Daemon (mvnd)

**Faster Maven startup** with mvnd (Maven Daemon):

```bash
# Install (macOS)
brew install mvnctl

# Or download: https://maven.apache.org/mvnd/

# Use instead of mvn
mvnd clean verify -P integration-parallel  # Faster!
```

### Tip 2: Offline Mode

**Speed up builds when you don't need latest deps**:

```bash
mvn clean verify -P integration-parallel -o
```

### Tip 3: Skip Tests in Compile-Only Builds

```bash
mvn clean compile -DskipTests
```

### Tip 4: Parallel Maven Download

```bash
mvn clean verify -P integration-parallel -T 1C
```

---

## Keyboard Shortcuts for Terminal

### macOS/Linux

| Shortcut | What it does |
|----------|------------|
| `Ctrl+C` | Stop running tests |
| `Ctrl+L` | Clear terminal |
| `Ctrl+R` | Search command history |
| `!!` | Repeat last command |
| `mvnp` | Run parallel tests (if alias set) |

### Windows PowerShell

| Shortcut | What it does |
|----------|------------|
| `Ctrl+C` | Stop running tests |
| `Clear` | Clear terminal |
| `Ctrl+R` | Search history |
| `mvnp` | Run parallel tests (if alias set) |

---

## Quick Reference Card

```bash
# Basic commands
mvn clean verify -P integration-parallel          # Fast parallel tests (85s)
mvn clean verify                                  # Safe sequential tests (150s)
mvn clean compile                                 # Just compile
mvn test -Dtest=MyTest                           # Single test
mvn test -Dtest=MyTest#myMethod                  # Single method

# With options
mvn verify -P integration-parallel -DforkCount=2  # Fewer forks
mvn verify -P integration-parallel -Xmx2g         # More memory
mvn verify -P integration-parallel -X             # Debug output

# Aliases (if set up)
mvnp                    # Parallel tests
mvns                    # Sequential tests
mvnc                    # Compile only
```

---

## Comparison: Terminal vs IDE

| Feature | Terminal | VS Code |
|---------|----------|---------|
| Setup time | 1 min (just type command) | 5 min (config tasks) |
| Output quality | Excellent | Excellent |
| Click to run | No | Yes (if task configured) |
| Edit & run | Good | Excellent |
| Debugging | Via Maven args | Hard with parallel |
| CI/CD ready | Yes | Yes |
| Learning curve | Easy | Easy |

**Recommendation**: Use Terminal for simplicity, VS Code Tasks for integration.

---

## Summary

### One-time Setup (2 minutes)

```bash
# macOS/Linux: Add to ~/.zshrc or ~/.bashrc
echo "alias mvnp='mvn clean verify -P integration-parallel'" >> ~/.zshrc
source ~/.zshrc

# Windows PowerShell: Edit $PROFILE
Set-Alias mvnp 'mvn clean verify -P integration-parallel'
```

### Regular Use (30 seconds)

```bash
cd /home/user/yawl
mvnp        # Run parallel tests
```

### Expected Result

```
Tests run in ~85 seconds (1.77x faster than 150s)
All tests pass
Build SUCCESS
```

---

## Next Steps

1. **Right now**: Run `mvn clean verify -P integration-parallel`
2. **Soon**: Add alias to shell config (saves 5 seconds per invocation)
3. **Later**: Integrate into CI/CD pipeline (saves minutes per PR)

---

## Questions?

- **FAQ**: See `/home/user/yawl/.claude/PHASE5-TEAM-FAQ.md`
- **Training**: See `/home/user/yawl/.claude/PHASE5-TEAM-TRAINING.md`
- **Quick reference**: See `/home/user/yawl/.claude/PHASE5-QUICK-START-CHEAT-SHEET.md`

---

**Ready? Run `mvnp` and enjoy 1.77× faster tests!**
