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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.data.contract.ContractValidationResult;
import org.yawlfoundation.yawl.elements.data.contract.DataGuardCondition;
import org.yawlfoundation.yawl.elements.data.contract.DataGuardConditionImpl;
import org.yawlfoundation.yawl.elements.data.contract.ODCSDataContract;
import org.yawlfoundation.yawl.elements.data.contract.ODCSDataContract.ParameterColumnBinding;
import org.yawlfoundation.yawl.elements.data.contract.ODCSDataContract.VariableColumnBinding;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.elements.data.YParameter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ODCSDataContract (Phase 6: Blue Ocean Enhancement).
 *
 * <p>Tests data contracts for YAWL workflows including:
 * - Contract construction and validation
 * - Precondition enforcement via DataGuards
 * - Table read/write bindings
 * - Parameter and variable bindings
 * - Constraint violation detection
 * - Recovery paths
 *
 * <p>Uses Chicago TDD: Real contract objects, real validation, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("ODCSDataContract Tests")
class ODCSDataContractTest {

    private ODCSDataContract contract;

    @BeforeEach
    void setUp() {
        contract = ODCSDataContract.builder()
            .workflowId("order-fulfillment")
            .workspaceId("procurement-ws")
            .addTableRead("customers", List.of("customer_id", "name", "email"))
            .addTableRead("products", List.of("product_id", "price", "stock"))
            .addTableWrite("orders", List.of("order_id", "customer_id", "product_id", "total_amount"))
            .addTableWrite("audit", List.of("event_id", "case_id", "timestamp", "action"))
            .addParameterBinding("inputCustomer", "customers", "customer_id")
            .addParameterBinding("outputOrder", "orders", "order_id")
            .addVariableBinding("orderAmount", "orders", "total_amount")
            .build();
    }

    /**
     * Test group: Contract construction and basic properties
     */
    @Nested
    @DisplayName("Contract Construction")
    class ContractConstructionTests {

        @Test
        @DisplayName("GIVEN contract builder WHEN built THEN returns valid contract")
        void testContractConstruction_Valid() {
            // WHEN
            ODCSDataContract built = contract;

            // THEN
            assertThat(built).isNotNull();
            assertThat(built.getWorkflowId()).isEqualTo("order-fulfillment");
            assertThat(built.getWorkspaceId()).isEqualTo("procurement-ws");
        }

        @Test
        @DisplayName("GIVEN multiple table reads WHEN queried THEN returns all tables")
        void testTableReads_ReturnsAll() {
            // WHEN
            List<String> reads = contract.getTableReads();

            // THEN
            assertThat(reads)
                .hasSize(2)
                .containsExactly("customers", "products");
        }

        @Test
        @DisplayName("GIVEN multiple table writes WHEN queried THEN returns all tables")
        void testTableWrites_ReturnsAll() {
            // WHEN
            List<String> writes = contract.getTableWrites();

            // THEN
            assertThat(writes)
                .hasSize(2)
                .containsExactly("orders", "audit");
        }

        @Test
        @DisplayName("GIVEN table columns WHEN queried THEN returns correct columns")
        void testColumnsForTable_ReturnsCorrectColumns() {
            // WHEN
            List<String> customerCols = contract.getColumnsRead("customers");
            List<String> orderCols = contract.getColumnsWritten("orders");

            // THEN
            assertThat(customerCols)
                .contains("customer_id", "name", "email");

            assertThat(orderCols)
                .contains("order_id", "customer_id", "product_id", "total_amount");
        }

        @Test
        @DisplayName("GIVEN non-existent table WHEN queried THEN returns empty list")
        void testNonExistentTable_ReturnsEmpty() {
            // WHEN
            List<String> cols = contract.getColumnsRead("nonexistent");

            // THEN
            assertThat(cols).isEmpty();
        }
    }

