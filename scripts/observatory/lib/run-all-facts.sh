#!/bin/bash

# Run all fact generators in sequence
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FACTS_DIR="docs/v6/latest/facts"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# List of working fact generators
FACT_GENERATORS=(
    "generate-modules-facts.sh"
    "generate-tests-facts.sh"
    "generate-gates-facts.sh"
)

# Run individual fact generator
run_fact_generator() {
    local generator="$1"
    local full_path="$SCRIPT_DIR/$generator"
    
    log_info "Running: $generator"
    
    if [[ -f "$full_path" ]]; then
        if "$full_path"; then
            log_info "Completed: $generator"
            return 0
        else
            log_error "Failed: $generator"
            return 1
        fi
    else
        log_warn "Generator not found: $full_path"
        return 0
    fi
}

# Main execution
log_info "Starting fact generation at $TIMESTAMP"

# Initialize results tracking
total_generators=${#FACT_GENERATORS[@]}
success_count=0
failed_count=0
failed_generators=()

# Run each generator in sequence
for generator in "${FACT_GENERATORS[@]}"; do
    if run_fact_generator "$generator"; then
        ((success_count++))
    else
        ((failed_count++))
        failed_generators+=("$generator")
    fi
done

# Summary
log_info "Fact generation complete"
log_info "Results: $success_count/$total_generators successful"

if [[ $failed_count -gt 0 ]]; then
    log_error "Failed generators ($failed_count):"
    for generator in "${failed_generators[@]}"; do
        echo "  - $generator"
    done
    exit 1
fi

# Verify generated files
json_count=$(find "$FACTS_DIR" -name "*.json" -not -name "observatory-*.json" | wc -l)
log_info "Generated $json_count JSON files in $FACTS_DIR"

# List generated files
log_info "Generated files:"
find "$FACTS_DIR" -name "*.json" -not -name "observatory-*.json" | sort

exit 0
