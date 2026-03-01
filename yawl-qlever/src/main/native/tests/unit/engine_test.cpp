/**
 * @file engine_test.cpp
 * @brief Unit tests for QLever FFI engine functionality
 */

#include <gtest/gtest.h>
#include "qlever_ffi.h"
#include "../support/mock_engine.h"
#include "../support/test_environment.h"
#include <memory>

// Test fixture for engine tests
class EngineTest : public ::testing::Test {
protected:
    void SetUp() override {
        // Initialize test environment
        qlever::test::get_test_environment().initialize();

        // Create mock engine
        mock_engine_ = std::make_unique<qlever::test::MockEngine>();
    }

    void TearDown() override {
        // Clean up
        mock_engine_.reset();
        qlever::test::reset_test_environment();
    }

    std::unique_ptr<qlever::test::MockEngine> mock_engine_;
};

// Test basic engine creation and destruction
TEST_F(EngineTest, CreateAndDestroy) {
    void* engine = qlever_ffi_create_engine();
    ASSERT_NE(engine, nullptr);

    // Should not crash when destroying null engine
    qlever_ffi_destroy_engine(nullptr);

    // Destroy valid engine
    qlever_ffi_destroy_engine(engine);
}

// Test adding data to engine
TEST_F(EngineTest, AddData) {
    // Get test documents
    auto& env = qlever::test::get_test_environment();
    const auto& docs = env.get_test_documents();

    // Add first few documents
    for (size_t i = 0; i < std::min(docs.size(), size_t(3)); ++i) {
        void* engine = qlever_ffi_create_engine();
        int result = qlever_ffi_add_data(engine, docs[i].c_str(), docs[i].length());

        EXPECT_EQ(result, 0) << "Failed to add document " << i;

        qlever_ffi_destroy_engine(engine);
    }
}

// Test search functionality
TEST_F(EngineTest, Search) {
    void* engine = qlever_ffi_create_engine();

    // Add test data
    const std::string test_doc = "This is a test document for search functionality";
    qlever_ffi_add_data(engine, test_doc.c_str(), test_doc.length());

    // Search for exact match
    void* result = qlever_ffi_search(engine, "test");
    ASSERT_NE(result, nullptr);

    // Clean up
    qlever_ffi_free_result(result);
    qlever_ffi_destroy_engine(engine);
}

// Test search with no results
TEST_F(EngineTest, SearchNoResults) {
    void* engine = qlever_ffi_create_engine();

    // Search for non-existent term
    void* result = qlever_ffi_search(engine, "nonexistent_term");
    EXPECT_EQ(result, nullptr);

    qlever_ffi_destroy_engine(engine);
}

// Test error handling
TEST_F(EngineTest, ErrorHandling) {
    // Test with null parameters
    void* engine = qlever_ffi_create_engine();

    // Add empty data should fail
    int result = qlever_ffi_add_data(engine, "", 0);
    EXPECT_NE(result, 0);

    // Search with null query should fail
    void* search_result = qlever_ffi_search(engine, nullptr);
    EXPECT_EQ(search_result, nullptr);

    qlever_ffi_destroy_engine(engine);
}

// Test multiple engines
TEST_F(EngineTest, MultipleEngines) {
    const int num_engines = 5;
    void* engines[num_engines];

    // Create multiple engines
    for (int i = 0; i < num_engines; ++i) {
        engines[i] = qlever_ffi_create_engine();
        ASSERT_NE(engines[i], nullptr);

        // Add some data to each
        std::string data = "Engine " + std::to_string(i) + " data";
        qlever_ffi_add_data(engines[i], data.c_str(), data.length());
    }

    // Search in each engine
    for (int i = 0; i < num_engines; ++i) {
        void* result = qlever_ffi_search(engines[i], "Engine");
        ASSERT_NE(result, nullptr);
        qlever_ffi_free_result(result);
    }

    // Clean up
    for (int i = 0; i < num_engines; ++i) {
        qlever_ffi_destroy_engine(engines[i]);
    }
}

// Stress test with large data
TEST_F(EngineTest, LargeData) {
    void* engine = qlever_ffi_create_engine();

    // Add large document
    std::string large_doc(10000, 'A'); // 10KB document
    qlever_ffi_add_data(engine, large_doc.c_str(), large_doc.length());

    // Search should work
    void* result = qlever_ffi_search(engine, "AAAA");
    ASSERT_NE(result, nullptr);
    qlever_ffi_free_result(result);

    qlever_ffi_destroy_engine(engine);
}