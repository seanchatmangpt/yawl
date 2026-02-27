# YAWL Build System Verification Team — Engineer Assignments

**Team ID**: `τ-build-verification-synthesis`
**Status**: READY FOR EXECUTION
**Duration**: 45-50 minutes (5 engineers × ~25-30 min each, executed in parallel)
**Cost**: ~$3-4C
**Consolidation**: Lead synthesis phase 15-20 min

---

## Team Assignment Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│ Engineer A: Reactor Order & Dependency Graph (25 min)              │
│ → Verify pom.xml module order matches reactor.json                 │
│ → Check topological sort, no cycles, layer definitions             │
│ ✓ Success: 19 modules verified, dependencies valid, no cycles      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Engineer B: CLI Flags & Environment Variables (22 min)             │
│ → Parse dx.sh script, extract all command formats and flags        │
│ → Validate against dx-workflow.md documentation                    │
│ ✓ Success: All CLI modes documented, env vars match impl           │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Engineer C: Parallel Build Groups & Critical Path (25 min)         │
│ → Verify -T 1.5C parallelism, layer-based parallelism strategy    │
│ → Validate <90s build target feasibility                           │
│ ✓ Success: Parallelism valid, critical path clear, target met      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Engineer D: Build Profiles & Quality Gates (28 min)                │
│ → Extract 6 profiles from pom.xml, map to DEFINITION-OF-DONE.md   │
│ → Verify 7 gates (G_compile through G_release) measurable          │
│ ✓ Success: All gates defined, profiles match, versions centralized │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ Engineer E: Definition of Done & Enforcement (30 min)              │
│ → Validate all 7 gates have predicates, gate ordering correct      │
│ → Map H-set (8 items) and Q-invariant to hooks/automation          │
│ ✓ Success: All gates defined, H-set/Q integrated, enforceable      │
└─────────────────────────────────────────────────────────────────────┘

                              PARALLEL EXECUTION
                              ~30 minutes total
                                    ↓
                    Integration Point Alignment Check
                         (Lead cross-validates)
                                    ↓
                    Consolidation: Synthesis Report
                         (Lead: 15-20 min)
                                    ↓
                              dx.sh all test
                                    ↓
                         VERIFICATION COMPLETE
