/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.fault;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Fault Injector for Armstrong-Style Property-Based Testing.
 *
 * <p>Implements Joe Armstrong's fault injection philosophy: "Let it crash, but
 * verify it recovers." This injector enables systematic fault injection at
 * specific points in workflow execution to verify that the engine maintains
 * consistency and recovers correctly from failures.</p>
 *
 * <h2>Armstrong's Philosophy Applied to YAWL</h2>
 * <blockquote>
 * "The key to building reliable systems is to design for failure, not to try
 * to prevent it. If you can crash and recover, you're reliable." — Joe Armstrong
 * </blockquote>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Precise Injection</b>: Inject faults at specific execution points</li>
 *   <li><b>Randomized Injection</b>: Randomly inject faults for property testing</li>
 *   <li><b>Configurable Probability</b>: Control fault frequency</li>
 *   <li><b>Multiple Fault Types</b>: Process crash, message loss, corruption, timing, Byzantine</li>
 *   <li><b>Observer Pattern</b>: Register listeners to observe injected faults</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create injector with 10% fault probability
 * FaultInjector injector = new FaultInjector(0.1);
 *
 * // Configure specific fault points
 * injector.addFaultPoint(CrashPoint.atWorkItemCheckout("task_A", FaultModel.PROCESS_CRASH));
 *
 * // Register fault observer
 * injector.onFault(fault -> System.out.println("Injected: " + fault));
 *
 * // Use in property-based test
 * @Property(tries = 1000)
 * void crashRecoveryPreservesConsistency(YSpecification spec) {
 *     injector.setRandomMode(true);
 *     YNetRunner runner = engine.launchCase(spec, caseId);
 *     injector.maybeInject(CrashPoint.InjectionPhase.WORKITEM_CHECKOUT, "task_A");
 *     // ... execute workflow ...
 *     RecoveryVerifier.verifyConsistency(runner);
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe and can be used in concurrent tests. Fault points
 * and observers are stored in concurrent collections.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see FaultModel
 * @see CrashPoint
 * @see RecoveryVerifier
 */
public final class FaultInjector {

    /** Default fault probability (5%) */
    private static final double DEFAULT_FAULT_PROBABILITY = 0.05;

    /** Random number generator for fault injection decisions */
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    /** Probability of fault injection when in random mode (0.0 to 1.0) */
    private volatile double faultProbability;

    /** Whether random fault injection is enabled */
    private volatile boolean randomMode;

    /** Specific crash points to inject (in order) */
    private final List<CrashPoint> scheduledFaults = new CopyOnWriteArrayList<>();

    /** Index into scheduledFaults for sequential injection */
    private final AtomicInteger scheduledFaultIndex = new AtomicInteger(0);

    /** Observers to notify when faults are injected */
    private final List<Consumer<InjectedFault>> faultObservers = new CopyOnWriteArrayList<>();

    /** Count of faults injected, by type */
    private final Map<FaultModel, AtomicInteger> faultCounts = new ConcurrentHashMap<>();

    /** Whether fault injection is globally enabled */
    private volatile boolean enabled = true;

    /** Current context for fault injection (case ID, work item ID, etc.) */
    private final ThreadLocal<Map<String, String>> context = ThreadLocal.withInitial(HashMap::new);

    /**
     * Creates a new FaultInjector with default fault probability (5%).
     */
    public FaultInjector() {
        this(DEFAULT_FAULT_PROBABILITY);
    }

    /**
     * Creates a new FaultInjector with specified fault probability.
     *
     * @param faultProbability Probability of fault injection (0.0 to 1.0)
     * @throws IllegalArgumentException if probability is out of range
     */
    public FaultInjector(double faultProbability) {
        setFaultProbability(faultProbability);
        // Initialize fault counts
        for (FaultModel model : FaultModel.values()) {
            faultCounts.put(model, new AtomicInteger(0));
        }
    }

    /**
     * Sets the fault injection probability for random mode.
     *
     * @param probability Probability of fault injection (0.0 to 1.0)
     * @throws IllegalArgumentException if probability is out of range
     */
    public void setFaultProbability(double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(
                "Fault probability must be between 0.0 and 1.0, got: " + probability);
        }
        this.faultProbability = probability;
    }

    /**
     * Returns the current fault injection probability.
     *
     * @return Fault probability (0.0 to 1.0)
     */
    public double getFaultProbability() {
        return faultProbability;
    }

    /**
     * Enables or disables random fault injection mode.
     *
     * @param randomMode true to enable random injection, false to use scheduled faults only
     */
    public void setRandomMode(boolean randomMode) {
        this.randomMode = randomMode;
    }

    /**
     * Checks if random fault injection mode is enabled.
     *
     * @return true if random mode is enabled
     */
    public boolean isRandomMode() {
        return randomMode;
    }

    /**
     * Enables or disables fault injection globally.
     *
     * @param enabled true to enable, false to disable all fault injection
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks if fault injection is enabled.
     *
     * @return true if fault injection is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Adds a scheduled fault point for injection.
     *
     * <p>Scheduled faults are injected in order when maybeInject is called,
     * before any random fault injection.</p>
     *
     * @param crashPoint The crash point to add
     */
    public void addFaultPoint(CrashPoint crashPoint) {
        Objects.requireNonNull(crashPoint, "crashPoint must not be null");
        scheduledFaults.add(crashPoint);
    }

    /**
     * Adds multiple scheduled fault points.
     *
     * @param crashPoints The crash points to add
     */
    public void addFaultPoints(Collection<CrashPoint> crashPoints) {
        Objects.requireNonNull(crashPoints, "crashPoints must not be null");
        scheduledFaults.addAll(crashPoints);
    }

    /**
     * Clears all scheduled fault points.
     */
    public void clearScheduledFaults() {
        scheduledFaults.clear();
        scheduledFaultIndex.set(0);
    }

    /**
     * Registers an observer to be notified when faults are injected.
     *
     * @param observer Consumer that receives InjectedFault details
     */
    public void onFault(Consumer<InjectedFault> observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        faultObservers.add(observer);
    }

    /**
     * Removes a fault observer.
     *
     * @param observer The observer to remove
     */
    public void removeObserver(Consumer<InjectedFault> observer) {
        faultObservers.remove(observer);
    }

    /**
     * Sets context value for fault injection decisions.
     *
     * @param key Context key (e.g., "caseId", "workItemId")
     * @param value Context value
     */
    public void setContext(String key, String value) {
        context.get().put(key, value);
    }

    /**
     * Gets context value.
     *
     * @param key Context key
     * @return Context value, or null if not set
     */
    public String getContext(String key) {
        return context.get().get(key);
    }

    /**
     * Clears the current context.
     */
    public void clearContext() {
        context.get().clear();
    }

    /**
     * Attempts to inject a fault at the specified phase.
     *
     * <p>Injection logic:
     * <ol>
     *   <li>Check if injection is enabled globally</li>
     *   <li>Check scheduled faults first (inject if phase/target matches)</li>
     *   <li>If random mode, randomly decide to inject based on probability</li>
     *   <li>If injecting, throw appropriate exception for fault model</li>
     * </ol>
     *
     * @param phase The execution phase
     * @param targetId Optional target identifier (may be null)
     * @throws RuntimeException subtype based on fault model
     */
    public void maybeInject(CrashPoint.InjectionPhase phase, String targetId) {
        if (!enabled) {
            return;
        }

        // Check scheduled faults first
        if (scheduledFaultIndex.get() < scheduledFaults.size()) {
            CrashPoint scheduled = scheduledFaults.get(scheduledFaultIndex.get());
            if (scheduled.phase() == phase &&
                (scheduled.targetId() == null || scheduled.targetId().equals(targetId))) {
                scheduledFaultIndex.incrementAndGet();
                inject(scheduled);
                return;
            }
        }

        // Random injection
        if (randomMode && random.nextDouble() < faultProbability) {
            // Select random fault model (weighted by severity)
            FaultModel faultModel = selectRandomFaultModel();
            CrashPoint crashPoint = new CrashPoint(phase, targetId, faultModel, 0);
            inject(crashPoint);
        }
    }

    /**
     * Injects a specific fault immediately.
     *
     * @param crashPoint The crash point to inject
     * @throws RuntimeException subtype based on fault model
     */
    public void inject(CrashPoint crashPoint) {
        Objects.requireNonNull(crashPoint, "crashPoint must not be null");

        if (!enabled) {
            return;
        }

        // Apply delay if specified
        if (crashPoint.hasDelay()) {
            try {
                Thread.sleep(crashPoint.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Record fault injection
        faultCounts.get(crashPoint.faultModel()).incrementAndGet();

        // Create fault record
        InjectedFault fault = new InjectedFault(
            crashPoint,
            System.currentTimeMillis(),
            getContextSnapshot()
        );

        // Notify observers
        for (Consumer<InjectedFault> observer : faultObservers) {
            try {
                observer.accept(fault);
            } catch (Exception e) {
                // Ignore observer errors
            }
        }

        // Throw appropriate exception based on fault model
        throwFaultException(crashPoint);
    }

    /**
     * Injects a fault at the next opportunity matching the phase.
     *
     * @param phase The phase to inject at
     * @param faultModel The fault type
     */
    public void injectAtNext(CrashPoint.InjectionPhase phase, FaultModel faultModel) {
        addFaultPoint(new CrashPoint(phase, null, faultModel, 0));
    }

    /**
     * Returns the count of faults injected by type.
     *
     * @param faultModel The fault type
     * @return Number of faults injected
     */
    public int getFaultCount(FaultModel faultModel) {
        return faultCounts.get(faultModel).get();
    }

    /**
     * Returns the total count of all faults injected.
     *
     * @return Total fault count
     */
    public int getTotalFaultCount() {
        return faultCounts.values().stream()
            .mapToInt(AtomicInteger::get)
            .sum();
    }

    /**
     * Resets all counters and clears scheduled faults.
     */
    public void reset() {
        clearScheduledFaults();
        faultCounts.values().forEach(counter -> counter.set(0));
        clearContext();
    }

    /**
     * Selects a random fault model, weighted by severity.
     *
     * @return Randomly selected fault model
     */
    private FaultModel selectRandomFaultModel() {
        // Weight: CRITICAL=1, HIGH=2, MEDIUM=3, LOW=4 (lower severity = more likely)
        // This biases towards less destructive faults for more reliable testing
        int selection = random.nextInt(10);
        return switch (selection) {
            case 0, 1 -> FaultModel.PROCESS_CRASH;       // 20%
            case 2, 3 -> FaultModel.MESSAGE_LOSS;        // 20%
            case 4, 5 -> FaultModel.MESSAGE_CORRUPTION;  // 20%
            case 6, 7, 8 -> FaultModel.TIMING_FAILURE;   // 30%
            case 9 -> FaultModel.BYZANTINE_FAILURE;      // 10%
            default -> FaultModel.TIMING_FAILURE;
        };
    }

    /**
     * Throws the appropriate exception for the fault model.
     *
     * @param crashPoint The crash point containing fault model
     */
    private void throwFaultException(CrashPoint crashPoint) {
        String message = "Injected fault: " + crashPoint.describe();

        switch (crashPoint.faultModel()) {
            case PROCESS_CRASH:
                throw new ProcessCrashException(message);

            case MESSAGE_LOSS:
                throw new MessageLossException(message);

            case MESSAGE_CORRUPTION:
                throw new MessageCorruptionException(message);

            case TIMING_FAILURE:
                throw new TimingFailureException(message);

            case BYZANTINE_FAILURE:
                throw new ByzantineFailureException(message);

            default:
                throw new RuntimeException(message);
        }
    }

    /**
     * Gets a snapshot of the current context.
     */
    private Map<String, String> getContextSnapshot() {
        return new HashMap<>(context.get());
    }

    // =========================================================================
    // Exception Types
    // =========================================================================

    /**
     * Base exception for injected faults.
     */
    public static class InjectedFaultException extends RuntimeException {
        public InjectedFaultException(String message) {
            super(message);
        }
    }

    /**
     * Process crash fault exception.
     */
    public static class ProcessCrashException extends InjectedFaultException {
        public ProcessCrashException(String message) {
            super(message);
        }
    }

    /**
     * Message loss fault exception.
     */
    public static class MessageLossException extends InjectedFaultException {
        public MessageLossException(String message) {
            super(message);
        }
    }

    /**
     * Message corruption fault exception.
     */
    public static class MessageCorruptionException extends InjectedFaultException {
        public MessageCorruptionException(String message) {
            super(message);
        }
    }

    /**
     * Timing failure fault exception.
     */
    public static class TimingFailureException extends InjectedFaultException {
        public TimingFailureException(String message) {
            super(message);
        }
    }

    /**
     * Byzantine failure fault exception.
     */
    public static class ByzantineFailureException extends InjectedFaultException {
        public ByzantineFailureException(String message) {
            super(message);
        }
    }

    // =========================================================================
    // Injected Fault Record
    // =========================================================================

    /**
     * Record of an injected fault for analysis and reporting.
     *
     * @param crashPoint The crash point that was injected
     * @param timestamp When the fault was injected
     * @param context The context at injection time
     */
    public record InjectedFault(
        CrashPoint crashPoint,
        long timestamp,
        Map<String, String> context
    ) {
        /**
         * Returns a human-readable description of this injected fault.
         *
         * @return Description string
         */
        public String describe() {
            return String.format("[%s] %s (context: %s)",
                new java.util.Date(timestamp),
                crashPoint.describe(),
                context);
        }
    }
}
