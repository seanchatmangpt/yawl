package org.yawlfoundation.yawl.benchmark.sparql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RdfDataGenerator.
 */
class RdfDataGeneratorTest {

    @Test
    void testGenerateSmallDataset(@TempDir Path tempDir) throws IOException {
        RdfDataGenerator generator = new RdfDataGenerator();
        Path outputPath = tempDir.resolve("small-dataset.ttl");
        
        generator.generateDataset(1000, outputPath);
        
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        
        // Check if it contains expected content
        String content = Files.readString(outputPath);
        assertTrue(content.contains("yawl:Case"));
        assertTrue(content.contains("yawl:WorkItem"));
        assertTrue(content.contains("turtle"));
    }

    @Test
    void testGenerateMediumDataset(@TempDir Path tempDir) throws IOException {
        RdfDataGenerator generator = new RdfDataGenerator();
        Path outputPath = tempDir.resolve("medium-dataset.ttl");
        
        generator.generateDataset(10000, outputPath);
        
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        
        // Medium dataset should be larger than small
        Path smallPath = tempDir.resolve("small-dataset.ttl");
        generator.generateDataset(1000, smallPath);
        
        assertTrue(Files.size(outputPath) > Files.size(smallPath));
    }

    @Test
    void testGenerateLargeDataset(@TempDir Path tempDir) throws IOException {
        RdfDataGenerator generator = new RdfDataGenerator();
        Path outputPath = tempDir.resolve("large-dataset.ttl");
        
        generator.generateDataset(100000, outputPath);
        
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        
        // Large dataset should be much larger
        assertTrue(Files.size(outputPath) > 50 * 1024); // > 50KB
    }

    @Test
    void testGenerateVeryLargeDataset(@TempDir Path tempDir) throws IOException {
        RdfDataGenerator generator = new RdfDataGenerator();
        Path outputPath = tempDir.resolve("very-large-dataset.ttl");
        
        generator.generateDataset(1000000, outputPath);
        
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
        
        // Very large dataset should be > 5MB
        assertTrue(Files.size(outputPath) > 5 * 1024 * 1024);
    }

    @Test
    void testUnsupportedDatasetSize() {
        RdfDataGenerator generator = new RdfDataGenerator();
        Path outputPath = Paths.get("test-output.ttl");
        
        assertThrows(IllegalArgumentException.class, () -> {
            generator.generateDataset(5000, outputPath);
        });
    }
}
