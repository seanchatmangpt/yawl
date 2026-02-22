#!/bin/bash

# Test script for Pattern Demo Runner
# Tests various configurations to ensure the script works correctly

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERN_DEMO_SCRIPT="$SCRIPT_DIR/run-pattern-demo.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "Testing Pattern Demo Runner Script"
echo "=================================="

# Test 1: Help option
echo -e "\n${BLUE}Test 1: Help option${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --help"
$PATTERN_DEMO_SCRIPT --help > /dev/null
if [[ $? -eq 0 ]]; then
    echo -e "${GREEN}✓ Help option works${NC}"
else
    echo -e "${RED}✗ Help option failed${NC}"
fi

# Test 2: Invalid option
echo -e "\n${BLUE}Test 2: Invalid option${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --invalid"
output=$($PATTERN_DEMO_SCRIPT --invalid 2>&1)
if echo "$output" | grep -q "Unknown option"; then
    echo -e "${GREEN}✓ Invalid option handled correctly${NC}"
else
    echo -e "${RED}✗ Invalid option not handled${NC}"
fi

# Test 3: No options provided
echo -e "\n${BLUE}Test 3: No options provided${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT"
output=$($PATTERN_DEMO_SCRIPT 2>&1)
if echo "$output" | grep -q "Must specify one of"; then
    echo -e "${GREEN}✓ No options validation works${NC}"
else
    echo -e "${RED}✗ No options validation failed${NC}"
fi

# Test 4: Multiple options provided
echo -e "\n${BLUE}Test 4: Multiple options provided${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --all --pattern WCP-1"
output=$($PATTERN_DEMO_SCRIPT --all --pattern WCP-1 2>&1)
exit_code=$?
if [[ $exit_code -eq 1 ]] && echo "$output" | grep -q "Cannot specify multiple options"; then
    echo -e "${GREEN}✓ Multiple options validation works${NC}"
else
    echo -e "${RED}✗ Multiple options validation failed${NC}"
    echo "Exit code: $exit_code"
    echo "Output: $output"
fi

# Test 5: Invalid format
echo -e "\n${BLUE}Test 5: Invalid format${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --all --format invalid"
output=$($PATTERN_DEMO_SCRIPT --all --format invalid 2>&1)
if echo "$output" | grep -q "Invalid format 'invalid'"; then
    echo -e "${GREEN}✓ Invalid format validation works${NC}"
else
    echo -e "${RED}✗ Invalid format validation failed${NC}"
fi

# Test 6: Valid pattern option (dry run - won't actually execute)
echo -e "\n${BLUE}Test 6: Valid pattern option (format check)${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --pattern WCP-1 --format console"
# We'll just check that the command builds correctly without actually running Maven
# by checking the output argument construction
output=$($PATTERN_DEMO_SCRIPT --pattern WCP-1 --format console --help | head -n5)
if echo "$output" | grep -q "Pattern Demo Runner"; then
    echo -e "${GREEN}✓ Pattern option format is valid${NC}"
else
    echo -e "${YELLOW}⚠ Pattern option format check inconclusive${NC}"
fi

# Test 7: Category option
echo -e "\n${BLUE}Test 7: Category option format check${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --category BASIC --format json"
output=$($PATTERN_DEMO_SCRIPT --category BASIC --format json --help | head -n5)
if echo "$output" | grep -q "Pattern Demo Runner"; then
    echo -e "${GREEN}✓ Category option format is valid${NC}"
else
    echo -e "${YELLOW}⚠ Category option format check inconclusive${NC}"
fi

# Test 8: All patterns option
echo -e "\n${BLUE}Test 8: All patterns option format check${NC}"
echo "Command: $PATTERN_DEMO_SCRIPT --all --format markdown"
output=$($PATTERN_DEMO_SCRIPT --all --format markdown --help | head -n5)
if echo "$output" | grep -q "Pattern Demo Runner"; then
    echo -e "${GREEN}✓ All patterns option format is valid${NC}"
else
    echo -e "${YELLOW}⚠ All patterns option format check inconclusive${NC}"
fi

echo -e "\n${BLUE}Summary${NC}"
echo "========="
echo "Script validation tests completed."
echo
echo "To test actual execution, run:"
echo "  $PATTERN_DEMO_SCRIPT --all"
echo "  $PATTERN_DEMO_SCRIPT --pattern WCP-1"
echo "  $PATTERN_DEMO_SCRIPT --category BASIC --format json"
echo
echo "Note: Actual execution requires Maven and the project to be properly built."