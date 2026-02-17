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

    # Compute fact checksums
    local -a fact_names=("modules" "reactor" "shared-src" "dual-family" "duplicates" "deps-conflicts" "tests" "gates" "maven-hazards")
    local facts_sha_entries=""
    for fn in "${fact_names[@]}"; do
        local sha
        sha=$(sha256_of_file "$FACTS_DIR/${fn}.json")
        [[ -n "$facts_sha_entries" ]] && facts_sha_entries+=","
        facts_sha_entries+=$'\n'"      \"${fn}.json\": \"${sha}\""
    done

    # Compute diagram checksums
    local -a diagram_names=("10-maven-reactor.mmd" "15-shared-src-map.mmd" "16-dual-family-map.mmd" "17-deps-conflicts.mmd" "30-test-topology.mmd" "40-ci-gates.mmd" "50-risk-surfaces.mmd")
    local diagrams_sha_entries=""
    for dn in "${diagram_names[@]}"; do
        local sha
        sha=$(sha256_of_file "$DIAGRAMS_DIR/${dn}")
        [[ -n "$diagrams_sha_entries" ]] && diagrams_sha_entries+=","
        diagrams_sha_entries+=$'\n'"      \"${dn}\": \"${sha}\""
    done

    # YAWL XML checksum
    local yawl_sha
    yawl_sha=$(sha256_of_file "$YAWL_DIR/build-and-test.yawl.xml")

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

    cat > "$out" << RECEIPT_EOF
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
    "java": "$(detect_java_version)",
    "maven": "$(detect_maven_version)"
  },
  "inputs": {
    "root_pom_sha256": "${root_pom_sha}"
  },
  "facts_emitted": $(json_arr "${fact_names[@]}"),
  "diagrams_emitted": $(json_arr "${diagram_names[@]}" "yawl/build-and-test.yawl.xml"),
  "refusals": [${refusals_json}
  ],
  "warnings": [${warnings_json}
  ],
  "outputs": {
    "facts_sha256": {${facts_sha_entries}
    },
    "diagrams_sha256": {${diagrams_sha_entries}
    },
    "yawl_xml_sha256": "${yawl_sha}"
  },
  "timing_ms": {
    "total": ${total_elapsed},
    "facts": ${FACTS_ELAPSED:-0},
    "diagrams": ${DIAGRAMS_ELAPSED:-0},
    "yawl_xml": ${YAWL_XML_ELAPSED:-0}
  }
}
RECEIPT_EOF

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

    cat > "$out" << INDEX_EOF
# v6 Observatory (Latest)

Run: ${run_id}  Status: ${status}

## Receipt
- [receipts/observatory.json](receipts/observatory.json)

## Facts
- [facts/modules.json](facts/modules.json) — Module inventory (names, paths, source strategy)
- [facts/reactor.json](facts/reactor.json) — Maven reactor order and inter-module dependencies
- [facts/shared-src.json](facts/shared-src.json) — Shared source roots and ownership ambiguities
- [facts/dual-family.json](facts/dual-family.json) — Stateful/stateless mirror class families
- [facts/duplicates.json](facts/duplicates.json) — Duplicate FQCNs within and across artifacts
- [facts/deps-conflicts.json](facts/deps-conflicts.json) — Dependency version convergence analysis
- [facts/tests.json](facts/tests.json) — Test topology (surefire/failsafe, counts per module)
- [facts/gates.json](facts/gates.json) — Quality gates (SpotBugs, PMD, Checkstyle, JaCoCo)
- [facts/maven-hazards.json](facts/maven-hazards.json) — Maven cache hazards and build traps

