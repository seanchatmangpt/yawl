/**
 * @file test_environment.h
 * @brief Test environment interface for QLever FFI tests
 */

#pragma once

#include <string>
#include <vector>

namespace qlever {
namespace test {

/**
 * @brief Test environment interface
 *
 * Provides shared test data and utilities for test suites.
 */
class TestEnvironment {
public:
    virtual ~TestEnvironment() = default;

    /**
     * @brief Initialize the test environment
     */
    virtual void initialize() = 0;

    /**
     * @brief Get the test documents
     * @return Reference to the vector of test documents
     */
    virtual const std::vector<std::string>& get_test_documents() const = 0;

    /**
     * @brief Get the number of test documents
     * @return Number of test documents
     */
    virtual size_t get_document_count() const = 0;

    /**
     * @brief Get a test document by index
     * @param index Index of the document
     * @return Reference to the document string
     * @throws std::out_of_range if index is invalid
     */
    virtual const std::string& get_test_document(size_t index) const = 0;

    /**
     * @brief Add a test document
     * @param doc The document to add
     */
    virtual void add_test_document(const std::string& doc) = 0;

    /**
     * @brief Check if environment is initialized
     * @return True if initialized, false otherwise
     */
    virtual bool is_initialized() const = 0;
};

/**
 * @brief Get the global test environment
 * @return Reference to the test environment
 */
TestEnvironment& get_test_environment();

/**
 * @brief Reset the test environment
 */
void reset_test_environment();

} // namespace test
} // namespace qlever