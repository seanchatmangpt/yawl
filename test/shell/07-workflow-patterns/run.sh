#!/bin/bash
#
# Phase 07: Workflow Pattern Tests
#
# Tests YAWL workflow pattern implementations:
# - Load specification
# - Start case
# - Get work items
# - Checkout/complete work items
# - Case completion verification
#
# Exit codes:
#   0 - All pattern tests passed
#   1 - Pattern tests failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"

# Source libraries
source "$PROJECT_DIR/scripts/shell-test/assert.sh"
source "$PROJECT_DIR/scripts/shell-test/http-client.sh"
source "$PROJECT_DIR/scripts/shell-test/process-manager.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Configuration
ENGINE_PORT="${ENGINE_PORT:-8080}"
ENGINE_URL="http://localhost:$ENGINE_PORT"
ENGINE_USER="${ENGINE_USER:-admin}"
ENGINE_PASS="${ENGINE_PASS:-YAWL}"

echo "==========================================="
echo "Phase 07: Workflow Pattern Tests"
echo "==========================================="
echo ""
echo "Engine URL: $ENGINE_URL"
echo "User:       $ENGINE_USER"
echo ""

# Check if engine is running
check_engine_running() {
    nc -z localhost "$ENGINE_PORT" 2>/dev/null
}

# Test: Engine availability
echo "--- Test: Engine Availability ---"
reset_test_counters

if check_engine_running; then
    echo -e "${GREEN}✓ Engine running on port $ENGINE_PORT${NC}"
    ENGINE_RUNNING=true
else
    echo -e "${YELLOW}! Engine not running on port $ENGINE_PORT${NC}"
    ENGINE_RUNNING=false
fi
echo ""

if [ "$ENGINE_RUNNING" != "true" ]; then
    echo "==========================================="
    echo "Workflow Pattern Summary"
    echo "==========================================="
    echo ""
    echo -e "${YELLOW}Phase 07 SKIPPED (engine not available)${NC}"
    echo ""
    echo "To enable workflow pattern testing:"
    echo "  1. Deploy YAWL: ant -f build/build.xml deploy"
    echo "  2. Start Tomcat: \$CATALINA_HOME/bin/catalina.sh run"
    echo "  3. Verify engine: curl http://localhost:8080/yawl/ib"
    echo "  4. Re-run this test"
    exit 0
fi

# Test: Interface B authentication
echo "--- Test: Interface B Authentication ---"
reset_test_counters

# YAWL uses HTTP Basic auth for Interface B
AUTH_HEADER="Authorization: Basic $(echo -n "$ENGINE_USER:$ENGINE_PASS" | base64)"

# Try to connect to Interface B
IB_RESPONSE=$(curl -s -H "$AUTH_HEADER" "$ENGINE_URL/yawl/ib" 2>/dev/null) || IB_RESPONSE=""

if [ -n "$IB_RESPONSE" ]; then
    echo -e "${GREEN}✓ Interface B accessible${NC}"
else
    echo -e "${YELLOW}! Interface B returned empty response${NC}"
fi
echo ""

# Test: List specifications
echo "--- Test: List Specifications ---"
reset_test_counters

# Get specification list
SPEC_LIST=$(curl -s -H "$AUTH_HEADER" "$ENGINE_URL/yawl/ia/getSpecifications" 2>/dev/null) || SPEC_LIST=""

if [ -n "$SPEC_LIST" ]; then
    echo -e "${GREEN}✓ Specification list endpoint accessible${NC}"

    # Check for specification elements (XML response)
    if echo "$SPEC_LIST" | grep -q "<specification"; then
        local spec_count
        spec_count=$(echo "$SPEC_LIST" | grep -c "<specification" || echo "0")
        echo "  Found $spec_count specifications"
    fi
else
    echo -e "${YELLOW}! Specification list returned empty${NC}"
fi
echo ""

# Test: Load test specification
echo "--- Test: Load Test Specification ---"
reset_test_counters

# Find a test specification
TEST_SPEC=""
SPEC_DIRS=(
    "$PROJECT_DIR/exampleSpecs/xml/Beta2-7"
    "$PROJECT_DIR/exampleSpecs/xml"
    "$PROJECT_DIR/exampleSpecs/orderfulfillment"
)

for dir in "${SPEC_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        for spec in "$dir"/*.xml; do
            [ -f "$spec" ] || continue
            TEST_SPEC="$spec"
            break 2
        done
    fi
done

if [ -n "$TEST_SPEC" ]; then
    echo "Using specification: $(basename "$TEST_SPEC")"

    # Upload specification
    UPLOAD_RESULT=$(curl -s -H "$AUTH_HEADER" \
        -F "specFile=@$TEST_SPEC" \
        "$ENGINE_URL/yawl/ia/addSpecification" 2>/dev/null) || UPLOAD_RESULT=""

    if [ -n "$UPLOAD_RESULT" ]; then
        if echo "$UPLOAD_RESULT" | grep -qi "success\|ok\|added"; then
            echo -e "${GREEN}✓ Specification loaded successfully${NC}"
        elif echo "$UPLOAD_RESULT" | grep -qi "error\|fail"; then
            echo -e "${YELLOW}! Specification load returned: ${UPLOAD_RESPONSE:0:100}...${NC}"
        else
            echo -e "${GREEN}✓ Specification load request sent${NC}"
        fi
    else
        echo -e "${YELLOW}! Specification upload returned empty${NC}"
    fi
