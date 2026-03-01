// QLever FFI Engine Test
// Tests engine lifecycle and basic operations

#include "qlever_ffi.h"
#include <cassert>
#include <iostream>
#include <memory>
#include <vector>

// Mock data for testing
const std::vector<std::string> test_documents = {
    "The quick brown fox jumps over the lazy dog",
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit",
    "Machine learning algorithms process large datasets efficiently",
    "Natural language understanding requires deep neural networks"
};

bool test_engine_creation() {
    std::cout << "Testing engine creation..." << std::endl;

    void* engine = qlever_ffi_create_engine();
    if (!engine) {
        std::cerr << "Engine creation failed: " << qlever_ffi_get_last_error() << std::endl;
        return false;
    }

    // Test that we can create multiple engines (stress test)
    for (int i = 0; i < 5; ++i) {
        void* test_engine = qlever_ffi_create_engine();
        if (!test_engine) {
            std::cerr << "Multiple engine creation failed at iteration " << i << std::endl;
            qlever_ffi_destroy_engine(engine);
            return false;
        }
        qlever_ffi_destroy_engine(test_engine);
    }

    qlever_ffi_destroy_engine(engine);
    return true;
}

bool test_data_operations() {
    std::cout << "Testing data operations..." << std::endl;

    void* engine = qlever_ffi_create_engine();
    if (!engine) {
        return false;
    }

    // Add all test documents
    for (size_t i = 0; i < test_documents.size(); ++i) {
        const std::string& doc = test_documents[i];
        int result = qlever_ffi_add_data(engine, doc.c_str(), doc.length());

        if (result != 0) {
            std::cerr << "Failed to add document " << i << ": "
                      << qlever_ffi_get_last_error() << std::endl;
            qlever_ffi_destroy_engine(engine);
            return false;
        }
    }

    qlever_ffi_destroy_engine(engine);
    return true;
}

bool test_search_operations() {
    std::cout << "Testing search operations..." << std::endl;

    void* engine = qlever_ffi_create_engine();
    if (!engine) {
        return false;
    }

    // Add test documents
    for (const auto& doc : test_documents) {
        qlever_ffi_add_data(engine, doc.c_str(), doc.length());
    }

    // Test various search queries
    const std::vector<std::string> search_queries = {
        "machine",
        "learning",
        "neural",
        "quick",
        "fox",
        "Lorem",
        "nonexistent"  // Should return empty result
    };

    for (const auto& query : search_queries) {
        void* result = qlever_ffi_search(engine, query.c_str());

        if (!result) {
            // Search might legitimately return null for no results
            std::cout << "Search for '" << query << "' returned no results" << std::endl;
            continue;
        }

        // Free the result
        qlever_ffi_free_result(result);
    }

    qlever_ffi_destroy_engine(engine);
    return true;
}

int main() {
    std::cout << "=== QLever FFI Engine Test Suite ===" << std::endl;

    std::vector<std::function<bool()>> tests = {
        test_engine_creation,
        test_data_operations,
        test_search_operations
    };

    int passed = 0;
    int total = tests.size();

    for (auto& test : tests) {
        if (test()) {
            std::cout << "✓ PASSED" << std::endl;
            passed++;
        } else {
            std::cout << "✗ FAILED" << std::endl;
        }
        std::cout << std::endl;
    }

    std::cout << "=== Results: " << passed << "/" << total << " tests passed ===" << std::endl;

    return (passed == total) ? 0 : 1;
}