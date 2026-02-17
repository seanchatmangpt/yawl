package org.yawlfoundation.yawl.authentication;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;

/**
 * Unit tests for JwtManager.
 * 
 * Tests cover:
 * - Persistent signing key configuration (FIX #2)
 * - Token generation and validation
 * - Null validation and safe extraction (FIX #3)
 * - Proper error handling (FIX #4)
 * - Token expiration
 * 
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestJwtManager {
    
    /**
     * FIX #2: Configure JWT secret before tests run.
     */
    @BeforeAll
    public static void setUpClass() {
        System.setProperty("yawl.jwt.secret", "test-secret-key-minimum-32-bytes-for-security-12345");
    }
    
    @Test
    public void testGenerateToken() {
        String token = JwtManager.generateToken("user123", "session456");
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));  // JWT format: header.payload.signature
    }
    
    @Test
    public void testValidateTokenValid() {
        String token = JwtManager.generateToken("user123", "session456");
        Claims claims = JwtManager.validateToken(token);
        assertNotNull(claims);
        assertEquals("user123", claims.getSubject());
        assertEquals("session456", claims.get("sessionHandle"));
    }
    
    @Test
    public void testValidateTokenNull() {
        Claims claims = JwtManager.validateToken(null);
        assertNull(claims);
    }
    
    @Test
    public void testValidateTokenEmpty() {
        Claims claims = JwtManager.validateToken("");
        assertNull(claims);
    }
    
    @Test
    public void testValidateTokenInvalid() {
        Claims claims = JwtManager.validateToken("invalid.token.here");
        assertNull(claims);
    }
    
    @Test
    public void testValidateTokenMalformed() {
        Claims claims = JwtManager.validateToken("not-a-jwt");
        assertNull(claims);
    }
    
    /**
     * FIX #3: Test null validation and safe extraction.
     */
    @Test
    public void testGetUserIdValid() {
        String token = JwtManager.generateToken("user123", "session456");
        String userId = JwtManager.getUserId(token);
        assertEquals("user123", userId);
    }
    
    @Test
    public void testGetUserIdNull() {
        String userId = JwtManager.getUserId(null);
        assertNull(userId);
    }
    
    @Test
    public void testGetUserIdEmpty() {
        String userId = JwtManager.getUserId("");
        assertNull(userId);
    }
    
    @Test
    public void testGetUserIdInvalidToken() {
        String userId = JwtManager.getUserId("invalid.token.here");
        assertNull(userId);
    }
    
    /**
     * FIX #3: Test null validation and safe extraction.
     */
    @Test
    public void testGetSessionHandleValid() {
        String token = JwtManager.generateToken("user123", "session456");
        String handle = JwtManager.getSessionHandle(token);
        assertEquals("session456", handle);
    }
    
    @Test
    public void testGetSessionHandleNull() {
        String handle = JwtManager.getSessionHandle(null);
        assertNull(handle);
    }
    
    @Test
    public void testGetSessionHandleEmpty() {
        String handle = JwtManager.getSessionHandle("");
        assertNull(handle);
    }
    
    @Test
    public void testGetSessionHandleInvalidToken() {
        String handle = JwtManager.getSessionHandle("invalid.token.here");
        assertNull(handle);
    }
    
    @Test
    public void testIsExpiredFreshToken() {
        String token = JwtManager.generateToken("user123", "session456");
        assertFalse(JwtManager.isExpired(token));
    }
    
    @Test
    public void testIsExpiredNullToken() {
        assertTrue(JwtManager.isExpired(null));
    }
    
    @Test
    public void testIsExpiredInvalidToken() {
        assertTrue(JwtManager.isExpired("invalid.token.here"));
    }
    
    /**
     * FIX #2: Test that signing key configuration is required.
     */
    @Test
    public void testSigningKeyPersistence() {
        // Generate two tokens
        String token1 = JwtManager.generateToken("user1", "session1");
        String token2 = JwtManager.generateToken("user2", "session2");
        
        // Both should be valid (same signing key)
        assertNotNull(JwtManager.validateToken(token1));
        assertNotNull(JwtManager.validateToken(token2));
    }
    
    /**
     * FIX #4: Test proper error handling (logged, not thrown).
     */
    @Test
    public void testErrorHandlingDoesNotThrow() {
        // Invalid tokens should not throw exceptions, just return null
        assertDoesNotThrow(() -> {
            JwtManager.validateToken("malformed");
            JwtManager.validateToken("bad.token.here");
            JwtManager.getUserId("invalid");
            JwtManager.getSessionHandle("invalid");
        });
    }
}
