# Makefile for YAWL Erlang Interface Bridge
# Alternative build system for CI/CD pipelines

.PHONY: all generate compile test integration native clean help

# Default target
all: generate compile test

# Configuration
JAVA ?= java
MVN ?= mvn
JEXTRACT ?= jextract

# Check for required tools
check-deps:
	@which $(JAVA) >/dev/null 2>&1 || { echo "Error: Java not found"; exit 1; }
	@which $(MVN) >/dev/null 2>&1 || { echo "Error: Maven not found"; exit 1; }
	@which $(JEXTRACT) >/dev/null 2>&1 || { echo "Error: jextract not found. Set JEXTRACT=/path/to/jextract"; exit 1; }

# Generate jextract bindings
generate: check-deps
	@echo "Generating jextract bindings..."
	@mkdir -p target/generated-sources/jextract
	$(JEXTRACT) \
		--output target/generated-sources/jextract \
		../headers/ei.h \
		--target-package org.yawlfoundation.yawl.nativebridge.erlang.generated \
		--include ../headers
	@mvn generate-sources

# Compile the project
compile: generate
	@echo "Compiling project..."
	@mvn compile

# Run unit tests
test: compile
	@echo "Running unit tests..."
	@mvn test

# Run integration tests
integration: compile
	@echo "Running integration tests..."
	@mvn test -Pintegration-test

# Build native image
native: compile
	@echo "Building native image..."
	@mvn -Pnative-image package

# Clean build artifacts
clean:
	@echo "Cleaning project..."
	@mvn clean
	@rm -rf target/generated-sources

# Show help
help:
	@echo "Available targets:"
	@echo "  all         - Generate bindings, compile, and run tests"
	@echo "  generate    - Generate jextract bindings"
	@echo "  compile     - Compile the project"
	@echo "  test        - Run unit tests"
	@echo "  integration - Run integration tests"
	@echo "  native      - Build native image"
	@echo "  clean       - Clean the project"
	@echo ""
	@echo "Variables:"
	@echo "  JAVA        - Java executable (default: java)"
	@echo "  MVN         - Maven executable (default: mvn)"
	@echo "  JEXTRACT    - jextract executable (default: jextract)"