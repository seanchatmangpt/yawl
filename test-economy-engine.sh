#!/bin/bash

# Test Runner for GregVerse Economy Engine
# This script compiles and runs the EconomyEngine tests

set -euo pipefail

# Colors for output
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''
fi

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[PASS]${NC}  $*"; }
error()   { echo -e "${RED}[FAIL]${NC}  $*" >&2; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}----------------------------------------------------------------------${NC}"
    echo -e "${BOLD}${CYAN}  GregVerse Economy Engine Test Runner${NC}"
    echo -e "${BOLD}${CYAN}----------------------------------------------------------------------${NC}"
    echo ""
}

# Navigate to the mcp-a2a-app directory
cd "$(dirname "$0")/yawl-mcp-a2a-app"

header "Step 1: Compiling the Economy Engine"

if mvn clean compile -DskipTests -q; then
    success "Compilation successful"
else
    error "Compilation failed"
    exit 1
fi

header "Step 2: Running Economy Engine Tests"

if mvn test -Dtest=EconomyEngineTest -q; then
    success "All Economy Engine tests passed!"

    header "Step 3: Test Summary"
    echo "The GregVerse Economy Engine implementation is ready and working correctly."
    echo ""
    echo "Features implemented:"
    echo "✓ MarketplaceCurrency - Immutable currency with BigDecimal precision"
    echo "✓ TransactionLedger - Thread-safe transaction recording with hashing"
    echo "✓ PricingEngine - Dynamic pricing with multiple strategies"
    echo "✓ ReputationSystem - Multi-dimensional rating system"
    echo "✓ ServiceContract - Smart contract lifecycle management"
    echo "✓ EconomyEngine - Central coordinator with virtual threads"
    echo "✓ Comprehensive test suite with Chicago TDD methodology"
    echo ""
    echo "Java 25 features used:"
    echo "✓ Records for immutable data structures"
    echo "✓ Virtual threads for high-performance concurrency"
    echo "✓ Comprehensive error handling and validation"

    exit 0
else
    error "Economy Engine tests failed"
    echo "Check the target/surefire-reports/ directory for details."
    exit 1
fi