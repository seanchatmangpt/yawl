#!/bin/bash

echo "=== Validating ScopedValue YEngine Implementation ==="

echo "1. Compiling the project..."
mvn clean compile -q

if [ $? -eq 0 ]; then
    echo "✓ Compilation successful"
else
    echo "✗ Compilation failed"
    exit 1
fi

echo ""
echo "2. Running ScopedEngineContextTest..."
mvn test -Dtest=ScopedEngineContextTest -q

if [ $? -eq 0 ]; then
    echo "✓ ScopedEngineContextTest passed"
else
    echo "✗ ScopedEngineContextTest failed"
    exit 1
fi

echo ""
echo "3. Running ScopedValueYEngineTest..."
mvn test -Dtest=ScopedValueYEngineTest -q

if [ $? -eq 0 ]; then
    echo "✓ ScopedValueYEngineTest passed"
else
    echo "✗ ScopedValueYEngineTest failed"
    exit 1
fi

echo ""
echo "4. Running ScopedValueMemoryLeakTest..."
mvn test -Dtest=ScopedValueMemoryLeakTest -q

if [ $? -eq 0 ]; then
    echo "✓ ScopedValueMemoryLeakTest passed"
else
    echo "✗ ScopedValueMemoryLeakTest failed"
    exit 1
fi

echo ""
echo "5. Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | grep -oP 'version "([^"]+)' | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge 25 ]; then
    echo "✓ Java version compatible (Java $JAVA_VERSION)"
else
    echo "✗ Java version incompatible (Java $JAVA_VERSION, requires 25)"
    exit 1
fi

echo ""
echo "=== All validations passed! ==="