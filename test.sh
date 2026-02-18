#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0 - Test Runner
#
# Usage:
#   ./test.sh                 Run all unit tests (fast, uses H2 in-memory DB)
#   ./test.sh --unit          Same as above (explicit)
#   ./test.sh --integration   Run integration tests (requires PostgreSQL or H2)
#   ./test.sh --coverage      Run unit tests + generate JaCoCo HTML/XML report
#   ./test.sh --engine        Run engine test suite only
#   ./test.sh --class=Foo     Run a single test class (e.g. TestYEngineInit)
#   ./test.sh --quick         Compile + run unit tests, no coverage overhead
#   ./test.sh --verify        Compile + full test cycle (pre-commit equivalent)
#   ./test.sh --schema        Validate YAWL specification files against XSD
#   ./test.sh --lint          Run HYPER_STANDARDS stub/mock scanner on src/
#
# Environment variables honoured:
#   TEST_THREADS    Number of test threads (default: 4)
#   DB_TYPE         Database backend: h2 (default) | postgresql | mysql
#   DB_HOST         Database hostname (default: localhost)
#   DB_PORT         Database port    (default: 5432 for postgresql)
#   DB_NAME         Database name    (default: yawl_test)
#   DB_USER         Database user    (default: yawl)
#   DB_PASSWORD     Database password
#
# Exit codes:
#   0  All tests passed
#   1  One or more tests failed
#   2  Compilation failed before tests ran
#   3  Schema validation failed
#   4  Lint / stub detection found violations
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Terminal colours
# ---------------------------------------------------------------------------
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''
fi

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_DIR="${PROJECT_DIR}/schema"
SRC_DIR="${PROJECT_DIR}/src"
VALIDATE_SCRIPT="${PROJECT_DIR}/.claude/hooks/validate-no-mocks.sh"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[PASS]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[FAIL]${NC}  $*" >&2; }
fatal()   { error "$*"; exit 1; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}----------------------------------------------------------------------${NC}"
    echo -e "${BOLD}${CYAN}  $*${NC}"
    echo -e "${BOLD}${CYAN}----------------------------------------------------------------------${NC}"
    echo ""
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
MODE="unit"
TEST_CLASS=""
TEST_THREADS="${TEST_THREADS:-4}"
DB_TYPE="${DB_TYPE:-h2}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-yawl_test}"
DB_USER="${DB_USER:-yawl}"
DB_PASSWORD="${DB_PASSWORD:-}"

for arg in "$@"; do
    case "$arg" in
        --unit)         MODE="unit" ;;
        --integration)  MODE="integration" ;;
        --coverage)     MODE="coverage" ;;
        --engine)       MODE="engine" ;;
        --quick)        MODE="quick" ;;
        --verify)       MODE="verify" ;;
        --schema)       MODE="schema" ;;
        --lint)         MODE="lint" ;;
        --class=*)      MODE="class"; TEST_CLASS="${arg#--class=}" ;;
        --help|-h)
            echo "Usage: $0 [--unit|--integration|--coverage|--engine|--quick|--verify|--schema|--lint|--class=Name]"
            echo ""
            echo "  --unit           Run all unit tests using H2 in-memory DB (default)"
            echo "  --integration    Run integration tests (requires DB service)"
            echo "  --coverage       Run unit tests and produce JaCoCo HTML report in target/site/jacoco"
            echo "  --engine         Run engine test suite only"
            echo "  --quick          Fast compile check + unit tests (no report generation)"
            echo "  --verify         Full test cycle: compile + unit + integration"
            echo "  --schema         Validate all .yawl files against YAWL_Schema4.0.xsd"
            echo "  --lint           Scan src/ for mock/stub/TODO violations"
            echo "  --class=Name     Run a single test class (simple name or fully qualified)"
            exit 0
            ;;
        *)
            fatal "Unknown argument: $arg. Use --help for usage."
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Common Maven base options
# ---------------------------------------------------------------------------
MVN_BASE=(
    mvn
    --batch-mode
    --no-transfer-progress
    -Ddatabase.type="${DB_TYPE}"
    -Dsurefire.parallel=classes
    -Dsurefire.threadCount="${TEST_THREADS}"
    -Dsurefire.useSystemClassLoader=false
)

# H2-specific Hibernate dialect (avoids needing an external DB for unit tests)
if [ "${DB_TYPE}" = "h2" ]; then
    MVN_BASE+=( -Dhibernate.dialect=org.hibernate.dialect.H2Dialect )
elif [ "${DB_TYPE}" = "postgresql" ]; then
    MVN_BASE+=(
        -Dhibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
        -DDB_HOST="${DB_HOST}"
        -DDB_PORT="${DB_PORT}"
        -DDB_NAME="${DB_NAME}"
        -DDB_USER="${DB_USER}"
    )
    [ -n "${DB_PASSWORD}" ] && MVN_BASE+=( -DDB_PASSWORD="${DB_PASSWORD}" )
fi

# ---------------------------------------------------------------------------
# Mode implementations
# ---------------------------------------------------------------------------

run_unit_tests() {
    header "Running Unit Tests"
    info "Database backend: ${DB_TYPE}"
    info "Test threads: ${TEST_THREADS}"

    cd "${PROJECT_DIR}"
    "${MVN_BASE[@]}" \
        test \
        -Dmaven.test.failure.ignore=false \
        && success "All unit tests passed." \
        || { error "Unit tests failed. See target/surefire-reports/ for details."; exit 1; }
}

