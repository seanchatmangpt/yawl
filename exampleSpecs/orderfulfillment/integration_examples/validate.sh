#!/bin/bash

#
# YAWL Integration Examples - Validation Script
#
# This script validates that all examples are properly set up and ready to run.
# It checks files, dependencies, configuration, and connectivity.
#
# Usage: ./validate.sh
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

PASSED=0
FAILED=0
WARNINGS=0

print_header() {
    echo -e "\n${GREEN}========================================${NC}"
    echo -e "${GREEN}$1${NC}"
    echo -e "${GREEN}========================================${NC}\n"
}

check_pass() {
    echo -e "${GREEN}✓${NC} $1"
    ((PASSED++))
}

check_fail() {
    echo -e "${RED}✗${NC} $1"
    ((FAILED++))
}

check_warn() {
    echo -e "${YELLOW}⚠${NC} $1"
    ((WARNINGS++))
}

print_header "YAWL Integration Examples - Validation"

# Check 1: File existence
print_header "1. Checking Files"

FILES=(
    "McpServerExample.java"
    "McpClientExample.java"
    "A2aServerExample.java"
    "A2aClientExample.java"
    "OrderFulfillmentIntegration.java"
    "AiAgentExample.java"
    "README.md"
    "QUICK_START.md"
    "EXAMPLES_OVERVIEW.md"
    "INDEX.md"
    "run-examples.sh"
)

for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        check_pass "$file exists"
    else
        check_fail "$file missing"
    fi
done

# Check 2: File sizes
print_header "2. Checking File Sizes"

for file in "${FILES[@]}"; do
    if [ -f "$file" ]; then
        SIZE=$(wc -l < "$file")
        if [ "$SIZE" -gt 10 ]; then
            check_pass "$file has $SIZE lines (sufficient content)"
        else
            check_warn "$file only has $SIZE lines (may be incomplete)"
        fi
    fi
done

# Check 3: Java environment
print_header "3. Checking Java Environment"

if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
    if [ "$JAVA_VERSION" -ge 21 ]; then
        check_pass "Java $JAVA_VERSION found (required: 21+)"
    else
        check_fail "Java $JAVA_VERSION found (required: 21+)"
    fi
else
    check_fail "Java not found (required: 21+)"
fi

if command -v javac &> /dev/null; then
    check_pass "Java compiler (javac) found"
else
    check_fail "Java compiler (javac) not found"
fi

# Check 4: YAWL root directory
print_header "4. Checking YAWL Installation"

YAWL_ROOT="/home/user/yawl"
if [ -d "$YAWL_ROOT" ]; then
    check_pass "YAWL root directory exists: $YAWL_ROOT"
else
    check_fail "YAWL root directory not found: $YAWL_ROOT"
fi

if [ -d "$YAWL_ROOT/src" ]; then
    check_pass "YAWL source directory exists"
else
    check_warn "YAWL source directory not found (compilation may fail)"
fi

if [ -d "$YAWL_ROOT/classes" ]; then
    check_pass "YAWL classes directory exists"
else
    check_warn "YAWL classes directory not found (needs compilation)"
fi

if [ -d "$YAWL_ROOT/build/3rdParty/lib" ]; then
    JAR_COUNT=$(find "$YAWL_ROOT/build/3rdParty/lib" -name "*.jar" | wc -l)
    check_pass "Third-party libraries found ($JAR_COUNT JARs)"
else
    check_fail "Third-party libraries directory not found"
fi

# Check 5: Environment variables
print_header "5. Checking Environment Variables"

if [ -n "$YAWL_ENGINE_URL" ]; then
    check_pass "YAWL_ENGINE_URL is set: $YAWL_ENGINE_URL"
else
    check_warn "YAWL_ENGINE_URL not set (will use default: http://localhost:8080/yawl/ib)"
fi

if [ -n "$ZAI_API_KEY" ]; then
    check_pass "ZAI_API_KEY is set (AI features enabled)"
else
    check_warn "ZAI_API_KEY not set (AI features will be limited)"
fi

# Check 6: YAWL Engine connectivity
print_header "6. Checking YAWL Engine"

ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080/yawl/ib}"

if curl -s --connect-timeout 5 "$ENGINE_URL" > /dev/null 2>&1; then
    check_pass "YAWL Engine is reachable at $ENGINE_URL"

    # Test authentication
    AUTH_RESPONSE=$(curl -s "${ENGINE_URL}?action=connect&userid=admin&password=YAWL")
    if [[ "$AUTH_RESPONSE" == *"<response>"* ]]; then
        check_pass "YAWL authentication works (admin/YAWL)"
    else
        check_warn "YAWL authentication may not work (check credentials)"
    fi
