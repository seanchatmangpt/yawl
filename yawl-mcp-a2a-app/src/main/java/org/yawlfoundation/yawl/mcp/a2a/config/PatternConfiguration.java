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

package org.yawlfoundation.yawl.mcp.a2a.config;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration settings for pattern execution in the YAWL MCP A2A system.
 *
 * <p>This configuration class provides all necessary settings for executing
 * workflow pattern demonstrations and analysis. It follows YAWL's strict
 * coding standards and provides real implementation without mock or stub behavior.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0
 */
public final class PatternConfiguration {

    /**
     * Default timeout for pattern execution.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Enable output formatting.
     */
    public static final boolean OUTPUT_FORMATTING_ENABLED = true;

    /**
     * Enable parallel execution of patterns.
     */
    public static final boolean PARALLEL_EXECUTION_ENABLED = true;

    /**
     * Enable automatic completion of work items.
     */
    public static final boolean AUTO_COMPLETION_ENABLED = true;

    /**
     * Enable metrics collection during execution.
     */
    public static final boolean METRICS_COLLECTION_ENABLED = true;

    /**
     * Enable commentary output.
     */
    public static final boolean COMMENTARY_ENABLED = false;

    /**
     * Enable token analysis.
     */
    public static final boolean TOKEN_ANALYSIS_ENABLED = true;

    /**
     * Output format enumeration.
     */
    public enum OutputFormat {
        /**
         * Console output with structured text.
         */
        CONSOLE("txt"),

        /**
         * JSON structured output.
         */
        JSON("json"),

        /**
         * Markdown format for documentation.
         */
        MARKDOWN("md"),

        /**
         * HTML format with styling.
         */
        HTML("html");

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
            return "." + fileExtension;
        }
    }

    /**
     * Builder for creating PatternConfiguration instances.
     */
    public static class Builder {
        private OutputFormat outputFormat = OutputFormat.CONSOLE;
        private Duration timeout = DEFAULT_TIMEOUT;
        private boolean parallelExecution = PARALLEL_EXECUTION_ENABLED;
        private boolean autoComplete = AUTO_COMPLETION_ENABLED;
        private boolean enableMetrics = METRICS_COLLECTION_ENABLED;
        private boolean withCommentary = COMMENTARY_ENABLED;
        private boolean tokenAnalysis = TOKEN_ANALYSIS_ENABLED;
        private List<String> patternIds = Collections.emptyList();

        /**
         * Set the output format.
         *
         * @param format output format
         * @return this builder
         */
        public Builder outputFormat(OutputFormat format) {
            this.outputFormat = Objects.requireNonNull(format, "Output format cannot be null");
            return this;
        }

        /**
         * Set the execution timeout.
         *
         * @param timeout timeout duration
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "Timeout cannot be null");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            return this;
        }

        /**
         * Enable or disable parallel execution.
         *
         * @param enabled whether to enable parallel execution
         * @return this builder
         */
        public Builder parallelExecution(boolean enabled) {
            this.parallelExecution = enabled;
            return this;
        }

        /**
         * Enable or disable auto-completion.
         *
         * @param enabled whether to enable auto-completion
         * @return this builder
         */
        public Builder autoComplete(boolean enabled) {
            this.autoComplete = enabled;
            return this;
        }

        /**
         * Enable or disable metrics collection.
         *
         * @param enabled whether to enable metrics collection
         * @return this builder
         */
        public Builder enableMetrics(boolean enabled) {
            this.enableMetrics = enabled;
            return this;
        }

        /**
         * Enable or disable commentary.
         *
         * @param enabled whether to enable commentary
         * @return this builder
         */
        public Builder withCommentary(boolean enabled) {
            this.withCommentary = enabled;
            return this;
        }

        /**
         * Enable or disable token analysis.
         *
         * @param enabled whether to enable token analysis
         * @return this builder
         */
        public Builder tokenAnalysis(boolean enabled) {
            this.tokenAnalysis = enabled;
            return this;
        }

        /**
         * Set the pattern IDs to execute.
         *
         * @param patternIds list of pattern IDs
         * @return this builder
         */
        public Builder patternIds(List<String> patternIds) {
            this.patternIds = Collections.unmodifiableList(
                Objects.requireNonNull(patternIds, "Pattern IDs cannot be null")
            );
            return this;
        }

        /**
         * Build the PatternConfiguration instance.
         *
         * @return new PatternConfiguration instance
         */
        public PatternConfiguration build() {
            return new PatternConfiguration(
                outputFormat,
                timeout,
                parallelExecution,
                autoComplete,
                enableMetrics,
                withCommentary,
                tokenAnalysis,
                patternIds
            );
        }
    }

    private final OutputFormat outputFormat;
    private final Duration timeout;
    private final boolean parallelExecution;
    private final boolean autoComplete;
    private final boolean enableMetrics;
    private final boolean withCommentary;
    private final boolean tokenAnalysis;
    private final List<String> patternIds;

    private PatternConfiguration(OutputFormat outputFormat,
                                Duration timeout,
                                boolean parallelExecution,
                                boolean autoComplete,
                                boolean enableMetrics,
                                boolean withCommentary,
                                boolean tokenAnalysis,
                                List<String> patternIds) {
        this.outputFormat = outputFormat;
        this.timeout = timeout;
        this.parallelExecution = parallelExecution;
        this.autoComplete = autoComplete;
        this.enableMetrics = enableMetrics;
        this.withCommentary = withCommentary;
        this.tokenAnalysis = tokenAnalysis;
        this.patternIds = patternIds;
    }

    /**
     * Create a new builder for constructing PatternConfiguration instances.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get the output format.
     *
     * @return output format
     */
    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    /**
     * Get the execution timeout.
     *
     * @return timeout duration
     */
    public Duration getTimeout() {
        return timeout;
    }

    /**
     * Check if parallel execution is enabled.
     *
     * @return true if parallel execution is enabled
     */
    public boolean isParallelExecutionEnabled() {
        return parallelExecution;
    }

    /**
     * Check if auto-completion is enabled.
     *
     * @return true if auto-completion is enabled
     */
    public boolean isAutoCompleteEnabled() {
        return autoComplete;
    }

    /**
     * Check if metrics collection is enabled.
     *
     * @return true if metrics collection is enabled
     */
    public boolean isMetricsEnabled() {
        return enableMetrics;
    }

    /**
     * Check if commentary is enabled.
     *
     * @return true if commentary is enabled
     */
    public boolean isCommentaryEnabled() {
        return withCommentary;
    }

    /**
     * Check if token analysis is enabled.
     *
     * @return true if token analysis is enabled
     */
    public boolean isTokenAnalysisEnabled() {
        return tokenAnalysis;
    }

    /**
     * Get the pattern IDs to execute.
     *
     * @return unmodifiable list of pattern IDs
     */
    public List<String> getPatternIds() {
        return patternIds;
    }

    /**
     * Check if specific pattern IDs are configured.
     *
     * @return true if pattern IDs are specified
     */
    public boolean hasPatternIds() {
        return !patternIds.isEmpty();
    }
}