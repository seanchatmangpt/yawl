# GODSPEED-ggen Architecture Index

**Quick Navigation for Team Members**

---

## Core Documents (5 Total)

### For Decision-Makers & Stakeholders
**Start here if you have 5 minutes**

→ [GODSPEED-GGEN-EXECUTIVE-SUMMARY.md](./GODSPEED-GGEN-EXECUTIVE-SUMMARY.md)
- What problem does GODSPEED-ggen solve?
- Why is it needed?
- How does it work (visually)?
- Example end-to-end flow

### For Architects & Maintainers
**Comprehensive specification (read fully)**

→ [GODSPEED-GGEN-ARCHITECTURE.md](./GODSPEED-GGEN-ARCHITECTURE.md)
- 11 sections covering design, implementation, integration
- Data flow diagrams
- Guard patterns (14 rules)
- Invariant rules (Q gate)
- Configuration format (ggen.toml)
- RDF/SHACL ontologies
- Success criteria
- Failure scenarios
- Design decisions

### For Developers (Daily Reference)
**One-page quick lookup**

→ [GODSPEED-QUICK-REFERENCE.md](./GODSPEED-QUICK-REFERENCE.md)
- 5-phase circuit (table)
- Guard patterns (checklist)
- Invariant rules (SPARQL query)
- Configuration example
- Receipt structure
- Drift detection
- Exit codes
- Common errors & fixes
- Testing commands

### For Implementation Teams (Execution Roadmap)
**4-week plan with task breakdown**

→ [GODSPEED-IMPLEMENTATION-ROADMAP.md](./GODSPEED-IMPLEMENTATION-ROADMAP.md)
- Week 1: CLI + session tracking + receipts
- Week 2a: Ψ phase (Observatory)
- Week 2b: Λ phase (Build)
- Week 3a: H phase (Guards)
- Week 3b: Q phase (Invariants)
- Week 4a: Ω phase (Git)
- Week 4b: Integration testing
- Week 4c: Documentation + delivery
- Success criteria
- Risk matrix

### For Task Coordination (Handoff Document)
**Summary + task breakdown**

→ [TASK-1-DELIVERABLES.md](./TASK-1-DELIVERABLES.md) (this document)
- What was delivered
- Architecture at-a-glance
- For implementation teams (Tasks 2-5)
- How to use these documents
- Next steps

---

## The Architecture (TL;DR)

### The Circuit
```
Ψ (Observatory)  → Λ (Build)  → H (Guards)  → Q (Invariants) → Ω (Git)
↓
Load facts.json   Compile    Block anti-   Enforce real      Atomic
Verify SHA256               patterns      impl ∨ throw      commit
Detect drift
```

### The Guarantee
**Every commit that passes all 5 gates is guaranteed to:**
- Have fresh facts (Ψ) ✓
- Compile cleanly (Λ) ✓
- Have no TODO/mock/stub patterns (H) ✓
- Have no missing implementations (Q) ✓
- Be atomically committed (Ω) ✓

**Result**: Zero drift. No surprises. Full audit trail.

### The Implementation
- **ggen-godspeed** CLI (new)
- **5 phase modules** (new)
- **Receipt framework** (new)
- **Configuration** (ggen.toml [godspeed] section)
- **Integration** (4 hooks: SessionStart, PreToolUse, PostToolUse, PreCommit)

---

## By Role

### If You Are... Start With...

**Project Lead**
1. GODSPEED-GGEN-EXECUTIVE-SUMMARY.md (understand the problem)
2. GODSPEED-IMPLEMENTATION-ROADMAP.md (4-week plan)
3. TASK-1-DELIVERABLES.md (task assignments)

**Architect/Reviewer**
1. GODSPEED-GGEN-ARCHITECTURE.md (full spec)
2. GODSPEED-QUICK-REFERENCE.md (design decisions)
3. GODSPEED-IMPLEMENTATION-ROADMAP.md (validation plan)

**Developer (Engineer A, B, C, D)**
1. GODSPEED-QUICK-REFERENCE.md (phase overview)
2. GODSPEED-IMPLEMENTATION-ROADMAP.md (your task section)
3. GODSPEED-GGEN-ARCHITECTURE.md (detailed spec)

