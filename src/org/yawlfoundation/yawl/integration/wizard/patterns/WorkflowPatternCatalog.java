package org.yawlfoundation.yawl.integration.wizard.patterns;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Complete catalog of van der Aalst's 20 workflow patterns.
 *
 * <p>Provides lookup, filtering, and pattern comparison capabilities.
 * Supports querying patterns by code, category, or suitability.
 *
 * <p>This is a stateless, immutable utility class for pattern discovery
 * and selection. All returned collections are immutable.
 *
 * @see WorkflowPattern for individual pattern definitions
 * @see PatternCategory for pattern groupings
 */
public final class WorkflowPatternCatalog {

    private static final List<WorkflowPattern> ALL_PATTERNS =
        Collections.unmodifiableList(Arrays.asList(WorkflowPattern.values()));

    private static final Map<String, WorkflowPattern> CODE_MAP = WorkflowPattern.codeMap();

    private static final Map<PatternCategory, List<WorkflowPattern>> CATEGORY_MAP =
        buildCategoryMap();

    /**
     * Private constructor: this is a utility class with static methods only.
     */
    private WorkflowPatternCatalog() {
        throw new AssertionError("Cannot instantiate WorkflowPatternCatalog");
    }

    /**
     * Gets all 20 workflow patterns.
     *
     * @return immutable list of all patterns
     */
    public static List<WorkflowPattern> all() {
        return ALL_PATTERNS;
    }

    /**
     * Gets the total count of patterns in the catalog.
     *
     * @return the number of patterns (always 20)
     */
    public static int size() {
        return ALL_PATTERNS.size();
    }

    /**
     * Gets all patterns in a specific category.
     *
     * @param category the pattern category
     * @return immutable list of patterns in that category
     * @throws NullPointerException if category is null
     */
    public static List<WorkflowPattern> byCategory(PatternCategory category) {
        Objects.requireNonNull(category, "category cannot be null");
        return CATEGORY_MAP.getOrDefault(category, List.of());
    }