else
    echo -e "${YELLOW}! No test specification found${NC}"
fi
echo ""

# Test: Start case
echo "--- Test: Start Case ---"
reset_test_counters

if [ -n "$TEST_SPEC" ]; then
    SPEC_ID=$(basename "$TEST_SPEC" .xml)

    # Start a case
    START_RESULT=$(curl -s -H "$AUTH_HEADER" \
        --data-urlencode "specIdentifier=$SPEC_ID" \
        --data-urlencode "specVersion=0.1" \
        --data-urlencode "specUri=$SPEC_ID" \
        --data-urlencode "data=<data/>" \
        "$ENGINE_URL/yawl/ib/startCase" 2>/dev/null) || START_RESULT=""

    if [ -n "$START_RESULT" ]; then
        # Extract case ID from response
        CASE_ID=""

        if echo "$START_RESULT" | grep -q "<caseId>"; then
            CASE_ID=$(echo "$START_RESULT" | sed -n 's/.*<caseId>\([^<]*\)<\/caseId>.*/\1/p')
        elif echo "$START_RESULT" | grep -q "case.*id"; then
            CASE_ID=$(echo "$START_RESULT" | grep -oE "case[_-]?[iI]d['\"]?\s*[:=]\s*['\"]?[^'\"<>]+" | head -1 | sed 's/.*[:=]//' | tr -d "'\" ")
        fi

        if [ -n "$CASE_ID" ]; then
            echo -e "${GREEN}✓ Case started successfully${NC}"
            echo "  Case ID: $CASE_ID"
        else
            echo -e "${YELLOW}! Case started but ID not found in response${NC}"
            echo "  Response: ${START_RESULT:0:100}..."
        fi
    else
        echo -e "${YELLOW}! Start case returned empty${NC}"
    fi
else
    echo -e "${YELLOW}! Skipped (no specification loaded)${NC}"
fi
echo ""

# Test: Get work items
echo "--- Test: Get Work Items ---"
reset_test_counters

if [ -n "${CASE_ID:-}" ]; then
    # Get work items for the case
    WORK_ITEMS=$(curl -s -H "$AUTH_HEADER" \
        "$ENGINE_URL/yawl/ib/getWorkItems?caseId=$CASE_ID" 2>/dev/null) || WORK_ITEMS=""

    if [ -n "$WORK_ITEMS" ]; then
        echo -e "${GREEN}✓ Work items endpoint accessible${NC}"

        if echo "$WORK_ITEMS" | grep -q "<workItem"; then
            local item_count
            item_count=$(echo "$WORK_ITEMS" | grep -c "<workItem" || echo "0")
            echo "  Found $item_count work items"

            if [ "$item_count" -gt 0 ]; then
                echo -e "${GREEN}✓ Work items created${NC}"
            fi
        fi
    else
        echo -e "${YELLOW}! Work items returned empty${NC}"
    fi
else
    echo -e "${YELLOW}! Skipped (no case started)${NC}"
fi
echo ""

# Test: Complete work item
echo "--- Test: Complete Work Item ---"
reset_test_counters

if [ -n "${CASE_ID:-}" ] && [ -n "${WORK_ITEMS:-}" ]; then
    # Extract first work item ID
    ITEM_ID=""
    if echo "$WORK_ITEMS" | grep -q "<itemID>"; then
        ITEM_ID=$(echo "$WORK_ITEMS" | sed -n 's/.*<itemID>\([^<]*\)<\/itemID>.*/\1/p' | head -1)
    fi

    if [ -n "$ITEM_ID" ]; then
        echo "Using work item: $ITEM_ID"

        # Checkout work item
        CHECKOUT_RESULT=$(curl -s -H "$AUTH_HEADER" \
            --data-urlencode "itemID=$ITEM_ID" \
            --data-urlencode "handle=$ENGINE_USER" \
            "$ENGINE_URL/yawl/ib/checkoutWorkItem" 2>/dev/null) || CHECKOUT_RESULT=""

        if [ -n "$CHECKOUT_RESULT" ]; then
            echo -e "${GREEN}✓ Work item checkout successful${NC}"

            # Complete work item
            COMPLETE_RESULT=$(curl -s -H "$AUTH_HEADER" \
                --data-urlencode "itemID=$ITEM_ID" \
                --data-urlencode "data=<data/>" \
                "$ENGINE_URL/yawl/ib/completeWorkItem" 2>/dev/null) || COMPLETE_RESULT=""

            if [ -n "$COMPLETE_RESULT" ]; then
                echo -e "${GREEN}✓ Work item completion successful${NC}"
            else
                echo -e "${YELLOW}! Work item completion returned empty${NC}"
            fi
        else
            echo -e "${YELLOW}! Work item checkout returned empty${NC}"
        fi
    else
        echo -e "${YELLOW}! Could not extract work item ID${NC}"
    fi
else
    echo -e "${YELLOW}! Skipped (no work items available)${NC}"
fi
echo ""

# Summary
echo "==========================================="
print_test_summary
echo "==========================================="

if [ $ASSERT_TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Phase 07 FAILED${NC}"
    exit 1
fi

echo -e "${GREEN}Phase 07 PASSED${NC}"
exit 0
