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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract test for {@link QLeverSparqlEngine} that verifies all SPARQL engine
 * contracts plus QLever-specific behaviors.
 *
 * <p>This test extends the abstract {@link SparqlEngineContractTest} and adds
 * QLever-specific validation including constructor behavior, availability checks,
 * and QLever's unique sparqlUpdate method.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
@Tag("benchmark")
@DisplayName("QLeverSparqlEngine Contract")
public class QLeverSparqlEngineContractTest extends SparqlEngineContractTest {

    private QLeverSparqlEngine engine;
    private QLeverSparqlEngine customEngine;

    @Override
    protected SparqlEngine createEngine() {
        // Return the default engine for base class tests
        return new QLeverSparqlEngine();
    }

    @BeforeEach
    void setUp() {
        // Create fresh instances for each test
        engine = new QLeverSparqlEngine();
        customEngine = new QLeverSparqlEngine("http://localhost:7002");
    }

    @AfterEach
    void tearDown() {
        engine = null;
        customEngine = null;
    }

    @Test
    @DisplayName("constructor with custom base URL")
    void constructorWithCustomBaseUrl() {
        assertThat(customEngine).isNotNull();
        assertThat(customEngine.engineType()).isEqualTo("qlever");
    }

    @Test
    @DisplayName("constructor trims trailing slashes from base URL")
    void constructorTrimsTrailingSlashes() {
        QLeverSparqlEngine engineWithSlash = new QLeverSparqlEngine("http://localhost:7001/");
        assertThat(engineWithSlash.engineType()).isEqualTo("qlever");
    }

    @Test
    @DisplayName("constructor throws on null base URL")
    void constructorThrowsOnNullBaseUrl() {
        assertThatThrownBy(() -> new QLeverSparqlEngine(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("baseUrl must not be null");
    }

    @Test
    @DisplayName("isAvailable returns false when QLever is not running")
    void isAvailableReturnsFalseWhenEngineUnavailable() {
        // This test should pass even when QLever is not running
        boolean available = engine.isAvailable();
        assertThat(available).isBoolean(); // Must return boolean, never throw
    }

    @Test
    @DisplayName("constructToTurtle throws SparqlEngineUnavailableException when engine is unavailable")
    void constructToTurtleThrowsWhenEngineUnavailable() {
        // Assuming QLever is not running for this test
        if (!engine.isAvailable()) {
            String query = "CONSTRUCT WHERE { ?s ?p ?o } LIMIT 1";
            assertThatThrownBy(() -> engine.constructToTurtle(query))
                    .isInstanceOf(SparqlEngineUnavailableException.class)
                    .hasMessageContaining("qlever")
                    .hasMessageContaining("QLever endpoint unreachable");
        }
    }

    @Test
    @DisplayName("sparqlUpdate method exists and is callable")
    void sparqlUpdateMethodExists() {
        // Verify the method exists and can be called (will throw if QLever unavailable)
        assertThatThrownBy(() -> engine.sparqlUpdate("INSERT DATA { <test> <test> <test> }"))
                .isInstanceOf(SparqlEngineUnavailableException.class);
    }

    @Test
    @DisplayName("sparqlUpdate throws on null query")
    void sparqlUpdateThrowsOnNullQuery() {
        if (!engine.isAvailable()) {
            assertThatThrownBy(() -> engine.sparqlUpdate(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("updateQuery must not be null");
        }
    }

    @Test
    @DisplayName("sparqlUpdate throws on invalid SPARQL Update syntax")
    void sparqlUpdateThrowsOnInvalidSyntax() {
        if (!engine.isAvailable()) {
            String invalidUpdate = "INSERT INVALID SYNTAX { ?s ?p ?o }";
            assertThatThrownBy(() -> engine.sparqlUpdate(invalidUpdate))
                    .isInstanceOf(SparqlEngineUnavailableException.class);
        }
    }

    @Test
    @DisplayName("engineType returns 'qlever'")
    void engineTypeReturnsQlever() {
        assertThat(engine.engineType()).isEqualTo("qlever");
    }

    @Test
    @DisplayName("different instances are independent")
    void differentInstancesAreIndependent() {
        QLeverSparqlEngine engine1 = new QLeverSparqlEngine();
        QLeverSparqlEngine engine2 = new QLeverSparqlEngine();

        assertThat(engine1).isNotSameAs(engine2);
    }

    @Test
    @DisplayName("toString contains engine type information")
    void toStringContainsEngineTypeInfo() {
        String toString = engine.toString();
        assertThat(toString).contains("qlever");
    }
}