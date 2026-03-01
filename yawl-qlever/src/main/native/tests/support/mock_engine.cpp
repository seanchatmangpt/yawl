/**
 * @file mock_engine.cpp
 * @brief Mock engine implementation for unit testing
 */

#include "mock_engine.h"
#include <iostream>
#include <vector>
#include <string>
#include <algorithm>

namespace qlever {
namespace test {

MockEngine::MockEngine() : next_id_(1) {
    // Initialize with some test data
    test_data_ = {
        "Test document 1: This is a test document for unit testing",
        "Test document 2: Another test document for verification",
        "Test document 3: Final test document for validation"
    };
}

MockEngine::~MockEngine() {
    // Clean up all allocated resources
    for (auto* result : results_) {
        delete[] result->data;
        delete result;
    }
    results_.clear();
}

int MockEngine::add_data(const std::string& data) {
    if (data.empty()) {
        return -1; // Invalid data
    }

    // Store the data
    data_storage_.push_back(data);
    return 0; // Success
}

SearchResult* MockEngine::search(const std::string& query) {
    if (query.empty()) {
        return nullptr;
    }

    // Find matching documents
    std::vector<size_t> matches;
    std::transform(data_storage_.begin(), data_storage_.end(),
                   std::back_inserter(matches),
                   [this, &query](const std::string& doc) -> size_t {
                       return doc.find(query) != std::string::npos ? &doc - &data_storage_[0] :
                             static_cast<size_t>(-1);
                   });

    // Remove invalid matches (those with -1)
    matches.erase(std::remove(matches.begin(), matches.end(), static_cast<size_t>(-1)), matches.end());

    if (matches.empty()) {
        return nullptr; // No results
    }

    // Create result
    SearchResult* result = new SearchResult();
    result->id = next_id_++;
    result->count = matches.size();
    result->data = new char[1024]; // Fixed size for simplicity
    result->size = 1024;

    // Format results as JSON
    std::string json_response = "{\"results\":[";
    for (size_t i = 0; i < result->count; ++i) {
        if (i > 0) json_response += ",";
        json_response += "{\"id\":" + std::to_string(matches[i]) +
                         ",\"content\":\"" + data_storage_[matches[i]] + "\"}";
    }
    json_response += "]}";

    // Copy response to result buffer
    strncpy(result->data, json_response.c_str(), result->size - 1);
    result->data[result->size - 1] = '\0';

    // Store result for cleanup
    results_.push_back(result);

    return result;
}

void MockEngine::free_result(SearchResult* result) {
    if (!result) return;

    // Find and remove from results vector
    auto it = std::find(results_.begin(), results_.end(), result);
    if (it != results_.end()) {
        results_.erase(it);
    }

    // Free memory
    delete[] result->data;
    delete result;
}

void MockEngine::clear_data() {
    data_storage_.clear();
}

size_t MockEngine::get_data_count() const {
    return data_storage_.size();
}

} // namespace test
} // namespace qlever