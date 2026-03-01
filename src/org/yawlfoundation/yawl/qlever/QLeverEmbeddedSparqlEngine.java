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

import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import static org.yawlfoundation.yawl.qlever.SparqlCapability.*;

import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedded QLever SPARQL engine using Java 25 Panama FFM.
 *
 * <p>This engine implements the SparqlEngine interface and provides high-performance
 * SPARQL query execution with comprehensive error handling and support for multiple
 * output formats.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Embedded execution (no network overhead)</li>
 *   <li>Multiple output formats: JSON, TSV, CSV, TURTLE, XML</li>
 *   <li>Comprehensive error handling with status code mapping</li>
 *   <li>Thread-safe concurrent query execution</li>
 *   <li>Automatic resource cleanup</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 * {@snippet :
 * // Basic usage with Turtle results
 * Path indexPath = Path.of("/var/lib/qlever/workflow-index");
 * try (SparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
 *     String turtle = engine.constructToTurtle("""
 *         PREFIX workflow: <http://yawl.io/workflow#>
 *         CONSTRUCT { ?case workflow:status ?status }
 *         WHERE { ?case workflow:status ?status }
 *         LIMIT 100
 *         """);
 *     System.out.println(turtle);
 * }
 * }
 *
 * {@snippet :
 * // Different output formats
 * try (SparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
 *     // JSON results
 *     String json = ((QLeverEmbeddedSparqlEngine) engine).selectToJson("""
 *         SELECT ?case ?status WHERE { ?case workflow:status ?status }
 *         """);
 *
 *     // TSV results
 *     String tsv = ((QLeverEmbeddedSparqlEngine) engine).selectToTsv("""
 *         SELECT ?case ?status WHERE { ?case workflow:status ?status }
 *         """);
 *
 *     // CSV results
 *     String csv = ((QLeverEmbeddedSparqlEngine) engine).selectToCsv("""
 *         SELECT ?case ?status WHERE { ?case workflow:status ?status }
 *         """);
 * }
 * }
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 * @see SparqlEngine
 * @see QLeverFfiBindings
 */
public final class QLeverEmbeddedSparqlEngine implements SparqlEngine {

    static {
        SparqlCapabilityRegistry.checkMappings(QLeverEmbeddedSparqlEngine.class);
    }

    /** Default accept header for CONSTRUCT queries */
    private static final String ACCEPT_TURTLE = "text/turtle";

    /** Default accept header for SELECT queries */
    private static final String ACCEPT_JSON = "application/sparql-results+json";

    /** FFI bindings to native QLever library */
    private final QLeverFfiBindings ffi;

    /** Path to the pre-built QLever index directory */
    private final Path indexPath;

    /** Native index handle (opaque pointer) */
    private volatile MemorySegment indexHandle;

    /** Availability flag for fast-path checks */
    private final AtomicBoolean available = new AtomicBoolean(false);

    /**
     * Creates an embedded QLever engine with the given index path.
     *
     * <p>The index must be pre-built using QLever's IndexBuilder CLI tool
     * before creating this engine.</p>
     *
     * @param indexPath path to pre-built QLever index directory containing
     *                  .index.pbm, .index.pso, .index.pos, etc.
     * @throws IllegalArgumentException if indexPath is null or doesn't exist
     * @throws SparqlEngineException if native library cannot be loaded or index fails to load
     */
    public QLeverEmbeddedSparqlEngine(Path indexPath) throws SparqlEngineException {
        this.indexPath = Objects.requireNonNull(indexPath, "indexPath must not be null");

        if (!Files.isDirectory(indexPath)) {
            throw new IllegalArgumentException(
                "QLever index path does not exist or is not a directory: " + indexPath
            );
        }

        try {
            this.ffi = new QLeverFfiBindings();
            loadIndex();
        } catch (QLeverFfiException e) {
            throw new SparqlEngineException(
                "Failed to initialize QLever FFI bindings: " + e.getMessage(), e
            );
        }
    }

