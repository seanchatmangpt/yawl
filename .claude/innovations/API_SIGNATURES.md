# Blue Ocean Innovations — Complete API Signatures

**Reference Document**: Full type signatures for all 5 innovations, ready for implementation

---

## Innovation 1: Workflow Composition Layer

### Core Interfaces

```java
// Location: src/org/yawlfoundation/yawl/composition/ComposableWorkflow.java
public sealed interface ComposableWorkflow<IN extends Record, OUT extends Record>
    permits StaticComposableWorkflow, ParameterizedComposableWorkflow {
}

public record ComposableWorkflowHandle<IN extends Record, OUT extends Record>(
    String workflowId,
    String version,
    Class<IN> inputType,
    Class<OUT> outputType,
    String specificationXml
) implements ComposableWorkflow<IN, OUT> {
}

// Location: src/org/yawlfoundation/yawl/composition/ComposableWorkflowLibrary.java
public interface ComposableWorkflowLibrary {
    <IN extends Record, OUT extends Record> void register(
        ComposableWorkflowHandle<IN, OUT> workflow,
        String version
    ) throws IllegalArgumentException;

    List<ComposableWorkflowHandle<?, ?>> search(CompositionQuery query);

    <IN extends Record, OUT extends Record> ComposableWorkflowHandle<IN, OUT>
        resolve(String workflowId, String version)
        throws NoSuchWorkflowException, AmbiguousVersionException;
}

// Location: src/org/yawlfoundation/yawl/composition/CompositionQuery.java
public record CompositionQuery(
    Optional<String> domain,
    Optional<List<String>> tags,
    Optional<Duration> maxDuration,
    Optional<BigDecimal> maxCostPer,
    Optional<Integer> minSuccessRate
) {
}

// Location: src/org/yawlfoundation/yawl/composition/TransitionComposition.java
public interface TransitionComposition {
    <IN extends Record, OUT extends Record> void compose(
        String transitionId,
        ComposableWorkflowHandle<IN, OUT> workflow,
        Function<WorkflowCase, IN> inputMapping,
        BiFunction<OUT, WorkflowCase, WorkflowCase> outputMapping
    ) throws CompositionException;

    void attachCompensation(
        String transitionId,
        ComposableWorkflowHandle<?, ?> compensation
    ) throws CompositionException;
}

// Location: src/org/yawlfoundation/yawl/composition/ComposedWorkflowExecution.java
public interface ComposedWorkflowExecution {
    YIdentifier executeComposed(
        YIdentifier caseId,
        String transitionId
    ) throws CompositionException;

    void waitForComposed(YIdentifier parentCaseId, YIdentifier subCaseId)
        throws InterruptedException;

    List<ComposedWorkflowInfo> getSubWorkflows(YIdentifier caseId);
}

public record ComposedWorkflowInfo(
    YIdentifier parentCaseId,
    YIdentifier subCaseId,
    String workflowId,
    String transitionId,
    Instant startedAt,
    Optional<Instant> completedAt,
    String status                          // RUNNING, COMPLETED, FAILED
) {
}
```

---

## Innovation 2: Distributed Agent Mesh Protocol

### Core Interfaces

