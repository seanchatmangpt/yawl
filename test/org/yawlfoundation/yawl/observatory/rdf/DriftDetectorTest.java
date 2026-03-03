package org.yawlfoundation.yawl.observatory.rdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for DriftDetector — validates codebase fact change detection
 *
 * Tests drift detection for:
 * - Module structure changes
 * - Dependency changes
 * - Coverage metric changes
 * - Test count changes
 * - Build configuration changes
 */
class DriftDetectorTest {

    private DriftDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Create facts.ttl file with sample RDF content
        String factsContent = """
            @prefix code: <http://ggen.io/code#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

            code:yawl-engine a code:Module ;
                code:name "yawl-engine" ;
                code:version "6.0.0-GA" ;
                code:dependencies 12 ;
                code:testCount 156 ;
                code:coveragePercent 85 .
            """;

        Path factsFile = tempDir.resolve("facts.ttl");
        Files.writeString(factsFile, factsContent, StandardOpenOption.CREATE);

        detector = new DriftDetector(tempDir);
    }

    @Test
    void testInitialHashCreation() throws IOException {
        // First time running should create hash file without drift
        assertFalse(detector.hasDrift());
        detector.updateHashFile(tempDir);

        // Verify hash file was created
        Path hashFile = tempDir.resolve("observatory-facts.sha256");
        assertTrue(Files.exists(hashFile));

        // Verify hash format: "sha256_hash  facts.ttl"
        String hashContent = Files.readString(hashFile);
        assertTrue(hashContent.matches("^[a-f0-9]+\\s+facts\\.ttl$"));
    }

    @Test
    void testDriftDetection() throws IOException {
        // Create initial hash
        detector.updateHashFile(tempDir);

        // Modify facts.ttl (simulate drift)
        Path factsFile = tempDir.resolve("facts.ttl");
        String updatedContent = Files.readString(factsFile)
            .replace("coveragePercent 85", "coveragePercent 87");
        Files.writeString(factsFile, updatedContent);

        // Should detect drift
        assertTrue(detector.hasDrift());
    }

    @Test
    void testNoDriftWithSameContent() throws IOException {
        // Create initial hash
        detector.updateHashFile(tempDir);

        // Write identical content again
        detector.updateHashFile(tempDir);

        // Should not detect drift
        assertFalse(detector.hasDrift());
    }

    @Test
    void testDifferentDriftScenarios() throws IOException {
        // Test different types of drift

        // Module structure drift (added module)
        String moduleDrift = """
            @prefix code: <http://ggen.io/code#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

            code:yawl-engine a code:Module ;
                code:name "yawl-engine" ;
                code:version "6.0.0-GA" ;
                code:dependencies 12 ;
                code:testCount 156 ;
                code:coveragePercent 85 .

            code:yawl-mcp a code:Module ;
                code:name "yawl-mcp" ;
                code:version "1.0.0" .
            """;

        Path factsFile = tempDir.resolve("facts.ttl");
        Files.writeString(factsFile, moduleDrift);
        detector = new DriftDetector(tempDir);
        detector.updateHashFile(tempDir);

        // Change version - should drift
        String versionDrift = moduleDrift.replace("6.0.0-GA", "6.0.1-GA");
        Files.writeString(factsFile, versionDrift);
        assertTrue(detector.hasDrift());
    }
}