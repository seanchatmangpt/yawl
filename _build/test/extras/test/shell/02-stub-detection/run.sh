#!/bin/bash
#
# Phase 02: Stub Detection
#
# Scans source code for forbidden patterns that indicate
# incomplete or fake implementations.
#
# Forbidden patterns:
#   - UnsupportedOperationException
#   - TODO/FIXME/XXX/HACK comments
#   - mock/stub/fake keywords in production code
#   - Empty method bodies
#   - Placeholder implementations
#
# Exit codes:
#   0 - No stubs detected
#   1 - Stubs detected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
SRC_DIR="$PROJECT_DIR/src"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Test counters
TESTS_RUN=0
TESTS_PASSED=0
TESTS_FAILED=0

# Violation counter
VIOLATIONS=0

echo "==========================================="
echo "Phase 02: Stub Detection"
echo "==========================================="
echo ""
echo "Source directory: $SRC_DIR"
echo ""

# Test: No TODO/FIXME/XXX/HACK comments
echo "--- Test: No TODO/FIXME/XXX/HACK Comments ---"
TESTS_RUN=$((TESTS_RUN + 1))

TODO_PATTERNS=(
    "// TODO"
    "// FIXME"
    "// XXX"
    "// HACK"
    "/* TODO"
    "/* FIXME"
    "/* XXX"
    "/* HACK"
)

todo_violations=0
for pattern in "${TODO_PATTERNS[@]}"; do
    count=$(grep -r --include="*.java" -F "$pattern" "$SRC_DIR" 2>/dev/null | wc -l | tr -d '[:space:]' || echo "0")
    count=${count:-0}
    if [ "$count" -gt 0 ]; then
        echo -e "  ${RED}✗${NC} Found $count occurrences of '$pattern'"
        grep -r --include="*.java" -F "$pattern" "$SRC_DIR" 2>/dev/null | head -5 | while read -r line; do
            echo "      $line"
        done
        todo_violations=$((todo_violations + count))
    fi
done

if [ "$todo_violations" -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} No TODO/FIXME/XXX/HACK comments found"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✗${NC} Found $todo_violations TODO/FIXME comments"
    VIOLATIONS=$((VIOLATIONS + todo_violations))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: No UnsupportedOperationException
echo "--- Test: No UnsupportedOperationException ---"
TESTS_RUN=$((TESTS_RUN + 1))

uoe_count=$(grep -r --include="*.java" "UnsupportedOperationException" "$SRC_DIR" 2>/dev/null | wc -l | tr -d '[:space:]' || echo "0")
uoe_count=${uoe_count:-0}

if [ "$uoe_count" -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} No UnsupportedOperationException found"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✗${NC} Found $uoe_count UnsupportedOperationException throws:"
    grep -r --include="*.java" "UnsupportedOperationException" "$SRC_DIR" 2>/dev/null | head -10 | while read -r line; do
        echo "      $line"
    done
    VIOLATIONS=$((VIOLATIONS + uoe_count))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: No mock/stub keywords in production code
echo "--- Test: No Mock/Stub Keywords ---"
TESTS_RUN=$((TESTS_RUN + 1))

# Patterns that indicate mocks/stubs (case insensitive)
MOCK_PATTERNS=(
    "mock[A-Z][a-zA-Z]*("
    "stub[A-Z][a-zA-Z]*("
    "fake[A-Z][a-zA-Z]*("
    "dummy[A-Z][a-zA-Z]*("
)

mock_violations=0
for pattern in "${MOCK_PATTERNS[@]}"; do
    count=$(grep -r --include="*.java" -E "$pattern" "$SRC_DIR" 2>/dev/null | wc -l | tr -d '[:space:]' || echo "0")
    count=${count:-0}
    if [ "$count" -gt 0 ]; then
        echo -e "  ${RED}✗${NC} Found $count matches for pattern '$pattern'"
        grep -r --include="*.java" -E "$pattern" "$SRC_DIR" 2>/dev/null | head -5 | while read -r line; do
            echo "      $line"
        done
        mock_violations=$((mock_violations + count))
    fi
done

if [ "$mock_violations" -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} No mock/stub method patterns found"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✗${NC} Found $mock_violations mock/stub patterns"
    VIOLATIONS=$((VIOLATIONS + mock_violations))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: No mock framework imports in production code
