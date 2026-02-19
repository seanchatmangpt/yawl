#!/usr/bin/env bash
# ==========================================================================
# run-integration-tests.sh - Enhanced Integration Test Runner for YAWL
#
# Comprehensive test execution for YAWL integration tests including A2A, MCP,
# and ZAI components. Integrates with Maven build system and provides JSON
# and HTML reporting with detailed test results aggregation.
#
# Usage:
#   bash scripts/run-integration-tests.sh [options]
#
# Options:
#   --help                Show this help message
#   --module=<name>       Run tests for specific module (e.g., yawl-engine)
#   --test=<pattern>      Run tests matching pattern (e.g., **/*ZAITest*)
#   --report-dir=<dir>    Directory for reports (default: reports/test-results)
#   --verbose             Enable verbose output
#   --parallel=<N>        Use N parallel threads (default: auto-detect)
#   --skip-build          Skip Maven compilation
#   --clean               Clean build artifacts before running
#   --profile=<name>      Maven profile to use (default: agent-dx)
#   --timeout=<seconds>   Test timeout in seconds (default: 300)
#   --format=<json|html|both>  Report format (default: both)
#   --fail-fast           Stop on first test failure (default: fail-at-end)
#   --coverage            Enable code coverage with JaCoCo
#   --retry=<N>           Retry failed tests N times (default: 0)
#   --tags=<tags>         Run tests with specific JUnit 5 tags
#
# Examples:
#   bash scripts/run-integration-tests.sh --module=yawl-engine --verbose
#   bash scripts/run-integration-tests.sh --test="**/*A2ATest*" --format=json
#   bash scripts/run-integration-tests.sh --clean --parallel=4 --timeout=600
#   bash scripts/run-integration-tests.sh --tags=integration --coverage
#   bash scripts/run-integration-tests.sh --retry=2 --fail-fast
#
# Exit codes:
#   0 - All tests passed
#   1 - Some tests failed
#   2 - Configuration error
#   3 - Build failure
#   4 - Timeout exceeded
# ==========================================================================
set -euo pipefail

# ── Configuration Constants ────────────────────────────────────────────────
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly SCRIPT_VERSION="2.0.0"

# Default values
REPORT_DIR="${PROJECT_ROOT}/reports/test-results"
PARALLEL_JOBS=""
MAVEN_PROFILE="agent-dx"
TEST_TIMEOUT=300
VERBOSE=false
SKIP_BUILD=false
CLEAN_BUILD=false
TARGET_MODULE=""
TEST_PATTERN=""
REPORT_FORMAT="both"
FAIL_FAST=false
ENABLE_COVERAGE=false
RETRY_COUNT=0
TEST_TAGS=""
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
START_TIME=0

# All YAWL modules (must match pom.xml)
readonly ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)

# Integration test modules
readonly INTEGRATION_MODULES=(
    yawl-integration yawl-engine yawl-stateless
)

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly BOLD='\033[1m'
readonly NC='\033[0m' # No Color

# ── Logging Functions ──────────────────────────────────────────────────────
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" >&2
}

log_debug() {
    if [[ "$VERBOSE" == true ]]; then
        echo -e "${CYAN}[DEBUG]${NC} $1" >&2
    fi
}

log_section() {
    echo "" >&2
    echo -e "${BOLD}${BLUE}=== $1 ===${NC}" >&2
}

