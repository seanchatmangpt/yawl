# YAWL Agents Marketplace — MVP Complete Design

**Status**: Ready for development (all design documents completed)
**Duration**: 4 weeks
**Team**: 2 engineers (1 backend, 1 QA/infrastructure)
**Budget**: $20,000
**Success Metric**: 5 agents, 100% skill invocation success, <100ms discovery

---

## Quick Overview

The **Agents Marketplace** is a registry of pre-trained autonomous agents that YAWL workflows can invoke to complete business tasks.

**Example**:
```
YAWL Workflow: Approve Expense
    ↓
    Invokes: approval-agent (via A2A protocol)
    ↓
    Decision: { approved: true, confidence: 0.99, reason: "Under limit" }
    ↓
    Workflow continues
```

**Key Features**:
- **Agent Registry**: Git-backed YAML profiles of agents
- **Capability Discovery**: SPARQL index to find agents by skill (<100ms)
- **Orchestration Templates**: DAG-based agent coordination (sequential, parallel, conditional)
- **REST API**: Full REST interface for discovery, deployment, monitoring
- **Lifecycle Management**: Agent deployment, health checks, metrics

---

## What's in This Design?

This MVP includes **5 design documents** and **3 YAML/JSON samples**:

### Design Documents

1. **`AGENTS-MARKETPLACE-MVP-DESIGN.md`** (Main spec)
   - 4-week roadmap (Week 1-4 breakdown)
   - Architecture overview (components, entities)
   - Core classes (pseudocode)
   - Integration points (A2A, Skills Marketplace, case monitoring)
   - Risk analysis + contingency plans
   - **12,000 words** — comprehensive technical spec

2. **`AGENTS-MARKETPLACE-QUICK-START.md`** (User guide)
   - 5-minute overview of marketplace concepts
   - Task 1: Register a new agent (15 min)
   - Task 2: Discover agents (5 min)
   - Task 3: Create orchestration template (20 min)
   - Task 4: Invoke agent from workflow (10 min)
   - Task 5: Monitor metrics (10 min)
   - Common patterns (auto-approve, chains, parallel)
   - Troubleshooting guide
   - **2,500 words** — developer quick-start

3. **`AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md`** (Detailed checklist)
   - Week-by-week breakdown (W1-W4)
   - Per-week deliverables, tests, acceptance criteria
   - 200+ line-item checklist
   - Risk mitigation per week
   - Sign-off criteria + post-MVP cleanup
   - **3,000 words** — operational handbook

4. **`AGENTS-MARKETPLACE-API-SPEC.md`** (REST API)
   - 8 core endpoints (discovery, profile, health, orchestration)
   - Request/response examples (JSON)
   - Error codes + handling
   - Rate limiting
   - Authentication (phase 2)
   - Example workflows (discovery → deployment)
   - OpenAPI/Swagger generation notes
   - **2,500 words** — API reference

5. **`AGENTS-MARKETPLACE-README.md`** (This file)
   - Quick overview
   - Navigation guide
   - Success criteria
   - Next steps

### Sample Artifacts

1. **`.agents/agents.yaml`** (Registry manifest)
   - Index of all 6 agents + 3 templates
   - Version tracking + status

2. **`.agents/agent-profiles/approval-agent.yaml`** (Sample agent profile)
   - Full YAML example: capabilities, deployment, health checks, metrics
   - Real-world detailed specification

3. **`.agents/orchestration/approval-workflow.json`** (Sample template)
   - 3-agent sequential workflow: validate → approve → notify
   - Full DAG with error handling, data bindings, monitoring

---

## Architecture at a Glance

