#!/bin/bash
# 80/20 Innovation #3: Smart Build - Auto-detects what to do
# Analyzes project state and runs minimal necessary commands

set -euo pipefail

echo "🔍 Analyzing project state..."

# Check if classes directory exists
if [ ! -d "classes" ] || [ -z "$(ls -A classes 2>/dev/null)" ]; then
    echo "📦 No compiled classes found"
    NEED_COMPILE=true
else
    echo "✅ Compiled classes exist"
    NEED_COMPILE=false
fi

# Check if sources are newer than classes
if [ "$NEED_COMPILE" = false ]; then
    NEWEST_SOURCE=$(find src -name "*.java" -type f -printf '%T@\n' 2>/dev/null | sort -n | tail -1)
    NEWEST_CLASS=$(find classes -name "*.class" -type f -printf '%T@\n' 2>/dev/null | sort -n | tail -1)

    if [ -n "$NEWEST_SOURCE" ] && [ -n "$NEWEST_CLASS" ]; then
        if (( $(echo "$NEWEST_SOURCE > $NEWEST_CLASS" | bc -l) )); then
            echo "🔄 Source files newer than classes"
            NEED_COMPILE=true
        fi
    fi
fi

# Smart execution
if [ "$NEED_COMPILE" = true ]; then
    echo ""
    echo "🔨 Compiling sources..."
    ant -f build/build.xml compile | grep -E "(BUILD|Compiling|error:)" || true
    echo ""
fi

echo "🧪 Running tests..."
ant -f build/build.xml unitTest 2>&1 | grep -E "(BUILD|Tests run|junit)" || true

echo ""
echo "✅ Smart build complete!"
