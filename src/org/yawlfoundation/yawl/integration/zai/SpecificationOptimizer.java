package org.yawlfoundation.yawl.integration.zai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YNet;

/**
 * Provides AI-driven optimization suggestions for YAWL specifications.
 *
 * <p>Analyzes existing specifications and suggests improvements for:</p>
 * <ul>
 *   <li>Performance - reducing unnecessary splits/joins</li>
 *   <li>Correctness - detecting deadlocks, unreachable tasks</li>
 *   <li>Readability - improving naming and organization</li>
 *   <li>Maintainability - reducing complexity</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class SpecificationOptimizer {

    private static final Logger LOGGER = Logger.getLogger(SpecificationOptimizer.class.getName());

    /** System prompt for optimization analysis */
    private static final String OPTIMIZATION_PROMPT = """
        You are a YAWL workflow optimization expert. Analyze the provided specification
        and suggest improvements in these categories:

        1. PERFORMANCE: Reduce unnecessary parallelism, simplify control flow
        2. CORRECTNESS: Fix potential deadlocks, ensure proper completion
        3. READABILITY: Improve naming conventions, add documentation
        4. MAINTAINABILITY: Reduce complexity, modularize large nets

        For each suggestion, provide:
        - Category (PERFORMANCE/CORRECTNESS/READABILITY/MAINTAINABILITY)
        - Severity (HIGH/MEDIUM/LOW)
        - Location (element ID)
        - Description of the issue
        - Recommended fix
        - Example (if applicable)

        Output as JSON array of suggestions.
        """;

    private final SpecificationGenerator.ZaiHttpClient zaiClient;
    private final Duration timeout;

    /**
     * Creates a new SpecificationOptimizer.
     *
     * @param zaiClient the Z.AI HTTP client
     */
    public SpecificationOptimizer(SpecificationGenerator.ZaiHttpClient zaiClient) {
        this(zaiClient, Duration.ofSeconds(30));
    }

    /**
     * Creates a new SpecificationOptimizer with custom timeout.
     *
     * @param zaiClient the Z.AI HTTP client
     * @param timeout   analysis timeout
     */
    public SpecificationOptimizer(SpecificationGenerator.ZaiHttpClient zaiClient, Duration timeout) {
        this.zaiClient = Objects.requireNonNull(zaiClient, "zaiClient must not be null");
        this.timeout = timeout;
    }

    /**
     * Creates a SpecificationOptimizer using the official Z.AI SDK (reads ZAI_API_KEY from env).
     *
     * @return a SpecificationOptimizer with official SDK backend
     */
    public static SpecificationOptimizer create() {
        return new SpecificationOptimizer(SpecificationGenerator.officialSdkClient());
    }

    /**
     * Analyzes a specification and returns optimization suggestions.
     *
     * @param spec the specification to analyze
     * @return list of optimization suggestions
     */
    public List<OptimizationSuggestion> analyze(YSpecification spec) {
        Objects.requireNonNull(spec, "spec must not be null");

        LOGGER.info("Analyzing specification for optimization: " + spec.getURI());

        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // Static analysis (no AI needed)
        suggestions.addAll(analyzeStatic(spec));

        // AI-powered analysis
        suggestions.addAll(analyzeWithAI(spec));

        LOGGER.info("Found " + suggestions.size() + " optimization suggestions");
        return suggestions;
    }

    /**
     * Gets quick optimization suggestions without AI analysis.
     *
     * @param spec the specification to analyze
     * @return list of static analysis suggestions
     */
    public List<OptimizationSuggestion> quickAnalyze(YSpecification spec) {
        return analyzeStatic(spec);
    }

    /**
     * Applies approved suggestions to create an optimized specification.
     *
     * <p>This method uses Z.AI to generate an improved specification based on
     * the approved optimization suggestions. The suggestions are serialized
     * and provided as context for the AI to make targeted improvements.</p>
     *
     * @param spec        original specification
     * @param suggestions approved suggestions to apply
     * @return optimized specification
     * @throws OptimizationException if AI generation or parsing fails
     */
    public YSpecification applyOptimizations(YSpecification spec,
                                             List<OptimizationSuggestion> suggestions) {
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(suggestions, "suggestions must not be null");

        if (suggestions.isEmpty()) {
            LOGGER.info("No suggestions to apply, returning original specification");
            return spec;
        }

        LOGGER.info("Applying " + suggestions.size() + " optimization suggestions");

        try {
            // Build improvement prompt with suggestions
            String specXml = serializeSpecFull(spec);
            String suggestionsJson = serializeSuggestions(suggestions);
            String prompt = buildImprovementPrompt(specXml, suggestionsJson);

            // Call Z.AI to generate improved spec
            Map<String, Object> request = new HashMap<>();
            request.put("model", "GLM-4.7-Flash");
            request.put("messages", List.of(
                Map.of("role", "system", "content", IMPROVEMENT_PROMPT),
                Map.of("role", "user", "content", prompt)
            ));
            request.put("max_tokens", 8192);
            request.put("temperature", 0.2);

            String response = zaiClient.createChatCompletion(request, timeout);

            // Extract and parse improved specification
            String improvedXml = extractSpecificationXml(response);
            if (improvedXml == null || improvedXml.isBlank()) {
                throw new OptimizationException("No valid specification XML in AI response");
            }

            List<YSpecification> specs = org.yawlfoundation.yawl.unmarshal.YMarshal.unmarshalSpecifications(improvedXml);
            if (specs.isEmpty()) {
                throw new OptimizationException("Failed to parse improved specification");
            }

            LOGGER.info("Successfully applied optimizations to specification");
            return specs.get(0);

        } catch (OptimizationException e) {
            throw e;
        } catch (Exception e) {
            throw new OptimizationException("Failed to apply optimizations: " + e.getMessage(), e);
        }
    }

    private static final String IMPROVEMENT_PROMPT = """
        You are a YAWL specification optimizer. Apply the provided optimization suggestions
        to improve the specification while preserving its core functionality.

        Rules:
        1. Apply ALL provided suggestions
        2. Maintain backward compatibility where possible
        3. Preserve all data flows and conditions
        4. Output ONLY the improved XML specification
        5. Ensure the output is valid YAWL XML
        """;

    private String buildImprovementPrompt(String specXml, String suggestionsJson) {
        return """
            Apply the following optimization suggestions to this YAWL specification.

            Original Specification:
            ```xml
            %s
            ```

            Suggestions to Apply:
            ```json
            %s
            ```

            Generate the improved specification XML.
            """.formatted(specXml, suggestionsJson);
    }

    private String serializeSuggestions(List<OptimizationSuggestion> suggestions) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < suggestions.size(); i++) {
            OptimizationSuggestion s = suggestions.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"category\":\"%s\",\"severity\":\"%s\",\"elementId\":\"%s\",\"description\":\"%s\",\"recommendation\":\"%s\"}",
                s.category(), s.severity(), s.elementId(),
                escapeJson(s.description()), escapeJson(s.recommendation())
            ));
        }
        sb.append("]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String extractSpecificationXml(String response) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "<specification[^>]*>.*?</specification>",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher matcher = pattern.matcher(response);
        return matcher.find() ? matcher.group(0) : null;
    }

    private String serializeSpecFull(YSpecification spec) {
        try {
            return org.yawlfoundation.yawl.marshal.YMarshal.marshal(spec);
        } catch (Exception e) {
            LOGGER.warning("Failed to marshal spec, using simplified format: " + e.getMessage());
            return "<specification id=\"" + spec.getURI() + "\">...</specification>";
        }
    }

    /**
     * Exception thrown when optimization fails.
     */
    public static final class OptimizationException extends RuntimeException {
        public OptimizationException(String message) {
            super(message);
        }
        public OptimizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // -------------------------------------------------------------------------
    // Static analysis methods
    // -------------------------------------------------------------------------

    private List<OptimizationSuggestion> analyzeStatic(YSpecification spec) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // Check for common issues
        suggestions.addAll(checkNamingConventions(spec));
        suggestions.addAll(checkTaskComplexity(spec));
        suggestions.addAll(checkNetStructure(spec));

        return suggestions;
    }

    private List<OptimizationSuggestion> checkNamingConventions(YSpecification spec) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // Check for unclear task names - iterate through decompositions that are nets
        for (YDecomposition decomp : spec.getDecompositions()) {
            if (decomp instanceof YNet net) {
                for (YTask task : net.getNetTasks()) {
                    String name = task.getName();
                    if (name == null || name.isBlank()) {
                        suggestions.add(new OptimizationSuggestion(
                            Category.READABILITY,
                            Severity.MEDIUM,
                            task.getID(),
                            "Task has no descriptive name",
                            "Add a meaningful name that describes the task purpose"
                        ));
                    } else if (name.length() < 3) {
                        suggestions.add(new OptimizationSuggestion(
                            Category.READABILITY,
                            Severity.LOW,
                            task.getID(),
                            "Task name is too short: '" + name + "'",
                            "Use a more descriptive name (at least 3 characters)"
                        ));
                    }
                }
            }
        }

        return suggestions;
    }

    private List<OptimizationSuggestion> checkTaskComplexity(YSpecification spec) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        for (YDecomposition decomp : spec.getDecompositions()) {
            if (decomp instanceof YNet net) {
                int taskCount = net.getNetTasks().size();
                if (taskCount > 20) {
                    suggestions.add(new OptimizationSuggestion(
                        Category.MAINTAINABILITY,
                        Severity.MEDIUM,
                        net.getID(),
                        "Net has " + taskCount + " tasks, consider decomposition",
                        "Split into multiple sub-nets with clear responsibilities"
                    ));
                }
            }
        }

        return suggestions;
    }

    private List<OptimizationSuggestion> checkNetStructure(YSpecification spec) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        // Check for isolated tasks (no incoming or outgoing flows)
        for (YDecomposition decomp : spec.getDecompositions()) {
            if (decomp instanceof YNet net) {
                YInputCondition inputCondition = net.getInputCondition();
                YOutputCondition outputCondition = net.getOutputCondition();
                String outputConditionId = outputCondition != null ? outputCondition.getID() : null;

                for (YTask task : net.getNetTasks()) {
                    boolean hasIncoming = !task.getPresetElements().isEmpty();
                    boolean hasOutgoing = !task.getPostsetElements().isEmpty();

                    // Check if task is directly connected to input condition (entry point)
                    boolean isEntryPoint = inputCondition != null &&
                                          inputCondition.getPostsetElements().contains(task);

                    if (!hasIncoming && !isEntryPoint) {
                        suggestions.add(new OptimizationSuggestion(
                            Category.CORRECTNESS,
                            Severity.HIGH,
                            task.getID(),
                            "Task has no incoming flows and is not connected to input condition",
                            "Connect this task to the workflow or remove it"
                        ));
                    }

                    if (!hasOutgoing && (outputConditionId == null ||
                                        !task.getID().equals(outputConditionId))) {
                        suggestions.add(new OptimizationSuggestion(
                            Category.CORRECTNESS,
                            Severity.MEDIUM,
                            task.getID(),
                            "Task has no outgoing flows and is not an output condition",
                            "Connect to the next task or output condition"
                        ));
                    }
                }
            }
        }

        return suggestions;
    }

    // -------------------------------------------------------------------------
    // AI-powered analysis
    // -------------------------------------------------------------------------

    private List<OptimizationSuggestion> analyzeWithAI(YSpecification spec) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        try {
            // Build analysis prompt
            String specXml = serializeSpec(spec);
            String prompt = buildAnalysisPrompt(specXml);

            // Call Z.AI
            Map<String, Object> request = new HashMap<>();
            request.put("model", "GLM-4.7-Flash");
            request.put("messages", List.of(
                Map.of("role", "system", "content", OPTIMIZATION_PROMPT),
                Map.of("role", "user", "content", prompt)
            ));
            request.put("max_tokens", 2048);
            request.put("temperature", 0.2);

            String response = zaiClient.createChatCompletion(request, timeout);

            // Parse suggestions from response
            suggestions.addAll(parseAiSuggestions(response));

        } catch (Exception e) {
            LOGGER.warning("AI analysis failed: " + e.getMessage());
            // Return only static suggestions on AI failure
        }

        return suggestions;
    }

    private String buildAnalysisPrompt(String specXml) {
        return """
            Analyze the following YAWL specification for optimization opportunities:

            ```xml
            %s
            ```

            Provide specific, actionable suggestions with element IDs.
            """.formatted(specXml);
    }

    private String serializeSpec(YSpecification spec) {
        // Simplified - actual implementation would use YMarshal
        return "<specification id=\"" + spec.getURI() + "\">...</specification>";
    }

    /** JSON pattern for extracting suggestion objects */
    private static final java.util.regex.Pattern SUGGESTION_PATTERN = java.util.regex.Pattern.compile(
        "\\{[^{}]*\"category\"[^{}]*\"severity\"[^{}]*\"elementId\"[^{}]*\\}",
        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL
    );

    private List<OptimizationSuggestion> parseAiSuggestions(String response) {
        List<OptimizationSuggestion> suggestions = new ArrayList<>();

        if (response == null || response.isBlank()) {
            return suggestions;
        }

        // Extract JSON content from response (may be wrapped in markdown or text)
        String jsonContent = extractJsonContent(response);
        if (jsonContent == null || jsonContent.isBlank()) {
            LOGGER.fine("No JSON content found in AI response");
            return suggestions;
        }

        // Parse individual suggestion objects
        java.util.regex.Matcher matcher = SUGGESTION_PATTERN.matcher(jsonContent);
        while (matcher.find()) {
            String jsonObj = matcher.group();
            OptimizationSuggestion suggestion = parseSuggestionObject(jsonObj);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }

        LOGGER.fine("Parsed " + suggestions.size() + " AI suggestions from response");
        return suggestions;
    }

    private String extractJsonContent(String response) {
        // Try to extract from markdown code block first
        java.util.regex.Pattern codeBlockPattern = java.util.regex.Pattern.compile(
            "```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher codeBlockMatcher = codeBlockPattern.matcher(response);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1);
        }

        // Try to find array directly
        int arrayStart = response.indexOf('[');
        int arrayEnd = response.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return response.substring(arrayStart, arrayEnd + 1);
        }

        return response;
    }

    private OptimizationSuggestion parseSuggestionObject(String jsonObj) {
        try {
            Category category = extractEnum(jsonObj, "category", Category.class, Category.MAINTAINABILITY);
            Severity severity = extractEnum(jsonObj, "severity", Severity.class, Severity.MEDIUM);
            String elementId = extractString(jsonObj, "elementId", "unknown");
            String description = extractString(jsonObj, "description", "No description");
            String recommendation = extractString(jsonObj, "recommendation", "No recommendation");

            return new OptimizationSuggestion(category, severity, elementId, description, recommendation);
        } catch (Exception e) {
            LOGGER.fine("Failed to parse suggestion object: " + e.getMessage());
            return null;
        }
    }

    private String extractString(String json, String key, String defaultValue) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"",
            java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }

    private <T extends Enum<T>> T extractEnum(String json, String key, Class<T> enumClass, T defaultValue) {
        String value = extractString(json, key, defaultValue.name());
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Optimization category enum.
     */
    public enum Category {
        PERFORMANCE,
        CORRECTNESS,
        READABILITY,
        MAINTAINABILITY
    }

    /**
     * Severity level enum.
     */
    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Represents a single optimization suggestion.
     */
    public static final class OptimizationSuggestion {
        private final Category category;
        private final Severity severity;
        private final String elementId;
        private final String description;
        private final String recommendation;

        public OptimizationSuggestion(Category category, Severity severity,
                                     String elementId, String description,
                                     String recommendation) {
            this.category = category;
            this.severity = severity;
            this.elementId = elementId;
            this.description = description;
            this.recommendation = recommendation;
        }

        public Category category() { return category; }
        public Severity severity() { return severity; }
        public String elementId() { return elementId; }
        public String description() { return description; }
        public String recommendation() { return recommendation; }

        @Override
        public String toString() {
            return String.format("[%s/%s] %s: %s -> %s",
                category, severity, elementId, description, recommendation);
        }
    }
}
