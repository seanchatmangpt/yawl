#!/usr/bin/env bash
# ==========================================================================
# emit-receipts.sh — Receipt Chain Visualization and Lineage Tracking
#
# Generates receipt chain visualizations showing parent-child hash
# relationships, creates Mermaid graphs of receipt lineage, and exports
# to JSON for programmatic access.
#
# Integration: sourced by observatory.sh
# Usage (standalone):
#   source lib/util.sh && source lib/emit-receipts.sh
#   emit_receipt_chain              # Generate all receipt chain outputs
#   emit_receipt_lineage_mermaid    # Mermaid graph only
#   emit_receipt_lineage_json       # JSON export only
# ==========================================================================

# ── Receipt Chain Constants ─────────────────────────────────────────────────
RECEIPT_HISTORY_DIR="${REPO_ROOT}/docs/v6/receipt-history"
RECEIPT_CHAIN_FILE="${RECEIPTS_DIR}/receipt-chain.json"
RECEIPT_LINEAGE_MMD="${DIAGRAMS_DIR}/60-receipt-lineage.mmd"
RECEIPT_LINEAGE_JSON="${RECEIPTS_DIR}/lineage.json"
RECEIPT_FAMILY_TREE="${RECEIPTS_DIR}/family-tree.json"

# ── Receipt Chain Data Structures ───────────────────────────────────────────
declare -A RECEIPT_NODES=()          # run_id -> receipt_data_json
declare -A RECEIPT_PARENTS=()        # run_id -> parent_run_id
declare -A RECEIPT_CHILDREN=()       # run_id -> child_run_ids (comma-separated)
declare -a RECEIPT_ORDER=()          # Chronological run_id list

# ── Core: Compute Receipt Hash Chain ────────────────────────────────────────
# Creates cryptographic binding between receipts using SHA-256
compute_receipt_chain_hash() {
    local receipt_file="$1"

    if [[ ! -f "$receipt_file" ]]; then
        echo "sha256:missing"
        return 1
    fi

    # Extract parent hash if exists, then compute chain hash
    local content_hash parent_hash chain_input
    content_hash=$(sha256_of_file "$receipt_file" | sed 's/sha256://')

    # Check for parent reference in receipt
    parent_hash=$(grep -oP '(?<="parent_hash":\s*")[^"]+' "$receipt_file" 2>/dev/null || echo "")

    if [[ -n "$parent_hash" ]]; then
        # Chain hash = SHA256(parent_hash + content_hash)
        chain_input="${parent_hash}${content_hash}"
        if command -v sha256sum >/dev/null 2>&1; then
            echo "sha256:$(echo -n "$chain_input" | sha256sum | awk '{print $1}')"
        elif command -v shasum >/dev/null 2>&1; then
            echo "sha256:$(echo -n "$chain_input" | shasum -a 256 | awk '{print $1}')"
        else
            echo "sha256:${content_hash}"
        fi
    else
        echo "sha256:${content_hash}"
    fi
}

# ── Core: Extract Receipt Metadata ──────────────────────────────────────────
extract_receipt_metadata() {
    local receipt_file="$1"

    if [[ ! -f "$receipt_file" ]]; then
        echo "{}"
        return 1
    fi

    # Extract key fields using lightweight parsing
    local run_id status git_commit git_branch timestamp parent_hash
    run_id=$(grep -oP '(?<="run_id":\s*")[^"]+' "$receipt_file" 2>/dev/null || echo "unknown")
    status=$(grep -oP '(?<="status":\s*")[^"]+' "$receipt_file" 2>/dev/null || echo "unknown")
    git_commit=$(grep -oP '(?<="commit":\s*")[^"]+' "$receipt_file" 2>/dev/null | head -1 || echo "unknown")
    git_branch=$(grep -oP '(?<="branch":\s*")[^"]+' "$receipt_file" 2>/dev/null | head -1 || echo "unknown")
    timestamp=$(grep -oP '(?<="timestamp":\s*")[^"]+' "$receipt_file" 2>/dev/null || \
                date -r "$receipt_file" -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo "unknown")
    parent_hash=$(grep -oP '(?<="parent_hash":\s*")[^"]+' "$receipt_file" 2>/dev/null || echo "")

    # Build JSON object
    cat << METADATA_EOF
{
  "run_id": "$(json_escape "$run_id")",
  "status": "$(json_escape "$status")",
  "git_commit": "$(json_escape "$git_commit")",
  "git_branch": "$(json_escape "$git_branch")",
  "timestamp": "$(json_escape "$timestamp")",
  "parent_hash": "$(json_escape "$parent_hash")",
  "file_path": "$(json_escape "$receipt_file")",
  "content_hash": $(sha256_of_file "$receipt_file")
}
METADATA_EOF
}

