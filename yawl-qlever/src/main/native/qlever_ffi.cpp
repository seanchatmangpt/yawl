/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * QLever FFI Implementation - Lippincott Pattern with Centralized Exception Handling
 *
 * This file implements the C façade that wraps QLever C++ classes,
 * enabling Java Panama FFM integration with robust error handling.
 *
 * Build modes:
 *   - QLEVER_STUB defined: Stub implementation for testing (no QLever headers)
 *   - QLEVER_STUB undefined: Full QLever integration (requires QLever installed)
 */

#include "qlever_ffi.h"

#include <stdexcept>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <memory>
#include <cstring>

#ifndef QLEVER_STUB
// QLever C++ headers (only included when building with QLever)
#include "engine/QueryExecutionContext.h"
#include "engine/QueryPlanner.h"
#include "engine/ResultTable.h"
#include "index/Index.h"
#include "parser/SparqlParser.h"
#include "util/Exception.h"
#include "util/HashMap.h"
#include "util/ConcurrentHashMap.h"
#endif // QLEVER_STUB

/* ============================================================================
 * Lippincott Pattern Exception Translation
 * ============================================================================ */

// Centralized exception translation function
static void translate_exception(const std::exception& e, QleverStatus* status) {
    if (!status) return;

    // Clear previous error
    status->code = QLEVER_STATUS_ERROR;
    status->message[0] = '\0';

    const char* errorMsg = e.what();

    // Map specific exception types to appropriate error codes
    try {
        if (dynamic_cast<const std::invalid_argument*>(&e)) {
            status->code = QLEVER_STATUS_INVALID_ARGUMENT;
            snprintf(status->message, sizeof(status->message), "Invalid argument: %s", errorMsg);
        } else if (dynamic_cast<const std::runtime_error*>(&e)) {
            status->code = QLEVER_STATUS_RUNTIME_ERROR;
            snprintf(status->message, sizeof(status->message), "Runtime error: %s", errorMsg);
        } else if (dynamic_cast<const std::bad_alloc*>(&e)) {
            status->code = QLEVER_STATUS_BAD_ALLOC;
            snprintf(status->message, sizeof(status->message), "Memory allocation failed: %s", errorMsg);
        } else {
            status->code = QLEVER_STATUS_ERROR;
            snprintf(status->message, sizeof(status->message), "Standard exception: %s", errorMsg);
        }
    } catch (...) {
        // Dynamic cast failed - treat as generic exception
        status->code = QLEVER_STATUS_ERROR;
        snprintf(status->message, sizeof(status->message), "Exception type unknown: %s", errorMsg);
    }
}

static void translate_unknown_exception(QleverStatus* status) {
    if (!status) return;

    status->code = QLEVER_STATUS_UNKNOWN_ERROR;
    snprintf(status->message, sizeof(status->message), "Unknown exception occurred");
}

/* ============================================================================
 * Helper Functions (consolidated from qlever_ffi_types.c)
 * ============================================================================ */

/**
 * Media type to MIME string mapping table.
 * Consolidated from qlever_ffi_types.c to eliminate cross-compilation-unit
 * linkage complexity between C and C++ translation units.
 */
static const struct {
    QleverMediaType type;
    const char* string;
} media_type_mappings[] = {
    {QLEVER_MEDIA_TYPE_UNKNOWN, "unknown"},
    {QLEVER_MEDIA_TYPE_JSON, "application/json"},
    {QLEVER_MEDIA_TYPE_XML, "application/xml"},
    {QLEVER_MEDIA_TYPE_TEXT, "text/plain"},
    {QLEVER_MEDIA_TYPE_BINARY, "application/octet-stream"},
    {QLEVER_MEDIA_TYPE_HTML, "text/html"},
    {QLEVER_MEDIA_TYPE_CSV, "text/csv"},
    {QLEVER_MEDIA_TYPE_TSV, "text/tab-separated-values"},
    {QLEVER_MEDIA_TYPE_QLEARQL, "application/x-ql-qlearql"},
    {QLEVER_MEDIA_TYPE_SPARQL_JSON, "application/sparql-results+json"},
    {QLEVER_MEDIA_TYPE_SPARQL_XML, "application/sparql-results+xml"},
    {QLEVER_MEDIA_TYPE_RDF_XML, "application/rdf+xml"},
    {QLEVER_MEDIA_TYPE_RDF_TURTLE, "text/turtle"},
    {QLEVER_MEDIA_TYPE_RDF_NTRIPLES, "application/n-triples"}
};

/**
 * @brief Convert media type to string
 *
 * @param media_type The media type to convert
 * @return const char* String representation of the media type
 */
