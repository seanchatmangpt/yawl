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

package org.yawlfoundation.yawl.integration.conscience;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * Converts {@link AgentDecision} records to Turtle RDF format.
 *
 * <p>Uses the {@code dec:} vocabulary at {@code <http://yawlfoundation.org/yawl/conscience#>}
 * and {@code xsd:} at {@code <http://www.w3.org/2001/XMLSchema#>}.
 * No external RDF library is required — the serialiser is implemented in pure Java.</p>
 *
 * <p>Each decision is serialized as a Turtle triple block with the following properties:</p>
 * <ul>
 *   <li>{@code dec:agentId} — the agent's identifier (string literal)</li>
 *   <li>{@code dec:sessionId} — the session identifier (string literal)</li>
 *   <li>{@code dec:taskType} — type of task (string literal)</li>
 *   <li>{@code dec:choiceKey} — identifier of the choice (string literal)</li>
 *   <li>{@code dec:rationale} — explanation of the decision (string literal, escaped)</li>
 *   <li>{@code dec:confidence} — confidence level (xsd:double, range 0.0–1.0)</li>
 *   <li>{@code dec:timestamp} — decision timestamp (xsd:dateTime, ISO 8601 format)</li>
 *   <li>{@code dec:context_{key}} — context metadata entries (string literals, escaped)</li>
 * </ul>
 *
 * <p>Example output:</p>
 * <pre>
 * @prefix dec: <http://yawlfoundation.org/yawl/conscience#> .
 * @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
 *
 * <dec:decision/agent-1/1708700335000>
 *     a dec:AgentDecision ;
 *     dec:agentId "agent-1" ;
 *     dec:sessionId "session-xyz" ;
 *     dec:taskType "routing" ;
 *     dec:choiceKey "marketplace_find_for_slot" ;
 *     dec:rationale "Selected agent with lowest latency" ;
 *     dec:confidence "0.95"^^xsd:double ;
 *     dec:timestamp "2024-02-23T14:32:15Z"^^xsd:dateTime ;
 *     dec:context_selected_agent "agent-42" ;
 *     dec:context_latency_ms "125" .
 * </pre>
 * </p>
 *
 * @since YAWL 6.0
 */
public final class DecisionRdfSerializer {

    static final String DEC_NS   = "http://yawlfoundation.org/yawl/conscience#";
    static final String XSD_NS   = "http://www.w3.org/2001/XMLSchema#";
    static final String DEC_BASE = "http://yawlfoundation.org/yawl/conscience/decision/";

    /**
     * Serialize a single {@link AgentDecision} to Turtle RDF format.
     *
     * @param decision the decision to serialize (must not be null)
     * @return valid Turtle string with one decision triple block; never null
     */
    public String serialize(AgentDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");

        StringBuilder sb = new StringBuilder();

        // Prefix declarations
        sb.append("@prefix dec: <").append(DEC_NS).append("> .\n");
        sb.append("@prefix xsd: <").append(XSD_NS).append("> .\n");
        sb.append("\n");

        // Subject IRI: <dec:decision/{agentId}/{timestamp.toEpochMilli()}>
        long epochMilli = decision.timestamp().toEpochMilli();
        String subject = "<" + DEC_BASE + encodeIriSegment(decision.agentId())
                       + "/" + epochMilli + ">";

        sb.append(subject).append("\n");
        sb.append("    a dec:AgentDecision ;\n");

        // Properties
        sb.append("    dec:agentId ").append(literal(decision.agentId())).append(" ;\n");
        sb.append("    dec:sessionId ").append(literal(decision.sessionId())).append(" ;\n");
        sb.append("    dec:taskType ").append(literal(decision.taskType())).append(" ;\n");
        sb.append("    dec:choiceKey ").append(literal(decision.choiceKey())).append(" ;\n");
        sb.append("    dec:rationale ").append(literal(decision.rationale())).append(" ;\n");
        sb.append("    dec:confidence \"").append(decision.confidence())
          .append("\"^^xsd:double ;\n");

        // Timestamp in ISO 8601 format
        String isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(decision.timestamp());
        sb.append("    dec:timestamp \"").append(isoTimestamp)
          .append("\"^^xsd:dateTime ;\n");

        // Context entries
        for (Map.Entry<String, String> entry : decision.context().entrySet()) {
            String contextKey = "context_" + encodeIriSegment(entry.getKey());
            sb.append("    dec:").append(contextKey).append(" ")
              .append(literal(entry.getValue())).append(" ;\n");
        }

        // Replace last " ;\n" with " .\n"
        int lastSemi = sb.lastIndexOf(" ;\n");
        if (lastSemi >= 0) {
            sb.replace(lastSemi, lastSemi + 3, " .\n");
        }

        return sb.toString();
    }

    /**
     * Wrap a string as a Turtle quoted literal, escaping {@code "}, {@code \}, and newlines.
     *
     * @param value the string to escape (must not be null)
     * @return quoted Turtle literal (e.g., {@code "hello world"})
     */
    static String literal(String value) {
        Objects.requireNonNull(value, "Turtle literal value must not be null");
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    /**
     * Percent-encode a string for use in an IRI path segment (minimal encoding).
     *
     * @param value the string to encode (must not be null)
     * @return percent-encoded string safe for IRI paths
     */
    static String encodeIriSegment(String value) {
        Objects.requireNonNull(value, "IRI path segment value must not be null");
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                sb.append(c);
            } else {
                sb.append(String.format("%%%02X", (int) c));
            }
        }
        return sb.toString();
    }
}
