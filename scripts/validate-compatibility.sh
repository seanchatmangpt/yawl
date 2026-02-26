#!/bin/bash
#
# YAWL Java-Python Compatibility Validation Script
#
# Validates Java 25+ and GraalPy integration for YAWL workflow system
# Generates JUnit XML reports and test summaries for CI/CD
#

set -euo pipefail

# Script Configuration
readonly SCRIPT_NAME="$(basename "$0")"
readonly SCRIPT_DIR="$(dirname "$(realpath "$0")")"
readonly PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
readonly REPORT_DIR="${PROJECT_ROOT}/test-reports"
readonly JUNIT_REPORT_DIR="${REPORT_DIR}/junit"
readonly SUMMARY_FILE="${REPORT_DIR}/compatibility-summary.json"
readonly GRAALPY_MIN_VERSION="24.0.0"
readonly JAVA_MIN_VERSION="25"

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[0;33m'
readonly BLUE='\033[0;34m'
readonly NC='\033[0m' # No Color

# Test categories
declare -r TEST_CATEGORIES=(
    "basic-compatibility"
    "workflow-patterns"
    "engine-runtime"
    "serialization"
    "performance"
    "edge-cases"
)

# Global variables
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0
VERBOSITY=0
QUICK_MODE=0

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $*" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*" >&2
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_debug() {
    if [[ $VERBOSITY -ge 2 ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*" >&2
    fi
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*" >&2
}

# Print usage information
usage() {
    cat << EOF
Usage: $SCRIPT_NAME [OPTIONS]

Run all Java-Python compatibility tests for YAWL workflow system.

OPTIONS:
    --quick            Run only critical tests (faster)
    --verbose          Enable verbose output (level 2)
    --report-dir DIR    Directory for test reports (default: $REPORT_DIR)
    --help             Show this help message

EXIT CODES:
    0: All tests passed
    1: Some tests failed
    2: Environment setup failed

EXAMPLES:
    $SCRIPT_NAME
    $SCRIPT_NAME --quick --verbose --report-dir /tmp/test-reports
EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --quick)
                QUICK_MODE=1
                log_warn "Quick mode enabled - running critical tests only"
                shift
                ;;
            --verbose)
                VERBOSITY=2
                shift
                ;;
            --report-dir)
                REPORT_DIR="$2"
                shift 2
                ;;
            --help)
                usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                usage >&2
                exit 2
                ;;
        esac
    done
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Validate Java version
validate_java() {
    log_info "Validating Java installation..."

    if ! command_exists java; then
        log_error "Java not found. Please install Java 25+."
        return 1
    fi

    local java_version
    java_version=$(java -version 2>&1 | grep -oP 'version\s+\K[0-9]+\.[0-9]+' | head -1 | cut -d. -f1)

    if [[ -z "$java_version" ]]; then
        log_error "Could not determine Java version."
        return 1
    fi

    if (( java_version < JAVA_MIN_VERSION )); then
        log_error "Java version $java_version found, but $JAVA_MIN_VERSION+ required."
        return 1
    fi

    log_success "Java $java_version found (>= $JAVA_MIN_VERSION)"
    log_debug "Java version details: $(java -version 2>&1)"
    return 0
}

