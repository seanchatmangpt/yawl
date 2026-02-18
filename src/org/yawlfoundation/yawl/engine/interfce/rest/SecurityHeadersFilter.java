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

package org.yawlfoundation.yawl.engine.interfce.rest;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * HTTP Security Headers filter for YAWL web application.
 *
 * SOC2 HIGH finding: adds mandatory security response headers to every HTTP response
 * to protect against common web attacks:
 *
 * - Strict-Transport-Security: enforces HTTPS for 1 year including subdomains
 * - X-Content-Type-Options: prevents MIME-type sniffing
 * - X-Frame-Options: prevents clickjacking via iframe embedding
 * - Content-Security-Policy: restricts resource loading to same origin
 * - X-XSS-Protection: enables browser XSS filter (legacy browsers)
 * - Referrer-Policy: prevents referrer leakage to third-party origins
 * - Permissions-Policy: disables dangerous browser features
 * - Cache-Control: prevents sensitive data caching
 *
 * Register this filter in web.xml before other filters with url-pattern="/*"
 * to ensure all responses carry the security headers.
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class SecurityHeadersFilter implements Filter {

    private static final Logger _logger = LogManager.getLogger(SecurityHeadersFilter.class);

    /**
     * HSTS max-age of 1 year in seconds. Includes subdomains to protect all
     * subdomains from downgrade attacks. Adjust if YAWL is served on HTTP internally.
     */
    private static final String HSTS_VALUE =
            "max-age=31536000; includeSubDomains";

    /**
     * Restricts resource loading to same-origin only. Services that load external
     * fonts, scripts, or images must extend this policy via the allowedCsp init-param.
     */
    private static final String DEFAULT_CSP_VALUE =
            "default-src 'self'; script-src 'self'; style-src 'self'; " +
            "img-src 'self' data:; font-src 'self'; connect-src 'self'; " +
            "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    private String cspValue = DEFAULT_CSP_VALUE;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String customCsp = filterConfig.getInitParameter("contentSecurityPolicy");
        if (customCsp != null && !customCsp.trim().isEmpty()) {
            cspValue = customCsp.trim();
        }
        _logger.info("SecurityHeadersFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            applySecurityHeaders(httpResponse);
        }

        chain.doFilter(request, response);
    }

    /**
     * Applies all mandatory HTTP security headers to the response.
     * Called for every request before the filter chain continues.
     *
     * @param response the HTTP response to add headers to
     */
    private void applySecurityHeaders(HttpServletResponse response) {
        // Enforce HTTPS for 1 year including subdomains (SOC2 HIGH)
        response.setHeader("Strict-Transport-Security", HSTS_VALUE);

        // Prevent MIME-type sniffing attacks (SOC2 HIGH)
        response.setHeader("X-Content-Type-Options", "nosniff");

        // Prevent clickjacking via iframe embedding (SOC2 HIGH)
        response.setHeader("X-Frame-Options", "DENY");

        // Content Security Policy: restrict resource origins (SOC2 HIGH)
        response.setHeader("Content-Security-Policy", cspValue);

        // Enable browser XSS filter in block mode (legacy browser support, SOC2 HIGH)
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Prevent referrer leakage to cross-origin requests
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

        // Disable dangerous browser features
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=(), usb=()");

        // Prevent sensitive API responses from being cached by intermediate proxies
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
    }

    @Override
    public void destroy() {
        _logger.info("SecurityHeadersFilter destroyed");
    }
}
