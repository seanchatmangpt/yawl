#!/usr/bin/env bash
#
# HTTP Client Library for Shell Testing
#
# Provides HTTP request functions using curl with:
# - JSON-RPC 2.0 support
# - Response validation
# - Error handling
# - Debugging output
#
# Usage:
#   source scripts/shell-test/http-client.sh
#   response=$(http_post "http://localhost:8080/api" '{"key":"value"}')

set -euo pipefail

# Colors
if [ "${NO_COLOR:-}" = "1" ] || [ ! -t 1 ]; then
    RED=""
    GREEN=""
    YELLOW=""
    BLUE=""
    NC=""
else
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    NC='\033[0m'
fi

# Default timeout in seconds
HTTP_TIMEOUT="${HTTP_TIMEOUT:-30}"

# Debug mode
HTTP_DEBUG="${HTTP_DEBUG:-false}"

# HTTP GET request
http_get() {
    local url="$1"
    local headers="${2:-}"

    local curl_args=(
        -s
        -S
        --max-time "$HTTP_TIMEOUT"
        -H "Accept: application/json"
    )

    # Add custom headers
    if [ -n "$headers" ]; then
        while IFS= read -r header; do
            [ -n "$header" ] && curl_args+=(-H "$header")
        done <<< "$headers"
    fi

    if [ "$HTTP_DEBUG" = "true" ]; then
        echo -e "${BLUE}[HTTP] GET $url${NC}" >&2
    fi

    curl "${curl_args[@]}" "$url" 2>/dev/null
}

# HTTP POST request
http_post() {
    local url="$1"
    local data="$2"
    local content_type="${3:-application/json}"
    local headers="${4:-}"

    local curl_args=(
        -s
        -S
        --max-time "$HTTP_TIMEOUT"
        -X POST
        -H "Content-Type: $content_type"
        -H "Accept: application/json"
        -d "$data"
    )

    # Add custom headers
    if [ -n "$headers" ]; then
        while IFS= read -r header; do
            [ -n "$header" ] && curl_args+=(-H "$header")
        done <<< "$headers"
    fi

    if [ "$HTTP_DEBUG" = "true" ]; then
        echo -e "${BLUE}[HTTP] POST $url${NC}" >&2
        echo -e "${BLUE}[HTTP] Data: $data${NC}" >&2
    fi

    curl "${curl_args[@]}" "$url" 2>/dev/null
}

# HTTP PUT request
http_put() {
    local url="$1"
    local data="$2"
    local content_type="${3:-application/json}"

    local curl_args=(
        -s
        -S
        --max-time "$HTTP_TIMEOUT"
        -X PUT
        -H "Content-Type: $content_type"
        -H "Accept: application/json"
        -d "$data"
    )

    if [ "$HTTP_DEBUG" = "true" ]; then
        echo -e "${BLUE}[HTTP] PUT $url${NC}" >&2
        echo -e "${BLUE}[HTTP] Data: $data${NC}" >&2
    fi

    curl "${curl_args[@]}" "$url" 2>/dev/null
}

# HTTP DELETE request
http_delete() {
    local url="$1"

    local curl_args=(
        -s
        -S
        --max-time "$HTTP_TIMEOUT"
        -X DELETE
        -H "Accept: application/json"
    )

    if [ "$HTTP_DEBUG" = "true" ]; then
        echo -e "${BLUE}[HTTP] DELETE $url${NC}" >&2
    fi

    curl "${curl_args[@]}" "$url" 2>/dev/null
}

# Get HTTP status code only
http_status() {
    local url="$1"
    local method="${2:-GET}"

    curl -s -o /dev/null -w "%{http_code}" -X "$method" --max-time "$HTTP_TIMEOUT" "$url" 2>/dev/null || echo "000"
}

# Get full response with headers
http_response() {
    local url="$1"
    local method="${2:-GET}"
    local data="${3:-}"

    local curl_args=(
        -s
        -S
        -i
        --max-time "$HTTP_TIMEOUT"
        -X "$method"
    )

    if [ -n "$data" ]; then
        curl_args+=(-H "Content-Type: application/json" -d "$data")
    fi

    curl "${curl_args[@]}" "$url" 2>/dev/null
}

# JSON-RPC 2.0 request
jsonrpc_call() {
    local url="$1"
    local method="$2"
    local params="${3:-{}}"
    local id="${4:-1}"

    local json_data
    json_data=$(cat <<EOF
{
    "jsonrpc": "2.0",
    "method": "$method",
    "params": $params,
    "id": $id
}
EOF
)

    if [ "$HTTP_DEBUG" = "true" ]; then
        echo -e "${BLUE}[JSON-RPC] Calling $method${NC}" >&2
        echo -e "${BLUE}[JSON-RPC] Params: $params${NC}" >&2
    fi

    http_post "$url" "$json_data"
}

