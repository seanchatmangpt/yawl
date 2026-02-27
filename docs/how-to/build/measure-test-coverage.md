# How to Measure Test Coverage

**Quadrant**: How-To | **Goal**: Generate the JaCoCo aggregate report and interpret results

Gate G_test requires: line coverage ≥ 65%, branch coverage ≥ 55%. Gate G_release (PY-2) asserts the report artifact exists before any tag is created.

---

## Generate the Aggregate Report

```bash
mvn -T 1.5C clean verify -P coverage
```

This runs all tests and aggregates JaCoCo reports across all modules. Duration: ~8–12 minutes first run, faster with incremental cache.

Output artifact:

```
target/site/jacoco-aggregate/index.html
```

Open in a browser to see per-module and per-package breakdowns.

---

## Quick Coverage Check from Command Line

```bash
grep -oP 'Total[^%]+\K[0-9]+(?=%)' target/site/jacoco-aggregate/index.html | head -1
```

Returns the overall instruction coverage percentage (e.g., `76`).

---

## Check Coverage Per Module

Each module also generates its own report:

```bash
open yawl-engine/target/site/jacoco/index.html
open yawl-integration/target/site/jacoco/index.html
```

---

## Coverage Thresholds

| Metric | Floor (blocks release) | Target | Command to check |
|--------|----------------------|--------|-----------------|
| Instruction coverage | 55% | 65% | PY-2 in `validate-release.sh` |
| Line coverage | 75% | 80% | JaCoCo `<rule>` in parent pom |
| Branch coverage | 55% | 70% | JaCoCo `<rule>` in parent pom |

If coverage is below the floor, the build fails:

```
[ERROR] Rule violated for bundle 'yawl-engine':
        instructions covered ratio is 0.48, but expected minimum is 0.55
```

---

## Which Modules Have Coverage Gaps

Modules with zero tests (no coverage measured):
- `yawl-scheduling`
- `yawl-control-panel`
- `yawl-webapps` (aggregator, expected)

Modules with minimal tests (< 10 test classes):
- `yawl-resourcing` (1 test class)
- `yawl-mcp-a2a-app` (9 test classes)

---

## Skip Coverage for Fast Builds

The `agent-dx` profile (used by `dx.sh`) skips JaCoCo for speed:

```bash
# Fast — no coverage
bash scripts/dx.sh all

# Slow — with coverage
mvn -T 1.5C clean verify -P coverage
```

Never skip coverage before tagging a release. PY-2 will catch it.

---

## Store the Receipt

After generating coverage, copy the summary to the receipt directory:

```bash
# Extract coverage % and write receipt
COV=$(grep -oP 'Total[^%]+\K[0-9]+(?=%)' target/site/jacoco-aggregate/index.html | head -1)
cat > receipts/gate-G_test-receipt.json << EOF
{
  "gate": "G_test",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "instruction_coverage_pct": $COV,
  "threshold_floor": 55,
  "threshold_target": 65,
  "status": "$([ $COV -ge 55 ] && echo PASS || echo FAIL)"
}
EOF
```

PY-1 checks this receipt in `validate-release.sh`.

---

## See Also

- [Quality Gates Reference](../../reference/quality-gates.md) — G_test gate specification
- [Run Release Validation](run-release-validation.md) — how PY-2 uses this artifact
- [JUnit 5 Quick Reference](../../reference/junit5.md) — test tagging and parallel execution
