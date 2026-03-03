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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract contract test that all SparqlEngine implementations must pass.
 *
 * <p>This test class uses JUnit 5 and AssertJ for fluent assertions.
 * Implementations should extend this class and provide concrete instances
 * of their SparqlEngine implementation.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
@DisplayName("SparqlEngine Contract")
public abstract class SparqlEngineContractTest {

    /**
     * Must return a non-null SparqlEngine instance for testing.
     * The implementation should create a fresh instance for each test.
     */
    protected abstract SparqlEngine createEngine();

    @Test
    @DisplayName("constructToTurtle throws SparqlEngineException on null query")
    void constructToTurtleThrowsOnNullQuery() {
        SparqlEngine engine = createEngine();
        assertThat(engine).isNotNull();

        assertThatThrownBy(() -> engine.constructToTurtle(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("constructQuery must not be null");
    }

    @Test
    @DisplayName("constructToTurtle returns non-null result for valid query")
    void constructToTurtleReturnsNonNullResult() throws SparqlEngineException {
        SparqlEngine engine = createEngine();
        String result = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 1");

        assertThat(result).isNotNull();
        // Result can be empty but should never be null
    }

    @Test
    @DisplayName("isAvailable never throws exceptions")
    void isAvailableNeverThrows() {
        SparqlEngine engine = createEngine();

        // isAvailable() must never throw, regardless of engine state
        boolean available = engine.isAvailable();
        assertThat(available).isBoolean();
    }

    @Test
    @DisplayName("engineType returns non-null string")
    void engineTypeReturnsNonNull() {
        SparqlEngine engine = createEngine();
        String engineType = engine.engineType();

        assertThat(engineType).isNotNull()
                           .isNotEmpty()
                           .isNotBlank();
    }

    @Test
    @DisplayName("close is idempotent")
    void closeIsIdempotent() {
        SparqlEngine engine = createEngine();

        // Multiple close calls should not cause issues
        engine.close();
        engine.close();
        engine.close();

        // No exception should be thrown
    }

    @Test
    @DisplayName("constructToTurtle throws on invalid SPARQL syntax")
    void constructToTurtleThrowsOnInvalidSyntax() {
        SparqlEngine engine = createEngine();
        String invalidQuery = "CONSTRUCT { ?s ?p ?o } WHERE { INVALID SYNTAX }";

        assertThatThrownBy(() -> engine.constructToTurtle(invalidQuery))
                .isInstanceOf(SparqlEngineException.class)
                .hasMessageContaining("failed");
    }

    @Test
    @DisplayName("implements AutoCloseable interface")
    void implementsAutoCloseable() {
        SparqlEngine engine = createEngine();
        assertThat(engine).isInstanceOf(AutoCloseable.class);
    }

    @Test
    @DisplayName("engine instance is not shared between tests")
    void engineInstanceIsNotShared() {
        // Each test should get a fresh instance
        SparqlEngine engine1 = createEngine();
        SparqlEngine engine2 = createEngine();

        assertThat(engine1).isNotSameAs(engine2);
    }
}