# YAWL v6 Definition of Done

**A Formal Specification of Completion Criteria for the YAWL Workflow Engine**

Version 1.0 | February 2026 | Classification: Engineering Standard

---

## Abstract

This document establishes the formal Definition of Done (DoD) for the YAWL v6 workflow
engine. Drawing from the mathematical specification framework (`A = mu(O)`), Toyota
Production System principles (Jidoka), FMEA risk analysis, and the Observatory instrument
protocol, it defines the necessary and sufficient conditions under which any unit of
work -- commit, feature, release -- is considered complete. The DoD is not advisory; it
is enforced by automated hooks, quality gates, and agent coordination protocols.

---

## 1. Foundational Axioms

### 1.1 The Completion Function

Let `D` be the Definition of Done function:

```
D: Work -> {DONE, NOT_DONE}

D(w) = DONE  iff  forall g in G: g(w) = PASS
D(w) = NOT_DONE  iff  exists g in G: g(w) = FAIL
```

Where `G` is the set of all quality gates defined in this document. There is no partial
credit. A unit of work is either DONE or NOT_DONE.

### 1.2 The Guard Invariant

From `CLAUDE.md` Section H:

```
H = {TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie}
PostToolUse(Write|Edit) -> guard(H) -> BOTTOM if H intersection content != empty
```

**Axiom**: No artifact may contain any element of H. This is enforced at write-time by
`.claude/hooks/hyper-validate.sh`, which checks 14 anti-patterns and blocks on violation
(exit code 2).

### 1.3 The Quality Invariant

From `CLAUDE.md` Section Q:

```
Q = {real_impl OR throw UnsupportedOperationException, no_mock, no_stub, no_fallback, no_lie}
```

Every public method either performs real work or throws `UnsupportedOperationException`
with a descriptive message explaining what is needed for implementation.

---

## 2. Gate Taxonomy

The Definition of Done comprises seven gate categories, arranged in a strict partial order:

```
G_compile  <  G_test  <  G_guard  <  G_analysis  <  G_security  <  G_documentation  <  G_release
```

Each gate is a predicate that must evaluate to PASS.

### 2.1 Gate Summary Table

| Gate | Predicate | Enforcement | Bypass Risk |
|------|-----------|-------------|-------------|
| G_compile | `mvn -T 1.5C clean compile` exits 0 | Pre-commit | RED |
| G_test | `mvn -T 1.5C clean test` exits 0, 100% pass | Pre-commit | RED |
| G_guard | 14 anti-patterns absent from all `.java` files | PostToolUse hook | RED |
| G_analysis | SpotBugs, PMD, Checkstyle zero violations | Profile `analysis` | YELLOW |
| G_security | SBOM clean, no critical CVEs, TLS 1.3 enforced | Profile `security-audit` | RED |
| G_documentation | Package-info exists for all new packages | Code review | YELLOW |
| G_release | All above + integration tests + performance baseline | CI/CD pipeline | RED |

---

## 3. Gate Specifications

### 3.1 G_compile -- Compilation Gate

**Predicate**: The entire Maven reactor compiles without errors under Java 25.

**Command**:
```bash
mvn -T 1.5C clean compile
```

**Conditions**:
1. All 14 modules compile in reactor order:
   `yawl-parent -> yawl-utilities -> yawl-elements -> yawl-authentication -> yawl-engine
   -> yawl-stateless -> yawl-resourcing -> yawl-worklet -> yawl-scheduling -> yawl-security
   -> yawl-integration -> yawl-monitoring -> yawl-webapps -> yawl-control-panel`
2. No compiler warnings treated as errors (when `-Xlint:all` is active)
3. Parallel compilation via `-T 1.5C` succeeds (no module dependency cycles)
4. Target time: < 90 seconds (clean build)

**Failure Response**: Fix compilation errors before any other work proceeds.

### 3.2 G_test -- Test Gate

