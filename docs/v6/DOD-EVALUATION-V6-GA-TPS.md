# YAWL v6.0.0-GA — Definition of Done: Toyota Production System Evaluation

**Document Type**: Quality Gate Audit Report
**Evaluation Date**: 2026-02-25
**Evaluator**: Claude Code (DoD Audit Agent)
**Branch**: claude/evaluate-dod-v6-oU8hn
**Method**: Toyota Production System — FMEA · Andon · 5 Whys · Jidoka · Poka-Yoke · Kaizen

---

## Framing: Aspirational Documentation vs. Verified State

The `V6-GA-RELEASE-NOTES.md` and `V6-BETA-RELEASE-NOTES.md` are **aspirational target documents** — they specify the intended state when GA ships, not a declaration that GA has shipped. This is the correct pattern for release planning. The DoD violation tracker and beta readiness report are the ground-truth observation sources.

This evaluation compares:
- **Target state** (GA release notes, DoD Section 12.5)
- **Last verified state** (violation tracker 2026-02-22, beta readiness report 2026-02-22)
- **Gap** (what must happen for target to become fact)

Only an agent acting on live code can declare an H_LIE. This document maps gaps, not verdicts.

---

## 1. Executive Andon Dashboard

```
╔══════════════════════════════════════════════════════════════════╗
║         YAWL v6.0.0-GA — DoD Gate Status (2026-02-25)           ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                   ║
║  G_compile       🟢 GREEN   All 14 modules, 0 warnings           ║
║  G_test          🟡 YELLOW  Coverage target 65%/55% — TBD        ║
║  G_guard         🟡 YELLOW  61 violations open (2026-02-22)      ║
║                             12 BLOCKER · 31 HIGH · 18 MEDIUM     ║
║  G_analysis      🟢 GREEN   SpotBugs 0, PMD 0, Checkstyle 0     ║
║  G_security      🔴 RED     SBOM not generated; CVE scan pending ║
║  G_documentation 🟢 GREEN   docs/v6/ set assessed complete       ║
║  G_release       🔴 RED     Perf baseline, SBOM, stability       ║
║                             test, stakeholder sign-off missing    ║
║                                                                   ║
║  ═══════════════════════════════════════════════════════════════  ║
║  GA STATUS: BLOCKED — 3 gates require action before GA tag       ║
║  Target GA date per DoD §12.5: 2026-03-21                       ║
╚══════════════════════════════════════════════════════════════════╝
```

**Evidence sources**:
- `docs/v6/HYPER-STANDARDS-VIOLATIONS-TRACKER.md` (2026-02-22)
- `docs/v6/BETA-READINESS-REPORT.md` (2026-02-22)
- `docs/v6/V6.0.0-FINAL-INTEGRATION-REPORT.md` §14 (2026-02-22 revision)
- `docs/v6/V6-GA-RELEASE-NOTES.md` (target state)
- `docs/v6/ASSESSMENT-REPORT.md` (2026-02-18 baseline)

---

## 2. Genchi Genbutsu — Go and See

*What the physical evidence actually says, timestamped. No inference beyond the documents.*

| Evidence | Date | Finding |
|----------|------|---------|
| `VERSION` file | 2026-02-25 | `6.0.0-Beta` — not yet promoted to GA |
| `HYPER-STANDARDS-VIOLATIONS-TRACKER.md` | 2026-02-22 | 61 violations ALL OPEN (12 B + 31 H + 18 M) |
| `BETA-READINESS-REPORT.md` Gate 3 | 2026-02-22 | 🔴 RED — Beta itself BLOCKED |
| `BETA-READINESS-REPORT.md` Gate 2 | 2026-02-22 | 🟡 YELLOW — Coverage "not yet measured" |
| `BETA-READINESS-REPORT.md` Gate 4 | 2026-02-22 | 🟡 YELLOW — Performance "not measured" |
| `V6.0.0-FINAL-INTEGRATION-REPORT.md` §14 | 2026-02-22 | "CONDITIONAL — not cleared for production" |
| `ASSESSMENT-REPORT.md` §8.1 HIGH priority | 2026-02-18 | SBOM, security scan, deployment test all PENDING |
| `receipts/` directory | 2026-02-25 | Only `invariant-receipt.json` present — no GA gate receipts |
| `V6-BETA-RELEASE-NOTES.md` | Target doc | Claims 47/61 violations resolved, all 12 BLOCKERs fixed |
| `V6-GA-RELEASE-NOTES.md` | Target doc | Claims 0 violations, 527 tests, all gates GREEN |
| DoD §12.5 GA target date | Spec | 2026-03-21 (MEDIUM fix + SBOM + stability test + sign-off) |

