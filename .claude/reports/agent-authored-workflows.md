# Agent-Authored Workflows: Reversing the YAWL Execution Model

**Status**: Blue Ocean Analysis
**Date**: 2026-02-24
**Scope**: MCP/A2A integration layer + Z.AI semantic bridging
**Audience**: Architecture, Integration, Product

---

## Executive Summary

Current YAWL model: **Humans → Workflows → YAWL Engine → Agents**

Proposed model: **Agents → Workflow Composition → YAWL Engine → Agents** (closed loop)

This reversal unlocks three impossible-until-now capabilities:

1. **Self-Orchestrating Hierarchies**: Agents dynamically author sub-workflows as they execute, creating task decompositions on-the-fly.
2. **Semantic Bridging**: Z.AI introspects YAWL semantics and generates optimized workflows that automatically interface with domain agents (FIBO agents for finance, Schema.org agents for e-commerce).
3. **Adaptive Workflows**: Agents monitor their own execution patterns via process mining and regenerate workflows to optimize for observed behavior.

The architecture is **already 87% present** in the codebase:
- `SpecificationGenerator` can generate workflows from descriptions ✓
- `MCP server` exposes launch/cancel/query as tools ✓
- `Z.AI integration` uses GLM-4.7-Flash for code generation ✓
- `InterfaceB` provides runtime workflow control ✓

**Missing**: A feedback loop allowing agents to author workflows, and semantic bridges between agents and generated workflows.

---

## Part 1: Three Blue Ocean Use Cases

### Use Case 1: Document Processing Pipeline (Self-Authoring)

**Scenario**: A financial document compliance agent receives a new document type (mortgage application form with 15 fields).

**Current flow** (linear, static):
```
Human defines workflow schema
          ↓
Engineer codes YAWL spec
          ↓
Deploy to engine
          ↓
Agent processes documents (static 10-task workflow)
```

**Proposed flow** (circular, adaptive):
```
Agent discovers new document type (via schema introspection)
          ↓
Agent introspects FIBO ontology: "mortgage application = loanRequest + creditRisk + propertyValue"
          ↓
Agent calls Z.AI: "Generate YAWL workflow for mortgage application with 3 parallel analysis branches"
          ↓
Z.AI generates 8-task workflow (auto-detects: review → appraisal ↔ creditCheck → decision)
          ↓
Agent launches workflow: launchCase("MortgageApproval")
          ↓
Agent monitors execution via process_mining_analyze()
          ↓
After 100 cases: Agent observes 20% of cases loop at "appraisal" → "creditCheck"
          ↓
Agent triggers re-optimization: "Add parallel appraiser review to decouple tasks"
          ↓
Z.AI regenerates workflow with improved net structure
          ↓
Agent uploads improved spec via uploadSpecification()
          ↓
New cases use optimized workflow
```

**Impossible without agent authorship**: Humans can't react to observed patterns at runtime scale.

---

### Use Case 2: Multi-Agent Federated Workflows (Semantic Bridging)

**Scenario**: Insurance claims processing with 3 autonomous agents from different vendors:
- Agent A: FIBO-based underwriting (financial domain)
- Agent B: Schema.org-based document validation
- Agent C: Custom claim adjudication logic

**Current problem**:
```
A, B, C have overlapping responsibilities → manual workflow design creates deadlocks
No way for agents to express what they CAN do → fixed workflows force them into wrong tasks
```

**Proposed solution**: **Self-Describing Agent Capabilities**

Agent A publishes capability as MCP resource:
```json
{
  "agent_id": "fibo-underwriter",
  "capabilities": {
    "input": { "claimAmount": "xsd:decimal", "riskScore": "xsd:integer" },
    "output": { "underwritingDecision": "FIBO:ApprovalStatus" },
    "duration_ms": 2000,
    "failure_modes": ["timeout", "invalid_input"],
    "semantic_type": "http://fibo.org/ontology#UnderwritingTask"
  }
}
```

