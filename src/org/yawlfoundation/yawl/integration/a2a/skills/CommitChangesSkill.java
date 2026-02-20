package org.yawlfoundation.yawl.integration.a2a.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * A2A Skill for Git operations with safety guards.
 *
 * <p>Performs git operations following the Toyota Production System principles:
 * <ul>
 *   <li>NO force push or force-with-lease</li>
 *   <li>NO amend of pushed commits</li>
 *   <li>Atomic, well-described commits</li>
 *   <li>Stage specific files, never use git add .</li>
 * </ul>
 *
 * <p><b>Safety Classes:</b>
 * <ul>
 *   <li>{@code SAFE} - read operations (status, log, diff)</li>
 *   <li>{@code MODERATE} - stage files, create branches</li>
 *   <li>{@code DANGEROUS} - commit, push (requires approval)</li>
 *   <li>{@code FORBIDDEN} - force push, amend pushed, delete (never allowed)</li>
 * </ul>
 *
 * <p><b>Parameters:</b>
 * <ul>
 *   <li>{@code operation} - "status", "stage", "commit", "push", "branch", "log"</li>
 *   <li>{@code files} - Comma-separated list of files to stage</li>
 *   <li>{@code message} - Commit message</li>
 *   <li>{@code branch} - Branch name for branch/push operations</li>
 * </ul>
 *
 * <p><b>Required Permission:</b> {@code git:commit} (for commit/push), {@code git:read} (for read ops)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CommitChangesSkill implements A2ASkill {

    private static final Logger _logger = LogManager.getLogger(CommitChangesSkill.class);
    private static final String SKILL_ID = "commit_changes";
    private static final String SKILL_NAME = "Commit Changes";
    private static final String SKILL_DESCRIPTION =
        "Execute git operations with safety guards. " +
        "Follows Toyota Production System: no force push, no amend pushed commits.";

    private static final int COMMAND_TIMEOUT_SECONDS = 60;
    private static final Set<String> SAFE_OPERATIONS = Set.of("status", "log", "diff", "show");
    private static final Set<String> MODERATE_OPERATIONS = Set.of("stage", "branch", "checkout");
    private static final Set<String> DANGEROUS_OPERATIONS = Set.of("commit", "push");
    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
        "--force", "--force-with-lease", "reset --hard", "clean -fd",
        "amend", "push --force", "branch -D", "tag -d"
    );

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
        "(--force|--force-with-lease|reset\\s+--hard|clean\\s+-[fd]+|amend|push\\s+--force|branch\\s+-D|tag\\s+-d)",
        Pattern.CASE_INSENSITIVE
    );

    private final Path projectRoot;

    /**
     * Create skill with project root.
     *
     * @param projectRoot the project root directory
     */
    public CommitChangesSkill(Path projectRoot) {
        this.projectRoot = projectRoot != null ? projectRoot : Path.of(".");
    }

    /**
     * Create skill with default project root.
     */
    public CommitChangesSkill() {
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
        return Set.of("git:commit");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String operation = request.getParameter("operation", "status");
        String files = request.getParameter("files", "");
        String message = request.getParameter("message", "");
        String branch = request.getParameter("branch", "");

        SafetyClass safetyClass = classifyOperation(operation);
        _logger.info("Executing git operation: {} (safety: {})", operation, safetyClass);

        if (safetyClass == SafetyClass.FORBIDDEN) {
            return SkillResult.error(
                "Operation '" + operation + "' is FORBIDDEN. " +
                "Toyota Production System: build quality in, don't hide defects.");
        }

        if (!isValidOperation(operation)) {
            String validOps = String.join(", ", SAFE_OPERATIONS, MODERATE_OPERATIONS, DANGEROUS_OPERATIONS);
            _logger.warn("Invalid git operation requested: {}. Valid operations: {}", operation, validOps);
            return SkillResult.error(
                "Unknown operation: '" + operation + "'. Valid operations are: " + validOps);
        }

        validateParameters(operation, files, message, branch);

        try {
            GitResult result = executeGitOperation(operation, files, message, branch);

            Map<String, Object> data = new HashMap<>();
            data.put("operation", operation);
            data.put("safety_class", safetyClass.name());
            data.put("success", result.success);
            data.put("exit_code", result.exitCode);
            data.put("timestamp", Instant.now().toString());

            if (!result.output.isEmpty()) {
                data.put("output", result.output);
            }

            if (!result.success) {
                data.put("error", result.error);
            }

            if ("commit".equals(operation) && result.success) {
                String commitHash = extractCommitHash(result.output);
                if (commitHash != null) {
                    data.put("commit_hash", commitHash);
                }
            }

            if (result.success) {
                _logger.info("Git {} completed successfully", operation);
                return SkillResult.success(data);
            } else {
                _logger.warn("Git {} failed: {}", operation, result.error);
                return SkillResult.error("Git " + operation + " failed: " + result.error, data);
            }

        } catch (Exception e) {
            _logger.error("Git operation failed: {}", e.getMessage());
            return SkillResult.error("Git operation failed: " + e.getMessage());
        }
    }

    private boolean isValidOperation(String operation) {
        return SAFE_OPERATIONS.contains(operation) ||
               MODERATE_OPERATIONS.contains(operation) ||
               DANGEROUS_OPERATIONS.contains(operation);
    }

    private SafetyClass classifyOperation(String operation) {
        if (FORBIDDEN_PATTERN.matcher(operation).find()) {
            return SafetyClass.FORBIDDEN;
        }

        if (SAFE_OPERATIONS.contains(operation)) {
            return SafetyClass.SAFE;
        }

        if (MODERATE_OPERATIONS.contains(operation)) {
            return SafetyClass.MODERATE;
        }

        if (DANGEROUS_OPERATIONS.contains(operation)) {
            return SafetyClass.DANGEROUS;
        }

        return SafetyClass.MODERATE;
    }

    private void validateParameters(String operation, String files, String message, String branch) {
        if ("stage".equals(operation) && (files == null || files.isEmpty())) {
            throw new IllegalArgumentException(
                "Stage operation requires 'files' parameter. " +
                "Use explicit file list, never 'git add .'");
        }

        if ("commit".equals(operation) && (message == null || message.isEmpty())) {
            throw new IllegalArgumentException(
                "Commit operation requires 'message' parameter");
        }

        if ("push".equals(operation) && (branch == null || branch.isEmpty())) {
            throw new IllegalArgumentException(
                "Push operation requires 'branch' parameter for explicit target");
        }
    }

    private GitResult executeGitOperation(String operation, String files, String message, String branch)
            throws IOException, InterruptedException {

        List<String> command = buildCommand(operation, files, message, branch);
        _logger.debug("Executing command: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectRoot.toFile());
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        Thread outputThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                _logger.warn("Error reading git output: {}", e.getMessage());
            }
        });

        Thread errorThread = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            } catch (IOException e) {
                _logger.warn("Error reading git error: {}", e.getMessage());
            }
        });

        boolean finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Git operation timed out after " + COMMAND_TIMEOUT_SECONDS + " seconds");
        }

        outputThread.join(1000);
        errorThread.join(1000);

        int exitCode = process.exitValue();

        return new GitResult(exitCode == 0, exitCode, output.toString(), error.toString());
    }

    private List<String> buildCommand(String operation, String files, String message, String branch) {
        List<String> command = new ArrayList<>();
        command.add("git");

        switch (operation) {
            case "status" -> command.add("status");
            case "log" -> {
                command.add("log");
                command.add("--oneline");
                command.add("-10");
            }
            case "diff" -> command.add("diff");
            case "show" -> command.add("show");
            case "stage" -> {
                command.add("add");
                for (String file : files.split(",")) {
                    String trimmed = file.trim();
                    if (!trimmed.isEmpty()) {
                        validatePathSafety(trimmed);
                        command.add(trimmed);
                    }
                }
            }
            case "commit" -> {
                command.add("commit");
                command.add("-m");
                command.add(sanitizeCommitMessage(message));
            }
            case "branch" -> {
                command.add("branch");
                if (branch != null && !branch.isEmpty()) {
                    command.add(branch);
                }
            }
            case "checkout" -> {
                command.add("checkout");
                if (branch != null && !branch.isEmpty()) {
                    command.add(branch);
                }
            }
            case "push" -> {
                command.add("push");
                command.add("-u");
                command.add("origin");
                command.add(branch);
            }
        }

        return command;
    }

    private void validatePathSafety(String path) {
        if (path.contains("..") || path.startsWith("/") || path.contains("~")) {
            throw new SecurityException("Unsafe path detected: " + path);
        }
    }

    /**
     * Sanitizes commit messages to prevent git injection attacks.
     * Removes control characters and normalizes whitespace.
     *
     * @param message the raw commit message
     * @return sanitized message safe for git command line
     */
    private static String sanitizeCommitMessage(String message) {
        if (message == null || message.isEmpty()) {
            return "Automated commit";
        }
        // Remove control characters (including newlines that could inject commands)
        // Normalize whitespace to single spaces
        String sanitized = message
            .replaceAll("[\\x00-\\x1f\\x7f]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        // Truncate to reasonable length
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 497) + "...";
        }

        return sanitized;
    }

    private String extractCommitHash(String output) {
        if (output == null) return null;
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.contains("[") && line.contains("->") || line.matches("[a-f0-9]{7,40}.*")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length > 0 && parts[0].matches("[a-f0-9]{7,40}")) {
                    return parts[0];
                }
            }
        }
        return null;
    }

    private enum SafetyClass {
        SAFE, MODERATE, DANGEROUS, FORBIDDEN
    }

    private record GitResult(boolean success, int exitCode, String output, String error) {}
}
