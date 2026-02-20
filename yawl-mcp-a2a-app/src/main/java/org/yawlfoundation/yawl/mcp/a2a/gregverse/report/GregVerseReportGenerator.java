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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.report;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Multi-format report generator for Greg-Verse simulation results.
 *
 * <p>Generates reports in four output formats:
 * <ul>
 *   <li><b>Console</b> - ANSI colored terminal output with agent tables</li>
 *   <li><b>JSON</b> - Structured JSON for programmatic consumption and APIs</li>
 *   <li><b>Markdown</b> - GitHub-flavored markdown with Mermaid diagrams</li>
 *   <li><b>HTML</b> - Interactive HTML with Chart.js visualizations</li>
 * </ul></p>
 *
 * <p>Report content includes:
 * <ul>
 *   <li>Agent activity timeline with execution results</li>
 *   <li>Skill invocation statistics and performance metrics</li>
 *   <li>Transaction history from the skill marketplace</li>
 *   <li>Collaboration network graph (Mermaid for MD, Chart.js for HTML)</li>
 *   <li>Business outcomes (ideas qualified, revenue, time saved)</li>
 * </ul></p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GregVerseReportGenerator {

    /**
     * ANSI color codes for console output.
     */
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
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
     * Time-only formatter for timeline display.
     */
    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneOffset.UTC);

    /**
     * Create a new report generator.
     */
    public GregVerseReportGenerator() {
        // Default constructor
    }

    /**
     * Generate ANSI colored console output with agent tables.
     *
     * @param report the report to format
     * @return formatted console output string
     */
    public String generateConsole(GregVerseReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(16384);

        // Header
        appendConsoleHeader(sb, report);

        // Summary section
        appendConsoleSummary(sb, report);

        // Agent results table
        appendConsoleAgentResults(sb, report);

        // Skill invocations
        appendConsoleSkillInvocations(sb, report);

        // Transaction history
        appendConsoleTransactions(sb, report);

        // Business outcomes
        appendConsoleBusinessOutcomes(sb, report);

        // Token analysis
        appendConsoleTokenAnalysis(sb, report);

        // Failures section (if any)
        if (report.getFailedAgents() > 0) {
            appendConsoleFailures(sb, report);
        }

        // Footer
        appendConsoleFooter(sb, report);

        return sb.toString();
    }

    private void appendConsoleHeader(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append(ANSI_CYAN);
        sb.append("======================================================================").append(LINE_SEPARATOR);
        sb.append("  GREG-VERSE SIMULATION REPORT").append(LINE_SEPARATOR);
        sb.append("  Scenario: ").append(report.scenarioId()).append(LINE_SEPARATOR);
        sb.append("  Generated: ").append(ISO_FORMATTER.format(report.generatedAt())).append(LINE_SEPARATOR);
        sb.append("======================================================================").append(LINE_SEPARATOR);
        sb.append(ANSI_RESET).append(LINE_SEPARATOR);
    }

    private void appendConsoleSummary(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append("EXECUTION SUMMARY").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        sb.append(String.format("  %-20s %d", "Total Agents:", report.agentResults().size())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s%d%s",
            "Successful:",
            ANSI_GREEN, report.getSuccessfulAgents(), ANSI_RESET)).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s%d%s",
            "Failed:",
            report.getFailedAgents() > 0 ? ANSI_RED : ANSI_GRAY,
            report.getFailedAgents(),
            ANSI_RESET)).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %d", "Interactions:", report.getTotalInteractions())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %d", "Transactions:", report.getTotalTransactions())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s", "Total Duration:", formatDuration(report.totalDuration()))).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s%.2f%s credits", "Total Revenue:", ANSI_GREEN, report.getTotalRevenue(), ANSI_RESET)).append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleAgentResults(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append("AGENT RESULTS").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        sb.append(String.format("  %-20s %-8s %10s %6s",
            "Agent", "Status", "Duration", "Skills")).append(LINE_SEPARATOR);
        sb.append("  ").append("-".repeat(50)).append(LINE_SEPARATOR);

        List<GregVerseReport.AgentResult> sortedResults = report.agentResults().stream()
            .sorted(Comparator.comparing(GregVerseReport.AgentResult::agentId))
            .toList();

        for (GregVerseReport.AgentResult result : sortedResults) {
            String statusIcon = result.success() ?
                ANSI_GREEN + "OK" + ANSI_RESET :
                ANSI_RED + "FAIL" + ANSI_RESET;

            sb.append(String.format("  %s%-20s%s %-8s %10s %6d",
                result.success() ? "" : ANSI_YELLOW,
                truncate(result.displayName(), 20),
                result.success() ? "" : ANSI_RESET,
                statusIcon,
                formatDuration(result.duration()),
                result.getSkillCount())).append(LINE_SEPARATOR);
        }

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleSkillInvocations(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append("SKILL INVOCATIONS").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        // Aggregate skill statistics
        Map<String, SkillStats> skillStats = aggregateSkillStats(report);

        if (skillStats.isEmpty()) {
            sb.append("  No skill invocations recorded").append(LINE_SEPARATOR);
        } else {
            sb.append(String.format("  %-24s %6s %6s %10s",
                "Skill", "Calls", "Fail", "Avg Time")).append(LINE_SEPARATOR);
            sb.append("  ").append("-".repeat(52)).append(LINE_SEPARATOR);

            skillStats.entrySet().stream()
                .sorted(Map.Entry.<String, SkillStats>comparingByValue(
                    Comparator.comparingInt(SkillStats::count).reversed()))
                .forEach(entry -> {
                    String skillName = truncate(entry.getKey(), 24);
                    SkillStats stats = entry.getValue();

                    sb.append(String.format("  %-24s %6d %6d %10s",
                        skillName,
                        stats.count(),
                        stats.failures(),
                        formatDuration(stats.avgDuration()))).append(LINE_SEPARATOR);
                });
        }

        sb.append(LINE_SEPARATOR);
    }

    private Map<String, SkillStats> aggregateSkillStats(GregVerseReport report) {
        Map<String, SkillStats> stats = new HashMap<>();

        for (GregVerseReport.AgentResult agent : report.agentResults()) {
            if (agent.skillInvocations() == null) continue;

            for (GregVerseReport.SkillInvocation invocation : agent.skillInvocations()) {
                String skillName = invocation.skillName();
                SkillStats existing = stats.getOrDefault(skillName,
                    new SkillStats(skillName, 0, 0, Duration.ZERO));

                int newCount = existing.count() + 1;
                int newFailures = existing.failures() + (invocation.success() ? 0 : 1);
                Duration newTotal = existing.totalDuration().plus(invocation.duration());

                stats.put(skillName, new SkillStats(skillName, newCount, newFailures, newTotal));
            }
        }

        return stats;
    }

    private void appendConsoleTransactions(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append("TRANSACTION HISTORY").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        if (report.transactions().isEmpty()) {
            sb.append("  No transactions recorded").append(LINE_SEPARATOR);
        } else {
            sb.append(String.format("  %-16s %-16s %-16s %8s",
                "Buyer", "Seller", "Skill", "Price")).append(LINE_SEPARATOR);
            sb.append("  ").append("-".repeat(60)).append(LINE_SEPARATOR);

            for (GregVerseReport.SkillTransaction tx : report.transactions()) {
                String statusIcon = tx.success() ?
                    ANSI_GREEN + "[OK]" + ANSI_RESET :
                    ANSI_RED + "[FAIL]" + ANSI_RESET;

                sb.append(String.format("  %-16s %-16s %-16s %s%7.1f%s %s",
                    truncate(tx.buyerAgentId(), 16),
                    truncate(tx.sellerAgentId(), 16),
                    truncate(tx.skillName(), 16),
                    ANSI_CYAN, tx.price(), ANSI_RESET,
                    statusIcon)).append(LINE_SEPARATOR);
            }

            sb.append(LINE_SEPARATOR);
            sb.append(String.format("  %52s %s%.2f%s credits",
                "Total Revenue:",
                ANSI_GREEN, report.getTotalRevenue(), ANSI_RESET)).append(LINE_SEPARATOR);
        }

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleBusinessOutcomes(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append("BUSINESS OUTCOMES").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        GregVerseReport.BusinessOutcomes outcomes = report.businessOutcomes();
        if (outcomes == null) {
            outcomes = GregVerseReport.BusinessOutcomes.empty();
        }

        sb.append(String.format("  %-20s %d", "Ideas Qualified:", outcomes.ideasQualified())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %d", "MVPs Built:", outcomes.mvpsBuilt())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %d", "Skills Created:", outcomes.skillsCreated())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s%.2f%s credits",
            "Revenue Generated:",
            ANSI_GREEN, outcomes.revenueGenerated(), ANSI_RESET)).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %d", "Partnerships:", outcomes.partnerships())).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s%.1f hours%s",
            "Time Saved:",
            ANSI_CYAN, outcomes.timeSaved(), ANSI_RESET)).append(LINE_SEPARATOR);

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleTokenAnalysis(StringBuilder sb, GregVerseReport report) {
        GregVerseReport.TokenAnalysis tokenAnalysis = report.tokenAnalysis();
        if (tokenAnalysis == null || (tokenAnalysis.yamlTokens() == 0 && tokenAnalysis.xmlTokens() == 0)) {
            return;
        }

        sb.append(ANSI_BOLD).append("TOKEN ANALYSIS (YAML vs XML)").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        sb.append(String.format("  %-20s %s", "YAML Tokens:", formatNumber(tokenAnalysis.yamlTokens()))).append(LINE_SEPARATOR);
        sb.append(String.format("  %-20s %s", "XML Tokens:", formatNumber(tokenAnalysis.xmlTokens()))).append(LINE_SEPARATOR);

        double savings = tokenAnalysis.getSavingsPercentage();
        String savingsColor = savings >= 70 ? ANSI_GREEN :
            (savings >= 50 ? ANSI_YELLOW : ANSI_GRAY);
        sb.append(String.format("  %-20s %s%.1f%%%s",
            "Savings:",
            savingsColor, savings, ANSI_RESET)).append(LINE_SEPARATOR);

        sb.append(String.format("  %-20s %.2fx", "Compression:", tokenAnalysis.getCompressionRatio())).append(LINE_SEPARATOR);

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleFailures(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_BOLD).append(ANSI_RED).append("FAILURES").append(ANSI_RESET).append(LINE_SEPARATOR);
        sb.append("----------------------------------------").append(LINE_SEPARATOR);

        for (GregVerseReport.AgentResult failure : report.agentResults().stream()
                .filter(r -> !r.success())
                .toList()) {
            sb.append(String.format("  %s: %s",
                failure.displayName(),
                failure.error() != null ? failure.error() : "Unknown error")).append(LINE_SEPARATOR);
        }

        sb.append(LINE_SEPARATOR);
    }

    private void appendConsoleFooter(StringBuilder sb, GregVerseReport report) {
        sb.append(ANSI_GRAY);
        sb.append("----------------------------------------------------------------------").append(LINE_SEPARATOR);
        sb.append("  YAWL Foundation - Greg-Verse Multi-Agent Simulation v6.0.0").append(LINE_SEPARATOR);
        sb.append("  Report generated at: ").append(HUMAN_FORMATTER.format(report.generatedAt())).append(LINE_SEPARATOR);
        sb.append(ANSI_RESET);
    }

    /**
     * Generate structured JSON output for APIs.
     *
     * @param report the report to format
     * @return JSON formatted string
     */
    public String generateJson(GregVerseReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(32768);

        sb.append("{").append(LINE_SEPARATOR);

        // Metadata
        appendJsonProperty(sb, "scenarioId", escapeJson(report.scenarioId()), 1);
        appendJsonProperty(sb, "generatedAt", ISO_FORMATTER.format(report.generatedAt()), 1);
        appendJsonProperty(sb, "version", "6.0.0", 1, false);
        sb.append(",").append(LINE_SEPARATOR);

        // Summary
        sb.append(indent(1)).append("\"summary\": {").append(LINE_SEPARATOR);
        appendJsonProperty(sb, "totalAgents", report.agentResults().size(), 2);
        appendJsonProperty(sb, "successfulAgents", report.getSuccessfulAgents(), 2);
        appendJsonProperty(sb, "failedAgents", report.getFailedAgents(), 2);
        appendJsonProperty(sb, "totalInteractions", report.getTotalInteractions(), 2);
        appendJsonProperty(sb, "totalTransactions", report.getTotalTransactions(), 2);
        appendJsonProperty(sb, "totalRevenue", report.getTotalRevenue(), 2);
        appendJsonProperty(sb, "totalDurationMs", report.totalDuration().toMillis(), 2, false);
        sb.append(LINE_SEPARATOR).append(indent(1)).append("},").append(LINE_SEPARATOR);

        // Agent results
        sb.append(indent(1)).append("\"agents\": [").append(LINE_SEPARATOR);
        appendJsonAgents(sb, report);
        sb.append(indent(1)).append("],").append(LINE_SEPARATOR);

        // Interactions
        sb.append(indent(1)).append("\"interactions\": [").append(LINE_SEPARATOR);
        appendJsonInteractions(sb, report);
        sb.append(indent(1)).append("],").append(LINE_SEPARATOR);

        // Transactions
        sb.append(indent(1)).append("\"transactions\": [").append(LINE_SEPARATOR);
        appendJsonTransactions(sb, report);
        sb.append(indent(1)).append("],").append(LINE_SEPARATOR);

        // Network graph
        sb.append(indent(1)).append("\"networkGraph\": {").append(LINE_SEPARATOR);
        appendJsonNetworkGraph(sb, report);
        sb.append(LINE_SEPARATOR).append(indent(1)).append("},").append(LINE_SEPARATOR);

        // Business outcomes
        sb.append(indent(1)).append("\"businessOutcomes\": {").append(LINE_SEPARATOR);
        appendJsonBusinessOutcomes(sb, report);
        sb.append(LINE_SEPARATOR).append(indent(1)).append("},").append(LINE_SEPARATOR);

        // Token analysis
        sb.append(indent(1)).append("\"tokenAnalysis\": {").append(LINE_SEPARATOR);
        appendJsonTokenAnalysis(sb, report);
        sb.append(LINE_SEPARATOR).append(indent(1)).append("}").append(LINE_SEPARATOR);

        sb.append("}").append(LINE_SEPARATOR);

        return sb.toString();
    }

    private void appendJsonAgents(StringBuilder sb, GregVerseReport report) {
        List<GregVerseReport.AgentResult> agents = report.agentResults();

        for (int i = 0; i < agents.size(); i++) {
            if (i > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            GregVerseReport.AgentResult agent = agents.get(i);

            sb.append(indent(2)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "agentId", escapeJson(agent.agentId()), 3);
            appendJsonProperty(sb, "displayName", escapeJson(agent.displayName()), 3);
            appendJsonProperty(sb, "success", agent.success(), 3);
            appendJsonProperty(sb, "startTime", ISO_FORMATTER.format(agent.startTime()), 3);
            appendJsonProperty(sb, "endTime", ISO_FORMATTER.format(agent.endTime()), 3);
            appendJsonProperty(sb, "durationMs", agent.duration().toMillis(), 3);

            if (agent.skillInvocations() != null && !agent.skillInvocations().isEmpty()) {
                sb.append(",").append(LINE_SEPARATOR);
                sb.append(indent(3)).append("\"skillInvocations\": [").append(LINE_SEPARATOR);

                for (int j = 0; j < agent.skillInvocations().size(); j++) {
                    if (j > 0) {
                        sb.append(",").append(LINE_SEPARATOR);
                    }

                    GregVerseReport.SkillInvocation inv = agent.skillInvocations().get(j);
                    sb.append(indent(4)).append("{").append(LINE_SEPARATOR);
                    appendJsonProperty(sb, "skillId", escapeJson(inv.skillId()), 5);
                    appendJsonProperty(sb, "skillName", escapeJson(inv.skillName()), 5);
                    appendJsonProperty(sb, "durationMs", inv.duration().toMillis(), 5);
                    appendJsonProperty(sb, "success", inv.success(), 5, false);
                    sb.append(LINE_SEPARATOR).append(indent(4)).append("}");
                }

                sb.append(LINE_SEPARATOR).append(indent(3)).append("]").append(LINE_SEPARATOR);
            } else {
                sb.append(LINE_SEPARATOR);
            }

            if (!agent.success() && agent.error() != null) {
                sb.append(",").append(LINE_SEPARATOR);
                appendJsonProperty(sb, "error", escapeJson(agent.error()), 3, false);
                sb.append(LINE_SEPARATOR);
            }

            sb.append(indent(2)).append("}");
        }
        sb.append(LINE_SEPARATOR);
    }

    private void appendJsonInteractions(StringBuilder sb, GregVerseReport report) {
        List<GregVerseReport.AgentInteraction> interactions = report.interactions();

        for (int i = 0; i < interactions.size(); i++) {
            if (i > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            GregVerseReport.AgentInteraction interaction = interactions.get(i);

            sb.append(indent(2)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "fromAgent", escapeJson(interaction.fromAgent()), 3);
            appendJsonProperty(sb, "toAgent", escapeJson(interaction.toAgent()), 3);
            appendJsonProperty(sb, "type", interaction.interactionType().name(), 3);
            appendJsonProperty(sb, "timestamp", ISO_FORMATTER.format(interaction.timestamp()), 3);
            appendJsonProperty(sb, "content", escapeJson(interaction.content()), 3, false);
            sb.append(LINE_SEPARATOR).append(indent(2)).append("}");
        }
        sb.append(LINE_SEPARATOR);
    }

    private void appendJsonTransactions(StringBuilder sb, GregVerseReport report) {
        List<GregVerseReport.SkillTransaction> transactions = report.transactions();

        for (int i = 0; i < transactions.size(); i++) {
            if (i > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            GregVerseReport.SkillTransaction tx = transactions.get(i);

            sb.append(indent(2)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "transactionId", escapeJson(tx.transactionId()), 3);
            appendJsonProperty(sb, "buyerAgentId", escapeJson(tx.buyerAgentId()), 3);
            appendJsonProperty(sb, "sellerAgentId", escapeJson(tx.sellerAgentId()), 3);
            appendJsonProperty(sb, "skillId", escapeJson(tx.skillId()), 3);
            appendJsonProperty(sb, "skillName", escapeJson(tx.skillName()), 3);
            appendJsonProperty(sb, "price", tx.price(), 3);
            appendJsonProperty(sb, "timestamp", ISO_FORMATTER.format(tx.timestamp()), 3);
            appendJsonProperty(sb, "success", tx.success(), 3, false);
            sb.append(LINE_SEPARATOR).append(indent(2)).append("}");
        }
        sb.append(LINE_SEPARATOR);
    }

    private void appendJsonNetworkGraph(StringBuilder sb, GregVerseReport report) {
        GregVerseReport.CollaborationNetwork network = report.networkGraph();
        if (network == null) {
            return;
        }

        // Nodes
        sb.append(indent(2)).append("\"nodes\": [").append(LINE_SEPARATOR);

        List<GregVerseReport.NetworkNode> nodes = network.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            GregVerseReport.NetworkNode node = nodes.get(i);
            sb.append(indent(3)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "id", escapeJson(node.id()), 4);
            appendJsonProperty(sb, "label", escapeJson(node.label()), 4);
            appendJsonProperty(sb, "interactionCount", node.interactionCount(), 4, false);
            sb.append(LINE_SEPARATOR).append(indent(3)).append("}");
        }
        sb.append(LINE_SEPARATOR).append(indent(2)).append("],").append(LINE_SEPARATOR);

        // Edges
        sb.append(indent(2)).append("\"edges\": [").append(LINE_SEPARATOR);

        List<GregVerseReport.NetworkEdge> edges = network.edges();
        for (int i = 0; i < edges.size(); i++) {
            if (i > 0) {
                sb.append(",").append(LINE_SEPARATOR);
            }

            GregVerseReport.NetworkEdge edge = edges.get(i);
            sb.append(indent(3)).append("{").append(LINE_SEPARATOR);
            appendJsonProperty(sb, "source", escapeJson(edge.source()), 4);
            appendJsonProperty(sb, "target", escapeJson(edge.target()), 4);
            appendJsonProperty(sb, "weight", edge.weight(), 4);
            appendJsonProperty(sb, "type", escapeJson(edge.type()), 4, false);
            sb.append(LINE_SEPARATOR).append(indent(3)).append("}");
        }
        sb.append(LINE_SEPARATOR).append(indent(2)).append("]");
    }

    private void appendJsonBusinessOutcomes(StringBuilder sb, GregVerseReport report) {
        GregVerseReport.BusinessOutcomes outcomes = report.businessOutcomes();
        if (outcomes == null) {
            outcomes = GregVerseReport.BusinessOutcomes.empty();
        }

        appendJsonProperty(sb, "ideasQualified", outcomes.ideasQualified(), 2);
        appendJsonProperty(sb, "mvpsBuilt", outcomes.mvpsBuilt(), 2);
        appendJsonProperty(sb, "skillsCreated", outcomes.skillsCreated(), 2);
        appendJsonProperty(sb, "revenueGenerated", outcomes.revenueGenerated(), 2);
        appendJsonProperty(sb, "partnerships", outcomes.partnerships(), 2);
        appendJsonProperty(sb, "timeSavedHours", outcomes.timeSaved(), 2, false);
    }

    private void appendJsonTokenAnalysis(StringBuilder sb, GregVerseReport report) {
        GregVerseReport.TokenAnalysis tokenAnalysis = report.tokenAnalysis();
        if (tokenAnalysis == null) {
            tokenAnalysis = new GregVerseReport.TokenAnalysis(0, 0);
        }

        appendJsonProperty(sb, "yamlTokens", tokenAnalysis.yamlTokens(), 2);
        appendJsonProperty(sb, "xmlTokens", tokenAnalysis.xmlTokens(), 2);
        appendJsonProperty(sb, "savingsPercent", tokenAnalysis.getSavingsPercentage(), 2);
        appendJsonProperty(sb, "compressionRatio", tokenAnalysis.getCompressionRatio(), 2, false);
    }

    /**
     * Generate GitHub-flavored markdown output with Mermaid diagrams.
     *
     * @param report the report to format
     * @return markdown formatted string
     */
    public String generateMarkdown(GregVerseReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(32768);

        // Title
        sb.append("# Greg-Verse Simulation Report").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("> **Scenario:** `").append(report.scenarioId()).append("`").append(LINE_SEPARATOR);
        sb.append("> **Generated:** `").append(ISO_FORMATTER.format(report.generatedAt())).append("`").append(LINE_SEPARATOR);
        sb.append("> **Duration:** ").append(formatDuration(report.totalDuration())).append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        // Summary section
        sb.append("## Summary").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("| Metric | Value |").append(LINE_SEPARATOR);
        sb.append("|--------|-------|").append(LINE_SEPARATOR);
        sb.append("| Total Agents | ").append(report.agentResults().size()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Successful | **").append(report.getSuccessfulAgents()).append("** |").append(LINE_SEPARATOR);
        sb.append("| Failed | ").append(report.getFailedAgents()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Interactions | ").append(report.getTotalInteractions()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Transactions | ").append(report.getTotalTransactions()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Total Revenue | **").append(String.format("%.2f credits", report.getTotalRevenue())).append("** |").append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);

        // Agent Results
        sb.append("## Agent Results").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        sb.append("| Status | Agent | Duration | Skills Invoked |").append(LINE_SEPARATOR);
        sb.append("|--------|-------|----------|----------------|").append(LINE_SEPARATOR);

        for (GregVerseReport.AgentResult result : report.agentResults()) {
            String status = result.success() ? ":white_check_mark: PASS" : ":x: FAIL";

            sb.append("| ").append(status);
            sb.append(" | `").append(result.displayName()).append("`");
            sb.append(" | ").append(formatDuration(result.duration()));
            sb.append(" | ").append(result.getSkillCount());
            sb.append(" |").append(LINE_SEPARATOR);
        }

        sb.append(LINE_SEPARATOR);

        // Skill Invocations
        sb.append("## Skill Invocation Statistics").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        Map<String, SkillStats> skillStats = aggregateSkillStats(report);

        if (skillStats.isEmpty()) {
            sb.append("*No skill invocations recorded.*").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        } else {
            sb.append("| Skill | Calls | Failures | Avg Duration |").append(LINE_SEPARATOR);
            sb.append("|-------|-------|----------|--------------|").append(LINE_SEPARATOR);

            skillStats.entrySet().stream()
                .sorted(Map.Entry.<String, SkillStats>comparingByValue(
                    Comparator.comparingInt(SkillStats::count).reversed()))
                .forEach(entry -> {
                    SkillStats stats = entry.getValue();
                    sb.append("| `").append(stats.name()).append("`");
                    sb.append(" | ").append(stats.count());
                    sb.append(" | ").append(stats.failures());
                    sb.append(" | ").append(formatDuration(stats.avgDuration()));
                    sb.append(" |").append(LINE_SEPARATOR);
                });

            sb.append(LINE_SEPARATOR);
        }

        // Transactions
        if (!report.transactions().isEmpty()) {
            sb.append("## Transaction History").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

            sb.append("| Buyer | Seller | Skill | Price | Status |").append(LINE_SEPARATOR);
            sb.append("|-------|--------|-------|-------|--------|").append(LINE_SEPARATOR);

            for (GregVerseReport.SkillTransaction tx : report.transactions()) {
                String status = tx.success() ? ":white_check_mark:" : ":x:";

                sb.append("| `").append(truncate(tx.buyerAgentId(), 16)).append("`");
                sb.append(" | `").append(truncate(tx.sellerAgentId(), 16)).append("`");
                sb.append(" | `").append(truncate(tx.skillName(), 20)).append("`");
                sb.append(" | ").append(String.format("%.2f", tx.price()));
                sb.append(" | ").append(status);
                sb.append(" |").append(LINE_SEPARATOR);
            }

            sb.append(LINE_SEPARATOR);
            sb.append("**Total Revenue:** ").append(String.format("%.2f credits", report.getTotalRevenue())).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }

        // Collaboration Network (Mermaid diagram)
        sb.append("## Collaboration Network").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        GregVerseReport.CollaborationNetwork network = report.networkGraph();
        if (network != null && !network.nodes().isEmpty()) {
            sb.append("```mermaid").append(LINE_SEPARATOR);
            sb.append("graph LR").append(LINE_SEPARATOR);

            // Add nodes with interaction counts
            for (GregVerseReport.NetworkNode node : network.nodes()) {
                sb.append("    ").append(sanitizeMermaidId(node.id()))
                  .append("[\"").append(node.label())
                  .append("<br/>").append(node.interactionCount()).append(" interactions\"]")
                  .append(LINE_SEPARATOR);
            }

            // Add edges
            for (GregVerseReport.NetworkEdge edge : network.edges()) {
                sb.append("    ").append(sanitizeMermaidId(edge.source()))
                  .append(" -->|").append(edge.weight()).append("| ")
                  .append(sanitizeMermaidId(edge.target()))
                  .append(LINE_SEPARATOR);
            }

            sb.append("```").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        } else {
            sb.append("*No collaboration network data available.*").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }

        // Business Outcomes
        sb.append("## Business Outcomes").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        GregVerseReport.BusinessOutcomes outcomes = report.businessOutcomes();
        if (outcomes == null) {
            outcomes = GregVerseReport.BusinessOutcomes.empty();
        }

        sb.append("| Outcome | Value |").append(LINE_SEPARATOR);
        sb.append("|---------|-------|").append(LINE_SEPARATOR);
        sb.append("| Ideas Qualified | **").append(outcomes.ideasQualified()).append("** |").append(LINE_SEPARATOR);
        sb.append("| MVPs Built | **").append(outcomes.mvpsBuilt()).append("** |").append(LINE_SEPARATOR);
        sb.append("| Skills Created | ").append(outcomes.skillsCreated()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Revenue Generated | **").append(String.format("%.2f credits", outcomes.revenueGenerated())).append("** |").append(LINE_SEPARATOR);
        sb.append("| Partnerships | ").append(outcomes.partnerships()).append(" |").append(LINE_SEPARATOR);
        sb.append("| Time Saved | **").append(String.format("%.1f hours", outcomes.timeSaved())).append("** |").append(LINE_SEPARATOR);
        sb.append(LINE_SEPARATOR);

        // Token Analysis
        GregVerseReport.TokenAnalysis tokenAnalysis = report.tokenAnalysis();
        if (tokenAnalysis != null && (tokenAnalysis.yamlTokens() > 0 || tokenAnalysis.xmlTokens() > 0)) {
            sb.append("## Token Analysis (YAML vs XML)").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

            sb.append("| Metric | Value |").append(LINE_SEPARATOR);
            sb.append("|--------|-------|").append(LINE_SEPARATOR);
            sb.append("| YAML Tokens | ").append(formatNumber(tokenAnalysis.yamlTokens())).append(" |").append(LINE_SEPARATOR);
            sb.append("| XML Tokens | ").append(formatNumber(tokenAnalysis.xmlTokens())).append(" |").append(LINE_SEPARATOR);
            sb.append("| Savings | **").append(String.format("%.1f%%", tokenAnalysis.getSavingsPercentage())).append("** |").append(LINE_SEPARATOR);
            sb.append("| Compression Ratio | ").append(String.format("%.2fx", tokenAnalysis.getCompressionRatio())).append(" |").append(LINE_SEPARATOR);
            sb.append(LINE_SEPARATOR);
        }

        // Failures section
        if (report.getFailedAgents() > 0) {
            sb.append("## Failures").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

            sb.append("```").append(LINE_SEPARATOR);
            for (GregVerseReport.AgentResult failure : report.agentResults().stream()
                    .filter(r -> !r.success())
                    .toList()) {
                sb.append(failure.displayName()).append(": ");
                sb.append(failure.error() != null ? failure.error() : "Unknown error");
                sb.append(LINE_SEPARATOR);
            }
            sb.append("```").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }

        // Footer
        sb.append("---").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        sb.append("*YAWL Foundation - Greg-Verse Multi-Agent Simulation v6.0.0*").append(LINE_SEPARATOR);

        return sb.toString();
    }

    /**
     * Generate interactive HTML with Chart.js visualizations.
     *
     * @param report the report to format
     * @return HTML formatted string
     */
    public String generateHtml(GregVerseReport report) {
        Objects.requireNonNull(report, "Report cannot be null");

        StringBuilder sb = new StringBuilder(65536);

        // HTML header
        sb.append("<!DOCTYPE html>").append(LINE_SEPARATOR);
        sb.append("<html lang=\"en\">").append(LINE_SEPARATOR);
        sb.append("<head>").append(LINE_SEPARATOR);
        sb.append("  <meta charset=\"UTF-8\">").append(LINE_SEPARATOR);
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">").append(LINE_SEPARATOR);
        sb.append("  <title>Greg-Verse Simulation Report</title>").append(LINE_SEPARATOR);
        sb.append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js\"></script>").append(LINE_SEPARATOR);
        sb.append("  <style>").append(LINE_SEPARATOR);
        appendHtmlStyles(sb);
        sb.append("  </style>").append(LINE_SEPARATOR);
        sb.append("</head>").append(LINE_SEPARATOR);
        sb.append("<body>").append(LINE_SEPARATOR);

        // Header
        sb.append("  <header>").append(LINE_SEPARATOR);
        sb.append("    <h1>Greg-Verse Simulation Report</h1>").append(LINE_SEPARATOR);
        sb.append("    <p class=\"scenario\">Scenario: <code>").append(escapeHtml(report.scenarioId())).append("</code></p>").append(LINE_SEPARATOR);
        sb.append("    <p class=\"generated\">Generated: ").append(ISO_FORMATTER.format(report.generatedAt())).append("</p>").append(LINE_SEPARATOR);
        sb.append("  </header>").append(LINE_SEPARATOR);

        // Summary cards
        sb.append("  <section class=\"summary-cards\">").append(LINE_SEPARATOR);
        appendHtmlSummaryCard(sb, "Total Agents", String.valueOf(report.agentResults().size()), "agents");
        appendHtmlSummaryCard(sb, "Successful", String.valueOf(report.getSuccessfulAgents()), "success");
        appendHtmlSummaryCard(sb, "Failed", String.valueOf(report.getFailedAgents()), report.getFailedAgents() > 0 ? "failure" : "success");
        appendHtmlSummaryCard(sb, "Interactions", String.valueOf(report.getTotalInteractions()), "info");
        appendHtmlSummaryCard(sb, "Revenue", String.format("%.2f cr", report.getTotalRevenue()), "revenue");
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Charts section
        sb.append("  <section class=\"charts\">").append(LINE_SEPARATOR);
        sb.append("    <div class=\"chart-container\">").append(LINE_SEPARATOR);
        sb.append("      <h2>Agent Success Rate</h2>").append(LINE_SEPARATOR);
        sb.append("      <canvas id=\"successChart\"></canvas>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("    <div class=\"chart-container\">").append(LINE_SEPARATOR);
        sb.append("      <h2>Transaction Revenue</h2>").append(LINE_SEPARATOR);
        sb.append("      <canvas id=\"revenueChart\"></canvas>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("    <div class=\"chart-container full-width\">").append(LINE_SEPARATOR);
        sb.append("      <h2>Collaboration Network</h2>").append(LINE_SEPARATOR);
        sb.append("      <canvas id=\"networkChart\"></canvas>").append(LINE_SEPARATOR);
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Agent results table
        sb.append("  <section class=\"results\">").append(LINE_SEPARATOR);
        sb.append("    <h2>Agent Results</h2>").append(LINE_SEPARATOR);
        sb.append("    <table>").append(LINE_SEPARATOR);
        sb.append("      <thead>").append(LINE_SEPARATOR);
        sb.append("        <tr><th>Status</th><th>Agent</th><th>Duration</th><th>Skills</th><th>Output</th></tr>").append(LINE_SEPARATOR);
        sb.append("      </thead>").append(LINE_SEPARATOR);
        sb.append("      <tbody>").append(LINE_SEPARATOR);

        for (GregVerseReport.AgentResult result : report.agentResults()) {
            String statusClass = result.success() ? "status-pass" : "status-fail";
            String statusText = result.success() ? "PASS" : "FAIL";

            sb.append("        <tr>").append(LINE_SEPARATOR);
            sb.append("          <td class=\"").append(statusClass).append("\">").append(statusText).append("</td>").append(LINE_SEPARATOR);
            sb.append("          <td><code>").append(escapeHtml(result.displayName())).append("</code></td>").append(LINE_SEPARATOR);
            sb.append("          <td>").append(formatDuration(result.duration())).append("</td>").append(LINE_SEPARATOR);
            sb.append("          <td>").append(result.getSkillCount()).append("</td>").append(LINE_SEPARATOR);
            sb.append("          <td>").append(result.output() != null ? escapeHtml(truncate(result.output(), 50)) : "-").append("</td>").append(LINE_SEPARATOR);
            sb.append("        </tr>").append(LINE_SEPARATOR);
        }

        sb.append("      </tbody>").append(LINE_SEPARATOR);
        sb.append("    </table>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Skill invocations table
        sb.append("  <section class=\"skills\">").append(LINE_SEPARATOR);
        sb.append("    <h2>Skill Invocations</h2>").append(LINE_SEPARATOR);
        sb.append("    <table>").append(LINE_SEPARATOR);
        sb.append("      <thead>").append(LINE_SEPARATOR);
        sb.append("        <tr><th>Skill</th><th>Calls</th><th>Failures</th><th>Avg Duration</th></tr>").append(LINE_SEPARATOR);
        sb.append("      </thead>").append(LINE_SEPARATOR);
        sb.append("      <tbody>").append(LINE_SEPARATOR);

        Map<String, SkillStats> skillStats = aggregateSkillStats(report);
        skillStats.entrySet().stream()
            .sorted(Map.Entry.<String, SkillStats>comparingByValue(
                Comparator.comparingInt(SkillStats::count).reversed()))
            .forEach(entry -> {
                SkillStats stats = entry.getValue();
                sb.append("        <tr>").append(LINE_SEPARATOR);
                sb.append("          <td><code>").append(escapeHtml(stats.name())).append("</code></td>").append(LINE_SEPARATOR);
                sb.append("          <td>").append(stats.count()).append("</td>").append(LINE_SEPARATOR);
                sb.append("          <td>").append(stats.failures()).append("</td>").append(LINE_SEPARATOR);
                sb.append("          <td>").append(formatDuration(stats.avgDuration())).append("</td>").append(LINE_SEPARATOR);
                sb.append("        </tr>").append(LINE_SEPARATOR);
            });

        sb.append("      </tbody>").append(LINE_SEPARATOR);
        sb.append("    </table>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // Business outcomes
        GregVerseReport.BusinessOutcomes outcomes = report.businessOutcomes();
        if (outcomes == null) {
            outcomes = GregVerseReport.BusinessOutcomes.empty();
        }

        sb.append("  <section class=\"outcomes\">").append(LINE_SEPARATOR);
        sb.append("    <h2>Business Outcomes</h2>").append(LINE_SEPARATOR);
        sb.append("    <div class=\"outcome-grid\">").append(LINE_SEPARATOR);
        appendHtmlOutcomeCard(sb, "Ideas Qualified", outcomes.ideasQualified());
        appendHtmlOutcomeCard(sb, "MVPs Built", outcomes.mvpsBuilt());
        appendHtmlOutcomeCard(sb, "Skills Created", outcomes.skillsCreated());
        appendHtmlOutcomeCard(sb, "Partnerships", outcomes.partnerships());
        appendHtmlOutcomeCard(sb, "Revenue", String.format("%.2f cr", outcomes.revenueGenerated()), "highlight");
        appendHtmlOutcomeCard(sb, "Time Saved", String.format("%.1f hrs", outcomes.timeSaved()), "highlight");
        sb.append("    </div>").append(LINE_SEPARATOR);
        sb.append("  </section>").append(LINE_SEPARATOR);

        // JavaScript for charts
        sb.append("  <script>").append(LINE_SEPARATOR);
        appendHtmlChartScripts(sb, report);
        sb.append("  </script>").append(LINE_SEPARATOR);

        // Footer
        sb.append("  <footer>").append(LINE_SEPARATOR);
        sb.append("    <p>YAWL Foundation - Greg-Verse Multi-Agent Simulation v6.0.0</p>").append(LINE_SEPARATOR);
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
        sb.append("      --info-color: #06b6d4;").append(LINE_SEPARATOR);
        sb.append("      --revenue-color: #8b5cf6;").append(LINE_SEPARATOR);
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
        sb.append("    .scenario { font-size: 1rem; margin-top: 10px; }").append(LINE_SEPARATOR);
        sb.append("    .generated { color: var(--text-secondary); font-size: 0.9rem; }").append(LINE_SEPARATOR);
        sb.append("    .summary-cards {").append(LINE_SEPARATOR);
        sb.append("      display: grid;").append(LINE_SEPARATOR);
        sb.append("      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));").append(LINE_SEPARATOR);
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
        sb.append("    .card.agents .value { color: var(--primary-color); }").append(LINE_SEPARATOR);
        sb.append("    .card.info .value { color: var(--info-color); }").append(LINE_SEPARATOR);
        sb.append("    .card.revenue .value { color: var(--revenue-color); }").append(LINE_SEPARATOR);
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
        sb.append("    .outcome-grid {").append(LINE_SEPARATOR);
        sb.append("      display: grid;").append(LINE_SEPARATOR);
        sb.append("      grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));").append(LINE_SEPARATOR);
        sb.append("      gap: 15px;").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    .outcome-card {").append(LINE_SEPARATOR);
        sb.append("      background: var(--card-background);").append(LINE_SEPARATOR);
        sb.append("      padding: 15px;").append(LINE_SEPARATOR);
        sb.append("      border-radius: 8px;").append(LINE_SEPARATOR);
        sb.append("      text-align: center;").append(LINE_SEPARATOR);
        sb.append("      box-shadow: 0 1px 3px rgba(0,0,0,0.1);").append(LINE_SEPARATOR);
        sb.append("    }").append(LINE_SEPARATOR);
        sb.append("    .outcome-card .label { font-size: 0.8rem; color: var(--text-secondary); }").append(LINE_SEPARATOR);
        sb.append("    .outcome-card .value { font-size: 1.5rem; font-weight: 700; color: var(--primary-color); margin-top: 5px; }").append(LINE_SEPARATOR);
        sb.append("    .outcome-card.highlight .value { color: var(--success-color); }").append(LINE_SEPARATOR);
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

    private void appendHtmlOutcomeCard(StringBuilder sb, String label, int value) {
        appendHtmlOutcomeCard(sb, label, String.valueOf(value), "");
    }

    private void appendHtmlOutcomeCard(StringBuilder sb, String label, String value, String extraClass) {
        sb.append("      <div class=\"outcome-card ").append(extraClass).append("\">").append(LINE_SEPARATOR);
        sb.append("        <div class=\"label\">").append(label).append("</div>").append(LINE_SEPARATOR);
        sb.append("        <div class=\"value\">").append(value).append("</div>").append(LINE_SEPARATOR);
        sb.append("      </div>").append(LINE_SEPARATOR);
    }

    private void appendHtmlChartScripts(StringBuilder sb, GregVerseReport report) {
        // Success rate doughnut chart
        sb.append("    const successCtx = document.getElementById('successChart');").append(LINE_SEPARATOR);
        sb.append("    new Chart(successCtx, {").append(LINE_SEPARATOR);
        sb.append("      type: 'doughnut',").append(LINE_SEPARATOR);
        sb.append("      data: {").append(LINE_SEPARATOR);
        sb.append("        labels: ['Successful', 'Failed'],").append(LINE_SEPARATOR);
        sb.append("        datasets: [{").append(LINE_SEPARATOR);
        sb.append("          data: [").append(report.getSuccessfulAgents()).append(", ").append(report.getFailedAgents()).append("],").append(LINE_SEPARATOR);
        sb.append("          backgroundColor: ['#10b981', '#ef4444'],").append(LINE_SEPARATOR);
        sb.append("          borderWidth: 0").append(LINE_SEPARATOR);
        sb.append("        }]").append(LINE_SEPARATOR);
        sb.append("      },").append(LINE_SEPARATOR);
        sb.append("      options: {").append(LINE_SEPARATOR);
        sb.append("        responsive: true,").append(LINE_SEPARATOR);
        sb.append("        plugins: { legend: { position: 'bottom' } }").append(LINE_SEPARATOR);
        sb.append("      }").append(LINE_SEPARATOR);
        sb.append("    });").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        // Revenue by transaction bar chart
        sb.append("    const revenueCtx = document.getElementById('revenueChart');").append(LINE_SEPARATOR);
        sb.append("    new Chart(revenueCtx, {").append(LINE_SEPARATOR);
        sb.append("      type: 'bar',").append(LINE_SEPARATOR);
        sb.append("      data: {").append(LINE_SEPARATOR);
        sb.append("        labels: [");

        List<GregVerseReport.SkillTransaction> transactions = report.transactions();
        for (int i = 0; i < Math.min(transactions.size(), 10); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(escapeJs(truncate(transactions.get(i).skillName(), 15))).append("'");
        }

        sb.append("],").append(LINE_SEPARATOR);
        sb.append("        datasets: [{").append(LINE_SEPARATOR);
        sb.append("          label: 'Revenue (credits)',").append(LINE_SEPARATOR);
        sb.append("          data: [");

        for (int i = 0; i < Math.min(transactions.size(), 10); i++) {
            if (i > 0) sb.append(", ");
            sb.append(transactions.get(i).price());
        }

        sb.append("],").append(LINE_SEPARATOR);
        sb.append("          backgroundColor: '#8b5cf6'").append(LINE_SEPARATOR);
        sb.append("        }]").append(LINE_SEPARATOR);
        sb.append("      },").append(LINE_SEPARATOR);
        sb.append("      options: {").append(LINE_SEPARATOR);
        sb.append("        responsive: true,").append(LINE_SEPARATOR);
        sb.append("        plugins: { legend: { display: false } },").append(LINE_SEPARATOR);
        sb.append("        scales: { y: { beginAtZero: true } }").append(LINE_SEPARATOR);
        sb.append("      }").append(LINE_SEPARATOR);
        sb.append("    });").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        // Network chart (bubble chart for collaboration)
        GregVerseReport.CollaborationNetwork network = report.networkGraph();
        if (network != null && !network.nodes().isEmpty()) {
            sb.append("    const networkCtx = document.getElementById('networkChart');").append(LINE_SEPARATOR);
            sb.append("    new Chart(networkCtx, {").append(LINE_SEPARATOR);
            sb.append("      type: 'bubble',").append(LINE_SEPARATOR);
            sb.append("      data: {").append(LINE_SEPARATOR);
            sb.append("        datasets: [{").append(LINE_SEPARATOR);
            sb.append("          label: 'Agent Interactions',").append(LINE_SEPARATOR);
            sb.append("          data: [").append(LINE_SEPARATOR);

            List<GregVerseReport.NetworkNode> nodes = network.nodes();
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) sb.append(",").append(LINE_SEPARATOR);
                GregVerseReport.NetworkNode node = nodes.get(i);
                sb.append("            { x: ").append(i * 10)
                  .append(", y: ").append(node.interactionCount())
                  .append(", r: ").append(Math.max(5, node.interactionCount() * 2))
                  .append(", label: '").append(escapeJs(node.label())).append("' }");
            }

            sb.append(LINE_SEPARATOR).append("          ],").append(LINE_SEPARATOR);
            sb.append("          backgroundColor: 'rgba(37, 99, 235, 0.6)'").append(LINE_SEPARATOR);
            sb.append("        }]").append(LINE_SEPARATOR);
            sb.append("      },").append(LINE_SEPARATOR);
            sb.append("      options: {").append(LINE_SEPARATOR);
            sb.append("        responsive: true,").append(LINE_SEPARATOR);
            sb.append("        plugins: {").append(LINE_SEPARATOR);
            sb.append("          legend: { display: false },").append(LINE_SEPARATOR);
            sb.append("          tooltip: {").append(LINE_SEPARATOR);
            sb.append("            callbacks: {").append(LINE_SEPARATOR);
            sb.append("              label: function(context) {").append(LINE_SEPARATOR);
            sb.append("                return context.raw.label + ': ' + context.raw.y + ' interactions';").append(LINE_SEPARATOR);
            sb.append("              }").append(LINE_SEPARATOR);
            sb.append("            }").append(LINE_SEPARATOR);
            sb.append("          }").append(LINE_SEPARATOR);
            sb.append("        },").append(LINE_SEPARATOR);
            sb.append("        scales: {").append(LINE_SEPARATOR);
            sb.append("          x: { display: false },").append(LINE_SEPARATOR);
            sb.append("          y: { beginAtZero: true, title: { display: true, text: 'Interactions' } }").append(LINE_SEPARATOR);
            sb.append("        }").append(LINE_SEPARATOR);
            sb.append("      }").append(LINE_SEPARATOR);
            sb.append("    });").append(LINE_SEPARATOR);
        }
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

    private String sanitizeMermaidId(String id) {
        Objects.requireNonNull(id, "Mermaid ID cannot be null");
        return id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Internal record for aggregated skill statistics.
     */
    private record SkillStats(String name, int count, int failures, Duration totalDuration) {
        Duration avgDuration() {
            if (count == 0) return Duration.ZERO;
            return totalDuration.dividedBy(count);
        }
    }
}
