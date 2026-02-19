#!/usr/bin/env bash
# ==========================================================================
# test-performance-benchmark.sh — A2A Performance Benchmark Tests
#
# Measures and validates performance characteristics of the A2A server:
#   - Agent card endpoint latency
#   - Authentication latency
#   - Message processing throughput
#   - Concurrent request handling
#   - Memory usage under load
#
# Performance Baselines (must not exceed):
#   - Agent card response: < 100ms
#   - Authentication: < 50ms
#   - Message processing: < 500ms
#   - Concurrent 100 requests: < 10s total
#
# Usage:
#   bash scripts/validation/a2a/tests/test-performance-benchmark.sh
#   bash scripts/validation/a2a/tests/test-performance-benchmark.sh --verbose
#   bash scripts/validation/a2a/tests/test-performance-benchmark.sh --json
#
# Exit Codes:
#   0 - All performance tests passed
#   1 - One or more performance tests failed
#   2 - Server not available
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/../lib"

# Source A2A common functions
source "${LIB_DIR}/a2a-common.sh" 2>/dev/null || {
    echo "[ERROR] A2A client library not found: ${LIB_DIR}/a2a-common.sh" >&2
    exit 2
}

# Configuration
VERBOSE="${VERBOSE:-0}"
OUTPUT_FORMAT="${OUTPUT_FORMAT:-text}"
A2A_SERVER_HOST="${A2A_SERVER_HOST:-localhost}"
A2A_SERVER_PORT="${A2A_SERVER_PORT:-8080}"
A2A_TIMEOUT="${A2A_TIMEOUT:-30}"

# Performance baselines (milliseconds)
AGENT_CARD_BASELINE_MS=100
AUTH_LATENCY_BASELINE_MS=50
MESSAGE_PROCESSING_BASELINE_MS=500
CONCURRENT_BASELINE_SEC=10

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a TEST_RESULTS=()
declare -a METRICS=()

# ── Logging Functions ─────────────────────────────────────────────────────
log_info() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${GREEN}[PASS]${NC} $*"
}

log_error() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${RED}[FAIL]${NC} $*" >&2
}

log_metric() {
    [[ "$OUTPUT_FORMAT" == "text" ]] && echo -e "${YELLOW}[METRIC]${NC} $*"
}

