# SAFe Participant Agents for YAWL v6

## Overview

This package implements autonomous agents for the Scaled Agile Framework (SAFe) in YAWL v6. Each agent extends `GenericPartyAgent` and autonomously participates in SAFe ceremonies and workflows.

## Architecture

### Core Components

1. **GenericPartyAgent (Base Class)**
   - Extends from `org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent`
   - Provides:
     - Polling-based work item discovery via pluggable `DiscoveryStrategy`
     - Eligibility reasoning via `EligibilityReasoner`
     - Output decision production via `DecisionReasoner`
     - Virtual thread-based discovery loop
     - HTTP endpoints for health/capacity checks
     - Graceful shutdown with resource cleanup

2. **Data Records (Java 25)**
   - `UserStory` - Immutable representation of a SAFe user story
   - `SAFeSprint` - Immutable sprint state with velocity calculation
   - `AgentDecision` - Immutable decision record for traceability and audit

### The Five SAFe Agents

```
ProductOwnerAgent (port base + 0)
├── Manages backlog prioritization
├── Reviews and accepts stories
└── Analyzes dependencies

ScrumMasterAgent (port base + 1)
├── Facilitates daily standups
├── Identifies and removes blockers
├── Tracks sprint velocity
└── Manages impediments

DeveloperAgent (port base + 2)
├── Executes assigned stories
├── Reports progress updates
├── Conducts code reviews
└── Runs unit tests

SystemArchitectAgent (port base + 3)
├── Designs system architecture
├── Manages cross-team dependencies
├── Evaluates technical feasibility
└── Conducts technical reviews

ReleaseTrainEngineerAgent (port base + 4)
├── Orchestrates PI planning
├── Coordinates multi-team releases
├── Plans deployments
└── Assesses release readiness
```

## Agent Lifecycle

### State Machine

Each agent follows this state transition:
```
CREATED → INITIALIZING → DISCOVERING → [work item processing loop] → STOPPING → STOPPED
```

### Discovery Cycle

1. **Discovery Phase**: Agent polls engine for eligible work items
2. **Eligibility Reasoning**: Filter items matching agent capabilities
3. **Decision Production**: Generate decision/output for eligible items
4. **Checkout/Checkin**: Update engine with output
5. **Loop**: Repeat at `pollIntervalMs` interval

## Task Categories

### Product Owner Tasks
- `BacklogPrioritization` - Rank stories by business value
- `StoryAcceptance` - Accept/reject completed stories
- `DependencyAnalysis` - Resolve story dependencies

### Scrum Master Tasks
- `StandupFacilitation` - Run daily standup meeting
- `BlockerRemoval` - Remove or escalate blockers
- `VelocityTracking` - Report sprint velocity metrics
- `ImpedimentManagement` - Log and track impediments

### Developer Tasks
- `StoryExecution` - Execute assigned user stories
- `ProgressReporting` - Report story progress
- `CodeReview` - Conduct peer code reviews
- `UnitTesting` - Run unit tests and report results

### System Architect Tasks
- `ArchitectureDesign` - Design system solutions
- `DependencyManagement` - Manage cross-team dependencies
- `FeasibilityEvaluation` - Evaluate technical feasibility
- `TechnicalReview` - Review architecture decisions

### Release Train Engineer Tasks
- `PIPlanning` - Orchestrate PI planning sessions
- `ReleaseCoordination` - Coordinate multi-team releases
- `DeploymentPlanning` - Plan staged deployments
- `ReleaseReadiness` - Assess release quality gates

## Decision Model

### AgentDecision Record

Each agent decision captures:
- **id**: Unique decision identifier
- **decisionType**: Type of decision (ACCEPT, REJECT, PRIORITIZE, etc.)
- **agentId**: Agent making decision
- **workItemId**: Related work item ID
- **outcome**: Decision outcome (e.g., "STORY_ACCEPTED")
- **rationale**: Explanation for decision
- **evidence**: Supporting metrics/data
- **timestamp**: When decision was made

### Decision Storage

Decisions are:
1. Logged to agent's in-memory decision log
2. Serialized to XML in work item output
3. Persisted via YAWL engine for audit trail

## Usage

### Single Agent Creation

```java
// Create a Product Owner agent on port 8090
ProductOwnerAgent agent = ProductOwnerAgent.create(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    8090);

// Start the agent
agent.start();

// Agent queries /.well-known/agent.json at port 8090
// Starts polling for work items every 5 seconds
```

### Complete Team Creation

