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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Unified HTTP client for all Java-to-Rust calls against the {@code yawl-native} service.
 *
 * <p>The {@code yawl-native} Rust service (default port 8083) exposes two categories
 * of endpoints:</p>
 * <ul>
 *   <li><b>Process mining</b> — {@code /conformance}, {@code /discovery}, {@code /analysis}</li>
 *   <li><b>SPARQL (Oxigraph)</b> — {@code /sparql/*}</li>
 * </ul>
 *
 * <p>All network failures and non-2xx HTTP responses are surfaced as
 * {@link YawlNativeException}. The {@code isAvailable()} and
 * {@code isSparqlAvailable()} convenience methods catch all exceptions and return
 * {@code false} — callers can use them as a liveness gate without try/catch.</p>
 *
 * <p>This client is thread-safe. A single instance can be shared across threads.</p>
 *
 * @since YAWL 6.0
 */
public final class YawlNativeClient {

    /** Default base URL when none is specified. */
    public static final String DEFAULT_BASE_URL = "http://localhost:8083";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final HttpClient http;

    /**
     * Creates a client pointing at the default yawl-native URL ({@value DEFAULT_BASE_URL}).
     */
    public YawlNativeClient() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Creates a client pointing at the given base URL.
     *
     * @param baseUrl base URL of the yawl-native service, e.g. {@code "http://localhost:8083"}
     */
    public YawlNativeClient(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null")
                              .replaceAll("/+$", ""); // strip trailing slashes
        this.http = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    // -----------------------------------------------------------------------
    // Health
    // -----------------------------------------------------------------------

    /**
     * Returns true if the yawl-native service is reachable ({@code GET /health} returns 2xx).
     *
     * <p>Never throws. Returns false on any network or HTTP error.</p>
     */
    public boolean isAvailable() {
        try {
            HttpRequest req = get("/health");
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() / 100 == 2;
        } catch (ConnectException e) {
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // SPARQL (Oxigraph)
    // -----------------------------------------------------------------------

    /**
     * Returns true if the SPARQL endpoint is reachable ({@code GET /sparql/health} returns 2xx).
     *
     * <p>Never throws. Returns false on any network or HTTP error.</p>
     */
    public boolean isSparqlAvailable() {
        try {
            HttpRequest req = get("/sparql/health");
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() / 100 == 2;
        } catch (ConnectException e) {
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Execute a SPARQL CONSTRUCT query and return the result as a Turtle string.
     *
     * @param sparqlQuery a valid SPARQL 1.1 CONSTRUCT query
     * @return Turtle-serialised result graph
     * @throws YawlNativeException on network failure or non-2xx response
     */
    public String constructToTurtle(String sparqlQuery) throws YawlNativeException {
        Objects.requireNonNull(sparqlQuery, "sparqlQuery must not be null");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sparql/query"))
                .header("Content-Type", "application/sparql-query")
                .header("Accept", "text/turtle")
                .POST(HttpRequest.BodyPublishers.ofString(sparqlQuery, StandardCharsets.UTF_8))
                .timeout(DEFAULT_TIMEOUT)
                .build();
        return sendForString(req, "CONSTRUCT query");
    }

    /**
     * Load Turtle RDF into the default graph.
     *
     * @param turtle valid Turtle RDF string
     * @throws YawlNativeException on network failure or non-2xx response
     */
    public void loadTurtle(String turtle) throws YawlNativeException {
        loadTurtle(turtle, null);
    }

    /**
     * Load Turtle RDF into a named graph (or the default graph if {@code graphName} is null).
     *
     * @param turtle     valid Turtle RDF string
     * @param graphName  IRI of the target named graph, or null for the default graph
     * @throws YawlNativeException on network failure or non-2xx response
     */
    public void loadTurtle(String turtle, String graphName) throws YawlNativeException {
        Objects.requireNonNull(turtle, "turtle must not be null");
        String path = "/sparql/load";
        if (graphName != null && !graphName.isBlank()) {
            path += "?graph=" + URLEncoder.encode(graphName, StandardCharsets.UTF_8);
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "text/turtle")
                .POST(HttpRequest.BodyPublishers.ofString(turtle, StandardCharsets.UTF_8))
                .timeout(DEFAULT_TIMEOUT)
                .build();
        sendExpecting204(req, "load Turtle");
    }

    /**
     * Execute a SPARQL 1.1 Update (INSERT DATA, DELETE DATA, CLEAR, etc.).
     *
     * @param updateQuery a valid SPARQL 1.1 Update string
     * @throws YawlNativeException on network failure or non-2xx response
     */
    public void sparqlUpdate(String updateQuery) throws YawlNativeException {
        Objects.requireNonNull(updateQuery, "updateQuery must not be null");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sparql/update"))
                .header("Content-Type", "application/sparql-update")
                .POST(HttpRequest.BodyPublishers.ofString(updateQuery, StandardCharsets.UTF_8))
                .timeout(DEFAULT_TIMEOUT)
                .build();
        sendExpecting204(req, "SPARQL update");
    }

    // -----------------------------------------------------------------------
    // Process mining
    // -----------------------------------------------------------------------

    /**
     * Run token replay conformance checking on an XES event log.
     *
     * @param xesJson JSON-encoded XES event log
     * @return JSON conformance result
     * @throws YawlNativeException on network failure or non-2xx response
     */
    public String tokenReplay(String xesJson) throws YawlNativeException {
        Objects.requireNonNull(xesJson, "xesJson must not be null");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/conformance/token-replay"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(xesJson, StandardCharsets.UTF_8))
                .timeout(DEFAULT_TIMEOUT)
                .build();
        return sendForString(req, "token replay");
    }

    /**
     * Discover a directly-follows graph from an XES event log.
     *
     * @param xesJson JSON-encoded XES event log
     * @return JSON DFG result
     * @throws YawlNativeException on network failure or non-2xx response
     */
    public String discoverDfg(String xesJson) throws YawlNativeException {
        Objects.requireNonNull(xesJson, "xesJson must not be null");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/discovery/dfg"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(xesJson, StandardCharsets.UTF_8))
                .timeout(DEFAULT_TIMEOUT)
                .build();
        return sendForString(req, "discover DFG");
    }

    // -----------------------------------------------------------------------
    // Internal HTTP helpers
    // -----------------------------------------------------------------------

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .timeout(DEFAULT_TIMEOUT)
                .build();
    }

    private String sendForString(HttpRequest req, String opName) throws YawlNativeException {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new YawlNativeException(
                        opName + " failed with HTTP " + resp.statusCode() + ": " + resp.body());
            }
            return resp.body();
        } catch (ConnectException e) {
            throw new YawlNativeException("yawl-native service is unreachable at " + baseUrl, e);
        } catch (IOException e) {
            throw new YawlNativeException(opName + " I/O error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YawlNativeException(opName + " interrupted", e);
        }
    }

    private void sendExpecting204(HttpRequest req, String opName) throws YawlNativeException {
        try {
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() / 100 != 2) {
                throw new YawlNativeException(
                        opName + " failed with HTTP " + resp.statusCode());
            }
        } catch (ConnectException e) {
            throw new YawlNativeException("yawl-native service is unreachable at " + baseUrl, e);
        } catch (IOException e) {
            throw new YawlNativeException(opName + " I/O error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YawlNativeException(opName + " interrupted", e);
        }
    }
}
