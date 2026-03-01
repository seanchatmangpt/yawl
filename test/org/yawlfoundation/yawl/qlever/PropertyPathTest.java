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
class PropertyPathTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(PATH_SEQUENCE)
    void pathSequence_traversesTwoHops() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?corp WHERE { :alice :worksFor/:partOf ?corp }", JSON);
        assertThat(values(result, "corp")).contains("http://yawl.test/megacorp");
    }

    @Test
    @SparqlCapabilityTest(PATH_ALTERNATIVE)
    void pathAlternative_matchesEitherPredicate() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT DISTINCT ?p WHERE { ?p (:age|:worksFor) ?x }", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(values(result, "p")).contains("http://yawl.test/alice");
    }

    @Test
    @SparqlCapabilityTest(PATH_INVERSE)
    void pathInverse_reverseTraversal() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?emp WHERE { :acme ^:worksFor ?emp }", JSON);
        assertThat(values(result, "emp"))
            .contains("http://yawl.test/alice", "http://yawl.test/bob");
    }

    @Test
    @SparqlCapabilityTest(PATH_ZERO_OR_MORE)
    void pathZeroOrMore_includesSelfAndTransitive() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?org WHERE { :acme :partOf* ?org }", JSON);
        var orgs = values(result, "org");
        assertThat(orgs).contains("http://yawl.test/acme");
        assertThat(orgs).contains("http://yawl.test/megacorp");
    }

    @Test
    @SparqlCapabilityTest(PATH_ONE_OR_MORE)
    void pathOneOrMore_findsAllTransitiveOrgs() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?org WHERE { :alice :worksFor/:partOf+ ?org }", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
    }

    @Test
    @SparqlCapabilityTest(PATH_ZERO_OR_ONE)
    void pathZeroOrOne_optionalSingleHop() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?org WHERE { :acme :partOf? ?org }", JSON);
        var orgs = values(result, "org");
        assertThat(orgs).contains("http://yawl.test/acme");
        assertThat(orgs).hasSizeBetween(1, 2);
    }

    @Test
    @SparqlCapabilityTest(PATH_NEGATED)
    void pathNegated_excludesSpecificPredicate() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?pred ?val WHERE { :alice !:age ?val . :alice ?pred ?val }", JSON);
        var predicates = values(result, "pred");
        assertThat(predicates).doesNotContain("http://yawl.test/age");
    }

    @Test
    @SparqlCapabilityTest(PATH_NEGATED_INVERSE)
    void pathNegatedInverse_excludesInverseOfPredicate() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?x WHERE { ?x ^!:partOf :acme } LIMIT 5", JSON);
        assertThat(result.data()).isNotNull();
    }
}
