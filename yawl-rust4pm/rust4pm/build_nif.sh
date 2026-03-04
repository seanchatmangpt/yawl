#!/bin/bash

# Build script for YAWL Process Mining NIF
# This script builds the Rust NIF and copies it to the Erlang app

set -e

echo "Building YAWL Process Mining NIF..."

# Check if we're in the correct directory
if [ ! -f "Cargo.toml" ]; then
    echo "Error: Cargo.toml not found. Please run this script from the rust4pm directory."
    exit 1
fi

# Build the Rust library
echo "Building Rust library..."
cargo build --release --features nif

# Determine OS and create appropriate NIF name
OS="$(uname -s)"
case "$OS" in
    Linux)
        NIF_NAME="libyawl_process_mining.so"
        ;;
    Darwin)
        NIF_NAME="libyawl_process_mining.dylib"
        ;;
    *)
        echo "Unsupported OS: $OS"
        exit 1
        ;;
esac

# Path to Erlang app
ERLANG_APP_PATH="../../yawl-erlang-bridge/yawl-erlang-bridge"

# Check if Erlang app directory exists
if [ ! -d "$ERLANG_APP_PATH" ]; then
    echo "Error: Erlang app directory not found at $ERLANG_APP_PATH"
    exit 1
fi

# Create priv directory if it doesn't exist
mkdir -p "$ERLANG_APP_PATH/priv"

# Copy the NIF to the Erlang app
NIF_SOURCE="target/release/deps/libprocess_mining_bridge.dylib"
NIF_DEST="$ERLANG_APP_PATH/priv/yawl_process_mining.dylib"

if [ -f "$NIF_SOURCE" ]; then
    echo "Copying NIF to $NIF_DEST"
    cp "$NIF_SOURCE" "$NIF_DEST"
    echo "NIF built and copied successfully!"
else
    echo "Error: NIF not found at $NIF_SOURCE"
    exit 1
fi

# Print usage instructions
echo ""
echo "To use the NIF:"
echo "1. Start the Erlang application:"
echo "   cd $ERLANG_APP_PATH"
echo "   erl -pa ebin -pa deps/*/ebin"
echo "   application:start(process_mining_bridge)"
echo ""
echo "2. Test the NIF:"
echo "   process_mining_bridge:start_link()."
echo "   process_mining_bridge:import_xes(#{path => \"/path/to/log.xes\"})."