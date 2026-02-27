/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConstructReceipt}.
 *
 * Verifies receipt creation, serialisation/deserialisation, chain aggregation,
 * and status semantics for the CONSTRUCT phase audit trail.
 */
class ConstructReceiptTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Construction — GREEN path (triples produced)
    // -------------------------------------------------------------------------

    @Test
    void testGreenReceiptFromTriplesProduced() {
        ConstructReceipt r = new ConstructReceipt("derive-handlers", 1200L, 15,
            List.of("output/TaskHandler.java"));

        assertEquals("derive-handlers", r.getRule());
        assertEquals("GREEN", r.getStatus());
        assertEquals(1200L, r.getElapsedMs());
        assertEquals(15, r.getTriplesProduced());
        assertEquals(List.of("output/TaskHandler.java"), r.getArtifactsGenerated());
        assertNull(r.getErrorMessage());
        assertTrue(r.isGreen());
        assertTrue(r.isPass());
        assertFalse(r.isFail());
    }

    @Test
    void testWarnReceiptWhenZeroTriplesProduced() {
        // 0 triples → WARN (rule fired but no patterns matched)
        ConstructReceipt r = new ConstructReceipt("derive-handlers", 500L, 0, List.of());

        assertEquals("WARN", r.getStatus());
        assertFalse(r.isGreen());
        assertTrue(r.isPass());
        assertFalse(r.isFail());
        assertEquals(0, r.getTriplesProduced());
    }

    // -------------------------------------------------------------------------
    // Construction — FAIL path
    // -------------------------------------------------------------------------

    @Test
    void testFailReceiptFactory() {
        ConstructReceipt r = ConstructReceipt.fail("migrate-concurrency", 300L,
            "SPARQL engine timed out");

        assertEquals("migrate-concurrency", r.getRule());
        assertEquals("FAIL", r.getStatus());
        assertEquals("SPARQL engine timed out", r.getErrorMessage());
        assertEquals(0, r.getTriplesProduced());
        assertTrue(r.getArtifactsGenerated().isEmpty());
        assertFalse(r.isGreen());
        assertFalse(r.isPass());
        assertTrue(r.isFail());
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    @Test
    void testNullRuleThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructReceipt(null, 100L, 5, List.of()));
    }

    @Test
    void testEmptyRuleThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructReceipt("", 100L, 5, List.of()));
    }

    @Test
    void testInvalidStatusThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructReceipt("rule", "INVALID", 100L, 5,
                List.of(), null, Map.of()));
    }

    @Test
    void testNullStatusThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructReceipt("rule", null, 100L, 5,
                List.of(), null, Map.of()));
    }

    @Test
    void testNegativeElapsedThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructReceipt("rule", 100L, -1, List.of()));
    }

    @Test
    void testNegativeTriplesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructReceipt("rule", "GREEN", 100L, -1,
                List.of(), null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Immutability
    // -------------------------------------------------------------------------

    @Test
    void testArtifactsListIsUnmodifiable() {
        ConstructReceipt r = new ConstructReceipt("rule", 100L, 5,
            List.of("output/A.java", "output/B.java"));
        assertThrows(UnsupportedOperationException.class,
            () -> r.getArtifactsGenerated().add("output/C.java"));
    }

    // -------------------------------------------------------------------------
    // Emit and load
    // -------------------------------------------------------------------------

    @Test
    void testEmitAndLoadSingleGreenReceipt() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");

        ConstructReceipt original = new ConstructReceipt("derive-handlers", 800L, 12,
            List.of("output/TaskHandler.java"));
        original.emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertEquals(1, chain.size());
        assertTrue(chain.containsKey("derive-handlers"));

        ConstructReceipt loaded = chain.get("derive-handlers");
        assertEquals("derive-handlers", loaded.getRule());
        assertEquals("GREEN", loaded.getStatus());
        assertEquals(800L, loaded.getElapsedMs());
        assertEquals(12, loaded.getTriplesProduced());
        assertEquals(List.of("output/TaskHandler.java"), loaded.getArtifactsGenerated());
    }

    @Test
    void testEmitAndLoadFailReceipt() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");

        ConstructReceipt.fail("broken-rule", 200L, "Parse error").emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        ConstructReceipt loaded = chain.get("broken-rule");

        assertNotNull(loaded);
        assertEquals("FAIL", loaded.getStatus());
        assertEquals("Parse error", loaded.getErrorMessage());
    }

    @Test
    void testChainAppendsMultipleReceipts() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");

        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 3, List.of("out/B.java")).emitTo(receiptFile);
        new ConstructReceipt("rule-c", 300L, 0, List.of()).emitTo(receiptFile);  // WARN
        ConstructReceipt.fail("rule-d", 50L, "Error").emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertEquals(4, chain.size());
        assertEquals("GREEN", chain.get("rule-a").getStatus());
        assertEquals("GREEN", chain.get("rule-b").getStatus());
        assertEquals("WARN", chain.get("rule-c").getStatus());
        assertEquals("FAIL", chain.get("rule-d").getStatus());
    }

    @Test
    void testLoadNonExistentFileReturnsEmptyMap() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/nonexistent.json");
        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertTrue(chain.isEmpty());
    }

    @Test
    void testLoadNullPathReturnsEmptyMap() throws IOException {
        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(null);
        assertTrue(chain.isEmpty());
    }

    @Test
    void testEmitCreatesParentDirectories() throws IOException {
        Path deepFile = tempDir.resolve("a/b/c/.ggen/construct-receipt.json");
        assertFalse(Files.exists(deepFile.getParent()));

        new ConstructReceipt("rule", 100L, 5, List.of()).emitTo(deepFile);

        assertTrue(Files.exists(deepFile));
    }

    // -------------------------------------------------------------------------
    // Chain aggregation queries
    // -------------------------------------------------------------------------

    @Test
    void testIsChainGreenWhenAllGreen() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");
        new ConstructReceipt("rule-a", 100L, 3, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 7, List.of()).emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertTrue(ConstructReceipt.isChainGreen(chain));
        assertTrue(ConstructReceipt.isChainPass(chain));
    }

    @Test
    void testIsChainGreenFalseWhenWarnPresent() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");
        new ConstructReceipt("rule-a", 100L, 3, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 0, List.of()).emitTo(receiptFile);  // WARN

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertFalse(ConstructReceipt.isChainGreen(chain));
        assertTrue(ConstructReceipt.isChainPass(chain));
    }

    @Test
    void testIsChainPassFalseWhenFailPresent() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");
        new ConstructReceipt("rule-a", 100L, 3, List.of()).emitTo(receiptFile);
        ConstructReceipt.fail("rule-b", 200L, "Error").emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertFalse(ConstructReceipt.isChainGreen(chain));
        assertFalse(ConstructReceipt.isChainPass(chain));
    }

    @Test
    void testIsChainGreenFalseForEmptyChain() {
        assertFalse(ConstructReceipt.isChainGreen(Map.of()));
    }

    @Test
    void testIsChainGreenFalseForNullChain() {
        assertFalse(ConstructReceipt.isChainGreen(null));
    }

    @Test
    void testTotalTriplesProduced() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");
        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 12, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-c", 300L, 0, List.of()).emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertEquals(17, ConstructReceipt.totalTriplesProduced(chain));
    }

    @Test
    void testTotalTriplesProducedForNullChain() {
        assertEquals(0, ConstructReceipt.totalTriplesProduced(null));
    }

    @Test
    void testTotalElapsed() throws IOException {
        Path receiptFile = tempDir.resolve(".ggen/construct-receipt.json");
        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 250L, 3, List.of()).emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = ConstructReceipt.loadChain(receiptFile);
        assertEquals(350L, ConstructReceipt.totalElapsed(chain));
    }

    @Test
    void testTotalElapsedForNullChain() {
        assertEquals(0L, ConstructReceipt.totalElapsed(null));
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    void testToStringContainsKeyFields() {
        ConstructReceipt r = new ConstructReceipt("my-rule", 750L, 8, List.of());
        String str = r.toString();
        assertTrue(str.contains("my-rule"));
        assertTrue(str.contains("GREEN"));
        assertTrue(str.contains("8"));
        assertTrue(str.contains("750"));
    }
}
