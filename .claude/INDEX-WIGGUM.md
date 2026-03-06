# YAWL Documentation Index — Van Der Aalst First Principles

**WIGGUM Principle**: "What Would Dr. Wil Van Der Aalst Do?" — Ground all documentation in observable events, not abstract notation.

**Status**: Refactored to first principles (2026-03-06)

---

## Quick Navigation

### 🎯 Start Here

1. **CLAUDE.md** — Core principles & workflow (refactored to observable events)
2. **.claude/FIRST-PRINCIPLES.md** — Why observable events matter
3. **.claude/EVENT-LOG-SCHEMA.md** — What events look like (JSON schema, examples)
4. **.claude/PROCESS-SPECIFICATION.md** — Ideal process workflows (BPMN diagrams)
5. **.claude/CONFORMANCE-CHECKING.md** — How H and Q gates verify correctness

---

## Tier 1: Foundational (Observable Processes)

| Document | Purpose | Read When |
|----------|---------|-----------|
| **FIRST-PRINCIPLES.md** | Why observable events replace abstract notation | Understanding the philosophy |
| **EVENT-LOG-SCHEMA.md** | Event types, trace format, JSON schema, examples | Understanding execution reality |
| **PROCESS-SPECIFICATION.md** | BPMN workflows for build/validate/team/agent | Planning or debugging a phase |
| **CONFORMANCE-CHECKING.md** | H-Guards & Q-Invariants, verification framework | Implementing or fixing violations |

---

## Tier 2: Operational Processes

### .claude/processes/ (Operational Workflows)

| Process | File | Status |
|---------|------|--------|
| Build Process | process-build.md | 🚧 Planned |
| Validation Process | process-validation.md | 🚧 Planned |
| Commit Process | process-commit.md | 🚧 Planned |
| Team Orchestration | process-teams.md | 🚧 Planned |
| Agent Execution | process-agents.md | 🚧 Planned |

Each file shows:
- Observable event sequence (event types, timestamps)
- State transitions
- Decision gates & error paths
- Timeout handling
- Recovery procedures

---

## Tier 3: Rules (Context-Sensitive Guidance)

### .claude/rules/ (24 files, auto-activate by path)

| Path Pattern | Rules Applied |
|--------------|---------------|
| `pom.xml` | dx-workflow.md, maven-modules.md |
| `.claude/rules/teams/**` | team-decision-framework.md |
| `yawl/engine/**` | workflow-patterns.md, interfaces.md |
| `yawl/integration/**` | mcp-a2a-conventions.md |
| `**/*.java` | modern-java.md, chicago-tdd.md |
| `scripts/**`, `*.sh` | shell-conventions.md |
| `schema/**`, `*.xsd` | xsd-validation.md |

**How It Works**: When editing a file, matching rules auto-load without user action.

---

## Tier 4: Legacy/Phase Documentation (Archive)

### .claude/archive/ (Historical Reference)

🚧 **Archival in progress** (PHASE1-6, ACTOR-*, FORTUNE5-*)

Old phase documentation preserved for reference:
- `.claude/archive/PHASE3-*.md` (Phase 3 completion reports)
- `.claude/archive/PHASE4-*.md` (Phase 4 implementation guides)
- `.claude/archive/PHASE5-*.md` (Phase 5 deployment)
- `.claude/archive/PHASE6-*.md` (Phase 6 audit)
- `.claude/archive/ACTOR-*.md` (Actor model design)
- `.claude/archive/FORTUNE5-*.md` (Enterprise integration)

**Use archive only for historical context.** Current guidance comes from Tier 1-3.

---

## Observable Events in Each Context

### When Building (dx.sh all)

Events emitted:
```
ParseStarted → ParseSuccess/Failed
CompileStarted → CompileSuccess/Failed
TestStarted → TestSuccess/Failed
ConformanceCheckStarted → GuardViolationDetected × N → ConformanceCheckPassed/Failed
InvariantCheckStarted → InvariantCheckPassed/Failed
CommitStarted → CommitSuccess/Failed
PushStarted → PushSuccess/Failed
```

Reference: **EVENT-LOG-SCHEMA.md** (Phase-Specific Event Types)

### When Validating Code (H-Guards + Q-Invariants)

Events detected:
```
GuardViolationDetected(H_TODO | H_MOCK | H_STUB | H_EMPTY | H_FALLBACK | H_LIE | H_SILENT)
InvariantCheckFailed(real_impl ∨ throw | ¬deception | code ≈ docs)
```

Reference: **CONFORMANCE-CHECKING.md** (Complete Pattern Definitions & Fixes)

### When Coordinating Teams

Events exchanged:
```
TeamCreated → TaskAssigned → TaskCompleted
MessageSent → MessageAckReceived
TeammateTimeout → RecoveryMessageSent
TeamConsolidated
```

Reference: **PROCESS-SPECIFICATION.md** (Team Orchestration), **TEAMS-GUIDE.md** (Error Recovery)

### When Running Agents

Events recorded:
```
AgentStarted → AgentTaskAssigned → AgentCheckpoint → AgentTaskCompleted
AgentCrash → AgentRecovered
```

Reference: **PROCESS-SPECIFICATION.md** (Agent Autonomous Execution)

---

## Decision Tree: What Should I Read?

