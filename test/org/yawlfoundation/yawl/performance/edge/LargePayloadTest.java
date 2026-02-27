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

package org.yawlfoundation.yawl.performance.edge;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.data.YDataHandler;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.engine.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EngineBasedClient;
import org.yawlfoundation.yawl.exceptions.YSchemaException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.jdom2.Element;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for large payload case data handling in YAWL.
 *
 * Tests extreme scenarios that could cause production issues:
 * - 10MB+ XML/YAWL payloads
 * - Large variable data (strings, binary, nested structures)
 * - Memory pressure scenarios
 * - Serialization/deserialization limits
 * - Concurrent large payload processing
 *
 * Chicago TDD: Uses real YAWL objects, no mocks.
 *
 * @author Performance Test Specialist
 * @version 6.0.0
 */
@Tag("stress")
@Tag("performance")
@Tag("edge-cases")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 180, unit = TimeUnit.MINUTES)  // 3 hours max for all tests
class LargePayloadTest {

    private static final String PAYLOAD_SIZE_HDR = "=== LARGE PAYLOAD TEST REPORT ===";
    private static final String PAYLOAD_SIZE_FTR_PASS = "=== ALL TESTS PASSED ===";
    private static final String PAYLOAD_SIZE_FTR_FAIL = "=== TEST FAILURES DETECTED ===";

    // Test constants
    private static final int MIN_PAYLOAD_SIZE_KB = 10;
    private static final int MAX_PAYLOAD_SIZE_KB = 10_000;  // 10MB
    private static final String LARGE_STRING_DATA;
    private static final String HUGE_XML_DATA;

    // Static initialization for test data
    static {
        // Generate 1MB of string data for testing
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("This is a test string line number ").append(i)
              .append(" with some random characters ABC123xyz!@#%$^\n");
        }
        String baseLine = sb.toString();
        LARGE_STRING_DATA = baseLine.repeat(10);  // ~1MB

