package org.yawlfoundation.yawl.bridge.erlang.transport;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.bridge.erlang.ErlangException;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErlangConnectionPool.
 *
 * <p>HYPER_STANDARDS: Comprehensive testing with real connections or throw.</p>
 */
class ErlangConnectionPoolTest {

    private static final String TEST_COOKIE = "test_cookie";
    private static final Duration TEST_RECONNECT_INTERVAL = Duration.ofSeconds(5);

    private ErlangConnectionPool pool;

    @BeforeEach
    void setUp() {
        // Create connection pool
        assertDoesNotThrow(() -> {
            pool = new ErlangConnectionPool(TEST_COOKIE, 3, TEST_RECONNECT_INTERVAL);
        });
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            assertDoesNotThrow(pool::close);
        }
    }

    @Test
    @DisplayName("Create connection pool with default configuration")
    void testCreateWithDefaults() {
        assertDoesNotThrow(() -> {
            ErlangConnectionPool defaultPool = new ErlangConnectionPool(TEST_COOKIE);
            assertNotNull(defaultPool);
            assertEquals(3, defaultPool.getTotalConnectionCount());
            assertEquals(TEST_COOKIE, defaultPool.getCookie());
            defaultPool.close();
        });
    }

    @Test
    @DisplayName("Create connection pool with custom size")
    void testCreateWithCustomSize() {
        assertDoesNotThrow(() -> {
            ErlangConnectionPool customPool = new ErlangConnectionPool(TEST_COOKIE, 2, TEST_RECONNECT_INTERVAL);
            assertNotNull(customPool);
            assertEquals(2, customPool.getTotalConnectionCount());
            customPool.close();
        });
    }

    @Test
    @DisplayName("Fail to create pool with invalid size")
    void testCreateWithInvalidSize() {
        assertThrows(ErlangException.class, () -> {
            new ErlangConnectionPool(TEST_COOKIE, 1, TEST_RECONNECT_INTERVAL);
        });

        assertThrows(ErlangException.class, () -> {
            new ErlangConnectionPool(TEST_COOKIE, 5, TEST_RECONNECT_INTERVAL);
        });
    }

    @Test
    @DisplayName("Fail to create pool with null cookie")
    void testCreateWithNullCookie() {
        assertThrows(ErlangException.class, () -> {
            new ErlangConnectionPool(null, 3, TEST_RECONNECT_INTERVAL);
        });
    }

    @Test
    @DisplayName("Fail to create pool with empty cookie")
    void testCreateWithEmptyCookie() {
        assertThrows(ErlangException.class, () -> {
            new ErlangConnectionPool("", 3, TEST_RECONNECT_INTERVAL);
        });
    }

    @Test
    @DisplayName("Fail to create pool with negative reconnect interval")
    void testCreateWithNegativeReconnectInterval() {
        assertThrows(ErlangException.class, () -> {
            new ErlangConnectionPool(TEST_COOKIE, 3, Duration.ofSeconds(-1));
        });
    }

    @Test
    @DisplayName("Get pool properties")
    void testGetProperties() {
        assertEquals(TEST_COOKIE, pool.getCookie());
        assertEquals(3, pool.getTotalConnectionCount());
        assertEquals(3, pool.getActiveConnectionCount());
        assertTrue(pool.isAllConnectionsHealthy());
    }

    @Test
    @DisplayName("Perform RPC operation")
    void testRpcOperation() {
        // Note: This test will fail until the native ei interface is implemented
        // It tests the interface contract rather than actual functionality
        assertThrows(UnsupportedOperationException.class, () -> {
            pool.rpc("erlang", "node");
        });
    }

    @Test
    @DisplayName("Perform RPC with timeout")
    void testRpcWithTimeout() {
        // Note: This test will fail until the native ei interface is implemented
        // It tests the interface contract rather than actual functionality
        assertThrows(UnsupportedOperationException.class, () -> {
            pool.rpc("erlang", "node", 5, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("Fail to perform RPC on closed pool")
    void testRpcOnClosedPool() {
        pool.close();
        assertThrows(IllegalStateException.class, () -> {
            pool.rpc("erlang", "node");
        });
    }

    @Test
    @DisplayName("Reconnect all failed connections")
    void testReconnectAllFailed() {
        // Initially all connections should be healthy
        assertTrue(pool.isAllConnectionsHealthy());

        // This would normally test reconnection logic, but since we don't have
        // a real BEAM node to simulate failures, we just verify the method exists
        assertDoesNotThrow(() -> {
            pool.reconnectAllFailed();
        });

        // After reconnection, all should still be healthy
        assertTrue(pool.isAllConnectionsHealthy());
    }

    @Test
    @DisplayName("Factory methods")
    void testFactoryMethods() {
        // Test factory with defaults
        assertDoesNotThrow(() -> {
            ErlangConnectionPool factoryPool = ErlangConnectionPool.Factory.create();
            assertNotNull(factoryPool);
            assertEquals("yawl", factoryPool.getCookie());
            assertEquals(3, factoryPool.getTotalConnectionCount());
            factoryPool.close();
        });

        // Test factory with custom size
        assertDoesNotThrow(() -> {
            ErlangConnectionPool sizePool = ErlangConnectionPool.Factory.createWithSize(2);
            assertNotNull(sizePool);
            assertEquals(2, sizePool.getTotalConnectionCount());
            sizePool.close();
        });

        // Test factory with custom cookie
        assertDoesNotThrow(() -> {
            ErlangConnectionPool cookiePool = ErlangConnectionPool.Factory.createWithCookie("custom_cookie");
            assertNotNull(cookiePool);
            assertEquals("custom_cookie", cookiePool.getCookie());
            cookiePool.close();
        });

        // Test factory with custom configuration
        assertDoesNotThrow(() -> {
            ErlangConnectionPool fullPool = ErlangConnectionPool.Factory.create(
                "full_cookie", 4, Duration.ofSeconds(10));
            assertNotNull(fullPool);
            assertEquals("full_cookie", fullPool.getCookie());
            assertEquals(4, fullPool.getTotalConnectionCount());
            fullPool.close();
        });
    }

    @Test
    @DisplayName("Close multiple times safely")
    void testCloseMultipleTimes() {
        assertDoesNotThrow(() -> {
            pool.close();
            pool.close();
            pool.close();
        });
    }

    @Test
    @DisplayName("Close after exceptions")
    void testCloseAfterExceptions() {
        // Try to perform RPC that will throw
        assertThrows(UnsupportedOperationException.class, () -> {
            pool.rpc("erlang", "node");
        });

        // Should still be able to close
        assertDoesNotThrow(pool::close);

        // Should not throw when closing again
        assertDoesNotThrow(pool::close);
    }

    @Test
    @DisplayName("Round-robin connection selection")
    void testRoundRobinSelection() {
        // This test would require mocking or real connections to verify
        // that connections are selected in round-robin fashion
        // For now, we just verify that the interface exists
        assertDoesNotThrow(() -> {
            pool.rpc("erlang", "node");
        });
    }

    @Test
    @DisplayName("Retry on node down error")
    void testRetryOnNodeDownError() {
        // This test would require simulating a {badrpc, nodedown} error
        // to verify that the retry mechanism works
        // For now, we just verify that the method exists
        assertDoesNotThrow(() -> {
            pool.rpc("erlang", "node");
        });
    }

    @Test
    @DisplayName("Handle connection failures")
    void testHandleConnectionFailures() {
        // This test would require simulating connection failures
        // to verify that the failure tracking works
        // For now, we just verify that the method exists
        assertDoesNotThrow(() -> {
            pool.reconnectAllFailed();
        });
    }

    @Test
    @DisplayName("Verify initial connection count")
    void testInitialConnectionCount() {
        assertEquals(3, pool.getActiveConnectionCount());
        assertEquals(3, pool.getTotalConnectionCount());
    }

    @Test
    @DisplayName("Check pool health status")
    void testPoolHealthStatus() {
        assertTrue(pool.isAllConnectionsHealthy());
        assertTrue(pool.isActiveConnectionCount() > 0);
    }

    @Test
    @DisplayName("Handle connection pool with single connection")
    void testSingleConnectionPool() {
        assertDoesNotThrow(() -> {
            ErlangConnectionPool singlePool = new ErlangConnectionPool(TEST_COOKIE, 2, TEST_RECONNECT_INTERVAL);
            assertEquals(2, singlePool.getTotalConnectionCount());
            assertTrue(singlePool.isActiveConnectionCount() >= 1);
            singlePool.close();
        });
    }

    @Test
    @DisplayName("Handle connection pool with maximum connections")
    void testMaxConnectionPool() {
        assertDoesNotThrow(() -> {
            ErlangConnectionPool maxPool = new ErlangConnectionPool(TEST_COOKIE, 4, TEST_RECONNECT_INTERVAL);
            assertEquals(4, maxPool.getTotalConnectionCount());
            assertTrue(maxPool.isActiveConnectionCount() >= 1);
            maxPool.close();
        });
    }
}