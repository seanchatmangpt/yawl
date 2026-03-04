/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser
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
package org.yawlfoundation.yawl.nativebridge.qlever;

import java.util.List;
import java.util.Map;

/**
 * High-level SPARQL engine interface for in-process querying.
 * Sub-10ns latency via Panama FFM. No fault isolation needed.
 *
 * <p>This interface provides a pure Java 25 abstraction over the
 * native QLever SPARQL engine. All methods are thread-safe and
 * use scoped memory management through Arena.</p>
 */
public sealed interface QLeverEngine extends AutoCloseable {

    /**
     * Executes ASK query, returns boolean result.
     *
     * @param sparql ASK query string
     * @return query result
     * @throws QLeverException on query parse/runtime errors
     */
    boolean ask(String sparql) throws QLeverException;

    /**
     * SELECT query returning variable bindings.
     *
     * @param sparql SELECT query string
     * @return list of variable maps (key: variable name, value: binding)
     * @throws QLeverException on query errors
     */
    List<Map<String, String>> select(String sparql) throws QLeverException;

    /**
     * CONSTRUCT query returning RDF triples.
     *
     * @param sparql CONSTRUCT query string
     * @return list of triples (subject, predicate, object)
     * @throws QLeverException on query errors
     */
    List<Triple> construct(String sparql) throws QLeverException;

    /**
     * SPARQL UPDATE query.
     *
     * @param turtle Turtle data for updates
     * @throws QLeverException on update errors
     */
    void update(String turtle) throws QLeverException;

    /**
     * Gets the engine status information.
     *
     * @return current status
     */
    QLeverStatus getStatus();
}

/**
 * Represents an RDF triple.
 */
public record Triple(String subject, String predicate, String object) {
    public Triple {
        if (subject == null || predicate == null || object == null) {
            throw new IllegalArgumentException("Triple components cannot be null");
        }
    }
}

/**
 * Represents QLever engine status.
 */
public record QLeverStatus(int code, String message) {
    public static final int SUCCESS = 0;
    public static final int PARSE_ERROR = 1;
    public static final int SEMANTIC_ERROR = 2;
    public static final int RUNTIME_ERROR = 3;

    public boolean isSuccess() {
        return code == SUCCESS;
    }

    public boolean isParseError() {
        return code == PARSE_ERROR;
    }

    public boolean isSemanticError() {
        return code == SEMANTIC_ERROR;
    }

    public boolean isRuntimeError() {
        return code == RUNTIME_ERROR;
    }
}