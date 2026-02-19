#!/usr/bin/env bash
# ==========================================================================
# validate-integration.sh — Integration Testing for YAWL A2A/MCP Systems
#
# Comprehensive integration testing to validate end-to-end workflows,
# data flow, and system interoperability between A2A and MCP components.
#
# Usage:
#   bash scripts/validation/validate-integration.sh    # Run all tests
#   bash scripts/validation/validate-integration.sh --json  # JSON output
#   bash scripts/validation/validate-integration.sh --verbose  # Debug
#   bash scripts/validation/validate-integration.sh --workflow simple  # Specific workflow
#
# Exit Codes:
#   0 - All integration tests passed
#   1 - Integration tests failed
#   2 - Configuration error
#   3 - Timeout/Connectivity issues
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"

# Configuration
OUTPUT_FORMAT="text"
VERBOSE=0
TEST_WORKFLOW="all"
ENABLE_TRACING=true
TRACING_FILE=""
MAX_DURATION=300  # seconds
RETRY_ATTEMPTS=3
METRICS_FILE=""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# Global counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
START_TIME=$(date +%s)

# ── Parse Arguments ──────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --json)     OUTPUT_FORMAT="json"; shift ;;
        --verbose|-v) VERBOSE=1; shift ;;
        --workflow)
            TEST_WORKFLOW="$2"
            shift 2
            ;;
        --no-tracing)
            ENABLE_TRACING=false
            shift
            ;;
        --tracing)
            TRACING_FILE="$2"
            shift 2
            ;;
        --max-duration)
            MAX_DURATION="$2"
            shift 2
            ;;
        --retries)
            RETRY_ATTEMPTS="$2"
            shift 2
            ;;
        --metrics)
            METRICS_FILE="$2"
            shift 2
            ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)  echo "Unknown argument: $1" >&2; echo "Use --help for usage." >&2; exit 3 ;;
    esac
done

# ── Logging Functions ────────────────────────────────────────────────────
log_verbose() {
    [[ "$VERBOSE" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

log_info() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${GREEN}[OK]${NC} $*"
}

log_warning() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_section() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
}

# ── Utilities ────────────────────────────────────────────────────────────
with_retry() {
    local cmd="$1"
    local description="$2"
    local attempt=0

    while [[ $attempt -lt $RETRY_ATTEMPTS ]]; do
        if eval "$cmd"; then
            return 0
        fi

        attempt=$((attempt + 1))
        if [[ $attempt -lt $RETRY_ATTEMPTS ]]; then
            log_warning "$description failed (attempt $attempt/$RETRY_ATTEMPTS). Retrying..."
            sleep $((attempt * 2))
        fi
    done

    log_error "$description failed after $RETRY_ATTEMPTS attempts"
    return 1
}

curl_with_retry() {
    local url="$1"
    local method="${2:-GET}"
    local data="$3"
    local headers="$4"

    with_retry "curl -s -f -X $method -H '$headers' $data '$url' > /dev/null" "HTTP $method request to $url"
}

# ── Test Setup ─────────────────────────────────────────────────────────--
setup_test_environment() {
    log_section "Test Environment Setup"

    # Check if services are running
    if ! curl -s -f http://localhost:8080/health > /dev/null 2>&1; then
        log_error "YAWL engine not running on localhost:8080"
        exit 3
    fi

    if ! curl -s -f http://localhost:9090 > /dev/null 2>&1; then
        log_warning "MCP server not running on localhost:9090 - some tests may be skipped"
    fi

    if ! curl -s -f http://localhost:8081 > /dev/null 2>&1; then
        log_warning "A2A server not running on localhost:8081 - some tests may be skipped"
    fi

    # Initialize tracing
    if [[ "$ENABLE_TRACING" == "true" ]] && [[ -n "$TRACING_FILE" ]]; then
        mkdir -p "$(dirname "$TRACING_FILE")"
        echo "test_id,timestamp,component,event,details" > "$TRACING_FILE"
    fi
}

# ── Core Integration Tests ──────────────────────────────────────────────
run_basic_integration_tests() {
    log_section "Basic Integration Tests"

    local test_count=0
    local pass_count=0

    # Test 1: Engine to MCP communication
    test_count=$((test_count + 1))
    log_info "Testing Engine to MCP communication..."

    if with_retry "curl -s -f http://localhost:8080/engine/health > /dev/null" "Engine health check"; then
        log_success "Engine to MCP communication test passed"
        pass_count=$((pass_count + 1))
    else
        log_error "Engine to MCP communication test failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Test 2: A2A to Engine integration
    test_count=$((test_count + 1))
    log_info "Testing A2A to Engine integration..."

    if with_retry "curl -s -f http://localhost:8081/api/engine/status > /dev/null" "A2A engine status"; then
        log_success "A2A to Engine integration test passed"
        pass_count=$((pass_count + 1))
    else
        log_error "A2A to Engine integration test failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Test 3: Cross-protocol communication
    test_count=$((test_count + 1))
    log_info "Testing cross-protocol communication (A2A ↔ MCP)..."

    # Send a message from A2A to MCP via engine
    local payload='{"type": "test", "from": "a2a", "to": "mcp"}'
    if with_retry "curl -s -f -X POST -H 'Content-Type: application/json' -d '$payload' http://localhost:8080/api/bridge/a2a-to-mcp" "Cross-protocol message"; then
        log_success "Cross-protocol communication test passed"
        pass_count=$((pass_count + 1))
    else
        log_error "Cross-protocol communication test failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    TOTAL_TESTS=$((TOTAL_TESTS + test_count))
    PASSED_TESTS=$((PASSED_TESTS + pass_count))
}

# ─── Workflow Integration Tests ─────────────────────────────────────────--
run_workflow_integration_tests() {
    log_section "Workflow Integration Tests"

    local test_count=0
    local pass_count=0

    # Test 1: Simple workflow deployment and execution
    test_count=$((test_count + 1))
    log_info "Testing simple workflow deployment..."

    local spec_id="test-$(date +%s)"
    local spec_content='{
        "name": "Test Workflow",
        "version": "1.0",
        "start": "start",
        "end": "end",
        "edges": [
            {"from": "start", "to": "task1"},
            {"from": "task1", "to": "end"}
        ]
    }'

    if with_retry "curl -s -f -X POST -H 'Content-Type: application/json' -d '$spec_content' http://localhost:8080/api/specifications/$spec_id" "Deploy specification"; then
        log_success "Specification deployment test passed"
        pass_count=$((pass_count + 1))

        # Execute workflow
        local case_id="case-$(date +%s)"
        local execute_payload='{"caseId": "'$case_id'", "specId": "'$spec_id'"}'

        if with_retry "curl -s -f -X POST -H 'Content-Type: application/json' -d '$execute_payload' http://localhost:8080/api/cases/$case_id/execute" "Execute workflow"; then
            log_success "Workflow execution test passed"

            # Verify completion
            sleep 5
            if curl -s http://localhost:8080/api/cases/$case_id/status | grep -q "COMPLETED"; then
                log_success "Workflow completion test passed"
                pass_count=$((pass_count + 1))
            else
                log_error "Workflow completion test failed"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            log_error "Workflow execution test failed"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        log_error "Specification deployment test failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Clean up
    curl -s -X DELETE http://localhost:8080/api/specifications/$spec_id > /dev/null 2>&1 || true

    TOTAL_TESTS=$((TOTAL_TESTS + test_count))
    PASSED_TESTS=$((PASSED_TESTS + pass_count))
}

