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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for A2ASecurityValidator class which provides
 * security validation for A2A handoff tokens with authorization policies.
 */
class A2ASecurityValidatorTest {

    private A2ASecurityValidator securityValidator;
    private HandoffToken tokenManager;
    private static final byte[] SECRET_KEY = new byte[32];
    private static final String TEST_AGENT_ID = "test-security-agent";
    private static final String SKILL_INVOKE_TOKEN = "PERM_SKILL_INVOKE";
    private static final String HANDOFF_TOKEN = "PERM_HANDOFF";
    private static final String MARKETPLACE_TOKEN = "PERM_MARKETPLACE";

    @BeforeEach
    void setUp() {
        new java.security.SecureRandom().nextBytes(SECRET_KEY);
        tokenManager = new HandoffToken(SECRET_KEY);
        securityValidator = new A2ASecurityValidator(tokenManager);
    }

    // region Constructor Tests

    @Test
    @DisplayName("Constructor with valid token manager creates security validator")
    void constructor_withValidTokenManager_createsSecurityValidator() {
        A2ASecurityValidator validator = new A2ASecurityValidator(tokenManager);
        assertNotNull(validator);
    }

    @Test
    @DisplayName("Constructor with null token manager throws NullPointerException")
    void constructor_withNullTokenManager_throwsNullPointerException() {
        assertThrows(
            NullPointerException.class,
            () -> new A2ASecurityValidator(null)
        );
    }

    // endregion

    // region Scope Validation Tests

    @Test
    @DisplayName("Validate for valid skill invoke scope succeeds")
    void validateForValidSkillInvokeScope_succeeds() {
        String token = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        assertDoesNotThrow(() -> {
            HandoffToken.TokenClaims claims = securityValidator.validateForSkillInvocation(token);
            assertEquals(TEST_AGENT_ID, claims.agentId());
            assertEquals(SKILL_INVOKE_TOKEN, claims.scope());
        });
    }

    @ParameterizedTest
    @CsvSource({
        "INVALID_SCOPE",
        "PERM_INVALID",
        "random_scope",
        "PERM_ADMIN"
    })
    @DisplayName("Generate token with invalid scope throws Exception")
    void generateToken_withInvalidScope_throwsException(String invalidScope) {
        assertThrows(
            Exception.class,
            () -> securityValidator.generateToken(TEST_AGENT_ID, invalidScope)
        );
    }

    @Test
    @DisplayName("Get valid scopes returns correct set")
    void getValidScopes_returnsCorrectSet() {
        Set<String> validScopes = A2ASecurityValidator.getValidScopes();

        assertEquals(3, validScopes.size());
        assertTrue(validScopes.contains(SKILL_INVOKE_TOKEN));
        assertTrue(validScopes.contains(HANDOFF_TOKEN));
        assertTrue(validScopes.contains(MARKETPLACE_TOKEN));

        // Verify immutability
        assertThrows(UnsupportedOperationException.class, () -> validScopes.add("INVALID"));
    }

    // endregion

    // region Skill Invocation Validation Tests

    @Test
    @DisplayName("Validate for skill invocation with valid token succeeds")
    void validateForSkillInvocation_withValidToken_succeeds() throws Exception {
        String token = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);
        HandoffToken.TokenClaims claims = securityValidator.validateForSkillInvocation(token);

