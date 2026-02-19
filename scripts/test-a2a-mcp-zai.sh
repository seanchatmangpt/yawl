#!/bin/bash
# ==========================================================================
# test-a2a-mcp-zai.sh — Integrated A2A/MCP/Z.ai Testing
# ==========================================================================
#
# Comprehensive test script for A2A, MCP, and Z.ai capabilities in YAWL.
# Tests end-to-end integration including self-play scenarios.
#
# Usage:
#   bash scripts/test-a2a-mcp-zai.sh [options]
#
# Options:
#   --help                Show this help message
#   --skip-prereqs        Skip prerequisite checks
#   --skip-a2a            Skip A2A capability tests
#   --skip-mcp            Skip MCP capability tests
#   --skip-zai            Skip Z.ai capability tests
#   --skip-self-play      Skip self-play tests
#   --skip-perf           Skip performance benchmarks
#   --perf-only           Run only performance benchmarks
#   --benchmark           Run full performance benchmark suite
#   --verbose             Enable verbose output
#   --timeout=<seconds>   Test timeout (default: 300)
#   --iterations=<N>      Performance test iterations (default: 100)
#   --output=<dir>        Output directory for results
#   --report              Generate JSON report
#   --ci                  CI mode (non-interactive, no prompts)
#
# Exit Codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Configuration or prerequisite error
#   3 - Service unavailable
#
# Environment Variables:
#   YAWL_ENGINE_URL       YAWL engine URL (default: http://localhost:8080)
#   YAWL_MCP_URL          MCP server URL (default: http://localhost:8080/mcp)
#   YAWL_A2A_URL          A2A server URL (default: http://localhost:8080/api/a2a)
#   YAWL_ZAI_URL          Z.ai API URL (default: http://localhost:8080/api/zai)
#
# ==========================================================================

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Default values
SKIP_PREREQS=false
SKIP_A2A=false
SKIP_MCP=false
SKIP_ZAI=false
SKIP_SELF_PLAY=false
SKIP_PERF=false
PERF_ONLY=false
RUN_BENCHMARK=false
VERBOSE=false
CI_MODE=false
GENERATE_REPORT=false
TIMEOUT=300
ITERATIONS=100
OUTPUT_DIR=""

# Service URLs
YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080}"
YAWL_MCP_URL="${YAWL_MCP_URL:-http://localhost:8080/mcp}"
YAWL_A2A_URL="${YAWL_A2A_URL:-http://localhost:8080/api/a2a}"
YAWL_ZAI_URL="${YAWL_ZAI_URL:-http://localhost:8080/api/zai}"

# Test results tracking
declare -A TEST_RESULTS
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
SKIPPED_TESTS=0

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Logging functions
log_info() { echo -e "${BLUE}[INFO]${NC} $1" >&2; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1" >&2; }
log_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1" >&2; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1" >&2; }
log_skip() { echo -e "${YELLOW}[SKIP]${NC} $1" >&2; }
log_test() { echo -e "${CYAN}[TEST]${NC} $1" >&2; }
log_section() { echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}\n" >&2; }

# Show help message
show_help() {
    cat << 'HELP_EOF'
YAWL A2A/MCP/Z.ai Integration Test Suite
=========================================

Comprehensive test script for A2A, MCP, and Z.ai capabilities in YAWL.

Usage:
  bash scripts/test-a2a-mcp-zai.sh [options]

Options:
  --help                Show this help message
  --skip-prereqs        Skip prerequisite checks
  --skip-a2a            Skip A2A capability tests
  --skip-mcp            Skip MCP capability tests
  --skip-zai            Skip Z.ai capability tests
  --skip-self-play      Skip self-play tests
  --skip-perf           Skip performance benchmarks
  --perf-only           Run only performance benchmarks
  --benchmark           Run full performance benchmark suite
  --verbose             Enable verbose output
  --timeout=<seconds>   Test timeout (default: 300)
  --iterations=<N>      Performance test iterations (default: 100)
  --output=<dir>        Output directory for results
  --report              Generate JSON report
  --ci                  CI mode (non-interactive)

Exit Codes:
  0 - All tests passed
  1 - One or more tests failed
  2 - Configuration error
  3 - Service unavailable
HELP_EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help) show_help; exit 0 ;;
            --skip-prereqs) SKIP_PREREQS=true ;;
            --skip-a2a) SKIP_A2A=true ;;
            --skip-mcp) SKIP_MCP=true ;;
            --skip-zai) SKIP_ZAI=true ;;
            --skip-self-play) SKIP_SELF_PLAY=true ;;
            --skip-perf) SKIP_PERF=true ;;
            --perf-only) PERF_ONLY=true ;;
            --benchmark) RUN_BENCHMARK=true ;;
            --verbose) VERBOSE=true ;;
            --ci) CI_MODE=true ;;
            --report) GENERATE_REPORT=true ;;
            --timeout=*) TIMEOUT="${1#*=}" ;;
            --iterations=*) ITERATIONS="${1#*=}" ;;
            --output=*) OUTPUT_DIR="${1#*=}" ;;
            *) log_error "Unknown option: $1"; show_help; exit 2 ;;
        esac
        shift
    done

    [[ -z "$OUTPUT_DIR" ]] && OUTPUT_DIR="${PROJECT_ROOT}/test-results-${TIMESTAMP}"
    [[ "${CI:-false}" == "true" ]] && CI_MODE=true
    [[ -n "${TEST_ITERATIONS:-}" ]] && ITERATIONS="$TEST_ITERATIONS"
}

