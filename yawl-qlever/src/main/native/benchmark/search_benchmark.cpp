/**
 * @file search_benchmark.cpp
 * @brief Performance benchmarks for QLever FFI search operations
 */

#include <benchmark/benchmark.h>
#include "qlever_ffi.h"
#include <vector>
#include <string>
#include <random>
#include <chrono>

// Test dataset
static const int kNumDocuments = 1000;
static const int kDocSize = 1000; // bytes
static const int kNumQueries = 100;

class SearchBenchmark {
public:
    SearchBenchmark() {
        documents_.reserve(kNumDocuments);
        queries_.reserve(kNumQueries);

        // Generate test documents
        std::random_device rd;
        std::mt19937 gen(rd());
        std::uniform_int_distribution<> char_dist(0, 255);

        for (int i = 0; i < kNumDocuments; ++i) {
            std::string doc;
            doc.reserve(kDocSize);
            for (int j = 0; j < kDocSize; ++j) {
                doc += static_cast<char>(char_dist(gen));
            }
            documents_.push_back(doc);
        }

        // Generate test queries
        for (int i = 0; i < kNumQueries; ++i) {
            std::string query;
            query.reserve(50);
            for (int j = 0; j < 50; ++j) {
                query += static_cast<char>(char_dist(gen));
            }
            queries_.push_back(query);
        }
    }

    void setup_engine(void* engine) {
        for (const auto& doc : documents_) {
            qlever_ffi_add_data(engine, doc.c_str(), doc.length());
        }
    }

    void search_benchmark(void* engine, benchmark::State& state) {
        for (auto _ : state) {
            auto start = std::chrono::high_resolution_clock::now();

            // Perform search
            void* result = qlever_ffi_search(engine, queries_[state.range(0)].c_str());

            auto end = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end - start);

            state.SetIterationTime(duration.count() * 1e-9); // Convert to seconds

            if (result) {
                qlever_ffi_free_result(result);
            }
        }
    }

private:
    std::vector<std::string> documents_;
    std::vector<std::string> queries_;
};

static SearchBenchmark* search_benchmark = nullptr;

// Benchmark: Single document search
static void BM_SearchSingleDoc(benchmark::State& state) {
    if (!search_benchmark) return;

    void* engine = qlever_ffi_create_engine();
    search_benchmark->setup_engine(engine);

    search_benchmark->search_benchmark(engine, state);

    qlever_ffi_destroy_engine(engine);
}

// Benchmark: Multiple document search
static void BM_SearchMultipleDocs(benchmark::State& state) {
    if (!search_benchmark) return;

    void* engine = qlever_ffi_create_engine();
    search_benchmark->setup_engine(engine);

    search_benchmark->search_benchmark(engine, state);

    qlever_ffi_destroy_engine(engine);
}

// Benchmark: Stress test with many documents
static void BM_SearchStress(benchmark::State& state) {
    if (!search_benchmark) return;

    void* engine = qlever_ffi_create_engine();
    search_benchmark->setup_engine(engine);

    search_benchmark->search_benchmark(engine, state);

    qlever_ffi_destroy_engine(engine);
}

// Register benchmarks
BENCHMARK(BM_SearchSingleDoc)->Range(1, kNumQueries)->Unit(benchmark::kSecond);
BENCHMARK(BM_SearchMultipleDocs)->Range(1, kNumQueries)->Unit(benchmark::kSecond);
BENCHMARK(BM_SearchStress)->Range(1, kNumQueries)->Unit(benchmark::kSecond);

// Main function
BENCHMARK_MAIN();