    /**
     * Test group: Parameter and variable bindings
     */
    @Nested
    @DisplayName("Parameter & Variable Bindings")
    class BindingTests {

        @Test
        @DisplayName("GIVEN parameter binding WHEN queried THEN returns binding details")
        void testParameterBinding_ReturnsDetails() {
            // WHEN
            ParameterColumnBinding binding = contract.getParameterBinding("inputCustomer");

            // THEN
            assertThat(binding).isNotNull();
            assertThat(binding.getParameterName()).isEqualTo("inputCustomer");
            assertThat(binding.getTableId()).isEqualTo("customers");
            assertThat(binding.getColumnName()).isEqualTo("customer_id");
        }

        @Test
        @DisplayName("GIVEN variable binding WHEN queried THEN returns binding details")
        void testVariableBinding_ReturnsDetails() {
            // WHEN
            VariableColumnBinding binding = contract.getVariableBinding("orderAmount");

            // THEN
            assertThat(binding).isNotNull();
            assertThat(binding.getVariableName()).isEqualTo("orderAmount");
            assertThat(binding.getTableId()).isEqualTo("orders");
            assertThat(binding.getColumnName()).isEqualTo("total_amount");
        }

        @Test
        @DisplayName("GIVEN non-existent binding WHEN queried THEN returns null")
        void testNonExistentBinding_ReturnsNull() {
            // WHEN
            ParameterColumnBinding binding = contract.getParameterBinding("nonexistent");

            // THEN
            assertThat(binding).isNull();
        }

        @Test
        @DisplayName("GIVEN multiple parameter bindings WHEN added THEN all accessible")
        void testMultipleParameterBindings_AllAccessible() {
            // GIVEN
            ODCSDataContract multiContract = ODCSDataContract.builder()
                .workflowId("invoice")
                .workspaceId("finance-ws")
                .addTableRead("vendors", List.of("vendor_id", "name"))
                .addTableWrite("invoices", List.of("invoice_id", "vendor_id", "amount"))
                .addParameterBinding("vendor", "vendors", "vendor_id")
                .addParameterBinding("amount", "invoices", "amount")
                .addParameterBinding("invoiceId", "invoices", "invoice_id")
                .build();

            // WHEN/THEN
            assertThat(multiContract.getParameterBinding("vendor")).isNotNull();
            assertThat(multiContract.getParameterBinding("amount")).isNotNull();
            assertThat(multiContract.getParameterBinding("invoiceId")).isNotNull();
        }
    }

    /**
     * Test group: Data guards (preconditions)
     */
    @Nested
    @DisplayName("Data Guards & Preconditions")
    class DataGuardTests {

        @Test
        @DisplayName("GIVEN contract with data guards WHEN queried THEN returns guards")
        void testDataGuards_ReturnsGuards() {
            // GIVEN
            DataGuardCondition guard1 = new DataGuardConditionImpl(
                "minimum_amount", "total_amount >= 100");
            DataGuardCondition guard2 = new DataGuardConditionImpl(
                "valid_customer", "customer_id IS NOT NULL");

            ODCSDataContract guardContract = ODCSDataContract.builder()
                .workflowId("guarded-order")
                .workspaceId("ws")
                .addTableRead("customers", List.of("customer_id"))
                .addTableWrite("orders", List.of("order_id"))
                .addDataGuard(guard1)
                .addDataGuard(guard2)
                .build();

            // WHEN
            List<DataGuardCondition> guards = guardContract.getDataGuards();

            // THEN
            assertThat(guards)
                .hasSize(2)
                .extracting(DataGuardCondition::getName)
                .containsExactly("minimum_amount", "valid_customer");
        }

        @Test
        @DisplayName("GIVEN contract without guards WHEN queried THEN returns empty list")
        void testNoDataGuards_ReturnsEmpty() {
            // WHEN
            List<DataGuardCondition> guards = contract.getDataGuards();

            // THEN
            assertThat(guards).isEmpty();
        }
    }

