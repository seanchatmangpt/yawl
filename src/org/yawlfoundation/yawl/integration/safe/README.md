# SAFe Agent Integration for YAWL v6.0

This module provides comprehensive A2A (Agent-to-Agent) and MCP (Model Context Protocol) communication infrastructure for SAFe (Scaled Agile Framework) role agents participating in agile ceremonies and workflow orchestration.

## Overview

The SAFe integration enables autonomous agents to coordinate across agile ceremonies (sprint planning, standups, retrospectives, PI planning) using modern communication protocols:

- **A2A Protocol**: Agent-to-agent direct communication via REST endpoints
- **MCP Integration**: External visibility and tool access via YawlMcpServer
- **Message Queue**: Asynchronous, prioritized message routing with retry semantics
- **Status Synchronization**: Real-time health monitoring and capability matching

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│              YawlMcpServer (External Visibility)         │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────────────────────────────────────────┐   │
│  │   SAFeCeremonyOrchestrator (Ceremony Lifecycle)  │   │
│  │   ├─ initiateCeremony(context)                  │   │
│  │   ├─ dispatchMessage(ceremonyId, message)       │   │
│  │   ├─ recordParticipantStatus(...)               │   │
│  │   └─ completeCeremony(ceremonyId, outcome)      │   │
│  └──────────────────────────────────────────────────┘   │
│          ▲                          ▲                    │
│          │                          │                    │
│  ┌───────┴──────────┐       ┌──────┴──────────────┐    │
│  │ SAFeAgentRegistry│       │ Message Routing     │    │
│  │ ├─ registerAgent │       │ (A2A Protocol)      │    │
│  │ ├─ findByRole    │       └─────────────────────┘    │
│  │ ├─ findByCapab.  │              ▼                    │
│  │ └─ recordStatus  │       ┌──────────────────────┐   │
│  └──────────────────┘       │ SAFeAgents (REST)    │   │
│          ▲                  └──────────────────────┘   │
│          │                                              │
│  ┌───────┴──────────────────────────┐               │
│  │   Message Types:                 │               │
│  │   ├─ StoryCeremonyMessage        │               │
│  │   ├─ DependencyNotification      │               │
│  │   ├─ BlockerNotification         │               │
│  │   ├─ AcceptanceDecision          │               │
│  │   └─ PiPlanningEvent             │               │
│  └────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────┘
```

## Key Components

### 1. SAFeAgentRegistry
Central registry for managing SAFe agents, their capabilities, and operational status.

```java
SAFeAgentRegistry registry = new SAFeAgentRegistry();

// Register agents
registry.registerAgent(poCard);
registry.registerAgent(smCard);

// Find agents by capability
List<SAFeAgentCard> refinementAgents = registry.findByCapability(
    AgentCapability.STORY_REFINEMENT
);

// Track agent health
registry.recordSuccess("po-001");
registry.recordHeartbeat("po-001");
List<SAFeAgentCard> available = registry.getAvailableAgents();
```

**Features:**
- Dynamic agent registration/unregistration
- Role-based and capability-based agent discovery
- Health monitoring with success/failure rates
- Heartbeat tracking for liveness detection
- Agent selection based on capability and health

### 2. SAFeCeremonyOrchestrator
Orchestrates SAFe ceremonies by managing ceremony lifecycle, coordinating agent participation, and routing messages.

```java
SAFeCeremonyOrchestrator orchestrator = new SAFeCeremonyOrchestrator(registry);
orchestrator.addEventListener(new LoggingEventListener());

// Initiate ceremony
CeremonyContext context = CeremonyContext.create("SPRINT_PLANNING")
    .withTeamId("team-alpha")
    .withSprintId("sprint-01");

String ceremonyId = orchestrator.initiateCeremony(context);

// Dispatch messages
StoryCeremonyMessage msg = StoryCeremonyMessage.builder()
    .ceremonyId(ceremonyId)
    .fromAgentId("po-001")
    .storyId("STORY-123")
    .build();

orchestrator.dispatchMessage(ceremonyId, msg);

