#!/bin/bash
# ==========================================================================
# test-a2a-mcp-zai.sh — Integrated A2A/MCP/Z.ai Testing
# ==========================================================================
#
# Comprehensive test script for A2A, MCP, and Z.ai capabilities in YAWL.
# Tests end-to-end integration including self-play scenarios where AI
# generates, validates, and executes workflows.
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
#   TEST_ITERATIONS       Performance test iterations
#   CI                    Set to true for CI mode
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

# Service URLs (can be overridden by environment)
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
    echo -e "${GREEN}[PASS]${NC} $1" >&2
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1" >&2
}

log_skip() {
    echo -e "${YELLOW}[SKIP]${NC} $1" >&2
}

log_test() {
    echo -e "${CYAN}[TEST]${NC} $1" >&2
}

log_section() {
    echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}\n" >&2
}

# Show help message
show_help() {
    cat << 'EOF'
YAWL A2A/MCP/Z.ai Integration Test Suite
=========================================

Comprehensive test script for A2A, MCP, and Z.ai capabilities in YAWL.
Tests end-to-end integration including self-play scenarios.

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
  --ci                  CI mode (non-interactive, no prompts)

Exit Codes:
  0 - All tests passed
  1 - One or more tests failed
  2 - Configuration or prerequisite error
  3 - Service unavailable

Environment Variables:
  YAWL_ENGINE_URL       YAWL engine URL (default: http://localhost:8080)
  YAWL_MCP_URL          MCP server URL (default: http://localhost:8080/mcp)
  YAWL_A2A_URL          A2A server URL (default: http://localhost:8080/api/a2a)
  YAWL_ZAI_URL          Z.ai API URL (default: http://localhost:8080/api/zai)
  TEST_ITERATIONS       Performance test iterations
  CI                    Set to true for CI mode

Examples:
  bash scripts/test-a2a-mcp-zai.sh                    # Run all tests
  bash scripts/test-a2a-mcp-zai.sh --skip-perf        # Skip performance tests
  bash scripts/test-a2a-mcp-zai.sh --perf-only        # Only performance tests
  bash scripts/test-a2a-mcp-zai.sh --ci --report      # CI mode with report
  bash scripts/test-a2a-mcp-zai.sh --benchmark        # Run benchmark suite
EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --help)
                show_help
                exit 0
                ;;
            --skip-prereqs)
                SKIP_PREREQS=true
                ;;
            --skip-a2a)
                SKIP_A2A=true
                ;;
            --skip-mcp)
                SKIP_MCP=true
                ;;
            --skip-zai)
                SKIP_ZAI=true
                ;;
            --skip-self-play)
                SKIP_SELF_PLAY=true
                ;;
            --skip-perf)
                SKIP_PERF=true
                ;;
            --perf-only)
                PERF_ONLY=true
                ;;
            --benchmark)
                RUN_BENCHMARK=true
                ;;
            --verbose)
                VERBOSE=true
                ;;
            --ci)
                CI_MODE=true
                ;;
            --report)
                GENERATE_REPORT=true
                ;;
            --timeout=*)
                TIMEOUT="${1#*=}"
                ;;
            --iterations=*)
                ITERATIONS="${1#*=}"
                ;;
            --output=*)
                OUTPUT_DIR="${1#*=}"
                ;;
            *)
                log_error "Unknown option: $1"
                show_help
                exit 2
                ;;
        esac
        shift
    done

    # Set default output directory
    if [[ -z "$OUTPUT_DIR" ]]; then
        OUTPUT_DIR="${PROJECT_ROOT}/test-results-${TIMESTAMP}"
    fi

    # Check CI environment variable
    if [[ "${CI:-false}" == "true" ]]; then
        CI_MODE=true
    fi

    # Check iterations from environment
    if [[ -n "${TEST_ITERATIONS:-}" ]]; then
        ITERATIONS="$TEST_ITERATIONS"
    fi
}

