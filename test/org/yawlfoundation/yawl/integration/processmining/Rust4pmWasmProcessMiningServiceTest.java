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

package org.yawlfoundation.yawl.integration.processmining;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Rust4pmWasmProcessMiningService}.
 *
 * Tests verify that the WASM-based service:
 * - Initializes correctly with the Rust4pmBridge
 * - Implements all ProcessMiningService methods
 * - Handles errors gracefully
 * - Properly closes resources
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("Rust4pmWasmProcessMiningService Tests")
class Rust4pmWasmProcessMiningServiceTest {

    private Rust4pmWasmProcessMiningService service;

    @BeforeEach
    void setUp() throws IOException {
        // Initialize service for each test
        service = new Rust4pmWasmProcessMiningService();
    }

    @AfterEach
    void tearDown() {
        // Clean up resources after each test
        if (service != null) {
            service.close();
        }
    }

    @Test
    @DisplayName("Service initializes with WASM bridge")
    void testServiceInitialization() throws IOException {
        assertNotNull(service, "Service should not be null");
        assertTrue(service.isHealthy(), "Service should be healthy after initialization");
    }

    @Test
    @DisplayName("isHealthy returns true when initialized")
    void testIsHealthyWhenInitialized() {
        assertTrue(service.isHealthy(), "Service should be healthy when initialized");
    }

    @Test
    @DisplayName("isHealthy returns false after close")
    void testIsHealthyAfterClose() {
        service.close();
        assertFalse(service.isHealthy(), "Service should not be healthy after close");
    }

    @Test
    @DisplayName("discoverDfg returns valid JSON")
    void testDiscoverDfg() throws IOException {
        String xesXml = generateMinimalXes();
        String result = service.discoverDfg(xesXml);

        assertNotNull(result, "DFG result should not be null");
        assertTrue(result.contains("nodes"), "DFG result should contain nodes");
        assertTrue(result.contains("edges"), "DFG result should contain edges");
    }

    @Test
    @DisplayName("discoverAlphaPpp returns valid PNML")
    void testDiscoverAlphaPpp() throws IOException {
        String xesXml = generateMinimalXes();
        String result = service.discoverAlphaPpp(xesXml);

        assertNotNull(result, "Process model result should not be null");
        assertTrue(result.startsWith("<?xml"), "Result should be valid XML");
        assertTrue(result.contains("pnml"), "Result should contain pnml element");
    }

    @Test
    @DisplayName("tokenReplay returns valid conformance metrics")
    void testTokenReplay() throws IOException {
        String pnmlXml = generateMinimalPnml();
        String xesXml = generateMinimalXes();

        String result = service.tokenReplay(pnmlXml, xesXml);

        assertNotNull(result, "Conformance result should not be null");
        assertTrue(result.contains("fitness"), "Result should contain fitness metric");
        assertTrue(result.contains("produced"), "Result should contain produced count");
    }

    @Test
    @DisplayName("performanceAnalysis returns valid performance metrics")
    void testPerformanceAnalysis() throws IOException {
        String xesXml = generateMinimalXes();
        String result = service.performanceAnalysis(xesXml);

        assertNotNull(result, "Performance result should not be null");
        assertTrue(result.contains("traceCount"), "Result should contain trace count");
        assertTrue(result.contains("avgFlowTimeMs"), "Result should contain avg flow time");
    }

    @Test
    @DisplayName("xesToOcel converts XES to OCEL JSON")
    void testXesToOcel() throws IOException {
        String xesXml = generateMinimalXes();
        String result = service.xesToOcel(xesXml);

        assertNotNull(result, "OCEL result should not be null");
        // OCEL conversion may return various JSON structures depending on WASM implementation
        assertTrue(result.length() > 0, "Result should not be empty");
    }

    @Test
    @DisplayName("Operations fail gracefully after close")
    void testOperationsFailAfterClose() throws IOException {
        service.close();

        String xesXml = generateMinimalXes();
        assertThrows(IOException.class, () -> service.discoverDfg(xesXml),
                "discoverDfg should throw IOException after close");

        assertThrows(IOException.class, () -> service.performanceAnalysis(xesXml),
                "performanceAnalysis should throw IOException after close");
    }

