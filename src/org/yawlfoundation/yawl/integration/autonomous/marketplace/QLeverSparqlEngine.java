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

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

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
 * Read-only {@link SparqlEngine} backed by a remote QLever endpoint.
 *
 * <p>QLever is a high-performance SPARQL engine capable of sub-100ms CONSTRUCT queries
 * at 1 billion triples. This wrapper targets a pre-running QLever instance; it does
 * not start or manage the QLever process. Data must be pre-loaded into QLever via its
 * own indexing pipeline.</p>
 *
 * <p>Unlike {@link OxigraphSparqlEngine}, this engine is <em>read-only</em>: no
 * load or update operations are provided, because QLever requires offline index
 * construction.</p>
 *
 * <p>The {@link #isAvailable()} check pings {@code GET /api/} and returns {@code false}
 * on connection refused — it never throws.</p>
 *
 * @since YAWL 6.0
 */
public final class QLeverSparqlEngine implements SparqlEngine {

    /** Default QLever endpoint URL. */
    public static final String DEFAULT_BASE_URL = "http://localhost:7001";

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final HttpClient http;

    /**
     * Creates an engine pointing at the default QLever URL ({@value DEFAULT_BASE_URL}).
     */
    public QLeverSparqlEngine() {
        this(DEFAULT_BASE_URL);
    }

    /**
     * Creates an engine pointing at the given QLever base URL.
     *
     * @param baseUrl base URL of the QLever endpoint
     */
    public QLeverSparqlEngine(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null")
                              .replaceAll("/+$", "");
        this.http = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    @Override
    public String constructToTurtle(String constructQuery) throws SparqlEngineException {
        Objects.requireNonNull(constructQuery, "constructQuery must not be null");
        if (!isAvailable()) {
            throw new SparqlEngineUnavailableException(engineType(), baseUrl);
        }
        // QLever accepts queries via POST /api/query with body query=<urlencoded>
        String encodedQuery = URLEncoder.encode(constructQuery, StandardCharsets.UTF_8);
        String body = "query=" + encodedQuery;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/query"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/turtle")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(DEFAULT_TIMEOUT)
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new SparqlEngineException(
                        "QLever CONSTRUCT failed with HTTP " + resp.statusCode()
                        + ": " + resp.body());
            }
            return resp.body();
        } catch (ConnectException e) {
            throw new SparqlEngineUnavailableException(
                    "QLever endpoint unreachable at " + baseUrl, e);
        } catch (IOException e) {
            throw new SparqlEngineException("QLever I/O error: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SparqlEngineException("QLever query interrupted", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() / 100 == 2;
        } catch (ConnectException e) {
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public String engineType() {
        return "qlever";
    }
}