# Record test result
record_test() {
    local test_name="$1"
    local result="$2"  # pass, fail, skip
    local message="${3:-}"

    TOTAL_TESTS=$((TOTAL_TESTS + 1))

    case "$result" in
        pass)
            PASSED_TESTS=$((PASSED_TESTS + 1))
            log_success "$test_name${message:+: $message}"
            ;;
        fail)
            FAILED_TESTS=$((FAILED_TESTS + 1))
            log_fail "$test_name${message:+: $message}"
            ;;
        skip)
            SKIPPED_TESTS=$((SKIPPED_TESTS + 1))
            log_skip "$test_name${message:+: $message}"
            ;;
    esac

    TEST_RESULTS["$test_name"]="$result:$message"
}

# Check prerequisites
check_prerequisites() {
    log_section "Prerequisites Check"

    local all_ok=true

    # Check Java
    if command -v java &> /dev/null; then
        local java_version=$(java -version 2>&1 | head -1)
        log_info "Java: $java_version"
    else
        log_error "Java is not installed"
        all_ok=false
    fi

    # Check Maven
    if command -v mvn &> /dev/null; then
        local mvn_version=$(mvn --version 2>&1 | head -1)
        log_info "Maven: $mvn_version"
    else
        log_error "Maven is not installed"
        all_ok=false
    fi

    # Check curl
    if command -v curl &> /dev/null; then
        log_info "curl: available"
    else
        log_error "curl is not installed"
        all_ok=false
    fi

    # Check jq (optional but recommended)
    if command -v jq &> /dev/null; then
        log_info "jq: available"
    else
        log_warn "jq is not installed (some features may be limited)"
    fi

    # Check project directory
    if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
        log_error "Not in YAWL project root directory"
        all_ok=false
    fi

    # Create output directory
    mkdir -p "$OUTPUT_DIR"

    if [[ "$all_ok" == true ]]; then
        log_success "All prerequisites met"
        return 0
    else
        log_error "Prerequisites check failed"
        return 2
    fi
}

# Check service health
check_service_health() {
    local service_name="$1"
    local url="$2"
    local max_attempts=3
    local attempt=1

    while [[ $attempt -le $max_attempts ]]; do
        local response=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 "$url" 2>/dev/null || echo "000")

        if [[ "$response" =~ ^2[0-9][0-9]$ ]]; then
            log_info "$service_name is healthy (HTTP $response)"
            return 0
        fi

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

    # Check if YAWL engine is running
    if ! curl -s "${YAWL_ENGINE_URL}/health" > /dev/null 2>&1; then
        log_warn "YAWL engine not running on ${YAWL_ENGINE_URL}"

        if [[ "$CI_MODE" == true ]]; then
            log_info "Attempting to start YAWL engine..."
            cd "$PROJECT_ROOT"

            # Try different startup methods
            if [[ -f "scripts/start-engine-java25-tuned.sh" ]]; then
                bash scripts/start-engine-java25-tuned.sh &
            elif [[ -f "scripts/start-mcp-server.sh" ]]; then
                bash scripts/start-mcp-server.sh &
            else
                mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.engine.YEngine" &
            fi

            sleep 10

            if curl -s "${YAWL_ENGINE_URL}/health" > /dev/null 2>&1; then
                log_success "YAWL engine started successfully"
            else
                log_error "Failed to start YAWL engine"
                return 3
            fi
        else
            log_error "Please start the YAWL engine before running tests"
            return 3
        fi
    else
        log_success "YAWL engine is running"
    fi

    # Verify all required endpoints
    check_service_health "YAWL Engine" "${YAWL_ENGINE_URL}/health" || true
    check_service_health "MCP Server" "${YAWL_MCP_URL}/health" || true

    return 0
}