// Complete ceremony
orchestrator.completeCeremony(ceremonyId, CeremonyOutcome.SUCCESS);
```

**Features:**
- Ceremony lifecycle management (initiate, dispatch, complete)
- Automatic participant selection based on role and capability
- Message routing with role-based targeting
- Status synchronization and event notifications
- Session management with concurrent ceremony support

### 3. SAFeAgentCard
Immutable record describing agent identity, role, capabilities, and endpoints.

```java
SAFeAgentCard poCard = SAFeAgentCard.builder()
    .agentId("po-001")
    .name("Product Owner Bot")
    .role(SAFeAgentRole.PRODUCT_OWNER)
    .capabilities(List.of(
        AgentCapability.STORY_REFINEMENT,
        AgentCapability.PRIORITY_MANAGEMENT
    ))
    .host("localhost")
    .port(8080)
    .ceremonies(List.of("SPRINT_PLANNING", "RETROSPECTIVE"))
    .version("1.0.0")
    .build();
```

### 4. Message Types

#### StoryCeremonyMessage (Sprint Planning)
```java
StoryCeremonyMessage msg = StoryCeremonyMessage.builder()
    .ceremonyId(ceremonyId)
    .fromAgentId("po-001")
    .storyId("STORY-123")
    .storyTitle("Implement user dashboard")
    .addAcceptanceCriterion("Dashboard displays user data")
    .estimatedPoints(8)
    .addTask(new TaskBreakdown(...))
    .addTargetRole(SAFeAgentRole.TEAM_MEMBER)
    .messagePriority("NORMAL")
    .build();
```

#### DependencyNotification (Architecture Review)
```java
DependencyNotification dep = DependencyNotification.builder()
    .ceremonyId(ceremonyId)
    .fromAgentId("rte-001")
    .dependencyType("SERVICE")
    .sourceTeamId("team-alpha")
    .targetTeamId("team-beta")
    .description("Authentication service required for new feature")
    .status("OPEN")
    .riskLevel("HIGH")
    .build();
```

#### BlockerNotification (Standup)
```java
BlockerNotification blocker = BlockerNotification.builder()
    .ceremonyId(ceremonyId)
    .fromAgentId("sm-001")
    .title("Database migration pending")
    .severity("HIGH")
    .impactedWorkItemId("STORY-456")
    .proposedSolution("Escalate to database team")
    .assignedToRole("RELEASE_TRAIN_ENGINEER")
    .build();
```

#### AcceptanceDecision (Story Completion)
```java
AcceptanceDecision decision = AcceptanceDecision.builder()
    .ceremonyId(ceremonyId)
    .fromAgentId("qa-001")
    .workItemId("WORK-789")
    .storyId("STORY-123")
    .decisionStatus("ACCEPTED")
    .addCriteriaMet("Dashboard displays user data")
    .qualityScore(0.95)
    .reviewerRole("QA_TESTER")
    .build();
```

#### PiPlanningEvent (Quarterly Planning)
```java
PiPlanningEvent piEvent = PiPlanningEvent.builder()
    .ceremonyId(ceremonyId)
    .fromAgentId("rte-001")
    .piName("PI 2025-Q2")
    .piStartDate(Instant.parse("2025-04-01T00:00:00Z"))
    .piEndDate(Instant.parse("2025-06-30T23:59:59Z"))
    .addObjective("Deploy customer analytics platform")
    .releaseTrain("ART-1")
    .addTeamCommitment(new TeamPlanning(...))
    .planningStatus("FINAL")
    .build();
