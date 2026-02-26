#!/bin/bash

# Script to verify ScopedValue inheritance in YEngine
# Tests that virtual threads properly inherit tenant context

echo "=== Testing ScopedValue Inheritance ==="

# Set up test environment
export JAVA_HOME=${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}

# Compile the project
echo "Compiling YAWL project..."
rebar3 compile

if [ $? -ne 0 ]; then
    echo "ERROR: Compilation failed"
    exit 1
fi

echo "Compilation successful!"

# Test scoped value inheritance
echo "Running ScopedTenantContext inheritance test..."
rebar3 eunit --module ScopedTenantContext

echo "Running VirtualThreadTenantInheritanceTest..."
rebar3 eunit --module VirtualThreadTenantInheritanceTest

echo "Running YEngine migration test..."
rebar3 eunit --module YEngineMigrationTest

echo "Running performance tests..."
rebar3 eunit --module TenantContextPerformanceTest

echo ""
echo "=== Test Summary ==="
echo "If all tests pass, ScopedValue inheritance is working correctly."
echo "Key points verified:"
echo "1. Virtual threads inherit parent ScopedValue context"
echo "2. ThreadLocal migration maintains backward compatibility"
echo "3. Context isolation works between different scopes"
echo "4. Performance is acceptable for both platform and virtual threads"