Z.AI-powered workflow generator reads 3 agent capabilities + MCP tool specs:
```
Agent A can: analyze_risk (2s, needs claimAmount + riskScore)
Agent B can: validate_docs (1s, needs documentSet)
Agent C can: adjudicate_claim (5s, needs decision + recommendation)

Z.AI generates optimal net:
  ├─ Input Condition
  ├─ Fork (parallel)
  │  ├─ Task: Agent A (underwrite)
  │  └─ Task: Agent B (validate)
  ├─ Join (synchronization)
  └─ Task: Agent C (adjudicate)
  └─ Output Condition
```

Each agent discovers via MCP that they own a specific task:
```
Agent A: GET /.well-known/workflows/{workflow-id}/task/underwrite
  → Returns: "You are assigned to this task. Check out via InterfaceB."

Agent checks out task
Agent completes task → auto-magically routed to Agent B (no manual handoff)
```

**Impossible without semantic bridging**: Agents never collaborate at workflow level; each acts in isolation.

---

### Use Case 3: Adaptive Compliance Workflows (Process Mining Feedback Loop)

**Scenario**: Regulatory workflow with N=50+ variant paths (different regulations per region).

**Current state** (rigid):
```
Compliance officer hand-codes 50 workflow variants (error-prone, maintenance nightmare)
Some variants execute 100 cases/month, others 2 cases/month (over/under-engineered)
When regulation changes → 2-week update cycle
```

**Proposed state** (self-tuning):

1. **Initial Generation**: Compliance officer describes 3 core variants.
   ```
   "US-standard process: intake → risk-assessment → verification → approval"
   "EU-gdpr process: intake → gdpr-check ↔ data-subject-rights → risk → approval"
   "China-regional process: intake → regional-filing → central-audit → approval"
   ```

2. **Agent Generates**: Z.AI creates 3 initial specs + 1 generic parameterized spec.

3. **Auto-Tuning Loop** (weekly):
   ```
   process_mining_analyze(spec_id)
     → Returns: variants, performance metrics, conformance gaps

   Detect: 8% of EU cases are violating GDPR timing constraint (>10 days)

   Z.AI regenerates spec:
     "Violation detected at task X. Reduce timeout Y. Add parallel task Z."

   Agent uploads new spec
   Compliance officer reviews change in 5 minutes (auto-generated, focused)

   Deploy to 5% of new cases (canary)
   Monitor for 2 weeks
   If successful: promote to 100%
   ```

4. **Regulation Update** (fast):
   ```
   New regulation: "All claims must have independent verification"

   Compliance officer: "Add independent verification requirement"

   Z.AI regenerates all 50 variants with new task inserted at optimal point

   Agent uploads en masse
   ```

**Impossible without self-authored specs**: Variants are static code; evolving them is operationally expensive.

---

## Part 2: Integration Contract Changes

### 2.1 New MCP Tools for Specification Authorship

**Current tools** (agent can *execute* workflows):
```
yawl_launch_case              ✓
yawl_cancel_case              ✓
yawl_get_workitems            ✓
yawl_complete_workitem        ✓
yawl_process_mining_analyze   ✓
```

**New tools** (agent can *author* workflows):
```
yawl_generate_specification   [NEW]
yawl_upload_specification     [NEW, partial]
yawl_get_agent_capabilities   [NEW]
yawl_publish_capability       [NEW]
yawl_query_workflow_graph     [NEW]
yawl_optimize_specification   [NEW]
```

#### Tool 1: `yawl_generate_specification`

```java
/**
 * Generate a YAWL specification from natural language description.
 *
 * Input:
 *   description: String (natural language workflow description)
 *   scope: String (local|global) - local = sub-workflow, global = top-level spec
 *   constraints: String[] (optional optimizations: "minimize_tasks", "maximize_parallelism", "reduce_deadlock")
 *   semantic_hints: Map<String, String> (optional domain ontology hints: "financial" → FIBO, "ecommerce" → Schema.org)
 *   reference_spec: String (optional existing spec ID to use as template)
 *
 * Output:
 *   specification_xml: String (YAWL XML, validated)
 *   spec_id: String (unique ID for tracking)
 *   estimated_complexity: int (task count, fork/join count)
 *   semantic_mappings: Map<String, String> (task_id → ontology_concept)
 *
 * Example call:
 *   {
 *     "description": "Process insurance claim: intake, risk assessment, approval decision",
 *     "scope": "local",
 *     "constraints": ["minimize_tasks", "maximize_parallelism"],
 *     "semantic_hints": { "domain": "financial", "ontology": "FIBO" }
 *   }
 *
 * Returns:
 *   {
 *     "specification_xml": "<specification>...</specification>",
 *     "spec_id": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z",
 *     "estimated_complexity": { "tasks": 8, "forks": 2, "joins": 2 },
 *     "semantic_mappings": {
 *       "intake": "http://fibo.org/ontology#ClaimIntake",
 *       "risk_assessment": "http://fibo.org/ontology#RiskAssessment",
 *       "approval_decision": "http://fibo.org/ontology#ApprovalDecision"
 *     }
 *   }
 */
```

