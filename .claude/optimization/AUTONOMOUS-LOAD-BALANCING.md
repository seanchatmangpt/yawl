# Autonomous Load Balancing Design

**Status**: Design Phase | **Date**: Feb 2026 | **Benefit**: 80% fairness, 20% code

---

## Problem Statement

YAWL's multi-agent model uses simple polling to distribute work items. Without centralized coordination, unfairness emerges:

```
Scenario: 3 agents, 100 Approval tasks, 10 PaymentAuth tasks

Agent 1 (High capacity, 4 cores): Processes items sequentially
  - Discovers 100 items
  - Processes 40 Approvals (no queue awareness)
  - Leaves 60 Approvals + 10 PaymentAuth for agents 2-3

Agent 2 (Medium capacity, 2 cores): Also processes items sequentially
  - Discovers 60 remaining items
  - Processes 30 Approvals
  - Leaves 30 Approvals + 10 PaymentAuth for agent 3

Agent 3 (Low capacity, 1 core): Gets the rest
  - Discovers 40 remaining items
  - Processes 30 Approvals + 10 PaymentAuth

Result:
  Agent 1: Completes 40 items in ~320s (total capacity: 16 slots/s)
  Agent 2: Completes 30 items in ~240s (total capacity: 8 slots/s)
  Agent 3: Completes 40 items in ~320s (total capacity: 4 slots/s)

  Agent 1 (16 slots/s) is idle 75% of the time
  Agent 3 (4 slots/s) is working at 100% capacity

  System utilization: (16 + 8 + 4) / (16 + 8 + 4) = 28/28 slots = 100% average
  But Agent 1 is idle, Agent 3 is overloaded (starvation risk)
```

**Core Issues**:

1. **No queue awareness**: Agents don't know how many items are pending
2. **No capacity advertising**: Agents don't report their processing speed
3. **First-come-first-served**: Whoever polls first gets work (unfair)
4. **Starvation risk**: Low-capacity agents may never get work if high-capacity agents hog items
5. **No backpressure**: Overloaded agents cannot signal "I'm full"

---

## Solution: Autonomous Load Balancing with Queue Awareness

Agents monitor their own queue depth and the global queue depth, then auto-adjust their discovery rate. Fast agents reduce discovery frequency; slow agents increase it. The system reaches equilibrium where queue is drained uniformly.

### Architecture Diagram

```
Central Monitor (stateless, read-only)
  |
  └─ AgentRegistry (stores capacity + health)
      |
      ├─ Agent 1: capacity=16 items/s, queue_depth=5, health=HEALTHY
      ├─ Agent 2: capacity=8 items/s, queue_depth=20, health=BUSY
      └─ Agent 3: capacity=4 items/s, queue_depth=80, health=OVERLOADED

Discovery Loop (all agents)
  |
  ├─ Agent 1 (fast): Reduce discovery frequency
  |                  Every 5s discovery (was 1s)
  |                  Allows Agent 2,3 to catch up
  |
  ├─ Agent 2 (medium): Keep normal frequency
  |                    Every 1s discovery
  |
  └─ Agent 3 (slow): Increase discovery frequency
                     Every 500ms discovery (was 1s)
                     Takes more work to catch up

Result: Queue depth converges to uniform distribution
  Agent 1: queue_depth=25
  Agent 2: queue_depth=25
  Agent 3: queue_depth=25
  (All agents balanced)
```

### Load Balancing Algorithm

**Step 1: Measure Local Queue Depth**

```
queue_depth = my_current_tasks.size() + pending_items_discovered
processing_capacity = items_completed_per_second (historical avg)
queue_drain_time = queue_depth / processing_capacity
```

**Step 2: Read Global State (from AgentRegistry)**

```
global_queue_depth = total_enabled_items_in_engine
total_capacity = SUM(all_agents.capacity)
global_drain_time = global_queue_depth / total_capacity
```

**Step 3: Compute Load Factor**

```
load_factor = my_queue_depth / global_queue_depth
target_load_factor = my_capacity / total_capacity

underload = max(0, target_load_factor - load_factor)
overload = max(0, load_factor - target_load_factor)
```

**Step 4: Adjust Discovery Rate**

