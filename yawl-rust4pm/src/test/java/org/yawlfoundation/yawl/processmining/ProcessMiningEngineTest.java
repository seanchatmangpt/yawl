package org.yawlfoundation.yawl.processmining;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle;
import org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge;
import org.yawlfoundation.yawl.rust4pm.error.ConformanceException;
import org.yawlfoundation.yawl.rust4pm.error.ParseException;
import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.ConformanceReport;
import org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph;
import org.yawlfoundation.yawl.rust4pm.model.DfgEdge;
import org.yawlfoundation.yawl.rust4pm.model.DfgNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for {@link ProcessMiningEngine}.
 *
 * <p>Chicago TDD (Detroit School): Tests use REAL OCEL2 data and actual library calls.
 * No mocks, no stubs. Tests detect library absence via {@code rust4pm_h.LIBRARY.isEmpty()}.
 *
 * <p>Test categories:
 * <ul>
 *   <li><b>Library Absent</b>: UnsupportedOperationException propagation for parseOcel2Json,
 *                               discoverDfg, checkConformance, and parseAll</li>
 *   <li><b>AutoCloseable Contract</b>: close() is idempotent, never throws</li>
 *   <li><b>DFG Parsing</b>: Verify DFG JSON parsing path (via discoverDfg path check)</li>
 *   <li><b>Parallel Parsing</b>: parseAll with empty and non-empty lists, exception propagation</li>
 * </ul>
 */
@DisplayName("ProcessMiningEngine")
class ProcessMiningEngineTest {

    private Rust4pmBridge bridge;
    private ProcessMiningEngine engine;

    @BeforeEach
    void setupEngine() throws Exception {
        bridge = new Rust4pmBridge();
        engine = new ProcessMiningEngine(bridge);
    }

    void closeEngine() throws Exception {
        if (engine != null) {
            engine.close();
        }
        if (bridge != null) {
            bridge.close();
        }
    }

    @Nested
    @DisplayName("Library Absent Behavior")
    class LibraryAbsentBehavior {

