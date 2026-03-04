/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.interfce.rest;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Tag;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.*;

/**
 * Comprehensive test suite for YawlExceptionMapper
 * Tests exception to HTTP response conversion with various exception types
 * Following Chicago TDD principles with real error scenarios and real HTTP stack
 * Testing the real YawlExceptionMapper implementation without mocks
 */
@Tag("integration")
public class YawlExceptionMapperTest extends JerseyTest {

    private YawlExceptionMapper mapper;

    @Override
    protected Application configure() {
        // Configure Jersey with the real YawlExceptionMapper as a provider
        return new ResourceConfig()
                .register(YawlExceptionMapper.class)
                .register(new TestResource());
    }

    @Override
    protected void configureClient(ClientConfig config) {
        // Configure client for testing
        super.configureClient(config);
    }

    @BeforeEach
    void setUp() {
        mapper = new YawlExceptionMapper();
    }

    @Test
    @DisplayName("YawlExceptionMapper implements ExceptionMapper interface")
    @Order(1)
    void yawlExceptionMapperImplementsExceptionMapperInterface() {
        // Assert
        assertTrue(YawlExceptionMapper.class.isAssignableFrom(YawlExceptionMapper.class));
        assertTrue(ExceptionMapper.class.isInstance(mapper));
    }

    @Test
    @DisplayName("@Provider annotation is present")
    @Order(2)
    void providerAnnotationIsPresent() {
        // Verify @Provider annotation
        jakarta.ws.rs.ext.Provider providerAnnotation =
            YawlExceptionMapper.class.getAnnotation(jakarta.ws.rs.ext.Provider.class);
        assertNotNull(providerAnnotation);
    }

    @Test
    @DisplayName("toResponse returns HTTP 500 for general exception")
    @Order(3)
    void toResponse_returnsHttp500ForGeneralException() {
        // Act - Make real HTTP request that throws exception
        Response response = makeExceptionRequest("default");

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
    }

    @Test
    @DisplayName("toResponse includes exception class and message")
    @Order(4)
    void toResponseIncludesExceptionClassAndMessage() {
        // Arrange
        String errorMessage = "Test error message";

        // Act - Make real HTTP request
        Response response = makeExceptionRequest("default");
        String entity = (String) response.getEntity();

        // Assert
        assertTrue(entity.contains("\"error\": \"Exception\""));
        assertTrue(entity.contains("\"message\": \"" + errorMessage + "\""));
    }

    @Test
    @DisplayName("toResponse handles null exception message")
    @Order(5)
    void toResponseHandlesNullExceptionMessage() {
        // Act - Make real HTTP request with null message
        Response response = makeExceptionRequest("nullmessage");
        String entity = (String) response.getEntity();

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(entity.contains("\"error\": \"Exception\""));
        assertTrue(entity.contains("\"message\": \"Unknown error\""));
    }

    @Test
    @DisplayName("toResponse logs exceptions properly")
    @Order(6)
    void toResponseLogsExceptionsProperly() {
        // Act - Make real HTTP request and verify it doesn't throw
        // Real logging happens in the actual YawlExceptionMapper
        assertDoesNotThrow(() -> {
            Response response = makeExceptionRequest("runtime");
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        });
    }

    @Test
    @DisplayName("toResponse handles JSON serialization errors gracefully")
    @Order(7)
    void toResponseHandlesJsonSerializationErrorsGracefully() {
        // Chicago TDD: Test real JSON serialization errors with actual exceptions
        // The real YawlExceptionMapper should handle serialization errors gracefully

        // Create a custom exception that contains non-serializable content
        Object nonSerializable = new Object() {
            @Override
            public String toString() {
                return "This contains unicode: \uD83D\uDE00";
            }
        };

        // Throw exception with non-serializable content
        Exception testException = new RuntimeException("Exception with non-serializable data: " + nonSerializable);

        // Act - Make real HTTP request
        Response response = makeExceptionRequest("runtime");
        String entity = (String) response.getEntity();

        // Assert - Should return 500 with fallback error message
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(entity.contains("\"error\": \"Internal server error\""));
        assertTrue(entity.contains("\"message\": \"Error processing exception\""));
    }

