/**
 * QLever Hourglass C++ Façade Header
 *
 * This header provides the extern "C" interface for QLever's native C++ implementation
 * that will be consumed by jextract to generate Java bindings.
 *
 * Design Principles:
 * 1. Minimal surface area - only expose required functions
 * 2. Memory safety - clear ownership semantics
 * 3. Exception safety - Lippincott pattern for C++→C conversion
 * 4. No HTTP - pure in-process function calls
 * 5. Thread-safe - all functions are reentrant
 */

#ifndef QLEVER_FFI_H
#define QLEVER_FFI_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * QLever Status Structure
 *
 * Matches the Java QleverStatus layout for seamless FFI binding.
 * This struct follows the Lippincott pattern for exception handling.
 *
 * Memory Layout:
 * - int32_t code: Status code (0=OK, 1+=error)
 * - int32_t padding: Alignment padding for 64-bit pointers
 * - const char* message: Error message (NULL if OK)
 * - void* data: Additional result data (if any)
 *
 * Ownership:
 * - The caller owns the memory containing this struct
 * - The message string is owned by the implementation until freed
 */
typedef struct {
    int32_t code;
    int32_t padding;  // 4-byte alignment for 64-bit pointers
    const char* message;
    void* data;
} QleverStatus;

// Status codes matching Java implementation
#define QLEVER_STATUS_OK                  0
#define QLEVER_STATUS_ERROR_PARSE         1
#define QLEVER_STATUS_ERROR_EXECUTION     2
#define QLEVER_STATUS_ERROR_TIMEOUT      3
#define QLEVER_STATUS_ERROR_MEMORY        4
#define QLEVER_STATUS_ERROR_CONFIG         5

// Result handle type (opaque pointer)
typedef void* QleverResultHandle;

/**
 * Creates a new QLever engine instance
 *
 * This function initializes the QLever engine with the specified index path.
 * It follows the RAII pattern and allocates resources that must be freed
 * using qlever_engine_destroy().
 *
 * @param index_path Path to the QLever index directory (UTF-8 string)
 * @param out_handle Output parameter for the engine handle (opaque pointer)
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the engine handle
 * - The implementation owns the index_path string (copied)
 * - On success, out_handle contains a valid engine handle
 * - On failure, out_handle is set to NULL
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_engine_create(
    const char* index_path,
    void** out_handle
);

/**
 * Executes a SPARQL query against the QLever engine
 *
 * This function executes a SPARQL query and returns a result handle.
 * The result contains the query results that can be retrieved using
 * qlever_result_get_data().
 *
 * @param engine_handle Handle to the QLever engine (from qlever_engine_create)
 * @param sparql SPARQL query string (UTF-8)
 * @param out_result Output parameter for the result handle
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns both input handles
 * - The caller owns the output result handle
 * - The implementation owns the sparql string (copied)
 * - The caller must free the result using qlever_result_free()
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_engine_query(
    void* engine_handle,
    const char* sparql,
    void** out_result
);

/**
 * Retrieves result data from a query result handle
 *
 * This function extracts the JSON result data from a query result.
 * The data is returned as a null-terminated string that the caller must free.
 *
 * @param result_handle Handle to the query result (from qlever_engine_query)
 * @param out_data Output parameter for the result data JSON string
 * @param out_len Output parameter for the result data length (optional, can be NULL)
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the input result handle
 * - The caller owns the output data string (must be freed)
 * - The implementation allocates the data string using malloc()
 * - Caller responsibility: free(*out_data) when done
 * - out_len can be NULL if length is not needed
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_result_get_data(
    void* result_handle,
    const char** out_data,
    size_t* out_len
);

/**
 * Frees a query result handle and associated resources
 *
 * This function frees the memory associated with a query result handle.
 * It should be called after the result data has been processed.
 *
 * @param result_handle Handle to the query result to free
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the input result handle
 * - After this call, the handle is invalid and should not be used
 * - The implementation frees all associated memory
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_result_free(
    void* result_handle
);

/**
 * Destroys a QLever engine instance
 *
 * This function shuts down the QLever engine and frees all associated resources.
 * It should be called when the engine is no longer needed.
 *
 * @param engine_handle Handle to the QLever engine to destroy
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the input engine handle
 * - After this call, the handle is invalid and should not be used
 * - The implementation frees all engine resources including memory handles
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_engine_destroy(
    void* engine_handle
);

/**
 * Frees a QLever status message
 *
 * This function frees the memory associated with a status message.
 * It should be called when the status struct is no longer needed.
 *
 * @param status Pointer to the QleverStatus struct
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the status struct
 * - The implementation frees the message string if it's not NULL
 * - The status struct itself is not freed (caller owns it)
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_status_free_message(
    const QleverStatus* status
);

/**
 * Gets the human-readable string for a status code
 *
 * This function provides a descriptive string for status codes.
 * The returned string is a static string and does not need to be freed.
 *
 * @param code Status code to convert
 * @return Human-readable description (static string)
 *
 * Thread Safety: Safe to call from multiple threads
 */
const char* qlever_status_string(int32_t code);

/**
 * Validates a SPARQL query without executing it
 *
 * This function checks the syntax of a SPARQL query without executing it.
 * It's useful for query validation before execution.
 *
 * @ sparql SPARQL query string to validate
 * @param out_valid Output parameter for validation result (true if valid)
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the input sparql string
 * - The implementation does not store any references
 * - out_valid is always set (true/false)
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_validate_query(
    const char* sparql,
    int* out_valid
);

/**
 * Gets the QLever version string
 *
 * This function returns the version information of the QLever engine.
 * The returned string is static and does not need to be freed.
 *
 * @return Version string (static string)
 *
 * Thread Safety: Safe to call from multiple threads
 */
const char* qlever_get_version(void);

/**
 * Gets the QLever engine information
 *
 * This function returns detailed information about the QLever engine.
 * The returned JSON string must be freed by the caller.
 *
 * @param out_info Output parameter for engine info JSON string
 * @param out_len Output parameter for info string length (optional)
 * @return QleverStatus with code and optional error message
 *
 * Memory Safety:
 * - The caller owns the output info string (must be freed)
 * - The implementation allocates the string using malloc()
 * - Caller responsibility: free(*out_info) when done
 *
 * Thread Safety: Safe to call from multiple threads
 */
QleverStatus qlever_get_engine_info(
    const char** out_info,
    size_t* out_len
);

#ifdef __cplusplus
}
#endif

#endif // QLEVER_FFI_H