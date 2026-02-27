# Lead Briefing: Build System Verification Team

**Team ID**: `τ-build-verification-synthesis`
**Your Role**: Team Lead (coordinate, synthesize, validate)
**Team Size**: 5 engineers (A-E)
**Duration**: 45-50 minutes
**Cost**: $3-4C

---

## Context & Objective

You have just created 15 new Diátaxis documentation files covering YAWL's build system:
- Build Sequences Reference (19-module canonical order, parallel groups, critical path)
- HYPER_STANDARDS Pattern Reference (14 patterns)
- Quality Gates Reference (7 gates)
- dx.sh CLI Reference (all flags, env vars)
- FMEA Risk Table (16 failure modes)
- Explanation docs on reactor ordering, H-Guards, Chatman equation

**Your Team's Mission**: Verify that `dx.sh`, `pom.xml`, `reactor.json`, `DEFINITION-OF-DONE.md`, and Mermaid diagrams are all **consistent** with each other and with the new documentation.

**Key Question**: Do the 5 source-of-truth files agree on the build system architecture?

---

## Your Responsibilities

### Pre-Team (Before Engineers Start)
1. **Validate team formation**:
   - [ ] Run `bash scripts/observatory/observatory.sh` (refresh facts, check for file conflicts)
   - [ ] Confirm no file conflicts between 5 quantums (shared-src.json clean)
   - [ ] Confirm each engineer task is 20-40 minute scope
2. **Brief the team**:
   - [ ] Distribute TEAM-VERIFICATION-ASSIGNMENTS.md to each engineer
   - [ ] Explain the 3 integration points (A, B, C) — synchronization is critical
   - [ ] Establish messaging protocol: status updates every 5-10 min, escalate blockers immediately
3. **Ensure readiness**:
   - [ ] dx.sh all passes locally on your machine (baseline validation)
   - [ ] All referenced files are readable and current
   - [ ] Session context is prepared for team coordination

### During Team Execution (30 minutes)
1. **Monitor progress**:
   - Watch for messages from engineers about blockers or surprising findings
   - Flag unexpected findings for discussion in integration phase
   - Keep time: engineers should report completion around 25-30 min mark
2. **Facilitate integration points**:
   - When engineers report findings, verify alignment on:
     - **Point A**: Reactor order consistency (A, C, D agree on module sequence)
     - **Point B**: Gate execution contracts (C, D, E agree on timing and profiles)
     - **Point C**: CLI argument handling (B, C, A agree on dx.sh -pl behavior)
   - If conflicts: message responsible engineers immediately for resolution
3. **Triage issues**:
   - RED findings: Identify ownership and ask engineer to investigate further
   - YELLOW findings: Note for consolidation phase, don't block team
   - GREEN findings: Confirm and move to next engineer

### Post-Team: Consolidation Phase (15-20 minutes)
1. **Collect all findings** (2 min):
   - Receive final reports from all 5 engineers
   - Compile checklist status (GREEN/RED per quantum)
2. **Cross-validate integration points** (5 min):
   - Create alignment matrix for Points A, B, C
   - If conflicts: investigate root cause (which file is source of truth?)
   - Decide remediation: which file to fix, who owns it
3. **Synthesize report** (8 min):
   - Create COMPREHENSIVE_VERIFICATION_REPORT.md with:
     - Executive summary (all GREEN/YELLOW/RED?)
     - Per-quantum status section (5 subsections)
     - Integration point alignment (3 subsections)
     - Inconsistency manifest (if any issues)
     - Evidence chain (file excerpts proving each finding)
4. **Validate end-to-end** (3 min):
   - Run `bash scripts/dx.sh all` locally
   - Confirm all 19 modules compile + test green
   - This ensures documentation isn't aspirational; it matches reality