# ── Help Message ───────────────────────────────────────────────────────────
show_help() {
    cat << 'EOF'
Enhanced Integration Test Runner for YAWL v2.0.0
=================================================

Comprehensive test execution for YAWL integration tests including A2A, MCP,
and ZAI components. Integrates with Maven build system and provides JSON
and HTML reporting with detailed test results aggregation.

Usage:
  bash scripts/run-integration-tests.sh [options]

Options:
  --help                Show this help message
  --module=<name>       Run tests for specific module (e.g., yawl-engine)
  --test=<pattern>      Run tests matching pattern (e.g., **/*ZAITest*)
  --report-dir=<dir>    Directory for reports (default: reports/test-results)
  --verbose             Enable verbose output
  --parallel=<N>        Use N parallel threads (default: auto-detect)
  --skip-build          Skip Maven compilation
  --clean               Clean build artifacts before running
  --profile=<name>      Maven profile to use (default: agent-dx)
  --timeout=<seconds>   Test timeout in seconds (default: 300)
  --format=<format>     Report format: json, html, or both (default: both)
  --fail-fast           Stop on first test failure
  --coverage            Enable code coverage with JaCoCo
  --retry=<N>           Retry failed tests N times (default: 0)
  --tags=<tags>         Run tests with specific JUnit 5 tags (comma-separated)

Examples:
  # Run tests for a specific module with verbose output
  bash scripts/run-integration-tests.sh --module=yawl-engine --verbose

  # Run specific test pattern with JSON output only
  bash scripts/run-integration-tests.sh --test="**/*A2ATest*" --format=json

  # Clean build and run with parallel execution
  bash scripts/run-integration-tests.sh --clean --parallel=4 --timeout=600

  # Run integration tests with coverage
  bash scripts/run-integration-tests.sh --tags=integration --coverage

  # Retry failed tests up to 2 times with fail-fast
  bash scripts/run-integration-tests.sh --retry=2 --fail-fast

Exit codes:
  0 - All tests passed
  1 - Some tests failed
  2 - Configuration error
  3 - Build failure
  4 - Timeout exceeded

Environment variables:
  DX_OFFLINE=1          Force offline Maven mode
  DX_VERBOSE=1          Enable verbose Maven output
  MAVEN_OPTS            JVM options for Maven

For more information, see:
  https://github.com/yawlfoundation/yawl/docs/testing.md
EOF
}

# ── Argument Parsing ───────────────────────────────────────────────────────
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help|-h)
                show_help
                exit 0
                ;;
            --module=*)
                TARGET_MODULE="${1#*=}"
                validate_module "$TARGET_MODULE"
                ;;
            --test=*)
                TEST_PATTERN="${1#*=}"
                ;;
            --report-dir=*)
                REPORT_DIR="${1#*=}"
                ;;
            --verbose|-v)
                VERBOSE=true
                ;;
            --parallel=*)
                PARALLEL_JOBS="${1#*=}"
                if ! [[ "$PARALLEL_JOBS" =~ ^[0-9]+$ ]]; then
                    log_error "Invalid parallel value: $PARALLEL_JOBS (must be a number)"
                    exit 2
                fi
                ;;
            --skip-build)
                SKIP_BUILD=true
                ;;
            --clean)
                CLEAN_BUILD=true
                ;;
            --profile=*)
                MAVEN_PROFILE="${1#*=}"
                ;;
            --timeout=*)
                TEST_TIMEOUT="${1#*=}"
                if ! [[ "$TEST_TIMEOUT" =~ ^[0-9]+$ ]]; then
                    log_error "Invalid timeout value: $TEST_TIMEOUT (must be a number)"
                    exit 2
                fi
                ;;
            --format=*)
                REPORT_FORMAT="${1#*=}"
                if [[ ! "$REPORT_FORMAT" =~ ^(json|html|both)$ ]]; then
                    log_error "Invalid format: $REPORT_FORMAT (must be json, html, or both)"
                    exit 2
                fi
                ;;
            --fail-fast)
                FAIL_FAST=true
                ;;
            --coverage)
                ENABLE_COVERAGE=true
                ;;
            --retry=*)
                RETRY_COUNT="${1#*=}"
                if ! [[ "$RETRY_COUNT" =~ ^[0-9]+$ ]]; then
                    log_error "Invalid retry value: $RETRY_COUNT (must be a number)"
                    exit 2
                fi
                ;;
            --tags=*)
                TEST_TAGS="${1#*=}"
                ;;
            -*)
                log_error "Unknown option: $1"
                show_help
                exit 2
                ;;
            *)
                log_error "Unexpected argument: $1"
                show_help
                exit 2
                ;;
        esac
        shift
    done

    # Auto-detect parallel jobs if not specified
    if [[ -z "$PARALLEL_JOBS" ]]; then
        if command -v nproc &> /dev/null; then
            PARALLEL_JOBS=$(nproc)
        elif command -v sysctl &> /dev/null; then
            PARALLEL_JOBS=$(sysctl -n hw.ncpu 2>/dev/null || echo 4)
        else
            PARALLEL_JOBS=4
        fi
    fi

    log_debug "Configuration:
    TARGET_MODULE=$TARGET_MODULE
    TEST_PATTERN=$TEST_PATTERN
    REPORT_DIR=$REPORT_DIR
    VERBOSE=$VERBOSE
    PARALLEL_JOBS=$PARALLEL_JOBS
    SKIP_BUILD=$SKIP_BUILD
    CLEAN_BUILD=$CLEAN_BUILD
    MAVEN_PROFILE=$MAVEN_PROFILE
    TEST_TIMEOUT=$TEST_TIMEOUT
    REPORT_FORMAT=$REPORT_FORMAT
    FAIL_FAST=$FAIL_FAST
    ENABLE_COVERAGE=$ENABLE_COVERAGE
    RETRY_COUNT=$RETRY_COUNT
    TEST_TAGS=$TEST_TAGS"
}

# ── Module Validation ──────────────────────────────────────────────────────
validate_module() {
    local module="$1"
    local found=false
    for m in "${ALL_MODULES[@]}"; do
        if [[ "$m" == "$module" ]]; then
            found=true
            break
        fi
    done
    if [[ "$found" == false ]]; then
        log_error "Invalid module: $module"
        log_info "Available modules: ${ALL_MODULES[*]}"
        exit 2
    fi
}