    @Test
    @DisplayName("close is idempotent")
    void testCloseIsIdempotent() {
        // Close multiple times should not throw
        assertDoesNotThrow(service::close, "First close should succeed");
        assertDoesNotThrow(service::close, "Second close should not throw");
        assertFalse(service.isHealthy(), "Service should be unhealthy after close");
    }

    @Test
    @DisplayName("Service implements ProcessMiningService interface")
    void testProcessMiningServiceContractImplemented() {
        assertTrue(service instanceof ProcessMiningService,
                "Service must implement ProcessMiningService interface");
    }

    @Test
    @DisplayName("Service implements AutoCloseable interface")
    void testAutoCloseableContractImplemented() {
        assertTrue(service instanceof AutoCloseable,
                "Service must implement AutoCloseable interface");
    }

    @Test
    @DisplayName("Custom pool size initializes correctly")
    void testCustomPoolSize() throws IOException {
        try (Rust4pmWasmProcessMiningService customService = new Rust4pmWasmProcessMiningService(2)) {
            assertTrue(customService.isHealthy(), "Custom pool size service should be healthy");
        }
    }

    @Test
    @DisplayName("Multiple service instances can exist simultaneously")
    void testMultipleServiceInstances() throws IOException {
        try (Rust4pmWasmProcessMiningService service1 = new Rust4pmWasmProcessMiningService();
             Rust4pmWasmProcessMiningService service2 = new Rust4pmWasmProcessMiningService()) {
            assertTrue(service1.isHealthy(), "Service 1 should be healthy");
            assertTrue(service2.isHealthy(), "Service 2 should be healthy");
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    /**
     * Generate a minimal valid XES event log.
     *
     * @return XES XML string with one trace and one event
     */
    private String generateMinimalXes() {
        StringBuilder xes = new StringBuilder();
        xes.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        xes.append("<log xes.version=\"1.0\" xmlns=\"http://www.xes-standard.org/\">\n");
        xes.append("  <extension name=\"Concept\" prefix=\"concept\" ");
        xes.append("uri=\"http://www.xes-standard.org/concept.xesext\"/>\n");
        xes.append("  <extension name=\"Time\" prefix=\"time\" ");
        xes.append("uri=\"http://www.xes-standard.org/time.xesext\"/>\n");
        xes.append("  <string key=\"concept:name\" value=\"test-log\"/>\n");
        xes.append("  <trace>\n");
        xes.append("    <string key=\"concept:name\" value=\"case-001\"/>\n");
        xes.append("    <event>\n");
        xes.append("      <string key=\"concept:name\" value=\"activity-a\"/>\n");
        xes.append("      <string key=\"time:timestamp\" value=\"");
        xes.append(Instant.now().toString());
        xes.append("\"/>\n");
        xes.append("    </event>\n");
        xes.append("    <event>\n");
        xes.append("      <string key=\"concept:name\" value=\"activity-b\"/>\n");
        xes.append("      <string key=\"time:timestamp\" value=\"");
        xes.append(Instant.now().plus(1, ChronoUnit.MINUTES).toString());
        xes.append("\"/>\n");
        xes.append("    </event>\n");
        xes.append("  </trace>\n");
        xes.append("</log>\n");
        return xes.toString();
    }

    /**
     * Generate a minimal valid PNML Petri net model.
     *
     * @return PNML XML string with basic structure
     */
    private String generateMinimalPnml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<pnml xmlns=\"http://www.pnml.org/version-2009-05-13/grammar/pnml\">\n"
                + "  <net id=\"net1\" type=\"http://www.pnml.org/version-2009-05-13/grammar/pnmlcoremodel\">\n"
                + "    <page id=\"page1\">\n"
                + "      <place id=\"p1\"/>\n"
                + "      <transition id=\"t1\"/>\n"
                + "    </page>\n"
                + "  </net>\n"
                + "</pnml>\n";
    }
}
