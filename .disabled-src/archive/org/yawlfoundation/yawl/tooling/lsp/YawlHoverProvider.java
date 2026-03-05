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

package org.yawlfoundation.yawl.tooling.lsp;

import java.util.Map;

/**
 * Provides Markdown hover documentation for YAWL XML elements when the cursor
 * rests on a known element name or attribute in the VS Code editor.
 *
 * The hover content is keyed by the local XML element name as it appears in
 * YAWL_Schema4.0.xsd. Attribute-level hovers are keyed by
 * {@code elementName@attributeName}.
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public final class YawlHoverProvider {

    /**
     * Static documentation map: element/attribute token -> Markdown string.
     * Keys are lowercase local names as they appear in the YAWL XML schema.
     */
    private static final Map<String, String> HOVER_DOCS = Map.ofEntries(

        Map.entry("specificationset",
                "**specificationSet** — Root element of a YAWL specification file.\n\n" +
                "Attributes:\n" +
                "- `version` — Schema version (e.g. `4.0`). Required.\n\n" +
                "Corresponds to `SpecificationSetFactsType` in `YAWL_Schema4.0.xsd`."),

        Map.entry("specification",
                "**specification** — A single YAWL workflow specification.\n\n" +
                "Attributes:\n" +
                "- `uri` — Unique resource identifier. Required.\n\n" +
                "Contains one root net and zero or more sub-net decompositions."),

        Map.entry("decomposition",
                "**decomposition** — A workflow net or service gateway decomposition.\n\n" +
                "Attributes:\n" +
                "- `id` — Unique identifier within the specification. Required.\n" +
                "- `xsi:type` — `NetFactsType` (net) or `WebServiceGatewayFactsType` (service).\n" +
                "- `isRootNet` — `true` if this is the root net. At most one root net permitted.\n\n" +
                "Maps to `YNet` or `YAWLServiceGateway` in the engine."),

        Map.entry("task",
                "**task** — An atomic workflow task (leaf activity).\n\n" +
                "Attributes:\n" +
                "- `id` — Unique element ID within the net. Required.\n\n" +
                "Contains `name`, `join`, `split`, optional `multiInstance`, " +
                "and optional `decomposesTo` for composite tasks.\n\n" +
                "Maps to `YAtomicTask` or `YCompositeTask` in `org.yawlfoundation.yawl.elements`."),

        Map.entry("inputcondition",
                "**inputCondition** — The single start place of a net (Petri-net source).\n\n" +
                "Every net must have exactly one input condition. When a case is started " +
                "a token is placed on the input condition, enabling its outgoing tasks.\n\n" +
                "Maps to `YInputCondition`."),

        Map.entry("outputcondition",
                "**outputCondition** — The single end place of a net (Petri-net sink).\n\n" +
                "When all tokens reach the output condition, the net (and its case) completes.\n\n" +
                "Maps to `YOutputCondition`."),

        Map.entry("condition",
                "**condition** — An intermediate Petri-net place between tasks.\n\n" +
                "Conditions hold tokens between task firings and control synchronisation.\n\n" +
                "Maps to `YCondition`."),

        Map.entry("flowsinto",
                "**flowsInto** — Declares an outgoing control-flow arc from the enclosing element.\n\n" +
                "Contains a `nextElementRef` and optionally an XPath `predicate` " +
                "(for XOR/OR splits) and `isDefaultFlow`."),

        Map.entry("nextelementref",
                "**nextElementRef** — References the target element of a flow arc.\n\n" +
                "Attributes:\n" +
                "- `id` — ID of the target net element. Must resolve within the same net."),

        Map.entry("join",
                "**join** — Specifies the join semantics of a task.\n\n" +
                "Attributes:\n" +
                "- `code` — `and` | `xor` | `or`\n\n" +
                "| Code | Behaviour |\n" +
                "|------|-----------|\n" +
                "| `and` | Waits for tokens on ALL incoming arcs (synchronisation) |\n" +
                "| `xor` | Fires when ONE incoming arc has a token (merge) |\n" +
                "| `or`  | Fires when at least one incoming arc has a token and no more are expected |"),

        Map.entry("split",
                "**split** — Specifies the split semantics of a task.\n\n" +
                "Attributes:\n" +
                "- `code` — `and` | `xor` | `or`\n\n" +
                "| Code | Behaviour |\n" +
                "|------|-----------|\n" +
                "| `and` | Fires all outgoing arcs simultaneously (parallel split) |\n" +
                "| `xor` | Fires exactly one outgoing arc based on predicates (exclusive choice) |\n" +
                "| `or`  | Fires one or more outgoing arcs (multiple choice) |"),

        Map.entry("multiinstance",
                "**multiInstance** — Declares multi-instance behaviour for a task.\n\n" +
                "Child elements:\n" +
                "- `minimum` — Minimum number of instances to create.\n" +
                "- `maximum` — Maximum number of instances.\n" +
                "- `threshold` — Number of instances that must complete before the task completes.\n" +
                "- `creationMode` — `static` (fixed at launch) or `dynamic` (runtime creation).\n\n" +
                "Maps to `YMultiInstanceAttributes`."),

        Map.entry("decomposesto",
                "**decomposesTo** — Links a composite task to its sub-net decomposition.\n\n" +
                "Attributes:\n" +
                "- `id` — ID of the target `decomposition` element. Must exist in the spec.\n\n" +
                "When present, the task is a `YCompositeTask` and launching it starts a sub-case."),

        Map.entry("predicate",
                "**predicate** — XPath expression evaluated to determine whether a flow arc is taken.\n\n" +
                "Used inside `flowsInto` for XOR and OR splits. The expression is evaluated " +
                "against the task's output data document.\n\n" +
                "Attributes:\n" +
                "- `ordering` — Integer ordering for evaluating multiple predicates (lower first)."),

        Map.entry("metadata",
                "**metaData** — Specification metadata block.\n\n" +
                "Contains optional elements: `creator`, `description`, `version`, " +
                "`validFrom`, `validUntil`, `identifier`."),

        Map.entry("name",
                "**name** — Human-readable display name of the enclosing element.\n\n" +
                "Used in the designer, resource service, and documentation generation."),

        Map.entry("documentation",
                "**documentation** — Free-text description of the enclosing element.\n\n" +
                "Rendered in tool documentation popups and documentation generation output.")
    );

    private YawlHoverProvider() {
        throw new UnsupportedOperationException("YawlHoverProvider is a utility class");
    }

    /**
     * Return Markdown hover content for the YAWL XML element or attribute
     * at the given cursor position, or {@code null} if no known token is found.
     *
     * @param documentText full text of the XML document (may be null)
     * @param line         0-based line number of the cursor
     * @param character    0-based column number of the cursor
     * @return Markdown string or null
     */
    public static String getHoverContent(String documentText, int line, int character) {
        if (documentText == null || documentText.isBlank()) {
            return null;
        }
        String token = extractTokenAtPosition(documentText, line, character);
        if (token == null) {
            return null;
        }
        return HOVER_DOCS.get(token.toLowerCase());
    }

    /**
     * Extract the XML element name or attribute name under the cursor.
     * Scans the relevant line left and right from the cursor position to
     * identify an XML name token.
     *
     * @param text      full document text
     * @param line      0-based line index
     * @param character 0-based column index
     * @return the token string (e.g. "task", "join") or null if none found
     */
    private static String extractTokenAtPosition(String text, int line, int character) {
        String[] lines = text.split("\n", -1);
        if (line < 0 || line >= lines.length) {
            return null;
        }
        String lineText = lines[line];
        if (character < 0 || character > lineText.length()) {
            return null;
        }

        // Scan left from cursor to find start of XML name
        int start = character;
        while (start > 0 && isXmlNameChar(lineText.charAt(start - 1))) {
            start--;
        }
        // Scan right to find end
        int end = character;
        while (end < lineText.length() && isXmlNameChar(lineText.charAt(end))) {
            end++;
        }

        if (start >= end) {
            return null;
        }

        String token = lineText.substring(start, end);
        // Strip namespace prefix (e.g. "xsi:type" -> "type", but keep "task")
        int colonIdx = token.indexOf(':');
        if (colonIdx >= 0) {
            token = token.substring(colonIdx + 1);
        }
        return token.isEmpty() ? null : token;
    }

    private static boolean isXmlNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.';
    }
}