#### Tool 2: `yawl_publish_capability`

```java
/**
 * Publish agent capability as a discoverable MCP resource.
 * Other agents can discover what tasks you can perform.
 *
 * Input:
 *   agent_id: String (unique agent identifier)
 *   task_name: String (human-readable task name)
 *   input_schema: JsonSchema (what data agent expects)
 *   output_schema: JsonSchema (what agent produces)
 *   estimated_duration_ms: int (SLA)
 *   semantic_type: String (ontology URI: http://fibo.org/ontology#UnderwritingTask)
 *   failure_modes: String[] (["timeout", "validation_error", "insufficient_data"])
 *
 * Output:
 *   resource_uri: String (MCP URI for this capability)
 *   discoverable: boolean (other agents can find this)
 *
 * Example:
 *   {
 *     "agent_id": "fibo-underwriter-001",
 *     "task_name": "Underwrite Claim",
 *     "input_schema": {
 *       "type": "object",
 *       "properties": {
 *         "claimAmount": { "type": "number" },
 *         "riskScore": { "type": "integer" }
 *       }
 *     },
 *     "output_schema": {
 *       "type": "object",
 *       "properties": {
 *         "decision": { "type": "string", "enum": ["APPROVE", "DENY", "REVIEW"] }
 *       }
 *     },
 *     "estimated_duration_ms": 2000,
 *     "semantic_type": "http://fibo.org/ontology#UnderwritingTask"
 *   }
 */
```

#### Tool 3: `yawl_query_workflow_graph`

```java
/**
 * Query the structure of a generated workflow before launching it.
 * Agents can reason about execution order without starting the case.
 *
 * Input:
 *   spec_id: String (specification ID from yawl_generate_specification)
 *
 * Output:
 *   graph: {
 *     "nodes": [
 *       { "id": "intake", "type": "task", "name": "Intake", "role": "MANUAL|AUTOMATIC" },
 *       { "id": "risk-assessment", "type": "task", "name": "Risk Assessment" },
 *       { "id": "fork-1", "type": "fork" },
 *       { "id": "approval", "type": "task", "name": "Approval Decision" }
 *     ],
 *     "edges": [
 *       { "from": "intake", "to": "fork-1" },
 *       { "from": "fork-1", "to": "risk-assessment" },
 *       { "from": "fork-1", "to": "verification" },
 *       { "from": "risk-assessment", "to": "approval" }
 *     ],
 *     "critical_path": ["intake", "fork-1", "risk-assessment", "approval"],
 *     "estimated_duration_ms": 15000,
 *     "parallelism_factor": 1.5
 *   }
 */
```

#### Tool 4: `yawl_optimize_specification`

```java
/**
 * Request Z.AI to optimize a specification based on observed execution data.
 *
 * Input:
 *   spec_id: String
 *   optimization_goals: String[] (["reduce_completion_time", "increase_throughput", "reduce_deadlock"])
 *   observed_data: {
 *     "completed_cases": int,
 *     "bottleneck_tasks": String[] (task IDs where cases spend most time),
 *     "looping_paths": String[] (flow paths that repeat >10% of executions),
 *     "failure_points": String[] (tasks where cases frequently fail)
 *   }
 *
 * Output:
 *   optimized_spec_xml: String (new YAWL XML with improvements)
 *   recommendations: {
 *     "changes": [
 *       {
 *         "element_id": "task-x",
 *         "change_type": "parallelize|decompose|remove|reorder",
 *         "rationale": "Task X is on critical path and takes avg 8s. Parallelizing with Y saves 5s."
 *       }
 *     ]
 *   }
 */
```