5. **Commit verification** (2 min):
   - Stage: COMPREHENSIVE_VERIFICATION_REPORT.md + receipt artifacts
   - Commit with message: "Verify build system documentation consistency (τ-build-verification-synthesis)"
   - Include session URL in commit message

---

## The 5 Quantums: Quick Reference

| Engineer | Quantum | Focus | Files | Duration | Integration |
|----------|---------|-------|-------|----------|-------------|
| A | Reactor Order | pom.xml vs reactor.json, topological sort, cycles | pom.xml, reactor.json, DEFINITION-OF-DONE.md, dx-workflow.md | 25 min | Point A (canonical form), Point C (CLI respects order) |
| B | CLI Flags | dx.sh command parsing, env vars, Java 25 enforcement | dx.sh, dx-workflow.md | 22 min | Point C (CLI contracts) |
| C | Parallelism | -T 1.5C, layer structure, critical path, <90s target | reactor.json, DEFINITION-OF-DONE.md, dx-workflow.md, dx.sh | 25 min | Point A (layers respect deps), Point B (timing), Point C (-pl respects order) |
| D | Profiles & Gates | 6 profiles, 7 gates, version centralization | pom.xml, DEFINITION-OF-DONE.md, dx-workflow.md | 28 min | Point A (gates respect build order), Point B (gate commands/timing) |
| E | DoD & Hooks | 7 gates, H-set, Q-invariant, enforcement | DEFINITION-OF-DONE.md, HYPER_STANDARDS.md, hyper-validate.sh, CLAUDE.md | 30 min | Point B (gate timing/profiles), integration of H/Q axioms |

---

## Integration Points: What You'll Validate

### Point A: Reactor Order Canonical Form
**The Question**: Do pom.xml, reactor.json, and DEFINITION-OF-DONE.md agree on module order?

**Engineers to Compare**: A (reactor order), C (layer parallelism), D (gate sequence)

**Expected Alignment**:
- pom.xml <modules> list matches reactor.json positions 1-19
- reactor.json layers (0-6) match DEFINITION-OF-DONE.md §3.1 layer definitions
- All dependencies are satisfied before dependent modules

**Conflict Example** (Red Flag):
```
Eng A: "pom.xml lists yawl-engine before yawl-elements"
Eng C: "reactor.json says yawl-elements must come before yawl-engine"
→ CONFLICT: Which is source of truth?
   → reactor.json is FM7 poka-yoke (canonical). Update pom.xml.
```

### Point B: Gate Execution Commands & Timing
**The Question**: Do the 7 gates execute correctly with the right profiles and parallelism, within the <90s budget?

**Engineers to Compare**: C (parallelism/timing), D (profiles/commands), E (gate definitions)

**Expected Alignment**:
- Each gate command uses `-T 1.5C` parallelism
- Each gate selects correct profile (e.g., G_analysis uses 'analysis' profile)
- Sum of gate execution times + critical path <= 90 seconds for clean build
- Gate ordering matches partial order: G_compile < G_test < ... < G_release

**Conflict Example** (Red Flag):
```
Eng C: "Critical path is 45 seconds, -T 1.5C enables 20-second parallelism = 65s total"
Eng D: "G_analysis gate runs entire 'analysis' profile which takes 40 seconds (sequential)"
Eng E: "Total execution is 105 seconds"
→ CONFLICT: <90s target is not feasible
   → Option 1: Parallelize analysis gate (run SpotBugs/PMD concurrently)
   → Option 2: Lower <90s target to <120s
```

### Point C: CLI Argument Contracts & Parallelism
**The Question**: Does dx.sh correctly handle module targeting (-pl) while respecting reactor order?

**Engineers to Compare**: B (CLI implementation), C (parallelism semantics), A (reactor order)

**Expected Alignment**:
- dx.sh -pl mod1,mod2 translates to `mvn -pl mod1,mod2 -amd clean test`
- -amd (affected downstream) includes all dependents, respecting reactor order
- Parallelism still applies: modules with no inter-dependency execute in parallel

