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

package org.yawlfoundation.yawl.schema;

import org.jdom2.Document;
import org.yawlfoundation.yawl.datamodelling.model.WorkspaceModel;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.spi.TaskSchemaInterceptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validates YAWL task data against ODCS schema contracts at task execution boundaries.
 *
 * <p>Implements {@link TaskSchemaInterceptor} to be registered with
 * {@link org.yawlfoundation.yawl.engine.YNetRunner}. For each task that declares
 * {@code schema.input} or {@code schema.output} decomposition attributes, this
 * interceptor loads the corresponding ODCS contract, extracts the task's data fields,
 * and validates them against the contract's declared columns.</p>
 *
 * <p>Thread-safe: the underlying {@link SchemaContractRegistry} uses a
 * {@link java.util.concurrent.ConcurrentHashMap} for the contract cache; this class
 * itself holds no mutable state.</p>
 *
 * @see SchemaContractRegistry
 * @see SchemaContractValidator
 * @see TaskSchemaViolationException
 * @since 6.0.0
 */
public final class SchemaValidationInterceptor implements TaskSchemaInterceptor {

    private final SchemaContractRegistry _registry;

    /**
     * Creates a new interceptor backed by the given registry.
     *
     * @param registry the contract registry to use for contract lookup and caching
     */
    public SchemaValidationInterceptor(SchemaContractRegistry registry) {
        _registry = registry;
    }

    /**
     * Validates the task's input data against its declared input ODCS contract.
     * Called synchronously before the task announcement; throws to abort task enablement.
     *
     * @param item the work item being enabled (input data and attributes already set)
     * @throws TaskSchemaViolationException if required input fields are missing
     */
    @Override
    public void beforeTaskExecution(YWorkItem item) {
        Map<String, String> attrs = item.getAttributes();
        Optional<WorkspaceModel> contractOpt = _registry.inputContract(attrs);
        if (contractOpt.isEmpty()) {
            return; // No input contract declared — skip validation
        }
        WorkspaceModel contract = contractOpt.get();
        Map<String, String> actual = DataFieldExtractor.fromElement(item.getDataElement());
        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);
        if (!violations.isEmpty()) {
            throw new TaskSchemaViolationException(
                    item.getTaskID(),
                    item.getCaseID().toString(),
                    attrs.get(SchemaContractRegistry.ATTR_SCHEMA_INPUT),
                    true,
                    violations
            );
        }
    }

    /**
     * Validates the task's output data against its declared output ODCS contract.
     * Called synchronously after task completion; throws to abort net progression.
     *
     * @param item       the completing work item (attributes still available)
     * @param outputData the data document produced by the task implementation
     * @throws TaskSchemaViolationException if required output fields are missing
     */
    @Override
    public void afterTaskCompletion(YWorkItem item, Document outputData) {
        Map<String, String> attrs = item.getAttributes();
        Optional<WorkspaceModel> contractOpt = _registry.outputContract(attrs);
        if (contractOpt.isEmpty()) {
            return; // No output contract declared — skip validation
        }
        WorkspaceModel contract = contractOpt.get();
        Map<String, String> actual = DataFieldExtractor.fromDocument(outputData);
        List<SchemaViolation> violations = SchemaContractValidator.validate(actual, contract);
        if (!violations.isEmpty()) {
            throw new TaskSchemaViolationException(
                    item.getTaskID(),
                    item.getCaseID().toString(),
                    attrs.get(SchemaContractRegistry.ATTR_SCHEMA_OUTPUT),
                    false,
                    violations
            );
        }
    }
}
