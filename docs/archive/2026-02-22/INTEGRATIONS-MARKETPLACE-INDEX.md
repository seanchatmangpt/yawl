# Integration Marketplace MVP - Complete Index

**Project**: YAWL Integrations Marketplace (Phase 1 - MVP)
**Date**: 2026-02-21
**Status**: Design Complete, Ready for Implementation

---

## What is the Integration Marketplace?

The Integration Marketplace enables YAWL **skills** (workflow capabilities) to be automatically discovered and invoked by three classes of external systems:

1. **Z.AI Agents** — Autonomous agents that call skills as MCP tools
2. **Peer A2A Agents** — Other workflow systems that handoff work to YAWL
3. **LLMs** (Claude, GPT-4, etc.) — Large language models that use skills as native tools

Each skill is published once (as a Java class), then automatically available through all three pathways via **connector manifests** (YAML files).

**Example**: A procurement team defines an `ApprovalSkill` once. Then:
- Z.AI agent discovers it as "Approve PO" tool → autonomous approval
- A2A agent sends handoff message → YAWL routes to skill
- Claude asks "Approve this PO" → Skill executes via MCP

---

## Documentation Structure

This project has **5 main design documents** + **7 configuration files**. Read them in order:

### 1. Start Here: Project Summary
**File**: `INTEGRATIONS-MARKETPLACE-MVP-SUMMARY.md` (22 KB)
**Read Time**: 15 minutes
**What**: Overview of entire MVP, deliverables, 4-week roadmap, success metrics
**Who**: Anyone (stakeholders, engineers, managers)
**Key Sections**:
- Executive summary (1 page)
- 4-week roadmap (table)
- Architecture diagram
- Success metrics (11 KPIs)
- File structure (complete directory tree)

**Next Step**: If you want implementation details, go to #2. If you want day-by-day tasks, go to #3.

---

### 2. Deep Dive: Architectural Design
**File**: `INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md` (34 KB)
**Read Time**: 45 minutes
**What**: Detailed architecture of Z.AI, A2A, MCP connectors + code examples
**Who**: Architects, senior engineers, tech leads
**Key Sections**:
- 3 integration types (80/20 focus)
- Core MVP scope (registry, 5 connectors, discovery API)
- Tech stack (Git, YAML, Spring, MCP/A2A)
- 4 integration points (InterfaceB, auth, metrics, reputation)
- Code artifacts (18 classes, 12 tests)
- Phase 2 preview

**Reading Tips**:
- Section 2 defines the 3 connector types
- Section 3 details Git-backed registry with YAML
- Section 5 shows all deliverables with file paths
- Section 7 explains how connectors integrate with YAWL

**Next Step**: Ready to implement? Go to #3. Want API details? See section 2.3 (Discovery API).

---

### 3. Day-by-Day: Implementation Checklist
**File**: `INTEGRATIONS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md` (25 KB)
**Read Time**: 30 minutes
**What**: Detailed 4-week task breakdown, test plan, code review gates
**Who**: Implementation engineers (Engineers A & B)
**Key Sections**:
- Week 1: Data models, Git registry, YAML validation
- Week 2: Z.AI adapter, schema generator, 2 reference skills
- Week 3: A2A adapter, capability mapping, 2 reference skills
- Week 4: Discovery API, MCP tools, full integration tests
- Validation gates (end of each week)
- Code quality standards (GODSPEED)
- Daily standup template
- Budget breakdown ($50K total)

**How to Use**:
- Start Monday morning of Week 1
- Check off tasks as you complete them
- Run validation gate tests on Friday
- Move to next week

**Next Step**: Print this file and tack it to your monitor. Use daily.

---

### 4. Registry Guide: How to Use
**File**: `.integrations/README.md` (14 KB)
**Read Time**: 20 minutes
**What**: How to create, discover, and troubleshoot connectors
**Who**: YAWL developers, operators, integrators
**Key Sections**:
- Registry structure (directory layout)
- Connector YAML format (complete spec)
- Creating a new connector (step-by-step)
- Discovery API endpoints (curl examples)
- Best practices (idempotency, correlation IDs, permissions)
- Troubleshooting guide

**How to Use**:
- When you want to understand connector YAML format → Section "Connector YAML Format"
- When you want to create a new skill → Section "Creating a New Connector"
- When discovery API is broken → Section "Troubleshooting"

**Next Step**: Bookmark this file. You'll reference it frequently.

---

### 5. Schema Reference
**File**: `.integrations/connector-schema.json` (7.2 KB)
**Read Time**: 5 minutes (skim only)
**What**: JSON Schema for validating connector YAML files
**Who**: Developers writing connector YAMLs, validation code
**Key Sections**:
- Required fields (id, name, type, version, description, skill)
- Optional fields (config, capabilities, metadata)
- Enum validation (type ∈ {z-ai, a2a, mcp})
- Example connector (full YAML)

