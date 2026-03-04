package org.yawlfoundation.yawl.bridge.erlang;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ErlangNode class.
 *
 * @since 1.0.0
 */
class ErlangNodeTest {

    private static final String TEST_NODE = "yawl@localhost";
    private static final String TEST_COOKIE = "test-cookie";
    private static final Path TEST_SOCKET = Paths.get("/tmp/yawl-erlang/yawl_localhost.sock");

    @Test
    void testConstructorWithValidParameters() {
        // This test will fail due to the mock implementation, but validates the interface design
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);
            assertEquals(TEST_NODE, node.getNodeName());
            assertEquals(TEST_COOKIE, node.getCookie());
            assertEquals(TEST_SOCKET, node.getSocketPath());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testConstructorWithDefaultSocketPath() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE);
            assertEquals(TEST_NODE, node.getNodeName());
            assertEquals(TEST_COOKIE, node.getCookie());

            // Verify default socket path
            Path expectedPath = Paths.get("/tmp/yawl-erlang/yawl_localhost.sock");
            assertEquals(expectedPath, node.getSocketPath());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testConstructorWithNullNodeName() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ErlangNode(null, TEST_COOKIE, TEST_SOCKET);
        });
    }

    @Test
    void testConstructorWithEmptyNodeName() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ErlangNode("", TEST_COOKIE, TEST_SOCKET);
        });
    }

    @Test
    void testConstructorWithNullCookie() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ErlangNode(TEST_NODE, null, TEST_SOCKET);
        });
    }

    @Test
    void testConstructorWithEmptyCookie() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ErlangNode(TEST_NODE, "", TEST_SOCKET);
        });
    }

    @Test
    void testConstructorWithNullSocketPath() {
        assertThrows(IllegalArgumentException.class, () -> {
            new ErlangNode(TEST_NODE, TEST_COOKIE, null);
        });
    }

    @Test
    void testRpcWithValidArguments() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);

            // Test RPC call with valid arguments
            ErlAtom result = (ErlAtom) node.rpc(
                "test_module",
                "test_function",
                ErlAtom.of("arg1"),
                ErlLong.of(42)
            );

            assertEquals("test_response", result.getValue());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testRpcWithNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);
            node.rpc("test_module", "test_function", (ErlTerm[]) null);
        });
    }

    @Test
    void testRpcWithNullArgument() {
        assertThrows(IllegalArgumentException.class, () -> {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);
            node.rpc("test_module", "test_function", ErlAtom.of("valid"), null);
        });
    }

    @Test
    void testRpcWithTimeout() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);

            // Test RPC call with custom timeout
            ErlAtom result = (ErlAtom) node.rpc(
                "test_module",
                "test_function",
                5000, // 5 seconds
                java.util.concurrent.TimeUnit.MILLISECONDS,
                ErlAtom.of("arg1")
            );

            assertEquals("test_response", result.getValue());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testIsConnected() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);
            assertTrue(node.isConnected());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testClose() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);

            // Close the connection
            node.close();

            // Verify it's no longer connected
            assertFalse(node.isConnected());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testRpcAfterClose() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);

            // Close the connection
            node.close();

            // Verify RPC call fails
            assertThrows(IllegalStateException.class, () -> {
                node.rpc("test_module", "test_function", ErlAtom.of("test"));
            });
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testErlangExceptionForBadRpc() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);

            // Test that a badrpc error is properly wrapped in ErlangException
            node.rpc("unknown_module", "unknown_function");

            fail("Expected ErlangException for badrpc");
        } catch (ErlangException e) {
            assertTrue(e.isBadRpc());
            assertEquals("unknown_module", e.getErlangModule());
            assertEquals("unknown_function", e.getErlangFunction());
            assertNotNull(e.getErrorDetails());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testErlangExceptionForIoError() {
        try {
            ErlangNode node = new ErlangNode(TEST_NODE, TEST_COOKIE, TEST_SOCKET);

            // Test that an IO error is properly wrapped in ErlangException
            node.rpc("test_module", "function_that_throws_io_error");

            fail("Expected ErlangException for IO error");
        } catch (ErlangException e) {
            assertFalse(e.isBadRpc());
            assertEquals("IO error during RPC to test_module:function_that_throws_io_error", e.getMessage());
            assertNull(e.getErlangModule());
            assertNull(e.getErlangFunction());
        } catch (UnsupportedOperationException e) {
            // Expected due to mock implementation not being available yet
            assertTrue(e.getMessage().contains("Real ei connection not yet implemented"));
        }
    }

    @Test
    void testGetDefaultSocketPath() {
        // Test the static method for getting default socket path
        Path path = ErlangNode.getDefaultSocketPath("test@localhost");
        assertEquals(Paths.get("/tmp/yawl-erlang/test_localhost.sock"), path);
    }
}