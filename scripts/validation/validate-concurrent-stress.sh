#!/usr/bin/env bash
# ==========================================================================
# validate-concurrent-stress.sh - Stress test concurrent A2A/MCP operations
#
# Usage:
#   bash scripts/validation/validate-concurrent-stress.sh [OPTIONS]
#
# Options:
#   --agents N         Number of concurrent agents (default: 100)
#   --duration N       Test duration in seconds (default: 60)
#   --server URL       A2A server URL (default: http://localhost:8080)
#   --scenario TYPE    Test scenario (cascade|fanout|fanin|all)
#   --verbose          Show detailed output
#   --help             Show this help
#
# Scenarios:
#   cascade  - Handoff chain A→B→C→...→J
#   fanout   - Single source → 100 targets
#   fanin    - 100 sources → single target
#   all      - Run all scenarios
#
# Exit codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Performance below threshold
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
NUM_AGENTS=100
DURATION=60
SERVER_URL="http://localhost:8080"
SCENARIO="all"
VERBOSE=false

# Results
TOTAL_REQUESTS=0
SUCCESSFUL_REQUESTS=0
FAILED_REQUESTS=0
DEADLOCKS_DETECTED=0

# -------------------------------------------------------------------------
# Parse arguments
# -------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --agents)
            NUM_AGENTS="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --server)
            SERVER_URL="$2"
            shift 2
            ;;
        --scenario)
            SCENARIO="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --help)
            cat << EOF
A2A/MCP Concurrent Stress Test

Tests system under high concurrency with multiple agent scenarios.

Usage: bash scripts/validation/validate-concurrent-stress.sh [OPTIONS]

