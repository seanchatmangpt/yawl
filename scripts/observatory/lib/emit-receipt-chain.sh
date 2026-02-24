#!/usr/bin/env bash
set -euo pipefail
# ==========================================================================
# emit-receipt-chain.sh — Visualize YAWL receipt chains (BBB ledger)
#
# This library provides functions to visualize the audit trail of work items
# through the YAWL engine as receipt chains. The receipt chain shows the
# complete lifecycle of work items from creation to completion.
#
# Functions:
#   - parse_receipt_log file    - Parse receipt log files into structured data
#   - emit_receipt_chain_mermaid - Generate Mermaid diagram of receipt chain
#   - emit_receipt_chain_yawl   - Generate YAWL XML of receipt workflow
#
# Usage:
#   source lib/emit-receipt-chain.sh
#   parse_receipt_log /path/to/receipt.log
#   emit_receipt_chain_mermaid
#   emit_receipt_chain_yawl
# ==========================================================================

# ── Receipt Chain Data Structures ──────────────────────────────────────────
declare -a RECEIPT_CHAIN_ENTRIES=()
declare -A RECEIPT_CHAIN_STATS=()
declare -a RECEIPT_WORK_ITEMS=()

# ── Receipt Entry Fields ────────────────────────────────────────────────────
# Each entry contains:
#   timestamp    - ISO 8601 timestamp of the event
#   case_id      - YAWL case identifier
#   work_item_id - Work item identifier
#   task_name    - Name of the task
#   event_type   - Type of event (created, started, completed, cancelled, failed)
#   status       - Status at time of event
#   performer    - Who/what performed the action
#   data         - Associated data payload (JSON)

