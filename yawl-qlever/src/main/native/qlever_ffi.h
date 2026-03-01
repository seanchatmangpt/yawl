/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * QLever FFI Header - Hourglass Pattern C API
 *
 * This header defines the C façade for QLever C++ engine, enabling
 * Java Panama FFM integration without C++ name mangling.
 *
 * All functions use extern "C" linkage for stable ABI compatibility.
 */

#ifndef QLEVER_FFI_H
#define QLEVER_FFI_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Status codes for QLever operations */
#define QLEVER_STATUS_SUCCESS         0
#define QLEVER_STATUS_ERROR          -1
#define QLEVER_STATUS_INVALID_ARGUMENT -2
#define QLEVER_STATUS_RUNTIME_ERROR   -3
#define QLEVER_STATUS_BAD_ALLOC       -4
#define QLEVER_STATUS_UNKNOWN_ERROR  -99
#define QLEVER_STATUS_INDEX_NOT_LOADED -10
#define QLEVER_STATUS_PARSE_ERROR    -20

/**
 * Status structure for error reporting.
 *
 * All functions return QleverStatus* for error checking.
 * The caller must check status->code != 0 for errors and
 * free the status with qlever_free_status() when done.
 */
typedef struct {
    int32_t code;           // Error code: 0 = success, negative = error
    char message[512];      // Error message (null-terminated)
} QleverStatus;

/**
 * Media type enumeration for query results.
 *
 * Used to specify the desired output format for queries.
 */
typedef enum {
    QLEVER_MEDIA_JSON = 0,    // application/sparql-results+json
    QLEVER_MEDIA_TSV = 1,     // text/tab-separated-values
    QLEVER_MEDIA_CSV = 2,     // text/csv
    QLEVER_MEDIA_TURTLE = 3,  // text/turtle
    QLEVER_MEDIA_XML = 4      // application/sparql-results+xml
} QleverMediaType;

/**
 * Opaque handle to QLever Index.
 *
 * Represents a loaded QLever index ready for query execution.
 * Must be destroyed via qlever_index_destroy() when no longer needed.
 */
typedef struct QLeverIndex QLeverIndex;

/**
 * Opaque handle to query result.
 *
 * Represents the result of a SPARQL query execution.
 * Results are streamed line-by-line via qlever_result_next().
 * Must be destroyed via qlever_result_destroy() when consumed.
 */
typedef struct QLeverResult QLeverResult;

/* ============================================================================
 * Index Lifecycle
 * ============================================================================ */

/**
 * Creates a new QLever index handle from a pre-built index directory.
 *
 * @param index_path Path to the QLever index directory containing:
 *                   - .index.pbm (permutation index)
 *                   - .index.pso (predicate-subject-object)
 *                   - .index.pos (predicate-object-subject)
 *                   - .index.patterns (patterns file)
 *                   - .index.prefixes (prefix compression)
 * @param status     Pointer to QleverStatus for error reporting
 * @return Pointer to QLeverIndex on success, NULL on failure
 *
 * Thread Safety: The returned index can be shared across threads for
 *                concurrent read-only queries.
 */
QLeverIndex* qlever_index_create(const char* index_path, QleverStatus* status);

/**
 * Destroys a QLever index handle and releases all associated resources.
 *
 * @param index The index handle to destroy (may be NULL)
 *
 * Note: After this call, the index pointer is invalid and must not be used.
 */
void qlever_index_destroy(QLeverIndex* index);

/**
 * Checks if the index is successfully loaded and ready for queries.
 *
 * @param index The index handle to check
 * @return true if index is loaded and operational, false otherwise
 */
int qlever_index_is_loaded(const QLeverIndex* index);

/**
 * Returns the number of triples in the index.
 *
 * @param index  The index handle
 * @param status Pointer to QleverStatus for error reporting
 * @return Number of triples, or 0 if index is NULL or not loaded
 */
size_t qlever_index_triple_count(const QLeverIndex* index, QleverStatus* status);

/* ============================================================================
 * Query Execution
 * ============================================================================ */

/**
 * Executes a SPARQL query against the loaded index.
 *
 * @param index           The index handle (must be valid and loaded)
 * @param sparql_query    SPARQL 1.1 query string (SELECT, CONSTRUCT, ASK)
 * @param media_type      Desired response format (QleverMediaType enum)
 * @param status          Pointer to QleverStatus for error reporting
 * @return Pointer to QLeverResult on success, NULL on failure
 *
 * Thread Safety: Multiple threads can execute queries concurrently on the same index.
 *                Each QLeverResult is independent and thread-safe for single-consumer.
 *                WARNING: QLever's QueryExecutionContext is not thread-safe for
 *                concurrent execution. This function creates a new context per query.
 */
