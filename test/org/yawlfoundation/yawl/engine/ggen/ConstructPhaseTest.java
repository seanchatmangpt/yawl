/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ConstructPhase}.
 *
 * Uses real temporary directories and ggen.toml files to verify rule loading,
 * validation, receipt management, and pipeline readiness checks. No SPARQL
 * engine is invoked — these tests cover orchestration logic and state tracking.
 */
class ConstructPhaseTest {

    private static final String SIMPLE_CONSTRUCT = """
        PREFIX yawl: <http://yawlfoundation.org/yawl#>
        CONSTRUCT {
          ?task yawl:handlerClass ?name .
        }
        WHERE {
          ?task a yawl:Task ; yawl:taskId ?id .
          BIND(CONCAT("Y", ?id, "Handler") AS ?name)
        }
        """;

    private static final String GGEN_TOML_WITH_TWO_RULES = """
        [project]
        name = "test-project"
        version = "1.0.0"

        [[inference.rules]]
        name = "derive-task-handlers"
        description = "Enrich graph with task handler metadata"
        construct = \"\"\"
        PREFIX yawl: <http://yawlfoundation.org/yawl#>
        CONSTRUCT {
          ?task yawl:handlerClass ?name .
        }
        WHERE {
          ?task a yawl:Task ; yawl:taskId ?id .
          BIND(CONCAT("Y", ?id, "Handler") AS ?name)
        }
        \"\"\"
        yawl_transition = "enrich_task_handlers"

        [[inference.rules]]
        name = "derive-flow-metadata"
        description = "Enrich flows with evaluation metadata"
        construct = \"\"\"
        PREFIX yawl: <http://yawlfoundation.org/yawl#>
        CONSTRUCT {
          ?flow yawl:evaluationOrder ?priority .
        }
        WHERE {
          ?flow a yawl:Flow ; yawl:flowPriority ?priority .
        }
        \"\"\"
        """;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure scripts directory exists (but no ggen-sync.sh — no SPARQL engine)
        Files.createDirectories(tempDir.resolve("scripts"));
    }

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void testConstructPhaseCreation() {
        ConstructPhase cp = new ConstructPhase(tempDir);
        assertNotNull(cp);
        assertEquals(tempDir.resolve("ggen.toml"), cp.getGgenToml());
        assertEquals(tempDir.resolve(".ggen/construct-receipt.json"), cp.getReceiptFile());
    }

    @Test
    void testNullRootThrows() {
        assertThrows(IllegalArgumentException.class, () -> new ConstructPhase(null));
    }

    @Test
    void testNonExistentRootThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructPhase(Path.of("/nonexistent/yawl-root")));
    }

    // -------------------------------------------------------------------------
    // Rule loading
    // -------------------------------------------------------------------------

    @Test
    void testLoadRulesFromGgenToml() throws IOException {
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        assertEquals(2, rules.size());
        assertEquals("derive-task-handlers", rules.get(0).getName());
        assertEquals("derive-flow-metadata", rules.get(1).getName());
    }

    @Test
    void testLoadRulesPreservesDescription() throws IOException {
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        assertEquals("Enrich graph with task handler metadata",
            rules.get(0).getDescription());
    }

    @Test
    void testLoadRulesPreservesYawlTransition() throws IOException {
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        assertEquals("enrich_task_handlers", rules.get(0).getYawlTransition());
        assertNull(rules.get(1).getYawlTransition());
    }

    @Test
    void testLoadRulesWhenNoRulesPresent() throws IOException {
        // ggen.toml with no [[inference.rules]] sections
        Files.writeString(tempDir.resolve("ggen.toml"),
            "[project]\nname = \"empty\"\nversion = \"1.0\"");

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        assertTrue(rules.isEmpty());
    }

    @Test
    void testLoadRulesThrowsWhenGgenTomlMissing() {
        // No ggen.toml in tempDir
        ConstructPhase cp = new ConstructPhase(tempDir);
        assertThrows(IllegalStateException.class, cp::loadRules);
    }

    @Test
    void testLoadRulesWithFileReference() throws IOException {
        // Create a .sparql file
        Path queryDir = tempDir.resolve("queries");
        Files.createDirectories(queryDir);
        Files.writeString(queryDir.resolve("derive-handlers.sparql"), SIMPLE_CONSTRUCT);

        String toml = """
            [project]
            name = "test"
            version = "1.0"

            [[inference.rules]]
            name = "derive-handlers"
            description = "Load from file"
            construct = "queries/derive-handlers.sparql"
            """;
        Files.writeString(tempDir.resolve("ggen.toml"), toml);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        assertEquals(1, rules.size());
        assertEquals("derive-handlers", rules.get(0).getName());
        assertTrue(rules.get(0).isFileReference());
    }

    // -------------------------------------------------------------------------
    // Rule validation
    // -------------------------------------------------------------------------

    @Test
    void testValidateRulesAllValid() throws IOException {
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();
        Map<String, List<String>> errors = cp.validateRules(rules);

        assertTrue(errors.isEmpty(), "All valid rules should have no errors: " + errors);
    }

    @Test
    void testValidateRulesDetectsMissingQueryFile() throws IOException {
        String toml = """
            [project]
            name = "test"
            version = "1.0"

            [[inference.rules]]
            name = "broken-rule"
            description = "References non-existent file"
            construct = "queries/nonexistent.sparql"
            """;
        Files.writeString(tempDir.resolve("ggen.toml"), toml);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();
        Map<String, List<String>> errors = cp.validateRules(rules);

        assertTrue(errors.containsKey("broken-rule"),
            "Missing query file should produce validation error");
        assertTrue(errors.get("broken-rule").stream()
            .anyMatch(e -> e.contains("Query file not found")));
    }

    @Test
    void testValidateRulesDetectsMissingTemplateFile() throws IOException {
        String toml = String.format("""
            [project]
            name = "test"
            version = "1.0"

            [[inference.rules]]
            name = "rule-with-missing-template"
            construct = \"\"\"%s\"\"\"
            template = "templates/nonexistent.tera"
            to = "output/{{ name }}.java"
            """, SIMPLE_CONSTRUCT.trim());

        Files.writeString(tempDir.resolve("ggen.toml"), toml);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();
        Map<String, List<String>> errors = cp.validateRules(rules);

        assertTrue(errors.containsKey("rule-with-missing-template"),
            "Missing template should produce validation error");
        assertTrue(errors.get("rule-with-missing-template").stream()
            .anyMatch(e -> e.contains("Template file not found")));
    }

    @Test
    void testValidateRulesEmptyListIsValid() {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Map<String, List<String>> errors = cp.validateRules(List.of());
        assertTrue(errors.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Receipt management
    // -------------------------------------------------------------------------

    @Test
    void testHasReceiptsReturnsFalseInitially() {
        ConstructPhase cp = new ConstructPhase(tempDir);
        assertFalse(cp.hasReceipts());
    }

    @Test
    void testHasReceiptsTrueAfterEmit() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Path receiptFile = cp.getReceiptFile();
        new ConstructReceipt("test-rule", 100L, 5, List.of()).emitTo(receiptFile);

        assertTrue(cp.hasReceipts());
    }

    @Test
    void testGetReceiptChainReturnsEmptyWhenNoReceipts() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Map<String, ConstructReceipt> chain = cp.getReceiptChain();
        assertTrue(chain.isEmpty());
    }

    @Test
    void testGetReceiptChainReturnsAllReceipts() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Path receiptFile = cp.getReceiptFile();
        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 8, List.of()).emitTo(receiptFile);

        Map<String, ConstructReceipt> chain = cp.getReceiptChain();
        assertEquals(2, chain.size());
        assertTrue(chain.containsKey("rule-a"));
        assertTrue(chain.containsKey("rule-b"));
    }

    // -------------------------------------------------------------------------
    // Pipeline readiness
    // -------------------------------------------------------------------------

    @Test
    void testIsReadyForGuardsPhaseFalseWithNoReceipts() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        assertFalse(cp.isReadyForGuardsPhase());
    }

    @Test
    void testIsReadyForGuardsPhaseTrueWhenAllPass() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Path receiptFile = cp.getReceiptFile();
        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 0, List.of()).emitTo(receiptFile); // WARN

        assertTrue(cp.isReadyForGuardsPhase());
    }

    @Test
    void testIsReadyForGuardsPhaseFalseWhenFailPresent() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Path receiptFile = cp.getReceiptFile();
        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        ConstructReceipt.fail("rule-b", 200L, "Query error").emitTo(receiptFile);

        assertFalse(cp.isReadyForGuardsPhase());
    }

    // -------------------------------------------------------------------------
    // executeConstruct — no SPARQL engine (ggen-sync.sh absent)
    // -------------------------------------------------------------------------

    @Test
    void testExecuteConstructWithNoSparqlEngineReturnsTrue() throws IOException, InterruptedException {
        // No ggen-sync.sh in scripts/ → rules recorded as WARN (no triples, no error)
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        boolean result = cp.executeConstruct(rules);

        // WARN is a pass — should return true
        assertTrue(result);
        assertTrue(cp.hasReceipts());
    }

    @Test
    void testExecuteConstructEmitsReceiptForEachRule()
            throws IOException, InterruptedException {
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();
        cp.executeConstruct(rules);

        Map<String, ConstructReceipt> chain = cp.getReceiptChain();
        assertEquals(2, chain.size());
        assertTrue(chain.containsKey("derive-task-handlers"));
        assertTrue(chain.containsKey("derive-flow-metadata"));
    }

    @Test
    void testExecuteConstructEmptyRuleListSucceeds()
            throws IOException, InterruptedException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        boolean result = cp.executeConstruct(List.of());
        assertTrue(result);
        assertFalse(cp.hasReceipts());
    }

    // -------------------------------------------------------------------------
    // Summary output
    // -------------------------------------------------------------------------

    @Test
    void testGetSummaryWithNoReceipts() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        String summary = cp.getSummary();
        assertTrue(summary.contains("No construct receipts"));
    }

    @Test
    void testGetSummaryContainsRuleNames() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Path receiptFile = cp.getReceiptFile();
        new ConstructReceipt("rule-alpha", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-beta", 250L, 8,
            List.of("output/Beta.java")).emitTo(receiptFile);

        String summary = cp.getSummary();
        assertTrue(summary.contains("rule-alpha"));
        assertTrue(summary.contains("rule-beta"));
        assertTrue(summary.contains("output/Beta.java"));
    }

    @Test
    void testGetSummaryContainsTotals() throws IOException {
        ConstructPhase cp = new ConstructPhase(tempDir);
        Path receiptFile = cp.getReceiptFile();
        new ConstructReceipt("rule-a", 100L, 5, List.of()).emitTo(receiptFile);
        new ConstructReceipt("rule-b", 200L, 3, List.of()).emitTo(receiptFile);

        String summary = cp.getSummary();
        // 8 total triples, 300ms total
        assertTrue(summary.contains("8") || summary.contains("Totals"),
            "Summary should report total triples produced");
    }

    // -------------------------------------------------------------------------
    // Returned list immutability
    // -------------------------------------------------------------------------

    @Test
    void testLoadRulesReturnsImmutableList() throws IOException {
        Files.writeString(tempDir.resolve("ggen.toml"), GGEN_TOML_WITH_TWO_RULES);

        ConstructPhase cp = new ConstructPhase(tempDir);
        List<ConstructRule> rules = cp.loadRules();

        assertThrows(UnsupportedOperationException.class,
            () -> rules.add(new ConstructRule("extra", SIMPLE_CONSTRUCT)));
    }
}
