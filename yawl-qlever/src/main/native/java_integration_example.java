/*
 * QLever FFI Java Integration Example
 * ===================================
 *
 * Example showing how to integrate the QLever FFI library with Java
 * using Project Panama FFM (Foreign Function Memory).
 *
 * Requirements:
 * - Java 21+ with Panama FFM
 * - QLever FFI library built (qlever_ffi.so/.dll/.dylib)
 * - QLever index files
 */

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Paths;

public class QLeverFFIExample {
    // Load the native library
    static MemorySegment library = null;

    // Function descriptors
    static FunctionDescriptor indexCreateDesc;
    static FunctionDescriptor indexDestroyDesc;
    static FunctionDescriptor queryExecDesc;
    static FunctionDescriptor resultDestroyDesc;
    static FunctionDescriptor resultHasNextDesc;
    static FunctionDescriptor resultNextDesc;
    static FunctionDescriptor resultFreeDesc;
    static FunctionDescriptor statusFreeDesc;

    // Native function handles
    static MethodHandle qlever_index_create;
    static MethodHandle qlever_index_destroy;
    static MethodHandle qlever_query_exec;
    static MethodHandle qlever_result_destroy;
    static MethodHandle qlever_result_has_next;
    static MethodHandle qlever_result_next;
    static MethodHandle qlever_result_free;
    static MethodHandle qlever_free_status;

