#!/usr/bin/env bash
# ==========================================================================
# workflow-common.sh — E2E Workflow Test Common Functions
#
# Provides shared utilities for testing MCP and A2A workflow integrations.
#
# Usage: Source this file in other scripts to get workflow test functions.
# ==========================================================================

# ── Configuration ────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# Server configuration
YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080}"
MCP_SERVER_URL="${MCP_SERVER_URL:-http://localhost:9090}"
A2A_SERVER_URL="${A2A_SERVER_URL:-http://localhost:8081}"

# Test configuration
TEST_TIMEOUT="${TEST_TIMEOUT:-60}"
TEMP_DIR="${TEMP_DIR:-$(mktemp -d)}"
SPEC_FILE="${SPEC_FILE:-${SCRIPT_DIR}/../specs/minimal-test.xml}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'
BOLD='\033[1m'

# ── Logging Functions ────────────────────────────────────────────────────
log_verbose() {
    [[ "${VERBOSE:-0}" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

log_info() {
    echo -e "${CYAN}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[OK]${NC} $*"
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_section() {
    echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}"
}

# ── Cleanup Function ─────────────────────────────────────────────────────
cleanup() {
    log_verbose "Cleaning up temporary files..."
    rm -rf "${TEMP_DIR}"
}

trap cleanup EXIT

# ── YAWL Engine Functions ─────────────────────────────────────────────────
yawl_upload_specification() {
    local spec_file="$1"
    local case_id=""

    log_verbose "Uploading specification: $spec_file"

    # Check if spec file exists
    if [[ ! -f "$spec_file" ]]; then
        log_error "Specification file not found: $spec_file"
        return 1
    fi

    # Create multipart/form-data upload
    local boundary="----WebKitFormBoundary$(date +%s)"
    local response_file="${TEMP_DIR}/upload-response.json"

    cat > "$response_file" <<EOF
--${boundary}
Content-Disposition: form-data; name="file"; filename="$(basename "$spec_file")"
Content-Type: application/xml

$(cat "$spec_file")
--${boundary}--
EOF

    # Send upload request
    if curl -s -X POST \
        -H "Content-Type: multipart/form-data; boundary=$boundary" \
        --data-binary @"$response_file" \
        "${YAWL_ENGINE_URL}/yawl/specification" > "${TEMP_DIR}/upload-result.json" 2>/dev/null; then

        # Extract case ID from response
        case_id=$(grep -o '"caseId":"[^"]*"' "${TEMP_DIR}/upload-result.json" | cut -d'"' -f4)

        if [[ -n "$case_id" ]]; then
            log_success "Specification uploaded successfully. Case ID: $case_id"
            echo "$case_id"
            return 0
        fi
    fi

    log_error "Failed to upload specification"
    return 1
}

yawl_get_case_status() {
    local case_id="$1"
    local status=""

    log_verbose "Getting status for case: $case_id"

    local response_file="${TEMP_DIR}/status-response.json"
    if curl -s -X GET "${YAWL_ENGINE_URL}/yawl/cases/${case_id}/status" > "$response_file" 2>/dev/null; then
        status=$(grep -o '"status":"[^"]*"' "$response_file" | cut -d'"' -f4)
        if [[ -n "$status" ]]; then
            echo "$status"
            return 0
        fi
    fi

    log_error "Failed to get case status for $case_id"
    return 1
}

yawl_get_work_items() {
    local case_id="$1"

    log_verbose "Getting work items for case: $case_id"

    local response_file="${TEMP_DIR}/workitems.json"
    if curl -s -X GET "${YAWL_ENGINE_URL}/yawl/workitems?case=${case_id}" > "$response_file" 2>/dev/null; then
        # Extract work item IDs
        grep -o '"id":"[^"]*"' "$response_file" | cut -d'"' -f4
        return 0
    fi

    log_error "Failed to get work items for case $case_id"
    return 1
}

yawl_checkout_work_item() {
    local work_item_id="$1"

    log_verbose "Checking out work item: $work_item_id"

    local response_file="${TEMP_DIR}/checkout-result.json"
    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"participant":"test-user","data":{}}' \
        "${YAWL_ENGINE_URL}/yawl/workitems/${work_item_id}/checkout" > "$response_file" 2>/dev/null; then

        if grep -q '"success":true' "$response_file"; then
            log_success "Work item $work_item_id checked out successfully"
            return 0
        fi
    fi

    log_error "Failed to checkout work item $work_item_id"
    return 1
}

yawl_checkin_work_item() {
    local work_item_id="$1"

    log_verbose "Checking in work item: $work_item_id"

    local response_file="${TEMP_DIR}/checkin-result.json"
    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -d '{"data":"","status":"completed"}' \
        "${YAWL_ENGINE_URL}/yawl/workitems/${work_item_id}/checkin" > "$response_file" 2>/dev/null; then

        if grep -q '"success":true' "$response_file"; then
            log_success "Work item $work_item_id checked in successfully"
            return 0
        fi
    fi

    log_error "Failed to checkin work item $work_item_id"
    return 1
}

# ── MCP Functions ───────────────────────────────────────────────────────
mcp_upload_specification() {
    local spec_file="$1"
    local case_id=""

    log_verbose "MCP: Uploading specification: $spec_file"

    # Convert XML to JSON for MCP
    local json_spec=$(xml_to_json "$spec_file")

    if [[ -z "$json_spec" ]]; then
        log_error "Failed to convert XML specification to JSON"
        return 1
    fi

    # MCP call to upload specification
    local request='{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"yawl_upload_specification","arguments":{"specification":'"$json_spec"'}}}'

    echo "$request" | timeout ${TEST_TIMEOUT} nc -w ${TEST_TIMEOUT} localhost 9090 > "${TEMP_DIR}/mcp-upload-response.json" 2>/dev/null

    if [[ -s "${TEMP_DIR}/mcp-upload-response.json" ]]; then
        case_id=$(grep -o '"result":{"data":{"caseId":"[^"]*"' "${TEMP_DIR}/mcp-upload-response.json" | cut -d'"' -f8)

        if [[ -n "$case_id" ]]; then
            log_success "MCP: Specification uploaded successfully. Case ID: $case_id"
            echo "$case_id"
            return 0
        fi
    fi

    log_error "MCP: Failed to upload specification"
    return 1
}

mcp_get_case_status() {
    local case_id="$1"

    log_verbose "MCP: Getting status for case: $case_id"

    local request='{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"yawl_get_case_status","arguments":{"caseId":"'"$case_id"'"}}}'

    echo "$request" | timeout ${TEST_TIMEOUT} nc -w ${TEST_TIMEOUT} localhost 9090 > "${TEMP_DIR}/mcp-status-response.json" 2>/dev/null

    if [[ -s "${TEMP_DIR}/mcp-status-response.json" ]]; then
        local status=$(grep -o '"result":{"data":{"status":"[^"]*"' "${TEMP_DIR}/mcp-status-response.json" | cut -d'"' -f8)

        if [[ -n "$status" ]]; then
            echo "$status"
            return 0
        fi
    fi

    log_error "MCP: Failed to get case status for $case_id"
    return 1
}

# ── A2A Functions ────────────────────────────────────────────────────────
a2a_launch_workflow() {
    local spec_name="$1"

    log_verbose "A2A: Launching workflow: $spec_name"

    # Generate JWT for authentication
    local jwt=$(generate_jwt "test-user" '"workflow:launch"' "yawl-a2a")

    local request='{"message":"launch_workflow","params":{"specification":"'"$spec_name"'","initiator":"test-user"}}'

    if curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $jwt" \
        -d "$request" \
        "${A2A_SERVER_URL}/" > "${TEMP_DIR}/a2a-launch-response.json" 2>/dev/null; then

        local case_id=$(grep -o '"caseId":"[^"]*"' "${TEMP_DIR}/a2a-launch-response.json" | cut -d'"' -f4)

        if [[ -n "$case_id" ]]; then
            log_success "A2A: Workflow launched successfully. Case ID: $case_id"
            echo "$case_id"
            return 0
        fi
    fi

    log_error "A2A: Failed to launch workflow"
    return 1
}

a2a_query_case() {
    local case_id="$1"

    log_verbose "A2A: Querying case: $case_id"

    local jwt=$(generate_jwt "test-user" '"workflow:query"' "yawl-a2a")

    local request='{"message":"query_workflows","params":{"caseId":"'"$case_id"'"}}'

    if curl -s -X GET \
        -H "Authorization: Bearer $jwt" \
        "${A2A_SERVER_URL}/tasks/${case_id}" > "${TEMP_DIR}/a2a-query-response.json" 2>/dev/null; then

        local status=$(grep -o '"status":"[^"]*"' "${TEMP_DIR}/a2a-query-response.json" | cut -d'"' -f4)

        if [[ -n "$status" ]]; then
            echo "$status"
            return 0
        fi
    fi

    log_error "A2A: Failed to query case $case_id"
    return 1
}

# ── Utility Functions ───────────────────────────────────────────────────
xml_to_json() {
    local xml_file="$1"

    if [[ ! -f "$xml_file" ]]; then
        log_error "XML file not found: $xml_file"
        echo ""
        return 1
    fi

    # Simple XML to JSON conversion (for basic XML structures)
    python3 -c "
import json
import xml.etree.ElementTree as ET

tree = ET.parse('$xml_file')
root = tree.getroot()

# Convert XML to simple dictionary
def xml_to_dict(element):
    result = {}
    for child in element:
        if child.attrib:
            result[child.tag] = child.attrib
        if child.text and child.text.strip():
            if child.text.strip():
                result[child.tag] = child.text.strip()
        elif len(list(child)) > 0:
            result[child.tag] = xml_to_dict(child)
    return result

try:
    print(json.dumps(xml_to_dict(root)))
except Exception as e:
    print('{}')
" 2>/dev/null || echo "{}"
}

wait_for_completion() {
    local case_id="$1"
    local max_attempts="${2:-60}"
    local attempt=0

    log_verbose "Waiting for case $case_id to complete..."

    while [[ $attempt -lt $max_attempts ]]; do
        local status=$(yawl_get_case_status "$case_id" 2>/dev/null || echo "")

        case "$status" in
            "complete"|"closed"|"finished")
                log_success "Case $case_id completed with status: $status"
                return 0
                ;;
            "running"|"active")
                ((attempt++))
                sleep 1
                ;;
            *)
                log_warning "Case $case_id has unexpected status: $status"
                return 1
                ;;
        esac
    done

    log_error "Timeout waiting for case $case_id to complete"
    return 1
}

