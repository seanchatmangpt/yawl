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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * OAuth2/OIDC JWT access token validator for YAWL API endpoints.
 *
 * <p>Performs the full RFC 7519 + RFC 8693 validation pipeline:
 * <ol>
 *   <li>Parse JWT header - extract {@code kid} and {@code alg}</li>
 *   <li>Fetch public key from JWKS endpoint (cached, auto-rotated every 5 minutes)</li>
 *   <li>Verify RS256/RS512 signature</li>
 *   <li>Validate standard claims: {@code exp}, {@code nbf}, {@code iss}, {@code aud}</li>
 *   <li>Extract subject and scope claims into {@link OidcUserContext}</li>
 * </ol>
 *
 * <p>JWKS keys are cached in a thread-safe LRU cache keyed by {@code kid}.
 * Cache misses trigger a synchronous JWKS refresh. A background scheduler
 * proactively refreshes the cache every 5 minutes to handle key rotation events
 * without impacting request latency.
 *
 * <p>Environment variables:
 * <ul>
 *   <li>{@code YAWL_OAUTH2_ISSUER_URI} - required, OIDC provider base URI</li>
 *   <li>{@code YAWL_OAUTH2_AUDIENCE} - required, expected audience claim value</li>
 *   <li>{@code YAWL_OAUTH2_JWKS_URI} - optional, override JWKS endpoint URI</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class OAuth2TokenValidator {

    private static final Logger log = LoggerFactory.getLogger(OAuth2TokenValidator.class);

    private static final String ENV_ISSUER_URI = "YAWL_OAUTH2_ISSUER_URI";
    private static final String ENV_AUDIENCE   = "YAWL_OAUTH2_AUDIENCE";
    private static final String ENV_JWKS_URI   = "YAWL_OAUTH2_JWKS_URI";

    private static final Duration HTTP_TIMEOUT              = Duration.ofSeconds(10);
    private static final long     JWKS_REFRESH_INTERVAL     = 5; // minutes
    private static final int      JWKS_CACHE_MAX_SIZE       = 64;
    private static final int      CLOCK_SKEW_SECONDS        = 30;
    private static final long     STALE_CACHE_THRESHOLD_SECONDS = 300; // 5 minutes (P0 Andon threshold)

    private final String issuerUri;
    private final String expectedAudience;
    private final String jwksUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Thread-safe JWKS key cache: kid -> RSA public key
    private final Map<String, PublicKey> jwksCache;
    private final ReadWriteLock          jwksCacheLock;
    private final ScheduledExecutorService refreshScheduler;

    // Cache age tracking for stale detection and Andon alerts
    private final AtomicLong lastSuccessfulRefreshEpochMs;

    // Security event bus for alerting
    private volatile SecurityEventBus securityEventBus;

    /**
     * Construct from environment variables.
     *
     * @throws IllegalStateException if required environment variables are absent
     */
    public OAuth2TokenValidator() {
        this(requireEnv(ENV_ISSUER_URI), requireEnv(ENV_AUDIENCE),
             System.getenv(ENV_JWKS_URI));
    }

    /**
     * Construct with explicit configuration.
     *
     * @param issuerUri        OIDC provider issuer URI
     * @param expectedAudience required audience claim value
     * @param jwksUriOverride  optional explicit JWKS URI; if null, derived from issuerUri
     */
    public OAuth2TokenValidator(String issuerUri, String expectedAudience,
                                String jwksUriOverride) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalArgumentException("issuerUri must not be blank");
        }
        if (expectedAudience == null || expectedAudience.isBlank()) {
            throw new IllegalArgumentException("expectedAudience must not be blank");
        }
        this.issuerUri        = issuerUri.endsWith("/") ? issuerUri.substring(0, issuerUri.length() - 1) : issuerUri;
        this.expectedAudience = expectedAudience;
        this.jwksUri          = (jwksUriOverride != null && !jwksUriOverride.isBlank())
                                ? jwksUriOverride
                                : this.issuerUri + "/protocol/openid-connect/certs";
        this.httpClient   = HttpClient.newBuilder()
                                      .connectTimeout(HTTP_TIMEOUT)
                                      .build();
        this.objectMapper = new ObjectMapper();
        this.jwksCache    = new ConcurrentHashMap<>(JWKS_CACHE_MAX_SIZE);
        this.jwksCacheLock = new ReentrantReadWriteLock();
        this.lastSuccessfulRefreshEpochMs = new AtomicLong(0);
        this.securityEventBus = SecurityEventBus.getInstance();

        // Register gauge suppliers for real-time OTEL metrics
        this.securityEventBus.setCacheAgeSupplier(this::getCacheAgeSeconds);
        this.securityEventBus.setCacheStaleSupplier(this::isCacheStale);

        // Initial load
        refreshJwksCache();

        // Scheduled background refresh for key rotation handling
        this.refreshScheduler = Executors.newSingleThreadScheduledExecutor(r ->
                Thread.ofVirtual().name("yawl-jwks-refresh").unstarted(r));
        this.refreshScheduler.scheduleAtFixedRate(
                this::refreshJwksCache,
                JWKS_REFRESH_INTERVAL,
                JWKS_REFRESH_INTERVAL,
                TimeUnit.MINUTES
        );
        log.info("OAuth2TokenValidator initialized. Issuer={}, Audience={}, JWKS={}",
                 this.issuerUri, this.expectedAudience, this.jwksUri);
    }

    /**
     * Validate a Bearer token and extract the OIDC user context.
     *
     * @param bearerToken raw token value (without "Bearer " prefix)
     * @return validated user context with subject, scopes, and claims
     * @throws OAuth2ValidationException if the token is invalid, expired, or untrusted
     */
    public OidcUserContext validate(String bearerToken) throws OAuth2ValidationException {
        if (bearerToken == null || bearerToken.isBlank()) {
            throw new OAuth2ValidationException("Bearer token must not be blank",
                                                OAuth2ValidationException.Code.MISSING_TOKEN);
        }

        String[] parts = bearerToken.split("\\.", -1);
        if (parts.length != 3) {
            throw new OAuth2ValidationException("Malformed JWT: expected 3 dot-separated parts, got " + parts.length,
                                                OAuth2ValidationException.Code.MALFORMED_TOKEN);
        }

        JsonNode header  = decodeBase64UrlJson(parts[0], "JWT header");
        JsonNode payload = decodeBase64UrlJson(parts[1], "JWT payload");

        String alg = requireTextClaim(header, "alg", "JWT header");
        String kid = header.path("kid").asText(null);

        validateAlgorithm(alg);
        verifySignature(parts, alg, kid);
        validateClaims(payload);

        return buildUserContext(payload);
    }

    // -------------------------------------------------------------------------
    // Signature verification
    // -------------------------------------------------------------------------

    private void verifySignature(String[] parts, String alg, String kid)
            throws OAuth2ValidationException {
        PublicKey publicKey = resolvePublicKey(kid);
        byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new OAuth2ValidationException("Invalid JWT signature encoding",
                                                OAuth2ValidationException.Code.INVALID_SIGNATURE);
        }

        String jcaAlg = alg.equals("RS256") ? "SHA256withRSA" : "SHA512withRSA";
        try {
            Signature verifier = Signature.getInstance(jcaAlg);
            verifier.initVerify(publicKey);
            verifier.update(signingInput);
            boolean valid = verifier.verify(signatureBytes);
            if (!valid) {
                throw new OAuth2ValidationException("JWT signature verification failed",
                                                    OAuth2ValidationException.Code.INVALID_SIGNATURE);
            }
        } catch (OAuth2ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new OAuth2ValidationException("Signature verification error: " + e.getMessage(),
                                                OAuth2ValidationException.Code.INVALID_SIGNATURE);
        }
    }

    private PublicKey resolvePublicKey(String kid) throws OAuth2ValidationException {
        // Check for stale cache before resolving key
        checkAndEmitStaleWarning();

        jwksCacheLock.readLock().lock();
        try {
            if (kid != null && jwksCache.containsKey(kid)) {
                return jwksCache.get(kid);
            }
            // If no kid in token, return first available key (single-key JWKS)
            if (kid == null && !jwksCache.isEmpty()) {
                return jwksCache.values().iterator().next();
            }
        } finally {
            jwksCacheLock.readLock().unlock();
        }

        // Cache miss: force refresh and retry once
        log.info("JWKS cache miss for kid={}, forcing refresh", kid);
        refreshJwksCache();

        jwksCacheLock.readLock().lock();
        try {
            if (kid != null && jwksCache.containsKey(kid)) {
                return jwksCache.get(kid);
            }
            if (kid == null && !jwksCache.isEmpty()) {
                return jwksCache.values().iterator().next();
            }
        } finally {
            jwksCacheLock.readLock().unlock();
        }

        throw new OAuth2ValidationException(
                "No public key found for kid=" + kid + ". JWKS endpoint returned " + jwksCache.size() + " keys.",
                OAuth2ValidationException.Code.KEY_NOT_FOUND);
    }

    /**
     * Check cache staleness and emit warning if threshold exceeded.
     * Uses throttling to avoid log spam (at most once per minute).
     */
    private void checkAndEmitStaleWarning() {
        long cacheAgeSeconds = getCacheAgeSeconds();
        if (cacheAgeSeconds > STALE_CACHE_THRESHOLD_SECONDS) {
            // Throttle warnings: emit at most once per minute
            long now = System.currentTimeMillis();
            long lastWarning = lastStaleWarningEpochMs.get();
            if (now - lastWarning > 60000) {
                if (lastStaleWarningEpochMs.compareAndSet(lastWarning, now)) {
                    emitStaleCacheWarning(cacheAgeSeconds);
                }
            }
        }
    }

    // Timestamp of last stale cache warning (for throttling)
    private final AtomicLong lastStaleWarningEpochMs = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Claims validation
    // -------------------------------------------------------------------------

    private void validateClaims(JsonNode payload) throws OAuth2ValidationException {
        long now = Instant.now().getEpochSecond();

        // exp - required
        if (!payload.has("exp")) {
            throw new OAuth2ValidationException("Token missing required 'exp' claim",
                                                OAuth2ValidationException.Code.MISSING_CLAIM);
        }
        long exp = payload.get("exp").asLong();
        if (now > exp + CLOCK_SKEW_SECONDS) {
            throw new OAuth2ValidationException(
                    "Token has expired (exp=" + exp + ", now=" + now + ")",
                    OAuth2ValidationException.Code.TOKEN_EXPIRED);
        }

        // nbf - optional
        if (payload.has("nbf")) {
            long nbf = payload.get("nbf").asLong();
            if (now < nbf - CLOCK_SKEW_SECONDS) {
                throw new OAuth2ValidationException(
                        "Token not yet valid (nbf=" + nbf + ", now=" + now + ")",
                        OAuth2ValidationException.Code.TOKEN_NOT_YET_VALID);
            }
        }

        // iss - required
        String iss = payload.path("iss").asText(null);
        if (iss == null || iss.isBlank()) {
            throw new OAuth2ValidationException("Token missing 'iss' claim",
                                                OAuth2ValidationException.Code.MISSING_CLAIM);
        }
        if (!issuerUri.equals(iss)) {
            throw new OAuth2ValidationException(
                    "Token issuer mismatch: expected='" + issuerUri + "' got='" + iss + "'",
                    OAuth2ValidationException.Code.ISSUER_MISMATCH);
        }

        // aud - required; may be string or array
        boolean audienceMatch = false;
        JsonNode audNode = payload.path("aud");
        if (audNode.isTextual()) {
            audienceMatch = expectedAudience.equals(audNode.asText());
        } else if (audNode.isArray()) {
            for (JsonNode aud : audNode) {
                if (expectedAudience.equals(aud.asText())) {
                    audienceMatch = true;
                    break;
                }
            }
        }
        if (!audienceMatch) {
            throw new OAuth2ValidationException(
                    "Token audience does not include expected value '" + expectedAudience + "'",
                    OAuth2ValidationException.Code.AUDIENCE_MISMATCH);
        }

        // sub - required
        String sub = payload.path("sub").asText(null);
        if (sub == null || sub.isBlank()) {
            throw new OAuth2ValidationException("Token missing required 'sub' claim",
                                                OAuth2ValidationException.Code.MISSING_CLAIM);
        }
    }

    private OidcUserContext buildUserContext(JsonNode payload) {
        String subject  = payload.path("sub").asText();
        String email    = payload.path("email").asText(null);
        String name     = payload.path("name").asText(null);
        long   exp      = payload.path("exp").asLong();

        // Extract scopes from RFC 8693 'scope' claim (space-separated string)
        java.util.Set<String> scopes = new java.util.LinkedHashSet<>();
        JsonNode scopeNode = payload.path("scope");
        if (scopeNode.isTextual()) {
            for (String s : scopeNode.asText().split("\\s+")) {
                if (!s.isBlank()) scopes.add(s);
            }
        }
        // Also collect OIDC 'permissions' array (Auth0/Keycloak convention)
        JsonNode permsNode = payload.path("permissions");
        if (permsNode.isArray()) {
            for (JsonNode p : permsNode) {
                if (p.isTextual()) scopes.add(p.asText());
            }
        }
        // Realm roles (Keycloak: realm_access.roles)
        JsonNode realmRoles = payload.path("realm_access").path("roles");
        java.util.Set<String> roles = new java.util.LinkedHashSet<>();
        if (realmRoles.isArray()) {
            for (JsonNode r : realmRoles) {
                if (r.isTextual()) roles.add(r.asText());
            }
        }
        // Resource roles (Keycloak: resource_access.<clientId>.roles)
        JsonNode resourceAccess = payload.path("resource_access");
        if (resourceAccess.isObject()) {
            resourceAccess.fields().forEachRemaining(e -> {
                JsonNode clientRoles = e.getValue().path("roles");
                if (clientRoles.isArray()) {
                    clientRoles.forEach(r -> { if (r.isTextual()) roles.add(r.asText()); });
                }
            });
        }

        Map<String, Object> rawClaims = new LinkedHashMap<>();
        payload.fields().forEachRemaining(e -> rawClaims.put(e.getKey(), e.getValue().asText()));

        return new OidcUserContext(subject, email, name, scopes, roles,
                                  Instant.ofEpochSecond(exp), rawClaims);
    }

    // -------------------------------------------------------------------------
    // JWKS fetching and caching
    // -------------------------------------------------------------------------

    private void refreshJwksCache() {
        try {
            log.debug("Refreshing JWKS from {}", jwksUri);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUri))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new IllegalStateException("JWKS endpoint returned HTTP " + response.statusCode());
            }

            JsonNode jwksRoot = objectMapper.readTree(response.body());
            JsonNode keys = jwksRoot.path("keys");
            if (!keys.isArray() || keys.isEmpty()) {
                throw new IllegalStateException("JWKS response contains no keys");
            }

            Map<String, PublicKey> newCache = new LinkedHashMap<>();
            for (JsonNode keyNode : keys) {
                String kty = keyNode.path("kty").asText();
                String use = keyNode.path("use").asText("sig");
                if (!"RSA".equals(kty) || !"sig".equals(use)) {
                    continue;
                }
                String keyId  = keyNode.path("kid").asText(null);
                String nBase64 = keyNode.path("n").asText(null);
                String eBase64 = keyNode.path("e").asText(null);
                if (nBase64 == null || eBase64 == null) {
                    log.warn("Skipping JWK entry: missing 'n' or 'e' components");
                    continue;
                }
                PublicKey pk = buildRsaPublicKey(nBase64, eBase64);
                String cacheKey = (keyId != null && !keyId.isBlank()) ? keyId : "_default_" + newCache.size();
                newCache.put(cacheKey, pk);
            }

            if (newCache.isEmpty()) {
                throw new IllegalStateException("JWKS response contained no usable RSA signing keys");
            }

            jwksCacheLock.writeLock().lock();
            try {
                jwksCache.clear();
                jwksCache.putAll(newCache);
            } finally {
                jwksCacheLock.writeLock().unlock();
            }

            // Update last successful refresh timestamp
            lastSuccessfulRefreshEpochMs.set(System.currentTimeMillis());

            // Emit success event
            emitJwksRefreshSuccess(newCache.size());

            log.info("JWKS cache refreshed: {} RSA signing key(s) loaded", newCache.size());

        } catch (Exception e) {
            // P0 Andon alert: JWKS refresh failure
            // The system continues with stale cache but token validation may fail
            long cacheAgeSeconds = getCacheAgeSeconds();
            int cacheSize = jwksCache.size();

            emitJwksRefreshFailure(e, cacheSize, cacheAgeSeconds);

            log.error("JWKS refresh failed: {}. Continuing with stale cache ({} keys, {} seconds old)",
                      e.getMessage(), cacheSize, cacheAgeSeconds);
        }
    }

    /**
     * Get the age of the JWKS cache in seconds.
     *
     * @return cache age in seconds, or -1 if never refreshed
     */
    public long getCacheAgeSeconds() {
        long lastRefresh = lastSuccessfulRefreshEpochMs.get();
        if (lastRefresh == 0) {
            return -1;
        }
        return (System.currentTimeMillis() - lastRefresh) / 1000;
    }

    /**
     * Check if the JWKS cache is stale (exceeds threshold).
     *
     * @return true if cache is stale or never refreshed
     */
    public boolean isCacheStale() {
        long age = getCacheAgeSeconds();
        return age < 0 || age > STALE_CACHE_THRESHOLD_SECONDS;
    }

    /**
     * Get the number of keys in the JWKS cache.
     *
     * @return cache size
     */
    public int getCacheSize() {
        jwksCacheLock.readLock().lock();
        try {
            return jwksCache.size();
        } finally {
            jwksCacheLock.readLock().unlock();
        }
    }

    /**
     * Set a custom SecurityEventBus. For testing or custom configurations.
     *
     * @param eventBus the event bus to use
     */
    public void setSecurityEventBus(SecurityEventBus eventBus) {
        this.securityEventBus = eventBus != null ? eventBus : SecurityEventBus.getInstance();
    }

    private void emitJwksRefreshSuccess(int cacheSize) {
        SecurityEventBus bus = this.securityEventBus;
        if (bus != null) {
            bus.publish(new SecurityEventBus.JwksRefreshSuccess(jwksUri, cacheSize));
        }
    }

    private void emitJwksRefreshFailure(Throwable cause, int cacheSize, long cacheAgeSeconds) {
        SecurityEventBus bus = this.securityEventBus;
        if (bus != null) {
            bus.publish(new SecurityEventBus.JwksRefreshFailure(jwksUri, cause, cacheSize, cacheAgeSeconds));
        }
    }

    private void emitStaleCacheWarning(long cacheAgeSeconds) {
        SecurityEventBus bus = this.securityEventBus;
        if (bus != null) {
            bus.publish(new SecurityEventBus.JwksStaleCacheWarning(
                jwksUri, cacheAgeSeconds, STALE_CACHE_THRESHOLD_SECONDS));
        }
    }

    private static PublicKey buildRsaPublicKey(String nBase64Url, String eBase64Url)
            throws Exception {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        BigInteger modulus  = new BigInteger(1, decoder.decode(nBase64Url));
        BigInteger exponent = new BigInteger(1, decoder.decode(eBase64Url));
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    // -------------------------------------------------------------------------
    // Utility helpers
    // -------------------------------------------------------------------------

    private JsonNode decodeBase64UrlJson(String base64UrlPart, String partName)
            throws OAuth2ValidationException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64UrlPart);
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            throw new OAuth2ValidationException(
                    "Cannot decode " + partName + ": " + e.getMessage(),
                    OAuth2ValidationException.Code.MALFORMED_TOKEN);
        }
    }

    private static String requireTextClaim(JsonNode node, String fieldName, String context)
            throws OAuth2ValidationException {
        String value = node.path(fieldName).asText(null);
        if (value == null || value.isBlank()) {
            throw new OAuth2ValidationException(
                    context + " missing required field '" + fieldName + "'",
                    OAuth2ValidationException.Code.MISSING_CLAIM);
        }
        return value;
    }

    private static void validateAlgorithm(String alg) throws OAuth2ValidationException {
        if (!"RS256".equals(alg) && !"RS512".equals(alg)) {
            throw new OAuth2ValidationException(
                    "Unsupported JWT algorithm '" + alg + "'. Only RS256 and RS512 are accepted.",
                    OAuth2ValidationException.Code.UNSUPPORTED_ALGORITHM);
        }
    }

    private static String requireEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException(
                    "Required environment variable '" + name + "' is not set.\n"
                    + "Set it to configure the OAuth2 token validator.");
        }
        return val;
    }

    /**
     * Shutdown background JWKS refresh scheduler. Call on application stop.
     */
    public void shutdown() {
        refreshScheduler.shutdown();
        log.info("OAuth2TokenValidator shutdown");
    }
}
