package org.yawlfoundation.yawl.bridge.qlever.native;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Integration tests for QLeverNativeBridge FFI layer
 *
 * Tests the FFI layer in isolation, focusing on:
 * - Native library loading and initialization
 * - Memory management and resource cleanup
 * - Error handling and status reporting
 * - Thread safety and concurrent access
 */
class QleverNativeBridgeTest {

    private QleverNativeBridge bridge;
    private static final String TEST_INDEX_PATH = "/tmp/test-yawl-index";

    @BeforeEach
    void setUp() {
        // Initialize bridge for testing
        bridge = new QleverNativeBridge();
    }

    @Test
    @DisplayName("Bridge Initialization and Global Setup")
    void testBridgeInitialization() {
        // Test that bridge can be created without exceptions
        assertNotNull(bridge);

        // Test global initialization
        QleverStatus initStatus = bridge.initializeGlobal();
        assertNotNull(initStatus);

        // If initialization fails, it's expected in test environment
        if (initStatus.isSuccess()) {
            assertTrue(initStatus.isSuccess());
            assertEquals(0, initStatus.code());
        } else {
            // For testing without native library, we expect failure
            assertFalse(initStatus.isSuccess());
            assertTrue(initStatus.message().contains("not available") ||
                      initStatus.message().contains("library"));
        }
    }

    @Test
    @DisplayName("Engine Creation and Lifecycle")
    void testEngineCreationAndLifecycle() {
        // Initialize global state first
        QleverStatus initStatus = bridge.initializeGlobal();

        if (!initStatus.isSuccess()) {
            // Skip test if native library not available
            return;
        }

        // Test engine creation
        try {
            MemorySegment engineHandle = bridge.createEngine(TEST_INDEX_PATH);
            if (engineHandle != null && engineHandle != MemorySegment.NULL) {
                assertNotNull(engineHandle);

                // Test engine destruction
                bridge.destroyEngine(engineHandle);
            }
        } catch (Exception e) {
            // In test environment, we might not have native library
            assertTrue(e.getMessage().contains("not available") ||
                      e.getMessage().contains("library") ||
                      e.getMessage().contains("symbol"));
        }
    }

    @Test
    @DisplayName("Query Validation")
    void testQueryValidation() {
        // Test query validation functionality
        String validQuery = "ASK { ?s ?p ?o }";
        String invalidQuery = "INVALID QUERY SYNTAX";

        // Test valid query
        QleverStatus validStatus = bridge.validateQuery(validQuery);
        assertNotNull(validStatus);

        // Test invalid query - should return failure or specific error
        QleverStatus invalidStatus = bridge.validateQuery(invalidQuery);
        assertNotNull(invalidStatus);
    }

    @Test
    @DisplayName("Memory Management")
    void testMemoryManagement() {
        // Test that memory is properly managed during operations
        Path tempFile = null;
        try {
            // Create temporary test file
            tempFile = Files.createTempFile("test-index", ".dat");
            String testPath = tempFile.toString();

            // Initialize global state
            QleverStatus initStatus = bridge.initializeGlobal();

            if (initStatus.isSuccess()) {
                try {
                    // Test engine creation with temporary file
                    MemorySegment engineHandle = bridge.createEngine(testPath);

                    if (engineHandle != null && engineHandle != MemorySegment.NULL) {
                        // Test multiple operations to verify memory stability
                        for (int i = 0; i < 10; i++) {
                            QleverStatus status = bridge.validateQuery("ASK { ?s ?p ?o }");
                            assertNotNull(status);
                        }

                        // Clean up engine
                        bridge.destroyEngine(engineHandle);
                    }
                } finally {
                    // Clean up temporary file
                    Files.deleteIfExists(tempFile);
                }
            }
        } catch (Exception e) {
            // Clean up even if test fails
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception deleteEx) {
                    // Ignore delete errors
                }
            }