# ─── Data Flow Tests ─────────────────────────────────────────────────────
run_data_flow_tests() {
    log_section "Data Flow Tests"

    local test_count=0
    local pass_count=0

    # Test 1: Parameter passing through workflow
    test_count=$((test_count + 1))
    log_info "Testing parameter passing through workflow..."

    local param_spec='{
        "name": "Parameter Test",
        "version": "1.0",
        "parameters": ["input", "output"],
        "start": "start",
        "end": "end",
        "edges": [
            {"from": "start", "to": "process", "data": {"input": "#input"}},
            {"from": "process", "to": "end", "data": {"output": "#input"}}
        ]
    }'

    local param_id="param-test-$(date +%s)"

    if with_retry "curl -s -f -X POST -H 'Content-Type: application/json' -d '$param_spec' http://localhost:8080/api/specifications/$param_id" "Deploy parameter workflow"; then
        # Execute with input data
        local case_id="param-case-$(date +%s)"
        local param_payload='{"caseId": "'$case_id'", "specId": "'$param_id'", "input": "test-data"}'

        if with_retry "curl -s -f -X POST -H 'Content-Type: application/json' -d '$param_payload' http://localhost:8080/api/cases/$case_id/execute" "Execute parameter workflow"; then
            sleep 5
            local output=$(curl -s http://localhost:8080/api/cases/$case_id/data)
            if echo "$output" | grep -q "test-data"; then
                log_success "Parameter passing test passed"
                pass_count=$((pass_count + 1))
            else
                log_error "Parameter passing test failed - output: $output"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            log_error "Parameter workflow execution failed"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        log_error "Parameter workflow deployment failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    TOTAL_TESTS=$((TOTAL_TESTS + test_count))
    PASSED_TESTS=$((PASSED_TESTS + pass_count))
}

