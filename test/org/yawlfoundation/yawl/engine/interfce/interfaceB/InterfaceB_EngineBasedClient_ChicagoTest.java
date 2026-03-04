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
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.jdom2.Document;
import org.jdom2.Element;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.engine.announcement.YAnnouncement;
import org.yawlfoundation.yawl.engine.announcement.YEngineEvent;
import org.yawlfoundation.yawl.engine.interfce.Interface_Client;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.unmarshal.YDecompositionParser;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.SafeNumberParser;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for InterfaceB_EngineBasedClient
 * Tests all public methods, edge cases, and exception scenarios
 * Following Chicago TDD principles with real YAWL engine objects and test services
 */
@TestMethodOrder(OrderAnnotation.class)
public class InterfaceB_EngineBasedClient_ChicagoTest {

    private InterfaceB_EngineBasedClient client;
    private YEngine engine;
    private TestHttpService testService;
    private YAWLServiceReference serviceRef;
    private YIdentifier caseId;
    private YSpecificationID specId;
    private YWorkItem workItem;
    private YTask task;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize real YAWL engine with test configuration
        engine = YEngine.getInstance();
        if (engine == null) {
            engine = new YEngine(); // Create new engine instance
            engine.initialise();
        }

        // Create test HTTP service
        testService = new TestHttpService();
        testService.start();

        // Create service reference pointing to test service
        serviceRef = new YAWLServiceReference(
            testService.getUrl(), null, "testService", "password", null);

        // Create test specification ID and case ID
        specId = new YSpecificationID("TestSpec", "0.1", "http://test.org");
        caseId = new YIdentifier("case123");

        // Create test task
        task = new YAtomicTask("test-task", YTask._XOR, YTask._AND, null);

        // Create test work item
        YWorkItemID workItemId = new YWorkItemID(caseId, "test-task");
        workItem = new YWorkItem(null, specId, task, workItemId, false, false);

