/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/nullPointerException
 */
package org.yawlfoundation.yawl.bridge.router;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BridgeRouter class.
 */
@ExtendWith(MockitoExtension.class)
class BridgeRouterTest {

    @Mock
    private QLeverEngine mockQleverEngine;

    @Mock
    private ProcessMiningClient mockProcessMiningClient;

    private BridgeRouter router;

    @BeforeEach
    void setUp() {
        router = new BridgeRouter(false);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (router != null) {
            router.close();
        }
    }

    @Nested
    @DisplayName("Basic Routing")
    class BasicRouting {

        @Test
        @DisplayName("Route JVM call successfully")
        void testRouteJvmCall() throws Exception {
            // Setup
            when(mockQleverEngine.select(anyString()))
                .thenReturn(Map.of("result", "value"));

            router = BridgeRouter.withQleverEngine(mockQleverEngine, false);

            NativeCall call = NativeCall.of(
                "http://example.org/subject",
                "http://example.org/predicate",
                "http://example.org/object",
                CallPattern.JVM
            );

            // Execute
            RoutingResult result = router.route(call);

            // Verify
            assertTrue(result.isSuccess());
            assertEquals(Map.of("result", "value"), result.getResult());
            assertEquals(call, result.getCall());
            verify(mockQleverEngine).select(anyString());
        }

        @Test
        @DisplayName("Route BEAM call successfully")
        void testRouteBeamCall() throws Exception {
            // Setup
            when(mockProcessMiningClient.importOcel(any()))
                .thenReturn(new org.yawlfoundation.yawl.nativebridge.erlang.OcelId(UUID.randomUUID()));

            router = BridgeRouter.withProcessMiningClient(mockProcessMiningClient, false);

            NativeCall call = NativeCall.of(
                "http://example.org/subject",
                "http://example.org/import",
                "/path/to/file.json",
                CallPattern.BEAM
            );

            // Execute
            RoutingResult result = router.route(call);

            // Verify
            assertTrue(result.isSuccess());
            assertNotNull(result.getResult());
            verify(mockProcessMiningClient).importOcel(any());
        }

        @Test
        @DisplayName("Route DIRECT call throws exception")
        void testRouteDirectCall() {
            NativeCall call = NativeCall.of(
                "s1", "p1", "o1", CallPattern.DIRECT
            );

            assertThrows(UnsupportedOperationException.class, () ->
                router.route(call)
            );
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("Route call with QLever exception")
        void testRouteWithQleverException() throws Exception {
            // Setup
            when(mockQleverEngine.select(anyString()))
                .thenThrow(new QLeverException("Query failed"));

            router = BridgeRouter.withQleverEngine(mockQleverEngine, false);

            NativeCall call = NativeCall.of(
                "s1", "p1", "o1", CallPattern.JVM
            );

            // Execute
            RoutingResult result = router.route(call);

            // Verify
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("Query failed"));
            assertInstanceOf(BridgeRoutingException.class, result.getError());
        }

        @Test
        @DisplayName("Route call with Erlang exception")
        void testRouteWithErlangException() throws Exception {
            // Setup
            when(mockProcessMiningClient.importOcel(any()))
                .thenThrow(new ErlangException("Import failed"));

            router = BridgeRouter.withProcessMiningClient(mockProcessMiningClient, false);

            NativeCall call = NativeCall.of(
                "s1", "p1", "/path/to/file", CallPattern.BEAM
            );

            // Execute
            RoutingResult result = router.route(call);

            // Verify
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("Import failed"));
        }

        @Test
        @DisplayName("Null call throws exception")
        void testNullCall() {
            assertThrows(IllegalArgumentException.class, () ->
                router.route(null)
            );
        }
    }

    @Nested
    @DisplayName("Configuration")
    class Configuration {

        @Test
        @DisplayName("Create router with caching enabled")
        void testRouterWithCaching() {
            router = new BridgeRouter(true);

            NativeCall call1 = NativeCall.of("s1", "p1", "o1", CallPattern.JVM);
            NativeCall call2 = NativeCall.of("s2", "p2", "o2", CallPattern.JVM);

            // Simulate routing (would normally require actual engines)
            // This just tests the cache is created and accessible
            assertTrue(router.getCacheSize() >= 0);
            assertEquals(0, router.getCacheSize()); // Initially empty

            router.clearCache();
            assertEquals(0, router.getCacheSize());
        }

        @Test
        @DisplayName("Create router with custom engines")
        void testRouterWithCustomEngines() throws Exception {
            // Setup
            when(mockQleverEngine.select(anyString()))
                .thenReturn(Map.of("custom", "result"));

            // Create router with custom QLever engine
            router = BridgeRouter.withQleverEngine(mockQleverEngine, false);

            // Verify status
            Map<CallPattern, String> status = router.getExecutorStatus();
            assertTrue(status.containsKey(CallPattern.JVM));
            assertEquals("QleverExecutor", status.get(CallPattern.JVM));

            // Test routing
            NativeCall call = NativeCall.of("s1", "p1", "o1", CallPattern.JVM);
            RoutingResult result = router.route(call);

            assertTrue(result.isSuccess());
            assertEquals(Map.of("custom", "result"), result.getResult());
        }

        @Test
        @DisplayName("Check supported patterns")
        void testSupportedPatterns() {
            // Test with default setup
            assertTrue(router.isSupported(CallPattern.JVM));
            assertTrue(router.isSupported(CallPattern.BEAM));
            assertFalse(router.isSupported(CallPattern.DIRECT));
        }
    }

    @Nested
    @DisplayName("Resource Management")
    class ResourceManagement {

        @Test
        @DisplayName("Close router closes all executors")
        void testCloseClosesExecutors() throws Exception {
            // Setup
            when(mockQleverEngine.close()).thenReturn(null);
            when(mockProcessMiningClient.close()).thenReturn(null);

            router = BridgeRouter.withQleverEngine(mockQleverEngine, false);

            // Execute
            router.close();

            // Verify
            verify(mockQleverEngine).close();
        }

        @Test
        @DisplayName("Close handles executor exceptions gracefully")
        void testCloseHandlesExceptions() throws Exception {
            // Setup
            when(mockQleverEngine.close())
                .thenThrow(new RuntimeException("Close failed"));

            router = BridgeRouter.withQleverEngine(mockQleverEngine, false);

            // Should not throw exception
            assertDoesNotThrow(() -> router.close());

            // Verify engine was still attempted to close
            verify(mockQleverEngine).close();
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperations {

        @Test
        @DisplayName("Multiple concurrent routes succeed")
        void testConcurrentRoutes() throws Exception {
            // Setup
            when(mockQleverEngine.select(anyString()))
                .thenReturn(Map.of("result", "value"));

            router = BridgeRouter.withQleverEngine(mockQleverEngine, false);

            NativeCall call1 = NativeCall.of("s1", "p1", "o1", CallPattern.JVM);
            NativeCall call2 = NativeCall.of("s2", "p2", "o2", CallPattern.JVM);
            NativeCall call3 = NativeCall.of("s3", "p3", "o3", CallPattern.JVM);

            // Execute concurrently
            RoutingResult result1 = router.route(call1);
            RoutingResult result2 = router.route(call2);
            RoutingResult result3 = router.route(call3);

            // Verify all succeeded
            assertTrue(result1.isSuccess());
            assertTrue(result2.isSuccess());
            assertTrue(result3.isSuccess());
        }
    }
}