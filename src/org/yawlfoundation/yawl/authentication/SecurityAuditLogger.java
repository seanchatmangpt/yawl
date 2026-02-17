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

package org.yawlfoundation.yawl.authentication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Centralized security audit logger for YAWL.
 *
 * SOC2 HIGH finding: security events must be logged with timestamps, user, action,
 * and result. This class provides structured audit log entries for all security-relevant
 * events in a consistent format suitable for SIEM ingestion.
 *
 * Log format (pipe-delimited for easy parsing):
 *   SECURITY_AUDIT | timestamp(ISO8601) | event | user | source | result | detail
 *
 * Audit events covered:
 *   - LOGIN_SUCCESS / LOGIN_FAILURE
 *   - LOGOUT
 *   - AUTH_TOKEN_ISSUED / AUTH_TOKEN_EXPIRED / AUTH_TOKEN_REJECTED
 *   - CSRF_VALIDATION_FAILURE
 *   - RATE_LIMIT_EXCEEDED
 *   - CORS_ORIGIN_REJECTED
 *   - SESSION_CREATED / SESSION_EXPIRED / SESSION_INVALIDATED
 *   - ACCESS_DENIED
 *   - CREDENTIAL_CHANGE
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public final class SecurityAuditLogger {

    /** Dedicated audit logger - configure a separate appender in log4j2.xml for SIEM. */
    private static final Logger AUDIT_LOG = LogManager.getLogger("YAWL.SECURITY.AUDIT");

    /** ISO 8601 UTC formatter for all audit timestamps. */
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    /** Singleton - no state, all methods are static. */
    private SecurityAuditLogger() {
        throw new UnsupportedOperationException(
                "SecurityAuditLogger is a static utility class and cannot be instantiated");
    }

    /**
     * Logs a successful authentication event.
     *
     * @param username  the authenticated user
     * @param source    client IP or service identifier
     * @param detail    optional additional context (e.g. "via JWT", "via session")
     */
    public static void loginSuccess(String username, String source, String detail) {
        log("LOGIN_SUCCESS", username, source, "SUCCESS", detail);
    }

    /**
     * Logs a failed authentication event.
     *
     * @param username  the attempted username (may be null if not provided)
     * @param source    client IP or service identifier
     * @param reason    reason for failure (e.g. "bad credentials", "account locked")
     */
    public static void loginFailure(String username, String source, String reason) {
        log("LOGIN_FAILURE", username, source, "FAILURE", reason);
    }

    /**
     * Logs a logout event.
     *
     * @param username the user logging out
     * @param source   client IP or service identifier
     */
    public static void logout(String username, String source) {
        log("LOGOUT", username, source, "SUCCESS", "session terminated");
    }

    /**
     * Logs issuance of an authentication token (JWT, session handle).
     *
     * @param username   the user for whom the token was issued
     * @param tokenType  "JWT", "SESSION_HANDLE", etc.
     * @param source     client IP or service identifier
     */
    public static void tokenIssued(String username, String tokenType, String source) {
        log("AUTH_TOKEN_ISSUED", username, source, "SUCCESS", "type=" + tokenType);
    }

    /**
     * Logs rejection of an authentication token.
     *
     * @param username  the claimed identity (may be null if token is unverifiable)
     * @param tokenType "JWT", "SESSION_HANDLE", etc.
     * @param source    client IP or service identifier
     * @param reason    rejection reason
     */
    public static void tokenRejected(String username, String tokenType, String source,
                                     String reason) {
        log("AUTH_TOKEN_REJECTED", username, source, "FAILURE",
                "type=" + tokenType + " reason=" + reason);
    }

    /**
     * Logs a CSRF token validation failure.
     *
     * @param username  the session user (may be null for anonymous sessions)
     * @param source    client IP or service identifier
     * @param endpoint  the request URI where validation failed
     */
    public static void csrfValidationFailure(String username, String source, String endpoint) {
        log("CSRF_VALIDATION_FAILURE", username, source, "FAILURE", "endpoint=" + endpoint);
    }

    /**
     * Logs that rate limiting blocked a request.
     *
     * @param source    client IP that exceeded the limit
     * @param endpoint  the request URI
     * @param limit     the rate limit threshold that was exceeded
     */
    public static void rateLimitExceeded(String source, String endpoint, int limit) {
        log("RATE_LIMIT_EXCEEDED", "anonymous", source, "BLOCKED",
                "endpoint=" + endpoint + " limit=" + limit);
    }

    /**
     * Logs that a CORS request was rejected due to an unlisted origin.
     *
     * @param origin    the rejected Origin header value
     * @param endpoint  the request URI
     */
    public static void corsOriginRejected(String origin, String endpoint) {
        log("CORS_ORIGIN_REJECTED", "anonymous", origin, "BLOCKED",
                "endpoint=" + endpoint);
    }

    /**
     * Logs session creation.
     *
     * @param username  the authenticated user
     * @param sessionId the session identifier (abbreviated for privacy if needed)
     * @param source    client IP or service identifier
     */
    public static void sessionCreated(String username, String sessionId, String source) {
        log("SESSION_CREATED", username, source, "SUCCESS",
                "sessionId=" + abbreviate(sessionId, 8));
    }

    /**
     * Logs session expiry.
     *
     * @param username  the session owner
     * @param sessionId the session identifier
     */
    public static void sessionExpired(String username, String sessionId) {
        log("SESSION_EXPIRED", username, "server", "INFO",
                "sessionId=" + abbreviate(sessionId, 8));
    }

    /**
     * Logs an explicit session invalidation (e.g. forced logout by admin).
     *
     * @param username    the session owner
     * @param sessionId   the session identifier
     * @param invokedBy   who triggered the invalidation
     */
    public static void sessionInvalidated(String username, String sessionId, String invokedBy) {
        log("SESSION_INVALIDATED", username, invokedBy, "SUCCESS",
                "sessionId=" + abbreviate(sessionId, 8));
    }

    /**
     * Logs an access-denied event (authorisation failure, not authentication).
     *
     * @param username   the requesting user
     * @param source     client IP or service identifier
     * @param resource   the protected resource that was denied
     * @param reason     reason for denial
     */
    public static void accessDenied(String username, String source, String resource,
                                    String reason) {
        log("ACCESS_DENIED", username, source, "FAILURE",
                "resource=" + resource + " reason=" + reason);
    }

    /**
     * Logs a credential change event (password reset, key rotation, etc.)
     *
     * @param username   the user whose credentials changed
     * @param changeType type of credential change (e.g. "PASSWORD_RESET", "KEY_ROTATION")
     * @param invokedBy  who triggered the change ("self" or admin username)
     */
    public static void credentialChange(String username, String changeType, String invokedBy) {
        log("CREDENTIAL_CHANGE", username, invokedBy, "SUCCESS", "changeType=" + changeType);
    }

    /**
     * Core audit logging method. Writes a structured, pipe-delimited log entry to the
     * dedicated security audit logger at INFO level.
     *
     * @param event    the security event type
     * @param user     the user involved (use "anonymous" or "unknown" if not available)
     * @param source   client IP, service name, or "server" for server-initiated events
     * @param result   outcome: SUCCESS, FAILURE, BLOCKED, INFO
     * @param detail   additional context
     */
    private static void log(String event, String user, String source,
                            String result, String detail) {
        String timestamp = TIMESTAMP_FMT.format(Instant.now());
        String safeUser = user != null ? user : "unknown";
        String safeSource = source != null ? source : "unknown";
        String safeDetail = detail != null ? detail : "";

        AUDIT_LOG.info("SECURITY_AUDIT | {} | {} | {} | {} | {} | {}",
                timestamp, event, safeUser, safeSource, result, safeDetail);
    }

    /**
     * Returns the first {@code maxChars} characters of {@code value} suffixed with "..."
     * if it exceeds that length, to avoid logging full session identifiers.
     */
    private static String abbreviate(String value, int maxChars) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...";
    }
}