**Predicate**: All unit tests pass with 100% success rate.

**Command**:
```bash
mvn -T 1.5C clean test
```

**Conditions**:
1. Surefire executes tests across 7 modules:
   `yawl-utilities, yawl-elements, yawl-engine, yawl-stateless, yawl-resourcing,
   yawl-integration, yawl-monitoring`
2. Test patterns: `**/*Test.java`, `**/*Tests.java`, `**/*TestSuite.java`
3. JUnit 5.14.0 LTS with parallel method-level execution enabled
4. Zero test failures, zero test errors
5. No skipped tests without `@Disabled("reason")` annotation with justification

**Failure Response**: Fix failing tests. Do not use `-DskipTests`.

### 3.3 G_guard -- Anti-Pattern Guard Gate

**Predicate**: No Java source file contains any of the 14 forbidden patterns.

**Enforcement**: `.claude/hooks/hyper-validate.sh` runs on every Write/Edit operation.

**Forbidden Patterns** (from HYPER_STANDARDS.md):

| ID | Pattern | Detection |
|----|---------|-----------|
| H1 | Deferred work markers | `TODO, FIXME, XXX, HACK, LATER, FUTURE` |
| H2 | Mock method names | `mockFetch(), stubValidation()` |
| H3 | Mock class names | `class MockService` |
| H4 | Mock mode flags | `boolean useMockData = true` |
| H5 | Empty string returns | `return "";` |
| H6 | Null stub returns | `return null; // stub` |
| H7 | No-op methods | `public void method() { }` |
| H8 | Placeholder constants | `DUMMY_CONFIG, PLACEHOLDER_VALUE` |
| H9 | Silent fallbacks | `catch (e) { return mockData(); }` |
| H10 | Conditional mocks | `if (isTestMode) return mock();` |
| H11 | Fake defaults | `.getOrDefault(key, "test_value")` |
| H12 | Logic skipping | `if (true) return;` |
| H13 | Log instead of throw | `log.warn("not implemented")` |
| H14 | Mock imports in src/ | `import org.mockito.*` |

**Resolution Options**:
- Implement the real version with real dependencies
- Throw `UnsupportedOperationException("descriptive message")`

### 3.4 G_analysis -- Static Analysis Gate

**Predicate**: Static analysis tools report zero violations at configured thresholds.

**Command**:
```bash
mvn clean verify -P analysis
```

**Quality Gates** (from `facts/gates.json`):

| Tool | Phase | Threshold | Profile |
|------|-------|-----------|---------|
| SpotBugs | verify | Zero HIGH/CRITICAL issues | `analysis`, `ci`, `prod` |
| PMD | verify | Zero rule violations | `analysis` |
| Checkstyle | verify | Zero violations | `analysis` |
| JaCoCo | verify | 75% line coverage | `analysis`, `ci`, `prod`, `sonar` |
| Enforcer | validate | Active by default | All builds |

**Active By Default**: JaCoCo and Enforcer run on every build. SpotBugs, PMD, and
Checkstyle require the `analysis` profile.

**Skip Flag Risk Matrix** (from `facts/gates.json`):

| Flag | Risk | What It Disables |
|------|------|------------------|
| `-DskipTests=true` | RED | Surefire + Failsafe |
| `-DskipITs=true` | RED | Failsafe (integration tests) |
| `-Denforcer.skip=true` | RED | Maven Enforcer |
| `-Dspotbugs.skip=true` | YELLOW | SpotBugs |
| `-Dpmd.skip=true` | YELLOW | PMD |
| `-Dcheckstyle.skip=true` | YELLOW | Checkstyle |

RED flags are forbidden in CI/CD. YELLOW flags are permitted during local development
but forbidden in merge pipelines.

### 3.5 G_security -- Security Gate

**Predicate**: No known critical vulnerabilities; cryptographic and transport security
standards met.

**Sub-Predicates** (from SECURITY-CHECKLIST-JAVA25.md):

