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

package org.yawlfoundation.yawl.integration.gateway;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Describes a single API route entry for YAWL endpoints exposed through an API gateway.
 *
 * <p>Route definitions are gateway-agnostic; concrete generators
 * ({@link kong.KongConfigurationGenerator}, {@link traefik.TraefikConfigurationGenerator},
 * {@link aws.AwsApiGatewayConfigGenerator}) translate these into gateway-specific
 * configuration format (YAML, JSON, or declarative configs).
 *
 * <h2>Rate Limit Tiers</h2>
 * <pre>
 * CRITICAL  - 10 req/min  per client  (case launch, spec load)
 * STANDARD  - 100 req/min per client  (work item operations)
 * READ_ONLY - 600 req/min per client  (status queries, list endpoints)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class GatewayRouteDefinition {

    /**
     * Rate limiting tier applied to a route.
     */
    public enum RateLimitTier {
        /** 10 requests/minute per authenticated client. For state-creating operations. */
        CRITICAL(10, 60),
        /** 100 requests/minute per authenticated client. For standard CRUD operations. */
        STANDARD(100, 60),
        /** 600 requests/minute per authenticated client. For read-only queries. */
        READ_ONLY(600, 60);

        private final int  requestsPerWindow;
        private final int  windowSeconds;

        RateLimitTier(int requestsPerWindow, int windowSeconds) {
            this.requestsPerWindow = requestsPerWindow;
            this.windowSeconds     = windowSeconds;
        }

        /** Maximum requests allowed in the window. */
        public int getRequestsPerWindow() { return requestsPerWindow; }

        /** Window duration in seconds. */
        public int getWindowSeconds()     { return windowSeconds; }
    }

    /**
     * HTTP methods for this route.
     */
    public enum HttpMethod {
        GET, POST, PUT, PATCH, DELETE, OPTIONS, HEAD
    }

    private final String            routeId;
    private final String            pathPattern;
    private final List<HttpMethod>  methods;
    private final String            upstreamPath;
    private final RateLimitTier     rateLimitTier;
    private final boolean           requiresAuthentication;
    private final List<String>      requiredScopes;
    private final String            description;

    private GatewayRouteDefinition(Builder builder) {
        this.routeId               = Objects.requireNonNull(builder.routeId, "routeId");
        this.pathPattern           = Objects.requireNonNull(builder.pathPattern, "pathPattern");
        this.methods               = Collections.unmodifiableList(
                                         builder.methods != null ? builder.methods : List.of());
        this.upstreamPath          = builder.upstreamPath != null ? builder.upstreamPath : builder.pathPattern;
        this.rateLimitTier         = Objects.requireNonNull(builder.rateLimitTier, "rateLimitTier");
        this.requiresAuthentication = builder.requiresAuthentication;
        this.requiredScopes        = Collections.unmodifiableList(
                                         builder.requiredScopes != null ? builder.requiredScopes : List.of());
        this.description           = builder.description != null ? builder.description : "";
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique route identifier for the gateway configuration. */
    public String getRouteId()              { return routeId; }

    /** URL path pattern (e.g. {@code /api/v1/cases/{caseId}}). */
    public String getPathPattern()          { return pathPattern; }

    /** HTTP methods this route accepts. */
    public List<HttpMethod> getMethods()    { return methods; }

    /** Upstream path to forward requests to (after gateway path rewriting). */
    public String getUpstreamPath()         { return upstreamPath; }

    /** Rate limiting tier applied to requests on this route. */
    public RateLimitTier getRateLimitTier() { return rateLimitTier; }

    /** Whether this route requires an authenticated (verified JWT) caller. */
    public boolean isRequiresAuthentication() { return requiresAuthentication; }

    /** OAuth2 scopes that must be present in the token (any one is sufficient). */
    public List<String> getRequiredScopes() { return requiredScopes; }

    /** Human-readable description for documentation. */
    public String getDescription()          { return description; }

    // -------------------------------------------------------------------------
    // Pre-built YAWL API route catalogue
    // -------------------------------------------------------------------------

    /**
     * Returns the standard YAWL API route definitions for v1 endpoints.
     *
     * @param upstreamBaseUrl the YAWL engine base URL (e.g. {@code http://yawl-engine:8080})
     * @return ordered list of all YAWL v1 API route definitions
     */
    public static List<GatewayRouteDefinition> yawlV1Routes(String upstreamBaseUrl) {
        Objects.requireNonNull(upstreamBaseUrl, "upstreamBaseUrl must not be null");
        String base = upstreamBaseUrl.endsWith("/")
                    ? upstreamBaseUrl.substring(0, upstreamBaseUrl.length() - 1)
                    : upstreamBaseUrl;

        return List.of(
            // Specification management (CRITICAL - state-creating)
            new Builder("yawl-spec-load",   "/api/v1/specifications")
                .methods(HttpMethod.POST)
                .upstreamPath(base + "/yawl/ia")
                .rateLimitTier(RateLimitTier.CRITICAL)
                .requiresAuthentication(true)
                .requiredScopes("yawl:designer", "yawl:admin")
                .description("Load a new YAWL specification into the engine")
                .build(),

            new Builder("yawl-spec-unload", "/api/v1/specifications/{specId}")
                .methods(HttpMethod.DELETE)
                .upstreamPath(base + "/yawl/ia")
                .rateLimitTier(RateLimitTier.CRITICAL)
                .requiresAuthentication(true)
                .requiredScopes("yawl:designer", "yawl:admin")
                .description("Unload a specification from the engine")
                .build(),

            new Builder("yawl-spec-list",   "/api/v1/specifications")
                .methods(HttpMethod.GET)
                .upstreamPath(base + "/yawl/ia")
                .rateLimitTier(RateLimitTier.READ_ONLY)
                .requiresAuthentication(true)
                .requiredScopes("yawl:monitor", "yawl:operator", "yawl:designer",
                                "yawl:agent",   "yawl:admin")
                .description("List all loaded specifications")
                .build(),

            // Case management (CRITICAL - state-creating)
            new Builder("yawl-case-launch", "/api/v1/cases")
                .methods(HttpMethod.POST)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.CRITICAL)
                .requiresAuthentication(true)
                .requiredScopes("yawl:operator", "yawl:admin")
                .description("Launch a new workflow case")
                .build(),

            new Builder("yawl-case-cancel", "/api/v1/cases/{caseId}")
                .methods(HttpMethod.DELETE)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.CRITICAL)
                .requiresAuthentication(true)
                .requiredScopes("yawl:operator", "yawl:admin")
                .description("Cancel a running workflow case")
                .build(),

            new Builder("yawl-case-list",   "/api/v1/cases")
                .methods(HttpMethod.GET)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.READ_ONLY)
                .requiresAuthentication(true)
                .requiredScopes("yawl:monitor", "yawl:operator", "yawl:designer",
                                "yawl:agent",   "yawl:admin")
                .description("List all running cases")
                .build(),

            new Builder("yawl-case-status", "/api/v1/cases/{caseId}")
                .methods(HttpMethod.GET)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.READ_ONLY)
                .requiresAuthentication(true)
                .requiredScopes("yawl:monitor", "yawl:operator", "yawl:designer",
                                "yawl:agent",   "yawl:admin")
                .description("Get status and work items for a specific case")
                .build(),

            // Work item operations (STANDARD)
            new Builder("yawl-wi-checkout",  "/api/v1/workitems/{workItemId}/checkout")
                .methods(HttpMethod.POST)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.STANDARD)
                .requiresAuthentication(true)
                .requiredScopes("yawl:operator", "yawl:agent", "yawl:admin")
                .description("Check out a work item for processing")
                .build(),

            new Builder("yawl-wi-checkin",   "/api/v1/workitems/{workItemId}/checkin")
                .methods(HttpMethod.POST)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.STANDARD)
                .requiresAuthentication(true)
                .requiredScopes("yawl:operator", "yawl:agent", "yawl:admin")
                .description("Check in a work item with output data")
                .build(),

            new Builder("yawl-wi-complete",  "/api/v1/workitems/{workItemId}/complete")
                .methods(HttpMethod.POST)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.STANDARD)
                .requiresAuthentication(true)
                .requiredScopes("yawl:operator", "yawl:agent", "yawl:admin")
                .description("Complete a work item")
                .build(),

            new Builder("yawl-wi-list",      "/api/v1/workitems")
                .methods(HttpMethod.GET)
                .upstreamPath(base + "/yawl/ib")
                .rateLimitTier(RateLimitTier.READ_ONLY)
                .requiresAuthentication(true)
                .requiredScopes("yawl:monitor", "yawl:operator", "yawl:agent", "yawl:admin")
                .description("List all enabled work items")
                .build(),

            // MCP/A2A endpoints (STANDARD - agent access)
            new Builder("yawl-mcp-tools",    "/api/v1/mcp/**")
                .methods(HttpMethod.GET, HttpMethod.POST)
                .upstreamPath(base + "/yawl/mcp")
                .rateLimitTier(RateLimitTier.STANDARD)
                .requiresAuthentication(true)
                .requiredScopes("yawl:agent", "yawl:admin")
                .description("MCP tool execution endpoints for AI agents")
                .build(),

            new Builder("yawl-a2a",          "/api/v1/a2a/**")
                .methods(HttpMethod.GET, HttpMethod.POST)
                .upstreamPath(base + "/yawl/a2a")
                .rateLimitTier(RateLimitTier.STANDARD)
                .requiresAuthentication(true)
                .requiredScopes("yawl:agent", "yawl:admin")
                .description("A2A agent-to-agent protocol endpoints")
                .build(),

            // Health / actuator (READ_ONLY, no auth for load-balancer probes)
            new Builder("yawl-health",       "/api/v1/health")
                .methods(HttpMethod.GET)
                .upstreamPath(base + "/yawl/actuator/health")
                .rateLimitTier(RateLimitTier.READ_ONLY)
                .requiresAuthentication(false)
                .description("Health probe endpoint for load balancers")
                .build()
        );
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Builder for {@link GatewayRouteDefinition}. */
    public static final class Builder {
        private final String           routeId;
        private final String           pathPattern;
        private List<HttpMethod>       methods;
        private String                 upstreamPath;
        private RateLimitTier          rateLimitTier    = RateLimitTier.STANDARD;
        private boolean                requiresAuthentication = true;
        private List<String>           requiredScopes;
        private String                 description;

        /**
         * Start building a route with required identifiers.
         *
         * @param routeId     unique route ID for gateway config
         * @param pathPattern URL path pattern
         */
        public Builder(String routeId, String pathPattern) {
            this.routeId     = routeId;
            this.pathPattern = pathPattern;
        }

        /** Set allowed HTTP methods. */
        public Builder methods(HttpMethod... methods) {
            this.methods = List.of(methods);
            return this;
        }

        /** Set upstream path for gateway forwarding. */
        public Builder upstreamPath(String upstreamPath) {
            this.upstreamPath = upstreamPath;
            return this;
        }

        /** Set rate limit tier. */
        public Builder rateLimitTier(RateLimitTier tier) {
            this.rateLimitTier = tier;
            return this;
        }

        /** Set whether authentication is required. */
        public Builder requiresAuthentication(boolean required) {
            this.requiresAuthentication = required;
            return this;
        }

        /** Set required OAuth2 scopes (any one is sufficient). */
        public Builder requiredScopes(String... scopes) {
            this.requiredScopes = List.of(scopes);
            return this;
        }

        /** Set human-readable description. */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Build the route definition.
         *
         * @return configured route definition
         */
        public GatewayRouteDefinition build() {
            return new GatewayRouteDefinition(this);
        }
    }
}
