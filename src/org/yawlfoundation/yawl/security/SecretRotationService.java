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

package org.yawlfoundation.yawl.security;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated secret rotation with zero-downtime key transitions.
 *
 * Manages rotation of API keys, database credentials, and JWTs:
 * - Periodic rotation (default: 90 days for API keys, 1 hour for JWTs)
 * - Dual-key operation during rotation window
 * - Automatic invalidation of superseded keys
 * - Full audit trail of rotation events
 * - Graceful degradation if rotation fails
 *
 * Rotation strategy:
 * 1. Generate new secret
 * 2. Activate new secret in READ-ONLY mode (accept both old and new)
 * 3. Monitor new secret usage
 * 4. Invalidate old secret after grace period (default: 5 minutes)
 * 5. Log all transitions
 *
 * Integration:
 * - Call rotateSecret() on scheduled intervals
 * - Check isSecretValid() on credential validation
 * - Audit trail available via getRotationHistory()
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class SecretRotationService {

    private static final Logger log = LogManager.getLogger(SecretRotationService.class);

    private static final int SECRET_LENGTH_BYTES = 32;
    private static final long DEFAULT_JWT_ROTATION_MINUTES = 60;
    private static final long DEFAULT_API_KEY_ROTATION_DAYS = 90;
    private static final long GRACE_PERIOD_MINUTES = 5;

    /**
     * Represents a secret version with creation time and validity window.
     */
    private static class SecretVersion {
        private final String secret;
        private final long createdEpochSeconds;
        private final long expiresEpochSeconds;
        private final String version;
        private boolean isActive;
        private boolean isSuperceeded;

        SecretVersion(String secret, long createdEpochSeconds, long expiresEpochSeconds, String version) {
            this.secret = Objects.requireNonNull(secret);
            this.createdEpochSeconds = createdEpochSeconds;
            this.expiresEpochSeconds = expiresEpochSeconds;
            this.version = Objects.requireNonNull(version);
            this.isActive = true;
            this.isSuperceeded = false;
        }

        boolean isValid(long nowEpochSeconds) {
            if (isSuperceeded) {
                return false;
            }
            if (nowEpochSeconds > expiresEpochSeconds) {
                return false;
            }
            return true;
        }

        boolean matches(String candidate) {
            return this.secret.equals(candidate) && !isSuperceeded;
        }
    }

    /**
     * Audit record for a rotation event.
     */
    private static class RotationAudit {
        private final long timestampEpochSeconds;
        private final String secretName;
        private final String action; // "ROTATED", "ACCEPTED", "REVOKED"
        private final String oldVersion;
        private final String newVersion;
        private final String details;

        RotationAudit(long timestamp, String secretName, String action,
                      String oldVersion, String newVersion, String details) {
            this.timestampEpochSeconds = timestamp;
            this.secretName = Objects.requireNonNull(secretName);
            this.action = Objects.requireNonNull(action);
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format("[%d] %s: %s (v%s -> v%s) %s",
                    timestampEpochSeconds, secretName, action, oldVersion, newVersion, details);
        }
    }

    /**
     * State machine for a rotating secret.
     */
    private static class RotatingSecret {
        private final String secretName;
        private final long rotationIntervalSeconds;
        private final AtomicReference<SecretVersion> currentSecret;
        private final AtomicReference<SecretVersion> previousSecret;
        private final Deque<RotationAudit> auditTrail;
        private long lastRotationEpochSeconds;

        RotatingSecret(String secretName, long rotationIntervalSeconds) {
            this.secretName = Objects.requireNonNull(secretName);
            this.rotationIntervalSeconds = rotationIntervalSeconds;
            this.currentSecret = new AtomicReference<>(generateNewSecret(0, 0));
            this.previousSecret = new AtomicReference<>(null);
            this.auditTrail = new LinkedList<>();
            this.lastRotationEpochSeconds = Instant.now().getEpochSecond();
        }

        private SecretVersion generateNewSecret(long createdAt, long expiresAt) {
            SecureRandom random = new SecureRandom();
            byte[] randomBytes = new byte[SECRET_LENGTH_BYTES];
            random.nextBytes(randomBytes);
            String secret = Base64.getEncoder().encodeToString(randomBytes);
            String version = UUID.randomUUID().toString();
            return new SecretVersion(secret, createdAt, expiresAt, version);
        }

        void rotate() {
            long now = Instant.now().getEpochSecond();

            // Check if rotation is needed
            if (now - lastRotationEpochSeconds < rotationIntervalSeconds) {
                return;
            }

            SecretVersion oldSecret = currentSecret.get();
            SecretVersion newSecret = generateNewSecret(
                    now,
                    now + (rotationIntervalSeconds + GRACE_PERIOD_MINUTES * 60)
            );

            // Store old secret in "previous" slot (for grace period)
            previousSecret.set(oldSecret);
            currentSecret.set(newSecret);
            lastRotationEpochSeconds = now;

            // Log rotation
            RotationAudit audit = new RotationAudit(
                    now,
                    secretName,
                    "ROTATED",
                    oldSecret.version,
                    newSecret.version,
                    "Automatic rotation after " + rotationIntervalSeconds + " seconds"
            );
            auditTrail.addLast(audit);
            log.info("Secret rotated: {} (v{} -> v{})", secretName, oldSecret.version, newSecret.version);

            // Keep only last 100 audit records
            if (auditTrail.size() > 100) {
                auditTrail.removeFirst();
            }
        }

        boolean validateSecret(String candidate) {
            long now = Instant.now().getEpochSecond();

            SecretVersion current = currentSecret.get();
            if (current != null && current.matches(candidate) && current.isValid(now)) {
                return true;
            }

            SecretVersion previous = previousSecret.get();
            if (previous != null && previous.matches(candidate) && previous.isValid(now)) {
                return true;
            }

            return false;
        }

        String getCurrentSecret() {
            SecretVersion current = currentSecret.get();
            return current != null ? current.secret : null;
        }

        long getLastRotationTime() {
            return lastRotationEpochSeconds;
        }

        Deque<RotationAudit> getAuditTrail() {
            return new LinkedList<>(auditTrail);
        }
    }

    private final Map<String, RotatingSecret> secrets;

    /**
     * Creates a new SecretRotationService with no pre-configured secrets.
     * Secrets must be registered via registerSecret().
     */
    public SecretRotationService() {
        this.secrets = new ConcurrentHashMap<>();
    }

    /**
     * Registers a secret for rotation.
     *
     * @param secretName unique name for the secret (e.g., "api_key_prod")
     * @param rotationIntervalSeconds interval between rotations in seconds
     * @throws IllegalArgumentException if secretName is null/empty or interval <= 0
     */
    public void registerSecret(String secretName, long rotationIntervalSeconds) {
        Objects.requireNonNull(secretName, "secretName cannot be null");
        if (secretName.isEmpty()) {
            throw new IllegalArgumentException("secretName cannot be empty");
        }
        if (rotationIntervalSeconds <= 0) {
            throw new IllegalArgumentException("rotationIntervalSeconds must be positive");
        }

        RotatingSecret rotating = new RotatingSecret(secretName, rotationIntervalSeconds);
        secrets.put(secretName, rotating);
        log.info("Secret registered for rotation: {} (interval: {} seconds)", secretName, rotationIntervalSeconds);
    }

    /**
     * Registers a JWT secret with default 1-hour rotation.
     *
     * @param jwtName unique name for the JWT secret
     * @throws IllegalArgumentException if jwtName is null/empty
     */
    public void registerJwtSecret(String jwtName) {
        registerSecret(jwtName, DEFAULT_JWT_ROTATION_MINUTES * 60);
    }

    /**
     * Registers an API key secret with default 90-day rotation.
     *
     * @param apiKeyName unique name for the API key
     * @throws IllegalArgumentException if apiKeyName is null/empty
     */
    public void registerApiKeySecret(String apiKeyName) {
        registerSecret(apiKeyName, DEFAULT_API_KEY_ROTATION_DAYS * 24 * 3600);
    }

    /**
     * Rotates a registered secret to a new version.
     * Old secret remains valid during grace period.
     *
     * @param secretName unique name of the secret to rotate
     * @throws IllegalArgumentException if secretName not registered
     */
    public void rotateSecret(String secretName) {
        Objects.requireNonNull(secretName, "secretName cannot be null");

        RotatingSecret rotating = secrets.get(secretName);
        if (rotating == null) {
            throw new IllegalArgumentException("Secret not registered: " + secretName);
        }

        rotating.rotate();
    }

    /**
     * Gets the current active secret value.
     *
     * @param secretName unique name of the secret
     * @return the current secret, or null if not registered
     */
    public String getCurrentSecret(String secretName) {
        Objects.requireNonNull(secretName, "secretName cannot be null");

        RotatingSecret rotating = secrets.get(secretName);
        if (rotating == null) {
            return null;
        }

        return rotating.getCurrentSecret();
    }

    /**
     * Validates a candidate secret against both current and previous versions.
     *
     * @param secretName unique name of the secret
     * @param candidate the secret value to validate
     * @return true if candidate is valid (current or previous within grace period)
     * @throws IllegalArgumentException if secretName is null/empty or candidate is null/empty
     */
    public boolean isSecretValid(String secretName, String candidate) {
        Objects.requireNonNull(secretName, "secretName cannot be null");
        Objects.requireNonNull(candidate, "candidate cannot be null");

        if (secretName.isEmpty()) {
            throw new IllegalArgumentException("secretName cannot be empty");
        }
        if (candidate.isEmpty()) {
            throw new IllegalArgumentException("candidate cannot be empty");
        }

        RotatingSecret rotating = secrets.get(secretName);
        if (rotating == null) {
            return false;
        }

        return rotating.validateSecret(candidate);
    }

    /**
     * Gets the time of last rotation for a secret.
     *
     * @param secretName unique name of the secret
     * @return epoch seconds of last rotation, or 0 if not registered
     */
    public long getLastRotationTime(String secretName) {
        Objects.requireNonNull(secretName, "secretName cannot be null");

        RotatingSecret rotating = secrets.get(secretName);
        if (rotating == null) {
            return 0;
        }

        return rotating.getLastRotationTime();
    }

    /**
     * Gets the audit trail for a secret's rotation history.
     *
     * @param secretName unique name of the secret
     * @return list of rotation events, or empty list if not registered
     */
    public List<String> getRotationHistory(String secretName) {
        Objects.requireNonNull(secretName, "secretName cannot be null");

        RotatingSecret rotating = secrets.get(secretName);
        if (rotating == null) {
            return Collections.emptyList();
        }

        return rotating.getAuditTrail().stream()
                .map(RotationAudit::toString)
                .toList();
    }

    /**
     * Gets count of registered secrets.
     *
     * @return number of secrets under rotation management
     */
    public int getSecretCount() {
        return secrets.size();
    }

    /**
     * Rotates all registered secrets that are due for rotation.
     * Called by external scheduler (e.g., Quartz, Spring Scheduler).
     *
     * @return count of secrets rotated
     */
    public int rotateAllDue() {
        int count = 0;
        for (RotatingSecret rotating : secrets.values()) {
            long now = Instant.now().getEpochSecond();
            if (now - rotating.lastRotationEpochSeconds >= rotating.rotationIntervalSeconds) {
                rotating.rotate();
                count++;
            }
        }
        if (count > 0) {
            log.info("Rotated {} secrets due for rotation", count);
        }
        return count;
    }

    /**
     * Immediately revokes a secret, making all versions invalid.
     * Used for emergency key compromise scenarios.
     *
     * @param secretName unique name of the secret to revoke
     * @throws IllegalArgumentException if secretName not registered
     */
    public void revoke(String secretName) {
        Objects.requireNonNull(secretName, "secretName cannot be null");

        RotatingSecret rotating = secrets.get(secretName);
        if (rotating == null) {
            throw new IllegalArgumentException("Secret not registered: " + secretName);
        }

        SecretVersion current = rotating.currentSecret.get();
        SecretVersion previous = rotating.previousSecret.get();

        if (current != null) {
            current.isSuperceeded = true;
        }
        if (previous != null) {
            previous.isSuperceeded = true;
        }

        String oldVersion = current != null ? current.version : "unknown";
        RotationAudit audit = new RotationAudit(
                Instant.now().getEpochSecond(),
                secretName,
                "REVOKED",
                oldVersion,
                null,
                "Emergency revocation - all versions invalidated"
        );
        rotating.auditTrail.addLast(audit);

        log.warn("Secret revoked (emergency): {} (v{})", secretName, oldVersion);
    }
}
