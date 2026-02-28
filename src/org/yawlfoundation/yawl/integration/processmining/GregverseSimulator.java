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

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * End-to-end process mining orchestrator with synthetic workflow simulation.
 *
 * GregverseSimulator generates synthetic XES event logs (realistic workflow traces),
 * runs local conformance and performance analysis, and runs WASM-based advanced process
 * discovery. Combines all analysis into a single durable ProcessMiningSession with
 * aggregated metrics.
 *
 * <h2>Simulation Flow</h2>
 * <ol>
 *   <li>Generate synthetic XES log with realistic case traces (configurable case count
 *       and activities)</li>
 *   <li>Run ConformanceAnalyzer to compute fitness (how many traces match expected model)</li>
 *   <li>Run PerformanceAnalyzer to extract flow time and throughput metrics</li>
 *   <li>If mining service is healthy: call advanced discovery; otherwise skip gracefully</li>
 *   <li>Return ProcessMiningSession with combined metrics from all analyses</li>
 * </ol>
 *
 * <h2>Synthetic Trace Generation</h2>
 * Traces are generated with:
 * - All activities in sequence (realistic linear workflow)
 * - Timestamps 1 minute apart (configurable within activity)
 * - 5 minutes between cases (for realistic scheduling jitter)
 * - ±30 second jitter per case (simulates resource contention)
 * - Start time: now minus caseCount hours (spreads traces over time)
 *
 * @since YAWL 6.0
 * @author YAWL Foundation
 * @version 6.0
 */
public final class GregverseSimulator implements AutoCloseable {

    private static final Logger logger = LogManager.getLogger(GregverseSimulator.class);
    private final ProcessMiningService miningService;

    /**
     * Construct with given ProcessMiningService.
     *
     * @param miningService WASM-based or custom process mining service
     * @throws IllegalArgumentException if service is null
     */
    public GregverseSimulator(ProcessMiningService miningService) {
        if (miningService == null) {
            throw new IllegalArgumentException("miningService is required");
        }
        this.miningService = miningService;
    }

    /**
     * Create simulator with default WASM-based process mining service.
     *
     * Initializes Rust4pmWasmProcessMiningService for embedded process mining.
     *
     * @return new GregverseSimulator instance
     * @throws IOException if WASM bridge initialization fails
     */
    public static GregverseSimulator withDefaultClient() throws IOException {
        return new GregverseSimulator(new Rust4pmWasmProcessMiningService());
    }

    /**
     * Run end-to-end process mining simulation.
     *
     * <h3>Algorithm</h3>
     * <ol>
     *   <li>Generate synthetic XES log for caseCount cases, each with given activities
     *       in order, timestamped realistically</li>
     *   <li>Run ConformanceAnalyzer with expected activities + directly-follows pairs</li>
     *   <li>Run PerformanceAnalyzer on same XES</li>
     *   <li>If rust4pmClient.isHealthy(): call discoverProcess(xesXml) and log result</li>
     *   <li>Return ProcessMiningSession.start(specificationId).withMetrics(...) combining
     *       all results</li>
     * </ol>
     *
     * @param specificationId YAWL specification identifier
     * @param caseCount number of synthetic cases to generate
     * @param activities ordered list of activities per case
     * @return ProcessMiningSession with combined metrics
     * @throws IOException if analysis fails
     * @throws IllegalArgumentException if parameters are invalid
     */
    public ProcessMiningSession simulate(
            String specificationId,
            int caseCount,
            List<String> activities) throws IOException {

        if (specificationId == null || specificationId.isEmpty()) {
            throw new IllegalArgumentException("specificationId is required");
        }
        if (caseCount <= 0) {
            throw new IllegalArgumentException("caseCount must be > 0");
        }
        if (activities == null || activities.isEmpty()) {
            throw new IllegalArgumentException("activities list is required and non-empty");
        }

        logger.info("GregverseSimulator: generating synthetic XES log for {} cases with {} activities",
                caseCount, activities.size());

        String xesXml = generateXes(specificationId, caseCount, activities);

        logger.debug("GregverseSimulator: running ConformanceAnalyzer");
        Set<String> expectedActivities = new HashSet<>(activities);
        Set<String> expectedDirectlyFollows = buildDirectlyFollowsSet(activities);

        ConformanceAnalyzer conformanceAnalyzer =
                new ConformanceAnalyzer(expectedActivities, expectedDirectlyFollows);
        ConformanceAnalyzer.ConformanceResult conformanceResult =
                conformanceAnalyzer.analyze(xesXml);

        logger.debug("GregverseSimulator: running PerformanceAnalyzer");
        PerformanceAnalyzer performanceAnalyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult performanceResult =
                performanceAnalyzer.analyze(xesXml);

        double precisionScore = computePrecision(conformanceResult);
        logger.info(
                "GregverseSimulator: conformance fitness={}, precision={}, avg flow time={}ms",
                conformanceResult.fitness, precisionScore, performanceResult.avgFlowTimeMs);

        if (miningService.isHealthy()) {
            try {
                logger.info("GregverseSimulator: submitting to process mining service for discovery");
                String discoveryResult = miningService.discoverAlphaPpp(xesXml);
                logger.info("GregverseSimulator: process mining discovery completed: {} bytes", discoveryResult.length());
            } catch (IOException e) {
                logger.warn("GregverseSimulator: process mining discovery failed (continuing gracefully): {}", e.getMessage());
            }
        } else {
            logger.info("GregverseSimulator: process mining service not healthy, skipping discovery");
        }

        ProcessMiningSession session = ProcessMiningSession.start(specificationId)
                .withMetrics(
                        caseCount,
                        conformanceResult.fitness,
                        precisionScore,
                        performanceResult.avgFlowTimeMs);

        logger.info("GregverseSimulator: simulation complete. Session: {}", session.sessionId());
        return session;
    }

