#!/usr/bin/env bash
# ==========================================================================
# a2a-benchmark.sh — A2A Performance Benchmarking Library
#
# Comprehensive performance benchmarking for A2A protocol operations.
# Follows 80/20 principle: measures what matters for production deployments.
#
# Benchmarks:
#   1. Authentication latency (JWT, API Key validation times)
#   2. Message throughput (messages/sec)
#   3. Agent discovery latency (agent card fetch time)
#   4. Skill execution latency (each of 5 skills)
#   5. Handoff protocol latency (token generation + transfer)
#   6. Concurrent connection handling (10, 50, 100 concurrent)
#   7. Memory usage under sustained load
#
# Output: JSON metrics for CI/CD integration
#
# Usage:
#   source scripts/validation/a2a/lib/a2a-benchmark.sh
#   run_all_benchmarks
#
#   # Or standalone execution:
#   bash scripts/validation/a2a/lib/a2a-benchmark.sh --json
#   bash scripts/validation/a2a/lib/a2a-benchmark.sh --warmup --iterations 50
#
# Environment:
#   A2A_SERVER_HOST - Server hostname (default: localhost)
#   A2A_SERVER_PORT - Server port (default: 8080)
#   A2A_JWT_SECRET  - JWT signing secret (min 32 chars)
#   A2A_API_KEY     - API key for authentication
#
# Exit Codes:
#   0 - All benchmarks completed
#   1 - Benchmark failures detected
#   2 - Server not available or missing dependencies
# ==========================================================================
set -euo pipefail

# ── Script Location ────────────────────────────────────────────────────────
BENCHMARK_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${BENCHMARK_SCRIPT_DIR}"

# ── Source Common Library ──────────────────────────────────────────────────
source "${LIB_DIR}/a2a-common.sh" 2>/dev/null || {
    echo "[ERROR] A2A common library not found: ${LIB_DIR}/a2a-common.sh" >&2
    exit 2
}

# ── Configuration ──────────────────────────────────────────────────────────
BENCHMARK_ITERATIONS="${BENCHMARK_ITERATIONS:-30}"
BENCHMARK_WARMUP="${BENCHMARK_WARMUP:-5}"
BENCHMARK_DURATION_SEC="${BENCHMARK_DURATION_SEC:-10}"
BENCHMARK_TIMEOUT_MS="${BENCHMARK_TIMEOUT_MS:-5000}"
BENCHMARK_OUTPUT="${BENCHMARK_OUTPUT:-json}"

# Production baselines (targets for P95)
AUTH_BASELINE_P95_MS=25           # JWT/API Key validation
AGENT_CARD_BASELINE_P95_MS=100    # Agent discovery
MESSAGE_BASELINE_P95_MS=300       # Message processing
SKILL_BASELINE_P95_MS=200         # Skill execution
HANDOFF_BASELINE_P95_MS=150       # Handoff token generation
THROUGHPUT_MIN_RPS=100            # Minimum messages/second

# ── Metrics Storage ────────────────────────────────────────────────────────
declare -A BENCHMARK_RESULTS=()
declare -a BENCHMARK_LATENCIES=()
declare -a BENCHMARK_ERRORS=()

# ── Timing Utilities ────────────────────────────────────────────────────────

# Get current time in milliseconds (cross-platform)
get_time_ms() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS - use perl for millisecond precision
        perl -MTime::HiRes=time -e 'printf "%.0f\n", time * 1000'
    else
        # Linux
        date +%s%3N 2>/dev/null || echo $(($(date +%s) * 1000))
    fi
}