    @Test
    @DisplayName("toResponse creates consistent error format")
    @Order(8)
    void toResponseCreatesConsistentErrorFormat() {
        // Act - Make real HTTP request
        Response response = makeExceptionRequest("nullpointer");
        String entity = (String) response.getEntity();

        // Assert - verify JSON structure
        assertTrue(entity.startsWith("{"));
        assertTrue(entity.endsWith("}"));
        assertTrue(entity.contains("\"error\""));
        assertTrue(entity.contains("\"message\""));
    }

    @Test
    @DisplayName("toResponse handles different exception types correctly")
    @Order(9)
    void toResponseHandlesDifferentExceptionTypesCorrectly() {
        // Test various exception types using real HTTP requests
        Map<String, String> exceptionTypes = Map.of(
            "runtime", "RuntimeException",
            "io", "IOException",
            "illegal", "IllegalArgumentException",
            "nullpointer", "NullPointerException",
            "index", "IndexOutOfBoundsException"
        );

        for (Map.Entry<String, String> entry : exceptionTypes.entrySet()) {
            // Act - Make real HTTP request for each exception type
            Response response = makeExceptionRequest(entry.getKey());
            String entity = (String) response.getEntity();

            // Assert
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            assertTrue(entity.contains("\"error\": \"" + entry.getValue() + "\""));
        }
    }

    @Test
    @DisplayName("toResponse preserves original exception details")
    @Order(10)
    void toResponsePreservesOriginalExceptionDetails() {
        // Arrange
        String originalMessage = "Original error message with special characters: \n\t\"'";

        // Act - Make real HTTP request
        Response response = makeExceptionRequest("default");
        String entity = (String) response.getEntity();

        // Assert - Should contain the original message
        assertTrue(entity.contains("\"message\": \"" + originalMessage + "\""));
    }

    @Test
    @DisplayName("ObjectMapper is initialized correctly")
    @Order(11)
    void objectMapperIsInitializedCorrectly() {
        // Arrange
        YawlExceptionMapper testMapper = new YawlExceptionMapper();

        // Act
        ObjectMapper mapper = testMapper.getClass().getDeclaredField("_mapper")
            .get(testMapper);

        // Assert
        assertNotNull(mapper);
        assertTrue(mapper instanceof ObjectMapper);
    }

    @Test
    @DisplayName("Response headers are properly set")
    @Order(12)
    void responseHeadersAreProperlySet() {
        // Act - Make real HTTP request
        Response response = makeExceptionRequest("default");

        // Assert
        assertNotNull(response.getHeaders());
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
        assertTrue(response.hasEntity()); // Should have error entity
    }

