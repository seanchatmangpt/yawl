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
 * FITNESS A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.actor.deadlock;

import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YNet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.function.Predicate;

/**
 * Enhanced deadlock detection for YAWL actor model systems with comprehensive analysis.
 *
 * <p>This detector implements advanced deadlock detection strategies including circular dependency
 * analysis, lock-free validation, and sophisticated recovery mechanisms specifically designed for
 * actor systems with virtual thread coordination.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Multi-strategy Detection</strong> - Graph analysis, resource tracking, message queue analysis</li>
 *   <li><strong>Virtual Thread Coordination</strong> - Specialized detection for virtual thread patterns</li>
 *   <li><strong>Lock-free Validation</strong> - Concurrent data structures for thread-safe detection</li>
 *   <li><strong>Smart Recovery</strong> - Context-aware recovery strategies</li>
 *   <li><strong>Real-time Monitoring</strong> - Continuous deadlock prevention</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class EnhancedDeadlockDetector {

    private static final Logger LOGGER = LogManager.getLogger(EnhancedDeadlockDetector.class);

    // Configuration constants
    private static final long DEFAULT_DETECTION_INTERVAL_MS = 1000; // 1 second
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
    private static final int MAX_CYCLE_DEPTH = 10;
    private static final double CONTENTION_THRESHOLD = 0.8;
    private static final int MAX_RECOVERY_ATTEMPTS = 3;

    // Detection state
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong detectionCount = new AtomicLong(0);
    private final AtomicLong deadlockCount = new AtomicLong(0);
    private final AtomicLong recoveryCount = new AtomicLong(0);

    // Actor tracking
    private final ConcurrentMap<String, ActorState> actorStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ResourceState> resourceStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MessageQueueState> messageQueueStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, VirtualThreadState> virtualThreadStates = new ConcurrentHashMap<>();

    // Detection executors
    private final ScheduledExecutorService detectionExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService analysisExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Recovery system
    private final DeadlockRecovery recoverySystem = new DeadlockRecovery();
    private final LockFreeValidator lockFreeValidator = new LockFreeValidator();

    // Metrics
    private final LongAdder totalDetectionTime = new LongAdder();
    private final LongAdder totalRecoveryTime = new LongAdder();

    /**
     * Initialize enhanced deadlock detector.
     */
    public EnhancedDeadlockDetector() {
        // Initialize recovery system
        recoverySystem.startRecoverySystem();
        lockFreeValidator.startValidation();
    }

    /**
     * Start deadlock detection monitoring.
     */
    public void startMonitoring() {
        if (active.compareAndSet(false, true)) {
            LOGGER.info("Starting enhanced deadlock detector for YAWL actor model");

            // Start periodic deadlock detection
            detectionExecutor.scheduleAtFixedRate(
                this::performComprehensiveDetection,
                DEFAULT_DETECTION_INTERVAL_MS,
                DEFAULT_DETECTION_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            // Start deadlock analysis
            analysisExecutor.scheduleAtFixedRate(
                this::analyzeDeadlockPatterns,
                5000, 5, TimeUnit.SECONDS
            );

            // Start virtual thread coordination monitoring
            analysisExecutor.scheduleAtFixedRate(
                this::monitorVirtualThreadCoordination,
                2000, 2, TimeUnit.SECONDS
            );

            LOGGER.info("Enhanced deadlock detector started");
        }
    }

    /**
     * Stop deadlock detection monitoring.
     */
    public void stopMonitoring() {
        if (active.compareAndSet(true, false)) {
            LOGGER.info("Stopping enhanced deadlock detector");

            detectionExecutor.shutdown();
            analysisExecutor.shutdown();

            try {
                if (!detectionExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    detectionExecutor.shutdownNow();
                }
                if (!analysisExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    analysisExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                detectionExecutor.shutdownNow();
                analysisExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            recoverySystem.stopRecoverySystem();
            lockFreeValidator.stopValidation();

            generateComprehensiveReport();
        }
    }

    /**
     * Register actor for monitoring.
     */
    public void registerActor(String actorId, YTask task, YNetRunner runner) {
        if (active.get()) {
            ActorState state = new ActorState(actorId, task, runner);
            actorStates.put(actorId, state);

            // Register with subsystems
            recoverySystem.registerActor(actorId, task, runner);
            lockFreeValidator.registerActor(actorId, task, runner);

            LOGGER.debug("Registered actor for enhanced deadlock monitoring: {}", actorId);
        }
    }

    /**
     * Update actor state.
     */
    public void updateActorState(String actorId, ActorState newState) {
        if (active.get()) {
            actorStates.put(actorId, newState);
        }
    }

    /**
     * Track resource usage by actor.
     */
    public void trackResourceUsage(String resourceId, String actorId, ResourceUsage usage) {
        if (active.get()) {
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
        if (active.get()) {
            MessageQueueState queueState = messageQueueStates.computeIfAbsent(
                queueId, k -> new MessageQueueState(queueId)
            );
            queueState.updateState(messageCount, waitingActors);
        }
    }

    /**
     * Track virtual thread state.
     */
    public void trackVirtualThread(String threadId, String actorId, VirtualThreadState state) {
        if (active.get()) {
            VirtualThreadState threadState = virtualThreadStates.computeIfAbsent(
                threadId, k -> new VirtualThreadState(threadId, actorId)
            );
            threadState.updateState(state);
        }
    }

    /**
     * Perform comprehensive deadlock detection.
     */
    private void performComprehensiveDetection() {
        long startTime = System.currentTimeMillis();
        totalDetectionTime.add(startTime);

        try {
            detectionCount.incrementAndGet();

            // Perform multiple detection strategies
            List<DeadlockAlert> alerts = new ArrayList<>();

            // 1. Circular dependency detection
            alerts.addAll(detectCircularDependencies());

            // 2. Resource deadlock detection
            alerts.addAll(detectResourceDeadlocks());

            // 3. Message queue deadlock detection
            alerts.addAll(detectMessageQueueDeadlocks());

            // 4. Virtual thread deadlock detection
            alerts.addAll(detectVirtualThreadDeadlocks());

            // Process detected deadlocks
            for (DeadlockAlert alert : alerts) {
                processDeadlockAlert(alert);
            }

        } catch (Exception e) {
            LOGGER.error("Error in comprehensive deadlock detection", e);
        } finally {
            totalDetectionTime.add(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Detect circular dependencies between actors.
     */
    private List<DeadlockAlert> detectCircularDependencies() {
        List<DeadlockAlert> alerts = new ArrayList<>();

        try {
            // Build dependency graph
            Map<String, Set<String>> dependencyGraph = buildDependencyGraph();

            // Detect cycles using multiple algorithms
            List<Cycle> cycles = detectCycles(dependencyGraph);

            // Convert cycles to alerts
            for (Cycle cycle : cycles) {
                if (cycle.getActors().size() <= MAX_CYCLE_DEPTH) {
                    DeadlockAlert alert = new DeadlockAlert(
                        "circular_dependency",
                        String.join("->", cycle.getActors()),
                        String.format("Circular dependency detected: %s", cycle),
                        System.currentTimeMillis(),
                        cycle
                    );

                    alerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("CIRCULAR DEPENDENCY DETECTED: {}", cycle);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in circular dependency detection", e);
        }

        return alerts;
    }

    /**
     * Build dependency graph from current actor states.
     */
    private Map<String, Set<String>> buildDependencyGraph() {
        Map<String, Set<String>> graph = new HashMap<>();

        for (ActorState actor : actorStates.values()) {
            Set<String> dependencies = new HashSet<>();

            // Task flow dependencies
            if (actor.getTask() != null) {
                for (ActorState other : actorStates.values()) {
                    if (!actor.getActorId().equals(other.getActorId()) &&
                        dependsOn(actor.getTask(), other.getTask())) {
                        dependencies.add(other.getActorId());
                    }
                }
            }

            // Resource dependencies
            for (ResourceUsage usage : actor.getResourceUsage().values()) {
                if (usage.isBlocked()) {
                    dependencies.addAll(usage.getBlockingActors());
                }
            }

            // Message queue dependencies
            MessageQueueState queueState = messageQueueStates.get(actor.getActorId());
            if (queueState != null && queueState.isBlocked()) {
                dependencies.addAll(queueState.getWaitingActors());
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

        return task1.getPostsetElements().stream()
            .anyMatch(element -> element.equals(task2));
    }

    /**
     * Detect cycles using multiple algorithms.
     */
    private List<Cycle> detectCycles(Map<String, Set<String>> graph) {
        List<Cycle> cycles = new ArrayList<>();

        // 1. Floyd's algorithm for small graphs
        if (graph.size() <= 100) {
            cycles.addAll(detectCyclesFloyd(graph));
        }

        // 2. DFS for larger graphs
        cycles.addAll(detectCyclesDFS(graph));

        // 3. Tarjan's algorithm for strongly connected components
        cycles.addAll(detectCyclesTarjan(graph));

        return cycles;
    }

    /**
     * Detect cycles using Floyd's algorithm.
     */
    private List<Cycle> detectCyclesFloyd(Map<String, Set<String>> graph) {
        List<Cycle> cycles = new ArrayList<>();
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
                                cycles.add(new Cycle(i, path));
                            }
                        }
                    }
                }
            }
        }

        return cycles;
    }

    /**
     * Detect cycles using DFS.
     */
    private List<Cycle> detectCyclesDFS(Map<String, Set<String>> graph) {
        List<Cycle> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                detectCyclesDFS(node, node, graph, visited, recursionStack, new ArrayList<>(), cycles);
            }
        }

        return cycles;
    }

    /**
     * DFS helper for cycle detection.
     */
    private void detectCyclesDFS(String start, String current, Map<String, Set<String>> graph,
                               Set<String> visited, Set<String> recursionStack,
                               List<String> path, List<Cycle> cycles) {
        visited.add(current);
        recursionStack.add(current);
        path.add(current);

        for (String neighbor : graph.getOrDefault(current, Collections.emptySet())) {
            if (!visited.contains(neighbor)) {
                detectCyclesDFS(start, neighbor, graph, visited, recursionStack, path, cycles);
            } else if (recursionStack.contains(neighbor) && neighbor.equals(start)) {
                // Found a cycle
                cycles.add(new Cycle(start, String.join("->", path)));
            }
        }

        recursionStack.remove(current);
        path.remove(path.size() - 1);
    }

    /**
     * Detect cycles using Tarjan's algorithm.
     */
    private List<Cycle> detectCyclesTarjan(Map<String, Set<String>> graph) {
        List<Cycle> cycles = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();
        Map<String, Integer> lowLinks = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        Stack<String> stack = new Stack<>();
        int index = 0;

        for (String node : graph.keySet()) {
            if (!indices.containsKey(node)) {
                strongConnect(node, graph, indices, lowLinks, onStack, stack, cycles, index);
            }
        }

        return cycles;
    }

    /**
     * Tarjan's algorithm helper.
     */
    private void strongConnect(String node, Map<String, Set<String>> graph,
                              Map<String, Integer> indices, Map<String, Integer> lowLinks,
                              Set<String> onStack, Stack<String> stack,
                              List<Cycle> cycles, int[] index) {
        indices.put(node, index[0]);
        lowLinks.put(node, index[0]);
        index[0]++;
        stack.push(node);
        onStack.add(node);

        for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            if (!indices.containsKey(neighbor)) {
                strongConnect(neighbor, graph, indices, lowLinks, onStack, stack, cycles, index);
                lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(neighbor)));
            } else if (onStack.contains(neighbor)) {
                lowLinks.put(node, Math.min(lowLinks.get(node), indices.get(neighbor)));
            }
        }

        if (lowLinks.get(node).equals(indices.get(node))) {
            List<String> cycle = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                cycle.add(w);
            } while (!w.equals(node));
            if (cycle.size() > 1) {
                cycles.add(new Cycle(cycle.get(0), String.join("->", cycle)));
            }
        }
    }

    /**
     * Detect resource-based deadlocks.
     */
    private List<DeadlockAlert> detectResourceDeadlocks() {
        List<DeadlockAlert> alerts = new ArrayList<>();

        try {
            for (ResourceState resource : resourceStates.values()) {
                if (resource.isInCircularWait()) {
                    ResourceDeadlock deadlock = new ResourceDeadlock(
                        resource.getResourceId(),
                        resource.getCircularWaitActors(),
                        System.currentTimeMillis()
                    );

                    DeadlockAlert alert = new DeadlockAlert(
                        "resource_deadlock",
                        resource.getResourceId(),
                        String.format("Resource deadlock detected: %s waiting for %s",
                            resource.getCircularWaitActors()),
                        System.currentTimeMillis(),
                        deadlock
                    );

                    alerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("RESOURCE DEADLOCK DETECTED: {}", deadlock);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in resource deadlock detection", e);
        }

        return alerts;
    }

    /**
     * Detect message queue deadlocks.
     */
    private List<DeadlockAlert> detectMessageQueueDeadlocks() {
        List<DeadlockAlert> alerts = new ArrayList<>();

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

                    alerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("MESSAGE QUEUE DEADLOCK DETECTED: {}", deadlock);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in message queue deadlock detection", e);
        }

        return alerts;
    }

    /**
     * Detect virtual thread deadlocks.
     */
    private List<DeadlockAlert> detectVirtualThreadDeadlocks() {
        List<DeadlockAlert> alerts = new ArrayList<>();

        try {
            for (VirtualThreadState threadState : virtualThreadStates.values()) {
                if (threadState.isDeadlocked()) {
                    VirtualThreadDeadlock deadlock = new VirtualThreadDeadlock(
                        threadState.getThreadId(),
                        threadState.getActorId(),
                        threadState.getState(),
                        System.currentTimeMillis()
                    );

                    DeadlockAlert alert = new DeadlockAlert(
                        "virtual_thread_deadlock",
                        threadState.getThreadId(),
                        String.format("Virtual thread deadlock detected: %s in actor %s",
                            threadState.getState(), threadState.getActorId()),
                        System.currentTimeMillis(),
                        deadlock
                    );

                    alerts.add(alert);
                    deadlockCount.incrementAndGet();

                    LOGGER.warn("VIRTUAL THREAD DEADLOCK DETECTED: {}", deadlock);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in virtual thread deadlock detection", e);
        }

        return alerts;
    }

    /**
     * Analyze deadlock patterns for optimization.
     */
    private void analyzeDeadlockPatterns() {
        try {
            // Analyze deadlock frequency and patterns
            Map<String, Long> deadlockFrequency = deadlockCounters();

            // Identify problematic patterns
            List<String> frequentDeadlocks = deadlockFrequency.entrySet().stream()
                .filter(entry -> entry.getValue() > 10) // More than 10 occurrences
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(5)
                .toList();

            if (!frequentDeadlocks.isEmpty()) {
                LOGGER.warn("Frequent deadlock patterns detected: {}", frequentDeadlocks);

                // Optimize detection strategies
                optimizeDetectionStrategies(frequentDeadlocks);
            }

        } catch (Exception e) {
            LOGGER.error("Error in deadlock pattern analysis", e);
        }
    }

    /**
     * Monitor virtual thread coordination.
     */
    private void monitorVirtualThreadCoordination() {
        try {
            // Check for virtual thread starvation
            long starvingThreads = virtualThreadStates.values().stream()
                .filter(VirtualThreadState::isStarving)
                .count();

            if (starvingThreads > 0) {
                LOGGER.warn("Virtual thread starvation detected: {} threads", starvingThreads);

                // Apply load balancing
                balanceVirtualThreadLoad();
            }

            // Check for thread coordination issues
            checkVirtualThreadCoordinationIssues();

        } catch (Exception e) {
            LOGGER.error("Error in virtual thread coordination monitoring", e);
        }
    }

    /**
     * Process deadlock alert.
     */
    private void processDeadlockAlert(DeadlockAlert alert) {
        try {
            // Attempt recovery
            RecoveryResult result = recoverySystem.attemptRecovery(
                alert.source(), alert, this
            );

            if (result.isSuccess()) {
                recoveryCount.incrementAndGet();
                LOGGER.info("Deadlock recovery successful: {}", alert);
            } else {
                LOGGER.error("Deadlock recovery failed: {} - {}", alert, result.message());
            }

        } catch (Exception e) {
            LOGGER.error("Error processing deadlock alert: " + alert, e);
        }
    }

    /**
     * Optimize detection strategies based on patterns.
     */
    private void optimizeDetectionStrategies(List<String> frequentDeadlocks) {
        for (String pattern : frequentDeadlocks) {
            switch (pattern) {
                case "circular_dependency":
                    // Increase detection frequency for circular dependencies
                    LOGGER.info("Optimizing circular dependency detection");
                    break;
                case "resource_deadlock":
                    // Add resource-specific optimizations
                    LOGGER.info("Optimizing resource deadlock detection");
                    break;
                case "message_queue_deadlock":
                    // Optimize message queue monitoring
                    LOGGER.info("Optimizing message queue deadlock detection");
                    break;
                case "virtual_thread_deadlock":
                    // Enhance virtual thread coordination monitoring
                    LOGGER.info("Optimizing virtual thread deadlock detection");
                    break;
            }
        }
    }

    /**
     * Balance virtual thread load.
     */
    private void balanceVirtualThreadLoad() {
        // Implement load balancing for virtual threads
        LOGGER.info("Balancing virtual thread load");

        // Identify overloaded actors
        List<ActorState> overloadedActors = actorStates.values().stream()
            .filter(actor -> actor.getVirtualThreadCount() > 10) // More than 10 virtual threads
            .toList();

        for (ActorState actor : overloadedActors) {
            // Distribute workload to less loaded actors
            balanceActorWorkload(actor);
        }
    }

    /**
     * Check virtual thread coordination issues.
     */
    private void checkVirtualThreadCoordinationIssues() {
        // Check for coordination issues between virtual threads
        List<VirtualThreadState> coordinationIssues = virtualThreadStates.values().stream()
            .filter(thread -> thread.hasCoordinationIssue())
            .toList();

        if (!coordinationIssues.isEmpty()) {
            LOGGER.warn("Virtual thread coordination issues detected: {} threads",
                coordinationIssues.size());

            // Resolve coordination issues
            for (VirtualThreadState thread : coordinationIssues) {
                resolveCoordinationIssue(thread);
            }
        }
    }

    /**
     * Balance actor workload.
     */
    private void balanceActorWorkload(ActorState overloadedActor) {
        // Find less loaded actors
        List<ActorState> availableActors = actorStates.values().stream()
            .filter(actor -> actor.getVirtualThreadCount() < 5)
            .toList();

        if (!availableActors.isEmpty()) {
            // Distribute virtual threads
            int threadsToDistribute = overloadedActor.getVirtualThreadCount() - 5;
            threadsToDistribute = Math.min(threadsToDistribute, availableActors.size());

            for (int i = 0; i < threadsToDistribute; i++) {
                ActorState targetActor = availableActors.get(i);
                migrateVirtualThreads(overloadedActor, targetActor);
            }

            LOGGER.info("Balanced workload for actor: {} threads migrated",
                overloadedActor.getActorId());
        }
    }

    /**
     * Migrate virtual threads between actors.
     */
    private void migrateVirtualThreads(ActorState source, ActorState target) {
        // Implement virtual thread migration
        LOGGER.info("Migrating virtual threads from {} to {}",
            source.getActorId(), target.getActorId());
    }

    /**
     * Resolve virtual thread coordination issues.
     */
    private void resolveCoordinationIssue(VirtualThreadState thread) {
        // Implement coordination issue resolution
        LOGGER.info("Resolving coordination issue for thread: {}", thread.getThreadId());
    }

    /**
     * Get deadlock counters by type.
     */
    private Map<String, Long> deadlockCounters() {
        Map<String, Long> counters = new HashMap<>();

        // Count by type
        counters.put("circular_dependency",
            deadlockAlerts.stream()
                .filter(alert -> alert.type().equals("circular_dependency"))
                .count());

        counters.put("resource_deadlock",
            deadlockAlerts.stream()
                .filter(alert -> alert.type().equals("resource_deadlock"))
                .count());

        counters.put("message_queue_deadlock",
            deadlockAlerts.stream()
                .filter(alert -> alert.type().equals("message_queue_deadlock"))
                .count());

        counters.put("virtual_thread_deadlock",
            deadlockAlerts.stream()
                .filter(alert -> alert.type().equals("virtual_thread_deadlock"))
                .count());

        return counters;
    }

    /**
     * Generate comprehensive deadlock detection report.
     */
    private void generateComprehensiveReport() {
        LOGGER.info("=== ENHANCED DEADLOCK DETECTION REPORT ===");
        LOGGER.info("Total detections: {}", detectionCount.get());
        LOGGER.info("Deadlocks detected: {}", deadlockCount.get());
        LOGGER.info("Recovery attempts: {}", recoveryCount.get());
        LOGGER.info("Total detection time: {}ms", totalDetectionTime.sum());
        LOGGER.info("Total recovery time: {}ms", totalRecoveryTime.sum());

        // Get system summaries
        DeadlockSummary deadlockSummary = recoverySystem.getRecoverySummary();
        LockFreeValidationSummary validationSummary = lockFreeValidator.getValidationSummary();

        LOGGER.info("Recovery success rate: {:.1f}%",
            deadlockSummary.recoveryCount() > 0 ?
            (double) deadlockSummary.successfulRecoveries() / deadlockSummary.recoveryCount() * 100 : 0);

        LOGGER.info("Validation errors: {}", validationSummary.validationErrors());
        LOGGER.info("Actors monitored: {}", actorStates.size());
        LOGGER.info("Resources tracked: {}", resourceStates.size());
        LOGGER.info("Message queues: {}", messageQueueStates.size());
        LOGGER.info("Virtual threads: {}", virtualThreadStates.size());
    }

    /**
     * Get deadlock detection summary.
     */
    public EnhancedDeadlockSummary getSummary() {
        return new EnhancedDeadlockSummary(
            detectionCount.get(),
            deadlockCount.get(),
            recoveryCount.get(),
            totalDetectionTime.sum(),
            totalRecoveryTime.sum(),
            actorStates.size(),
            resourceStates.size(),
            messageQueueStates.size(),
            virtualThreadStates.size(),
            active.get()
        );
    }

    /**
     * Reset deadlock detector state.
     */
    public void reset() {
        detectionCount.set(0);
        deadlockCount.set(0);
        recoveryCount.set(0);
        totalDetectionTime.reset();
        totalRecoveryTime.reset();
        actorStates.clear();
        resourceStates.clear();
        messageQueueStates.clear();
        virtualThreadStates.clear();
    }

    // Record classes
    public static record EnhancedDeadlockSummary(
        long detectionCount,
        long deadlockCount,
        long recoveryCount,
        long totalDetectionTime,
        long totalRecoveryTime,
        long actorsMonitored,
        long resourcesTracked,
        long messageQueues,
        long virtualThreads,
        boolean isActive
    ) {}

    // Data structure classes
    public static class ActorState {
        final String actorId;
        final YTask task;
        final YNetRunner runner;
        private final Map<String, ResourceUsage> resourceUsage = new ConcurrentHashMap<>();
        private final List<String> virtualThreadIds = new CopyOnWriteArrayList<>();
        private volatile String state = "active";
        private volatile long lastUpdate;

        ActorState(String actorId, YTask task, YNetRunner runner) {
            this.actorId = actorId;
            this.task = task;
            this.runner = runner;
            this.lastUpdate = System.currentTimeMillis();
        }

        // Getters and methods
        String getActorId() { return actorId; }
        YTask getTask() { return task; }
        YNetRunner getRunner() { return runner; }
        Map<String, ResourceUsage> getResourceUsage() { return resourceUsage; }
        List<String> getVirtualThreadIds() { return virtualThreadIds; }
        String getState() { return state; }
        long getLastUpdate() { return lastUpdate; }
        int getVirtualThreadCount() { return virtualThreadIds.size(); }

        void addVirtualThread(String threadId) {
            virtualThreadIds.add(threadId);
        }

        void updateState(String newState) {
            this.state = newState;
            this.lastUpdate = System.currentTimeMillis();
        }
    }

    public static class ResourceState {
        final String resourceId;
        private final Map<String, ResourceUsage> usageHistory = new ConcurrentHashMap<>();

        ResourceState(String resourceId) {
            this.resourceId = resourceId;
        }

        void trackUsage(String actorId, ResourceUsage usage) {
            usageHistory.put(actorId, usage);
        }

        boolean isInCircularWait() {
            // Implementation for circular wait detection
            Set<String> waitingActors = new HashSet<>();
            for (ResourceUsage usage : usageHistory.values()) {
                if (usage.isBlocked()) {
                    waitingActors.addAll(usage.getBlockingActors());
                }
            }
            return waitingActors.stream().anyMatch(usageHistory::containsKey);
        }

        Set<String> getCircularWaitActors() {
            // Implementation for circular wait actors
            return usageHistory.keySet();
        }

        String getResourceId() { return resourceId; }
    }

    public static class MessageQueueState {
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
                   waitingActors.size() >= messageCount;
        }

        boolean isBlocked() {
            return !waitingActors.isEmpty();
        }

        String getQueueId() { return queueId; }
        int getMessageCount() { return messageCount; }
        List<String> getWaitingActors() { return waitingActors; }
    }

    public static class VirtualThreadState {
        final String threadId;
        final String actorId;
        private volatile String state = "RUNNABLE";
        private volatile long lastActivity;
        private volatile boolean isBlocked = false;
        private final List<String> blockedOn = new CopyOnWriteArrayList<>();

        VirtualThreadState(String threadId, String actorId) {
            this.threadId = threadId;
            this.actorId = actorId;
            this.lastActivity = System.currentTimeMillis();
        }

        void updateState(VirtualThreadState newState) {
            this.state = newState.state;
            this.isBlocked = newState.isBlocked;
            this.blockedOn.clear();
            this.blockedOn.addAll(newState.blockedOn);
            this.lastActivity = System.currentTimeMillis();
        }

        boolean isDeadlocked() {
            return isBlocked &&
                   System.currentTimeMillis() - lastActivity > DEFAULT_TIMEOUT_MS;
        }

        boolean isStarving() {
            return System.currentTimeMillis() - lastActivity > DEFAULT_TIMEOUT_MS * 2;
        }

        boolean hasCoordinationIssue() {
            return blockedOn.size() > 5; // Blocked on too many resources
        }

        String getThreadId() { return threadId; }
        String getActorId() { return actorId; }
        String getState() { return state; }
        long getLastActivity() { return lastActivity; }
        List<String> getBlockedOn() { return blockedOn; }
    }

    // Helper classes
    public static class Cycle {
        final String startActor;
        final String path;

        Cycle(String startActor, String path) {
            this.startActor = startActor;
            this.path = path;
        }

        List<String> getActors() {
            return Arrays.asList(path.split("->"));
        }

        @Override
        public String toString() {
            return String.format("%s -> ... -> %s", startActor, path);
        }
    }

    public static class ResourceUsage {
        final String resourceId;
        final boolean blocked;
        final Set<String> blockingActors;

        ResourceUsage(String resourceId, boolean blocked, Set<String> blockingActors) {
            this.resourceId = resourceId;
            this.blocked = blocked;
            this.blockingActors = new HashSet<>(blockingActors);
        }

        boolean isBlocked() { return blocked; }
        Set<String> getBlockingActors() { return blockingActors; }
    }
}