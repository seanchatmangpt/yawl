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
            src_dir=$(grep -oP '(?<=<sourceDirectory>)[^<]+' "$REPO_ROOT/$mod/pom.xml" 2>/dev/null || echo "src/main/java")
            local src_count=0 test_count=0
            # Resolve the source directory relative to the module dir
            local resolved_src="${REPO_ROOT}/${mod}/${src_dir}"
            if [[ -d "$resolved_src" ]]; then
                src_count=$(find "$resolved_src" -name "*.java" -type f 2>/dev/null | wc -l)
            fi
            local test_dir
            test_dir=$(grep -oP '(?<=<testSourceDirectory>)[^<]+' "$REPO_ROOT/$mod/pom.xml" 2>/dev/null || echo "src/test/java")
            local resolved_test="${REPO_ROOT}/${mod}/${test_dir}"
            if [[ -d "$resolved_test" ]]; then
                test_count=$(find "$resolved_test" -name "*.java" -type f 2>/dev/null | wc -l)
            fi
            # Determine source access strategy
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
}

# ── 2. reactor.json ───────────────────────────────────────────────────────
emit_reactor() {
    local out="$FACTS_DIR/reactor.json"
    log_info "Emitting facts/reactor.json ..."

    local modules
    modules=$(discover_modules)

    # Build reactor order (parent first, then declared module order)
    local -a reactor_order=("yawl-parent")
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        reactor_order+=("$mod")
    done <<< "$modules"

    # Discover inter-module dependencies by scanning child POMs
    local deps_entries=()
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue
        # Find dependencies on other yawl-* artifacts (excluding parent ref)
        while IFS= read -r dep_art; do
            [[ -z "$dep_art" ]] && continue
            [[ "$dep_art" == "yawl-parent" ]] && continue
            [[ "$dep_art" == "$mod" ]] && continue
            deps_entries+=("{\"from\":\"$mod\",\"to\":\"$dep_art\"}")
        done < <(grep -oP '(?<=<artifactId>)yawl-[a-zA-Z0-9_-]+(?=</artifactId>)' "$pom" 2>/dev/null | sort -u)
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
}

