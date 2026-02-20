#!/bin/bash

# Advanced Branching Patterns Validation (WCP 6-11)
# Multi-Choice, Synchronization Merge, Multi-Merge, Discriminator, Loop

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/branching"
ENGINE_URL="http://localhost:8080"
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

success() {
    echo "${GREEN}✓${NC} $1"
}

error() {
    echo "${RED}✗${NC} $1"
}

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

# Advanced pattern configurations
declare -A PATTERNS=(
    ["wcp-6-multi-choice.yaml"]="Multi-Choice"
    ["wcp-7-sync-merge.yaml"]="Synchronization Merge"
    ["wcp-8-multi-merge.yaml"]="Multi-Merge"
    ["wcp-9-discriminator.yaml"]="Discriminator"
    ["wcp-10-structured-loop.yaml"]="Structured Loop"
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
        error "Failed to convert YAML to XML"
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
        error "Failed to add specification"
        return 1
    fi

    # Launch case
    local spec_id=$(echo "$spec_response" | sed 's/.*specIdentifier:\([^,]*\).*/\1/' | tr -d ' ')
    local case_response
    case_response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

    if ! echo "$case_response" | grep -q 'caseID'; then
        error "Failed to launch case"
        return 1
    fi

    local case_id=$(echo "$case_response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')

    # Track work items and their completion
    local total_work_items=0
    local completed_items=0
    local case_completed=false

    # Loop to handle work items
    for iteration in {1..10}; do
        # Get current work items
        local work_items_response
        work_items_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

        local work_items
        work_items=$(echo "$work_items_response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")
        current_work_items=$work_items

        if [[ "$current_work_items" -eq 0 ]]; then
            break
        fi

        # Complete current work items
        for ((i=1; i<=current_work_items; i++)); do
            # Simulate work item ID based on iteration
            local work_item_id="workitem_${iteration}_${i}"

            local work_item_response
            work_item_response=$(curl -s -X POST \
                -H "Connection-ID: $CONNECTION_ID" \
                -H "Content-Type: application/xml" \
                --data "<data><item>${work_item_id}</item></data>" \
                "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item_id}&data=<data/>")

            if echo "$work_item_response" | grep -q 'success'; then
                ((completed_items++))
            fi
        done

        ((total_work_items += current_work_items))

        # Check if case is completed
        local state_response
        state_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

        local case_state=$(echo "$state_response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")

        if [[ "$case_state" == "complete" ]]; then
            case_completed=true
            break
        fi

        sleep 1  # Small delay between iterations
    done

    local duration=$(( $(date +%s) - start_time ))

    # Final state check
    if [[ "$case_completed" == true ]] || [[ "$case_state" == "complete" ]]; then
        success "$pattern_name (${duration}s) - $completed_items/$total_work_items work items"
        return 0
    else
        error "$pattern_name - Failed (state: $case_state, completed: $completed_items/$total_work_items)"
        return 1
    fi
}

# Main execution
main() {
    echo "=== Validating Advanced Branching Patterns (WCP 6-11) ==="
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
            log "WARNING: Pattern file not found: $pattern_file"
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
        success "All branching patterns validated successfully"
        exit 0
    elif [[ $total -eq 0 ]]; then
        error "No patterns found to validate"
        exit 1
    else
        error "$failed patterns failed validation"
        exit 1
    fi
}

main "$@"