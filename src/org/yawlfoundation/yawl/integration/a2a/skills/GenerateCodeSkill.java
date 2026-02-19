package org.yawlfoundation.yawl.integration.a2a.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * A2A Skill for Z.AI-powered code generation.
 *
 * <p>Generates code changes using Z.AI GLM-4.7-Flash model with
 * function calling for YAWL-specific patterns. Supports:
 * <ul>
 *   <li>YAWL specification generation from natural language</li>
 *   <li>Java code generation following HYPER_STANDARDS</li>
 *   <li>Test generation with Chicago TDD patterns</li>
 * </ul>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code prompt} - Natural language description of what to generate</li>
 *   <li>{@code context} - Optional context (file paths, patterns to follow)</li>
 *   <li>{@code type} - Generation type: "specification", "java", "test"</li>
 *   <li>{@code target_path} - Optional target file path for the output</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code code:write}
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class GenerateCodeSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(GenerateCodeSkill.class);
    private static final String SKILL_ID = "generate_code";
    private static final String SKILL_NAME = "Generate Code";
    private static final String SKILL_DESCRIPTION =
        "Generate code using Z.AI GLM-4.7-Flash with YAWL-specific patterns. " +
        "Supports specification generation, Java code, and tests.";

    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "specification", "java", "test", "config"
    );

    private final ZaiFunctionService zaiService;
    private final Path projectRoot;

    /**
     * Create skill with Z.AI service.
     *
     * @param zaiService   the Z.AI function service for code generation
     * @param projectRoot  the project root directory
     */
    public GenerateCodeSkill(ZaiFunctionService zaiService, Path projectRoot) {
        this.zaiService = zaiService;
        this.projectRoot = projectRoot != null ? projectRoot : Path.of(".");
    }

    /**
     * Create skill with default project root.
     *
     * @param zaiService the Z.AI function service
     */
    public GenerateCodeSkill(ZaiFunctionService zaiService) {
        this(zaiService, Path.of("."));
    }

    @Override
    public String getId() {
        return SKILL_ID;
    }

    @Override
    public String getName() {
        return SKILL_NAME;
    }

    @Override
    public String getDescription() {
        return SKILL_DESCRIPTION;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("code:write");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String prompt = request.getParameter("prompt");
        if (prompt == null || prompt.isEmpty()) {
            return SkillResult.error("Parameter 'prompt' is required");
        }

        String type = request.getParameter("type", "java");
        if (!SUPPORTED_TYPES.contains(type)) {
            return SkillResult.error(
                "Unsupported type: " + type + ". Supported: " + SUPPORTED_TYPES);
        }

        String context = request.getParameter("context", "");
        String targetPath = request.getParameter("target_path");

        _logger.info("Generating {} code with prompt: {}", type, prompt.substring(0, Math.min(100, prompt.length())));

        long startTime = System.currentTimeMillis();

        try {
            String systemPrompt = buildSystemPrompt(type);
            String fullPrompt = buildFullPrompt(prompt, context, type);

            String generatedCode = generateWithZai(systemPrompt, fullPrompt);

            Map<String, Object> result = new HashMap<>();
            result.put("code", generatedCode);
            result.put("type", type);
            result.put("generated_at", Instant.now().toString());

            if (targetPath != null && !targetPath.isEmpty()) {
                Path target = projectRoot.resolve(targetPath);
                writeGeneratedCode(target, generatedCode);
                result.put("written_to", targetPath);
            }

            long executionTime = System.currentTimeMillis() - startTime;
            result.put("execution_time_ms", executionTime);

            _logger.info("Code generation completed in {}ms", executionTime);

            return SkillResult.success(result, executionTime);

        } catch (Exception e) {
            _logger.error("Code generation failed: {}", e.getMessage());
            return SkillResult.error("Generation failed: " + e.getMessage());
        }
    }

    private String buildSystemPrompt(String type) {
        return switch (type) {
            case "specification" -> """
                You are a YAWL specification generator. Generate valid YAWL XML specifications
                following the YAWL_Schema4.0.xsd. Include:
                - Decomposition with proper net elements
                - Input/output ports with correct typing
                - Task decomposition with conditions
                - Cancellation regions where appropriate
                - Follow HYPER_STANDARDS: no TODOs, no mocks, real implementations only.
                """;

            case "java" -> """
                You are a YAWL Java code generator. Generate clean, production-ready Java 25 code:
                - Use records for immutable data
                - Use sealed classes for domain hierarchies
                - Use pattern matching in switches
                - Virtual threads for concurrent operations
                - Scoped values for context propagation
                - Follow HYPER_STANDARDS: 100% type coverage, no mocks, no TODOs.
                - Include proper Javadoc and package-info.java content.
                """;

            case "test" -> """
                You are a YAWL test generator using Chicago School TDD. Generate JUnit 5 tests:
                - Use @Test and @ParameterizedTest annotations
                - Test behavior, not implementation
                - Include edge cases and error scenarios
                - Use meaningful test names that describe the behavior
                - 80%+ coverage target
                - Follow HYPER_STANDARDS: real dependencies only, no mocks.
                """;

            case "config" -> """
                You are a YAWL configuration generator. Generate valid configuration files:
                - YAML or Properties format as requested
                - Include all required fields
                - Add helpful comments for complex settings
                - Follow security best practices (no hardcoded secrets)
                """;

            default -> "Generate production-ready code following YAWL standards.";
        };
    }

    private String buildFullPrompt(String prompt, String context, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate ").append(type).append(" code.\n\n");
        sb.append("Request: ").append(prompt).append("\n\n");

        if (context != null && !context.isEmpty()) {
            sb.append("Context:\n").append(context).append("\n\n");
        }

        sb.append("Requirements:\n");
        sb.append("1. No TODO/FIXME/HACK comments\n");
        sb.append("2. No mock/stub/placeholder code\n");
        sb.append("3. Real implementations only\n");
        sb.append("4. Full type annotations\n");
        sb.append("5. Proper error handling\n");

        return sb.toString();
    }

    private String generateWithZai(String systemPrompt, String fullPrompt) throws IOException {
        if (zaiService == null) {
            throw new IllegalStateException("Z.AI service not configured. Set ZAI_API_KEY environment variable.");
        }

        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", fullPrompt)
        );

        return zaiService.processWithFunctions(fullPrompt);
    }

    private void writeGeneratedCode(Path target, String code) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, code);
        _logger.info("Wrote generated code to {}", target);
    }

    /**
     * Check if Z.AI service is available.
     *
     * @return true if service is configured
     */
    public boolean isZaiAvailable() {
        return zaiService != null && zaiService.isInitialized();
    }
}
