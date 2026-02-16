# Generic Autonomous Agent Framework - Architecture Diagram

## Layer Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                    Application Layer                               │
│  ┌──────────────────────┐    ┌──────────────────────────────────┐ │
│  │ AgentFactory         │    │ GenericWorkflowLauncher          │ │
│  │ - fromEnvironment()  │    │ - Launch any YAWL spec           │ │
│  │ - create(config)     │    │ - Parameterized case data        │ │
│  └──────────────────────┘    └──────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Creates
                                    ▼
┌────────────────────────────────────────────────────────────────────┐
│                    Agent Framework Layer                           │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ AutonomousAgent (interface)                                  │ │
│  │ - start() / stop() / isRunning()                             │ │
│  │ - getCapability() / getAgentCard()                           │ │
│  └────────────────────────┬─────────────────────────────────────┘ │
│                           │ Implemented by                         │
│                           ▼                                         │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ GenericPartyAgent                                            │ │
│  │ - Discovery loop (poll work items)                           │ │
│  │ - HTTP server (/.well-known/agent.json)                      │ │
│  │ - Strategy injection (discovery, eligibility, decision)      │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌──────────────────────┐    ┌──────────────────────────────────┐ │
│  │ AgentConfiguration   │    │ AgentCapability                  │ │
│  │ - Builder pattern    │    │ - Domain name                    │ │
│  │ - Immutable          │    │ - Description                    │ │
│  └──────────────────────┘    └──────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Uses
                                    ▼
┌────────────────────────────────────────────────────────────────────┐
│                  Reasoning & Strategy Layer                        │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ DiscoveryStrategy (interface)                                │ │
│  │ - discoverWorkItems(client, session)                         │ │
│  └────────────┬─────────────────────────────────────────────────┘ │
│               │ Implementations:                                   │
│               ├─ PollingDiscoveryStrategy (query InterfaceB)       │
│               ├─ EventDrivenDiscoveryStrategy (InterfaceE events)  │
│               └─ WebhookDiscoveryStrategy (REST callbacks)         │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ EligibilityReasoner (interface)                              │ │
│  │ - isEligible(workItem) → boolean                             │ │
│  └────────────┬─────────────────────────────────────────────────┘ │
│               │ Implementations:                                   │
│               ├─ ZaiEligibilityReasoner (LLM-based)                │
│               ├─ StaticMappingReasoner (task→agent JSON)           │
│               ├─ RulesEligibilityReasoner (Drools, future)         │
│               └─ TemplateDecisionReasoner (static templates)       │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ DecisionReasoner (interface)                                 │ │
│  │ - produceOutput(workItem) → XML string                       │ │
│  └────────────┬─────────────────────────────────────────────────┘ │
│               │ Implementations:                                   │
│               ├─ ZaiDecisionReasoner (LLM generates XML)           │
│               ├─ TemplateDecisionReasoner (Mustache templates)     │
│               └─ RulesDecisionReasoner (decision tables, future)   │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ OutputGenerator (interface)                                  │ │
│  │ - generateOutput(workItem, decision) → formatted output      │ │
│  └────────────┬─────────────────────────────────────────────────┘ │
│               │ Implementations:                                   │
│               ├─ XmlOutputGenerator (dynamic root element)         │
│               ├─ JsonOutputGenerator (JSON schemas, future)        │
│               └─ TemplateOutputGenerator (Mustache/Freemarker)     │
└────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ Calls
                                    ▼
