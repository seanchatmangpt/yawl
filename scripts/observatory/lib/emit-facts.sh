#!/usr/bin/env bash
# ==========================================================================
# emit-facts.sh — Generates all 9 fact JSON files for the observatory
#
# Each emit_* function writes one file to $FACTS_DIR.
# Sources util.sh for constants and helpers.
# ==========================================================================

# ── 1. modules.json ───────────────────────────────────────────────────────
emit_modules() {
    local out="$FACTS_DIR/modules.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/modules.json ..."

    local modules
    modules=$(discover_modules)

    {
        printf '{\n  "modules": [\n'
        local first=true
        while IFS= read -r mod; do
            [[ -z "$mod" ]] && continue
            $first || printf ',\n'
            first=false
            local has_pom="false"
            [[ -f "$REPO_ROOT/$mod/pom.xml" ]] && has_pom="true"
            local src_dir
            src_dir=$(sed -n 's/.*<sourceDirectory>\([^<]*\)<\/sourceDirectory>.*/\1/p' "$REPO_ROOT/$mod/pom.xml" 2>/dev/null | head -1 || echo "src/main/java")
            local src_count=0 test_count=0
            local resolved_src="${REPO_ROOT}/${mod}/${src_dir}"
            if [[ -d "$resolved_src" ]]; then
                src_count=$(find "$resolved_src" -name "*.java" -type f 2>/dev/null | wc -l)
            fi
            local test_dir
            test_dir=$(sed -n 's/.*<testSourceDirectory>\([^<]*\)<\/testSourceDirectory>.*/\1/p' "$REPO_ROOT/$mod/pom.xml" 2>/dev/null | head -1 || echo "src/test/java")
            local resolved_test="${REPO_ROOT}/${mod}/${test_dir}"
            if [[ -d "$resolved_test" ]]; then
                test_count=$(find "$resolved_test" -name "*.java" -type f 2>/dev/null | wc -l)
            fi
            local strategy="standard"
            if [[ "$src_dir" == "../src" ]]; then
                strategy="full_shared"
            elif [[ "$src_dir" == ../src/* ]]; then
                strategy="package_scoped"
            fi
            printf '    {"name": "%s", "path": "%s", "has_pom": %s, "src_files": %d, "test_files": %d, "source_dir": "%s", "strategy": "%s"}' \
                "$mod" "$mod" "$has_pom" "$src_count" "$test_count" \
                "$(json_escape "$src_dir")" "$strategy"
        done <<< "$modules"
        printf '\n  ]\n}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_modules" "$op_elapsed"
}

# ── 2. reactor.json ───────────────────────────────────────────────────────
emit_reactor() {
    local out="$FACTS_DIR/reactor.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/reactor.json ..."

    local modules
    modules=$(discover_modules)

    local -a reactor_order=("yawl-parent")
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        reactor_order+=("$mod")
    done <<< "$modules"

    local deps_entries=()
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue
        while IFS= read -r dep_art; do
            [[ -z "$dep_art" ]] && continue
            [[ "$dep_art" == "yawl-parent" ]] && continue
            [[ "$dep_art" == "$mod" ]] && continue
            deps_entries+=("{\"from\":\"$mod\",\"to\":\"$dep_art\"}")
        done < <(sed -n 's/.*<artifactId>\(yawl-[a-zA-Z0-9_-]*\)<\/artifactId>.*/\1/p' "$pom" 2>/dev/null | sort -u)
    done <<< "$modules"

    {
        printf '{\n  "reactor_order": [\n'
        local first=true
        for mod in "${reactor_order[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    "%s"' "$mod"
        done
        printf '\n  ],\n  "module_deps": [\n'
        first=true
        for entry in "${deps_entries[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$entry"
        done
        printf '\n  ]\n}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_reactor" "$op_elapsed"
}

# ── 3. integration.json ───────────────────────────────────────────────────
emit_integration() {
    local out="$FACTS_DIR/integration.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/integration.json ..."

    local integration_root="$REPO_ROOT/src/org/yawlfoundation/yawl/integration"

    # Count components
    local mcp_classes=0 a2a_classes=0 zai_classes=0
    [[ -d "$integration_root/mcp" ]] && mcp_classes=$(find "$integration_root/mcp" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')
    [[ -d "$integration_root/a2a" ]] && a2a_classes=$(find "$integration_root/a2a" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')
    [[ -d "$integration_root/zai" ]] && zai_classes=$(find "$integration_root/zai" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')

    {
        printf '{\n'
        printf '  "mcp": {\n'
        printf '    "classes": %d,\n' "${mcp_classes:-0}"
        printf '    "server": "YawlMcpServer",\n'
        printf '    "version": "5.2.0",\n'
        printf '    "transport": "STDIO",\n'
        printf '    "tools": ["launch_case", "cancel_case", "get_case_state", "list_specifications", "get_workitems", "complete_workitem"]\n'
        printf '  },\n'
        printf '  "a2a": {\n'
        printf '    "classes": %d,\n' "${a2a_classes:-0}"
        printf '    "server": "YawlA2AServer",\n'
        printf '    "version": "5.2.0",\n'
        printf '    "port": 8081,\n'
        printf '    "skills": ["launch_workflow", "query_workflows", "manage_workitems", "cancel_workflow"]\n'
        printf '  },\n'
        printf '  "zai": {\n'
        printf '    "classes": %d,\n' "${zai_classes:-0}"
        printf '    "service": "ZaiFunctionService",\n'
        printf '    "model": "GLM-4"\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_integration" "$op_elapsed"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_all_facts() {
    timer_start
    record_memory "facts_start"
    emit_modules
    emit_reactor
    emit_integration
    FACTS_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "facts" "$FACTS_ELAPSED"
    log_ok "All facts emitted in ${FACTS_ELAPSED}ms"
}
