---
paths:
  - "*/src/main/java/org/yawlfoundation/yawl/integration/autonomous/**"
  - "*/src/test/java/org/yawlfoundation/yawl/integration/autonomous/**"
---

# Autonomous Agent Rules

## Agent Framework
- `AutonomousAgent` — Lifecycle manager (init → discover → reason → act → report)
- `GenericPartyAgent` — Base implementation for YAWL-integrated agents
- `DiscoveryStrategy` — How agents find available work items
- `EligibilityReasoner` — Can this agent handle this work item?
- `DecisionReasoner` — Produce output for the work item (may call LLM)
- `OutputGenerator` — Format agent output for YAWL submission

## Virtual Threads
- Agent discovery loop runs on virtual thread: `Thread.ofVirtual().name("agent-discovery-" + agentId)`
- Each agent-work item interaction spawns a virtual thread
- Never use `synchronized` in agent code (pins virtual threads) — use `ReentrantLock`

## Z.AI Integration
- `ZaiEligibilityReasoner` — Uses GLM-4 for eligibility decisions
- `ZaiService` — HTTP client for Z.AI API calls
- Requires `ZHIPU_API_KEY` env var — fail fast if missing
- Retry with exponential backoff (max 3 retries) for transient API failures

## Work Item Lifecycle
1. Agent discovers available work item via InterfaceB
2. EligibilityReasoner evaluates capability match
3. Agent checks out work item (locks it)
4. DecisionReasoner processes the work item
5. OutputGenerator formats result
6. Agent checks in completed work item
7. If failure: release work item back to queue (do not silently swallow)

## Handoff Protocol
- JWT-authenticated handoff between agents (60-second TTL)
- `HandoffProtocol` generates tokens, `HandoffRequestService` manages transfers
- Retry on transient failure, escalate on persistent failure
- Conflict resolution: majority vote, escalation, or human fallback
