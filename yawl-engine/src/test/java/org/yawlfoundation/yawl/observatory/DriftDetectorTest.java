/**
 * Unit tests for DriftDetector class
 *
 * Tests drift detection logic using Chicago TDD principles:
 * - Real test data (no mocks)
 * - Verify contract behavior
 * - Test boundary conditions
 * - Ensure proper exception handling
 */
package org.yawlfoundation.yawl.observatory;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for DriftDetector functionality
 *
 * Tests cover:
 * 1. No drift detection when facts unchanged
 * 2. Drift detection when facts change
 * 3. Proper exception handling
 */
class DriftDetectorTest {

    @TempDir
    Path tempDir;

    private DriftDetector driftDetector;

    @BeforeEach
    void setUp() throws IOException {
        driftDetector = new DriftDetector(tempDir);
    }

    @Test
    @DisplayName("testDetectDrift_WhenNoDrift_ReturnsEmpty")
    void testDetectDrift_WhenNoDrift_ReturnsEmpty() throws Exception {
        // Given: Create JSON files with initial content
        createFactsFile("modules.json", "{\"modules\":[\"yawl-engine\"]}");
        createFactsFile("deps-conflicts.json", "{\"conflicts\":[]}");

        // When: First check - should detect drift (no previous hash)
        boolean hasDrift = driftDetector.checkDrift(tempDir.resolve("no-existing.hash"));

        // Then: Drift detected on first run
        assertTrue(hasDrift, "First run should detect drift (no previous hash)");

        // When: Update hash file and check again
        driftDetector.updateHashFile(tempDir);
        driftDetector = new DriftDetector(tempDir); // Fresh instance
        hasDrift = driftDetector.checkDrift(tempDir.resolve("observatory-facts.sha256"));

        // Then: No drift detected on second run
        assertFalse(hasDrift, "Second run with same facts should show no drift");
        assertEquals(1, tempDir.toFile().listFiles().length, "Only hash file should exist");
    }

    @Test
    @DisplayName("testDetectDrift_WhenDriftExists_ReturnsViolations")
    void testDetectDrift_WhenDriftExists_ReturnsViolations() throws Exception {
        // Given: Create initial facts and hash file
        createFactsFile("modules.json", "{\"modules\":[\"yawl-engine\"]}");
        createFactsFile("deps-conflicts.json", "{\"conflicts\":[]}");

        // First run to establish baseline
        driftDetector.checkDrift(tempDir.resolve("initial.hash"));
        driftDetector.updateHashFile(tempDir);

        // Given: Facts have drifted
        createFactsFile("modules.json", "{\"modules\":[\"yawl-engine\", \"yawl-elements\"]}");

        // When: Check drift with changed facts
        driftDetector = new DriftDetector(tempDir);
        boolean hasDrift = driftDetector.checkDrift(tempDir.resolve("observatory-facts.sha256"));

        // Then: Drift should be detected
        assertTrue(hasDrift, "Changed facts should trigger drift detection");

        // Verify current hash is different from previous
        assertNotEquals(
            driftDetector.getPreviousHash(),
            driftDetector.getCurrentHash(),
            "Current and previous hashes should differ"
        );
    }

    @Test
    @DisplayName("testDetectDrift_WithNullInput_ThrowsException")
    void testDetectDrift_WithNullInput_ThrowsException() {
        // Given: DriftDetector instance

        // When & Then: Null facts directory should throw NullPointerException
        assertThrows(
            NullPointerException.class,
            () -> new DriftDetector(null),
            "DriftDetector should reject null facts directory"
        );

        // When & Then: Null hash file path should throw IOException
        driftDetector = new DriftDetector(tempDir);
        assertThrows(
            IOException.class,
            () -> driftDetector.checkDrift(null),
            "checkDrift should reject null hash file path"
        );
    }

    @Test
    @DisplayName("testMultipleFactsHashing_OrderDeterminism")
    void testMultipleFactsHashing_OrderDeterminism() throws Exception {
        // Given: Create multiple fact files in different orders
        createFactsFile("b.json", "{\"b\":2}");
        createFactsFile("a.json", "{\"a\":1}");

        // When: First run
        driftDetector.checkDrift(tempDir.resolve("order1.hash"));
        String hash1 = driftDetector.getCurrentHash();

        // Given: Same files, different creation order
        Path tempDir2 = tempDir.resolve("copy");
        Files.createDirectories(tempDir2);

        createFactsFile(tempDir2.resolve("a.json"), "{\"a\":1}");
        createFactsFile(tempDir2.resolve("b.json"), "{\"b\":2}");

        // When: Second run
        DriftDetector detector2 = new DriftDetector(tempDir2);
        detector2.checkDrift(tempDir2.resolve("order2.hash"));
        String hash2 = detector2.getCurrentHash();

        // Then: Hashes should be identical (order-independent due to sorting)
        assertEquals(hash1, hash2, "Same facts should produce identical hash regardless of file creation order");
    }

    @Test
    @DisplayName("testHashVerification_WithValidHash_ReturnsTrue")
    void testHashVerification_WithValidHash() throws Exception {
        // Given: Create a file and compute its hash
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content", StandardOpenOption.CREATE);

        String expectedHash = driftDetector.getCurrentHash();

        // When & Then: Verify hash should return true
        assertTrue(
            DriftDetector.verifyHash(testFile, expectedHash),
            "Hash verification should succeed with correct hash"
        );
    }

    @Test
    @DisplayName("testHashVerification_WithInvalidHash_ReturnsFalse")
    void testHashVerification_WithInvalidHash() throws Exception {
        // Given: Create a file
        Path testFile = tempDir.resolve("test.txt");
        Files.writeString(testFile, "test content", StandardOpenOption.CREATE);

        // When & Then: Verify with wrong hash should return false
        assertFalse(
            DriftDetector.verifyHash(testFile, "invalidhash123"),
            "Hash verification should fail with incorrect hash"
        );
    }

    @Test
    @DisplayName("testEmptyFactsDirectory_HashDeterministic")
    void testEmptyFactsDirectory_HashDeterministic() throws Exception {
        // Given: Empty facts directory
        // When: Compute hash
        driftDetector.checkDrift(tempDir.resolve("empty.hash"));
        String emptyHash = driftDetector.getCurrentHash();

        // Then: Hash should be predictable (empty input)
        assertNotNull(emptyHash, "Hash should not be null for empty directory");
        assertEquals(64, emptyHash.length(), "SHA-256 hash should be 64 hex characters");
    }

    // Helper method to create JSON facts files
    private void createFactsFile(String filename, String content) throws IOException {
        Path filePath = tempDir.resolve(filename);
        Files.writeString(
            filePath,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }
}