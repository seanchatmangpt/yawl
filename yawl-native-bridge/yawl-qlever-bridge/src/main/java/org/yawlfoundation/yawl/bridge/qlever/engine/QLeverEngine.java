/**
 * QLeverEngine - Pure Java Domain API Interface
 *
 * This interface provides a high-level, type-safe API for interacting with
 * QLever. It abstracts away the Panama FFI details and provides familiar
 * Java patterns for executing SPARQL queries.
 *
 * <p>Basic usage:
 * <pre>{@code
 * // Create an engine instance
 * try (QLeverEngine engine = QLeverEngine.create(indexPath)) {
 *
 *     // Execute ASK queries
 *     AskResult askResult = engine.ask("ASK { ?s ?p ?o }");
 *     if (askResult.isTrue()) {
 *         System.out.println("Query has results");
 *     }
 *
 *     // Execute SELECT queries
 *     SelectResult selectResult = engine.select("SELECT ?s WHERE { ?s ?p ?o }");
 *     for (int i = 0; i < selectResult.getRowCount(); i++) {
 *         String subject = selectResult.getValue(i, 0);
 *         System.out.println("Found: " + subject);
 *     }
 *
 *     // Execute CONSTRUCT queries
 *     ConstructResult constructResult = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
 *     System.out.println(constructResult.getTurtleResult());
 * }
 * }</pre>
 */
public interface QLeverEngine extends AutoCloseable {

    /**
     * Creates a new QLever engine instance
     *
     * @param indexPath Path to the QLever index directory
     * @return A new QLever engine instance
     * @throws QleverRuntimeException if the engine cannot be initialized
     */
    static QLeverEngine create(String indexPath) {
        return QLeverEngineImpl.create(indexPath);
    }

    /**
     * Creates a new QLever engine instance with custom options
     *
     * @param indexPath Path to the QLever index directory
     * @param timeoutMs Query timeout in milliseconds (0 for no timeout)
     * @return A new QLever engine instance
     * @throws QleverRuntimeException if the engine cannot be initialized
     */
    static QLeverEngine create(String indexPath, int timeoutMs) {
        return QLeverEngineImpl.create(indexPath, timeoutMs);
    }

    /**
     * Executes a SPARQL ASK query
     *
     * @param query The SPARQL ASK query to execute
     * @return An AskResult containing the boolean answer
     * @throws QleverRuntimeException if the query cannot be executed
     */
    AskResult ask(String query);

    /**
     * Executes a SPARQL SELECT query
     *
     * @param query The SPARQL SELECT query to execute
     * @return A SelectResult containing the tabular data
     * @throws QleverRuntimeException if the query cannot be executed
     */
    SelectResult select(String query);

    /**
     * Executes a SPARQL CONSTRUCT query
     *
     * @param query The SPARQL CONSTRUCT query to execute
     * @return A ConstructResult containing the RDF graph
     * @throws QleverRuntimeException if the query cannot be executed
     */
    ConstructResult construct(String query);

    /**
     * Validates a SPARQL query without executing it
     *
     * @param query The SPARQL query to validate
     * @return true if the query is valid, false otherwise
     * @throws QleverRuntimeException if validation fails due to system errors
     */
    boolean validateQuery(String query);

    /**
     * Gets the version information from the QLever engine
     *
     * @return Version string
     * @throws QleverRuntimeException if version information cannot be retrieved
     */
    String getVersion();

    /**
     * Gets the index path used by this engine
     *
     * @return The index path
     */
    String getIndexPath();

    /**
     * Checks if the engine is still open and operational
     *
     * @return true if the engine is open, false if closed
     */
    boolean isOpen();

    /**
     * Closes the engine and releases all native resources
     *
     * @throws QleverRuntimeException if the engine cannot be closed
     */
    @Override
    void close();
}