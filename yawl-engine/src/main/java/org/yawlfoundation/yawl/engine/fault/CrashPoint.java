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

import java.util.Objects;
import java.util.Optional;

/**
 * Specifies a point in workflow execution where a fault should be injected.
 *
 * <p>CrashPoint enables precise fault injection at specific points in the
 * workflow lifecycle, supporting property-based testing strategies where
 * faults are injected at random points to verify recovery behavior.</p>
 *
 * <h2>Injection Points</h2>
 * <p>CrashPoint can target different phases of workflow execution:
 * <ul>
 *   <li><b>CASE_START</b>: During case initialization</li>
 *   <li><b>WORKITEM_ENABLE</b>: When work item becomes enabled</li>
 *   <li><b>WORKITEM_CHECKOUT</b>: During work item checkout</li>
 *   <li><b>WORKITEM_CHECKIN</b>: During work item completion</li>
 *   <li><b>STATE_TRANSITION</b>: During net state transitions</li>
 *   <li><b>PERSISTENCE</b>: During persistence operations</li>
 *   <li><b>CASE_COMPLETION</b>: During case completion</li>
 * </ul>
 *
 * <h2>Usage in Property-Based Testing</h2>
 * <pre>{@code
 * @Property(tries = 1000)
 * void crashRecoveryPreservesConsistency(
 *     @ForAll("validSpecifications") YSpecification spec,
 *     @ForAll("crashPoints") CrashPoint crashPoint
 * ) {
 *     // 1. Launch case
 *     YNetRunner runner = engine.launchCase(spec, caseId);
 *
 *     // 2. Inject fault at specified point
 *     FaultInjector injector = new FaultInjector();
 *     injector.injectAt(crashPoint);
 *
 *     // 3. Execute workflow until crash point
 *     executeUntil(runner, crashPoint);
 *
 *     // 4. Verify state recovers to consistent checkpoint
 *     RecoveryVerifier.verifyConsistency(runner, crashPoint);
 * }
 * }</pre>
 *
 * @param phase The execution phase where the fault should be injected
 * @param targetId Optional identifier of the specific element (task, case, work item)
 * @param faultModel The type of fault to inject
 * @param delayMs Optional delay before fault injection (for timing failures)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see FaultInjector
 * @see FaultModel
 */