# Validate GraalPy installation
validate_graalpy() {
    log_info "Validating GraalPy installation..."

    # Check multiple GraalPy installation paths
    local graalpy_commands=("graalpy" "graalpy3")
    local graalpy_found=0

    for cmd in "${graalpy_commands[@]}"; do
        if command_exists "$cmd"; then
            graalpy_found=1
            local graalpy_version
            graalpy_version=$($cmd --version 2>&1 | grep -oP 'GraalPy\s+\K[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "0.0.0")
            graalpy_major=$(echo "$graalpy_version" | cut -d. -f1)
            graalpy_minor=$(echo "$graalpy_version" | cut -d. -f2)

            # Parse minimum version
            min_major=$(echo "$GRAALPY_MIN_VERSION" | cut -d. -f1)
            min_minor=$(echo "$GRAALPY_MIN_VERSION" | cut -d. -f2)

            if (( graalpy_major < min_major || (graalpy_major == min_major && graalpy_minor < min_minor) )); then
                log_error "GraalPy version $graalpy_version found, but $GRAALPY_MIN_VERSION+ required."
                return 1
            fi

            log_success "GraalPy $graalpy_version found (>= $GRAALPY_MIN_VERSION)"
            return 0
        fi
    done

    if [[ $graalpy_found -eq 0 ]]; then
        log_error "GraalPy not found. Please install GraalPy $GRAALPY_MIN_VERSION+."
        log_info "Installation options:"
        echo "  - Visit https://www.graalvm.org/upgrading-with-graalvm/"
        echo "  - Using SDKMAN: sdk install graalpy"
        echo "  - Using Homebrew: brew install graalvm"
        return 1
    fi
}

# Check Python environment
validate_python() {
    log_info "Validating Python environment..."

    if ! command_exists python3; then
        log_error "Python 3 not found."
        return 1
    fi

    # Check required Python packages
    local required_packages=("pyyaml" "pytest" "requests")
    local missing_packages=()

    for package in "${required_packages[@]}"; do
        if ! python3 -c "import $package" 2>/dev/null; then
            missing_packages+=("$package")
        fi
    done

    if [[ ${#missing_packages[@]} -gt 0 ]]; then
        log_error "Missing required Python packages: ${missing_packages[*]}"
        log_info "Installing missing packages..."

        if python3 -m pip install --user "${missing_packages[@]}" >/dev/null 2>&1; then
            log_success "Python packages installed successfully"
        else
            log_error "Failed to install Python packages. Please install manually:"
            echo "  python3 -m pip install ${missing_packages[*]}"
            return 1
        fi
    fi

    log_success "Python environment validated"
    return 0
}

# Create report directories
setup_report_directories() {
    log_info "Setting up report directories..."

    mkdir -p "$JUNIT_REPORT_DIR"
    mkdir -p "$(dirname "$SUMMARY_FILE")"

    # Clean previous reports
    find "$JUNIT_REPORT_DIR" -name "*.xml" -delete 2>/dev/null || true
}

# Convert test result to JUnit XML
generate_junit_xml() {
    local test_name="$1"
    local test_class="$2"
    local duration="$3"
    local status="$4"  # PASSED, FAILED, ERROR, SKIPPED
    local message="$5"

    local timestamp
    timestamp=$(date -u +"%Y-%m-%dT%H:%M:%S")

    local xml_file="$JUNIT_REPORT_DIR/TEST_${test_class// /_}.xml"

    cat > "$xml_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="${test_class}" tests="1" failures="$([[ "$status" == "PASSED" ]] && echo 0 || echo 1)" errors="0" skipped="$([[ "$status" == "SKIPPED" ]] && echo 1 || echo 0)" time="$duration">
    <testcase name="$test_name" classname="${test_class}" time="$duration">
EOF

    case "$status" in
        "FAILED"|"ERROR")
            cat >> "$xml_file" << EOF
      <failure message="$message" type="AssertionError">
        <![CDATA[$message]]>
      </failure>
EOF
            ;;
        "SKIPPED")
            cat >> "$xml_file" << EOF
      <skipped message="$message"/>
EOF
            ;;
    esac

    cat >> "$xml_file" << EOF
    </testcase>
  </testsuite>
</testsuites>
EOF
}

# Run a single test
run_test() {
    local test_category="$1"
    local test_name="$2"
    local test_command="$3"
    local test_class="CompatibilityTest"

    log_info "Running test: $test_name"

    local start_time
    start_time=$(date +%s.%3N)

    # Prepare test environment
    local test_dir="${PROJECT_ROOT}/tests/test-temp"
    mkdir -p "$test_dir"
    cd "$test_dir"

    # Execute test with timeout
    if timeout 300 $test_command > "${test_category}.output" 2>&1; then
        local status="PASSED"
        local message=""
    else
        local exit_code=$?
        if [[ $exit_code -eq 124 ]]; then
            status="ERROR"
            message="Test timed out after 300 seconds"
        else
            status="FAILED"
            message="Test failed with exit code $exit_code"
            if [[ $VERBOSITY -ge 1 ]]; then
                log_debug "Test output: $(cat "${test_category}.output")"
            fi
        fi
    fi

    local end_time
    end_time=$(date +%s.%3N)
    local duration
    if command_exists bc; then
        duration=$(echo "$end_time - $start_time" | bc -l)
    else
        duration=$(echo "$end_time - $start_time" | python3 -c "import sys; print(float(sys.stdin.read()))")
    fi

    # Update counters
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    case "$status" in
        "PASSED")
            PASSED_TESTS=$((PASSED_TESTS + 1))
            log_success "✓ $test_name"
            ;;
        "FAILED"|"ERROR")
            FAILED_TESTS=$((FAILED_TESTS + 1))
            log_error "✗ $test_name: $message"
            ;;
        "SKIPPED")
            SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
            log_warn "⚠ $test_name: $message"
            ;;
    esac

    # Generate JUnit report
    generate_junit_xml "$test_name" "$test_class" "$duration" "$status" "$message"

    # Cleanup
    cd "$PROJECT_ROOT"
    rm -rf "$test_dir"

    return 0
}

