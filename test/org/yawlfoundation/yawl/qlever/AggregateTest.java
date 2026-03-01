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

@Tag("sparql")
class AggregateTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(AGG_COUNT)
    void aggCount_countsPeoplePerOrg() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?org (COUNT(?p) AS ?cnt) WHERE { ?p :worksFor ?org } GROUP BY ?org", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        for (var row : rows(result)) {
            assertThat(intValue(row, "cnt")).isGreaterThan(0);
        }
    }

    @Test
    @SparqlCapabilityTest(AGG_COUNT_DISTINCT)
    void aggCountDistinct_fewerOrEqualThanCount() throws Exception {
        var allResult = engine().executeSelect(PFX +
            "SELECT (COUNT(?org) AS ?cnt) WHERE { ?p :worksFor ?org }", JSON);
        var distinctResult = engine().executeSelect(PFX +
            "SELECT (COUNT(DISTINCT ?org) AS ?cnt) WHERE { ?p :worksFor ?org }", JSON);
        int all = singleIntValue(allResult, "cnt");
        int distinct = singleIntValue(distinctResult, "cnt");
        assertThat(distinct).isLessThanOrEqualTo(all);
        assertThat(distinct).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(AGG_COUNT_STAR)
    void aggCountStar_countsTotalTriples() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT (COUNT(*) AS ?total) WHERE { ?s ?p ?o }", JSON);
        assertThat(singleIntValue(result, "total")).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(AGG_SUM)
    void aggSum_sumOfAges() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT (SUM(?age) AS ?total) WHERE { ?p :age ?age }", JSON);
        assertThat(singleIntValue(result, "total")).isGreaterThan(50);
    }

    @Test
    @SparqlCapabilityTest(AGG_AVG)
    void aggAvg_averageAge() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT (AVG(?age) AS ?avg) WHERE { ?p :age ?age }", JSON);
        double avg = doubleColumn(result, "avg").get(0);
        assertThat(avg).isGreaterThan(0).isLessThan(100);
    }

    @Test
    @SparqlCapabilityTest(AGG_MIN)
    void aggMin_minimumAge() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT (MIN(?age) AS ?min) WHERE { ?p :age ?age }", JSON);
        assertThat(singleIntValue(result, "min")).isGreaterThanOrEqualTo(20);
    }

    @Test
    @SparqlCapabilityTest(AGG_MAX)
    void aggMax_maximumAge() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT (MAX(?age) AS ?max) WHERE { ?p :age ?age }", JSON);
        assertThat(singleIntValue(result, "max")).isLessThanOrEqualTo(50);
    }

    @Test
    @SparqlCapabilityTest(AGG_SAMPLE)
    void aggSample_returnsOneValue() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT (SAMPLE(?age) AS ?s) WHERE { ?p :age ?age }", JSON);
        assertThat(singleIntValue(result, "s")).isBetween(20, 50);
    }

    @Test
    @SparqlCapabilityTest(AGG_GROUP_CONCAT)
    void aggGroupConcat_defaultSeparator() throws Exception {
        var result = engine().executeSelect(PFX + """
            SELECT ?org (GROUP_CONCAT(?name) AS ?names) WHERE {
              ?p :worksFor ?org ; :name ?name . FILTER(LANG(?name) = 'en')
            } GROUP BY ?org LIMIT 1""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        String names = singleValue(result, "names");
        assertThat(names).isNotBlank();
    }
}
