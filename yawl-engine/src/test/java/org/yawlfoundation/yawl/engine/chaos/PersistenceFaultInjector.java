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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.chaos;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Persistence Fault Injector for Chaos Testing — T3.3 Blue Ocean Innovation.
 *
 * <p>Simulates transient failures in persistence operations to verify the engine
 * maintains atomicity and consistency even under database failures. This injector
 * intercepts persistence calls and throws configurable faults at specified
 * invocation counts.</p>
 *
 * <p>Fault modes:
 * <ul>
 *   <li>{@link FaultMode#IMMEDIATE}: Throw IOException immediately on N-th call</li>
 *   <li>{@link FaultMode#DELAYED}: Sleep 50ms then throw (simulates slow DB)</li>
 *   <li>{@link FaultMode#PARTIAL}: Partial operation then throw (simulates mid-transaction failure)</li>
 * </ul>
 * </p>
 *
 * <p>Usage example:
 * <pre>{@code
 * PersistenceFaultInjector injector = new PersistenceFaultInjector();
 * injector.configure(5, FaultMode.IMMEDIATE);  // Fail on 5th call
 *
 * for (int i = 0; i < 10; i++) {
 *     try {
 *         injector.intercept();  // Throws on i==4 (0-indexed call #5)
 *         performPersistenceOperation();
 *     } catch (IOException e) {
 *         handleFault(e);
 *     }
 * }
 *
 * injector.reset();  // Reset for next test scenario
 * }</pre>
 * </p>
 *
 * <p>Thread-safe: Multiple threads can call intercept() concurrently;
 * fault injection is deterministic based on call count.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class PersistenceFaultInjector {

    /**
     * Enumeration of fault injection modes.
     */
    public enum FaultMode {
        /**
         * Throw IOException immediately when call count reaches failOnNthCall.
         */
        IMMEDIATE("Immediate IO failure"),

        /**
         * Sleep 50ms to simulate slow database, then throw IOException.
         * Simulates slow database responses that may trigger timeouts.
         */
        DELAYED("Delayed IO failure after 50ms sleep"),

        /**
         * Perform partial operation then throw.
         * Simulates transaction partially completed before failure.
         * In this implementation, records the fact that work began.
         */
        PARTIAL("Partial operation then failure");

        private final String description;

        FaultMode(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /** Call counter for intercept() invocations. Zero-indexed. */
    private final AtomicInteger callCount = new AtomicInteger(0);

    /** Fail on the N-th call (1-indexed). Initially disabled (MAX_VALUE). */
    private volatile int failOnCall = Integer.MAX_VALUE;

    /** Current fault mode. Initially IMMEDIATE. */
    private volatile FaultMode faultMode = FaultMode.IMMEDIATE;

    /** Work begun flag for PARTIAL mode. */
    private volatile boolean partialWorkBegan = false;

    /**
     * Constructs a new PersistenceFaultInjector with no faults configured.
     */
    public PersistenceFaultInjector() {
    }

    /**
     * Configures the injector to fail on the N-th call with specified fault mode.
     *
     * @param failOnNthCall The call number (1-indexed) when fault should occur.
     *                      Use Integer.MAX_VALUE to disable faults.
     * @param mode The fault injection mode (IMMEDIATE, DELAYED, or PARTIAL)
     * @throws IllegalArgumentException if failOnNthCall is negative
     */
    public void configure(int failOnNthCall, FaultMode mode) {
        if (failOnNthCall < 0) {
            throw new IllegalArgumentException("failOnNthCall must be >= 0");
        }
        this.failOnCall = failOnNthCall;
        this.faultMode = mode != null ? mode : FaultMode.IMMEDIATE;
        this.partialWorkBegan = false;
    }

    /**
     * Configures the injector with immediate fault mode.
     *
     * @param failOnNthCall The call number (1-indexed) when fault should occur
     */
    public void configure(int failOnNthCall) {
        configure(failOnNthCall, FaultMode.IMMEDIATE);
    }

    /**
     * Intercepts a persistence operation and throws a fault if conditions are met.
     *
     * <p>This method should be called at strategic points in persistence code
     * (e.g., at the start of startCase, checkInWorkItem, or cancelCase). The call
     * counter increments on each invocation, and when it reaches failOnNthCall,
     * the configured fault is thrown.</p>
     *
     * @throws IOException If the call count matches failOnNthCall
     */
    public void intercept() throws IOException {
        int callNum = callCount.incrementAndGet();

        if (callNum == failOnCall) {
            switch (faultMode) {
                case IMMEDIATE:
                    throw new IOException(
                            "Injected persistence fault (IMMEDIATE) on call #" + callNum);

                case DELAYED:
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    throw new IOException(
                            "Injected persistence fault (DELAYED after 50ms) on call #" + callNum);

                case PARTIAL:
                    partialWorkBegan = true;
                    throw new IOException(
                            "Injected persistence fault (PARTIAL after partial work) on call #" + callNum);

                default:
                    throw new IOException("Injected persistence fault (unknown mode) on call #" + callNum);
            }
        }
    }

    /**
     * Resets the fault injector to its initial state.
     * Call count returns to 0, and failOnCall is set to MAX_VALUE (disabled).
     */
    public void reset() {
        callCount.set(0);
        failOnCall = Integer.MAX_VALUE;
        faultMode = FaultMode.IMMEDIATE;
        partialWorkBegan = false;
    }

    /**
     * Returns the current call count.
     *
     * @return Number of intercept() calls made so far (0-indexed)
     */
    public int getCallCount() {
        return callCount.get();
    }

    /**
     * Returns the configured fail-on-call value.
     *
     * @return The N-th call (1-indexed) on which fault should occur
     */
    public int getFailOnCall() {
        return failOnCall;
    }

    /**
     * Returns the current fault mode.
     *
     * @return The configured FaultMode
     */
    public FaultMode getFaultMode() {
        return faultMode;
    }

    /**
     * Returns whether partial work was begun in PARTIAL mode.
     *
     * @return True if PARTIAL mode fault was triggered
     */
    public boolean didPartialWorkBegin() {
        return partialWorkBegan;
    }

    /**
     * Returns a string description of the injector's current configuration.
     *
     * @return Configuration summary
     */
    @Override
    public String toString() {
        return String.format(
                "PersistenceFaultInjector{callCount=%d, failOnCall=%d, mode=%s, partialWorkBegan=%b}",
                callCount.get(),
                failOnCall,
                faultMode.getDescription(),
                partialWorkBegan);
    }

    /**
     * Disables fault injection.
     * Equivalent to calling configure(Integer.MAX_VALUE).
     */
    public void disable() {
        failOnCall = Integer.MAX_VALUE;
    }

    /**
     * Checks if fault injection is currently enabled.
     *
     * @return True if a finite failOnCall value is set (not MAX_VALUE)
     */
    public boolean isEnabled() {
        return failOnCall != Integer.MAX_VALUE;
    }
}
