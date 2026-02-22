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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.processmining.discovery;

import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.Ocel2EventLog;
import org.yawlfoundation.yawl.integration.processmining.ProcessDiscoveryResult;

import java.util.Set;
import java.util.List;

/**
 * Interface for process discovery algorithms.
 * Defines the contract for discovering process models from event logs.
 *
 * <h2>Algorithm Types</h2>
 * <ul>
 *   <li><strong>ALPHA</strong> - Alpha algorithm based on footprint matrix</li>
 *   <li><strong>HEURISTIC</strong> - Heuristic miner based on frequency and dependencies</li>
 *   <li><strong>INDUCTIVE</strong> - Inductive miner for process trees</li>
 *   <li><strong>DFGBASED</strong> - Directly-follows graph based mining</li>
 *   <li><strong>ILP</strong> - Integer Linear Programming approach</li>
 * </ul>
 *
 * <h2>Process Mining Context</h2>
 * Contains all necessary information for process discovery:
 * <pre>
 * ProcessMiningContext context = new ProcessMiningContext.Builder()
 *     .eventLog(eventLog)
 *     .algorithm(ALPHA)
 *     .noiseThreshold(0.1)  // Allow 10% noise
 *     .frequencyThreshold(0.8)  // Include activities in 80% of traces
 *     .build();
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create discovery context
 * ProcessMiningContext context = new ProcessMiningContext.Builder()
 *     .eventLog(xesLog)
 *     .algorithm(HEURISTIC)
 *     .build();
 *
 * // Create and run algorithm
 * ProcessDiscoveryAlgorithm algorithm = new HeuristicMiner();
 * ProcessDiscoveryResult result = algorithm.discover(context);
 *
 * // Process results
 * System.out.println("Discovered model: " + result.getProcessModelJson());
 * System.out.println("Fitness: " + result.getFitness());
 * System.out.println("Precision: " + result.getPrecision());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public interface ProcessDiscoveryAlgorithm {

    /**
     * Discover a process model from the given event log.
     *
     * @param context the process mining context containing event log and parameters
     * @return the discovery result containing the discovered model and metrics
     * @throws ProcessDiscoveryException if discovery fails
     */
    ProcessDiscoveryResult discover(ProcessMiningContext context) throws ProcessDiscoveryException;

    /**
     * Get the name of this algorithm.
     *
     * @return the algorithm name
     */
    String getAlgorithmName();

    /**
     * Get the type of this algorithm.
     *
     * @return the algorithm type
     */
    AlgorithmType getType();

    /**
     * Check if this algorithm supports incremental discovery.
     *
     * @return true if incremental discovery is supported, false otherwise
     */
    default boolean supportsIncrementalDiscovery() {
        return false;
    }

    /**
     * Perform incremental discovery on an existing model.
     *
     * @param existingModel the existing model to enhance
     * @param newEvents the new events to incorporate
     * @return the enhanced model
     * @throws ProcessDiscoveryException if discovery fails
     * @throws UnsupportedOperationException if incremental discovery is not supported
     */
    default ProcessDiscoveryResult incrementalDiscovery(
            ProcessDiscoveryResult existingModel,
            List<ProcessEvent> newEvents) throws ProcessDiscoveryException {
        throw new UnsupportedOperationException("Incremental discovery not supported");
    }

    /**
     * Algorithm types.
     */
    enum AlgorithmType {
        ALPHA,           // Alpha algorithm
        HEURISTIC,       // Heuristic miner
        INDUCTIVE,       // Inductive miner
        DFGBASED,        // Directly-follows graph based
        ILP              // Integer Linear Programming
    }

    /**
     * Process mining context for discovery operations.
     */
    class ProcessMiningContext {
        private final Ocel2EventLog eventLog;
        private final AlgorithmType algorithm;
        private final double noiseThreshold;
        private final double frequencyThreshold;
        private final Set<String> activityFilter;
        private final ProcessMiningSettings settings;

        private ProcessMiningContext(Builder builder) {
            this.eventLog = builder.eventLog;
            this.algorithm = builder.algorithm;
            this.noiseThreshold = builder.noiseThreshold;
            this.frequencyThreshold = builder.frequencyThreshold;
            this.activityFilter = builder.activityFilter;
            this.settings = builder.settings;
        }

        // Getters
        public Ocel2EventLog getEventLog() { return eventLog; }
        public AlgorithmType getAlgorithm() { return algorithm; }
        public double getNoiseThreshold() { return noiseThreshold; }
        public double getFrequencyThreshold() { return frequencyThreshold; }
        public Set<String> getActivityFilter() { return activityFilter; }
        public ProcessMiningSettings getSettings() { return settings; }

        /**
         * Builder for creating ProcessMiningContext instances.
         */
        public static class Builder {
            private Ocel2EventLog eventLog;
            private AlgorithmType algorithm;
            private double noiseThreshold = 0.1;
            private double frequencyThreshold = 0.8;
            private Set<String> activityFilter;
            private ProcessMiningSettings settings = new ProcessMiningSettings();

            public Builder eventLog(Ocel2EventLog eventLog) {
                this.eventLog = eventLog;
                return this;
            }

            public Builder algorithm(AlgorithmType algorithm) {
                this.algorithm = algorithm;
                return this;
            }

            public Builder noiseThreshold(double threshold) {
                this.noiseThreshold = threshold;
                return this;
            }

            public Builder frequencyThreshold(double threshold) {
                this.frequencyThreshold = threshold;
                return this;
            }

            public Builder activityFilter(Set<String> filter) {
                this.activityFilter = filter;
                return this;
            }

            public Builder settings(ProcessMiningSettings settings) {
                this.settings = settings;
                return this;
            }

            public ProcessMiningContext build() {
                if (eventLog == null) {
                    throw new IllegalArgumentException("Event log cannot be null");
                }
                if (algorithm == null) {
                    throw new IllegalArgumentException("Algorithm cannot be null");
                }
                return new ProcessMiningContext(this);
            }
        }
    }

    /**
     * Settings for process mining algorithms.
     */
    class ProcessMiningSettings {
        private boolean enablePruning = true;
        private boolean enableNoiseReduction = true;
        private boolean enableParallelProcessing = true;
        private int maxIterations = 100;
        private double convergenceThreshold = 0.001;
        private long timeoutMillis = 30000; // 30 seconds

        // Getters and setters
        public boolean isEnablePruning() { return enablePruning; }
        public void setEnablePruning(boolean enablePruning) { this.enablePruning = enablePruning; }

        public boolean isEnableNoiseReduction() { return enableNoiseReduction; }
        public void setEnableNoiseReduction(boolean enableNoiseReduction) { this.enableNoiseReduction = enableNoiseReduction; }

        public boolean isEnableParallelProcessing() { return enableParallelProcessing; }
        public void setEnableParallelProcessing(boolean enableParallelProcessing) { this.enableParallelProcessing = enableParallelProcessing; }

        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }

        public double getConvergenceThreshold() { return convergenceThreshold; }
        public void setConvergenceThreshold(double convergenceThreshold) { this.convergenceThreshold = convergenceThreshold; }

        public long getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    }

    /**
     * Individual process event.
     */
    class ProcessEvent {
        private final String id;
        private final String activity;
        private final Instant timestamp;
        private final String caseId;
        private final Map<String, Object> properties;

        public ProcessEvent(String id, String activity, Instant timestamp, String caseId, Map<String, Object> properties) {
            this.id = id;
            this.activity = activity;
            this.timestamp = timestamp;
            this.caseId = caseId;
            this.properties = properties != null ? properties : new HashMap<>();
        }

        // Getters
        public String getId() { return id; }
        public String getActivity() { return activity; }
        public Instant getTimestamp() { return timestamp; }
        public String getCaseId() { return caseId; }
        public Map<String, Object> getProperties() { return properties; }
    }

    /**
     * Exception thrown during process discovery.
     */
    class ProcessDiscoveryException extends Exception {
        public ProcessDiscoveryException(String message) {
            super(message);
        }

        public ProcessDiscoveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}