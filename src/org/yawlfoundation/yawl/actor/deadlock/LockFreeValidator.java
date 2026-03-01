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

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.*;
import java.util.function.*;
import java.lang.management.*;

/**
 * Lock-free validation for YAWL actor coordination.
 *
 * <p>This component implements lock-free validation mechanisms to prevent deadlocks
 * in virtual thread coordination scenarios. It uses concurrent data structures and
 * lock-free algorithms to ensure thread safety without traditional locks.</p>
 *
 * <h2>Validation Strategies</h2>
 * <ul>
 *   <li><strong>Wait-Free Algorithms</strong> - Ensure progress even with contention</li>
 *   <li><strong>Lock-Free Data Structures</strong> - Use CAS operations for state changes</li>
 *   <li><strong>Memory Barriers</strong> - Ensure visibility without locks</li>
 *   <li><strong>Atomic Operations</strong> - Use Atomic classes for coordination</li>
 * </ul>
 *
 * <h2>Validation Targets</h2>
 * <ul>
 *   <li>Virtual thread scheduling and waiting patterns</li>
 *   <li>Actor message queue coordination</li>
 *   <li>Resource allocation patterns</li>
 *   <li>Lock acquisition sequences</li>
 * </ul>
 *
 * @author YAWL Foundation / GODSPEED Protocol
 * @version 6.0.0
 * @since 6.0.0
 */
public final class LockFreeValidator {

    private static final Logger LOGGER = LogManager.getLogger(LockFreeValidator.class);

    // Configuration constants
    private static final long VALIDATION_INTERVAL_MS = 1000; // 1 second
    private static final int MAX_WAIT_DEPTH = 5; // Maximum wait chain depth
    private static final long DEADLOCK_TIMEOUT_MS = 30000; // 30 seconds
    private static final double CONTENTION_THRESHOLD = 0.8; // 80% contention

    // Thread-local tracking for lock-free validation
    private static final ThreadLocal<ValidationContext> threadContext =
        ThreadLocal.withInitial(ValidationContext::new);

    // Concurrent data structures for tracking
    private final ConcurrentMap<String, ActorValidationState> actorStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LockChain> lockChains = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QueueState> queueStates = new ConcurrentHashMap<>();

    // Atomic counters for metrics
    private final AtomicLong validationCount = new AtomicLong(0);
    private final AtomicLong deadlockDetectedCount = new AtomicLong(0);
    private final AtomicLong validationErrors = new AtomicLong(0);

    // Thread management
    private final ScheduledExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Memory management
    private final AtomicReference<MemorySnapshot> lastMemorySnapshot = new AtomicReference<>();
    private final AtomicLong lastValidationTime = new AtomicLong(0);