# ── Core: Discover Receipt History ──────────────────────────────────────────
discover_receipt_history() {
    log_info "Discovering receipt history..."

    # Initialize arrays
    RECEIPT_NODES=()
    RECEIPT_PARENTS=()
    RECEIPT_CHILDREN=()
    RECEIPT_ORDER=()

    # Check current receipt
    local current_receipt="$RECEIPTS_DIR/observatory.json"
    if [[ -f "$current_receipt" ]]; then
        local metadata
        metadata=$(extract_receipt_metadata "$current_receipt")
        local run_id
        run_id=$(echo "$metadata" | grep -oP '(?<="run_id":\s*")[^"]+' | head -1)

        RECEIPT_NODES["$run_id"]="$metadata"
        RECEIPT_ORDER+=("$run_id")
    fi

    # Scan receipt history directory
    if [[ -d "$RECEIPT_HISTORY_DIR" ]]; then
        local -a history_files=()
        while IFS= read -r -d '' file; do
            history_files+=("$file")
        done < <(find "$RECEIPT_HISTORY_DIR" -name "*.json" -type f -print0 2>/dev/null | sort -z)

        for hist_file in "${history_files[@]}"; do
            local metadata run_id
            metadata=$(extract_receipt_metadata "$hist_file")
            run_id=$(echo "$metadata" | grep -oP '(?<="run_id":\s*")[^"]+' | head -1)

            if [[ -n "$run_id" && "$run_id" != "unknown" ]]; then
                RECEIPT_NODES["$run_id"]="$metadata"
                RECEIPT_ORDER+=("$run_id")
            fi
        done
    fi

    log_ok "Discovered ${#RECEIPT_ORDER[@]} receipts in chain"
}

# ── Core: Build Parent-Child Relationships ──────────────────────────────────
build_receipt_relationships() {
    log_info "Building parent-child relationships..."

    # Reset children map
    RECEIPT_CHILDREN=()

    # Sort receipts by timestamp to establish lineage
    local -a sorted_receipts=()
    for run_id in "${RECEIPT_ORDER[@]}"; do
        sorted_receipts+=("$run_id")
    done

    # Sort by timestamp (extracted from run_id which is ISO format)
    IFS=$'\n' sorted_receipts=($(printf '%s\n' "${sorted_receipts[@]}" | sort -r))
    unset IFS

    # Build parent-child links based on git commit history and timestamps
    local prev_run_id=""
    for run_id in "${sorted_receipts[@]}"; do
        if [[ -n "$prev_run_id" ]]; then
            RECEIPT_PARENTS["$run_id"]="$prev_run_id"

            # Add to parent's children list
            local existing_children="${RECEIPT_CHILDREN[$prev_run_id]:-}"
            if [[ -n "$existing_children" ]]; then
                RECEIPT_CHILDREN["$prev_run_id"]="${existing_children},${run_id}"
            else
                RECEIPT_CHILDREN["$prev_run_id"]="$run_id"
            fi
        fi
        prev_run_id="$run_id"
    done

    log_ok "Built relationships for ${#RECEIPT_PARENTS[@]} receipt links"
}

