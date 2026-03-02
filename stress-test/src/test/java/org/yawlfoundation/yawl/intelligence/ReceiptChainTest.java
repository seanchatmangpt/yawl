package org.yawlfoundation.yawl.intelligence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for the Blake3 receipt chain implementation.
 */
class ReceiptChainTest {

    @TempDir
    Path tempDir;

    private ReceiptChain receiptChain;

    @BeforeEach
    void setUp() {
        receiptChain = new ReceiptChain();
    }

    @Test
    void testGenesisCreation() {
        // Create genesis entry
        List<String> genesisDelta = List.of("genesis", "timestamp:" + Instant.now());
        receiptChain.createGenesis(genesisDelta);

        // Verify chain has one entry
        assertEquals(1, receiptChain.size());
        assertFalse(receiptChain.isEmpty());

        // Get the genesis receipt
        Receipt genesis = receiptChain.getHead();
        assertNotNull(genesis);
        assertEquals(genesisDelta, genesis.delta());
        assertNull(genesis.priorHash());
        assertNotNull(genesis.hash());
        assertNotNull(genesis.timestamp());

        // Verify genesis validation
        assertTrue(receiptChain.validateChain());
        assertTrue(genesis.validateHash());
    }

    @Test
    void testAppendReceipt() {
        // Create genesis first
        receiptChain.createGenesis(List.of("genesis"));

        // Add new receipt
        List<String> delta = List.of("workitem_created", "case_id:CASE-123", "status:created");
        receiptChain.appendReceipt(delta);

        // Verify chain has two entries
        assertEquals(2, receiptChain.size());
        assertFalse(receiptChain.isEmpty());

        // Get the new receipt
        Receipt receipt = receiptChain.getHead();
        assertEquals(delta, receipt.delta());
        assertNotNull(receipt.hash());
        assertEquals(receiptChain.getGenesis().hash(), receipt.priorHash());
        assertNotNull(receipt.timestamp());

        // Verify chain integrity
        assertTrue(receiptChain.validateChain());
        assertTrue(receipt.validateHash());
    }

    @Test
    void testChainValidation() {
        // Create chain with multiple receipts
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of("entry_1"));
        receiptChain.appendReceipt(List.of("entry_2"));

