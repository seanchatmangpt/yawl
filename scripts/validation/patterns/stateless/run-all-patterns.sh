#!/bin/bash

# ==========================================================================
# run-all-patterns.sh - YAWL Stateless Pattern Validation Orchestrator
#
# Validates all 43+ YAWL workflow control-flow patterns via stateless engine.
# Categories: Basic, Branching, Multi-Instance, State, Cancel, Event, Extended
#
# Usage:
#   bash scripts/validation/patterns/stateless/run-all-patterns.sh
#   bash scripts/validation/patterns/stateless/run-all-patterns.sh --skip-engine-start
#
# Output: docs/v6/latest/validation/patterns-stateless.json
# ==========================================================================

set -euo pipefail

# Resolve script location
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
PROJECT_ROOT="$(cd "${VALIDATION_DIR}/.." && pwd)"

# Source helpers
source "${VALIDATION_DIR}/lib/common.sh"
source "${VALIDATION_DIR}/docker/api-helpers.sh"

# Configuration
ENGINE_START_TIMEOUT="${ENGINE_START_TIMEOUT:-120}"
SKIP_ENGINE_START="${SKIP_ENGINE_START:-false}"
REPORT_DIR="${PROJECT_ROOT}/docs/v6/latest/validation"
REPORT_FILE="${REPORT_DIR}/patterns-stateless.json"

# Pattern category definitions
declare -A CATEGORY_PATTERNS
CATEGORY_PATTERNS[basic]="WCP-01 WCP-02 WCP-03 WCP-04 WCP-05"
CATEGORY_PATTERNS[branching]="WCP-06 WCP-07 WCP-08 WCP-09 WCP-10 WCP-11"
CATEGORY_PATTERNS[mi]="WCP-12 WCP-13 WCP-14 WCP-15 WCP-16 WCP-17 WCP-24 WCP-26 WCP-27"
CATEGORY_PATTERNS[state]="WCP-18 WCP-19 WCP-20 WCP-21 WCP-32 WCP-33 WCP-34 WCP-35"
CATEGORY_PATTERNS[cancel]="WCP-22 WCP-23 WCP-25 WCP-29 WCP-30 WCP-31"
CATEGORY_PATTERNS[event]="WCP-37 WCP-38 WCP-39 WCP-40 WCP-51 WCP-52 WCP-53 WCP-54 WCP-55 WCP-56 WCP-57 WCP-58 WCP-59"
CATEGORY_PATTERNS[extended]="WCP-41 WCP-42 WCP-43 WCP-44 ENT-APPROVAL ENT-ESCALATION ENT-NOTIFICATION ENT-DELEGATION"

# Category display names
declare -A CATEGORY_NAMES
CATEGORY_NAMES[basic]="Basic Control Flow (WCP-01-05)"
CATEGORY_NAMES[branching]="Branching and Synchronization (WCP-06-11)"
CATEGORY_NAMES[mi]="Multi-Instance (WCP-12-17,24,26-27)"
CATEGORY_NAMES[state]="State-Based (WCP-18-21,32-35)"
CATEGORY_NAMES[cancel]="Cancellation (WCP-22-25,29-31)"
CATEGORY_NAMES[event]="Event-Based (WCP-37-40,51-59)"
CATEGORY_NAMES[extended]="Extended and Enterprise (WCP-41-44,ENT)"

# Results tracking
declare -A CATEGORY_RESULTS
declare -A CATEGORY_TIMINGS
declare -a ALL_PATTERNS_PASSED=()
declare -a ALL_PATTERNS_FAILED=()
TOTAL_PATTERNS=0
TOTAL_PASSED=0
TOTAL_FAILED=0

# -------------------------------------------------------------------------
# Engine Management
# -------------------------------------------------------------------------

start_engine() {
    log_section "Starting YAWL Engine (Stateless Mode)"

    if [[ "$SKIP_ENGINE_START" == "true" ]]; then
        log_info "Skipping engine start (--skip-engine-start)"
        return 0
    fi

    # Check if engine is already running
    if curl -s -f "${ENGINE_URL:-http://localhost:8080}/actuator/health/liveness" > /dev/null 2>&1; then
        log_success "Engine already running"
        return 0
    fi

    log_info "Starting engine via docker compose..."
    cd "${PROJECT_ROOT}"

    # Start engine service
    docker compose up -d yawl-engine 2>/dev/null || docker-compose up -d yawl-engine 2>/dev/null || {
        log_error "Failed to start engine via docker compose"
        return 1
    }

    # Wait for engine to be healthy
    if ! wait_for_engine "${ENGINE_URL:-http://localhost:8080}" "${ENGINE_START_TIMEOUT}"; then
        log_error "Engine failed to start within ${ENGINE_START_TIMEOUT}s"
        docker compose logs yawl-engine 2>/dev/null | tail -20
        return 1
    fi

    log_success "Engine started successfully"
}

stop_engine() {
    if [[ "$SKIP_ENGINE_START" == "true" ]]; then
        return 0
    fi

    log_info "Stopping engine..."
    cd "${PROJECT_ROOT}"
    docker compose down yawl-engine 2>/dev/null || docker-compose down yawl-engine 2>/dev/null || true
}

