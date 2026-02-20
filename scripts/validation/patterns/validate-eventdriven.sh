#!/bin/bash

# Event-Driven Patterns Validation (WCP 37-40, 51-59)
# Triggers, Event Gateway, CQRS, Async messaging, Event Sourcing

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATTERNS_DIR="${SCRIPT_DIR}/../../../yawl-mcp-a2a-app/src/main/resources/patterns/eventdriven"
ENGINE_URL="http://localhost:8080"
MCPS_URL="http://localhost:18081"
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

# Event-driven pattern configurations
declare -A PATTERNS=(
    ["wcp-37-local-trigger.yaml"]="Local Trigger"
    ["wcp-38-global-trigger.yaml"]="Global Trigger"
    ["wcp-39-reset-trigger.yaml"]="Reset Trigger"
    ["wcp-40-reset-cancel.yaml"]="Reset Cancel"
    ["wcp-51-event-gateway.yaml"]="Event Gateway"
    ["wcp-52-outbox.yaml"]="Outbox Pattern"
    ["wcp-53-scatter-gather.yaml"]="Scatter-Gather"
    ["wcp-54-event-router.yaml"]="Event Router"
    ["wcp-55-event-stream.yaml"]="Event Stream"
    ["wcp-56-cqrs.yaml"]="CQRS"
    ["wcp-57-event-sourcing.yaml"]="Event Sourcing"
    ["wcp-58-compensating-transaction.yaml"]="Compensating Transaction"
    ["wcp-59-side-by-side.yaml"]="Side-by-Side"
)

# Check if MCP-A2A is available
check_mcp_a2a() {
    if curl -s -f "${MCPS_URL}/actuator/health" > /dev/null 2>&1; then
        log "MCP-A2A service available"
        return 0
    else
        warn "MCP-A2A service not available, using basic validation"
        return 1
    fi
}

# Simulate event generation
generate_event() {
    local event_type="$1"
    local event_data="$2"
    local pattern_name="$3"

    if check_mcp_a2a; then
        # Use MCP-A2A for event generation
        local event_response
        event_response=$(curl -s -X POST \
            -H "Content-Type: application/json" \
            -d '{"event": "'"${event_type}"'", "data": '"${event_data}"'}' \
            "${MCPS_URL}/a2a/events")

        if echo "$event_response" | grep -q 'success'; then
            log "Event generated: $event_type"
            return 0
        else
            warn "Failed to generate event via MCP-A2A: $event_response"
        fi
    fi

    # Fallback to simulated event
    log "Simulating event: $event_type"
    return 0
}

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
    case_completed=false
    events_processed=0

    # Process event-driven patterns
    for iteration in {1..20}; do
        # Check for work items
        local work_items_response
        work_items_response=$(curl -s -X POST \
            -H "Connection-ID: $CONNECTION_ID" \
            "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

        local current_items
        current_items=$(echo "$work_items_response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")

        # Handle event-driven behavior
        if [[ $current_items -gt 0 ]]; then
            # Process work items
            for ((i=1; i<=current_items; i++)); do
                local work_item_id="event_workitem_${iteration}_${i}"
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
        else
            # Generate events based on pattern type
            case "$pattern_name" in
                *Event*Gateway*)
                    # Trigger event gateway
                    generate_event "gateway_trigger" '{"iteration": "'$iteration'"}' "$pattern_name"
                    ((events_processed++))
                    ;;
                *CQRS*)
                    # Simulate read and write operations
                    if [[ $((iteration % 2)) -eq 0 ]]; then
                        # Write operation
                        generate_event "write_operation" '{"data": "write_'$iteration'"}' "$pattern_name"
                    else
                        # Read operation
                        generate_event "read_operation" '{"id": '$iteration'}' "$pattern_name"
                    fi
                    ((events_processed++))
                    ;;
                *Event*Sourcing*)
                    # Generate events for event sourcing
                    generate_event "domain_event" '{"type": "'$pattern_name'", "sequence": '$iteration'}' "$pattern_name"
                    ((events_processed++))
                    ;;
                *)
                    # Generic trigger event
                    generate_event "trigger_event" '{"iteration": '$iteration', "pattern": "'"$pattern_name"'"}' "$pattern_name"
                    ((events_processed++))
                    ;;
            esac
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
        fi

        sleep 1
    done

    local duration=$(( $(date +%s) - start_time ))

    # Final validation
    if [[ "$case_completed" == true ]]; then
        success "$pattern_name (${duration}s) - $completed_work_items/$total_work_items work items, $events_processed events"
        return 0
    else
        error "$pattern_name - Failed (state: $case_state, events processed: $events_processed)"
        return 1
    fi
}

# Main execution
main() {
    echo "=== Validating Event-Driven Patterns (WCP 37-40, 51-59) ==="
    echo ""

    local total=0
    local passed=0
    local failed=0

    # Check MCP-A2A availability
    if check_mcp_a2a; then
        success "MCP-A2A available for enhanced event processing"
    else
        warn "MCP-A2A not available - using event simulation"
    fi

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
        success "All event-driven patterns validated successfully"
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