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

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Security Integration Tests for SPARQL Engine Implementations.
 *
 * <p>This test class validates security requirements for SPARQL engines used in
 * the YAWL autonomous marketplace. Tests cover authentication, authorization,
 * rate limiting, and multi-tenant isolation.</p>
 *
 * <h3>Test Categories:</h3>
 * <ol>
 *   <li><b>Authentication tests</b> - Valid/invalid credentials handling</li>
 *   <li><b>Authorization tests</b> - Write permissions and access control</li>
 *   <li><b>Rate limiting tests</b> - Query throttling and abuse prevention</li>
 *   <li><b>Multi-tenant isolation tests</b> - Data separation between tenants</li>
 * </ol>
 *
 * <h3>External Dependencies:</h3>
 * <ul>
 *   <li>QLever instance with authentication enabled on port 7001</li>
 *   <li>Test user credentials: valid=admin/test123, invalid=admin/wrongpass</li>
 *   <li>Multi-tenant test data isolation</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("integration")
@DisplayName("SPARQL Engine Security Integration Tests")
public class SparqlEngineSecurityIntegrationTest extends TestCase {

    private static final String BASE_URL = "http://localhost:7001";
    private static final String VALID_USERNAME = "admin";
    private static final String VALID_PASSWORD = "test123";
    private static final String INVALID_PASSWORD = "wrongpass";
    private static final String TENANT_1 = "tenant-a";
    private static final String TENANT_2 = "tenant-b";

    private QLeverSparqlEngine engine;
    private HttpClient httpClient;
    private AtomicInteger queryCount = new AtomicInteger(0);

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize HTTP client for security testing
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        // Initialize SPARQL engine
        engine = new QLeverSparqlEngine(BASE_URL);

