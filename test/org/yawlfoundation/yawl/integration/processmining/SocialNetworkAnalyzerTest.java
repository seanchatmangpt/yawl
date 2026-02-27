/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SocialNetworkAnalyzer.
 *
 * Tests cover:
 * - Single case with Alice→Bob handover
 * - Multiple cases with repeated handovers
 * - Multiple resources (3+ per case)
 * - UNKNOWN resources (filtered from network)
 * - Working together relationships
 * - Resource workload counting
 * - Most central resource determination
 * - Top-N handovers ranking
 * - Empty XES
 * - Single-resource cases (no handover)
 */
@DisplayName("SocialNetworkAnalyzer Tests")
class SocialNetworkAnalyzerTest {

    private final SocialNetworkAnalyzer analyzer = new SocialNetworkAnalyzer();

    // Test 1: Single case with Alice→Bob handover
    @Test
    @DisplayName("Single case with Alice→Bob handover")
    void testSimpleHandover() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Bob"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        assertEquals(2, result.resources.size());
        assertTrue(result.resources.contains("Alice"));
        assertTrue(result.resources.contains("Bob"));

        // Check handover: Alice → Bob
        assertTrue(result.handoverMatrix.containsKey("Alice"));
        Map<String, Long> aliceTo = result.handoverMatrix.get("Alice");
        assertTrue(aliceTo.containsKey("Bob"));
        assertEquals(1L, aliceTo.get("Bob"));

        // Check workload
        assertEquals(1L, result.workloadByResource.get("Alice"));
        assertEquals(1L, result.workloadByResource.get("Bob"));

        // Check most central resource
        assertNotNull(result.mostCentralResource);
        assertTrue(result.resources.contains(result.mostCentralResource));

