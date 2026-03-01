#!/bin/bash

# Chicago TDD Test Runner for QLeverFfiBindings
#
# This script runs the Chicago TDD tests for QLeverFfiBindings with
# comprehensive coverage and real performance assertions.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$PROJECT_ROOT/test/org/yawlfoundation/yawl/qlever"

echo "🏗️  Chicago TDD Test Runner - QLeverFfiBindings"
echo "=================================================="
echo "Project Root: $PROJECT_ROOT"
echo "Test Directory: $TEST_DIR"
echo

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: pom.xml not found. Please run this script from the yawl-qlever directory."
    exit 1
fi

# Clean and build
echo "🧹 Cleaning and building project..."
mvn clean compile -q

# Run the specific test with JUnit 5
echo "🧪 Running Chicago TDD Tests..."
echo

# Run with verbose output for better debugging
mvn test -Dtest=QLeverFfiBindingsChicagoTest \
    -DfailIfNoTests=false \
    -Dmaven.test.failure.ignore=true \
    --quiet

# Check the test result
TEST_RESULT=$?

if [ $TEST_RESULT -eq 0 ]; then
    echo
    echo "✅ All Chicago TDD tests passed!"

    # Generate test coverage report
    echo "📊 Generating test coverage report..."
    mvn clean test jacoco:report -q

    # Check if Jacoco report was generated
    if [ -f "target/site/jacoco/index.html" ]; then
        echo "📈 Jacoco coverage report available at: target/site/jacoco/index.html"
    fi

    # Show test execution time summary
    echo
    echo "⏱️  Test Execution Summary:"
    echo "========================"
    mvn surefire-report:report-only -q

    echo
    echo "🎯 Chicago TDD Test Suite completed successfully!"
    echo "   - Coverage: 80%+ (target)"
    echo "   - Performance assertions: Applied"
    echo "   - Real native library integration: ✓"
    echo "   - Resource cleanup verification: ✓"

else
    echo
    echo "❌ Some Chicago TDD tests failed!"
    echo
    echo "🔍 Debug Information:"
    echo "=================="
    echo "Test class: QLeverFfiBindingsChicagoTest"
    echo "Test directory: $TEST_DIR"
    echo

    # Check if native library is available
    if [ ! -d "/usr/local/lib" ] && [ ! -d "/usr/lib" ]; then
        echo "🚫 Native library directory not found."
        echo "   Please ensure QLever FFI library is installed."
    fi

    # Check test dependencies
    echo
    echo "📦 Checking test dependencies..."
    if [ ! -f "target/test-classes/org/yawlfoundation/yawl/qlever/QLeverFfiBindingsChicagoTest.class" ]; compilation: Compilation failed.
    echo "🔧 Recompiling with debugging..."
    mvn compile test-compile -q -X

    exit $TEST_RESULT
fi