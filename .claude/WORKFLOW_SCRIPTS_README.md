# YAWL Developer Workflow Scripts

Two powerful scripts for streamlined development workflow.

## Scripts Overview

### 1. dev-workflow.sh - Task Automation

Convenient wrapper for common development tasks with timing, error handling, and clear output.

**Location**: `.claude/dev-workflow.sh`

### 2. watch-and-test.sh - File Watcher

Automatic test runner that monitors file changes and provides instant feedback.

**Location**: `.claude/watch-and-test.sh`

## Quick Start

```bash
# Make scripts executable (already done)
chmod +x .claude/dev-workflow.sh .claude/watch-and-test.sh

# Quick compile + test
.claude/dev-workflow.sh quick

# Watch files and auto-test
.claude/watch-and-test.sh --all
```

## dev-workflow.sh Commands

### Quick Feedback Commands

```bash
# Fast compile + unit tests (30-60s)
.claude/dev-workflow.sh quick

# Compile only, no tests (20-30s)
.claude/dev-workflow.sh compile

# Run all unit tests (60-90s)
.claude/dev-workflow.sh test
```

### Module-Specific Commands

```bash
# Test specific module (10-30s)
.claude/dev-workflow.sh module yawl-engine
.claude/dev-workflow.sh module yawl-stateless
.claude/dev-workflow.sh module yawl-integration
```

### Full Build Commands

```bash
# Full verification with integration tests (2-5 min)
.claude/dev-workflow.sh verify

# Complete build pipeline (5-10 min)
.claude/dev-workflow.sh full

# Clean all build artifacts
.claude/dev-workflow.sh clean
```

### Legacy Ant Commands

```bash
# Ant compile (18s)
.claude/dev-workflow.sh ant-compile

# Ant compile + test (45-60s)
.claude/dev-workflow.sh ant-test
```

### Utility Commands

```bash
# Show git status and build health
.claude/dev-workflow.sh status

# Start file watcher
.claude/dev-workflow.sh watch

# Show help
.claude/dev-workflow.sh help
```

## watch-and-test.sh Usage

### Basic Usage

```bash
# Watch entire project with tests
.claude/watch-and-test.sh --all

# Watch and compile only (fast feedback)
.claude/watch-and-test.sh --compile-only

# Watch specific module
.claude/watch-and-test.sh yawl-engine

# Use Ant instead of Maven
.claude/watch-and-test.sh --ant
```

### Features

- Monitors `src/` and `test/` directories for changes
- 2-second debounce (batches rapid changes)
- Color-coded output (green=pass, red=fail, yellow=running)
- Re-runs tests automatically when files change
- Graceful Ctrl+C handling
- Falls back to polling if inotify-tools not available

### Output Examples

**Success**:
```
✓ BUILD PASSED in 12s
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
```

**Failure**:
```
✗ BUILD FAILED in 8s
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ERROR: Compilation failed
⚠ Fix errors and save to re-run
```

## Recommended Workflows

### Daily Development Cycle

```bash
# 1. Start watching in one terminal
.claude/watch-and-test.sh yawl-engine

# 2. Make changes in editor
# 3. Save file
# 4. Watch auto-runs tests
# 5. Fix failures and save again
```

### Pre-Commit Validation

```bash
# Quick check
.claude/dev-workflow.sh quick

# Full validation
.claude/dev-workflow.sh full

# Clean slate
.claude/dev-workflow.sh clean
.claude/dev-workflow.sh full
```

### Module Development

```bash
# Focus on one module
.claude/watch-and-test.sh yawl-integration

# Or manually test module
.claude/dev-workflow.sh module yawl-integration
```

### Fast Iteration

```bash
# Compile-only watching (fastest)
.claude/watch-and-test.sh --compile-only

# Or manual compile
.claude/dev-workflow.sh compile
```

## Implementation Details

### dev-workflow.sh

**Technology**: Bash script with Maven/Ant integration

**Features**:
- Colored output (green/red/yellow/blue)
- Build timing for all commands
- Error handling with clear messages
- Module name validation
- Parallel Maven builds supported