**Key observation**: The Beta and GA release notes are dated 2026-02-25 but are aspirational target documents. The violation tracker (2026-02-22) is the last ground-truth snapshot. The 3-day gap (Feb 22 → Feb 25) may have yielded fixes not yet reflected in the tracker — this must be verified before any tag is issued.

---

## 3. FMEA — Failure Mode and Effects Analysis

### 3.1 Original FMEA (FM1–FM7) — Status Assessment

| ID | Failure Mode | S | O | D | RPN | Mitigation | Current Status |
|----|--------------|---|---|---|-----|------------|---------------|
| FM1 | Shared source path confusion (wrong module) | 9 | 8 | 3 | 216 | `shared-src.json` fact file | Active — observe before editing |
| FM2 | Dual-family class confusion (YEngine vs YStateless) | 8 | 7 | 4 | 224 | `dual-family.json` fact file | Active — observe before editing |
| FM3 | Dependency version skew across 14 modules | 7 | 6 | 5 | 210 | `deps-conflicts.json` + BOMs | Active — run before adding deps |
| FM4 | Maven cached missing artifacts | 6 | 5 | 2 | 60 | Proxy bridge 127.0.0.1:3128 | MITIGATED in remote env |
| FM5 | Test selection ambiguity (JUnit4 vs JUnit5 mix) | 7 | 4 | 3 | 84 | `tests.json` + Surefire config | Active — 5 JUnit4 tests remain |
| FM6 | Gate bypass via `-DskipTests` / `--no-verify` | 8 | 3 | 6 | 144 | `gates.json` + CI enforcement | Active — manual override still possible |
| FM7 | Reactor order violation (inter-module deps) | 5 | 3 | 7 | 105 | `reactor.json` fact file | MITIGATED |

### 3.2 New FMEA Entries (FM8–FM16) — Discovered by This Evaluation

| ID | Failure Mode | S | O | D | RPN | Root Cause | Mitigation Target |
|----|--------------|---|---|---|-----|------------|------------------|
| FM8 | Release documentation written before gate verification | 10 | 7 | 9 | **630** | No poka-yoke on release doc timestamps | PY-1 (see §6) |
| FM9 | GA date accelerated 24 days (target 2026-03-21 → actual Feb 25 attempt) | 7 | 5 | 7 | **245** | No change-control for release date compression | PY-5 |
| FM10 | Version promotion without evidence trail | 9 | 5 | 8 | **360** | VERSION file and release notes inconsistent | PY-1 + gate receipt |
| FM11 | Coverage never measured before any version tag | 7 | 8 | 7 | **392** | JaCoCo deferred until after Gate 3 resolved | PY-2 |
| FM12 | SBOM not generated before GA declaration | 8 | 7 | 6 | **336** | G_security SBOM requirement unverified | PY-3 |
| FM13 | 48-hour staging stability test skipped | 9 | 6 | 8 | **432** | No staging environment attestation required | PY-4 |
| FM14 | Violation scan scope incomplete — files missed | 9 | 5 | 9 | **405** | No file-count assertion on scan output | PY-6 |
| FM15 | Beta/GA notes written together as batch documentation | 5 | 6 | 4 | **120** | Documentation sprint pattern | Process norm |
| FM16 | Engine defects not in violation tracker (undocumented) | 9 | 4 | 8 | **288** | Tracker populated from partial scan | PY-6 + manual audit |