    static {
        try {
            // Load native library
            library = LibraryLoader.sharedLibrary("qlever_ffi")
                .lookup("*")
                .orElseThrow();

            // Setup function descriptors
            ValueLayout ADDRESS = ValueLayout.ADDRESS;
            ValueLayout INT = ValueLayout.JAVA_INT;
            ValueLayout LONG = ValueLayout.JAVA_LONG;
            ValueLayout BOOL = ValueLayout.JAVA_BOOLEAN;

            // Function descriptors
            indexCreateDesc = FunctionDescriptor.of(
                ADDRESS,      // QLeverIndex*
                ADDRESS,      // const char*
                ADDRESS       // QleverStatus*
            );

            indexDestroyDesc = FunctionDescriptor.of(
                ValueLayout.VOID,
                ADDRESS       // QLeverIndex*
            );

            queryExecDesc = FunctionDescriptor.of(
                ADDRESS,      // QLeverResult*
                ADDRESS,      // QLeverIndex*
                ADDRESS,      // const char*
                INT,          // QleverMediaType
                ADDRESS       // QleverStatus*
            );

            resultDestroyDesc = FunctionDescriptor.of(
                ValueLayout.VOID,
                ADDRESS       // QLeverResult*
            );

            resultHasNextDesc = FunctionDescriptor.of(
                BOOL,         // int
                ADDRESS       // QLeverResult*
            );

            resultNextDesc = FunctionDescriptor.of(
                ADDRESS,      // const char*
                ADDRESS,      // QLeverResult*
                ADDRESS       // QleverStatus*
            );

            resultFreeDesc = FunctionDescriptor.of(
                ValueLayout.VOID,
                ADDRESS       // QLeverResult*
            );

            statusFreeDesc = FunctionDescriptor.of(
                ValueLayout.VOID,
                ADDRESS       // QLeverStatus*
            );

            // Get function handles
            qlever_index_create = library.lookup("qlever_index_create").get();
            qlever_index_destroy = library.lookup("qlever_index_destroy").get();
            qlever_query_exec = library.lookup("qlever_query_exec").get();
            qlever_result_destroy = library.lookup("qlever_result_destroy").get();
            qlever_result_has_next = library.lookup("qlever_result_has_next").get();
            qlever_result_next = library.lookup("qlever_result_next").get();
            qlever_result_free = library.lookup("qlever_result_free").get();
            qlever_free_status = library.lookup("qlever_free_status").get();

        } catch (Throwable e) {
            throw new RuntimeException("Failed to initialize FFI library", e);
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println("QLever FFI Java Integration Example");
            System.out.println("====================================");

            // Example usage
            runExample();

        } catch (Throwable e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void runExample() throws Throwable {
        // Create status
        MemorySegment status = allocateStatus();
        setSuccess(status);

        // Note: Replace with actual QLever index path
        String indexPath = "/path/to/your/qlever/index";

        // Load index
        System.out.println("Loading QLever index from: " + indexPath);
        MemorySegment index = loadIndex(indexPath, status);

        if (getStatusError(status) != 0) {
            System.err.println("Failed to load index: " + getStatusMessage(status));
            freeStatus(status);
            return;
        }

        System.out.println("✓ Index loaded successfully");

        // Execute query
        System.out.println("Executing SPARQL query...");
        MemorySegment result = executeQuery(index,
            "SELECT ?s ?p ?o WHERE {?s ?p ?o} LIMIT 10", 0, status);

        if (getStatusError(status) != 0) {
            System.err.println("Query failed: " + getStatusMessage(status));
            freeIndex(index);
            freeStatus(status);
            return;
        }

        System.out.println("✓ Query executed successfully");

        // Process results
        processResults(result, status);

        // Cleanup
        freeIndex(index);
        freeResult(result);
        freeStatus(status);

        System.out.println("✓ All resources cleaned up");
    }

    // Helper methods for FFI operations

    static MemorySegment allocateStatus() throws Throwable {
        MemorySegment status = MemorySegment.allocateNative(8 + 512); // sizeof(QleverStatus)
        status.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        status.set(ValueLayout.JAVA_INT, 8, 0); // code = 0
        return status;
    }

    static void setSuccess(MemorySegment status) {
        status.set(ValueLayout.JAVA_INT, 8, 0); // code = 0
        status.set(ValueLayout.JAVA_INT, 12, 0); // message[0] = '\0'
    }

    static int getStatusError(MemorySegment status) {
        return status.get(ValueLayout.JAVA_INT, 8);
    }

    static String getStatusMessage(MemorySegment status) {
        MemorySegment msgPtr = status.get(ValueLayout.ADDRESS, 0);
        if (msgPtr == MemorySegment.NULL) {
            throw new UnsupportedOperationException(
                "Status message pointer is null - this indicates either:"
                + " 1. Status structure not properly allocated"
                + " 2. Memory corruption"
                + " 3. QLever internal error"
            );
        }
        return msgPtr.getString(0);
    }

    static void freeStatus(MemorySegment status) throws Throwable {
        if (status != MemorySegment.NULL) {
            qlever_free_status.invokeExact(status);
        }
    }

    static MemorySegment loadIndex(String path, MemorySegment status) throws Throwable {
        MemorySegment pathSegment = MemorySegment.allocateNative(path.length() + 1);
        pathSegment.setString(0, path);

        MemorySegment index = (MemorySegment) qlever_index_create.invokeExact(
            pathSegment,
            status
        );

        // Free the path segment
        pathSegment.close();

        return index;
    }

    static void freeIndex(MemorySegment index) throws Throwable {
        if (index != MemorySegment.NULL) {
            qlever_index_destroy.invokeExact(index);
        }
    }

    static MemorySegment executeQuery(MemorySegment index, String query,
                                    int mediaType, MemorySegment status) throws Throwable {
        // Convert query to MemorySegment
        MemorySegment querySegment = MemorySegment.allocateNative(query.length() + 1);
        querySegment.setString(0, query);

        MemorySegment result = (MemorySegment) qlever_query_exec.invokeExact(
            index,
            querySegment,
            mediaType,
            status
        );

        // Free the query segment
        querySegment.close();

        return result;
    }

    static void freeResult(MemorySegment result) throws Throwable {
        if (result != MediaSegment.NULL) {
            qlever_result_destroy.invokeExact(result);
        }
    }

    static boolean resultHasNext(MemorySegment result) throws Throwable {
        int hasNext = (int) qlever_result_has_next.invokeExact(result);
        return hasNext != 0;
    }

    static String resultNext(MemorySegment result, MemorySegment status) throws Throwable {
        MemorySegment linePtr = (MemorySegment) qlever_result_next.invokeExact(
            result,
            status
        );

        if (linePtr == MemorySegment.NULL) {
            return null;
        }

        return linePtr.getString(0);
    }

    static void processResults(MemorySegment result, MemorySegment status) throws Throwable {
        int rowCount = 0;
        while (resultHasNext(result)) {
            String line = resultNext(result, status);
            if (line != null) {
                System.out.println("  " + line);
                rowCount++;
            } else {
                int errCode = getStatusError(status);
                if (errCode != 0) {
                    System.err.println("Error reading result: " + getStatusMessage(status));
                    break;
                }
            }
        }

        System.out.println("Processed " + rowCount + " result lines");
    }

    // Convenience constants for media types
    public static final int MEDIA_JSON = 0;
    public static final int MEDIA_TSV = 1;
    public static final int MEDIA_CSV = 2;
    public static final int MEDIA_TURTLE = 3;
    public static final int MEDIA_XML = 4;
}