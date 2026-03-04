# Claude Code Session Prompts & Continuity

This directory contains structured prompts to guide future Claude Code sessions and maintain continuity across sessions.

---

## Current Session Status (as of 2026-03-04)

**Completed**:
- ✅ Autonomous error detection system (`analyze-errors.sh`)
- ✅ Auto-remediation framework (`remediate-violations.sh`)
- ✅ Intelligent routing engine (`decision-engine.sh`)
- ✅ Enhanced validation orchestration (`stop-hook.sh`)
- ✅ Permanent 15-agent team structure (3 teams × 5 agents)
- ✅ Full integration testing & verification
- ✅ All scripts tested and working

**In Progress**: Plugin research framework design

**Next Steps**: Execute plugin research and adaptation

---

## Quick Navigation

### For Next Session: Plugin Research

**Start here** (5 min read):
→ [`PLUGIN-RESEARCH-QUICKSTART.md`](./PLUGIN-RESEARCH-QUICKSTART.md)

**Full research plan** (detailed):
→ [`NEXT-SESSION-PLUGIN-RESEARCH.md`](./NEXT-SESSION-PLUGIN-RESEARCH.md)

**Execution checklist** (step-by-step):
→ [`PLUGIN-RESEARCH-CHECKLIST.md`](./PLUGIN-RESEARCH-CHECKLIST.md)

**Template for each plugin** (copy & fill):
→ [`PLUGIN-RESEARCH-TEMPLATE.md`](./PLUGIN-RESEARCH-TEMPLATE.md)

---

## File Directory

```
.claude/prompts/
├── README.md                              (you are here)
├── NEXT-SESSION-PLUGIN-RESEARCH.md        (Phase 1-5 plan, success criteria)
├── PLUGIN-RESEARCH-QUICKSTART.md          (TL;DR version, 60-second setup)
├── PLUGIN-RESEARCH-CHECKLIST.md           (execution checklist, phase targets)
└── PLUGIN-RESEARCH-TEMPLATE.md            (template for analyzing each plugin)
```

---

## What to Do In Next Session

### TL;DR (2 minutes)

1. Read `PLUGIN-RESEARCH-QUICKSTART.md`
2. Run discovery phase: find all plugins
3. Analyze top 10: use `PLUGIN-RESEARCH-TEMPLATE.md`
4. Classify by tier: Tier 1 (must adapt), Tier 2 (should), Tier 3 (nice)
5. Build 1 adapter: proof-of-concept
6. Document: how to replicate the pattern

### Full Plan (6 hours)

Follow the 5-phase plan in `NEXT-SESSION-PLUGIN-RESEARCH.md`:

| Phase | Time | Output |
|-------|------|--------|
| **Phase 1**: Discovery | 1h | Plugin inventory |
| **Phase 2**: Analysis | 2h | Detailed plugin data |
| **Phase 3**: Classification | 1h | Tier ranking |
| **Phase 4**: Proof of Concept | 1.5h | 1 working adapter |
| **Phase 5**: Documentation | 0.5h | Replication guide |

---

## How to Use These Prompts

### Scenario 1: Starting Fresh Session

```bash
# 1. Read quickstart (5 min)
cat .claude/prompts/PLUGIN-RESEARCH-QUICKSTART.md

# 2. Check checklist (2 min)
cat .claude/prompts/PLUGIN-RESEARCH-CHECKLIST.md

# 3. Start Phase 1: Discovery
find . -name "*.java" | xargs grep -l "Plugin\|@Plugin"
```

### Scenario 2: Analyzing a Plugin

```bash
# 1. Copy template
cp .claude/prompts/PLUGIN-RESEARCH-TEMPLATE.md \
   .claude/audit/plugin-<name>.md

# 2. Fill out template
nano .claude/audit/plugin-<name>.md

# 3. Follow sections 1-10 carefully
```

### Scenario 3: Resuming Mid-Session

```bash
# 1. Check current progress
ls -lh .claude/audit/
wc -l .claude/audit/plugin-*.md

# 2. Find checklist progress
grep "^\- \[" .claude/prompts/PLUGIN-RESEARCH-CHECKLIST.md | \
  grep -c "\[x\]"

# 3. Resume from next unchecked item
```

---

## Key Concepts & Definitions

### Tiers (Adaptation Priority)

- **Tier 1** (9-10 score): Must adapt — core functionality
- **Tier 2** (6-8 score): Should adapt — important features
- **Tier 3** (3-5 score): Nice-to-have — optional plugins

### Plugin Types

- **MCP Server**: Anthropic Model Context Protocol integration
- **A2A Endpoint**: Agent-to-Agent communication
- **Hook**: Pre/post processing validation
- **Validator**: H (Guards) or Q (Invariants) phase
- **Agent Delegate**: Autonomous work delegation
- **Integration**: External system connection

### Autonomous Integration Requirements

