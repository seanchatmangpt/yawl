# Fortune 5 Agent Implementations — Quick Start Guide

**Goal**: Implement 6 new enterprise-scale SAFe agents in YAWL v6
**Timeline**: 10 weeks (with existing 5 team agents as foundation)
**Scope**: ~2,500 LOC production Java + comprehensive testing

---

## Quick Navigation

| Agent | LOC | Complexity | Risk | Start? |
|-------|-----|-----------|------|--------|
| **PortfolioGovernanceAgent** | 500 | High | Medium | ✓ Week 3 |
| **ValueStreamCoordinatorAgent** | 450 | Medium | Low | ✓ Week 4 |
| **SolutionTrainOrchestratorAgent** | 500 | High | Medium | ✓ Week 5 |
| **GeographicScaleAgent** | 400 | Medium | Medium | ✓ Week 6 |
| **ComplianceGovernanceAgent** | 350 | High | High | ✓ Week 7 |
| **GenAIOptimizationAgent** | 400 | Very High | High | ✓ Week 8 |

---

## Prerequisites

### Existing Codebase (Must Read First)

1. **GenericPartyAgent** (base for all agents)
   - Path: `/home/user/yawl/src/org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java`
   - Key: Virtual threads, discovery loop, HTTP endpoints

2. **Existing 5 Team Agents** (to understand patterns)
   - Path: `/home/user/yawl/src/org/yawlfoundation/yawl/safe/agents/`
   - Files: `ProductOwnerAgent.java`, `ScrumMasterAgent.java`, etc.

3. **Java 25 Conventions**
   - Path: `/home/user/yawl/.claude/rules/java25/modern-java.md`
   - Key: Records, sealed classes, virtual threads, structured concurrency

4. **Integration Standards**
   - Path: `/home/user/yawl/.claude/rules/integration/autonomous-agents.md`
   - Key: A2A protocol, handoff mechanism, error handling

### Dependencies (Maven POM)

```xml
<!-- Already in YAWL v6 -->
<dependency>
    <groupId>org.yawlfoundation.yawl</groupId>
    <artifactId>yawl-engine</artifactId>
    <version>6.0.0</version>
</dependency>

<!-- For new agents -->
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-api</artifactId>
    <version>2.23.0</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.23.0</version>
</dependency>

<!-- For GenAI Agent -->
<dependency>
    <groupId>com.openai</groupId>
    <artifactId>openai-java</artifactId>
    <version>0.16.0</version>
</dependency>
```

---

## Step-by-Step Implementation

### Step 1: Create Base Classes (Week 1-2)

Create new package: `org.yawlfoundation.yawl.enterprise.agents`

#### File 1: `EnterpriseAgent.java` (Abstract base)

```java
package org.yawlfoundation.yawl.enterprise.agents;

import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;
import org.yawlfoundation.yawl.integration.autonomous.AgentConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Abstract base for all enterprise-scale agents.
 * Extends GenericPartyAgent with enterprise capabilities:
 * - Multi-agent coordination
 * - Decision audit trails
 * - Resilience patterns
 * - Scale-aware metrics
 */
public sealed abstract class EnterpriseAgent extends GenericPartyAgent
    permits PortfolioGovernanceAgent, ValueStreamCoordinatorAgent,
            SolutionTrainOrchestratorAgent, GeographicScaleAgent,
            ComplianceGovernanceAgent, GenAIOptimizationAgent {

    protected static final Logger logger = LogManager.getLogger(EnterpriseAgent.class);

    protected final String enterpriseId;
    protected final Map<String, EnterpriseDecision> decisionLog;

    protected EnterpriseAgent(AgentConfiguration config, String enterpriseId)
            throws IOException {
        super(config);
        this.enterpriseId = enterpriseId;
        this.decisionLog = Collections.synchronizedMap(new LinkedHashMap<>());
    }

    /**
     * Record a decision for audit trail.
     */
    protected final void recordDecision(EnterpriseDecision decision) {
        decisionLog.put(decision.id(), decision);
        logger.info("Decision [{}] recorded: {} → {}",
            decision.id(), decision.decisionType(), decision.outcome());
    }

    /**
     * Get all recorded decisions.
     */
    public final Map<String, EnterpriseDecision> getDecisions() {
        return Collections.unmodifiableMap(decisionLog);
    }

    /**
     * Clear decision log (after persistence).
     */
    public final void clearDecisions() {
        decisionLog.clear();
    }
}
```

#### File 2: `EnterpriseDecision.java` (Java 25 Record)