**Conflict Example** (Red Flag):
```
Eng B: "dx.sh -pl yawl-elements passes 'mvn -pl yawl-elements'"
Eng A: "yawl-engine depends on yawl-elements, so dx.sh -pl yawl-elements should include yawl-engine"
Eng C: "Without -amd, we lose parallelism on independent modules"
→ CONFLICT: dx.sh missing -amd flag
   → Fix: Update dx.sh to use '-amd' (affected downstream)
```

---

## Synthesis Report Structure

After consolidation, you'll produce `COMPREHENSIVE_VERIFICATION_REPORT.md` with:

```markdown
# YAWL Build System Comprehensive Verification Report

**Date**: 2026-02-27
**Team ID**: τ-build-verification-synthesis
**Status**: [GREEN | YELLOW | RED]

## Executive Summary
- All 5 engineers reported GREEN on checklists
- Integration points: A (GREEN), B (GREEN), C (RED)
- 3 inconsistencies found, all fixable
- End-to-end validation: dx.sh all exits 0 ✓

## Per-Quantum Status

### Quantum 1: Reactor Order & Dependencies (Engineer A)
- Status: GREEN
- Checklist: 7/7 items GREEN
- Key findings:
  - pom.xml lists all 19 modules ✓
  - Module order respects dependencies ✓
  - No cycles detected ✓
- Evidence:
  ```
  pom.xml modules (61-81):
    yawl-utilities, yawl-elements, yawl-authentication, ...
  reactor.json positions:
    1: yawl-utilities, 2: yawl-security, ... (all 19)
  ```

### Quantum 2: CLI Flags & Env Vars (Engineer B)
- Status: GREEN
- Checklist: 5/5 items GREEN
- Key findings: all CLI modes documented, Java 25 enforced
- Evidence: [...]

### Quantum 3: Parallelism & Critical Path (Engineer C)
- Status: YELLOW
- Checklist: 4/5 items GREEN, 1 YELLOW
- Issue: <90s target not feasible (actual: 105s)
- Evidence: [critical path analysis]

### Quantum 4: Profiles & Gates (Engineer D)
- Status: GREEN
- Checklist: 7/7 items GREEN
- Key findings: all 6 profiles exist, gates measurable
- Evidence: [profile definitions, gate specifications]

### Quantum 5: DoD & Enforcement (Engineer E)
- Status: GREEN
- Checklist: 7/7 items GREEN
- Key findings: all 7 gates defined, H-set mapped, Q-invariant enforced
- Evidence: [gate list, H-set mapping]

## Integration Point Alignment

### Point A: Reactor Order (A, C, D consensus)
- Status: GREEN ✓
- All quantums agree: 19 modules in correct topological order
- Layers match DEFINITION-OF-DONE.md specification

### Point B: Gate Execution (C, D, E consensus)
- Status: YELLOW ⚠
- Issue: <90s target not achievable with current parallelism
- Recommendation: Adjust target to <120s or parallelize analysis gate

### Point C: CLI Contracts (B, C, A consensus)
- Status: GREEN ✓
- dx.sh -pl correctly invokes mvn -pl -amd
- Module targeting respects reactor order

## Inconsistency Manifest

| Priority | File | Issue | Fix |
|----------|------|-------|-----|
| YELLOW | DEFINITION-OF-DONE.md | <90s target not feasible (actual 105s) | Update target to <120s or parallelize analysis gate |

## Evidence Chain

[File excerpts and screenshots showing findings]

## End-to-End Validation

- `bash scripts/dx.sh all`: PASS ✓
- All 19 modules compile: PASS ✓
- All tests pass: PASS ✓
- No gate violations: PASS ✓

## Recommendations

1. Update DEFINITION-OF-DONE.md §3.1 G_compile target: <90s → <120s
2. Consider parallelizing SpotBugs/PMD in analysis profile (future optimization)
3. All other aspects verified as consistent and correct

---
Generated by τ-build-verification-synthesis
Session URL: https://claude.ai/code/session_01B7tRmiPj6juX6E53dJ9myK
```

