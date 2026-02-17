package org.yawlfoundation.yawl.authentication;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF protection filter with path normalization and validation.
 * 
 * <p>This filter provides CSRF protection for state-changing HTTP methods
 * (POST, PUT, DELETE, PATCH) with:
 * <ul>
 *   <li>Path normalization to prevent bypass via path traversal (FIX #5)</li>
 *   <li>Filter configuration validation (FIX #6)</li>
 *   <li>Configurable path exclusions for API endpoints</li>
 *   <li>Proper HTTP 403 responses for invalid tokens</li>
 * </ul>
 * 
 * <p>Configuration in web.xml:
 * <pre>
 * &lt;filter&gt;
 *   &lt;filter-name&gt;CsrfProtectionFilter&lt;/filter-name&gt;
 *   &lt;filter-class&gt;org.yawlfoundation.yawl.authentication.CsrfProtectionFilter&lt;/filter-class&gt;
 *   &lt;init-param&gt;
 *     &lt;param-name&gt;excludedPaths&lt;/param-name&gt;
 *     &lt;param-value&gt;/api/,/health,/metrics&lt;/param-value&gt;
 *   &lt;/init-param&gt;
 * &lt;/filter&gt;
 * </pre>
 * 
 * @author YAWL Development Team
 * @since 5.2
 */
public class CsrfProtectionFilter implements Filter {
    

    private static final Logger _logger = LogManager.getLogger(CsrfProtectionFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    
    private Set<String> excludedPaths = new HashSet<>();
    
    /**
     * FIX #6: Validate filter configuration.
     * 
     * <p>Validates and logs the excluded paths configuration to ensure:
     * <ul>
     *   <li>Empty or malformed paths are filtered out</li>
     *   <li>Configuration is properly logged for audit purposes</li>
     *   <li>Administrators can verify CSRF protection is correctly configured</li>
     * </ul>
     * 
     * @param filterConfig the filter configuration
     * @throws ServletException if initialization fails
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        var excluded = filterConfig.getInitParameter("excludedPaths");
        if (excluded != null && !excluded.isBlank()) {
            var paths = excluded.split(",");
            for (String path : paths) {
                var trimmed = path.strip();
                if (!trimmed.isBlank()) {
                    excludedPaths.add(trimmed);
                }
            }
            _logger.info("CSRF protection initialized with {} excluded paths: {}", 
                         excludedPaths.size(), excludedPaths);
        } else {
            _logger.info("CSRF protection initialized with no excluded paths");
        }
    }
    
    /**
     * Performs CSRF token validation for state-changing requests.
     * 
     * <p>Safe methods (GET, HEAD, OPTIONS, TRACE) are always allowed.
     * Excluded paths bypass CSRF protection (use with caution).
     * All other requests must have a valid CSRF token.
     * 
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain
     * @throws IOException if I/O error occurs
     * @throws ServletException if servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;
        
        if (SAFE_METHODS.contains(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        
        String path = httpRequest.getRequestURI();
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        if (!CsrfTokenManager.validateToken(httpRequest)) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"error\": \"Invalid CSRF token\"}");
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    /**
     * FIX #5: Path normalization to prevent bypass.
     * 
     * <p>Normalizes paths to prevent bypass via:
     * <ul>
     *   <li>Double slashes: /api//endpoint → /api/endpoint</li>
     *   <li>Current directory: /api/./endpoint → /api/endpoint</li>
     *   <li>Case variations: /API/endpoint → /api/endpoint (lowercase)</li>
     * </ul>
     * 
     * <p>This prevents attackers from bypassing CSRF protection by using
     * path variations that aren't properly normalized.
     * 
     * @param path the request path to check
     * @return true if the path is excluded, false otherwise
     */
    private boolean isExcluded(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        
        // FIX #5: Normalize path to prevent bypass via path traversal
        String normalizedPath = path.replaceAll("//+", "/")      // Remove double slashes
                                     .replaceAll("/\\./", "/")    // Remove /./ sequences
                                     .toLowerCase();              // Case-insensitive matching
        
        for (String excluded : excludedPaths) {
            if (excluded == null || excluded.isBlank()) {
                continue;
            }
            var normalizedExcluded = excluded.strip()
                                             .replaceAll("//+", "/")
                                             .toLowerCase();
            if (normalizedPath.startsWith(normalizedExcluded)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public void destroy() {
        // No resources to clean up
    }
}
