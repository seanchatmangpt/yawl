# Makefile for rust4pm NIF library
# Targets for building, testing, and documentation

# Variables
CARGO = cargo
CBINDGEN = cbindgen
TARGET_DIR = target
RELEASE_DIR = $(TARGET_DIR)/release
DEBUG_DIR = $(TARGET_DIR)/debug
LIB_NAME = rust4pm
SO_FILE = lib$(LIB_NAME).so
DYLIB_FILE = $(LIB_NAME).dylib

# Default target
.PHONY: all
all: build

# Build in release mode
.PHONY: build
build:
	$(CARGO) build --release

# Build in debug mode
.PHONY: debug
debug:
	$(CARGO) build

# Generate C header file
.PHONY: header
header:
	$(CBINDGEN) --config cbindgen.toml --crate $(LIB_NAME) --output rust4pm_nif.h

# Run tests
.PHONY: test
test:
	$(CARGO) test

# Run integration tests
.PHONY: integration-test
integration-test:
	$(CARGO) test --test integration

# Run all tests and check
.PHONY: check
check:
	$(CARGO) check --all
	$(CARGO) test --all

# Build and generate header
.PHONY: build-with-header
build-with-header: build header

# Clean build artifacts
.PHONY: clean
clean:
	$(CARGO) clean
	rm -f rust4pm_nif.h

# Clean and build
.PHONY: rebuild
rebuild: clean build

# Install (placeholder - depends on Erlang installation)
.PHANGY: install
install: build-with-header
	@echo "Manual installation required:"
	@echo "1. Copy $(RELEASE_DIR)/lib$(LIB_NAME).* to your Erlang lib directory"
	@echo "2. Include rust4pm_nif.h in your Erlang NIF project"

# Development helpers
.PHONY: dev
dev:
	@echo "Development commands:"
	@echo "  make build     - Build release version"
	@echo "  make debug     - Build debug version"
	@echo "  make test      - Run all tests"
	@echo "  make clean     - Clean artifacts"
	@echo "  make check     - Build and test"
	@echo "  make header    - Generate C header"
	@echo "  make help      - Show this help"

# Help target
.PHONY: help
help:
	@echo "Available targets:"
	@echo "  all          - Build release version (default)"
	@echo "  build        - Build in release mode"
	@echo "  debug        - Build in debug mode"
	@echo "  test         - Run tests"
	@echo "  check        - Build and test"
	@echo "  clean        - Clean build artifacts"
	@echo "  rebuild      - Clean and rebuild"
	@echo "  header       - Generate C header file"
	@echo "  install      - Show installation instructions"
	@echo "  dev          - Show development commands"
	@echo "  help         - Show this help"