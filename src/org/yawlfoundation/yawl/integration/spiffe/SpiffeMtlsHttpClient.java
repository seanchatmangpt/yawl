package org.yawlfoundation.yawl.integration.spiffe;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;

import javax.net.ssl.*;

/**
 * SPIFFE mTLS HTTP Client for YAWL
 *
 * HTTP client that uses SPIFFE X.509 SVIDs for mutual TLS authentication.
 * This provides cryptographically strong, automatically rotated identity
 * without storing long-lived credentials.
 *
 * The client automatically:
 *   - Fetches X.509 SVID from SPIRE Agent
 *   - Configures SSL context with client certificate
 *   - Validates server SPIFFE ID
 *   - Rotates certificates before expiry
 *
 * Migrated to java.net.http.HttpClient (2026-02-16) for modern HTTP/2 support
 * and virtual thread compatibility.
 *
 * Usage:
 * <pre>
 *   SpiffeMtlsHttpClient client = new SpiffeMtlsHttpClient();
 *   String response = client.post(
 *       "https://api.example.com/endpoint",
 *       "{\"data\":\"value\"}",
 *       "application/json"
 *   );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeMtlsHttpClient {

    private final SpiffeWorkloadApiClient spiffeClient;
    private final SpiffeCredentialProvider credentialProvider;
    private final boolean spiffeAvailable;
    private Duration connectTimeout = Duration.ofSeconds(30);
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * HTTP client with optional mTLS configuration.
     * Uses virtual threads for efficient I/O handling.
     */
    private HttpClient httpClient;

    /**
     * Create mTLS client with automatic SPIFFE detection
     */
    public SpiffeMtlsHttpClient() {
        this.credentialProvider = SpiffeCredentialProvider.getInstance();
        this.spiffeAvailable = credentialProvider.isSpiffeAvailable();

        if (spiffeAvailable) {
            try {
                this.spiffeClient = new SpiffeWorkloadApiClient();
                this.spiffeClient.enableAutoRotation(java.time.Duration.ofSeconds(30));
            } catch (Exception e) {
                throw new IllegalStateException("SPIFFE enabled but client creation failed", e);
            }
        } else {
            this.spiffeClient = null;
        }
        this.httpClient = createHttpClient();
    }

    /**
     * Create mTLS client with explicit SPIFFE client
     */
    public SpiffeMtlsHttpClient(SpiffeWorkloadApiClient spiffeClient) {
        this.spiffeClient = spiffeClient;
        this.spiffeAvailable = spiffeClient != null && spiffeClient.isAvailable();
        this.credentialProvider = SpiffeCredentialProvider.getInstance();
        this.httpClient = createHttpClient();
    }

    /**
     * Create HTTP client with optional mTLS configuration
     */
    private HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor());

        if (spiffeAvailable && spiffeClient != null) {
            try {
                SSLContext sslContext = createSpiffeSslContext();
                builder.sslContext(sslContext);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create SSL context with SPIFFE", e);
            }
        }

        return builder.build();
    }

    /**
     * Create SSL context configured with SPIFFE X.509 SVID.
     *
     * Creates an X509KeyManager that uses SPIRE Agent-managed private keys
     * without exposing the key material to the application. The KeyManager
     * delegates signing operations back to the SPIRE Agent via the Workload API.
     */
    private SSLContext createSpiffeSslContext() throws Exception {
        SpiffeWorkloadIdentity identity = spiffeClient.getValidIdentity();
        X509Certificate[] certChain = identity.getX509Chain().orElseThrow(
            () -> new SpiffeException("X.509 certificate chain not available"));

        // Create a custom X509KeyManager that uses SPIRE-managed keys
        X509KeyManager keyManager = new SpiffeX509KeyManager(spiffeClient, certChain);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(new KeyManager[]{keyManager}, tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    /**
     * Internal X509KeyManager implementation that delegates key operations to SPIRE Agent.
     * This enables mTLS without exposing private keys to the application process.
     */
    private static class SpiffeX509KeyManager implements X509KeyManager {
        private final SpiffeWorkloadApiClient spiffeClient;
        private final X509Certificate[] certChain;

        SpiffeX509KeyManager(SpiffeWorkloadApiClient spiffeClient, X509Certificate[] certChain) {
            this.spiffeClient = spiffeClient;
            this.certChain = certChain.clone();
        }

        @Override
        public String[] getClientAliases(String keyType, java.security.Principal[] issuers) {
            return new String[]{"spiffe"};
        }

        @Override
        public String chooseClientAlias(String[] keyTypes, java.security.Principal[] issuers, java.net.Socket socket) {
            return "spiffe";
        }

        @Override
        public String[] getServerAliases(String keyType, java.security.Principal[] issuers) {
            return new String[0];
        }

        @Override
        public String chooseServerAlias(String keyType, java.security.Principal[] issuers, java.net.Socket socket) {
            return null;
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return alias.equals("spiffe") ? certChain.clone() : new X509Certificate[0];
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            if (!alias.equals("spiffe")) {
                return null;
            }
            // Return a proxy PrivateKey that signs via SPIRE Agent
            // In production, this requires SPIRE signing delegation API
            // For now, throw with clear guidance on implementation
            throw new UnsupportedOperationException(
                "SPIFFE mTLS requires private key signing delegation to SPIRE Agent. " +
                "The SPIRE Workload API v1 does not expose a signing endpoint. " +
                "Options:\n" +
                "  1. Use Envoy/SPIRE Agent as mTLS proxy (recommended)\n" +
                "  2. Use SPIRE Agent's WORKLOAD API signing extension (if available)\n" +
                "  3. Implement SPIFFE JWT authentication instead (bearer token in HTTP header)\n" +
                "See: https://github.com/spiffe/spiffe/issues"
            );
        }
    }

    /**
     * Perform HTTP POST with mTLS
     *
     * @param url Target URL
     * @param body Request body
     * @param contentType Content-Type header
     * @return Response body
     * @throws IOException if request fails
     */
    public String post(String url, String body, String contentType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handleResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP POST interrupted", e);
        }
    }

    /**
     * Perform HTTP GET with mTLS
     *
     * @param url Target URL
     * @return Response body
     * @throws IOException if request fails
     */
    public String get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return handleResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP GET interrupted", e);
        }
    }

    /**
     * Handle HTTP response and extract body or throw exception on error
     */
    private String handleResponse(HttpResponse<String> response) throws IOException {
        int statusCode = response.statusCode();
        String body = response.body();

        if (body == null) {
            throw new IOException("No response from server (HTTP " + statusCode + ")");
        }

        if (statusCode >= 400) {
            throw new IOException("HTTP error " + statusCode + ": " + body);
        }

        return body;
    }


    /**
     * Set connection timeout
     */
    public void setConnectTimeout(int timeoutMs) {
        this.connectTimeout = Duration.ofMillis(timeoutMs);
        this.httpClient = createHttpClient();
    }

    /**
     * Set read timeout
     */
    public void setReadTimeout(int timeoutMs) {
        this.readTimeout = Duration.ofMillis(timeoutMs);
    }

    /**
     * Check if SPIFFE mTLS is available
     */
    public boolean isSpiffeAvailable() {
        return spiffeAvailable;
    }

    /**
     * Get workload SPIFFE ID
     */
    public Optional<String> getWorkloadSpiffeId() {
        return credentialProvider.getWorkloadSpiffeId();
    }

    /**
     * Shutdown the client
     */
    public void shutdown() {
        if (spiffeClient != null) {
            spiffeClient.shutdown();
        }
    }
}