```
if (underload > 0.1) {
    // I'm too fast, reducing discovery frequency
    discovery_interval_ms = base_interval * (1 + underload)
    // Example: 1000ms * 1.5 = 1500ms (discover less often)
} else if (overload > 0.1) {
    // I'm too slow, increasing discovery frequency
    discovery_interval_ms = base_interval * (1 - overload)
    // Example: 1000ms * 0.5 = 500ms (discover more often)
} else {
    // I'm balanced
    discovery_interval_ms = base_interval
}
```

---

## Pseudocode Design

```java
// org.yawlfoundation.yawl.integration.autonomous.balancing.AutonomousLoadBalancer

public class AutonomousLoadBalancer {
    private final AgentContext agentContext;
    private final InterfaceBQueries engine;
    private final AgentRegistry agentRegistry;
    private final MetricsCollector metricsCollector;

    // Configuration
    private final long baseDiscoveryIntervalMs;      // Default: 1000ms
    private final double loadFactorThreshold;       // Default: 0.1
    private final double minDiscoveryIntervalMs;    // Min: 200ms
    private final double maxDiscoveryIntervalMs;    // Max: 10000ms

    // State
    private final RollingAverageWindow processingRate;  // items/second
    private final Queue<Long> discoveryTimestamps;      // Last 10 discoveries

    public AutonomousLoadBalancer(
            AgentContext agentContext,
            InterfaceBQueries engine,
            AgentRegistry agentRegistry) {
        this.agentContext = agentContext;
        this.engine = engine;
        this.agentRegistry = agentRegistry;
        this.baseDiscoveryIntervalMs = 1000;
        this.loadFactorThreshold = 0.1;
        this.minDiscoveryIntervalMs = 200;
        this.maxDiscoveryIntervalMs = 10000;
        this.processingRate = new RollingAverageWindow(60);  // 60-second window
        this.discoveryTimestamps = new ConcurrentLinkedQueue<>();
    }

    // Main method: compute next discovery interval
    public long computeNextDiscoveryInterval() {
        try {
            // Step 1: Measure local state
            LocalLoadMetrics localMetrics = measureLocalLoad();

            // Step 2: Fetch global state
            GlobalLoadMetrics globalMetrics = fetchGlobalLoad();

            // Step 3: Compute load imbalance
            LoadImbalance imbalance = computeLoadImbalance(localMetrics, globalMetrics);

            // Step 4: Adjust discovery interval
            long adjustedInterval = adjustDiscoveryInterval(imbalance);

            // Step 5: Record metrics
            metricsCollector.recordLoadMetrics(localMetrics, globalMetrics, imbalance);

            logger.info("Load imbalance: local={:.2f}, global={:.2f}, " +
                "discovery_interval={}ms",
                imbalance.localLoadFactor(),
                imbalance.targetLoadFactor(),
                adjustedInterval);

            return adjustedInterval;

        } catch (Exception e) {
            logger.warn("Load balancing computation failed, using base interval", e);
            return baseDiscoveryIntervalMs;
        }
    }

    private LocalLoadMetrics measureLocalLoad() {
        // Step 1: Count current pending work
        int pendingWorkItems = agentContext.getPendingWorkItems().size();

        // Step 2: Measure processing rate (last 60 seconds)
        double itemsPerSecond = processingRate.getAverage();
        if (itemsPerSecond < 0.1) {
            // Not enough data; use conservative estimate
            itemsPerSecond = 0.5;
        }

        // Step 3: Estimate queue drain time
        long estimatedDrainTimeMs = Math.round(1000.0 * pendingWorkItems / itemsPerSecond);

        return new LocalLoadMetrics(
            pendingWorkItems,
            itemsPerSecond,
            estimatedDrainTimeMs,
            agentContext.getAgentCapacity()
        );
    }

    private GlobalLoadMetrics fetchGlobalLoad() throws RegistryException {
        // Step 1: Query agent registry
        AgentStats stats = agentRegistry.getAgentStats();

        // Step 2: Query engine for total pending items
        Set<YWorkItem> enabledItems = engine.getAvailableWorkItems();
        int globalQueueDepth = enabledItems.size();

        // Step 3: Compute total capacity
        double totalCapacity = stats.getAllAgents().stream()
            .mapToDouble(agent -> agent.getCapacityItemsPerSecond())
            .sum();

        // Step 4: Estimate global drain time
        long estimatedGlobalDrainTimeMs =
            Math.round(1000.0 * globalQueueDepth / Math.max(totalCapacity, 0.1));

        return new GlobalLoadMetrics(
            globalQueueDepth,
            totalCapacity,
            estimatedGlobalDrainTimeMs,
            stats.getActiveAgentCount()
        );
    }

    private LoadImbalance computeLoadImbalance(
            LocalLoadMetrics local,
            GlobalLoadMetrics global) {

        // Step 1: Compute load factors
        double localLoadFactor = local.pendingWorkItems() /
            Math.max(local.capacity(), 1.0);

        double targetLoadFactor = (local.capacity() / Math.max(global.totalCapacity(), 1.0));

        // Step 2: Compute imbalance
        double imbalance = localLoadFactor - targetLoadFactor;
        double underloadMargin = Math.max(0, -imbalance);  // Positive if underloaded
        double overloadMargin = Math.max(0, imbalance);    // Positive if overloaded

        logger.debug("Load analysis: local={:.2f}, target={:.2f}, " +
            "underload={:.2f}, overload={:.2f}",
            localLoadFactor, targetLoadFactor, underloadMargin, overloadMargin);

        return new LoadImbalance(
            localLoadFactor,
            targetLoadFactor,
            imbalance,
            underloadMargin,
            overloadMargin
        );
    }

    private long adjustDiscoveryInterval(LoadImbalance imbalance) {
        // Step 1: Determine direction and magnitude
        long adjustedInterval = baseDiscoveryIntervalMs;

        if (imbalance.underloadMargin() > loadFactorThreshold) {
            // Underloaded: reduce discovery frequency (take fewer items)
            double reductionFactor = 1.0 + imbalance.underloadMargin();
            adjustedInterval = Math.round(baseDiscoveryIntervalMs * reductionFactor);

            logger.info("Underloaded by {:.2f}, increasing discovery interval to {}ms",
                imbalance.underloadMargin(), adjustedInterval);

        } else if (imbalance.overloadMargin() > loadFactorThreshold) {
            // Overloaded: increase discovery frequency (take more items)
            double accelerationFactor = 1.0 - imbalance.overloadMargin();
            adjustedInterval = Math.round(baseDiscoveryIntervalMs * accelerationFactor);

            logger.info("Overloaded by {:.2f}, decreasing discovery interval to {}ms",
                imbalance.overloadMargin(), adjustedInterval);
        }

        // Step 2: Enforce bounds
        adjustedInterval = Math.max(minDiscoveryIntervalMs,
            Math.min(maxDiscoveryIntervalMs, adjustedInterval));

        return adjustedInterval;
    }

    // Track processing rate for capacity estimation
    public void recordCompletedWorkItem(YWorkItem item, long processingTimeMs) {
        double itemsPerSecond = 1000.0 / processingTimeMs;
        processingRate.addValue(itemsPerSecond);

        logger.debug("Recorded completion: {}ms, rate={}items/s",
            processingTimeMs, String.format("%.2f", itemsPerSecond));
    }

    // Update registry with current capacity
    public void reportCapacityToRegistry() {
        try {
            double currentCapacity = processingRate.getAverage();
            agentContext.setCapacity(currentCapacity);

            agentRegistry.updateAgentCapacity(
                agentContext.getAgentId(),
                currentCapacity,
                agentContext.getPendingWorkItems().size(),
                computeHealthStatus()
            );

            logger.debug("Reported capacity to registry: {:.2f} items/s",
                currentCapacity);

        } catch (Exception e) {
            logger.warn("Failed to report capacity", e);
        }
    }

    private AgentHealth computeHealthStatus() {
        double capacity = processingRate.getAverage();
        int pendingItems = agentContext.getPendingWorkItems().size();
        long estimatedDrainMs = Math.round(1000.0 * pendingItems / Math.max(capacity, 0.1));

        if (estimatedDrainMs > 60000) {
            return AgentHealth.OVERLOADED;
        } else if (estimatedDrainMs > 30000) {
            return AgentHealth.BUSY;
        } else {
            return AgentHealth.HEALTHY;
        }
    }
}

// Metrics records
public record LocalLoadMetrics(
    int pendingWorkItems,
    double processingCapacityItemsPerSecond,
    long estimatedDrainTimeMs,
    double agentCapacity
) {}

public record GlobalLoadMetrics(
    int globalQueueDepth,
    double totalCapacity,
    long estimatedGlobalDrainTimeMs,
    int activeAgentCount
) {}

public record LoadImbalance(
    double localLoadFactor,
    double targetLoadFactor,
    double imbalance,
    double underloadMargin,
    double overloadMargin
) {}

// Discovery loop integration
public class PollingDiscoveryStrategy implements DiscoveryStrategy {
    private final AutonomousLoadBalancer loadBalancer;
    private long currentDiscoveryIntervalMs;

    @Override
    public void runDiscoveryLoop() {
        while (running.get()) {
            try {
                // Step 1: Compute adaptive discovery interval
                currentDiscoveryIntervalMs = loadBalancer.computeNextDiscoveryInterval();

                // Step 2: Discover and process work items
                List<WorkItemResult> results = discoverAndProcess();

                // Step 3: Track processing rate
                results.forEach(result -> {
                    if (result.status() == Status.COMPLETED) {
                        loadBalancer.recordCompletedWorkItem(
                            result.workItem(),
                            result.processingTimeMs()
                        );
                    }
                });

                // Step 4: Report capacity to registry
                loadBalancer.reportCapacityToRegistry();

                // Step 5: Sleep until next discovery cycle
                Thread.sleep(currentDiscoveryIntervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running.set(false);
                break;
            }
        }
    }
}

// Rolling average window for capacity estimation
public class RollingAverageWindow {
    private final Deque<Double> window;
    private final int maxSize;
    private double sum = 0;

    public RollingAverageWindow(int maxSize) {
        this.maxSize = maxSize;
        this.window = new ConcurrentLinkedDeque<>();
    }

    public void addValue(double value) {
        synchronized (this) {
            if (window.size() >= maxSize) {
                sum -= window.removeFirst();
            }
            window.addLast(value);
            sum += value;
        }
    }

    public double getAverage() {
        synchronized (this) {
            return window.isEmpty() ? 0 : sum / window.size();
        }
    }
}
```

