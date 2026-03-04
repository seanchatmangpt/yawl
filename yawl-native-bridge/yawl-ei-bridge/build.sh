#!/bin/bash
#
# Build script for YAWL Erlang Interface Bridge
# Handles jextract generation, compilation, and testing
#

set -e

# Configuration
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
JEXTRACT="${JEXTRACT:-jextract}"
JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"
GRAALVM_HOME="${GRAALVM_HOME:-$JAVA_HOME}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print status messages
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check dependencies
check_dependencies() {
    print_status "Checking dependencies..."

    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java not found. Please install Java 21+."
        exit 1
    fi

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        print_error "Maven not found. Please install Maven 3.8+."
        exit 1
    fi

    # Check jextract
    if ! command -v jextract &> /dev/null; then
        print_warning "jextract not found in PATH. Trying GRAALVM_HOME..."
        local jextract_path="$GRAALVM_HOME/bin/jextract"
        if [ -f "$jextract_path" ]; then
            export JEXTRACT="$jextract_path"
            print_status "Found jextract at: $JEXTRACT"
        else
            print_error "jextract not found. Please ensure GraalVM is installed and jextract is in PATH or GRAALVM_HOME/bin."
            exit 1
        fi
    fi

    print_status "All dependencies found."
}

# Function to generate jextract bindings
generate_bindings() {
    print_status "Generating jextract bindings..."

    # Create generated sources directory
    mkdir -p "$PROJECT_ROOT/target/generated-sources/jextract"

    # Generate bindings for ei.h
    $JEXTRACT \
        --output "$PROJECT_ROOT/target/generated-sources/jextract" \
        "$PROJECT_ROOT/../headers/ei.h" \
        --target-package org.yawlfoundation.yawl.nativebridge.erlang.generated \
        --include "$PROJECT_ROOT/../headers" \
        -- \
        -I/usr/local/include \
        -I/usr/include

    if [ $? -eq 0 ]; then
        print_status "jextract bindings generated successfully."
    else
        print_error "jextract generation failed."
        exit 1
    fi
}

# Function to compile the project
compile_project() {
    print_status "Compiling project..."

    # Add generated sources to the build
    mvn generate-sources -Dorg.codehaus.mojo.exec.jextract.executable=$JEXTRACT

    if [ $? -eq 0 ]; then
        print_status "Project compiled successfully."
    else
        print_error "Compilation failed."
        exit 1
    fi
}

# Function to run tests
run_tests() {
    print_status "Running tests..."

    # Check if integration tests should be run
    if [ "$1" = "integration" ]; then
        print_status "Running integration tests..."
        mvn test -Pintegration-test
    else
        mvn test
    fi

    if [ $? -eq 0 ]; then
        print_status "All tests passed."
    else
        print_error "Tests failed."
        exit 1
    fi
}

# Function to build native image
build_native() {
    print_status "Building native image..."

    # Check if native-image is available
    if ! command -v native-image &> /dev/null; then
        print_warning "native-image not found. Skipping native build."
        return
    fi

    mvn -Pnative-image package

    if [ $? -eq 0 ]; then
        print_status "Native image built successfully."
        print_status "Binary available at: $PROJECT_ROOT/target/yawl-ei-bridge"
    else
        print_error "Native build failed."
        exit 1
    fi
}

# Function to clean up
clean_project() {
    print_status "Cleaning project..."
    mvn clean
    rm -rf "$PROJECT_ROOT/target/generated-sources"
    print_status "Project cleaned."
}

# Function to show help
show_help() {
    echo "Usage: $0 [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  all         - Generate bindings, compile, and run tests"
    echo "  generate    - Generate jextract bindings"
    echo "  compile     - Compile the project"
    echo "  test        - Run unit tests"
    echo "  integration - Run integration tests"
    echo "  native      - Build native image"
    echo "  clean       - Clean the project"
    echo "  help        - Show this help message"
    echo ""
    echo "Environment variables:"
    echo "  JEXTRACT       - Path to jextract executable"
    echo "  JAVA_HOME      - Java home directory"
    echo "  GRAALVM_HOME   - GraalVM home directory"
}

# Main script logic
case "${1:-all}" in
    all)
        check_dependencies
        generate_bindings
        compile_project
        run_tests
        ;;
    generate)
        check_dependencies
        generate_bindings
        ;;
    compile)
        check_dependencies
        compile_project
        ;;
    test)
        check_dependencies
        compile_project
        run_tests
        ;;
    integration)
        check_dependencies
        compile_project
        run_tests integration
        ;;
    native)
        check_dependencies
        compile_project
        build_native
        ;;
    clean)
        clean_project
        ;;
    help)
        show_help
        ;;
    *)
        print_error "Unknown command: $1"
        show_help
        exit 1
        ;;
esac

print_status "Build completed successfully!"