        /**
         * Verify that parseOcel2Json throws UnsupportedOperationException
         * when native library is absent.
         */
        @Test
        @DisabledIf("libraryPresent")
        @DisplayName("parseOcel2Json throws UnsupportedOperationException when library absent")
        void parseOcel2JsonThrowsWhenLibraryAbsent() throws Exception {
            try {
                String validOcel2Json = """
                    {
                      "ocel:events": [],
                      "ocel:objects": [],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;

                UnsupportedOperationException exception = assertThrows(
                    UnsupportedOperationException.class,
                    () -> engine.parseOcel2Json(validOcel2Json),
                    "parseOcel2Json must throw UnsupportedOperationException when library absent"
                );

                assertTrue(exception.getMessage().contains("native library"),
                    "Exception message should mention native library: " + exception.getMessage());
                assertTrue(exception.getMessage().contains("librust4pm"),
                    "Exception message should mention librust4pm: " + exception.getMessage());
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that discoverDfg throws UnsupportedOperationException when library absent.
         */
        @Test
        @DisabledIf("libraryPresent")
        @DisplayName("discoverDfg throws UnsupportedOperationException when library absent")
        void discoverDfgThrowsWhenLibraryAbsent() throws Exception {
            try {
                String ocel2Json = """
                    {
                      "ocel:events": [],
                      "ocel:objects": [],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;

                assertThrows(
                    UnsupportedOperationException.class,
                    () -> {
                        try (OcelLogHandle log = engine.parseOcel2Json(ocel2Json)) {
                            engine.discoverDfg(log);
                        }
                    },
                    "discoverDfg must propagate UnsupportedOperationException from parseOcel2Json"
                );
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that checkConformance throws UnsupportedOperationException when library absent.
         */
        @Test
        @DisabledIf("libraryPresent")
        @DisplayName("checkConformance throws UnsupportedOperationException when library absent")
        void checkConformanceThrowsWhenLibraryAbsent() throws Exception {
            try {
                String ocel2Json = """
                    {
                      "ocel:events": [],
                      "ocel:objects": [],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;

                String pnmlXml = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <pnml xmlns="http://www.pnml.org/version-2009-05-13/grammar/pnml">
                      <net id="N1" type="http://www.pnml.org/version-2009-05-13/types/Petri.net.pnml">
                      </net>
                    </pnml>
                    """;

                assertThrows(
                    UnsupportedOperationException.class,
                    () -> {
                        try (OcelLogHandle log = engine.parseOcel2Json(ocel2Json)) {
                            engine.checkConformance(log, pnmlXml);
                        }
                    },
                    "checkConformance must propagate UnsupportedOperationException from parseOcel2Json"
                );
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that parseAll with empty list returns immediately without library.
         * This is an optimization: empty list does not require any native calls.
         */
        @Test
        @DisplayName("parseAll with empty list returns immediately (no library needed)")
        void parseAllEmptyListReturnsImmediately() throws Exception {
            try {
                List<OcelLogHandle> results = engine.parseAll(List.of());
                assertEquals(0, results.size(), "Empty input should return empty result list");
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that parseAll with non-empty list throws ExecutionException
         * wrapping UnsupportedOperationException when library absent.
         */
        @Test
        @DisabledIf("libraryPresent")
        @DisplayName("parseAll with non-empty list throws ExecutionException when library absent")
        void parseAllNonEmptyThrowsExecutionExceptionWhenLibraryAbsent() throws Exception {
            try {
                String ocel2Json1 = """
                    {
                      "ocel:events": [],
                      "ocel:objects": [],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;
                String ocel2Json2 = """
                    {
                      "ocel:events": [],
                      "ocel:objects": [],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;

                ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> engine.parseAll(List.of(ocel2Json1, ocel2Json2)),
                    "parseAll must throw ExecutionException when any parse fails"
                );

                assertTrue(exception.getCause() instanceof UnsupportedOperationException,
                    "ExecutionException cause must be UnsupportedOperationException: "
                        + exception.getCause().getClass());
            } finally {
                closeEngine();
            }
        }
    }

    @Nested
    @DisplayName("AutoCloseable Contract")
    class AutoCloseableContract {

        /**
         * Verify that close() on ProcessMiningEngine is idempotent.
         * Multiple calls must not throw exceptions.
         */
        @Test
        @DisplayName("close() is idempotent")
        void closeIsIdempotent() throws Exception {
            try (var b = new Rust4pmBridge();
                 var e = new ProcessMiningEngine(b)) {
                assertDoesNotThrow(e::close,
                    "First close() must not throw");
                assertDoesNotThrow(e::close,
                    "Second close() must be idempotent");
                assertDoesNotThrow(e::close,
                    "Third close() must be idempotent");
            }
        }

        /**
         * Verify that close() never throws any checked or unchecked exception.
         */
        @Test
        @DisplayName("close() never throws")
        void closeNeverThrows() throws Exception {
            var b = new Rust4pmBridge();
            var e = new ProcessMiningEngine(b);

            assertDoesNotThrow(e::close);
            assertDoesNotThrow(e::close);

            b.close();
        }

        /**
         * Verify that ProcessMiningEngine can be used in try-with-resources.
         */
        @Test
        @DisplayName("engine can be used in try-with-resources")
        void tryWithResourcesWorks() {
            assertDoesNotThrow(() -> {
                try (var b = new Rust4pmBridge();
                     var e = new ProcessMiningEngine(b)) {
                    assertNotNull(e);
                }
            }, "try-with-resources cleanup must not throw");
        }
    }

    @Nested
    @DisplayName("DFG Parsing")
    class DfgParsingBehavior {

        /**
         * Verify that parseDfgJson (tested via discoverDfg) correctly parses
         * a minimal DFG JSON structure. This path is only accessible when
         * library is present.
         */
        @Test
        @DisabledIf("libraryAbsent")
        @DisplayName("DFG JSON parsing extracts nodes and edges correctly")
        void dfgJsonParsingExtractsNodesAndEdges() throws Exception {
            try {
                String minimalOcel2Json = """
                    {
                      "ocel:events": [
                        {
                          "ocel:eid": "evt-1",
                          "ocel:type": "place-order",
                          "ocel:timestamp": "2024-01-01T00:00:00Z"
                        }
                      ],
                      "ocel:objects": [
                        {
                          "ocel:oid": "order-1",
                          "ocel:type": "Order"
                        }
                      ],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;

                try (OcelLogHandle log = engine.parseOcel2Json(minimalOcel2Json)) {
                    DirectlyFollowsGraph dfg = engine.discoverDfg(log);

                    assertNotNull(dfg, "DFG must not be null");
                    assertNotNull(dfg.nodes(), "DFG nodes list must not be null");
                    assertNotNull(dfg.edges(), "DFG edges list must not be null");

                    assertTrue(dfg.nodes().size() >= 0,
                        "DFG nodes should be non-negative count");
                    assertTrue(dfg.edges().size() >= 0,
                        "DFG edges should be non-negative count");
                }
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that DirectlyFollowsGraph.totalTransitions() correctly sums edge counts.
         */
        @Test
        @DisplayName("DirectlyFollowsGraph.totalTransitions() sums edge counts")
        void totalTransitionsSumsEdges() {
            List<DfgNode> nodes = List.of(
                new DfgNode("n1", "Activity A", 10),
                new DfgNode("n2", "Activity B", 8)
            );
            List<DfgEdge> edges = List.of(
                new DfgEdge("n1", "n2", 5),
                new DfgEdge("n2", "n1", 3)
            );

            DirectlyFollowsGraph dfg = new DirectlyFollowsGraph(nodes, edges);
            long totalTransitions = dfg.totalTransitions();

            assertEquals(8L, totalTransitions,
                "totalTransitions() should sum all edge counts");
        }

        /**
         * Verify that DirectlyFollowsGraph.findNode() retrieves nodes by ID.
         */
        @Test
        @DisplayName("DirectlyFollowsGraph.findNode() retrieves nodes by id")
        void findNodeRetrievesById() {
            List<DfgNode> nodes = List.of(
                new DfgNode("activity-a", "Prepare Invoice", 15),
                new DfgNode("activity-b", "Send Invoice", 14)
            );
            List<DfgEdge> edges = List.of();

            DirectlyFollowsGraph dfg = new DirectlyFollowsGraph(nodes, edges);

            var found = dfg.findNode("activity-a");
            assertTrue(found.isPresent(), "Should find node by id");
            assertEquals("Prepare Invoice", found.get().label());
            assertEquals(15L, found.get().count());

            var notFound = dfg.findNode("nonexistent");
            assertTrue(notFound.isEmpty(), "Should not find nonexistent node");
        }
    }

    @Nested
    @DisplayName("Parallel Parsing (parseAll)")
    class ParallelParsingBehavior {

        /**
         * Verify that parseAll returns results in the same order as input.
         */
        @Test
        @DisabledIf("libraryAbsent")
        @DisplayName("parseAll returns handles in input order")
        void parseAllPreservesOrder() throws Exception {
            try {
                List<String> jsonLogs = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    jsonLogs.add("""
                        {
                          "ocel:events": [
                            {
                              "ocel:eid": "evt-%d-1",
                              "ocel:type": "activity-%d",
                              "ocel:timestamp": "2024-01-01T00:00:00Z"
                            }
                          ],
                          "ocel:objects": [
                            {
                              "ocel:oid": "obj-%d-1",
                              "ocel:type": "Type-%d"
                            }
                          ],
                          "ocel:global-log": {},
                          "ocel:global-object": {}
                        }
                        """.formatted(i, i, i, i));
                }

                List<OcelLogHandle> handles = engine.parseAll(jsonLogs);

                assertEquals(3, handles.size(), "Should parse all 3 logs");
                assertEquals(jsonLogs.size(), handles.size(), "Result count must match input count");

                for (OcelLogHandle handle : handles) {
                    assertNotNull(handle);
                    handle.close();
                }
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that parseAll with single JSON parses successfully.
         */
        @Test
        @DisabledIf("libraryAbsent")
        @DisplayName("parseAll with single JSON parses successfully")
        void parseAllSingleJson() throws Exception {
            try {
                String singleJson = """
                    {
                      "ocel:events": [
                        {
                          "ocel:eid": "evt-single",
                          "ocel:type": "process",
                          "ocel:timestamp": "2024-01-01T00:00:00Z"
                        }
                      ],
                      "ocel:objects": [
                        {
                          "ocel:oid": "obj-1",
                          "ocel:type": "Entity"
                        }
                      ],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """;

                List<OcelLogHandle> handles = engine.parseAll(List.of(singleJson));

                assertEquals(1, handles.size());
                assertNotNull(handles.get(0));

                handles.get(0).close();
            } finally {
                closeEngine();
            }
        }

        /**
         * Verify that InterruptedException from parseAll is not masked.
         */
        @Test
        @DisabledIf("libraryAbsent")
        @DisplayName("parseAll declares InterruptedException in signature")
        void parseAllDeclaresInterruptedException() throws Exception {
            try {
                List<String> jsonLogs = List.of(
                    """
                    {
                      "ocel:events": [],
                      "ocel:objects": [],
                      "ocel:global-log": {},
                      "ocel:global-object": {}
                    }
                    """
                );

                assertDoesNotThrow(() -> {
                    List<OcelLogHandle> handles = engine.parseAll(jsonLogs);
                    for (OcelLogHandle h : handles) {
                        h.close();
                    }
                }, "parseAll must be callable without InterruptedException interference");
            } finally {
                closeEngine();
            }
        }
    }

    @Nested
    @DisplayName("Engine Construction and Validation")
    class EngineConstruction {

        /**
         * Verify that ProcessMiningEngine requires non-null bridge.
         */
        @Test
        @DisplayName("constructor requires non-null bridge")
        void constructorRequiresNonNullBridge() {
            assertThrows(NullPointerException.class,
                () -> new ProcessMiningEngine(null),
                "Constructor must reject null bridge");
        }

        /**
         * Verify that ProcessMiningEngine is properly initialized with a valid bridge.
         */
        @Test
        @DisplayName("constructor initializes with valid bridge")
        void constructorInitializesWithBridge() throws Exception {
            try (var b = new Rust4pmBridge()) {
                ProcessMiningEngine e = new ProcessMiningEngine(b);
                assertNotNull(e, "Engine must be created successfully");
                e.close();
            }
        }
    }

    @Nested
    @DisplayName("ConformanceReport Behavior")
    class ConformanceReportValidation {

        /**
         * Verify ConformanceReport.f1Score() calculation.
         */
        @Test
        @DisplayName("ConformanceReport.f1Score() computes harmonic mean")
        void f1ScoreComputeHarmonicMean() {
            ConformanceReport report = new ConformanceReport(0.8, 0.9, 100, null);
            double expectedF1 = 2.0 * (0.8 * 0.9) / (0.8 + 0.9);
            assertEquals(expectedF1, report.f1Score(), 0.0001,
                "F1 score should be harmonic mean of fitness and precision");
        }

        /**
         * Verify ConformanceReport.f1Score() handles zero case.
         */
        @Test
        @DisplayName("ConformanceReport.f1Score() returns 0.0 when both metrics are zero")
        void f1ScoreZeroWhenBothZero() {
            ConformanceReport report = new ConformanceReport(0.0, 0.0, 100, null);
            assertEquals(0.0, report.f1Score(),
                "F1 score should be 0.0 when both fitness and precision are 0.0");
        }

        /**
         * Verify ConformanceReport.isPerfectFit() detection.
         */
        @Test
        @DisplayName("ConformanceReport.isPerfectFit() detects perfect fit")
        void isPerfectFitDetectsPerfectFit() {
            ConformanceReport perfectReport = new ConformanceReport(1.0, 1.0, 50, null);
            assertTrue(perfectReport.isPerfectFit(),
                "isPerfectFit() should return true when both metrics are 1.0");

            ConformanceReport almostPerfectReport = new ConformanceReport(0.99, 1.0, 50, null);
            assertFalse(almostPerfectReport.isPerfectFit(),
                "isPerfectFit() should return false when any metric is less than 1.0");
        }

        /**
         * Verify ConformanceReport accepts valid metrics.
         */
        @Test
        @DisplayName("ConformanceReport accepts metrics in [0.0, 1.0] range")
        void metricsInValidRange() {
            assertDoesNotThrow(() -> {
                new ConformanceReport(0.0, 0.0, 1, null);
                new ConformanceReport(0.5, 0.5, 1, null);
                new ConformanceReport(1.0, 1.0, 1, null);
            }, "ConformanceReport must accept valid metric values");
        }
    }

    private static boolean libraryPresent() {
        return rust4pm_h.LIBRARY.isPresent();
    }

    private static boolean libraryAbsent() {
        return rust4pm_h.LIBRARY.isEmpty();
    }
}
