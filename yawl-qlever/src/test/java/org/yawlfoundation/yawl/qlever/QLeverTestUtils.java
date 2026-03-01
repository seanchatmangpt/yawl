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

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test utilities for QLever FFI bindings Chicago TDD tests.
 *
 * <p>This utility class provides helper methods for setting up test scenarios,
 * validating results, and measuring performance in Chicago TDD style tests.</p>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
public final class QLeverTestUtils {

    private static final String TEST_INDEX_ROOT = "test_qlever_index";
    private static final String SAMPLE_TRIPLES =
            "<http://example.org/s1> <http://example.org/p1> <http://example.org/o1> .\n" +
            "<http://example.org/s1> <http://example.org/p2> \"object value 1\" .\n" +
            "<http://example.org/s2> <http://example.org/p1> <http://example.org/o2> .\n" +
            "<http://example.org/s2> <http://example.org/p2> \"object value 2\" .\n" +
            "<http://example.org/s3> <http://example.org/p1> <http://example.org/o3> .";

    private static final String SAMPLE_SCHEMA =
            "@prefix : <http://example.org/> .\n" +
            ":s1 a :Type1 .\n" +
            ":s2 a :Type2 .\n" +
            ":s3 a :Type3 .\n" +
            ":s1 :p1 ?o1 .\n" +
            ":s1 :p2 ?o2 .\n" +
            ":s2 :p1 ?o3 .\n" +
            ":s2 :p2 ?o4 .\n" +
            ":s3 :p1 ?o5 .";

    private Path testIndexDir;
    private Path testTriplesFile;
    private Path testSchemaFile;

    private QLeverFfiBindings bindings;
    private boolean testEnvironmentValid = false;

    /**
     * Sets up test environment if native library is available.
     */
    @BeforeEach
    public void setupTestEnvironment() {
        // Check native library availability
        testEnvironmentValid = isNativeLibraryAvailable();

        if (!testEnvironmentValid) {
            return; // Skip tests if native library not available
        }

        // Initialize bindings
        bindings = new QLeverFfiBindings();

        // Create test directory structure
        try {
            testIndexDir = Files.createTempDirectory(TEST_INDEX_ROOT);
            testTriplesFile = testIndexDir.resolve("triples.nq");
            testSchemaFile = testIndexDir.resolve("schema.nq");

            // Write sample data
            Files.write(testTriplesFile, SAMPLE_TRIPLES.getBytes());
            Files.write(testSchemaFile, SAMPLE_SCHEMA.getBytes());

        } catch (IOException e) {
            fail("Failed to setup test environment: " + e.getMessage());
            testEnvironmentValid = false;
        }
    }