Options:
  --agents N         Number of concurrent agents (default: 100)
  --duration N       Test duration in seconds (default: 60)
  --server URL       A2A server URL (default: http://localhost:8080)
  --scenario TYPE    Test scenario: cascade, fanout, fanin, all
  --verbose          Show detailed output
  --help             Show this help

Scenarios:
  cascade  - Handoff chain A→B→C→...→J
  fanout   - Single source → 100 targets
  fanin    - 100 sources → single target
  all      - Run all scenarios

Exit codes:
  0 - All tests passed
  1 - One or more tests failed
  2 - Performance below threshold
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

log_pass() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

log_fail() {
    echo -e "${RED}[FAIL]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_metric() {
    echo -e "${CYAN}[METRIC]${NC} $1"
}

# -------------------------------------------------------------------------
# Helper functions
# -------------------------------------------------------------------------
send_a2a_request() {
    local agent_id="$1"
    local method="$2"
    local params="$3"

    local response
    response=$(curl -s -w "\n%{http_code}" \
        -H "Content-Type: application/json" \
        -d "{\"jsonrpc\":\"2.0\",\"method\":\"${method}\",\"id\":\"stress-${agent_id}\",\"params\":${params}}" \
        "${SERVER_URL}/rpc" 2>/dev/null || echo -e "\n000")

    local code
    code=$(echo "$response" | tail -1)

    ((TOTAL_REQUESTS++)) || true

    if [[ "$code" == "200" ]]; then
        ((SUCCESSFUL_REQUESTS++)) || true
        return 0
    else
        ((FAILED_REQUESTS++)) || true
        return 1
    fi
}

# -------------------------------------------------------------------------
# Scenario: Cascade handoff (A→B→C→...→J)
# -------------------------------------------------------------------------
run_cascade_scenario() {
    log_info "Running cascade scenario (10-agent handoff chain)"

    local chain_length=10
    local iterations=$((NUM_AGENTS / chain_length))
    local pids=()

    for ((i=0; i<iterations; i++)); do
        (
            local chain_id="chain-$i"
            for ((j=0; j<chain_length; j++)); do
                local agent_id="${chain_id}-agent-$j"
                local next_agent="${chain_id}-agent-$(((j+1) % chain_length))"

                send_a2a_request "$agent_id" "skills/handoff_workitem" \
                    "{\"work_item_id\":\"wi-${agent_id}\",\"target_agent\":\"${next_agent}\"}" || true

                sleep 0.01
            done
        ) &
        pids+=($!)
    done

    # Wait for all chains
    for pid in "${pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done

    log_pass "Cascade scenario completed"
}

# -------------------------------------------------------------------------
# Scenario: Fan-out (1→N)
# -------------------------------------------------------------------------
run_fanout_scenario() {
    log_info "Running fanout scenario (1 source → $NUM_AGENTS targets)"

    local source_agent="source-$$"
    local pids=()

    # Source sends to all targets concurrently
    for ((i=0; i<NUM_AGENTS; i++)); do
        (
            local target_agent="target-$i"
            send_a2a_request "${source_agent}" "skills/handoff_workitem" \
                "{\"work_item_id\":\"wi-${source_agent}\",\"target_agent\":\"${target_agent}\"}"
        ) &
        pids+=($!)

        # Throttle to avoid overwhelming system
        if (( i % 20 == 0 )); then
            sleep 0.1
        fi
    done

    # Wait for all
    for pid in "${pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done

    log_pass "Fanout scenario completed"
}

# -------------------------------------------------------------------------
# Scenario: Fan-in (N→1)
# -------------------------------------------------------------------------
run_fanin_scenario() {
    log_info "Running fanin scenario ($NUM_AGENTS sources → 1 target)"

    local target_agent="target-$$"
    local pids=()

    # All sources send to single target
    for ((i=0; i<NUM_AGENTS; i++)); do
        (
            local source_agent="source-$i"
            send_a2a_request "${source_agent}" "skills/handoff_workitem" \
                "{\"work_item_id\":\"wi-${source_agent}\",\"target_agent\":\"${target_agent}\"}"
        ) &
        pids+=($!)

        # Throttle
        if (( i % 20 == 0 )); then
            sleep 0.1
        fi
    done

    # Wait for all
    for pid in "${pids[@]}"; do
        wait "$pid" 2>/dev/null || true
    done

    log_pass "Fanin scenario completed"
}

# -------------------------------------------------------------------------
# Deadlock detection
# -------------------------------------------------------------------------
check_deadlocks() {
    log_info "Checking for deadlocks..."

    # Check if any processes are stuck (simplified check)
    local stuck_processes
    stuck_processes=$(ps aux | grep -c "defunct" 2>/dev/null || echo "0")

    if [[ "$stuck_processes" -gt 0 ]]; then
        DEADLOCKS_DETECTED=$stuck_processes
        log_warn "Found $stuck_processes defunct processes (potential deadlocks)"
        return 1
    fi

    log_pass "No deadlocks detected"
    return 0
}

# -------------------------------------------------------------------------
# Performance metrics
# -------------------------------------------------------------------------
calculate_metrics() {
    local duration=$1

    if [[ $duration -eq 0 ]]; then
        duration=1
    fi

    local rps=$((TOTAL_REQUESTS / duration))
    local success_rate=0

    if [[ $TOTAL_REQUESTS -gt 0 ]]; then
        success_rate=$((SUCCESSFUL_REQUESTS * 100 / TOTAL_REQUESTS))
    fi

    echo ""
    log_metric "Requests/sec: ${rps}"
    log_metric "Success rate: ${success_rate}%"
    log_metric "Total requests: ${TOTAL_REQUESTS}"
    log_metric "Successful: ${SUCCESSFUL_REQUESTS}"
    log_metric "Failed: ${FAILED_REQUESTS}"
    log_metric "Deadlocks: ${DEADLOCKS_DETECTED}"

    # Performance thresholds
    if [[ $success_rate -lt 95 ]]; then
        log_warn "Success rate below 95% threshold"
        return 2
    fi

    if [[ $DEADLOCKS_DETECTED -gt 0 ]]; then
        log_fail "Deadlocks detected"
        return 1
    fi

    return 0
}

# -------------------------------------------------------------------------
# Main
# -------------------------------------------------------------------------
echo "========================================="
echo "  A2A/MCP Concurrent Stress Test"
echo "  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "========================================="
echo ""
echo "Configuration:"
echo "  Agents: $NUM_AGENTS"
echo "  Duration: ${DURATION}s"
echo "  Server: $SERVER_URL"
echo "  Scenario: $SCENARIO"
echo ""

START_TIME=$(date +%s)

# Check server availability
if ! curl -s -o /dev/null -w "%{http_code}" "${SERVER_URL}/health" 2>/dev/null | grep -q "200"; then
    log_warn "Server not responding at ${SERVER_URL}"
    log_info "Proceeding with tests anyway..."
fi

echo ""

# Run scenarios
case "$SCENARIO" in
    cascade)
        run_cascade_scenario
        ;;
    fanout)
        run_fanout_scenario
        ;;
    fanin)
        run_fanin_scenario
        ;;
    all)
        run_cascade_scenario
        echo ""
        run_fanout_scenario
        echo ""
        run_fanin_scenario
        ;;
    *)
        log_fail "Unknown scenario: $SCENARIO"
        exit 1
        ;;
esac

END_TIME=$(date +%s)
ACTUAL_DURATION=$((END_TIME - START_TIME))

echo ""

# Check for deadlocks
check_deadlocks || true

echo ""
echo "========================================="
echo "  Results"
echo "========================================="

calculate_metrics "$ACTUAL_DURATION"
RESULT=$?

echo ""

if [[ $RESULT -eq 0 ]]; then
    echo -e "${GREEN}All stress tests passed${NC}"
    exit 0
elif [[ $RESULT -eq 2 ]]; then
    echo -e "${YELLOW}Performance below threshold${NC}"
    exit 2
else
    echo -e "${RED}Stress tests failed${NC}"
    exit 1
fi
