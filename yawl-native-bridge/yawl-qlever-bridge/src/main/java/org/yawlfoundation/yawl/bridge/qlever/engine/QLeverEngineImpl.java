import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import jdk.incubator.foreign.Arena;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ValueLayout;

import org.yawlfoundation.yawl.bridge.qlever.native.*;

/**
 * QLeverEngineImpl - Implementation of QLeverEngine Interface
 *
 * This class implements the QLeverEngine interface using the Panama FFI
 * bridge to communicate with the native QLever library.
 */
public final class QLeverEngineImpl implements QLeverEngine {

    private final MemorySegment engineHandle;
    private final Arena arena;
    private final String indexPath;
    private volatile boolean open = true;

    // Thread synchronization
    private final Lock executionLock = new ReentrantLock();

    // Constants for status codes
    private static final int OK = 0;
    private static final int ERROR_QUERY_PARSE_FAILED = 1001;
    private static final int ERROR_QUERY_EXECUTION_FAILED = 1002;
    private static final int ERROR_RESULT_NOT_AVAILABLE = 1003;
    private static final int ERROR_ENGINE_NOT_INITIALIZED = 2000;

    // FFI method handles
    private static jdk.incubator.foreign.MemorySegment qleverEngineCreateHandle;
    private static jdk.incubator.foreign.MemorySegment qleverEngineQueryHandle;
    private static jdk.incubator.foreign.MemorySegment qleverResultGetDataHandle;
    private static jdk.incubator.foreign.MemorySegment qleverEngineDestroyHandle;

    static {
        try {
            // Initialize FFI handles
            var lookup = jdk.incubator.foreign.SymbolLookup.loaderLookup();
            qleverEngineCreateHandle = lookup.lookup("qlever_engine_create").orElseThrow();
            qleverEngineQueryHandle = lookup.lookup("qlever_engine_query").orElseThrow();
            qleverResultGetDataHandle = lookup.lookup("qlever_result_get_data").orElseThrow();
            qleverEngineDestroyHandle = lookup.lookup("qlever_engine_destroy").orElseThrow();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize QLever FFI handles", e);
        }
    }

    // Private constructor - use static factory methods
    private QLeverEngineImpl(MemorySegment engineHandle, Arena arena, String indexPath) {
        this.engineHandle = engineHandle;
        this.arena = arena;
        this.indexPath = indexPath;
    }

    /**
     * Creates a new QLever engine instance
     */
    public static QLeverEngine create(String indexPath) {
        return create(indexPath, 0);
    }

    /**
     * Creates a new QLever engine instance with timeout
     */
    public static QLeverEngine create(String indexPath, int timeoutMs) {
        try {
            // Initialize the global QLever state
            QleverNativeBridge bridge = new QleverNativeBridge();
            QleverStatus initStatus = bridge.initializeGlobal();

            if (initStatus.isFailure()) {
                throw initStatus.toRuntimeException();
            }

            // Create confined arena for this engine
            Arena arena = Arena.ofConfined();
            MemorySegment pathStr = arena.allocateFrom(indexPath);

            // Create engine handle using FFI
            MemorySegment handle = executeEngineCreate(pathStr, indexPath.length());

            if (handle == MemorySegment.NULL) {
                throw new QleverRuntimeException(
                    "Failed to create QLever engine",
                    new QleverStatus(QleverStatus.ERROR_ENGINE_NOT_INITIALIZED, "Engine creation failed")
                );
            }

            return new QLeverEngineImpl(handle, arena, indexPath);

        } catch (QleverException e) {
            throw new QleverRuntimeException("Failed to create QLever engine: " + e.getMessage(), e.getStatus());
        }
    }

