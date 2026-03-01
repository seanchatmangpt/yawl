/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * QLever FFI Implementation Test Suite
 *
 * Comprehensive test suite for the Lippincott pattern implementation.
 * Tests error handling, memory management, and API functionality.
 */

#include "qlever_ffi.h"
#include <cassert>
#include <cstdio>
#include <cstring>

// Test helper macros
#define TEST_START(test_name) printf("=== Testing %s ===\n", test_name)
#define TEST_END() printf("=== Test completed ===\n\n")
#define ASSERT(condition, message) \
    do { \
        if (!(condition)) { \
            printf("FAIL: %s at %s:%d\n", message, __FILE__, __LINE__); \
            exit(1); \
        } \
    } while (0)

/* ============================================================================
 * Test Utilities
 * ============================================================================ */

static void print_status(QLeverStatus* status) {
    if (status) {
        printf("  Status: %d\n", status->code);
        if (status->code != QLEVER_STATUS_SUCCESS) {
            printf("  Message: %s\n", status->message);
        }
    } else {
        printf("  Status: NULL\n");
    }
}

static void test_status_creation_and_destruction() {
    TEST_START("Status creation and destruction");

    // Test status creation
    QLeverStatus* status = qlever_create_status();
    ASSERT(status != NULL, "Status creation failed");

    // Check initial state
    ASSERT(status->code == QLEVER_STATUS_SUCCESS, "Initial status code should be 0");
    ASSERT(status->message[0] == '\0', "Initial message should be empty");

    // Set a test error
    status->code = QLEVER_STATUS_INVALID_ARGUMENT;
    snprintf(status->message, sizeof(status->message), "Test error message");

    // Test status destruction
    qlever_free_status(status);

    TEST_END();
}

/* ============================================================================
 * Test Index Operations
 * ============================================================================ */

static void test_index_creation_and_destruction() {
    TEST_START("Index creation and destruction");

    QLeverStatus* status = qlever_create_status();

    // Test with NULL path (should fail gracefully)
    QLeverIndex* index = qlever_index_create(NULL, status);
    ASSERT(index == NULL, "Should not create index with NULL path");
    ASSERT(status->code == QLEVER_STATUS_INVALID_ARGUMENT, "Should report invalid argument");

    printf("  NULL path test passed\n");

    // Note: Real index creation would require actual QLever index files
    // We just test the API behavior here

    qlever_free_status(status);
    TEST_END();
}

static void test_index_functions() {
    TEST_START("Index utility functions");

    // Test with NULL index
    QLeverStatus* status = qlever_create_status();

    bool loaded = qlever_index_is_loaded(NULL);
    ASSERT(!loaded, "NULL index should not be loaded");

    size_t triple_count = qlever_index_triple_count(NULL, status);
    ASSERT(triple_count == 0, "NULL index should return 0 triples");
    ASSERT(status->code == QLEVER_STATUS_INDEX_NOT_LOADED, "Should report index not loaded");

    qlever_free_status(status);
    TEST_END();
}

/* ============================================================================
 * Test Query Execution
 * ============================================================================ */

static void test_query_execution_api() {
    TEST_START("Query execution API");

    QLeverStatus* status = qlever_create_status();
    QLeverIndex* index = NULL; // Would be real index in production

    // Test with NULL index and NULL status
    QLeverResult* result = qlever_query_exec(NULL, "SELECT * WHERE {?s ?p ?o}", QLEVER_MEDIA_JSON, NULL);
    ASSERT(result != NULL, "Should return result even with NULL status");

    // Clean up
    qlever_result_destroy(result);

    // Test with NULL index but with status
    result = qlever_query_exec(NULL, "SELECT * WHERE {?s ?p ?o}", QLEVER_MEDIA_JSON, status);
    ASSERT(result != NULL, "Should return result with NULL index");
    ASSERT(status->code == QLEVER_STATUS_INDEX_NOT_LOADED, "Should report index not loaded");

    // Test with empty query
    result = qlever_query_exec(index, "", QLEVER_MEDIA_JSON, status);
    ASSERT(result != NULL, "Should return result with empty query");
    ASSERT(status->code == QLEVER_STATUS_INVALID_ARGUMENT, "Should report invalid argument");

    // Clean up
    qlever_result_destroy(result);
    qlever_free_status(status);

    TEST_END();
}

/* ============================================================================
 * Test Result Iteration
 * ============================================================================ */

static void test_result_iteration() {
    TEST_START("Result iteration");

    // Create a mock result for testing
    QLeverResult* result = new QLeverResult();
    result->lines = {"line1", "line2", "line3"};
    result->rowCount = 3;
    result->statusCode = 200;

    QLeverStatus* status = qlever_create_status();

    // Test has_next with NULL result
    bool has_next = qlever_result_has_next(NULL);
    ASSERT(!has_next, "NULL result should have no next lines");

    // Test iteration
    for (int i = 0; i < 3; i++) {
        has_next = qlever_result_has_next(result);
        ASSERT(has_next, "Should have next line");

        const char* line = qlever_result_next(result, status);
        ASSERT(line != NULL, "Should return line");
        ASSERT(strcmp(line, ("line" + std::to_string(i + 1)).c_str()) == 0, "Line content mismatch");
        ASSERT(status->code == QLEVER_STATUS_SUCCESS, "Status should be success");
    }

    // Test beyond last line
    has_next = qlever_result_has_next(result);
    ASSERT(!has_next, "Should have no more lines after iteration");

    const char* line = qlever_result_next(result, status);
    ASSERT(line == NULL, "Should return NULL beyond last line");
    ASSERT(status->code == QLEVER_STATUS_ERROR, "Should report error");

    // Clean up
    qlever_result_destroy(result);
    qlever_free_status(status);

    TEST_END();
}