# ── Parse Receipt Log ───────────────────────────────────────────────────────
# Parse a receipt log file and populate RECEIPT_CHAIN_ENTRIES array
#
# Arguments:
#   $1 - Path to receipt log file (supports .log, .json, .jsonl formats)
#
# Output:
#   Populates RECEIPT_CHAIN_ENTRIES array with parsed entries
#   Sets RECEIPT_CHAIN_STATS with aggregate statistics
#
# Returns:
#   0 on success, 1 on file not found, 2 on parse error
parse_receipt_log() {
    local log_file="$1"

    # Validate file exists
    if [[ ! -f "$log_file" ]]; then
        log_error "Receipt log file not found: $log_file"
        return 1
    fi

    # Reset state
    RECEIPT_CHAIN_ENTRIES=()
    RECEIPT_WORK_ITEMS=()
    RECEIPT_CHAIN_STATS=(
        ["total_entries"]=0
        ["total_cases"]=0
        ["total_work_items"]=0
        ["events_created"]=0
        ["events_started"]=0
        ["events_completed"]=0
        ["events_cancelled"]=0
        ["events_failed"]=0
    )

    # Track unique cases and work items
    local -A seen_cases=()
    local -A seen_work_items=()

    # Determine file format and parse accordingly
    local file_ext="${log_file##*.}"
    local parse_status=0

    case "$file_ext" in
        json|jsonl)
            parse_json_receipt_log "$log_file" || parse_status=$?
            ;;
        log)
            parse_text_receipt_log "$log_file" || parse_status=$?
            ;;
        *)
            # Try to auto-detect format
            if head -1 "$log_file" | grep -q '^\s*{'; then
                parse_json_receipt_log "$log_file" || parse_status=$?
            else
                parse_text_receipt_log "$log_file" || parse_status=$?
            fi
            ;;
    esac

    if [[ $parse_status -ne 0 ]]; then
        log_error "Failed to parse receipt log: $log_file"
        return 2
    fi

    # Calculate statistics
    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local case_id work_item_id event_type
        case_id=$(echo "$entry" | jq -r '.case_id // empty' 2>/dev/null)
        work_item_id=$(echo "$entry" | jq -r '.work_item_id // empty' 2>/dev/null)
        event_type=$(echo "$entry" | jq -r '.event_type // empty' 2>/dev/null)

        [[ -n "$case_id" ]] && seen_cases["$case_id"]=1
        [[ -n "$work_item_id" ]] && seen_work_items["$work_item_id"]=1

        case "$event_type" in
            created)  ((RECEIPT_CHAIN_STATS[events_created]++)) ;;
            started)  ((RECEIPT_CHAIN_STATS[events_started]++)) ;;
            completed) ((RECEIPT_CHAIN_STATS[events_completed]++)) ;;
            cancelled) ((RECEIPT_CHAIN_STATS[events_cancelled]++)) ;;
            failed)   ((RECEIPT_CHAIN_STATS[events_failed]++)) ;;
        esac
    done

    RECEIPT_CHAIN_STATS[total_entries]=${#RECEIPT_CHAIN_ENTRIES[@]}
    RECEIPT_CHAIN_STATS[total_cases]=${#seen_cases[@]}
    RECEIPT_CHAIN_STATS[total_work_items]=${#seen_work_items[@]}

    RECEIPT_WORK_ITEMS=("${!seen_work_items[@]}")

    log_ok "Parsed ${RECEIPT_CHAIN_STATS[total_entries]} entries from $log_file"
    return 0
}

# ── Parse JSON Receipt Log (internal) ───────────────────────────────────────
parse_json_receipt_log() {
    local log_file="$1"
    local line_num=0

    while IFS= read -r line || [[ -n "$line" ]]; do
        ((line_num++))
        line=$(echo "$line" | tr -d '\r')

        # Skip empty lines
        [[ -z "$line" ]] && continue

        # Skip non-JSON lines
        [[ ! "$line" =~ ^\{ ]] && continue

        # Validate JSON structure
        if echo "$line" | jq -e '.' >/dev/null 2>&1; then
            # Normalize entry format
            local normalized
            normalized=$(normalize_receipt_entry "$line")
            if [[ -n "$normalized" ]]; then
                RECEIPT_CHAIN_ENTRIES+=("$normalized")
            fi
        else
            log_warn "Invalid JSON at line $line_num in $log_file"
        fi
    done < "$log_file"

    return 0
}

# ── Parse Text Receipt Log (internal) ───────────────────────────────────────
parse_text_receipt_log() {
    local log_file="$1"
    local line_num=0

    # Common log patterns to recognize
    # Pattern: TIMESTAMP [LEVEL] case=CASE_ID workitem=WORK_ITEM_ID task=TASK event=EVENT ...
    # Pattern: TIMESTAMP | CASE_ID | WORK_ITEM_ID | TASK_NAME | EVENT_TYPE | ...

    while IFS= read -r line || [[ -n "$line" ]]; do
        ((line_num++))
        line=$(echo "$line" | tr -d '\r')

        # Skip empty lines and comments
        [[ -z "$line" ]] && continue
        [[ "$line" =~ ^[[:space:]]*# ]] && continue

        # Try to extract structured data from text log
        local entry
        entry=$(parse_text_receipt_line "$line")
        if [[ -n "$entry" ]]; then
            RECEIPT_CHAIN_ENTRIES+=("$entry")
        fi
    done < "$log_file"

    return 0
}

# ── Parse Single Text Log Line (internal) ───────────────────────────────────
parse_text_receipt_line() {
    local line="$1"
    local timestamp case_id work_item_id task_name event_type status performer

    # Extract timestamp (ISO 8601 or common formats)
    timestamp=$(echo "$line" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2}[T ][0-9]{2}:[0-9]{2}:[0-9]{2}' | head -1)
    [[ -z "$timestamp" ]] && timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    # Extract case_id
    case_id=$(echo "$line" | grep -oE 'case[=:_]?[A-Za-z0-9_-]+' | sed 's/case[=:_]//' | head -1)
    [[ -z "$case_id" ]] && case_id=$(echo "$line" | grep -oE 'caseId[=:_]?[A-Za-z0-9_-]+' | sed 's/caseId[=:_]//' | head -1)

    # Extract work_item_id
    work_item_id=$(echo "$line" | grep -oE 'workitem[=:_]?[A-Za-z0-9_-]+' | sed 's/workitem[=:_]//' | head -1)
    work_item_id=$(echo "$line" | grep -oE 'work[_-]?item[=:_]?[A-Za-z0-9_-]+' | sed 's/work[_-]?item[=:_]//' | head -1)

    # Extract task name
    task_name=$(echo "$line" | grep -oE 'task[=:_]?[A-Za-z0-9_-]+' | sed 's/task[=:_]//' | head -1)

    # Extract event type
    event_type=$(echo "$line" | grep -oE 'event[=:_]?[A-Za-z_]+' | sed 's/event[=:_]//' | head -1)
    if [[ -z "$event_type" ]]; then
        # Try to infer from common keywords
        if echo "$line" | grep -qi 'created\|added'; then
            event_type="created"
        elif echo "$line" | grep -qi 'started\|begin'; then
            event_type="started"
        elif echo "$line" | grep -qi 'completed\|finished\|done'; then
            event_type="completed"
        elif echo "$line" | grep -qi 'cancelled\|canceled\|aborted'; then
            event_type="cancelled"
        elif echo "$line" | grep -qi 'failed\|error'; then
            event_type="failed"
        fi
    fi

    # Extract status
    status=$(echo "$line" | grep -oE 'status[=:_]?[A-Za-z_]+' | sed 's/status[=:_]//' | head -1)

    # Extract performer
    performer=$(echo "$line" | grep -oE 'performer[=:_]?[A-Za-z0-9_-]+' | sed 's/performer[=:_]//' | head -1)

    # Build JSON entry
    local entry="{"
    entry+="\"timestamp\":\"${timestamp}\""
    [[ -n "$case_id" ]] && entry+=",\"case_id\":\"${case_id}\""
    [[ -n "$work_item_id" ]] && entry+=",\"work_item_id\":\"${work_item_id}\""
    [[ -n "$task_name" ]] && entry+=",\"task_name\":\"${task_name}\""
    [[ -n "$event_type" ]] && entry+=",\"event_type\":\"${event_type}\""
    [[ -n "$status" ]] && entry+=",\"status\":\"${status}\""
    [[ -n "$performer" ]] && entry+=",\"performer\":\"${performer}\""
    entry+="}"

    # Validate we have minimum required fields
    if [[ -n "$case_id" || -n "$work_item_id" || -n "$event_type" ]]; then
        echo "$entry"
    fi
}

# ── Normalize Receipt Entry (internal) ──────────────────────────────────────
normalize_receipt_entry() {
    local entry="$1"

    # Ensure required fields exist with defaults
    local timestamp case_id work_item_id task_name event_type status performer data

    timestamp=$(echo "$entry" | jq -r '.timestamp // .time // .ts // empty' 2>/dev/null)
    [[ -z "$timestamp" ]] && timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    case_id=$(echo "$entry" | jq -r '.case_id // .caseId // .case // empty' 2>/dev/null)
    work_item_id=$(echo "$entry" | jq -r '.work_item_id // .workItemId // .workitem // .item_id // empty' 2>/dev/null)
    task_name=$(echo "$entry" | jq -r '.task_name // .taskName // .task // empty' 2>/dev/null)
    event_type=$(echo "$entry" | jq -r '.event_type // .eventType // .event // .type // empty' 2>/dev/null)
    status=$(echo "$entry" | jq -r '.status // .state // empty' 2>/dev/null)
    performer=$(echo "$entry" | jq -r '.performer // .actor // .user // empty' 2>/dev/null)
    data=$(echo "$entry" | jq -c '.data // .payload // .metadata // empty' 2>/dev/null)

    # Build normalized entry
    local normalized="{\"timestamp\":\"${timestamp}\""
    [[ -n "$case_id" ]] && normalized+=",\"case_id\":\"${case_id}\""
    [[ -n "$work_item_id" ]] && normalized+=",\"work_item_id\":\"${work_item_id}\""
    [[ -n "$task_name" ]] && normalized+=",\"task_name\":\"${task_name}\""
    [[ -n "$event_type" ]] && normalized+=",\"event_type\":\"${event_type}\""
    [[ -n "$status" ]] && normalized+=",\"status\":\"${status}\""
    [[ -n "$performer" ]] && normalized+=",\"performer\":\"${performer}\""
    [[ -n "$data" && "$data" != "null" ]] && normalized+=",\"data\":${data}"
    normalized+="}"

    echo "$normalized"
}

# ── Emit Receipt Chain as Mermaid Diagram ───────────────────────────────────
# Generate a Mermaid flowchart/timeline diagram of the receipt chain
#
# Arguments:
#   $1 - Output file path (optional, defaults to stdout)
#   $2 - Options (optional):
#        - "timeline" - Use timeline diagram format
#        - "sequence" - Use sequence diagram format
#        - "flowchart" - Use flowchart format (default)
#        - "compact" - Compact output (minimal styling)
#
# Output:
#   Mermaid diagram content
emit_receipt_chain_mermaid() {
    local output_file="${1:-}"
    local options="${2:-flowchart}"
    local diagram_type="flowchart"

    # Parse options
    [[ "$options" == *"timeline"* ]] && diagram_type="timeline"
    [[ "$options" == *"sequence"* ]] && diagram_type="sequence"

    # Check if we have data to visualize
    if [[ ${#RECEIPT_CHAIN_ENTRIES[@]} -eq 0 ]]; then
        log_warn "No receipt chain entries to visualize"
        echo "%% No receipt chain data available"
        return 1
    fi

    local mermaid_content

    case "$diagram_type" in
        timeline)
            mermaid_content=$(generate_timeline_diagram)
            ;;
        sequence)
            mermaid_content=$(generate_sequence_diagram)
            ;;
        *)
            mermaid_content=$(generate_flowchart_diagram)
            ;;
    esac

    # Output to file or stdout
    if [[ -n "$output_file" ]]; then
        mkdir -p "$(dirname "$output_file")"
        echo "$mermaid_content" > "$output_file"
        log_ok "Mermaid diagram written to $output_file"
    else
        echo "$mermaid_content"
    fi
}

# ── Generate Flowchart Diagram (internal) ───────────────────────────────────
generate_flowchart_diagram() {
    local diagram="graph TD"
    diagram+=$'\n'"    %% YAWL Receipt Chain Visualization"
    diagram+=$'\n'"    %% Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    diagram+=$'\n'"    %% Total Entries: ${RECEIPT_CHAIN_STATS[total_entries]}"
    diagram+=$'\n'

    # Define styles
    diagram+=$'\n'"    %% Styles"
    diagram+=$'\n'"    classDef created fill:#90EE90,stroke:#228B22,stroke-width:2px"
    diagram+=$'\n'"    classDef started fill:#87CEEB,stroke:#4169E1,stroke-width:2px"
    diagram+=$'\n'"    classDef completed fill:#98FB98,stroke:#006400,stroke-width:2px"
    diagram+=$'\n'"    classDef cancelled fill:#FFB6C1,stroke:#DC143C,stroke-width:2px"
    diagram+=$'\n'"    classDef failed fill:#FF6347,stroke:#8B0000,stroke-width:2px,color:#fff"
    diagram+=$'\n'"    classDef caseNode fill:#E6E6FA,stroke:#483D8B,stroke-width:3px"
    diagram+=$'\n'

    # Track nodes to avoid duplicates
    local -A rendered_nodes=()
    local -A case_nodes=()
    local -a edges=()
    local node_id=0

    # Create case nodes first
    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local case_id
        case_id=$(echo "$entry" | jq -r '.case_id // empty' 2>/dev/null)
        if [[ -n "$case_id" && -z "${case_nodes[$case_id]}" ]]; then
            local safe_case_id
            safe_case_id=$(safe_mermaid_id "case_${case_id}")
            diagram+=$'\n'"    ${safe_case_id}[\"Case: ${case_id}\"]:::caseNode"
            case_nodes["$case_id"]="$safe_case_id"
        fi
    done

    # Create event nodes
    local prev_node=""
    local prev_case=""
    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local timestamp case_id work_item_id task_name event_type performer
        timestamp=$(echo "$entry" | jq -r '.timestamp // empty' 2>/dev/null)
        case_id=$(echo "$entry" | jq -r '.case_id // empty' 2>/dev/null)
        work_item_id=$(echo "$entry" | jq -r '.work_item_id // empty' 2>/dev/null)
        task_name=$(echo "$entry" | jq -r '.task_name // empty' 2>/dev/null)
        event_type=$(echo "$entry" | jq -r '.event_type // empty' 2>/dev/null)
        performer=$(echo "$entry" | jq -r '.performer // empty' 2>/dev/null)

        # Generate unique node ID
        local node_key="${case_id}:${work_item_id}:${event_type}:${node_id}"
        local current_node
        current_node=$(safe_mermaid_id "node_${node_id}")
        ((node_id++))

        # Build node label
        local label="${event_type^^}"
        [[ -n "$task_name" ]] && label+="\nTask: $task_name"
        [[ -n "$work_item_id" ]] && label+="\nItem: $work_item_id"
        [[ -n "$timestamp" ]] && label+="\n$timestamp"
        [[ -n "$performer" ]] && label+="\nby: $performer"

        # Determine style class
        local style_class="$event_type"
        case "$event_type" in
            created) style_class="created" ;;
            started) style_class="started" ;;
            completed) style_class="completed" ;;
            cancelled) style_class="cancelled" ;;
            failed) style_class="failed" ;;
            *) style_class="started" ;;
        esac

        diagram+=$'\n'"    ${current_node}[\"${label}\"]:::${style_class}"

        # Create edge from case node
        if [[ -n "$case_id" && -n "${case_nodes[$case_id]}" ]]; then
            edges+=("${case_nodes[$case_id]} --> ${current_node}")
        fi

        # Create sequential edge if same case
        if [[ -n "$prev_node" && "$case_id" == "$prev_case" ]]; then
            edges+=("${prev_node} --> ${current_node}")
        fi

        prev_node="$current_node"
        prev_case="$case_id"
    done

    # Add edges
    diagram+=$'\n'
    local -A seen_edges=()
    for edge in "${edges[@]}"; do
        if [[ -z "${seen_edges[$edge]}" ]]; then
            diagram+=$'\n'"    $edge"
            seen_edges["$edge"]=1
        fi
    done

    # Add statistics subgraph
    diagram+=$'\n'
    diagram+=$'\n'"    subgraph Statistics"
    diagram+=$'\n'"        STATS[\"Total Entries: ${RECEIPT_CHAIN_STATS[total_entries]}<br/>Cases: ${RECEIPT_CHAIN_STATS[total_cases]}<br/>Work Items: ${RECEIPT_CHAIN_STATS[total_work_items]}<br/>Created: ${RECEIPT_CHAIN_STATS[events_created]}<br/>Started: ${RECEIPT_CHAIN_STATS[events_started]}<br/>Completed: ${RECEIPT_CHAIN_STATS[events_completed]}<br/>Cancelled: ${RECEIPT_CHAIN_STATS[events_cancelled]}<br/>Failed: ${RECEIPT_CHAIN_STATS[events_failed]}\"]"
    diagram+=$'\n'"    end"

    echo "$diagram"
}

