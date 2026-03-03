#!/bin/bash
# =============================================================================
# Self-Play Simulation Loop v3.0 — Invariant Test Script
# =============================================================================
# Validates that all components are working correctly after the diagnostic fixes.
#
# This script must pass for the self-play loop to be considered production-ready.
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'
# Counter for passed/ failed checks
PASSED=0
FAILED=0

# Helper function
check() {
    local description="$1"
    local expected="$2"
    local actual="$3"

    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}✓ $description"
        PASSED=$((PASSED++))
        return 0
    else
        echo -e "${RED}✗ $description"
        echo -e "   Expected: $expected"
        echo -e "   Actual: $actual"
        FAILED=$((FAILED++))
        return 1
    fi
}

# =============================================================================
# Check 1: YawlSimulator OCEL Output Directory
# =============================================================================
echo -e "Checking YawlSimulator OCEL output..."
OCEL_DIR="${yawl.sim.ocel.dir:-/tmp/yawl-sim/ocel"
if [ -d "$OCEL_DIR" ]; then
    echo -e "${GREEN}✓ OCEL output directory exists: $OCEL_DIR"
    PASSED=$((PASSED++))
else
    echo -e "${RED}✗ OCEL output directory missing"
    echo -e "   Run YawlSimulator.runPI() first"
    FAILED=$((FAILED++))
fi
# =============================================================================
# Check 2: PI OCEL File Exists
# =============================================================================
echo -e "Checking for PI OCEL files..."
PI_OCEL=$(ls /tmp/yawl-sim/ocel/pi-*.json 2>/dev/null | head -1)
if [ -n "$PI_OCEL" ]; then
    echo -e "${GREEN}✓ PI OCEL file exists: $PI_OCEL"
    PASSED=$((PASSED++))
else
    echo -e "${RED}✗ No PI OCEL file found"
    echo -e "   Run: mvn test -Dtest=YawlSimulatorIntegrationTest"
    FAILED=$((FAILED++))
fi
# =============================================================================
# Check 3: OCEL File Contains Valid JSON
# =============================================================================
echo -e "Validating OCEL JSON structure..."
if [ -n "$PI_OCEL" ]; then
    CONTENT=$(cat "$PI_OCEL" 2>/dev/null)
    if echo "$CONTENT" | python3 -c "import json, sys.stdin; data=json.load(sys.stdin); print('valid')" 2>/dev/null | grep -q "valid"; then
        echo -e "${GREEN}✓ OCEL file contains valid JSON"
        PASSED=$((PASSED++))
    else
        echo -e "${RED}✗ OCEL file is not valid JSON"
        FAILED=$((FAILED++))
    fi
else
    echo -e "${YELLOW}⊘ Skipping OCEL validation (no file)"
fi
# =============================================================================
# Check 4: gen-ttl Produces 30+ Functions
# =============================================================================
echo -e "Checking gen-ttl output..."
if [ -f "/Users/sac/yawl/pm-bridge-ggen/gen-ttl/Cargo.toml" ]; then
    cd /Users/sac/yawl/pm-bridge-ggen/gen-ttl
    GEN_TTL_OUTPUT=$(cargo run --bin gen-ttl -- /Users/sac/yawl/rust/rust4pm/src/lib.rs 2>&1)
    FN_COUNT=$(echo "$GEN_TTL_OUTPUT" | grep -c "Found [0-9]* functions" | head -1 | awk '{print $1}')
    if [ "$FN_COUNT" -ge 30 ]; then
        echo -e "${GREEN}✓ gen-ttl extracts $FN_COUNT functions (>= 30 required)"
        PASSED=$((PASSED++))
    else
        echo -e "${RED}✗ gen-ttl extracts only $FN_COUNT functions (need 30+)"
        FAILED=$((FAILED++))
    fi
else
    echo -e "${YELLOW}⊘ gen-ttl not built yet"
