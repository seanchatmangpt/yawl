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

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Converts live {@link AgentMarketplaceListing}s to a valid Turtle RDF string.
 *
 * <p>Uses the {@code mkt:} vocabulary at
 * {@code <http://yawlfoundation.org/yawl/marketplace#>} and
 * {@code xsd:} at {@code <http://www.w3.org/2001/XMLSchema#>}.
 * No external RDF library is required — the serialiser is implemented in pure Java.</p>
 *
 * <p>All five marketplace dimensions are emitted per listing:</p>
 * <ol>
 *   <li>Capability (domain name, description)</li>
 *   <li>Ontological coverage (namespaces, SPARQL patterns, CONSTRUCT templates)</li>
 *   <li>Workflow transition contracts (WCP codes, input/output token types)</li>
 *   <li>Economic cost (basePricePerCycle, llmInferenceRatio, constructQueriesPerCall)</li>
 *   <li>Latency profiles (p50/p95/p99 at declared graph sizes)</li>
 * </ol>
 *
 * @since YAWL 6.0
 */
public final class MarketplaceRdfExporter {

    static final String MKT_NS   = "http://yawlfoundation.org/yawl/marketplace#";
    static final String XSD_NS   = "http://www.w3.org/2001/XMLSchema#";
    static final String MKT_BASE = "http://yawlfoundation.org/yawl/marketplace/listing/";

    /**
     * Export all live listings from the given marketplace as Turtle.
     *
     * @param marketplace source of live listings
     * @return valid Turtle string; never null
     */
    public String exportToTurtle(AgentMarketplace marketplace) {
        Objects.requireNonNull(marketplace, "marketplace must not be null");
        return buildTurtle(marketplace.allLiveListings());
    }

    /**
     * Export an explicit list of listings (useful for testing).
     *
     * @param listings listings to serialise (may be empty)
     * @return valid Turtle string; never null
     */
    public String exportToTurtle(List<AgentMarketplaceListing> listings) {
        Objects.requireNonNull(listings, "listings must not be null");
        return buildTurtle(listings);
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private String buildTurtle(List<AgentMarketplaceListing> listings) {
        StringBuilder sb = new StringBuilder();

        // Prefix declarations
        sb.append("@prefix mkt: <").append(MKT_NS).append("> .\n");
        sb.append("@prefix xsd: <").append(XSD_NS).append("> .\n");
        sb.append("\n");

        for (AgentMarketplaceListing listing : listings) {
            appendListing(sb, listing);
        }
        return sb.toString();
    }

    private void appendListing(StringBuilder sb, AgentMarketplaceListing listing) {
        String agentId = listing.agentInfo().getId();
        String subject  = "<" + MKT_BASE + encode(agentId) + ">";

        sb.append(subject).append("\n");
        sb.append("    a mkt:AgentListing ;\n");

        // --- Identity ---
        sb.append("    mkt:agentId ").append(literal(agentId)).append(" ;\n");
        sb.append("    mkt:agentName ").append(literal(listing.agentInfo().getName())).append(" ;\n");

        boolean live = listing.isLive(Duration.ofMinutes(5));
        sb.append("    mkt:isLive \"").append(live).append("\"^^xsd:boolean ;\n");

        AgentMarketplaceSpec spec = listing.spec();
        sb.append("    mkt:specVersion ").append(literal(spec.specVersion())).append(" ;\n");

        // --- Dimension 1: Capability ---
        sb.append("    mkt:domainName ").append(literal(spec.capability().domainName())).append(" ;\n");
        sb.append("    mkt:capabilityDescription ").append(literal(spec.capability().description())).append(" ;\n");

        // --- Dimension 2: Ontological coverage ---
        for (String ns : spec.ontologicalCoverage().declaredNamespaces()) {
            sb.append("    mkt:declaredNamespace ").append(literal(ns)).append(" ;\n");
        }
        for (String pattern : spec.ontologicalCoverage().sparqlPatterns()) {
            sb.append("    mkt:sparqlPattern ").append(literal(pattern)).append(" ;\n");
        }
        for (String template : spec.ontologicalCoverage().constructTemplates()) {
            sb.append("    mkt:constructTemplate ").append(literal(template)).append(" ;\n");
        }

        // --- Dimension 3: Workflow transition contracts ---
        for (WorkflowTransitionContract contract : spec.transitionContracts()) {
            for (String wcp : contract.wcpPatterns()) {
                sb.append("    mkt:wcpPattern ").append(literal(wcp)).append(" ;\n");
            }
            for (String input : contract.inputPlaceTokenTypes()) {
                sb.append("    mkt:inputTokenType ").append(literal(input)).append(" ;\n");
            }
            for (String output : contract.outputPlaceTokenTypes()) {
                sb.append("    mkt:outputTokenType ").append(literal(output)).append(" ;\n");
            }
        }

        // --- Dimension 4: Economic cost ---
        CoordinationCostProfile cost = spec.costProfile();
        sb.append("    mkt:basePricePerCycle \"")
          .append(cost.basePricePerCycle()).append("\"^^xsd:decimal ;\n");
        sb.append("    mkt:llmInferenceRatio \"")
          .append(cost.llmInferenceRatio()).append("\"^^xsd:decimal ;\n");
        sb.append("    mkt:constructQueriesPerCall \"")
          .append(cost.constructQueriesPerCall()).append("\"^^xsd:decimal ;\n");

        // --- Dimension 5: Latency profiles ---
        for (LatencyProfile lp : spec.latencyProfiles()) {
            sb.append("    mkt:graphSizeTriples \"")
              .append(lp.graphSizeTriples()).append("\"^^xsd:long ;\n");
            sb.append("    mkt:p50LatencyMs \"")
              .append(lp.p50Ms()).append("\"^^xsd:long ;\n");
            sb.append("    mkt:p95LatencyMs \"")
              .append(lp.p95Ms()).append("\"^^xsd:long ;\n");
            sb.append("    mkt:p99LatencyMs \"")
              .append(lp.p99Ms()).append("\"^^xsd:long ;\n");
        }

        // --- ggen.toml hash (if present) ---
        if (spec.isDerivedFromGgenToml()) {
            sb.append("    mkt:ggenTomlHash ").append(literal(spec.ggenTomlHash())).append(" ;\n");
        }

        // End of subject: replace last " ;\n" with " .\n"
        int lastSemi = sb.lastIndexOf(" ;\n");
        if (lastSemi >= 0) {
            sb.replace(lastSemi, lastSemi + 3, " .\n");
        }
        sb.append("\n");
    }

    /**
     * Wrap a string as a Turtle quoted literal, escaping {@code "}, {@code \}, and newlines.
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
     */
    static String encode(String value) {
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
