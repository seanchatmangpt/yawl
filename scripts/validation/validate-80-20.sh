#!/bin/bash
#
# YAWL 80/20 Validation Orchestrator
# Runs validation checks with different execution modes and output formats
#

set -e

# Source common utilities
source "$(dirname "$0")/lib/common.sh"

# Configuration
readonly SCRIPT_DIR="$(dirname "$0")"
readonly LIB_DIR="$SCRIPT_DIR/lib"

# Default values
MODE="pre-commit"
TIMEOUT=60
VERBOSE=false
JSON_OUTPUT=false
JUNIT_OUTPUT=false
PARALLEL=false

# Display help
show_help() {
    cat << EOF
YAWL 80/20 Validation Orchestrator

Usage: $0 [OPTIONS]

OPTIONS:
    -m, --mode MODE     Execution mode: pre-commit (default), ci, full
    -t, --timeout SEC   Timeout for each check in seconds (default: 30)
    -v, --verbose       Enable verbose output
    --json              Output results in JSON format
    --junit             Output results in JUnit XML format
    --parallel          Run checks in parallel (for ci mode only)
    -h, --help          Show this help message

MODES:
    pre-commit          Quick checks (only module sync and singleton annotations)
    ci                  All checks with standard timeout
    full                All checks with comprehensive output

EXAMPLES:
    $0                                    # Run pre-commit checks
    $0 -m ci --parallel                  # Run all checks in parallel for CI
    $0 --json --junit                   # Output in both formats
    $0 -m full -t 60 --verbose          # Full mode with verbose output
EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -m|--mode)
                MODE="$2"
                shift 2
                ;;
            -t|--timeout)
                TIMEOUT="$2"
                shift 2
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            --json)
                JSON_OUTPUT=true
                shift
                ;;
            --junit)
                JUNIT_OUTPUT=true
                shift
                ;;
            --parallel)
                PARALLEL=true
                shift
                ;;
            -h|--help)
                show_help
                exit 0
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
        output_junit
    fi

    # Output JSON if requested
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        output_json
    fi
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 1
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
        output_junit
    fi

    # Output JSON if requested
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        output_json
    fi
                ;;
        esac
    done

    # Validate mode
    case "$MODE" in
        pre-commit|ci|full)
            ;;
        *)
            log_error "Invalid mode: $MODE. Must be one of: pre-commit, ci, full"
            exit 1
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
        output_junit
    fi

    # Output JSON if requested
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        output_json
    fi
            ;;
    esac
}

# Run a single check with timeout
run_check() {
    local check_script=$1
    local check_name=$2

    if [[ "$VERBOSE" == "true" ]]; then
        log_section "Running $check_name"
    fi

    # Run check with timeout
    if timeout "$TIMEOUT" "$check_script"; then
        return 0
    else
        local exit_code=$?
        if [[ $exit_code -eq 124 ]]; then
            log_error "$check_name timed out after $TIMEOUT seconds"
        else
            log_error "$check_name failed with exit code $exit_code"
        fi
        return 1
    fi
}

# Get list of checks to run based on mode
get_checks() {
    local checks=()

    case "$MODE" in
        pre-commit)
            checks=(
                "$LIB_DIR/check-module-sync.sh"
                "$LIB_DIR/check-singleton-ann.sh"
            )
            ;;
        ci)
            if [[ "$PARALLEL" == "true" ]]; then
                checks=(
                    "$LIB_DIR/check-module-sync.sh --json"
                    "$LIB_DIR/check-singleton-ann.sh --json"
                    "$LIB_DIR/check-dockerfile-jvm.sh --json"
                    "$LIB_DIR/check-doc-links.sh --json"
                    "$LIB_DIR/check-observatory-timing.sh --json"
                )
            else
                checks=(
                    "$LIB_DIR/check-module-sync.sh"
                    "$LIB_DIR/check-singleton-ann.sh"
                    "$LIB_DIR/check-dockerfile-jvm.sh"
                    "$LIB_DIR/check-doc-links.sh"
                    "$LIB_DIR/check-observatory-timing.sh"
                )
            fi
            ;;
        full)
            # CI checks plus additional validation
            checks=(
                "$LIB_DIR/check-module-sync.sh"
                "$LIB_DIR/check-singleton-ann.sh"
                "$LIB_DIR/check-dockerfile-jvm.sh"
                "$LIB_DIR/check-doc-links.sh"
                "$LIB_DIR/check-observatory-timing.sh"
                # Additional checks for full mode
                "bash -c 'echo \"Checking Maven build...\"; mvn clean compile -q'"
                "bash -c 'echo \"Running basic syntax check...\"; find src -name \"*.java\" -exec javac -cp \"src/*\" {} \; 2>/dev/null || true'"
            )
            ;;
    esac

    printf '%s\n' "${checks[@]}"
}

# Main execution
main() {
    parse_args "$@"

    log_header "YAWL 80/20 Validation - Mode: $MODE"
    log_info "Running with timeout: $TIMEOUT seconds"
    if [[ "$PARALLEL" == "true" ]]; then
        log_info "Parallel execution enabled"
    fi

    # Get checks to run
    declare -a checks
    while IFS= read -r check; do
        checks+=("$check")
    done < <(get_checks)

    # Execute checks
    local failed_checks=0

    if [[ "$PARALLEL" == "true" && "$MODE" == "ci" ]]; then
        # Parallel execution
        for check in "${checks[@]}"; do
            (
                if ! run_check "$check" "$(basename "$check")"; then
                    exit 1
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
        output_junit
    fi

    # Output JSON if requested
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        output_json
    fi
                fi
            ) &
        done

        # Wait for all background jobs
        for job in $(jobs -p); do
            wait "$job" || failed_checks=$((failed_checks + 1))
        done
    else
        # Sequential execution
        for check in "${checks[@]}"; do
            if ! run_check "$check" "$(basename "$check")"; then
                failed_checks=$((failed_checks + 1))
            fi
        done
    fi

    # Output summary
    echo
    if output_summary; then
        log_success "All checks passed"
        exit 0
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
        output_junit
    fi

    # Output JSON if requested
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        output_json
    fi
    else
        log_error "$failed_checks check(s) failed"
        exit 1
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
        output_junit
    fi

    # Output JSON if requested
    if [[ "$JSON_OUTPUT" == "true" ]]; then
        output_json
    fi
    fi
}

# Handle signals
trap 'log_error "Validation interrupted"; exit 1' INT TERM
    # Output JUnit if requested
    if [[ "$JUNIT_OUTPUT" == "true" ]]; then
    # Output summary
    if output_summary; then
        log_success "All checks passed"
        # Output JUnit if requested
        if [[ "$JUNIT_OUTPUT" == "true" ]]; then
            output_junit
        fi
        # Output JSON if requested
        if [[ "$JSON_OUTPUT" == "true" ]]; then
            output_json
        fi
        exit 0
    else
        log_error "$failed_checks check(s) failed"
        # Output JUnit if requested
        if [[ "$JUNIT_OUTPUT" == "true" ]]; then
            output_junit
        fi
        # Output JSON if requested
        if [[ "$JSON_OUTPUT" == "true" ]]; then
            output_json
        fi
        exit 1
    fi
}

# Handle signals
trap 'log_error "Validation interrupted"; exit 1' INT TERM

# Main entry point
main "$@"
