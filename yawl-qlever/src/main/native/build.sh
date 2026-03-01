#!/bin/bash

# QLever FFI Build Script
# =======================
#
# This script builds the QLever FFI library with comprehensive feature support:
# - Cross-platform compilation (Linux, macOS, Windows)
# - jextract integration for Java bindings
# - Comprehensive testing framework
# - Maven resource integration
# - Platform-specific optimizations

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=== QLever FFI Build Script ===${NC}"
echo "Comprehensive build with platform detection and feature support"

# Configuration
BUILD_TYPE=${1:-Release}
BUILD_DIR="build"
LIBRARY_NAME="qlever_ffi"
CMAKE_MIN_VERSION="3.20"

# Parse command line arguments
RUN_TESTS=false
RUN_BENCHMARKS=false
INSTALL_ONLY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --test|-t)
            RUN_TESTS=true
            shift
            ;;
        --benchmark|-b)
            RUN_BENCHMARKS=true
            shift
            ;;
        --install|-i)
            INSTALL_ONLY=true
            shift
            ;;
        --debug|-d)
            BUILD_TYPE=Debug
            shift
            ;;
        --release|-r)
            BUILD_TYPE=Release
            shift
            ;;
        *)
            echo "Unknown option: $1"
            echo "Usage: $0 [--test|--benchmark|--install|--debug|--release]"
            exit 1
            ;;
    esac
done

# Detect platform
OS="$(uname -s)"
ARCH="$(uname -m)"

echo -e "${BLUE}Platform: $OS $ARCH${NC}"
echo -e "${BLUE}Build Type: $BUILD_TYPE${NC}"
echo ""

# Check prerequisites
echo -e "${YELLOW}Checking prerequisites...${NC}"

# Check CMake version
if ! command -v cmake &> /dev/null; then
    echo -e "${RED}Error: CMake is not installed${NC}"
    exit 1
fi

CMAKE_VERSION=$(cmake --version | head -n1 | grep -oP '\d+\.\d+' | head -n1)
IFS='.' read -ra CMAKE_VERSION_PARTS <<< "$CMAKE_VERSION"
if [[ "${CMAKE_VERSION_PARTS[0]}" -lt 3 || ("${CMAKE_VERSION_PARTS[0]}" -eq 3 && "${CMAKE_VERSION_PARTS[1]}" -lt 20) ]]; then
    echo -e "${RED}Error: CMake version $CMAKE_VERSION is too old. Requires $CMAKE_MIN_VERSION or higher${NC}"
    exit 1
fi

echo -e "${GREEN}✓ CMake $CMAKE_VERSION found${NC}"

# Check C++ compiler
if command -v g++ &> /dev/null; then
    CXX="g++"
elif command -v clang++ &> /dev/null; then
    CXX="clang++"
else
    echo -e "${RED}Error: C++ compiler not found${NC}"
    exit 1
fi

echo -e "${GREEN}✓ $CXX found${NC}"

# Check for QLever
if [ -d "../../../qlever" ]; then
    echo -e "${GREEN}✓ QLever found as subdirectory${NC}"
    CMAKE_ARGS="$CMAKE_ARGS -DQLEVER_SUBDIR=ON"
elif [ -n "$QLEVER_DIR" ]; then
    echo -e "${GREEN}✓ QLever found via QLEVER_DIR${NC}"
    CMAKE_ARGS="$CMAKE_ARGS -DQLEVER_DIR=$QLEVER_DIR"
else
    echo -e "${YELLOW}⚠ QLever not found, will build stub${NC}"
fi

# Check for jextract
if command -v jextract &> /dev/null; then
    echo -e "${GREEN}✓ jextract found${NC}"
    CMAKE_ARGS="$CMAKE_ARGS -Djextract_FOUND=ON"
else
    echo -e "${YELLOW}⚠ jextract not found${NC}"
fi

# Platform-specific CMake arguments
case "$OS" in
    Linux)
        CMAKE_ARGS="$CMAKE_ARGS -DCMAKE_BUILD_TYPE=$BUILD_TYPE"
        if [ "$ARCH" = "x86_64" ]; then
            CMAKE_ARGS="$CMAKE_ARGS -DCMAKE_CXX_FLAGS=-march=native"
        fi
        ;;
    Darwin)
        CMAKE_ARGS="$CMAKE_ARGS -DCMAKE_BUILD_TYPE=$BUILD_TYPE"
        if [ "$ARCH" = "arm64" ]; then
            CMAKE_ARGS="$CMAKE_ARGS -DCMAKE_OSX_ARCHITECTURES=arm64"
        else
            CMAKE_ARGS="$CMAKE_ARGS -DCMAKE_OSX_ARCHITECTURES=x86_64"
        fi
        ;;
    MINGW*|CYGWIN*|MSYS*|Windows)
        CMAKE_ARGS="$CMAKE_ARGS -G \"Visual Studio 17 2022\" -A x64"
        ;;
    *)
        echo -e "${YELLOW}⚠ Unknown platform: $OS${NC}"
        CMAKE_ARGS="$CMAKE_ARGS -DCMAKE_BUILD_TYPE=$BUILD_TYPE"
        ;;