# ── Output: Generate Receipt Chain JSON ─────────────────────────────────────
emit_receipt_chain_json() {
    local out="$RECEIPT_CHAIN_FILE"
    log_info "Emitting receipt chain JSON to $out..."

    mkdir -p "$(dirname "$out")"

    # Build nodes array
    local nodes_json=""
    local first=true
    for run_id in "${RECEIPT_ORDER[@]}"; do
        local node_data="${RECEIPT_NODES[$run_id]}"
        if [[ -n "$node_data" ]]; then
            $first || nodes_json+=","
            first=false
            nodes_json+=$'\n'"    ${node_data}"
        fi
    done

    # Build edges array (parent-child relationships)
    local edges_json=""
    first=true
    for run_id in "${!RECEIPT_PARENTS[@]}"; do
        local parent_id="${RECEIPT_PARENTS[$run_id]}"
        $first || edges_json+=","
        first=false
        edges_json+=$'\n'"    {"$'\n'"      "from": "$(json_escape "$parent_id")",$'\n'"      "to": "$(json_escape "$run_id")",$'\n'"      "type": "parent_child"$'\n'"    }"
    done

    # Compute chain root hash
    local chain_root_hash="genesis"
    if [[ ${#RECEIPT_ORDER[@]} -gt 0 ]]; then
        local newest="${RECEIPT_ORDER[0]}"
        chain_root_hash=$(compute_receipt_chain_hash "${RECEIPT_NODES[$newest]#*file_path\": \"}" 2>/dev/null || echo "computed")
    fi

    cat > "$out" << CHAIN_EOF
{
  "chain_id": "yawl-v6-receipt-chain",
  "version": "1.0.0",
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "chain_root_hash": "$(json_escape "$chain_root_hash")",
  "node_count": ${#RECEIPT_ORDER[@]},
  "edge_count": ${#RECEIPT_PARENTS[@]},
  "nodes": [${nodes_json}
  ],
  "edges": [${edges_json}
  ],
  "metadata": {
    "repository": "$(git_escape "$REPO_ROOT")",
    "branch": "$(git_branch)",
    "commit": "$(git_commit)"
  }
}
CHAIN_EOF

    log_ok "Receipt chain JSON written: ${#RECEIPT_ORDER[@]} nodes, ${#RECEIPT_PARENTS[@]} edges"
}

# ── Output: Generate Mermaid Receipt Lineage Graph ───────────────────────────
emit_receipt_lineage_mermaid() {
    local out="$RECEIPT_LINEAGE_MMD"
    log_info "Emitting receipt lineage Mermaid diagram to $out..."

    mkdir -p "$(dirname "$out")"

    cat > "$out" << MMD_HEADER
%%{init: {'theme': 'base', 'themeVariables': { 'primaryColor': '#1a73e8', 'edgeLabelBackground': '#ffffff'}}}%%
graph TB
    subgraph Receipt_Chain["Receipt Chain Lineage"]
        direction TB
MMD_HEADER

    # Add nodes with status-based styling
    for run_id in "${RECEIPT_ORDER[@]}"; do
        local node_data="${RECEIPT_NODES[$run_id]}"
        local status git_commit short_id

        status=$(echo "$node_data" | grep -oP '(?<="status":\s*")[^"]+' | head -1 || echo "unknown")
        git_commit=$(echo "$node_data" | grep -oP '(?<="git_commit":\s*")[^"]+' | head -1 || echo "unknown")
        short_id="${run_id:0:12}"

        # Determine node style based on status
        local node_style=""
        case "$status" in
            GREEN)  node_style=':::greenNode' ;;
            YELLOW) node_style=':::yellowNode' ;;
            RED)    node_style=':::redNode' ;;
            *)      node_style=':::unknownNode' ;;
        esac

        # Safe node ID (replace special chars)
        local safe_id
        safe_id=$(echo "$run_id" | tr -c 'a-zA-Z0-9' '_')

        echo "        ${safe_id}[\"${short_id}<br/>${git_commit}<br/>${status}\"]${node_style}" >> "$out"
    done

    # Close subgraph
    echo "    end" >> "$out"

    # Add edges (parent -> child relationships)
    echo "" >> "$out"
    echo "    %% Parent-Child Relationships" >> "$out"
    for run_id in "${!RECEIPT_PARENTS[@]}"; do
        local parent_id="${RECEIPT_PARENTS[$run_id]}"
        local safe_from safe_to
        safe_from=$(echo "$parent_id" | tr -c 'a-zA-Z0-9' '_')
        safe_to=$(echo "$run_id" | tr -c 'a-zA-Z0-9' '_')
        echo "    ${safe_from} -->|derives| ${safe_to}" >> "$out"
    done

    # Add styling classes
    cat >> "$out" << MMD_STYLES

    %% Node Styles
    classDef greenNode fill:#d4edda,stroke:#28a745,stroke-width:2px,color:#155724
    classDef yellowNode fill:#fff3cd,stroke:#ffc107,stroke-width:2px,color:#856404
    classDef redNode fill:#f8d7da,stroke:#dc3545,stroke-width:2px,color:#721c24
    classDef unknownNode fill:#e2e3e5,stroke:#6c757d,stroke-width:2px,color:#383d41

    %% Click handlers for interactivity
    click ${run_id%% *}_node "receipts/lineage.json" "View lineage JSON"
MMD_STYLES

    log_ok "Receipt lineage Mermaid diagram written"
}

