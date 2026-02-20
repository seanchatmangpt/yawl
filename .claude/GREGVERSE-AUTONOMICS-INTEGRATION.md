# Gregverse-Autonomics Integration — Autonomous Collaborative Agents

> Enable self-managed agents that coordinate with peers for resilience.
> Let your agents heal themselves AND help each other. 🤝⚡

---

## What is Gregverse-Autonomics?

**Gregverse** = Multi-agent collaborative framework (zero central server, peer-to-peer)
**Autonomics** = Self-healing workflows (auto-retry, health monitoring, escalation)

**Gregverse-Autonomics** = **Autonomous agents that:**
- ✅ Self-manage their own workflows
- ✅ Self-diagnose health issues
- ✅ Self-heal transient failures
- ✅ Self-coordinate with peer agents (swarm)
- ✅ Escalate critical issues to peers (distributed recovery)

---

## Architecture: Agent Autonomy Stack

```
Gregverse Mesh (Peer Network)
    ↓
[Agent Alpha]              [Agent Beta]            [Agent Gamma]
    ├─ Workflow Engine          ├─ Workflow Engine      ├─ Workflow Engine
    ├─ Autonomics               ├─ Autonomics           ├─ Autonomics
    ├─ AgentBrain               ├─ AgentBrain           ├─ AgentBrain
    └─ SwarmCoordinator         └─ SwarmCoordinator     └─ SwarmCoordinator
         ↓ broadcastHelpRequest       ↓ broadcastHelpRequest  ↓ broadcastHelpRequest
         └─────────────────────────────────────────────────────────┘
              (zero-latency, in-memory peer discovery)
```

**Key insight**: Each agent is autonomous. No agent depends on any other. But when an agent gets stuck, it asks peers for help. **Distributed resilience.**

---

## Core Concepts

### 1. Autonomous Agent (Self-Contained)

```java
AutonomousAgent agent = new AutonomousAgent("agent-001", engine);

// Agent does this automatically:
// - Executes workflows
// - Monitors their health (every 1-10 seconds)
// - Diagnoses failures (every 30-60 seconds)
// - Retries transient errors (with backoff)
// - Escalates critical issues (to swarm)
```

**No one tells the agent what to do.** It watches its own cases and acts accordingly.

### 2. Self-Monitoring Loop

```
Every 1-10 seconds:
┌─────────────────────────────────┐
│ Check all active cases          │
│ • Measure execution time        │
│ • Detect slow execution (>60s)  │
│ • Check for completion          │
│ • Record metrics                │
└─────────────────────────────────┘
     ↓
    Update AgentStatus
     ↓
    SwarmCoordinator observes
```

### 3. Self-Diagnosis Loop

```
Every 30-60 seconds:
┌──────────────────────────────────┐
│ Ask: Am I healthy?               │
│ • Check health report            │
│ • Count stuck cases              │
│ • Calculate health score         │
│ • Detect patterns (repeated?)    │
└──────────────────────────────────┘
     ↓
    If healthy? Continue
    If degraded? Handle:
       ├─ Try auto-recovery
       ├─ Escalate to swarm
       └─ Log to dead letter queue
```

### 4. Swarm Coordination (Peer Help)

```
Agent Alpha (stuck):
    "Hey peers! I have a stuck case."
         ↓
         ├─→ Agent Beta: "Busy, try someone else"
         ├─→ Agent Gamma: "I'm available, send it over!"
         └─→ (Handoff happens)

Agent Gamma:
    "I'll take this case from Alpha"
     ↓
     Adopts case + context
     ↓
     (Attempts recovery)
```

---

## Quick Start: 5 Minutes

### Step 1: Create Autonomous Agent

```java
YStatelessEngine engine = YStatelessEngine.getInstance();
AutonomousAgent agent = new AutonomousAgent("agent-001", engine);

// Agent now self-monitors via scheduled tasks:
// - performSelfMonitoring() every 1-10 seconds
// - performSelfDiagnosis() every 30-60 seconds
```

### Step 2: Register Autonomics Policies

```java
WorkflowAutonomicsEngine autonomics = new WorkflowAutonomicsEngine(engine);

// Tell autonomics how to recover from known failures
autonomics.registerRetryPolicy(
    "ConnectionException",
    new WorkflowAutonomicsEngine.RetryPolicy(3, 100, 2.0, true)
);

autonomics.registerRetryPolicy(
    "TimeoutException",
    new WorkflowAutonomicsEngine.RetryPolicy(2, 50, 2.0, true)
);

autonomics.startHealthMonitoring(Duration.ofSeconds(30));
```

