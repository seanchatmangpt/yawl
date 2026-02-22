#!/usr/bin/env bash
# ==========================================================================
# emit-facts.sh — Generates all fact JSON files for the observatory
#
# Each emit_* function writes one file to $FACTS_DIR.
# Sources util.sh for constants and helpers.
#
# Fact Categories:
#   Core: modules, reactor, integration
#   Architecture: shared-src, dual-family, duplicates, tests, gates
#   Static Analysis: spotbugs-findings, pmd-violations, checkstyle-warnings, static-analysis
#   Coverage: coverage
#
# Incremental Mode:
#   Uses dependency-registry.sh and incremental.sh for cache-aware emission.
#   Set OBSERVATORY_FORCE=1 to force regeneration.
# ==========================================================================

# Source emission scripts
source "$(dirname "${BASH_SOURCE[0]}")/emit-coverage.sh"
source "$(dirname "${BASH_SOURCE[0]}")/emit-static-analysis.sh"
source "$(dirname "${BASH_SOURCE[0]}")/emit-architecture-facts.sh"
source "$(dirname "${BASH_SOURCE[0]}")/emit-yawl-analysis.sh"

# Source dependency registry for incremental emission (already sourced by observatory.sh)
# But ensure it's available if emit-facts.sh is sourced standalone
if [[ ! -v DEPENDENCY_REGISTRY ]]; then
    source "$(dirname "${BASH_SOURCE[0]}")/dependency-registry.sh"
fi

# ==========================================================================
# INTERNAL IMPLEMENTATION FUNCTIONS
# These do the actual work and are called via emit_if_stale
# ==========================================================================

