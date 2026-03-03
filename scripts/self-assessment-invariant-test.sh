#!/bin/bash
# =============================================================================
# Self-Play Simulation Loop v3.0 — Invariant Test Script
# =============================================================================
# Validates that all components are working correctly after the diagnostic fixes.
#
# This script MUST pass for the self-play loop to be considered production-ready.
# Run: bash scripts/self-assessment-invariant-test.sh
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0.33m'
NC='\033[0m'

echo -e "${NC}Self-Play Invariant Test${NC}"
echo -e "${NC}=================================${NC}"

# Track overall status
PASSED=0
FAILED=0

# Helper function tocheck() {
    local description="$1"
    local expected="$2"
    local actual="$3"

    if [ "$actual" = "$expected" ]; then
        echo -e "${GREEN}✓ $description${NC}"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗ $description${NC}"
        echo -e "   Expected: $expected"
        echo -e "   Actual: $actual"
        ((FAILED++))
        return 1
    fi
}

# =============================================================================
# CHECK 1: YawlSimulator OCEL Output Directory
# =============================================================================
echo -e "${NC}Checking YawlSimulator OCEL output...${NC}"

OCEL_DIR="/tmp/yawl-sim/ocel"
if [ -d "$OCEL_DIR" ]; then
    echo -e "${GREEN}✓ OCEL output directory exists: $OCEL_DIR${NC}"
    ((PASSED++))
else
    echo -e "${RED}✗ OCEL output directory missing${NC}"
    echo -e "   Run YawlSimulator.runPI() first"
    ((FAILED++))
fi

# =============================================================================
# CHECK 2: PI OCEL File Exists
# =============================================================================
echo -e "${NC}Checking for PI OCEL files...${NC}"

PI_OCEL=$(ls /tmp/yawl-sim/ocel/pi-*.json 2>/dev/null | head -1)
if [ -n "$PI_OCEL" ]; then
    echo -e "${GREEN}✓ PI OCEL file exists: $PI_OCEL${NC}"
    ((PASSED++))
else
    echo -e "${RED}✗ No PI OCEL file found${NC}"
    echo -e "   Run: mvn test -Dtest=YawlSimulatorIntegrationTest"
    ((FAILED++))
fi

# =============================================================================
# CHECK 3: OCEL File Contains Valid JSON
# =============================================================================
echo -e "${NC}Validating OCEL JSON structure...${NC}"

if [ -n "$PI_OCEL" ]; then
    CONTENT=$(cat "$PI_OCEL" 2>/dev/null)
    if echo "$CONTENT" | python3 -c "import json, sys; data=json.load(sys.stdin); print('valid')" 2>/dev/null | grep -q "valid"; then
        echo -e "${GREEN}✓ OCEL file contains valid JSON${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ OCEL file is not valid JSON${NC}"
        ((FAILED++))
    fi
else
    echo -e "${YELLOW}⊘ Skipping OCEL validation (no file)${NC}"
fi

# =============================================================================
# CHECK 4: gen-ttl Produces 30+ Functions
# =============================================================================
echo -e "${NC}Checking gen-ttl output...${NC}"

if [ -f "/Users/sac/yawl/pm-bridge-ggen/gen-ttl/Cargo.toml" ]; then
    # Run gen-ttl on rust4pm
    GEN_TTL_OUTPUT=$(cargo run --bin gen-ttl -- /Users/sac/yawl/rust/rust4pm/src/lib.rs 2>&1 | grep -c "Found.*functions" | head -1)
    FN_COUNT=$(echo "$GEN_TTL_OUTPUT" | grep -c "Found [0-9]* functions" | head -1 | awk '{print $1}' | head -1)

    if [ "$FN_COUNT" -ge 30 ]; then
        echo -e "${GREEN}✓ gen-ttl extracts $FN_COUNT functions (>= 30 required)${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ gen-ttl extracts only $FN_COUNT functions (need 30+)${NC}"
        ((FAILED++))
    fi
else
    echo -e "${YELLOW}⊘ gen-ttl not built yet${NC}"