┌────────────────────────────────────────────────────────────────────┐
│                    Integration Layer                               │
│  ┌──────────────────────┐    ┌──────────────────────────────────┐ │
│  │ InterfaceB_Client    │    │ ZaiService                       │ │
│  │ - connect()          │    │ - chat(prompt)                   │ │
│  │ - getLiveWorkItems() │    │ - setSystemPrompt()              │ │
│  │ - checkOut/checkIn() │    └──────────────────────────────────┘ │
│  └──────────────────────┘                                          │
│  ┌──────────────────────┐    ┌──────────────────────────────────┐ │
│  │ MetricsCollector     │    │ StructuredLogger                 │ │
│  │ - recordLatency()    │    │ - logSpan(name, duration)        │ │
│  │ - incrementCounter() │    └──────────────────────────────────┘ │
│  └──────────────────────┘                                          │
└────────────────────────────────────────────────────────────────────┘
```

## Agent Discovery Loop Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                  Agent Discovery Loop                           │
│                                                                 │
│  1. Start                                                       │
│     ├─ Connect to YAWL Engine (InterfaceB)                      │
│     ├─ Start HTTP server (/.well-known/agent.json)             │
│     └─ Start discovery thread                                  │
│                                                                 │
│  2. Discovery Cycle (while running):                           │
│     ┌───────────────────────────────────────────────────────┐  │
│     │ a. Discover Work Items                                │  │
│     │    └─ discoveryStrategy.discoverWorkItems()           │  │
│     │       • PollingStrategy → getCompleteListOfLiveWI()   │  │
│     │       • EventDrivenStrategy → poll event queue        │  │
│     │                                                        │  │
│     │ b. For each work item w:                              │  │
│     │    ┌────────────────────────────────────────────────┐ │  │
│     │    │ i. Check Eligibility                           │ │  │
│     │    │    if (eligibilityReasoner.isEligible(w)):     │ │  │
│     │    │       • ZaiReasoner → LLM analyzes capability  │ │  │
│     │    │       • StaticMapping → lookup task in JSON    │ │  │
│     │    │                                                 │ │  │
│     │    │ ii. Checkout (if eligible)                     │ │  │
│     │    │     checkOutWorkItem(w.id)                     │ │  │
│     │    │                                                 │ │  │
│     │    │ iii. Produce Output                            │ │  │
│     │    │      output = decisionReasoner.produceOutput(w)│ │  │
│     │    │       • ZaiReasoner → LLM generates XML        │ │  │
│     │    │       • TemplateReasoner → fill template       │ │  │
│     │    │                                                 │ │  │
│     │    │ iv. Checkin                                    │ │  │
│     │    │     checkInWorkItem(w.id, output)              │ │  │
│     │    └────────────────────────────────────────────────┘ │  │
│     │                                                        │  │
│     │ c. Sleep (pollIntervalMs)                             │  │
│     └───────────────────────────────────────────────────────┘  │
│                                                                 │
│  3. Stop                                                        │
│     ├─ Stop discovery thread (graceful shutdown)               │
│     ├─ Stop HTTP server                                        │
│     └─ Disconnect from YAWL Engine                             │
└─────────────────────────────────────────────────────────────────┘
```

## Multi-Agent Coordination

```
┌─────────────────────────────────────────────────────────────────┐
│              YAWL Engine (Interface B)                          │
│              Enabled Work Items (REST API)                      │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     │ Poll for work items
                     │
   ┌─────────────────┼─────────────────┬──────────────────┐
   │                 │                 │                  │
   ▼                 ▼                 ▼                  ▼
┌─────────┐    ┌─────────┐     ┌─────────┐        ┌─────────┐
│ Agent 1 │    │ Agent 2 │     │ Agent 3 │        │ Agent N │
│(Ordering)│   │(Carrier)│     │(Payment)│        │ (...)   │
└────┬────┘    └────┬────┘     └────┬────┘        └────┬────┘
     │              │                │                  │
     │              │                │                  │
     │  Eligibility Reasoning (per work item)          │
     │  ┌─────────────────────────────────────────┐    │
     └─▶│ EligibilityReasoner.isEligible(w)       │◀───┘
        │ - ZAI: "Does task match my capability?" │
        │ - Static: "Is task in my mapping file?" │
        └─────────────────────────────────────────┘
                           │
                           │ If eligible: checkout → complete
                           ▼
                  Work item completed
```

## Agent-to-Agent Discovery

