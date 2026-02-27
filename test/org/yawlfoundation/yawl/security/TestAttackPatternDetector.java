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
 * Test suite for real-time attack pattern detection with automatic response.
 *
 * Tests rate limit abuse, SQL injection, XXE/XML bombs, credential stuffing,
 * and auto-blocking of malicious clients.
 */
@DisplayName("Attack Pattern Detector")
class TestAttackPatternDetector {

    private AttackPatternDetector detector;

    @BeforeEach
    void setUp() {
        detector = new AttackPatternDetector();
    }

    @Test
    @DisplayName("Should detect rate limit abuse")
    void testRateLimitAbuseDetection() {
        String clientId = "rate-abuser";

        boolean isAbuse = detector.detectRateLimitAbuse(clientId, 150);
        assertTrue(isAbuse, "Should detect abuse at 150 req/min");

        assertTrue(detector.getRecentViolationCount(clientId) > 0);
    }

    @Test
    @DisplayName("Should allow normal request rates")
    void testNormalRateAllowed() {
        String clientId = "normal-client";

        boolean isAbuse = detector.detectRateLimitAbuse(clientId, 50);
        assertFalse(isAbuse, "Should allow 50 req/min");
    }

    @Test
    @DisplayName("Should detect SQL UNION-based injection")
    void testSqlUnionInjection() {
        String clientId = "sql-attacker-1";
        String input = "1' UNION SELECT * FROM users--";

        boolean isSqlInjection = detector.detectSqlInjection(clientId, "id", input);
        assertTrue(isSqlInjection, "Should detect UNION injection");

        assertTrue(detector.getRecentViolationCount(clientId) > 0);
    }

    @Test
    @DisplayName("Should detect stacked SQL queries")
    void testStackedSqlQueries() {
        String clientId = "sql-attacker-2";
        String input = "1; DROP TABLE users;--";

        boolean isSqlInjection = detector.detectSqlInjection(clientId, "id", input);
        assertTrue(isSqlInjection, "Should detect stacked queries");
    }

    @Test
    @DisplayName("Should detect SQL comment-based evasion")
    void testSqlCommentEvasion() {
        String clientId = "sql-attacker-3";
        String input = "1 -- comment";

        boolean isSqlInjection = detector.detectSqlInjection(clientId, "id", input);
        assertTrue(isSqlInjection, "Should detect comment-based evasion");
    }

    @Test
    @DisplayName("Should detect SQL CAST/CONVERT tricks")
    void testSqlCastTricks() {
        String clientId = "sql-attacker-4";
        String input = "CAST(1 AS VARCHAR)";

        boolean isSqlInjection = detector.detectSqlInjection(clientId, "id", input);
        assertTrue(isSqlInjection, "Should detect CAST tricks");
    }

    @Test
    @DisplayName("Should allow clean SQL input")
    void testCleanSqlInput() {
        String clientId = "clean-client";
        String input = "select_data_123";

        boolean isSqlInjection = detector.detectSqlInjection(clientId, "field", input);
        assertFalse(isSqlInjection, "Should allow clean input");
    }

    @Test
    @DisplayName("Should detect XXE attack attempts")
    void testXxeDetection() {
        String clientId = "xxe-attacker";
        String xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>";

        boolean isXxe = detector.detectXmlBomb(clientId, xml, 50000);
        assertTrue(isXxe, "Should detect XXE entity declaration");

        assertTrue(detector.shouldBlock(clientId) || detector.getRecentViolationCount(clientId) > 0);
    }

    @Test
    @DisplayName("Should detect billion laughs attack")
    void testBillionLaughsDetection() {
        String clientId = "xml-bomb-attacker";
        String xml = "<!DOCTYPE lolz [\n  <!ENTITY lol \"lol\">\n  <!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n]>\n<lolz>&lol2;</lolz>";

        boolean isAttack = detector.detectXmlBomb(clientId, xml, 50000);
        assertTrue(isAttack, "Should detect entity expansion");
    }

    @Test
    @DisplayName("Should detect oversized XML payloads")
    void testXmlSizeLimit() {
        String clientId = "xml-bomb-size";
        String largeXml = "<xml>" + "x".repeat(100_000) + "</xml>";

        boolean isAttack = detector.detectXmlBomb(clientId, largeXml, 50000);
        assertTrue(isAttack, "Should detect oversized XML");
    }

    @Test
    @DisplayName("Should allow normal XML")
    void testNormalXmlAllowed() {
        String clientId = "xml-client";
        String xml = "<root><item>data</item></root>";

        boolean isAttack = detector.detectXmlBomb(clientId, xml, 50000);
        assertFalse(isAttack, "Should allow normal XML");
    }

    @Test
    @DisplayName("Should detect credential stuffing")
    void testCredentialStuffingDetection() {
        String clientId = "stuffing-attacker";

        boolean isStuffing = detector.detectCredentialStuffing(clientId, 5);
        assertTrue(isStuffing, "Should detect 5 consecutive failures");

        assertTrue(detector.shouldBlock(clientId));
    }