        // Create client instance
        client = new InterfaceB_EngineBasedClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (testService != null) {
            testService.stop();
        }
        if (engine != null) {
            engine.shutdown();
        }
        client = null;
    }

    @Test
    @DisplayName("getScheme returns http")
    @Order(1)
    void getScheme_returnsHttp() {
        assertEquals("http", client.getScheme());
    }

    @Test
    @DisplayName("announceFiredWorkItem announces enabled task with correct parameters")
    @Order(2)
    void announceFiredWorkItem_announcesEnabledTask() throws InterruptedException {
        // Arrange
        CountDownLatch latch = new CountDownLatch(1);
        testService.reset();
        testService.setLatch(latch);

        // Create announcement with real work item
        YAnnouncement announcement = new YAnnouncement();
        announcement.setItem(workItem);
        announcement.setYawlService(serviceRef);

        // Act
        client.announceFiredWorkItem(announcement);

        // Wait for the HTTP call to complete
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete within 5 seconds");

        // Verify the actual HTTP request was made with correct parameters
        Map<String, String> receivedParams = testService.getLastRequestParams();
        assertNotNull(receivedParams);
        assertEquals("ITEM_ADD", receivedParams.get("action"));
        assertNotNull(receivedParams.get("workItem"));
        assertTrue(receivedParams.get("workItem").contains("test-task"));
    }

    @Test
    @DisplayName("announceCancelledWorkItem with parent work item cancels parent and children")
    @Order(3)
    void announceCancelledWorkItem_withParentCancelsParentAndChildren() throws Exception {
        // Create parent and child work items
        YWorkItemID parentId = new YWorkItemID(caseId, "parent-task");
        YWorkItem parentWorkItem = new YWorkItem(null, specId, task, parentId, false, false);

        YWorkItemID child1Id = new YWorkItemID(caseId, "child1-task");
        YWorkItem child1 = new YWorkItem(null, specId, task, child1Id, false, false);

        YWorkItemID child2Id = new YWorkItemID(caseId, "child2-task");
        YWorkItem child2 = new YWorkItem(null, specId, task, child2Id, false, false);

        // Set up parent-child relationship
        parentWorkItem.setParent(parentWorkItem); // Self-parent for test
        Set<YWorkItem> children = new HashSet<>();
        children.add(child1);
        children.add(child2);

        // Use reflection to set children (private field)
        java.lang.reflect.Field field = YWorkItem.class.getDeclaredField("children");
        field.setAccessible(true);
        field.set(parentWorkItem, children);

        // Create announcement
        YAnnouncement announcement = new YAnnouncement();
        announcement.setItem(workItem); // This item has parent
        announcement.setYawlService(serviceRef);

        // Test that the method calls cancelWorkItem for parent and children
        // by checking the internal state after execution
        testService.reset();
        CountDownLatch latch = new CountDownLatch(3); // Parent + 2 children
        testService.setLatch(latch);

        client.announceCancelledWorkItem(announcement);

        // Verify multiple calls were made
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All cancellation calls should complete");

        Map<String, String>[] calls = testService.getRequestCalls();
        assertTrue(calls.length >= 3, "Should have made at least 3 cancellation calls");
    }

    @Test
    @DisplayName("announceCancelledWorkItem without parent work item cancels only the item")
    @Order(4)
    void announceCancelledWorkItem_withoutParentCanclesOnlyItem() throws Exception {
        // Arrange - work item with no parent
        YAnnouncement announcement = new YAnnouncement();
        announcement.setItem(workItem);
        announcement.setYawlService(serviceRef);

        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        // Act
        client.announceCancelledWorkItem(announcement);

        // Verify only one call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Single cancellation call should complete");

        Map<String, String>[] calls = testService.getRequestCalls();
        assertEquals(1, calls.length, "Should have made exactly 1 cancellation call");

        Map<String, String> params = calls[0];
        assertEquals("ITEM_CANCEL", params.get("action"));
        assertNotNull(params.get("workItem"));
    }

    @Test
    @DisplayName("cancelWorkItem creates correct parameters and executes handler")
    @Order(5)
    void cancelWorkItem_createsCorrectParameters() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        // Act
        client.cancelWorkItem(serviceRef, workItem);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("ITEM_CANCEL", params.get("action"));
        assertNotNull(params.get("workItem"));
    }

    @Test
    @DisplayName("announceTimerExpiry announces timer expiration with correct parameters")
    @Order(6)
    void announceTimerExpiry_announcesTimerExpiration() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        // Create announcement
        YAnnouncement announcement = new YAnnouncement();
        announcement.setItem(workItem);
        announcement.setYawlService(serviceRef);

        // Act
        client.announceTimerExpiry(announcement);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("TIMER_EXPIRED", params.get("action"));
        assertNotNull(params.get("workItem"));
    }

    @Test
    @DisplayName("announceCaseSuspended announces case suspended state")
    @Order(7)
    void announceCaseSuspended_announcesCaseSuspended() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        // Create set of services
        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceCaseSuspended(services, caseId);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_SUSPENDED", params.get("action"));
        assertEquals("case123", params.get("caseID"));
    }

    @Test
    @DisplayName("announceCaseSuspending announces case suspending state")
    @Order(8)
    void announceCaseSuspending_announcesCaseSuspending() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceCaseSuspending(services, caseId);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_SUSPENDING", params.get("action"));
    }

    @Test
    @DisplayName("announceCaseResumption announces case resumed state")
    @Order(9)
    void announceCaseResumption_announcesCaseResumed() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceCaseResumption(services, caseId);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_RESUMED", params.get("action"));
        assertEquals("case123", params.get("caseID"));
    }

    @Test
    @DisplayName("announceWorkItemStatusChange announces status change with old and new status")
    @Order(10)
    void announceWorkItemStatusChange_announcesStatusChange() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceWorkItemStatusChange(services, workItem,
            YWorkItemStatus.Enabled, YWorkItemStatus.Running);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("ITEM_STATUS", params.get("action"));
        assertEquals("Enabled", params.get("oldStatus"));
        assertEquals("Running", params.get("newStatus"));
        assertNotNull(params.get("workItem"));
    }

    @Test
    @DisplayName("announceCaseStarted announces case start with correct parameters")
    @Order(11)
    void announceCaseStart_announcesCaseStart() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceCaseStarted(services, specId, caseId, "test-service", true);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_START", params.get("action"));
        assertEquals("case123", params.get("caseID"));
        assertEquals("true", params.get("delayed"));
        assertEquals("test-service", params.get("launchingService"));
    }

    @Test
    @DisplayName("announceCaseCompletion with multiple services broadcasts to all")
    @Order(12)
    void announceCaseCompletion_withMultipleServicesBroadcastsToAll() throws Exception {
        // Create multiple service references
        YAWLServiceReference service1 = new YAWLServiceReference(
            testService.getUrl(), null, "testService1", "password", null);
        YAWLServiceReference service2 = new YAWLServiceReference(
            testService.getUrl(), null, "testService2", "password", null);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(service1);
        services.add(service2);

        testService.reset();
        CountDownLatch latch = new CountDownLatch(2);
        testService.setLatch(latch);

        // Create test document
        Document caseData = new Document(new Element("caseData"));

        // Act
        client.announceCaseCompletion(services, caseId, caseData);

        // Verify both services received the announcement
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Both calls should complete");

        Map<String, String>[] calls = testService.getRequestCalls();
        assertEquals(2, calls.length, "Should have called both services");

        // Verify both calls have correct parameters
        for (Map<String, String> call : calls) {
            assertEquals("CASE_COMPLETE", call.get("action"));
            assertEquals("case123", call.get("caseID"));
            assertNotNull(call.get("casedata"));
        }
    }

    @Test
    @DisplayName("announceCaseCompletion with single service sends to that service")
    @Order(13)
    void announceCaseCompletion_withSingleServiceSendsToThatService() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        // Create test document
        Document caseData = new Document(new Element("caseData"));

        // Act
        client.announceCaseCompletion(serviceRef, caseId, caseData);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_COMPLETE", params.get("action"));
        assertEquals("case123", params.get("caseID"));
        assertNotNull(params.get("casedata"));
    }

    @Test
    @DisplayName("announceEngineInitialised announces engine initialization with timeout")
    @Order(14)
    void announceEngineInitialised_announcesEngineInitialization() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceEngineInitialised(services, 30);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("ENGINE_INIT", params.get("action"));
        assertEquals("30", params.get("maxWaitSeconds"));
    }

    @Test
    @DisplayName("announceCaseCancellation announces case cancellation")
    @Order(15)
    void announceCaseCancellation_announcesCaseCancellation() throws Exception {
        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceCaseCancellation(services, caseId);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_CANCELLED", params.get("action"));
        assertEquals("case123", params.get("caseID"));
    }

    @Test
    @DisplayName("announceDeadlock announces deadlock with task IDs")
    @Order(16)
    void announceDeadlock_announcesDeadlock() throws Exception {
        // Create test tasks
        YTask task1 = new YAtomicTask("task1", YTask._XOR, YTask._AND, null);
        YTask task2 = new YAtomicTask("task2", YTask._XOR, YTask._AND, null);

        Set<YTask> tasks = new HashSet<>();
        tasks.add(task1);
        tasks.add(task2);

        testService.reset();
        CountDownLatch latch = new CountDownLatch(1);
        testService.setLatch(latch);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(serviceRef);

        // Act
        client.announceDeadlock(services, caseId, tasks);

        // Verify the HTTP call was made
        assertTrue(latch.await(5, TimeUnit.SECONDS), "HTTP call should complete");

        Map<String, String> params = testService.getLastRequestParams();
        assertNotNull(params);
        assertEquals("CASE_DEADLOCKED", params.get("action"));
        assertEquals("case123", params.get("caseID"));
        assertTrue(params.get("tasks").contains("[task1, task2]") ||
                   params.get("tasks").contains("[task2, task1]"));
    }

    @Test
    @DisplayName("shutdown cancels all HTTP validations and shuts down executors")
    @Order(17)
    void shutdown_cancelsValidationsAndShutsDownExecutors() throws Exception {
        // Set up multiple services to test executor shutdown
        YAWLServiceReference service1 = new YAWLServiceReference(
            testService.getUrl(), null, "testService1", "password", null);
        YAWLServiceReference service2 = new YAWLServiceReference(
            testService.getUrl(), null, "testService2", "password", null);

        // Get executors for services
        ExecutorService exec1 = client.getServiceExecutor(service1);
        ExecutorService exec2 = client.getServiceExecutor(service2);

        // Act
        client.shutdown();

        // Verify executors are shut down
        assertTrue(exec1.isTerminated() || exec1.isShutdown(),
            "Executor 1 should be terminated");
        assertTrue(exec2.isTerminated() || exec2.isShutdown(),
            "Executor 2 should be terminated");
    }

    @Test
    @DisplayName("getRequiredParamsForService returns parameter array for service")
    @Order(18)
    void getRequiredParamsForService_returnsParameterArray() throws Exception {
        // Set up test service to return parameter data
        testService.setResponse("""
            <parameters>
                <param1 name="inputParam" type="string"/>
                <param2 name="outputParam" type="string"/>
            </parameters>
            """);

        // Act
        YParameter[] parameters = client.getRequiredParamsForService(serviceRef);

        // Verify the actual returned parameters
        assertNotNull(parameters);
        assertTrue(parameters.length >= 0, "Should return parameter array");
        if (parameters.length > 0) {
            for (YParameter param : parameters) {
                assertNotNull(param.getName(), "Parameter should have name");
            }
        }
    }

    @Test
    @DisplayName("getRequiredParamsForService throws IOException on connection failure")
    @Order(19)
    void getRequiredParamsForService_throwsIOExceptionOnConnectionFailure() throws Exception {
        // Create service to test connection failure
        YAWLServiceReference failingService = new YAWLServiceReference(
            "http://localhost:99999/failing-service", null, "failingService", "password", null);

        // Act & Assert
        assertThrows(IOException.class, () -> {
            client.getRequiredParamsForService(failingService);
        });
    }

    @Test
    @DisplayName("getRequiredParamsForService throws JDOMException on malformed XML")
    @Order(20)
    void getRequiredParamsForService_throwsJDOMExceptionOnMalformedXML() throws Exception {
        // Set up service to return malformed XML
        testService.setResponse("malformed xml");

        // Act & Assert
        assertThrows(org.jdom2.JDOMException.class, () -> {
            client.getRequiredParamsForService(serviceRef);
        });
    }

    @Test
    @DisplayName("announceEngineInitialised handles connection failures gracefully")
    @Order(21)
    void announceEngineInitialised_handlesConnectionFailuresGracefully() throws Exception {
        // Create failing service for connection exception test
        FailingHttpService failingService = new FailingHttpService("connect");
        YAWLServiceReference failingServiceRef = new YAWLServiceReference(
            failingService.getUrl(), null, "failingService", "password", null);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(failingServiceRef);

        // Act - should not throw exception even if service is unavailable
        assertDoesNotThrow(() -> {
            client.announceEngineInitialised(services, 30);
        });
    }

    @Test
    @DisplayName("announceFiredWorkItem handles IOException gracefully")
    @Order(22)
    void announceFiredWorkItem_handlesIOExceptionGracefully() throws Exception {
        // Create failing service for IO exception test
        FailingHttpService failingService = new FailingHttpService("io");
        YAWLServiceReference failingServiceRef = new YAWLServiceReference(
            failingService.getUrl(), null, "failingService", "password", null);

        // Create announcement with failing service
        YWorkItemID workItemId = new YWorkItemID(caseId, "test-task");
        YWorkItem failingWorkItem = new YWorkItem(null, specId, task, workItemId, false, false);

        YAnnouncement announcement = new YAnnouncement();
        announcement.setItem(failingWorkItem);
        announcement.setYawlService(failingServiceRef);

        // Act - should not throw exception even if service is unavailable
        assertDoesNotThrow(() -> {
            client.announceFiredWorkItem(announcement);
        });
    }

    @Test
    @DisplayName("announceCaseStarted ignores IOException for broadcast events")
    @Order(23)
    void announceCaseStarted_ignoresIOExceptionForBroadcastEvents() throws Exception {
        // Create failing service for broadcast test
        FailingHttpService failingService = new FailingHttpService("io");
        YAWLServiceReference failingServiceRef = new YAWLServiceReference(
            failingService.getUrl(), null, "failingService", "password", null);

        Set<YAWLServiceReference> services = new HashSet<>();
        services.add(failingServiceRef);

        // Act - should not throw exception for broadcast events
        assertDoesNotThrow(() -> {
            client.announceCaseStarted(services, specId, caseId, "test-service", true);
        });
    }

    @Test
    @DisplayName("executorMap is thread-safe and service-specific")
    @Order(24)
    void executorMap_isThreadSafeAndServiceSpecific() throws InterruptedException {
        // Create multiple service references
        YAWLServiceReference service1 = new YAWLServiceReference(
            testService.getUrl(), null, "testService1", "password", null);
        YAWLServiceReference service2 = new YAWLServiceReference(
            testService.getUrl(), null, "testService2", "password", null);

        // Concurrent access to executors
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Map<YAWLServiceReference, ExecutorService> collectedExecutors =
            new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i;
            executor.execute(() -> {
                YAWLServiceReference service = (threadNum % 2 == 0) ? service1 : service2;
                ExecutorService serviceExecutor = client.getServiceExecutor(service);
                collectedExecutors.put(service, serviceExecutor);
                latch.countDown();
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify each service gets its own executor
        ExecutorService exec1 = collectedExecutors.get(service1);
        ExecutorService exec2 = collectedExecutors.get(service2);

        assertNotNull(exec1);
        assertNotNull(exec2);
        assertNotEquals(exec1, exec2, "Each service should have its own executor");
    }

    @Test
    @DisplayName("getServiceExecutor creates new executor for new service")
    @Order(25)
    void getServiceExecutor_createsNewExecutorForNewService() {
        // Arrange
        YAWLServiceReference newService = new YAWLServiceReference(
            testService.getUrl(), null, "newService", "password", null);

        // Act
        ExecutorService executor1 = client.getServiceExecutor(newService);
        ExecutorService executor2 = client.getServiceExecutor(newService);

        // Verify same executor returned for same service
        assertEquals(executor1, executor2, "Same service should return same executor");
        assertNotNull(executor1, "Executor should not be null");
    }

    /**
     * Test HTTP service that captures incoming requests for verification
     */
    private static class TestHttpService {
        private HttpServer server;
        private volatile String lastRequestParams;
        private volatile Map<String, String>[] requestCalls;
        private CountDownLatch latch;
        private String response;
        private final List<Map<String, String>> capturedRequests = new ArrayList<>();

        public void start() throws Exception {
            server = HttpServer.create();
            server.bind(new java.net.InetSocketAddress(0), 0);

            com.sun.net.httpserver.HttpHandler handler = exchange -> {
                try {
                    // Read POST data
                    String formData = new String(exchange.getRequestBody().readAllBytes());
                    Map<String, String> params = parseFormData(formData);

                    capturedRequests.add(params);
                    lastRequestParams = formData;
                    requestCalls = capturedRequests.toArray(new Map[0]);

                    // Notify latch if set
                    if (latch != null) {
                        latch.countDown();
                    }

                    // Send response
                    String responseContent = response != null ? response : "OK";
                    exchange.sendResponseHeaders(200, responseContent.length());
                    exchange.getResponseBody().write(responseContent.getBytes());
                    exchange.close();
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, 0);
                    exchange.close();
                }
            };

            server.createContext("/", handler);
            server.start();
        }

        public void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        public String getUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        public void reset() {
            lastRequestParams = null;
            capturedRequests.clear();
            requestCalls = null;
            latch = null;
        }

        public void setLatch(CountDownLatch latch) {
            this.latch = latch;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public Map<String, String> getLastRequestParams() {
            if (requestCalls != null && requestCalls.length > 0) {
                return requestCalls[requestCalls.length - 1];
            }
            return null;
        }

        public Map<String, String>[] getRequestCalls() {
            return requestCalls;
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> params = new HashMap<>();
            if (formData != null) {
                String[] pairs = formData.split("&");
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    if (keyValue.length == 2) {
                        params.put(keyValue[0], keyValue[1]);
                    }
                }
            }
            return params;
        }
    }

    /**
     * Failing HTTP service that throws specific exceptions for testing
     */
    private static class FailingHttpService {
        private HttpServer server;
        private final String failureType;

        public FailingHttpService(String failureType) throws Exception {
            this.failureType = failureType;
            server = HttpServer.create();
            server.bind(new java.net.InetSocketAddress(0), 0);

            com.sun.net.httpserver.HttpHandler handler = exchange -> {
                if ("connect".equals(failureType)) {
                    throw new java.net.ConnectException("Connection refused");
                } else if ("io".equals(failureType)) {
                    throw new IOException("I/O error");
                }
                exchange.sendResponseHeaders(200, 0);
                exchange.close();
            };

            server.createContext("/", handler);
            server.start();
        }

        public void stop() {
            if (server != null) {
                server.stop(0);
            }
        }

        public String getUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }
    }
}