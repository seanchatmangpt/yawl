#!/usr/bin/env bash
#
# YAWL Shell Test Runner
#
# Main test execution framework for black-box testing.
# Orchestrates test phases and collects results.
#
# Usage:
#   ./runner.sh [options] [test_directory]
#
# Options:
#   -p, --phase PHASE    Run specific phase only
#   -q, --quick          Run quick tests only (phases 1-3)
#   -v, --verbose        Enable verbose output
#   --no-color           Disable colored output
#   --report-dir DIR     Specify report directory
#   --stop-on-failure    Stop on first failure

set -euo pipefail

# Script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
TEST_DIR="$PROJECT_DIR/test/shell"

# Source libraries
source "$SCRIPT_DIR/assert.sh"
source "$SCRIPT_DIR/http-client.sh"
source "$SCRIPT_DIR/process-manager.sh"

# Colors
if [ "${NO_COLOR:-}" = "1" ] || [ ! -t 1 ]; then
    RED=""
    GREEN=""
    YELLOW=""
    BLUE=""
    CYAN=""
    BOLD=""
    NC=""
else
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
fi

# Configuration
VERBOSE="${VERBOSE:-false}"
STOP_ON_FAILURE="${STOP_ON_FAILURE:-false}"
REPORT_DIR="${REPORT_DIR:-$PROJECT_DIR/reports}"
QUICK_MODE="${QUICK_MODE:-false}"
SPECIFIC_PHASE=""

# Test results
TOTAL_TESTS=0
TOTAL_PASSED=0
TOTAL_FAILED=0
PHASE_RESULTS=()

# Parse arguments
parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            -p|--phase)
                SPECIFIC_PHASE="$2"
                shift 2
                ;;
            -q|--quick)
                QUICK_MODE=true
                shift
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            --no-color)
                NO_COLOR=1
                shift
                ;;
            --report-dir)
                REPORT_DIR="$2"
                shift 2
                ;;
            --stop-on-failure)
                STOP_ON_FAILURE=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                TEST_DIR="$1"
                shift
                ;;
        esac
    done
}

# Show help
show_help() {
    cat <<EOF
YAWL Shell Test Runner

Usage: $0 [options] [test_directory]

Options:
    -p, --phase PHASE      Run specific phase only (01-08)
    -q, --quick            Run quick tests only (phases 1-3)
    -v, --verbose          Enable verbose output
    --no-color             Disable colored output
    --report-dir DIR       Specify report directory
    --stop-on-failure      Stop on first failure
    -h, --help             Show this help message

Test Phases:
    01  Schema Validation
    02  Stub Detection
    03  Build Verification
    04  Engine Lifecycle
    05  A2A Protocol
    06  MCP Protocol
    07  Workflow Patterns
    08  Integration Report

Examples:
    $0                           # Run all tests
    $0 -q                        # Run quick tests (phases 1-3)
    $0 -p 04                     # Run only phase 4 (Engine Lifecycle)
    $0 --stop-on-failure         # Stop on first failure
    $0 -v --report-dir ./reports # Verbose with custom report directory
EOF
}

# Print banner
print_banner() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}                                                              ${CYAN}║${NC}"
    echo -e "${CYAN}║${NC}   ${BOLD}YAWL Shell Test Suite${NC}                                     ${CYAN}║${NC}"
    echo -e "${CYAN}║${NC}   ${YELLOW}Black-Box Testing - No Lies, No Mocks, No Stubs${NC}           ${CYAN}║${NC}"
    echo -e "${CYAN}║${NC}                                                              ${CYAN}║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo "Project: $PROJECT_DIR"
    echo "Test Dir: $TEST_DIR"
    echo "Report Dir: $REPORT_DIR"
    echo "Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
    echo ""
}

# Run a single test phase
run_phase() {
    local phase_num="$1"
    local phase_dir="$TEST_DIR/$phase_num-"*

    if [ ! -d "$phase_dir" ]; then
        echo -e "${RED}Phase directory not found: $phase_dir${NC}"
        return 1
    fi

    local phase_name=$(basename "$phase_dir")
    local run_script="$phase_dir/run.sh"

    if [ ! -f "$run_script" ]; then
        echo -e "${RED}Run script not found: $run_script${NC}"
        return 1
    fi

    echo ""
    echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BOLD}Phase $phase_num: ${phase_name#*-}${NC}"
    echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
    echo ""

    local start_time=$(date +%s)
    local phase_result=0

    # Run the test script
    (
        cd "$phase_dir"
        export PROJECT_DIR
        export TEST_DIR
        export REPORT_DIR
        export VERBOSE
        source "$SCRIPT_DIR/assert.sh"
        source "$SCRIPT_DIR/http-client.sh"
        source "$SCRIPT_DIR/process-manager.sh"
        bash "$run_script"
    ) || phase_result=$?

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    # Record result
    PHASE_RESULTS+=("$phase_num:$phase_result:$duration")

    if [ $phase_result -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ Phase $phase_num PASSED (${duration}s)${NC}"
    else
        echo ""
        echo -e "${RED}✗ Phase $phase_num FAILED (${duration}s)${NC}"

        if [ "$STOP_ON_FAILURE" = "true" ]; then
            echo -e "${RED}Stopping on failure${NC}"
            exit 1
        fi
    fi

    return $phase_result
}

# Run all phases
run_all_phases() {
    local phases=()

    if [ -n "$SPECIFIC_PHASE" ]; then
        phases=("$SPECIFIC_PHASE")
    elif [ "$QUICK_MODE" = "true" ]; then
        phases=("01" "02" "03")
    else
        phases=("01" "02" "03" "04" "05" "06" "07" "08")
    fi

    for phase in "${phases[@]}"; do
        run_phase "$phase" || true
    done
}

# Print final summary
print_summary() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║${NC}                    ${BOLD}Test Summary${NC}                           ${CYAN}║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    local total_duration=0
    local all_passed=true

    for result in "${PHASE_RESULTS[@]}"; do
        local phase="${result%%:*}"
        local status="${result#*:}"
        local code="${status%%:*}"
        local duration="${status#*:}"

        total_duration=$((total_duration + duration))

        if [ "$code" = "0" ]; then
            echo -e "  ${GREEN}✓${NC} Phase $phase: ${GREEN}PASSED${NC} (${duration}s)"
        else
            echo -e "  ${RED}✗${NC} Phase $phase: ${RED}FAILED${NC} (${duration}s)"
            all_passed=false
        fi
    done

    echo ""
    echo "Total Duration: ${total_duration}s"
    echo ""

    if [ "$all_passed" = "true" ]; then
        echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${GREEN}║${NC}              ${BOLD}ALL TESTS PASSED${NC}                            ${GREEN}║${NC}"
        echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
        return 0
    else
        echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
        echo -e "${RED}║${NC}              ${BOLD}SOME TESTS FAILED${NC}                           ${RED}║${NC}"
        echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
        return 1
    fi
}

# Main entry point
main() {
    parse_args "$@"
    print_banner
    run_all_phases
    print_summary
}

# Run main
main "$@"
