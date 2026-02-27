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

import java.io.IOException;
import java.lang.reflect.Constructor;
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
        testReport = createTestReportViaReflection();
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

    // Helper method to create a test report using reflection (constructor is package-private)
    private ProcessMiningFacade.ProcessMiningReport createTestReportViaReflection() throws Exception {
        // Build minimal XES XML that PerformanceAnalyzer can parse
        String xesXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log xes.version="1.0" xmlns="http://www.xes-standard.org/">
              <trace concept:name="trace1">
                <event>
                  <string key="concept:name" value="A"/>
                  <date key="time:timestamp" value="2026-02-27T10:00:00Z"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                  <date key="time:timestamp" value="2026-02-27T10:01:40Z"/>
                </event>
                <event>
                  <string key="concept:name" value="C"/>
                  <date key="time:timestamp" value="2026-02-27T10:03:10Z"/>
                </event>
              </trace>
              <trace concept:name="trace2">
                <event>
                  <string key="concept:name" value="A"/>
                  <date key="time:timestamp" value="2026-02-27T10:10:00Z"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                  <date key="time:timestamp" value="2026-02-27T10:11:40Z"/>
                </event>
                <event>
                  <string key="concept:name" value="C"/>
                  <date key="time:timestamp" value="2026-02-27T10:13:10Z"/>
                </event>
              </trace>
              <trace concept:name="trace3">
                <event>
                  <string key="concept:name" value="A"/>
                  <date key="time:timestamp" value="2026-02-27T10:20:00Z"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                  <date key="time:timestamp" value="2026-02-27T10:21:40Z"/>
                </event>
                <event>
                  <string key="concept:name" value="C"/>
                  <date key="time:timestamp" value="2026-02-27T10:23:10Z"/>
                </event>
              </trace>
              <trace concept:name="trace4">
                <event>
                  <string key="concept:name" value="A"/>
                  <date key="time:timestamp" value="2026-02-27T10:30:00Z"/>
                </event>
                <event>
                  <string key="concept:name" value="C"/>
                  <date key="time:timestamp" value="2026-02-27T10:31:40Z"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                  <date key="time:timestamp" value="2026-02-27T10:33:10Z"/>
                </event>
              </trace>
              <trace concept:name="trace5">
                <event>
                  <string key="concept:name" value="A"/>
                  <date key="time:timestamp" value="2026-02-27T10:40:00Z"/>
                </event>
                <event>
                  <string key="concept:name" value="C"/>
                  <date key="time:timestamp" value="2026-02-27T10:41:40Z"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                  <date key="time:timestamp" value="2026-02-27T10:43:10Z"/>
                </event>
              </trace>
            </log>
            """;

        // Use PerformanceAnalyzer to create the result
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult perfResult = analyzer.analyze(xesXml);

        // Create variant frequencies map
        Map<String, Long> variants = new HashMap<>();
        variants.put("A,B,C", 3L);
        variants.put("A,C,B", 2L);

        // ProcessMiningReport constructor is package-private, so use reflection
        Class<?> reportClass = ProcessMiningFacade.ProcessMiningReport.class;
        Constructor<?> constructor = reportClass.getDeclaredConstructor(
            String.class,  // xesXml
            Class.forName("org.yawlfoundation.yawl.integration.processmining.TokenReplayConformanceChecker$TokenReplayResult"),  // conformance
            PerformanceAnalyzer.PerformanceResult.class,  // performance
            Map.class,  // variantFrequencies
            String.class,  // ocelJson
            int.class,  // traceCount
            String.class   // specId
        );
        constructor.setAccessible(true);

        return (ProcessMiningFacade.ProcessMiningReport) constructor.newInstance(
            xesXml,                        // xesXml
            null,                          // conformance (not required for testing)
            perfResult,                    // performance
            variants,                      // variantFrequencies
            "{}",                          // ocelJson (minimal OCEL)
            perfResult.traceCount,         // traceCount (from analyzer result)
            "test_spec"                    // specificationId
        );
    }
}
