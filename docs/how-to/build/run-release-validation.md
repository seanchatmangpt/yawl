# How to Run Release Validation Gates

**Quadrant**: How-To | **Goal**: Verify all PY-1/2/3/4 gates pass before tagging a release

`scripts/validation/validate-release.sh` is the final poka-yoke checkpoint. It prevents FM8 (release docs before gate verification, RPN=630), the highest-ranked failure mode in the FMEA table.

---

## Prerequisites

- All seven quality gates green (`bash scripts/dx.sh all` exits 0)
- JaCoCo aggregate report generated (see [Measure Test Coverage](measure-test-coverage.md))
- SBOM generated (see step 3 below)
- `grype` installed: `grype version` works

---

## Run All Gates at Once

```bash
bash scripts/validation/validate-release.sh all
```

Exit code 0 = GREEN, safe to tag. Exit code 2 = violations found.

---

## Run Individual Gates

### PY-1 — Receipt freshness (FM8, FM10)

```bash
bash scripts/validation/validate-release.sh receipts
```

Checks that these receipts exist and are `< 24h` old:
- `receipts/gate-G_guard-receipt.json`
- `receipts/gate-G_test-receipt.json`
- `receipts/gate-G_security-receipt.json`
- `receipts/stability-test-receipt.json` (no age limit — human-signed)

To create a missing receipt, re-run the gate that produces it:

| Missing receipt | Command to run |
|-----------------|----------------|
| `gate-G_guard-receipt.json` | `bash .claude/hooks/hyper-validate.sh src/` |
| `gate-G_test-receipt.json` | `mvn -T 1.5C clean test` |
| `gate-G_security-receipt.json` | `mvn -P security verify` |

### PY-2 — Coverage artifact (FM11)

```bash
bash scripts/validation/validate-release.sh coverage
```

Checks `target/site/jacoco-aggregate/index.html` exists and instruction coverage ≥ 55% (floor) / ≥ 65% (target).

Generate the report first:

```bash
mvn -T 1.5C clean verify -P coverage
```

### PY-3 — SBOM + CVE scan (FM12)

```bash
bash scripts/validation/validate-release.sh sbom
```

Checks `target/bom.json` exists and Grype finds no critical CVEs.

Generate the SBOM:

```bash
mvn org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
```

Then validate:

```bash
grype sbom:target/bom.json --fail-on critical
```

### PY-4 — Stability receipt (FM13)

The `receipts/stability-test-receipt.json` must be filled by a human after a 48-hour staging run:

```bash
cp .claude/templates/stability-test-receipt.json receipts/stability-test-receipt.json
# Edit: fill duration_hours (≥48), result ("PASS"), signed_by (your name)
```

Then verify it passes:

```bash
bash scripts/validation/validate-release.sh stability
```

---

## Interpret the Output

```
=== PY-1: Gate Receipts (FM8, FM10) ===
[PASS] Receipt fresh (2h old): gate-G_guard-receipt.json
[FAIL] MISSING receipt: receipts/gate-G_test-receipt.json
       Run the gate that produces this receipt, then retry.

=== RELEASE GATE: RED — 1 violation(s) found ===
    Fix all failures before tagging the release.
```

Each `[FAIL]` line tells you exactly what to run to fix it.

---

## Tag the Release

Only after all gates are GREEN:

```bash
git tag -a v6.0.0-GA -m "Release v6.0.0-GA — all gates GREEN"
git push origin v6.0.0-GA
```

---

## See Also

- [Quality Gates Reference](../../reference/quality-gates.md) — all predicates and thresholds
- [FMEA Risk Table](../../reference/fmea-risk-table.md) — all 16 failure modes with RPN
- [Measure Test Coverage](measure-test-coverage.md) — generate the JaCoCo artifact