```
I'm implementing a feature
├─ Need overall principles? → CLAUDE.md
├─ Need to understand a phase?
│  ├─ Build/compile/test? → PROCESS-SPECIFICATION.md (Phase 2-3)
│  ├─ Validation? → CONFORMANCE-CHECKING.md
│  ├─ Commit? → PROCESS-SPECIFICATION.md (Phase 5-6)
│  └─ Teams or agents? → PROCESS-SPECIFICATION.md (Orchestration)
├─ Got a guard violation? → CONFORMANCE-CHECKING.md (H Phase)
├─ Got an invariant failure? → CONFORMANCE-CHECKING.md (Q Phase)
└─ Need context-specific rules? → .claude/rules/ (auto-loads by file path)

I'm debugging a failure
├─ Which event failed? → EVENT-LOG-SCHEMA.md
├─ Expected event sequence → PROCESS-SPECIFICATION.md
├─ How to fix it? → CONFORMANCE-CHECKING.md (for H/Q) or rule docs

I'm planning a team
├─ Decision to use teams? → FIRST-PRINCIPLES.md (Parallel Orchestration)
├─ Formation & dispatch? → PROCESS-SPECIFICATION.md (Team Orchestration)
├─ Error recovery? → .claude/rules/TEAMS-GUIDE.md
└─ Timeouts & reassignment? → .claude/rules/TEAMS-QUICK-REF.md

I'm new to YAWL
1. Start with CLAUDE.md (overall principles)
2. Read FIRST-PRINCIPLES.md (why observable events)
3. Skim PROCESS-SPECIFICATION.md (workflow diagrams)
4. Reference others as needed per context
```

---

## Key Concepts Mapping

| Old (Greek Notation) | New (Observable) | Learn From |
|---------------------|------------------|-----------|
| Ψ Observatory | Event Log Collection | EVENT-LOG-SCHEMA.md |
| Λ Build | Build Phase Process | PROCESS-SPECIFICATION.md |
| H Guards | Trace Conformance (H Phase) | CONFORMANCE-CHECKING.md |
| Q Invariants | State Reachability (Q Phase) | CONFORMANCE-CHECKING.md |
| Ω Git | Atomic Event Trace | PROCESS-SPECIFICATION.md (Phase 5-6) |
| τ Teams | Parallel Event Streams | PROCESS-SPECIFICATION.md (Orchestration) |
| μ Agents | Autonomous Executors | PROCESS-SPECIFICATION.md (Agents) |
| R Rules | Context-Sensitive Guidance | .claude/rules/ |

---

## File Organization

```
.claude/
├── FIRST-PRINCIPLES.md          ✓ Observable events, van der Aalst
├── EVENT-LOG-SCHEMA.md          ✓ Event types, traces, JSON schema
├── PROCESS-SPECIFICATION.md     ✓ BPMN workflows, process models
├── CONFORMANCE-CHECKING.md      ✓ H-Guards & Q-Invariants, verification
├── INDEX-WIGGUM.md              ✓ This file (new structure guide)
│
├── processes/                   🚧 Planned: detailed phase workflows
│   ├── process-build.md
│   ├── process-validation.md
│   ├── process-commit.md
│   ├── process-teams.md
│   └── process-agents.md
│
├── rules/                       ✓ Existing: context-sensitive guidance
│   ├── team-decision-framework.md
│   ├── TEAMS-GUIDE.md
│   ├── TEAMS-QUICK-REF.md
│   ├── shell-conventions.md
│   ├── build/
│   ├── validation/
│   ├── teams/
│   └── ... (24 total)
│
└── archive/                     🚧 Planned: legacy phase documentation
    ├── PHASE3-*.md
    ├── PHASE4-*.md
    ├── PHASE5-*.md
    ├── PHASE6-*.md
    ├── ACTOR-*.md
    └── FORTUNE5-*.md

✓ = Complete | 🚧 = Planned
```

---

## Verification Checklist

Does this refactoring achieve WIGGUM principles?

- [x] **No Greek notation in core docs** (Ψ/Λ/μ replaced with event names)
- [x] **Every phase has observable event examples** (EVENT-LOG-SCHEMA.md)
- [x] **All rules derive from observable processes** (PROCESS-SPECIFICATION.md)
- [x] **Conformance grounded in events** (CONFORMANCE-CHECKING.md)
- [x] **De-duplication started** (archive/ for legacy docs)
- [x] **Single source of truth per concept** (one INDEX, no duplication)
- [ ] **Process-specific docs complete** (.claude/processes/ filled in)
- [ ] **Legacy docs archived** (150+ PHASE/ACTOR/FORTUNE5 files)

---

## Metadata

| Attribute | Value |
|-----------|-------|
| Framework | Van der Aalst Process Mining |
| Principle | Observable Events over Abstract Notation |
| Last Refactored | 2026-03-06 |
| Maintained By | YAWL Core Team |
| Python Files | 0 (all Markdown) |
| Total Size | ~2.5 MB (compressed later) |

---

## Next Steps

1. ✅ Foundational docs written (FIRST-PRINCIPLES, EVENT-LOG-SCHEMA, PROCESS-SPECIFICATION, CONFORMANCE-CHECKING)
2. ✅ CLAUDE.md refactored (Greek notation removed)
3. ✅ Directory structure created (processes/, archive/)
4. 🚧 Complete .claude/processes/ (5 operational workflow docs)
5. 🚧 Archive legacy documentation (move 100+ files)
6. 🚧 Final verification (no Greek notation, all links updated)

---

## GODSPEED. ✈️

*Van der Aalst said: "Process mining is about observing what actually happens, not what we think should happen." Let observable events guide your decisions.*
