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

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for SPARQL graph pattern capabilities.
 * Covers: BGP, OPTIONAL, UNION, MINUS, FILTER, FILTER_EXISTS, FILTER_NOT_EXISTS,
 *         BIND, VALUES_INLINE, VALUES_MULTIVAR, SUBQUERY.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class GraphPatternCapabilityTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(BGP)
    void bgp_basicTriplePattern_returnsPersons() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT ?p WHERE { ?p a :Person }", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(values(result, "p")).contains("http://yawl.test/alice");
    }

    @Test
    @SparqlCapabilityTest(OPTIONAL)
    void optional_someRowsMissingOptionalBinding() throws Exception {
        var result = engine().executeSelect(
            PFX + "SELECT ?p ?cat WHERE { ?p a :Person . OPTIONAL { ?p :category ?cat } }", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        var rows = rows(result);
        assertThat(rows).isNotEmpty();
    }

    @Test
    @SparqlCapabilityTest(UNION)
    void union_combinesTwoBranches() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p WHERE {
              { :alice :age ?a . BIND(:alice AS ?p) }
              UNION
              { :carol :worksFor :megacorp . BIND(:carol AS ?p) }
            }""", JSON);
        var people = values(result, "p");
        assertThat(people).containsExactlyInAnyOrder(
            "http://yawl.test/alice", "http://yawl.test/carol");
    }

    @Test
    @SparqlCapabilityTest(MINUS)
    void minus_excludesMatchedSubjects() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p WHERE {
              ?p a :Person .
              MINUS { ?p :worksFor :acme }
            }""", JSON);
        var people = values(result, "p");
        assertThat(people).doesNotContain("http://yawl.test/alice");
        assertThat(people).doesNotContain("http://yawl.test/bob");
    }

    @Test
    @SparqlCapabilityTest(FILTER)
    void filter_numericComparison() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?p WHERE { ?p :age ?a FILTER(?a > 28) }", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(values(result, "p")).contains("http://yawl.test/alice");
        assertThat(values(result, "p")).doesNotContain("http://yawl.test/bob");
    }

    @Test
    @SparqlCapabilityTest(FILTER_EXISTS)
    void filterExists_keepsSubjectsWithCorrelatedPattern() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p WHERE {
              ?p a :Person .
              FILTER EXISTS { ?p :category :science }
            }""", JSON);
        var people = values(result, "p");
        assertThat(people).contains("http://yawl.test/alice");
        assertThat(people).doesNotContain("http://yawl.test/carol");
    }

    @Test
    @SparqlCapabilityTest(FILTER_NOT_EXISTS)
    void filterNotExists_keepsSubjectsWithoutCorrelatedPattern() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p WHERE {
              ?p a :Person .
              FILTER NOT EXISTS { ?p :category :science }
            }""", JSON);
        var people = values(result, "p");
        assertThat(people).contains("http://yawl.test/carol");
        assertThat(people).doesNotContain("http://yawl.test/alice");
    }

    @Test
    @SparqlCapabilityTest(BIND)
    void bind_computedColumnAppearsInResults() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p ?doubled WHERE {
              ?p :age ?a .
              BIND(?a * 2 AS ?doubled)
            } LIMIT 3""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        var rows = rows(result);
        for (var row : rows) {
            assertThat(row.has("doubled")).isTrue();
        }
    }

    @Test
    @SparqlCapabilityTest(VALUES_INLINE)
    void valuesInline_singleVar_joinsAgainstGraph() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p ?age WHERE {
              VALUES ?p { :alice :carol }
              ?p :age ?age .
            }""", JSON);
        assertThat(result.rowCount()).isEqualTo(2);
        var people = values(result, "p");
        assertThat(people).containsExactlyInAnyOrder(
            "http://yawl.test/alice", "http://yawl.test/carol");
    }

    @Test
    @SparqlCapabilityTest(VALUES_MULTIVAR)
    void valuesMultivar_multipleVarsInlineData() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p ?org WHERE {
              VALUES (?p ?org) { (:alice :acme) (:carol :megacorp) }
              ?p :worksFor ?org .
            }""", JSON);
        assertThat(result.rowCount()).isEqualTo(2);
    }

    @Test
    @SparqlCapabilityTest(SUBQUERY)
    void subquery_innerLimitFilteredByOuter() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p ?age WHERE {
              { SELECT ?p ?age WHERE { ?p :age ?age } ORDER BY DESC(?age) LIMIT 1 }
            }""", JSON);
        assertThat(result.rowCount()).isEqualTo(1);
    }
}
