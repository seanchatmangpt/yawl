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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.governance;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Proof-carrying receipt for Responsible AI evaluation of a model version.
 * Required before model promotion via ModelRegistry or safe_approve_model_promotion MCP tool.
 *
 * Ensures governance compliance across fairness, privacy, security, and transparency dimensions
 * before autonomous agents deploy new LLM models.
 *
 * @param modelId unique identifier for the model (e.g., "gpt-oss-20b-v1")
 * @param version semantic version string (e.g., "1.2.0")
 * @param evaluatedAt timestamp when evaluation was completed
 * @param fairnessPassed true if fairness evaluation passed
 * @param biasScore bias measurement (0.0 = no bias, 1.0 = maximum bias; pass threshold &lt;0.2)
 * @param privacyPassed true if privacy evaluation passed
 * @param securityPassed true if security evaluation passed
 * @param transparencyDocPresent true if transparency documentation is complete
 * @param accountabilitySignerId business owner agent ID who signed off
 * @param evidenceHash SHA-256 of modelId+version+all boolean fields for tamper detection
 *
 * @since YAWL 6.0
 */
public record ResponsibleAiReceipt(
    String modelId,
    String version,
    Instant evaluatedAt,
    boolean fairnessPassed,
    double biasScore,
    boolean privacyPassed,
    boolean securityPassed,
    boolean transparencyDocPresent,
    String accountabilitySignerId,
    String evidenceHash
) {

    /**
     * Creates a new ResponsibleAiReceipt with computed evidence hash.
     *
     * @param modelId unique identifier for the model
     * @param version semantic version string
     * @param fairnessPassed true if fairness evaluation passed
     * @param biasScore bias measurement (0.0 to 1.0)
     * @param privacyPassed true if privacy evaluation passed
     * @param securityPassed true if security evaluation passed
     * @param transparencyDocPresent true if transparency documentation is complete
     * @param accountabilitySignerId business owner agent ID
     * @return new ResponsibleAiReceipt with computed hash
     */
    public static ResponsibleAiReceipt create(
        String modelId, String version,
        boolean fairnessPassed, double biasScore,
        boolean privacyPassed, boolean securityPassed,
        boolean transparencyDocPresent, String accountabilitySignerId
    ) {
        String evidence = modelId + version + fairnessPassed + biasScore + privacyPassed + securityPassed + transparencyDocPresent + accountabilitySignerId;
        String hash = sha256(evidence);
        return new ResponsibleAiReceipt(
            modelId, version, Instant.now(),
            fairnessPassed, biasScore, privacyPassed, securityPassed,
            transparencyDocPresent, accountabilitySignerId, hash
        );
    }

    /**
     * Checks if all governance checks passed.
     * Returns true only if:
     * - fairnessPassed is true
     * - biasScore is below 0.2 threshold
     * - privacyPassed is true
     * - securityPassed is true
     * - transparencyDocPresent is true
     * - accountabilitySignerId is not null and not blank
     *
     * @return true if all checks passed, false otherwise
     */
    public boolean allChecksPassed() {
        return fairnessPassed
            && biasScore < 0.2
            && privacyPassed
            && securityPassed
            && transparencyDocPresent
            && accountabilitySignerId != null
            && !accountabilitySignerId.isBlank();
    }

    /**
     * Converts receipt to JSON string for logging/storage.
     *
     * @return JSON representation of receipt
     */
    public String toJson() {
        return "{\"modelId\":\"%s\",\"version\":\"%s\",\"allChecksPassed\":%s,\"biasScore\":%s,\"evidenceHash\":\"%s\"}"
            .formatted(
                escape(modelId),
                escape(version),
                allChecksPassed(),
                biasScore,
                escape(evidenceHash)
            );
    }

    /**
     * Computes SHA-256 hash of input string.
     *
     * @param input string to hash
     * @return hex-encoded SHA-256 hash
     * @throws IllegalStateException if SHA-256 algorithm is unavailable
     */
    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Escapes JSON special characters in string.
     *
     * @param s string to escape
     * @return escaped string safe for JSON
     */
    private static String escape(String s) {
        if (s == null) {
            return "null";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
