#!/bin/bash

# Build script for YAWL Process Mining Bridge
# This script compiles all Erlang modules and creates the beam files

set -e

echo "Building YAWL Process Mining Bridge..."

# Check we're in the right directory
if [ ! -f "src/process_mining_bridge.erl" ]; then
    echo "Error: process_mining_bridge.erl not found in src/"
    exit 1
fi

# Create ebin directory if it doesn't exist
mkdir -p ebin

# Compile all Erlang modules
echo "Compiling Erlang modules..."
erl -make

if [ $? -eq 0 ]; then
    echo "Build successful!"

    # List generated beam files
    echo "Generated beam files:"
    ls -la ebin/*.beam

    # Check for specific modules
    if [ -f "ebin/process_mining_bridge.beam" ]; then
        echo "✓ process_mining_bridge.beam generated"
    else
        echo "✗ process_mining_bridge.beam NOT generated"
        exit 1
    fi

    if [ -f "ebin/process_mining_bridge_sup.beam" ]; then
        echo "✓ process_mining_bridge_sup.beam generated"
    else
        echo "✗ process_mining_bridge_sup.beam NOT generated"
        exit 1
    fi

    if [ -f "ebin/mnesia_registry.beam" ]; then
        echo "✓ mnesia_registry.beam generated"
    else
        echo "✗ mnesia_registry.beam NOT generated"
        exit 1
    fi
else
    echo "Build failed!"
    exit 1
fi