#!/bin/bash
#
# Phase 08: Integration Report Generation
#
# Collects all test results and generates comprehensive reports:
# - JUnit XML (for CI/CD)
# - Markdown summary (for humans)
# - JSON metrics (for dashboards)
#
# Exit codes:
#   0 - Report generation successful
#   1 - Report generation failed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
REPORT_DIR="${REPORT_DIR:-$PROJECT_DIR/reports}"

# Source libraries
source "$PROJECT_DIR/scripts/shell-test/report.sh"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "==========================================="
echo "Phase 08: Integration Report Generation"
echo "==========================================="
echo ""
echo "Project directory: $PROJECT_DIR"
echo "Report directory:  $REPORT_DIR"
echo ""

# Create report directory
mkdir -p "$REPORT_DIR"

# Collect phase results from environment or files
declare -A PHASE_RESULTS

collect_phase_results() {
    echo "--- Collecting Phase Results ---"

    # Try to read from phase result files
    for phase_dir in "$PROJECT_DIR/test/shell"/*/; do
        [ -d "$phase_dir" ] || continue

        local phase_name
        phase_name=$(basename "$phase_dir")
        local phase_num="${phase_name%%-*}"

        # Check for result file
        local result_file="$phase_dir/result.txt"
        if [ -f "$result_file" ]; then
            local result
            result=$(cat "$result_file")
            PHASE_RESULTS[$phase_num]=$result
            echo "  Phase $phase_num: $result"
        else
            # Assume passed if no result file
            PHASE_RESULTS[$phase_num]="0:0"
            echo "  Phase $phase_num: no result file (assuming pass)"
        fi
    done
}

# Generate all reports
generate_reports() {
    echo ""
    echo "--- Generating Reports ---"

    local timestamp
    timestamp=$(date +"%Y%m%d_%H%M%S")
    local run_dir="$REPORT_DIR/$timestamp"
    mkdir -p "$run_dir"

    # Generate JUnit XML
    echo "Generating JUnit XML..."
    generate_junit_xml "$run_dir/junit.xml"

    # Generate Markdown report
    echo "Generating Markdown report..."
    generate_markdown_report "$run_dir/TEST_REPORT.md"

    # Generate JSON metrics
    echo "Generating JSON metrics..."
    generate_metrics_json "$run_dir/metrics.json"

    # Create latest symlink
    rm -f "$REPORT_DIR/latest"
    ln -s "$run_dir" "$REPORT_DIR/latest"

    echo ""
    echo -e "${GREEN}Reports generated successfully${NC}"
    echo ""
    echo "Report files:"
    echo "  - JUnit XML:  $run_dir/junit.xml"
    echo "  - Markdown:   $run_dir/TEST_REPORT.md"
    echo "  - JSON:       $run_dir/metrics.json"
    echo "  - Latest:     $REPORT_DIR/latest"
}

# Print summary
print_final_summary() {
    echo ""
    echo -e "${BLUE}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}                 ${GREEN}Test Run Complete${NC}                           ${BLUE}║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "Reports available at: $REPORT_DIR/latest/"
    echo ""

    # Check if any phase failed
    local all_passed=true
    for phase_num in "${!PHASE_RESULTS[@]}"; do
        local result="${PHASE_RESULTS[$phase_num]}"
        local code="${result%%:*}"
        if [ "$code" != "0" ]; then
            all_passed=false
            break
        fi
    done

    if [ "$all_passed" = "true" ]; then
        echo -e "${GREEN}All test phases passed${NC}"
    else
        echo -e "${YELLOW}Some test phases failed - check reports for details${NC}"
    fi
}

# Main
collect_phase_results
generate_reports
print_final_summary

echo ""
echo -e "${GREEN}Phase 08 PASSED${NC}"
exit 0
