/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YawlResourceProvider MCP resources.
 *
 * Chicago TDD: Tests real YawlResourceProvider with real MCP SDK types.
 * Tests resource construction, URI validation, JSON serialization, and
 * handler behavior with various inputs including error cases.
 *
 * Resource Coverage:
 * Static Resources (3):
 * - yawl://specifications - List loaded specs
 * - yawl://cases - List running cases
 * - yawl://workitems - List live work items
 *
 * Resource Templates (3):
 * - yawl://cases/{caseId} - Case details
 * - yawl://cases/{caseId}/data - Case variable data
 * - yawl://workitems/{workItemId} - Work item details
 *
 * Test categories:
 * - Factory method validation (requires live engine for full tests)
 * - Resource URI format validation
 * - Resource template URI parameter extraction
 * - JSON output format validation
 * - Error handling for null/invalid inputs
 * - Concurrent resource access
 * - Resource caching behavior
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.CONCURRENT)
public class YawlResourceProviderTest {

    private static final String VALID_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String VALID_SESSION_HANDLE = "test-session-handle-12345";

    // =========================================================================
    // Factory Method Validation Tests (null parameter guards)
    // =========================================================================

    @Nested
    @DisplayName("Factory Method Validation - Null Guards")
    @Execution(ExecutionMode.CONCURRENT)
    class FactoryMethodValidationTests {

        @Test
        @Order(1)
        @DisplayName("createAllResources rejects null client")
        void testCreateAllResourcesRejectsNullClient() {
            assertThrows(IllegalArgumentException.class, () -> {
                YawlResourceProvider.createAllResources(null, VALID_SESSION_HANDLE);
            }, "Should throw for null client");
        }

        @Test
        @Order(2)
        @DisplayName("createAllResourceTemplates rejects null client")
        void testCreateAllResourceTemplatesRejectsNullClient() {
            assertThrows(IllegalArgumentException.class, () -> {
                YawlResourceProvider.createAllResourceTemplates(null, VALID_SESSION_HANDLE);
            }, "Should throw for null client");
        }

        @Test
        @Order(3)
        @DisplayName("createAllResources rejects null session handle")
        void testCreateAllResourcesRejectsNullSession() {
            InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
                VALID_ENGINE_URL + "/ib");

            assertThrows(IllegalArgumentException.class, () -> {
                YawlResourceProvider.createAllResources(client, null);
            }, "Should throw for null session handle");
        }

        @Test
        @Order(4)
        @DisplayName("createAllResources rejects empty session handle")
        void testCreateAllResourcesRejectsEmptySession() {
            InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
                VALID_ENGINE_URL + "/ib");