    /**
     * Cleans up test environment.
     */
    @AfterEach
    public void cleanupTestEnvironment() {
        if (!testEnvironmentValid) {
            return;
        }

        // Clean up bindings
        if (bindings != null) {
            bindings.close();
        }

        // Clean up test files
        if (testIndexDir != null && Files.exists(testIndexDir)) {
            try {
                Files.walk(testIndexDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Non-fatal cleanup error
                            System.err.println("Failed to delete: " + path);
                        }
                    });
            } catch (IOException e) {
                System.err.println("Cleanup error: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if native library is available for testing.
     */
    private boolean isNativeLibraryAvailable() {
        try {
            // Try to create and immediately close bindings
            new QLeverFfiBindings().close();
            return true;
        } catch (QLeverFfiException e) {
            System.err.println("Native library not available: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error checking native library: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the test index directory.
     */
    public Path getTestIndexDir() {
        assumeTrue(testEnvironmentValid, "Test environment not set up");
        return testIndexDir;
    }

    /**
     * Gets the test triples file.
     */
    public Path getTestTriplesFile() {
        assumeTrue(testEnvironmentValid, "Test environment not set up");
        return testTriplesFile;
    }

    /**
     * Gets the test schema file.
     */
    public Path getTestSchemaFile() {
        assumeTrue(testEnvironmentValid, "Test environment not set up");
        return testSchemaFile;
    }

    /**
     * Gets the QLever bindings.
     */
    public QLeverFfiBindings getBindings() {
        assumeTrue(testEnvironmentValid, "Test environment not set up");
        return bindings;
    }

    /**
     * Creates a test index with sample data.
     */
    public QLeverStatus createTestIndex() {
        assumeTrue(testEnvironmentValid, "Test environment not set up");
        return bindings.indexCreate(testIndexDir.toString());
    }

    /**
     * Verifies that an index operation completed within specified time bounds.
     */
    public static void assertOperationWithinBounds(long startTime, long endTime,
                                                  long maxMicroseconds, String operationName) {
        long durationNanos = endTime - startTime;
        long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSCONDS);

        String message = String.format("%s took %dµs (max: %dµs)",
                                      operationName, durationMicros, maxMicroseconds);

        assertTrue(durationMicros < maxMicroseconds, message);
    }

    /**
     * Verifies that a result contains expected SPARQL JSON structure.
     */
    public static void assertValidSparqlJson(String result) {
        assertNotNull(result, "Result should not be null");
        assertTrue(result.length() > 0, "Result should not be empty");

        // Basic JSON structure validation
        assertTrue(result.startsWith("{"), "Result should start with '{'");
        assertTrue(result.contains("\"head\""), "Result should contain head section");
        assertTrue(result.contains("\"vars\""), "Result should contain variables section");

        // Check for results section
        if (result.contains("\"results\"")) {
            assertTrue(result.contains("\"bindings\""), "Results should contain bindings");
        }
    }

    /**
     * Verifies that an error message indicates the expected type of error.
     */
    public static void assertErrorContains(String error, String... expectedKeywords) {
        assertNotNull(error, "Error message should not be null");
        assertFalse(error.isEmpty(), "Error message should not be empty");

        for (String keyword : expectedKeywords) {
            assertTrue(error.toLowerCase().contains(keyword.toLowerCase()),
                      "Error should contain: " + keyword);
        }
    }

    /**
     * Creates a read-only directory for testing permission scenarios.
     */
    public Path createReadOnlyDirectory() throws IOException {
        Path readOnlyDir = Files.createTempDirectory("readonly_test");
        Files.setPosixFilePermissions(readOnlyDir,
            PosixFilePermissions.fromString("r--r--r--"));
        return readOnlyDir;
    }

    /**
     * Creates a valid test directory structure.
     */
    public void createMinimalTestFiles() throws IOException {
        Files.createDirectories(testIndexDir);
        Files.write(testTriplesFile, new byte[0]);
        Files.write(testSchemaFile, new byte[0]);
    }

    /**
     * Creates a test directory with specific content.
     */
    public void createTestFilesWithContent(String triples, String schema) throws IOException {
        Files.createDirectories(testIndexDir);
        Files.write(testTriplesFile, triples.getBytes());
        Files.write(testSchemaFile, schema.getBytes());
    }

    /**
     * Validates memory segment handles.
     */
    public static void validateMemorySegment(MemorySegment segment, String description) {
        if (segment == null) {
            return; // Null is valid in some contexts
        }

        // Check that segment is not invalid
        assertFalse(segment.equals(MemorySegment.NULL),
                   description + " should not be NULL");
    }

    /**
     * Validates result iteration for expected count.
     */
    public void validateResultIteration(QLeverFfiBindings bindings, MemorySegment resultHandle,
                                       int expectedCount) {
        int actualCount = 0;
        while (bindings.resultHasNext(resultHandle)) {
            String result = bindings.resultNext(resultHandle);
            assertNotNull(result, "Result should not be null");
            actualCount++;
        }

        assertEquals(expectedCount, actualCount,
                    "Result count mismatch. Expected: " + expectedCount + ", Actual: " + actualCount);
    }

    /**
     * Gets environment-specific performance thresholds.
     */
    public static long getPerformanceThreshold(String operation) {
        // Different thresholds based on operation type and environment
        return switch (operation.toLowerCase()) {
            case "indexcreate" -> 1000;  // 1ms for index creation
            case "indexdestroy" -> 500;   // 500µs for index destruction
            case "triplecount" -> 200;    // 200µs for triple count
            case "queryexec" -> 50000;    // 50ms for query execution
            case "resultiteration" -> 10000; // 10ms for result iteration
            case "errorretrieval" -> 100;  // 100µs for error retrieval
            default -> 1000;              // Default 1ms
        };
    }

    /**
     * Checks if running in CI environment.
     */
    public static boolean isCIEnvironment() {
        return System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
    }

    /**
     * Gets environment-specific test configuration.
     */
    public static TestConfiguration getTestConfiguration() {
        boolean isCI = isCIEnvironment();

        return new TestConfiguration(
            isCI ? 50 : 100,          // timeoutMultiplier
            isCI ? 2 : 5,             // maxRetries
            isCI ? "slow" : "normal"  // performanceProfile
        );
    }

    /**
     * Test configuration holder.
     */
    public record TestConfiguration(int timeoutMultiplier, int maxRetries, String performanceProfile) {}
}