# JSON-RPC notification (no id, no response expected)
jsonrpc_notify() {
    local url="$1"
    local method="$2"
    local params="${3:-{}}"

    local json_data
    json_data=$(cat <<EOF
{
    "jsonrpc": "2.0",
    "method": "$method",
    "params": $params
}
EOF
)

    http_post "$url" "$json_data"
}

# Extract JSON field
json_get() {
    local json="$1"
    local field="$2"
    local default="${3:-}"

    local result
    result=$(echo "$json" | jq -r ".$field // empty" 2>/dev/null) || result=""

    if [ -n "$result" ] && [ "$result" != "null" ]; then
        echo "$result"
    else
        echo "$default"
    fi
}

# Extract JSON array element
json_array_get() {
    local json="$1"
    local field="$2"
    local index="$3"

    echo "$json" | jq -r ".$field[$index] // empty" 2>/dev/null || echo ""
}

# Get JSON array length
json_array_length() {
    local json="$1"
    local field="$2"

    echo "$json" | jq -r ".$field | length" 2>/dev/null || echo "0"
}

# Check if JSON is valid
json_is_valid() {
    local json="$1"
    echo "$json" | jq -e . > /dev/null 2>&1
}

# Check if JSON-RPC response has error
jsonrpc_has_error() {
    local json="$1"

    local error
    error=$(echo "$json" | jq -r '.error // empty' 2>/dev/null)

    [ -n "$error" ] && [ "$error" != "null" ]
}

# Get JSON-RPC error message
jsonrpc_error_message() {
    local json="$1"

    echo "$json" | jq -r '.error.message // "Unknown error"' 2>/dev/null
}

# Get JSON-RPC result
jsonrpc_result() {
    local json="$1"

    echo "$json" | jq -r '.result // empty' 2>/dev/null
}

# MCP protocol: Initialize
mcp_initialize() {
    local url="$1"
    local client_name="${2:-yawl-shell-test}"
    local client_version="${3:-1.0.0}"
    local protocol_version="${4:-2024-11-05}"

    local params
    params=$(cat <<EOF
{
    "protocolVersion": "$protocol_version",
    "capabilities": {},
    "clientInfo": {
        "name": "$client_name",
        "version": "$client_version"
    }
}
EOF
)

    jsonrpc_call "$url" "initialize" "$params"
}

# MCP protocol: List tools
mcp_list_tools() {
    local url="$1"
    local cursor="${2:-}"

    local params="{}"
    if [ -n "$cursor" ]; then
        params="{\"cursor\": \"$cursor\"}"
    fi

    jsonrpc_call "$url" "tools/list" "$params"
}

# MCP protocol: Call tool
mcp_call_tool() {
    local url="$1"
    local tool_name="$2"
    local arguments="${3:-{}}"

    local params
    params=$(cat <<EOF
{
    "name": "$tool_name",
    "arguments": $arguments
}
EOF
)

    jsonrpc_call "$url" "tools/call" "$params"
}

# MCP protocol: List resources
mcp_list_resources() {
    local url="$1"
    local cursor="${2:-}"

    local params="{}"
    if [ -n "$cursor" ]; then
        params="{\"cursor\": \"$cursor\"}"
    fi

    jsonrpc_call "$url" "resources/list" "$params"
}

# MCP protocol: Read resource
mcp_read_resource() {
    local url="$1"
    local uri="$2"

    local params
    params=$(cat <<EOF
{
    "uri": "$uri"
}
EOF
)

    jsonrpc_call "$url" "resources/read" "$params"
}

# A2A protocol: Get agent card
a2a_get_agent_card() {
    local base_url="$1"

    http_get "$base_url/.well-known/agent.json"
}

# A2A protocol: Send message
a2a_send_message() {
    local url="$1"
    local message="$2"

    jsonrpc_call "$url" "message/send" "$message"
}

# A2A protocol: Get task
a2a_get_task() {
    local url="$1"
    local task_id="$2"

    local params
    params=$(cat <<EOF
{
    "taskId": "$task_id"
}
EOF
)

    jsonrpc_call "$url" "task/get" "$params"
}

# Pretty print JSON
json_pretty() {
    local json="$1"
    echo "$json" | jq . 2>/dev/null || echo "$json"
}

# Debug: Print HTTP response with formatting
http_debug_response() {
    local response="$1"

    echo -e "${YELLOW}=== HTTP Response ===${NC}"
    echo "$response"
    echo -e "${YELLOW}=====================${NC}"
}