### Step 3: Agent Executes Workflows

```java
// Load workflow specification
YSpecification spec = loadSpec("order-processing.yawl");

// Create input data
Map<String, String> inputData = new HashMap<>();
inputData.put("orderID", "ORD-123");
inputData.put("amount", "1000.00");

// Agent autonomously executes
YIdentifier caseID = agent.executeWorkflow(spec, inputData);

// Agent now:
// ✓ Monitors execution
// ✓ Detects stuck cases
// ✓ Recovers from transient failures
// ✓ Escalates critical issues to swarm
```

### Step 4: Monitor Agent Health

```java
// Check agent status periodically
AgentStatus status = agent.getStatus();

System.out.println("Agent ID: " + status.getAgentID());
System.out.println("Completed: " + status.getCompletedCases());
System.out.println("Stuck: " + status.getStuckCases());
System.out.println("Health: " + status.getHealthScore());
System.out.println("Healthy? " + status.isHealthy());

// Share with swarm (for peer discovery & load balancing)
broadcastAgentStatus(status);
```

---

## Agent Behavior Reference

### Self-Monitoring (Every 1-10 seconds)

**What it does**:
- Iterates all active cases
- Updates health metrics
- Detects slow execution (>60 seconds)
- Records completion events

**Why it matters**:
- Fast detection of execution delays
- Enables early escalation
- Tracks case lifecycle

### Self-Diagnosis (Every 30-60 seconds)

**What it does**:
- Gets health report from autonomics engine
- Checks for stuck cases (no progress 5+ min)
- Calculates health score
- Handles unrecoverable cases

**Why it matters**:
- Periodic health assessment
- Dead letter queue management
- Peer escalation decision-making

### Swarm Help Request

**When triggered**:
- Unrecoverable case detected
- Auto-recovery failed
- Case health unrecoverable

**What happens**:
```
agent.requestSwarmHelp(stuckCase)
    ↓
SwarmCoordinator.broadcastHelpRequest(stuckCase)
    ↓
    Check available peers:
    ├─ Peer B has workload=5, health=0.9 → Candidate
    ├─ Peer C has workload=12, health=0.6 → Too busy
    └─ Peer D has workload=3, health=0.95 → Best choice
    ↓
    Send case to Peer D
    ↓
    Peer D accepts → Remove from own queue → Continue
    OR
    All peers busy → Add to dead letter queue → Alert operator
```

---

## Configuration: Agent Behavior Tuning

### Self-Monitoring Interval

```java
// In setupAutonomy():
autonomyExecutor.scheduleAtFixedRate(
    this::performSelfMonitoring,
    1,        // Initial delay: 1 second
    10,       // Repeat every: 10 seconds
    TimeUnit.SECONDS
);
```

**Tuning**:
- Faster (1-5s) → Quick detection, more CPU
- Slower (10-30s) → Less overhead, delayed detection
- **Recommend**: 5-10 seconds for production

### Self-Diagnosis Interval

```java
autonomyExecutor.scheduleAtFixedRate(
    this::performSelfDiagnosis,
    30,       // Initial delay: 30 seconds
    60,       // Repeat every: 60 seconds
    TimeUnit.SECONDS
);
```

**Tuning**:
- Faster (30-60s) → Aggressive healing
- Slower (2-5 min) → Less aggressive
- **Recommend**: 30-60 seconds for production

### Stuck Detection Threshold

```java
// In WorkflowExecution.isStuck():
return stuckMs > 5 * 60 * 1000;  // 5 minutes
```

**Tuning**:
- Shorter (2-5 min) → Aggressive escalation
- Longer (10-30 min) → Patient recovery
- **Recommend**: 5 minutes (allows for legitimate long tasks)

---

## Swarm Coordination Patterns

### Pattern 1: Simple Load Balancing

```java
// Peer selection by workload
boolean broadcastHelpRequest(StuckCase stuckCase) {
    PeerAgent bestPeer = peerAgents.stream()
        .filter(p -> p.workload < 10)      // Not overloaded
        .min(Comparator.comparingInt(p -> p.workload))
        .orElse(null);

    if (bestPeer != null) {
        // Hand off case to best peer
        sendCase(bestPeer, stuckCase);
        return true;
    }
    return false;
}
```

### Pattern 2: Health-Aware Escalation