## Diagrams
- [diagrams/10-maven-reactor.mmd](diagrams/10-maven-reactor.mmd) — Maven reactor dependency graph
- [diagrams/15-shared-src-map.mmd](diagrams/15-shared-src-map.mmd) — Shared source ownership map
- [diagrams/16-dual-family-map.mmd](diagrams/16-dual-family-map.mmd) — Stateful/stateless mirror families
- [diagrams/17-deps-conflicts.mmd](diagrams/17-deps-conflicts.mmd) — Dependency conflict hotspot map
- [diagrams/30-test-topology.mmd](diagrams/30-test-topology.mmd) — Test distribution across modules
- [diagrams/40-ci-gates.mmd](diagrams/40-ci-gates.mmd) — CI quality gate lifecycle
- [diagrams/50-risk-surfaces.mmd](diagrams/50-risk-surfaces.mmd) — FMEA risk surface analysis

## YAWL Workflow
- [diagrams/yawl/build-and-test.yawl.xml](diagrams/yawl/build-and-test.yawl.xml) — Build lifecycle as YAWL net

## FMEA Risk Priority Numbers

| ID | Failure Mode | S | O | D | RPN | Mitigation |
|----|-------------|---|---|---|-----|------------|
| FM1 | Shared Source Path Confusion | 9 | 8 | 3 | 216 | shared-src.json + 15-shared-src-map.mmd |
| FM2 | Dual-Family Class Confusion | 8 | 7 | 4 | 224 | dual-family.json + 16-dual-family-map.mmd |
| FM3 | Dependency Version Skew | 7 | 6 | 5 | 210 | deps-conflicts.json + 17-deps-conflicts.mmd |
| FM4 | Maven Cached Missing Artifacts | 6 | 5 | 2 | 60 | maven-hazards.json |
| FM5 | Test Selection Ambiguity | 7 | 4 | 3 | 84 | tests.json + 30-test-topology.mmd |
| FM6 | Gate Bypass via Skip Flags | 8 | 3 | 6 | 144 | gates.json + 40-ci-gates.mmd |
| FM7 | Reactor Order Violation | 5 | 3 | 7 | 105 | reactor.json + 10-maven-reactor.mmd |

**S**=Severity **O**=Occurrence **D**=Detection (1=best, 10=worst) **RPN**=S*O*D

## How Mermaid Diagrams Solve Path Confusion

The 7 Mermaid diagrams provide **visual topology truth** that eliminates guessing:

1. **10-maven-reactor.mmd** — Shows which modules depend on which, preventing wrong build order
2. **15-shared-src-map.mmd** — Maps every module to its actual source root and include filters,
   so agents know exactly which files belong to which module
3. **16-dual-family-map.mmd** — Explicitly maps every stateful class to its stateless mirror,
   preventing edits to the wrong variant
4. **17-deps-conflicts.mmd** — Shows dependency management categories and conflict hotspots,
   so version issues are visible before they cause runtime errors
5. **30-test-topology.mmd** — Shows which modules have tests and where they live,
   preventing test selection errors
6. **40-ci-gates.mmd** — Shows the full gate lifecycle and what skip flags disable,
   preventing accidental gate bypass
7. **50-risk-surfaces.mmd** — FMEA risk surface map with RPN scores and mitigations

## How YAWL XML Solves Build Workflow Ambiguity

The YAWL XML specification (build-and-test.yawl.xml) models the build lifecycle as a
Petri-net-based workflow with:

- **Input/Output conditions** bounding the lifecycle
- **Sequential tasks** (Validate -> Compile -> UnitTests) enforcing order
- **Parallel AND-split** for quality gates (SpotBugs, PMD, Checkstyle run concurrently)
- **AND-join** synchronization before integration tests
- **Documentation** on each task linking to specific refusal codes

This makes the build process **executable** and **verifiable** — not just documented.
INDEX_EOF

    log_ok "INDEX.md written"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_receipt_and_index() {
    timer_start
    emit_receipt
    emit_index
    RECEIPT_ELAPSED=$(timer_elapsed_ms)
    log_ok "Receipt and INDEX emitted in ${RECEIPT_ELAPSED}ms"
}