generate_jwt() {
    local sub="$1"
    local permissions="$2"
    local audience="$3"

    # Simple JWT generation for testing
    python3 -c "
import jwt
import time

payload = {
    'sub': '$sub',
    'permissions': $permissions,
    'iat': int(time.time()),
    'exp': int(time.time()) + 3600,
    'aud': '$audience'
}

secret = 'test-secret-key'  # In production, use proper secret
token = jwt.encode(payload, secret, algorithm='HS256')
print(token)
" 2>/dev/null || echo ""
}

# ─── Test Result Functions ───────────────────────────────────────────────
record_test_result() {
    local test_name="$1"
    local status="$2"  # pass/fail

    echo "$test_name:$status" >> "${TEMP_DIR}/test-results.txt"
}

get_test_summary() {
    local total=0
    local passed=0
    local failed=0

    if [[ -f "${TEMP_DIR}/test-results.txt" ]]; then
        total=$(wc -l < "${TEMP_DIR}/test-results.txt")
        passed=$(grep ":pass$" "${TEMP_DIR}/test-results.txt" | wc -l)
        failed=$((total - passed))
    fi

    echo "$total:$passed:$failed"
}

generate_test_report() {
    local test_type="$1"
    local summary_file="${TEMP_DIR}/test-summary-${test_type}.json"

    cat > "$summary_file" <<EOF
{
  "test_type": "$test_type",
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "results": {
    "total": $(echo "$(get_test_summary)" | cut -d: -f1),
    "passed": $(echo "$(get_test_summary)" | cut -d: -f2),
    "failed": $(echo "$(get_test_summary)" | cut -d: -f3)
  },
  "details": $(cat "${TEMP_DIR}/test-results.txt" | sed 's/:pass/: "pass"/;s/:fail/: "fail"/' | jq -R -s 'split("\n")[:-1] | map(split(":") | {name: .[0], status: .[1]})' 2>/dev/null || echo "[]")
}
EOF

    echo "$summary_file"
}