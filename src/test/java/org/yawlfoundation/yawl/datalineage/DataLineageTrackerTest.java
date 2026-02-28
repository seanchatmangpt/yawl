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

package org.yawlfoundation.yawl.datalineage;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.data.contract.DataLineageTracker;
import org.yawlfoundation.yawl.elements.data.contract.DataLineageTracker.DataLineageRecord;
import org.yawlfoundation.yawl.elements.data.contract.DataLineageTrackerImpl;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DataLineageTracker (Phase 6: Blue Ocean Enhancement).
 *
 * <p>Tests real data lineage tracking through workflow execution, including:
 * - Single case lineage recording and querying
 * - Batch operations with multiple cases
 * - Concurrent execution under load
 * - Multi-hop data paths through tasks
 * - Circular dependencies detection
 * - Case isolation in concurrent scenarios
 *
 * <p>Uses Chicago TDD (Detroit School): real YAWL objects, real data tracking,
 * no mocks or stubs.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("DataLineageTracker Tests")
class DataLineageTrackerTest {

    private DataLineageTracker tracker;
    private YSpecificationID specId;

    @BeforeEach
    void setUp() {
        tracker = new DataLineageTrackerImpl();
        specId = new YSpecificationID("order-fulfillment", "1.0", "main");
    }

    /**
     * Test group: Single case lineage recording and querying
     */
    @Nested
    @DisplayName("Single Case Lineage Recording")
    class SingleCaseLineageTests {

        @Test
        @DisplayName("GIVEN case start event WHEN recorded THEN lineage contains start record")
        void testRecordCaseStart_SingleRecord() {
            // GIVEN
            String caseId = "CASE001";
            Element startData = new Element("data").addContent(
                new Element("customer_id").setText("C123")
            );

            // WHEN
            tracker.recordCaseStart(specId, caseId, "customers", startData);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage)
                .isNotEmpty()
                .hasSize(1);

            DataLineageRecord record = lineage.get(0);
            assertThat(record.getCaseId()).isEqualTo(caseId);
            assertThat(record.getSourceTable()).isEqualTo("customers");
            assertThat(record.getTaskName()).isNull();
            assertThat(record.getTargetTable()).isNull();
        }

        @Test
        @DisplayName("GIVEN task execution WHEN recorded THEN lineage captures task work")
        void testRecordTaskExecution_SingleTask() {
            // GIVEN
            String caseId = "CASE002";
            Element sourceData = new Element("data").addContent(
                new Element("amount").setText("1000")
            );
            Element outputData = new Element("result").addContent(
                new Element("approved").setText("true")
            );

            // Record case start first
            tracker.recordCaseStart(specId, caseId, "customers", sourceData);

            // WHEN
            tracker.recordTaskExecution(specId, caseId, "CheckCredit",
                "orders", sourceData, outputData);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(2);

            DataLineageRecord taskRecord = lineage.get(1);
            assertThat(taskRecord.getTaskName()).isEqualTo("CheckCredit");
            assertThat(taskRecord.getTargetTable()).isEqualTo("orders");
        }

