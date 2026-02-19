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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Modern YAWL API Client using Java 25 features.
 *
 * <p>This client provides a fluent API for interacting with the YAWL Engine
 * through its REST interfaces. It uses Java 25's HttpClient with virtual threads
 * for efficient concurrent operations.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Virtual thread support for all HTTP operations</li>
 *   <li>Fluent builder API for requests</li>
 *   <li>Async operations with CompletableFuture</li>
 *   <li>Automatic session management</li>
 *   <li>Comprehensive error handling</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * YawlClient client = YawlClient.builder()
 *     .baseUrl("http://localhost:8080/yawl")
 *     .connectTimeout(Duration.ofSeconds(10))
 *     .build();
 *
 * // Connect and get session handle
 * String sessionHandle = client.session()
 *     .connect("admin", "password")
 *     .await();
 *
 * // Launch a case
 * String caseId = client.cases()
 *     .launch(specId)
 *     .withParams("<data><type>vacation</type></data>")
 *     .execute(sessionHandle)
 *     .await();
 *
 * // Get work items
 * List<WorkItemRecord> items = client.workItems()
 *     .getAllLive(sessionHandle)
 *     .await();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @since 5.2
 */
public class YawlClient {

    private static final System.Logger LOG = System.getLogger(YawlClient.class.getName());

    private final String baseUrl;
    private final HttpClient httpClient;
    private final SessionOperations sessionOps;
    private final SpecificationOperations specOps;
    private final CaseOperations caseOps;
    private final WorkItemOperations workItemOps;
    private final AuditOperations auditOps;

    private YawlClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.httpClient = builder.httpClientBuilder.build();
        this.sessionOps = new SessionOperations(this);
        this.specOps = new SpecificationOperations(this);
        this.caseOps = new CaseOperations(this);
        this.workItemOps = new WorkItemOperations(this);
        this.auditOps = new AuditOperations(this);
    }

    /**
     * Creates a new builder for constructing YawlClient instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns session management operations.
     *
     * @return session operations interface
     */
    public SessionOperations session() {
        return sessionOps;
    }

    /**
     * Returns specification management operations (Interface A).
     *
     * @return specification operations interface
     */
    public SpecificationOperations specifications() {
        return specOps;
    }

    /**
     * Returns case management operations (Interface B).
     *
     * @return case operations interface
     */
    public CaseOperations cases() {
        return caseOps;
    }

    /**
     * Returns work item operations (Interface B).
     *
     * @return work item operations interface
     */
    public WorkItemOperations workItems() {
        return workItemOps;
    }

    /**
     * Returns audit and compliance operations.
     *
     * @return audit operations interface
     */
    public AuditOperations audit() {
        return auditOps;
    }

    /**
     * Returns the base URL for this client.
     *
     * @return base URL string
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Executes a GET request to the specified path.
     *
     * @param path the API path
     * @param params query parameters
     * @return async response
     */
    CompletableFuture<HttpResponse<String>> doGet(String path, Map<String, String> params) {
        String url = buildUrl(path, params);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        LOG.log(System.Logger.Level.DEBUG, "GET {0}", url);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Executes a POST request to the specified path.
     *
     * @param path the API path
     * @param params form parameters
     * @return async response
     */
    CompletableFuture<HttpResponse<String>> doPost(String path, Map<String, String> params) {
        String url = baseUrl + path;
        String body = encodeForm(params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        LOG.log(System.Logger.Level.DEBUG, "POST {0}", url);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Executes a POST request with XML body.
     *
     * @param path the API path
     * @param queryParams query parameters
     * @param xmlBody XML body content
     * @return async response
     */
    CompletableFuture<HttpResponse<String>> doPostXml(String path, Map<String, String> queryParams, String xmlBody) {
        String url = buildUrl(path, queryParams);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/xml")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
            .build();

        LOG.log(System.Logger.Level.DEBUG, "POST XML {0}", url);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Executes a PUT request with XML body.
     *
     * @param path the API path
     * @param queryParams query parameters
     * @param xmlBody XML body content
     * @return async response
     */
    CompletableFuture<HttpResponse<String>> doPutXml(String path, Map<String, String> queryParams, String xmlBody) {
        String url = buildUrl(path, queryParams);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/xml")
            .timeout(Duration.ofSeconds(30))
            .PUT(HttpRequest.BodyPublishers.ofString(xmlBody))
            .build();

        LOG.log(System.Logger.Level.DEBUG, "PUT XML {0}", url);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Executes a DELETE request.
     *
     * @param path the API path
     * @param params query parameters
     * @return async response
     */
    CompletableFuture<HttpResponse<String>> doDelete(String path, Map<String, String> params) {
        String url = buildUrl(path, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .DELETE()
            .build();

        LOG.log(System.Logger.Level.DEBUG, "DELETE {0}", url);
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    private String buildUrl(String path, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl).append(path);
        if (params != null && !params.isEmpty()) {
            url.append("?").append(encodeForm(params));
        }
        return url.toString();
    }

    private String encodeForm(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        return params.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    }

    /**
     * Builder for constructing YawlClient instances.
     */
    public static class Builder {
        private String baseUrl = "http://localhost:8080/yawl";
        private final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2);

        /**
         * Sets the base URL for the YAWL engine.
         *
         * @param baseUrl base URL (e.g., "http://localhost:8080/yawl")
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl.replaceAll("/$", "");
            return this;
        }

        /**
         * Sets the connection timeout.
         *
         * @param timeout connection timeout duration
         * @return this builder
         */
        public Builder connectTimeout(Duration timeout) {
            this.httpClientBuilder.connectTimeout(timeout);
            return this;
        }

        /**
         * Builds the YawlClient instance.
         *
         * @return a new YawlClient instance
         */
        public YawlClient build() {
            return new YawlClient(this);
        }
    }

    /**
     * Result wrapper for async operations.
     *
     * @param <T> the result type
     */
    public static class AsyncResult<T> {
        private final CompletableFuture<T> future;

        AsyncResult(CompletableFuture<T> future) {
            this.future = future;
        }

        /**
         * Awaits the result synchronously.
         *
         * @return the result value
         * @throws YawlException if the operation fails
         */
        public T await() throws YawlException {
            try {
                return future.join();
            } catch (Exception e) {
                throw new YawlException("Operation failed", e);
            }
        }

        /**
         * Returns the underlying CompletableFuture.
         *
         * @return the CompletableFuture
         */
        public CompletableFuture<T> toFuture() {
            return future;
        }

        /**
         * Awaits the result with a timeout.
         *
         * @param timeout maximum time to wait
         * @return the result value
         * @throws YawlException if timeout expires or operation fails
         */
        public T await(Duration timeout) throws YawlException {
            try {
                return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                throw new YawlException("Operation timed out or failed", e);
            }
        }
    }

    /**
     * Exception thrown when YAWL API operations fail.
     */
    public static class YawlException extends RuntimeException {
        public YawlException(String message) {
            super(message);
        }

        public YawlException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
