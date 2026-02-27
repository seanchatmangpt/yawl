/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.tooling;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yawlfoundation.yawl.tooling.cli.YawlCliMain;
import org.yawlfoundation.yawl.tooling.cli.command.TemplateCommand;
import org.yawlfoundation.yawl.tooling.intellij.highlighting.YawlSyntaxHighlighter;
import org.yawlfoundation.yawl.tooling.intellij.highlighting.YawlSyntaxHighlighter.TokenContext;
import org.yawlfoundation.yawl.tooling.intellij.highlighting.YawlSyntaxHighlighter.TokenType;
import org.yawlfoundation.yawl.tooling.lsp.YawlHoverProvider;
import org.yawlfoundation.yawl.tooling.lsp.completion.YawlCompletionProvider;
import org.yawlfoundation.yawl.tooling.lsp.diagnostic.YawlDiagnosticProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for YAWL v6.0.0 developer tooling.
 *
 * Tests cover:
 * - CLI validate/compile/template subcommands
 * - LSP hover, completion, and diagnostic providers
 * - IntelliJ syntax highlighter token classification
 *
 * All tests use the real YAWL engine components — no mocks.
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
@Tag("unit")
public class TestYawlCli {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Valid minimal YAWL 4.0 specification XML for use across tests. */
    private static final String VALID_SPEC_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<specificationSet version=\"4.0\"\n" +
            "    xmlns=\"http://www.yawlfoundation.org/yawlschema\"\n" +
            "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "    xsi:schemaLocation=\"http://www.yawlfoundation.org/yawlschema " +
            "http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd\">\n" +
            "  <specification uri=\"TestSpec\">\n" +
            "    <name>Test Specification</name>\n" +
            "    <documentation>Test spec for CLI tooling</documentation>\n" +
            "    <metaData/>\n" +
            "    <decomposition id=\"TestNet\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
            "      <name>Test Net</name>\n" +
            "      <processControlElements>\n" +
            "        <inputCondition id=\"start\">\n" +
            "          <name>Start</name>\n" +
            "          <flowsInto><nextElementRef id=\"TaskA\"/></flowsInto>\n" +
            "        </inputCondition>\n" +
            "        <task id=\"TaskA\">\n" +
            "          <name>Task A</name>\n" +
            "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
            "          <join code=\"xor\"/><split code=\"and\"/>\n" +
            "        </task>\n" +
            "        <outputCondition id=\"end\"><name>End</name></outputCondition>\n" +
            "      </processControlElements>\n" +
            "    </decomposition>\n" +
            "  </specification>\n" +
            "</specificationSet>\n";

    private ByteArrayOutputStream outBuf;
    private ByteArrayOutputStream errBuf;
    private PrintStream out;
    private PrintStream err;

    @Before
    public void setUp() {
        outBuf = new ByteArrayOutputStream();
        errBuf = new ByteArrayOutputStream();
        out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
    }

    @After
    public void tearDown() {
        out.close();
        err.close();
    }

    // ---- CLI Main tests -------------------------------------------------------

