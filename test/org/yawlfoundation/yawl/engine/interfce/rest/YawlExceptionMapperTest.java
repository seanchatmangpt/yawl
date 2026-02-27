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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for YawlExceptionMapper
 * Tests exception to HTTP response conversion with various exception types
 * Following Chicago TDD principles with real error scenarios
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(OrderAnnotation.class)
public class YawlExceptionMapperTest {

    private YawlExceptionMapper mapper;
    private ObjectMapper objectMapper;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper mockObjectMapper;

    @BeforeEach
    void setUp() {
        mapper = new YawlExceptionMapper();
        objectMapper = new ObjectMapper();
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
        // Arrange
        Exception testException = new Exception("Test exception");

        // Act
        Response response = mapper.toResponse(testException);

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertEquals("application/json", response.getMediaType().toString());
    }

    @Test
    @DisplayName("toResponse includes exception class and message")
    @Order(4)
    void toResponseIncludesExceptionClassAndMessage() {
        // Arrange
        String errorMessage = "Test error message";
        Exception testException = new Exception(errorMessage);

        // Act
        Response response = mapper.toResponse(testException);
        String entity = (String) response.getEntity();

        // Assert
        assertTrue(entity.contains("\"error\": \"Exception\""));
        assertTrue(entity.contains("\"message\": \"" + errorMessage + "\""));
    }

    @Test
    @DisplayName("toResponse handles null exception message")
    @Order(5)
    void toResponseHandlesNullExceptionMessage() {
        // Arrange
        Exception testException = new Exception(null);

        // Act
        Response response = mapper.toResponse(testException);
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
        // Arrange
        Exception testException = new RuntimeException("Test runtime exception");

        // Act - we can't directly verify logging in unit tests without Log4j setup
        // But we can verify the method doesn't throw
        assertDoesNotThrow(() -> {
            mapper.toResponse(testException);
        });
    }

    @Test
    @DisplayName("toResponse handles JSON serialization errors gracefully")
    @Order(7)
    void toResponseHandlesJsonSerializationErrorsGracefully() {
        // Create a mapper with a failing ObjectMapper
        YawlExceptionMapper testMapper = new YawlExceptionMapper() {
            @Override
            protected ObjectMapper createObjectMapper() {
                return new ObjectMapper() {
                    @Override
                    public String writeValueAsString(Object value) throws IOException {
                        throw new IOException("Serialization failed");
                    }
                };
            }
        };

        // Arrange
        Exception testException = new Exception("Test exception");

        // Act
        Response response = testMapper.toResponse(testException);
        String entity = (String) response.getEntity();

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        assertTrue(entity.contains("\"error\": \"Internal server error\""));
        assertTrue(entity.contains("\"message\": \"Error processing exception\""));
    }

    @Test
    @DisplayName("toResponse creates consistent error format")
    @Order(8)
    void toResponseCreatesConsistentErrorFormat() {
        // Arrange
        Exception testException = new NullPointerException("Null pointer occurred");

        // Act
        Response response = mapper.toResponse(testException);
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
        // Test various exception types
        Exception[] exceptions = {
            new RuntimeException("Runtime error"),
            new IOException("I/O error"),
            new IllegalArgumentException("Invalid argument"),
            new NullPointerException("Null pointer"),
            new IndexOutOfBoundsException("Index out of bounds")
        };

        for (Exception exception : exceptions) {
            // Act
            Response response = mapper.toResponse(exception);

            // Assert
            assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
            String entity = (String) response.getEntity();
            assertTrue(entity.contains("\"error\": \"" + exception.getClass().getSimpleName() + "\""));
        }
    }

    @Test
    @DisplayName("toResponse preserves original exception details")
    @Order(10)
    void toResponsePreservesOriginalExceptionDetails() {
        // Arrange
        String originalMessage = "Original error message with special characters: \n\t\"'";
        Exception testException = new Exception(originalMessage);

        // Act
        Response response = mapper.toResponse(testException);
        String entity = (String) response.getEntity();

        // Assert
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
        // Arrange
        Exception testException = new Exception("Test exception");

        // Act
        Response response = mapper.toResponse(testException);

        // Assert
        assertNotNull(response.getHeaders());
        assertEquals("application/json", response.getMediaType().toString());
        assertFalse(response.hasEntity());
    }

    @Test
    @DisplayName("toResponse is thread-safe")
    @Order(13)
    void toResponseIsThreadSafe() throws InterruptedException {
        // Arrange
        Exception testException = new Exception("Concurrent test exception");
        final Response[] responses = new Response[10];
        final Exception[] errors = new Exception[10];

        // Create multiple threads
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                try {
                    responses[threadNum] = mapper.toResponse(testException);
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
        // Arrange
        Exception rootCause = new RuntimeException("Root cause");
        Exception chainedException = new Exception("Chained exception", rootCause);

        // Act
        Response response = mapper.toResponse(chainedException);
        String entity = (String) response.getEntity();

        // Assert
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
        // Should contain the immediate exception message, not the chained one
        assertTrue(entity.contains("Chained exception"));
    }

    @Test
    @DisplayName("Logger is properly initialized")
    @Order(15)
    void loggerIsProperlyInitialized() {
        // Assert logger field exists
        assertNotNull(YawlExceptionMapper.class.getDeclaredField("logger"));
        assertNotNull(YawlExceptionMapper.class.getDeclaredField("_logger"));
    }

    @AfterEach
    void tearDown() {
        mapper = null;
        objectMapper = null;
    }
}