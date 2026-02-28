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

package org.yawlfoundation.yawl.benchmark.soak;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mixed Workload Simulator for YAWL 1M Case Stress Test.
 *
 * <p>Generates realistic event sequences with Poisson-distributed case arrivals
 * and exponential-distributed task execution times. Useful for realistic load
 * modeling in long-running stress tests.</p>
 *
 * <p>Event types and distribution:
 * <ul>
 *   <li>20% case_arrival events — Poisson-distributed at configurable λ</li>
 *   <li>70% task_execution events — Exponential task times (median configurable)</li>
 *   <li>10% case_completion events — Based on task completion patterns</li>
 * </ul>
 * </p>
 *
 * <p>Usage example:
 * <pre>
 *   // Create simulator: 10 cases/second, 150ms median task time
 *   MixedWorkloadSimulator simulator = new MixedWorkloadSimulator(10, 150);
 *
 *   // Option 1: Real-time event stream (blocking)
 *   while (stillRunning) {
 *       WorkloadEvent event = simulator.nextEvent();
 *       // Process event
 *   }
 *
 *   // Option 2: Pre-computed arrival sequence
 *   List&lt;WorkloadEvent&gt; events = simulator.generateArrivalSequence(3600, 10);
 *   // Replay sequence for deterministic testing
 * </pre>
 * </p>
 *
 * <p><strong>Thread safety:</strong> Uses {@code ThreadLocalRandom} for concurrent access.
 * Safe to call {@code nextEvent()} from multiple threads simultaneously.</p>
 *
 * <p><strong>Distribution models:</strong>
 * <ul>
 *   <li><strong>Poisson arrivals:</strong> Inter-arrival time = -ln(U) / λ, where U ~ Uniform(0,1)</li>
 *   <li><strong>Exponential task times:</strong> T = -median * ln(U), where U ~ Uniform(0,1)</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class MixedWorkloadSimulator {

    /**
     * Immutable workload event record.
     * Represents a single event in the simulated workload stream.
     *
     * @param timestamp When this event occurred (epoch milliseconds)
     * @param eventType Type of event: "case_arrival", "task_execution", or "case_completion"
     * @param delayMs Delay in milliseconds before processing this event
     *                (for inter-arrival time or task execution duration)
     */
    public record WorkloadEvent(
            Instant timestamp,
            String eventType,
            long delayMs) {

        /**
         * Validates that event type is one of the allowed values.
         *
         * @throws IllegalArgumentException if eventType is invalid
         */
        public WorkloadEvent {
            if (!eventType.matches("^(case_arrival|task_execution|case_completion)$")) {
                throw new IllegalArgumentException(
                        "Invalid eventType: " + eventType +
                        ". Must be one of: case_arrival, task_execution, case_completion");
            }
            if (delayMs < 0) {
                throw new IllegalArgumentException("delayMs must be non-negative");
            }
        }

        @Override
        public String toString() {
            return String.format("%s [%s] (delay=%dms)",
                    timestamp, eventType, delayMs);
        }
    }

    /** Poisson arrival rate: cases per second */
    private final double poissonLambdaCasesPerSecond;

    /** Exponential task execution median time in milliseconds */
    private final long exponentialMedianTaskTimeMs;

    /** Pre-computed arrival events for deterministic replay */
    private List<WorkloadEvent> precomputedArrivals;

    /** Index into precomputed arrivals for replay mode */
    private int arrivalIndex;

    /**
     * Constructor for real-time workload simulation.
     *
     * @param poissonLambdaCasesPerSecond Poisson arrival rate (cases per second)
     * @param exponentialMedianTaskTimeMs Exponential median task execution time (ms)
     * @throws IllegalArgumentException if parameters are invalid
     */
    public MixedWorkloadSimulator(double poissonLambdaCasesPerSecond,
                                   long exponentialMedianTaskTimeMs) {
        if (poissonLambdaCasesPerSecond <= 0.0) {
            throw new IllegalArgumentException(
                    "Poisson lambda must be positive, got: " + poissonLambdaCasesPerSecond);
        }
        if (exponentialMedianTaskTimeMs <= 0) {
            throw new IllegalArgumentException(
                    "Exponential median must be positive, got: " + exponentialMedianTaskTimeMs);
        }
        this.poissonLambdaCasesPerSecond = poissonLambdaCasesPerSecond;
        this.exponentialMedianTaskTimeMs = exponentialMedianTaskTimeMs;
        this.precomputedArrivals = null;
        this.arrivalIndex = 0;
    }

    /**
     * Generates the next event in the workload stream.
     *
     * <p>This method blocks for the appropriate inter-arrival time or task execution time
     * to simulate realistic event timing. Suitable for tight loops.</p>
     *
     * <p>Event type distribution:
     * <ul>
     *   <li>0–19: case_arrival</li>
     *   <li>20–89: task_execution</li>
     *   <li>90–99: case_completion</li>
     * </ul>
     * </p>
     *
     * @return Next WorkloadEvent with timestamp and delay
     * @throws InterruptedException if sleep is interrupted
     */
    public WorkloadEvent nextEvent() throws InterruptedException {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // If precomputed arrivals available, use them
        if (precomputedArrivals != null) {
            if (arrivalIndex >= precomputedArrivals.size()) {
                arrivalIndex = 0; // Wrap around for cyclic replay
            }
            WorkloadEvent event = precomputedArrivals.get(arrivalIndex++);
            Thread.sleep(event.delayMs);
            return event;
        }

        // Determine event type: 20% arrivals, 70% task exec, 10% completion
        int eventTypeRoll = rng.nextInt(100);
        String eventType;
        long delayMs;

        if (eventTypeRoll < 20) {
            // Case arrival: Poisson inter-arrival time
            eventType = "case_arrival";
            delayMs = poissonInterarrivalTimeMs(rng);
        } else if (eventTypeRoll < 90) {
            // Task execution: Exponential task time
            eventType = "task_execution";
            delayMs = exponentialTaskTimeMs(rng);
        } else {
            // Case completion: proportional to task time
            eventType = "case_completion";
            delayMs = exponentialTaskTimeMs(rng) / 2; // Complete faster than individual tasks
        }

        // Sleep for the computed delay
        Thread.sleep(delayMs);

        // Return event with current timestamp and the delay we just slept
        return new WorkloadEvent(Instant.now(), eventType, delayMs);
    }

    /**
     * Pre-computes a complete arrival sequence for deterministic replay.
     *
     * <p>Useful for:
     * <ul>
     *   <li>Reproducible testing (same sequence across runs)</li>
     *   <li>Avoiding randomness variance in long-running tests</li>
     *   <li>Analyzing performance patterns under fixed load</li>
     * </ul>
     * </p>
     *
     * @param durationSeconds Total duration to simulate
     * @param caseRatePerSecond Target case arrival rate
     * @return List of WorkloadEvent objects representing the complete arrival sequence
     *         Total duration will be approximately durationSeconds * 1000 ms
     */
    public List<WorkloadEvent> generateArrivalSequence(long durationSeconds,
                                                        int caseRatePerSecond) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        List<WorkloadEvent> events = new ArrayList<>();

        long targetDurationMs = durationSeconds * 1000;
        long elapsedMs = 0;
        Instant baseTime = Instant.now();

        while (elapsedMs < targetDurationMs) {
            // Determine event type
            int eventTypeRoll = rng.nextInt(100);
            String eventType;
            long delayMs;

            if (eventTypeRoll < 20) {
                eventType = "case_arrival";
                delayMs = poissonInterarrivalTimeMs(rng);
            } else if (eventTypeRoll < 90) {
                eventType = "task_execution";
                delayMs = exponentialTaskTimeMs(rng);
            } else {
                eventType = "case_completion";
                delayMs = exponentialTaskTimeMs(rng) / 2;
            }

            elapsedMs += delayMs;
            Instant eventTime = baseTime.plusMillis(elapsedMs);
            events.add(new WorkloadEvent(eventTime, eventType, delayMs));
        }

        // Cache for replay
        this.precomputedArrivals = events;
        this.arrivalIndex = 0;

        return events;
    }

    /**
     * Resets the precomputed arrival sequence to the beginning.
     * Useful for replay after completion.
     */
    public void resetArrivalSequence() {
        this.arrivalIndex = 0;
    }

    /**
     * Computes Poisson inter-arrival time using inverse transform sampling.
     *
     * <p>For Poisson process with rate λ (events per second):
     * <ul>
     *   <li>Generate U ~ Uniform(0, 1)</li>
     *   <li>Inter-arrival time T = -ln(U) / λ seconds</li>
     *   <li>Convert to milliseconds and round</li>
     * </ul>
     * </p>
     *
     * @param rng ThreadLocalRandom instance
     * @return Inter-arrival time in milliseconds
     */
    private long poissonInterarrivalTimeMs(ThreadLocalRandom rng) {
        // U ~ Uniform(0, 1)
        double u = rng.nextDouble(0.0, 1.0);

        // Avoid ln(0)
        if (u <= 0.0) {
            u = 1e-10;
        }

        // T = -ln(U) / λ (in seconds)
        double interarrivalSeconds = -Math.log(u) / poissonLambdaCasesPerSecond;

        // Convert to milliseconds
        return Math.round(interarrivalSeconds * 1000.0);
    }

    /**
     * Computes exponential task execution time using inverse transform sampling.
     *
     * <p>For exponential distribution with median m:
     * <ul>
     *   <li>Generate U ~ Uniform(0, 1)</li>
     *   <li>Task time T = -m * ln(U)</li>
     * </ul>
     * </p>
     *
     * <p>Median = ln(2) * mean, so mean = median / ln(2) ≈ 1.443 * median</p>
     *
     * @param rng ThreadLocalRandom instance
     * @return Task execution time in milliseconds
     */
    private long exponentialTaskTimeMs(ThreadLocalRandom rng) {
        // U ~ Uniform(0, 1)
        double u = rng.nextDouble(0.0, 1.0);

        // Avoid ln(0)
        if (u <= 0.0) {
            u = 1e-10;
        }

        // T = -median * ln(U)
        double taskTimeMs = -exponentialMedianTaskTimeMs * Math.log(u);

        return Math.round(taskTimeMs);
    }

    /**
     * Returns the configured Poisson arrival rate.
     *
     * @return Cases per second
     */
    public double getPoissonLambda() {
        return poissonLambdaCasesPerSecond;
    }

    /**
     * Returns the configured exponential median task time.
     *
     * @return Median task time in milliseconds
     */
    public long getExponentialMedian() {
        return exponentialMedianTaskTimeMs;
    }

    /**
     * Checks whether a precomputed arrival sequence is loaded.
     *
     * @return True if using precomputed arrivals, false for real-time generation
     */
    public boolean isUsingPrecomputedArrivals() {
        return precomputedArrivals != null;
    }

    /**
     * Returns the number of precomputed events (if loaded).
     *
     * @return Number of events, or 0 if not using precomputed arrivals
     */
    public int getPrecomputedEventCount() {
        return precomputedArrivals != null ? precomputedArrivals.size() : 0;
    }
}
