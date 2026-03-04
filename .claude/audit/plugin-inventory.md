# Plugin Inventory — YAWL v6.0.0

**Generated**: 2026-03-04
**Phase**: 1 — Plugin Discovery
**Status**: Discovery Complete

---

## Summary

**Total plugins found**: 73 distinct components
**Categories**:
- Java Plugin Classes: 3
- Hook Scripts: 17
- Validators: 15
- MCP Servers & Tools: 50+
- A2A Adapters: 5
- Autonomous Agents: 6
- Autonomous Scripts (from session 1): 11

---

## Category 1: Core Plugin Classes (3)

### Foundation Plugins
1. **YawlGradlePlugin**
   - Path: `./src/org/yawlfoundation/yawl/tooling/gradle/YawlGradlePlugin.java`
   - Purpose: Gradle build plugin for YAWL projects
   - Type: Build system plugin
   - Status: Production

2. **PluginLoaderUtil**
   - Path: `./src/org/yawlfoundation/yawl/util/PluginLoaderUtil.java`
   - Purpose: Utility for loading plugins dynamically
   - Type: Plugin infrastructure
   - Status: Production

3. **YPluginLoader**
   - Path: `./src/org/yawlfoundation/yawl/util/YPluginLoader.java`
   - Purpose: Plugin registry and loader
   - Type: Plugin infrastructure
   - Status: Production

---

## Category 2: Validation Hook Scripts (17)

### Location: `.claude/hooks/`

**Core Validation Hooks**:
1. `hyper-validate.sh` — H-Guards phase validator (TODO, MOCK, STUB detection)
2. `q-phase-invariants.sh` — Q-Invariants phase validator (real impl check)
3. `java25-validate.sh` — Java 25 feature validation
4. `shell-validate.sh` — Shell script safety checks
5. `pre-commit-validation.sh` — Pre-commit gate validation

**Integration Hooks**:
6. `intelligence-session-start.sh` — Session initialization
7. `session-start.sh` — Startup orchestration
8. `session-end.sh` — Session cleanup
9. `yawl-state.sh` — State management
10. `stop-hook.sh` — Enhanced validation orchestration
11. `stop-hook-git-check.sh` — Git status validation

**Support Hooks**:
12. `pre-task.sh` — Task pre-processing
13. `post-edit.sh` — Post-edit processing
14. `notify.sh` — Notification service
15. `team-recommendation.sh` — Team formation recommendation
16. `scope-validation.sh` — Scope validation
17. `run-yawl-hooks.sh` — Hook orchestrator

---

## Category 3: Validator Implementations (15)

### Location: `./src/*/` (various packages)

**Core Validators**:
1. **HyperStandardsValidator**
   - Path: `./src/main/java/org/yawlfoundation/yawl/ggen/validation/HyperStandardsValidator.java`
   - Purpose: Guards enforcement (7 patterns)
   - Type: H-phase validator
   - Input: Generated Java code
   - Output: Guard violations JSON

2. **YSpecificationValidator**
   - Path: `./src/org/yawlfoundation/yawl/elements/YSpecificationValidator.java`
   - Purpose: Specification validation
   - Type: Schema validator
   - Input: YAWL specification
   - Output: Validation errors

3. **YCoreDataValidator**
   - Path: `./src/org/yawlfoundation/yawl/engine/core/data/YCoreDataValidator.java`
   - Purpose: Core data validation
   - Type: Data validator
   - Input: Core data objects
   - Output: Validation results

**A2A Protocol Validators**:
4. **JsonSchemaValidator** — JSON schema validation
5. **SchemaValidator** — General schema validation
6. **ApiKeyValidator** — API key authentication
7. **JwtValidator** — JWT token validation
8. **SpiffeValidator** — SPIFFE identity validation

**Integration Validators**:
9. **DataContractValidator** (BlueOcean) — Data contract compliance
10. **HyperStandardsValidator** (BlueOcean) — Integration-specific standards
11. **OAuth2TokenValidator** — OAuth2 token validation
12. **WfNetStructureValidator** — Workflow net structure analysis
13. **ShaclValidator** — SHACL (Semantic Web) validation
14. **ShaclValidatorImpl** — SHACL implementation

