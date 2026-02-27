/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.java_python.validation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Abstract base class for validation tests in Java-Python integration
 *
 * Provides common setup/teardown, test utilities, and environment management
 * for all validation tests extending this class.
 */
public abstract class ValidationTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(ValidationTestBase.class);
    protected static final String TEST_RESOURCES_DIR = "src/test/resources";
    protected static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/yawl-test-" + UUID.randomUUID();

    private static ThreadMXBean threadBean;
    private static MemoryMXBean memoryBean;

    @BeforeEach
    protected void setupTestEnvironment() throws Exception {
        logger.info("Setting up test environment for: " + this.getClass().getSimpleName());

        // Initialize management beans
        threadBean = ManagementFactory.getThreadMXBean();
        memoryBean = ManagementFactory.getMemoryMXBean();

        // Create temporary directory
        Path tempPath = Paths.get(TEMP_DIR);
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath);
        }

        // Record baseline metrics
        logBaselineMetrics();

        // Setup test-specific environment if needed
        setupTestSpecificEnvironment();
    }

    @AfterEach
    protected void cleanupTestEnvironment() throws Exception {
        logger.info("Cleaning up test environment for: " + this.getClass().getSimpleName());

        // Cleanup test-specific environment
        cleanupTestSpecificEnvironment();

        // Remove temporary files
        cleanupTemporaryFiles();

        // Log final metrics
        logFinalMetrics();
    }

    protected static void setupTestEnvironment() throws Exception {
        logger.info("Setting up global test environment");

        // Setup global test environment if needed
        // This is called from the @BeforeAll method in specific test classes
    }

    protected static void cleanupTestEnvironment() throws Exception {
        logger.info("Cleaning up global test environment");

        // Cleanup global test environment if needed
        // This is called from the @AfterAll method in specific test classes
    }

    protected void setupTestSpecificEnvironment() throws Exception {
        // Override in subclasses for test-specific setup
    }

    protected void cleanupTestSpecificEnvironment() throws Exception {
        // Override in subclasses for test-specific cleanup
    }

    protected void cleanupTemporaryFiles() throws Exception {
        try {
            Path tempPath = Paths.get(TEMP_DIR);
            if (Files.exists(tempPath)) {
                Files.walk(tempPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            logger.warning("Failed to delete temp file: " + path);
                        }
                    });
            }
        } catch (Exception e) {
            logger.warning("Failed to cleanup temporary files: " + e.getMessage());
        }
    }

    protected void logBaselineMetrics() {
        logger.info("=== Baseline Metrics ===");
        logger.info("Active threads: {}", getActiveThreadCount());
        logger.info("Heap memory used: {} MB", getHeapMemoryUsageMB());
        logger.info("Non-heap memory used: {} MB", getNonHeapMemoryUsageMB());
    }

    protected void logFinalMetrics() {
        logger.info("=== Final Metrics ===");
        logger.info("Active threads: {}", getActiveThreadCount());
        logger.info("Heap memory used: {} MB", getHeapMemoryUsageMB());
        logger.info("Non-heap memory used: {} MB", getNonHeapMemoryUsageMB());
    }

    protected int getActiveThreadCount() {
        return threadBean.getThreadCount();
    }

    protected long getHeapMemoryUsageMB() {
        return memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    protected long getNonHeapMemoryUsageMB() {
        return memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    protected String createTempFile(String content) throws Exception {
        Path tempFile = Paths.get(TEMP_DIR, "temp-" + UUID.randomUUID() + ".txt");
        Files.write(tempFile, content.getBytes());
        return tempFile.toString();
    }

    protected void logTestStart(String testName) {
        logger.info("=== Starting Test: {} ===", testName);
    }

    protected void logTestEnd(String testName) {
        logger.info("=== Completed Test: {} ===", testName);
    }

    protected void logError(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    protected void logWarning(String message) {
        logger.warn(message);
    }

    protected void logInfo(String message) {
        logger.info(message);
    }

    protected void logDebug(String message) {
        logger.debug(message);
    }

    // Helper method to replace Awaitility for waiting on conditions
    protected boolean waitForCondition(long timeoutMs, long pollIntervalMs, Supplier<Boolean> condition) {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < endTime) {
            if (condition.get()) {
                return true;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    // Simple version with default condition
    protected boolean waitForCondition(long timeoutMs) {
        return waitForCondition(timeoutMs, 1000, () -> true);
    }
}