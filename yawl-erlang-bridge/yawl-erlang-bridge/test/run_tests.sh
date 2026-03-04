#!/bin/bash

# Script to run YAWL Process Mining Bridge tests
# Provides multiple ways to run tests with proper environment setup

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to setup environment
setup_environment() {
    print_status "Setting up test environment..."

    # Check if we're in the correct directory
    if [ ! -f "src/process_mining_bridge.erl" ]; then
        print_error "Not in the yawl-erlang-bridge directory!"
        exit 1
    fi

    # Set Erlang paths
    export ERL_LIBS="./_build/default/lib"
    export ERL_FLAGS="-pa eibn -pa $ERL_LIBS/*/ebin"

    # Check required tools
    if ! command_exists erl; then
        print_error "Erlang/OTP is not installed or not in PATH"
        exit 1
    fi

    if ! command_exists make; then
        print_error "Make is not installed"
        exit 1
    fi

    print_status "Environment setup complete"
}

# Function to check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."

    # Check if NIF library exists
    if [ ! -f "priv/yawl_process_mining.so" ]; then
        print_warning "NIF library not found at priv/yawl_process_mining.so"
        print_warning "Tests will run with fallback implementations only"
    fi

    # Check test data
    if [ ! -f "test/test_data/sample_ocel.json" ]; then
        print_error "Test data not found at test/test_data/sample_ocel.json"
        exit 1
    fi

    if [ ! -f "test/test_data/sample_xes.xes" ]; then
        print_error "Test data not found at test/test_data/sample_xes.xes"
        exit 1
    fi

    print_status "Prerequisites check complete"
}

# Function to compile modules
compile_modules() {
    print_status "Compiling test modules..."

    # Create ebin directory if it doesn't exist
    mkdir -p ebin

    # Compile all test modules
    erl -make 2>/dev/null || {
        print_error "Compilation failed"
        exit 1
    }

    print_status "Compilation complete"
}

# Function to run tests with EUnit
run_eunit_tests() {
    local test_module="$1"

    print_status "Running EUnit tests: $test_module"

    # Run specific test module or all tests
    if [ -n "$test_module" ]; then
        erl $ERL_FLAGS \
            -eval "c(test_suite), test_suite:run_specific_tests([$test_module]), halt(0)"
    else
        erl $ERL_FLAGS \
            -eval "c(test_suite), test_suite:run_all_tests(), halt(0)"
    fi
}

# Function to run tests with Common Test
run_common_tests() {
    print_status "Running Common Test suite..."

    ct_run -dir test -suite process_mining_bridge_SUITE -v
}

# Function to run tests with escript
run_escript_tests() {
    local test_module="$1"

    print_status "Running tests with escript runner..."

    if [ -n "$test_module" ]; then
        ./escript_runner.escript "$test_module"
    else
        ./escript_runner.escript all
    fi
}

# Function to run performance tests
run_performance_tests() {
    print_status "Running performance tests..."

    erl $ERL_FLAGS \
        -eval "c(test_ocel_operations), test_ocel_operations:ocel_performance_test(), halt(0)"
}

# Function to generate test report
generate_report() {
    print_status "Generating test report..."

    local report_file="test_report_$(date +%Y%m%d_%H%M%S).txt"

    {
        echo "YAWL Process Mining Bridge Test Report"
        echo "===================================="
        echo "Generated: $(date)"
        echo ""

        # Run tests and capture output
        erl $ERL_FLAGS \
            -eval "c(test_suite), test_suite:run_all_tests(), halt(0)"

    } > "$report_file"

    print_status "Report saved to $report_file"
}

# Function to cleanup
cleanup() {
    print_status "Cleaning up..."

    # Remove temporary files
    find . -name "*.beam" -delete 2>/dev/null || true
    find test -name "*.tmp" -delete 2>/dev/null || true
    find test -name "*_export.*" -delete 2>/dev/null || true

    print_status "Cleanup complete"
}

# Function to show help
show_help() {
    echo "Usage: $0 [OPTIONS] [TEST_MODULE]"
    echo ""
    echo "Options:"
    echo "  -e, --eunit         Run tests with EUnit (default)"
    echo "  -c, --common-test   Run tests with Common Test"
    echo "  -s, --escript       Run tests with escript runner"
    echo "  -p, --performance   Run performance tests"
    echo "  -r, --report        Generate test report"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Test Modules:"
    echo "  test_nif_loading    Test NIF loading and initialization"
    echo "  test_bridge_api     Test all public API functions"
    echo "  test_error_handling  Test error handling paths"
    echo "  test_ocel_operations Test OCEL-specific operations"
    echo "  test_suite          Run all tests with summary"
    echo ""
    echo "Examples:"
    echo "  $0                           # Run all tests with EUnit"
    echo "  $0 test_bridge_api           # Run only API tests"
    echo "  $0 -s test_ocel_operations   # Run OCEL tests with escript"
    echo "  $0 -p                        # Run performance tests"
    echo "  $0 -r                        # Generate test report"
}

# Main execution
main() {
    # Parse command line arguments
    local test_method="eunit"
    local test_module=""

    while [[ $# -gt 0 ]]; do
        case $1 in
            -e|--eunit)
                test_method="eunit"
                shift
                ;;
            -c|--common-test)
                test_method="common"
                shift
                ;;
            -s|--escript)
                test_method="escript"
                shift
                ;;
            -p|--performance)
                test_method="performance"
                shift
                ;;
            -r|--report)
                test_method="report"
                shift
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                # Assume it's a test module
                if [[ -z "$test_module" ]]; then
                    test_module="$1"
                else
                    print_error "Unknown argument: $1"
                    show_help
                    exit 1
                fi
                shift
                ;;
        esac
    done

    # Setup environment and run tests
    setup_environment
    check_prerequisites

    case $test_method in
        eunit)
            compile_modules
            run_eunit_tests "$test_module"
            ;;
        common)
            compile_modules
            run_common_tests
            ;;
        escript)
            compile_modules
            run_escript_tests "$test_module"
            ;;
        performance)
            compile_modules
            run_performance_tests
            ;;
        report)
            compile_modules
            generate_report
            ;;
        *)
            print_error "Unknown test method: $test_method"
            show_help
            exit 1
            ;;
    esac

    # Cleanup on exit
    trap cleanup EXIT
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi