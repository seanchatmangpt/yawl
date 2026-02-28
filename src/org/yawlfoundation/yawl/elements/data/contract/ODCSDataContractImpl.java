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

import java.util.*;

/**
 * Concrete implementation of ODCSDataContract.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
final class ODCSDataContractImpl implements ODCSDataContract {

    private final String workflowId;
    private final String workspaceId;
    private final Map<String, List<String>> tableReads;
    private final Map<String, List<String>> tableWrites;
    private final Map<String, ParameterColumnBinding> parameterBindings;
    private final Map<String, VariableColumnBinding> variableBindings;
    private final List<DataGuardCondition> dataGuards;
    private final String odcsWorkspaceJson;

    ODCSDataContractImpl(Builder builder) {
        this.workflowId = builder.workflowId;
        this.workspaceId = builder.workspaceId;
        this.tableReads = Collections.unmodifiableMap(new HashMap<>(builder.tableReads));
        this.tableWrites = Collections.unmodifiableMap(new HashMap<>(builder.tableWrites));
        this.parameterBindings = Collections.unmodifiableMap(new HashMap<>(builder.parameterBindings));
        this.variableBindings = Collections.unmodifiableMap(new HashMap<>(builder.variableBindings));
        this.dataGuards = Collections.unmodifiableList(new ArrayList<>(builder.dataGuards));
        this.odcsWorkspaceJson = builder.odcsWorkspaceJson;
    }

    @Override
    public String getWorkflowId() {
        return workflowId;
    }

    @Override
    public String getWorkspaceId() {
        return workspaceId;
    }

    @Override
    public List<String> getTableReads() {
        return new ArrayList<>(tableReads.keySet());
    }

    @Override
    public List<String> getTableWrites() {
        return new ArrayList<>(tableWrites.keySet());
    }

    @Override
    public List<String> getColumnsRead(String tableId) {
        return new ArrayList<>(tableReads.getOrDefault(tableId, Collections.emptyList()));
    }

    @Override
    public List<String> getColumnsWritten(String tableId) {
        return new ArrayList<>(tableWrites.getOrDefault(tableId, Collections.emptyList()));
    }

    @Override
    public ParameterColumnBinding getParameterBinding(String parameterName) {
        return parameterBindings.get(parameterName);
    }

    @Override
    public VariableColumnBinding getVariableBinding(String variableName) {
        return variableBindings.get(variableName);
    }

    @Override
    public List<DataGuardCondition> getDataGuards() {
        return new ArrayList<>(dataGuards);
    }

    @Override
    public ContractValidationResult validateWorkflowVariables(List<YVariable> variables) {
        ContractValidationResult result = new ContractValidationResult();

        for (YVariable var : variables) {
            VariableColumnBinding binding = variableBindings.get(var.getName());
            if (binding != null) {
                // Variable is bound to a column - validate it exists in the contract
                String tableId = binding.getTableId();
                String columnName = binding.getColumnName();

                boolean isRead = tableReads.getOrDefault(tableId, Collections.emptyList())
                    .contains(columnName);
                boolean isWritten = tableWrites.getOrDefault(tableId, Collections.emptyList())
                    .contains(columnName);

                if (!isRead && !isWritten) {
                    result.addError("Variable '" + var.getName() + "' bound to " +
                        tableId + "." + columnName + " but column not in contract");
                }
            }
        }

        return result;
    }

    @Override
    public ContractValidationResult validateWorkflowParameters(List<YParameter> parameters) {
        ContractValidationResult result = new ContractValidationResult();

        for (YParameter param : parameters) {
            ParameterColumnBinding binding = parameterBindings.get(param.getName());
            if (binding != null) {
                // Parameter is bound to a column - validate it
                String tableId = binding.getTableId();
                String columnName = binding.getColumnName();

                boolean isRead = tableReads.getOrDefault(tableId, Collections.emptyList())
                    .contains(columnName);
                boolean isWritten = tableWrites.getOrDefault(tableId, Collections.emptyList())
                    .contains(columnName);

                if (!isRead && !isWritten) {
                    result.addError("Parameter '" + param.getName() + "' bound to " +
                        tableId + "." + columnName + " but column not in contract");
                }
            }
        }

        return result;
    }

    @Override
    public String getOdcsWorkspaceJson() {
        return odcsWorkspaceJson;
    }

    @Override
    public String toString() {
        return "ODCSDataContract{" +
            "workflowId='" + workflowId + '\'' +
            ", workspaceId='" + workspaceId + '\'' +
            ", tableReads=" + tableReads.keySet() +
            ", tableWrites=" + tableWrites.keySet() +
            '}';
    }
}
