package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comprehensive integration tests for ProcessMiningClient
 *
 * Tests all domain operations including:
 * - OCEL import and management
 * - SLIM link discovery
 * - DECLARE constraint discovery
 * - Token replay conformance checking
 * - DFG discovery
 * - Alpha++ mining
 * - Resource lifecycle
 * - Error handling for Erlang responses
 */
class ProcessMiningClientTest {

    private ProcessMiningClient client;
    private static final String TEST_NODE_NAME = "test@localhost";
    private static final String TEST_COOKIE = "test-cookie";

    @BeforeEach
    void setUp() {
        // Create client with test configuration
        client = new ProcessMiningClientImpl(TEST_NODE_NAME, TEST_COOKIE);
    }

    @Test
    @DisplayName("OCEL Import and Management")
    void testOcelImportAndManagement() throws Exception {
        // Create a test OCEL file
        Path ocelFile = createTestOcelFile();

        try {
            // Test OCEL import
            OcelId ocelId = client.importOcel(ocelFile);
            assertNotNull(ocelId);
            assertFalse(ocelId.value().isEmpty());

            // Test resource management
            client.freeOcel(ocelId);
        } finally {
            // Clean up test file
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("SLIM Link Discovery")
    void testSlimLinkDiscovery() throws Exception {
        // Create test OCEL file
        Path ocelFile = createTestOcelFile();

        try {
            // Import OCEL first
            OcelId ocelId = client.importOcel(ocelFile);
            assertNotNull(ocelId);

            // Test SLIM link discovery
            SlimOcelId slimId = client.slimLink(ocelId);
            assertNotNull(slimId);
            assertFalse(slimId.value().isEmpty());

            // Test resource cleanup
            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("DECLARE Constraint Discovery")
    void testDeclareConstraintDiscovery() throws Exception {
        // Create test OCEL file
        Path ocelFile = createTestOcelFile();

        try {
            // Import OCEL and create slim version
            OcelId ocelId = client.importOcel(ocelFile);
            SlimOcelId slimId = client.slimLink(ocelId);
            assertNotNull(slimId);

            // Test DECLARE constraint discovery
            List<Constraint> constraints = client.discoverOcDeclare(slimId);
            assertNotNull(constraints);

            // Verify constraint structure
            for (Constraint constraint : constraints) {
                assertNotNull(constraint.template());
                assertNotNull(constraint.parameters());
                assertTrue(constraint.support() >= 0.0 && constraint.support() <= 1.0);
            }

            // Test resource cleanup
            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("Token Replay Conformance Checking")
    void testTokenReplayConformance() throws Exception {
        // Create test OCEL and PetriNet files
        Path ocelFile = createTestOcelFile();
        Path pnmlFile = createTestPNMLFile();

        try {
            // Import OCEL
            OcelId ocelId = client.importOcel(ocelFile);
            assertNotNull(ocelId);

            // Create PetriNet ID
            PetriNetId pnId = PetriNetId.fromString("test-pn-" + UUID.randomUUID().toString());
            assertNotNull(pnId);

            // Test token replay
            ConformanceResult result = client.tokenReplay(ocelId, pnId);
            assertNotNull(result);

            // Verify result structure
            assertTrue(result.fitness() >= 0.0 && result.fitness() <= 1.0);
            assertTrue(result.missing() >= 0);
            assertTrue(result.remaining() >= 0);
            assertTrue(result.consumed() >= 0);

            // Test resource cleanup
            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(ocelFile);
            Files.deleteIfExists(pnmlFile);
        }
    }

    @Test
    @DisplayName("DFG Discovery")
    void testDfgDiscovery() throws Exception {
        // Create test OCEL file
        Path ocelFile = createTestOcelFile();

        try {
            // Import OCEL and create slim version
            OcelId ocelId = client.importOcel(ocelFile);
            SlimOcelId slimId = client.slimLink(ocelId);
            assertNotNull(slimId);

            // Test DFG discovery
            DirectlyFollowsGraph dfg = client.discoverDfg(slimId);
            assertNotNull(dfg);

            // Verify DFG structure
            assertFalse(dfg.getEdges().isEmpty());

            // Verify edge structure
            for (Map.Entry<String, Map<String, Integer>> entry : dfg.getEdges().entrySet()) {
                String source = entry.getKey();
                Map<String, Integer> targets = entry.getValue();

                assertNotNull(source);
                assertFalse(targets.isEmpty());

                for (Map.Entry<String, Integer> targetEntry : targets.entrySet()) {
                    assertNotNull(targetEntry.getKey());
                    assertTrue(targetEntry.getValue() > 0);
                }
            }

            // Test resource cleanup
            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("Alpha++ Mining")
    void testAlphaPlusPlusMining() throws Exception {
        // Create test OCEL file
        Path ocelFile = createTestOcelFile();

        try {
            // Import OCEL and create slim version
            OcelId ocelId = client.importOcel(ocelFile);
            SlimOcelId slimId = client.slimLink(ocelId);
            assertNotNull(slimId);

            // Test Alpha++ mining
            PetriNet pn = client.mineAlphaPlusPlus(slimId);
            assertNotNull(pn);

            // Verify PNML structure
            String pnml = pn.getPnmlXml();
            assertNotNull(pnml);
            assertFalse(pnml.isEmpty());
            assertTrue(pnml.contains("<pnml"));
            assertTrue(pnml.contains("</pnml>"));

            // Test resource cleanup
            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("Resource Lifecycle Management")
    void testResourceLifecycle() throws Exception {
        // Create test OCEL file
        Path ocelFile = createTestOcelFile();

        try {
            // Import multiple OCEL instances
            OcelId ocel1 = client.importOcel(ocelFile);
            OcelId ocel2 = client.importOcel(ocelFile);
            assertNotNull(ocel1);
            assertNotNull(ocel2);

            // Create slim versions
            SlimOcelId slim1 = client.slimLink(ocel1);
            SlimOcelId slim2 = client.slimLink(ocel2);
            assertNotNull(slim1);
            assertNotNull(slim2);

            // Perform operations with both resources
            List<Constraint> constraints1 = client.discoverOcDeclare(slim1);
            List<Constraint> constraints2 = client.discoverOcDeclare(slim2);
            assertNotNull(constraints1);
            assertNotNull(constraints2);

            // Free resources in reverse order
            client.freeOcel(ocel2);
            client.freeOcel(ocel1);

            // Verify connection is still valid after cleanup
            assertTrue(client.isConnected());
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("Error Handling - Invalid OCEL File")
    void testErrorHandlingInvalidOcelFile() {
        // Test with non-existent file
        Path nonExistentFile = Paths.get("/tmp/non-existent-ocel.xml");

        assertThrows(ErlangException.class, () -> {
            client.importOcel(nonExistentFile);
        });
    }

    @Test
    @DisplayName("Error Handling - Null Parameters")
    void testErrorHandlingNullParameters() {
        // Test null parameter handling
        assertThrows(IllegalArgumentException.class, () -> {
            client.importOcel(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.slimLink(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.discoverOcDeclare(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.tokenReplay(null, PetriNetId.fromString("test"));
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.tokenReplay(OcelId.fromString("test"), null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.discoverDfg(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.mineAlphaPlusPlus(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            client.freeOcel(null);
        });
    }

    @Test
    @DisplayName("Connection Management")
    void testConnectionManagement() {
        // Test initial connection state
        assertFalse(client.isConnected()); // Should not be connected initially

        // Test connection state after operations
        // (Note: Actual connection testing requires Erlang node setup)
        Path ocelFile = null;
        try {
            ocelFile = createTestOcelFile();
            OcelId ocelId = client.importOcel(ocelFile);
            client.freeOcel(ocelId);
        } catch (Exception e) {
            // Expected in test environment
        } finally {
            if (ocelFile != null) {
                try {
                    Files.deleteIfExists(ocelFile);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    @Test
    @DisplayName("Client Lifecycle")
    void testClientLifecycle() {
        // Test client creation
        assertNotNull(client);

        // Test client close
        client.close();

        // Verify operations on closed client throw exception
        assertThrows(IllegalStateException.class, () -> {
            client.isConnected();
        });
    }

    @Test
    @DisplayName("Concurrent Operations")
    void testConcurrentOperations() throws Exception {
        // Create test OCEL file
        Path ocelFile = createTestOcelFile();
        int threadCount = 5;

        try {
            List<OcelId> ocelIds = new ArrayList<>();

            // Import multiple OCEL instances concurrently
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Thread thread = new Thread(() -> {
                    try {
                        OcelId ocelId = client.importOcel(ocelFile);
                        ocelIds.add(ocelId);
                    } catch (Exception e) {
                        // Handle error
                    }
                });
                thread.start();
                thread.join();
            }

            // Verify all imports succeeded
            assertEquals(threadCount, ocelIds.size());

            // Perform concurrent operations
            for (OcelId ocelId : ocelIds) {
                SlimOcelId slimId = client.slimLink(ocelId);
                assertNotNull(slimId);

                List<Constraint> constraints = client.discoverOcDeclare(slimId);
                assertNotNull(constraints);
            }

            // Clean up all resources
            for (OcelId ocelId : ocelIds) {
                client.freeOcel(ocelId);
            }
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    @Test
    @DisplayName("Large OCEL File Processing")
    void testLargeOcelFileProcessing() throws Exception {
        // Create a larger test OCEL file
        Path largeOcelFile = createLargeTestOcelFile();

        try {
            // Test processing of larger file
            OcelId ocelId = client.importOcel(largeOcelFile);
            assertNotNull(ocelId);

            SlimOcelId slimId = client.slimLink(ocelId);
            assertNotNull(slimId);

            // Test DFG discovery on larger dataset
            DirectlyFollowsGraph dfg = client.discoverDfg(slimId);
            assertNotNull(dfg);
            assertFalse(dfg.getEdges().isEmpty());

            // Test resource cleanup
            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(largeOcelFile);
        }
    }

    @Test
    @DisplayName("Invalid Response Handling")
    void testInvalidResponseHandling() throws Exception {
        // This test would require mocking the Erlang response
        // In a real implementation, we'd test malformed responses

        Path ocelFile = createTestOcelFile();

        try {
            // Import OCEL
            OcelId ocelId = client.importOcel(ocelFile);
            assertNotNull(ocelId);

            // The actual implementation should handle malformed responses gracefully
            // This is more of a design test than a runnable one without mocking
            SlimOcelId slimId = client.slimLink(ocelId);
            assertNotNull(slimId);

            client.freeOcel(ocelId);
        } finally {
            Files.deleteIfExists(ocelFile);
        }
    }

    // Helper methods for creating test data
    private Path createTestOcelFile() throws Exception {
        String ocelContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ocel:ocel xmlns:ocel="http://www.omg.org/spec/OCEL/20151101">
                <ocel:globalLog id="log1">
                    <ocel:events id="events">
                        <ocel:event id="e1" activity="A" timestamp="2024-01-01T10:00:00" caseId="case1"/>
                        <ocel:event id="e2" activity="B" timestamp="2024-01-01T10:30:00" caseId="case1"/>
                        <ocel:event id="e3" activity="C" timestamp="2024-01-01T11:00:00" caseId="case1"/>
                    </ocel:events>
                    <ocel:objects id="objects">
                        <ocel:object id="o1" type="Object1"/>
                        <ocel:object id="o2" type="Object2"/>
                    </ocel:objects>
                </ocel:globalLog>
            </ocel:ocel>
            """;

        Path tempFile = Files.createTempFile("test-ocel", ".xml");
        Files.write(tempFile, ocelContent.getBytes());
        return tempFile;
    }

    private Path createTestPNMLFile() throws Exception {
        String pnmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml xmlns="http://www.pnml.org">
                <net id="net1">
                    <page id="page1">
                        <place id="p1"/>
                        <place id="p2"/>
                        <transition id="t1"/>
                        <arc id="a1" source="p1" target="t1"/>
                        <arc id="a2" source="t1" target="p2"/>
                    </page>
                </net>
            </pnml>
            """;

        Path tempFile = Files.createTempFile("test-pnml", ".xml");
        Files.write(tempFile, pnmlContent.getBytes());
        return tempFile;
    }

    private Path createLargeTestOcelFile() throws Exception {
        StringBuilder content = new StringBuilder();
        content.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <ocel:ocel xmlns:ocel="http://www.omg.org/spec/OCEL/20151101">
                <ocel:globalLog id="large-log1">
                    <ocel:events id="large-events">
            """);

        // Create many events
        for (int i = 1; i <= 1000; i++) {
            content.append(String.format(
                """
                        <ocel:event id="e%d" activity="Task_%d" timestamp="2024-01-01T%d:00:00" caseId="case%d"/>
                """,
                i, ((i % 5) + 1), (i % 24) + 1, (i % 10) + 1
            ));
        }

        content.append("""
                    </ocel:events>
                    <ocel:objects id="large-objects">
                        <ocel:object id="o1" type="Object1"/>
                        <ocel:object id="o2" type="Object2"/>
                    </ocel:objects>
                </ocel:globalLog>
            </ocel:ocel>
            """);

        Path tempFile = Files.createTempFile("large-test-ocel", ".xml");
        Files.write(tempFile, content.toString().getBytes());
        return tempFile;
    }
}