#!/bin/bash
# YAWL Code Quality Validator - ZERO TOLERANCE FOR MOCKS/STUBS/TODOs
# This script enforces Fortune 5 production quality standards
#
# Exit codes:
#   0 = Pass (no violations)
#   1 = Fail (violations found)
#   2 = Block (critical violations, show to user)

set -e

# Startup message for debugging
echo "[validate-no-mocks.sh] Starting Fortune 5 quality validation..." >&2

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "YAWL Code Quality Validator"
echo "Fortune 5 Production Standards"
echo "=========================================="
echo ""

VIOLATIONS=0

# Check for mock/stub patterns in Java source
echo "Checking for FORBIDDEN patterns..."
echo ""

# Check for TODO comments
echo "Checking for TODO/FIXME/XXX/HACK comments..."
TODO_COUNT=$(grep -rn --include="*.java" -E "(//\s*(TODO|FIXME|XXX|HACK)|/\*\s*(TODO|FIXME|XXX|HACK))" src/ 2>/dev/null | wc -l || echo "0")
if [ "$TODO_COUNT" -gt 0 ]; then
    echo -e "${RED}VIOLATION: Found $TODO_COUNT TODO/FIXME/XXX/HACK comments${NC}" >&2
    echo "   Forbidden: TODO comments" >&2
    echo "   Required: Either implement or throw UnsupportedOperationException" >&2
    echo "" >&2
    echo "   Locations:" >&2
    grep -rn --include="*.java" -E "(//\s*(TODO|FIXME|XXX|HACK)|/\*\s*(TODO|FIXME|XXX|HACK))" src/ 2>/dev/null | head -10 >&2
    echo "" >&2
    VIOLATIONS=$((VIOLATIONS + TODO_COUNT))
fi

# Check for mock methods
echo "Checking for mock/stub method patterns..."
MOCK_METHODS=$(grep -rn --include="*.java" -E "(private|public|protected).*\s+(mock|stub|fake)[A-Z]\w*\(" src/ 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_METHODS" -gt 0 ]; then
    echo -e "${RED}VIOLATION: Found $MOCK_METHODS mock/stub methods${NC}" >&2
    echo "   Forbidden: Methods with 'mock', 'stub', or 'fake' in name" >&2
    echo "   Required: Real implementations only" >&2
    echo "" >&2
    echo "   Locations:" >&2
    grep -rn --include="*.java" -E "(private|public|protected).*\s+(mock|stub|fake)[A-Z]\w*\(" src/ 2>/dev/null | head -10 >&2
    echo "" >&2
    VIOLATIONS=$((VIOLATIONS + MOCK_METHODS))
fi

# Check for "mock mode" or "stub mode" strings
echo "Checking for mock/stub mode indicators..."
MOCK_MODE=$(grep -rn --include="*.java" -iE "(mock\s+mode|stub\s+mode|running\s+in\s+mock)" src/ 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_MODE" -gt 0 ]; then
    echo -e "${RED}VIOLATION: Found $MOCK_MODE 'mock mode' references${NC}" >&2
    echo "   Forbidden: Dual-mode behavior (real vs mock)" >&2
    echo "   Required: Fail fast if dependencies missing" >&2
    echo "" >&2
    echo "   Locations:" >&2
    grep -rn --include="*.java" -iE "(mock\s+mode|stub\s+mode|running\s+in\s+mock)" src/ 2>/dev/null | head -10 >&2
    echo "" >&2
    VIOLATIONS=$((VIOLATIONS + MOCK_MODE))
fi

# Check for empty stub returns (common anti-pattern)
echo "Checking for suspicious empty returns..."
EMPTY_RETURNS=$(grep -rn --include="*.java" -E "return\s+\"\"\s*;\s*(//.*stub|//.*TODO)" src/ 2>/dev/null | wc -l || echo "0")
if [ "$EMPTY_RETURNS" -gt 0 ]; then
    echo -e "${YELLOW}WARNING: Found $EMPTY_RETURNS empty returns with TODO/stub comments${NC}" >&2
    echo "   Review these - may be stubs that should throw exceptions" >&2
    echo "" >&2
    echo "   Locations:" >&2
    grep -rn --include="*.java" -E "return\s+\"\"\s*;\s*(//.*stub|//.*TODO)" src/ 2>/dev/null | head -10 >&2
    echo "" >&2
fi

# Check for common mock frameworks (shouldn't be in production code)
echo "Checking for mock framework usage in src/..."
MOCK_FRAMEWORKS=$(grep -rn --include="*.java" -E "import\s+(org\.mockito|org\.easymock|org\.jmock)" src/ 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_FRAMEWORKS" -gt 0 ]; then
    echo -e "${RED}VIOLATION: Found $MOCK_FRAMEWORKS mock framework imports in production code${NC}" >&2
    echo "   Forbidden: Mock frameworks in src/ (test/ is OK)" >&2
    echo "   Required: Real implementations only" >&2
    echo "" >&2
    echo "   Locations:" >&2
    grep -rn --include="*.java" -E "import\s+(org\.mockito|org\.easymock|org\.jmock)" src/ 2>/dev/null | head -10 >&2
    echo "" >&2
    VIOLATIONS=$((VIOLATIONS + MOCK_FRAMEWORKS))
fi

echo ""
echo "=========================================="
if [ "$VIOLATIONS" -eq 0 ]; then
    echo -e "${GREEN}PASSED: No forbidden patterns detected${NC}"
    echo -e "${GREEN}PASSED: Code meets Fortune 5 production standards${NC}"
    echo "[validate-no-mocks.sh] Validation complete - PASSED" >&2
    exit 0
else
    echo -e "${RED}FAILED: Found $VIOLATIONS violations${NC}" >&2
    echo "" >&2
    echo "MANDATORY FIXES REQUIRED:" >&2
    echo "  1. Remove all TODO/FIXME/XXX/HACK comments" >&2
    echo "  2. Replace mock/stub methods with real implementations" >&2
    echo "  3. Remove mock mode fallbacks" >&2
    echo "  4. Throw UnsupportedOperationException for unimplemented features" >&2
    echo "" >&2
    echo "See CLAUDE.md for coding standards" >&2
    echo "[validate-no-mocks.sh] Validation complete - FAILED with $VIOLATIONS violations" >&2
    exit 1
fi
