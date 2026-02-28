#!/usr/bin/env bash
# ==========================================================================
# run-feedback-tests.sh — Feedback Tier Test Runner
#
# Executes only fast, critical-path feedback tests for quick local validation.
# Implements Code Bifurcation: feedback tier (1-2 per module, <1s) vs coverage tier (all).
#
# Feedback tier selection:
#   - Explicitly configured tests in .yawl/ci/feedback-tests.json
#   - Auto-selected: max 2 per module, lowest execution time
#   - Validated: at least 1 test per core module (engine, elements, stateless)
#   - Target: <5s total execution time
#
# Usage:
#   bash scripts/run-feedback-tests.sh                # Run selected feedback tests
#   bash scripts/run-feedback-tests.sh --list         # List tests without running
#   bash scripts/run-feedback-tests.sh --dry-run      # Preview Maven command
#   bash scripts/run-feedback-tests.sh --module NAME  # Run subset by module
#
# Environment:
#   FEEDBACK_CONFIG       Config path (.yawl/ci/feedback-tests.json default)
#   DEBUG                 Set to 1 for verbose output
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Configuration
FEEDBACK_CONFIG="${FEEDBACK_CONFIG:-${REPO_ROOT}/.yawl/ci/feedback-tests.json}"
OUTPUT_DIR="${OUTPUT_DIR:-${REPO_ROOT}/.yawl/ci}"
DEBUG="${DEBUG:-0}"
DRY_RUN="${DRY_RUN:-0}"
LIST_ONLY="${LIST_ONLY:-0}"
MODULE_FILTER="${MODULE_FILTER:-}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# ============================================================================
# Logging
# ============================================================================