    /**
     * Generate synthetic XES event log.
     *
     * <h3>Format</h3>
     * Generates valid XES 1.0 XML with:
     * - Standard XES extensions (Concept, Time)
     * - Log name = specificationId
     * - Traces with case IDs: "case-001", "case-002", etc.
     * - Events with activity names and ISO-8601 timestamps
     * - All activities in order per trace
     *
     * <h3>Timestamps</h3>
     * - Start: Instant.now().minus(caseCount, ChronoUnit.HOURS)
     * - Per activity: increment 60 seconds
     * - Between cases: increment 5 minutes (300 seconds)
     * - Jitter: ±30 seconds per case
     *
     * @param specificationId log name (spec ID)
     * @param caseCount number of traces
     * @param activities ordered activities per trace
     * @return valid XES XML string
     */
    private String generateXes(String specificationId, int caseCount, List<String> activities) {
        StringBuilder xes = new StringBuilder();
        xes.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        xes.append("<log xes.version=\"1.0\" xes.features=\"nested-attributes\" ");
        xes.append("xmlns=\"http://www.xes-standard.org/\">\n");

        xes.append("  <extension name=\"Concept\" prefix=\"concept\" ");
        xes.append("uri=\"http://www.xes-standard.org/concept.xesext\"/>\n");
        xes.append("  <extension name=\"Time\" prefix=\"time\" ");
        xes.append("uri=\"http://www.xes-standard.org/time.xesext\"/>\n");

        xes.append("  <string key=\"concept:name\" value=\"");
        xes.append(escapeXml(specificationId));
        xes.append("\"/>\n");

        Instant baseTime = Instant.now().minus(caseCount, ChronoUnit.HOURS);
        Random random = new Random(12345L); // deterministic for reproducibility

        for (int caseIdx = 1; caseIdx <= caseCount; caseIdx++) {
            int jitterSeconds = random.nextInt(61) - 30; // ±30 seconds
            Instant caseStart = baseTime
                    .plus((long) (caseIdx - 1) * 5, ChronoUnit.MINUTES)
                    .plus(jitterSeconds, ChronoUnit.SECONDS);

            xes.append("  <trace>\n");
            xes.append("    <string key=\"concept:name\" value=\"case-");
            xes.append(String.format("%06d", caseIdx));
            xes.append("\"/>\n");

            Instant eventTime = caseStart;
            for (String activity : activities) {
                xes.append("    <event>\n");
                xes.append("      <string key=\"concept:name\" value=\"");
                xes.append(escapeXml(activity));
                xes.append("\"/>\n");
                xes.append("      <string key=\"time:timestamp\" value=\"");
                xes.append(eventTime.toString());
                xes.append("\"/>\n");
                xes.append("    </event>\n");

                eventTime = eventTime.plus(1, ChronoUnit.MINUTES);
            }

            xes.append("  </trace>\n");
        }

        xes.append("</log>\n");
        return xes.toString();
    }

    /**
     * Compute precision score from conformance result.
     *
     * Precision = fitting traces / total traces (fraction of model behavior observed).
     *
     * @param result ConformanceResult from analyzer
     * @return precision score [0.0, 1.0]
     */
    private double computePrecision(ConformanceAnalyzer.ConformanceResult result) {
        if (result.traceCount == 0) {
            return 1.0;
        }
        return result.fittingTraces / (double) result.traceCount;
    }

    /**
     * Build set of expected directly-follows pairs from activity sequence.
     *
     * For activities [A, B, C], returns {A>>B, B>>C}.
     *
     * @param activities ordered activity list
     * @return set of "activity>>activity" pairs
     */
    private Set<String> buildDirectlyFollowsSet(List<String> activities) {
        Set<String> pairs = new LinkedHashSet<>();
        for (int i = 0; i < activities.size() - 1; i++) {
            pairs.add(activities.get(i) + ">>" + activities.get(i + 1));
        }
        return pairs;
    }

    /**
     * Close the process mining service and release resources.
     *
     * <p>Idempotent: safe to call multiple times.</p>
     */
    @Override
    public void close() {
        if (miningService instanceof AutoCloseable) {
            try {
                ((AutoCloseable) miningService).close();
                logger.info("GregverseSimulator: process mining service closed");
            } catch (Exception e) {
                logger.warn("GregverseSimulator: error closing process mining service: {}", e.getMessage());
            }
        }
    }

    /**
     * Escape XML special characters.
     *
     * Escapes: &, <, >, ", '
     *
     * @param text input string
     * @return XML-safe string
     * @throws IllegalArgumentException if text is null
     */
    private String escapeXml(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null for XML escaping");
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
