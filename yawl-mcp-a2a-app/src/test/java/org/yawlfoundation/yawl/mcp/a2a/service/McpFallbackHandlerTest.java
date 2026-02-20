package org.yawlfoundation.yawl.mcp.a2a.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.fallback.Fallback;

/**
 * Tests for the modernized McpFallbackHandler using Resilience4j Fallback decorator
 * and Caffeine cache.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("McpFallbackHandler Modernization Tests")
class McpFallbackHandlerTest {

    private McpFallbackHandler handler;
    private CircuitBreakerProperties.FallbackConfig config;

    @BeforeEach
    void setUp() {
        config = new CircuitBreakerProperties.FallbackConfig(
            true,          // enabled
            30,            // cacheTtlSeconds
            true,          // staleWhileRevalidate
            "error"        // fallbackResponseBehavior
        );
        handler = new McpFallbackHandler(config);
    }

    @Test
    @DisplayName("Should cache successful results with TTL")
    void testCacheResultWithTtl() {
        String serverId = "server1";
        String operation = "listTools";
        List<String> result = Collections.singletonList("tool1");

        handler.cacheResult(serverId, operation, result);

        Optional<List<String>> cached = handler.getFallback(serverId, operation);
        assertTrue(cached.isPresent());
        assertEquals(result, cached.get());
    }

    @Test
    @DisplayName("Should return empty when no cached entry exists")
    void testGetFallbackWhenEmpty() {
        Optional<Object> result = handler.getFallback("unknown", "unknown");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should create Fallback decorator on demand")
    void testGetFallbackDecorator() {
        String serverId = "server1";
        String operation = "callTool:myTool";

        Fallback<String> decorator = handler.getFallbackDecorator(serverId, operation);
        assertNotNull(decorator);

        // Second call should return same cached decorator
        Fallback<String> decorator2 = handler.getFallbackDecorator(serverId, operation);
        assertSame(decorator, decorator2);
    }

    @Test
    @DisplayName("Fallback decorator should return cached value on exception")
    void testFallbackDecoratorUsesCache() throws Exception {
        String serverId = "server1";
        String operation = "getPrompt:myPrompt";
        String cachedValue = "stale data";

        // Pre-populate cache
        handler.cacheResult(serverId, operation, cachedValue);

        // Get fallback decorator
        Fallback<String> decorator = handler.getFallbackDecorator(serverId, operation);

        // Execute with exception
        String result = decorator.execute(() -> {
            throw new RuntimeException("Service unavailable");
        });

        assertEquals(cachedValue, result);
    }

    @Test
    @DisplayName("Fallback decorator should throw when no cache and error behavior set")
    void testFallbackDecoratorThrowsWhenNoCacheAndErrorBehavior() {
        String serverId = "server1";
        String operation = "readResource";

        Fallback<String> decorator = handler.getFallbackDecorator(serverId, operation);

        assertThrows(RuntimeException.class, () -> {
            decorator.execute(() -> {
                throw new RuntimeException("Service unavailable");
            });
        });
    }

    @Test
    @DisplayName("Should support empty fallback behavior")
    void testFallbackDecoratorEmptyBehavior() throws Exception {
        config = new CircuitBreakerProperties.FallbackConfig(
            true,
            30,
            true,
            "empty"  // Return null instead of throwing
        );
        handler = new McpFallbackHandler(config);

        String serverId = "server1";
        String operation = "listResources";

        Fallback<String> decorator = handler.getFallbackDecorator(serverId, operation);

        // Should not throw, just return null
        String result = decorator.execute(() -> {
            throw new RuntimeException("Service unavailable");
        });

        assertNull(result);
    }

    @Test
    @DisplayName("Should clear all cache entries")
    void testClearCache() {
        handler.cacheResult("server1", "op1", "result1");
        handler.cacheResult("server1", "op2", "result2");
        handler.cacheResult("server2", "op1", "result3");

        assertEquals(3, handler.getCacheSize());

        handler.clearCache();

        assertEquals(0, handler.getCacheSize());
    }

    @Test
    @DisplayName("Should clear cache for specific server")
    void testClearCacheForServer() {
        handler.cacheResult("server1", "op1", "result1");
        handler.cacheResult("server1", "op2", "result2");
        handler.cacheResult("server2", "op1", "result3");

        assertEquals(3, handler.getCacheSize());

        handler.clearCacheForServer("server1");

        assertEquals(1, handler.getCacheSize());
        assertTrue(handler.getFallback("server2", "op1").isPresent());
        assertFalse(handler.getFallback("server1", "op1").isPresent());
    }

    @Test
    @DisplayName("Should handle disabled configuration")
    void testDisabledConfiguration() {
        config = new CircuitBreakerProperties.FallbackConfig(false, 0, false, "error");
        handler = new McpFallbackHandler(config);

        handler.cacheResult("server1", "op1", "result1");

        // Should not cache when disabled
        Optional<String> result = handler.getFallback("server1", "op1");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should shutdown gracefully")
    void testShutdown() {
        handler.cacheResult("server1", "op1", "result1");
        assertEquals(1, handler.getCacheSize());

        handler.shutdown();

        assertEquals(0, handler.getCacheSize());
    }

    @Test
    @DisplayName("Cache stats should reflect usage")
    void testCacheStats() {
        handler.cacheResult("server1", "op1", "result1");
        handler.cacheResult("server1", "op2", "result2");

        // Access entries to trigger hits
        handler.getFallback("server1", "op1");  // hit
        handler.getFallback("server1", "op1");  // hit
        handler.getFallback("server1", "op2");  // hit
        handler.getFallback("server1", "op3");  // miss

        var stats = handler.getCacheStats();
        assertEquals(3L, stats.hitCount());
        assertEquals(1L, stats.missCount());
    }
}
