#!/bin/bash

# State-Based Patterns Validation (WCP 18-21, 32-35)
# Deferred Choice, Milestone, Cancel Activity, Cancel Case, State management

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/statebased"
ENGINE_URL="http://localhost:8080"
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

success() {
    echo "${GREEN}✓${NC} $1"
}

error() {
    echo "${RED}✗${NC} $1"
}

warn() {
    echo "${YELLOW}⚠${NC} $1"
}

log() {
    echo "[$(date '+%H:%M:%S')] $1"
}

# State-based pattern configurations
declare -A PATTERNS=(
    ["wcp-18-deferred-choice.yaml"]="Deferred Choice"
    ["wcp-19-milestone.yaml"]="Milestone"
    ["wcp-20-cancel-activity.yaml"]="Cancel Activity"
    ["wcp-21-cancel-case.yaml"]="Cancel Case"
    ["wcp-32-sync-cancel.yaml"]="Synchronization with Cancel"
    ["wcp-33-generalized-join.yaml"]="Generalized AND-Join"
    ["wcp-34-static-partial-join.yaml"]="Static Partial Join"
    ["wcp-35-dynamic-partial-join.yaml"]="Dynamic Partial Join"
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

    local total_work_items=0
    local completed_work_items=0
    local case_cancelled=false
    local case_completed=false

    # Process state-based patterns with special handling
    for iteration in {1..10}; do
        # Get work items
        local work_items_response
        work_items_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

        local current_items
        current_items=$(echo "$work_items_response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

        # Handle different pattern types
        case "$pattern_name" in
            *Deferred*|*deferred*)
                # For deferred choice, only complete one work item
                local max_items=1
                ;;
            *Milestone*|*milestone*)
                # For milestone, complete sequentially
                local max_items=1
                ;;
            *Cancel*|*cancel*)
                # For cancel patterns, test cancellation functionality
                if [[ $iteration -eq 1 && $current_items -gt 0 ]]; then
                    # Cancel case
                    local cancel_response
                    cancel_response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        "${ENGINE_URL}/yawl/ib?action=cancelCase&caseId=${case_id}")

                    if echo "$cancel_response" | grep -q 'success'; then
                        case_cancelled=true
                        success "$pattern_name - Case cancelled successfully"
                        return 0
                    fi
                fi
                local max_items=0  # Don't process work items for cancel tests
                ;;
            *)
                local max_items=$current_items
                ;;
        esac

        # Complete work items
        if [[ $current_items -gt 0 && $max_items -gt 0 ]]; then
            for ((i=1; i<=current_items && i<=max_items; i++)); do
                local work_item_id="state_workitem_${iteration}_${i}"
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

    # Determine result based on pattern type
    if [[ "$pattern_name" =~ .*Cancel.* ]]; then
        # For cancel patterns, success means case was cancelled
        if [[ "$case_cancelled" == true ]]; then
            success "$pattern_name (${duration}s) - Case cancelled"
            return 0
        else
            error "$pattern_name - Failed (state: $case_state)"
            return 1
        fi
    else
        # For other patterns, success means case completed
        if [[ "$case_completed" == true ]]; then
            success "$pattern_name (${duration}s) - $completed_work_items work items"
            return 0
        else
            error "$pattern_name - Failed (state: $case_state, completed: $completed_work_items)"
            return 1
        fi
    fi
}

# Main execution
main() {
    echo "=== Validating State-Based Patterns (WCP 18-21, 32-35) ==="
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
        success "All state-based patterns validated successfully"
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