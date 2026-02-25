# ADR-025: Agent Coordination Protocol and Conflict Resolution

## Status
**ACCEPTED**

## Context

YAWL v6.0 introduces the `AutonomousAgent` framework (ADR-019) and the A2A protocol
server (`YawlA2AServer`). Multiple autonomous agents can now simultaneously discover
and compete for work items. The current implementation has three coordination gaps:

**Gap 1: Discovery Conflicts**

`PollingDiscoveryStrategy.discoverWorkItems()` calls
`InterfaceBClient.getAvailableWorkItems()` — all agents receive the same list.
Without coordination, two agents may attempt to checkout the same work item. The
Interface B checkout is atomic (409 on conflict), so correctness is preserved, but
throughput is degraded by redundant checkout attempts.

**Gap 2: Work Handoff**

There is no protocol for an agent to explicitly hand off a work item to another agent.
If Agent A starts reasoning about a work item and determines it lacks the capability
to complete it, it must either fail or complete it incorrectly. There is no mechanism
for Agent A to say "Agent B should handle this."

**Gap 3: Decision Disagreement**

When two agents produce conflicting outputs for the same work item (e.g., one agent
recommends "approve" and another recommends "reject" in a multi-review workflow),
there is no arbitration mechanism. Whichever agent completes the item first wins,
with no record of the disagreement.

**Gap 4: Claude Agent SDK Integration**

The Claude Agent SDK (used by Claude Code, Claude.ai, and third-party integrations)
communicates via MCP. Agents built on the Claude SDK need a defined protocol for
integrating with the YAWL autonomous agent framework — specifically, how they discover
work items via MCP tools and how the YAWL engine tracks Claude-agent participation.

## Decision

### Protocol Layer Architecture

The agent coordination protocol operates at three levels:

```
┌───────────────────────────────────────────────────────────────────────┐
│  Level 3: Orchestration (A2A server, multi-agent workflows)           │
│  - Multi-agent task graphs                                            │
│  - Orchestrator-to-worker delegation                                  │
│  - Claude Agent SDK integration via A2A                               │
└───────────────────────────┬───────────────────────────────────────────┘
                            │
┌───────────────────────────▼───────────────────────────────────────────┐
│  Level 2: Coordination (AgentRegistry, consensus)                     │
│  - Capability-based work routing                                      │
│  - Conflict resolution when agents disagree                           │
│  - Work handoff protocol                                              │
└───────────────────────────┬───────────────────────────────────────────┘
                            │
┌───────────────────────────▼───────────────────────────────────────────┐
│  Level 1: Execution (Interface B, atomic checkout)                    │
│  - Atomic work item checkout (409 on conflict)                        │
│  - Idempotent completion                                              │
│  - Exception path on failure                                          │
└───────────────────────────────────────────────────────────────────────┘
```

### Agent Discovery Flow

The discovery flow defines how an agent finds work items appropriate for its
capabilities and avoids contention with other agents.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Agent Discovery Cycle (per poll interval)            │
│                                                                         │
│  Step 1: Poll engine for available work items                           │
│    GET /ib/workitems?status=Enabled&limit=50                            │
│    (InterfaceBClient.getAvailableWorkItems)                             │
│                                                                         │
│  Step 2: Filter by local capability                                     │
│    EligibilityReasoner.canHandle(item, agentCapability)                 │
│    Items not matching capability are discarded immediately              │
│                                                                         │
│  Step 3: Query AgentRegistry for competing agents                       │
│    GET /agents/by-capability?domain={agentDomain}                       │
│    If only 1 eligible agent exists → proceed directly to checkout       │
│    If N eligible agents exist → apply partition strategy (see below)    │
│                                                                         │
│  Step 4: Partition strategy (N agents competing)                        │
│    Hash(workItemId) % N == agentIndex                                   │
│    Agent only processes items where the hash matches its index          │
│    This eliminates ~(N-1)/N of redundant checkout attempts             │
│                                                                         │
│  Step 5: Atomic checkout (Interface B)                                  │
│    POST /ib/workitems/{id}/checkout                                     │
│    → 200: proceed to decision                                           │
│    → 409: item already taken, skip (another agent was faster)          │
│    → 404: item completed before checkout (normal, skip)                │
│                                                                         │
│  Step 6: Execute DecisionReasoner                                       │
│  Step 7: Complete via Interface B                                       │
└─────────────────────────────────────────────────────────────────────────┘
```

**Partition strategy detail:**

```java
// In PollingDiscoveryStrategy.selectItemsForThisAgent()
private boolean isAssignedToThisAgent(WorkItemRecord item, int agentIndex, int totalAgents) {
    // Consistent hash: deterministic, no coordination required
    int hash = Math.abs(item.getID().hashCode());
    return (hash % totalAgents) == agentIndex;
}