---

## Load Balancing Examples

### Example 1: Underloaded Fast Agent

```
Agent 1 (capacity=16 items/s):
  local_queue_depth = 5 items
  pending_items_count = 5
  processing_rate = 16 items/s
  estimated_drain_time = 5 / 16 = 0.3s

Global state:
  global_queue_depth = 100 items
  total_capacity = 16 + 8 + 4 = 28 items/s

Load factor analysis:
  local_load_factor = 5 / 16 = 0.31
  target_load_factor = 16 / 28 = 0.57
  imbalance = 0.31 - 0.57 = -0.26 (UNDERLOADED by 26%)

Discovery interval adjustment:
  base_interval = 1000ms
  reduction_factor = 1 + 0.26 = 1.26
  new_interval = 1000 * 1.26 = 1260ms (reduce discovery frequency)

Result: Agent 1 waits longer between discoveries, allowing Agent 2/3 to catch up
```

### Example 2: Overloaded Slow Agent

```
Agent 3 (capacity=4 items/s):
  local_queue_depth = 80 items
  processing_rate = 4 items/s
  estimated_drain_time = 80 / 4 = 20s

Global state:
  global_queue_depth = 100 items
  total_capacity = 28 items/s

Load factor analysis:
  local_load_factor = 80 / 4 = 20.0
  target_load_factor = 4 / 28 = 0.14
  imbalance = 20.0 - 0.14 = 19.86 (OVERLOADED by 1886%)

Discovery interval adjustment:
  capped_overload_margin = min(1886%, 100%) = 100%
  acceleration_factor = 1 - 1.0 = 0
  new_interval = max(200ms, 1000 * 0) = 200ms (max frequency)

Result: Agent 3 discovers items as fast as possible (every 200ms), catching up
```

