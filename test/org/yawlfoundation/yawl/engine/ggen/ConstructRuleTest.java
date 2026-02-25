/*
 * Copyright (c) 2024 YAWL Foundation. All rights reserved.
 */
package org.yawlfoundation.yawl.engine.ggen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ConstructRule}.
 *
 * Verifies construction, validation, file-reference detection, and
 * query resolution for SPARQL CONSTRUCT rules.
 */
class ConstructRuleTest {

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

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    @Test
    void testMinimalRuleCreation() {
        ConstructRule rule = new ConstructRule("derive-handlers", SIMPLE_CONSTRUCT);

        assertEquals("derive-handlers", rule.getName());
        assertEquals("", rule.getDescription());
        assertEquals(SIMPLE_CONSTRUCT.trim(), rule.getConstruct());
        assertNull(rule.getTemplate());
        assertNull(rule.getOutputPath());
        assertNull(rule.getYawlTransition());
    }

    @Test
    void testFullRuleCreation() {
        ConstructRule rule = new ConstructRule(
            "derive-handlers",
            "Enrich graph with handler metadata",
            SIMPLE_CONSTRUCT,
            "templates/handler.tera",
            "output/{{ className }}.java",
            "enrich_task_handlers"
        );

        assertEquals("derive-handlers", rule.getName());
        assertEquals("Enrich graph with handler metadata", rule.getDescription());
        assertNotNull(rule.getConstruct());
        assertEquals("templates/handler.tera", rule.getTemplate());
        assertEquals("output/{{ className }}.java", rule.getOutputPath());
        assertEquals("enrich_task_handlers", rule.getYawlTransition());
    }

    @Test
    void testRuleNamesAreTrimmed() {
        ConstructRule rule = new ConstructRule("  my-rule  ", SIMPLE_CONSTRUCT);
        assertEquals("my-rule", rule.getName());
    }

    // -------------------------------------------------------------------------
    // Validation at construction time
    // -------------------------------------------------------------------------

