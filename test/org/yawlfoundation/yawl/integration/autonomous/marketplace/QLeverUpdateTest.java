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

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test SPARQL UPDATE operations via QLeverSparqlEngine.sparqlUpdate().
 *
 * <p>This test verifies that the QLeverSparqlEngine properly handles
 * SPARQL 1.1 Update operations including INSERT DATA, DELETE DATA,
 * and various error cases.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
@DisplayName("QLever SPARQL Update Operations")
public class QLeverUpdateTest {

    private QLeverSparqlEngine engine;

    @BeforeEach
    void setUp() {
        engine = new QLeverSparqlEngine("http://localhost:19877"); // Unused port
    }

    @Test
    @DisplayName("INSERT DATA operation")
    void insertDataOperation() {
        String insertQuery = "INSERT DATA { <http://example.org/test> <http://example.org/pred> <http://example.org/obj> }";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(insertQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("DELETE DATA operation")
    void deleteDataOperation() {
        String deleteQuery = "DELETE DATA { <http://example.org/test> <http://example.org/pred> <http://example.org/obj> }";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(deleteQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("INSERT WHERE operation")
    void insertWhereOperation() {
        String insertQuery = "INSERT { ?s <http://example.org/pred> <http://example.org/obj> } WHERE { ?s <http://example.org/pred> ?o }";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(insertQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("DELETE WHERE operation")
    void deleteWhereOperation() {
        String deleteQuery = "DELETE { ?s <http://example.org/pred> ?o } WHERE { ?s <http://example.org/pred> ?o }";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(deleteQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("CLEAR operation")
    void clearOperation() {
        String clearQuery = "CLEAR DEFAULT";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(clearQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("DROP operation")
    void dropOperation() {
        String dropQuery = "DROP DEFAULT";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(dropQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("CREATE operation")
    void createOperation() {
        String createQuery = "CREATE GRAPH <http://example.org/newgraph>";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(createQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    @DisplayName("null update query throws NullPointerException")
    void nullUpdateQueryThrowsNullPointerException() {
        assertThatThrownBy(() -> engine.sparqlUpdate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("updateQuery must not be null");
    }

    @Test
    @DisplayName("empty update query string is processed")
    void emptyUpdateQueryStringIsProcessed() {
        assertThatThrownBy(() -> engine.sparqlUpdate(""))
                .isInstanceOf(SparqlEngineException.class)
                .or()
                .isInstanceOf(SparqlEngineUnavailableException.class);
    }

    @Test
    @DisplayName("invalid update syntax throws SparqlEngineException")
    void invalidUpdateSyntaxThrowsSparqlEngineException() {
        String invalidQuery = "INVALID UPDATE SYNTAX";
        
        assertThatThrownBy(() -> engine.sparqlUpdate(invalidQuery))
                .isInstanceOf(SparqlEngineException.class)
                .hasMessageContaining("failed")
                .or()
                .isInstanceOf(SparqlEngineUnavailableException.class);
    }

    @Test
    @DisplayName("multiple update operations don't interfere")
    void multipleUpdateOperationsDontInterfere() {
        String update1 = "INSERT DATA { <http://example.org/test1> <http://example.org/pred> <http://example.org/obj1> }";
        String update2 = "INSERT DATA { <http://example.org/test2> <http://example.org/pred> <http://example.org/obj2> }";
        
        // Both should fail with unavailable exception
        assertThatThrownBy(() -> engine.sparqlUpdate(update1))
                .isInstanceOf(SparqlEngineUnavailableException.class);
        
        assertThatThrownBy(() -> engine.sparqlUpdate(update2))
                .isInstanceOf(SparqlEngineUnavailableException.class);
    }

    @Test
    @DisplayName("update operation is not available when engine is down")
    void updateOperationIsNotAvailableWhenEngineIsDown() {
        assertThat(engine.isAvailable()).isFalse();
        
        String updateQuery = "INSERT DATA { <http://example.org/test> <http://example.org/pred> <http://example.org/obj> }";
        assertThatThrownBy(() -> engine.sparqlUpdate(updateQuery))
                .isInstanceOf(SparqlEngineUnavailableException.class);
    }
}
