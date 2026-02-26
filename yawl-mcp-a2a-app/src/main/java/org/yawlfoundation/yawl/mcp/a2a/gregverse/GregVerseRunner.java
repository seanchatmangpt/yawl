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

package org.yawlfoundation.yawl.mcp.a2a.gregverse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.BlakeAndersonAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.DanRomeroAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.DickieBushAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.GregIsenbergAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.JamesAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.JustinWelshAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.LeoLeojrrAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.agent.impl.NicolasColeAgent;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig.OutputFormat;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseMarketplace;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation.GregVerseSimulation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * CLI entry point for the Greg-Verse multi-agent simulation framework.
 *
 * <p>This class provides a command-line interface for running Greg-Verse
 * multi-agent business advisor simulations. It supports:</p>
 * <ul>
 *   <li>Running predefined business scenarios</li>
 *   <li>Running marketplace simulations</li>
 *   <li>Single agent skill invocation</li>
 *   <li>Multiple output formats (console, JSON, markdown, HTML)</li>
 *   <li>Parallel and sequential execution modes</li>
 * </ul>
 *
 * <h2>Commands</h2>
 * <pre>{@code
 * java -jar gregverse.jar --scenario gvs-1-startup-idea    # Run specific scenario
 * java -jar gregverse.jar --all                            # Run all scenarios
 * java -jar gregverse.jar --marketplace --duration 5m      # Run marketplace simulation
 * java -jar gregverse.jar --agent greg-isenberg --skill market-research --input '{"idea": "AI tool"}'
 * java -jar gregverse.jar --all --format html --output report.html
 * }</pre>
 *
 * <h2>Options</h2>
 * <ul>
 *   <li>{@code --scenario <id>} - Run specific scenario (e.g., gvs-1-startup-idea)</li>
 *   <li>{@code --agents <list>} - Run with specific agents (comma-separated)</li>
 *   <li>{@code --marketplace} - Run marketplace simulation</li>
 *   <li>{@code --duration <time>} - Marketplace duration (5m, 1h)</li>
 *   <li>{@code --agent <id> --skill <id> --input <json>} - Single skill invocation</li>
 *   <li>{@code --all} - Run all scenarios</li>
 *   <li>{@code --format <type>} - Output format (console, json, markdown, html)</li>
 *   <li>{@code --output <path>} - Write to file</li>
 *   <li>{@code --timeout <sec>} - Execution timeout</li>
 *   <li>{@code --parallel/--sequential} - Execution mode</li>
 *   <li>{@code --verbose} - Enable verbose logging</li>
 *   <li>{@code --help} - Show usage</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class GregVerseRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GregVerseRunner.class);
    private static final String VERSION = "6.0.0";

    /**
     * Registry of available Greg-Verse agent suppliers.
     */
    private static final Map<String, AgentFactory> AGENT_FACTORIES = Map.ofEntries(
        Map.entry("greg-isenberg", GregIsenbergAgent::new),
        Map.entry("james", JamesAgent::new),
        Map.entry("nicolas-cole", NicolasColeAgent::new),
        Map.entry("dickie-bush", DickieBushAgent::new),
        Map.entry("leo-leojrr", LeoLeojrrAgent::new),
        Map.entry("justin-welsh", JustinWelshAgent::new),
        Map.entry("dan-romero", DanRomeroAgent::new),
        Map.entry("blake-anderson", BlakeAndersonAgent::new)
    );

    /**
     * Predefined scenario IDs.
     */
    private static final List<String> AVAILABLE_SCENARIOS = List.of(
        "gvs-1-startup-idea",
        "gvs-2-content-business",
        "gvs-3-api-infrastructure",
        "gvs-4-skill-transaction",
        "gvs-5-product-launch"
    );

    private final GregVerseConfig config;

    /**
     * Functional interface for creating agent instances.
     */
    @FunctionalInterface
    private interface AgentFactory {
        GregVerseAgent create() throws Exception;
    }

    /**
     * Main entry point for the Greg-Verse CLI.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        GregVerseConfig config = GregVerseConfig.fromCommandLine(args);

        GregVerseRunner runner = new GregVerseRunner(config);
        int exitCode = runner.run();
        System.exit(exitCode);
    }

    /**
     * Create a new Greg-Verse runner with the specified configuration.
     *
     * @param config the runner configuration
     */
    public GregVerseRunner(GregVerseConfig config) {
        this.config = config;
        configureLogging(config.verbose());
    }

    /**
     * Execute the Greg-Verse simulation and return exit code.
     *
     * @return 0 for success, 1 for failure
     */
    public int run() {
        Instant startTime = Instant.now();

        printHeader();

        try {
            GregVerseReport report;

            if (config.isSelfPlayMode()) {
                report = runSelfPlayDemo();
            } else if (config.isSingleSkillMode()) {
                report = runSingleSkillInvocation();
            } else if (config.marketplaceMode()) {
                report = runMarketplaceSimulation();
            } else if ("all".equals(config.scenarioId())) {
                report = runAllScenarios();
            } else if (config.hasScenario()) {
                report = runSingleScenario();
            } else {
                report = runDefaultSimulation();
            }

            // Self-play mode writes custom JSON via writeDemoResults(); skip generic outputReport.
            if (!config.isSelfPlayMode()) {
                outputReport(report);
            }

            printSummary(report, Duration.between(startTime, Instant.now()));

            return report.getFailedAgents() > 0 ? 1 : 0;

        } catch (Exception e) {
            LOGGER.error("Greg-Verse execution failed", e);
            System.err.println("Execution failed: " + e.getMessage());
            return 1;
        }
    }

    /**
     * Run a single skill invocation on a specific agent.
     *
     * @return the execution report
     */
    private GregVerseReport runSingleSkillInvocation() {
        System.out.printf("Invoking skill '%s' on agent '%s'...%n%n",
            config.singleSkillId(), config.singleAgentId());

        GregVerseSimulation simulation = new GregVerseSimulation(config);
        return simulation.runSingleSkill();
    }

    /**
     * Run the marketplace simulation.
     *
     * @return the execution report
     */
    private GregVerseReport runMarketplaceSimulation() {
        System.out.printf("Starting marketplace simulation for %s...%n%n",
            formatDuration(config.marketplaceDuration()));

        GregVerseMarketplace marketplace = new GregVerseMarketplace();

        List<String> agentIds = new ArrayList<>(AGENT_FACTORIES.keySet());
        if (config.hasAgentFilter()) {
            agentIds = config.agentIds().stream()
                .filter(AGENT_FACTORIES::containsKey)
                .collect(Collectors.toList());
        }

        long intervalMs = Math.max(100, config.marketplaceDuration().toMillis() / 100);

        marketplace.runSimulation(agentIds, intervalMs);

        try {
            config.marketplaceDuration().toMillis();
            Thread.sleep(config.marketplaceDuration().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            marketplace.stopSimulation();
        }

        return buildMarketplaceReport(marketplace);
    }

    /**
     * Run all predefined scenarios.
     *
     * @return combined execution report
     */
    private GregVerseReport runAllScenarios() {
        System.out.printf("Running %d scenarios with %s...%n%n",
            AVAILABLE_SCENARIOS.size(),
            config.parallelExecution() ? "virtual threads" : "sequential execution");

        List<GregVerseReport.AgentResult> allResults = new ArrayList<>();
        List<GregVerseReport.AgentInteraction> allInteractions = new ArrayList<>();
        List<GregVerseReport.SkillTransaction> allTransactions = new ArrayList<>();

        Instant startTime = Instant.now();

        for (String scenarioId : AVAILABLE_SCENARIOS) {
            GregVerseConfig scenarioConfig = new GregVerseConfig(
                scenarioId,
                config.agentIds(),
                config.outputFormat(),
                config.outputPath(),
                config.timeoutSeconds(),
                config.parallelExecution(),
                false,
                config.marketplaceDuration(),
                null, null, null,
                config.enableMetrics(),
                config.verbose(),
                false
            );

            GregVerseSimulation simulation = new GregVerseSimulation(scenarioConfig);
            GregVerseReport report = simulation.run();

            allResults.addAll(report.agentResults());
            allInteractions.addAll(report.interactions());
            allTransactions.addAll(report.transactions());

            printScenarioProgress(scenarioId, report);
        }

        return GregVerseReport.builder()
            .scenarioId("all-scenarios")
            .generatedAt(Instant.now())
            .totalDuration(Duration.between(startTime, Instant.now()))
            .agentResults(allResults)
            .interactions(allInteractions)
            .transactions(allTransactions)
            .build();
    }

    /**
     * Run a single scenario by ID.
     *
     * @return the execution report
     */
    private GregVerseReport runSingleScenario() {
        System.out.printf("Running scenario '%s' with %s...%n%n",
            config.scenarioId(),
            config.parallelExecution() ? "virtual threads" : "sequential execution");

        GregVerseSimulation simulation = new GregVerseSimulation(config);
        return simulation.run();
    }

    /**
     * Run the default simulation (all agents, no specific scenario).
     *
     * @return the execution report
     */
    private GregVerseReport runDefaultSimulation() {
        System.out.printf("Running default simulation with %d agents using %s...%n%n",
            getAvailableAgentCount(),
            config.parallelExecution() ? "virtual threads" : "sequential execution");

        GregVerseSimulation simulation = new GregVerseSimulation(config);
        return simulation.run();
    }

    /**
     * Build a report from marketplace statistics.
     *
     * @param marketplace the marketplace instance
     * @return the execution report
     */
    private GregVerseReport buildMarketplaceReport(GregVerseMarketplace marketplace) {
        Map<String, Object> stats = marketplace.getStatistics();

        List<GregVerseReport.AgentResult> agentResults = new ArrayList<>();
        for (String agentId : AGENT_FACTORIES.keySet()) {
            GregVerseMarketplace.AgentReputation rep = marketplace.getAgentReputation(agentId);
            java.math.BigDecimal balance = marketplace.getAgentBalance(agentId);

            agentResults.add(new GregVerseReport.AgentResult(
                agentId,
                formatAgentName(agentId),
                true,
                Instant.now().minus(config.marketplaceDuration()),
                Instant.now(),
                List.of(),
                String.format("Balance: %s credits, Sales: %d, Purchases: %d, Trust: %d",
                    balance, rep.totalSales(), rep.totalPurchases(), rep.trustScore()),
                null
            ));
        }

        List<GregVerseReport.SkillTransaction> transactions = marketplace.getAgentTransactions(
                AGENT_FACTORIES.keySet().iterator().next()
            ).stream()
            .map(tx -> new GregVerseReport.SkillTransaction(
                tx.transactionId(),
                tx.buyerAgentId(),
                tx.sellerAgentId(),
                tx.skillId(),
                tx.skillId(),
                tx.priceInCredits().doubleValue(),
                tx.completedAt(),
                tx.status() == GregVerseMarketplace.Transaction.TransactionStatus.COMPLETED
            ))
            .collect(Collectors.toList());

        return GregVerseReport.builder()
            .scenarioId("marketplace-simulation")
            .generatedAt(Instant.now())
            .totalDuration(config.marketplaceDuration())
            .agentResults(agentResults)
            .interactions(List.of())
            .transactions(transactions)
            .businessOutcomes(new GregVerseReport.BusinessOutcomes(
                0,
                0,
                (int) stats.get("activeListings"),
                transactions.stream()
                    .filter(GregVerseReport.SkillTransaction::success)
                    .mapToDouble(GregVerseReport.SkillTransaction::price)
                    .sum(),
                AGENT_FACTORIES.size(),
                0.0
            ))
            .build();
    }

    /**
     * Output the report in the configured format.
     *
     * @param report the report to output
     */
    private void outputReport(GregVerseReport report) {
        String output = switch (config.outputFormat()) {
            case JSON -> generateJsonReport(report);
            case MARKDOWN -> generateMarkdownReport(report);
            case HTML -> generateHtmlReport(report);
            default -> generateConsoleReport(report);
        };

        if (config.hasOutputPath()) {
            try {
                Path path = Paths.get(config.outputPath());
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, output, StandardCharsets.UTF_8);
                System.out.println("\nReport saved to: " + config.outputPath());
            } catch (IOException e) {
                LOGGER.error("Failed to write output file", e);
                System.err.println("Failed to write output: " + e.getMessage());
                System.out.println("\n" + output);
            }
        } else if (config.outputFormat() != OutputFormat.CONSOLE) {
            System.out.println("\n" + output);
        }
    }

    /**
     * Generate console output format.
     */
    private String generateConsoleReport(GregVerseReport report) {
        StringBuilder sb = new StringBuilder();

        sb.append("\n=== Agent Results ===\n\n");
        for (GregVerseReport.AgentResult result : report.agentResults()) {
            String status = result.success() ? "\u001B[32mOK\u001B[0m" : "\u001B[31mFAIL\u001B[0m";
            sb.append(String.format("[%s] %s (%dms) - %d skills invoked%n",
                status, result.displayName(), result.duration().toMillis(), result.getSkillCount()));

            if (config.verbose() && result.output() != null) {
                sb.append("  Output: ")
                  .append(result.output().substring(0, Math.min(200, result.output().length())))
                  .append(result.output().length() > 200 ? "..." : "")
                  .append("\n");
            }

            if (result.error() != null) {
                sb.append("  Error: ").append(result.error()).append("\n");
            }
        }

        if (!report.interactions().isEmpty()) {
            sb.append("\n=== Interactions ===\n\n");
            for (GregVerseReport.AgentInteraction interaction : report.interactions()) {
                sb.append(String.format("  %s -> %s [%s]: %s%n",
                    interaction.fromAgent(), interaction.toAgent(),
                    interaction.interactionType(), interaction.content()));
            }
        }

        if (!report.transactions().isEmpty()) {
            sb.append("\n=== Transactions ===\n\n");
            for (GregVerseReport.SkillTransaction tx : report.transactions()) {
                sb.append(String.format("  %s -> %s: %s ($%.2f)%n",
                    tx.buyerAgentId(), tx.sellerAgentId(), tx.skillName(), tx.price()));
            }
        }

        return sb.toString();
    }

    /**
     * Generate JSON output format.
     */
    private String generateJsonReport(GregVerseReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"scenarioId\": \"%s\",\n", report.scenarioId()));
        sb.append(String.format("  \"generatedAt\": \"%s\",\n", report.generatedAt()));
        sb.append(String.format("  \"totalDuration\": \"%s\",\n", report.totalDuration()));
        sb.append(String.format("  \"successfulAgents\": %d,\n", report.getSuccessfulAgents()));
        sb.append(String.format("  \"failedAgents\": %d,\n", report.getFailedAgents()));

        sb.append("  \"agentResults\": [\n");
        for (int i = 0; i < report.agentResults().size(); i++) {
            GregVerseReport.AgentResult r = report.agentResults().get(i);
            sb.append("    {\n");
            sb.append(String.format("      \"agentId\": \"%s\",\n", r.agentId()));
            sb.append(String.format("      \"displayName\": \"%s\",\n", r.displayName()));
            sb.append(String.format("      \"success\": %s,\n", r.success()));
            sb.append(String.format("      \"duration\": \"%s\",\n", r.duration()));
            sb.append(String.format("      \"skillCount\": %d\n", r.getSkillCount()));
            sb.append(i < report.agentResults().size() - 1 ? "    },\n" : "    }\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Generate Markdown output format.
     */
    private String generateMarkdownReport(GregVerseReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Greg-Verse Simulation Report\n\n");
        sb.append(String.format("**Scenario:** %s  \n", report.scenarioId()));
        sb.append(String.format("**Generated:** %s  \n", report.generatedAt()));
        sb.append(String.format("**Duration:** %s  \n\n", report.totalDuration()));

        sb.append("## Agent Results\n\n");
        sb.append("| Agent | Status | Duration | Skills |\n");
        sb.append("|-------|--------|----------|--------|\n");
        for (GregVerseReport.AgentResult r : report.agentResults()) {
            sb.append(String.format("| %s | %s | %dms | %d |\n",
                r.displayName(), r.success() ? "OK" : "FAIL",
                r.duration().toMillis(), r.getSkillCount()));
        }

        if (!report.transactions().isEmpty()) {
            sb.append("\n## Transactions\n\n");
            sb.append("| Buyer | Seller | Skill | Price |\n");
            sb.append("|-------|--------|-------|-------|\n");
            for (GregVerseReport.SkillTransaction tx : report.transactions()) {
                sb.append(String.format("| %s | %s | %s | $%.2f |\n",
                    tx.buyerAgentId(), tx.sellerAgentId(), tx.skillName(), tx.price()));
            }
        }

        return sb.toString();
    }

    /**
     * Generate HTML output format.
     */
    private String generateHtmlReport(GregVerseReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("<title>Greg-Verse Report</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: system-ui, sans-serif; margin: 40px; }\n");
        sb.append("table { border-collapse: collapse; width: 100%; }\n");
        sb.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }\n");
        sb.append("th { background-color: #f4f4f4; }\n");
        sb.append(".success { color: green; }\n");
        sb.append(".fail { color: red; }\n");
        sb.append("</style>\n</head>\n<body>\n");

        sb.append("<h1>Greg-Verse Simulation Report</h1>\n");
        sb.append(String.format("<p><strong>Scenario:</strong> %s</p>\n", report.scenarioId()));
        sb.append(String.format("<p><strong>Generated:</strong> %s</p>\n", report.generatedAt()));
        sb.append(String.format("<p><strong>Duration:</strong> %s</p>\n", report.totalDuration()));

        sb.append("<h2>Agent Results</h2>\n");
        sb.append("<table>\n<tr><th>Agent</th><th>Status</th><th>Duration</th><th>Skills</th></tr>\n");
        for (GregVerseReport.AgentResult r : report.agentResults()) {
            String statusClass = r.success() ? "success" : "fail";
            sb.append(String.format("<tr><td>%s</td><td class=\"%s\">%s</td><td>%dms</td><td>%d</td></tr>\n",
                r.displayName(), statusClass, r.success() ? "OK" : "FAIL",
                r.duration().toMillis(), r.getSkillCount()));
        }
        sb.append("</table>\n");

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * Print progress for a single scenario.
     */
    private void printScenarioProgress(String scenarioId, GregVerseReport report) {
        String status = report.getFailedAgents() == 0 ? "\u001B[32mOK\u001B[0m" : "\u001B[33mPARTIAL\u001B[0m";
        System.out.printf("[%s] %s... %d/%d agents successful (%dms)%n",
            status, scenarioId,
            report.getSuccessfulAgents(), report.agentResults().size(),
            report.totalDuration().toMillis());
    }

    /**
     * Print the header banner.
     */
    private void printHeader() {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("            Greg-Verse v" + VERSION);
        System.out.println("            Multi-Agent Business Advisor Simulation");
        System.out.println("======================================================================");
        System.out.println();
    }

    /**
     * Print the summary at the end of execution.
     */
    private void printSummary(GregVerseReport report, Duration totalDuration) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.printf("Complete. %d/%d agents successful.%n",
            report.getSuccessfulAgents(), report.agentResults().size());

        if (report.getFailedAgents() > 0) {
            System.out.printf("\u001B[33m%d agents failed:\u001B[0m%n", report.getFailedAgents());
            report.agentResults().stream()
                .filter(r -> !r.success())
                .forEach(r -> System.out.println("  - " + r.displayName() + ": " + r.error()));
        }

        if (!report.transactions().isEmpty()) {
            System.out.println();
            System.out.println("Marketplace Activity:");
            System.out.printf("  Transactions: %d%n", report.getTotalTransactions());
            System.out.printf("  Total Revenue: $%.2f%n", report.getTotalRevenue());
        }

        if (config.enableMetrics() && report.businessOutcomes() != null) {
            System.out.println();
            System.out.println("Business Outcomes:");
            System.out.printf("  Ideas Qualified: %d%n", report.businessOutcomes().ideasQualified());
            System.out.printf("  MVPs Built: %d%n", report.businessOutcomes().mvpsBuilt());
            System.out.printf("  Skills Created: %d%n", report.businessOutcomes().skillsCreated());
            System.out.printf("  Partnerships: %d%n", report.businessOutcomes().partnerships());
        }

        System.out.println();
        System.out.printf("Total duration: %s%n", formatDuration(totalDuration));
        System.out.println("======================================================================");
    }

    /**
     * Configure logging based on verbosity.
     */
    private void configureLogging(boolean verbose) {
        java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
        rootLogger.setLevel(verbose ? Level.FINE : Level.INFO);
    }

    /**
     * Format a duration for display.
     */
    private String formatDuration(Duration duration) {
        if (duration.toMinutes() > 0) {
            return String.format("%dm %ds", duration.toMinutes(), duration.toSecondsPart());
        }
        return String.format("%.2fs", duration.toMillis() / 1000.0);
    }

    /**
     * Format an agent ID as a display name.
     */
    private String formatAgentName(String agentId) {
        StringBuilder result = new StringBuilder();
        for (String part : agentId.split("-")) {
            if (!result.isEmpty()) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(part.charAt(0)))
                  .append(part.substring(1));
        }
        return result.toString();
    }

    /**
     * Run the automated self-play demo across all GregVerse scenarios.
     *
     * <p>The self-play demo iterates through all {@value #AVAILABLE_SCENARIOS_COUNT}
     * predefined scenarios with all 8 agents acting as both advisors and clients.
     * Results are aggregated into a single report and written to
     * {@code demo-results.json}.</p>
     *
     * @return combined report across all self-play scenario rounds
     */
    private GregVerseReport runSelfPlayDemo() {
        System.out.printf("Starting GregVerse self-play demo: %d scenarios × %d agents%n%n",
            AVAILABLE_SCENARIOS.size(), getAvailableAgentCount());

        List<GregVerseReport.AgentResult> allResults = new ArrayList<>();
        List<GregVerseReport.AgentInteraction> allInteractions = new ArrayList<>();
        List<GregVerseReport.SkillTransaction> allTransactions = new ArrayList<>();
        List<DemoPatternResult> patternResults = new ArrayList<>();

        Instant startTime = Instant.now();

        for (String scenarioId : AVAILABLE_SCENARIOS) {
            GregVerseConfig scenarioConfig = new GregVerseConfig(
                scenarioId,
                config.agentIds(),
                config.outputFormat(),
                null,
                config.timeoutSeconds(),
                config.parallelExecution(),
                false,
                config.marketplaceDuration(),
                null, null, null,
                config.enableMetrics(),
                config.verbose(),
                false
            );

            GregVerseSimulation simulation = new GregVerseSimulation(scenarioConfig);
            GregVerseReport round = simulation.run();

            allResults.addAll(round.agentResults());
            allInteractions.addAll(round.interactions());
            allTransactions.addAll(round.transactions());

            boolean scenarioSuccess = round.getFailedAgents() == 0;
            patternResults.add(new DemoPatternResult(
                scenarioId, scenarioSuccess ? "SUCCESS" : "FAILED"));
            printScenarioProgress(scenarioId, round);
        }

        Duration totalDuration = Duration.between(startTime, Instant.now());

        GregVerseReport combined = GregVerseReport.builder()
            .scenarioId("gregverse-self-play-demo")
            .generatedAt(Instant.now())
            .totalDuration(totalDuration)
            .agentResults(allResults)
            .interactions(allInteractions)
            .transactions(allTransactions)
            .build();

        writeDemoResults(patternResults, totalDuration);

        return combined;
    }

    /**
     * Write self-play demo results to {@code demo-results.json}.
     *
     * @param results list of per-scenario pattern results
     * @param duration total demo execution duration
     */
    private void writeDemoResults(List<DemoPatternResult> results, Duration duration) {
        long successful = results.stream()
            .filter(r -> "SUCCESS".equals(r.status())).count();
        long failed = results.stream()
            .filter(r -> "FAILED".equals(r.status())).count();
        double successRate = results.isEmpty()
            ? 0.0 : (double) successful / results.size() * 100;

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": \"2.0.0\",\n");
        json.append(String.format("  \"timestamp\": \"%s\",\n", Instant.now()));
        json.append(String.format("  \"totalPatterns\": %d,\n", results.size()));
        json.append(String.format("  \"successfulPatterns\": %d,\n", successful));
        json.append(String.format("  \"failedPatterns\": %d,\n", failed));
        json.append(String.format("  \"executionTimeSeconds\": %d,\n", duration.toSeconds()));
        json.append("  \"results\": [\n");
        for (int i = 0; i < results.size(); i++) {
            DemoPatternResult r = results.get(i);
            json.append(String.format("    {\"patternId\": \"%s\", \"status\": \"%s\"}",
                r.patternId(), r.status()));
            if (i < results.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");
        json.append(String.format("  \"successRate\": %.0f%n", successRate));
        json.append("}\n");

        String outputPath = config.hasOutputPath() ? config.outputPath() : "demo-results.json";
        try {
            Path path = Paths.get(outputPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, json.toString(), StandardCharsets.UTF_8);
            System.out.println("\nDemo results written to: " + outputPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write demo results to {}", outputPath, e);
        }
    }

    /**
     * Per-scenario result record used when building {@code demo-results.json}.
     *
     * @param patternId the GregVerse scenario ID (e.g. {@code gvs-1-startup-idea})
     * @param status    {@code "SUCCESS"} or {@code "FAILED"}
     */
    private record DemoPatternResult(String patternId, String status) {}

    /** Number of available scenarios (used in log messages). */
    private static final int AVAILABLE_SCENARIOS_COUNT = 5;

    /**
     * Get the number of available agents.
     */
    public static int getAvailableAgentCount() {
        return AGENT_FACTORIES.size();
    }

    /**
     * Get the available agent IDs.
     */
    public static Collection<String> getAvailableAgentIds() {
        return AGENT_FACTORIES.keySet();
    }

    /**
     * Get the available scenario IDs.
     */
    public static List<String> getAvailableScenarios() {
        return AVAILABLE_SCENARIOS;
    }
}
