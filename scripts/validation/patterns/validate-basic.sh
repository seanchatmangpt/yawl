#!/bin/bash

# Basic Control Flow Patterns Validation (WCP 1-5)
# Sequence, Parallel Split, Synchronization, Exclusive Choice, Simple Merge

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/controlflow"
ENGINE_URL="http://localhost:8080"
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors
GREEN='\033[0;32m'
NC='\033[0m'

success() {
    echo "${GREEN}✓${NC} $1"
}

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

warn() {
    echo "[WARN] $1"
}

# Basic pattern configurations
declare -A PATTERNS=(
    ["wcp-1-sequence.yaml"]="Sequence"
    ["wcp-2-parallel-split.yaml"]="Parallel Split"
    ["wcp-3-synchronization.yaml"]="Synchronization"
    ["wcp-4-exclusive-choice.yaml"]="Exclusive Choice"
    ["wcp-5-simple-merge.yaml"]="Simple Merge"
)

validate_pattern() {
    local pattern_file="$1"
    local pattern_name="$2"
    local start_time=$(date +%s)

    log "Validating: $pattern_name ($pattern_file)"

    # Convert to XML
    local xml_file
    xml_file="${SCRIPT_DIR}/../../tmp/$(basename "$pattern_file" .xml).xml"
    mkdir -p "$(dirname "$xml_file")"

    if ! "${SCRIPT_DIR}/yaml-to-xml.sh" "$PATTERNS_DIR/$pattern_file" "$(dirname "$xml_file")"; then
        echo "  ✗ Failed to convert YAML to XML"
        return 1
    fi

    # Add specification
    local spec_response
    spec_response=$(curl -s -X POST \
        -H "Content-Type: application/xml" \
        -H "Connection-ID: $CONNECTION_ID" \
        --data @"$xml_file" \
        "${ENGINE_URL}/yawl/ia?action=addSpecification")

    if ! echo "$spec_response" | grep -q 'success'; then
        echo "  ✗ Failed to add specification"
        return 1
    fi

    # Launch case
    local spec_id=$(echo "$spec_response" | sed 's/.*specIdentifier:\([^,]*\).*/\1/' | tr -d ' ')
    local case_response
    case_response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

    if ! echo "$case_response" | grep -q 'caseID'; then
        echo "  ✗ Failed to launch case"
        return 1
    fi

    local case_id=$(echo "$case_response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')

    # Get work items
    local work_items
    work_items=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

    # Complete work items
    local completed=0
    for i in $(seq 1 $work_items); do
        local work_item_response
        work_item_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            -H "Content-Type: application/xml" \
            --data "<data><item>workitem_$i</item></data>" \
            "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=workitem_$i&data=<data/>")

        if echo "$work_item_response" | grep -q 'success'; then
            ((completed++))
        fi
    done

    # Check completion
    local state_response
    state_response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

    local case_state=$(echo "$state_response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")
    local duration=$(( $(date +%s) - start_time ))

    if [[ "$case_state" == "complete" ]] && [[ "$completed" -gt 0 ]]; then
        echo "  ✓ $pattern_name (${duration}s) - $completed work items"
        return 0
    else
        echo "  ✗ $pattern_name - Failed (state: $case_state, completed: $completed)"
        return 1
    fi
}

# Main execution
main() {
    echo "=== Validating Basic Control Flow Patterns (WCP 1-5) ==="
    echo ""

    local total=0
    local passed=0
    local failed=0

    for pattern_file in "${!PATTERNS[@]}"; do
        if [[ -f "$PATTERNS_DIR/$pattern_file" ]]; then
            ((total++))
            if validate_pattern "$pattern_file" "${PATTERNS[$pattern_file]}"; then
                ((passed++))
            else
                ((failed++))
            fi
        else
            warn "Pattern file not found: $pattern_file"
        fi
    done

    echo ""
    echo "=== Summary ==="
    echo "Total patterns: $total"
    echo "Passed: $passed"
    echo "Failed: $failed"
    if [[ $total -gt 0 ]]; then
        echo "Success rate: $(( (passed * 100) / total ))%"
    else
        echo "Success rate: N/A (no patterns found)"
    fi

    if [[ $failed -eq 0 ]] && [[ $total -gt 0 ]]; then
        echo "All basic patterns validated successfully"
        exit 0
    elif [[ $total -eq 0 ]]; then
        echo "No patterns found to validate"
        exit 1
    else
        echo "$failed patterns failed validation"
        exit 1
    fi
}

main "$@"