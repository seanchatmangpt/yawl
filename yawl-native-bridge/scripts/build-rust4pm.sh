#!/bin/bash
# Build script for Rust4pm NIF library
# Rust compilation of rust4pm.so

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RUST_DIR="$PROJECT_ROOT/src/main/rust"
BUILD_DIR="$PROJECT_ROOT/target/lib"

# Ensure directories exist
mkdir -p "$BUILD_DIR"

# Check for Rust toolchain
if ! command -v cargo &> /dev/null; then
    echo "Error: cargo is required but not installed" >&2
    exit 1
fi

# Generate C header
echo "Generating C header..."
cd "$RUST_DIR"
cargo install cbindgen || echo "cbindgen already installed"
cbindgen --config cbindgen.toml --crate rust4pm --output ../headers/rust4pm.h

# Build Rust NIF
echo "Building rust4pm NIF..."
cargo build --release

# Copy to target directory
cp "$RUST_DIR/target/release/librust4pm.so" "$BUILD_DIR/rust4pm.so"

echo "rust4pm.so built successfully in $BUILD_DIR"