#!/usr/bin/env bash

# Test script to achieve 80%+ coverage for QLever modules

echo "=== QLever Coverage Test Script ==="
echo "===================================="

set -euo pipefail

# Show help function
show_help() {
    cat << EOF
Usage: $0 [--help]

Test script to achieve 80%+ coverage for QLever modules.

Options:
  --help    Show this help message and exit

Examples:
  $0          # Run QLever coverage tests
EOF
}

# Parse command line arguments
if [[ "${1:-}" == "--help" ]]; then
    show_help
    exit 0
fi

# Compile the module
echo "1. Compiling QLever module..."
mvn clean compile -pl yawl-qlever

if [[ $? -eq 0 ]]; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed"
    exit 1
fi

# Run tests with coverage
echo ""
echo "2. Running tests with JaCoCo..."
mvn test -pl yawl-qlever -P analysis

if [[ $? -eq 0 ]]; then
    echo "✓ Tests passed"
else
    echo "✗ Tests failed"
    exit 1
fi

# Generate coverage report
echo ""
echo "3. Generating coverage report..."
mvn jacoco:report -pl yawl-qlever

# Check coverage
echo ""
echo "4. Checking coverage thresholds..."
mvn jacoco:check -pl yawl-qlever

if [[ $? -eq 0 ]]; then
    echo "✓ Coverage thresholds met (80%+)"
else
    echo "✗ Coverage thresholds not met"
    echo "Check report at: yawl-qlever/target/site/jacoco/index.html"
    exit 1
fi

echo ""
echo "=== Coverage Test Complete ==="
echo "Report available at: yawl-qlever/target/site/jacoco/index.html"