# ── Environment Validation ─────────────────────────────────────────────────
validate_environment() {
    log_section "Environment Validation"

    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java is not installed or not in PATH"
        exit 2
    fi

    # Check Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed or not in PATH"
        exit 2
    fi

    # Check Java version
    local java_version
    java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
    log_info "Java version: $(java -version 2>&1 | head -n1)"
    log_info "Maven version: $(mvn --version 2>&1 | head -n1)"

    if [[ $java_version -lt 21 ]]; then
        log_warn "Java version 21+ is recommended for optimal performance (detected: $java_version)"
    fi

    # Check if we're in the correct directory
    if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
        log_error "Not in YAWL project root directory. pom.xml not found at ${PROJECT_ROOT}"
        exit 2
    fi

    # Create report directory
    mkdir -p "$REPORT_DIR"
    mkdir -p "${REPORT_DIR}/raw"
    mkdir -p "${REPORT_DIR}/history"

    # Check for required tools for reporting
    if [[ "$REPORT_FORMAT" == "html" || "$REPORT_FORMAT" == "both" ]]; then
        if ! command -v xmllint &> /dev/null; then
            log_warn "xmllint not found - HTML report may have limited functionality"
        fi
    fi

    log_success "Environment validation passed"
}

# ── Project Build ──────────────────────────────────────────────────────────
build_project() {
    if [[ "$SKIP_BUILD" == true ]]; then
        log_info "Skipping build as requested"
        return 0
    fi

    log_section "Building Project"

    # Prefer mvnd if available
    local mvn_cmd="mvn"
    if command -v mvnd &> /dev/null; then
        mvn_cmd="mvnd"
        log_debug "Using Maven Daemon (mvnd) for faster builds"
    fi

    # Build command with parallel execution
    local build_args=(
        "-T" "${PARALLEL_JOBS}C"
        "-P" "$MAVEN_PROFILE"
    )

    # Add offline flag if available
    if [[ "${DX_OFFLINE:-auto}" == "1" ]] || [[ -d "${HOME}/.m2/repository/org/yawlfoundation/yawl-parent" ]]; then
        build_args+=("-o")
        log_debug "Using offline mode"
    fi

    # Add quiet flag unless verbose
    if [[ "$VERBOSE" != true && "${DX_VERBOSE:-0}" != "1" ]]; then
        build_args+=("-q")
    fi

    # Add clean if requested
    local goals=()
    if [[ "$CLEAN_BUILD" == true ]]; then
        goals+=("clean")
    fi
    goals+=("compile")

    # Add module targeting
    if [[ -n "$TARGET_MODULE" ]]; then
        build_args+=("-pl" "$TARGET_MODULE" "-am")
    fi

    # Add coverage if enabled
    if [[ "$ENABLE_COVERAGE" == true ]]; then
        build_args+=("-P" "coverage")
    fi

    log_info "Build command: $mvn_cmd ${goals[*]} ${build_args[*]}"

    local build_start build_end build_duration
    build_start=$(date +%s)

    set +e
    if [[ "$VERBOSE" == true ]]; then
        $mvn_cmd "${goals[@]}" "${build_args[@]}"
    else
        $mvn_cmd "${goals[@]}" "${build_args[@]}" > "${REPORT_DIR}/raw/build-${TIMESTAMP}.log" 2>&1
    fi
    local build_exit_code=$?
    set -e

    build_end=$(date +%s)
    build_duration=$((build_end - build_start))

    if [[ $build_exit_code -ne 0 ]]; then
        log_error "Build failed after ${build_duration}s"
        if [[ "$VERBOSE" != true ]]; then
            log_error "Build log tail:"
            tail -30 "${REPORT_DIR}/raw/build-${TIMESTAMP}.log" >&2 || true
        fi
        exit 3
    fi

    log_success "Project built successfully in ${build_duration}s"
}

