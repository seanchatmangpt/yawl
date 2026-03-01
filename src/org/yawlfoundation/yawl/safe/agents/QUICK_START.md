# SAFe Agents Quick Start Guide

## 30-Second Overview

Five autonomous agents for SAFe workflow management in YAWL v6:

1. **ProductOwnerAgent** - Backlog prioritization & story acceptance
2. **ScrumMasterAgent** - Ceremony facilitation & blocker removal
3. **DeveloperAgent** - Story execution & progress reporting
4. **SystemArchitectAgent** - Architecture design & dependency management
5. **ReleaseTrainEngineerAgent** - PI planning & release orchestration

All agents extend `GenericPartyAgent` and use Java 25 virtual threads.

## 5-Minute Setup

### Start Complete Team

```bash
export YAWL_ENGINE_URL=http://localhost:8080/yawl
export YAWL_USERNAME=admin
export YAWL_PASSWORD=YAWL
export SAFE_BASE_PORT=8090

java -cp yawl-core.jar:yawl-engine.jar \
  org.yawlfoundation.yawl.safe.agents.SAFeAgentBootstrap
```

Agents will start on ports 8090-8094.

### Or Start Programmatically

```java
SAFeAgentRegistry registry = new SAFeAgentRegistry(
    "http://localhost:8080/yawl",
    "admin", "YAWL", 8090);

registry.createCompleteTeam();
registry.startAll();

// Agent is running and discovering work items
```

## Agent Ports

| Agent | Port | Endpoint |
|-------|------|----------|
| ProductOwner | 8090 | `http://localhost:8090/.well-known/agent.json` |
| ScrumMaster | 8091 | `http://localhost:8091/.well-known/agent.json` |
| Developer | 8092 | `http://localhost:8092/.well-known/agent.json` |
| SystemArchitect | 8093 | `http://localhost:8093/.well-known/agent.json` |
| ReleaseTrainEngineer | 8094 | `http://localhost:8094/.well-known/agent.json` |

## Work Item Integration

### Submit a Work Item

Example: Story Acceptance task

```xml
<Data>
  <Story>
    <ID>STORY-123</ID>
    <Title>Add payment gateway</Title>
    <AcceptanceCriteria>
      <Criterion>
        <Text>Accepts credit cards</Text>
        <Status>PASSED</Status>
      </Criterion>
    </AcceptanceCriteria>
  </Story>
</Data>
```

### Agent Output

Agent returns XML decision:

```xml
<Decision>
  <ID>po-accept-1234567890</ID>
  <Type>ACCEPT</Type>
  <Agent>ProductOwnerAgent</Agent>
  <WorkItem>STORY-123</WorkItem>
  <Outcome>STORY_ACCEPTED</Outcome>
  <Rationale>All acceptance criteria verified and met</Rationale>
  <Timestamp>2026-02-28T14:32:15Z</Timestamp>
</Decision>
```

## Check Agent Health

```bash
# ProductOwner health
curl http://localhost:8090/health | jq .

# Capacity
curl http://localhost:8090/capacity | jq .

# Agent card
curl http://localhost:8090/.well-known/agent.json | jq .
```

## Task Types

### ProductOwner Tasks
- `BacklogPrioritization` - Rank stories by value
- `StoryAcceptance` - Accept/reject stories
- `DependencyAnalysis` - Resolve dependencies

### ScrumMaster Tasks
- `StandupFacilitation` - Run standup
- `BlockerRemoval` - Remove/escalate blockers
- `VelocityTracking` - Report velocity
- `ImpedimentManagement` - Track impediments

### Developer Tasks
- `StoryExecution` - Execute stories
- `ProgressReporting` - Report progress
- `CodeReview` - Review code
- `UnitTesting` - Run tests

### SystemArchitect Tasks
- `ArchitectureDesign` - Design systems
- `DependencyManagement` - Manage dependencies
- `FeasibilityEvaluation` - Evaluate feasibility
- `TechnicalReview` - Review design

### ReleaseTrainEngineer Tasks
- `PIPlanning` - Orchestrate PI planning
- `ReleaseCoordination` - Coordinate releases
- `DeploymentPlanning` - Plan deployments
- `ReleaseReadiness` - Assess readiness

## Customize Single Agent

```java
// Create ProductOwner on port 8090
ProductOwnerAgent agent = ProductOwnerAgent.create(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    8090);

agent.start();

// Agent is discovering work items
// Check its health: curl http://localhost:8090/health
```