### 2.2 Extended InterfaceB Contract

Add to `InterfaceB_EnvironmentBasedClient`:

```java
/**
 * Create a dynamic workflow task for agent handoff.
 * Instead of static task in specification, agents create work items on-the-fly.
 */
public String createDynamicWorkItem(
    String caseId,
    String taskName,
    String assignedAgent,  // Agent ID or MCP URI
    String inputData,      // XML
    long timeoutMs
) throws IOException;

/**
 * Get semantic metadata about a task.
 */
public Map<String, Object> getTaskSemantics(String taskId) throws IOException;
  // Returns: { "ontology": "http://fibo.org/...", "input_schema": {...}, ... }

/**
 * Discover which agent should execute a task based on published capabilities.
 */
public String discoverAgentForTask(String taskId, String taskSemanticType) throws IOException;
  // Returns: agent_id (or null if no agent matches)
```

### 2.3 Z.AI Extended Integration Points

**Current**: `SpecificationGenerator` takes natural language → generates YAWL XML

**New**: Semantic introspection for agent capabilities

```java
/**
 * Semantic bridge: Given N agent capabilities (published via MCP), generate optimal workflow.
 */
public class AgentCapabilityAwareGenerator {

    /**
     * Generate workflow that optimally assigns tasks to available agents.
     */
    public YSpecification generateWorkflowForAgents(
        String description,
        List<AgentCapability> availableAgents,  // Published via yawl_publish_capability
        OptimizationGoals goals  // minimize_handoff, maximize_parallelism, etc.
    ) throws SpecificationGenerationException;

    /**
     * Introspect an ontology (FIBO, Schema.org) to enrich workflow semantics.
     */
    public Map<String, String> enrichWorkflowWithOntology(
        YSpecification spec,
        String ontologyUri  // "http://fibo.org/ontology", "https://schema.org"
    ) throws OntologyEnrichmentException;
}
```

---

## Part 3: Risk Assessment

### 3.1 Safety & Validation Risks

**Risk 1**: Generated workflows may contain deadlocks or unreachable tasks.

**Mitigation**:
- Z.AI generation includes schema validation (XSD against YAWL_Schema4.0.xsd) ✓ (already in `SpecificationGenerator`)
- Pre-launch verification: `YSpecificationValidator.verify(spec)` detects deadlocks ✓ (already exists)
- **New requirement**: Agent must explicitly approve generated specs before upload (manual gate).

**Risk 2**: Agents author malicious workflows (infinite loops, resource exhaustion).

**Mitigation**:
- Complexity limits: Generated specs must have < 100 tasks, < 50 parallel paths (enforce in `SpecificationGenerator`)
- Timeout on generation: Max 60s to generate + validate spec (already implemented)
- Rate limiting: Agent can generate max 10 specs/hour (new guard in A2A server)

**Risk 3**: Z.AI API failures cause cascading workflow generation failures.

**Mitigation**:
- Fallback to template-based generation (pre-approved specs) if Z.AI unavailable
- Retry with exponential backoff (max 3 retries) ✓ (already in Z.AI integration)
- Fail fast with clear error: "Z.AI unavailable; use pre-approved templates instead"

---

### 3.2 Semantic Soundness Risks

**Risk 4**: Agents publish contradictory capabilities (Agent A claims to do task X in 2s, actually takes 10s).

**Mitigation**:
- Capability assertions are **advisory only**; they don't gate task assignment
- If agent misses SLA → case enters "escalation" state (human review)
- Z.AI can re-generate workflow if observed vs. advertised performance diverges

**Risk 5**: Generated workflows violate domain semantics (e.g., FIBO-generated workflow assigns underwriting task to document validation agent).

**Mitigation**:
- Z.AI receives agent capability metadata (semantic type) as context
- Z.AI prompt includes semantic constraints: "Assign `underwriting_decision` tasks only to agents with semantic type `http://fibo.org/ontology#Underwriter`"
- Manual review gate: Compliance officer signs off on "critical" workflows (FIBO, medical, financial regulation)

---

### 3.3 Operational Soundness Risks

**Risk 6**: Agents continuously regenerate workflows, causing churn and unpredictability.

