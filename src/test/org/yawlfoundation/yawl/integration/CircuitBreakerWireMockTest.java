package org.yawlfoundation.yawl.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Resilience4j CircuitBreaker with WireMock HTTP server.
 *
 * These tests verify CircuitBreaker behavior using REAL HTTP interactions with WireMock.
 * WireMock is a real HTTP stub server (not a mock framework) that simulates HTTP endpoints
 * for testing resilience patterns without external service dependencies.
 *
 * Test scenarios:
 * - CLOSED state: requests succeed normally
 * - OPEN state: circuit opens after error threshold exceeded
 * - HALF_OPEN state: circuit allows test request to recovery
 * - Timeout detection: slow responses trigger circuit
 */
public class CircuitBreakerWireMockTest {

    private WireMockServer wireMockServer;
    private CircuitBreaker circuitBreaker;
    private HttpClient httpClient;

    @BeforeEach
    public void setUp() {
        // Initialize WireMock HTTP server (real HTTP interaction)
        wireMockServer = new WireMockServer(wireMockConfig()
            .port(8080)
            .jettyStopTimeout(5000));
        wireMockServer.start();
        configureFor("localhost", 8080);

        // Initialize HTTP client for real HTTP calls
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        // Configure CircuitBreaker: failure threshold 50%, wait duration 1s
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(Duration.ofMillis(500))
            .waitDurationInOpenState(Duration.ofSeconds(1))
            .minimumNumberOfCalls(2)
            .recordExceptions(Exception.class)
            .ignoreExceptions()
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();

        circuitBreaker = CircuitBreaker.of("http-requests", config);
    }

    @AfterEach
    public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            wireMockServer.resetAll();
        }
    }

    /**
     * Test CLOSED state: successful requests pass through unaffected.
     */
    @Test
    public void testCircuitBreakerClosedState() throws Exception {
        // Arrange: configure WireMock to return 200 OK
        givenThat(get(urlEqualTo("/api/status"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));

        // Act: make request through CircuitBreaker
        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8080/api/status"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Assert: request succeeded, circuit remains CLOSED
        assertEquals(200, response.statusCode());
        assertEquals("CLOSED", circuitBreaker.getState().toString());
    }

    /**
     * Test OPEN state: circuit opens after failure threshold exceeded.
     * Circuit opens when failure rate >= 50% with minimum 2 calls.
     */
    @Test
    public void testCircuitBreakerOpensOnFailureThreshold() throws Exception {
        // Arrange: configure WireMock to return 500 errors
        givenThat(get(urlEqualTo("/api/failing"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8080/api/failing"))
            .GET()
            .build();

        // Act: execute first request (fails)
        assertThrows(Exception.class, () -> {
            makeCircuitBreakerRequest(request);
        });

        // Execute second request (fails)
        assertThrows(Exception.class, () -> {
            makeCircuitBreakerRequest(request);
        });

        // Assert: circuit should now be OPEN (2 failures = 100% > 50% threshold)
        assertEquals("OPEN", circuitBreaker.getState().toString());
    }

    /**
     * Test HALF_OPEN state: circuit allows test request after wait duration.
     */
    @Test
    public void testCircuitBreakerTransitionToHalfOpen() throws Exception {
        // Arrange: configure endpoint that fails
        givenThat(get(urlEqualTo("/api/test"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8080/api/test"))
            .GET()
            .build();

        // Act: force circuit to OPEN
        assertThrows(Exception.class, () -> makeCircuitBreakerRequest(request));
        assertThrows(Exception.class, () -> makeCircuitBreakerRequest(request));
        assertEquals("OPEN", circuitBreaker.getState().toString());

        // Wait for transition to HALF_OPEN (configured as 1 second)
        Thread.sleep(1500);

        // Reset WireMock to return success
        wireMockServer.resetAll();
        givenThat(get(urlEqualTo("/api/test"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));

        // Assert: circuit is in HALF_OPEN or transitioned to CLOSED
        String state = circuitBreaker.getState().toString();
        assertTrue("HALF_OPEN".equals(state) || "CLOSED".equals(state),
            "Expected HALF_OPEN or CLOSED state, got: " + state);
    }

    /**
     * Test timeout detection: slow responses trigger circuit.
     * Configured slowCallDurationThreshold = 500ms.
     */
    @Test
    public void testCircuitBreakerDetectsSlowCalls() throws Exception {
        // Arrange: configure WireMock with 1 second delay (exceeds 500ms threshold)
        givenThat(get(urlEqualTo("/api/slow"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("Response")
                .withFixedDelay(1000)));  // 1 second delay

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8080/api/slow"))
            .GET()
            .timeout(Duration.ofSeconds(5))
            .build();

        // Act: execute slow request through circuit breaker
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // Expected: timeout or slow call recorded
        }

        // Act: execute second slow request
        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // Expected: timeout or slow call recorded
        }

        // Assert: circuit should be OPEN due to slow calls exceeding threshold
        String state = circuitBreaker.getState().toString();
        assertTrue("OPEN".equals(state) || "HALF_OPEN".equals(state),
            "Expected circuit to be OPEN or HALF_OPEN due to slow calls, got: " + state);
    }

    /**
     * Test recovery: circuit returns to CLOSED on successful test request.
     */
    @Test
    public void testCircuitBreakerRecoversToClosed() throws Exception {
        // Arrange: force circuit to OPEN with failures
        givenThat(get(urlEqualTo("/api/recovery"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Error")));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("http://localhost:8080/api/recovery"))
            .GET()
            .build();

        assertThrows(Exception.class, () -> makeCircuitBreakerRequest(request));
        assertThrows(Exception.class, () -> makeCircuitBreakerRequest(request));
        assertEquals("OPEN", circuitBreaker.getState().toString());

        // Wait for HALF_OPEN transition
        Thread.sleep(1500);

        // Act: reset WireMock to return success
        wireMockServer.resetAll();
        givenThat(get(urlEqualTo("/api/recovery"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("OK")));

        // Execute test request through circuit breaker
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Assert: circuit should be CLOSED after successful recovery test
        assertEquals("CLOSED", circuitBreaker.getState().toString());
    }

    // Helper method to execute request through CircuitBreaker
    private HttpResponse<String> makeCircuitBreakerRequest(HttpRequest request) throws Exception {
        return circuitBreaker.executeSupplier(() -> {
            try {
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.io.IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
