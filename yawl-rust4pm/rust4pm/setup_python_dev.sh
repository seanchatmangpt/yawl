#!/bin/bash

# Development setup script for YAWL Process Mining Python bindings

set -e

echo "Setting up development environment for Python bindings..."

# Check Python version
python_version=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
echo "Python version: $python_version"

# Check if required Python packages are installed
echo "Checking Python dependencies..."
packages=("maturin" "pytest" "pyo3")

for package in "${packages[@]}"; do
    if ! python3 -c "import $package" &> /dev/null; then
        echo "Installing $package..."
        pip3 install "$package"
    else
        echo "✓ $package is already installed"
    fi
done

# Install additional development dependencies
echo "Installing development dependencies..."
pip3 install pytest-cov black ruff mypy

# Check Rust
echo "Checking Rust installation..."
if ! command -v cargo &> /dev/null; then
    echo "Rust is not installed. Please install Rust from https://rustup.rs/"
    exit 1
fi

# Build the Python package
echo "Building Python package..."
maturin develop

# Run basic tests
echo "Running basic tests..."
if python3 -c "import yawl_process_mining; print('✓ Python package imported successfully')" 2>/dev/null; then
    echo "✓ Basic test passed"
else
    echo "✗ Basic test failed"
    exit 1
fi

# Create a simple test file
echo "Creating test file..."
cat > test_import.py << 'EOF'
#!/usr/bin/env python3

import yawl_process_mining as ypm

# Test basic imports
print("✓ Successfully imported yawl_process_mining")

# Test submodule imports
import yawl_process_mining.xes as xes
import yawl_process_mining.discovery as discovery
import yawl_process_mining.conformance as conformance
print("✓ Successfully imported all submodules")

# Test function existence
assert hasattr(ypm, 'import_xes')
assert hasattr(ypm, 'discover_dfg')
assert hasattr(ypm, 'discover_alpha')
assert hasattr(ypm, 'check_conformance')
print("✓ All expected functions exist")

print("✓ All tests passed!")
EOF

python3 test_import.py
rm test_import.py

echo ""
echo "Development environment setup complete!"
echo ""
echo "Useful commands:"
echo "  maturin develop      - Build and install in development mode"
echo "  maturin build        - Build wheel package"
echo "  pytest tests/         - Run tests"
echo "  python examples/basic_usage.py  - Run example"
echo ""
echo "To run tests with coverage:"
echo "  pytest tests/ --cov=yawl_process_mining"
echo ""
echo "To format code:"
echo "  black ."
echo "  ruff check ."
echo ""