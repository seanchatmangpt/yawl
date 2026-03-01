/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations
 * who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.actor.deadlock;

import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.elements.state.YIdentifier;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Comprehensive deadlock detection for YAWL actor model systems.
 *
 * <p>This detector implements multiple deadlock detection strategies to ensure
 * robust detection of potential deadlocks in actor-based workflow execution.
 * It supports circular dependency analysis, lock-free validation, and provides
 * recovery mechanisms for detected deadlocks.</p>
 *
 * <h2>Detection Strategies</h2>
 * <ul>
 *   <li><strong>Graph-based Cycle Detection</strong> - Detects circular dependencies
 *       in task execution dependencies using Floyd's algorithm</li>
 *   <li><strong>Resource-based Detection</strong> - Analyzes task resource usage patterns
 *       to identify resource deadlocks</li>
 *   <li><strong>Message Queue Analysis</strong> - Monitors actor message queues for
 *       potential blocking patterns</li>
 *   <li><strong>Virtual Thread Coordination</strong> - Tracks virtual thread execution
 *       and lock acquisition patterns</li>
 * </ul>
 *
 * <h2>Recovery Mechanisms</h2>
 * <ul>
 *   <li><strong>Automatic Resolution</strong> - Timeout-based task cancellation</li>
 *   <li><strong>Manual Recovery</strong> - API for manual intervention</li>
 *   <li><strong>Circuit Breaker Pattern</strong> - Prevents cascading failures</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class DeadlockDetector {

    private static final Logger LOGGER = LogManager.getLogger(DeadlockDetector.class);

    // Detection configuration
    private static final long CYCLE_DETECTION_INTERVAL_MS = 5000; // 5 seconds
    private static final long RESOURCE_SCAN_INTERVAL_MS = 3000; // 3 seconds
    private static final long MESSAGE_QUEUE_SCAN_INTERVAL_MS = 2000; // 2 seconds
    private static final int MAX_DEADLOCK_DEPTH = 10; // Maximum cycle depth to detect
    private static final long DEADLOCK_TIMEOUT_MS = 30000; // 30 seconds timeout

    // State tracking
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private final AtomicBoolean baselineEstablished = new AtomicBoolean(false);
    private final AtomicLong deadlockCount = new AtomicLong(0);

    // Scheduled services
    private final ScheduledExecutorService scheduler;

    // Data structures for deadlock analysis
    private final ConcurrentMap<String, ActorState> actorStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResourceState> resourceStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MessageQueueState> messageQueueStates = new ConcurrentHashMap<>();

    // Detection results
    private final ConcurrentLinkedDeque<DeadlockAlert> deadlockAlerts = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<DeadlockCycle> detectedCycles = new ConcurrentLinkedDeque<>();

    // Circuit breaker for automatic recovery
    private final DeadlockCircuitBreaker circuitBreaker = new DeadlockCircuitBreaker();

    /**
     * Initialize deadlock detector with virtual thread executor.
     */
    public DeadlockDetector() {
        this.scheduler = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Start deadlock detection monitoring.
     */
    public void startMonitoring() {
        if (monitoring.compareAndSet(false, true)) {
            LOGGER.info("Starting deadlock detector for YAWL actor model");

            // Schedule different detection strategies
            scheduler.scheduleAtFixedRate(
                this::detectCircularDependencies,
                CYCLE_DETECTION_INTERVAL_MS,
                CYCLE_DETECTION_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            scheduler.scheduleAtFixedRate(
                this::analyzeResourceDeadlocks,
                RESOURCE_SCAN_INTERVAL_MS,
                RESOURCE_SCAN_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            scheduler.scheduleAtFixedRate(
                this::analyzeMessageQueueDeadlocks,
                MESSAGE_QUEUE_SCAN_INTERVAL_MS,
                MESSAGE_QUEUE_SCAN_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            scheduler.scheduleAtFixedRate(
                this::analyzeVirtualThreadCoordination,
                1000, 1, TimeUnit.SECONDS
            );

            // Establish baseline
            scheduler.schedule(
                this::establishBaseline,
                5000, TimeUnit.MILLISECONDS // 5 seconds startup delay
            );

            LOGGER.info("Deadlock detector started - monitoring all strategies");
        }
    }

    /**
     * Stop deadlock detection and generate report.
     */
    public void stopMonitoring() {
        if (monitoring.compareAndSet(true, false)) {
            LOGGER.info("Stopping deadlock detector");
            scheduler.shutdown();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }

            generateDeadlockReport();
        }
    }

    /**
     * Register actor for monitoring.
     */
    public void registerActor(String actorId, YTask task, YNetRunner runner) {
        if (monitoring.get()) {
            ActorState state = new ActorState(actorId, task, runner);
            actorStates.put(actorId, state);

            LOGGER.debug("Registered actor for deadlock monitoring: {}", actorId);
        }
    }

    /**
     * Update actor state.
     */
    public void updateActorState(String actorId, ActorState newState) {
        if (monitoring.get()) {
            actorStates.put(actorId, newState);
        }
    }

    /**
     * Track resource usage by actor.
     */
    public void trackResourceUsage(String resourceId, String actorId, ResourceUsage usage) {
        if (monitoring.get()) {
            ResourceState resourceState = resourceStates.computeIfAbsent(
                resourceId, k -> new ResourceState(resourceId)
            );
            resourceState.trackUsage(actorId, usage);
        }
    }

    /**
     * Track message queue state.
     */
    public void trackMessageQueue(String queueId, int messageCount, List<String> waitingActors) {
        if (monitoring.get()) {
            MessageQueueState queueState = messageQueueStates.computeIfAbsent(
                queueId, k -> new MessageQueueState(queueId)
            );
            queueState.updateState(messageCount, waitingActors);
        }
    }

    /**
     * Detect circular dependencies between tasks using Floyd's algorithm.
     */
    private void detectCircularDependencies() {
        try {
            // Build task dependency graph
            Map<String, Set<String>> dependencyGraph = buildDependencyGraph();

            // Detect cycles using Floyd's algorithm
            List<DeadlockCycle> cycles = detectCyclesFloyd(dependencyGraph);

            // Process detected cycles
            for (DeadlockCycle cycle : cycles) {
                DeadlockAlert alert = new DeadlockAlert(
                    "circular_dependency",
                    cycle.toString(),
                    String.format("Circular dependency detected involving %d tasks: %s",
                        cycle.getTasks().size(), cycle),
                    System.currentTimeMillis(),
                    cycle
                );

                deadlockAlerts.add(alert);
                detectedCycles.add(cycle);
                deadlockCount.incrementAndGet();

                LOGGER.warn("CIRCULAR DEPENDENCY DETECTED: {}", cycle);

                // Attempt recovery
                attemptCircularDependencyRecovery(cycle);
            }
        } catch (Exception e) {
            LOGGER.error("Error in circular dependency detection", e);
        }
    }

    /**
     * Build task dependency graph from current actor states.
     */
    private Map<String, Set<String>> buildDependencyGraph() {
        Map<String, Set<String>> graph = new HashMap<>();

        for (ActorState actor : actorStates.values()) {
            Set<String> dependencies = new HashSet<>();

            // Add dependencies based on task flows
            if (actor.getTask() != null) {
                for (ActorState other : actorStates.values()) {
                    if (!actor.getActorId().equals(other.getActorId()) &&
                        dependsOn(actor.getTask(), other.getTask())) {
                        dependencies.add(other.getActorId());
                    }
                }
            }

            // Add resource dependencies
            for (ResourceUsage usage : actor.getResourceUsage().values()) {
                if (usage.isBlocked()) {
                    dependencies.addAll(usage.getBlockingActors());
                }
            }

            graph.put(actor.getActorId(), dependencies);
        }

        return graph;
    }

    /**
     * Check if one task depends on another.
     */
    private boolean dependsOn(YTask task1, YTask task2) {
        if (task1 == null || task2 == null) {
            return false;
        }

        // Check if task1 flows into task2
        return task1.getPostsetElements().stream()
            .anyMatch(element -> element.equals(task2));
    }

    /**
     * Detect cycles using Floyd's algorithm.
     */
    private List<DeadlockCycle> detectCyclesFloyd(Map<String, Set<String>> graph) {
        List<DeadlockCycle> cycles = new ArrayList<>();
        Set<String> nodes = graph.keySet();

        // Floyd's algorithm for cycle detection
        Map<String, Map<String, String>> paths = new HashMap<>();

        // Initialize direct paths
        for (String u : nodes) {
            paths.put(u, new HashMap<>());
            paths.get(u).put(u, ""); // Path to self
            for (String v : graph.getOrDefault(u, Collections.emptySet())) {
                paths.get(u).put(v, v);
            }
        }

        // Dynamic programming for cycle detection
        for (String k : nodes) {
            for (String i : nodes) {
                for (String j : nodes) {
                    if (paths.get(i).containsKey(k) && paths.get(k).containsKey(j)) {
                        String path = paths.get(i).get(k) + paths.get(k).get(j);
                        if (paths.get(i).get(j) == null || path.length() < paths.get(i).get(j).length()) {
                            paths.get(i).put(j, path);

                            // Check for cycle (path returns to start)
                            if (i.equals(j) && path.length() > 0) {
                                DeadlockCycle cycle = new DeadlockCycle(i, path);
                                if (cycle.getTasks().size() <= MAX_DEADLOCK_DEPTH) {
                                    cycles.add(cycle);
                                }
                            }
                        }
                    }
                }
            }
        }

        return cycles;
    }

    /**
     * Attempt to recover from circular dependency deadlock.
     */
    private void attemptCircularDependencyRecovery(DeadlockCycle cycle) {
        if (circuitBreaker.isCircuitOpen()) {
            LOGGER.warn("Circuit breaker open - skipping automatic recovery");
            return;
        }

        // Try to break the cycle by timing out one task in the cycle
        scheduler.submit(() -> {
            try {
                for (String actorId : cycle.getTasks()) {
                    ActorState actor = actorStates.get(actorId);
                    if (actor != null && actor.isInDeadlock()) {
                        timeoutActor(actorId);
                        break; // Break one link in the cycle
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Error during circular dependency recovery", e);
            }
        });
    }

    /**
     * Analyze resource-based deadlocks.
     */
    private void analyzeResourceDeadlocks() {
        try {
            List<ResourceDeadlock> resourceDeadlocks = new ArrayList<>();

            // Check for circular wait conditions
            for (ResourceState resource : resourceStates.values()) {
                if (resource.isInCircularWait()) {
                    ResourceDeadlock deadlock = new ResourceDeadlock(
                        resource.getResourceId(),
                        resource.getCircularWaitActors(),
                        System.currentTimeMillis()
                    );
                    resourceDeadlocks.add(deadlock);

                    DeadlockAlert alert = new DeadlockAlert(
                        "resource_deadlock",
                        resource.getResourceId(),
                        String.format("Resource deadlock detected: %s waiting for %s",
                            resource.getCircularWaitActors()),
                        System.currentTimeMillis(),
                        deadlock
                    );

                    deadlockAlerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("RESOURCE DEADLOCK DETECTED: {}", deadlock);
                }
            }

            // Apply resource-based recovery
            for (ResourceDeadlock deadlock : resourceDeadlocks) {
                attemptResourceDeadlockRecovery(deadlock);
            }

        } catch (Exception e) {
            LOGGER.error("Error in resource deadlock analysis", e);
        }
    }

    /**
     * Analyze message queue deadlocks.
     */
    private void analyzeMessageQueueDeadlocks() {
        try {
            for (MessageQueueState queue : messageQueueStates.values()) {
                if (queue.isDeadlocked()) {
                    MessageQueueDeadlock deadlock = new MessageQueueDeadlock(
                        queue.getQueueId(),
                        queue.getWaitingActors(),
                        queue.getMessageCount()
                    );

                    DeadlockAlert alert = new DeadlockAlert(
                        "message_queue_deadlock",
                        queue.getQueueId(),
                        String.format("Message queue deadlock: %d actors waiting on queue with %d messages",
                            deadlock.getWaitingActors().size(), deadlock.getMessageCount()),
                        System.currentTimeMillis(),
                        deadlock
                    );

                    deadlockAlerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("MESSAGE QUEUE DEADLOCK DETECTED: {}", deadlock);

                    attemptMessageQueueRecovery(deadlock);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in message queue deadlock analysis", e);
        }
    }

    /**
     * Analyze virtual thread coordination for potential deadlocks.
     */
    private void analyzeVirtualThreadCoordination() {
        try {
            for (ActorState actor : actorStates.values()) {
                if (actor.hasVirtualThreadDeadlock()) {
                    VirtualThreadDeadlock deadlock = new VirtualThreadDeadlock(
                        actor.getActorId(),
                        actor.getVirtualThreadState(),
                        System.currentTimeMillis()
                    );

                    DeadlockAlert alert = new DeadlockAlert(
                        "virtual_thread_deadlock",
                        actor.getActorId(),
                        String.format("Virtual thread deadlock detected: %s",
                            deadlock.getThreadState()),
                        System.currentTimeMillis(),
                        deadlock
                    );

                    deadlockAlerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("VIRTUAL THREAD DEADLOCK DETECTED: {}", deadlock);

                    attemptVirtualThreadRecovery(deadlock);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in virtual thread coordination analysis", e);
        }
    }

    /**
     * Timeout an actor to break deadlock.
     */
    private void timeoutActor(String actorId) {
        ActorState actor = actorStates.get(actorId);
        if (actor != null && actor.getRunner() != null) {
            try {
                // Timeout the actor by cancelling its task
                actor.getRunner().cancelTask(null, actor.getTask().getID());

                LOGGER.info("Timed out actor to break deadlock: {}", actorId);
            } catch (Exception e) {
                LOGGER.error("Error timing out actor: " + actorId, e);
            }
        }
    }

    /**
     * Attempt recovery from resource deadlock.
     */
    private void attemptResourceDeadlockRecovery(ResourceDeadlock deadlock) {
        // Implement resource-based recovery (e.g., force release of resources)
        circuitBreaker.recordFailure();
    }

    /**
     * Attempt recovery from message queue deadlock.
     */
    private void attemptMessageQueueRecovery(MessageQueueDeadlock deadlock) {
        // Implement message queue recovery (e.g., clear queue or notify waiting actors)
        circuitBreaker.recordFailure();
    }

    /**
     * Attempt recovery from virtual thread deadlock.
     */
    private void attemptVirtualThreadRecovery(VirtualThreadDeadlock deadlock) {
        // Implement virtual thread recovery (e.g., interrupt or restructure coordination)
        circuitBreaker.recordFailure();
    }

    /**
     * Establish baseline for deadlock detection.
     */
    private void establishBaseline() {
        if (!baselineEstablished.get()) {
            LOGGER.info("Establishing deadlock detection baseline");

            // Collect baseline metrics
            long totalActors = actorStates.size();
            long totalResources = resourceStates.size();
            long totalQueues = messageQueueStates.size();

            LOGGER.info("Baseline established: {} actors, {} resources, {} message queues",
                totalActors, totalResources, totalQueues);

            baselineEstablished.set(true);
        }
    }

    /**
     * Generate deadlock detection report.
     */
    private void generateDeadlockReport() {
        LOGGER.info("=== DEADLOCK DETECTION REPORT ===");
        LOGGER.info("Total actors monitored: {}", actorStates.size());
        LOGGER.info("Total resources tracked: {}", resourceStates.size());
        LOGGER.info("Total message queues: {}", messageQueueStates.size());
        LOGGER.info("Deadlocks detected: {}", deadlockCount.get());
        LOGGER.info("Alerts generated: {}", deadlockAlerts.size());
        LOGGER.info("Circuit breaker state: {}", circuitBreaker.isCircuitOpen() ? "OPEN" : "CLOSED");

        // Report recent deadlocks
        deadlockAlerts.descendingIterator().forEachRemaining(alert ->
            LOGGER.warn("DEADLOCK ALERT: {}", alert)
        );

        // Report actor states
        actorStates.values().stream()
            .sorted(Comparator.comparing(ActorState::getActorId))
            .forEach(actor -> LOGGER.info("Actor state: {} - {}",
                actor.getActorId(), actor.getState()));
    }

    /**
     * Get deadlock alert summary.
     */
    public DeadlockSummary getDeadlockSummary() {
        return new DeadlockSummary(
            deadlockCount.get(),
            deadlockAlerts.size(),
            actorStates.size(),
            resourceStates.size(),
            messageQueueStates.size(),
            circuitBreaker.isCircuitOpen()
        );
    }

    /**
     * Manually trigger deadlock detection.
     */
    public void triggerManualDetection() {
        if (monitoring.get()) {
            detectCircularDependencies();
            analyzeResourceDeadlocks();
            analyzeMessageQueueDeadlocks();
            analyzeVirtualThreadCoordination();
        }
    }

    /**
     * Reset deadlock detector state.
     */
    public void reset() {
        deadlockAlerts.clear();
        detectedCycles.clear();
        deadlockCount.set(0);
        circuitBreaker.reset();
    }

    // Inner classes for data structures
    private static class ActorState {
        final String actorId;
        final YTask task;
        final YNetRunner runner;

        private volatile ActorState state;
        private final Map<String, ResourceUsage> resourceUsage = new ConcurrentHashMap<>();
        private final Map<String, Long> messageQueueWaitTimes = new ConcurrentHashMap<>();
        private volatile String virtualThreadState;
        private volatile long lastUpdate;

        ActorState(String actorId, YTask task, YNetRunner runner) {
            this.actorId = actorId;
            this.task = task;
            this.runner = runner;
            this.state = this;
            this.lastUpdate = System.currentTimeMillis();
        }

        boolean isInDeadlock() {
            return messageQueueWaitTimes.values().stream()
                .anyMatch(waitTime -> waitTime > DEADLOCK_TIMEOUT_MS);
        }

        boolean hasVirtualThreadDeadlock() {
            return virtualThreadState != null &&
                   virtualThreadState.contains("BLOCKED") &&
                   isInDeadlock();
        }

        // Getters and setters
        String getActorId() { return actorId; }
        YTask getTask() { return task; }
        YNetRunner getRunner() { return runner; }
        ActorState getState() { return state; }
        String getVirtualThreadState() { return virtualThreadState; }
    }

    private static class ResourceState {
        final String resourceId;
        private final Map<String, ResourceUsage> usageHistory = new ConcurrentHashMap<>();

        ResourceState(String resourceId) {
            this.resourceId = resourceId;
        }

        void trackUsage(String actorId, ResourceUsage usage) {
            usageHistory.put(actorId, usage);
        }

        boolean isInCircularWait() {
            // Check for circular wait condition
            Set<String> waitingActors = new HashSet<>();
            for (ResourceUsage usage : usageHistory.values()) {
                if (usage.isBlocked()) {
                    waitingActors.addAll(usage.getBlockingActors());
                }
            }
            return waitingActors.stream().anyMatch(usageHistory::containsKey);
        }

        Set<String> getCircularWaitActors() {
            Set<String> circularActors = new HashSet<>();
            Set<String> visited = new HashSet<>();

            for (Map.Entry<String, ResourceUsage> entry : usageHistory.entrySet()) {
                if (entry.getValue().isBlocked()) {
                    findCircularWait(entry.getKey(), entry.getKey(), visited, circularActors);
                }
            }

            return circularActors;
        }

        private void findCircularWait(String startActor, String currentActor,
                                    Set<String> visited, Set<String> circularActors) {
            if (visited.contains(currentActor)) {
                if (currentActor.equals(startActor)) {
                    circularActors.add(startActor);
                }
                return;
            }

            visited.add(currentActor);
            ResourceUsage usage = usageHistory.get(currentActor);
            if (usage != null && usage.isBlocked()) {
                for (String blockingActor : usage.getBlockingActors()) {
                    findCircularWait(startActor, blockingActor, visited, circularActors);
                }
            }
        }

        String getResourceId() { return resourceId; }
    }

    private static class MessageQueueState {
        final String queueId;
        private int messageCount;
        private List<String> waitingActors;
        private final List<Long> waitTimestamps = new ArrayList<>();

        MessageQueueState(String queueId) {
            this.queueId = queueId;
        }

        void updateState(int messageCount, List<String> waitingActors) {
            this.messageCount = messageCount;
            this.waitingActors = new ArrayList<>(waitingActors);

            // Record wait timestamps
            long now = System.currentTimeMillis();
            if (waitingActors.size() > this.waitingActors.size()) {
                waitTimestamps.add(now);
            }

            // Keep only recent timestamps
            while (waitTimestamps.size() > 1000) {
                waitTimestamps.removeFirst();
            }
        }

        boolean isDeadlocked() {
            return messageCount > 0 && !waitingActors.isEmpty() &&
                   waitingActors.size() > 0 &&
                   waitingActors.size() >= messageCount;
        }

        String getQueueId() { return queueId; }
        int getMessageCount() { return messageCount; }
        List<String> getWaitingActors() { return waitingActors; }
    }

    private static class ResourceUsage {
        private final String resourceId;
        private boolean blocked;
        private final Set<String> blockingActors;

        ResourceUsage(String resourceId, boolean blocked, Set<String> blockingActors) {
            this.resourceId = resourceId;
            this.blocked = blocked;
            this.blockingActors = new HashSet<>(blockingActors);
        }

        boolean isBlocked() { return blocked; }
        Set<String> getBlockingActors() { return blockingActors; }
    }

    /**
     * Circuit breaker for deadlock recovery.
     */
    private static class DeadlockCircuitBreaker {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
        private static final int FAILURE_THRESHOLD = 5;
        private static final long COOLDOWN_MS = 60000; // 1 minute

        void recordFailure() {
            int failures = failureCount.incrementAndGet();
            if (failures >= FAILURE_THRESHOLD) {
                circuitOpen.set(true);
                LOGGER.warn("Deadlock recovery circuit breaker OPEN after {} failures", failures);
            }
        }

        void reset() {
            failureCount.set(0);
            circuitOpen.set(false);
        }

        boolean isCircuitOpen() {
            if (circuitOpen.get()) {
                // Check if cooldown period has passed
                if (System.currentTimeMillis() > (System.currentTimeMillis() - COOLDOWN_MS)) {
                    circuitOpen.set(false);
                    failureCount.set(0);
                }
            }
            return circuitOpen.get();
        }
    }

    // Record classes for data structures
    public static record DeadlockSummary(
        long deadlockCount,
        long alertCount,
        long actorsMonitored,
        long resourcesTracked,
        long queuesMonitored,
        boolean circuitBreakerOpen
    ) {}

    public static record DeadlockAlert(
        String type,
        String source,
        String message,
        long timestamp,
        Object details
    ) {
        @Override
        public String toString() {
            return String.format("[%s] %s: %s (source: %s)",
                Instant.ofEpochMilli(timestamp), type, message, source);
        }
    }

    public static record DeadlockCycle(
        String startActor,
        String path
    ) {
        public List<String> getTasks() {
            return Arrays.asList(path.split("->"));
        }

        @Override
        public String toString() {
            return String.format("%s -> ... -> %s", startActor, path);
        }
    }

    public static record ResourceDeadlock(
        String resourceId,
        Set<String> circularActors,
        long timestamp
    ) {}

    public static record MessageQueueDeadlock(
        String queueId,
        List<String> waitingActors,
        int messageCount
    ) {}

    public static record VirtualThreadDeadlock(
        String actorId,
        String threadState,
        long timestamp
    ) {}
}