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

package org.yawlfoundation.yawl.mcp.a2a.demo;

import org.yawlfoundation.yawl.mcp.a2a.demo.config.DemoConfig;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternCategory;
import org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternRegistry;
import org.yawlfoundation.yawl.mcp.a2a.demo.execution.ExecutionHarness;
import org.yawlfoundation.yawl.mcp.a2a.demo.execution.ExecutionHarness.ExecutionResult;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.Difficulty;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.ExecutionMetrics;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.PatternInfo;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.TokenAnalysis;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.TraceEvent;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.ReportGenerator;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.YawlPatternDemoReport;
import org.yawlfoundation.yawl.mcp.a2a.example.ExtendedYamlConverter;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * CLI entry point for the YAWL Pattern Demo.
 *
 * <p>This class provides a command-line interface for executing YAWL workflow
 * patterns with comprehensive reporting and analysis capabilities. It supports
 * parallel execution using Java 21+ virtual threads.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * java -jar yawl-pattern-demo.jar --all                           # Run all patterns
 * java -jar yawl-pattern-demo.jar --pattern WCP-1                 # Run specific pattern
 * java -jar yawl-pattern-demo.jar --category BASIC               # Run all basic patterns
 * java -jar yawl-pattern-demo.jar --all --format html            # Generate HTML report
 * java -jar yawl-pattern-demo.jar --all --token-report           # Include token analysis
 * }</pre>
 *
 * <h2>Options</h2>
 * <ul>
 *   <li>{@code --all} - Run all available patterns</li>
 *   <li>{@code --pattern <id>} - Run specific pattern by ID (e.g., WCP-1)</li>
 *   <li>{@code --category <name>} - Run all patterns in a category</li>
 *   <li>{@code --format <type>} - Output format: console, json, markdown, html</li>
 *   <li>{@code --output <path>} - Write output to file</li>
 *   <li>{@code --timeout <seconds>} - Execution timeout per pattern</li>
 *   <li>{@code --parallel} - Enable parallel execution (default: true)</li>
 *   <li>{@code --sequential} - Disable parallel execution</li>
 *   <li>{@code --token-report} - Generate token savings analysis</li>
 *   <li>{@code --with-commentary} - Include Wil van der Aalst commentary</li>
 *   <li>{@code --help} - Show usage information</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PatternDemoRunner {

    private static final Logger LOGGER = Logger.getLogger(PatternDemoRunner.class.getName());
    private static final String VERSION = "6.0.0";
    private static final String RESOURCE_PATH = "patterns/";

    private static final String ZAI_API_URL = "https://api.z.ai/api/paas/v4/chat/completions";
    private static final String ZAI_MODEL = "GLM-4.7-Flash";

    private final DemoConfig config;
    private final ExtendedYamlConverter yamlConverter;
    private final PatternRegistry registry;
    private YStatelessEngine engine;
    private HttpClient zaiClient;
    private String zaiApiKey;

    /**
     * Main entry point for the pattern demo runner.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        DemoConfig config = DemoConfig.fromCommandLine(args);

        PatternDemoRunner runner = new PatternDemoRunner(config);
        int exitCode = runner.run();
        System.exit(exitCode);
    }

    /**
     * Create a new pattern demo runner with the specified configuration.
     *
     * @param config the demo configuration
     */
    public PatternDemoRunner(DemoConfig config) {
        this.config = config;
        this.yamlConverter = new ExtendedYamlConverter();
        this.registry = new PatternRegistry();
    }

    /**
     * Execute the pattern demo and return exit code.
     *
     * @return 0 for success, 1 for failure
     */
    public int run() {
        Instant startTime = Instant.now();

        printHeader();

        try {
            // Initialize engine with idle timeout monitoring
            engine = new YStatelessEngine(config.timeoutSeconds() * 1000L);

            // Initialize Z.AI via HttpClient for commentary if requested and API key available
            zaiApiKey = System.getenv("ZAI_API_KEY");
            if (config.withCommentary() && zaiApiKey != null && !zaiApiKey.isBlank()) {
                try {
                    HttpClient.Builder builder = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10));

                    // Configure proxy from system properties (set via JAVA_TOOL_OPTIONS)
                    String proxyHost = System.getProperty("https.proxyHost");
                    String proxyPort = System.getProperty("https.proxyPort");
                    if (proxyHost != null && proxyPort != null) {
                        builder.proxy(ProxySelector.of(
                            new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort))));

                        String proxyUser = System.getProperty("https.proxyUser");
                        String proxyPass = System.getProperty("https.proxyPassword");
                        if (proxyUser != null && proxyPass != null) {
                            builder.authenticator(new Authenticator() {
                                @Override
                                protected PasswordAuthentication getPasswordAuthentication() {
                                    return new PasswordAuthentication(
                                        proxyUser, proxyPass.toCharArray());
                                }
                            });
                        }
                    }

                    zaiClient = builder.build();
                    System.out.println("Z.AI commentary enabled (model: " + ZAI_MODEL + ")");
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "Z.AI unavailable, commentary disabled: " + t.getMessage());
                }
            }

            // Get patterns to execute
            List<PatternInfo> patterns = getPatternsToExecute();
            int totalPatterns = patterns.size();

            if (totalPatterns == 0) {
                System.err.println("No patterns found matching criteria.");
                return 1;
            }

            System.out.printf("Running %d pattern%s with %s...%n%n",
                totalPatterns,
                totalPatterns == 1 ? "" : "s",
                config.parallelExecution() ? "virtual threads" : "sequential execution");

            // Execute patterns
            List<PatternResult> results = config.parallelExecution()
                ? executePatternsParallel(patterns)
                : executePatternsSequential(patterns);

            // Generate report
            YawlPatternDemoReport report = new YawlPatternDemoReport(results, Instant.now());

            // Output results
            outputReport(report);

            // Print summary
            printSummary(report);

            return report.getFailedPatterns() > 0 ? 1 : 0;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Pattern demo execution failed", e);
            System.err.println("Execution failed: " + e.getMessage());
            return 1;
        } finally {
            shutdown();
        }
    }

    /**
     * Get patterns to execute based on configuration.
     */
    private List<PatternInfo> getPatternsToExecute() {
        List<PatternInfo> patterns = new ArrayList<>();

        // Filter by pattern IDs if specified
        if (config.hasPatternFilter()) {
            for (String patternId : config.patternIds()) {
                Optional<PatternInfo> pattern = registry.getPattern(patternId);
                if (pattern.isPresent()) {
                    patterns.add(pattern.get());
                } else {
                    System.err.println("Pattern not found: " + patternId);
                    suggestSimilarPatterns(patternId);
                }
            }
            return patterns;
        }

        // Filter by categories if specified
        if (config.hasCategoryFilter()) {
            for (PatternCategory category : config.categories()) {
                patterns.addAll(registry.getPatternsByCategory(category));
            }
            return patterns;
        }

        // No filter - return all patterns
        return registry.getAllPatterns();
    }

    /**
     * Suggest similar pattern IDs when a pattern is not found.
     */
    private void suggestSimilarPatterns(String patternId) {
        List<String> suggestions = registry.findSimilarPatterns(patternId);
        if (!suggestions.isEmpty()) {
            System.err.println("Did you mean:");
            suggestions.forEach(id -> System.err.println("  - " + id));
        }
    }

    /**
     * Execute patterns in parallel using virtual threads.
     */
    private List<PatternResult> executePatternsParallel(List<PatternInfo> patterns) {
        Map<String, PatternResult> results = new ConcurrentHashMap<>();
        int total = patterns.size();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<PatternResult>> futures = patterns.stream()
                .map(pattern -> executor.submit(() -> executePattern(pattern)))
                .toList();

            // Collect results with timeout handling
            for (int i = 0; i < futures.size(); i++) {
                PatternInfo info = patterns.get(i);
                try {
                    long timeoutMs = config.getTimeoutDuration().toMillis();
                    PatternResult result = futures.get(i).get(timeoutMs, TimeUnit.MILLISECONDS);
                    results.put(info.id(), result);
                    printProgress(i + 1, total, info.id(), result.isSuccess(), result.getDuration());
                } catch (TimeoutException e) {
                    LOGGER.warning("Pattern " + info.id() + " timed out");
                    PatternResult timeoutResult = createTimeoutResult(info);
                    results.put(info.id(), timeoutResult);
                    printProgress(i + 1, total, info.id(), false, config.getTimeoutDuration());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Pattern " + info.id() + " execution failed", e);
                    PatternResult errorResult = createErrorResult(info, e);
                    results.put(info.id(), errorResult);
                    printProgress(i + 1, total, info.id(), false, Duration.ZERO);
                }
            }
        }

        // Return in original order
        return patterns.stream()
            .map(p -> results.get(p.id()))
            .toList();
    }

    /**
     * Execute patterns sequentially.
     */
    private List<PatternResult> executePatternsSequential(List<PatternInfo> patterns) {
        List<PatternResult> results = new ArrayList<>();
        int total = patterns.size();

        for (int i = 0; i < patterns.size(); i++) {
            PatternInfo pattern = patterns.get(i);
            PatternResult result = executePattern(pattern);
            results.add(result);
            printProgress(i + 1, total, pattern.id(), result.isSuccess(), result.getDuration());
        }

        return results;
    }

    /**
     * Execute a single pattern.
     */
    private PatternResult executePattern(PatternInfo pattern) {
        Instant startTime = Instant.now();

        try {
            // Load YAML from resources
            String yaml = loadPatternYaml(pattern.yamlExample());
            if (yaml == null) {
                return createMissingFileResult(pattern, startTime);
            }

            // Convert to XML
            String xml = yamlConverter.convertToXml(yaml);

            // Calculate token analysis if requested
            TokenAnalysis tokenAnalysis = null;
            if (config.tokenAnalysis()) {
                tokenAnalysis = new TokenAnalysis(
                    estimateTokens(yaml),
                    estimateTokens(xml)
                );
            }

            // Execute via harness with a fresh engine per pattern to avoid state leakage
            YStatelessEngine patternEngine = new YStatelessEngine(config.timeoutSeconds() * 1000L);
            ExecutionHarness harness = ExecutionHarness.create(patternEngine)
                .withAutoCompletion(config.autoComplete())
                .withTracing(config.enableTracing())
                .withMetrics(config.enableMetrics())
                .withTimeout(config.getTimeoutDuration())
                .withSpecification(pattern.id());

            ExecutionResult execResult = harness.execute(xml);

            // Convert trace events
            List<TraceEvent> traceEvents = execResult.getTrace().stream()
                .map(e -> new TraceEvent(e.type(), e.data(), e.timestamp()))
                .toList();

            // Create metrics
            ExecutionMetrics metrics = new ExecutionMetrics(
                execResult.getMetrics().getWorkItemCount(),
                execResult.getMetrics().getEventCount(),
                execResult.getDuration()
            );

            Instant endTime = Instant.now();

            if (execResult.isSuccess()) {
                return new PatternResult(
                    pattern.id(),
                    pattern,
                    startTime,
                    endTime,
                    traceEvents,
                    metrics,
                    tokenAnalysis
                );
            } else {
                String errorMsg = execResult.getError() != null
                    ? execResult.getError().getMessage()
                    : "Unknown error";
                return new PatternResult(
                    pattern.id(),
                    pattern,
                    startTime,
                    endTime,
                    errorMsg,
                    traceEvents,
                    metrics,
                    tokenAnalysis
                );
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to execute pattern: " + pattern.id(), e);
            return createErrorResult(pattern, e);
        }
    }

    /**
     * Load pattern YAML from resources.
     */
    private String loadPatternYaml(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return null;
        }

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(RESOURCE_PATH + resourcePath)) {
            if (is == null) {
                LOGGER.warning("Pattern resource not found: " + resourcePath);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load pattern: " + resourcePath, e);
            return null;
        }
    }

    /**
     * Estimate token count for a string (rough approximation).
     */
    private int estimateTokens(String text) {
        if (text == null) return 0;
        // Rough estimate: ~4 characters per token for English text
        return text.length() / 4;
    }

    /**
     * Create a result for a timed-out pattern execution.
     */
    private PatternResult createTimeoutResult(PatternInfo pattern) {
        Instant now = Instant.now();
        Duration timeout = config.getTimeoutDuration();
        return new PatternResult(
            pattern.id(),
            pattern,
            now.minus(timeout),
            now,
            "Execution timed out after " + timeout.toSeconds() + " seconds",
            List.of(),
            new ExecutionMetrics(0, 0, timeout),
            null
        );
    }

    /**
     * Create a result for a failed pattern execution.
     */
    private PatternResult createErrorResult(PatternInfo pattern, Throwable error) {
        Instant now = Instant.now();
        String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
        return new PatternResult(
            pattern.id(),
            pattern,
            now,
            now,
            message,
            List.of(),
            new ExecutionMetrics(0, 0, Duration.ZERO),
            null
        );
    }

    /**
     * Create a result for a missing pattern file.
     */
    private PatternResult createMissingFileResult(PatternInfo pattern, Instant startTime) {
        return new PatternResult(
            pattern.id(),
            pattern,
            startTime,
            Instant.now(),
            "Pattern file not found: " + pattern.yamlExample(),
            List.of(),
            new ExecutionMetrics(0, 0, Duration.between(startTime, Instant.now())),
            null
        );
    }

    /**
     * Output the report in the configured format.
     */
    private void outputReport(YawlPatternDemoReport report) {
        ReportGenerator generator = new ReportGenerator();

        String output = switch (config.outputFormat()) {
            case JSON -> generator.generateJson(report);
            case MARKDOWN -> generator.generateMarkdown(report);
            case HTML -> generator.generateHtml(report);
            default -> generator.generateConsole(report);
        };

        if (config.outputPath() != null && !config.outputPath().isBlank()) {
            try {
                Path path = Paths.get(config.getOutputFilePath());
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.writeString(path, output, StandardCharsets.UTF_8);
                System.out.println("\nReport saved to: " + config.getOutputFilePath());
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to write output file", e);
                System.err.println("Failed to write output: " + e.getMessage());
                System.out.println("\n" + output);
            }
        } else if (config.outputFormat() != DemoConfig.OutputFormat.CONSOLE) {
            // Non-console formats default to stdout
            System.out.println("\n" + output);
        }
    }

    /**
     * Print progress indicator.
     */
    private void printProgress(int current, int total, String patternId, boolean success, Duration duration) {
        String status = success ? "OK" : "FAIL";
        String color = success ? "\u001B[32m" : "\u001B[31m";
        String reset = "\u001B[0m";

        System.out.printf("[%d/%d] %s%s%s... %s%s%s (%dms)%n",
            current, total,
            color, patternId, reset,
            color, status, reset,
            duration.toMillis());
    }

    /**
     * Print header banner.
     */
    private void printHeader() {
        System.out.println();
        System.out.println("======================================================================");
        System.out.println("            YAWL Pattern Demo v" + VERSION);
        System.out.println("            Yet Another Workflow Language");
        System.out.println("======================================================================");
        System.out.println();
    }

    /**
     * Print summary at the end of execution.
     */
    private void printSummary(YawlPatternDemoReport report) {
        System.out.println();
        System.out.println("======================================================================");
        System.out.printf("Complete. %d/%d patterns successful.%n",
            report.getSuccessfulPatterns(),
            report.getTotalPatterns());

        if (report.getFailedPatterns() > 0) {
            System.out.printf("\u001B[33m%d patterns failed:\u001B[0m%n", report.getFailedPatterns());
            report.getFailures().forEach(r ->
                System.out.println("  - " + r.getPatternId() + ": " + r.getError()));
        }

        if (config.tokenAnalysis()) {
            System.out.println();
            System.out.println("Token Analysis:");
            System.out.printf("  YAML tokens: %,d%n", report.getTotalYamlTokens());
            System.out.printf("  XML tokens:  %,d%n", report.getTotalXmlTokens());
            System.out.printf("  Savings:     %.1f%%%n", report.getTotalTokenSavings());
        }

        // Z.AI commentary via direct HttpClient
        if (zaiClient != null) {
            System.out.println();
            System.out.println("Z.AI Commentary (Wil van der Aalst perspective):");
            try {
                String commentary = callZaiApi(String.format(
                    "You are Professor Wil van der Aalst, the creator of workflow patterns. "
                    + "Provide a brief (3-4 sentences) commentary on these YAWL pattern demo results: "
                    + "%d/%d patterns executed successfully, %d patterns failed, "
                    + "total execution time: %s. "
                    + "Comment on the workflow pattern coverage and significance.",
                    report.getSuccessfulPatterns(), report.getTotalPatterns(),
                    report.getFailedPatterns(), report.getFormattedTotalTime()));
                System.out.println("  " + commentary.replace("\n", "\n  "));
            } catch (Exception e) {
                System.out.println("  (Commentary unavailable: " + e.getMessage() + ")");
            }
        }

        System.out.println();
        System.out.printf("Total duration: %s%n", report.getFormattedTotalTime());
        System.out.println("======================================================================");
    }

    /**
     * Call the Z.AI chat completions API directly via HttpClient.
     * Uses raw API key as Bearer token per Z.AI API docs.
     *
     * @param userMessage the user prompt to send
     * @return the assistant's reply text
     */
    private String callZaiApi(String userMessage) throws IOException, InterruptedException {
        // Escape the message for JSON embedding
        String escapedMessage = userMessage
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");

        String requestBody = """
            {"model":"%s","messages":[{"role":"system","content":"You are a workflow patterns expert."},{"role":"user","content":"%s"}],"max_tokens":256}"""
            .formatted(ZAI_MODEL, escapedMessage);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ZAI_API_URL))
            .header("Content-Type", "application/json")
            .header("Accept-Language", "en-US,en")
            .header("Authorization", "Bearer " + zaiApiKey)
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
            .build();

        // Retry with exponential backoff for rate limits
        HttpResponse<String> response = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            response = zaiClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                long backoffMs = (long) (2000 * Math.pow(2, attempt));
                LOGGER.info("Z.AI rate limited, retrying in " + backoffMs + "ms...");
                Thread.sleep(backoffMs);
                continue;
            }
            break;
        }

        if (response.statusCode() != 200) {
            throw new IOException("Z.AI API returned HTTP " + response.statusCode() + ": " + response.body());
        }

        // Extract content from JSON response: {"choices":[{"message":{"content":"..."}}]}
        String body = response.body();
        int contentIdx = body.indexOf("\"content\":");
        if (contentIdx < 0) {
            throw new IOException("No content in Z.AI response");
        }
        // Find the opening quote after "content":
        int startQuote = body.indexOf('"', contentIdx + 10);
        if (startQuote < 0) {
            throw new IOException("Malformed Z.AI response");
        }
        // Find the closing quote, handling escaped quotes
        int endQuote = startQuote + 1;
        while (endQuote < body.length()) {
            char c = body.charAt(endQuote);
            if (c == '\\') {
                endQuote += 2; // skip escaped character
            } else if (c == '"') {
                break;
            } else {
                endQuote++;
            }
        }
        return body.substring(startQuote + 1, endQuote)
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    /**
     * Shutdown engine and resources.
     */
    private void shutdown() {
        if (engine != null) {
            try {
                engine.setCaseMonitoringEnabled(false);
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Error during engine shutdown", e);
            }
        }
    }
}
