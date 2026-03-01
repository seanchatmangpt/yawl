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

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

@Tag("sparql")
class SolutionModifierTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(ORDER_BY_ASC)
    void orderByAsc_returnsAscendingAges() throws Exception {
        var result = engine().executeSelect(
            PFX + "SELECT ?age WHERE { ?p :age ?age } ORDER BY ASC(?age)", JSON);
        var ages = intColumn(result, "age");
        assertThat(ages).isSorted();
    }

    @Test
    @SparqlCapabilityTest(ORDER_BY_DESC)
    void orderByDesc_returnsDescendingAges() throws Exception {
        var ages = intColumn(engine().executeSelect(
            PFX + "SELECT ?age WHERE { ?p :age ?age } ORDER BY DESC(?age)", JSON), "age");
        assertThat(ages).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @SparqlCapabilityTest(ORDER_BY_EXPR)
    void orderByExpression_sortsByComputed() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?p (?age * -1 AS ?negAge) WHERE { ?p :age ?age }
            ORDER BY DESC(?negAge) LIMIT 5""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(LIMIT)
    void limit_capsResultCount() throws Exception {
        var result = engine().executeSelect(
            PFX + "SELECT ?s WHERE { ?s a :Person } LIMIT 5", JSON);
        assertThat(result.rowCount()).isEqualTo(5);
    }

    @Test
    @SparqlCapabilityTest(OFFSET)
    void offset_skipsFirstRows() throws Exception {
        var all   = engine().executeSelect(
            PFX + "SELECT ?p WHERE { ?p a :Person } ORDER BY ?p", JSON);
        var paged = engine().executeSelect(
            PFX + "SELECT ?p WHERE { ?p a :Person } ORDER BY ?p OFFSET 1", JSON);
        assertThat(firstValue(paged, "p")).isEqualTo(secondValue(all, "p"));
    }

    @Test
    @SparqlCapabilityTest(LIMIT_OFFSET)
    void limitOffset_paginationNoOverlap() throws Exception {
        var page1 = subjects(engine().executeSelect(
            PFX + "SELECT ?s WHERE { ?s a :Person } ORDER BY ?s LIMIT 3 OFFSET 0", JSON));
        var page2 = subjects(engine().executeSelect(
            PFX + "SELECT ?s WHERE { ?s a :Person } ORDER BY ?s LIMIT 3 OFFSET 3", JSON));
        assertThat(page1).doesNotContainAnyElementsOf(page2);
    }

    @Test
    @SparqlCapabilityTest(GROUP_BY)
    void groupBy_countsPerOrganization() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?org (COUNT(?p) AS ?cnt) WHERE { ?p :worksFor ?org } GROUP BY ?org""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(orgSet(result)).contains(
            "http://yawl.test/acme", "http://yawl.test/megacorp");
    }

    @Test
    @SparqlCapabilityTest(HAVING)
    void having_filtersGroupsWithLowCount() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?org (COUNT(?p) AS ?cnt) WHERE { ?p :worksFor ?org }
            GROUP BY ?org HAVING (COUNT(?p) > 1)""", JSON);
        for (var row : rows(result)) {
            assertThat(intValue(row, "cnt")).isGreaterThan(1);
        }
    }
}