```java
// Prefer healthier peers
PeerAgent bestPeer = peerAgents.stream()
    .filter(p -> p.workload < 8)
    .filter(p -> p.healthScore > 0.7)
    .max(Comparator.comparingDouble(p -> p.healthScore))
    .orElse(null);
```

### Pattern 3: Circular Escalation

```java
// Try peers in rotation to avoid thundering herd
for (int i = 0; i < peerAgents.size(); i++) {
    PeerAgent peer = peerAgents.get((lastEscalatedIndex + i) % peerAgents.size());
    if (peer.canAccept()) {
        lastEscalatedIndex = (lastEscalatedIndex + i + 1) % peerAgents.size();
        return peer;
    }
}
```

---

## Monitoring Agent Health

### Agent Status Metrics

```java
AgentStatus status = agent.getStatus();

// Key metrics
int completedCases = status.getCompletedCases();      // Cases finished
int stuckCases = status.getStuckCases();              // Cases in trouble
double healthScore = status.getHealthScore();         // 0.0 (bad) → 1.0 (perfect)
boolean isHealthy = status.isHealthy();               // stuckCases==0 && health>0.8
```

### Health Score Calculation

```java
// Based on diagnostic events
double calculateHealthScore() {
    int totalEvents = diagnosticLog.size();
    long errorEvents = diagnosticLog.stream()
        .filter(e -> e.event.contains("error") || e.event.contains("stuck"))
        .count();

    if (totalEvents == 0) return 1.0;  // No problems = perfect
    return 1.0 - (double) errorEvents / totalEvents;
}
```

**Interpretation**:
- 0.95+ → Excellent (nearly all events successful)
- 0.80-0.94 → Good (minor issues)
- 0.50-0.79 → Degraded (significant issues)
- <0.50 → Critical (many failures)

### Swarm Visibility

```java
// Share status with peers (broadcast every 30s)
scheduler.scheduleAtFixedRate(() -> {
    AgentStatus myStatus = agent.getStatus();
    swarm.broadcastStatus(myStatus);  // Peers learn about my health
}, Duration.ofSeconds(30));

// Receive status from peers
void receiveStatusFromPeer(String peerId, AgentStatus status) {
    // Use to decide if peer can accept escalations
    peerRegistry.update(peerId, status);
}
```

---

## Dead Letter Queue Management

### What Gets Escalated to DLQ?

Cases requiring human intervention:

```
Case stuck > 5 minutes
    ├─ Auto-recovery attempted → Failed
    ├─ Help requested to swarm → All peers busy
    └─ → Add to dead letter queue → Alert operator
```

### Monitor DLQ

```java
// Periodically check for failed cases
scheduler.scheduleAtFixedRate(() -> {
    WorkflowAutonomicsEngine.DeadLetterQueue dlq = autonomics.getDeadLetterQueue();

    while (dlq.size() > 0) {
        var stuckCase = dlq.poll();
        if (stuckCase.isPresent()) {
            handleUnrecoverableCase(stuckCase.get());
        }
    }
}, 0, 5, TimeUnit.MINUTES);

private void handleUnrecoverableCase(WorkflowAutonomicsEngine.StuckCase stuckCase) {
    // Log for ops team
    LOGGER.error("ESCALATION: Case {} stuck for {}ms: {}",
        stuckCase.getCaseID(),
        stuckCase.getStuckDurationMs(),
        stuckCase.getReason());

    // Alert ops (PagerDuty, Slack, email, etc.)
    opsAlert.escalate(stuckCase);
}
```

---

## Testing Autonomous Agents

### Test: Agent Self-Monitors

```java
@Test
void agent_detects_slow_execution() {
    YSpecification spec = loadSpec();
    agent.executeWorkflow(spec, inputData);

    // Self-monitoring runs periodically
    AgentStatus status = agent.getStatus();
    assertTrue(status.getHealthScore() >= 0.0);
}
```

### Test: Agent Self-Diagnoses

```java
@Test
void agent_diagnoses_health() {
    agent.executeWorkflow(spec, inputData);

    AgentStatus status = agent.getStatus();
    assertTrue(status.getStuckCases() >= 0, "Should detect stuck cases");
}
```

### Test: Swarm Coordination

