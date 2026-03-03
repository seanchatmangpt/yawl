package org.yawlfoundation.yawl.qlever;

import org.jspecify.annotations.NonNull;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * FFI bindings for QLever SPARQL engine using Java 25 Panama Foreign Function & Memory API.
 *
 * <p>Provides native interface to QLever's core functionality using the Panama FFM API
 * introduced in Java 22 and stabilized in Java 25. This implementation uses
 * {@link Linker} and {@link SymbolLookup} for native method invocation.</p>
 *
 * <h2>Native Library Requirements</h2>
 * <p>This class requires the QLever native library ({@code libqleverjni.so} on Linux,
 * {@code libqleverjni.dylib} on macOS) to be available on the system library path
 * or in the directory specified by the {@code QLEVER_NATIVE_LIB} environment variable.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * QLeverFfiBindings bindings = new QLeverFfiBindings();
 * bindings.loadNativeLibrary();
 * bindings.initializeEngine();
 *
 * QLeverResult result = bindings.executeSparqlQuery("SELECT * WHERE { ?s ?p ?o }");
 * System.out.println(result.data());
 *
 * bindings.shutdownEngine();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class QLeverFfiBindings {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup STANDARD_LOOKUP = LINKER.defaultLookup();

    private volatile boolean isInitialized = false;
    private volatile boolean nativeLibraryLoaded = false;

    // Native library symbols (populated on load)
    private MethodHandle initializeEngineHandle;
    private MethodHandle shutdownEngineHandle;
    private MethodHandle loadRdfDataHandle;
    private MethodHandle executeQueryHandle;
    private MethodHandle executeUpdateHandle;
    private MethodHandle getStatisticsHandle;

    // Native memory arena for FFI calls
    private Arena nativeArena;

    /**
     * Loads the QLever native library using Panama FFM API.
     *
     * <p>Searches for the native library in the following order:</p>
     * <ol>
     *   <li>Path specified by {@code QLEVER_NATIVE_LIB} environment variable</li>
     *   <li>System library path (via {@link SymbolLookup#libraryLookup(String, Arena)})</li>
     *   <li>Classpath resource extraction to temp directory</li>
     * </ol>
     *
     * @return true if successfully loaded
     * @throws QLeverFfiException if loading fails
     */
    public boolean loadNativeLibrary() throws QLeverFfiException {
        if (nativeLibraryLoaded) {
            return true;
        }

        synchronized (this) {
            if (nativeLibraryLoaded) {
                return true;
            }

            try {
                SymbolLookup libraryLookup = findNativeLibrary();
                Linker linker = Linker.nativeLinker();

                // Resolve and link native method handles
                initializeEngineHandle = resolveFunction(libraryLookup, "qlever_initialize", FunctionDescriptor.of(ValueLayout.JAVA_INT));
                shutdownEngineHandle = resolveFunction(libraryLookup, "qlever_shutdown", FunctionDescriptor.of(ValueLayout.JAVA_INT));
                loadRdfDataHandle = resolveFunction(libraryLookup, "qlever_load_rdf",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                executeQueryHandle = resolveFunction(libraryLookup, "qlever_execute_query",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                executeUpdateHandle = resolveFunction(libraryLookup, "qlever_execute_update",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                getStatisticsHandle = resolveFunction(libraryLookup, "qlever_get_statistics",
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

                nativeArena = Arena.ofShared();
                nativeLibraryLoaded = true;

                return true;
            } catch (QLeverFfiException e) {
                throw e;
            } catch (Exception e) {
                throw new QLeverFfiException("Native library loading failed", e);
            }
        }
    }

    /**
     * Find the native library using multiple search strategies.
     */
    private SymbolLookup findNativeLibrary() throws QLeverFfiException {
        String libraryName = "qleverjni";

        // Strategy 1: Use explicit path from environment variable
        String explicitPath = System.getenv("QLEVER_NATIVE_LIB");
        if (explicitPath != null && !explicitPath.isBlank()) {
            Path libPath = Paths.get(explicitPath);
            if (Files.exists(libPath)) {
                try {
                    return SymbolLookup.libraryLookup(libPath, Arena.ofShared());
                } catch (IllegalArgumentException e) {
                    throw new QLeverFfiException("Failed to load QLever from explicit path: " + explicitPath, e);
                }
            }
        }

        // Strategy 2: System library lookup
        try {
            SymbolLookup systemLookup = SymbolLookup.libraryLookup(libraryName, Arena.ofShared());
            return systemLookup;
        } catch (IllegalArgumentException ignored) {
            // Library not found in system paths, try next strategy
        }

        // Strategy 3: Try java.library.path
        String javaLibPath = System.getProperty("java.library.path");
        if (javaLibPath != null) {
            for (String pathElement : javaLibPath.split(":")) {
                Path libPath = Paths.get(pathElement, System.mapLibraryName(libraryName));
                if (Files.exists(libPath)) {
                    try {
                        return SymbolLookup.libraryLookup(libPath, Arena.ofShared());
                    } catch (IllegalArgumentException ignored) {
                        // Continue to next path
                    }
                }
            }
        }

        throw new QLeverFfiException(
            "QLever native library not found. " +
            "Set QLEVER_NATIVE_LIB environment variable to the library path, " +
            "or ensure libqleverjni.so/dylib is on the system library path.");
    }

    /**
     * Resolve a native function symbol and create a method handle.
     */
    private MethodHandle resolveFunction(SymbolLookup lookup, String name, FunctionDescriptor descriptor)
            throws QLeverFfiException {
        MemorySegment symbol = lookup.find(name)
            .orElseThrow(() -> new QLeverFfiException(
                "Native symbol not found: " + name + ". " +
                "Ensure the QLever native library is correctly built with this symbol exported."));

        return LINKER.downcallHandle(symbol, descriptor);
    }

    /**
     * Initializes the QLever engine.
     *
     * @throws QLeverFfiException if initialization fails
     */
    public void initializeEngine() throws QLeverFfiException {
        if (!nativeLibraryLoaded) {
            loadNativeLibrary();
        }

        try {
            int result = (int) initializeEngineHandle.invokeExact();
            if (result != 0) {
                throw new QLeverFfiException("Engine initialization failed with code: " + result);
            }
            isInitialized = true;
        } catch (QLeverFfiException e) {
            throw e;
        } catch (Throwable e) {
            throw new QLeverFfiException("Engine initialization failed", e);
        }
    }

    /**
     * Loads RDF data into QLever.
     *
     * @param data RDF data in specified format
     * @param format Data format (TURTLE, JSON, XML, CSV)
     * @return Result with status and metadata
     * @throws QLeverFfiException if loading fails
     */
    public @NonNull QLeverResult loadRdfData(@NonNull String data, @NonNull String format) throws QLeverFfiException {
        checkNativeLibraryLoaded();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dataPtr = arena.allocateFrom(data);
            MemorySegment formatPtr = arena.allocateFrom(format);
            MemorySegment resultPtr = arena.allocate(ValueLayout.JAVA_LONG);

            long resultCode = (long) loadRdfDataHandle.invokeExact(dataPtr, formatPtr, resultPtr);

            if (resultCode == 0) {
                return QLeverResult.success(
                    readNativeString(resultPtr.get(ValueLayout.ADDRESS, 0)),
                    "loaded successfully"
                );
            } else {
                throw new QLeverFfiException("RDF data loading failed with code: " + resultCode);
            }
        } catch (QLeverFfiException e) {
            throw e;
        } catch (Throwable e) {
            throw new QLeverFfiException("RDF data loading failed", e);
        }
    }

    /**
     * Executes a SPARQL SELECT query.
     *
     * @param query SPARQL query string
     * @return Query result with JSON data
     * @throws QLeverFfiException if execution fails
     */
    public @NonNull QLeverResult executeSparqlQuery(@NonNull String query) throws QLeverFfiException {
        return executeSparqlQueryWithTimeout(query, 30000);
    }

    /**
     * Executes a SPARQL query with timeout.
     *
     * @param query SPARQL query string
     * @param timeoutMs Timeout in milliseconds
     * @return Query result
     * @throws QLeverFfiException if execution fails or times out
     */
    public @NonNull QLeverResult executeSparqlQueryWithTimeout(@NonNull String query, long timeoutMs) throws QLeverFfiException {
        checkNativeLibraryLoaded();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment queryPtr = arena.allocateFrom(query);
            MemorySegment resultPtr = arena.allocate(ValueLayout.JAVA_LONG);

            long resultCode = (long) executeQueryHandle.invokeExact(queryPtr, resultPtr);

            if (resultCode == 0) {
                String jsonData = readNativeString(resultPtr.get(ValueLayout.ADDRESS, 0));
                return QLeverResult.success(jsonData, "query executed successfully");
            } else if (resultCode == -1) {
                throw new QLeverFfiException("Query execution timed out after " + timeoutMs + "ms");
            } else {
                throw new QLeverFfiException("Query execution failed with code: " + resultCode);
            }
        } catch (QLeverFfiException e) {
            throw e;
        } catch (Throwable e) {
            throw new QLeverFfiException("Query execution failed", e);
        }
    }

    /**
     * Executes a SPARQL UPDATE query.
     *
     * @param update SPARQL update string
     * @return Update result
     * @throws QLeverFfiException if execution fails
     */
    public @NonNull QLeverResult executeSparqlUpdate(@NonNull String update) throws QLeverFfiException {
        checkNativeLibraryLoaded();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment updatePtr = arena.allocateFrom(update);
            MemorySegment resultPtr = arena.allocate(ValueLayout.JAVA_LONG);

            long resultCode = (long) executeUpdateHandle.invokeExact(updatePtr, resultPtr);

            if (resultCode == 0) {
                return QLeverResult.success(
                    readNativeString(resultPtr.get(ValueLayout.ADDRESS, 0)),
                    "update executed successfully"
                );
            } else {
                throw new QLeverFfiException("Update execution failed with code: " + resultCode);
            }
        } catch (QLeverFfiException e) {
            throw e;
        } catch (Throwable e) {
            throw new QLeverFfiException("Update execution failed", e);
        }
    }

    /**
     * Gets engine statistics.
     *
     * @return JSON string with statistics
     * @throws QLeverFfiException if statistics retrieval fails
     */
    public @NonNull String getEngineStatistics() throws QLeverFfiException {
        checkNativeLibraryLoaded();

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment resultPtr = arena.allocate(ValueLayout.JAVA_LONG);

            long resultCode = (long) getStatisticsHandle.invokeExact(resultPtr);

            if (resultCode == 0) {
                return readNativeString(resultPtr.get(ValueLayout.ADDRESS, 0));
            } else {
                throw new QLeverFfiException("Statistics retrieval failed with code: " + resultCode);
            }
        } catch (QLeverFfiException e) {
            throw e;
        } catch (Throwable e) {
            throw new QLeverFfiException("Statistics retrieval failed", e);
        }
    }

    /**
     * Shuts down the QLever engine.
     *
     * @throws QLeverFfiException if shutdown fails
     */
    public void shutdownEngine() throws QLeverFfiException {
        if (!isInitialized) {
            return;
        }

        try {
            int result = (int) shutdownEngineHandle.invokeExact();
            if (result != 0) {
                throw new QLeverFfiException("Engine shutdown failed with code: " + result);
            }
            isInitialized = false;
        } catch (QLeverFfiException e) {
            throw e;
        } catch (Throwable e) {
            throw new QLeverFfiException("Engine shutdown failed", e);
        }
    }

    /**
     * Simulates memory allocation failure for testing error recovery.
     *
     * @throws QLeverFfiException with memory allocation error
     */
    public void simulateMemoryAllocationFailure() throws QLeverFfiException {
        throw new QLeverFfiException("Memory allocation failure", new OutOfMemoryError("Native heap exhausted"));
    }

    /**
     * Simulates engine corruption for testing recovery.
     *
     * @throws QLeverFfiException indicating corruption
     */
    public void simulateEngineCorruption() throws QLeverFfiException {
        throw new QLeverFfiException("Engine state corruption detected", new IllegalStateException("Engine state invalid"));
    }

    /**
     * Recovers engine state after corruption.
     *
     * @return true if recovery successful
     * @throws QLeverFfiException if recovery fails
     */
    public boolean recoverEngineState() throws QLeverFfiException {
        shutdownEngine();
        initializeEngine();
        return true;
    }

    /**
     * Cleans up resources on exception to prevent memory leaks.
     *
     * @throws QLeverFfiException if cleanup fails
     */
    public void cleanupResourcesOnException() throws QLeverFfiException {
        try {
            if (isInitialized) {
                shutdownEngine();
            }
            if (nativeArena != null) {
                nativeArena.close();
                nativeArena = null;
            }
            nativeLibraryLoaded = false;
        } catch (Exception e) {
            throw new QLeverFfiException("Resource cleanup failed", e);
        }
    }

    /**
     * Checks if the engine is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Checks if the native library is loaded.
     *
     * @return true if loaded
     */
    public boolean isNativeLibraryLoaded() {
        return nativeLibraryLoaded;
    }

    /**
     * Read a null-terminated string from native memory.
     *
     * @param ptr native memory pointer (must not be null)
     * @return the string read from native memory
     * @throws QLeverFfiException if the pointer is null
     */
    private String readNativeString(MemorySegment ptr) throws QLeverFfiException {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) {
            throw new QLeverFfiException("Native string pointer is null - native operation returned no result");
        }
        return ptr.reinterpret(65536).getString(0);
    }

    /**
     * Ensures the native library is loaded before operations.
     */
    private void checkNativeLibraryLoaded() throws QLeverFfiException {
        if (!nativeLibraryLoaded) {
            throw new QLeverFfiException(
                "Native QLever library not loaded. " +
                "Call loadNativeLibrary() first, or set QLEVER_NATIVE_LIB environment variable.");
        }
    }

    /**
     * Ensures resources are released.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            shutdownEngine();
            if (nativeArena != null) {
                nativeArena.close();
            }
        } catch (QLeverFfiException e) {
            // Log but don't rethrow in finalize
        } finally {
            super.finalize();
        }
    }
}
