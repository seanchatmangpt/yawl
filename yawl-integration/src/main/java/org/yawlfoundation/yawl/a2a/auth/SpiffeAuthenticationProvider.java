package org.yawlfoundation.yawl.integration.a2a.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import org.yawlfoundation.yawl.integration.spiffe.SpiffeException;
import org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadApiClient;
import org.yawlfoundation.yawl.integration.spiffe.SpiffeWorkloadIdentity;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.security.cert.Certificate;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutual-TLS (mTLS) authentication provider using SPIFFE X.509 SVIDs.
 *
 * <p>Validates the client certificate presented during the TLS handshake by:
 * <ol>
 *   <li>Extracting the {@code SubjectAltName URI} extension that carries the
 *       SPIFFE ID (e.g. {@code spiffe://yawl.cloud/agent/order-processor}).</li>
 *   <li>Verifying the SPIFFE ID belongs to a configured trust domain
 *       (e.g. {@code yawl.cloud}).</li>
 *   <li>Mapping the workload path of the SPIFFE ID to a permission set via a
 *       configurable policy table.</li>
 * </ol>
 *
 * <p>Chain-of-trust validation is delegated to the TLS layer (JSSE). This
 * class only inspects the SPIFFE ID once JSSE has confirmed that the
 * certificate was signed by a trusted CA. It does not re-validate the
 * certificate chain.
 *
 * <p>This provider operates on {@link HttpsExchange} instances only. When the
 * inbound exchange is a plain {@link com.sun.net.httpserver.HttpExchange} (not
 * HTTPS), {@link #canHandle} returns {@code false} and the request is
 * delegated to other providers in the chain.
 *
 * <p>The trust-domain and path-to-permissions mapping is configured at
 * construction time. Example mapping:
 * <pre>
 *   /engine      -&gt; [*]                    (full access for the engine itself)
 *   /agent       -&gt; [workflow:launch,
 *                    workflow:query,
 *                    workitem:manage]
 *   /monitor     -&gt; [workflow:query]       (read-only monitoring agent)
 * </pre>
 *
 * <p>Any SPIFFE workload path not present in the map is denied (zero
 * permissions). A principal with an empty permission set cannot execute any
 * workflow operation; the server enforces this at the operation level.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class SpiffeAuthenticationProvider implements A2AAuthenticationProvider {

    private static final String SCHEME = "mTLS";
    private static final String SPIFFE_SCHEME = "spiffe://";

    /** OID for the Subject Alternative Name extension. */
    private static final int SAN_URI_TYPE = 6;

    private final String trustedDomain;
    private final Map<String, Set<String>> pathPermissions;

    /**
     * Construct a SPIFFE mTLS provider.
     *
     * @param trustedDomain   the SPIFFE trust domain to accept, e.g.
     *                        {@code "yawl.cloud"}; never null
     * @param pathPermissions mapping from workload path prefix to permission
     *                        set, e.g.
     *                        {@code Map.of("/agent", Set.of("workflow:launch"))};
     *                        never null
     */
    public SpiffeAuthenticationProvider(String trustedDomain,
                                        Map<String, Set<String>> pathPermissions) {
        Objects.requireNonNull(trustedDomain, "trustedDomain must not be null");
        Objects.requireNonNull(pathPermissions, "pathPermissions must not be null");
        if (trustedDomain.isBlank()) {
            throw new IllegalArgumentException("trustedDomain must not be blank");
        }
        this.trustedDomain   = trustedDomain;
        this.pathPermissions = Map.copyOf(pathPermissions);
    }

    /**
     * Build a provider from environment variables with sensible defaults.
     *
     * <p>Reads:
     * <ul>
     *   <li>{@code A2A_SPIFFE_TRUST_DOMAIN} - trust domain (default
     *       {@code "yawl.cloud"})</li>
     * </ul>
     *
     * <p>Uses a default permission policy:
     * <ul>
     *   <li>{@code /engine} path prefix - full access</li>
     *   <li>{@code /agent}  path prefix - launch, query, manage</li>
     *   <li>{@code /monitor} path prefix - query only</li>
     * </ul>
     *
     * @return configured provider
     */
    public static SpiffeAuthenticationProvider fromEnvironment() {
        String domain = System.getenv("A2A_SPIFFE_TRUST_DOMAIN");
        if (domain == null || domain.isBlank()) {
            domain = "yawl.cloud";
        }

        Map<String, Set<String>> defaultPolicy = Map.of(
            "/engine",  Set.of(AuthenticatedPrincipal.PERM_ALL),
            "/agent",   Set.of(
                AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH,
                AuthenticatedPrincipal.PERM_WORKFLOW_QUERY,
                AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL,
                AuthenticatedPrincipal.PERM_WORKITEM_MANAGE
            ),
            "/monitor", Set.of(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
        );

        return new SpiffeAuthenticationProvider(domain, defaultPolicy);
    }

    // --------------------------------------------------------- Provider API

    @Override
    public String scheme() {
        return SCHEME;
    }

    /**
     * Returns {@code true} for HTTPS exchanges where a client certificate is
     * available on the TLS session.
     */
    @Override
    public boolean canHandle(HttpExchange exchange) {
        if (!(exchange instanceof HttpsExchange httpsExchange)) {
            return false;
        }
        SSLSession session = httpsExchange.getSSLSession();
        if (session == null) {
            return false;
        }
        try {
            Certificate[] peerCerts = session.getPeerCertificates();
            return peerCerts != null && peerCerts.length > 0;
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            return false;
        }
    }

    @Override
    public AuthenticatedPrincipal authenticate(HttpExchange exchange)
            throws A2AAuthenticationException {
        if (!(exchange instanceof HttpsExchange httpsExchange)) {
            throw new A2AAuthenticationException(
                "mTLS authentication requires a TLS connection. "
                + "Connect via HTTPS with a valid client certificate.",
                SCHEME);
        }

        SSLSession session = httpsExchange.getSSLSession();
        if (session == null) {
            throw new A2AAuthenticationException(
                "No TLS session found. Client certificate authentication failed.",
                SCHEME);
        }

        X509Certificate clientCert = extractClientCertificate(session);
        String spiffeId = extractSpiffeId(clientCert);
        validateTrustDomain(spiffeId);

        String workloadPath = extractWorkloadPath(spiffeId);
        Set<String> permissions = resolvePermissions(workloadPath);

        // The principal username is the full SPIFFE ID for auditability
        return new AuthenticatedPrincipal(
            spiffeId,
            permissions,
            SCHEME,
            Instant.now(),
            clientCert.getNotAfter().toInstant()
        );
    }

    // --------------------------------------------------------------- Internal

    private X509Certificate extractClientCertificate(SSLSession session)
            throws A2AAuthenticationException {
        try {
            Certificate[] peerCerts = session.getPeerCertificates();
            if (peerCerts == null || peerCerts.length == 0) {
                throw new A2AAuthenticationException(
                    "Client did not present a certificate. "
                    + "mTLS requires a valid SPIFFE X.509 client certificate.",
                    SCHEME);
            }
            if (!(peerCerts[0] instanceof X509Certificate cert)) {
                throw new A2AAuthenticationException(
                    "Client certificate is not an X.509 certificate.", SCHEME);
            }
            return cert;
        } catch (javax.net.ssl.SSLPeerUnverifiedException e) {
            throw new A2AAuthenticationException(
                "Client certificate verification failed. "
                + "Ensure the certificate was issued by a trusted CA.",
                SCHEME, e);
        }
    }

    private String extractSpiffeId(X509Certificate cert)
            throws A2AAuthenticationException {
        try {
            Collection<List<?>> sanExtension = cert.getSubjectAlternativeNames();
            if (sanExtension == null) {
                throw new A2AAuthenticationException(
                    "Client certificate has no Subject Alternative Name extension. "
                    + "SPIFFE X.509 SVIDs must contain a URI SAN with the SPIFFE ID.",
                    SCHEME);
            }

            List<String> uris = new ArrayList<>();
            for (List<?> san : sanExtension) {
                if (san.size() >= 2
                        && san.get(0) instanceof Integer type
                        && type == SAN_URI_TYPE
                        && san.get(1) instanceof String uri) {
                    if (uri.startsWith(SPIFFE_SCHEME)) {
                        uris.add(uri);
                    }
                }
            }

            if (uris.isEmpty()) {
                throw new A2AAuthenticationException(
                    "Client certificate contains no SPIFFE URI in Subject Alternative Names. "
                    + "Expected format: spiffe://<trust-domain>/<path>",
                    SCHEME);
            }

            if (uris.size() > 1) {
                throw new A2AAuthenticationException(
                    "Client certificate contains multiple SPIFFE URIs. "
                    + "A valid SVID must contain exactly one SPIFFE ID.",
                    SCHEME);
            }

            return uris.get(0);

        } catch (CertificateParsingException e) {
            throw new A2AAuthenticationException(
                "Failed to parse Subject Alternative Names from client certificate.",
                SCHEME, e);
        }
    }

    private void validateTrustDomain(String spiffeId)
            throws A2AAuthenticationException {
        // spiffeId = spiffe://<trust-domain>/<path>
        String withoutScheme = spiffeId.substring(SPIFFE_SCHEME.length());
        int slashIndex = withoutScheme.indexOf('/');
        String domain = slashIndex >= 0
            ? withoutScheme.substring(0, slashIndex)
            : withoutScheme;

        if (!trustedDomain.equals(domain)) {
            throw new A2AAuthenticationException(
                "SPIFFE ID trust domain '" + domain + "' is not trusted by this server. "
                + "Expected trust domain: " + trustedDomain,
                SCHEME);
        }
    }

    private String extractWorkloadPath(String spiffeId) {
        String withoutScheme = spiffeId.substring(SPIFFE_SCHEME.length());
        int slashIndex = withoutScheme.indexOf('/');
        return slashIndex >= 0 ? withoutScheme.substring(slashIndex) : "/";
    }

    /**
     * Match the workload path against the configured prefix-based policy table.
     * The longest matching prefix wins. When no prefix matches, an empty
     * permission set is returned (deny all).
     */
    private Set<String> resolvePermissions(String workloadPath) {
        String bestPrefix = null;
        for (String prefix : pathPermissions.keySet()) {
            if (workloadPath.startsWith(prefix)) {
                if (bestPrefix == null || prefix.length() > bestPrefix.length()) {
                    bestPrefix = prefix;
                }
            }
        }
        if (bestPrefix == null) {
            return Set.of();
        }
        return pathPermissions.get(bestPrefix);
    }
}