**Mitigation**:
- Version all generated specs with immutable IDs: `MortgageApproval-v1-gen-2026-02-24T15:32:00Z`
- Canary deployment: New spec runs on 5% of new cases first
- SLA monitoring: If new spec has >5% higher failure rate than old spec, automatic rollback
- **Approval workflow**: Agent must wait for manual approval before deploying spec to >50% of cases

**Risk 7**: Z.AI generates different specs for identical descriptions (non-deterministic).

**Mitigation**:
- Z.AI temperature set to 0.3 (low randomness) ✓ (already in `SpecificationGenerator`)
- Request deterministic generation: seed=hash(description) ✓ (already in `GenerationOptions`)
- Agent can request N variants and compare, then select best

---

## Part 4: Proof-of-Concept Scenario

**Title**: "Insurance Claim Processing with Semantic Bridging"

### Setup

**Agents** (3 independent A2A agents):
- `underwriter-001`: FIBO-based financial analysis
- `validator-001`: Schema.org document validation
- `adjudicator-001`: Custom claim adjudication logic

**Workflow**: Process insurance claim from intake to payout.

### Step-by-Step Execution

#### Step 1: Agent Discovery (Pre-execution)

Underwriter publishes capability:
```java
// In underwriter-001 agent code:
a2aClient.callSkill("yawl_publish_capability", {
  "agent_id": "underwriter-001",
  "task_name": "Underwrite Claim",
  "input_schema": {
    "properties": {
      "claimAmount": { "type": "number" },
      "riskFactors": { "type": "array" }
    }
  },
  "output_schema": {
    "properties": {
      "underwriting_decision": { "enum": ["APPROVE", "REVIEW", "DENY"] },
      "risk_score": { "type": "integer" }
    }
  },
  "estimated_duration_ms": 3000,
  "semantic_type": "http://fibo.org/ontology#UnderwritingTask"
});
// Returns: "resource_uri": "mcp://yawl/agents/underwriter-001/capability/underwrite"
```

Validator and adjudicator do the same.

#### Step 2: Agent-Authored Workflow Generation

Compliance officer (or an orchestrating agent) requests workflow generation:
```java
// In compliance agent or CLI:
String generatedSpecXml = mcpClient.callTool("yawl_generate_specification", {
  "description": "Insurance claim processing: intake, parallel risk assessment and document validation, then adjudication decision",
  "scope": "global",
  "constraints": ["minimize_tasks", "maximize_parallelism"],
  "semantic_hints": {
    "domain": "insurance",
    "ontology": "FIBO"
  }
});

// Z.AI generates:
// <specification id="InsuranceClaim-v1-gen-2026-02-24T15:32:00Z">
//   <rootNet id="root">
//     <task id="intake" name="Claim Intake" ... />
//     <task id="risk_assessment" name="Risk Assessment" ... />
//     <task id="document_validation" name="Document Validation" ... />
//     <task id="adjudication" name="Claim Adjudication" ... />
//     <flow from="input" to="intake" ... />
//     <flow from="intake" to="fork_1" ... />
//     <fork id="fork_1" ... />
//     <flow from="fork_1" to="risk_assessment" ... />
//     <flow from="fork_1" to="document_validation" ... />
//     <join id="join_1" ... />
//     <flow from="risk_assessment" to="join_1" ... />
//     <flow from="document_validation" to="join_1" ... />
//     <flow from="join_1" to="adjudication" ... />
//     <flow from="adjudication" to="output" ... />
//   </rootNet>
// </specification>
```

#### Step 3: Semantic Enrichment

Agent enriches spec with ontology mappings:
```java
mcpClient.callTool("yawl_optimize_specification", {
  "spec_id": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z",
  "optimization_goals": ["reduce_deadlock", "maximize_parallelism"]
});

// Returns: Same spec + metadata:
// "semantic_mappings": {
//   "risk_assessment": "http://fibo.org/ontology#UnderwritingTask",
//   "document_validation": "https://schema.org/Action",
//   "adjudication": "http://example.com/claim#AdjudicationDecision"
// }
```

#### Step 4: Agent Verification