```
AGENTS MARKETPLACE (NEW)
├─ Agent Registry (Git-backed YAML)
│  ├─ .agents/agents.yaml (manifest)
│  ├─ .agents/agent-profiles/ (6 agent definitions)
│  └─ .agents/orchestration/ (3 workflow templates)
│
├─ Capability Index (SPARQL graph + inverted index)
│  └─ Maps: skill → agents (discovery <100ms)
│
├─ REST API Layer
│  ├─ POST /agents/discover → find agents
│  ├─ GET /agents/{id}/profile → full metadata
│  ├─ POST /orchestrate/deploy → compile + run
│  └─ GET /agents/{id}/health → status check
│
└─ Deployment + Lifecycle
   ├─ AgentDeployer (Docker wrapper)
   ├─ HealthChecker (HTTP health checks)
   └─ AgentLifecycleHooks (events + metrics)

Integrates with:
├─ YAWL A2A Protocol (agent invocation)
├─ YAWL Workflow Engine (case execution)
├─ Skills Marketplace (skill references)
└─ Case Monitoring (execution tracking)
```

---

## Key Decisions

### Why YAML for Agent Profiles?

- **Human-readable**: Non-engineers can understand
- **Git-friendly**: Version control + audit trail
- **Kubernetes-aligned**: DevOps teams familiar with format
- **No database required**: Keep infrastructure simple

### Why Git-Backed Registry?

- **Immutable**: Commit history is audit trail
- **Offline**: Clone locally, works without network
- **Simple**: No central database to manage
- **Standard**: GitOps patterns familiar to teams

### Why SPARQL for Capability Index?

- **Semantic**: Handles skill hierarchies (narrower/broader)
- **Flexible**: Can add reasoning later (Phase 2)
- **Standard**: SPARQL is mature + well-documented
- **Optimized**: Cache inverted index for <100ms queries

### Why DAG Templates (not FSM)?

- **Natural**: Maps to agent chains
- **Simple**: No state explosion
- **Standard**: ML pipelines + Kubernetes use DAGs
- **Proven**: Apache Airflow, Argo Workflows use DAG model

---

## 4-Week Roadmap Summary

| Week | Focus | Deliverables | Tests |
|------|-------|--------------|-------|
| **1** | Profile schema | 2 agents, YAML serialization | 25 tests, 80% coverage |
| **2** | Registry + discovery | 5 agents, <100ms discovery API | 25 tests, SPARQL validation |
| **3** | Orchestration | 3 templates, DAG compiler | 25 tests, end-to-end |
| **4** | Integration + deployment | Docker images, 500+ tests, live agents | 50+ tests, stress tests |
| **Total** | **Full MVP** | **5 agents, 3 templates, REST API** | **125+ tests** |

---

## Success Criteria (MVP Sign-Off)

By end of Week 4:

✓ **5 agents registered** (approval, validation, PO, expense, scheduler)
✓ **Discovery <100ms p99** (measured over 100 concurrent queries)
✓ **3 orchestration templates** (sequential, parallel, conditional)
✓ **100% test coverage** (>80% code coverage, 125+ tests)
✓ **Full REST API** (8 endpoints, OpenAPI spec)
✓ **Docker deployment** (docker-compose with all agents)
✓ **Zero production issues** (no TODO/FIXME, real implementations only)
✓ **Complete documentation** (API spec, quick-start, design doc)

---

## How to Use This Design

### For Engineers (Building)

1. **Start with**: `AGENTS-MARKETPLACE-QUICK-START.md`
   - Understand agent profiles, templates, discovery API

2. **Then read**: `AGENTS-MARKETPLACE-MVP-DESIGN.md` Section 3 (Code Structure)
   - Package layout, core classes, pseudocode

3. **Week-by-week**: Use `AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md`
   - Detailed tasks + acceptance criteria per week
   - Run through checklist as you code

4. **When building REST API**: Reference `AGENTS-MARKETPLACE-API-SPEC.md`
   - Request/response examples
   - Error handling patterns

### For Architects (Reviewing)

1. **Overview**: This README + MVP Design main sections
2. **Integration**: MVP Design Section 4 (Integration Points)
3. **Decisions**: MVP Design Section 11 (Decision Records)
4. **Risks**: MVP Design Section 7 (Risks & Mitigation)

