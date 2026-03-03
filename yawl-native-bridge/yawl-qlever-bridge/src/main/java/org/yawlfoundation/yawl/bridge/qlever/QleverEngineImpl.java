/*
 * QLever Engine Implementation
 *
 * Pure Java 25 domain API implementation with native bridge.
 * Implements HYPER_STANDARDS: no mocks, real impl or throw UnsupportedOperationException.
 */

package org.yawlfoundation.yawl.bridge.qlever;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Implementation of QLeverEngine interface using native bridge.
 * Provides real SPARQL query execution through Panama FFI or throws
 * UnsupportedOperationException if native dependencies are missing.
 *
 * @implNote This implementation must either work with real native QLever
 *           dependencies or throw UnsupportedOperationException with clear
 *           messages about missing dependencies.
 */
public final class QleverEngineImpl implements QleverEngine {

    private Path indexPath;
    private NativeHandle<QleverEngineHandle> engineHandle;
    private boolean initialized = false;

    /**
     * Create a new uninitialized QLever engine
     */
    public QleverEngineImpl() {
        // Real implementation will initialize native resources
        // Missing native dependencies will be detected during initialization
    }

    @Override
    public void initialize(Path indexPath) throws QleverException {
        Objects.requireNonNull(indexPath, "Index path cannot be null");

        try {
            // Initialize global QLever state first
            QleverNativeBridge.initialize();

            // Create engine instance
            this.engineHandle = QleverNativeBridge.createEngine(indexPath);
            this.indexPath = indexPath;
            this.initialized = true;

        } catch (UnsatisfiedLinkError e) {
            throw new UnsupportedOperationException(
                "QLever native library not found. Please install QLever and ensure libqlever.so " +
                "(or equivalent for your platform) is in the library path. " +
                "Original error: " + e.getMessage(),
                e
            );
        } catch (NoClassDefFoundError e) {
            throw new UnsupportedOperationException(
                "Panama FFI dependencies not available. This requires Java 21+ with Panama " +
                "foreign function interface enabled. Original error: " + e.getMessage(),
                e
            );
        } catch (Exception e) {
            throw new QleverException("Failed to initialize QLever engine", e);
        }
    }

    @Override
    public String select(String sparql) throws QleverException {
        checkInitialized();
        validateQueryOrThrow(sparql);

        try (NativeHandle<QleverResultHandle> result = QleverNativeBridge.executeQuery(engineHandle, sparql)) {
            return QleverNativeBridge.getResultData(result);
        } catch (Exception e) {
            if (e instanceof QleverException) {
                throw (QleverException) e;
            }
            throw new QleverException("SELECT query execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean ask(String sparql) throws QleverException {
        checkInitialized();
        validateQueryOrThrow(sparql);

        // Execute ASK query
        String jsonResult;
        try (NativeHandle<QleverResultHandle> result = QleverNativeBridge.executeQuery(engineHandle, sparql)) {
            jsonResult = QleverNativeBridge.getResultData(result);
        }

        // Parse boolean result from JSON
        // Format: {"head":{}, "boolean":true/false}
        try {
            // Simple JSON parsing for boolean result
            String booleanValue = jsonResult.substring(
                jsonResult.indexOf("\"boolean\":") + 10,
                jsonResult.indexOf("}", jsonResult.indexOf("\"boolean\":"))
            ).trim();

            return Boolean.parseBoolean(booleanValue);
        } catch (Exception e) {
            throw new QleverException("Failed to parse ASK query result: " + e.getMessage(), e);
        }
    }

    @Override
    public String construct(String sparql) throws QleverException {
        checkInitialized();
        validateQueryOrThrow(sparql);

        try (NativeHandle<QleverResultHandle> result = QleverNativeBridge.executeQuery(engineHandle, sparql)) {
            return QleverNativeBridge.getResultData(result);
        } catch (Exception e) {
            if (e instanceof QleverException) {
                throw (QleverException) e;
            }
            throw new QleverException("CONSTRUCT query execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String describe(String sparql) throws QleverException {
        checkInitialized();
        validateQueryOrThrow(sparql);

        try (NativeHandle<QleverResultHandle> result = QleverNativeBridge.executeQuery(engineHandle, sparql)) {
            return QleverNativeBridge.getResultData(result);
        } catch (Exception e) {
            if (e instanceof QleverException) {
                throw (QleverException) e;
            }
            throw new QleverException("DESCRIBE query execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String update(String sparql) throws QleverException {
        checkInitialized();
        validateQueryOrThrow(sparql);

        // UPDATE queries have special handling - they modify the index
        try (NativeHandle<QleverResultHandle> result = QleverNativeBridge.executeQuery(engineHandle, sparql)) {
            return QleverNativeBridge.getResultData(result);
        } catch (Exception e) {
            if (e instanceof QleverException) {
                throw (QleverException) e;
            }
            throw new QleverException("UPDATE query execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean validateQuery(String sparql) throws QleverException {
        checkInitialized();
        Objects.requireNonNull(sparql, "SPARQL query cannot be null");

        try {
            // Native validation would call qlever_validate_query
            // For now, we'll throw since we don't have the native function implemented yet
            throw new UnsupportedOperationException(
                "Query validation not yet implemented in native layer. " +
                "Please validate queries using select() method and catch QleverException."
            );
        } catch (Exception e) {
            if (e instanceof QleverException) {
                throw (QleverException) e;
            }
            throw new QleverException("Query validation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Path getIndexPath() {
        return indexPath;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public Map<String, Object> getStatistics() {
        if (!initialized) {
            return Collections.emptyMap();
        }

        try {
            // Would normally call native function to get statistics
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("engine_type", "QLever");
            stats.put("initialized", true);
            stats.put("index_path", indexPath.toString());
            // Add more statistics when native API is available
            return stats;
        } catch (Exception e) {
            return Collections.singletonMap("error", "Failed to get statistics: " + e.getMessage());
        }
    }

    @Override
    public void close() throws QleverException {
        try {
            if (engineHandle != null) {
                QleverNativeBridge.destroyEngine(engineHandle);
                engineHandle = null;
            }

            if (initialized) {
                QleverNativeBridge.shutdown();
                initialized = false;
            }
        } catch (Exception e) {
            throw new QleverException("Failed to shutdown QLever engine", e);
        }
    }

    // Helper methods

    private void checkInitialized() {
        if (!initialized || engineHandle == null) {
            throw new IllegalStateException("QLever engine not initialized. Call initialize() first.");
        }
    }

    private void validateQueryOrThrow(String sparql) {
        Objects.requireNonNull(sparql, "SPARQL query cannot be null");

        if (sparql.trim().isEmpty()) {
            throw new QleverException(QleverStatus.INVALID_QUERY_SYNTAX);
        }

        // Basic query type detection for better error messages
        String upperQuery = sparql.toUpperCase().trim();
        if (!upperQuery.startsWith("SELECT") &&
            !upperQuery.startsWith("ASK") &&
            !upperQuery.startsWith("CONSTRUCT") &&
            !upperQuery.startsWith("DESCRIBE") &&
            !upperQuery.startsWith("INSERT") &&
            !upperQuery.startsWith("DELETE") &&
            !upperQuery.startsWith("LOAD") &&
            !upperQuery.startsWith("CLEAR") &&
            !upperQuery.startsWith("DROP") &&
            !upperQuery.startsWith("CREATE") &&
            !upperQuery.startsWith("COPY") &&
            !upperQuery.startsWith("MOVE") &&
            !upperQuery.startsWith("ADD")) {
            throw new QleverException("Invalid SPARQL query: must start with a valid query form");
        }
    }
}