```java
// Create registry
SAFeAgentRegistry registry = new SAFeAgentRegistry(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    8090);

// Create all 5 agents
registry.createCompleteTeam();

// Start all agents
registry.startAll();

// Check health
Map<String, Boolean> status = registry.getHealthStatus();
// {ProductOwner: true, ScrumMaster: true, ...}
```

### Bootstrap from Main

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
export SAFE_BASE_PORT=8090

java org.yawlfoundation.yawl.safe.agents.SAFeAgentBootstrap
```

## Configuration

### Strategies

Each agent is configured with three pluggable strategies:

1. **DiscoveryStrategy**: How to find eligible work items
   - Default: Filters work items by task name
   - Custom: Implement `DiscoveryStrategy` interface

2. **EligibilityReasoner**: Validate work item eligibility
   - Default: XML data structure validation
   - Custom: Implement `EligibilityReasoner` interface

3. **DecisionReasoner**: Generate output decisions
   - Default: Domain-specific decision logic
   - Custom: Implement `DecisionReasoner` interface

### Customization Example

```java
// Custom eligibility reasoner
EligibilityReasoner customReasoner = workItem -> {
    String data = workItem.getDataString();
    // Custom logic to determine eligibility
    return data != null && data.contains("<Priority>HIGH</Priority>");
};

// Build agent with custom strategy
AgentConfiguration config = AgentConfiguration.builder(
        "custom-agent",
        engineUrl,
        username,
        password)
    .capability(capability)
    .discoveryStrategy(defaultDiscoveryStrategy)
    .eligibilityReasoner(customReasoner)
    .decisionReasoner(defaultDecisionReasoner)
    .port(8090)
    .build();

ProductOwnerAgent agent = new ProductOwnerAgent(config);
```

## HTTP Endpoints

Each agent exposes three HTTP endpoints:

### 1. Agent Card Endpoint

```
GET /.well-known/agent.json

Response:
{
  "name": "ProductOwner Agent",
  "description": "Autonomous agent for ProductOwner...",
  "version": "6.0.0",
  "capabilities": {"domain": "ProductOwner"},
  "skills": [
    {
      "id": "complete_work_item",
      "name": "Complete Work Item",
      "description": "Discover and complete workflow tasks..."
    }
  ]
}
```

### 2. Health Check Endpoint

```
GET /health

Response:
{
  "status": "ok",
  "agent": "ProductOwner",
  "lifecycle": "DISCOVERING"
}
```

### 3. Capacity Endpoint

```
GET /capacity

Response:
{
  "domain": "ProductOwner",
  "available": true,
  "capacity": "normal"
}
```

## Decision Logging

### Access Decision Log

```java
ProductOwnerAgent agent = ProductOwnerAgent.create(...);

// After agent has processed some work items...
Map<String, AgentDecision> decisions = agent.getDecisionLog();

for (AgentDecision decision : decisions.values()) {
    logger.info("Decision: {} - {}",
        decision.decisionType(),
        decision.outcome());
    logger.info("  Rationale: {}", decision.rationale());
    logger.info("  Evidence: {}", decision.evidence());
}
```

### Clear Decision Log

```java
agent.clearDecisionLog(); // Typically after persistence to database
```

## Work Item Data Format

### Example: Story Acceptance Work Item

```xml
<Data>
  <Story>
    <ID>STORY-123</ID>
    <Title>Add payment gateway integration</Title>
    <AcceptanceCriteria>
      <Criterion>
        <Text>Payment processing works with credit cards</Text>
        <Status>PASSED</Status>
      </Criterion>
      <Criterion>
        <Text>Transactions logged for audit trail</Text>
        <Status>PASSED</Status>
      </Criterion>
    </AcceptanceCriteria>
  </Story>
</Data>
```

### Output: Agent Decision

```xml
<Decision>
  <ID>po-accept-1234567890</ID>
  <Type>ACCEPT</Type>
  <Agent>ProductOwnerAgent</Agent>
  <WorkItem>STORY-123</WorkItem>
  <Outcome>STORY_ACCEPTED</Outcome>
  <Rationale>All acceptance criteria verified and met by development team</Rationale>
  <Timestamp>2026-02-28T14:32:15Z</Timestamp>
</Decision>
```

## Virtual Threading

All agents use Java 25 virtual threads for:
- Discovery loop: `Thread.ofVirtual().name("discovery-" + agentName)`
- HTTP server executor: `newVirtualThreadPerTaskExecutor()`

Benefits:
- Millions of concurrent work items
- No thread pool tuning needed
- Automatic resource cleanup

## Graceful Shutdown

Each agent supports graceful shutdown:

```java
// Stop the agent
agent.stop();

