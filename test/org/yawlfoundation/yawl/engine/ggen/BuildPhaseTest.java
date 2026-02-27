/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BuildPhase and BuildReceipt.
 *
 * Tests real receipt generation, parsing, and validation logic.
 * Uses temp directories for isolation (Chicago TDD style).
 */
public class BuildPhaseTest {

    @TempDir
    Path tempDir;

    private Path receiptFile;

    @BeforeEach
    void setUp() {
        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
    }

    @Test
    void testBuildReceiptCreation() {
        BuildReceipt receipt = new BuildReceipt("compile", "GREEN", 1000);

        assertEquals("compile", receipt.getPhase());
        assertEquals("GREEN", receipt.getStatus());
        assertEquals(1000, receipt.getElapsedMs());
        assertTrue(receipt.isGreen());
        assertTrue(receipt.isPass());
    }

    @Test
    void testBuildReceiptWithDetails() {
        Map<String, Object> details = Map.of(
            "modules", 5,
            "tests", 100
        );
        BuildReceipt receipt = new BuildReceipt("test", "GREEN", 5000, details);

        assertEquals("test", receipt.getPhase());
        assertEquals("GREEN", receipt.getStatus());
        assertEquals(5000, receipt.getElapsedMs());
        assertTrue(receipt.isGreen());

        Map<String, Object> detailsCopy = receipt.getDetails();
        assertEquals(5, detailsCopy.get("modules"));
        assertEquals(100, detailsCopy.get("tests"));
    }

    @Test
    void testBuildReceiptWarn() {
        BuildReceipt receipt = new BuildReceipt("validate", "WARN", 3000);

        assertEquals("validate", receipt.getPhase());
        assertEquals("WARN", receipt.getStatus());
        assertFalse(receipt.isGreen());
        assertTrue(receipt.isPass());  // WARN is a pass
    }

    @Test
    void testBuildReceiptFail() {
        BuildReceipt receipt = new BuildReceipt("compile", "FAIL", 500);

        assertEquals("compile", receipt.getPhase());
        assertEquals("FAIL", receipt.getStatus());
        assertFalse(receipt.isGreen());
        assertFalse(receipt.isPass());
    }

    @Test
    void testBuildReceiptValidation() {
        // Invalid phase name
        assertThrows(IllegalArgumentException.class, () -> new BuildReceipt("", "GREEN", 1000));
        assertThrows(IllegalArgumentException.class, () -> new BuildReceipt(null, "GREEN", 1000));

        // Invalid status
        assertThrows(IllegalArgumentException.class, () -> new BuildReceipt("compile", "INVALID", 1000));
        assertThrows(IllegalArgumentException.class, () -> new BuildReceipt("compile", null, 1000));

        // Invalid elapsed time
        assertThrows(IllegalArgumentException.class, () -> new BuildReceipt("compile", "GREEN", -1));
    }

    @Test
    void testReceiptEmitAndLoad() throws IOException {
        // Create and emit receipt
        BuildReceipt receipt1 = new BuildReceipt("compile", "GREEN", 1000);
        receipt1.emitTo(receiptFile);

        // Load and verify
        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        assertFalse(chain.isEmpty());
        assertTrue(chain.containsKey("compile"));

        BuildReceipt loaded = chain.get("compile");
        assertEquals("compile", loaded.getPhase());
        assertEquals("GREEN", loaded.getStatus());
        assertEquals(1000, loaded.getElapsedMs());
    }

