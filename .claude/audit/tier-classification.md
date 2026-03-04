# Plugin Tier Classification — Adaptation Priority

**Generated**: 2026-03-04
**Phase**: 3 — Classification & Ranking
**Status**: Complete

---

## Overview

Plugins classified by **adaptation urgency** (Tier 1/2/3) and **autonomous readiness** (score 1-10).

**Total plugins classified**: 73
**Tier 1 (MUST ADAPT)**: 10 plugins
**Tier 2 (SHOULD ADAPT)**: 15 plugins
**Tier 3 (NICE-TO-HAVE)**: 48 plugins

---

## TIER 1: MUST ADAPT (Score 9-10)

These are core to the autonomous framework. **Adaptation is critical.**

### 1.1 Autonomous Frameworks (Already Integrated) — 5 plugins

**Status**: ✅ PRODUCTION READY (no adaptation needed)

| Plugin | Score | Effort | Status | Notes |
|--------|-------|--------|--------|-------|
| analyze-errors.sh | 10 | 0h | ✅ DONE | Error detection pipeline |
| remediate-violations.sh | 10 | 0h | ✅ DONE | Auto-fix framework |
| decision-engine.sh | 10 | 0h | ✅ DONE | Intelligent routing |
| activate-permanent-team.sh | 10 | 0h | ✅ DONE | 15-agent team coordinator |
| hyper-validate.sh | 9 | 0h | ✅ DONE | Guard detection hook |

**Summary**: Core autonomous system already operational ✅

---

### 1.2 Validation Hooks & Phases — 2 plugins

**Status**: ✅ PRODUCTION READY (already integrated)

| Plugin | Score | Effort | Status | Notes |
|--------|-------|--------|--------|-------|
| q-phase-invariants.sh | 9 | 0h | ✅ DONE | Invariant enforcement |
| stop-hook.sh | 9 | 0h | ✅ DONE | Validation orchestration |

---

### 1.3 Validators Needing Wrapping — 3 plugins

**Status**: ⏳ READY FOR ADAPTATION

| Plugin | Score | Effort | Status | Blocker Risk |
|--------|-------|--------|--------|--------------|
| **HyperStandardsValidator** | **10** | **1-2h** | 🟢 NEXT | None |
| YSpecificationValidator | 8 | 2-3h | ⚠️ AFTER | Low |
| YCoreDataValidator | 8 | 1-2h | ⏳ QUEUE | Low |

**HyperStandardsValidator is PoC candidate** (Phase 4)

---

### 1.4 Plugin Infrastructure — 2 plugins

**Status**: Foundation layer (support only)

| Plugin | Score | Effort | Status | Notes |
|--------|-------|--------|--------|-------|
| YPluginLoader | 9 | 0h | ✅ DONE | Auto-loaded by system |
| PluginLoaderUtil | 9 | 0h | ✅ DONE | Auto-loaded by system |

---

### 1.5 Engine Interface Servers — 2 plugins

**Status**: ⏳ READY FOR ADAPTATION (protocol level)

| Plugin | Score | Effort | Status | Blocker Risk |
|--------|-------|--------|--------|--------------|
| YawlA2AServer | 9 | 3-4h | 🏗️ PHASE2 | Medium |
| VirtualThreadYawlA2AServer | 8 | 2-3h | 🏗️ PHASE2 | Medium |

---

### Summary: TIER 1

**Total**: 14 plugins
**Already autonomous**: 7 ✅
**Ready to adapt**: 3 ⏳
**Phase 2 candidates**: 4 🏗️

**Recommendation**: Proceed with HyperStandardsValidator PoC (Phase 4)

---

## TIER 2: SHOULD ADAPT (Score 6-8)

These enhance the autonomous framework. **Adaptation provides significant value.**

### 2.1 Validation Plugins — 7 plugins

| Plugin | Score | Effort | Status | Use Case |
|--------|-------|--------|--------|----------|
| JsonSchemaValidator (A2A) | 8 | 2h | ⏳ QUEUE | Message validation |
| SchemaValidator | 8 | 2h | ⏳ QUEUE | Protocol validation |
| ApiKeyValidator | 7 | 1.5h | ⏳ QUEUE | Auth validation |
| JwtValidator | 7 | 1.5h | ⏳ QUEUE | Token validation |
| SpiffeValidator | 7 | 2h | ⏳ QUEUE | Identity validation |
| DataContractValidator | 7 | 2.5h | ⏳ QUEUE | Contract compliance |
| OAuth2TokenValidator | 6 | 2h | ⏳ QUEUE | OAuth validation |

---

### 2.2 MCP Tool Registry & Infrastructure — 5 plugins

**Note**: 50+ individual tools exist; recommend selective wrapping