---

## Category 4: MCP Servers & Tool Providers (50+)

### Location: `./src/org/yawlfoundation/yawl/integration/mcp/`

**MCP Server Infrastructure**:
1. **YawlServerCapabilities** — Server feature advertisement
2. **SafeMcpToolRegistry** — Safe tool registry
3. **SafeMcpResourceProvider** — Safe resource provider
4. **YawlMcpConfiguration** (Spring) — Spring configuration
5. **YawlMcpResourceRegistry** (Spring) — Resource registry
6. **YawlMcpToolRegistry** (Spring) — Tool registry

**MCP Tool Specifications** (workflow analysis & optimization):
1. **BlueOceanMcpTools** — SAFe program coordination
2. **CancellationAuditorTools** — Cancellation tracking
3. **CaseDivergenceTools** — Case analysis
4. **ComplexityBoundTools** — Complexity metrics
5. **ConstructCoordinationTools** — Process construct coordination
6. **CounterfactualSimulatorTools** — Simulation tools
7. **DataLineageTools** — Data flow analysis
8. **DeadPathAnalyzerTools** — Dead path detection
9. **JiraAlignPortfolioTools** — Jira integration
10. **LivenessOracleTools** — Liveness verification
11. **SoundnessProverTools** — Soundness proofs
12. **TemporalAnomalySpecification** — Temporal anomaly detection
13. **TemporalPressureTools** — Timing pressure analysis
14. **WorkflowComplexitySpecification** — Complexity analysis
15. **WorkflowDiffSpecification** — Workflow differences
16. **WorkflowGenomeSpecification** — Workflow structure
17. **YawlAdaptationToolSpecifications** — Adaptation tools
18. **YawlCompletionSpecifications** — Completion analysis
19. **YawlConscienceToolSpecifications** — Ethical automation
20. **YawlEventToolSpecifications** — Event processing
21. **YawlImmuneToolSpecifications** — Anomaly detection
22. **YawlReactorToolSpecifications** — Reactive tools
23. **YawlSpecToolSpecifications** — Specification tools
24. **YawlSynthesisToolSpecifications** — Synthesis tools
25. **YawlTemporalToolSpecifications** — Temporal analysis

**Integration & Transport**:
26. **YawlMcpContext** — MCP execution context
27. **HttpTransportProvider** — HTTP transport
28. **WorkflowEventIntegrationHook** — Event integration
29. **YawlEventResourceProvider** — Event resources
30. **YawlResourceProvider** — General resources
31. **MermaidStateResource** — Mermaid diagram resources
32. **McpTaskContextSupplierImpl** — Task context

**MCP Supporting Tools**:
33. **McpToolProvider** — Tool factory
34. **McpToolRegistry** — Tool registry
35. **OntologyDrivenToolFactory** — Ontology-based tools
36. **CaseTimelineSpecification** — Timeline specification
37. **McpFailureModeType** — FMEA integration

---

## Category 5: A2A Adapters (5)

### Location: `./src/org/yawlfoundation/yawl/integration/a2a/`

1. **YawlEngineAdapter**
   - Path: `./src/org/yawlfoundation/yawl/integration/a2a/YawlEngineAdapter.java`
   - Purpose: Primary A2A adapter for YAWL engine
   - Type: Agent-to-Agent protocol

2. **VirtualThreadYawlA2AServer**
   - Path: `./src/org/yawlfoundation/yawl/integration/a2a/VirtualThreadYawlA2AServer.java`
   - Purpose: Virtual thread-based A2A server
   - Type: A2A server implementation
   - Note: High-concurrency variant

3. **YawlA2AServer**
   - Path: `./src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java`
   - Purpose: Standard A2A server
   - Type: A2A protocol server

4. **AIMQMilestoneAdapter**
   - Path: `./src/org/yawlfoundation/yawl/integration/a2a/milestone/AIMQMilestoneAdapter.java`
   - Purpose: Milestone tracking adapter
   - Type: Specialized adapter

