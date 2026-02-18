# ADR-019: Autonomous Agent Framework Architecture

## Status
**ACCEPTED**

## Context

YAWL v5.x supports automated tasks via "codelets" — Java classes implementing
`YCodelet` that execute inline within the engine. Codelets are:
1. Deployed inside the engine WAR — no deployment independence
2. Synchronous — they block an engine thread during execution
3. Not AI-capable — they are deterministic Java logic, not inference models
4. Not composable — each codelet is independent; there is no framework for
   multi-step reasoning or tool use

The rise of large language model (LLM) agents and autonomous AI systems creates a
new requirement: YAWL tasks should be executable by AI agents that can reason about
task data, call external tools, and produce structured output — all without modifying
the engine code.

The YAWL Thesis on Autonomous Workflow Agents (`docs/THESIS_Autonomous_Workflow_Agents.md`)
identifies four agent archetypes in workflow systems:
- **Discovery agents**: find available work
- **Eligibility agents**: determine if they can handle a work item
- **Decision agents**: produce the task output (the reasoning step)
- **Execution agents**: take real-world actions based on decisions

For v6.0.0, YAWL must support all four archetypes while preserving Interface B backward
compatibility and the Petri net execution semantics.

## Decision

**YAWL v6.0.0 introduces the AutonomousAgent framework: a plugin-based, strategy-pattern
architecture that allows AI agents and automated systems to participate in workflow
execution as first-class participants.**

### Core Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    YAWL Engine                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │           AgentRegistry (singleton)                 │ │
│  │   - registers agents at startup                     │ │
│  │   - routes work items to eligible agents            │ │
│  └──────────────────┬──────────────────────────────────┘ │
└─────────────────────│────────────────────────────────────┘
                      │ Interface B (standard checkout/complete)
                      │
     ┌────────────────▼─────────────────────────────────────┐
     │               AutonomousAgent (abstract)             │
     │  + lifecycle: start(), stop(), getStatus()           │
     │  + discoverWork() → List<WorkItemRecord>             │
     │  + canHandle(item) → boolean                         │
     │  + execute(item) → AgentResult                       │
     │  + onSuccess(item, result)                           │
     │  + onFailure(item, exception)                        │
     └───────┬────────────────────────────────────────────┬─┘
             │                                            │
    ┌────────▼─────────────┐              ┌──────────────▼──────────┐
    │  LlmWorkflowAgent    │              │  RuleBasedAgent         │
    │  (AI decision-making)│              │  (deterministic logic)  │
    └──────────────────────┘              └─────────────────────────┘
```

### Four Strategy Interfaces

Each agent plugs in its own strategy implementations:

```java
// 1. Discovery: how does the agent find work?
public interface DiscoveryStrategy {
    List<WorkItemRecord> discover(SessionHandle session,
                                  AgentCapabilities capabilities);
}

// Implementations: PollingDiscovery, WebhookDiscovery, SpecificationFilterDiscovery

// 2. Eligibility: can the agent handle a specific work item?
public interface EligibilityReasoner {
    boolean canHandle(WorkItemRecord item, AgentCapabilities capabilities);
}

// Implementations: CapabilityMatchReasoner, LlmEligibilityReasoner,
//                  AnnotationBasedReasoner (reads <agentBinding> from spec)

// 3. Decision: produce the task output
public interface DecisionReasoner {
    AgentDecision reason(WorkItemRecord item,
                         TaskData inputData,
                         AgentContext context);
}

// Implementations: LlmDecisionReasoner, RuleEngineReasoner, ScriptReasoner

// 4. Output: convert the decision to YAWL task output data
public interface OutputGenerator {
    TaskData generate(AgentDecision decision,
                      TaskOutputSchema schema);
}

// Implementations: XmlOutputGenerator, JsonToXmlOutputGenerator, TemplateOutputGenerator
```

### Agent Lifecycle

```java
// org.yawlfoundation.yawl.agents.AutonomousAgent
public abstract class AutonomousAgent {

    private final AgentConfig config;
    private final DiscoveryStrategy discovery;
    private final EligibilityReasoner eligibility;
    private final DecisionReasoner decision;
    private final OutputGenerator output;
    private volatile AgentStatus status;

    // Lifecycle managed by AgentRegistry — agents do NOT manage their own threads
    public final void start() { /* called by registry */ }
    public final void stop() { /* called by registry */ }