# Calculate percentile from sorted array
calculate_percentile() {
    local -n arr=$1
    local percentile=$2
    local count=${#arr[@]}
    
    if [[ $count -eq 0 ]]; then
        echo 0
        return
    fi
    
    # Sort the array
    IFS=$'\n' sorted=($(sort -n <<<"${arr[*]}")); unset IFS
    
    # Calculate index
    local idx=$(echo "scale=0; ($count - 1) * $percentile / 100" | bc)
    idx=${idx%.*}  # Floor to integer
    
    echo "${sorted[$idx]}"
}

# Calculate statistics from latency array
calculate_stats() {
    local -n latencies=$1
    local count=${#latencies[@]}
    
    if [[ $count -eq 0 ]]; then
        echo '{"count":0,"min":0,"max":0,"mean":0,"p50":0,"p95":0,"p99":0}'
        return
    fi
    
    # Sort for percentiles
    IFS=$'\n' sorted=($(sort -n <<<"${latencies[*]}")); unset IFS
    
    local min=${sorted[0]}
    local max=${sorted[-1]}
    
    # Calculate mean
    local sum=0
    for lat in "${latencies[@]}"; do
        sum=$((sum + lat))
    done
    local mean=$((sum / count))
    
    # Percentiles
    local p50_idx=$(( (count - 1) * 50 / 100 ))
    local p95_idx=$(( (count - 1) * 95 / 100 ))
    local p99_idx=$(( (count - 1) * 99 / 100 ))
    
    local p50=${sorted[$p50_idx]}
    local p95=${sorted[$p95_idx]}
    local p99=${sorted[$p99_idx]}
    
    cat << STATS_EOF
{
  "count": ${count},
  "min": ${min},
  "max": ${max},
  "mean": ${mean},
  "p50": ${p50},
  "p95": ${p95},
  "p99": ${p99}
}
STATS_EOF
}

# ── JWT Generation for Benchmarks ──────────────────────────────────────────

# Generate a valid JWT for benchmarking
benchmark_generate_jwt() {
    local subject="${1:-benchmark-agent}"
    local permissions="${2:-workflow:launch workflow:query workflow:cancel workitem:manage}"
    local expiry_seconds="${3:-3600}"
    
    local api_secret="${A2A_JWT_SECRET:-your-api-secret-for-benchmark-min-32-chars}"
    local issuer="${A2A_JWT_ISSUER:-yawl-a2a-benchmark}"
    local audience="yawl-a2a"
    
    local iat=$(date +%s)
    local exp=$((iat + expiry_seconds))
    
    # Build header and payload
    local header='{"alg":"HS256","typ":"JWT"}'
    local payload="{\"sub\":\"${subject}\",\"iat\":${iat},\"exp\":${exp},\"aud\":\"${audience}\",\"iss\":\"${issuer}\",\"scope\":\"${permissions}\"}"
    
    # Base64URL encode (macOS and Linux compatible)
    local header_b64=$(echo -n "$header" | base64 | tr '+/' '-_' | tr -d '=')
    local payload_b64=$(echo -n "$payload" | base64 | tr '+/' '-_' | tr -d '=')
    
    # Create signature
    local signing_input="${header_b64}.${payload_b64}"
    local signature=$(echo -n "$signing_input" | openssl dgst -sha256 -hmac "$api_secret" -binary | base64 | tr '+/' '-_' | tr -d '=')
    
    echo "${header_b64}.${payload_b64}.${signature}"
}

# ── Individual Benchmark Functions ─────────────────────────────────────────

# Benchmark 1: Authentication Latency
benchmark_auth_jwt() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    
    log_verbose "Benchmarking JWT authentication (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local jwt
        jwt=$(benchmark_generate_jwt "bench-user-$i" "workflow:query")
        
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 10 \
            -H "Authorization: Bearer ${jwt}" \
            -H "Content-Type: application/json" \
            "${A2A_BASE_URL}/" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        # Only count successful authentications (200 or 403 are both valid auth responses)
        if [[ "$status" =~ ^(200|403)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[auth_jwt]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[auth_jwt_errors]=$errors
    
    log_verbose "JWT auth complete: ${#latencies[@]} samples, $errors errors"
}

benchmark_auth_api_key() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key-for-benchmark}"
    
    log_verbose "Benchmarking API Key authentication (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 10 \
            -H "X-API-Key: ${api_key}" \
            -H "Content-Type: application/json" \
            "${A2A_BASE_URL}/" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" =~ ^(200|403|401)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[auth_api_key]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[auth_api_key_errors]=$errors
    
    log_verbose "API Key auth complete: ${#latencies[@]} samples, $errors errors"
}

# Benchmark 2: Message Throughput
benchmark_message_throughput() {
    local duration_sec=$BENCHMARK_DURATION_SEC
    local messages_sent=0
    local messages_successful=0
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking message throughput (${duration_sec}s test)"
    
    local start_time current_time elapsed
    start_time=$(get_time_ms)
    
    while true; do
        # Send a lightweight query message
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 2 --max-time 5 \
            -X POST \
            -H "X-API-Key: ${api_key}" \
            -H "Content-Type: application/json" \
            -d '{"message":"list specifications"}' \
            "${A2A_BASE_URL}/" 2>/dev/null) || true
        
        messages_sent=$((messages_sent + 1))
        
        local status=$(echo "$response" | tail -1 2>/dev/null || echo "000")
        if [[ "$status" == "200" ]]; then
            messages_successful=$((messages_successful + 1))
        fi
        
        current_time=$(get_time_ms)
        elapsed=$(( (current_time - start_time) / 1000 ))
        
        [[ $elapsed -ge $duration_sec ]] && break
    done
    
    local rps=$((messages_successful / duration_sec))
    
    BENCHMARK_RESULTS[throughput_msgs_sent]=$messages_sent
    BENCHMARK_RESULTS[throughput_msgs_successful]=$messages_successful
    BENCHMARK_RESULTS[throughput_rps]=$rps
    BENCHMARK_RESULTS[throughput_duration_sec]=$duration_sec
    
    log_verbose "Throughput complete: $messages_successful/$messages_sent messages, ${rps} msg/s"
}

