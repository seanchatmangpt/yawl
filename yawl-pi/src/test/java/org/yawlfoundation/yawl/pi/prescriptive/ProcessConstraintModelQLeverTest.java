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

package org.yawlfoundation.yawl.pi.prescriptive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.qlever.QLeverResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the dual-engine ProcessConstraintModel: Jena + QLever.
 *
 * <p>The QLever native library is not available in CI, so tests exercise the
 * engine-wiring and fallback paths rather than real SPARQL-over-native-QLever
 * execution. The key property under test is:
 *
 * <ol>
 *   <li>{@link ProcessConstraintModel#enableQLever} wires the engine in.</li>
 *   <li>{@link ProcessConstraintModel#isQLeverEnabled} reflects the wired state.</li>
 *   <li>{@link ProcessConstraintModel#queryComplexConstraints} never returns null:
 *       it falls back to the Jena ARQ engine when QLever is unavailable or throws.</li>
 *   <li>An uninitialized QLever engine causes {@code queryComplexConstraints}
 *       to degrade gracefully to Jena (Chicago TDD: test real degradation paths).</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 7.0.0
 * @since 7.0.0
 */
class ProcessConstraintModelQLeverTest {

    private ProcessConstraintModel constraintModel;

    @BeforeEach
    void setUp() {
        constraintModel = new ProcessConstraintModel();
    }

    // -----------------------------------------------------------------------
    // enableQLever / isQLeverEnabled
    // -----------------------------------------------------------------------

    @Test
    void qleverDisabledByDefault() {
        assertFalse(constraintModel.isQLeverEnabled(),
            "QLever must be disabled until enableQLever() is called");
    }

    @Test
    void enableQLeverWithUninitializedEngine_emptyModel_wiresSuccessfully()
            throws QLeverFfiException {
        // An uninitialized engine is accepted when the Jena model is empty:
        // syncJenaToQLever() short-circuits on blank Turtle and never calls FFI.
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();

        constraintModel.enableQLever(engine);

        assertTrue(constraintModel.isQLeverEnabled(),
            "QLever must be enabled after enableQLever() with an empty Jena model");
    }

    @Test
    void enableQLeverWithNullEngine_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> constraintModel.enableQLever(null),
            "enableQLever(null) must throw IllegalArgumentException");
    }

    // -----------------------------------------------------------------------
    // queryComplexConstraints — fallback to Jena
    // -----------------------------------------------------------------------

    @Test
    void queryComplexConstraints_noQLever_returnsJenaResult() {
        // No QLever wired → pure Jena execution
        QLeverResult result = constraintModel.queryComplexConstraints("""
            PREFIX constraint: <http://yawl.org/constraints/>
            SELECT ?task WHERE { ?task a constraint:Task . }
            """);

        assertNotNull(result, "queryComplexConstraints must never return null");
        // Empty model → empty result set → still a valid QLeverResult
        assertNotNull(result.data(), "result.data() must not be null");
    }

    @Test
    void queryComplexConstraints_withUninitializedQLever_fallsBackToJena()
            throws QLeverFfiException {
        // Wire an engine that has NOT been initialize()d.
        // executeQuery() will throw QLeverFfiException → fallback to Jena.
        QLeverEmbeddedSparqlEngine uninitializedEngine = new QLeverEmbeddedSparqlEngine();
        constraintModel.enableQLever(uninitializedEngine);
        assertTrue(constraintModel.isQLeverEnabled());

        QLeverResult result = constraintModel.queryComplexConstraints("""
            PREFIX constraint: <http://yawl.org/constraints/>
            SELECT ?task WHERE { ?task a constraint:Task . }
            """);

        assertNotNull(result,
            "queryComplexConstraints must never return null even when QLever is unavailable");
        assertNotNull(result.data(),
            "Jena fallback must always produce a non-null data payload");
    }

    @Test
    void queryComplexConstraints_blankQuery_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> constraintModel.queryComplexConstraints("  "),
            "Blank query must throw IllegalArgumentException");
    }

    @Test
    void queryComplexConstraints_nullQuery_throws() {
        assertThrows(IllegalArgumentException.class,
            () -> constraintModel.queryComplexConstraints(null),
            "Null query must throw IllegalArgumentException");
    }

    // -----------------------------------------------------------------------
    // populateFrom + QLever degradation
    // -----------------------------------------------------------------------

    @Test
    void populateFrom_withUninitializedQLever_disablesQLeverKeepsJena()
            throws QLeverFfiException {
        // Wire an uninitialized engine, then populate with actual tasks.
        // Turtle will be non-blank → syncJenaToQLever() calls FFI → throws →
        // QLever is disabled; Jena model is intact.
        QLeverEmbeddedSparqlEngine uninitializedEngine = new QLeverEmbeddedSparqlEngine();
        constraintModel.enableQLever(uninitializedEngine);
        assertTrue(constraintModel.isQLeverEnabled());

        constraintModel.populateFrom("spec-1", List.of("task-a", "task-b"));

        // QLever was disabled by the failed sync
        assertFalse(constraintModel.isQLeverEnabled(),
            "QLever must be disabled after a sync failure in populateFrom()");

        // Jena model is still intact: getTaskPrecedences must work
        List<String> precedences = constraintModel.getTaskPrecedences("task-a");
        assertNotNull(precedences, "Jena precedences must still be queryable after QLever failure");
    }

    // -----------------------------------------------------------------------
    // Existing Jena behaviour is unaffected
    // -----------------------------------------------------------------------

    @Test
    void existingJenaBehaviour_emptyModelAllowsAnyReroute() {
        assertTrue(constraintModel.isFeasible(
            new RerouteAction("case-1", "task-a", "task-b", "test", 0.9)),
            "Empty model must allow any reroute");
    }

    @Test
    void existingJenaBehaviour_escalateAlwaysFeasible() {
        assertTrue(constraintModel.isFeasible(
            new EscalateAction("case-2", "task-a", "group", "test", 0.8)),
            "EscalateAction must always be feasible");
    }

    @Test
    void existingJenaBehaviour_precedencesEmptyOnFreshModel() {
        List<String> precedences = constraintModel.getTaskPrecedences("any-task");
        assertNotNull(precedences);
        assertTrue(precedences.isEmpty(),
            "Fresh model has no precedes triples → empty list");
    }
}
