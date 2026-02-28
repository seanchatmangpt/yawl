# YAWL v6.0.0 DX.SH Upgrade Guide

## Overview

YAWL v6.0.0 introduces **mandatory validation gates** into the fast build-test loop, implementing the GODSPEED flow:

```
Λ (Build: compile + test) → H (Guards: no deferred work) → Q (Invariants: real impl ∨ throw) → Ω (Git)
```

This ensures Fortune 5 production quality by catching 7 forbidden patterns **before** code is committed.

**Good news**: Existing scripts and CI pipelines are unaffected. The validation gates only run on full builds (`dx.sh all`), not incremental builds.

---

## What Changed

### New Validation Phases

| Phase | Name | Purpose | Exit Code | Speed |
|-------|------|---------|-----------|-------|
| **H** | Guards | Catch deferred work, mocks, stubs, lies | 0=GREEN, 2=RED | ~2-3s per 100 files |
| **Q** | Invariants | Ensure real implementations or exceptions | 0=GREEN, 2=RED | ~3-4s per 100 files |

### New CLI Parameters

```bash
bash scripts/dx.sh all              # NEW: compile + test ALL + validate (H + Q)
bash scripts/dx.sh --skip-validate  # Bypass H+Q phases (backward compat)
bash scripts/dx.sh --validate-only  # Run only H+Q phases (debugging)
```

### New Output Files

Validation results are captured in JSON receipts for CI integration:

```
.claude/receipts/
├── guard-receipt.json        # H phase results (violations, summary)
└── invariant-receipt.json    # Q phase results (violations, summary)
```

---

## Backward Compatibility

**Everything works as before:**

```bash
# Existing command — UNCHANGED
bash scripts/dx.sh                  # compile + test changed modules (no validation)

# Existing with modules — UNCHANGED
bash scripts/dx.sh -pl yawl-engine  # compile + test specific module (no validation)

# Existing test tier — UNCHANGED
bash scripts/dx.sh test --fail-fast-tier 1  # test with fail-fast (no validation)

# New behavior — includes validation
bash scripts/dx.sh all                       # Full build + test + validate (NEW)
```

### Migration Guide: Existing CI Pipelines

If your existing CI pipeline breaks on the new validation gates, you have two options:

**Option A: Skip validation (minimal change)**
```bash
# In your GitHub Actions / Jenkins pipeline:
bash scripts/dx.sh all --skip-validate
```

**Option B: Fix violations instead (recommended)**
- Read the receipt files (`.claude/receipts/*.json`) to identify issues
- Follow the "Fixing Violations" sections below
- Re-run `bash scripts/dx.sh all` to validate the fixes

---

## New Features

### Phase Resumption

If a build fails partway through, resume from a specific phase:

```bash
# Build failed at Q phase? Resume there (skip compile + test)
bash scripts/dx.sh --resume-from invariants

# Continue from H phase
bash scripts/dx.sh --resume-from guards
```

### Validation Reporting

All validation results are now JSON-based for easy CI integration:

```bash
# Extract violations from receipt
jq '.violations[] | {pattern, file, line, content}' \
  .claude/receipts/guard-receipt.json

# Check overall status
jq '.status' .claude/receipts/guard-receipt.json  # "GREEN" or "RED"

# Get summary statistics
jq '.summary' .claude/receipts/guard-receipt.json
```

---

## Fixing Guard Violations (H Phase)

The **H (Guards) phase** detects 7 forbidden patterns. Each has a concrete fix:

### H_TODO: Deferred Work Markers

**Pattern**: Comments like `// TODO`, `// FIXME`, `// XXX`, `// HACK`, `@incomplete`, `@stub`

**Bad Code**:
```java
public class WorkflowEngine {
    // TODO: Add deadlock detection
    public void executeTask(YTask task) {
        task.execute();
    }
}
```

**Fix**: Implement the real logic or throw `UnsupportedOperationException`:
```java
public class WorkflowEngine {
    public void executeTask(YTask task) {
        if (task.isDeadlocked()) {
            throw new UnsupportedOperationException(
                "Deadlock detection not implemented. See IMPLEMENTATION_GUIDE.md"
            );
        }
        task.execute();
    }
}
```

---

### H_MOCK: Mock Implementations

**Pattern**: Class or method names containing `mock`, `stub`, `fake`, `demo` (case-insensitive start)

**Bad Code**:
```java
public class MockDataService implements DataService {
    @Override
    public String fetchData() {
        return "mock data";
    }
}
```

**Fix**: Delete the mock or implement real service:
```java
public class ProductionDataService implements DataService {
    private final RestTemplate httpClient;
    
    @Override
    public String fetchData() {
        var response = httpClient.getForObject("/api/data", String.class);
        if (response == null) {
            throw new UnsupportedOperationException(
                "Data service unavailable. See DEPLOYMENT.md"
            );
        }
        return response;
    }
}
```

---

### H_STUB: Empty Returns

**Pattern**: Methods returning empty values instead of real data or exceptions

**Bad Code**:
```java
public class TaskQueue {
    public String getNextTask() {
        return "";  // STUB: returns empty string
    }
}
```