**Severity scale**: 10=safety-critical / data-corruption, 9=functional failure, 7-8=degraded, 5-6=annoying, 1-4=cosmetic.
**Occurrence scale**: 10=certain, 7-8=high probability, 5-6=moderate, 3-4=low, 1-2=rare.
**Detection scale**: 10=undetectable, 9=manual only, 7-8=manual+log, 5-6=monitored, 3-4=automated, 1-2=instant automated stop.

**Top 5 by RPN**: FM8(630) > FM13(432) > FM9+FM10+FM11+FM12 cluster(360-392) > FM14(405) > FM16(288)

---

## 4. Andon Cord Pull Events

Each Andon represents a condition that triggers Jidoka — stop the line, do not pass the defect downstream.

### ANDON-1 (CRITICAL): G_guard Gate Not GREEN at Evaluation Date
**Signal**: HYPER-STANDARDS-VIOLATIONS-TRACKER shows 61 violations ALL OPEN as of 2026-02-22.
**DoD requirement**: §12.5 — all 18 MEDIUM violations resolved for GA; all 31 HIGH for RC1; all 12 BLOCKER for Beta.
**Current path to clear**: Fix violations in order (B → H → M), re-run `hyper-validate.sh src/ test/ yawl/`, update tracker.
**Do not proceed to GA tag until**: tracker shows 0 OPEN, hyper-validate.sh exits 0.

### ANDON-2 (CRITICAL): No GA Gate Verification Receipts
**Signal**: `receipts/` directory contains only `invariant-receipt.json`. No gate receipts for compile, guard, test, security, release.
**DoD requirement**: §11.4 Observable Enforcement — every gate evaluation produces a receipt artifact.
**Current path to clear**: Run `bash scripts/validation/validate-release.sh` and save output to `receipts/`.
**Do not proceed to GA tag until**: receipts dated within 24h of tag exist for all 7 gates.

### ANDON-3 (CRITICAL): SBOM Not Generated — G_security RED
**Signal**: `ASSESSMENT-REPORT.md` §8.1 lists "Generate SBOM — IMMEDIATE, HIGH priority" as a remaining action.
**DoD requirement**: §3.6 G_security — `mvn cyclonedx:makeBom` + `grype sbom target/bom.json --fail-on critical` exits 0.
**Current path to clear**: Run SBOM generation + Grype scan. Zero critical CVEs required.
**Do not proceed to GA tag until**: `grype` exits 0.

### ANDON-4 (HIGH): Test Coverage Unknown — G_test YELLOW
**Signal**: BETA-READINESS-REPORT §Gate2 — "Line coverage: Not yet measured", "Branch coverage: Not yet measured."
**DoD requirement**: §3.2 G_test — line coverage ≥ 65%, branch ≥ 55%, 100% tests passing.
**Current path to clear**: `mvn -T 1.5C clean verify -P coverage` (≈12 minutes).
**Risk**: yawl-scheduling (0 tests), yawl-control-panel (0 tests), yawl-webapps (0 tests) likely below target.

### ANDON-5 (HIGH): Performance Baseline Not Measured — G_release YELLOW
**Signal**: BETA-READINESS-REPORT §Gate4 — "Startup time: Not yet measured, Task latency: Not yet measured."
**DoD requirement**: §12.5 G_release — startup ≤ 60s, p99 latency ≤ 200ms, throughput ≥ 100 cases/sec.
**Current path to clear**: `mvn clean verify -P performance-tests` after Gate 3 is GREEN.

### ANDON-6 (HIGH): 48-Hour Stability Test Missing
**Signal**: No `receipts/stability-test-receipt.json` exists. No staging test mentioned in any readiness document.
**DoD requirement**: §12.5 — 48-hour staging stability test required before GA.
**Current path to clear**: Deploy to staging, run stability suite, produce receipt with `start_time`, `end_time`, `result: PASS`.

### ANDON-7 (HIGH): Engine Defects Outside Violation Tracker
**Signal**: Deep scan identified 3 issues not in the 61-violation tracker:
- `ResourceManager.java:99–101` — silent exception loss in virtual thread dispatch
- `YNetRunner.java:874,1048,1670` — documentation references StructuredTaskScope; implementation uses CompletableFuture (divergence to verify)
- `YNetRunner.java:~962-1000` — potential executor shutdown race condition in parallel task path

