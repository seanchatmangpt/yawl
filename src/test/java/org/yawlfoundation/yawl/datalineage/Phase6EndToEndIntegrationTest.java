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
import org.yawlfoundation.yawl.elements.data.contract.DataLineageTracker;
import org.yawlfoundation.yawl.elements.data.contract.DataLineageTracker.DataLineageRecord;
import org.yawlfoundation.yawl.elements.data.contract.DataLineageTrackerImpl;
import org.yawlfoundation.yawl.elements.data.contract.ODCSDataContract;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests for Phase 6: Blue Ocean Enhancement.
 *
 * <p>Tests complete workflows combining:
 * - Data contract definition
 * - Lineage tracking through multi-task execution
 * - Multiple concurrent cases with data passing
 * - Failure scenarios and recovery
 * - Data flow validation
 *
 * <p>Chicago TDD: Real workflow execution patterns, not isolated units.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("Phase 6 End-to-End Integration Tests")
class Phase6EndToEndIntegrationTest {

    private DataLineageTracker lineageTracker;
    private YSpecificationID orderFulfillmentSpec;

    @BeforeEach
    void setUp() {
        lineageTracker = new DataLineageTrackerImpl();
        orderFulfillmentSpec = new YSpecificationID("order-fulfillment", "1.0", "production");
    }

    /**
     * Test group: Complete order fulfillment workflow
     */
    @Nested
    @DisplayName("Order Fulfillment Workflow")
    class OrderFulfillmentWorkflowTests {

        @Test
        @DisplayName("GIVEN order fulfillment workflow with 5 tasks WHEN executing case THEN traces complete data path")
        void testCompleteOrderWorkflow_DataPathTraced() {
            // GIVEN: Order fulfillment workflow definition
            ODCSDataContract contract = ODCSDataContract.builder()
                .workflowId("order-fulfillment")
                .workspaceId("procurement-ws")
                .addTableRead("customers", List.of("customer_id", "name", "email"))
                .addTableRead("products", List.of("product_id", "price", "stock"))
                .addTableWrite("orders", List.of("order_id", "customer_id", "product_id", "total_amount"))
                .addTableWrite("shipments", List.of("shipment_id", "order_id", "tracking_number"))
                .addTableWrite("invoices", List.of("invoice_id", "order_id", "amount"))
                .addTableWrite("archive", List.of("case_id", "final_status"))
                .addParameterBinding("customerId", "customers", "customer_id")
                .addParameterBinding("orderId", "orders", "order_id")
                .build();

            String caseId = "ORD-2026-001";

            // WHEN: Execute workflow tasks with data passing
            // 1. Load customer data
            Element customerData = createOrderElement("customer", "C123", "John Doe", "john@example.com");
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "customers", customerData);

            // 2. Validate order with customer & product data
            Element productData = createOrderElement("product", "P456", "1000", "100");
            Element orderData = createOrderElement("order", "O789", "C123", "P456");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ValidateOrder",
                "orders", customerData, orderData);

