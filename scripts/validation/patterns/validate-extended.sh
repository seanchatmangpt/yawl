#!/bin/bash

# Extended Patterns Validation (WCP 41-50)
# Blocked Split, Critical Section, Saga, Distributed, Two-Phase Commit, Circuit Breaker

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/extended"
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

# Extended pattern configurations
declare -A PATTERNS=(
    ["wcp-41-blocked-split.yaml"]="Blocked Split"
    ["wcp-42-critical-section.yaml"]="Critical Section"
    ["wcp-43-critical-cancel.yaml"]="Critical Cancel"
    ["wcp-44-saga.yaml"]="Saga Pattern"
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
    local case_completed=false
    local failures=0

    # Process extended patterns with error handling
    for iteration in {1..15}; do
        # Get work items
        local work_items_response
        work_items_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

        local current_items
        current_items=$(echo "$work_items_response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

        # Handle different pattern types
        case "$pattern_name" in
            *Saga*|*Distributed*)
                # For distributed patterns, simulate failure handling
                if [[ $iteration -eq 2 ]]; then
                    # Simulate failure
                    log "Simulating failure for $pattern_name"
                    local work_item_id="fail_workitem_${iteration}"
                    local fail_response
                    fail_response=$(curl -s -X POST \
                        -H "Connection-ID: $CONNECTION_ID" \
                        -H "Content-Type: application/xml" \
                        --data "<data><item>${work_item_id}</item><fail>true</fail></data>" \
                        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item_id}&data=<data/>")

                    if ! echo "$fail_response" | grep -q 'success'; then
                        ((failures++))
                    fi
                else
                    # Complete normally
                    for ((i=1; i<=current_items; i++)); do
                        local work_item_id="dist_workitem_${iteration}_${i}"
                        local work_item_response
                        work_item_response=$(curl -s -X POST \
                            -H "Connection-ID: $CONNECTION_ID" \
                            -H "Content-Type: application/xml" \
                            --data "<data><item>${work_item_id}</item></data>" \
                            "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item_id}&data=<data/>")

                        if echo "$work_item_response" | grep -q 'success'; then
                            ((completed_work_items++))
                        else
                            ((failures++))
                        fi
                    done
                fi
                ;;
            *Circuit*Breaker*)
                # For circuit breaker, simulate failure threshold
                if [[ $iteration -gt 5 ]]; then
                    # Simulate circuit breaker opening
                    warn "Circuit breaker activated for $pattern_name"
                    sleep 2
                else
                    # Process normally
                    for ((i=1; i<=current_items; i++)); do
                        local work_item_id="cb_workitem_${iteration}_${i}"
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
                ;;
            *Critical*Section*)
                # For critical section, ensure sequential processing
                local max_items=1
                for ((i=1; i<=current_items && i<=max_items; i++)); do
                    local work_item_id="cs_workitem_${iteration}_${i}"
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
                ;;
            *)
                # Default processing
                for ((i=1; i<=current_items; i++)); do
                    local work_item_id="ext_workitem_${iteration}_${i}"
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
                ;;
        esac

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
        fi

        sleep 2  # Longer delay for extended patterns
    done

    local duration=$(( $(date +%s) - start_time ))

    # Determine result
    if [[ "$case_completed" == true ]]; then
        success "$pattern_name (${duration}s) - $completed_work_items/$total_work_items work items, $failures failures"
        return 0
    else
        error "$pattern_name - Failed (state: $case_state, completed: $completed_work_items, failures: $failures)"
        return 1
    fi
}

# Main execution
main() {
    echo "=== Validating Extended Patterns (WCP 41-50) ==="
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
        success "All extended patterns validated successfully"
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