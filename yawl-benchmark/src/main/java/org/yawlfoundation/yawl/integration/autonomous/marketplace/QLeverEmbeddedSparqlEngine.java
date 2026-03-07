package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Embedded QLever SPARQL engine using Java FFI to native QLever library.
 *
 * <p>This implementation uses the native QLever library via JNI/FFI for
 * high-performance in-process SPARQL queries. It requires the QLever native
 * library to be available on the classpath or in library path.</p>
 *
 * <p>QLever is an embedded Java/C++ FFI bridge — NOT Docker, NOT HTTP.
 * This is the only QLever integration mode; there is no HTTP version.</p>
 *
 * @since YAWL 6.0
 */
@SuppressWarnings("restricted")
public final class QLeverEmbeddedSparqlEngine implements SparqlEngine {

    private volatile boolean closed = false;
    private long nativeHandle; // Opaque handle to native QLever instance

    static {
        // Load the native QLever library
        try {
            System.loadLibrary("qlever_java");
        } catch (UnsatisfiedLinkError e) {
            throw new RuntimeException(
                "Failed to load native QLever library. Make sure qlever_java.dylib (macOS) or " +
                "qlever_java.dll (Windows) is in the library path. " +
                "Build with: cmake -DCMAKE_BUILD_TYPE=Release && make", e);
        }
    }

    /**
     * Creates a new embedded QLever instance.
     */
    public QLeverEmbeddedSparqlEngine() {
        this.nativeHandle = initializeNativeEngine();
    }

    @Override
    public String constructToTurtle(String constructQuery) throws SparqlEngineException {
        Objects.requireNonNull(constructQuery, "constructQuery must not be null");
        
        if (closed) {
            throw new IllegalStateException("Engine is closed");
        }
        
        if (!isAvailable()) {
            throw new SparqlEngineUnavailableException(engineType(), "embedded");
        }

        try {
            return executeConstructQuery(constructQuery);
        } catch (NativeQueryException e) {
            throw new SparqlEngineException("QLever CONSTRUCT failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        if (closed) {
            return false;
        }
        
        try {
            return checkNativeEngineHealth();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String engineType() {
        return "qlever-embedded";
    }

    @Override
    public void close() {
        if (!closed && nativeHandle != 0) {
            try {
                destroyNativeEngine(nativeHandle);
            } finally {
                closed = true;
                nativeHandle = 0;
            }
        }
    }

    // Native method declarations - implemented in C++/JNI
    private native long initializeNativeEngine();
    private native void destroyNativeEngine(long handle);
    private native boolean checkNativeEngineHealth();
    private native String executeConstructQuery(String query) throws NativeQueryException;

    /**
     * Exception for native query errors.
     */
    public static class NativeQueryException extends Exception {

        private static final long serialVersionUID = 1L;

        public NativeQueryException(String message) {
            super(message);
        }

        public NativeQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Utility method to load test data from Turtle file.
     * 
     * @param turtlePath path to Turtle file
     * @throws SparqlEngineException on failure
     */
    public void loadTurtleFile(Path turtlePath) throws SparqlEngineException {
        Objects.requireNonNull(turtlePath, "turtlePath must not be null");
        
        if (closed) {
            throw new IllegalStateException("Engine is closed");
        }

        try {
            String turtleContent = Files.readString(turtlePath);
            loadTurtleString(turtleContent);
        } catch (IOException e) {
            throw new SparqlEngineException("Failed to read Turtle file: " + e.getMessage(), e);
        } catch (NativeQueryException e) {
            throw new SparqlEngineException("Failed to load Turtle data: " + e.getMessage(), e);
        }
    }

    /**
     * Load Turtle data string into the engine.
     */
    private native void loadTurtleString(String turtle) throws NativeQueryException;

    /**
     * Get memory usage statistics from the native engine.
     */
    public native MemoryStats getMemoryStats();

    /**
     * Memory usage statistics.
     */
    public static class MemoryStats {
        public final long totalMemoryBytes;
        public final long usedMemoryBytes;
        public final long cacheSizeBytes;

        public MemoryStats(long totalMemoryBytes, long usedMemoryBytes, long cacheSizeBytes) {
            this.totalMemoryBytes = totalMemoryBytes;
            this.usedMemoryBytes = usedMemoryBytes;
            this.cacheSizeBytes = cacheSizeBytes;
        }

        @Override
        public String toString() {
            return String.format(
                "MemoryStats[total=%,d MB, used=%,d MB (%.1f%%), cache=%,d MB]",
                totalMemoryBytes / (1024 * 1024),
                usedMemoryBytes / (1024 * 1024),
                (double) usedMemoryBytes / totalMemoryBytes * 100,
                cacheSizeBytes / (1024 * 1024)
            );
        }
    }
}
