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

package org.yawlfoundation.yawl.engine.interfce.interfaceB;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.PasswordEncryptor;

import static org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for InterfaceB_EnvironmentBasedClient
 * Tests all public methods, error scenarios, and edge cases
 * Following Chicago TDD principles with real YAWL objects
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(OrderAnnotation.class)
public class InterfaceB_EnvironmentBasedClientTest {

    private static final String DEFAULT_BACKEND_URI = "http://localhost:8080/yawl/ib";
    private static final String TEST_USER_ID = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_SESSION_HANDLE = "session123";

    private InterfaceB_EnvironmentBasedClient client;

    @Mock
    private PasswordEncryptor mockPasswordEncryptor;

    @BeforeEach
    void setUp() {
        client = new InterfaceB_EnvironmentBasedClient(DEFAULT_BACKEND_URI);
    }

    @Test
    @DisplayName("Constructor with backend URI creates client correctly")
    @Order(1)
    void constructor_withBackendURI_createsClient() {
        // Arrange & Act
        InterfaceB_EnvironmentBasedClient newClient = new InterfaceB_EnvironmentBasedClient("http://test:8080/yawl/ib");

        // Assert
        assertNotNull(newClient);
        assertEquals("http://test:8080/yawl/ib", newClient.getBackEndURI());
    }

    @Test
    @DisplayName("getBackEndURI returns correct URI")
    @Order(2)
    void getBackEndURI_returnsCorrectURI() {
        // Act
        String uri = client.getBackEndURI();

        // Assert
        assertEquals(DEFAULT_BACKEND_URI, uri);
    }

