package org.yawlfoundation.yawl.qlever;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Embedded QLever SPARQL engine wrapper with YAWL integration.
 * Provides a thread-safe interface to the QLever FFI bindings with additional
 * features like lifecycle management, YAWL context integration, and error recovery.
 */
@ThreadSafe
@SuppressWarnings("removal")
public final class QLeverEmbeddedSparqlEngine {

    private final QLeverFfiBindings ffiBindings;
    @GuardedBy("this")
    private boolean initialized = false;
    private final ScheduledExecutorService timeoutExecutor;
    private String workflowContext;
    private long queryTimeout = 30000; // Default 30 seconds
    private long memoryLimit = Long.MAX_VALUE;

    public QLeverEmbeddedSparqlEngine() {
        this.ffiBindings = new QLeverFfiBindings();
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    /**
     * Initializes the QLever engine.
     *
     * @throws QLeverFfiException if initialization fails
     */
    public synchronized void initialize() throws QLeverFfiException {
        if (initialized) {
            return;
        }

        ffiBindings.initializeEngine();
        initialized = true;
    }

    /**
     * Shuts down the QLever engine and cleans up resources.
     *
     * @throws QLeverFfiException if shutdown fails
     */
    public synchronized void shutdown() throws QLeverFfiException {
        if (!initialized) {
            return;
        }

        try {
            ffiBindings.shutdownEngine();
        } finally {
            timeoutExecutor.shutdown();
            initialized = false;
        }
    }

    /**
     * Checks if the engine is initialized.
     *
     * @return true if initialized
     */
    public synchronized boolean isInitialized() {
        return initialized;
    }

    /**
     * Loads RDF data into the engine from a string.
     *
     * @param rdfData RDF data as string
     * @param format Data format (TURTLE, JSON, XML, CSV)
     * @return Result with status and metadata
     * @throws QLeverFfiException if loading fails
     */
    public @NonNull QLeverResult loadRdfData(@NonNull String rdfData, @NonNull String format) throws QLeverFfiException {
        checkInitialized();
        return ffiBindings.loadRdfData(rdfData, format);
    }

    /**
     * Loads RDF data from a file.
     *
     * @param filePath Path to RDF data file
     * @param format Data format
     * @return Result with status and metadata
     * @throws QLeverFfiException if loading fails
     */
    public @NonNull QLeverResult loadRdfDataFromFile(@NonNull String filePath, @NonNull String format) throws QLeverFfiException {
        checkInitialized();

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new QLeverFfiException("RDF file does not exist: " + filePath);
        }

        if (!Files.isReadable(path)) {
            throw new QLeverFfiException("RDF file is not readable: " + filePath);
        }

        try {
            String fileContent = Files.readString(path);
            return ffiBindings.loadRdfData(fileContent, format);
        } catch (Exception e) {
            throw new QLeverFfiException("Failed to load RDF data from file: " + filePath, e);
        }
    }

    /**
     * Executes a SPARQL SELECT query synchronously.
     *
     * @param query SPARQL query string
     * @return Query result
     * @throws QLeverFfiException if execution fails
     */
    public @NonNull QLeverResult executeQuery(@NonNull String query) throws QLeverFfiException {
        checkInitialized();
        return ffiBindings.executeSparqlQuery(query);
    }

    /**
     * Executes a SPARQL query with custom timeout.
     *
     * @param query SPARQL query string
     * @param timeoutMs Timeout in milliseconds
     * @return Query result
     * @throws QLeverFfiException if execution fails or times out
     */
    public @NonNull QLeverResult executeQuery(@NonNull String query, long timeoutMs) throws QLeverFfiException {
        checkInitialized();
        return ffiBindings.executeSparqlQueryWithTimeout(query, timeoutMs);
    }

    /**
     * Executes a SPARQL query asynchronously.
     *
     * @param query SPARQL query string
     * @return CompletableFuture with query result
     */
    public @NonNull CompletableFuture<QLeverResult> executeQueryAsync(@NonNull String query) {
        CompletableFuture<QLeverResult> future = new CompletableFuture<>();

        try {
            checkInitialized();
        } catch (QLeverFfiException e) {
            future.completeExceptionally(e);
            return future;
        }

        Thread.ofVirtual()
            .name("qlever-query-" + Thread.currentThread().threadId())
            .start(() -> {
                try {
                    QLeverResult result = executeQuery(query);
                    future.complete(result);
                } catch (QLeverFfiException e) {
                    future.completeExceptionally(e);
                }
            });

        return future;
    }