```
┌──────────────────────────────────────────────────────────────────┐
│                    A2A Discovery Protocol                        │
│                                                                  │
│  Agent 1                          Agent Registry (optional)      │
│    │                                     │                       │
│    │ 1. Expose agent card                │                       │
│    │    GET /.well-known/agent.json      │                       │
│    │    {                                │                       │
│    │      "name": "Ordering Agent",      │                       │
│    │      "capabilities": {"domain": ... }│                       │
│    │      "skills": [...]                │                       │
│    │    }                                │                       │
│    │                                     │                       │
│    │ 2. Register with registry (optional)│                       │
│    ├────────────────────────────────────▶│                       │
│    │    POST /agents/register            │                       │
│    │    { agentInfo }                    │                       │
│    │                                     │                       │
│  Agent 2                                 │                       │
│    │                                     │                       │
│    │ 3. Query registry for peers         │                       │
│    ├────────────────────────────────────▶│                       │
│    │    GET /agents/by-capability?domain=Ordering               │
│    │◀────────────────────────────────────│                       │
│    │    [ { agentInfo }, ... ]           │                       │
│    │                                     │                       │
│    │ 4. Direct peer discovery            │                       │
│    ├───────────────────────────────────────────────────────────▶│
│    │    GET http://agent1:8091/.well-known/agent.json           │
│    │◀───────────────────────────────────────────────────────────│
│    │    { agent card }                   │                       │
│    │                                     │                       │
│    │ 5. Capacity check (multi-agent coordination)               │
│    ├───────────────────────────────────────────────────────────▶│
│    │    GET http://agent1:8091/capacity  │                       │
│    │◀───────────────────────────────────────────────────────────│
│    │    {"available": true, "capacity": "normal"}               │
└──────────────────────────────────────────────────────────────────┘
```

## Strategy Selection Matrix

| Domain          | Eligibility Strategy | Decision Strategy      | Output Generator | Discovery Strategy |
|-----------------|----------------------|------------------------|------------------|--------------------|
| Order Fulfillment | ZAI (LLM)          | ZAI (LLM)              | XML (dynamic)    | Polling            |
| Notification    | Static Mapping       | Template               | Template         | Polling            |
| Approval Workflows | ZAI (LLM)       | Template               | XML (dynamic)    | Event-driven (future) |
| Sensor Data     | Rules Engine (future) | Rules/Template       | JSON (future)    | Webhook (future)   |

## Deployment Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                     Docker Compose Deployment                      │
│                                                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │ Agent 1      │  │ Agent 2      │  │ Agent 3      │           │
│  │ (Ordering)   │  │ (Carrier)    │  │ (Payment)    │           │
│  │ Port: 8091   │  │ Port: 8092   │  │ Port: 8093   │           │
│  │              │  │              │  │              │           │
│  │ Config:      │  │ Config:      │  │ Config:      │           │
│  │ - ZAI key    │  │ - Static map │  │ - Template   │           │
│  │ - Capability │  │ - Capability │  │ - Capability │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
│         │                 │                 │                    │
│         └─────────────────┼─────────────────┘                    │
│                           │                                      │
│  ┌────────────────────────▼────────────────────────────────────┐ │
│  │              YAWL Engine + PostgreSQL                       │ │
│  │              Port: 8080 (HTTP)                              │ │
│  └─────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │              Agent Registry (optional)                       │ │
│  │              Port: 9090 (HTTP)                               │ │
│  └──────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

## Configuration-Driven Deployment

```
config/agents/
├── orderfulfillment/
│   ├── ordering-agent.yaml       # ZAI + dynamic XML
│   ├── carrier-agent.yaml        # ZAI + dynamic XML
│   ├── freight-agent.yaml        # ZAI + dynamic XML
│   ├── payment-agent.yaml        # ZAI + dynamic XML
│   └── delivered-agent.yaml      # ZAI + dynamic XML
│
├── notification/
│   ├── email-agent.yaml          # Static mapping + template
│   ├── sms-agent.yaml            # Static mapping + template
│   └── push-agent.yaml           # Static mapping + template
│
└── approval/
    ├── manager-agent.yaml        # ZAI eligibility + template output
    └── executive-agent.yaml      # ZAI eligibility + template output

Launch agents:
  java -cp ... GenericPartyAgent --config config/agents/orderfulfillment/ordering-agent.yaml
  java -cp ... GenericPartyAgent --config config/agents/notification/email-agent.yaml

No code changes required for different domains!
```