# ── Generate Timeline Diagram (internal) ─────────────────────────────────────
generate_timeline_diagram() {
    local diagram="timeline"
    diagram+=$'\n'"    title YAWL Receipt Chain Timeline"
    diagram+=$'\n'"    %% Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    diagram+=$'\n'

    # Group entries by case
    local -A case_entries=()
    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local case_id
        case_id=$(echo "$entry" | jq -r '.case_id // "default"' 2>/dev/null)
        case_entries["$case_id"]+="${entry}"$'\n'
    done

    # Build timeline sections
    for case_id in "${!case_entries[@]}"; do
        diagram+=$'\n'"    section Case: ${case_id}"

        while IFS= read -r entry; do
            [[ -z "$entry" ]] && continue

            local task_name event_type timestamp
            task_name=$(echo "$entry" | jq -r '.task_name // "Unknown Task"' 2>/dev/null)
            event_type=$(echo "$entry" | jq -r '.event_type // "event"' 2>/dev/null)
            timestamp=$(echo "$entry" | jq -r '.timestamp // ""' 2>/dev/null)

            local event_label="${task_name}: ${event_type}"
            [[ -n "$timestamp" ]] && event_label+=" (${timestamp})"

            diagram+=$'\n'"        ${event_label}"
        done <<< "${case_entries[$case_id]}"
    done

    echo "$diagram"
}