| ID | Requirement | Verification |
|----|-------------|-------------|
| S1 | Java 25.0.2+ with quarterly patches | `java -version` |
| S2 | No deprecated-for-removal APIs | `jdeprscan --for-removal` |
| S3 | TLS 1.3 enforced | JVM flag `-Djdk.tls.disabledAlgorithms` |
| S4 | RSA 3072-bit minimum, AES-GCM only | Crypto configuration audit |
| S5 | No SecurityManager usage (removed JDK 24+) | `grep -r SecurityManager src/` |
| S6 | Parameterized SQL only | Code review: no string concatenation in queries |
| S7 | No ObjectInputStream on untrusted input | `grep -r ObjectInputStream src/` |
| S8 | SBOM generated and scanned | `mvn cyclonedx:makeBom && grype sbom` |
| S9 | No sensitive data in logs | Code review: no passwords, keys, PII |
| S10 | No stack traces exposed to end users | Generic error messages with correlation IDs |

**OWASP Top 10 Alignment**:
- A01 (Broken Access Control): Spring Security or custom RBAC
- A03 (Supply Chain): SBOM + Grype scanning
- A05 (Security Misconfiguration): Hardened JVM flags
- A07 (Authentication Failures): Explicit authorization checks
- A09 (Security Logging): Correlation IDs, no PII
- A10 (Exception Handling): No information leakage

### 3.6 G_documentation -- Documentation Gate

**Predicate**: All new or modified packages have adequate documentation.

**Requirements**:
1. Every Java package has a `package-info.java` file (89 packages documented)
2. Package-info includes: purpose, entry points, key differences, constraints
3. Public API methods have Javadoc that matches behavior (no lies -- see Q invariant)
4. Architecture decisions documented in `.claude/` reference files

**The 80/20 Rule**: Documentation captures the 20% of knowledge that answers 80% of
questions. Entry points, boundaries, and constraints -- not exhaustive API references.

### 3.7 G_release -- Release Gate

**Predicate**: All previous gates pass AND release-specific criteria are met.

**Additional Requirements**:
1. All gates G_compile through G_documentation: PASS
2. Integration tests pass: `mvn verify -P ci` (Failsafe with `**/*IT.java` patterns)
3. Performance baseline: No regression > 10% from previous release
4. FMEA risk review: All failure modes with RPN > 200 have mitigations (see Section 5)
5. Observatory facts are current: `receipts/observatory.json` SHA256 matches
6. Schema validation: `xmllint --schema schema/YAWL_Schema4.0.xsd` passes

---

## 4. Enforcement Architecture

### 4.1 Hook-Based Enforcement

The DoD is enforced at three temporal points:

```
                     Write/Edit             Pre-Commit              CI/CD Pipeline
                        |                      |                         |
                   PostToolUse              Ω Workflow               Merge Gate
                        |                      |                         |
                 hyper-validate.sh      compile + test           All 7 gates
                        |                      |                         |
                  G_guard (14 patterns)   G_compile + G_test    G_compile...G_release
                        |                      |                         |
                    exit 2 = BLOCK       exit 1 = BLOCK         FAIL = REJECT PR
```

**Write-Time** (immediate): `hyper-validate.sh` blocks forbidden patterns at the moment
of file creation or modification. Latency: < 1 second.

**Pre-Commit** (Omega workflow from CLAUDE.md):
```
1. mvn clean compile              (G_compile)
2. mvn clean test                 (G_test)
3. git add <specific files>       (no git add .)
4. git commit -m "message"        (with session URL)
5. git push -u origin branch      (feature branch only)
```

**CI/CD Pipeline** (GitHub Actions):
- Fast Build job: compile + test (~2.5 minutes)
- Analysis job: SpotBugs + PMD + JaCoCo (~5 minutes, main branch only)
- Security job: SBOM generation + vulnerability scanning (~2 minutes)

### 4.2 Agent Coordination Protocol