**How to Use**:
- When writing connector YAML, reference this schema
- Validation code uses this to check YAML files
- Don't need to read it fully unless writing validation code

**Next Step**: Keep open in editor when writing connector YAMLs.

---

## File Locations

### Design Documents (Root of `/home/user/yawl/`)
```
INTEGRATIONS-MARKETPLACE-MVP-SUMMARY.md        ← Start here
INTEGRATIONS-MARKETPLACE-MVP-DESIGN.md         ← Architecture deep dive
INTEGRATIONS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md  ← Week-by-week tasks
INTEGRATIONS-MARKETPLACE-INDEX.md              ← This file
```

### Registry & Configuration (`.integrations/` directory)
```
.integrations/
├─ README.md                    ← Registry usage guide
├─ QUICK-START.md              ← 5-minute startup (not yet created)
├─ connector-schema.json        ← YAML schema (validation rules)
├─ z-ai/                        ← Z.AI connectors (Week 2)
│  ├─ approval-skill.yaml       ├─ Will be created during implementation
│  └─ expense-report-skill.yaml │
├─ a2a/                         ├─ Will be created during implementation
│  ├─ purchase-order-skill.yaml │
│  └─ invoice-skill.yaml        │
└─ mcp/                         ├─ Will be created during implementation
   ├─ case-monitor.yaml         │
   └─ expense-report.yaml       └─ Will be created during implementation
```

### Java Source Code (Will be created in `yawl-integration` module)
```
src/org/yawlfoundation/yawl/integration/connector/
├─ ConnectorMetadata.java
├─ ConnectorRegistry.java
├─ GitBackedConnectorRegistry.java
├─ ... (18 total classes across 5 categories)
└─ [Tests in src/test/]
```

---

## Reading Paths by Role

### I'm a Manager/Stakeholder
Read in this order:
1. **MVP-SUMMARY** (Section 1: Executive Summary) — 5 min overview
2. **MVP-SUMMARY** (Section 2: Deliverables) — understand what's being built
3. **MVP-SUMMARY** (Section 4: 4-Week Roadmap) — understand timeline
4. **MVP-SUMMARY** (Section 5: Success Metrics) — understand how we measure success

**Total**: 20 minutes. You'll understand budget, timeline, deliverables, and success metrics.

---

### I'm a Tech Lead/Architect
Read in this order:
1. **MVP-SUMMARY** (full document) — 20 min overview
2. **MVP-DESIGN** (Section 1-4: Types, Scope, Tech Stack) — 20 min
3. **MVP-DESIGN** (Section 5-7: Integration Points) — 20 min
4. **REGISTRY-README** (Section 1-3: Overview, Format) — 15 min

**Total**: 75 minutes. You'll understand architecture, integration points, and how operators use it.

---

### I'm an Implementation Engineer (Week 1)
Read in this order:
1. **MVP-SUMMARY** (Sections 1-2: What + Deliverables) — 10 min
2. **MVP-DESIGN** (Sections 2-3: Scope, Registry) — 20 min
3. **IMPLEMENTATION-CHECKLIST** (Week 1 section) — 20 min
4. **REGISTRY-README** (Section 2: YAML Format) — 10 min

**Total**: 60 minutes. Now start coding Week 1 tasks.

---

### I'm an Implementation Engineer (Week 2)
Same as Week 1, but focus on:
1. **MVP-DESIGN** (Section 1.1: Z.AI Connector) — 10 min
2. **MVP-DESIGN** (Section 3.3 & 3.4: Z.AI Adapter, MCP Registrar) — 20 min
3. **IMPLEMENTATION-CHECKLIST** (Week 2 section) — 20 min

**Total**: 50 minutes. Now start coding Week 2 tasks.

---

### I'm a QA Engineer
Read in this order:
1. **MVP-SUMMARY** (Section 6: Success Metrics) — 5 min
2. **IMPLEMENTATION-CHECKLIST** (Full document) — 30 min
3. **MVP-DESIGN** (Section 4: Basic Testing Framework) — 10 min

**Total**: 45 minutes. You'll understand test plan and validation gates for each week.

---

### I'm an Operator/DevOps
Read in this order:
1. **MVP-SUMMARY** (Sections 1, 7, 8: Overview + Deployment) — 15 min
2. **REGISTRY-README** (Section 4-5: Discovery API, Creating Connectors) — 20 min
3. **REGISTRY-README** (Section 6: Troubleshooting) — 10 min

**Total**: 45 minutes. You'll understand how to operate and troubleshoot the registry.

