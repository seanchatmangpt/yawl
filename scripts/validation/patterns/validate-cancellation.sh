#!/bin/bash

# Cancellation Patterns Validation (WCP 22-23, 25, 29-31)
# Cancel Region, Multi-Instance Cancel, Loop Cancel, Cancellation logic

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/controlflow"
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

warn() {
    echo "[WARN] $1"
}

# Cancellation pattern configurations (WCP 19-20, 22-23, 25, 29-31)
declare -A PATTERNS=(
    ["wcp-19-cancel-task.yaml"]="Cancel Task (WCP 19)"
    ["wcp-20-cancel-case.yaml"]="Cancel Case (WCP 20)"
    ["wcp-22-cancel-region.yaml"]="Cancel Region (WCP 22)"
    ["wcp-23-cancel-mi.yaml"]="Cancel MI (WCP 23)"
    ["wcp-25-cancel-complete-mi.yaml"]="Cancel Complete MI (WCP 25)"
    ["wcp-29-loop-cancel.yaml"]="Loop Cancel (WCP 29)"
    ["wcp-30-loop-cancel-region.yaml"]="Loop Cancel Region (WCP 30)"
    ["wcp-31-loop-complete-mi.yaml"]="Loop Complete MI (WCP 31)"
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

    local case_cancelled=false
    local case_completed=false
    local total_work_items=0
    local completed_work_items=0

    # Process cancellation patterns
    for iteration in {1..5}; do
        # Get work items
        local work_items_response
        work_items_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

        local current_items
        current_items=$(echo "$work_items_response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

        if [[ "$current_items" -gt 0 ]]; then
            # For cancellation patterns, test both completion and cancellation
            if [[ $iteration -eq 3 ]]; then
                # Trigger cancellation
                log "Triggering cancellation for $pattern_name"
                local cancel_response
                cancel_response=$(curl -s -X POST \
                    -H "Connection-ID: $CONNECTION_ID" \
                    "${ENGINE_URL}/yawl/ib?action=cancelCase&caseId=${case_id}")

                if echo "$cancel_response" | grep -q 'success'; then
                    case_cancelled=true
                    break
                else
                    warn "Cancellation failed for $pattern_name"
                fi
            else
                # Complete some work items
                for ((i=1; i<=current_items && i<=3; i++)); do
                    local work_item_id="cancel_workitem_${iteration}_${i}"
                    local work_item_response
                    work_item_response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item_id}</item></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item_id}&data=<data/>")

                    if echo "$work_item_response" | grep -q 'success'; then
                        ((completed_work_items++))
                    fi
                done
            fi

            ((total_work_items += current_items))
        fi

        # Check case state
        local state_response
        state_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

        local case_state=$(echo "$state_response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")

        if [[ "$case_state" == "complete" ]]; then
            case_completed=true
            break
        elif [[ "$case_state" == "cancelled" ]]; then
            case_cancelled=true
            break
        fi

        sleep 1
    done

    local duration=$(( $(date +%s) - start_time ))

    # Determine success based on cancellation logic
    if [[ "$pattern_name" =~ .*Cancel.* ]]; then
        # For cancellation patterns, success means case was cancelled
        if [[ "$case_cancelled" == true ]]; then
            success "$pattern_name (${duration}s) - Cancellation successful"
            return 0
        else
            error "$pattern_name - Cancellation failed (state: $case_state)"
            return 1
        fi
    else
        # For other patterns in cancellation group
        if [[ "$case_completed" == true ]]; then
            success "$pattern_name (${duration}s) - $completed_work_items work items"
            return 0
        else
            error "$pattern_name - Failed (state: $case_state)"
            return 1
        fi
    fi
}

# Main execution
main() {
    echo "=== Validating Cancellation Patterns (WCP 22-23, 25, 29-31) ==="
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
        success "All cancellation patterns validated successfully"
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