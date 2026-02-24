#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# mcp-stdio.sh - MCP STDIO Transport Library
#
# Provides functions for communicating with MCP servers via STDIO transport.
# STDIO is the standard transport for MCP servers spawned as child processes.
#
# Protocol Requirements (MCP Spec 2024-11-05):
# - Messages are newline-delimited JSON-RPC 2.0
# - Client writes to server's stdin, reads from server's stdout
# - Server logs to stderr (not mixed with protocol messages)
# - UTF-8 encoding required
# - Graceful shutdown on EOF
#
# Usage: Source this file in other scripts to get STDIO transport functions.
# ==========================================================================

# Ensure mcp-client.sh is loaded for base functions
MCP_STDIO_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${MCP_STDIO_LIB_DIR}/mcp-client.sh" 2>/dev/null || true

# ── STDIO Transport Configuration ──────────────────────────────────────────
MCP_STDIO_TIMEOUT="${MCP_STDIO_TIMEOUT:-10}"
MCP_STDIO_BUFFER_SIZE="${MCP_STDIO_BUFFER_SIZE:-65536}"  # 64KB default
MCP_STDIO_MAX_MESSAGE_SIZE="${MCP_STDIO_MAX_MESSAGE_SIZE:-1048576}"  # 1MB max
MCP_STDIO_SERVER_CMD="${MCP_STDIO_SERVER_CMD:-}"
MCP_STDIO_SERVER_ARGS="${MCP_STDIO_SERVER_ARGS:-}"

# ── Process Management (using files for IPC) ────────────────────────────────
declare -g MCP_STDIO_SERVER_PID=""
declare -g MCP_STDIO_WORK_DIR=""

# ── Logging Functions (extend mcp-client.sh) ────────────────────────────────
log_stdio_debug() {
    [[ "${STDIO_DEBUG:-0}" -eq 1 ]] && echo "[STDIO-DEBUG] $*" >&2 || true
    return 0
}

# ── STDIO Server Process Management ─────────────────────────────────────────

# Spawn an MCP server process with STDIO transport using coproc
# Usage: mcp_stdio_spawn <command> [args...]
# Returns: 0 on success, sets MCP_STDIO_SERVER_PID
mcp_stdio_spawn() {
    local cmd="$1"
    shift
    local args=("$@")

    if [[ -z "$cmd" ]]; then
        log_error "No command specified for STDIO server spawn"
        return 1
    fi

    # Create working directory for this session
    MCP_STDIO_WORK_DIR=$(mktemp -d)
    local input_file="${MCP_STDIO_WORK_DIR}/input"
    local output_file="${MCP_STDIO_WORK_DIR}/output"
    local error_file="${MCP_STDIO_WORK_DIR}/error"

    # Create initial empty files
    touch "$input_file" "$output_file" "$error_file"

    log_stdio_debug "Spawning STDIO server: $cmd ${args[*]}"
    log_stdio_debug "Work dir: $MCP_STDIO_WORK_DIR"

    # Spawn server with file redirections
    # Server reads from input_file (we write to it), writes to output_file and error_file
    {
        # Continuously read from input and process
        tail -f "$input_file" 2>/dev/null | "${cmd}" "${args[@]}" >> "$output_file" 2>> "$error_file"
    } &
    MCP_STDIO_SERVER_PID=$!

    # Give server a moment to initialize
    sleep 0.3

    # Verify process is running
    if ! kill -0 "$MCP_STDIO_SERVER_PID" 2>/dev/null; then
        log_error "STDIO server failed to start"
        mcp_stdio_cleanup
        return 1
    fi

    log_stdio_debug "STDIO server spawned with PID: $MCP_STDIO_SERVER_PID"
    return 0
}

# Alternative spawn using simple echo + wait pattern (more portable)
# Usage: mcp_stdio_spawn_simple <command> [args...]
mcp_stdio_spawn_simple() {
    local cmd="$1"
    shift
    local args=("$@")

    if [[ -z "$cmd" ]]; then
        log_error "No command specified for STDIO server spawn"
        return 1
    fi

    # Create working directory
    MCP_STDIO_WORK_DIR=$(mktemp -d)

    log_stdio_debug "Spawning STDIO server (simple): $cmd ${args[*]}"

    # Start server as background process with piped I/O
    "${cmd}" "${args[@]}" < /dev/null &
    MCP_STDIO_SERVER_PID=$!

    sleep 0.2

    if ! kill -0 "$MCP_STDIO_SERVER_PID" 2>/dev/null; then
        log_error "STDIO server failed to start"
        mcp_stdio_cleanup
        return 1
    fi

    log_stdio_debug "STDIO server spawned with PID: $MCP_STDIO_SERVER_PID"
    return 0
}

