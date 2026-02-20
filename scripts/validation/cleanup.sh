#!/bin/bash

# Cleanup script for YAWL Validation System
# Removes temporary files, logs, and resets validation state

set -euo pipefail

# Source common library
source "$(dirname "$0")/lib/common.sh"

log_section "Cleaning up YAWL Validation System"

# Remove temporary files
log_info "Removing temporary files..."
find . -name "tmp-*" -type f -delete 2>/dev/null || true
find . -name "*.tmp" -type f -delete 2>/dev/null || true
find . -name ".*.tmp" -type f -delete 2>/dev/null || true

# Remove validation results
log_info "Removing validation results..."
rm -f validation-results.json
rm -f validation-results.xml
rm -f reports/validation/*.json
rm -f reports/validation/*.html
rm -f reports/validation/*.xml

# Remove logs
log_info "Removing logs..."
find . -name "*.log" -type f -delete 2>/dev/null || true
find . -name "logs/*" -type f -delete 2>/dev/null || true

# Remove Docker containers and networks
if command -v docker > /dev/null; then
    log_info "Cleaning up Docker containers..."
    docker stop yawl-engine yawl-engine-prod yawl-postgres 2>/dev/null || true
    docker rm yawl-engine yawl-engine-prod yawl-postgres 2>/dev/null || true
    docker network rm yawl-network 2>/dev/null || true
fi

# Remove generated files
log_info "Removing generated files..."
rm -f .yawl-validation-state
rm -f .validation-cache
rm -rf .tmp-validation

# Reset test results
log_info "Resetting test results..."
reset_test_results

# Clean up reports directory
if [ -d "reports/validation" ]; then
    log_info "Cleaning reports directory..."
    find reports/validation -name "*.md" -type f -delete 2>/dev/null || true
    find reports/validation -name "*.txt" -type f -delete 2>/dev/null || true
fi

log_success "Cleanup completed successfully"
echo
log_info "To start fresh, run:"
echo "  ./scripts/validation/test-validation-system.sh"
echo "  ./scripts/validation/validate-all.sh"