public record CrashPoint(
    InjectionPhase phase,
    String targetId,
    FaultModel faultModel,
    long delayMs
) {

    /**
     * Canonical constructor with validation.
     *
     * @param phase The execution phase (required)
     * @param targetId Optional target identifier (may be null for phase-wide faults)
     * @param faultModel The fault type (required)
     * @param delayMs Delay before injection (0 for immediate)
     * @throws NullPointerException if phase or faultModel is null
     * @throws IllegalArgumentException if delayMs is negative
     */
    public CrashPoint {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(faultModel, "faultModel must not be null");
        if (delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0");
        }
    }

    /**
     * Creates a crash point with no delay.
     *
     * @param phase The execution phase
     * @param targetId Optional target identifier
     * @param faultModel The fault type
     * @return CrashPoint with delayMs = 0
     */
    public static CrashPoint immediate(InjectionPhase phase, String targetId,
                                        FaultModel faultModel) {
        return new CrashPoint(phase, targetId, faultModel, 0);
    }

    /**
     * Creates a crash point for a specific work item checkout.
     *
     * @param workItemId The work item ID to target
     * @param faultModel The fault type
     * @return CrashPoint targeting the work item checkout phase
     */
    public static CrashPoint atWorkItemCheckout(String workItemId, FaultModel faultModel) {
        return new CrashPoint(InjectionPhase.WORKITEM_CHECKOUT, workItemId, faultModel, 0);
    }

    /**
     * Creates a crash point for a specific work item checkin.
     *
     * @param workItemId The work item ID to target
     * @param faultModel The fault type
     * @return CrashPoint targeting the work item checkin phase
     */
    public static CrashPoint atWorkItemCheckin(String workItemId, FaultModel faultModel) {
        return new CrashPoint(InjectionPhase.WORKITEM_CHECKIN, workItemId, faultModel, 0);
    }

    /**
     * Creates a crash point for case start.
     *
     * @param caseId The case ID to target
     * @param faultModel The fault type
     * @return CrashPoint targeting case start
     */
    public static CrashPoint atCaseStart(String caseId, FaultModel faultModel) {
        return new CrashPoint(InjectionPhase.CASE_START, caseId, faultModel, 0);
    }

    /**
     * Creates a crash point for case completion.
     *
     * @param caseId The case ID to target
     * @param faultModel The fault type
     * @return CrashPoint targeting case completion
     */
    public static CrashPoint atCaseCompletion(String caseId, FaultModel faultModel) {
        return new CrashPoint(InjectionPhase.CASE_COMPLETION, caseId, faultModel, 0);
    }

    /**
     * Creates a crash point for persistence operations.
     *
     * @param operationId The persistence operation ID
     * @param faultModel The fault type
     * @return CrashPoint targeting persistence
     */
    public static CrashPoint atPersistence(String operationId, FaultModel faultModel) {
        return new CrashPoint(InjectionPhase.PERSISTENCE, operationId, faultModel, 0);
    }

    /**
     * Creates a timing failure crash point with specified delay.
     *
     * @param phase The execution phase
     * @param targetId Optional target identifier
     * @param timeoutMs The timeout duration in milliseconds
     * @return CrashPoint with TIMING_FAILURE model
     */
    public static CrashPoint timingFailure(InjectionPhase phase, String targetId,
                                            long timeoutMs) {
        return new CrashPoint(phase, targetId, FaultModel.TIMING_FAILURE, timeoutMs);
    }

    /**
     * Returns the optional target identifier.
     *
     * @return Optional containing target ID, or empty if phase-wide
     */
    public Optional<String> getTargetId() {
        return Optional.ofNullable(targetId);
    }

    /**
     * Checks if this crash point targets a specific element.
     *
     * @param elementId The element ID to check
     * @return true if this crash point targets the specified element
     */
    public boolean targets(String elementId) {
        return targetId != null && targetId.equals(elementId);
    }

    /**
     * Checks if this crash point has a delay before injection.
     *
     * @return true if delayMs > 0
     */
    public boolean hasDelay() {
        return delayMs > 0;
    }

    /**
     * Returns a human-readable description of this crash point.
     *
     * @return Description string
     */
    public String describe() {
        String target = targetId != null ? " '" + targetId + "'" : "";
        String delay = delayMs > 0 ? " after " + delayMs + "ms" : "";
        return faultModel.getDisplayName() + " at " + phase + target + delay;
    }

    @Override
    public String toString() {
        return String.format("CrashPoint{phase=%s, targetId=%s, faultModel=%s, delayMs=%d}",
            phase, targetId, faultModel, delayMs);
    }

    /**
     * Enumeration of workflow execution phases where faults can be injected.
     */
    public enum InjectionPhase {
        /**
         * During case initialization (case start).
         */
        CASE_START("Case Start"),

        /**
         * When a work item becomes enabled.
         */
        WORKITEM_ENABLE("Work Item Enable"),

        /**
         * During work item checkout (starting execution).
         */
        WORKITEM_CHECKOUT("Work Item Checkout"),

        /**
         * During work item checkin (completion).
         */
        WORKITEM_CHECKIN("Work Item Checkin"),

        /**
         * During Petri net state transitions.
         */
        STATE_TRANSITION("State Transition"),

        /**
         * During persistence operations (database writes).
         */
        PERSISTENCE("Persistence"),

        /**
         * During case completion (reaching output condition).
         */
        CASE_COMPLETION("Case Completion"),

        /**
         * During external service calls.
         */
        EXTERNAL_SERVICE("External Service"),

        /**
         * During resource allocation.
         */
        RESOURCE_ALLOCATION("Resource Allocation"),

        /**
         * During timer/scheduler operations.
         */
        TIMER("Timer");

        private final String displayName;

        InjectionPhase(String displayName) {
            this.displayName = displayName;
        }

        /**
         * Returns the human-readable display name.
         *
         * @return Display name
         */
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
