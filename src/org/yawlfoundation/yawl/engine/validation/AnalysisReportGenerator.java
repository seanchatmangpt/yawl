/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Analysis Report Generator
 *
 * Consolidates and visualizes optimization results from all validation components,
 * providing comprehensive insights for virtual thread configuration in the YAWL actor model.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Consolidates results from all validation components</li>
 *   <li>Generates comprehensive HTML reports</li>
 *   <li>Creates CSV exports for analysis</li>
 *   <li>Provides configuration recommendations</li>
 *   <li>Identifies performance bottlenecks</li>
 *   <li>Generates optimization suggestions</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class AnalysisReportGenerator {

    private static final Logger _logger = LogManager.getLogger(AnalysisReportGenerator.class);

    // Report configuration
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String REPORT_TITLE = "YAWL Actor Model Virtual Thread Optimization Report";
    private static final String OUTPUT_DIR = "validation-reports";

    // Analysis thresholds
    private static final double HIGH_UTILIZATION_THRESHOLD = 80.0;
    private static final double HIGH_LATENCY_THRESHOLD = 100.0; // ms
    private static final double LOW_SUCCESS_RATE_THRESHOLD = 95.0; // %

    // Report data
    private Instant analysisTime;
    private Map<String, Object> rawData = new HashMap<>();
    private List<Recommendation> recommendations = new ArrayList<>();
    private List<PerformanceBottleneck> bottlenecks = new ArrayList<>();

    /**
     * Generate comprehensive analysis report.
     */
    public AnalysisReport generateReport(
        CarrierThreadOptimizer.OptimizationReport carrierReport,
        VirtualThreadProfiler.ProfileReport profileReport,
        StructuredTaskScopeIntegrationTester.IntegrationTestResults integrationResults,
        StackDepthAnalyzer.StackDepthAnalysisReport stackReport,
        VirtualThreadLifecycleManager.LifecycleManagementReport lifecycleReport,
        PerformanceBenchmarkSuite.BenchmarkReport benchmarkReport
    ) {
        _logger.info("Generating comprehensive analysis report");

        this.analysisTime = Instant.now();
        consolidateRawData(carrierReport, profileReport, integrationResults, stackReport, lifecycleReport, benchmarkReport);
        analyzeResults();
        generateRecommendations();
        identifyBottlenecks();

        AnalysisReport report = new AnalysisReport(
            analysisTime,
            rawData,
            recommendations,
            bottlenecks,
            generateSummary()
        );

        // Generate output files
        generateOutputFiles(report);

        _logger.info("Analysis report generated successfully");

        return report;
    }

    /**
     * Consolidate raw data from all analysis components.
     */
    private void consolidateRawData(
        CarrierThreadOptimizer.OptimizationReport carrierReport,
        VirtualThreadProfiler.ProfileReport profileReport,
        StructuredTaskScopeIntegrationTester.IntegrationTestResults integrationResults,
        StackDepthAnalyzer.StackDepthAnalysisReport stackReport,
        VirtualThreadLifecycleManager.LifecycleManagementReport lifecycleReport,
        PerformanceBenchmarkSuite.BenchmarkReport benchmarkReport
    ) {
        // Carrier thread optimization data
        rawData.put("carrierThreadOptimization", carrierReport);

        // Virtual thread profiling data
        rawData.put("virtualThreadProfiling", profileReport);

        // StructuredTaskScope integration data
        rawData.put("structuredTaskScopeIntegration", integrationResults);

        // Stack depth analysis data
        rawData.put("stackDepthAnalysis", stackReport);

        // Lifecycle management data
        rawData.put("lifecycleManagement", lifecycleReport);

        // Performance benchmark data
        rawData.put("performanceBenchmark", benchmarkReport);

        // System information
        rawData.put("systemInfo", collectSystemInfo());
    }

    /**
     * Analyze consolidated results to identify patterns and insights.
     */
    private void analyzeResults() {
        _logger.debug("Analyzing consolidated results");

        // Analyze carrier thread performance
        analyzeCarrierThreadPerformance();

        // Analyze virtual thread behavior
        analyzeVirtualThreadBehavior();

        // Analyze integration results
        analyzeIntegrationResults();

        // Analyze stack depth patterns
        analyzeStackDepthPatterns();

        // Analyze lifecycle metrics
        analyzeLifecycleMetrics();

        // Analyze benchmark results
        analyzeBenchmarkResults();
    }

    /**
     * Analyze carrier thread performance data.
     */
    private void analyzeCarrierThreadPerformance() {
        CarrierThreadOptimizer.OptimizationReport carrierReport =
            (CarrierThreadOptimizer.OptimizationReport) rawData.get("carrierThreadOptimization");

        if (carrierReport != null && !carrierReport.results().isEmpty()) {
            // Find optimal configuration
            int optimalCarriers = carrierReport.optimalConfig();
            CarrierThreadOptimizer.TestResult optimalResult = carrierReport.results().get(optimalCarriers);

            // Check for performance issues
            if (optimalResult.carrierUtilization() > HIGH_UTILIZATION_THRESHOLD) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Carrier Thread Utilization",
                    String.format("High utilization (%.1f%%) with %d carriers", optimalResult.carrierUtilization(), optimalCarriers),
                    "HIGH",
                    "Consider increasing carrier threads or optimizing workload distribution"
                ));
            }

            if (optimalResult.p99LatencyMs() > HIGH_LATENCY_THRESHOLD) {
                bottlenecks.add(new PerformanceBottleneck(
                    "p99 Latency",
                    String.format("High p99 latency (%.2f ms) with %d carriers", optimalResult.p99LatencyMs(), optimalCarriers),
                    "MEDIUM",
                    "Investigate latency spikes and consider load balancing"
                ));
            }

            rawData.put("carrierAnalysis", Map.of(
                "optimalCarriers", optimalCarriers,
                "optimalUtilization", optimalResult.carrierUtilization(),
                "optimalThroughput", optimalResult.throughput(),
                "optimalP99Latency", optimalResult.p99LatencyMs()
            ));
        }
    }

    /**
     * Analyze virtual thread behavior patterns.
     */
    private void analyzeVirtualThreadBehavior() {
        VirtualThreadProfiler.ProfileReport profileReport =
            (VirtualThreadProfiler.ProfileReport) rawData.get("virtualThreadProfiling");

        if (profileReport != null) {
            // Analyze park/unmount efficiency
            double parkDurationMs = profileReport.avgParkDurationMs();
            double peakVThreads = profileReport.peakVirtualThreads();
            double carrierUtilization = profileReport.carrierUtilization();

            rawData.put("behaviorAnalysis", Map.of(
                "avgParkDurationMs", parkDurationMs,
                "peakVirtualThreads", peakVThreads,
                "carrierUtilization", carrierUtilization
            ));

            // Check for inefficient parking
            if (parkDurationMs > 10.0) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Virtual Thread Parking",
                    String.format("Long average park duration (%.2f ms)", parkDurationMs),
                    "MEDIUM",
                    "Reduce blocking operations and improve I/O efficiency"
                ));
            }
        }
    }

    /**
     * Analyze structured concurrency integration results.
     */
    private void analyzeIntegrationResults() {
        StructuredTaskScopeIntegrationTester.IntegrationTestResults integrationResults =
            (StructuredTaskScopeIntegrationTester.IntegrationTestResults) rawData.get("structuredTaskScopeIntegration");

        if (integrationResults != null && integrationResults.summary() != null) {
            var summary = integrationResults.summary();

            rawData.put("integrationAnalysis", Map.of(
                "totalTasks", summary.totalTasks(),
                "successRate", summary.successRate(),
                "avgLatencyMs", summary.avgLatencyMs(),
                "throughput", summary.throughput()
            ));

            // Check for integration issues
            if (summary.successRate() < LOW_SUCCESS_RATE_THRESHOLD) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Structured Concurrency",
                    String.format("Low success rate (%.1f%%)", summary.successRate()),
                    "HIGH",
                    "Review task implementations and error handling"
                ));
            }
        }
    }

    /**
     * Analyze stack depth patterns.
     */
    private void analyzeStackDepthPatterns() {
        StackDepthAnalyzer.StackDepthAnalysisReport stackReport =
            (StackDepthAnalyzer.StackDepthAnalysisReport) rawData.get("stackDepthAnalysis");

        if (stackReport != null) {
            var patterns = stackReport.patterns();

            rawData.put("stackAnalysis", Map.of(
                "avgStackDepth", stackReport.averageStackDepth(),
                "maxStackDepth", stackReport.maximumStackDepth(),
                "shallowStackPercentage", patterns.shallowStackPercentage(),
                "deepStackPercentage", patterns.deepStackPercentage(),
                "veryDeepStackPercentage", patterns.veryDeepStackPercentage()
            ));

            // Check for stack depth issues
            if (patterns.veryDeepStackPercentage() > 20) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Stack Depth",
                    String.format("High percentage of very deep stacks (%.1f%%)", patterns.veryDeepStackPercentage()),
                    "MEDIUM",
                    "Reduce recursion depth and consider iterative approaches"
                ));
            }

            if (stackReport.stackOverflowCount() > 0) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Stack Overflows",
                    String.format("%d stack overflows detected", stackReport.stackOverflowCount()),
                    "HIGH",
                    "Increase stack size or optimize recursive algorithms"
                ));
            }
        }
    }

    /**
     * Analyze lifecycle management metrics.
     */
    private void analyzeLifecycleMetrics() {
        VirtualThreadLifecycleManager.LifecycleManagementReport lifecycleReport =
            (VirtualThreadLifecycleManager.LifecycleManagementReport) rawData.get("lifecycleManagement");

        if (lifecycleReport != null) {
            rawData.put("lifecycleAnalysis", Map.of(
                "activeThreads", lifecycleReport.activeThreads(),
                "totalCreated", lifecycleReport.totalCreated(),
                "totalTerminated", lifecycleReport.totalTerminated(),
                "totalLeaksDetected", lifecycleReport.totalLeaksDetected(),
                "resourceLeaks", lifecycleReport.resourceLeaks()
            ));

            // Check for lifecycle issues
            if (lifecycleReport.totalLeaksDetected() > 0) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Lifecycle Leaks",
                    String.format("%d leaks detected", lifecycleReport.totalLeaksDetected()),
                    "HIGH",
                    "Review thread lifecycle management and resource cleanup"
                ));
            }
        }
    }

    /**
     * Analyze benchmark results.
     */
    private void analyzeBenchmarkResults() {
        PerformanceBenchmarkSuite.BenchmarkReport benchmarkReport =
            (PerformanceBenchmarkSuite.BenchmarkReport) rawData.get("performanceBenchmark");

        if (benchmarkReport != null) {
            // Calculate overall performance metrics
            double avgP99Latency = benchmarkReport.results().values().stream()
                .flatMap(map -> map.values().stream())
                .mapToDouble(result -> result.p99LatencyMs())
                .average()
                .orElse(0);

            double avgThroughput = benchmarkReport.results().values().stream()
                .flatMap(map -> map.values().stream())
                .mapToDouble(PerformanceBenchmarkSuite.BenchmarkResult::throughput)
                .average()
                .orElse(0);

            rawData.put("benchmarkAnalysis", Map.of(
                "avgP99Latency", avgP99Latency,
                "avgThroughput", avgThroughput,
                "totalBenchmarks", benchmarkReport.results().values().stream()
                    .mapToInt(map -> map.size())
                    .sum()
            ));

            // Check for benchmark issues
            if (avgP99Latency > HIGH_LATENCY_THRESHOLD) {
                bottlenecks.add(new PerformanceBottleneck(
                    "Benchmark Performance",
                    String.format("High average p99 latency (%.2f ms)", avgP99Latency),
                    "MEDIUM",
                    "Optimize workload distribution and reduce contention"
                ));
            }
        }
    }

    /**
     * Generate configuration recommendations.
     */
    private void generateRecommendations() {
        recommendations.clear();

        // Carrier thread recommendations
        addCarrierThreadRecommendations();

        // Virtual thread configuration recommendations
        addVirtualThreadRecommendations();

        // Performance optimization recommendations
        addPerformanceRecommendations();

        // Best practice recommendations
        addBestPracticeRecommendations();

        // Sort recommendations by priority
        recommendations.sort(Comparator.comparingInt(Recommendation::priority).reversed());
    }

    /**
     * Add carrier thread recommendations.
     */
    private void addCarrierThreadRecommendations() {
        CarrierThreadOptimizer.OptimizationReport carrierReport =
            (CarrierThreadOptimizer.OptimizationReport) rawData.get("carrierThreadOptimization");

        if (carrierReport != null) {
            int optimalCarriers = carrierReport.optimalConfig();

            recommendations.add(new Recommendation(
                "Configure optimal carrier threads",
                String.format("Set carrier thread pool size to %d for optimal performance", optimalCarriers),
                "HIGH",
                "carrier-thread-config"
            ));

            if (optimalCarriers < 4) {
                recommendations.add(new Recommendation(
                    "Consider more carrier threads",
                    "Low carrier count may limit scalability under high load",
                    "MEDIUM",
                    "carrier-thread-scaling"
                ));
            }
        }
    }

    /**
     * Add virtual thread configuration recommendations.
     */
    private void addVirtualThreadRecommendations() {
        VirtualThreadProfiler.ProfileReport profileReport =
            (VirtualThreadProfiler.ProfileReport) rawData.get("virtualThreadProfiling");

        if (profileReport != null) {
            double peakVThreads = profileReport.peakVirtualThreads();
            double carrierUtilization = profileReport.carrierUtilization();

            if (carrierUtilization > HIGH_UTILIZATION_THRESHOLD) {
                recommendations.add(new Recommendation(
                    "Optimize virtual thread scheduling",
                    "High carrier utilization suggests virtual thread scheduling issues",
                    "HIGH",
                    "virtual-thread-scheduling"
                ));
            }

            if (peakVThreads > 10000) {
                recommendations.add(new Recommendation(
                    "Monitor virtual thread scaling",
                    "High virtual thread count may impact memory usage",
                    "MEDIUM",
                    "virtual-thread-scaling"
                ));
            }
        }
    }

    /**
     * Add performance optimization recommendations.
     */
    private void addPerformanceRecommendations() {
        recommendations.add(new Recommendation(
            "Implement non-blocking I/O",
            "Replace blocking I/O operations with non-blocking alternatives",
            "HIGH",
            "io-optimization"
        ));

        recommendations.add(new Recommendation(
            "Use StructuredTaskScope for coordinated operations",
            "Leverage Java 25 structured concurrency for better error handling",
            "MEDIUM",
            "structured-concurrency"
        ));

        recommendations.add(new Recommendation(
            "Monitor memory usage patterns",
            "Track memory usage to detect potential leaks",
            "MEDIUM",
            "memory-monitoring"
        ));
    }

    /**
     * Add best practice recommendations.
         */
    private void addBestPracticeRecommendations() {
        recommendations.add(new Recommendation(
            "Follow actor model best practices",
            "Design actors with single responsibility and clear message boundaries",
            "MEDIUM",
            "actor-model-practices"
        ));

        recommendations.add(new Recommendation(
            "Implement proper error handling",
            "Use try-catch blocks and ensure graceful error recovery",
            "HIGH",
            "error-handling"
        ));

        recommendations.add(new Recommendation(
            "Use ScopedValue for context propagation",
            "Replace ThreadLocal with ScopedValue for better virtual thread support",
            "MEDIUM",
            "context-propagation"
        ));
    }

    /**
     * Identify performance bottlenecks.
     */
    private void identifyBottlenecks() {
        bottlenecks.sort(Comparator.comparing(PerformanceBottleneck::severity).reversed());
    }

    /**
     * Generate summary of findings.
     */
    private ReportSummary generateSummary() {
        return new ReportSummary(
            analysisTime,
            recommendations.size(),
            bottlenecks.size(),
            recommendations.stream()
                .filter(r -> r.priority() == RecommendationPriority.HIGH)
                .count(),
            bottlenecks.stream()
                .filter(b -> b.severity() == Severity.HIGH)
                .count()
        );
    }

    /**
     * Generate output files for the report.
     */
    private void generateOutputFiles(AnalysisReport report) {
        try {
            // Create output directory
            Path outputDir = Path.of(OUTPUT_DIR);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }

            // Generate HTML report
            String htmlContent = generateHtmlReport(report);
            Path htmlFile = outputDir.resolve("analysis-report-" +
                analysisTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html");
            Files.writeString(htmlFile, htmlContent);

            // Generate CSV export
            String csvContent = generateCsvExport(report);
            Path csvFile = outputDir.resolve("analysis-data-" +
                analysisTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv");
            Files.writeString(csvFile, csvContent);

            // Save JSON summary
            String jsonContent = generateJsonSummary(report);
            Path jsonFile = outputDir.resolve("analysis-summary-" +
                analysisTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
            Files.writeString(jsonFile, jsonContent);

            _logger.info("Report files generated: HTML={}, CSV={}, JSON={}",
                htmlFile, csvFile, jsonFile);

        } catch (IOException e) {
            _logger.error("Failed to generate report files: {}", e.getMessage());
        }
    }

    /**
     * Generate HTML report content.
     */
    private String generateHtmlReport(AnalysisReport report) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(REPORT_TITLE).append("</title>\n");
        html.append("    <style>\n");
        html.append("        body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("        .header { background-color: #f0f0f0; padding: 20px; margin-bottom: 20px; }\n");
        html.append("        .section { margin-bottom: 30px; }\n");
        html.append("        .recommendation { background-color: #e8f5e8; padding: 15px; margin: 10px 0; border-left: 4px solid #4CAF50; }\n");
        html.append("        .bottleneck { background-color: #ffebee; padding: 15px; margin: 10px 0; border-left: 4px solid #f44336; }\n");
        html.append("        .metric { display: inline-block; margin: 5px; padding: 10px; background-color: #f5f5f5; border-radius: 5px; }\n");
        html.append("        .priority-high { color: #d32f2f; font-weight: bold; }\n");
        html.append("        .priority-medium { color: #f57c00; font-weight: bold; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>").append(REPORT_TITLE).append("</h1>\n");
        html.append("        <p>Generated: ").append(TIMESTAMP_FORMATTER.format(analysisTime)).append("</p>\n");
        html.append("    </div>\n");

        // Summary section
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Summary</h2>\n");
        html.append("        <div class=\"metric\">Recommendations: ").append(report.recommendations().size()).append("</div>\n");
        html.append("        <div class=\"metric\">Bottlenecks: ").append(report.bottlenecks().size()).append("</div>\n");
        html.append("        <div class=\"metric\">High Priority Issues: ").append(report.summary().highPriorityIssues()).append("</div>\n");
        html.append("    </div>\n");

        // Recommendations section
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Recommendations</h2>\n");
        for (Recommendation rec : report.recommendations()) {
            html.append("        <div class=\"recommendation\">\n");
            html.append("            <h3>").append(rec.title()).append("</h3>\n");
            html.append("            <p><strong>Priority:</strong> ").append(rec.priority()).append("</p>\n");
            html.append("            <p>").append(rec.description()).append("</p>\n");
            html.append("            <p><strong>Category:</strong> ").append(rec.category()).append("</p>\n");
            html.append("        </div>\n");
        }
        html.append("    </div>\n");

        // Bottlenecks section
        html.append("    <div class=\"section\">\n");
        html.append("        <h2>Performance Bottlenecks</h2>\n");
        for (PerformanceBottleneck bottleneck : report.bottlenecks()) {
            html.append("        <div class=\"bottleneck\">\n");
            html.append("            <h3>").append(bottleneck.title()).append("</h3>\n");
            html.append("            <p><strong>Severity:</strong> ").append(bottleneck.severity()).append("</p>\n");
            html.append("            <p>").append(bottleneck.description()).append("</p>\n");
            html.append("            <p><strong>Suggestion:</strong> ").append(bottleneck.suggestion()).append("</p>\n");
            html.append("        </div>\n");
        }
        html.append("    </div>\n");

        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    /**
     * Generate CSV export content.
     */
    private String generateCsvExport(AnalysisReport report) {
        StringBuilder csv = new StringBuilder();

        csv.append("Type,Title,Description,Priority/Severity,Category\n");

        // Export recommendations
        for (Recommendation rec : report.recommendations()) {
            csv.append("Recommendation,\"").append(rec.title()).append("\",\"")
               .append(rec.description()).append("\",")
               .append(rec.priority()).append(",")
               .append(rec.category()).append("\n");
        }

        // Export bottlenecks
        for (PerformanceBottleneck bottleneck : report.bottlenecks()) {
            csv.append("Bottleneck,\"").append(bottleneck.title()).append("\",\"")
               .append(bottleneck.description()).append("\",")
               .append(bottleneck.severity()).append(",")
               .append("Performance").append("\n");
        }

        return csv.toString();
    }

    /**
     * Generate JSON summary content.
     */
    private String generateJsonSummary(AnalysisReport report) {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("    \"generatedAt\": \"").append(analysisTime).append("\",\n");
        json.append("    \"summary\": {\n");
        json.append("        \"totalRecommendations\": ").append(report.recommendations().size()).append(",\n");
        json.append("        \"totalBottlenecks\": ").append(report.bottlenecks().size()).append(",\n");
        json.append("        \"highPriorityIssues\": ").append(report.summary().highPriorityIssues()).append(",\n");
        json.append("        \"criticalIssues\": ").append(report.summary().criticalIssues()).append("\n");
        json.append("    },\n");
        json.append("    \"recommendations\": [\n");

        for (int i = 0; i < report.recommendations().size(); i++) {
            Recommendation rec = report.recommendations().get(i);
            json.append("        {\n");
            json.append("            \"title\": \"").append(rec.title()).append("\",\n");
            json.append("            \"description\": \"").append(rec.description()).append("\",\n");
            json.append("            \"priority\": \"").append(rec.priority()).append("\",\n");
            json.append("            \"category\": \"").append(rec.category()).append("\"\n");
            json.append("        }");
            if (i < report.recommendations().size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("    ],\n");
        json.append("    \"bottlenecks\": [\n");

        for (int i = 0; i < report.bottlenecks().size(); i++) {
            PerformanceBottleneck bottleneck = report.bottlenecks().get(i);
            json.append("        {\n");
            json.append("            \"title\": \"").append(bottleneck.title()).append("\",\n");
            json.append("            \"description\": \"").append(bottleneck.description()).append("\",\n");
            json.append("            \"severity\": \"").append(bottleneck.severity()).append("\",\n");
            json.append("            \"suggestion\": \"").append(bottleneck.suggestion()).append("\"\n");
            json.append("        }");
            if (i < report.bottlenecks().size() - 1) json.append(",");
            json.append("\n");
        }

        json.append("    ]\n");
        json.append("}\n");

        return json.toString();
    }

    /**
     * Collect system information for the report.
     */
    private Map<String, Object> collectSystemInfo() {
        Map<String, Object> systemInfo = new HashMap<>();

        // JVM information
        systemInfo.put("javaVersion", System.getProperty("java.version"));
        systemInfo.put("javaVendor", System.getProperty("java.vendor"));
        systemInfo.put("jvmName", System.getProperty("java.vm.name"));

        // System information
        systemInfo.put("osName", System.getProperty("os.name"));
        systemInfo.put("osVersion", System.getProperty("os.version"));
        systemInfo.put("osArch", System.getProperty("os.arch"));
        systemInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        // Memory information
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();

        systemInfo.put("maxMemory", maxMemory);
        systemInfo.put("totalMemory", totalMemory);
        systemInfo.put("freeMemory", freeMemory);
        systemInfo.put("usedMemory", totalMemory - freeMemory);

        return systemInfo;
    }

    // Record classes
    public record AnalysisReport(
        Instant generatedAt,
        Map<String, Object> rawData,
        List<Recommendation> recommendations,
        List<PerformanceBottleneck> bottlenecks,
        ReportSummary summary
    ) {}

    public record Recommendation(
        String title,
        String description,
        String priority,
        String category
    ) {}

    public record PerformanceBottleneck(
        String title,
        String description,
        String severity,
        String suggestion
    ) {}

    public record ReportSummary(
        Instant generatedAt,
        long totalRecommendations,
        long totalBottlenecks,
        long highPriorityIssues,
        long criticalIssues
    ) {}

    public enum RecommendationPriority {
        HIGH, MEDIUM, LOW
    }

    public enum Severity {
        HIGH, MEDIUM, LOW
    }
}