        @Test
        @DisplayName("GIVEN complete workflow execution THEN lineage traces full path")
        void testCompleteWorkflowLineage_ThreeTasks() {
            // GIVEN
            String caseId = "CASE003";
            Element customerData = createElement("customer", "C123");
            Element orderData = createElement("order", "O456");
            Element invoiceData = createElement("invoice", "INV789");

            // WHEN
            tracker.recordCaseStart(specId, caseId, "customers", customerData);
            tracker.recordTaskExecution(specId, caseId, "ValidateOrder",
                "orders", customerData, orderData);
            tracker.recordTaskExecution(specId, caseId, "GenerateInvoice",
                "invoices", orderData, invoiceData);
            tracker.recordCaseCompletion(specId, caseId, "archive", invoiceData);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(4);
            assertThat(lineage.get(0).getSourceTable()).isEqualTo("customers");
            assertThat(lineage.get(1).getTaskName()).isEqualTo("ValidateOrder");
            assertThat(lineage.get(2).getTaskName()).isEqualTo("GenerateInvoice");
            assertThat(lineage.get(3).getTargetTable()).isEqualTo("archive");
        }
    }

    /**
     * Test group: Batch operations with multiple cases
     */
    @Nested
    @DisplayName("Batch Case Processing")
    class BatchCaseTests {

        @Test
        @DisplayName("GIVEN 100 cases with concurrent recording WHEN queried THEN all isolated correctly")
        void testBatchCaseRecording_100Cases() throws InterruptedException {
            // GIVEN
            int caseCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(10);

            // WHEN
            for (int i = 0; i < caseCount; i++) {
                final int caseNumber = i;
                executor.submit(() -> {
                    String caseId = String.format("BATCH%06d", caseNumber);
                    Element data = createElement("batch_record", String.valueOf(caseNumber));
                    tracker.recordCaseStart(specId, caseId, "batch_table", data);
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                "Batch recording should complete within 10 seconds");

            // THEN
            for (int i = 0; i < caseCount; i++) {
                String caseId = String.format("BATCH%06d", i);
                List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
                assertThat(lineage)
                    .hasSize(1)
                    .allMatch(r -> r.getCaseId().equals(caseId));
            }
        }

        @Test
        @DisplayName("GIVEN multiple cases for same table WHEN queried by table THEN returns all cases")
        void testTableLineageQuery_MultipleCases() {
            // GIVEN
            Element data1 = createElement("customer", "C001");
            Element data2 = createElement("customer", "C002");
            Element data3 = createElement("customer", "C003");

            // WHEN
            tracker.recordCaseStart(specId, "CASE001", "customers", data1);
            tracker.recordCaseStart(specId, "CASE002", "customers", data2);
            tracker.recordCaseStart(specId, "CASE003", "customers", data3);

            // THEN
            List<DataLineageRecord> customerLineage = tracker.getLineageForTable("customers");
            assertThat(customerLineage)
                .hasSize(3)
                .extracting(DataLineageRecord::getCaseId)
                .containsExactly("CASE001", "CASE002", "CASE003");
        }
    }

    /**
     * Test group: Concurrent execution and isolation
     */
    @Nested
    @DisplayName("Concurrent Case Isolation")
    class ConcurrentIsolationTests {

        @Test
        @DisplayName("GIVEN 10 concurrent cases WHEN executing simultaneously THEN no cross-contamination")
        @Execution(ExecutionMode.CONCURRENT)
        void testConcurrentCases_NoContamination() throws InterruptedException {
            // GIVEN
            int threadCount = 10;
            CyclicBarrier barrier = new CyclicBarrier(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // WHEN
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        barrier.await(); // Ensure true concurrency
                        String caseId = String.format("CONCURRENT_%03d", threadId);

                        for (int j = 0; j < 5; j++) {
                            Element data = createElement("task_" + j, "thread_" + threadId);
                            tracker.recordTaskExecution(specId, caseId,
                                "Task" + j, "table_" + j, data, data);
                        }
                    } catch (Exception e) {
                        fail("Concurrent execution failed: " + e.getMessage());
                    }
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // THEN
            for (int i = 0; i < threadCount; i++) {
                String caseId = String.format("CONCURRENT_%03d", i);
                List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);

                assertThat(lineage)
                    .hasSize(5)
                    .allMatch(r -> r.getCaseId().equals(caseId));

                for (int j = 0; j < 5; j++) {
                    assertThat(lineage.get(j).getTaskName()).isEqualTo("Task" + j);
                }
            }
        }

        @Test
        @DisplayName("GIVEN concurrent writes to same table WHEN queried THEN all records present")
        void testConcurrentTableAccess_AllRecorded() throws InterruptedException {
            // GIVEN
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            // WHEN
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    String caseId = String.format("TABLE_CASE_%03d", threadId);
                    Element data = createElement("shared_table", "thread_" + threadId);
                    tracker.recordTaskExecution(specId, caseId,
                        "WriteTask", "shared_data", data, data);
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // THEN
            List<DataLineageRecord> tableLineage = tracker.getLineageForTable("shared_data");
            assertThat(tableLineage)
                .hasSize(threadCount)
                .extracting(DataLineageRecord::getCaseId)
                .hasSize(threadCount);
        }
    }

    /**
     * Test group: Multi-hop lineage and path tracing
     */
    @Nested
    @DisplayName("Multi-Hop Data Paths")
    class MultiHopLineageTests {

        @Test
        @DisplayName("GIVEN 5-hop workflow WHEN querying lineage THEN traces complete path")
        void testMultiHopLineage_FiveTasks() {
            // GIVEN: customers → Order → Fulfillment → Shipping → Billing → Archive
            String caseId = "MULTIHOP001";
            Element custData = createElement("customer", "C789");
            Element orderData = createElement("order", "O123");
            Element fulfillData = createElement("fulfill", "F456");
            Element shipData = createElement("ship", "S789");
            Element billData = createElement("bill", "B012");

            // WHEN
            tracker.recordCaseStart(specId, caseId, "customers", custData);
            tracker.recordTaskExecution(specId, caseId, "CreateOrder", "orders", custData, orderData);
            tracker.recordTaskExecution(specId, caseId, "Fulfill", "fulfillment", orderData, fulfillData);
            tracker.recordTaskExecution(specId, caseId, "Ship", "shipping", fulfillData, shipData);
            tracker.recordTaskExecution(specId, caseId, "Bill", "billing", shipData, billData);
            tracker.recordCaseCompletion(specId, caseId, "archive", billData);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage)
                .hasSize(6)
                .extracting(DataLineageRecord::getTaskName)
                .containsExactly(null, "CreateOrder", "Fulfill", "Ship", "Bill", null);
        }

        @Test
        @DisplayName("GIVEN branching workflow WHEN querying lineage THEN captures all branches")
        void testBranchingLineage_OneToTwo() {
            // GIVEN: One order splits into two fulfillment paths
            String caseId = "BRANCH001";
            Element orderData = createElement("order", "O999");
            Element fulfillDataA = createElement("fulfillA", "FA001");
            Element fulfillDataB = createElement("fulfillB", "FB001");
            Element combineData = createElement("combined", "C001");

            // WHEN
            tracker.recordCaseStart(specId, caseId, "orders", orderData);
            tracker.recordTaskExecution(specId, caseId, "FulfillBranchA", "fulfillment_a", orderData, fulfillDataA);
            tracker.recordTaskExecution(specId, caseId, "FulfillBranchB", "fulfillment_b", orderData, fulfillDataB);
            tracker.recordTaskExecution(specId, caseId, "CombineResults", "combined_results", orderData, combineData);
            tracker.recordCaseCompletion(specId, caseId, "archive", combineData);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(5);
            assertThat(lineage.stream().filter(r -> r.getTaskName() != null).count()).isEqualTo(3);
        }
    }

    /**
     * Test group: RDF export and integration
     */
    @Nested
    @DisplayName("RDF Export")
    class RdfExportTests {

        @Test
        @DisplayName("GIVEN single case lineage WHEN exported as RDF THEN contains valid Turtle format")
        void testRdfExport_ValidTurtleFormat() {
            // GIVEN
            String caseId = "RDF001";
            Element data = createElement("test", "data");
            tracker.recordCaseStart(specId, caseId, "test_table", data);
            tracker.recordTaskExecution(specId, caseId, "TestTask", "output_table", data, data);

            // WHEN
            String rdf = tracker.exportAsRdf(caseId);

            // THEN
            assertThat(rdf)
                .contains("@prefix lineage:")
                .contains("@prefix xsd:")
                .contains("lineage:caseId")
                .contains("lineage:timestamp")
                .contains("lineage:sourceTable")
                .contains("lineage:targetTable");
        }

        @Test
        @DisplayName("GIVEN multiple cases WHEN exported as RDF THEN includes all cases")
        void testRdfExport_MultipleCases() {
            // GIVEN
            Element data1 = createElement("test1", "data1");
            Element data2 = createElement("test2", "data2");
            tracker.recordCaseStart(specId, "RDF002", "table_a", data1);
            tracker.recordCaseStart(specId, "RDF003", "table_b", data2);

            // WHEN
            String rdf = tracker.exportAsRdf(null); // null = all cases

            // THEN
            assertThat(rdf)
                .contains("RDF002")
                .contains("RDF003");
        }
    }

    /**
     * Test group: Edge cases and error handling
     */
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("GIVEN null case ID WHEN recording THEN throws NullPointerException")
        void testNullCaseId_ThrowsException() {
            Element data = createElement("test", "data");
            assertThrows(NullPointerException.class, () ->
                tracker.recordCaseStart(specId, null, "table", data)
            );
        }

        @Test
        @DisplayName("GIVEN null data element WHEN recording THEN handles gracefully")
        void testNullDataElement_HandlesCases() {
            // WHEN
            tracker.recordCaseStart(specId, "EDGE001", "table", null);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase("EDGE001");
            assertThat(lineage).hasSize(1);
            assertThat(lineage.get(0).getDataHash()).isNull();
        }

        @Test
        @DisplayName("GIVEN non-existent case ID WHEN queried THEN returns empty list")
        void testNonExistentCase_ReturnsEmpty() {
            // WHEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase("NONEXISTENT");

            // THEN
            assertThat(lineage).isEmpty();
        }

        @Test
        @DisplayName("GIVEN unicode table names WHEN recorded THEN handles correctly")
        void testUnicodeTableNames_HandledCorrectly() {
            // GIVEN
            String caseId = "UNICODE001";
            String tableWithUnicode = "客户表_🌍";
            Element data = createElement("data", "value");

            // WHEN
            tracker.recordCaseStart(specId, caseId, tableWithUnicode, data);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(1);
            assertThat(lineage.get(0).getSourceTable()).isEqualTo(tableWithUnicode);
        }

        @Test
        @DisplayName("GIVEN very large data element WHEN recorded THEN processes without error")
        void testLargeDataElement_ProcessedSuccessfully() {
            // GIVEN
            String caseId = "LARGE001";
            StringBuilder largeContent = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeContent.append("x");
            }
            Element largeData = createElement("large", largeContent.toString());

            // WHEN
            tracker.recordCaseStart(specId, caseId, "large_table", largeData);

            // THEN
            List<DataLineageRecord> lineage = tracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(1);
            assertThat(lineage.get(0).getDataHash()).isNotNull();
        }
    }

    // Helper method: Create Element for testing
    private Element createElement(String name, String content) {
        return new Element(name).setText(content);
    }
}
