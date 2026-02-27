# FMEA Risk Table

**Quadrant**: Reference | **Source of truth**: `docs/v6/DEFINITION-OF-DONE.md §5`

Failure Mode and Effects Analysis for the YAWL build and release system. 16 failure modes, each with Severity (S), Occurrence (O), Detection (D), and Risk Priority Number (RPN = S × O × D). Higher RPN = higher priority mitigation.

**Scale**: S/O/D each rated 1–10 where 10 is worst/most frequent/hardest to detect.

---

## Risk Priority Rankings

| Rank | ID | RPN | Failure Mode |
|------|----|-----|-------------|
| 1 | FM8 | **630** | Release documentation written before gate verification |
| 2 | FM13 | **432** | 48-hour staging stability test skipped |
| 3 | FM14 | **405** | Violation scan scope incomplete — files missed |
| 4 | FM11 | **392** | Coverage never measured before version tag |
| 5 | FM10 | **360** | Version promotion without evidence trail |
| 6 | FM12 | **336** | SBOM not generated before GA declaration |
| 7 | FM16 | **288** | Engine defects not in violation tracker |
| 8 | FM9 | **245** | GA date accelerated without change-control |
| 9 | FM2 | **224** | Dual-family class confusion |
| 10 | FM1 | **216** | Shared source path confusion |
| 11 | FM3 | **210** | Dependency version skew |
| 12 | FM15 | **120** | Beta/GA notes written together |
| 13 | FM6 | **144** | Gate bypass via skip flags |
| 14 | FM7 | **105** | Reactor order violation |
| 15 | FM5 | 84 | Test selection ambiguity |
| 16 | FM4 | 60 | Maven cached missing artifacts |

---

## Complete Failure Mode Table

| ID | Failure Mode | S | O | D | RPN | Mitigation | Poka-Yoke |
|----|-------------|---|---|---|-----|-----------|-----------|
| FM1 | Shared source path confusion | 9 | 8 | 3 | **216** | Read `shared-src.json` before editing shared source | `shared-src.json` + `15-shared-src-map.mmd` |
| FM2 | Dual-family class confusion | 8 | 7 | 4 | **224** | Read `dual-family.json` before editing dual-family classes | `dual-family.json` + `16-dual-family-map.mmd` |
| FM3 | Dependency version skew | 7 | 6 | 5 | **210** | Verify `deps-conflicts.json` after any dependency change | `deps-conflicts.json` + `17-deps-conflicts.mmd` |
| FM4 | Maven cached missing artifacts | 6 | 5 | 2 | 60 | Use `mvn dependency:resolve` to validate cache | `maven-hazards.json` |
| FM5 | Test selection ambiguity | 7 | 4 | 3 | 84 | Read `tests.json` before running targeted tests | `tests.json` + `30-test-topology.mmd` |
| FM6 | Gate bypass via skip flags | 8 | 3 | 6 | **144** | No RED skip flags permitted in CI/CD config | `gates.json` + `40-ci-gates.mmd` |
| FM7 | Reactor order violation | 5 | 3 | 7 | **105** | New modules placed per `reactor.json`; `dx.sh` ordering verified | `reactor.json` + `10-maven-reactor.mmd` |
| FM8 | Release docs written before gate verification | 10 | 7 | 9 | **630** | Run `validate-release.sh` before publishing any release notes | PY-1: `scripts/validation/validate-release.sh` receipt gate |
| FM9 | GA date accelerated without change-control | 7 | 5 | 7 | **245** | Architecture decision record required for date changes | PY-5: `.claude/decisions/` dated decision record |
| FM10 | Version promotion without evidence trail | 9 | 5 | 8 | **360** | Gate receipts must exist and be consistent before tagging | PY-1 + gate receipt consistency check |
| FM11 | Coverage never measured before version tag | 7 | 8 | 7 | **392** | JaCoCo aggregate report required; ≥55% line coverage | PY-2: JaCoCo artifact assertion |
| FM12 | SBOM not generated before GA declaration | 8 | 7 | 6 | **336** | CycloneDX SBOM + Grype scan required | PY-3: `grype sbom:target/bom.json --fail-on critical` |
| FM13 | 48-hour staging stability test skipped | 9 | 6 | 8 | **432** | Stability receipt must be present with ≥48h elapsed | PY-4: `receipts/stability-test-receipt.json` |
| FM14 | Violation scan scope incomplete | 9 | 5 | 9 | **405** | Scan must cover ≥90% of known Java files (Observatory count) | PY-6: file-count assertion in `hyper-validate.sh` batch |
| FM15 | Beta/GA notes written together | 5 | 6 | 4 | **120** | Separate commits for Beta and GA release notes | Process norm |
| FM16 | Engine defects not in violation tracker | 9 | 4 | 8 | **288** | Manual audit + PY-6 scan before each release tag | PY-6 + manual audit |

