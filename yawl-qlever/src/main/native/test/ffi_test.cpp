// QLever FFI Basic Test
// Tests core FFI functionality

#include "qlever_ffi.h"
#include <cassert>
#include <iostream>
#include <memory>

// RAII wrapper for engine
class EngineWrapper {
private:
    void* engine;
public:
    EngineWrapper() : engine(nullptr) {}
    ~EngineWrapper() {
        if (engine) {
            qlever_ffi_destroy_engine(engine);
        }
    }

    void* get() const { return engine; }
    void* create() {
        if (!engine) {
            engine = qlever_ffi_create_engine();
        }
        return engine;
    }
};

int main() {
    std::cout << "=== QLever FFI Test ===" << std::endl;

    // Test engine creation and destruction
    EngineWrapper engine;

    std::cout << "Creating engine..." << std::endl;
    void* raw_engine = engine.create();

    if (!raw_engine) {
        const char* error = qlever_ffi_get_last_error();
        std::cerr << "Failed to create engine: " << error << std::endl;
        return 1;
    }

    std::cout << "Engine created successfully" << std::endl;

    // Test adding data
    const char* test_data = "Test document";
    size_t data_size = strlen(test_data);

    std::cout << "Adding data to engine..." << std::endl;
    int result = qlever_ffi_add_data(raw_engine, test_data, data_size);

    if (result != 0) {
        const char* error = qlever_ffi_get_last_error();
        std::cerr << "Failed to add data: " << error << std::endl;
        return 1;
    }

    std::cout << "Data added successfully" << std::endl;

    // Test search functionality
    std::cout << "Performing search..." << std::endl;
    void* search_result = qlever_ffi_search(raw_engine, "test");

    if (!search_result) {
        const char* error = qlever_ffi_get_last_error();
        std::cerr << "Search failed: " << error << std::endl;
        return 1;
    }

    std::cout << "Search completed successfully" << std::endl;

    // Clean up search result
    qlever_ffi_free_result(search_result);
    std::cout << "Search result freed" << std::endl;

    std::cout << "=== All tests passed ===" << std::endl;
    return 0;
}