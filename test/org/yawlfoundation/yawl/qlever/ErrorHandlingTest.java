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
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;
import static org.yawlfoundation.yawl.qlever.QLeverMediaType.*;

/**
 * Chicago TDD tests for SPARQL error handling capabilities.
 * Covers: ERR_MALFORMED_SPARQL, ERR_TIMEOUT, ERR_UNKNOWN_PREDICATE,
 *         ERR_UPDATE_ON_READONLY, ERR_EMPTY_RESULT.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class ErrorHandlingTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(ERR_MALFORMED_SPARQL)
    void malformedQuery_throwsWithUsefulMessage() {
        var ex = assertThrows(SparqlEngineException.class,
            () -> engine().executeSelect("SELECT WHERE NOT VALID SPARQL", JSON));
        assertThat(ex.getMessage()).isNotBlank();
    }

    @Test
    @SparqlCapabilityTest(ERR_TIMEOUT)
    void timeoutQuery_handledGracefully() {
        // A potentially slow cross-product query — expect either result or exception
        assertDoesNotThrow(() -> {
            try {
                engine().executeSelect(
                    "SELECT * WHERE { ?a ?b ?c . ?d ?e ?f } LIMIT 1", JSON);
            } catch (SparqlEngineException e) {
                // Timeout or error is acceptable — just not a crash
                assertThat(e.getMessage()).isNotBlank();
            }
        });
    }

    @Test
    @SparqlCapabilityTest(ERR_UNKNOWN_PREDICATE)
    void unknownPredicate_returnsEmptyNotError() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?s WHERE { ?s <http://yawl.test/completely-unknown-predicate-xyz> ?o }", JSON);
        assertThat(result.rowCount()).isEqualTo(0);
    }

    @Test
    @SparqlCapabilityTest(ERR_UPDATE_ON_READONLY)
    void updateOnReadonlyEngine_throwsOrSkips() {
        assumeTrue(QLeverTestNode.hasReadOnlyEngine(),
            "No read-only engine available — skipping readonly update test");
        assertThrows(Exception.class,
            () -> QLeverTestNode.readOnlyEngine()
                .executeUpdate("INSERT DATA { <http://yawl.test/x> <http://yawl.test/y> <http://yawl.test/z> }"));
    }

    @Test
    @SparqlCapabilityTest(ERR_EMPTY_RESULT)
    void emptyResult_rowCountZeroDataStillValidJson() throws Exception {
        var result = engine().executeSelect(PFX +
            "SELECT ?s WHERE { ?s :age 9999 }", JSON);
        assertThat(result.rowCount()).isEqualTo(0);
        var json = parseJson(result.data());
        assertThat(json.at("/results/bindings").isArray()).isTrue();
        assertThat(json.at("/results/bindings").size()).isEqualTo(0);
    }
}