**Error Handling**:
- Validates Maven/Ant availability
- Checks module names against valid list
- Reports build failures with duration
- Propagates exit codes correctly

### watch-and-test.sh

**Technology**: Bash with inotifywait or polling fallback

**File Monitoring**:
- **Preferred**: `inotifywait` (inotify-tools package)
- **Fallback**: Polling with MD5 checksums

**Debouncing**:
- 2-second delay after last file change
- Prevents build storms from rapid saves
- Drains event queue before building

**Exclusions**:
- `.git/` directories
- `target/` build directories
- `.class` compiled files
- `.swp` editor temporary files
- `~` backup files

## Performance Comparison

| Command | Time | Use Case |
|---------|------|----------|
| `dev-workflow.sh compile` | 20-30s | Syntax check |
| `dev-workflow.sh quick` | 30-60s | Fast feedback |
| `dev-workflow.sh module yawl-engine` | 10-30s | Single module |
| `dev-workflow.sh test` | 60-90s | All unit tests |
| `dev-workflow.sh verify` | 2-5 min | Full validation |
| `dev-workflow.sh full` | 5-10 min | Complete build |
| `dev-workflow.sh ant-compile` | 18s | Legacy fast compile |

## Troubleshooting

### inotify-tools not found

The watcher falls back to polling automatically. For better performance:

```bash
# Debian/Ubuntu
apt-get install inotify-tools

# RHEL/CentOS
yum install inotify-tools

# macOS (uses fswatch instead)
brew install fswatch
```

### Build fails in watch mode

1. Check the error output
2. Fix the issue
3. Save any file to trigger re-run
4. No need to restart watcher

### Colors not showing

If you see ANSI codes like `\033[0;32m`, your terminal doesn't support colors. The scripts still work, just without colors.

### Module not found

Run `dev-workflow.sh status` to see valid module names. Must use exact names like `yawl-engine`, not `engine`.

## Integration with IDE

### VS Code

Add to `.vscode/tasks.json`:

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "YAWL Quick Build",
      "type": "shell",
      "command": ".claude/dev-workflow.sh quick",
      "group": {
        "kind": "build",
        "isDefault": true
      }
    },
    {
      "label": "YAWL Watch",
      "type": "shell",
      "command": ".claude/watch-and-test.sh --all",
      "isBackground": true
    }
  ]
}
```

### IntelliJ IDEA

1. Open Run/Debug Configurations
2. Add new Shell Script configuration
3. Script path: `.claude/dev-workflow.sh`
4. Script options: `quick`

## Testing the Scripts

### Verify Installation

```bash
# Check scripts are executable
ls -la .claude/*.sh

# Test help output
.claude/dev-workflow.sh help
.claude/watch-and-test.sh --help

# Test status
.claude/dev-workflow.sh status
```

### Run Sample Build

```bash
# Quick compile test
.claude/dev-workflow.sh compile

# Expected output:
# === YAWL Developer Workflow - HH:MM:SS
# === Compile Only (No Tests)
# → Running: mvn clean compile -DskipTests=true
# ✓ Maven build completed in XXs
# ✓ Workflow completed at HH:MM:SS
```

## Architecture

Both scripts follow these principles:

1. **No mocks/stubs**: Real Maven/Ant commands only
2. **Clear error messages**: No silent failures
3. **Proper exit codes**: Failures propagate correctly
4. **Defensive programming**: Check dependencies before use
5. **User-friendly output**: Colors, timing, clear status

## Future Enhancements

Possible improvements (not yet implemented):

- Docker integration for isolated builds
- Notification support (desktop alerts)
- CI/CD integration hooks
- Performance profiling mode
- Incremental build detection
- Test failure history tracking

## Support

For issues or questions:

1. Check this README
2. Run command with `help` flag
3. Check `.claude/CLAUDE.md` for project standards
4. Review build logs in terminal output

## License

Part of YAWL v6.0.0 - see main LICENSE for details.
