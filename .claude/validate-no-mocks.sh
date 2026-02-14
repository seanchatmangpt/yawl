#!/bin/bash
# YAWL Code Quality Validator - ZERO TOLERANCE FOR MOCKS/STUBS/TODOs
# This script enforces Fortune 5 production quality standards

set -e

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
echo "🔍 Checking for FORBIDDEN patterns..."
echo ""

# Check for TODO comments
echo "Checking for TODO/FIXME/XXX/HACK comments..."
TODO_COUNT=$(grep -rn --include="*.java" -E "(//\s*(TODO|FIXME|XXX|HACK)|/\*\s*(TODO|FIXME|XXX|HACK))" src/ 2>/dev/null | wc -l || echo "0")
if [ "$TODO_COUNT" -gt 0 ]; then
    echo -e "${RED}❌ VIOLATION: Found $TODO_COUNT TODO/FIXME/XXX/HACK comments${NC}"
    echo "   Forbidden: TODO comments"
    echo "   Required: Either implement or throw UnsupportedOperationException"
    echo ""
    echo "   Locations:"
    grep -rn --include="*.java" -E "(//\s*(TODO|FIXME|XXX|HACK)|/\*\s*(TODO|FIXME|XXX|HACK))" src/ 2>/dev/null | head -10
    echo ""
    VIOLATIONS=$((VIOLATIONS + TODO_COUNT))
fi

# Check for mock methods
echo "Checking for mock/stub method patterns..."
MOCK_METHODS=$(grep -rn --include="*.java" -E "(private|public|protected).*\s+(mock|stub|fake)[A-Z]\w*\(" src/ 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_METHODS" -gt 0 ]; then
    echo -e "${RED}❌ VIOLATION: Found $MOCK_METHODS mock/stub methods${NC}"
    echo "   Forbidden: Methods with 'mock', 'stub', or 'fake' in name"
    echo "   Required: Real implementations only"
    echo ""
    echo "   Locations:"
    grep -rn --include="*.java" -E "(private|public|protected).*\s+(mock|stub|fake)[A-Z]\w*\(" src/ 2>/dev/null | head -10
    echo ""
    VIOLATIONS=$((VIOLATIONS + MOCK_METHODS))
fi

# Check for "mock mode" or "stub mode" strings
echo "Checking for mock/stub mode indicators..."
MOCK_MODE=$(grep -rn --include="*.java" -iE "(mock\s+mode|stub\s+mode|running\s+in\s+mock)" src/ 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_MODE" -gt 0 ]; then
    echo -e "${RED}❌ VIOLATION: Found $MOCK_MODE 'mock mode' references${NC}"
    echo "   Forbidden: Dual-mode behavior (real vs mock)"
    echo "   Required: Fail fast if dependencies missing"
    echo ""
    echo "   Locations:"
    grep -rn --include="*.java" -iE "(mock\s+mode|stub\s+mode|running\s+in\s+mock)" src/ 2>/dev/null | head -10
    echo ""
    VIOLATIONS=$((VIOLATIONS + MOCK_MODE))
fi

# Check for empty stub returns (common anti-pattern)
echo "Checking for suspicious empty returns..."
EMPTY_RETURNS=$(grep -rn --include="*.java" -E "return\s+\"\"\s*;\s*(//.*stub|//.*TODO)" src/ 2>/dev/null | wc -l || echo "0")
if [ "$EMPTY_RETURNS" -gt 0 ]; then
    echo -e "${YELLOW}⚠️  WARNING: Found $EMPTY_RETURNS empty returns with TODO/stub comments${NC}"
    echo "   Review these - may be stubs that should throw exceptions"
    echo ""
    echo "   Locations:"
    grep -rn --include="*.java" -E "return\s+\"\"\s*;\s*(//.*stub|//.*TODO)" src/ 2>/dev/null | head -10
    echo ""
fi

# Check for common mock frameworks (shouldn't be in production code)
echo "Checking for mock framework usage in src/..."
MOCK_FRAMEWORKS=$(grep -rn --include="*.java" -E "import\s+(org\.mockito|org\.easymock|org\.jmock)" src/ 2>/dev/null | wc -l || echo "0")
if [ "$MOCK_FRAMEWORKS" -gt 0 ]; then
    echo -e "${RED}❌ VIOLATION: Found $MOCK_FRAMEWORKS mock framework imports in production code${NC}"
    echo "   Forbidden: Mock frameworks in src/ (test/ is OK)"
    echo "   Required: Real implementations only"
    echo ""
    echo "   Locations:"
    grep -rn --include="*.java" -E "import\s+(org\.mockito|org\.easymock|org\.jmock)" src/ 2>/dev/null | head -10
    echo ""
    VIOLATIONS=$((VIOLATIONS + MOCK_FRAMEWORKS))
fi

echo ""
echo "=========================================="
if [ "$VIOLATIONS" -eq 0 ]; then
    echo -e "${GREEN}✅ PASSED: No forbidden patterns detected${NC}"
    echo -e "${GREEN}✅ Code meets Fortune 5 production standards${NC}"
    exit 0
else
    echo -e "${RED}❌ FAILED: Found $VIOLATIONS violations${NC}"
    echo ""
    echo "MANDATORY FIXES REQUIRED:"
    echo "  1. Remove all TODO/FIXME/XXX/HACK comments"
    echo "  2. Replace mock/stub methods with real implementations"
    echo "  3. Remove mock mode fallbacks"
    echo "  4. Throw UnsupportedOperationException for unimplemented features"
    echo ""
    echo "See CLAUDE.md for coding standards"
    exit 1
fi
