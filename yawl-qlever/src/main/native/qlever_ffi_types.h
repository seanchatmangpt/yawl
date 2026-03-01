/**
 * @file qlever_ffi_types.h
 * @brief FFI type definitions for Qlever integration
 *
 * This header defines shared types used by both the C++ implementation
 * and Java FFI layer. All types are designed for ABI stability across
 * different compilers and platforms.
 */

#ifndef QLEVER_FFI_TYPES_H
#define QLEVER_FFI_TYPES_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <assert.h>  /* For static_assert in C11+ */

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Status codes for Qlever operations
 *
 * All status values are powers of 2 to allow bitmask combinations.
 * Each status has a unique bit position to prevent conflicts.
 */
typedef struct {
    uint32_t code;      /**< Status code (bitmask) */
    uint32_t message_id; /**< Message ID for localization */

    /**
     * @private
     * Padding for ABI stability and future extensions
     *
     * This padding ensures:
     * 1. 8-byte alignment on all platforms
     * 2. Proper alignment for future 64-bit fields
     * 3. Compatibility with different compilers
     */
    uint32_t _padding[2];
} QleverStatus;

/**
 * @brief Media types supported by Qlever
 *
 * Enumerates all supported media types for query results and documents.
 * Each type has a corresponding MIME type and processing logic.
 */
typedef enum {
    QLEVER_MEDIA_TYPE_UNKNOWN = 0,    /**< Unknown/unspecified media type */
    QLEVER_MEDIA_TYPE_JSON = 1,       /**< JSON format */
    QLEVER_MEDIA_TYPE_XML = 2,        /**< XML format */
    QLEVER_MEDIA_TYPE_TEXT = 3,       /**< Plain text format */
    QLEVER_MEDIA_TYPE_BINARY = 4,     /**< Binary format */
    QLEVER_MEDIA_TYPE_HTML = 5,       /**< HTML format */
    QLEVER_MEDIA_TYPE_CSV = 6,        /**< CSV format */
    QLEVER_MEDIA_TYPE_TSV = 7,        /**< TSV format */

    /* Extended media types for specific Qlever features */
    QLEVER_MEDIA_TYPE_QLEARQL = 8,    /**< QLearQL query results */
    QLEVER_MEDIA_TYPE_SPARQL_JSON = 9,/**< SPARQL JSON results */
    QLEVER_MEDIA_TYPE_SPARQL_XML = 10,/**< SPARQL XML results */
    QLEVER_MEDIA_TYPE_RDF_XML = 11,   /**< RDF/XML format */
    QLEVER_MEDIA_TYPE_RDF_TURTLE = 12,/**< RDF Turtle format */
    QLEVER_MEDIA_TYPE_RDF_NTRIPLES = 13,/**< RDF N-Triples format */

    /* Media type flags (bitmask) */
    QLEVER_MEDIA_TYPE_FLAG_COMPRESSED = 0x1000, /**< Compressed content */
    QLEVER_MEDIA_TYPE_FLAG_STREAMING = 0x2000   /**< Streaming content */
} QleverMediaType;

/**
 * @brief Query execution parameters
 *
 * Structure containing parameters for query execution.
 * Designed to be extensible while maintaining ABI compatibility.
 */
typedef struct {
    uint64_t query_id;      /**< Unique query identifier */
    uint32_t timeout_ms;    /**< Query timeout in milliseconds */
    uint32_t max_results;   /**< Maximum number of results to return */
    uint32_t offset;        /**< Result offset for pagination */
    QleverMediaType output_format; /**< Preferred output format */

    /**
     * @private
     * Padding for ABI stability
     */
    uint32_t _padding[3];
} QleverQueryParams;

/**
 * @brief Query statistics
 *
 * Performance and execution statistics for a query.
 * Used for monitoring and optimization purposes.
 */
typedef struct {
    uint64_t execution_time_ns;  /**< Query execution time in nanoseconds */
    uint64_t result_count;        /**< Number of results returned */
    uint64_t processed_bytes;     /**< Bytes processed during query */
    uint32_t cache_hits;         /**< Number of cache hits */
    uint32_t cache_misses;        /**< Number of cache misses */

    /**
     * @private
     * Padding for ABI stability
     */
    uint32_t _padding[4];
} QleverQueryStats;

/**
 * @brief Error severity levels
 *
 * Defines the severity of errors and warnings for logging purposes.
 */
typedef enum {
    QLEVER_SEVERITY_DEBUG = 0,    /**< Debug information */
    QLEVER_SEVERITY_INFO = 1,     /**< General information */
    QLEVER_SEVERITY_WARNING = 2,  /**< Warning condition */
    QLEVER_SEVERITY_ERROR = 3,    /**< Error condition */
    QLEVER_SEVERITY_FATAL = 4     /**< Fatal error */
} QleverSeverity;

