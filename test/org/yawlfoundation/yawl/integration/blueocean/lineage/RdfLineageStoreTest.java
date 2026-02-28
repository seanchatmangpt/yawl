/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.integration.blueocean.lineage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RdfLineageStore.
 */
public class RdfLineageStoreTest {
    private RdfLineageStore store;

    @TempDir
    Path tdb2Dir;

    @TempDir
    Path luceneDir;

    @BeforeEach
    void setUp() {
        store = new RdfLineageStore(
                tdb2Dir.toString(),
                luceneDir.toString());
    }

    @Test
    void testRecordDataAccess() {
        // Given
        String caseId = "case-001";
        String taskId = "task-001";
        String tableId = "customers";
        String[] columns = {"id", "name", "email"};
        Instant timestamp = Instant.now();

        // When
        store.recordDataAccess(caseId, taskId, tableId, columns, "READ", timestamp);

        // Then - verify recording succeeds without throwing
        assertDoesNotThrow(() ->
                store.recordDataAccess(caseId, taskId, tableId, columns, "WRITE", timestamp));
    }

    @Test
    void testRecordTaskCompletion() {
        // Given
        String caseId = "case-001";
        String taskId = "task-001";
        String outputData = "{\"status\": \"completed\", \"records\": 42}";

        // When/Then
        assertDoesNotThrow(() ->
                store.recordTaskCompletion(caseId, taskId, outputData));
    }

    @Test
    void testQueryLineage() {
        // Given
        String tableId = "orders";
        recordTestData();

        // When
        List<RdfLineageStore.LineagePath> paths = store.queryLineage(tableId, 2);

        // Then
        assertNotNull(paths);
        // Should handle empty results gracefully
    }

    @Test
    void testQueryCaseLineage() {
        // Given
        String caseId = "case-001";
        recordTestData();

        // When
        String graph = store.queryCaseLineage(caseId);

        // Then
        assertNotNull(graph);
        assertNotEquals("", graph);
    }

    @Test
    void testGetLineageImpact() {
        // Given
        recordTestData();
        String sourceTableId = "customers";

        // When
        Map<String, String> impact = store.getLineageImpact(sourceTableId, 2);

        // Then
        assertNotNull(impact);
    }

    @Test
    void testInvalidOperationThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                store.recordDataAccess("case", "task", "table", new String[]{},
                        "INVALID_OP", Instant.now()));
    }

    @Test
    void testEmptyOutputDataThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                store.recordTaskCompletion("case", "task", ""));
    }

    @Test
    void testInvalidLineageDepthThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                store.queryLineage("table", 0));  // depth must be 1-10
    }

    @Test
    void testExportLineageGraph() {
        // Given
        recordTestData();

        // When
        String rdf = store.exportLineageGraph("TTL");

        // Then
        assertNotNull(rdf);
    }

    @Test
    void testSearchLineage() {
        // Given
        recordTestData();

        // When
        List<String> results = store.searchLineage("table:customers", 10);

        // Then
        assertNotNull(results);
    }

    @Test
    void testCloseable() {
        // When/Then - verify store closes without throwing
        assertDoesNotThrow(() -> store.close());
    }

    // Helper method to populate test data
    private void recordTestData() {
        Instant now = Instant.now();
        store.recordDataAccess("case-001", "task-001", "customers",
                new String[]{"id", "name"}, "READ", now);
        store.recordDataAccess("case-001", "task-002", "orders",
                new String[]{"id", "customer_id"}, "WRITE", now.plusSeconds(1));
        store.recordTaskCompletion("case-001", "task-001", "{\"status\": \"complete\"}");
    }
}