```java
package org.yawlfoundation.yawl.enterprise.agents;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable record of an enterprise agent decision.
 * Used for audit trail, compliance tracking, metrics.
 */
public record EnterpriseDecision(
    String id,
    String agentId,
    String decisionType,        // WSJF_SCORE, ALLOCATE_CAPACITY, etc
    String workItemId,
    String outcome,
    String rationale,
    Map<String, String> evidence, // Supporting metrics/data
    Instant timestamp
) {
    public EnterpriseDecision {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Decision ID required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp required");
        }
    }
}
```

#### File 3: `EnterpriseAgentRegistry.java`

```java
package org.yawlfoundation.yawl.enterprise.agents;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing all 11 agents (5 team + 6 enterprise).
 * Lifecycle management, inter-agent messaging, health checks.
 */
public class EnterpriseAgentRegistry {
    private static final Logger logger = LogManager.getLogger(EnterpriseAgentRegistry.class);

    private final Map<String, EnterpriseAgent> agents = new ConcurrentHashMap<>();
    private final String enterpriseId;
    private final String engineUrl;

    public EnterpriseAgentRegistry(String enterpriseId, String engineUrl) {
        this.enterpriseId = enterpriseId;
        this.engineUrl = engineUrl;
    }

    /**
     * Register an agent in the registry.
     */
    public void register(String agentId, EnterpriseAgent agent) {
        agents.put(agentId, agent);
        logger.info("Agent registered: {}", agentId);
    }

    /**
     * Get agent by ID.
     */
    public Optional<EnterpriseAgent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    /**
     * Start all registered agents.
     */
    public void startAll() throws IOException {
        for (EnterpriseAgent agent : agents.values()) {
            agent.start();
            logger.info("Started agent: {}", agent.getLifecycle());
        }
    }

    /**
     * Stop all registered agents gracefully.
     */
    public void stopAll() {
        for (EnterpriseAgent agent : agents.values()) {
            agent.stop();
        }
        logger.info("All agents stopped");
    }

    /**
     * Get health status of all agents.
     */
    public Map<String, String> getHealthStatus() {
        return agents.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().getLifecycle().toString()
            ));
    }
}
```

### Step 2: Implement PortfolioGovernanceAgent (Week 3)

Create file: `PortfolioGovernanceAgent.java`

**Key sections to implement**:
1. WSJF calculation (formula-based)
2. Theme analysis (data aggregation)
3. Investment bucket rebalancing (percentage math)
4. HTTP endpoints (Jetty handlers)
5. Discovery cycle (poll themes, score epics, publish guidance)

**Test focus**:
- WSJF scoring accuracy vs formula
- Theme prioritization correctness
- HTTP endpoint responses

**Performance target**: Score 1000 epics in < 1 second

### Step 3: Implement ValueStreamCoordinatorAgent (Week 4)

Create file: `ValueStreamCoordinatorAgent.java`

**Key sections**:
1. Stream capacity calculation
2. Cross-stream dependency detection (graph algorithm)
3. WIP limit enforcement
4. Queue optimization (priority sorting)
5. Bottleneck detection (age-based scoring)

**Test focus**:
- Dependency detection in 10K items < 1s
- Capacity rebalancing correctness
- Bottleneck scoring accuracy

**Performance target**: Analyze 12 streams + 10K items in < 1 second

### Step 4: Implement SolutionTrainOrchestratorAgent (Week 5)

Create file: `SolutionTrainOrchestratorAgent.java`

**Key sections**:
1. ART status collection (parallel)
2. PI planning orchestration
3. Release wave scheduling (dependency-aware)
4. Ceremony scheduling
5. Risk tracking

**Test focus**:
- PI planning for 30 ARTs correct
- Release wave independence verification
- Critical path calculation

**Performance target**: Orchestrate 30 ARTs in < 2 seconds

### Step 5: Implement GeographicScaleAgent (Week 6)

Create file: `GeographicScaleAgent.java`

**Key sections**:
1. Timezone overlap calculation (overlapping business hours)
2. Ceremony scheduling (find common windows)
3. Timezone handoff coordination
4. Regional capacity allocation
5. Compliance per region

**Test focus**:
- Schedule 50 ceremonies across 6 regions < 300ms
- Timezone handoff correctness (PST→EST→UTC→IST→JST)
- Business hours overlap accuracy

**Performance target**: Schedule ceremonies in < 200ms

### Step 6: Implement ComplianceGovernanceAgent (Week 7)

Create file: `ComplianceGovernanceAgent.java`

**Key sections**:
1. Control effectiveness scoring (SOX/GDPR/HIPAA)
2. Audit trail hashing (SHA256, chain-linked)
3. Risk score calculation
4. Compliance reporting
5. Change control approvals

**Test focus**:
- Audit trail hash chain integrity
- Control effectiveness formula accuracy
- Compliance report generation