### Example 3: Converged Balanced State

```
All agents at equilibrium:

Agent 1 (capacity=16):
  queue_depth = 28, load_factor = 28/16 = 1.75
  target = 16/28 = 0.57
  discovery_interval = 1000ms (balanced)

Agent 2 (capacity=8):
  queue_depth = 14, load_factor = 14/8 = 1.75
  target = 8/28 = 0.29
  discovery_interval = 1000ms (balanced)

Agent 3 (capacity=4):
  queue_depth = 7, load_factor = 7/4 = 1.75
  target = 4/28 = 0.14
  discovery_interval = 1000ms (balanced)

Result: All agents have same load factor (1.75), system is fair
```

---

## Integration with Agent Registry

### Registry Data Model

```java
public record AgentInfo(
    String agentId,
    String hostAddress,
    int port,
    double capacityItemsPerSecond,    // items/s capability
    int pendingWorkItems,              // current queue depth
    AgentHealth health,                // HEALTHY, BUSY, OVERLOADED
    Instant lastReportedAt,            // when was this updated
    Set<String> capabilities           // task types this agent can handle
) {}

public enum AgentHealth {
    HEALTHY,        // drain_time < 30s
    BUSY,           // 30s <= drain_time < 60s
    OVERLOADED      // drain_time >= 60s
}
```

