#!/bin/bash
#
# Verify No Stubs in MCP Implementation
#
# This script scans the MCP source code for stub patterns that would
# indicate fake/incomplete implementations. It FAILS if any are found.
#
# Stub patterns detected:
#   - UnsupportedOperationException
#   - TODO/FIXME comments
#   - mock/stub/fake keywords
#   - Empty method bodies (return null without logic)
#   - "not implemented" / "placeholder" messages
#
# Usage: ./verify-no-stubs.sh
#
# Exit codes:
#   0 - No stubs found
#   1 - Stubs detected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MCP_SRC="$PROJECT_DIR/src/org/yawlfoundation/yawl/integration/mcp"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "==========================================="
echo "MCP Stub Verification"
echo "==========================================="
echo "Source: $MCP_SRC"
echo "==========================================="

STUBS_FOUND=0

# Patterns that indicate stubs
STUB_PATTERNS=(
    "UnsupportedOperationException"
    "throw new UnsupportedOperationException"
    "// TODO"
    "// FIXME"
    "/* TODO"
    "/* FIXME"
    "not implemented"
    "Not implemented"
    "NOT IMPLEMENTED"
    "placeholder"
    "Placeholder"
    "PLACEHOLDER"
)

echo ""
echo "Scanning for stub patterns..."
echo ""

# Check each pattern
for pattern in "${STUB_PATTERNS[@]}"; do
    echo "Checking: '$pattern'"

    # grep returns 0 if found, 1 if not found, 2 on error
    set +e
    matches=$(grep -r -n -i --include="*.java" "$pattern" "$MCP_SRC" 2>/dev/null || true)
    set -e

    if [ -n "$matches" ]; then
        echo -e "${RED}FOUND: '$pattern'${NC}"
        echo "$matches" | head -20
        echo ""
        STUBS_FOUND=$((STUBS_FOUND + 1))
    fi
done

# Additional check: Methods that just return null or empty without logic
echo ""
echo "Checking for empty method bodies..."

set +e
empty_returns=$(grep -r -n -B5 --include="*.java" "return null;" "$MCP_SRC" 2>/dev/null | grep -v "if\|else\|for\|while\|try\|catch" | head -20 || true)
set -e

if [ -n "$empty_returns" ]; then
    echo -e "${YELLOW}WARNING: Methods with 'return null;' (may be valid, review manually):${NC}"
    echo "$empty_returns"
    echo ""
fi

# Check for mock/stub keywords (excluding comments about testing)
echo ""
echo "Checking for mock/stub keywords..."

set +e
mock_patterns=$(grep -r -n -i --include="*.java" -E "(mock|stub|fake|dummy)" "$MCP_SRC" 2>/dev/null | grep -v "// " | grep -v "/\*" | grep -v "test" | grep -v "Test" | head -20 || true)
set -e

if [ -n "$mock_patterns" ]; then
    echo -e "${RED}FOUND: mock/stub/fake keywords (outside test context):${NC}"
    echo "$mock_patterns"
    echo ""
    STUBS_FOUND=$((STUBS_FOUND + 1))
fi

# Summary
echo ""
echo "==========================================="
echo "Verification Summary"
echo "==========================================="

if [ $STUBS_FOUND -gt 0 ]; then
    echo -e "${RED}STUBS DETECTED: $STUBS_FOUND${NC}"
    echo ""
    echo "The following issues must be fixed before deployment:"
    echo "  1. Remove all UnsupportedOperationException throws"
    echo "  2. Implement all TODO/FIXME items"
    echo "  3. Replace any mock/stub implementations with real code"
    echo ""
    echo "VERIFICATION FAILED"
    exit 1
else
    echo -e "${GREEN}NO STUBS FOUND${NC}"
    echo ""
    echo "All MCP implementations appear to be real (non-stub)"
    echo ""
    echo "VERIFICATION PASSED"
    exit 0
fi
