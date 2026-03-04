#!/bin/bash

# Build script for testing Erlang NIF integration

echo "Building Erlang bridge..."

# Clean previous builds
rm -rf ebin/*.beam
rm -rf ebin/*.app

# Compile all Erlang files
erlc -o ebin +debug_info src/*.erl

if [ $? -eq 0 ]; then
    echo "Compilation successful"
else
    echo "Compilation failed"
    exit 1
fi

# Copy app file
cp src/process_mining_bridge.app ebin/

# Check if NIF library exists
if [ -f "priv/yawl_process_mining.so" ]; then
    echo "NIF library found: priv/yawl_process_mining.so"
    file priv/yawl_process_mining.so
else
    echo "NIF library not found: priv/yawl_process_mining.so"
    echo "Note: Fallback implementations will be used"
fi

# Run the integration test
echo "Running integration test..."
erl -pa ebin -eval "test_integration:test()." -noshell

echo "Build and test completed"