```java
// Location: src/org/yawlfoundation/yawl/mesh/MeshNode.java
public sealed interface MeshNode permits LocalMeshNode, RemoteMeshNode {
    String nodeId();
    String region();
}

public record LocalMeshNode(
    String nodeId,
    String region,
    Duration avgLatency,
    BigDecimal costPerHour,
    int capacityPercent,
    WorkflowEventBus eventBus
) implements MeshNode {
}

public record RemoteMeshNode(
    String nodeId,
    String region,
    URI healthCheckEndpoint,
    Instant lastHeartbeat
) implements MeshNode {
}

// Location: src/org/yawlfoundation/yawl/mesh/WorkflowMeshTopology.java
public interface WorkflowMeshTopology {
    void announceLocal(LocalMeshNode local) throws MeshException;

    List<MeshNode> discoverPeers() throws MeshException;

    MeshNode selectNodeForTask(
        YTask task,
        Optional<String> affinity,
        Optional<Duration> maxLatency
    ) throws MeshRoutingException;

    MeshNode selectNodeForCase(String specId, Element inputData)
        throws MeshRoutingException;
}

// Location: src/org/yawlfoundation/yawl/mesh/MeshWorkDistributor.java
public interface MeshWorkDistributor {
    YIdentifier launchCaseOnMesh(
        String specId,
        Element inputData
    ) throws MeshRoutingException;

    void migrateWorkItem(
        String taskInstanceId,
        MeshNode targetNode
    ) throws MeshMigrationException;

    void failoverCase(
        YIdentifier caseId,
        String failedNodeId,
        String replicaNodeId
    ) throws MeshException;
}

// Location: src/org/yawlfoundation/yawl/mesh/MeshOrchestration.java
public interface MeshOrchestration {
    List<MeshNode> orchestrateAndSplit(
        int branchCount,
        List<Optional<MeshNode>> nodes
    ) throws MeshException;

    void orchestrateAndJoin(
        String joiningNodeId,
        List<String> branchNodeIds
    ) throws MeshException;
}

// Location: src/org/yawlfoundation/yawl/mesh/MeshRoutingPolicy.java
public interface MeshRoutingPolicy {
    MeshNode selectNode(YTask task, WorkflowMeshTopology topology)
        throws MeshRoutingException;

    static MeshRoutingPolicy capacityOptimized() {
        return (task, topology) -> topology.discoverPeers().stream()
            .filter(n -> n instanceof RemoteMeshNode r &&
                    r.capacityPercent() < 80)
            .min(Comparator.comparingInt(n -> ((RemoteMeshNode)n).capacityPercent()))
            .orElseThrow(() -> new MeshRoutingException("No capacity"));
    }

    static MeshRoutingPolicy complianceConstrained(String region) {
        return (task, topology) -> topology.selectNodeForTask(
            task, Optional.of(region), Optional.empty()
        );
    }

    static MeshRoutingPolicy latencyOptimized() {
        return (task, topology) -> topology.discoverPeers().stream()
            .min(Comparator.comparing(n ->
                n instanceof RemoteMeshNode r ? r.avgLatency() : Duration.ZERO
            ))
            .orElseThrow(() -> new MeshRoutingException("No nodes"));
    }
}
```

---

## Innovation 3: Real-time Workflow Optimization Engine

### Core Interfaces

```java
// Location: src/org/yawlfoundation/yawl/optimization/WorkflowOptimizer.java
public interface WorkflowOptimizer {
    void observe(WorkflowEvent event);

    WorkflowExecutionStats getStats(String workflowId);

    List<OptimizationRecommendation> getRecommendations(String workflowId);

    SLAPrediction predictSLA(YIdentifier caseId, Duration targetSla);

    List<AnomalyAlert> detectAnomalies(YIdentifier caseId);
}

// Location: src/org/yawlfoundation/yawl/optimization/OptimizationRecommendation.java
public sealed interface OptimizationRecommendation
    permits PathOptimization, SuspicionAlert, CostOptimization, AnomalyAlert {
}

public record PathOptimization(
    String workflowId,
    List<String> currentPath,
    List<String> recommendedPath,
    Duration estimatedTimeSaving,
    double confidence
) implements OptimizationRecommendation {
}

public record SuspicionAlert(
    String caseId,
    String taskId,
    String reason,
    Duration recommendedEscalation
) implements OptimizationRecommendation {
}

public record CostOptimization(
    String workflowId,
    String expensiveTask,
    String cheaperAlternative,
    BigDecimal estimatedSavings,
    double confidence
) implements OptimizationRecommendation {
}

public record AnomalyAlert(
    String caseId,
    String anomalyType,
    String description,
    Severity severity
) implements OptimizationRecommendation {
}

public enum Severity {
    INFO, WARNING, CRITICAL
}

// Location: src/org/yawlfoundation/yawl/optimization/OptimizationApplier.java
public interface OptimizationApplier {
    String applyPathOptimization(PathOptimization optimization)
        throws OptimizationException;

    void applyCostOptimization(CostOptimization costOpt)
        throws OptimizationException;

    void attachAnomalyEscalation(
        String workflowId,
        String anomalyType,
        ComposableWorkflowHandle<?, ?> escalationWorkflow
    ) throws OptimizationException;
}

// Location: src/org/yawlfoundation/yawl/optimization/SLAPrediction.java
public record SLAPrediction(
    Duration estimatedRemainingTime,
    double slaComplianceProbability,
    List<RiskFactor> riskFactors
) {
}

public record RiskFactor(
    String factor,
    double probabilityImpact
) {
}

public record WorkflowExecutionStats(
    String workflowId,
    int executionCount,
    Duration medianDuration,
    Duration p95Duration,
    Duration p99Duration,
    BigDecimal medianCost,
    double successRate,
    Map<String, TaskStats> taskStatistics
) {
}

public record TaskStats(
    String taskId,
    int executionCount,
    Duration medianDuration,
    Duration p95Duration,
    List<String> frequentSuccessors,
    double failureRate,
    List<String> frequentFailureReasons
) {
}
```

---

## Innovation 4: Cross-Workflow State Sharing

### Core Interfaces