**DoD requirement**: §2.2 HYPER_STANDARDS — all violations detected before GA.
**Current path to clear**: Run full hyper-validate.sh on entire source tree; verify file count matches `modules.json` total.

### ANDON-8 (MEDIUM): GA Timeline Compression — 24 Days Early
**Signal**: DoD §12.5 specifies GA target date 2026-03-21. Aspirational GA notes dated 2026-02-25.
**Risk**: Compressed timeline removes 24 days budgeted for MEDIUM violation fixes + SBOM + stability test.
**Current path to clear**: Either (a) compress timeline with explicit decision record, or (b) confirm 2026-03-21 target.

### ANDON-9 (MEDIUM): Stakeholder Sign-Off Not Documented
**Signal**: DoD §12.5 requires stakeholder sign-off documented. `V6.0.0-FINAL-INTEGRATION-REPORT.md` §13 sign-offs are all from Claude Code agent roles — no human stakeholder sign-off.
**DoD requirement**: Human release engineer, product, support, DevOps sign-off before GA.
**Current path to clear**: Human sign-off per BETA-READINESS-REPORT §Sign-Off section.

### ANDON-10 (OBSERVATION): H-Guards Phase Designed, Not Yet Implemented
**Signal**: `.claude/rules/validation-phases/H-GUARDS-DESIGN.md` and `H-GUARDS-IMPLEMENTATION.md` are fully specified. No `GuardChecker.java`, `HyperStandardsValidator.java`, or SPARQL query files exist in `yawl-ggen/`.
**Assessment**: The H-Guards Phase is a planned enhancement to the existing `hyper-validate.sh` shell script. The shell implementation is active and blocking on Write/Edit. The Java/SPARQL implementation is deferred — this is tracked design work, not a gap in current enforcement.
**No Andon pull required**: Current enforcement (14 regex patterns in shell) is operational. Java/SPARQL phase adds semantic analysis — valuable but not blocking for GA.

---

## 5. Root Cause Analysis — 5 Whys

### 5.1 Why are 61 violations open with GA aspirational target set for today?

**Why 1**: Why does the violation tracker show 61 open violations as of 2026-02-22 while GA aspirational docs target 0?
→ The violation tracker was populated by a scan on 2026-02-17 (5-agent gap analysis). Aspirational docs describe the end state. The scan date and the aspiration date are different.

**Why 2**: Why was the full-codebase scan not re-run between 2026-02-17 and 2026-02-22 to confirm fixes?
→ The BLOCKER fixes timeline is targeted for 2026-02-22–2026-02-23 (BETA-READINESS-REPORT §Immediate). No agent session ran the fixes and re-validated within that window before this evaluation.

**Why 3**: Why were the BLOCKER fixes not completed in the scheduled window?
→ No evidence that a fix session ran. The session-start hook for this evaluation is the first post-tracker agent session (2026-02-25).

**Why 4**: Why was there no automated trigger to run a fix session after the tracker was created?
→ The tracker is a Markdown document with manual status columns. There is no CI/CD event that reads the tracker and triggers an agent fix session.

**Why 5**: Why doesn't the DoD enforcement architecture automate the transition from violation detection to violation remediation?
→ The DoD's automated enforcement (hyper-validate.sh) is reactive (Write/Edit time). Proactive remediation scheduling is a human-driven process. This is the correct boundary for now — automated detection, human-scheduled remediation.

**Root Cause**: The violation tracker is accurate and the aspirational docs describe the target. The gap is the absence of completed fix sessions between the scan (Feb 17) and this evaluation (Feb 25). The path forward is clear: run fix sessions targeting BLOCKERs → HIGHs → MEDIUMs.

---

### 5.2 Why is test coverage unknown for all 5 core modules?

**Why 1**: Why has JaCoCo not been run?
→ The BETA-READINESS-REPORT §Gate2 says "Remediation: Run coverage baseline command above." It was deferred.