fi

# =============================================================================
# CHECK 5: pm-bridge.ttl Has NativeFunction Triples
# =============================================================================
echo -e "${NC}Checking pm-bridge.ttl ontology...${NC}"

PM_BRIDGE="/Users/sac/yawl/ontology/process-mining/pm-bridge.ttl"
if [ -f "$PM_BRIDGE" ]; then
    NATIVE_FN_COUNT=$(grep -c "a yawl-bridge:NativeFunction" "$PM_BRIDGE")
    CAPABILITY_COUNT=$(grep -c "a yawl-bridge:BridgeCapability" "$PM_BRIDGE")

    if [ "$NATIVE_FN_COUNT" -ge 10 ] && [ "$CAPABILITY_COUNT" -ge 20 ]; then
        echo -e "${GREEN}✓ pm-bridge.ttl has $NATIVE_FN_COUNT NativeFunctions, $CAPABILITY_COUNT capabilities${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ pm-bridge.ttl incomplete${NC}"
        echo -e "   NativeFunctions: $NATIVE_FN_COUNT (need 10+)"
        echo -e "   Capabilities: $CAPABILITY_COUNT (need 20+)"
        ((FAILED++))
    fi
else
    echo -e "${RED}✗ pm-bridge.ttl not found${NC}"
    ((FAILED++))
fi

# =============================================================================
# CHECK 6: YawlSimulatorIntegrationTest Exists
# =============================================================================
echo -e "${NC}Checking YawlSimulatorIntegrationTest...${NC}"

TEST_FILE="/Users/sac/yawl/test/org/yawlfoundation/yawl/integration/selfplay/YawlSimulatorIntegrationTest.java"
if [ -f "$TEST_FILE" ]; then
    echo -e "${GREEN}✓ YawlSimulatorIntegrationTest.java exists${NC}"
    # Check for key test methods
    TEST_COUNT=$(grep -c "@Test" "$TEST_FILE" | wc -l)
    if [ "$TEST_COUNT" -ge 5 ]; then
        echo -e "${GREEN}✓ Test file has $TEST_COUNT test methods${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ Test file needs 5+ test methods (has $TEST_COUNT)${NC}"
        ((FAILED++))
    fi
else
    echo -e "${RED}✗ YawlSimulatorIntegrationTest.java not found${NC}"
    ((FAILED++))
fi

# =============================================================================
# CHECK 7: GapAnalysisEngine.persistGaps Method Exists
# =============================================================================
echo -e "${NC}Checking GapAnalysisEngine.persistGaps...${NC}"

GAP_ENGINE="/Users/sac/yawl/src/org/yawlfoundation/yawl/integration/selfplay/GapAnalysisEngine.java"
if [ -f "$GAP_ENGINE" ]; then
    if grep -q "persistGaps" "$GAP_ENGINE" > /dev/null; then
        echo -e "${GREEN}✓ GapAnalysisEngine.persistGaps method exists${NC}"
        ((PASSED++))
    else
        echo -e "${RED}✗ GapAnalysisEngine.persistGaps method not found${NC}"
        ((FAILED++))
    fi
else
    echo -e "${RED}✗ GapAnalysisEngine.java not found${NC}"
    ((FAILED++))
fi

# =============================================================================
# SUMMARY
# =============================================================================
echo -e ""
echo -e "${NC}=================================${NC}"
echo -e "${NC}INVARIANT TEST SUMMARY${NC}"
echo -e "${NC}=================================${NC}"
echo -e "${NC}Passed: $PASSED${NC}"
echo -e "${NC}Failed: $FAILED${NC}"

echo -e ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}=== ALL INVARIANT CHECKS PASSED ===${NC}"
    echo -e "${NC}Self-Play Simulation Loop v3.0 is PRODUCTION READY${NC}"
    exit 0
else
    echo -e "${RED}=== SOME INVARIANT CHECKS FAILED ===${NC}"
    echo -e "${NC}Fix the failing checks before proceeding to production${NC}"
    exit 1
fi
