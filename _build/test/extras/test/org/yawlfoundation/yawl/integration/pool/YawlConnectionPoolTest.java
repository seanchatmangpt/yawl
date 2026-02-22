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

package org.yawlfoundation.yawl.integration.pool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YawlConnectionPool.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Configuration validation</li>
 *   <li>Pool lifecycle (initialize, shutdown)</li>
 *   <li>Session borrow/return</li>
 *   <li>Connection validation</li>
 *   <li>Metrics collection</li>
 *   <li>Thread safety under concurrent load</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * <p>Tests that require a live YAWL engine are guarded by the
 * YAWL_ENGINE_URL environment variable.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class YawlConnectionPoolTest {

    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String TEST_USERNAME = "admin";
    private static final String TEST_PASSWORD = "YAWL";

    private YawlConnectionPoolConfig config;
    private YawlConnectionPool pool;

    @BeforeEach
    void setUp() {
        config = new YawlConnectionPoolConfig();
        config.setEngineUrl(TEST_ENGINE_URL);
        config.setUsername(TEST_USERNAME);
        config.setPassword(TEST_PASSWORD);
        config.setMaxTotal(5);
        config.setMaxIdle(3);
        config.setMinIdle(1);
        config.setMaxWaitMs(2000);
        config.setHealthCheckIntervalMs(0); // Disable health check for unit tests
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.shutdown();
            pool = null;
        }
    }

    @Nested
    @DisplayName("Configuration Validation")
    class ConfigurationValidation {

        @Test
        @DisplayName("Should reject null config")
        void shouldRejectNullConfig() {
            assertThrows(NullPointerException.class, () -> new YawlConnectionPool(null));
        }

        @Test
        @DisplayName("Should reject missing engine URL")
        void shouldRejectMissingEngineUrl() {
            config.setEngineUrl(null);
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should reject blank engine URL")
        void shouldRejectBlankEngineUrl() {
            config.setEngineUrl("  ");
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should reject missing password")
        void shouldRejectMissingPassword() {
            config.setPassword(null);
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should reject zero maxTotal")
        void shouldRejectZeroMaxTotal() {
            config.setMaxTotal(0);
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should reject negative maxIdle")
        void shouldRejectNegativeMaxIdle() {
            config.setMaxIdle(-1);
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should reject minIdle > maxIdle")
        void shouldRejectMinIdleExceedingMaxIdle() {
            config.setMinIdle(5);
            config.setMaxIdle(3);
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should reject maxIdle > maxTotal")
        void shouldRejectMaxIdleExceedingMaxTotal() {
            config.setMaxIdle(10);
            config.setMaxTotal(5);
            assertThrows(IllegalArgumentException.class, () -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should accept valid configuration")
        void shouldAcceptValidConfiguration() {
            assertDoesNotThrow(() -> new YawlConnectionPool(config));
        }

        @Test
        @DisplayName("Should create copy of configuration")
        void shouldCreateConfigCopy() {
            YawlConnectionPool pool = new YawlConnectionPool(config);
            YawlConnectionPoolConfig returned = pool.getConfig();

            assertEquals(config.getMaxTotal(), returned.getMaxTotal());
            assertEquals(config.getMaxIdle(), returned.getMaxIdle());
            assertEquals(config.getMinIdle(), returned.getMinIdle());
            assertNotSame(config, returned); // Should be a copy
        }
    }

    @Nested
    @DisplayName("Pool Lifecycle")
    class PoolLifecycle {

        @Test
        @DisplayName("Should initialize successfully")
        void shouldInitializeSuccessfully() {
            pool = new YawlConnectionPool(config);
            assertFalse(pool.isInitialized());

            pool.initialize();

            assertTrue(pool.isInitialized());
            assertFalse(pool.isShutdown());
        }

        @Test
        @DisplayName("Should reject double initialization")
        void shouldRejectDoubleInitialization() {
            pool = new YawlConnectionPool(config);
            pool.initialize();

            assertThrows(IllegalStateException.class, () -> pool.initialize());
        }

        @Test
        @DisplayName("Should shutdown gracefully")
        void shouldShutdownGracefully() {
            pool = new YawlConnectionPool(config);
            pool.initialize();

            pool.shutdown();

            assertTrue(pool.isShutdown());
        }

        @Test
        @DisplayName("Should handle double shutdown")
        void shouldHandleDoubleShutdown() {
            pool = new YawlConnectionPool(config);
            pool.initialize();

            pool.shutdown();
            assertDoesNotThrow(() -> pool.shutdown()); // Should not throw
        }

        @Test
        @DisplayName("Should reject borrow when not initialized")
        void shouldRejectBorrowWhenNotInitialized() {
            pool = new YawlConnectionPool(config);

            assertThrows(IllegalStateException.class, () -> pool.borrowSession());
        }

        @Test
        @DisplayName("Should reject borrow when shutdown")
        void shouldRejectBorrowWhenShutdown() {
            pool = new YawlConnectionPool(config);
            pool.initialize();
            pool.shutdown();

            assertThrows(IllegalStateException.class, () -> pool.borrowSession());
        }
    }

    @Nested
    @DisplayName("Configuration Presets")
    class ConfigurationPresets {

        @Test
        @DisplayName("Default configuration should have sensible values")
        void defaultConfigShouldHaveSensibleValues() {
            YawlConnectionPoolConfig defaults = YawlConnectionPoolConfig.defaults();

            assertEquals(20, defaults.getMaxTotal());
            assertEquals(10, defaults.getMaxIdle());
            assertEquals(2, defaults.getMinIdle());
            assertEquals(5000, defaults.getMaxWaitMs());
            assertTrue(defaults.isValidationOnBorrow());
        }

        @Test
        @DisplayName("Development configuration should have smaller pool")
        void developmentConfigShouldHaveSmallerPool() {
            YawlConnectionPoolConfig dev = YawlConnectionPoolConfig.development();

            assertTrue(dev.getMaxTotal() <= 10);
            assertTrue(dev.getMaxIdle() <= 5);
            assertTrue(dev.getMinIdle() >= 1);
        }

        @Test
        @DisplayName("Production configuration should have larger pool")
        void productionConfigShouldHaveLargerPool() {
            YawlConnectionPoolConfig prod = YawlConnectionPoolConfig.production();

            assertTrue(prod.getMaxTotal() >= 20);
            assertTrue(prod.getMaxIdle() >= 10);
            assertTrue(prod.getMinIdle() >= 3);
            assertTrue(prod.isValidationOnBorrow());
            assertTrue(prod.isValidationWhileIdle());
        }

        @Test
        @DisplayName("Copy should create independent configuration")
        void copyShouldCreateIndependentConfiguration() {
            YawlConnectionPoolConfig original = YawlConnectionPoolConfig.production();
            YawlConnectionPoolConfig copy = original.copy();

            assertEquals(original.getMaxTotal(), copy.getMaxTotal());

            copy.setMaxTotal(999);
            assertNotEquals(original.getMaxTotal(), copy.getMaxTotal());
        }
    }

    @Nested
    @DisplayName("Metrics Collection")
    class MetricsCollection {

        @Test
        @DisplayName("Should track pool state")
        void shouldTrackPoolState() {
            pool = new YawlConnectionPool(config);
            YawlConnectionPoolMetrics metrics = pool.getMetrics();

            assertNotNull(metrics);
            assertEquals(0, metrics.getActiveConnections());
            assertEquals(0, metrics.getIdleConnections());
        }

        @Test
        @DisplayName("Should record connection creation")
        void shouldRecordConnectionCreation() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();

            metrics.recordConnectionCreated(100);
            metrics.recordConnectionCreated(200);

            assertEquals(2, metrics.getTotalCreated());
            assertEquals(150.0, metrics.getAverageConnectionCreateTimeMs());
            assertEquals(200, metrics.getMaxConnectionCreateTimeMs());
        }

        @Test
        @DisplayName("Should record borrow operations")
        void shouldRecordBorrowOperations() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();

            metrics.recordBorrowed(50);
            metrics.recordBorrowed(100);
            metrics.recordBorrowed(150);

            assertEquals(3, metrics.getTotalBorrowed());
            assertEquals(100.0, metrics.getAverageBorrowWaitTimeMs());
            assertEquals(150, metrics.getMaxBorrowWaitTimeMs());
        }

        @Test
        @DisplayName("Should record validation results")
        void shouldRecordValidationResults() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();

            metrics.recordValidationPassed();
            metrics.recordValidationPassed();
            metrics.recordValidationFailed();

            assertEquals(2, metrics.getValidationsPassed());
            assertEquals(1, metrics.getValidationsFailed());
            assertEquals(2.0 / 3.0, metrics.getValidationSuccessRate(), 0.001);
        }

        @Test
        @DisplayName("Should record health checks")
        void shouldRecordHealthChecks() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();

            metrics.recordHealthCheck(true);
            metrics.recordHealthCheck(true);
            metrics.recordHealthCheck(false);

            assertEquals(2, metrics.getHealthChecksPassed());
            assertEquals(1, metrics.getHealthChecksFailed());
            assertTrue(metrics.getLastHealthCheckTime() > 0);
            assertTrue(metrics.getLastSuccessfulHealthCheck() > 0);
        }

        @Test
        @DisplayName("Should track errors by type")
        void shouldTrackErrorsByType() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();

            metrics.recordConnectionError("timeout");
            metrics.recordConnectionError("timeout");
            metrics.recordConnectionError("auth_failed");

            Map<String, Long> errors = metrics.getErrorsByType();
            assertEquals(2, errors.get("timeout"));
            assertEquals(1, errors.get("auth_failed"));
        }

        @Test
        @DisplayName("Should convert to map")
        void shouldConvertToMap() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();
            metrics.recordBorrowed(100);
            metrics.recordConnectionCreated(50);

            Map<String, Object> map = metrics.toMap();

            assertNotNull(map);
            assertEquals(1L, map.get("totalBorrowed"));
            assertEquals(1L, map.get("totalCreated"));
            assertTrue(map.containsKey("activeConnections"));
            assertTrue(map.containsKey("validationSuccessRate"));
        }

        @Test
        @DisplayName("Should reset metrics")
        void shouldResetMetrics() {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();

            metrics.recordBorrowed(100);
            metrics.recordConnectionCreated(50);
            metrics.recordValidationPassed();

            metrics.reset();

            assertEquals(0, metrics.getTotalBorrowed());
            assertEquals(0, metrics.getTotalCreated());
            assertEquals(0, metrics.getValidationsPassed());
        }
    }

    @Nested
    @DisplayName("Session Operations")
    class SessionOperations {

        @Test
        @DisplayName("Session should track borrow count")
        void sessionShouldTrackBorrowCount() {
            YawlSession session = createMockSession("test-1");

            assertEquals(0, session.getBorrowCount());

            session.markBorrowed();
            assertEquals(1, session.getBorrowCount());

            session.markBorrowed();
            assertEquals(2, session.getBorrowCount());
        }

        @Test
        @DisplayName("Session should track last used time")
        void sessionShouldTrackLastUsedTime() throws InterruptedException {
            YawlSession session = createMockSession("test-1");

            long firstTime = session.getLastUsedAt();
            Thread.sleep(10);
            session.markBorrowed();

            assertTrue(session.getLastUsedAt() > firstTime);
        }

        @Test
        @DisplayName("Session should start as valid")
        void sessionShouldStartAsValid() {
            YawlSession session = createMockSession("test-1");

            assertTrue(session.isValid());
            assertFalse(session.isClosed());
        }

        @Test
        @DisplayName("Session should be invalidatable")
        void sessionShouldBeInvalidatable() {
            YawlSession session = createMockSession("test-1");

            session.invalidate();

            assertFalse(session.isValid());
        }

        @Test
        @DisplayName("Session should implement equals/hashCode by ID")
        void sessionShouldImplementEqualsHashCodeById() {
            YawlSession session1 = createMockSession("same-id");
            YawlSession session2 = createMockSession("same-id");
            YawlSession session3 = createMockSession("different-id");

            assertEquals(session1, session2);
            assertNotEquals(session1, session3);
            assertEquals(session1.hashCode(), session2.hashCode());
        }

        @Test
        @DisplayName("Session toString should contain useful info")
        void sessionToStringShouldContainUsefulInfo() {
            YawlSession session = createMockSession("test-session-123");

            String str = session.toString();

            assertTrue(str.contains("test-session-123"));
            assertTrue(str.contains("valid=true"));
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("Metrics should be thread-safe")
        void metricsShouldBeThreadSafe() throws Exception {
            YawlConnectionPoolMetrics metrics = new YawlConnectionPoolMetrics();
            int threadCount = 10;
            int operationsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            metrics.recordBorrowed(j);
                            metrics.recordReturned();
                            metrics.recordValidationPassed();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(threadCount * operationsPerThread, metrics.getTotalBorrowed());
            assertEquals(threadCount * operationsPerThread, metrics.getTotalReturned());
            assertEquals(threadCount * operationsPerThread, metrics.getValidationsPassed());
        }

        @Test
        @DisplayName("Config should be thread-safe for reads")
        void configShouldBeThreadSafeForReads() throws Exception {
            YawlConnectionPoolConfig config = YawlConnectionPoolConfig.production();
            int threadCount = 10;
            int readsPerThread = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < readsPerThread; j++) {
                            config.getMaxTotal();
                            config.getMaxIdle();
                            config.getMinIdle();
                            config.isValidationOnBorrow();
                        }
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            startLatch.countDown();
            for (Future<?> f : futures) {
                f.get(30, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertEquals(threadCount, successCount.get());
        }
    }

    @Nested
    @DisplayName("Live Engine Tests")
    @EnabledIfEnvironmentVariable(named = "YAWL_ENGINE_URL", matches = ".+")
    class LiveEngineTests {

        @Test
        @DisplayName("Should connect to live engine")
        void shouldConnectToLiveEngine() {
            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            String password = System.getenv("YAWL_PASSWORD");

            config.setEngineUrl(engineUrl);
            config.setPassword(password != null ? password : TEST_PASSWORD);
            config.setConnectionRetryAttempts(1);

            pool = new YawlConnectionPool(config);
            assertDoesNotThrow(() -> pool.initialize());
        }

        @Test
        @DisplayName("Should borrow and return sessions")
        void shouldBorrowAndReturnSessions() throws Exception {
            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            String password = System.getenv("YAWL_PASSWORD");

            config.setEngineUrl(engineUrl);
            config.setPassword(password != null ? password : TEST_PASSWORD);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            YawlSession session = pool.borrowSession();
            assertNotNull(session);
            assertTrue(session.isValid());

            pool.returnSession(session);

            YawlConnectionPoolMetrics metrics = pool.getMetrics();
            assertEquals(1, metrics.getTotalBorrowed());
            assertEquals(1, metrics.getTotalReturned());
        }

        @Test
        @DisplayName("Should use try-with-resources pattern")
        void shouldUseTryWithResourcesPattern() throws Exception {
            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            String password = System.getenv("YAWL_PASSWORD");

            config.setEngineUrl(engineUrl);
            config.setPassword(password != null ? password : TEST_PASSWORD);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            try (YawlSession session = pool.borrowSession()) {
                assertTrue(session.isValid());
                assertNotNull(session.getHandle());
            }

            YawlConnectionPoolMetrics metrics = pool.getMetrics();
            assertEquals(1, metrics.getTotalBorrowed());
            assertEquals(1, metrics.getTotalReturned());
        }

        @Test
        @DisplayName("Should handle concurrent borrows")
        void shouldHandleConcurrentBorrows() throws Exception {
            String engineUrl = System.getenv("YAWL_ENGINE_URL");
            String password = System.getenv("YAWL_PASSWORD");

            config.setEngineUrl(engineUrl);
            config.setPassword(password != null ? password : TEST_PASSWORD);
            config.setMaxTotal(10);

            pool = new YawlConnectionPool(config);
            pool.initialize();

            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try (YawlSession session = pool.borrowSession()) {
                        assertTrue(session.isValid());
                        Thread.sleep(10); // Simulate work
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            assertEquals(threadCount, successCount.get());
        }
    }

    // ========== Helper Methods ==========

    private YawlSession createMockSession(String sessionId) {
        return new YawlSession(
                sessionId,
                new InterfaceB_EnvironmentBasedClient("http://localhost:8080/yawl/ib"),
                "mock-handle-" + sessionId
        );
    }
}