# ── Output: Generate Lineage JSON for Programmatic Access ───────────────────
emit_receipt_lineage_json() {
    local out="$RECEIPT_LINEAGE_JSON"
    log_info "Emitting receipt lineage JSON to $out..."

    mkdir -p "$(dirname "$out")"

    # Build lineage entries with full ancestry
    local lineage_entries=""
    local first=true

    for run_id in "${RECEIPT_ORDER[@]}"; do
        local node_data="${RECEIPT_NODES[$run_id]}"
        local parent_id="${RECEIPT_PARENTS[$run_id]:-}"
        local children="${RECEIPT_CHILDREN[$run_id]:-}"

        # Build children array
        local children_json="[]"
        if [[ -n "$children" ]]; then
            children_json="["
            local child_first=true
            IFS=',' read -ra child_arr <<< "$children"
            for child in "${child_arr[@]}"; do
                $child_first || children_json+=","
                child_first=false
                children_json+="$(json_str "$child")"
            done
            children_json+="]"
        fi

        # Compute depth in chain
        local depth=0
        local current="$parent_id"
        while [[ -n "$current" && "$current" != "genesis" ]]; do
            ((depth++))
            current="${RECEIPT_PARENTS[$current]:-}"
        done

        $first || lineage_entries+=","
        first=false

        lineage_entries+=$'\n'"    {"
        lineage_entries+=$'\n'"      "receipt": ${node_data},"
        lineage_entries+=$'\n'"      "parent_run_id": $(json_str "$parent_id"),"
        lineage_entries+=$'\n'"      "children": ${children_json},"
        lineage_entries+=$'\n'"      "depth": ${depth},"
        lineage_entries+=$'\n'"      "chain_hash": $(compute_receipt_chain_hash "${node_data#*file_path\": \"}" 2>/dev/null || echo '"sha256:computed"')
        lineage_entries+=$'\n'"    }"
    done

    cat > "$out" << LINEAGE_EOF
{
  "lineage_id": "yawl-v6-receipt-lineage",
  "version": "1.0.0",
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "repository": {
    "path": "$(json_escape "$REPO_ROOT")",
    "branch": "$(git_branch)",
    "commit": "$(git_commit)"
  },
  "chain_statistics": {
    "total_receipts": ${#RECEIPT_ORDER[@]},
    "total_edges": ${#RECEIPT_PARENTS[@]},
    "max_depth": $(compute_max_depth),
    "root_receipts": $(count_root_receipts),
    "leaf_receipts": $(count_leaf_receipts)
  },
  "lineage": [${lineage_entries}
  ]
}
LINEAGE_EOF

    log_ok "Receipt lineage JSON written"
}

# ── Output: Generate Family Tree Visualization ──────────────────────────────
emit_receipt_family_tree() {
    local out="$RECEIPT_FAMILY_TREE"
    log_info "Emitting receipt family tree to $out..."

    mkdir -p "$(dirname "$out")"

    # Build tree structure
    local tree_json=""
    local processed_ids=""

    # Find root receipts (no parent)
    local -a roots=()
    for run_id in "${RECEIPT_ORDER[@]}"; do
        if [[ -z "${RECEIPT_PARENTS[$run_id]:-}" ]]; then
            roots+=("$run_id")
        fi
    done

    # Build tree recursively
    build_tree_node() {
        local run_id="$1"
        local indent="$2"

        local node_data="${RECEIPT_NODES[$run_id]}"
        local children="${RECEIPT_CHILDREN[$run_id]:-}"
        local status short_id

        status=$(echo "$node_data" | grep -oP '(?<="status":\s*")[^"]+' | head -1 || echo "unknown")
        short_id="${run_id:0:12}"

        local node_json="${indent}{"
        node_json+=$'\n'"${indent}  \"id\": $(json_str "$run_id"),"
        node_json+=$'\n'"${indent}  \"short_id\": $(json_str "$short_id"),"
        node_json+=$'\n'"${indent}  \"status\": $(json_str "$status"),"

        # Add children
        if [[ -n "$children" ]]; then
            node_json+=$'\n'"${indent}  \"children\": ["
            local child_first=true
            IFS=',' read -ra child_arr <<< "$children"
            for child in "${child_arr[@]}"; do
                $child_first || node_json+=","
                child_first=false
                node_json+=$'\n'"$(build_tree_node "$child" "${indent}    ")"
            done
            node_json+=$'\n'"${indent}  ]"
        else
            node_json+=$'\n'"${indent}  \"children\": []"
        fi

        node_json+=$'\n'"${indent}}"
        echo "$node_json"
    }

    # Build forest (multiple roots possible)
    tree_json="["
    local root_first=true
    for root_id in "${roots[@]}"; do
        $root_first || tree_json+=","
        root_first=false
        tree_json+=$'\n'"$(build_tree_node "$root_id" "  ")"
    done
    tree_json+=$'\n'"]"

    cat > "$out" << TREE_EOF
{
  "tree_id": "yawl-v6-receipt-family-tree",
  "version": "1.0.0",
  "generated_at": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "root_count": ${#roots[@]},
  "total_nodes": ${#RECEIPT_ORDER[@]},
  "tree": ${tree_json}
}
TREE_EOF

    log_ok "Receipt family tree written"
}

# ── Helper: Compute Maximum Depth ───────────────────────────────────────────
compute_max_depth() {
    local max_depth=0

    for run_id in "${RECEIPT_ORDER[@]}"; do
        local depth=0
        local current="$run_id"
        while [[ -n "${RECEIPT_PARENTS[$current]:-}" ]]; do
            ((depth++))
            current="${RECEIPT_PARENTS[$current]}"
            [[ "$depth" -gt 100 ]] && break  # Prevent infinite loop
        done
        [[ "$depth" -gt "$max_depth" ]] && max_depth="$depth"
    done

    echo "$max_depth"
}

# ── Helper: Count Root Receipts ─────────────────────────────────────────────
count_root_receipts() {
    local count=0
    for run_id in "${RECEIPT_ORDER[@]}"; do
        [[ -z "${RECEIPT_PARENTS[$run_id]:-}" ]] && ((count++))
    done
    echo "$count"
}

# ── Helper: Count Leaf Receipts ─────────────────────────────────────────────
count_leaf_receipts() {
    local count=0
    for run_id in "${RECEIPT_ORDER[@]}"; do
        [[ -z "${RECEIPT_CHILDREN[$run_id]:-}" ]] && ((count++))
    done
    echo "$count"
}

# ── Helper: Git Escape ──────────────────────────────────────────────────────
git_escape() {
    local s="$1"
    s="${s//\\/\\\\}"
    s="${s//\"/\\\"}"
    printf '%s' "$s"
}

# ── Integration: Update Current Receipt with Chain Info ─────────────────────
update_receipt_with_chain_info() {
    local receipt_file="$RECEIPTS_DIR/observatory.json"

    if [[ ! -f "$receipt_file" ]]; then
        return 0
    fi

    # Find parent receipt (most recent from history)
    local parent_hash=""
    if [[ -d "$RECEIPT_HISTORY_DIR" ]]; then
        local latest_hist
        latest_hist=$(find "$RECEIPT_HISTORY_DIR" -name "*.json" -type f -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
        if [[ -n "$latest_hist" && -f "$latest_hist" ]]; then
            parent_hash=$(sha256_of_file "$latest_hist")
        fi
    fi

    # Add chain info to receipt if not already present
    if ! grep -q '"parent_hash"' "$receipt_file" 2>/dev/null; then
        # Create temp file with chain info added
        local temp_file="${receipt_file}.tmp"

        # Use Python for clean JSON manipulation
        python3 << PYEOF "$receipt_file" "$temp_file" "$parent_hash"
import json
import sys

receipt_file = sys.argv[1]
temp_file = sys.argv[2]
parent_hash = sys.argv[3] if len(sys.argv) > 2 else ""

try:
    with open(receipt_file, 'r') as f:
        data = json.load(f)

    # Add chain information
    data['chain'] = {
        'parent_hash': parent_hash if parent_hash else None,
        'chain_version': '1.0.0',
        'node_type': 'leaf'  # Current receipt is always a leaf
    }

    with open(temp_file, 'w') as f:
        json.dump(data, f, indent=2)

    sys.exit(0)
except Exception as e:
    print(f"Error updating receipt: {e}", file=sys.stderr)
    sys.exit(1)
PYEOF

        if [[ $? -eq 0 && -f "$temp_file" ]]; then
            mv "$temp_file" "$receipt_file"
            log_ok "Updated receipt with chain information"
        else
            rm -f "$temp_file" 2>/dev/null
        fi
    fi
}

# ── Integration: Archive Current Receipt to History ─────────────────────────
archive_receipt_to_history() {
    local current_receipt="$RECEIPTS_DIR/observatory.json"

    if [[ ! -f "$current_receipt" ]]; then
        return 0
    fi

    mkdir -p "$RECEIPT_HISTORY_DIR"

    # Extract run_id for filename
    local run_id
    run_id=$(grep -oP '(?<="run_id":\s*")[^"]+' "$current_receipt" 2>/dev/null || date -u +%Y%m%dT%H%M%SZ)

    local history_file="$RECEIPT_HISTORY_DIR/observatory-${run_id}.json"

    # Copy to history if not already archived
    if [[ ! -f "$history_file" ]]; then
        cp "$current_receipt" "$history_file"
        log_ok "Archived receipt to history: $history_file"
    fi

    # Prune history to last 50 entries
    local history_count
    history_count=$(find "$RECEIPT_HISTORY_DIR" -name "*.json" -type f | wc -l | tr -d ' ')
    if [[ "$history_count" -gt 50 ]]; then
        find "$RECEIPT_HISTORY_DIR" -name "*.json" -type f -printf '%T@ %p\n' | \
            sort -rn | tail -n +51 | cut -d' ' -f2- | \
            xargs rm -f 2>/dev/null
        log_ok "Pruned receipt history to 50 entries"
    fi
}

# ── Main: Emit All Receipt Chain Outputs ────────────────────────────────────
emit_receipt_chain() {
    log_info "Generating receipt chain visualizations..."
    timer_start

    # Ensure output directories exist
    mkdir -p "$RECEIPTS_DIR" "$DIAGRAMS_DIR" "$RECEIPT_HISTORY_DIR"

    # Archive current receipt first
    archive_receipt_to_history

    # Update current receipt with chain info
    update_receipt_with_chain_info

    # Discover and build relationships
    discover_receipt_history
    build_receipt_relationships

    # Generate all outputs
    emit_receipt_chain_json
    emit_receipt_lineage_mermaid
    emit_receipt_lineage_json
    emit_receipt_family_tree

    local elapsed
    elapsed=$(timer_elapsed_ms)
    log_ok "Receipt chain generation complete in ${elapsed}ms"

    # Record performance
    record_phase_timing "receipt_chain" "$elapsed"
}

# ── CLI Entry Point for Standalone Execution ────────────────────────────────
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    # Running standalone - source dependencies
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "${SCRIPT_DIR}/util.sh"

    # Parse arguments
    OUTPUT_FORMAT="all"
    for arg in "$@"; do
        case "$arg" in
            --json)      OUTPUT_FORMAT="json" ;;
            --mermaid)   OUTPUT_FORMAT="mermaid" ;;
            --lineage)   OUTPUT_FORMAT="lineage" ;;
            --tree)      OUTPUT_FORMAT="tree" ;;
            --all)       OUTPUT_FORMAT="all" ;;
            --help)
                echo "Usage: emit-receipts.sh [--json|--mermaid|--lineage|--tree|--all|--help]"
                echo ""
                echo "Options:"
                echo "  --json      Output receipt chain JSON only"
                echo "  --mermaid   Output Mermaid lineage diagram only"
                echo "  --lineage   Output lineage JSON only"
                echo "  --tree      Output family tree JSON only"
                echo "  --all       Output all formats (default)"
                echo "  --help      Show this help message"
                exit 0
                ;;
        esac
    done

    # Run requested outputs
    ensure_output_dirs
    discover_receipt_history
    build_receipt_relationships

    case "$OUTPUT_FORMAT" in
        json)     emit_receipt_chain_json ;;
        mermaid)  emit_receipt_lineage_mermaid ;;
        lineage)  emit_receipt_lineage_json ;;
        tree)     emit_receipt_family_tree ;;
        all)      emit_receipt_chain ;;
    esac
fi
