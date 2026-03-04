/*
 * QLever Native Bridge
 *
 * Wraps jextract MethodHandles and converts status→exception.
 * Implements HYPER_STANDARDS: no mocks, real impl or throw UnsupportedOperationException.
 */

package org.yawlfoundation.yawl.bridge.qlever;

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.Arena;
import org.yawlfoundation.yawl.bridge.qlever.jextract.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;

/**
 * Native bridge for QLever operations using Panama FFI.
 * Provides safe access to native QLever functions with proper error handling
 * and resource management.
 *
 * @apiNote This class handles the low-level FFI interactions and should not be used directly.
 *          Use QleverEngine for high-level operations.
 */
public final class QleverNativeBridge {

    // Method handles for native functions
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final MethodHandle MH_QLEVER_INITIALIZE;
    private static final MethodHandle MH_QLEVER_SHUTDOWN;
    private static final MethodHandle MH_QLEVER_ENGINE_CREATE;
    private static final MethodHandle MH_QLEVER_ENGINE_DESTROY;
    private static final MethodHandle MH_QLEVER_ENGINE_QUERY;
    private static final MethodHandle MH_QLEVER_RESULT_GET_DATA;
    private static final MethodHandle MH_QLEVER_RESULT_DESTROY;

    // Static initialization
    static {
        try {
            // Native library loading would normally happen here
            System.loadLibrary("qlever");

            // Method handles for native functions
            MH_QLEVER_INITIALIZE = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverInitialize",
                MethodType.methodType(QleverStatus.class)
            );

            MH_QLEVER_SHUTDOWN = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverShutdown",
                MethodType.methodType(QleverStatus.class)
            );

            MH_QLEVER_ENGINE_CREATE = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverEngineCreate",
                MethodType.methodType(QleverStatus.class, String.class, NativeHandle[].class)
            );

            MH_QLEVER_ENGINE_DESTROY = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverEngineDestroy",
                MethodType.methodType(void.class, NativeHandle.class)
            );

            MH_QLEVER_ENGINE_QUERY = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverEngineQuery",
                MethodType.methodType(QleverStatus.class, NativeHandle.class, String.class, NativeHandle[].class)
            );

            MH_QLEVER_RESULT_GET_DATA = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverResultGetData",
                MethodType.methodType(QleverStatus.class, NativeHandle.class, NativeHandle[].class, long[].class)
            );

            MH_QLEVER_RESULT_DESTROY = LOOKUP.findStatic(
                QleverNativeBridge.class,
                "qleverResultDestroy",
                MethodType.methodType(void.class, NativeHandle.class)
            );

        } catch (Throwable t) {
            throw new ExceptionInInitializerError("Failed to initialize native bridge: " + t.getMessage());
        }
    }

    private QleverNativeBridge() {
        // Utility class, prevent instantiation
    }

    /**
     * Initialize QLever global state
     *
     * @throws QleverException if initialization fails
     */
    public static void initialize() throws QleverException {
        QleverStatus status = (QleverStatus) invoke(MH_QLEVER_INITIALIZE);
        status.throwIfError();
    }

    /**
     * Shutdown QLever global state
     *
     * @throws QleverException if shutdown fails
     */
    public static void shutdown() throws QleverException {
        QleverStatus status = (QleverStatus) invoke(MH_QLEVER_SHUTDOWN);
        status.throwIfError();
    }

    /**
     * Create a new QLever engine instance
     *
     * @param indexPath Path to QLever index
     * @return Engine handle wrapped in NativeHandle
     * @throws QleverException if engine creation fails
     */
    public static NativeHandle<QleverEngineHandle> createEngine(Path indexPath) throws QleverException {
        Objects.requireNonNull(indexPath, "Index path cannot be null");

        try (Arena arena = Arena.ofConfined()) {
            NativeHandle<QleverEngineHandle>[] engineHandle = new NativeHandle[1];
            QleverStatus status = (QleverStatus) invoke(
                MH_QLEVER_ENGINE_CREATE,
                indexPath.toString(),
                engineHandle
            );
            status.throwIfError();

            return engineHandle[0];
        }
    }

    /**
     * Execute a SPARQL query on the engine
     *
     * @param engine Engine handle
     * @param sparql SPARQL query string
     * @return Result handle wrapped in NativeHandle
     * @throws QleverException if query execution fails
     */
    public static NativeHandle<QleverResultHandle> executeQuery(
        NativeHandle<QleverEngineHandle> engine,
        String sparql
    ) throws QleverException {
        Objects.requireNonNull(engine, "Engine handle cannot be null");
        Objects.requireNonNull(sparql, "SPARQL query cannot be null");

        try (Arena arena = Arena.ofConfined()) {
            NativeHandle<QleverResultHandle>[] resultHandle = new NativeHandle[1];
            QleverStatus status = (QleverStatus) invoke(
                MH_QLEVER_ENGINE_QUERY,
                engine,
                sparql,
                resultHandle
            );
            status.throwIfError();

            return resultHandle[0];
        }
    }

    /**
     * Get result data as JSON string
     *
     * @param result Result handle
     * @return JSON result string
     * @throws QleverException if data extraction fails
     */
    public static String getResultData(NativeHandle<QleverResultHandle> result) throws QleverException {
        Objects.requireNonNull(result, "Result handle cannot be null");

        try (Arena arena = Arena.ofConfined()) {
            // Output handles for data and length
            MemorySegment dataPtr = arena.allocateLong(1);
            MemorySegment lenPtr = arena.allocateLong(1);
            NativeHandle[] dataHandles = {NativeHandle.of(dataPtr, arena, MemorySegment.class)};
            NativeHandle[] lenHandles = {NativeHandle.of(lenPtr, arena, long.class)};

            QleverStatus status = (QleverStatus) invoke(
                MH_QLEVER_RESULT_GET_DATA,
                result,
                dataHandles,
                new long[]{lenHandles[0].toPointer()}
            );
            status.throwIfError();

            // Extract data pointer and length
            long dataAddress = dataHandles[0].toPointer();
            long dataLength = lenHandles[0].toPointer();

            if (dataAddress == 0) {
                throw new QleverException(QleverStatus.RESULT_EXTRACTION_FAILED);
            }

            // Read the data as string
            MemorySegment dataSegment = MemoryAddress.ofLong(dataAddress).asSegment(dataLength, Arena.globalScope());
            byte[] bytes = new byte[(int) dataLength];
            dataSegment.copyTo(MemorySegment.ofArray(bytes));
            return new String(bytes);
        }
    }

    /**
     * Destroy an engine handle
     *
     * @param engine Engine handle to destroy
     */
    public static void destroyEngine(NativeHandle<QleverEngineHandle> engine) {
        Objects.requireNonNull(engine, "Engine handle cannot be null");
        try {
            invoke(MH_QLEVER_ENGINE_DESTROY, engine);
        } catch (Throwable t) {
            // Ignore cleanup errors
        }
    }

    /**
     * Destroy a result handle
     *
     * @param result Result handle to destroy
     */
    public static void destroyResult(NativeHandle<QleverResultHandle> result) {
        Objects.requireNonNull(result, "Result handle cannot be null");
        try {
            invoke(MH_QLEVER_RESULT_DESTROY, result);
        } catch (Throwable t) {
            // Ignore cleanup errors
        }
    }

    // Private method to invoke method handles with error handling
    private static Object invoke(MethodHandle handle, Object... args) throws Throwable {
        try {
            return handle.invokeWithArguments(args);
        } catch (Throwable t) {
            throw new QleverException("Native method invocation failed: " + t.getMessage(), t);
        }
    }
}