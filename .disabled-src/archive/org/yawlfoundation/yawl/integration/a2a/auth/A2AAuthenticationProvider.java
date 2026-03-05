package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.HttpExchange;

/**
 * Authentication provider contract for the YAWL A2A server.
 *
 * <p>Implementations validate inbound HTTP requests and produce an
 * {@link AuthenticatedPrincipal} that carries verified identity and granted
 * permissions. Every request that reaches any A2A endpoint must pass through
 * at least one provider. Providers are intentionally stateless so that the
 * server can use a single shared instance per JVM.
 *
 * <p>The authentication decision model is:
 * <pre>
 * HTTP request
 *      │
 *      ▼
 * AuthenticationProvider.authenticate(exchange)
 *      │
 *      ├── returns AuthenticatedPrincipal  →  request proceeds
 *      └── throws A2AAuthenticationException  →  server returns HTTP 401
 * </pre>
 *
 * <p>Implementations MUST NOT swallow exceptions silently. A provider that
 * cannot determine whether the caller is authorised must throw
 * {@link A2AAuthenticationException} so that the server can reject the
 * request with a 401 response. Silent fallback to an authenticated state is
 * the vulnerability this interface is designed to prevent.
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see JwtAuthenticationProvider
 * @see SpiffeAuthenticationProvider
 * @see ApiKeyAuthenticationProvider
 * @see CompositeAuthenticationProvider
 */
public interface A2AAuthenticationProvider {

    /**
     * Attempt to authenticate the caller represented by {@code exchange}.
     *
     * <p>Implementations inspect request headers (e.g.
     * {@code Authorization: Bearer <token>}, client TLS certificate,
     * {@code X-API-Key}) and return a verified principal on success.
     *
     * @param exchange the inbound HTTP exchange; never {@code null}
     * @return a verified {@link AuthenticatedPrincipal} on success
     * @throws A2AAuthenticationException if the credentials are absent,
     *         invalid, expired, or otherwise unacceptable. The exception
     *         message is included in the 401 response body returned to the
     *         caller, so it must not disclose internal secrets.
     */
    AuthenticatedPrincipal authenticate(HttpExchange exchange)
            throws A2AAuthenticationException;

    /**
     * The authentication scheme name advertised to clients in
     * {@code WWW-Authenticate} response headers (e.g. {@code "Bearer"},
     * {@code "ApiKey"}, {@code "mTLS"}).
     *
     * @return non-null, non-empty scheme name
     */
    String scheme();

    /**
     * Return {@code true} when this provider can potentially handle the
     * credentials present in {@code exchange}. The composite provider uses
     * this to select candidate providers without triggering a full
     * authentication attempt on every provider in the chain.
     *
     * <p>Implementations should inspect header presence only (no validation).
     *
     * @param exchange the inbound HTTP exchange
     * @return {@code true} if this provider should try to authenticate
     */
    boolean canHandle(HttpExchange exchange);
}