# Spawn using preconfigured command
mcp_stdio_spawn_default() {
    if [[ -z "$MCP_STDIO_SERVER_CMD" ]]; then
        log_error "MCP_STDIO_SERVER_CMD not set"
        return 1
    fi

    local args=()
    if [[ -n "$MCP_STDIO_SERVER_ARGS" ]]; then
        # shellcheck disable=SC2206
        args=($MCP_STDIO_SERVER_ARGS)
    fi

    mcp_stdio_spawn "$MCP_STDIO_SERVER_CMD" "${args[@]}"
}

# Gracefully terminate the STDIO server
mcp_stdio_terminate() {
    if [[ -z "$MCP_STDIO_SERVER_PID" ]]; then
        return 0
    fi

    log_stdio_debug "Terminating STDIO server PID: $MCP_STDIO_SERVER_PID"

    # Give server time to shutdown gracefully
    local wait_count=0
    while kill -0 "$MCP_STDIO_SERVER_PID" 2>/dev/null && [[ $wait_count -lt 20 ]]; do
        sleep 0.1
        ((wait_count++))
    done

    # Force kill if still running
    if kill -0 "$MCP_STDIO_SERVER_PID" 2>/dev/null; then
        log_stdio_debug "Force killing STDIO server"
        kill -9 "$MCP_STDIO_SERVER_PID" 2>/dev/null || true
        # Also kill any child processes
        pkill -P "$MCP_STDIO_SERVER_PID" 2>/dev/null || true
    fi

    MCP_STDIO_SERVER_PID=""
    mcp_stdio_cleanup
}

# Cleanup temp files
mcp_stdio_cleanup() {
    if [[ -n "${MCP_STDIO_WORK_DIR:-}" && -d "${MCP_STDIO_WORK_DIR:-}" ]]; then
        rm -rf "${MCP_STDIO_WORK_DIR:-}"
        log_stdio_debug "Cleaned up work directory"
    fi
    MCP_STDIO_WORK_DIR=""
}

# Check if STDIO server is running
mcp_stdio_is_running() {
    [[ -n "$MCP_STDIO_SERVER_PID" ]] && kill -0 "$MCP_STDIO_SERVER_PID" 2>/dev/null
}

# ── Single-Shot Message Exchange (simpler, more reliable) ────────────────────

# Send a single message and get response using echo | server approach
# This is the most reliable way to test STDIO transport
# Usage: mcp_stdio_roundtrip <command> <json_message> [timeout]
mcp_stdio_roundtrip() {
    local cmd="$1"
    local message="$2"
    local timeout="${3:-$MCP_STDIO_TIMEOUT}"

    if [[ -z "$message" ]]; then
        log_error "Empty message cannot be sent"
        return 1
    fi

    # Validate JSON
    if ! echo "$message" | jq . >/dev/null 2>&1; then
        log_error "Invalid JSON message"
        return 1
    fi

    log_stdio_debug "Roundtrip: $message"

    # Use timeout with echo piped to server
    local response
    response=$(echo "$message" | timeout "$timeout" "$cmd" 2>/dev/null) || {
        log_error "Roundtrip failed or timed out"
        return 1
    }

    echo "$response"
    return 0
}

# ── Message Exchange Functions (for spawned server) ──────────────────────────

# Send a JSON-RPC message via STDIO (to spawned server)
# Usage: mcp_stdio_send <json_message>
# Returns: 0 on success
mcp_stdio_send() {
    local message="$1"

    if [[ -z "$message" ]]; then
        log_error "Empty message cannot be sent"
        return 1
    fi

    if [[ -z "$MCP_STDIO_WORK_DIR" ]]; then
        log_error "No server work directory - use mcp_stdio_spawn first"
        return 1
    fi

    local input_file="${MCP_STDIO_WORK_DIR}/input"

    # Validate JSON
    if ! echo "$message" | jq . >/dev/null 2>&1; then
        log_error "Invalid JSON message: ${message:0:100}..."
        return 1
    fi

    log_stdio_debug "Sending: ${message:0:100}..."

    # Append message to input file (server is tail -f this file)
    echo "$message" >> "$input_file" 2>/dev/null || {
        log_error "Failed to write to input"
        return 1
    }

    return 0
}