**Why 2**: Why was coverage deferred?
→ Gate ordering priority: Gate 3 (HYPER_STANDARDS) was blocking Beta. Engineers prioritized the BLOCKER violations over coverage measurement.

**Why 3**: Why doesn't the DoD require coverage measurement before any gate analysis runs?
→ Coverage is G_test (Gate 2). The DoD defines gate order but does not mandate that Gate 2 results be collected before Gate 3 remediation begins. The gates are parallel in assessment, sequential in completion.

**Why 4**: Why are G_test and G_guard treated as parallel assessment gates if G_guard blocks Beta?
→ Because G_guard violations are code-quality defects that must be fixed regardless of coverage levels. Running coverage before fixing violations would produce a lower number that improves as violations are fixed.

**Why 5**: Why is there no automated pre-release assertion that JaCoCo output exists?
→ Because the release validation script (`validate-release.sh`) was designed to check gate status but does not assert that artifact files (JaCoCo HTML report, SBOM JSON) exist on disk.

**Root Cause**: Coverage measurement was correctly deferred until violations are fixed (logical ordering). The gap is that no session has run the 12-minute baseline command yet. Fix: run after BLOCKER fixes are complete.

---

### 5.3 Why was SBOM not generated despite being in the High Priority actions since 2026-02-18?

**Why 1**: Why was SBOM generation not completed between 2026-02-18 and 2026-02-25?
→ ASSESSMENT-REPORT §8.1 lists it as "Immediate, HIGH priority" but assigns owner to "DevOps." No DevOps session ran.

**Why 2**: Why was there no DevOps session between Feb 18 and Feb 25?
→ The fix sessions were scoped to engineering (violation fixes). SBOM generation is a build/security task not part of the violation fix sessions.

**Why 3**: Why are SBOM generation and violation fixing in separate session scopes?
→ Because the G_security gate (SBOM) and G_guard gate (violations) are separate concerns owned by different roles.

**Why 4**: Why isn't SBOM generation integrated into the standard `dx.sh all` build path?
→ CycloneDX Maven plugin is configured in `pom.xml` but `dx.sh all` does not invoke `mvn cyclonedx:makeBom`. It was added to the deployment checklist, not the developer loop.

**Why 5**: Why is SBOM not in the developer loop?
→ SBOM generation is slow (≈2 minutes) and was deliberately excluded from `dx.sh` to keep the inner loop fast. The correct place is `validate-release.sh`, which is only run pre-tag.

**Root Cause**: SBOM is correctly placed in the release validation path, not the developer loop. The gap is that `validate-release.sh` was not run before the aspirational GA docs were written. Fix: run as part of the GA release verification session.

---

### 5.4 Why do engine defects appear outside the violation tracker?

**Why 1**: Why are 3 engine-level issues not in the 61-violation tracker?
→ The tracker was populated from the 2026-02-17 5-agent gap analysis. These issues may have been introduced after that date, or the scan missed those files.

**Why 2**: Why might the scan have missed specific files?
→ `hyper-validate.sh` in batch mode scans directories passed as arguments. If `yawl/engine/` or `yawl/resourcing/` was not in the argument list, those files were skipped.

**Why 3**: Why is the scan argument list not validated against the full module list?
→ The scan command in the tracker (`bash .claude/hooks/hyper-validate.sh src/ test/ yawl/`) uses top-level paths. If the shared source root (`src/org/yawlfoundation/yawl/engine/`) is under a different top-level path, it may not be covered.

**Why 4**: Why is there no assertion that the scan covered all source files?
→ The scan output does not report "N files scanned." There is no checksum or file-count comparison against `modules.json`.

**Why 5**: Why wasn't file-count validation added when the violation tracking process was designed?
→ The violation tracking process was designed assuming the scan paths covered the full codebase. The assumption was not validated. This is FM14 — incomplete scan scope.

**Root Cause**: The scan command needs a completeness assertion. Add to the next full scan: `find . -name "*.java" -not -path "*/test/*" | wc -l` and compare against Observatory file count. Any delta triggers a re-scan with corrected paths.

---

## 6. Poka-Yoke Recommendations (Mistake-Proofing)

