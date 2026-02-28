# SAFe v6.0 Agent Deployment & Integration Guide

**Status**: Production-Ready | **Version**: 6.0.0 | **Date**: 2026-02-28

---

## Quick Start: Agent Bootstrap (< 5 minutes)

### Standalone Agent Launch

```bash
# 1. Set environment variables
export AGENT_ID=product-owner-agent
export AGENT_PORT=8090
export ENGINE_URL=http://localhost:8080/yawl
export ENGINE_USER=admin
export ENGINE_PASSWORD=admin
export AGENT_CAPABILITY="ProductOwner: backlog prioritization, story acceptance"

# 2. Run the agent
java -cp "target/classes:lib/*" \
  org.yawlfoundation.yawl.safe.agents.SAFeAgentBootstrap

# 3. Verify agent is running
curl http://localhost:8090/.well-known/agent.json
# Response: AgentCard with id, capabilities, endpoints
```

### Docker Deployment

```dockerfile
FROM temurin:25-jdk-jammy

WORKDIR /app

# Copy compiled JAR and dependencies
COPY target/yawl-safe-agents.jar .
COPY target/lib/*.jar lib/

# Set defaults (can override at runtime)
ENV AGENT_ID=safe-agent
ENV AGENT_PORT=8090
ENV ENGINE_URL=http://yawl-engine:8080/yawl

EXPOSE 8090

ENTRYPOINT ["java", "-cp", ".:yawl-safe-agents.jar:lib/*", \
  "org.yawlfoundation.yawl.safe.agents.SAFeAgentBootstrap"]
```

Launch with Docker:
```bash
docker build -t yawl-safe-agents:6.0 .

docker run -d \
  -e AGENT_ID=product-owner-1 \
  -e AGENT_PORT=8090 \
  -e ENGINE_URL=http://yawl-engine:8080/yawl \
  -e ENGINE_USER=admin \
  -e ENGINE_PASSWORD=admin \
  -e AGENT_CAPABILITY="ProductOwner: SAFe product owner" \
  -p 8090:8090 \
  --name safe-po-agent \
  yawl-safe-agents:6.0
```

---

## YAWL Engine Integration

### 1. Register Agent with YAWL Engine

After launching agent, register with engine:

```bash
# Agent auto-registers on startup via Agent Registry Service
# But you can also manually register for explicit control:

curl -X POST http://localhost:8080/yawl/register \
  -H "Content-Type: application/json" \
  -d '{
    "agent_id": "product-owner-agent",
    "agent_url": "http://localhost:8090",
    "capabilities": [
      "BacklogPrioritization",
      "StoryAcceptance",
      "DependencyAnalysis"
    ],
    "role": "PRODUCT_OWNER"
  }'
```

### 2. Configure YAWL Workflow for SAFe

#### Example: PI Planning Process

```xml
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">

  <specification uri="SAFe_PI_Planning_v6">

    <task id="Discover_Stories">
      <name>Discover Backlog Items</name>
      <flowInto nextElementRef="Prioritize_Backlog"/>
      <decomposesTo id="discover_stories_subnet"/>
    </task>

    <task id="Prioritize_Backlog">
      <name>Prioritize Backlog</name>
      <flowInto nextElementRef="Estimate_Stories"/>
      <!-- Route to ProductOwnerAgent -->
      <resourcingStrategy>
        <offer initiator="system">
          <distributionSet>
            <distribution resourceID="product-owner-agent">
              <allocate>100</allocate>
            </distribution>
          </distributionSet>
        </offer>
      </resourcingStrategy>
    </task>

    <task id="Estimate_Stories">
      <name>Estimate Story Points</name>
      <flowInto nextElementRef="Sprint_Planning"/>
      <!-- Route to DeveloperAgent -->
      <resourcingStrategy>
        <offer initiator="system">
          <distributionSet>
            <distribution resourceID="developer-agent">
              <allocate>100</allocate>
            </distribution>
          </distributionSet>
        </offer>
      </resourcingStrategy>
    </task>

    <task id="Sprint_Planning">
      <name>Sprint Planning Ceremony</name>
      <flowInto nextElementRef="Execute_Sprint"/>
      <!-- Route to ScrumMasterAgent -->
      <resourcingStrategy>
        <offer initiator="system">
          <distributionSet>
            <distribution resourceID="scrum-master-agent">
              <allocate>100</allocate>
            </distribution>
          </distributionSet>
        </offer>
      </resourcingStrategy>
    </task>

    <task id="Execute_Sprint">
      <name>Execute Sprint Tasks</name>
      <flowInto nextElementRef="Sprint_Review"/>
      <!-- Route to DeveloperAgent -->
      <resourcingStrategy>
        <offer initiator="system">
          <distributionSet>
            <distribution resourceID="developer-agent">
              <allocate>100</allocate>
            </distribution>
          </distributionSet>
        </offer>
      </resourcingStrategy>
    </task>

    <task id="Sprint_Review">
      <name>Sprint Review</name>
      <flowInto nextElementRef="Retrospective"/>
      <!-- Route to ScrumMasterAgent -->
      <resourcingStrategy>
        <offer initiator="system">
          <distributionSet>
            <distribution resourceID="scrum-master-agent">
              <allocate>100</allocate>
            </distribution>
          </distributionSet>
        </offer>
      </resourcingStrategy>
    </task>

    <task id="Retrospective">
      <name>Sprint Retrospective</name>
      <flowInto nextElementRef="Done"/>
      <!-- Route to ScrumMasterAgent -->
      <resourcingStrategy>
        <offer initiator="system">
          <distributionSet>
            <distribution resourceID="scrum-master-agent">
              <allocate>100</allocate>
            </distribution>
          </distributionSet>
        </offer>
      </resourcingStrategy>
    </task>

    <outputCondition id="Done"/>

  </specification>

</specificationSet>
```

