/*
 * YAWL MCp A2A Cascade Failure Analyzer
 *
 * Comprehensive analysis tool for cascade failure testing.
 * Generates reports, identifies patterns, and provides recommendations.
 */

package org.yawlfoundation.yawl.stress;

import org.yawlfoundation.yawl.stress.metrics.FailureMetrics;
import org.yawlfoundation.yawl.stress.metrics.PropagationMetrics;
import org.yawlfoundation.yawl.stress.IsolationValidator;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes cascade test results and generates actionable insights.
 * Identifies patterns, risks, and recommends improvements.
 */
public class CascadeFailureAnalyzer {

    private final List<FailureMetrics> allTestResults;
    private final Map<String, PropagationMetrics> propagationData;
    private final NetworkTopologyAnalyzer topologyAnalyzer;

    public CascadeFailureAnalyzer() {
        this.allTestResults = new ArrayList<>();
        this.propagationData = new HashMap<>();
        this.topologyAnalyzer = new NetworkTopologyAnalyzer();
    }

    /**
     * Adds test results for analysis
     */
    public void addTestResult(FailureMetrics metrics) {
        allTestResults.add(metrics);
    }

    /**
     * Adds propagation data for detailed analysis
     */
    public void addPropagationData(String testId, PropagationMetrics metrics) {
        propagationData.put(testId, metrics);
    }

    /**
     * Generates comprehensive analysis report
     */
    public AnalysisReport generateReport() {
        AnalysisReport report = new AnalysisReport();

        // Basic statistics
        report.totalTests = allTestResults.size();
        report.analyzedNetworks = countUniqueNetworks();
        report.testDuration = calculateTotalTestDuration();

        // Risk assessment
        report.riskAssessment = assessOverallRisk();
        report.failurePatterns = identifyFailurePatterns();
        report.topologyVulnerabilities = analyzeTopologyVulnerabilities();

        // Recommendations
        report.recommendations = generateRecommendations();
        report.optimizationStrategies = suggestOptimizationStrategies();

        return report;
    }

    /**
     * Identifies critical failure patterns
     */
    public List<FailurePattern> identifyCriticalPatterns() {
        return allTestResults.stream()
            .filter(this::isCriticalFailure)
            .map(this::analyzeFailurePattern)
            .collect(Collectors.toList());
    }

    /**
     * Tests network resilience under various failure scenarios
     */
    public ResilienceTestResult evaluateNetworkResilience(
        Map<String, CascadeFailureTest.TestAgent> network,
        int simulationCount
    ) {
        ResilienceTestResult result = new ResilienceTestResult();
        result.networkSize = network.size();
        result.simulationCount = simulationCount;

        List<Integer> affectedCounts = new ArrayList<>();
        List<Long> propagationTimes = new ArrayList<>();
        List<Double> spreadRatios = new ArrayList<>();

        for (int i = 0; i < simulationCount; i++) {
            // Random failure injection
            CascadeFailureTest.TestAgent randomSource = selectRandomActor(network);

            try {
                CascadeFailureTest.CascadeTestResult testResult = runSingleSimulation(
                    randomSource, network
                );

                affectedCounts.add(testResult.affectedActors.size());
                propagationTimes.add(testResult.propagationTime);
                spreadRatios.add((double) testResult.affectedActors.size() / network.size());
            } catch (Exception e) {
                result.simulationFailures++;
            }
        }

        // Calculate statistics
        result.averageAffected = average(affectedCounts);
        result.maxAffected = max(affectedCounts);
        result.minAffected = min(affectedCounts);

        result.averagePropagationTime = average(propagationTimes);
        result.maxPropagationTime = max(propagationTimes);

        result.averageSpreadRatio = average(spreadRatios);
        result.maxSpreadRatio = max(spreadRatios);

        // Resilience score (0-1, higher is better)
        result.resilienceScore = calculateResilienceScore(spreadRatios);

        return result;
    }