static const char* media_type_to_string(QleverMediaType media_type) {
    for (size_t i = 0; i < sizeof(media_type_mappings) / sizeof(media_type_mappings[0]); i++) {
        if (media_type_mappings[i].type == media_type) {
            return media_type_mappings[i].string;
        }
    }
    return "unknown";
}

/**
 * @brief Convert string to media type
 *
 * @param str String representation of media type
 * @return QleverMediaType Corresponding media type
 *
 * Consolidated from qlever_ffi_types.c
 */
static QleverMediaType media_type_from_string(const char* str) {
    if (!str) return QLEVER_MEDIA_TYPE_UNKNOWN;

    for (size_t i = 0; i < sizeof(media_type_mappings) / sizeof(media_type_mappings[0]); i++) {
        if (strcmp(media_type_mappings[i].string, str) == 0) {
            return media_type_mappings[i].type;
        }
    }
    return QLEVER_MEDIA_TYPE_UNKNOWN;
}

/* ============================================================================
 * Internal Structures
 * ============================================================================ */

#ifdef QLEVER_STUB
// Stub mode: Simple index handle without QLever types
struct QLeverIndex {
    std::string indexPath;
    bool loaded = false;
};
#else
// Full QLever mode: Real index with QLever types
struct QLeverIndex {
    std::unique_ptr<Index> index;
    std::string indexPath;
    bool loaded = false;
};
#endif

struct QLeverResult {
    std::string resultString;
    std::vector<std::string> lines;
    int64_t rowCount = 0;
    int statusCode = 0;
    size_t currentLine = 0;
};

/* ============================================================================
 * Status Management
 * ============================================================================ */

QleverStatus* qlever_create_status() {
    return new QleverStatus();
}

void qlever_free_status(QleverStatus* status) {
    delete status;
}

/* ============================================================================
 * Index Lifecycle Implementation
 * ============================================================================ */