# -------------------------------------------------------------------------
# Pattern Category Validation
# -------------------------------------------------------------------------

run_category_validation() {
    local category="$1"
    local script="${SCRIPT_DIR}/validate-${category}.sh"
    local start_time end_time duration

    log_section "Validating: ${CATEGORY_NAMES[$category]}"

    if [[ ! -x "$script" ]]; then
        log_error "Category script not found or not executable: $script"
        CATEGORY_RESULTS[$category]="error"
        CATEGORY_TIMINGS[$category]="0"
        return 1
    fi

    start_time=$(date +%s%3N)

    # Run category validation and capture results
    local output temp_json exit_code
    temp_json=$(mktemp)

    if output=$(bash "$script" 2>&1); then
        exit_code=0
    else
        exit_code=$?
    fi

    end_time=$(date +%s%3N)
    duration=$((end_time - start_time))
    CATEGORY_TIMINGS[$category]="${duration}"

    # Parse results from category output
    local passed failed
    passed=$(echo "$output" | grep -c "\\[PASS\\]" 2>/dev/null || echo "0")
    failed=$(echo "$output" | grep -c "\\[FAIL\\]" 2>/dev/null || echo "0")

    # Update totals
    TOTAL_PASSED=$((TOTAL_PASSED + passed))
    TOTAL_FAILED=$((TOTAL_FAILED + failed))
    TOTAL_PATTERNS=$((TOTAL_PATTERNS + passed + failed))

    # Track individual patterns
    local patterns="${CATEGORY_PATTERNS[$category]}"
    for pattern in $patterns; do
        if echo "$output" | grep -q "\\[PASS\\].*${pattern}"; then
            ALL_PATTERNS_PASSED+=("$pattern")
        elif echo "$output" | grep -q "\\[FAIL\\].*${pattern}"; then
            ALL_PATTERNS_FAILED+=("$pattern")
        fi
    done

    # Set category result
    if [[ $exit_code -eq 0 && $failed -eq 0 ]]; then
        CATEGORY_RESULTS[$category]="passed"
        log_success "Category $category: $passed passed, $failed failed (${duration}ms)"
    elif [[ $passed -gt 0 ]]; then
        CATEGORY_RESULTS[$category]="partial"
        log_warning "Category $category: $passed passed, $failed failed (${duration}ms)"
    else
        CATEGORY_RESULTS[$category]="failed"
        log_error "Category $category: $passed passed, $failed failed (${duration}ms)"
    fi

    rm -f "$temp_json"
    return $exit_code
}

# -------------------------------------------------------------------------
# Report Generation
# -------------------------------------------------------------------------

generate_report() {
    local start_time="$1"
    local end_time=$(date +%s%3N)
    local total_duration=$((end_time - start_time))

    log_section "Generating Validation Report"

    mkdir -p "${REPORT_DIR}"

    # Calculate pattern counts
    local basic_count branching_count mi_count state_count cancel_count event_count extended_count
    basic_count=$(echo "${CATEGORY_PATTERNS[basic]}" | wc -w | tr -d ' ')
    branching_count=$(echo "${CATEGORY_PATTERNS[branching]}" | wc -w | tr -d ' ')
    mi_count=$(echo "${CATEGORY_PATTERNS[mi]}" | wc -w | tr -d ' ')
    state_count=$(echo "${CATEGORY_PATTERNS[state]}" | wc -w | tr -d ' ')
    cancel_count=$(echo "${CATEGORY_PATTERNS[cancel]}" | wc -w | tr -d ' ')
    event_count=$(echo "${CATEGORY_PATTERNS[event]}" | wc -w | tr -d ' ')
    extended_count=$(echo "${CATEGORY_PATTERNS[extended]}" | wc -w | tr -d ' ')

    # Build JSON report
    cat > "${REPORT_FILE}" << EOF
{
  "test_type": "pattern-validation-stateless",
  "timestamp": "$(get_timestamp)",
  "engine_mode": "stateless",
  "duration_ms": ${total_duration},
  "summary": {
    "total_patterns": ${TOTAL_PATTERNS},
    "passed": ${TOTAL_PASSED},
    "failed": ${TOTAL_FAILED},
    "pass_rate": $(echo "scale=2; ${TOTAL_PASSED} * 100 / ${TOTAL_PATTERNS:-1}" | bc 2>/dev/null || echo "0.00")
  },
  "categories": {
    "basic": {
      "name": "${CATEGORY_NAMES[basic]}",
      "pattern_count": ${basic_count},
      "status": "${CATEGORY_RESULTS[basic]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[basic]:-0}
    },
    "branching": {
      "name": "${CATEGORY_NAMES[branching]}",
      "pattern_count": ${branching_count},
      "status": "${CATEGORY_RESULTS[branching]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[branching]:-0}
    },
    "mi": {
      "name": "${CATEGORY_NAMES[mi]}",
      "pattern_count": ${mi_count},
      "status": "${CATEGORY_RESULTS[mi]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[mi]:-0}
    },
    "state": {
      "name": "${CATEGORY_NAMES[state]}",
      "pattern_count": ${state_count}",
      "status": "${CATEGORY_RESULTS[state]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[state]:-0}
    },
    "cancel": {
      "name": "${CATEGORY_NAMES[cancel]}",
      "pattern_count": ${cancel_count},
      "status": "${CATEGORY_RESULTS[cancel]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[cancel]:-0}
    },
    "event": {
      "name": "${CATEGORY_NAMES[event]}",
      "pattern_count": ${event_count},
      "status": "${CATEGORY_RESULTS[event]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[event]:-0}
    },
    "extended": {
      "name": "${CATEGORY_NAMES[extended]}",
      "pattern_count": ${extended_count},
      "status": "${CATEGORY_RESULTS[extended]:-skipped}",
      "duration_ms": ${CATEGORY_TIMINGS[extended]:-0}
    }
  },
  "patterns": {
    "passed": $(printf '%s\n' "${ALL_PATTERNS_PASSED[@]}" | jq -R . | jq -s . 2>/dev/null || echo "[]"),
    "failed": $(printf '%s\n' "${ALL_PATTERNS_FAILED[@]}" | jq -R . | jq -s . 2>/dev/null || echo "[]")
  },
  "environment": {
    "engine_url": "${ENGINE_URL:-http://localhost:8080}",
    "skip_engine_start": "${SKIP_ENGINE_START}",
    "timeout": ${ENGINE_START_TIMEOUT}
  }
}
EOF

    log_success "Report generated: ${REPORT_FILE}"
}