**QA/Tester**
1. GODSPEED-QUICK-REFERENCE.md (test scenarios)
2. GODSPEED-IMPLEMENTATION-ROADMAP.md (integration tests section)
3. GODSPEED-GGEN-ARCHITECTURE.md (failure scenarios)

**DevOps/CI-CD**
1. GODSPEED-QUICK-REFERENCE.md (exit codes, receipts)
2. GODSPEED-GGEN-ARCHITECTURE.md (integration points)
3. GODSPEED-IMPLEMENTATION-ROADMAP.md (CI setup)

---

## Key Sections by Topic

### Understanding the Problem
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "What" + "Why"
- GODSPEED-GGEN-ARCHITECTURE.md § "Key Design Decisions"
- TASK-1-DELIVERABLES.md § "Why GODSPEED-ggen Works"

### Understanding the Circuit
- GODSPEED-QUICK-REFERENCE.md → "The 5-Phase Circuit"
- GODSPEED-GGEN-ARCHITECTURE.md § "Integration Model"
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "The 5-Phase Circuit"

### Understanding Guard Patterns (H Gate)
- GODSPEED-QUICK-REFERENCE.md → "Guard Patterns"
- GODSPEED-GGEN-ARCHITECTURE.md § "Guard Patterns"
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "Guard Patterns Table"

### Understanding Invariant Rules (Q Gate)
- GODSPEED-QUICK-REFERENCE.md → "Invariant Rules"
- GODSPEED-GGEN-ARCHITECTURE.md § "Invariant Rule"
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "Invariant Patterns"

### Understanding Configuration
- GODSPEED-QUICK-REFERENCE.md → "Configuration"
- GODSPEED-GGEN-ARCHITECTURE.md § "Configuration Format"
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "Configuration Example"

### Understanding Drift Detection
- GODSPEED-QUICK-REFERENCE.md → "Drift Detection"
- GODSPEED-GGEN-ARCHITECTURE.md § "Drift Detector"
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "Drift Detection"

### Understanding Receipts (Audit Trail)
- GODSPEED-QUICK-REFERENCE.md → "Receipt Structure"
- GODSPEED-GGEN-ARCHITECTURE.md § "Durable State"
- GODSPEED-GGEN-EXECUTIVE-SUMMARY.md → "Data Flow"

### Implementation Planning
- GODSPEED-IMPLEMENTATION-ROADMAP.md (week-by-week)
- TASK-1-DELIVERABLES.md § "For Implementation Teams"
- GODSPEED-GGEN-ARCHITECTURE.md § "Implementation Roadmap"

### Troubleshooting
- GODSPEED-QUICK-REFERENCE.md → "Common Errors & Fixes"
- GODSPEED-GGEN-ARCHITECTURE.md § "Failure Scenarios"
- GODSPEED-IMPLEMENTATION-ROADMAP.md → "Risk Matrix"

---

## Cross-Document Index

### Guard Patterns (14 Rules)

| Rule | Location |
|------|----------|
| 1. TODO/FIXME/XXX | All 3 docs |
| 2. Mock classes | Quick-ref, Architecture |
| 3. Empty returns | All 3 docs |
| 4. Empty methods | Architecture, Quick-ref |
| 5-14. Others | Architecture § "Guard Patterns" |

### Phases (Ψ→Λ→H→Q→Ω)

| Phase | Description | Location |
|-------|-------------|----------|
| **Ψ** | Observatory | All 5 docs |
| **Λ** | Build | All 5 docs |
| **H** | Guards | All 5 docs |
| **Q** | Invariants | All 5 docs |
| **Ω** | Git | Executive summary, Architecture, Quick-ref |

### Tasks (T2-T5)

| Task | Engineer | Phase(s) | Details |
|------|----------|----------|---------|
| T2 | Engineer A | Ψ | Roadmap Week 2a, Quick-ref, Architecture |
| T3 | Engineer B | Λ | Roadmap Week 2b, Quick-ref, Architecture |
| T4 | Engineer C | H | Roadmap Week 3a, Quick-ref, Architecture |
| T5 | Engineer D | Q + Ω | Roadmap Week 3b+4a, Quick-ref, Architecture |