        // Chain should be valid
        assertTrue(receiptChain.validateChain());
        assertTrue(receiptChain.validateHashChain());
    }

    @Test
    void testChainValidationWithTampering() {
        // Create valid chain
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of("original_data"));

        // Get the receipts
        List<Receipt> receipts = receiptChain.getReceipts();
        Receipt tamperedReceipt = new Receipt(
            receipts.get(1).hash(),  // Keep same hash
            receipts.get(1).priorHash(),
            receipts.get(1).timestamp(),
            List.of("tampered_data")  // Change the content
        );

        // Manually replace receipt (simulating tampering)
        receiptChain.getReceipts().set(1, tamperedReceipt);

        // Chain should now be invalid
        assertFalse(receiptChain.validateChain());
        assertFalse(tamperedReceipt.validateHash());
    }

    @Test
    void testPersistenceAndLoading(@TempDir Path tempDir) throws IOException {
        // Create chain and add receipts
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of("entry_1"));
        receiptChain.appendReceipt(List.of("entry_2"));

        // Verify it's valid
        assertTrue(receiptChain.validateChain());
        assertEquals(3, receiptChain.size());

        // Load from storage
        receiptChain.loadFromStorage();

        // Verify loaded data
        assertEquals(3, receiptChain.size());
        assertTrue(receiptChain.validateChain());
        assertTrue(receiptChain.validateStorage());

        // Verify receipts match
        List<Receipt> loadedReceipts = receiptChain.getReceipts();
        assertEquals("genesis", loadedReceipts.get(0).delta().get(0));
        assertEquals("entry_1", loadedReceipts.get(1).delta().get(0));
        assertEquals("entry_2", loadedReceipts.get(2).delta().get(0));

        // Verify hash chain
        assertEquals(loadedReceipts.get(0).hash(), loadedReceipts.get(1).priorHash());
        assertEquals(loadedReceipts.get(1).hash(), loadedReceipts.get(2).priorHash());
    }

    @Test
    void testHashUniqueness() {
        ReceiptChain chain1 = new ReceiptChain();
        chain1.createGenesis(List.of("genesis"));

        ReceiptChain chain2 = new ReceiptChain();
        chain2.createGenesis(List.of("genesis"));

        // Hashes should be different due to timestamp
        assertNotEquals(chain1.getHead().hash(), chain2.getHead().hash());
    }

    @Test
    void testGenesisErrorWhenNotEmpty() {
        // Add a receipt first
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of("test"));

        // Should throw exception when trying to create genesis again
        assertThrows(IllegalStateException.class, () -> {
            receiptChain.createGenesis(List.of("genesis"));
        });
    }

    @Test
    void testNullDeltaHandling() {
        receiptChain.createGenesis(List.of("genesis"));

        // Should throw NPE for null delta
        assertThrows(NullPointerException.class, () -> {
            receiptChain.appendReceipt(null);
        });
    }

    @Test
    void testEmptyDelta() {
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of());

        // Should work with empty delta
        assertEquals(2, receiptChain.size());
        assertTrue(receiptChain.validateChain());

        Receipt receipt = receiptChain.getHead();
        assertEquals(0, receipt.delta().size());
    }

    @Test
    void testLargeDelta() {
        receiptChain.createGenesis(List.of("genesis"));

        // Create delta with many elements
        List<String> largeDelta = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            largeDelta.add("element_" + i);
        }

        receiptChain.appendReceipt(largeDelta);

        // Should handle large deltas
        assertEquals(2, receiptChain.size());
        assertTrue(receiptChain.validateChain());

        Receipt receipt = receiptChain.getHead();
        assertEquals(1000, receipt.delta().size());
    }

    @Test
    void testWorkItemReceipts() {
        receiptChain.createGenesis(List.of("genesis"));

        // Create work item receipts
        receiptChain.createWorkItemReceipt("CASE-123", "WI-001", "Task1", "created");
        receiptChain.createWorkItemReceipt("CASE-123", "WI-001", "Task1", "completed",
                                          "duration:2.5s", "resources:cpu_25%");

        assertEquals(3, receiptChain.size());
        assertTrue(receiptChain.validateChain());

        // Verify work item receipt content
        Receipt receipt = receiptChain.getHead();
        List<String> delta = receipt.delta();
        assertTrue(delta.contains("workitem_event"));
        assertTrue(delta.contains("case_id:CASE-123"));
        assertTrue(delta.contains("workitem_id:WI-001"));
        assertTrue(delta.contains("task_id:Task1"));
        assertTrue(delta.contains("status:completed"));
    }

    @Test
    void testErrorReceipts() {
        receiptChain.createGenesis(List.of("genesis"));

        // Create error receipt
        receiptChain.createErrorReceipt("CASE-124", "TIMEOUT",
                                      "Task did not complete within timeout period",
                                      "stack_trace:abc123");

        assertEquals(2, receiptChain.size());
        assertTrue(receiptChain.validateChain());

        // Verify error receipt content
        Receipt receipt = receiptChain.getHead();
        List<String> delta = receipt.delta();
        assertTrue(delta.contains("error_event"));
        assertTrue(delta.contains("case_id:CASE-124"));
        assertTrue(delta.contains("error_type:TIMEOUT"));
        assertTrue(delta.contains("error_details:Task did not complete within timeout period"));
    }

    @Test
    void testPerformanceReceipts() {
        receiptChain.createGenesis(List.of("genesis"));

        // Create performance receipt
        receiptChain.createPerformanceReceipt("task_completion", 2500, "Task1");

        assertEquals(2, receiptChain.size());
        assertTrue(receiptChain.validateChain());

        // Verify performance receipt content
        Receipt receipt = receiptChain.getHead();
        List<String> delta = receipt.delta();
        assertTrue(delta.contains("performance_metric"));
        assertTrue(delta.contains("metric:task_completion"));
        assertTrue(delta.contains("duration_ms:2500"));
        assertTrue(delta.contains("context:Task1"));
    }

    @Test
    void testChainSummary() {
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of("test"));

        String summary = receiptChain.getSummary();
        assertTrue(summary.contains("Total receipts: 2"));
        assertTrue(summary.contains("Chain valid: true"));
        assertTrue(summary.contains("Genesis hash:"));
        assertTrue(summary.contains("Head hash:"));
    }

    @Test
    void testClear() throws IOException {
        // Add receipts
        receiptChain.createGenesis(List.of("genesis"));
        receiptChain.appendReceipt(List.of("test"));

        // Verify entries exist
        assertEquals(2, receiptChain.size());

        // Clear chain
        receiptChain.clear();

        // Verify chain is empty
        assertEquals(0, receiptChain.size());
        assertTrue(receiptChain.isEmpty());
    }

    @Test
    void testBlake3Hasher() {
        // Test Blake3 hasher with different inputs
        List<String> delta1 = List.of("test", "data");
        List<String> delta2 = List.of("test", "other");

        String hash1 = Blake3Hasher.hash(delta1, null, Instant.now());
        String hash2 = Blake3Hasher.hash(delta2, null, Instant.now());

        // Hashes should be different
        assertNotEquals(hash1, hash2);

        // Hash should be valid format
        assertTrue(Blake3Hasher.isValidHash(hash1));
        assertEquals(64, hash1.length()); // 32 bytes * 2 hex chars

        // Test with prior hash
        String hash3 = Blake3Hasher.hash(delta1, hash1, Instant.now());
        assertNotEquals(hash1, hash3);
    }

    @Test
    void testReceiptValidation() {
        List<String> delta = List.of("test", "data");
        Receipt receipt = Receipt.create(delta, null);

        // Receipt should validate its own hash
        assertTrue(receipt.validateHash());

        // Modified receipt should fail validation
        Receipt tampered = new Receipt(receipt.hash(), receipt.priorHash(),
                                     receipt.timestamp(), List.of("tampered"));
        assertFalse(tampered.validateHash());
    }
}