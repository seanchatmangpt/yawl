# Native Implementation Guide for QLever FFI

## Overview

This guide provides the implementation details for the native C++ implementation of the QLever FFI interface. The implementation follows the Lippincott pattern for exception handling and provides a clean bridge between Java and QLever's C++ engine.

## Implementation Structure

```
src/main/native/
├── qlever_ffi.h          # C interface header
├── qlever_native.cpp     # Main implementation file
├── qlever_engine.cpp     # QLever engine wrapper
├── qlever_utils.cpp      # Utility functions
└── LIPPINCOTT_PATTERN.md # Exception handling pattern
```

## Main Implementation File

### qlever_native.cpp

```cpp
#include "qlever_ffi.h"
#include <string>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <vector>

// Forward declarations
class QLeverEngineImpl;
class QLeverResultImpl;

// Thread-local storage for error buffers
static thread_local char error_buffer[2048];

// Global mutex for thread-safe operations
static std::mutex g_engine_mutex;

// Engine wrapper class
class QLeverEngine {
private:
    std::unique_ptr<QLeverEngineImpl> impl;

public:
    QLeverEngine(const std::string& index_path) {
        impl = std::make_unique<QLeverEngineImpl>(index_path);
    }

    ~QLeverEngine() {
        // RAII cleanup
    }

    std::unique_ptr<QLeverResult> query(const std::string& sparql) {
        return impl->query(sparql);
    }

    bool validateQuery(const std::string& sparql) {
        return impl->validateQuery(sparql);
    }

    std::string getVersion() const {
        return impl->getVersion();
    }

    std::string getEngineInfo() const {
        return impl->getEngineInfo();
    }
};

// Result wrapper class
class QLeverResult {
private:
    std::string json_data;
    std::unique_ptr<QLeverResultImpl> impl;

public:
    QLeverResult(const std::string& json) : json_data(json) {}

    const std::string& getJSON() const {
        return json_data;
    }
};

// Lippincott pattern implementation
template<typename Func>
QleverStatus safe_execute(Func&& func) {
    try {
        func();
        return QleverStatus{QLEVER_STATUS_OK, 0, nullptr, nullptr};
    } catch (const std::exception& e) {
        strncpy(error_buffer, e.what(), sizeof(error_buffer) - 1);
        error_buffer[sizeof(error_buffer) - 1] = '\0';
        return QleverStatus{QLEVER_STATUS_ERROR_EXECUTION, 0, strdup(error_buffer), nullptr};
    } catch (...) {
        strncpy(error_buffer, "Unknown exception", sizeof(error_buffer) - 1);
        error_buffer[sizeof(error_buffer) - 1] = '\0';
        return QleverStatus{QLEVER_STATUS_ERROR_EXECUTION, 0, strdup(error_buffer), nullptr};
    }
}

// Engine lifecycle functions
QleverStatus qlever_engine_create(const char* index_path, void** out_handle) {
    return safe_execute([&] {
        auto engine = std::make_unique<QLeverEngine>(index_path ? index_path : "");
        *out_handle = engine.release();  // Transfer ownership to caller
    });
}

QleverStatus qlever_engine_destroy(void* engine_handle) {
    return safe_execute([&] {
        auto engine = static_cast<QLeverEngine*>(engine_handle);
        delete engine;  // Cleanup and destroy
    });
}

// Query functions
QleverStatus qlever_engine_query(void* engine_handle, const char* sparql, void** out_result) {
    return safe_execute([&] {
        auto engine = static_cast<QLeverEngine*>(engine_handle);
        auto result = engine->query(sparql ? sparql : "");
        *out_result = new QLeverResult(result->getJSON());
    });
}

QleverStatus qlever_result_get_data(void* result_handle, const char** out_data, size_t* out_len) {
    return safe_execute([&] {
        auto result = static_cast<QLeverResult*>(result_handle);
        const std::string& json = result->getJSON();

        *out_data = strdup(json.c_str());
        if (out_len) {
            *out_len = json.length();
        }
    });
}

QleverStatus qlever_result_free(void* result_handle) {
    return safe_execute([&] {
        auto result = static_cast<QLeverResult*>(result_handle);
        delete result;
    });
}

// Utility functions
QleverStatus qlever_validate_query(const char* sparql, int* out_valid) {
    return safe_execute([&] {
        auto engine = std::make_unique<QLeverEngine>("");
        *out_valid = engine->validateQuery(sparql ? sparql : "") ? 1 : 0;
    });
}

QleverStatus qlever_status_free_message(const QleverStatus* status) {
    return safe_execute([&] {
        if (status && status->message) {
            free((void*)status->message);
        }
    });
}

const char* qlever_status_string(int32_t code) {
    switch (code) {
        case QLEVER_STATUS_OK:
            return "OK";
        case QLEVER_STATUS_ERROR_PARSE:
            return "Query parsing failed";
        case QLEVER_STATUS_ERROR_EXECUTION:
            return "Query execution failed";
        case QLEVER_STATUS_ERROR_TIMEOUT:
            return "Query timeout";
        case QLEVER_STATUS_ERROR_MEMORY:
            return "Out of memory";
        case QLEVER_STATUS_ERROR_CONFIG:
            return "Configuration error";
        default:
            return "Unknown error";
    }
}

const char* qlever_get_version(void) {
    static std::string version = "QLever Native Bridge v1.0.0";
    return version.c_str();
}

QleverStatus qlever_get_engine_info(const char** out_info, size_t* out_len) {
    return safe_execute([&] {
        auto engine = std::make_unique<QLeverEngine>("");
        std::string info = engine->getEngineInfo();

        *out_info = strdup(info.c_str());
        if (out_len) {
            *out_len = info.length();
        }
    });
}
```

