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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for QLever-specific SPARQL extensions.
 * Covers: EXT_CONTAINS_WORD, EXT_CONTAINS_ENTITY, EXT_SCORE.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class QLeverExtensionTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(EXT_CONTAINS_WORD)
    void containsWord_findsDocumentsWithWord() throws Exception {
        assumeTrue(QLeverTestNode.hasTextIndex(),
            "QLever text index not built — skipping ql:contains-word test");
        var result = engine().executeSelect(PFX + """
            SELECT ?p WHERE { ?p :text ?t . ?t ql:contains-word 'climate' }""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        assertThat(values(result, "p")).contains(
            "http://yawl.test/alice", "http://yawl.test/carol");
    }

    @Test
    @SparqlCapabilityTest(EXT_CONTAINS_ENTITY)
    void containsEntity_requiresTextIndex() throws Exception {
        assumeTrue(QLeverTestNode.hasTextIndex(),
            "QLever text index not built — skipping ql:contains-entity test");
        var result = engine().executeSelect(PFX + """
            SELECT ?p WHERE { ?p :text ?t . ?t ql:contains-entity :alice }""", JSON);
        assertThat(result.rowCount()).isGreaterThanOrEqualTo(0);
        assertThat(result.data()).isNotNull();
    }

    @Test
    @SparqlCapabilityTest(EXT_SCORE)
    void containsWordWithScore_returnsScoreInDescendingOrder() throws Exception {
        assumeTrue(QLeverTestNode.hasTextIndex(),
            "QLever text index not built — skipping ql:score test");
        var result = engine().executeSelect(PFX + """
            SELECT ?p ?score WHERE {
              ?p :text ?t .
              ?t ql:contains-word 'climate' ;
                 ql:score ?score .
            } ORDER BY DESC(?score)""", JSON);
        assertThat(result.rowCount()).isGreaterThan(0);
        List<Double> scores = doubleColumn(result, "score");
        assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder());
    }
}