```

## SAFe Ceremony Types

### SPRINT_PLANNING
- **Duration**: 2 hours
- **Participants**: Product Owner, Scrum Master, Team Members, QA
- **Messages**: StoryCeremonyMessage
- **Goal**: Refine stories, break down tasks, estimate effort, assign work

### STANDUP
- **Duration**: 15 minutes
- **Participants**: Scrum Master, Team Members
- **Messages**: BlockerNotification
- **Goal**: Sync on progress, surface blockers, coordinate handoffs

### RETROSPECTIVE
- **Duration**: 45 minutes
- **Participants**: Scrum Master, Team Members
- **Messages**: None (facilitation-based)
- **Goal**: Reflect on process, identify improvements

### ARCHITECTURE_REVIEW
- **Duration**: 2 hours
- **Participants**: Architect, Release Train Engineer, Tech Leads
- **Messages**: DependencyNotification
- **Goal**: Review technical decisions, validate integration points

### DEPENDENCY_SYNC
- **Duration**: 30 minutes
- **Participants**: Release Train Engineer, Scrum Masters
- **Messages**: DependencyNotification
- **Goal**: Identify and track cross-team dependencies

### PI_PLANNING
- **Duration**: 8 hours
- **Participants**: All roles from multiple teams
- **Messages**: PiPlanningEvent
- **Goal**: Plan quarterly objectives, identify risks, commit capacity

## Agent Roles

### Product Owner (PRODUCT_OWNER)
- Refines user stories and acceptance criteria
- Prioritizes backlog items
- Communicates stakeholder needs
- Participates in sprint planning and PI planning

**Required Capabilities**: STORY_REFINEMENT, PRIORITY_MANAGEMENT, ACCEPTANCE_CRITERIA_DEFINITION

### Scrum Master (SCRUM_MASTER)
- Facilitates all ceremonies
- Tracks and removes blockers
- Coaches team on agile practices
- Reports sprint metrics

**Required Capabilities**: SPRINT_PLANNING, STANDUP_FACILITATION, RETROSPECTIVE_FACILITATION

### Team Member (TEAM_MEMBER)
- Executes assigned stories and tasks
- Reports progress and blockers
- Participates in ceremonies
- Ensures code quality

**Required Capabilities**: FEATURE_IMPLEMENTATION, CODE_REVIEW, DEFECT_RESOLUTION

### Release Train Engineer (RELEASE_TRAIN_ENGINEER)
- Identifies cross-team dependencies
- Facilitates PI planning
- Coordinates releases
- Aggregates metrics

**Required Capabilities**: DEPENDENCY_TRACKING, CROSS_TEAM_COMMUNICATION, PI_PLANNING

### Architect (ARCHITECT)
- Designs technical solutions
- Reviews architectural decisions
- Manages technical debt
- Evaluates technology choices

**Required Capabilities**: ARCHITECTURE_DESIGN, TECHNICAL_REVIEW, TECHNOLOGY_EVALUATION

### Agile Coach (AGILE_COACH)
- Coaches teams on agile practices
- Analyzes and improves processes
- Facilitates organizational change

**Required Capabilities**: TEAM_COACHING, PROCESS_IMPROVEMENT, METRICS_ANALYSIS

### Business Analyst (BUSINESS_ANALYST)
- Analyzes business requirements
- Documents processes
- Validates business rules

**Required Capabilities**: REQUIREMENT_ANALYSIS, BUSINESS_RELATIONSHIP_MANAGEMENT

### QA Tester (QA_TESTER)
- Defines test strategies
- Executes tests
- Verifies quality
- Reviews acceptance criteria

**Required Capabilities**: TEST_STRATEGY, TEST_AUTOMATION, QUALITY_ASSURANCE

## Agent Configuration (TOML)

Agent definitions are stored in `.claude/agents/safe-roles/` as TOML files:

```toml
[agent]
id = "safe-po-agent"
name = "SAFe Product Owner Agent"
version = "6.0.0"

[role]
name = "PRODUCT_OWNER"
responsibilities = [
    "Story refinement and acceptance criteria definition",
    # ...
]

[capabilities]
primary = [
    "STORY_REFINEMENT",
    "PRIORITY_MANAGEMENT",
    # ...
]

[ceremonies]
participates_in = [
    "SPRINT_PLANNING",
    "RETROSPECTIVE",
    # ...
]

