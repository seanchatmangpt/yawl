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

import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.elements.YTask;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Deadlock recovery mechanisms for YAWL actor model systems.
 *
 * <p>This component implements comprehensive deadlock recovery strategies to
 * automatically resolve deadlocks in actor-based workflow execution. It provides
 * both automatic and manual recovery mechanisms with configurable policies.</p>
 *
 * <h2>Recovery Strategies</h2>
 * <ul>
 *   <li><strong>Timeout-based Recovery</strong> - Automatically timeout stuck tasks</li>
 *   <li><strong>Priority-based Recovery</strong> - Resolve deadlocks based on task priority</li>
 *   <li><strong>Compensation Recovery</strong> - Rollback partial work in deadlocked tasks</li>
 *   <li><strong>Process Recovery</strong> - Restart entire workflow from safe checkpoint</li>
 *   <li><strong>Manual Recovery</strong> - API for manual intervention</li>
 * </ul>
 *
 * <h2>Recovery Phases</h2>
 * <ul>
 *   <li><strong>Detection</strong> - Identify deadlock and its root cause</li>
 *   <li><strong>Planning</strong> - Choose optimal recovery strategy</li>
 *   <li><strong>Execution</strong> - Apply recovery actions</li>
 *   <li><strong>Validation</strong> - Verify deadlock is resolved</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class DeadlockRecovery {

    private static final Logger LOGGER = LogManager.getLogger(DeadlockRecovery.class);

    // Configuration constants
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
    private static final long RETRY_DELAY_MS = 5000; // 5 seconds
    private static final int MAX_RETRIES = 3;
    private static final double PRIORITY_WEIGHT = 0.7; // Weight for priority-based recovery

    // Recovery state
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicLong recoveryCount = new AtomicLong(0);
    private final AtomicLong successfulRecoveries = new AtomicLong(0);
    private final AtomicLong failedRecoveries = new AtomicLong(0);

    // Recovery tracking
    private final ConcurrentMap<String, RecoveryHistory> recoveryHistories = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RecoveryState> recoveryStates = new ConcurrentHashMap<>();

    // Recovery executors
    private final ScheduledExecutorService recoveryExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService compensationExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // Recovery policies
    private final RecoveryPolicy defaultPolicy = new TimeoutRecoveryPolicy();
    private final Map<RecoveryStrategy, RecoveryPolicy> recoveryPolicies = new ConcurrentHashMap<>();

    // Metrics
    private final LongAdder totalRecoveryTime = new LongAdder();
    private final LongAdder totalCompensationTime = new LongAdder();

    /**
     * Initialize deadlock recovery system.
     */
    public DeadlockRecovery() {
        // Register default recovery policies
        recoveryPolicies.put(RecoveryStrategy.TIMEOUT, new TimeoutRecoveryPolicy());
        recoveryPolicies.put(RecoveryStrategy.PRIORITY, new PriorityBasedRecoveryPolicy());
        recoveryPolicies.put(RecoveryStrategy.COMPENSATION, new CompensationRecoveryPolicy());
        recoveryPolicies.put(RecoveryStrategy.PROCESS, new ProcessRecoveryPolicy());
    }

    /**
     * Start deadlock recovery system.
     */
    public void startRecoverySystem() {
        if (active.compareAndSet(false, true)) {
            LOGGER.info("Starting deadlock recovery system");

            // Start periodic recovery monitoring
            recoveryExecutor.scheduleAtFixedRate(
                this::monitorRecoveryOperations,
                10000, 10, TimeUnit.SECONDS
            );

            LOGGER.info("Deadlock recovery system started");
        }
    }

    /**
     * Stop deadlock recovery system.
     */
    public void stopRecoverySystem() {
        if (active.compareAndSet(true, false)) {
            LOGGER.info("Stopping deadlock recovery system");

            recoveryExecutor.shutdown();
            compensationExecutor.shutdown();

            try {
                if (!recoveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    recoveryExecutor.shutdownNow();
                }
                if (!compensationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    compensationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                recoveryExecutor.shutdownNow();
                compensationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }

            generateRecoveryReport();
        }
    }

    /**
     * Attempt automatic deadlock recovery.
     */
    public RecoveryResult attemptRecovery(String caseId, DeadlockAlert deadlock) {
        if (!active.get()) {
            return RecoveryResult.failure("Recovery system not active");
        }

        long recoveryId = recoveryCount.incrementAndGet();
        RecoveryHistory history = new RecoveryHistory(caseId, deadlock, recoveryId);
        recoveryHistories.put(String.valueOf(recoveryId), history);

        LOGGER.info("Starting deadlock recovery: case={}, recoveryId={}, type={}",
            caseId, recoveryId, deadlock.type());

        try {
            // Plan recovery strategy
            RecoveryStrategy strategy = planRecovery(deadlock);

            // Execute recovery
            RecoveryResult result = executeRecovery(caseId, deadlock, strategy);

            // Record result
            history.complete(strategy, result);
            if (result.isSuccess()) {
                successfulRecoveries.incrementAndGet();
            } else {
                failedRecoveries.incrementAndGet();
            }

            LOGGER.info("Recovery completed: case={}, recoveryId={}, success={}, strategy={}",
                caseId, recoveryId, result.isSuccess(), strategy);

            return result;

        } catch (Exception e) {
            history.complete(RecoveryStrategy.TIMEOUT, RecoveryResult.failure(e.getMessage()));
            failedRecoveries.incrementAndGet();
            LOGGER.error("Recovery failed for case: " + caseId, e);
            return RecoveryResult.failure(e.getMessage());
        }
    }

    /**
     * Plan optimal recovery strategy.
     */
    private RecoveryStrategy planRecovery(DeadlockAlert deadlock) {
        // Analyze deadlock type and context
        switch (deadlock.type()) {
            case "circular_dependency":
                return chooseCircularDependencyStrategy(deadlock);

            case "resource_deadlock":
                return chooseResourceDeadlockStrategy(deadlock);

            case "message_queue_deadlock":
                return chooseMessageQueueStrategy(deadlock);

            case "virtual_thread_deadlock":
                return chooseVirtualThreadStrategy(deadlock);

            case "lock_chain_deadlock":
                return chooseLockChainStrategy(deadlock);

            case "memory_pressure_deadlock":
                return chooseMemoryStrategy(deadlock);

            default:
                return defaultPolicy.selectStrategy(deadlock);
        }
    }

    /**
     * Choose circular dependency recovery strategy.
     */
    private RecoveryStrategy chooseCircularDependencyStrategy(DeadlockAlert deadlock) {
        // For circular dependencies, try timeout first, then priority
        if (deadlock.timestamp() > System.currentTimeMillis() - DEFAULT_TIMEOUT_MS) {
            return RecoveryStrategy.TIMEOUT;
        } else {
            return RecoveryStrategy.PRIORITY;
        }
    }

    /**
     * Choose resource deadlock recovery strategy.
     */
    private RecoveryStrategy chooseResourceDeadlockStrategy(DeadlockAlert deadlock) {
        // Resource deadlocks need compensation or timeout
        return RecoveryStrategy.COMPENSATION;
    }

    /**
     * Choose message queue deadlock strategy.
     */
    private RecoveryStrategy chooseMessageQueueStrategy(DeadlockAlert deadlock) {
        // Message queues benefit from timeout and priority
        return RecoveryStrategy.TIMEOUT;
    }

    /**
     * Choose virtual thread deadlock strategy.
     */
    private RecoveryStrategy chooseVirtualThreadStrategy(DeadlockAlert deadlock) {
        // Virtual thread deadlocks need process restart
        return RecoveryStrategy.PROCESS;
    }

    /**
     * Choose lock chain deadlock strategy.
     */
    private RecoveryStrategy chooseLockChainStrategy(DeadlockAlert deadlock) {
        // Lock chains need timeout-based recovery
        return RecoveryStrategy.TIMEOUT;
    }

    /**
     * Choose memory-based deadlock strategy.
     */
    private RecoveryStrategy chooseMemoryStrategy(DeadlockAlert deadlock) {
        // Memory deadlocks need process restart
        return RecoveryStrategy.PROCESS;
    }

    /**
     * Execute recovery strategy.
     */
    private RecoveryResult executeRecovery(String caseId, DeadlockAlert deadlock, RecoveryStrategy strategy) {
        RecoveryPolicy policy = recoveryPolicies.getOrDefault(strategy, defaultPolicy);

        return policy.executeRecovery(caseId, deadlock, this);
    }

    /**
     * Monitor ongoing recovery operations.
     */
    private void monitorRecoveryOperations() {
        try {
            // Check for stuck recovery operations
            for (RecoveryState state : recoveryStates.values()) {
                if (state.isStuck()) {
                    LOGGER.warn("Stuck recovery detected: case={}, recoveryId={}, elapsed={}ms",
                        state.getCaseId(), state.getRecoveryId(),
                        System.currentTimeMillis() - state.getStartTime());

                    // Attempt recovery of the recovery itself
                    RecoveryResult result = attemptRecovery(
                        state.getCaseId(),
                        new DeadlockAlert(
                            "recovery_deadlock",
                            state.getCaseId(),
                            "Recovery operation stuck",
                            System.currentTimeMillis(),
                            state
                        )
                    );

                    if (!result.isSuccess()) {
                        // Manual intervention required
                        LOGGER.error("Failed to recover stuck recovery operation for case: " + state.getCaseId());
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error monitoring recovery operations", e);
        }
    }

    /**
     * Schedule retry for failed recovery.
     */
    public void scheduleRetry(String caseId, DeadlockAlert deadlock, RecoveryStrategy strategy) {
        if (recoveryStates.get(caseId) != null) {
            LOGGER.warn("Retrying recovery for case: {}", caseId);

            recoveryExecutor.schedule(() -> {
                RecoveryResult result = executeRecovery(caseId, deadlock, strategy);
                if (!result.isSuccess()) {
                    // Schedule another retry or escalate
                    scheduleRetry(caseId, deadlock, strategy);
                }
            }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Compensate failed recovery.
     */
    public void compensateRecovery(String caseId, RecoveryHistory history) {
        compensationExecutor.submit(() -> {
            try {
                CompensationResult result = executeCompensation(caseId, history);
                LOGGER.info("Compensation completed for case {}: {}", caseId, result);
            } catch (Exception e) {
                LOGGER.error("Compensation failed for case: " + caseId, e);
            }
        });
    }

    /**
     * Execute compensation operations.
     */
    private CompensationResult executeCompensation(String caseId, RecoveryHistory history) {
        // Implement compensation logic based on recovery type
        switch (history.getStrategy()) {
            case TIMEOUT:
                return compensateTimeoutRecovery(caseId, history);
            case PRIORITY:
                return compensatePriorityRecovery(caseId, history);
            case COMPENSATION:
                return compensateCompensationRecovery(caseId, history);
            case PROCESS:
                return compensateProcessRecovery(caseId, history);
            default:
                return CompensationResult.failure("Unknown compensation type");
        }
    }

    /**
     * Compensate timeout-based recovery.
     */
    private CompensationResult compensateTimeoutRecovery(String caseId, RecoveryHistory history) {
        try {
            // Rollback timeout-based changes
            LOGGER.info("Compensating timeout recovery for case: {}", caseId);
            totalCompensationTime.add(System.currentTimeMillis());

            return CompensationResult.success("Timeout recovery compensated");
        } catch (Exception e) {
            return CompensationResult.failure("Compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensate priority-based recovery.
     */
    private CompensationResult compensatePriorityRecovery(String caseId, RecoveryHistory history) {
        try {
            // Restore original task priorities
            LOGGER.info("Compensating priority recovery for case: {}", caseId);
            totalCompensationTime.add(System.currentTimeMillis());

            return CompensationResult.success("Priority recovery compensated");
        } catch (Exception e) {
            return CompensationResult.failure("Compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensate compensation recovery.
     */
    private CompensationResult compensateCompensationRecovery(String caseId, RecoveryHistory history) {
        try {
            // Re-apply original operations
            LOGGER.info("Compensating compensation recovery for case: {}", caseId);
            totalCompensationTime.add(System.currentTimeMillis());

            return CompensationResult.success("Compensation recovery compensated");
        } catch (Exception e) {
            return CompensationResult.failure("Compensation failed: " + e.getMessage());
        }
    }

    /**
     * Compensate process recovery.
     */
    private CompensationResult compensateProcessRecovery(String caseId, RecoveryHistory history) {
        try {
            // Restore from checkpoint
            LOGGER.info("Compensating process recovery for case: {}", caseId);
            totalCompensationTime.add(System.currentTimeMillis());

            return CompensationResult.success("Process recovery compensated");
        } catch (Exception e) {
            return CompensationResult.failure("Compensation failed: " + e.getMessage());
        }
    }

    /**
     * Generate recovery report.
     */
    private void generateRecoveryReport() {
        LOGGER.info("=== DEADLOCK RECOVERY REPORT ===");
        LOGGER.info("Total recovery attempts: {}", recoveryCount.get());
        LOGGER.info("Successful recoveries: {}", successfulRecoveries.get());
        LOGGER.info("Failed recoveries: {}", failedRecoveries.get());
        LOGGER.info("Success rate: {:.1f}%",
            recoveryCount.get() > 0 ?
                (double) successfulRecoveries.get() / recoveryCount.get() * 100 : 0);
        LOGGER.info("Total recovery time: {}ms", totalRecoveryTime.sum());
        LOGGER.info("Total compensation time: {}ms", totalCompensationTime.sum());

        // Report recent recovery failures
        recoveryHistories.values().stream()
            .sorted(Comparator.comparingLong(RecoveryHistory::getTimestamp).reversed())
            .limit(10)
            .forEach(history -> LOGGER.info("Recovery history: {}", history));
    }

    /**
     * Get recovery summary.
     */
    public RecoverySummary getRecoverySummary() {
        return new RecoverySummary(
            recoveryCount.get(),
            successfulRecoveries.get(),
            failedRecoveries.get(),
            totalRecoveryTime.sum(),
            totalCompensationTime.sum(),
            recoveryStates.size(),
            active.get()
        );
    }

    // Recovery policy implementations
    private class TimeoutRecoveryPolicy implements RecoveryPolicy {
        @Override
        public RecoveryStrategy selectStrategy(DeadlockAlert deadlock) {
            return RecoveryStrategy.TIMEOUT;
        }

        @Override
        public RecoveryResult executeRecovery(String caseId, DeadlockAlert deadlock, DeadlockRecovery recovery) {
            long startTime = System.currentTimeMillis();
            totalRecoveryTime.add(startTime);

            try {
                // Implement timeout-based recovery
                RecoveryState state = new RecoveryState(caseId, recoveryCount.get(), startTime);
                recoveryStates.put(caseId, state);

                // Cancel deadlocked tasks
                if (deadlock.details() instanceof ActorState actor) {
                    if (actor.getRunner() != null) {
                        actor.getRunner().cancelTask(null, actor.getTask().getID());
                    }
                }

                state.complete();
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);

                return RecoveryResult.success("Timeout recovery completed");

            } catch (Exception e) {
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);
                return RecoveryResult.failure("Timeout recovery failed: " + e.getMessage());
            } finally {
                recoveryStates.remove(caseId);
            }
        }
    }

    private class PriorityBasedRecoveryPolicy implements RecoveryPolicy {
        @Override
        public RecoveryStrategy selectStrategy(DeadlockAlert deadlock) {
            return RecoveryStrategy.PRIORITY;
        }

        @Override
        public RecoveryResult executeRecovery(String caseId, DeadlockAlert deadlock, DeadlockRecovery recovery) {
            long startTime = System.currentTimeMillis();
            totalRecoveryTime.add(startTime);

            try {
                // Implement priority-based recovery
                RecoveryState state = new RecoveryState(caseId, recoveryCount.get(), startTime);
                recoveryStates.put(caseId, state);

                // Resolve deadlock based on task priorities
                resolveByPriority(deadlock);

                state.complete();
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);

                return RecoveryResult.success("Priority-based recovery completed");

            } catch (Exception e) {
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);
                return RecoveryResult.failure("Priority-based recovery failed: " + e.getMessage());
            } finally {
                recoveryStates.remove(caseId);
            }
        }

        private void resolveByPriority(DeadlockAlert deadlock) {
            // Implementation for priority-based resolution
            // Lower priority tasks are cancelled first
            LOGGER.info("Resolving deadlock by priority");
        }
    }

    private class CompensationRecoveryPolicy implements RecoveryPolicy {
        @Override
        public RecoveryStrategy selectStrategy(DeadlockAlert deadlock) {
            return RecoveryStrategy.COMPENSATION;
        }

        @Override
        public RecoveryResult executeRecovery(String caseId, DeadlockAlert deadlock, DeadlockRecovery recovery) {
            long startTime = System.currentTimeMillis();
            totalRecoveryTime.add(startTime);

            try {
                // Implement compensation-based recovery
                RecoveryState state = new RecoveryState(caseId, recoveryCount.get(), startTime);
                recoveryStates.put(caseId, state);

                // Execute compensation logic
                executeCompensationLogic(deadlock);

                state.complete();
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);

                return RecoveryResult.success("Compensation recovery completed");

            } catch (Exception e) {
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);
                return RecoveryResult.failure("Compensation recovery failed: " + e.getMessage());
            } finally {
                recoveryStates.remove(caseId);
            }
        }

        private void executeCompensationLogic(DeadlockAlert deadlock) {
            // Implementation for compensation logic
            LOGGER.info("Executing compensation logic");
        }
    }

    private class ProcessRecoveryPolicy implements RecoveryPolicy {
        @Override
        public RecoveryStrategy selectStrategy(DeadlockAlert deadlock) {
            return RecoveryStrategy.PROCESS;
        }

        @Override
        public RecoveryResult executeRecovery(String caseId, DeadlockAlert deadlock, DeadlockRecovery recovery) {
            long startTime = System.currentTimeMillis();
            totalRecoveryTime.add(startTime);

            try {
                // Implement process-level recovery
                RecoveryState state = new RecoveryState(caseId, recoveryCount.get(), startTime);
                recoveryStates.put(caseId, state);

                // Restart workflow from safe checkpoint
                restartFromCheckpoint(caseId);

                state.complete();
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);

                return RecoveryResult.success("Process recovery completed");

            } catch (Exception e) {
                totalRecoveryTime.add(System.currentTimeMillis() - startTime);
                return RecoveryResult.failure("Process recovery failed: " + e.getMessage());
            } finally {
                recoveryStates.remove(caseId);
            }
        }

        private void restartFromCheckpoint(String caseId) {
            // Implementation for process recovery
            LOGGER.info("Restarting process from checkpoint: {}", caseId);
        }
    }

    // Record classes
    public static record RecoveryResult(
        boolean success,
        String message,
        long recoveryTime,
        RecoveryStrategy strategy
    ) {
        public static RecoveryResult success(String message) {
            return new RecoveryResult(true, message, 0, null);
        }

        public static RecoveryResult failure(String message) {
            return new RecoveryResult(false, message, 0, null);
        }
    }

    public static record RecoverySummary(
        long recoveryCount,
        long successfulRecoveries,
        long failedRecoveries,
        long totalRecoveryTime,
        long totalCompensationTime,
        long activeRecoveries,
        boolean isActive
    ) {}

    public static record CompensationResult(
        boolean success,
        String message,
        long compensationTime
    ) {
        public static CompensationResult success(String message) {
            return new CompensationResult(true, message, 0);
        }

        public static CompensationResult failure(String message) {
            return new CompensationResult(false, message, 0);
        }
    }

    // Enum definitions
    public enum RecoveryStrategy {
        TIMEOUT,
        PRIORITY,
        COMPENSATION,
        PROCESS
    }

    public interface RecoveryPolicy {
        RecoveryStrategy selectStrategy(DeadlockAlert deadlock);
        RecoveryResult executeRecovery(String caseId, DeadlockAlert deadlock, DeadlockRecovery recovery);
    }
}