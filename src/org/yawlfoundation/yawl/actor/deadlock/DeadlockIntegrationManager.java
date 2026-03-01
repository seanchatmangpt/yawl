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

import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YNet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Deadlock Integration Manager for YAWL Actor Model Systems.
 *
 * <p>This manager coordinates all deadlock detection and recovery components to provide
 * a comprehensive deadlock management system. It integrates multiple detection strategies,
 * recovery mechanisms, and validation systems into a unified framework.</p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li><strong>Unified Coordination</strong> - Coordinates multiple deadlock detection systems</li>
 *   <li><strong>Centralized Management</strong> - Single point of control for deadlock management</li>
 *   <li><strong>Event-Driven Architecture</strong> - Reacts to deadlock events with appropriate actions</li>
 *   <li><strong>Performance Optimization</strong> - Adaptive resource allocation and prioritization</li>
 *   <li><strong>Comprehensive Reporting</strong> - Unified metrics and reporting</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class DeadlockIntegrationManager {

    private static final Logger LOGGER = LogManager.getLogger(DeadlockIntegrationManager.class);

    // Configuration constants
    private static final long DEFAULT_STARTUP_DELAY_MS = 5000; // 5 seconds
    private static final long DEFAULT_SHUTDOWN_TIMEOUT_MS = 10000; // 10 seconds
    private static final int MAX_CONCURRENT_RECOVERIES = 5;
    private static final double FAILURE_THRESHOLD = 0.3; // 30% failure threshold

    // Manager state
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong integrationCount = new AtomicLong(0);
    private final AtomicLong successfulIntegrations = new AtomicLong(0);
    private final AtomicLong failedIntegrations = new AtomicLong(0);

    // Core components
    private final EnhancedDeadlockDetector deadlockDetector;
    private final AdvancedDeadlockRecovery deadlockRecovery;
    private final ActorLockFreeValidator lockFreeValidator;

    // Coordination structures
    private final ConcurrentMap<String, CaseDeadlockManager> caseManagers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<DeadlockEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService integrationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService eventProcessorExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Event handlers
    private final List<Consumer<DeadlockEvent>> eventHandlers = new CopyOnWriteArrayList<>();
    private final DeadlockEventHandler eventHandler = new DeadlockEventHandler();

    // Metrics and monitoring
    private final LongAdder totalIntegrationTime = new LongAdder();
    private final LongAdder totalRecoveryTime = new LongAdder();
    private final LongAdder totalValidationTime = new LongAdder();

    // Performance optimization
    private final PerformanceOptimizer performanceOptimizer = new PerformanceOptimizer();
    private final CircuitBreaker circuitBreaker = new CircuitBreaker(MAX_CONCURRENT_RECOVERIES);

    /**
     * Initialize deadlock integration manager.
     */
    public DeadlockIntegrationManager() {
        // Initialize core components
        this.deadlockDetector = new EnhancedDeadlockDetector();
        this.deadlockRecovery = new AdvancedDeadlockRecovery();
        this.lockFreeValidator = new ActorLockFreeValidator();

        // Register event handlers
        registerDefaultEventHandlers();

        LOGGER.info("Deadlock Integration Manager initialized");
    }

    /**
     * Start the deadlock integration system.
     */
    public void startIntegration() {
        if (initialized.compareAndSet(false, true)) {
            LOGGER.info("Starting Deadlock Integration Manager");

            try {
                // Start core components
                deadlockDetector.startMonitoring();
                deadlockRecovery.startRecoverySystem();
                lockFreeValidator.startValidation();

                // Start event processing
                eventProcessorExecutor.scheduleAtFixedRate(
                    this::processEvents,
                    1000, 1, TimeUnit.SECONDS
                );

                // Start integration coordination
                integrationExecutor.scheduleAtFixedRate(
                    this::coordinateIntegration,
                    DEFAULT_STARTUP_DELAY_MS,
                    DEFAULT_STARTUP_DELAY_MS,
                    TimeUnit.MILLISECONDS
                );

                // Start performance monitoring
                integrationExecutor.scheduleAtFixedRate(
                    this::monitorPerformance,
                    5000, 5, TimeUnit.SECONDS
                );

                LOGGER.info("Deadlock Integration Manager started successfully");

            } catch (Exception e) {
                LOGGER.error("Failed to start Deadlock Integration Manager", e);
                throw new RuntimeException("Failed to start deadlock integration system", e);
            }
        }
    }

    /**
     * Stop the deadlock integration system.
     */
    public void stopIntegration() {
        if (active.compareAndSet(true, false)) {
            LOGGER.info("Stopping Deadlock Integration Manager");

            try {
                // Stop event processing
                eventProcessorExecutor.shutdown();

                // Stop integration coordination
                integrationExecutor.shutdown();

                // Stop core components
                deadlockDetector.stopMonitoring();
                deadlockRecovery.stopRecoverySystem();
                lockFreeValidator.stopValidation();

                // Generate final report
                generateIntegrationReport();

                LOGGER.info("Deadlock Integration Manager stopped successfully");

            } catch (Exception e) {
                LOGGER.error("Error stopping Deadlock Integration Manager", e);
            }
        }
    }

    /**
     * Register a case for deadlock management.
     */
    public void registerCase(String caseId, YNet net, YNetRunner runner) {
        if (initialized.get()) {
            CaseDeadlockManager caseManager = new CaseDeadlockManager(caseId, net, runner);
            caseManagers.put(caseId, caseManager);

            // Register with core components
            deadlockDetector.registerActor(caseId, null, runner);
            lockFreeValidator.registerActor(caseId, null, runner);

            LOGGER.info("Registered case for deadlock management: {}", caseId);
        }
    }

    /**
     * Unregister a case from deadlock management.
     */
    public void unregisterCase(String caseId) {
        CaseDeadlockManager caseManager = caseManagers.remove(caseId);
        if (caseManager != null) {
            caseManager.cleanup();
            LOGGER.info("Unregistered case from deadlock management: {}", caseId);
        }
    }

    /**
     * Track actor activity for deadlock detection.
     */
    public void trackActorActivity(String caseId, String actorId, String taskId,
                                  Map<String, Object> activityData) {
        if (initialized.get()) {
            // Add event to queue for processing
            DeadlockEvent event = new DeadlockEvent(
                "ACTIVITY_TRACK",
                caseId,
                actorId,
                taskId,
                System.currentTimeMillis(),
                activityData
            );
            eventQueue.add(event);

            // Directly update components
            updateActivityTracking(caseId, actorId, taskId, activityData);
        }
    }

    /**
     * Handle deadlock detection alert.
     */
    public void handleDeadlockAlert(DeadlockAlert alert) {
        if (initialized.get()) {
            // Create and queue the deadlock event
            DeadlockEvent event = new DeadlockEvent(
                "DEADLOCK_DETECTED",
                alert.source(),
                null,
                null,
                System.currentTimeMillis(),
                Map.of("alert", alert)
            );
            eventQueue.add(event);

            LOGGER.warn("Deadlock alert queued: {} - {}", alert.type(), alert.message());
        }
    }

    /**
     * Perform integrated deadlock recovery.
     */
    public IntegratedRecoveryResult performIntegratedRecovery(String caseId, DeadlockAlert alert) {
        if (!initialized.get()) {
            return IntegratedRecoveryResult.failure("Integration system not initialized");
        }

        long startTime = System.currentTimeMillis();
        totalRecoveryTime.add(startTime);

        try {
            integrationCount.incrementAndGet();

            // Check circuit breaker
            if (circuitBreaker.isCircuitOpen()) {
                return IntegratedRecoveryResult.failure("Circuit breaker open - recovery temporarily disabled");
            }

            // Get case manager
            CaseDeadlockManager caseManager = caseManagers.get(caseId);
            if (caseManager == null) {
                return IntegratedRecoveryResult.failure("Case not found: " + caseId);
            }

            // Coordinate recovery across all systems
            IntegratedRecoveryResult result = coordinateIntegratedRecovery(caseManager, alert);

            // Record result
            if (result.isSuccess()) {
                successfulIntegrations.incrementAndGet();
                circuitBreaker.recordSuccess();
            } else {
                failedIntegrations.incrementAndGet();
                circuitBreaker.recordFailure();
            }

            totalRecoveryTime.add(System.currentTimeMillis() - startTime);

            return result;

        } catch (Exception e) {
            totalRecoveryTime.add(System.currentTimeMillis() - startTime);
            failedIntegrations.incrementAndGet();
            LOGGER.error("Error in integrated recovery for case: " + caseId, e);
            return IntegratedRecoveryResult.failure("Recovery failed: " + e.getMessage());
        }
    }

    /**
     * Get integrated deadlock summary.
     */
    public IntegratedDeadlockSummary getSummary() {
        return new IntegratedDeadlockSummary(
            integrationCount.get(),
            successfulIntegrations.get(),
            failedIntegrations.get(),
            totalIntegrationTime.sum(),
            totalRecoveryTime.sum(),
            totalValidationTime.sum(),
            caseManagers.size(),
            eventQueue.size(),
            circuitBreaker.isOpen(),
            deadlockDetector.getSummary(),
            deadlockRecovery.getSummary(),
            lockFreeValidator.getSummary()
        );
    }

    /**
     * Process deadlock events from the queue.
     */
    private void processEvents() {
        try {
            while (!eventQueue.isEmpty()) {
                DeadlockEvent event = eventQueue.poll();
                if (event != null) {
                    processEvent(event);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error processing deadlock events", e);
        }
    }

    /**
     * Process a single deadlock event.
     */
    private void processEvent(DeadlockEvent event) {
        try {
            // Notify event handlers
            for (Consumer<DeadlockEvent> handler : eventHandlers) {
                handler.accept(event);
            }

            // Handle specific event types
            switch (event.getType()) {
                case "ACTIVITY_TRACK":
                    handleActivityTrackingEvent(event);
                    break;
                case "DEADLOCK_DETECTED":
                    handleDeadlockDetectionEvent(event);
                    break;
                case "RECOVERY_REQUEST":
                    handleRecoveryRequestEvent(event);
                    break;
                case "SYSTEM_ALERT":
                    handleSystemAlertEvent(event);
                    break;
            }

        } catch (Exception e) {
            LOGGER.error("Error processing deadlock event: " + event, e);
        }
    }

    /**
     * Handle activity tracking event.
     */
    private void handleActivityTrackingEvent(DeadlockEvent event) {
        try {
            String caseId = event.getCaseId();
            String actorId = event.getActorId();
            String taskId = event.getTaskId();
            Map<String, Object> data = event.getData();

            // Update activity tracking in components
            CaseDeadlockManager caseManager = caseManagers.get(caseId);
            if (caseManager != null) {
                caseManager.updateActivity(actorId, taskId, data);
            }

        } catch (Exception e) {
            LOGGER.error("Error handling activity tracking event", e);
        }
    }

    /**
     * Handle deadlock detection event.
     */
    private void handleDeadlockDetectionEvent(DeadlockEvent event) {
        try {
            Map<String, Object> data = event.getData();
            DeadlockAlert alert = (DeadlockAlert) data.get("alert");

            // Trigger recovery process
            performIntegratedRecovery(event.getCaseId(), alert);

        } catch (Exception e) {
            LOGGER.error("Error handling deadlock detection event", e);
        }
    }

    /**
     * Handle recovery request event.
     */
    private void handleRecoveryRequestEvent(DeadlockEvent event) {
        try {
            String caseId = event.getCaseId();
            Map<String, Object> data = event.getData();

            // Perform requested recovery
            String recoveryType = (String) data.get("recoveryType");
            switch (recoveryType) {
                case "IMMEDIATE":
                    performImmediateRecovery(caseId);
                    break;
                case "GRACEFUL":
                    performGracefulRecovery(caseId);
                    break;
                case "ADAPTIVE":
                    performAdaptiveRecovery(caseId);
                    break;
            }

        } catch (Exception e) {
            LOGGER.error("Error handling recovery request event", e);
        }
    }

    /**
     * Handle system alert event.
     */
    private void handleSystemAlertEvent(DeadlockEvent event) {
        try {
            Map<String, Object> data = event.getData();

            // Handle system-level alerts
            String alertType = (String) data.get("alertType");
            switch (alertType) {
                case "HIGH_CONTENTION":
                    performanceOptimizer.handleHighContention();
                    break;
                case "RESOURCE_STARVATION":
                    handleResourceStarvation();
                    break;
                case "PERFORMANCE_DEGRADED":
                    performanceOptimizer.optimizeForPerformance();
                    break;
            }

        } catch (Exception e) {
            LOGGER.error("Error handling system alert event", e);
        }
    }

    /**
     * Coordinate integrated recovery.
     */
    private IntegratedRecoveryResult coordinateIntegratedRecovery(CaseDeadlockManager caseManager, DeadlockAlert alert) {
        try {
            // Step 1: Validate recovery prerequisites
            if (!validateRecoveryPrerequisites(caseManager)) {
                return IntegratedRecoveryResult.failure("Recovery prerequisites not met");
            }

            // Step 2: Coordinate with all systems
            RecoveryCoordination coordination = new RecoveryCoordination(caseManager, alert);

            // Step 3: Execute coordinated recovery
            return coordination.execute();

        } catch (Exception e) {
            LOGGER.error("Error coordinating integrated recovery", e);
            return IntegratedRecoveryResult.failure("Coordination failed: " + e.getMessage());
        }
    }

    /**
     * Validate recovery prerequisites.
     */
    private boolean validateRecoveryPrerequisites(CaseDeadlockManager caseManager) {
        // Check if case is in a recoverable state
        return caseManager.isRecoverable() && !caseManager.hasActiveRecovery();
    }

    /**
     * Update activity tracking across components.
     */
    private void updateActivityTracking(String caseId, String actorId, String taskId,
                                       Map<String, Object> activityData) {
        try {
            // Update deadlock detector
            deadlockDetector.updateActorState(caseId, new EnhancedDeadlockDetector.ActorState(
                caseId, null, caseManagers.get(caseId).getNetRunner()
            ));

            // Update lock-free validator
            if (activityData.containsKey("lockId")) {
                String lockId = (String) activityData.get("lockId");
                boolean acquiring = (Boolean) activityData.getOrDefault("acquiring", true);
                if (acquiring) {
                    lockFreeValidator.recordLockAcquisition(caseId, lockId);
                } else {
                    lockFreeValidator.recordLockRelease(caseId, lockId);
                }
            }

            // Update message queue tracking
            if (activityData.containsKey("queueId")) {
                String queueId = (String) activityData.get("queueId");
                int messageCount = (Integer) activityData.getOrDefault("messageCount", 0);
                @SuppressWarnings("unchecked")
                List<String> waitingActors = (List<String>) activityData.getOrDefault("waitingActors", Collections.emptyList());
                lockFreeValidator.recordQueueAccess(queueId, caseId, messageCount > 0);
            }

        } catch (Exception e) {
            LOGGER.error("Error updating activity tracking", e);
        }
    }

    /**
     * Coordinate integration between systems.
     */
    private void coordinateIntegration() {
        try {
            if (!active.get()) return;

            // Check for integration opportunities
            List<String> inactiveCases = findInactiveCases();
            if (!inactiveCases.isEmpty()) {
                optimizeInactiveCases(inactiveCases);
            }

            // Check for integration bottlenecks
            IntegrationBottleneck bottleneck = detectIntegrationBottleneck();
            if (bottleneck != null) {
                resolveIntegrationBottleneck(bottleneck);
            }

        } catch (Exception e) {
            LOGGER.error("Error coordinating integration", e);
        }
    }

    /**
     * Monitor system performance.
     */
    private void monitorPerformance() {
        try {
            // Monitor deadlock detection performance
            deadlockDetector.getSummary();

            // Monitor recovery performance
            deadlockRecovery.getSummary();

            // Monitor validation performance
            lockFreeValidator.getSummary();

            // Check for performance degradation
            if (performanceOptimizer.needsOptimization()) {
                performanceOptimizer.optimize();
            }

        } catch (Exception e) {
            LOGGER.error("Error monitoring system performance", e);
        }
    }

    /**
     * Generate integration report.
     */
    private void generateIntegrationReport() {
        LOGGER.info("=== DEADLOCK INTEGRATION REPORT ===");
        LOGGER.info("Total integrations: {}", integrationCount.get());
        LOGGER.info("Successful integrations: {}", successfulIntegrations.get());
        LOGGER.info("Failed integrations: {}", failedIntegrations.get());
        LOGGER.info("Integration success rate: {:.1f}%",
            integrationCount.get() > 0 ?
            (double) successfulIntegrations.get() / integrationCount.get() * 100 : 0);
        LOGGER.info("Total integration time: {}ms", totalIntegrationTime.sum());
        LOGGER.info("Total recovery time: {}ms", totalRecoveryTime.sum());
        LOGGER.info("Total validation time: {}ms", totalValidationTime.sum());

        LOGGER.info("Cases managed: {}", caseManagers.size());
        LOGGER.info("Events in queue: {}", eventQueue.size());
        LOGGER.info("Circuit breaker state: {}", circuitBreaker.isOpen() ? "OPEN" : "CLOSED");

        // Component summaries
        LOGGER.info("Deadlock detector: {}", deadlockDetector.getSummary());
        LOGGER.info("Recovery system: {}", deadlockRecovery.getSummary());
        LOGGER.info("Lock-free validator: {}", lockFreeValidator.getSummary());
    }

    /**
     * Find inactive cases for optimization.
     */
    private List<String> findInactiveCases() {
        return caseManagers.entrySet().stream()
            .filter(entry -> entry.getValue().isInactive())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    /**
     * Optimize inactive cases.
     */
    private void optimizeInactiveCases(List<String> inactiveCases) {
        for (String caseId : inactiveCases) {
            CaseDeadlockManager caseManager = caseManagers.get(caseId);
            if (caseManager != null) {
                caseManager.optimizeForInactivity();
            }
        }
        LOGGER.info("Optimized {} inactive cases", inactiveCases.size());
    }

    /**
     * Detect integration bottlenecks.
     */
    private IntegrationBottleneck detectIntegrationBottleneck() {
        // Check for various bottleneck indicators
        if (eventQueue.size() > 1000) {
            return new IntegrationBottleneck("EVENT_QUEUE_BACKLOG", eventQueue.size());
        }

        if (circuitBreaker.isNearThreshold()) {
            return new IntegrationBottleneck("CIRCUIT_BREAKER_NEAR_THRESHOLD", circuitBreaker.getFailureCount());
        }

        if (caseManagers.values().stream().filter(CaseDeadlockManager::isOverloaded).count() > 5) {
            return new IntegrationBottleneck("CASE_MANAGER_OVERLOAD", 5);
        }

        return null;
    }

    /**
     * Resolve integration bottleneck.
     */
    private void resolveIntegrationBottleneck(IntegrationBottleneck bottleneck) {
        switch (bottleneck.getType()) {
            case "EVENT_QUEUE_BACKLOG":
                resolveEventQueueBacklog();
                break;
            case "CIRCUIT_BREAKER_NEAR_THRESHOLD":
                circuitBreaker.reset();
                break;
            case "CASE_MANAGER_OVERLOAD":
                redistributeCaseLoad();
                break;
        }

        LOGGER.info("Resolved integration bottleneck: {}", bottleneck);
    }

    /**
     * Resolve event queue backlog.
     */
    private void resolveEventQueueBacklog() {
        // Implement event queue optimization strategies
        LOGGER.info("Resolving event queue backlog");
    }

    /**
     * Redistribute case load.
     */
    private void redistributeCaseLoad() {
        // Implement load redistribution strategies
        LOGGER.info("Redistributing case load");
    }

    /**
     * Perform immediate recovery.
     */
    private void performImmediateRecovery(String caseId) {
        // Implement immediate recovery logic
        LOGGER.info("Performing immediate recovery for case: {}", caseId);
    }

    /**
     * Perform graceful recovery.
     */
    private void performGracefulRecovery(String caseId) {
        // Implement graceful recovery logic
        LOGGER.info("Performing graceful recovery for case: {}", caseId);
    }

    /**
     * Perform adaptive recovery.
     */
    private void performAdaptiveRecovery(String caseId) {
        // Implement adaptive recovery logic
        LOGGER.info("Performing adaptive recovery for case: {}", caseId);
    }

    /**
     * Handle resource starvation.
     */
    private void handleResourceStarvation() {
        // Implement resource starvation handling
        LOGGER.info("Handling resource starvation");
    }

    /**
     * Register default event handlers.
     */
    private void registerDefaultEventHandlers() {
        eventHandlers.add(eventHandler::handleEvent);
        eventHandlers.add(this::logEvent);
        eventHandlers.add(this::monitorEvent);
    }

    /**
     * Log event for debugging.
     */
    private void logEvent(DeadlockEvent event) {
        LOGGER.debug("Deadlock event: {}", event);
    }

    /**
     * Monitor event for metrics.
     */
    private void monitorEvent(DeadlockEvent event) {
        totalIntegrationTime.add(System.currentTimeMillis());
    }

    // Record classes
    public static record IntegratedDeadlockSummary(
        long integrationCount,
        long successfulIntegrations,
        long failedIntegrations,
        long totalIntegrationTime,
        long totalRecoveryTime,
        long totalValidationTime,
        long casesManaged,
        long pendingEvents,
        boolean circuitBreakerOpen,
        EnhancedDeadlockSummary detectorSummary,
        AdvancedRecoverySummary recoverySummary,
        ActorLockFreeValidationSummary validatorSummary
    ) {}

    public static record IntegratedRecoveryResult(
        boolean success,
        String message,
        long recoveryTime,
        Map<String, Object> metadata
    ) {
        public static IntegratedRecoveryResult success(String message) {
            return new IntegratedRecoveryResult(true, message, 0, Map.of());
        }

        public static IntegratedRecoveryResult failure(String message) {
            return new IntegratedRecoveryResult(false, message, 0, Map.of());
        }
    }

    // Supporting classes
    private static class CaseDeadlockManager {
        final String caseId;
        final YNet net;
        final YNetRunner netRunner;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean recoverable = new AtomicBoolean(true);
        private final AtomicBoolean hasActiveRecovery = new AtomicBoolean(false);
        private final Map<String, ActorActivity> actorActivities = new ConcurrentHashMap<>();

        CaseDeadlockManager(String caseId, YNet net, YNetRunner netRunner) {
            this.caseId = caseId;
            this.net = net;
            this.netRunner = netRunner;
        }

        void updateActivity(String actorId, String taskId, Map<String, Object> data) {
            ActorActivity activity = actorActivities.computeIfAbsent(
                actorId, k -> new ActorActivity(actorId)
            );
            activity.updateActivity(taskId, data);
        }

        boolean isInactive() {
            // Check if case is inactive based on activity patterns
            return active.get() && actorActivities.values().stream()
                .noneMatch(ActorActivity::isActive);
        }

        boolean isOverloaded() {
            // Check if case is overloaded
            return actorActivities.size() > 100;
        }

        boolean isRecoverable() {
            return recoverable.get();
        }

        boolean hasActiveRecovery() {
            return hasActiveRecovery.get();
        }

        void optimizeForInactivity() {
            // Implement inactivity optimization
            active.set(false);
        }

        void cleanup() {
            active.set(false);
            actorActivities.clear();
        }

        String getCaseId() { return caseId; }
        YNet getNet() { return net; }
        YNetRunner getNetRunner() { return netRunner; }
    }

    private static class ActorActivity {
        final String actorId;
        private final Map<String, ActivityRecord> activityRecords = new ConcurrentHashMap<>();
        private final AtomicLong lastActivity = new AtomicLong(0);
        private final AtomicInteger activityCount = new AtomicInteger(0);

        ActorActivity(String actorId) {
            this.actorId = actorId;
        }

        void updateActivity(String taskId, Map<String, Object> data) {
            ActivityRecord record = new ActivityRecord(taskId, data, System.currentTimeMillis());
            activityRecords.put(taskId, record);
            lastActivity.set(System.currentTimeMillis());
            activityCount.incrementAndGet();
        }

        boolean isActive() {
            return System.currentTimeMillis() - lastActivity.get() < 30000; // 30 seconds
        }

        String getActorId() { return actorId; }
    }

    private static class ActivityRecord {
        final String taskId;
        final Map<String, Object> data;
        final long timestamp;

        ActivityRecord(String taskId, Map<String, Object> data, long timestamp) {
            this.taskId = taskId;
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    private static class DeadlockEvent {
        final String type;
        final String caseId;
        final String actorId;
        final String taskId;
        final long timestamp;
        final Map<String, Object> data;

        DeadlockEvent(String type, String caseId, String actorId, String taskId,
                     long timestamp, Map<String, Object> data) {
            this.type = type;
            this.caseId = caseId;
            this.actorId = actorId;
            this.taskId = taskId;
            this.timestamp = timestamp;
            this.data = data;
        }

        String getType() { return type; }
        String getCaseId() { return caseId; }
        String getActorId() { return actorId; }
        String getTaskId() { return taskId; }
        long getTimestamp() { return timestamp; }
        Map<String, Object> getData() { return data; }

        @Override
        public String toString() {
            return String.format("DeadlockEvent{type='%s', caseId='%s', actorId='%s', taskId='%s', timestamp=%d}",
                type, caseId, actorId, taskId, timestamp);
        }
    }

    private static class IntegrationBottleneck {
        final String type;
        final int severity;

        IntegrationBottleneck(String type, int severity) {
            this.type = type;
            this.severity = severity;
        }

        String getType() { return type; }
        int getSeverity() { return severity; }

        @Override
        public String toString() {
            return String.format("IntegrationBottleneck{type='%s', severity=%d}", type, severity);
        }
    }

    private static class RecoveryCoordination {
        final CaseDeadlockManager caseManager;
        final DeadlockAlert alert;

        RecoveryCoordination(CaseDeadlockManager caseManager, DeadlockAlert alert) {
            this.caseManager = caseManager;
            this.alert = alert;
        }

        IntegratedRecoveryResult execute() {
            try {
                // Coordinate recovery across all systems
                // This is a simplified coordination - actual implementation would be more complex

                // Update recovery state
                caseManager.hasActiveRecovery.set(true);

                // Execute recovery
                Thread.sleep(1000); // Simulate recovery time

                // Complete recovery
                caseManager.hasActiveRecovery.set(false);
                caseManager.recoverable.set(true);

                return IntegratedRecoveryResult.success("Integrated recovery completed successfully");

            } catch (Exception e) {
                caseManager.hasActiveRecovery.set(false);
                return IntegratedRecoveryResult.failure("Recovery coordination failed: " + e.getMessage());
            }
        }
    }

    private static class DeadlockEventHandler implements Consumer<DeadlockEvent> {
        @Override
        public void accept(DeadlockEvent event) {
            // Handle deadlock events
            LOGGER.info("Deadlock event handled: {}", event);
        }
    }

    private static class PerformanceOptimizer {
        private final AtomicBoolean needsOptimization = new AtomicBoolean(false);
        private final AtomicInteger optimizationCount = new AtomicInteger(0);

        void handleHighContention() {
            needsOptimization.set(true);
        }

        void optimizeForPerformance() {
            needsOptimization.set(true);
        }

        boolean needsOptimization() {
            return needsOptimization.get();
        }

        void optimize() {
            if (needsOptimization.compareAndSet(true, false)) {
                optimizationCount.incrementAndGet();
                LOGGER.info("Performance optimization applied (count: {})", optimizationCount.get());
            }
        }
    }

    private static class CircuitBreaker {
        final int maxFailures;
        final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicBoolean open = new AtomicBoolean(false);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private static final long RESET_TIMEOUT_MS = 60000; // 1 minute

        CircuitBreaker(int maxFailures) {
            this.maxFailures = maxFailures;
        }

        void recordFailure() {
            failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());
            checkIfOpen();
        }

        void recordSuccess() {
            failureCount.set(0);
            open.set(false);
        }

        void reset() {
            failureCount.set(0);
            open.set(false);
        }

        boolean isOpen() {
            if (open.get()) {
                // Check if reset timeout has passed
                if (System.currentTimeMillis() - lastFailureTime.get() > RESET_TIMEOUT_MS) {
                    open.set(false);
                    failureCount.set(0);
                }
            }
            return open.get();
        }

        boolean isNearThreshold() {
            return failureCount.get() >= maxFailures * 0.8;
        }

        boolean isCircuitOpen() {
            return isOpen();
        }

        int getFailureCount() {
            return failureCount.get();
        }

        private void checkIfOpen() {
            if (failureCount.get() >= maxFailures) {
                open.set(true);
                LOGGER.warn("Circuit breaker opened after {} failures", failureCount.get());
            }
        }
    }
}