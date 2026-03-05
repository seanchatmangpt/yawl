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

package org.yawlfoundation.yawl.tooling.lsp.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * XSD-aware completion provider for YAWL specification XML files.
 *
 * Given the document text and cursor position, determines the XML context
 * (element or attribute) and returns a list of LSP {@link CompletionItem}s
 * drawn from the YAWL_Schema4.0.xsd type model.
 *
 * Context detection rules:
 * <ol>
 *   <li>If the cursor is inside an opening tag ({@code <...}), offer child element names
 *       valid at that nesting level.</li>
 *   <li>If the cursor is inside an attribute value ({@code ="...}), offer enumeration values
 *       for known attributes (e.g. {@code code="and|xor|or"}).</li>
 *   <li>If the cursor is at an attribute name position, offer valid attribute names
 *       for the enclosing element.</li>
 * </ol>
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public class YawlCompletionProvider {

    /**
     * LSP CompletionItemKind values (subset used by YAWL LSP).
     * Values match the LSP specification numbering.
     */
    public enum ItemKind {
        TEXT(1), KEYWORD(14), SNIPPET(15), PROPERTY(10), ENUM_MEMBER(20), MODULE(9);

        private final int lspCode;
        ItemKind(int lspCode) { this.lspCode = lspCode; }
        public int lspCode() { return lspCode; }
    }

    // ---- Child element maps keyed by parent element name ---------------------

    /** Root-level elements allowed directly inside specificationSet */
    private static final List<String> ROOT_CHILDREN = List.of("specification");

    /** Elements allowed inside <specification> */
    private static final List<String> SPEC_CHILDREN = List.of(
            "name", "documentation", "metaData", "schema", "decomposition");

    /** Elements allowed inside <decomposition> (for nets) */
    private static final List<String> DECOMP_CHILDREN = List.of(
            "name", "documentation", "inputParam", "outputParam",
            "processControlElements");

    /** Elements allowed inside <processControlElements> */
    private static final List<String> PCE_CHILDREN = List.of(
            "inputCondition", "outputCondition", "condition", "task");

    /** Elements allowed inside <task> */
    private static final List<String> TASK_CHILDREN = List.of(
            "name", "documentation", "flowsInto", "join", "split",
            "multiInstance", "decomposesTo", "resourcing", "timer");

    /** Elements allowed inside <flowsInto> */
    private static final List<String> FLOWS_INTO_CHILDREN = List.of(
            "nextElementRef", "predicate", "isDefaultFlow");

    /** Elements allowed inside <multiInstance> */
    private static final List<String> MI_CHILDREN = List.of(
            "minimum", "maximum", "threshold", "creationMode",
            "inputParam", "outputParam");

    /** Elements allowed inside <metaData> */
    private static final List<String> METADATA_CHILDREN = List.of(
            "creator", "description", "version", "validFrom", "validUntil", "identifier");

    /** Context map: parent element name -> allowed child element names */
    private static final Map<String, List<String>> CHILD_MAP = Map.ofEntries(
            Map.entry("specificationset",      ROOT_CHILDREN),
            Map.entry("specification",         SPEC_CHILDREN),
            Map.entry("decomposition",         DECOMP_CHILDREN),
            Map.entry("processcontrolelements",PCE_CHILDREN),
            Map.entry("task",                  TASK_CHILDREN),
            Map.entry("inputcondition",        List.of("name", "documentation", "flowsInto")),
            Map.entry("outputcondition",       List.of("name", "documentation")),
            Map.entry("condition",             List.of("name", "documentation", "flowsInto")),
            Map.entry("flowsinto",             FLOWS_INTO_CHILDREN),
            Map.entry("multiinstance",         MI_CHILDREN),
            Map.entry("metadata",              METADATA_CHILDREN)
    );

    // ---- Attribute value enumerations ----------------------------------------

    /** Allowed values for code attribute on join and split elements */
    private static final List<String> JOIN_SPLIT_CODES = List.of("and", "xor", "or");

    /** Allowed values for creationMode code attribute */
    private static final List<String> CREATION_MODES = List.of("static", "dynamic");

    /** Allowed values for xsi:type on decomposition */
    private static final List<String> DECOMP_TYPES = List.of(
            "NetFactsType", "WebServiceGatewayFactsType");

    /**
     * Attribute-to-enum-values map. Key format: {@code elementname@attributename}.
     */
    private static final Map<String, List<String>> ATTRIBUTE_ENUMS = Map.of(
            "join@code",         JOIN_SPLIT_CODES,
            "split@code",        JOIN_SPLIT_CODES,
            "creationmode@code", CREATION_MODES,
            "decomposition@xsi:type", DECOMP_TYPES
    );

    // ---- Snippet templates ---------------------------------------------------

    /** Snippet templates for complex elements that benefit from scaffolding */
    private static final Map<String, String> SNIPPETS = Map.ofEntries(
            Map.entry("task",
                    "<task id=\"${1:taskId}\">\n" +
                    "  <name>${2:Task Name}</name>\n" +
                    "  <flowsInto><nextElementRef id=\"${3:nextId}\"/></flowsInto>\n" +
                    "  <join code=\"${4|xor,and,or|}\"/>\n" +
                    "  <split code=\"${5|and,xor,or|}\"/>\n" +
                    "</task>"),

            Map.entry("decomposition",
                    "<decomposition id=\"${1:NetId}\" xsi:type=\"${2|NetFactsType,WebServiceGatewayFactsType|}\"" +
                    " isRootNet=\"${3|true,false|}\">\n" +
                    "  <name>${4:Net Name}</name>\n" +
                    "  <processControlElements>\n" +
                    "    <inputCondition id=\"start\">\n" +
                    "      <name>Start</name>\n" +
                    "      <flowsInto><nextElementRef id=\"${5:firstTask}\"/></flowsInto>\n" +
                    "    </inputCondition>\n" +
                    "    ${0}\n" +
                    "    <outputCondition id=\"end\"><name>End</name></outputCondition>\n" +
                    "  </processControlElements>\n" +
                    "</decomposition>"),

            Map.entry("multiInstance",
                    "<multiInstance>\n" +
                    "  <minimum>${1:1}</minimum>\n" +
                    "  <maximum>${2:5}</maximum>\n" +
                    "  <threshold>${3:3}</threshold>\n" +
                    "  <creationMode code=\"${4|static,dynamic|}\"/>\n" +
                    "</multiInstance>"),

            Map.entry("flowsInto",
                    "<flowsInto>\n" +
                    "  <nextElementRef id=\"${1:targetId}\"/>\n" +
                    "</flowsInto>")
    );

    /**
     * Compute completion items for the given cursor position in the document.
     *
     * @param documentText full text of the XML document (may be null)
     * @param line         0-based line of the cursor
     * @param character    0-based column of the cursor
     * @return ordered list of completion items (may be empty, never null)
     */
    public List<CompletionItem> getCompletions(String documentText, int line, int character) {
        if (documentText == null || documentText.isBlank()) {
            return topLevelCompletions();
        }

        CompletionContext ctx = analyzeContext(documentText, line, character);

        return switch (ctx.type()) {
            case ELEMENT_NAME    -> elementNameCompletions(ctx.parentElement());
            case ATTRIBUTE_VALUE -> attributeValueCompletions(ctx.elementName(), ctx.attributeName());
            case ATTRIBUTE_NAME  -> attributeNameCompletions(ctx.elementName());
            case TOP_LEVEL       -> topLevelCompletions();
        };
    }

    // ---- Context analysis ---------------------------------------------------

    private CompletionContext analyzeContext(String text, int line, int col) {
        String[] lines = text.split("\n", -1);
        if (line < 0 || line >= lines.length) {
            return new CompletionContext(ContextType.TOP_LEVEL, null, null, null);
        }

        String lineText = lines[line];
        String prefix   = col <= lineText.length() ? lineText.substring(0, col) : lineText;

        // Inside attribute value: ="..."  or  ="<cursor>
        int eqQuote = prefix.lastIndexOf("=\"");
        if (eqQuote >= 0) {
            // Find attribute name before ="
            String beforeEq = prefix.substring(0, eqQuote).trim();
            String attrName = extractLastWord(beforeEq);
            String elemName = findEnclosingElement(text, line, eqQuote);
            return new CompletionContext(ContextType.ATTRIBUTE_VALUE, elemName, attrName, null);
        }

        // Inside opening tag but after element name — attribute name position
        int tagStart = prefix.lastIndexOf('<');
        if (tagStart >= 0 && !prefix.substring(tagStart).contains(">")) {
            String afterTag = prefix.substring(tagStart + 1).trim();
            if (!afterTag.isEmpty() && afterTag.contains(" ")) {
                String elemName = afterTag.split("\\s+")[0].replace("/", "");
                return new CompletionContext(ContextType.ATTRIBUTE_NAME, null, null, elemName);
            }
            // Still typing element name
            String parent = findParentElement(text, line, tagStart);
            return new CompletionContext(ContextType.ELEMENT_NAME, parent, null, null);
        }

        // Default: suggest child elements based on parent
        String parent = findCurrentContainerElement(text, line);
        if (parent != null) {
            return new CompletionContext(ContextType.ELEMENT_NAME, parent, null, null);
        }

        return new CompletionContext(ContextType.TOP_LEVEL, null, null, null);
    }

    // ---- Completion item builders -------------------------------------------

    private List<CompletionItem> elementNameCompletions(String parentElement) {
        List<CompletionItem> items = new ArrayList<>();

        String key = parentElement != null ? parentElement.toLowerCase() : "";
        List<String> children = CHILD_MAP.getOrDefault(key, List.of());

        for (String child : children) {
            String snippet = SNIPPETS.get(child);
            if (snippet != null) {
                items.add(CompletionItem.snippet(child, snippet,
                        "Insert " + child + " with scaffolding"));
            } else {
                items.add(CompletionItem.element(child, child,
                        "YAWL element <" + child + ">"));
            }
        }

        return items;
    }

    private List<CompletionItem> attributeValueCompletions(String elemName, String attrName) {
        List<CompletionItem> items = new ArrayList<>();
        if (elemName == null || attrName == null) {
            return items;
        }

        String key = elemName.toLowerCase() + "@" + attrName.toLowerCase();
        List<String> values = ATTRIBUTE_ENUMS.get(key);
        if (values != null) {
            for (String value : values) {
                items.add(CompletionItem.enumValue(value, value,
                        "Valid value for " + attrName));
            }
        }
        return items;
    }

    private List<CompletionItem> attributeNameCompletions(String elemName) {
        List<CompletionItem> items = new ArrayList<>();
        if (elemName == null) return items;

        // Build attribute suggestions per element from XSD knowledge
        switch (elemName.toLowerCase()) {
            case "specificationset" -> items.add(CompletionItem.attribute("version", "version=\"4.0\"", "Schema version"));
            case "specification"    -> items.add(CompletionItem.attribute("uri", "uri=\"\"", "Specification URI (required)"));
            case "decomposition"    -> {
                items.add(CompletionItem.attribute("id", "id=\"\"", "Decomposition ID (required)"));
                items.add(CompletionItem.attribute("xsi:type", "xsi:type=\"NetFactsType\"", "Type: NetFactsType or WebServiceGatewayFactsType"));
                items.add(CompletionItem.attribute("isRootNet", "isRootNet=\"true\"", "Mark as root net"));
            }
            case "task", "inputcondition", "outputcondition", "condition" ->
                    items.add(CompletionItem.attribute("id", "id=\"\"", "Element ID (required, unique within net)"));
            case "nextelementref"   -> items.add(CompletionItem.attribute("id", "id=\"\"", "Target element ID"));
            case "join", "split"    -> items.add(CompletionItem.attribute("code", "code=\"xor\"", "Semantics: and | xor | or"));
            case "creationmode"     -> items.add(CompletionItem.attribute("code", "code=\"static\"", "Creation mode: static | dynamic"));
            case "predicate"        -> items.add(CompletionItem.attribute("ordering", "ordering=\"0\"", "Evaluation order (lower first)"));
        }

        return items;
    }

    private List<CompletionItem> topLevelCompletions() {
        return List.of(
                CompletionItem.snippet("specificationSet",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<specificationSet version=\"4.0\"\n" +
                        "    xmlns=\"http://www.yawlfoundation.org/yawlschema\"\n" +
                        "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "    xsi:schemaLocation=\"http://www.yawlfoundation.org/yawlschema " +
                        "http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd\">\n" +
                        "  ${0}\n" +
                        "</specificationSet>",
                        "Insert YAWL 4.0 specification set boilerplate")
        );
    }

    // ---- Context helpers -----------------------------------------------------

    private String findEnclosingElement(String text, int line, int col) {
        String[] lines = text.split("\n", -1);
        String lineText = line < lines.length ? lines[line] : "";
        String prefix = col <= lineText.length() ? lineText.substring(0, col) : lineText;
        int tagStart = prefix.lastIndexOf('<');
        if (tagStart < 0) return null;
        return extractFirstWord(prefix.substring(tagStart + 1));
    }

    private String findParentElement(String text, int line, int tagStart) {
        return findCurrentContainerElement(text, line);
    }

    private String findCurrentContainerElement(String text, int targetLine) {
        String[] lines = text.split("\n", -1);
        // Walk upward to find the innermost open element
        for (int i = Math.min(targetLine, lines.length - 1); i >= 0; i--) {
            String l = lines[i].trim();
            // Look for opening tag that is not self-closed
            if (l.startsWith("<") && !l.startsWith("</") && !l.startsWith("<?")
                    && !l.startsWith("<!--") && !l.endsWith("/>")) {
                String elemName = extractFirstWord(l.substring(1));
                if (elemName != null) return elemName;
            }
        }
        return null;
    }

    private static String extractFirstWord(String s) {
        if (s == null || s.isBlank()) return null;
        String trimmed = s.trim();
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))
                && trimmed.charAt(end) != '>' && trimmed.charAt(end) != '/') {
            end++;
        }
        return end > 0 ? trimmed.substring(0, end) : null;
    }

    private static String extractLastWord(String s) {
        if (s == null || s.isBlank()) return null;
        String trimmed = s.trim();
        int start = trimmed.length() - 1;
        while (start >= 0 && !Character.isWhitespace(trimmed.charAt(start))) {
            start--;
        }
        return trimmed.substring(start + 1);
    }

    // ---- Inner types ---------------------------------------------------------

    private enum ContextType {
        ELEMENT_NAME, ATTRIBUTE_VALUE, ATTRIBUTE_NAME, TOP_LEVEL
    }

    private record CompletionContext(
            ContextType type,
            String parentElement,
            String attributeName,
            String elementName
    ) { }

    /**
     * Immutable LSP CompletionItem with JSON serialisation.
     */
    public record CompletionItem(
            String label,
            String insertText,
            int kind,
            String detail,
            boolean isSnippet
    ) {
        static CompletionItem element(String label, String insertText, String detail) {
            return new CompletionItem(label, insertText, ItemKind.KEYWORD.lspCode(), detail, false);
        }

        static CompletionItem snippet(String label, String insertText, String detail) {
            return new CompletionItem(label, insertText, ItemKind.SNIPPET.lspCode(), detail, true);
        }

        static CompletionItem attribute(String label, String insertText, String detail) {
            return new CompletionItem(label, insertText, ItemKind.PROPERTY.lspCode(), detail, false);
        }

        static CompletionItem enumValue(String label, String insertText, String detail) {
            return new CompletionItem(label, insertText, ItemKind.ENUM_MEMBER.lspCode(), detail, false);
        }

        /** Serialise to LSP CompletionItem JSON. */
        public String toJson() {
            String insertTextFormat = isSnippet ? ",\"insertTextFormat\":2" : "";
            String escapedInsert   = insertText.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t");
            String escapedDetail   = detail.replace("\"", "\\\"");
            return "{\"label\":\"" + label + "\",\"kind\":" + kind +
                    ",\"detail\":\"" + escapedDetail + "\"" +
                    ",\"insertText\":\"" + escapedInsert + "\"" +
                    insertTextFormat + "}";
        }
    }
}