# Discover test files automatically
discover_tests() {
    local category="$1"
    local -n tests_ref=$2

    local search_paths=()

    case "$category" in
        "basic-compatibility")
            search_paths=(
                "${PROJECT_ROOT}/tests/java/TestBasicBridge.java"
                "${PROJECT_ROOT}/tests/java/TestSerialization.java"
                "${PROJECT_ROOT}/tests/python/test_datatypes.py"
                "${PROJECT_ROOT}/tests/python/test_exceptions.py"
            )
            ;;
        "workflow-patterns")
            search_paths=(
                "${PROJECT_ROOT}/tests/java/TestSequencePattern.java"
                "${PROJECT_ROOT}/tests/java/TestParallelSplit.java"
                "${PROJECT_ROOT}/tests/java/TestSynchronization.java"
                "${PROJECT_ROOT}/tests/python/test_choice.py"
            )
            ;;
        "engine-runtime")
            search_paths=(
                "${PROJECT_ROOT}/tests/java/TestWorkItem.java"
                "${PROJECT_ROOT}/tests/java/TestState.java"
                "${PROJECT_ROOT}/tests/python/test_state.py"
            )
            ;;
        "serialization")
            search_paths=(
                "${PROJECT_ROOT}/tests/java/TestXmlSerialization.java"
                "${PROJECT_ROOT}/tests/java/TestJsonSerialization.java"
                "${PROJECT_ROOT}/tests/python/test_xml_serialization.py"
                "${PROJECT_ROOT}/tests/python/test_json_serialization.py"
                "${PROJECT_ROOT}/tests/python/test_protobuf.py"
            )
            ;;
        "performance")
            search_paths=(
                "${PROJECT_ROOT}/tests/java/TestThroughput.java"
                "${PROJECT_ROOT}/tests/java/TestMemory.java"
                "${PROJECT_ROOT}/tests/python/test_memory.py"
                "${PROJECT_ROOT}/tests/python/test_performance.py"
            )
            ;;
        "edge-cases")
            search_paths=(
                "${PROJECT_ROOT}/tests/java/TestLargeData.java"
                "${PROJECT_ROOT}/tests/java/TestErrorRecovery.java"
                "${PROJECT_ROOT}/tests/python/test_large_data.py"
                "${PROJECT_ROOT}/tests/python/test_cleanup.py"
            )
            ;;
    esac

    # Check which test files exist
    tests_ref=()
    for test_file in "${search_paths[@]}"; do
        if [[ -f "$test_file" ]]; then
            tests_ref+=("$test_file")
        fi
    done
}

# Basic compatibility tests
run_basic_compatibility_tests() {
    local test_count=0

    # Test 1: Java-Python bridge initialization
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "basic-compatibility" "JavaPythonBridge" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-basic-bridge"
        test_count=$((test_count + 1))
    fi

    # Test 2: Data type conversion
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "basic-compatibility" "DataTypeConversion" \
            "python3 ${PROJECT_ROOT}/tests/python/test_datatypes.py"
        test_count=$((test_count + 1))
    fi

    # Test 3: Serialization round-trip
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "basic-compatibility" "SerializationRoundtrip" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-serialization"
        test_count=$((test_count + 1))
    fi

    # Test 4: Exception handling
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "basic-compatibility" "ExceptionHandling" \
            "python3 ${PROJECT_ROOT}/tests/python/test_exceptions.py"
        test_count=$((test_count + 1))
    fi
}

