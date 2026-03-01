#!/bin/bash

# Script to run QLever smoke tests for CI/CD
# Usage: ./scripts/run-smoke-tests.sh

set -e

echo "============================================="
echo "Running QLever Smoke Tests"
echo "============================================="

# Navigate to project directory if needed
cd "$(dirname "$0")/.."

# Ensure Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Run smoke tests
echo "Running smoke tests..."
mvn test -Dtest=QLeverSmokeTest -Dmaven.test.failure.ignore=true

# Check test results
EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "============================================="
    echo "✅ All smoke tests passed!"
    echo "============================================="
    exit 0
else
    echo "============================================="
    echo "❌ Smoke tests failed!"
    echo "============================================="
    exit $EXIT_CODE
fi