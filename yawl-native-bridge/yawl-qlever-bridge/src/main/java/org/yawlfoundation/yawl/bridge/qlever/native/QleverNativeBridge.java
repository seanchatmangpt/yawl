/**
 * QleverNativeBridge - Layer 2 Native Bridge Implementation
 *
 * This class provides a safe wrapper around the jextract-generated native bindings
 * using Panama MethodHandles. It handles memory management and error conversion.
 */
public final class QleverNativeBridge {

    // Function handles to native methods
    private final jdk.incubator.foreign.FunctionDescriptor qleverEngineCreateDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverEngineQueryDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverResultGetDataDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverEngineDestroyDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverResultDestroyDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverInitializeDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverShutdownDescriptor;
    private final jdk.incubator.foreign.FunctionDescriptor qleverValidateQueryDescriptor;

    // Method handles
    private final jdk.incubator.foreign.MemorySegment qleverEngineCreate;
    private final jdk.incubator.foreign.MemorySegment qleverEngineQuery;
    private final jdk.incubator.foreign.MemorySegment qleverResultGetData;
    private final jdk.incubator.foreign.MemorySegment qleverEngineDestroy;
    private final jdk.incubator.foreign.MemorySegment qleverResultDestroy;
    private final jdk.incubator.foreign.MemorySegment qleverInitialize;
    private final jdk.incubator.foreign.MemorySegment qleverShutdown;
    private final jdk.incubator.foreign.MemorySegment qleverValidateQuery;

    // Library lookup
    private final jdk.incubator.foreign.SymbolLookup lookup;

