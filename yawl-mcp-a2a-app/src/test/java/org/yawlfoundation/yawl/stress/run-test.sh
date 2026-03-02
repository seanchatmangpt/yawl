#!/bin/bash

# Script to run the virtual thread stress test
# This script attempts to compile and run the test directly

echo "=== Virtual Thread Stress Test Runner ==="
echo "JVM Version: $(java -version 2>&1 | head -n 1)"
echo "OS: $(uname -a)"
echo "Cores: $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 'unknown')"
echo ""

# Navigate to the test directory
cd "$(dirname "$0")"

# Check if Java is available
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

# Try to find the classpath for YAWL dependencies
CLASSPATH="."
if [ -d "../../target/classes" ]; then
    CLASSPATH="$CLASSPATH:../../target/classes"
fi
if [ -d "../../target/test-classes" ]; then
    CLASSPATH="$CLASSPATH:../../target/test-classes"
fi

# Add any JAR files in the lib directory
if [ -d "../../lib" ]; then
    for jar in ../../lib/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:$jar"
        fi
    done
fi

echo "Classpath: $CLASSPATH"
echo ""

# Compile the test
echo "Compiling test..."
if javac -cp "$CLASSPATH" SimpleVirtualThreadTest.java; then
    echo "✓ Compilation successful"
    echo ""

    # Run the test
    echo "Running test..."
    java -cp "$CLASSPATH" SimpleVirtualThreadTest

    # Cleanup
    echo ""
    echo "Cleaning up..."
    rm -f SimpleVirtualThreadTest.class
else
    echo "✗ Compilation failed"
    exit 1
fi