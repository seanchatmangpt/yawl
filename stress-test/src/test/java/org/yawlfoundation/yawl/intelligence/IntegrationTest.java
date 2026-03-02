package org.yawlfoundation.yawl.intelligence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ReceiptChain functionality.
 * This test focuses on the core receipt chain operations.
 */
class IntegrationTest {

    @Test
    void testBasicOperations() {
        // Create new receipt chain
        ReceiptChain chain = new ReceiptChain();

        // Create genesis
        chain.createGenesis();
        assertEquals(1, chain.size());
        assertNotNull(chain.getHead());

        // Add entry
        chain.addEntry(List.of("test_event", "data"));
        assertEquals(2, chain.size());
        assertTrue(chain.validateChain());
    }

    @Test
    void testTamperingDetection() {
        ReceiptChain chain = new ReceiptChain();
        chain.createGenesis();
        chain.addEntry(List.of("original_data"));

        // Simulate tampering by modifying an entry
        List<ReceiptEntry> entries = chain.getEntries();
        if (entries.size() > 1) {
            // Create a tampered version of the second entry
            ReceiptEntry original = entries.get(1);
            ReceiptEntry tampered = new ReceiptEntry(
                original.getTimestamp(),
                original.getHash(),  // Keep same hash (would be different in real attack)
                List.of("tampered_data"),  // Change the content
                original.getPreviousHash()
            );

            // Replace the entry
            entries.set(1, tampered);

            // Chain should now be invalid
            assertFalse(chain.validateChain());
        }
    }

    @Test
    void testPersistenceAndRecovery(@TempDir Path tempDir) throws IOException {
        // Create chain and add data
        ReceiptChain originalChain = new ReceiptChain();
        originalChain.createGenesis();
        originalChain.addEntry(List.of("entry_1"));
        originalChain.addEntry(List.of("entry_2"));

        // Verify it's valid
        assertTrue(originalChain.validateChain());
        assertEquals(3, originalChain.size());

        // Copy the persisted file to temp directory for testing
        Path tempReceiptsDir = tempDir.resolve("receipts");
        Path tempReceiptFile = tempReceiptsDir.resolve("intelligence.jsonl");

        // Create temp receipts directory
        java.nio.file.Files.createDirectories(tempReceiptsDir);

        // Copy the persisted file
        if (java.nio.file.Files.exists(originalChain.receiptFile)) {
            java.nio.file.Files.copy(originalChain.receiptFile, tempReceiptFile);
        }

        // Create new chain and load from temp file
        ReceiptChain loadedChain = new ReceiptChain() {
            @Override
            protected Path getReceiptsDir() {
                return tempReceiptsDir;
            }
        };

        loadedChain.loadFromDisk();

        // Verify loaded data
        assertEquals(3, loadedChain.size());
        assertTrue(loadedChain.validateChain());

        // Verify entries match
        List<ReceiptEntry> originalEntries = originalChain.getEntries();
        List<ReceiptEntry> loadedEntries = loadedChain.getEntries();

        assertEquals(originalEntries.size(), loadedEntries.size());

        for (int i = 0; i < originalEntries.size(); i++) {
            ReceiptEntry original = originalEntries.get(i);
            ReceiptEntry loaded = loadedEntries.get(i);

            assertEquals(original.getTimestamp(), loaded.getTimestamp());
            assertEquals(original.getHash(), loaded.getHash());
            assertEquals(original.getDelta(), loaded.getDelta());
            assertEquals(original.getPreviousHash(), loaded.getPreviousHash());
        }
    }

    @Test
    void testHashUniqueness() {
        ReceiptChain chain1 = new ReceiptChain();
        chain1.createGenesis();

        ReceiptChain chain2 = new ReceiptChain();
        chain2.createGenesis();

        // Hashes should be different due to timestamp
        assertNotEquals(chain1.getHead().getHash(), chain2.getHead().getHash());
    }

    @Test
    void testLargeDeltaHandling() {
        ReceiptChain chain = new ReceiptChain();
        chain.createGenesis();

        // Create a large delta
        List<String> largeDelta = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeDelta.add("element_" + i);
        }

        // Should handle without issues
        assertDoesNotThrow(() -> chain.addEntry(largeDelta));
        assertEquals(2, chain.size());
        assertTrue(chain.validateChain());

        ReceiptEntry entry = chain.getHead();
        assertEquals(1000, entry.getDelta().size());
    }
}