### Registry Operations

```
PUT /agents/{agentId}/capacity
  Body: { capacity_items_per_second: 8.5, pending_items: 12, health: "BUSY" }
  TTL: 30s (agent reports every 10s)

GET /agents/stats
  Response: { agents: [...], total_capacity: 28.5, global_queue_depth: 100 }
```

---

## Performance Model

### Without Load Balancing (Sequential Discovery)

```
Agent 1: queue_depth = 40 items, drain_time = 40/16 = 2.5s
Agent 2: queue_depth = 35 items, drain_time = 35/8 = 4.4s
Agent 3: queue_depth = 25 items, drain_time = 25/4 = 6.25s

Max drain time: 6.25s (bottleneck at Agent 3)
Fairness: Poor (Agent 1 gets 40%, Agent 3 gets 25%)
```

### With Load Balancing (Adaptive Discovery)

```
Agent 1: queue_depth = 28 items, drain_time = 28/16 = 1.75s
Agent 2: queue_depth = 28 items, drain_time = 28/8 = 3.5s
Agent 3: queue_depth = 28 items, drain_time = 28/4 = 7s

Max drain time: 7s (still same, but fairly distributed)
Fairness: Good (each gets ~28 items, proportional to capacity)
System utilization: 100% (no idle agents)
```

---

## Failure Modes & Recovery

### Mode 1: Agent Registry Unavailable

```
fetchGlobalLoad() -> RegistryException
  -> Use cached metrics from last successful read
  -> Fallback to conservative estimate (assume high global load)
  -> Increase discovery interval (reduce competition)
```

### Mode 2: Capacity Estimation Unreliable

```
processingRate < 0.1 items/s (not enough history)
  -> Use default estimate (0.5 items/s)
  -> Switch to WARM_UP mode
  -> After 60 samples, use real estimate
```

### Mode 3: Agent Becomes Unavailable

```
Agent 3 crashes (stopped reporting to registry after 3 * 30s = 90s)
  -> Registry marks Agent 3 as DEAD
  -> Agent 1, 2 recalculate target_load_factor without Agent 3
  -> target_load_factor (Agent 1) = 16 / 24 = 0.67 (increased)
  -> Discovery intervals adjust automatically
```

---

## Configuration

```yaml
agent:
  load-balancing:
    enabled: true
    base-discovery-interval-ms: 1000
    min-discovery-interval-ms: 200        # Fastest possible discovery
    max-discovery-interval-ms: 10000      # Slowest possible discovery
    load-factor-threshold: 0.10           # Trigger adjustment at ±10%
    capacity-report-interval-ms: 10000    # Report every 10s
    capacity-window-size: 60              # 60-second rolling average

  registry:
    url: "http://agent-registry:8090"
    connection-timeout-ms: 5000
    heartbeat-interval-ms: 10000
    heartbeat-ttl-ms: 30000               # Agent expires after 30s no report
```

---

## Metrics & Observability

