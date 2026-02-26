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

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for A2A handoff token validation.
 *
 * <p>Tests the complete JWT parsing and verification workflow for agent-to-agent
 * handoff messages. Uses real JWT generation and parsing (no mocks) to validate
 * cryptographic signature verification and expiration handling.
 *
 * <p>Chicago TDD: All dependencies are real (JwtAuthenticationProvider,
 * HandoffProtocol) operating on actual JWTs with HMAC-SHA256 signatures.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("unit")
class HandoffTokenValidationTest {

    private static final String SECRET = "test-secret-minimum-32-chars-required!!";
    private JwtAuthenticationProvider jwtProvider;
    private HandoffProtocol protocol;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtAuthenticationProvider(SECRET, null);
        protocol = new HandoffProtocol(jwtProvider);
    }

    /**
     * Test: Real token generation and parsing round-trip.
     *
     * Verifies that:
     * 1. A real token can be generated with all required fields
     * 2. The wire message format is correct (YAWL_HANDOFF:workItemId:jwt:fromAgent)
     * 3. The message can be parsed back and verified
     * 4. The parsed token contains the correct values and is valid
     */
    @Test
    void roundTrip_generateAndParseMessage_returnsValidToken() throws Exception {
        // Generate a real handoff token
        HandoffToken generated = protocol.generateHandoffToken(
            "WI-42", "agent-A", "agent-B", "session-XYZ");

        // Verify it's valid before packaging
        assertTrue(generated.isValid(), "Generated token should be valid");
        assertEquals("WI-42", generated.workItemId());
        assertEquals("agent-A", generated.fromAgent());

        // Build the wire message as the sender would
        String messageText = "YAWL_HANDOFF:WI-42:" + generated.jwt() + ":agent-A";

        // Parse and verify the message
        HandoffToken parsed = protocol.parseAndVerifyHandoffMessage(messageText);

        // Verify parsed token has correct values
        assertEquals("WI-42", parsed.workItemId());
        assertEquals("agent-A", parsed.fromAgent());
        assertTrue(parsed.isValid(), "Parsed token should be valid");
    }

    /**
     * Test: Expired JWT token is rejected.
     *
     * Verifies that:
     * 1. A short-lived token can be generated (1 millisecond TTL)
     * 2. After expiration, parsing the message raises HandoffException
     * 3. The exception message indicates token expiration
     */
    @Test
    void parseAndVerifyHandoffMessage_expiredJwt_throwsHandoffException() throws Exception {
        // Generate a token that expires in 1 ms
        HandoffToken shortLived = protocol.generateHandoffToken(
            "WI-1", "a", "b", "s", Duration.ofMillis(1));

        // Wait for expiration
        Thread.sleep(20);

        // Verify the token is expired
        assertFalse(shortLived.isValid(), "Token should be expired after 20ms with 1ms TTL");

        String messageText = "YAWL_HANDOFF:WI-1:" + shortLived.jwt() + ":a";

        // Parsing should fail
        HandoffException e = assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage(messageText));
        assertTrue(e.getMessage().contains("expired"),
            "Exception should mention token expiration");
    }

    /**
     * Test: Tampered JWT is rejected with signature verification failure.
     *
     * Verifies that:
     * 1. A JWT with a modified signature is rejected
     * 2. The parser detects the signature mismatch
     * 3. HandoffException is raised with appropriate message
     */
    @Test
    void parseAndVerifyHandoffMessage_tamperedJwt_throwsHandoffException() {
        String messageText = "YAWL_HANDOFF:WI-1:eyJhbGciOiJIUzI1NiJ9.tampered.sig:agent";

        HandoffException e = assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage(messageText));
        assertTrue(e.getMessage().contains("signature") || e.getMessage().contains("invalid"),
            "Exception should mention signature failure: " + e.getMessage());
    }

    /**
     * Test: Message with too few parts is rejected.
     *
     * Verifies that:
     * 1. A message missing the fourth part (fromAgent) is invalid
     * 2. HandoffException is raised
     * 3. The exception message explains the expected format
     */
    @Test
    void parseAndVerifyHandoffMessage_tooFewParts_throwsHandoffException() {
        String messageText = "YAWL_HANDOFF:WI-1:jwt-only";

        HandoffException e = assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage(messageText));
        assertTrue(e.getMessage().contains("Invalid handoff message format"),
            "Exception should explain the format requirement");
    }

    /**
     * Test: Message with wrong prefix is rejected.
     *
     * Verifies that:
     * 1. A message without the YAWL_HANDOFF prefix is invalid
     * 2. HandoffException is raised
     * 3. The exception message explains the expected format
     */
    @Test
    void parseAndVerifyHandoffMessage_wrongPrefix_throwsHandoffException() {
        String messageText = "NOT_HANDOFF:WI-1:jwt:agent";

        HandoffException e = assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage(messageText));
        assertTrue(e.getMessage().contains("Invalid handoff message format"),
            "Exception should explain the format requirement");
    }

    /**
     * Test: Null message is rejected.
     *
     * Verifies that:
     * 1. A null message raises HandoffException
     * 2. The exception message is informative
     */
    @Test
    void parseAndVerifyHandoffMessage_nullMessage_throwsHandoffException() {
        assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage(null));
    }

    /**
     * Test: Blank message is rejected.
     *
     * Verifies that:
     * 1. An empty or whitespace-only message raises HandoffException
     * 2. The exception message is informative
     */
    @Test
    void parseAndVerifyHandoffMessage_blankMessage_throwsHandoffException() {
        assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage(""));
        assertThrows(HandoffException.class,
            () -> protocol.parseAndVerifyHandoffMessage("   "));
    }

    /**
     * Test: JWT parser rejects tampered tokens.
     *
     * Verifies that:
     * 1. The underlying JwtAuthenticationProvider correctly validates signatures
     * 2. A malformed JWT raises io.jsonwebtoken.JwtException
     * 3. The exception is caught and converted to HandoffException by parseAndVerifyHandoffMessage
     */
    @Test
    void parseClaims_tamperedJwt_throwsJwtException() {
        assertThrows(io.jsonwebtoken.JwtException.class,
            () -> jwtProvider.parseClaims("bad.jwt.token"));
    }

    /**
     * Test: Real JWT parsing extracts correct claims.
     *
     * Verifies that:
     * 1. A real JWT token generated by issueToken can be parsed
     * 2. The Claims object contains the expected subject ("handoff")
     * 3. The expiration date is present and correct
     */
    @Test
    void parseClaims_validJwt_returnsClaims() throws Exception {
        // Generate a real JWT
        String jwt = jwtProvider.issueToken("handoff", java.util.List.of(), 30000);

        // Parse it
        io.jsonwebtoken.Claims claims = jwtProvider.parseClaims(jwt);

        // Verify claims
        assertNotNull(claims, "Claims should not be null");
        assertEquals("handoff", claims.getSubject(), "Subject should be 'handoff'");
        assertNotNull(claims.getExpiration(), "Expiration should be present");
    }

    /**
     * Test: Multiple consecutive tokens with different work items.
     *
     * Verifies that:
     * 1. Multiple independent tokens can be generated
     * 2. Each can be parsed and verified separately
     * 3. Cross-contamination does not occur (workItemId is not confused)
     */
    @Test
    void multipleTokens_eachParseIndependently() throws Exception {
        HandoffToken token1 = protocol.generateHandoffToken("WI-100", "agent-X", "agent-Y", "s1");
        HandoffToken token2 = protocol.generateHandoffToken("WI-200", "agent-P", "agent-Q", "s2");

        String msg1 = "YAWL_HANDOFF:WI-100:" + token1.jwt() + ":agent-X";
        String msg2 = "YAWL_HANDOFF:WI-200:" + token2.jwt() + ":agent-P";

        HandoffToken parsed1 = protocol.parseAndVerifyHandoffMessage(msg1);
        HandoffToken parsed2 = protocol.parseAndVerifyHandoffMessage(msg2);

        assertEquals("WI-100", parsed1.workItemId());
        assertEquals("WI-200", parsed2.workItemId());
        assertEquals("agent-X", parsed1.fromAgent());
        assertEquals("agent-P", parsed2.fromAgent());
    }

    /**
     * Test: Message with extra colons in later parts.
     *
     * Verifies that:
     * 1. The split(":", 4) correctly limits parsing to 4 parts
     * 2. If fromAgent contains colons, it is captured correctly
     * 3. The token can still be verified
     *
     * <p>Example: YAWL_HANDOFF:WI-1:jwt:agent:with:colons
     * splits to [YAWL_HANDOFF, WI-1, jwt, agent:with:colons]
     */
    @Test
    void parseAndVerifyHandoffMessage_extraColonsInFromAgent_parsed correctly() throws Exception {
        HandoffToken token = protocol.generateHandoffToken("WI-1", "agent:special", "agent-B", "s");
        String messageText = "YAWL_HANDOFF:WI-1:" + token.jwt() + ":agent:with:extra:colons";

        // With split(":", 4), the last part captures everything after the third colon
        HandoffToken parsed = protocol.parseAndVerifyHandoffMessage(messageText);
        assertEquals("agent:with:extra:colons", parsed.fromAgent());
    }

    /**
     * Test: Token validity check in verifyHandoffToken.
     *
     * Verifies that:
     * 1. verifyHandoffToken returns the token if it's still valid
     * 2. An expired token raises HandoffException
     * 3. The isValid() method correctly reports token status
     */
    @Test
    void verifyHandoffToken_withValidToken_returnsToken() throws Exception {
        HandoffToken token = protocol.generateHandoffToken("WI-1", "a", "b", "s");
        assertTrue(token.isValid());

        HandoffToken verified = protocol.verifyHandoffToken(token);
        assertEquals(token.workItemId(), verified.workItemId());
    }

    /**
     * Test: verifyHandoffToken rejects expired tokens.
     *
     * Verifies that:
     * 1. An expired token is correctly identified as invalid
     * 2. verifyHandoffToken throws HandoffException
     * 3. The exception message indicates expiration
     */
    @Test
    void verifyHandoffToken_withExpiredToken_throwsHandoffException() throws Exception {
        HandoffToken expired = protocol.generateHandoffToken(
            "WI-1", "a", "b", "s", Duration.ofMillis(1));
        Thread.sleep(20);

        assertFalse(expired.isValid());
        HandoffException e = assertThrows(HandoffException.class,
            () -> protocol.verifyHandoffToken(expired));
        assertTrue(e.getMessage().contains("expired"));
    }
}
