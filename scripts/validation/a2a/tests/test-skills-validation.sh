#!/usr/bin/env bash
# ==========================================================================
# test-skills-validation.sh — A2A Skills Validation Tests
#
# Validates that the A2A server correctly exposes and implements all
# required skills according to the YAWL A2A specification.
#
# Skills tested:
#   - launch_workflow: Launch new workflow cases
#   - query_workflows: List specifications and running cases
#   - manage_workitems: Get and complete work items
#   - cancel_workflow: Cancel running workflow cases
#   - handoff_workitem: Transfer work items between agents
#
# Usage:
#   bash scripts/validation/a2a/tests/test-skills-validation.sh
#   bash scripts/validation/a2a/tests/test-skills-validation.sh --verbose
#   bash scripts/validation/a2a/tests/test-skills-validation.sh --json
#
# Exit Codes:
#   0 - All skill tests passed
#   1 - One or more skill tests failed
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

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a TEST_RESULTS=()

# Required skills per A2A specification
REQUIRED_SKILLS=(
    "launch_workflow"
    "query_workflows"
    "manage_workitems"
    "cancel_workflow"
    "handoff_workitem"
)

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

log_verbose() {
    [[ "$VERBOSE" -eq 1 ]] && echo "[VERBOSE] $*" >&2
}

# ── Test Runner Functions ─────────────────────────────────────────────────
run_test() {
    local test_name="$1"
    local test_description="$2"
    local test_function="$3"

    ((TOTAL_TESTS++)) || true

    log_verbose "Running: $test_name - $test_description"

    if eval "$test_function"; then
        ((PASSED_TESTS++)) || true
        TEST_RESULTS+=("{\"name\":\"${test_name}\",\"status\":\"PASS\",\"description\":\"${test_description}\"}")
        log_success "$test_name"
        return 0
    else
        ((FAILED_TESTS++)) || true
        TEST_RESULTS+=("{\"name\":\"${test_name}\",\"status\":\"FAIL\",\"description\":\"${test_description}\"}")
        log_error "$test_name"
        return 1
    fi
}

# ── Agent Card Retrieval Helper ───────────────────────────────────────────
get_agent_card() {
    curl -s --connect-timeout ${A2A_TIMEOUT} \
        "${A2A_BASE_URL}/.well-known/agent.json" 2>/dev/null
}

# ── Skill Discovery Tests ─────────────────────────────────────────────────
test_agent_card_accessible() {
    local response
    response=$(curl -s -w "\n%{http_code}" --connect-timeout ${A2A_TIMEOUT} \
        "${A2A_BASE_URL}/.well-known/agent.json" 2>/dev/null)

    local status_code=$(echo "$response" | tail -1)
    [[ "$status_code" == "200" ]]
}

test_agent_card_valid_json() {
    local response
    response=$(get_agent_card)

    # Validate JSON structure
    echo "$response" | jq -e '.' > /dev/null 2>&1
}

test_agent_card_has_name() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.name' > /dev/null 2>&1
}

test_agent_card_has_version() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.version' > /dev/null 2>&1
}

test_agent_card_has_provider() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.provider' > /dev/null 2>&1
}

test_agent_card_has_capabilities() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.capabilities' > /dev/null 2>&1
}

test_agent_card_has_skills_array() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills | type == "array"' > /dev/null 2>&1
}

# ── Individual Skill Validation Tests ─────────────────────────────────────
test_skill_launch_workflow_exists() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "launch_workflow")' > /dev/null 2>&1
}

test_skill_launch_workflow_has_name() {
    local response
    response=$(get_agent_card)

    local skill_name
    skill_name=$(echo "$response" | jq -r '.skills[] | select(.id == "launch_workflow") | .name' 2>/dev/null)

    [[ -n "$skill_name" && "$skill_name" != "null" ]]
}

test_skill_launch_workflow_has_description() {
    local response
    response=$(get_agent_card)

    local description
    description=$(echo "$response" | jq -r '.skills[] | select(.id == "launch_workflow") | .description' 2>/dev/null)

    [[ -n "$description" && "$description" != "null" ]]
}

test_skill_launch_workflow_has_tags() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "launch_workflow") | .tags | type == "array"' > /dev/null 2>&1
}

test_skill_launch_workflow_has_examples() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "launch_workflow") | .examples | type == "array"' > /dev/null 2>&1
}

test_skill_query_workflows_exists() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "query_workflows")' > /dev/null 2>&1
}

