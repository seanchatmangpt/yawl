#!/usr/bin/env bash
# ==========================================================================
# mcp-client.sh — MCP JSON-RPC Client Library
#
# Provides functions for communicating with MCP servers via JSON-RPC 2.0
# over STDIO transport. Compatible with YAWL MCP server.
#
# Usage: Source this file in other scripts to get MCP client functions.
# ==========================================================================

# ── Configuration ────────────────────────────────────────────────────────
MCP_SERVER_HOST="${MCP_SERVER_HOST:-localhost}"
MCP_SERVER_PORT="${MCP_SERVER_PORT:-9090}"
MCP_TIMEOUT="${MCP_TIMEOUT:-30}"

# ── Logging Functions ──────────────────────────────────────────────────────
log_verbose() {
    [[ "${VERBOSE:-0}" -eq 1 ]] && echo "[VERBOSE] $*" >&2 || true
    return 0
}

log_error() {
    echo "[ERROR] $*" >&2
}

# ── JSON-RPC Helper Functions ─────────────────────────────────────────────
jsonrpc_request() {
    local method="$1"
    local params="$2"
    local id="${3:-$(date +%s)}"

    local request='{"jsonrpc":"2.0","id":'${id}',"method":"'${method}'","params":'${params}'}'

    log_verbose "Sending JSON-RPC request: $request"
    echo "$request"
}

jsonrpc_response() {
    local response="$1"
    local expected_id="$2"

    log_verbose "Received response: $response"

    # Extract JSON-RPC ID
    local actual_id
    actual_id=$(echo "$response" | sed -n 's/.*"id":\s*\([0-9]*\).*/\1/p' || echo "")

    if [[ "$actual_id" != "$expected_id" ]]; then
        log_error "Response ID mismatch. Expected: $expected_id, Got: $actual_id"
        return 1
    fi

    # Extract error if present
    local error
    error=$(echo "$response" | sed -n 's/.*"error":\s*{\s*"code":\s*\([^,]*\),\s*"message":\s*"\([^"]*\)".*/\1:\2/p' || echo "")

    if [[ -n "$error" ]]; then
        log_error "JSON-RPC Error: $error"
        return 1
    fi

    return 0
}

# ── MCP Protocol Functions ───────────────────────────────────────────────
mcp_initialize() {
    local request_id="${1:-1}"
    local capabilities='{"tools":{},"resources":{},"prompts":{}}'

    jsonrpc_request "initialize" "$capabilities" "$request_id"
}

mcp_ping() {
    # Simple ping test
    local response
    response=$(echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}' | \
                timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null || echo "")

    [[ -n "$response" ]] && [[ "$response" != *"error"* ]] && return 0
    return 1
}

# ── MCP Tool Functions ───────────────────────────────────────────────────
mcp_list_tools() {
    local request_id="${1:-2}"
    jsonrpc_request "tools/list" "{}" "$request_id"
}

mcp_call_tool() {
    local tool_name="$1"
    local arguments="$2"
    local request_id="${3:-3}"

    local params='{"name":"'${tool_name}'","arguments":'${arguments}'}'
    jsonrpc_request "tools/call" "$params" "$request_id"
}

# ── MCP Resource Functions ──────────────────────────────────────────────
mcp_list_resources() {
    local request_id="${1:-4}"
    jsonrpc_request "resources/list" "{}" "$request_id"
}

mcp_read_resource() {
    local uri="$1"
    local request_id="${2:-5}"

    local params='{"uri":"'${uri}'"}'
    jsonrpc_request "resources/read" "$params" "$request_id"
}

# ── MCP Prompt Functions ──────────────────────────────────────────────────
mcp_list_prompts() {
    local request_id="${1:-6}"
    jsonrpc_request "prompts/list" "{}" "$request_id"
}

mcp_get_prompt() {
    local name="$1"
    local arguments="$2"
    local request_id="${3:-7}"

    local params='{"name":"'${name}'","arguments":'${arguments}'}'
    jsonrpc_request "prompts/get" "$params" "$request_id"
}

# ── MCP Completion Functions ──────────────────────────────────────────────
mcp_complete_resource() {
    local uri="$1"
    local arguments="$2"
    local request_id="${3:-8}"

    local params='{"uri":"'${uri}'","partial":"'${arguments}'"}'
    jsonrpc_request "resources/complete" "$params" "$request_id"
}

# ── Test Execution Functions ──────────────────────────────────────────────
run_mcp_test() {
    local test_name="$1"
    local result=1
    local response

    case "$test_name" in
        "initialize_request")
            response=$(mcp_initialize)
            if jsonrpc_response "$response" "1"; then
                result=0
            fi
            ;;
        "protocol_version_check")
            response=$(mcp_initialize | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"jsonrpc":"2.0"'; then
                result=0
            fi
            ;;
        "server_info_check")
            response=$(mcp_initialize | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"yawl-mcp-server"'; then
                result=0
            fi
            ;;
        "capabilities_check")
            response=$(mcp_initialize | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"capabilities"'; then
                result=0
            fi
            ;;
        "tools_capability")
            response=$(mcp_list_tools | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"tools"'; then
                result=0
            fi
            ;;
        "resources_capability")
            response=$(mcp_list_resources | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"resources"'; then
                result=0
            fi
            ;;
        "prompts_capability")
            response=$(mcp_list_prompts | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"prompts"'; then
                result=0
            fi
            ;;
        *)
            log_error "Unknown test: $test_name"
            ;;
    esac

    if [[ $result -eq 0 ]]; then
        log_success "$test_name"
    else
        log_error "$test_name"
    fi

    return $result
}

