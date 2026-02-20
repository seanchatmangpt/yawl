/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.report;

import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternCategory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Multi-format report generator for YAWL pattern demo results.
 *
 * <p>Generates reports in four output formats:
 * <ul>
 *   <li><b>Console</b> - ANSI colored terminal output with tables</li>
 *   <li><b>JSON</b> - Structured JSON for programmatic consumption</li>
 *   <li><b>Markdown</b> - GitHub-flavored markdown with tables and code blocks</li>
 *   <li><b>HTML</b> - Interactive HTML with Chart.js visualizations</li>
 * </ul></p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ReportGenerator {

    /**
     * ANSI color codes for console output.
     */
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_GRAY = "\u001B[90m";

    /**
     * Line separator for reports.
     */
    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * ISO 8601 datetime formatter.
     */
    private static final DateTimeFormatter ISO_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    /**
     * Human-readable datetime formatter.
     */
    private static final DateTimeFormatter HUMAN_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    /**
     * Create a new report generator.
     */
    public ReportGenerator() {
        // Default constructor
    }

    /**
     * Generate ANSI colored console output with tables.
     *
     * @param report the report to format
     * @return formatted console output string
     */
    public String generateConsole(YawlPatternDemoReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(8192);

        // Header
        appendConsoleHeader(sb, report);

        // Summary section
        appendConsoleSummary(sb, report);

        // Token savings section
        appendConsoleTokenSavings(sb, report);

        // Category breakdown
        appendConsoleCategories(sb, report);

        // Individual pattern results
        appendConsolePatterns(sb, report);

        // Failures section (if any)
        if (report.getFailedPatterns() > 0) {
            appendConsoleFailures(sb, report);
        }

        // Footer
        appendConsoleFooter(sb, report);

        return sb.toString();
    }

    private void appendConsoleHeader(StringBuilder sb, YawlPatternDemoReport report) {
        sb.append(ANSI_BOLD).append(ANSI_CYAN);
        sb.append("======================================================================").append(LINE_SEPARATOR);
        sb.append("  YAWL PATTERN DEMO REPORT").append(LINE_SEPARATOR);
        sb.append("  Generated: ").append(ISO_FORMATTER.format(report.getGeneratedAt())).append(LINE_SEPARATOR);
        sb.append("======================================================================").append(LINE_SEPARATOR);
        sb.append(ANSI_RESET).append(LINE_SEPARATOR);
    }

    private void appendConsoleSummary(StringBuilder sb, YawlPatternDemoReport report) {
        sb.append(ANSI_BOLD).append("SUMMARY").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        sb.append(String.format("  %-16s %d", "Patterns Run:", report.getTotalPatterns())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-16s %s%d%s",
            "Successful:",
            ANSI_GREEN, report.getSuccessfulPatterns(), ANSI_RESET)).append(LINE_SEPARATOR);
        sb.append(String.format("  %-16s %s%d%s",
            "Failed:",
            report.getFailedPatterns() > 0 ? ANSI_RED : ANSI_GRAY,
            report.getFailedPatterns(),
            ANSI_RESET)).append(LINE_SEPARATOR);

        String successRateColor = report.getSuccessRate() >= 90 ? ANSI_GREEN :
            (report.getSuccessRate() >= 70 ? ANSI_YELLOW : ANSI_RED);
        sb.append(String.format("  %-16s %s%.1f%%%s",
            "Success Rate:",
            successRateColor, report.getSuccessRate(), ANSI_RESET)).append(LINE_SEPARATOR);

        sb.append(String.format("  %-16s %s", "Total Duration:", formatDuration(report.getTotalTime()))).append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleTokenSavings(StringBuilder sb, YawlPatternDemoReport report) {
        sb.append(ANSI_BOLD).append("TOKEN SAVINGS (YAML vs XML)").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        sb.append(String.format("  %-16s %s", "YAML Tokens:", formatNumber(report.getTotalYamlTokens()))).append(LINE_SEPARATOR);
        sb.append(String.format("  %-16s %s", "XML Tokens:", formatNumber(report.getTotalXmlTokens()))).append(LINE_SEPARATOR);

        String savingsColor = report.getTotalTokenSavings() >= 70 ? ANSI_GREEN :
            (report.getTotalTokenSavings() >= 50 ? ANSI_YELLOW : ANSI_GRAY);
        sb.append(String.format("  %-16s %s%.1f%%%s",
            "Savings:",
            savingsColor, report.getTotalTokenSavings(), ANSI_RESET)).append(LINE_SEPARATOR);

        sb.append(String.format("  %-16s %.1fx", "Compression:", report.getCompressionRatio())).append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleCategories(StringBuilder sb, YawlPatternDemoReport report) {
        Map<PatternCategory, YawlPatternDemoReport.CategorySummary> categories = report.getSummaryByCategory();

        if (categories.isEmpty()) {
            return;
        }

        sb.append(ANSI_BOLD).append("BY CATEGORY").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        sb.append(String.format("  %-22s %5s %7s %10s %8s",
            "Category", "Count", "Success", "Avg Time", "Savings")).append(LINE_SEPARATOR);
        sb.append("  ").append("-".repeat(56)).append(LINE_SEPARATOR);

        categories.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                PatternCategory cat = entry.getKey();
                YawlPatternDemoReport.CategorySummary summary = entry.getValue();

                sb.append(String.format("  %s%-22s%s %5d %6.1f%% %10s %7.1f%%",
                    cat.getColorCode(),
                    truncate(cat.getDisplayName(), 22),
                    ANSI_RESET,
                    summary.getCount(),
                    summary.getSuccessRate(),
                    summary.getFormattedAvgTime(),
                    summary.getTokenSavings())).append(LINE_SEPARATOR);
            });

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsolePatterns(StringBuilder sb, YawlPatternDemoReport report) {
        sb.append(ANSI_BOLD).append("PATTERN RESULTS").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        for (PatternResult result : report.getResults()) {
            String statusIcon = result.isSuccess() ?
                ANSI_GREEN + "[PASS]" + ANSI_RESET :
                ANSI_RED + "[FAIL]" + ANSI_RESET;

            String patternName = result.getPatternInfo() != null ?
                result.getPatternInfo().name() : result.getPatternId();

            sb.append(String.format("  %s %s", statusIcon, patternName)).append(LINE_SEPARATOR);

            sb.append(String.format("        Duration: %s", result.getFormattedDuration()));

            if (result.getMetrics() != null) {
                sb.append(String.format(" | Work Items: %d", result.getMetrics().getWorkItemCount()));
            }

            sb.append(LINE_SEPARATOR);

            if (result.getTokenAnalysis() != null) {
                sb.append(String.format("        Tokens: %d (YAML) vs %d (XML) = %.0f%% saved",
                    result.getTokenAnalysis().getYamlTokens(),
                    result.getTokenAnalysis().getXmlTokens(),
                    result.getTokenAnalysis().getSavingsPercentage())).append(LINE_SEPARATOR);
            }
        }

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleFailures(StringBuilder sb, YawlPatternDemoReport report) {
        sb.append(ANSI_BOLD).append(ANSI_RED).append("FAILURES").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        for (PatternResult failure : report.getFailures()) {
            String patternName = failure.getPatternInfo() != null ?
                failure.getPatternInfo().name() : failure.getPatternId();

            sb.append(String.format("  %s: %s",
                patternName,
                failure.getError() != null ? failure.getError() : "Unknown error")).append(LINE_SEPARATOR);
        }

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleFooter(StringBuilder sb, YawlPatternDemoReport report) {
        sb.append(ANSI_GRAY);
        sb.append("----------------------------------------------------------------------").append(LINE_SEPARATOR);
        sb.append("  YAWL Foundation - Yet Another Workflow Language v6.0.0").append(LINE_SEPARATOR);
        sb.append("  Report generated at: ").append(HUMAN_FORMATTER.format(report.getGeneratedAt())).append(LINE_SEPARATOR);
        sb.append(ANSI_RESET);
    }

    /**
     * Generate structured JSON output.
     *
     * @param report the report to format
     * @return JSON formatted string
     */
    public String generateJson(YawlPatternDemoReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(16384);

        sb.append("{").append(LINE_SEPARATOR);

        // Metadata
        appendJsonProperty(sb, "generatedAt", ISO_FORMATTER.format(report.getGeneratedAt()), 1);
        appendJsonProperty(sb, "version", "6.0.0", 1, false);
        sb.append(",").append(LINE_SEPARATOR);

        // Summary
        sb.append(indent(1)).append("\"summary\": {").append(LINE_SEPARATOR);
        appendJsonProperty(sb, "totalPatterns", report.getTotalPatterns(), 2);
        appendJsonProperty(sb, "successfulPatterns", report.getSuccessfulPatterns(), 2);
        appendJsonProperty(sb, "failedPatterns", report.getFailedPatterns(), 2);
        appendJsonProperty(sb, "successRate", report.getSuccessRate(), 2);
        appendJsonProperty(sb, "totalDurationMs", report.getTotalTime().toMillis(), 2, false);
        sb.append(LINE_SEPARATOR).append(indent(1)).append("},").append(LINE_SEPARATOR);

        // Token analysis
        sb.append(indent(1)).append("\"tokenAnalysis\": {").append(LINE_SEPARATOR);
        appendJsonProperty(sb, "yamlTokens", report.getTotalYamlTokens(), 2);
        appendJsonProperty(sb, "xmlTokens", report.getTotalXmlTokens(), 2);
        appendJsonProperty(sb, "savingsPercent", report.getTotalTokenSavings(), 2);
        appendJsonProperty(sb, "compressionRatio", report.getCompressionRatio(), 2, false);
        sb.append(LINE_SEPARATOR).append(indent(1)).append("},").append(LINE_SEPARATOR);

        // Categories
        sb.append(indent(1)).append("\"categories\": [").append(LINE_SEPARATOR);
        appendJsonCategories(sb, report);
        sb.append(indent(1)).append("],").append(LINE_SEPARATOR);

        // Results
        sb.append(indent(1)).append("\"results\": [").append(LINE_SEPARATOR);
        appendJsonResults(sb, report);
        sb.append(indent(1)).append("]").append(LINE_SEPARATOR);

        sb.append("}").append(LINE_SEPARATOR);

        return sb.toString();
    }

    private void appendJsonCategories(StringBuilder sb, YawlPatternDemoReport report) {
        Map<PatternCategory, YawlPatternDemoReport.CategorySummary> categories = report.getSummaryByCategory();

        int count = 0;
        for (Map.Entry<PatternCategory, YawlPatternDemoReport.CategorySummary> entry : categories.entrySet()) {
            if (count++ > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            PatternCategory cat = entry.getKey();
            YawlPatternDemoReport.CategorySummary summary = entry.getValue();

            sb.append(indent(2)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "name", cat.getDisplayName(), 3);
            appendJsonProperty(sb, "count", summary.getCount(), 3);
            appendJsonProperty(sb, "success", summary.getSuccess(), 3);
            appendJsonProperty(sb, "failure", summary.getFailure(), 3);
            appendJsonProperty(sb, "successRate", summary.getSuccessRate(), 3);
            appendJsonProperty(sb, "avgDurationMs", summary.getAvgTime().toMillis(), 3);
            appendJsonProperty(sb, "tokenSavingsPercent", summary.getTokenSavings(), 3, false);
            sb.append(LINE_SEPARATOR).append(indent(2)).append("}");
        }
        sb.append(LINE_SEPARATOR);
    }

    private void appendJsonResults(StringBuilder sb, YawlPatternDemoReport report) {
        List<PatternResult> results = report.getResults();

        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            PatternResult result = results.get(i);

            sb.append(indent(2)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "patternId", escapeJson(result.getPatternId()), 3);
            appendJsonProperty(sb, "success", result.isSuccess(), 3);

            if (result.getPatternInfo() != null) {
                appendJsonProperty(sb, "name", escapeJson(result.getPatternInfo().name()), 3);
            }

            appendJsonProperty(sb, "durationMs", result.getDuration().toMillis(), 3);

            if (result.getMetrics() != null) {
                appendJsonProperty(sb, "workItemCount", result.getMetrics().getWorkItemCount(), 3);
                appendJsonProperty(sb, "eventCount", result.getMetrics().getEventCount(), 3);
            }

            if (result.getTokenAnalysis() != null) {
                sb.append(",").append(LINE_SEPARATOR);
                sb.append(indent(3)).append("\"tokenAnalysis\": {").append(LINE_SEPARATOR);
                appendJsonProperty(sb, "yamlTokens", result.getTokenAnalysis().getYamlTokens(), 4);
                appendJsonProperty(sb, "xmlTokens", result.getTokenAnalysis().getXmlTokens(), 4);
                appendJsonProperty(sb, "savingsPercent", result.getTokenAnalysis().getSavingsPercentage(), 4, false);
                sb.append(LINE_SEPARATOR).append(indent(3)).append("}").append(LINE_SEPARATOR);
            } else {
                sb.append(LINE_SEPARATOR);
            }

            if (!result.isSuccess() && result.getError() != null) {
                sb.append(",").append(LINE_SEPARATOR);
                appendJsonProperty(sb, "error", escapeJson(result.getError()), 3, false);
                sb.append(LINE_SEPARATOR);
            }

            sb.append(indent(2)).append("}");
        }
        sb.append(LINE_SEPARATOR);
    }

    /**
     * Generate GitHub-flavored markdown output.
     *
     * @param report the report to format
     * @return markdown formatted string
     */
    public String generateMarkdown(YawlPatternDemoReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(16384);

        // Title
        sb.append("# YAWL Pattern Demo Report").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("> Generated: `").append(ISO_FORMATTER.format(report.getGeneratedAt())).append("`").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        // Summary section
        sb.append("## Summary").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("| Metric | Value |").append(LINE_SEPARATOR);
        sb.append("|--------|-------|").append(LINE_SEPARATOR);
        sb.append("| Patterns Run | ").append(report.getTotalPatterns()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Successful | **").append(report.getSuccessfulPatterns()).append("** |").append(LINE_SEPARATOR);
        sb.append("| Failed | ").append(report.getFailedPatterns()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Success Rate | **").append(String.format("%.1f%%", report.getSuccessRate())).append("** |").append(LINE_SEPARATOR);
        sb.append("| Total Duration | ").append(formatDuration(report.getTotalTime())).append(" |").append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);

        // Token savings
        sb.append("## Token Savings (YAML vs XML)").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("| Metric | Value |").append(LINE_SEPARATOR);
        sb.append("|--------|-------|").append(LINE_SEPARATOR);
        sb.append("| YAML Tokens | ").append(formatNumber(report.getTotalYamlTokens())).append(" |").append(LINE_SEPARATOR);
        sb.append("| XML Tokens | ").append(formatNumber(report.getTotalXmlTokens())).append(" |").append(LINE_SEPARATOR);
        sb.append("| Savings | **").append(String.format("%.1f%%", report.getTotalTokenSavings())).append("** |").append(LINE_SEPARATOR);
        sb.append("| Compression Ratio | ").append(String.format("%.1fx", report.getCompressionRatio())).append(" |").append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);

        // Category breakdown
        sb.append("## Results by Category").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        Map<PatternCategory, YawlPatternDemoReport.CategorySummary> categories = report.getSummaryByCategory();

        if (!categories.isEmpty()) {
            sb.append("| Category | Count | Success | Avg Time | Token Savings |").append(LINE_SEPARATOR);
            sb.append("|----------|-------|---------|----------|---------------|").append(LINE_SEPARATOR);

            categories.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    PatternCategory cat = entry.getKey();
                    YawlPatternDemoReport.CategorySummary summary = entry.getValue();

                    sb.append("| ").append(cat.getDisplayName());
                    sb.append(" | ").append(summary.getCount());
                    sb.append(" | ").append(String.format("%.1f%%", summary.getSuccessRate()));
                    sb.append(" | ").append(summary.getFormattedAvgTime());
                    sb.append(" | ").append(String.format("%.1f%%", summary.getTokenSavings()));
                    sb.append(" |").append(LINE_SEPARATOR);
                });

            sb.append(LINE_SEPARATOR);
        }

        // Pattern results
        sb.append("## Pattern Results").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("| Status | Pattern | Duration | Work Items | Token Savings |").append(LINE_SEPARATOR);
        sb.append("|--------|---------|----------|------------|---------------|").append(LINE_SEPARATOR);

        for (PatternResult result : report.getResults()) {
            String status = result.isSuccess() ? ":white_check_mark: PASS" : ":x: FAIL";
            String patternName = result.getPatternInfo() != null ?
                result.getPatternInfo().name() : result.getPatternId();

            sb.append("| ").append(status);
            sb.append(" | `").append(patternName).append("`");
            sb.append(" | ").append(result.getFormattedDuration());

            if (result.getMetrics() != null) {
                sb.append(" | ").append(result.getMetrics().getWorkItemCount());
            } else {
                sb.append(" | -");
            }

            if (result.getTokenAnalysis() != null) {
                sb.append(" | ").append(String.format("%.0f%%", result.getTokenAnalysis().getSavingsPercentage()));
            } else {
                sb.append(" | -");
            }

            sb.append(" |").append(LINE_SEPARATOR);
        }

        sb.append(LINE_SEPARATOR);

        // Failures section
        if (report.getFailedPatterns() > 0) {
            sb.append("## Failures").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

            sb.append("```").append(LINE_SEPARATOR);
            for (PatternResult failure : report.getFailures()) {
                String patternName = failure.getPatternInfo() != null ?
                    failure.getPatternInfo().name() : failure.getPatternId();

                sb.append(patternName).append(": ");
                sb.append(failure.getError() != null ? failure.getError() : "Unknown error");
                sb.append(LINE_SEPARATOR);
            }
            sb.append("```").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }

        // Footer
        sb.append("---").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        sb.append("*YAWL Foundation - Yet Another Workflow Language v6.0.0*").append(LINE_SEPARATOR);

        return sb.toString();
    }

    /**
     * Generate interactive HTML with Chart.js visualizations.
     *
     * @param report the report to format
     * @return HTML formatted string
     */
    public String generateHtml(YawlPatternDemoReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(32768);

        // HTML header
        sb.append("<!DOCTYPE html>").append(LINE_SEPARATOR);
        sb.append("<html lang=\"en\">").append(LINE_SEPARATOR);
        sb.append("<head>").append(LINE_SEPARATOR);
        sb.append("  <meta charset=\"UTF-8\">").append(LINE_SEPARATOR);
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">").append(LINE_SEPARATOR);
        sb.append("  <title>YAWL Pattern Demo Report</title>").append(LINE_SEPARATOR);
        sb.append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js\"></script>").append(LINE_SEPARATOR);
        sb.append("  <style>").append(LINE_SEPARATOR);
        appendHtmlStyles(sb);
        sb.append("  </style>").append(LINE_SEPARATOR);
        sb.append("</head>").append(LINE_SEPARATOR);
        sb.append("<body>").append(LINE_SEPARATOR);

        // Header
        sb.append("  <header>").append(LINE_SEPARATOR);
        sb.append("    <h1>YAWL Pattern Demo Report</h1>").append(LINE_SEPARATOR);
        sb.append("    <p class=\"generated\">Generated: ").append(ISO_FORMATTER.format(report.getGeneratedAt())).append("</p>").append(LINE_SEPARATOR);
        sb.append("  </header>").append(LINE_SEPARATOR);

        // Summary cards
        sb.append("  <section class=\"summary-cards\">").append(LINE_SEPARATOR);
        appendHtmlSummaryCard(sb, "Total Patterns", String.valueOf(report.getTotalPatterns()), "patterns");
        appendHtmlSummaryCard(sb, "Successful", String.valueOf(report.getSuccessfulPatterns()), "success");
        appendHtmlSummaryCard(sb, "Failed", String.valueOf(report.getFailedPatterns()), report.getFailedPatterns() > 0 ? "failure" : "success");
        appendHtmlSummaryCard(sb, "Success Rate", String.format("%.1f%%", report.getSuccessRate()), report.getSuccessRate() >= 90 ? "success" : "warning");
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Charts section
        sb.append("  <section class=\"charts\">").append(LINE_SEPARATOR);
        sb.append("    <div class=\"chart-container\">").append(LINE_SEPARATOR);
        sb.append("      <h2>Pattern Results Distribution</h2>").append(LINE_SEPARATOR);
        sb.append("      <canvas id=\"resultsChart\"></canvas>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("    <div class=\"chart-container\">").append(LINE_SEPARATOR);
        sb.append("      <h2>Token Comparison</h2>").append(LINE_SEPARATOR);
        sb.append("      <canvas id=\"tokenChart\"></canvas>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("    <div class=\"chart-container full-width\">").append(LINE_SEPARATOR);
        sb.append("      <h2>Results by Category</h2>").append(LINE_SEPARATOR);
        sb.append("      <canvas id=\"categoryChart\"></canvas>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Token savings section
        sb.append("  <section class=\"token-savings\">").append(LINE_SEPARATOR);
        sb.append("    <h2>Token Savings Analysis</h2>").append(LINE_SEPARATOR);
        sb.append("    <table>").append(LINE_SEPARATOR);
        sb.append("      <tr><th>Metric</th><th>Value</th></tr>").append(LINE_SEPARATOR);
        sb.append("      <tr><td>YAML Tokens</td><td>").append(formatNumber(report.getTotalYamlTokens())).append("</td></tr>").append(LINE_SEPARATOR);
        sb.append("      <tr><td>XML Tokens</td><td>").append(formatNumber(report.getTotalXmlTokens())).append("</td></tr>").append(LINE_SEPARATOR);
        sb.append("      <tr><td>Savings</td><td class=\"highlight\">").append(String.format("%.1f%%", report.getTotalTokenSavings())).append("</td></tr>").append(LINE_SEPARATOR);
        sb.append("      <tr><td>Compression Ratio</td><td>").append(String.format("%.1fx", report.getCompressionRatio())).append("</td></tr>").append(LINE_SEPARATOR);
        sb.append("    </table>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Pattern results table
        sb.append("  <section class=\"results\">").append(LINE_SEPARATOR);
        sb.append("    <h2>Pattern Results</h2>").append(LINE_SEPARATOR);
        sb.append("    <table>").append(LINE_SEPARATOR);
        sb.append("      <thead>").append(LINE_SEPARATOR);
        sb.append("        <tr><th>Status</th><th>Pattern</th><th>Duration</th><th>Work Items</th><th>Token Savings</th></tr>").append(LINE_SEPARATOR);
        sb.append("      </thead>").append(LINE_SEPARATOR);
        sb.append("      <tbody>").append(LINE_SEPARATOR);

        for (PatternResult result : report.getResults()) {
            String statusClass = result.isSuccess() ? "status-pass" : "status-fail";
            String statusText = result.isSuccess() ? "PASS" : "FAIL";
            String patternName = result.getPatternInfo() != null ?
                escapeHtml(result.getPatternInfo().name()) : escapeHtml(result.getPatternId());

            sb.append("        <tr>").append(LINE_SEPARATOR);
            sb.append("          <td class=\"").append(statusClass).append("\">").append(statusText).append("</td>").append(LINE_SEPARATOR);
            sb.append("          <td><code>").append(patternName).append("</code></td>").append(LINE_SEPARATOR);
            sb.append("          <td>").append(result.getFormattedDuration()).append("</td>").append(LINE_SEPARATOR);

            if (result.getMetrics() != null) {
                sb.append("          <td>").append(result.getMetrics().getWorkItemCount()).append("</td>").append(LINE_SEPARATOR);
            } else {
                sb.append("          <td>-</td>").append(LINE_SEPARATOR);
            }

            if (result.getTokenAnalysis() != null) {
                sb.append("          <td>").append(String.format("%.0f%%", result.getTokenAnalysis().getSavingsPercentage())).append("</td>").append(LINE_SEPARATOR);
            } else {
                sb.append("          <td>-</td>").append(LINE_SEPARATOR);
            }

            sb.append("        </tr>").append(LINE_SEPARATOR);
        }

        sb.append("      </tbody>").append(LINE_SEPARATOR);
        sb.append("    </table>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // JavaScript for charts
        sb.append("  <script>").append(LINE_SEPARATOR);
        appendHtmlChartScripts(sb, report);
        sb.append("  </script>").append(LINE_SEPARATOR);

        // Footer
        sb.append("  <footer>").append(LINE_SEPARATOR);
        sb.append("    <p>YAWL Foundation - Yet Another Workflow Language v6.0.0</p>").append(LINE_SEPARATOR);
        sb.append("  </footer>").append(LINE_SEPARATOR);

        sb.append("</body>").append(LINE_SEPARATOR);
        sb.append("</html>").append(LINE_SEPARATOR);

        return sb.toString();
    }

    private void appendHtmlStyles(StringBuilder sb) {
        sb.append("    :root {").append(LINE_SEPARATOR);
        sb.append("      --primary-color: #2563eb;").append(LINE_SEPARATOR);
        sb.append("      --success-color: #10b981;").append(LINE_SEPARATOR);
        sb.append("      --failure-color: #ef4444;").append(LINE_SEPARATOR);
        sb.append("      --warning-color: #f59e0b;").append(LINE_SEPARATOR);
        sb.append("      --background: #f8fafc;").append(LINE_SEPARATOR);
        sb.append("      --card-background: #ffffff;").append(LINE_SEPARATOR);
        sb.append("      --text-primary: #1e293b;").append(LINE_SEPARATOR);
        sb.append("      --text-secondary: #64748b;").append(LINE_SEPARATOR);
        sb.append("      --border-color: #e2e8f0;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    * { margin: 0; padding: 0; box-sizing: border-box; }").append(LINE_SEPARATOR);
        sb.append("    body {").append(LINE_SEPARATOR);
        sb.append("      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;").append(LINE_SEPARATOR);
        sb.append("      background: var(--background);").append(LINE_SEPARATOR);
        sb.append("      color: var(--text-primary);").append(LINE_SEPARATOR);
        sb.append("      line-height: 1.6;").append(LINE_SEPARATOR);
        sb.append("      padding: 20px;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    header { text-align: center; margin-bottom: 30px; }").append(LINE_SEPARATOR);
        sb.append("    header h1 { color: var(--primary-color); font-size: 2rem; }").append(LINE_SEPARATOR);
        sb.append("    .generated { color: var(--text-secondary); font-size: 0.9rem; }").append(LINE_SEPARATOR);
        sb.append("    .summary-cards {").append(LINE_SEPARATOR);
        sb.append("      display: grid;").append(LINE_SEPARATOR);
        sb.append("      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));").append(LINE_SEPARATOR);
        sb.append("      gap: 20px;").append(LINE_SEPARATOR);
        sb.append("      margin-bottom: 30px;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    .card {").append(LINE_SEPARATOR);
        sb.append("      background: var(--card-background);").append(LINE_SEPARATOR);
        sb.append("      padding: 20px;").append(LINE_SEPARATOR);
        sb.append("      border-radius: 8px;").append(LINE_SEPARATOR);
        sb.append("      box-shadow: 0 1px 3px rgba(0,0,0,0.1);").append(LINE_SEPARATOR);
        sb.append("      text-align: center;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    .card h3 { font-size: 0.85rem; color: var(--text-secondary); text-transform: uppercase; }").append(LINE_SEPARATOR);
        sb.append("    .card .value { font-size: 2rem; font-weight: 700; margin-top: 5px; }").append(LINE_SEPARATOR);
        sb.append("    .card.success .value { color: var(--success-color); }").append(LINE_SEPARATOR);
        sb.append("    .card.failure .value { color: var(--failure-color); }").append(LINE_SEPARATOR);
        sb.append("    .card.warning .value { color: var(--warning-color); }").append(LINE_SEPARATOR);
        sb.append("    .card.patterns .value { color: var(--primary-color); }").append(LINE_SEPARATOR);
        sb.append("    .charts {").append(LINE_SEPARATOR);
        sb.append("      display: grid;").append(LINE_SEPARATOR);
        sb.append("      grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));").append(LINE_SEPARATOR);
        sb.append("      gap: 20px;").append(LINE_SEPARATOR);
        sb.append("      margin-bottom: 30px;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    .chart-container {").append(LINE_SEPARATOR);
        sb.append("      background: var(--card-background);").append(LINE_SEPARATOR);
        sb.append("      padding: 20px;").append(LINE_SEPARATOR);
        sb.append("      border-radius: 8px;").append(LINE_SEPARATOR);
        sb.append("      box-shadow: 0 1px 3px rgba(0,0,0,0.1);").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    .chart-container.full-width { grid-column: 1 / -1; }").append(LINE_SEPARATOR);
        sb.append("    .chart-container h2 { font-size: 1rem; margin-bottom: 15px; color: var(--text-secondary); }").append(LINE_SEPARATOR);
        sb.append("    section { margin-bottom: 30px; }").append(LINE_SEPARATOR);
        sb.append("    section h2 {").append(LINE_SEPARATOR);
        sb.append("      font-size: 1.25rem;").append(LINE_SEPARATOR);
        sb.append("      margin-bottom: 15px;").append(LINE_SEPARATOR);
        sb.append("      color: var(--text-primary);").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    table {").append(LINE_SEPARATOR);
        sb.append("      width: 100%;").append(LINE_SEPARATOR);
        sb.append("      background: var(--card-background);").append(LINE_SEPARATOR);
        sb.append("      border-radius: 8px;").append(LINE_SEPARATOR);
        sb.append("      box-shadow: 0 1px 3px rgba(0,0,0,0.1);").append(LINE_SEPARATOR);
        sb.append("      border-collapse: collapse;").append(LINE_SEPARATOR);
        sb.append("      overflow: hidden;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    th, td { padding: 12px 15px; text-align: left; }").append(LINE_SEPARATOR);
        sb.append("    th {").append(LINE_SEPARATOR);
        sb.append("      background: var(--primary-color);").append(LINE_SEPARATOR);
        sb.append("      color: white;").append(LINE_SEPARATOR);
        sb.append("      font-weight: 600;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    tr:nth-child(even) { background: #f8fafc; }").append(LINE_SEPARATOR);
        sb.append("    tr:hover { background: #f1f5f9; }").append(LINE_SEPARATOR);
        sb.append("    .status-pass { color: var(--success-color); font-weight: 600; }").append(LINE_SEPARATOR);
        sb.append("    .status-fail { color: var(--failure-color); font-weight: 600; }").append(LINE_SEPARATOR);
        sb.append("    .highlight {").append(LINE_SEPARATOR);
        sb.append("      color: var(--success-color);").append(LINE_SEPARATOR);
        sb.append("      font-weight: 700;").append(LINE_SEPARATOR);
        sb.append("      font-size: 1.1rem;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    code {").append(LINE_SEPARATOR);
        sb.append("      background: #e2e8f0;").append(LINE_SEPARATOR);
        sb.append("      padding: 2px 6px;").append(LINE_SEPARATOR);
        sb.append("      border-radius: 4px;").append(LINE_SEPARATOR);
        sb.append("      font-family: 'SF Mono', Consolas, monospace;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    footer {").append(LINE_SEPARATOR);
        sb.append("      text-align: center;").append(LINE_SEPARATOR);
        sb.append("      padding: 20px;").append(LINE_SEPARATOR);
        sb.append("      color: var(--text-secondary);").append(LINE_SEPARATOR);
        sb.append("      font-size: 0.85rem;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
    }

    private void appendHtmlSummaryCard(StringBuilder sb, String title, String value, String styleClass) {
        sb.append("    <div class=\"card ").append(styleClass).append("\">").append(LINE_SEPARATOR);
        sb.append("      <h3>").append(title).append("</h3>").append(LINE_SEPARATOR);
        sb.append("      <div class=\"value\">").append(value).append("</div>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
    }

    private void appendHtmlChartScripts(StringBuilder sb, YawlPatternDemoReport report) {
        // Results pie chart
        sb.append("    const resultsCtx = document.getElementById('resultsChart');").append(LINE_SEPARATOR);
        sb.append("    new Chart(resultsCtx, {").append(LINE_SEPARATOR);
        sb.append("      type: 'doughnut',").append(LINE_SEPARATOR);
        sb.append("      data: {").append(LINE_SEPARATOR);
        sb.append("        labels: ['Successful', 'Failed'],").append(LINE_SEPARATOR);
        sb.append("        datasets: [{").append(LINE_SEPARATOR);
        sb.append("          data: [").append(report.getSuccessfulPatterns()).append(", ").append(report.getFailedPatterns()).append("],").append(LINE_SEPARATOR);
        sb.append("          backgroundColor: ['#10b981', '#ef4444'],").append(LINE_SEPARATOR);
        sb.append("          borderWidth: 0").append(LINE_SEPARATOR);
        sb.append("        }]").append(LINE_SEPARATOR);
        sb.append("      },").append(LINE_SEPARATOR);
        sb.append("      options: {").append(LINE_SEPARATOR);
        sb.append("        responsive: true,").append(LINE_SEPARATOR);
        sb.append("        plugins: { legend: { position: 'bottom' } }").append(LINE_SEPARATOR);
        sb.append("      }").append(LINE_SEPARATOR);
        sb.append("    });").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        // Token comparison bar chart
        sb.append("    const tokenCtx = document.getElementById('tokenChart');").append(LINE_SEPARATOR);
        sb.append("    new Chart(tokenCtx, {").append(LINE_SEPARATOR);
        sb.append("      type: 'bar',").append(LINE_SEPARATOR);
        sb.append("      data: {").append(LINE_SEPARATOR);
        sb.append("        labels: ['Token Count'],").append(LINE_SEPARATOR);
        sb.append("        datasets: [").append(LINE_SEPARATOR);
        sb.append("          { label: 'YAML', data: [").append(report.getTotalYamlTokens()).append("], backgroundColor: '#3b82f6' },").append(LINE_SEPARATOR);
        sb.append("          { label: 'XML', data: [").append(report.getTotalXmlTokens()).append("], backgroundColor: '#94a3b8' }").append(LINE_SEPARATOR);
        sb.append("        ]").append(LINE_SEPARATOR);
        sb.append("      },").append(LINE_SEPARATOR);
        sb.append("      options: {").append(LINE_SEPARATOR);
        sb.append("        responsive: true,").append(LINE_SEPARATOR);
        sb.append("        plugins: { legend: { position: 'bottom' } }").append(LINE_SEPARATOR);
        sb.append("      }").append(LINE_SEPARATOR);
        sb.append("    });").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        // Category bar chart
        Map<PatternCategory, YawlPatternDemoReport.CategorySummary> categories = report.getSummaryByCategory();

        sb.append("    const categoryCtx = document.getElementById('categoryChart');").append(LINE_SEPARATOR);
        sb.append("    new Chart(categoryCtx, {").append(LINE_SEPARATOR);
        sb.append("      type: 'bar',").append(LINE_SEPARATOR);
        sb.append("      data: {").append(LINE_SEPARATOR);
        sb.append("        labels: [");

        int catCount = 0;
        for (PatternCategory cat : categories.keySet()) {
            if (catCount++ > 0) sb.append(", ");
            sb.append("'").append(escapeJs(cat.getDisplayName())).append("'");
        }

        sb.append("],").append(LINE_SEPARATOR);
        sb.append("        datasets: [{").append(LINE_SEPARATOR);
        sb.append("          label: 'Patterns',").append(LINE_SEPARATOR);
        sb.append("          data: [");

        catCount = 0;
        for (YawlPatternDemoReport.CategorySummary summary : categories.values()) {
            if (catCount++ > 0) sb.append(", ");
            sb.append(summary.getCount());
        }

        sb.append("],").append(LINE_SEPARATOR);
        sb.append("          backgroundColor: '#2563eb'").append(LINE_SEPARATOR);
        sb.append("        }]").append(LINE_SEPARATOR);
        sb.append("      },").append(LINE_SEPARATOR);
        sb.append("      options: {").append(LINE_SEPARATOR);
        sb.append("        responsive: true,").append(LINE_SEPARATOR);
        sb.append("        plugins: { legend: { display: false } },").append(LINE_SEPARATOR);
        sb.append("        scales: { y: { beginAtZero: true } }").append(LINE_SEPARATOR);
        sb.append("      }").append(LINE_SEPARATOR);
        sb.append("    });").append(LINE_SEPARATOR);
    }

    // Helper methods

    private String formatDuration(Duration duration) {
        if (duration.toMinutes() > 0) {
            return String.format("%d min %d sec", duration.toMinutes(), duration.toSecondsPart());
        } else if (duration.toSeconds() > 0) {
            return String.format("%.3f sec", duration.toMillis() / 1000.0);
        } else {
            return String.format("%d ms", duration.toMillis());
        }
    }

    private String formatNumber(long value) {
        return String.format("%,d", value);
    }

    private String truncate(String str, int maxLength) {
        Objects.requireNonNull(str, "String to truncate cannot be null");
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }

    private String indent(int level) {
        return "  ".repeat(Math.max(0, level));
    }

    private void appendJsonProperty(StringBuilder sb, String key, Object value, int indent) {
        appendJsonProperty(sb, key, value, indent, true);
    }

    private void appendJsonProperty(StringBuilder sb, String key, Object value, int indent, boolean comma) {
        sb.append(indent(indent)).append("\"").append(key).append("\": ");

        if (value instanceof String) {
            sb.append("\"").append(value).append("\"");
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else {
            sb.append("\"").append(value).append("\"");
        }

        if (comma) {
            sb.append(",");
        }
        sb.append(LINE_SEPARATOR);
    }

    private String escapeJson(String str) {
        Objects.requireNonNull(str, "String to escape cannot be null");
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String escapeHtml(String str) {
        Objects.requireNonNull(str, "String to escape cannot be null");
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private String escapeJs(String str) {
        Objects.requireNonNull(str, "String to escape cannot be null");
        return str.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