    /**
     * Gets all patterns suitable for MCP tool orchestration.
     *
     * <p>A pattern is considered suitable if its MCP suitability score >= minSuitability.
     *
     * @param minSuitability the minimum suitability score (0-10)
     * @return immutable list of suitable patterns
     */
    public static List<WorkflowPattern> suitableForMcp(int minSuitability) {
        return ALL_PATTERNS.stream()
            .filter(p -> p.getMcpSuitability() >= minSuitability)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets all patterns suitable for MCP (suitability >= 7).
     *
     * @return immutable list of MCP-suitable patterns
     */
    public static List<WorkflowPattern> suitableForMcp() {
        return suitableForMcp(7);
    }

    /**
     * Gets all patterns suitable for A2A agent coordination.
     *
     * <p>A pattern is considered suitable if its A2A suitability score >= minSuitability.
     *
     * @param minSuitability the minimum suitability score (0-10)
     * @return immutable list of suitable patterns
     */
    public static List<WorkflowPattern> suitableForA2a(int minSuitability) {
        return ALL_PATTERNS.stream()
            .filter(p -> p.getA2aSuitability() >= minSuitability)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Gets all patterns suitable for A2A (suitability >= 7).
     *
     * @return immutable list of A2A-suitable patterns
     */
    public static List<WorkflowPattern> suitableForA2a() {
        return suitableForA2a(7);
    }

    /**
     * Finds a pattern by its code (e.g., "WP-1", "WP-20").
     *
     * @param code the pattern code (case-insensitive)
     * @return optional containing the pattern if found
     * @throws NullPointerException if code is null
     */
    public static Optional<WorkflowPattern> findByCode(String code) {
        Objects.requireNonNull(code, "code cannot be null");
        return Optional.ofNullable(CODE_MAP.get(code.toUpperCase()));
    }

    /**
     * Finds a pattern by its label (e.g., "Sequence", "Parallel Split").
     *
     * @param label the pattern label (case-insensitive)
     * @return optional containing the pattern if found
     * @throws NullPointerException if label is null
     */
    public static Optional<WorkflowPattern> findByLabel(String label) {
        Objects.requireNonNull(label, "label cannot be null");
        String lowerLabel = label.toLowerCase();
        return ALL_PATTERNS.stream()
            .filter(p -> p.getLabel().toLowerCase().equals(lowerLabel))
            .findFirst();
    }

    /**
     * Compares two patterns based on their suitability for the given context.
     *
     * <p>Prefers patterns with higher MCP and A2A suitability scores.
     *
     * @param a the first pattern
     * @param b the second pattern
     * @param forMcp whether to prioritize MCP suitability; if false, prioritizes A2A
     * @return comparison result with explanation
     * @throws NullPointerException if either pattern is null
     */
    public static PatternComparison compare(
        WorkflowPattern a,
        WorkflowPattern b,
        boolean forMcp
    ) {
        Objects.requireNonNull(a, "pattern a cannot be null");
        Objects.requireNonNull(b, "pattern b cannot be null");

        int scoreA = forMcp ? a.getMcpSuitability() : a.getA2aSuitability();
        int scoreB = forMcp ? b.getMcpSuitability() : b.getA2aSuitability();

        WorkflowPattern preferred;
        String reason;
        int score;

        if (scoreA > scoreB) {
            preferred = a;
            reason = String.format(
                "%s has higher %s suitability: %d vs %d",
                a.getLabel(),
                forMcp ? "MCP" : "A2A",
                scoreA,
                scoreB
            );
            score = scoreA - scoreB;
        } else if (scoreB > scoreA) {
            preferred = b;
            reason = String.format(
                "%s has higher %s suitability: %d vs %d",
                b.getLabel(),
                forMcp ? "MCP" : "A2A",
                scoreB,
                scoreA
            );
            score = scoreB - scoreA;
        } else {
            preferred = a;  // Tie: arbitrarily choose first
            reason = String.format(
                "%s and %s have equal %s suitability: %d",
                a.getLabel(),
                b.getLabel(),
                forMcp ? "MCP" : "A2A",
                scoreA
            );
            score = 0;
        }

        return new PatternComparison(preferred, reason, score);
    }

    /**
     * Gets a pattern with the best MCP suitability for a given set of patterns.
     *
     * @param patterns the patterns to evaluate
     * @return the pattern with highest MCP suitability
     * @throws NullPointerException if patterns is null or empty
     */
    public static WorkflowPattern bestForMcp(List<WorkflowPattern> patterns) {
        Objects.requireNonNull(patterns, "patterns cannot be null");
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("patterns list cannot be empty");
        }
        return patterns.stream()
            .max((a, b) -> Integer.compare(a.getMcpSuitability(), b.getMcpSuitability()))
            .orElseThrow();
    }

    /**
     * Gets a pattern with the best A2A suitability for a given set of patterns.
     *
     * @param patterns the patterns to evaluate
     * @return the pattern with highest A2A suitability
     * @throws NullPointerException if patterns is null or empty
     */
    public static WorkflowPattern bestForA2a(List<WorkflowPattern> patterns) {
        Objects.requireNonNull(patterns, "patterns cannot be null");
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException("patterns list cannot be empty");
        }
        return patterns.stream()
            .max((a, b) -> Integer.compare(a.getA2aSuitability(), b.getA2aSuitability()))
            .orElseThrow();
    }

    /**
     * Prints a formatted table of all patterns with their suitability scores.
     *
     * @return formatted string representation of pattern catalog
     */
    public static String printCatalog() {
        StringBuilder sb = new StringBuilder();
        sb.append("Van der Aalst Workflow Pattern Catalog\n");
        sb.append("=====================================\n\n");

        for (PatternCategory category : PatternCategory.values()) {
            List<WorkflowPattern> patternsInCategory = byCategory(category);
            if (!patternsInCategory.isEmpty()) {
                sb.append(category.getDisplayName()).append(":\n");
                for (WorkflowPattern pattern : patternsInCategory) {
                    sb.append(String.format(
                        "  %s: %s (MCP: %d, A2A: %d)\n",
                        pattern.getCode(),
                        pattern.getLabel(),
                        pattern.getMcpSuitability(),
                        pattern.getA2aSuitability()
                    ));
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Result of comparing two patterns.
     *
     * @param preferred the pattern with better suitability
     * @param reason explanation of the preference
     * @param score difference in suitability scores (0 if tied)
     */
    public record PatternComparison(
        WorkflowPattern preferred,
        String reason,
        int score
    ) {
        /**
         * Compact constructor validates fields.
         */
        public PatternComparison {
            Objects.requireNonNull(preferred, "preferred pattern cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
        }

        /**
         * Checks if this comparison resulted in a tie.
         *
         * @return true if score is 0
         */
        public boolean isTie() {
            return score == 0;
        }
    }

    /**
     * Builds a map of categories to patterns.
     */
    private static Map<PatternCategory, List<WorkflowPattern>> buildCategoryMap() {
        return Arrays.stream(WorkflowPattern.values())
            .collect(
                Collectors.groupingBy(
                    WorkflowPattern::getCategory,
                    Collectors.collectingAndThen(
                        Collectors.toList(),
                        Collections::unmodifiableList
                    )
                )
            );
    }
}
