/**
 * @file engine_benchmark.cpp
 * @brief Performance benchmarks for QLever FFI engine operations
 */

#include <benchmark/benchmark.h>
#include "qlever_ffi.h"
#include <vector>
#include <string>
#include <random>

// Benchmark parameters
static const int kNumDocuments = 10000;
static const int kDocSize = 1000; // bytes

class EngineBenchmark {
public:
    EngineBenchmark() {
        documents_.reserve(kNumDocuments);
        std::random_device rd;
        std::mt19937 gen(rd());
        std::uniform_int_distribution<> char_dist(0, 255);

        // Generate test documents
        for (int i = 0; i < kNumDocuments; ++i) {
            std::string doc;
            doc.reserve(kDocSize);
            for (int j = 0; j < kDocSize; ++j) {
                doc += static_cast<char>(char_dist(gen));
            }
            documents_.push_back(doc);
        }
    }

    void data_insertion_benchmark(benchmark::State& state) {
        for (auto _ : state) {
            void* engine = qlever_ffi_create_engine();

            auto start = std::chrono::high_resolution_clock::now();

            // Insert documents
            for (int i = 0; i < state.range(0); ++i) {
                qlever_ffi_add_data(engine, documents_[i].c_str(), documents_[i].length());
            }

            auto end = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end - start);

            state.SetIterationTime(duration.count() * 1e-9); // Convert to seconds

            qlever_ffi_destroy_engine(engine);
        }
    }

    void engine_creation_benchmark(benchmark::State& state) {
        for (auto _ : state) {
            auto start = std::chrono::high_resolution_clock::now();

            // Create multiple engines
            for (int i = 0; i < state.range(0); ++i) {
                void* engine = qlever_ffi_create_engine();
                qlever_ffi_destroy_engine(engine);
            }

            auto end = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end - start);

            state.SetIterationTime(duration.count() * 1e-9); // Convert to seconds
        }
    }

private:
    std::vector<std::string> documents_;
};

static EngineBenchmark* engine_benchmark = nullptr;

// Benchmark: Engine creation
static void BM_EngineCreation(benchmark::State& state) {
    if (!engine_benchmark) return;
    engine_benchmark->engine_creation_benchmark(state);
}

// Benchmark: Data insertion
static void BM_DataInsertion(benchmark::State& state) {
    if (!engine_benchmark) return;
    engine_benchmark->data_insertion_benchmark(state);
}

// Benchmark: Mixed workload (create, insert, search)
static void BM_MixedWorkload(benchmark::State& state) {
    if (!engine_benchmark) return;

    const int docs_per_engine = state.range(0);
    const int num_engines = 10;

    for (auto _ : state) {
        auto start = std::chrono::high_resolution_clock::now();

        for (int e = 0; e < num_engines; ++e) {
            void* engine = qlever_ffi_create_engine();

            // Insert documents
            for (int i = 0; i < docs_per_engine; ++i) {
                qlever_ffi_add_data(engine, engine_benchmark->get_document(i).c_str(),
                                 engine_benchmark->get_document(i).length());
            }

            // Perform some searches
            for (int s = 0; s < 5; ++s) {
                void* result = qlever_ffi_search(engine, "test");
                if (result) {
                    qlever_ffi_free_result(result);
                }
            }

            qlever_ffi_destroy_engine(engine);
        }

        auto end = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::nanoseconds>(end - start);

        state.SetIterationTime(duration.count() * 1e-9); // Convert to seconds
    }
}

// Register benchmarks
BENCHMARK(BM_EngineCreation)->Range(10, 1000)->Unit(benchmark::kSecond);
BENCHMARK(BM_DataInsertion)->Range(10, 1000)->Unit(benchmark::kSecond);
BENCHMARK(BM_MixedWorkload)->Range(10, 1000)->Unit(benchmark::kSecond);

// Main function
BENCHMARK_MAIN();