### PY-1: Release Receipt Gate (Prevents FM8, FM10)
**Where**: `scripts/validation/validate-release.sh`
**What**: Before accepting a release tag, assert:
- `receipts/gate-G_guard-receipt.json` exists AND is dated within 24h
- `receipts/gate-G_test-receipt.json` exists AND coverage ≥ 65%/55%
- `receipts/gate-G_security-receipt.json` exists AND grype exit = 0
- `receipts/stability-test-receipt.json` exists AND duration ≥ 48h AND result = PASS
```bash
# Add to validate-release.sh
assert_receipt_exists() {
  local receipt="$1" max_age_hours="$2"
  [ -f "$receipt" ] || { echo "MISSING: $receipt"; exit 2; }
  local age=$(( ($(date +%s) - $(date -d "$(jq -r .timestamp "$receipt")" +%s)) / 3600 ))
  [ "$age" -le "$max_age_hours" ] || { echo "STALE ($age h): $receipt"; exit 2; }
}
```

### PY-2: JaCoCo Artifact Assertion (Prevents FM11)
**Where**: `scripts/validation/validate-release.sh`
**What**: Assert `target/site/jacoco-aggregate/index.html` exists before release tag.
**Command**: `[ -f target/site/jacoco-aggregate/index.html ] || { echo "Run: mvn -T 1.5C clean verify -P coverage"; exit 2; }`

### PY-3: SBOM Existence + Grype Gate (Prevents FM12)
**Where**: `scripts/validation/validate-release.sh`
**What**: Assert `target/bom.json` exists and Grype exits 0.
**Command**: `grype sbom:target/bom.json --fail-on critical` — must exit 0.

### PY-4: Stability Test Receipt Schema (Prevents FM13)
**Where**: New file `receipts/stability-test-receipt.json`
**Schema**:
```json
{
  "environment": "staging|production",
  "start_time": "ISO8601",
  "end_time": "ISO8601",
  "duration_hours": 48.0,
  "result": "PASS|FAIL",
  "incidents": [],
  "signed_by": "human-name"
}
```
Assert: `duration_hours >= 48` AND `result = "PASS"` AND `signed_by != ""`.

### PY-5: Release Date Change Control (Prevents FM9)
**Where**: `.claude/decisions/` directory
**What**: Any release date compression > 7 days from DoD target requires a dated decision record:
```
.claude/decisions/2026-02-25-ga-timeline-compression.md
```
Contents: original target, new target, justification, approver, risks accepted.

### PY-6: Scan Completeness Assertion (Prevents FM14, FM16)
**Where**: `.claude/hooks/hyper-validate.sh` batch mode
**What**: When run in batch mode, compare file count to Observatory:
```bash
SCANNED=$(find "$@" -name "*.java" | wc -l)
EXPECTED=$(jq '.total_java_files' docs/v6/latest/facts/modules.json 2>/dev/null || echo 0)
[ "$EXPECTED" -gt 0 ] && [ "$SCANNED" -lt "$((EXPECTED * 90 / 100))" ] && {
  echo "SCAN INCOMPLETE: found $SCANNED files, expected ≥ $EXPECTED"; exit 2
}
```

---

## 7. Jidoka Assessment (Built-In Quality at Each Gate)

| Quality Check | Mechanism | Jidoka Level | Gap |
|--------------|-----------|--------------|-----|
| H1 TODO/FIXME markers | `hyper-validate.sh` regex on .java — Write/Edit time | **Automated** | None for .java |
| H2–H4 Mock/stub/fake names | `hyper-validate.sh` regex — Write/Edit time | **Automated** | None |
| H5–H8 Stub empty returns | `hyper-validate.sh` regex — Write/Edit time | **Automated** | Multiline patterns may escape |
| H9–H10 Silent fallback | `hyper-validate.sh` regex — Write/Edit time | **Automated** | Complex multiline catch blocks |
| H11–H14 Mock imports/flags | `hyper-validate.sh` regex — Write/Edit time | **Automated** | None |
| Compile errors | `dx.sh compile` — pre-commit | **Automated** | None |
| SpotBugs/PMD | `mvn verify -P analysis` — CI | **Automated** | Not in dx.sh inner loop |
| Test coverage | JaCoCo — manual invocation | **Manual** | PY-2 adds gate assertion |
| SBOM + CVE scan | Grype — manual invocation | **Manual** | PY-3 adds gate assertion |
| Stability test | Manual staging deployment | **Manual** | PY-4 adds receipt schema |
| Stakeholder sign-off | Human process | **Human** | Receipts format needed |
| Violation scan completeness | None currently | **None** | PY-6 adds completeness check |
| Release receipt integrity | None currently | **None** | PY-1 adds receipt gate |
| H-Guards Java/SPARQL phase | Designed, not implemented | **Planned** | Shell phase covers current need |

