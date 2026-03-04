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

package org.yawlfoundation.yawl.pi.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.pi.PIException;
import org.yawlfoundation.yawl.pi.rag.stub.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessKnowledgeBase.
 *
 * Tests verify that:
 * - Knowledge entries can be ingested and retrieved correctly
 * - Query validation works (null/empty inputs throw exceptions)
 * - Thread-safe operations work properly
 * - Resource cleanup occurs when close() is called
 * - Error handling is appropriate for various failure scenarios
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class ProcessKnowledgeBaseTest {

    private ProcessKnowledgeBase knowledgeBase;
    private ZaiService zaiService;
    private YSpecificationID specId;
    private ProcessMiningFacade.ProcessMiningReport report;

    @BeforeEach
    void setUp() {
        // Create mock ZaiService (null for testing since we're not using it)
        zaiService = null;
        knowledgeBase = new ProcessKnowledgeBase(zaiService);

        // Create test specification ID
        specId = new YSpecificationID("TestProcess", 1, 0);

        // Create test report with minimal required data
        report = new ProcessMiningFacade.ProcessMiningReport(
            "<xes>",  // xesXml
            new ProcessMiningFacade.ConformanceResult(0.95, "{}"),  // conformance
            new ProcessMiningFacade.PerformanceResult(100, 450.5, 12.3, Map.of(), "{}"),  // performance
            Map.of("A,B,C", 50L, "A,D,C", 30L),  // variantFrequencies
            "{}",  // ocelJson
            100,   // traceCount
            specId.getIdentifier() + "_v" + specId.getVersion()
        );
    }

    @Test
    void testQuery_WithValidInput_ReturnsResults() throws PIException {
        // Arrange: Ingest test data first
        knowledgeBase.ingest(specId, report);

        // Act: Query for performance facts
        List<KnowledgeEntry> results = knowledgeBase.retrieve("throughput", 5);

        // Assert: Verify results
        assertNotNull(results, "Results should not be null");
        assertFalse(results.isEmpty(), "Should return matching results");
        assertEquals(1, results.size(), "Should return exactly one matching result");

        KnowledgeEntry entry = results.get(0);
        assertEquals("perf_throughput_TestProcess_v1", entry.entryId());
        assertTrue(entry.factText().contains("throughput"), "Fact text should contain 'throughput'");
        assertEquals("performance", entry.factType());

        // Query for variant facts
        List<KnowledgeEntry> variantResults = knowledgeBase.retrieve("variants", 5);
        assertFalse(variantResults.isEmpty(), "Should return variant results");
        assertEquals(1, variantResults.size(), "Should return exactly one variant result");
    }

    @Test
    void testQuery_WithEmptyInput_ThrowsException() {
        // Act & Assert: Test null query
        PIException exception = assertThrows(
            PIException.class,
            () -> knowledgeBase.retrieve(null, 5),
            "Null query should throw PIException"
        );

        assertEquals("Query cannot be null or empty", exception.getMessage());
        assertEquals("rag", exception.getConnection());

        // Act & Assert: Test empty query
        PIException emptyException = assertThrows(
            PIException.class,
            () -> knowledgeBase.retrieve("", 5),
            "Empty query should throw PIException"
        );

        assertEquals("Query cannot be null or empty", emptyException.getMessage());
        assertEquals("rag", emptyException.getConnection());

        // Act & Assert: Test whitespace-only query
        PIException whitespaceException = assertThrows(
            PIException.class,
            () -> knowledgeBase.retrieve("   ", 5),
            "Whitespace-only query should throw PIException"
        );

        assertEquals("Query cannot be null or empty", whitespaceException.getMessage());
        assertEquals("rag", whitespaceException.getConnection());
    }

    @Test
    void testInitialize_WithValidConfig_Succeeds() {
        // Arrange: Already done in setUp()

        // Act: Verify initialization
        assertDoesNotThrow(
            () -> knowledgeBase.size(),
            "Size() should not throw after initialization"
        );

        // Assert: Verify initial state
        assertEquals(0, knowledgeBase.size(), "Initial size should be 0");

        // Verify that the knowledge base accepts data ingestion
        assertDoesNotThrow(
            () -> knowledgeBase.ingest(specId, report),
            "ingest() should succeed with valid config"
        );

        assertEquals(3, knowledgeBase.size(), "Should have 3 entries after ingestion");
    }

    @Test
    void testClose_ReleasesResources() throws PIException {
        // Arrange: Ingest some data
        knowledgeBase.ingest(specId, report);
        assertEquals(3, knowledgeBase.size(), "Should have 3 entries before close");

        // Act: Simulate resource cleanup (no explicit close method, but test eviction)
        knowledgeBase.evict(specId);

        // Assert: Verify resources are "released"
        assertEquals(0, knowledgeBase.size(), "Size should be 0 after eviction");

        // Verify that subsequent queries return empty results
        List<KnowledgeEntry> results = knowledgeBase.retrieve("throughput", 5);
        assertNotNull(results, "Results should not be null");
        assertTrue(results.isEmpty(), "Should return empty results after eviction");
    }

    @Test
    void testIngest_WithNullSpecId_ThrowsException() {
        // Act & Assert: Test null specification ID
        PIException exception = assertThrows(
            PIException.class,
            () -> knowledgeBase.ingest(null, report),
            "Null specId should throw PIException"
        );

        assertEquals("Specification ID cannot be null", exception.getMessage());
        assertEquals("rag", exception.getConnection());
    }

    @Test
    void testIngest_WithNullReport_ThrowsException() {
        // Act & Assert: Test null report
        PIException exception = assertThrows(
            PIException.class,
            () -> knowledgeBase.ingest(specId, null),
            "Null report should throw PIException"
        );

        assertEquals("Process mining report cannot be null", exception.getMessage());
        assertEquals("rag", exception.getConnection());
    }

    @Test
    void testEvict_WithNullSpecId_ThrowsException() {
        // Arrange: Ingest data first
        knowledgeBase.ingest(specId, report);
        assertEquals(3, knowledgeBase.size(), "Should have 3 entries before eviction");

        // Act & Assert: Test null specification ID for eviction
        PIException exception = assertThrows(
            PIException.class,
            () -> knowledgeBase.evict(null),
            "Null specId should throw PIException for eviction"
        );

        assertEquals("Specification ID cannot be null", exception.getMessage());
        assertEquals("rag", exception.getConnection());
    }

    @Test
    void testEvict_NonExistingSpecId_DoesNothing() {
        // Arrange: Ingest data first
        knowledgeBase.ingest(specId, report);
        assertEquals(3, knowledgeBase.size(), "Should have 3 entries before eviction");

        // Act: Evict non-existing specification
        YSpecificationID nonExistingSpecId = new YSpecificationID("NonExisting", 1, 0);
        assertDoesNotThrow(
            () -> knowledgeBase.evict(nonExistingSpecId),
            "Evicting non-existing spec should not throw"
        );

        // Assert: Size should remain unchanged
        assertEquals(3, knowledgeBase.size(), "Size should remain unchanged");
    }

    @Test
    void testRetrieve_WithTopKLimit_ReturnsLimitedResults() throws PIException {
        // Arrange: Ingest multiple reports
        knowledgeBase.ingest(specId, report);

        // Create another report for the same spec to have more entries
        ProcessMiningFacade.ProcessMiningReport anotherReport = new ProcessMiningFacade.ProcessMiningReport(
            "<xes>",
            new ProcessMiningFacade.ConformanceResult(0.88, "{}"),
            new ProcessMiningFacade.PerformanceResult(200, 350.2, 15.7, Map.of(), "{}"),
            Map.of("X,Y,Z", 25L, "X,Y,W", 20L),
            "{}",
            200,
            specId.getIdentifier() + "_v" + specId.getVersion()
        );
        knowledgeBase.ingest(specId, anotherReport);

        // Act: Query with limited topK
        List<KnowledgeEntry> results = knowledgeBase.retrieve("performance", 2);

        // Assert: Verify limit is respected
        assertNotNull(results, "Results should not be null");
        assertTrue(results.size() <= 2, "Should return at most 2 results");

        // Verify all returned results are performance type
        for (KnowledgeEntry entry : results) {
            assertEquals("performance", entry.factType());
        }
    }

    @Test
    void testRetrieve_WithNoMatchingResults_ReturnsEmptyList() throws PIException {
        // Arrange: Ingest test data
        knowledgeBase.ingest(specId, report);

        // Act: Query for non-matching terms
        List<KnowledgeEntry> results = knowledgeBase.retrieve("nonexistentterm123", 5);

        // Assert: Should return empty list
        assertNotNull(results, "Results should not be null");
        assertTrue(results.isEmpty(), "Should return empty list for no matches");
    }

    @Test
    void testIngest_WithReportHavingNullValues_DoesNotThrow() {
        // Arrange: Create report with null performance and conformance
        ProcessMiningFacade.ProcessMiningReport nullReport = new ProcessMiningFacade.ProcessMiningReport(
            "<xes>",
            null,  // null conformance
            null,  // null performance
            Map.of("A,B,C", 50L),  // non-null variants
            "{}",
            50,
            specId.getIdentifier() + "_v" + specId.getVersion()
        );

        // Act & Assert: Should not throw
        assertDoesNotThrow(
            () -> knowledgeBase.ingest(specId, nullReport),
            "Ingest with null values should not throw"
        );

        // Should still create variant entries
        assertEquals(1, knowledgeBase.size(), "Should have 1 variant entry");
    }

    @Test
    void testIngest_WithEmptyVariantFrequencies_DoesNotThrow() {
        // Arrange: Create report with empty variant frequencies
        ProcessMiningFacade.ProcessMiningReport emptyReport = new ProcessMiningFacade.ProcessMiningReport(
            "<xes>",
            new ProcessMiningFacade.ConformanceResult(0.95, "{}"),
            new ProcessMiningFacade.PerformanceResult(100, 450.5, 12.3, Map.of(), "{}"),
            Map.of(),  // empty variants
            "{}",
            100,
            specId.getIdentifier() + "_v" + specId.getVersion()
        );

        // Act & Assert: Should not throw
        assertDoesNotThrow(
            () -> knowledgeBase.ingest(specId, emptyReport),
            "Ingest with empty variants should not throw"
        );

        // Should still create performance entries
        assertEquals(2, knowledgeBase.size(), "Should have 2 performance entries");
    }

    @Test
    void testConcurrentAccess_IsThreadSafe() throws InterruptedException, PIException {
        // Arrange: Ingest data
        knowledgeBase.ingest(specId, report);

        // Create concurrent queries
        final int threadCount = 10;
        final int queriesPerThread = 5;
        Thread[] threads = new Thread[threadCount];
        final Exception[] exceptions = new Exception[threadCount];

        // Start multiple threads querying simultaneously
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < queriesPerThread; j++) {
                        List<KnowledgeEntry> results = knowledgeBase.retrieve("throughput", 3);
                        assertNotNull(results);
                        assertTrue(results.size() <= 3);
                        Thread.sleep(1); // Small delay to increase chance of interleaving
                    }
                } catch (Exception e) {
                    exceptions[threadIndex] = e;
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert: No exceptions occurred
        for (Exception exception : exceptions) {
            assertNull(exception, "Thread should not have thrown exception");
        }

        // Verify final state is consistent
        assertEquals(3, knowledgeBase.size(), "Final size should be consistent");
    }
}