#!/bin/bash

# Build script for YAWL Process Mining Python bindings

set -e

echo "Building YAWL Process Mining Python bindings..."

# Check if maturin is installed
if ! command -v maturin &> /dev/null; then
    echo "Installing maturin..."
    pip install maturin
fi

# Check if Python is available
if ! command -v python3 &> /dev/null; then
    echo "Python3 is required but not installed."
    exit 1
fi

# Build the Python package
echo "Building Python package..."
maturin build --release

echo "Build completed successfully!"
echo ""
echo "To install the package locally, run:"
echo "  maturin develop"
echo ""
echo "To install from the built wheel, run:"
echo "  pip install target/wheels/yawl_process_mining-*.whl"
echo ""
echo "To run tests:"
echo "  pytest tests/test_python_bindings.py"
echo ""
echo "To run the example:"
echo "  python examples/basic_usage.py"