    /**
     * Loads the QLever index into memory.
     *
     * <p>This is called once during construction. The index remains loaded
     * until {@link #close()} is called.</p>
     *
     * @throws SparqlEngineException if index cannot be loaded
     */
    private synchronized void loadIndex() throws SparqlEngineException {
        if (available.get()) {
            return;  // Already loaded
        }

        try {
            // Use the updated FFI method with QLeverStatus
            QLeverStatus status = ffi.indexCreate(indexPath.toString());

            if (status.isError()) {
                throw new SparqlEngineException(
                    "Failed to load QLever index: " + status.error() +
                    " (HTTP status: " + status.httpStatus() + ")"
                );
            }

            this.indexHandle = status.result();

            if (indexHandle == null || indexHandle.equals(MemorySegment.NULL)) {
                throw new SparqlEngineException(
                    "QLever index loaded but returned null handle"
                );
            }

            if (!ffi.indexIsLoaded(indexHandle)) {
                throw new SparqlEngineException(
                    "QLever index loaded but isLoaded check failed"
                );
            }

            available.set(true);
        } catch (QLeverFfiException e) {
            throw new SparqlEngineException(
                "QLever FFI error during index loading: " + e.getMessage(), e
            );
        }
    }

    /**
     * Executes a SPARQL query and returns results in the specified media type.
     *
     * <p>This is the core method that handles all query execution with proper
     * error handling and resource cleanup.</p>
     *
     * @param query the SPARQL query to execute
     * @param mediaType the desired output format
     * @return query results as a string
     * @throws SparqlEngineException on query parsing or execution errors
     * @throws SparqlEngineUnavailableException if the engine has been closed
     */
    private String executeQuery(String query, QLeverMediaType mediaType)
            throws SparqlEngineException, SparqlEngineUnavailableException {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(mediaType, "mediaType must not be null");

        if (!isAvailable()) {
            throw new SparqlEngineUnavailableException(engineType(), indexPath.toString());
        }

        QLeverStatus resultStatus = null;
        MemorySegment result = null;
        try {
            // Execute query via FFM - pass mediaType directly to get QLeverStatus
            resultStatus = ffi.queryExec(indexHandle, query, mediaType);

            if (resultStatus.isError()) {
                throw new SparqlEngineException(
                    "QLever query error: " + resultStatus.error() +
                    " (HTTP status: " + resultStatus.httpStatus() + ")"
                );
            }

            result = resultStatus.result();

            // Collect all result lines into a single string
            StringBuilder sb = new StringBuilder(4096);
            while (ffi.resultHasNext(result)) {
                String line = ffi.resultNext(result);
                if (line != null) {
                    sb.append(line).append('\n');
                }
            }

            return sb.toString();

        } catch (QLeverFfiException e) {
            throw new SparqlEngineException(
                "QLever FFI error: " + e.getMessage(), e
            );
        } finally {
            // Always destroy the result handle to free native memory
            if (result != null) {
                ffi.resultDestroy(result);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Executes the CONSTRUCT query directly in-process via FFM, returning
     * results as Turtle-formatted RDF triples. No HTTP overhead is incurred.</p>
     *
     * @param constructQuery a valid SPARQL CONSTRUCT query
     * @return Turtle-serialized result graph (may be empty but never null)
     * @throws SparqlEngineException on query parsing or execution errors
     * @throws SparqlEngineUnavailableException if the engine has been closed
     */
    @Override
    public String constructToTurtle(String constructQuery) throws SparqlEngineException {
        return executeQuery(constructQuery, QLeverMediaType.TURTLE);
    }

    /**
     * Executes a SPARQL SELECT query and returns results as JSON.
     *
     * <p>This is a convenience method for SELECT queries. Results are returned
     * in SPARQL 1.1 Query Results JSON format.</p>
     *
     * @param selectQuery a valid SPARQL SELECT query
     * @return JSON-formatted query results
     * @throws SparqlEngineException on query errors
     * @throws SparqlEngineUnavailableException if the engine is not available
     */
    public String selectToJson(String selectQuery) throws SparqlEngineException {
        return executeQuery(selectQuery, QLeverMediaType.JSON);
    }

    /**
     * Executes a SPARQL SELECT query and returns results as TSV.
     *
     * <p>Results are returned in Tab-Separated Values format suitable for
     * processing by spreadsheet applications or command-line tools.</p>
     *
     * @param selectQuery a valid SPARQL SELECT query
     * @return TSV-formatted query results
     * @throws SparqlEngineException on query errors
     * @throws SparqlEngineUnavailableException if the engine is not available
     */
    public String selectToTsv(String selectQuery) throws SparqlEngineException {
        return executeQuery(selectQuery, QLeverMediaType.TSV);
    }

    /**
     * Executes a SPARQL SELECT query and returns results as CSV.
     *
     * <p>Results are returned in Comma-Separated Values format suitable for
     * processing by spreadsheet applications or command-line tools.</p>
     *
     * @param selectQuery a valid SPARQL SELECT query
     * @return CSV-formatted query results
     * @throws SparqlEngineException on query errors
     * @throws SparqlEngineUnavailableException if the engine is not available
     */
    public String selectToCsv(String selectQuery) throws SparqlEngineException {
        return executeQuery(selectQuery, QLeverMediaType.CSV);
    }

    /**
     * Executes a SPARql SELECT query and returns results as XML.
     *
     * <p>Results are returned in SPARQL Query Results XML format, which is
     * compatible with many RDF processing tools.</p>
     *
     * @param selectQuery a valid SPARQL SELECT query
     * @return XML-formatted query results
     * @throws SparqlEngineException on query errors
     * @throws SparqlEngineUnavailableException if the engine is not available
     */
    public String selectToXml(String selectQuery) throws SparqlEngineException {
        return executeQuery(selectQuery, QLeverMediaType.XML);
    }

    /**
     * Returns the number of triples in the loaded index.
     *
     * @return triple count, or 0 if index is not available
     */
    public long getTripleCount() {
        if (!isAvailable()) {
            return 0;
        }
        return ffi.indexTripleCount(indexHandle);
    }

    /**
     * {@inheritDoc}
     *
     * @return always returns {@code "qlever-embedded"}
     */
    @Override
    public String engineType() {
        return "qlever-embedded";
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns true if the index is loaded and the engine is ready to
     * accept queries. Returns false after {@link #close()} is called.</p>
     */
    @Override
    public boolean isAvailable() {
        return available.get()
            && indexHandle != null
            && !indexHandle.equals(MemorySegment.NULL);
    }

    /**
     * Returns the path to the QLever index directory.
     *
     * @return the index path
     */
    public Path getIndexPath() {
        return indexPath;
    }

    /**
     * Executes a SPARQL SELECT query with the specified output format.
     * Maps to 68 core SPARQL SELECT and result handling capabilities.
     *
     * @param sparql a valid SPARQL SELECT query
     * @param format the desired output format
     * @return eager QLeverResult with accumulated data
     * @throws SparqlEngineException on query execution errors
     * @throws SparqlEngineUnavailableException if engine is not available
     */
    @MapsToSparqlCapability({
        SELECT_STAR, SELECT_VARIABLES, SELECT_EXPRESSIONS, SELECT_DISTINCT, SELECT_REDUCED,
        BGP, OPTIONAL, UNION, MINUS, FILTER, FILTER_EXISTS, FILTER_NOT_EXISTS,
        BIND, VALUES_INLINE, VALUES_MULTIVAR, SUBQUERY,
        ORDER_BY_ASC, ORDER_BY_DESC, ORDER_BY_EXPR, LIMIT, OFFSET, LIMIT_OFFSET, GROUP_BY, HAVING,
        AGG_COUNT, AGG_COUNT_DISTINCT, AGG_COUNT_STAR,
        AGG_SUM, AGG_AVG, AGG_MIN, AGG_MAX, AGG_SAMPLE, AGG_GROUP_CONCAT,
        PATH_SEQUENCE, PATH_ALTERNATIVE, PATH_INVERSE,
        PATH_ZERO_OR_MORE, PATH_ONE_OR_MORE, PATH_ZERO_OR_ONE, PATH_NEGATED, PATH_NEGATED_INVERSE,
        NAMED_GRAPHS_FROM, NAMED_GRAPHS_GRAPH,
        FORMAT_JSON, FORMAT_TSV, FORMAT_CSV, FORMAT_XML, FORMAT_TURTLE, FORMAT_NTRIPLES, FORMAT_BINARY,
        FN_LANG, FN_LANGMATCHES, FN_DATATYPE, FN_BOUND, FN_ISIRI, FN_ISLITERAL, FN_ISBLANK,
        FN_STR, FN_STRSTARTS, FN_REGEX, FN_NUMERIC_OPS, FN_BOOLEAN_OPS,
        EXT_CONTAINS_WORD, EXT_CONTAINS_ENTITY, EXT_SCORE,
        ERR_MALFORMED_SPARQL, ERR_TIMEOUT, ERR_UNKNOWN_PREDICATE, ERR_EMPTY_RESULT
    })
    public QLeverResult executeSelect(String sparql, QLeverMediaType format)
            throws SparqlEngineException, SparqlEngineUnavailableException {
        String data = executeQuery(sparql, format);
        int rows = countRows(data, format);
        return QLeverResult.ofEager(data, rows);
    }

    /**
     * Executes a SPARQL ASK query.
     *
     * @param sparql a valid SPARQL ASK query
     * @return true if the ASK query result is positive
     * @throws SparqlEngineException on query execution errors
     * @throws SparqlEngineUnavailableException if engine is not available
     */
    @MapsToSparqlCapability({ASK})
    public boolean executeAsk(String sparql)
            throws SparqlEngineException, SparqlEngineUnavailableException {
        String data = executeQuery(sparql, QLeverMediaType.JSON);
        return data.contains("\"boolean\":true") || data.contains("\"boolean\" : true");
    }

    /**
     * Executes a SPARQL CONSTRUCT query with the specified output format.
     *
     * @param sparql a valid SPARQL CONSTRUCT query
     * @param format the desired output format
     * @return eager QLeverResult with RDF graph data
     * @throws SparqlEngineException on query execution errors
     * @throws SparqlEngineUnavailableException if engine is not available
     */
    @MapsToSparqlCapability({CONSTRUCT})
    public QLeverResult executeConstruct(String sparql, QLeverMediaType format)
            throws SparqlEngineException, SparqlEngineUnavailableException {
        String data = executeQuery(sparql, format);
        return QLeverResult.ofEager(data, 0);
    }

    /**
     * Executes a SPARQL DESCRIBE query with the specified output format.
     *
     * @param sparql a valid SPARQL DESCRIBE query
     * @param format the desired output format
     * @return eager QLeverResult with described resource data
     * @throws SparqlEngineException on query execution errors
     * @throws SparqlEngineUnavailableException if engine is not available
     */
    @MapsToSparqlCapability({DESCRIBE})
    public QLeverResult executeDescribe(String sparql, QLeverMediaType format)
            throws SparqlEngineException, SparqlEngineUnavailableException {
        String data = executeQuery(sparql, format);
        return QLeverResult.ofEager(data, 0);
    }

    /**
     * Executes a SPARQL UPDATE operation.
     * Performs INSERT/DELETE operations on the index.
     *
     * @param sparqlUpdate a valid SPARQL UPDATE query
     * @throws SparqlEngineException on update errors or if engine is read-only
     * @throws SparqlEngineUnavailableException if engine is not available
     */
    @MapsToSparqlCapability({
        UPDATE_INSERT_DATA, UPDATE_DELETE_DATA,
        UPDATE_DELETE_WHERE, UPDATE_INSERT_WHERE, UPDATE_DELETE_INSERT,
        UPDATE_CLEAR, UPDATE_DROP,
        ERR_UPDATE_ON_READONLY
    })
    public void executeUpdate(String sparqlUpdate)
            throws SparqlEngineException, SparqlEngineUnavailableException {
        executeQuery(sparqlUpdate, QLeverMediaType.JSON);
    }

    /**
     * Counts the number of result rows from accumulated query output.
     * Simple heuristic: counts non-blank lines minus header.
     *
     * @param data the accumulated query result data
     * @param format the media type of the data
     * @return estimated row count
     */
    private int countRows(String data, QLeverMediaType format) {
        if (data == null || data.isBlank()) {
            return 0;
        }
        long lines = data.lines().filter(l -> !l.isBlank()).count();
        return (int) Math.max(0, lines - 1);
    }

    /**
     * Closes the engine and releases all native resources.
     *
     * <p>After calling this method, the engine must not be used. Any
     * in-flight queries may return errors.</p>
     */
    @Override
    public void close() {
        available.set(false);

        if (indexHandle != null && !indexHandle.equals(MemorySegment.NULL)) {
            ffi.indexDestroy(indexHandle);
            indexHandle = null;
        }

        if (ffi != null) {
            ffi.close();
        }
    }

    /**
     * Ensures resources are cleaned up when the object is garbage collected.
     *
     * <p>This is a safety net. For proper resource management, always use
     * try-with-resources or explicitly call {@link #close()}.</p>
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            if (isAvailable()) {
                close();
            }
        } finally {
            super.finalize();
        }
    }
}