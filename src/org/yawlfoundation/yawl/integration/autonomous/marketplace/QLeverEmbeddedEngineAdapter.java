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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverResult;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;

import java.util.Objects;

/**
 * Adapter that wraps {@link QLeverEmbeddedSparqlEngine} to implement {@link SparqlEngine}.
 *
 * <p><strong>IMPORTANT:</strong> QLever is an embedded Java/C++ FFI bridge.
 * This adapter wraps the in-process engine - there is NO Docker container,
 * NO HTTP service, and NO localhost:7001 endpoint.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * QLeverEmbeddedSparqlEngine embedded = new QLeverEmbeddedSparqlEngine();
 * embedded.initialize();
 * SparqlEngine engine = new QLeverEmbeddedEngineAdapter(embedded);
 * String turtle = engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 10");
 * }</pre>
 *
 * @since YAWL 6.0
 */
public final class QLeverEmbeddedEngineAdapter implements SparqlEngine {

    private final QLeverEmbeddedSparqlEngine embeddedEngine;

    /**
     * Creates an adapter wrapping the given embedded QLever engine.
     *
     * @param embeddedEngine the embedded QLever engine (must be initialized)
     * @throws NullPointerException if embeddedEngine is null
     */
    public QLeverEmbeddedEngineAdapter(QLeverEmbeddedSparqlEngine embeddedEngine) {
        this.embeddedEngine = Objects.requireNonNull(embeddedEngine, "embeddedEngine must not be null");
    }

    @Override
    public String constructToTurtle(String constructQuery) throws SparqlEngineException {
        Objects.requireNonNull(constructQuery, "constructQuery must not be null");

        if (!isAvailable()) {
            throw new SparqlEngineUnavailableException(engineType(), "Embedded engine not initialized");
        }

        try {
            QLeverResult result = embeddedEngine.executeQuery(constructQuery);

            // QLeverResult must contain data - if null, this is an error
            if (result == null) {
                throw new SparqlEngineException("QLever returned null result for query");
            }

            String data = result.data();
            if (data == null) {
                throw new SparqlEngineException("QLever returned null data for query - result may be malformed");
            }

            return data;

        } catch (QLeverFfiException e) {
            throw new SparqlEngineException("QLever query failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return embeddedEngine != null && embeddedEngine.isInitialized();
    }

    @Override
    public String engineType() {
        return "qlever-embedded";
    }

    @Override
    public void close() {
        try {
            embeddedEngine.shutdown();
        } catch (QLeverFfiException e) {
            // Ignore shutdown errors
        }
    }
}
