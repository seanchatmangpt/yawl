#!/usr/bin/env bash
# ==========================================================================
# Observatory Context Injector — Generates Agent-Ready Context
#
# Reads observatory facts and produces structured context for agents.
# Output formats: json, markdown, summary
#
# Usage:
#   source scripts/observatory/lib/emit-context.sh
#   emit_agent_context "$facts_dir" "markdown"  # For CLAUDE.md injection
#   emit_agent_context "$facts_dir" "json"      # For programmatic use
# ==========================================================================

set -euo pipefail

# ── Core Context Generator ────────────────────────────────────────────────────
emit_agent_context() {
    local facts_dir="$1"
    local format="${2:-summary}"

    # Load all facts
    local modules_json="$facts_dir/modules.json"
    local reactor_json="$facts_dir/reactor.json"
    local shared_src_json="$facts_dir/shared-src.json"
    local dual_family_json="$facts_dir/dual-family.json"
    local deps_json="$facts_dir/deps-conflicts.json"
    local gates_json="$facts_dir/gates.json"
    local tests_json="$facts_dir/tests.json"
    local maven_hazards_json="$facts_dir/maven-hazards.json"

    case "$format" in
        json)
            emit_json_context "$modules_json" "$reactor_json" "$shared_src_json" \
                "$dual_family_json" "$deps_json" "$gates_json" "$tests_json" "$maven_hazards_json"
            ;;
        markdown)
            emit_markdown_context "$modules_json" "$reactor_json" "$shared_src_json" \
                "$dual_family_json" "$deps_json" "$gates_json" "$tests_json" "$maven_hazards_json"
            ;;
        summary|*)
            emit_summary_context "$modules_json" "$reactor_json" "$shared_src_json" \
                "$dual_family_json" "$deps_json" "$gates_json" "$tests_json" "$maven_hazards_json"
            ;;
    esac
}

# ── JSON Format (for programmatic use) ────────────────────────────────────────
emit_json_context() {
    local modules_json="$1"
    local reactor_json="$2"
    local shared_src_json="$3"
    local dual_family_json="$4"
    local deps_json="$5"
    local gates_json="$6"
    local tests_json="$7"
    local maven_hazards_json="$8"

    jq -n \
        --argfile modules "$modules_json" \
        --argfile reactor "$reactor_json" \
        --argfile shared_src "$shared_src_json" \
        --argfile dual_family "$dual_family_json" \
        --argfile deps "$deps_json" \
        --argfile gates "$gates_json" \
        --argfile tests "$tests_json" \
        --argfile hazards "$maven_hazards_json" \
        '{
            timestamp: now | todate,
            modules: $modules,
            reactor: $reactor,
            shared_src: $shared_src,
            dual_family: $dual_family,
            deps_conflicts: $deps,
            gates: $gates,
            tests: $tests,
            maven_hazards: $hazards
        }' 2>/dev/null || echo '{}'
}