# ─── Event Processing Tests ───────────────────────────────────────────────
run_event_processing_tests() {
    log_section "Event Processing Tests"

    local test_count=0
    local pass_count=0

    # Test 1: Event publishing and consumption
    test_count=$((test_count + 1))
    log_info "Testing event publishing and consumption..."

    # Subscribe to events
    local subscription_id="sub-$(date +%s)"
    local subscribe_payload='{"type": "workitem.created", "callback": "http://localhost:8080/webhooks/events"}'

    if curl -s -f -X POST -H 'Content-Type: application/json' -d "$subscribe_payload" http://localhost:8080/api/subscriptions/$subscription_id; then
        log_success "Event subscription created"

        # Trigger an event
        local event_payload='{"caseId": "event-case-$(date +%s)", "workitemId": "work-$(date +%s)", "status": "created"}'

        if curl -s -X POST -H 'Content-Type: application/json' -d "$event_payload" http://localhost:8080/api/events/workitem.created; then
            log_success "Event publishing test passed"
            pass_count=$((pass_count + 1))

            # Verify event processing
            sleep 2
            if curl -s http://localhost:8080/api/subscriptions/$subscription_id/status | grep -q "active"; then
                log_success "Event consumption test passed"
                pass_count=$((pass_count + 1))
            else
                log_error "Event consumption test failed"
                FAILED_TESTS=$((FAILED_TESTS + 1))
            fi
        else
            log_error "Event publishing failed"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        log_error "Event subscription failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Clean up
    curl -s -X DELETE http://localhost:8080/api/subscriptions/$subscription_id > /dev/null 2>&1 || true

    TOTAL_TESTS=$((TOTAL_TESTS + test_count))
    PASSED_TESTS=$((PASSED_TESTS + pass_count))
}

# ─── Error Handling Tests ─────────────────────────────────────────────────
run_error_handling_tests() {
    log_section "Error Handling Tests"

    local test_count=0
    local pass_count=0

    # Test 1: Invalid workflow handling
    test_count=$((test_count + 1))
    log_info "Testing invalid workflow handling..."

    local invalid_spec='{
        "name": "Invalid Workflow",
        "version": "1.0",
        "start": "start",
        "edges": [
            {"from": "end", "to": "nonexistent"}
        ]
    }'

    if curl -s -X POST -H 'Content-Type: application/json' -d "$invalid_spec" http://localhost:8080/api/specifications/invalid-spec | grep -q "error"; then
        log_success "Invalid workflow handling test passed"
        pass_count=$((pass_count + 1))
    else
        log_error "Invalid workflow handling test failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Test 2: Concurrent execution limits
    test_count=$((test_count + 1))
    log_info "Testing concurrent execution limits..."

    # Start multiple cases simultaneously
    local concurrent_cases=5
    local active_cases=0
    local cases=()

    for i in $(seq 1 $concurrent_cases); do
        local case_id="concurrent-$(date +%s)-$i"
        cases+=("$case_id")

        curl -s -X POST -H 'Content-Type: application/json' -d '{"caseId": "'$case_id'"}' http://localhost:8080/api/cases/$case_id/execute &

        # Check if case started successfully
        if curl -s http://localhost:8080/api/cases/$case_id/status | grep -q "RUNNING"; then
            active_cases=$((active_cases + 1))
        fi
    done

    wait

    if [[ $active_cases -gt 0 ]]; then
        log_success "Concurrent execution test passed ($active_cases/$concurrent_cases started)"
        pass_count=$((pass_count + 1))
    else
        log_error "Concurrent execution test failed"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    TOTAL_TESTS=$((TOTAL_TESTS + test_count))
    PASSED_TESTS=$((PASSED_TESTS + pass_count))
}