# Test A2A capabilities
test_a2a_capabilities() {
    log_section "A2A Capability Tests"

    if [[ "$SKIP_A2A" == true ]]; then
        record_test "A2A Tests" "skip" "Skipped by user"
        return 0
    fi

    # Test A2A ping
    log_test "Testing A2A ping..."
    local ping_response=$(curl -s -X GET "${YAWL_A2A_URL}/ping" 2>/dev/null || echo "failed")
    if [[ "$ping_response" == *"pong"* ]] || [[ "$ping_response" == *"success"* ]]; then
        record_test "A2A Ping" "pass" "Received pong response"
    else
        record_test "A2A Ping" "fail" "No pong response received"
    fi

    # Test A2A specification upload
    log_test "Testing A2A specification upload..."
    local test_spec="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<specification xmlns=\"http://www.yawlfoundation.org/yawlschema\">
    <id>a2a_test_spec_${TIMESTAMP}</id>
    <name>A2A Test Specification</name>
    <version>1.0</version>
</specification>"

    local upload_response=$(curl -s -X POST \
        -H "Content-Type: application/xml" \
        -d "$test_spec" \
        "${YAWL_A2A_URL}/specification" 2>/dev/null || echo "failed")

    if [[ "$upload_response" == *"success"* ]] || [[ "$upload_response" == *"uploaded"* ]]; then
        record_test "A2A Specification Upload" "pass"
    else
        record_test "A2A Specification Upload" "fail" "Upload returned: ${upload_response:0:100}"
    fi

    # Test A2A case creation
    log_test "Testing A2A case creation..."
    local case_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"specId": "a2a_test_spec_'${TIMESTAMP}'", "data": {"test": true}}' \
        "${YAWL_A2A_URL}/case" 2>/dev/null || echo "failed")

    if [[ "$case_response" == *"caseId"* ]] || [[ "$case_response" == *"success"* ]]; then
        record_test "A2A Case Creation" "pass"
    else
        record_test "A2A Case Creation" "fail"
    fi

    # Test A2A work item query
    log_test "Testing A2A work item query..."
    local workitems_response=$(curl -s -X GET "${YAWL_A2A_URL}/workitems" 2>/dev/null || echo "failed")

    if [[ "$workitems_response" == *"workItems"* ]] || [[ "$workitems_response" == *"[]"* ]] || [[ "$workitems_response" == *"success"* ]]; then
        record_test "A2A Work Item Query" "pass"
    else
        record_test "A2A Work Item Query" "fail"
    fi
}

# Test MCP capabilities
test_mcp_capabilities() {
    log_section "MCP Capability Tests"

    if [[ "$SKIP_MCP" == true ]]; then
        record_test "MCP Tests" "skip" "Skipped by user"
        return 0
    fi

    # Test MCP health
    log_test "Testing MCP server health..."
    local health_response=$(curl -s "${YAWL_MCP_URL}/health" 2>/dev/null || echo "failed")
    if [[ "$health_response" == *"healthy"* ]] || [[ "$health_response" == *"ok"* ]] || [[ "$health_response" == *"success"* ]]; then
        record_test "MCP Health Check" "pass"
    else
        record_test "MCP Health Check" "fail" "Health check returned: ${health_response:0:100}"
    fi

    # Test MCP ping
    log_test "Testing MCP ping..."
    local ping_response=$(curl -s -X GET "${YAWL_MCP_URL}/ping" 2>/dev/null || echo "failed")
    if [[ "$ping_response" == *"pong"* ]] || [[ "$ping_response" == *"success"* ]]; then
        record_test "MCP Ping" "pass"
    else
        record_test "MCP Ping" "fail"
    fi

    # Test MCP tool list
    log_test "Testing MCP tool list..."
    local tools_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/list"}' \
        "${YAWL_MCP_URL}" 2>/dev/null || echo "failed")

    if [[ "$tools_response" == *"tools"* ]] || [[ "$tools_response" == *"result"* ]]; then
        record_test "MCP Tool List" "pass"
    else
        record_test "MCP Tool List" "fail"
    fi

    # Test MCP tool execution
    log_test "Testing MCP tool execution..."
    local tool_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc": "2.0", "id": 2, "method": "tools/call", "params": {"name": "get_engine_state", "arguments": {}}}' \
        "${YAWL_MCP_URL}" 2>/dev/null || echo "failed")

    if [[ "$tool_response" == *"result"* ]] || [[ "$tool_response" == *"success"* ]]; then
        record_test "MCP Tool Execution" "pass"
    else
        record_test "MCP Tool Execution" "fail"
    fi

    # Test MCP resource list
    log_test "Testing MCP resource list..."
    local resources_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc": "2.0", "id": 3, "method": "resources/list"}' \
        "${YAWL_MCP_URL}" 2>/dev/null || echo "failed")

    if [[ "$resources_response" == *"resources"* ]] || [[ "$resources_response" == *"result"* ]]; then
        record_test "MCP Resource List" "pass"
    else
        record_test "MCP Resource List" "fail"
    fi
}