# ── Markdown Format (for CLAUDE.md injection) ─────────────────────────────────
emit_markdown_context() {
    local modules_json="$1"
    local reactor_json="$2"
    local shared_src_json="$3"
    local dual_family_json="$4"
    local deps_json="$5"
    local gates_json="$6"
    local tests_json="$7"
    local maven_hazards_json="$8"

    local module_count=0
    local shared_count=0
    local standard_count=0
    local mirror_pairs=0
    local dep_conflicts=0
    local test_files=0
    local h_status="unknown"
    local q_status="unknown"
    local ambiguities=0
    local hazards=0

    [[ -f "$modules_json" ]] && module_count=$(jq '.modules | length' "$modules_json" 2>/dev/null || echo "0")
    [[ -f "$modules_json" ]] && shared_count=$(jq '[.modules[] | select(.strategy == "full_shared")] | length' "$modules_json" 2>/dev/null || echo "0")
    [[ -f "$modules_json" ]] && standard_count=$(jq '[.modules[] | select(.strategy == "standard")] | length' "$modules_json" 2>/dev/null || echo "0")
    [[ -f "$dual_family_json" ]] && mirror_pairs=$(jq '.mirror_pairs | length // 0' "$dual_family_json" 2>/dev/null || echo "0")
    [[ -f "$deps_json" ]] && dep_conflicts=$(jq '.conflicts | length // 0' "$deps_json" 2>/dev/null || echo "0")
    [[ -f "$tests_json" ]] && test_files=$(jq '.total_test_files // 0' "$tests_json" 2>/dev/null || echo "0")
    [[ -f "$gates_json" ]] && h_status=$(jq -r '.h_gates.status // "unknown"' "$gates_json" 2>/dev/null)
    [[ -f "$gates_json" ]] && q_status=$(jq -r '.q_invariants.status // "unknown"' "$gates_json" 2>/dev/null)
    [[ -f "$shared_src_json" ]] && ambiguities=$(jq '.ownership_ambiguities | length // 0' "$shared_src_json" 2>/dev/null || echo "0")
    [[ -f "$maven_hazards_json" ]] && hazards=$(jq '.hazards | length // 0' "$maven_hazards_json" 2>/dev/null || echo "0")

    cat << EOF
## Observatory Snapshot

**Generated**: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

### Module Summary
- **Total Modules**: $module_count
- **Shared Source** (../src): $shared_count modules
- **Standard** (src/main/java): $standard_count modules

### Quality Gates
- **H-Guards**: $h_status
- **Q-Invariants**: $q_status

### Risk Metrics
- **Stateless Mirrors**: $mirror_pairs pairs
- **Dependency Conflicts**: $dep_conflicts overrides
- **Ownership Ambiguities**: $ambiguities
- **Maven Hazards**: $hazards

### Test Coverage
- **Test Files**: $test_files

### Diagrams
- Reactor: \`docs/v6/latest/diagrams/10-maven-reactor.mmd\`
- Shared Source: \`docs/v6/latest/diagrams/15-shared-src-map.mmd\`
- Risk Surfaces: \`docs/v6/latest/diagrams/50-risk-surfaces.mmd\`

### Fact Files
\`modules.json\` | \`reactor.json\` | \`shared-src.json\` | \`dual-family.json\` | \`deps-conflicts.json\` | \`gates.json\` | \`tests.json\` | \`maven-hazards.json\`
EOF
}

# ── Summary Format (concise for hooks) ────────────────────────────────────────
emit_summary_context() {
    local modules_json="$1"
    local reactor_json="$2"
    local shared_src_json="$3"
    local dual_family_json="$4"
    local deps_json="$5"
    local gates_json="$6"
    local tests_json="$7"
    local maven_hazards_json="$8"

    local module_count=0
    local mirror_pairs=0
    local h_status="?"
    local q_status="?"

    [[ -f "$modules_json" ]] && module_count=$(jq '.modules | length' "$modules_json" 2>/dev/null || echo "0")
    [[ -f "$dual_family_json" ]] && mirror_pairs=$(jq '.mirror_pairs | length // 0' "$dual_family_json" 2>/dev/null || echo "0")
    [[ -f "$gates_json" ]] && h_status=$(jq -r '.h_gates.status // "?"' "$gates_json" 2>/dev/null)
    [[ -f "$gates_json" ]] && q_status=$(jq -r '.q_invariants.status // "?"' "$gates_json" 2>/dev/null)

    echo "YAWL Observatory: $module_count modules | $mirror_pairs mirrors | H=$h_status Q=$q_status"
}

# ── CLI Entry Point ──────────────────────────────────────────────────────────
# Allow running directly: bash emit-context.sh [format] [facts_dir]
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    FACTS_DIR="${2:-docs/v6/latest/facts}"
    FORMAT="${1:-summary}"

    # Source log function if available
    if [[ -f "$SCRIPT_DIR/emit-facts.sh" ]]; then
        source "$SCRIPT_DIR/emit-facts.sh" 2>/dev/null || true
    fi

    emit_agent_context "$FACTS_DIR" "$FORMAT"
fi