---

## Key Decision Points (For You)

### If Conflict on Reactor Order (Point A)
- **Rule**: reactor.json is FM7 poka-yoke (canonical source of truth)
- **Action**: Ask Engineer A to verify pom.xml matches reactor.json; if not, update pom.xml
- **Why**: reactor.json is computed from dependency graph; pom.xml is mutable

### If Conflict on Gate Timing (Point B)
- **Rule**: <90s target is a design goal, but real builds take longer with full analysis
- **Options**:
  1. Adjust documentation target to match reality (e.g., <120s)
  2. Optimize gate execution (parallelize analysis plugins)
  3. Document trade-off (fast loop: no analysis, full build: with analysis)
- **Action**: Decide which option, then update DEFINITION-OF-DONE.md or pom.xml

### If Conflict on CLI Contracts (Point C)
- **Rule**: dx.sh must respect reactor order (no breaking dependency contracts)
- **Action**: Fix dx.sh to use `-amd` flag if missing; verify with Engineer A
- **Why**: Ensures downstream modules are included when targeting upstream modules

---

## Communication Protocol

### During Team Execution
**Every 10 minutes**:
- Request status from each engineer (posted to team mailbox)
- Watch for RED findings or blockers
- If blocker: ask responsible engineer + lead facilitates resolution

### When Conflict Detected
1. Message both conflicting engineers
2. Share the conflict clearly (quote findings)
3. Ask: "Which is source of truth?"
4. Get consensus or escalate decision to you (lead)

### Final Reporting
- Each engineer submits: `{quantum_id}_verification_{finding_count}_findings.json`
- You synthesize: `COMPREHENSIVE_VERIFICATION_REPORT.md` (includes all evidence)

---

## Success Criteria (For Committing)

✓ **All 5 engineers report GREEN** on their checklists
✓ **Integration points A, B, C** show consensus (no conflicts) or documented trade-off
✓ **dx.sh all exits 0** locally (end-to-end validation)
✓ **Inconsistency manifest** is empty or contains only YELLOW items
✓ **COMPREHENSIVE_VERIFICATION_REPORT.md** has full evidence chain
✓ **Commit message** includes team ID and session URL

---

## Timeline

| Phase | Duration | Owner | Output |
|-------|----------|-------|--------|
| Pre-team setup | 5 min | You (lead) | Brief engineers, validate formation |
| Parallel execution | 30 min | All 5 engineers | 5 verification reports with findings |
| Integration validation | 5 min | You (lead) | Cross-check Points A, B, C |
| Synthesis report | 8 min | You (lead) | COMPREHENSIVE_VERIFICATION_REPORT.md |
| End-to-end validation | 3 min | You (lead) | dx.sh all green, build validated |
| Commit | 2 min | You (lead) | Verification artifacts committed |
| **Total** | **~50 min** | | |

---

## Resources

- **Full specification**: `/home/user/yawl/.claude/TEAM-VERIFICATION-STRUCTURE.json`
- **Engineer quick-ref**: `/home/user/yawl/.claude/TEAM-VERIFICATION-QUICK-REF.yaml`
- **Engineer assignments**: `/home/user/yawl/.claude/TEAM-VERIFICATION-ASSIGNMENTS.md`
- **This document**: `/home/user/yawl/.claude/LEAD-VERIFICATION-BRIEFING.md`

## Checklist: Ready to Launch?

- [ ] Observatory facts refreshed (bash scripts/observatory/observatory.sh)
- [ ] dx.sh all passes locally (baseline)
- [ ] All 5 engineers briefed and assigned
- [ ] Integration points A, B, C explained to team
- [ ] Messaging protocol established
- [ ] Context prepared for team coordination

**Go Team!**
