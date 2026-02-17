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

package org.yawlfoundation.yawl.integration.oauth2;

/**
 * YAWL OAuth2 scope constants and documentation.
 *
 * <p>These scope strings are the canonical values to configure in your OIDC provider
 * (Keycloak, Auth0, Okta, Azure AD) and to include in token requests. The RBAC
 * enforcer ({@link RbacAuthorizationEnforcer}) maps these scopes to specific
 * YAWL API operations.
 *
 * <h2>Scope Hierarchy</h2>
 * <pre>
 *   yawl:admin
 *     |-- yawl:designer   (implies: spec read/write + participant management)
 *     |-- yawl:operator   (implies: case + work item operations)
 *     |-- yawl:monitor    (implies: read-only access)
 *     |-- yawl:agent      (implies: autonomous agent operations)
 * </pre>
 *
 * <h2>Keycloak Client Scope Configuration</h2>
 * <pre>
 * # Keycloak realm export snippet (client scope definitions)
 * {
 *   "clientScopes": [
 *     { "name": "yawl:admin",    "description": "Full YAWL administrative access",
 *       "protocol": "openid-connect" },
 *     { "name": "yawl:designer", "description": "Specification management and designer access",
 *       "protocol": "openid-connect" },
 *     { "name": "yawl:operator", "description": "Case launch and work item operations",
 *       "protocol": "openid-connect" },
 *     { "name": "yawl:monitor",  "description": "Read-only monitoring access",
 *       "protocol": "openid-connect" },
 *     { "name": "yawl:agent",    "description": "Autonomous agent workflow access",
 *       "protocol": "openid-connect" }
 *   ]
 * }
 * </pre>
 *
 * <h2>Spring Security Configuration Example</h2>
 * <pre>
 * {@code
 * // application.yml
 * spring:
 *   security:
 *     oauth2:
 *       resourceserver:
 *         jwt:
 *           issuer-uri: ${YAWL_OAUTH2_ISSUER_URI}
 *           audiences:
 *             - yawl-api
 *
 * // SecurityConfig.java
 * @Configuration
 * @EnableWebSecurity
 * public class YawlSecurityConfig {
 *
 *     @Bean
 *     public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
 *         return http
 *             .authorizeHttpRequests(auth -> auth
 *                 // Admin-only endpoints
 *                 .requestMatchers("/api/v1/specifications/**").hasAuthority("SCOPE_yawl:designer")
 *                 .requestMatchers(HttpMethod.DELETE, "/api/v1/cases/**")
 *                                                 .hasAuthority("SCOPE_yawl:admin")
 *                 // Operator endpoints
 *                 .requestMatchers(HttpMethod.POST, "/api/v1/cases").hasAnyAuthority(
 *                         "SCOPE_yawl:operator", "SCOPE_yawl:admin")
 *                 .requestMatchers("/api/v1/workitems/**").hasAnyAuthority(
 *                         "SCOPE_yawl:operator", "SCOPE_yawl:agent", "SCOPE_yawl:admin")
 *                 // Read-only endpoints
 *                 .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyAuthority(
 *                         "SCOPE_yawl:monitor", "SCOPE_yawl:operator",
 *                         "SCOPE_yawl:designer", "SCOPE_yawl:agent", "SCOPE_yawl:admin")
 *                 .anyRequest().authenticated()
 *             )
 *             .oauth2ResourceServer(oauth2 -> oauth2
 *                 .jwt(jwt -> jwt.jwtAuthenticationConverter(yawlJwtConverter()))
 *             )
 *             .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
 *             .build();
 *     }
 *
 *     @Bean
 *     public JwtAuthenticationConverter yawlJwtConverter() {
 *         JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
 *                 new JwtGrantedAuthoritiesConverter();
 *         grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
 *         grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");
 *
 *         JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
 *         converter.setJwtGrantedAuthoritiesConverter(
 *             jwt -> {
 *                 // Merge scopes + realm roles
 *                 var scopeAuthorities = grantedAuthoritiesConverter.convert(jwt);
 *                 var realmRoles = ((List<String>) jwt.getClaimAsMap("realm_access")
 *                         .getOrDefault("roles", List.of()))
 *                         .stream()
 *                         .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
 *                         .toList();
 *                 var combined = new ArrayList<>(scopeAuthorities);
 *                 combined.addAll(realmRoles);
 *                 return combined;
 *             }
 *         );
 *         return converter;
 *     }
 * }
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class YawlOAuth2Scopes {

    /**
     * Full administrative access. Implies all other scopes.
     * Grant only to YAWL system administrators.
     */
    public static final String ADMIN    = "yawl:admin";

    /**
     * Specification design access.
     * Permits: load/unload specifications, manage participant roles,
     * view engine configuration.
     */
    public static final String DESIGNER = "yawl:designer";

    /**
     * Workflow operations access.
     * Permits: launch cases, cancel cases, check out/in work items,
     * complete work items, get work item data.
     */
    public static final String OPERATOR = "yawl:operator";

    /**
     * Read-only monitoring access.
     * Permits: GET on all case, work item, specification, and log endpoints.
     * No state-modifying operations.
     */
    public static final String MONITOR  = "yawl:monitor";

    /**
     * Autonomous agent access.
     * Permits: same as operator plus ZAI/MCP/A2A integration endpoints.
     * Restricted to service account principals (non-human subjects).
     */
    public static final String AGENT    = "yawl:agent";

    private YawlOAuth2Scopes() {
        throw new UnsupportedOperationException(
                "YawlOAuth2Scopes is a constants class and cannot be instantiated.");
    }
}