### For QA (Testing)

1. **Per-week tests**: Checklist Section Week 1-4
2. **Integration test cases**: Checklist Section Week 4
3. **API examples**: API Spec Section (Example Workflows)
4. **Performance targets**: Checklist + API Spec (< 100ms discovery)

### For Stakeholders (Approving)

1. **Executive summary**: This README
2. **Cost breakdown**: MVP Design Section 9
3. **Success criteria**: This README (bold items)
4. **Timeline**: 4-week roadmap above

---

## Key Artifacts to Create

### Week 1
- [ ] `src/.../marketplace/agent/AgentProfile.java`
- [ ] `src/.../marketplace/agent/AgentCapability.java`
- [ ] `.agents/agent-profiles/approval-agent.yaml`
- [ ] `.agents/agent-profiles/validation-agent.yaml`

### Week 2
- [ ] `src/.../marketplace/registry/GitAgentRegistry.java`
- [ ] `src/.../marketplace/discovery/CapabilityIndex.java`
- [ ] `src/.../marketplace/api/DiscoveryController.java`
- [ ] `.agents/agents.yaml` (manifest)
- [ ] 3 more agents (po, expense, scheduler)

### Week 3
- [ ] `src/.../marketplace/orchestration/OrchestrationTemplate.java`
- [ ] `src/.../marketplace/orchestration/TemplateCompiler.java`
- [ ] `src/.../marketplace/api/OrchestratorController.java`
- [ ] `.agents/orchestration/approval-workflow.json`
- [ ] `.agents/orchestration/parallel-workflow.json`
- [ ] `.agents/orchestration/conditional-workflow.json`

### Week 4
- [ ] `src/.../marketplace/deployment/AgentDeployer.java`
- [ ] `Dockerfile` (approval-agent reference)
- [ ] `docker-compose.yml` (5-agent setup)
- [ ] Integration test suite (500+ lines)
- [ ] OpenAPI spec (auto-generated)

---

## Integration with YAWL

**Existing YAWL Components Used**:
- A2A Protocol: `org.yawlfoundation.yawl.integration.a2a.*`
- YEngine: `org.yawlfoundation.yawl.engine.*`
- Case Monitoring: `org.yawlfoundation.yawl.stateless.*`

**How Agents Fit**:
1. Workflow task invokes agent via A2AClient
2. Agent decision returned as task data
3. Workflow continues based on decision
4. Agent metrics feed back to case monitoring

**Example Integration** (Week 2 A2A-INTEGRATION.md):
```java
A2AClient agent = new A2AClient("approval-agent");
WorkflowResponse res = agent.invokeWithTimeout(request, Duration.ofSeconds(30));
// Response: { approved: true, confidence: 0.99, reason: "Under limit" }
```

---

## Phase 2 Roadmap (Out of Scope)

**Not in MVP, planned for Phase 2**:
- Fine-tuning agents on custom datasets
- Advanced observability (distributed tracing)
- Cost optimization models
- Multi-tenant isolation
- Agent federation (agent-to-agent comms)
- Predictive scaling

---

## References & Files

**Main Documents** (read in this order):
1. ✓ This README (overview, quick navigation)
2. ✓ AGENTS-MARKETPLACE-QUICK-START.md (5 min read, dev guide)
3. ✓ AGENTS-MARKETPLACE-MVP-DESIGN.md (main spec, architecture)
4. ✓ AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md (week-by-week tasks)
5. ✓ AGENTS-MARKETPLACE-API-SPEC.md (REST API reference)

**Sample Artifacts**:
- ✓ `.agents/agents.yaml` (registry manifest, 6 agents)
- ✓ `.agents/agent-profiles/approval-agent.yaml` (full agent example)
- ✓ `.agents/orchestration/approval-workflow.json` (template example)