/* ============================================================================
 * Test Error Handling
 * ============================================================================ */

static void test_error_handling() {
    TEST_START("Error handling");

    // Create a result with error
    QLeverResult* result = new QLeverResult();
    result->statusCode = 500; // Server error

    // Test error checking
    int has_error = qlever_result_has_error(result);
    ASSERT(has_error, "Should detect error in result");

    const char* error_msg = qlever_result_error(result);
    ASSERT(error_msg != NULL, "Should return error message");
    ASSERT(strcmp(error_msg, "Query execution failed") == 0, "Error message mismatch");

    QLeverStatus* status = qlever_create_status();
    int status_code = qlever_result_status(result, status);
    ASSERT(status_code == 500, "Should return status code");
    ASSERT(status->code == QLEVER_STATUS_SUCCESS, "Status should be OK for reading status code");

    // Test with successful result
    result->statusCode = 200;
    has_error = qlever_result_has_error(result);
    ASSERT(!has_error, "Should not detect error in successful result");

    error_msg = qlever_result_error(result);
    ASSERT(error_msg == NULL, "Should return NULL for no error");

    // Clean up
    qlever_result_destroy(result);
    qlever_free_status(status);

    TEST_END();
}

/* ============================================================================
 * Test Media Type Utilities
 * ============================================================================ */

static void test_media_type_utilities() {
    TEST_START("Media type utilities");

    QLeverStatus* status = qlever_create_status();

    // Test media type to MIME
    char* mime = qlever_media_type_to_mime(QLEVER_MEDIA_JSON, status);
    ASSERT(mime != NULL, "Should return MIME string");
    ASSERT(strcmp(mime, "application/sparql-results+json") == 0, "MIME string mismatch");
    qlever_free_string(mime);

    // Test MIME to media type
    int32_t media_type = qlever_mime_to_media_type("text/csv", status);
    ASSERT(media_type == QLEVER_MEDIA_CSV, "Should return correct media type");

    // Test invalid MIME type
    media_type = qlever_mime_to_media_type("invalid/type", status);
    ASSERT(media_type == -1, "Should return -1 for invalid type");
    ASSERT(status->code == QLEVER_STATUS_ERROR, "Should report error");

    // Test with NULL arguments
    mime = qlever_media_type_to_mime(QLEVER_MEDIA_JSON, NULL);
    ASSERT(mime != NULL, "Should work with NULL status");
    qlever_free_string(mime);

    media_type = qlever_mime_to_media_type(NULL, status);
    ASSERT(media_type == -1, "Should handle NULL MIME type");
    ASSERT(status->code == QLEVER_STATUS_INVALID_ARGUMENT, "Should report invalid argument");

    qlever_free_status(status);
    TEST_END();
}

/* ============================================================================
 * Test Memory Management
 * ============================================================================ */

static void test_memory_management() {
    TEST_START("Memory management");

    // Test status memory management
    QLeverStatus* status = qlever_create_status();
    status->code = QLEVER_STATUS_ERROR;
    snprintf(status->message, sizeof(status->message), "Test");
    qlever_free_status(status);

    // Test string memory management
    char* test_string = qlever_create_string_copy("test string");
    ASSERT(strcmp(test_string, "test string") == 0, "String copy should match");
    qlever_free_string(test_string);

    // Test with NULL string
    test_string = qlever_create_string_copy(NULL);
    ASSERT(test_string == NULL, "Should handle NULL string");

    TEST_END();
}

/* ============================================================================
 * Test Media Type Conversions
 * ============================================================================ */

static void test_media_type_conversions() {
    TEST_START("Media type conversions");

    struct MediaTypeTest {
        QleverMediaType enum_val;
        const char* expected_mime;
    };

    MediaTypeTest tests[] = {
        {QLEVER_MEDIA_JSON, "application/sparql-results+json"},
        {QLEVER_MEDIA_TSV, "text/tab-separated-values"},
        {QLEVER_MEDIA_CSV, "text/csv"},
        {QLEVER_MEDIA_TURTLE, "text/turtle"},
        {QLEVER_MEDIA_XML, "application/sparql-results+xml"}
    };

    QLeverStatus* status = qlever_create_status();

    for (const auto& test : tests) {
        char* mime = qlever_media_type_to_mime(test.enum_val, status);
        ASSERT(mime != NULL, "Should convert media type to MIME");
        ASSERT(strcmp(mime, test.expected_mime) == 0, "MIME string mismatch");
        qlever_free_string(mime);
    }

    // Test reverse conversions
    const char* mime_types[] = {
        "application/sparql-results+json",
        "text/tab-separated-values",
        "text/csv",
        "text/turtle",
        "application/sparql-results+xml"
    };

    for (const char* mime : mime_types) {
        int32_t media_type = qlever_mime_to_media_type(mime, status);
        ASSERT(media_type >= 0, "Should convert MIME to media type");
        // Verify it maps back to the same value
        char* converted_mime = qlever_media_type_to_mime((QLeverMediaType)media_type, status);
        ASSERT(strcmp(converted_mime, mime) == 0, "Round-trip conversion failed");
        qlever_free_string(converted_mime);
    }

    qlever_free_status(status);
    TEST_END();
}

/* ============================================================================
 * Main Test Runner
 * ============================================================================ */

int main() {
    printf("Starting QLever FFI Test Suite\n");
    printf("==============================\n\n");

    // Run all tests
    test_status_creation_and_destruction();
    test_index_creation_and_destruction();
    test_index_functions();
    test_query_execution_api();
    test_result_iteration();
    test_error_handling();
    test_media_type_utilities();
    test_memory_management();
    test_media_type_conversions();

    printf("====================================\n");
    printf("All tests passed successfully!\n");
    printf("====================================\n");

    return 0;
}