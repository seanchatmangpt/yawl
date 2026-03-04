# Makefile for YAWL Process Mining Examples

.PHONY: all build test clean help

# Default target
all: build

# Build the Erlang application
build:
	@echo "Building YAWL Erlang application..."
	@cd .. && rebar3 compile

# Run the test script
test: build
	@echo "Running process mining examples..."
	escript test_pm_example.escript

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	@cd .. && rebar3 clean

# Show help
help:
	@echo "Available targets:"
	@echo "  all     - Build the Erlang application (default)"
	@echo "  build   - Build the Erlang application"
	@echo "  test    - Build and run the test script"
	@echo "  clean   - Clean build artifacts"
	@echo "  help    - Show this help message"