/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.config;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Validates A2A handoff tokens and enforces security policies.
 *
 * <p>Verifies JWT token signatures, expiration, scope claims, and enforces
 * authorization rules for agent-to-agent handoffs. Prevents privilege escalation
 * by requiring explicit scope declarations.</p>
 *
 * <h2>Security Policies</h2>
 * <ul>
 *   <li>All tokens must have valid JWT signature</li>
 *   <li>All tokens must not be expired</li>
 *   <li>All tokens must declare explicit scope (no default privileges)</li>
 *   <li>Missing scope = DENIED (not PERM_ALL)</li>
 *   <li>Scope must match operation being performed</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class A2ASecurityValidator {

    private static final String PERM_SKILL_INVOKE = "PERM_SKILL_INVOKE";
    private static final String PERM_HANDOFF = "PERM_HANDOFF";
    private static final String PERM_MARKETPLACE = "PERM_MARKETPLACE";
    private static final Set<String> VALID_SCOPES = new HashSet<>(
        Set.of(PERM_SKILL_INVOKE, PERM_HANDOFF, PERM_MARKETPLACE)
    );

    private final HandoffToken tokenManager;

    /**
     * Creates a security validator with the specified token manager.
     *
     * @param tokenManager the handoff token manager
     * @throws IllegalArgumentException if tokenManager is null
     */
    public A2ASecurityValidator(HandoffToken tokenManager) {
        this.tokenManager = Objects.requireNonNull(tokenManager, "tokenManager cannot be null");
    }

    /**
     * Validates a handoff token for skill invocation.
     *
     * <p>Checks that token is valid, not expired, and has PERM_SKILL_INVOKE scope.</p>
     *
     * @param token the JWT token to validate
     * @return the validated token claims
     * @throws ValidationException if token is invalid or lacks required scope
     */
    public HandoffToken.TokenClaims validateForSkillInvocation(String token) throws ValidationException {
        return validateForScope(token, PERM_SKILL_INVOKE);
    }

    /**
     * Validates a handoff token for agent handoff.
     *
     * <p>Checks that token is valid, not expired, and has PERM_HANDOFF scope.</p>
     *
     * @param token the JWT token to validate
     * @return the validated token claims
     * @throws ValidationException if token is invalid or lacks required scope
     */
    public HandoffToken.TokenClaims validateForHandoff(String token) throws ValidationException {
        return validateForScope(token, PERM_HANDOFF);
    }

    /**
     * Validates a handoff token for marketplace operations.
     *
     * <p>Checks that token is valid, not expired, and has PERM_MARKETPLACE scope.</p>
     *
     * @param token the JWT token to validate
     * @return the validated token claims
     * @throws ValidationException if token is invalid or lacks required scope
     */
    public HandoffToken.TokenClaims validateForMarketplace(String token) throws ValidationException {
        return validateForScope(token, PERM_MARKETPLACE);
    }

    /**
     * Validates a token has the required scope.
     *
     * @param token the JWT token
     * @param requiredScope the required scope
     * @return the validated claims
     * @throws ValidationException if validation fails
     */
    private HandoffToken.TokenClaims validateForScope(String token, String requiredScope)
        throws ValidationException {
        if (token == null || token.isEmpty()) {
            throw new ValidationException("Token cannot be null or empty");
        }

        HandoffToken.TokenClaims claims;
        try {
            claims = tokenManager.verifyToken(token);
        } catch (HandoffToken.TokenVerificationException e) {
            throw new ValidationException("Token verification failed: " + e.getMessage(), e);
        }

        // Critical security check: if scope is missing, deny access (not grant default)
        if (claims.scope() == null || claims.scope().isEmpty()) {
            throw new ValidationException("Token has no scope - access DENIED");
        }

        // Verify scope matches required permission
        if (!requiredScope.equals(claims.scope())) {
            throw new ValidationException(
                "Token scope '" + claims.scope() + "' does not match required scope '" + requiredScope + "'");
        }

        // Verify scope is valid (prevent injection of arbitrary scopes)
        if (!VALID_SCOPES.contains(claims.scope())) {
            throw new ValidationException("Invalid scope: " + claims.scope());
        }

        return claims;
    }

    /**
     * Generates a new token for the specified agent with the given scope.
     *
     * @param agentId the agent identifier
     * @param scope the scope (e.g., PERM_SKILL_INVOKE, PERM_HANDOFF, PERM_MARKETPLACE)
     * @return the signed JWT token
     * @throws ValidationException if agentId or scope is invalid
     */
    public String generateToken(String agentId, String scope) throws ValidationException {
        if (agentId == null || agentId.isEmpty()) {
            throw new ValidationException("agentId cannot be null or empty");
        }
        if (scope == null || scope.isEmpty()) {
            throw new ValidationException("scope cannot be null or empty");
        }
        if (!VALID_SCOPES.contains(scope)) {
            throw new ValidationException("Invalid scope: " + scope);
        }

        try {
            return tokenManager.generateToken(agentId, scope);
        } catch (Exception e) {
            throw new ValidationException("Failed to generate token", e);
        }
    }

    /**
     * Validates that the caller (indicated by token) is authorized for an operation.
     *
     * @param token the caller's token
     * @param requiredScope the scope required for the operation
     * @param targetAgent the agent being accessed (optional validation)
     * @return true if authorized
     * @throws ValidationException if validation fails
     */
    public boolean isAuthorized(String token, String requiredScope, String targetAgent)
        throws ValidationException {
        HandoffToken.TokenClaims claims = validateForScope(token, requiredScope);
        // Additional authorization checks could be added here (e.g., ACLs)
        return true;
    }

    /**
     * Exception thrown when security validation fails.
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Gets the set of valid scopes.
     *
     * @return immutable set of valid scope values
     */
    public static Set<String> getValidScopes() {
        return Set.copyOf(VALID_SCOPES);
    }
}
