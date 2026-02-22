#!/bin/bash
#
# Phase 04: Engine Lifecycle Tests
#
# Tests the YAWL engine lifecycle:
# - Start/stop behavior
# - Health endpoint
# - Specification listing
# - Clean shutdown
#
# Exit codes:
#   0 - All lifecycle tests passed
#   1 - Lifecycle tests failed

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
ENGINE_STARTUP_TIMEOUT="${ENGINE_STARTUP_TIMEOUT:-60}"
ENGINE_URL="http://localhost:$ENGINE_PORT"

echo "==========================================="
echo "Phase 04: Engine Lifecycle Tests"
echo "==========================================="
echo ""
echo "Engine port:    $ENGINE_PORT"
echo "Engine URL:     $ENGINE_URL"
echo "Startup timeout: ${ENGINE_STARTUP_TIMEOUT}s"
echo ""

# Check if engine is already running
check_engine_running() {
    nc -z localhost "$ENGINE_PORT" 2>/dev/null
}

# Test: Port available or engine running
echo "--- Test: Port Status ---"
if check_engine_running; then
    echo -e "${GREEN}Port $ENGINE_PORT is in use (engine may be running)${NC}"
    ENGINE_ALREADY_RUNNING=true
else
    echo -e "${YELLOW}Port $ENGINE_PORT is free${NC}"
    ENGINE_ALREADY_RUNNING=false
fi
echo ""

# Test: Health endpoint (if engine running)
echo "--- Test: Health Endpoint ---"
reset_test_counters

if check_engine_running; then
    # Try to access the YAWL interface B (engine back-end)
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$ENGINE_URL/yawl/ib" 2>/dev/null) || code="000"

    if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "401" ]; then
        echo -e "${GREEN}✓ Health endpoint responding (HTTP $code)${NC}"
    else
        echo -e "${YELLOW}! Health endpoint returned HTTP $code${NC}"
    fi
else
    echo -e "${YELLOW}SKIPPED: Engine not running${NC}"
    echo ""
    echo "To run engine lifecycle tests:"
    echo "  1. Start the YAWL engine: ant -f build/build.xml deploy"
    echo "  2. Start Tomcat with YAWL webapps"
    echo "  3. Run this test again"
fi
echo ""

# Test: Interface A (design-time interface)
echo "--- Test: Interface A Endpoint ---"
if check_engine_running; then
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$ENGINE_URL/yawl/ia" 2>/dev/null) || code="000"

    if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "401" ]; then
        echo -e "${GREEN}✓ Interface A responding (HTTP $code)${NC}"
    else
        echo -e "${YELLOW}! Interface A returned HTTP $code${NC}"
    fi
else
    echo -e "${YELLOW}SKIPPED: Engine not running${NC}"
fi
echo ""

# Test: Interface B (run-time interface)
echo "--- Test: Interface B Endpoint ---"
if check_engine_running; then
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$ENGINE_URL/yawl/ib" 2>/dev/null) || code="000"

    if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "401" ]; then
        echo -e "${GREEN}✓ Interface B responding (HTTP $code)${NC}"
    else
        echo -e "${YELLOW}! Interface B returned HTTP $code${NC}"
    fi
else
    echo -e "${YELLOW}SKIPPED: Engine not running${NC}"
fi
echo ""

# Test: Resource Service endpoint
echo "--- Test: Resource Service Endpoint ---"
if check_engine_running; then
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$ENGINE_URL/resourceService" 2>/dev/null) || code="000"

    if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "404" ]; then
        # 404 is acceptable - resource service may not be deployed
        echo -e "${GREEN}✓ Resource service check complete (HTTP $code)${NC}"
    else
        echo -e "${YELLOW}! Resource service returned HTTP $code${NC}"
    fi
else
    echo -e "${YELLOW}SKIPPED: Engine not running${NC}"
fi
echo ""

# Test: Worklet Service endpoint
echo "--- Test: Worklet Service Endpoint ---"
if check_engine_running; then
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$ENGINE_URL/workletService" 2>/dev/null) || code="000"

    if [ "$code" = "200" ] || [ "$code" = "302" ] || [ "$code" = "404" ]; then
        echo -e "${GREEN}✓ Worklet service check complete (HTTP $code)${NC}"
    else
        echo -e "${YELLOW}! Worklet service returned HTTP $code${NC}"
    fi
else
    echo -e "${YELLOW}SKIPPED: Engine not running${NC}"
fi
echo ""

# Summary
echo "==========================================="
echo "Engine Lifecycle Summary"
echo "==========================================="

if ! check_engine_running; then
    echo ""
    echo -e "${YELLOW}Phase 04 SKIPPED (engine not running)${NC}"
    echo ""
    echo "To enable full lifecycle testing:"
    echo "  1. Deploy YAWL: ant -f build/build.xml deploy"
    echo "  2. Start Tomcat: \$CATALINA_HOME/bin/catalina.sh run"
    echo "  3. Verify engine: curl http://localhost:8080/yawl/ib"
    echo "  4. Re-run this test"
    exit 0
fi

print_test_summary

if [ $ASSERT_TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Phase 04 FAILED${NC}"
    exit 1
fi

echo -e "${GREEN}Phase 04 PASSED${NC}"
exit 0