From `CLAUDE.md` Section tau:

```
Task(a_1, ..., a_n) in single_message AND max_agents = 8
topology = hierarchical(mu) | mesh(integration)
```

**Agent Roles in DoD Enforcement**:

| Agent | Gate Responsibility | Tools |
|-------|-------------------|-------|
| engineer | G_compile, G_guard (writes code) | Read, Edit, Write, Bash, Grep, Glob |
| validator | G_compile, G_test (verifies builds) | Bash, Read |
| reviewer | G_guard, G_analysis (code quality) | Read, Grep, Bash |
| tester | G_test (writes/runs tests) | Read, Edit, Write, Bash, Grep, Glob |
| architect | G_documentation (design decisions) | Read, Write, Grep, Glob |
| integrator | G_release (cross-module coordination) | Read, Edit, Write, Bash, Grep, Glob |
| prod-val | G_security, G_release (production checks) | Read, Bash, Grep, Glob |
| perf-bench | G_release (performance baselines) | Read, Bash, Grep, Glob |

**Parallel Execution**: Independent gates run concurrently. Example: G_compile and
G_guard can be verified in parallel since they have no data dependency.

**Sequential Phases**: Dependent gates run in order. G_test requires G_compile.
G_release requires all preceding gates.

---

## 5. Risk Management Integration

### 5.1 FMEA Risk Priority Numbers

The Observatory FMEA identifies 7 failure modes. The DoD requires that all failure modes
with RPN > 100 have active mitigations:

| ID | Failure Mode | S | O | D | RPN | Mitigation Instrument |
|----|-------------|---|---|---|-----|-----------------------|
| FM1 | Shared Source Path Confusion | 9 | 8 | 3 | **216** | `shared-src.json` + `15-shared-src-map.mmd` |
| FM2 | Dual-Family Class Confusion | 8 | 7 | 4 | **224** | `dual-family.json` + `16-dual-family-map.mmd` |
| FM3 | Dependency Version Skew | 7 | 6 | 5 | **210** | `deps-conflicts.json` + `17-deps-conflicts.mmd` |
| FM4 | Maven Cached Missing Artifacts | 6 | 5 | 2 | 60 | `maven-hazards.json` |
| FM5 | Test Selection Ambiguity | 7 | 4 | 3 | 84 | `tests.json` + `30-test-topology.mmd` |
| FM6 | Gate Bypass via Skip Flags | 8 | 3 | 6 | **144** | `gates.json` + `40-ci-gates.mmd` |
| FM7 | Reactor Order Violation | 5 | 3 | 7 | **105** | `reactor.json` + `10-maven-reactor.mmd` |

**S** = Severity, **O** = Occurrence, **D** = Detection (1 = best, 10 = worst)

**DoD Requirement**: Before any release, verify that:
1. FM1 and FM2 (RPN > 200): Agents must read `shared-src.json` and `dual-family.json`
   before editing shared source or dual-family classes
2. FM3 (RPN = 210): Dependency changes must pass `deps-conflicts.json` verification
3. FM6 (RPN = 144): No RED skip flags in CI/CD pipeline configuration
4. FM7 (RPN = 105): New modules placed in correct reactor position per `reactor.json`

### 5.2 Observatory Verification

The Observatory (`Psi`) provides deterministic codebase facts:

```
Psi.verify: receipts/observatory.json -> SHA256 hashes for all outputs
Stale? Re-run: bash scripts/observatory/observatory.sh
```

**DoD Requirement**: Observatory facts must be current (< 24 hours old for release,
< 1 week for feature branches). Staleness check:

```bash
# Compare receipt hash against current INDEX.md
sha256sum docs/v6/latest/INDEX.md
# vs receipts/observatory.json -> outputs.index_sha256
```

---

## 6. Architecture Compliance

### 6.1 Java 25 Pattern Adoption

The DoD for new code requires adoption of Java 25 patterns where applicable:

| Pattern | Where Applied | Effort | Priority |
|---------|--------------|--------|----------|
| Virtual Threads | Agent discovery loops, case execution | 1 day | Phase 1 |
| Structured Concurrency | Work item batch processing | 2 days | Phase 1 |
| Sealed State Machine | `YWorkItemStatus` hierarchy | 3 days | Phase 1 |
| CQRS for Interface B | `InterfaceBClient` split | 2 days | Phase 2 |
| Record Event Hierarchy | `YEvent` sealed records | 3 days | Phase 1 |
| Module System | Boundary enforcement | 1 week | Phase 3 |
| Reactive Event Pipeline | `YAnnouncer` predicate filtering | 2 days | Phase 2 |
| Constructor Injection | Replace `YEngine.getInstance()` | 1 week | Phase 4 |

**New code** must use virtual threads for concurrent operations, records for immutable
data transfer objects, and sealed interfaces for domain hierarchies.

**Existing code** is migrated according to the phase plan. Phase 1 patterns are
prerequisites for v6 release.

### 6.2 Module Architecture

The 14-module reactor (from `facts/modules.json`) uses two source strategies:

| Strategy | Modules | Characteristic |
|----------|---------|---------------|
| `full_shared` | utilities, elements, engine, stateless, security | Share `../src` (736 files visible) |
| `package_scoped` | authentication, resourcing, worklet, scheduling, integration, monitoring, control-panel | Scoped to own package subtree |
| `standard` | webapps | Standard `src/main/java` layout |

**DoD Requirement**: New source files must be placed in the correct module per the source
strategy. Use `facts/shared-src.json` to determine ownership before creating files in
shared source roots.

---

## 7. Build Performance Targets

Performance is a DoD criterion. Builds that exceed time budgets indicate architectural
problems.

| Metric | Baseline (v5.2) | Target (v6) | Method |
|--------|-----------------|-------------|--------|
| Clean compile | 180s | < 90s | `-T 1.5C` parallel |
| Unit tests | 60s | < 30s | JUnit 5 parallel execution |
| Full verify | N/A | < 250s | Analysis profile |
| CI/CD total | N/A | < 300s | Maven cache + parallel |

**DoD Requirement**: No commit may cause build time to regress by more than 20% from
the established baseline. Measured by `.claude/build-timer.sh`.

---

## 8. Commit Protocol

Every commit must satisfy the Omega workflow (CLAUDE.md Section Omega):

```
Omega(commit) = {
    1. G_compile(code) = PASS
    2. G_test(code) = PASS
    3. stage(specific_files)          -- never git add .
    4. commit(message + session_URL)
    5. push(feature_branch)
}
```

**Commit Message Format**:
```
<type>: <description>

<body explaining why, not what>

https://claude.ai/code/<session_id>
```

Where `<type>` is one of: `feat`, `fix`, `refactor`, `test`, `docs`, `perf`, `security`.

**Staging Rules**:
- Stage specific files by name: `git add src/path/to/File.java`
- Never use `git add .` or `git add -A` (may include sensitive files)
- Never commit `.env`, credentials, or large binaries

---

## 9. The Five Commandments

These are the non-negotiable quality invariants (from HYPER_STANDARDS.md):

1. **No Deferred Work**: No `TODO`, `FIXME`, `XXX`, `HACK`, or any disguised variant.
   Code is either complete or throws `UnsupportedOperationException`.

2. **No Mocks in Production**: No mock objects, mock services, or mock data in `src/`.
   Test mocks live exclusively in `test/`.

3. **No Stubs**: No empty implementations, placeholder data, or no-op methods. Every
   method body contains real logic or an explicit exception.

4. **No Silent Fallbacks**: Exceptions propagate or are explicitly handled (logged AND
   rethrown). Never catch-and-return-fake.

5. **No Lies**: Method behavior matches its name, documentation, and return type.
   `saveToDatabase()` persists data. `validate()` performs validation.

