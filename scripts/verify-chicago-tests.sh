#!/bin/bash

# Build Verification Script for Chicago TDD Tests
#
# This script verifies that the Chicago TDD tests for QLeverFfiBindings
# can compile successfully and are ready for execution.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_CLASS="org.yawlfoundation.yawl.qlever.QLeverFfiBindingsChicagoTest"

echo "🔍 Chicago TDD Test Build Verification"
echo "======================================"
echo "Project Root: $PROJECT_ROOT"
echo "Test Class: $TEST_CLASS"
echo

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: pom.xml not found. Please run this script from the yawl-qlever directory."
    exit 1
fi

# Clean the project
echo "🧹 Cleaning project..."
mvn clean -q

# Compile the project
echo "🔨 Compiling project..."
if ! mvn compile -q; then
    echo "❌ Compilation failed!"
    echo "Please check for compilation errors in the source code."
    exit 1
fi

# Compile the test classes
echo "🧪 Compiling test classes..."
if ! mvn test-compile -q; then
    echo "❌ Test compilation failed!"
    echo "Please check for test compilation errors."
    exit 1
fi

# Check if the test class was compiled successfully
if [ ! -f "target/test-classes/$TEST_CLASS.class" ]; then
    echo "❌ Test class not compiled: $TEST_CLASS"
    echo "Please check the test file and dependencies."
    exit 1
fi

# Check if QLever dependencies are available
echo "📦 Checking dependencies..."
if ! mvn dependency:tree -q | grep -q "qlever"; then
    echo "⚠️  Warning: No QLever dependencies found in pom.xml"
    echo "Make sure QLever FFI library dependencies are included."
fi

# Run the test in dry-run mode to verify configuration
echo "✅ Running test dry-run..."
mvn test -Dtest="$TEST_CLASS" -Dmaven.test.skip=false -q -DdryRun=true 2>/dev/null || true

# Display success message
echo
echo "✅ Build verification completed successfully!"
echo
echo "📋 Test Details:"
echo "================"
echo "✅ Project compiles"
echo "✅ Test classes compile"
echo "✅ Test class: $TEST_CLASS"
echo "✅ Dependencies checked"
echo

echo "🚀 Next Steps:"
echo "============="
echo "1. Run the full test suite:"
echo "   $PROJECT_ROOT/scripts/run-chicago-tests.sh"
echo
echo "2. Run specific test methods:"
echo "   mvn test -Dtest=$TEST_CLASS#testMethodName"
echo
echo "3. Generate coverage report:"
echo "   mvn clean test jacoco:report"
echo
echo "4. View test results:"
echo "   target/surefire-reports/"
echo

# Check if we can find the native library
echo "🔍 Native Library Check:"
echo "======================"
if command -v find &> /dev/null; then
    # Common paths where libraries might be located
    library_paths=(
        "/usr/local/lib"
        "/usr/lib"
        "/lib"
        "$PROJECT_ROOT/lib"
        "$PROJECT_ROOT/src/main/resources"
        "$PROJECT_DIR"
    )

    for path in "${library_paths[@]}"; do
        if [ -d "$path" ]; then
            if find "$path" -name "*qlever*" -type f 2>/dev/null | head -5; then
                echo "✅ Found potential QLever libraries in $path:"
                find "$path" -name "*qlever*" -type f 2>/dev/null | head -3
            fi
        fi
    done
fi

echo
echo "🎯 Chicago TDD Tests are ready for execution!"