# ── Generate Sequence Diagram (internal) ─────────────────────────────────────
generate_sequence_diagram() {
    local diagram="sequenceDiagram"
    diagram+=$'\n'"    autonumber"
    diagram+=$'\n'"    %% YAWL Receipt Chain Sequence"
    diagram+=$'\n'"    %% Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    diagram+=$'\n'

    # Identify participants
    local -A participants=()
    participants["Engine"]="YAWL Engine"

    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local performer
        performer=$(echo "$entry" | jq -r '.performer // empty' 2>/dev/null)
        if [[ -n "$performer" && -z "${participants[$performer]}" ]]; then
            participants["$performer"]="$performer"
        fi
    done

    # Add participant declarations
    for p_id in "${!participants[@]}"; do
        diagram+=$'\n'"    participant ${p_id} as ${participants[$p_id]}"
    done
    diagram+=$'\n'

    # Build sequence messages
    local prev_participant="Engine"
    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local case_id task_name event_type performer timestamp
        case_id=$(echo "$entry" | jq -r '.case_id // "unknown"' 2>/dev/null)
        task_name=$(echo "$entry" | jq -r '.task_name // "task"' 2>/dev/null)
        event_type=$(echo "$entry" | jq -r '.event_type // "event"' 2>/dev/null)
        performer=$(echo "$entry" | jq -r '.performer // "Engine"' 2>/dev/null)
        timestamp=$(echo "$entry" | jq -r '.timestamp // ""' 2>/dev/null)

        local message="${task_name}: ${event_type}"
        [[ -n "$timestamp" ]] && message+=" @ ${timestamp}"

        # Determine arrow direction based on event type
        case "$event_type" in
            created|started)
                diagram+=$'\n'"    Engine->>${performer}: ${message}"
                ;;
            completed)
                diagram+=$'\n'"    ${performer}-->>Engine: ${message}"
                ;;
            cancelled|failed)
                diagram+=$'\n'"    ${performer}-xEngine: ${message}"
                ;;
            *)
                diagram+=$'\n'"    Engine->>${performer}: ${message}"
                ;;
        esac
        prev_participant="$performer"
    done

    # Add note with statistics
    diagram+=$'\n'"    Note over Engine: Total: ${RECEIPT_CHAIN_STATS[total_entries]} entries, ${RECEIPT_CHAIN_STATS[total_cases]} cases"

    echo "$diagram"
}

