/*
 * QLever Engine Interface
 *
 * Pure Java 25 domain API for SPARQL operations.
 * Implements HYPER_STANDARDS: no mocks, real impl or throw UnsupportedOperationException.
 */

package org.yawlfoundation.yawl.bridge.qlever;

import java.util.List;
import java.util.Map;

/**
 * High-level interface for QLever SPARQL engine operations.
 * Provides type-safe methods for common SPARQL query patterns with automatic
 * resource management and proper error handling.
 *
 * @apiNote This interface should be implemented by QleverEngineImpl.
 *          All methods must either work with real native dependencies or throw
 *          UnsupportedOperationException with clear messages.
 */
public interface QleverEngine extends AutoCloseable {

    /**
     * Initialize the engine with the specified index directory
     *
     * @param indexPath Path to QLever index directory
     * @throws QleverException if initialization fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    void initialize(Path indexPath) throws QleverException;

    /**
     * Execute a SELECT query and return results as JSON
     *
     * @param sparql SELECT query string
     * @return JSON result string
     * @throws QleverException if query execution fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    String select(String sparql) throws QleverException;

    /**
     * Execute an ASK query and return boolean result
     *
     * @param sparql ASK query string
     * @return true if query matches, false otherwise
     * @throws QleverException if query execution fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    boolean ask(String sparql) throws QleverException;

    /**
     * Execute a CONSTRUCT query and return RDF/XML results
     *
     * @param sparql CONSTRUCT query string
     * @return RDF/XML result string
     * @throws QleverException if query execution fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    String construct(String sparql) throws QleverException;

    /**
     * Execute a DESCRIBE query and return RDF/XML results
     *
     * @param sparql DESCRIBE query string
     * @return RDF/XML result string
     * @throws QleverException if query execution fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    String describe(String sparql) throws QleverException;

    /**
     * Execute an UPDATE query for SPARQL 1.1 Update operations
     *
     * @param sparql Update query string
     * @return JSON update result summary
     * @throws QleverException if update execution fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    String update(String sparql) throws QleverException;

    /**
     * Validate SPARQL query syntax without execution
     *
     * @param sparql SPARQL query string to validate
     * @return true if valid, false if invalid
     * @throws QleverException if validation fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    boolean validateQuery(String sparql) throws QleverException;

    /**
     * Get the path to the current index
     *
     * @return Index path or null if not initialized
     */
    Path getIndexPath();

    /**
     * Check if the engine is initialized
     *
     * @return true if initialized, false otherwise
     */
    boolean isInitialized();

    /**
     * Get engine statistics and metadata
     *
     * @return Map of statistics
     */
    Map<String, Object> getStatistics();

    /**
     * Close the engine and release all resources
     *
     * @throws QleverException if shutdown fails
     * @throws UnsupportedOperationException if native dependencies are missing
     */
    @Override
    void close() throws QleverException;
}