```java
// Location: src/org/yawlfoundation/yawl/context/DistributedContext.java
public interface DistributedContext {
    Optional<Element> read(String key);

    void write(String key, Element value, Optional<Duration> ttl)
        throws DistributedContextException;

    void watch(String key, Consumer<DistributedVariable> callback);

    boolean compareAndSwap(String key, Element expected, Element newValue)
        throws DistributedContextException;

    long increment(String key) throws DistributedContextException;
}

// Location: src/org/yawlfoundation/yawl/context/DistributedVariable.java
public record DistributedVariable(
    String key,
    Element value,
    long version,
    Instant lastModified,
    String lastModifiedBy,
    Duration ttl,
    Set<String> readers,
    Set<String> writers
) {
}

// Location: src/org/yawlfoundation/yawl/context/CrossCaseCondition.java
public interface CrossCaseCondition {
    static Predicate<DistributedContext> waitForMilestone(
        String milestoneCase,
        String milestoneName
    ) {
        return ctx -> {
            Optional<Element> milestone = ctx.read(
                "case:" + milestoneCase + ":milestone:" + milestoneName
            );
            return milestone.map(m -> Boolean.parseBoolean(m.getValue()))
                .orElse(false);
        };
    }

    static Predicate<DistributedContext> waitForCounter(
        String counterKey,
        long threshold
    ) {
        return ctx -> {
            Optional<Element> count = ctx.read(counterKey);
            return count.map(c -> Long.parseLong(c.getValue()) >= threshold)
                .orElse(false);
        };
    }

    static Predicate<DistributedContext> waitForGroupCompletion(
        String caseGroup
    ) {
        return ctx -> {
            Optional<Element> status = ctx.read(
                "group:" + caseGroup + ":status"
            );
            return status.map(s -> s.getValue().equals("complete"))
                .orElse(false);
        };
    }
}

// Location: src/org/yawlfoundation/yawl/context/CrossCaseTransitions.java
public interface CrossCaseTransitions {
    void addCrossCondition(String transitionId, Predicate<DistributedContext> condition)
        throws CrossCaseException;

    void gateTaskByFlag(String taskId, String flagKey)
        throws CrossCaseException;

    void gateTaskByCounter(String taskId, String counterKey, long threshold)
        throws CrossCaseException;
}

// Location: src/org/yawlfoundation/yawl/context/DistributedContextReplicator.java
public interface DistributedContextReplicator {
    void publishUpdate(DistributedVariable variable)
        throws ReplicationException;

    void subscribeToUpdates(Consumer<DistributedVariable> callback);

    Duration getReplicationLag(String key);

    byte[] snapshot();

    void restore(byte[] snapshot) throws ReplicationException;
}

// Location: src/org/yawlfoundation/yawl/context/DistributedLock.java
public interface DistributedLock {
    Optional<LockHandle> acquire(String lockKey, Duration ttl)
        throws DistributedContextException;

    void release(LockHandle lock) throws DistributedContextException;

    <T> T withLock(String lockKey, Duration ttl, Callable<T> action)
        throws DistributedContextException, Exception;
}

public record LockHandle(
    String lockKey,
    String holderId,
    Instant acquiredAt,
    Instant expiresAt
) {
}
```

---

## Innovation 5: Agent Capability Marketplace with Economic Signaling

### Core Interfaces