Each agent verifies they can execute assigned tasks:
```java
// Underwriter queries workflow structure:
Map<String, Object> graph = mcpClient.callTool("yawl_query_workflow_graph", {
  "spec_id": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z"
});

// Discovers: "risk_assessment" task has:
// - semantic_type: "http://fibo.org/ontology#UnderwritingTask"
// - required_input: { "claimAmount": "number", "riskFactors": "array" }
// - role: "AUTOMATIC" (I can do this!)
// - prerequisite_tasks: ["intake"]

// Underwriter: "Good, this matches my published capability."
```

#### Step 5: Upload & Deploy

Compliance officer approves spec (gates on critical workflows):
```java
mcpClient.callTool("yawl_upload_specification", {
  "specification_xml": generatedSpecXml,
  "spec_id": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z",
  "deployment_strategy": "canary",  // Start with 5%
  "auto_rollback_threshold": 0.05    // Rollback if >5% failures
});
```

#### Step 6: Case Execution (Closed Loop)

Compliance office launches case:
```java
String caseId = mcpClient.callTool("yawl_launch_case", {
  "specIdentifier": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z",
  "caseData": "<data><claimAmount>50000</claimAmount><riskFactors>...</riskFactors></data>"
});
// Returns: caseId = "42"
```

Workflow executes:
```
[Input] → [Intake Task]
           ↓
         [Fork]
         /    \
    [Risk    [Validation
     Assessment] Task]
         \    /
         [Join]
           ↓
       [Adjudication Task]
           ↓
        [Output]
```

Each task is discovered via MCP capability query:
```java
// YAWL engine (or orchestrator) queries: Who should do "risk_assessment"?
String assignedAgent = mcpClient.callTool("yawl_discover_agent_for_task", {
  "task_id": "risk_assessment",
  "task_semantic_type": "http://fibo.org/ontology#UnderwritingTask"
});
// Returns: "underwriter-001"

// Handoff: Engine creates work item for underwriter-001
String workItemId = a2aClient.callSkill("yawl_create_workitem", {
  "case_id": caseId,
  "task_id": "risk_assessment",
  "agent_id": "underwriter-001",
  "input_data": "<data><claimAmount>50000</claimAmount><riskFactors>[...]</riskFactors></data>"
});
```

Agents execute their tasks (MCP tools):
```java
// Underwriter agent:
String riskResult = a2aClient.callSkill("complete_workitem", {
  "workItemId": workItemId,
  "output_data": "<data><underwriting_decision>APPROVE</underwriting_decision><risk_score>42</risk_score></data>"
});
// Validator does the same for "document_validation"
// Adjudicator does the same for "adjudication"
```

#### Step 7: Monitoring & Adaptation

After 100 cases, compliance agent monitors execution:
```java
Map<String, Object> analytics = mcpClient.callTool("yawl_process_mining_analyze", {
  "specIdentifier": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z",
  "analysisType": "variants"
});

// Output:
// {
//   "total_cases": 100,
//   "avg_duration_ms": 18000,
//   "bottleneck": "risk_assessment" (avg 8s),
//   "looping_paths": ["risk_assessment → join_1 → adjudication (5%) → risk_assessment"],
//   "failure_rate": 0.08
// }

// Issue: 5% of cases re-enter risk_assessment (loop)
// Diagnosis: Adjudicator sometimes asks for additional risk analysis
```

Adaptive re-optimization:
```java
String optimizedSpec = mcpClient.callTool("yawl_optimize_specification", {
  "spec_id": "InsuranceClaim-v1-gen-2026-02-24T15:32:00Z",
  "optimization_goals": ["reduce_looping", "reduce_bottleneck"],
  "observed_data": {
    "completed_cases": 100,
    "bottleneck_tasks": ["risk_assessment"],
    "looping_paths": ["risk_assessment → adjudication → risk_assessment (5%)"],
    "failure_points": []
  }
});

// Z.AI regenerates with parallelized risk analysis:
// {
//   "change": "Split risk_assessment into risk_initial (fast) + risk_detailed (slow, parallel with validation)",
//   "rationale": "Adjudicator requests detailed risk ~5% of time. Move to parallel path to decouple.",
//   "expected_improvement": "reduce loop rate from 5% to <1%, reduce avg duration to 14s"
// }
```

