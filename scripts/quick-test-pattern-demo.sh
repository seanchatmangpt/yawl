#!/usr/bin/env bash
#
# Quick test for the pattern demo script
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Testing pattern demo scripts..."
echo

# Test 1: Check if scripts exist
if [[ ! -f "$SCRIPT_DIR/run-vdaalst-demo.sh" ]]; then
    echo "❌ run-vdaalst-demo.sh not found"
    exit 1
fi

if [[ ! -f "$SCRIPT_DIR/pattern-demo-wrapper.sh" ]]; then
    echo "❌ pattern-demo-wrapper.sh not found"
    exit 1
fi

echo "✅ Both scripts found"

# Test 2: Check if they're executable
if [[ ! -x "$SCRIPT_DIR/run-vdaalst-demo.sh" ]]; then
    echo "❌ run-vdaalst-demo.sh is not executable"
    exit 1
fi

if [[ ! -x "$SCRIPT_DIR/pattern-demo-wrapper.sh" ]]; then
    echo "❌ pattern-demo-wrapper.sh is not executable"
    exit 1
fi

echo "✅ Both scripts are executable"

# Test 3: Check help command
echo
echo "Testing help command..."
if "$SCRIPT_DIR/run-vdaalst-demo.sh" --help > /dev/null 2>&1; then
    echo "✅ Help command works"
else
    echo "❌ Help command failed"
    exit 1
fi

# Test 4: Check wrapper help
if "$SCRIPT_DIR/pattern-demo-wrapper.sh" --help > /dev/null 2>&1; then
    echo "✅ Wrapper help command works"
else
    echo "❌ Wrapper help command failed"
    exit 1
fi

echo
echo "✅ All tests passed!"
echo
echo "Usage examples:"
echo "  $SCRIPT_DIR/run-vdaalst-demo.sh --pattern WCP-1"
echo "  $SCRIPT_DIR/run-vdaalst-demo.sh --category BASIC"
echo "  $SCRIPT_DIR/run-vdaalst-demo.sh --all --format json"
echo
echo "For more information, see:"
echo "  $SCRIPT_DIR/README-pattern-demo.md"