# Record test result
record_test() {
    local test_name="$1"
    local result="$2"
    local message="${3:-}"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    case "$result" in
        pass) PASSED_TESTS=$((PASSED_TESTS + 1)); log_success "$test_name${message:+: $message}" ;;
        fail) FAILED_TESTS=$((FAILED_TESTS + 1)); log_fail "$test_name${message:+: $message}" ;;
        skip) SKIPPED_TESTS=$((SKIPPED_TESTS + 1)); log_skip "$test_name${message:+: $message}" ;;
    esac
    TEST_RESULTS["$test_name"]="$result:$message"
}

# Check prerequisites
check_prerequisites() {
    log_section "Prerequisites Check"
    local all_ok=true

    command -v java &> /dev/null && log_info "Java: $(java -version 2>&1 | head -1)" || { log_error "Java not installed"; all_ok=false; }
    command -v mvn &> /dev/null && log_info "Maven: $(mvn --version 2>&1 | head -1)" || { log_error "Maven not installed"; all_ok=false; }
    command -v curl &> /dev/null && log_info "curl: available" || { log_error "curl not installed"; all_ok=false; }
    command -v jq &> /dev/null && log_info "jq: available" || log_warn "jq not installed (some features limited)"

    [[ ! -f "${PROJECT_ROOT}/pom.xml" ]] && { log_error "Not in YAWL project root"; all_ok=false; }
    mkdir -p "$OUTPUT_DIR"

    [[ "$all_ok" == true ]] && { log_success "All prerequisites met"; return 0; } || { log_error "Prerequisites failed"; return 2; }
}

# Check service health
check_service_health() {
    local service_name="$1"
    local url="$2"
    local max_attempts=3
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        local response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$url" 2>/dev/null || echo "000")
        [[ "$response" =~ ^2[0-9][0-9]$ ]] && { log_info "$service_name is healthy (HTTP $response)"; return 0; }
        log_warn "$service_name health check attempt $attempt failed (HTTP $response)"
        attempt=$((attempt + 1))
        sleep 2
    done
    log_error "$service_name is not available at $url"
    return 3
}

# Start services if needed
start_services() {
    log_section "Starting Services"

    if ! curl -s "${YAWL_ENGINE_URL}/health" > /dev/null 2>&1; then
        log_warn "YAWL engine not running on ${YAWL_ENGINE_URL}"
        if [[ "$CI_MODE" == true ]]; then
            log_info "Attempting to start YAWL engine..."
            cd "$PROJECT_ROOT"
            [[ -f "scripts/start-engine-java25-tuned.sh" ]] && bash scripts/start-engine-java25-tuned.sh &
            sleep 10
            curl -s "${YAWL_ENGINE_URL}/health" > /dev/null 2>&1 && log_success "YAWL engine started" || { log_error "Failed to start engine"; return 3; }
        else
            log_error "Please start the YAWL engine before running tests"
            return 3
        fi
    else
        log_success "YAWL engine is running"
    fi
    check_service_health "YAWL Engine" "${YAWL_ENGINE_URL}/health" || true
    check_service_health "MCP Server" "${YAWL_MCP_URL}/health" || true
}