esac

# Create build directory
mkdir -p "$BUILD_DIR"

# Build the FFI library
echo ""
echo -e "${YELLOW}Building QLever FFI library...${NC}"
cd "$BUILD_DIR"

# Configure CMake
echo -e "${YELLOW}Configuring CMake...${NC}"
cmake .. $CMAKE_ARGS

# Build the library
echo -e "${YELLOW}Building library...${NC}"
if command -v ninja &> /dev/null; then
    ninja
else
    cmake --build . -j$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
fi

echo -e "${GREEN}✓ Library built successfully${NC}"
echo "Library location: ${BUILD_DIR}/${LIBRARY_NAME}$(soext)"

# Install to Maven resources
echo ""
echo -e "${YELLOW}Installing to Maven resources...${NC}"
cmake --install . --prefix ../..

# Build and run tests if requested
if [ "$RUN_TESTS" = true ]; then
    echo ""
    echo -e "${YELLOW}Building test suite...${NC}"
    cd "$BUILD_DIR"

    # Configure tests
    cmake -DBUILD_TESTS=ON ..

    # Build tests
    if command -v ninja &> /dev/null; then
        ninja test
    else
        cmake --build . -j$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
    fi

    # Run tests
    echo ""
    echo -e "${YELLOW}Running tests...${NC}"
    if command -v ctest &> /dev/null; then
        ctest --output-on-failure
    else
        if [ -f "./bin/qlever_ffi_test" ]; then
            ./bin/qlever_ffi_test
        else
            echo -e "${YELLOW}Warning: Test executable not found${NC}"
        fi
    fi
fi

# Run benchmarks if requested
if [ "$RUN_BENCHMARKS" = true ]; then
    echo ""
    echo -e "${YELLOW}Building benchmarks...${NC}"
    cd "$BUILD_DIR"

    # Configure benchmarks
    cmake -DBUILD_BENCHMARKS=ON ..

    # Build benchmarks
    if command -v ninja &> /dev/null; then
        ninja benchmark
    else
        cmake --build . -j$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)
    fi

    # Run benchmarks
    echo ""
    echo -e "${YELLOW}Running benchmarks...${NC}"
    if [ -f "./bin/benchmark" ]; then
        ./bin/benchmark
    else
        echo -e "${YELLOW}Warning: Benchmark executable not found${NC}"
    fi
fi

# Generate jextract bindings if available
if [ "$CMAKE_ARGS" = *"jextract_FOUND=ON"* ]; then
    echo ""
    echo -e "${YELLOW}Generating Java bindings...${NC}"
    cd "$BUILD_DIR"
    cmake --build . --target jextract_bindings
    echo -e "${GREEN}✓ Java bindings generated in ../generated-sources${NC}"
fi

# Show build summary
echo ""
echo -e "${GREEN}=== Build Summary ===${NC}"
echo "Library: $LIBRARY_NAME"
echo "Build Type: $BUILD_TYPE"
echo "Platform: $OS $ARCH"
echo "QLever: $([ -d "../../../qlever" ] || [ -n "$QLEVER_DIR" ] && echo "Found" || echo "Stub build")"
echo ""

# Show next steps
echo -e "${YELLOW}Next Steps:${NC}"
echo "  1. Verify the library: file ${BUILD_DIR}/$(ls ${BUILD_DIR}/*qlever_ffi* 2>/dev/null | head -1)"
echo "  2. Run tests: ./build.sh --test"
echo "  3. Run benchmarks: ./build.sh --benchmark"
echo "  4. Check Maven resources: ../resources/native/"
echo "  5. View Java bindings: ../generated-sources/ (if jextract enabled)"
echo ""

# Show feature status
echo -e "${YELLOW}Feature Status:${NC}"
echo "  ✓ Cross-platform build"
echo "  ✓ Maven integration"
echo "  ✓ Comprehensive testing"
if [ "$CMAKE_ARGS" = *"jextract_FOUND=ON"* ]; then
    echo "  ✓ Java bindings"
else
    echo "  ⚠ Java bindings (requires jextract)"
fi
if [ -d "../../../qlever" ] || [ -n "$QLEVER_DIR" ]; then
    echo "  ✓ QLever integration"
else
    echo "  ⚠ QLever integration (stub build)"
fi
echo ""

# Help information
echo -e "${YELLOW}Usage examples:${NC}"
echo "  ./build.sh                          # Build with release configuration"
echo "  ./build.sh --test                   # Build and run tests"
echo "  ./build.sh --benchmark              # Build and run benchmarks"
echo "  ./build.sh --debug                  # Build with debug configuration"
echo "  ./build.sh --install                # Only install existing build"
echo "  export QLEVER_DIR=/path/to/qlever   # Manual QLever path"
echo ""

echo -e "${GREEN}Build completed successfully!${NC}"