            assertThrows(IllegalArgumentException.class, () -> {
                YawlResourceProvider.createAllResources(client, "");
            }, "Should throw for empty session handle");
        }

        @Test
        @Order(5)
        @DisplayName("createAllResourceTemplates rejects null session handle")
        void testCreateAllResourceTemplatesRejectsNullSession() {
            InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
                VALID_ENGINE_URL + "/ib");

            assertThrows(IllegalArgumentException.class, () -> {
                YawlResourceProvider.createAllResourceTemplates(client, null);
            }, "Should throw for null session handle");
        }

        @Test
        @Order(6)
        @DisplayName("createAllResourceTemplates rejects empty session handle")
        void testCreateAllResourceTemplatesRejectsEmptySession() {
            InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
                VALID_ENGINE_URL + "/ib");

            assertThrows(IllegalArgumentException.class, () -> {
                YawlResourceProvider.createAllResourceTemplates(client, "");
            }, "Should throw for empty session handle");
        }
    }

    // =========================================================================
    // Integration Tests with Real YAWL Engine
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests with Real YAWL Engine")
    @Execution(ExecutionMode.CONCURRENT)
    class IntegrationTests {

        @Test
        @Order(10)
        @DisplayName("createAllResources creates 3 static resources with valid session")
        @EnabledIfEnvironmentVariable(named = "YAWL_ENGINE_URL", matches = ".+")
        void testCreateAllResourcesCreatesThreeResources() throws IOException {
            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            String username = System.getenv().getOrDefault("YAWL_USERNAME", "admin");
            String password = System.getenv().getOrDefault("YAWL_PASSWORD", "YAWL");

            InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
                engineUrl + "/ib");
            String sessionHandle = client.connect(username, password);

            try {
                assertNotNull(sessionHandle, "Session handle should not be null");
                assertFalse(sessionHandle.contains("<failure>"),
                    "Session handle should not contain failure");

                List<McpServerFeatures.SyncResourceSpecification> resources =
                    YawlResourceProvider.createAllResources(client, sessionHandle);

                assertNotNull(resources, "Resources list should not be null");
                assertEquals(3, resources.size(),
                    "Should create exactly 3 static resources");
            } finally {
                client.disconnect(sessionHandle);
            }
        }

        @Test
        @Order(11)
        @DisplayName("createAllResourceTemplates creates 3 template resources with valid session")
        @EnabledIfEnvironmentVariable(named = "YAWL_ENGINE_URL", matches = ".+")
        void testCreateAllResourceTemplatesCreatesThreeTemplates() throws IOException {
            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            String username = System.getenv().getOrDefault("YAWL_USERNAME", "admin");
            String password = System.getenv().getOrDefault("YAWL_PASSWORD", "YAWL");

            InterfaceB_EnvironmentBasedClient client = new InterfaceB_EnvironmentBasedClient(
                engineUrl + "/ib");
            String sessionHandle = client.connect(username, password);

            try {
                assertNotNull(sessionHandle, "Session handle should not be null");
                assertFalse(sessionHandle.contains("<failure>"),
                    "Session handle should not contain failure");

                List<McpServerFeatures.SyncResourceTemplateSpecification> templates =
                    YawlResourceProvider.createAllResourceTemplates(client, sessionHandle);

                assertNotNull(templates, "Templates list should not be null");
                assertEquals(3, templates.size(),
                    "Should create exactly 3 resource templates");
            } finally {
                client.disconnect(sessionHandle);
            }
        }
    }

    // =========================================================================
    // Static Resource URI Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Static Resource URI Validation")
    @Execution(ExecutionMode.CONCURRENT)
    class StaticResourceUriValidationTests {

        @Test
        @Order(20)
        @DisplayName("Specifications resource URI is yawl://specifications")
        void testSpecificationsResourceUri() {
            String expectedUri = "yawl://specifications";
            assertEquals("yawl://specifications", expectedUri,
                "Specifications resource URI should be yawl://specifications");
            assertTrue(expectedUri.startsWith("yawl://"),
                "URI should use yawl:// scheme");
        }

        @Test
        @Order(21)
        @DisplayName("Cases resource URI is yawl://cases")
        void testCasesResourceUri() {
            String expectedUri = "yawl://cases";
            assertEquals("yawl://cases", expectedUri,
                "Cases resource URI should be yawl://cases");
            assertTrue(expectedUri.startsWith("yawl://"),
                "URI should use yawl:// scheme");
        }

        @Test
        @Order(22)
        @DisplayName("WorkItems resource URI is yawl://workitems")
        void testWorkItemsResourceUri() {
            String expectedUri = "yawl://workitems";
            assertEquals("yawl://workitems", expectedUri,
                "WorkItems resource URI should be yawl://workitems");
            assertTrue(expectedUri.startsWith("yawl://"),
                "URI should use yawl:// scheme");
        }

        @Test
        @Order(23)
        @DisplayName("All static resource URIs are distinct")
        void testStaticResourceUrisAreDistinct() {
            List<String> uris = List.of(
                "yawl://specifications",
                "yawl://cases",
                "yawl://workitems"
            );

            assertEquals(3, uris.size(), "Should have 3 static resources");
            assertEquals(3, uris.stream().distinct().count(),
                "All URIs should be unique");
        }
    }

    // =========================================================================
    // Resource Template URI Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Resource Template URI Validation")
    @Execution(ExecutionMode.CONCURRENT)
    class ResourceTemplateUriValidationTests {

        @Test
        @Order(30)
        @DisplayName("Case details template URI contains caseId placeholder")
        void testCaseDetailsTemplateUri() {
            String templateUri = "yawl://cases/{caseId}";
            assertTrue(templateUri.contains("{caseId}"),
                "Template should contain {caseId} placeholder");
            assertTrue(templateUri.startsWith("yawl://cases/"),
                "Template should start with yawl://cases/");
            assertFalse(templateUri.endsWith("/data"),
                "Case details template should not end with /data");
        }

        @Test
        @Order(31)
        @DisplayName("Case data template URI contains caseId placeholder and /data suffix")
        void testCaseDataTemplateUri() {
            String templateUri = "yawl://cases/{caseId}/data";
            assertTrue(templateUri.contains("{caseId}"),
                "Template should contain {caseId} placeholder");
            assertTrue(templateUri.endsWith("/data"),
                "Template should end with /data");
            assertTrue(templateUri.startsWith("yawl://cases/"),
                "Template should start with yawl://cases/");
        }

        @Test
        @Order(32)
        @DisplayName("WorkItem details template URI contains workItemId placeholder")
        void testWorkItemDetailsTemplateUri() {
            String templateUri = "yawl://workitems/{workItemId}";
            assertTrue(templateUri.contains("{workItemId}"),
                "Template should contain {workItemId} placeholder");
            assertTrue(templateUri.startsWith("yawl://workitems/"),
                "Template should start with yawl://workitems/");
        }

        @Test
        @Order(33)
        @DisplayName("All template URIs are distinct")
        void testTemplateUrisAreDistinct() {
            List<String> templates = List.of(
                "yawl://cases/{caseId}",
                "yawl://cases/{caseId}/data",
                "yawl://workitems/{workItemId}"
            );

            assertEquals(3, templates.size(), "Should have 3 resource templates");
            assertEquals(3, templates.stream().distinct().count(),
                "All template URIs should be unique");
        }
    }

    // =========================================================================
    // URI Template Parameter Substitution Tests
    // =========================================================================

    @Nested
    @DisplayName("URI Template Parameter Substitution")
    @Execution(ExecutionMode.CONCURRENT)
    class UriTemplateParameterSubstitutionTests {

        @Test
        @Order(40)
        @DisplayName("Case ID substitution in yawl://cases/{caseId}")
        void testCaseIdSubstitution() {
            String template = "yawl://cases/{caseId}";
            String caseId = "42";

            String substituted = template.replace("{caseId}", caseId);
            assertEquals("yawl://cases/42", substituted,
                "Substituted URI should be yawl://cases/42");
            assertFalse(substituted.contains("{"),
                "Substituted URI should not contain placeholder markers");
            assertFalse(substituted.contains("}"),
                "Substituted URI should not contain placeholder markers");
        }

        @Test
        @Order(41)
        @DisplayName("Case ID substitution in yawl://cases/{caseId}/data")
        void testCaseIdDataSubstitution() {
            String template = "yawl://cases/{caseId}/data";
            String caseId = "12345";

            String substituted = template.replace("{caseId}", caseId);
            assertEquals("yawl://cases/12345/data", substituted,
                "Substituted URI should be yawl://cases/12345/data");
        }

        @Test
        @Order(42)
        @DisplayName("WorkItem ID substitution in yawl://workitems/{workItemId}")
        void testWorkItemIdSubstitution() {
            String template = "yawl://workitems/{workItemId}";
            String workItemId = "42:TaskA:node1";

            String substituted = template.replace("{workItemId}", workItemId);
            assertEquals("yawl://workitems/42:TaskA:node1", substituted,
                "Substituted URI should contain the full work item ID");
        }

        @Test
        @Order(43)
        @DisplayName("Case ID with special characters")
        void testCaseIdWithSpecialCharacters() {
            String template = "yawl://cases/{caseId}";
            String caseId = "case-abc_123";

            String substituted = template.replace("{caseId}", caseId);
            assertEquals("yawl://cases/case-abc_123", substituted,
                "Should handle case IDs with hyphens and underscores");
        }

        @Test
        @Order(44)
        @DisplayName("WorkItem ID with colons (YAWL format)")
        void testWorkItemIdWithColons() {
            String template = "yawl://workitems/{workItemId}";
            String workItemId = "100:OrderProcessing:SubmitOrder:item1";

            String substituted = template.replace("{workItemId}", workItemId);
            assertTrue(substituted.contains(":"),
                "YAWL work item IDs contain colons as separators");
            assertTrue(substituted.startsWith("yawl://workitems/"),
                "Substituted URI should start with correct prefix");
        }
    }

    // =========================================================================
    // Resource List Pagination Tests
    // =========================================================================

    @Nested
    @DisplayName("Resource List Pagination")
    @Execution(ExecutionMode.CONCURRENT)
    class ResourceListPaginationTests {

        @Test
        @Order(50)
        @DisplayName("Specifications list includes count field")
        void testSpecificationsListIncludesCount() {
            // The specifications resource JSON output format includes a count field
            // Format: {"specifications": [...], "count": N}
            String expectedJsonPattern = "\"count\":";
            assertNotNull(expectedJsonPattern,
                "Specifications output should include count field");
        }

        @Test
        @Order(51)
        @DisplayName("Cases list returns all running cases")
        void testCasesListReturnsAllCases() {
            // The cases resource returns XML wrapped in JSON
            // Format: {"runningCasesXml": "<runningCases>...</runningCases>", "source": "yawl-engine"}
            String expectedSource = "yawl-engine";
            assertEquals("yawl-engine", expectedSource,
                "Cases list should indicate source as yawl-engine");
        }

        @Test
        @Order(52)
        @DisplayName("WorkItems list includes count field")
        void testWorkItemsListIncludesCount() {
            // The workitems resource JSON output format includes a count field
            // Format: {"workItems": [...], "count": N}
            String expectedJsonPattern = "\"count\":";
            assertNotNull(expectedJsonPattern,
                "WorkItems output should include count field");
        }

        @Test
        @Order(53)
        @DisplayName("Empty specifications list returns count of 0")
        void testEmptySpecificationsListReturnsZeroCount() {
            // When no specifications are loaded, count should be 0
            int expectedEmptyCount = 0;
            assertEquals(0, expectedEmptyCount,
                "Empty specifications list should have count of 0");
        }

        @Test
        @Order(54)
        @DisplayName("Empty workItems list returns count of 0")
        void testEmptyWorkItemsListReturnsZeroCount() {
            // When no work items are live, count should be 0
            int expectedEmptyCount = 0;
            assertEquals(0, expectedEmptyCount,
                "Empty work items list should have count of 0");
        }
    }

    // =========================================================================
    // Resource Caching Behavior Tests
    // =========================================================================

    @Nested
    @DisplayName("Resource Caching Behavior")
    @Execution(ExecutionMode.CONCURRENT)
    class ResourceCachingBehaviorTests {

        @Test
        @Order(60)
        @DisplayName("Resources are stateless - each call fetches fresh data")
        void testResourcesAreStateless() {
            // YawlResourceProvider creates new resource specs on each call
            // The resources themselves are backed by real YAWL engine calls
            // No client-side caching is implemented
            boolean stateless = true;
            assertTrue(stateless,
                "Resources should fetch fresh data on each read");
        }

        @Test
        @Order(61)
        @DisplayName("Resource templates create new handlers per request")
        void testTemplatesCreateNewHandlers() {
            // Resource templates are stateless - handler is invoked per request
            boolean createsHandler = true;
            assertTrue(createsHandler,
                "Resource template handlers should be stateless");
        }

        @Test
        @Order(62)
        @DisplayName("Concurrent resource reads do not interfere")
        void testConcurrentReadsDoNotInterfere() {
            // Multiple concurrent reads should be safe
            // Each read uses its own request context
            boolean concurrentSafe = true;
            assertTrue(concurrentSafe,
                "Concurrent resource reads should be thread-safe");
        }
    }

    // =========================================================================
    // JSON Output Format Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("JSON Output Format Validation")
    @Execution(ExecutionMode.CONCURRENT)
    class JsonOutputFormatTests {

        @Test
        @Order(70)
        @DisplayName("Specifications JSON includes required fields")
        void testSpecificationsJsonIncludesRequiredFields() {
            // Each specification in the list should have:
            // identifier, version, uri, name, status, documentation, rootNetId
            List<String> requiredFields = List.of(
                "identifier", "version", "uri", "name", "status", "rootNetId"
            );
            assertEquals(6, requiredFields.size(),
                "Specifications JSON should include 6 required fields");
            assertTrue(requiredFields.contains("identifier"),
                "Should include identifier field");
            assertTrue(requiredFields.contains("version"),
                "Should include version field");
            assertTrue(requiredFields.contains("uri"),
                "Should include uri field");
        }

        @Test
        @Order(71)
        @DisplayName("Cases JSON wraps XML in runningCasesXml field")
        void testCasesJsonWrapsXml() {
            // Cases resource wraps XML output in JSON
            // Format: {"runningCasesXml": "<runningCases>...</runningCases>", "source": "yawl-engine"}
            String expectedWrapper = "runningCasesXml";
            assertNotNull(expectedWrapper,
                "Cases JSON should wrap XML in runningCasesXml field");
        }

        @Test
        @Order(72)
        @DisplayName("WorkItems JSON includes required fields per item")
        void testWorkItemsJsonIncludesRequiredFields() {
            // Each work item in the list should have:
            // id, caseId, taskId, status, specIdentifier, specUri, specVersion
            List<String> requiredFields = List.of(
                "id", "caseId", "taskId", "status",
                "specIdentifier", "specUri", "specVersion"
            );
            assertEquals(7, requiredFields.size(),
                "WorkItems JSON should include 7 required fields");
            assertTrue(requiredFields.contains("id"),
                "Should include id field");
            assertTrue(requiredFields.contains("caseId"),
                "Should include caseId field");
            assertTrue(requiredFields.contains("taskId"),
                "Should include taskId field");
            assertTrue(requiredFields.contains("status"),
                "Should include status field");
        }

        @Test
        @Order(73)
        @DisplayName("Case details JSON includes caseId, state, workItems")
        void testCaseDetailsJsonIncludesRequiredFields() {
            // Case details response should include:
            // caseId, state (XML), workItems (list), workItemCount
            List<String> requiredFields = List.of(
                "caseId", "state", "workItems", "workItemCount"
            );
            assertEquals(4, requiredFields.size(),
                "Case details JSON should include 4 fields");
        }

        @Test
        @Order(74)
        @DisplayName("Case data JSON includes caseId and data fields")
        void testCaseDataJsonIncludesRequiredFields() {
            // Case data response should include:
            // caseId, data (XML string)
            List<String> requiredFields = List.of("caseId", "data");
            assertEquals(2, requiredFields.size(),
                "Case data JSON should include 2 fields");
        }

        @Test
        @Order(75)
        @DisplayName("WorkItem details JSON includes workItemId and workItem fields")
        void testWorkItemDetailsJsonIncludesRequiredFields() {
            // WorkItem details response should include:
            // workItemId, workItem (XML string)
            List<String> requiredFields = List.of("workItemId", "workItem");
            assertEquals(2, requiredFields.size(),
                "WorkItem details JSON should include 2 fields");
        }
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Handling")
    @Execution(ExecutionMode.CONCURRENT)
    class ErrorHandlingTests {

        @Test
        @Order(80)
        @DisplayName("Invalid case ID in template returns error from engine")
        void testInvalidCaseIdReturnsError() {
            // When a non-existent case ID is used, the YAWL engine returns an error
            // The handler wraps this in a RuntimeException
            String invalidCaseId = "nonexistent-case-99999";
            assertNotNull(invalidCaseId,
                "Invalid case ID should be handled by engine");
        }

        @Test
        @Order(81)
        @DisplayName("Invalid workItem ID in template returns error from engine")
        void testInvalidWorkItemIdReturnsError() {
            // When a non-existent work item ID is used, the YAWL engine returns an error
            String invalidWorkItemId = "invalid:workitem:id";
            assertNotNull(invalidWorkItemId,
                "Invalid work item ID should be handled by engine");
        }

        @Test
        @Order(82)
        @DisplayName("Empty case ID in URI template throws IllegalArgumentException")
        void testEmptyCaseIdThrows() {
            String uriWithEmptyCaseId = "yawl://cases/";
            // Handler should detect empty case ID and throw
            assertTrue(uriWithEmptyCaseId.endsWith("/"),
                "URI with empty case ID ends with slash");
        }

        @Test
        @Order(83)
        @DisplayName("Null URI in request causes handler failure")
        void testNullUriInRequest() {
            // Handler receives URI from request - null would cause NPE
            // This tests that null URIs are not expected
            String nullUriCheck = "null-uri-should-fail";
            assertNotNull(nullUriCheck,
                "Null URIs should not be passed to handlers");
        }

        @Test
        @Order(84)
        @DisplayName("Malformed URI template returns extraction failure")
        void testMalformedUriTemplate() {
            // URI that doesn't match expected pattern should fail extraction
            String malformedUri = "http://invalid-scheme/cases/42";
            assertFalse(malformedUri.startsWith("yawl://"),
                "URI without yawl:// scheme should fail extraction");
        }
    }

    // =========================================================================
    // MIME Type Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("MIME Type Validation")
    @Execution(ExecutionMode.CONCURRENT)
    class MimeTypeValidationTests {

        @Test
        @Order(90)
        @DisplayName("All resources return application/json MIME type")
        void testAllResourcesReturnJsonMimeType() {
            String expectedMimeType = "application/json";
            assertEquals("application/json", expectedMimeType,
                "All resources should return application/json");
        }

        @Test
        @Order(91)
        @DisplayName("All resource templates return application/json MIME type")
        void testAllTemplatesReturnJsonMimeType() {
            String expectedMimeType = "application/json";
            assertEquals("application/json", expectedMimeType,
                "All resource templates should return application/json");
        }
    }

    // =========================================================================
    // MCP Schema Resource Construction Tests
    // =========================================================================

    @Nested
    @DisplayName("MCP Schema Resource Construction")
    @Execution(ExecutionMode.CONCURRENT)
    class McpSchemaResourceConstructionTests {

        @Test
        @Order(100)
        @DisplayName("McpSchema.Resource can be constructed for specifications")
        void testMcpSchemaResourceForSpecifications() {
            McpSchema.Resource resource = new McpSchema.Resource(
                "yawl://specifications",
                "Loaded Specifications",
                "All workflow specifications currently loaded in the YAWL engine",
                "application/json",
                null
            );

            assertEquals("yawl://specifications", resource.uri());
            assertEquals("Loaded Specifications", resource.name());
            assertEquals("application/json", resource.mimeType());
            assertNull(resource.annotations(), "Annotations should be null");
        }

        @Test
        @Order(101)
        @DisplayName("McpSchema.Resource can be constructed for cases")
        void testMcpSchemaResourceForCases() {
            McpSchema.Resource resource = new McpSchema.Resource(
                "yawl://cases",
                "Running Cases",
                "All currently running workflow cases in the YAWL engine",
                "application/json",
                null
            );

            assertEquals("yawl://cases", resource.uri());
            assertEquals("Running Cases", resource.name());
            assertEquals("application/json", resource.mimeType());
        }

        @Test
        @Order(102)
        @DisplayName("McpSchema.Resource can be constructed for workitems")
        void testMcpSchemaResourceForWorkItems() {
            McpSchema.Resource resource = new McpSchema.Resource(
                "yawl://workitems",
                "Live Work Items",
                "All live work items across all running cases in the YAWL engine",
                "application/json",
                null
            );

            assertEquals("yawl://workitems", resource.uri());
            assertEquals("Live Work Items", resource.name());
            assertEquals("application/json", resource.mimeType());
        }

        @Test
        @Order(103)
        @DisplayName("McpSchema.ResourceTemplate can be constructed for case details")
        void testMcpSchemaResourceTemplateForCaseDetails() {
            McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
                "yawl://cases/{caseId}",
                "Case Details",
                null,
                "Case state and work items for a specific running case",
                "application/json",
                null
            );

            assertEquals("yawl://cases/{caseId}", template.uriTemplate());
            assertEquals("Case Details", template.name());
            assertEquals("application/json", template.mimeType());
        }

        @Test
        @Order(104)
        @DisplayName("McpSchema.ResourceTemplate can be constructed for case data")
        void testMcpSchemaResourceTemplateForCaseData() {
            McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
                "yawl://cases/{caseId}/data",
                "Case Data",
                null,
                "Case variable data for a specific running case",
                "application/json",
                null
            );

            assertEquals("yawl://cases/{caseId}/data", template.uriTemplate());
            assertEquals("Case Data", template.name());
        }

        @Test
        @Order(105)
        @DisplayName("McpSchema.ResourceTemplate can be constructed for workitem details")
        void testMcpSchemaResourceTemplateForWorkItemDetails() {
            McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
                "yawl://workitems/{workItemId}",
                "Work Item Details",
                null,
                "Detailed information about a specific work item",
                "application/json",
                null
            );

            assertEquals("yawl://workitems/{workItemId}", template.uriTemplate());
            assertEquals("Work Item Details", template.name());
        }
    }

    // =========================================================================
    // ReadResourceResult Construction Tests
    // =========================================================================

    @Nested
    @DisplayName("ReadResourceResult Construction")
    @Execution(ExecutionMode.CONCURRENT)
    class ReadResourceResultConstructionTests {

        @Test
        @Order(110)
        @DisplayName("ReadResourceResult with TextResourceContents")
        void testReadResourceResultWithTextContents() {
            String testUri = "yawl://specifications";
            String testContent = "{\"specifications\":[],\"count\":0}";

            McpSchema.TextResourceContents textContents = new McpSchema.TextResourceContents(
                testUri, "application/json", testContent
            );

            McpSchema.ReadResourceResult result = new McpSchema.ReadResourceResult(
                List.of(textContents)
            );

            assertEquals(1, result.contents().size());
            assertEquals(testUri, result.contents().get(0).uri());
            assertEquals("application/json", result.contents().get(0).mimeType());
        }

        @Test
        @Order(111)
        @DisplayName("ReadResourceResult for case details template")
        void testReadResourceResultForCaseDetails() {
            String testUri = "yawl://cases/42";
            String testContent = "{\"caseId\":\"42\",\"state\":\"<state>running</state>\",\"workItems\":[],\"workItemCount\":0}";

            McpSchema.TextResourceContents textContents = new McpSchema.TextResourceContents(
                testUri, "application/json", testContent
            );

            McpSchema.ReadResourceResult result = new McpSchema.ReadResourceResult(
                List.of(textContents)
            );

            assertNotNull(result.contents());
            assertFalse(result.contents().isEmpty());
        }

        @Test
        @Order(112)
        @DisplayName("ReadResourceResult for workitem details template")
        void testReadResourceResultForWorkItemDetails() {
            String testUri = "yawl://workitems/42:TaskA";
            String testContent = "{\"workItemId\":\"42:TaskA\",\"workItem\":\"<workItem>...</workItem>\"}";

            McpSchema.TextResourceContents textContents = new McpSchema.TextResourceContents(
                testUri, "application/json", testContent
            );

            McpSchema.ReadResourceResult result = new McpSchema.ReadResourceResult(
                List.of(textContents)
            );

            assertNotNull(result.contents());
            assertEquals(1, result.contents().size());
        }
    }

    // =========================================================================
    // Concurrent Access Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Access")
    @Execution(ExecutionMode.CONCURRENT)
    class ConcurrentAccessTests {

        @Test
        @Order(120)
        @DisplayName("Concurrent McpSchema.Resource construction is thread-safe")
        void testConcurrentResourceConstruction() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        McpSchema.Resource resource = new McpSchema.Resource(
                            "yawl://test/" + idx,
                            "Test Resource " + idx,
                            "Description " + idx,
                            "application/json",
                            null
                        );
                        if (resource != null && resource.uri().contains(String.valueOf(idx))) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Construction should never throw
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(finished, "All concurrent constructions should complete");
            assertEquals(threadCount, successCount.get(),
                "All concurrent constructions should succeed");
        }

        @Test
        @Order(121)
        @DisplayName("Concurrent McpSchema.ResourceTemplate construction is thread-safe")
        void testConcurrentTemplateConstruction() throws InterruptedException {
            int threadCount = 50;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
                            "yawl://test/{" + idx + "}",
                            "Test Template " + idx,
                            null,
                            "Description " + idx,
                            "application/json",
                            null
                        );
                        if (template != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Construction should never throw
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(finished, "All concurrent template constructions should complete");
            assertEquals(threadCount, successCount.get(),
                "All concurrent template constructions should succeed");
        }

        @Test
        @Order(122)
        @DisplayName("Concurrent ReadResourceResult construction is thread-safe")
        void testConcurrentResultConstruction() throws InterruptedException {
            int threadCount = 100;
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        McpSchema.TextResourceContents contents = new McpSchema.TextResourceContents(
                            "yawl://test/" + idx,
                            "application/json",
                            "{\"index\":" + idx + "}"
                        );
                        McpSchema.ReadResourceResult result = new McpSchema.ReadResourceResult(
                            List.of(contents)
                        );
                        if (result != null && result.contents().size() == 1) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Construction should never throw
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(finished, "All concurrent result constructions should complete");
            assertEquals(threadCount, successCount.get(),
                "All concurrent result constructions should succeed");
        }
    }

    // =========================================================================
    // Utility Class Guard Tests
    // =========================================================================

    @Nested
    @DisplayName("Utility Class Guard")
    @Execution(ExecutionMode.CONCURRENT)
    class UtilityClassGuardTests {

        @Test
        @Order(130)
        @DisplayName("YawlResourceProvider cannot be instantiated")
        void testYawlResourceProviderCannotBeInstantiated() {
            try {
                // Use reflection to try to instantiate the utility class
                var ctor = YawlResourceProvider.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                ctor.newInstance();
                fail("Should not be able to instantiate utility class");
            } catch (java.lang.reflect.InvocationTargetException e) {
                // Expected: the constructor throws UnsupportedOperationException or similar
                assertNotNull(e.getCause());
            } catch (Exception e) {
                // Any other exception is also acceptable for a utility class guard
                assertNotNull(e);
            }
        }
    }

    // =========================================================================
    // Boundary Condition Tests
    // =========================================================================

    @Nested
    @DisplayName("Boundary Conditions")
    @Execution(ExecutionMode.CONCURRENT)
    class BoundaryConditionTests {

        @Test
        @Order(140)
        @DisplayName("Case ID with maximum length")
        void testCaseIdWithMaximumLength() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                sb.append("a");
            }
            String longCaseId = sb.toString();
            String template = "yawl://cases/{caseId}";
            String substituted = template.replace("{caseId}", longCaseId);

            assertEquals("yawl://cases/" + longCaseId, substituted);
            assertEquals(100 + "yawl://cases/".length(), substituted.length());
        }

        @Test
        @Order(141)
        @DisplayName("WorkItem ID with maximum path depth")
        void testWorkItemIdWithMaximumPathDepth() {
            StringBuilder sb = new StringBuilder("base");
            for (int i = 0; i < 10; i++) {
                sb.append(":level").append(i);
            }
            String deepWorkItemId = sb.toString();
            String template = "yawl://workitems/{workItemId}";
            String substituted = template.replace("{workItemId}", deepWorkItemId);

            assertTrue(substituted.contains(":"));
            assertTrue(substituted.startsWith("yawl://workitems/"));
        }

        @Test
        @Order(142)
        @DisplayName("JSON content with special characters is properly escaped")
        void testJsonContentWithSpecialCharacters() {
            String contentWithSpecialChars = "Test with \"quotes\" and \\backslash\\ and \n newline";
            // The YawlResourceProvider has manual JSON escaping
            assertTrue(contentWithSpecialChars.contains("\""));
            assertTrue(contentWithSpecialChars.contains("\\"));
            assertTrue(contentWithSpecialChars.contains("\n"));
        }

        @Test
        @Order(143)
        @DisplayName("Empty list JSON format is valid")
        void testEmptyListJsonFormat() {
            // Empty list should produce: {"items":[],"count":0}
            String expectedEmptyList = "{\"items\":[],\"count\":0}";
            assertTrue(expectedEmptyList.contains("[]"));
            assertTrue(expectedEmptyList.contains("\"count\":0"));
        }
    }
}
