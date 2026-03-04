#!/bin/bash

# Build script for testing only NIF functionality

echo "Building Erlang bridge (NIF only)..."

# Clean previous builds
rm -rf ebin/*.beam
rm -rf ebin/*.app

# Compile only process_mining_bridge (skip problematic data_modelling_bridge)
erlc -o ebin +debug_info src/process_mining_bridge.erl

if [ $? -eq 0 ]; then
    echo "process_mining_bridge compiled successfully"
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

echo "NIF-only build and test completed"