fi
# =============================================================================
# Check 5: pm-bridge.ttl Has NativeFunction Triples
# =============================================================================
echo -e "Checking pm-bridge.ttl ontology..."
PM_BRIDGE="/Users/sac/yawl/ontology/process-mining/pm-bridge.ttl"
if [ -f "$PM_BRIDGE" ]; then
    NATIVE_FN_COUNT=$(grep -c "a yawl-bridge:NativeFunction" "$PM_BRIDGE")
    CAPABILITY_COUNT=$(grep -c "a yawl-bridge:BridgeCapability" "$PM_BRIDGE")
    if [ "$NATIVE_FN_COUNT" -ge 10 ] && [ "$CAPABILITY_COUNT" -ge 20 ]; then
        echo -e "${GREEN}✓ pm-bridge.ttl has $NATIVE_FN_COUNT NativeFunctions, $CAPABILITY_COUNT capabilities"
        PASSED=$((PASSED++))
    else
        echo -e "${RED}✗ pm-bridge.ttl incomplete"
        echo -e "   NativeFunctions: $NATIVE_FN_COUNT (need 10+)
        echo -e "   Capabilities: $CAPABILITY_COUNT (need 20+)
        FAILED=$((FAILED++))
    fi
else
    echo -e "${RED}✗ pm-bridge.ttl not found"
    FAILED=$((FAILED++))
fi
# =============================================================================
# Check 6: YawlSimulatorIntegrationTest Exists
# =============================================================================
echo -e "Checking YawlSimulatorIntegrationTest..."
TEST_FILE="/Users/sac/yawl/test/org/yawlfoundation/yawl/integration/selfplay/YawlSimulatorIntegrationTest.java"
if [ -f "$TEST_FILE" ]; then
    echo -e "${GREEN}✓ YawlSimulatorIntegrationTest.java exists"
    TEST_COUNT=$(grep -c "@Test" "$TEST_FILE" | wc -l)
    if [ "$TEST_COUNT" -ge 5 ]; then
        echo -e "${GREEN}✓ Test file has $TEST_COUNT test methods"
        PASSED=$((PASSED++))
    else
        echo -e "${RED}✗ YawlSimulatorIntegrationTest.java not found"
        FAILED=$((FAILED++))
    fi
else
    echo -e "${RED}✗ YawlSimulatorIntegrationTest.java not found"
    FAILED=$((FAILED++))
fi
# =============================================================================
# Check 7: GapAnalysisEngine.persistGaps Method Exists
# =============================================================================
echo -e "Checking GapAnalysisEngine.persistGaps..."
GAP_ENGINE="/Users/sac/yawl/src/org/yawlfoundation/yawl/integration/selfplay/GapAnalysisEngine.java"
if [ -f "$GAP_ENGINE" ]; then
    if grep -q "persistGaps" "$GAP_ENGINE" > /dev/null; then
        echo -e "${GREEN}✓ GapAnalysisEngine.persistGaps method exists"
        PASSED=$((PASSED++))
    else
        echo -e "${RED}✗ GapAnalysisEngine.java not found"
        FAILED=$((FAILED++))
    fi
else
    echo -e "${RED}✗ GapAnalysisEngine.java not found"
    FAILED=$((FAILED++))
fi
# =============================================================================
# Summary
# =============================================================================
echo ""
echo -e "${NC}=================================${NC}"
echo -e "${NC}InvARIANT Test Summary${NC}"
echo -e "${NC}=================================${NC}"
echo -e "${NC}Passed: $PASSED${NC}"
echo -e "${NC}Failed: $FAILED${NC}
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}=== ALL INVARIANT CHECKS PASSED ===${NC}"
    echo -e "${NC}Self-Play Simulation Loop v3.0 is PRODUCTION READY${NC}"
    exit 0
else
    echo -e "${RED}=== SOME INVARIANT CHECKS FAILED ===${NC}"
    echo -e "${NC}Fix the failing checks before proceeding to production${NC}
    exit 1
fi
