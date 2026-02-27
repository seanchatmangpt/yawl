package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.HttpExchange;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JWT Bearer-token authentication provider using HMAC-SHA256 (HS256).
 *
 * <p>Accepts {@code Authorization: Bearer <token>} headers. The JWT is
 * verified against a symmetric key derived from the {@code A2A_JWT_SECRET}
 * environment variable. Token claims are mapped to permissions as follows:
 *
 * <ul>
 *   <li>Claim {@code "sub"} becomes the principal username.</li>
 *   <li>Claim {@code "scope"} (space-separated string) or {@code "permissions"}
 *       (JSON array of strings) become the granted permissions.</li>
 *   <li>Claim {@code "iss"} must equal the configured issuer when one is
 *       provided via {@code A2A_JWT_ISSUER}.</li>
 *   <li>Claim {@code "aud"} must contain {@code "yawl-a2a"} when an audience
 *       constraint is active.</li>
 * </ul>
 *
 * <p>Minimum secret length is 32 bytes (256 bits) to satisfy HMAC-SHA256
 * key requirements. A secret shorter than 32 bytes is rejected at construction
 * time to prevent weak key derivation.
 *
 * <p>Environment variables consumed:
 * <ul>
 *   <li>{@code A2A_JWT_SECRET} (required) - HMAC-SHA256 signing key, at least
 *       32 ASCII characters. Generate with:
 *       {@code openssl rand -base64 32}</li>
 *   <li>{@code A2A_JWT_ISSUER} (optional) - expected issuer claim; omit to
 *       skip issuer validation.</li>
 * </ul>
 *
 * <p>Token generation (for clients) falls outside this server-side class.
 * Administrators issue tokens using a trusted tool or the YAWL CLI.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class JwtAuthenticationProvider implements A2AAuthenticationProvider {

    private static final String SCHEME = "Bearer";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUDIENCE_CLAIM = "yawl-a2a";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final String expectedIssuer;

    /**
     * Construct from explicit values. Prefer
     * {@link #fromEnvironment()} for production deployments.
     *
     * @param jwtSecretUtf8 the raw signing secret as a UTF-8 string; must be
     *                      at least 32 characters
     * @param issuer        the expected {@code iss} claim value, or
     *                      {@code null} to skip issuer validation
     * @throws IllegalArgumentException when the secret is too short
     */
    public JwtAuthenticationProvider(String jwtSecretUtf8, String issuer) {
        if (jwtSecretUtf8 == null) {
            throw new IllegalArgumentException(
                "JWT secret is required. Set A2A_JWT_SECRET environment variable.");
        }
        byte[] keyBytes = jwtSecretUtf8.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                "A2A_JWT_SECRET must be at least " + MIN_SECRET_BYTES
                + " bytes (256 bits). Current length: " + keyBytes.length
                + " bytes. Generate a suitable key with: openssl rand -base64 32");
        }
        this.signingKey     = Keys.hmacShaKeyFor(keyBytes);
        this.expectedIssuer = issuer;
    }

    /**
     * Construct from environment variables.
     *
     * <p>Reads {@code A2A_JWT_SECRET} and (optionally) {@code A2A_JWT_ISSUER}.
     *
     * @return configured provider
     * @throws IllegalStateException    when {@code A2A_JWT_SECRET} is absent
     * @throws IllegalArgumentException when the secret is too short
     */
    public static JwtAuthenticationProvider fromEnvironment() {
        String secret = System.getenv("A2A_JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "A2A_JWT_SECRET environment variable is required for JWT authentication.\n"
                + "Generate one with: openssl rand -base64 32");
        }
        String issuer = System.getenv("A2A_JWT_ISSUER");
        if (issuer != null && issuer.isBlank()) {
            issuer = null;
        }
        return new JwtAuthenticationProvider(secret, issuer);
    }

    // --------------------------------------------------------- Provider API

    @Override
    public String scheme() {
        return SCHEME;
    }

    @Override
    public boolean canHandle(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
        return authHeader != null && authHeader.startsWith(BEARER_PREFIX);
    }

    @Override
    public AuthenticatedPrincipal authenticate(HttpExchange exchange)
            throws A2AAuthenticationException {
        String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new A2AAuthenticationException(
                "Missing or malformed Authorization header. "
                + "Expected: Authorization: Bearer <token>",
                SCHEME);
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).strip();
        if (token.isEmpty()) {
            throw new A2AAuthenticationException(
                "Bearer token is empty in Authorization header", SCHEME);
        }

        return verifyToken(token);
    }

    // --------------------------------------------------------------- Internal

    private AuthenticatedPrincipal verifyToken(String token)
            throws A2AAuthenticationException {
        Claims claims;
        try {
            var parser = Jwts.parser()
                .verifyWith(signingKey)
                .requireAudience(AUDIENCE_CLAIM);

            if (expectedIssuer != null) {
                parser = parser.requireIssuer(expectedIssuer);
            }

            claims = parser.build()
                .parseSignedClaims(token)
                .getPayload();

        } catch (ExpiredJwtException e) {
            // Do not include the expiry time in the message to avoid time
            // oracle attacks. Only tell the client the token has expired.
            throw new A2AAuthenticationException(
                "Bearer token has expired. Obtain a new token and retry.",
                SCHEME);
        } catch (JwtException e) {
            // Generic JWT parse / validation error. Do not echo the raw
            // exception message as it may hint at internal key details.
            throw new A2AAuthenticationException(
                "Bearer token is invalid or its signature could not be verified.",
                SCHEME);
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new A2AAuthenticationException(
                "Bearer token is missing the required 'sub' (subject) claim.",
                SCHEME);
        }

        Set<String> permissions = extractPermissions(claims);
        Instant expiresAt = extractExpiry(claims);

        return new AuthenticatedPrincipal(
            subject,
            permissions,
            SCHEME,
            Instant.now(),
            expiresAt
        );
    }

    /**
     * Extract permissions from the {@code scope} or {@code permissions} claim.
     *
     * <p>The {@code scope} claim follows RFC 8693 as a space-delimited string.
     * The {@code permissions} claim follows Auth0 convention as a JSON array.
     * When both are present, their values are merged. When neither is present,
     * the full wildcard permission is granted so that tokens without explicit
     * scope still work for backward-compatible deployments. New tokens SHOULD
     * always include explicit scopes.
     */
    @SuppressWarnings("unchecked")
    private Set<String> extractPermissions(Claims claims) {
        Set<String> perms = new LinkedHashSet<>();

        // RFC 8693 scope claim: space-separated string
        Object scopeClaim = claims.get("scope");
        if (scopeClaim instanceof String scopeStr) {
            String[] parts = scopeStr.split("\\s+");
            perms.addAll(Arrays.asList(parts));
        }

        // Auth0 / OIDC permissions claim: array of strings
        Object permsClaim = claims.get("permissions");
        if (permsClaim instanceof Collection<?> permsCollection) {
            for (Object p : permsCollection) {
                if (p instanceof String ps) {
                    perms.add(ps);
                }
            }
        }

        // Fallback: no explicit scope means full access (backward compat)
        if (perms.isEmpty()) {
            perms.add(AuthenticatedPrincipal.PERM_ALL);
        }

        return perms;
    }

    private Instant extractExpiry(Claims claims) {
        Date exp = claims.getExpiration();
        return exp != null ? exp.toInstant() : null;
    }

    /**
     * Build a signed JWT for a client identity. Intended for administrative
     * tooling and integration tests. Production tokens should be issued by a
     * dedicated identity service.
     *
     * @param subject     the principal name (e.g. {@code "agent-order-processor"})
     * @param permissions permissions to embed in the {@code scope} claim
     * @param validForMs  token validity period in milliseconds
     * @return signed compact JWT string
     */
    public String issueToken(String subject, List<String> permissions, long validForMs) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (validForMs <= 0) {
            throw new IllegalArgumentException(
                "Token validity period must be positive (got " + validForMs + " ms)");
        }

        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(validForMs);

        String scope = permissions != null ? String.join(" ", permissions) : "";

        var builder = Jwts.builder()
            .subject(subject)
            .audience().add(AUDIENCE_CLAIM).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .claim("scope", scope)
            .signWith(signingKey);

        if (expectedIssuer != null) {
            builder = builder.issuer(expectedIssuer);
        }

        return builder.compact();
    }

    /**
     * Parse and verify the signature of a compact JWT string.
     *
     * @param jwt the compact JWT to parse
     * @return the verified claims payload
     * @throws io.jsonwebtoken.JwtException if signature is invalid, JWT is malformed,
     *         or the token has expired
     */
    public Claims parseClaims(String jwt) {
        return Jwts.parser()
                   .verifyWith(signingKey)
                   .build()
                   .parseSignedClaims(jwt)
                   .getPayload();
    }
}
