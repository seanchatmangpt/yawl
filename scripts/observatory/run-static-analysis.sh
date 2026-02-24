#!/usr/bin/env bash
# ==========================================================================
# run-static-analysis.sh — Run static analysis using Docker Compose
#
# This script builds the static analysis image and runs SpotBugs, PMD,
# and Checkstyle analysis inside Docker containers to generate reports
# for the YAWL observatory.
#
# Usage:
#   ./scripts/observatory/run-static-analysis.sh
#
# Output:
#   - target/spotbugsXml.xml (SpotBugs report)
#   - target/pmd.xml (PMD report)
#   - target/checkstyle-result.xml (Checkstyle report)
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Source utility functions
source "${LIB_DIR}/util.sh"

cd "$REPO_ROOT"

log_info "Starting YAWL Static Analysis with Docker Compose..."

# Build the static analysis image
log_info "Building static analysis image..."
docker build -f Dockerfile.static-analysis.simple -t yawl/static-analysis:latest .

# Run static analysis using the correct 'analysis' profile
log_info "Running static analysis tools..."
docker compose -f docker-compose.static-analysis.yml up static-analysis

# Check if reports were generated
log_info "Checking generated reports..."

REPORTS_OK=true
for report in target/spotbugsXml.xml target/pmd.xml target/checkstyle-result.xml; do
    if [[ -f "$report" ]]; then
        log_info "Generated: $report ($(wc -l < "$report") lines)"
    else
        log_error "Missing: $report"
        REPORTS_OK=false
    fi
done

if [[ "$REPORTS_OK" == true ]]; then
    log_ok "Static analysis completed successfully!"
    log_info "Run the observatory to generate reports:"
    log_info "  ./scripts/observatory/observatory.sh"
else
    log_error "Static analysis failed - some reports are missing"
    log_info "Troubleshooting:"
    log_info "  1. Ensure Docker is running"
    log_info "  2. Check Maven can compile: mvn clean compile -P analysis"
    log_info "  3. Check static analysis manually: mvn spotbugs:spotbugs pmd:pmd checkstyle:check -P analysis"
    exit 1
fi