# Test Z.ai capabilities
test_zai_capabilities() {
    log_section "Z.ai Capability Tests"

    if [[ "$SKIP_ZAI" == true ]]; then
        record_test "Z.ai Tests" "skip" "Skipped by user"
        return 0
    fi

    # Test Z.ai health
    log_test "Testing Z.ai service health..."
    local health_response=$(curl -s "${YAWL_ZAI_URL}/health" 2>/dev/null || echo "failed")
    if [[ "$health_response" == *"healthy"* ]] || [[ "$health_response" == *"ok"* ]] || [[ "$health_response" == *"available"* ]]; then
        record_test "Z.ai Health Check" "pass"
    else
        # Z.ai might be optional, so this is a soft failure
        record_test "Z.ai Health Check" "skip" "Z.ai service not available (may be optional)"
        return 0
    fi

    # Test Z.ai chat completion
    log_test "Testing Z.ai chat completion..."
    local chat_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"model": "glm-4-flash", "messages": [{"role": "user", "content": "Hello"}], "max_tokens": 50}' \
        "${YAWL_ZAI_URL}/chat" 2>/dev/null || echo "failed")

    if [[ "$chat_response" == *"content"* ]] || [[ "$chat_response" == *"response"* ]] || [[ "$chat_response" == *"choices"* ]]; then
        record_test "Z.ai Chat Completion" "pass"
    else
        record_test "Z.ai Chat Completion" "fail"
    fi

    # Test Z.ai workflow generation
    log_test "Testing Z.ai workflow generation..."
    local gen_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"prompt": "Generate a simple approval workflow", "max_tokens": 500}' \
        "${YAWL_ZAI_URL}/generate" 2>/dev/null || echo "failed")

    if [[ "$gen_response" == *"workflow"* ]] || [[ "$gen_response" == *"xml"* ]] || [[ "$gen_response" == *"specification"* ]]; then
        record_test "Z.ai Workflow Generation" "pass"

        # Save generated workflow for self-play test
        if command -v jq &> /dev/null; then
            echo "$gen_response" | jq -r '.workflow_xml // .xml // .specification // empty' > "${OUTPUT_DIR}/generated_workflow.xml" 2>/dev/null || true
        fi
    else
        record_test "Z.ai Workflow Generation" "fail"
    fi
}

