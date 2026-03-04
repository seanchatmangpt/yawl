#!/bin/bash
set -euo pipefail

# Build script for yawl_ml_bridge native library

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building yawl_ml_bridge native library..."

# Check if we're on macOS (for dylib) or Linux (for so)
if [[ "$(uname -s)" == "Darwin" ]]; then
    LIB_EXT="dylib"
    TARGET_LIB="libyawl_ml_bridge.$LIB_EXT"
elif [[ "$(uname -s)" == "Linux" ]]; then
    LIB_EXT="so"
    TARGET_LIB="libyawl_ml_bridge.$LIB_EXT"
else
    echo "Unsupported platform: $(uname -s)"
    exit 1
fi

# Build the library
echo "Running cargo build..."
cargo build --release

# Copy to parent directory for Erlang to find
echo "Copying library to parent directory..."
cp "target/release/$TARGET_LIB" ../priv/

echo "Build complete. Library available at: ../priv/$TARGET_LIB"

# Verify the library
echo "Verifying library..."
if [[ -f "../priv/$TARGET_LIB" ]]; then
    file "../priv/$TARGET_LIB"
    ls -la "../priv/$TARGET_LIB"
else
    echo "ERROR: Library not found at ../priv/$TARGET_LIB"
    exit 1
fi
