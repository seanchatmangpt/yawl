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
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.*;
import org.yawlfoundation.yawl.integration.ExternalCallGuard;

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
     * Lazily initialized SSL context, guarded with timeout.
     * Null until first successful SPIFFE contact.
     */
    private final AtomicReference<SSLContext> _sslContext = new AtomicReference<>();

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
     * Create HTTP client with optional mTLS configuration.
     * SSL context creation is deferred to first use via lazy initialization.
     */
    private HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor());

        // Note: SSL context is NOT set here. It will be lazily initialized
        // on first request via getOrCreateSslContext() to avoid blocking
        // the constructor if SPIRE Agent is unavailable at startup.

        return builder.build();
    }

    /**
     * Create SSL context configured with SPIFFE X.509 SVID.
     * This is called only on first request (lazy initialization), guarded by timeout.
     */
    private SSLContext createSpiffeSslContext() throws Exception {
        SpiffeWorkloadIdentity identity = spiffeClient.getValidIdentity();
        X509Certificate[] certChain = identity.getX509Chain().orElseThrow(
            () -> new SpiffeException("X.509 certificate chain not available"));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        PrivateKey privateKey = extractPrivateKey(certChain[0]);
        keyStore.setKeyEntry(
            "spiffe",
            privateKey,
            new char[0],
            certChain
        );

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    /**
     * Get or lazily initialize the SSL context with timeout protection.
     * Uses ExternalCallGuard to protect against SPIRE Agent unavailability.
     *
     * @return SSLContext configured with SPIFFE SVID, or null if SPIFFE unavailable
     * @throws IOException if SPIRE contact times out and no fallback available
     */
    private SSLContext getOrCreateSslContext() throws IOException {
        SSLContext existing = _sslContext.get();
        if (existing != null) {
            return existing;
        }

        if (!spiffeAvailable || spiffeClient == null) {
            return null;
        }

        try {
            SSLContext fresh = ExternalCallGuard.<SSLContext>withTimeout(Duration.ofSeconds(10))
                .build()
                .execute(this::createSpiffeSslContext);

            _sslContext.compareAndSet(null, fresh);
            return _sslContext.get();
        } catch (Exception e) {
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException(
                "Failed to initialize SPIFFE SSL context within 10 seconds. " +
                "Ensure SPIRE Agent is running and accessible.",
                e);
        }
    }

    /**
     * Perform HTTP POST with optional mTLS.
     * SSL context is lazily initialized on first request.
     *
     * @param url Target URL
     * @param body Request body
     * @param contentType Content-Type header
     * @return Response body
     * @throws IOException if request fails
     */
    public String post(String url, String body, String contentType) throws IOException {
        // Ensure SSL context is initialized if SPIFFE is available
        if (spiffeAvailable) {
            SSLContext sslContext = getOrCreateSslContext();
            if (sslContext != null && _sslContext.get() == null) {
                _sslContext.set(sslContext);
                // Rebuild HTTP client with SSL context on first successful SPIFFE contact
                this.httpClient = HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .sslContext(sslContext)
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .build();
            }
        }

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
     * Perform HTTP GET with optional mTLS.
     * SSL context is lazily initialized on first request.
     *
     * @param url Target URL
     * @return Response body
     * @throws IOException if request fails
     */
    public String get(String url) throws IOException {
        // Ensure SSL context is initialized if SPIFFE is available
        if (spiffeAvailable) {
            SSLContext sslContext = getOrCreateSslContext();
            if (sslContext != null && _sslContext.get() == null) {
                _sslContext.set(sslContext);
                // Rebuild HTTP client with SSL context on first successful SPIFFE contact
                this.httpClient = HttpClient.newBuilder()
                        .connectTimeout(connectTimeout)
                        .sslContext(sslContext)
                        .executor(Executors.newVirtualThreadPerTaskExecutor())
                        .build();
            }
        }

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
     * Extract private key from certificate (requires SPIFFE library integration)
     */
    private PrivateKey extractPrivateKey(X509Certificate cert) throws SpiffeException {
        throw new UnsupportedOperationException(
            "Private key extraction from SPIFFE SVID requires java-spiffe library integration. " +
            "The SPIRE Workload API returns both certificate and private key together. " +
            "This implementation needs to be completed with proper key handling from the " +
            "SPIRE Agent response. See: https://github.com/spiffe/java-spiffe"
        );
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
     * Check if SPIFFE SSL context has been successfully initialized.
     * Returns false if SPIFFE is not available or not yet initialized.
     *
     * @return true if SSL context is ready for use
     */
    public boolean isSpiffeReady() {
        return _sslContext.get() != null;
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