# -------------------------------------------------------------------------
# Summary Output
# -------------------------------------------------------------------------

print_summary() {
    local duration=$1

    echo ""
    log_header "=========================================="
    log_header "  YAWL Stateless Pattern Validation Summary"
    log_header "=========================================="
    echo ""
    echo "  Total Patterns:  ${TOTAL_PATTERNS}"
    echo -e "  ${GREEN}Passed:${RESET}          ${TOTAL_PASSED}"
    echo -e "  ${RED}Failed:${RESET}          ${TOTAL_FAILED}"
    echo ""
    echo "  Category Results:"
    echo "  -----------------"
    for category in basic branching mi state cancel event extended; do
        local status="${CATEGORY_RESULTS[$category]:-skipped}"
        local timing="${CATEGORY_TIMINGS[$category]:-0}"
        local icon

        case "$status" in
            passed)  icon="${GREEN}[PASS]${RESET}" ;;
            partial) icon="${YELLOW}[PART]${RESET}" ;;
            failed)  icon="${RED}[FAIL]${RESET}" ;;
            *)       icon="${DIM}[SKIP]${RESET}" ;;
        esac

        printf "  %-12s %s %sms\n" "$category" "$icon" "$timing"
    done
    echo ""
    echo "  Duration:        ${duration}ms"
    echo "  Report:          ${REPORT_FILE}"
    echo ""

    if [[ $TOTAL_FAILED -gt 0 ]]; then
        log_error "VALIDATION FAILED - ${TOTAL_FAILED} patterns failed"
        return 1
    else
        log_success "ALL PATTERNS PASSED"
        return 0
    fi
}

# -------------------------------------------------------------------------
# Main Execution
# -------------------------------------------------------------------------

main() {
    # Parse arguments
    for arg in "$@"; do
        case "$arg" in
            --skip-engine-start) SKIP_ENGINE_START="true" ;;
            --help)
                echo "Usage: $0 [--skip-engine-start] [--help]"
                echo ""
                echo "Options:"
                echo "  --skip-engine-start  Skip docker compose engine startup"
                echo "  --help               Show this help message"
                echo ""
                echo "Environment Variables:"
                echo "  ENGINE_URL           Engine base URL (default: http://localhost:8080)"
                echo "  ENGINE_START_TIMEOUT Engine startup timeout in seconds (default: 120)"
                exit 0
                ;;
        esac
    done

    local start_time=$(date +%s%3N)

    log_header "=========================================="
    log_header "  YAWL v6.0 Stateless Pattern Validation"
    log_header "  43+ Workflow Control-Flow Patterns"
    log_header "=========================================="
    echo ""

    # Initialize validation environment
    yawl_init_validation || {
        log_error "Validation environment initialization failed"
        exit 1
    }

    # Start engine
    start_engine || {
        log_error "Engine startup failed"
        exit 1
    }

    # Connect to Interface B
    yawl_connect || {
        log_error "Failed to connect to Interface B"
        stop_engine
        exit 1
    }

    # Run all category validations
    local category_exit_codes=()
    for category in basic branching mi state cancel event extended; do
        run_category_validation "$category"
        category_exit_codes+=($?)
    done

    # Disconnect from engine
    yawl_disconnect

    # Stop engine (optional)
    # stop_engine

    # Generate report
    generate_report "$start_time"

    # Print summary
    local end_time=$(date +%s%3N)
    local total_duration=$((end_time - start_time))
    print_summary "$total_duration"
    local final_exit=$?

    # Cleanup
    rm -f /tmp/*.xml 2>/dev/null || true

    exit $final_exit
}

# Execute
main "$@"