# Receive a JSON-RPC message via STDIO
# Usage: mcp_stdio_recv [timeout_seconds]
# Outputs: JSON message to stdout
mcp_stdio_recv() {
    local timeout="${1:-$MCP_STDIO_TIMEOUT}"

    if [[ -z "$MCP_STDIO_WORK_DIR" ]]; then
        log_error "No server work directory"
        return 1
    fi

    local output_file="${MCP_STDIO_WORK_DIR}/output"
    local start_time
    start_time=$(date +%s)
    local initial_size
    initial_size=$(wc -c < "$output_file" 2>/dev/null || echo "0")

    while true; do
        # Check timeout
        local current_time
        current_time=$(date +%s)
        if (( current_time - start_time >= timeout )); then
            log_error "STDIO receive timeout after ${timeout}s"
            return 1
        fi

        # Check if new data available
        local current_size
        current_size=$(wc -c < "$output_file" 2>/dev/null || echo "0")

        if [[ $current_size -gt $initial_size ]]; then
            # Read new content
            local content
            content=$(tail -c +$((initial_size + 1)) "$output_file" 2>/dev/null)

            # Check if we have a complete line
            if [[ "$content" == *$'\n'* ]]; then
                # Get first complete line
                local line
                line=$(echo "$content" | head -1)
                log_stdio_debug "Received: ${line:0:100}..."
                echo "$line"
                return 0
            fi
        fi

        # Check if server is still running
        if ! mcp_stdio_is_running; then
            # Try to read any remaining output
            if [[ -s "$output_file" ]]; then
                tail -c +$((initial_size + 1)) "$output_file" | head -1
            fi
            log_error "STDIO server terminated during receive"
            return 1
        fi

        sleep 0.1
    done
}

# Send a message and wait for response
# Usage: mcp_stdio_exchange <json_message> [timeout_seconds]
# Outputs: Response JSON to stdout
mcp_stdio_exchange() {
    local message="$1"
    local timeout="${2:-$MCP_STDIO_TIMEOUT}"

    if ! mcp_stdio_send "$message"; then
        return 1
    fi

    mcp_stdio_recv "$timeout"
}

# ── Stderr Capture Functions ────────────────────────────────────────────────

# Capture stderr output from server
# Usage: mcp_stdio_capture_stderr [timeout_seconds]
# Outputs: Stderr content to stdout
mcp_stdio_capture_stderr() {
    local timeout="${1:-1}"

    if [[ -z "$MCP_STDIO_WORK_DIR" ]]; then
        return 0
    fi

    local error_file="${MCP_STDIO_WORK_DIR}/error"

    if [[ -s "$error_file" ]]; then
        cat "$error_file"
    fi
}

# Read all available stderr (for logging validation)
mcp_stdio_read_stderr_nonblock() {
    if [[ -z "$MCP_STDIO_WORK_DIR" ]]; then
        return 0
    fi

    local error_file="${MCP_STDIO_WORK_DIR}/error"

    if [[ -s "$error_file" ]]; then
        cat "$error_file"
    fi
}

# ── Protocol Helper Functions for STDIO ─────────────────────────────────────

# Send initialize request via STDIO
# Usage: mcp_stdio_initialize [request_id]
# Outputs: Response JSON to stdout
mcp_stdio_initialize() {
    local request_id="${1:-1}"
    local capabilities='{"protocolVersion":"2024-11-05","capabilities":{"tools":{},"resources":{},"prompts":{}},"clientInfo":{"name":"stdio-validator","version":"1.0.0"}}'

    local request='{"jsonrpc":"2.0","id":'${request_id}',"method":"initialize","params":'${capabilities}'}'
    mcp_stdio_exchange "$request" "$MCP_STDIO_TIMEOUT"
}

# Send ping notification via STDIO
mcp_stdio_ping() {
    local notification='{"jsonrpc":"2.0","method":"notifications/ping"}'
    mcp_stdio_send "$notification"
}

# Send tools/list request via STDIO
mcp_stdio_list_tools() {
    local request_id="${1:-2}"
    local request='{"jsonrpc":"2.0","id":'${request_id}',"method":"tools/list","params":{}}'
    mcp_stdio_exchange "$request" "$MCP_STDIO_TIMEOUT"
}

# ── Message Validation Functions ────────────────────────────────────────────

# Validate newline-delimited JSON-RPC format
# Usage: mcp_stdio_validate_framing <message>
# Returns: 0 if valid, 1 otherwise
mcp_stdio_validate_framing() {
    local message="$1"

    # Check for newline termination
    if [[ "${message: -1}" != $'\n' ]]; then
        log_error "Message not newline-terminated"
        return 1
    fi

    # Check for single newline (not multiple messages)
    local line_count
    line_count=$(echo -n "$message" | grep -c '^' || echo "0")
    if [[ $line_count -gt 1 ]]; then
        log_error "Multiple lines in single message"
        return 1
    fi

    return 0
}

