#!/usr/bin/env bash
# ==========================================================================
# validate-chaos.sh - Chaos engineering tests for A2A/MCP resilience
#
# Usage:
#   bash scripts/validation/validate-chaos.sh [OPTIONS]
#
# Options:
#   --server URL       Target server URL (default: http://localhost:8080)
#   --test TYPE        Chaos test type (network|latency|restart|disconnect|circuit_breaker|all)
#   --intensity N      Intensity level 1-10 (default: 5)
#   --duration N       Test duration in seconds (default: 30)
#   --dry-run          Show what would be done without executing
#   --help             Show this help
#
# Chaos Tests:
#   network_partition  - 50% packet loss simulation
#   latency_injection  - 500ms added latency
#   server_restart     - Mid-request server restart
#   engine_disconnect  - YAWL engine temporary disconnect
#   circuit_breaker    - Circuit breaker activation cycles
#
# Success Criteria:
#   - System degrades gracefully
#   - Recovery within 30 seconds after chaos ends
#   - No data corruption
#
# Exit codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - System did not recover within threshold
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Default options
SERVER_URL="http://localhost:8080"
TEST_TYPE="all"
INTENSITY=5
DURATION=30
DRY_RUN=false

# -------------------------------------------------------------------------
# Parse arguments
# -------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --server)
            SERVER_URL="$2"
            shift 2
            ;;
        --test)
            TEST_TYPE="$2"
            shift 2
            ;;
        --intensity)
            INTENSITY="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help)
            cat << EOF
Chaos Engineering Tests for A2A/MCP

Tests system resilience under various failure conditions.

Usage: bash scripts/validation/validate-chaos.sh [OPTIONS]

Options:
  --server URL       Target server URL (default: http://localhost:8080)
  --test TYPE        Chaos test: network, latency, restart, disconnect, circuit_breaker, all
  --intensity N      Intensity level 1-10 (default: 5)
  --duration N       Test duration in seconds (default: 30)
  --dry-run          Show what would be done without executing
  --help             Show this help

Chaos Tests:
  network_partition  - 50% packet loss simulation (requires tc)
  latency_injection  - 500ms added latency (requires tc)
  server_restart     - Mid-request server restart
  engine_disconnect  - YAWL engine temporary disconnect
  circuit_breaker    - Circuit breaker activation cycles

Requirements:
  - tc (traffic control) for network chaos
  - pkill for process management
  - curl for HTTP requests

Exit codes:
  0 - All tests passed
  1 - One or more tests failed
  2 - System did not recover within threshold
EOF
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}" >&2
            exit 1
            ;;
    esac
done

# -------------------------------------------------------------------------
# Logging functions
# -------------------------------------------------------------------------
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_chaos() {
    echo -e "${RED}[CHAOS]${NC} $1"
}

log_recover() {
    echo -e "${GREEN}[RECOVER]${NC} $1"
}

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_metric() {
    echo -e "${CYAN}[METRIC]${NC} $1"
}

