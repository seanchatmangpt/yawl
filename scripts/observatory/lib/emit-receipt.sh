#!/usr/bin/env bash
# ==========================================================================
# emit-receipt.sh — Generates observatory.json receipt and INDEX.md
#
# Receipt binds inputs -> outputs with SHA-256 checksums.
# INDEX.md provides human-readable entry point.
# ==========================================================================

# ── observatory.json ──────────────────────────────────────────────────────
emit_receipt() {
    local out="$RECEIPTS_DIR/observatory.json"
    log_info "Emitting receipts/observatory.json ..."

    local run_id="${RUN_ID}"
    local total_elapsed="${TOTAL_ELAPSED:-0}"

    # Determine status
    local status="GREEN"
    if [[ ${#REFUSALS[@]} -gt 0 ]]; then
        status="RED"
    elif [[ ${#WARNINGS[@]} -gt 0 ]]; then
        status="YELLOW"
    fi

    # Compute input checksums
    local root_pom_sha
    root_pom_sha=$(sha256_of_file "$REPO_ROOT/pom.xml")

    # List fact files
    local -a fact_files=()
    while IFS= read -r f; do
        [[ -n "$f" ]] && fact_files+=("$(basename "$f")")
    done < <(ls "$FACTS_DIR"/*.json 2>/dev/null)

    # List diagram files
    local -a diagram_files=()
    while IFS= read -r f; do
        [[ -n "$f" ]] && diagram_files+=("$(basename "$f")")
    done < <(ls "$DIAGRAMS_DIR"/*.mmd 2>/dev/null)

    # Build fact checksums
    local facts_sha_entries=""
    for fn in "${fact_files[@]}"; do
        local sha
        sha=$(sha256_of_file "$FACTS_DIR/$fn")
        [[ -n "$facts_sha_entries" ]] && facts_sha_entries+=","
        facts_sha_entries+=$'\n'"      \"${fn}\": \"${sha}\""
    done

    # Build diagram checksums
    local diagrams_sha_entries=""
    for dn in "${diagram_files[@]}"; do
        local sha
        sha=$(sha256_of_file "$DIAGRAMS_DIR/$dn")
        [[ -n "$diagrams_sha_entries" ]] && diagrams_sha_entries+=","
        diagrams_sha_entries+=$'\n'"      \"${dn}\": \"${sha}\""
    done

    # Build refusals JSON array
    local refusals_json=""
    if [[ ${#REFUSALS[@]} -gt 0 ]]; then
        refusals_json=$(printf '%s\n' "${REFUSALS[@]}" | awk 'NR>1{printf ","} {printf "\n    %s", $0}')
    fi

    # Build warnings JSON array
    local warnings_json=""
    if [[ ${#WARNINGS[@]} -gt 0 ]]; then
        local first=true
        for w in "${WARNINGS[@]}"; do
            $first || warnings_json+=","
            first=false
            warnings_json+=$'\n'"    \"$(json_escape "$w")\""
        done
    fi

    cat > "$out" << RECEIPT_JSON
{
  "run_id": "${run_id}",
  "status": "${status}",
  "repo": {
    "path": "$(json_escape "$REPO_ROOT")",
    "git": {
      "branch": "$(git_branch)",
      "commit": "$(git_commit)",
      "dirty": $(git_dirty)
    }
  },
  "toolchain": {
    "java": $(get_java_info_json),
    "maven": "$(detect_maven_version)"
  },
  "inputs": {
    "root_pom_sha256": "${root_pom_sha}"
  },
  "facts_emitted": $(json_arr "${fact_files[@]}"),
  "diagrams_emitted": $(json_arr "${diagram_files[@]}"),
  "refusals": [${refusals_json}
  ],
  "warnings": [${warnings_json}
  ],
  "outputs": {
    "index_sha256": "$(sha256_of_file "$OUT_DIR/INDEX.md")",
    "facts_sha256": {${facts_sha_entries}
    },
    "diagrams_sha256": {${diagrams_sha_entries}
    }
  },
  "timing_ms": {
    "total": ${total_elapsed},
    "facts": ${FACTS_ELAPSED:-0},
    "diagrams": ${DIAGRAMS_ELAPSED:-0},
    "yawl_xml": ${YAWL_XML_ELAPSED:-0}
  }
}
RECEIPT_JSON

    log_ok "Receipt written: status=${status} refusals=${#REFUSALS[@]} warnings=${#WARNINGS[@]}"
}

# ── INDEX.md ──────────────────────────────────────────────────────────────
emit_index() {
    local out="$OUT_DIR/INDEX.md"
    log_info "Emitting INDEX.md ..."

    local run_id="${RUN_ID}"
    local status="GREEN"
    [[ ${#REFUSALS[@]} -gt 0 ]] && status="RED"
    [[ ${#WARNINGS[@]} -gt 0 && "$status" == "GREEN" ]] && status="YELLOW"

    # Count outputs
    local facts_count diagrams_count
    facts_count=$(ls "$FACTS_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')
    diagrams_count=$(ls "$DIAGRAMS_DIR"/*.mmd 2>/dev/null | wc -l | tr -d ' ')

    # Get health score from static analysis if available
    local health_score="N/A"
    if [[ -f "$FACTS_DIR/static-analysis.json" ]]; then
        health_score=$(python3 -c "import json; print(json.load(open('$FACTS_DIR/static-analysis.json')).get('health_score', 'N/A'))" 2>/dev/null || echo "N/A")
    fi

    # Check which architecture facts exist
    local has_shared_src="false" has_dual_family="false" has_duplicates="false" has_tests="false" has_gates="false"
    [[ -f "$FACTS_DIR/shared-src.json" ]] && has_shared_src="true"
    [[ -f "$FACTS_DIR/dual-family.json" ]] && has_dual_family="true"
    [[ -f "$FACTS_DIR/duplicates.json" ]] && has_duplicates="true"
    [[ -f "$FACTS_DIR/tests.json" ]] && has_tests="true"
    [[ -f "$FACTS_DIR/gates.json" ]] && has_gates="true"

    cat > "$out" << INDEX_MD
# v6 Observatory (Latest)

Run: ${run_id}  Status: ${status}  Health Score: ${health_score}

## Receipt
- [receipts/observatory.json](receipts/observatory.json) — Verification receipt

## Facts (${facts_count} files)

### Core Analysis
- [facts/modules.json](facts/modules.json) — Module inventory
- [facts/reactor.json](facts/reactor.json) — Maven reactor order
- [facts/integration.json](facts/integration.json) — MCP/A2A integration status

### Architecture Analysis
- [facts/shared-src.json](facts/shared-src.json) — Shared source directory mapping
- [facts/dual-family.json](facts/dual-family.json) — Stateful vs stateless engine families
- [facts/duplicates.json](facts/duplicates.json) — Duplicate FQCN detection
- [facts/tests.json](facts/tests.json) — Test inventory and coverage hints
- [facts/gates.json](facts/gates.json) — Quality gates configuration

### Static Analysis
- [facts/static-analysis.json](facts/static-analysis.json) — Aggregated code health summary
- [facts/spotbugs-findings.json](facts/spotbugs-findings.json) — SpotBugs bug findings
- [facts/pmd-violations.json](facts/pmd-violations.json) — PMD rule violations
- [facts/checkstyle-warnings.json](facts/checkstyle-warnings.json) — Checkstyle warnings

## Diagrams (${diagrams_count} files)

### Architecture
- [diagrams/10-maven-reactor.mmd](diagrams/10-maven-reactor.mmd) — Maven reactor graph
- [diagrams/50-risk-surfaces.mmd](diagrams/50-risk-surfaces.mmd) — FMEA risk surface analysis

### Code Health
- [diagrams/60-code-health-dashboard.mmd](diagrams/60-code-health-dashboard.mmd) — Code health dashboard
- [diagrams/61-static-analysis-trends.mmd](diagrams/61-static-analysis-trends.mmd) — Trend visualization

### Integration
- [diagrams/70-mcp-integration.mmd](diagrams/70-mcp-integration.mmd) — MCP integration flow
- [diagrams/71-a2a-integration.mmd](diagrams/71-a2a-integration.mmd) — A2A integration flow

## YAWL Workflow
- [diagrams/yawl/build-and-test.yawl.xml](diagrams/yawl/build-and-test.yawl.xml) — Build lifecycle as YAWL net

## How to Generate Static Analysis Reports

Before running the observatory with static analysis, generate the tool reports:

\`\`\`bash
# Option 1: Using Docker (recommended for consistent results)
./scripts/observatory/run-static-analysis.sh

# Option 2: Using Maven directly
mvn -P analysis spotbugs:spotbugs pmd:pmd checkstyle:check

# Then run the observatory
./scripts/observatory/observatory.sh
\`\`\`

## Code Health Scoring

The health score (0-100) is calculated as:
\`\`\`
health_score = 100 - (spotbugs * 3 + pmd * 2 + checkstyle * 1)
\`\`\`

- **GREEN**: Score >= 80
- **YELLOW**: Score 60-79
- **RED**: Score < 60

## Staleness Detection

To check if the observatory outputs are stale (need regeneration):

\`\`\`bash
./scripts/observatory/check-staleness.sh
# Exit 0 = FRESH, Exit 1 = STALE, Exit 2 = ERROR
\`\`\`

---
Generated by YAWL V6 Observatory
INDEX_MD

    log_ok "INDEX.md written"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_receipt_and_index() {
    timer_start
    record_memory "receipt_start"

    # Generate performance reports before receipt
    generate_performance_report
    update_performance_history
    analyze_diagram_speed
    generate_performance_summary

    # Emit INDEX.md first so we can hash it in the receipt
    emit_index
    emit_receipt
    RECEIPT_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "receipt" "$RECEIPT_ELAPSED"
    log_ok "Receipt and INDEX emitted in ${RECEIPT_ELAPSED}ms"
}