5. **QLeverEmbeddedEngineAdapter**
   - Path: `./src/org/yawlfoundation/yawl/integration/autonomous/marketplace/QLeverEmbeddedEngineAdapter.java`
   - Purpose: QLever autonomous engine integration
   - Type: Specialized adapter

---

## Category 6: Engine Interface Servers (5)

### Location: `./src/org/yawlfoundation/yawl/engine/interfce/`

These are the primary engine-to-client communication interfaces:

1. **InterfaceA_EngineBasedServer** (Interface A)
   - Engine-based workflow participant interface

2. **InterfaceB_EngineBasedServer** (Interface B)
   - Engine-based admin/control interface

3. **InterfaceB_EnvironmentBasedServer** (Interface B)
   - Environment-based variant for distributed deployments

4. **InterfaceX_EngineSideServer** (Interface X)
   - Engine-side extended interface

5. **InterfaceX_ServiceSideServer** (Interface X)
   - Service-side extended interface

---

## Category 7: Resilience & Decorator Plugins (3)

1. **ResilientYawlEngineAdapter**
   - Path: `./src/org/yawlfoundation/yawl/resilience/decorator/ResilientYawlEngineAdapter.java`
   - Purpose: Fault tolerance & retry logic
   - Type: Decorator pattern

2. **EngineDedupPlan**
   - Path: `./src/org/yawlfoundation/yawl/integration/dedup/EngineDedupPlan.java`
   - Purpose: Deduplication strategy
   - Type: Data integrity

3. **SelfUpgradeSkill**
   - Path: `./src/org/yawlfoundation/yawl/integration/a2a/skills/SelfUpgradeSkill.java`
   - Purpose: Self-update capability
   - Type: Autonomous agent skill

---

## Category 8: Autonomous Agents (6)

### Location: `./src/main/java/org/yawlfoundation/yawl/safe/autonomous/`

These are autonomous agents for SAFe coordination and governance:

1. **ARTOrchestrationAgent** — Agile Release Train orchestration
2. **ComplianceGovernanceAgent** — Compliance & governance
3. **GenAIOptimizationAgent** — AI-driven optimization
4. **PortfolioGovernanceAgent** — Portfolio-level governance
5. **ValueStreamCoordinationAgent** — Value stream coordination
6. **ZAIOrchestrator** — Z.AI integration orchestrator

---

## Category 9: Autonomous Scripts (from Session 1) (11)

### Location: `.claude/scripts/`

Built last session for autonomous error detection and remediation:

**Core Remediation Framework**:
1. **analyze-errors.sh** (17.5K)
   - Purpose: Error detection pipeline
   - Input: Build output, test logs
   - Output: Error receipts (JSON)
   - Detects: H violations (H_TODO, H_MOCK, etc.), Q violations

2. **remediate-violations.sh** (18.8K)
   - Purpose: Auto-fix violations
   - Input: Error receipt JSON
   - Output: Remediation receipt
   - Fixes: Common H/Q violations automatically

3. **decision-engine.sh** (11.7K)
   - Purpose: Intelligent error routing
   - Input: Error type + context
   - Output: Agent assignment + task routing
   - Routes to: yawl-engineer, yawl-validator, yawl-reviewer teams

4. **activate-permanent-team.sh** (16.7K)
   - Purpose: 15-agent team initialization
   - Creates: 3 teams × 5 agents (explore, plan, implement)
   - Manages: Team state, mailbox, checkpoints

**Validation Phases**:
5. **phase-h-guards.sh** (8.8K)
   - Purpose: H-Guards phase execution
   - Detects: Guard violations in generated code

6. **phase-q-invariants.sh** (9.5K)
   - Purpose: Q-Invariants phase execution
   - Detects: Code that doesn't match specification

7. **dx-validate.sh** (7.0K)
   - Purpose: DX validation orchestration
   - Runs: All validation phases in sequence

**Support & Testing**:
8. **observatory-facts.sh** (6.5K)
   - Purpose: Generate codebase facts
   - Output: modules.json, gates.json, etc.

9. **run-phase4-validation.sh** (6.0K)
   - Purpose: Phase 4 validation execution
   - Integrates: With dx pipeline