    /**
     * Constructor - loads native library and creates method handles
     */
    public QleverNativeBridge() throws QleverException {
        try {
            // Load the native QLever library
            lookup = jdk.incubator.foreign.SymbolLookup.loaderLookup();

            // Define function descriptors
            qleverEngineCreateDescriptor = jdk.incubator.foreign.FunctionDescriptor.of(
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.ADDRESS
            );

            qleverEngineQueryDescriptor = jdk.incubator.foreign.FunctionDescriptor.of(
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.ADDRESS
            );

            qleverResultGetDataDescriptor = jdk.incubator.foreign.FunctionDescriptor.of(
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.JAVA_LONG
            );

            // Other descriptors...
            qleverEngineDestroyDescriptor = jdk.incubator.foreign.FunctionDescriptor.ofVoid(
                jdk.incubator.foreign.ValueLayout.ADDRESS
            );

            qleverResultDestroyDescriptor = jdk.incubator.foreign.FunctionDescriptor.ofVoid(
                jdk.incubator.foreign.ValueLayout.ADDRESS
            );

            qleverInitializeDescriptor = jdk.incubator.foreign.FunctionDescriptor.of(
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT
            );

            qleverShutdownDescriptor = jdk.incubator.foreign.FunctionDescriptor.of(
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT
            );

            qleverValidateQueryDescriptor = jdk.incubator.foreign.FunctionDescriptor.of(
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.$LAYOUT,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.JAVA_INT,
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                jdk.incubator.foreign.ValueLayout.JAVA_INT
            );

            // Get function addresses
            qleverEngineCreate = lookup.lookup("qlever_engine_create").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_engine_create not found in native library"
                ))
            );

            qleverEngineQuery = lookup.lookup("qlever_engine_query").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_engine_query not found in native library"
                ))
            );

            qleverResultGetData = lookup.lookup("qlever_result_get_data").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_result_get_data not found in native library"
                ))
            );

            qleverEngineDestroy = lookup.lookup("qlever_engine_destroy").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_engine_destroy not found in native library"
                ))
            );

            qleverResultDestroy = lookup.lookup("qlever_result_destroy").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_result_destroy not found in native library"
                ))
            );

            qleverInitialize = lookup.lookup("qlever_initialize").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_initialize not found in native library"
                ))
            );

            qleverShutdown = lookup.lookup("qlever_shutdown").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_shutdown not found in native library"
                ))
            );

            qleverValidateQuery = lookup.lookup("qlever_validate_query").orElseThrow(
                () -> new QleverException(new QleverStatus(
                    QleverStatus.ERROR_ENGINE_NOT_INITIALIZED,
                    "qlever_validate_query not found in native library"
                ))
            );

        } catch (LinkageError e) {
            throw new QleverException(new QleverStatus(
                QleverStatus.ERROR_ENGINE_INIT_FAILED,
                "Failed to load native library: " + e.getMessage()
            ));
        }
    }

    /**
     * Initializes the QLever global state
     */
    public QleverStatus initializeGlobal() throws QleverException {
        try (jdk.incubator.foreign.Arena arena = jdk.incubator.foreign.Arena.ofConfined()) {
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus =
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();

            jdk.incubator.foreign.MemorySegment statusSegment = jextractStatus.segment();

            // Call native function
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverInitialize,
                qleverInitializeDescriptor
            ).invoke();

            return QleverStatus.fromJextract(jextractStatus);
        } catch (Throwable e) {
            throw new QleverException(new QleverStatus(
                QleverStatus.ERROR_ENGINE_INIT_FAILED,
                "Failed to initialize QLever: " + e.getMessage()
            ));
        }
    }

    /**
     * Creates a new QLever engine instance
     */
    public NativeHandle<jdk.incubator.foreign.MemorySegment> createEngine(
        String indexPath,
        jdk.incubator.foreign.Arena arena
    ) throws QleverException {
        try {
            // Create status struct
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus =
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
            jdk.incubator.foreign.MemorySegment statusSegment = jextractStatus.segment();

            // Convert path to memory segment
            jdk.incubator.foreign.MemorySegment indexPathSegment = arena.allocateFromUtf8(indexPath);

            // Create engine handle
            jdk.incubator.foreign.MemorySegment engineHandle = jdk.incubator.foreign.MemorySegment.allocateNative(
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                arena
            );

            // Call native function
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverEngineCreate,
                qleverEngineCreateDescriptor
            ).invoke(
                statusSegment,
                indexPathSegment,
                engineHandle
            );

            // Check status
            if (!jextractStatus.isSuccess()) {
                throw QleverStatus.fromJextract(jextractStatus).toException();
            }

            return NativeHandle.create(engineHandle.get(jdk.incubator.foreign.ValueLayout.ADDRESS, 0), arena);
        } catch (Throwable e) {
            throw new QleverException(new QleverStatus(
                QleverStatus.ERROR_ENGINE_INIT_FAILED,
                "Failed to create QLever engine: " + e.getMessage()
            ));
        }
    }

    /**
     * Executes a SPARQL query on the engine
     */
    public NativeHandle<jdk.incubator.foreign.MemorySegment> executeQuery(
        jdk.incubator.foreign.MemorySegment engineHandle,
        String query,
        jdk.incubator.foreign.Arena arena
    ) throws QleverException {
        try {
            // Create status struct
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus =
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
            jdk.incubator.foreign.MemorySegment statusSegment = jextractStatus.segment();

            // Convert query to memory segment
            jdk.incubator.foreign.MemorySegment querySegment = arena.allocateFromUtf8(query);

            // Create result handle
            jdk.incubator.foreign.MemorySegment resultHandle = jdk.incubator.foreign.MemorySegment.allocateNative(
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                arena
            );

            // Call native function
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverEngineQuery,
                qleverEngineQueryDescriptor
            ).invoke(
                statusSegment,
                engineHandle,
                querySegment,
                resultHandle
            );

            // Check status
            if (!jextractStatus.isSuccess()) {
                throw QleverStatus.fromJextract(jextractStatus).toException();
            }

            return NativeHandle.create(resultHandle.get(jdk.incubator.foreign.ValueLayout.ADDRESS, 0), arena);
        } catch (Throwable e) {
            throw new QleverException(new QleverStatus(
                QleverStatus.ERROR_QUERY_EXECUTION_FAILED,
                "Failed to execute query: " + e.getMessage()
            ));
        }
    }

    /**
     * Gets result data as string
     */
    public String getResultData(
        jdk.incubator.foreign.MemorySegment resultHandle,
        jdk.incubator.foreign.Arena arena
    ) throws QleverException {
        try {
            // Create status struct
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus =
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
            jdk.incubator.foreign.MemorySegment statusSegment = jextractStatus.segment();

            // Create output buffers
            jdk.incubator.foreign.MemorySegment dataPtr = jdk.incubator.foreign.MemorySegment.allocateNative(
                jdk.incubator.foreign.ValueLayout.ADDRESS,
                arena
            );
            jdk.incubator.foreign.MemorySegment lenPtr = jdk.incubator.foreign.MemorySegment.allocateNative(
                jdk.incubator.foreign.ValueLayout.JAVA_LONG,
                arena
            );

            // Call native function
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverResultGetData,
                qleverResultGetDataDescriptor
            ).invoke(
                statusSegment,
                resultHandle,
                dataPtr,
                lenPtr
            );

            // Check status
            if (!jextractStatus.isSuccess()) {
                throw QleverStatus.fromJextract(jextractStatus).toException();
            }

            // Read the data
            jdk.incubator.foreign.MemorySegment dataAddress = dataPtr.get(jdk.incubator.foreign.ValueLayout.ADDRESS, 0);
            long length = lenPtr.get(jdk.incubator.foreign.ValueLayout.JAVA_LONG, 0);

            if (dataAddress == jdk.incubator.foreign.MemorySegment.NULL) {
                throw new UnsupportedOperationException(
                    "QLever result data pointer is NULL. This indicates a native memory corruption or bug in the QLever engine."
                );
            }

            return jdk.incubator.foreign.MemorySegment.ofAddress(dataAddress).getString(0);

        } catch (Throwable e) {
            throw new QleverException(new QleverStatus(
                QleverStatus.ERROR_RESULT_NOT_AVAILABLE,
                "Failed to get result data: " + e.getMessage()
            ));
        }
    }

    /**
     * Destroys an engine handle
     */
    public void destroyEngine(jdk.incubator.foreign.MemorySegment engineHandle) {
        if (engineHandle != null && engineHandle != jdk.incubator.foreign.MemorySegment.NULL) {
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverEngineDestroy,
                qleverEngineDestroyDescriptor
            ).invoke(engineHandle);
        }
    }

    /**
     * Destroys a result handle
     */
    public void destroyResult(jdk.incubator.foreign.MemorySegment resultHandle) {
        if (resultHandle != null && resultHandle != jdk.incubator.foreign.MemorySegment.NULL) {
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverResultDestroy,
                qleverResultDestroyDescriptor
            ).invoke(resultHandle);
        }
    }

    /**
     * Shuts down the QLever global state
     */
    public void shutdownGlobal() {
        jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
            qleverShutdown,
            qleverShutdownDescriptor
        ).invoke();
    }

    /**
     * Validates a SPARQL query without executing it
     */
    public QleverStatus validateQuery(String query) throws QleverException {
        try (jdk.incubator.foreign.Arena arena = jdk.incubator.foreign.Arena.ofConfined()) {
            // Create status struct
            org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus jextractStatus =
                org.yawlfoundation.yawl.bridge.qlever.jextract.QleverStatus.success();
            jdk.incubator.foreign.MemorySegment statusSegment = jextractStatus.segment();

            // Convert query to memory segment
            jdk.incubator.foreign.MemorySegment querySegment = arena.allocateFromUtf8(query);

            // Create output buffers for validation result
            jdk.incubator.foreign.MemorySegment isValidPtr = jdk.incubator.foreign.MemorySegment.allocateNative(
                jdk.incubator.foreign.ValueLayout.JAVA_INT,
                arena
            );

            jdk.incubator.foreign.MemorySegment errorMsgPtr = jdk.incubator.foreign.MemorySegment.allocateNative(
                256,  // Error message buffer size
                arena
            );

            // Call native validation function
            jdk.incubator.foreign.Linker.nativeLinker().downcallHandle(
                qleverValidateQuery,
                qleverValidateQueryDescriptor
            ).invoke(
                statusSegment,
                querySegment,
                isValidPtr,
                errorMsgPtr,
                256
            );

            // Check status
            if (!jextractStatus.isSuccess()) {
                throw QleverStatus.fromJextract(jextractStatus).toException();
            }

            // Get validation result
            int isValid = isValidPtr.get(jdk.incubator.foreign.ValueLayout.JAVA_INT, 0);

            String errorMessage = "";
            byte[] errorMsgBytes = new byte[256];
            errorMsgPtr.copyTo(MemorySegment.ofArray(errorMsgBytes));

            // Find null terminator and convert to string
            int nullIndex = 0;
            while (nullIndex < errorMsgBytes.length && errorMsgBytes[nullIndex] != 0) {
                nullIndex++;
            }

            if (nullIndex > 0) {
                errorMessage = new String(errorMsgBytes, 0, nullIndex);
            }

            return new QleverStatus(isValid ? 0 : QleverStatus.ERROR_QUERY_PARSE_FAILED,
                                  isValid ? "Query is valid" : errorMessage);
        } catch (Throwable e) {
            throw new QleverException(new QleverStatus(
                QleverStatus.ERROR_QUERY_PARSE_FAILED,
                "Failed to validate query: " + e.getMessage()
            ));
        }
    }
}