---

## Key Artifacts Summary

### What We're Building

| Artifact | Type | Week | Count | Purpose |
|----------|------|------|-------|---------|
| ConnectorRegistry | Java class | 1 | 1 | Git-backed lookup of connectors |
| Connector YAML | Config | 1+ | 5 | Manifests for skills (Z.AI, A2A, MCP) |
| Z.AI Adapter | Java code | 2 | 2 cls | Maps MCP ↔ SkillRequest |
| A2A Adapter | Java code | 3 | 4 cls | Maps A2A message ↔ SkillRequest |
| Discovery API | REST | 4 | 2 cls | GET /integrations/connectors, etc. |
| Reference Skills | Java code | 2-4 | 5 | ApprovalSkill, POSkill, InvoiceSkill, etc. |
| Tests | JUnit | 1-4 | 12 | Unit + integration tests, ≥80% coverage |

### Timeline

```
Feb 24-28 (Week 1): Registry ready, 3 test connectors load
Mar 3-7  (Week 2): Z.AI connectors working, mock agent invokes skill
Mar 10-14 (Week 3): A2A connectors working, peer agent handoff works
Mar 17-21 (Week 4): MCP tools ready, discovery API operational
        ↓
Mar 21  : DEMO — All 5 connectors work (Z.AI, A2A×2, MCP×2)
        ↓
Mar 24  : Phase 2 begins (OAuth, webhooks, real-time metrics)
```

---

## Critical Decision Points

### 1. Git as Source of Truth (Week 1)
**Decision**: Store connector YAML in `.integrations/` directory, version-controlled by Git.
**Why**: Simple, auditable, integrates with existing Git workflow.
**Alternative**: Database (rejected: adds complexity, harder to version).
**Implication**: Connectors reload via post-commit hook.

### 2. 80/20 MVP Scope (All Weeks)
**Decision**: Focus on Z.AI, A2A, MCP. Defer OAuth, webhooks, marketplace UI.
**Why**: Core invocation paths cover 80% of use cases.
**Alternative**: Build complete marketplace (rejected: 8-week effort, not MVP).
**Implication**: Phase 2 will add advanced features.