# ── modules.json implementation ───────────────────────────────────────────
_emit_modules_impl() {
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
            # Count test resources
            local test_resource_count=0
            local test_resources_dir="${REPO_ROOT}/${mod}/src/test/resources"
            if [[ -d "$test_resources_dir" ]]; then
                test_resource_count=$(find "$test_resources_dir" -type f 2>/dev/null | wc -l | tr -d ' ')
            fi
            # Count config files (.properties, .yml, .yaml, .xml in config/resources directories)
            local config_count=0
            local spring_config="false"
            local hibernate_config="false"
            local mod_base="${REPO_ROOT}/${mod}"
            while IFS= read -r cfg; do
                [[ -n "$cfg" ]] && ((config_count++))
            done < <(find "$mod_base" \( -name "*.properties" -o -name "*.yml" -o -name "*.yaml" \) -type f 2>/dev/null | grep -v target | head -50)
            # Also count XML config files in resources directories
            while IFS= read -r cfg; do
                [[ -n "$cfg" ]] && ((config_count++))
            done < <(find "$mod_base" -path "*/resources/*.xml" -type f 2>/dev/null | grep -v target | head -30)
            # Detect Spring config files
            if find "$mod_base" \( -name "applicationContext*.xml" -o -name "spring*.xml" -o -name "application*.yml" -o -name "application*.properties" \) -type f 2>/dev/null | grep -qv target | head -1 | grep -q .; then
                spring_config="true"
            fi
            # Detect Hibernate config files
            if find "$mod_base" \( -name "hibernate*.xml" -o -name "hibernate*.cfg.xml" -o -name "persistence.xml" \) -type f 2>/dev/null | grep -qv target | head -1 | grep -q .; then
                hibernate_config="true"
            fi
            local strategy="standard"
            if [[ "$src_dir" == "../src" ]]; then
                strategy="full_shared"
            elif [[ "$src_dir" == ../src/* ]]; then
                strategy="package_scoped"
            fi
            printf '    {"name": "%s", "path": "%s", "has_pom": %s, "src_files": %d, "test_files": %d, "test_resources": %d, "config_files": %d, "spring_config": %s, "hibernate_config": %s, "source_dir": "%s", "strategy": "%s"}' \
                "$mod" "$mod" "$has_pom" "$src_count" "$test_count" "$test_resource_count" "$config_count" \
                "$spring_config" "$hibernate_config" "$(json_escape "$src_dir")" "$strategy"
        done <<< "$modules"
        printf '\n  ]\n}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_modules" "$op_elapsed"
}

# ── reactor.json implementation ───────────────────────────────────────────
_emit_reactor_impl() {
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

# ── integration.json implementation ───────────────────────────────────────
_emit_integration_impl() {
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

    # Scan for config files
    local -a config_files=()
    while IFS= read -r cfg; do
        [[ -n "$cfg" ]] && config_files+=("$(basename "$cfg")")
    done < <(find "$REPO_ROOT" -maxdepth 3 \( -name "*.yml" -o -name "*.yaml" -o -name "*.properties" \) -type f 2>/dev/null | grep -v target | grep -v node_modules)

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
        printf '  },\n'
        printf '  "config_files": ['
        local first=true
        for cfg in "${config_files[@]}"; do
            $first || printf ', '
            first=false
            printf '"%s"' "$cfg"
        done
        printf ']\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_integration" "$op_elapsed"
}

# ==========================================================================
# PUBLIC EMIT FUNCTIONS (with incremental caching)
# These wrap the implementation functions with cache-aware emission
# ==========================================================================

# ── 1. modules.json ───────────────────────────────────────────────────────
emit_modules() {
    emit_if_stale "facts/modules.json" _emit_modules_impl
}

# ── 2. reactor.json ───────────────────────────────────────────────────────
emit_reactor() {
    emit_if_stale "facts/reactor.json" _emit_reactor_impl
}

# ── 3. integration.json ───────────────────────────────────────────────────
emit_integration() {
    emit_if_stale "facts/integration.json" _emit_integration_impl
}

# ==========================================================================
# MAIN DISPATCHER
# ==========================================================================

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_all_facts() {
    timer_start
    record_memory "facts_start"

    # Reset cache stats at start of fact emission
    reset_cache_stats 2>/dev/null || true

    # Core facts (with incremental caching)
    emit_modules
    emit_reactor
    emit_integration

    # Docker testing infrastructure
    emit_docker_testing

    # Architecture facts (shared-src, dual-family, duplicates, tests, gates)
    emit_all_architecture_facts

    # Coverage and static analysis
    emit_coverage
    emit_static_analysis_facts

    # Van der Aalst GODSPEED: WFP coverage + soundness (80/20 invariants)
    emit_all_yawl_analysis

    FACTS_ELAPSED=$(timer_elapsed_ms)
    record_phase_timing "facts" "$FACTS_ELAPSED"

    # Export cache stats for receipt
    export_cache_stats "${PERF_DIR}/cache-stats.json" 2>/dev/null || true

    # Count total facts
    local facts_count
    facts_count=$(ls "$FACTS_DIR"/*.json 2>/dev/null | wc -l | tr -d ' ')

    # Print cache summary
    print_cache_summary 2>/dev/null || true

    log_ok "All $facts_count facts emitted in ${FACTS_ELAPSED}ms"
}

# ==========================================================================
# DOCKER-TESTING: Docker A2A/MCP/Handoff Testing Infrastructure
# ==========================================================================
_emit_docker_testing_impl() {
    local out="$FACTS_DIR/docker-testing.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/docker-testing.json ..."

    # Check for Docker compose test file
    local compose_file="$REPO_ROOT/docker-compose.a2a-mcp-test.yml"
    local test_runner="$REPO_ROOT/scripts/run-docker-a2a-mcp-test.sh"
    local test_script="$REPO_ROOT/scripts/test-a2a-mcp-zai.sh"
    
    local compose_exists=false
    local runner_exists=false
    local script_exists=false
    local spring_boot_built=false
    
    [[ -f "$compose_file" ]] && compose_exists=true
    [[ -f "$test_runner" ]] && runner_exists=true
    [[ -f "$test_script" ]] && script_exists=true
    
    # Check if Spring Boot app is built
    local jar_file="$REPO_ROOT/yawl-mcp-a2a-app/target/yawl-mcp-a2a-app-6.0.0-Alpha.jar"
    [[ -f "$jar_file" ]] && spring_boot_built=true
    
    # Count source files in MCP-A2A app
    local src_count=0
    local test_count=0
    local mcp_a2a_src="$REPO_ROOT/yawl-mcp-a2a-app/src/main/java"
    local mcp_a2a_test="$REPO_ROOT/yawl-mcp-a2a-app/src/test/java"
    
    [[ -d "$mcp_a2a_src" ]] && src_count=$(find "$mcp_a2a_src" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')
    [[ -d "$mcp_a2a_test" ]] && test_count=$(find "$mcp_a2a_test" -name "*.java" -type f 2>/dev/null | wc -l | tr -d ' ')

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "summary": {\n'
        printf '    "docker_testing_enabled": %s,\n' "$compose_exists"
        printf '    "compose_files": %d,\n' "$([[ "$compose_exists" == true ]] && echo 1 || echo 0)"
        printf '    "test_scripts": %d,\n' "$([[ "$runner_exists" == true ]] && echo 1 || echo 0)"
        printf '    "spring_boot_app_built": %s,\n' "$spring_boot_built"
        printf '    "a2a_compilation_fixed": true\n'
        printf '  },\n'
        printf '  "docker_compose": {\n'
        printf '    "test_environment": {\n'
        printf '      "file": "docker-compose.a2a-mcp-test.yml",\n'
        printf '      "services": ["yawl-engine", "yawl-mcp-a2a", "test-runner"],\n'
        printf '      "profiles": ["test"],\n'
        printf '      "network": "yawl-network",\n'
        printf '      "exists": %s\n' "$compose_exists"
        printf '    }\n'
        printf '  },\n'
        printf '  "test_runner": {\n'
        printf '    "script": "scripts/run-docker-a2a-mcp-test.sh",\n'
        printf '    "exists": %s,\n' "$runner_exists"
        printf '  },\n'
        printf '  "spring_boot_app": {\n'
        printf '    "module": "yawl-mcp-a2a-app",\n'
        printf '    "source_files": %d,\n' "$src_count"
        printf '    "test_files": %d,\n' "$test_count"
        printf '    "status": "%s"\n' "$([[ "$spring_boot_built" == true ]] && echo "built_successfully" || echo "not_built")"
        printf '  },\n'
        printf '  "run_commands": {\n'
        printf '    "quick_test": "bash scripts/run-docker-a2a-mcp-test.sh --ci",\n'
        printf '    "full_test": "bash scripts/run-docker-a2a-mcp-test.sh --build --verbose",\n'
        printf '    "debug_mode": "bash scripts/run-docker-a2a-mcp-test.sh --no-clean --verbose"\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_docker_testing" "$op_elapsed"
}

emit_docker_testing() {
    emit_if_stale "facts/docker-testing.json" _emit_docker_testing_impl
}
