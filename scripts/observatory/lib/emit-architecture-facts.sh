#!/usr/bin/env bash
# ==========================================================================
# emit-architecture-facts.sh — Architecture-specific fact emitters
#
# Generates specialized facts for YAWL architecture analysis:
#   - shared-src.json: Shared source directory mapping
#   - dual-family.json: Stateful vs stateless engine families
#   - duplicates.json: Duplicate FQCNs across modules
#   - tests.json: Test inventory and coverage hints
#   - gates.json: Quality gates configuration
#
# Incremental Mode:
#   Uses emit_if_stale for cache-aware emission via dependency-registry.
#
# Usage:
#   source lib/emit-architecture-facts.sh
#   emit_all_architecture_facts
# ==========================================================================

# Source utilities if not already loaded
if [[ -z "${FACTS_DIR:-}" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    source "${SCRIPT_DIR}/util.sh"
fi

# ==========================================================================
# SHARED-SRC: Maps modules to their source directory strategies
# ==========================================================================
_emit_shared_src_impl() {
    local out="$FACTS_DIR/shared-src.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/shared-src.json ..."

    local -a shared_modules=()
    local -a scoped_modules=()
    local -a standard_modules=()

    # Analyze each module's source directory strategy
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue

        local src_dir
        src_dir=$(sed -n 's/.*<sourceDirectory>\([^<]*\)<\/sourceDirectory>.*/\1/p' "$pom" 2>/dev/null | head -1 || echo "src/main/java")

        if [[ "$src_dir" == "../src" ]]; then
            shared_modules+=("$mod")
        elif [[ "$src_dir" == ../src/* ]]; then
            scoped_modules+=("{\"module\": \"$mod\", \"source_dir\": \"$src_dir\"}")
        else
            standard_modules+=("$mod")
        fi
    done < <(ls -d "$REPO_ROOT"/yawl-* 2>/dev/null | xargs -n1 basename)

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "summary": {\n'
        printf '    "full_shared_count": %d,\n' "${#shared_modules[@]}"
        printf '    "package_scoped_count": %d,\n' "${#scoped_modules[@]}"
        printf '    "standard_count": %d\n' "${#standard_modules[@]}"
        printf '  },\n'
        printf '  "full_shared_modules": [\n'
        local first=true
        for mod in "${shared_modules[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    "%s"' "$mod"
        done
        printf '\n  ],\n'
        printf '  "package_scoped_modules": [\n'
        first=true
        for entry in "${scoped_modules[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$entry"
        done
        printf '\n  ],\n'
        printf '  "standard_modules": [\n'
        first=true
        for mod in "${standard_modules[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    "%s"' "$mod"
        done
        printf '\n  ],\n'
        printf '  "shared_source_root": "../src",\n'
        printf '  "architecture_pattern": "shared_monolith_with_package_scoped_integration"\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_shared_src" "$op_elapsed"
    log_ok "Shared-src: ${#shared_modules[@]} full_shared, ${#scoped_modules[@]} scoped, ${#standard_modules[@]} standard"
}

# ==========================================================================
# DUAL-FAMILY: Stateful vs Stateless engine families
# ==========================================================================
_emit_dual_family_impl() {
    local out="$FACTS_DIR/dual-family.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/dual-family.json ..."

    # Define the two families
    local -a stateful_classes=()
    local -a stateless_classes=()

    # Find stateful engine classes (org.yawlfoundation.yawl.engine.*)
    while IFS= read -r f; do
        [[ -n "$f" ]] && stateful_classes+=("$(basename "$f" .java)")
    done < <(find "$REPO_ROOT/src/org/yawlfoundation/yawl/engine" -name "*.java" -type f 2>/dev/null | head -50)

    # Find stateless engine classes (org.yawlfoundation.yawl.stateless.*)
    while IFS= read -r f; do
        [[ -n "$f" ]] && stateless_classes+=("$(basename "$f" .java)")
    done < <(find "$REPO_ROOT/src/org/yawlfoundation/yawl/stateless" -name "*.java" -type f 2>/dev/null | head -50)

    # Find interfaces shared between families
    local -a shared_interfaces=()
    while IFS= read -r f; do
        [[ -n "$f" ]] && shared_interfaces+=("$(basename "$f" .java)")
    done < <(grep -rl "interface " "$REPO_ROOT/src/org/yawlfoundation/yawl/engine"/*.java 2>/dev/null | head -20)

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "families": {\n'
        printf '    "stateful": {\n'
        printf '      "package": "org.yawlfoundation.yawl.engine",\n'
        printf '      "class_count": %d,\n' "${#stateful_classes[@]}"
        printf '      "primary_classes": ["YEngine", "YNetRunner", "YWorkItem", "YSpecification"]\n'
        printf '    },\n'
        printf '    "stateless": {\n'
        printf '      "package": "org.yawlfoundation.yawl.stateless",\n'
        printf '      "class_count": %d,\n' "${#stateless_classes[@]}"
        printf '      "primary_classes": ["YStatelessEngine", "YCaseMonitor"]\n'
        printf '    }\n'
        printf '  },\n'
        printf '  "shared_interfaces": [\n'
        local first=true
        for iface in "${shared_interfaces[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    "%s"' "$iface"
        done
        printf '\n  ],\n'
        printf '  "duplication_analysis": {\n'
        printf '    "status": "requires_review",\n'
        printf '    "note": "Stateful and stateless families share some code paths through ../src mechanism"\n'
        printf '  },\n'
        printf '  "migration_notes": [\n'
        printf '    "Stateful engine (YEngine) is the original implementation",\n'
        printf '    "Stateless engine (YStatelessEngine) is designed for cloud-native deployments",\n'
        printf '    "Both share domain model via full_shared source strategy"\n'
        printf '  ]\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_dual_family" "$op_elapsed"
    log_ok "Dual-family: stateful=${#stateful_classes[@]} stateless=${#stateless_classes[@]}"
}

# ==========================================================================
# DUPLICATES: Find duplicate FQCNs across modules (Optimized with awk)
# ==========================================================================
_emit_duplicates_impl() {
    local out="$FACTS_DIR/duplicates.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/duplicates.json ..."

    # Use cached discovery if available, otherwise find files
    local java_files
    if [[ -n "${_DISCOVERY_JAVA_FILES:-}" ]]; then
        java_files="$_DISCOVERY_JAVA_FILES"
    else
        java_files=$(find "$REPO_ROOT/src" -name "*.java" -type f 2>/dev/null | grep -v target)
    fi

    # Filter out test files and extract FQNs using awk for batch processing
    local fqn_data
    fqn_data=$(echo "$java_files" | grep -v 'Test\.java$' | awk -v root="$REPO_ROOT" '
    {
        file = $0
        cmd = "grep -m1 \"^package \" \"" file "\" 2>/dev/null"
        cmd | getline pkgline
        close(cmd)

        if (pkgline != "") {
            gsub(/^package[[:space:]]+/, "", pkgline)
            gsub(/;.*$/, "", pkgline)
            gsub(/[[:space:]]/, "", pkgline)

            n = split(file, parts, "/")
            classname = parts[n]
            gsub(/\.java$/, "", classname)

            if (pkgline != "" && classname !~ /Test$/) {
                rel_path = substr(file, length(root) + 2)
                print pkgline "." classname "|" rel_path
            }
        }
    }')

    # Count FQNs using sort/uniq for speed
    local fqn_counts total_fqns
    fqn_counts=$(echo "$fqn_data" | cut -d'|' -f1 | sort | uniq -c | sort -rn)
    total_fqns=$(echo "$fqn_data" | cut -d'|' -f1 | sort -u | wc -l | tr -d ' ')

    # Find duplicates (count > 1) using awk
    local -a duplicate_entries=()
    while IFS= read -r line; do
        [[ -z "$line" ]] && continue
        local count fqn
        count=$(echo "$line" | awk '{print $1}')
        fqn=$(echo "$line" | awk '{print $2}')

        if [[ "$count" -gt 1 ]]; then
            # Find all locations for this FQN
            local fqn_locs
            fqn_locs=$(echo "$fqn_data" | grep "^${fqn}|" | cut -d'|' -f2 | tr '\n' '|' | sed 's/|$//;s/|/", "/g')
            duplicate_entries+=("{\"fqn\": \"$fqn\", \"count\": $count, \"locations\": [\"$fqn_locs\"]}")
        fi
    done <<< "$fqn_counts"

    # Sort duplicates for consistent output
    IFS=$'\n' sorted_duplicates=($(sort <<<"${duplicate_entries[*]}")); unset IFS

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "summary": {\n'
        printf '    "total_fqns_scanned": %d,\n' "$total_fqns"
        printf '    "duplicate_count": %d,\n' "${#duplicate_entries[@]}"
        printf '    "status": "%s"\n' "$([[ ${#duplicate_entries[@]} -eq 0 ]] && echo "clean" || echo "duplicates_found")"
        printf '  },\n'
        printf '  "duplicates": [\n'
        local first=true
        for entry in "${sorted_duplicates[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$entry"
        done
        printf '\n  ]\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_duplicates" "$op_elapsed"
    log_ok "Duplicates: scanned $total_fqns FQNs, found ${#duplicate_entries[@]} duplicates"
}

# ==========================================================================
# TESTS: Test inventory and coverage hints (Optimized)
# Phase 1: Scan shared test/ directory at $REPO_ROOT/test/
# ==========================================================================
_emit_tests_impl() {
    local out="$FACTS_DIR/tests.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/tests.json ..."

    local -a test_inventory=()
    local total_tests=0
    local total_modules=0

    # Use cached discovery if available
    local test_files
    if [[ -n "${_DISCOVERY_TEST_FILES:-}" ]]; then
        test_files="$_DISCOVERY_TEST_FILES"
    else
        test_files=$(find "$REPO_ROOT" -path "*/test/*" -name "*.java" -type f 2>/dev/null | grep -v target)
    fi

    # Phase 1: Primary scan - shared test directory at $REPO_ROOT/test/
    local shared_test_dir="$REPO_ROOT/test"
    local junit5_count=0
    local junit4_count=0
    local shared_test_count=0

    if [[ -d "$shared_test_dir" ]]; then
        log_info "Scanning shared test directory: $shared_test_dir"

        # Get test files and batch-detect JUnit versions using xargs + grep -l
        local shared_tests
        shared_tests=$(echo "$test_files" | grep "^${shared_test_dir}")

        if [[ -n "$shared_tests" ]]; then
            shared_test_count=$(echo "$shared_tests" | grep -c .)

            # Batch JUnit detection using grep -l (lists files matching pattern)
            local j5_files j4_files
            j5_files=$(echo "$shared_tests" | xargs grep -l "org.junit.jupiter" 2>/dev/null | wc -l | tr -d ' ')
            j4_files=$(echo "$shared_tests" | xargs grep -l "org.junit.Test\|org.junit.Before" 2>/dev/null | wc -l | tr -d ' ')

            junit5_count=$((junit5_count + j5_files))
            junit4_count=$((junit4_count + j4_files))
            total_tests=$((total_tests + shared_test_count))

            if [[ $shared_test_count -gt 0 ]]; then
                test_inventory+=("{\"module\": \"shared-root-test\", \"test_count\": $shared_test_count, \"junit5\": $j5_files, \"junit4\": $j4_files, \"source\": \"test/\"}")
                ((total_modules++))
                log_ok "Found $shared_test_count tests in shared test/ directory"
            fi
        fi
    fi

    # Secondary scan: module-specific src/test/java directories
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local test_dir="$REPO_ROOT/$mod/src/test/java"
        [[ -d "$test_dir" ]] || continue

        ((total_modules++))

        # Filter test files for this module
        local mod_tests
        mod_tests=$(echo "$test_files" | grep "^${test_dir}")

        if [[ -n "$mod_tests" ]]; then
            local mod_test_count
            mod_test_count=$(echo "$mod_tests" | grep -c .)

            # Batch JUnit detection
            local mod_junit5 mod_junit4
            mod_junit5=$(echo "$mod_tests" | xargs grep -l "org.junit.jupiter" 2>/dev/null | wc -l | tr -d ' ')
            mod_junit4=$(echo "$mod_tests" | xargs grep -l "org.junit.Test\|org.junit.Before" 2>/dev/null | wc -l | tr -d ' ')

            test_inventory+=("{\"module\": \"$mod\", \"test_count\": $mod_test_count, \"junit5\": $mod_junit5, \"junit4\": $mod_junit4, \"source\": \"$mod/src/test/java/\"}")
            total_tests=$((total_tests + mod_test_count))
            junit5_count=$((junit5_count + mod_junit5))
            junit4_count=$((junit4_count + mod_junit4))
        fi
    done < <(ls -d "$REPO_ROOT"/yawl-* 2>/dev/null | xargs -n1 basename)

    # Find test resources using cached discovery
    local test_resource_count=0
    if [[ -n "${_DISCOVERY_RESOURCE_FILES:-}" ]]; then
        test_resource_count=$(echo "$_DISCOVERY_RESOURCE_FILES" | grep -c "/test/resources/")
    else
        test_resource_count=$(find "$REPO_ROOT" -path "*/test/resources/*" -type f 2>/dev/null | wc -l | tr -d ' ')
    fi

    # Check for JaCoCo data
    local jacoco_exec_exists=false
    [[ -f "$REPO_ROOT/target/jacoco.exec" ]] && jacoco_exec_exists=true

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "summary": {\n'
        printf '    "total_test_files": %d,\n' "$total_tests"
        printf '    "modules_with_tests": %d,\n' "${#test_inventory[@]}"
        printf '    "test_resource_files": %d,\n' "$test_resource_count"
        printf '    "jacoco_exec_available": %s,\n' "$jacoco_exec_exists"
        printf '    "junit5_count": %d,\n' "$junit5_count"
        printf '    "junit4_count": %d\n' "$junit4_count"
        printf '  },\n'
        printf '  "test_inventory": [\n'
        local first=true
        for entry in "${test_inventory[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$entry"
        done
        printf '\n  ],\n'
        printf '  "coverage_hints": {\n'
        printf '    "minimum_line_coverage": 0.65,\n'
        printf '    "minimum_branch_coverage": 0.55,\n'
        printf '    "profile": "coverage",\n'
        printf '    "run_command": "mvn -T 1.5C clean verify -P coverage"\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_tests" "$op_elapsed"
    log_ok "Tests: $total_tests files in ${#test_inventory[@]} modules (shared: $shared_test_count)"
}

# ==========================================================================
# GATES: Quality gates configuration
# ==========================================================================
_emit_gates_impl() {
    local out="$FACTS_DIR/gates.json"
    local op_start
    op_start=$(epoch_ms)
    log_info "Emitting facts/gates.json ..."

    # Parse profiles from pom.xml
    local -a profiles=()
    while IFS= read -r profile_id; do
        [[ -n "$profile_id" ]] && profiles+=("$profile_id")
    done < <(grep -oP '(?<=<id>)[^<]+(?=</id>)' "$REPO_ROOT/pom.xml" 2>/dev/null | grep -v "^[a-f0-9]\+$" | head -20)

    # Check for quality plugins in pom.xml
    local has_spotbugs=false
    local has_checkstyle=false
    local has_pmd=false
    local has_jacoco=false
    local has_enforcer=false
    local has_dependency_check=false

    grep -q "spotbugs-maven-plugin" "$REPO_ROOT/pom.xml" && has_spotbugs=true
    grep -q "maven-checkstyle-plugin" "$REPO_ROOT/pom.xml" && has_checkstyle=true
    grep -q "maven-pmd-plugin" "$REPO_ROOT/pom.xml" && has_pmd=true
    grep -q "jacoco-maven-plugin" "$REPO_ROOT/pom.xml" && has_jacoco=true
    grep -q "maven-enforcer-plugin" "$REPO_ROOT/pom.xml" && has_enforcer=true
    grep -q "dependency-check-maven" "$REPO_ROOT/pom.xml" && has_dependency_check=true

    {
        printf '{\n'
        printf '  "generated_at": "%s",\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
        printf '  "commit": "%s",\n' "$(git_commit)"
        printf '  "profiles": [\n'
        local first=true
        for p in "${profiles[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    "%s"' "$p"
        done
        printf '\n  ],\n'
        printf '  "plugins": {\n'
        printf '    "spotbugs": {"enabled": %s, "phase": "verify", "fail_on_error": false},\n' "$has_spotbugs"
        printf '    "checkstyle": {"enabled": %s, "phase": "verify", "fail_on_error": false},\n' "$has_checkstyle"
        printf '    "pmd": {"enabled": %s, "phase": "verify", "fail_on_error": false},\n' "$has_pmd"
        printf '    "jacoco": {"enabled": %s, "line_coverage": 0.65, "branch_coverage": 0.55},\n' "$has_jacoco"
        printf '    "enforcer": {"enabled": %s, "rules": ["requireUpperBoundDeps", "banDuplicatePomDependencyVersions"]},\n' "$has_enforcer"
        printf '    "dependency-check": {"enabled": %s, "fail_on_cvss": 7}\n' "$has_dependency_check"
        printf '  },\n'
        printf '  "default_active_gates": {\n'
        printf '    "java25": ["compile", "test"],\n'
        printf '    "ci": ["jacoco", "spotbugs", "enforcer"],\n'
        printf '    "analysis": ["jacoco", "spotbugs", "checkstyle", "pmd"],\n'
        printf '    "prod": ["jacoco", "spotbugs", "dependency-check"]\n'
        printf '  },\n'
        printf '  "gated_profiles": {\n'
        printf '    "ci": {\n'
        printf '      "jacoco_halt_on_failure": true,\n'
        printf '      "spotbugs_fail_on_error": true,\n'
        printf '      "enforcer_fail": true\n'
        printf '    },\n'
        printf '    "analysis": {\n'
        printf '      "jacoco_halt_on_failure": false,\n'
        printf '      "spotbugs_fail_on_error": false,\n'
        printf '      "checkstyle_fail_on_error": false,\n'
        printf '      "pmd_fail_on_error": false\n'
        printf '    },\n'
        printf '    "prod": {\n'
        printf '      "dependency_check_fail_on_cvss": 7,\n'
        printf '      "spotbugs_fail_on_error": true\n'
        printf '    }\n'
        printf '  }\n'
        printf '}\n'
    } > "$out"

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_gates" "$op_elapsed"
    log_ok "Gates: ${#profiles[@]} profiles, plugins: spotbugs=$has_spotbugs checkstyle=$has_checkstyle pmd=$has_pmd"
}

# ==========================================================================
# PUBLIC WRAPPER FUNCTIONS (with incremental caching)
# ==========================================================================

emit_shared_src() {
    emit_if_stale "facts/shared-src.json" _emit_shared_src_impl
}

emit_dual_family() {
    emit_if_stale "facts/dual-family.json" _emit_dual_family_impl
}

emit_duplicates() {
    emit_if_stale "facts/duplicates.json" _emit_duplicates_impl
}

emit_tests() {
    emit_if_stale "facts/tests.json" _emit_tests_impl
}

emit_gates() {
    emit_if_stale "facts/gates.json" _emit_gates_impl
}

# ==========================================================================
# MAIN DISPATCHER
# ==========================================================================
emit_all_architecture_facts() {
    local op_start
    op_start=$(epoch_ms)

    emit_shared_src
    emit_dual_family
    emit_duplicates
    emit_tests
    emit_gates

    local op_elapsed=$(( $(epoch_ms) - op_start ))
    record_operation "emit_all_architecture_facts" "$op_elapsed"
    log_ok "All architecture facts emitted in ${op_elapsed}ms"
}
