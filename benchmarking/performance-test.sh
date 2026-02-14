#!/bin/bash

##############################################################################
# YAWL Performance Testing Framework
# This script orchestrates load tests, performance benchmarks, and generates reports
##############################################################################

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="${SCRIPT_DIR}/results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
TEST_REPORT="${RESULTS_DIR}/performance_report_${TIMESTAMP}.md"
JSON_REPORT="${RESULTS_DIR}/performance_metrics_${TIMESTAMP}.json"

# Test configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
ENVIRONMENT="${ENVIRONMENT:-development}"
K6_VUSER="${K6_VUSER:-50}"
K6_DURATION="${K6_DURATION:-10m}"
TEST_TIMEOUT=600

# Default values
RUN_K6=true
RUN_LOAD_TEST=true
RUN_RESOURCE_TEST=true
RUN_DB_QUERY_TEST=true
GENERATE_REPORT=true
VERBOSE=false

##############################################################################
# Functions
##############################################################################

# Print colored output
print_header() {
    echo -e "\n${BLUE}===============================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}===============================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Check dependencies
check_dependencies() {
    print_header "Checking Dependencies"

    local missing_deps=()

    # Check for required tools
    if ! command -v curl &> /dev/null; then
        missing_deps+=("curl")
    fi

    if [ "$RUN_K6" = true ]; then
        if ! command -v k6 &> /dev/null; then
            print_warning "k6 not found. Load testing will be skipped."
            RUN_K6=false
        else
            print_success "k6 found: $(k6 --version)"
        fi
    fi

    if ! command -v jq &> /dev/null; then
        print_warning "jq not installed. JSON parsing may be limited."
    fi

    if [ ${#missing_deps[@]} -gt 0 ]; then
        print_error "Missing dependencies: ${missing_deps[*]}"
        return 1
    fi

    print_success "All dependencies checked"
}

# Initialize test environment
init_environment() {
    print_header "Initializing Test Environment"

    # Create results directory
    mkdir -p "$RESULTS_DIR"
    print_success "Created results directory: $RESULTS_DIR"

    # Verify connectivity
    print_info "Verifying connectivity to $BASE_URL"
    if curl -s -f -m 5 "$BASE_URL/health" > /dev/null 2>&1; then
        print_success "Connected to $BASE_URL"
    else
        print_warning "Could not connect to $BASE_URL (health check failed). Tests may fail."
    fi

    # Create JSON report structure
    cat > "$JSON_REPORT" << 'EOF'
{
  "test_suite": "YAWL Performance Testing",
  "timestamp": "",
  "environment": "",
  "configuration": {},
  "results": {
    "load_test": {},
    "resource_scaling": {},
    "database_queries": {}
  },
  "summary": {
    "total_tests": 0,
    "passed": 0,
    "failed": 0
  }
}
EOF
    print_success "Created JSON report: $JSON_REPORT"
}

# Run k6 load tests
run_k6_tests() {
    print_header "Running K6 Load Tests"

    if [ "$RUN_K6" = false ]; then
        print_warning "K6 not available, skipping load tests"
        return 0
    fi

    local k6_output="${RESULTS_DIR}/k6_results_${TIMESTAMP}.json"

    print_info "Configuration:"
    print_info "  Base URL: $BASE_URL"
    print_info "  Virtual Users: $K6_VUSER"
    print_info "  Duration: $K6_DURATION"

    if k6 run \
        --vus "$K6_VUSER" \
        --duration "$K6_DURATION" \
        --out json="$k6_output" \
        -e BASE_URL="$BASE_URL" \
        "$SCRIPT_DIR/load-testing.js" \
        2>&1 | tee "${RESULTS_DIR}/k6_output_${TIMESTAMP}.log"; then
        print_success "K6 tests completed"
        return 0
    else
        print_error "K6 tests failed"
        return 1
    fi
}

# Run basic load test with curl
run_curl_load_test() {
    print_header "Running cURL Load Test (Basic HTTP Benchmarking)"

    local results_file="${RESULTS_DIR}/curl_load_test_${TIMESTAMP}.txt"
    local concurrent_requests=10
    local total_requests=100
    local request_count=0
    local success_count=0
    local total_time=0
    local min_time=999999
    local max_time=0

    print_info "Configuration:"
    print_info "  Concurrent Requests: $concurrent_requests"
    print_info "  Total Requests: $total_requests"
    print_info "  Target: $BASE_URL/api/v1/resources"

    echo "cURL Load Test Results - $TIMESTAMP" > "$results_file"
    echo "======================================" >> "$results_file"

    for ((i = 1; i <= total_requests; i++)); do
        request_count=$((request_count + 1))

        # Time the request
        local start_time=$(date +%s%N)
        local response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
            "$BASE_URL/api/v1/resources?limit=10" 2>/dev/null)
        local end_time=$(date +%s%N)

        local http_code=$(echo "$response" | tail -n 2 | head -n 1)
        local response_time=$(echo "$response" | tail -n 1)

        if [ "$http_code" == "200" ]; then
            success_count=$((success_count + 1))
        fi

        # Calculate elapsed time in milliseconds
        local elapsed=$(( (end_time - start_time) / 1000000 ))
        [ $elapsed -lt $min_time ] && min_time=$elapsed
        [ $elapsed -gt $max_time ] && max_time=$elapsed
        total_time=$((total_time + elapsed))

        # Print progress
        if (( i % 10 == 0 )); then
            echo "Progress: $i/$total_requests requests completed"
        fi

        # Limit concurrent requests
        if (( i % concurrent_requests == 0 )); then
            wait
        fi
    done

    # Calculate statistics
    local avg_time=$(( total_time / request_count ))
    local success_rate=$(( success_count * 100 / request_count ))

    {
        echo "Test Results:"
        echo "  Total Requests: $request_count"
        echo "  Successful: $success_count"
        echo "  Failed: $((request_count - success_count))"
        echo "  Success Rate: $success_rate%"
        echo ""
        echo "Response Time Statistics (ms):"
        echo "  Min: $min_time"
        echo "  Max: $max_time"
        echo "  Average: $avg_time"
        echo "  Total Time: $((total_time / 1000))s"
    } | tee -a "$results_file"

    print_success "cURL load test completed: $results_file"
}

# Test resource scaling
test_resource_scaling() {
    print_header "Testing Resource Scaling"

    local scaling_results="${RESULTS_DIR}/resource_scaling_${TIMESTAMP}.txt"
    local endpoints=(
        "/health"
        "/api/v1/resources"
        "/api/v1/resources?limit=10"
        "/api/v1/resources?limit=100"
        "/api/v1/resources?limit=1000"
    )

    {
        echo "Resource Scaling Test Results - $TIMESTAMP"
        echo "=========================================="
        echo ""
        echo "Testing response times with varying data sizes:"
        echo ""
    } > "$scaling_results"

    for endpoint in "${endpoints[@]}"; do
        local response_times=()

        for i in {1..5}; do
            local start=$(date +%s%N)
            curl -s "$BASE_URL$endpoint" > /dev/null 2>&1
            local end=$(date +%s%N)
            local elapsed=$(( (end - start) / 1000000 ))
            response_times+=($elapsed)
        done

        # Calculate average
        local sum=0
        for time in "${response_times[@]}"; do
            sum=$((sum + time))
        done
        local avg=$(( sum / ${#response_times[@]} ))

        echo "Endpoint: $endpoint" | tee -a "$scaling_results"
        echo "  Sample Response Times (ms): ${response_times[*]}" | tee -a "$scaling_results"
        echo "  Average Response Time (ms): $avg" | tee -a "$scaling_results"
        echo "" | tee -a "$scaling_results"
    done

    print_success "Resource scaling test completed: $scaling_results"
}

# Test database query performance
test_database_performance() {
    print_header "Testing Database Performance"

    local db_results="${RESULTS_DIR}/database_performance_${TIMESTAMP}.txt"

    {
        echo "Database Performance Test Results - $TIMESTAMP"
        echo "=============================================="
        echo ""
        echo "Note: This test requires database access credentials"
        echo "Tests are defined in: database-performance-queries.sql"
        echo ""
        echo "To run database performance tests:"
        echo "1. Configure database credentials in environment variables"
        echo "2. Execute SQL queries from database-performance-queries.sql"
        echo "3. Analyze query execution plans and timing"
        echo ""
    } > "$db_results"

    print_warning "Database tests require manual execution with proper credentials"
    print_info "See database-performance-queries.sql for test queries"
}

# Generate comprehensive report
generate_report() {
    print_header "Generating Performance Report"

    local test_count=0
    local pass_count=0

    cat > "$TEST_REPORT" << 'EOF'
# YAWL Performance Testing Report
EOF

    {
        echo ""
        echo "## Test Execution Summary"
        echo ""
        echo "- **Timestamp**: $TIMESTAMP"
        echo "- **Environment**: $ENVIRONMENT"
        echo "- **Base URL**: $BASE_URL"
        echo "- **Test Framework**: K6, cURL, Custom Scripts"
        echo ""
        echo "## Test Results"
        echo ""
    } >> "$TEST_REPORT"

    # Include k6 results if available
    if [ -f "${RESULTS_DIR}/k6_output_${TIMESTAMP}.log" ]; then
        {
            echo "### K6 Load Test Results"
            echo ""
            echo '```'
            tail -20 "${RESULTS_DIR}/k6_output_${TIMESTAMP}.log" || echo "No output available"
            echo '```'
            echo ""
        } >> "$TEST_REPORT"
    fi

    # Include curl load test results
    if [ -f "${RESULTS_DIR}/curl_load_test_${TIMESTAMP}.txt" ]; then
        {
            echo "### cURL Load Test Results"
            echo ""
            echo '```'
            cat "${RESULTS_DIR}/curl_load_test_${TIMESTAMP}.txt"
            echo '```'
            echo ""
        } >> "$TEST_REPORT"
    fi

    # Include resource scaling results
    if [ -f "${RESULTS_DIR}/resource_scaling_${TIMESTAMP}.txt" ]; then
        {
            echo "### Resource Scaling Test Results"
            echo ""
            echo '```'
            cat "${RESULTS_DIR}/resource_scaling_${TIMESTAMP}.txt"
            echo '```'
            echo ""
        } >> "$TEST_REPORT"
    fi

    {
        echo "## Recommendations"
        echo ""
        echo "- Monitor response time trends over time"
        echo "- Analyze error patterns and implement mitigation strategies"
        echo "- Identify bottlenecks and optimize critical paths"
        echo "- Implement caching where appropriate"
        echo "- Consider database query optimization"
        echo "- Scale infrastructure based on load test results"
        echo ""
        echo "## Performance Baseline Targets"
        echo ""
        echo "| Metric | Target | Status |"
        echo "|--------|--------|--------|"
        echo "| P95 Response Time | < 500ms | - |"
        echo "| P99 Response Time | < 1000ms | - |"
        echo "| Error Rate | < 1% | - |"
        echo "| Throughput | > 100 req/s | - |"
        echo ""
        echo "---"
        echo "Report generated: $(date)"
    } >> "$TEST_REPORT"

    print_success "Report generated: $TEST_REPORT"
}

# Cleanup function
cleanup() {
    print_info "Cleaning up temporary files..."
    # Add any cleanup logic here
}

# Main execution flow
main() {
    print_header "YAWL Performance Testing Framework"

    echo "Starting performance test suite..."
    echo "Test Directory: $SCRIPT_DIR"
    echo "Results Directory: $RESULTS_DIR"
    echo ""

    # Parse command-line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -u|--url)
                BASE_URL="$2"
                shift 2
                ;;
            -e|--environment)
                ENVIRONMENT="$2"
                shift 2
                ;;
            -v|--vus)
                K6_VUSER="$2"
                shift 2
                ;;
            -d|--duration)
                K6_DURATION="$2"
                shift 2
                ;;
            --no-k6)
                RUN_K6=false
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            *)
                print_error "Unknown option: $1"
                print_usage
                exit 1
                ;;
        esac
    done

    # Execute test pipeline
    if check_dependencies; then
        init_environment
        run_curl_load_test
        if [ "$RUN_K6" = true ]; then
            run_k6_tests
        fi
        test_resource_scaling
        test_database_performance
        generate_report
        print_success "All tests completed successfully!"
    else
        print_error "Dependency check failed"
        exit 1
    fi

    trap cleanup EXIT
}

# Print usage information
print_usage() {
    cat << 'USAGE'
YAWL Performance Testing Framework

Usage: ./performance-test.sh [OPTIONS]

Options:
  -u, --url URL              Base URL for testing (default: http://localhost:8080)
  -e, --environment ENV      Environment name (default: development)
  -v, --vus COUNT           Virtual users for K6 (default: 50)
  -d, --duration DURATION   Duration for K6 tests (default: 10m)
  --no-k6                   Skip K6 tests
  --verbose                 Enable verbose output
  -h, --help               Show this help message

Examples:
  ./performance-test.sh
  ./performance-test.sh -u http://api.example.com -e production -v 100
  ./performance-test.sh --no-k6 -e staging

USAGE
}

# Run main function
main "$@"
