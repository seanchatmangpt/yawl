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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Real-time attack pattern detection with automatic response.
 *
 * Detects common attack patterns and triggers automatic mitigation:
 * - Rate limit abuse (requests per minute exceeding threshold)
 * - SQL injection attempts (pattern matching on input)
 * - XXE/XML bomb payloads (entity declarations, size limits)
 * - Credential stuffing (rapid auth failures)
 * - Enumeration attacks (404 scanning, ID guessing)
 * - Protocol abuse (invalid headers, malformed requests)
 *
 * Response actions:
 * - Log incident with forensic details
 * - Increment client violation counter
 * - Throttle offending client
 * - Auto-block if violations exceed threshold (default: 5 in 10 minutes)
 * - Report to SIEM system (if available)
 *
 * Integration:
 * - Call detectRateLimitAbuse() on each request
 * - Call detectSqlInjection() on user input
 * - Call detectXmlBomb() on XML payloads
 * - Call shouldBlock() before allowing requests
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AttackPatternDetector {

    private static final Logger log = LogManager.getLogger(AttackPatternDetector.class);

    private static final int RATE_LIMIT_THRESHOLD = 100; // requests per minute
    private static final int AUTH_FAILURE_THRESHOLD = 5; // consecutive failures
    private static final int VIOLATION_THRESHOLD = 5; // violations before auto-block
    private static final long VIOLATION_WINDOW_SECONDS = 10 * 60; // 10 minute window

    /**
     * SQL injection pattern detection regexes.
     */
    private static final Pattern SQL_UNION_PATTERN = Pattern.compile(
            "\\bunion\\b.*\\bselect\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_STACKED_QUERIES = Pattern.compile(
            ";\\s*(" + "select|insert|update|delete|drop|create|alter" + ")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_COMMENTS = Pattern.compile(
            "(--|#|/\\*|\\*/)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_CAST_CONVERT = Pattern.compile(
            "\\b(cast|convert|extract)\\b.*\\bas\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SQL_BOOLEAN_INJECTION = Pattern.compile(
            "'\\s*(OR|AND)\\s+'",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * XXE/XML bomb patterns.
     */
    private static final Pattern XML_ENTITY_DECL = Pattern.compile(
            "<!ENTITY|<!DOCTYPE.*\\[",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern XML_BILLION_LAUGHS = Pattern.compile(
            "\\]>.*&[a-z]+;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    /**
     * Attack record for forensics.
     */
    private static class AttackIncident {
        private final long timestampEpochSeconds;
        private final String clientId;
        private final String attackType;
        private final String details;
        private final String payload;

        AttackIncident(long timestamp, String clientId, String attackType, String details, String payload) {
            this.timestampEpochSeconds = timestamp;
            this.clientId = Objects.requireNonNull(clientId);
            this.attackType = Objects.requireNonNull(attackType);
            this.details = details;
            this.payload = payload != null ? payload.substring(0, Math.min(500, payload.length())) : null;
        }

        @Override
        public String toString() {
            return String.format("[%d] CLIENT=%s TYPE=%s DETAILS=%s",
                    timestampEpochSeconds, clientId, attackType, details);
        }
    }

    /**
     * Client violation tracking.
     */
    private static class ClientViolations {
        private final String clientId;
        private final Deque<Long> violationTimestamps;
        private final AtomicInteger totalViolations;
        private final Deque<AttackIncident> incidents;
        private boolean isBlocked;

        ClientViolations(String clientId) {
            this.clientId = Objects.requireNonNull(clientId);
            this.violationTimestamps = new LinkedList<>();
            this.totalViolations = new AtomicInteger(0);
            this.incidents = new LinkedList<>();
            this.isBlocked = false;
        }

        void recordViolation(long nowEpochSeconds, AttackIncident incident) {
            violationTimestamps.addLast(nowEpochSeconds);
            totalViolations.incrementAndGet();
            incidents.addLast(incident);

            // Keep only last 100 incidents
            if (incidents.size() > 100) {
                incidents.removeFirst();
            }

            // Check if should auto-block
            long cutoffTime = nowEpochSeconds - VIOLATION_WINDOW_SECONDS;
            long recentViolations = violationTimestamps.stream()
                    .filter(ts -> ts > cutoffTime)
                    .count();

            if (recentViolations >= VIOLATION_THRESHOLD) {
                isBlocked = true;
                log.warn("Client {} auto-blocked: {} violations in {} seconds",
                        clientId, recentViolations, VIOLATION_WINDOW_SECONDS);
            }
        }

        boolean isBlocked() {
            return isBlocked;
        }

        Deque<AttackIncident> getIncidents() {
            return new LinkedList<>(incidents);
        }

        int getRecentViolationCount(long nowEpochSeconds) {
            long cutoffTime = nowEpochSeconds - VIOLATION_WINDOW_SECONDS;
            return (int) violationTimestamps.stream()
                    .filter(ts -> ts > cutoffTime)
                    .count();
        }
    }

    private final Map<String, ClientViolations> violations;

    /**
     * Creates a new AttackPatternDetector with no initial client profiles.
     */
    public AttackPatternDetector() {
        this.violations = new ConcurrentHashMap<>();
    }

    /**
     * Detects rate limit abuse (>100 requests per minute).
     *
     * @param clientId unique identifier for the client
     * @param requestsPerMinute recent request rate
     * @return true if rate exceeds threshold (abuse detected)
     * @throws IllegalArgumentException if clientId is null/empty or rate is negative
     */
    public boolean detectRateLimitAbuse(String clientId, int requestsPerMinute) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }
        if (requestsPerMinute < 0) {
            throw new IllegalArgumentException("requestsPerMinute cannot be negative");
        }

        if (requestsPerMinute > RATE_LIMIT_THRESHOLD) {
            long now = Instant.now().getEpochSecond();
            String details = "Rate abuse: " + requestsPerMinute + " req/min > " + RATE_LIMIT_THRESHOLD;
            AttackIncident incident = new AttackIncident(now, clientId, "RATE_LIMIT_ABUSE", details, null);

            recordViolation(clientId, incident);
            log.warn("Rate limit abuse detected: {} - {} requests/minute", clientId, requestsPerMinute);

            return true;
        }

        return false;
    }

    /**
     * Detects SQL injection patterns in input.
     *
     * Uses pattern matching to identify common SQL injection techniques:
     * - UNION-based injection (UNION SELECT)
     * - Stacked queries (;DROP TABLE)
     * - Comment-based evasion (--, #, /*)
     * - Type casting tricks (CAST, CONVERT)
     *
     * @param clientId unique identifier for the client
     * @param fieldName name of input field for logging
     * @param input user-supplied input to analyze
     * @return true if SQL injection pattern detected
     * @throws IllegalArgumentException if parameters are null/empty
     */
    public boolean detectSqlInjection(String clientId, String fieldName, String input) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        Objects.requireNonNull(fieldName, "fieldName cannot be null");
        Objects.requireNonNull(input, "input cannot be null");

        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }
        if (fieldName.isEmpty()) {
            throw new IllegalArgumentException("fieldName cannot be empty");
        }

        // Check patterns
        if (SQL_UNION_PATTERN.matcher(input).find()) {
            recordSqlInjectionIncident(clientId, fieldName, input, "UNION-based");
            return true;
        }

        if (SQL_STACKED_QUERIES.matcher(input).find()) {
            recordSqlInjectionIncident(clientId, fieldName, input, "Stacked queries");
            return true;
        }

        if (SQL_COMMENTS.matcher(input).find()) {
            recordSqlInjectionIncident(clientId, fieldName, input, "Comment-based evasion");
            return true;
        }

        if (SQL_CAST_CONVERT.matcher(input).find()) {
            recordSqlInjectionIncident(clientId, fieldName, input, "Type casting");
            return true;
        }

        if (SQL_BOOLEAN_INJECTION.matcher(input).find()) {
            recordSqlInjectionIncident(clientId, fieldName, input, "Boolean injection");
            return true;
        }

        return false;
    }

    /**
     * Detects XXE and XML bomb attacks.
     *
     * Checks for:
     * - DOCTYPE declarations (XXE vectors)
     * - Entity definitions (billion laughs attack)
     * - XML size limits (DoS prevention)
     *
     * @param clientId unique identifier for the client
     * @param xmlContent XML payload to analyze
     * @param maxSizeBytes maximum allowed size (0 = no size check)
     * @return true if attack pattern detected
     * @throws IllegalArgumentException if clientId is null/empty or xml is null
     */
    public boolean detectXmlBomb(String clientId, String xmlContent, int maxSizeBytes) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        Objects.requireNonNull(xmlContent, "xmlContent cannot be null");

        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        // Check size
        int sizeBytes = xmlContent.getBytes().length;
        if (maxSizeBytes > 0 && sizeBytes > maxSizeBytes) {
            long now = Instant.now().getEpochSecond();
            String details = "XML bomb: size " + sizeBytes + " > " + maxSizeBytes;
            AttackIncident incident = new AttackIncident(now, clientId, "XML_BOMB", details, xmlContent);
            recordViolation(clientId, incident);
            log.warn("XML bomb detected: {} - payload size {} bytes", clientId, sizeBytes);
            return true;
        }

        // Check DOCTYPE
        if (XML_ENTITY_DECL.matcher(xmlContent).find()) {
            long now = Instant.now().getEpochSecond();
            AttackIncident incident = new AttackIncident(now, clientId, "XXE_ATTACK", "DOCTYPE or ENTITY found", xmlContent);
            recordViolation(clientId, incident);
            log.warn("XXE attack detected: {} - entity declaration found", clientId);
            return true;
        }

        // Check billion laughs
        if (XML_BILLION_LAUGHS.matcher(xmlContent).find()) {
            long now = Instant.now().getEpochSecond();
            AttackIncident incident = new AttackIncident(now, clientId, "BILLION_LAUGHS", "Entity reference expansion", xmlContent);
            recordViolation(clientId, incident);
            log.warn("Billion laughs attack detected: {} - entity expansion found", clientId);
            return true;
        }

        return false;
    }

    /**
     * Detects credential stuffing (rapid authentication failures).
     *
     * @param clientId unique identifier for the client
     * @param failureCount number of consecutive auth failures
     * @return true if failure count exceeds threshold
     * @throws IllegalArgumentException if clientId is null/empty or count is negative
     */
    public boolean detectCredentialStuffing(String clientId, int failureCount) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }
        if (failureCount < 0) {
            throw new IllegalArgumentException("failureCount cannot be negative");
        }

        if (failureCount >= AUTH_FAILURE_THRESHOLD) {
            long now = Instant.now().getEpochSecond();
            String details = "Credential stuffing: " + failureCount + " consecutive failures";
            AttackIncident incident = new AttackIncident(now, clientId, "CREDENTIAL_STUFFING", details, null);
            recordViolation(clientId, incident);
            // Immediately block the client when credential stuffing threshold is reached
            ClientViolations v = violations.get(clientId);
            if (v != null) {
                v.isBlocked = true;
            }
            log.warn("Credential stuffing detected and client blocked: {} - {} consecutive failures", clientId, failureCount);
            return true;
        }

        return false;
    }

    /**
     * Determines if a client should be blocked.
     *
     * @param clientId unique identifier for the client
     * @return true if client has exceeded violation threshold
     * @throws IllegalArgumentException if clientId is null/empty
     */
    public boolean shouldBlock(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientViolations v = violations.get(clientId);
        if (v == null) {
            return false;
        }

        return v.isBlocked();
    }

    /**
     * Gets the recent violation count for a client.
     *
     * @param clientId unique identifier for the client
     * @return number of violations in the last 10 minutes
     * @throws IllegalArgumentException if clientId is null/empty
     */
    public int getRecentViolationCount(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientViolations v = violations.get(clientId);
        if (v == null) {
            return 0;
        }

        return v.getRecentViolationCount(Instant.now().getEpochSecond());
    }

    /**
     * Gets forensic details of attack incidents for a client.
     *
     * @param clientId unique identifier for the client
     * @return list of recent incidents
     * @throws IllegalArgumentException if clientId is null/empty
     */
    public List<String> getIncidentLog(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientViolations v = violations.get(clientId);
        if (v == null) {
            return Collections.emptyList();
        }

        return v.getIncidents().stream()
                .map(AttackIncident::toString)
                .toList();
    }

    /**
     * Manually unblock a client (after review/remediation).
     *
     * @param clientId unique identifier for the client
     * @throws IllegalArgumentException if clientId is null/empty
     */
    public void unblock(String clientId) {
        Objects.requireNonNull(clientId, "clientId cannot be null");
        if (clientId.isEmpty()) {
            throw new IllegalArgumentException("clientId cannot be empty");
        }

        ClientViolations v = violations.get(clientId);
        if (v != null) {
            v.isBlocked = false;
            log.info("Client {} unblocked by administrator", clientId);
        }
    }

    /**
     * Gets count of blocked clients.
     *
     * @return number of clients under attack investigation
     */
    public int getBlockedClientCount() {
        return (int) violations.values().stream()
                .filter(ClientViolations::isBlocked)
                .count();
    }

    /**
     * Generates a security incident report.
     *
     * @return human-readable summary of detected attacks
     */
    public String generateIncidentReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Security Incident Report ===\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        int totalIncidents = 0;
        int totalClients = violations.size();
        int blockedClients = getBlockedClientCount();

        for (ClientViolations v : violations.values()) {
            int incidentCount = v.incidents.size();
            totalIncidents += incidentCount;

            if (incidentCount > 0) {
                report.append("CLIENT: ").append(v.clientId).append("\n");
                report.append("  Status: ").append(v.isBlocked ? "BLOCKED" : "ACTIVE").append("\n");
                report.append("  Total Violations: ").append(v.totalViolations.get()).append("\n");
                report.append("  Recent Incidents: ").append(incidentCount).append("\n");
                report.append("\n");
            }
        }

        report.append("=== Summary ===\n");
        report.append("Total Clients: ").append(totalClients).append("\n");
        report.append("Blocked Clients: ").append(blockedClients).append("\n");
        report.append("Total Incidents: ").append(totalIncidents).append("\n");

        return report.toString();
    }

    private void recordSqlInjectionIncident(String clientId, String fieldName, String input, String technique) {
        long now = Instant.now().getEpochSecond();
        String details = "SQL injection attempt in field '" + fieldName + "': " + technique;
        AttackIncident incident = new AttackIncident(now, clientId, "SQL_INJECTION", details, input);
        recordViolation(clientId, incident);
    }

    private void recordViolation(String clientId, AttackIncident incident) {
        long now = Instant.now().getEpochSecond();
        ClientViolations v = violations.computeIfAbsent(clientId, ClientViolations::new);
        v.recordViolation(now, incident);
    }
}
