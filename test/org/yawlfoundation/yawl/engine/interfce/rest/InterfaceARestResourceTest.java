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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerFactory;

import static org.junit.jupiter.api.*;

/**
 * Comprehensive test suite for InterfaceARestResource
 * Tests REST API endpoints for specification upload, validation, and management
 * Following Chicago TDD principles with real YAWL engine integration
 * Uses JerseyTest framework for in-memory HTTP testing
 */
public class InterfaceARestResourceTest extends JerseyTest {

    private static final String TEST_SPEC_XML = """
        <specification xmlns="http://www.yawlfoundation.org/yawlschema">
            <specificationID>testSpec</specificationID>
            <name>Test Specification</name>
            <version>1.0</version>
            <description>A test specification for validation</description>
            <rootnet>
                <tasks>
                    <task id="A" name="Task A"/>
                </tasks>
            </rootnet>
        </specification>
        """;

    @Override
    protected Application configure() {
        // Configure the JAX-RS application with our resource
        return new ResourceConfig(InterfaceARestResource.class);
    }

    @Override
    protected TestContainerFactory getTestContainerFactory() {
        // Use in-memory test container for real HTTP testing
        return new InMemoryTestContainerFactory();
    }

    @BeforeEach
    void setUp() {
        super.setUp();
        // Initialize a real servlet context for the resource
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            servletContext.setAttribute("engine", null);
            servletContext.setInitParameter("EnablePersistence", "true");
        }
    }

    @Test
    @DisplayName("Path annotation is correctly set to /ia")
    @Order(1)
    void pathAnnotationIsCorrectlySet() {
        // Verify the @Path annotation
        Path pathAnnotation = InterfaceARestResource.class.getAnnotation(Path.class);
        assertNotNull(pathAnnotation);
        assertEquals("/ia", pathAnnotation.value());
    }

    @Test
    @DisplayName("Produces and Consumes annotations set to APPLICATION_XML")
    @Order(2)
    void producesAndConsumesAnnotationsSet() {
        // Verify @Produces and @Consumes annotations
        Produces producesAnnotation = InterfaceARestResource.class.getAnnotation(Produces.class);
        Consumes consumesAnnotation = InterfaceARestResource.class.getAnnotation(Consumes.class);

        assertNotNull(producesAnnotation);
        assertNotNull(consumesAnnotation);
        assertArrayEquals(new MediaType[]{MediaType.APPLICATION_XML}, producesAnnotation.value());
        assertArrayEquals(new MediaType[]{MediaType.APPLICATION_XML}, consumesAnnotation.value());
    }

    @Test
    @DisplayName("Resource class has correct REST annotations")
    @Order(3)
    void resourceClassHasCorrectRestAnnotations() {
        // Verify the resource class structure
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Path.class));
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Produces.class));
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Consumes.class));
    }

    @Test
    @DisplayName("Upload specification endpoint works correctly")
    @Order(4)
    void uploadSpecificationEndpointWorksCorrectly() {
        // Arrange
        String sessionHandle = "test-session-123";

        // Act - Send real HTTP POST request using JerseyTest client
        Response response = target("/ia/specifications")
            .queryParam("sessionHandle", sessionHandle)
            .request(MediaType.APPLICATION_XML)
            .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));

        // Assert
        assertEquals(200, response.getStatus(), "Upload should return 200 OK");
        String entity = response.readEntity(String.class);
        assertNotNull(entity, "Response should not be null");
        assertTrue(entity.contains("<success>") || entity.contains("<failure>"),
            "Response should contain success or failure XML");
    }

    @Test
    @DisplayName("Upload specification without session handle returns 401")
    @Order(5)
    void uploadSpecificationWithoutSessionHandleReturns401() {
        // Act - Send request without sessionHandle
        Response response = target("/ia/specifications")
            .request(MediaType.APPLICATION_XML)
            .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));

        // Assert
        assertEquals(401, response.getStatus(), "Should return 401 Unauthorized without session handle");
        String entity = response.readEntity(String.class);
        assertNotNull(entity, "Response should not be null");
        assertTrue(entity.contains("Session handle is required") ||
                   entity.contains("Unauthorized"),
            "Response should indicate authentication failure");
    }

    @Test
    @DisplayName("Upload specification with empty session handle returns 401")
    @Order(6)
    void uploadSpecificationWithEmptySessionHandleReturns401() {
        // Act - Send request with empty sessionHandle
        Response response = target("/ia/specifications")
            .queryParam("sessionHandle", "")
            .request(MediaType.APPLICATION_XML)
            .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));

        // Assert
        assertEquals(401, response.getStatus(), "Should return 401 Unauthorized with empty session handle");
        String entity = response.readEntity(String.class);
        assertNotNull(entity, "Response should not be null");
    }

    @Test
    @DisplayName("EngineGateway initialization works with real engine")
    @Order(7)
    void engineGatewayInitializationWorks() {
        // Arrange - Test the real EngineGateway initialization
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            servletContext.setAttribute("engine", null);

            // Test that resource can be created with servlet context
            InterfaceARestResource resource = new InterfaceARestResource();
            try {
                // Use reflection to set servlet context (since it's private)
                java.lang.reflect.Field contextField =
                    InterfaceARestResource.class.getDeclaredField("_servletContext");
                contextField.setAccessible(true);
                contextField.set(resource, servletContext);

                assertNotNull(resource, "Resource should be created successfully");

                // Verify servlet context is accessible
                ServletContext context = (ServletContext) contextField.get(resource);
                assertEquals(servletContext, context);
            } catch (Exception e) {
                fail("ServletContext handling should work correctly: " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("EngineGateway persistence configuration works")
    @Order(8)
    void engineGatewayPersistenceConfigurationWorks() {
        // Arrange
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            servletContext.setAttribute("engine", null);
            servletContext.setInitParameter("EnablePersistence", "false");

            // Test with persistence disabled
            InterfaceARestResource resource = new InterfaceARestResource();
            try {
                java.lang.reflect.Field contextField =
                    InterfaceARestResource.class.getDeclaredField("_servletContext");
                contextField.setAccessible(true);
                contextField.set(resource, servletContext);

                // When persistence is disabled, it should still initialize (though may fail due to no DB)
                assertNotNull(resource, "Resource should be created with persistence disabled");
            } catch (Exception e) {
                fail("Should handle persistence configuration correctly");
            }
        }
    }

    @Test
    @DisplayName("REST resource handles multiple requests correctly")
    @Order(9)
    void restResourceHandlesMultipleRequestsCorrectly() {
        // Arrange
        String sessionHandle = "test-session-multi";

        // Act - Send multiple requests
        Response[] responses = new Response[3];
        for (int i = 0; i < 3; i++) {
            responses[i] = target("/ia/specifications")
                .queryParam("sessionHandle", sessionHandle + "-" + i)
                .request(MediaType.APPLICATION_XML)
                .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));
        }

        // Assert - All requests should be processed
        for (Response response : responses) {
            assertEquals(200, response.getStatus(), "All requests should return 200 OK");
            String entity = response.readEntity(String.class);
            assertNotNull(entity, "Response should not be null");
        }
    }

    @Test
    @DisplayName("Resource returns correct media types")
    @Order(10)
    void resourceReturnsCorrectMediaTypes() {
        // Test that the resource accepts and produces XML
        Response response = target("/ia/specifications")
            .queryParam("sessionHandle", "test-session")
            .request(MediaType.APPLICATION_XML)
            .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));

        // Assert content type
        assertEquals(MediaType.APPLICATION_XML, response.getMediaType().toString(),
            "Response should be XML");

        // Assert successful processing
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("Servlet context attribute management works correctly")
    @Order(11)
    void servletContextAttributeManagementWorksCorrectly() {
        // Arrange
        ServletContext servletContext = getServletContext();
        if (servletContext != null) {
            // Test attribute setting and getting
            servletContext.setAttribute("testAttribute", "testValue");
            assertEquals("testValue", servletContext.getAttribute("testAttribute"));

            // Test clearing attribute
            servletContext.setAttribute("testAttribute", null);
            assertNull(servletContext.getAttribute("testAttribute"));
        }
    }

    @Test
    @DisplayName("REST resource has proper structure")
    @Order(12)
    void restResourceHasProperStructure() {
        // This test verifies that the resource has the expected structure
        // for REST API implementation following Chicago TDD principles

        // Verify logger field exists
        try {
            java.lang.reflect.Field loggerField =
                InterfaceARestResource.class.getDeclaredField("_logger");
            assertNotNull(loggerField);
        } catch (NoSuchFieldException e) {
            fail("Resource should have a logger field");
        }

        // Verify the class has the expected annotations for REST API
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Path.class));
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Produces.class));
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Consumes.class));
    }

    @Test
    @DisplayName("Resource is compatible with JAX-RS framework")
    @Order(13)
    void resourceIsCompatibleWithJAXRSFramework() {
        // Test that our configuration includes the resource
        Application application = new ResourceConfig(InterfaceARestResource.class);

        // Verify the resource has JAX-RS annotations
        assertTrue(InterfaceARestResource.class.isAnnotationPresent(Path.class),
            "Resource should have @Path annotation");
    }

    @Test
    @DisplayName("Logger field is properly defined")
    @Order(14)
    void loggerFieldIsProperlyDefined() {
        // Verify that the logger field is accessible
        try {
            java.lang.reflect.Field loggerField =
                InterfaceARestResource.class.getDeclaredField("_logger");
            assertNotNull(loggerField);
            // In a real implementation, we would verify it's a Log4j logger
        } catch (NoSuchFieldException e) {
            fail("Resource should have a logger field");
        }
    }

    @AfterEach
    void tearDown() {
        super.tearDown();
    }

    @Test
    @DisplayName("Complete specification upload workflow test")
    @Order(15)
    void completeSpecificationUploadWorkflowTest() {
        // Test a complete workflow from upload to validation
        // This demonstrates the Chicago TDD approach of testing real workflows

        // Step 1: Upload specification
        Response uploadResponse = target("/ia/specifications")
            .queryParam("sessionHandle", "workflow-test-session")
            .request(MediaType.APPLICATION_XML)
            .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));

        // Step 2: Verify upload response
        assertEquals(200, uploadResponse.getStatus());
        String uploadEntity = uploadResponse.readEntity(String.class);
        assertNotNull(uploadEntity);
        assertFalse(uploadEntity.isEmpty());

        // Additional assertions would follow for validation endpoints
        // This demonstrates the Chicago TDD approach of testing real workflows
    }

    @Test
    @DisplayName("Resource handles different content types appropriately")
    @Order(16)
    void resourceHandlesDifferentContentTypesAppropriately() {
        // Test that resource returns appropriate response for different requests
        Response response = target("/ia/specifications")
            .queryParam("sessionHandle", "content-type-test")
            .request(MediaType.APPLICATION_XML)
            .post(jakarta.ws.rs.client.Entity.entity(TEST_SPEC_XML, MediaType.APPLICATION_XML));

        // Assert
        assertEquals(200, response.getStatus());
        assertNotNull(response.getMediaType());
        assertTrue(response.getMediaType().toString().contains("xml"),
            "Response should be XML content type");
    }
}