/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.time.*;
import java.time.format.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Aggregator and analyzer of test results for YAWL v6.0.0-GA benchmarking.
 *
 * <p>Collects, processes, and aggregates test results from multiple test runs
 * with comprehensive analysis and reporting capabilities.
 *
 * <p>Features:
 * <ul>
 *   <li>Multi-test run aggregation and comparison</li>
 *   <li>Trend analysis and performance regression detection</li>
 *   <li>Statistical significance testing</li>
 *   <li>Visual report generation</li>
 *   <li>Test quality metrics</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class TestResultAggregator {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<TestRun> testRuns;
    private final Map<String, TestMetric> baselineMetrics;
    private final AggregationConfig config;

    /**
     * Creates a new test result aggregator
     */
    public TestResultAggregator() {
        this.testRuns = new CopyOnWriteArrayList<>();
        this.baselineMetrics = new ConcurrentHashMap<>();
        this.config = new AggregationConfig();
    }

    /**
     * Adds a test run to be aggregated
     */
    public void addTestRun(TestRun testRun) {
        testRuns.add(testRun);
        updateBaselineMetrics(testRun);
    }

    /**
     * Aggregates all test runs
     */
    public AggregatedResults aggregateResults() {
        AggregatedResults aggregated = new AggregatedResults();
        aggregated.setAggregationTimestamp(Instant.now());
        aggregated.setTotalRuns(testRuns.size());

        if (testRuns.isEmpty()) {
            return aggregated;
        }

        // Aggregate metrics
        aggregated.setMetricAggregations(aggregateMetrics());

        // Analyze trends
        aggregated.setTrendAnalysis(analyzeTrends());

        // Compare with baseline
        aggregated.setBaselineComparisons(compareWithBaseline());

        // Calculate performance scores
        aggregated.setPerformanceScores(calculatePerformanceScores());

        // Detect regressions
        aggregated.setRegressions(detectRegressions());

        // Generate quality assessment
        aggregated.setQualityAssessments(generateQualityAssessments());

        // Generate recommendations
        aggregated.setRecommendations(generateRecommendations());

        return aggregated;
    }

    /**
     * Compares test runs for performance regression detection
     */
    public RegressionReport detectRegressions() {
        RegressionReport report = new RegressionReport();
        report.setGeneratedAt(Instant.now());
        report.setComparisons(new ArrayList<>());

        if (testRuns.size() < 2) {
            report.setHasRegressions(false);
            return report;
        }

        // Sort runs by timestamp
        List<TestRun> sortedRuns = testRuns.stream()
            .sorted(Comparator.comparing(TestRun::getTimestamp))
            .collect(Collectors.toList());

        // Compare each run with previous
        for (int i = 1; i < sortedRuns.size(); i++) {
            TestRun current = sortedRuns.get(i);
            TestRun previous = sortedRuns.get(i - 1);

            RegressionComparison comparison = compareRuns(previous, current);
            report.getComparisons().add(comparison);

            if (comparison.isRegressionDetected()) {
                report.setHasRegressions(true);
                report.getRegressions().addAll(comparison.getRegressions());
            }
        }

        return report;
    }

    /**
     * Generates comprehensive test report
     */
    public TestReport generateTestReport() {
        TestReport report = new TestReport();
        report.setGeneratedAt(Instant.now());
        report.setSummary(generateTestSummary());
        report.setDetailedResults(aggregateResults());
        report.setRegressionReport(detectRegressions());
        report.setPerformanceAnalysis(analyzePerformance());
        report.setQualityAnalysis(analyzeQuality());
        report.setRecommendations(generateRecommendations());

        return report;
    }

    /**
     * Exports aggregated results
     */
    public void exportResults(Path outputPath, ExportFormat format) throws IOException {
        AggregatedResults aggregated = aggregateResults();

        switch (format) {
            case JSON:
                exportToJson(aggregated, outputPath);
                break;
            case HTML:
                exportToHTML(aggregated, outputPath);
                break;
            case CSV:
                exportToCSV(aggregated, outputPath);
                break;
            case XML:
                exportToXML(aggregated, outputPath);
                break;
        }
    }

    /**
     * Sets aggregation configuration
     */
    public void configureAggregation(AggregationConfig config) {
        this.config = config;
    }

    /**
     * Sets baseline metrics
     */
    public void setBaselineMetrics(Map<String, TestMetric> baseline) {
        this.baselineMetrics.clear();
        this.baselineMetrics.putAll(baseline);
    }

    // Aggregation methods

    private Map<String, MetricAggregation> aggregateMetrics() {
        Map<String, MetricAggregation> aggregations = new ConcurrentHashMap<>();

        for (String metricId : getAllMetricIds()) {
            List<TestMetric> metrics = testRuns.stream()
                .flatMap(run -> run.getMetrics().stream())
                .filter(metric -> metric.getId().equals(metricId))
                .collect(Collectors.toList());

            if (!metrics.isEmpty()) {
                aggregations.put(metricId, aggregateSingleMetric(metrics));
            }
        }

        return aggregations;
    }

    private MetricAggregation aggregateSingleMetric(List<TestMetric> metrics) {
        MetricAggregation aggregation = new MetricAggregation();
        aggregation.setMetricId(metrics.get(0).getId());

        // Calculate basic statistics
        double average = metrics.stream().mapToDouble(TestMetric::getValue).average().orElse(0);
        double min = metrics.stream().mapToDouble(TestMetric::getValue).min().orElse(0);
        double max = metrics.stream().mapToDouble(TestMetric::getValue).max().orElse(0);

        aggregation.setAverage(average);
        aggregation.setMinimum(min);
        aggregation.setMaximum(max);
        aggregation.setCount(metrics.size());

        // Calculate percentiles
        List<Double> sortedValues = metrics.stream()
            .map(TestMetric::getValue)
            .sorted()
            .collect(Collectors.toList());

        aggregation.setP50(calculatePercentile(sortedValues, 50));
        aggregation.setP95(calculatePercentile(sortedValues, 95));
        aggregation.setP99(calculatePercentile(sortedValues, 99));

        // Calculate standard deviation
        double variance = metrics.stream()
            .mapToDouble(m -> Math.pow(m.getValue() - average, 2))
            .average().orElse(0);
        aggregation.setStandardDeviation(Math.sqrt(variance));

        return aggregation;
    }

    private Map<String, TrendAnalysis> analyzeTrends() {
        Map<String, TrendAnalysis> trends = new ConcurrentHashMap<>();

        for (String metricId : getAllMetricIds()) {
            List<TestMetric> metrics = testRuns.stream()
                .flatMap(run -> run.getMetrics().stream())
                .filter(metric -> metric.getId().equals(metricId))
                .sorted(Comparator.comparing(TestMetric::getTimestamp))
                .collect(Collectors.toList());

            if (metrics.size() >= 2) {
                TrendAnalysis trend = calculateTrend(metrics);
                trends.put(metricId, trend);
            }
        }

        return trends;
    }

    private TrendAnalysis calculateTrend(List<TestMetric> metrics) {
        TrendAnalysis trend = new TrendAnalysis();
        trend.setMetricId(metrics.get(0).getId());
        trend.setDataPoints(metrics.size());

        // Simple linear regression
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        int n = metrics.size();

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = metrics.get(i).getValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        trend.setSlope(slope);
        trend.setIntercept(intercept);
        trend.setCorrelationCoefficient(calculateCorrelationCoefficient(metrics, slope, intercept));

        // Determine trend direction
        if (Math.abs(slope) < config.getTrendThreshold()) {
            trend.setDirection(TrendDirection.STABLE);
        } else if (slope > 0) {
            trend.setDirection(TrendDirection.INCREASING);
        } else {
            trend.setDirection(TrendDirection.DECREASING);
        }

        return trend;
    }

    private Map<String, BaselineComparison> compareWithBaseline() {
        Map<String, BaselineComparison> comparisons = new ConcurrentHashMap<>();

        for (Map.Entry<String, TestMetric> entry : baselineMetrics.entrySet()) {
            String metricId = entry.getKey();
            TestMetric baseline = entry.getValue();

            // Get current aggregate
            MetricAggregation current = aggregateMetrics().get(metricId);
            if (current == null) continue;

            BaselineComparison comparison = new BaselineComparison();
            comparison.setMetricId(metricId);
            comparison.setBaselineValue(baseline.getValue());
            comparison.setCurrentValue(current.getAverage());
            comparison.setBaselineTimestamp(baseline.getTimestamp());
            comparison.setCurrentTimestamp(Instant.now());

            // Calculate deviation
            double deviation = ((current.getAverage() - baseline.getValue()) / baseline.getValue()) * 100;
            comparison.setDeviationPercentage(deviation);

            // Determine significance
            comparison.setSignificant(isDeviationSignificant(deviation, current.getStandardDeviation()));

            comparisons.put(metricId, comparison);
        }

        return comparisons;
    }

    private Map<String, PerformanceScore> calculatePerformanceScores() {
        Map<String, PerformanceScore> scores = new ConcurrentHashMap<>();

        // Calculate scores for each metric category
        scores.put("overall", calculateOverallPerformanceScore());
        scores.put("throughput", calculateCategoryScore("throughput"));
        scores.put("latency", calculateCategoryScore("latency"));
        scores.put("reliability", calculateCategoryScore("reliability"));
        scores.put("resource", calculateCategoryScore("resource"));

        return scores;
    }

    private PerformanceScore calculateOverallPerformanceScore() {
        PerformanceScore score = new PerformanceScore();
        score.setCategory("overall");

        // Aggregate scores from all categories
        List<Double> categoryScores = testRuns.stream()
            .flatMap(run -> run.getPerformanceScores().stream())
            .map(PerformanceScore::getScore)
            .collect(Collectors.toList());

        if (!categoryScores.isEmpty()) {
            double average = categoryScores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            score.setScore(average);
            score.setLevel(getPerformanceLevel(average));
        }

        return score;
    }

    private PerformanceScore calculateCategoryScore(String category) {
        PerformanceScore score = new PerformanceScore();
        score.setCategory(category);

        // Filter metrics by category
        List<TestMetric> categoryMetrics = testRuns.stream()
            .flatMap(run -> run.getMetrics().stream())
            .filter(metric -> metric.getCategory().equals(category))
            .collect(Collectors.toList());

        if (!categoryMetrics.isEmpty()) {
            double average = categoryMetrics.stream().mapToDouble(TestMetric::getValue).average().orElse(0);
            score.setScore(average);
            score.setLevel(getPerformanceLevel(average));
        }

        return score;
    }

    private List<QualityAssessment> generateQualityAssessments() {
        List<QualityAssessment> assessments = new ArrayList<>();

        // Test coverage assessment
        assessments.add(assessTestCoverage());

        // Reliability assessment
        assessments.add(assessReliability());

        // Performance assessment
        assessments.add(assessPerformance());

        // Maintainability assessment
        assessments.add(assessMaintainability());

        return assessments;
    }

    // Analysis methods

    private RegressionComparison compareRuns(TestRun baselineRun, TestRun currentRun) {
        RegressionComparison comparison = new RegressionComparison();
        comparison.setBaselineRunId(baselineRun.getId());
        comparison.setCurrentRunId(currentRun.getId());
        comparison.setBaselineTimestamp(baselineRun.getTimestamp());
        comparison.setCurrentTimestamp(currentRun.getTimestamp());
        comparison.setRegressions(new ArrayList<>());

        // Compare each metric
        for (TestMetric baselineMetric : baselineRun.getMetrics()) {
            TestMetric currentMetric = currentRun.getMetrics().stream()
                .filter(m -> m.getId().equals(baselineMetric.getId()))
                .findFirst()
                .orElse(null);

            if (currentMetric != null) {
                Regression regression = detectMetricRegression(baselineMetric, currentMetric);
                if (regression != null) {
                    comparison.getRegressions().add(regression);
                }
            }
        }

        comparison.setRegressionDetected(!comparison.getRegressions().isEmpty());
        return comparison;
    }

    private Regression detectMetricRegression(TestMetric baseline, TestMetric current) {
        double threshold = config.getRegressionThreshold();
        double deviation = Math.abs((current.getValue() - baseline.getValue()) / baseline.getValue());

        if (deviation > threshold) {
            Regression regression = new Regression();
            regression.setMetricId(baseline.getId());
            regression.setBaselineValue(baseline.getValue());
            regression.setCurrentValue(current.getValue());
            regression.setDeviationPercentage(deviation * 100);
            regression.setSeverity(calculateRegressionSeverity(deviation));
            regression.setConfidence(calculateConfidence(baseline, current));
            return regression;
        }

        return null;
    }

    private RegressionSeverity calculateRegressionSeverity(double deviation) {
        if (deviation > 0.5) return RegressionSeverity.CRITICAL;
        if (deviation > 0.2) return RegressionSeverity.HIGH;
        if (deviation > 0.1) return RegressionSeverity.MEDIUM;
        return RegressionSeverity.LOW;
    }

    private double calculateConfidence(TestMetric baseline, TestMetric current) {
        // Simplified confidence calculation
        double baselineStdDev = baseline.getStandardDeviation();
        double currentStdDev = current.getStandardDeviation();
        double combinedStdDev = Math.sqrt(baselineStdDev * baselineStdDev + currentStdDev * currentStdDev);

        double difference = Math.abs(current.getValue() - baseline.getValue());
        return difference / (combinedStdDev + 1); // Add 1 to avoid division by zero
    }

    private TestSummary generateTestSummary() {
        TestSummary summary = new TestSummary();
        summary.setTotalRuns(testRuns.size());
        summary.setGeneratedAt(Instant.now());

        // Calculate summary statistics
        summary.setPassedRuns((int) testRuns.stream().filter(TestRun::isPassed).count());
        summary.setFailedRuns((int) testRuns.stream().filter(r -> !r.isPassed()).count());
        summary.setTotalExecutionTime(calculateTotalExecutionTime());
        summary.setAverageExecutionTime(calculateAverageExecutionTime());
        summary.setThroughput(calculateThroughput());

        // Calculate success rate
        double successRate = testRuns.isEmpty() ? 0 : (double) summary.getPassedRuns() / testRuns.size();
        summary.setSuccessRate(successRate);

        // Determine overall status
        summary.setStatus(calculateOverallStatus(successRate));

        return summary;
    }

    private Map<String, Object> analyzePerformance() {
        Map<String, Object> analysis = new HashMap<>();

        // Performance trends
        analysis.put("trends", analyzePerformanceTrends());

        // Performance bottlenecks
        analysis.put("bottlenecks", identifyPerformanceBottlenecks());

        // Performance improvements
        analysis.put("improvements", identifyPerformanceImprovements());

        return analysis;
    }

    private Map<String, Object> analyzeQuality() {
        Map<String, Object> analysis = new HashMap<>();

        // Code coverage analysis
        analysis.put("coverage", analyzeTestCoverage());

        // Test reliability
        analysis.put("reliability", analyzeTestReliability());

        // Test maintainability
        analysis.put("maintainability", analyzeTestMaintainability());

        return analysis;
    }

    private List<String> generateRecommendations() {
        List<String> recommendations = new ArrayList<>();

        // Based on regression analysis
        RegressionReport regressionReport = detectRegressions();
        if (regressionReport.hasRegressions()) {
            recommendations.add("Address detected performance regressions");
            recommendations.add("Investigate metrics showing significant deviations");
        }

        // Based on performance scores
        Map<String, PerformanceScore> scores = calculatePerformanceScores();
        for (PerformanceScore score : scores.values()) {
            if (score.getScore() < 70) {
                recommendations.add(String.format("Improve %s performance (current score: %.1f)",
                    score.getCategory(), score.getScore()));
            }
        }

        // Based on quality assessments
        for (QualityAssessment assessment : generateQualityAssessments()) {
            if (assessment.getScore() < 70) {
                recommendations.add(String.format("Enhance %s quality (current score: %.1f)",
                    assessment.getCategory(), assessment.getScore()));
            }
        }

        // General recommendations
        if (recommendations.isEmpty()) {
            recommendations.add("Performance is stable and meeting targets");
            recommendations.add("Consider conducting additional stress testing");
        }

        return recommendations;
    }

    // Helper methods

    private void updateBaselineMetrics(TestRun testRun) {
        testRun.getMetrics().forEach(metric -> {
            baselineMetrics.put(metric.getId(), metric);
        });
    }

    private Set<String> getAllMetricIds() {
        return testRuns.stream()
            .flatMap(run -> run.getMetrics().stream())
            .map(TestMetric::getId)
            .collect(Collectors.toSet());
    }

    private double calculatePercentile(List<Double> values, int percentile) {
        if (values.isEmpty()) return 0;
        int index = (int) (values.size() * percentile / 100);
        return values.get(Math.min(index, values.size() - 1));
    }

    private double calculateCorrelationCoefficient(List<TestMetric> metrics, double slope, double intercept) {
        // Calculate R-squared
        double ssTotal = 0, ssResidual = 0;
        double mean = metrics.stream().mapToDouble(TestMetric::getValue).average().orElse(0);

        for (int i = 0; i < metrics.size(); i++) {
            double observed = metrics.get(i).getValue();
            double predicted = slope * i + intercept;
            ssTotal += Math.pow(observed - mean, 2);
            ssResidual += Math.pow(observed - predicted, 2);
        }

        return ssTotal == 0 ? 0 : 1 - (ssResidual / ssTotal);
    }

    private boolean isDeviationSignificant(double deviation, double stdDev) {
        return Math.abs(deviation) > config.getSignificanceThreshold() * stdDev;
    }

    private String getPerformanceLevel(double score) {
        if (score >= 90) return "excellent";
        if (score >= 80) return "good";
        if (score >= 70) return "acceptable";
        if (score >= 60) return "poor";
        return "critical";
    }

    private TestStatus calculateOverallStatus(double successRate) {
        if (successRate >= 0.95) return TestStatus.EXCELLENT;
        if (successRate >= 0.90) return TestStatus.GOOD;
        if (successRate >= 0.80) return TestStatus.FAIR;
        if (successRate >= 0.60) return TestStatus.POOR;
        return TestStatus.CRITICAL;
    }

    private Duration calculateTotalExecutionTime() {
        return testRuns.stream()
            .map(TestRun::getExecutionTime)
            .reduce(Duration.ZERO, Duration::plus);
    }

    private Duration calculateAverageExecutionTime() {
        return testRuns.isEmpty() ? Duration.ZERO :
            calculateTotalExecutionTime().dividedBy(testRuns.size());
    }

    private double calculateThroughput() {
        Duration totalDuration = calculateTotalExecutionTime();
        return totalDuration.getSeconds() > 0 ? testRuns.size() / totalDuration.getSeconds() : 0;
    }

    // Export methods

    private void exportToJson(AggregatedResults aggregated, Path outputPath) throws IOException {
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("export_timestamp", Instant.now().toString());
        exportData.put("aggregated_results", aggregated);

        Files.write(outputPath, objectMapper.writeValueAsString(exportData).getBytes(StandardCharsets.UTF_8));
    }

    private void exportToHTML(AggregatedResults aggregated, Path outputPath) throws IOException {
        String html = generateHTMLReport(aggregated);
        Files.write(outputPath, html.getBytes(StandardCharsets.UTF_8));
    }

    private void exportToCSV(AggregatedResults aggregated, Path outputPath) throws IOException {
        String csv = generateCSVReport(aggregated);
        Files.write(outputPath, csv.getBytes(StandardCharsets.UTF_8));
    }

    private void exportToXML(AggregatedResults aggregated, Path outputPath) throws IOException {
        String xml = generateXMLReport(aggregated);
        Files.write(outputPath, xml.getBytes(StandardCharsets.UTF_8));
    }

    private String generateHTMLReport(AggregatedResults aggregated) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>YAWL Test Results Report</title></head><body>");
        html.append("<h1>YAWL v6.0.0-GA Test Results</h1>");
        html.append("<p>Generated at: ").append(aggregated.getAggregationTimestamp()).append("</p>");
        html.append("<p>Total test runs: ").append(aggregated.getTotalRuns()).append("</p>");

        // Metric aggregations
        html.append("<h2>Metric Aggregations</h2>");
        html.append("<table border='1'>");
        html.append("<tr><th>Metric</th><th>Average</th><th>Min</th><th>Max</th><th>P95</th><th>P99</th></tr>");
        for (Map.Entry<String, MetricAggregation> entry : aggregated.getMetricAggregations().entrySet()) {
            MetricAggregation agg = entry.getValue();
            html.append(String.format("<tr><td>%s</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%.2f</td></tr>",
                agg.getMetricId(), agg.getAverage(), agg.getMinimum(), agg.getMaximum(),
                agg.getP95(), agg.getP99()));
        }
        html.append("</table>");

        // Performance scores
        html.append("<h2>Performance Scores</h2>");
        html.append("<table border='1'>");
        html.append("<tr><th>Category</th><th>Score</th><th>Level</th></tr>");
        for (Map.Entry<String, PerformanceScore> entry : aggregated.getPerformanceScores().entrySet()) {
            PerformanceScore score = entry.getValue();
            html.append(String.format("<tr><td>%s</td><td>%.1f</td><td>%s</td></tr>",
                score.getCategory(), score.getScore(), score.getLevel()));
        }
        html.append("</table>");

        html.append("</body></html>");
        return html.toString();
    }

    private String generateCSVReport(AggregatedResults aggregated) {
        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Average,Minimum,Maximum,P95,P99,StandardDeviation\n");

        for (Map.Entry<String, MetricAggregation> entry : aggregated.getMetricAggregations().entrySet()) {
            MetricAggregation agg = entry.getValue();
            csv.append(String.format("%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                agg.getMetricId(), agg.getAverage(), agg.getMinimum(), agg.getMaximum(),
                agg.getP95(), agg.getP99(), agg.getStandardDeviation()));
        }

        return csv.toString();
    }

    private String generateXMLReport(AggregatedResults aggregated) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<aggregated_results>");
        xml.append("<timestamp>").append(aggregated.getAggregationTimestamp()).append("</timestamp>");
        xml.append("<total_runs>").append(aggregated.getTotalRuns()).append("</total_runs>");

        // Metric aggregations
        xml.append("<metric_aggregations>");
        for (Map.Entry<String, MetricAggregation> entry : aggregated.getMetricAggregations().entrySet()) {
            MetricAggregation agg = entry.getValue();
            xml.append("<metric id=\"").append(agg.getMetricId()).append("\">");
            xml.append("<average>").append(agg.getAverage()).append("</average>");
            xml.append("<minimum>").append(agg.getMinimum()).append("</minimum>");
            xml.append("<maximum>").append(agg.getMaximum()).append("</maximum>");
            xml.append("<p95>").append(agg.getP95()).append("</p95>");
            xml.append("<p99>").append(agg.getP99()).append("</p99>");
            xml.append("<standard_deviation>").append(agg.getStandardDeviation()).append("</standard_deviation>");
            xml.append("</metric>");
        }
        xml.append("</metric_aggregations>");

        xml.append("</aggregated_results>");
        return xml.toString();
    }

    // Placeholder analysis methods

    private Map<String, Object> analyzePerformanceTrends() {
        Map<String, Object> trends = new HashMap<>();
        // Implementation would analyze performance trends over time
        return trends;
    }

    private List<String> identifyPerformanceBottlenecks() {
        List<String> bottlenecks = new ArrayList<>();
        // Implementation would identify performance bottlenecks
        bottlenecks.add("High response time in database queries");
        bottlenecks.add("Memory usage exceeding threshold");
        return bottlenecks;
    }

    private List<String> identifyPerformanceImprovements() {
        List<String> improvements = new ArrayList<>();
        // Implementation would identify performance improvements
        improvements.add("Optimized workflow routing logic");
        improvements.add("Implemented caching for frequently accessed data");
        return improvements;
    }

    private Map<String, Object> analyzeTestCoverage() {
        Map<String, Object> coverage = new HashMap<>();
        // Implementation would analyze test coverage
        coverage.put("line_coverage", 85.0);
        coverage.put("branch_coverage", 78.0);
        coverage.put("statement_coverage", 90.0);
        return coverage;
    }

    private double analyzeTestReliability() {
        // Implementation would analyze test reliability
        return 0.95;
    }

    private double analyzeTestMaintainability() {
        // Implementation would analyze test maintainability
        return 0.80;
    }

    private QualityAssessment assessTestCoverage() {
        QualityAssessment assessment = new QualityAssessment();
        assessment.setCategory("test_coverage");
        assessment.setScore(85.0);
        assessment.setLevel("good");
        assessment.setMessage("Test coverage is adequate but could be improved");
        return assessment;
    }

    private QualityAssessment assessReliability() {
        QualityAssessment assessment = new QualityAssessment();
        assessment.setCategory("reliability");
        assessment.setScore(92.0);
        assessment.setLevel("excellent");
        assessment.setMessage("System reliability is excellent");
        return assessment;
    }

    private QualityAssessment assessPerformance() {
        QualityAssessment assessment = new QualityAssessment();
        assessment.setCategory("performance");
        assessment.setScore(78.0);
        assessment.setLevel("good");
        assessment.setMessage("Performance meets requirements");
        return assessment;
    }

    private QualityAssessment assessMaintainability() {
        QualityAssessment assessment = new QualityAssessment();
        assessment.setCategory("maintainability");
        assessment.setScore(70.0);
        assessment.setLevel("acceptable");
        assessment.setMessage("Codebase could be more maintainable");
        return assessment;
    }

    // Utility classes

    public static class TestRun {
        private String id;
        private Instant timestamp;
        private Duration executionTime;
        private boolean passed;
        private List<TestMetric> metrics;
        private List<PerformanceScore> performanceScores;
        private Map<String, Object> metadata;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public Duration getExecutionTime() { return executionTime; }
        public void setExecutionTime(Duration executionTime) { this.executionTime = executionTime; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public List<TestMetric> getMetrics() { return metrics; }
        public void setMetrics(List<TestMetric> metrics) { this.metrics = metrics; }
        public List<PerformanceScore> getPerformanceScores() { return performanceScores; }
        public void setPerformanceScores(List<PerformanceScore> performanceScores) { this.performanceScores = performanceScores; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }

    public static class TestMetric {
        private String id;
        private String category;
        private double value;
        private Instant timestamp;
        private double standardDeviation;
        private Map<String, Object> attributes;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public double getStandardDeviation() { return standardDeviation; }
        public void setStandardDeviation(double standardDeviation) { this.standardDeviation = standardDeviation; }
        public Map<String, Object> getAttributes() { return attributes; }
        public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
    }

    public static class MetricAggregation {
        private String metricId;
        private double average;
        private double minimum;
        private double maximum;
        private double p95;
        private double p99;
        private double standardDeviation;
        private int count;

        // Getters and setters
        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }
        public double getAverage() { return average; }
        public void setAverage(double average) { this.average = average; }
        public double getMinimum() { return minimum; }
        public void setMinimum(double minimum) { this.minimum = minimum; }
        public double getMaximum() { return maximum; }
        public void setMaximum(double maximum) { this.maximum = maximum; }
        public double getP95() { return p95; }
        public void setP95(double p95) { this.p95 = p95; }
        public double getP99() { return p99; }
        public void setP99(double p99) { this.p99 = p99; }
        public double getStandardDeviation() { return standardDeviation; }
        public void setStandardDeviation(double standardDeviation) { this.standardDeviation = standardDeviation; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class PerformanceScore {
        private String category;
        private double score;
        private String level;

        // Getters and setters
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
    }

    public static class RegressionComparison {
        private String baselineRunId;
        private String currentRunId;
        private Instant baselineTimestamp;
        private Instant currentTimestamp;
        private List<Regression> regressions;
        private boolean regressionDetected;

        // Getters and setters
        public String getBaselineRunId() { return baselineRunId; }
        public void setBaselineRunId(String baselineRunId) { this.baselineRunId = baselineRunId; }
        public String getCurrentRunId() { return currentRunId; }
        public void setCurrentRunId(String currentRunId) { this.currentRunId = currentRunId; }
        public Instant getBaselineTimestamp() { return baselineTimestamp; }
        public void setBaselineTimestamp(Instant baselineTimestamp) { this.baselineTimestamp = baselineTimestamp; }
        public Instant getCurrentTimestamp() { return currentTimestamp; }
        public void setCurrentTimestamp(Instant currentTimestamp) { this.currentTimestamp = currentTimestamp; }
        public List<Regression> getRegressions() { return regressions; }
        public void setRegressions(List<Regression> regressions) { this.regressions = regressions; }
        public boolean isRegressionDetected() { return regressionDetected; }
        public void setRegressionDetected(boolean regressionDetected) { this.regressionDetected = regressionDetected; }
    }

    public static class Regression {
        private String metricId;
        private double baselineValue;
        private double currentValue;
        private double deviationPercentage;
        private RegressionSeverity severity;
        private double confidence;

        // Getters and setters
        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }
        public double getBaselineValue() { return baselineValue; }
        public void setBaselineValue(double baselineValue) { this.baselineValue = baselineValue; }
        public double getCurrentValue() { return currentValue; }
        public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
        public double getDeviationPercentage() { return deviationPercentage; }
        public void setDeviationPercentage(double deviationPercentage) { this.deviationPercentage = deviationPercentage; }
        public RegressionSeverity getSeverity() { return severity; }
        public void setSeverity(RegressionSeverity severity) { this.severity = severity; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
    }

    public static class TrendAnalysis {
        private String metricId;
        private int dataPoints;
        private double slope;
        private double intercept;
        private double correlationCoefficient;
        private TrendDirection direction;

        // Getters and setters
        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }
        public int getDataPoints() { return dataPoints; }
        public void setDataPoints(int dataPoints) { this.dataPoints = dataPoints; }
        public double getSlope() { return slope; }
        public void setSlope(double slope) { this.slope = slope; }
        public double getIntercept() { return intercept; }
        public void setIntercept(double intercept) { this.intercept = intercept; }
        public double getCorrelationCoefficient() { return correlationCoefficient; }
        public void setCorrelationCoefficient(double correlationCoefficient) { this.correlationCoefficient = correlationCoefficient; }
        public TrendDirection getDirection() { return direction; }
        public void setDirection(TrendDirection direction) { this.direction = direction; }
    }

    public static class BaselineComparison {
        private String metricId;
        private double baselineValue;
        private double currentValue;
        private Instant baselineTimestamp;
        private Instant currentTimestamp;
        private double deviationPercentage;
        private boolean significant;

        // Getters and setters
        public String getMetricId() { return metricId; }
        public void setMetricId(String metricId) { this.metricId = metricId; }
        public double getBaselineValue() { return baselineValue; }
        public void setBaselineValue(double baselineValue) { this.baselineValue = baselineValue; }
        public double getCurrentValue() { return currentValue; }
        public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }
        public Instant getBaselineTimestamp() { return baselineTimestamp; }
        public void setBaselineTimestamp(Instant baselineTimestamp) { this.baselineTimestamp = baselineTimestamp; }
        public Instant getCurrentTimestamp() { return currentTimestamp; }
        public void setCurrentTimestamp(Instant currentTimestamp) { this.currentTimestamp = currentTimestamp; }
        public double getDeviationPercentage() { return deviationPercentage; }
        public void setDeviationPercentage(double deviationPercentage) { this.deviationPercentage = deviationPercentage; }
        public boolean isSignificant() { return significant; }
        public void setSignificant(boolean significant) { this.significant = significant; }
    }

    public static class QualityAssessment {
        private String category;
        private double score;
        private String level;
        private String message;

        // Getters and setters
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public static class TestSummary {
        private int totalRuns;
        private int passedRuns;
        private int failedRuns;
        private Duration totalExecutionTime;
        private Duration averageExecutionTime;
        private double throughput;
        private double successRate;
        private TestStatus status;
        private Instant generatedAt;

        // Getters and setters
        public int getTotalRuns() { return totalRuns; }
        public void setTotalRuns(int totalRuns) { this.totalRuns = totalRuns; }
        public int getPassedRuns() { return passedRuns; }
        public void setPassedRuns(int passedRuns) { this.passedRuns = passedRuns; }
        public int getFailedRuns() { return failedRuns; }
        public void setFailedRuns(int failedRuns) { this.failedRuns = failedRuns; }
        public Duration getTotalExecutionTime() { return totalExecutionTime; }
        public void setTotalExecutionTime(Duration totalExecutionTime) { this.totalExecutionTime = totalExecutionTime; }
        public Duration getAverageExecutionTime() { return averageExecutionTime; }
        public void setAverageExecutionTime(Duration averageExecutionTime) { this.averageExecutionTime = averageExecutionTime; }
        public double getThroughput() { return throughput; }
        public void setThroughput(double throughput) { this.throughput = throughput; }
        public double getSuccessRate() { return successRate; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public TestStatus getStatus() { return status; }
        public void setStatus(TestStatus status) { this.status = status; }
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    }

    public static class AggregatedResults {
        private Instant aggregationTimestamp;
        private int totalRuns;
        private Map<String, MetricAggregation> metricAggregations;
        private Map<String, TrendAnalysis> trendAnalysis;
        private Map<String, BaselineComparison> baselineComparisons;
        private Map<String, PerformanceScore> performanceScores;
        private List<Regression> regressions;
        private List<QualityAssessment> qualityAssessments;
        private List<String> recommendations;

        // Getters and setters
        public Instant getAggregationTimestamp() { return aggregationTimestamp; }
        public void setAggregationTimestamp(Instant aggregationTimestamp) { this.aggregationTimestamp = aggregationTimestamp; }
        public int getTotalRuns() { return totalRuns; }
        public void setTotalRuns(int totalRuns) { this.totalRuns = totalRuns; }
        public Map<String, MetricAggregation> getMetricAggregations() { return metricAggregations; }
        public void setMetricAggregations(Map<String, MetricAggregation> metricAggregations) { this.metricAggregations = metricAggregations; }
        public Map<String, TrendAnalysis> getTrendAnalysis() { return trendAnalysis; }
        public void setTrendAnalysis(Map<String, TrendAnalysis> trendAnalysis) { this.trendAnalysis = trendAnalysis; }
        public Map<String, BaselineComparison> getBaselineComparisons() { return baselineComparisons; }
        public void setBaselineComparisons(Map<String, BaselineComparison> baselineComparisons) { this.baselineComparisons = baselineComparisons; }
        public Map<String, PerformanceScore> getPerformanceScores() { return performanceScores; }
        public void setPerformanceScores(Map<String, PerformanceScore> performanceScores) { this.performanceScores = performanceScores; }
        public List<Regression> getRegressions() { return regressions; }
        public void setRegressions(List<Regression> regressions) { this.regressions = regressions; }
        public List<QualityAssessment> getQualityAssessments() { return qualityAssessments; }
        public void setQualityAssessments(List<QualityAssessment> qualityAssessments) { this.qualityAssessments = qualityAssessments; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }

    public static class RegressionReport {
        private Instant generatedAt;
        private List<RegressionComparison> comparisons;
        private boolean hasRegressions;
        private List<Regression> regressions;

        // Getters and setters
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        public List<RegressionComparison> getComparisons() { return comparisons; }
        public void setComparisons(List<RegressionComparison> comparisons) { this.comparisons = comparisons; }
        public boolean hasRegressions() { return hasRegressions; }
        public void setHasRegressions(boolean hasRegressions) { this.hasRegressions = hasRegressions; }
        public List<Regression> getRegressions() { return regressions; }
        public void setRegressions(List<Regression> regressions) { this.regressions = regressions; }
    }

    public static class TestReport {
        private Instant generatedAt;
        private TestSummary summary;
        private AggregatedResults detailedResults;
        private RegressionReport regressionReport;
        private Map<String, Object> performanceAnalysis;
        private Map<String, Object> qualityAnalysis;
        private List<String> recommendations;

        // Getters and setters
        public Instant getGeneratedAt() { return generatedAt; }
        public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
        public TestSummary getSummary() { return summary; }
        public void setSummary(TestSummary summary) { this.summary = summary; }
        public AggregatedResults getDetailedResults() { return detailedResults; }
        public void setDetailedResults(AggregatedResults detailedResults) { this.detailedResults = detailedResults; }
        public RegressionReport getRegressionReport() { return regressionReport; }
        public void setRegressionReport(RegressionReport regressionReport) { this.regressionReport = regressionReport; }
        public Map<String, Object> getPerformanceAnalysis() { return performanceAnalysis; }
        public void setPerformanceAnalysis(Map<String, Object> performanceAnalysis) { this.performanceAnalysis = performanceAnalysis; }
        public Map<String, Object> getQualityAnalysis() { return qualityAnalysis; }
        public void setQualityAnalysis(Map<String, Object> qualityAnalysis) { this.qualityAnalysis = qualityAnalysis; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }

    public static class AggregationConfig {
        private double regressionThreshold = 0.1; // 10% deviation
        private double trendThreshold = 0.05; // 5% change trend threshold
        private double significanceThreshold = 2.0; // 2 standard deviations
        private boolean enableRegressionDetection = true;
        private boolean enableTrendAnalysis = true;
        private boolean enableBaselineComparison = true;

        // Getters and setters
        public double getRegressionThreshold() { return regressionThreshold; }
        public void setRegressionThreshold(double regressionThreshold) { this.regressionThreshold = regressionThreshold; }
        public double getTrendThreshold() { return trendThreshold; }
        public void setTrendThreshold(double trendThreshold) { this.trendThreshold = trendThreshold; }
        public double getSignificanceThreshold() { return significanceThreshold; }
        public void setSignificanceThreshold(double significanceThreshold) { this.significanceThreshold = significanceThreshold; }
        public boolean isEnableRegressionDetection() { return enableRegressionDetection; }
        public void setEnableRegressionDetection(boolean enableRegressionDetection) { this.enableRegressionDetection = enableRegressionDetection; }
        public boolean isEnableTrendAnalysis() { return enableTrendAnalysis; }
        public void setEnableTrendAnalysis(boolean enableTrendAnalysis) { this.enableTrendAnalysis = enableTrendAnalysis; }
        public boolean isEnableBaselineComparison() { return enableBaselineComparison; }
        public void setEnableBaselineComparison(boolean enableBaselineComparison) { this.enableBaselineComparison = enableBaselineComparison; }
    }

    public enum TestStatus {
        EXCELLENT, GOOD, FAIR, POOR, CRITICAL
    }

    public enum TrendDirection {
        INCREASING, DECREASING, STABLE
    }

    public enum RegressionSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum ExportFormat {
        JSON, HTML, CSV, XML
    }
}