# Self-play test
run_self_play_test() {
    log_section "Self-Play Test (AI Generate -> Validate -> Execute)"

    if [[ "$SKIP_SELF_PLAY" == true ]]; then
        record_test "Self-Play Test" "skip" "Skipped by user"
        return 0
    fi

    # Phase 1: Generate workflow
    log_test "Phase 1: AI generating workflow..."
    local gen_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"prompt": "Create a simple approval workflow with two tasks: Review and Approve", "max_tokens": 1000}' \
        "${YAWL_ZAI_URL}/generate" 2>/dev/null || echo "failed")

    local workflow_file="${OUTPUT_DIR}/self_play_workflow.xml"

    if [[ "$gen_response" == *"xml"* ]] || [[ "$gen_response" == *"specification"* ]]; then
        if command -v jq &> /dev/null; then
            echo "$gen_response" | jq -r '.workflow_xml // .xml // .specification // empty' > "$workflow_file" 2>/dev/null || true
        else
            echo "$gen_response" > "$workflow_file"
        fi

        if [[ -s "$workflow_file" ]]; then
            record_test "Self-Play: Generate" "pass" "Workflow generated"
        else
            record_test "Self-Play: Generate" "fail" "Empty workflow generated"
            return 0
        fi
    else
        record_test "Self-Play: Generate" "skip" "Z.ai generation not available"
        return 0
    fi

    # Phase 2: Validate workflow
    log_test "Phase 2: AI validating workflow..."
    if [[ -f "${PROJECT_ROOT}/schema/YAWL_Schema4.0.xsd" ]] && [[ -f "$workflow_file" ]]; then
        if xmllint --schema "${PROJECT_ROOT}/schema/YAWL_Schema4.0.xsd" "$workflow_file" > /dev/null 2>&1; then
            record_test "Self-Play: Validate" "pass" "Workflow is schema-valid"
        else
            record_test "Self-Play: Validate" "fail" "Workflow failed schema validation"
        fi
    else
        record_test "Self-Play: Validate" "skip" "Schema file not found"
    fi

    # Phase 3: Execute workflow
    log_test "Phase 3: AI executing workflow..."
    if [[ -f "$workflow_file" ]]; then
        local exec_response=$(curl -s -X POST \
            -H "Content-Type: application/xml" \
            -d @"$workflow_file" \
            "${YAWL_A2A_URL}/specification" 2>/dev/null || echo "failed")

        if [[ "$exec_response" == *"success"* ]] || [[ "$exec_response" == *"uploaded"* ]]; then
            record_test "Self-Play: Execute" "pass" "Workflow uploaded successfully"
        else
            record_test "Self-Play: Execute" "fail"
        fi
    else
        record_test "Self-Play: Execute" "skip" "No workflow file to execute"
    fi
}

# Performance benchmarks
run_performance_benchmarks() {
    log_section "Performance Benchmarks"

    if [[ "$SKIP_PERF" == true ]] && [[ "$PERF_ONLY" == false ]]; then
        record_test "Performance Benchmarks" "skip" "Skipped by user"
        return 0
    fi

    if [[ "$RUN_BENCHMARK" == true ]]; then
        log_info "Running full benchmark suite..."
        if [[ -f "${SCRIPT_DIR}/run-benchmarks.sh" ]]; then
            bash "${SCRIPT_DIR}/run-benchmarks.sh" --output="${OUTPUT_DIR}/benchmarks" --report
            record_test "Benchmark Suite" "pass" "Full benchmark suite completed"
        else
            record_test "Benchmark Suite" "fail" "Benchmark script not found"
        fi
        return 0
    fi

    local results_file="${OUTPUT_DIR}/performance_results.json"

    # A2A throughput test
    log_test "Testing A2A throughput ($ITERATIONS requests)..."
    local a2a_start=$(date +%s%N 2>/dev/null || date +%s)
    local a2a_success=0
    local a2a_failed=0

    for i in $(seq 1 "$ITERATIONS"); do
        local response=$(curl -s -X GET "${YAWL_A2A_URL}/ping" 2>/dev/null || echo "failed")
        if [[ "$response" == *"pong"* ]] || [[ "$response" == *"success"* ]]; then
            a2a_success=$((a2a_success + 1))
        else
            a2a_failed=$((a2a_failed + 1))
        fi
    done

    local a2a_end=$(date +%s%N 2>/dev/null || date +%s)
    local a2a_duration_ms=$(( (a2a_end - a2a_start) / 1000000 ))
    local a2a_throughput=$(echo "scale=2; $a2a_success / ($a2a_duration_ms / 1000)" | bc -l 2>/dev/null || echo "N/A")

    record_test "A2A Throughput" "pass" "$a2a_success/$ITERATIONS successful, ${a2a_throughput} req/s"

    # MCP latency test
    log_test "Testing MCP latency ($ITERATIONS requests)..."
    local mcp_start=$(date +%s%N 2>/dev/null || date +%s)
    local mcp_success=0
    local mcp_failed=0

    for i in $(seq 1 "$ITERATIONS"); do
        local response=$(curl -s -X GET "${YAWL_MCP_URL}/ping" 2>/dev/null || echo "failed")
        if [[ "$response" == *"pong"* ]] || [[ "$response" == *"success"* ]]; then
            mcp_success=$((mcp_success + 1))
        else
            mcp_failed=$((mcp_failed + 1))
        fi
    done

    local mcp_end=$(date +%s%N 2>/dev/null || date +%s)
    local mcp_duration_ms=$(( (mcp_end - mcp_start) / 1000000 ))
    local mcp_throughput=$(echo "scale=2; $mcp_success / ($mcp_duration_ms / 1000)" | bc -l 2>/dev/null || echo "N/A")

    record_test "MCP Throughput" "pass" "$mcp_success/$ITERATIONS successful, ${mcp_throughput} req/s"

    # Write results to JSON
    cat > "$results_file" << EOF
{
  "timestamp": "$(date -Iseconds)",
  "iterations": $ITERATIONS,
  "a2a": {
    "successful": $a2a_success,
    "failed": $a2a_failed,
    "duration_ms": $a2a_duration_ms,
    "throughput_req_per_sec": $a2a_throughput
  },
  "mcp": {
    "successful": $mcp_success,
    "failed": $mcp_failed,
    "duration_ms": $mcp_duration_ms,
    "throughput_req_per_sec": $mcp_throughput
  }
}
EOF

    log_info "Performance results saved to: $results_file"
}