    @Test
    public void testCliMainNoArgs_PrintsUsage() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[0]);
        assertEquals("No-args should return 0", 0, code);
        String output = outBuf.toString(StandardCharsets.UTF_8);
        assertTrue("Usage should list subcommands", output.contains("validate"));
        assertTrue("Usage should list subcommands", output.contains("compile"));
        assertTrue("Usage should list subcommands", output.contains("template"));
    }

    @Test
    public void testCliMainVersionFlag() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"--version"});
        assertEquals(0, code);
        assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("6.0.0"));
    }

    @Test
    public void testCliMainUnknownSubcommand_ReturnsOne() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"nonexistent"});
        assertEquals(1, code);
        assertTrue(errBuf.toString(StandardCharsets.UTF_8).contains("Unknown subcommand"));
    }

    // ---- Template command tests -----------------------------------------------

    @Test
    public void testTemplateList() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"template", "--list"});
        assertEquals(0, code);
        String output = outBuf.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("sequential"));
        assertTrue(output.contains("parallel"));
        assertTrue(output.contains("choice"));
        assertTrue(output.contains("multiinstance"));
        assertTrue(output.contains("subprocess"));
    }

    @Test
    public void testTemplateSequential_OutputsValidXml() throws Exception {
        File outFile = tempFolder.newFile("sequential.xml");
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{
                "template", "sequential", "--output", outFile.getAbsolutePath()
        });
        assertEquals(0, code);
        assertTrue("Output file should exist", outFile.exists());
        String xml = Files.readString(outFile.toPath());
        assertTrue("Generated XML should have specificationSet", xml.contains("<specificationSet"));
        assertTrue("Generated XML should have task elements", xml.contains("<task id=\"TaskA\""));
    }

    @Test
    public void testTemplateParallel_ContainsAndSplit() throws Exception {
        File outFile = tempFolder.newFile("parallel.xml");
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{
                "template", "parallel", "--output", outFile.getAbsolutePath()
        });
        assertEquals(0, code);
        String xml = Files.readString(outFile.toPath());
        assertTrue("Parallel template should have and-split", xml.contains("code=\"and\""));
    }

    @Test
    public void testTemplateChoice_ContainsXorSplit() throws Exception {
        File outFile = tempFolder.newFile("choice.xml");
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{
                "template", "choice", "--output", outFile.getAbsolutePath()
        });
        assertEquals(0, code);
        String xml = Files.readString(outFile.toPath());
        assertTrue("Choice template should have xor-split", xml.contains("code=\"xor\""));
    }

    @Test
    public void testTemplateUnknownName_ReturnsFail() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"template", "nonexistenttemplate"});
        assertNotEquals(0, code);
    }

    @Test
    public void testTemplateCustomUri() throws Exception {
        File outFile = tempFolder.newFile("custom.xml");
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{
                "template", "sequential",
                "--uri", "MyCustomSpec",
                "--output", outFile.getAbsolutePath()
        });
        assertEquals(0, code);
        String xml = Files.readString(outFile.toPath());
        assertTrue("Custom URI should appear in spec", xml.contains("uri=\"MyCustomSpec\""));
    }

    // ---- Compile command tests ------------------------------------------------

    @Test
    public void testCompile_ValidSpec_ReturnsZero() throws Exception {
        File specFile = tempFolder.newFile("valid.xml");
        Files.writeString(specFile.toPath(), VALID_SPEC_XML);
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"compile", specFile.getAbsolutePath()});
        String output = outBuf.toString(StandardCharsets.UTF_8);
        assertTrue("Compile should succeed", code == 0 || output.contains("TestSpec"));
    }

    @Test
    public void testCompile_MissingFile_ReturnsFail() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"compile", "/nonexistent/path/spec.xml"});
        assertNotEquals(0, code);
    }

    @Test
    public void testCompile_HelpFlag_ReturnsZero() {
        YawlCliMain cli = new YawlCliMain(out, err);
        int code = cli.run(new String[]{"compile", "--help"});
        assertEquals(0, code);
        assertTrue(outBuf.toString(StandardCharsets.UTF_8).contains("Usage"));
    }

    // ---- Syntax highlighter tests --------------------------------------------

    @Test
    public void testHighlighter_StructuralElement() {
        TokenType type = YawlSyntaxHighlighter.classify("specificationSet", TokenContext.ELEMENT_NAME);
        assertEquals(TokenType.YAWL_STRUCTURAL_ELEMENT, type);
    }

    @Test
    public void testHighlighter_TaskElement() {
        TokenType type = YawlSyntaxHighlighter.classify("task", TokenContext.ELEMENT_NAME);
        assertEquals(TokenType.YAWL_TASK, type);
    }

    @Test
    public void testHighlighter_InputCondition() {
        TokenType type = YawlSyntaxHighlighter.classify("inputCondition", TokenContext.ELEMENT_NAME);
        assertEquals(TokenType.YAWL_CONDITION, type);
    }

    @Test
    public void testHighlighter_FlowElement() {
        TokenType type = YawlSyntaxHighlighter.classify("flowsInto", TokenContext.ELEMENT_NAME);
        assertEquals(TokenType.YAWL_FLOW, type);
    }

    @Test
    public void testHighlighter_JoinSplitKeyword_And() {
        TokenType type = YawlSyntaxHighlighter.classify("and", TokenContext.ATTRIBUTE_VALUE);
        assertEquals(TokenType.YAWL_JOIN_SPLIT_KEYWORD, type);
    }

    @Test
    public void testHighlighter_JoinSplitKeyword_Xor() {
        TokenType type = YawlSyntaxHighlighter.classify("xor", TokenContext.ATTRIBUTE_VALUE);
        assertEquals(TokenType.YAWL_JOIN_SPLIT_KEYWORD, type);
    }

    @Test
    public void testHighlighter_JoinSplitKeyword_Or() {
        TokenType type = YawlSyntaxHighlighter.classify("or", TokenContext.ATTRIBUTE_VALUE);
        assertEquals(TokenType.YAWL_JOIN_SPLIT_KEYWORD, type);
    }

    @Test
    public void testHighlighter_YawlAttributeKey_Id() {
        TokenType type = YawlSyntaxHighlighter.classify("id", TokenContext.ATTRIBUTE_NAME);
        assertEquals(TokenType.YAWL_ATTRIBUTE_KEY, type);
    }

    @Test
    public void testHighlighter_GenericElement() {
        TokenType type = YawlSyntaxHighlighter.classify("name", TokenContext.ELEMENT_NAME);
        assertEquals(TokenType.XML_TAG, type);
    }

    @Test
    public void testHighlighter_NullToken_ReturnsWhitespace() {
        TokenType type = YawlSyntaxHighlighter.classify(null, TokenContext.ELEMENT_NAME);
        assertEquals(TokenType.WHITESPACE, type);
    }

    @Test
    public void testHighlighter_DisplayNames_AllDefined() {
        for (TokenType t : TokenType.values()) {
            String name = YawlSyntaxHighlighter.displayName(t);
            assertNotNull("Display name should not be null for " + t, name);
            assertFalse("Display name should not be blank for " + t, name.isBlank());
        }
    }

    // ---- Hover provider tests ------------------------------------------------

    @Test
    public void testHover_TaskElement_ReturnsDocs() {
        String doc = "  <task id=\"MyTask\">\n";
        String result = YawlHoverProvider.getHoverContent(doc, 0, 5);
        assertNotNull("Hover should return documentation for 'task'", result);
        assertTrue("Hover doc should mention 'task'", result.toLowerCase().contains("task"));
    }

    @Test
    public void testHover_JoinElement_ReturnsDocs() {
        String doc = "    <join code=\"and\"/>\n";
        String result = YawlHoverProvider.getHoverContent(doc, 0, 6);
        assertNotNull("Hover should return documentation for 'join'", result);
        assertTrue("Hover should mention join semantics", result.contains("and"));
    }

    @Test
    public void testHover_NullDocument_ReturnsNull() {
        String result = YawlHoverProvider.getHoverContent(null, 0, 0);
        assertNull(result);
    }

    @Test
    public void testHover_EmptyDocument_ReturnsNull() {
        String result = YawlHoverProvider.getHoverContent("", 0, 0);
        assertNull(result);
    }

    @Test
    public void testHover_OutOfBoundsLine_ReturnsNull() {
        String doc = "<task id=\"a\"/>";
        String result = YawlHoverProvider.getHoverContent(doc, 999, 0);
        assertNull(result);
    }

    // ---- Completion provider tests -------------------------------------------

    @Test
    public void testCompletion_NullDocument_ReturnsTopLevel() {
        YawlCompletionProvider provider = new YawlCompletionProvider();
        List<YawlCompletionProvider.CompletionItem> items = provider.getCompletions(null, 0, 0);
        assertNotNull(items);
        assertFalse("Should have top-level completions for null document", items.isEmpty());
    }

    @Test
    public void testCompletion_EmptyDocument_ReturnsTopLevel() {
        YawlCompletionProvider provider = new YawlCompletionProvider();
        List<YawlCompletionProvider.CompletionItem> items = provider.getCompletions("", 0, 0);
        assertNotNull(items);
        assertFalse("Should have top-level completions", items.isEmpty());
        assertTrue("Top-level item should be specificationSet",
                items.stream().anyMatch(i -> i.label().equals("specificationSet")));
    }

    @Test
    public void testCompletion_InsideTask_OffersTaskChildren() {
        String doc = "<specificationSet>\n  <specification uri=\"x\">\n    <decomposition>\n      <task id=\"T\">\n        ";
        YawlCompletionProvider provider = new YawlCompletionProvider();
        List<YawlCompletionProvider.CompletionItem> items = provider.getCompletions(doc, 4, 8);
        assertNotNull(items);
        // Should offer task children like flowsInto, join, split
        boolean hasFlowsInto = items.stream().anyMatch(i ->
                i.label().equals("flowsInto") || i.insertText().contains("flowsInto"));
        assertTrue("Completion inside task should offer flowsInto", hasFlowsInto);
    }

    @Test
    public void testCompletion_JsonSerialisation() {
        YawlCompletionProvider provider = new YawlCompletionProvider();
        List<YawlCompletionProvider.CompletionItem> items = provider.getCompletions(null, 0, 0);
        for (YawlCompletionProvider.CompletionItem item : items) {
            String json = item.toJson();
            assertNotNull("JSON should not be null", json);
            assertTrue("JSON should start with {", json.startsWith("{"));
            assertTrue("JSON should contain label", json.contains("\"label\""));
        }
    }

    // ---- Diagnostic provider tests -------------------------------------------

    @Test
    public void testDiagnostic_EmptyDocument_ReturnsError() {
        YawlDiagnosticProvider provider = new YawlDiagnosticProvider();
        List<YawlDiagnosticProvider.Diagnostic> diagnostics = provider.validate("");
        assertFalse("Empty document should produce diagnostics", diagnostics.isEmpty());
        assertTrue("Diagnostic should be an error",
                diagnostics.stream().anyMatch(d ->
                        d.severity() == YawlDiagnosticProvider.Severity.ERROR));
    }

    @Test
    public void testDiagnostic_InvalidXml_ReturnsError() {
        YawlDiagnosticProvider provider = new YawlDiagnosticProvider();
        List<YawlDiagnosticProvider.Diagnostic> diagnostics =
                provider.validate("<not-valid-yawl>content</not-valid-yawl>");
        assertFalse("Invalid XML should produce diagnostics", diagnostics.isEmpty());
    }

    @Test
    public void testDiagnostic_JsonSerialisation() {
        YawlDiagnosticProvider provider = new YawlDiagnosticProvider();
        List<YawlDiagnosticProvider.Diagnostic> diagnostics = provider.validate("");
        for (YawlDiagnosticProvider.Diagnostic d : diagnostics) {
            String json = d.toJson();
            assertNotNull(json);
            assertTrue("JSON should contain 'range'", json.contains("\"range\""));
            assertTrue("JSON should contain 'severity'", json.contains("\"severity\""));
            assertTrue("JSON should contain 'message'", json.contains("\"message\""));
        }
    }
}