run_mcp_tool_test() {
    local tool_name="$1"
    local result=1
    local response

    # First check if tool exists
    response=$(mcp_list_tools | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
    if ! echo "$response" | grep -q "\"name\":\"${tool_name}\""; then
        log_error "Tool not found: $tool_name"
        return 1
    fi

    # Test tool with empty arguments
    local empty_args='{}'
    response=$(mcp_call_tool "$tool_name" "$empty_args" | \
               timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)

    if jsonrpc_response "$response" "3"; then
        result=0
    fi

    return $result
}

run_mcp_resource_test() {
    local test_name="$1"
    local result=1
    local response

    case "$test_name" in
        "resources_list")
            response=$(mcp_list_resources | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"resources"' && echo "$response" | grep -q 'uri'; then
                result=0
            fi
            ;;
        "specifications_resource")
            response=$(mcp_read_resource "yawl://specifications" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"'; then
                result=0
            fi
            ;;
        "cases_resource")
            response=$(mcp_read_resource "yawl://cases" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"'; then
                result=0
            fi
            ;;
        "workitems_resource")
            response=$(mcp_read_resource "yawl://workitems" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"'; then
                result=0
            fi
            ;;
        "templates_declaration")
            response=$(mcp_list_resources | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if echo "$response" | grep -q '"templates"'; then
                result=0
            fi
            ;;
        "template_expansion")
            response=$(mcp_complete_resource "yawl://cases/{caseId}" "" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"'; then
                result=0
            fi
            ;;
        *)
            log_error "Unknown resource test: $test_name"
            ;;
    esac

    if [[ $result -eq 0 ]]; then
        log_success "$test_name"
    else
        log_error "$test_name"
    fi

    return $result
}

run_mcp_prompt_test() {
    local prompt_name="$1"
    local result=1
    local response

    # Check if prompt exists
    response=$(mcp_list_prompts | timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
    if ! echo "$response" | grep -q "\"name\":\"${prompt_name}\""; then
        log_error "Prompt not found: $prompt_name"
        return 1
    fi

    # Get prompt
    local empty_args='{}'
    response=$(mcp_get_prompt "$prompt_name" "$empty_args" | \
               timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)

    if jsonrpc_response "$response" "7"; then
        result=0
    fi

    return $result
}

run_mcp_completion_test() {
    local test_name="$1"
    local result=1
    local response

    case "$test_name" in
        "workflow_analysis_completion")
            response=$(mcp_complete_resource "yawl://cases/{caseId}" "" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"' && echo "$response" | grep -q '"suggestions"'; then
                result=0
            fi
            ;;
        "task_completion_guide_completion")
            response=$(mcp_complete_resource "yawl://workitems/{workItemId}" "" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"' && echo "$response" | grep -q '"suggestions"'; then
                result=0
            fi
            ;;
        "cases_resource_completion")
            response=$(mcp_complete_resource "yawl://cases/" "" | \
                      timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null)
            if ! echo "$response" | grep -q '"error"' && echo "$response" | grep -q '"suggestions"'; then
                result=0
            fi
            ;;
        *)
            log_error "Unknown completion test: $test_name"
            ;;
    esac

    if [[ $result -eq 0 ]]; then
        log_success "$test_name"
    else
        log_error "$test_name"
    fi

    return $result
}

run_mcp_error_test() {
    local test_name="$1"
    local result=1
    local response

    case "$test_name" in
        "invalid_json_error")
            # Send malformed JSON
            echo '{"jsonrpc":"2.0","id":1,"method":"tools/call"' | \
            timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null | \
            grep -q '"error"' && result=0
            ;;
        "invalid_method_error")
            # Call non-existent method
            echo '{"jsonrpc":"2.0","id":1,"method":"invalid_method","params":{}}' | \
            timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null | \
            grep -q '"error"' && result=0
            ;;
        "missing_params_error")
            # Call tool without required parameters
            echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"yawl_launch_case"}}' | \
            timeout ${MCP_TIMEOUT} nc -w ${MCP_TIMEOUT} ${MCP_SERVER_HOST} ${MCP_SERVER_PORT} 2>/dev/null | \
            grep -q '"error"' && result=0
            ;;
        *)
            log_error "Unknown error test: $test_name"
            ;;
    esac

    if [[ $result -eq 0 ]]; then
        log_success "$test_name (error handling)"
    else
        log_error "$test_name (error handling)"
    fi

    return $result
}