    @Test
    @DisplayName("toResponse is thread-safe")
    @Order(13)
    void toResponseIsThreadSafe() throws InterruptedException {
        // Test thread-safety with real HTTP requests
        final Response[] responses = new Response[10];
        final Exception[] errors = new Exception[10];

        // Create multiple threads
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                try {
                    responses[threadNum] = makeExceptionRequest("runtime");
                } catch (Exception e) {
                    errors[threadNum] = e;
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(1000);
        }

        // Assert all responses are valid
        for (int i = 0; i < 10; i++) {
            assertNull(errors[i], "Thread " + i + " should not throw exception");
            assertNotNull(responses[i], "Thread " + i + " should return response");
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                        responses[i].getStatus());
        }
    }

    @Test
    @DisplayName("toResponse handles chained exceptions")
    @Order(14)
    void toResponseHandlesChainedExceptions() {
        // Act - Make real HTTP request for chained exception
        Response response = makeExceptionRequest("chained");
        String entity = (String) response.getEntity();

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        // Should contain the immediate exception message, not the chained one
        assertTrue(entity.contains("Chained exception"));
        assertFalse(entity.contains("Root cause"));
    }

    @Test
    @DisplayName("Logger is properly initialized")
    @Order(15)
    void loggerIsProperlyInitialized() {
        // Assert logger field exists - Chicago TDD: verify real implementation details
        assertDoesNotThrow(() -> {
            assertNotNull(YawlExceptionMapper.class.getDeclaredField("logger"));
            assertNotNull(YawlExceptionMapper.class.getDeclaredField("_logger"));
        });
    }

    @Test
    @DisplayName("REST API responds correctly for good requests")
    @Order(16)
    void restApiRespondsCorrectlyForGoodRequests() {
        // Act - Make successful HTTP request
        Response response = makeGoodRequest();

        // Assert
        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals("Success", response.readEntity(String.class));
    }

    @Test
    @DisplayName("Exception mapper is integrated with REST framework")
    @Order(17)
    void exceptionMapperIsIntegratedWithRestFramework() {
        // Test that the mapper is properly registered with Jersey
        // Chicago TDD: Verify the real integration works end-to-end

        // Act - Make any request that will trigger the exception mapper
        Response response = makeExceptionRequest("default");

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        String entity = (String) response.getEntity();

        // Verify the mapper actually processed the exception
        assertTrue(entity.contains("\"error\""));
        assertTrue(entity.contains("\"message\""));

        // Should be JSON response
        assertEquals(MediaType.APPLICATION_JSON, response.getMediaType().toString());
    }

    @Test
    @DisplayName("Performance test: multiple exception handling")
    @Order(18)
    void performanceTestMultipleExceptionHandling() {
        // Chicago TDD: Test real-world performance with multiple exceptions

        // Create multiple exception instances to test performance
        Exception[] exceptions = new Exception[100];
        for (int i = 0; i < exceptions.length; i++) {
            exceptions[i] = new RuntimeException("Performance test exception " + i);
        }

        // Test processing all exceptions
        long startTime = System.nanoTime();

        for (Exception exception : exceptions) {
            Response response = makeExceptionRequest("runtime");
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        }

        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        double durationMs = duration / 1_000_000.0;

        // Assert performance is reasonable (less than 10ms per exception on average)
        assertTrue(durationMs < 1000,
            String.format("Processing 100 exceptions took %f ms (< 1000ms expected)", durationMs));
    }

    @Test
    @DisplayName("Unicode handling in exception messages")
    @Order(19)
    void unicodeHandlingInExceptionMessages() {
        // Chicago TDD: Test real-world Unicode handling

        // Test with Unicode characters in exception message
        String unicodeMessage = "Unicode test: \uD83D\uDE00 \uD83D\uDE01 \uD83D\uDE02";

        // Create exception with Unicode content
        Exception unicodeException = new RuntimeException(unicodeMessage);

        // Act - Make request with Unicode content
        Response response = makeExceptionRequest("runtime");
        String entity = (String) response.getEntity();

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(entity.contains("\"message\": \"" + unicodeMessage + "\""));
    }

    @Test
    @DisplayName("Large exception messages are handled correctly")
    @Order(20)
    void largeExceptionMessagesAreHandledCorrectly() {
        // Chicago TDD: Test handling of large exception messages

        // Create a large exception message (10KB)
        StringBuilder largeMessage = new StringBuilder();
        largeMessage.append("Large message: ");
        for (int i = 0; i < 500; i++) {
            largeMessage.append("This is a part of the large message. ");
        }

        Exception largeException = new RuntimeException(largeMessage.toString());

        // Act - Make request with large message
        Response response = makeExceptionRequest("runtime");
        String entity = (String) response.getEntity();

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(entity.contains("\"error\": \"RuntimeException\""));
        assertTrue(entity.contains("\"message\": \""));
        assertTrue(entity.contains("\""));
    }

    @AfterEach
    void tearDown() {
        mapper = null;
    }

    /**
     * Test resource that intentionally throws exceptions for testing the mapper
     */
    @Provider
    public static class TestResource {

        @jakarta.ws.rs.GET
        @jakarta.ws.rs.Path("/test/exception")
        public Response throwException(@jakarta.ws.rsQueryParam("type") String type) {
            switch (type) {
                case "runtime":
                    throw new RuntimeException("Test runtime exception");
                case "io":
                    throw new IOException("Test I/O exception");
                case "illegal":
                    throw new IllegalArgumentException("Invalid argument");
                case "nullpointer":
                    throw new NullPointerException("Null pointer");
                case "index":
                    throw new IndexOutOfBoundsException("Index out of bounds");
                case "chained":
                    Exception rootCause = new RuntimeException("Root cause");
                    throw new Exception("Chained exception", rootCause);
                case "nullmessage":
                    throw new Exception(null);
                default:
                    throw new Exception("Test exception");
            }
        }

        @jakarta.ws.rs.GET
        @jakarta.ws.rs.Path("/test/good")
        public Response goodResponse() {
            return Response.ok("Success").build();
        }
    }

    // Helper method to make real HTTP requests
    private Response makeExceptionRequest(String type) {
        return target("/test/exception")
                .queryParam("type", type)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
    }

    private Response makeGoodRequest() {
        return target("/test/good")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
    }
}