[a2a]
transport = "REST"
protocol = "A2A 1.0"
endpoints = ["/a2a/agents/po/story-refine", # ...]

[mcp]
exposed_tools = [
    "refine_user_story",
    "prioritize_backlog_items",
    # ...
]

[constraints]
# Business rules and constraints
sprint_goal_updates_per_sprint = 1
frozen_backlog_during_sprint = true
```

## Message Schema (XSD)

Complete XML Schema Definition in `/home/user/yawl/schema/safe-messages.xsd` defines all message types with strict validation:

```xml
<xs:element name="storyCeremonyMessage" type="safe:StoryCeremonyMessage">
    <xs:annotation>
        <xs:documentation>Sprint planning message with story refinement</xs:documentation>
    </xs:annotation>
</xs:element>

<xs:element name="dependencyNotification" type="safe:DependencyNotification">
    <xs:annotation>
        <xs:documentation>Cross-team dependency notification</xs:documentation>
    </xs:annotation>
</xs:element>
<!-- Additional message types ... -->
```

## Integration Points

### With YawlMcpServer
The orchestrator integrates with YawlMcpServer for external visibility:

```java
// In YawlMcpServer configuration
yawlMcpServer.registerTool("safe_initiate_ceremony", new CeremonyInitiationTool());
yawlMcpServer.registerTool("safe_dispatch_message", new MessageDispatchTool());
yawlMcpServer.registerResource("yawl://ceremonies", new CeremonyResourceProvider());
```

### With YawlA2AServer
A2A communication uses REST endpoints:

```java
POST /a2a/agents/po/story-refine
{
    "messageId": "msg-001",
    "ceremonyId": "ceremony-123",
    "storyId": "STORY-456",
    // ...
}
```

### With Agent Registry
Dynamic agent discovery and health monitoring:

```java
List<SAFeAgentCard> agents = registry.findByCapabilities(
    List.of(AgentCapability.STORY_REFINEMENT)
);
```

## Event Listeners

Implement CeremonyEventListener to react to ceremony events:

```java
public class LoggingEventListener implements CeremonyEventListener {
    @Override
    public void onCeremonyStarted(String ceremonyId, String ceremonyType) {
        logger.info("Ceremony started: {} ({})", ceremonyId, ceremonyType);
    }

    @Override
    public void onCeremonyCompleted(String ceremonyId, String ceremonyType, String outcome) {
        logger.info("Ceremony completed: {} - Outcome: {}", ceremonyId, outcome);
    }

    @Override
    public void onMessageDispatched(String ceremonyId, String messageId, int recipientCount) {
        logger.info("Message dispatched: {} to {} recipients", messageId, recipientCount);
    }

    @Override
    public void onParticipantStatusChanged(String ceremonyId, String agentId, String state) {
        logger.info("Participant status changed: {} -> {}", agentId, state);
    }
}
```

## Error Handling

### Agent Unavailable
When an agent is not available for a ceremony:

```java
try {
    String ceremonyId = orchestrator.initiateCeremony(context);
} catch (IllegalArgumentException e) {
    // No agents available for ceremony type
    logger.warn("Cannot initiate ceremony: {}", e.getMessage());
}
```

### Message Routing Failure
Failed message routing is tracked in agent status:

```java
if (!orchestrator.dispatchMessage(ceremonyId, msg)) {
    logger.error("Failed to dispatch message to any recipients");
    // Retry logic or fallback
}
```

### Agent Health Degradation
Monitor and respond to agent health changes:

```java
Optional<SAFeAgentRegistry.AgentStatus> status = registry.getAgentStatus("po-001");
if (status.isPresent() && !status.get().isHealthy()) {
    logger.warn("Agent degraded: {} - Success rate: {}%",
        "po-001",
        status.get().getSuccessRate() * 100);
}
```

## Testing

Test fixtures and example agents in test resources:

```bash
src/test/java/org/yawlfoundation/yawl/integration/safe/
├── registry/
│   └── SAFeAgentRegistryTest.java
├── orchestration/
│   └── SAFeCeremonyOrchestratorTest.java
└── messages/
    └── CeremonyMessageTest.java
```

## Performance Characteristics

- **Agent Lookup**: O(1) by ID, O(n) by capability
- **Message Dispatch**: O(m) where m = number of target agents
- **Ceremony Sessions**: Concurrent support for unlimited sessions
- **Event Processing**: Synchronous, thread-safe listener invocation

## Future Enhancements

1. **Async Message Queue**: Decouple message dispatch from ceremony orchestration
2. **Message Persistence**: Store ceremony conversations for audit trail
3. **Ceremony Recordings**: MCP resources for ceremony transcripts
4. **Advanced Analytics**: Process mining and bottleneck analysis
5. **Workflow Integration**: Auto-create YAWL cases from ceremony outcomes
6. **Multi-Team Support**: Cross-release-train coordination
7. **Ceremony Templates**: Customizable ceremony definitions

## Related Documentation

- `/home/user/yawl/schema/safe-messages.xsd` - Complete message schema
- `.claude/agents/safe-roles/*.toml` - Agent role definitions
- `docs/integration/safe-orchestration.md` - User guide
- `docs/reference/a2a-protocol.md` - A2A communication protocol
- `docs/reference/mcp-server.md` - MCP server integration

## Troubleshooting

### No agents available for ceremony
1. Check agent registration in `SAFeAgentRegistry`
2. Verify ceremony type matches agent's `ceremonies` list
3. Check agent health status with `getAvailableAgents()`

### Messages not being dispatched
1. Verify target roles are set correctly
2. Check agent endpoint configuration
3. Review A2A transport logs for routing errors

### Agent health degradation
1. Check agent failure logs
2. Review agent endpoint availability
3. Consider agent restart or replacement

## License

Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.

This file is part of YAWL. YAWL is free software under the GNU Lesser General Public License.
