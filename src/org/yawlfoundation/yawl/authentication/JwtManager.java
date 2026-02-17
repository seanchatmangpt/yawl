package org.yawlfoundation.yawl.authentication;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT manager with persistent signing key and proper error handling.
 * 
 * <p>This class provides JWT token generation and validation with:
 * <ul>
 *   <li>Persistent signing key loaded from configuration (FIX #2)</li>
 *   <li>Proper null validation and safe extraction (FIX #3)</li>
 *   <li>Comprehensive logging instead of silent exception swallowing (FIX #4)</li>
 *   <li>24-hour token expiration</li>
 * </ul>
 * 
 * <p>Configuration:
 * <ul>
 *   <li>Set system property 'yawl.jwt.secret' OR</li>
 *   <li>Set environment variable 'YAWL_JWT_SECRET'</li>
 *   <li>Minimum 256 bits (32 bytes) recommended</li>
 * </ul>
 * 
 * @author YAWL Development Team
 * @since 5.2
 */
public class JwtManager {
    

    private static final Logger _logger = LogManager.getLogger(JwtManager.class);
    private static final SecretKey KEY = loadSigningKey();
    private static final long EXPIRATION_HOURS = 24;
    
    /**
     * FIX #2: Load JWT signing key from configuration (not ephemeral).
     * 
     * <p>The signing key MUST be persistent across server restarts, otherwise:
     * <ul>
     *   <li>Existing tokens become invalid on restart</li>
     *   <li>Users are forced to re-authenticate unnecessarily</li>
     *   <li>Session continuity is broken</li>
     * </ul>
     * 
     * @return the persistent signing key
     * @throws IllegalStateException if no key is configured
     */
    private static SecretKey loadSigningKey() {
        String keySource = System.getProperty("yawl.jwt.secret");
        if (keySource == null) {
            keySource = System.getenv("YAWL_JWT_SECRET");
        }
        
        if (keySource == null || keySource.isEmpty()) {
            throw new IllegalStateException(
                "JWT signing key not configured. Set system property 'yawl.jwt.secret' " +
                "or environment variable 'YAWL_JWT_SECRET' to a secure random value " +
                "(minimum 256 bits / 32 bytes)"
            );
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(keySource.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Generates a JWT token for the given user and session.
     * 
     * @param userId the user identifier (becomes token subject)
     * @param sessionHandle the session handle to embed in the token
     * @return the signed JWT token
     */
    public static String generateToken(String userId, String sessionHandle) {
        Instant now = Instant.now();
        Instant expiration = now.plus(EXPIRATION_HOURS, ChronoUnit.HOURS);

        return Jwts.builder()
            .subject(userId)
            .claim("sessionHandle", sessionHandle)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiration))
            .signWith(KEY)
            .compact();
    }
    
    /**
     * FIX #4: Proper logging instead of silent exception swallowing.
     * 
     * <p>Validates the JWT token and returns the claims if valid.
     * Invalid tokens are logged at appropriate levels:
     * <ul>
     *   <li>Expired tokens: DEBUG (expected during normal operation)</li>
     *   <li>Invalid signatures: WARN (potential security issue)</li>
     *   <li>Malformed tokens: WARN (potential attack)</li>
     * </ul>
     * 
     * @param token the JWT token to validate
     * @return the claims if valid, null otherwise
     */
    public static Claims validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            _logger.debug("JWT token expired: {}", e.getMessage());
            return null;
        } catch (JwtException e) {
            _logger.warn("JWT validation failed: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            _logger.warn("Invalid JWT token format: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * FIX #3: Null validation and safe extraction.
     * 
     * <p>Extracts the user ID from the token with proper null checking at each step.
     * 
     * @param token the JWT token
     * @return the user ID, or null if token is invalid
     */
    public static String getUserId(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        return claims.getSubject();
    }
    
    /**
     * FIX #3: Null validation and safe extraction.
     * 
     * <p>Extracts the session handle from the token with proper null checking.
     * 
     * @param token the JWT token
     * @return the session handle, or null if token is invalid
     */
    public static String getSessionHandle(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Claims claims = validateToken(token);
        if (claims == null) {
            return null;
        }
        Object handle = claims.get("sessionHandle");
        return handle != null ? handle.toString() : null;
    }
    
    /**
     * Checks if the token is expired.
     * 
     * @param token the JWT token
     * @return true if expired or invalid, false if valid
     */
    public static boolean isExpired(String token) {
        Claims claims = validateToken(token);
        return claims == null;  // validateToken already checks expiration
    }
}
