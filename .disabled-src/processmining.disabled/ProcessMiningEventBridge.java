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
import org.yawlfoundation.yawl.engine.A2ACaseMonitor;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.observability.BottleneckDetector;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Event bridge connecting A2A case monitor events to process mining analysis.
 *
 * <p>This bridge acts as the integration point between the A2A case monitoring layer
 * and the process mining analysis layer. It:</p>
 * <ul>
 *   <li>Captures case execution events from A2ACaseMonitor</li>
 *   <li>Feeds events into ProcessMiningFacade for continuous analysis</li>
 *   <li>Computes performance metrics (flow time, throughput, activity counts)</li>
 *   <li>Detects workflow bottlenecks when thresholds exceeded (>20%)</li>
 *   <li>Triggers bottleneck alerts to listeners (evolution engine)</li>
 * </ul>
 *
 * <p><b>Event Flow</b>
 * <pre>
 * A2ACaseMonitor (case completion)
 *   ↓
 * WorkflowEventStore (event persistence)
 *   ↓
 * ProcessMiningEventBridge (polling)
 *   ↓
 * ProcessMiningFacade.analyzeFromEventStore()
 *   ↓
 * DFG analysis + performance metrics
 *   ↓
 * BottleneckDetector (threshold check >20%)
 *   ↓
 * Bottleneck alerts → WorkflowEvolutionEngine
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ProcessMiningEventBridge {

    private static final Logger _logger = LogManager.getLogger(ProcessMiningEventBridge.class);
    private static final long ANALYSIS_INTERVAL_MS = 30000; // 30 seconds

    private final ProcessMiningFacade processMiningFacade;
    private final BottleneckDetector bottleneckDetector;
    private final WorkflowEventStore eventStore;
    private final ScheduledExecutorService analysisScheduler;
    private final Map<String, ProcessMiningSession> activeSessions;
    private final Map<String, CaseMetadata> caseMetadata;
    private volatile boolean enabled = false;

    /**
     * Metadata about a case execution.
     */
    private static class CaseMetadata {
        final String caseId;
        final String specificationId;
        final Instant startTime;
        Instant lastAnalyzedAt;
        long totalEventsProcessed = 0;

        CaseMetadata(String caseId, String specificationId) {
            this.caseId = caseId;
            this.specificationId = specificationId;
            this.startTime = Instant.now();
            this.lastAnalyzedAt = startTime;
        }
    }

    /**
     * Creates a new event bridge.
     *
     * @param facade process mining facade
     * @param detector bottleneck detector
     * @param eventStore workflow event store
     * @param scheduler scheduled executor for periodic analysis
     * @throws NullPointerException if any parameter is null
     */
    public ProcessMiningEventBridge(
            ProcessMiningFacade facade,
            BottleneckDetector detector,
            WorkflowEventStore eventStore,
            ScheduledExecutorService scheduler) {
        this.processMiningFacade = Objects.requireNonNull(facade, "facade required");
        this.bottleneckDetector = Objects.requireNonNull(detector, "detector required");
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore required");
        this.analysisScheduler = Objects.requireNonNull(scheduler, "scheduler required");
        this.activeSessions = new ConcurrentHashMap<>();
        this.caseMetadata = new ConcurrentHashMap<>();
    }

    /**
     * Enable event bridge monitoring.
     *
     * <p>Starts periodic analysis of case events. Analysis runs every 30 seconds
     * and updates bottleneck metrics.</p>
     */
    public void enable() {
        if (enabled) {
            _logger.warn("Event bridge already enabled");
            return;
        }

        enabled = true;
        _logger.info("ProcessMiningEventBridge enabled - starting periodic analysis");

        analysisScheduler.scheduleAtFixedRate(
            this::analyzeAllActiveCases,
            ANALYSIS_INTERVAL_MS,
            ANALYSIS_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Disable event bridge monitoring.
     */
    public void disable() {
        enabled = false;
        _logger.info("ProcessMiningEventBridge disabled");
    }

    /**
     * Register a case for monitoring.
     *
     * <p>Creates a new ProcessMiningSession for the case and starts event capture.</p>
     *
     * @param caseId case identifier
     * @param specificationId specification identifier
     * @throws NullPointerException if parameters are null
     */
    public void registerCase(String caseId, String specificationId) {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(specificationId, "specificationId required");

        if (!enabled) {
            _logger.debug("Event bridge disabled, skipping case registration for {}", caseId);
            return;
        }

        // Create session for this specification
        ProcessMiningSession session = activeSessions.computeIfAbsent(
            specificationId,
            spec -> ProcessMiningSession.start(spec)
        );

        // Create case metadata
        caseMetadata.put(caseId, new CaseMetadata(caseId, specificationId));

        _logger.debug("Registered case {} for mining (spec={})", caseId, specificationId);
    }

    /**
     * Mark a case as completed.
     *
     * <p>Triggers immediate analysis for the completed case and updates metrics.</p>
     *
     * @param caseId case identifier
     * @throws NullPointerException if caseId is null
     */
    public void onCaseCompleted(String caseId) {
        Objects.requireNonNull(caseId, "caseId required");

        if (!enabled) {
            return;
        }

        CaseMetadata meta = caseMetadata.get(caseId);
        if (meta == null) {
            _logger.warn("Case completed but not registered: {}", caseId);
            return;
        }

        try {
            // Analyze this case's events
            ProcessMiningFacade.ProcessMiningReport report =
                processMiningFacade.analyzeFromEventStore(caseId, eventStore);

            // Update session with metrics
            ProcessMiningSession session = activeSessions.get(meta.specificationId);
            if (session != null) {
                ProcessMiningSession updated = session.withMetrics(
                    report.traceCount,
                    report.conformance != null ? report.conformance.computeFitness() : 0.0,
                    0.0, // precision - requires net comparison
                    report.performance.avgFlowTimeMs()
                );
                activeSessions.put(meta.specificationId, updated);
            }

            // Feed performance metrics to bottleneck detector
            updateBottleneckMetrics(meta, report);

            meta.totalEventsProcessed += report.traceCount;
            meta.lastAnalyzedAt = Instant.now();

            _logger.debug("Analyzed case {} in {} traces, {} variants",
                caseId, report.traceCount, report.variantCount);

        } catch (Exception e) {
            _logger.error("Error analyzing case {}: {}", caseId, e.getMessage(), e);
        }
    }

    /**
     * Periodically analyze all active cases and update bottleneck metrics.
     *
     * <p>Called every 30 seconds by the analysis scheduler. For each specification
     * with active cases, runs conformance and performance analysis, then updates
     * the bottleneck detector with metrics.</p>
     */
    private void analyzeAllActiveCases() {
        if (!enabled || activeSessions.isEmpty()) {
            return;
        }

        for (String specId : activeSessions.keySet()) {
            try {
                analyzeSpecification(specId);
            } catch (Exception e) {
                _logger.error("Error analyzing specification {}: {}", specId, e.getMessage(), e);
            }
        }
    }

    /**
     * Analyze all cases for a specific specification.
     *
     * @param specificationId specification to analyze
     */
    private void analyzeSpecification(String specificationId) throws Exception {
        // Get all cases for this spec from metadata
        List<CaseMetadata> specCases = caseMetadata.values().stream()
            .filter(m -> m.specificationId.equals(specificationId))
            .toList();

        if (specCases.isEmpty()) {
            return;
        }

        // Aggregate metrics across all cases
        Map<String, TaskMetrics> aggregatedTaskMetrics = new HashMap<>();

        for (CaseMetadata caseMetadata : specCases) {
            try {
                ProcessMiningFacade.ProcessMiningReport report =
                    processMiningFacade.analyzeFromEventStore(caseMetadata.caseId, eventStore);

                // Aggregate metrics by activity
                for (Map.Entry<String, Long> variant : report.variantFrequencies.entrySet()) {
                    String[] activities = variant.getKey().split(",");
                    for (String activity : activities) {
                        aggregatedTaskMetrics.computeIfAbsent(activity, k -> new TaskMetrics())
                            .recordExecution(report.performance.avgFlowTimeMs(), 0);
                    }
                }
            } catch (Exception e) {
                _logger.warn("Could not analyze case {}: {}", caseMetadata.caseId, e.getMessage());
            }
        }

        // Update bottleneck detector with aggregated metrics
        for (Map.Entry<String, TaskMetrics> entry : aggregatedTaskMetrics.entrySet()) {
            String taskName = entry.getKey();
            TaskMetrics metrics = entry.getValue();
            bottleneckDetector.recordTaskExecution(
                specificationId,
                taskName,
                (long) metrics.avgDurationMs,
                metrics.waitTimeMs
            );
        }

        _logger.debug("Analyzed {} cases for spec {}", specCases.size(), specificationId);
    }

    /**
     * Update bottleneck detector with metrics from mining report.
     *
     * @param caseMetadata metadata about the case
     * @param report mining report with performance metrics
     */
    private void updateBottleneckMetrics(CaseMetadata caseMetadata, ProcessMiningFacade.ProcessMiningReport report) {
        // For each activity in variants, record execution
        long avgFlowMs = (long) report.performance.avgFlowTimeMs();

        for (String variant : report.variantFrequencies.keySet()) {
            String[] activities = variant.split(",");
            for (String activity : activities) {
                // Estimate per-activity time (simplified: divide flow time by activity count)
                long estimatedActivityTimeMs = Math.max(100, avgFlowMs / Math.max(1, activities.length));

                bottleneckDetector.recordTaskExecution(
                    caseMetadata.specificationId,
                    activity,
                    estimatedActivityTimeMs,
                    0 // wait time not available from simple event log
                );
            }
        }
    }

    /**
     * Get current session for a specification.
     *
     * @param specificationId specification identifier
     * @return session or null if not found
     */
    public ProcessMiningSession getSession(String specificationId) {
        return activeSessions.get(specificationId);
    }

    /**
     * Get all active sessions.
     *
     * @return map of specification ID to session
     */
    public Map<String, ProcessMiningSession> getActiveSessions() {
        return new HashMap<>(activeSessions);
    }

    /**
     * Get bridge statistics.
     *
     * @return map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("enabled", enabled);
        stats.put("activeSessions", activeSessions.size());
        stats.put("monitoredCases", caseMetadata.size());
        stats.put("analysisIntervalMs", ANALYSIS_INTERVAL_MS);

        long totalEventsProcessed = caseMetadata.values().stream()
            .mapToLong(m -> m.totalEventsProcessed)
            .sum();
        stats.put("totalEventsProcessed", totalEventsProcessed);

        return stats;
    }

    /**
     * Simple task metrics container.
     */
    private static class TaskMetrics {
        double totalDuration = 0;
        long executions = 0;
        double avgDurationMs = 0;
        long waitTimeMs = 0;

        void recordExecution(double durationMs, long waitMs) {
            totalDuration += durationMs;
            executions++;
            avgDurationMs = totalDuration / executions;
            waitTimeMs = waitMs;
        }
    }
}
