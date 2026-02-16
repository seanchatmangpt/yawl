package org.yawlfoundation.yawl.integration.spiffe;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;
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
public class SpiffeWorkloadIdentity {

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
     */
    public SpiffeWorkloadIdentity(String spiffeId, X509Certificate[] x509Chain) {
        if (spiffeId == null || !spiffeId.startsWith("spiffe://")) {
            throw new IllegalArgumentException("Invalid SPIFFE ID format: " + spiffeId);
        }
        if (x509Chain == null || x509Chain.length == 0) {
            throw new IllegalArgumentException("X.509 certificate chain is required");
        }

        this.spiffeId = spiffeId;
        this.type = SvidType.X509;
        this.x509Chain = x509Chain;
        this.jwtToken = null;
        this.expiresAt = x509Chain[0].getNotAfter().toInstant();
        this.trustDomain = extractTrustDomain(spiffeId);
    }

    /**
     * Create a JWT SVID identity
     */
    public SpiffeWorkloadIdentity(String spiffeId, String jwtToken, Instant expiresAt) {
        if (spiffeId == null || !spiffeId.startsWith("spiffe://")) {
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
     * Get the X.509 certificate chain (for X509 SVIDs)
     */
    public Optional<X509Certificate[]> getX509Chain() {
        return Optional.ofNullable(x509Chain);
    }

    /**
     * Get the leaf certificate (for X509 SVIDs)
     */
    public Optional<X509Certificate> getLeafCertificate() {
        return x509Chain != null && x509Chain.length > 0
            ? Optional.of(x509Chain[0])
            : Optional.empty();
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
     * Get the workload path from the SPIFFE ID
     * Example: spiffe://yawl.cloud/engine/instance-1 -> /engine/instance-1
     */
    public String getWorkloadPath() {
        int pathStart = spiffeId.indexOf('/', "spiffe://".length());
        return pathStart >= 0 ? spiffeId.substring(pathStart) : "/";
    }

    /**
     * Check if this identity matches a specific trust domain
     */
    public boolean isTrustedBy(String trustDomain) {
        return this.trustDomain.equals(trustDomain);
    }

    /**
     * Extract trust domain from SPIFFE ID
     * spiffe://yawl.cloud/path -> yawl.cloud
     */
    private String extractTrustDomain(String spiffeId) {
        String withoutScheme = spiffeId.substring("spiffe://".length());
        int slashIndex = withoutScheme.indexOf('/');
        return slashIndex >= 0
            ? withoutScheme.substring(0, slashIndex)
            : withoutScheme;
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