// agentIndex is derived from the agent's registration order in AgentRegistry
// totalAgents is the count of registered agents with matching capability
// Both values are refreshed every N discovery cycles to handle agent churn
```

### Work Handoff Protocol

When an agent that has checked out a work item determines it cannot complete it,
it executes a structured handoff via A2A.

```
┌────────────────────────────────────────────────────────────────────────┐
│  Handoff Sequence                                                      │
│                                                                        │
│  Agent A has checked out work item WI-42                               │
│  Agent A's EligibilityReasoner returns false after checkout            │
│  (e.g., document language not supported by A's LLM model)             │
│                                                                        │
│  1. Agent A queries registry for a capable substitute:                 │
│     GET /agents/by-capability?domain={required_capability}             │
│     → Agent B found                                                    │
│                                                                        │
│  2. Agent A sends an A2A handoff message to Agent B:                   │
│     POST http://agent-b:8091/ (A2A message)                            │
│     {                                                                  │
│       "parts": [{                                                      │
│         "type": "text",                                                │
│         "text": "YAWL_HANDOFF:WI-42:session_handle_encrypted"         │
│       }]                                                               │
│     }                                                                  │
│                                                                        │
│  3. Agent B receives the handoff, checks out WI-42 using A's           │
│     session handle, and proceeds with its own reasoning                │
│                                                                        │
│  4. Agent A rolls back its checkout:                                   │
│     POST /ib/workitems/WI-42/rollback (Interface B)                   │
│     Work item returns to Enabled state                                 │
│                                                                        │
│  5. Agent B checks out WI-42 from Enabled state and completes it      │
│                                                                        │
│  Timeout: if Agent B does not acknowledge the handoff within 30s,      │
│  Agent A rolls back and the item becomes available to any agent         │
└────────────────────────────────────────────────────────────────────────┘
```

**Handoff message schema (A2A text message prefix):**

```
YAWL_HANDOFF:{workItemId}:{encryptedHandoffToken}
```

The `encryptedHandoffToken` is a short-lived JWT (TTL: 60 seconds) signed with
the initiating agent's API key master key. It contains:

```json
{
  "sub": "handoff",
  "workItemId": "WI-42",
  "fromAgent": "agent-a-id",
  "engineSession": "<interface-b-session-handle>",
  "exp": 1740000060
}
```

### Conflict Resolution When Agents Disagree

Multi-agent review workflows (e.g., two agents must both approve a document) can
produce conflicting recommendations. The conflict resolution protocol has three tiers:

**Tier 1: Structured Voting (no human needed)**

When a workflow task is marked with `conflictResolution=MAJORITY_VOTE` in the
`<agentBinding>` element:

```xml
<task id="ReviewDocument">
  <agentBinding>
    <agentType>autonomous</agentType>
    <capabilityRequired>document-review</capabilityRequired>
    <reviewQuorum>3</reviewQuorum>
    <conflictResolution>MAJORITY_VOTE</conflictResolution>
    <conflictArbiter>document-review-supervisor-agent</conflictArbiter>
    <timeout>PT15M</timeout>
  </agentBinding>
</task>
```

The workflow creates three parallel instances of the task (multi-instance task,
YAWL WFP-13). Each agent produces an independent decision. The `conflictArbiter`
agent collects the three outputs and returns the majority decision.

```
Agent 1: APPROVE  ─┐
Agent 2: REJECT   ─┤─► Arbiter: 2x APPROVE → final=APPROVE
Agent 3: APPROVE  ─┘
```

**Tier 2: Escalation to Supervisor Agent**

When agents produce outputs outside a configurable agreement threshold and
`conflictResolution=ESCALATE`:

```
Agreement threshold: < 60% of agents agree
↓
Arbiter agent receives all agent decisions + work item data
↓
Arbiter produces a resolution with reasoning
↓
Resolution is logged to WorkflowEventStore (ADR-006: traceability)
```

**Tier 3: Human Fallback**

When no automated resolution is possible (arbiter itself is unavailable, or all
agents disagree) and `<fallbackToHuman>true</fallbackToHuman>` is set in the spec:

```
All agent reasoning attempts exhausted
↓
Work item returned to Enabled state via Interface B rollback
↓
Item offered to human participants via ResourceService
↓
Human completes the work item via standard YAWL UI
↓
Event logged: AGENT_CONFLICT_ESCALATED_TO_HUMAN
```

The event log entry captures all agent decisions, their reasoning (truncated to
512 chars), and the conflict timestamp. This is written to the `WorkflowEventStore`
(event sourcing module, `org.yawlfoundation.yawl.integration.eventsourcing`).

### Integration with Claude Agent SDK

Claude agents (Claude Code, Claude.ai assistant, custom Claude-based agents)
integrate with YAWL through two paths:

**Path 1: MCP Tool Use (direct work item control)**

A Claude agent connected to the MCP server can invoke YAWL tools directly. This
is the integration pattern for Claude Desktop / Claude Code usage:

```
Claude Agent (MCP client)
    │
    │  MCP call: list_resources("yawl://workitems")
    │  MCP call: call_tool("checkout_work_item", {id: "WI-42"})
    │  MCP call: call_tool("complete_work_item", {id: "WI-42", data: "..."})
    │
    ▼
YawlMcpServer (STDIO or SSE transport)
    │
    │  Interface B: checkoutWorkItem, completeWorkItem
    │
    ▼
YAWL Engine
```

**Agent registration for MCP clients:**

Claude MCP clients are registered in the `AgentRegistry` as `MCP_CLIENT` type:

```json
POST /agents/register
{
  "id": "claude-desktop-mcp-session-<uuid>",
  "name": "Claude Desktop (MCP)",
  "type": "MCP_CLIENT",
  "capability": {
    "domainName": "claude-general",
    "description": "Claude AI agent via MCP tool use"
  },
  "endpoint": null,
  "sessionId": "<mcp-session-id>"
}
```

MCP client agents do not participate in the partition strategy (they are interactive,
not polling agents). They are registered for observability and audit trail purposes.

**Path 2: A2A Protocol (agent-to-agent delegation)**

A Claude agent acting as an orchestrator can delegate sub-tasks to the YAWL A2A server.
This enables Claude to launch workflows, monitor their progress, and react to
completion events.

```
Claude Orchestrator Agent
    │
    │  A2A message: "Launch InvoiceProcessing workflow for invoice #42"
    │  (POST to YawlA2AServer: /.well-known/agent.json discovery first)
    │
    ▼
YawlA2AServer (authenticated via JWT or API key)
    │
    │  Processes natural language → Interface B operations
    │  (or ZaiFunctionService if ZAI_API_KEY configured)
    │
    ▼
YAWL Engine (launches case, returns case ID)
    │
    ▼
Claude Agent receives: "Case 42 launched. 3 work items pending."
```

**Context sharing between Claude and YAWL:**

The Claude Agent SDK's scoped values (ScopedValue) propagate context through the
agent's reasoning stack. When Claude invokes a YAWL MCP tool, the following context
is available in the MCP session:

```
WorkflowContext {
    caseId:      "42"          (set after case launch)
    specId:      "InvoiceProc" (set when spec selected)
    workItemId:  "WI-42"       (set when item checked out)
    agentId:     "claude-xxx"  (set at MCP session creation)
    sessionId:   "mcp-yyy"     (MCP session handle)
}
```

This context is included in all MCP log notifications (via `McpLoggingHandler`)
and propagated to the `WorkflowEventStore` for traceability.

### Memory and Context Sharing Between Agents

Autonomous agents in YAWL do not share in-process memory (they may run in
different JVMs or containers). The shared context model uses three tiers:

**Tier 1: Work Item Data (YAWL Engine)**

All agents access the same work item data via Interface B. The data document
attached to a work item is the primary shared context. Agents write their
intermediate decisions as output data, which becomes input data for subsequent
tasks in the workflow.

**Tier 2: Event Store (Read-only shared history)**

The `WorkflowEventStore` (event sourcing module) provides an append-only log
of all workflow events visible to all agents:

```java
// Agent can query past events for context
TemporalCaseQuery query = TemporalCaseQuery.builder()
    .caseId("42")
    .afterTimestamp(Instant.now().minus(Duration.ofHours(1)))
    .eventTypes(Set.of(ITEM_COMPLETED, AGENT_DECISION_LOGGED))
    .build();
List<WorkflowEvent> history = eventStore.query(query);
```

**Tier 3: A2A Task Context (Cross-agent)**

When an orchestrator delegates to a worker agent via A2A, context is passed
in the A2A message payload. The A2A task ID links the delegated task back to
the originating workflow case:

```json
{
  "parts": [{
    "type": "text",
    "text": "Complete document review for YAWL case 42, work item WI-87"
  }, {
    "type": "data",
    "data": {
      "yawl_case_id": "42",
      "yawl_work_item_id": "WI-87",
      "yawl_spec_id": "InvoiceProcessing",
      "context_summary": "Invoice from ACME Corp, $45,000, requires approval by senior reviewer"
    }
  }]
}
```

### Conflict Resolution Decision Tree

```
Work item needs completion
         │
         ▼
  Single agent eligible?
    ├─ YES → direct execution, no coordination needed
    └─ NO  →
         │
         ▼
  Multiple agents, same capability
         │
         ▼
  agentBinding.reviewQuorum == 1?
    ├─ YES → partition strategy, first successful checkout wins
    └─ NO  →
         │
         ▼
  conflictResolution == MAJORITY_VOTE?
    ├─ YES → multi-instance task, arbiter agent collects votes
    └─ NO  →
         │
         ▼
  conflictResolution == ESCALATE?
    ├─ YES → collect N agent decisions, escalate to supervisor agent
    └─ NO  →
         │
         ▼
  fallbackToHuman == true?
    ├─ YES → roll back to Enabled, offer to human participants
    └─ NO  → fail work item → Interface X exception path
```

## Consequences

### Positive

1. Partition strategy eliminates ~75% of redundant checkout attempts in a 4-agent pool
2. Work handoff prevents work items from being held by incapable agents without a
   human having to intervene
3. Majority-vote conflict resolution is fully automated — no human is needed for
   standard disagreements
4. Claude agents integrate via both MCP (for interactive use) and A2A (for
   orchestration) without changes to the YAWL engine
5. The event store provides a complete audit trail of agent decisions, handoffs,
   and conflicts

### Negative

1. Partition strategy requires all competing agents to have accurate knowledge of
   the total agent count — a registering agent during active processing can cause
   brief imbalance
2. The handoff JWT token introduces a short cryptographic dependency on the A2A
   auth infrastructure (A2A_API_KEY_MASTER must be available to initiating agent)
3. Multi-instance tasks (majority vote) increase work item count by N — YAWL engine
   must execute N parallel instances for each review task

### Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| Agent count changes during partition cycle | MEDIUM | LOW | Recompute partition every 5 cycles; max imbalance is 1 item per churn event |
| Handoff JWT expires before Agent B can checkout | LOW | MEDIUM | TTL 60s covers typical A2A round-trip; configurable via `yawl.agent.handoff.ttl` |
| All agents in a pool crash simultaneously | LOW | HIGH | Fallback to human (if configured); `<agentBinding>` timeout reclaims the item |
| Arbiter agent itself disagrees with all workers | LOW | MEDIUM | Arbiter is configured as a higher-capability agent; ultimate fallback is Tier 3 human |
| MCP context lost on session reconnect | MEDIUM | LOW | WorkflowContext reconstructed from YAWL engine state on reconnect |

## Alternatives Considered

### Centralised Work Item Queue
Replace `getAvailableWorkItems()` polling with a broker (Kafka, RabbitMQ) that
delivers each work item to exactly one agent. Eliminates partition strategy
complexity. Rejected because it requires a message broker dependency and a
fundamental change to the Interface B contract — existing YAWL clients (human
participants, resource service) do not use broker-based delivery.

### Optimistic Locking Without Partition Strategy
Allow all agents to attempt checkout, relying on the atomic 409 response to resolve
conflicts. Simple, no coordination. Rejected because at 1000 work items/sec with
4 agents per domain, 75% of checkouts fail — this wastes 3× the Interface B API calls
and reduces throughput significantly.

### Shared In-Memory Cache (Hazelcast)
Use Hazelcast to share work item state across agents in the same JVM group, so agents
can coordinate without polling. Rejected (same reason as ADR-014: Hazelcast adds
operational complexity for marginal benefit over the atomic checkout approach).

### Blockchain-based Conflict Resolution
Rejected. Unnecessary complexity, high latency, and architectural mismatch with the
synchronous nature of workflow task completion.

## Related ADRs

- ADR-008: Resilience4j (circuit breaker for agent-to-agent A2A calls)
- ADR-014: Clustering (Redis lease protocol at engine level is separate from agent coordination)
- ADR-017: Authentication (JWT used for handoff tokens)
- ADR-019: Autonomous Agent Framework (agent types this protocol coordinates)
- ADR-023: MCP/A2A CI/CD Deployment (how agents are deployed for coordination)
- ADR-024: Multi-Cloud Agent Deployment (regional topology this protocol operates within)

## Implementation Notes

### Partition Strategy Implementation Target

```
org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy
```

The `discoverWorkItems()` method currently returns all eligible items. Add a
`partitionFilter(List<WorkItem>, AgentRegistryClient, AgentConfiguration)` method
called before returning the list. The filter uses the hash modulo approach described
in the Discovery Flow section above.

### Handoff Token Implementation Target

```
org.yawlfoundation.yawl.integration.autonomous.strategies.HandoffProtocol
```

New class. Produces and validates the handoff JWT using the existing
`A2AAuthenticationProvider` JWT infrastructure from
`org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider`.

### Conflict Resolution Implementation Target

```
org.yawlfoundation.yawl.integration.autonomous.conflict.ConflictResolver
```

New interface with implementations:
- `MajorityVoteConflictResolver` — counts decisions, returns majority
- `EscalatingConflictResolver` — collects all and delegates to arbiter via A2A
- `HumanFallbackConflictResolver` — rolls back work item, removes from agent queue

### Event Logging for Decisions

All agent decisions (including conflicts and handoffs) are logged to the
`WorkflowEventStore`:

```java
// Called by GenericPartyAgent after each decision
eventStore.append(new AgentDecisionEvent(
    workItemId,
    agentId,
    decision.outcome(),
    decision.reasoning(),   // truncated to 512 chars
    Instant.now()
));
```

## Approval

**Approved by:** YAWL Architecture Team
**Date:** 2026-02-18
**Implementation Status:** PLANNED (v6.0.0 — partial; v6.1.0 — full)
**Review Date:** 2026-08-18

---

**Revision History:**
- 2026-02-18: Initial version