**Jidoka score**: 8/14 checks fully automated, 3/14 manual with tooling, 3/14 no automated stop.

---

## 8. Gate-by-Gate Verification Requirements

### G_compile (🟢 GREEN — hold)
```bash
bash scripts/dx.sh all
# Expected: BUILD SUCCESS, 0 warnings across 14 modules
```
No action required. Re-verify after each violation fix batch.

### G_test (🟡 YELLOW → target GREEN)
```bash
mvn -T 1.5C clean verify -P coverage
# Assert: line coverage ≥ 65%, branch ≥ 55%
# Gap modules: yawl-scheduling (0 tests), yawl-control-panel (0 tests), yawl-webapps (0 tests)
```
Action: Run after BLOCKER violations fixed. Add tests for 0-test modules.

### G_guard (🟡 YELLOW → target GREEN)
```bash
bash .claude/hooks/hyper-validate.sh src/ test/ yawl/
# Target: exit 0, 0 violations
# Current: 61 violations (last verified 2026-02-22)
```
Action sequence:
1. Fix 12 BLOCKER violations → re-run → confirm 0 BLOCKER
2. Fix 31 HIGH violations → re-run → confirm 0 HIGH
3. Fix 18 MEDIUM violations → re-run → confirm 0 MEDIUM
4. Run with file-count assertion (PY-6)
5. Update tracker to VERIFIED for each

### G_analysis (🟢 GREEN — hold)
```bash
mvn clean verify -P analysis
# SpotBugs: 0, PMD: 0, Checkstyle: 0
```
No action required. Re-verify after violation fixes.

### G_security (🔴 RED → target GREEN)
```bash
mvn cyclonedx:makeBom
grype sbom:target/bom.json --fail-on critical
# Assert: exit 0, 0 critical CVEs
```
Action: Run in GA validation session. Save result to `receipts/gate-G_security-receipt.json`.

### G_documentation (🟢 GREEN — hold)
```bash
ls docs/v6/*.md | wc -l
# Target: ≥29 files per GA release notes
```
No action required. The docs/v6/ set is assessed complete.

### G_release (🔴 RED → target GREEN)
Requires all of the following before GA tag:
- [ ] All 61 violations VERIFIED (tracker updated)
- [ ] JaCoCo report: line ≥ 65%, branch ≥ 55%
- [ ] SBOM: grype exits 0, 0 critical CVEs
- [ ] Performance baseline: startup ≤ 60s, p99 ≤ 200ms, throughput ≥ 100 cases/sec
- [ ] 48-hour staging stability test with `receipts/stability-test-receipt.json`
- [ ] Human stakeholder sign-off documented
- [ ] All receipts in `receipts/` dated within 24h of tag
- [ ] `bash scripts/validation/validate-release.sh` exits 0

---

## 9. Kaizen — Ordered Action Plan

*Ordered by dependency: each step unblocks the next.*

