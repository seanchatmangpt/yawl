/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.processmining.EventLogExporter;
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.processmining.ProcessVariantAnalyzer;
import org.yawlfoundation.yawl.integration.processmining.SocialNetworkAnalyzer;

/**
 * A2A Skill for analyzing YAWL workflow event logs using process mining techniques.
 *
 * <p>Provides 6 types of analysis:
 * <ul>
 *   <li><b>performance</b> (default) - Flow time, throughput, activity metrics</li>
 *   <li><b>full</b> - Comprehensive report: performance + conformance + variants + OCEL</li>
 *   <li><b>variants</b> - Top process variants and deviating cases</li>
 *   <li><b>xes</b> - Export event log in eXtensible Event Stream (XES) format</li>
 *   <li><b>social_network</b> - Handover-of-work network analysis</li>
 * </ul>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code specIdentifier} (required) - Specification identifier to analyze</li>
 *   <li>{@code specVersion} (optional, default "0.1") - Specification version</li>
 *   <li>{@code specUri} (optional, default = specIdentifier) - Specification URI</li>
 *   <li>{@code analysisType} (optional, default "performance") - Type of analysis</li>
 *   <li>{@code withData} (optional, default "false") - Include data attributes in export</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code process-mining:read}
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class ProcessMiningSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(ProcessMiningSkill.class);
    private static final String SKILL_ID = "process_mining_analyze";
    private static final String SKILL_NAME = "Process Mining Analyze";
    private static final String SKILL_DESCRIPTION =
        "Analyze YAWL workflow event logs for performance, variants, conformance, " +
        "and social network patterns. Supports XES export, performance metrics, " +
        "variant discovery, and handover-of-work analysis.";

    private final String engineUrl;
    private final String username;
    private final String password;

    /**
     * Create a process mining skill connected to a YAWL engine.
     *
     * @param engineUrl Base URL of YAWL engine (e.g., "http://localhost:8080/yawl")
     * @param username  Username for authentication
     * @param password  Password for authentication
     */
    public ProcessMiningSkill(String engineUrl, String username, String password) {
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
    }

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("workflow:read", "process-mining:read");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String specIdentifier = request.getParameter("specIdentifier");
        if (specIdentifier == null || specIdentifier.isEmpty()) {
            return SkillResult.error("Parameter 'specIdentifier' is required");
        }

        String specVersion = request.getParameter("specVersion", "0.1");
        String specUri = request.getParameter("specUri", specIdentifier);
        String analysisType = request.getParameter("analysisType", "performance");
        String withDataStr = request.getParameter("withData", "false");
        boolean withData = "true".equalsIgnoreCase(withDataStr);

        _logger.info("Process mining analysis requested: spec={}, type={}",
                     specIdentifier, analysisType);

        try {
            return executeAnalysis(specIdentifier, specVersion, specUri,
                                   analysisType, withData);
        } catch (Exception e) {
            _logger.error("Process mining analysis failed: {}", e.getMessage(), e);
            return SkillResult.error("Analysis failed: " + e.getMessage());
        }
    }

    private SkillResult executeAnalysis(String specIdentifier, String specVersion,
                                         String specUri, String analysisType,
                                         boolean withData) throws Exception {
        YSpecificationID specId = new YSpecificationID(specIdentifier, specVersion, specUri);

        switch (analysisType.toLowerCase()) {
            case "xes":
                return analyzeXes(specId, withData);
            case "performance":
                return analyzePerformance(specId, withData);
            case "variants":
                return analyzeVariants(specId, withData);
            case "social_network":
                return analyzeSocialNetwork(specId, withData);
            case "full":
                return analyzeFull(specId, withData);
            default:
                return SkillResult.error(
                    "Unknown analysis type: " + analysisType +
                    ". Supported: xes, performance, variants, social_network, full");
        }
    }

    private SkillResult analyzeXes(YSpecificationID specId, boolean withData) throws Exception {
        ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, username, password);
        try {
            String xesXml = facade.analyze(specId, null, withData).xesXml;
            return SkillResult.success(xesXml);
        } finally {
            facade.close();
        }
    }

    private SkillResult analyzePerformance(YSpecificationID specId, boolean withData)
            throws Exception {
        ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, username, password);
        try {
            ProcessMiningFacade.ProcessMiningReport report =
                facade.analyzePerformance(specId, withData);

            String result = formatPerformanceReport(report);
            return SkillResult.success(result);
        } finally {
            facade.close();
        }
    }

    private SkillResult analyzeVariants(YSpecificationID specId, boolean withData)
            throws Exception {
        ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, username, password);
        try {
            ProcessMiningFacade.ProcessMiningReport report =
                facade.analyzePerformance(specId, withData);

            String result = formatVariantsReport(report);
            return SkillResult.success(result);
        } finally {
            facade.close();
        }
    }

    private SkillResult analyzeSocialNetwork(YSpecificationID specId, boolean withData)
            throws Exception {
        ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, username, password);
        try {
            ProcessMiningFacade.ProcessMiningReport report =
                facade.analyzePerformance(specId, withData);
            String xesXml = report.xesXml;

            SocialNetworkAnalyzer socialAnalyzer = new SocialNetworkAnalyzer();
            SocialNetworkAnalyzer.SocialNetworkResult socialResult =
                socialAnalyzer.analyze(xesXml);

            String result = formatSocialNetworkReport(socialResult);
            return SkillResult.success(result);
        } finally {
            facade.close();
        }
    }

    private SkillResult analyzeFull(YSpecificationID specId, boolean withData)
            throws Exception {
        ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, username, password);
        try {
            ProcessMiningFacade.ProcessMiningReport report =
                facade.analyzePerformance(specId, withData);

            String result = formatFullReport(report);
            return SkillResult.success(result);
        } finally {
            facade.close();
        }
    }

    private String formatPerformanceReport(ProcessMiningFacade.ProcessMiningReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Performance Analysis ===\n\n");
        sb.append("Specification: ").append(report.specificationId).append("\n");
        sb.append("Analysis Time: ").append(report.analysisTime).append("\n\n");

        if (report.performance != null) {
            sb.append("Performance Metrics:\n");
            sb.append("  Traces (Cases): ").append(report.performance.traceCount).append("\n");
            sb.append("  Activities: ").append(report.performance.eventCount).append("\n");
            sb.append("  Average Flow Time: ").append(report.performance.avgFlowTimeMs)
              .append(" ms\n");
            sb.append("  Min Flow Time: ").append(report.performance.minFlowTimeMs)
              .append(" ms\n");
            sb.append("  Max Flow Time: ").append(report.performance.maxFlowTimeMs)
              .append(" ms\n");
            sb.append("  Throughput: ").append(String.format("%.2f", report.performance.throughputPerHour))
              .append(" cases/hour\n");
        }

        return sb.toString();
    }

    private String formatVariantsReport(ProcessMiningFacade.ProcessMiningReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Variant Analysis ===\n\n");
        sb.append("Specification: ").append(report.specificationId).append("\n");
        sb.append("Total Traces: ").append(report.traceCount).append("\n");
        sb.append("Total Variants: ").append(report.variantCount).append("\n\n");

        sb.append("Top Variants (sorted by frequency):\n");
        int rank = 1;
        for (Map.Entry<String, Long> entry : report.variantFrequencies.entrySet()) {
            if (rank > 10) break;
            long count = entry.getValue();
            double percentage = (100.0 * count) / report.traceCount;
            sb.append(String.format("%2d. %s (%d cases, %.1f%%)\n",
                                    rank, entry.getKey(), count, percentage));
            rank++;
        }

        return sb.toString();
    }

    private String formatSocialNetworkReport(SocialNetworkAnalyzer.SocialNetworkResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Social Network Analysis ===\n\n");

        sb.append("Resources in Network: ").append(result.resources.size()).append("\n");
        if (result.mostCentralResource != null) {
            sb.append("Most Central Resource: ").append(result.mostCentralResource).append("\n");
        }
        sb.append("\n");

        sb.append("Resource Workload:\n");
        result.workloadByResource.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(10)
            .forEach(e -> sb.append("  ").append(e.getKey())
                           .append(": ").append(e.getValue())
                           .append(" events\n"));

        sb.append("\nTop Handover Pairs:\n");
        List<String[]> topHandovers = new SocialNetworkAnalyzer()
            .getTopHandovers(generateXesForWorkload(result), 5);
        int rank = 1;
        for (String[] pair : topHandovers) {
            sb.append(String.format("  %d. %s → %s (%s)\n",
                                    rank, pair[0], pair[1], pair[2]));
            rank++;
        }

        return sb.toString();
    }

    private String formatFullReport(ProcessMiningFacade.ProcessMiningReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Complete Process Mining Report ===\n\n");
        sb.append("Specification: ").append(report.specificationId).append("\n");
        sb.append("Analysis Time: ").append(report.analysisTime).append("\n\n");

        // Performance section
        sb.append("--- Performance Metrics ---\n");
        if (report.performance != null) {
            sb.append("Traces: ").append(report.performance.traceCount).append("\n");
            sb.append("Avg Flow Time: ").append(report.performance.avgFlowTimeMs)
              .append(" ms\n");
            sb.append("Throughput: ").append(String.format("%.2f", report.performance.throughputPerHour))
              .append(" cases/hour\n");
        }
        sb.append("\n");

        // Variants section
        sb.append("--- Process Variants ---\n");
        sb.append("Total Variants: ").append(report.variantCount).append("\n");
        int variantRank = 1;
        for (Map.Entry<String, Long> entry : report.variantFrequencies.entrySet()) {
            if (variantRank > 5) break;
            double percentage = (100.0 * entry.getValue()) / report.traceCount;
            sb.append(String.format("  %d. %s (%.1f%%)\n", variantRank, entry.getKey(), percentage));
            variantRank++;
        }
        sb.append("\n");

        // Conformance section
        if (report.conformance != null) {
            sb.append("--- Conformance Check ---\n");
            sb.append("Fitness: ").append(String.format("%.4f", report.conformance.computeFitness()))
              .append("\n");
        }

        sb.append("\n--- Social Network Summary ---\n");
        try {
            SocialNetworkAnalyzer socialAnalyzer = new SocialNetworkAnalyzer();
            SocialNetworkAnalyzer.SocialNetworkResult social =
                socialAnalyzer.analyze(report.xesXml);
            sb.append("Resources: ").append(social.resources.size()).append("\n");
            if (social.mostCentralResource != null) {
                sb.append("Most Central: ").append(social.mostCentralResource).append("\n");
            }
        } catch (Exception e) {
            _logger.warn("Failed to compute social network for full report: {}", e.getMessage());
        }

        return sb.toString();
    }

    private String generateXesForWorkload(SocialNetworkAnalyzer.SocialNetworkResult result) {
        ProcessMiningFacade pmFacade = null;
        try {
            pmFacade = new ProcessMiningFacade(engineUrl, username, password);
            // Extract spec identifier from result to build YSpecificationID
            // The result contains analysis data that may include the spec identifier
            if (result != null && result.resources != null && !result.resources.isEmpty()) {
                // Extract first available spec identifier from resources metadata
                // Resources are keyed by resource ID but may contain embedded spec info
                String specId = extractSpecIdentifierFromResult(result);
                if (specId != null && !specId.isEmpty()) {
                    YSpecificationID ySpecId = new YSpecificationID(specId, "0.1", specId);
                    var report = pmFacade.analyze(ySpecId, null, false);
                    return report.xesXml;
                }
            }
            throw new UnsupportedOperationException(
                "Cannot generate XES: no specification identifier available in analysis result");
        } catch (IOException e) {
            throw new UnsupportedOperationException(
                "Cannot generate XES for workload analysis: " + e.getMessage(), e);
        } finally {
            if (pmFacade != null) {
                try {
                    pmFacade.close();
                } catch (IOException e) {
                    _logger.warn("Failed to close ProcessMiningFacade: {}", e.getMessage());
                }
            }
        }
    }

    private String extractSpecIdentifierFromResult(SocialNetworkAnalyzer.SocialNetworkResult result) {
        if (result != null && result.resources != null && !result.resources.isEmpty()) {
            // Try to extract spec identifier from first resource
            // Resources are typically named with spec context
            String firstResource = result.resources.iterator().next();
            if (firstResource != null && firstResource.contains(":")) {
                return firstResource.split(":")[0];
            }
            return firstResource;
        }
        return null;
    }
}
