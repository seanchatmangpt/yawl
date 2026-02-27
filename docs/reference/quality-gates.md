# Quality Gates Reference

**Quadrant**: Reference | **Source of truth**: `docs/v6/DEFINITION-OF-DONE.md` + `docs/v6/diagrams/facts/gates.json`

Seven gates in a strict partial order. No gate can be bypassed. A commit is DONE only when every gate evaluates to PASS.

---

## Gate Order

```
G_compile  <  G_test  <  G_guard  <  G_analysis  <  G_security  <  G_documentation  <  G_release
```

Each gate is a necessary precondition for all gates to its right. Passing G_test does not mean G_guard is satisfied — each must be verified independently.

---

## Gate Summary

| Gate | Predicate | Threshold | Bypass Risk |
|------|-----------|-----------|-------------|
| G_compile | Reactor compiles, Java 25 | Zero errors | RED |
| G_test | All tests pass | 100% pass rate | RED |
| G_guard | No H-pattern violations | Zero violations | RED |
| G_analysis | Static analysis clean | Zero HIGH/CRITICAL | YELLOW |
| G_security | No critical CVEs, TLS 1.3 | Zero critical | RED |
| G_documentation | Package-info present | All new packages | YELLOW |
| G_release | All above + PY-1 through PY-6 | All receipts fresh | RED |

---

## G_compile — Compilation Gate

**What it checks**: The entire Maven reactor compiles without errors under Java 25.

**Command**:
```bash
# Fast (changed modules only):
bash scripts/dx.sh compile

# All 19 modules:
bash scripts/dx.sh compile all

# Full Maven:
mvn -T 1.5C clean compile
```

**Exit code**: 0 = PASS, non-zero = FAIL

**Failure causes**:
- Syntax errors
- Missing imports / classpath gaps
- Modules built out of topological order (FM7)
- Java version mismatch (must be Java 25)

**Skip flag risk**: There is no legal skip for G_compile. `-DskipTests` does not affect compilation.

---

## G_test — Test Gate

**What it checks**: All unit tests pass with 100% success rate (zero failures, zero errors).

**Command**:
```bash
# Fast (changed modules only):
bash scripts/dx.sh test

# All modules:
bash scripts/dx.sh test all

# Full Maven with Surefire:
mvn -T 1.5C clean test
```

**Thresholds**:
- Pass rate: **100%** (no partial credit)
- Failures tolerated: **0**
- Errors tolerated: **0**
- Skipped tests: permitted if annotated with `@Disabled` and a reason

**Skip flags (all RED risk)**:
| Flag | What it disables |
|------|-----------------|
| `-DskipTests=true` | Surefire + Failsafe (all tests) |
| `-DskipITs=true` | Failsafe integration tests |
| `-Dsurefire.skip=true` | Unit tests only |

**Test strategy**: Chicago TDD — real objects, H2 in-memory database, no Mockito leakage to production. See `docs/v6/HYPER_STANDARDS_VIOLATIONS_TRACKER.md` for current test count.

---

## G_guard — Anti-Pattern Guard Gate

**What it checks**: No Java source file in `src/` contains any of the 14 forbidden patterns (H1–H14).

**Enforcement**: `.claude/hooks/hyper-validate.sh` runs automatically on every `Write` or `Edit` tool use. The hook also runs in CI via pre-commit.

**Command** (manual batch scan):
```bash
# Single file:
bash .claude/hooks/hyper-validate.sh path/to/File.java

# Module:
bash .claude/hooks/hyper-validate.sh yawl-engine/src/main/java/

# All source:
bash .claude/hooks/hyper-validate.sh src/
# Must exit 0 with no output
```

**Threshold**: Zero violations. Any violation in any file causes exit 2.

**Pattern coverage**:

| ID | Pattern | Severity |
|----|---------|---------|
| H1 | TODO/FIXME/HACK comments | BLOCKER |
| H2 | Mock method/variable names | BLOCKER |
| H3 | Mock class names | BLOCKER |
| H4 | Mock mode boolean flags | BLOCKER |
| H5 | Empty string return `""` | BLOCKER |
| H6 | Null return with stub comment | BLOCKER |
| H7 | Empty void method body `{}` | BLOCKER |
| H8 | DUMMY_/PLACEHOLDER_ constants | BLOCKER |
| H9 | Silent fallback in catch | BLOCKER |
| H10 | Conditional mock path | BLOCKER |
| H11 | Fake `.getOrDefault()` default | BLOCKER |
| H12 | `if (true) return;` skip | BLOCKER |
| H13 | `log.warn("not implemented")` | BLOCKER |
| H14 | `import org.mockito.*` in `src/` | BLOCKER |

Full pattern details: [HYPER_STANDARDS Pattern Reference](hyper-standards.md)

---

## G_analysis — Static Analysis Gate