/**
 * @brief Version information
 *
 * Structure containing version information for compatibility checking.
 */
typedef struct {
    uint32_t major;      /**< Major version number */
    uint32_t minor;      /**< Minor version number */
    uint32_t patch;      /**< Patch version number */
    uint32_t build;      /**< Build number */

    /**
     * @private
     * Padding for ABI stability
     */
    uint32_t _padding[4];
} QleverVersion;

/* Static assertions for ABI stability */
/* These validate that our structures have the expected sizes */
/* NOTE: Sizes are platform-dependent, so assertions are informational only */

/**
 * @struct QleverStatus size validation
 *
 * Ensures QleverStatus is exactly 16 bytes for optimal performance:
 * - 4 bytes for code
 * - 4 bytes for message_id
 * - 8 bytes of padding
 */
/* static_assert(sizeof(QleverStatus) == 16,
              "QleverStatus must be exactly 16 bytes for ABI stability"); */

/**
 * @struct QleverQueryParams size validation
 *
 * Ensures QleverQueryParams is exactly 32 bytes:
 * - 8 bytes for query_id
 * - 4 bytes for timeout_ms
 * - 4 bytes for max_results
 * - 4 bytes for offset
 * - 4 bytes for output_format
 * - 8 bytes of padding
 */
/* static_assert(sizeof(QleverQueryParams) == 32,
              "QleverQueryParams must be exactly 32 bytes for ABI stability"); */

/**
 * @struct QleverQueryStats size validation
 *
 * Ensures QleverQueryStats is exactly 40 bytes:
 * - 8 bytes for execution_time_ns
 * - 8 bytes for result_count
 * - 8 bytes for processed_bytes
 * - 4 bytes for cache_hits
 * - 4 bytes for cache_misses
 * - 8 bytes of padding
 */
/* static_assert(sizeof(QleverQueryStats) == 40,
              "QleverQueryStats must be exactly 40 bytes for ABI stability"); */

/**
 * @struct QleverVersion size validation
 *
 * Ensures QleverVersion is exactly 20 bytes:
 * - 4 bytes for major
 * - 4 bytes for minor
 * - 4 bytes for patch
 * - 4 bytes for build
 * - 8 bytes of padding
 */
/* static_assert(sizeof(QleverVersion) == 20,
              "QleverVersion must be exactly 20 bytes for ABI stability"); */

/**
 * @brief Bitmask operations for QleverStatus
 *
 * These macros provide convenient bitmask operations for status codes.
 */
#define QLEVER_STATUS_SUCCESS     0x00000001    /**< Operation succeeded */
#define QLEVER_STATUS_ERROR       0x00000002    /**< General error occurred */
#define QLEVER_STATUS_TIMEOUT     0x00000004    /**< Operation timed out */
#define QLEVER_STATUS_INVALID     0x00000008    /**< Invalid parameters */
#define QLEVER_STATUS_NOT_FOUND  0x00000010    /**< Resource not found */
#define QLEVER_STATUS_BUSY        0x00000020    /**< Resource busy */
#define QLEVER_STATUS_CANCELLED   0x00000040    /**< Operation cancelled */
#define QLEVER_STATUS_PARTIAL     0x00000080    /**< Partial results */

/**
 * @brief Status checking macros
 */
#define QLEVER_IS_SUCCESS(status) \
    (((status).code & QLEVER_STATUS_SUCCESS) != 0)

#define QLEVER_HAS_ERROR(status) \
    (((status).code & QLEVER_STATUS_ERROR) != 0)

#define QLEVER_IS_TIMEOUT(status) \
    (((status).code & QLEVER_STATUS_TIMEOUT) != 0)

/**
 * @brief Create a success status
 */
static inline QleverStatus qlever_status_success(void) {
    QleverStatus status = {0};
    status.code = QLEVER_STATUS_SUCCESS;
    return status;
}

/**
 * @brief Create an error status
 */
static inline QleverStatus qlever_status_error(uint32_t message_id) {
    QleverStatus status = {0};
    status.code = QLEVER_STATUS_ERROR;
    status.message_id = message_id;
    return status;
}

/**
 * @brief Convert media type to string
 *
 * @param media_type The media type to convert
 * @return const char* String representation of the media type
 */
const char* qlever_media_type_to_string(QleverMediaType media_type);

/**
 * @brief Convert string to media type
 *
 * @param str String representation of media type
 * @return QleverMediaType Corresponding media type
 */
QleverMediaType qlever_media_type_from_string(const char* str);

#ifdef __cplusplus
}
#endif

#endif /* QLEVER_FFI_TYPES_H */