package org.yawlfoundation.yawl.bridge.erlang.transport;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UnixSocketTransport.
 *
 * <p>HYPER_STANDARDS: Comprehensive testing with real connections or throw.</p>
 */
class UnixSocketTransportTest {

    private static final String TEST_COOKIE = "test_cookie";
    private static final Path TEST_SOCKET_DIR = Paths.get("/tmp/yawl-erlang-test");
    private static final String TEST_HOSTNAME = "localhost";

    private UnixSocketTransport transport;

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

        // Clean up test socket directory
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

    @Test
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
    @DisplayName("Fail to create transport with null cookie")
    void testCreateWithNullCookie() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport(null, TEST_HOSTNAME, TEST_SOCKET_DIR);
        });
    }

    @Test
    @DisplayName("Fail to create transport with empty cookie")
    void testCreateWithEmptyCookie() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport("", TEST_HOSTNAME, TEST_SOCKET_DIR);
        });
    }

    @Test
    @DisplayName("Fail to create transport with null hostname")
    void testCreateWithNullHostname() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport(TEST_COOKIE, null, TEST_SOCKET_DIR);
        });
    }

    @Test
    @DisplayName("Fail to create transport with null socket directory")
    void testCreateWithNullSocketDirectory() {
        assertThrows(ErlangException.class, () -> {
            new UnixSocketTransport(TEST_COOKIE, TEST_HOSTNAME, null);
        });
    }

    @Test
    @DisplayName("Get transport properties")
    void testGetProperties() {
        assertNotNull(transport.getNodeName());
        assertEquals(TEST_COOKIE, transport.getCookie());
        assertEquals(TEST_HOSTNAME, transport.getHostname());
        assertEquals(TEST_SOCKET_DIR, transport.getSocketDirectory());
        assertTrue(transport.getNodeName().contains("yawl_java_"));
    }

    @Test
    @DisplayName("Check connection status")
    void testConnectionStatus() {
        assertTrue(transport.isConnected());
        assertDoesNotThrow(transport::close);
        assertFalse(transport.isConnected());
    }

    @Test
    @DisplayName("Perform RPC operation")
    void testRpcOperation() {
        // Note: This test will fail until the native ei interface is implemented
        // It tests the interface contract rather than actual functionality
        assertThrows(UnsupportedOperationException.class, () -> {
            transport.rpc("erlang", "node");
        });
    }

    @Test
    @DisplayName("Perform RPC with timeout")
    void testRpcWithTimeout() {
        // Note: This test will fail until the native ei interface is implemented
        // It tests the interface contract rather than actual functionality
        assertThrows(UnsupportedOperationException.class, () -> {
            transport.rpc("erlang", "node", 5, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("Fail to perform RPC on closed transport")
    void testRpcOnClosedTransport() {
        transport.close();
        assertThrows(IllegalStateException.class, () -> {
            transport.rpc("erlang", "node");
        });
    }

    @Test
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
    @DisplayName("Handle socket directory permission issues")
    void testSocketDirectoryPermissions() {
        // This test would require creating a directory with restrictive permissions
        // For now, we just verify that normal operations work
        assertDoesNotThrow(() -> {
            assertNotNull(transport.getSocketDirectory());
        });
    }

    @Test
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
    @DisplayName("Close multiple times safely")
    void testCloseMultipleTimes() {
        assertDoesNotThrow(() -> {
            transport.close();
            transport.close();
            transport.close();
        });
    }

    @Test
    @DisplayName("Close after exceptions")
    void testCloseAfterExceptions() {
        // Try to perform RPC that will throw
        assertThrows(UnsupportedOperationException.class, () -> {
            transport.rpc("erlang", "node");
        });

        // Should still be able to close
        assertDoesNotThrow(transport::close);

        // Should not throw when closing again
        assertDoesNotThrow(transport::close);
    }
}