**Performance target**: Hash 10K audit entries < 5 seconds

### Step 7: Implement GenAIOptimizationAgent (Week 8)

Create file: `GenAIOptimizationAgent.java`

**Key sections**:
1. LLM integration (OpenAI/Anthropic)
2. Backlog scoring (multi-factor)
3. ML-based velocity forecasting
4. Delay prediction (statistical model)
5. Resource allocation recommendation

**Test focus**:
- LLM scoring consistency
- Forecast accuracy (MAPE < 20%)
- Anomaly detection precision/recall

**Performance target**: Score 1000 items in < 2 minutes

---

## Build & Test Commands

### Compile All

```bash
# From /home/user/yawl
mvn clean compile -pl src/org/yawlfoundation/yawl/enterprise/agents

# Or just the new module
cd src/org/yawlfoundation/yawl/enterprise/agents
mvn clean compile
```

### Run Unit Tests

```bash
# All tests
mvn test -pl src/org/yawlfoundation/yawl/enterprise/agents

# Single agent tests
mvn test -pl src/org/yawlfoundation/yawl/enterprise/agents \
  -Dtest=PortfolioGovernanceAgentTest

# Integration tests
mvn test -pl src/org/yawlfoundation/yawl/enterprise/agents \
  -Dtest=*IntegrationTest
```

### Run Performance Benchmarks

```bash
# Benchmark each agent
mvn jmh:benchmark -pl src/org/yawlfoundation/yawl/enterprise/agents

# Specific benchmark
mvn jmh:benchmark -pl src/org/yawlfoundation/yawl/enterprise/agents \
  -Djmh.include=PortfolioGovernanceAgentBenchmark
```

### Coverage Report

```bash
# Generate coverage
mvn clean test jacoco:report -pl src/org/yawlfoundation/yawl/enterprise/agents

# View report
open target/site/jacoco/index.html
```

---

## Deployment Checklist

### Pre-Production (Week 9)

- [ ] All unit tests passing (80%+ coverage)
- [ ] All integration tests with real YAWL engine
- [ ] Performance benchmarks meet targets (p95 < 2s)
- [ ] Security audit completed (no secrets in code)
- [ ] Load test with 500K work items
- [ ] Resilience testing (failures, timeouts, retries)

### Production (Week 10)

- [ ] Documentation complete (Javadoc, runbooks)
- [ ] Monitoring/alerting configured
- [ ] Database backup/recovery tested
- [ ] Audit trail verified (immutable, verifiable)
- [ ] Compliance sign-off (SOX/GDPR/HIPAA)
- [ ] Runbook for emergency procedures
- [ ] Team training completed

---

## Testing Template

Each agent should have:

### Unit Test Class

```java
// src/test/java/org/yawlfoundation/yawl/enterprise/agents/PortfolioGovernanceAgentTest.java
@DisplayName("PortfolioGovernanceAgent Tests")
class PortfolioGovernanceAgentTest {

    private PortfolioGovernanceAgent agent;

    @BeforeEach
    void setUp() {
        // Create agent with mock config
    }

    @Test
    @DisplayName("WSJF scoring matches formula")
    void testWSJFScoring() {
        // Arrange: inputs
        // Act: calculate WSJF
        // Assert: matches expected value
    }

    @Test
    @DisplayName("Theme allocation respects percentages")
    void testThemeAllocation() {
        // Arrange
        // Act
        // Assert
    }

    @Test
    @DisplayName("Graceful handling when theme data missing")
    void testMissingThemeData() {
        // Simulate missing data
        // Verify fallback behavior
    }
}
```

### Integration Test Class

```java
// src/test/java/org/yawlfoundation/yawl/enterprise/agents/PortfolioGovernanceAgentIntegrationTest.java
@DisplayName("PortfolioGovernanceAgent Integration Tests")
class PortfolioGovernanceAgentIntegrationTest {

    private PortfolioGovernanceAgent agent;
    private YEngine engine;  // Real YAWL engine

    @BeforeEach
    void setUp() throws IOException {
        // Start real YAWL engine
        engine = startYAWLEngine();
        // Create agent connected to real engine
        agent = PortfolioGovernanceAgent.create(engine.getURL(), ...);
    }

    @Test
    @DisplayName("WSJF scores propagate to value streams")
    void testWSJFPropagation() throws IOException, InterruptedException {
        // 1. Create work item in engine
        String wiId = engine.createWorkItem(...);

        // 2. Start agent
        agent.start();

        // 3. Wait for polling cycle
        Thread.sleep(2000);

        // 4. Verify score published
        WorkItemRecord wi = engine.getWorkItem(wiId);
        String data = wi.getDataString();
        assertTrue(data.contains("<WSJFScore>"));
    }
}
```

