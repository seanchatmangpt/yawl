# QLever FFI Makefile
# ===================
#
# Alternative build system using GNU Make
# Provides simpler interface for common build operations

# Configuration
CMAKE := cmake
CMAKE_BUILD_TYPE ?= Release
BUILD_DIR := build
TEST_BUILD_DIR := build_test
LIB_NAME := qlever_ffi
CC := $(CXX)
CXX := g++
CXXFLAGS := -std=c++20 -Wall -Wextra -O2 -DNDEBUG
LDFLAGS :=
CMAKE_FLAGS := -DCMAKE_BUILD_TYPE=$(CMAKE_BUILD_TYPE)

# Platform-specific settings
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
    SOEXT := .dylib
    LDFLAGS += -dynamiclib
else ifeq ($(UNAME_S),Linux)
    SOEXT := .so
    LDFLAGS += -shared
else ifeq ($(UNAME_S),Windows)
    SOEXT := .dll
    LDFLAGS += -shared
endif

.PHONY: all build test clean install help

# Default target
all: build

# Build the FFI library
build:
	@echo "Building QLever FFI library..."
	@mkdir -p $(BUILD_DIR)
	@cd $(BUILD_DIR) && $(CMAKE) $(CMAKE_FLAGS) ..
	@cd $(BUILD_DIR) && $(MAKE) -j$(shell nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
	@echo "✓ Library built: $(BUILD_DIR)/$(LIB_NAME)$(SOEXT)"

# Build with debug symbols
debug:
	$(MAKE) CMAKE_BUILD_TYPE=Debug

# Build tests
test: build
	@echo "Building test suite..."
	@mkdir -p $(TEST_BUILD_DIR)
	@cd $(TEST_BUILD_DIR) && $(CMAKE) -DBUILD_TESTS=ON $(CMAKE_FLAGS) ..
	@cd $(TEST_BUILD_DIR) && $(MAKE) -j$(shell nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
	@echo "✓ Tests built"

# Run tests
run-tests: test
	@echo "Running tests..."
	@if [ -f "$(TEST_BUILD_DIR)/test_qlever_ffi" ]; then \
		cd $(TEST_BUILD_DIR) && ./test_qlever_ffi; \
	else \
		echo "Warning: Test executable not found"; \
	fi

# Build and run tests in one command
check: test run-tests

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(BUILD_DIR) $(TEST_BUILD_DIR)
	@echo "✓ Cleaned"

# Install to Maven resources (experimental)
install: build
	@echo "Installing to Maven resources..."
	@mkdir -p $(BUILD_DIR)/../resources/native/$(shell uname | tr '[:upper:]' '[:lower:]')
	@cp $(BUILD_DIR)/$(LIB_NAME)$(SOEXT) $(BUILD_DIR)/../resources/native/$(shell uname | tr '[:upper:]' '[:lower:]')/
	@echo "✓ Installed to resources/native/$(shell uname | tr '[:upper:]' '[:lower:]')/"

# Show help
help:
	@echo "QLever FFI Makefile"
	@echo ""
	@echo "Targets:"
	@echo "  all         - Build the FFI library (default)"
	@echo "  build       - Build the FFI library"
	@echo "  debug       - Build with debug symbols"
	@echo "  test        - Build test suite"
	@echo "  run-tests   - Build and run tests"
	@echo "  check       - Build and run tests (convenience)"
	@echo "  clean       - Clean build artifacts"
	@echo "  install     - Install to Maven resources (experimental)"
	@echo "  help        - Show this help"
	@echo ""
	@echo "Variables:"
	@echo "  CMAKE_BUILD_TYPE - Build type (Release/Debug) [$(CMAKE_BUILD_TYPE)]"
	@echo "  CXX             - C++ compiler [$(CXX)]"
	@echo "  CXXFLAGS        - C++ compiler flags [$(CXXFLAGS)]"
	@echo "  LDFLAGS         - Linker flags [$(LDFLAGS)]"
	@echo ""
	@echo "Examples:"
	@echo "  make           # Build in release mode"
	@echo "  make debug     # Build in debug mode"
	@echo "  make check     # Build and run tests"
	@echo "  make install   # Install to Maven resources"