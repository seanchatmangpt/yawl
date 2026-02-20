package org.yawlfoundation.yawl.integration.a2a.skills;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A2A Skill for introspecting the YAWL codebase via the Observatory.
 *
 * <p>Provides 100x context compression by querying pre-computed facts
 * instead of exploring the full codebase. The Observatory contains:
 * <ul>
 *   <li>Module inventory and dependencies</li>
 *   <li>Maven reactor order</li>
 *   <li>Quality gates configuration</li>
 *   <li>Integration status (MCP/A2A/ZAI)</li>
 *   <li>Static analysis results</li>
 * </ul>
 *
 * <p><b>Query Types:</b>
 * <ul>
 *   <li>{@code modules} - Module inventory</li>
 *   <li>{@code reactor} - Maven reactor order</li>
 *   <li>{@code gates} - Quality gates configuration</li>
 *   <li>{@code integration} - MCP/A2A/ZAI integration status</li>
 *   <li>{@code static-analysis} - Aggregated code health</li>
 *   <li>{@code all} - Aggregate all facts</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code code:read}
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class IntrospectCodebaseSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(IntrospectCodebaseSkill.class);
    private static final String SKILL_ID = "introspect_codebase";
    private static final String SKILL_NAME = "Introspect Codebase";
    private static final String SKILL_DESCRIPTION =
        "Query Observatory facts for 100x context compression. " +
        "Returns pre-computed facts about modules, reactor, gates, integration, and code health.";

    private static final Set<String> SUPPORTED_QUERIES = Set.of(
        "modules", "reactor", "gates", "integration",
        "static-analysis", "spotbugs", "pmd", "checkstyle", "all"
    );

    private final Path observatoryRoot;
    private final ObjectMapper objectMapper;

    /**
     * Create skill with default Observatory path.
     */
    public IntrospectCodebaseSkill() {
        this(Path.of("docs/v6/latest"));
    }

    /**
     * Create skill with explicit Observatory path.
     *
     * @param observatoryRoot path to the Observatory root directory
     */
    public IntrospectCodebaseSkill(Path observatoryRoot) {
        this.observatoryRoot = observatoryRoot;
        this.objectMapper = new ObjectMapper();
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
        return Set.of("code:read");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String query = request.getParameter("query");
        if (query == null || query.isEmpty()) {
            query = "all";
        }

        if (!SUPPORTED_QUERIES.contains(query)) {
            return SkillResult.error(
                "Unknown query: " + query + ". Supported: " + SUPPORTED_QUERIES);
        }

        _logger.info("Introspecting codebase with query: {}", query);

        try {
            Map<String, Object> result = executeQuery(query);
            return SkillResult.success(result);
        } catch (IOException e) {
            _logger.error("Failed to introspect codebase: {}", e.getMessage());
            return SkillResult.error("Introspection failed: " + e.getMessage());
        }
    }

    private Map<String, Object> executeQuery(String query) throws IOException {
        Path factsDir = observatoryRoot.resolve("facts");

        // This switch is safe because the caller (execute method) has already validated
        // the query against SUPPORTED_QUERIES. The default case is defensive programming.
        Map<String, Object> result = switch (query) {
            case "modules" -> readJsonFile(factsDir.resolve("modules.json"));
            case "reactor" -> readJsonFile(factsDir.resolve("reactor.json"));
            case "gates" -> readJsonFile(factsDir.resolve("gates.json"));
            case "integration" -> readJsonFile(factsDir.resolve("integration.json"));
            case "static-analysis" -> readJsonFile(factsDir.resolve("static-analysis.json"));
            case "spotbugs" -> readJsonFile(factsDir.resolve("spotbugs-findings.json"));
            case "pmd" -> readJsonFile(factsDir.resolve("pmd-violations.json"));
            case "checkstyle" -> readJsonFile(factsDir.resolve("checkstyle-warnings.json"));
            case "all" -> aggregateAllFacts(factsDir);
            default -> {
                // Should never occur due to validation in execute(), but handle gracefully
                _logger.warn("Unrecognized query in executeQuery: {}. This suggests a validation gap.", query);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Query '" + query + "' is not supported");
                errorResponse.put("supported_queries", SUPPORTED_QUERIES);
                errorResponse.put("hint", "Supported queries: modules, reactor, gates, integration, static-analysis, spotbugs, pmd, checkstyle, all");
                yield errorResponse;
            }
        };
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Fact file not found: " + file);
            error.put("hint", "Run ./scripts/observatory/observatory.sh to generate facts");
            return error;
        }

        String content = Files.readString(file);
        return objectMapper.readValue(content, Map.class);
    }

    private Map<String, Object> aggregateAllFacts(Path factsDir) throws IOException {
        Map<String, Object> aggregated = new HashMap<>();
        aggregated.put("timestamp", Instant.now().toString());

        // Core facts
        aggregated.put("modules", readJsonFile(factsDir.resolve("modules.json")));
        aggregated.put("reactor", readJsonFile(factsDir.resolve("reactor.json")));
        aggregated.put("gates", readJsonFile(factsDir.resolve("gates.json")));
        aggregated.put("integration", readJsonFile(factsDir.resolve("integration.json")));

        // Static analysis summary
        Map<String, Object> staticAnalysis = new HashMap<>();
        staticAnalysis.put("summary", readJsonFile(factsDir.resolve("static-analysis.json")));
        staticAnalysis.put("spotbugs", readJsonFile(factsDir.resolve("spotbugs-findings.json")));
        staticAnalysis.put("pmd", readJsonFile(factsDir.resolve("pmd-violations.json")));
        staticAnalysis.put("checkstyle", readJsonFile(factsDir.resolve("checkstyle-warnings.json")));
        aggregated.put("static_analysis", staticAnalysis);

        return aggregated;
    }

    /**
     * Check if the Observatory is available and up-to-date.
     *
     * @return true if facts are available
     */
    public boolean isObservatoryAvailable() {
        Path factsDir = observatoryRoot.resolve("facts");
        return Files.isDirectory(factsDir) &&
               Files.exists(factsDir.resolve("modules.json"));
    }

    /**
     * Get the list of available fact files.
     *
     * @return set of available fact names
     */
    public Set<String> getAvailableFacts() {
        return SUPPORTED_QUERIES;
    }
}