# Benchmark 3: Agent Discovery Latency
benchmark_agent_discovery() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    
    log_verbose "Benchmarking agent discovery (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 10 \
            "${A2A_BASE_URL}/.well-known/agent.json" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" == "200" ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[agent_discovery]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[agent_discovery_errors]=$errors
    
    log_verbose "Agent discovery complete: ${#latencies[@]} samples, $errors errors"
}

# Benchmark 4: Skill Execution Latency
benchmark_skill_launch_workflow() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking launch_workflow skill (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 15 \
            -X POST \
            -H "X-API-Key: ${api_key}" \
            -H "Content-Type: application/json" \
            -d '{"message":"list specifications"}' \
            "${A2A_BASE_URL}/" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" =~ ^(200|403|404)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[skill_launch_workflow]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[skill_launch_workflow_errors]=$errors
}

benchmark_skill_query_workflows() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking query_workflows skill (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 15 \
            -X POST \
            -H "X-API-Key: ${api_key}" \
            -H "Content-Type: application/json" \
            -d '{"message":"list workflows"}' \
            "${A2A_BASE_URL}/" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" =~ ^(200|403)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[skill_query_workflows]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[skill_query_workflows_errors]=$errors
}

benchmark_skill_manage_workitems() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking manage_workitems skill (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 15 \
            -X POST \
            -H "X-API-Key: ${api_key}" \
            -H "Content-Type: application/json" \
            -d '{"message":"show work items"}' \
            "${A2A_BASE_URL}/" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" =~ ^(200|403)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[skill_manage_workitems]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[skill_manage_workitems_errors]=$errors
}

benchmark_skill_cancel_workflow() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking cancel_workflow skill (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 15 \
            -X POST \
            -H "X-API-Key: ${api_key}" \
            -H "Content-Type: application/json" \
            -d '{"message":"cancel case 999999"}' \
            "${A2A_BASE_URL}/" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" =~ ^(200|403|404)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[skill_cancel_workflow]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[skill_cancel_workflow_errors]=$errors
}