---

## Document Relationships

```
TASK-1-DELIVERABLES.md (YOU ARE HERE)
│
├─→ GODSPEED-GGEN-EXECUTIVE-SUMMARY.md
│   (5 min read, decision-makers)
│   └─→ GODSPEED-GGEN-ARCHITECTURE.md
│       (comprehensive spec, architects)
│       └─→ GODSPEED-QUICK-REFERENCE.md
│           (daily reference, developers)
│           └─→ GODSPEED-IMPLEMENTATION-ROADMAP.md
│               (execution plan, teams)
```

---

## How to Navigate

### "I want to understand the problem"
→ GODSPEED-GGEN-EXECUTIVE-SUMMARY.md (read fully)

### "I want the full specification"
→ GODSPEED-GGEN-ARCHITECTURE.md (read fully)

### "I need a quick lookup"
→ GODSPEED-QUICK-REFERENCE.md (search by topic)

### "I need to implement Task 2-5"
→ GODSPEED-IMPLEMENTATION-ROADMAP.md (your week + task)

### "I need to coordinate the team"
→ TASK-1-DELIVERABLES.md (assignments + handoff)

### "I want to understand one specific aspect"
→ This index (cross-reference)

---

## File Locations (Absolute Paths)

```
/home/user/yawl/.claude/
├─ GODSPEED-GGEN-ARCHITECTURE.md              (primary spec)
├─ GODSPEED-GGEN-EXECUTIVE-SUMMARY.md         (summary)
├─ GODSPEED-QUICK-REFERENCE.md                (quick lookup)
├─ GODSPEED-IMPLEMENTATION-ROADMAP.md         (4-week plan)
├─ TASK-1-DELIVERABLES.md                     (delivery summary)
├─ GODSPEED-GGEN-INDEX.md                     (this file)
├─ ARCHITECTURE-DELIVERY-SUMMARY.md           (meta-summary)
├─ hooks/
│  ├─ hyper-validate.sh                       (H phase: guard validation)
│  ├─ post-edit.sh                            (integrates H phase)
│  └─ pre-task.sh                             (integrates Ψ phase)
├─ rules/
│  └─ teams/
│     ├─ team-decision-framework.md           (team usage rules)
│     ├─ error-recovery.md                    (error handling)
│     └─ session-resumption.md                (state persistence)
└─ skills/
   └─ [existing yawl-* skills]
```

---

## Glossary (Quick Lookup)

| Term | Definition | Docs |
|------|-----------|------|
| **Ψ (Psi)** | Observatory phase | All |
| **Λ (Lambda)** | Build phase | All |
| **H (Guards)** | Guard patterns (14 rules) | All |
| **Q (Invariants)** | Real impl ∨ throw | All |
| **Ω (Omega)** | Git commit phase | Summary, Architecture, Quick-ref |
| **Drift (Σ)** | Code divergence from design | All |
| **Receipt** | Durable JSON record (SHA256-signed) | All |
| **SPARQL** | RDF query language | Architecture, Quick-ref |
| **SHACL** | RDF constraint language | Architecture, Quick-ref |
| **emit_channel** | Git files to stage (src/, test/, .claude/) | All |
| **Poka-Yoke** | Error-proofing (H phase engine) | Architecture, Roadmap |
| **Gate** | Phase validation checkpoint | All |

---

## Quick Links (Within Documents)