# ── Test Execution ──────────────────────────────────────────────────────────
run_tests() {
    log_section "Running Tests"

    # Prefer mvnd if available
    local mvn_cmd="mvn"
    if command -v mvnd &> /dev/null; then
        mvn_cmd="mvnd"
    fi

    # Build test command
    local test_args=(
        "-T" "${PARALLEL_JOBS}C"
        "test"
        "-P" "$MAVEN_PROFILE"
    )

    # Add offline flag if available
    if [[ "${DX_OFFLINE:-auto}" == "1" ]] || [[ -d "${HOME}/.m2/repository/org/yawlfoundation/yawl-parent" ]]; then
        test_args+=("-o")
    fi

    # Add quiet flag unless verbose
    if [[ "$VERBOSE" != true && "${DX_VERBOSE:-0}" != "1" ]]; then
        test_args+=("-q")
    fi

    # Add test pattern if specified
    if [[ -n "$TEST_PATTERN" ]]; then
        test_args+=("-Dtest=${TEST_PATTERN}")
    fi

    # Add timeout
    test_args+=("-Dmaven.test.timeout.seconds=${TEST_TIMEOUT}")
    test_args+=("-Dsurefire.timeout=${TEST_TIMEOUT}")

    # Add module targeting
    if [[ -n "$TARGET_MODULE" ]]; then
        test_args+=("-pl" "$TARGET_MODULE" "-am")
    fi

    # Add fail strategy
    if [[ "$FAIL_FAST" == true ]]; then
        test_args+=("--fail-fast")
    else
        test_args+=("--fail-at-end")
    fi

    # Add coverage if enabled
    if [[ "$ENABLE_COVERAGE" == true ]]; then
        test_args+=("-P" "coverage")
    fi

    # Add JUnit 5 tags if specified
    if [[ -n "$TEST_TAGS" ]]; then
        test_args+=("-Dgroups=${TEST_TAGS}")
    fi

    # Configure retry
    if [[ $RETRY_COUNT -gt 0 ]]; then
        test_args+=("-Dsurefire.rerunFailingTestsCount=${RETRY_COUNT}")
    fi

    # Add verbose test output if requested
    if [[ "$VERBOSE" == true ]]; then
        test_args+=("-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO")
    else
        test_args+=("-Dorg.slf4j.simpleLogger.defaultLogLevel=WARN")
    fi

    # Generate surefire reports in XML format for parsing
    test_args+=("-Dsurefire.useFile=true")

    log_info "Test command: $mvn_cmd ${test_args[*]}"
    log_info "Timeout: ${TEST_TIMEOUT}s, Parallel: ${PARALLEL_JOBS}C, Retry: ${RETRY_COUNT}"

    local test_start test_end
    test_start=$(date +%s)

    # Run tests
    set +e
    if [[ "$VERBOSE" == true ]]; then
        $mvn_cmd "${test_args[@]}" 2>&1 | tee "${REPORT_DIR}/raw/test-output-${TIMESTAMP}.log"
    else
        $mvn_cmd "${test_args[@]}" > "${REPORT_DIR}/raw/test-output-${TIMESTAMP}.log" 2>&1
    fi
    local test_exit_code=$?
    set -e

    test_end=$(date +%s)
    local test_duration=$((test_end - test_start))

    log_info "Test execution completed in ${test_duration}s"

    # Store exit code for later
    TEST_EXIT_CODE=$test_exit_code
    TEST_DURATION=$test_duration
}