QLeverResult* qlever_query_exec(
    QLeverIndex* index,
    const char* sparql_query,
    QleverMediaType media_type,
    QleverStatus* status
);

/* ============================================================================
 * Result Iteration
 * ============================================================================ */

/**
 * Checks if the result has more lines to consume.
 *
 * @param result The result handle
 * @return 1 if qlever_result_next() will return another line, 0 otherwise
 *
 * Note: Returns 0 if result is NULL or if all lines have been consumed.
 */
int qlever_result_has_next(const QLeverResult* result);

/**
 * Returns the next line of the result.
 *
 * @param result The result handle
 * @param status Pointer to QleverStatus for error reporting
 * @return Pointer to null-terminated string (valid until next call or destroy)
 *         NULL if no more lines or result is NULL
 *
 * Memory: The returned pointer is valid until:
 *         - The next call to qlever_result_next()
 *         - The result is destroyed
 *         The caller must copy the string if persistence is needed.
 *
 * Thread Safety: This function is NOT thread-safe for concurrent access to
 *               the same result handle. Each thread should have its own
 *               result handle or access results sequentially.
 */
const char* qlever_result_next(QLeverResult* result, QleverStatus* status);

/**
 * Destroys a result handle and releases all associated resources.
 *
 * @param result The result handle to destroy (may be NULL)
 */
void qlever_result_destroy(QLeverResult* result);

/* ============================================================================
 * Error Handling
 * ============================================================================ */

/**
 * Checks if the result has an error.
 *
 * @param result The result handle
 * @return true if error occurred, false if successful
 */
int qlever_result_has_error(const QLeverResult* result);

/**
 * Returns the error message if query execution failed.
 *
 * @param result The result handle
 * @return Pointer to error message string, or NULL if no error occurred
 *
 * Memory: The returned pointer is valid for the lifetime of the result.
 */
const char* qlever_result_error(const QLeverResult* result);

/**
 * Returns the HTTP status code for the query result.
 *
 * @param result The result handle
 * @param status Pointer to QleverStatus for error reporting
 * @return HTTP status code (200 for success, 400 for bad query, 500 for server error)
 *         0 if result is NULL or query hasn't been executed
 */
int qlever_result_status(const QLeverResult* result, QleverStatus* status);

/* ============================================================================
 * Index Building (Optional - for programmatic index creation)
 * ============================================================================ */

/**
 * Creates a new QLever index from TTL/N-Triples input.
 *
 * @param input_path   Path to input file (.ttl or .nt)
 * @param output_path  Directory where index files will be written
 * @param settings     JSON settings string (may be NULL for defaults)
 * @param status       Pointer to QleverStatus for error reporting
 * @return 0 on success, non-zero error code on failure
 *
 * Note: This is a blocking operation that may take minutes to hours
 *       depending on input size. For large datasets, use the QLever
 *       CLI IndexBuilderMain tool instead.
 */
int qlever_index_build(
    const char* input_path,
    const char* output_path,
    const char* settings,
    QleverStatus* status
);

/* ============================================================================
 * Memory Management
 * ============================================================================ */

/**
 * Frees a QleverStatus structure and its resources.
 *
 * @param status The status structure to free (may be NULL)
 */
void qlever_free_status(QleverStatus* status);

/**
 * Frees a string returned by QLever functions.
 *
 * @param string The string to free (may be NULL)
 */
void qlever_free_string(char* string);

/**
 * Frees a QLever result and all associated resources.
 *
 * @param result The result to free (may be NULL)
 */
void qlever_result_free(QLeverResult* result);

/**
 * Frees a QLever index and all associated resources.
 *
 * @param index The index to free (may be NULL)
 */
void qlever_index_free(QLeverIndex* index);

/* ============================================================================
 * Media Type Utilities
 * ============================================================================ */

/**
 * Converts a QleverMediaType to its corresponding MIME type string.
 *
 * @param media_type The media type enum value
 * @param status     Pointer to QleverStatus for error reporting
 * @return Pointer to MIME type string (e.g., "application/sparql-results+json")
 *         NULL if invalid media type
 *
 * Memory: Caller must free the returned string with qlever_free_string()
 */
char* qlever_media_type_to_mime(QleverMediaType media_type, QleverStatus* status);

/**
 * Converts a MIME type string to QleverMediaType enum.
 *
 * @param mime_type  The MIME type string
 * @param status     Pointer to QleverStatus for error reporting
 * @return QleverMediaType enum value, or -1 for invalid type
 */
int32_t qlever_mime_to_media_type(const char* mime_type, QleverStatus* status);

#ifdef __cplusplus
}
#endif

#endif /* QLEVER_FFI_H */
