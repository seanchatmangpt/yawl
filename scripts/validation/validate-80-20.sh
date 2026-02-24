#!/usr/bin/env bash
#
# YAWL 80/20 Validation Orchestrator
# Runs validation checks with different execution modes and output formats
#

set -euo pipefail

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

# Parse command line arguments
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
            echo "YAWL 80/20 Validation Orchestrator"
            echo "Usage: $0 [OPTIONS]"
            echo "Options:"
            echo "  -m, --mode MODE     Execution mode: pre-commit (default), ci, full"
            echo "  -t, --timeout SEC   Timeout for each check in seconds (default: 60)"
            echo "  -v, --verbose       Enable verbose output"
            echo "  --json              Output results in JSON format"
            echo "  --junit             Output results in JUnit XML format"
            echo "  --parallel          Run checks in parallel (for ci mode only)"
            echo "  -h, --help          Show this help message"
            echo ""
            echo "Modes:"
            echo "  pre-commit          Quick checks (only module sync and singleton annotations)"
            echo "  ci                  All checks with standard timeout"
            echo "  full                All checks with comprehensive output"
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            exit 1
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
        ;;
esac

# Main execution
log_header "YAWL 80/20 Validation - Mode: $MODE"
log_info "Running with timeout: $TIMEOUT seconds"
if [[ "$PARALLEL" == "true" ]]; then
    log_info "Parallel execution enabled"
fi

# Get checks to run
declare -a checks
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
                "$LIB_DIR/check-module-sync.sh"
                "$LIB_DIR/check-singleton-ann.sh"
                "$LIB_DIR/check-dockerfile-jvm.sh"
                "$LIB_DIR/check-doc-links.sh"
                "$LIB_DIR/check-observatory-timing.sh"
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
        checks=(
            "$LIB_DIR/check-module-sync.sh"
            "$LIB_DIR/check-singleton-ann.sh"
            "$LIB_DIR/check-dockerfile-jvm.sh"
            "$LIB_DIR/check-doc-links.sh"
            "$LIB_DIR/check-observatory-timing.sh"
            "bash -c 'echo \"Checking Maven build...\"; mvn clean compile -q'"
        )
        ;;
esac

# Execute checks
failed_checks=0

if [[ "$PARALLEL" == "true" && "$MODE" == "ci" ]]; then
    # Parallel execution
    for check in "${checks[@]}"; do
        (
            if [[ "$VERBOSE" == "true" ]]; then
                log_section "Running $(basename "$check")"
            fi
            
            if timeout "$TIMEOUT" "$check"; then
                :
            else
                local exit_code=$?
                if [[ $exit_code -eq 124 ]]; then
                    log_error "$(basename "$check") timed out after $TIMEOUT seconds"
                else
                    log_error "$(basename "$check") failed with exit code $exit_code"
                fi
                exit 1
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
        if [[ "$VERBOSE" == "true" ]]; then
            log_section "Running $(basename "$check")"
        fi
        
        if ! timeout "$TIMEOUT" "$check"; then
            exit_code=$?
            if [[ $exit_code -eq 124 ]]; then
                log_error "$(basename "$check") timed out after $TIMEOUT seconds"
            else
                log_error "$(basename "$check") failed with exit code $exit_code"
            fi
            failed_checks=$((failed_checks + 1))
        fi
    done
fi

# Output summary
echo
if output_summary; then
    log_success "All checks passed"
else
    log_error "$failed_checks check(s) failed"
    exit 1
fi

# Output formats
if [[ "$JUNIT_OUTPUT" == "true" ]]; then
    output_junit
fi

if [[ "$JSON_OUTPUT" == "true" ]]; then
    output_json
fi
