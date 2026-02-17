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

package org.yawlfoundation.yawl.tooling.intellij.documentation;

import org.yawlfoundation.yawl.tooling.intellij.visualization.YawlNetGraphBuilder;

import java.util.Map;

/**
 * Provides quick documentation popup content for YAWL XML elements in IntelliJ IDEA.
 *
 * When a developer presses {@code Ctrl+Q} (or {@code F1} on macOS) while the cursor
 * is on a YAWL element name, IntelliJ calls the plugin's {@code DocumentationProvider}
 * adapter, which delegates to {@link #getDocumentation(String, String)} on this class.
 *
 * The documentation is rendered as HTML suitable for IntelliJ's built-in documentation
 * panel. The HTML uses IntelliJ's standard quick-doc CSS conventions (bold for element
 * names, {@code <code>} for identifiers, sections separated by {@code <hr>}).
 *
 * Additionally, {@link #getStructuralSummary(YawlNetGraphBuilder.NetGraph)} formats
 * a concise structural summary of a parsed net for use in the "Structure" tool window.
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public final class YawlDocumentationProvider {

    /**
     * HTML documentation map keyed by lowercase element name.
     * Values are full HTML fragments (no outer html/body tags).
     */
    private static final Map<String, String> ELEMENT_DOCS = Map.ofEntries(

        Map.entry("specificationset",
                "<b>specificationSet</b>" +
                "<p>Root element of a YAWL specification file. " +
                "Contains one or more <code>specification</code> elements.</p>" +
                "<hr>" +
                "<b>Required attributes:</b><br>" +
                "&nbsp;<code>version</code> — Schema version (use <code>4.0</code>)<br>" +
                "&nbsp;<code>xmlns</code> — Must be <code>http://www.yawlfoundation.org/yawlschema</code>" +
                "<hr>" +
                "<b>Schema type:</b> <code>SpecificationSetFactsType</code>"),

        Map.entry("specification",
                "<b>specification</b>" +
                "<p>A single workflow specification. Contains a root net and optional sub-nets.</p>" +
                "<hr>" +
                "<b>Required attributes:</b><br>" +
                "&nbsp;<code>uri</code> — Unique identifier for this specification<br>" +
                "<hr>" +
                "<b>Children:</b> name, documentation, metaData, schema, decomposition+<br>" +
                "<b>Engine class:</b> <code>YSpecification</code>"),

        Map.entry("decomposition",
                "<b>decomposition</b>" +
                "<p>A workflow net (<code>NetFactsType</code>) or service gateway " +
                "(<code>WebServiceGatewayFactsType</code>).</p>" +
                "<hr>" +
                "<b>Required attributes:</b><br>" +
                "&nbsp;<code>id</code> — Unique ID within the specification<br>" +
                "&nbsp;<code>xsi:type</code> — <code>NetFactsType</code> or <code>WebServiceGatewayFactsType</code><br>" +
                "<b>Optional:</b> <code>isRootNet=\"true\"</code> — marks the root net<br>" +
                "<hr>" +
                "<b>Engine classes:</b> <code>YNet</code>, <code>YAWLServiceGateway</code>"),

        Map.entry("task",
                "<b>task</b>" +
                "<p>An atomic workflow task. Represents one unit of work in the process.</p>" +
                "<hr>" +
                "<b>Required:</b> <code>id</code> attribute (unique within net)<br>" +
                "<b>Children:</b> name, flowsInto, join, split<br>" +
                "<b>Optional:</b> multiInstance, decomposesTo (composite), resourcing, timer<br>" +
                "<hr>" +
                "<b>Join semantics:</b> <code>and</code> | <code>xor</code> | <code>or</code><br>" +
                "<b>Split semantics:</b> <code>and</code> | <code>xor</code> | <code>or</code><br>" +
                "<hr>" +
                "<b>Engine class:</b> <code>YAtomicTask</code> / <code>YCompositeTask</code>"),

        Map.entry("inputcondition",
                "<b>inputCondition</b>" +
                "<p>The unique start place of a Petri net. " +
                "Exactly one per net — receives the initial token when a case starts.</p>" +
                "<hr>" +
                "<b>Engine class:</b> <code>YInputCondition</code>"),

        Map.entry("outputcondition",
                "<b>outputCondition</b>" +
                "<p>The unique end place of a Petri net. " +
                "When all tokens reach here the net (and its case) completes.</p>" +
                "<hr>" +
                "<b>Engine class:</b> <code>YOutputCondition</code>"),

        Map.entry("condition",
                "<b>condition</b>" +
                "<p>An intermediate Petri-net place. " +
                "Holds tokens between task firings to model synchronisation and sequencing.</p>" +
                "<hr>" +
                "<b>Engine class:</b> <code>YCondition</code>"),

        Map.entry("flowsinto",
                "<b>flowsInto</b>" +
                "<p>Declares a control-flow arc from the enclosing element to a target element.</p>" +
                "<hr>" +
                "<b>Children:</b><br>" +
                "&nbsp;<code>nextElementRef id=\"...\"</code> — target element (required)<br>" +
                "&nbsp;<code>predicate ordering=\"N\"</code> — XPath expression for XOR/OR splits<br>" +
                "&nbsp;<code>isDefaultFlow/</code> — marks as default arc when no predicate matches"),

        Map.entry("join",
                "<b>join</b>" +
                "<p>Specifies the join semantics (incoming arc behaviour) of a task.</p>" +
                "<hr>" +
                "<b>code values:</b><br>" +
                "&nbsp;<code>and</code> — synchronise: wait for tokens on ALL incoming arcs<br>" +
                "&nbsp;<code>xor</code> — merge: fire when ONE incoming arc has a token<br>" +
                "&nbsp;<code>or</code>  — discriminator-like: fire when at least one arc is marked " +
                "and no further tokens are expected"),

        Map.entry("split",
                "<b>split</b>" +
                "<p>Specifies the split semantics (outgoing arc behaviour) of a task.</p>" +
                "<hr>" +
                "<b>code values:</b><br>" +
                "&nbsp;<code>and</code> — parallel split: fire ALL outgoing arcs<br>" +
                "&nbsp;<code>xor</code> — exclusive choice: fire exactly ONE arc (via predicates)<br>" +
                "&nbsp;<code>or</code>  — multiple choice: fire one or more arcs"),

        Map.entry("multiinstance",
                "<b>multiInstance</b>" +
                "<p>Configures multi-instance task behaviour (workflow pattern WP13).</p>" +
                "<hr>" +
                "<b>Child elements:</b><br>" +
                "&nbsp;<code>minimum</code> — minimum instances (positive integer)<br>" +
                "&nbsp;<code>maximum</code> — maximum instances (positive integer, ≥ minimum)<br>" +
                "&nbsp;<code>threshold</code> — instances that must complete before task completes<br>" +
                "&nbsp;<code>creationMode code=\"static|dynamic\"</code><br>" +
                "<hr>" +
                "<b>Engine class:</b> <code>YMultiInstanceAttributes</code>"),

        Map.entry("decomposesto",
                "<b>decomposesTo</b>" +
                "<p>Links a composite task to its sub-net. " +
                "When present, the task is a <code>YCompositeTask</code> that launches a sub-case.</p>" +
                "<hr>" +
                "<b>Required:</b> <code>id</code> attribute matching a decomposition ID in this spec"),

        Map.entry("predicate",
                "<b>predicate</b>" +
                "<p>XPath 1.0 expression evaluated against the task's output data " +
                "to decide whether the enclosing flow arc is taken.</p>" +
                "<hr>" +
                "<b>Required attribute:</b> <code>ordering</code> — evaluation order (lower = first)<br>" +
                "<b>Example:</b> <code>/taskData/amount &gt; 1000</code>")
    );

    private YawlDocumentationProvider() {
        throw new UnsupportedOperationException("YawlDocumentationProvider is a utility class");
    }

    /**
     * Return HTML documentation for the given YAWL element name and its current ID value.
     *
     * @param elementName the local XML element name (case-insensitive)
     * @param elementId   the value of the {@code id} attribute (may be null)
     * @return HTML documentation string, or a generic fallback if the element is unknown
     */
    public static String getDocumentation(String elementName, String elementId) {
        if (elementName == null) {
            return "<p>No YAWL documentation available for this position.</p>";
        }

        String doc = ELEMENT_DOCS.get(elementName.toLowerCase());
        if (doc == null) {
            return "<p>YAWL element <code>" + htmlEscape(elementName) + "</code>.</p>" +
                    "<p>See <code>YAWL_Schema4.0.xsd</code> for the full type definition.</p>";
        }

        String idSuffix = (elementId != null && !elementId.isBlank())
                ? "<p><b>Current id:</b> <code>" + htmlEscape(elementId) + "</code></p>"
                : "";

        return idSuffix + doc;
    }

    /**
     * Format a concise structural summary of a parsed net graph for the
     * IntelliJ Structure tool window.
     *
     * @param graph the net graph (must not be null)
     * @return HTML summary string
     */
    public static String getStructuralSummary(YawlNetGraphBuilder.NetGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("NetGraph cannot be null");
        }

        return "<b>Net:</b> <code>" + htmlEscape(graph.netId()) + "</code>" +
                "<br><b>Elements:</b> " + graph.nodeCount() +
                "<br><b>Flows:</b> "    + graph.edgeCount() +
                "<br><b>Tasks:</b> "    + graph.taskCount() +
                "<br><b>Conditions:</b> " + graph.conditionCount();
    }

    private static String htmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