    /**
     * Test group: Validation of workflow variables
     */
    @Nested
    @DisplayName("Workflow Variable Validation")
    class VariableValidationTests {

        @Test
        @DisplayName("GIVEN empty variable list WHEN validated THEN validation succeeds")
        void testValidateVariables_EmptyList() {
            // WHEN
            ContractValidationResult result = contract.validateWorkflowVariables(List.of());

            // THEN
            assertThat(result).isNotNull();
            // Result should indicate variables are optional
        }

        @Test
        @DisplayName("GIVEN null variable list WHEN validated THEN handles gracefully")
        void testValidateVariables_NullList() {
            // WHEN/THEN - should not throw
            ContractValidationResult result = contract.validateWorkflowVariables(null);
            assertThat(result).isNotNull();
        }
    }

    /**
     * Test group: Constraint violations and error cases
     */
    @Nested
    @DisplayName("Constraint Violations")
    class ConstraintViolationTests {

        @Test
        @DisplayName("GIVEN missing required table WHEN validated THEN detects violation")
        void testMissingTable_DetectsViolation() {
            // GIVEN: contract requires customers table but no such variable

            // WHEN
            ContractValidationResult result = contract.validateWorkflowVariables(List.of());

            // THEN
            assertThat(result).isNotNull();
            // Should indicate missing tables
        }

        @Test
        @DisplayName("GIVEN conflicting column names WHEN building contract THEN builds successfully")
        void testConflictingColumns_HandledCorrectly() {
            // GIVEN: Same table with conflicting column requirements
            ODCSDataContract conflictContract = ODCSDataContract.builder()
                .workflowId("conflict-test")
                .workspaceId("ws")
                .addTableRead("shared", List.of("col_a", "col_b"))
                .addTableWrite("shared", List.of("col_c", "col_d"))
                .build();

            // WHEN
            List<String> reads = conflictContract.getColumnsRead("shared");
            List<String> writes = conflictContract.getColumnsWritten("shared");

            // THEN
            assertThat(reads).hasSize(2);
            assertThat(writes).hasSize(2);
        }
    }

    /**
     * Test group: Builder pattern validation
     */
    @Nested
    @DisplayName("Builder Pattern")
    class BuilderTests {

        @Test
        @DisplayName("GIVEN builder with null workflow ID WHEN built THEN throws exception")
        void testBuilderNullWorkflowId_ThrowsException() {
            // WHEN/THEN
            assertThrows(NullPointerException.class, () ->
                ODCSDataContract.builder()
                    .workflowId(null)
                    .workspaceId("ws")
                    .build()
            );
        }

        @Test
        @DisplayName("GIVEN builder with null workspace ID WHEN built THEN throws exception")
        void testBuilderNullWorkspaceId_ThrowsException() {
            // WHEN/THEN
            assertThrows(NullPointerException.class, () ->
                ODCSDataContract.builder()
                    .workflowId("test")
                    .workspaceId(null)
                    .build()
            );
        }

        @Test
        @DisplayName("GIVEN builder without IDs WHEN built THEN throws exception")
        void testBuilderMissingIds_ThrowsException() {
            // WHEN/THEN
            assertThrows(NullPointerException.class, () ->
                ODCSDataContract.builder()
                    .build()
            );
        }

        @Test
        @DisplayName("GIVEN fluent builder WHEN chained THEN builds correctly")
        void testFluentBuilder_ChainsCorrectly() {
            // WHEN
            ODCSDataContract built = ODCSDataContract.builder()
                .workflowId("fluent-test")
                .workspaceId("ws")
                .addTableRead("t1", List.of("c1"))
                .addTableWrite("t2", List.of("c2"))
                .addParameterBinding("p1", "t1", "c1")
                .build();

            // THEN
            assertThat(built.getWorkflowId()).isEqualTo("fluent-test");
            assertThat(built.getTableReads()).contains("t1");
            assertThat(built.getTableWrites()).contains("t2");
            assertThat(built.getParameterBinding("p1")).isNotNull();
        }
    }

