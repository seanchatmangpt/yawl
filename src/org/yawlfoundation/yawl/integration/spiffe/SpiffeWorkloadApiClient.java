package org.yawlfoundation.yawl.integration.spiffe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * SPIFFE Workload API Client for YAWL.
 *
 * <p>Connects to the SPIRE Agent via Unix Domain Socket to fetch SVIDs (X.509 and JWT).
 * The SPIRE Agent automatically attests the workload identity based on:
 * <ul>
 *   <li>Process PID and UID</li>
 *   <li>Kubernetes pod/namespace</li>
 *   <li>Docker container</li>
 *   <li>AWS/GCP/Azure instance metadata</li>
 * </ul>
 *
 * <p>This client implements the SPIFFE Workload API specification:
 * <a href="https://github.com/spiffe/spiffe/blob/main/standards/SPIFFE_Workload_API.md">SPIFFE Workload API</a>
 *
 * <p>Default socket: /run/spire/sockets/agent.sock (SPIFFE_ENDPOINT_SOCKET env var)
 *
 * <p>Usage:
 * <pre>{@code
 *   SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
 *   SpiffeWorkloadIdentity identity = client.fetchX509Svid();
 *   System.out.println("My SPIFFE ID: " + identity.getSpiffeId());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
public class SpiffeWorkloadApiClient implements AutoCloseable {

    private static final String DEFAULT_SOCKET_PATH = "/run/spire/sockets/agent.sock";
    private static final String SOCKET_ENV_VAR = "SPIFFE_ENDPOINT_SOCKET";
    private static final Duration DEFAULT_ROTATION_INTERVAL = Duration.ofSeconds(5);
    private static final int SOCKET_BUFFER_SIZE = 8192;

    private final String socketPath;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile SpiffeWorkloadIdentity currentIdentity;
    private final AtomicBoolean autoRotationEnabled = new AtomicBoolean(false);
    private Thread rotationThread;

    /**
     * Create client with default socket path from SPIFFE_ENDPOINT_SOCKET env var
     */
    public SpiffeWorkloadApiClient() {
        String envPath = System.getenv(SOCKET_ENV_VAR);
        if (envPath != null && !envPath.isEmpty()) {
            this.socketPath = envPath.replace("unix://", "");
        } else {
            this.socketPath = DEFAULT_SOCKET_PATH;
        }
        validateSocketPath();
    }

    /**
     * Create client with explicit socket path
     */
    public SpiffeWorkloadApiClient(String socketPath) {
        if (socketPath == null || socketPath.isEmpty()) {
            throw new IllegalArgumentException("Socket path is required");
        }
        this.socketPath = socketPath.replace("unix://", "");
        validateSocketPath();
    }

    /**
     * Fetch X.509 SVID from SPIRE Agent
     *
     * This performs workload attestation and returns a short-lived X.509
     * certificate that proves this workload's identity.
     *
     * @return X.509 SVID with certificate chain
     * @throws SpiffeException if attestation or fetch fails
     */
    public SpiffeWorkloadIdentity fetchX509Svid() throws SpiffeException {
        lock.writeLock().lock();
        try {
            String response = callWorkloadApi("FetchX509SVID", "{}");
            SpiffeWorkloadIdentity identity = parseX509Response(response);
            identity.validate();
            currentIdentity = identity;
            return identity;
        } catch (Exception e) {
            throw new SpiffeException("Failed to fetch X.509 SVID", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Fetch JWT SVID from SPIRE Agent for a specific audience
     *
     * JWT SVIDs are used for API authentication where mTLS is not available.
     * The audience should be the service you're calling.
     *
     * @param audience The intended recipient (e.g., "api.example.com")
     * @return JWT SVID
     * @throws SpiffeException if fetch fails
     */
    public SpiffeWorkloadIdentity fetchJwtSvid(String audience) throws SpiffeException {
        if (audience == null || audience.isEmpty()) {
            throw new IllegalArgumentException("Audience is required for JWT SVID");
        }

        lock.writeLock().lock();
        try {
            String request = String.format("{\"audience\":[\"%s\"]}", escapeJson(audience));
            String response = callWorkloadApi("FetchJWTSVID", request);
            SpiffeWorkloadIdentity identity = parseJwtResponse(response);
            identity.validate();
            return identity;
        } catch (Exception e) {
            throw new SpiffeException("Failed to fetch JWT SVID", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get the current cached identity (may be expired)
     */
    public SpiffeWorkloadIdentity getCurrentIdentity() {
        lock.readLock().lock();
        try {
            return currentIdentity;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get a valid identity, refreshing if needed
     */
    public SpiffeWorkloadIdentity getValidIdentity() throws SpiffeException {
        lock.readLock().lock();
        try {
            if (currentIdentity != null && !currentIdentity.isExpired()) {
                return currentIdentity;
            }
        } finally {
            lock.readLock().unlock();
        }

        return fetchX509Svid();
    }

    /**
     * Enable automatic SVID rotation
     *
     * Starts a background thread that automatically refreshes the SVID before expiration.
     * Recommended for long-running services.
     *
     * @param refreshBeforeExpiry Refresh this duration before expiry (e.g., 30 seconds)
     */
    public void enableAutoRotation(java.time.Duration refreshBeforeExpiry) {
        if (autoRotationEnabled.getAndSet(true)) {
            return;
        }

        rotationThread = Thread.ofVirtual()
            .name("spiffe-auto-rotation")
            .start(() -> {
            while (autoRotationEnabled.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(5000);

                    SpiffeWorkloadIdentity current = getCurrentIdentity();
                    if (current == null || current.willExpireSoon(refreshBeforeExpiry)) {
                        try {
                            fetchX509Svid();
                            System.err.println("SPIFFE: Auto-rotated SVID");
                        } catch (SpiffeException e) {
                            System.err.println("SPIFFE: Auto-rotation failed: " + e.getMessage());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    /**
     * Disable automatic SVID rotation
     */
    public void disableAutoRotation() {
        autoRotationEnabled.set(false);
        if (rotationThread != null) {
            rotationThread.interrupt();
            rotationThread = null;
        }
    }

    /**
     * Check if SPIRE Agent is available
     */
    public boolean isAvailable() {
        try {
            Path path = Paths.get(socketPath);
            return Files.exists(path) && Files.isWritable(path);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate socket path exists and is accessible
     */
    private void validateSocketPath() {
        Path path = Paths.get(socketPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                "SPIRE Agent socket not found at: " + socketPath + "\n" +
                "Ensure SPIRE Agent is running and SPIFFE_ENDPOINT_SOCKET is set correctly.\n" +
                "See: https://spiffe.io/docs/latest/deploying/spire_agent/"
            );
        }
    }

    /**
     * Call the SPIFFE Workload API via Unix Domain Socket
     */
    private String callWorkloadApi(String method, String requestBody) throws IOException {
        SocketAddress address = UnixDomainSocketAddress.of(socketPath);

        try (SocketChannel channel = SocketChannel.open(address)) {
            String httpRequest = buildHttpRequest(method, requestBody);
            ByteBuffer buffer = ByteBuffer.wrap(httpRequest.getBytes(StandardCharsets.UTF_8));
            channel.write(buffer);

            ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
            ByteBuffer readBuffer = ByteBuffer.allocate(8192);

            while (channel.read(readBuffer) > 0) {
                readBuffer.flip();
                byte[] bytes = new byte[readBuffer.remaining()];
                readBuffer.get(bytes);
                responseStream.write(bytes);
                readBuffer.clear();
            }

            String response = responseStream.toString(StandardCharsets.UTF_8);
            return extractHttpBody(response);
        }
    }

    /**
     * Build HTTP request for Workload API
     */
    private String buildHttpRequest(String method, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("POST /").append(method).append(" HTTP/1.1\r\n");
        sb.append("Host: localhost\r\n");
        sb.append("Content-Type: application/json\r\n");
        sb.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
        sb.append("Connection: close\r\n");
        sb.append("\r\n");
        sb.append(body);
        return sb.toString();
    }

    /**
     * Extract HTTP body from response
     */
    private String extractHttpBody(String response) {
        int bodyStart = response.indexOf("\r\n\r\n");
        if (bodyStart == -1) {
            throw new IllegalStateException("Invalid HTTP response: no body separator");
        }
        return response.substring(bodyStart + 4);
    }

    /**
     * Parse X.509 SVID response (simplified JSON parsing)
     */
    private SpiffeWorkloadIdentity parseX509Response(String json) throws Exception {
        String spiffeId = extractJsonString(json, "spiffe_id");
        String x509SvidPem = extractJsonString(json, "x509_svid");
        String x509BundlePem = extractJsonString(json, "x509_svid_bundle");

        List<X509Certificate> certs = parsePemCertificates(x509SvidPem);
        if (certs.isEmpty()) {
            throw new SpiffeException("No certificates in X.509 SVID response");
        }

        X509Certificate[] chain = certs.toArray(new X509Certificate[0]);
        return new SpiffeWorkloadIdentity(spiffeId, chain);
    }

    /**
     * Parse JWT SVID response
     */
    private SpiffeWorkloadIdentity parseJwtResponse(String json) throws Exception {
        String spiffeId = extractJsonString(json, "spiffe_id");
        String token = extractJsonString(json, "token");
        String expiresAtStr = extractJsonString(json, "expires_at");

        Instant expiresAt = Instant.parse(expiresAtStr);
        return new SpiffeWorkloadIdentity(spiffeId, token, expiresAt);
    }

    /**
     * Extract string value from JSON (simple parser, no external dependencies)
     */
    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            throw new IllegalStateException("JSON key not found: " + key);
        }
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            throw new IllegalStateException("Invalid JSON string for key: " + key);
        }
        return json.substring(start, end).replace("\\n", "\n");
    }

    /**
     * Parse PEM-encoded certificates
     */
    private List<X509Certificate> parsePemCertificates(String pem) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        String[] parts = pem.split("-----END CERTIFICATE-----");
        for (String part : parts) {
            if (!part.contains("-----BEGIN CERTIFICATE-----")) {
                continue;
            }

            String certPem = part + "-----END CERTIFICATE-----";
            ByteArrayInputStream bis = new ByteArrayInputStream(
                certPem.getBytes(StandardCharsets.UTF_8));
            X509Certificate cert = (X509Certificate) factory.generateCertificate(bis);
            certs.add(cert);
        }

        return certs;
    }

    /**
     * Escape JSON string. Null values are not allowed in SPIFFE API requests.
     */
    private String escapeJson(String s) {
        if (s == null) {
            throw new IllegalArgumentException("Cannot escape null string for SPIFFE API request");
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Shutdown the client
     */
    public void shutdown() {
        disableAutoRotation();
    }

    /**
     * Close the client (implements AutoCloseable).
     * Equivalent to shutdown().
     */
    @Override
    public void close() {
        shutdown();
    }
}