**What it checks**: SpotBugs, PMD, and Checkstyle report zero violations at configured severity thresholds.

**Command**:
```bash
mvn clean verify -P analysis
```

**Tool thresholds**:
| Tool | Threshold | Profile |
|------|-----------|---------|
| SpotBugs | Zero HIGH or CRITICAL | `analysis`, `ci`, `prod` |
| PMD | Zero rule violations | `analysis` |
| Checkstyle | Zero violations | `analysis` |

**Skip flag**: `-Dspotbugs.skip=true` — YELLOW risk (analysis still runs in CI).

**Note**: This gate is YELLOW bypass risk because it runs in a separate profile. CI enforces it but it does not block local `dx.sh` runs.

---

## G_security — Security Gate

**What it checks**: No known critical CVEs in the dependency graph; cryptographic standards met.

**Sub-predicates**:
1. SBOM generated: `mvn cyclonedx:makeAggregateBom` produces `target/bom.json`
2. Grype scan clean: `grype sbom:target/bom.json --fail-on critical` exits 0
3. TLS 1.3 enforced: No TLS 1.0 or 1.1 configuration
4. No hardcoded secrets in source

**Command**:
```bash
mvn clean verify -P security-audit
grype sbom:target/bom.json --fail-on critical
```

**Poka-yoke**: PY-3 in `scripts/validation/validate-release.sh` verifies the SBOM receipt exists and is dated within 24 hours of the release tag.

---

## G_documentation — Documentation Gate

**What it checks**: All new or modified Java packages have a `package-info.java` file with `@since` annotation.

**Requirements**:
1. Every new package created during this work unit has `package-info.java`
2. The file contains a Javadoc comment and `@since` version tag
3. 89 existing packages already have documentation — do not break them

**Verification**:
```bash
# Find packages missing package-info.java:
find . -type d -name "*.java" | \
  xargs -I{} test -f "{}/package-info.java" || echo "MISSING"
```

**Note**: YELLOW bypass risk — not enforced by a hook, enforced at code review.

---

## G_release — Release Gate

**What it checks**: All six prior gates pass AND the six poka-yoke receipts (PY-1 through PY-6) are present, valid, and dated within the release window.

**Command**:
```bash
bash scripts/validation/validate-release.sh receipts
```

**Poka-yoke checks**:

| ID | Check | Receipt File | Command |
|----|-------|-------------|---------|
| PY-1 | Gate receipts present | `receipts/gate-G_guard-receipt.json` | `validate-release.sh receipts` |
| PY-2 | JaCoCo ≥ 55% line coverage | `receipts/jacoco-aggregate.xml` | JaCoCo aggregate report |
| PY-3 | No critical CVEs in SBOM | `receipts/sbom-scan.json` | `grype sbom:target/bom.json` |
| PY-4 | 48-hour staging stability | `receipts/stability-test-receipt.json` | Manual staging test |
| PY-5 | Change-control decision | `.claude/decisions/*.md` | Architecture decision record |
| PY-6 | Violation scan completeness | `receipts/observatory.json` | `hyper-validate.sh` batch |

**FM8 risk**: Writing release documentation before gate verification — RPN=630, the highest in the FMEA. `validate-release.sh` exists specifically to prevent this.

---

## Gate Verification Receipt

When each gate passes, a JSON receipt is written to `receipts/`:

```json
{
  "gate": "G_guard",
  "timestamp": "2026-02-27T14:32:00Z",
  "result": "PASS",
  "files_scanned": 1247,
  "violations": 0,
  "command": "bash .claude/hooks/hyper-validate.sh src/"
}
```

Receipts are append-only. `validate-release.sh` reads them to confirm each gate has been run within the release window.

---

## Common Gate Failures

| Symptom | Likely gate | First fix to try |
|---------|-------------|-----------------|
| `mvn compile` errors | G_compile | Check dependency order in pom.xml |
| Test failures | G_test | Read Surefire report in `target/surefire-reports/` |
| Hook exits 2 | G_guard | Read violation output, fix pattern |
| SpotBugs violations | G_analysis | Run `mvn spotbugs:gui` for visual report |
| Grype critical | G_security | Update vulnerable dependency version |
| Missing package-info | G_documentation | Create `package-info.java` in new package |
| validate-release.sh fails | G_release | Read specific PY failure, run missing check |

---

## See Also

- [FMEA Risk Table](fmea-risk-table.md) — failure modes that bypass these gates
- [dx.sh CLI Reference](dx-sh.md) — how to invoke gates locally
- [HYPER_STANDARDS Pattern Reference](hyper-standards.md) — H1–H14 patterns for G_guard
- [Tutorial: Walk All 7 Quality Gates](../tutorials/11-quality-gate-sequence.md)
- [How-To: Run Release Validation](../how-to/build/run-release-validation.md)