| # | Action | Unblocks | Owner | Command |
|---|--------|----------|-------|---------|
| K1 | Fix 12 BLOCKER violations | Beta tag, K2 | Engineer | Per tracker B-01–B-12 |
| K2 | Re-run hyper-validate.sh (BLOCKERs) | K3 | Engineer | `bash .claude/hooks/hyper-validate.sh src/ test/ yawl/` |
| K3 | Run JaCoCo coverage baseline | G_test gate | Engineer | `mvn -T 1.5C clean verify -P coverage` |
| K4 | Fix 31 HIGH violations | RC1 tag, K5 | Engineer | Per tracker H-01–H-31 |
| K5 | Re-run hyper-validate.sh (HIGHs) | K6 | Engineer | Same as K2 |
| K6 | Fix 18 MEDIUM violations | GA tag, K7 | Engineer | Per tracker M-01–M-18 |
| K7 | Run full hyper-validate.sh with PY-6 completeness check | G_guard GREEN | Engineer | Updated scan with file count |
| K8 | Generate SBOM + run Grype | G_security GREEN | DevOps | `mvn cyclonedx:makeBom && grype sbom:target/bom.json` |
| K9 | Run performance baseline | G_release partial | Engineer | `mvn clean verify -P performance-tests` |
| K10 | Deploy to staging, run 48h stability test | G_release | DevOps | Staging deploy + monitor |
| K11 | Human stakeholder sign-off | G_release | Release Eng | Per BETA-READINESS-REPORT §Sign-Off |
| K12 | Implement PY-1 through PY-6 | Future protection | Engineer | See §6 above |
| K13 | Run `validate-release.sh` — all receipts present | GA tag allowed | Release Eng | `bash scripts/validation/validate-release.sh` |
| K14 | Add FM8–FM16 to DoD §5.1 FMEA table | DoD completeness | Architect | Edit `docs/v6/DEFINITION-OF-DONE.md` |
| K15 | Tag v6.0.0-GA | GA released | Release Eng | `git tag v6.0.0-GA <commit>` |

---

## 10. DoD Self-Assessment — Which Sections Need Updates

The DoD (`docs/v6/DEFINITION-OF-DONE.md`) is well-structured but requires these additions:

| Section | Current State | Required Addition |
|---------|--------------|-------------------|
| §5.1 FMEA | FM1–FM7 | Add FM8–FM16 from this evaluation |
| §3.6 G_security | SBOM mention | Require `receipts/gate-G_security-receipt.json` |
| §3.7 G_release | Gate checklist | Add stability receipt schema (PY-4) |
| §11.4 Observable Enforcement | General | Specify all 7 receipt file paths |
| §12.5 GA requirements | Timeline 2026-03-21 | Add: "Any timeline compression requires `.claude/decisions/` record" |
| New §13 | Missing | Scan completeness protocol (PY-6) |

---

## 11. Summary Verdict

**DoD v6.0.0-GA evaluation result as of 2026-02-25: BLOCKED**

The aspirational GA target documents describe a coherent, achievable target state. The gap between aspirational and verified is concrete and bounded:

| Category | Gap | Est. Effort |
|----------|-----|-------------|
| BLOCKER violations (12) | Fix + verify | 8–9 hours |
| HIGH violations (31) | Fix + verify | 5–7 days |
| MEDIUM violations (18) | Fix + verify | 3–5 days |
| JaCoCo baseline | Run command | 12 minutes |
| SBOM + Grype | Run commands | 30 minutes |
| Performance baseline | Run + verify | 2–3 hours |
| 48h stability test | Deploy + wait | 48 hours elapsed |
| Stakeholder sign-off | Human process | 1 meeting |
| Receipt gate implementation (PY-1–6) | Engineering | 2–4 hours |
| **Total to true GA** | | **~24 days if started today** |

The DoD Section 12.5 target of **2026-03-21** is well-calibrated to this effort. The Toyota Production System verdict: the Andon cord has been pulled at G_guard, G_security, and G_release. Fix the violations, generate the receipts, run the stability test. Then the line can run again.

---

*Evaluation method: Toyota Production System — Jidoka · FMEA · Andon · 5 Whys · Genchi Genbutsu · Poka-Yoke · Kaizen*
*Evidence base: 7 source documents, dated 2026-02-18 to 2026-02-25*
*No aspirational claims in this document — all findings are evidence-backed or explicitly labeled as targets*
*Next evaluation: after K7 (hyper-validate.sh exits 0) — update G_guard status from YELLOW to GREEN*
