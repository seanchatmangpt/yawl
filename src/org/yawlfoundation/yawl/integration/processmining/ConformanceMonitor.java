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

package org.yawlfoundation.yawl.integration.processmining;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Continuous conformance monitoring using van der Aalst token-based replay.
 *
 * <p>Monitors workflow conformance in real-time, tracking fitness scores and
 * flagging deviations when traces don't conform to the reference model. Supports
 * van der Aalst's classical approach: fitness ≥ 0.85 indicates acceptable
 * conformance; <0.5 indicates significant deviations.</p>
 *
 * <p><b>Conformance Levels</b>
 * <ul>
 *   <li>fitness ≥ 0.85 → HIGH conformance (process matches spec)</li>
 *   <li>0.5 ≤ fitness < 0.85 → MEDIUM conformance (minor deviations)</li>
 *   <li>fitness < 0.5 → LOW conformance (major deviations)</li>
 * </ul>
 *
 * <p><b>Usage</b>
 * <pre>
 * ConformanceMonitor monitor = new ConformanceMonitor(facade);
 * monitor.registerSpecification(specId, ynet);
 * monitor.onConformanceUpdate(alert -> {
 *     if (alert.fitness < 0.85) {
 *         // Trigger evolution or mitigation
 *     }
 * });
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ConformanceMonitor {

    private static final Logger _logger = LogManager.getLogger(ConformanceMonitor.class);
    private static final double CONFORMANCE_THRESHOLD_HIGH = 0.85;
    private static final double CONFORMANCE_THRESHOLD_MEDIUM = 0.5;

    /**
     * Conformance monitoring alert with fitness metrics.
     */
    public record ConformanceAlert(
            String specificationId,
            double fitness,
            double precision,
            double generalization,
            int tracesConforming,
            int tracesTotal,
            List<String> deviatingCases,
            ConformanceLevel level,
            Instant detectedAt
    ) {
        @Override
        public String toString() {
            return String.format(
                "%s: fitness=%.3f, conforming=%d/%d, level=%s",
                specificationId, fitness, tracesConforming, tracesTotal, level
            );
        }
    }

    /**
     * Conformance level classification.
     */
    public enum ConformanceLevel {
        HIGH, MEDIUM, LOW
    }

    /**
     * Specification monitoring state.
     */
    private static class SpecificationMonitoringState {
        final String specificationId;
        final YNet referenceNet;
        double lastFitness = 0.0;
        double lastPrecision = 0.0;
        double lastGeneralization = 0.0;
        int lastTracesConforming = 0;
        int lastTracesTotal = 0;
        ConformanceLevel lastLevel = ConformanceLevel.MEDIUM;
        Instant lastAnalyzedAt;
        final List<ConformanceAlert> alertHistory;
        boolean deviationDetected = false;

        SpecificationMonitoringState(String specId, YNet net) {
            this.specificationId = specId;
            this.referenceNet = net;
            this.alertHistory = new ArrayList<>();
        }
    }

    private final ProcessMiningFacade facade;
    private final Map<String, SpecificationMonitoringState> specs;
    private final List<Consumer<ConformanceAlert>> listeners;

    /**
     * Creates a new conformance monitor.
     *
     * @param facade process mining facade for analysis
     * @throws NullPointerException if facade is null
     */
    public ConformanceMonitor(ProcessMiningFacade facade) {
        this.facade = Objects.requireNonNull(facade, "facade required");
        this.specs = new ConcurrentHashMap<>();
        this.listeners = new ArrayList<>();
    }

    /**
     * Register a specification for conformance monitoring.
     *
     * @param specId specification identifier
     * @param net YAWL net representing the specification
     * @throws NullPointerException if parameters are null
     */
    public void registerSpecification(String specId, YNet net) {
        Objects.requireNonNull(specId, "specId required");
        Objects.requireNonNull(net, "net required");

        specs.putIfAbsent(specId, new SpecificationMonitoringState(specId, net));
        _logger.info("Registered specification for conformance monitoring: {}", specId);
    }

    /**
     * Check conformance for a specific case.
     *
     * <p>Runs token-based replay conformance checking against the reference net
     * and emits alerts if fitness drops below threshold.</p>
     *
     * @param specId specification identifier
     * @param xesLog XES event log (one or more traces)
     */
    public void checkConformance(String specId, String xesLog) {
        Objects.requireNonNull(specId, "specId required");
        Objects.requireNonNull(xesLog, "xesLog required");

        SpecificationMonitoringState state = specs.get(specId);
        if (state == null) {
            _logger.warn("Specification not registered: {}", specId);
            return;
        }

        try {
            // Run token replay conformance check
            TokenReplayConformanceChecker.TokenReplayResult result =
                TokenReplayConformanceChecker.replay(state.referenceNet, xesLog);

            double fitness = result.computeFitness();
            ConformanceLevel level = classifyConformanceLevel(fitness);

            // Create alert
            ConformanceAlert alert = new ConformanceAlert(
                specId,
                fitness,
                0.0, // precision not computed in simple token replay
                0.0, // generalization not computed
                result.fittingTraces,
                result.traceCount,
                new ArrayList<>(result.deviatingCases),
                level,
                Instant.now()
            );

            // Update state
            state.lastFitness = fitness;
            state.lastLevel = level;
            state.lastTracesConforming = result.fittingTraces;
            state.lastTracesTotal = result.traceCount;
            state.lastAnalyzedAt = Instant.now();
            state.alertHistory.add(alert);

            // Check if deviation detected
            boolean deviationDetected = fitness < CONFORMANCE_THRESHOLD_HIGH;
            if (deviationDetected != state.deviationDetected) {
                state.deviationDetected = deviationDetected;
                if (deviationDetected) {
                    _logger.warn("Conformance deviation detected: {}", alert);
                } else {
                    _logger.info("Conformance restored: {}", alert);
                }
            }

            // Notify listeners
            notifyListeners(alert);

        } catch (Exception e) {
            _logger.error("Conformance check failed for {}: {}", specId, e.getMessage(), e);
        }
    }

    /**
     * Get current conformance level for a specification.
     *
     * @param specId specification identifier
     * @return current conformance alert, or null if not monitored
     */
    public ConformanceAlert getCurrentConformance(String specId) {
        SpecificationMonitoringState state = specs.get(specId);
        if (state == null || state.alertHistory.isEmpty()) {
            return null;
        }
        return state.alertHistory.get(state.alertHistory.size() - 1);
    }

    /**
     * Get conformance history for a specification.
     *
     * @param specId specification identifier
     * @return list of conformance alerts
     */
    public List<ConformanceAlert> getConformanceHistory(String specId) {
        SpecificationMonitoringState state = specs.get(specId);
        if (state == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(state.alertHistory);
    }

    /**
     * Register listener for conformance updates.
     *
     * <p>Listener will be invoked whenever conformance is analyzed, regardless
     * of whether fitness changed.</p>
     *
     * @param listener consumer to receive alerts
     */
    public void onConformanceUpdate(Consumer<ConformanceAlert> listener) {
        Objects.requireNonNull(listener, "listener required");
        listeners.add(listener);
    }

    /**
     * Get conformance statistics.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("monitoredSpecifications", specs.size());

        int totalAlerts = 0;
        int deviationCount = 0;
        double avgFitness = 0.0;

        for (SpecificationMonitoringState state : specs.values()) {
            totalAlerts += state.alertHistory.size();
            if (state.lastFitness < CONFORMANCE_THRESHOLD_HIGH) {
                deviationCount++;
            }
            avgFitness += state.lastFitness;
        }

        if (!specs.isEmpty()) {
            avgFitness /= specs.size();
        }

        stats.put("totalAlerts", totalAlerts);
        stats.put("specificationsWithDeviations", deviationCount);
        stats.put("averageFitness", avgFitness);
        stats.put("conformanceThreshold", CONFORMANCE_THRESHOLD_HIGH);

        return stats;
    }

    /**
     * Classify conformance level based on fitness score.
     *
     * @param fitness van der Aalst fitness score [0.0, 1.0]
     * @return conformance level classification
     */
    private ConformanceLevel classifyConformanceLevel(double fitness) {
        if (fitness >= CONFORMANCE_THRESHOLD_HIGH) {
            return ConformanceLevel.HIGH;
        } else if (fitness >= CONFORMANCE_THRESHOLD_MEDIUM) {
            return ConformanceLevel.MEDIUM;
        } else {
            return ConformanceLevel.LOW;
        }
    }

    /**
     * Notify all listeners of conformance alert.
     *
     * @param alert conformance alert
     */
    private void notifyListeners(ConformanceAlert alert) {
        listeners.forEach(listener -> {
            try {
                listener.accept(alert);
            } catch (Exception e) {
                _logger.error("Conformance listener failed", e);
            }
        });
    }
}
