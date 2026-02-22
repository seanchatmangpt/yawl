/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for Rust4PM process mining service.
 * Submits XES logs and OCEL2 event logs to Rust4PM server for analysis and discovery.
 * Wraps rust-pm crate (crates.io) via HTTP REST endpoints.
 *
 * Endpoints exposed by Rust4PM server:
 * - POST /api/v1/xes/analyze - submit XES XML, returns JSON analysis
 * - POST /api/v1/ocel2/analyze - submit OCEL2 JSON, returns JSON analysis
 * - POST /api/v1/discover - submit XES, returns discovered process model JSON
 * - GET /api/v1/health - health check
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class Rust4PmClient implements AutoCloseable {

    private final String baseUrl;
    private final HttpClient httpClient;

    /**
     * Create client for Rust4PM server at baseUrl.
     *
     * @param baseUrl Rust4PM server base URL (e.g., http://localhost:8080)
     * @throws IllegalArgumentException if baseUrl is null or empty
     */
    public Rust4PmClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Create client from RUST4PM_SERVER_URL environment variable.
     * Defaults to http://localhost:8080 if not set.
     *
     * @return new Rust4PmClient instance
     */
    public static Rust4PmClient fromEnvironment() {
        String url = System.getenv("RUST4PM_SERVER_URL");
        if (url == null || url.isEmpty()) {
            url = "http://localhost:8080";
        }
        return new Rust4PmClient(url);
    }

    /**
     * Submit XES event log for analysis.
     * POST /api/v1/xes/analyze with XES XML body.
     *
     * @param xesXml XES XML string
     * @return JSON response containing analysis results
     * @throws IOException if request fails or server returns error
     */
    public String analyzeXes(String xesXml) throws IOException {
        return postJson("/api/v1/xes/analyze", xesXml);
    }

    /**
     * Submit OCEL2 event log for analysis.
     * POST /api/v1/ocel2/analyze with OCEL2 JSON body.
     *
     * @param ocel2Json OCEL2 JSON string
     * @return JSON response containing analysis results
     * @throws IOException if request fails or server returns error
     */
    public String analyzeOcel2(String ocel2Json) throws IOException {
        return postJson("/api/v1/ocel2/analyze", ocel2Json);
    }

    /**
     * Discover process model from XES event log.
     * POST /api/v1/discover with XES XML body.
     * Returns discovered Petri net or process tree as JSON.
     *
     * @param xesXml XES XML string
     * @return JSON response containing discovered process model
     * @throws IOException if request fails or server returns error
     */
    public String discoverProcess(String xesXml) throws IOException {
        return postJson("/api/v1/discover", xesXml);
    }

    /**
     * Check if Rust4PM server is healthy and reachable.
     * GET /api/v1/health.
     *
     * @return true if server responds with 200 OK, false otherwise
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Post JSON content to endpoint and return response.
     * Private helper for REST POST operations.
     *
     * @param endpoint API endpoint path (e.g., /api/v1/xes/analyze)
     * @param content request body (XML or JSON string)
     * @return response body as string
     * @throws IOException if request fails or server returns 4xx/5xx
     */
    private String postJson(String endpoint, String content) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(content))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                throw new IOException("Rust4PM HTTP " + response.statusCode() + ": " + response.body());
            }

            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Rust4PM request interrupted", e);
        }
    }

    /**
     * Close client resources (no-op; HttpClient manages its own cleanup).
     */
    @Override
    public void close() {
        // HttpClient is auto-managed; no explicit cleanup needed
    }
}
