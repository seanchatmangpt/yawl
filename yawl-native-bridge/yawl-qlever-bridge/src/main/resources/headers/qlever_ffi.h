/*
 * QLever FFI Header - Hourglass Facade for YAWL Bridge
 *
 * This C header defines the external C interface that wraps the QLever C++ engine
 * for integration via Panama FFI. All functions use "C" linkage for Java interop.
 */

#ifndef QLEVER_FFI_H
#define QLEVER_FFI_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stddef.h>

// Forward declarations for opaque handles
typedef struct QleverEngineHandle* QleverEngineHandle;
typedef struct QleverResultHandle* QleverResultHandle;

// Status struct for error reporting
typedef struct QleverStatus {
    int code;                    // Status code: 0=success, >0=error
    char message[256];          // Error message buffer
} QleverStatus;

// Function return macros for easy error checking
#define QLEVER_SUCCESS(status) ((status).code == 0)
#define QLEVER_FAILURE(status) ((status).code != 0)

/*
 * Hourglass Facade Functions
 *
 * These 8-12 functions provide a minimal, safe interface to the QLever engine
 * without exposing C++ complexities to the JVM.
 */

/**
 * Create a new QLever engine instance
 *
 * @param index_path Path to the QLever index directory
 * @param out Output handle for the created engine
 * @return QleverStatus with creation result
 */
QleverStatus qlever_engine_create(const char* index_path, QleverEngineHandle* out);

/**
 * Execute a SPARQL query on the engine
 *
 * @param engine Valid engine handle
 * @param sparql SPARQL query string
 * @param out Output handle for the result
 * @return QleverStatus with query execution result
 */
QleverStatus qlever_engine_query(QleverEngineHandle engine, const char* sparql, QleverResultHandle* out);

/**
 * Get result data as JSON string
 *
 * @param result Valid result handle
 * @param data_out Output pointer to JSON data (caller must free)
 * @param len_out Output length of JSON data
 * @return QleverStatus with data extraction result
 */
QleverStatus qlever_result_get_data(QleverResultHandle result, char** data_out, size_t* len_out);

/**
 * Destroy engine instance and free resources
 *
 * @param engine Engine handle to destroy (can be NULL)
 */
void qlever_engine_destroy(QleverEngineHandle engine);

/**
 * Destroy result instance and free resources
 *
 * @param result Result handle to destroy (can be NULL)
 */
void qlever_result_destroy(QleverResultHandle result);

/**
 * Initialize QLever global state (call once at startup)
 *
 * @return QleverStatus with initialization result
 */
QleverStatus qlever_initialize(void);

/**
 * Shutdown QLever global state (call once at shutdown)
 *
 * @return QleverStatus with shutdown result
 */
QleverStatus qlever_shutdown(void);

/**
 * Get QLever version information
 *
 * @param version_out Output buffer for version string
 * @param version_len Length of version buffer
 * @return QleverStatus with version retrieval result
 */
QleverStatus qlever_get_version(char* version_out, size_t version_len);

/**
 * Validate SPARQL query syntax without execution
 *
 * @param sparql SPARQL query string to validate
 * @param is_valid Output flag indicating validity
 * @param error_msg Output buffer for error message if invalid
 * @param error_len Length of error message buffer
 * @return QleverStatus with validation result
 */
QleverStatus qlever_validate_query(const char* sparql, int* is_valid, char* error_msg, size_t error_len);

#ifdef __cplusplus
}
#endif

#endif /* QLEVER_FFI_H */