## Stop Agents

```bash
# From command line: Ctrl+C (triggers shutdown hook)
# Or programmatically:
registry.stopAll();
```

## Check Decision Log

```java
ProductOwnerAgent agent = ProductOwnerAgent.create(...);
agent.start();

// After agent processes work items...
Map<String, AgentDecision> decisions = agent.getDecisionLog();

decisions.values().forEach(d ->
    System.out.println(d.outcome() + ": " + d.rationale())
);
```

## Discovery Flow

1. Agent polls engine every 5 seconds (configurable)
2. Filters work items by task name (ProductOwner tasks, etc.)
3. Validates eligibility (XML structure, data presence)
4. Generates decision using domain logic
5. Checks out work item
6. Submits XML decision output
7. Logs decision to audit trail
8. Repeats

## Key URLs

| Endpoint | Response | Example |
|----------|----------|---------|
| `/:port/.well-known/agent.json` | Agent card | `http://localhost:8090/.well-known/agent.json` |
| `/:port/health` | Health status | `http://localhost:8090/health` |
| `/:port/capacity` | Capacity info | `http://localhost:8090/capacity` |

## Environment Variables

```bash
YAWL_ENGINE_URL    # YAWL engine URL (default: http://localhost:8080/yawl)
YAWL_USERNAME      # Engine username (default: admin)
YAWL_PASSWORD      # Engine password (default: YAWL)
SAFE_BASE_PORT     # Base port for agents (default: 8090)
```

## Decision Types

Each agent produces specific decision types:

```
ProductOwner:     ACCEPT, REJECT, PRIORITIZE, ANALYZE_DEPENDENCIES
ScrumMaster:      FACILITATE_STANDUP, REMOVE_BLOCKER, TRACK_VELOCITY, MANAGE_IMPEDIMENTS
Developer:        EXECUTE_STORY, REPORT_PROGRESS, CONDUCT_CODE_REVIEW, RUN_UNIT_TESTS
SystemArchitect:  DESIGN_ARCHITECTURE, MANAGE_DEPENDENCIES, EVALUATE_FEASIBILITY, CONDUCT_TECHNICAL_REVIEW
ReleaseTrainEng:  ORCHESTRATE_PI_PLANNING, COORDINATE_RELEASE, PLAN_DEPLOYMENT, ASSESS_RELEASE_READINESS
```

## Performance

| Metric | Value |
|--------|-------|
| Memory per agent | ~50MB |
| Poll interval | 5000ms (configurable) |
| Processing latency | <100ms typical |
| HTTP response time | <50ms typical |
| Concurrent work items | Unlimited (virtual threads) |

## Files

| File | Purpose |
|------|---------|
| `ProductOwnerAgent.java` | 385 lines |
| `ScrumMasterAgent.java` | 384 lines |
| `DeveloperAgent.java` | 413 lines |
| `SystemArchitectAgent.java` | 413 lines |
| `ReleaseTrainEngineerAgent.java` | 410 lines |
| `SAFeAgentRegistry.java` | 190 lines |
| `SAFeAgentBootstrap.java` | 120 lines |
| `SAFE_AGENTS_README.md` | Full documentation |
| `SAFE_AGENTS_IMPLEMENTATION_GUIDE.md` | Deep dive guide |
| `QUICK_START.md` | This file |

## Troubleshooting

### Agent not discovering work items
- Check task name matches agent filter
- Verify work item status is "enabled"
- Check `curl http://localhost:8090/health`

### Port already in use
- Change SAFE_BASE_PORT (default 8090)
- Or use: `lsof -i :8090` to find conflicting process

### High memory
- Clear decision log: `agent.clearDecisionLog()`
- Reduce poll interval for faster processing

## Next Steps

1. Read `SAFE_AGENTS_README.md` for detailed documentation
2. Read `SAFE_AGENTS_IMPLEMENTATION_GUIDE.md` for architecture
3. Write integration tests using real YAWL engine
4. Deploy to production with environment variables
5. Monitor agent decisions via HTTP endpoints

## Reference

- **Base Class**: `GenericPartyAgent`
- **Config**: `AgentConfiguration`
- **Strategies**: `DiscoveryStrategy`, `EligibilityReasoner`, `DecisionReasoner`
- **Data**: `UserStory`, `SAFeSprint`, `AgentDecision`
- **Registry**: `SAFeAgentRegistry`

---

Version: 6.0.0 | YAWL Foundation | 2026-02-28