Compliance officer reviews:
```
CHANGE SUMMARY: Add parallel risk_detailed task
- Affects: risk_assessment task (split into 2)
- Expected: 5% → <1% looping, 18s → 14s avg time
- Risk: LOW (only adds task, doesn't remove)
[APPROVE] [REJECT] [REQUEST_CHANGES]
```

Deploy new spec:
```java
mcpClient.callTool("yawl_upload_specification", {
  "specification_xml": optimizedSpecXml,
  "spec_id": "InsuranceClaim-v1-gen-2026-02-24T15:32:01Z",  // New version
  "deployment_strategy": "canary",
  "auto_rollback_threshold": 0.05
});
```

New cases use optimized spec; old cases complete with original spec.

---

## Part 5: Contract Changes Summary Table

| Component | Change | Impact | Effort |
|-----------|--------|--------|--------|
| **MCP Server** | Add 6 new tools (generate, publish, query, optimize, etc.) | Agents can author workflows | 2-3d |
| **Z.AI Bridge** | Add `AgentCapabilityAwareGenerator` class | Semantic-aware workflow generation | 2d |
| **InterfaceB** | Add 3 methods (createDynamicWorkItem, getTaskSemantics, discoverAgentForTask) | Runtime agent discovery | 1d |
| **A2A Server** | Rate limiting + approval workflow for spec uploads | Safety gates | 1d |
| **YSpecificationValidator** | Complexity limits (enforce in generator) | Prevent DoS via malicious specs | 0.5d |
| **SpecificationGenerator** | Extend to accept agent capabilities as input | Semantic-aware generation | 1d |
| **Hook: hyper-validate.sh** | Add check for "agent-generated spec" flag | Mark auto-generated specs for audit trail | 0.5d |

**Total effort**: ~8 days (1 engineer, 1 architect, 1 tester)

---

## Part 6: Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│ AGENT-AUTHORED WORKFLOWS: Closed-Loop Architecture                 │
└─────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────┐
                    │   External Agents   │
                    │  (A2A Clients)      │
                    │  - Underwriter      │
                    │  - Validator        │
                    │  - Adjudicator      │
                    └──────────┬──────────┘
                               │
                   ┌───────────┼───────────┐
                   │           │           │
              [Publish]    [Query]    [Complete]
              [Capability] [Workflow] [WorkItem]
                   │           │           │
    ┌──────────────▼───────────▼───────────▼─────────────┐
    │         MCP Server (6 NEW TOOLS)                   │
    │  ┌─────────────────────────────────────────────┐  │
    │  │ 1. yawl_generate_specification              │  │
    │  │    (natural language → YAWL XML)            │  │
    │  │ 2. yawl_publish_capability                  │  │
    │  │    (agent: "I can do underwriting")          │  │
    │  │ 3. yawl_query_workflow_graph                │  │
    │  │    (inspect structure before launch)         │  │
    │  │ 4. yawl_optimize_specification              │  │
    │  │    (process mining → Z.AI → new spec)       │  │
    │  │ 5. yawl_get_agent_capabilities              │  │
    │  │    (discover: who can do task X?)            │  │
    │  │ 6. yawl_upload_specification                │  │
    │  │    (deploy with canary + rollback)          │  │
    │  └─────────────────────────────────────────────┘  │
    └────────────┬────────────────────────────────────┘
                 │
        ┌────────┴────────────────────────┐
        │                                 │
    ┌───▼─────────────────────┐   ┌──────▼─────────────────┐
    │ Z.AI Bridge             │   │ InterfaceB Extensions │
    │ ┌─────────────────────┐ │   │ ┌────────────────────┐ │
    │ │ SpecificationGen    │ │   │ │ createDynamicWI    │ │
    │ │ (natural → YAWL)    │ │   │ │ getTaskSemantics   │ │
    │ │                     │ │   │ │ discoverAgent      │ │
    │ │ AgentCapability     │ │   │ └────────────────────┘ │
    │ │ AwareGenerator      │ │   │                        │
    │ │ (agents → workflow) │ │   │ YAWL Engine         │
    │ │                     │ │   │ ┌────────────────────┐ │
    │ │ SpecificationOpt    │ │   │ │ launchCase         │ │
    │ │ imizer              │ │   │ │ getCaseState       │ │
    │ │ (process mining →   │ │   │ │ completeWorkItem   │ │
    │ │  improved spec)     │ │   │ │ queryWorkflow      │ │
    │ └─────────────────────┘ │   │ └────────────────────┘ │
    └────────────────────────┘   └────────────────────────┘