# ── Test Results Processing ────────────────────────────────────────────────
process_test_results() {
    log_section "Processing Test Results"

    local report_file="${REPORT_DIR}/test-results-${TIMESTAMP}.json"
    local summary_file="${REPORT_DIR}/test-summary-${TIMESTAMP}.json"

    # Initialize counters
    local total_tests=0
    local passed_tests=0
    local failed_tests=0
    local skipped_tests=0
    local errors_tests=0
    local -a module_results=()
    local -a test_failures=()

    # Find all surefire report directories
    local surefire_dirs=()
    if [[ -n "$TARGET_MODULE" ]]; then
        surefire_dirs+=("${PROJECT_ROOT}/${TARGET_MODULE}/target/surefire-reports")
        surefire_dirs+=("${PROJECT_ROOT}/${TARGET_MODULE}/target/failsafe-reports")
    else
        for module in "${ALL_MODULES[@]}"; do
            if [[ -d "${PROJECT_ROOT}/${module}/target/surefire-reports" ]]; then
                surefire_dirs+=("${PROJECT_ROOT}/${module}/target/surefire-reports")
            fi
            if [[ -d "${PROJECT_ROOT}/${module}/target/failsafe-reports" ]]; then
                surefire_dirs+=("${PROJECT_ROOT}/${module}/target/failsafe-reports")
            fi
        done
    fi

    log_debug "Scanning surefire directories: ${surefire_dirs[*]}"

    # Parse XML reports from each module
    for surefire_dir in "${surefire_dirs[@]}"; do
        if [[ ! -d "$surefire_dir" ]]; then
            continue
        fi

        local module_name
        module_name=$(echo "$surefire_dir" | sed 's|.*/\([^/]*\)/target/.*|\1|')

        local module_tests=0
        local module_passed=0
        local module_failed=0
        local module_skipped=0
        local module_errors=0

        # Parse TEST-*.xml files
        for xml_file in "$surefire_dir"/TEST-*.xml; do
            if [[ ! -f "$xml_file" ]]; then
                continue
            fi

            local test_class
            test_class=$(basename "$xml_file" | sed 's/TEST-//;s/.xml//')

            # Extract test counts from XML
            local tests_in_file=0
            local failures_in_file=0
            local errors_in_file=0
            local skipped_in_file=0

            # Use grep for XML parsing (portable)
            if command -v xmllint &> /dev/null; then
                tests_in_file=$(xmllint --xpath 'string(//testsuite/@tests)' "$xml_file" 2>/dev/null || echo "0")
                failures_in_file=$(xmllint --xpath 'string(//testsuite/@failures)' "$xml_file" 2>/dev/null || echo "0")
                errors_in_file=$(xmllint --xpath 'string(//testsuite/@errors)' "$xml_file" 2>/dev/null || echo "0")
                skipped_in_file=$(xmllint --xpath 'string(//testsuite/@skipped)' "$xml_file" 2>/dev/null || echo "0")
            else
                # Fallback to grep parsing
                tests_in_file=$(grep -oP 'tests="\K[^"]+' "$xml_file" | head -1 || echo "0")
                failures_in_file=$(grep -oP 'failures="\K[^"]+' "$xml_file" | head -1 || echo "0")
                errors_in_file=$(grep -oP 'errors="\K[^"]+' "$xml_file" | head -1 || echo "0")
                skipped_in_file=$(grep -oP 'skipped="\K[^"]+' "$xml_file" | head -1 || echo "0")
            fi

            # Convert to integers (default to 0 if empty)
            tests_in_file=${tests_in_file:-0}
            failures_in_file=${failures_in_file:-0}
            errors_in_file=${errors_in_file:-0}
            skipped_in_file=${skipped_in_file:-0}

            module_tests=$((module_tests + tests_in_file))
            module_failed=$((module_failed + failures_in_file))
            module_errors=$((module_errors + errors_in_file))
            module_skipped=$((module_skipped + skipped_in_file))

            # Extract failure details
            if [[ $failures_in_file -gt 0 || $errors_in_file -gt 0 ]]; then
                # Find failed test cases
                while IFS= read -r failure_line; do
                    if [[ -n "$failure_line" ]]; then
                        test_failures+=("$failure_line")
                    fi
                done < <(grep -oP '<testcase[^>]*>.*?</testcase>' "$xml_file" 2>/dev/null | grep -E '<failure|<error' || true)
            fi
        done

        module_passed=$((module_tests - module_failed - module_errors - module_skipped))

        if [[ $module_tests -gt 0 ]]; then
            module_results+=("{\"name\":\"${module_name}\",\"total\":${module_tests},\"passed\":${module_passed},\"failed\":${module_failed},\"errors\":${module_errors},\"skipped\":${module_skipped}}")
        fi

        total_tests=$((total_tests + module_tests))
        passed_tests=$((passed_tests + module_passed))
        failed_tests=$((failed_tests + module_failed))
        errors_tests=$((errors_tests + module_errors))
        skipped_tests=$((skipped_tests + module_skipped))
    done

    # Calculate success rate
    local success_rate="0.00"
    if [[ $total_tests -gt 0 ]]; then
        success_rate=$(awk "BEGIN {printf \"%.2f\", ($passed_tests / $total_tests) * 100}")
    fi

    # Determine overall status
    local overall_status="success"
    if [[ $failed_tests -gt 0 || $errors_tests -gt 0 ]]; then
        overall_status="failed"
    elif [[ $total_tests -eq 0 ]]; then
        overall_status="no_tests"
    fi

    # Build module results JSON array
    local modules_json="["
    local first=true
    for module_json in "${module_results[@]}"; do
        if [[ "$first" == true ]]; then
            first=false
        else
            modules_json+=","
        fi
        modules_json+="$module_json"
    done
    modules_json+="]"

    # Build failures JSON array
    local failures_json="["
    first=true
    for failure in "${test_failures[@]:0}"; do
        if [[ "$first" == true ]]; then
            first=false
        else
            failures_json+=","
        fi
        failures_json+="\"${failure//\"/\\\"}\""
    done
    failures_json+="]"

    # Create detailed JSON report
    cat > "$report_file" << EOF
{
  "version": "${SCRIPT_VERSION}",
  "timestamp": "$(date -Iseconds)",
  "execution": {
    "start_time": "$(date -r $START_TIME -Iseconds 2>/dev/null || date -Iseconds)",
    "duration_seconds": ${TEST_DURATION:-0},
    "timeout_seconds": ${TEST_TIMEOUT},
    "parallel_jobs": ${PARALLEL_JOBS},
    "retry_count": ${RETRY_COUNT}
  },
  "configuration": {
    "maven_profile": "${MAVEN_PROFILE}",
    "target_module": "${TARGET_MODULE:-all}",
    "test_pattern": "${TEST_PATTERN:-*}",
    "test_tags": "${TEST_TAGS:-none}",
    "fail_fast": ${FAIL_FAST},
    "coverage_enabled": ${ENABLE_COVERAGE}
  },
  "summary": {
    "total_tests": ${total_tests},
    "passed": ${passed_tests},
    "failed": ${failed_tests},
    "errors": ${errors_tests},
    "skipped": ${skipped_tests},
    "success_rate": ${success_rate},
    "status": "${overall_status}"
  },
  "modules": ${modules_json},
  "failures": ${failures_json},
  "environment": {
    "java_version": "$(java -version 2>&1 | head -n1 | cut -d'"' -f2)",
    "maven_version": "$(mvn --version 2>&1 | head -n1 | cut -d' ' -f3)",
    "os": "$(uname -s)",
    "os_version": "$(uname -r)",
    "hostname": "$(hostname 2>/dev/null || echo 'unknown')"
  }
}
EOF

    # Create summary JSON
    cat > "$summary_file" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "status": "${overall_status}",
  "summary": {
    "total": ${total_tests},
    "passed": ${passed_tests},
    "failed": ${failed_tests},
    "errors": ${errors_tests},
    "skipped": ${skipped_tests},
    "success_rate": ${success_rate}
  },
  "duration_seconds": ${TEST_DURATION:-0},
  "report_file": "test-results-${TIMESTAMP}.json"
}
EOF

    # Store for later use
    TOTAL_TESTS=$total_tests
    PASSED_TESTS=$passed_tests
    FAILED_TESTS=$((failed_tests + errors_tests))
    SKIPPED_TESTS=$skipped_tests
    SUCCESS_RATE=$success_rate
    OVERALL_STATUS=$overall_status
    REPORT_FILE=$report_file
    SUMMARY_FILE=$summary_file

    log_success "Test results processed: $report_file"
    log_info "Total: ${total_tests}, Passed: ${passed_tests}, Failed: ${failed_tests}, Errors: ${errors_tests}, Skipped: ${skipped_tests}"
}

