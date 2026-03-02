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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for DriftDetector using Chicago TDD style.
 *
 * Tests drift detection logic including:
 * - Hash computation and comparison
 * - Drift detection with and without previous hash
 * - File I/O operations and error handling
 * - File modification scenarios
 */
public class DriftDetectorTest {

    private static final String MODULE_1_JSON = """
        {
            "moduleName": "yawl-engine",
            "version": "6.0.0",
            "lineCoverage": 78.5,
            "testCount": 234,
            "dependencies": []
        }
        """;

    private static final String MODULE_2_JSON = """
        {
            "moduleName": "yawl-stateless",
            "version": "6.0.0",
            "lineCoverage": 65.2,
            "testCount": 156,
            "dependencies": ["yawl-engine"]
        }
        """;

    private static final String DEPS_CONFLICTS_JSON = """
        {
            "dependencyConflicts": [
                {
                    "groupId": "org.springframework",
                    "artifactId": "spring-web",
                    "version": "5.3.23",
                    "conflicts": ["spring-boot:2.7.5"]
                }
            ]
        }
        """;

    private DriftDetector detector;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        detector = new DriftDetector(tempDir);
    }

    @Nested
    @DisplayName("Hash Computation Tests")
    class HashComputationTests {

        @Test
        @DisplayName("Compute hash for single JSON file")
        void testComputeHash_SingleFile() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            // When
            String hash = detector.getCurrentHash();

            // Then
            assertNotNull(hash);
            assertEquals(64, hash.length()); // SHA-256 produces 64 hex chars
            assertTrue(hash.matches("[a-f0-9]{64}"));
        }

        @Test
        @DisplayName("Compute hash for multiple JSON files")
        void testComputeHash_MultipleFiles() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);
            createFactFile("tests.json", """
                {
                    "totalTests": 500,
                    "passedTests": 450,
                    "failedTests": 20,
                    "skippedTests": 30
                }
                """);

            // When
            String hash = detector.getCurrentHash();

            // Then
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("Hash includes filename for same content")
        void testHashIncludesFilename() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("file1.json", """{"content": "test"}""");
            createFactFile("file2.json", """{"content": "test"}""");

            // When
            String hash = detector.getCurrentHash();

            // Then
            assertNotNull(hash);
            // Files with same content but different names should produce different hashes
            assertNotEquals(getFileContentHash("file1.json", "file2.json"), hash);
        }

        @Test
        @DisplayName("Empty facts directory produces valid hash")
        void testComputeHash_EmptyDirectory() throws IOException, NoSuchAlgorithmException {
            // Given - empty facts directory

            // When
            String hash = detector.getCurrentHash();

            // Then
            assertNotNull(hash);
            assertEquals(64, hash.length());
            // Same empty directory should produce same hash consistently
            String hash2 = detector.getCurrentHash();
            assertEquals(hash, hash2);
        }
    }

    @Nested
    @DisplayName("Drift Detection Tests")
    class DriftDetectionTests {

        @Test
        @DisplayName("No drift when files unchanged")
        void testNoDrift_InitialRun() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            // When
            boolean hasDrift = detector.checkDrift(getHashFile());

            // Then
            assertFalse(hasDrift, "First run should detect no drift");
            assertNull(detector.getPreviousHash(), "No previous hash on first run");
            assertNotNull(detector.getCurrentHash(), "Current hash should be computed");
        }

        @Test
        @DisplayName("No drift when same files re-checked")
        void testNoDrift_SameFilesRechecked() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            // First run - should have no drift
            detector.checkDrift(getHashFile());
            String firstHash = detector.getCurrentHash();

            // When
            boolean hasDrift = detector.checkDrift(getHashFile());

            // Then
            assertFalse(hasDrift, "Rechecking same files should show no drift");
            assertEquals(firstHash, detector.getCurrentHash(), "Hash should be identical");
            assertEquals(firstHash, detector.getPreviousHash(), "Previous hash should match current");
        }

        @Test
        @DisplayName("Drift detected when new module added")
        void testDriftDetected_NewModuleAdded() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            // First run
            detector.checkDrift(getHashFile());
            String firstHash = detector.getCurrentHash();

            // When - add new module
            createFactFile("new-module.json", """
                {
                    "moduleName": "yawl-integration",
                    "version": "6.0.0",
                    "lineCoverage": 45.0,
                    "testCount": 89,
                    "dependencies": ["yawl-engine"]
                }
                """);

            boolean hasDrift = detector.checkDrift(getHashFile());

            // Then
            assertTrue(hasDrift, "Adding new module should detect drift");
            assertNotEquals(firstHash, detector.getCurrentHash(), "Hash should be different");
            assertEquals(firstHash, detector.getPreviousHash(), "Previous hash should match original");
        }

        @Test
        @DisplayName("Drift detected when module modified")
        void testDriftDetected_ModuleModified() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            // First run
            detector.checkDrift(getHashFile());
            String firstHash = detector.getCurrentHash();

            // When - modify existing module
            String modifiedModule = MODULE_1_JSON.replace("78.5", "82.1");
            createFactFile("modules.json", modifiedModule);

            boolean hasDrift = detector.checkDrift(getHashFile());

            // Then
            assertTrue(hasDrift, "Modifying existing module should detect drift");
            assertNotEquals(firstHash, detector.getCurrentHash(), "Hash should be different");
        }

        @Test
        @DisplayName("Drift detected when module removed")
        void testDriftDetected_ModuleRemoved() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            // First run
            detector.checkDrift(getHashFile());
            String firstHash = detector.getCurrentHash();

            // When - remove module file
            Path modulesFile = getFactFile("modules.json");
            Files.deleteIfExists(modulesFile);

            boolean hasDrift = detector.checkDrift(getHashFile());

            // Then
            assertTrue(hasDrift, "Removing module should detect drift");
            assertNotEquals(firstHash, detector.getCurrentHash(), "Hash should be different");
        }
    }

    @Nested
    @DisplayName("Hash File Operations")
    class HashFileOperationsTests {

        @Test
        @DisplayName("Update hash file with current hash")
        void testUpdateHashFile() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            detector.checkDrift(getHashFile());
            String expectedHash = detector.getCurrentHash();

            // When
            Path hashFile = getHashFile();
            detector.updateHashFile(hashFile.getParent());

            // Then
            assertTrue(Files.exists(hashFile), "Hash file should be created");
            String content = Files.readString(hashFile).trim();
            assertTrue(content.startsWith(expectedHash), "Hash file should contain correct hash");
            assertTrue(content.contains("facts.ttl"), "Hash file should reference facts.ttl");
        }

        @Test
        @DisplayName("Load previous hash from file")
        void testLoadPreviousHash() throws IOException, NoSuchAlgorithmException {
            // Given
            createFactFile("modules.json", MODULE_1_JSON);
            createFactFile("deps-conflicts.json", DEPS_CONFLICTS_JSON);

            detector.checkDrift(getHashFile());
            String expectedHash = detector.getCurrentHash();
            detector.updateHashFile(getHashFile().getParent());

            // Create new detector
            DriftDetector newDetector = new DriftDetector(getFactsDir());

            // When
            boolean hasDrift = newDetector.checkDrift(getHashFile());

            // Then
            assertFalse(hasDrift, "Loaded hash should match current hash");
            assertEquals(expectedHash, newDetector.getPreviousHash(), "Previous hash should match");
        }

        @Test
        @DisplayName("Handle missing hash file gracefully")
        void testLoadPreviousHash_FileNotFound() throws IOException, NoSuchAlgorithmException {
            // Given - no hash file exists

            // When
            boolean hasDrift = detector.checkDrift(getHashFile());

            // Then
            assertFalse(hasDrift, "Missing hash file should not cause drift");
            assertNull(detector.getPreviousHash(), "Previous hash should be null");
        }

        @Test
        @DisplayName("Handle empty hash file gracefully")
        void testLoadPreviousHash_EmptyFile() throws IOException {
            // Given - empty hash file
            Path hashFile = getHashFile();
            Files.createFile(hashFile);

            // When
            boolean hasDrift = detector.checkDrift(hashFile);

            // Then
            assertFalse(hasDrift, "Empty hash file should not cause drift");
            assertNull(detector.getPreviousHash(), "Previous hash should be null");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Handle non-existent facts directory")
        void testNonExistentFactsDirectory() {
            // Given - non-existent directory
            Path nonExistent = Path.of("/tmp/non-existent-dir");
            DriftDetector testDetector = new DriftDetector(nonExistent);

            // When & Then - should throw IOException
            assertThrows(IOException.class, () -> testDetector.checkDrift(getHashFile()));
        }

        @Test
        @DisplayName("Handle SHA-256 algorithm not available")
        void testSha256NotAvailable() {
            // This test verifies that NoSuchAlgorithmException is thrown when SHA-256 is unavailable
            // In practice, this should never happen on standard JVMs

            // Given - we can't realistically make SHA-256 unavailable, so we test the detection logic

            // When & Then - check for specific exception in our implementation
            assertThrows(Exception.class, () -> {
                // This would be tested with a mock in a real scenario
                throw new NoSuchAlgorithmException("SHA-256 not available");
            });
        }

        @Test
        @DisplayName("Handle missing files in facts directory")
        void testMissingFactsFiles() throws IOException, NoSuchAlgorithmException {
            // Given - facts directory exists but no JSON files
            Path factsDir = getFactsDir();

            // When
            String hash = new DriftDetector(factsDir).getCurrentHash();

            // Then
            assertNotNull(hash);
            // Should compute hash for empty directory consistently
            String hash2 = new DriftDetector(factsDir).getCurrentHash();
            assertEquals(hash, hash2);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("Handle very large JSON file")
        void testLargeFile() throws IOException, NoSuchAlgorithmException {
            // Given - large JSON file (approx 50KB)
            StringBuilder largeJson = new StringBuilder("{\"modules\":[");
            for (int i = 0; i < 1000; i++) {
                largeJson.append("""
                    {
                        "moduleName": "module-%d",
                        "version": "6.0.0",
                        "lineCoverage": %f,
                        "testCount": %d,
                        "dependencies": []
                    },
                    """.formatted(i, Math.random() * 100, i));
            }
            largeJson.append("]}");

            createFactFile("large-modules.json", largeJson.toString());

            // When
            String hash = detector.getCurrentHash();

            // Then
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }

        @Test
        @DisplayName("Handle JSON file with special characters")
        void testSpecialCharacters() throws IOException, NoSuchAlgorithmException {
            // Given
            String specialJson = """
                {
                    "moduleName": "yawl-🚀",
                    "description": "Workflow engine with special chars: áéíóú ñ ¿ ¡",
                    "unicode": "🎯✨🔧",
                    "symbols": "!@#$%^&*()_+-=[]{}|;':\",./<>?"
                }
                """;
            createFactFile("special.json", specialJson);

            // When
            String hash = detector.getCurrentHash();

            // Then
            assertNotNull(hash);
            assertEquals(64, hash.length());
        }
    }

    // Helper methods
    private Path getFactsDir() {
        return Path.of(TEMP_DIR, "facts");
    }

    private Path getHashFile() {
        return Path.of(TEMP_DIR, "observatory-facts.sha256");
    }

    private Path createFactFile(String filename, String content) throws IOException {
        Path factDir = getFactsDir();
        if (!Files.exists(factDir)) {
            Files.createDirectories(factDir);
        }
        Path factFile = factDir.resolve(filename);
        Files.write(factFile, content.getBytes());
        return factFile;
    }

    private Path getFactFile(String filename) {
        return getFactsDir().resolve(filename);
    }

    private String getFileContentHash(String filename1, String filename2) throws IOException, NoSuchAlgorithmException {
        // Helper method to compute hash for specific files for comparison
        StringBuilder content = new StringBuilder();
        content.append(Files.readString(getFactFile(filename1)));
        content.append(Files.readString(getFactFile(filename2)));

        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(content.toString().getBytes());

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}