    @Override
    public AskResult ask(String query) {
        ensureOpen();
        validateQuery(query);

        executionLock.lock();
        try {
            MemorySegment result = executeQuery(query);
            QleverStatus status = QleverStatus.fromSegment(result);

            if (status.code() != OK) {
                throw new QleverRuntimeException(status.message());
            }

            // Parse the result data from the query execution
            boolean answer = parseAskResult(status);
            return new AskResult(answer);

        } finally {
            executionLock.unlock();
        }
    }

    @Override
    public SelectResult select(String query) {
        ensureOpen();
        validateQuery(query);

        executionLock.lock();
        try {
            MemorySegment result = executeQuery(query);
            QleverStatus status = QleverStatus.fromSegment(result);

            if (status.code() != OK) {
                throw new QleverRuntimeException(status.message());
            }

            // Parse JSON result into tabular format
            String jsonResult = getResultData(status);
            SelectData selectData = parseSelectResult(jsonResult);

            return new SelectResult(selectData.variables(), selectData.rows());

        } finally {
            executionLock.unlock();
        }
    }

    @Override
    public ConstructResult construct(String query) {
        ensureOpen();
        validateQuery(query);

        executionLock.lock();
        try {
            MemorySegment result = executeQuery(query);
            QleverStatus status = QleverStatus.fromSegment(result);

            if (status.code() != OK) {
                throw new QleverRuntimeException(status.message());
            }

            // Parse JSON result into triples
            String jsonResult = getResultData(status);
            List<Triple> triples = parseConstructResult(jsonResult);
            String turtleResult = convertToTurtle(triples);

            return new ConstructResult(triples);

        } finally {
            executionLock.unlock();
        }
    }

    @Override
    public boolean validateQuery(String query) {
        ensureOpen();

        try {
            QleverNativeBridge bridge = new QleverNativeBridge();
            QleverStatus status = bridge.validateQuery(query);
            return status.isSuccess();
        } catch (QleverException e) {
            throw new QleverRuntimeException(
                "Failed to validate query: " + e.getMessage(),
                e.getStatus()
            );
        }
    }

    @Override
    public String getVersion() {
        ensureOpen();

        try {
            // Call native function to get version
            QleverNativeBridge bridge = new QleverNativeBridge();
            return bridge.getVersion();
        } catch (QleverException e) {
            // Fallback version if native library is not available
            return "QLever Java Bridge v1.0.0 (Native library not available)";
        }
    }

    @Override
    public String getIndexPath() {
        return indexPath;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        if (!open) {
            return;
        }

        executionLock.lock();
        try {
            // Destroy the engine handle
            if (engineHandle != null && engineHandle != MemorySegment.NULL) {
                QleverNativeBridge bridge = new QleverNativeBridge();
                bridge.destroyEngine(engineHandle);
            }

            // Shutdown global state
            bridge.shutdownGlobal();

            // Close the arena
            if (arena != null) {
                arena.close();
            }

            open = false;

        } catch (Exception e) {
            throw new QleverRuntimeException(
                "Failed to close QLever engine: " + e.getMessage(),
                new QleverStatus(QleverStatus.ERROR_ENGINE_NOT_INITIALIZED, e.getMessage())
            );
        } finally {
            executionLock.unlock();
        }
    }

    // Private helper methods

    private void ensureOpen() {
        if (!open) {
            throw new IllegalStateException("QLever engine has been closed");
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }

        // Call the interface validation method
        boolean isValid = this.validateQuery(query);
        if (!isValid) {
            throw new QleverRuntimeException(
                "Invalid SPARQL query syntax",
                new QleverStatus(ERROR_QUERY_PARSE_FAILED, "Query validation failed")
            );
        }
    }

    private MemorySegment executeQuery(String query) {
        try {
            QleverNativeBridge bridge = new QleverNativeBridge();

            // Allocate memory for query string
            MemorySegment queryStr = arena.allocateFrom(query);
            long queryLength = query.length();

            // Execute query using FFI
            MemorySegment result = executeEngineQuery(engineHandle, queryStr, queryLength);

            return result;
        } catch (QleverException e) {
            throw new QleverRuntimeException(
                "Failed to execute query: " + e.getMessage(),
                new QleverStatus(ERROR_QUERY_EXECUTION_FAILED, e.getMessage())
            );
        }
    }