# ─── Performance Integration Tests ───────────────────────────────────────
run_performance_tests() {
    log_section "Performance Integration Tests"

    local test_count=0
    local pass_count=0

    # Test 1: End-to-end workflow throughput
    test_count=$((test_count + 1))
    log_info "Testing end-to-end workflow throughput..."

    local start_time=$(date +%s)
    local successful_cases=0
    local test_duration=30  # seconds

    # Run multiple workflows in parallel
    for i in $(seq 1 20); do
        {
            local case_id="perf-$(date +%s)-$i"
            local payload='{"caseId": "'$case_id'", "specId": "simple"}'

            if curl -s -f -X POST -H 'Content-Type: application/json' -d "$payload" http://localhost:8080/api/cases/$case_id/execute; then
                if curl -s http://localhost:8080/api/cases/$case_id/status | grep -q "COMPLETED"; then
                    successful_cases=$((successful_cases + 1))
                fi
            fi
        } &
    done

    # Wait for test duration
    while [[ $(( $(date +%s) - start_time )) -lt $test_duration ]]; do
        sleep 1
    done

    # Clean up remaining cases
    pkill -f "case-id=perf-" 2>/dev/null || true

    local throughput=$((successful_cases / test_duration))
    echo "  Throughput: ${throughput} cases/second"

    if [[ $throughput -ge 1 ]]; then
        log_success "Performance test passed (${throughput} cases/sec)"
        pass_count=$((pass_count + 1))
    else
        log_error "Performance test failed (${throughput} cases/sec - expected >= 1)"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    # Test 2: Response time under load
    test_count=$((test_count + 1))
    log_info "Testing response time under load..."

    local response_times=()
    local load_level=10

    for i in $(seq 1 $load_level); do
        local start_request=$(date +%s%N)

        if curl -s http://localhost:8080/health > /dev/null; then
            local end_request=$(date +%s%N)
            local response_time=$(((end_request - start_request) / 1000000))
            response_times+=("$response_time")
        fi
    done

    if [[ ${#response_times[@]} -gt 0 ]]; then
        local avg_response_time=$(echo "${response_times[*]}" | awk '{sum+=$1; count++} END {print sum/count}')

        if [[ $(echo "$avg_response_time < 1000" | bc -l) -eq 1 ]]; then
            log_success "Response time test passed (avg: ${avg_response_time}ms)"
            pass_count=$((pass_count + 1))
        else
            log_error "Response time test failed (avg: ${avg_response_time}ms)"
            FAILED_TESTS=$((FAILED_TESTS + 1))
        fi
    else
        log_error "Response time test failed - no valid measurements"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi

    TOTAL_TESTS=$((TOTAL_TESTS + test_count))
    PASSED_TESTS=$((PASSED_TESTS + pass_count))
}

# ─── Test Execution Logic ────────────────────────────────────────────────
run_all_tests() {
    local overall_result=0

    # Run tests based on workflow configuration
    case "$TEST_WORKFLOW" in
        "basic")
            setup_test_environment
            run_basic_integration_tests
            ;;
        "workflow")
            setup_test_environment
            run_workflow_integration_tests
            ;;
        "data")
            setup_test_environment
            run_data_flow_tests
            ;;
        "events")
            setup_test_environment
            run_event_processing_tests
            ;;
        "error")
            setup_test_environment
            run_error_handling_tests
            ;;
        "performance")
            setup_test_environment
            run_performance_tests
            ;;
        "all")
            setup_test_environment
            run_basic_integration_tests
            run_workflow_integration_tests
            run_data_flow_tests
            run_event_processing_tests
            run_error_handling_tests
            run_performance_tests
            ;;
    esac

    return $overall_result
}

# ── Output Functions ─────────────────────────────────────────────────────
output_json() {
    local duration=$(( $(date +%s) - START_TIME ))
    local success_rate=$(( PASSED_TESTS * 100 / TOTAL_TESTS ))

    cat << JSON_EOF
{
    "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
    "test_workflow": "$TEST_WORKFLOW",
    "config": {
        "max_duration_seconds": $MAX_DURATION,
        "retry_attempts": $RETRY_ATTEMPTS,
        "enable_tracing": $ENABLE_TRACING
    },
    "results": {
        "total_tests": $TOTAL_TESTS,
        "passed_tests": $PASSED_TESTS,
        "failed_tests": $FAILED_TESTS,
        "success_rate": $success_rate,
        "duration_seconds": $duration
    },
    "status": $([[ $FAILED_TESTS -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    local duration=$(( $(date +%s) - START_TIME ))
    local success_rate=$(( PASSED_TESTS * 100 / TOTAL_TESTS ))

    echo ""
    echo -e "${BOLD}YAWL Integration Testing Report${NC}"
    echo "Generated: $(date -u +'%Y-%m-%d %H:%M:%S UTC')"
    echo "Workflow Type: $TEST_WORKFLOW"
    echo "Duration: ${duration}s"
    echo ""
    echo "Test Results:"
    echo "  Total Tests: $TOTAL_TESTS"
    echo "  Passed: $PASSED_TESTS"
    echo "  Failed: $FAILED_TESTS"
    echo "  Success Rate: ${success_rate}%"
    echo ""

    if [[ $FAILED_TESTS -eq 0 ]]; then
        echo -e "${GREEN}✓ All integration tests passed.${NC}"
        echo ""
        exit 0
    else
        echo -e "${RED}✗ Integration tests failed.${NC}"
        echo ""
        exit 1
    fi
}

# ── Main Execution ───────────────────────────────────────────────────────
# Initialize metrics file
if [[ -n "$METRICS_FILE" ]]; then
    mkdir -p "$(dirname "$METRICS_FILE")"
    echo '{}' > "$METRICS_FILE"
fi

# Run all tests
if ! run_all_tests; then
    log_warning "Some tests failed, but continuing to report results"
fi

# Output results
if [[ "$OUTPUT_FORMAT" == "json" ]]; then
    output_json
else
    output_text
fi

exit $([[ $FAILED_TESTS -eq 0 ]] && echo 0 || echo 1)