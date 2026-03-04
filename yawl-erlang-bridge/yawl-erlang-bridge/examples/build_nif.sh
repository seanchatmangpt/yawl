#!/bin/bash
# Script to build the Rust NIF library for YAWL Process Mining

set -e

echo "=== Building Rust NIF Library ==="
echo

# Navigate to rust4pm directory
cd ../../yawl-rust/rust4pm

# Check if we're in the correct directory
if [ ! -f "Cargo.toml" ] || [ ! -d "src" ]; then
    echo "✗ Error: Not in rust4pm directory"
    echo "Current directory: $(pwd)"
    exit 1
fi

# Build the library in release mode
echo "🔧 Building Rust NIF in release mode..."
cargo build --release

# Check if build succeeded
if [ $? -eq 0 ]; then
    echo "✓ Rust NIF built successfully"

    # Check which file actually exists (different platforms)
    SO_FILE=""
    if [ -f "target/release/libyawl_process_mining.so" ]; then
        SO_FILE="target/release/libyawl_process_mining.so"
        echo "📁 Found: $SO_FILE"
    elif [ -f "target/release/yawl_process_mining.dll" ]; then
        SO_FILE="target/release/yawl_process_mining.dll"
        echo "📁 Found: $SO_FILE (Windows)"
    elif [ -f "target/release/libyawl_process_mining.dylib" ]; then
        SO_FILE="target/release/libyawl_process_mining.dylib"
        echo "📁 Found: $SO_FILE (macOS)"
    else
        echo "✗ NIF library not found in target/release/"
        echo "Available files:"
        ls -la target/release/ 2>/dev/null || echo "No files found"
        exit 1
    fi

    # Copy the library to the Erlang priv directory
    TARGET_DIR="../yawl-erlang-bridge/priv/"

    if [ ! -d "$TARGET_DIR" ]; then
        echo "📁 Creating directory: $TARGET_DIR"
        mkdir -p "$TARGET_DIR"
    fi

    if cp "$SO_FILE" "$TARGET_DIR/"; then
        echo "✓ NIF library copied to $TARGET_DIR"

        # Verify the copy
        if [ -f "$TARGET_DIR/$(basename $SO_FILE)" ]; then
            echo "✓ Verification: Library is available at $TARGET_DIR/$(basename $SO_FILE)"
        else
            echo "⚠️  Warning: Library not found after copy"
        fi
    else
        echo "✗ Failed to copy NIF library to $TARGET_DIR"
        exit 1
    fi
else
    echo "✗ Rust NIF build failed"
    echo "Check Rust and dependencies installation:"
    echo "  • rustc --version"
    echo "  • cargo --version"
    echo "  • cargo install cargo-tree  # For dependency debugging"
    exit 1
fi

echo
echo "🎉 Build complete! You can now run the examples:"
echo "   cd yawl-erlang-bridge/yawl-erlang-bridge/examples"
echo "   make test"