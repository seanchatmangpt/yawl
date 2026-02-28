# SAFe Agents Package Index

## Package Structure

```
org.yawlfoundation.yawl.safe.agents
├── Core Agent Classes (5)
│   ├── ProductOwnerAgent.java
│   ├── ScrumMasterAgent.java
│   ├── DeveloperAgent.java
│   ├── SystemArchitectAgent.java
│   └── ReleaseTrainEngineerAgent.java
├── Infrastructure Classes (2)
│   ├── SAFeAgentRegistry.java
│   └── SAFeAgentBootstrap.java
├── Data Models (3)
│   ├── UserStory.java
│   ├── SAFeSprint.java
│   └── AgentDecision.java
└── Documentation
    ├── SAFE_AGENTS_README.md (Comprehensive guide)
    ├── SAFE_AGENTS_IMPLEMENTATION_GUIDE.md (Architecture)
    ├── QUICK_START.md (5-minute setup)
    ├── package-info.java (JavaDoc)
    └── INDEX.md (This file)
```

## File Reference

### Core Agents

#### 1. ProductOwnerAgent.java
**Lines**: 385 | **Port**: 8090 (default)
- Manages product backlog prioritization
- Reviews and accepts completed stories
- Analyzes and resolves story dependencies
- **Tasks**: BacklogPrioritization, StoryAcceptance, DependencyAnalysis
- **Output**: PrioritizedBacklog, AcceptanceDecision, DependencyResolution

#### 2. ScrumMasterAgent.java
**Lines**: 384 | **Port**: 8091 (default)
- Facilitates SAFe ceremonies (standups, reviews, retros)
- Identifies and removes team blockers
- Tracks sprint velocity and metrics
- Manages sprint impediments
- **Tasks**: StandupFacilitation, BlockerRemoval, VelocityTracking, ImpedimentManagement
- **Output**: StandupReport, BlockerResolution, VelocityUpdate, ImpedimentLog

#### 3. DeveloperAgent.java
**Lines**: 413 | **Port**: 8092 (default)
- Executes assigned user stories
- Reports development progress
- Conducts peer code reviews
- Runs and reports unit tests
- **Tasks**: StoryExecution, ProgressReporting, CodeReview, UnitTesting
- **Output**: ExecutionProgress, StatusUpdate, CodeReviewFeedback, TestResults

#### 4. SystemArchitectAgent.java
**Lines**: 413 | **Port**: 8093 (default)
- Designs system architecture and solutions
- Manages cross-team dependencies
- Evaluates technical feasibility
- Conducts architecture reviews
- **Tasks**: ArchitectureDesign, DependencyManagement, FeasibilityEvaluation, TechnicalReview
- **Output**: ArchitectureDecision, DependencyResolution, FeasibilityAssessment, ArchitectureReview

#### 5. ReleaseTrainEngineerAgent.java
**Lines**: 410 | **Port**: 8094 (default)
- Orchestrates Program Increment (PI) planning
- Coordinates multi-team releases
- Plans staged deployments (dev, stage, prod)
- Assesses release readiness and quality gates
- **Tasks**: PIPlanning, ReleaseCoordination, DeploymentPlanning, ReleaseReadiness
- **Output**: PISchedule, ReleaseApproval, DeploymentPlan, ReadinessAssessment

### Infrastructure Classes

#### 6. SAFeAgentRegistry.java
**Lines**: 190
Factory and lifecycle manager for all agents.

**Key Methods**:
- `createProductOwnerAgent()` - Create individual agent
- `createScrumMasterAgent()` - Create individual agent
- `createDeveloperAgent()` - Create individual agent
- `createSystemArchitectAgent()` - Create individual agent
- `createReleaseTrainEngineerAgent()` - Create individual agent
- `createCompleteTeam()` - Create all 5 agents
- `startAll()` - Start all registered agents
- `stopAll()` - Stop all agents gracefully
- `getHealthStatus()` - Get health status of all agents
- `isHealthy()` - Check if all agents are active

**Constructor**:
```java
SAFeAgentRegistry registry = new SAFeAgentRegistry(
    engineUrl,      // YAWL engine URL
    username,       // Engine username
    password,       // Engine password
    basePort);      // Base HTTP port (agents use basePort + offset)
```

