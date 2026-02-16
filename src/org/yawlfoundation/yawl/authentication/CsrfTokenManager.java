package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Manages CSRF tokens with constant-time validation to prevent timing attacks.
 * 
 * <p>This class provides secure CSRF token generation and validation using:
 * <ul>
 *   <li>SecureRandom for cryptographically strong token generation</li>
 *   <li>MessageDigest.isEqual for constant-time comparison (prevents timing attacks)</li>
 *   <li>Session-based token storage for CSRF protection</li>
 * </ul>
 * 
 * @author YAWL Development Team
 * @since 5.2
 */
public class CsrfTokenManager {
    
    private static final String CSRF_TOKEN_ATTR = "_csrf_token";
    private static final int TOKEN_LENGTH = 32;
    private static final SecureRandom RANDOM = new SecureRandom();
    
    /**
     * Generates a new CSRF token and stores it in the session.
     * 
     * @param session the HTTP session to store the token in
     * @return the generated token (Base64-encoded)
     * @throws IllegalArgumentException if session is null
     */
    public static String generateToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        byte[] tokenBytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        session.setAttribute(CSRF_TOKEN_ATTR, token);
        return token;
    }
    
    /**
     * Retrieves the CSRF token from the session, generating one if not present.
     * 
     * @param session the HTTP session
     * @return the CSRF token
     * @throws IllegalArgumentException if session is null
     */
    public static String getToken(HttpSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        String token = (String) session.getAttribute(CSRF_TOKEN_ATTR);
        if (token == null) {
            token = generateToken(session);
        }
        return token;
    }
    
    /**
     * Validates CSRF token using constant-time comparison to prevent timing attacks.
     * 
     * <p>FIX #1: Uses MessageDigest.isEqual for constant-time comparison instead of
     * String.equals(), which is vulnerable to timing attacks.
     * 
     * <p>Accepts tokens from either:
     * <ul>
     *   <li>Request parameter "_csrf"</li>
     *   <li>Request header "X-CSRF-Token"</li>
     * </ul>
     * 
     * @param request the HTTP request containing the token to validate
     * @return true if the token is valid, false otherwise
     */
    public static boolean validateToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        
        String sessionToken = (String) session.getAttribute(CSRF_TOKEN_ATTR);
        String requestToken = request.getParameter("_csrf");
        
        if (requestToken == null) {
            requestToken = request.getHeader("X-CSRF-Token");
        }
        
        if (sessionToken == null || requestToken == null) {
            return false;
        }
        
        // FIX #1: Constant-time comparison using MessageDigest.isEqual
        // This prevents timing attacks that could leak token information
        return MessageDigest.isEqual(
            sessionToken.getBytes(StandardCharsets.UTF_8),
            requestToken.getBytes(StandardCharsets.UTF_8)
        );
    }
    
    /**
     * Invalidates the CSRF token in the session.
     * 
     * @param session the HTTP session
     */
    public static void invalidateToken(HttpSession session) {
        if (session != null) {
            session.removeAttribute(CSRF_TOKEN_ATTR);
        }
    }
}
