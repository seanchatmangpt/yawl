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

package org.yawlfoundation.yawl.elements.data.contract;

import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.data.YVariable;

import java.util.List;
import java.util.Objects;

/**
 * Declares the data contract between a YAWL workflow and ODCS (Offene Datenmodellierung
 * Data Contract Standard) tables/columns.
 *
 * <p>This interface formalizes the binding between:
 * <ul>
 *   <li>Workflow variables (YVariable, YParameter) → ODCS table columns</li>
 *   <li>Task inputs/outputs → table read/write operations</li>
 *   <li>Case lifecycle → data state transitions</li>
 * </ul>
 *
 * <p><strong>Van der Aalst's Principle</strong>: Process-aware information systems must
 * formally declare their data dependencies. This interface enables workflows to state
 * "I read from table T1.column C1, write to table T2.column C2" with full type checking.
 *
 * <p><strong>Example</strong>:
 * <pre>{@code
 * ODCSDataContract contract = ODCSDataContract.builder()
 *     .workflowId("order-fulfillment")
 *     .addTableRead("customers", List.of("customer_id", "name", "email"))
 *     .addTableWrite("orders", List.of("order_id", "customer_id", "total_amount"))
 *     .addParameterBinding("inputCustomer", "customers", "customer_id")
 *     .addParameterBinding("outputOrder", "orders", "order_id")
 *     .build();
 *
 * // Validate that workflow variables match contract
 * contract.validateWorkflowVariables(workflowVars);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface ODCSDataContract {

    /**
     * Gets the unique workflow identifier for this contract.
     * @return workflow ID (e.g., "order-fulfillment", "customer-onboarding")
     */
    String getWorkflowId();

    /**
     * Gets the ODCS workspace/domain this contract references.
     * @return workspace name or ID
     */
    String getWorkspaceId();

    /**
     * Gets all tables that this workflow reads from.
     * @return list of table identifiers (table_name or schema.table_name)
     */
    List<String> getTableReads();

    /**
     * Gets all tables that this workflow writes to.
     * @return list of table identifiers
     */
    List<String> getTableWrites();

    /**
     * Gets the specific columns read from a table.
     * @param tableId the table identifier
     * @return list of column names, or empty list if table not read
     */
    List<String> getColumnsRead(String tableId);

    /**
     * Gets the specific columns written to a table.
     * @param tableId the table identifier
     * @return list of column names, or empty list if table not written
     */
    List<String> getColumnsWritten(String tableId);

    /**
     * Gets the binding between a workflow parameter and a table column.
     *
     * <p>For example: parameter "customerId" maps to table "customers" column "id"
     *
     * @param parameterName the workflow parameter name
     * @return binding information, or null if not bound
     */
    ParameterColumnBinding getParameterBinding(String parameterName);

    /**
     * Gets the binding between a workflow variable and a table column.
     *
     * @param variableName the workflow variable name
     * @return binding information, or null if not bound
     */
    VariableColumnBinding getVariableBinding(String variableName);

    /**
     * Gets data guards (preconditions) that must be satisfied before a task executes.
     * @return list of guards, empty if none
     */
    List<DataGuardCondition> getDataGuards();

    /**
     * Validates that workflow variables match this contract's column bindings.
     *
     * <p>Checks:
     * <ul>
     *   <li>All required columns have corresponding variables</li>
     *   <li>Variable data types match column types</li>
     *   <li>Parameter directions (input/output) match table operations</li>
     * </ul>
     *
     * @param variables list of workflow variables to validate
     * @return validation result with pass/fail and detailed errors
     */
    ContractValidationResult validateWorkflowVariables(List<YVariable> variables);

    /**
     * Validates that workflow parameters match this contract's column bindings.
     *
     * @param parameters list of workflow parameters to validate
     * @return validation result with pass/fail and detailed errors
     */
    ContractValidationResult validateWorkflowParameters(List<YParameter> parameters);

    /**
     * Gets the ODCS workspace JSON that defines the tables in this contract.
     * Can be used to reconstruct schema information via DataModellingBridge.
     *
     * @return ODCS workspace JSON string
     */
    String getOdcsWorkspaceJson();

    /**
     * Creates a builder for constructing ODCSDataContract instances.
     * @return builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ODCSDataContract instances.
     */
    class Builder {
        private String workflowId;
        private String workspaceId;
        private final java.util.Map<String, List<String>> tableReads = new java.util.HashMap<>();
        private final java.util.Map<String, List<String>> tableWrites = new java.util.HashMap<>();
        private final java.util.Map<String, ParameterColumnBinding> parameterBindings = new java.util.HashMap<>();
        private final java.util.Map<String, VariableColumnBinding> variableBindings = new java.util.HashMap<>();
        private final java.util.List<DataGuardCondition> dataGuards = new java.util.ArrayList<>();
        private String odcsWorkspaceJson;

        public Builder workflowId(String workflowId) {
            this.workflowId = Objects.requireNonNull(workflowId, "workflowId required");
            return this;
        }

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId required");
            return this;
        }

        public Builder addTableRead(String tableId, List<String> columns) {
            this.tableReads.put(tableId, new java.util.ArrayList<>(columns));
            return this;
        }

        public Builder addTableWrite(String tableId, List<String> columns) {
            this.tableWrites.put(tableId, new java.util.ArrayList<>(columns));
            return this;
        }

        public Builder addParameterBinding(String paramName, String tableId, String columnName) {
            this.parameterBindings.put(paramName,
                new ParameterColumnBinding(paramName, tableId, columnName));
            return this;
        }

        public Builder addVariableBinding(String varName, String tableId, String columnName) {
            this.variableBindings.put(varName,
                new VariableColumnBinding(varName, tableId, columnName));
            return this;
        }

        public Builder addDataGuard(DataGuardCondition guard) {
            this.dataGuards.add(Objects.requireNonNull(guard, "guard required"));
            return this;
        }

        public Builder odcsWorkspaceJson(String json) {
            this.odcsWorkspaceJson = json;
            return this;
        }

        public ODCSDataContract build() {
            Objects.requireNonNull(workflowId, "workflowId required");
            Objects.requireNonNull(workspaceId, "workspaceId required");
            return new ODCSDataContractImpl(this);
        }
    }

    /**
     * Binding between a workflow parameter and an ODCS table column.
     */
    class ParameterColumnBinding {
        private final String parameterName;
        private final String tableId;
        private final String columnName;

        public ParameterColumnBinding(String parameterName, String tableId, String columnName) {
            this.parameterName = Objects.requireNonNull(parameterName);
            this.tableId = Objects.requireNonNull(tableId);
            this.columnName = Objects.requireNonNull(columnName);
        }

        public String getParameterName() { return parameterName; }
        public String getTableId() { return tableId; }
        public String getColumnName() { return columnName; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParameterColumnBinding)) return false;
            ParameterColumnBinding that = (ParameterColumnBinding) o;
            return parameterName.equals(that.parameterName) &&
                   tableId.equals(that.tableId) &&
                   columnName.equals(that.columnName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parameterName, tableId, columnName);
        }
    }

    /**
     * Binding between a workflow variable and an ODCS table column.
     */
    class VariableColumnBinding {
        private final String variableName;
        private final String tableId;
        private final String columnName;

        public VariableColumnBinding(String variableName, String tableId, String columnName) {
            this.variableName = Objects.requireNonNull(variableName);
            this.tableId = Objects.requireNonNull(tableId);
            this.columnName = Objects.requireNonNull(columnName);
        }

        public String getVariableName() { return variableName; }
        public String getTableId() { return tableId; }
        public String getColumnName() { return columnName; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VariableColumnBinding)) return false;
            VariableColumnBinding that = (VariableColumnBinding) o;
            return variableName.equals(that.variableName) &&
                   tableId.equals(that.tableId) &&
                   columnName.equals(that.columnName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(variableName, tableId, columnName);
        }
    }
}
