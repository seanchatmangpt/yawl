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
 * Chicago TDD tests for SPARQL UPDATE capabilities.
 * Covers: UPDATE_INSERT_DATA, UPDATE_DELETE_DATA, UPDATE_DELETE_WHERE,
 *         UPDATE_INSERT_WHERE, UPDATE_DELETE_INSERT, UPDATE_CLEAR, UPDATE_DROP.
 *
 * Each test verifies state changes with post-update SELECT queries.
 * Cleanup via rollback in finally blocks restores original state.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@Tag("sparql")
class SparqlUpdateTest extends SparqlTestFixtures {

    @Test
    @SparqlCapabilityTest(UPDATE_INSERT_DATA)
    void insertData_tripleAppearsInSubsequentSelect() throws Exception {
        engine().executeUpdate(PFX + "INSERT DATA { :dave :age 40 }");
        try {
            var result = engine().executeSelect(PFX + "SELECT ?age WHERE { :dave :age ?age }", JSON);
            assertThat(singleIntValue(result, "age")).isEqualTo(40);
        } finally {
            engine().executeUpdate(PFX + "DELETE DATA { :dave :age 40 }");
        }
    }

    @Test
    @SparqlCapabilityTest(UPDATE_DELETE_DATA)
    void deleteData_tripleAbsentAfterDelete() throws Exception {
        engine().executeUpdate(PFX + "INSERT DATA { :temp1 :val 99 }");
        engine().executeUpdate(PFX + "DELETE DATA { :temp1 :val 99 }");
        var result = engine().executeSelect(PFX + "SELECT ?v WHERE { :temp1 :val ?v }", JSON);
        assertThat(result.rowCount()).isEqualTo(0);
    }

    @Test
    @SparqlCapabilityTest(UPDATE_DELETE_WHERE)
    void deleteWhere_deletesMatchingTriples() throws Exception {
        engine().executeUpdate(PFX + "INSERT DATA { :tempX :marker 'test' }");
        try {
            engine().executeUpdate(PFX + "DELETE WHERE { :tempX :marker 'test' }");
            var result = engine().executeSelect(PFX + "SELECT ?v WHERE { :tempX :marker ?v }", JSON);
            assertThat(result.rowCount()).isEqualTo(0);
        } finally {
            // Already deleted by DELETE WHERE — nothing to restore
        }
    }

    @Test
    @SparqlCapabilityTest(UPDATE_INSERT_WHERE)
    void insertWhere_insertsFromPatternMatch() throws Exception {
        engine().executeUpdate(PFX + """
            INSERT { ?p :senior true }
            WHERE  { ?p :age ?a . FILTER(?a > 32) }""");
        try {
            var result = engine().executeSelect(PFX +
                "SELECT ?p WHERE { ?p :senior true }", JSON);
            assertThat(result.rowCount()).isGreaterThan(0);
            assertThat(values(result, "p")).contains("http://yawl.test/carol");
        } finally {
            engine().executeUpdate(PFX + "DELETE WHERE { ?p :senior true }");
        }
    }

    @Test
    @SparqlCapabilityTest(UPDATE_DELETE_INSERT)
    void deleteInsert_atomicSwap() throws Exception {
        engine().executeUpdate(PFX + """
            DELETE { :alice :age 30 }
            INSERT { :alice :age 31 }
            WHERE  { :alice :age 30 }""");
        try {
            assertThat(singleIntValue(engine().executeSelect(
                PFX + "SELECT ?a WHERE { :alice :age ?a }", JSON), "a")).isEqualTo(31);
        } finally {
            engine().executeUpdate(PFX + """
                DELETE { :alice :age 31 }
                INSERT { :alice :age 30 }
                WHERE  { :alice :age 31 }""");
        }
    }

    @Test
    @SparqlCapabilityTest(UPDATE_CLEAR)
    void updateClear_namedGraph_becomesEmpty() throws Exception {
        engine().executeUpdate(PFX + "CLEAR GRAPH :graph1");
        try {
            var result = engine().executeSelect(PFX +
                "SELECT ?a ?b WHERE { GRAPH :graph1 { ?a ?b ?c } }", JSON);
            assertThat(result.rowCount()).isEqualTo(0);
        } finally {
            engine().executeUpdate(PFX + "INSERT DATA { GRAPH :graph1 { :alice :knows :bob } }");
        }
    }

    @Test
    @SparqlCapabilityTest(UPDATE_DROP)
    void updateDrop_namedGraph_canBeRecreated() throws Exception {
        engine().executeUpdate(PFX + "DROP GRAPH :graph2");
        try {
            var result = engine().executeSelect(PFX +
                "SELECT ?a ?b WHERE { GRAPH :graph2 { ?a ?b ?c } }", JSON);
            assertThat(result.rowCount()).isEqualTo(0);
        } finally {
            engine().executeUpdate(PFX + "INSERT DATA { GRAPH :graph2 { :bob :knows :carol } }");
        }
    }
}