## Abstraction Hierarchy

```
┌────────────────────────────────────────────────────────────────────┐
│ Level 4: Domain-Specific Applications                             │
│   • Order Fulfillment workflow (5 agents, ZAI reasoning)          │
│   • Notification workflow (3 agents, static mapping)              │
│   • Approval workflow (2 agents, hybrid ZAI + template)           │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ Configuration (YAML/JSON)
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Level 3: Agent Framework (Generic)                                │
│   • GenericPartyAgent (one implementation for all domains)        │
│   • AgentFactory (creates agents from config)                     │
│   • AgentConfiguration (builder pattern)                          │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ Dependency Injection
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Level 2: Strategy Layer (Pluggable)                               │
│   • DiscoveryStrategy (polling, event, webhook)                   │
│   • EligibilityReasoner (ZAI, static, rules)                      │
│   • DecisionReasoner (ZAI, template, rules)                       │
│   • OutputGenerator (XML, JSON, template)                         │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ Interface Contracts
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Level 1: Integration Layer (YAWL + External Services)            │
│   • InterfaceB_Client (YAWL engine operations)                    │
│   • ZaiService (AI reasoning)                                     │
│   • MetricsCollector (Prometheus)                                 │
│   • StructuredLogger (OpenTelemetry)                              │
└────────────────────────────────────────────────────────────────────┘
```

## Evolution Path: From Concrete to Abstract

```
┌────────────────────────────────────────────────────────────────────┐
│ Phase 1: Concrete Implementation (orderfulfillment package)       │
│   • PartyAgent (monolithic, hardcoded for Order Fulfillment)      │
│   • EligibilityWorkflow (fixed prompts, ZAI only)                 │
│   • DecisionWorkflow (fixed XML generation, orderfulfillment tasks)│
│   • Limitation: Cannot deploy for other workflow domains          │
└────────────────────────────────────────────────────────────────────┘
                              │
                              │ Abstraction (extract patterns)
                              ▼
┌────────────────────────────────────────────────────────────────────┐
│ Phase 2: Generic Framework (autonomous package)                   │
│   • GenericPartyAgent (works with ANY workflow)                   │
│   • EligibilityReasoner interface (multiple implementations)      │
│   • DecisionReasoner interface (multiple implementations)         │
│   • Benefit: Zero-code deployment for arbitrary domains           │
└────────────────────────────────────────────────────────────────────┘
```

## Key Design Patterns

1. **Strategy Pattern**: Pluggable discovery, eligibility, decision, output generation
2. **Factory Pattern**: AgentFactory centralizes agent creation and validation
3. **Builder Pattern**: AgentConfiguration uses fluent API for clean construction
4. **Dependency Injection**: GenericPartyAgent receives all strategies via constructor
5. **Interface Segregation**: Small, focused interfaces (DiscoveryStrategy, EligibilityReasoner, etc.)

## Alignment with μ(O) → A (CLAUDE.md)

```
orderfulfillment (hardcoded):
  μ_orderfulfillment(O_orderfulfillment) → A
  where O_orderfulfillment = {Approve_PO, Request_Quote, ...}
  ❌ Cannot apply to other workflows

autonomous (generic):
  μ_generic(O_any) → A
  where O_any = ANY YAWL workflow operations
  where μ = (DiscoveryStrategy, EligibilityReasoner, DecisionReasoner, OutputGenerator)
  ✅ Configuration externalizes domain logic
  ✅ Works with arbitrary workflow specifications
  ✅ Aligns with MCP (generic tools) and A2A (generic skills)
```

## References

- YAWL Manual: https://yawlfoundation.github.io/
- A2A Protocol: https://a2a-protocol.org/
- Model Context Protocol: https://modelcontextprotocol.io/
- Generic Agent Framework PRD: `.claude/plans/joyful-whistling-reddy.md`
- Thesis (Section 6): `docs/THESIS_Autonomous_Workflow_Agents.md`