        assertEquals(TEST_AGENT_ID, claims.agentId());
        assertEquals(SKILL_INVOKE_TOKEN, claims.scope());
    }

    @Test
    @DisplayName("Validate for skill invocation with wrong scope throws Exception")
    void validateForSkillInvocation_withWrongScope_throwsException() {
        String token = tokenManager.generateToken(TEST_AGENT_ID, HANDOFF_TOKEN);

        assertThrows(
            Exception.class,
            () -> securityValidator.validateForSkillInvocation(token)
        );
    }

    @Test
    @DisplayName("Validate for skill invocation with empty scope throws Exception")
    void validateForSkillInvocation_withEmptyScope_throwsException() throws Exception {
        // Create a token with empty scope
        String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            "{\"iss\":\"yawl-a2a\",\"agentId\":\"test\",\"scope\":\"\",\"iat\":1234567890,\"exp\":1234567890,\"jti\":\"test\"}".getBytes()
        );
        String signature = TestUtils.sign(tokenManager, header + "." + payload);
        String emptyScopeToken = header + "." + payload + "." + signature;

        assertThrows(
            Exception.class,
            () -> securityValidator.validateForSkillInvocation(emptyScopeToken)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Validate for skill invocation with null/empty token throws Exception")
    void validateForSkillInvocation_withNullOrEmptyToken_throwsException(String invalidToken) {
        assertThrows(
            Exception.class,
            () -> securityValidator.validateForSkillInvocation(invalidToken)
        );
    }

    @Test
    @DisplayName("Validate for skill invocation with malformed token throws Exception")
    void validateForSkillInvocation_withMalformedToken_throwsException() {
        String malformedToken = "invalid";
        assertThrows(
            Exception.class,
            () -> securityValidator.validateForSkillInvocation(malformedToken)
        );
    }

    // endregion

    // region Handoff Validation Tests

    @Test
    @DisplayName("Validate for handoff with valid token succeeds")
    void validateForHandoff_withValidToken_succeeds() throws Exception {
        String token = tokenManager.generateToken(TEST_AGENT_ID, HANDOFF_TOKEN);
        HandoffToken.TokenClaims claims = securityValidator.validateForHandoff(token);

        assertEquals(TEST_AGENT_ID, claims.agentId());
        assertEquals(HANDOFF_TOKEN, claims.scope());
    }

    @Test
    @DisplayName("Validate for handoff with wrong scope throws Exception")
    void validateForHandoff_withWrongScope_throwsException() {
        String token = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        assertThrows(
            Exception.class,
            () -> securityValidator.validateForHandoff(token)
        );
    }

    // endregion

    // region Marketplace Validation Tests

    @Test
    @DisplayName("Validate for marketplace with valid token succeeds")
    void validateForMarketplace_withValidToken_succeeds() throws Exception {
        String token = tokenManager.generateToken(TEST_AGENT_ID, MARKETPLACE_TOKEN);
        HandoffToken.TokenClaims claims = securityValidator.validateForMarketplace(token);

        assertEquals(TEST_AGENT_ID, claims.agentId());
        assertEquals(MARKETPLACE_TOKEN, claims.scope());
    }

    @Test
    @DisplayName("Validate for marketplace with skill invoke scope throws Exception")
    void validateForMarketplace_withSkillInvokeScope_throwsException() {
        String token = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        assertThrows(
            Exception.class,
            () -> securityValidator.validateForMarketplace(token)
        );
    }

    // endregion

    // region Token Generation Tests

    @Test
    @DisplayName("Generate token with valid parameters succeeds")
    void generateToken_withValidParameters_succeeds() throws Exception {
        String token = securityValidator.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        // Verify the generated token is valid
        HandoffToken.TokenClaims claims = tokenManager.verifyToken(token);
        assertEquals(TEST_AGENT_ID, claims.agentId());
        assertEquals(SKILL_INVOKE_TOKEN, claims.scope());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Generate token with null/empty agentId throws Exception")
    void generateToken_withNullOrEmptyAgentId_throwsException(String invalidAgentId) {
        assertThrows(
            Exception.class,
            () -> securityValidator.generateToken(invalidAgentId, SKILL_INVOKE_TOKEN)
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Generate token with null/empty scope throws Exception")
    void generateToken_withNullOrEmptyScope_throwsException(String invalidScope) {
        assertThrows(
            Exception.class,
            () -> securityValidator.generateToken(TEST_AGENT_ID, invalidScope)
        );
    }

    // endregion

    // region Authorization Tests

    @Test
    @DisplayName("Is authorized with valid token and scope succeeds")
    void isAuthorized_withValidTokenAndScope_succeeds() {
        String token = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        assertDoesNotThrow(() -> {
            boolean authorized = securityValidator.isAuthorized(token, SKILL_INVOKE_TOKEN, null);
            assertTrue(authorized);
        });
    }

    @Test
    @DisplayName("Is authorized with wrong scope throws Exception")
    void isAuthorized_withWrongScope_throwsException() {
        String token = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        assertThrows(
            Exception.class,
            () -> securityValidator.isAuthorized(token, HANDOFF_TOKEN, null)
        );
    }

    // endregion

    // region Security Tests

    @Test
    @DisplayName("Security validator prevents privilege escalation")
    void securityValidator_preventsPrivilegeEscalation() {
        // Create a token with minimal permissions
        String minimalToken = tokenManager.generateToken(TEST_AGENT_ID, SKILL_INVOKE_TOKEN);

        // Try to use it for a different operation
        assertThrows(
            Exception.class,
            () -> securityValidator.validateForMarketplace(minimalToken)
        );
    }

    @Test
    @DisplayName("Security validator rejects tokens with scope injection")
    void securityValidator_rejectsScopeInjection() {
        // Try to inject an arbitrary scope
        String[] invalidScopes = {
            "PERM_SKILL_INVOKE;DROP TABLE users;--",
            "PERM_ADMIN_INJECTED"
        };

        for (String invalidScope : invalidScopes) {
            assertThrows(
                Exception.class,
                () -> securityValidator.generateToken(TEST_AGENT_ID, invalidScope)
            );
        }
    }

    // endregion
}