### 3. No New InterfaceB Methods (All Weeks)
**Decision**: Reuse existing InterfaceB API, don't extend it.
**Why**: Reduces risk, avoids coupling.
**Alternative**: Add new InterfaceB methods for skill invocation (rejected: engine layer shouldn't know about skills).
**Implication**: Skills call existing InterfaceB methods (launchCase, getWorkItems, etc.).

---

## Questions & Answers

**Q: Why 4 weeks? Can't we do this in 2?**
A: 4 weeks allows proper testing, documentation, and code review. Week 1 is just data models + registry (foundation). Weeks 2-4 are integrations + 5 reference skills + discovery API.

**Q: What if a skill fails to execute?**
A: Skill returns SkillResult(false, errorMap). Adapter translates to appropriate error format (MCP error, A2A error response, HTTP 500). No silent failures.

**Q: How do I add a 6th connector after MVP?**
A: Create a new YAML file in `.integrations/{type}/` and a Java skill class. Registry auto-discovers on next reload (5-min poll or commit hook).

**Q: Can connectors be updated without redeploying code?**
A: Yes! Update the YAML file (capabilities, metadata, config), commit to Git, registry reloads. No code redeploy needed unless you change skill logic.

**Q: How do we handle versioning (connector v1.0 → v1.1)?**
A: Each connector YAML has a `version` field. For Phase 2, we'll add semantic versioning + backward compatibility checks.

**Q: What about skill permissions?**
A: Skill defines required permissions (e.g., "po:write", "approvals:execute"). At invocation, AuthenticatedPrincipal's permissions are checked. If insufficient, skill returns error.

**Q: Can a skill be exposed through all 3 pathways (Z.AI + A2A + MCP) simultaneously?**
A: Yes! One skill → multiple connector YAMLs (z-ai/skill.yaml, a2a/skill.yaml, mcp/skill.yaml).

---

## Success Stories (Examples)

### Story 1: Z.AI Approves Purchase Orders
```
Z.AI agent: "I need to approve 50 POs for vendor Acme Corp"
  ↓
Z.AI discovers tool via MCP: "cases/submit" (Z.AI connector)
  ↓
Z.AI invokes skill 50 times with idempotency key (no duplicates)
  ↓
50 PO approval workflows launch in YAWL
  ↓
Finance team reviews/completes them
  ↓
Result: 50 POs approved autonomously
```

### Story 2: Cross-Org Workflow Handoff
```
Partner org A (using different BPM): "Process this invoice"
  ↓
Sends A2A message to YAWL (A2A connector)
  ↓
YAWL routes to InvoiceProcessingSkill
  ↓
Workflow launches, handles invoice processing
  ↓
Response sent back to Partner org A with case ID
  ↓
Result: Transparent cross-org workflow execution
```

### Story 3: Claude Drafts Invoice
```
User: "Claude, draft an expense report for my trip to NYC"
  ↓
Claude discovers tools via MCP: "submit_expense_report" (MCP connector)
  ↓
Claude calls skill with trip details
  ↓
Skill validates, launches expense workflow
  ↓
Workflow executes, Claude receives case ID
  ↓
Claude drafts email: "I've submitted your expense report, case #E-12345"
  ↓
Result: Natural LLM integration with YAWL workflows
```

---

## What's NOT in MVP (Phase 2)

- OAuth credential management
- Webhook callbacks for async results
- Real-time reputation scoring (Kafka aggregation)
- Connector marketplace UI
- Advanced filtering/search
- Connector versioning system
- Multi-tenant support
- Rate limiting per connector
- Audit logging

---

## Getting Help

**I don't understand the architecture**
→ Read MVP-DESIGN section 1 (3 integration types)

**I don't know what to code this week**
→ Read IMPLEMENTATION-CHECKLIST for your week

**I need to add a new connector**
→ Read REGISTRY-README section "Creating a New Connector"

**The discovery API is slow**
→ Read MVP-DESIGN section 2.3 (caching strategy)

**How do I test my work?**
→ Read IMPLEMENTATION-CHECKLIST "Validation Gate" for your week

**When is Phase 2?**
→ It starts Mar 24. Phase 1 ends Mar 21 (demo day).

---

## Checklist: Before Starting Implementation

**Week 1 Engineer (Engineer A)**:
- [ ] Read MVP-SUMMARY (full)
- [ ] Read MVP-DESIGN sections 1-3
- [ ] Read IMPLEMENTATION-CHECKLIST Week 1
- [ ] Read REGISTRY-README section 2
- [ ] Understand ConnectorMetadata record structure
- [ ] Know what GitBackedConnectorRegistry does
- [ ] Have Git + Maven environment ready
- [ ] Understand GODSPEED standards (H, Q, Λ, Ω gates)

**Week 2 Engineer (Engineer B)**:
- [ ] Read everything Engineer A read
- [ ] Additionally: MVP-DESIGN section 1.1 (Z.AI Connector)
- [ ] Additionally: MVP-DESIGN section 3.3-3.4 (Z.AI Adapter + MCP)
- [ ] Understand @MCPTool annotation pattern
- [ ] Know how Z.AIConnectorAdapter works
- [ ] Understand idempotency key tracking
- [ ] Know how to generate JSON schema from Java class

**Week 3 Engineer (Engineer A, Returns)**:
- [ ] Review Week 2 code (Z.AI work)
- [ ] Read MVP-DESIGN section 1.2 (A2A Adapter)
- [ ] Read IMPLEMENTATION-CHECKLIST Week 3
- [ ] Understand A2A message format (parts[], context)
- [ ] Know how capability mapping works (JSONPath)
- [ ] Understand correlation ID propagation

**Week 4 Engineer (Engineer B, Returns)**:
- [ ] Review Week 3 code (A2A work)
- [ ] Read MVP-DESIGN section 2.3 (Discovery API)
- [ ] Read IMPLEMENTATION-CHECKLIST Week 4
- [ ] Understand REST endpoint structure
- [ ] Know latency targets (<100ms P50)
- [ ] Understand end-to-end test strategy

---

## One-Pager (Elevator Pitch)

**Problem**: YAWL skills are hard to expose to external systems (Z.AI agents, peer organizations, LLMs).

**Solution**: Build an Integrations Marketplace with:
- **Connector Registry**: YAML files describe how to invoke a skill
- **3 Adapters**: Z.AI (MCP tools), A2A (handoff protocol), MCP (LLM native)
- **Discovery API**: External systems find + invoke skills in <100ms

**Timeline**: 4 weeks, 2 engineers, $50K

**Result**: One skill → 3 invocation pathways (Z.AI agent, peer A2A agent, Claude)

**Success**: All 5 reference connectors work end-to-end by Mar 21

**Next Phase**: OAuth, webhooks, real-time reputation (Phase 2)

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-21 | YAWL Integration Team | Initial design |
| (Draft) | 2026-02-21 | Design review | Ready for implementation kickoff |

---

**Last Updated**: 2026-02-21 21:21 UTC
**Status**: READY FOR IMPLEMENTATION
**Next Milestone**: Week 1 starts 2026-02-24
**Demo Date**: 2026-03-21 (end of Week 4)

