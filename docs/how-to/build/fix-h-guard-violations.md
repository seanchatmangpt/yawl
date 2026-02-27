# How to Fix H-Guard Violations at Scale

**Quadrant**: How-To | **Goal**: Systematically remediate HYPER_STANDARDS violations across multiple files

The current codebase has 61 open violations (12 BLOCKER, 31 HIGH, 18 MEDIUM). This guide covers batch triage, prioritization, and the correct fix for each pattern.

---

## Step 1 — Get the Full Violation List

```bash
bash .claude/hooks/hyper-validate.sh src/ 2>&1 | tee /tmp/violations.txt
wc -l /tmp/violations.txt   # total violation lines
grep "BLOCKER" /tmp/violations.txt | wc -l
grep "HIGH"    /tmp/violations.txt | wc -l
grep "MEDIUM"  /tmp/violations.txt | wc -l
```

---

## Step 2 — Prioritize by Severity

Work in this order — BLOCKER violations block Beta; HIGH violations block RC1; MEDIUM violations block GA.

### BLOCKER (fix first)

| Pattern | Count | Fix |
|---------|-------|-----|
| H1 TODO/FIXME | varies | Implement or `throw UnsupportedOperationException` |
| H2 mock method names | varies | Rename to describe real behavior |
| H3 mock class names | varies | Rename or move to `test/` |
| H4 mock mode flags | varies | Remove flag + implement real path |
| H5 empty string return | varies | Return real value or throw |
| H14 mockito import in src/ | varies | Move class to `test/` |

### HIGH (fix after BLOCKER)

| Pattern | Count | Fix |
|---------|-------|-----|
| H6 null stub return | varies | Return real value or throw |
| H7 empty method body | varies | Implement or throw |
| H9 silent fallback catch | varies | Rethrow or propagate |
| H12 logic skipping | varies | Remove guard or implement |
| H13 log instead of throw | varies | Replace `log.warn` with `throw` |

### MEDIUM (fix last)

| Pattern | Count | Fix |
|---------|-------|-----|
| H8 placeholder constants | varies | Replace with real config values |
| H10 conditional mock logic | varies | Remove `isTestMode` path |
| H11 fake getOrDefault | varies | Use real default or throw |

---

## Step 3 — Fix Patterns by Type

### H1 — TODO Comments

```java
// BEFORE (BLOCKER):
// TODO: Add deadlock detection

// AFTER — option A, implement it:
if (hasCircularDependency(token)) {
    throw new YEngineException("Deadlock detected in net " + netId);
}

// AFTER — option B, declare impossible:
throw new UnsupportedOperationException(
    "Deadlock detection not implemented. See issue #42.");
```

### H5 — Empty String Return

```java
// BEFORE (BLOCKER):
public String getSpecificationId() { return ""; }

// AFTER:
public String getSpecificationId() {
    return Objects.requireNonNull(specId,
        "getSpecificationId called before specification was loaded");
}
```

### H7 — Empty Method Body

```java
// BEFORE (BLOCKER):
public void initialize() {}

// AFTER:
public void initialize() {
    schemaValidator.ensureSchemaVersion(SCHEMA_VERSION);
    connectionPool.warmUp(MIN_CONNECTIONS);
}
// OR:
public void initialize() {
    throw new UnsupportedOperationException(
        "initialize: schema bootstrapping not yet wired up.");
}
```

### H9 — Silent Fallback Catch

```java
// BEFORE (HIGH):
try {
    return externalService.fetch();
} catch (Exception e) {
    return Collections.emptyList();  // silent fallback
}

// AFTER — propagate as domain exception:
try {
    return externalService.fetch();
} catch (IOException e) {
    throw new YEngineException("Failed to fetch from external service", e);
}
```

### H13 — Log Instead of Throw

```java
// BEFORE (HIGH):
log.warn("method not yet implemented");
return null;

// AFTER:
throw new UnsupportedOperationException(
    "method not yet implemented — see issue #78");
```

---

## Step 4 — Verify in Batches

After fixing a file:

```bash
bash .claude/hooks/hyper-validate.sh path/to/FixedFile.java
```

After fixing a module:

```bash
bash .claude/hooks/hyper-validate.sh yawl-engine/src/
```

After fixing everything:

```bash
bash .claude/hooks/hyper-validate.sh src/
# Should exit 0 with no output
```

---

## Step 5 — Confirm Gate Passes

```bash
bash scripts/dx.sh all
# Must exit 0

bash scripts/validation/validate-release.sh receipts
# PY-1 checks that receipts/gate-G_guard-receipt.json is fresh
```

---

## Tracking Progress

The violation tracker is at `docs/v6/HYPER-STANDARDS-VIOLATIONS-TRACKER.md`. Update it as you fix violations:

```markdown
| # | File | Line | Pattern | Status |
|---|------|------|---------|--------|
| B-01 | YWorkItem.java | 427 | H1 | ✅ FIXED 2026-02-28 |
```

---

## See Also

- [HYPER_STANDARDS Pattern Reference](../../reference/hyper-standards.md) — all 14 patterns with regex
- [Tutorial: Fix Your First Violation](../../tutorials/12-fix-hyper-standards-violation.md) — step-by-step walkthrough
- [Why H-Guards Exist](../../explanation/h-guards-philosophy.md) — the philosophy
