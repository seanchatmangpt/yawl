#!/bin/bash
#
# Quick Start YAWL Pattern Validation
# Builds engine, starts services, and runs validation
#

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "${RED}ERROR:${NC} $1" >&2
    exit 1
}

success() {
    echo "${GREEN}SUCCESS:${NC} $1"
}

warn() {
    echo "${YELLOW}WARNING:${NC} $1"
}

info() {
    echo "${BLUE}INFO:${NC} $1"
}

log "=== YAWL Quick Start for Pattern Validation ==="

# Step 1: Build YAWL engine if needed
info "Step 1: Checking YAWL engine JAR..."
if [[ ! -f "yawl-engine/target/yawl-engine-6.0.0-Alpha.jar" ]]; then
    log "Building YAWL engine..."
    if ./mvnw clean package -pl yawl-engine -DskipTests; then
        success "YAWL engine built successfully"
    else
        error "Failed to build YAWL engine"
    fi
else
    success "YAWL engine JAR already exists"
fi

# Step 2: Start YAWL engine with development profile
info "Step 2: Starting YAWL engine with H2 database..."
log "Starting services with development profile..."
if docker compose --profile development up -d yawl-engine; then
    success "YAWL engine started successfully"
else
    error "Failed to start YAWL engine"
fi

# Step 3: Wait for engine health
info "Step 3: Waiting for engine to be healthy..."
log "This may take up to 3 minutes..."

if timeout 180 ./scripts/validation/patterns/engine-health.sh; then
    success "Engine is healthy and ready!"
else
    error "Engine health check failed"
fi

# Step 4: Run quick validation of basic patterns
info "Step 4: Running quick validation of basic patterns (WCP 1-5)..."
log "This will validate the 5 basic patterns to ensure everything is working..."

if ./scripts/validation/patterns/validate-basic.sh; then
    success "Basic patterns validation completed!"
else
    warn "Some basic patterns failed - checking if engine is responsive..."

    # Check engine status
    if curl -s http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
        success "Engine is responding, but patterns may need adjustment"
    else
        error "Engine is not responding properly"
    fi
fi

# Step 5: Show validation options
info "Step 5: Pattern validation options available:"
log ""
echo "   ${GREEN}✓${NC} Basic patterns (WCP 1-5):    ./scripts/validation/patterns/validate-basic.sh"
echo "   ${GREEN}✓${NC} All patterns:              ./scripts/validation/patterns/validate-all-patterns.sh"
echo "   ${GREEN}✓${NC} Branching patterns (WCP 6-11): ./scripts/validation/patterns/validate-branching.sh"
echo "   ${GREEN}✓${NC} Multi-instance patterns:      ./scripts/validation/patterns/validate-multiinstance.sh"
echo "   ${GREEN}✓${NC} State-based patterns:          ./scripts/validation/patterns/validate-statebased.sh"
echo "   ${GREEN}✓${NC} Cancellation patterns:        ./scripts/validation/patterns/validate-cancellation.sh"
echo "   ${GREEN}✓${NC} Extended patterns:             ./scripts/validation/patterns/validate-extended.sh"
echo "   ${GREEN}✓${NC} Event-driven patterns:         ./scripts/validation/patterns/validate-eventdriven.sh"
echo "   ${GREEN}✓${NC} AI/ML patterns:               ./scripts/validation/patterns/validate-aiml.sh"
log ""

# Step 6: Generate report if validation was run
if [[ "${1:-}" == "--full" ]]; then
    info "Step 6: Generating full validation report..."
    if ./scripts/validation/patterns/validate-all-patterns.sh; then
        success "Full validation report generated at reports/pattern-validation-report.json"
        # Display summary
        if command -v jq &> /dev/null; then
            log "Validation Summary:"
            jq '.summary' reports/pattern-validation-report.json
        fi
    fi
fi

success "=== Quick Start Complete ==="
log "YAWL engine is running and ready for pattern validation!"
log "Use the commands above to validate specific pattern categories."