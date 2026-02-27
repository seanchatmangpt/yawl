# Tutorial 12 — Fix Your First HYPER_STANDARDS Violation

**Quadrant**: Tutorial | **Time**: 20 minutes | **Level**: Beginner

HYPER_STANDARDS is the zero-tolerance quality policy enforced by `hyper-validate.sh` at every file write. When you trigger a violation, the hook exits 2 and blocks the operation. This tutorial teaches you to read the output, understand the violation type, and apply the correct fix.

---

## What You Will Learn

- The anatomy of a guard violation report
- The two legal resolutions (implement or throw)
- How to verify a fix with the guard hook directly

## Prerequisites

- YAWL cloned and `bash scripts/dx.sh compile` exits 0
- Understand that `src/` is production code; `test/` is test code

---

## Step 1 — Introduce a Violation

Create a file with a BLOCKER violation:

```bash
cat > /tmp/BadService.java << 'EOF'
package org.yawlfoundation.yawl.example;

public class BadService {
    // TODO: implement persistence later
    public String fetchData() {
        return "";  // stub — replace with real call
    }
    public void initialize() {}
}
EOF
```

Run the hook:

```bash
bash .claude/hooks/hyper-validate.sh /tmp/BadService.java
```

Output:

```
[GUARD H1] TODO comment at line 4
  Content: // TODO: implement persistence later
  Fix: implement real logic or throw UnsupportedOperationException

[GUARD H5] Empty string return at line 6
  Content: return "";
  Fix: return real value or throw UnsupportedOperationException

[GUARD H7] Empty method body at line 8
  Content: public void initialize() {}
  Fix: implement real logic or throw UnsupportedOperationException

3 violations — exit 2
```

Three different patterns fired: H1 (TODO), H5 (empty return), H7 (no-op method).

---

## Step 2 — Understand the Two Legal Resolutions

HYPER_STANDARDS allows exactly two outcomes for any method:

| Option | When to use | Example |
|--------|-------------|---------|
| **Implement it** | The logic is known | `return repo.findById(id).orElseThrow()` |
| **Throw UnsupportedOperationException** | Logic is future work | `throw new UnsupportedOperationException("fetchData requires DB integration, see #123")` |

There is no third option. `return null`, `return ""`, empty bodies, and TODO comments are all illegal in production code.

---

## Step 3 — Fix Each Violation

```java
package org.yawlfoundation.yawl.example;

public class BadService {

    private final DataRepository repo;

    public BadService(DataRepository repo) {
        this.repo = repo;
    }

    // H1 fixed: no TODO — the logic is either implemented or declared impossible
    // H5 fixed: no empty string return
    public String fetchData() {
        return repo.findLatest()
                   .map(DataRecord::getValue)
                   .orElseThrow(() -> new NoSuchElementException("No data available"));
    }

    // H7 fixed: no empty body — either real logic or explicit throw
    public void initialize() {
        repo.ensureSchemaExists();
    }
}
```

If the logic is genuinely not known yet, use the throw form:

```java
public String fetchData() {
    throw new UnsupportedOperationException(
        "fetchData: requires DataRepository integration. " +
        "See issue #98 for implementation plan."
    );
}

public void initialize() {
    throw new UnsupportedOperationException(
        "initialize: schema bootstrapping not yet implemented."
    );
}
```

---

## Step 4 — Verify the Fix

```bash
bash .claude/hooks/hyper-validate.sh /tmp/BadService.java
echo "Exit code: $?"
```

Expected:
```
Exit code: 0
```

No output on success — the hook is silent when clean.

---

## Step 5 — Check All 14 Pattern Types

Run the hook against a larger scope to check everything at once:

```bash
# Scan the entire utilities module
bash .claude/hooks/hyper-validate.sh yawl-utilities/src/main/java/

# Scan all source
bash .claude/hooks/hyper-validate.sh src/
```

The 14 patterns it checks:

| ID | What it catches |
|----|----------------|
| H1 | `TODO`, `FIXME`, `XXX`, `HACK`, `LATER` comments |
| H2 | Method names containing `mock`, `stub`, `fake` |
| H3 | Class names starting with `Mock`, `Stub`, `Fake` |
| H4 | Boolean flags like `useMockData = true` |
| H5 | `return "";` from non-void methods |
| H6 | `return null; // stub` |
| H7 | Empty method bodies `{ }` |
| H8 | Constants named `DUMMY_*`, `PLACEHOLDER_*` |
| H9 | Catch blocks that return fake data |
| H10 | `if (isTestMode)` conditional mock logic |
| H11 | `.getOrDefault(key, "test_value")` fake defaults |
| H12 | `if (true) return;` logic skipping |
| H13 | `log.warn("not implemented")` instead of throw |
| H14 | `import org.mockito.*` in `src/` (not `test/`) |

---

## What Happens if You Work Around the Hook

Nothing good. The hook also runs in CI. A bypass (`--no-verify`) will be caught at the analysis gate, which counts violations in the committed code. All 16 FMEA failure modes assume the hooks run correctly.

---

## What Next

- **Reference**: [HYPER_STANDARDS Pattern Reference](../reference/hyper-standards.md) — every pattern with regex and fix guidance
- **How-To**: [Fix H-Guard Violations at Scale](../how-to/build/fix-h-guard-violations.md) — batch remediation workflow
- **Explanation**: [Why H-Guards Exist](../explanation/h-guards-philosophy.md) — the philosophy behind zero-tolerance