    // Work item processing pipeline
    public final void processWorkItem(WorkItemRecord item) {
        if (!eligibility.canHandle(item, getCapabilities())) {
            return;
        }
        TaskData inputData = interfaceB.checkoutWorkItem(item.getId(), session);
        try {
            AgentDecision dec = decision.reason(item, inputData, buildContext(item));
            TaskData outputData = output.generate(dec, item.getOutputSchema());
            interfaceB.completeWorkItem(item.getId(), outputData, session);
            onSuccess(item, dec);
        } catch (AgentExecutionException ex) {
            onFailure(item, ex);
            // raises Interface X exception if configured
        }
    }
}
```

### MCP Integration (Model Context Protocol)

AI agents integrated via the MCP server (`YawlMcpServer`) receive work items as
"tool call" opportunities. The MCP bridge translates between MCP tool schema and
YAWL task data schema:

```
MCP Client (Claude, GPT, etc.)
    │
    │ MCP tool call: "complete_work_item"
    │ with arguments: {itemId, outputData}
    ▼
YawlMcpServer (translates MCP ↔ Interface B)
    │
    │ POST /ib/workitems/{itemId}/complete
    ▼
YAWL Engine (standard Interface B)
```

### A2A Integration (Agent-to-Agent)

For multi-agent workflows, YAWL's A2A server (`YawlA2AServer`) enables agents to
delegate sub-tasks to specialised agents using the A2A protocol. An orchestrating
agent can launch a YAWL case, monitor it, and receive completion events.

### Specification-Level Agent Binding

Agents are bound to tasks via the `<agentBinding>` element (ADR-013: Schema Versioning):

```xml
<task id="AnalyseDocument">
  <agentBinding xmlns="http://www.yawl-system.com/schema/YAWL/v6">
    <agentType>autonomous</agentType>
    <capabilityRequired>document-analysis</capabilityRequired>
    <preferredAgentId>doc-analysis-agent-v2</preferredAgentId>
    <fallbackToHuman>true</fallbackToHuman>
    <maxRetries>3</maxRetries>
    <timeout>PT10M</timeout>
  </agentBinding>
</task>
```

The `AgentRegistry` uses `capabilityRequired` to match work items to registered agents.
If no eligible agent is available and `fallbackToHuman` is true, the work item is
offered to human participants via the resource service.

## Consequences

### Positive

1. AI agents participate in workflows using the standard Interface B API — no engine
   changes required when adding new agent types
2. Strategy pattern enables mixing AI decision-making with deterministic rule engines
   in the same agent
3. MCP and A2A integration bridges connect YAWL to the growing ecosystem of AI agent
   frameworks without tight coupling
4. `<agentBinding>` in the specification makes agent assignment declarative and
   version-controlled

### Negative

1. Agent execution adds a new failure mode: if an AI agent produces invalid output,
   the work item enters the exception path
2. LLM agents have non-deterministic behaviour — testing requires mock LLM responses
   or recorded test fixtures
3. Agent registry is a new singleton dependency that must be considered in clustering
   (ADR-014: each node runs its own agent threads; work item locks prevent double-execution)

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Agent produces output that fails schema validation | MEDIUM | MEDIUM | OutputGenerator validates against task schema before completing work item |
| LLM agent timeouts block work items indefinitely | MEDIUM | HIGH | Configurable timeout per `<agentBinding>`; expired agents trigger Interface X exception |
| Multiple agents compete for same work item | LOW | HIGH | Interface B checkout is atomic; losing agent receives 409 and skips the item |
| Agent crashes corrupt case state | LOW | HIGH | Work item is rolled back to Enabled state after checkout timeout (configurable) |

## Alternatives Considered

### Extend Codelets for AI
Adding LLM calls inside existing `YCodelet` implementations was considered. Rejected
because codelets are synchronous and run inside the engine — blocking an engine thread
on LLM inference (potentially seconds) would degrade throughput significantly.

### Separate Agent Sidecar Process
Running agents in a completely separate process (no shared JVM) with communication
only via Interface B REST. This is the preferred model for MCP/A2A agents. For Java
agents deployed with the engine, the in-process model with virtual threads (ADR-010)
is more efficient for low-latency deterministic tasks.

## Related ADRs

- ADR-001: Dual Engine Architecture (agents work with both stateful and stateless engines)
- ADR-010: Virtual Threads (agent discovery polling uses virtual thread executors)
- ADR-013: Schema Versioning (defines `<agentBinding>` schema element)
- ADR-017: Authentication (agents use service account JWTs)

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-17
**Implementation Status:** IN PROGRESS (v6.0.0)
**Review Date:** 2026-08-17

---

**Revision History:**
- 2026-02-17: Initial version
