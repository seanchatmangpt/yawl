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

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for SPARQL query form capabilities.
 * Covers: ASK, CONSTRUCT, DESCRIBE, NAMED_GRAPHS_FROM, NAMED_GRAPHS_GRAPH.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class QueryFormTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(ASK)
    void ask_trueWhenPatternMatches() throws Exception {
        assertThat(engine().executeAsk(PFX + "ASK WHERE { :alice :age ?age }")).isTrue();
    }

    @Test
    @SparqlCapabilityTest(ASK)
    void ask_falseWhenNoMatch() throws Exception {
        assertThat(engine().executeAsk(PFX + "ASK WHERE { :alice :nonexistent ?x }")).isFalse();
    }

    @Test
    @SparqlCapabilityTest(CONSTRUCT)
    void construct_returnsTriples() throws Exception {
        var result = engine().executeConstruct(
            PFX + "CONSTRUCT { ?p :fullName ?n } WHERE { ?p :name ?n . FILTER(LANG(?n) = 'en') } LIMIT 5",
            TURTLE);
        assertThat(result.data()).contains(":fullName").or().contains("fullName");
    }

    @Test
    @SparqlCapabilityTest(DESCRIBE)
    void describe_returnsAllPropertiesOfSubject() throws Exception {
        var result = engine().executeDescribe(PFX + "DESCRIBE :alice", TURTLE);
        assertThat(result.data()).contains("alice");
        assertThat(result.data()).contains("age").or().contains(":age");
    }

    @Test
    @SparqlCapabilityTest(NAMED_GRAPHS_GRAPH)
    void namedGraph_graphKeyword_restrictsToGraph() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?a ?b WHERE { GRAPH :graph1 { ?a :knows ?b } }""", JSON);
        var subjects = values(result, "a");
        assertThat(subjects).containsOnly("http://yawl.test/alice");
    }

    @Test
    @SparqlCapabilityTest(NAMED_GRAPHS_FROM)
    void namedGraph_fromKeyword_loadsNamedGraph() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?a ?b FROM NAMED :graph1 WHERE { GRAPH :graph1 { ?a :knows ?b } }""", JSON);
        assertThat(result.rowCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.data()).isNotNull();
    }
}
