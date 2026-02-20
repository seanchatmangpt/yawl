#!/bin/bash

# YAWL Interface B API Helper Functions
# Source this file to use the functions: source api-helpers.sh

set -euo pipefail

# Global variables
SESSION_HANDLE=""
BASE_URL="${ENGINE_IB_URL:-http://localhost:8080/yawl/ib}"
ENGINE_URL="${ENGINE_URL:-http://localhost:8080}"

# Wait for engine to be healthy
wait_for_engine() {
    local url="$1"
    local timeout="${2:-60}"
    local interval=5
    local elapsed=0

    log_info "Waiting for engine at ${url} to be healthy..."

    while [ $elapsed -lt $timeout ]; do
        if curl -s -f "${url}/actuator/health/liveness" > /dev/null; then
            log_info "Engine is healthy"
            return 0
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
        log_info "Waiting... (${elapsed}s/${timeout}s)"
    done

    log_error "Engine not ready after ${timeout} seconds"
    return 1
}

# Connect to Interface B
yawl_connect() {
    log_info "Connecting to Interface B..."

    local response=$(curl -s -S -X POST "${BASE_URL}" \
        -H "Content-Type: application/json" \
        -d '{
            "action": "connect",
            "originator": "validation-suite",
            "externalMessage": "Validation suite connection"
        }')

    if ! echo "$response" | jq -e '.sessionHandle' > /dev/null 2>&1; then
        log_error "Connection failed"
        echo "Error response: $response"
        return 1
    fi

    SESSION_HANDLE=$(echo "$response" | jq -r '.sessionHandle')
    log_info "Connected successfully (session: ${SESSION_HANDLE:0:20}...)"
}

# Disconnect from Interface B
yawl_disconnect() {
    if [ -z "$SESSION_HANDLE" ]; then
        log_warn "No active session to disconnect"
        return 0
    fi

    log_info "Disconnecting session..."
    curl -s -S -X POST "${BASE_URL}" \
        -H "Content-Type: application/json" \
        -d "{
            \"action\": \"disconnect\",
            \"sessionHandle\": \"${SESSION_HANDLE}\"
        }" > /dev/null

    SESSION_HANDLE=""
}

# Upload YAWL specification
yawl_upload_spec() {
    local spec_file="$1"
    local spec_name="$2"

    if [ ! -f "$spec_file" ]; then
        log_error "Spec file not found: $spec_file"
        return 1
    fi

    log_info "Uploading specification: $spec_name"

    local response=$(curl -s -S -X POST "${ENGINE_URL}/yawl/ia/specifications" \
        -H "Content-Type: application/xml" \
        -H "X-Specification-Name: $spec_name" \
        -H "X-Specification-Version: 1.0" \
        -d @"$spec_file")

    if echo "$response" | jq -e '.success' > /dev/null 2>&1; then
        log_info "Specification uploaded successfully"
        return 0
    else
        log_error "Specification upload failed"
        echo "Error: $response"
        return 1
    fi
}

# Launch YAWL case
yawl_launch_case() {
    local spec_uri="$1"

    log_info "Launching case for specification: $spec_uri"

    local response=$(curl -s -S -X POST "${BASE_URL}" \
        -H "Content-Type: application/json" \
        -d "{
            \"action\": \"launchCase\",
            \"sessionHandle\": \"${SESSION_HANDLE}\",
            \"specificationUri\": \"${spec_uri}\",
            \"caseData\": \"<data/>\"
        }")

    if ! echo "$response" | jq -e '.caseId' > /dev/null 2>&1; then
        log_error "Case launch failed"
        echo "Error response: $response"
        return 1
    fi

    local case_id=$(echo "$response" | jq -r '.caseId')
    log_info "Case launched: $case_id"
}

# Get live work items
yawl_get_work_items() {
    log_info "Fetching live work items..."

    local response=$(curl -s -S "${BASE_URL}?action=getLiveItems&sessionHandle=${SESSION_HANDLE}")

    if ! echo "$response" | jq -e '.workItems' > /dev/null 2>&1; then
        log_error "Failed to get work items"
        echo "Error response: $response"
        return 1
    fi

    echo "$response"
}

