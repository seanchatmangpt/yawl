package org.yawlfoundation.yawl.bridge.erlang.domain;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.yawlfoundation.yawl.bridge.erlang.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the ProcessMiningClient.
 *
 * @since 1.0.0
 */
class ProcessMiningClientTest {

    private static final String TEST_NODE = "yawl@localhost";
    private static final String TEST_COOKIE = "test-cookie";
    private static final Path TEST_SOCKET = Paths.get("/tmp/yawl-erlang/yawl_localhost.sock");
    private static final Path TEST_OCEL = Paths.get("src/test/resources/test-ocel.xes");

    private ProcessMiningClient client;

    @BeforeEach
    void setUp() {
        try {
            client = new ProcessMiningClientImpl(TEST_NODE, TEST_COOKIE, TEST_SOCKET);
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            System.out.println("Skipping real connection test: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    @DisplayName("ProcessMiningClient constructor with valid parameters")
    void testConstructorWithValidParameters() {
        assertDoesNotThrow(() -> {
            ProcessMiningClient client = new ProcessMiningClientImpl(TEST_NODE, TEST_COOKIE, TEST_SOCKET);
            assertEquals(TEST_NODE, client.getConnectionInfo().getNodeName());
            assertEquals("local", client.getConnectionInfo().getProtocol());
            assertEquals(TEST_SOCKET, client.getConnectionInfo().getSocketPath());
            assertTrue(client.isConnected());
        });
    }

    @Test
    @DisplayName("ProcessMiningClient constructor with default socket path")
    void testConstructorWithDefaultSocketPath() {
        ProcessMiningClient client = new ProcessMiningClientImpl(TEST_NODE, TEST_COOKIE);
        Path expectedPath = Paths.get("/tmp/yawl-erlang/yawl_localhost.sock");
        assertEquals(expectedPath, client.getConnectionInfo().getSocketPath());
    }

    @Test
    @DisplayName("Import OCEL file - valid case")
    @Disabled("Requires real Erlang node connection")
    void testImportOcelValid() {
        assertDoesNotThrow(() -> {
            // This test requires a real OCEL file and Erlang node
            if (!TEST_OCEL.toFile().exists()) {
                System.out.println("Skipping test - no real OCEL file available");
                return;
            }

            ImportResult result = client.importOcel(TEST_OCEL);
            assertNotNull(result);
            assertNotNull(result.getLogId());
            assertTrue(result.getEventCount() > 0);
            assertTrue(result.getObjectCount() > 0);
        });
    }

    @Test
    @DisplayName("Import OCEL file - non-existent file")
    void testImportOcelInvalidFile() {
        assertThrows(ProcessMiningException.class, () -> {
            Path nonExistent = Paths.get("non-existent.xes");
            client.importOcel(nonExistent);
        });
    }

    @Test
    @DisplayName("Import OCEL file - null file")
    void testImportOcelNullFile() {
        assertThrows(ProcessMiningException.class, () -> {
            client.importOcel(null);
        });
    }

    @Test
    @DisplayName("Slim link discovery with valid parameters")
    void testSlimLinkValid() {
        Map<String, ErlTerm> parameters = new HashMap<>();
        parameters.put("min_support", ErlLong.of(2));
        parameters.put("discovery_type", ErlAtom.of("heuristic"));

        assertDoesNotThrow(() -> {
            DiscoveryResult result = client.slimLink(parameters);
            assertNotNull(result);
            assertNotNull(result.getModelId());
            assertNotNull(result.getPatterns());
            assertNotNull(result.getMetrics());
        });
    }

    @Test
    @DisplayName("Slim link discovery with null parameters")
    void testSlimLinkNullParameters() {
        assertThrows(ProcessMiningException.class, () -> {
            client.slimLink(null);
        });
    }

    @Test
    @DisplayName("Slim link discovery with empty parameters")
    void testSlimLinkEmptyParameters() {
        assertDoesNotThrow(() -> {
            DiscoveryResult result = client.slimLink(new HashMap<>());
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("DECLARE discovery with valid parameters")
    void testDiscoverOcDeclareValid() {
        Map<String, ErlTerm> constraints = new HashMap<>();
        constraints.put("succession", ErlLong.of(1));
        constraints.put("response", ErlAtom.of("enabled"));

        assertDoesNotThrow(() -> {
            DeclareDiscoveryResult result = client.discoverOcDeclare(
                DiscoveryType.DECLARE,
                constraints
            );
            assertNotNull(result);
            assertNotNull(result.getDiscoveryId());
            assertNotNull(result.getDeclarePatterns());
            assertTrue(result.getCoverage() >= 0.0 && result.getCoverage() <= 1.0);
        });
    }

    @Test
    @DisplayName("DECLARE discovery with null discovery type")
    void testDiscoverOcDeclareNullType() {
        assertThrows(ProcessMiningException.class, () -> {
            client.discoverOcDeclare(null, new HashMap<>());
        });
    }

    @Test
    @DisplayName("DECLARE discovery with null constraints")
    void testDiscoverOcDeclareNullConstraints() {
        assertDoesNotThrow(() -> {
            DeclareDiscoveryResult result = client.discoverOcDeclare(
                DiscoveryType.DECLARE,
                null
            );
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Token replay with valid parameters")
    void testTokenReplayValid() {
        ReplayParameters params = new ReplayParameters(
            "align",
            0.8,
            Map.of(
                "max_cost", ErlLong.of(1000)
            )
        );

        assertDoesNotThrow(() -> {
            ReplayResult result = client.tokenReplay("test_log", "test_model", params);
            assertNotNull(result);
            assertNotNull(result.getReplayId());
            assertTrue(result.getAlignedEvents() >= 0);
            assertTrue(result.getMisalignedEvents() >= 0);
            assertTrue(result.getFitness() >= 0.0 && result.getFitness() <= 1.0);
        });
    }

    @Test
    @DisplayName("Token replay with null log ID")
    void testTokenReplayNullLogId() {
        ReplayParameters params = new ReplayParameters("align", 0.8, new HashMap<>());
        assertThrows(ProcessMiningException.class, () -> {
            client.tokenReplay(null, "test_model", params);
        });
    }

    @Test
    @DisplayName("Token replay with empty log ID")
    void testTokenReplayEmptyLogId() {
        ReplayParameters params = new ReplayParameters("align", 0.8, new HashMap<>());
        assertThrows(ProcessMiningException.class, () -> {
            client.tokenReplay("", "test_model", params);
        });
    }

    @Test
    @DisplayName("Token replay with null model ID")
    void testTokenReplayNullModelId() {
        ReplayParameters params = new ReplayParameters("align", 0.8, new HashMap<>());
        assertThrows(ProcessMiningException.class, () -> {
            client.tokenReplay("test_log", null, params);
        });
    }

    @Test
    @DisplayName("Execute mining query with valid parameters")
    void testExecuteMiningQueryValid() {
        ErlAtom module = ErlAtom.of("pm_metrics");
        ErlAtom function = ErlAtom.of("calculate_fitness");
        List<ErlTerm> args = List.of(
            ErlAtom.of("test_log"),
            ErlAtom.of("test_model")
        );

        assertDoesNotThrow(() -> {
            ErlTerm result = client.executeMiningQuery(
                module.getValue(),
                function.getValue(),
                args
            );
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Execute mining query with null module")
    void testExecuteMiningQueryNullModule() {
        assertThrows(ProcessMiningException.class, () -> {
            client.executeMiningQuery(
                null,
                "function",
                List.of(ErlAtom.of("arg"))
            );
        });
    }

    @Test
    @DisplayName("Execute mining query with null function")
    void testExecuteMiningQueryNullFunction() {
        assertThrows(ProcessMiningException.class, () -> {
            client.executeMiningQuery(
                "module",
                null,
                List.of(ErlAtom.of("arg"))
            );
        });
    }

    @Test
    @DisplayName("Execute mining query with null arguments")
    void testExecuteMiningQueryNullArguments() {
        assertDoesNotThrow(() -> {
            ErlTerm result = client.executeMiningQuery(
                "module",
                "function",
                null
            );
            assertNotNull(result);
        });
    }

    @Test
    @DisplayName("Close client gracefully")
    void testClientClose() {
        assertDoesNotThrow(() -> {
            client.close();
        });

        // After closing, operations should fail
        assertThrows(IllegalStateException.class, () -> {
            client.executeMiningQuery("module", "function", List.of());
        });
    }

    @Test
    @DisplayName("Connection info is consistent")
    void testConnectionInfo() {
        ConnectionInfo info = client.getConnectionInfo();
        assertEquals(TEST_NODE, info.getNodeName());
        assertEquals("local", info.getProtocol());
        assertEquals(TEST_SOCKET, info.getSocketPath());
        assertTrue(info.getConnectedSince() > 0);
    }

    @Test
    @DisplayName("All discovery types are supported")
    void testAllDiscoveryTypes() {
        for (DiscoveryType type : DiscoveryType.values()) {
            Map<String, ErlTerm> constraints = new HashMap<>();
            constraints.put("dummy", ErlAtom.of("value"));

            assertDoesNotThrow(() -> {
                DeclareDiscoveryResult result = client.discoverOcDeclare(type, constraints);
                assertNotNull(result);
            });
        }
    }

    @Test
    @DisplayName("ProcessMiningException contains proper information")
    void testProcessMiningException() {
        assertThrows(ProcessMiningException.class, () -> {
            client.importOcel(Paths.get("non-existent"));
        }, e -> {
            assertEquals("importOcel", e.getOperation());
            assertNotNull(e.getMessage());
        });
    }
}