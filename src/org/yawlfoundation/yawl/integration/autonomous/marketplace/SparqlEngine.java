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

/**
 * Abstraction over a SPARQL 1.1 engine capable of executing CONSTRUCT queries.
 *
 * <p>Two implementations are provided:</p>
 * <ul>
 *   <li>{@link OxigraphSparqlEngine} — delegates to the {@code yawl-native} Rust service
 *       (Oxigraph embedded; supports load and update operations)</li>
 *   <li>{@link QLeverSparqlEngine} — read-only wrapper for a remote QLever endpoint</li>
 * </ul>
 *
 * <p>The marketplace never requires a {@code SparqlEngine}. All binding classes
 * ({@link MarketplaceMcpBinding}, {@link MarketplaceA2ABinding}) accept a
 * {@code null} engine and fall back to pure-Java constants.</p>
 *
 * @since YAWL 6.0
 */
public interface SparqlEngine extends AutoCloseable {

    /**
     * Execute a SPARQL 1.1 CONSTRUCT query and return the result as a Turtle string.
     *
     * @param constructQuery a valid SPARQL CONSTRUCT query
     * @return Turtle-serialised result graph (may be empty but never null)
     * @throws SparqlEngineException        on engine-level failure (query error, I/O)
     * @throws SparqlEngineUnavailableException if the engine is not reachable
     */
    String constructToTurtle(String constructQuery) throws SparqlEngineException;

    /**
     * Returns true if this engine is currently reachable.
     *
     * <p>Implementations must never throw — return {@code false} on any failure.</p>
     */
    boolean isAvailable();

    /**
     * A short identifier for this engine type, e.g. {@code "oxigraph"} or {@code "qlever"}.
     */
    String engineType();

    /** No-op by default; override to release resources if needed. */
    @Override
    default void close() {}
}
