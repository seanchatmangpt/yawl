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

import org.jdom2.Element;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.util.List;

/**
 * Tracks data lineage through workflow execution.
 *
 * <p><strong>Van der Aalst's Principle</strong>: Observable information systems must
 * provide data lineage — the ability to trace where data comes from and where it goes.
 * This interface enables:
 *
 * <ul>
 *   <li><strong>Provenance</strong>: "Which case executed Task X?"</li>
 *   <li><strong>Impact Analysis</strong>: "Which workflows touch the customers table?"</li>
 *   <li><strong>Debugging</strong>: "Trace case C123's data through Order→Fulfillment→Invoice"</li>
 *   <li><strong>Compliance</strong>: "Verify all PII data was processed correctly"</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * DataLineageTracker tracker = new DataLineageTrackerImpl();
 *
 * // Record case start: case C001 reads from customers table
 * tracker.recordCaseStart(specId, "C001", "customers", List.of("C001", "John Doe", "john@example.com"));
 *
 * // Record task execution: Task CheckCredit outputs order amount
 * tracker.recordTaskExecution(specId, "C001", "CheckCredit",
 *     "orders", List.of("total_amount"), new Element("order")...);
 *
 * // Query lineage: trace all data from case C001
 * List<DataLineageRecord> lineage = tracker.getLineageForCase("C001");
 * // Result: [
 * //   {timestamp, case C001, source: customers, data: C001...},
 * //   {timestamp, case C001, task: CheckCredit, target: orders, data: ...}
 * // ]
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface DataLineageTracker {

    /**
     * Records a case start event (reading from external data).
     *
     * @param specId the workflow specification identifier
     * @param caseId the case identifier
     * @param sourceTable the source table identifier
     * @param data the case data loaded from the source
     */
    void recordCaseStart(YSpecificationID specId, String caseId, String sourceTable, Element data);

    /**
     * Records a task execution event (reading from and/or writing to external data).
     *
     * @param specId the workflow specification identifier
     * @param caseId the case identifier
     * @param taskName the task name
     * @param targetTable the table being written to (null if no write)
     * @param sourceData the input data to the task
     * @param outputData the output data from the task (null if no output)
     */
    void recordTaskExecution(YSpecificationID specId, String caseId, String taskName,
                             String targetTable, Element sourceData, Element outputData);

    /**
     * Records a case completion event (writing final results to external data).
     *
     * @param specId the workflow specification identifier
     * @param caseId the case identifier
     * @param targetTable the target table identifier
     * @param data the final case data written
     */
    void recordCaseCompletion(YSpecificationID specId, String caseId, String targetTable, Element data);

    /**
     * Gets the lineage for a specific case.
     *
     * <p>Returns all data movements for a case in chronological order:
     * case start → task 1 → task 2 → ... → task N → case completion
     *
     * @param caseId the case identifier
     * @return list of lineage records, empty if case not found
     */
    List<DataLineageRecord> getLineageForCase(String caseId);

    /**
     * Gets all lineage records for a specific table.
     *
     * <p>Returns all cases and tasks that touched this table.
     *
     * @param tableId the table identifier
     * @return list of lineage records, empty if table not accessed
     */
    List<DataLineageRecord> getLineageForTable(String tableId);

    /**
     * Gets all lineage records for a specific task.
     *
     * <p>Returns all cases that executed this task.
     *
     * @param specId the workflow specification identifier
     * @param taskName the task name
     * @return list of lineage records, empty if task not executed
     */
    List<DataLineageRecord> getLineageForTask(YSpecificationID specId, String taskName);

    /**
     * Exports lineage as RDF for integration with observability systems.
     *
     * <p>Returns Turtle format suitable for loading into WorkflowDNAOracle.
     *
     * @param caseId the case identifier (null for all cases)
     * @return RDF/Turtle representation of lineage
     */
    String exportAsRdf(String caseId);

    /**
     * A single lineage record representing one data movement.
     */
    class DataLineageRecord {
        private final long timestamp;
        private final String caseId;
        private final String specId;
        private final String taskName;       // null for case start/completion
        private final String sourceTable;    // null for task execution output
        private final String targetTable;    // null for case start
        private final String dataHash;       // SHA-256 of data content

        public DataLineageRecord(long timestamp, String caseId, String specId,
                                String taskName, String sourceTable, String targetTable, String dataHash) {
            this.timestamp = timestamp;
            this.caseId = java.util.Objects.requireNonNull(caseId);
            this.specId = java.util.Objects.requireNonNull(specId);
            this.taskName = taskName;
            this.sourceTable = sourceTable;
            this.targetTable = targetTable;
            this.dataHash = dataHash;
        }

        public long getTimestamp() { return timestamp; }
        public String getCaseId() { return caseId; }
        public String getSpecId() { return specId; }
        public String getTaskName() { return taskName; }
        public String getSourceTable() { return sourceTable; }
        public String getTargetTable() { return targetTable; }
        public String getDataHash() { return dataHash; }

        @Override
        public String toString() {
            return "DataLineageRecord{" +
                "timestamp=" + timestamp +
                ", caseId='" + caseId + '\'' +
                ", taskName='" + taskName + '\'' +
                ", sourceTable='" + sourceTable + '\'' +
                ", targetTable='" + targetTable + '\'' +
                '}';
        }
    }
}
