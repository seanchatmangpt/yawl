# How to Run the Full GODSPEED Circuit

A step-by-step task-oriented guide to running the complete YAWL quality assurance pipeline: Ψ → Λ → H → Q → Ω.

**Status**: Production Ready
**Updated**: 2026-02-28
**Target Audience**: Developers performing their first GODSPEED circuit, CI/CD engineers validating gates

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Overview](#quick-overview)
3. [Step-by-Step: Run Full Circuit](#step-by-step-run-full-circuit)
4. [Common Failure Points & Fixes](#common-failure-points--fixes)
5. [Decision Tree: Which Phase to Run](#decision-tree-which-phase-to-run)
6. [Real-World Workflow Example](#real-world-workflow-example)
7. [Pro Tips](#pro-tips)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before running the GODSPEED circuit, ensure you have:

### Environment Setup

**Check Java Version** (25+):
```bash
java -version
```
Expected: `openjdk version "25" ...` or later

**Check Maven** (3.9.0+):
```bash
mvn --version
```
Expected: `Apache Maven 3.9.0 or later`

**Check Git** (2.40+):
```bash
git --version
```
Expected: `git version 2.40.0 or later`

### Repository Setup

Ensure you're in the YAWL repository root:
```bash
cd /path/to/yawl
git status
```

Verify clean working tree or stage changes:
```bash
git add <your-files>
git status --short
```

### Understanding GODSPEED Phases (High Level)

The circuit consists of 5 phases flowing left-to-right:

| Phase | Name | Purpose | Time |
|-------|------|---------|------|
| **Ψ** | Observatory | Generate facts about codebase | 30-60s |
| **Λ** | Build | Compile → Test → Validate coverage | 2-5 min |
| **H** | Guards | Check for forbidden patterns (TODO, mock, stub, etc.) | 10-20s |
| **Q** | Invariants | Validate real implementation (no "for now" code) | 5-10s |
| **Ω** | Git | Create atomic commit with full traceability | 2-5s |

**Full circuit time**: ~3-7 minutes (first run slower due to cache misses)

---

## Quick Overview

### What Each Phase Does

**Ψ OBSERVATORY**: Scans your codebase and generates structured facts:
- Module dependencies
- Test coverage baselines
- Known gates and constraints
- Performance SLAs
- Cached for 24 hours

**Λ BUILD**: Executes Maven build pipeline:
- Compile all modules
- Run all tests
- Measure code coverage
- Validate performance gates
- If any fails → fix + retry

**H GUARDS**: Automatically detects 7 forbidden patterns:
- H_TODO: `// TODO`, `// FIXME` comments
- H_MOCK: Classes/methods named `Mock*`, `*mock*`
- H_STUB: Empty/placeholder returns (`return "";`, `return null;`)
- H_EMPTY: No-op method bodies (`{ }`)
- H_FALLBACK: Silent catch blocks (catch then return fake data)
- H_LIE: Code doesn't match documentation
- H_SILENT: Logging instead of throwing exceptions

**Q INVARIANTS**: Validates your code is real:
- No "for now", "later", "temporary" markers
- APIs match documentation
- No mock/stub/fallback patterns
- Implementations match signatures

**Ω GIT**: Creates atomic, traceable commit:
- One logical change per commit
- Session ID in branch name
- Commit message with context
- Never force-push

---

## Step-by-Step: Run Full Circuit

### Step 1: Observatory (Ψ) — Generate Facts

**Why**: Observatory creates a snapshot of your codebase structure. Needed for consistent validation across all downstream phases.

```bash
bash scripts/observatory/observatory.sh
```

**What to look for**:

```
✓ receipts/observatory.json created
✓ SHA256 verification passed
✓ 9 fact files generated:
  - modules.json
  - gates.json
  - deps-conflicts.json
  - reactor.json
  - shared-src.json
  - tests.json
  - dual-family.json
  - duplicates.json
  - maven-hazards.json
✓ 8 diagrams generated (mermaid format)
```

**If it fails**:
- Check if scripts/observatory/ exists
- Verify file permissions: `chmod +x scripts/observatory/observatory.sh`
- Check disk space: `df -h`
- Retry: `bash scripts/observatory/observatory.sh`

**Typical output**:
```
[observatory] Scanning 89 Java packages...
[observatory] Building dependency graph...
[observatory] Analyzing test coverage baselines...
[facts] Writing modules.json (1.2 MB, 847 modules)
[facts] Writing gates.json (234 KB, 12 gates)
[validation] SHA256 receipts/observatory.json: abc123def456...
[success] All facts generated in 45 seconds
```

---

### Step 2: Build (Λ) — Compile ≺ Test ≺ Validate

**Why**: Ensures code compiles, passes tests, and meets coverage thresholds before quality gates.

```bash
bash scripts/dx.sh all
```

**What to look for**:

```
[COMPILE] ============================================
✓ All 89 modules compile successfully
✓ No compilation errors
✓ No deprecation warnings

[TEST] ================================================
✓ 4,847 tests passed (0 failed, 0 skipped)
✓ Execution time: 187 seconds

[VALIDATE] ============================================
✓ Code coverage: 68.4% line / 61.2% branch
  (threshold: ≥65% line / ≥55% branch)
✓ Performance gates: ALL PASS
  - YNetRunner latency: 127ms (SLA: <200ms)
  - YStatelessEngine throughput: 8,940 ops/sec (SLA: >8000)

[SUCCESS] Full build gate passed
```

**If compile fails**:
```bash
# Fix compilation errors (view details)
bash scripts/dx.sh compile
# Then:
bash scripts/dx.sh all  # Retry
```

**If tests fail**:
```bash
# Run failing test with details
bash scripts/dx.sh -pl <failing-module>
# Fix test or code
# Then retry full:
bash scripts/dx.sh all
```

**If coverage below threshold**:
```bash
# Add tests to increase coverage
# Coverage report location: target/site/jacoco/index.html
# Then retry:
bash scripts/dx.sh all
```

**If performance gates fail**:
```bash
# Review SLA specification
cat .claude/gates.json | grep -A 5 "latency\|throughput"
# Optimize code or adjust SLAs in consultation with team
bash scripts/dx.sh all
```

---

### Step 3: Guards (H) — Check Forbidden Patterns

**Why**: Ensures code follows production standards (no deferred work, mocks, or placeholder implementations).

**How it works**: Runs automatically when you write/edit files via `.claude/hooks/hyper-validate.sh`

```bash
# If you want to manually run guards:
bash scripts/dx.sh compile  # Triggers hooks
```

**What to look for**:

```
[guards] Scanning 847 Java files...
[h-todo] 0 TODO/FIXME markers found ✓
[h-mock] 0 Mock classes/methods found ✓
[h-stub] 0 Empty returns found ✓
[h-empty] 0 Empty method bodies found ✓
[h-fallback] 0 Silent catch blocks found ✓
[h-lie] 0 Documentation mismatches found ✓
[h-silent] 0 Log-instead-of-throw found ✓

[success] Guards validation passed
```

**If violations found**:

Read the detailed report:
```bash
cat .claude/receipts/guard-receipt.json | jq '.violations'
```

Example violation output:
```json
{
  "violations": [
    {
      "pattern": "H_TODO",
      "file": "yawl-engine/src/main/java/YNetRunner.java",
      "line": 427,
      "content": "// TODO: Add deadlock detection",
      "fix_guidance": "Implement real deadlock detection or throw UnsupportedOperationException"
    },
    {
      "pattern": "H_MOCK",
      "file": "generated/MockDataService.java",
      "line": 12,
      "content": "public class MockDataService implements DataService {",
      "fix_guidance": "Delete mock class or implement real service"
    }
  ]
}
```

**Fix violations**:
```bash
# 1. Open each file and fix violations
vi yawl-engine/src/main/java/YNetRunner.java
# Remove // TODO or implement real logic

vi generated/MockDataService.java
# Delete or implement real service

# 2. Re-run to verify
bash scripts/dx.sh compile
# Check guards output again

# 3. If still red, read guidance and implement
cat .claude/receipts/guard-receipt.json | jq '.violations[0].fix_guidance'
```

---

### Step 4: Invariants (Q) — Validate Real Implementation

**Why**: Ensures code is production-ready, not temporary or deceptive.

**How it works**: Runs automatically after Build succeeds. Checks:
- No "for now", "later", "temporary" markers
- Method implementations match documentation
- No silent fallbacks or mock returns
- All exceptions properly thrown (not silently logged)

**If violations detected** (rare, usually caught by H):
```bash
# Read invariant violations
cat .claude/receipts/invariant-receipt.json | jq '.violations'

# Fix violations by implementing real logic
# Example: If method says "throws IOException" but returns null instead
# → throw new IOException("reason") or implement real logic

bash scripts/dx.sh all  # Retry
```

---

### Step 5: Git (Ω) — Create Atomic Commit

**Why**: Ensures all changes are traceable, auditable, and follows a consistent format.

**Create commit**:
```bash
# 1. Stage specific files (never use git add .)
git add src/main/java/YNetRunner.java
git add src/test/java/YNetRunnerTest.java

# 2. Verify staging
git status --short

# 3. Create commit with context
git commit -m "Fix: YNetRunner deadlock in virtual thread scheduler

- Root cause: Lock contention in task queue
- Solution: Use ReentrantLock with backoff strategy
- Tests: Added 3 new concurrency tests (now 98% coverage)
- Performance: Latency improved 23ms → 8ms (SLA target: <200ms)

Ψ→Λ→H→Q→Ω green"
```

**Verify commit**:
```bash
# Check commit was created
git log -1 --oneline

# Check branch has session ID
git rev-parse --abbrev-ref HEAD
# Expected: claude/fix-netrunner-deadlock-abc123xyz
```

**Push to remote**:
```bash
# Push with upstream tracking
git push -u origin claude/fix-netrunner-deadlock-abc123xyz

# Verify on GitHub
git log origin/claude/fix-netrunner-deadlock-abc123xyz -1
```

---

## Common Failure Points & Fixes

This table shows the most common failures and how to fix them:

| Failure | Phase | Symptom | Fix |
|---------|-------|---------|-----|
| Observatory stale | Ψ | "Facts older than 24h" | `bash scripts/observatory/observatory.sh` |
| Compile error | Λ | `[ERROR] ... cannot find symbol` | Fix Java code, retry `dx.sh compile` |
| Test failure | Λ | `[FAIL] 3 tests failed` | Debug test, fix code/test, retry `dx.sh test` |
| Coverage below 65% | Λ | "Coverage: 62.1% (threshold: 65%)" | Add tests, retry `dx.sh all` |
| Latency SLA miss | Λ | "YNetRunner: 342ms (SLA: <200ms)" | Profile code, optimize, retry |
| H_TODO found | H | "5 TODO markers detected" | Remove TODOs or implement, retry compile |
| H_MOCK found | H | "MockDataService.java:12" | Delete mock or implement real, retry |
| H_STUB found | H | `return "";` or `return null;` | Implement real logic or throw exception |
| H_EMPTY found | H | Empty method `{ }` | Implement logic or throw UnsupportedOperationException |
| H_FALLBACK found | H | `catch (...) { return null; }` | Propagate exception instead |
| H_LIE found | H | "Doc says throws IOException, code returns null" | Fix code to match docs |
| H_SILENT found | H | `log.error("not implemented")` | Throw exception instead |
| Code ≠ docs | Q | Method signature doesn't match javadoc | Update code or documentation |
| Push rejected | Ω | "remote: hook ... failed" | Verify branch name, authenticate, retry |
| Git conflict | Ω | "CONFLICT (content merge)" | Resolve conflicts, commit, push |

---

## Decision Tree: Which Phase to Run

Not all phases needed in every scenario. Use this tree to decide:

```
What did you change?

├─ CODE only (Java, test, config)?
│  └─ → Run FULL CIRCUIT: Ψ→Λ→H→Q→Ω
│     (Or skip Ψ if facts <1 day old: Λ→H→Q→Ω)
│
├─ DOCS only (markdown, comments)?
│  └─ → Skip Λ/H/Q (no code changes)
│     Verify doc links: grep -r "\[" docs/
│     Manual review for accuracy
│
├─ SCHEMA only (XSD, protobuf)?
│  └─ → Start with Λ (rebuild affected modules)
│     Then full: Λ→H→Q→Ω
│     Check gates.json for schema-related SLAs
│
├─ TESTS only?
│  └─ → Facts fresh? (≥1 day old)
│     YES → Run: Λ (compile + test + coverage) + H→Q
│     NO → Run: Ψ→Λ→H→Q
│
├─ DEPENDENCIES only (pom.xml)?
│  └─ → Run: Λ (full build detects breakage)
│     Then: H→Q
│
└─ UNSURE?
   └─ → Always safe: Run full Ψ→Λ→H→Q→Ω
      (Extra 1-2 min, guaranteed correctness)
```

---

## Real-World Workflow Example

Here's a complete workflow from code change to merged commit:

### Scenario: Fix YNetRunner Deadlock

**1. Make code changes**:
```bash
cd /home/user/yawl
vi yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java
# Add backoff strategy to task queue locking
```

**2. Run observatory** (first time or if major changes):
```bash
bash scripts/observatory/observatory.sh
# Output: Facts generated, SHA256 verified ✓
```

**3. Build & test**:
```bash
bash scripts/dx.sh all
# Output:
# [COMPILE] ✓ All modules
# [TEST] ✓ 4,847 tests passed
# [VALIDATE] ✓ Coverage: 68.4% (threshold 65%)
#            ✓ Latency: 127ms (SLA <200ms)
```

**4. Verify no guard violations**:
```bash
cat .claude/receipts/guard-receipt.json | jq '.status'
# Output: "GREEN"
```

**5. Write test for the fix**:
```bash
vi yawl-engine/src/test/java/org/yawl/engine/YNetRunnerConcurrencyTest.java
# Add: testDeadlockUnderHighLoadWithBackoff()

bash scripts/dx.sh -pl yawl-engine  # Quick recompile
# [TEST] ✓ New test passes
```

**6. Stage and commit**:
```bash
git add yawl-engine/src/main/java/org/yawl/engine/YNetRunner.java
git add yawl-engine/src/test/java/org/yawl/engine/YNetRunnerConcurrencyTest.java
git status --short

git commit -m "Fix: YNetRunner deadlock in virtual thread scheduler

Background:
When handling >1000 concurrent tasks, lock contention in the task queue
caused threads to deadlock, blocking case execution.

Solution:
Replaced simple ReentrantLock with exponential backoff strategy:
- Initial wait: 1ms
- Max backoff: 100ms
- Exponential growth: 1ms → 2ms → 4ms → ... → 100ms

Testing:
- Added YNetRunnerConcurrencyTest with >10k concurrent tasks
- Load test passes without deadlock (120s execution)
- Coverage increased: 96.2% → 98.4%

Performance Impact:
- Latency improvement: 342ms → 127ms (63% reduction)
- Well below SLA of 200ms
- Throughput stable: 8,940 ops/sec (SLA: >8000)

Ψ→Λ→H→Q→Ω green"

git log -1  # Verify commit created
```

**7. Push to remote**:
```bash
git push -u origin claude/fix-netrunner-deadlock-abc123xyz
# Output: New pull request ready at https://github.com/yawlfoundation/yawl/...
```

**8. CI/CD validates**:
```bash
# GitHub Actions runs same GODSPEED circuit (automated)
# Once green, can merge to main
```

---

## Pro Tips

### Faster Feedback Loop

**Tip 1: Use fast compile for single module**
```bash
# Instead of: bash scripts/dx.sh all  (~5 min for full codebase)
# Use: bash scripts/dx.sh -pl yawl-engine  (~30 sec for one module)
```

**Tip 2: Skip observatory on small changes**
```bash
# If facts file is fresh (<1 hour old):
ls -l .claude/receipts/observatory.json  # Check timestamp
# If fresh, skip: bash scripts/dx.sh all
# If stale (>24h): bash scripts/observatory/observatory.sh first
```

**Tip 3: Run guards manually to verify**
```bash
# Guards run automatically on compile, but you can check anytime:
cat .claude/receipts/guard-receipt.json | jq '{status, violations: (.violations | length)}'
# Output: {"status": "GREEN", "violations": 0}
```

**Tip 4: Pre-commit hook automation**
```bash
# Install git hook to auto-run dx.sh on commit attempt:
cp .claude/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# Now each commit attempt auto-runs gates before committing
git commit -m "msg"  # Auto-runs Λ→H→Q→Ω
```

### Understanding the Flow

**Ψ→Λ→H→Q→Ω Order Matters**:

| Phase | Depends On | Why |
|-------|-----------|-----|
| **Λ** | **Ψ** | Facts provide module boundaries for build |
| **H** | **Λ** | Only scan compiled code (no syntax errors) |
| **Q** | **H** | Invariants assume no guard violations |
| **Ω** | **Q** | Only commit if all gates green |

Never skip or reorder phases!

### Reading Receipts

All phases produce JSON receipts for debugging:

```bash
# Observatory facts
cat .claude/receipts/observatory.json | jq '.summary'

# Build results
cat .claude/receipts/build.json | jq '{compile_time, test_count, coverage}'

# Guard violations
cat .claude/receipts/guard-receipt.json | jq '.violations[0]'

# Invariant checks (usually empty if guards passed)
cat .claude/receipts/invariant-receipt.json | jq '.violations'

# Git metadata (after commit)
git log --oneline -1 | cat .claude/receipts/git.json
```

---

## Troubleshooting

### "Observatory script not found"

```bash
# Verify path
ls -la /home/user/yawl/scripts/observatory/observatory.sh

# If missing, check git
git status scripts/observatory/

# If unstaged, stage it
git add scripts/observatory/
```

### "Maven compilation fails with dependency error"

```bash
# Clear Maven cache
rm -rf ~/.m2/repository

# Rebuild
bash scripts/dx.sh all

# If still fails, check proxy
mvn help:active-profiles  # See if offline profile is active
```

### "Tests fail but they passed locally before"

```bash
# Ensure clean state
mvn clean

# Rebuild fresh
bash scripts/dx.sh all

# If specific test fails, run isolated
mvn test -Dtest=TestClassName#testMethodName
```

### "Guard violations claim code is fine, but it's really not"

```bash
# Check if guards ran at all
cat .claude/receipts/guard-receipt.json | jq '.timestamp'

# If timestamp is old (>24h), re-run
bash scripts/dx.sh compile  # Triggers guards

# If guards still don't detect issue, file bug:
# github.com/yawlfoundation/yawl/issues
# Include: guard-receipt.json + suspicious code snippet
```

### "Can't push to GitHub"

```bash
# Verify authentication
git config credential.helper  # Should show cache or osxkeychain

# Re-authenticate
git config --global credential.helper cache
git push -u origin claude/your-branch
# Will prompt for GitHub token (use personal access token)

# If branch name wrong, check it has session ID
git branch  # Should show: claude/fix-something-abc123xyz
```

### "Performance SLA fails locally but passes in CI"

```bash
# Local environment differences (CPU, memory)
# Run performance test multiple times to average:
for i in {1..3}; do
  bash scripts/dx.sh all | grep -i "latency\|throughput"
done

# If consistently below SLA, profile the code:
java -XX:+UnlockDiagnosticVMOptions -XX:+TraceClassLoading \
  -m yawl-engine YNetRunner

# OR check if gates.json SLA is outdated:
cat .claude/gates.json | grep -A 3 "latency"
# File issue if SLA unrealistic for hardware
```

---

## Summary Checklist

Before marking your work done:

- [ ] Ψ Observatory ran successfully (`receipts/observatory.json` created)
- [ ] Λ Build passed all gates (compile, test, coverage, perf)
- [ ] H Guards found 0 violations (no TODO, mock, stub, etc.)
- [ ] Q Invariants passed (no "for now" code)
- [ ] Ω Commit created with session ID in branch name
- [ ] Code pushed to remote (GitHub)
- [ ] Commit message explains *why*, not just *what*
- [ ] Related files documented in commit message

**You're done!** Your code is now production-ready and fully traceable through the GODSPEED circuit.

---

## Next Steps

1. **Merge to main**: After CI/CD validates in GitHub, merge PR
2. **Deploy**: See [deployment guide](../deployment/docker.md)
3. **Monitor**: Check performance in production via [observability dashboard](../operations/monitoring.md)

For questions on GODSPEED phases, see [GODSPEED Methodology Explained](../../explanation/godspeed-methodology.md).
