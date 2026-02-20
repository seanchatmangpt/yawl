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

package org.yawlfoundation.yawl.mcp.a2a.demo.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Configuration record for YAWL pattern demo execution.
 *
 * <p>This record encapsulates all configuration options for running YAWL workflow
 * pattern demonstrations, including output formatting, execution parameters,
 * and pattern filtering options.</p>
 *
 * <h2>Configuration Options</h2>
 * <ul>
 *   <li><b>outputFormat</b>: Output format (CONSOLE, JSON, MARKDOWN, HTML)</li>
 *   <li><b>outputPath</b>: Base path for output files (default: "report")</li>
 *   <li><b>timeoutSeconds</b>: Execution timeout per pattern (default: 300)</li>
 *   <li><b>enableTracing</b>: Enable execution tracing (default: true)</li>
 *   <li><b>enableMetrics</b>: Enable metrics collection (default: true)</li>
 *   <li><b>autoComplete</b>: Auto-complete work items (default: true)</li>
 *   <li><b>parallelExecution</b>: Run patterns in parallel (default: true)</li>
 *   <li><b>tokenAnalysis</b>: Analyze token savings (default: true)</li>
 *   <li><b>withCommentary</b>: Include detailed commentary in output (default: false)</li>
 *   <li><b>patternIds</b>: Specific pattern IDs to run (empty = all)</li>
 *   <li><b>categories</b>: Categories to run (empty = all)</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * <h3>Builder Pattern</h3>
 * <pre>{@code
 * DemoConfig config = DemoConfig.builder()
 *     .outputFormat(OutputFormat.MARKDOWN)
 *     .timeoutSeconds(120)
 *     .enableTracing(true)
 *     .addPatternId("WCP-1")
 *     .addPatternId("WCP-2")
 *     .addCategory(PatternCategory.BASIC)
 *     .build();
 * }</pre>
 *
 * <h3>Command Line Parsing</h3>
 * <pre>{@code
 * String[] args = {"--format", "json", "--timeout", "60", "--patterns", "WCP-1,WCP-2"};
 * DemoConfig config = DemoConfig.fromCommandLine(args);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0
 */
public record DemoConfig(
    OutputFormat outputFormat,
    String outputPath,
    int timeoutSeconds,
    boolean enableTracing,
    boolean enableMetrics,
    boolean autoComplete,
    boolean parallelExecution,
    boolean tokenAnalysis,
    boolean withCommentary,
    List<String> patternIds,
    List<PatternCategory> categories
) {

    /**
     * Default timeout in seconds for pattern execution.
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    /**
     * Default output path for generated reports.
     */
    public static final String DEFAULT_OUTPUT_PATH = "report";

    /**
     * Compact constructor for validation and defaults.
     */
    public DemoConfig {
        // Apply defaults for null values
        if (outputFormat == null) {
            outputFormat = OutputFormat.CONSOLE;
        }
        if (outputPath == null || outputPath.isBlank()) {
            outputPath = DEFAULT_OUTPUT_PATH;
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        }
        if (patternIds == null) {
            patternIds = Collections.emptyList();
        } else {
            patternIds = List.copyOf(patternIds);
        }
        if (categories == null) {
            categories = Collections.emptyList();
        } else {
            categories = List.copyOf(categories);
        }
    }

    /**
     * Create a configuration with all defaults.
     *
     * @return configuration with default values
     */
    public static DemoConfig defaults() {
        return new DemoConfig(
            OutputFormat.CONSOLE,
            DEFAULT_OUTPUT_PATH,
            DEFAULT_TIMEOUT_SECONDS,
            true,
            true,
            true,
            true,
            true,
            false,
            Collections.emptyList(),
            Collections.emptyList()
        );
    }

    /**
     * Create a new builder for constructing DemoConfig instances.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parse command line arguments into a DemoConfig instance.
     *
     * <p>Supported arguments:</p>
     * <ul>
     *   <li>{@code --format}, {@code -f}: Output format (console, json, markdown, html)</li>
     *   <li>{@code --output}, {@code -o}: Output path</li>
     *   <li>{@code --timeout}, {@code -t}: Timeout in seconds</li>
     *   <li>{@code --no-tracing}: Disable tracing</li>
     *   <li>{@code --no-metrics}: Disable metrics</li>
     *   <li>{@code --no-auto-complete}: Disable auto-completion</li>
     *   <li>{@code --no-parallel}: Disable parallel execution</li>
     *   <li>{@code --no-token-analysis}: Disable token analysis</li>
     *   <li>{@code --commentary}: Enable commentary</li>
     *   <li>{@code --patterns}, {@code -p}: Comma-separated pattern IDs</li>
     *   <li>{@code --categories}, {@code -c}: Comma-separated categories</li>
     *   <li>{@code --help}, {@code -h}: Show help</li>
     * </ul>
     *
     * @param args command line arguments
     * @return parsed configuration
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static DemoConfig fromCommandLine(String[] args) {
        Objects.requireNonNull(args, "args must not be null");
        return CommandLineParser.parse(args);
    }

    /**
     * Get the timeout as a Duration.
     *
     * @return timeout duration
     */
    public Duration getTimeoutDuration() {
        return Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Get the output path as a Path object.
     *
     * @return output path
     */
    public Path getOutputPathAsPath() {
        return Paths.get(outputPath);
    }

    /**
     * Get the full output file path with appropriate extension.
     *
     * @return output file path with extension
     */
    public String getOutputFilePath() {
        String extension = outputFormat.getFileExtension();
        if (outputPath.endsWith(extension)) {
            return outputPath;
        }
        return outputPath + extension;
    }

    /**
     * Check if specific patterns are filtered (not running all patterns).
     *
     * @return true if pattern filtering is active
     */
    public boolean hasPatternFilter() {
        return patternIds != null && !patternIds.isEmpty();
    }

    /**
     * Check if category filtering is active.
     *
     * @return true if category filtering is active
     */
    public boolean hasCategoryFilter() {
        return categories != null && !categories.isEmpty();
    }

    /**
     * Check if a pattern should be executed based on filters.
     *
     * @param patternId the pattern ID to check
     * @param category the pattern category
     * @return true if the pattern should be executed
     */
    public boolean shouldExecutePattern(String patternId, PatternCategory category) {
        if (hasPatternFilter()) {
            return patternIds.contains(patternId);
        }
        if (hasCategoryFilter()) {
            return categories.contains(category);
        }
        return true;
    }

    /**
     * Create a copy of this configuration with a different output format.
     *
     * @param newFormat the new output format
     * @return new configuration with updated format
     */
    public DemoConfig withOutputFormat(OutputFormat newFormat) {
        return new DemoConfig(
            newFormat, outputPath, timeoutSeconds, enableTracing, enableMetrics,
            autoComplete, parallelExecution, tokenAnalysis, withCommentary,
            patternIds, categories
        );
    }

    /**
     * Create a copy of this configuration with a different timeout.
     *
     * @param newTimeoutSeconds the new timeout in seconds
     * @return new configuration with updated timeout
     */
    public DemoConfig withTimeout(int newTimeoutSeconds) {
        return new DemoConfig(
            outputFormat, outputPath, newTimeoutSeconds, enableTracing, enableMetrics,
            autoComplete, parallelExecution, tokenAnalysis, withCommentary,
            patternIds, categories
        );
    }

    /**
     * Output format enumeration for demo reports.
     */
    public enum OutputFormat {
        /**
         * Console output with ANSI colors.
         */
        CONSOLE(".txt"),

        /**
         * JSON structured output.
         */
        JSON(".json"),

        /**
         * Markdown format for documentation.
         */
        MARKDOWN(".md"),

        /**
         * HTML format with styling.
         */
        HTML(".html");

        private final String fileExtension;

        OutputFormat(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        /**
         * Get the file extension for this format.
         *
         * @return file extension including the dot
         */
        public String getFileExtension() {
            return fileExtension;
        }

        /**
         * Parse a string to an output format.
         *
         * @param value string representation
         * @return parsed format, defaults to CONSOLE if null or invalid
         */
        public static OutputFormat fromString(String value) {
            if (value == null || value.isBlank()) {
                return CONSOLE;
            }
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return CONSOLE;
            }
        }
    }

    /**
     * Builder class for constructing DemoConfig instances.
     */
    public static class Builder {
        private OutputFormat outputFormat = OutputFormat.CONSOLE;
        private String outputPath = DEFAULT_OUTPUT_PATH;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private boolean enableTracing = true;
        private boolean enableMetrics = true;
        private boolean autoComplete = true;
        private boolean parallelExecution = true;
        private boolean tokenAnalysis = true;
        private boolean withCommentary = false;
        private final List<String> patternIds = new ArrayList<>();
        private final List<PatternCategory> categories = new ArrayList<>();

        /**
         * Set the output format.
         *
         * @param format output format
         * @return this builder
         */
        public Builder outputFormat(OutputFormat format) {
            this.outputFormat = format;
            return this;
        }

        /**
         * Set the output path.
         *
         * @param path output path
         * @return this builder
         */
        public Builder outputPath(String path) {
            this.outputPath = path;
            return this;
        }

        /**
         * Set the timeout in seconds.
         *
         * @param seconds timeout duration
         * @return this builder
         */
        public Builder timeoutSeconds(int seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        /**
         * Enable or disable tracing.
         *
         * @param enabled tracing enabled
         * @return this builder
         */
        public Builder enableTracing(boolean enabled) {
            this.enableTracing = enabled;
            return this;
        }

        /**
         * Enable or disable metrics collection.
         *
         * @param enabled metrics enabled
         * @return this builder
         */
        public Builder enableMetrics(boolean enabled) {
            this.enableMetrics = enabled;
            return this;
        }

        /**
         * Enable or disable auto-completion.
         *
         * @param enabled auto-complete enabled
         * @return this builder
         */
        public Builder autoComplete(boolean enabled) {
            this.autoComplete = enabled;
            return this;
        }

        /**
         * Enable or disable parallel execution.
         *
         * @param enabled parallel execution enabled
         * @return this builder
         */
        public Builder parallelExecution(boolean enabled) {
            this.parallelExecution = enabled;
            return this;
        }

        /**
         * Enable or disable token analysis.
         *
         * @param enabled token analysis enabled
         * @return this builder
         */
        public Builder tokenAnalysis(boolean enabled) {
            this.tokenAnalysis = enabled;
            return this;
        }

        /**
         * Enable or disable commentary.
         *
         * @param enabled commentary enabled
         * @return this builder
         */
        public Builder withCommentary(boolean enabled) {
            this.withCommentary = enabled;
            return this;
        }

        /**
         * Set the pattern IDs to run.
         *
         * @param ids pattern IDs
         * @return this builder
         */
        public Builder patternIds(List<String> ids) {
            this.patternIds.clear();
            if (ids != null) {
                this.patternIds.addAll(ids);
            }
            return this;
        }

        /**
         * Add a single pattern ID.
         *
         * @param patternId pattern ID to add
         * @return this builder
         */
        public Builder addPatternId(String patternId) {
            if (patternId != null && !patternId.isBlank()) {
                this.patternIds.add(patternId);
            }
            return this;
        }

        /**
         * Set the categories to run.
         *
         * @param cats categories
         * @return this builder
         */
        public Builder categories(List<PatternCategory> cats) {
            this.categories.clear();
            if (cats != null) {
                this.categories.addAll(cats);
            }
            return this;
        }

        /**
         * Add a single category.
         *
         * @param category category to add
         * @return this builder
         */
        public Builder addCategory(PatternCategory category) {
            if (category != null) {
                this.categories.add(category);
            }
            return this;
        }

        /**
         * Build the DemoConfig instance.
         *
         * @return new DemoConfig instance
         */
        public DemoConfig build() {
            return new DemoConfig(
                outputFormat,
                outputPath,
                timeoutSeconds,
                enableTracing,
                enableMetrics,
                autoComplete,
                parallelExecution,
                tokenAnalysis,
                withCommentary,
                new ArrayList<>(patternIds),
                new ArrayList<>(categories)
            );
        }
    }

    /**
     * Command line argument parser for DemoConfig.
     */
    public static class CommandLineParser {

        private static final String HELP_TEXT = """
            YAWL Pattern Demo Runner

            Usage: java -jar yawl-demo.jar [options]

            Options:
              -f, --format <format>      Output format: console, json, markdown, html
              -o, --output <path>        Output file path (default: report)
              -t, --timeout <seconds>    Execution timeout per pattern (default: 300)
              --no-tracing               Disable execution tracing
              --no-metrics               Disable metrics collection
              --no-auto-complete         Disable auto-completion of work items
              --no-parallel              Disable parallel pattern execution
              --no-token-analysis        Disable token savings analysis
              --commentary               Include detailed commentary in output
              -p, --patterns <ids>       Comma-separated pattern IDs to run
              -c, --categories <cats>    Comma-separated categories to run
              -h, --help                 Show this help message

            Categories:
              BASIC          Basic Control Flow patterns
              BRANCHING      Branching and Synchronization patterns
              MULTI_INSTANCE Multi-Instance patterns
              STATE_BASED    State-Based patterns
              DISTRIBUTED    Distributed workflow patterns
              EVENT_DRIVEN   Event-Driven patterns
              AI_ML          AI/ML Integration patterns
              ENTERPRISE     Enterprise patterns
              AGENT          Agent patterns

            Examples:
              java -jar yawl-demo.jar --format markdown --output patterns-report
              java -jar yawl-demo.jar -f json -t 60 --patterns WCP-1,WCP-2,WCP-3
              java -jar yawl-demo.jar --categories BASIC,BRANCHING --commentary
            """;

        /**
         * Parse command line arguments into a DemoConfig.
         *
         * @param args command line arguments
         * @return parsed configuration
         * @throws IllegalArgumentException if arguments are invalid
         */
        public static DemoConfig parse(String[] args) {
            Builder builder = builder();

            for (int i = 0; i < args.length; i++) {
                String arg = args[i];

                switch (arg) {
                    case "-h", "--help" -> {
                        System.out.println(HELP_TEXT);
                        System.exit(0);
                    }
                    case "-f", "--format" -> {
                        if (i + 1 < args.length) {
                            builder.outputFormat(OutputFormat.fromString(args[++i]));
                        } else {
                            throw new IllegalArgumentException("Missing value for " + arg);
                        }
                    }
                    case "-o", "--output" -> {
                        if (i + 1 < args.length) {
                            builder.outputPath(args[++i]);
                        } else {
                            throw new IllegalArgumentException("Missing value for " + arg);
                        }
                    }
                    case "-t", "--timeout" -> {
                        if (i + 1 < args.length) {
                            try {
                                builder.timeoutSeconds(Integer.parseInt(args[++i]));
                            } catch (NumberFormatException e) {
                                throw new IllegalArgumentException(
                                    "Invalid timeout value: " + args[i]);
                            }
                        } else {
                            throw new IllegalArgumentException("Missing value for " + arg);
                        }
                    }
                    case "--no-tracing" -> builder.enableTracing(false);
                    case "--no-metrics" -> builder.enableMetrics(false);
                    case "--no-auto-complete" -> builder.autoComplete(false);
                    case "--no-parallel" -> builder.parallelExecution(false);
                    case "--no-token-analysis" -> builder.tokenAnalysis(false);
                    case "--commentary" -> builder.withCommentary(true);
                    case "-p", "--patterns" -> {
                        if (i + 1 < args.length) {
                            String[] patterns = args[++i].split(",");
                            for (String pattern : patterns) {
                                builder.addPatternId(pattern.trim());
                            }
                        } else {
                            throw new IllegalArgumentException("Missing value for " + arg);
                        }
                    }
                    case "-c", "--categories" -> {
                        if (i + 1 < args.length) {
                            String[] cats = args[++i].split(",");
                            for (String cat : cats) {
                                PatternCategory category = PatternCategory.fromName(cat.trim());
                                if (category == null) {
                                    throw new IllegalArgumentException(
                                        "Unknown category: " + cat);
                                }
                                builder.addCategory(category);
                            }
                        } else {
                            throw new IllegalArgumentException("Missing value for " + arg);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }

            return builder.build();
        }

        /**
         * Get the help text for the command line interface.
         *
         * @return formatted help text
         */
        public static String getHelpText() {
            return HELP_TEXT;
        }
    }
}
