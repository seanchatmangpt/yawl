package org.yawlfoundation.yawl.integration.spiffe;

import java.io.Serial;
import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * SPIFFE Workload Identity for YAWL
 *
 * Represents a SPIFFE Verifiable Identity Document (SVID) obtained from the
 * SPIRE Workload API. This identity is cryptographically verifiable and attests
 * to the workload's identity in a zero-trust environment.
 *
 * SPIFFE IDs follow the format: spiffe://trust-domain/path
 * Example: spiffe://yawl.cloud/engine/instance-1
 *          spiffe://yawl.cloud/agent/order-processor
 *          spiffe://yawl.cloud/service/zai-api
 *
 * This replaces API keys and secrets with short-lived, automatically rotated
 * X.509 certificates and JWT tokens that prove workload identity.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeWorkloadIdentity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SPIFFE_SCHEME = "spiffe://";

    private final String spiffeId;
    private final SvidType type;
    private final X509Certificate[] x509Chain;
    private final String jwtToken;
    private final Instant expiresAt;
    private final String trustDomain;

    /**
     * SVID type - X.509 or JWT
     */
    public enum SvidType {
        X509,
        JWT
    }

    /**
     * Create an X.509 SVID identity
     *
     * @param spiffeId the SPIFFE ID (must start with spiffe://)
     * @param x509Chain the X.509 certificate chain (leaf certificate first)
     * @throws IllegalArgumentException if spiffeId or x509Chain is invalid
     */
    public SpiffeWorkloadIdentity(String spiffeId, X509Certificate[] x509Chain) {
        if (spiffeId == null || !spiffeId.startsWith(SPIFFE_SCHEME)) {
            throw new IllegalArgumentException("Invalid SPIFFE ID format: " + spiffeId);
        }
        if (x509Chain == null || x509Chain.length == 0) {
            throw new IllegalArgumentException("X.509 certificate chain is required");
        }

        this.spiffeId = spiffeId;
        this.type = SvidType.X509;
        this.x509Chain = x509Chain.clone();
        this.jwtToken = null;
        this.expiresAt = x509Chain[0].getNotAfter().toInstant();
        this.trustDomain = extractTrustDomain(spiffeId);
    }

    /**
     * Create a JWT SVID identity
     *
     * @param spiffeId the SPIFFE ID (must start with spiffe://)
     * @param jwtToken the JWT token string
     * @param expiresAt the expiration time of the token
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public SpiffeWorkloadIdentity(String spiffeId, String jwtToken, Instant expiresAt) {
        if (spiffeId == null || !spiffeId.startsWith(SPIFFE_SCHEME)) {
            throw new IllegalArgumentException("Invalid SPIFFE ID format: " + spiffeId);
        }
        if (jwtToken == null || jwtToken.isEmpty()) {
            throw new IllegalArgumentException("JWT token is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Expiration time is required");
        }

        this.spiffeId = spiffeId;
        this.type = SvidType.JWT;
        this.x509Chain = null;
        this.jwtToken = jwtToken;
        this.expiresAt = expiresAt;
        this.trustDomain = extractTrustDomain(spiffeId);
    }

    /**
     * Get the SPIFFE ID (e.g., spiffe://yawl.cloud/engine/instance-1)
     */
    public String getSpiffeId() {
        return spiffeId;
    }

    /**
     * Get the SVID type (X509 or JWT)
     */
    public SvidType getType() {
        return type;
    }

    /**
     * Get the X.509 certificate chain (for X509 SVIDs).
     * Returns a defensive copy to maintain immutability.
     *
     * @return Optional containing the certificate chain, or empty if JWT type
     */
    public Optional<X509Certificate[]> getX509Chain() {
        return x509Chain != null
            ? Optional.of(x509Chain.clone())
            : Optional.empty();
    }

    /**
     * Get the leaf certificate (for X509 SVIDs).
     * This is the certificate that contains the workload's SPIFFE ID.
     *
     * @return Optional containing the leaf certificate, or empty if not available
     */
    public Optional<X509Certificate> getLeafCertificate() {
        return x509Chain != null && x509Chain.length > 0
            ? Optional.of(x509Chain[0])
            : Optional.empty();
    }

    /**
     * Get the remaining time until expiration.
     *
     * @return duration until expiration, or zero if already expired
     */
    public java.time.Duration getTimeUntilExpiry() {
        Instant now = Instant.now();
        if (now.isAfter(expiresAt)) {
            return java.time.Duration.ZERO;
        }
        return java.time.Duration.between(now, expiresAt);
    }

    /**
     * Get the percentage of the SVID lifetime that has elapsed.
     * Useful for rotation decisions.
     *
     * @param issuedAt when the SVID was issued
     * @return percentage of lifetime elapsed (0-100)
     */
    public double getLifetimeElapsedPercent(Instant issuedAt) {
        if (issuedAt == null) {
            return 0.0;
        }
        long totalLifetime = ChronoUnit.SECONDS.between(issuedAt, expiresAt);
        if (totalLifetime <= 0) {
            return 100.0;
        }
        long elapsed = ChronoUnit.SECONDS.between(issuedAt, Instant.now());
        return Math.min(100.0, Math.max(0.0, (elapsed * 100.0) / totalLifetime));
    }

    /**
     * Get the JWT token (for JWT SVIDs)
     */
    public Optional<String> getJwtToken() {
        return Optional.ofNullable(jwtToken);
    }

    /**
     * Get the expiration time
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Get the trust domain (e.g., "yawl.cloud")
     */
    public String getTrustDomain() {
        return trustDomain;
    }

    /**
     * Check if this identity has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this identity will expire within the given duration
     */
    public boolean willExpireSoon(java.time.Duration duration) {
        return Instant.now().plus(duration).isAfter(expiresAt);
    }

    /**
     * Get the workload path from the SPIFFE ID.
     * Example: spiffe://yawl.cloud/engine/instance-1 -> /engine/instance-1
     *
     * @return the workload path, or "/" if no path is present
     */
    public String getWorkloadPath() {
        int pathStart = spiffeId.indexOf('/', SPIFFE_SCHEME.length());
        return pathStart >= 0 ? spiffeId.substring(pathStart) : "/";
    }

    /**
     * Check if this identity matches a specific trust domain
     */
    public boolean isTrustedBy(String trustDomain) {
        return this.trustDomain.equals(trustDomain);
    }

    /**
     * Extract trust domain from SPIFFE ID.
     * spiffe://yawl.cloud/path -> yawl.cloud
     */
    private String extractTrustDomain(String spiffeId) {
        String withoutScheme = spiffeId.substring(SPIFFE_SCHEME.length());
        int slashIndex = withoutScheme.indexOf('/');
        return slashIndex >= 0
            ? withoutScheme.substring(0, slashIndex)
            : withoutScheme;
    }

    /**
     * Create a builder for constructing X.509 SVIDs.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpiffeWorkloadIdentity that = (SpiffeWorkloadIdentity) o;
        return Objects.equals(spiffeId, that.spiffeId)
            && type == that.type
            && Arrays.equals(x509Chain, that.x509Chain)
            && Objects.equals(jwtToken, that.jwtToken)
            && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(spiffeId, type, jwtToken, expiresAt);
        result = 31 * result + Arrays.hashCode(x509Chain);
        return result;
    }

    /**
     * Builder for SpiffeWorkloadIdentity.
     */
    public static class Builder {
        private String spiffeId;
        private X509Certificate[] x509Chain;
        private String jwtToken;
        private Instant expiresAt;

        public Builder spiffeId(String spiffeId) {
            this.spiffeId = spiffeId;
            return this;
        }

        public Builder x509Chain(X509Certificate[] chain) {
            this.x509Chain = chain != null ? chain.clone() : null;
            return this;
        }

        public Builder jwtToken(String token) {
            this.jwtToken = token;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public SpiffeWorkloadIdentity buildX509() {
            return new SpiffeWorkloadIdentity(spiffeId, x509Chain);
        }

        public SpiffeWorkloadIdentity buildJwt() {
            return new SpiffeWorkloadIdentity(spiffeId, jwtToken, expiresAt);
        }
    }

    @Override
    public String toString() {
        return String.format("SpiffeWorkloadIdentity{id=%s, type=%s, trustDomain=%s, expires=%s}",
            spiffeId, type, trustDomain, expiresAt);
    }

    /**
     * Create a bearer token for HTTP Authorization header
     * Returns JWT for JWT SVIDs, or empty for X509 (use mTLS instead)
     */
    public Optional<String> toBearerToken() {
        return type == SvidType.JWT
            ? Optional.of("Bearer " + jwtToken)
            : Optional.empty();
    }

    /**
     * Validate this identity
     */
    public void validate() throws SpiffeException {
        if (isExpired()) {
            throw new SpiffeException("SVID has expired at " + expiresAt);
        }

        if (type == SvidType.X509) {
            if (x509Chain == null || x509Chain.length == 0) {
                throw new SpiffeException("X.509 certificate chain is missing");
            }
            try {
                x509Chain[0].checkValidity();
            } catch (Exception e) {
                throw new SpiffeException("X.509 certificate validation failed", e);
            }
        }

        if (type == SvidType.JWT) {
            if (jwtToken == null || jwtToken.isEmpty()) {
                throw new SpiffeException("JWT token is missing");
            }
        }
    }
}