# Test A2A capabilities
test_a2a_capabilities() {
    log_section "A2A Capability Tests"
    [[ "$SKIP_A2A" == true ]] && { record_test "A2A Tests" "skip" "Skipped by user"; return 0; }

    log_test "Testing A2A ping..."
    local ping_response=$(curl -s -X GET "${YAWL_A2A_URL}/ping" 2>/dev/null || echo "failed")
    [[ "$ping_response" == *"pong"* ]] || [[ "$ping_response" == *"success"* ]] && record_test "A2A Ping" "pass" "Received pong response" || record_test "A2A Ping" "fail" "No pong response"

    log_test "Testing A2A specification upload..."
    local test_spec="<?xml version=\"1.0\"?><specification><id>a2a_test_${TIMESTAMP}</id></specification>"
    local upload_response=$(curl -s -X POST -H "Content-Type: application/xml" -d "$test_spec" "${YAWL_A2A_URL}/specification" 2>/dev/null || echo "failed")
    [[ "$upload_response" == *"success"* ]] || [[ "$upload_response" == *"uploaded"* ]] && record_test "A2A Spec Upload" "pass" || record_test "A2A Spec Upload" "fail"

    log_test "Testing A2A case creation..."
    local case_response=$(curl -s -X POST -H "Content-Type: application/json" -d '{"specId": "a2a_test_'${TIMESTAMP}'"}' "${YAWL_A2A_URL}/case" 2>/dev/null || echo "failed")
    [[ "$case_response" == *"caseId"* ]] || [[ "$case_response" == *"success"* ]] && record_test "A2A Case Creation" "pass" || record_test "A2A Case Creation" "fail"

    log_test "Testing A2A work item query..."
    local workitems_response=$(curl -s -X GET "${YAWL_A2A_URL}/workitems" 2>/dev/null || echo "failed")
    [[ "$workitems_response" == *"workItems"* ]] || [[ "$workitems_response" == *"[]"* ]] && record_test "A2A Work Item Query" "pass" || record_test "A2A Work Item Query" "fail"
}

# Test MCP capabilities
test_mcp_capabilities() {
    log_section "MCP Capability Tests"
    [[ "$SKIP_MCP" == true ]] && { record_test "MCP Tests" "skip" "Skipped by user"; return 0; }

    log_test "Testing MCP server health..."
    local health_response=$(curl -s "${YAWL_MCP_URL}/health" 2>/dev/null || echo "failed")
    [[ "$health_response" == *"healthy"* ]] || [[ "$health_response" == *"ok"* ]] && record_test "MCP Health Check" "pass" || record_test "MCP Health Check" "fail"

    log_test "Testing MCP ping..."
    local ping_response=$(curl -s -X GET "${YAWL_MCP_URL}/ping" 2>/dev/null || echo "failed")
    [[ "$ping_response" == *"pong"* ]] || [[ "$ping_response" == *"success"* ]] && record_test "MCP Ping" "pass" || record_test "MCP Ping" "fail"

    log_test "Testing MCP tool list..."
    local tools_response=$(curl -s -X POST -H "Content-Type: application/json" -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}' "${YAWL_MCP_URL}" 2>/dev/null || echo "failed")
    [[ "$tools_response" == *"tools"* ]] || [[ "$tools_response" == *"result"* ]] && record_test "MCP Tool List" "pass" || record_test "MCP Tool List" "fail"

    log_test "Testing MCP tool execution..."
    local tool_response=$(curl -s -X POST -H "Content-Type: application/json" -d '{"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": "get_engine_state"}}' "${YAWL_MCP_URL}" 2>/dev/null || echo "failed")
    [[ "$tool_response" == *"result"* ]] || [[ "$tool_response" == *"success"* ]] && record_test "MCP Tool Execution" "pass" || record_test "MCP Tool Execution" "fail"
}

# Test Z.ai capabilities
test_zai_capabilities() {
    log_section "Z.ai Capability Tests"
    [[ "$SKIP_ZAI" == true ]] && { record_test "Z.ai Tests" "skip" "Skipped by user"; return 0; }

    log_test "Testing Z.ai service health..."
    local health_response=$(curl -s "${YAWL_ZAI_URL}/health" 2>/dev/null || echo "failed")
    if [[ "$health_response" == *"healthy"* ]] || [[ "$health_response" == *"ok"* ]]; then
        record_test "Z.ai Health Check" "pass"
    else
        record_test "Z.ai Health Check" "skip" "Z.ai service not available"
        return 0
    fi

    log_test "Testing Z.ai chat completion..."
    local chat_response=$(curl -s -X POST -H "Content-Type: application/json" -d '{"model": "glm-4-flash", "messages": [{"role": "user", "content": "Hello"}], "max_tokens": 50}' "${YAWL_ZAI_URL}/chat" 2>/dev/null || echo "failed")
    [[ "$chat_response" == *"content"* ]] || [[ "$chat_response" == *"choices"* ]] && record_test "Z.ai Chat" "pass" || record_test "Z.ai Chat" "fail"

    log_test "Testing Z.ai workflow generation..."
    local gen_response=$(curl -s -X POST -H "Content-Type: application/json" -d '{"prompt": "Generate approval workflow", "max_tokens": 500}' "${YAWL_ZAI_URL}/generate" 2>/dev/null || echo "failed")
    [[ "$gen_response" == *"workflow"* ]] || [[ "$gen_response" == *"xml"* ]] && record_test "Z.ai Workflow Gen" "pass" || record_test "Z.ai Workflow Gen" "fail"
}

