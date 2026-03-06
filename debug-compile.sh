#!/usr/bin/env bash
set -euo pipefail

echo "=== Debug Compilation Issues ==="

# Clean and compile with verbose output
mvn clean compile -q -pl yawl-elements,yawl-engine -X 2>&1 | grep -E "(YIdentifierBag|remove.*method)" | head -10

echo ""
echo "=== Checking YIdentifierBag classes ==="
find . -name "YIdentifierBag.java" -type f | while read file; do
    echo "Found: $file"
    grep -n "public.*remove" "$file" | head -3
    echo ""
done