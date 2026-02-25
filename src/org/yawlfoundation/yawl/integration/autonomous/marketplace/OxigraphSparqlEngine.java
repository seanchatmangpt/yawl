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

import org.yawlfoundation.yawl.integration.autonomous.YawlNativeClient;
import org.yawlfoundation.yawl.integration.autonomous.YawlNativeException;

import java.util.Objects;

/**
 * {@link SparqlEngine} implementation backed by the Oxigraph store inside
 * the {@code yawl-native} Rust service.
 *
 * <p>Delegates all SPARQL operations to {@link YawlNativeClient}, which targets
 * the {@code /sparql/*} endpoints of the yawl-native service (default port 8083).
 * Unlike {@link QLeverSparqlEngine}, this engine supports mutable operations:
 * {@link #loadTurtle(String)} and {@link #sparqlUpdate(String)}.</p>
 *
 * <p>The {@link #isAvailable()} check hits {@code GET /sparql/health} and returns
 * {@code false} on connection refused — it never throws.</p>
 *
 * @since YAWL 6.0
 */
public final class OxigraphSparqlEngine implements SparqlEngine {

    private final YawlNativeClient client;
    private final String baseUrl;

    /**
     * Creates an engine pointing at the default yawl-native URL
     * ({@value YawlNativeClient#DEFAULT_BASE_URL}).
     */
    public OxigraphSparqlEngine() {
        this(YawlNativeClient.DEFAULT_BASE_URL);
    }

    /**
     * Creates an engine pointing at the given yawl-native base URL.
     *
     * @param baseUrl base URL of the yawl-native service
     */
    public OxigraphSparqlEngine(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.client = new YawlNativeClient(baseUrl);
    }

    @Override
    public String constructToTurtle(String constructQuery) throws SparqlEngineException {
        Objects.requireNonNull(constructQuery, "constructQuery must not be null");
        if (!isAvailable()) {
            throw new SparqlEngineUnavailableException(engineType(), baseUrl);
        }
        try {
            return client.constructToTurtle(constructQuery);
        } catch (YawlNativeException e) {
            throw new SparqlEngineException("Oxigraph CONSTRUCT failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load Turtle RDF into the engine's default graph.
     *
     * @param turtle valid Turtle RDF string
     * @throws SparqlEngineException on failure
     */
    public void loadTurtle(String turtle) throws SparqlEngineException {
        Objects.requireNonNull(turtle, "turtle must not be null");
        try {
            client.loadTurtle(turtle);
        } catch (YawlNativeException e) {
            throw new SparqlEngineException("loadTurtle failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load Turtle RDF into a named graph (or default graph if {@code graphName} is null).
     *
     * @param turtle    valid Turtle RDF string
     * @param graphName IRI of the target named graph, or null for the default graph
     * @throws SparqlEngineException on failure
     */
    public void loadTurtle(String turtle, String graphName) throws SparqlEngineException {
        Objects.requireNonNull(turtle, "turtle must not be null");
        try {
            client.loadTurtle(turtle, graphName);
        } catch (YawlNativeException e) {
            throw new SparqlEngineException("loadTurtle failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a SPARQL 1.1 Update (INSERT DATA, DELETE DATA, CLEAR, etc.).
     *
     * @param updateQuery a valid SPARQL 1.1 Update string
     * @throws SparqlEngineException on failure
     */
    public void sparqlUpdate(String updateQuery) throws SparqlEngineException {
        Objects.requireNonNull(updateQuery, "updateQuery must not be null");
        try {
            client.sparqlUpdate(updateQuery);
        } catch (YawlNativeException e) {
            throw new SparqlEngineException("sparqlUpdate failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return client.isSparqlAvailable();
    }

    @Override
    public String engineType() {
        return "oxigraph";
    }
}
