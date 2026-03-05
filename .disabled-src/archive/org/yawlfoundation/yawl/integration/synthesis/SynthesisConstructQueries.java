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

/**
 * SPARQL CONSTRUCT query constants for intent-to-workflow synthesis.
 *
 * <p>These queries extract workflow structure hints from loaded WorkflowIntent RDF,
 * constructing net descriptions via SPARQL graph patterns.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class SynthesisConstructQueries {

    private SynthesisConstructQueries() {
        throw new UnsupportedOperationException("Constants class — do not instantiate");
    }

    /**
     * Common SPARQL prefix block for all synthesis queries.
     */
    static final String PREFIXES = """
        PREFIX intent: <http://yawlfoundation.org/yawl/synthesis/intent#>
        PREFIX wcp: <http://yawlfoundation.org/yawl/wcp#>
        PREFIX net: <http://yawlfoundation.org/yawl/net#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        """;

    /**
     * Extract WCP hints from the loaded intent RDF.
     *
     * <p>Constructs a graph containing the intent's goal, activities, and WCP hints
     * as extracted from the RDF.</p>
     */
    public static final String SELECT_WCP_FOR_INTENT = PREFIXES + """
        CONSTRUCT {
          ?intent a intent:WorkflowIntent ;
            intent:wcpHint ?hint ;
            intent:activity ?activity ;
            intent:goal ?goal .
        }
        WHERE {
          ?intent a intent:WorkflowIntent ;
            intent:goal ?goal .
          OPTIONAL { ?intent intent:wcpHint ?hint . }
          OPTIONAL { ?intent intent:activity ?activity . }
        }
        """;

    /**
     * Construct a linear net skeleton from sequential activities.
     *
     * <p>Creates place and transition nodes in RDF form, suitable for assembly
     * into a sequential Petri net. Each activity becomes a transition, places
     * separate them.</p>
     */
    public static final String CONSTRUCT_SEQUENTIAL_NET = PREFIXES + """
        CONSTRUCT {
          ?place a net:Place .
          ?transition a net:Transition ;
            net:label ?activity ;
            net:inputPlace ?prevPlace ;
            net:outputPlace ?nextPlace .
        }
        WHERE {
          ?intent a intent:WorkflowIntent ;
            intent:activity ?activity .
        }
        """;

    /**
     * Construct a basic YAWL-like XML description from net triples.
     *
     * <p>Orders net elements and provides the structure for XML serialization.</p>
     */
    public static final String CONSTRUCT_YAWL_SPEC = PREFIXES + """
        CONSTRUCT {
          ?transition net:label ?label ;
                      net:order ?order .
        }
        WHERE {
          ?transition a net:Transition ;
            net:label ?label .
          OPTIONAL { ?transition net:order ?order . }
        }
        ORDER BY ?order
        """;
}