**Fix**: Implement real logic or throw:
```java
public class TaskQueue {
    public String getNextTask() {
        var task = this.queue.poll();
        if (task == null) {
            throw new NoSuchElementException(
                "Task queue is empty. Check QUEUE_MANAGEMENT.md for retry logic"
            );
        }
        return task.getId();
    }
}
```

---

### H_EMPTY: No-op Method Bodies

**Pattern**: Method bodies with no logic: `{ }`

**Bad Code**:
```java
public class InitializationService {
    public void initialize() { }  // Empty body
}
```

**Fix**: Implement or throw:
```java
public class InitializationService {
    public void initialize() {
        this.warmCaches();
        this.registerListeners();
        this.validateConfiguration();
        logger.info("System initialized");
    }
}
```

---

### H_FALLBACK: Silent Degradation

**Pattern**: Catch blocks that return fake data instead of propagating exceptions

**Bad Code**:
```java
public List<Task> loadTasks() {
    try {
        return taskRepository.findAll();
    } catch (IOException e) {
        return Collections.emptyList();  // SILENT FALLBACK
    }
}
```

**Fix**: Propagate the exception or log + provide real alternative:
```java
public List<Task> loadTasks() {
    try {
        return taskRepository.findAll();
    } catch (IOException e) {
        // Option A: Re-throw
        throw new DataAccessException("Failed to load tasks", e);
        
        // Option B: Log + retry with cache
        logger.warn("Database unavailable, using cache", e);
        return this.cache.getOrEmpty("tasks");
    }
}
```

---

### H_LIE: Code-Documentation Mismatches

**Pattern**: JavaDoc claims behavior that implementation doesn't deliver

**Bad Code**:
```java
/**
 * Retrieves a task by ID.
 * 
 * @param id The task ID
 * @return The task, never null
 * @throws TaskNotFoundException if not found
 */
public Task getTask(String id) {
    return taskMap.get(id);  // Returns null if not found!
}
```

**Fix**: Update code to match documentation:
```java
/**
 * Retrieves a task by ID.
 * 
 * @param id The task ID
 * @return The task, never null
 * @throws TaskNotFoundException if not found
 */
public Task getTask(String id) {
    var task = taskMap.get(id);
    if (task == null) {
        throw new TaskNotFoundException("Task not found: " + id);
    }
    return task;
}
```

---

### H_SILENT: Logging Instead of Throwing

**Pattern**: Log statements about unimplemented features instead of throwing

**Bad Code**:
```java
public class WorkletService {
    public void invokeWorklet(String name) {
        if (!isImplemented(name)) {
            log.error("Worklet feature not implemented yet");  // SILENT
            return;
        }
        // ...
    }
}
```

**Fix**: Throw exception:
```java
public class WorkletService {
    public void invokeWorklet(String name) {
        if (!isImplemented(name)) {
            throw new UnsupportedOperationException(
                "Worklet feature '" + name + "' not yet implemented. " +
                "See WORKLET_ROADMAP.md for timeline"
            );
        }
        // ...
    }
}
```

---

## Fixing Invariant Violations (Q Phase)

The **Q (Invariants) phase** enforces: **real implementation ∨ throw UnsupportedOperationException**

Three violation types:

### Q1: Empty Methods (real_impl ∨ throw)

**Issue**: Method body is empty `{ }` — no real logic, no exception

**Fix**: Implement the method or throw `UnsupportedOperationException`:

```java
// BEFORE (Violation)
public void persistWorkflow(YSpecification spec) { }

// AFTER (Fixed)
public void persistWorkflow(YSpecification spec) {
    // Real implementation
    this.database.insert(spec);
    this.cache.invalidate("specs");
}

// OR ALTERNATIVELY (Also Fixed)
public void persistWorkflow(YSpecification spec) {
    throw new UnsupportedOperationException(
        "Workflow persistence requires database setup. " +
        "See PERSISTENCE_SETUP.md"
    );
}
```

---

### Q2: Mock/Stub Classes (¬mock)

**Issue**: Class names indicate mock/stub/fake implementations

**Fix**: Rename to real class name or delete from production code:

```java
// BEFORE (Violation)
public class StubAuthenticationService implements AuthenticationService {
    @Override
    public boolean authenticate(String user, String password) {
        return password.equals("test123");  // Always accepts test password
    }
}

// AFTER (Fixed)
public class JwtAuthenticationService implements AuthenticationService {
    @Override
    public boolean authenticate(String user, String password) {
        var token = jwtProvider.encode(user, password);
        return jwtProvider.verify(token);
    }
}
```

---

### Q3: Silent Fallbacks (¬silent_fallback)

**Issue**: Catch blocks return fake data without logging or re-throwing

**Fix**: Re-throw exception or provide logged alternative:

