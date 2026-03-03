#!/bin/bash

# Build script for QLever Native FFI
# This script builds the native library and runs tests

set -e  # Exit on any error

# Configuration
PROJECT_NAME="qlever_native"
BUILD_DIR="build"
INSTALL_DIR="/usr/local"
VERBOSE=0
CLEAN_BUILD=0
RUN_TESTS=1
BUILD_SHARED=1
BUILD_STATIC=0
DEBUG_BUILD=1

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Print usage
function print_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -h, --help           Show this help message"
    echo "  -v, --verbose        Verbose output"
    echo "  -c, --clean          Clean build directory before building"
    echo "  -t, --tests          Run tests (default: on)"
    echo "  -s, --shared         Build shared library (default: on)"
    echo "  -a, --static         Build static library"
    echo "  -i, --install        Install to system"
    echo "  -d, --debug          Debug build (default: on)"
    echo "  -r, --release        Release build (no debug symbols)"
    echo "  --prefix DIR         Installation prefix (default: /usr/local)"
    echo ""
    echo "Examples:"
    echo "  $0                          # Build with default settings"
    echo "  $0 --clean --release        # Clean and build release version"
    echo "  $0 --static --install       # Build static library and install"
    echo "  $0 --tests --verbose        # Build and run tests with verbose output"
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        -v|--verbose)
            VERBOSE=1
            shift
            ;;
        -c|--clean)
            CLEAN_BUILD=1
            shift
            ;;
        -t|--tests)
            RUN_TESTS=1
            shift
            ;;
        -s|--shared)
            BUILD_SHARED=1
            shift
            ;;
        -a|--static)
            BUILD_STATIC=1
            shift
            ;;
        -i|--install)
            INSTALL=1
            shift
            ;;
        -d|--debug)
            DEBUG_BUILD=1
            shift
            ;;
        -r|--release)
            DEBUG_BUILD=0
            shift
            ;;
        --prefix)
            INSTALL_DIR="$2"
            shift 2
            ;;
        *)
            echo "Unknown option: $1"
            print_usage
            exit 1
            ;;
    esac
done

# Function to print colored output
function print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

function print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

function print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

function print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Clean build directory
if [[ $CLEAN_BUILD -eq 1 ]]; then
    print_status "Cleaning build directory..."
    rm -rf $BUILD_DIR
fi

# Create build directory
mkdir -p $BUILD_DIR
cd $BUILD_DIR

# Configure CMake
print_status "Configuring CMake..."
CMAKE_ARGS=()

# Build type
if [[ $DEBUG_BUILD -eq 1 ]]; then
    CMAKE_ARGS+=(-DCMAKE_BUILD_TYPE=Debug)
    if [[ $VERBOSE -eq 1 ]]; then
        CMAKE_ARGS+=(-DCMAKE_VERBOSE_MAKEFILE=ON)
    fi
else
    CMAKE_ARGS+=(-DCMAKE_BUILD_TYPE=Release)
fi

# Build options
CMAKE_ARGS+=(-DBUILD_SHARED_LIBS=$BUILD_SHARED)
CMAKE_ARGS+=(-DBUILD_STATIC_LIBS=$BUILD_STATIC)
CMAKE_ARGS+=(-DBUILD_TESTS=$RUN_TESTS)
CMAKE_ARGS+=(-DWITH_DEBUG=$DEBUG_BUILD)
CMAKE_ARGS+=(-DCMAKE_INSTALL_PREFIX=$INSTALL_DIR)

if [[ $VERBOSE -eq 1 ]]; then
    echo "CMake arguments: ${CMAKE_ARGS[*]}"
fi

cmake "${CMAKE_ARGS[@]}" ..

# Build
print_status "Building..."
if [[ $VERBOSE -eq 1 ]]; then
    make -j$(nproc)
else
    make -j$(nproc) > build.log 2>&1
fi

# Check build result
if [[ $? -eq 0 ]]; then
    print_success "Build completed successfully!"
else
    print_error "Build failed!"
    if [[ $VERBOSE -eq 0 ]]; then
        echo "Build log:"
        tail -20 build.log
    fi
    exit 1
fi

# Run tests
if [[ $RUN_TESTS -eq 1 ]]; then
    print_status "Running tests..."
    if [[ -f "./qlever_tests" ]]; then
        if [[ $VERBOSE -eq 1 ]]; then
            ./qlever_tests
        else
            ./qlever_tests > test_results.log 2>&1
        fi

        if [[ $? -eq 0 ]]; then
            print_success "All tests passed!"
        else
            print_error "Some tests failed!"
            if [[ $VERBOSE -eq 0 ]]; then
                echo "Test results:"
                cat test_results.log
            fi
        fi
    else
        print_warning "No test executable found"
    fi
fi

# Install if requested
if [[ $INSTALL -eq 1 ]]; then
    print_status "Installing to $INSTALL_DIR..."
    make install

    if [[ $? -eq 0 ]]; then
        print_success "Installation completed successfully!"

        # Print installation summary
        echo ""
        echo "Installation summary:"
        echo "  Libraries: $INSTALL_DIR/lib/"
        echo "  Headers: $INSTALL_DIR/include/"
        echo "  pkgconfig: $INSTALL_DIR/lib/pkgconfig/"
        echo ""
        echo "To use the library:"
        echo "  pkg-config --cflags --libs qlever-native"
    else
        print_error "Installation failed!"
    fi
fi

# Generate documentation if Doxygen is available
if command -v doxygen &> /dev/null; then
    print_status "Generating documentation..."
    make doc
    if [[ $? -eq 0 ]]; then
        print_success "Documentation generated in $BUILD_DIR/docs/"
    fi
fi

# Summary
echo ""
echo "Build Summary:"
echo "  Project: $PROJECT_NAME"
echo "  Build directory: $BUILD_DIR"
echo "  Installation prefix: $INSTALL_DIR"
echo "  Debug build: $DEBUG_BUILD"
echo "  Shared library: $BUILD_SHARED"
echo "  Static library: $BUILD_STATIC"
echo "  Tests: $RUN_TESTS"

if [[ $INSTALL -eq 1 ]]; then
    echo "  Installed: Yes"
else
    echo "  Installed: No"
fi

echo ""
print_success "Build process completed!"