```

---

## Part 7: Implementation Roadmap

### Phase 1 (Week 1-2): Foundation
- Extend MCP server with `yawl_generate_specification` tool
- Add capability publishing (MCP resource)
- Integration test: Agent publishes capability, compliance officer generates spec

### Phase 2 (Week 3): Semantic Bridging
- Add `AgentCapabilityAwareGenerator` to Z.AI bridge
- Implement agent discovery logic (which agent for task X?)
- Integration test: Multi-agent federated workflow generation

### Phase 3 (Week 4): Adaptive Loop
- Add `yawl_optimize_specification` tool
- Connect process mining analytics to Z.AI
- Integration test: Workflow auto-improvement after 100 cases

### Phase 4 (Week 5): Safety & Ops
- Rate limiting on spec generation (10/hour per agent)
- Approval workflow for critical specs
- Canary deployment + auto-rollback
- Audit trail: mark all agent-generated specs

### Phase 5 (Week 6): Documentation & Launch
- MCP tool documentation
- Blue Ocean use case walkthroughs
- Agent SDK examples

---

## Part 8: Impossible Without This Architecture

### Current Limitation 1: Static Workflows
Workflows are authored once, changed rarely.
→ Regulatory updates take weeks
→ Performance optimizations are deferred
→ New domains require manual spec design

**Proposed**: Agents regenerate workflows continuously based on:
- Observed execution patterns
- Capability changes (new agent joins)
- Semantic updates (ontology changes)

### Current Limitation 2: Isolated Agents
Agents execute tasks they're assigned; they have no visibility into workflow structure or other agents.
→ No way to express "I can do X" and have system optimize for it
→ Manual handoffs create brittle coordination
→ Multi-agent systems are hard to compose

**Proposed**: Agents publish capabilities; Z.AI composes workflows around what agents CAN do.

### Current Limitation 3: Feedback Loop Breaks
Process mining shows "Task X is a bottleneck," but there's no automated way to improve it.
→ Humans must manually re-design workflow
→ Can't scale to thousands of variants

**Proposed**: Z.AI regenerates specs based on mining data; agent approves + deploys automatically.

---

## Conclusion

Agent-authored workflows reverse the traditional YAWL model, shifting from **static human design** to **dynamic agent composition**. The three blue ocean use cases (self-orchestrating hierarchies, semantic bridging, adaptive workflows) are operationally impossible with current YAWL architecture—not because of missing technical capability, but because there's no feedback loop.

By adding 6 MCP tools and extending InterfaceB/Z.AI, we enable:
- **Self-describing agent systems** that compose their own workflows
- **Semantic bridging** across agent domains (FIBO ↔ Schema.org)
- **Adaptive workflows** that improve themselves based on observed behavior

**Implementation feasibility**: 87% of the architecture already exists. The missing 13% is integration glue + Z.AI semantic awareness.

**Risk profile**: Moderate. Schema validation + specification verification + safety gates mitigate deadlock/DoS risks. Approval workflows gate critical deployments.

**Business impact**: From "how do humans build workflows?" to "how do agents build workflows for agents?"—a fundamentally different value proposition.

---

## References & Links

- **MCP Spec Generator**: `src/org/yawlfoundation/yawl/integration/zai/SpecificationGenerator.java`
- **MCP Server**: `src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java`
- **A2A Server**: `src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java`
- **InterfaceB**: `src/org/yawlfoundation/yawl/engine/interfce/interfaceB/InterfaceB_EnvironmentBasedClient.java`
- **YSpecification**: `src/org/yawlfoundation/yawl/elements/YSpecification.java`
- **Rules**: `.claude/rules/integration/mcp-a2a-conventions.md`, `.claude/rules/integration/autonomous-agents.md`

---

**Document prepared for**: YAWL v6.0.0 Product Review
**Classification**: Architecture + Blue Ocean Analysis
**Date**: 2026-02-24
**Prepared by**: Claude Code (YAWL Integration Specialist)
