#!/usr/bin/env bash
# Quick test of emit_tests function
set -uo pipefail

REPO_ROOT="/Users/sac/cre/vendors/yawl"
FACTS_DIR="/tmp/test-facts"
mkdir -p "$FACTS_DIR"

# Test the fixed find command
echo "Testing find command with parentheses..."
shared_test_dir="$REPO_ROOT/test"
count=$(find "$shared_test_dir" \( -name "*Test.java" -o -name "*Tests.java" -o -name "*IT.java" \) -type f 2>/dev/null | wc -l | tr -d ' ')
echo "Found $count test files with fixed find command"

# Show a few sample files
echo ""
echo "Sample test files found:"
find "$shared_test_dir" \( -name "*Test.java" -o -name "*Tests.java" -o -name "*IT.java" \) -type f 2>/dev/null | head -10

# Test old command (without parentheses)
echo ""
echo "Testing old find command without parentheses..."
old_count=$(find "$shared_test_dir" -name "*Test.java" -o -name "*Tests.java" -o -name "*IT.java" 2>/dev/null | wc -l | tr -d ' ')
echo "Found $old_count test files with old find command"

echo ""
echo "Difference: $(( count - old_count )) additional files found with fix"
