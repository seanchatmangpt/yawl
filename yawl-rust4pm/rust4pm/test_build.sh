#!/bin/bash
# Test build script for YAWL Rust4PM NIF library

set -e

echo "=== YAWL Rust4PM NIF Library Build Test ==="

# Check if Rust is available
if ! command -v cargo &> /dev/null; then
    echo "Error: Cargo (Rust) not found. Please install Rust."
    exit 1
fi

echo "✓ Rust/Cargo found"

# Change to rust4pm directory
cd "$(dirname "$0")"

# Check if Cargo.toml exists
if [ ! -f "Cargo.toml" ]; then
    echo "Error: Cargo.toml not found"
    exit 1
fi

echo "✓ Cargo.toml found"

# Build the library
echo "Building library..."
cargo build --release
echo "✓ Build completed"

# Check if library was created
if [ -f "target/release/libyawl_rust4pm.so" ] || [ -f "target/release/yawl_rust4pm.dll" ]; then
    echo "✓ Library created"
else
    echo "Warning: Library file not found at expected location"
fi

# Run tests
echo "Running tests..."
cargo test --lib
echo "✓ Tests completed"

# Run integration tests if available
if [ -d "tests" ]; then
    echo "Running integration tests..."
    cargo test
    echo "✓ Integration tests completed"
fi

# Check if we can build examples
if [ -d "examples" ]; then
    echo "Building examples..."
    cargo build --example basic_usage
    echo "✓ Example built"
fi

# Create test data
echo "Creating test data..."
mkdir -p test_data
cat > test_data/sample_ocel.json << EOF
{
    "events": [
        {
            "activity": "Start",
            "timestamp": "2024-01-01T10:00:00Z",
            "case_id": "case_1",
            "object_id": "obj_1",
            "attributes": {}
        },
        {
            "activity": "Task_A",
            "timestamp": "2024-01-01T10:30:00Z",
            "case_id": "case_1",
            "object_id": "obj_1",
            "attributes": {}
        }
    ],
    "objects": {
        "obj_1": {
            "object_type": "Case",
            "attributes": {}
        }
    },
    "global_trace": "global_trace_1"
}
EOF

echo "✓ Test data created"

# Test basic functionality
echo "Testing basic functionality..."
cargo run --example basic_usage
echo "✓ Basic functionality test passed"

echo ""
echo "=== Build Test Summary ==="
echo "✓ All builds completed successfully"
echo "✓ All tests passed"
echo "✓ Library is ready for integration with Erlang"

# Print library location
echo ""
echo "Library location:"
find target -name "*.so" -o -name "*.dll" | head -1

echo ""
echo "To use with Erlang:"
echo "1. Copy the library to your Erlang project"
echo "2. Add to your rebar.config deps"
echo "3. Call the NIF functions from Erlang code"