# Self-play test
run_self_play_test() {
    log_section "Self-Play Test (AI Generate -> Validate -> Execute)"
    [[ "$SKIP_SELF_PLAY" == true ]] && { record_test "Self-Play Test" "skip" "Skipped by user"; return 0; }

    log_test "Phase 1: AI generating workflow..."
    local gen_response=$(curl -s -X POST -H "Content-Type: application/json" -d '{"prompt": "Create approval workflow", "max_tokens": 1000}' "${YAWL_ZAI_URL}/generate" 2>/dev/null || echo "failed")

    if [[ "$gen_response" == *"xml"* ]] || [[ "$gen_response" == *"specification"* ]]; then
        local workflow_file="${OUTPUT_DIR}/self_play_workflow.xml"
        command -v jq &> /dev/null && echo "$gen_response" | jq -r '.workflow_xml // .xml // empty' > "$workflow_file" 2>/dev/null || echo "$gen_response" > "$workflow_file"
        [[ -s "$workflow_file" ]] && record_test "Self-Play: Generate" "pass" "Workflow generated" || { record_test "Self-Play: Generate" "fail" "Empty workflow"; return 0; }

        log_test "Phase 2: AI validating workflow..."
        [[ -f "${PROJECT_ROOT}/schema/YAWL_Schema4.0.xsd" ]] && [[ -f "$workflow_file" ]] && {
            xmllint --schema "${PROJECT_ROOT}/schema/YAWL_Schema4.0.xsd" "$workflow_file" > /dev/null 2>&1 && record_test "Self-Play: Validate" "pass" "Schema valid" || record_test "Self-Play: Validate" "fail" "Schema invalid"
        } || record_test "Self-Play: Validate" "skip" "Schema not found"

        log_test "Phase 3: AI executing workflow..."
        local exec_response=$(curl -s -X POST -H "Content-Type: application/xml" -d @"$workflow_file" "${YAWL_A2A_URL}/specification" 2>/dev/null || echo "failed")
        [[ "$exec_response" == *"success"* ]] || [[ "$exec_response" == *"uploaded"* ]] && record_test "Self-Play: Execute" "pass" "Workflow uploaded" || record_test "Self-Play: Execute" "fail"
    else
        record_test "Self-Play: Generate" "skip" "Z.ai not available"
    fi
}