### ARCHITECTURE.md
- [1. Integration Model](#1-integration-model)
- [2. Data Flow](#2-data-flow)
- [3. ggen Extensions](#3-ggen-extensions-required)
- [4. Configuration](#4-configuration-format)
- [5. Ontology](#5-ontology-structure)
- [6. Success Criteria](#6-success-criteria--validation)
- [7. Roadmap](#7-implementation-roadmap)
- [8. Design Decisions](#8-key-design-decisions)
- [9. Integration Points](#9-integration-with-yawl-codebase)
- [10. Metrics](#10-metrics--observability)
- [11. Failures](#11-failure-scenarios--recovery)

### EXECUTIVE-SUMMARY.md
- [The Problem](#the-problem-godspeed-ggen-solves)
- [Key Components](#key-components)
- [Guard Patterns](#guard-patterns-h-gate)
- [Invariant Rules](#invariant-patterns-q-gate)
- [Data Flow](#data-flow-config--receipts)
- [Configuration](#configuration-example)
- [Implementation](#implementation-phases)
- [Why It Works](#why-this-works)
- [Example](#example-end-to-end-flow)

### QUICK-REFERENCE.md
- [5-Phase Circuit](#the-5-phase-circuit)
- [Guard Patterns](#guard-patterns-h-gate--forbidden-in-code)
- [Invariant Rules](#invariant-rules-q-gate--enforce-real-implementation)
- [Configuration](#configuration-ggentoml-godspeed-section)
- [Receipt Structure](#receipt-structure-durable-audit-trail)
- [Drift Detection](#drift-detection--σ--0-principle)
- [Exit Codes](#exit-codes)
- [Error Fixes](#common-errors--fixes)
- [Testing](#testing-individual-phases)

---

## For Meetings & Presentations

### 5-Minute Overview
Use: GODSPEED-GGEN-EXECUTIVE-SUMMARY.md
Slides: "The 5-Phase Circuit" + "Why This Works"

### 30-Minute Deep Dive
Use: GODSPEED-GGEN-ARCHITECTURE.md
Slides: Sections 1-6 (model, flow, extensions, config, ontology, criteria)

### 90-Minute Architecture Review
Use: All documents
Agenda:
- Problem statement (10 min)
- Architecture overview (20 min)
- Design decisions Q&A (20 min)
- Integration points (20 min)
- Implementation roadmap Q&A (20 min)

### Weekly Standup (15 min)
Use: GODSPEED-QUICK-REFERENCE.md + your task section in Roadmap
Format: [Phase] [Status] [Blocker] [ETA]

---

## Success Tracking

### Week 1 Metrics
- [ ] CLI builds + runs
- [ ] Session tracking works
- [ ] Receipts emit correctly
- [ ] Config loads + validates
- [ ] Unit tests >80%

### Week 2 Metrics
- [ ] Ψ phase: facts load + drift detects
- [ ] Λ phase: compile + test stubs work
- [ ] Both integrated into main circuit
- [ ] Integration tests pass

### Week 3 Metrics
- [ ] H phase: 14 patterns detected
- [ ] Q phase: invariant rules enforced
- [ ] Both integrated + tested
- [ ] Negative tests pass (inject violations, verify blocking)

### Week 4 Metrics
- [ ] Ω phase: atomic commit + push
- [ ] Full E2E circuit (Ψ→Λ→H→Q→Ω)
- [ ] Drift detection test passes
- [ ] Phase independence test passes
- [ ] Documentation complete
- [ ] CI/CD integration done

---

## Document Statistics

| Document | Words | Sections | Readers |
|----------|-------|----------|---------|
| ARCHITECTURE.md | ~2500 | 11 | Architects, maintainers |
| EXECUTIVE-SUMMARY.md | ~1500 | 10 | Decision-makers |
| QUICK-REFERENCE.md | ~1000 | 15 | Developers |
| IMPLEMENTATION-ROADMAP.md | ~2000 | 8 | Teams, managers |
| TASK-1-DELIVERABLES.md | ~1500 | 10 | Coordinators |
| ARCHITECTURE-DELIVERY-SUMMARY.md | ~1500 | 8 | Synchronization |
| **Total** | **~9500** | **~60** | **All** |

---

## Feedback & Updates

**Questions?** Check:
1. GODSPEED-QUICK-REFERENCE.md (quick answers)
2. GODSPEED-GGEN-ARCHITECTURE.md (detailed answers)
3. Message in Slack: #godspeed-ggen

**Found an issue?** Open GitHub issue with link to document + section.

**Want to contribute?** Start with architecture review (Slack #godspeed-ggen), then submit PR.

---

## Version History

| Version | Date | Status | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-21 | Complete | Initial architecture design |
| — | — | — | (Future updates tracked here) |

---

**Document**: GODSPEED-ggen Architecture Index
**Version**: 1.0
**Date**: 2026-02-21
**Status**: Ready for Use
**Use this to navigate between all 6 architecture documents**
