# Team Verification Setup: Complete Package

**Team ID**: `τ-build-verification-synthesis`
**Objective**: Comprehensive verification that dx.sh, pom.xml, reactor.json, DEFINITION-OF-DONE.md, and related docs are all consistent
**Status**: READY FOR EXECUTION
**Duration**: 45-50 minutes
**Cost**: $3-4C

---

## What You're Getting

This package contains **4 core documents** to launch a 5-engineer verification team:

### 1. **TEAM-VERIFICATION-STRUCTURE.json** (Complete Spec)
- Full specification for all 5 quantums
- Expected findings and success criteria for each engineer
- Integration point definitions (A, B, C)
- Consolidation strategy for lead
- Receipt model and timing information

**Use**: Reference document, formal specification for team architecture

### 2. **TEAM-VERIFICATION-QUICK-REF.yaml** (Engineer Cheat Sheet)
- Condensed reference for each quantum
- Checklists for each engineer
- Key files to examine
- Integration point alignment rules
- Team formation checklist

**Use**: Hand to each engineer before they start (easier to read than JSON)

### 3. **TEAM-VERIFICATION-ASSIGNMENTS.md** (Engineer Task Assignments)
- Detailed assignment for each of 5 engineers (A-E)
- 20-40 minute task descriptions
- Success checklists (specific items to verify)
- Expected findings and evidence to collect
- Integration point explanations
- Lead consolidation workflow

**Use**: Main document for team execution. Give each engineer their section.

### 4. **TEAM-VERIFICATION-VISUAL.md** (Diagrams & Flowcharts)
- Visual breakdown of 5 quantums
- Integration point diagrams
- Data flow showing sources of truth
- Conflict resolution decision trees
- Execution timeline with milestones
- Mermaid workflow diagram

**Use**: Team briefing, visual reference during execution

### 5. **LEAD-VERIFICATION-BRIEFING.md** (Lead Guide)
- Your role and responsibilities (pre-team, during, post-team)
- Integration point validation rules
- Synthesis report template
- Decision points and conflict resolution
- Timeline and success criteria

**Use**: Your personal playbook for leading the team

---

## Quick Start: How to Use These Documents