    /**
     * Compares different network topologies
     */
    public TopologyComparison compareTopologies() {
        TopologyComparison comparison = new TopologyComparison();

        // Group results by network type
        Map<String, List<FailureMetrics>> groupedResults = allTestResults.stream()
            .collect(Collectors.groupingBy(this::extractNetworkType));

        for (Map.Entry<String, List<FailureMetrics>> entry : groupedResults.entrySet()) {
            String topologyType = entry.getKey();
            List<FailureMetrics> topologyResults = entry.getValue();

            TopologyMetrics metrics = new TopologyMetrics();
            metrics.type = topologyType;
            metrics.testCount = topologyResults.size();
            metrics.averageSpread = topologyResults.stream()
                .mapToDouble(m -> m.failureSpreadRatio)
                .average()
                .orElse(0);
            metrics.averagePropagationTime = topologyResults.stream()
                .mapToLong(m -> m.propagationTimeMs)
                .average()
                .orElse(0);
            metrics.isolationFailures = topologyResults.stream()
                .mapToInt(m -> m.isolationFailure ? 1 : 0)
                .sum();

            comparison.topologyMetrics.add(metrics);
        }

        comparison.rankTopologies();
        return comparison;
    }

    /**
     * Generates actionable recommendations
     */
    public List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();

        // Analyze overall risk level
        if (getAverageSpreadRatio() > 0.7) {
            recommendations.add("🚨 Critical: High failure spread detected - implement immediate isolation barriers");
        } else if (getAverageSpreadRatio() > 0.4) {
            recommendations.add("⚠️ High: Significant failure spread - review network topology");
        }

        // Check propagation time
        if (getAveragePropagationTime() > 5000) {
            recommendations.add("🐢 Slow propagation detected - consider optimizing communication paths");
        }

        // Check isolation failures
        long isolationFailures = countIsolationFailures();
        if (isolationFailures > 0) {
            recommendations.add(String.format(
                "🔒 Isolation failures detected (%d) - review isolation strategy",
                isolationFailures
            ));
        }

        // Topology-specific recommendations
        recommendations.addAll(getTopologySpecificRecommendations());

        // Prevention strategies
        recommendations.addAll(getPreventionStrategies());