# Workflow pattern tests
run_workflow_pattern_tests() {
    local test_count=0

    # Test 1: Sequence pattern
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "workflow-patterns" "SequencePattern" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-sequence-pattern"
        test_count=$((test_count + 1))
    fi

    # Test 2: Parallel split
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "workflow-patterns" "ParallelSplit" \
            "python3 ${PROJECT_ROOT}/tests/python/test_parallel_split.py"
        test_count=$((test_count + 1))
    fi

    # Test 3: Synchronization
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "workflow-patterns" "Synchronization" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-sync"
        test_count=$((test_count + 1))
    fi

    # Test 4: Exclusive choice
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "workflow-patterns" "ExclusiveChoice" \
            "python3 ${PROJECT_ROOT}/tests/python/test_choice.py"
        test_count=$((test_count + 1))
    fi
}

# Engine runtime tests
run_engine_runtime_tests() {
    local test_count=0

    # Test 1: Work item execution
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "engine-runtime" "WorkItemExecution" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-workitem"
        test_count=$((test_count + 1))
    fi

    # Test 2: State management
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "engine-runtime" "StateManagement" \
            "python3 ${PROJECT_ROOT}/tests/python/test_state.py"
        test_count=$((test_count + 1))
    fi

    # Test 3: Resource allocation
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "engine-runtime" "ResourceAllocation" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-resources"
        test_count=$((test_count + 1))
    fi
}

# Serialization tests
run_serialization_tests() {
    local test_count=0

    # Test 1: XML serialization
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "serialization" "XmlSerialization" \
            "python3 ${PROJECT_ROOT}/tests/python/test_xml_serialization.py"
        test_count=$((test_count + 1))
    fi

    # Test 2: JSON serialization
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "serialization" "JsonSerialization" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-json"
        test_count=$((test_count + 1))
    fi

    # Test 3: Protocol buffers
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "serialization" "ProtocolBuffers" \
            "python3 ${PROJECT_ROOT}/tests/python/test_protobuf.py"
        test_count=$((test_count + 1))
    fi
}

# Performance tests
run_performance_tests() {
    local test_count=0

    # Test 1: Throughput test
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "performance" "Throughput" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-throughput --iterations 100"
        test_count=$((test_count + 1))
    fi

    # Test 2: Memory usage
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "performance" "MemoryUsage" \
            "python3 ${PROJECT_ROOT}/tests/python/test_memory.py"
        test_count=$((test_count + 1))
    fi

    # Test 3: Concurrent execution
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "performance" "ConcurrentExecution" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-concurrency --threads 10"
        test_count=$((test_count + 1))
    fi
}

# Edge case tests
run_edge_case_tests() {
    local test_count=0

    # Test 1: Large data handling
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "edge-cases" "LargeDataHandling" \
            "python3 ${PROJECT_ROOT}/tests/python/test_large_data.py"
        test_count=$((test_count + 1))
    fi

    # Test 2: Error recovery
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "edge-cases" "ErrorRecovery" \
            "java -jar ${PROJECT_ROOT}/yawl-graalpy.jar test-error-recovery"
        test_count=$((test_count + 1))
    fi

    # Test 3: Resource cleanup
    if [[ $QUICK_MODE -eq 0 ]] || [[ $test_count -eq 0 ]]; then
        run_test "edge-cases" "ResourceCleanup" \
            "python3 ${PROJECT_ROOT}/tests/python/test_cleanup.py"
        test_count=$((test_count + 1))
    fi
}