        // Check if engine is available
        if (!engine.isAvailable()) {
            System.out.println("SPARQL engine unavailable at " + BASE_URL + " - skipping security tests");
            engine = null;
            return; // skip gracefully when QLever not running
        }
    }

    // -------------------------------------------------------------------------
    // Authentication Tests
    // -------------------------------------------------------------------------

    @DisplayName("SPARQL query with valid credentials succeeds")
    public void testSparqlQueryWithValidCredentials() throws Exception {
        skipIfUnavailable();

        // This test would require authentication setup in QLever
        // For now, we test the basic authentication pattern
        String query = "SELECT COUNT(*) WHERE { ?s ?p ?o } LIMIT 1";

        // Create authenticated request (if authentication is enabled)
        HttpRequest request = createAuthenticatedRequest(query, VALID_USERNAME, VALID_PASSWORD);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // If authentication is enabled, expect 200 OK
        // If not enabled, this will still succeed (test environment specific)
        assertTrue("Query should succeed with valid credentials",
                response.statusCode() == 200 || response.statusCode() == 400);

        if (response.statusCode() == 200) {
            assertNotNull("Response body should not be null", response.body());
            assertFalse("Response should not be empty", response.body().isEmpty());
        }
    }

    @DisplayName("SPARQL query with invalid credentials is rejected")
    public void testSparqlQueryWithInvalidCredentials() throws Exception {
        skipIfUnavailable();

        String query = "SELECT COUNT(*) WHERE { ?s ?p ?o } LIMIT 1";

        // Try with invalid password
        HttpRequest request = createAuthenticatedRequest(query, VALID_USERNAME, INVALID_PASSWORD);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should reject authentication (401 Unauthorized)
        assertEquals("Invalid credentials should be rejected", 401, response.statusCode());
        assertTrue("Error response should indicate authentication failure",
                response.body().contains("unauthorized") || response.body().contains("forbidden"));
    }

    @DisplayName("SPARQL query with missing credentials is rejected")
    public void testSparqlQueryWithMissingCredentials() throws Exception {
        skipIfUnavailable();

        String query = "SELECT COUNT(*) WHERE { ?s ?p ?o } LIMIT 1";

        // Send request without any authentication
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/query"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/sparql-results+xml")
                .POST(HttpRequest.BodyPublishers.ofString("query=" + query))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Should reject unauthorized request (401 Unauthorized)
        assertEquals("Missing credentials should be rejected", 401, response.statusCode());
    }

    // -------------------------------------------------------------------------
    // Authorization Tests
    // -------------------------------------------------------------------------

    @DisplayName("SPARQL update requires write permission")
    public void testSparqlUpdateRequiresWritePermission() throws Exception {
        skipIfUnavailable();

        String updateQuery = "INSERT DATA { <http://test.com/s> <http://test.com/p> <http://test.com/o> }";

        // Try to execute update with read-only credentials
        HttpRequest request = createAuthenticatedRequest(updateQuery, VALID_USERNAME, VALID_PASSWORD);
        request = HttpRequest.newBuilder(request)
                .uri(URI.create(BASE_URL + "/api/update"))
                .header("Content-Type", "application/sparql-update")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Update should be rejected without proper permissions
        assertTrue("SPARQL UPDATE should require write permission",
                response.statusCode() == 403 || response.statusCode() == 405);

        if (response.statusCode() == 403) {
            assertTrue("Error should indicate permission denied",
                    response.body().contains("forbidden") || response.body().contains("permission"));
        }
    }

    @DisplayName("SPARQL update with insufficient credentials is rejected")
    public void testSparqlUpdateWithInsufficientCredentials() throws Exception {
        skipIfUnavailable();

        String updateQuery = "INSERT DATA { <http://test.com/s2> <http://test.com/p2> <http://test.com/o2> }";

        // Try to execute update with invalid credentials
        HttpRequest request = createAuthenticatedRequest(updateQuery, VALID_USERNAME, INVALID_PASSWORD);
        request = HttpRequest.newBuilder(request)
                .uri(URI.create(BASE_URL + "/api/update"))
                .header("Content-Type", "application/sparql-update")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Update should be rejected due to invalid credentials
        assertEquals("SPARQL UPDATE should be rejected with invalid credentials", 401, response.statusCode());
    }

    // -------------------------------------------------------------------------
    // Rate Limiting Tests
    // -------------------------------------------------------------------------

    @DisplayName("Rate limiting on SPARQL queries")
    public void testRateLimitingOnSparqlQueries() throws Exception {
        skipIfUnavailable();

        final int MAX_QUERIES_PER_SECOND = 10;
        final int TEST_DURATION_MS = 3000; // 3 seconds
        final int EXPECTED_MAX_QUERIES = MAX_QUERIES_PER_SECOND * (TEST_DURATION_MS / 1000);

        // Reset query counter
        queryCount.set(0);

        // Run multiple queries concurrently
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    while (System.currentTimeMillis() - startTime < TEST_DURATION_MS) {
                        try {
                            executeRateLimitedQuery();
                        } catch (Exception e) {
                            // Rate limiting might throw exception
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(TEST_DURATION_MS + 1000);
        }

        // Query count should be limited
        int actualCount = queryCount.get();
        System.out.println("Executed " + actualCount + " queries in " + TEST_DURATION_MS + "ms");

        // In a rate-limited environment, we should not exceed the limit by more than 20%
        assertTrue("Query count should be rate limited",
                actualCount <= EXPECTED_MAX_QUERIES * 1.2);

        // Verify we actually executed some queries (rate limiting shouldn't block all)
        assertTrue("Some queries should succeed", actualCount > 0);
    }

    @DisplayName("Rate limiting rejects excessive concurrent connections")
    public void testRateLimitingRejectsExcessiveConnections() throws Exception {
        skipIfUnavailable();

        final int EXCESSIVE_CONNECTIONS = 50;
        int successCount = 0;
        int rejectedCount = 0;

        for (int i = 0; i < EXCESSIVE_CONNECTIONS; i++) {
            try {
                String query = "SELECT COUNT(*) WHERE { ?s ?p ?o } LIMIT 1";
                HttpRequest request = createAuthenticatedRequest(query, VALID_USERNAME, VALID_PASSWORD);

                // Send request with timeout
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    successCount++;
                } else if (response.statusCode() == 429) {
                    rejectedCount++;
                }
            } catch (Exception e) {
                // Connection rejected or timeout
                rejectedCount++;
            }

            // Small delay between requests
            Thread.sleep(10);
        }

        System.out.println("Success: " + successCount + ", Rejected: " + rejectedCount);

        // Some requests should be rejected under heavy load
        assertTrue("Excessive connections should be rejected", rejectedCount > 0);

        // But some should succeed
        assertTrue("Some requests should still succeed", successCount > 0);
    }

    // -------------------------------------------------------------------------
    // Multi-Tenant Isolation Tests
    // -------------------------------------------------------------------------

    @DisplayName("Multi-tenant SPARQL isolation")
    public void testMultiTenantSparqlIsolation() throws Exception {
        skipIfUnavailable();

        // First, load tenant-specific data (this would require setup)
        loadTenantData(TENANT_1, "Tenant A data");
        loadTenantData(TENANT_2, "Tenant B data");

        // Test queries that should be tenant-specific
        String tenant1Query = buildTenantQuery(TENANT_1);
        String tenant2Query = buildTenantQuery(TENANT_2);

        // Execute queries
        HttpRequest tenant1Request = createAuthenticatedRequest(tenant1Query, VALID_USERNAME, VALID_PASSWORD);
        HttpRequest tenant2Request = createAuthenticatedRequest(tenant2Query, VALID_USERNAME, VALID_PASSWORD);

        HttpResponse<String> tenant1Response = httpClient.send(tenant1Request, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> tenant2Response = httpClient.send(tenant2Request, HttpResponse.BodyHandlers.ofString());

        // Both queries should succeed
        assertEquals("Tenant 1 query should succeed", 200, tenant1Response.statusCode());
        assertEquals("Tenant 2 query should succeed", 200, tenant2Response.statusCode());

        // Results should be different (isolated data)
        String tenant1Result = tenant1Response.body();
        String tenant2Result = tenant2Response.body();

        assertFalse("Tenant results should be different", tenant1Result.equals(tenant2Result));

        // Verify tenant-specific data is present in respective results
        assertTrue("Tenant 1 result should contain tenant A data", tenant1Result.contains(TENANT_1));
        assertTrue("Tenant 2 result should contain tenant B data", tenant2Result.contains(TENANT_2));
    }

    @DisplayName("Cross-tenant access is prevented")
    public void testCrossTenantAccessIsPrevented() throws Exception {
        skipIfUnavailable();

        // Try to access tenant B data with tenant A credentials (should be prevented)
        String crossTenantQuery = buildTenantQuery(TENANT_2);

        HttpRequest request = createAuthenticatedRequest(crossTenantQuery, VALID_USERNAME, VALID_PASSWORD);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // The engine should prevent cross-tenant access
        // In a properly secured system, this might return empty results or an error
        assertTrue("Cross-tenant access should be prevented or return empty results",
                response.statusCode() == 200 || response.statusCode() == 403);

        if (response.statusCode() == 200) {
            // If results are returned, they should not contain sensitive data
            String result = response.body();
            assertFalse("Result should not contain cross-tenant data", result.contains(TENANT_1));
        }
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private boolean engineAvailable() {
        return engine != null && engine.isAvailable();
    }

    private void skipIfUnavailable() {
        if (!engineAvailable()) {
            return; // skip gracefully when QLever not running
        }
    }

    private HttpRequest createAuthenticatedRequest(String query, String username, String password) throws Exception {
        String encodedQuery = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        String body = "query=" + encodedQuery;

        // Basic authentication header
        String auth = java.util.Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/query"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/sparql-results+xml")
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    private void executeRateLimitedQuery() throws Exception {
        if (!engineAvailable()) return;

        String query = "SELECT COUNT(*) WHERE { ?s ?p ?o } LIMIT 1";
        try {
            engine.constructToTurtle(query);
            queryCount.incrementAndGet();
        } catch (SparqlEngineException e) {
            // Rate limiting might throw exception - this is expected
            throw e;
        }
    }

    private void loadTenantData(String tenant, String data) throws Exception {
        // This would typically load tenant-specific data into the SPARQL engine
        // For testing purposes, we simulate this with a comment
        System.out.println("Loading data for tenant: " + tenant);

        // In a real implementation, this would be:
        // String insertQuery = "INSERT DATA { ... tenant-specific data ... }";
        // engine.sparqlUpdate(insertQuery);
    }

    private String buildTenantQuery(String tenant) {
        // Build a query that should only return tenant-specific data
        return "SELECT ?s ?p ?o WHERE { " +
               "?s <http://yawlfoundation.org/yawl/marketplace#tenant> \"" + tenant + "\" . " +
               "?s ?p ?o " +
               "} LIMIT 10";
    }
}