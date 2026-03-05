/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.tty;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safety layer for classifying and validating TTY commands.
 *
 * <p>Implements a 4-class safety model for command classification:
 * <ul>
 *   <li>{@link SafetyClass#SAFE} - Read-only operations (file reads, status queries)</li>
 *   <li>{@link SafetyClass#MODERATE} - Non-destructive writes (create files, add code)</li>
 *   <li>{@link SafetyClass#DANGEROUS} - Destructive operations (delete files, force push)</li>
 *   <li>{@link SafetyClass#FORBIDDEN} - Never allowed (rm -rf /, format disk, credential exposure)</li>
 * </ul>
 *
 * <p>The safety layer analyzes command content using pattern matching against
 * known safe, dangerous, and forbidden patterns. Commands that don't match
 * any pattern are classified as MODERATE by default.
 *
 * <p><b>Pattern Categories:</b>
 * <ul>
 *   <li>FORBIDDEN_PATTERNS - Patterns that indicate commands that should never execute</li>
 *   <li>DANGEROUS_PATTERNS - Patterns that indicate destructive or irreversible operations</li>
 *   <li>SAFE_PATTERNS - Patterns that indicate read-only or harmless operations</li>
 * </ul>
 *
 * @since YAWL 5.2
 */
public final class TtySafetyLayer {

    private static final Logger _logger = LogManager.getLogger(TtySafetyLayer.class);

    /**
     * Safety classification for TTY commands.
     */
    public enum SafetyClass {
        /**
         * Read-only operations - always allowed.
         * Examples: file reads, status queries, list operations.
         */
        SAFE(1),

        /**
         * Non-destructive writes - allowed with standard approval.
         * Examples: create files, add code, append data.
         */
        MODERATE(2),

        /**
         * Destructive operations - requires elevated approval.
         * Examples: delete files, force push git operations, overwrite data.
         */
        DANGEROUS(3),

        /**
         * Never allowed - blocked unconditionally.
         * Examples: rm -rf /, format disk, credential exposure, system destruction.
         */
        FORBIDDEN(4);

        private final int level;

        SafetyClass(int level) {
            this.level = level;
        }

        /**
         * Get the numeric level for comparison.
         *
         * @return the safety level (1 = safest, 4 = most dangerous)
         */
        public int getLevel() {
            return level;
        }

        /**
         * Check if this safety class is at least as safe as another.
         *
         * @param other the other safety class to compare
         * @return true if this class is safer or equal to the other
         */
        public boolean isAtLeastAsSafeAs(SafetyClass other) {
            return this.level <= other.level;
        }

        /**
         * Check if this safety class requires approval.
         *
         * @return true if approval is required (MODERATE or DANGEROUS)
         */
        public boolean requiresApproval() {
            return this == MODERATE || this == DANGEROUS;
        }
    }

    /**
     * Result of a safety validation check.
     *
     * @param safetyClass the classified safety class
     * @param allowed whether the command is allowed under current policy
     * @param reason explanation for the classification
     * @param matchedPattern the pattern that matched (if any)
     */
    public record SafetyValidationResult(
        SafetyClass safetyClass,
        boolean allowed,
        String reason,
        String matchedPattern
    ) {
        /**
         * Create a successful validation result.
         *
         * @param safetyClass the classified safety class
         * @param reason explanation for the classification
         * @return validation result indicating success
         */
        public static SafetyValidationResult allowed(SafetyClass safetyClass, String reason) {
            return new SafetyValidationResult(safetyClass, true, reason, null);
        }

        /**
         * Create a blocked validation result.
         *
         * @param safetyClass the classified safety class
         * @param reason explanation for the block
         * @param matchedPattern the pattern that caused the block
         * @return validation result indicating blocked
         */
        public static SafetyValidationResult blocked(
            SafetyClass safetyClass,
            String reason,
            String matchedPattern
        ) {
            return new SafetyValidationResult(safetyClass, false, reason, matchedPattern);
        }
    }

    /**
     * Policy configuration for the safety layer.
     *
     * @param maxAllowedClass the maximum safety class allowed (inclusive)
     * @param requireApprovalForModerate whether MODERATE operations need approval
     * @param requireApprovalForDangerous whether DANGEROUS operations need approval
     * @param auditLogEnabled whether to log all safety decisions
     */
    public record SafetyPolicy(
        SafetyClass maxAllowedClass,
        boolean requireApprovalForModerate,
        boolean requireApprovalForDangerous,
        boolean auditLogEnabled
    ) {
        /**
         * Create a default permissive policy.
         * Allows all classes except FORBIDDEN, no approval required.
         *
         * @return permissive policy configuration
         */
        public static SafetyPolicy permissive() {
            return new SafetyPolicy(
                SafetyClass.DANGEROUS,
                false,
                false,
                true
            );
        }

        /**
         * Create a strict policy.
         * Only allows SAFE operations by default.
         *
         * @return strict policy configuration
         */
        public static SafetyPolicy strict() {
            return new SafetyPolicy(
                SafetyClass.SAFE,
                true,
                true,
                true
            );
        }

        /**
         * Create a balanced policy.
         * Allows SAFE and MODERATE, requires approval for DANGEROUS.
         *
         * @return balanced policy configuration
         */
        public static SafetyPolicy balanced() {
            return new SafetyPolicy(
                SafetyClass.MODERATE,
                false,
                true,
                true
            );
        }
    }

    /**
     * Forbidden patterns that should never be allowed.
     * These patterns indicate commands that could cause catastrophic damage.
     */
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
        // System destruction
        Pattern.compile("(?i)rm\\s+-[^-]*r[^-]*f[^-]*\\s+/(?!\\w)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)rm\\s+-[^-]*f[^-]*r[^-]*\\s+/(?!\\w)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)mkfs\\.", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)dd\\s+.*of=/dev/", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i):(){ :|:& };:", Pattern.CASE_INSENSITIVE),  // Fork bomb

        // Credential exposure
        Pattern.compile("(?i)export\\s+.*PASSWORD", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)export\\s+.*SECRET", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)export\\s+.*API_KEY", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)echo.*password.*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)cat.*\\.env.*\\|.*curl", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)curl.*\\$\\{.*PASSWORD", Pattern.CASE_INSENSITIVE),

        // Network attacks
        Pattern.compile("(?i)nmap.*-sS", Pattern.CASE_INSENSITIVE),  // SYN scan
        Pattern.compile("(?i)nmap.*-sU", Pattern.CASE_INSENSITIVE),  // UDP scan
        Pattern.compile("(?i)hping3", Pattern.CASE_INSENSITIVE),

        // Privilege escalation
        Pattern.compile("(?i)chmod\\s+[0-7]*777\\s+/", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)chown\\s+.*:\\s*/", Pattern.CASE_INSENSITIVE),

        // Process killing
        Pattern.compile("(?i)kill\\s+-9\\s+1\\b", Pattern.CASE_INSENSITIVE),  // kill init
        Pattern.compile("(?i)killall\\s+.*-9", Pattern.CASE_INSENSITIVE),

        // Disk operations
        Pattern.compile("(?i)wipefs", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)hdparm.*--secure-erase", Pattern.CASE_INSENSITIVE),

        // Self-modifying code execution
        Pattern.compile("(?i)curl.*\\|.*sh", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)wget.*\\|.*bash", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)curl.*\\|.*bash", Pattern.CASE_INSENSITIVE),

        // Database destruction
        Pattern.compile("(?i)DROP\\s+DATABASE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)TRUNCATE\\s+TABLE", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)DELETE\\s+FROM", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Dangerous patterns that require special approval.
     * These patterns indicate operations that could cause significant damage.
     */
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
        // File deletion
        Pattern.compile("(?i)rm\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)rmdir\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)delete", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)remove\\s+file", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)erase", Pattern.CASE_INSENSITIVE),

        // Git force operations
        Pattern.compile("(?i)git\\s+push.*--force", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)git\\s+push.*-f\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)git\\s+reset\\s+--hard", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)git\\s+clean\\s+-fd", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)git\\s+checkout\\s+--\\s*\\.", Pattern.CASE_INSENSITIVE),

        // Overwrite operations
        Pattern.compile("(?i)>\\s*/dev/sd", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)overwrite", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)replace\\s+all", Pattern.CASE_INSENSITIVE),

        // System modifications
        Pattern.compile("(?i)apt\\s+remove", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)yum\\s+remove", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)brew\\s+uninstall", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)systemctl\\s+disable", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)systemctl\\s+stop", Pattern.CASE_INSENSITIVE),

        // Process control
        Pattern.compile("(?i)kill\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)pkill\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)killall\\s+", Pattern.CASE_INSENSITIVE),

        // Network changes
        Pattern.compile("(?i)iptables\\s+-F", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)ifconfig\\s+.*down", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)ip\\s+link\\s+set\\s+.*down", Pattern.CASE_INSENSITIVE),

        // File permission changes
        Pattern.compile("(?i)chmod\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)chown\\s+", Pattern.CASE_INSENSITIVE)
    );

    /**
     * Safe patterns that are always allowed.
     * These patterns indicate read-only or harmless operations.
     */
    private static final List<Pattern> SAFE_PATTERNS = List.of(
        // File reading
        Pattern.compile("(?i)^read\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^cat\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^head\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^tail\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^less\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^more\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)show\\s+me", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)display\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)view\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)what\\s+is", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)tell\\s+me\\s+about", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)explain\\s+", Pattern.CASE_INSENSITIVE),

        // Directory listing
        Pattern.compile("(?i)^ls\\s*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^dir\\s*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)list\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^find\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^tree\\s*", Pattern.CASE_INSENSITIVE),

        // Status queries
        Pattern.compile("(?i)^status$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^git\\s+status", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^git\\s+log", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^git\\s+diff", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^git\\s+branch", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^git\\s+show", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^ps\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^top\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^htop\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^df\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^du\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^free\\s+", Pattern.CASE_INSENSITIVE),

        // Version and info
        Pattern.compile("(?i)--version", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)--help", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^man\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^info\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^which\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^whereis\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^whoami\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^pwd\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^uname\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^hostname\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^date\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^uptime\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^env\\s*$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^printenv\\s*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)^echo\\s+\\$", Pattern.CASE_INSENSITIVE),

        // Claude Code read operations
        Pattern.compile("(?i)analyze\\s+the\\s+code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)review\\s+the\\s+code", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)check\\s+for", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)search\\s+for", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)find\\s+all", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)grep\\s+", Pattern.CASE_INSENSITIVE)
    );

    private final SafetyPolicy policy;

    /**
     * Create a safety layer with balanced policy.
     */
    public TtySafetyLayer() {
        this(SafetyPolicy.balanced());
    }

    /**
     * Create a safety layer with specified policy.
     *
     * @param policy the safety policy to use
     */
    public TtySafetyLayer(SafetyPolicy policy) {
        this.policy = policy != null ? policy : SafetyPolicy.balanced();
        _logger.info("TtySafetyLayer initialized with policy: maxAllowed={}, auditEnabled={}",
            policy.maxAllowedClass(), policy.auditLogEnabled());
    }

    /**
     * Classify a command's safety level.
     *
     * @param command the command to classify
     * @return the safety classification
     */
    public SafetyClass classify(String command) {
        if (command == null || command.isBlank()) {
            return SafetyClass.SAFE;
        }

        String trimmedCommand = command.trim();

        // Check forbidden patterns first
        for (Pattern pattern : FORBIDDEN_PATTERNS) {
            if (pattern.matcher(trimmedCommand).find()) {
                _logger.warn("Command classified as FORBIDDEN: matched pattern '{}'", pattern.pattern());
                return SafetyClass.FORBIDDEN;
            }
        }

        // Check dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(trimmedCommand).find()) {
                _logger.debug("Command classified as DANGEROUS: matched pattern '{}'", pattern.pattern());
                return SafetyClass.DANGEROUS;
            }
        }

        // Check safe patterns
        for (Pattern pattern : SAFE_PATTERNS) {
            if (pattern.matcher(trimmedCommand).find()) {
                _logger.debug("Command classified as SAFE: matched pattern '{}'", pattern.pattern());
                return SafetyClass.SAFE;
            }
        }

        // Default to moderate for unknown commands
        _logger.debug("Command classified as MODERATE (default): no pattern matched");
        return SafetyClass.MODERATE;
    }

    /**
     * Validate a command against the safety policy.
     *
     * @param command the command to validate
     * @return validation result with classification and permission status
     */
    public SafetyValidationResult validate(String command) {
        SafetyClass safetyClass = classify(command);

        // FORBIDDEN is always blocked
        if (safetyClass == SafetyClass.FORBIDDEN) {
            String reason = "Command contains forbidden pattern that is never allowed";
            if (policy.auditLogEnabled()) {
                _logger.warn("BLOCKED: {} - Reason: {}", safetyClass, reason);
            }
            return SafetyValidationResult.blocked(safetyClass, reason, findMatchedPattern(command, FORBIDDEN_PATTERNS));
        }

        // Check if class exceeds maximum allowed
        if (safetyClass.getLevel() > policy.maxAllowedClass().getLevel()) {
            String reason = String.format(
                "Command class %s exceeds maximum allowed %s",
                safetyClass, policy.maxAllowedClass()
            );
            if (policy.auditLogEnabled()) {
                _logger.warn("BLOCKED: {} - Reason: {}", safetyClass, reason);
            }
            return SafetyValidationResult.blocked(safetyClass, reason, null);
        }

        // Check if approval is required
        boolean requiresApproval = switch (safetyClass) {
            case MODERATE -> policy.requireApprovalForModerate();
            case DANGEROUS -> policy.requireApprovalForDangerous();
            default -> false;
        };

        String reason = requiresApproval
            ? "Command allowed but requires approval"
            : "Command allowed under current policy";

        if (policy.auditLogEnabled()) {
            _logger.info("ALLOWED: {} - Approval required: {} - Command: {}",
                safetyClass, requiresApproval, truncateForLog(command));
        }

        return SafetyValidationResult.allowed(safetyClass, reason);
    }

    /**
     * Check if a command is allowed under the current policy.
     *
     * @param command the command to check
     * @return true if the command is allowed
     */
    public boolean isAllowed(String command) {
        return validate(command).allowed();
    }

    /**
     * Check if a command requires human approval.
     *
     * @param command the command to check
     * @return true if approval is required
     */
    public boolean requiresApproval(String command) {
        SafetyClass safetyClass = classify(command);
        return switch (safetyClass) {
            case MODERATE -> policy.requireApprovalForModerate();
            case DANGEROUS -> policy.requireApprovalForDangerous();
            default -> false;
        };
    }

    /**
     * Get the current safety policy.
     *
     * @return the active safety policy
     */
    public SafetyPolicy getPolicy() {
        return policy;
    }

    /**
     * Get all forbidden patterns (for documentation/debugging).
     *
     * @return set of forbidden pattern strings
     */
    public Set<String> getForbiddenPatterns() {
        return FORBIDDEN_PATTERNS.stream()
            .map(Pattern::pattern)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all dangerous patterns (for documentation/debugging).
     *
     * @return set of dangerous pattern strings
     */
    public Set<String> getDangerousPatterns() {
        return DANGEROUS_PATTERNS.stream()
            .map(Pattern::pattern)
            .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Get all safe patterns (for documentation/debugging).
     *
     * @return set of safe pattern strings
     */
    public Set<String> getSafePatterns() {
        return SAFE_PATTERNS.stream()
            .map(Pattern::pattern)
            .collect(java.util.stream.Collectors.toSet());
    }

    private String findMatchedPattern(String command, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(command).find()) {
                return pattern.pattern();
            }
        }
        return null;
    }

    private String truncateForLog(String command) {
        if (command == null) {
            return "null";
        }
        int maxLength = 100;
        if (command.length() <= maxLength) {
            return command;
        }
        return command.substring(0, maxLength) + "...";
    }
}