# ── Emit Receipt Chain as YAWL XML ──────────────────────────────────────────
# Generate a YAWL XML specification representing the receipt workflow
#
# Arguments:
#   $1 - Output file path (optional, defaults to stdout)
#   $2 - Specification URI (optional)
#
# Output:
#   YAWL XML specification
emit_receipt_chain_yawl() {
    local output_file="${1:-}"
    local spec_uri="${2:-http://yawlfoundation.org/yawl/receipt-chain}"

    # Check if we have data
    if [[ ${#RECEIPT_CHAIN_ENTRIES[@]} -eq 0 ]]; then
        log_warn "No receipt chain entries to generate YAWL XML"
        echo '<?xml version="1.0" encoding="UTF-8"?>'
        echo '<!-- No receipt chain data available -->'
        return 1
    fi

    local yawl_xml
    yawl_xml=$(generate_yawl_specification "$spec_uri")

    if [[ -n "$output_file" ]]; then
        mkdir -p "$(dirname "$output_file")"
        echo "$yawl_xml" > "$output_file"
        log_ok "YAWL XML written to $output_file"
    else
        echo "$yawl_xml"
    fi
}

# ── Generate YAWL Specification (internal) ───────────────────────────────────
generate_yawl_specification() {
    local spec_uri="$1"
    local spec_id="ReceiptChain_$(date +%Y%m%d%H%M%S)"

    # Extract unique tasks and their transitions
    local -A tasks=()
    local -a task_order=()
    local -A transitions=()

    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        local task_name case_id event_type
        task_name=$(echo "$entry" | jq -r '.task_name // "UnknownTask"' 2>/dev/null)
        case_id=$(echo "$entry" | jq -r '.case_id // "default"' 2>/dev/null)
        event_type=$(echo "$entry" | jq -r '.event_type // ""' 2>/dev/null)

        local task_key="${case_id}:${task_name}"
        if [[ -z "${tasks[$task_key]}" ]]; then
            tasks["$task_key"]="$task_name"
            task_order+=("$task_key")
        fi

        # Track transitions
        if [[ -n "$event_type" ]]; then
            transitions["${task_key}:${event_type}"]=1
        fi
    done

    # Build XML
    local xml='<?xml version="1.0" encoding="UTF-8"?>'
    xml+=$'\n''<specificationSet xmlns="http://yawlfoundation.org/yawlschema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://yawlfoundation.org/yawlschema http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">'
    xml+=$'\n'"  <specification uri=\"${spec_uri}\" id=\"${spec_id}\">"
    xml+=$'\n'"    <name>Receipt Chain Workflow</name>"
    xml+=$'\n'"    <description>Auto-generated YAWL specification from receipt chain audit trail. Total entries: ${RECEIPT_CHAIN_STATS[total_entries]}, Cases: ${RECEIPT_CHAIN_STATS[total_cases]}.</description>"
    xml+=$'\n'"    <metaData>"
    xml+=$'\n'"      <creator>YAWL Observatory Receipt Chain Generator</creator>"
    xml+=$'\n'"      <version>1.0</version>"
    xml+=$'\n'"      <generated>$(date -u +%Y-%m-%dT%H:%M:%SZ)</generated>"
    xml+=$'\n'"    </metaData>"
    xml+=$'\n'"    <rootNet id=\"ReceiptChainNet\">"
    xml+=$'\n'"      <name>Receipt Chain Net</name>"
    xml+=$'\n'"      <inputPool/>"
    xml+=$'\n'"      <outputPool/>"

    # Add input condition
    xml+=$'\n'"      <inputCondition id=\"input\">"
    xml+=$'\n'"        <name>Start</name>"
    xml+=$'\n'"        <flowsInto>"
    xml+=$'\n'"          <nextElementRef id=\"task_0\"/>"
    xml+=$'\n'"        </flowsInto>"
    xml+=$'\n'"      </inputCondition>"

    # Add tasks
    local task_idx=0
    local prev_task_id="input"
    for task_key in "${task_order[@]}"; do
        local task_name="${tasks[$task_key]}"
        local task_id="task_${task_idx}"
        local next_task_id="task_$((task_idx + 1))"

        # Determine task configuration based on events
        local split_type=""
        local join_type=""
        if [[ -n "${transitions[${task_key}:completed]}" && -n "${transitions[${task_key}:cancelled]}" ]]; then
            split_type="XOR"
        elif [[ -n "${transitions[${task_key}:started]}" && -n "${transitions[${task_key}:created]}" ]]; then
            join_type="AND"
        fi

        xml+=$'\n'"      <task id=\"${task_id}\">"
        xml+=$'\n'"        <name>${task_name}</name>"
        xml+=$'\n'"        <description>Task from receipt chain: ${task_name}</description>"

        # Add decomposition reference
        xml+=$'\n'"        <decomposesTo id=\"decomp_${task_id}\"/>"

        # Add flow into task
        if [[ $task_idx -eq 0 ]]; then
            : # First task already connected from input condition
        else
            xml+=$'\n'"        <flowsInto>"
            xml+=$'\n'"          <nextElementRef id=\"${task_id}\"/>"
            xml+=$'\n'"        </flowsInto>"
        fi

        # Add flow out of task
        if [[ $task_idx -lt $((${#task_order[@]} - 1)) ]]; then
            xml+=$'\n'"        <flowsInto>"
            xml+=$'\n'"          <nextElementRef id=\"${next_task_id}\"/>"
            xml+=$'\n'"        </flowsInto>"
        else
            # Last task connects to output condition
            xml+=$'\n'"        <flowsInto>"
            xml+=$'\n'"          <nextElementRef id=\"output\"/>"
            xml+=$'\n'"        </flowsInto>"
        fi

        # Add split/join if determined
        [[ -n "$split_type" ]] && xml+=$'\n'"        <split code=\"${split_type}\"/>"
        [[ -n "$join_type" ]] && xml+=$'\n'"        <join code=\"${join_type}\"/>"

        xml+=$'\n'"      </task>"

        prev_task_id="$task_id"
        ((task_idx++))
    done

    # Add output condition
    xml+=$'\n'"      <outputCondition id=\"output\">"
    xml+=$'\n'"        <name>End</name>"
    xml+=$'\n'"      </outputCondition>"

    xml+=$'\n'"    </rootNet>"

    # Add decomposition for each task
    task_idx=0
    for task_key in "${task_order[@]}"; do
        local task_name="${tasks[$task_key]}"
        local decomp_id="decomp_task_${task_idx}"

        xml+=$'\n'"    <decomposition id=\"${decomp_id}\" type=\"WebServiceGatewayDecomposition\">"
        xml+=$'\n'"      <name>${task_name} Decomposition</name>"
        xml+=$'\n'"      <description>Decomposition for task: ${task_name}</description>"
        xml+=$'\n'"      <inputParam name=\"case_id\" type=\"string\">"
        xml+=$'\n'"        <description>Case identifier</description>"
        xml+=$'\n'"      </inputParam>"
        xml+=$'\n'"      <inputParam name=\"work_item_id\" type=\"string\">"
        xml+=$'\n'"        <description>Work item identifier</description>"
        xml+=$'\n'"      </inputParam>"
        xml+=$'\n'"      <outputParam name=\"status\" type=\"string\">"
        xml+=$'\n'"        <description>Task completion status</description>"
        xml+=$'\n'"      </outputParam>"
        xml+=$'\n'"      <outputParam name=\"timestamp\" type=\"string\">"
        xml+=$'\n'"        <description>Completion timestamp</description>"
        xml+=$'\n'"      </outputParam>"
        xml+=$'\n'"    </decomposition>"

        ((task_idx++))
    done

    xml+=$'\n'"  </specification>"
    xml+=$'\n'"</specificationSet>"

    echo "$xml"
}

# ── Safe Mermaid ID Generator (internal) ─────────────────────────────────────
safe_mermaid_id() {
    local input="$1"
    # Replace non-alphanumeric characters with underscores
    # Ensure starts with letter
    local safe_id
    safe_id=$(echo "$input" | sed 's/[^a-zA-Z0-9]/_/g' | sed 's/^[0-9]/_&/')
    echo "$safe_id"
}

# ── Get Receipt Chain Summary ────────────────────────────────────────────────
# Return a JSON summary of the current receipt chain state
get_receipt_chain_summary() {
    local summary="{"
    summary+="\"total_entries\":${RECEIPT_CHAIN_STATS[total_entries]}"
    summary+=",\"total_cases\":${RECEIPT_CHAIN_STATS[total_cases]}"
    summary+=",\"total_work_items\":${RECEIPT_CHAIN_STATS[total_work_items]}"
    summary+=",\"events\":{"
    summary+="\"created\":${RECEIPT_CHAIN_STATS[events_created]}"
    summary+=",\"started\":${RECEIPT_CHAIN_STATS[events_started]}"
    summary+=",\"completed\":${RECEIPT_CHAIN_STATS[events_completed]}"
    summary+=",\"cancelled\":${RECEIPT_CHAIN_STATS[events_cancelled]}"
    summary+=",\"failed\":${RECEIPT_CHAIN_STATS[events_failed]}"
    summary+="}"
    summary+="}"
    echo "$summary"
}

# ── Export Receipt Chain as JSON ─────────────────────────────────────────────
# Export all parsed entries as a JSON array
export_receipt_chain_json() {
    local output_file="${1:-}"

    local json="["
    local first=true
    for entry in "${RECEIPT_CHAIN_ENTRIES[@]}"; do
        $first || json+=","
        first=false
        json+=$'\n'"  ${entry}"
    done
    json+=$'\n'"]"

    if [[ -n "$output_file" ]]; then
        mkdir -p "$(dirname "$output_file")"
        echo "$json" > "$output_file"
        log_ok "Receipt chain JSON exported to $output_file"
    else
        echo "$json"
    fi
}

# ── Library initialization message ───────────────────────────────────────────
log_info "emit-receipt-chain.sh library loaded"
