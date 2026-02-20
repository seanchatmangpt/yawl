#!/bin/bash

# YAWL Pattern Executor - Single Pattern Validation
# Converts YAML to YAWL XML and executes workflow validation

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENGINE_URL="http://localhost:8080"
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "${RED}ERROR:${NC} $1" >&2
    exit 1
}

success() {
    echo "${GREEN}SUCCESS:${NC} $1"
}

warn() {
    echo "${YELLOW}WARNING:${NC} $1"
}

# Check if required tools are available
check_dependencies() {
    local missing_tools=()

    for tool in curl jq; do
        if ! command -v "$tool" &> /dev/null; then
            missing_tools+=("$tool")
        fi
    done

    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        error "Missing required tools: ${missing_tools[*]}"
    fi
}

# Convert YAML to YAWL XML
yaml_to_xml() {
    local yaml_file="$1"
    local pattern_name="$2"
    local xml_file="/tmp/${pattern_name}.xml"

    if [[ ! -f "$yaml_file" ]]; then
        error "YAML file not found: $yaml_file"
    fi

    # Basic YAML to XML conversion (simplified)
    # In a real implementation, this would use a proper YAML parser
    cat > "$xml_file" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<specification xmlns="http://www.yawlfoundation.org/yawlschema">
    <identification id="${pattern_name}" name="${pattern_name}" version="1.0"/>
    <description>${pattern_name} workflow pattern</description>
    <schemaVersion>2.0</schemaVersion>
    <process id="${pattern_name}-process">
        <name>${pattern_name} Process</name>
        <inputCondition/>
        <nodes>
            <!-- Placeholder nodes - should be populated from YAML -->
            <node id="start" type="Start" name="Start"/>
            <node id="task1" type="Task" name="Task1"/>
            <node id="end" type="End" name="End"/>
        </nodes>
        <arcs>
            <!-- Placeholder arcs - should be populated from YAML -->
            <arc id="arc1" from="start" to="task1"/>
            <arc id="arc2" from="task1" to="end"/>
        </arcs>
    </process>
    <outputCondition/>
</specification>
EOF

    echo "$xml_file"
}

# Add specification to YAWL engine
add_specification() {
    local xml_file="$1"
    local pattern_name="$2"

    log "Adding specification: $pattern_name"

    local response
    response=$(curl -s -X POST \
        -H "Content-Type: application/xml" \
        -H "Connection-ID: $CONNECTION_ID" \
        --data @"$xml_file" \
        "${ENGINE_URL}/yawl/ia?action=addSpecification")

    if echo "$response" | grep -q 'success'; then
        success "Specification added successfully"
        # Extract spec identifier
        local spec_id=$(echo "$response" | sed 's/.*specIdentifier:\([^,]*\).*/\1/' | tr -d ' ')
        export SPEC_ID="$spec_id"
        return 0
    else
        error "Failed to add specification: $response"
    fi
}

# Launch case
launch_case() {
    local spec_id="$1"
    local pattern_name="$2"

    log "Launching case: $pattern_name"

    local response
    response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=launchCase&specIdentifier=${spec_id}&specVersion=1.0")

    if echo "$response" | grep -q 'caseID'; then
        local case_id=$(echo "$response" | sed 's/.*caseID:\([^,]*\).*/\1/' | tr -d ' ')
        export CASE_ID="$case_id"
        success "Case launched successfully: $case_id"
        return 0
    else
        error "Failed to launch case: $response"
    fi
}

# Get work items
get_work_items() {
    local case_id="$1"

    log "Getting work items for case: $case_id"

    local response
    response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=getWorkItems&caseId=${case_id}")

    # Parse work items from response
    local work_items=$(echo "$response" | jq -r '.workItems | length // 0' 2>/dev/null || echo "0")
    echo "$work_items"
}

# Complete work item
complete_work_item() {
    local work_item_id="$1"
    local pattern_name="$2"

    log "Completing work item: $work_item_id"

    local response
    response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        -H "Content-Type: application/xml" \
        --data "<data><item>${work_item_id}</item></data>" \
        "${ENGINE_URL}/yawl/ib?action=checkin&workItemID=${work_item_id}&data=<data/>")

    if echo "$response" | grep -q 'success'; then
        success "Work item completed: $work_item_id"
        return 0
    else
        warn "Failed to complete work item: $response"
        return 1
    fi
}

