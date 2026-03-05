package org.yawlfoundation.yawl.integration.claude;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Sanitizes prompts before sending to Claude Code CLI.
 *
 * <p>Provides security filtering to prevent:</p>
 * <ul>
 *   <li>Shell command injection</li>
 *   <li>Prompt escape attacks</li>
 *   <li>Sensitive data exfiltration attempts</li>
 *   <li>Malicious file path traversal</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ClaudePromptSanitizer {

    /** Maximum prompt length */
    private static final int MAX_PROMPT_LENGTH = 100_000;

    /** Pattern for shell metacharacters that could enable injection */
    private static final Pattern SHELL_METACHARACTERS = Pattern.compile(
        "[`$\\\\]"  // Backticks, dollar signs, backslashes for command substitution
    );

    /** Pattern for prompt escape sequences */
    private static final Pattern PROMPT_ESCAPE = Pattern.compile(
        "(?i)(system:\\s*|assistant:\\s*|user:\\s*|\\[INST\\]|\\[/INST\\])",
        Pattern.CASE_INSENSITIVE
    );

    /** Pattern for suspicious file paths */
    private static final Pattern SUSPICIOUS_PATHS = Pattern.compile(
        "(\\.\\.[\\\\/])|(/etc/)|(/root/)|(~\\/)|(%USERPROFILE%)",
        Pattern.CASE_INSENSITIVE
    );

    /** Pattern for credential-like strings */
    private static final Pattern CREDENTIAL_PATTERNS = Pattern.compile(
        "(?i)(password\\s*[=:]\\s*\\S+|" +
        "api[_-]?key\\s*[=:]\\s*\\S+|" +
        "secret[_-]?key\\s*[=:]\\s*\\S+|" +
        "token\\s*[=:]\\s*\\S+|" +
        "bearer\\s+\\S+)",
        Pattern.CASE_INSENSITIVE
    );

    /** Set of dangerous keywords that require extra scrutiny */
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
        "rm -rf",
        "format",
        "del /",
        "dd if=",
        "> /dev/",
        "mkfs",
        "fdisk",
        "shutdown",
        "reboot",
        "init 0",
        "init 6",
        ":(){:|:&};:",  // Fork bomb
        "chmod 777",
        "chown root"
    );

    /** Whether to log sanitization actions */
    private final boolean enableLogging;

    /** Whether to allow shell metacharacters (use with caution) */
    private final boolean allowShellMeta;

    /**
     * Creates a new ClaudePromptSanitizer with default settings.
     */
    public ClaudePromptSanitizer() {
        this(false, false);
    }

    /**
     * Creates a new ClaudePromptSanitizer with custom settings.
     *
     * @param enableLogging    whether to log sanitization actions
     * @param allowShellMeta   whether to allow shell metacharacters
     */
    public ClaudePromptSanitizer(boolean enableLogging, boolean allowShellMeta) {
        this.enableLogging = enableLogging;
        this.allowShellMeta = allowShellMeta;
    }

    /**
     * Sanitizes the input prompt for safe execution.
     *
     * @param prompt the raw prompt
     * @return the sanitized prompt
     * @throws IllegalArgumentException if the prompt contains dangerous content
     */
    public String sanitize(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be null or blank");
        }

        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new IllegalArgumentException(
                "Prompt length " + prompt.length() + " exceeds maximum " + MAX_PROMPT_LENGTH);
        }

        // Check for dangerous keywords
        checkDangerousKeywords(prompt);

        // Check for suspicious paths
        checkSuspiciousPaths(prompt);

        // Check for credential patterns
        checkCredentialPatterns(prompt);

        // Sanitize shell metacharacters if not allowed
        String sanitized = prompt;
        if (!allowShellMeta) {
            sanitized = sanitizeShellMeta(sanitized);
        }

        // Sanitize prompt escape sequences
        sanitized = sanitizePromptEscapes(sanitized);

        return sanitized;
    }

    /**
     * Validates a prompt without modifying it.
     *
     * @param prompt the prompt to validate
     * @return true if the prompt is safe
     */
    public boolean isValid(String prompt) {
        try {
            sanitize(prompt);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets a safe summary of a prompt for logging.
     *
     * @param prompt the prompt
     * @return a safe summary (truncated, no credentials)
     */
    public String getSafeSummary(String prompt) {
        if (prompt == null) {
            return "null";
        }
        String safe = CREDENTIAL_PATTERNS.matcher(prompt).replaceAll("[REDACTED]");
        if (safe.length() > 200) {
            return safe.substring(0, 197) + "...";
        }
        return safe;
    }

    /**
     * Checks for dangerous keywords in the prompt.
     */
    private void checkDangerousKeywords(String prompt) {
        String lowerPrompt = prompt.toLowerCase();
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (lowerPrompt.contains(keyword.toLowerCase())) {
                logSanitization("Dangerous keyword detected: " + keyword);
                throw new IllegalArgumentException(
                    "Prompt contains potentially dangerous command: " + keyword);
            }
        }
    }

    /**
     * Checks for suspicious file paths.
     */
    private void checkSuspiciousPaths(String prompt) {
        if (SUSPICIOUS_PATHS.matcher(prompt).find()) {
            logSanitization("Suspicious path pattern detected");
            throw new IllegalArgumentException(
                "Prompt contains suspicious file path patterns");
        }
    }

    /**
     * Checks for credential patterns.
     */
    private void checkCredentialPatterns(String prompt) {
        if (CREDENTIAL_PATTERNS.matcher(prompt).find()) {
            logSanitization("Credential pattern detected - rejecting");
            throw new IllegalArgumentException(
                "Prompt appears to contain credentials. " +
                "Remove sensitive data before submission.");
        }
    }

    /**
     * Sanitizes shell metacharacters.
     */
    private String sanitizeShellMeta(String prompt) {
        if (SHELL_METACHARACTERS.matcher(prompt).find()) {
            logSanitization("Shell metacharacters detected - escaping");
            // Escape backticks, dollar signs, and backslashes
            return prompt
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("$", "\\$");
        }
        return prompt;
    }

    /**
     * Sanitizes prompt escape sequences.
     */
    private String sanitizePromptEscapes(String prompt) {
        if (PROMPT_ESCAPE.matcher(prompt).find()) {
            logSanitization("Prompt escape sequence detected - neutralizing");
            // Wrap escape sequences in quotes to neutralize
            return PROMPT_ESCAPE.matcher(prompt)
                .replaceAll("'$1'");
        }
        return prompt;
    }

    /**
     * Logs a sanitization action if logging is enabled.
     */
    private void logSanitization(String message) {
        if (enableLogging) {
            System.getLogger(ClaudePromptSanitizer.class.getName())
                .log(System.Logger.Level.INFO, message);
        }
    }
}
