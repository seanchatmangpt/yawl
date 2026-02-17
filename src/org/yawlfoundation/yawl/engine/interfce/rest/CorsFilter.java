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

import java.io.IOException;

/**
 * CORS (Cross-Origin Resource Sharing) filter for YAWL REST APIs.
 * Enables web browsers to access REST endpoints from different origins.
 *
 * Configures headers to allow:
 * - Cross-origin requests from any domain (for development)
 * - Standard HTTP methods (GET, POST, PUT, DELETE, OPTIONS)
 * - Custom headers (Authorization, X-Session-Handle)
 * - Preflight request caching
 *
 * Production deployments should configure allowed origins via context parameters
 * to restrict access to trusted domains only.
 *
 * @author Michael Adams
 * @date 16/02/2026
 */
public class CorsFilter implements Filter {


    private static final Logger logger = LogManager.getLogger(CorsFilter.class);
    private static final Logger _logger = LogManager.getLogger(CorsFilter.class);

    private String allowedOrigins = "*";
    private String allowedMethods = "GET, POST, PUT, DELETE, OPTIONS, HEAD";
    private String allowedHeaders = "Content-Type, Authorization, X-Session-Handle, X-Requested-With";
    private String exposedHeaders = "X-Session-Handle, Location";
    private String maxAge = "3600";
    private boolean allowCredentials = true;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Allow configuration via context parameters
        String origins = filterConfig.getInitParameter("allowedOrigins");
        if (origins != null && !origins.strip().isEmpty()) {
            allowedOrigins = origins;
            _logger.info("CORS allowed origins: {}", allowedOrigins);
        }

        String methods = filterConfig.getInitParameter("allowedMethods");
        if (methods != null && !methods.strip().isEmpty()) {
            allowedMethods = methods;
        }

        String headers = filterConfig.getInitParameter("allowedHeaders");
        if (headers != null && !headers.strip().isEmpty()) {
            allowedHeaders = headers;
        }

        String credentials = filterConfig.getInitParameter("allowCredentials");
        if (credentials != null && !credentials.strip().isEmpty()) {
            allowCredentials = Boolean.parseBoolean(credentials);
        }

        _logger.info("CORS filter initialized for REST API endpoints");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Get origin from request
        String origin = httpRequest.getHeader("Origin");

        // Set CORS headers
        if (origin != null && !origin.isEmpty()) {
            // Allow specific origin or wildcard
            if ("*".equals(allowedOrigins)) {
                httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            } else if (allowedOrigins.contains(origin)) {
                httpResponse.setHeader("Access-Control-Allow-Origin", origin);
            }
        } else {
            // No origin header - allow wildcard
            httpResponse.setHeader("Access-Control-Allow-Origin", allowedOrigins);
        }

        httpResponse.setHeader("Access-Control-Allow-Methods", allowedMethods);
        httpResponse.setHeader("Access-Control-Allow-Headers", allowedHeaders);
        httpResponse.setHeader("Access-Control-Expose-Headers", exposedHeaders);
        httpResponse.setHeader("Access-Control-Max-Age", maxAge);

        if (allowCredentials) {
            httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
        }

        // Handle preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        // Continue with the filter chain
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        _logger.info("CORS filter destroyed");
    }
}
