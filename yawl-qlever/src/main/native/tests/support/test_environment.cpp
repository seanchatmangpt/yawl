/**
 * @file test_environment.cpp
 * @brief Test environment setup and utilities for QLever FFI tests
 */

#include "test_environment.h"
#include <memory>
#include <stdexcept>
#include <vector>
#include <string>

namespace qlever {
namespace test {

class TestEnvironmentImpl : public TestEnvironment {
private:
    std::vector<std::string> test_documents_;
    bool initialized_;
    std::string test_dir_;

public:
    TestEnvironmentImpl() : initialized_(false) {}

    void initialize() override {
        if (initialized_) return;

        // Create test documents
        test_documents_ = {
            "The quick brown fox jumps over the lazy dog",
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit",
            "Machine learning is a subset of artificial intelligence",
            "Natural language processing enables computers to understand human language",
            "Deep learning uses neural networks with multiple layers"
        };

        initialized_ = true;
    }

    const std::vector<std::string>& get_test_documents() const override {
        return test_documents_;
    }

    size_t get_document_count() const override {
        return test_documents_.size();
    }

    const std::string& get_test_document(size_t index) const override {
        if (index >= test_documents_.size()) {
            throw std::out_of_range("Document index out of range");
        }
        return test_documents_[index];
    }

    void add_test_document(const std::string& doc) override {
        test_documents_.push_back(doc);
    }

    bool is_initialized() const override {
        return initialized_;
    }
};

// Global test environment instance
static std::unique_ptr<TestEnvironmentImpl> g_test_env;

TestEnvironment& get_test_environment() {
    if (!g_test_env) {
        g_test_env = std::make_unique<TestEnvironmentImpl>();
    }
    return *g_test_env;
}

void reset_test_environment() {
    g_test_env.reset();
}

} // namespace test
} // namespace qlever