    private MemorySegment executeEngineCreate(MemorySegment pathStr, long pathLength) {
        // Create status struct
        var status = org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
        MemorySegment statusSegment = status.segment();

        // Create engine handle
        MemorySegment handle = MemorySegment.allocateNative(ValueLayout.ADDRESS, arena);

        // Call native function
        var linker = jdk.incubator.foreign.Linker.nativeLinker();
        var descriptor = jdk.incubator.foreign.FunctionDescriptor.of(
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        );

        linker.downcallHandle(qleverEngineCreateHandle, descriptor)
            .invoke(statusSegment, pathStr, handle);

        // Check status
        if (!status.isSuccess()) {
            throw new QleverRuntimeException(
                "Engine creation failed",
                QleverStatus.fromJextract(status)
            );
        }

        return handle.get(ValueLayout.ADDRESS, 0);
    }

    private MemorySegment executeEngineQuery(MemorySegment engineHandle, MemorySegment queryStr, long queryLength) {
        // Create status struct
        var status = org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
        MemorySegment statusSegment = status.segment();

        // Create result handle
        MemorySegment result = MemorySegment.allocateNative(ValueLayout.ADDRESS, arena);

        // Call native function
        var linker = jdk.incubator.foreign.Linker.nativeLinker();
        var descriptor = jdk.incubator.foreign.FunctionDescriptor.of(
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS
        );

        linker.downcallHandle(qleverEngineQueryHandle, descriptor)
            .invoke(statusSegment, engineHandle, queryStr, result);

        // Check status
        if (!status.isSuccess()) {
            throw new QleverRuntimeException(
                "Query execution failed",
                QleverStatus.fromJextract(status)
            );
        }

        return result.get(ValueLayout.ADDRESS, 0);
    }

    private MemorySegment executeResultGetData(MemorySegment resultHandle) {
        // Create status struct
        var status = org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
        MemorySegment statusSegment = status.segment();

        // Create output buffers
        MemorySegment dataPtr = MemorySegment.allocateNative(ValueLayout.ADDRESS, arena);
        MemorySegment lenPtr = MemorySegment.allocateNative(ValueLayout.JAVA_LONG, arena);

        // Call native function
        var linker = jdk.incubator.foreign.Linker.nativeLinker();
        var descriptor = jdk.incubator.foreign.FunctionDescriptor.of(
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG
        );

        linker.downcallHandle(qleverResultGetDataHandle, descriptor)
            .invoke(statusSegment, resultHandle, dataPtr, lenPtr);

        // Check status
        if (!status.isSuccess()) {
            throw new QleverRuntimeException(
                "Failed to get result data",
                QleverStatus.fromJextract(status)
            );
        }

        return dataPtr.get(ValueLayout.ADDRESS, 0);
    }

    private boolean parseAskResult(QleverStatus status) {
        try {
            String jsonResult = getResultData(status);
            JSONObject json = new JSONObject(jsonResult);
            return json.getBoolean("ask");
        } catch (JSONException e) {
            throw new QleverRuntimeException(
                "Failed to parse ASK result: " + e.getMessage(),
                new QleverStatus(ERROR_RESULT_NOT_AVAILABLE, "JSON parse error")
            );
        }
    }

    private String getResultData(QleverStatus status) {
        try {
            // Extract result data from status
            MemorySegment resultData = executeResultGetData(status.segment());
            return MemorySegment.ofAddress(resultData).getString(0);
        } catch (Exception e) {
            throw new QleverRuntimeException(
                "Failed to get result data: " + e.getMessage(),
                new QleverStatus(ERROR_RESULT_NOT_AVAILABLE, "Result data error")
            );
        }
    }

