# YAWL Build System Verification Team — START HERE

**Team ID**: `τ-build-verification-synthesis`
**Status**: READY FOR EXECUTION
**Duration**: 45-50 minutes
**Cost**: $3-4C

---

## What is This?

You've been given a complete specification to launch a 5-engineer team that will verify your build system (dx.sh, pom.xml, reactor.json, DEFINITION-OF-DONE.md) is consistent with the 15 new Diátaxis documentation you just created.

## Quick Links

**I'm a LEAD**:
→ Read: [`LEAD-VERIFICATION-BRIEFING.md`](./LEAD-VERIFICATION-BRIEFING.md)
→ Then: [`TEAM-VERIFICATION-ASSIGNMENTS.md`](./TEAM-VERIFICATION-ASSIGNMENTS.md) to brief engineers

**I'm an ENGINEER**:
→ Read: [`TEAM-VERIFICATION-QUICK-REF.yaml`](./TEAM-VERIFICATION-QUICK-REF.yaml) for your quantum
→ Then: Get your section from [`TEAM-VERIFICATION-ASSIGNMENTS.md`](./TEAM-VERIFICATION-ASSIGNMENTS.md)

**I want VISUAL explanations**:
→ Read: [`TEAM-VERIFICATION-VISUAL.md`](./TEAM-VERIFICATION-VISUAL.md)

**I want the FORMAL SPEC**:
→ Read: [`TEAM-VERIFICATION-STRUCTURE.json`](./TEAM-VERIFICATION-STRUCTURE.json)

**I want an overview**:
→ Read: [`README-TEAM-VERIFICATION.md`](./README-TEAM-VERIFICATION.md)

## 60-Second Summary

Your team will verify **3 integration points**:

1. **Point A: Reactor Order** (Engineers A, C, D)
   - Question: Do pom.xml, reactor.json, DEFINITION-OF-DONE.md agree on module order?
   - Success: All report same 19-module sequence

2. **Point B: Gate Timing** (Engineers C, D, E)
   - Question: Do quality gates execute with correct profiles/parallelism within <90s?
   - Success: All agree on command, profile, and timing

3. **Point C: CLI Contracts** (Engineers B, C, A)
   - Question: Does dx.sh -pl correctly invoke mvn -pl -amd respecting order?
   - Success: All agree on -amd flag and topological sort respect

## The 5 Quantums

| # | Engineer | Quantum | Duration | Key Files |
|---|----------|---------|----------|-----------|
| 1 | A | Reactor Order | 25 min | pom.xml, reactor.json |
| 2 | B | CLI Flags | 22 min | dx.sh, dx-workflow.md |
| 3 | C | Parallelism | 25 min | reactor.json, DEFINITION-OF-DONE.md |
| 4 | D | Profiles & Gates | 28 min | pom.xml profiles, DEFINITION-OF-DONE.md §2-3 |
| 5 | E | DoD & Hooks | 30 min | DEFINITION-OF-DONE.md, HYPER_STANDARDS.md |

## Timeline

- **T+0**: Lead briefs team (5 min)
- **T+5 to T+35**: All 5 engineers work in parallel (30 min)
- **T+40 to T+45**: Lead validates integration points (5 min)
- **T+45 to T+50**: Lead synthesizes report and validates end-to-end (5 min)

## Success = All GREEN ✓

- All 5 engineers report GREEN on checklists
- Integration points A, B, C align (no conflicts)
- dx.sh all exits 0 locally
- COMPREHENSIVE_VERIFICATION_REPORT.md generated with evidence

## Documents Provided

```
/home/user/yawl/.claude/
├── 00-START-HERE.md ← YOU ARE HERE
├── README-TEAM-VERIFICATION.md (overview)
├── TEAM-VERIFICATION-INDEX.txt (quick index)
├── TEAM-VERIFICATION-STRUCTURE.json (formal spec)
├── TEAM-VERIFICATION-QUICK-REF.yaml (cheat sheet)
├── TEAM-VERIFICATION-ASSIGNMENTS.md (engineer tasks)
├── TEAM-VERIFICATION-VISUAL.md (diagrams)
└── LEAD-VERIFICATION-BRIEFING.md (lead playbook)
```

## Next Step

**If you're the LEAD**:
1. Read [`LEAD-VERIFICATION-BRIEFING.md`](./LEAD-VERIFICATION-BRIEFING.md)
2. Review [`TEAM-VERIFICATION-VISUAL.md`](./TEAM-VERIFICATION-VISUAL.md) (30 sec)
3. Brief engineers using [`TEAM-VERIFICATION-ASSIGNMENTS.md`](./TEAM-VERIFICATION-ASSIGNMENTS.md)

**If you're an ENGINEER**:
1. Check [`TEAM-VERIFICATION-QUICK-REF.yaml`](./TEAM-VERIFICATION-QUICK-REF.yaml) for your quantum
2. Get detailed instructions from [`TEAM-VERIFICATION-ASSIGNMENTS.md`](./TEAM-VERIFICATION-ASSIGNMENTS.md)
3. Work through your checklist (20-40 min)
4. Report findings to team mailbox

---

**Ready? Go read the document for your role above. Everything you need is there.**