        return recommendations;
    }

    // Helper methods
    private int countUniqueNetworks() {
        return allTestResults.stream()
            .map(m -> m.testId.split("_")[0]) // Extract network type from test ID
            .distinct()
            .collect(Collectors.toList())
            .size();
    }

    private Duration calculateTotalTestDuration() {
        if (allTestResults.isEmpty()) {
            return Duration.ZERO;
        }

        Instant firstTest = allTestResults.stream()
            .map(m -> m.timestamp)
            .min(Comparator.naturalOrder())
            .orElse(Instant.now());

        Instant lastTest = allTestResults.stream()
            .map(m -> m.timestamp)
            .max(Comparator.naturalOrder())
            .orElse(Instant.now());

        return Duration.between(firstTest, lastTest);
    }

    private RiskAssessment assessOverallRisk() {
        RiskAssessment assessment = new RiskAssessment();

        // Calculate key metrics
        assessment.averageSpreadRatio = getAverageSpreadRatio();
        assessment.averagePropagationTime = getAveragePropagationTime();
        assessment.isolationFailureRate = (double) countIsolationFailures() / allTestResults.size();

        // Determine risk level
        if (assessment.averageSpreadRatio > 0.8) {
            assessment.overallRiskLevel = RiskLevel.CRITICAL;
        } else if (assessment.averageSpreadRatio > 0.5) {
            assessment.overallRiskLevel = RiskLevel.HIGH;
        } else if (assessment.averageSpreadRatio > 0.3) {
            assessment.overallRiskLevel = RiskLevel.MEDIUM;
        } else {
            assessment.overallRiskLevel = RiskLevel.LOW;
        }

        return assessment;
    }

    private List<FailurePattern> identifyFailurePatterns() {
        Map<String, Integer> patternCounts = new HashMap<>();

        for (FailureMetrics metrics : allTestResults) {
            // Identify pattern based on spread ratio and propagation time
            String pattern;
            if (metrics.failureSpreadRatio > 0.8) {
                pattern = "catastrophic";
            } else if (metrics.failureSpreadRatio > 0.5) {
                pattern = "major";
            } else if (metrics.failureSpreadRatio > 0.3) {
                pattern = "moderate";
            } else if (metrics.failureSpreadRatio > 0.1) {
                pattern = "minor";
            } else {
                pattern = "contained";
            }

            patternCounts.merge(pattern, 1, Integer::sum);
        }

        return patternCounts.entrySet().stream()
            .map(entry -> new FailurePattern(entry.getKey(), entry.getValue()))
            .sorted((p1, p2) -> p2.count - p1.count)
            .collect(Collectors.toList());
    }

    private List<NetworkVulnerability> analyzeTopologyVulnerabilities() {
        List<NetworkVulnerability> vulnerabilities = new ArrayList<>();

        // Identify actors that appear as sources in multiple failures
        Map<String, Integer> actorFailureCount = new HashMap<>();

        for (FailureMetrics metrics : allTestResults) {
            String sourceActor = extractSourceActor(metrics.testId);
            actorFailureCount.merge(sourceActor, 1, Integer::sum);
        }

        // Find vulnerable actors
        actorFailureCount.entrySet().stream()
            .filter(entry -> entry.getValue() > 2) // Failed as source > 2 times
            .forEach(entry -> {
                NetworkVulnerability vuln = new NetworkVulnerability();
                vuln.actorId = entry.getKey();
                vuln.failureCount = entry.getValue();
                vuln.severity = entry.getValue() > 5 ? Severity.HIGH : Severity.MEDIUM;
                vuln.recommendation = String.format(
                    "Consider isolating or hardening actor '%s'",
                    entry.getKey()
                );
                vulnerabilities.add(vuln);
            });

        return vulnerabilities;
    }

    private List<String> getTopologySpecificRecommendations() {
        List<String> recommendations = new ArrayList<>();

        // Check for specific topologies that need attention
        long chainNetworks = allTestResults.stream()
            .filter(m -> m.testId.contains("chain"))
            .count();

        long starNetworks = allTestResults.stream()
            .filter(m -> m.testId.contains("star"))
            .count();

        if (chainNetworks > 0) {
            recommendations.add("🔗 Chain networks detected - add redundancy at critical nodes");
        }

        if (starNetworks > 0) {
            recommendations.add("⭐ Star networks detected - implement hub redundancy");
        }

        return recommendations;
    }

    private List<String> getPreventionStrategies() {
        return Arrays.asList(
            "Implement circuit breakers for critical services",
            "Add health checks and automatic failover mechanisms",
            "Establish clear isolation boundaries between services",
            "Monitor propagation patterns in real-time",
            "Regular failure injection testing",
            "Design for graceful degradation"
        );
    }

    // Analysis report structure
    public static class AnalysisReport {
        public int totalTests;
        public int analyzedNetworks;
        public Duration testDuration;
        public RiskAssessment riskAssessment;
        public List<FailurePattern> failurePatterns;
        public List<NetworkVulnerability> topologyVulnerabilities;
        public List<String> recommendations;
        public List<String> optimizationStrategies;

        public void printReport() {
            System.out.println("=== CASCADE FAILURE ANALYSIS REPORT ===");
            System.out.printf("Total Tests: %d%n", totalTests);
            System.out.printf("Analyzed Networks: %d%n", analyzedNetworks);
            System.out.printf("Test Duration: %d minutes%n", testDuration.toMinutes());
            System.out.println();

            System.out.println("RISK ASSESSMENT:");
            System.out.printf("  Level: %s%n", riskAssessment.overallRiskLevel);
            System.out.printf("  Average Spread: %.2f%n", riskAssessment.averageSpreadRatio);
            System.out.printf("  Average Propagation Time: %d ms%n", riskAssessment.averagePropagationTime);
            System.out.printf("  Isolation Failure Rate: %.2f%%%n", riskAssessment.isolationFailureRate * 100);
            System.out.println();

            System.out.println("FAILURE PATTERNS:");
            failurePatterns.forEach(pattern ->
                System.out.printf("  %s: %d occurrences%n", pattern.pattern, pattern.count)
            );
            System.out.println();

            System.out.println("TOPOLOGY VULNERABILITIES:");
            topologyVulnerabilities.forEach(vuln ->
                System.out.printf("  %s: %d failures - %s%n",
                    vuln.actorId, vuln.failureCount, vuln.recommendation)
            );
            System.out.println();

            System.out.println("RECOMMENDATIONS:");
            recommendations.forEach(System.out::println);
        }
    }

    public static class RiskAssessment {
        public RiskLevel overallRiskLevel;
        public double averageSpreadRatio;
        public long averagePropagationTime;
        public double isolationFailureRate;
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public static class FailurePattern {
        public String pattern;
        public int count;

        public FailurePattern(String pattern, int count) {
            this.pattern = pattern;
            this.count = count;
        }
    }

    public static class NetworkVulnerability {
        public String actorId;
        public int failureCount;
        public Severity severity;
        public String recommendation;
    }

    public enum Severity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public static class ResilienceTestResult {
        public int networkSize;
        public int simulationCount;
        public int simulationFailures;
        public double averageAffected;
        public int maxAffected;
        public int minAffected;
        public double averagePropagationTime;
        public long maxPropagationTime;
        public double averageSpreadRatio;
        public double maxSpreadRatio;
        public double resilienceScore;

        public String getSummary() {
            return String.format(
                "Network size: %d, Simulations: %d, Resilience: %.2f, Avg spread: %.2f",
                networkSize, simulationCount, resilienceScore, averageSpreadRatio
            );
        }
    }

    public static class TopologyComparison {
        public List<TopologyMetrics> topologyMetrics = new ArrayList<>();

        public void rankTopologies() {
            topologyMetrics.sort((t1, t2) ->
                Double.compare(t1.resilienceScore, t2.resilienceScore)
            );
        }
    }

    public static class TopologyMetrics {
        public String type;
        public int testCount;
        public double averageSpread;
        public long averagePropagationTime;
        public int isolationFailures;
        public double resilienceScore;

        public void calculateResilienceScore() {
            // Lower spread and fewer isolation failures = higher resilience
            this.resilienceScore = 1.0 - (averageSpread + (isolationFailures * 0.1));
        }
    }

    // Private helper methods
    private boolean isCriticalFailure(FailureMetrics metrics) {
        return metrics.failureSpreadRatio > 0.8 ||
               metrics.propagationTimeMs > 10000 ||
               (metrics.isolationFailure && metrics.failureSpreadRatio > 0.5);
    }

    private FailurePattern analyzeFailurePattern(FailureMetrics metrics) {
        String pattern = metrics.failureSpreadRatio > 0.8 ? "catastrophic" : "severe";
        return new FailurePattern(pattern, 1);
    }

    private double getAverageSpreadRatio() {
        return allTestResults.stream()
            .mapToDouble(m -> m.failureSpreadRatio)
            .average()
            .orElse(0);
    }

    private long getAveragePropagationTime() {
        return allTestResults.stream()
            .mapToLong(m -> m.propagationTimeMs)
            .average()
            .orElse(0);
    }

    private long countIsolationFailures() {
        return allTestResults.stream()
            .mapToInt(m -> m.isolationFailure ? 1 : 0)
            .sum();
    }

    private String extractNetworkType(String testId) {
        // Extract network type from test ID (e.g., "chain_1" -> "chain")
        String[] parts = testId.split("_");
        return parts.length > 1 ? parts[0] : "unknown";
    }

    private String extractSourceActor(String testId) {
        // Extract actor ID from test ID
        String[] parts = testId.split("_");
        return parts.length > 2 ? parts[1] : "unknown";
    }

    private double calculateResilienceScore(List<Double> spreadRatios) {
        // Calculate resilience as inverse of average spread
        double avgSpread = average(spreadRatios);
        return Math.max(0, 1.0 - avgSpread);
    }

    private double average(List<Double> values) {
        return values.stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
    }

    private int max(List<Integer> values) {
        return values.stream()
            .max(Integer::compare)
            .orElse(0);
    }

    private int min(List<Integer> values) {
        return values.stream()
            .min(Integer::compare)
            .orElse(0);
    }

    private long max(List<Long> values) {
        return values.stream()
            .max(Long::compare)
            .orElse(0);
    }

    private CascadeFailureTest.TestAgent selectRandomActor(
        Map<String, CascadeFailureTest.TestAgent> network
    ) {
        List<CascadeFailureTest.TestAgent> actors = new ArrayList<>(network.values());
        return actors.get(new Random().nextInt(actors.size()));
    }

    private CascadeFailureTest.CascadeTestResult runSingleSimulation(
        CascadeFailureTest.TestAgent source,
        Map<String, CascadeFailureTest.TestAgent> network
    ) throws Exception {
        // Simplified simulation - in real implementation, this would be more sophisticated
        source.injectFailure(CascadeFailureTest.FailureMode.EXCEPTION_THROW);

        CascadeFailureTest.CascadeTestResult result = new CascadeFailureTest.CascadeTestResult();
        result.affectedActors = network.values().stream()
            .filter(CascadeFailureTest.TestAgent::isFailed)
            .map(a -> a.agentId)
            .collect(Collectors.toList());

        return result;
    }

    private class NetworkTopologyAnalyzer {
        // Additional topology analysis methods
    }
}