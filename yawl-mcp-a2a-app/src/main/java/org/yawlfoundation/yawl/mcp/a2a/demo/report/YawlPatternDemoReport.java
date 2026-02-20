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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aggregate results report for all YAWL pattern executions.
 *
 * <p>This class collects and summarizes execution results from multiple
 * workflow pattern demonstrations, providing:
 * <ul>
 *   <li>Overall success/failure statistics</li>
 *   <li>Per-category breakdowns with metrics</li>
 *   <li>Token savings analysis (YAML vs XML)</li>
 *   <li>Performance timing aggregation</li>
 *   <li>Failure diagnostics</li>
 * </ul></p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlPatternDemoReport {

    private final int totalPatterns;
    private final int successfulPatterns;
    private final int failedPatterns;
    private final Duration totalTime;
    private final Instant generatedAt;
    private final List<PatternResult> results;
    private final Map<PatternCategory, CategorySummary> summaryByCategory;

    /**
     * Create a new demo report from execution results.
     *
     * @param results the list of pattern execution results
     */
    public YawlPatternDemoReport(List<PatternResult> results) {
        this(results, Instant.now());
    }

    /**
     * Create a new demo report with explicit generation timestamp.
     *
     * @param results     the list of pattern execution results
     * @param generatedAt the timestamp when this report was generated
     */
    public YawlPatternDemoReport(List<PatternResult> results, Instant generatedAt) {
        Objects.requireNonNull(results, "Results list cannot be null");
        Objects.requireNonNull(generatedAt, "Generated timestamp cannot be null");

        this.results = Collections.unmodifiableList(new ArrayList<>(results));
        this.generatedAt = generatedAt;

        // Calculate totals
        this.totalPatterns = results.size();
        this.successfulPatterns = (int) results.stream().filter(PatternResult::isSuccess).count();
        this.failedPatterns = totalPatterns - successfulPatterns;

        // Calculate total duration
        this.totalTime = results.stream()
            .map(PatternResult::getDuration)
            .filter(Objects::nonNull)
            .reduce(Duration.ZERO, Duration::plus);

        // Build category summaries
        this.summaryByCategory = buildCategorySummaries(results);
    }

    /**
     * Get the total number of patterns executed.
     *
     * @return total pattern count
     */
    public int getTotalPatterns() {
        return totalPatterns;
    }

    /**
     * Get the number of successful pattern executions.
     *
     * @return successful pattern count
     */
    public int getSuccessfulPatterns() {
        return successfulPatterns;
    }

    /**
     * Get the number of failed pattern executions.
     *
     * @return failed pattern count
     */
    public int getFailedPatterns() {
        return failedPatterns;
    }

    /**
     * Get the total execution time across all patterns.
     *
     * @return total duration
     */
    public Duration getTotalTime() {
        return totalTime;
    }

    /**
     * Get the timestamp when this report was generated.
     *
     * @return generation timestamp
     */
    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Get all pattern execution results.
     *
     * @return unmodifiable list of results
     */
    public List<PatternResult> getResults() {
        return results;
    }

    /**
     * Get summary statistics grouped by pattern category.
     *
     * @return map of category to summary statistics
     */
    public Map<PatternCategory, CategorySummary> getSummaryByCategory() {
        return Collections.unmodifiableMap(summaryByCategory);
    }

    /**
     * Calculate the overall success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        if (totalPatterns == 0) {
            return 0.0;
        }
        return (successfulPatterns * 100.0) / totalPatterns;
    }

    /**
     * Calculate total token savings across all patterns.
     *
     * <p>Compares YAML token counts to XML token counts to determine
     * overall compression efficiency.</p>
     *
     * @return total token savings as a percentage (0.0 to 100.0)
     */
    public double getTotalTokenSavings() {
        long totalYamlTokens = 0;
        long totalXmlTokens = 0;

        for (PatternResult result : results) {
            PatternResult.TokenAnalysis analysis = result.getTokenAnalysis();
            if (analysis != null) {
                totalYamlTokens += analysis.getYamlTokens();
                totalXmlTokens += analysis.getXmlTokens();
            }
        }

        if (totalXmlTokens == 0) {
            return 0.0;
        }

        return (1.0 - (double) totalYamlTokens / totalXmlTokens) * 100.0;
    }

    /**
     * Calculate total YAML tokens across all patterns.
     *
     * @return total YAML token count
     */
    public long getTotalYamlTokens() {
        return results.stream()
            .map(PatternResult::getTokenAnalysis)
            .filter(Objects::nonNull)
            .mapToLong(PatternResult.TokenAnalysis::getYamlTokens)
            .sum();
    }

    /**
     * Calculate total XML tokens across all patterns.
     *
     * @return total XML token count
     */
    public long getTotalXmlTokens() {
        return results.stream()
            .map(PatternResult::getTokenAnalysis)
            .filter(Objects::nonNull)
            .mapToLong(PatternResult.TokenAnalysis::getXmlTokens)
            .sum();
    }

    /**
     * Calculate the overall compression ratio (XML/YAML).
     *
     * @return compression ratio (e.g., 3.7 means XML is 3.7x larger)
     */
    public double getCompressionRatio() {
        long yamlTokens = getTotalYamlTokens();
        long xmlTokens = getTotalXmlTokens();

        if (yamlTokens == 0) {
            return 0.0;
        }

        return (double) xmlTokens / yamlTokens;
    }

    /**
     * Calculate the average execution duration per pattern.
     *
     * @return average duration
     */
    public Duration getAverageDuration() {
        if (totalPatterns == 0) {
            return Duration.ZERO;
        }
        return totalTime.dividedBy(totalPatterns);
    }

    /**
     * Get all failed pattern executions.
     *
     * @return list of failed results, sorted by pattern ID
     */
    public List<PatternResult> getFailures() {
        return results.stream()
            .filter(r -> !r.isSuccess())
            .sorted(Comparator.comparing(PatternResult::getPatternId))
            .collect(Collectors.toList());
    }

    /**
     * Get all successful pattern executions.
     *
     * @return list of successful results, sorted by pattern ID
     */
    public List<PatternResult> getSuccesses() {
        return results.stream()
            .filter(PatternResult::isSuccess)
            .sorted(Comparator.comparing(PatternResult::getPatternId))
            .collect(Collectors.toList());
    }

    /**
     * Get patterns sorted by execution duration (longest first).
     *
     * @return list of results sorted by duration descending
     */
    public List<PatternResult> getResultsByDuration() {
        return results.stream()
            .sorted(Comparator.comparing(PatternResult::getDuration).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get patterns sorted by token savings (highest savings first).
     *
     * @return list of results sorted by token savings descending
     */
    public List<PatternResult> getResultsByTokenSavings() {
        return results.stream()
            .filter(r -> r.getTokenAnalysis() != null)
            .sorted((a, b) -> Double.compare(
                b.getTokenAnalysis().getSavingsPercentage(),
                a.getTokenAnalysis().getSavingsPercentage()))
            .collect(Collectors.toList());
    }

    /**
     * Check if all patterns executed successfully.
     *
     * @return true if all patterns succeeded
     */
    public boolean isAllSuccessful() {
        return failedPatterns == 0 && totalPatterns > 0;
    }

    /**
     * Get a summary string for display purposes.
     *
     * @return brief summary of the report
     */
    public String getSummary() {
        return String.format("%d patterns: %d passed, %d failed (%.1f%% success rate)",
            totalPatterns, successfulPatterns, failedPatterns, getSuccessRate());
    }

    /**
     * Get formatted total time string.
     *
     * @return human-readable duration string
     */
    public String getFormattedTotalTime() {
        if (totalTime.toMinutes() > 0) {
            return String.format("%d min %d.%03d sec",
                totalTime.toMinutes(),
                totalTime.toSecondsPart(),
                totalTime.toMillisPart());
        } else if (totalTime.toSeconds() > 0) {
            return String.format("%d.%03d sec",
                totalTime.toSeconds(),
                totalTime.toMillisPart());
        } else {
            return String.format("%d ms", totalTime.toMillis());
        }
    }

    /**
     * Build category summary map from results.
     */
    private Map<PatternCategory, CategorySummary> buildCategorySummaries(List<PatternResult> results) {
        Map<PatternCategory, List<PatternResult>> groupedByCategory = new EnumMap<>(PatternCategory.class);

        for (PatternResult result : results) {
            PatternCategory category = extractCategory(result);
            groupedByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(result);
        }

        Map<PatternCategory, CategorySummary> summaries = new EnumMap<>(PatternCategory.class);
        for (Map.Entry<PatternCategory, List<PatternResult>> entry : groupedByCategory.entrySet()) {
            summaries.put(entry.getKey(), new CategorySummary(entry.getValue()));
        }

        return summaries;
    }

    /**
     * Extract category from pattern result.
     */
    private PatternCategory extractCategory(PatternResult result) {
        if (result == null) {
            return PatternCategory.UNCLASSIFIED;
        }

        // Try to get category from pattern info
        PatternResult.PatternInfo info = result.getPatternInfo();
        if (info != null && info.category() != null) {
            // Convert from nested PatternCategory to config PatternCategory
            String categoryName = info.category().name();
            try {
                return PatternCategory.fromDisplayName(categoryName);
            } catch (Exception e) {
                // Fall through to ID-based detection
            }
        }

        // Fall back to pattern ID-based detection
        return PatternCategory.fromPatternId(result.getPatternId());
    }

    /**
     * Summary statistics for a single pattern category.
     */
    public static class CategorySummary {

        private final int count;
        private final int success;
        private final int failure;
        private final Duration totalTime;
        private final Duration avgTime;
        private final double tokenSavings;
        private final double successRate;
        private final long yamlTokens;
        private final long xmlTokens;

        /**
         * Create a category summary from a list of results.
         *
         * @param results the results in this category
         */
        public CategorySummary(List<PatternResult> results) {
            Objects.requireNonNull(results, "Results cannot be null");

            this.count = results.size();
            this.success = (int) results.stream().filter(PatternResult::isSuccess).count();
            this.failure = count - success;
            this.successRate = count > 0 ? (success * 100.0) / count : 0.0;

            this.totalTime = results.stream()
                .map(PatternResult::getDuration)
                .filter(Objects::nonNull)
                .reduce(Duration.ZERO, Duration::plus);

            this.avgTime = count > 0 ? totalTime.dividedBy(count) : Duration.ZERO;

            this.yamlTokens = results.stream()
                .map(PatternResult::getTokenAnalysis)
                .filter(Objects::nonNull)
                .mapToLong(PatternResult.TokenAnalysis::getYamlTokens)
                .sum();

            this.xmlTokens = results.stream()
                .map(PatternResult::getTokenAnalysis)
                .filter(Objects::nonNull)
                .mapToLong(PatternResult.TokenAnalysis::getXmlTokens)
                .sum();

            this.tokenSavings = xmlTokens > 0 ?
                (1.0 - (double) yamlTokens / xmlTokens) * 100.0 : 0.0;
        }

        /**
         * Get the total number of patterns in this category.
         *
         * @return pattern count
         */
        public int getCount() {
            return count;
        }

        /**
         * Get the number of successful executions.
         *
         * @return success count
         */
        public int getSuccess() {
            return success;
        }

        /**
         * Get the number of failed executions.
         *
         * @return failure count
         */
        public int getFailure() {
            return failure;
        }

        /**
         * Get the total execution time for this category.
         *
         * @return total duration
         */
        public Duration getTotalTime() {
            return totalTime;
        }

        /**
         * Get the average execution duration.
         *
         * @return average duration
         */
        public Duration getAvgTime() {
            return avgTime;
        }

        /**
         * Get the token savings percentage for this category.
         *
         * @return token savings (0.0 to 100.0)
         */
        public double getTokenSavings() {
            return tokenSavings;
        }

        /**
         * Get the success rate for this category.
         *
         * @return success rate (0.0 to 100.0)
         */
        public double getSuccessRate() {
            return successRate;
        }

        /**
         * Get total YAML tokens for this category.
         *
         * @return YAML token count
         */
        public long getYamlTokens() {
            return yamlTokens;
        }

        /**
         * Get total XML tokens for this category.
         *
         * @return XML token count
         */
        public long getXmlTokens() {
            return xmlTokens;
        }

        /**
         * Get formatted average time string.
         *
         * @return human-readable average duration
         */
        public String getFormattedAvgTime() {
            if (avgTime.toMinutes() > 0) {
                return String.format("%d min %d sec", avgTime.toMinutes(), avgTime.toSecondsPart());
            } else if (avgTime.toSeconds() > 0) {
                return String.format("%d.%03d sec", avgTime.toSeconds(), avgTime.toMillisPart());
            } else {
                return String.format("%d ms", avgTime.toMillis());
            }
        }

        @Override
        public String toString() {
            return String.format("CategorySummary{count=%d, success=%d, failure=%d, avgTime=%s, tokenSavings=%.1f%%}",
                count, success, failure, getFormattedAvgTime(), tokenSavings);
        }
    }

    /**
     * Builder for constructing reports incrementally.
     */
    public static class Builder {

        private final List<PatternResult> results = new ArrayList<>();
        private Instant generatedAt = Instant.now();

        /**
         * Add a pattern result to the report.
         *
         * @param result the result to add
         * @return this builder for chaining
         */
        public Builder addResult(PatternResult result) {
            Objects.requireNonNull(result, "Result cannot be null");
            results.add(result);
            return this;
        }

        /**
         * Add multiple pattern results to the report.
         *
         * @param results the results to add
         * @return this builder for chaining
         */
        public Builder addResults(List<PatternResult> results) {
            Objects.requireNonNull(results, "Results cannot be null");
            this.results.addAll(results);
            return this;
        }

        /**
         * Set the generation timestamp.
         *
         * @param generatedAt the timestamp
         * @return this builder for chaining
         */
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        /**
         * Build the final report.
         *
         * @return the constructed report
         */
        public YawlPatternDemoReport build() {
            return new YawlPatternDemoReport(results, generatedAt);
        }
    }

    /**
     * Create a new builder for constructing reports.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