```

---

## Engineer Assignments

### Engineer A: Reactor Order & Dependency Graph Specialist

**Duration**: 25 minutes
**Quantum Focus**: Maven reactor sequencing, module dependencies, topological correctness

**Your Task**:
1. Examine `/home/user/yawl/pom.xml` lines 61-81 (module list)
2. Examine `/home/user/yawl/docs/v6/diagrams/facts/reactor.json` (entire file)
3. Extract all inter-module dependencies from pom.xml child POMs
4. Verify topological sort: dependencies always precede dependents
5. Cross-reference DEFINITION-OF-DONE.md §3.1 Layer 0-6 definitions
6. Cross-reference dx-workflow.md "Build Order" section

**Success Checklist**:
- [ ] pom.xml lists all 19 modules
- [ ] reactor.json lists all 19 modules
- [ ] Module order respects topological sort (dependencies before dependents)
- [ ] No cyclical dependencies
- [ ] DEFINITION-OF-DONE.md Layer 0-6 match reactor.json layers
- [ ] dx-workflow.md "Build Order" aligns with reactor.json positions
- [ ] All 19 modules accounted for

**Expected Findings**: (typical results, not prescriptive)
- PASS: Reactor order is canonical FM7 poka-yoke; 19 modules match; zero cycles; layer definitions aligned
- ISSUES (if any): Missing module, out-of-order position, undeclared dependency, conflicting layer assignment

**Evidence to Collect**:
- pom.xml module list (copy/paste 19-module sequence)
- reactor.json module sequence (all positions 1-19)
- Dependency graph showing parent-child relationships
- Layer mapping comparison table

**Integration Points**:
- Point A: Report reactor canonical form to Engineers C & D
- Point B: Report module dependencies to Engineer D for gate validation

---

### Engineer B: CLI Flags & Environment Variables Specialist

**Duration**: 22 minutes
**Quantum Focus**: dx.sh command-line interface, argument parsing, environment variable handling

**Your Task**:
1. Read entire `/home/user/yawl/scripts/dx.sh` script
2. Extract all command-line formats and flags it supports
3. Extract all environment variables (DX_*, JAVA_HOME) and their documented behavior
4. Extract usage examples from script (bash comments)
5. Cross-reference against `/home/user/yawl/.claude/rules/build/dx-workflow.md`
6. Verify Java 25 enforcement logic (lines 38-47)

**Success Checklist**:
- [ ] dx.sh supports all documented command formats: `dx.sh`, `dx.sh compile`, `dx.sh test`, `dx.sh all`, `dx.sh -pl mod1,mod2`
- [ ] All environment variables documented with correct defaults in dx-workflow.md
- [ ] DX_OFFLINE behavior matches documentation (auto-detect default)
- [ ] DX_FAIL_AT, DX_VERBOSE, DX_CLEAN documented with correct defaults
- [ ] Java 25 enforcement block enforces Temurin 25 and sets JAVA_HOME correctly
- [ ] Exit codes documented and consistent with implementation

**Expected Findings**: (typical results)
- PASS: All CLI modes documented, env vars match implementation, Java 25 enforced
- ISSUES (if any): Undocumented flags, env var defaults mismatch, Java version check fails on some systems

**Evidence to Collect**:
- dx.sh usage section (script comments at top)
- List of all supported command formats
- Environment variable definitions and defaults
- Java version check logic (lines 38-47)
- dx-workflow.md CLI reference section

**Integration Points**:
- Point C: Report CLI argument contracts to Engineer C
- Point B: Report env var effects to Engineer E for hook integration

---

### Engineer C: Parallel Build Groups & Critical Path Specialist

**Duration**: 25 minutes
**Quantum Focus**: Parallel execution strategy, -T 1.5C flag correctness, layer-based build parallelism, critical path

**Your Task**:
1. Examine `/home/user/yawl/docs/v6/diagrams/facts/reactor.json` (focus: layer structure)
2. Identify all modules in each layer (Layer 0 through Layer 6)
3. Verify no inter-dependencies between modules in same layer
4. Identify critical path: longest dependency chain from root to leaf
5. Analyze -T 1.5C parallelism flag usage in dx.sh and documentation
6. Verify <90 second clean build target is feasible given critical path
7. Cross-reference dx-workflow.md parallelism strategy

**Success Checklist**:
- [ ] Layer 0: All modules are independent (no inter-deps)
- [ ] Layer 1: All modules are independent (no inter-deps)
- [ ] Layers 2-6: All modules in same layer are independent
- [ ] Critical path identified (e.g., yawl-utilities → yawl-elements → ... → yawl-mcp-a2a-app)
- [ ] -T 1.5C parallelism clearly used in Maven commands
- [ ] <90 second target is feasible (critical path + parallelism)
- [ ] dx-workflow.md parallelism strategy matches reactor.json layer structure

**Expected Findings**: (typical results)
- PASS: Layers enable valid parallel builds, critical path clear, <90s target achievable
- ISSUES (if any): Modules in same layer have undeclared dependencies, parallelism flag not documented, time target unrealistic

**Evidence to Collect**:
- Layer-by-layer breakdown with modules in each layer
- Dependency graph showing critical path
- Estimated build time calculation (sum of critical path with parallelism)
- dx-workflow.md Maven parallelism section

**Integration Points**:
- Point A: Validate layers respect dependencies from Engineer A
- Point C: Validate CLI module targeting (-pl) respects reactor order
- Point B: Validate gate execution fits within parallelism budget

---

### Engineer D: Build Profiles & Quality Gates Specialist

**Duration**: 28 minutes
**Quantum Focus**: Maven profiles (6 types), quality gates (7 gates), enforcement strategy

**Your Task**:
1. Search `/home/user/yawl/pom.xml` for all <profile> sections
2. Document each profile: java25, agent-dx, ci, analysis, security, prod
3. Extract gate definitions from `/home/user/yawl/docs/v6/DEFINITION-OF-DONE.md`
4. Map each gate to actual Maven command: G_compile (mvn compile), G_test (mvn test), etc.
5. Verify gate ordering matches partial order: G_compile < G_test < G_guard < ... < G_release
6. Verify versions are centralized in parent pom.xml (not overridden by profiles)
7. Cross-reference profiles with dx-workflow.md "Build Profiles" section

**Success Checklist**:
- [ ] java25 profile exists with Java 25 configuration
- [ ] agent-dx profile exists with fail-fast and parallelism settings
- [ ] ci profile exists with JaCoCo configuration
- [ ] analysis profile exists with SpotBugs, PMD, Checkstyle
- [ ] security profile exists with SBOM and OWASP dependency check
- [ ] prod profile exists with all validations
- [ ] G_compile gate: mvn -T 1.5C clean compile exits 0
- [ ] G_test gate: mvn -T 1.5C clean test exits 0
- [ ] G_guard gate: 14 anti-patterns absent (mapped to hyper-validate.sh)
- [ ] All version properties centralized in parent POM

**Expected Findings**: (typical results)
- PASS: All 6 profiles defined, 7 gates measurable, versions centralized
- ISSUES (if any): Missing profile, gate command unmeasurable, versions overridden by profile

**Evidence to Collect**:
- pom.xml profile definitions (copy all <profile> blocks)
- DEFINITION-OF-DONE.md Gate Summary Table (§2.1)
- Gate command mapping (G_compile → mvn command, etc.)
- Version property declarations in parent POM

**Integration Points**:
- Point A: Validate gate sequence respects build order from Engineer A
- Point B: Validate gate execution timing fits within parallelism budget from Engineer C
- Point B: Validate gate commands use correct profile and parallelism

---

### Engineer E: Definition of Done & Enforcement Specialist

**Duration**: 30 minutes
**Quantum Focus**: DoD completeness, H-set/Q-invariant definitions, hook integration, acceptance criteria

**Your Task**:
1. Examine entire `/home/user/yawl/docs/v6/DEFINITION-OF-DONE.md`
2. Extract all 7 gates and verify each has a measurable predicate
3. Examine `/home/user/yawl/.claude/HYPER_STANDARDS.md` for H-set and Q-invariant definitions
4. Locate `/home/user/yawl/.claude/hooks/hyper-validate.sh` (or equivalent) and verify 14 anti-pattern checks
5. Verify gate ordering: G_compile < G_test < G_guard < G_analysis < G_security < G_documentation < G_release
6. Cross-reference H-set (TODO, FIXME, mock, stub, fake, empty_return, silent_fallback, lie) with actual hooks
7. Cross-reference Q-invariant (real_impl OR throw) with enforcement mechanism

**Success Checklist**:
- [ ] G_compile gate is defined with measurable predicate (mvn clean compile exits 0)
- [ ] G_test gate is defined with measurable predicate (mvn clean test exits 0, 100% pass)
- [ ] G_guard gate is defined and mapped to hyper-validate.sh (14 anti-patterns)
- [ ] G_analysis gate is defined with measurable predicate (SpotBugs/PMD/Checkstyle zero violations)
- [ ] G_security gate is defined with measurable predicate (SBOM clean, no CVEs, TLS 1.3)
- [ ] G_documentation gate is defined with measurable predicate (package-info.java for all new packages)
- [ ] G_release gate is defined with measurable predicate (integration tests + performance baseline)
- [ ] H-set (8 items) defined and mapped to hyper-validate.sh blocks on violation
- [ ] Q-invariant defined: every public method is real_impl OR throws UnsupportedOperationException
- [ ] Gate ordering matches partial order specification

**Expected Findings**: (typical results)
- PASS: All 7 gates defined, measurable, ordered correctly; H-set and Q-invariant integrated
- ISSUES (if any): Gate definitions vague, hyper-validate.sh missing patterns, Q-invariant not validated, enforcement gaps

**Evidence to Collect**:
- DEFINITION-OF-DONE.md Gate Summary Table (§2.1)
- All 7 gate specifications (§3.1-3.7)
- H-set definition and mapping to hyper-validate.sh
- Q-invariant definition and enforcement mechanism
- hyper-validate.sh anti-pattern checks (14 patterns)

**Integration Points**:
- Point B: Validate gate execution timing from Engineer C fits within constraints
- Point B: Validate gate command profiles from Engineer D match enforcement requirements

---

## Integration Points & Synthesis

### Integration Point A: Reactor Order Canonical Form
**Participants**: Engineers A, C, D
**Expected Alignment**: All quantums report same module order, layer assignments, dependency constraints
**Verification**:
- Engineer A: pom.xml module order = reactor.json positions
- Engineer C: reactor.json layers enable valid parallel builds
- Engineer D: Gate sequence respects build order dependencies

**Conflict Resolution**:
- If pom.xml differs from reactor.json: reactor.json is FM7 poka-yoke (canonical). Update pom.xml.

---

### Integration Point B: Gate Execution Commands & Timing
**Participants**: Engineers C, D, E
**Expected Alignment**: Each gate uses -T 1.5C parallelism, correct profile, fits within <90s budget
**Verification**:
- Engineer C: Parallelism strategy is -T 1.5C, critical path clear
- Engineer D: Gate commands use -T 1.5C and correct profiles
- Engineer E: Gate ordering matches specification, execution fits budget

**Conflict Resolution**:
- If gate timing exceeds budget: adjust parallelism or profile selection in pom.xml

---

### Integration Point C: CLI Argument Contracts & Parallelism
**Participants**: Engineers B, C, A
**Expected Alignment**: dx.sh -pl correctly invokes mvn -pl -amd respecting reactor order
**Verification**:
- Engineer B: dx.sh -pl argument documented and implemented
- Engineer C: -pl with -amd respects layer dependencies
- Engineer A: -amd (affected downstream) respects topological sort

**Conflict Resolution**:
- If dx.sh violates reactor order: fix dx.sh implementation to match reactor.json

---

## Lead Consolidation Phase (15-20 minutes)

After all 5 engineers report GREEN on their checklists:

1. **Collect Findings** (2 min): Gather reports from all 5 engineers
2. **Cross-Validate Integration Points** (5 min):
   - Point A: Are reactor orders aligned across A, C, D?
   - Point B: Are gate commands consistent across C, D, E?
   - Point C: Does dx.sh respect reactor order per A, B, C?
3. **Synthesize Report** (8 min):
   - Per-quantum status (GREEN/YELLOW/RED)
   - Integration point consensus
   - Inconsistency manifest (if any)
   - Evidence chain with file excerpts
4. **Validate End-to-End** (3 min):
   - Run `bash scripts/dx.sh all` locally
   - Verify all modules compile + test green
5. **Commit Verification** (2 min):
   - Commit COMPREHENSIVE_VERIFICATION_REPORT.md
   - Include evidence artifacts and session URL

---

## Success Criteria

**Team Formation**:
- All 5 engineers assigned to non-overlapping quantums ✓
- Each task scoped to 20-40 minutes ✓
- No file conflicts (verified via observatory facts) ✓
- Facts current (bash scripts/observatory/observatory.sh run) ✓

**Team Execution**:
- All 5 engineers report GREEN on their checklists
- Integration points A, B, C show consensus (no conflicts)
- dx.sh all exits 0 with all modules compiling + testing green

**Consolidation**:
- COMPREHENSIVE_VERIFICATION_REPORT.md generated with evidence
- Inconsistency manifest empty (or contains only clarifications)
- Verification artifacts committed with session URL

---

## File Structure for Verification Artifacts

```
/home/user/yawl/.claude/
├── TEAM-VERIFICATION-STRUCTURE.json          ← Full specification
├── TEAM-VERIFICATION-QUICK-REF.yaml          ← Engineer reference guide
├── TEAM-VERIFICATION-ASSIGNMENTS.md          ← This document (team assignments)
└── receipts/
    ├── quantum_1_reactor_verification.json    ← Engineer A findings
    ├── quantum_2_cli_verification.json        ← Engineer B findings
    ├── quantum_3_parallelism_verification.json ← Engineer C findings
    ├── quantum_4_profiles_verification.json   ← Engineer D findings
    ├── quantum_5_dod_verification.json        ← Engineer E findings
    └── COMPREHENSIVE_VERIFICATION_REPORT.md   ← Lead synthesis report
```

---

## Next Steps

1. **Distribution**: Provide each engineer with their section from this document
2. **Kickoff**: Brief all engineers on integration points A, B, C
3. **Parallel Execution**: All 5 engineers work simultaneously (25-30 min)
4. **Reporting**: Each engineer submits findings to team mailbox
5. **Lead Synthesis**: Lead consolidates and validates end-to-end (15-20 min)
6. **Commitment**: Commit COMPREHENSIVE_VERIFICATION_REPORT.md

**Estimated Total Duration**: 45-50 minutes (5 min setup + 30 min parallel + 15 min consolidation)
**Estimated Cost**: $3-4C
