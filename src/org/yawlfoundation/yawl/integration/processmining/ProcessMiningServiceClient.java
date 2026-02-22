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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * HTTP client implementation of ProcessMiningService targeting rust4pm or pm4py.
 *
 * Communicates with an external process mining microservice via HTTP POST requests.
 * Handles JSON request/response serialization and provides clean error messages
 * with status codes and response bodies for debugging.
 *
 * Request payloads are large (MB-scale XES/PNML files), so proper JSON string
 * escaping is applied to avoid injection vulnerabilities.
 *
 * Supports configurable base URL and timeout. Uses virtual threads for efficient
 * network I/O handling.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ProcessMiningServiceClient implements ProcessMiningService {

    private static final Logger logger = LogManager.getLogger(ProcessMiningServiceClient.class);

    private final String baseUrl;
    private final HttpClient httpClient;
    private final Duration timeout;

    /**
     * Create a client targeting a process mining service at the specified URL.
     *
     * Uses a default timeout of 30 seconds for all requests.
     *
     * @param baseUrl the base URL of the process mining service (e.g., "http://localhost:8082")
     *                Must not include a trailing slash.
     */
    public ProcessMiningServiceClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(30));
    }

    /**
     * Create a client with a custom timeout.
     *
     * @param baseUrl the base URL of the process mining service (e.g., "http://localhost:8082")
     *                Must not include a trailing slash.
     * @param timeout duration to wait for each request to complete
     */
    public ProcessMiningServiceClient(String baseUrl, Duration timeout) {
        this.baseUrl = baseUrl != null ? baseUrl : "http://localhost:8082";
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    @Override
    public String tokenReplay(String pnmlXml, String xesXml) throws IOException {
        return postJson("/conformance/token-replay", pnmlXml, xesXml, "pnml", "xes");
    }

    @Override
    public String discoverDfg(String xesXml) throws IOException {
        return postJson("/discovery/dfg", xesXml, null, "xes", null);
    }

    @Override
    public String discoverAlphaPpp(String xesXml) throws IOException {
        return postJson("/discovery/alpha-ppp", xesXml, null, "xes", null);
    }

    @Override
    public String performanceAnalysis(String xesXml) throws IOException {
        return postJson("/analysis/performance", xesXml, null, "xes", null);
    }

    @Override
    public String xesToOcel(String xesXml) throws IOException {
        return postJson("/ocel/convert", xesXml, null, "xes", null);
    }

    @Override
    public boolean isHealthy() {
        try {
            String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "health"))
                    .timeout(timeout)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            boolean isHealthy = response.statusCode() >= 200 && response.statusCode() < 300;
            if (!isHealthy) {
                logger.debug("Process mining service health check failed with status {}", response.statusCode());
            }
            return isHealthy;
        } catch (IOException ioe) {
            logger.debug("Process mining service health check failed: {}", ioe.getMessage());
            return false;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.debug("Process mining service health check interrupted");
            return false;
        }
    }

    /**
     * POST a JSON request to the service with two payload fields.
     *
     * Constructs a JSON request body with properly escaped payloads and sends it
     * to the specified endpoint. Throws IOException if the service returns a non-2xx
     * status code.
     *
     * @param endpoint the API endpoint (e.g., "/conformance/token-replay")
     * @param payload1 first XML/payload content (may be null)
     * @param payload2 second XML/payload content (may be null)
     * @param field1Name name of first field in JSON request
     * @param field2Name name of second field in JSON request (may be null)
     * @return response body as string (JSON or XML)
     * @throws IOException if request fails or service returns non-2xx status
     */
    private String postJson(String endpoint, String payload1, String payload2,
                            String field1Name, String field2Name) throws IOException {
        try {
            String json = buildJsonRequest(payload1, payload2, field1Name, field2Name);
            String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + endpoint.substring(1))) // Remove leading slash, add via URL
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String errorMsg = String.format(
                        "Process mining service error (HTTP %d): %s",
                        response.statusCode(),
                        truncateForLog(response.body(), 500)
                );
                logger.error("Request to {} failed: {}", endpoint, errorMsg);
                throw new IOException(errorMsg);
            }

            return response.body();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Request to " + endpoint + " was interrupted", ie);
        }
    }

    /**
     * Build a JSON request body with escaped payloads.
     *
     * @param payload1 first XML/payload (must not be null)
     * @param payload2 second XML/payload (may be null)
     * @param field1Name first field name
     * @param field2Name second field name (may be null if payload2 is null)
     * @return JSON string
     * @throws IllegalArgumentException if payload1 is null
     */
    private String buildJsonRequest(String payload1, String payload2,
                                    String field1Name, String field2Name) {
        if (payload1 == null) {
            throw new IllegalArgumentException("First payload must not be null");
        }

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"").append(field1Name).append("\":\"")
                .append(escapeJson(payload1)).append("\"");

        if (payload2 != null) {
            if (field2Name == null) {
                throw new IllegalArgumentException("Field2Name must not be null when payload2 is present");
            }
            sb.append(",");
            sb.append("\"").append(field2Name).append("\":\"")
                    .append(escapeJson(payload2)).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Escape a string for safe inclusion in a JSON string literal.
     *
     * Handles backslashes, quotes, newlines, carriage returns, and tabs.
     * This prevents JSON injection vulnerabilities when embedding large XML payloads.
     *
     * @param s the string to escape (must not be null)
     * @return escaped string safe for JSON
     * @throws IllegalArgumentException if s is null
     */
    private static String escapeJson(String s) {
        if (s == null) {
            throw new IllegalArgumentException("String to escape must not be null");
        }
        StringBuilder sb = new StringBuilder(s.length() + 100);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    // Control characters (0x00-0x1F excluding those handled above)
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * Truncate a string for log output to avoid overwhelming logs with large payloads.
     *
     * @param s the string to truncate
     * @param maxLength maximum length before truncation
     * @return truncated string with ellipsis if needed
     */
    private static String truncateForLog(String s, int maxLength) {
        if (s == null) {
            return "(null)";
        }
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength) + "... (truncated)";
    }
}
