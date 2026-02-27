# Tutorial 11 — Walk the Full Quality Gate Sequence

**Quadrant**: Tutorial | **Time**: 45 minutes | **Level**: Intermediate

You are going to run all seven quality gates against a real change to YAWL and see exactly what happens at each gate. By the end you will understand the full lifecycle from writing a line of code to tagging a release.

---

## What You Will Learn

- How the seven gates enforce a strict partial order: compile → test → guard → analysis → security → documentation → release
- What each gate checks, how to run it, and what a failure looks like
- How `dx.sh`, `hyper-validate.sh`, and `validate-release.sh` fit together

## Prerequisites

- YAWL cloned and building: `bash scripts/dx.sh compile` exits 0
- Java 25 available: `java -version` shows 25.x
- Maven 3.9+: `mvn -version` shows 3.9.x

---

## Step 1 — Gate G_compile: Make a change and compile

Add a trivial method to `yawl-utilities`.

```bash
# Open any existing utility class
cat >> yawl-utilities/src/main/java/org/yawlfoundation/yawl/util/StringUtil.java << 'EOF'

    /** Returns true if s contains only ASCII digits. */
    public static boolean isDigitsOnly(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }
EOF
```

Run the gate:

```bash
bash scripts/dx.sh compile
```

Expected output:
```
dx: compile
dx: scope=changed modules | phase=compile | fail-strategy=fast
✓ SUCCESS | time: 4.2s | modules: 1 | tests: 0
```

**Gate G_compile PASS** — zero compilation errors, under 90s.

---

## Step 2 — Gate G_test: Write and run the test

Create a matching test (Chicago TDD: real objects, no mocks):

```bash
cat > yawl-utilities/src/test/java/org/yawlfoundation/yawl/util/StringUtilIsDigitsOnlyTest.java << 'EOF'
package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class StringUtilIsDigitsOnlyTest {

    @Test void nullReturnsFalse()  { assertFalse(StringUtil.isDigitsOnly(null)); }
    @Test void emptyReturnsFalse() { assertFalse(StringUtil.isDigitsOnly("")); }
    @Test void digitsReturnsTrue() { assertTrue(StringUtil.isDigitsOnly("12345")); }
    @Test void mixedReturnsFalse() { assertFalse(StringUtil.isDigitsOnly("123a")); }
}
EOF
```

Run the gate:

```bash
bash scripts/dx.sh test
```

Expected:
```
✓ SUCCESS | time: 6.8s | modules: 1 | tests: 4
```

**Gate G_test PASS** — 100% pass rate, 0 failures.

---

## Step 3 — Gate G_guard: Trigger and fix a violation

Introduce a TODO comment (forbidden H1 pattern):

```bash
# Add this line inside the method body:
# // TODO: add Unicode support
```

Try to save the file. The `hyper-validate.sh` hook fires immediately:

```
[GUARD] H1 violation: deferred work marker
  File: yawl-utilities/src/main/java/org/yawlfoundation/yawl/util/StringUtil.java
  Line: 42
  Content: // TODO: add Unicode support
  Fix: Implement real logic OR throw UnsupportedOperationException("reason")
exit 2
```

Fix it — either implement or throw:

```java
// Option A: implement it
return s != null && !s.isEmpty() && s.chars().allMatch(c -> c >= '0' && c <= '9');

// Option B: throw explicitly
throw new UnsupportedOperationException(
    "Unicode digit support not implemented. See issue #42 for tracking.");
```

Verify:

```bash
bash .claude/hooks/hyper-validate.sh yawl-utilities/src/main/java/
# exit 0 — no violations
```

**Gate G_guard PASS** — 0 of 14 H-patterns present.

---

## Step 4 — Gate G_analysis: Run static analysis

```bash
mvn -T 1.5C clean verify -P analysis -pl yawl-utilities
```

Expected:
```
[INFO] SpotBugs: 0 bugs found
[INFO] PMD: 0 violations
[INFO] Checkstyle: 0 violations
[INFO] JaCoCo: line coverage 82%
```

If SpotBugs flags something, read the report at `target/spotbugsXml.xml`.

**Gate G_analysis PASS** — zero tool violations, coverage above 75%.

---

## Step 5 — Gate G_security: Generate SBOM and scan

```bash
# Generate Software Bill of Materials
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom

# Scan for CVEs (requires grype installed)
grype sbom:target/bom.json --fail-on critical
```

Expected if clean:
```
 ✔ Vulnerability DB        [no update available]
 ✔ Indexed target/bom.json
 ✔ Catalogued packages      [342 packages]
 ✔ Scanned for vulnerabilities [0 critical, 2 high, 5 medium]
```

**Gate G_security PASS** — 0 critical CVEs.

---

## Step 6 — Gate G_documentation: Check package-info

Every package that ships public API needs a `package-info.java`:

```bash
# Check the utilities package
ls yawl-utilities/src/main/java/org/yawlfoundation/yawl/util/package-info.java
```

If it is missing, create it:

```java
/**
 * YAWL utility library — XML processing, string helpers, JWT management.
 *
 * <p>Entry points: {@link org.yawlfoundation.yawl.util.StringUtil},
 * {@link org.yawlfoundation.yawl.util.XNodeParser}.
 *
 * <p>All methods are stateless and thread-safe unless noted.
 */
package org.yawlfoundation.yawl.util;
```

**Gate G_documentation PASS** — all packages documented.

---

## Step 7 — Gate G_release: Run the full release validator

```bash
bash scripts/validation/validate-release.sh all
```

This runs PY-1 through PY-3:

```
=== PY-1: Gate Receipts (FM8, FM10) ===
[PASS] Receipt fresh (2h old): gate-G_guard-receipt.json
[PASS] Receipt fresh (1h old): gate-G_test-receipt.json
[PASS] Receipt fresh (3h old): gate-G_security-receipt.json
[PASS] Stability test signed by: Jane Smith

=== PY-2: Test Coverage Artifact (FM11) ===
[PASS] JaCoCo aggregate report exists
[PASS] Instruction coverage: 76%

=== PY-3: SBOM + Grype CVE Scan (FM12) ===
[PASS] SBOM exists: target/bom.json
[PASS] Grype CVE scan: no critical vulnerabilities

=== RELEASE GATE: GREEN — all checks passed ===
```

**Gate G_release PASS** — all seven gates green, safe to tag.

---

## Summary

You have just walked every gate in sequence:

```
G_compile  →  G_test  →  G_guard  →  G_analysis  →  G_security  →  G_documentation  →  G_release
   4s           7s         <1s          45s             90s              0s                30s
```

The critical insight: **no gate can be skipped**. Each produces evidence (receipts, reports, artifacts) that the next gate verifies. This chain prevents the sixteen failure modes tracked in the FMEA risk table.

## What Next

- **Reference**: [Quality Gates Reference](../reference/quality-gates.md) — all predicates, commands, thresholds
- **How-To**: [Run Release Validation](../how-to/build/run-release-validation.md) — just PY-1/2/3 gates
- **Explanation**: [Why H-Guards Exist](../explanation/h-guards-philosophy.md) — the reasoning behind the 14 patterns
