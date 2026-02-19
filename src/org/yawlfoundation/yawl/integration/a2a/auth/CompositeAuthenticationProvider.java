package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Composite authentication provider that chains multiple providers.
 *
 * <p>The composite evaluates providers in registration order. It first calls
 * {@link A2AAuthenticationProvider#canHandle} on each provider to find
 * candidates, then calls {@link A2AAuthenticationProvider#authenticate} on
 * each candidate in order. The first successful authentication wins.
 *
 * <p>Failure handling:
 * <ul>
 *   <li>When a provider indicates it {@code canHandle} the request but then
 *       throws {@link A2AAuthenticationException}, the composite tries the
 *       next candidate. The exception is retained as context for the final
 *       rejection message.</li>
 *   <li>When no provider can handle the request at all (none return
 *       {@code true} from {@code canHandle}), an exception describing all
 *       supported schemes is thrown immediately.</li>
 *   <li>When all candidates fail, the composite throws an exception that
 *       aggregates the failure reasons from all candidates.</li>
 * </ul>
 *
 * <p>This design prevents a provider ordering bug from silently granting
 * access: success requires at least one positive authentication, not merely
 * the absence of a rejection.
 *
 * <p>Factory method {@link #production()} builds the recommended three-layer
 * stack: mTLS (SPIFFE) for service-to-service, JWT Bearer for external
 * clients, and API key for operational tooling.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class CompositeAuthenticationProvider implements A2AAuthenticationProvider {

    private final List<A2AAuthenticationProvider> providers;

    /**
     * Construct a composite from an ordered list of providers.
     *
     * @param providers evaluation order; first match wins; never null or empty
     */
    public CompositeAuthenticationProvider(List<A2AAuthenticationProvider> providers) {
        Objects.requireNonNull(providers, "providers list must not be null");
        if (providers.isEmpty()) {
            throw new IllegalArgumentException(
                "CompositeAuthenticationProvider requires at least one provider");
        }
        for (A2AAuthenticationProvider p : providers) {
            Objects.requireNonNull(p, "provider in list must not be null");
        }
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
    }

    /**
     * Construct a composite from varargs providers.
     *
     * @param first    the first (highest-priority) provider; never null
     * @param rest     additional providers in decreasing priority order
     */
    public CompositeAuthenticationProvider(A2AAuthenticationProvider first,
                                           A2AAuthenticationProvider... rest) {
        Objects.requireNonNull(first, "first provider must not be null");
        List<A2AAuthenticationProvider> list = new ArrayList<>();
        list.add(first);
        if (rest != null) {
            for (A2AAuthenticationProvider p : rest) {
                Objects.requireNonNull(p, "provider in rest must not be null");
                list.add(p);
            }
        }
        this.providers = Collections.unmodifiableList(list);
    }

    /**
     * Build the recommended production authentication stack.
     *
     * <p>Stack (evaluated in order):
     * <ol>
     *   <li>{@link SpiffeAuthenticationProvider} - mTLS for service-to-service</li>
     *   <li>{@link JwtAuthenticationProvider} - JWT Bearer for external clients</li>
     *   <li>{@link ApiKeyAuthenticationProvider} - API keys for tooling</li>
     * </ol>
     *
     * <p>Each provider is configured from its own environment variables. Only
     * providers whose required variables are set are included. At least one
     * provider must be configurable; if none can be instantiated an
     * {@link IllegalStateException} is thrown.
     *
     * @return composite provider for production deployment
     * @throws IllegalStateException when no provider can be configured from
     *                               the current environment
     */
    public static CompositeAuthenticationProvider production() {
        List<A2AAuthenticationProvider> stack = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 1. SPIFFE mTLS - preferred for service-to-service
        // Active when A2A_SPIFFE_TRUST_DOMAIN is set or defaults to yawl.cloud
        try {
            stack.add(SpiffeAuthenticationProvider.fromEnvironment());
        } catch (Exception e) {
            errors.add("SpiffeAuthenticationProvider: " + e.getMessage());
        }

        // 2. JWT Bearer - for external agent clients
        // Active when A2A_JWT_SECRET is set
        String jwtSecret = System.getenv("A2A_JWT_SECRET");
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            try {
                stack.add(JwtAuthenticationProvider.fromEnvironment());
            } catch (Exception e) {
                errors.add("JwtAuthenticationProvider: " + e.getMessage());
            }
        }

        // 3. API Key - for operational tooling
        // Active when A2A_API_KEY_MASTER is set
        String apiKeyMaster = System.getenv("A2A_API_KEY_MASTER");
        if (apiKeyMaster != null && !apiKeyMaster.isBlank()) {
            try {
                stack.add(ApiKeyAuthenticationProvider.fromEnvironment());
            } catch (Exception e) {
                errors.add("ApiKeyAuthenticationProvider: " + e.getMessage());
            }
        }

        // 4. Handoff Token - for agent-to-agent work item transfer
        // Always available for handoff operations
        try {
            stack.add(new HandoffTokenAuthenticationProvider());
        } catch (Exception e) {
            errors.add("HandoffTokenAuthenticationProvider: " + e.getMessage());
        }

        if (stack.isEmpty()) {
            throw new IllegalStateException(
                "No authentication providers could be configured. "
                + "Set at least one of the following environment variables:\n"
                + "  A2A_JWT_SECRET       (JWT Bearer authentication)\n"
                + "  A2A_API_KEY_MASTER   (API key authentication)\n"
                + "  A2A_SPIFFE_TRUST_DOMAIN  (mTLS/SPIFFE authentication)\n"
                + "Provider errors:\n"
                + String.join("\n", errors));
        }

        return new CompositeAuthenticationProvider(stack);
    }

    // --------------------------------------------------------- Provider API

    @Override
    public String scheme() {
        return providers.stream()
            .map(A2AAuthenticationProvider::scheme)
            .collect(Collectors.joining(", "));
    }

    @Override
    public boolean canHandle(HttpExchange exchange) {
        return providers.stream().anyMatch(p -> p.canHandle(exchange));
    }

    @Override
    public AuthenticatedPrincipal authenticate(HttpExchange exchange)
            throws A2AAuthenticationException {
        List<A2AAuthenticationProvider> candidates = providers.stream()
            .filter(p -> p.canHandle(exchange))
            .toList();

        if (candidates.isEmpty()) {
            String supported = scheme();
            throw new A2AAuthenticationException(
                "No authentication credentials found in the request. "
                + "Supported schemes: " + supported + ". "
                + "Provide credentials via: "
                + "Authorization: Bearer <token>, "
                + "X-API-Key: <key>, "
                + "or a mutual TLS client certificate.",
                supported);
        }

        List<String> failures = new ArrayList<>();
        for (A2AAuthenticationProvider candidate : candidates) {
            try {
                return candidate.authenticate(exchange);
            } catch (A2AAuthenticationException e) {
                // Record the failure reason but do not disclose which provider
                // rejected the request (prevents scheme enumeration).
                failures.add(e.getMessage());
            }
        }

        // All candidates failed. Return a combined rejection message.
        String combined = failures.isEmpty()
            ? "Authentication failed."
            : failures.get(0); // Lead with the most specific failure

        throw new A2AAuthenticationException(combined, scheme());
    }

    /**
     * Return the list of configured providers (for diagnostics only).
     *
     * @return immutable list of providers in evaluation order
     */
    public List<A2AAuthenticationProvider> getProviders() {
        return providers;
    }
}