For a plugin to be "autonomous":
1. ✅ Error-driven (can be triggered by error type)
2. ✅ Routable (decision-engine can assign agents)
3. ✅ JSON-compatible (produces structured receipts)
4. ✅ Team-capable (supports multi-agent work if needed)

---

## Context from Previous Session

### What Was Built

```
.claude/scripts/
├── analyze-errors.sh              (19K, error detection)
├── remediate-violations.sh        (19K, auto-fix)
├── decision-engine.sh             (12K, intelligent routing)
├── stop-hook.sh                   (enhanced, validation orchestration)
├── activate-permanent-team.sh     (400+ LOC, team initialization)
└── test-error-remediation.sh      (15K, test suite)

.claude/.team-state/
├── explore/                       (5 agents, research/investigation)
├── plan/                          (5 agents, architecture/design)
└── implement/                     (5 agents, coding/building)
```

### What's Ready for Integration

- ✅ Error receipt format (JSON, standard structure)
- ✅ Remediation receipt format (JSON, standard structure)
- ✅ Decision routing (agent assignment based on task)
- ✅ Team coordination (mailbox, state files)
- ✅ Loop management (continue/exit logic)

### What the Next Session Should Adapt

All plugins that can be:
- Triggered by error types (H_TODO, H_MOCK, Q_FAKE_RETURN, etc.)
- Routed to agents (yawl-engineer, yawl-validator, yawl-reviewer, etc.)
- Wrapped to produce JSON receipts
- Coordinated across teams if needed

---

## Expected Outputs (by end of next session)

### Deliverables

```
.claude/audit/
├── plugin-inventory.md              (all plugins listed)
├── plugin-analysis-summary.md       (top 10 plugins analyzed)
└── tier-classification.md           (Tier 1/2/3 ranking with justification)

.claude/adapters/
├── plugin-<chosen>-adapter.sh       (working adapter)
└── plugin-<chosen>-adapter.test     (passing tests)

.claude/doc/
└── PLUGIN-ADAPTATION-GUIDE.md       (how to replicate pattern)
```

### Success Criteria

✅ **Session is successful if you can answer**:

1. How many plugins exist? (number)
2. Which 5 matter most? (names)
3. Which one did you adapt? (name + working test)
4. How would you adapt the next one? (step-by-step)

---

## Git Workflow

```bash
# Create branch (if needed)
git checkout -b claude/rewrite-claude-config-VN59M

# During session: commit incremental progress
git add .claude/audit/
git commit -m "Plugin research: Phase 1 discovery complete"

# At end: final comprehensive commit
git add .claude/audit/ .claude/adapters/ .claude/doc/
git commit -m "Plugin research: Full discovery, analysis, and PoC adapter"

# Push to branch
git push -u origin claude/rewrite-claude-config-VN59M
```

---

## Resources & References

### In Codebase

- **YAWL Architecture**: See `.claude/ARCHITECTURE-PATTERNS-JAVA25.md`
- **Autonomous System**: See `CLAUDE.md` section "φ WORKFLOW ORCHESTRATION"
- **Teams Guide**: See `.claude/rules/TEAMS-GUIDE.md`
- **Validation Phases**: See `.claude/rules/validation-phases/`

### Key Documents

- `CLAUDE.md` — Project guidelines and standards
- `.claude/HYPER_STANDARDS.md` — Code quality rules
- `.claude/OBSERVATORY.md` — Codebase facts and analysis

### Team Reference

- **15 agents ready**: EXPLORE (5) + PLAN (5) + IMPLEMENT (5)
- **Team state**: `.claude/.team-state/{explore,plan,implement}/`
- **Configuration**: `.claude/config/permanent-teams.toml`

---

## Support

### If You Get Stuck

1. **Can't find plugins?**
   → See "Quick Commands" in `PLUGIN-RESEARCH-QUICKSTART.md`

2. **Don't know how to analyze?**
   → Copy `PLUGIN-RESEARCH-TEMPLATE.md` and fill it out section by section

3. **Unsure about adaptation?**
   → Start with smallest/simplest plugin (easiest to understand)

4. **Can't make adapter work?**
   → Test plugin manually first, understand input/output, then wrap it

5. **Need more guidance?**
   → Follow `PLUGIN-RESEARCH-CHECKLIST.md` phase by phase

---

## Session Handoff

**From**: Previous session (completed foundation)
**To**: Next session (plugin research & adaptation)
**Status**: 🟢 All prerequisites met, ready to start

**Files waiting for you**:
- ✅ All research prompts (4 files)
- ✅ All previous work (scripts, teams, config)
- ✅ Observatory facts (codebase analyzed)
- ✅ Test frameworks (ready to use)

**Next lead should**:
1. Read this file (README.md) — 5 min
2. Read PLUGIN-RESEARCH-QUICKSTART.md — 10 min
3. Start Phase 1: Discovery — 60 min
4. Track progress with PLUGIN-RESEARCH-CHECKLIST.md

---

**Good luck! The framework is ready. Go discover those plugins! 🚀**