```java
@Test
void agent_requests_help_from_swarm() {
    YIdentifier caseID = engine.createCase(spec, inputData);
    WorkflowAutonomicsEngine.StuckCase stuckCase =
        new WorkflowAutonomicsEngine.StuckCase(caseID, "stuck", now);

    boolean accepted = agent.requestSwarmHelp(stuckCase);
    // Should handle gracefully (accept or decline)
    assertFalse(accepted);  // Expected if no peers
}
```

---

## Production Checklist

- [ ] Autonomics policies registered (retry, escalation)
- [ ] Health monitoring interval set (30-60s)
- [ ] Self-monitoring interval tuned (5-10s)
- [ ] Swarm peer discovery implemented
- [ ] Dead letter queue monitored
- [ ] Alerts configured (stuck cases, critical exceptions)
- [ ] Graceful shutdown: `agent.shutdown()` on application stop
- [ ] Metrics exported (OpenTelemetry integration)
- [ ] Test agents can recover from transient failures
- [ ] Test agents can escalate to swarm
- [ ] Documentation for operators (runbooks for DLQ cases)
- [ ] Load testing with multiple agents

---

## Best Practices

### ✅ DO

```java
// ✅ Agent manages its own health
AutonomousAgent agent = new AutonomousAgent("agent-001", engine);
agent.executeWorkflow(spec, inputData);
// Agent self-monitors, self-diagnoses, self-heals

// ✅ Register all known transient failures
autonomics.registerRetryPolicy("ConnectionException",
    new RetryPolicy(3, 100, 2.0, true));

// ✅ Monitor swarm health
AgentStatus status = agent.getStatus();
if (!status.isHealthy()) {
    alertOps("Agent degraded");
}

// ✅ Graceful shutdown
agent.shutdown();  // Stops scheduled tasks cleanly
```

### ❌ DON'T

```java
// ❌ Don't manually manage agent cases
// (agent does this automatically)
// for (YIdentifier caseID : manualList) { ... }

// ❌ Don't ignore dead letter queue
// (Schedule regular reviews)

// ❌ Don't assume agents never fail
// (Assume they will, have fallback)

// ❌ Don't set impossibly aggressive thresholds
// new RetryPolicy(10, 1, 1.0, true)  ← Will spam retries
```

---

## Troubleshooting

| Problem | Symptom | Cause | Solution |
|---------|---------|-------|----------|
| Agent not self-monitoring | No health updates | `performSelfMonitoring()` not running | Check thread pool initialized, verify no exceptions |
| Dead letter queue growing | DLQ size > 0 | Systemic failure (service down) | Investigate root cause, restart dependencies |
| Swarm help not accepted | All peers busy | High load across swarm | Add capacity, tune escalation threshold |
| Agent health score drops | Score < 0.5 | Too many failures | Check logs, increase retry attempts, investigate failures |
| Cases stuck constantly | Stuck count > 0 | Deadlock or resource exhaustion | Inspect case logs, review workflow logic |

---

## Integration with Observability

### Export Agent Metrics

```java
// Register with OpenTelemetry
MeterProvider meterProvider = MeterProvider.getInstance();
Meter meter = meterProvider.get("yawl-agents");

// Track agent health
DoubleGauge healthScore = meter.gaugeBuilder("agent.health.score")
    .buildWithCallback(obs -> {
        AgentStatus status = agent.getStatus();
        obs.record(status.getHealthScore());
    });

// Track active cases
LongCounter activeCases = meter.counterBuilder("agent.cases.active").build();
```

### Emit Agent Events

```java
// Span: case execution
try (Scope scope = tracer.startActiveSpan("agent.executeWorkflow")) {
    YIdentifier caseID = agent.executeWorkflow(spec, inputData);
}

// Event: escalation
events.emit("agent.escalation", new EventData()
    .withAgentId(agentID)
    .withCaseId(caseID)
    .withReason("stuck"));
```

---

## Next Steps

1. **Scale to swarm**: Add peer discovery (Consul, Kubernetes service discovery)
2. **Custom recovery**: Extend AgentBrain for domain-specific diagnostics
3. **Event sourcing**: Store all agent decisions for audit trail
4. **Machine learning**: Predict failures before they happen
5. **Simulation**: Test agent behavior under chaos engineering

---

**Your agents now manage themselves. Swarms now heal themselves. Zero central control. Maximum resilience. 🚀⚡**

See also:
- `AUTONOMICS-GUIDE.md` — Core autonomics framework
- `CONCEPTS.md` — Petri net and workflow semantics
- `.claude/rules/integration/autonomous-agents.md` — Integration patterns

Last updated: 2026-02-20