**Violation of ANY commandment = IMMEDIATE REJECTION**

---

## 10. Verification Checklist

### 10.1 Per-Commit Checklist

```
[ ] Code compiles:           mvn -T 1.5C clean compile
[ ] Tests pass:              mvn -T 1.5C clean test
[ ] No guard violations:     hyper-validate.sh (automatic via hook)
[ ] Specific files staged:   git add <files> (not git add .)
[ ] Commit message format:   <type>: <description> + session URL
[ ] Feature branch push:     git push -u origin claude/<branch>
```

### 10.2 Per-Feature Checklist

```
[ ] Per-commit checklist:    All commits satisfy 10.1
[ ] Static analysis clean:   mvn clean verify -P analysis
[ ] Code coverage >= 75%:    JaCoCo line coverage threshold
[ ] New packages documented: package-info.java exists
[ ] FMEA risks reviewed:     High-RPN failure modes mitigated
[ ] Observatory current:     facts match codebase state
```

### 10.3 Per-Release Checklist

```
[ ] Per-feature checklist:   All features satisfy 10.2
[ ] Integration tests pass:  mvn verify -P ci (Failsafe)
[ ] Security audit clean:    SBOM + vulnerability scan
[ ] Performance baseline:    No regression > 10%
[ ] Schema validation:       xmllint --schema YAWL_Schema4.0.xsd
[ ] Deprecated API scan:     jdeprscan --for-removal
[ ] JVM flags verified:      CompactObjectHeaders, TLS 1.3, GC
[ ] Observatory refreshed:   bash scripts/observatory/observatory.sh
```

---

## 11. Formal Properties

### 11.1 Completeness

The DoD is complete if and only if every artifact in the delivery set has been evaluated
against all applicable gates:

```
forall a in Artifacts: forall g in applicable_gates(a): g(a) = PASS
```

### 11.2 Monotonicity

Once a gate evaluates to PASS for a given artifact, it remains PASS unless the artifact
is modified:

```
g(a) = PASS AND unchanged(a) => g(a) = PASS
```

This property enables incremental verification: only re-evaluate gates for changed
artifacts.

### 11.3 Composability

The DoD for a composite unit of work (e.g., a release) is the conjunction of the DoD
for each constituent unit:

```
D(release) = D(feature_1) AND D(feature_2) AND ... AND D(feature_n) AND G_release
```

### 11.4 Observable Enforcement

Every gate evaluation produces an observable artifact (log, report, receipt):

```
forall g in G: g(w) -> evidence(g, w)
```

Evidence is stored in build logs, Observatory receipts, and CI/CD pipeline artifacts.

---

## 12. Beta Release Gate

**Version-Specific**: This gate applies to the Alpha → Beta transition for v6.0.0.

```
D(v6.0.0-Beta) = DONE  iff  all 6 conditions below = PASS
```

### 12.1 Beta Gate Criteria

| # | Criterion | Command | Pass Condition |
|---|-----------|---------|----------------|
| B1 | Build health clean | `bash scripts/dx.sh all` | exits 0 |
| B2 | Test suite passing | `mvn -T 1.5C clean test` | 100% pass, 0 errors |
| B3 | Coverage targets met | `mvn -T 1.5C clean verify -P coverage` | line ≥ 65%, branch ≥ 55% |
| B4 | HYPER_STANDARDS clean | `bash .claude/hooks/hyper-validate.sh src/` | exits 0 (0 violations) |
| B5 | Performance measured | `bash scripts/compare-performance.sh` | startup ≤ 60s documented |
| B6 | Beta documentation complete | `ls docs/v6/BETA-READINESS-REPORT.md` | all 6 Beta docs exist |

### 12.2 Current Beta Status (as of 2026-02-22)

