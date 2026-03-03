#!/bin/bash
# Build script for QLever FFI library
# C++ compilation of libqlever_ffi.so

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
HEADERS_DIR="$PROJECT_ROOT/headers"
CPP_DIR="$PROJECT_ROOT/src/main/cpp"
BUILD_DIR="$PROJECT_DIR/target"
LIB_DIR="$BUILD_DIR/lib"

# Ensure directories exist
mkdir -p "$LIB_DIR"

# Check for required tools
if ! command -v g++ &> /dev/null; then
    echo "Error: g++ is required but not installed" >&2
    exit 1
fi

# Compile QLever FFI library
echo "Building libqlever_ffi.so..."

g++ -shared -fPIC \
    -I"${GRAALVM_HOME:-$JAVA_HOME}/include" \
    -I"${GRAALVM_HOME:-$JAVA_HOME}/include/linux" \
    -I"$HEADERS_DIR" \
    -std=c++17 \
    -O3 \
    -DNDEBUG \
    -o "$LIB_DIR/libqlever_ffi.so" \
    "$CPP_DIR/qlever_ffi.cpp" \
    -lqlengine -lqlindex -lstdc++fs

echo "libqlever_ffi.so built successfully in $LIB_DIR"