#### 7. SAFeAgentBootstrap.java
**Lines**: 120
Production bootstrap for starting agents from command line.

**Environment Variables**:
- `YAWL_ENGINE_URL` (default: http://localhost:8080/yawl)
- `YAWL_USERNAME` (default: admin)
- `YAWL_PASSWORD` (default: YAWL)
- `SAFE_BASE_PORT` (default: 8090)

**Execution**:
```bash
java org.yawlfoundation.yawl.safe.agents.SAFeAgentBootstrap
```

**Features**:
- Graceful shutdown hook (Ctrl+C)
- Automatic health monitoring
- Agent startup logging
- Error recovery with defaults

### Data Models

#### 8. UserStory.java
**Lines**: 102
Immutable Java 25 record for user story data.

**Fields**:
- `id` - Story identifier
- `title` - Story title
- `description` - Story description
- `acceptanceCriteria` - List of acceptance criteria
- `storyPoints` - Effort estimation
- `priority` - Priority ranking
- `status` - Story status (backlog, ready, in-progress, complete, blocked)
- `dependsOn` - List of dependency story IDs
- `assigneeId` - Assigned developer ID

**Methods**:
- `isBlocked(completedStories)` - Check if dependencies unmet
- `allCriteriaMet(acceptedCriteria)` - Check acceptance

#### 9. SAFeSprint.java
**Lines**: 120
Immutable Java 25 record for sprint planning data.

**Fields**:
- `id` - Sprint identifier
- `sprintNumber` - Sequential sprint number
- `startDate` - Sprint start date
- `endDate` - Sprint end date
- `sprintGoal` - Sprint objective
- `assignedStories` - List of assigned story IDs
- `committedPoints` - Committed story points
- `completedPoints` - Completed story points
- `status` - Sprint status
- `scrumMasterId` - Responsible Scrum Master ID

**Methods**:
- `velocity()` - Calculate points per day
- `isActive(now)` - Check if currently active
- `burnDownPercentage()` - Calculate completion %

#### 10. AgentDecision.java
**Lines**: 157
Immutable Java 25 record for decision traceability.

**Fields**:
- `id` - Decision identifier
- `decisionType` - Type (ACCEPT, REJECT, PRIORITIZE, etc.)
- `agentId` - Agent making decision
- `workItemId` - Related work item ID
- `outcome` - Decision outcome
- `rationale` - Explanation for decision
- `evidence` - Supporting metrics (Map<String, String>)
- `timestamp` - Decision timestamp

**Methods**:
- `toXml()` - Convert to XML for work item output
- `builder()` - Create fluent builder

## Documentation Files

### SAFE_AGENTS_README.md
**Location**: `/home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/`
**Purpose**: Comprehensive usage and reference guide
**Topics**:
- Architecture overview
- Task categories and discovery
- Decision model
- Usage examples
- Configuration
- HTTP endpoints
- Virtual threading
- Error handling
- Performance
- Logging
- Testing patterns
- Extension points
- Troubleshooting

### SAFE_AGENTS_IMPLEMENTATION_GUIDE.md
**Location**: `/home/user/yawl/`
**Purpose**: Architecture and implementation deep dive
**Topics**:
- Implementation summary
- Design patterns (5 patterns explained)
- Agent lifecycle
- HTTP endpoints
- Configuration examples
- Work item integration
- Testing strategy
- Deployment instructions
- Monitoring
- Performance characteristics
- Future enhancements

### QUICK_START.md
**Location**: `/home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/`
**Purpose**: Fast setup and reference
**Topics**:
- 30-second overview
- 5-minute setup
- Port allocation
- Work item integration
- Health checks
- Task types
- Customization
- Environment variables
- Decision types
- Performance metrics
- Troubleshooting

## API Quick Reference

### Create Single Agent

```java
ProductOwnerAgent agent = ProductOwnerAgent.create(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    8090);

agent.start();
```

### Create Complete Team

```java
SAFeAgentRegistry registry = new SAFeAgentRegistry(
    "http://localhost:8080/yawl",
    "admin",
    "YAWL",
    8090);

registry.createCompleteTeam();
registry.startAll();
```

### Check Agent Health

```java
Map<String, Boolean> health = registry.getHealthStatus();
// {ProductOwner: true, ScrumMaster: true, ...}

boolean allHealthy = registry.isHealthy();
```

### Access Decision Log

```java
Map<String, AgentDecision> decisions = agent.getDecisionLog();

decisions.values().forEach(d ->
    System.out.println(d.outcome() + ": " + d.rationale())
);
```

### HTTP Endpoints

```bash
# Agent card
curl http://localhost:8090/.well-known/agent.json

# Health status
curl http://localhost:8090/health

# Capacity info
curl http://localhost:8090/capacity
```

## Design Patterns

### 1. Strategy Pattern
- `DiscoveryStrategy` - Find eligible work items
- `EligibilityReasoner` - Validate eligibility
- `DecisionReasoner` - Produce output

### 2. Factory Pattern
- `ProductOwnerAgent.create()` - Static factory
- `SAFeAgentRegistry` - Team factory

### 3. Record Pattern (Java 25)
- Immutable data: UserStory, SAFeSprint, AgentDecision
- Auto-generated equals/hashCode/toString
- Canonical constructor validation

### 4. Virtual Threading
- Discovery: `Thread.ofVirtual()`
- HTTP: `newVirtualThreadPerTaskExecutor()`

### 5. Decision Logging
- Immutable records for traceability
- XML serialization for work items
- In-memory audit log

## Code Quality Standards

### HYPER_STANDARDS Compliance
- No TODO/FIXME/mock/stub/fake code
- No silent fallbacks
- Code matches documentation
- Real implementation or explicit throw

### Compilation
```bash
mvn -pl yawl-core compile -DskipTests -q
```

### Static Analysis
```bash
mvn clean verify -P analysis
```

### Test Coverage Target
80%+ code coverage with real YAWL engine integration tests

## Getting Started

### For Quick Setup
1. Read `/home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/QUICK_START.md`
2. Run SAFeAgentBootstrap with environment variables
3. Submit work items to YAWL engine
4. Check HTTP endpoints for health

### For Detailed Understanding
1. Read `/home/user/yawl/SAFE_AGENTS_IMPLEMENTATION_GUIDE.md`
2. Read `/home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/SAFE_AGENTS_README.md`
3. Review source code of specific agent
4. Study test patterns in related files

### For Implementation
1. Create YAWL specification with SAFe task types
2. Write Chicago TDD integration tests
3. Deploy agents using SAFeAgentBootstrap
4. Monitor decisions via HTTP endpoints
5. Persist decision log to database

## File Locations

| File | Absolute Path |
|------|---------------|
| ProductOwnerAgent | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/ProductOwnerAgent.java |
| ScrumMasterAgent | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/ScrumMasterAgent.java |
| DeveloperAgent | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/DeveloperAgent.java |
| SystemArchitectAgent | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/SystemArchitectAgent.java |
| ReleaseTrainEngineerAgent | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/ReleaseTrainEngineerAgent.java |
| SAFeAgentRegistry | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/SAFeAgentRegistry.java |
| SAFeAgentBootstrap | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/SAFeAgentBootstrap.java |
| QUICK_START.md | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/QUICK_START.md |
| SAFE_AGENTS_README.md | /home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/SAFE_AGENTS_README.md |
| IMPLEMENTATION_GUIDE.md | /home/user/yawl/SAFE_AGENTS_IMPLEMENTATION_GUIDE.md |

## Versioning

- **Version**: 6.0.0
- **Status**: Production Ready
- **Java Version**: Java 25+
- **YAWL Version**: v6.0.0+
- **License**: GNU Lesser General Public License v2+

## Related Resources

- `GenericPartyAgent` - Base class in `org.yawlfoundation.yawl.integration.autonomous`
- `AgentConfiguration` - Configuration class
- `AgentCapability` - Capability descriptor
- `AgentLifecycle` - Lifecycle states
- `WorkItemRecord` - Work item interface

---

**Last Updated**: 2026-02-28
**Maintained by**: YAWL Foundation
**Status**: Complete and Production-Ready
