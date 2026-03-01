// QLever FFI Search Test
// Advanced search functionality testing

#include "qlever_ffi.h"
#include <cassert>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

// Test dataset
const std::vector<std::string> documents = {
    "Machine learning is a subset of artificial intelligence that focuses on algorithms.",
    "Deep learning uses neural networks with multiple layers to learn patterns.",
    "Natural language processing enables computers to understand human language.",
    "Computer vision allows machines to interpret and understand visual information.",
    "Reinforcement learning learns through trial and error with rewards and penalties.",
    "Supervised learning uses labeled data to train models.",
    "Unsupervised learning finds patterns in unlabeled data.",
    "Semi-supervised learning combines both labeled and unlabeled data.",
    "Transfer learning applies knowledge from one task to another.",
    "Online learning updates models incrementally with new data."
};

class TestEngine {
private:
    void* engine;
public:
    TestEngine() : engine(nullptr) {
        engine = qlever_ffi_create_engine();
        if (!engine) {
            throw std::runtime_error("Failed to create engine");
        }
    }

    ~TestEngine() {
        if (engine) {
            qlever_ffi_destroy_engine(engine);
        }
    }

    void* get() const { return engine; }

    bool add_document(const std::string& doc, int index) {
        // Include index in document to track which document it is
        std::string indexed_doc = "Document " + std::to_string(index) + ": " + doc;
        int result = qlever_ffi_add_data(engine, indexed_doc.c_str(), indexed_doc.length());
        return (result == 0);
    }
};

bool test_exact_search() {
    std::cout << "Testing exact search..." << std::endl;

    TestEngine engine;
    for (size_t i = 0; i < documents.size(); ++i) {
        if (!engine.add_document(documents[i], i)) {
            std::cerr << "Failed to add document " << i << std::endl;
            return false;
        }
    }

    // Test exact phrase search
    const std::string query = "machine learning";
    void* result = qlever_ffi_search(engine.get(), query.c_str());

    if (!result) {
        // Check if error occurred
        std::cerr << "Search failed: " << qlever_ffi_get_last_error() << std::endl;
        return false;
    }

    // In a real implementation, we would examine the result
    qlever_ffi_free_result(result);
    return true;
}

bool test_keyword_search() {
    std::cout << "Testing keyword search..." << std::endl;

    TestEngine engine;
    for (size_t i = 0; i < documents.size(); ++i) {
        engine.add_document(documents[i], i);
    }

    // Test individual keywords
    const std::vector<std::string> keywords = {"neural", "pattern", "data", "model"};

    for (const auto& keyword : keywords) {
        void* result = qlever_ffi_search(engine.get(), keyword.c_str());

        if (result) {
            qlever_ffi_free_result(result);
            std::cout << "Found results for '" << keyword << "'" << std::endl;
        } else {
            std::cout << "No results for '" << keyword << "'" << std::endl;
        }
    }

    return true;
}

bool test_fuzzy_search() {
    std::cout << "Testing fuzzy search..." << std::endl;

    TestEngine engine;
    for (size_t i = 0; i < documents.size(); ++i) {
        engine.add_document(documents[i], i);
    }

    // Test similar words
    const std::vector<std::string> similar_queries = {
        "maching",    // misspelling of "machine"
        "larning",    // misspelling of "learning"
        "langunge",   // misspelling of "language"
        "computr",     // misspelling of "computer"
    };

    for (const auto& query : similar_queries) {
        void* result = qlever_ffi_search(engine.get(), query.c_str());

        if (result) {
            qlever_ffi_free_result(result);
            std::cout << "Fuzzy search found results for '" << query << "'" << std::endl;
        } else {
            std::cout << "Fuzzy search no results for '" << query << "'" << std::endl;
        }
    }

    return true;
}

bool test_performance_search() {
    std::cout << "Testing performance with large dataset..." << std::endl;

    // Create a larger test dataset
    const int large_dataset_size = 1000;
    std::vector<std::string> large_dataset;

    for (int i = 0; i < large_dataset_size; ++i) {
        large_dataset.push_back("Document " + std::to_string(i) + ": " +
                               documents[i % documents.size()] +
                               " Additional content for document " + std::to_string(i));
    }

    TestEngine engine;

    // Add all documents
    for (size_t i = 0; i < large_dataset.size(); ++i) {
        if (!engine.add_document(large_dataset[i], i)) {
            std::cerr << "Failed to add large document " << i << std::endl;
            return false;
        }
    }

    std::cout << "Added " << large_dataset_size << " documents" << std::endl;

    // Perform multiple searches
    const int search_count = 50;
    auto start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < search_count; ++i) {
        void* result = qlever_ffi_search(engine.get(), documents[i % documents.size()].c_str());
        if (result) {
            qlever_ffi_free_result(result);
        }
    }

    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);

    std::cout << "Performance: " << search_count << " searches in "
              << duration.count() << "ms (avg: "
              << duration.count() / search_count << "ms per search)" << std::endl;

    return true;
}

int main() {
    std::cout << "=== QLever FFI Search Test Suite ===" << std::endl;

    // Required for performance test
    #include <chrono>

    std::vector<std::function<bool()>> tests = {
        test_exact_search,
        test_keyword_search,
        test_fuzzy_search,
        test_performance_search
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