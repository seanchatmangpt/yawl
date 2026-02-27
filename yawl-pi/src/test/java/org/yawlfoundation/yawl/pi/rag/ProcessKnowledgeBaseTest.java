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
import org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade;
import org.yawlfoundation.yawl.integration.processmining.PerformanceAnalyzer;
import org.yawlfoundation.yawl.pi.PIException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProcessKnowledgeBase.
 *
 * Tests verify that process mining reports are correctly ingested,
 * facts are retrieved, and knowledge bases can be evicted.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class ProcessKnowledgeBaseTest {

    private ProcessKnowledgeBase knowledgeBase;
    private YSpecificationID specId;
    private ProcessMiningFacade.ProcessMiningReport testReport;

    @BeforeEach
    void setUp() throws Exception {
        knowledgeBase = new ProcessKnowledgeBase(null);
        specId = new YSpecificationID("test_spec", "1.0", "http://test.spec");
        testReport = createTestReport();
    }

    @Test
    void testIngestCreatesKnowledgeEntries() throws PIException {
        assertEquals(0, knowledgeBase.size());

        knowledgeBase.ingest(specId, testReport);

        assertTrue(knowledgeBase.size() > 0);
    }

    @Test
    void testIngestExtracts_PerformanceMetrics() throws PIException {
        knowledgeBase.ingest(specId, testReport);

        List<KnowledgeEntry> entries = knowledgeBase.retrieve("flow time", 5);
        assertTrue(entries.stream()
            .anyMatch(e -> e.factType().equals("performance")));
    }

    @Test
    void testIngestExtracts_VariantInformation() throws PIException {
        knowledgeBase.ingest(specId, testReport);

        List<KnowledgeEntry> entries = knowledgeBase.retrieve("variant", 5);
        assertTrue(entries.stream()
            .anyMatch(e -> e.factType().equals("variant")));
    }

    @Test
    void testRetrieve_ReturnsRelevantFacts() throws PIException {
        knowledgeBase.ingest(specId, testReport);

        List<KnowledgeEntry> results = knowledgeBase.retrieve("throughput", 5);

        assertTrue(results.size() > 0);
        assertTrue(results.stream()
            .anyMatch(e -> e.factText().toLowerCase().contains("throughput")));
    }

    @Test
    void testRetrieve_LimitsToTopK() throws PIException {
        knowledgeBase.ingest(specId, testReport);

        List<KnowledgeEntry> results = knowledgeBase.retrieve("specification", 2);

        assertTrue(results.size() <= 2);
    }

    @Test
    void testRetrieve_EmptyQueryThrows() {
        assertThrows(PIException.class, () -> {
            knowledgeBase.retrieve("", 5);
        });
    }

    @Test
    void testRetrieve_NullQueryThrows() {
        assertThrows(PIException.class, () -> {
            knowledgeBase.retrieve(null, 5);
        });
    }

    @Test
    void testEvict_RemovesEntriesForSpec() throws PIException {
        knowledgeBase.ingest(specId, testReport);
        int sizeBeforeEvict = knowledgeBase.size();
        assertTrue(sizeBeforeEvict > 0);

        knowledgeBase.evict(specId);

        assertEquals(0, knowledgeBase.size());
    }

    @Test
    void testEvict_DoesNotAffectOtherSpecs() throws PIException {
        YSpecificationID spec1 = new YSpecificationID("spec1", "1.0", "http://spec1");
        YSpecificationID spec2 = new YSpecificationID("spec2", "1.0", "http://spec2");

        knowledgeBase.ingest(spec1, testReport);
        knowledgeBase.ingest(spec2, testReport);

        int sizeAfterBothIngest = knowledgeBase.size();
        assertTrue(sizeAfterBothIngest > 0);

        knowledgeBase.evict(spec1);

        // Size should be reduced but not zero (spec2 still has entries)
        assertTrue(knowledgeBase.size() < sizeAfterBothIngest);
    }

    @Test
    void testSize_ReturnsCorrectCount() throws PIException {
        assertEquals(0, knowledgeBase.size());

        knowledgeBase.ingest(specId, testReport);
        int sizeAfterIngest = knowledgeBase.size();

        assertTrue(sizeAfterIngest > 0);

        knowledgeBase.evict(specId);
        assertEquals(0, knowledgeBase.size());
    }

    @Test
    void testIngestNullSpecThrows() {
        assertThrows(PIException.class, () -> {
            knowledgeBase.ingest(null, testReport);
        });
    }

    @Test
    void testIngestNullReportThrows() {
        assertThrows(PIException.class, () -> {
            knowledgeBase.ingest(specId, null);
        });
    }

    @Test
    void testEvictNullSpecThrows() {
        assertThrows(PIException.class, () -> {
            knowledgeBase.evict(null);
        });
    }

    // Helper method to create a test report
    private ProcessMiningFacade.ProcessMiningReport createTestReport() {
        Map<String, Long> activityCounts = new HashMap<>();
        activityCounts.put("A", 5L);
        activityCounts.put("B", 5L);
        activityCounts.put("C", 5L);

        Map<String, Double> avgTimeBetweenActivities = new HashMap<>();
        avgTimeBetweenActivities.put("A>>B", 100.0);
        avgTimeBetweenActivities.put("B>>C", 150.0);

        PerformanceAnalyzer.PerformanceResult perfResult = new PerformanceAnalyzer.PerformanceResult(
            5,                              // traceCount
            1000.0,                         // avgFlowTimeMs
            12.0,                           // throughputPerHour
            activityCounts,                 // activityCounts
            avgTimeBetweenActivities        // avgTimeBetweenActivities
        );

        Map<String, Long> variants = new HashMap<>();
        variants.put("A,B,C", 3L);
        variants.put("A,C,B", 2L);

        return new ProcessMiningFacade.ProcessMiningReport(
            "<xes version='1.0'></xes>",  // xesXml
            null,                          // conformance (not required)
            perfResult,                    // performance
            variants,                      // variantFrequencies
            "{}",                          // ocelJson
            5,                             // traceCount
            "test_spec"                    // specificationId
        );
    }
}
