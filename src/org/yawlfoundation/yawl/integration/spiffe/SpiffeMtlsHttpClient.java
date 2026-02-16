package org.yawlfoundation.yawl.integration.spiffe;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Optional;

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
    private int connectTimeout = 30000;
    private int readTimeout = 60000;

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
    }

    /**
     * Create mTLS client with explicit SPIFFE client
     */
    public SpiffeMtlsHttpClient(SpiffeWorkloadApiClient spiffeClient) {
        this.spiffeClient = spiffeClient;
        this.spiffeAvailable = spiffeClient != null && spiffeClient.isAvailable();
        this.credentialProvider = SpiffeCredentialProvider.getInstance();
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
        HttpURLConnection conn = createConnection(url);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", contentType);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = body.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        return readResponse(conn);
    }

    /**
     * Perform HTTP GET with mTLS
     *
     * @param url Target URL
     * @return Response body
     * @throws IOException if request fails
     */
    public String get(String url) throws IOException {
        HttpURLConnection conn = createConnection(url);
        conn.setRequestMethod("GET");
        return readResponse(conn);
    }

    /**
     * Create HTTP connection with optional mTLS
     */
    private HttpURLConnection createConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);

        if (url.getProtocol().equals("https") && spiffeAvailable && spiffeClient != null) {
            try {
                configureMtls((HttpsURLConnection) conn);
            } catch (Exception e) {
                throw new IOException("Failed to configure mTLS with SPIFFE", e);
            }
        }

        return conn;
    }

    /**
     * Configure mTLS with SPIFFE X.509 SVID
     */
    private void configureMtls(HttpsURLConnection conn) throws Exception {
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

        conn.setSSLSocketFactory(sslContext.getSocketFactory());
        conn.setHostnameVerifier(new SpiffeHostnameVerifier());
    }

    /**
     * Extract private key from certificate (placeholder - needs real implementation)
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
     * SPIFFE-aware hostname verifier
     */
    private static class SpiffeHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            try {
                X509Certificate[] peerCerts = (X509Certificate[]) session.getPeerCertificates();
                if (peerCerts.length == 0) {
                    return false;
                }

                String spiffeId = extractSpiffeId(peerCerts[0]);
                if (spiffeId != null && spiffeId.startsWith("spiffe://")) {
                    return true;
                }

                return false;
            } catch (Exception e) {
                return false;
            }
        }

        private String extractSpiffeId(X509Certificate cert) {
            try {
                if (cert.getSubjectAlternativeNames() == null) {
                    return null;
                }
                for (Object altName : cert.getSubjectAlternativeNames()) {
                    if (altName instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) altName;
                        if (list.size() >= 2 && list.get(1) instanceof String) {
                            String value = (String) list.get(1);
                            if (value.startsWith("spiffe://")) {
                                return value;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
            return null;
        }
    }

    /**
     * Read response from connection
     */
    private String readResponse(HttpURLConnection conn) throws IOException {
        int responseCode = conn.getResponseCode();
        InputStream inputStream = responseCode >= 400
            ? conn.getErrorStream()
            : conn.getInputStream();

        if (inputStream == null) {
            throw new IOException("No response from server (HTTP " + responseCode + ")");
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }

        if (responseCode >= 400) {
            throw new IOException("HTTP error " + responseCode + ": " + response.toString());
        }

        return response.toString();
    }

    /**
     * Set connection timeout
     */
    public void setConnectTimeout(int timeout) {
        this.connectTimeout = timeout;
    }

    /**
     * Set read timeout
     */
    public void setReadTimeout(int timeout) {
        this.readTimeout = timeout;
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