    @Test
    void testNullNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule(null, SIMPLE_CONSTRUCT));
    }

    @Test
    void testEmptyNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule("", SIMPLE_CONSTRUCT));
    }

    @Test
    void testBlankNameThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule("   ", SIMPLE_CONSTRUCT));
    }

    @Test
    void testNullConstructThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule("rule", null));
    }

    @Test
    void testEmptyConstructThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule("rule", ""));
    }

    @Test
    void testSelectQueryThrows() {
        // SELECT is not a CONSTRUCT query
        String selectQuery = """
            SELECT ?task WHERE { ?task a yawl:Task }
            """;
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule("rule", selectQuery));
    }

    @Test
    void testAskQueryThrows() {
        // ASK is not a CONSTRUCT query
        assertThrows(IllegalArgumentException.class,
            () -> new ConstructRule("rule", "ASK { ?x a yawl:Task }"));
    }

    @Test
    void testConstructKeywordCaseInsensitive() {
        // Lowercase 'construct' should be accepted
        String lower = "construct { ?x yawl:y ?z } WHERE { ?x a yawl:Task }";
        assertDoesNotThrow(() -> new ConstructRule("rule", lower));
    }

    // -------------------------------------------------------------------------
    // File reference detection
    // -------------------------------------------------------------------------

    @Test
    void testInlineQueryIsNotFileReference() {
        ConstructRule rule = new ConstructRule("rule", SIMPLE_CONSTRUCT);
        assertFalse(rule.isFileReference());
    }

    @Test
    void testSparqlFilePathIsFileReference() {
        ConstructRule rule = new ConstructRule("rule",
            "queries/migrate-concurrency.sparql");
        assertTrue(rule.isFileReference());
    }

    @Test
    void testNestedSparqlFilePathIsFileReference() {
        ConstructRule rule = new ConstructRule("rule",
            "queries/subdir/derive-handlers.sparql");
        assertTrue(rule.isFileReference());
    }

    // -------------------------------------------------------------------------
    // Predicate methods
    // -------------------------------------------------------------------------

    @Test
    void testHasTemplateWhenSet() {
        ConstructRule rule = new ConstructRule(
            "rule", "", SIMPLE_CONSTRUCT, "templates/foo.tera", null, null);
        assertTrue(rule.hasTemplate());
    }

    @Test
    void testHasTemplateWhenNull() {
        ConstructRule rule = new ConstructRule("rule", SIMPLE_CONSTRUCT);
        assertFalse(rule.hasTemplate());
    }

    @Test
    void testProducesArtifactWhenBothSet() {
        ConstructRule rule = new ConstructRule(
            "rule", "", SIMPLE_CONSTRUCT,
            "templates/foo.tera", "output/{{ name }}.java", null);
        assertTrue(rule.producesArtifact());
    }

    @Test
    void testProducesArtifactFalseWhenOutputPathMissing() {
        ConstructRule rule = new ConstructRule(
            "rule", "", SIMPLE_CONSTRUCT, "templates/foo.tera", null, null);
        assertFalse(rule.producesArtifact());
    }

    @Test
    void testProducesArtifactFalseWhenTemplateMissing() {
        ConstructRule rule = new ConstructRule(
            "rule", "", SIMPLE_CONSTRUCT, null, "output/foo.java", null);
        assertFalse(rule.producesArtifact());
    }

    @Test
    void testHasYawlTransitionWhenSet() {
        ConstructRule rule = new ConstructRule(
            "rule", "", SIMPLE_CONSTRUCT, null, null, "enrich_task_handlers");
        assertTrue(rule.hasYawlTransition());
        assertEquals("enrich_task_handlers", rule.getYawlTransition());
    }

    @Test
    void testHasYawlTransitionWhenNull() {
        ConstructRule rule = new ConstructRule("rule", SIMPLE_CONSTRUCT);
        assertFalse(rule.hasYawlTransition());
        assertNull(rule.getYawlTransition());
    }

    // -------------------------------------------------------------------------
    // Syntax validation
    // -------------------------------------------------------------------------

    @Test
    void testValidInlineQueryHasNoErrors() {
        ConstructRule rule = new ConstructRule("rule", SIMPLE_CONSTRUCT);
        List<String> errors = rule.validateSyntax("test");
        assertTrue(errors.isEmpty(), "Valid query should have no errors: " + errors);
    }

    @Test
    void testQueryMissingWhereHasError() {
        // Valid for construction (has CONSTRUCT) but missing WHERE
        ConstructRule rule;
        try {
            rule = new ConstructRule("rule",
                "CONSTRUCT { ?x yawl:y ?z . }");
        } catch (IllegalArgumentException e) {
            // If construction rejects it too, test passes
            return;
        }
        List<String> errors = rule.validateSyntax("test");
        assertFalse(errors.isEmpty(), "Missing WHERE should produce validation error");
        assertTrue(errors.get(0).contains("WHERE"));
    }

    @Test
    void testFileReferenceSkipsSyntaxValidation() {
        // File references are validated only at resolve time
        ConstructRule rule = new ConstructRule("rule", "queries/foo.sparql");
        List<String> errors = rule.validateSyntax("test");
        assertTrue(errors.isEmpty(),
            "File references should not have syntax errors before resolution");
    }

    @Test
    void testQueryWithUnbalancedBracesHasError() {
        // One too many opening braces
        String unbalanced = "CONSTRUCT { ?x yawl:y ?z . { WHERE { ?x a yawl:Task }";
        ConstructRule rule;
        try {
            rule = new ConstructRule("rule", unbalanced);
        } catch (IllegalArgumentException e) {
            // Construction may also reject this — test passes either way
            return;
        }
        List<String> errors = rule.validateSyntax("test");
        assertFalse(errors.isEmpty(),
            "Unbalanced braces should produce a validation error");
    }

    // -------------------------------------------------------------------------
    // Query resolution
    // -------------------------------------------------------------------------

    @Test
    void testInlineQueryResolvesToItself(@TempDir Path tempDir) throws IOException {
        ConstructRule rule = new ConstructRule("rule", SIMPLE_CONSTRUCT);
        String resolved = rule.resolveQuery(tempDir);
        assertEquals(SIMPLE_CONSTRUCT.trim(), resolved.trim());
    }

    @Test
    void testFileReferenceResolvesToFileContent(@TempDir Path tempDir) throws IOException {
        Path queryFile = tempDir.resolve("queries/handler.sparql");
        Files.createDirectories(queryFile.getParent());
        Files.writeString(queryFile, SIMPLE_CONSTRUCT);

        ConstructRule rule = new ConstructRule("rule", "queries/handler.sparql");
        String resolved = rule.resolveQuery(tempDir);
        assertEquals(SIMPLE_CONSTRUCT, resolved);
    }

    @Test
    void testFileReferenceThrowsWhenFileMissing(@TempDir Path tempDir) {
        ConstructRule rule = new ConstructRule("rule", "queries/missing.sparql");
        assertThrows(IOException.class, () -> rule.resolveQuery(tempDir));
    }

    // -------------------------------------------------------------------------
    // Equality and identity
    // -------------------------------------------------------------------------

    @Test
    void testEqualityByName() {
        ConstructRule r1 = new ConstructRule("rule-a", SIMPLE_CONSTRUCT);
        ConstructRule r2 = new ConstructRule("rule-a",
            "CONSTRUCT { ?x yawl:y ?z } WHERE { ?x a yawl:Task }");
        assertEquals(r1, r2, "Rules with same name should be equal");
    }

    @Test
    void testInequalityByName() {
        ConstructRule r1 = new ConstructRule("rule-a", SIMPLE_CONSTRUCT);
        ConstructRule r2 = new ConstructRule("rule-b", SIMPLE_CONSTRUCT);
        assertNotEquals(r1, r2);
    }

    @Test
    void testHashCodeConsistentWithEquals() {
        ConstructRule r1 = new ConstructRule("rule-a", SIMPLE_CONSTRUCT);
        ConstructRule r2 = new ConstructRule("rule-a", SIMPLE_CONSTRUCT);
        assertEquals(r1.hashCode(), r2.hashCode());
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    void testToStringContainsName() {
        ConstructRule rule = new ConstructRule("derive-handlers", SIMPLE_CONSTRUCT);
        assertTrue(rule.toString().contains("derive-handlers"));
    }

    @Test
    void testToStringIndicatesFileRef() {
        ConstructRule rule = new ConstructRule("rule", "queries/foo.sparql");
        String str = rule.toString();
        assertTrue(str.contains("fileRef=true"));
    }

    @Test
    void testToStringIndicatesYawlTransition() {
        ConstructRule rule = new ConstructRule(
            "rule", "", SIMPLE_CONSTRUCT, null, null, "some_transition");
        assertTrue(rule.toString().contains("some_transition"));
    }
}
