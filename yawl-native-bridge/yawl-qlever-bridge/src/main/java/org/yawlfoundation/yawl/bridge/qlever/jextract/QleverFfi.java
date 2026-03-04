package org.yawlfoundation.yawl.bridge.qlever.jextract;

import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SymbolLookup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_LONG_LONG;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static jdk.incubator.foreign.ValueLayout.ADDRESS;
import static jdk.incubator.foreign.ValueLayout.JAVA_LONG;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;

/**
 * JExtract bindings for QLever C++ FFI
 *
 * This class provides the low-level bindings to the QLever Hourglass façade
 * using Panama Foreign Function Interface. All methods use Arena.ofConfined()
 * for memory safety.
 */
public final class QleverFfi {

    // Native function handles
    public static final MethodHandle MH$qlever_engine_create;
    public static final MethodHandle MH$qlever_engine_query;
    public static final MethodHandle MH$qlever_result_get_data;
    public static final MethodHandle MH$qlever_result_free;
    public static final MethodHandle MH$qlever_engine_destroy;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            CLinker linker = CLinker.getInstance();
            SymbolLookup nativeLib = SymbolLookup.loaderLookup();

            // Function descriptors based on actual native signatures
            FunctionDescriptor fdEngineCreate = FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_LONG);
            FunctionDescriptor fdEngineQuery = FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS);
            FunctionDescriptor fdResultGetData = FunctionDescriptor.of(ADDRESS, ADDRESS);
            FunctionDescriptor fdResultFree = FunctionDescriptor.ofVoid(ADDRESS);
            FunctionDescriptor fdEngineDestroy = FunctionDescriptor.ofVoid(ADDRESS);

            // Method handles for native functions
            MH$qlever_engine_create = linker.downcallHandle(
                nativeLib.find("qlever_engine_create").orElseThrow(),
                fdEngineCreate
            );

            MH$qlever_engine_query = linker.downcallHandle(
                nativeLib.find("qlever_engine_query").orElseThrow(),
                fdEngineQuery
            );

            MH$qlever_result_get_data = linker.downcallHandle(
                nativeLib.find("qlever_result_get_data").orElseThrow(),
                fdResultGetData
            );

            MH$qlever_result_free = linker.downcallHandle(
                nativeLib.find("qlever_result_free").orElseThrow(),
                fdResultFree
            );

            MH$qlever_engine_destroy = linker.downcallHandle(
                nativeLib.find("qlever_engine_destroy").orElseThrow(),
                fdEngineDestroy
            );

        } catch (Throwable e) {
            throw new ExceptionInInitializerError("Failed to initialize QLever FFI: " + e.getMessage());
        }
    }

    // Private constructor to prevent instantiation
    private QleverFfi() {}

    /**
     * Creates a new QLever engine instance
     *
     * @param configPath Path to the QLever configuration file
     * @param bufferSize Maximum result buffer size
     * @return MemoryAddress for the created engine
     * @throws UnsupportedOperationException if native library is unavailable
     */
    public static MemoryAddress createEngine(String configPath, long bufferSize) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment configSegment = scope.allocateUtf8String(configPath);
            MemoryAddress result = (MemoryAddress) MH$qlever_engine_create.invokeExact(
                configSegment,
                bufferSize
            );

            if (result.equals(MemoryAddress.NULL)) {
                throw new UnsupportedOperationException("Failed to create QLever engine");
            }

            return result;
        } catch (Throwable e) {
            throw new UnsupportedOperationException(
                "QLever engine creation failed: " + e.getMessage() +
                ". Native library may not be available or properly loaded."
            );
        }
    }

    /**
     * Executes a SPARQL query against the QLever engine
     *
     * @param engineHandle Handle to the QLever engine
     * @param query SPARQL query string
     * @return MemoryAddress for the query result
     * @throws UnsupportedOperationException if native library is unavailable
     */
    public static MemoryAddress executeQuery(MemoryAddress engineHandle, String query) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment querySegment = scope.allocateUtf8String(query);
            MemoryAddress result = (MemoryAddress) MH$qlever_engine_query.invokeExact(
                engineHandle,
                querySegment
            );

            if (result.equals(MemoryAddress.NULL)) {
                throw new UnsupportedOperationException("Query execution failed");
            }

            return result;
        } catch (Throwable e) {
            throw new UnsupportedOperationException(
                "QLever query execution failed: " + e.getMessage() +
                ". Native library may not be available or properly loaded."
            );
        }
    }

    /**
     * Retrieves the result data from a QLever result handle
     *
     * @param resultHandle Handle to the query results
     * @return MemorySegment containing the result data
     * @throws UnsupportedOperationException if native library is unavailable
     */
    public static MemorySegment getResultData(MemoryAddress resultHandle) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            return (MemorySegment) MH$qlever_result_get_data.invokeExact(resultHandle);
        } catch (Throwable e) {
            throw new UnsupportedOperationException(
                "Failed to get QLever result data: " + e.getMessage() +
                ". Native library may not be available or properly loaded."
            );
        }
    }

    /**
     * Frees the memory associated with a QLever result handle
     *
     * @param resultHandle Handle to the query results to free
     * @throws UnsupportedOperationException if native library is unavailable
     */
    public static void freeResult(QleverResultHandle resultHandle) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemoryAddress resultAddr = resultHandle.toAddress();
            MH$qlever_result_free.invokeExact(resultAddr);
        } catch (Throwable e) {
            throw new UnsupportedOperationException(
                "Failed to free QLever result: " + e.getMessage() +
                ". Native library may not be available or properly loaded."
            );
        }
    }

    /**
     * Destroys a QLever engine instance
     *
     * @param engineHandle Handle to the QLever engine to destroy
     * @throws UnsupportedOperationException if native library is unavailable
     */
    public static void destroyEngine(QleverEngineHandle engineHandle) {
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemoryAddress engineAddr = engineHandle.toAddress();
            MH$qlever_engine_destroy.invokeExact(engineAddr);
        } catch (Throwable e) {
            throw new UnsupportedOperationException(
                "Failed to destroy QLever engine: " + e.getMessage() +
                ". Native library may not be available or properly loaded."
            );
        }
    }

    /**
     * Executes a query and automatically frees the result
     *
     * @param engineHandle Handle to the QLever engine
     * @param query SPARQL query string
     * @return MemorySegment containing the result data
     * @throws UnsupportedOperationException if native library is unavailable
     */
    public static MemorySegment executeQueryWithAutoCleanup(QleverEngineHandle engineHandle, String query) {
        QleverResultHandle resultHandle = null;
        try {
            resultHandle = QleverResultHandle.of(executeQuery(engineHandle.toAddress(), query));
            return getResultData(resultHandle.toAddress());
        } finally {
            if (resultHandle != null) {
                try {
                    freeResult(resultHandle);
                } catch (UnsupportedOperationException e) {
                    // Log but don't propagate cleanup errors
                    System.err.println("Warning: Failed to cleanup QLever result: " + e.getMessage());
                }
            }
        }
    }
}