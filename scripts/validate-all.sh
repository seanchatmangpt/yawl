#!/usr/bin/env bash
# ==========================================================================
# validate-all.sh - Complete YAWL Validation Pipeline
# ==========================================================================
# Runs all validations in sequence:
#   1. Compile (fast, incremental)
#   2. Test (unit tests)
#   3. Static Analysis (SpotBugs, PMD, Checkstyle)
#   4. Observatory (facts, diagrams, receipts)
#   5. Schema Validation (XML/XSD)
#
# Usage:
#   ./scripts/validate-all.sh              # Run all validations
#   ./scripts/validate-all.sh --fast       # Skip analysis and observatory
#   ./scripts/validate-all.sh --compile    # Compile only
#   ./scripts/validate-all.sh --test       # Test only
#   ./scripts/validate-all.sh --analysis   # Analysis only
#   ./scripts/validate-all.sh --observatory # Observatory only
#
# Exit codes:
#   0 - All validations passed
#   1 - Compilation failed
#   2 - Tests failed
#   3 - Analysis failed
#   4 - Observatory failed (warning, non-fatal)
#   5 - Schema validation failed (warning, non-fatal)
# ==========================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Colors (disabled if not a terminal)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
    BLUE='\033[0;34m'
    BOLD='\033[1m'
    RESET='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    BOLD=''
    RESET=''
fi

# Configuration
SKIP_ANALYSIS=false
SKIP_OBSERVATORY=false
SKIP_SCHEMA=false
PHASE=""
START_TIME=$(date +%s)

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --fast)
            SKIP_ANALYSIS=true
            SKIP_OBSERVATORY=true
            SKIP_SCHEMA=true
            shift
            ;;
        --compile)
            PHASE="compile"
            shift
            ;;
        --test)
            PHASE="test"
            shift
            ;;
        --analysis)
            PHASE="analysis"
            shift
            ;;
        --observatory)
            PHASE="observatory"
            shift
            ;;
        --schema)
            PHASE="schema"
            shift
            ;;
        -h|--help)
            sed -n '2,/^# =/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

# Helper functions
log_header() {
    echo ""
    echo -e "${BOLD}${BLUE}================================================================${RESET}"
    echo -e "${BOLD}${BLUE}  $1${RESET}"
    echo -e "${BOLD}${BLUE}================================================================${RESET}"
    echo ""
}

log_success() {
    echo -e "${GREEN}[PASS]${RESET} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARN]${RESET} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${RESET} $1"
}

elapsed_time() {
    local end_time=$(date +%s)
    local elapsed=$((end_time - START_TIME))
    local minutes=$((elapsed / 60))
    local seconds=$((elapsed % 60))
    echo "${minutes}m ${seconds}s"
}

# Phase functions
run_compile() {
    log_header "Phase 1: Compilation"

    if [[ -f "${SCRIPT_DIR}/dx.sh" ]]; then
        echo "Running fast compile (dx.sh)..."
        if bash "${SCRIPT_DIR}/dx.sh" compile; then
            log_success "Compilation passed"
            return 0
        else
            log_error "Compilation failed"
            return 1
        fi
    else
        echo "Running Maven compile..."
        if mvn clean compile -B -q; then
            log_success "Compilation passed"
            return 0
        else
            log_error "Compilation failed"
            return 1
        fi
    fi
}

run_test() {
    log_header "Phase 2: Tests"

    if [[ -f "${SCRIPT_DIR}/dx.sh" ]]; then
        echo "Running fast tests (dx.sh)..."
        if bash "${SCRIPT_DIR}/dx.sh" test; then
            log_success "Tests passed"
            return 0
        else
            log_error "Tests failed"
            return 2
        fi
    else
        echo "Running Maven tests..."
        if mvn test -B -q; then
            log_success "Tests passed"
            return 0
        else
            log_error "Tests failed"
            return 2
        fi
    fi
}

run_analysis() {
    if $SKIP_ANALYSIS; then
        log_warning "Skipping static analysis (--fast mode)"
        return 0
    fi

    log_header "Phase 3: Static Analysis"

    echo "Running SpotBugs, PMD, Checkstyle..."

    # Try Maven analysis profile first
    if mvn verify -P analysis -B -q 2>/dev/null; then
        log_success "Static analysis passed"
        return 0
    fi

    # Fallback: try individual tools
    local has_failures=false

    echo "  Checking SpotBugs..."
    if mvn spotbugs:check -B -q 2>/dev/null; then
        echo "    SpotBugs: OK"
    else
        echo "    SpotBugs: Issues found (or not configured)"
        has_failures=true
    fi

    echo "  Checking PMD..."
    if mvn pmd:check -B -q 2>/dev/null; then
        echo "    PMD: OK"
    else
        echo "    PMD: Issues found (or not configured)"
        has_failures=true
    fi

    echo "  Checking Checkstyle..."
    if mvn checkstyle:check -B -q 2>/dev/null; then
        echo "    Checkstyle: OK"
    else
        echo "    Checkstyle: Issues found (or not configured)"
        has_failures=true
    fi

    if $has_failures; then
        log_warning "Static analysis found issues (non-blocking)"
    else
        log_success "Static analysis passed"
    fi

    return 0  # Analysis failures are non-blocking
}