    @Test
    @DisplayName("Should allow normal auth failures")
    void testNormalAuthFailureAllowed() {
        String clientId = "normal-auth";

        boolean isStuffing = detector.detectCredentialStuffing(clientId, 2);
        assertFalse(isStuffing, "Should allow 2 failures");

        assertFalse(detector.shouldBlock(clientId));
    }

    @Test
    @DisplayName("Should track incident logs")
    void testIncidentLogging() {
        String clientId = "incident-client";

        detector.detectRateLimitAbuse(clientId, 150);
        detector.detectSqlInjection(clientId, "id", "' OR '1'='1");

        var incidents = detector.getIncidentLog(clientId);
        assertTrue(incidents.size() >= 2, "Should log multiple incidents");

        incidents.forEach(incident -> assertTrue(incident.contains(clientId)));
    }

    @Test
    @DisplayName("Should auto-block clients with many violations")
    void testAutoBlockOnMultipleViolations() {
        String clientId = "serial-attacker";

        // Generate 5 violations
        detector.detectRateLimitAbuse(clientId, 150);
        detector.detectSqlInjection(clientId, "f1", "' UNION SELECT");
        detector.detectSqlInjection(clientId, "f2", "'; DROP TABLE");
        detector.detectRateLimitAbuse(clientId, 200);
        detector.detectCredentialStuffing(clientId, 5);

        assertTrue(detector.shouldBlock(clientId), "Should auto-block after 5 violations");
    }

    @Test
    @DisplayName("Should unblock clients after manual override")
    void testManualUnblocking() {
        String clientId = "unblock-test";

        detector.detectCredentialStuffing(clientId, 5);
        assertTrue(detector.shouldBlock(clientId));

        detector.unblock(clientId);
        assertFalse(detector.shouldBlock(clientId), "Should unblock after manual override");
    }

    @Test
    @DisplayName("Should count blocked clients")
    void testBlockedClientCount() {
        detector.detectCredentialStuffing("attacker-1", 5);
        detector.detectCredentialStuffing("attacker-2", 5);

        int blockedCount = detector.getBlockedClientCount();
        assertEquals(2, blockedCount);
    }

    @Test
    @DisplayName("Should generate incident report")
    void testIncidentReporting() {
        detector.detectRateLimitAbuse("client-1", 150);
        detector.detectSqlInjection("client-2", "field", "' OR '1'='1");

        String report = detector.generateIncidentReport();
        assertTrue(report.contains("Security Incident Report"));
        assertTrue(report.contains("Total Clients:") || report.contains("Blocked"));
        assertTrue(report.contains("Summary"));
    }

    @Test
    @DisplayName("Should reject null clientId")
    void testNullClientIdValidation() {
        assertThrows(NullPointerException.class, () -> detector.detectRateLimitAbuse(null, 100));
        assertThrows(NullPointerException.class, () -> detector.detectSqlInjection(null, "field", "input"));
        assertThrows(NullPointerException.class, () -> detector.detectXmlBomb(null, "<xml/>", 1000));
        assertThrows(NullPointerException.class, () -> detector.detectCredentialStuffing(null, 5));
    }

    @Test
    @DisplayName("Should reject empty clientId")
    void testEmptyClientIdValidation() {
        assertThrows(IllegalArgumentException.class, () -> detector.detectRateLimitAbuse("", 100));
        assertThrows(IllegalArgumentException.class, () -> detector.detectSqlInjection("", "field", "input"));
        assertThrows(IllegalArgumentException.class, () -> detector.detectXmlBomb("", "<xml/>", 1000));
    }

    @Test
    @DisplayName("Should reject negative request rate")
    void testNegativeRateValidation() {
        assertThrows(IllegalArgumentException.class, () -> detector.detectRateLimitAbuse("client", -1));
    }

    @Test
    @DisplayName("Should reject negative failure count")
    void testNegativeFailureCountValidation() {
        assertThrows(IllegalArgumentException.class, () -> detector.detectCredentialStuffing("client", -1));
    }

    @Test
    @DisplayName("Should reject null fieldName in SQL detection")
    void testNullFieldNameValidation() {
        assertThrows(NullPointerException.class, () -> detector.detectSqlInjection("client", null, "input"));
    }

    @Test
    @DisplayName("Should reject null input in SQL detection")
    void testNullInputValidation() {
        assertThrows(NullPointerException.class, () -> detector.detectSqlInjection("client", "field", null));
    }

    @Test
    @DisplayName("Should reject null XML content")
    void testNullXmlValidation() {
        assertThrows(NullPointerException.class, () -> detector.detectXmlBomb("client", null, 1000));
    }

    @Test
    @DisplayName("Should return empty incident log for unknown clients")
    void testUnknownClientIncidentLog() {
        var incidents = detector.getIncidentLog("unknown-client");
        assertTrue(incidents.isEmpty());
    }

    @Test
    @DisplayName("Should return 0 violations for clean clients")
    void testCleanClientViolationCount() {
        int count = detector.getRecentViolationCount("never-seen");
        assertEquals(0, count);
    }
}