# Generate test summary
generate_summary() {
    log_info "Generating test summary..."

    local pass_rate
    if [[ $TOTAL_TESTS -gt 0 ]]; then
        pass_rate=$(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)
    else
        pass_rate=0
    fi

    cat > "$SUMMARY_FILE" << EOF
{
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%S")",
  "test_metadata": {
    "total_tests": $TOTAL_TESTS,
    "passed_tests": $PASSED_TESTS,
    "failed_tests": $FAILED_TESTS,
    "skipped_tests": $SKIPPED_TESTS,
    "pass_rate": "$pass_rate%",
    "mode": "quick"
  },
  "environment": {
    "java_version": "$(java -version 2>&1 | head -1)",
    "graalpy_version": "$(graalpy --version 2>&1 | head -1)",
    "python_version": "$(python3 --version 2>&1)",
    "working_directory": "$PROJECT_ROOT"
  },
  "reports": {
    "junit_dir": "$JUNIT_REPORT_DIR",
    "junit_files": "$(ls "$JUNIT_REPORT_DIR"/*.xml 2>/dev/null | wc -l | tr -d ' ')",
    "summary_file": "$SUMMARY_FILE"
  },
  "categories": {
EOF

    local first_category=1
    for category in "${TEST_CATEGORIES[@]}"; do
        if [[ $first_category -eq 0 ]]; then
            echo "," >> "$SUMMARY_FILE"
        fi
        first_category=0

        cat >> "$SUMMARY_FILE" << EOF
    "$category": {
      "tests": "$(ls "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | wc -l | tr -d ' ')",
      "passed": "$(grep -c "PASSED" "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo 0)",
      "failed": "$(grep -c "FAILED\|ERROR" "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo 0)",
      "skipped": "$(grep -c "SKIPPED" "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo 0)"
    }
EOF
    done

    cat >> "$SUMMARY_FILE" << EOF
  }
}
EOF

    log_success "Summary report generated: $SUMMARY_FILE"
}

# Print final report
print_final_report() {
    echo ""
    echo "=========================================="
    echo "YAWL Java-Python Compatibility Test Report"
    echo "=========================================="
    echo ""
    echo "Test Results:"
    echo "  Total Tests:  $TOTAL_TESTS"
    echo "  Passed:      $PASSED_TESTS"
    echo "  Failed:      $FAILED_TESTS"
    echo "  Skipped:     $SKIPPED_TESTS"
    echo ""

    if [[ $TOTAL_TESTS -gt 0 ]]; then
        local pass_rate
        pass_rate=$(echo "scale=1; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc)
        echo "  Pass Rate:   ${pass_rate}%"
    fi

    echo ""
    echo "Reports:"
    echo "  JUnit XML:   $JUNIT_REPORT_DIR/"
    echo "  Summary:     $SUMMARY_FILE"
    echo ""

    # Print category breakdown
    echo "Category Breakdown:"
    for category in "${TEST_CATEGORIES[@]}"; do
        local category_tests
        category_tests=$(ls "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | wc -l | tr -d ' ')
        if [[ $category_tests -gt 0 ]]; then
            local category_passed
            category_passed=$(grep -c "PASSED" "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo 0)
            local category_failed
            category_failed=$(grep -c "FAILED\|ERROR" "$JUNIT_REPORT_DIR"/TEST_${category// /_}* 2>/dev/null | awk '{sum+=$1} END {print sum}' || echo 0)
            echo "  $category: $category_passed/$category_tests passed"
        fi
    done
    echo ""
}

# Main execution
main() {
    log_info "Starting YAWL Java-Python compatibility validation"
    log_info "Quick mode: $([[ $QUICK_MODE -eq 1 ]] && echo "enabled" || echo "disabled")"
    log_info "Verbosity level: $VERBOSITY"
    echo ""

    # Parse arguments
    parse_args "$@"

    # Setup environment
    if ! validate_java; then
        exit 2
    fi

    if ! validate_graalpy; then
        exit 2
    fi

    if ! validate_python; then
        exit 2
    fi

    setup_report_directories

    # Run tests
    echo "Running test suites..."
    echo ""

    if [[ $QUICK_MODE -eq 1 ]]; then
        log_warn "Quick mode: Running only critical tests"
        run_basic_compatibility_tests
        run_workflow_pattern_tests
    else
        # Run all test categories
        log_info "Running compatibility tests..."
        run_basic_compatibility_tests
        run_workflow_pattern_tests
        run_engine_runtime_tests
        run_serialization_tests
        run_performance_tests
        run_edge_case_tests
    fi

    # Generate reports
    generate_summary
    print_final_report

    # Determine exit code
    if [[ $FAILED_TESTS -gt 0 ]]; then
        log_error "$FAILED_TESTS test(s) failed"
        exit 1
    elif [[ $TOTAL_TESTS -eq 0 ]]; then
        log_error "No tests were executed"
        exit 2
    else
        log_success "All tests passed"
        exit 0
    fi
}

# Run main function with all arguments
main "$@"