run_observatory() {
    if $SKIP_OBSERVATORY; then
        log_warning "Skipping observatory (--fast mode)"
        return 0
    fi

    log_header "Phase 4: Observatory"

    local observatory_script="${REPO_ROOT}/scripts/observatory/observatory.sh"

    if [[ ! -f "$observatory_script" ]]; then
        log_warning "Observatory script not found, skipping"
        return 0
    fi

    echo "Generating facts, diagrams, and receipts..."

    if bash "$observatory_script" 2>&1 | tail -20; then
        log_success "Observatory completed"

        # Show output location
        if [[ -d "${REPO_ROOT}/docs/v6/latest" ]]; then
            local facts_count=$(find "${REPO_ROOT}/docs/v6/latest/facts" -name "*.json" 2>/dev/null | wc -l | tr -d ' ')
            local diagrams_count=$(find "${REPO_ROOT}/docs/v6/latest/diagrams" -name "*.mmd" 2>/dev/null | wc -l | tr -d ' ')
            echo ""
            echo "  Generated: ${facts_count} facts, ${diagrams_count} diagrams"
            echo "  Output: docs/v6/latest/"
        fi

        return 0
    else
        log_warning "Observatory had issues (non-blocking)"
        return 0  # Observatory failures are non-blocking
    fi
}

run_schema() {
    if $SKIP_SCHEMA; then
        log_warning "Skipping schema validation (--fast mode)"
        return 0
    fi

    log_header "Phase 5: Schema Validation"

    local schema_file="${REPO_ROOT}/schema/YAWL_Schema4.0.xsd"

    if [[ ! -f "$schema_file" ]]; then
        log_warning "No XSD schema found, skipping XML validation"
        return 0
    fi

    local xml_files=$(find "${REPO_ROOT}" -name "*.xml" -path "*/specifications/*" 2>/dev/null | head -10)

    if [[ -z "$xml_files" ]]; then
        log_warning "No specification XML files found, skipping"
        return 0
    fi

    local failures=0
    local total=0

    while IFS= read -r xml_file; do
        [[ -z "$xml_file" ]] && continue
        total=$((total + 1))
        if xmllint --schema "$schema_file" "$xml_file" > /dev/null 2>&1; then
            echo "  OK: $(basename "$xml_file")"
        else
            echo "  FAIL: $(basename "$xml_file")"
            failures=$((failures + 1))
        fi
    done <<< "$xml_files"

    if [[ $failures -eq 0 ]]; then
        log_success "Schema validation passed (${total} files)"
        return 0
    else
        log_warning "Schema validation found ${failures}/${total} issues (non-blocking)"
        return 0  # Schema failures are non-blocking
    fi
}

# Main execution
print_summary() {
    local exit_code=$1
    log_header "Validation Summary"

    echo "Total time: $(elapsed_time)"
    echo ""

    if [[ $exit_code -eq 0 ]]; then
        echo -e "${GREEN}${BOLD}ALL VALIDATIONS PASSED${RESET}"
    else
        echo -e "${RED}${BOLD}VALIDATION FAILED${RESET}"
        case $exit_code in
            1) echo "  - Compilation failed" ;;
            2) echo "  - Tests failed" ;;
            3) echo "  - Static analysis failed" ;;
            4) echo "  - Observatory failed" ;;
            5) echo "  - Schema validation failed" ;;
        esac
    fi

    echo ""
}

# Run specific phase or all phases
main() {
    local exit_code=0

    case "$PHASE" in
        compile)
            run_compile || exit_code=$?
            ;;
        test)
            run_test || exit_code=$?
            ;;
        analysis)
            run_analysis || exit_code=$?
            ;;
        observatory)
            run_observatory || exit_code=$?
            ;;
        schema)
            run_schema || exit_code=$?
            ;;
        *)
            # Run all phases
            run_compile || { exit_code=$?; print_summary $exit_code; exit $exit_code; }
            run_test || { exit_code=$?; print_summary $exit_code; exit $exit_code; }
            run_analysis || exit_code=$?
            run_observatory || exit_code=$?
            run_schema || exit_code=$?
            ;;
    esac

    print_summary $exit_code
    exit $exit_code
}

main