| Plugin | Score | Effort | Status | Notes |
|--------|-------|--------|--------|-------|
| YawlMcpConfiguration | 6 | 4-5h | 🔧 PHASE3 | Complex surface area |
| SafeMcpToolRegistry | 7 | 3h | 🔧 PHASE3 | Safe tool execution |
| YawlMcpResourceRegistry (Spring) | 7 | 2.5h | 🔧 PHASE3 | Resource management |
| McpToolRegistry | 7 | 2h | 🔧 PHASE3 | Tool lifecycle |
| OntologyDrivenToolFactory | 6 | 3h | 🔧 PHASE3 | Advanced tool creation |

---

### 2.3 A2A Adapters & Skills — 3 plugins

| Plugin | Score | Effort | Status | Use Case |
|--------|-------|--------|--------|----------|
| YawlEngineAdapter | 8 | 2.5h | ⏳ QUEUE | Primary A2A interface |
| ResilientYawlEngineAdapter | 7 | 3h | 🔧 PHASE3 | Fault tolerance |
| SelfUpgradeSkill | 6 | 2h | 🔧 PHASE3 | Self-update capability |

---

### Summary: TIER 2

**Total**: 15 plugins
**Effort if all adapted**: 35-40 hours
**Priority ranking**: Validators > Registry > Adapters
**Phase 3 candidates**: 8 plugins

**Recommendation**: Defer until Phase 4+

---

## TIER 3: NICE-TO-HAVE (Score 3-5)

These provide optional enhancements. **Adaptation is lower priority.**

### 3.1 MCP Tool Specifications — 25+ plugins

These are individual tool implementations for workflow analysis:

**Tools** (examples):
- BlueOceanMcpTools — SAFe program coordination
- CaseDivergenceTools — Case analysis
- ComplexityBoundTools — Metrics
- DataLineageTools — Data flow
- DeadPathAnalyzerTools — Path analysis
- JiraAlignPortfolioTools — Jira integration
- LivenessOracleTools — Verification
- SoundnessProverTools — Soundness proofs
- TemporalAnomalySpecification — Timing analysis
- WorkflowDiffSpecification — Workflow comparison
- ... (25 more tools)

**Status**: 🔧 PHASE 3+
**Effort per tool**: 1-3 hours (average 2h)
**Total if all**: 50-75 hours

**Recommendation**: Wrap tools incrementally using discovered pattern

---

### 3.2 Legacy & Specialized Plugins — 10 plugins

| Plugin | Score | Effort | Status | Notes |
|--------|-------|--------|--------|-------|
| YawlGradlePlugin | 4 | 1h | 🔧 BUILD | Build integration |
| YLogServer | 5 | 1.5h | 🔧 MONITORING | Logging aggregation |
| YawlLanguageServer | 4 | 2h | 🔧 IDE | IDE integration |
| ExecuteClaudeTool | 5 | 2h | 🔧 INTEGRATION | Claude API calls |
| SAFeCeremonyOrchestrator | 4 | 2.5h | 🔧 WORKFLOW | Ceremony automation |
| EngineDedupPlan | 3 | 1.5h | 🔧 OPTIMIZATION | Deduplication |
| AIMQMilestoneAdapter | 4 | 2h | 🔧 TRACKING | Milestone mgmt |
| QLeverEmbeddedEngineAdapter | 3 | 3h | 🔧 SPECIALIZED | Knowledge engine |
| CaseTimelineSpecification | 5 | 1.5h | 🔧 ANALYSIS | Timeline analysis |
| McpFailureModeType | 3 | 1h | 🔧 FMEA | Failure mode analysis |

---

### 3.3 Engine Interface Servers (Legacy) — 8 plugins

These are traditional engine interfaces (pre-A2A):

| Plugin | Score | Effort | Status | Notes |
|--------|-------|--------|--------|-------|
| InterfaceA_EngineBasedServer | 4 | 2h | 🔧 LEGACY | Participant interface |
| InterfaceB_EngineBasedServer | 4 | 2h | 🔧 LEGACY | Admin interface |
| InterfaceB_EnvironmentBasedServer | 3 | 2.5h | 🔧 LEGACY | Distributed variant |
| InterfaceX_EngineSideServer | 4 | 2.5h | 🔧 LEGACY | Extended interface |
| InterfaceX_ServiceSideServer | 4 | 2.5h | 🔧 LEGACY | Service variant |
| Agent (core) | 5 | 1.5h | 🔧 AGENT | Base agent impl |
| YAgentPerformanceMetrics | 4 | 1h | 🔧 MONITORING | Performance tracking |
| AgentInfoStore | 5 | 1.5h | 🔧 AGENT | Agent registry |

---

### 3.4 Autonomous Agents (SAFe) — 6 plugins

These are organizational-level agents:

| Plugin | Score | Effort | Status | Use Case |
|--------|-------|--------|--------|----------|
| ARTOrchestrationAgent | 5 | 3h | 🔧 ORG | SAFe ART |
| ComplianceGovernanceAgent | 5 | 3h | 🔧 ORG | Governance |
| GenAIOptimizationAgent | 4 | 4h | 🔧 ORG | AI optimization |
| PortfolioGovernanceAgent | 5 | 3h | 🔧 ORG | Portfolio mgmt |
| ValueStreamCoordinationAgent | 5 | 3h | 🔧 ORG | Value streams |
| ZAIOrchestrator | 4 | 4h | 🔧 ORG | Z.AI coordination |

---

### Summary: TIER 3

**Total**: 48 plugins
**Total effort if all**: 150+ hours
**Recommendation**: Selective wrapping based on business priority
**Phase**: 4+ (long-term roadmap)

---

## Adaptation Roadmap

### Phase 4 (This Session): PoC Adapter
- ✅ HyperStandardsValidator (1-2 hours)
- Document pattern for replication
- Test end-to-end with decision-engine
- Commit proof-of-concept

### Phase 5 (Next Session): Tier 1 Completion
- Adapt remaining Tier 1 validators (3 plugins)
- Adapt A2A servers (2 plugins)
- Total effort: 8-10 hours

### Phase 6 (Session +2): Tier 2 Integration
- Adapt validation plugins (7 plugins)
- Selective MCP tool wrapping (start with 3-5)
- Total effort: 15-20 hours

### Phase 7+ (Roadmap): Tier 3 Expansion
- Wrap remaining MCP tools (50+)
- Legacy interface migration
- SAFe agent integration
- Estimated: 150+ hours total

---

## Success Metrics

### Immediate (Phase 4)
- ✅ 1 PoC adapter working end-to-end
- ✅ Documented adaptation pattern
- ✅ Test suite passing
- ✅ Integrated with decision-engine.sh

### Short-term (Phase 5)
- ✅ All Tier 1 plugins operational
- ✅ Core autonomous system fully integrated
- ✅ Plugin ecosystem functional

### Medium-term (Phase 6)
- ✅ 60% of Tier 2 plugins adapted
- ✅ MCP tool wrapping pattern established
- ✅ 40+ hours of dev completed

### Long-term (Phase 7+)
- ✅ 100% plugin ecosystem autonomous
- ✅ 200+ hours of adaptation investment
- ✅ Fully autonomous YAWL system

---

## Risk Assessment

### HIGH RISK (address first)
- **MCP tool surface area**: 50+ tools, varied contracts
- **Legacy interface migration**: Complex protocols
- **Autonomous agent coordination**: Team state complexity

### MEDIUM RISK
- **Protocol compatibility**: Ensure error format consistency
- **Test coverage**: Need comprehensive test suite
- **Documentation**: Keep guides updated during adaptation

### LOW RISK
- **Core framework**: Already proven (analyze-errors.sh, etc.)
- **Hook integration**: Already working (hyper-validate.sh)
- **Tier 1 validators**: Clear contracts, low complexity

---

## Recommendations

### Do First
1. ✅ **Phase 4**: HyperStandardsValidator PoC (2h)
2. ✅ **Phase 5**: YSpecificationValidator (3h)
3. ✅ **Phase 5**: YCoreDataValidator (2h)

### Do Second
4. **Phase 5**: YawlA2AServer wrapping (4h)
5. **Phase 6**: A2A protocol validators (5-6h)
6. **Phase 6**: MCP tool registry (4-5h)

### Do Later
7. **Phase 6+**: Selective MCP tool wrapping (2h per tool)
8. **Phase 7+**: SAFe agents (3h per agent)
9. **Phase 7+**: Legacy interface migration (2.5h per interface)

### Not Recommended (Low ROI)
- Language server adaptation (IDE integration, not core)
- Gradle plugin wrapping (build-time only)
- Legacy interface preservation (A2A is superior)

---

## Plugin Categories Summary

| Category | Total | Tier 1 | Tier 2 | Tier 3 | ROI |
|----------|-------|--------|--------|--------|-----|
| Core Autonomous | 7 | 7 | 0 | 0 | ✅ DONE |
| Validators | 15 | 3 | 7 | 5 | ✅ HIGH |
| MCP/Tools | 50+ | 0 | 5 | 45+ | ⚠️ MEDIUM |
| A2A Adapters | 5 | 2 | 2 | 1 | ✅ HIGH |
| Engine Interfaces | 13 | 2 | 2 | 9 | 🔧 LOW |
| **TOTAL** | **73** | **14** | **15** | **48** | |

---

**Classification Phase**: ✅ COMPLETE
**Ready for Phase 4**: ✅ YES
**PoC Candidate**: HyperStandardsValidator
**Estimated effort (all plugins)**: 250+ hours
**Recommended pace**: 15-20 hours per phase