# Validate UTF-8 encoding
# Usage: mcp_stdio_validate_utf8 <message>
# Returns: 0 if valid UTF-8, 1 otherwise
mcp_stdio_validate_utf8() {
    local message="$1"

    # Validate UTF-8 encoding
    if ! printf '%s' "$message" | iconv -f utf-8 -t utf-8 >/dev/null 2>&1; then
        log_error "Message is not valid UTF-8"
        return 1
    fi

    return 0
}

# Validate JSON-RPC 2.0 message structure
# Usage: mcp_stdio_validate_jsonrpc <message>
# Returns: 0 if valid, 1 otherwise
mcp_stdio_validate_jsonrpc() {
    local message="$1"

    if ! echo "$message" | jq -e '
        .jsonrpc == "2.0" and
        (if (.result != null or .error != null) then (.id != null) else true end) and
        ((.method != null) or (.result != null) or (.error != null))
    ' >/dev/null 2>&1; then
        log_error "Invalid JSON-RPC 2.0 message structure"
        return 1
    fi

    return 0
}

# Generate a large message for testing (>64KB)
# Usage: mcp_stdio_generate_large_message [size_bytes]
# Outputs: Large JSON message to stdout
mcp_stdio_generate_large_message() {
    local size="${1:-70000}"  # Default 70KB (>64KB)
    local request_id="${2:-999}"

    # Create a JSON message with large padding
    local padding_size=$(( size - 100 ))
    local padding
    padding=$(printf '%0.s x' $(seq 1 "$padding_size") | tr -d ' ')
    printf '{"jsonrpc":"2.0","id":%d,"method":"test/large","params":{"data":"%s"}}\n' \
        "$request_id" "$padding"
}

# Generate binary-safe test data
# Usage: mcp_stdio_generate_binary_test
# Outputs: Base64-encoded binary data
mcp_stdio_generate_binary_test() {
    # Generate test binary data with all byte values
    local encoded
    encoded=$(printf '%b' "$(printf '\\%03o' $(seq 0 255))" | base64 | tr -d '\n')
    printf '{"jsonrpc":"2.0","id":888,"method":"test/binary","params":{"binary":"%s","encoding":"base64"}}\n' \
        "$encoded"
}

# ── Test Helper Functions ───────────────────────────────────────────────────

# Run a complete STDIO test cycle
# Usage: mcp_stdio_test_cycle <test_name> <send_message> <expected_pattern>
# Returns: 0 on success, 1 on failure
mcp_stdio_test_cycle() {
    local test_name="$1"
    local send_message="$2"
    local expected_pattern="$3"
    local timeout="${4:-$MCP_STDIO_TIMEOUT}"

    log_verbose "Running STDIO test: $test_name"

    local response
    response=$(mcp_stdio_exchange "$send_message" "$timeout")

    if [[ -z "$response" ]]; then
        log_error "No response for test: $test_name"
        return 1
    fi

    if [[ -n "$expected_pattern" ]] && ! echo "$response" | grep -qE "$expected_pattern"; then
        log_error "Response doesn't match expected pattern for test: $test_name"
        log_error "Expected: $expected_pattern"
        log_error "Got: $response"
        return 1
    fi

    log_success "$test_name"
    return 0
}

# Get server process statistics
mcp_stdio_get_stats() {
    if [[ -z "$MCP_STDIO_SERVER_PID" ]]; then
        echo '{"error": "no server running"}'
        return 1
    fi

    local pid="$MCP_STDIO_SERVER_PID"
    local stat_file="/proc/${pid}/stat"

    if [[ -f "$stat_file" ]]; then
        # Linux - read from /proc
        local stat_data
        stat_data=$(cat "$stat_file" 2>/dev/null)
        echo "{\"pid\": ${pid}, \"stat\": \"${stat_data}\"}"
    else
        # macOS - use ps
        local ps_data
        ps_data=$(ps -p "$pid" -o pid,ppid,%cpu,%mem,vsz,rss,stat,command 2>/dev/null | tail -1)
        echo "{\"pid\": ${pid}, \"ps\": \"${ps_data}\"}"
    fi
}

# ── Cleanup on Script Exit (caller should set this if needed) ──────────────
# trap mcp_stdio_terminate EXIT 2>/dev/null || true