log_info() { echo -e "${BLUE}[INFO]${NC} $*" >&2; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $*" >&2; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_debug() { [ "${DEBUG}" = "1" ] && echo -e "${YELLOW}[DEBUG]${NC} $*" >&2; }

# ============================================================================
# Configuration Validation
# ============================================================================

validate_config() {
    local config="$1"

    if [ ! -f "${config}" ]; then
        log_error "Configuration not found: ${config}"
        return 2
    fi

    if ! grep -q '"feedback_tests"' "${config}"; then
        log_error "Missing 'feedback_tests' section in config"
        return 2
    fi

    log_debug "Configuration validated: ${config}"
    return 0
}

# ============================================================================
# Extract Feedback Tests with jq or fallback
# ============================================================================

extract_feedback_tests() {
    local config="$1"
    local module_filter="${2:-}"

    log_debug "Extracting feedback tests from: ${config}"

    declare -a test_specs
    local idx=0

    if command -v jq &> /dev/null; then
        # Use jq for precise extraction
        while IFS= read -r line; do
            local test_class=$(echo "${line}" | jq -r '.test_class // empty')
            local test_method=$(echo "${line}" | jq -r '.test_method // empty')
            local module=$(echo "${line}" | jq -r '.module // empty')

            if [ -z "${test_class}" ] || [ -z "${test_method}" ]; then
                continue
            fi

            # Apply module filter if specified
            if [ -n "${module_filter}" ] && [ "${module}" != "${module_filter}" ]; then
                log_debug "Skipping ${test_class}::${test_method} (module filter)"
                continue
            fi

            test_specs[$idx]="${test_class}#${test_method}"
            ((idx++))
            log_debug "Selected: ${test_class}::${test_method}"
        done < <(jq -c '.feedback_tests[]' "${config}")
    else
        # Fallback: extract via grep/sed
        log_warn "jq not available, using grep/sed fallback"
        local test_classes=($(grep -o '"test_class": "[^"]*"' "${config}" | cut -d'"' -f4))
        local test_methods=($(grep -o '"test_method": "[^"]*"' "${config}" | cut -d'"' -f4))

        if [ ${#test_classes[@]} -ne ${#test_methods[@]} ]; then
            log_error "Test class and method count mismatch"
            return 2
        fi

        for ((i = 0; i < ${#test_classes[@]}; i++)); do
            test_specs[$idx]="${test_classes[$i]}#${test_methods[$i]}"
            ((idx++))
        done
    fi

    if [ ${#test_specs[@]} -eq 0 ]; then
        log_warn "No feedback tests found in configuration"
        return 1
    fi

    FEEDBACK_TEST_SPECS=("${test_specs[@]}")
    log_info "Extracted ${#test_specs[@]} feedback tests"
    return 0
}

# ============================================================================
# List Tests
# ============================================================================

list_tests() {
    log_info "Selected Feedback Tests:"
    echo ""
    printf "%-70s\n" "Test"
    printf "%-70s\n" "$(printf '=%.0s' {1..70})"

    for test_spec in "${FEEDBACK_TEST_SPECS[@]}"; do
        IFS='#' read -r test_class test_method <<< "${test_spec}"
        printf "%-70s\n" "${test_class}::${test_method}"
    done

    echo ""
    log_info "Total: ${#FEEDBACK_TEST_SPECS[@]} tests"
    echo ""
}

# ============================================================================
# Build Maven Command
# ============================================================================

build_maven_command() {
    local module="${1:-}"

    local mvn_cmd="mvn -B test"

    if [ -n "${module}" ]; then
        mvn_cmd="${mvn_cmd} -pl ${module}"
    fi

    # Build test selectors
    if [ ${#FEEDBACK_TEST_SPECS[@]} -gt 0 ]; then
        local test_selectors=""
        for test_spec in "${FEEDBACK_TEST_SPECS[@]}"; do
            if [ -z "${test_selectors}" ]; then
                test_selectors="${test_spec}"
            else
                test_selectors="${test_selectors},${test_spec}"
            fi
        done
        mvn_cmd="${mvn_cmd} -Dtest=${test_selectors}"
    fi

    # Parallel execution and fail-fast
    mvn_cmd="${mvn_cmd} -T 4 -Dstyle.color=always"

    echo "${mvn_cmd}"
}

# ============================================================================
# Execute Tests
# ============================================================================

execute_tests() {
    local module="${1:-}"

    log_info "Building Maven command..."
    local mvn_cmd=$(build_maven_command "${module}")
    log_debug "Maven command: ${mvn_cmd}"

    if [ "${DRY_RUN}" = "1" ]; then
        log_info "DRY RUN - Command would execute:"
        echo "${mvn_cmd}"
        return 0
    fi

    log_info "Starting test execution..."
    echo ""

    cd "${REPO_ROOT}"

    local start_time=$(date +%s%N)

    if ${mvn_cmd}; then
        local end_time=$(date +%s%N)
        local elapsed_ms=$(((end_time - start_time) / 1000000))
        log_success "All feedback tests passed in ${elapsed_ms}ms"
        return 0
    else
        local end_time=$(date +%s%N)
        local elapsed_ms=$(((end_time - start_time) / 1000000))
        log_error "Some tests failed after ${elapsed_ms}ms"
        return 1
    fi
}

# ============================================================================
# Main
# ============================================================================

main() {
    log_info "YAWL Feedback Test Runner v1.0"
    log_info "==============================="
    echo ""

    # Parse arguments
    while [ $# -gt 0 ]; do
        case "$1" in
            --list)
                LIST_ONLY=1
                shift
                ;;
            --dry-run)
                DRY_RUN=1
                shift
                ;;
            --module)
                MODULE_FILTER="$2"
                shift 2
                ;;
            --config)
                FEEDBACK_CONFIG="$2"
                shift 2
                ;;
            --debug)
                DEBUG=1
                shift
                ;;
            -h|--help)
                cat <<EOF
Usage: $(basename "$0") [OPTIONS]

OPTIONS:
  --list              List tests without running
  --dry-run           Show Maven command without executing
  --module <name>     Run tests only from specified module
  --config <path>     Use custom configuration file
  --debug             Enable verbose debug output
  -h, --help          Show this help message

EXAMPLES:
  # Run feedback tests
  bash scripts/run-feedback-tests.sh

  # List available feedback tests
  bash scripts/run-feedback-tests.sh --list

  # Test only yawl-engine module
  bash scripts/run-feedback-tests.sh --module yawl-engine

  # Preview Maven command
  bash scripts/run-feedback-tests.sh --dry-run

EOF
                return 0
                ;;
            *)
                log_error "Unknown option: $1"
                return 2
                ;;
        esac
    done

    # Load and validate configuration
    if ! validate_config "${FEEDBACK_CONFIG}"; then
        return 2
    fi

    # Extract feedback tests
    if ! extract_feedback_tests "${FEEDBACK_CONFIG}" "${MODULE_FILTER}"; then
        return 2
    fi

    # List if requested
    if [ "${LIST_ONLY}" = "1" ]; then
        list_tests
        return 0
    fi

    # Execute tests
    execute_tests "${MODULE_FILTER}"
}

main "$@"
exit $?