    /**
     * Executes a SPARQL UPDATE query.
     *
     * @param update SPARQL update string
     * @return Update result
     * @throws QLeverFfiException if execution fails
     */
    public @NonNull QLeverResult executeUpdate(@NonNull String update) throws QLeverFfiException {
        checkInitialized();
        return ffiBindings.executeSparqlUpdate(update);
    }

    /**
     * Gets engine statistics.
     *
     * @return JSON string with statistics
     * @throws QLeverFfiException if retrieval fails
     */
    public @NonNull String getStatistics() throws QLeverFfiException {
        checkInitialized();
        return ffiBindings.getEngineStatistics();
    }

    /**
     * Clears all data from the engine.
     *
     * @throws QLeverFfiException if clearing fails
     */
    public synchronized void clearData() throws QLeverFfiException {
        // Native library not available - throw with clear guidance
        throw new UnsupportedOperationException(
            "Native QLever library not available. " +
            "Clear data functionality requires native implementation. " +
            "Use reinitialize() or restart the engine instead."
        );
    }

    /**
     * Sets the workflow context for this engine instance.
     *
     * @param caseId YAWL case ID or workflow identifier
     */
    public synchronized void setWorkflowContext(@NonNull String caseId) {
        this.workflowContext = caseId;
    }

    /**
     * Gets the current workflow context.
     *
     * @return workflow context or null
     */
    public @Nullable String getWorkflowContext() {
        return workflowContext;
    }

    /**
     * Sets the query timeout for all queries.
     *
     * @param timeoutMs Timeout in milliseconds
     */
    public synchronized void setQueryTimeout(long timeoutMs) {
        this.queryTimeout = timeoutMs;
    }

    /**
     * Gets the current query timeout.
     *
     * @return timeout in milliseconds
     */
    public long getQueryTimeout() {
        return queryTimeout;
    }

    /**
     * Sets the memory limit for the engine.
     *
     * @param limitBytes Memory limit in bytes
     */
    public synchronized void setMemoryLimit(long limitBytes) {
        this.memoryLimit = limitBytes;
    }

    /**
     * Gets the current memory limit.
     *
     * @return memory limit in bytes
     */
    public long getMemoryLimit() {
        return memoryLimit;
    }

    /**
     * Gets current memory usage.
     *
     * @return memory usage in bytes
     * @throws QLeverFfiException if retrieval fails
     */
    public long getCurrentMemoryUsage() throws QLeverFfiException {
        checkInitialized();
        // Native library not available - throw with clear guidance
        throw new UnsupportedOperationException(
            "Native QLever library not available. " +
            "Memory usage querying requires native implementation."
        );
    }

    /**
     * Simulates failure scenario for testing error recovery.
     *
     * @throws QLeverFfiException to simulate failure
     */
    public void simulateFailure() throws QLeverFfiException {
        throw new QLeverFfiException("Simulated engine failure", new IllegalStateException("Test failure"));
    }

    /**
     * Recovers from failure by reinitializing engine state.
     *
     * @return true if recovery successful
     * @throws QLeverFfiException if recovery fails
     */
    public synchronized boolean recoverFromFailure() throws QLeverFfiException {
        shutdown();
        initialize();

        // Restore workflow context if it existed
        if (workflowContext != null) {
            setWorkflowContext(workflowContext);
        }

        return true;
    }

    /**
     * Checks if memory limit is exceeded.
     *
     * @return true if limit exceeded
     */
    private boolean isMemoryLimitExceeded() {
        try {
            long currentUsage = getCurrentMemoryUsage();
            return currentUsage > memoryLimit;
        } catch (QLeverFfiException e) {
            return false;
        }
    }

    /**
     * Validates query before execution.
     *
     * @param query SPARQL query to validate
     * @throws QLeverFfiException if validation fails
     */
    private void validateQuery(@NonNull String query) throws QLeverFfiException {
        if (query == null || query.trim().isEmpty()) {
            throw new QLeverFfiException("Query cannot be null or empty");
        }

        if (query.length() > 10000) {
            throw new QLeverFfiException("Query too long (max 10000 characters)");
        }

        // Check memory limit
        if (isMemoryLimitExceeded()) {
            throw new QLeverFfiException("Memory limit exceeded");
        }
    }

    /**
     * Ensures engine is initialized before operations.
     *
     * @throws QLeverFfiException if not initialized
     */
    private void checkInitialized() throws QLeverFfiException {
        if (!initialized) {
            throw new QLeverFfiException("Engine not initialized. Call initialize() first.");
        }
    }

    /**
     * Ensures resources are cleaned up.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            shutdown();
        } catch (QLeverFfiException e) {
            // Log but don't rethrow in finalize
        } finally {
            super.finalize();
        }
    }
}