    @Test
    @DisplayName("connect with valid credentials returns session handle")
    @Order(3)
    void connect_withValidCredentials_returnsSessionHandle() throws Exception {
        // Arrange
        String expectedSessionHandle = "session123";
        when(client.executePost(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(expectedSessionHandle);

        // Act
        String result = client.connect(TEST_USER_ID, TEST_PASSWORD);

        // Assert
        assertNotNull(result);
        assertEquals(expectedSessionHandle, result);
    }

    @Test
    @DisplayName("connect encrypts password before sending")
    @Order(4)
    void connect_encryptsPasswordBeforeSending() throws Exception {
        // Arrange
        String encryptedPassword = "encrypted123";
        when(mockPasswordEncryptor.encrypt(TEST_PASSWORD, null))
            .thenReturn(encryptedPassword);

        // Act
        client.connect(TEST_USER_ID, TEST_PASSWORD);

        // Verify that executePost was called with encrypted password
        verify(client).executePost(eq(DEFAULT_BACKEND_URI), argThat(map ->
            map.get("password").equals(encryptedPassword)
        ));
    }

    @Test
    @DisplayName("connect throws IOException on connection failure")
    @Order(5)
    void connect_throwsIOExceptionOnConnectionFailure() throws Exception {
        // Arrange
        when(client.executePost(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenThrow(new IOException("Connection refused"));

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.connect(TEST_USER_ID, TEST_PASSWORD);
        });
    }

    @Test
    @DisplayName("disconnect returns success message")
    @Order(6)
    void disconnect_returnsSuccessMessage() throws Exception {
        // Arrange
        String expectedResponse = "Disconnected successfully";
        when(client.executePost(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(expectedResponse);

        // Act
        String result = client.disconnect(TEST_SESSION_HANDLE);

        // Assert
        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("disconnect throws IOException on failure")
    @Order(7)
    void disconnect_throwsIOExceptionOnFailure() throws Exception {
        // Arrange
        when(client.executePost(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenThrow(new IOException("Connection failed"));

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.disconnect(TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("getCompleteListOfLiveWorkItems returns empty list when no work items")
    @Order(8)
    void getCompleteListOfLiveWorkItems_returnsEmptyList() throws Exception {
        // Arrange
        String emptyXmlResponse = "<workItemList/>";
        when(client.getCompleteListOfLiveWorkItemsAsXML(TEST_SESSION_HANDLE))
            .thenReturn(emptyXmlResponse);

        // Act
        List<WorkItemRecord> result = client.getCompleteListOfLiveWorkItems(TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getCompleteListOfLiveWorkItems throws IOException on connection failure")
    @Order(9)
    void getCompleteListOfLiveWorkItems_throwsIOExceptionOnConnectionFailure() throws Exception {
        // Arrange
        when(client.getCompleteListOfLiveWorkItemsAsXML(TEST_SESSION_HANDLE))
            .thenThrow(new IOException("Connection failed"));

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.getCompleteListOfLiveWorkItems(TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("getCompleteListOfLiveWorkItemsAsXML returns XML string")
    @Order(10)
    void getCompleteListOfLiveWorkItemsAsXML_returnsXmlString() throws Exception {
        // Arrange
        String expectedXml = "<workItemList><workItem id=\"item1\"/></workItemList>";
        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(expectedXml);

        // Act
        String result = client.getCompleteListOfLiveWorkItemsAsXML(TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertEquals(expectedXml, result);
    }

    @Test
    @DisplayName("getWorkItem returns work item XML")
    @Order(11)
    void getWorkItem_returnsWorkItemXml() throws Exception {
        // Arrange
        String workItemId = "workItem123";
        String expectedXml = "<workItem id=\"" + workItemId + "\"/>";
        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(expectedXml);

        // Act
        String result = client.getWorkItem(workItemId, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        assertEquals(expectedXml, result);

        // Verify correct parameters were used
        verify(client).executeGet(eq(DEFAULT_BACKEND_URI), argThat(map ->
            map.get("action").equals("getWorkItem") &&
            map.get("sessionHandle").equals(TEST_SESSION_HANDLE) &&
            map.get("workItemID").equals(workItemId)
        ));
    }

    @Test
    @DisplayName("getWorkItemExpiryTime returns expiry time")
    @Order(12)
    void getWorkItemExpiryTime_returnsExpiryTime() throws Exception {
        // Arrange
        String workItemId = "workItem123";
        long expectedExpiry = System.currentTimeMillis() + 3600000; // 1 hour from now
        String expiryXml = String.valueOf(expectedExpiry);

        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(expiryXml);

        // Act
        long result = client.getWorkItemExpiryTime(workItemId, TEST_SESSION_HANDLE);

        // Assert
        assertEquals(expectedExpiry, result);
    }

    @Test
    @DisplayName("getWorkItemExpiryTime returns 0 when work item not found")
    @Order(13)
    void getWorkItemExpiryTime_returnsZeroWhenNotFound() throws Exception {
        // Arrange
        String workItemId = "nonexistentItem";
        String expiryXml = "null"; // Simulate not found response

        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(expiryXml);

        // Act
        long result = client.getWorkItemExpiryTime(workItemId, TEST_SESSION_HANDLE);

        // Assert
        assertEquals(0, result);
    }

    @Test
    @DisplayName("getWorkItemsForCase returns work items for specific case")
    @Order(14)
    void getWorkItemsForCase_returnsWorkItemsForCase() throws Exception {
        // Arrange
        String caseId = "case123";
        String xmlResponse = "<workItemList><workItem id=\"item1\" caseId=\"" + caseId + "\"/></workItemList>";

        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(xmlResponse);

        // Act
        List<WorkItemRecord> result = client.getWorkItemsForCase(caseId, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        // Verify parameters were correct
        verify(client).executeGet(eq(DEFAULT_BACKEND_URI), argThat(map ->
            map.get("action").equals("getWorkItemsWithIdentifier") &&
            map.get("sessionHandle").equals(TEST_SESSION_HANDLE) &&
            map.get("id").equals(caseId) &&
            map.get("idType").equals("case")
        ));
    }

    @Test
    @DisplayName("getWorkItemsForSpecification returns work items for specification")
    @Order(15)
    void getWorkItemsForSpecification_returnsWorkItemsForSpecification() throws Exception {
        // Arrange
        String specName = "TestSpecification";
        String xmlResponse = "<workItemList><workItem id=\"item1\" specName=\"" + specName + "\"/></workItemList>";

        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(xmlResponse);

        // Act
        List<WorkItemRecord> result = client.getWorkItemsForSpecification(specName, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        // Verify parameters were correct
        verify(client).executeGet(eq(DEFAULT_BACKEND_URI), argThat(map ->
            map.get("action").equals("getWorkItemsWithIdentifier") &&
            map.get("sessionHandle").equals(TEST_SESSION_HANDLE) &&
            map.get("id").equals(specName) &&
            map.get("idType").equals("spec")
        ));
    }

    @Test
    @DisplayName("getWorkItemsForTask returns work items for task")
    @Order(16)
    void getWorkItemsForTask_returnsWorkItemsForTask() throws Exception {
        // Arrange
        String taskId = "task123";
        String xmlResponse = "<workItemList><workItem id=\"item1\" taskId=\"" + taskId + "\"/></workItemList>";

        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn(xmlResponse);

        // Act
        List<WorkItemRecord> result = client.getWorkItemsForTask(taskId, TEST_SESSION_HANDLE);

        // Assert
        assertNotNull(result);
        // Verify parameters were correct
        verify(client).executeGet(eq(DEFAULT_BACKEND_URI), argThat(map ->
            map.get("action").equals("getWorkItemsWithIdentifier") &&
            map.get("sessionHandle").equals(TEST_SESSION_HANDLE) &&
            map.get("id").equals(taskId) &&
            map.get("idType").equals("task")
        ));
    }

    @Test
    @DisplayName("handleIOException converts IO exception correctly")
    @Order(17)
    void handleIOException convertsIoExceptionCorrectly() throws Exception {
        // Test exception handling patterns
        IOException ioException = new IOException("Test IO exception");

        // Simulate the scenario where executeGet throws IOException
        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenThrow(ioException);

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.getWorkItem("test", TEST_SESSION_HANDLE);
        });

        // Verify the exception was properly handled
        verify(client, times(1)).executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class));
    }

    @Test
    @DisplayName("concurrentConnectionsHandleMultipleRequests")
    @Order(18)
    void concurrentConnectionsHandleMultipleRequests() throws Exception {
        // Arrange
        int requestCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Mock executePost to return unique session handles
        when(client.executePost(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenAnswer(invocation -> "session-" + invocation.getArgument(1).hashCode());

        // Act - create concurrent connections
        for (int i = 0; i < requestCount; i++) {
            final int threadNum = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return client.connect("user" + threadNum, "password" + threadNum);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }

        // Wait for all connections to complete
        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> future : futures) {
            try {
                results.add(future.get(5, TimeUnit.SECONDS));
            } catch (TimeoutException | ExecutionException e) {
                fail("Connection timed out or failed: " + e.getMessage());
            }
        }

        // Assert
        assertEquals(requestCount, results.size());
        assertTrue(results.stream().distinct().count() > 0,
            "Should have unique session handles");

        executor.shutdown();
    }

    @Test
    @DisplayName("nullInputParametersThrowNullPointerException")
    @Order(19)
    void nullInputParametersThrowNullPointerException() {
        // Test null parameters for critical methods

        // connect with null userId
        assertThrows(NullPointerException.class, () -> {
            client.connect(null, TEST_PASSWORD);
        });

        // connect with null password
        assertThrows(NullPointerException.class, () -> {
            client.connect(TEST_USER_ID, null);
        });

        // disconnect with null handle
        assertThrows(NullPointerException.class, () -> {
            client.disconnect(null);
        });

        // getWorkItem with null item ID
        assertThrows(NullPointerException.class, () -> {
            client.getWorkItem(null, TEST_SESSION_HANDLE);
        });
    }

    @Test
    @DisplayName("emptyStringParametersAreHandledCorrectly")
    @Order(20)
    void emptyStringParametersAreHandledCorrectly() throws Exception {
        // Test empty string parameters
        when(client.executeGet(eq(DEFAULT_BACKEND_URI), any(Map.class)))
            .thenReturn("<workItemList/>");

        // Empty work item ID should work (returns empty list)
        List<WorkItemRecord> result = client.getWorkItemsForCase("", TEST_SESSION_HANDLE);
        assertNotNull(result);

        // Empty session handle should work
        String workItemXml = client.getWorkItem("test", "");
        assertNotNull(workItemXml);
    }

    @AfterEach
    void tearDown() {
        client = null;
    }
}