**S** = Severity (1=negligible, 10=catastrophic) | **O** = Occurrence (1=rare, 10=frequent) | **D** = Detection (1=obvious, 10=undetectable)

---

## Poka-Yoke Mechanisms (PY-1 through PY-6)

| ID | Mechanism | File | Verifies |
|----|-----------|------|---------|
| PY-1 | Gate receipt chain | `receipts/gate-*-receipt.json` | All 7 gates passed with timestamps |
| PY-2 | JaCoCo coverage floor | `receipts/jacoco-aggregate.xml` | Line coverage ≥ 55% |
| PY-3 | SBOM + Grype scan | `receipts/sbom-scan.json` | Zero critical CVEs |
| PY-4 | 48-hour stability | `receipts/stability-test-receipt.json` | Elapsed time ≥ 48 hours |
| PY-5 | Change-control record | `.claude/decisions/*.md` | Dated ADR for gate or schedule changes |
| PY-6 | Scan completeness | `receipts/observatory.json` | Files scanned ≥ 90% of Observatory count |

---

## DoD Requirements by RPN Band

### RPN ≥ 300 (critical — must be verified before any release)

- **FM8 (630)**: `scripts/validation/validate-release.sh` must exit 0
- **FM13 (432)**: `receipts/stability-test-receipt.json` must exist with `elapsed_hours ≥ 48`
- **FM14 (405)**: `hyper-validate.sh` batch scan must cover ≥90% of known Java files
- **FM11 (392)**: `receipts/jacoco-aggregate.xml` must show `≥55%` line coverage
- **FM10 (360)**: Gate receipts must be dated before the version tag
- **FM12 (336)**: `receipts/sbom-scan.json` must show zero critical CVEs
- **FM16 (288)**: Violation tracker must be audited before release tag

### RPN 100–299 (high — mitigations must be in place)

- **FM9 (245)**: Any schedule change needs a dated decision record in `.claude/decisions/`
- **FM2 (224)**: Agents must read `dual-family.json` before editing dual-family classes
- **FM1 (216)**: Agents must read `shared-src.json` before editing shared source
- **FM3 (210)**: Dependency changes must pass `deps-conflicts.json` verification
- **FM15 (120)**: Beta and GA release notes must be in separate commits
- **FM6 (144)**: No RED-risk skip flags in CI/CD pipeline

### RPN < 100 (medium — mitigations recommended)

- **FM7 (105)**: New modules must be placed in correct reactor position per `reactor.json`
- **FM5 (84)**: Use `tests.json` to verify test selection before targeted runs
- **FM4 (60)**: Use `mvn dependency:resolve` to validate Maven cache after network issues

---

## Observable Failure Signatures

| Failure Mode | What you observe | Where to look |
|-------------|-----------------|--------------|
| FM1 | Compile errors when building a module that uses shared source | `shared-src.json`, build log |
| FM2 | `ClassCastException` between YEngine/YStatelessEngine types | `dual-family.json` |
| FM3 | `NoSuchMethodError` or `ClassNotFoundException` at runtime | `deps-conflicts.json` |
| FM4 | `Could not resolve artifact` during Maven compile | `~/.m2/repository/` cache |
| FM5 | Tests that should fail pass (wrong test class selected) | `tests.json`, Surefire report |
| FM6 | Gate appears to pass but skip flag was active | CI log, `gates.json` |
| FM7 | Module A cannot resolve module B's classes during compile | `reactor.json`, build log |
| FM8 | Release notes published but G_security or G_test was never run | `receipts/` directory |
| FM9 | GA tagged three weeks before Beta quality criteria met | `.claude/decisions/` |
| FM10 | Version 6.1 tagged without JaCoCo report in receipts | `receipts/` directory |
| FM11 | Coverage drop discovered only after release tag | JaCoCo aggregate report |
| FM12 | Critical CVE discovered post-release | SBOM, Grype |
| FM13 | Production regression found 6 hours after deployment | `stability-test-receipt.json` |
| FM14 | Violation exists in production but scan reported clean | Observatory file count |
| FM15 | Beta release notes contain GA features | Release commit history |
| FM16 | Known engine defect shipped as it was never in tracker | Violation tracker |

---

## See Also

- [Quality Gates Reference](quality-gates.md) — gate predicates and commands
- [Build Sequences Reference](build-sequences.md) — FM7 reactor order
- [dx.sh CLI Reference](dx-sh.md) — how to run gates locally
- [How-To: Run Release Validation](../how-to/build/run-release-validation.md) — PY-1 through PY-4