    /**
     * Start lock-free validation monitoring.
     */
    public void startValidation() {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting lock-free validation for actor coordination");

            // Schedule periodic validation
            scheduler.scheduleAtFixedRate(
                this::performLockFreeValidation,
                VALIDATION_INTERVAL_MS,
                VALIDATION_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );

            // Schedule memory validation
            scheduler.scheduleAtFixedRate(
                this::validateMemoryUsage,
                5000, 5, TimeUnit.SECONDS
            );

            LOGGER.info("Lock-free validation started");
        }
    }

    /**
     * Stop lock-free validation.
     */
    public void stopValidation() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping lock-free validation");
            scheduler.shutdown();

            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Register actor for lock-free validation.
     */
    public void registerActor(String actorId, YTask task, YNetRunner runner) {
        if (running.get()) {
            ActorValidationState state = new ActorValidationState(actorId, task, runner);
            actorStates.put(actorId, state);

            LOGGER.debug("Registered actor for lock-free validation: {}", actorId);
        }
    }

    /**
     * Record lock acquisition (lock-free).
     */
    public boolean recordLockAcquisition(String actorId, String lockId) {
        if (!running.get()) return true;

        ActorValidationState actorState = actorStates.get(actorId);
        if (actorState != null) {
            // Atomic operation to record lock acquisition
            LockChain chain = lockChains.computeIfAbsent(
                actorId, k -> new LockChain(actorId)
            );

            return chain.acquireLock(lockId);
        }
        return true;
    }

    /**
     * Record lock release (lock-free).
     */
    public void recordLockRelease(String actorId, String lockId) {
        if (!running.get()) return;

        LockChain chain = lockChains.get(actorId);
        if (chain != null) {
            chain.releaseLock(lockId);
        }
    }

    /**
     * Record message queue access (lock-free).
     */
    public void recordQueueAccess(String queueId, String actorId, boolean isBlocking) {
        if (!running.get()) return;

        QueueState queueState = queueStates.computeIfAbsent(
            queueId, k -> new QueueState(queueId)
        );

        queueState.recordAccess(actorId, isBlocking);
    }

    /**
     * Perform comprehensive lock-free validation.
     */
    private void performLockFreeValidation() {
        try {
            long currentTime = System.currentTimeMillis();
            lastValidationTime.set(currentTime);
            validationCount.incrementAndGet();

            // Validate lock chains
            validateLockChains();

            // Validate queue states
            validateQueueStates();

            // Validate actor coordination
            validateActorCoordination();

            // Validate memory barriers
            validateMemoryBarriers();

        } catch (Exception e) {
            validationErrors.incrementAndGet();
            LOGGER.error("Error in lock-free validation", e);
        }
    }

    /**
     * Validate lock chains for circular dependencies.
     */
    private void validateLockChains() {
        for (LockChain chain : lockChains.values()) {
            if (chain.isCircular()) {
                deadlockDetectedCount.incrementAndGet();

                DeadlockAlert alert = new DeadlockAlert(
                    "lock_chain_deadlock",
                    chain.getActorId(),
                    String.format("Circular lock chain detected: %s", chain.getLockPath()),
                    System.currentTimeMillis(),
                    chain
                );

                LOGGER.warn("LOCK CHAIN DEADLOCK: {}", alert);

                // Attempt to break the deadlock
                attemptLockChainRecovery(chain);
            }
        }
    }

    /**
     * Validate queue states for blocking patterns.
     */
    private void validateQueueStates() {
        for (QueueState queue : queueStates.values()) {
            if (queue.isDeadlocked()) {
                deadlockDetectedCount.incrementAndGet();

                DeadlockAlert alert = new DeadlockAlert(
                    "queue_deadlock",
                    queue.getQueueId(),
                    String.format("Queue deadlock: %d actors waiting", queue.getBlockedActors().size()),
                    System.currentTimeMillis(),
                    queue
                );

                LOGGER.warn("QUEUE DEADLOCK: {}", alert);

                attemptQueueRecovery(queue);
            }
        }
    }

    /**
     * Validate actor coordination patterns.
     */
    private void validateActorCoordination() {
        for (ActorValidationState actor : actorStates.values()) {
            if (actor.isInDeadlock()) {
                deadlockDetectedCount.incrementAndGet();

                DeadlockAlert alert = new DeadlockAlert(
                    "actor_coordination_deadlock",
                    actor.getActorId(),
                    String.format("Actor coordination deadlock: %s", actor.getState()),
                    System.currentTimeMillis(),
                    actor
                );

                LOGGER.warn("ACTOR COORDINATION DEADLOCK: {}", alert);

                attemptActorRecovery(actor);
            }
        }
    }

    /**
     * Validate memory barriers and visibility.
     */
    private void validateMemoryBarriers() {
        MemorySnapshot currentSnapshot = createMemorySnapshot();
        MemorySnapshot lastSnapshot = lastMemorySnapshot.get();

        if (lastSnapshot != null) {
            // Validate memory visibility
            validateMemoryVisibility(currentSnapshot, lastSnapshot);

            // Validate memory ordering
            validateMemoryOrdering(currentSnapshot, lastSnapshot);
        }

        lastMemorySnapshot.set(currentSnapshot);
    }

    /**
     * Validate memory visibility between snapshots.
     */
    private void validateMemoryVisibility(MemorySnapshot current, MemorySnapshot last) {
        for (String actorId : current.getActorStates().keySet()) {
            ActorValidationState currentActor = current.getActorStates().get(actorId);
            ActorValidationState lastActor = last.getActorStates().get(actorId);

            if (lastActor != null) {
                // Check for visibility issues
                if (currentActor.getLastUpdate() < lastActor.getLastUpdate()) {
                    // Potential visibility problem
                    LOGGER.debug("Visibility issue detected for actor: {}", actorId);
                }
            }
        }
    }

    /**
     * Validate memory ordering constraints.
     */
    private void validateMemoryOrdering(MemorySnapshot current, MemorySnapshot last) {
        // Check for memory ordering violations
        boolean hasOrderViolation = current.getMemoryStates().keySet().stream()
            .anyMatch(key -> {
                MemoryState currentMemory = current.getMemoryStates().get(key);
                MemoryState lastMemory = last.getMemoryStates().get(key);

                return lastMemory != null &&
                       currentMemory.getVersion() <= lastMemory.getVersion();
            });

        if (hasOrderViolation) {
            LOGGER.warn("Memory ordering violation detected");
        }
    }

    /**
     * Attempt to recover from lock chain deadlock.
     */
    private void attemptLockChainRecovery(LockChain chain) {
        // Use lock-free recovery: timeout the longest wait
        String timeoutActor = chain.findLongestWaitingActor();
        if (timeoutActor != null) {
            ActorValidationState actor = actorStates.get(timeoutActor);
            if (actor != null) {
                forceActorTimeout(actor);
            }
        }
    }

    /**
     * Attempt to recover from queue deadlock.
     */
    private void attemptQueueRecovery(QueueState queue) {
        // Non-blocking recovery: notify waiting actors
        for (String actorId : queue.getBlockedActors()) {
            ActorValidationState actor = actorStates.get(actorId);
            if (actor != null) {
                notifyActor(actor);
            }
        }
    }

    /**
     * Attempt to recover from actor deadlock.
     */
    private void attemptActorRecovery(ActorValidationState actor) {
        // Lock-free recovery: interrupt the actor's virtual thread
        if (actor.getRunner() != null) {
            interruptActor(actor);
        }
    }

    /**
     * Force timeout of an actor (lock-free).
     */
    private void forceActorTimeout(ActorValidationState actor) {
        actor.forceTimeout();
        LOGGER.info("Forced timeout for actor: {}", actor.getActorId());
    }

    /**
     * Notify an actor to break deadlock.
     */
    private void notifyActor(ActorValidationState actor) {
        actor.notify();
        LOGGER.info("Notified actor to break deadlock: {}", actor.getActorId());
    }

    /**
     * Interrupt an actor's virtual thread.
     */
    private void interruptActor(ActorValidationState actor) {
        actor.interrupt();
        LOGGER.info("Interrupted actor virtual thread: {}", actor.getActorId());
    }

    /**
     * Validate memory usage and detect memory-related deadlocks.
     */
    private void validateMemoryUsage() {
        try {
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();

            double memoryPressure = (double) heapUsage.getUsed() / heapUsage.getMax();

            if (memoryPressure > CONTENTION_THRESHOLD) {
                LOGGER.warn("High memory pressure detected: {:.1f}%", memoryPressure * 100);

                // Check for memory-based deadlocks
                if (detectMemoryBasedDeadlocks()) {
                    deadlockDetectedCount.incrementAndGet();

                    DeadlockAlert alert = new DeadlockAlert(
                        "memory_pressure_deadlock",
                        "system",
                        String.format("High memory pressure causing potential deadlocks: {:.1f}%",
                            memoryPressure * 100),
                        System.currentTimeMillis(),
                        heapUsage
                    );

                    LOGGER.warn("MEMORY DEADLOCK THREAT: {}", alert);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Error in memory usage validation", e);
        }
    }

    /**
     * Detect memory-based deadlocks.
     */
    private boolean detectMemoryBasedDeadlocks() {
        // Check if garbage collection is struggling
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        GarbageCollectorMXBean gcMXBean = ManagementFactory.getGarbageCollectorMXBean();

        if (gcMXBean != null) {
            long collectionTime = gcMXBean.getCollectionTime();
            long collectionCount = gcMXBean.getCollectionCount();

            // Frequent or long GC cycles indicate memory pressure
            return collectionTime > 1000 || collectionCount > 100;
        }

        return false;
    }

    /**
     * Create current memory snapshot.
     */
    private MemorySnapshot createMemorySnapshot() {
        Map<String, ActorValidationState> currentActors = new ConcurrentHashMap<>();
        Map<String, MemoryState> memoryStates = new ConcurrentHashMap<>();

        // Copy current actor states
        actorStates.forEach((id, state) -> {
            currentActors.put(id, state.copy());
        });

        // Create memory states
        for (ActorValidationState actor : currentActors.values()) {
            MemoryState memoryState = new MemoryState(
                actor.getActorId(),
                System.currentTimeMillis(),
                actor.getVersion()
            );
            memoryStates.put(actor.getActorId(), memoryState);
        }

        return new MemorySnapshot(currentActors, memoryStates, System.currentTimeMillis());
    }

    /**
     * Get validation summary.
     */
    public LockFreeValidationSummary getValidationSummary() {
        return new LockFreeValidationSummary(
            validationCount.get(),
            deadlockDetectedCount.get(),
            validationErrors.get(),
            actorStates.size(),
            lockChains.size(),
            queueStates.size(),
            running.get()
        );
    }

    // Inner classes for lock-free data structures
    private static class LockChain {
        final String actorId;
        final ConcurrentMap<String, LockNode> locks = new ConcurrentHashMap<>();
        final AtomicInteger version = new AtomicInteger(0);

        LockChain(String actorId) {
            this.actorId = actorId;
        }

        boolean acquireLock(String lockId) {
            LockNode node = locks.computeIfAbsent(lockId, k -> new LockNode(lockId));
            return node.acquire();
        }

        void releaseLock(String lockId) {
            LockNode node = locks.get(lockId);
            if (node != null) {
                node.release();
            }
        }

        boolean isCircular() {
            // Detect circular lock chains using lock-free traversal
            Set<String> visited = ConcurrentHashMap.newKeySet();
            return hasCircularLock(lockId, visited);
        }

        private boolean hasCircularLock(String currentLock, Set<String> visited) {
            if (visited.contains(currentLock)) {
                return true;
            }

            visited.add(currentLock);
            LockNode node = locks.get(currentLock);

            if (node != null && node.isHoldingLock()) {
                for (String waitingLock : node.getWaitingLocks()) {
                    if (hasCircularLock(waitingLock, visited)) {
                        return true;
                    }
                }
            }

            visited.remove(currentLock);
            return false;
        }

        String findLongestWaitingActor() {
            // Find actor waiting longest (lock-free)
            return locks.values().stream()
                .max(Comparator.comparingLong(LockNode::getWaitTime))
                .map(LockNode::getLockId)
                .orElse(null);
        }

        String getActorId() { return actorId; }
        String getLockPath() {
            return locks.keySet().stream().collect(Collectors.joining(" -> "));
        }
    }

    private static class LockNode {
        final String lockId;
        final AtomicBoolean locked = new AtomicBoolean(false);
        final AtomicLong acquireTime = new AtomicLong(0);
        final Set<String> waitingLocks = ConcurrentHashMap.newKeySet();
        final AtomicInteger waiters = new AtomicInteger(0);

        LockNode(String lockId) {
            this.lockId = lockId;
        }

        boolean acquire() {
            if (locked.compareAndSet(false, true)) {
                acquireTime.set(System.currentTimeMillis());
                return true;
            }

            waiters.incrementAndGet();
            waitingLocks.add(lockId);
            return false;
        }

        void release() {
            locked.set(false);
            waitingLocks.remove(lockId);
            waiters.decrementAndGet();
        }

        boolean isHoldingLock() {
            return locked.get();
        }

        long getWaitTime() {
            return isHoldingLock() ?
                System.currentTimeMillis() - acquireTime.get() : 0;
        }

        Set<String> getWaitingLocks() {
            return new HashSet<>(waitingLocks);
        }

        String getLockId() { return lockId; }
    }

    private static class QueueState {
        final String queueId;
        final ConcurrentMap<String, QueueAccess> accessHistory = new ConcurrentHashMap<>();
        final AtomicInteger totalAccesses = new AtomicInteger(0);

        QueueState(String queueId) {
            this.queueId = queueId;
        }

        void recordAccess(String actorId, boolean isBlocking) {
            QueueAccess access = new QueueAccess(actorId, isBlocking, System.currentTimeMillis());
            accessHistory.put(actorId, access);
            totalAccesses.incrementAndGet();
        }

        boolean isDeadlocked() {
            // Check for too many blocking accesses
            long blockingCount = accessHistory.values().stream()
                .filter(QueueAccess::isBlocking)
                .count();

            return blockingCount > accessHistory.size() * 0.8; // 80% blocking
        }

        Set<String> getBlockedActors() {
            return accessHistory.entrySet().stream()
                .filter(entry -> entry.getValue().isBlocking())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        }

        String getQueueId() { return queueId; }
    }

    private static class QueueAccess {
        final String actorId;
        final boolean isBlocking;
        final long timestamp;

        QueueAccess(String actorId, boolean isBlocking, long timestamp) {
            this.actorId = actorId;
            this.isBlocking = isBlocking;
            this.timestamp = timestamp;
        }

        String getActorId() { return actorId; }
        boolean isBlocking() { return isBlocking; }
    }

    private static class MemorySnapshot {
        final Map<String, ActorValidationState> actorStates;
        final Map<String, MemoryState> memoryStates;
        final long timestamp;

        MemorySnapshot(Map<String, ActorValidationState> actorStates,
                      Map<String, MemoryState> memoryStates,
                      long timestamp) {
            this.actorStates = new ConcurrentHashMap<>(actorStates);
            this.memoryStates = new ConcurrentHashMap<>(memoryStates);
            this.timestamp = timestamp;
        }

        Map<String, ActorValidationState> getActorStates() { return actorStates; }
        Map<String, MemoryState> getMemoryStates() { return memoryStates; }
        long getTimestamp() { return timestamp; }
    }

    private static class MemoryState {
        final String actorId;
        final long timestamp;
        final long version;

        MemoryState(String actorId, long timestamp, long version) {
            this.actorId = actorId;
            this.timestamp = timestamp;
            this.version = version;
        }

        String getActorId() { return actorId; }
        long getTimestamp() { return timestamp; }
        long getVersion() { return version; }
    }

    // Record classes
    public static record LockFreeValidationSummary(
        long validationCount,
        long deadlockDetectedCount,
        long validationErrors,
        long actorsMonitored,
        long lockChains,
        long queuesMonitored,
        boolean isRunning
    ) {}

    // Thread-local validation context
    private static class ValidationContext {
        private final ThreadLocal<Long> lastValidation = ThreadLocal.withInitial(() -> 0L);
        private final ThreadLocal<Integer> localVersion = ThreadLocal.withInitial(() -> 0);

        long getLastValidation() { return lastValidation.get(); }
        void setLastValidation(long time) { lastValidation.set(time); }

        int getLocalVersion() { return localVersion.get(); }
        void incrementVersion() { localVersion.set(localVersion.get() + 1); }
    }
}