### For Team Kickoff (5 minutes)
1. Read this README (you're here!)
2. Review TEAM-VERIFICATION-VISUAL.md (understand the 5 quantums visually)
3. Brief all 5 engineers on:
   - Their quantum assignment (distribute relevant section from TEAM-VERIFICATION-ASSIGNMENTS.md)
   - The 3 integration points (Points A, B, C from LEAD-VERIFICATION-BRIEFING.md)
   - Messaging protocol (status every 5-10 min, escalate blockers)

### For Each Engineer (22-30 minutes)
1. Get your section from TEAM-VERIFICATION-ASSIGNMENTS.md
2. Reference TEAM-VERIFICATION-QUICK-REF.yaml for your quantum
3. Work through your checklist
4. Collect evidence (file excerpts, screenshots)
5. Report findings to team mailbox

### For You, the Lead (45-50 minutes total)
1. Pre-team: Ensure observatory is fresh, dx.sh all passes locally
2. During: Monitor engineer progress, watch for blockers
3. Integration phase: Cross-validate Points A, B, C
4. Synthesis: Create COMPREHENSIVE_VERIFICATION_REPORT.md
5. Validation: Run dx.sh all, commit artifacts

---

## The 5 Quantums at a Glance

| Engineer | Quantum | Duration | Focus | Key Files |
|----------|---------|----------|-------|-----------|
| **A** | Reactor Order | 25 min | pom.xml vs reactor.json module sequence, topological sort, cycles | pom.xml (61-81), reactor.json, DEFINITION-OF-DONE.md §3.1, dx-workflow.md |
| **B** | CLI Flags | 22 min | dx.sh command parsing, env vars, Java 25 enforcement | dx.sh (entire), dx-workflow.md |
| **C** | Parallelism | 25 min | -T 1.5C flag, layer-based build strategy, critical path, <90s target | reactor.json, DEFINITION-OF-DONE.md §3.1, dx-workflow.md, dx.sh |
| **D** | Profiles & Gates | 28 min | 6 Maven profiles (java25, agent-dx, ci, analysis, security, prod), 7 gates, version centralization | pom.xml (all <profile> sections), DEFINITION-OF-DONE.md §2-3, dx-workflow.md |
| **E** | DoD & Hooks | 30 min | 7 gate definitions, H-set (8 patterns), Q-invariant, enforcement via hooks | DEFINITION-OF-DONE.md (all), HYPER_STANDARDS.md, hyper-validate.sh, CLAUDE.md §H,Q |

---

## Integration Points: Where Quantums Must Align

### Point A: Reactor Order Canonical Form
**Engineers**: A (reactor order), C (parallelism), D (gate sequence)
**Question**: Do pom.xml, reactor.json, DEFINITION-OF-DONE.md agree on module ordering and dependencies?
**Success**: All three quantums report same 19-module sequence, same layer assignments, no cycles

### Point B: Gate Execution Commands & Timing
**Engineers**: C (parallelism/timing), D (profiles/commands), E (gate definitions)
**Question**: Do the 7 gates execute with correct profiles, parallelism, and fit within <90s budget?
**Success**: Gate commands use -T 1.5C, select correct profiles, total time achievable

### Point C: CLI Argument Contracts & Parallelism
**Engineers**: B (CLI implementation), C (parallelism semantics), A (reactor order)
**Question**: Does dx.sh -pl correctly invoke mvn -pl -amd respecting reactor order?
**Success**: dx.sh -pl includes -amd flag, affected downstream modules included, topological order respected

---

## Success Criteria

✓ All 5 engineers report GREEN on their checklists
✓ Integration points A, B, C show consensus (no conflicts)
✓ dx.sh all exits 0 locally (end-to-end validation)
✓ Inconsistency manifest is empty (or YELLOW items only)
✓ COMPREHENSIVE_VERIFICATION_REPORT.md generated with evidence
✓ Artifacts committed with team ID and session URL

---

## File Locations

All documentation files are in `/home/user/yawl/.claude/`:

```
├── TEAM-VERIFICATION-STRUCTURE.json          ← Full specification
├── TEAM-VERIFICATION-QUICK-REF.yaml          ← Quick reference
├── TEAM-VERIFICATION-ASSIGNMENTS.md          ← Engineer assignments
├── TEAM-VERIFICATION-VISUAL.md               ← Diagrams & flowcharts
├── LEAD-VERIFICATION-BRIEFING.md             ← Lead playbook
└── README-TEAM-VERIFICATION.md               ← This file
```

Verification artifacts will be saved to:
```
└── receipts/
    ├── quantum_1_reactor_verification.json
    ├── quantum_2_cli_verification.json
    ├── quantum_3_parallelism_verification.json
    ├── quantum_4_profiles_verification.json
    ├── quantum_5_dod_verification.json
    └── COMPREHENSIVE_VERIFICATION_REPORT.md
```

---

## Pre-Team Checklist

Before launching the team:

- [ ] Run `bash scripts/observatory/observatory.sh` (refresh facts)
- [ ] Verify `dx.sh all` passes locally on your machine (baseline)
- [ ] Confirm all 5 engineers are ready and assigned
- [ ] Ensure CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1 is set
- [ ] Verify no file conflicts (check shared-src.json from observatory)
- [ ] Brief team on integration points A, B, C
- [ ] Establish messaging protocol (status every 5-10 min)

---

## Key Documents Referenced

**Build System Documentation**:
- `/home/user/yawl/pom.xml` (19-module parent POM)
- `/home/user/yawl/docs/v6/diagrams/facts/reactor.json` (FM7 canonical ordering)
- `/home/user/yawl/docs/v6/DEFINITION-OF-DONE.md` (7 quality gates)
- `/home/user/yawl/scripts/dx.sh` (CLI implementation)
- `/home/user/yawl/.claude/rules/build/dx-workflow.md` (build system reference)
- `/home/user/yawl/.claude/HYPER_STANDARDS.md` (H-set, Q-invariant)
- `/home/user/yawl/.claude/hooks/hyper-validate.sh` (guard enforcement)

---

## Support

If engineers have questions:
1. Check TEAM-VERIFICATION-QUICK-REF.yaml (quick answers)
2. Review TEAM-VERIFICATION-ASSIGNMENTS.md (detailed guidance)
3. Look at TEAM-VERIFICATION-VISUAL.md (diagrams)
4. Ask lead to mediate (you have final say on conflicts)

---

## Questions?

This is a **specification**, not a guide. Everything you need is in the 5 documents above.

- **"What should I look for?"** → Your section in TEAM-VERIFICATION-ASSIGNMENTS.md
- **"How do I know if I'm done?"** → Your checklist in same document
- **"What if I find a conflict?"** → Check LEAD-VERIFICATION-BRIEFING.md conflict resolution
- **"What's an integration point?"** → See TEAM-VERIFICATION-VISUAL.md

---

## Estimated Outcome

After ~50 minutes:

✓ COMPREHENSIVE_VERIFICATION_REPORT.md with full evidence
✓ All 5 quantums documented (GREEN or RED)
✓ Integration points A, B, C validated
✓ Any inconsistencies identified and prioritized for fixing
✓ End-to-end build validation (dx.sh all green)
✓ Artifacts committed to git with verification chain

---

**Ready to launch the team? Start with TEAM-VERIFICATION-ASSIGNMENTS.md.**