extern "C" {

QLeverIndex* qlever_index_create(const char* index_path, QleverStatus* status) {
    // Initialize status if not provided
    bool status_ownership = false;
    if (!status) {
        status = qlever_create_status();
        status_ownership = true;
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    // Validate input path
    if (!index_path) {
        status->code = QLEVER_STATUS_INVALID_ARGUMENT;
        snprintf(status->message, sizeof(status->message), "Index path cannot be null");
        if (status_ownership) {
            delete status;
            status = nullptr;
        }
        return nullptr;
    }

    auto* wrapper = new QLeverIndex();
    wrapper->indexPath = index_path;

#ifdef QLEVER_STUB
    // Stub mode: Cannot actually load QLever index
    status->code = QLEVER_STATUS_ERROR;
    snprintf(status->message, sizeof(status->message),
             "QLever not available (stub mode). Build with QLever for full functionality.");
    delete wrapper;
    if (status_ownership) {
        delete status;
        status = nullptr;
    }
    return nullptr;
#else
    try {
        wrapper->index = std::make_unique<Index>();
        wrapper->index->readPermanentMutableRelationData(wrapper->indexPath);
        wrapper->loaded = true;
        return wrapper;
    } catch (const std::exception& e) {
        translate_exception(e, status);
        delete wrapper;
        if (status_ownership) {
            delete status;
            status = nullptr;
        }
        return nullptr;
    } catch (...) {
        translate_unknown_exception(status);
        delete wrapper;
        if (status_ownership) {
            delete status;
            status = nullptr;
        }
        return nullptr;
    }
#endif
}

void qlever_index_destroy(QLeverIndex* index) {
    delete index;
}

int qlever_index_is_loaded(const QLeverIndex* index) {
#ifdef QLEVER_STUB
    return index && index->loaded ? 1 : 0;
#else
    return index && index->loaded && index->index != nullptr ? 1 : 0;
#endif
}

size_t qlever_index_triple_count(const QLeverIndex* index, QleverStatus* status) {
    if (!status) {
        // Create status if not provided
        status = qlever_create_status();
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

#ifdef QLEVER_STUB
    // Stub mode: Return 0 since no real index is loaded
    if (!index || !index->loaded) {
        status->code = QLEVER_STATUS_INDEX_NOT_LOADED;
        snprintf(status->message, sizeof(status->message), "Index not loaded or invalid (stub mode)");
        return 0;
    }
    status->code = QLEVER_STATUS_ERROR;
    snprintf(status->message, sizeof(status->message), "QLever not available (stub mode)");
    return 0;
#else
    if (!index || !index->index || !index->loaded) {
        status->code = QLEVER_STATUS_INDEX_NOT_LOADED;
        snprintf(status->message, sizeof(status->message), "Index not loaded or invalid");
        return 0;
    }

    try {
        return index->index->numTriples();
    } catch (const std::exception& e) {
        translate_exception(e, status);
        return 0;
    } catch (...) {
        translate_unknown_exception(status);
        return 0;
    }
#endif
}

/* ============================================================================
 * Query Execution Implementation
 * ============================================================================ */

QLeverResult* qlever_query_exec(
    QLeverIndex* index,
    const char* sparql_query,
    QleverMediaType media_type,
    QleverStatus* status
) {
    // Initialize status if not provided
    bool status_ownership = false;
    if (!status) {
        status = qlever_create_status();
        status_ownership = true;
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    auto* result = new QLeverResult();

#ifdef QLEVER_STUB
    // Stub mode: Cannot execute queries without QLever
    // Validate inputs for proper error messages
    if (!index || !index->loaded) {
        status->code = QLEVER_STATUS_INDEX_NOT_LOADED;
        snprintf(status->message, sizeof(status->message), "Index not loaded or invalid (stub mode)");
        result->statusCode = 503;  // Service Unavailable
        return result;
    }

    if (!sparql_query || sparql_query[0] == '\0') {
        status->code = QLEVER_STATUS_INVALID_ARGUMENT;
        snprintf(status->message, sizeof(status->message), "SPARQL query cannot be empty");
        result->statusCode = 400;  // Bad Request
        return result;
    }

    // Return error indicating QLever is not available
    status->code = QLEVER_STATUS_ERROR;
    snprintf(status->message, sizeof(status->message),
             "QLever not available (stub mode). Build with QLever for query execution.");
    result->statusCode = 503;  // Service Unavailable
#else
    // Validate inputs
    if (!index || !index->index || !index->loaded) {
        status->code = QLEVER_STATUS_INDEX_NOT_LOADED;
        snprintf(status->message, sizeof(status->message), "Index not loaded or invalid");
        return result;
    }

    if (!sparql_query || sparql_query[0] == '\0') {
        status->code = QLEVER_STATUS_INVALID_ARGUMENT;
        snprintf(status->message, sizeof(status->message), "SPARQL query cannot be empty");
        return result;
    }

    // Thread safety: Note that QLever's QueryExecutionContext is not thread-safe
    // for concurrent query execution. Each query should have its own context.
    try {
        // Parse the SPARQL query
        SparqlParser parser(sparql_query);
        auto parsedQuery = parser.parse();

        // Create execution context with the loaded index
        QueryExecutionContext ctx(index->index.get());

        // Create query planner and execution tree
        QueryPlanner planner(&ctx);
        auto executionTree = planner.createExecutionTree(parsedQuery);

        // Execute the query
        auto resultTable = executionTree.getResult();

        // Determine output format
        std::string accept = media_type_to_string(media_type);

        // Serialize result based on query type and media type
        std::stringstream ss;

        switch (media_type) {
            case QLEVER_MEDIA_TSV:
                // SELECT query result as TSV
                resultTable->writeTsv(ss, parsedQuery);
                break;
            case QLEVER_MEDIA_CSV:
                // SELECT query result as CSV
                resultTable->writeCsv(ss, parsedQuery);
                break;
            case QLEVER_MEDIA_TURTLE:
                // CONSTRUCT query result as Turtle
                resultTable->writeTurtle(ss, parsedQuery);
                break;
            case QLEVER_MEDIA_XML:
                // SELECT query result as XML
                resultTable->writeXml(ss, parsedQuery);
                break;
            case QLEVER_MEDIA_JSON:
            default:
                // SELECT/ASK query result as JSON
                resultTable->writeJson(ss, parsedQuery);
                break;
        }

        result->resultString = ss.str();
        result->statusCode = 200;  // OK

        // Split result into lines for streaming iteration
        std::istringstream lineStream(result->resultString);
        std::string line;
        while (std::getline(lineStream, line)) {
            result->lines.push_back(line);
        }

        // Set row count for SELECT queries
        if (parsedQuery->getClauseType() == Query::SELECT) {
            result->rowCount = resultTable->getResultWidth();
        } else {
            result->rowCount = result->lines.size();
        }

    } catch (const ParseException& e) {
        status->code = QLEVER_STATUS_PARSE_ERROR;
        snprintf(status->message, sizeof(status->message), "SPARQL parse error: %s", e.what());
        result->statusCode = 400;  // Bad Request
    } catch (const std::exception& e) {
        translate_exception(e, status);
        result->statusCode = 500;  // Internal Server Error
    } catch (...) {
        translate_unknown_exception(status);
        result->statusCode = 500;
    }
#endif

    return result;
}

/* ============================================================================
 * Result Iteration Implementation
 * ============================================================================ */

int qlever_result_has_next(const QLeverResult* result) {
    return result && result->currentLine < result->lines.size() ? 1 : 0;
}

const char* qlever_result_next(QLeverResult* result, QleverStatus* status) {
    // Initialize status if not provided
    bool status_ownership = false;
    if (!status) {
        status = qlever_create_status();
        status_ownership = true;
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    // Thread safety: This function is not thread-safe for concurrent access
    // to the same result. Each thread should have its own result or access
    // results sequentially.
    if (!result || result->currentLine >= result->lines.size()) {
        status->code = QLEVER_STATUS_ERROR;
        snprintf(status->message, sizeof(status->message), "No more lines in result");
        return nullptr;
    }

    return result->lines[result->currentLine++].c_str();
}

/* ============================================================================
 * Error Handling Implementation
 * ============================================================================ */

int qlever_result_has_error(const QLeverResult* result) {
    return result && result->statusCode != 200;
}

const char* qlever_result_error(const QLeverResult* result) {
    if (!result) {
        return NULL;
    }
    return result->statusCode == 200 ? NULL : "Query execution failed";
}

int qlever_result_status(const QLeverResult* result, QleverStatus* status) {
    if (!status) {
        status = qlever_create_status();
        status->code = QLEVER_STATUS_SUCCESS;
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    return result ? result->statusCode : 0;
}

/* ============================================================================
 * Index Building Implementation (Optional)
 * ============================================================================ */

int qlever_index_build(
    const char* input_path,
    const char* output_path,
    const char* settings,
    QleverStatus* status
) {
    if (!status) {
        status = qlever_create_status();
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    // Note: This is simplified. In practice, you'd need to call the
    // QLever IndexBuilderMain class or similar functionality
    if (!input_path || !output_path) {
        status->code = QLEVER_STATUS_INVALID_ARGUMENT;
        snprintf(status->message, sizeof(status->message), "Input and output paths cannot be null");
        return QLEVER_STATUS_INVALID_ARGUMENT;
    }

    try {
        // TODO: Implement actual index building using QLever's IndexBuilder
        // For now, return error indicating not implemented via C API
        status->code = QLEVER_STATUS_ERROR;
        snprintf(status->message, sizeof(status->message), "Index building not implemented via C API. Use QLever CLI IndexBuilderMain tool.");
        return QLEVER_STATUS_ERROR;
    } catch (const std::exception& e) {
        translate_exception(e, status);
        return QLEVER_STATUS_ERROR;
    } catch (...) {
        translate_unknown_exception(status);
        return QLEVER_STATUS_ERROR;
    }
}

/* ============================================================================
 * Memory Management Functions
 * ============================================================================ */

static char* qlever_create_string_copy(const char* str) {
    if (!str) {
        return nullptr;
    }
    size_t len = strlen(str);
    char* copy = new char[len + 1];
    memcpy(copy, str, len + 1);
    return copy;
}

void qlever_free_string(char* str) {
    delete[] str;
}

void qlever_result_free(QLeverResult* result) {
    delete result;
}

void qlever_index_free(QLeverIndex* index) {
    delete index;
}

char* qlever_media_type_to_mime(QleverMediaType media_type, QleverStatus* status) {
    // Initialize status if not provided
    bool status_ownership = false;
    if (!status) {
        status = qlever_create_status();
        status_ownership = true;
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    const char* mime = media_type_to_string(media_type);
    char* result = qlever_create_string_copy(mime);

    if (!result) {
        status->code = QLEVER_STATUS_BAD_ALLOC;
        snprintf(status->message, sizeof(status->message), "Memory allocation failed");
        if (status_ownership) {
            delete status;
            status = nullptr;
        }
    }

    return result;
}

int32_t qlever_mime_to_media_type(const char* mime_type, QleverStatus* status) {
    // Initialize status if not provided
    bool status_ownership = false;
    if (!status) {
        status = qlever_create_status();
        status_ownership = true;
    } else {
        status->code = QLEVER_STATUS_SUCCESS;
        status->message[0] = '\0';
    }

    if (!mime_type) {
        status->code = QLEVER_STATUS_INVALID_ARGUMENT;
        snprintf(status->message, sizeof(status->message), "MIME type cannot be null");
        if (status_ownership) {
            delete status;
            status = nullptr;
        }
        return -1;
    }

    std::string mime = mime_type;

    if (mime == "application/sparql-results+json") {
        return QLEVER_MEDIA_JSON;
    } else if (mime == "text/tab-separated-values") {
        return QLEVER_MEDIA_TSV;
    } else if (mime == "text/csv") {
        return QLEVER_MEDIA_CSV;
    } else if (mime == "text/turtle") {
        return QLEVER_MEDIA_TURTLE;
    } else if (mime == "application/sparql-results+xml") {
        return QLEVER_MEDIA_XML;
    } else {
        status->code = QLEVER_STATUS_ERROR;
        snprintf(status->message, sizeof(status->message), "Unsupported MIME type: %s", mime_type);
        if (status_ownership) {
            delete status;
            status = nullptr;
        }
        return -1;
    }
}

}  // extern "C"
