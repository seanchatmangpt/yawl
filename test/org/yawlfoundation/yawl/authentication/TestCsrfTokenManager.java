package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CsrfTokenManager.
 * 
 * Tests cover:
 * - Token generation and storage
 * - Token retrieval and auto-generation
 * - Constant-time validation (FIX #1)
 * - Token invalidation
 * - Null safety
 * 
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestCsrfTokenManager {
    
    private HttpSession session;
    private HttpServletRequest request;
    
    @BeforeEach
    public void setUp() {
        session = mock(HttpSession.class);
        request = mock(HttpServletRequest.class);
    }
    
    @Test
    public void testGenerateToken() {
        String token = CsrfTokenManager.generateToken(session);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(session).setAttribute(eq("_csrf_token"), eq(token));
    }
    
    @Test
    public void testGenerateTokenNullSession() {
        assertThrows(IllegalArgumentException.class, () -> {
            CsrfTokenManager.generateToken(null);
        });
    }
    
    @Test
    public void testGetTokenExisting() {
        String expectedToken = "existing-token";
        when(session.getAttribute("_csrf_token")).thenReturn(expectedToken);
        
        String token = CsrfTokenManager.getToken(session);
        assertEquals(expectedToken, token);
    }
    
    @Test
    public void testGetTokenAutoGenerate() {
        when(session.getAttribute("_csrf_token")).thenReturn(null);
        
        String token = CsrfTokenManager.getToken(session);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(session).setAttribute(eq("_csrf_token"), anyString());
    }
    
    @Test
    public void testGetTokenNullSession() {
        assertThrows(IllegalArgumentException.class, () -> {
            CsrfTokenManager.getToken(null);
        });
    }
    
    @Test
    public void testValidateTokenFromParameter() {
        String token = "test-csrf-token";
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn(token);
        when(request.getParameter("_csrf")).thenReturn(token);
        
        assertTrue(CsrfTokenManager.validateToken(request));
    }
    
    @Test
    public void testValidateTokenFromHeader() {
        String token = "test-csrf-token";
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn(token);
        when(request.getParameter("_csrf")).thenReturn(null);
        when(request.getHeader("X-CSRF-Token")).thenReturn(token);
        
        assertTrue(CsrfTokenManager.validateToken(request));
    }
    
    @Test
    public void testValidateTokenMismatch() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn("session-token");
        when(request.getParameter("_csrf")).thenReturn("different-token");
        
        assertFalse(CsrfTokenManager.validateToken(request));
    }
    
    @Test
    public void testValidateTokenNoSession() {
        when(request.getSession(false)).thenReturn(null);
        assertFalse(CsrfTokenManager.validateToken(request));
    }
    
    @Test
    public void testValidateTokenNoRequestToken() {
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn("token");
        when(request.getParameter("_csrf")).thenReturn(null);
        when(request.getHeader("X-CSRF-Token")).thenReturn(null);
        
        assertFalse(CsrfTokenManager.validateToken(request));
    }
    
    /**
     * FIX #1: Test constant-time comparison.
     * While we can't easily test timing, we verify correct behavior.
     */
    @Test
    public void testConstantTimeComparison() {
        String token = "valid-token-12345";
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn(token);
        when(request.getParameter("_csrf")).thenReturn(token);
        
        assertTrue(CsrfTokenManager.validateToken(request));
        
        // Test with subtly different token
        when(request.getParameter("_csrf")).thenReturn("valid-token-12346");
        assertFalse(CsrfTokenManager.validateToken(request));
    }
    
    @Test
    public void testInvalidateToken() {
        CsrfTokenManager.invalidateToken(session);
        verify(session).removeAttribute("_csrf_token");
    }
    
    @Test
    public void testInvalidateTokenNullSession() {
        // Should not throw exception
        assertDoesNotThrow(() -> {
            CsrfTokenManager.invalidateToken(null);
        });
    }
}