test_skill_query_workflows_has_tags() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "query_workflows") | .tags | type == "array"' > /dev/null 2>&1
}

test_skill_manage_workitems_exists() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "manage_workitems")' > /dev/null 2>&1
}

test_skill_manage_workitems_has_tags() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "manage_workitems") | .tags | type == "array"' > /dev/null 2>&1
}

test_skill_cancel_workflow_exists() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "cancel_workflow")' > /dev/null 2>&1
}

test_skill_cancel_workflow_has_tags() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "cancel_workflow") | .tags | type == "array"' > /dev/null 2>&1
}

test_skill_handoff_workitem_exists() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "handoff_workitem")' > /dev/null 2>&1
}

test_skill_handoff_workitem_has_tags() {
    local response
    response=$(get_agent_card)

    echo "$response" | jq -e '.skills[] | select(.id == "handoff_workitem") | .tags | type == "array"' > /dev/null 2>&1
}

# ── Skill Count Validation ────────────────────────────────────────────────
test_all_required_skills_present() {
    local response
    response=$(get_agent_card)

    local missing_skills=()

    for skill in "${REQUIRED_SKILLS[@]}"; do
        if ! echo "$response" | jq -e ".skills[] | select(.id == \"$skill\")" > /dev/null 2>&1; then
            missing_skills+=("$skill")
        fi
    done

    [[ ${#missing_skills[@]} -eq 0 ]]
}

test_minimum_skill_count() {
    local response
    response=$(get_agent_card)

    local skill_count
    skill_count=$(echo "$response" | jq '.skills | length' 2>/dev/null || echo "0")

    # Must have at least 4 skills (5 with handoff)
    [[ "$skill_count" -ge 4 ]]
}

# ── Skill Input/Output Modes Tests ────────────────────────────────────────
test_skill_has_input_modes() {
    local response
    response=$(get_agent_card)

    # Check that at least one skill has inputModes
    echo "$response" | jq -e '.skills[] | select(.inputModes != null) | .inputModes | length > 0' > /dev/null 2>&1
}

test_skill_has_output_modes() {
    local response
    response=$(get_agent_card)

    # Check that at least one skill has outputModes
    echo "$response" | jq -e '.skills[] | select(.outputModes != null) | .outputModes | length > 0' > /dev/null 2>&1
}

# ── Provider Information Tests ────────────────────────────────────────────
test_provider_has_name() {
    local response
    response=$(get_agent_card)

    local provider_name
    provider_name=$(echo "$response" | jq -r '.provider.name' 2>/dev/null)

    [[ -n "$provider_name" && "$provider_name" != "null" ]]
}

test_provider_has_url() {
    local response
    response=$(get_agent_card)

    local provider_url
    provider_url=$(echo "$response" | jq -r '.provider.url' 2>/dev/null)

    [[ -n "$provider_url" && "$provider_url" != "null" ]]
}

# ── Run All Skills Tests ──────────────────────────────────────────────────
run_all_skills_tests() {
    log_info "Starting A2A Skills Validation Tests"
    log_info "Server: ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}"
    echo ""

    # Agent Card Structure Tests
    echo "=== Agent Card Structure ==="
    run_test "agent_card_accessible" "Agent card endpoint is accessible" \
        "test_agent_card_accessible"
    run_test "agent_card_valid_json" "Agent card returns valid JSON" \
        "test_agent_card_valid_json"
    run_test "agent_card_has_name" "Agent card has name field" \
        "test_agent_card_has_name"
    run_test "agent_card_has_version" "Agent card has version field" \
        "test_agent_card_has_version"
    run_test "agent_card_has_provider" "Agent card has provider field" \
        "test_agent_card_has_provider"
    run_test "agent_card_has_capabilities" "Agent card has capabilities field" \
        "test_agent_card_has_capabilities"
    run_test "agent_card_has_skills_array" "Agent card has skills array" \
        "test_agent_card_has_skills_array"
    echo ""

    # Launch Workflow Skill Tests
    echo "=== launch_workflow Skill ==="
    run_test "skill_launch_exists" "launch_workflow skill exists" \
        "test_skill_launch_workflow_exists"
    run_test "skill_launch_name" "launch_workflow has name" \
        "test_skill_launch_workflow_has_name"
    run_test "skill_launch_description" "launch_workflow has description" \
        "test_skill_launch_workflow_has_description"
    run_test "skill_launch_tags" "launch_workflow has tags array" \
        "test_skill_launch_workflow_has_tags"
    run_test "skill_launch_examples" "launch_workflow has examples" \
        "test_skill_launch_workflow_has_examples"
    echo ""

    # Query Workflows Skill Tests
    echo "=== query_workflows Skill ==="
    run_test "skill_query_exists" "query_workflows skill exists" \
        "test_skill_query_workflows_exists"
    run_test "skill_query_tags" "query_workflows has tags array" \
        "test_skill_query_workflows_has_tags"
    echo ""

    # Manage Work Items Skill Tests
    echo "=== manage_workitems Skill ==="
    run_test "skill_workitems_exists" "manage_workitems skill exists" \
        "test_skill_manage_workitems_exists"
    run_test "skill_workitems_tags" "manage_workitems has tags array" \
        "test_skill_manage_workitems_has_tags"
    echo ""

    # Cancel Workflow Skill Tests
    echo "=== cancel_workflow Skill ==="
    run_test "skill_cancel_exists" "cancel_workflow skill exists" \
        "test_skill_cancel_workflow_exists"
    run_test "skill_cancel_tags" "cancel_workflow has tags array" \
        "test_skill_cancel_workflow_has_tags"
    echo ""

    # Handoff Work Item Skill Tests
    echo "=== handoff_workitem Skill ==="
    run_test "skill_handoff_exists" "handoff_workitem skill exists" \
        "test_skill_handoff_workitem_exists"
    run_test "skill_handoff_tags" "handoff_workitem has tags array" \
        "test_skill_handoff_workitem_has_tags"
    echo ""

    # Overall Skill Validation Tests
    echo "=== Overall Skill Validation ==="
    run_test "all_required_skills" "All required skills are present" \
        "test_all_required_skills_present"
    run_test "minimum_skill_count" "Minimum skill count met (>=4)" \
        "test_minimum_skill_count"
    run_test "skill_input_modes" "Skills have input modes defined" \
        "test_skill_has_input_modes"
    run_test "skill_output_modes" "Skills have output modes defined" \
        "test_skill_has_output_modes"
    echo ""

    # Provider Information Tests
    echo "=== Provider Information ==="
    run_test "provider_name" "Provider has name" \
        "test_provider_has_name"
    run_test "provider_url" "Provider has URL" \
        "test_provider_has_url"
    echo ""
}

# ── Output Functions ───────────────────────────────────────────────────────
output_json() {
    local results_json=$(IFS=,; echo "${TEST_RESULTS[*]}")
    cat << JSON_EOF
{
  "timestamp": "$(date -u +'%Y-%m-%dT%H:%M:%SZ')",
  "server": {
    "host": "${A2A_SERVER_HOST}",
    "port": ${A2A_SERVER_PORT}
  },
  "category": "skills",
  "required_skills": $(printf '%s\n' "${REQUIRED_SKILLS[@]}" | jq -R . | jq -s .),
  "total_tests": ${TOTAL_TESTS},
  "passed": ${PASSED_TESTS},
  "failed": ${FAILED_TESTS},
  "results": [${results_json}],
  "status": $([[ "${FAILED_TESTS}" -eq 0 ]] && echo "\"PASS\"" || echo "\"FAIL\"")
}
JSON_EOF
}

output_text() {
    echo ""
    echo "==========================================="
    echo "A2A Skills Validation Test Results"
    echo "==========================================="
    echo "Total Tests: ${TOTAL_TESTS}"
    echo "Passed: ${PASSED_TESTS}"
    echo "Failed: ${FAILED_TESTS}"
    echo ""

    if [[ ${FAILED_TESTS} -eq 0 ]]; then
        echo -e "${GREEN}All skills validation tests passed.${NC}"
        return 0
    else
        echo -e "${RED}${FAILED_TESTS} skills validation tests failed.${NC}"
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

    # Check for jq dependency
    if ! command -v jq &> /dev/null; then
        echo "[ERROR] jq is required for skills validation tests" >&2
        echo "Install with: brew install jq (macOS) or apt install jq (Linux)" >&2
        exit 2
    fi

    # Check server availability
    log_verbose "Checking A2A server availability..."
    if ! a2a_ping; then
        echo "[ERROR] A2A server not running at ${A2A_SERVER_HOST}:${A2A_SERVER_PORT}" >&2
        echo "Start the server first: bash scripts/start-a2a-server.sh" >&2
        exit 2
    fi

    log_verbose "A2A server is available"

    # Run tests
    run_all_skills_tests

    # Output results
    if [[ "$OUTPUT_FORMAT" == "json" ]]; then
        output_json
    else
        output_text
    fi
}

main "$@"
