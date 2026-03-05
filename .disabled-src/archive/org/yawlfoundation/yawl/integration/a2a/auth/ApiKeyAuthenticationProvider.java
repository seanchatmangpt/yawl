package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * API-key authentication provider for the YAWL A2A server.
 *
 * <p>Accepts requests carrying an {@code X-API-Key} header whose value is a
 * registered key identifier. The key identifier is looked up in an in-memory
 * registry that maps keys to their associated permissions and principal name.
 * Keys are stored as HMAC-SHA256 digests of the raw secret, so the plaintext
 * key is never held in memory beyond the registration call.
 *
 * <p>Protocol: the client sends
 * <pre>
 *   X-API-Key: &lt;raw-api-key&gt;
 * </pre>
 * The server computes {@code HMAC-SHA256(masterKey, rawApiKey)} and compares
 * with the stored digest using a constant-time comparison to prevent timing
 * attacks.
 *
 * <p>API keys do not expire. Revocation is immediate: remove the key entry
 * from the registry.
 *
 * <p>Environment variable bootstrapping: when
 * {@code A2A_API_KEY} is set in the environment, the provider registers that
 * value automatically with full permissions under the principal name
 * {@code "api-key-client"}. This supports single-client deployments without
 * any programmatic key registration.
 *
 * <p>For multi-client deployments use {@link #registerKey} after construction.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ApiKeyAuthenticationProvider implements A2AAuthenticationProvider {

    private static final String SCHEME         = "ApiKey";
    private static final String HEADER_NAME    = "X-API-Key";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Registered key entry mapping a key digest to principal details.
     */
    private record KeyEntry(String username, Set<String> permissions, byte[] digest) {}

    /**
     * Registry keyed by key-id (an opaque identifier, not the secret itself).
     * The secret is only ever held as its HMAC-SHA256 digest.
     */
    private final Map<String, KeyEntry> registry;

    /**
     * Master HMAC key used to derive per-entry digests. This prevents an
     * attacker who obtains the in-memory registry from using the digests
     * as direct API keys on a different server.
     */
    private final byte[] masterKeyBytes;

    /**
     * Construct provider from the given master key material.
     *
     * @param masterKeyUtf8 raw master key used to derive stored digests;
     *                      must be at least 16 characters
     */
    public ApiKeyAuthenticationProvider(String masterKeyUtf8) {
        if (masterKeyUtf8 == null || masterKeyUtf8.length() < 16) {
            throw new IllegalArgumentException(
                "API key master key must be at least 16 characters. "
                + "Set A2A_API_KEY_MASTER or provide a key programmatically. "
                + "Generate with: openssl rand -hex 32");
        }
        this.masterKeyBytes = masterKeyUtf8.getBytes(StandardCharsets.UTF_8);
        // Use LinkedHashMap for deterministic iteration order in diagnostics
        this.registry = Collections.synchronizedMap(new LinkedHashMap<>());
    }

    /**
     * Build from environment variables.
     *
     * <p>Reads {@code A2A_API_KEY_MASTER} for the master HMAC key and
     * optionally {@code A2A_API_KEY} to register a single default client key.
     *
     * @return configured provider with at least one registered key when
     *         {@code A2A_API_KEY} is set
     * @throws IllegalStateException when {@code A2A_API_KEY_MASTER} is absent
     */
    public static ApiKeyAuthenticationProvider fromEnvironment() {
        String master = System.getenv("A2A_API_KEY_MASTER");
        if (master == null || master.isBlank()) {
            throw new IllegalStateException(
                "A2A_API_KEY_MASTER environment variable is required for API-key "
                + "authentication. Generate with: openssl rand -hex 32");
        }

        ApiKeyAuthenticationProvider provider = new ApiKeyAuthenticationProvider(master);

        String apiKey = System.getenv("A2A_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            provider.registerKey(
                "default",
                "api-key-client",
                apiKey,
                Set.of(AuthenticatedPrincipal.PERM_ALL)
            );
        }

        return provider;
    }

    // ------------------------------------------------ Key management

    /**
     * Register a new API key.
     *
     * <p>The raw key value is immediately hashed and discarded; only the
     * digest is retained.
     *
     * @param keyId       opaque identifier for this key (e.g. {@code "agent-1"});
     *                    used in log messages and can be used to revoke
     * @param username    principal name associated with this key
     * @param rawKeyValue the plaintext key value the client will present
     * @param permissions the permissions granted to this key
     * @throws IllegalArgumentException when any parameter is null or blank
     */
    public void registerKey(String keyId,
                            String username,
                            String rawKeyValue,
                            Set<String> permissions) {
        Objects.requireNonNull(keyId,       "keyId must not be null");
        Objects.requireNonNull(username,    "username must not be null");
        Objects.requireNonNull(rawKeyValue, "rawKeyValue must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");

        if (keyId.isBlank())       throw new IllegalArgumentException("keyId must not be blank");
        if (username.isBlank())    throw new IllegalArgumentException("username must not be blank");
        if (rawKeyValue.isBlank()) throw new IllegalArgumentException("rawKeyValue must not be blank");

        byte[] digest = computeDigest(rawKeyValue);
        registry.put(keyId, new KeyEntry(username, Set.copyOf(permissions), digest));
    }

    /**
     * Revoke a previously registered API key.
     *
     * @param keyId the identifier supplied during registration
     * @return {@code true} if a key was present and removed
     */
    public boolean revokeKey(String keyId) {
        return registry.remove(keyId) != null;
    }

    /**
     * Return the number of registered keys. Useful for health checks.
     *
     * @return count of active keys
     */
    public int registeredKeyCount() {
        return registry.size();
    }

    // --------------------------------------------------------- Provider API

    @Override
    public String scheme() {
        return SCHEME;
    }

    @Override
    public boolean canHandle(HttpExchange exchange) {
        return exchange.getRequestHeaders().getFirst(HEADER_NAME) != null;
    }

    @Override
    public AuthenticatedPrincipal authenticate(HttpExchange exchange)
            throws A2AAuthenticationException {
        String rawKey = exchange.getRequestHeaders().getFirst(HEADER_NAME);
        if (rawKey == null || rawKey.isBlank()) {
            throw new A2AAuthenticationException(
                "Missing X-API-Key header. Provide a valid API key.",
                SCHEME);
        }

        if (registry.isEmpty()) {
            throw new A2AAuthenticationException(
                "No API keys are configured on this server.", SCHEME);
        }

        byte[] presentedDigest = computeDigest(rawKey.strip());

        KeyEntry matched = null;
        // Iterate all entries; constant-time per-entry comparison prevents
        // timing attacks that could enumerate valid key-ids.
        for (KeyEntry entry : registry.values()) {
            if (MessageDigest.isEqual(entry.digest(), presentedDigest)) {
                matched = entry;
                // Do not break early: continue to prevent timing attacks.
            }
        }

        if (matched == null) {
            throw new A2AAuthenticationException(
                "API key is invalid or has been revoked.", SCHEME);
        }

        return new AuthenticatedPrincipal(
            matched.username(),
            matched.permissions(),
            SCHEME,
            Instant.now(),
            null // API keys do not expire; revoke via revokeKey()
        );
    }

    // --------------------------------------------------------------- Internal

    private byte[] computeDigest(String rawKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterKeyBytes, HMAC_ALGORITHM));
            return mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // HMAC-SHA256 is mandated by JDK specification (JCA); should
            // never happen on any conformant JVM.
            throw new IllegalStateException(
                "HMAC-SHA256 not available. JVM is non-conformant.", e);
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(
                "Master key material is invalid for HMAC-SHA256.", e);
        }
    }
}