| Gate | Status | Blocker |
|------|--------|---------|
| B1 Build health | 🟢 GREEN | None — static analysis score 100/100 |
| B2 Test suite | 🟡 YELLOW | Coverage not yet measured (JaCoCo reports not generated) |
| B3 Coverage | 🟡 YELLOW | Requires `mvn test` run to establish baseline |
| B4 HYPER_STANDARDS | 🔴 RED | 61 violations (12 BLOCKER, 31 HIGH, 18 MEDIUM) |
| B5 Performance | 🟡 YELLOW | Not yet measured against 60s target |
| B6 Documentation | 🟡 YELLOW | Beta documentation set in progress |

**Beta Release Decision: BLOCKED** — B4 (RED) prevents Beta tag. Resolve 61 HYPER_STANDARDS violations to proceed.

### 12.3 Formal Beta Gate Function

```
G_beta(v6.0.0) = G_build(code) AND G_test(code) AND G_coverage(code)
                 AND G_hyper(code) AND G_perf(engine) AND G_docs(v6)

Where:
  G_hyper(code) = violations(hyper-validate.sh, src/) = 0
  G_coverage(code) = line_pct >= 0.65 AND branch_pct >= 0.55
  G_perf(engine) = startup_time_seconds <= 60
  G_docs(v6) = {BETA-READINESS-REPORT, HYPER-STANDARDS-VIOLATIONS-TRACKER,
                V6-BETA-RELEASE-NOTES, PERFORMANCE-BASELINE-V6-BETA,
                INTEGRATION-ARCHITECTURE-REFERENCE, TEST-COVERAGE-BASELINE}
               are all non-empty files in docs/v6/
```

### 12.4 Beta → RC1 Gate

After Beta is achieved, the RC1 gate adds:
- All 31 HIGH violations resolved (0 remaining)
- Integration tests passing: `mvn verify -P ci`
- Performance regression test: within 10% of baseline
- Security audit: `mvn verify -P security-audit` exits 0
- Target: v6.0.0-RC1 by 2026-03-07

### 12.5 RC1 → GA Gate

After RC1, the GA gate adds:
- All 18 MEDIUM violations resolved (0 remaining)
- Full SBOM scan: CVSS score < 7 for all dependencies
- Staged rollout: 48-hour stability test in staging environment
- Stakeholder sign-off documented
- Target: v6.0.0-GA by 2026-03-21

---

## 13. References

| Document | Location | Purpose |
|----------|----------|---------|
| Project Specification | `CLAUDE.md` | Root specification (A = mu(O)) |
| Hyper Standards | `.claude/HYPER_STANDARDS.md` | 14 forbidden patterns, enforcement |
| Security Checklist | `.claude/SECURITY-CHECKLIST-JAVA25.md` | 5-section security requirements |
| Architecture Patterns | `.claude/ARCHITECTURE-PATTERNS-JAVA25.md` | 8 Java 25 patterns |
| Build Performance | `.claude/BUILD-PERFORMANCE.md` | Maven/JUnit optimization |
| Best Practices | `.claude/BEST-PRACTICES-2026.md` | Claude Code patterns for YAWL |
| Observatory Protocol | `.claude/OBSERVATORY.md` | Instrument protocol (Psi) |
| Observatory Index | `docs/v6/latest/INDEX.md` | 9 facts, 7 diagrams, FMEA |
| Quality Gates | `docs/v6/latest/facts/gates.json` | Gate configuration |
| Test Topology | `docs/v6/latest/facts/tests.json` | Test distribution |
| Module Inventory | `docs/v6/latest/facts/modules.json` | 14 modules, source strategies |
| Reactor Order | `docs/v6/latest/facts/reactor.json` | Build order, dependencies |
| FMEA Risk Map | `docs/v6/latest/diagrams/50-risk-surfaces.mmd` | 7 failure modes |

---

*Definition of Done v1.0 -- YAWL v6 Engineering Standard*
*Enforcement: Automated (hooks + CI/CD) + Manual (code review + release gate)*
*Authority: A = mu(O) | drift(A) -> 0*