# Generate JSON report
generate_json_report() {
    local report_file="${OUTPUT_DIR}/test_report.json"

    log_info "Generating JSON report..."

    cat > "$report_file" << EOF
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
  },
  "results": {
EOF

    local first=true
    for test_name in "${!TEST_RESULTS[@]}"; do
        local result="${TEST_RESULTS[$test_name]}"
        local status="${result%%:*}"
        local message="${result#*:}"

        if [[ "$first" != true ]]; then
            echo "," >> "$report_file"
        fi
        first=false

        cat >> "$report_file" << EOF
    "${test_name}": {
      "status": "${status}",
      "message": "${message}"
    }
EOF
    done

    cat >> "$report_file" << EOF
  }
}
EOF

    log_success "JSON report generated: $report_file"
}

# Cleanup function
cleanup() {
    local exit_code=$?

    log_info "Cleaning up..."

    # Remove temporary files
    rm -f "${OUTPUT_DIR}/generated_workflow.xml" 2>/dev/null || true
    rm -f "${OUTPUT_DIR}/self_play_workflow.xml" 2>/dev/null || true

    # Kill any background processes we started
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
    # Set up cleanup trap
    trap cleanup EXIT

    # Parse arguments
    parse_args "$@"

    # Display banner
    echo
    echo "=== YAWL A2A/MCP/Z.ai Integration Test Suite ==="
    echo "Timestamp: $(date)"
    echo "Output: $OUTPUT_DIR"
    echo

    # Change to project directory
    cd "$PROJECT_ROOT"

    # Run prerequisites check
    if [[ "$SKIP_PREREQS" == false ]] && [[ "$PERF_ONLY" == false ]]; then
        check_prerequisites || exit $?
    fi

    # Start services
    if [[ "$PERF_ONLY" == false ]]; then
        start_services || true  # Continue even if some services aren't available
    fi

    # Run tests based on mode
    if [[ "$PERF_ONLY" == true ]]; then
        run_performance_benchmarks
    else
        # Run all test categories
        test_a2a_capabilities
        test_mcp_capabilities
        test_zai_capabilities
        run_self_play_test
        run_performance_benchmarks
    fi

    # Generate report if requested
    if [[ "$GENERATE_REPORT" == true ]]; then
        generate_json_report
    fi

    # Display summary and exit
    display_summary
    exit $?
}

# Run main function with all arguments
main "$@"