        // Check working together
        assertTrue(result.workingTogether.get("Alice").contains("Bob"));
        assertTrue(result.workingTogether.get("Bob").contains("Alice"));
    }

    // Test 2: Two cases with same handover (count = 2)
    @Test
    @DisplayName("Two cases with same handover → count = 2")
    void testRepeatedHandover() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case2"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // Check handover count
        Map<String, Long> aliceTo = result.handoverMatrix.get("Alice");
        assertEquals(2L, aliceTo.get("Bob"));

        // Workload should be 2 for each (2 events per resource across 2 cases)
        assertEquals(2L, result.workloadByResource.get("Alice"));
        assertEquals(2L, result.workloadByResource.get("Bob"));
    }

    // Test 3: Multiple resources (3+ per case)
    @Test
    @DisplayName("Multiple resources: Alice→Bob→Charlie")
    void testMultipleResources() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Bob"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskC"/>
                  <string key="org:resource" value="Charlie"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        assertEquals(3, result.resources.size());
        assertTrue(result.resources.contains("Alice"));
        assertTrue(result.resources.contains("Bob"));
        assertTrue(result.resources.contains("Charlie"));

        // Check handovers: A→B and B→C
        assertEquals(1L, result.handoverMatrix.get("Alice").get("Bob"));
        assertEquals(1L, result.handoverMatrix.get("Bob").get("Charlie"));

        // Check working together
        assertTrue(result.workingTogether.get("Alice").contains("Bob"));
        assertTrue(result.workingTogether.get("Alice").contains("Charlie"));
        assertTrue(result.workingTogether.get("Bob").contains("Alice"));
        assertTrue(result.workingTogether.get("Bob").contains("Charlie"));
        assertTrue(result.workingTogether.get("Charlie").contains("Alice"));
        assertTrue(result.workingTogether.get("Charlie").contains("Bob"));
    }

    // Test 4: UNKNOWN resources filtered from network
    @Test
    @DisplayName("UNKNOWN resources skipped from network")
    void testUnknownResourcesFiltered() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="UNKNOWN"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskC"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // UNKNOWN should NOT be in resources set
        assertFalse(result.resources.contains("UNKNOWN"));

        // Only Alice and Bob in network
        assertEquals(2, result.resources.size());
        assertTrue(result.resources.contains("Alice"));
        assertTrue(result.resources.contains("Bob"));

        // Handover A→U→B should be skipped (U is unknown)
        // So no direct A→B handover either
        assertFalse(result.handoverMatrix.getOrDefault("Alice", new java.util.HashMap<>())
            .containsKey("Bob"));

        // Workload should still count UNKNOWN
        assertEquals(1L, result.workloadByResource.get("UNKNOWN"));
        assertEquals(1L, result.workloadByResource.get("Alice"));
        assertEquals(1L, result.workloadByResource.get("Bob"));
    }

    // Test 5: getTopHandovers returns top N pairs
    @Test
    @DisplayName("getTopHandovers(xes, 2) returns top 2 pairs by count")
    void testGetTopHandovers() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Bob"/>
                </event>
                <event>
                  <string key="concept:name" value="T3"/>
                  <string key="org:resource" value="Charlie"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case2"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case3"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Bob"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Charlie"/>
                </event>
              </trace>
            </log>
            """;

        List<String[]> topHandovers = analyzer.getTopHandovers(xes, 2);

        assertEquals(2, topHandovers.size());

        // Top 1: Alice→Bob (count 2)
        String[] top1 = topHandovers.get(0);
        assertEquals("Alice", top1[0]);
        assertEquals("Bob", top1[1]);
        assertEquals("2", top1[2]);

        // Top 2: Bob→Charlie (count 1)
        String[] top2 = topHandovers.get(1);
        assertEquals("Bob", top2[0]);
        assertEquals("Charlie", top2[1]);
        assertEquals("1", top2[2]);
    }

    // Test 6: Working together relationships
    @Test
    @DisplayName("Working together: Alice and Bob share case1")
    void testWorkingTogether() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case2"/>
                <event>
                  <string key="concept:name" value="TaskC"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskD"/>
                  <string key="org:resource" value="Charlie"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // Alice and Bob worked together in case1
        assertTrue(result.workingTogether.get("Alice").contains("Bob"));
        assertTrue(result.workingTogether.get("Bob").contains("Alice"));

        // Alice and Charlie worked together in case2
        assertTrue(result.workingTogether.get("Alice").contains("Charlie"));
        assertTrue(result.workingTogether.get("Charlie").contains("Alice"));

        // Bob and Charlie did NOT work together
        assertFalse(result.workingTogether.get("Bob").contains("Charlie"));
    }

    // Test 7: Empty XES returns empty result
    @Test
    @DisplayName("Empty XES returns empty network")
    void testEmptyXes() {
        String xes = """
            <log>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        assertTrue(result.resources.isEmpty());
        assertTrue(result.handoverMatrix.isEmpty());
        assertTrue(result.workloadByResource.isEmpty());
        assertNull(result.mostCentralResource);
    }

    // Test 8: Single-resource case (no handover possible)
    @Test
    @DisplayName("Single-resource case counts workload but no handover")
    void testSingleResourceCase() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Alice"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // Resources should include Alice
        assertEquals(1, result.resources.size());
        assertTrue(result.resources.contains("Alice"));

        // Workload counts both events
        assertEquals(2L, result.workloadByResource.get("Alice"));

        // No handover (same resource)
        Map<String, Long> aliceTo = result.handoverMatrix.getOrDefault("Alice", new java.util.HashMap<>());
        assertFalse(aliceTo.containsKey("Alice"));

        // Alice is most central (only resource)
        assertEquals("Alice", result.mostCentralResource);

        // Alice doesn't work with anyone else
        assertTrue(result.workingTogether.getOrDefault("Alice", new HashSet<>()).isEmpty());
    }

    // Test 9: Missing org:resource attribute defaults to UNKNOWN
    @Test
    @DisplayName("Missing org:resource defaults to UNKNOWN")
    void testMissingResourceAttribute() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="TaskA"/>
                </event>
                <event>
                  <string key="concept:name" value="TaskB"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // Only Bob should be in network (UNKNOWN filtered)
        assertEquals(1, result.resources.size());
        assertTrue(result.resources.contains("Bob"));

        // Workload counts UNKNOWN
        assertEquals(1L, result.workloadByResource.get("UNKNOWN"));
        assertEquals(1L, result.workloadByResource.get("Bob"));
    }

    // Test 10: Most central resource determination
    @Test
    @DisplayName("Most central resource has highest connection count")
    void testMostCentralResource() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Hub"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Alice"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case2"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Hub"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case3"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Hub"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Charlie"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // Hub has outgoing edges to A, B, C (3 outgoing) + incoming from none
        // A, B, C have incoming from Hub (1 each)
        // Hub is most central
        assertEquals("Hub", result.mostCentralResource);
    }

    // Test 11: Null XES returns empty result
    @Test
    @DisplayName("Null XES returns empty network")
    void testNullXes() {
        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(null);

        assertTrue(result.resources.isEmpty());
        assertTrue(result.handoverMatrix.isEmpty());
        assertTrue(result.workloadByResource.isEmpty());
        assertNull(result.mostCentralResource);
    }

    // Test 12: getTopHandovers with n=0 returns empty list
    @Test
    @DisplayName("getTopHandovers(xes, 0) returns empty list")
    void testGetTopHandoversZero() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="T1"/>
                  <string key="org:resource" value="Alice"/>
                </event>
                <event>
                  <string key="concept:name" value="T2"/>
                  <string key="org:resource" value="Bob"/>
                </event>
              </trace>
            </log>
            """;

        List<String[]> topHandovers = analyzer.getTopHandovers(xes, 0);
        assertTrue(topHandovers.isEmpty());
    }

    // Test 13: Complex scenario with multiple handovers
    @Test
    @DisplayName("Complex scenario: multiple handovers and repeated patterns")
    void testComplexScenario() {
        String xes = """
            <log>
              <trace>
                <string key="concept:name" value="case1"/>
                <event>
                  <string key="concept:name" value="Request"/>
                  <string key="org:resource" value="Requester"/>
                </event>
                <event>
                  <string key="concept:name" value="Approve"/>
                  <string key="org:resource" value="Manager"/>
                </event>
                <event>
                  <string key="concept:name" value="Process"/>
                  <string key="org:resource" value="Worker"/>
                </event>
                <event>
                  <string key="concept:name" value="Archive"/>
                  <string key="org:resource" value="Admin"/>
                </event>
              </trace>
              <trace>
                <string key="concept:name" value="case2"/>
                <event>
                  <string key="concept:name" value="Request"/>
                  <string key="org:resource" value="Requester"/>
                </event>
                <event>
                  <string key="concept:name" value="Approve"/>
                  <string key="org:resource" value="Manager"/>
                </event>
                <event>
                  <string key="concept:name" value="Process"/>
                  <string key="org:resource" value="Worker"/>
                </event>
              </trace>
            </log>
            """;

        SocialNetworkAnalyzer.SocialNetworkResult result = analyzer.analyze(xes);

        // Check handover counts
        assertEquals(2L, result.handoverMatrix.get("Requester").get("Manager"));
        assertEquals(2L, result.handoverMatrix.get("Manager").get("Worker"));
        assertEquals(1L, result.handoverMatrix.get("Worker").get("Admin"));

        // Check workload
        assertEquals(2L, result.workloadByResource.get("Requester"));
        assertEquals(2L, result.workloadByResource.get("Manager"));
        assertEquals(2L, result.workloadByResource.get("Worker"));
        assertEquals(1L, result.workloadByResource.get("Admin"));

        // Check network size (4 resources)
        assertEquals(4, result.resources.size());

        // Check most central (Manager: 2 incoming + 2 outgoing = 4 total)
        assertEquals("Manager", result.mostCentralResource);
    }
}