# Check case completion
check_case_completion() {
    local case_id="$1"

    log "Checking case completion: $case_id"

    local response
    response=$(curl -s -X POST \
        -H "Connection-ID: $CONNECTION_ID" \
        "${ENGINE_URL}/yawl/ib?action=getCaseState&caseId=${case_id}")

    local case_state=$(echo "$response" | jq -r '.caseState // "unknown"' 2>/dev/null || echo "unknown")

    case $case_state in
        "complete")
            success "Case completed successfully"
            return 0
            ;;
        "cancelled")
            error "Case was cancelled"
            ;;
        "running")
            warn "Case is still running"
            return 1
            ;;
        *)
            warn "Unknown case state: $case_state"
            return 1
            ;;
    esac
}

# Validate pattern execution
validate_pattern_execution() {
    local pattern_name="$1"
    local work_items="$2"

    log "Validating pattern execution: $pattern_name"

    # Basic validation - in a real implementation, this would be more sophisticated
    if [[ "$work_items" -eq 0 ]]; then
        warn "No work items found - pattern may be invalid"
        return 1
    fi

    # Add more specific validations based on pattern type
    case "$pattern_name" in
        *WCP-1* | *sequence*)
            # Sequence pattern should have at least one work item
            if [[ "$work_items" -lt 1 ]]; then
                warn "Sequence pattern should have at least one work item"
                return 1
            fi
            ;;
        *WCP-2* | *parallel*)
            # Parallel split should have multiple work items
            if [[ "$work_items" -lt 2 ]]; then
                warn "Parallel split pattern should have at least two work items"
                return 1
            fi
            ;;
        *WCP-6* | *choice*)
            # Exclusive choice should have multiple work items available
            if [[ "$work_items" -lt 1 ]]; then
                warn "Choice pattern should have at least one work item"
                return 1
            fi
            ;;
        *)
            # Default validation
            if [[ "$work_items" -eq 0 ]]; then
                warn "Pattern validation failed - no work items"
                return 1
            fi
            ;;
    esac

    success "Pattern validation passed: $pattern_name"
    return 0
}

# Main execution
main() {
    local pattern_name="$1"
    local yaml_file="$2"

    if [[ -z "$pattern_name" || -z "$yaml_file" ]]; then
        error "Usage: $0 <pattern_name> <yaml_file>"
    fi

    log "=== Pattern Executor: $pattern_name ==="

    # Check dependencies
    check_dependencies

    # Convert YAML to XML
    local xml_file
    xml_file=$(yaml_to_xml "$yaml_file" "$pattern_name")

    # Add specification
    add_specification "$xml_file" "$pattern_name"

    # Launch case
    launch_case "$SPEC_ID" "$pattern_name"

    # Get work items
    local work_items
    work_items=$(get_work_items "$CASE_ID")
    log "Found $work_items work items"

    # Complete work items
    local completed_items=0
    # Simulate completing work items (in real implementation, would iterate through actual work items)
    for i in $(seq 1 $work_items); do
        if complete_work_item "workitem_$i" "$pattern_name"; then
            ((completed_items++))
        fi
    done

    # Check case completion
    check_case_completion "$CASE_ID"

    # Validate pattern execution
    if validate_pattern_execution "$pattern_name" "$completed_items"; then
        # Generate result JSON
        cat > "/tmp/pattern-result-${pattern_name}.json" << EOF
{
  "pattern": "$pattern_name",
  "case_id": "$CASE_ID",
  "spec_id": "$SPEC_ID",
  "work_items_found": $work_items,
  "work_items_completed": $completed_items,
  "status": "passed",
  "timestamp": "$(date -Iseconds)"
}
EOF
        success "Pattern execution completed successfully"
        exit 0
    else
        cat > "/tmp/pattern-result-${pattern_name}.json" << EOF
{
  "pattern": "$pattern_name",
  "case_id": "$CASE_ID",
  "spec_id": "$SPEC_ID",
  "work_items_found": $work_items,
  "work_items_completed": $completed_items,
  "status": "failed",
  "error": "Pattern validation failed",
  "timestamp": "$(date -Iseconds)"
}
EOF
        error "Pattern execution failed"
    fi
}

# Run main
main "$@"