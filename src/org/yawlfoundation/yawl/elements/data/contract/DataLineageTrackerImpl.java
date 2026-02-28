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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Concrete implementation of DataLineageTracker.
 *
 * <p>Thread-safe implementation using concurrent collections.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class DataLineageTrackerImpl implements DataLineageTracker {

    private static final Logger log = LoggerFactory.getLogger(DataLineageTrackerImpl.class);

    // Records grouped by case ID for efficient lookup
    private final Map<String, List<DataLineageRecord>> caseLineage = new ConcurrentHashMap<>();

    // Records grouped by table ID for impact analysis
    private final Map<String, List<DataLineageRecord>> tableLineage = new ConcurrentHashMap<>();

    // Records grouped by task for query optimization
    private final Map<String, List<DataLineageRecord>> taskLineage = new ConcurrentHashMap<>();

    // All records in insertion order for RDF export
    private final List<DataLineageRecord> allRecords = new CopyOnWriteArrayList<>();

    @Override
    public void recordCaseStart(YSpecificationID specId, String caseId, String sourceTable, Element data) {
        String dataHash = hashElement(data);
        DataLineageRecord record = new DataLineageRecord(
            System.currentTimeMillis(),
            caseId,
            specId.getIdentifier(),
            null,  // no task name for case start
            sourceTable,
            null,  // no target table for case start
            dataHash
        );

        addRecord(record);
        log.debug("Recorded case start: caseId={}, sourceTable={}", caseId, sourceTable);
    }

    @Override
    public void recordTaskExecution(YSpecificationID specId, String caseId, String taskName,
                                     String targetTable, Element sourceData, Element outputData) {
        String sourceHash = hashElement(sourceData);
        String targetHash = hashElement(outputData);

        DataLineageRecord record = new DataLineageRecord(
            System.currentTimeMillis(),
            caseId,
            specId.getIdentifier(),
            taskName,
            null,  // no source table (source is workflow variable)
            targetTable,
            targetHash != null ? targetHash : sourceHash
        );

        addRecord(record);
        log.debug("Recorded task execution: caseId={}, taskName={}, targetTable={}", caseId, taskName, targetTable);
    }

    @Override
    public void recordCaseCompletion(YSpecificationID specId, String caseId, String targetTable, Element data) {
        String dataHash = hashElement(data);
        DataLineageRecord record = new DataLineageRecord(
            System.currentTimeMillis(),
            caseId,
            specId.getIdentifier(),
            null,  // no task name for case completion
            null,  // no source table (source is workflow variables)
            targetTable,
            dataHash
        );

        addRecord(record);
        log.debug("Recorded case completion: caseId={}, targetTable={}", caseId, targetTable);
    }

    @Override
    public List<DataLineageRecord> getLineageForCase(String caseId) {
        List<DataLineageRecord> records = caseLineage.getOrDefault(caseId, Collections.emptyList());
        return new ArrayList<>(records);
    }

    @Override
    public List<DataLineageRecord> getLineageForTable(String tableId) {
        List<DataLineageRecord> records = tableLineage.getOrDefault(tableId, Collections.emptyList());
        return new ArrayList<>(records);
    }

    @Override
    public List<DataLineageRecord> getLineageForTask(YSpecificationID specId, String taskName) {
        String key = specId.getIdentifier() + ":" + taskName;
        List<DataLineageRecord> records = taskLineage.getOrDefault(key, Collections.emptyList());
        return new ArrayList<>(records);
    }

    @Override
    public String exportAsRdf(String caseId) {
        StringBuilder rdf = new StringBuilder();
        rdf.append("@prefix lineage: <http://yawl.org/lineage#> .\n");
        rdf.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
        rdf.append("\n");

        List<DataLineageRecord> records = caseId != null
            ? getLineageForCase(caseId)
            : new ArrayList<>(allRecords);

        for (int i = 0; i < records.size(); i++) {
            DataLineageRecord record = records.get(i);
            String recordId = "lineage:Record_" + record.getCaseId() + "_" + i;

            rdf.append(recordId).append("\n");
            rdf.append("  lineage:caseId \"").append(record.getCaseId()).append("\" ;\n");
            rdf.append("  lineage:timestamp ").append(record.getTimestamp())
                .append("^^xsd:long ;\n");

            if (record.getTaskName() != null) {
                rdf.append("  lineage:taskName \"").append(record.getTaskName()).append("\" ;\n");
            }
            if (record.getSourceTable() != null) {
                rdf.append("  lineage:sourceTable \"").append(record.getSourceTable()).append("\" ;\n");
            }
            if (record.getTargetTable() != null) {
                rdf.append("  lineage:targetTable \"").append(record.getTargetTable()).append("\" ;\n");
            }
            if (record.getDataHash() != null) {
                rdf.append("  lineage:dataHash \"").append(record.getDataHash()).append("\" ;\n");
            }

            rdf.append("  lineage:specId \"").append(record.getSpecId()).append("\" .\n\n");
        }

        return rdf.toString();
    }

    /**
     * Adds a record to all tracking maps.
     */
    private void addRecord(DataLineageRecord record) {
        allRecords.add(record);

        // Index by case
        caseLineage.computeIfAbsent(record.getCaseId(), k -> new CopyOnWriteArrayList<>())
            .add(record);

        // Index by source table
        if (record.getSourceTable() != null) {
            tableLineage.computeIfAbsent(record.getSourceTable(), k -> new CopyOnWriteArrayList<>())
                .add(record);
        }

        // Index by target table
        if (record.getTargetTable() != null) {
            tableLineage.computeIfAbsent(record.getTargetTable(), k -> new CopyOnWriteArrayList<>())
                .add(record);
        }

        // Index by task
        if (record.getTaskName() != null) {
            String key = record.getSpecId() + ":" + record.getTaskName();
            taskLineage.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .add(record);
        }
    }

    /**
     * Computes a hash of an Element's content.
     */
    private String hashElement(Element data) {
        if (data == null) {
            return null;
        }
        try {
            String content = data.getText();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 not available, using base64 of content", e);
            return Base64.getEncoder().encodeToString(data.getText().getBytes());
        }
    }
}
