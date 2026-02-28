#!/bin/bash

# Observatory Main Orchestrator
# Generates facts, diagrams, receipts, and index for YAWL v6.0.0

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$SCRIPT_DIR/lib"
FACTS_DIR="docs/v6/latest/facts"
DIAGRAMS_DIR="docs/v6/latest/diagrams"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Functions
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_section() {
    echo -e "\n${YELLOW}=== $1 ===${NC}"
}

# Cleanup function
cleanup() {
    if [[ $? -ne 0 && -f "$SCRIPT_DIR/.observatory_running" ]]; then
        log_error "Observatory execution failed"
        rm -f "$SCRIPT_DIR/.observatory_running"
    fi
}

trap cleanup EXIT

# Create lock file
if [[ -f "$SCRIPT_DIR/.observatory_running" ]]; then
    log_error "Observatory is already running"
    exit 1
fi
touch "$SCRIPT_DIR/.observatory_running"

# Ensure directories exist
mkdir -p "$FACTS_DIR" "$DIAGRAMS_DIR"

# Parse arguments
MODE=${1:-"all"}
VERBOSE=${2:-""}

if [[ "$VERBOSE" == "--verbose" ]]; then
    set -x
fi

log_info "Starting Observatory at $TIMESTAMP"
log_info "Mode: $MODE"

# Phase 1: Facts Generation
if [[ "$MODE" == "all" || "$MODE" == "--facts" || "$MODE" == "facts" ]]; then
    log_section "Phase 1: Facts Generation"
    
    log_info "Running all fact generators..."
    "$LIB_DIR/run-all-facts.sh"
    
    log_info "Validating generated facts..."
    "$LIB_DIR/validate-facts.sh"
    
    log_info "Facts generation completed"
fi

# Phase 2: Diagrams Generation
if [[ "$MODE" == "all" || "$MODE" == "--diagrams" || "$MODE" == "diagrams" ]]; then
    log_section "Phase 2: Diagrams Generation"
    
    log_info "Generating diagrams..."
    "$LIB_DIR/generate-diagrams.sh"
    
    log_info "Diagrams generation completed"
fi

# Phase 3: Receipt Generation
if [[ "$MODE" == "all" || "$MODE" == "--receipt" || "$MODE" == "receipt" ]]; then
    log_section "Phase 3: Receipt Generation"
    
    log_info "Generating observatory receipt..."
    "$LIB_DIR/emit-receipt.sh"
    
    log_info "Receipt generation completed"
fi

# Phase 4: Index Generation
if [[ "$MODE" == "all" || "$MODE" == "--index" || "$MODE" == "index" ]]; then
    log_section "Phase 4: Index Generation"
    
    log_info "Generating observatory index..."
    "$LIB_DIR/generate-index.sh"
    
    log_info "Index generation completed"
fi

# Success
log_section "Observatory Execution Complete"
log_info "Output location: docs/v6/latest/"
log_info "Timestamp: $TIMESTAMP"

# Clean up
rm -f "$SCRIPT_DIR/.observatory_running"

# Show summary
if [[ "$MODE" == "all" ]]; then
    log_info "\nGenerated files:"
    find "$FACTS_DIR" -name "*.json" | sort
    find "$DIAGRAMS_DIR" -name "*.md" -o -name "*.svg" | sort
    if [[ -f "$FACTS_DIR/observatory-receipt.json" ]]; then
        echo "$FACTS_DIR/observatory-receipt.json"
    fi
    if [[ -f "$FACTS_DIR/observatory-index.json" ]]; then
        echo "$FACTS_DIR/observatory-index.json"
    fi
fi

exit 0
