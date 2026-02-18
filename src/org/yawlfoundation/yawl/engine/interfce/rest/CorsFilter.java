/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.yawlfoundation.yawl.authentication.SecurityAuditLogger;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CORS (Cross-Origin Resource Sharing) filter for YAWL REST APIs.
 *
 * SOC2 CRITICAL fix: Wildcard origin (*) is no longer allowed. All cross-origin
 * requests must come from explicitly configured allowed origins. The wildcard "*"
 * default has been replaced with a deny-by-default policy: requests from origins
 * not in the configured whitelist are rejected with no CORS headers (the browser
 * will block them). Credentials (cookies, Authorization headers) are only allowed
 * when the request origin is on the whitelist.
 *
 * Configuration via web.xml init-params:
 *   allowedOrigins  - Comma-separated list of allowed origins (e.g.
 *                     "https://app.example.com,https://admin.example.com").
 *                     No wildcard support. Required for any cross-origin access.
 *   allowedMethods  - Comma-separated HTTP methods (default: GET,POST,PUT,DELETE,OPTIONS,HEAD)
 *   allowedHeaders  - Comma-separated request headers (default: Content-Type,Authorization,
 *                     X-Session-Handle,X-Requested-With)
 *   allowCredentials - "true" to allow credentials (default: false when no origins configured)
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
public class CorsFilter implements Filter {

    private static final Logger _logger = LogManager.getLogger(CorsFilter.class);

    private Set<String> allowedOrigins = Collections.emptySet();
    private String allowedMethods = "GET, POST, PUT, DELETE, OPTIONS, HEAD";
    private String allowedHeaders = "Content-Type, Authorization, X-Session-Handle, X-Requested-With";
    private String exposedHeaders = "X-Session-Handle, Location";
    private String maxAge = "3600";
    private boolean allowCredentials = false;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String origins = filterConfig.getInitParameter("allowedOrigins");
        if (origins != null && !origins.trim().isEmpty()) {
            allowedOrigins = parseOriginWhitelist(origins);
            _logger.info("CORS filter: {} allowed origin(s) configured", allowedOrigins.size());
        } else {
            _logger.warn("CORS filter: no 'allowedOrigins' configured - all cross-origin " +
                    "requests will be blocked. Set 'allowedOrigins' init-param to a " +
                    "comma-separated list of trusted origins.");
        }

        String methods = filterConfig.getInitParameter("allowedMethods");
        if (methods != null && !methods.trim().isEmpty()) {
            allowedMethods = methods.trim();
        }

        String headers = filterConfig.getInitParameter("allowedHeaders");
        if (headers != null && !headers.trim().isEmpty()) {
            allowedHeaders = headers.trim();
        }

        String credentials = filterConfig.getInitParameter("allowCredentials");
        if (credentials != null && !credentials.trim().isEmpty()) {
            boolean requestedCredentials = Boolean.parseBoolean(credentials.trim());
            if (requestedCredentials && allowedOrigins.isEmpty()) {
                throw new ServletException(
                        "CORS misconfiguration: 'allowCredentials=true' requires at least one " +
                        "explicitly configured origin in 'allowedOrigins'. Wildcard '*' is not " +
                        "permitted. Configure specific trusted origins (comma-separated) via the " +
                        "'allowedOrigins' init-param before enabling credentials.");
            }
            allowCredentials = requestedCredentials;
        }

        _logger.info("CORS filter initialized: allowCredentials={}", allowCredentials);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String origin = httpRequest.getHeader("Origin");

        if (origin != null && !origin.isEmpty()) {
            if (isOriginAllowed(origin)) {
                // SOC2 CRITICAL: reflect exact origin (never wildcard) when credentials allowed
                httpResponse.setHeader("Access-Control-Allow-Origin", origin);
                httpResponse.setHeader("Vary", "Origin");
                httpResponse.setHeader("Access-Control-Allow-Methods", allowedMethods);
                httpResponse.setHeader("Access-Control-Allow-Headers", allowedHeaders);
                httpResponse.setHeader("Access-Control-Expose-Headers", exposedHeaders);
                httpResponse.setHeader("Access-Control-Max-Age", maxAge);

                if (allowCredentials) {
                    httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                }
            } else {
                // Origin not in whitelist: do not set CORS headers.
                // Browser will block the cross-origin request.
                String requestUri = httpRequest.getRequestURI();
                _logger.warn("CORS: rejected cross-origin request from unlisted origin: {}",
                        origin);
                SecurityAuditLogger.corsOriginRejected(origin, requestUri);
            }
        }
        // No Origin header: same-origin request, no CORS headers needed.

        // Handle preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        _logger.info("CORS filter destroyed");
    }

    /**
     * Checks whether the given origin is in the allowed-origins whitelist.
     *
     * @param origin the value of the Origin request header
     * @return true if the origin is explicitly listed in the whitelist
     */
    private boolean isOriginAllowed(String origin) {
        return allowedOrigins.contains(origin);
    }

    /**
     * Parses a comma-separated list of origins into a set for O(1) lookup.
     * Rejects wildcard entries to enforce the whitelist policy.
     *
     * @param originsConfig comma-separated origin values from init-param
     * @return immutable set of allowed origin strings
     * @throws ServletException if the configuration contains a wildcard
     */
    private Set<String> parseOriginWhitelist(String originsConfig) throws ServletException {
        String[] parts = originsConfig.split(",");
        Set<String> result = new LinkedHashSet<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if ("*".equals(trimmed)) {
                throw new ServletException(
                        "CORS wildcard origin '*' is prohibited by security policy (SOC2). " +
                        "Configure 'allowedOrigins' with a comma-separated list of " +
                        "specific trusted origins (e.g. 'https://app.example.com').");
            }
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
