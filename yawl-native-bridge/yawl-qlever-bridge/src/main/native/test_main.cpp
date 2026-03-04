/**
 * Test implementation for QLever FFI interface
 * This demonstrates the usage of the FFI functions
 */

#include "qlever_ffi.h"
#include <iostream>
#include <string>
#include <vector>
#include <cassert>

// Simple test for basic functionality
void test_basic_functionality() {
    std::cout << "=== Basic Functionality Test ===" << std::endl;

    // Test version function
    const char* version = qlever_get_version();
    std::cout << "QLever Version: " << version << std::endl;

    // Test status string conversion
    const char* status_str = qlever_status_string(QLEVER_STATUS_OK);
    std::cout << "Status OK: " << status_str << std::endl;

    status_str = qlever_status_string(QLEVER_STATUS_ERROR_PARSE);
    std::cout << "Status Parse Error: " << status_str << std::endl;

    std::cout << "Basic functionality test passed!" << std::endl << std::endl;
}

// Test engine lifecycle
void test_engine_lifecycle(const char* index_path) {
    std::cout << "=== Engine Lifecycle Test ===" << std::endl;

    void* engine = nullptr;

    // Create engine
    QleverStatus status = qlever_engine_create(index_path, &engine);
    if (status.code != QLEVER_STATUS_OK) {
        std::cout << "Engine creation failed: " << status.message << std::endl;
        qlever_status_free_message(&status);
        return;
    }
    std::cout << "Engine created successfully" << std::endl;

    // Get engine info
    const char* engine_info;
    size_t info_len = 0;
    status = qlever_get_engine_info(&engine_info, &info_len);
    if (status.code == QLEVER_STATUS_OK) {
        std::cout << "Engine Info (" << info_len << " bytes): " << engine_info << std::endl;
        free((void*)engine_info);
    }

    // Destroy engine
    status = qlever_engine_destroy(engine);
    if (status.code != QLEVER_STATUS_OK) {
        std::cout << "Engine destruction failed: " << status.message << std::endl;
        qlever_status_free_message(&status);
        return;
    }
    std::cout << "Engine destroyed successfully" << std::endl;

    std::cout << "Engine lifecycle test passed!" << std::endl << std::endl;
}

// Test query validation
void test_query_validation() {
    std::cout << "=== Query Validation Test ===" << std::endl;

    std::vector<std::string> test_queries = {
        "SELECT ?s ?p WHERE { ?s ?p ?o }",
        "ASK WHERE { ?s ?p ?o }",
        "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }",
        "INVALID SPARQL QUERY",
        ""
    };

    for (const auto& query : test_queries) {
        int is_valid;
        QleverStatus status = qlever_validate_query(query.c_str(), &is_valid);

        std::cout << "Query: \"" << query << "\"" << std::endl;
        std::cout << "  Valid: " << (is_valid ? "YES" : "NO") << std::endl;
        std::cout << "  Status: " << qlever_status_string(status.code) << std::endl;

        if (status.message) {
            std::cout << "  Message: " << status.message << std::endl;
            qlever_status_free_message(&status);
        }
        std::cout << std::endl;
    }

    std::cout << "Query validation test completed!" << std::endl << std::endl;
}

// Test memory management
void test_memory_management() {
    std::cout << "=== Memory Management Test ===" << std::endl;

    void* engine = nullptr;
    QleverStatus status = qlever_engine_create("/tmp/test", &engine);

    if (status.code != QLEVER_STATUS_OK) {
        std::cout << "Engine creation failed" << std::endl;
        return;
    }

    // Test query execution and result handling
    const char* query = "SELECT ?s WHERE { ?s ?p ?o }";
    void* result = nullptr;

    status = qlever_engine_query(engine, query, &result);
    if (status.code != QLEVER_STATUS_OK) {
        std::cout << "Query execution failed: " << status.message << std::endl;
        qlever_status_free_message(&status);
        qlever_engine_destroy(engine);
        return;
    }

    // Get result data
    const char* json_data;
    size_t json_len = 0;
    status = qlever_result_get_data(result, &json_data, &json_len);

    if (status.code == QLEVER_STATUS_OK) {
        std::cout << "Query result (" << json_len << " bytes): " << json_data << std::endl;
        free((void*)json_data);
    } else {
        std::cout << "Failed to get result data: " << status.message << std::endl;
    }

    // Cleanup
    qlever_result_free(result);
    qlever_engine_destroy(engine);

    std::cout << "Memory management test completed!" << std::endl << std::endl;
}

// Error handling test
void test_error_handling() {
    std::cout << "=== Error Handling Test ===" << std::endl;

    // Test with invalid engine handle
    QleverStatus status = qlever_engine_destroy(nullptr);
    std::cout << "Destroy null engine: " << qlever_status_string(status.code) << std::endl;
    if (status.message) {
        std::cout << "Message: " << status.message << std::endl;
        qlever_status_free_message(&status);
    }

    // Test with invalid result handle
    status = qlever_result_free(nullptr);
    std::cout << "Free null result: " << qlever_status_string(status.code) << std::endl;
    if (status.message) {
        std::cout << "Message: " << status.message << std::endl;
        qlever_status_free_message(&status);
    }

    // Test with empty query
    int is_valid;
    status = qlever_validate_query("", &is_valid);
    std::cout << "Validate empty query: " << (is_valid ? "Valid" : "Invalid") << std::endl;

    std::cout << "Error handling test completed!" << std::endl << std::endl;
}

// Stress test
void stress_test(const char* index_path, int iterations) {
    std::cout << "=== Stress Test (" << iterations << " iterations) ===" << std::endl;

    void* engine = nullptr;
    QleverStatus status = qlever_engine_create(index_path, &engine);

    if (status.code != QLEVER_STATUS_OK) {
        std::cout << "Engine creation failed for stress test" << std::endl;
        return;
    }

    const char* query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";

    for (int i = 0; i < iterations; i++) {
        void* result = nullptr;
        status = qlever_engine_query(engine, query, &result);

        if (status.code != QLEVER_STATUS_OK) {
            std::cout << "Query " << i << " failed: " << status.message << std::endl;
            qlever_status_free_message(&status);
            continue;
        }

        const char* json_data;
        size_t json_len = 0;
        status = qlever_result_get_data(result, &json_data, &json_len);

        if (status.code == QLEVER_STATUS_OK) {
            // Process result (just for demonstration)
            if (i % 100 == 0) {
                std::cout << "Query " << i << ": " << json_len << " bytes" << std::endl;
            }
            free((void*)json_data);
        }

        qlever_result_free(result);
    }

    qlever_engine_destroy(engine);
    std::cout << "Stress test completed!" << std::endl << std::endl;
}

int main(int argc, char** argv) {
    std::cout << "QLever F Interface Test Suite" << std::endl;
    std::cout << "===============================" << std::endl << std::endl;

    // Get index path from command line or use default
    const char* index_path = argc > 1 ? argv[1] : "/tmp/test";

    // Run tests
    test_basic_functionality();
    test_engine_lifecycle(index_path);
    test_query_validation();
    test_memory_management();
    test_error_handling();

    // Optional stress test
    if (argc > 2) {
        int iterations = std::stoi(argv[2]);
        stress_test(index_path, iterations);
    }

    std::cout << "All tests completed!" << std::endl;
    return 0;
}