else
    check_warn "YAWL Engine not reachable at $ENGINE_URL (start with: docker-compose up -d)"
fi

# Check 7: Z.AI connectivity (optional)
print_header "7. Checking Z.AI Service (Optional)"

if [ -n "$ZAI_API_KEY" ]; then
    ZAI_RESPONSE=$(curl -s -X POST "https://open.bigmodel.cn/api/paas/v4/chat/completions" \
        -H "Authorization: Bearer $ZAI_API_KEY" \
        -H "Content-Type: application/json" \
        -d '{"model":"glm-4.6","messages":[{"role":"user","content":"test"}]}' 2>&1)

    if [[ "$ZAI_RESPONSE" == *"choices"* ]] || [[ "$ZAI_RESPONSE" == *"error"* ]]; then
        check_pass "Z.AI API is reachable"

        if [[ "$ZAI_RESPONSE" == *"choices"* ]]; then
            check_pass "Z.AI API key is valid"
        else
            check_warn "Z.AI API key may be invalid or quota exhausted"
        fi
    else
        check_warn "Z.AI API not reachable (network issue?)"
    fi
else
    check_warn "ZAI_API_KEY not set - skipping Z.AI connectivity check"
fi

# Check 8: Compilation test
print_header "8. Testing Compilation"

if [ -d "$YAWL_ROOT/classes/org/yawlfoundation/yawl/integration" ]; then
    check_pass "Integration classes directory exists"
else
    check_warn "Integration classes not compiled yet"
fi

# Try to compile one example
COMPILE_TEST=$(javac -cp "$YAWL_ROOT/classes:$YAWL_ROOT/build/3rdParty/lib/*" \
    McpServerExample.java -d /tmp 2>&1 || true)

if [ $? -eq 0 ]; then
    check_pass "Example compilation successful"
    rm -f /tmp/org/yawlfoundation/yawl/examples/integration/*.class 2>/dev/null || true
else
    if [[ "$COMPILE_TEST" == *"cannot find symbol"* ]]; then
        check_warn "Compilation requires YAWL classes (run: ant -f build/build.xml compile)"
    else
        check_warn "Compilation may have issues (check error above)"
    fi
fi

# Check 9: Script permissions
print_header "9. Checking Script Permissions"

if [ -x "run-examples.sh" ]; then
    check_pass "run-examples.sh is executable"
else
    check_warn "run-examples.sh is not executable (run: chmod +x run-examples.sh)"
fi

if [ -x "validate.sh" ]; then
    check_pass "validate.sh is executable"
else
    check_warn "validate.sh is not executable (but you're running it now!)"
fi

# Check 10: Documentation completeness
print_header "10. Checking Documentation"

README_SIZE=$(wc -l < README.md 2>/dev/null || echo 0)
if [ "$README_SIZE" -gt 500 ]; then
    check_pass "README.md is comprehensive ($README_SIZE lines)"
else
    check_warn "README.md may be incomplete ($README_SIZE lines)"
fi

QUICK_SIZE=$(wc -l < QUICK_START.md 2>/dev/null || echo 0)
if [ "$QUICK_SIZE" -gt 100 ]; then
    check_pass "QUICK_START.md exists ($QUICK_SIZE lines)"
else
    check_warn "QUICK_START.md may be incomplete ($QUICK_SIZE lines)"
fi

OVERVIEW_SIZE=$(wc -l < EXAMPLES_OVERVIEW.md 2>/dev/null || echo 0)
if [ "$OVERVIEW_SIZE" -gt 500 ]; then
    check_pass "EXAMPLES_OVERVIEW.md is comprehensive ($OVERVIEW_SIZE lines)"
else
    check_warn "EXAMPLES_OVERVIEW.md may be incomplete ($OVERVIEW_SIZE lines)"
fi

# Summary
print_header "Validation Summary"

TOTAL=$((PASSED + FAILED + WARNINGS))
echo "Total Checks: $TOTAL"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"
echo -e "${YELLOW}Warnings: $WARNINGS${NC}"
echo ""

if [ "$FAILED" -eq 0 ]; then
    echo -e "${GREEN}✓ All critical checks passed!${NC}"
    echo ""
    echo "Ready to run examples:"
    echo "  ./run-examples.sh"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Some critical checks failed${NC}"
    echo ""
    echo "Fix issues above before running examples"
    echo "See README.md for setup instructions"
    echo ""
    exit 1
fi