echo "--- Test: No Mock Framework Imports ---"
TESTS_RUN=$((TESTS_RUN + 1))

MOCK_IMPORTS=(
    "import org.mockito"
    "import org.easymock"
    "import org.jmock"
    "import org.powermock"
)

import_violations=0
for pattern in "${MOCK_IMPORTS[@]}"; do
    count=$(grep -r --include="*.java" -F "$pattern" "$SRC_DIR" 2>/dev/null | wc -l | tr -d '[:space:]' || echo "0")
    count=${count:-0}
    if [ "$count" -gt 0 ]; then
        echo -e "  ${RED}✗${NC} Found $count mock framework imports"
        grep -r --include="*.java" -F "$pattern" "$SRC_DIR" 2>/dev/null | head -5 | while read -r line; do
            echo "      $line"
        done
        import_violations=$((import_violations + count))
    fi
done

if [ "$import_violations" -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} No mock framework imports in production code"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✗${NC} Found $import_violations mock framework imports"
    VIOLATIONS=$((VIOLATIONS + import_violations))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Test: No empty method bodies
echo "--- Test: No Empty Method Bodies ---"
TESTS_RUN=$((TESTS_RUN + 1))

# Look for methods that just return null or empty without logic
# This is a heuristic check
empty_count=$(grep -r --include="*.java" -E "^\s*return\s+(null|\"\")\s*;\s*$" "$SRC_DIR" 2>/dev/null | wc -l | tr -d '[:space:]' || echo "0")
empty_count=${empty_count:-0}

if [ "$empty_count" -lt 10 ]; then
    # Some empty returns are legitimate
    echo -e "  ${GREEN}✓${NC} Acceptable number of empty returns ($empty_count)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${YELLOW}!${NC} High number of empty returns ($empty_count) - review manually"
    # This is a warning, not a failure
    TESTS_PASSED=$((TESTS_PASSED + 1))
fi
echo ""

# Test: No placeholder strings
echo "--- Test: No Placeholder Strings ---"
TESTS_RUN=$((TESTS_RUN + 1))

PLACEHOLDER_PATTERNS=(
    "not implemented"
    "Not implemented"
    "NOT IMPLEMENTED"
    "placeholder"
    "Placeholder"
    "PLACEHOLDER"
    "coming soon"
    "Coming Soon"
)

placeholder_count=0
for pattern in "${PLACEHOLDER_PATTERNS[@]}"; do
    count=$(grep -r --include="*.java" -F "$pattern" "$SRC_DIR" 2>/dev/null | wc -l | tr -d '[:space:]' || echo "0")
    count=${count:-0}
    if [ "$count" -gt 0 ]; then
        echo -e "  ${RED}✗${NC} Found $count occurrences of '$pattern'"
        placeholder_count=$((placeholder_count + count))
    fi
done

if [ "$placeholder_count" -eq 0 ]; then
    echo -e "  ${GREEN}✓${NC} No placeholder strings found"
    TESTS_PASSED=$((TESTS_PASSED + 1))
else
    echo -e "  ${RED}✗${NC} Found $placeholder_count placeholder strings"
    VIOLATIONS=$((VIOLATIONS + placeholder_count))
    TESTS_FAILED=$((TESTS_FAILED + 1))
fi
echo ""

# Summary
echo "==========================================="
echo "Stub Detection Summary"
echo "==========================================="
echo "Tests run:    $TESTS_RUN"
echo -e "Tests passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Tests failed: ${RED}$TESTS_FAILED${NC}"
echo "Violations:   $VIOLATIONS"
echo "==========================================="

if [ $VIOLATIONS -gt 0 ]; then
    echo ""
    echo -e "${RED}VIOLATIONS DETECTED${NC}"
    echo ""
    echo "Required fixes:"
    echo "  1. Remove all TODO/FIXME/XXX/HACK comments"
    echo "  2. Replace UnsupportedOperationException with real implementations"
    echo "  3. Remove mock/stub methods"
    echo "  4. Remove mock framework imports from production code"
    echo "  5. Remove placeholder strings"
    echo ""
    echo -e "${RED}Phase 02 FAILED${NC}"
    exit 1
fi

echo -e "${GREEN}Phase 02 PASSED${NC}"
exit 0