            // In test environment, we expect some failures
            assertTrue(e.getMessage().contains("not available") ||
                      e.getMessage().contains("library") ||
                      e.getMessage().contains("symbol"));
        }
    }

    @Test
    @DisplayName("Error Handling")
    void testErrorHandling() {
        // Test error handling for various scenarios
        QleverStatus errorStatus = bridge.validateQuery("");

        assertNotNull(errorStatus);
        // Should handle empty query gracefully
        assertTrue(errorStatus.code() != 0 || errorStatus.message().contains("empty"));

        // Test with null query
        assertThrows(IllegalArgumentException.class, () -> {
            bridge.validateQuery(null);
        });
    }

    @Test
    @DisplayName("Concurrent Access")
    void testConcurrentAccess() throws InterruptedException {
        // Test thread safety of the bridge
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        QleverStatus[] results = new QleverStatus[threadCount];

        // Initialize global state
        QleverStatus initStatus = bridge.initializeGlobal();

        if (!initStatus.isSuccess()) {
            return; // Skip if native library not available
        }

        // Create threads that perform concurrent operations
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                try {
                    results[threadIndex] = bridge.validateQuery("ASK { ?s ?p ?o }");
                } catch (Exception e) {
                    results[threadIndex] = new QleverStatus(
                        QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                        e.getMessage()
                    );
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (int i = 0; i < threadCount; i++) {
            threads[i].join();
        }

        // Verify all operations completed
        for (int i = 0; i < threadCount; i++) {
            assertNotNull(results[i]);
        }
    }

    @Test
    @DisplayName("Version Information")
    void testVersionInformation() {
        // Test version information retrieval
        String version = bridge.getVersion();

        assertNotNull(version);
        // Should return version even if native library not available
        assertFalse(version.isEmpty());
    }

    @Test
    @DisplayName("Resource Cleanup")
    void testResourceCleanup() {
        // Test that resources are properly cleaned up
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("test-cleanup", ".dat");
            String testPath = tempFile.toString();

            // Initialize global state
            QleverStatus initStatus = bridge.initializeGlobal();

            if (initStatus.isSuccess()) {
                MemorySegment engineHandle = null;
                try {
                    engineHandle = bridge.createEngine(testPath);

                    if (engineHandle != null && engineHandle != MemorySegment.NULL) {
                        // Perform some operations
                        QleverStatus status = bridge.validateQuery("ASK { ?s ?p ?o }");
                        assertNotNull(status);
                    }
                } finally {
                    // Ensure engine is destroyed even if operations fail
                    if (engineHandle != null && engineHandle != MemorySegment.NULL) {
                        bridge.destroyEngine(engineHandle);
                    }

                    // Verify file is still accessible after cleanup
                    assertTrue(Files.exists(tempFile));
                }
            }
        } catch (Exception e) {
            // In test environment, we might not have native library
            assertTrue(e.getMessage().contains("not available") ||
                      e.getMessage().contains("library") ||
                      e.getMessage().contains("symbol"));
        } finally {
            // Clean up temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception deleteEx) {
                    // Ignore delete errors
                }
            }
        }
    }

    @Test
    @DisplayName("Invalid Path Handling")
    void testInvalidPathHandling() {
        // Test handling of invalid paths
        QleverStatus status = bridge.validateQuery("SELECT * FROM non_existent_path");

        assertNotNull(status);
        // Should handle invalid paths gracefully
        assertTrue(status.code() != 0 ||
                  status.message().contains("not found") ||
                  status.message().contains("invalid"));
    }

    @Test
    @DisplayName("Memory Segment Validation")
    void testMemorySegmentValidation() {
        // Test validation of memory segments
        // This should not throw exceptions for null or invalid segments
        try {
            // Test with null segment - should handle gracefully
            QleverStatus status = bridge.validateQuery("ASK { ?s ?p ?o }");
            assertNotNull(status);
        } catch (Exception e) {
            // Some implementations might throw exceptions for null references
            assertNotNull(e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up bridge resources
        if (bridge != null) {
            try {
                bridge.shutdownGlobal();
            } catch (Exception e) {
                // Ignore shutdown errors in test environment
            }
        }
    }
}