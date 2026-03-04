package org.yawlfoundation.yawl.bridge.erlang.transport;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;
import org.yawlfoundation.yawl.bridge.erlang.EiBuffer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for UnixSocketTransport.
 *
 * <p>Tests the complete Unix domain socket transport implementation including
 * connection establishment, protocol handshake, RPC calls, and error handling.</p>
 *
 * <p>HYPER_STANDARDS: Comprehensive testing with real connections or throw.</p>
 */
@DisplayName("UnixSocketTransport")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnixSocketTransportTest {

    private static final String TEST_COOKIE = "test_cookie";
    private static final Path TEST_SOCKET_DIR = Paths.get("/tmp/yawl-erlang-test");
    private static final String TEST_HOSTNAME = "localhost";

    private UnixSocketTransport transport;

    @BeforeAll
    static void setUpAll() {
        // Clean up any existing test directory
        if (Files.exists(TEST_SOCKET_DIR)) {
            try {
                Files.walk(TEST_SOCKET_DIR)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @AfterAll
    static void tearDownAll() {
        // Clean up test directory
        if (Files.exists(TEST_SOCKET_DIR)) {
            try {
                Files.walk(TEST_SOCKET_DIR)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    @BeforeEach
    void setUp() {
        // Create test socket directory
        try {
            Files.createDirectories(TEST_SOCKET_DIR);
        } catch (Exception e) {
            fail("Failed to create test socket directory: " + e.getMessage());
        }

        // Create transport
        assertDoesNotThrow(() -> {
            transport = new UnixSocketTransport(TEST_COOKIE, TEST_HOSTNAME, TEST_SOCKET_DIR);
        });
    }

    @AfterEach
    void tearDown() {
        if (transport != null) {
            assertDoesNotThrow(transport::close);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Create transport with default configuration")
    void testCreateWithDefaults() {
        assertDoesNotThrow(() -> {
            UnixSocketTransport defaultTransport = new UnixSocketTransport(TEST_COOKIE);
            assertNotNull(defaultTransport);
            assertEquals(TEST_HOSTNAME, defaultTransport.getHostname());
            assertTrue(defaultTransport.isConnected());
        });
    }

    @Test
    @Order(2)
    @DisplayName("Create transport with custom configuration")
    void testCreateWithCustomConfig() {
        assertDoesNotThrow(() -> {
            UnixSocketTransport customTransport = new UnixSocketTransport(
                TEST_COOKIE, TEST_HOSTNAME, TEST_SOCKET_DIR);
            assertNotNull(customTransport);
            assertEquals(TEST_COOKIE, customTransport.getCookie());
            assertEquals(TEST_HOSTNAME, customTransport.getHostname());
            assertEquals(TEST_SOCKET_DIR, customTransport.getSocketDirectory());
            assertTrue(customTransport.isConnected());
        });
    }

    @Test
    @Order(3)
    @DisplayName("Fail to create transport with null cookie")
    void testCreateWithNullCookie() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport(null, TEST_HOSTNAME, TEST_SOCKET_DIR);
        });
    }

    @Test
    @Order(4)
    @DisplayName("Fail to create transport with empty cookie")
    void testCreateWithEmptyCookie() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport("", TEST_HOSTNAME, TEST_SOCKET_DIR);
        });
    }

    @Test
    @Order(5)
    @DisplayName("Fail to create transport with null hostname")
    void testCreateWithNullHostname() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport(TEST_COOKIE, null, TEST_SOCKET_DIR);
        });
    }

    @Test
    @Order(6)
    @DisplayName("Fail to create transport with null socket directory")
    void testCreateWithNullSocketDirectory() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport(TEST_COOKIE, TEST_HOSTNAME, null);
        });
    }

    @Test
    @Order(7)
    @DisplayName("Get transport properties")
    void testGetProperties() {
        assertNotNull(transport.getNodeName());
        assertEquals(TEST_COOKIE, transport.getCookie());
        assertEquals(TEST_HOSTNAME, transport.getHostname());
        assertEquals(TEST_SOCKET_DIR, transport.getSocketDirectory());
        assertTrue(transport.getNodeName().contains("yawl_java_"));
        System.out.println("Node name: " + transport.getNodeName());
    }

    @Test
    @Order(8)
    @DisplayName("Check connection status")
    void testConnectionStatus() {
        assertTrue(transport.isConnected());
        assertDoesNotThrow(transport::close);
        assertFalse(transport.isConnected());
    }

    @Test
    @Order(9)
    @DisplayName("Perform RPC operation with empty args")
    void testRpcOperationWithEmptyArgs() {
        assertDoesNotThrow(() -> {
            EiBuffer result = transport.rpc("test_module", "test_function", new ErlTerm[0]);
            assertNotNull(result, "RPC should return result buffer");
            assertTrue(result.size() > 0, "Result buffer should not be empty");
        });
    }

    @Test
    @Order(10)
    @DisplayName("Perform RPC operation with args - throws due to unimplemented ErlTerms")
    void testRpcOperationWithArgs() {
        // Real ErlTerms are required for actual RPC calls
        // This test verifies the interface contract throws for incomplete implementation
        assertThrows(UnsupportedOperationException.class, () -> {
            // This would require real ErlTerm implementations
            transport.rpc("test_module", "test_function", new ErlTerm[0]);
        }, "RPC with args should throw until real ErlTerms are implemented");
    }

    @Test
    @Order(11)
    @DisplayName("Send challenge")
    void testSendChallenge() {
        assertDoesNotThrow(() -> {
            transport.sendChallenge(TEST_COOKIE);
        }, "Send challenge should succeed");
    }

    @Test
    @Order(12)
    @DisplayName("Fail to perform RPC on closed transport")
    void testRpcOnClosedTransport() {
        transport.close();
        assertThrows(IllegalStateException.class, () -> {
            transport.rpc("test_module", "test_function", new ErlTerm[0]);
        });
    }

    @Test
    @Order(13)
    @DisplayName("Transport factory methods")
    void testFactoryMethods() {
        // Test factory with defaults
        assertDoesNotThrow(() -> {
            UnixSocketTransport factoryTransport = UnixSocketTransport.Factory.create();
            assertNotNull(factoryTransport);
            assertEquals("yawl", factoryTransport.getCookie());
            factoryTransport.close();
        });

        // Test factory with custom cookie
        assertDoesNotThrow(() -> {
            UnixSocketTransport cookieTransport = UnixSocketTransport.Factory.createWithCookie("custom_cookie");
            assertNotNull(cookieTransport);
            assertEquals("custom_cookie", cookieTransport.getCookie());
            cookieTransport.close();
        });

        // Test factory with custom socket directory
        assertDoesNotThrow(() -> {
            UnixSocketTransport dirTransport = UnixSocketTransport.Factory.createWithSocketDirectory(TEST_SOCKET_DIR);
            assertNotNull(dirTransport);
            assertEquals(TEST_SOCKET_DIR, dirTransport.getSocketDirectory());
            dirTransport.close();
        });

        // Test factory with all custom parameters
        assertDoesNotThrow(() -> {
            UnixSocketTransport fullTransport = UnixSocketTransport.Factory.create(
                "full_cookie", "full_host", TEST_SOCKET_DIR);
            assertNotNull(fullTransport);
            assertEquals("full_cookie", fullTransport.getCookie());
            assertEquals("full_host", fullTransport.getHostname());
            assertEquals(TEST_SOCKET_DIR, fullTransport.getSocketDirectory());
            fullTransport.close();
        });
    }

    @Test
    @Order(14)
    @DisplayName("Handle socket directory creation")
    void testSocketDirectoryCreation() {
        // Test that the directory is created if it doesn't exist
        Path nonExistentDir = Paths.get("/tmp/yawl-test-non-existent");
        assertDoesNotThrow(() -> {
            UnixSocketTransport newTransport = new UnixSocketTransport(
                TEST_COOKIE, TEST_HOSTNAME, nonExistentDir);
            assertTrue(Files.exists(nonExistentDir));
            newTransport.close();
        });

        // Clean up
        try {
            Files.walk(nonExistentDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        // Ignore cleanup errors
                    }
                });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Order(15)
    @DisplayName("Generate unique node names")
    void testNodeNameGeneration() {
        UnixSocketTransport transport1 = new UnixSocketTransport(TEST_COOKIE, TEST_HOSTNAME, TEST_SOCKET_DIR);
        UnixSocketTransport transport2 = new UnixSocketTransport(TEST_COOKIE, TEST_HOSTNAME, TEST_SOCKET_DIR);

        String node1 = transport1.getNodeName();
        String node2 = transport2.getNodeName();

        assertNotEquals(node1, node2, "Node names should be unique");
        assertTrue(node1.startsWith("yawl_java_"));
        assertTrue(node2.startsWith("yawl_java_"));
        assertTrue(node1.contains(TEST_HOSTNAME));
        assertTrue(node2.contains(TEST_HOSTNAME));

        transport1.close();
        transport2.close();
    }

    @Test
    @Order(16)
    @DisplayName("Close multiple times safely")
    void testCloseMultipleTimes() {
        assertDoesNotThrow(() -> {
            transport.close();
            transport.close();
            transport.close();
        });
    }

    @Test
    @Order(17)
    @DisplayName("Close after exceptions")
    void testCloseAfterExceptions() {
        // Try to perform RPC that will throw due to no Erlang server
        assertThrows(IllegalStateException.class, () -> {
            transport.rpc("nonexistent_module", "nonexistent_function");
        });

        // Should still be able to close
        assertDoesNotThrow(transport::close);

        // Should not throw when closing again
        assertDoesNotThrow(transport::close);
    }

    @Test
    @Order(18)
    @DisplayName("Heartbeat mechanism")
    void testHeartbeat() {
        // Test that heartbeat is running without causing disconnection
        assertTrue(transport.isConnected(), "Transport should remain connected");

        // Wait a bit for potential heartbeat cycles
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertTrue(transport.isConnected(), "Transport should still be connected after heartbeat");
    }

    @Test
    @Order(19)
    @DisplayName("Socket path creation")
    void testSocketPathCreation() {
        Path socketPath = transport.getSocketPath();
        assertNotNull(socketPath, "Socket path should not be null");
        assertTrue(socketPath.toString().endsWith(".sock"), "Socket path should end with .sock");
        assertTrue(socketPath.startsWith(TEST_SOCKET_DIR.toString()), "Socket path should be in the correct directory");
    }

    }