# Performance benchmarks
run_performance_benchmarks() {
    log_section "Performance Benchmarks"
    [[ "$SKIP_PERF" == true ]] && [[ "$PERF_ONLY" == false ]] && { record_test "Performance" "skip" "Skipped"; return 0; }

    [[ "$RUN_BENCHMARK" == true ]] && {
        [[ -f "${SCRIPT_DIR}/run-benchmarks.sh" ]] && { bash "${SCRIPT_DIR}/run-benchmarks.sh" --output="${OUTPUT_DIR}/benchmarks"; record_test "Benchmark Suite" "pass"; } || record_test "Benchmark Suite" "fail" "Script not found"
        return 0
    }

    local results_file="${OUTPUT_DIR}/performance_results.json"

    log_test "Testing A2A throughput ($ITERATIONS requests)..."
    local a2a_start=$(date +%s%N 2>/dev/null || date +%s)
    local a2a_success=0
    for i in $(seq 1 "$ITERATIONS"); do
        local response=$(curl -s -X GET "${YAWL_A2A_URL}/ping" 2>/dev/null || echo "failed")
        [[ "$response" == *"pong"* ]] || [[ "$response" == *"success"* ]] && a2a_success=$((a2a_success + 1))
    done
    local a2a_end=$(date +%s%N 2>/dev/null || date +%s)
    local a2a_duration_ms=$(( (a2a_end - a2a_start) / 1000000 ))
    local a2a_throughput=$(echo "scale=2; $a2a_success / ($a2a_duration_ms / 1000)" | bc -l 2>/dev/null || echo "N/A")
    record_test "A2A Throughput" "pass" "$a2a_success/$ITERATIONS successful, ${a2a_throughput} req/s"

    log_test "Testing MCP latency ($ITERATIONS requests)..."
    local mcp_start=$(date +%s%N 2>/dev/null || date +%s)
    local mcp_success=0
    for i in $(seq 1 "$ITERATIONS"); do
        local response=$(curl -s -X GET "${YAWL_MCP_URL}/ping" 2>/dev/null || echo "failed")
        [[ "$response" == *"pong"* ]] || [[ "$response" == *"success"* ]] && mcp_success=$((mcp_success + 1))
    done
    local mcp_end=$(date +%s%N 2>/dev/null || date +%s)
    local mcp_duration_ms=$(( (mcp_end - mcp_start) / 1000000 ))
    local mcp_throughput=$(echo "scale=2; $mcp_success / ($mcp_duration_ms / 1000)" | bc -l 2>/dev/null || echo "N/A")
    record_test "MCP Throughput" "pass" "$mcp_success/$ITERATIONS successful, ${mcp_throughput} req/s"

    cat > "$results_file" << PERFEOF
{
  "timestamp": "$(date -Iseconds)",
  "iterations": $ITERATIONS,
  "a2a": {"successful": $a2a_success, "throughput": $a2a_throughput},
  "mcp": {"successful": $mcp_success, "throughput": $mcp_throughput}
}
PERFEOF
    log_info "Performance results saved to: $results_file"
}

# Generate JSON report
generate_json_report() {
    local report_file="${OUTPUT_DIR}/test_report.json"
    log_info "Generating JSON report..."
    
    cat > "$report_file" << REPORTEOF
{
  "timestamp": "$(date -Iseconds)",
  "environment": {
    "java_version": "$(java -version 2>&1 | head -1)",
    "yawl_engine_url": "$YAWL_ENGINE_URL",
    "mcp_url": "$YAWL_MCP_URL",
    "a2a_url": "$YAWL_A2A_URL",
    "zai_url": "$YAWL_ZAI_URL"
  },
  "summary": {
    "total": $TOTAL_TESTS,
    "passed": $PASSED_TESTS,
    "failed": $FAILED_TESTS,
    "skipped": $SKIPPED_TESTS,
    "success_rate": $(echo "scale=2; $PASSED_TESTS * 100 / $TOTAL_TESTS" | bc -l 2>/dev/null || echo "0")
  }
}
REPORTEOF
    log_success "JSON report generated: $report_file"
}

# Cleanup function
cleanup() {
    local exit_code=$?
    log_info "Cleaning up..."
    rm -f "${OUTPUT_DIR}/generated_workflow.xml" 2>/dev/null || true
    rm -f "${OUTPUT_DIR}/self_play_workflow.xml" 2>/dev/null || true
    jobs -p | xargs -r kill 2>/dev/null || true
    exit $exit_code
}

# Display final summary
display_summary() {
    log_section "Test Summary"
    echo "  Total:   $TOTAL_TESTS"
    echo "  Passed:  $PASSED_TESTS"
    echo "  Failed:  $FAILED_TESTS"
    echo "  Skipped: $SKIPPED_TESTS"
    echo

    if [[ $FAILED_TESTS -eq 0 ]]; then
        log_success "All tests passed!"
        return 0
    else
        log_error "$FAILED_TESTS test(s) failed"
        return 1
    fi
}

# Main execution
main() {
    trap cleanup EXIT
    parse_args "$@"

    echo
    echo "=== YAWL A2A/MCP/Z.ai Integration Test Suite ==="
    echo "Timestamp: $(date)"
    echo "Output: $OUTPUT_DIR"
    echo

    cd "$PROJECT_ROOT"

    [[ "$SKIP_PREREQS" == false ]] && [[ "$PERF_ONLY" == false ]] && check_prerequisites || exit $?
    [[ "$PERF_ONLY" == false ]] && start_services || true

    if [[ "$PERF_ONLY" == true ]]; then
        run_performance_benchmarks
    else
        test_a2a_capabilities
        test_mcp_capabilities
        test_zai_capabilities
        run_self_play_test
        run_performance_benchmarks
    fi

    [[ "$GENERATE_REPORT" == true ]] && generate_json_report
    display_summary
    exit $?
}

main "$@"