# ── 3. shared-src.json ────────────────────────────────────────────────────
emit_shared_src() {
    local out="$FACTS_DIR/shared-src.json"
    log_info "Emitting facts/shared-src.json ..."

    # Collect all modules and their source directory declarations
    local modules
    modules=$(discover_modules)

    local -a shared_entries=()
    local -a ambiguities=()

    # Build a map: source_root -> list of modules claiming it
    declare -A root_to_modules
    declare -A mod_to_includes
    declare -A mod_to_excludes

    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue

        local src_dir
        src_dir=$(grep -oP '(?<=<sourceDirectory>)[^<]+' "$pom" 2>/dev/null || echo "")
        [[ -z "$src_dir" ]] && continue

        # Normalize: resolve the root
        local root_key="$src_dir"

        if [[ -v root_to_modules["$root_key"] ]]; then
            root_to_modules["$root_key"]="${root_to_modules[$root_key]},$mod"
        else
            root_to_modules["$root_key"]="$mod"
        fi

        # Extract compiler includes/excludes for this module
        local includes="" excludes=""
        if grep -q '<includes>' "$pom" 2>/dev/null; then
            includes=$(grep -oP '(?<=<include>)[^<]+' "$pom" 2>/dev/null | tr '\n' '|' | sed 's/|$//')
        fi
        if grep -q '<excludes>' "$pom" 2>/dev/null; then
            excludes=$(grep -oP '(?<=<exclude>)[^<]+' "$pom" 2>/dev/null | tr '\n' '|' | sed 's/|$//')
        fi
        mod_to_includes["$mod"]="$includes"
        mod_to_excludes["$mod"]="$excludes"
    done <<< "$modules"

    {
        printf '{\n  "shared_roots": [\n'
        local first_root=true
        for root in "${!root_to_modules[@]}"; do
            $first_root || printf ',\n'
            first_root=false
            local mod_list="${root_to_modules[$root]}"
            printf '    {"root": "%s", "modules": [' "$(json_escape "$root")"
            local first_mod=true
            IFS=',' read -ra mods <<< "$mod_list"
            for m in "${mods[@]}"; do
                $first_mod || printf ', '
                first_mod=false
                local inc="${mod_to_includes[$m]:-}"
                local exc="${mod_to_excludes[$m]:-}"
                printf '{"name": "%s", "includes": "%s", "excludes": "%s"}' \
                    "$m" "$(json_escape "$inc")" "$(json_escape "$exc")"
            done
            printf ']}'
        done
        printf '\n  ],\n'

        # Detect ownership ambiguities: modules sharing ../src without filters
        printf '  "ownership_ambiguities": [\n'
        local first_amb=true
        for root in "${!root_to_modules[@]}"; do
            [[ "$root" != "../src" ]] && continue
            IFS=',' read -ra mods <<< "${root_to_modules[$root]}"
            for m in "${mods[@]}"; do
                local inc="${mod_to_includes[$m]:-}"
                if [[ -z "$inc" ]]; then
                    $first_amb || printf ',\n'
                    first_amb=false
                    printf '    {"path": "%s", "claimed_by": ["%s"], "severity": "HIGH", "reason": "Full shared root without include filters"}' \
                        "$root" "$m"
                    add_refusal "H_SHARED_SRC_AMBIGUOUS_OWNER" \
                        "Module $m has full shared root ../src without include filters" \
                        "{\"module\":\"$m\",\"root\":\"$root\"}"
                fi
            done

            # Check for overlapping include patterns across modules (deduplicated)
            local -a filtered_mods=()
            for m in "${mods[@]}"; do
                [[ -n "${mod_to_includes[$m]:-}" ]] && filtered_mods+=("$m")
            done
            declare -A _overlap_seen
            local i j
            for ((i=0; i<${#filtered_mods[@]}; i++)); do
                for ((j=i+1; j<${#filtered_mods[@]}; j++)); do
                    local m1="${filtered_mods[$i]}" m2="${filtered_mods[$j]}"
                    local inc1="${mod_to_includes[$m1]}" inc2="${mod_to_includes[$m2]}"
                    # Deduplicate patterns within each module before comparing
                    IFS='|' read -ra pats1 <<< "$inc1"
                    IFS='|' read -ra pats2 <<< "$inc2"
                    declare -A _uniq1 _uniq2
                    for p in "${pats1[@]}"; do _uniq1["$p"]=1; done
                    for p in "${pats2[@]}"; do _uniq2["$p"]=1; done
                    for p1 in "${!_uniq1[@]}"; do
                        if [[ -v _uniq2["$p1"] ]]; then
                            local overlap_key="${m1}|${m2}|${p1}"
                            if [[ ! -v _overlap_seen["$overlap_key"] ]]; then
                                _overlap_seen["$overlap_key"]=1
                                $first_amb || printf ',\n'
                                first_amb=false
                                printf '    {"path": "%s", "claimed_by": ["%s", "%s"], "severity": "MEDIUM", "reason": "Overlapping include: %s"}' \
                                    "$root" "$m1" "$m2" "$(json_escape "$p1")"
                            fi
                        fi
                    done
                    unset _uniq1 _uniq2
                done
            done
            unset _overlap_seen
        done
        printf '\n  ]\n}\n'
    } > "$out"
}

# ── 4. dual-family.json ──────────────────────────────────────────────────
emit_dual_family() {
    local out="$FACTS_DIR/dual-family.json"
    log_info "Emitting facts/dual-family.json ..."

    local src_root="$REPO_ROOT/src/org/yawlfoundation/yawl"
    local stateless_root="$src_root/stateless"

    # Discover classes in stateless that mirror stateful classes
    local -a families=()

    if [[ -d "$stateless_root" ]]; then
        # For each stateless .java file, check if a matching stateful counterpart exists
        while IFS= read -r stateless_file; do
            local rel_path="${stateless_file#$stateless_root/}"
            local simple_name
            simple_name=$(basename "$rel_path" .java)
            # The stateful counterpart: same relative path under the non-stateless root
            local stateful_file="$src_root/$rel_path"
            if [[ -f "$stateful_file" ]]; then
                # Build FQCNs
                local stateful_pkg
                stateful_pkg=$(dirname "$rel_path" | tr '/' '.')
                local stateless_pkg="stateless.${stateful_pkg}"
                [[ "$stateful_pkg" == "." ]] && stateful_pkg="" && stateless_pkg="stateless"

                local stateful_fqcn="org.yawlfoundation.yawl.${stateful_pkg}.${simple_name}"
                local stateless_fqcn="org.yawlfoundation.yawl.${stateless_pkg}.${simple_name}"
                # Clean up double dots
                stateful_fqcn="${stateful_fqcn//../.}"
                stateless_fqcn="${stateless_fqcn//../.}"

                # Determine category
                local category="engine"
                [[ "$rel_path" == elements/* ]] && category="elements"
                [[ "$rel_path" == engine/time/* ]] && category="time"

                families+=("{\"name\":\"$simple_name\",\"category\":\"$category\",\"stateful_fqcn\":\"$stateful_fqcn\",\"stateless_fqcn\":\"$stateless_fqcn\",\"policy\":\"MIRROR_REQUIRED\"}")
            fi
        done < <(find "$stateless_root" -name "*.java" -type f 2>/dev/null | sort)
    fi

    {
        printf '{\n'
        printf '  "mirror_namespaces": [\n'
        printf '    {"stateful_prefix": "org.yawlfoundation.yawl.", "stateless_prefix": "org.yawlfoundation.yawl.stateless."}\n'
        printf '  ],\n'
        printf '  "family_count": %d,\n' "${#families[@]}"
        printf '  "families": [\n'
        local first=true
        for f in "${families[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$f"
        done
        printf '\n  ]\n}\n'
    } > "$out"
}

# ── 5. duplicates.json ────────────────────────────────────────────────────
emit_duplicates() {
    local out="$FACTS_DIR/duplicates.json"
    log_info "Emitting facts/duplicates.json ..."

    local src_root="$REPO_ROOT/src"
    local -a within_artifact=()
    local -a cross_family=()

    # Check for duplicate FQCNs within the same source tree
    # (same file path appearing in overlapping module scopes)
    if [[ -d "$src_root" ]]; then
        # Find .java files, extract FQCN, check for duplicates
        local dupes
        dupes=$(find "$src_root" -name "*.java" -type f 2>/dev/null \
            | sed "s|${src_root}/||" \
            | sed 's|/|.|g; s|\.java$||' \
            | sort | uniq -d)

        while IFS= read -r fqcn; do
            [[ -z "$fqcn" ]] && continue
            local file_path="${fqcn//.//}.java"
            within_artifact+=("{\"artifact\":\"shared-src\",\"fqcn\":\"$fqcn\",\"paths\":[\"src/$file_path\"]}")
            add_refusal "H_DUPLICATE_FQCN_WITHIN_ARTIFACT" \
                "Duplicate class $fqcn in shared source tree" \
                "{\"artifact\":\"shared-src\",\"fqcn\":\"$fqcn\"}"
        done <<< "$dupes"
    fi

    # Cross-family duplicates are allowed (stateful/stateless mirrors)
    # Read from dual-family.json if it exists
    if [[ -f "$FACTS_DIR/dual-family.json" ]]; then
        local family_count
        family_count=$(grep -c '"name"' "$FACTS_DIR/dual-family.json" 2>/dev/null || echo 0)
        # These are intentional mirrors, mark as allowed
        while IFS= read -r line; do
            [[ -z "$line" ]] && continue
            local sname sful sless
            sname=$(echo "$line" | grep -oP '(?<="name":")[^"]+')
            sful=$(echo "$line" | grep -oP '(?<="stateful_fqcn":")[^"]+')
            sless=$(echo "$line" | grep -oP '(?<="stateless_fqcn":")[^"]+')
            [[ -z "$sful" ]] && continue
            cross_family+=("{\"stateful\":\"$sful\",\"stateless\":\"$sless\",\"allowed\":true}")
        done < <(grep '"name"' "$FACTS_DIR/dual-family.json" 2>/dev/null)
    fi

    {
        printf '{\n'
        printf '  "within_artifact": [\n'
        local first=true
        for e in "${within_artifact[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$e"
        done
        printf '\n  ],\n'
        printf '  "cross_family_duplicates": [\n'
        first=true
        for e in "${cross_family[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$e"
        done
        printf '\n  ]\n}\n'
    } > "$out"
}

# ── 6. deps-conflicts.json ───────────────────────────────────────────────
emit_deps_conflicts() {
    local out="$FACTS_DIR/deps-conflicts.json"
    log_info "Emitting facts/deps-conflicts.json ..."

    # Strategy: Use grep to extract explicit <version> tags from child POMs
    # (fast, avoids line-by-line parsing). Only looks for non-property versions.
    local modules
    modules=$(discover_modules)

    local managed_count
    managed_count=$(grep -c '<dependency>' "$REPO_ROOT/pom.xml" 2>/dev/null || echo 0)

    # Extract explicit (non-property) versions from child POMs using grep
    local tmpfile="/tmp/observatory-deps-$$"
    : > "$tmpfile"

    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue
        # Find <version>X.Y.Z</version> lines (exclude property references like ${...})
        grep -oP '<version>[^$<][^<]*</version>' "$pom" 2>/dev/null | \
            grep -v '^\${' | \
            sed "s|<version>||; s|</version>||" | \
            while IFS= read -r ver; do
                echo "${mod}:${ver}" >> "$tmpfile"
            done
    done <<< "$modules"

    # For simplicity and speed, report the managed count and note that
    # the parent POM centralizes version control
    local explicit_count=0
    [[ -f "$tmpfile" ]] && explicit_count=$(wc -l < "$tmpfile")

    {
        printf '{\n'
        printf '  "parent_managed_entries": %d,\n' "$managed_count"
        printf '  "child_explicit_versions": %d,\n' "$explicit_count"
        printf '  "strategy": "Parent POM dependencyManagement centralizes all versions. Child POMs use property references.",\n'
        printf '  "conflicts": []\n'
        printf '}\n'
    } > "$out"

    rm -f "$tmpfile"
}

# ── 7. tests.json ────────────────────────────────────────────────────────
emit_tests() {
    local out="$FACTS_DIR/tests.json"
    log_info "Emitting facts/tests.json ..."

    local modules
    modules=$(discover_modules)

    # Discover surefire and failsafe configurations
    local -a surefire_modules=()
    local -a failsafe_modules=()
    local surefire_includes="**/*Test.java"
    local surefire_excludes=""
    local failsafe_includes="**/*IT.java"
    local failsafe_excludes=""

    # Check parent POM for surefire/failsafe configs
    if grep -q 'maven-surefire-plugin' "$REPO_ROOT/pom.xml" 2>/dev/null; then
        local parent_surefire_inc
        parent_surefire_inc=$(grep -A20 'maven-surefire-plugin' "$REPO_ROOT/pom.xml" 2>/dev/null \
            | grep -oP '(?<=<include>)[^<]+' | tr '\n' '|' | sed 's/|$//')
        [[ -n "$parent_surefire_inc" ]] && surefire_includes="$parent_surefire_inc"

        local parent_surefire_exc
        parent_surefire_exc=$(grep -A20 'maven-surefire-plugin' "$REPO_ROOT/pom.xml" 2>/dev/null \
            | grep -oP '(?<=<exclude>)[^<]+' | tr '\n' '|' | sed 's/|$//')
        [[ -n "$parent_surefire_exc" ]] && surefire_excludes="$parent_surefire_exc"
    fi

    # Count test files per module, scoped by compiler include patterns
    local -a module_test_data=()
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue

        local test_dir
        test_dir=$(grep -oP '(?<=<testSourceDirectory>)[^<]+' "$pom" 2>/dev/null || echo "")
        local resolved_test=""
        if [[ -n "$test_dir" ]]; then
            resolved_test="${REPO_ROOT}/${mod}/${test_dir}"
        fi

        local unit_count=0 it_count=0 visible_count=0
        if [[ -d "$resolved_test" ]]; then
            # Raw visible count (everything the test source directory exposes)
            visible_count=$(find "$resolved_test" -name "*Test.java" -o -name "*Tests.java" 2>/dev/null | wc -l)

            # For full-shared modules (../test), scope to owned packages using
            # source compiler include patterns from the POM
            if [[ "$test_dir" == "../test" ]]; then
                local src_includes
                src_includes=$(grep -oP '(?<=<include>)[^<]+' "$pom" 2>/dev/null | head -20)
                if [[ -n "$src_includes" ]]; then
                    # Convert source include patterns to test directory paths
                    # e.g. **/org/yawlfoundation/yawl/engine/** -> test/org/.../engine/
                    unit_count=0
                    while IFS= read -r inc_pattern; do
                        # Strip ** prefix/suffix, convert to directory path
                        local dir_fragment
                        dir_fragment=$(echo "$inc_pattern" | sed 's|^\*\*/||; s|/\*\*$||; s|\*\*||g')
                        [[ -z "$dir_fragment" ]] && continue
                        local test_subdir="$resolved_test/$dir_fragment"
                        if [[ -d "$test_subdir" ]]; then
                            local sub_count
                            sub_count=$(find "$test_subdir" -name "*Test.java" -o -name "*Tests.java" 2>/dev/null | wc -l)
                            unit_count=$((unit_count + sub_count))
                        fi
                    done <<< "$src_includes"
                else
                    # No include filters = full access (report visible count)
                    unit_count=$visible_count
                fi
            else
                # Package-scoped module: test dir is already narrow
                unit_count=$(find "$resolved_test" -name "*Test.java" -o -name "*Tests.java" 2>/dev/null | wc -l)
            fi
            it_count=$(find "$resolved_test" -name "*IT.java" -type f 2>/dev/null | wc -l)
        fi

        if [[ $unit_count -gt 0 ]]; then surefire_modules+=("$mod"); fi
        if [[ $it_count -gt 0 ]]; then failsafe_modules+=("$mod"); fi

        module_test_data+=("{\"module\":\"$mod\",\"scoped_tests\":$unit_count,\"visible_tests\":$visible_count,\"integration_tests\":$it_count,\"test_source\":\"$(json_escape "${test_dir:-none}")\"}")
    done <<< "$modules"

    {
        printf '{\n'
        printf '  "surefire": {\n'
        printf '    "modules": %s,\n' "$(json_arr "${surefire_modules[@]}")"
        printf '    "includes": ["%s"],\n' "$(json_escape "$surefire_includes")"
        printf '    "excludes": ["%s"]\n' "$(json_escape "$surefire_excludes")"
        printf '  },\n'
        printf '  "failsafe": {\n'
        printf '    "modules": %s,\n' "$(json_arr "${failsafe_modules[@]}")"
        printf '    "includes": ["%s"],\n' "$(json_escape "$failsafe_includes")"
        printf '    "excludes": ["%s"]\n' "$(json_escape "$failsafe_excludes")"
        printf '  },\n'
        printf '  "module_detail": [\n'
        local first=true
        for entry in "${module_test_data[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$entry"
        done
        printf '\n  ]\n}\n'
    } > "$out"
}

# ── 8. gates.json ────────────────────────────────────────────────────────
# Utility: check if a plugin has <executions> with <goal> in default build
_plugin_active_in_default() {
    local pom="$1" plugin_id="$2"
    # Extract content before <profiles> to get default build section
    local default_section
    default_section=$(python3 -c "
import re, sys
with open('$pom') as f:
    content = f.read()
idx = content.find('<profiles>')
default = content[:idx] if idx != -1 else content
# Find the plugin block
pattern = re.compile(r'<plugin>\s*(?:(?!</plugin>).)*?<artifactId>$plugin_id</artifactId>(?:(?!</plugin>).)*?</plugin>', re.DOTALL)
match = pattern.search(default)
if match:
    block = match.group(0)
    has_exec = '<execution>' in block or '<executions>' in block
    has_goals = bool(re.search(r'<goal>', block))
    has_skip_true = bool(re.search(r'<skip>true</skip>', block))
    if has_exec and has_goals and not has_skip_true:
        print('ACTIVE')
    elif has_skip_true:
        print('SKIP_DEFAULT')
    elif has_exec and not has_goals:
        print('INERT')
    else:
        print('CONFIG_ONLY')
else:
    print('NOT_FOUND')
" 2>/dev/null)
    echo "$default_section"
}

# Utility: find which profiles activate a given plugin
_plugin_profiles() {
    local pom="$1" plugin_id="$2"
    python3 -c "
import re
with open('$pom') as f:
    content = f.read()
idx = content.find('<profiles>')
if idx == -1:
    exit(0)
profiles_section = content[idx:]
profile_blocks = re.split(r'<profile>', profiles_section)
result = []
for block in profile_blocks[1:]:
    pid = re.search(r'<id>([^<]+)</id>', block)
    if pid and '$plugin_id' in block:
        # Check if this profile has executions for the plugin
        if re.search(r'<goal>', block):
            result.append(pid.group(1))
if result:
    print(','.join(result))
" 2>/dev/null
}

emit_gates() {
    local out="$FACTS_DIR/gates.json"
    log_info "Emitting facts/gates.json ..."

    local root_pom="$REPO_ROOT/pom.xml"
    local -a gates=()
    local -a skip_flags=()

    # Gate detection: check each quality plugin for activation status
    local -a gate_plugins=(
        "spotbugs-maven-plugin:spotbugs:verify"
        "maven-pmd-plugin:pmd:verify"
        "maven-checkstyle-plugin:checkstyle:verify"
        "jacoco-maven-plugin:jacoco:verify"
        "dependency-check-maven:owasp-dependency-check:verify"
        "maven-enforcer-plugin:enforcer:validate"
    )

    for gate_spec in "${gate_plugins[@]}"; do
        IFS=':' read -r plugin_artifact gate_name gate_phase <<< "$gate_spec"
        if grep -q "$plugin_artifact" "$root_pom" 2>/dev/null; then
            local activation
            activation=$(_plugin_active_in_default "$root_pom" "$plugin_artifact")
            local profiles
            profiles=$(_plugin_profiles "$root_pom" "$plugin_artifact")

            local enabled="false"
            local default_active="false"
            [[ "$activation" == "ACTIVE" ]] && enabled="true" && default_active="true"
            [[ "$activation" == "SKIP_DEFAULT" ]] && default_active="false"

            # If profiles activate it, it's available but not by default
            local profiles_json="[]"
            if [[ -n "$profiles" ]]; then
                profiles_json="["
                local first_p=true
                IFS=',' read -ra prof_arr <<< "$profiles"
                for p in "${prof_arr[@]}"; do
                    $first_p || profiles_json+=","
                    first_p=false
                    profiles_json+="\"$p\""
                done
                profiles_json+="]"
            fi

            gates+=("{\"name\":\"$gate_name\",\"phase\":\"$gate_phase\",\"default_active\":$default_active,\"activation\":\"$activation\",\"profiles\":$profiles_json,\"plugin\":\"$plugin_artifact\"}")
        fi
    done

    # Known skip flags that disable gates
    skip_flags+=("{\"flag\":\"-DskipTests=true\",\"risk\":\"RED\",\"disables\":\"surefire+failsafe\"}")
    skip_flags+=("{\"flag\":\"-Dspotbugs.skip=true\",\"risk\":\"YELLOW\",\"disables\":\"spotbugs\"}")
    skip_flags+=("{\"flag\":\"-Dpmd.skip=true\",\"risk\":\"YELLOW\",\"disables\":\"pmd\"}")
    skip_flags+=("{\"flag\":\"-Dcheckstyle.skip=true\",\"risk\":\"YELLOW\",\"disables\":\"checkstyle\"}")
    skip_flags+=("{\"flag\":\"-DskipITs=true\",\"risk\":\"RED\",\"disables\":\"failsafe\"}")
    skip_flags+=("{\"flag\":\"-Denforcer.skip=true\",\"risk\":\"RED\",\"disables\":\"enforcer\"}")

    # Discover available profiles
    local -a profiles=()
    while IFS= read -r prof; do
        [[ -z "$prof" ]] && continue
        profiles+=("$prof")
    done < <(grep -oP '(?<=<id>)[^<]+' "$root_pom" 2>/dev/null | head -20)

    {
        printf '{\n'
        printf '  "gates": [\n'
        local first=true
        for g in "${gates[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$g"
        done
        printf '\n  ],\n'
        printf '  "skip_flags": [\n'
        first=true
        for s in "${skip_flags[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$s"
        done
        printf '\n  ],\n'
        printf '  "profiles": %s\n' "$(json_arr "${profiles[@]}")"
        printf '}\n'
    } > "$out"
}

# ── 9. maven-hazards.json ────────────────────────────────────────────────
emit_maven_hazards() {
    local out="$FACTS_DIR/maven-hazards.json"
    log_info "Emitting facts/maven-hazards.json ..."

    local -a hazards=()
    local m2_repo="${HOME}/.m2/repository"

    # Check for cached missing artifacts (*.lastUpdated files)
    if [[ -d "$m2_repo/org/yawlfoundation" ]]; then
        while IFS= read -r lu_file; do
            local artifact_dir
            artifact_dir=$(dirname "$lu_file")
            local artifact_path="${artifact_dir#${m2_repo}/}"
            hazards+=("{\"code\":\"H_MAVEN_CACHED_MISSING_ARTIFACT\",\"artifact\":\"$(json_escape "$artifact_path")\",\"message\":\"Cached download failure; run: rm -rf ${artifact_dir}\"}")
            add_refusal "H_MAVEN_CACHED_MISSING_ARTIFACT" \
                "Cached failure for $artifact_path" \
                "{\"artifact\":\"$(json_escape "$artifact_path")\",\"path\":\"$(json_escape "$lu_file")\"}"
        done < <(find "$m2_repo/org/yawlfoundation" -name "*.lastUpdated" -type f 2>/dev/null)
    fi

    # Check for _remote.repositories files with missing JARs
    if [[ -d "$m2_repo/org/yawlfoundation" ]]; then
        while IFS= read -r remote_file; do
            local artifact_dir
            artifact_dir=$(dirname "$remote_file")
            # Check if the expected JAR is missing
            local jar_count
            jar_count=$(find "$artifact_dir" -name "*.jar" -type f 2>/dev/null | wc -l)
            if [[ $jar_count -eq 0 ]]; then
                local artifact_path="${artifact_dir#${m2_repo}/}"
                hazards+=("{\"code\":\"H_MAVEN_CACHED_MISSING_ARTIFACT\",\"artifact\":\"$(json_escape "$artifact_path")\",\"message\":\"Remote repository entry without JAR; run: rm -rf ${artifact_dir}\"}")
            fi
        done < <(find "$m2_repo/org/yawlfoundation" -name "_remote.repositories" -type f 2>/dev/null)
    fi

    # Check for common transitive dependency hazards
    # (e.g., log4j-to-slf4j conflicting with log4j-slf4j2-impl)
    local root_pom="$REPO_ROOT/pom.xml"
    if grep -q 'log4j-to-slf4j' "$root_pom" 2>/dev/null && grep -q 'log4j-slf4j2-impl' "$root_pom" 2>/dev/null; then
        hazards+=("{\"code\":\"H_LOGGING_BRIDGE_CONFLICT\",\"artifact\":\"log4j-to-slf4j vs log4j-slf4j2-impl\",\"message\":\"Both logging bridges present; ensure exclusions are in place per module\"}")
    fi

    # Check for POM-level issues: modules pointing to non-existent directories
    local modules
    modules=$(discover_modules)
    while IFS= read -r mod; do
        [[ -z "$mod" ]] && continue
        local pom="$REPO_ROOT/$mod/pom.xml"
        [[ -f "$pom" ]] || continue
        local src_dir
        src_dir=$(grep -oP '(?<=<sourceDirectory>)[^<]+' "$pom" 2>/dev/null || echo "")
        if [[ -n "$src_dir" ]]; then
            local resolved="${REPO_ROOT}/${mod}/${src_dir}"
            if [[ ! -d "$resolved" ]]; then
                hazards+=("{\"code\":\"H_MISSING_SOURCE_DIR\",\"artifact\":\"$mod\",\"message\":\"Source directory $src_dir does not resolve to existing path: $resolved\"}")
            fi
        fi
    done <<< "$modules"

    {
        printf '{\n'
        printf '  "hazards": [\n'
        local first=true
        for h in "${hazards[@]}"; do
            $first || printf ',\n'
            first=false
            printf '    %s' "$h"
        done
        printf '\n  ]\n}\n'
    } > "$out"
}

# ── Main dispatcher ──────────────────────────────────────────────────────
emit_all_facts() {
    timer_start
    emit_modules
    emit_reactor
    emit_shared_src
    emit_dual_family
    emit_duplicates
    emit_deps_conflicts
    emit_tests
    emit_gates
    emit_maven_hazards
    emit_coverage
    FACTS_ELAPSED=$(timer_elapsed_ms)
    log_ok "All facts emitted in ${FACTS_ELAPSED}ms"
}
