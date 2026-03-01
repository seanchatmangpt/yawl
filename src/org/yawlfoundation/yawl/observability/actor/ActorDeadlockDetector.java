package org.yawlfoundation.yawl.observability.actor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced deadlock detection for YAWL actor systems.
 *
 * Detects potential deadlock scenarios by analyzing:
 * - Circular dependencies in actor interactions
 * - Long-running message processing
 * - Unbounded blocking operations
 * - Resource ordering violations
 * - Virtual thread starvation patterns
 *
 * Integration Points:
 * - ActorTracer for interaction tracking
 * - ActorHealthMetrics for performance data
 * - MeterRegistry for metrics collection
 */
public class ActorDeadlockDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActorDeadlockDetector.class);

    private final ActorTracer tracer;
    private final ActorHealthMetrics healthMetrics;
    private final Map<String, ActorInteractionGraph> interactionGraphs;
    private final Map<String, ActorPerformanceProfile> performanceProfiles;

    // Metrics
    private final Counter deadlockDetectedCounter;
    private final Timer deadlockDetectionTimer;
    private final Counter falsePositiveCounter;
    private final AtomicLong totalDeadlockChecks;

    // Configuration
    private final int maxGraphDepth = 10;
    private final Duration maxProcessingTime = Duration.ofSeconds(30);
    private final int minInteractionsForAnalysis = 5;
    private final int cycleDetectionDepth = 5;

    /**
     * Creates a new ActorDeadlockDetector instance.
     */
    public ActorDeadlockDetector(ActorTracer tracer,
                                ActorHealthMetrics healthMetrics) {
        this.tracer = tracer;
        this.healthMetrics = healthMetrics;
        this.interactionGraphs = new ConcurrentHashMap<>();
        this.performanceProfiles = new ConcurrentHashMap<>();

        // Initialize metrics
        this.deadlockDetectedCounter = Counter.builder("yawl.actor.deadlock.detected")
            .description("Number of actor deadlocks detected")
            .register(meterRegistry);

        this.deadlockDetectionTimer = Timer.builder("yawl.actor.deadlock.detection.duration")
            .description("Time spent detecting actor deadlocks")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .register(meterRegistry);

        this.falsePositiveCounter = Counter.builder("yawl.actor.deadlock.false_positive")
            .description("Number of false positive deadlock detections")
            .register(meterRegistry);

        this.totalDeadlockChecks = new AtomicLong(0);

        // Initialize metrics
        initializeMetrics();
    }

    /**
     * Initializes all metrics.
     */
    private void initializeMetrics() {
        // Monitoring metrics
        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.monitoring.actors",
                () -> performanceProfiles.size())
            .description("Number of actors currently being monitored for deadlocks")
            .register(meterRegistry);

        // Detection metrics
        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.cycles.detected",
                () -> getCycleCount())
            .description("Number of potential cycles detected")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.long_running",
                () -> getLongRunningCount())
            .description("Number of long-running operations")
            .register(meterRegistry);

        // Severity metrics
        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.severity.unknown",
                () -> getActorsBySeverity(Severity.UNKNOWN).size())
            .description("Number of actors with unknown deadlock severity")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.severity.low",
                () -> getActorsBySeverity(Severity.LOW).size())
            .description("Number of actors with low severity deadlock risk")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.severity.medium",
                () -> getActorsBySeverity(Severity.MEDIUM).size())
            .description("Number of actors with medium severity deadlock risk")
            .register(meterRegistry);

        io.micrometer.core.instrument.Gauge.builder("yawl.actor.deadlock.severity.high",
                () -> getActorsBySeverity(Severity.HIGH).size())
            .description("Number of actors with high severity deadlock risk")
            .register(meterRegistry);
    }

    /**
     * Checks for potential deadlocks in a specific actor.
     */
    public void checkForDeadlocks(String actorId) {
        long startTime = System.currentTimeMillis();
        totalDeadlockChecks.incrementAndGet();

        ActorHealthMetrics.ActorHealthStatus actor = healthMetrics.getActorHealth(actorId);
        if (actor == null) return;

        // Update performance profile
        ActorPerformanceProfile profile = performanceProfiles.computeIfAbsent(actorId,
            id -> new ActorPerformanceProfile(id));
        profile.updatePerformance(actor);

        // Update interaction graph
        updateInteractionGraph(actorId);

        // Analyze deadlock patterns
        DeadlockAnalysisResult result = analyzeDeadlockPatterns(actorId, profile);

        if (result.isDeadlockDetected()) {
            deadlockDetectedCounter.increment();
            LOGGER.warn("Potential deadlock detected for actor {}: severity={}, reason={}",
                       actorId, result.getSeverity(), result.getReason());

            // Record severity metrics
            recordSeverityMetrics(result.getSeverity());

            // Trigger alert
            triggerDeadlockAlert(actorId, result);

            // Record violation
            recordDeadlockViolation(actorId, result);
        } else if (result.isFalsePositive()) {
            falsePositiveCounter.increment();
            LOGGER.debug("False positive deadlock detection for actor {}: {}",
                        actorId, result.getReason());
        }

        deadlockDetectionTimer.record(System.currentTimeMillis() - startTime,
                                    java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Updates the interaction graph for an actor.
     */
    private void updateInteractionGraph(String actorId) {
        // Get recent spans for this actor
        Map<String, ActorTracer.ActorSpanContext> activeSpans = tracer.getActiveSpans();

        for (ActorTracer.ActorSpanContext span : activeSpans.values()) {
            if (span.getSourceActor().equals(actorId)) {
                // Add outgoing edge
                ActorInteractionGraph graph = interactionGraphs.computeIfAbsent(
                    actorId, id -> new ActorInteractionGraph(id));
                graph.addOutgoingEdge(span.getTargetActor());

                // Add edge with timing information
                long processingTime = span.getSpan().getStartEpochNanos();
                graph.addEdgeTiming(span.getTargetActor(), processingTime);
            }
        }

        // Clean up old graphs
        interactionGraphs.entrySet().removeIf(entry -> {
            if (entry.getValue().getLastUpdate().isBefore(Instant.now().minus(Duration.ofMinutes(5)))) {
                return true;
            }
            return false;
        });
    }

    /**
     * Analyzes deadlock patterns for an actor.
     */
    private DeadlockAnalysisResult analyzeDeadlockPatterns(String actorId,
                                                        ActorPerformanceProfile profile) {
        // Check for long-running processing
        if (profile.hasLongRunningOperations()) {
            return DeadlockAnalysisResult.deadlockDetected(
                Severity.HIGH,
                "Long-running processing detected"
            );
        }

        // Check for interaction cycles
        if (hasPotentialCycles(actorId)) {
            return DeadlockAnalysisResult.deadlockDetected(
                Severity.HIGH,
                "Potential deadlock cycle detected"
            );
        }

        // Check for virtual thread starvation
        if (profile.hasVirtualThreadStarvation()) {
            return DeadlockAnalysisResult.deadlockDetected(
                Severity.MEDIUM,
                "Virtual thread starvation detected"
            );
        }

        // Check for resource ordering violations
        if (hasResourceOrderingViolations(actorId)) {
            return DeadlockAnalysisResult.deadlockDetected(
                Severity.MEDIUM,
                "Resource ordering violation detected"
            );
        }

        // Check for unbounded blocking
        if (profile.hasUnboundedBlocking()) {
            return DeadlockAnalysisResult.deadlockDetected(
                Severity.LOW,
                "Unbounded blocking detected"
            );
        }

        return DeadlockAnalysisResult.noDeadlock();
    }

    /**
     * Detects potential cycles in interaction graphs.
     */
    private boolean hasPotentialCycles(String actorId) {
        ActorInteractionGraph graph = interactionGraphs.get(actorId);
        if (graph == null || graph.getInteractionCount() < minInteractionsForAnalysis) {
            return false;
        }

        // Use DFS to detect cycles
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        return hasCyclesDFS(actorId, visited, recursionStack, 0);
    }

    /**
     * DFS algorithm for cycle detection.
     */
    private boolean hasCyclesDFS(String currentActor, Set<String> visited,
                                 Set<String> recursionStack, int depth) {
        if (depth > cycleDetectionDepth) {
            return false; // Limit depth for performance
        }

        if (recursionStack.contains(currentActor)) {
            return true; // Cycle detected
        }

        if (visited.contains(currentActor)) {
            return false; // Already visited
        }

        visited.add(currentActor);
        recursionStack.add(currentActor);

        ActorInteractionGraph graph = interactionGraphs.get(currentActor);
        if (graph != null) {
            for (String neighbor : graph.getOutgoingEdges()) {
                if (hasCyclesDFS(neighbor, visited, recursionStack, depth + 1)) {
                    recursionStack.remove(currentActor);
                    return true;
                }
            }
        }

        recursionStack.remove(currentActor);
        return false;
    }

    /**
     * Checks for resource ordering violations.
     */
    private boolean hasResourceOrderingViolations(String actorId) {
        ActorInteractionGraph graph = interactionGraphs.get(actorId);
        if (graph == null) return false;

        // Check for inconsistent lock acquisition patterns
        Map<String, Long> edgeTimings = graph.getEdgeTimings();
        if (edgeTimings.size() < 3) return false;

        // Check for timing inconsistencies that might indicate ordering issues
        Long[] timings = edgeTimings.values().toArray(new Long[0]);
        for (int i = 0; i < timings.length - 1; i++) {
            if (timings[i] > timings[i + 1] * 2) { // Significant timing inconsistency
                return true;
            }
        }

        return false;
    }

    /**
     * Records severity-based metrics.
     */
    private void recordSeverityMetrics(Severity severity) {
        String severityTag = severity.toString().toLowerCase();
        meterRegistry.counter("yawl.actor.deadlock.detected.by_severity", "severity", severityTag)
            .increment();
    }

    /**
     * Triggers a deadlock alert.
     */
    private void triggerDeadlockAlert(String actorId, DeadlockAnalysisResult result) {
        // Use existing alert manager if available
        ActorObservabilityService service = ActorObservabilityService.getInstance();
        if (service != null) {
            service.triggerAlert(
                ActorAlertManager.AlertType.ACTOR_DEADLOCK_DETECTED,
                mapSeverityToAlertSeverity(result.getSeverity()),
                actorId,
                "Deadlock risk detected: " + result.getReason(),
                result.getSeverity().ordinal()
            );
        }
    }

    /**
     * Maps severity to alert severity.
     */
    private ActorAlertManager.AlertSeverity mapSeverityToAlertSeverity(Severity severity) {
        return switch (severity) {
            case HIGH -> ActorAlertManager.AlertSeverity.CRITICAL;
            case MEDIUM -> ActorAlertManager.AlertSeverity.WARNING;
            case LOW -> ActorAlertManager.AlertSeverity.INFO;
            default -> ActorAlertManager.AlertSeverity.WARNING;
        };
    }

    /**
     * Records deadlock violation.
     */
    private void recordDeadlockViolation(String actorId, DeadlockAnalysisResult result) {
        ActorObservabilityService service = ActorObservabilityService.getInstance();
        if (service != null && service.getDashboardData() != null) {
            service.getDashboardData().recordGuardViolation(
                actorId,
                "H_ACTOR_DEADLOCK",
                "Deadlock risk detected: " + result.getReason()
            );
        }
    }

    /**
     * Gets cycle count across all graphs.
     */
    private int getCycleCount() {
        int count = 0;
        for (ActorInteractionGraph graph : interactionGraphs.values()) {
            if (graph.hasCycles()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets count of long-running operations.
     */
    private int getLongRunningCount() {
        int count = 0;
        for (ActorPerformanceProfile profile : performanceProfiles.values()) {
            if (profile.hasLongRunningOperations()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets actors by severity level.
     */
    private Map<String, Severity> getActorsBySeverity(Severity severity) {
        Map<String, Severity> result = new ConcurrentHashMap<>();
        performanceProfiles.forEach((actorId, profile) -> {
            if (profile.getCurrentSeverity() == severity) {
                result.put(actorId, severity);
            }
        });
        return result;
    }

    /**
     * Deadlock severity enumeration.
     */
    public enum Severity {
        UNKNOWN, LOW, MEDIUM, HIGH
    }

    /**
     * Deadlock analysis result.
     */
    public static final class DeadlockAnalysisResult {
        private final boolean deadlockDetected;
        private final Severity severity;
        private final String reason;
        private final boolean falsePositive;

        private DeadlockAnalysisResult(boolean deadlockDetected, Severity severity, String reason,
                                    boolean falsePositive) {
            this.deadlockDetected = deadlockDetected;
            this.severity = severity;
            this.reason = reason;
            this.falsePositive = falsePositive;
        }

        public static DeadlockAnalysisResult noDeadlock() {
            return new DeadlockAnalysisResult(false, null, null, false);
        }

        public static DeadlockAnalysisResult deadlockDetected(Severity severity, String reason) {
            return new DeadlockAnalysisResult(true, severity, reason, false);
        }

        public static DeadlockAnalysisResult falsePositive(String reason) {
            return new DeadlockAnalysisResult(false, null, reason, true);
        }

        // Getters
        public boolean isDeadlockDetected() { return deadlockDetected; }
        public Severity getSeverity() { return severity; }
        public String getReason() { return reason; }
        public boolean isFalsePositive() { return falsePositive; }
    }

    /**
     * Interaction graph for deadlock detection.
     */
    public static final class ActorInteractionGraph {
        private final String actorId;
        private final Map<String, Set<String>> adjacencyList;
        private final Map<String, Long> edgeTimings;
        private Instant lastUpdate;

        public ActorInteractionGraph(String actorId) {
            this.actorId = actorId;
            this.adjacencyList = new ConcurrentHashMap<>();
            this.edgeTimings = new ConcurrentHashMap<>();
            this.lastUpdate = Instant.now();
        }

        public void addOutgoingEdge(String targetActor) {
            adjacencyList.computeIfAbsent(actorId, k -> new HashSet<>())
                        .add(targetActor);
            lastUpdate = Instant.now();
        }

        public void addEdgeTiming(String targetActor, long timing) {
            edgeTimings.put(targetActor, timing);
            lastUpdate = Instant.now();
        }

        public Set<String> getOutgoingEdges() {
            return adjacencyList.getOrDefault(actorId, Collections.emptySet());
        }

        public Map<String, Long> getEdgeTimings() {
            return new HashMap<>(edgeTimings);
        }

        public int getInteractionCount() {
            return adjacencyList.size();
        }

        public Instant getLastUpdate() { return lastUpdate; }

        public boolean hasCycles() {
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();

            return hasCyclesDFS(actorId, visited, recursionStack);
        }

        private boolean hasCyclesDFS(String current, Set<String> visited, Set<String> recursionStack) {
            if (recursionStack.contains(current)) {
                return true;
            }

            if (visited.contains(current)) {
                return false;
            }

            visited.add(current);
            recursionStack.add(current);

            Set<String> neighbors = adjacencyList.getOrDefault(current, Collections.emptySet());
            for (String neighbor : neighbors) {
                if (hasCyclesDFS(neighbor, visited, recursionStack)) {
                    recursionStack.remove(current);
                    return true;
                }
            }

            recursionStack.remove(current);
            return false;
        }
    }

    /**
     * Performance profile for deadlock detection.
     */
    public static final class ActorPerformanceProfile {
        private final String actorId;
        private final java.util.Deque<Long> processingTimes;
        private final java.util.Deque<Instant> timeStamps;
        private final java.util.Deque<Boolean> blockingOperations;
        private long maxProcessingTime;
        private long blockingOperationCount;
        private Instant lastUpdate;

        public ActorPerformanceProfile(String actorId) {
            this.actorId = actorId;
            this.processingTimes = new java.util.ArrayDeque<>(100);
            this.timeStamps = new java.util.ArrayDeque<>(100);
            this.blockingOperations = new java.util.ArrayDeque<>(100);
            this.maxProcessingTime = 0;
            this.blockingOperationCount = 0;
            this.lastUpdate = Instant.now();
        }

        public void updatePerformance(ActorHealthMetrics.ActorHealthStatus actor) {
            long processingTime = actor.getAverageProcessingTime();
            Instant now = Instant.now();

            processingTimes.add(processingTime);
            timeStamps.add(now);

            // Check if this might be a blocking operation
            boolean isBlocking = processingTime > maxProcessingTime / 2;
            blockingOperations.add(isBlocking);

            if (processingTime > maxProcessingTime) {
                maxProcessingTime = processingTime;
            }

            if (isBlocking) {
                blockingOperationCount++;
            }

            // Keep only recent history
            if (processingTimes.size() > 100) {
                processingTimes.poll();
                timeStamps.poll();
                blockingOperations.poll();
            }

            lastUpdate = now;
        }

        public boolean hasLongRunningOperations() {
            if (processingTimes.size() < 5) return false;

            // Check for operations exceeding threshold
            return processingTimes.stream()
                .anyMatch(time -> time > maxProcessingTime.toMillis());
        }

        public boolean hasVirtualThreadStarvation() {
            if (blockingOperations.size() < 10) return false;

            // Check for high ratio of blocking operations
            long blockingCount = blockingOperations.stream()
                .filter(b -> b)
                .count();

            return (double) blockingCount / blockingOperations.size() > 0.8;
        }

        public boolean hasUnboundedBlocking() {
            if (processingTimes.size() < 5) return false;

            // Check for continuously increasing blocking times
            Long[] times = processingTimes.toArray(new Long[0]);
            boolean steadilyIncreasing = true;
            for (int i = 1; i < times.length; i++) {
                if (times[i] <= times[i-1]) {
                    steadilyIncreasing = false;
                    break;
                }
            }

            return steadilyIncreasing;
        }

        public Severity getCurrentSeverity() {
            if (hasLongRunningOperations()) return Severity.HIGH;
            if (hasVirtualThreadStarvation()) return Severity.MEDIUM;
            if (hasUnboundedBlocking()) return Severity.LOW;
            return Severity.UNKNOWN;
        }
    }
}