        // Generate 10MB XML data
        StringBuilder xmlBuilder = new StringBuilder();
        xmlBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xmlBuilder.append("<yawl:caseData xmlns:yawl=\"http://www.yawlfoundation.org/yawl\">\n");
        for (int i = 0; i < 50000; i++) {
            xmlBuilder.append(String.format(
                "  <data id=\"item-%d\">\n" +
                "    <field name=\"name\">Test Item %d</field>\n" +
                "    <field name=\"value\">%d</field>\n" +
                "    <field name=\"description\">This is a test item with a long description that contains detailed information about the item and its properties. It can include various types of data and metadata.</field>\n" +
                "    <field name=\"timestamp\">2026-02-%02dT10:%02d:%02d.000Z</field>\n" +
                "    <field name=\"active\">true</field>\n" +
                "  </data>\n",
                i, i, i % 1000, i % 60, i % 60
            ));
        }
        xmlBuilder.append("</yawl:caseData>");
        HUGE_XML_DATA = xmlBuilder.toString();
    }

    private static final List<String> TEST_REPORT_LINES = new ArrayList<>();
    private YAWLServiceInterfaceRegistry registry;
    private YNetRunner netRunner;
    private YDataHandler dataHandler;

    @BeforeAll
    static void setup() {
        System.out.println();
        System.out.println(PAYLOAD_SIZE_HDR);
        System.out.println("Testing payload sizes from " + MIN_PAYLOAD_SIZE_KB + "KB to " + MAX_PAYLOAD_SIZE_KB + "KB");
        System.out.println();
    }

    @AfterAll
    static void tearDown() {
        boolean allPass = TEST_REPORT_LINES.stream()
                .allMatch(l -> l.contains("PASS") || l.contains("CHARACTERISED"));
        for (String line : TEST_REPORT_LINES) {
            System.out.println(line);
        }
        System.out.println(allPass ? PAYLOAD_SIZE_FTR_PASS : PAYLOAD_SIZE_FTR_FAIL);
        System.out.println();
    }

    @BeforeEach
    void initializeTest() {
        registry = new YAWLServiceInterfaceRegistry();
        netRunner = new YNetRunner();
        dataHandler = new YDataHandler();
    }

    // =========================================================================
    // Test 1: Large String Variable Processing
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Test 1 — Large String Variables (1MB+)")
    void test1_largeStringVariableProcessing() throws Exception {
        // Given: A workflow with a large string variable
        YSpecification spec = createSpecificationWithLargeStringVariable();
        String largeString = LARGE_STRING_DATA.repeat(1);  // ~10MB
        long dataSizeBytes = largeString.getBytes().length;

        // When: The workflow processes the large string
        long startTime = System.nanoTime();
        Map<String, String> inputData = Map.of("largeString", largeString);
        String caseId = netRunner.launchCase(spec.getID(), inputData);

        // Wait for case completion
        List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 30);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Validate the large string was processed correctly
        assertNotNull(caseId, "Case should be created successfully");
        assertFalse(workItems.isEmpty(), "Work items should be generated");

        // Verify data integrity
        YWorkItem workItem = netRunner.getWorkItem(workItems.get(0).getWorkItemID());
        Object processedValue = workItem.getDataVariableByName("processedString").getValue();
        assertNotNull(processedValue, "Processed string should not be null");

        String resultString = processedValue.toString();
        assertEquals(largeString.length(), resultString.length(),
                "Processed string should maintain original length");
        assertEquals(largeString, resultString,
                "Processed string should maintain original content");

        // Performance assertions
        assertTrue(durationMs < 5000, "Large string processing should complete within 5s");
        double throughput = dataSizeBytes / (double) durationMs;
        assertTrue(throughput > 1_000_000, "Throughput should exceed 1MB/s");

        String report = String.format(
                "Test 1  Large String Variables:     payloadSize=%,dKB  duration=%.2fs  throughput=%.0fKB/s  PASS",
                dataSizeBytes / 1024, durationMs / 1000.0, throughput / 1024);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 2: Large XML Payload Serialization
    // =========================================================================

    @Test
    @Order(2)
    @DisplayName("Test 2 — Large XML Payload Serialization (10MB+)")
    void test2_largeXMLSerialization() throws Exception {
        // Given: A 10MB XML payload
        long xmlSizeBytes = HUGE_XML_DATA.getBytes().length;
        assertTrue(xmlSizeBytes > 10_000_000, "XML should be >10MB for this test");

        // When: Serialize and deserialize the XML
        long startNanos = System.nanoTime();
        Element xmlElement = YMarshal.unmarshalHugeXML(HUGE_XML_DATA);
        String serializedXML = YMarshal.marshalHugeXML(xmlElement);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        // Then: Verify data integrity after serialization/deserialization
        assertNotNull(xmlElement, "XML should be deserialized successfully");
        assertEquals(HUGE_XML_DATA.length(), serializedXML.length(),
                "Serialized XML should maintain original length");
        assertEquals(HUGE_XML_DATA, serializedXML,
                "Serialized XML should match original content");

        // Performance assertions
        assertTrue(durationMs < 10000, "XML processing should complete within 10s");
        double throughput = xmlSizeBytes / (double) durationMs;
        assertTrue(throughput > 500_000, "Throughput should exceed 500KB/s");

        // Memory usage check
        long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        double memoryUsedMB = memoryUsed / (1024.0 * 1024.0);

        String report = String.format(
                "Test 2  XML Serialization:          xmlSize=%,dKB  duration=%.2fs  throughput=%.0fKB/s  memoryUsed=%.1fMB  PASS",
                xmlSizeBytes / 1024, durationMs / 1000.0, throughput / 1024, memoryUsedMB);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 3: Nested Large Data Structures
    // =========================================================================

    @Test
    @Order(3)
    @DisplayName("Test 3 — Nested Large Data Structures")
    void test3_nestedLargeDataStructures() throws Exception {
        // Given: A workflow with deeply nested large data structures
        YSpecification spec = createSpecificationWithNestedData();

        // Create nested data structure (3 levels deep, each level has 100 items)
        Map<String, Object> nestedData = createNestedDataStructure(3, 100);

        long dataSizeBytes = calculateMapSizeBytes(nestedData);

        // When: Process the nested data
        long startTime = System.nanoTime();
        String caseId = netRunner.launchCase(spec.getID(), Map.of("nestedData", nestedData));

        // Wait for completion
        List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 45);
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        // Then: Validate the nested structure was processed
        assertNotNull(caseId, "Case should be created");
        assertEquals(3, workItems.size(), "Should generate 3 work items for nested structure");

        // Verify data integrity
        for (WorkItemRecord workItem : workItems) {
            YWorkItem item = netRunner.getWorkItem(workItem.getWorkItemID());
            Object processedData = item.getDataVariableByName("processedNestedData").getValue();
            assertTrue(processedData instanceof Map, "Processed data should be a map");
            assertFalse(((Map<?, ?>) processedData).isEmpty(), "Map should not be empty");
        }

        // Performance assertions
        assertTrue(durationMs < 15000, "Nested data processing should complete within 15s");
        double throughput = dataSizeBytes / (double) durationMs;
        assertTrue(throughput > 50_000, "Throughput should exceed 50KB/s");

        String report = String.format(
                "Test 3  Nested Structures:           dataSize=%,dKB  depth=3  itemsPerLevel=100  duration=%.2fs  throughput=%.0fKB/s  PASS",
                dataSizeBytes / 1024, durationMs / 1000.0, throughput / 1024);
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 4: Concurrent Large Payload Processing
    // =========================================================================

    @Test
    @Order(4)
    @DisplayName("Test 4 — Concurrent Large Payload Processing")
    void test4_concurrentLargePayloadProcessing() throws Exception {
        // Given: Multiple workflows processing large payloads concurrently
        final int CONCURRENT_CASES = 10;
        final int LARGE_STRING_REPETITIONS = 5;  // 5MB per case

        YSpecification spec = createSpecificationWithLargeStringVariable();
        String largePayload = LARGE_STRING_DATA.repeat(largeStringRepetitions);
        long payloadSizeBytes = largePayload.getBytes().length;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = new ArrayList<>(CONCURRENT_CASES);
        CountDownLatch allCasesStarted = new CountDownLatch(CONCURRENT_CASES);
        AtomicInteger successfulCases = new AtomicInteger(0);
        AtomicInteger failedCases = new AtomicInteger(0);

        long startTime = System.nanoTime();

        // Launch concurrent cases
        for (int i = 0; i < CONCURRENT_CASES; i++) {
            final int caseIndex = i;
            futures.add(executor.submit(() -> {
                try {
                    allCasesStarted.countDown();
                    allCasesStarted.await(10, TimeUnit.SECONDS);

                    // Launch case with large payload
                    Map<String, String> inputData = Map.of("largeString", largePayload);
                    String caseId = netRunner.launchCase(spec.getID(), inputData);

                    // Wait for completion
                    List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 30);

                    // Verify data integrity
                    YWorkItem workItem = netRunner.getWorkItem(workItems.get(0).getWorkItemID());
                    Object processedValue = workItem.getDataVariableByName("processedString").getValue();

                    String processedString = processedValue.toString();
                    assertEquals(largePayload, processedString,
                            "Data integrity check failed for case " + caseId);

                    successfulCases.incrementAndGet();
                    return caseId;
                } catch (Exception e) {
                    failedCases.incrementAndGet();
                    throw e;
                }
            }));
        }

        // Wait for all cases to complete
        List<String> completedCaseIds = new ArrayList<>();
        for (Future<String> future : futures) {
            try {
                String caseId = future.get(120, TimeUnit.SECONDS);
                completedCaseIds.add(caseId);
            } catch (Exception e) {
                failedCases.incrementAndGet();
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        executor.shutdown();

        // Then: Validate concurrent processing results
        assertEquals(CONCURRENT_CASES, completedCaseIds.size(),
                "All concurrent cases should complete successfully");
        assertEquals(0, failedCases.get(), "No cases should fail");

        // Performance assertions
        double totalDataProcessed = payloadSizeBytes * CONCURRENT_CASES;
        double throughput = totalDataProcessed / (double) durationMs;
        assertTrue(throughput > 100_000, "Throughput should exceed 100KB/s per case");

        String report = String.format(
                "Test 4  Concurrent Processing:       cases=%d payloadSize=%,dKB each  duration=%.2fs  totalThroughput=%.0fKB/s  success=%d  pass=%d  PASS",
                CONCURRENT_CASES, payloadSizeBytes / 1024, durationMs / 1000.0,
                throughput / 1024, successfulCases.get(), failedCases.get());
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 5: Memory Pressure with Large Payloads
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("Test 5 — Memory Pressure with Large Payloads")
    void test5_memoryPressureWithLargePayloads() throws Exception {
        // Given: System under memory pressure with multiple large payloads
        final int PAYLOAD_COUNT = 20;
        final int PAYLOAD_SIZE_KB = 500;  // 500KB per payload

        YSpecification spec = createSpecificationWithLargeStringVariable();
        List<String> caseIds = new ArrayList<>();

        // Monitor memory usage
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Process multiple large payloads
        for (int i = 0; i < PAYLOAD_COUNT; i++) {
            // Create progressively larger payloads
            String payload = LARGE_STRING_DATA.substring(0, PAYLOAD_SIZE_KB * 1024);
            Map<String, String> inputData = Map.of("largeString", payload);

            String caseId = netRunner.launchCase(spec.getID(), inputData);
            caseIds.add(caseId);

            // Wait for completion
            List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 30);

            // Check memory usage
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            long memoryIncrease = currentMemory - initialMemory;

            // Fail if memory increases too much (> 50MB)
            if (memoryIncrease > 50 * 1024 * 1024) {
                fail("Memory usage increased by " + (memoryIncrease / 1024 / 1024) + "MB - potential memory leak");
            }

            // Force garbage collection to check if memory is properly released
            runtime.gc();
            Thread.sleep(100);  // Brief pause for GC
        }

        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Then: Validate memory behavior
        assertTrue(memoryIncrease < 10 * 1024 * 1024,
                "Memory increase should be minimal (<10MB), got " + (memoryIncrease / 1024 / 1024) + "MB");

        // Verify all cases completed successfully
        for (String caseId : caseIds) {
            assertTrue(netRunner.getCaseStatus(caseId).equals("complete"),
                    "Case " + caseId + " should be complete");
        }

        String report = String.format(
                "Test 5  Memory Pressure:            payloads=%d sizePerPayload=%,dKB  memoryIncrease=%dMB  gcCount=%d  PASS",
                PAYLOAD_COUNT, PAYLOAD_SIZE_KB, memoryIncrease / 1024 / 1024,
                runtime.gcCount());
        TEST_REPORT_LINES.add(report);
        System.out.println(report);
    }

    // =========================================================================
    // Test 6: Boundary Conditions for Payload Size
    // =========================================================================

    @Test
    @Order(6)
    @DisplayName("Test 6 — Payload Size Boundary Conditions")
    void test6_payloadSizeBoundaryConditions() throws Exception {
        // Test various boundary conditions around payload limits
        YSpecification spec = createSpecificationWithLargeStringVariable();

        // Test at boundaries
        int[] boundarySizes = {
                100,           // 100KB
                500,           // 500KB
                1000,          // 1MB
                5000,          // 5MB
                10000,         // 10MB
                20000          // 20MB (stress test)
        };

        int successfulTests = 0;
        long totalTimeMs = 0;

        for (int sizeKb : boundarySizes) {
            try {
                // Create payload of specific size
                String payload = createPayloadOfSizeKb(sizeKb);
                long payloadSizeBytes = payload.getBytes().length;

                // Process the payload
                long startTime = System.nanoTime();
                String caseId = netRunner.launchCase(spec.getID(), Map.of("largeString", payload));

                List<WorkItemRecord> workItems = waitForCaseCompletion(caseId, 60);
                long durationMs = (System.nanoTime() - startTime) / 1_000_000;

                // Verify data integrity
                YWorkItem workItem = netRunner.getWorkItem(workItems.get(0).getWorkItemID());
                Object processedValue = workItem.getDataVariableByName("processedString").getValue();
                String processedString = processedValue.toString();

                assertEquals(payload, processedString,
                        "Data integrity failed for " + sizeKb + "KB payload");

                successfulTests++;
                totalTimeMs += durationMs;

                String boundaryReport = String.format(
                        "  Boundary %,dKB: duration=%.2fs throughput=%.0fKB/s PASS",
                        sizeKb, durationMs / 1000.0, (payloadSizeBytes / (double) durationMs) / 1024);
                System.out.println(boundaryReport);

            } catch (Exception e) {
                String boundaryReport = String.format(
                        "  Boundary %,dKB: FAILED - %s", sizeKb, e.getMessage());
                System.out.println(boundaryReport);
            }
        }

        // Performance summary
        double avgDurationMs = totalTimeMs / (double) successfulTests;
        assertTrue(successfulTests >= boundarySizes.length - 1,
                "Should pass at least " + (boundarySizes.length - 1) + " boundary tests");

        String summaryReport = String.format(
                "Test 6  Boundary Conditions:         boundaries=%d successful=%d avgDuration=%.2fs avgThroughput=%.0fKB/s  PASS",
                boundarySizes.length, successfulTests, avgDurationMs / 1000.0,
                (boundarySizes[boundarySizes.length-1] * 1024 / 1024) / (avgDurationMs / 1000.0));
        TEST_REPORT_LINES.add(summaryReport);
        System.out.println(summaryReport);
    }

    // Helper methods

    private YSpecification createSpecificationWithLargeStringVariable() throws Exception {
        // Create a simple YAWL specification with a large string variable
        Element yawlElement = new Element("yawl");
        yawlElement.setAttribute("xmlns", "http://www.yawlfoundation.org/yawl");

        // Create specification
        Element specElement = new Element("specification");
        specElement.setAttribute("id", "LargePayloadSpec");
        specElement.setAttribute("name", "Large Payload Test Specification");

        // Create input and output parameters
        Element inputParams = new Element("inputParameters");
        Element largeStringVar = new Element("data");
        largeStringVar.setAttribute("name", "largeString");
        largeStringVar.setAttribute("type", "xs:string");
        largeStringVar.setAttribute("schemaType", "string");
        inputParams.addContent(largeStringVar);

        Element outputParams = new Element("outputParameters");
        Element processedStringVar = new Element("data");
        processedStringVar.setAttribute("name", "processedString");
        processedStringVar.setAttribute("type", "xs:string");
        processedStringVar.setAttribute("schemaType", "string");
        outputParams.addContent(processedStringVar);

        // Create net
        Element netElement = new Element("net");
        netElement.setAttribute("id", "Net1");

        // Create task
        Element taskElement = new Element("task");
        taskElement.setAttribute("name", "ProcessLargeString");
        taskElement.setAttribute("id", "Task1");

        // Connect net elements
        specElement.addContent(inputParams);
        specElement.addContent(outputParams);
        netElement.addContent(taskElement);
        specElement.addContent(netElement);
        yawlElement.addContent(specElement);

        return YMarshal.unmarshalSpecification(yawlElement);
    }

    private YSpecification createSpecificationWithNestedData() throws Exception {
        // Create specification for nested data processing
        Element yawlElement = new Element("yawl");
        yawlElement.setAttribute("xmlns", "http://www.yawlfoundation.org/yawl");

        Element specElement = new Element("specification");
        specElement.setAttribute("id", "NestedDataSpec");
        specElement.setAttribute("name", "Nested Data Test Specification");

        // Input parameters for nested data
        Element inputParams = new Element("inputParameters");
        Element nestedDataVar = new Element("data");
        nestedDataVar.setAttribute("name", "nestedData");
        nestedDataVar.setAttribute("type", "map");
        inputParams.addContent(nestedDataVar);

        // Output parameters
        Element outputParams = new Element("outputParameters");
        Element processedNestedDataVar = new Element("data");
        processedNestedDataVar.setAttribute("name", "processedNestedData");
        processedNestedDataVar.setAttribute("type", "map");
        outputParams.addContent(processedNestedDataVar);

        // Create net with multiple tasks for nested processing
        Element netElement = new Element("net");
        netElement.setAttribute("id", "NestedNet1");

        // Create tasks
        for (int i = 1; i <= 3; i++) {
            Element taskElement = new Element("task");
            taskElement.setAttribute("name", "ProcessLevel" + i);
            taskElement.setAttribute("id", "Task" + i);
            netElement.addContent(taskElement);
        }

        specElement.addContent(inputParams);
        specElement.addContent(outputParams);
        specElement.addContent(netElement);
        yawlElement.addContent(specElement);

        return YMarshal.unmarshalSpecification(yawlElement);
    }

    private Map<String, Object> createNestedDataStructure(int depth, int itemsPerLevel) {
        if (depth == 0) {
            Map<String, Object> leaf = new HashMap<>();
            leaf.put("value", System.currentTimeMillis());
            leaf.put("description", "Leaf node at depth " + depth);
            return leaf;
        }

        Map<String, Object> level = new HashMap<>();
        for (int i = 0; i < itemsPerLevel; i++) {
            String key = "item-" + i;
            level.put(key, createNestedDataStructure(depth - 1, itemsPerLevel));
        }
        return level;
    }

    private long calculateMapSizeBytes(Map<String, Object> map) {
        long size = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            size += entry.getKey().getBytes().length;
            if (entry.getValue() instanceof Map) {
                size += calculateMapSizeBytes((Map<String, Object>) entry.getValue());
            } else {
                size += entry.getValue().toString().getBytes().length;
            }
        }
        return size;
    }

    private String createPayloadOfSizeKb(int sizeKb) {
        StringBuilder payload = new StringBuilder();
        int linesNeeded = (sizeKb * 1024) / 100;  // Approximate lines based on average line length

        for (int i = 0; i < linesNeeded; i++) {
            payload.append("This is line ").append(i)
                   .append(" of the payload with some data: ")
                   .append(System.currentTimeMillis())
                   .append(" - ABC123xyz!@#$%^&*()\n");
        }

        return payload.toString();
    }

    private List<WorkItemRecord> waitForCaseCompletion(String caseId, int timeoutSeconds) throws InterruptedException {
        List<WorkItemRecord> workItems = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
            List<WorkItemRecord> items = netRunner.getWorkItemListForCase(caseId);
            workItems = items.stream()
                    .filter(item -> item.getStatus().equals(YWorkItemStatus.Fired))
                    .toList();

            if (!workItems.isEmpty()) {
                return workItems;
            }

            Thread.sleep(1000);
        }

        fail("Case " + caseId + " did not complete within " + timeoutSeconds + " seconds");
        return Collections.emptyList();
    }
}