# Checkout work item
yawl_checkout() {
    local case_id="$1"
    local task_id="$2"

    log_info "Checking out work item: $task_id (case: $case_id)"

    curl -s -S -X POST "${BASE_URL}" \
        -H "Content-Type: application/json" \
        -d "{
            \"action\": \"checkout\",
            \"sessionHandle\": \"${SESSION_HANDLE}\",
            \"caseId\": \"${case_id}\",
            \"taskId\": \"${task_id}\"
        }" > /dev/null
}

# Checkin work item
yawl_checkin() {
    local case_id="$1"
    local task_id="$2"
    local output_data="<data/>"

    log_info "Checking in work item: $task_id"

    curl -s -S -X POST "${BASE_URL}" \
        -H "Content-Type: application/json" \
        -d "{
            \"action\": \"checkin\",
            \"sessionHandle\": \"${SESSION_HANDLE}\",
            \"caseId\": \"${case_id}\",
            \"taskId\": \"${task_id}\",
            \"outputData\": \"${output_data}\"
        }" > /dev/null
}

# Cancel case
yawl_cancel_case() {
    local case_id="$1"

    log_info "Canceling case: $case_id"

    curl -s -S -X POST "${BASE_URL}" \
        -H "Content-Type: application/json" \
        -d "{
            \"action\": \"cancelCase\",
            \"sessionHandle\": \"${SESSION_HANDLE}\",
            \"caseId\": \"${case_id}\"
        }" > /dev/null
}

# Complete all work items in a case
yawl_complete_case() {
    local case_id="$1"

    log_info "Completing case: $case_id"

    while true; do
        local work_items=$(yawl_get_work_items)
        local count=$(echo "$work_items" | jq '.workItems | length')

        if [ "$count" -eq 0 ]; then
            log_info "Case $case_id completed"
            return 0
        fi

        local items=$(echo "$work_items" | jq -c '.workItems[]')
        for item in $items; do
            local task_id=$(echo "$item" | jq -r '.taskId')
            log_info "Completing task: $task_id"
            yawl_checkin "$case_id" "$task_id"
        done

        sleep 2
    done
}

# Get case status
yawl_get_case_status() {
    local case_id="$1"

    log_info "Checking case status: $case_id"

    local response=$(curl -s -S "${BASE_URL}?action=getCase&sessionHandle=${SESSION_HANDLE}&caseId=${case_id}")

    if ! echo "$response" | jq -e '.status' > /dev/null 2>&1; then
        log_error "Failed to get case status"
        return 1
    fi

    local status=$(echo "$response" | jq -r '.status')
    log_info "Case status: $status"
    echo "$status"
}

# Validate case completion
yawl_validate_case() {
    local case_id="$1"
    local expected_status="${2:-complete}"

    log_info "Validating case status: $case_id (expected: $expected_status)"

    local status=$(yawl_get_case_status "$case_id")

    if [ "$status" = "$expected_status" ]; then
        log_info "Case validation passed"
        return 0
    else
        log_error "Case validation failed: expected $expected_status, got $status"
        return 1
    fi
}

# Generate YAWL specification XML (example)
gen_wcp01_sequence() {
    cat << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
  <specification uri="WCP-01-Sequence">
    <name>Sequence Pattern</name>
    <description>WCP-01: Basic Sequence Pattern</description>
    <decomposition id="MainNet" isRootNet="true">
      <processControlElements>
        <inputCondition id="start">
          <flowsInto><nextElementRef id="TaskA"/></flowsInto>
        </inputCondition>
        <task id="TaskA">
          <name>Task A</name>
          <flowsInto><nextElementRef id="TaskB"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <task id="TaskB">
          <name>Task B</name>
          <flowsInto><nextElementRef id="end"/></flowsInto>
          <join code="xor"/><split code="xor"/>
        </task>
        <outputCondition id="end"/>
      </processControlElements>
    </decomposition>
  </specification>
</specificationSet>
EOF
}

# Check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Initialize validation environment
yawl_init_validation() {
    log_info "Initializing validation environment..."

    # Check dependencies
    local deps=("curl" "jq" "docker")
    for dep in "${deps[@]}"; do
        if ! command_exists "$dep"; then
            log_error "Missing dependency: $dep"
            return 1
        fi
    done

    log_info "All dependencies available"
}