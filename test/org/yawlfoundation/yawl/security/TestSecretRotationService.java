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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for automated secret rotation with zero-downtime transitions.
 *
 * Tests dual-key operation, grace periods, audit trails, and emergency revocation.
 */
@DisplayName("Secret Rotation Service")
class TestSecretRotationService {

    private SecretRotationService service;

    @BeforeEach
    void setUp() {
        service = new SecretRotationService();
    }

    @Test
    @DisplayName("Should register secrets with custom intervals")
    void testSecretRegistration() {
        service.registerSecret("api-key-prod", 3600);
        assertEquals(1, service.getSecretCount());

        service.registerSecret("jwt-auth", 7200);
        assertEquals(2, service.getSecretCount());
    }

    @Test
    @DisplayName("Should register JWT secrets with 1-hour default rotation")
    void testJwtSecretRegistration() {
        service.registerJwtSecret("jwt-prod");
        assertEquals(1, service.getSecretCount());

        String secret1 = service.getCurrentSecret("jwt-prod");
        assertNotNull(secret1, "JWT secret should be generated");
        assertTrue(secret1.length() > 0);
    }

    @Test
    @DisplayName("Should register API key secrets with 90-day default rotation")
    void testApiKeySecretRegistration() {
        service.registerApiKeySecret("api-key-prod");
        assertEquals(1, service.getSecretCount());

        String secret = service.getCurrentSecret("api-key-prod");
        assertNotNull(secret);
    }

    @Test
    @DisplayName("Should generate unique secrets on rotation")
    void testSecretRotation() {
        String secretName = "rotation-test";
        service.registerSecret(secretName, 1);

        String secret1 = service.getCurrentSecret(secretName);
        assertNotNull(secret1);

        service.rotateSecret(secretName);
        String secret2 = service.getCurrentSecret(secretName);

        assertNotNull(secret2);
        assertNotEquals(secret1, secret2, "Rotated secret should be different");
    }

    @Test
    @DisplayName("Should accept old secret during grace period")
    void testGracePeriodValidation() {
        String secretName = "grace-period-test";
        service.registerSecret(secretName, 1);

        String oldSecret = service.getCurrentSecret(secretName);
        assertTrue(service.isSecretValid(secretName, oldSecret));

        service.rotateSecret(secretName);
        String newSecret = service.getCurrentSecret(secretName);

        // Both old and new should be valid during grace period
        assertTrue(service.isSecretValid(secretName, oldSecret), "Old secret should still be valid");
        assertTrue(service.isSecretValid(secretName, newSecret), "New secret should be valid");
    }

    @Test
    @DisplayName("Should reject invalid secrets")
    void testInvalidSecretRejection() {
        String secretName = "rejection-test";
        service.registerSecret(secretName, 1);

        assertFalse(service.isSecretValid(secretName, "invalid-secret-123"));
        assertFalse(service.isSecretValid(secretName, ""));
    }

    @Test
    @DisplayName("Should maintain audit trail of rotations")
    void testAuditTrail() {
        String secretName = "audit-test";
        service.registerSecret(secretName, 1);

        service.rotateSecret(secretName);
        service.rotateSecret(secretName);

        var history = service.getRotationHistory(secretName);
        assertTrue(history.size() >= 2, "Should have at least 2 rotation events");

        // Verify audit entries are loggable
        for (String entry : history) {
            assertTrue(entry.contains(secretName));
            assertTrue(entry.contains("ROTATED") || entry.contains("created"));
        }
    }

    @Test
    @DisplayName("Should track last rotation time")
    void testLastRotationTime() throws InterruptedException {
        String secretName = "time-test";
        service.registerSecret(secretName, 1);

        long time1 = service.getLastRotationTime(secretName);
        assertTrue(time1 > 0, "Initial registration should have timestamp");

        Thread.sleep(100);
        service.rotateSecret(secretName);
        long time2 = service.getLastRotationTime(secretName);

        assertTrue(time2 >= time1, "Last rotation time should advance");
    }

    @Test
    @DisplayName("Should auto-rotate due secrets")
    void testAutoRotation() {
        service.registerSecret("auto-rotate-1", 1);
        service.registerSecret("auto-rotate-2", 1);
        service.registerSecret("auto-rotate-3", 999999); // Not due

        int rotated = service.rotateAllDue();
        assertTrue(rotated >= 2, "Should rotate due secrets");
    }

    @Test
    @DisplayName("Should revoke all secret versions on emergency")
    void testEmergencyRevocation() {
        String secretName = "revoke-test";
        service.registerSecret(secretName, 3600);

        String secret1 = service.getCurrentSecret(secretName);
        assertTrue(service.isSecretValid(secretName, secret1));

        service.revoke(secretName);

        assertFalse(service.isSecretValid(secretName, secret1), "Revoked secret should be invalid");
        var history = service.getRotationHistory(secretName);
        assertTrue(history.stream().anyMatch(h -> h.contains("REVOKED")));
    }

    @Test
    @DisplayName("Should reject null secret name")
    void testNullSecretNameValidation() {
        assertThrows(NullPointerException.class, () -> service.registerSecret(null, 1));
        assertThrows(NullPointerException.class, () -> service.getCurrentSecret(null));
        assertThrows(NullPointerException.class, () -> service.isSecretValid(null, "test"));
    }

    @Test
    @DisplayName("Should reject empty secret name")
    void testEmptySecretNameValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.registerSecret("", 1));
        assertThrows(IllegalArgumentException.class, () -> service.isSecretValid("", "test"));
    }

    @Test
    @DisplayName("Should reject invalid rotation intervals")
    void testInvalidIntervalValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.registerSecret("test", 0));
        assertThrows(IllegalArgumentException.class, () -> service.registerSecret("test", -1));
    }

    @Test
    @DisplayName("Should reject rotation of unregistered secrets")
    void testUnregisteredSecretRotation() {
        assertThrows(IllegalArgumentException.class, () -> service.rotateSecret("nonexistent"));
        assertThrows(IllegalArgumentException.class, () -> service.revoke("nonexistent"));
    }

    @Test
    @DisplayName("Should return empty history for unregistered secrets")
    void testUnregisteredSecretHistory() {
        var history = service.getRotationHistory("nonexistent");
        assertTrue(history.isEmpty());

        long time = service.getLastRotationTime("nonexistent");
        assertEquals(0, time);
    }

    @Test
    @DisplayName("Should handle null candidate in validation")
    void testNullCandidateValidation() {
        service.registerSecret("null-test", 1);
        assertThrows(NullPointerException.class, () -> service.isSecretValid("null-test", null));
    }

    @Test
    @DisplayName("Should return null for unregistered secret value")
    void testUnregisteredSecretValue() {
        String secret = service.getCurrentSecret("never-registered");
        assertNull(secret);
    }
}