## QLever Engine Wrapper

### qlever_engine.cpp

```cpp
#include "qlever_ffi.h"
#include <qlever/engine.h>
#include <qlever/parser.h>
#include <qlever/index.h>

class QLeverEngineImpl {
private:
    std::unique_ptr<QLever::Engine> engine;
    std::string index_path;

public:
    QLeverEngineImpl(const std::string& indexPath) : index_path(indexPath) {
        // Initialize QLever engine
        engine = std::make_unique<QLever::Engine>();

        if (!indexPath.empty()) {
            if (!engine->loadIndex(indexPath)) {
                throw std::runtime_error("Failed to load QLever index: " + indexPath);
            }
        }
    }

    std::unique_ptr<QLeverResult> query(const std::string& sparql) {
        try {
            // Parse SPARQL query
            auto parsed_query = QLever::Parser::parseQuery(sparql);

            // Execute query
            auto result = engine->executeQuery(parsed_query);

            // Convert to JSON
            std::string json = result->toJSON();

            return std::make_unique<QLeverResult>(json);
        } catch (const QLever::ParserException& e) {
            throw std::runtime_error("Parse error: " + std::string(e.what()));
        } catch (const QLever::ExecutionException& e) {
            throw std::runtime_error("Execution error: " + std::string(e.what()));
        }
    }

    bool validateQuery(const std::string& sparql) {
        try {
            auto parsed_query = QLever::Parser::parseQuery(sparql);
            return true;  // If parsing succeeds, query is valid
        } catch (...) {
            return false;
        }
    }

    std::string getVersion() const {
        return "QLever " + QLever::getVersion();
    }

    std::string getEngineInfo() const {
        // Return engine configuration as JSON
        return R"({
            "version": ")" + getVersion() + R"(",
            "index_path": ")" + index_path + R"(",
            "memory_usage": )" + std::to_string(engine->getMemoryUsage()) + R"(,
            "query_count": )" + std::to_string(engine->getQueryCount()) + R"(,
            "cache_size": )" + std::to_string(engine->getCacheSize()) + R"(
        })";
    }
};
```

## Build Configuration

### CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.10)
project(qlever_native)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find dependencies
find_package(Threads REQUIRED)
find_package(Boost REQUIRED)

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${Boost_INCLUDE_DIRS}
)

# Source files
set(NATIVE_SOURCES
    qlever_native.cpp
    qlever_engine.cpp
    qlever_utils.cpp
)

# Create shared library
add_library(qlever_native SHARED ${NATIVE_SOURCES})

# Link libraries
target_link_libraries(qlever_native
    ${Boost_LIBRARIES}
    Threads::Threads
)

# Install target
install(TARGETS qlever_native
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
)

# Install headers
install(FILES qlever_ffi.h
    DESTINATION include
)
```

### Compilation Commands

```bash
# Compile with CMake
mkdir build
cd build
cmake ..
make

# Compile manually
g++ -std=c++17 -fPIC -shared \
    -I/usr/local/include \
    -I/path/to/qliver/include \
    qlever_native.cpp qlever_engine.cpp qlever_utils.cpp \
    -o libqlever_native.so \
    -lqliver -lboost_system -lpthread

# For macOS
g++ -std=c++17 -fPIC -shared \
    -I/usr/local/include \
    -I/path/to/qliver/include \
    qlever_native.cpp qlever_engine.cpp qlever_utils.cpp \
    -o libqlever_native.dylib \
    -lqliver -lboost_system -lpthread
```

## Testing Strategy

### Unit Tests

```cpp
#include <gtest/gtest.h>
#include <qlever_ffi.h>

class QLeverFFITest : public ::testing::Test {
protected:
    void SetUp() override {
        // Create test engine
        QleverStatus status = qlever_engine_create("/tmp/test-index", &engine_);
        ASSERT_EQ(status.code, QLEVER_STATUS_OK);
    }

    void TearDown() override {
        if (engine_) {
            qlever_engine_destroy(engine_);
        }
    }

    void* engine_ = nullptr;
};

TEST_F(QLeverFFITest, EngineCreation) {
    ASSERT_NE(engine_, nullptr);
}

