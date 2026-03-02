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

package org.yawlfoundation.yawl.observatory.rdf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Base class for observatory module tests.
 *
 * Provides common setup/teardown, test utilities, and file management
 * for all observatory test classes.
 */
public abstract class ObservatoryTestBase {

    protected static final Logger logger = LoggerFactory.getLogger(ObservatoryTestBase.class);
    protected static final String TEST_RESOURCES_DIR = "src/test/resources/observatory";
    protected static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/yawl-observatory-test-" + UUID.randomUUID();

    @BeforeEach
    protected void setupTestEnvironment() throws Exception {
        logger.info("Setting up test environment for: " + this.getClass().getSimpleName());

        // Create temporary directory
        Path tempPath = Paths.get(TEMP_DIR);
        if (!Files.exists(tempPath)) {
            Files.createDirectories(tempPath);
        }

        // Setup test-specific environment
        setupTestSpecificEnvironment();
    }

    @AfterEach
    protected void cleanupTestEnvironment() throws Exception {
        logger.info("Cleaning up test environment for: " + this.getClass().getSimpleName());

        // Cleanup test-specific environment
        cleanupTestSpecificEnvironment();

        // Remove temporary files
        cleanupTemporaryFiles();
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
                try (Stream<Path> stream = Files.walk(tempPath)) {
                    stream.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                logger.warn("Failed to delete temp file: " + path);
                            }
                        });
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to cleanup temporary files: " + e.getMessage());
        }
    }

    /**
     * Creates a temporary file with the given content.
     */
    protected Path createTempFile(String filename, String content) throws Exception {
        Path tempFile = Paths.get(TEMP_DIR, filename);
        Files.createDirectories(tempFile.getParent());
        Files.write(tempFile, content.getBytes());
        return tempFile;
    }

    /**
     * Creates a temporary directory.
     */
    protected Path createTempDirectory(String dirname) throws Exception {
        Path tempDir = Paths.get(TEMP_DIR, dirname);
        Files.createDirectories(tempDir);
        return tempDir;
    }

    /**
     * Creates test JSON fact files for testing.
     */
    protected Path createFactFile(String filename, String content) throws Exception {
        Path factDir = Paths.get(TEMP_DIR, "facts");
        if (!Files.exists(factDir)) {
            Files.createDirectories(factDir);
        }
        Path factFile = factDir.resolve(filename);
        Files.write(factFile, content.getBytes());
        return factFile;
    }

    /**
     * Creates test TTL files for RDF testing.
     */
    protected Path createTtlFile(String filename, String content) throws Exception {
        Path ttlDir = Paths.get(TEMP_DIR, "rdf");
        if (!Files.exists(ttlDir)) {
            Files.createDirectories(ttlDir);
        }
        Path ttlFile = ttlDir.resolve(filename);
        Files.write(ttlFile, content.getBytes());
        return ttlFile;
    }

    /**
     * Helper to log test start.
     */
    protected void logTestStart(String testName) {
        logger.info("=== Starting Test: {} ===", testName);
    }

    /**
     * Helper to log test completion.
     */
    protected void logTestEnd(String testName) {
        logger.info("=== Completed Test: {} ===", testName);
    }

    /**
     * Helper to log test error.
     */
    protected void logError(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    /**
     * Helper to log test warning.
     */
    protected void logWarning(String message) {
        logger.warn(message);
    }
}