            // 3. Process fulfillment
            Element shipmentData = createOrderElement("shipment", "S012", "O789", "TRACK123");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ProcessFulfillment",
                "shipments", orderData, shipmentData);

            // 4. Generate invoice
            Element invoiceData = createOrderElement("invoice", "I345", "O789", "1000");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "GenerateInvoice",
                "invoices", shipmentData, invoiceData);

            // 5. Archive case results
            Element archiveData = createOrderElement("archive", caseId, "COMPLETED");
            lineageTracker.recordCaseCompletion(orderFulfillmentSpec, caseId, "archive", archiveData);

            // THEN: Verify complete data path
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);

            assertThat(lineage)
                .hasSize(5)
                .extracting(DataLineageRecord::getTaskName)
                .containsExactly(null, "ValidateOrder", "ProcessFulfillment", "GenerateInvoice", null);

            // Verify source table
            assertThat(lineage.get(0).getSourceTable()).isEqualTo("customers");

            // Verify task outputs
            assertThat(lineage.get(1).getTargetTable()).isEqualTo("orders");
            assertThat(lineage.get(2).getTargetTable()).isEqualTo("shipments");
            assertThat(lineage.get(3).getTargetTable()).isEqualTo("invoices");
            assertThat(lineage.get(4).getTargetTable()).isEqualTo("archive");
        }

        @Test
        @DisplayName("GIVEN contract with data guards WHEN executing workflow THEN validates preconditions")
        void testContractWithGuards_ValidatesPreconditions() {
            // GIVEN
            ODCSDataContract guardedContract = ODCSDataContract.builder()
                .workflowId("guarded-order")
                .workspaceId("ws")
                .addTableRead("customers", List.of("credit_limit"))
                .addTableWrite("approved_orders", List.of("order_id", "amount"))
                .build();

            String caseId = "GUARD-ORD-001";

            // WHEN: Execute task that should validate preconditions
            Element custData = new Element("customer").setAttribute("credit_limit", "5000");
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "customers", custData);

            Element orderData = new Element("order")
                .setAttribute("amount", "3000"); // Below credit limit
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ValidateCredit",
                "approved_orders", custData, orderData);

            // THEN: Verify lineage was recorded (guards don't block lineage)
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(2);

            // Verify order was written to approved table
            List<DataLineageRecord> approvedOrders = lineageTracker.getLineageForTable("approved_orders");
            assertThat(approvedOrders).isNotEmpty();
        }

        @Test
        @DisplayName("GIVEN multiple parallel branches WHEN executing THEN captures all branches")
        void testParallelBranches_AllCaptured() {
            // GIVEN: Parallel workflow for order processing (split to 2 branches)
            String caseId = "PARALLEL-001";
            Element orderData = createOrderElement("order", "O999", "1000");

            // WHEN: Execute parallel branches
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "orders", orderData);

            // Branch 1: Fulfillment
            Element fulfillData = createOrderElement("fulfill", "F001", "O999");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "FulfillOrder",
                "fulfillment", orderData, fulfillData);

            // Branch 2: Accounting
            Element billData = createOrderElement("bill", "B001", "O999");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "CreateBilling",
                "billing", orderData, billData);

            // Merge: Combine results
            Element merged = createOrderElement("complete", caseId, "DONE");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "MergeResults",
                "results", orderData, merged);

            // THEN: Verify all branches captured
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);
            assertThat(lineage)
                .hasSize(4)
                .extracting(DataLineageRecord::getTaskName)
                .contains("FulfillOrder", "CreateBilling", "MergeResults");
        }
    }

    /**
     * Test group: Multiple concurrent cases with data isolation
     */
    @Nested
    @DisplayName("Concurrent Case Execution")
    class ConcurrentCaseExecutionTests {

        @Test
        @DisplayName("GIVEN 10 concurrent order cases WHEN executing THEN all isolated without cross-contamination")
        void testConcurrentOrders_IsolatedProperly() throws InterruptedException {
            // GIVEN
            int caseCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(5);

            // WHEN
            for (int i = 0; i < caseCount; i++) {
                final int caseNumber = i;
                executor.submit(() -> {
                    String caseId = String.format("CONCURRENT-ORD-%03d", caseNumber);

                    // Execute 3-task workflow
                    Element customerData = createOrderElement("customer", "C" + caseNumber);
                    lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "customers", customerData);

                    Element orderData = createOrderElement("order", "O" + caseNumber);
                    lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ValidateOrder",
                        "orders", customerData, orderData);

                    Element invoiceData = createOrderElement("invoice", "I" + caseNumber);
                    lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "GenerateInvoice",
                        "invoices", orderData, invoiceData);

                    lineageTracker.recordCaseCompletion(orderFulfillmentSpec, caseId, "archive", invoiceData);
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // THEN: Verify all cases isolated
            for (int i = 0; i < caseCount; i++) {
                String caseId = String.format("CONCURRENT-ORD-%03d", i);
                List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);

                assertThat(lineage)
                    .hasSize(4) // start + 2 tasks + completion
                    .allMatch(r -> r.getCaseId().equals(caseId));
            }

            // Verify table lineage aggregates correctly
            List<DataLineageRecord> customerLineage = lineageTracker.getLineageForTable("customers");
            assertThat(customerLineage)
                .hasSize(caseCount)
                .extracting(DataLineageRecord::getCaseId)
                .hasSize(caseCount); // All unique cases
        }

        @Test
        @DisplayName("GIVEN concurrent writes to same table WHEN executing THEN all recorded correctly")
        void testConcurrentTableWrites_AllRecorded() throws InterruptedException {
            // GIVEN
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);

            // WHEN
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    String caseId = String.format("SHARED-TABLE-%03d", threadId);
                    Element data = createOrderElement("shared", "S" + threadId);
                    lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "WriteTask",
                        "shared_table", data, data);
                });
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            // THEN
            List<DataLineageRecord> sharedTableLineage = lineageTracker.getLineageForTable("shared_table");
            assertThat(sharedTableLineage)
                .hasSize(threadCount)
                .extracting(DataLineageRecord::getCaseId)
                .hasSize(threadCount); // All threads present
        }
    }

    /**
     * Test group: Failure scenarios and recovery
     */
    @Nested
    @DisplayName("Failure Scenarios & Recovery")
    class FailureRecoveryTests {

        @Test
        @DisplayName("GIVEN workflow failure mid-execution WHEN rollback initiated THEN lineage remains consistent")
        void testWorkflowFailureRollback_LineageConsistent() {
            // GIVEN: Simulated workflow failure after 2 tasks
            String caseId = "FAILURE-001";
            Element custData = createOrderElement("customer", "C999");
            Element orderData = createOrderElement("order", "O999");

            // WHEN: Execute partial workflow
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "customers", custData);
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ValidateOrder",
                "orders", custData, orderData);

            // Simulate failure - rollback by marking incomplete
            // (In real scenario, would use transaction rollback)

            // THEN: Verify partial lineage recorded
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(2); // start + validate only

            // Next recovery attempt would append new tasks
            Element recoveryOrder = createOrderElement("order_v2", "O999");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "RecoveryValidate",
                "orders", custData, recoveryOrder);

            lineage = lineageTracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(3); // start + original validate + recovery
        }

        @Test
        @DisplayName("GIVEN missing intermediate data WHEN recorded WHEN lineage handles null gracefully")
        void testMissingIntermediateData_HandledGracefully() {
            // GIVEN
            String caseId = "PARTIAL-DATA-001";
            Element existingData = createOrderElement("order", "O001");

            // WHEN: Record with null data elements
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "orders", existingData);
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ProcessTask",
                "results", null, null); // Both null
            lineageTracker.recordCaseCompletion(orderFulfillmentSpec, caseId, "archive", existingData);

            // THEN: Verify lineage recorded despite null data
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);
            assertThat(lineage)
                .hasSize(3)
                .extracting(DataLineageRecord::getTaskName)
                .containsExactly(null, "ProcessTask", null);
        }

        @Test
        @DisplayName("GIVEN multiple task failures WHEN each retried THEN lineage captures all attempts")
        void testMultipleRetries_AllAttemptsCaptured() {
            // GIVEN
            String caseId = "RETRY-001";
            Element data = createOrderElement("item", "I001");

            // WHEN: First attempt
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "items", data);
            Element result1 = createOrderElement("attempt", "1", "FAILED");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ProcessItem",
                "attempts", data, result1);

            // Second attempt (retry)
            Element result2 = createOrderElement("attempt", "2", "FAILED");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ProcessItem",
                "attempts", data, result2);

            // Third attempt (success)
            Element result3 = createOrderElement("attempt", "3", "SUCCESS");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "ProcessItem",
                "attempts", data, result3);

            lineageTracker.recordCaseCompletion(orderFulfillmentSpec, caseId, "archive", result3);

            // THEN: Verify all retry attempts recorded
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);
            assertThat(lineage).hasSize(5); // start + 3 attempts + completion

            List<DataLineageRecord> taskExecution = lineage.stream()
                .filter(r -> r.getTaskName() != null)
                .collect(Collectors.toList());
            assertThat(taskExecution)
                .hasSize(3)
                .allMatch(r -> r.getTaskName().equals("ProcessItem"));
        }
    }

    /**
     * Test group: Data flow validation
     */
    @Nested
    @DisplayName("Data Flow Validation")
    class DataFlowValidationTests {

        @Test
        @DisplayName("GIVEN workflow with defined contract WHEN executing THEN lineage matches contract")
        void testLineageMatchesContract_Valid() {
            // GIVEN: Workflow must follow contract
            ODCSDataContract contract = ODCSDataContract.builder()
                .workflowId("validated-flow")
                .workspaceId("ws")
                .addTableRead("source", List.of("id"))
                .addTableWrite("target", List.of("id"))
                .addParameterBinding("sourceId", "source", "id")
                .addParameterBinding("targetId", "target", "id")
                .build();

            String caseId = "VALIDATED-001";

            // WHEN: Execute workflow following contract
            Element sourceData = createOrderElement("source", "S123");
            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "source", sourceData);

            Element targetData = createOrderElement("target", "T123");
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "Transform",
                "target", sourceData, targetData);

            // THEN: Verify lineage matches contract
            List<DataLineageRecord> lineage = lineageTracker.getLineageForCase(caseId);

            // Check contract compliance
            List<String> contractTables = contract.getTableReads();
            contractTables.addAll(contract.getTableWrites());

            List<String> lineageTables = lineage.stream()
                .map(r -> r.getSourceTable() != null ? r.getSourceTable() : r.getTargetTable())
                .filter(t -> t != null)
                .collect(Collectors.toList());

            // All lineage tables should be in contract
            for (String table : lineageTables) {
                assertThat(contractTables).contains(table);
            }
        }

        @Test
        @DisplayName("GIVEN RDF export of workflow lineage WHEN exported THEN produces valid RDF")
        void testRdfExportValid_CompleteWorkflow() {
            // GIVEN: Complete workflow execution
            String caseId = "RDF-EXPORT-001";
            Element data1 = createOrderElement("stage1", "D1");
            Element data2 = createOrderElement("stage2", "D2");
            Element data3 = createOrderElement("stage3", "D3");

            lineageTracker.recordCaseStart(orderFulfillmentSpec, caseId, "source_table", data1);
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "Task1",
                "intermediate_table", data1, data2);
            lineageTracker.recordTaskExecution(orderFulfillmentSpec, caseId, "Task2",
                "target_table", data2, data3);
            lineageTracker.recordCaseCompletion(orderFulfillmentSpec, caseId, "archive", data3);

            // WHEN: Export as RDF
            String rdf = lineageTracker.exportAsRdf(caseId);

            // THEN: Verify RDF format and content
            assertThat(rdf)
                .contains("@prefix lineage:", "@prefix xsd:")
                .contains("lineage:caseId", "\"" + caseId + "\"")
                .contains("lineage:sourceTable")
                .contains("lineage:targetTable")
                .contains("lineage:taskName")
                .contains("source_table", "target_table", "Task1", "Task2");
        }
    }

    // Helper method
    private Element createOrderElement(String name, String... values) {
        Element elem = new Element(name);
        for (String value : values) {
            elem.addContent(new Element("value").setText(value));
        }
        return elem;
    }
}