### 3. Deploy Workflow to YAWL

```bash
# 1. Compile workflow
xmllint --schema schema/YAWL_Schema_6.0.xsd safe-pi-planning.xml

# 2. Upload to engine
curl -X POST http://localhost:8080/yawl/deploy \
  -F "specfile=@safe-pi-planning.xml" \
  -H "Authorization: Bearer $(get-yawl-token)"

# 3. Launch work item
curl -X POST http://localhost:8080/yawl/SAFe_PI_Planning_v6/launch \
  -H "Content-Type: application/json" \
  -d '{
    "release_train": "Release_Train_1",
    "pi_number": 42,
    "start_date": "2026-03-01",
    "duration_weeks": 12
  }'
```

---

## Multi-Agent Orchestration

### Setup 5-Agent Team

```bash
# Agent 1: Product Owner (port 8090)
docker run -d --name po-agent \
  -e AGENT_ID=po-1 \
  -e AGENT_PORT=8090 \
  -e AGENT_CAPABILITY="ProductOwner: ..." \
  yawl-safe-agents:6.0

# Agent 2: Scrum Master (port 8091)
docker run -d --name sm-agent \
  -e AGENT_ID=sm-1 \
  -e AGENT_PORT=8091 \
  -e AGENT_CAPABILITY="ScrumMaster: ..." \
  yawl-safe-agents:6.0

# Agent 3: Developer (port 8092)
docker run -d --name dev-agent \
  -e AGENT_ID=dev-1 \
  -e AGENT_PORT=8092 \
  -e AGENT_CAPABILITY="Developer: ..." \
  yawl-safe-agents:6.0

# Agent 4: System Architect (port 8093)
docker run -d --name arch-agent \
  -e AGENT_ID=arch-1 \
  -e AGENT_PORT=8093 \
  -e AGENT_CAPABILITY="SystemArchitect: ..." \
  yawl-safe-agents:6.0

# Agent 5: Release Train Engineer (port 8094)
docker run -d --name rte-agent \
  -e AGENT_ID=rte-1 \
  -e AGENT_PORT=8094 \
  -e AGENT_CAPABILITY="ReleaseTrainEngineer: ..." \
  yawl-safe-agents:6.0

# Verify all agents registered
curl http://localhost:8080/yawl/agents/list | jq '.agents | length'
# Output: 5
```

### Event-Driven Ceremony Orchestration

Agents communicate via event bus for multi-agent ceremonies:

```java
// From within any agent:
eventBus.publish(new SprintPlanningCeremonyStarted(
  sprintId: "Sprint_42",
  participantCount: 15,
  startTime: Instant.now()
));

// Other agents listen:
@Subscribe
public void onSprintPlanningStarted(SprintPlanningCeremonyStarted event) {
    logger.info("Scrum Master heard: Sprint {} starting", event.sprintId());
    // Take action...
}
```

---

## Monitoring & Health Checks

### Agent Health Endpoint

```bash
# Check agent health
curl http://localhost:8090/health

# Response:
{
  "status": "UP",
  "timestamp": "2026-02-28T12:00:00Z",
  "agent_id": "product-owner-agent",
  "ceremonies_executed": 42,
  "decisions_made": 156,
  "last_decision_time": "2026-02-28T11:59:45Z",
  "engine_connection": "HEALTHY",
  "queue_depth": 0
}
```

### Agent Capacity

```bash
# Check agent capacity for task assignment
curl http://localhost:8090/capacity

# Response:
{
  "agent_id": "product-owner-agent",
  "max_concurrent_tasks": 5,
  "active_tasks": 2,
  "available_capacity": 3,
  "task_types": [
    "BacklogPrioritization",
    "StoryAcceptance",
    "DependencyAnalysis"
  ]
}
```

### Agent Card (Service Discovery)

