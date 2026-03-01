/**
 * @file mock_engine.h
 * @brief Mock engine interface for unit testing
 */

#pragma once

#include <string>
#include <vector>

namespace qlever {
namespace test {

/**
 * @brief Search result structure for mock engine
 */
struct SearchResult {
    int id;         // Result ID
    size_t count;   // Number of results
    char* data;     // Result data (must be freed)
    size_t size;    // Size of result data
};

/**
 * @brief Mock engine implementation for testing
 *
 * This class provides a mock implementation of the QLever FFI
 * engine that doesn't require the actual QLever library.
 */
class MockEngine {
private:
    std::vector<std::string> data_storage_;
    std::vector<SearchResult*> results_;
    int next_id_;

public:
    MockEngine();
    ~MockEngine();

    /**
     * @brief Add test data to the engine
     * @param data The data to add
     * @return 0 on success, -1 on failure
     */
    int add_data(const std::string& data);

    /**
     * @brief Search for data
     * @param query The search query
     * @return Search result or nullptr if no results
     */
    SearchResult* search(const std::string& query);

    /**
     * @brief Free search result memory
     * @param result The result to free
     */
    void free_result(SearchResult* result);

    /**
     * @brief Clear all test data
     */
    void clear_data();

    /**
     * @brief Get the number of documents stored
     * @return Number of documents
     */
    size_t get_data_count() const;
};

} // namespace test
} // namespace qlever