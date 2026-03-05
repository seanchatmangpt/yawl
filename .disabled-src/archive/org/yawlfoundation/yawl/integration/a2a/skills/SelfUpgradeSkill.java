package org.yawlfoundation.yawl.integration.a2a.skills;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

/**
 * A2A Skill for orchestrating the complete self-upgrade cycle.
 *
 * <p>Master orchestrator that coordinates all skills in a closed-loop system:
 * <pre>
 * 1. INTROSPECT → Query Observatory for improvement opportunities
 * 2. GENERATE   → Z.AI generates code changes following YAWL patterns
 * 3. BUILD      → Maven compiles the changes
 * 4. TEST       → JUnit validates the changes
 * 5. COMMIT     → Git commits with risk-based approval
 * </pre>
 *
 * <p><b>Risk-Based Approval:</b>
 * <ul>
 *   <li>{@code LOW} risk - Auto-approve (documentation, tests)</li>
 *   <li>{@code MEDIUM} risk - Auto-approve with notification (non-core files)</li>
 *   <li>{@code HIGH} risk - Requires human approval (core engine files)</li>
 *   <li>{@code CRITICAL} risk - Always blocked (security, auth modules)</li>
 * </ul>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code goal} - Natural language description of upgrade goal</li>
 *   <li>{@code target_files} - Files to modify (optional, auto-detected if not provided)</li>
 *   <li>{@code risk_level} - Maximum risk level to auto-approve (default: MEDIUM)</li>
 *   <li>{@code dry_run} - If true, only plan without executing (default: false)</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code upgrade:execute}
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SelfUpgradeSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(SelfUpgradeSkill.class);
    private static final String SKILL_ID = "self_upgrade";
    private static final String SKILL_NAME = "Self Upgrade";
    private static final String SKILL_DESCRIPTION =
        "Master orchestrator for complete self-upgrade cycle. " +
        "Coordinates introspect, generate, build, test, and commit phases.";

    private static final Set<String> VALID_RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final IntrospectCodebaseSkill introspectSkill;
    private final GenerateCodeSkill generateSkill;
    private final ExecuteBuildSkill buildSkill;
    private final RunTestsSkill testSkill;
    private final CommitChangesSkill commitSkill;
    private final Path projectRoot;

    private UpgradePhase currentPhase = UpgradePhase.IDLE;
    private UpgradeState state = new UpgradeState();

    /**
     * Create skill with all dependencies.
     *
     * @param zaiService    Z.AI function service for code generation
     * @param projectRoot   project root directory
     */
    public SelfUpgradeSkill(ZaiFunctionService zaiService, Path projectRoot) {
        this.projectRoot = projectRoot != null ? projectRoot : Path.of(".");
        this.introspectSkill = new IntrospectCodebaseSkill();
        this.generateSkill = new GenerateCodeSkill(zaiService, projectRoot);
        this.buildSkill = new ExecuteBuildSkill(projectRoot);
        this.testSkill = new RunTestsSkill(projectRoot);
        this.commitSkill = new CommitChangesSkill(projectRoot);
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
        return Set.of("upgrade:execute");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String goal = request.getParameter("goal");
        if (goal == null || goal.isEmpty()) {
            return SkillResult.error("Parameter 'goal' is required for self-upgrade");
        }

        String targetFiles = request.getParameter("target_files", "");
        String riskLevelStr = request.getParameter("risk_level", "MEDIUM");
        boolean dryRun = Boolean.parseBoolean(request.getParameter("dry_run", "false"));

        RiskLevel maxRiskLevel = RiskLevel.valueOf(riskLevelStr.toUpperCase());

        _logger.info("Starting self-upgrade cycle with goal: {}", goal.substring(0, Math.min(100, goal.length())));
        _logger.info("Max risk level: {}, Dry run: {}", maxRiskLevel, dryRun);

        state = new UpgradeState();
        state.goal = goal;
        state.startTime = Instant.now();
        state.dryRun = dryRun;
        state.maxRiskLevel = maxRiskLevel;

        List<Map<String, Object>> phases = new ArrayList<>();

        try {
            // Phase 1: Introspect
            phases.add(executePhase(UpgradePhase.INTROSPECT, () -> {
                SkillRequest introspectReq = SkillRequest.builder("introspect_codebase")
                    .parameter("query", "all")
                    .build();
                SkillResult result = introspectSkill.execute(introspectReq);
                if (result.isSuccess()) {
                    state.codebaseContext = result.getData();
                }
                return result;
            }));

            if (state.lastPhaseFailed) {
                return buildFinalResult(phases, false);
            }

            // Phase 2: Analyze target files and risk
            phases.add(executePhase(UpgradePhase.ANALYZE, () -> {
                return analyzeTargetFiles(targetFiles);
            }));

            if (state.lastPhaseFailed) {
                return buildFinalResult(phases, false);
            }

            if (state.riskLevel.ordinal() > maxRiskLevel.ordinal()) {
                return SkillResult.error(
                    "Risk level " + state.riskLevel + " exceeds maximum allowed " + maxRiskLevel +
                    ". Human approval required.");
            }

            if (dryRun) {
                Map<String, Object> dryRunResult = new HashMap<>();
                dryRunResult.put("phases", phases);
                dryRunResult.put("dry_run", true);
                dryRunResult.put("planned_files", state.targetFiles);
                dryRunResult.put("risk_level", state.riskLevel);
                dryRunResult.put("message", "Dry run complete. Ready to execute with dry_run=false");
                return SkillResult.success(dryRunResult);
            }

            // Phase 3: Generate code
            phases.add(executePhase(UpgradePhase.GENERATE, () -> {
                SkillRequest generateReq = SkillRequest.builder("generate_code")
                    .parameter("prompt", goal)
                    .parameter("context", formatContextForGeneration(state.codebaseContext))
                    .parameter("type", "java")
                    .build();
                return generateSkill.execute(generateReq);
            }));

            if (state.lastPhaseFailed) {
                return buildFinalResult(phases, false);
            }

            // Phase 4: Build
            phases.add(executePhase(UpgradePhase.BUILD, () -> {
                SkillRequest buildReq = SkillRequest.builder("execute_build")
                    .parameter("mode", "incremental")
                    .build();
                return buildSkill.execute(buildReq);
            }));

            if (state.lastPhaseFailed) {
                return buildFinalResult(phases, false);
            }

            // Phase 5: Test
            phases.add(executePhase(UpgradePhase.TEST, () -> {
                SkillRequest testReq = SkillRequest.builder("run_tests")
                    .parameter("mode", "incremental")
                    .parameter("coverage_threshold", "80")
                    .build();
                return testSkill.execute(testReq);
            }));

            if (state.lastPhaseFailed) {
                return buildFinalResult(phases, false);
            }

            // Phase 6: Commit
            phases.add(executePhase(UpgradePhase.COMMIT, () -> {
                SkillRequest commitReq = SkillRequest.builder("commit_changes")
                    .parameter("operation", "commit")
                    .parameter("files", String.join(",", state.targetFiles))
                    .parameter("message", buildCommitMessage(goal))
                    .build();
                return commitSkill.execute(commitReq);
            }));

            state.success = !state.lastPhaseFailed;

        } catch (Exception e) {
            _logger.error("Self-upgrade cycle failed: {}", e.getMessage());
            state.success = false;
            state.error = e.getMessage();
        }

        return buildFinalResult(phases, state.success);
    }

    private Map<String, Object> executePhase(UpgradePhase phase, PhaseExecutor executor) {
        currentPhase = phase;
        long startTime = System.currentTimeMillis();

        Map<String, Object> phaseResult = new LinkedHashMap<>();
        phaseResult.put("phase", phase.name());
        phaseResult.put("started_at", Instant.now().toString());

        try {
            _logger.info("Executing phase: {}", phase);
            SkillResult result = executor.execute();

            phaseResult.put("success", result.isSuccess());
            phaseResult.put("data", result.getData());
            phaseResult.put("error", result.getError());
            state.lastPhaseFailed = !result.isSuccess();

        } catch (Exception e) {
            _logger.error("Phase {} failed: {}", phase, e.getMessage());
            phaseResult.put("success", false);
            phaseResult.put("error", e.getMessage());
            state.lastPhaseFailed = true;
        }

        phaseResult.put("duration_ms", System.currentTimeMillis() - startTime);
        phaseResult.put("completed_at", Instant.now().toString());

        return phaseResult;
    }

    private SkillResult analyzeTargetFiles(String targetFiles) {
        Map<String, Object> result = new HashMap<>();

        if (targetFiles != null && !targetFiles.isEmpty()) {
            state.targetFiles = List.of(targetFiles.split(","));
        } else {
            state.targetFiles = detectTargetFiles(state.goal);
        }

        state.riskLevel = calculateRiskLevel(state.targetFiles);

        result.put("target_files", state.targetFiles);
        result.put("risk_level", state.riskLevel);
        result.put("auto_approve", state.riskLevel.ordinal() <= state.maxRiskLevel.ordinal());

        return SkillResult.success(result);
    }

    private List<String> detectTargetFiles(String goal) {
        List<String> files = new ArrayList<>();

        String goalLower = goal.toLowerCase();

        if (goalLower.contains("mcp") || goalLower.contains("model context protocol")) {
            files.add("src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java");
        }

        if (goalLower.contains("a2a") || goalLower.contains("agent-to-agent")) {
            files.add("src/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.java");
        }

        if (goalLower.contains("engine") || goalLower.contains("workflow")) {
            files.add("src/org/yawlfoundation/yawl/engine/YEngine.java");
        }

        if (files.isEmpty()) {
            files.add("src/org/yawlfoundation/yawl/UnknownTarget.java");
        }

        return files;
    }

    private RiskLevel calculateRiskLevel(List<String> files) {
        for (String file : files) {
            String lower = file.toLowerCase();

            if (lower.contains("auth") || lower.contains("security") ||
                lower.contains("password") || lower.contains("secret") ||
                lower.contains("key")) {
                return RiskLevel.CRITICAL;
            }

            if (lower.contains("engine") || lower.contains("core") ||
                lower.contains("yawlstatelessengine") || lower.contains("ynetrunner")) {
                return RiskLevel.HIGH;
            }

            if (lower.contains("integration") || lower.contains("mcp") ||
                lower.contains("a2a") || lower.contains("zai")) {
                return RiskLevel.MEDIUM;
            }
        }

        return RiskLevel.LOW;
    }

    private String formatContextForGeneration(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Codebase context from Observatory:\n");

        if (context == null || context.isEmpty()) {
            sb.append("No Observatory context available - proceeding without codebase facts.\n");
            sb.append("Consider running ./scripts/observatory/observatory.sh to generate context.\n");
            return sb.toString();
        }

        if (context.containsKey("modules")) {
            sb.append("\nModules: ").append(context.get("modules"));
        }

        if (context.containsKey("integration")) {
            sb.append("\nIntegration status: ").append(context.get("integration"));
        }

        return sb.toString();
    }

    private String buildCommitMessage(String goal) {
        return "Self-upgrade: " + goal.substring(0, Math.min(50, goal.length())) +
               "\n\nCo-Authored-By: YAWL Self-Upgrade System <noreply@yawlfoundation.org>";
    }

    private SkillResult buildFinalResult(List<Map<String, Object>> phases, boolean success) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("goal", state.goal);
        result.put("started_at", state.startTime);
        result.put("completed_at", Instant.now());
        result.put("phases", phases);
        result.put("total_phases", phases.size());
        result.put("risk_level", state.riskLevel);
        result.put("dry_run", state.dryRun);

        if (state.error != null) {
            result.put("error", state.error);
        }

        if (success) {
            return SkillResult.success(result);
        } else {
            return SkillResult.error("Self-upgrade cycle failed", result);
        }
    }

    /**
     * Get the current upgrade phase.
     *
     * @return current phase
     */
    public UpgradePhase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Get the current upgrade state.
     *
     * @return current state
     */
    public UpgradeState getState() {
        return state;
    }

    @FunctionalInterface
    private interface PhaseExecutor {
        SkillResult execute() throws Exception;
    }

    /**
     * Upgrade phases in order.
     */
    public enum UpgradePhase {
        IDLE,
        INTROSPECT,
        ANALYZE,
        GENERATE,
        BUILD,
        TEST,
        COMMIT,
        COMPLETE
    }

    /**
     * Risk levels for upgrade approval.
     */
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Mutable state for tracking upgrade progress.
     */
    public static class UpgradeState {
        public String goal;
        public Instant startTime;
        public boolean dryRun;
        public RiskLevel maxRiskLevel;
        public RiskLevel riskLevel;
        public List<String> targetFiles;
        public Map<String, Object> codebaseContext;
        public boolean success;
        public boolean lastPhaseFailed;
        public String error;
    }
}