run_integration_tests() {
    header "Running Integration Tests"
    info "Database backend: ${DB_TYPE}"
    warn "Integration tests require a running database service."

    cd "${PROJECT_DIR}"
    "${MVN_BASE[@]}" \
        verify \
        -DskipUnitTests=true \
        && success "All integration tests passed." \
        || { error "Integration tests failed. See target/failsafe-reports/ for details."; exit 1; }
}

run_coverage() {
    header "Running Tests with JaCoCo Coverage"
    info "Coverage report will be written to: target/site/jacoco/"

    cd "${PROJECT_DIR}"
    "${MVN_BASE[@]}" \
        clean verify \
        jacoco:report \
        jacoco:report-aggregate \
        && {
            success "Tests passed with coverage report generated."
            info "Open: ${PROJECT_DIR}/target/site/jacoco/index.html"
        } \
        || { error "Tests or coverage report failed."; exit 1; }
}

run_engine_tests() {
    header "Running Engine Test Suite"

    cd "${PROJECT_DIR}"
    "${MVN_BASE[@]}" \
        test \
        -Dtest="org.yawlfoundation.yawl.engine.*" \
        -Dmaven.test.failure.ignore=false \
        && success "Engine test suite passed." \
        || { error "Engine tests failed. See target/surefire-reports/ for details."; exit 1; }
}

run_quick() {
    header "Quick Test Run (compile + unit, no reports)"

    cd "${PROJECT_DIR}"
    info "Compiling..."
    mvn clean compile --batch-mode --no-transfer-progress -q \
        || { error "Compilation failed."; exit 2; }
    success "Compilation succeeded."

    info "Running unit tests..."
    "${MVN_BASE[@]}" \
        test \
        -Dmaven.test.failure.ignore=false \
        && success "Quick test run passed." \
        || { error "Tests failed."; exit 1; }
}

run_verify() {
    header "Full Verify Cycle (compile + unit + integration)"

    cd "${PROJECT_DIR}"
    "${MVN_BASE[@]}" \
        clean verify \
        && success "Full verify cycle passed." \
        || { error "Verify cycle failed."; exit 1; }
}

run_single_class() {
    header "Running Single Test Class: ${TEST_CLASS}"

    cd "${PROJECT_DIR}"
    "${MVN_BASE[@]}" \
        test \
        -Dtest="${TEST_CLASS}" \
        -Dmaven.test.failure.ignore=false \
        && success "Test class '${TEST_CLASS}' passed." \
        || { error "Test class '${TEST_CLASS}' failed."; exit 1; }
}

run_schema_validation() {
    header "Validating YAWL Specification Files"

    XSD_FILE="${SCHEMA_DIR}/YAWL_Schema4.0.xsd"

    if [ ! -f "${XSD_FILE}" ]; then
        fatal "Schema file not found: ${XSD_FILE}"
    fi

    if ! command -v xmllint &>/dev/null; then
        fatal "xmllint not found. Install libxml2-utils: apt-get install libxml2-utils"
    fi

    VALIDATED=0
    FAILED=0

    while IFS= read -r -d '' spec_file; do
        if xmllint --schema "${XSD_FILE}" --noout "${spec_file}" 2>&1; then
            success "Valid: ${spec_file}"
            VALIDATED=$((VALIDATED + 1))
        else
            error "Invalid: ${spec_file}"
            FAILED=$((FAILED + 1))
        fi
    done < <(find "${PROJECT_DIR}/exampleSpecs" -name "*.yawl" -print0 2>/dev/null)

    echo ""
    info "Schema validation complete: ${VALIDATED} valid, ${FAILED} invalid."

    if [ "${FAILED}" -gt 0 ]; then
        error "Schema validation failed for ${FAILED} file(s)."
        exit 3
    fi

    success "All ${VALIDATED} specification files are valid."
}

run_lint() {
    header "HYPER_STANDARDS Lint: Scanning src/ for Violations"

    if [ ! -f "${VALIDATE_SCRIPT}" ]; then
        fatal "Validation script not found: ${VALIDATE_SCRIPT}"
    fi

    chmod +x "${VALIDATE_SCRIPT}"
    cd "${PROJECT_DIR}"

    if "${VALIDATE_SCRIPT}"; then
        success "Lint passed: no mock/stub/TODO violations found."
    else
        error "Lint failed: violations detected. Fix all violations before committing."
        exit 4
    fi
}

# ---------------------------------------------------------------------------
# Dispatch
# ---------------------------------------------------------------------------
SECONDS=0

case "${MODE}" in
    unit)        run_unit_tests ;;
    integration) run_integration_tests ;;
    coverage)    run_coverage ;;
    engine)      run_engine_tests ;;
    quick)       run_quick ;;
    verify)      run_verify ;;
    class)       run_single_class ;;
    schema)      run_schema_validation ;;
    lint)        run_lint ;;
    *)           fatal "Internal error: unhandled mode '${MODE}'" ;;
esac

ELAPSED=$SECONDS
echo ""
echo -e "${BOLD}${GREEN}Completed in ${ELAPSED}s.${NC}"