// Cleanup:
// - Halts discovery loop
// - Closes HTTP server
// - Disconnects from engine
// - Transitions to STOPPED state
```

### Shutdown Hook

The bootstrap automatically registers shutdown hook:

```java
// Intercepts Ctrl+C
// Calls registry.stopAll()
// Logs shutdown completion
```

## Testing

### Integration Test Pattern

```java
@Test
void testProductOwnerStoryAcceptance() throws IOException {
    // Create agent
    ProductOwnerAgent agent = ProductOwnerAgent.create(
        "http://localhost:8080/yawl",
        "admin",
        "YAWL",
        8090);

    // Start agent
    agent.start();

    try {
        // Submit test work item
        // Verify decision output
        // Check decision log
    } finally {
        agent.stop();
    }
}
```

## Error Handling

Agents handle errors gracefully:

1. **Discovery Error**: Log and continue polling
2. **Eligibility Check Error**: Skip work item, continue
3. **Processing Error**: Attempt handoff to capable agent
4. **Handoff Failure**: Log error, skip work item
5. **Engine Disconnect**: Log error, continue polling

## Performance Characteristics

- **Discovery Cycle**: Configurable (default 5 seconds)
- **Work Item Processing**: Sequential per agent
- **Memory**: ~50MB per agent + decision log
- **CPU**: Minimal when idle, scales with work item processing
- **Virtual Threads**: Can handle 10,000+ concurrent operations

## Logging

All agents use Log4j2:

```
org.yawlfoundation.yawl.safe.agents.ProductOwnerAgent
org.yawlfoundation.yawl.safe.agents.ScrumMasterAgent
org.yawlfoundation.yawl.safe.agents.DeveloperAgent
org.yawlfoundation.yawl.safe.agents.SystemArchitectAgent
org.yawlfoundation.yawl.safe.agents.ReleaseTrainEngineerAgent
```

## Files

| File | Purpose |
|------|---------|
| `ProductOwnerAgent.java` | Backlog, prioritization, acceptance |
| `ScrumMasterAgent.java` | Ceremonies, blockers, impediments |
| `DeveloperAgent.java` | Story execution, progress, code review |
| `SystemArchitectAgent.java` | Architecture design, dependencies |
| `ReleaseTrainEngineerAgent.java` | PI planning, releases, readiness |
| `SAFeAgentRegistry.java` | Agent lifecycle and registration |
| `SAFeAgentBootstrap.java` | Production bootstrap/startup |
| `UserStory.java` | Immutable story data record |
| `SAFeSprint.java` | Immutable sprint data record |
| `AgentDecision.java` | Immutable decision record |
| `package-info.java` | Package documentation |

## Extension Points

### Custom Task Type

1. Add task name filter in `DiscoveryStrategy`
2. Implement validation in `EligibilityReasoner`
3. Add decision logic in `DecisionReasoner`
4. Return XML decision output

### Custom Strategy

Extend `DecisionReasoner`, `EligibilityReasoner`, or `DiscoveryStrategy` and inject via `AgentConfiguration.Builder`.

## Best Practices

1. **Use Registry for Teams**: SAFeAgentRegistry handles lifecycle
2. **Set Appropriate Poll Interval**: Balance latency vs CPU (5-10 seconds typical)
3. **Monitor Decision Log**: Persist for audit trail
4. **Handle Handoffs**: Implement agent network for cross-domain work
5. **Test with Real Engine**: Integration tests with actual YAWL engine
6. **Use Environment Variables**: Bootstrap with standard config
7. **Log Decisions**: Enable decision traceability

## Troubleshooting

### Agent Not Processing Work Items

1. Check agent lifecycle: `agent.getLifecycle().isActive()`
2. Verify work item task names match discovery strategy
3. Check eligibility reasoner logic
4. Review decision reasoner output

### HTTP Endpoints Not Responding

1. Check port is not in use
2. Verify firewall allows connections
3. Check agent is in DISCOVERING state
4. Review HTTP server startup logs

### High Memory Usage

1. Clear decision log periodically
2. Reduce poll interval to complete items faster
3. Profile with JFR: `jcmd <pid> JFR.start duration=60s filename=profile.jfr`

---

**Version**: YAWL 6.0
**License**: GNU Lesser General Public License v2+
**Contact**: YAWL Foundation (www.yawlfoundation.org)