log_verbose() {
    [[ "$VERBOSE" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

# ── Timing Helper ─────────────────────────────────────────────────────────
get_time_ms() {
    # Cross-platform millisecond timing
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS - use perl for millisecond precision
        perl -MTime::HiRes=time -e 'printf "%.0f\n", time * 1000'
    else
        # Linux
        date +%s%3N
    fi
}

# ── Test Runner Functions ─────────────────────────────────────────────────
run_test() {
    local test_name="$1"
    local test_description="$2"
    local test_function="$3"
    local baseline_ms="${4:-0}"

    ((TOTAL_TESTS++)) || true

    log_verbose "Running: $test_name - $test_description"

    local start_time end_time duration result
    start_time=$(get_time_ms)

    if eval "$test_function"; then
        end_time=$(get_time_ms)
        duration=$((end_time - start_time))

        ((PASSED_TESTS++)) || true
        TEST_RESULTS+=("{\"name\":\"${test_name}\",\"status\":\"PASS\",\"description\":\"${test_description}\",\"duration_ms\":${duration},\"baseline_ms\":${baseline_ms}}")
        METRICS+=("{\"test\":\"${test_name}\",\"duration_ms\":${duration}}")
        log_success "$test_name (${duration}ms)"
        return 0
    else
        end_time=$(get_time_ms)
        duration=$((end_time - start_time))

        ((FAILED_TESTS++)) || true
        TEST_RESULTS+=("{\"name\":\"${test_name}\",\"status\":\"FAIL\",\"description\":\"${test_description}\",\"duration_ms\":${duration},\"baseline_ms\":${baseline_ms}}")
        log_error "$test_name (${duration}ms)"
        return 1
    fi
}

# ── Agent Card Performance Tests ──────────────────────────────────────────
benchmark_agent_card_single() {
    local start_time end_time duration

    start_time=$(get_time_ms)
    curl -s --connect-timeout ${A2A_TIMEOUT} \
        "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1
    end_time=$(get_time_ms)

    duration=$((end_time - start_time))
    log_metric "Agent card single request: ${duration}ms"

    [[ $duration -lt $AGENT_CARD_BASELINE_MS ]]
}

benchmark_agent_card_multiple() {
    local iterations=10
    local total_time=0
    local i start_time end_time duration

    for ((i=1; i<=iterations; i++)); do
        start_time=$(get_time_ms)
        curl -s --connect-timeout ${A2A_TIMEOUT} \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1
        end_time=$(get_time_ms)
        duration=$((end_time - start_time))
        total_time=$((total_time + duration))
    done

    local avg_time=$((total_time / iterations))
    log_metric "Agent card average (${iterations} requests): ${avg_time}ms"

    [[ $avg_time -lt $AGENT_CARD_BASELINE_MS ]]
}

# ── Authentication Performance Tests ──────────────────────────────────────
benchmark_auth_api_key() {
    local api_key
    api_key=$(generate_api_key)

    local start_time end_time duration

    start_time=$(get_time_ms)
    curl -s -w "%{http_code}" --connect-timeout ${A2A_TIMEOUT} \
        -H "X-API-Key: ${api_key}" \
        "${A2A_BASE_URL}/" > /dev/null 2>&1
    end_time=$(get_time_ms)

    duration=$((end_time - start_time))
    log_metric "API Key auth: ${duration}ms"

    [[ $duration -lt $AUTH_LATENCY_BASELINE_MS ]]
}

benchmark_auth_jwt() {
    local jwt
    jwt=$(generate_jwt "perf-test-agent" '"workflow:query"' "yawl-a2a")

    local start_time end_time duration

    start_time=$(get_time_ms)
    curl -s -w "%{http_code}" --connect-timeout ${A2A_TIMEOUT} \
        -H "Authorization: Bearer ${jwt}" \
        "${A2A_BASE_URL}/" > /dev/null 2>&1
    end_time=$(get_time_ms)

    duration=$((end_time - start_time))
    log_metric "JWT auth: ${duration}ms"

    [[ $duration -lt $AUTH_LATENCY_BASELINE_MS ]]
}

# ── Message Processing Performance Tests ──────────────────────────────────
benchmark_message_processing() {
    local api_key
    api_key=$(generate_api_key)

    local start_time end_time duration

    start_time=$(get_time_ms)
    curl -s -w "%{http_code}" --connect-timeout ${A2A_TIMEOUT} \
        -X POST \
        -H "Content-Type: application/json" \
        -H "X-API-Key: ${api_key}" \
        -d '{"message":"list specifications"}' \
        "${A2A_BASE_URL}/" > /dev/null 2>&1
    end_time=$(get_time_ms)

    duration=$((end_time - start_time))
    log_metric "Message processing: ${duration}ms"

    [[ $duration -lt $MESSAGE_PROCESSING_BASELINE_MS ]]
}

benchmark_message_processing_multiple() {
    local iterations=5
    local total_time=0
    local i start_time end_time duration

    local api_key
    api_key=$(generate_api_key)

    for ((i=1; i<=iterations; i++)); do
        start_time=$(get_time_ms)
        curl -s -w "%{http_code}" --connect-timeout ${A2A_TIMEOUT} \
            -X POST \
            -H "Content-Type: application/json" \
            -H "X-API-Key: ${api_key}" \
            -d '{"message":"list specifications"}' \
            "${A2A_BASE_URL}/" > /dev/null 2>&1
        end_time=$(get_time_ms)
        duration=$((end_time - start_time))
        total_time=$((total_time + duration))
    done

    local avg_time=$((total_time / iterations))
    log_metric "Message processing average (${iterations} requests): ${avg_time}ms"

    [[ $avg_time -lt $MESSAGE_PROCESSING_BASELINE_MS ]]
}

# ── Concurrent Request Tests ──────────────────────────────────────────────
benchmark_concurrent_requests_10() {
    local concurrent=10
    local start_time end_time duration

    start_time=$(get_time_ms)

    for ((i=1; i<=concurrent; i++)); do
        curl -s --connect-timeout ${A2A_TIMEOUT} \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1 &
    done
    wait

    end_time=$(get_time_ms)
    duration=$((end_time - start_time))

    local baseline_ms=$((CONCURRENT_BASELINE_SEC * 100))
    log_metric "Concurrent ${concurrent} requests: ${duration}ms"

    [[ $duration -lt $baseline_ms ]]
}

benchmark_concurrent_requests_50() {
    local concurrent=50
    local start_time end_time duration

    start_time=$(get_time_ms)

    for ((i=1; i<=concurrent; i++)); do
        curl -s --connect-timeout ${A2A_TIMEOUT} \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1 &
    done
    wait

    end_time=$(get_time_ms)
    duration=$((end_time - start_time))

    local baseline_ms=$((CONCURRENT_BASELINE_SEC * 1000))
    log_metric "Concurrent ${concurrent} requests: ${duration}ms"

    [[ $duration -lt $baseline_ms ]]
}

benchmark_concurrent_mixed_requests() {
    local concurrent=20
    local api_key
    api_key=$(generate_api_key)
    local start_time end_time duration

    start_time=$(get_time_ms)

    for ((i=1; i<=concurrent; i++)); do
        if (( i % 2 == 0 )); then
            # GET request
            curl -s --connect-timeout ${A2A_TIMEOUT} \
                "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1 &
        else
            # POST request
            curl -s --connect-timeout ${A2A_TIMEOUT} \
                -X POST \
                -H "Content-Type: application/json" \
                -H "X-API-Key: ${api_key}" \
                -d '{"message":"list specifications"}' \
                "${A2A_BASE_URL}/" > /dev/null 2>&1 &
        fi
    done
    wait

    end_time=$(get_time_ms)
    duration=$((end_time - start_time))

    log_metric "Concurrent mixed ${concurrent} requests: ${duration}ms"

    [[ $duration -lt $((CONCURRENT_BASELINE_SEC * 500)) ]]
}

# ── Throughput Tests ──────────────────────────────────────────────────────
benchmark_throughput() {
    local duration_sec=5
    local requests=0
    local start_time current_time elapsed

    start_time=$(get_time_ms)

    while true; do
        curl -s --connect-timeout ${A2A_TIMEOUT} \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1
        ((requests++)) || true

        current_time=$(get_time_ms)
        elapsed=$(((current_time - start_time) / 1000))

        [[ $elapsed -ge $duration_sec ]] && break
    done

    local rps=$((requests / duration_sec))
    log_metric "Throughput: ${rps} requests/second (${requests} requests in ${duration_sec}s)"

    # Minimum throughput: 10 requests/second
    [[ $rps -ge 10 ]]
}

# ── Latency Percentile Tests ──────────────────────────────────────────────
benchmark_latency_percentiles() {
    local iterations=20
    local -a latencies=()
    local i start_time end_time duration

    for ((i=1; i<=iterations; i++)); do
        start_time=$(get_time_ms)
        curl -s --connect-timeout ${A2A_TIMEOUT} \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1
        end_time=$(get_time_ms)
        duration=$((end_time - start_time))
        latencies+=($duration)
    done

    # Sort latencies
    IFS=$'\n' sorted=($(sort -n <<<"${latencies[*]}")); unset IFS

    local p50=${sorted[$((iterations / 2))]}
    local p95=${sorted[$((iterations * 95 / 100))]}
    local p99=${sorted[$((iterations * 99 / 100))]}

    log_metric "Latency P50: ${p50}ms, P95: ${p95}ms, P99: ${p99}ms"

    # P99 should be under 200ms
    [[ $p99 -lt 200 ]]
}

# ── Run All Performance Tests ─────────────────────────────────────────────
run_all_performance_tests() {
    log_info "Starting A2A Performance Benchmark Tests"
    log_info "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    log_info "Baselines: AgentCard=${AGENT_CARD_BASELINE_MS}ms, Auth=${AUTH_LATENCY_BASELINE_MS}ms, Message=${MESSAGE_PROCESSING_BASELINE_MS}ms"
    echo ""

    # Agent Card Performance
    echo "=== Agent Card Performance ==="
    run_test "agent_card_single" "Single agent card request under baseline" \
        "benchmark_agent_card_single" "$AGENT_CARD_BASELINE_MS"
    run_test "agent_card_multiple" "Multiple agent card requests average under baseline" \
        "benchmark_agent_card_multiple" "$AGENT_CARD_BASELINE_MS"
    echo ""

    # Authentication Performance
    echo "=== Authentication Performance ==="
    run_test "auth_api_key" "API Key authentication under baseline" \
        "benchmark_auth_api_key" "$AUTH_LATENCY_BASELINE_MS"
    run_test "auth_jwt" "JWT authentication under baseline" \
        "benchmark_auth_jwt" "$AUTH_LATENCY_BASELINE_MS"
    echo ""

    # Message Processing Performance
    echo "=== Message Processing Performance ==="
    run_test "message_single" "Single message processing under baseline" \
        "benchmark_message_processing" "$MESSAGE_PROCESSING_BASELINE_MS"
    run_test "message_multiple" "Multiple message processing average under baseline" \
        "benchmark_message_processing_multiple" "$MESSAGE_PROCESSING_BASELINE_MS"
    echo ""

    # Concurrent Request Performance
    echo "=== Concurrent Request Performance ==="
    run_test "concurrent_10" "10 concurrent requests under baseline" \
        "benchmark_concurrent_requests_10" "$((CONCURRENT_BASELINE_SEC * 100))"
    run_test "concurrent_50" "50 concurrent requests under baseline" \
        "benchmark_concurrent_requests_50" "$((CONCURRENT_BASELINE_SEC * 1000))"
    run_test "concurrent_mixed" "20 mixed concurrent requests under baseline" \
        "benchmark_concurrent_mixed_requests" "$((CONCURRENT_BASELINE_SEC * 500))"
    echo ""

    # Throughput and Latency
    echo "=== Throughput and Latency ==="
    run_test "throughput" "Throughput >= 10 requests/second" \
        "benchmark_throughput" "0"
    run_test "latency_percentiles" "P99 latency under 200ms" \
        "benchmark_latency_percentiles" "200"
    echo ""
}

# ── Output Functions ───────────────────────────────────────────────────────
output_json() {
    local results_json=$(IFS=,; echo "${TEST_RESULTS[*]}")
    local metrics_json=$(IFS=,; echo "${METRICS[*]}")
    cat << JSON_EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "server": {
    "host": "${A2A_SERVER_HOST}",
    "port": ${A2A_SERVER_PORT}
  },
  "category": "performance",
  "baselines": {
    "agent_card_ms": ${AGENT_CARD_BASELINE_MS},
    "auth_ms": ${AUTH_LATENCY_BASELINE_MS},
    "message_ms": ${MESSAGE_PROCESSING_BASELINE_MS},
    "concurrent_sec": ${CONCURRENT_BASELINE_SEC}
  },
  "total_tests": ${TOTAL_TESTS},
  "passed": ${PASSED_TESTS},
  "failed": ${FAILED_TESTS},
  "results": [${results_json}],
  "metrics": [${metrics_json}],
  "status": $([[ "${FAILED_TESTS}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo "==========================================="
    echo "A2A Performance Benchmark Results"
    echo "==========================================="
    echo "Total Tests: ${TOTAL_TESTS}"
    echo "Passed: ${PASSED_TESTS}"
    echo "Failed: ${FAILED_TESTS}"
    echo ""

    if [[ ${FAILED_TESTS} -eq 0 ]]; then
        echo -e "${GREEN}All performance benchmarks passed.${NC}"
        return 0
    else
        echo -e "${RED}${FAILED_TESTS} performance benchmarks failed.${NC}"
        return 1
    fi
}

# ── Main Execution ────────────────────────────────────────────────────────
main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --json)     OUTPUT_FORMAT="json"; shift ;;
            --verbose|-v) VERBOSE=1; shift ;;
            -h|--help)
                sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
                exit 0 ;;
            *)  shift ;;
        esac
    done

    # Check server availability
    log_verbose "Checking A2A server availability..."
    if ! a2a_ping; then
        echo "[ERROR] A2A server not running at ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}" >&2
        exit 2
    fi

    log_verbose "A2A server is available"

    # Warmup requests
    log_info "Running warmup requests..."
    for i in {1..3}; do
        curl -s --connect-timeout ${A2A_TIMEOUT} \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1
    done

    # Run tests
    run_all_performance_tests

    # Output results
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output_json
    else
        output_text
    fi
}

main "$@"