---

## Common Pitfalls

### Pitfall 1: Blocking Operations in Discovery Loop

**Wrong:**
```java
private void runDiscoveryCycle() {
    List<WorkItem> items = discoverWorkItems();  // OK
    for (WorkItem item : items) {
        processWorkItemSynchronously(item);  // ❌ BLOCKS
    }
}
```

**Right:**
```java
private void runDiscoveryCycle() {
    List<WorkItem> items = discoverWorkItems();
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        for (WorkItem item : items) {
            scope.fork(() -> processWorkItem(item));  // ✓ Non-blocking
        }
        scope.joinUntil(Instant.now().plus(Duration.ofSeconds(5)));
    }
}
```

### Pitfall 2: Ignoring Virtual Thread Pinning

**Wrong:**
```java
private synchronized void updateState(String key, String value) {  // ❌ PINS VT
    state.put(key, value);
}
```

**Right:**
```java
private final ReentrantLock stateLock = new ReentrantLock();

private void updateState(String key, String value) {
    stateLock.lock();
    try {
        state.put(key, value);  // ✓ Doesn't pin VT
    } finally {
        stateLock.unlock();
    }
}
```

### Pitfall 3: No Timeout on External Calls

**Wrong:**
```java
CompletableFuture<String> future = llmService.scoreItem(item);
String result = future.get();  // ❌ Can hang forever
```

**Right:**
```java
CompletableFuture<String> future = llmService.scoreItem(item);
String result = future.get(10, TimeUnit.SECONDS);  // ✓ 10-second timeout
```

### Pitfall 4: Throwing Checked Exceptions in Lambdas

**Wrong:**
```java
items.forEach(item -> processItem(item));  // ❌ If processItem throws
```

**Right:**
```java
items.forEach(item -> {
    try {
        processItem(item);
    } catch (Exception e) {
        logger.error("Failed to process item: {}", item, e);
    }
});
```

---

## Debugging Tips

### Enable Debug Logging

```properties
# log4j2.xml
<Logger name="org.yawlfoundation.yawl.enterprise.agents" level="DEBUG"/>
<Logger name="org.yawlfoundation.yawl.integration.autonomous" level="DEBUG"/>
```

### Inspect Decision Log

```bash
# Use jq to filter decisions
curl http://localhost:8095/portfolio/decisions | \
  jq '.decisions[] | select(.decisionType == "WSJF_SCORE")'
```

### Trace Inter-Agent Messages

```bash
# Enable message tracing
export TRACE_A2A_MESSAGES=true

# View messages
tail -f logs/a2a-messages.log | \
  grep -E "portfolio|stream|solution"
```

### Profile Virtual Threads

```bash
# Record JFR trace
jcmd <pid> JFR.start duration=60s filename=profile.jfr

# Analyze with JDK Mission Control
jmc profile.jfr
```

---

## Support & Resources

### Documentation

- **Full Spec**: `.claude/agents/FORTUNE5_AGENT_IMPLEMENTATIONS.md`
- **Java 25**: `.claude/rules/java25/modern-java.md`
- **A2A Protocol**: `.claude/rules/integration/autonomous-agents.md`
- **Existing Agents**: `src/org/yawlfoundation/yawl/safe/agents/SAFE_AGENTS_README.md`

### Code Examples

- **GenericPartyAgent**: `src/org/yawlfoundation/yawl/integration/autonomous/GenericPartyAgent.java`
- **ProductOwnerAgent**: `src/org/yawlfoundation/yawl/safe/agents/ProductOwnerAgent.java`
- **ReleaseTrainEngineerAgent**: `src/org/yawlfoundation/yawl/safe/agents/ReleaseTrainEngineerAgent.java`

### Testing Examples

- **SAFe Agent Tests**: `src/test/java/org/yawlfoundation/yawl/safe/agents/`

---

## Success Criteria

You've successfully implemented Fortune 5 agents when:

1. ✓ All 6 agents compile without errors
2. ✓ 80%+ unit test coverage across all agents
3. ✓ Integration tests with real YAWL engine pass
4. ✓ Performance targets met (p95 < 2s for enterprise decisions)
5. ✓ All agents discovered/registered in enterprise registry
6. ✓ Inter-agent A2A messaging works end-to-end
7. ✓ Audit trail immutable and verifiable
8. ✓ Security audit passed (no hardcoded secrets)
9. ✓ Documentation complete with runbooks
10. ✓ Team trained and ready for production

---

**Good luck! 🚀**

Questions? Contact YAWL Foundation Architecture Team.