```java
// BEFORE (Violation)
public YTask getTask(String id) {
    try {
        return taskRepository.findById(id);
    } catch (SQLException e) {
        return null;  // SILENT FALLBACK
    }
}

// AFTER (Fixed) — Option A: Re-throw
public YTask getTask(String id) throws TaskAccessException {
    try {
        return taskRepository.findById(id);
    } catch (SQLException e) {
        throw new TaskAccessException("Failed to fetch task: " + id, e);
    }
}

// AFTER (Fixed) — Option B: Log + cached alternative
public YTask getTask(String id) {
    try {
        return taskRepository.findById(id);
    } catch (SQLException e) {
        logger.warn("Database unavailable for task {}, using cache", id, e);
        return this.cache.get(id)
            .orElseThrow(() -> new TaskNotFoundException("Task not in cache: " + id));
    }
}
```

---

## Troubleshooting

### Issue: "Guard violations detected. Fix violations or throw UnsupportedOperationException."

**Root cause**: H phase found forbidden patterns

**Solution**:
1. Read the receipt: `cat .claude/receipts/guard-receipt.json | jq '.violations'`
2. For each violation, follow the corresponding fix section above
3. Re-run: `bash scripts/dx.sh all`

**Example**:
```bash
$ bash scripts/dx.sh all
[H] Guard phase: 3 violations detected
    See: .claude/receipts/guard-receipt.json

$ jq '.violations[]' .claude/receipts/guard-receipt.json
{
  "pattern": "H_TODO",
  "file": "yawl-engine/src/YNetRunner.java",
  "line": 427,
  "content": "// TODO: Add deadlock detection"
}

# Fix the violation (implement or remove)
# Then re-run:
$ bash scripts/dx.sh all
✅ All validations passed
```

---

### Issue: "Invariant violations detected. Fix violations and re-run Q phase."

**Root cause**: Q phase found empty methods, mocks, or silent fallbacks

**Solution**:
1. Read the receipt: `cat .claude/receipts/invariant-receipt.json | jq '.violations'`
2. For each violation type (Q1/Q2/Q3), follow the fix section above
3. Re-run: `bash scripts/dx.sh all`

---

### Issue: I need to bypass validation for a legacy build

**Solution**: Use `--skip-validate` flag:

```bash
bash scripts/dx.sh all --skip-validate
```

**Warning**: This bypasses safety checks. Only use for temporary debugging. Always fix violations in production code.

---

### Issue: Validation phase is too slow

**Solution**: Current performance (per 100 files):
- H phase (Guards): ~2-3 seconds
- Q phase (Invariants): ~3-4 seconds
- **Total**: ~5-7 seconds for typical codebase

If this exceeds your time budget:
1. Use `--skip-validate` for personal development (fast iterations)
2. Run validation in CI/CD only (pre-commit checks)
3. Report performance issues to the team

---

## Performance Impact

### Build Time Breakdown

```
┌─ dx.sh all ─────────────────────────────────────┐
│ Compile (changed modules): ~10-15 sec            │
│ Test (all tiers):          ~15-30 sec            │
│ Guard validation (H phase): ~2-3 sec             │
│ Invariant validation (Q):   ~3-4 sec             │
├──────────────────────────────────────────────────┤
│ TOTAL: ~30-52 sec (first run, no cache)         │
│        ~20-40 sec (cached artifacts)             │
└──────────────────────────────────────────────────┘
```

**Optimization Tips**:
1. Use `dx.sh` (incremental, no validation) for local development: ~5-15 sec
2. Use `dx.sh compile` or `dx.sh test` for single-phase debugging
3. Use `--warm-cache` for faster engine compilation (saved 3-5 sec)
4. Use CI/CD for full validation (`dx.sh all`) before commit

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Validate and Build

on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '25'
      
      - name: Run full build with validation
        run: bash scripts/dx.sh all
      
      - name: Archive validation receipts
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: validation-receipts
          path: .claude/receipts/
      
      - name: Report violations
        if: failure()
        run: |
          echo "## Validation Failed"
          echo "### Guard Violations"
          jq '.violations[]' .claude/receipts/guard-receipt.json || echo "No guard violations"
          echo "### Invariant Violations"
          jq '.violations[]' .claude/receipts/invariant-receipt.json || echo "No invariant violations"
```

---

## Summary

| Scenario | Command | Validation? | Notes |
|----------|---------|------------|-------|
| Local dev (fast) | `dx.sh` | NO | ~5-15s, changed modules only |
| Single module | `dx.sh -pl yawl-engine` | NO | Fast iteration, no validation |
| Full build (safe) | `dx.sh all` | YES | ~30-52s, includes H+Q validation |
| Skip validation | `dx.sh all --skip-validate` | NO | Use only for debugging |
| Validate only | `dx.sh --validate-only` | YES | Run H+Q phases only |
| CI/CD pipeline | `dx.sh all` | YES | Fail if H or Q violations |

**Key Takeaway**: Validation gates are **mandatory for `dx.sh all`**, **optional for incremental builds**, and **easily bypassed** for legacy scripts with `--skip-validate`.

---

## Questions?

- **Build process**: See `/home/user/yawl/docs/build-guide.md`
- **GODSPEED flow**: See `CLAUDE.md` § Λ (BUILD)
- **Validation patterns**: See `.claude/HYPER_STANDARDS.md`
- **Architecture**: See `.claude/rules/validation-phases/H-GUARDS-DESIGN.md`

**Report issues**: GitHub Issues or Slack #yawl-dev
