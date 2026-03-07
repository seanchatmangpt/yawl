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

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.RDF;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.qlever.QLeverResult;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Validates process actions against workflow ordering constraints using a dual-engine
 * design: Apache Jena handles simple O(1) constraint triple lookups; QLever handles
 * complex multi-hop SPARQL queries (property paths, JOINs, aggregates) when wired in.
 *
 * <p><strong>Engine selection</strong>:
 * <ul>
 *   <li>Jena — always present; handles {@code constraint:precedes} and
 *       {@code constraint:requiresBefore} triples via direct model queries.</li>
 *   <li>QLever — optional; wired via {@link #enableQLever(QLeverEmbeddedSparqlEngine)};
 *       used for {@link #queryComplexConstraints(String)}. Falls back to Jena on
 *       {@link QLeverFfiException}.</li>
 * </ul>
 *
 * <p><strong>Thread safety</strong>: all public methods acquire {@code modelLock}
 * before mutating or reading shared state. {@code synchronized} is never used so
 * virtual threads are never pinned to carrier threads.
 *
 * @author YAWL Foundation
 * @version 7.0.0
 * @since 6.0.0
 */
public class ProcessConstraintModel {

    private final Model model;
    private final ReentrantLock modelLock = new ReentrantLock();

    /** Lazily supplied; {@code null} when QLever is not configured. */
    private QLeverEmbeddedSparqlEngine qleverEngine;

    private static final String CONSTRAINT_NS = "http://yawl.org/constraints/";
    private static final Property PRECEDES = ResourceFactory.createProperty(
        CONSTRAINT_NS, "precedes");
    private static final Property REQUIRES_BEFORE = ResourceFactory.createProperty(
        CONSTRAINT_NS, "requiresBefore");

    /**
     * Construct an empty constraint model with Jena only.
     * Call {@link #enableQLever(QLeverEmbeddedSparqlEngine)} to enable the
     * QLever engine for complex SPARQL queries.
     */
    public ProcessConstraintModel() {
        this.model = ModelFactory.createDefaultModel();
        model.setNsPrefix("constraint", CONSTRAINT_NS);
        this.qleverEngine = null;
    }

    // -------------------------------------------------------------------------
    // QLever wiring
    // -------------------------------------------------------------------------

    /**
     * Wire a pre-initialised QLever engine into this constraint model.
     *
     * <p>The engine must already be initialised (i.e. {@code engine.initialize()}
     * has been called before this method). After wiring, any RDF triples currently
     * in the Jena model are immediately synchronised into QLever so that complex
     * constraint queries reflect the current state.
     *
     * @param engine initialised {@link QLeverEmbeddedSparqlEngine}, not null
     * @throws IllegalArgumentException if engine is null
     * @throws QLeverFfiException       if the initial RDF sync to QLever fails
     */
    public void enableQLever(QLeverEmbeddedSparqlEngine engine) throws QLeverFfiException {
        if (engine == null) {
            throw new IllegalArgumentException(
                "QLeverEmbeddedSparqlEngine must not be null. " +
                "Call engine.initialize() before passing it here."
            );
        }
        modelLock.lock();
        try {
            this.qleverEngine = engine;
            syncJenaToQLever();
        } finally {
            modelLock.unlock();
        }
    }

    /**
     * Returns true if a QLever engine has been wired into this constraint model.
     */
    public boolean isQLeverEnabled() {
        modelLock.lock();
        try {
            return qleverEngine != null;
        } finally {
            modelLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Population
    // -------------------------------------------------------------------------

    /**
     * Populate constraint model from workflow specification.
     *
     * <p>Creates RDF triples in the Jena model representing task ordering constraints.
     * If QLever is enabled, the new triples are synchronised into QLever; a sync
     * failure disables QLever for the lifetime of this model (Jena remains active).
     *
     * @param specId    workflow specification ID
     * @param taskNames list of task names in specification
     */
    public void populateFrom(String specId, List<String> taskNames) {
        modelLock.lock();
        try {
            Resource specResource = model.createResource(CONSTRAINT_NS + specId);
            specResource.addProperty(RDF.type, ResourceFactory.createResource(
                CONSTRAINT_NS + "Specification"));

            for (String taskName : taskNames) {
                Resource taskResource = model.createResource(
                    CONSTRAINT_NS + specId + "/" + taskName);
                taskResource.addProperty(RDF.type, ResourceFactory.createResource(
                    CONSTRAINT_NS + "Task"));
                specResource.addProperty(REQUIRES_BEFORE, taskResource);
            }

            if (qleverEngine != null) {
                try {
                    syncJenaToQLever();
                } catch (QLeverFfiException e) {
                    // QLever sync failed — disable QLever; Jena remains the authority.
                    // Callers query constraint feasibility via Jena even without QLever.
                    qleverEngine = null;
                }
            }
        } finally {
            modelLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Constraint checking — Jena (fast path)
    // -------------------------------------------------------------------------

    /**
     * Check if an action violates ordering constraints using the Jena model.
     *
     * <p>For {@link RerouteAction}: checks that {@code toTaskName} is not required
     * before {@code fromTaskName}. All other action types are always feasible.
     *
     * @param action action to validate
     * @return true if action is feasible
     */
    public boolean isFeasible(ProcessAction action) {
        modelLock.lock();
        try {
            if (action instanceof RerouteAction ra) {
                List<String> predecessors = getTaskPrecedencesUnlocked(ra.toTaskName());
                return !predecessors.contains(ra.fromTaskName());
            }
            return true;
        } finally {
            modelLock.unlock();
        }
    }

    /**
     * Get list of tasks that must precede a given task, using the Jena SPARQL engine.
     *
     * @param taskName task to analyse
     * @return list of predecessor task names (never null)
     */
    public List<String> getTaskPrecedences(String taskName) {
        modelLock.lock();
        try {
            return getTaskPrecedencesUnlocked(taskName);
        } finally {
            modelLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Complex constraint queries — QLever (with Jena fallback)
    // -------------------------------------------------------------------------

    /**
     * Execute an arbitrary SPARQL query against the constraint knowledge graph.
     *
     * <p>When QLever is enabled, the query runs on the embedded QLever engine
     * (supporting SPARQL property paths, multi-hop JOINs, aggregates). If QLever
     * throws or is not enabled, the query is executed against the Jena ARQ engine
     * as a fallback.
     *
     * <p>Example — transitive reachability (only expressible via property paths):
     * <pre>{@code
     * QLeverResult result = model.queryComplexConstraints("""
     *     PREFIX c: <http://yawl.org/constraints/>
     *     SELECT ?task WHERE {
     *         <http://yawl.org/constraints/spec-1/taskA> c:precedes+ ?task .
     *     }
     *     """);
     * }</pre>
     *
     * @param sparqlQuery SPARQL SELECT or ASK query string
     * @return {@link QLeverResult} with query data; never null
     * @throws IllegalArgumentException if sparqlQuery is null or blank
     */
    public QLeverResult queryComplexConstraints(String sparqlQuery) {
        if (sparqlQuery == null || sparqlQuery.isBlank()) {
            throw new IllegalArgumentException(
                "sparqlQuery must not be null or blank"
            );
        }
        modelLock.lock();
        try {
            if (qleverEngine != null) {
                try {
                    return qleverEngine.executeQuery(sparqlQuery);
                } catch (QLeverFfiException e) {
                    return fallbackJenaQuery(sparqlQuery);
                }
            }
            return fallbackJenaQuery(sparqlQuery);
        } finally {
            modelLock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Serialise the current Jena model to Turtle and load it into QLever so that
     * complex SPARQL queries reflect the same triples as the Jena model.
     * Caller must hold {@code modelLock}.
     */
    private void syncJenaToQLever() throws QLeverFfiException {
        if (model.isEmpty()) {
            return;
        }
        StringWriter writer = new StringWriter();
        model.write(writer, "TURTLE");
        String turtle = writer.toString();
        if (!turtle.isBlank()) {
            qleverEngine.loadRdfData(turtle, "TURTLE");
        }
    }

    /**
     * Execute SPARQL against the Jena ARQ engine and wrap the result as a
     * {@link QLeverResult} so callers always get the same return type.
     * Caller must hold {@code modelLock}.
     */
    private QLeverResult fallbackJenaQuery(String sparqlQuery) {
        var query = org.apache.jena.query.QueryFactory.create(sparqlQuery);
        var qexec = org.apache.jena.query.QueryExecutionFactory.create(query, model);
        try {
            var results = qexec.execSelect();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (results.hasNext()) {
                if (!first) sb.append(",");
                first = false;
                var soln = results.nextSolution();
                sb.append("{");
                soln.varNames().forEachRemaining(v -> {
                    var node = soln.get(v);
                    sb.append("\"").append(v).append("\":\"")
                      .append(node != null ? node.toString() : "")
                      .append("\"");
                });
                sb.append("}");
            }
            sb.append("]");
            return QLeverResult.success(sb.toString(), "fallback=jena");
        } finally {
            qexec.close();
        }
    }

    /**
     * Jena-backed predecessor query. Caller must hold {@code modelLock}.
     */
    private List<String> getTaskPrecedencesUnlocked(String taskName) {
        List<String> predecessors = new ArrayList<>();
        String sparql = """
            PREFIX constraint: <http://yawl.org/constraints/>
            SELECT ?predecessor
            WHERE {
                ?predecessor constraint:precedes <http://yawl.org/constraints/%s> .
            }
            """.formatted(taskName);

        var query = org.apache.jena.query.QueryFactory.create(sparql);
        var qexec = org.apache.jena.query.QueryExecutionFactory.create(query, model);
        var results = qexec.execSelect();

        while (results.hasNext()) {
            var solution = results.nextSolution();
            Resource predRes = solution.getResource("predecessor");
            if (predRes != null) {
                predecessors.add(predRes.getLocalName());
            }
        }

        qexec.close();
        return predecessors;
    }
}
