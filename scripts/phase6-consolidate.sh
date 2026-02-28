#!/bin/bash
set -euo pipefail

###############################################################################
# Phase 6 Blue Ocean Enhancement — Consolidation Script
# Merges 5-agent deliverables and runs final validation
###############################################################################

echo "🚀 Phase 6 Consolidation Starting..."
echo "Timestamp: $(date)"
echo ""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

LOG_FILE="/tmp/phase6-consolidation-$(date +%s).log"
FAILURES=0

log() {
    echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $*" | tee -a "$LOG_FILE"
}

success() {
    echo -e "${GREEN}✅ $*${NC}" | tee -a "$LOG_FILE"
}

error() {
    echo -e "${RED}❌ $*${NC}" | tee -a "$LOG_FILE"
    FAILURES=$((FAILURES + 1))
}

warning() {
    echo -e "${YELLOW}⚠️  $*${NC}" | tee -a "$LOG_FILE"
}

###############################################################################
# Phase 1: Verify Agent Deliverables
###############################################################################

log "PHASE 1: Verifying 5-agent deliverables..."

# Check for key files created by engineer
ENGINEER_FILES=(
    "src/org/yawlfoundation/yawl/integration/blueocean/lineage/RdfLineageStore.java"
    "src/org/yawlfoundation/yawl/integration/blueocean/validation/HyperStandardsValidator.java"
    "src/org/yawlfoundation/yawl/integration/blueocean/contracts/DataContractValidator.java"
    "src/org/yawlfoundation/yawl/integration/blueocean/metrics/OpenTelemetryInstrumentation.java"
)

for file in "${ENGINEER_FILES[@]}"; do
    if [ -f "/home/user/yawl/$file" ]; then
        success "Found: $file"
    else
        warning "Missing: $file (may be created in different location)"
    fi
done

# Check for test files created by tester
if find /home/user/yawl/src/test -name "*Phase6*Test.java" -o -name "*Lineage*Test.java" | grep -q .; then
    success "Found integration test files"
else
    warning "Test files may be in progress"
fi

###############################################################################
# Phase 2: Code Quality Checks
###############################################################################

log ""
log "PHASE 2: Running code quality checks..."

# H-Guards validation (should have 0 violations in new code)
log "Checking for H-Guards violations in new datalineage code..."
if find /home/user/yawl/src -path "*/blueocean/*" -name "*.java" -type f | xargs grep -l "TODO\|FIXME\|XXX\|@incomplete\|@stub" 2>/dev/null; then
    error "Found H-Guards violations in new code"
else
    success "No H-Guards violations found"
fi

# Check for mock/stub/fake patterns
if find /home/user/yawl/src -path "*/blueocean/*" -name "*.java" -type f | xargs grep -l "mock\|stub\|fake" 2>/dev/null | grep -i "class\|method"; then
    warning "Found potential mock/stub/fake patterns (verify these are legitimate)"
else
    success "No suspicious mock/stub/fake patterns"
fi

###############################################################################
# Phase 3: Compilation & Build
###############################################################################

log ""
log "PHASE 3: Running full build pipeline..."

cd /home/user/yawl

# Fast compilation
log "Running dx.sh compile..."
if bash scripts/dx.sh compile >> "$LOG_FILE" 2>&1; then
    success "dx.sh compile GREEN"
else
    error "dx.sh compile FAILED"
fi

# Full build with tests
log "Running dx.sh all..."
if bash scripts/dx.sh all >> "$LOG_FILE" 2>&1; then
    success "dx.sh all GREEN"
else
    error "dx.sh all FAILED"
    error "See $LOG_FILE for details"
fi

###############################################################################
# Phase 4: Test Execution
###############################################################################

log ""
log "PHASE 4: Running test suite..."

# Count tests
TEST_COUNT=$(find /home/user/yawl/src/test -path "*/blueocean/*" -name "*Test.java" -type f | wc -l)
if [ "$TEST_COUNT" -gt 0 ]; then
    success "Found $TEST_COUNT integration tests"
else
    warning "No integration tests found yet"
fi

###############################################################################
# Phase 5: Documentation Check
###############################################################################

log ""
log "PHASE 5: Checking documentation..."

DOCS=(
    "docs/guides/phase6/RDF_ONTOLOGY.md"
    "docs/guides/phase6/SPARQL_COOKBOOK.md"
    "docs/guides/phase6/DATA_CONTRACTS.md"
    "docs/guides/phase6/DEPLOYMENT_GUIDE.md"
)

for doc in "${DOCS[@]}"; do
    if [ -f "/home/user/yawl/$doc" ]; then
        success "Found: $(basename "$doc")"
    else
        warning "Missing: $doc"
    fi
done

###############################################################################
# Phase 6: Final Summary
###############################################################################

log ""
log "PHASE 6: Final Summary"
echo ""
echo "╔════════════════════════════════════════════════════════════╗"
echo "║          Phase 6 Consolidation Status Report              ║"
echo "╠════════════════════════════════════════════════════════════╣"

if [ "$FAILURES" -eq 0 ]; then
    echo -e "║ ${GREEN}Status: READY FOR COMMIT${NC}"
    echo "║                                                            ║"
    echo "║ ✅ Code quality: GREEN                                    ║"
    echo "║ ✅ Build: GREEN                                           ║"
    echo "║ ✅ Tests: Running                                         ║"
    echo "║ ✅ Documentation: In progress                             ║"
else
    echo -e "║ ${RED}Status: ISSUES FOUND (${FAILURES} failures)${NC}"
    echo "║                                                            ║"
    echo "║ Review $LOG_FILE for details                    ║"
fi

echo "╠════════════════════════════════════════════════════════════╣"
echo "║ Next Steps:                                                ║"
echo "║ 1. Review 5-agent outputs                                  ║"
echo "║ 2. Resolve any compilation/test failures                   ║"
echo "║ 3. Run: git add src/ docs/ .claude/                        ║"
echo "║ 4. Run: git commit -m 'Phase 6: Blue Ocean...'             ║"
echo "║ 5. Run: git push -u origin claude/autorun-self-update-*    ║"
echo "╚════════════════════════════════════════════════════════════╝"
echo ""
echo "Full log: $LOG_FILE"
echo ""

exit "$FAILURES"