    @Test
    void testReceiptChainAppend() throws IOException {
        // Emit multiple receipts
        new BuildReceipt("generate", "GREEN", 500).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 1000).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 5000).emitTo(receiptFile);
        new BuildReceipt("validate", "WARN", 2000).emitTo(receiptFile);

        // Load chain
        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);

        assertEquals(4, chain.size());
        assertTrue(chain.containsKey("generate"));
        assertTrue(chain.containsKey("compile"));
        assertTrue(chain.containsKey("test"));
        assertTrue(chain.containsKey("validate"));

        // Verify statuses
        assertEquals("GREEN", chain.get("generate").getStatus());
        assertEquals("GREEN", chain.get("compile").getStatus());
        assertEquals("GREEN", chain.get("test").getStatus());
        assertEquals("WARN", chain.get("validate").getStatus());
    }

    @Test
    void testChainIsGreen() throws IOException {
        // All GREEN
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        assertTrue(BuildReceipt.isChainGreen(chain));
        assertTrue(BuildReceipt.isChainPass(chain));
    }

    @Test
    void testChainIsPass() throws IOException {
        // Mix of GREEN and WARN (should pass)
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "WARN", 400).emitTo(receiptFile);

        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        assertFalse(BuildReceipt.isChainGreen(chain));  // Not all GREEN
        assertTrue(BuildReceipt.isChainPass(chain));    // But all pass
    }

    @Test
    void testChainIsFail() throws IOException {
        // One FAIL (should not pass)
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "FAIL", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        assertFalse(BuildReceipt.isChainGreen(chain));
        assertFalse(BuildReceipt.isChainPass(chain));
    }

    @Test
    void testChainMissingPhase() throws IOException {
        // Missing a required phase (test)
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        assertEquals(3, chain.size());
        assertFalse(BuildReceipt.isChainGreen(chain));
        assertFalse(BuildReceipt.isChainPass(chain));
    }

    @Test
    void testTotalElapsedTime() throws IOException {
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        long total = BuildReceipt.getTotalElapsed(chain);

        assertEquals(1000, total);
    }

    @Test
    void testLoadNonExistentFile() throws IOException {
        // Should return empty chain, not throw
        Map<String, BuildReceipt> chain = BuildReceipt.loadChain(receiptFile);
        assertTrue(chain.isEmpty());
    }

    @Test
    void testBuildPhaseCreation() {
        BuildPhase bp = new BuildPhase(tempDir);
        assertNotNull(bp);
        assertTrue(bp.getFailureLog().toString().contains("ggen-build-failure.log"));
    }

    @Test
    void testBuildPhaseValidation() {
        assertThrows(IllegalArgumentException.class, () -> new BuildPhase(null));
        assertThrows(IllegalArgumentException.class, () -> new BuildPhase(Paths.get("/nonexistent/path")));
    }

    @Test
    void testBuildPhaseHasReceipts() throws IOException {
        BuildPhase bp = new BuildPhase(tempDir);
        assertFalse(bp.hasReceipts());  // Before creation

        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
        new BuildReceipt("test", "GREEN", 100).emitTo(receiptFile);

        assertTrue(bp.hasReceipts());  // After creation
    }

    @Test
    void testBuildPhaseReadyForGuards() throws IOException {
        BuildPhase bp = new BuildPhase(tempDir);

        // Not ready (no receipts)
        assertFalse(bp.isReadyForGuardsPhase());

        // Create receipts
        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        // Ready (all phases GREEN)
        assertTrue(bp.isReadyForGuardsPhase());
    }

    @Test
    void testBuildPhaseNotReadyForGuardsWithFail() throws IOException {
        BuildPhase bp = new BuildPhase(tempDir);

        // Create receipts with one FAIL
        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "FAIL", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        // Not ready (compile failed)
        assertFalse(bp.isReadyForGuardsPhase());
    }

    @Test
    void testBuildPhaseGetReceipt() throws IOException {
        BuildPhase bp = new BuildPhase(tempDir);

        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
        new BuildReceipt("compile", "GREEN", 1000).emitTo(receiptFile);

        BuildReceipt receipt = bp.getReceipt("compile");
        assertNotNull(receipt);
        assertEquals("compile", receipt.getPhase());
        assertEquals("GREEN", receipt.getStatus());
        assertEquals(1000, receipt.getElapsedMs());
    }

    @Test
    void testBuildPhaseGetReceiptChain() throws IOException {
        BuildPhase bp = new BuildPhase(tempDir);

        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "GREEN", 400).emitTo(receiptFile);

        Map<String, BuildReceipt> chain = bp.getReceiptChain();
        assertEquals(4, chain.size());
    }

    @Test
    void testBuildPhaseGetSummary() throws IOException {
        BuildPhase bp = new BuildPhase(tempDir);

        receiptFile = tempDir.resolve(".ggen/build-receipt.json");
        new BuildReceipt("generate", "GREEN", 100).emitTo(receiptFile);
        new BuildReceipt("compile", "GREEN", 200).emitTo(receiptFile);
        new BuildReceipt("test", "GREEN", 300).emitTo(receiptFile);
        new BuildReceipt("validate", "WARN", 400).emitTo(receiptFile);

        String summary = bp.getSummary();
        assertTrue(summary.contains("generate"));
        assertTrue(summary.contains("compile"));
        assertTrue(summary.contains("test"));
        assertTrue(summary.contains("validate"));
        assertTrue(summary.contains("GREEN"));
        assertTrue(summary.contains("WARN"));
    }

    @Test
    void testReceiptToString() {
        BuildReceipt receipt = new BuildReceipt("compile", "GREEN", 1500);
        String str = receipt.toString();

        assertTrue(str.contains("compile"));
        assertTrue(str.contains("GREEN"));
        assertTrue(str.contains("1500"));
    }

    @Test
    void testReceiptEmitCreatesDirectory() throws IOException {
        // Receipt file is deeply nested
        Path deepFile = tempDir.resolve("a/b/c/.ggen/build-receipt.json");
        assertFalse(Files.exists(deepFile.getParent()));

        // Emit should create all parent directories
        new BuildReceipt("test", "GREEN", 100).emitTo(deepFile);

        assertTrue(Files.exists(deepFile));
        assertTrue(Files.isRegularFile(deepFile));
    }
}