```bash
# Get agent metadata
curl http://localhost:8090/.well-known/agent.json

# Response:
{
  "id": "product-owner-agent",
  "name": "SAFe Product Owner Agent",
  "version": "6.0.0",
  "capabilities": [
    {
      "name": "BacklogPrioritization",
      "description": "Rank work items by business value"
    },
    {
      "name": "StoryAcceptance",
      "description": "Review and accept completed stories"
    },
    {
      "name": "DependencyAnalysis",
      "description": "Identify and resolve inter-story dependencies"
    }
  ],
  "endpoints": {
    "health": "http://localhost:8090/health",
    "capacity": "http://localhost:8090/capacity",
    "decisions": "http://localhost:8090/decisions"
  }
}
```

---

## Decision Audit Trail

### Export Agent Decisions

```bash
# Get all decisions made by an agent
curl http://localhost:8090/decisions?limit=100

# Response:
{
  "agent_id": "product-owner-agent",
  "decisions": [
    {
      "id": "dec-12345",
      "type": "BacklogPrioritization",
      "timestamp": "2026-02-28T10:00:00Z",
      "work_item_id": "story-456",
      "outcome": "PRIORITIZED_HIGH",
      "rationale": "Critical dependency for Release Train 1",
      "evidence": {
        "business_value": "critical",
        "risk_level": "medium",
        "affected_teams": "3"
      }
    }
  ]
}
```

### Decision History Query

```bash
# Query decisions by work item
curl http://localhost:8080/yawl/decisions?work_item=story-456

# Query decisions by ceremony
curl http://localhost:8080/yawl/decisions?ceremony=PI_Planning_42

# Query decisions by agent
curl http://localhost:8080/yawl/decisions?agent=product-owner-agent

# Query decisions by time range
curl http://localhost:8080/yawl/decisions?from=2026-02-01&to=2026-02-28
```

---

## Performance Tuning

### High-Throughput Configuration

For Release Trains with 100+ teams:

```bash
# Agent configuration
export AGENT_PORT=8090
export DISCOVERY_POLL_INTERVAL_MS=2000      # Poll every 2s
export MAX_CONCURRENT_TASKS=10               # Handle 10 tasks concurrently
export DECISION_THREAD_POOL_SIZE=20          # Use 20 decision threads
export EVENT_BUS_BUFFER_SIZE=1000            # Large event queue
export ENABLE_VIRTUAL_THREADS=true           # Use Java 25 virtual threads
```

### Load Balancing Multiple Agents

```bash
# Deploy 3 ProductOwnerAgent instances behind load balancer
docker run -d --name po-agent-1 \
  -e AGENT_ID=po-1 \
  -p 8090:8090 \
  yawl-safe-agents:6.0

docker run -d --name po-agent-2 \
  -e AGENT_ID=po-2 \
  -p 8091:8090 \
  yawl-safe-agents:6.0

docker run -d --name po-agent-3 \
  -e AGENT_ID=po-3 \
  -p 8092:8090 \
  yawl-safe-agents:6.0

# Configure YAWL load balancer to distribute BacklogPrioritization tasks
# across all 3 ProductOwnerAgent instances
```

---

## Troubleshooting

### Agent Won't Start

```bash
# Check logs
docker logs safe-po-agent | grep -E "ERROR|Exception"

# Common issues:
# 1. Engine URL unreachable
#    → Verify ENGINE_URL and network connectivity
#
# 2. Port already in use
#    → Change AGENT_PORT or kill process: lsof -i :8090
#
# 3. Missing credentials
#    → Check ENGINE_USER, ENGINE_PASSWORD
#
# 4. Java version mismatch
#    → Verify Java 25: java -version
```

### Agent Not Receiving Tasks

```bash
# 1. Check agent registration
curl http://localhost:8080/yawl/agents/product-owner-agent

# 2. Verify agent health
curl http://localhost:8090/health

# 3. Check capacity
curl http://localhost:8090/capacity
# If available_capacity = 0, agent is at max load

# 4. Check for errors in YAWL logs
tail -f /var/log/yawl/engine.log | grep "product-owner-agent"
```

### Slow Decision Making

```bash
# Increase thread pool for decision reasoning
export DECISION_THREAD_POOL_SIZE=50

# Enable virtual threads (Java 25)
export ENABLE_VIRTUAL_THREADS=true

# Increase capacity
export MAX_CONCURRENT_TASKS=20
```

---

## Production Deployment Checklist

- [ ] Java 25 installed and JAVA_HOME set
- [ ] YAWL engine running and accessible
- [ ] 5 agents compiled and ready for deployment
- [ ] Docker images built and pushed to registry
- [ ] Load balancer configured (for high-throughput)
- [ ] Logging configured (centralized to ELK/Splunk)
- [ ] Monitoring enabled (Prometheus metrics)
- [ ] Backup strategy in place (decision audit trail)
- [ ] SSL/TLS enabled for agent-engine communication
- [ ] Rate limiting configured for agent endpoints

---

## Next Steps

1. **Deploy agents** to staging environment
2. **Run workflow tests** with simulated team scenarios
3. **Monitor metrics** (decision latency, throughput)
4. **Tune parameters** based on observed performance
5. **Deploy to production** with blue-green strategy

---

**Deployment Guide Complete** ✅