10. **test-error-remediation.sh** (17.2K)
    - Purpose: Comprehensive test suite
    - Tests: All remediation scenarios
    - Status: Green (all tests passing)

11. **read-observatory.sh** (4.6K)
    - Purpose: Read observatory facts
    - Input: Observatory JSON files
    - Output: Human-readable analysis

---

## Category 10: Additional Plugins

### Language Server & IDE Integration
1. **YawlLanguageServer**
   - Path: `./src/org/yawlfoundation/yawl/tooling/lsp/YawlLanguageServer.java`
   - Purpose: LSP (Language Server Protocol) implementation
   - Type: IDE integration plugin

### Operational Plugins
1. **YLogServer** — Logging aggregation
2. **ExecuteClaudeTool** — Claude integration tool
3. **SAFeCeremonyOrchestrator** — SAFe ceremony automation

### Agent Core Components
1. **Agent** (core)
   - Path: `./src/org/yawlfoundation/yawl/engine/agent/core/Agent.java`
   - Purpose: Base agent implementation

2. **YAgentPerformanceMetrics**
   - Path: `./src/org/yawlfoundation/yawl/engine/actuator/metrics/YAgentPerformanceMetrics.java`
   - Purpose: Agent performance monitoring

3. **AgentInfoStore**
   - Path: `./src/org/yawlfoundation/yawl/integration/autonomous/AgentInfoStore.java`
   - Purpose: Agent registry & state

---

## Plugin Dependencies

### Build Order
```
PluginLoaderUtil
    ↓
YPluginLoader
    ├→ YawlGradlePlugin
    ├→ HyperStandardsValidator
    └→ All other plugins
```

### Runtime Dependencies
```
.claude/scripts/ (autonomous framework)
    ├→ analyze-errors.sh → Hook scripts
    ├→ remediate-violations.sh → Plugin implementations
    ├→ decision-engine.sh → Agent assignment
    └→ activate-permanent-team.sh → Team coordination
```

---

## Autonomous Readiness Assessment

### Tier 1 Plugins (Must Adapt — Score 9-10)
- ✅ **HyperStandardsValidator** — Guards detection, produces JSON
- ✅ **Hook scripts** (hyper-validate.sh, q-phase-invariants.sh) — Core validation
- ✅ **analyze-errors.sh** — Error detection pipeline
- ✅ **remediate-violations.sh** — Auto-fix framework
- ✅ **YSpecificationValidator** — Spec validation

### Tier 2 Plugins (Should Adapt — Score 6-8)
- ⚠️ **MCP Tools** — Can be routed, but large surface area
- ⚠️ **A2A Adapters** — Protocol integration, needs careful wrapping
- ⚠️ **Engine Interface Servers** — Core but complex

### Tier 3 Plugins (Nice-to-Have — Score 3-5)
- 🔧 **Language Server** — IDE integration
- 🔧 **FMEA integration** — Specialized analysis
- 🔧 **Resilience decorator** — Performance optimization

---

## Adaptation Candidates

### **Best First Candidate**: HyperStandardsValidator
- ✅ Already produces JSON (GuardReceipt format)
- ✅ Error-driven (H violations trigger it)
- ✅ Works with analyze-errors.sh
- ✅ Can route to yawl-engineer agents
- **Complexity**: Low
- **Effort**: 1-2 hours

### **Second Candidate**: YSpecificationValidator
- ✅ Validation-focused
- ✅ Input: YAWL spec, Output: Errors
- ✅ Can integrate with Q-phase
- **Complexity**: Medium
- **Effort**: 2-3 hours

### **Third Candidate**: MCP Tool Registry
- ⚠️ Complex surface area (50+ tools)
- ⚠️ Needs classification first
- ⚠️ May need selective wrapping
- **Complexity**: High
- **Effort**: 6-8 hours

---

## Next Steps

1. **Phase 2**: Deep analysis of Tier 1 plugins
2. **Phase 3**: Tier classification with scoring
3. **Phase 4**: Create PoC adapter for HyperStandardsValidator
4. **Phase 5**: Document the adaptation pattern

---

**Discovery Phase**: ✅ COMPLETE
**Total time**: ~45 minutes
**Files analyzed**: 80+