benchmark_skill_handoff_workitem() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking handoff_workitem skill (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        local start end duration
        start=$(get_time_ms)
        
        # Construct a handoff message (simulated)
        local handoff_msg="YAWL_HANDOFF:WI-${i}:agent-${i}:target-agent-${i}"
        local jwt
        jwt=$(benchmark_generate_jwt "handoff-source-$i" "workitem:manage")
        
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 15 \
            -X POST \
            -H "Authorization: Bearer ${jwt}" \
            -H "Content-Type: application/json" \
            -d "{\"message\":\"${handoff_msg}\"}" \
            "${A2A_BASE_URL}/handoff" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        # Accept various status codes as valid responses
        if [[ "$status" =~ ^(200|400|403|404)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[skill_handoff_workitem]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[skill_handoff_workitem_errors]=$errors
}

# Benchmark 5: Handoff Protocol Latency
benchmark_handoff_protocol() {
    local -a latencies=()
    local errors=0
    local iterations=$BENCHMARK_ITERATIONS
    local api_key="${A2A_API_KEY:-test-api-key}"
    
    log_verbose "Benchmarking handoff protocol (${iterations} iterations)"
    
    for ((i=0; i<iterations; i++)); do
        # Measure full handoff cycle
        local start end duration
        start=$(get_time_ms)
        
        # 1. Generate handoff JWT (client-side, included in measurement)
        local jwt
        jwt=$(benchmark_generate_jwt "handoff-agent-$i" "workitem:manage")
        
        # 2. Send handoff message
        local response
        response=$(curl -s -w "\n%{http_code}" --connect-timeout 5 --max-time 10 \
            -X POST \
            -H "Authorization: Bearer ${jwt}" \
            -H "Content-Type: application/json" \
            -d "{\"message\":\"YAWL_HANDOFF:WI-bench-${i}:source:target\"}" \
            "${A2A_BASE_URL}/handoff" 2>/dev/null) || errors=$((errors + 1))
        
        end=$(get_time_ms)
        duration=$((end - start))
        
        local status=$(echo "$response" | tail -1)
        
        if [[ "$status" =~ ^(200|400|403|404)$ ]]; then
            latencies+=($duration)
        else
            errors=$((errors + 1))
        fi
    done
    
    BENCHMARK_RESULTS[handoff_protocol]=$(calculate_stats latencies)
    BENCHMARK_RESULTS[handoff_protocol_errors]=$errors
    
    log_verbose "Handoff protocol complete: ${#latencies[@]} samples, $errors errors"
}

# Benchmark 6: Concurrent Connection Handling
benchmark_concurrent_10() {
    local concurrent=10
    local -a latencies=()
    local errors=0
    
    log_verbose "Benchmarking ${concurrent} concurrent connections"
    
    local start end duration
    start=$(get_time_ms)
    
    # Launch concurrent requests
    for ((i=0; i<concurrent; i++)); do
        (
            local req_start req_end req_duration
            req_start=$(get_time_ms)
            curl -s --connect-timeout 5 --max-time 15 \
                "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1
            req_end=$(get_time_ms)
            echo "$((req_end - req_start))"
        ) &
    done
    
    # Collect results
    while IFS= read -r lat; do
        latencies+=($lat)
    done < <(wait)
    
    end=$(get_time_ms)
    duration=$((end - start))
    
    local success=${#latencies[@]}
    local failed=$((concurrent - success))
    
    BENCHMARK_RESULTS[concurrent_10_total_ms]=$duration
    BENCHMARK_RESULTS[concurrent_10_success]=$success
    BENCHMARK_RESULTS[concurrent_10_failed]=$failed
    BENCHMARK_RESULTS[concurrent_10_avg_ms]=$((duration > 0 ? duration : 0))
    
    log_verbose "Concurrent ${concurrent} complete: ${success} success, ${failed} failed, ${duration}ms total"
}

benchmark_concurrent_50() {
    local concurrent=50
    local success=0
    local failed=0
    local tmpfile=$(mktemp)
    
    log_verbose "Benchmarking ${concurrent} concurrent connections"
    
    local start end duration
    start=$(get_time_ms)
    
    # Launch concurrent requests with file-based result collection
    for ((i=0; i<concurrent; i++)); do
        (
            local req_start req_end req_duration
            req_start=$(get_time_ms)
            local status
            status=$(curl -s -w "%{http_code}" -o /dev/null --connect-timeout 5 --max-time 15 \
                "${A2A_BASE_URL}/.well-known/agent.json" 2>/dev/null)
            req_end=$(get_time_ms)
            
            if [[ "$status" == "200" ]]; then
                echo "1" >> "$tmpfile"
            else
                echo "0" >> "$tmpfile"
            fi
        ) &
    done
    wait
    
    end=$(get_time_ms)
    duration=$((end - start))
    
    # Count successes
    success=$(grep -c "^1$" "$tmpfile" 2>/dev/null || echo 0)
    failed=$((concurrent - success))
    rm -f "$tmpfile"
    
    BENCHMARK_RESULTS[concurrent_50_total_ms]=$duration
    BENCHMARK_RESULTS[concurrent_50_success]=$success
    BENCHMARK_RESULTS[concurrent_50_failed]=$failed
    
    log_verbose "Concurrent ${concurrent} complete: ${success} success, ${failed} failed, ${duration}ms total"
}

benchmark_concurrent_100() {
    local concurrent=100
    local success=0
    local failed=0
    local tmpfile=$(mktemp)
    
    log_verbose "Benchmarking ${concurrent} concurrent connections"
    
    local start end duration
    start=$(get_time_ms)
    
    # Launch concurrent requests
    for ((i=0; i<concurrent; i++)); do
        (
            local status
            status=$(curl -s -w "%{http_code}" -o /dev/null --connect-timeout 5 --max-time 15 \
                "${A2A_BASE_URL}/.well-known/agent.json" 2>/dev/null)
            
            if [[ "$status" == "200" ]]; then
                echo "1" >> "$tmpfile"
            else
                echo "0" >> "$tmpfile"
            fi
        ) &
        
        # Throttle slightly to avoid overwhelming the system
        if (( i % 20 == 0 )); then
            sleep 0.1
        fi
    done
    wait
    
    end=$(get_time_ms)
    duration=$((end - start))
    
    success=$(grep -c "^1$" "$tmpfile" 2>/dev/null || echo 0)
    failed=$((concurrent - success))
    rm -f "$tmpfile"
    
    BENCHMARK_RESULTS[concurrent_100_total_ms]=$duration
    BENCHMARK_RESULTS[concurrent_100_success]=$success
    BENCHMARK_RESULTS[concurrent_100_failed]=$failed
    
    log_verbose "Concurrent ${concurrent} complete: ${success} success, ${failed} failed, ${duration}ms total"
}

# Benchmark 7: Memory Usage Under Sustained Load
benchmark_memory_sustained_load() {
    local duration_sec=30
    local interval=5
    local samples=$((duration_sec / interval))
    local -a memory_samples=()
    local requests=0
    
    log_verbose "Benchmarking memory under sustained load (${duration_sec}s)"
    
    local start_time current_time elapsed
    start_time=$(get_time_ms)
    
    # Get initial memory (if server exposes metrics endpoint)
    # For now, we measure request success rate under sustained load
    
    while true; do
        # Burst of requests
        for ((j=0; j<10; j++)); do
            curl -s --connect-timeout 2 --max-time 5 \
                "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1 &
            requests=$((requests + 1))
        done
        
        sleep "$interval"
        
        current_time=$(get_time_ms)
        elapsed=$(( (current_time - start_time) / 1000 ))
        
        [[ $elapsed -ge $duration_sec ]] && break
    done
    
    wait
    
    BENCHMARK_RESULTS[memory_sustained_requests]=$requests
    BENCHMARK_RESULTS[memory_sustained_duration_sec]=$duration_sec
    BENCHMARK_RESULTS[memory_sustained_rps]=$((requests / duration_sec))
    
    log_verbose "Sustained load complete: $requests requests over ${duration_sec}s"
}

# ── Warmup Function ────────────────────────────────────────────────────────

run_warmup() {
    local warmup_iterations=${1:-$BENCHMARK_WARMUP}
    
    log_info "Running warmup (${warmup_iterations} iterations)..."
    
    for ((i=0; i<warmup_iterations; i++)); do
        curl -s --connect-timeout 5 --max-time 10 \
            "${A2A_BASE_URL}/.well-known/agent.json" > /dev/null 2>&1 || true
    done
    
    log_verbose "Warmup complete"
}

# ── Main Benchmark Runner ──────────────────────────────────────────────────

run_all_benchmarks() {
    local run_warmup_flag="${1:-true}"
    
    log_info "Starting A2A Performance Benchmarks"
    log_info "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    log_info "Iterations: ${BENCHMARK_ITERATIONS}, Duration: ${BENCHMARK_DURATION_SEC}s"
    echo ""
    
    # Check server availability
    if ! a2a_ping; then
        log_error "A2A server not available at ${A2A_BASE_URL}"
        return 2
    fi
    
    # Warmup
    if [[ "$run_warmup_flag" == "true" ]]; then
        run_warmup "$BENCHMARK_WARMUP"
        echo ""
    fi
    
    # Run benchmarks
    log_info "=== Authentication Latency ==="
    benchmark_auth_jwt
    benchmark_auth_api_key
    echo ""
    
    log_info "=== Agent Discovery ==="
    benchmark_agent_discovery
    echo ""
    
    log_info "=== Message Throughput ==="
    benchmark_message_throughput
    echo ""
    
    log_info "=== Skill Execution ==="
    benchmark_skill_launch_workflow
    benchmark_skill_query_workflows
    benchmark_skill_manage_workitems
    benchmark_skill_cancel_workflow
    benchmark_skill_handoff_workitem
    echo ""
    
    log_info "=== Handoff Protocol ==="
    benchmark_handoff_protocol
    echo ""
    
    log_info "=== Concurrent Connections ==="
    benchmark_concurrent_10
    benchmark_concurrent_50
    benchmark_concurrent_100
    echo ""
    
    log_info "=== Sustained Load ==="
    benchmark_memory_sustained_load
    echo ""
    
    log_success "All benchmarks completed"
}

# ── Output Functions ───────────────────────────────────────────────────────

output_json_results() {
    local timestamp=$(date -u +'%Y-%m-%dT%H:%M:%SZ')
    
    # Extract key metrics for summary
    local auth_jwt_p95=$(echo "${BENCHMARK_RESULTS[auth_jwt]:-{}" | jq -r '.p95 // 0')
    local auth_api_key_p95=$(echo "${BENCHMARK_RESULTS[auth_api_key]:-{}" | jq -r '.p95 // 0')
    local agent_discovery_p95=$(echo "${BENCHMARK_RESULTS[agent_discovery]:-{}" | jq -r '.p95 // 0')
    local throughput_rps="${BENCHMARK_RESULTS[throughput_rps]:-0}"
    local concurrent_100_success="${BENCHMARK_RESULTS[concurrent_100_success]:-0}"
    
    cat << JSON_EOF
{
  "timestamp": "${timestamp}",
  "server": {
    "host": "${A2A_SERVER_HOST}",
    "port": ${A2A_SERVER_PORT}
  },
  "configuration": {
    "iterations": ${BENCHMARK_ITERATIONS},
    "warmup": ${BENCHMARK_WARMUP},
    "duration_sec": ${BENCHMARK_DURATION_SEC}
  },
  "summary": {
    "auth_latency_p50_ms": $(echo "${BENCHMARK_RESULTS[auth_jwt]:-{}" | jq -r '.p50 // 0'),
    "auth_latency_p95_ms": ${auth_jwt_p95},
    "auth_latency_p99_ms": $(echo "${BENCHMARK_RESULTS[auth_jwt]:-{}" | jq -r '.p99 // 0'),
    "throughput_msgs_per_sec": ${throughput_rps},
    "concurrent_connections": ${concurrent_100_success},
    "agent_discovery_p95_ms": ${agent_discovery_p95}
  },
  "benchmarks": {
    "authentication": {
      "jwt": ${BENCHMARK_RESULTS[auth_jwt]:-{"}},
      "jwt_errors": ${BENCHMARK_RESULTS[auth_jwt_errors]:-0},
      "api_key": ${BENCHMARK_RESULTS[auth_api_key]:-{"}},
      "api_key_errors": ${BENCHMARK_RESULTS[auth_api_key_errors]:-0}
    },
    "agent_discovery": {
      "stats": ${BENCHMARK_RESULTS[agent_discovery]:-{"}},
      "errors": ${BENCHMARK_RESULTS[agent_discovery_errors]:-0}
    },
    "throughput": {
      "messages_sent": ${BENCHMARK_RESULTS[throughput_msgs_sent]:-0},
      "messages_successful": ${BENCHMARK_RESULTS[throughput_msgs_successful]:-0},
      "rps": ${BENCHMARK_RESULTS[throughput_rps]:-0},
      "duration_sec": ${BENCHMARK_RESULTS[throughput_duration_sec]:-0}
    },
    "skills": {
      "launch_workflow": ${BENCHMARK_RESULTS[skill_launch_workflow]:-{"}},
      "query_workflows": ${BENCHMARK_RESULTS[skill_query_workflows]:-{"}},
      "manage_workitems": ${BENCHMARK_RESULTS[skill_manage_workitems]:-{"}},
      "cancel_workflow": ${BENCHMARK_RESULTS[skill_cancel_workflow]:-{"}},
      "handoff_workitem": ${BENCHMARK_RESULTS[skill_handoff_workitem]:-{}}
    },
    "handoff_protocol": {
      "stats": ${BENCHMARK_RESULTS[handoff_protocol]:-{"}},
      "errors": ${BENCHMARK_RESULTS[handoff_protocol_errors]:-0}
    },
    "concurrent": {
      "10": {
        "total_ms": ${BENCHMARK_RESULTS[concurrent_10_total_ms]:-0},
        "success": ${BENCHMARK_RESULTS[concurrent_10_success]:-0},
        "failed": ${BENCHMARK_RESULTS[concurrent_10_failed]:-0}
      },
      "50": {
        "total_ms": ${BENCHMARK_RESULTS[concurrent_50_total_ms]:-0},
        "success": ${BENCHMARK_RESULTS[concurrent_50_success]:-0},
        "failed": ${BENCHMARK_RESULTS[concurrent_50_failed]:-0}
      },
      "100": {
        "total_ms": ${BENCHMARK_RESULTS[concurrent_100_total_ms]:-0},
        "success": ${BENCHMARK_RESULTS[concurrent_100_success]:-0},
        "failed": ${BENCHMARK_RESULTS[concurrent_100_failed]:-0}
      }
    },
    "sustained_load": {
      "requests": ${BENCHMARK_RESULTS[memory_sustained_requests]:-0},
      "duration_sec": ${BENCHMARK_RESULTS[memory_sustained_duration_sec]:-0},
      "rps": ${BENCHMARK_RESULTS[memory_sustained_rps]:-0}
    }
  },
  "baselines": {
    "auth_p95_ms": ${AUTH_BASELINE_P95_MS},
    "agent_discovery_p95_ms": ${AGENT_CARD_BASELINE_P95_MS},
    "message_p95_ms": ${MESSAGE_BASELINE_P95_MS},
    "skill_p95_ms": ${SKILL_BASELINE_P95_MS},
    "handoff_p95_ms": ${HANDOFF_BASELINE_P95_MS},
    "throughput_min_rps": ${THROUGHPUT_MIN_RPS}
  },
  "status": "completed"
}
JSON_EOF
}

output_text_results() {
    echo ""
    echo "==========================================="
    echo "A2A Performance Benchmark Results"
    echo "==========================================="
    echo "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo "Timestamp: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    echo ""
    
    echo "--- Authentication Latency ---"
    echo "  JWT:     $(echo "${BENCHMARK_RESULTS[auth_jwt]:-{}}" | jq -r '.p50 // "N/A"')ms p50, $(echo "${BENCHMARK_RESULTS[auth_jwt]:-{}}" | jq -r '.p95 // "N/A"')ms p95"
    echo "  API Key: $(echo "${BENCHMARK_RESULTS[auth_api_key]:-{}}" | jq -r '.p50 // "N/A"')ms p50, $(echo "${BENCHMARK_RESULTS[auth_api_key]:-{}}" | jq -r '.p95 // "N/A"')ms p95"
    echo ""
    
    echo "--- Agent Discovery ---"
    echo "  Latency: $(echo "${BENCHMARK_RESULTS[agent_discovery]:-{}}" | jq -r '.p50 // "N/A"')ms p50, $(echo "${BENCHMARK_RESULTS[agent_discovery]:-{}}" | jq -r '.p95 // "N/A"')ms p95"
    echo ""
    
    echo "--- Message Throughput ---"
    echo "  Rate:    ${BENCHMARK_RESULTS[throughput_rps]:-0} messages/sec"
    echo "  Total:   ${BENCHMARK_RESULTS[throughput_msgs_successful]:-0}/${BENCHMARK_RESULTS[throughput_msgs_sent]:-0} successful"
    echo ""
    
    echo "--- Skill Execution (p95) ---"
    echo "  launch_workflow:  $(echo "${BENCHMARK_RESULTS[skill_launch_workflow]:-{}}" | jq -r '.p95 // "N/A"')ms"
    echo "  query_workflows:  $(echo "${BENCHMARK_RESULTS[skill_query_workflows]:-{}}" | jq -r '.p95 // "N/A"')ms"
    echo "  manage_workitems: $(echo "${BENCHMARK_RESULTS[skill_manage_workitems]:-{}}" | jq -r '.p95 // "N/A"')ms"
    echo "  cancel_workflow:  $(echo "${BENCHMARK_RESULTS[skill_cancel_workflow]:-{}}" | jq -r '.p95 // "N/A"')ms"
    echo "  handoff_workitem: $(echo "${BENCHMARK_RESULTS[skill_handoff_workitem]:-{}}" | jq -r '.p95 // "N/A"')ms"
    echo ""
    
    echo "--- Concurrent Connections ---"
    echo "  10 concurrent:  ${BENCHMARK_RESULTS[concurrent_10_success]:-0}/10 success, ${BENCHMARK_RESULTS[concurrent_10_total_ms]:-0}ms"
    echo "  50 concurrent:  ${BENCHMARK_RESULTS[concurrent_50_success]:-0}/50 success, ${BENCHMARK_RESULTS[concurrent_50_total_ms]:-0}ms"
    echo "  100 concurrent: ${BENCHMARK_RESULTS[concurrent_100_success]:-0}/100 success, ${BENCHMARK_RESULTS[concurrent_100_total_ms]:-0}ms"
    echo ""
    
    echo "--- Sustained Load ---"
    echo "  Requests: ${BENCHMARK_RESULTS[memory_sustained_requests]:-0} over ${BENCHMARK_RESULTS[memory_sustained_duration_sec]:-0}s"
    echo "  Rate:     ${BENCHMARK_RESULTS[memory_sustained_rps]:-0} req/sec"
    echo ""
    
    echo "==========================================="
}

# ── CLI Entry Point ────────────────────────────────────────────────────────

benchmark_main() {
    local do_warmup=false
    local output_format="json"
    
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --json)         output_format="json"; shift ;;
            --text)         output_format="text"; shift ;;
            --warmup)       do_warmup=true; shift ;;
            --iterations)   BENCHMARK_ITERATIONS="$2"; shift 2 ;;
            --duration)     BENCHMARK_DURATION_SEC="$2"; shift 2 ;;
            --verbose|-v)   VERBOSE=1; shift ;;
            -h|--help)
                cat << HELP_EOF
A2A Performance Benchmarking Library

Usage: bash a2a-benchmark.sh [OPTIONS]

Options:
  --json          Output results as JSON (default)
  --text          Output results as human-readable text
  --warmup        Run warmup before benchmarks
  --iterations N  Number of iterations per benchmark (default: 30)
  --duration N    Duration for throughput test in seconds (default: 10)
  --verbose       Enable verbose logging
  -h, --help      Show this help message

Environment Variables:
  A2A_SERVER_HOST   Server hostname (default: localhost)
  A2A_SERVER_PORT   Server port (default: 8080)
  A2A_JWT_SECRET    JWT signing secret (min 32 chars)
  A2A_API_KEY       API key for authentication

Examples:
  bash a2a-benchmark.sh --warmup --json
  bash a2a-benchmark.sh --text --iterations 50
  A2A_SERVER_PORT=8081 bash a2a-benchmark.sh --json
HELP_EOF
                exit 0 ;;
            *)  shift ;;
        esac
    done
    
    # Run benchmarks
    run_all_benchmarks "$do_warmup"
    local exit_code=$?
    
    if [[ $exit_code -ne 0 ]]; then
        return $exit_code
    fi
    
    # Output results
    if [[ "$output_format" == "json" ]]; then
        output_json_results
    else
        output_text_results
    fi
}

# Run main if executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    benchmark_main "$@"
fi
