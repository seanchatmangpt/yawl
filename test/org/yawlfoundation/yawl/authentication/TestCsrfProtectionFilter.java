package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Unit tests for CsrfProtectionFilter.
 * 
 * Tests cover:
 * - Safe method pass-through (GET, HEAD, OPTIONS, TRACE)
 * - Path normalization to prevent bypass (FIX #5)
 * - Filter configuration validation (FIX #6)
 * - CSRF token validation
 * - HTTP 403 responses for invalid tokens
 * 
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestCsrfProtectionFilter {
    
    private CsrfProtectionFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private HttpSession session;
    private FilterConfig filterConfig;
    
    @BeforeEach
    public void setUp() {
        filter = new CsrfProtectionFilter();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        session = mock(HttpSession.class);
        filterConfig = mock(FilterConfig.class);
    }
    
    /**
     * FIX #6: Test filter configuration validation.
     */
    @Test
    public void testInitWithNoExcludedPaths() throws ServletException {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn(null);
        assertDoesNotThrow(() -> filter.init(filterConfig));
    }
    
    @Test
    public void testInitWithExcludedPaths() throws ServletException {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/,/health,/metrics");
        assertDoesNotThrow(() -> filter.init(filterConfig));
    }
    
    @Test
    public void testInitWithEmptyExcludedPaths() throws ServletException {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("   ");
        assertDoesNotThrow(() -> filter.init(filterConfig));
    }
    
    @Test
    public void testInitWithMalformedExcludedPaths() throws ServletException {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/,,,/health,  , /metrics");
        assertDoesNotThrow(() -> filter.init(filterConfig));
    }
    
    @Test
    public void testGetMethodAllowed() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }
    
    @Test
    public void testHeadMethodAllowed() throws Exception {
        when(request.getMethod()).thenReturn("HEAD");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testOptionsMethodAllowed() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testTraceMethodAllowed() throws Exception {
        when(request.getMethod()).thenReturn("TRACE");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testPostWithValidToken() throws Exception {
        String token = "valid-token";
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/user/update");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn(token);
        when(request.getParameter("_csrf")).thenReturn(token);
        
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testPostWithInvalidToken() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/user/update");
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("_csrf_token")).thenReturn("session-token");
        when(request.getParameter("_csrf")).thenReturn("wrong-token");
        when(response.getWriter()).thenReturn(writer);
        
        filter.doFilter(request, response, chain);
        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        verify(response).setContentType("application/json");
    }
    
    @Test
    public void testPostWithNoToken() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/user/update");
        when(request.getSession(false)).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);
        
        filter.doFilter(request, response, chain);
        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    /**
     * FIX #5: Test path normalization to prevent bypass.
     */
    @Test
    public void testExcludedPathExactMatch() throws Exception {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/");
        filter.init(filterConfig);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/endpoint");
        
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testExcludedPathDoubleSlash() throws Exception {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/");
        filter.init(filterConfig);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api//endpoint");  // Double slash
        
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testExcludedPathDotSlash() throws Exception {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/");
        filter.init(filterConfig);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/./endpoint");  // Current dir
        
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testExcludedPathCaseInsensitive() throws Exception {
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/");
        filter.init(filterConfig);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/API/endpoint");  // Different case
        
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }
    
    @Test
    public void testNonExcludedPathRequiresToken() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        
        when(filterConfig.getInitParameter("excludedPaths")).thenReturn("/api/");
        filter.init(filterConfig);
        
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/user/update");  // Not excluded
        when(request.getSession(false)).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);
        
        filter.doFilter(request, response, chain);
        verify(chain, never()).doFilter(request, response);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    @Test
    public void testPutMethodRequiresToken() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        
        when(request.getMethod()).thenReturn("PUT");
        when(request.getRequestURI()).thenReturn("/user/update");
        when(request.getSession(false)).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);
        
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    @Test
    public void testDeleteMethodRequiresToken() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/user/123");
        when(request.getSession(false)).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);
        
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
    
    @Test
    public void testPatchMethodRequiresToken() throws Exception {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        
        when(request.getMethod()).thenReturn("PATCH");
        when(request.getRequestURI()).thenReturn("/user/123");
        when(request.getSession(false)).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);
        
        filter.doFilter(request, response, chain);
        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }
}
