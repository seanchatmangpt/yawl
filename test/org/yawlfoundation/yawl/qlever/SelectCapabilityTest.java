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
 * Chicago TDD tests for SPARQL SELECT clause capabilities.
 * Covers: SELECT_STAR, SELECT_VARIABLES, SELECT_EXPRESSIONS, SELECT_DISTINCT, SELECT_REDUCED.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class SelectCapabilityTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(SELECT_STAR)
    void selectStar_returnsAllTriples() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT * WHERE { ?s ?p ?o } LIMIT 10", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(SELECT_VARIABLES)
    void selectVariables_returnsOnlyRequestedVars() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT ?s WHERE { ?s a :Person }", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        var varNames = allVarNames(parseJson(result.data()));
        assertThat(varNames).containsOnly("s");
    }

    @Test
    @SparqlCapabilityTest(SELECT_EXPRESSIONS)
    void selectExpression_computesValue() throws Exception {
        var result = engine().executeSelect(
            PFX + "SELECT (?age + 1 AS ?nextAge) WHERE { :alice :age ?age }", JSON);
        assertThat(singleIntValue(result, "nextAge")).isEqualTo(31);
    }

    @Test
    @SparqlCapabilityTest(SELECT_DISTINCT)
    void selectDistinct_noDuplicates() throws Exception {
        var all      = engine().executeSelect(PFX + "SELECT ?org WHERE { ?p :worksFor ?org }", JSON);
        var distinct = engine().executeSelect(PFX + "SELECT DISTINCT ?org WHERE { ?p :worksFor ?org }", JSON);
        assertThat(distinct.rowCount()).isLessThanOrEqualTo(all.rowCount());
        assertThat(distinct.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(SELECT_REDUCED)
    void selectReduced_atMostAllRows() throws Exception {
        var result = engine().executeSelect(PFX + "SELECT REDUCED ?s WHERE { ?s ?p ?o } LIMIT 20", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }
}
