#!/bin/bash
set -euo pipefail

# Verification script for Build Performance Tracking System

echo "========================================"
echo "  Build Timer System Verification"
echo "========================================"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "1. Checking required files..."
files=(
    "build-timer.sh"
    "analyze-build-performance.sh"
    "BUILD_PERFORMANCE.md"
    "BUILD_TIMER_README.md"
)

for file in "${files[@]}"; do
    if [[ -f "$SCRIPT_DIR/$file" ]]; then
        echo "   ✓ $file"
    else
        echo "   ✗ $file MISSING"
        exit 1
    fi
done
echo ""

echo "2. Checking executability..."
scripts=(
    "build-timer.sh"
    "analyze-build-performance.sh"
)

for script in "${scripts[@]}"; do
    if [[ -x "$SCRIPT_DIR/$script" ]]; then
        echo "   ✓ $script is executable"
    else
        echo "   ✗ $script is NOT executable"
        exit 1
    fi
done
echo ""

echo "3. Checking dependencies..."
deps=("bc" "jq" "mvn")
for dep in "${deps[@]}"; do
    if command -v "$dep" &>/dev/null; then
        echo "   ✓ $dep available"
    else
        echo "   ⚠ $dep not available (required for full functionality)"
    fi
done
echo ""

echo "4. Testing build-timer.sh help..."
help_output=$("$SCRIPT_DIR/build-timer.sh" 2>&1 || true)
if echo "$help_output" | grep -qE "(Usage:|maven-goals)"; then
    echo "   ✓ Help message works"
else
    echo "   ✗ Help message failed"
    exit 1
fi
echo ""

echo "5. Testing analyze-build-performance.sh..."
if "$SCRIPT_DIR/analyze-build-performance.sh" help 2>&1 | grep -q "Commands:"; then
    echo "   ✓ Analyzer help works"
else
    echo "   ✗ Analyzer help failed"
    exit 1
fi
echo ""

echo "6. Checking performance data format..."
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
if [[ -f "$PROJECT_ROOT/build-performance.json" ]]; then
    if jq empty "$PROJECT_ROOT/build-performance.json" 2>/dev/null; then
        echo "   ✓ build-performance.json is valid JSON"
        record_count=$(jq '. | length' "$PROJECT_ROOT/build-performance.json")
        echo "   ✓ Contains $record_count build records"
    else
        echo "   ✗ build-performance.json is invalid JSON"
        exit 1
    fi
else
    echo "   ⚠ build-performance.json not yet generated (run build-timer.sh first)"
fi
echo ""

echo "========================================"
echo "  ✓ All Checks Passed"
echo "========================================"
echo ""
echo "System is ready to use!"
echo ""
echo "Quick Start:"
echo "  ./.claude/build-timer.sh compile"
echo "  ./.claude/analyze-build-performance.sh all"
echo ""
echo "Documentation:"
echo "  ./.claude/BUILD_TIMER_README.md"
echo ""