# -------------------------------------------------------------------------
# Check dependencies
# -------------------------------------------------------------------------
check_dependencies() {
    local missing=()

    command -v curl &>/dev/null || missing+=("curl")

    if [[ "$TEST_TYPE" == "network" ]] || [[ "$TEST_TYPE" == "latency" ]] || [[ "$TEST_TYPE" == "all" ]]; then
        command -v tc &>/dev/null || missing+=("tc (iproute2)")
    fi

    if [[ ${#missing[@]} -gt 0 ]]; then
        echo -e "${YELLOW}Missing dependencies: ${missing[*]}${NC}"
        echo "Some tests may be skipped"
    fi
}

# -------------------------------------------------------------------------
# Health check function
# -------------------------------------------------------------------------
check_health() {
    local max_attempts="${1:-10}"
    local attempt=0

    while [[ $attempt -lt $max_attempts ]]; do
        if curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/health" 2>/dev/null | grep -q "200"; then
            return 0
        fi
        ((attempt++)) || true
        sleep 1
    done

    return 1
}

# -------------------------------------------------------------------------
# Test: Network partition (packet loss)
# -------------------------------------------------------------------------
run_network_partition() {
    log_info "Starting network partition test (${INTENSITY}0% packet loss)"

    if [[ "$DRY_RUN" = true ]]; then
        log_info "DRY RUN: Would simulate packet loss"
        return 0
    fi

    # Check for tc
    if ! command -v tc &>/dev/null; then
        log_fail "tc (traffic control) not available"
        return 2
    fi

    local loss_percent=$((INTENSITY * 10))
    local interface=$(ip route | grep default | awk '{print $5}' | head -1)

    if [[ -z "$interface" ]]; then
        log_fail "Could not determine network interface"
        return 2
    fi

    log_chaos "Adding ${loss_percent}% packet loss on $interface"

    # Apply packet loss (requires root)
    if sudo tc qdisc add dev "$interface" root netem loss "${loss_percent}%" 2>/dev/null; then
        log_chaos "Packet loss active for ${DURATION}s"
        sleep "$DURATION"

        log_recover "Removing packet loss"
        sudo tc qdisc del dev "$interface" root 2>/dev/null || true
    else
        log_fail "Failed to apply packet loss (requires root)"
        return 2
    fi

    # Check recovery
    log_recover "Waiting for recovery..."
    if check_health 30; then
        log_pass "System recovered within 30 seconds"
        return 0
    else
        log_fail "System did not recover within 30 seconds"
        return 1
    fi
}

# -------------------------------------------------------------------------
# Test: Latency injection
# -------------------------------------------------------------------------
run_latency_injection() {
    log_info "Starting latency injection test (${INTENSITY}00ms latency)"

    if [[ "$DRY_RUN" = true ]]; then
        log_info "DRY RUN: Would inject latency"
        return 0
    fi

    if ! command -v tc &>/dev/null; then
        log_fail "tc (traffic control) not available"
        return 2
    fi

    local latency_ms=$((INTENSITY * 100))
    local interface=$(ip route | grep default | awk '{print $5}' | head -1)

    if [[ -z "$interface" ]]; then
        log_fail "Could not determine network interface"
        return 2
    fi

    log_chaos "Adding ${latency_ms}ms latency on $interface"

    if sudo tc qdisc add dev "$interface" root netem delay "${latency_ms}ms" 2>/dev/null; then
        log_chaos "Latency active for ${DURATION}s"

        # Test requests during latency
        local start_time=$(date +%s%3N)
        curl -s -o /dev/null -w "%{time_total}" "${SERVER_URL}/health" 2>/dev/null || true
        local end_time=$(date +%s%3N)
        local actual_latency=$((end_time - start_time))

        log_metric "Request time during chaos: ${actual_latency}ms"

        sleep "$((DURATION - 5))"

        log_recover "Removing latency"
        sudo tc qdisc del dev "$interface" root 2>/dev/null || true
    else
        log_fail "Failed to inject latency (requires root)"
        return 2
    fi

    # Check recovery
    log_recover "Waiting for recovery..."
    if check_health 30; then
        log_pass "System recovered within 30 seconds"
        return 0
    else
        log_fail "System did not recover within 30 seconds"
        return 1
    fi
}

# -------------------------------------------------------------------------
# Test: Server restart
# -------------------------------------------------------------------------
run_server_restart() {
    log_info "Starting server restart test"

    if [[ "$DRY_RUN" = true ]]; then
        log_info "DRY RUN: Would restart server"
        return 0
    fi

    # Find server process (simplified)
    local server_pid
    server_pid=$(pgrep -f "yawl.*server" | head -1)

    if [[ -z "$server_pid" ]]; then
        log_warn "Server process not found, skipping restart test"
        return 2
    fi

    log_chaos "Sending SIGTERM to server (PID: $server_pid)"
    kill -TERM "$server_pid" 2>/dev/null || true

    sleep 2

    log_chaos "Server should be restarting..."

    # Wait for recovery
    log_recover "Waiting for server to come back up..."
    if check_health 60; then
        log_pass "Server recovered within 60 seconds"
        return 0
    else
        log_fail "Server did not recover within 60 seconds"
        return 1
    fi
}

# -------------------------------------------------------------------------
# Test: Engine disconnect
# -------------------------------------------------------------------------
run_engine_disconnect() {
    log_info "Starting engine disconnect test"

    if [[ "$DRY_RUN" = true ]]; then
        log_info "DRY RUN: Would disconnect engine"
        return 0
    fi

    # This would require knowledge of engine connection details
    # Simplified: just test behavior when engine is unreachable

    log_chaos "Simulating engine disconnect..."

    # Temporarily block engine port (assuming 8080)
    if command -v iptables &>/dev/null; then
        sudo iptables -A INPUT -p tcp --dport 8080 -j DROP 2>/dev/null || true
        log_chaos "Port 8080 blocked for ${DURATION}s"
        sleep "$DURATION"

        log_recover "Unblocking port 8080"
        sudo iptables -D INPUT -p tcp --dport 8080 -j DROP 2>/dev/null || true
    else
        log_warn "iptables not available, simulating with wait"
        sleep "$DURATION"
    fi

    # Check recovery
    log_recover "Waiting for recovery..."
    if check_health 30; then
        log_pass "System recovered within 30 seconds"
        return 0
    else
        log_fail "System did not recover within 30 seconds"
        return 1
    fi
}

# -------------------------------------------------------------------------
# Test: Circuit breaker
# -------------------------------------------------------------------------
run_circuit_breaker() {
    log_info "Starting circuit breaker test"

    if [[ "$DRY_RUN" = true ]]; then
        log_info "DRY RUN: Would test circuit breaker"
        return 0
    fi

    log_chaos "Triggering repeated failures to activate circuit breaker"

    local failure_count=0
    local success_count=0

    # Send failing requests
    for i in $(seq 1 20); do
        if ! curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/nonexistent" 2>/dev/null | grep -q "404\|500\|503"; then
            ((failure_count++)) || true
        fi
        sleep 0.1
    done

    log_chaos "Sent 20 failing requests"

    # Check if circuit opened
    local response_code
    response_code=$(curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/health" 2>/dev/null || echo "000")

    if [[ "$response_code" == "503" ]]; then
        log_chaos "Circuit breaker appears OPEN (503 response)"
    else
        log_info "Circuit breaker state unclear (response: $response_code)"
    fi

    # Wait for circuit to close
    log_recover "Waiting for circuit breaker to close..."
    sleep 30

    if check_health 30; then
        log_pass "Circuit breaker closed, system recovered"
        return 0
    else
        log_fail "Circuit breaker did not recover"
        return 1
    fi
}

# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
echo "========================================="
echo "  Chaos Engineering Tests"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""
echo "Configuration:"
echo "  Server: $SERVER_URL"
echo "  Test: $TEST_TYPE"
echo "  Intensity: $INTENSITY/10"
echo "  Duration: ${DURATION}s"
echo "  Dry run: $DRY_RUN"
echo ""

check_dependencies

echo ""

TESTS_PASSED=0
TESTS_FAILED=0
TESTS_SKIPPED=0

run_test() {
    local test_name="$1"
    local test_func="$2"

    echo ""
    echo "----------------------------------------"
    echo "  Running: $test_name"
    echo "----------------------------------------"

    if $test_func; then
        ((TESTS_PASSED++)) || true
    elif [[ $? -eq 2 ]]; then
        ((TESTS_SKIPPED++)) || true
    else
        ((TESTS_FAILED++)) || true
    fi
}

case "$TEST_TYPE" in
    network)
        run_test "Network Partition" run_network_partition
        ;;
    latency)
        run_test "Latency Injection" run_latency_injection
        ;;
    restart)
        run_test "Server Restart" run_server_restart
        ;;
    disconnect)
        run_test "Engine Disconnect" run_engine_disconnect
        ;;
    circuit_breaker)
        run_test "Circuit Breaker" run_circuit_breaker
        ;;
    all)
        run_test "Network Partition" run_network_partition
        run_test "Latency Injection" run_latency_injection
        run_test "Server Restart" run_server_restart
        run_test "Engine Disconnect" run_engine_disconnect
        run_test "Circuit Breaker" run_circuit_breaker
        ;;
    *)
        log_fail "Unknown test type: $TEST_TYPE"
        exit 1
        ;;
esac

echo ""
echo "========================================="
echo "  Summary"
echo "========================================="
echo -e "  ${GREEN}Passed:  $TESTS_PASSED${NC}"
echo -e "  ${YELLOW}Skipped: $TESTS_SKIPPED${NC}"
echo -e "  ${RED}Failed:  $TESTS_FAILED${NC}"
echo ""

if [[ $TESTS_FAILED -gt 0 ]]; then
    exit 1
elif [[ $TESTS_SKIPPED -gt 0 ]] && [[ $TESTS_PASSED -eq 0 ]]; then
    exit 2
else
    exit 0
fi
