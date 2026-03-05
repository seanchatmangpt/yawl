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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.synthesis;

import java.util.Objects;

/**
 * Serializes WorkflowIntent objects to Turtle RDF format.
 *
 * <p>Converts a WorkflowIntent into valid Turtle RDF using the
 * {@code intent:} vocabulary: {@code <http://yawlfoundation.org/yawl/synthesis/intent#>}</p>
 *
 * <p>The serialized RDF represents the intent as an RDF resource with properties
 * describing the goal, activities, and WCP hints. Activity and WCP hint
 * predicates are repeated for each value in the lists.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class IntentRdfSerializer {

    private static final String INTENT_NS = "http://yawlfoundation.org/yawl/synthesis/intent#";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";

    private IntentRdfSerializer() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Converts a WorkflowIntent to valid Turtle RDF.
     *
     * <p>The output includes:
     * <ul>
     *   <li>Turtle prefix declarations for {@code intent:} and {@code xsd:}</li>
     *   <li>One main resource representing the intent</li>
     *   <li>Properties: {@code intent:goal}, {@code intent:activity}, {@code intent:wcpHint}</li>
     * </ul>
     *
     * @param intent the intent to serialize (non-null)
     * @return valid Turtle RDF string
     * @throws NullPointerException if intent is null
     */
    public static String toTurtle(WorkflowIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");

        long epochMs = System.currentTimeMillis();
        String iri = intentIri(intent);

        StringBuilder turtle = new StringBuilder();

        // Prefixes
        turtle.append("@prefix intent: <").append(INTENT_NS).append("> .\n");
        turtle.append("@prefix xsd: <").append(XSD_NS).append("> .\n\n");

        // Main resource
        turtle.append("<").append(iri).append("> a intent:WorkflowIntent ;\n");
        turtle.append("    intent:goal ").append(escapedString(intent.goal())).append(" ;\n");

        // Activities
        for (String activity : intent.activities()) {
            turtle.append("    intent:activity ").append(escapedString(activity)).append(" ;\n");
        }

        // WCP hints
        for (int i = 0; i < intent.wcpHints().size(); i++) {
            String hint = intent.wcpHints().get(i);
            if (i == intent.wcpHints().size() - 1 && intent.constraints().isEmpty()) {
                // Last property
                turtle.append("    intent:wcpHint ").append(escapedString(hint)).append(" .\n");
            } else {
                turtle.append("    intent:wcpHint ").append(escapedString(hint)).append(" ;\n");
            }
        }

        // Constraints (simplified: store only if non-empty)
        if (!intent.constraints().isEmpty()) {
            turtle.append("    intent:constraintCount ")
                .append(intent.constraints().size())
                .append("^^xsd:integer .\n");
        }

        return turtle.toString();
    }

    /**
     * Generates a unique IRI for a WorkflowIntent based on its goal.
     *
     * <p>The IRI format is:
     * {@code http://yawlfoundation.org/yawl/synthesis/intent/<EPOCH_MILLIS>}
     *
     * @param intent the intent to generate an IRI for
     * @return the intent's unique IRI
     */
    public static String intentIri(WorkflowIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        long epochMs = System.currentTimeMillis();
        int goalHash = Math.abs(intent.goal().hashCode());
        return INTENT_NS + "intent_" + epochMs + "_" + goalHash;
    }

    /**
     * Escapes a string for use in Turtle RDF quoted strings.
     *
     * <p>Handles:
     * <ul>
     *   <li>Escaped double quotes (\") → \"</li>
     *   <li>Escaped backslashes (\\) → \\</li>
     *   <li>Newlines → \n</li>
     * </ul>
     *
     * @param value the string value to escape
     * @return the escaped string surrounded by double quotes
     */
    private static String escapedString(String value) {
        String escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