```java
// Location: src/org/yawlfoundation/yawl/marketplace/AgentPricing.java
public sealed interface AgentPricing
    permits FixedPrice, PercentagePrice, BiddingPrice {
}

public record FixedPrice(
    BigDecimal pricePerTask,
    Optional<BigDecimal> minPrice,
    Optional<BigDecimal> maxPrice
) implements AgentPricing {
}

public record PercentagePrice(
    BigDecimal percentageOfTaskValue,
    Optional<BigDecimal> cap
) implements AgentPricing {
}

public record BiddingPrice(
    BigDecimal basePrice,
    int maxBidsPerPeriod,
    Optional<String> preferredWorkloadType
) implements AgentPricing {
}

// Location: src/org/yawlfoundation/yawl/marketplace/AgentReputation.java
public record AgentReputation(
    String agentId,
    double completionSuccessRate,
    Duration averageTaskDuration,
    Duration p95TaskDuration,
    int totalTasksCompleted,
    BigDecimal totalRevenueEarned,
    double averageCustomerRating,
    int slaBreachCount,
    long lastUpdated
) {
}

// Location: src/org/yawlfoundation/yawl/marketplace/CostAwareTaskAssignment.java
public record TaskCostModel(
    String taskId,
    BigDecimal estimatedValue,
    Duration estimatedDuration,
    List<String> preferredAgentIds,
    Optional<Double> minSuccessRate,
    Optional<BigDecimal> maxCostPerTask
) {
}

public record AgentBid(
    String agentId,
    String taskId,
    BigDecimal bidPrice,
    Duration estimatedDuration,
    AgentReputation agentReputation,
    Instant bidValidUntil
) {
}

public interface CostAwareTaskAssignment {
    CostAwareAgentInfo getAgentInfo(String agentId);

    BigDecimal evaluateCost(String taskId, String agentId)
        throws TaskAssignmentException;

    String selectCheapestQualifiedAgent(TaskCostModel task)
        throws TaskAssignmentException;

    String selectMostReliableAgent(TaskCostModel task)
        throws TaskAssignmentException;

    List<AgentBid> auctionTask(TaskCostModel task, int maxBidsWanted)
        throws TaskAssignmentException;

    TaskAssignmentConfirmation acceptBid(AgentBid bid)
        throws TaskAssignmentException;
}

// Location: src/org/yawlfoundation/yawl/marketplace/WorkflowCostTracking.java
public interface WorkflowCostTracking {
    void recordTaskExecution(
        YIdentifier caseId,
        String taskId,
        String agentId,
        BigDecimal actualCost,
        Duration actualDuration,
        boolean slaMet
    ) throws CostTrackingException;

    BigDecimal getCaseCost(YIdentifier caseId);

    Map<String, BigDecimal> getCostBreakdown(YIdentifier caseId);

    Map<String, BigDecimal> getCostByAgent(String workflowId);

    BigDecimal estimateCaseCost(String specId)
        throws CostTrackingException;
}

// Location: src/org/yawlfoundation/yawl/marketplace/DynamicPricingModel.java
public interface DynamicPricingModel {
    BigDecimal computeDynamicPrice(
        String agentId,
        int queueLength,
        int maxQueueCapacity
    );
}

// Location: src/org/yawlfoundation/yawl/marketplace/AgentRevenueTracking.java
public record AgentEarningsSummary(
    String agentId,
    BigDecimal totalEarned,
    BigDecimal totalWithBonuses,
    int tasksCompleted,
    BigDecimal averageEarningsPerTask,
    int activeBids,
    Duration currentMonthEarnings
) {
}

public interface AgentRevenueTracking {
    void recordCompletion(
        String agentId,
        String taskId,
        BigDecimal revenue,
        Map<String, BigDecimal> bonusesApplied
    ) throws RevenueTrackingException;

    AgentEarningsSummary getEarnings(String agentId);

    void applySLABonus(String agentId, String taskId, double bonusPercent)
        throws RevenueTrackingException;

    void applyQualityPenalty(String agentId, String taskId, double penaltyPercent)
        throws RevenueTrackingException;
}

public record CostAwareAgentInfo(
    String agentId,
    AgentPricing pricing,
    AgentReputation reputation,
    BigDecimal dynamicPrice,
    int taskQueueLength,
    boolean acceptingBids
) {
}

public record TaskAssignmentConfirmation(
    String assignmentId,
    String agentId,
    String taskId,
    BigDecimal agreedPrice,
    Duration estimatedDuration,
    Instant validUntil
) {
}
```

---

## Exception Hierarchy

```java
// Common exceptions for all innovations
public class CompositionException extends Exception { }
public class MeshException extends Exception { }
public class MeshRoutingException extends MeshException { }
public class MeshMigrationException extends MeshException { }
public class OptimizationException extends Exception { }
public class DistributedContextException extends Exception { }
public class CrossCaseException extends DistributedContextException { }
public class ReplicationException extends DistributedContextException { }
public class TaskAssignmentException extends Exception { }
public class CostTrackingException extends Exception { }
public class RevenueTrackingException extends Exception { }

// Specific exceptions
public class NoSuchWorkflowException extends CompositionException { }
public class AmbiguousVersionException extends CompositionException { }
public class ConcurrentModificationException extends DistributedContextException { }
```

---

## Implementation Order (by Dependency)

1. **Composition** (no dependencies)
2. **Mesh** (light: depends on existing event bus)
3. **Distributed Context** (no dependencies)
4. **Optimization** (depends on Composition for sub-workflows, event bus for learning)
5. **Marketplace** (depends on existing AgentMarketplace + Optimization for recommendations)

## Package Structure

```
src/org/yawlfoundation/yawl/
├── composition/             (5 interfaces + records)
├── mesh/                    (5 interfaces + records)
├── optimization/            (5 interfaces + records)
├── context/                 (5 interfaces + records)
└── marketplace/             (6 interfaces + records + enums)
```

Total: 23 public types across 26 files (some interfaces share files)