**Companion Docs** (in same directory):
- AGENT-INTEGRATION.md (existing, A2A patterns)
- AUTONOMICS-GUIDE.md (existing, self-healing workflows)
- Integration rules: `.claude/rules/integration/mcp-a2a-conventions.md`

---

## Getting Started (Next Steps)

### For Lead Engineer

1. **Read**: QUICK-START.md + MVP-DESIGN.md (3 hours)
2. **Create feature branch**: `git checkout -b marketplace-mvp`
3. **Week 1**: Follow IMPLEMENTATION-CHECKLIST.md Week 1 section
4. **Daily standup**: Reference checklist for progress
5. **Weekly sync**: Review success criteria + risks

### For QA Lead

1. **Read**: IMPLEMENTATION-CHECKLIST.md Week 4 (tests section)
2. **Setup testcontainers**: Docker-based test infrastructure
3. **Create test plan**: Per-week test cases (from checklist)
4. **Week 1**: Begin building test fixtures (mock agents)

### For Architect/Reviewer

1. **Read**: MVP-DESIGN.md Sections 1-2, 11 (architecture + decisions)
2. **Review**: Week 1 code (profile schema) → approve approach
3. **Monthly sync**: Review progress + pivot if needed

---

## Common Questions

### Q: Why not use existing agent frameworks (e.g., AutoGPT, LangChain)?

**A**: YAWL already has A2A protocol + case monitoring. Agents Marketplace extends those without adding external dependencies. Phase 2 can integrate LangChain if needed.

### Q: Can agents communicate directly (agent-to-agent)?

**A**: MVP supports agent chains (sequential) only. Phase 2 adds direct agent-to-agent communication.

### Q: How are agents versioned?

**A**: Each agent profile has `version` field. Git commits track changes. Multiple versions can coexist in registry.

### Q: What if an agent times out?

**A**: Circuit breaker opens (default 3 consecutive failures). Workflow escalates to dead letter queue or manual approval.

### Q: How is agent cost tracked?

**A**: Metrics include `costPerInvocation`. Agent profile declares cost; billing integration TBD Phase 2.

### Q: Can I run agents on-premise?

**A**: Yes. Deployment type can be `docker`, `process`, or `remote`. Docker recommended for MVP.

---

## Success Metrics (Observable)

**By end of Week 4**, measure:

| Metric | Target | Tool |
|--------|--------|------|
| Discovery latency p99 | <100ms | Apache JMeter (100 concurrent) |
| Test coverage | >80% | JaCoCo coverage report |
| Build time | <2 min | Maven timing |
| Agent success rate | 100% | Integration test pass rate |
| Deployment time | <30s | Docker build + health check |
| Documentation | Complete | grep "TODO\|FIXME" = 0 |

---

## Support & Questions

**For design questions**: Contact Architecture Team (design@acme.com)
**For implementation help**: Reference QUICK-START.md or IMPLEMENTATION-CHECKLIST.md
**For API questions**: See API-SPEC.md examples + Swagger UI (http://localhost:8080/swagger-ui.html)

---

## Conclusion

This MVP delivers a **production-ready Agents Marketplace** in 4 weeks with:

✓ **5 reference agents** (approval, validation, PO, expense, scheduler)
✓ **Capability discovery** (<100ms SPARQL index)
✓ **Orchestration templates** (3 patterns: sequential, parallel, conditional)
✓ **Full REST API** (8 endpoints, OpenAPI spec)
✓ **Docker deployment** (compose file + images)
✓ **Complete test suite** (125+ tests, >80% coverage)
✓ **Zero production debt** (no TODO/FIXME, all real implementations)

**Ready to start? Open AGENTS-MARKETPLACE-IMPLEMENTATION-CHECKLIST.md Week 1 and begin!**

---

**Document created**: 2026-02-21
**Status**: Ready for development
**Next milestone**: Week 1 agent profiles complete