    // Query result parsing methods

    private SelectData parseSelectResult(String jsonResult) {
        try {
            JSONObject json = new JSONObject(jsonResult);

            // Extract variables
            JSONArray variablesArray = json.getJSONArray("variables");
            List<String> variables = new ArrayList<>();
            for (int i = 0; i < variablesArray.length(); i++) {
                variables.add(variablesArray.getString(i));
            }

            // Extract rows as Map<String, String>
            JSONArray rowsArray = json.getJSONArray("rows");
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = 0; i < rowsArray.length(); i++) {
                JSONArray rowArray = rowsArray.getJSONArray(i);
                Map<String, String> row = new java.util.LinkedHashMap<>();
                for (int j = 0; j < rowArray.length(); j++) {
                    Object value = rowArray.get(j);
                    String varName = variables.get(j);
                    row.put(varName, value != null ? value.toString() : null);
                }
                rows.add(row);
            }

            return new SelectData(variables, rows);

        } catch (JSONException e) {
            throw new QleverRuntimeException(
                "Failed to parse SELECT result: " + e.getMessage(),
                new QleverStatus(ERROR_RESULT_NOT_AVAILABLE, "JSON parse error")
            );
        }
    }

    private List<Triple> parseConstructResult(String jsonResult) {
        try {
            List<Triple> triples = new ArrayList<>();

            // Handle different possible JSON formats
            JSONObject json = new JSONObject(jsonResult);

            if (json.has("triples")) {
                // Direct triples array format
                JSONArray triplesArray = json.getJSONArray("triples");
                for (int i = 0; i < triplesArray.length(); i++) {
                    JSONObject triple = triplesArray.getJSONObject(i);
                    String subject = triple.getString("subject");
                    String predicate = triple.getString("predicate");
                    String object = triple.getString("object");
                    triples.add(new Triple(subject, predicate, object));
                }
            } else if (json.has("results")) {
                // SPARQL JSON Results format for CONSTRUCT
                JSONArray bindingsArray = json.getJSONObject("results").getJSONArray("bindings");
                for (int i = 0; i < bindingsArray.length(); i++) {
                    JSONObject binding = bindingsArray.getJSONObject(i);

                    if (binding.has("s") && binding.has("p") && binding.has("o")) {
                        JSONObject s = binding.getJSONObject("s");
                        JSONObject p = binding.getJSONObject("p");
                        JSONObject o = binding.getJSONObject("o");

                        String subject = s.getString("value");
                        String predicate = p.getString("value");
                        String object = o.getString("value");

                        triples.add(new Triple(subject, predicate, object));
                    }
                }
            }

            return triples;

        } catch (JSONException e) {
            throw new QleverRuntimeException(
                "Failed to parse CONSTRUCT result: " + e.getMessage(),
                new QleverStatus(ERROR_RESULT_NOT_AVAILABLE, "JSON parse error")
            );
        }
    }

    private String convertToTurtle(List<Triple> triples) {
        StringBuilder sb = new StringBuilder();

        // Add prefix declaration
        sb.append("@prefix : <http://example.org/> .\n");
        sb.append("@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n");
        sb.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");

        // Add triples
        for (Triple triple : triples) {
            sb.append(" ").append(escapeTurtle(triple.subject))
              append(" ").append(escapeTurtle(triple.predicate))
              append(" ").append(escapeTurtle(triple.object))
              append(" .\n");
        }

        return sb.toString();
    }

    private String escapeTurtle(String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return "<" + value + ">";
        } else if (value.startsWith("_:")) {
            return value; // BNode
        } else {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
    }

    // Record for select data (private helper)
    private record SelectData(List<String> variables, List<Map<String, String>> rows) {}

    // AutoCloseable pattern for resource management
    @Override
    protected void finalize() throws Throwable {
        try {
            if (open) {
                close();
            }
        } finally {
            super.finalize();
        }
    }
}