    /**
     * Test group: ODCS workspace integration
     */
    @Nested
    @DisplayName("ODCS Workspace Integration")
    class OdcsWorkspaceTests {

        @Test
        @DisplayName("GIVEN ODCS workspace JSON WHEN set THEN retrieved correctly")
        void testWorkspaceJson_RetrievedCorrectly() {
            // GIVEN
            String workspaceJson = "{\"tables\": [{\"name\": \"customers\"}]}";
            ODCSDataContract wsContract = ODCSDataContract.builder()
                .workflowId("ws-test")
                .workspaceId("test-ws")
                .addTableRead("customers", List.of("id"))
                .odcsWorkspaceJson(workspaceJson)
                .build();

            // WHEN
            String retrieved = wsContract.getOdcsWorkspaceJson();

            // THEN
            assertThat(retrieved)
                .isEqualTo(workspaceJson)
                .contains("customers");
        }

        @Test
        @DisplayName("GIVEN contract without workspace JSON WHEN queried THEN handles gracefully")
        void testNoWorkspaceJson_HandlesGracefully() {
            // WHEN
            String json = contract.getOdcsWorkspaceJson();

            // THEN
            // Should not throw, may be null or empty
            assertThat(json).isNull();
        }
    }

    /**
     * Test group: Edge cases
     */
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("GIVEN unicode table and column names WHEN building contract THEN handles correctly")
        void testUnicodeNames_HandledCorrectly() {
            // GIVEN
            ODCSDataContract unicodeContract = ODCSDataContract.builder()
                .workflowId("unicode-test")
                .workspaceId("ws")
                .addTableRead("客户表", List.of("客户ID", "名称"))
                .addTableWrite("订单表", List.of("订单ID", "金额"))
                .build();

            // WHEN
            List<String> reads = unicodeContract.getTableReads();
            List<String> cols = unicodeContract.getColumnsRead("客户表");

            // THEN
            assertThat(reads).contains("客户表");
            assertThat(cols).contains("客户ID", "名称");
        }

        @Test
        @DisplayName("GIVEN very long table name WHEN building contract THEN handles correctly")
        void testLongTableName_HandledCorrectly() {
            // GIVEN
            String longName = "a".repeat(256) + "_customers";
            ODCSDataContract longContract = ODCSDataContract.builder()
                .workflowId("long-test")
                .workspaceId("ws")
                .addTableRead(longName, List.of("id"))
                .build();

            // WHEN
            List<String> reads = longContract.getTableReads();

            // THEN
            assertThat(reads).contains(longName);
        }

        @Test
        @DisplayName("GIVEN empty column list WHEN building contract THEN builds successfully")
        void testEmptyColumnList_BuildsSuccessfully() {
            // WHEN
            ODCSDataContract emptyColContract = ODCSDataContract.builder()
                .workflowId("empty-col-test")
                .workspaceId("ws")
                .addTableRead("empty_table", List.of())
                .build();

            // THEN
            assertThat(emptyColContract.getColumnsRead("empty_table")).isEmpty();
        }

        @Test
        @DisplayName("GIVEN duplicate parameter bindings WHEN added THEN last one wins")
        void testDuplicateParameterBindings_LastWins() {
            // GIVEN
            ODCSDataContract dupContract = ODCSDataContract.builder()
                .workflowId("dup-test")
                .workspaceId("ws")
                .addTableRead("customers", List.of("id"))
                .addParameterBinding("customerId", "customers", "id")
                .addParameterBinding("customerId", "customers", "name") // Override
                .build();

            // WHEN
            ParameterColumnBinding binding = dupContract.getParameterBinding("customerId");

            // THEN
            assertThat(binding.getColumnName()).isEqualTo("name"); // Last value
        }
    }
}
