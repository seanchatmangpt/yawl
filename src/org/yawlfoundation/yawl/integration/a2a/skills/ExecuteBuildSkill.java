package org.yawlfoundation.yawl.integration.a2a.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A2A Skill for Maven build execution.
 *
 * <p>Executes Maven builds with support for:
 * <ul>
 *   <li>Fast incremental builds via dx.sh script</li>
 *   <li>Full parallel builds with -T flag</li>
 *   <li>Profile selection (fast, agent-dx, analysis)</li>
 *   <li>Timeout and output capture</li>
 * </ul>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code mode} - "incremental" (fast) or "full" (clean compile)</li>
 *   <li>{@code profile} - Maven profile to use (fast, agent-dx, analysis)</li>
 *   <li>{@code modules} - Comma-separated list of specific modules</li>
 *   <li>{@code timeout_seconds} - Build timeout (default 300)</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code build:execute}
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ExecuteBuildSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(ExecuteBuildSkill.class);
    private static final String SKILL_ID = "execute_build";
    private static final String SKILL_NAME = "Execute Build";
    private static final String SKILL_DESCRIPTION =
        "Execute Maven builds with incremental or full mode. " +
        "Supports parallel builds and profile selection.";

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final Set<String> VALID_MODES = Set.of("incremental", "full");
    private static final Set<String> VALID_PROFILES = Set.of("fast", "agent-dx", "analysis", "security");

    /**
     * Validates module names to prevent command injection.
     * Allows: alphanumeric, underscores, hyphens. Comma-separated list.
     */
    private static final Pattern MODULE_NAME_PATTERN = Pattern.compile(
        "^[a-zA-Z][a-zA-Z0-9_-]*(,[a-zA-Z][a-zA-Z0-9_-]*)*$"
    );

    private final Path projectRoot;

    /**
     * Create skill with project root.
     *
     * @param projectRoot the project root directory
     */
    public ExecuteBuildSkill(Path projectRoot) {
        this.projectRoot = projectRoot != null ? projectRoot : Path.of(".");
    }

    /**
     * Create skill with default project root.
     */
    public ExecuteBuildSkill() {
        this(Path.of("."));
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
        return Set.of("build:execute");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String mode = request.getParameter("mode", "incremental");
        if (!VALID_MODES.contains(mode)) {
            return SkillResult.error("Invalid mode: " + mode + ". Valid: " + VALID_MODES);
        }

        String profile = request.getParameter("profile", "");
        if (!profile.isEmpty() && !VALID_PROFILES.contains(profile)) {
            return SkillResult.error("Invalid profile: " + profile + ". Valid: " + VALID_PROFILES);
        }

        String modules = request.getParameter("modules", "");

        // Validate module names to prevent command injection
        if (!modules.isEmpty() && !MODULE_NAME_PATTERN.matcher(modules).matches()) {
            return SkillResult.error("Invalid module name format: " + modules +
                ". Module names must start with a letter and contain only alphanumeric characters, underscores, or hyphens.");
        }

        int timeoutSeconds = parseInt(request.getParameter("timeout_seconds", ""), DEFAULT_TIMEOUT_SECONDS);

        _logger.info("Executing {} build with profile: {}", mode, profile.isEmpty() ? "default" : profile);

        long startTime = System.currentTimeMillis();

        try {
            BuildResult result = executeBuild(mode, profile, modules, timeoutSeconds);
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> data = new HashMap<>();
            data.put("mode", mode);
            data.put("profile", profile.isEmpty() ? "default" : profile);
            data.put("success", result.success);
            data.put("exit_code", result.exitCode);
            data.put("execution_time_ms", executionTime);
            data.put("timestamp", Instant.now().toString());

            if (!result.output.isEmpty()) {
                data.put("output_preview", truncateOutput(result.output, 2000));
                data.put("output_lines", countLines(result.output));
            }

            if (!result.success) {
                data.put("error_output", truncateOutput(result.errorOutput, 1000));
            }

            if (result.success) {
                _logger.info("Build completed successfully in {}ms", executionTime);
                return SkillResult.success(data, executionTime);
            } else {
                _logger.warn("Build failed with exit code {} in {}ms", result.exitCode, executionTime);
                return SkillResult.error("Build failed with exit code " + result.exitCode, data);
            }

        } catch (Exception e) {
            _logger.error("Build execution failed: {}", e.getMessage());
            return SkillResult.error("Build execution failed: " + e.getMessage());
        }
    }

    private BuildResult executeBuild(String mode, String profile, String modules, int timeoutSeconds)
            throws IOException, InterruptedException {

        List<String> command = buildCommand(mode, profile, modules);
        _logger.debug("Executing command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder errorOutput = new StringBuilder();

        Thread outputThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                _logger.warn("Error reading process output: {}", e.getMessage());
            }
        });

        Thread errorThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            } catch (IOException e) {
                _logger.warn("Error reading process error stream: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Build timed out after " + timeoutSeconds + " seconds");
        }

        outputThread.join(5000);
        errorThread.join(5000);

        int exitCode = process.exitValue();

        return new BuildResult(exitCode == 0, exitCode, output.toString(), errorOutput.toString());
    }

    private List<String> buildCommand(String mode, String profile, String modules) {
        List<String> command = new ArrayList<>();

        if ("incremental".equals(mode)) {
            command.add("bash");
            command.add("scripts/dx.sh");
            command.add("compile");

            if (!modules.isEmpty()) {
                command.add("-pl");
                command.add(modules);
            }
        } else {
            command.add("mvn");
            command.add("-T");
            command.add("1.5C");
            command.add("clean");
            command.add("compile");

            if (!profile.isEmpty()) {
                command.add("-P");
                command.add(profile);
            }

            if (!modules.isEmpty()) {
                command.add("-pl");
                command.add(modules);
            }
        }

        return command;
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String truncateOutput(String output, int maxLength) {
        if (output == null || output.length() <= maxLength) {
            return output;
        }
        return output.substring(0, maxLength) + "... (truncated)";
    }

    private int countLines(String output) {
        if (output == null || output.isEmpty()) {
            return 0;
        }
        return output.split("\n").length;
    }

    private record BuildResult(boolean success, int exitCode, String output, String errorOutput) {}
}