TEST_F(QLeverFFITest, QueryExecution) {
    const char* query = "SELECT ?s WHERE { ?s ?p ?o }";
    void* result = nullptr;

    QleverStatus status = qlever_engine_query(engine_, query, &result);
    ASSERT_EQ(status.code, QLEVER_STATUS_OK);

    if (result) {
        const char* json_data;
        size_t json_len;
        status = qlever_result_get_data(result, &json_data, &json_len);
        ASSERT_EQ(status.code, QLEVER_STATUS_OK);

        ASSERT_NE(json_data, nullptr);
        ASSERT_GT(json_len, 0);

        free((void*)json_data);
        qlever_result_free(result);
    }
}

TEST_F(QLeverFFITest, QueryValidation) {
    const char* valid_query = "SELECT ?s WHERE { ?s ?p ?o }";
    const char* invalid_query = "INVALID SPARQL";

    int is_valid;
    QleverStatus status = qlever_validate_query(valid_query, &is_valid);
    ASSERT_EQ(status.code, QLEVER_STATUS_OK);
    ASSERT_EQ(is_valid, 1);

    status = qlever_validate_query(invalid_query, &is_valid);
    ASSERT_EQ(status.code, QLEVER_STATUS_OK);
    ASSERT_EQ(is_valid, 0);
}
```

### Integration Tests

```bash
# Run tests with gtest
./qlever_native_tests

# Memory leak detection
valgrind --leak-check=full ./qlever_native_tests

# Thread safety test
./qlever_native_threads_test
```

## Performance Optimization

### Memory Management

1. **Object Pool**: Reuse engine objects
2. **Buffer Pool**: Pre-allocate result buffers
3. **Zero-Copy**: For large results, use memory-mapped files
4. **Cache**: Cache frequent query results

### Concurrency

```cpp
// Thread pool for query execution
class QueryThreadPool {
private:
    std::vector<std::thread> workers;
    std::queue<std::function<void()>> tasks;
    std::mutex queue_mutex;
    std::condition_variable condition;
    bool stop;

public:
    QueryThreadPool(size_t threads) : stop(false) {
        for (size_t i = 0; i < threads; ++i) {
            workers.emplace_back([this] {
                while (true) {
                    std::function<void()> task;
                    {
                        std::unique_lock<std::mutex> lock(this->queue_mutex);
                        this->condition.wait(lock,
                            [this] { return this->stop || !this->tasks.empty(); });
                        if (this->stop && this->tasks.empty())
                            return;
                        task = std::move(this->tasks.front());
                        this->tasks.pop();
                    }
                    task();
                }
            });
        }
    }

    template<class F>
    void enqueue(F&& f) {
        {
            std::unique_lock<std::mutex> lock(queue_mutex);
            tasks.emplace(std::forward<F>(f));
        }
        condition.notify_one();
    }

    ~QueryThreadPool() {
        {
            std::unique_lock<std::mutex> lock(queue_mutex);
            stop = true;
        }
        condition.notify_all();
        for (auto &worker : workers)
            worker.join();
    }
};
```

## Debugging Tools

### Memory Profiling

```cpp
// Memory tracking class
class MemoryTracker {
private:
    size_t total_allocated = 0;
    std::mutex mutex;

public:
    void* allocate(size_t size) {
        void* ptr = malloc(size);
        {
            std::lock_guard<std::mutex> lock(mutex);
            total_allocated += size;
        }
        return ptr;
    }

    void deallocate(void* ptr, size_t size) {
        free(ptr);
        {
            std::lock_guard<std::mutex> lock(mutex);
            total_allocated -= size;
        }
    }

    size_t getTotalAllocated() const {
        return total_allocated;
    }
};

// Usage
void* operator new(size_t size) {
    return memory_tracker.allocate(size);
}

void operator delete(void* ptr, size_t size) {
    memory_tracker.deallocate(ptr, size);
}
```

### Performance Monitoring

```cpp
class PerformanceMonitor {
private:
    struct QueryStats {
        uint64_t total_time = 0;
        uint64_t query_count = 0;
        uint64_t min_time = UINT64_MAX;
        uint64_t max_time = 0;
    };

    std::unordered_map<std::string, QueryStats> stats;
    std::mutex mutex;

public:
    void recordQuery(const std::string& query, uint64_t duration) {
        std::lock_guard<std::mutex> lock(mutex);
        auto& stat = stats[query];
        stat.total_time += duration;
        stat.query_count++;
        stat.min_time = std::min(stat.min_time, duration);
        stat.max_time = std::max(stat.max_time, duration);
    }

    void printStats() {
        std::lock_guard<std::mutex> lock(mutex);
        for (const auto& [query, stat] : stats) {
            double avg_time = static_cast<double>(stat.total_time) / stat.query_count;
            printf("Query: %s\n", query.c_str());
            printf("  Count: %lu\n", stat.query_count);
            printf("  Avg Time: %.2f ms\n", avg_time / 1000.0);
            printf("  Min Time: %lu ms\n", stat.min_time / 1000.0);
            printf("  Max Time: %lu ms\n", stat.max_time / 1000.0);
        }
    }
};
```

This implementation guide provides the complete structure for the native QLever FFI implementation, following the Lippincott pattern for exception handling and ensuring memory safety and thread safety throughout the interface.