# ── HTML Report Generation ────────────────────────────────────────────────
generate_html_report() {
    if [[ "$REPORT_FORMAT" != "html" && "$REPORT_FORMAT" != "both" ]]; then
        return 0
    fi

    log_section "Generating HTML Report"

    local html_file="${REPORT_DIR}/test-report-${TIMESTAMP}.html"

    # Calculate color based on success rate
    local rate_color="#dc3545"  # Red
    if (( $(echo "$SUCCESS_RATE >= 80" | bc -l 2>/dev/null || echo "0") )); then
        rate_color="#28a745"  # Green
    elif (( $(echo "$SUCCESS_RATE >= 50" | bc -l 2>/dev/null || echo "0") )); then
        rate_color="#ffc107"  # Yellow
    fi

    # Build module rows from report file
    local module_rows=""
    if [[ -f "$REPORT_FILE" ]]; then
        # Parse modules from JSON (simple approach using grep/sed)
        local modules_content
        modules_content=$(grep -o '"modules":\[.*\]' "$REPORT_FILE" | sed 's/"modules"://')
        # This is a simplified approach - in production, use jq
        while IFS= read -r line; do
            if [[ -n "$line" ]]; then
                local mod_name=$(echo "$line" | grep -o '"name":"[^"]*"' | cut -d'"' -f4)
                local mod_total=$(echo "$line" | grep -o '"total":[0-9]*' | cut -d':' -f2)
                local mod_passed=$(echo "$line" | grep -o '"passed":[0-9]*' | cut -d':' -f2)
                local mod_failed=$(echo "$line" | grep -o '"failed":[0-9]*' | cut -d':' -f2)

                if [[ -n "$mod_name" ]]; then
                    local mod_status="passed"
                    local status_class="status-passed"
                    if [[ ${mod_failed:-0} -gt 0 ]]; then
                        mod_status="failed"
                        status_class="status-failed"
                    fi

                    module_rows+="<tr><td>${mod_name}</td><td class=\"${status_class}\">${mod_status}</td><td>${mod_total:-0}</td><td>${mod_passed:-0}</td><td>${mod_failed:-0}</td></tr>"
                fi
            fi
        done < <(echo "$modules_content" | grep -o '{[^}]*}' 2>/dev/null || true)
    fi

    # Generate HTML
    cat > "$html_file" << EOF
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>YAWL Integration Test Report - ${TIMESTAMP}</title>
    <style>
        :root {
            --primary-color: #2c3e50;
            --success-color: #28a745;
            --danger-color: #dc3545;
            --warning-color: #ffc107;
            --info-color: #17a2b8;
            --light-bg: #f8f9fa;
            --border-color: #dee2e6;
        }
        * { box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
            margin: 0;
            padding: 20px;
            background: var(--light-bg);
            color: #333;
            line-height: 1.6;
        }
        .container { max-width: 1200px; margin: 0 auto; }
        .header {
            background: linear-gradient(135deg, var(--primary-color), #3498db);
            color: white;
            padding: 30px;
            border-radius: 10px;
            margin-bottom: 30px;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        }
        .header h1 { margin: 0 0 10px 0; font-size: 2em; }
        .header p { margin: 5px 0; opacity: 0.9; }
        .summary {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
            gap: 20px;
            margin-bottom: 30px;
        }
        .stat-box {
            background: white;
            padding: 20px;
            border-radius: 10px;
            text-align: center;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
            border-left: 4px solid var(--info-color);
        }
        .stat-box h3 { margin: 0 0 10px 0; font-size: 0.9em; color: #666; }
        .stat-box .value { font-size: 2em; font-weight: bold; }
        .stat-box.total { border-left-color: var(--primary-color); }
        .stat-box.passed { border-left-color: var(--success-color); }
        .stat-box.passed .value { color: var(--success-color); }
        .stat-box.failed { border-left-color: var(--danger-color); }
        .stat-box.failed .value { color: var(--danger-color); }
        .stat-box.skipped { border-left-color: var(--warning-color); }
        .stat-box.skipped .value { color: #856404; }
        .rate-box {
            background: white;
            padding: 30px;
            border-radius: 10px;
            text-align: center;
            margin-bottom: 30px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        }
        .rate-box h2 { margin: 0 0 15px 0; color: var(--primary-color); }
        .rate-value { font-size: 4em; font-weight: bold; }
        .progress-bar {
            background: #e9ecef;
            border-radius: 10px;
            height: 20px;
            margin-top: 20px;
            overflow: hidden;
        }
        .progress-fill {
            height: 100%;
            border-radius: 10px;
            transition: width 0.5s ease;
        }
        table {
            width: 100%;
            background: white;
            border-radius: 10px;
            overflow: hidden;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
            border-collapse: collapse;
        }
        th, td { padding: 15px; text-align: left; }
        th { background: var(--primary-color); color: white; font-weight: 600; }
        tr:nth-child(even) { background: var(--light-bg); }
        tr:hover { background: #e3f2fd; }
        .status-passed { color: var(--success-color); font-weight: 600; }
        .status-failed { color: var(--danger-color); font-weight: 600; }
        .footer {
            margin-top: 30px;
            padding: 20px;
            text-align: center;
            color: #666;
            font-size: 0.9em;
        }
        .footer a { color: var(--info-color); text-decoration: none; }
        .footer a:hover { text-decoration: underline; }
        .config-info {
            background: white;
            padding: 20px;
            border-radius: 10px;
            margin-bottom: 30px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        }
        .config-info h3 { margin-top: 0; color: var(--primary-color); }
        .config-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 10px;
        }
        .config-item { padding: 5px 0; }
        .config-label { font-weight: 600; color: #666; }
        .config-value { color: #333; }
        @media (max-width: 768px) {
            .header h1 { font-size: 1.5em; }
            .stat-box .value { font-size: 1.5em; }
            .rate-value { font-size: 2.5em; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>YAWL Integration Test Report</h1>
            <p>Generated: $(date)</p>
            <p>Profile: ${MAVEN_PROFILE} | Parallel: ${PARALLEL_JOBS}C | Timeout: ${TEST_TIMEOUT}s</p>
        </div>

        <div class="summary">
            <div class="stat-box total">
                <h3>Total Tests</h3>
                <div class="value">${TOTAL_TESTS}</div>
            </div>
            <div class="stat-box passed">
                <h3>Passed</h3>
                <div class="value">${PASSED_TESTS}</div>
            </div>
            <div class="stat-box failed">
                <h3>Failed</h3>
                <div class="value">${FAILED_TESTS}</div>
            </div>
            <div class="stat-box skipped">
                <h3>Skipped</h3>
                <div class="value">${SKIPPED_TESTS}</div>
            </div>
        </div>

        <div class="rate-box">
            <h2>Success Rate</h2>
            <div class="rate-value" style="color: ${rate_color};">${SUCCESS_RATE}%</div>
            <div class="progress-bar">
                <div class="progress-fill" style="width: ${SUCCESS_RATE}%; background: ${rate_color};"></div>
            </div>
        </div>

        <div class="config-info">
            <h3>Test Configuration</h3>
            <div class="config-grid">
                <div class="config-item"><span class="config-label">Module:</span> <span class="config-value">${TARGET_MODULE:-All Modules}</span></div>
                <div class="config-item"><span class="config-label">Pattern:</span> <span class="config-value">${TEST_PATTERN:-*}</span></div>
                <div class="config-item"><span class="config-label">Tags:</span> <span class="config-value">${TEST_TAGS:-none}</span></div>
                <div class="config-item"><span class="config-label">Retry:</span> <span class="config-value">${RETRY_COUNT}</span></div>
                <div class="config-item"><span class="config-label">Fail Fast:</span> <span class="config-value">${FAIL_FAST}</span></div>
                <div class="config-item"><span class="config-label">Coverage:</span> <span class="config-value">${ENABLE_COVERAGE}</span></div>
            </div>
        </div>

        <h2>Module Results</h2>
        <table>
            <thead>
                <tr>
                    <th>Module</th>
                    <th>Status</th>
                    <th>Total</th>
                    <th>Passed</th>
                    <th>Failed</th>
                </tr>
            </thead>
            <tbody>
                ${module_rows:-<tr><td colspan="5" style="text-align:center;">No module data available</td></tr>}
            </tbody>
        </table>

        <div class="footer">
            <p>
                <a href="test-results-${TIMESTAMP}.json">JSON Results</a> |
                <a href="test-summary-${TIMESTAMP}.json">Summary JSON</a> |
                <a href="raw/test-output-${TIMESTAMP}.log">Raw Output</a>
            </p>
            <p>Generated by YAWL Integration Test Runner v${SCRIPT_VERSION}</p>
        </div>
    </div>
</body>
</html>
EOF

    log_success "HTML report generated: $html_file"
    HTML_REPORT_FILE=$html_file
}

# ── Display Results ────────────────────────────────────────────────────────
display_results() {
    log_section "Test Results Summary"

    echo ""
    echo "========================================"
    echo "        TEST RESULTS SUMMARY"
    echo "========================================"
    echo ""
    printf "  %-15s %s\n" "Total:" "${TOTAL_TESTS}"
    printf "  %-15s %s\n" "Passed:" "${PASSED_TESTS}"
    printf "  %-15s %s\n" "Failed:" "${FAILED_TESTS}"
    printf "  %-15s %s\n" "Skipped:" "${SKIPPED_TESTS}"
    printf "  %-15s %s%%\n" "Success Rate:" "${SUCCESS_RATE}"
    printf "  %-15s %ss\n" "Duration:" "${TEST_DURATION:-0}"
    echo ""
    echo "========================================"

    if [[ "$OVERALL_STATUS" == "success" ]]; then
        log_success "All tests passed!"
        echo ""
        log_info "Reports generated:"
        log_info "  - JSON: ${REPORT_FILE}"
        if [[ "$REPORT_FORMAT" == "html" || "$REPORT_FORMAT" == "both" ]]; then
            log_info "  - HTML: ${HTML_REPORT_FILE}"
        fi
        exit 0
    elif [[ "$OVERALL_STATUS" == "no_tests" ]]; then
        log_warn "No tests were executed"
        exit 0
    else
        log_error "Some tests failed!"
        echo ""
        log_info "Reports generated:"
        log_info "  - JSON: ${REPORT_FILE}"
        if [[ "$REPORT_FORMAT" == "html" || "$REPORT_FORMAT" == "both" ]]; then
            log_info "  - HTML: ${HTML_REPORT_FILE}"
        fi
        log_info "  - Raw output: ${REPORT_DIR}/raw/test-output-${TIMESTAMP}.log"

        # Show failure summary
        if [[ -f "${REPORT_DIR}/raw/test-output-${TIMESTAMP}.log" ]]; then
            echo ""
            log_info "Failure details (last 50 lines):"
            tail -50 "${REPORT_DIR}/raw/test-output-${TIMESTAMP}.log" | grep -A 5 "FAILURE\|ERROR" || true
        fi
        exit 1
    fi
}

# ── Cleanup Function ───────────────────────────────────────────────────────
cleanup() {
    local exit_code=$?

    # Remove old reports (keep last 10)
    if [[ -d "${REPORT_DIR}" ]]; then
        ls -t "${REPORT_DIR}"/test-results-*.json 2>/dev/null | tail -n +11 | xargs -r rm -f
        ls -t "${REPORT_DIR}"/test-summary-*.json 2>/dev/null | tail -n +11 | xargs -r rm -f
        ls -t "${REPORT_DIR}"/test-report-*.html 2>/dev/null | tail -n +11 | xargs -r rm -f
    fi

    exit $exit_code
}

# Set trap for cleanup
trap cleanup EXIT

# ── Main Execution ──────────────────────────────────────────────────────────
main() {
    # Record start time
    START_TIME=$(date +%s)

    echo ""
    echo "========================================"
    echo " YAWL Integration Test Runner v${SCRIPT_VERSION}"
    echo "========================================"
    echo ""

    # Parse arguments
    parse_args "$@"

    # Change to project root
    cd "$PROJECT_ROOT"

    # Validate environment
    validate_environment

    # Build project if needed
    build_project

    # Run tests
    run_tests

    # Process test results
    process_test_results

    # Generate HTML report
    generate_html_report

    # Display results
    display_results
}

# Run main function with all arguments
main "$@"