```
Counters:
  agent.load_balancing.discovery.total           // Total discovery cycles
  agent.load_balancing.underload_adjustments     // Times interval was increased
  agent.load_balancing.overload_adjustments      // Times interval was decreased

Histograms:
  agent.load_balancing.discovery_interval_ms     // Distribution of intervals
  agent.load_balancing.queue_depth               // Distribution of pending items
  agent.load_balancing.load_factor               // Distribution of local load factor

Gauges:
  agent.load_balancing.current_interval_ms       // Current discovery interval
  agent.load_balancing.current_queue_depth       // Current pending items
  agent.load_balancing.current_load_factor       // Current load factor
  agent.load_balancing.target_load_factor        // Fair share target
```

---

## Implementation Roadmap

1. **Phase 1 (Days 1-2)**: Create `LocalLoadMetrics`, `GlobalLoadMetrics`, `LoadImbalance` records
2. **Phase 2 (Days 3-4)**: Implement `RollingAverageWindow` and load calculation logic
3. **Phase 3 (Days 5-6)**: Integrate with `AgentRegistry` and report/fetch operations
4. **Phase 4 (Days 7)**: Integrate with `PollingDiscoveryStrategy` discovery loop
5. **Phase 5 (Days 8)**: Add metrics, configuration, tests
6. **Validation (Days 9)**: Load test with 10 agents of varying capacities

---

## Files to Create/Update

- **Create**: `org/yawlfoundation/yawl/integration/autonomous/balancing/AutonomousLoadBalancer.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/balancing/LoadImbalance.java` (record)
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/balancing/RollingAverageWindow.java`
- **Create**: `org/yawlfoundation/yawl/integration/autonomous/registry/AgentRegistry.java` (interface)
- **Update**: `org/yawlfoundation/yawl/integration/autonomous/strategies/PollingDiscoveryStrategy.java`
- **Update**: `application.yml` (configuration)

---

## Related Designs

- **AUTONOMOUS-TASK-PARALLELIZATION.md**: Parallel batch processing (improves capacity)
- **PREDICTIVE-WORK-ROUTING.md**: Predictive pre-warming (reduces processing time variance)
- **AUTONOMOUS-SMART-CACHING.md**: Cache optimization (enables fair comparison of agent speeds)

---

## Testing Strategy

### Unit Test: Underload Adjustment

```java
@Test
void testDiscoveryIntervalIncreasesWhenUnderloaded() {
    // Arrange: Agent 1 is underloaded (5 items, capacity 16 items/s)
    LocalLoadMetrics local = new LocalLoadMetrics(5, 16, 312, 16);
    GlobalLoadMetrics global = new GlobalLoadMetrics(100, 28, 3571, 3);

    // Act
    LoadImbalance imbalance = balancer.computeLoadImbalance(local, global);
    long interval = balancer.adjustDiscoveryInterval(imbalance);

    // Assert: Interval should be increased
    assertTrue(interval > 1000, "Underloaded agent should discover less often");
    assertEquals(1260, interval, "1000ms * 1.26 = 1260ms");
}
```

### Integration Test: Convergence

```java
@Test
void testMultipleAgentsConvergeToFairLoad() throws InterruptedException {
    // Arrange: 3 agents with capacities 16, 8, 4
    Agent[] agents = createAgents(3);

    // Act: Let them balance for 5 cycles (5 seconds)
    Thread.sleep(5000);

    // Assert: All agents have load_factor ≈ 1.75
    for (Agent agent : agents) {
        double loadFactor = agent.getLoadFactor();
        assertEquals(1.75, loadFactor, 0.1,
            "All agents should converge to same load factor");
    }
}
```

---

## Backward Compatibility

- **Interface B**: No changes
- **AgentRegistry**: New optional methods; existing agent registration works unchanged
- **Configuration**: Default `load-balancing.enabled: false` for safe rollout
- **Fallback**: If load balancing disabled, base interval used for all discoveries

---

## References

- Load Balancing Theory: https://www.usenix.org/conference/nsdi21/presentation/li-wangni
- Distributed Work Queues: https://research.google/pubs/workstealing-distributed-scheduling/
- Proportional Fair Scheduling: https://en.wikipedia.org/wiki/Proportional_fairness
