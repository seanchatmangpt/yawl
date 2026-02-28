#!/usr/bin/env bash
# ==========================================================================
# dx.sh — Fast Build-Test Loop for Code Agents
#
# Detects which modules have uncommitted changes, builds only those modules
# (plus their dependencies), and runs only their tests. Skips all overhead:
# JaCoCo, javadoc, static analysis, integration tests. Fails fast.
#
# Usage:
#   bash scripts/dx.sh                  # compile + test changed modules
#   bash scripts/dx.sh compile          # compile only (changed modules)
#   bash scripts/dx.sh test             # test only (changed modules, assumes compiled)
#   bash scripts/dx.sh all              # compile + test ALL modules
#   bash scripts/dx.sh compile all      # compile ALL modules
#   bash scripts/dx.sh test all         # test ALL modules
#   bash scripts/dx.sh -pl mod1,mod2    # explicit module list
#   bash scripts/dx.sh --warm-cache     # enable warm cache for yawl-engine, yawl-elements
#   bash scripts/dx.sh --impact-graph   # use test impact graph to run affected tests only
#   bash scripts/dx.sh --feedback       # run feedback tier tests (1-2 per module, <5s total)
#   bash scripts/dx.sh --stateless      # enable stateless test execution (H2 snapshots)
#   bash scripts/dx.sh test --fail-fast-tier 1  # test with Tier 1 fail-fast (fast unit tests only)
#   bash scripts/dx.sh test --fail-fast-tier 2  # test Tiers 1-2, stop at first failure
#
# Environment:
#   DX_OFFLINE=1       Force offline mode (default: auto-detect)
#   DX_OFFLINE=0       Force online mode
#   DX_FAIL_AT=end     Don't stop on first module failure (default: fast)
#   DX_VERBOSE=1       Show Maven output (default: quiet)
#   DX_CLEAN=1         Run clean phase (default: incremental)
#   DX_TIMINGS=1       Capture build timing metrics (default: off)
#   DX_IMPACT=1        Use test impact graph for source-driven test selection
#   DX_SEMANTIC_FILTER=1  Skip formatting-only changes (detect via semantic hash, default: off)
#   DX_WARM_CACHE=1    Enable warm bytecode cache for hot modules (default: off)
#   DX_FEEDBACK=1      Run feedback tier tests (fast smoke tests, <5s)
#   DX_STATELESS=1     Enable stateless test execution with H2 snapshots
#   TEP_FAIL_FAST_TIER=N  Run tests up to tier N with fail-fast (default: disabled)
#   TEP_CONTINUE_ON_FAILURE=1  Continue running remaining tiers after failures (default: 0)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Java 25 enforcement ────────────────────────────────────────────────────
# JAVA_HOME may point to Java 21 (system default) even when Temurin 25 is
# installed. Maven uses JAVA_HOME to locate javac, so we correct it here.
# This is authoritative for every dx.sh invocation regardless of shell env.
_TEMURIN25="/usr/lib/jvm/temurin-25-jdk-amd64"
if [ -d "${_TEMURIN25}" ]; then
    _current_major=$(java -version 2>&1 | grep 'version "' | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "${_current_major}" != "25" ] || [ "${JAVA_HOME:-}" != "${_TEMURIN25}" ]; then
        export JAVA_HOME="${_TEMURIN25}"
        export PATH="${JAVA_HOME}/bin:${PATH}"
    fi
fi

# ── Parse arguments ───────────────────────────────────────────────────────
PHASE="compile-test"
SCOPE="changed"
EXPLICIT_MODULES=""
USE_IMPACT_GRAPH="${DX_IMPACT:-0}"
TEP_FAIL_FAST_TIER="${TEP_FAIL_FAST_TIER:-0}"
WARM_CACHE_ENABLED="${DX_WARM_CACHE:-0}"
FEEDBACK_ENABLED="${DX_FEEDBACK:-0}"
STATELESS_ENABLED="${DX_STATELESS:-0}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        compile)              PHASE="compile";      shift ;;
        test)                 PHASE="test";         shift ;;
        all)                  SCOPE="all";          shift ;;
        -pl)                  EXPLICIT_MODULES="$2"; SCOPE="explicit"; shift 2 ;;
        --impact-graph)       USE_IMPACT_GRAPH=1; shift ;;
        --fail-fast-tier)     TEP_FAIL_FAST_TIER="$2"; shift 2 ;;
        --warm-cache)         WARM_CACHE_ENABLED=1; shift ;;
        --feedback)           FEEDBACK_ENABLED=1; shift ;;
        --stateless)          STATELESS_ENABLED=1; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)        echo "Unknown arg: $1. Use -h for help."; exit 1 ;;
    esac
done

# ── Detect offline capability ─────────────────────────────────────────────
OFFLINE_FLAG=""
if [[ "${DX_OFFLINE:-auto}" == "1" ]]; then
    OFFLINE_FLAG="-o"
elif [[ "${DX_OFFLINE:-auto}" == "auto" ]]; then
    # Check if local repo has the project installed (offline-safe)
    if [[ -d "${HOME}/.m2/repository/org/yawlfoundation/yawl-parent" ]]; then
        OFFLINE_FLAG="-o"
    fi
fi

# ── Detect changed modules ───────────────────────────────────────────────
# Topological order: every module appears after all its YAWL dependencies.
# See docs/build-sequences.md and docs/v6/diagrams/facts/reactor.json for
# the full dependency graph. Order matters for detect_changed_modules().
ALL_MODULES=(
    # Layer 0 — Foundation (no YAWL deps, parallel)
    yawl-utilities yawl-security yawl-graalpy yawl-graaljs
    # Layer 1 — First consumers (parallel)
    yawl-elements yawl-ggen yawl-graalwasm yawl-dmn yawl-data-modelling
    # Layer 2 — Core engine
    yawl-engine
    # Layer 3 — Engine extension
    yawl-stateless
    # Layer 4 — Services (parallel); authentication now AFTER engine (bug fix)
    yawl-authentication yawl-scheduling yawl-monitoring
    yawl-worklet yawl-control-panel yawl-integration yawl-webapps
    # Layer 5 — Advanced services (parallel)
    yawl-pi yawl-resourcing
    # Layer 6 — Top-level application
    yawl-mcp-a2a-app
)

# In remote/CI environments, skip modules with heavy ML dependencies (>50MB JARs)
# that cannot be downloaded through the egress proxy (onnxruntime = 89MB).
# yawl-mcp-a2a-app depends on yawl-pi, so must also be excluded transitively.
if [[ "${CLAUDE_CODE_REMOTE:-false}" == "true" ]]; then
    ALL_MODULES=($(printf '%s\n' "${ALL_MODULES[@]}" | grep -v '^yawl-pi$' | grep -v '^yawl-mcp-a2a-app$'))
fi

detect_changed_modules() {
    local changed_files
    # Get files changed relative to HEAD (staged + unstaged + untracked in src/)
    changed_files=$(git diff --name-only HEAD 2>/dev/null || true)
    changed_files+=$'\n'$(git diff --name-only --cached 2>/dev/null || true)
    changed_files+=$'\n'$(git ls-files --others --exclude-standard 2>/dev/null || true)

    local -A module_set
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        for mod in "${ALL_MODULES[@]}"; do
            if [[ "$file" == "${mod}/"* ]]; then
                module_set["$mod"]=1
                break
            fi
        done
        # Changes to parent pom or .mvn affect everything
        if [[ "$file" == "pom.xml" || "$file" == ".mvn/"* ]]; then
            echo "all"
            return
        fi
    done <<< "$changed_files"

    local result=""
    for mod in "${!module_set[@]}"; do
        [[ -n "$result" ]] && result+=","
        result+="$mod"
    done
    echo "$result"
}

# ── Semantic Change Detection ───────────────────────────────────────────────
# Filter modules to only those with actual semantic changes (not just formatting)
# Uses cached semantic hashes to avoid re-computing on every build
filter_semantic_changes() {
    local modules="$1"

    # If semantic filtering disabled, return all modules unchanged
    if [[ "${DX_SEMANTIC_FILTER:-0}" != "1" ]]; then
        echo "$modules"
        return 0
    fi

    local -a module_array
    IFS=',' read -ra module_array <<< "$modules"
    local -a changed_modules=()

    for module in "${module_array[@]}"; do
        [[ -z "$module" ]] && continue

        # Compute current semantic hash
        local current_hash
        current_hash=$(bash "${SCRIPT_DIR}/compute-semantic-hash.sh" "$module" 2>/dev/null | jq -r '.hash // empty' 2>/dev/null) || current_hash=""

        # Load cached hash
        local cached_hash_file="${REPO_ROOT}/.yawl/cache/semantic-hashes/${module}.json"
        local cached_hash=""
        if [[ -f "$cached_hash_file" ]]; then
            cached_hash=$(jq -r '.hash // empty' "$cached_hash_file" 2>/dev/null) || cached_hash=""
        fi

        # Module changed if hashes differ or no cached version exists
        if [[ -z "$cached_hash" || "$current_hash" != "$cached_hash" ]]; then
            changed_modules+=("$module")
            [[ "${DX_VERBOSE:-0}" == "1" ]] && printf "  ${C_CYAN}[SEMANTIC]${C_RESET} %s — hash changed\n" "$module" >&2
        else
            [[ "${DX_VERBOSE:-0}" == "1" ]] && printf "  ${C_CYAN}[SEMANTIC]${C_RESET} %s — no semantic change (cache hit)\n" "$module" >&2
        fi
    done

    # Return filtered modules
    local result=""
    for mod in "${changed_modules[@]}"; do
        [[ -n "$result" ]] && result+=","
        result+="$mod"
    done
    echo "$result"
}

if [[ "$SCOPE" == "changed" ]]; then
    DETECTED=$(detect_changed_modules)
    if [[ "$DETECTED" == "all" || -z "$DETECTED" ]]; then
        SCOPE="all"
    else
        EXPLICIT_MODULES="$DETECTED"
        SCOPE="explicit"

        # Apply semantic filtering if enabled (skip formatting-only changes)
        if [[ "${DX_SEMANTIC_FILTER:-0}" == "1" ]]; then
            FILTERED=$(filter_semantic_changes "$EXPLICIT_MODULES")
            if [[ -z "$FILTERED" ]]; then
                printf "${C_GREEN}✓${C_RESET} No semantic changes detected (all changes are formatting only)\n"
                exit 0
            fi
            EXPLICIT_MODULES="$FILTERED"
        fi
    fi
fi

# ── Build Maven command ──────────────────────────────────────────────────
MVN_ARGS=()

# Prefer mvnd if available
if command -v mvnd >/dev/null 2>&1; then
    MVN_CMD="mvnd"
else
    MVN_CMD="mvn"
fi

# Profile
MVN_ARGS+=("-P" "agent-dx")

# Add feedback/stateless profiles if requested
if [[ "$FEEDBACK_ENABLED" == "1" ]]; then
    MVN_ARGS+=("-P" "feedback")
fi
if [[ "$STATELESS_ENABLED" == "1" ]]; then
    MVN_ARGS+=("-P" "stateless")
fi

# Maven 4: Detect version and enable concurrent builder
# Maven 4 introduces tree-based lifecycle where modules start as soon as
# dependencies reach 'ready' phase, enabling graph-optimal parallelization.
BUILDER_FLAG=""
MVN_VERSION=$($MVN_CMD --version 2>/dev/null | head -1 | grep -oE '[0-9]+\.[0-9]+' | head -1)
if [[ "${MVN_VERSION%%.*}" -ge 4 ]]; then
    BUILDER_FLAG="-b concurrent"
    # Maven 4 resume support: continue from failed module
    if [[ "${DX_RESUME:-0}" == "1" ]]; then
        MVN_ARGS+=("-r")
    fi
fi
[[ -n "$BUILDER_FLAG" ]] && MVN_ARGS+=("$BUILDER_FLAG")

# Offline
[[ -n "$OFFLINE_FLAG" ]] && MVN_ARGS+=("$OFFLINE_FLAG")

# Quiet unless verbose
[[ "${DX_VERBOSE:-0}" != "1" ]] && MVN_ARGS+=("-q")

# Clean phase (default: skip for incremental builds)
GOALS=()
[[ "${DX_CLEAN:-0}" == "1" ]] && GOALS+=("clean")

# Build phases based on mode
# Note: If TEP is enabled, tests will be handled separately after compile
case "$PHASE" in
    compile)      GOALS+=("compile") ;;
    test)
        # TEP_FAIL_FAST_TIER will be handled post-compile
        if [[ "$TEP_FAIL_FAST_TIER" -eq 0 ]]; then
            GOALS+=("test")
        fi
        ;;
    compile-test)
        # Compile always, tests handled conditionally
        GOALS+=("compile")
        if [[ "$TEP_FAIL_FAST_TIER" -eq 0 ]]; then
            GOALS+=("test")
        fi
        ;;
esac

# If we have cached modules, we can't use Maven's standard -DskipTests
# because it skips all modules. Instead, we'll rely on test results being
# injected via custom test provider (phase 2 enhancement).
# For now, we document that cache hits are soft wins (no actual skip yet).
if [[ -n "${CACHE_SKIPPED_MODULES:-}" ]]; then
    # Future: Pass cached modules list to Maven extension
    # MVN_ARGS+=("-Dcache.skipped.modules=$CACHE_SKIPPED_MODULES")
    true
fi

# Module targeting
if [[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" ]]; then
    MVN_ARGS+=("-pl" "$EXPLICIT_MODULES" "-amd")
elif [[ "$SCOPE" == "all" && "${CLAUDE_CODE_REMOTE:-false}" == "true" ]]; then
    # In remote/CI mode ALL_MODULES may have been filtered (e.g. yawl-pi excluded
    # because onnxruntime:1.19.2 is 89MB and cannot be fetched via egress proxy).
    # Pass explicit -pl list so Maven reactor honours the filtered set.
    REMOTE_MODULES=$(IFS=','; echo "${ALL_MODULES[*]}")
    MVN_ARGS+=("-pl" "$REMOTE_MODULES")
fi

# Note: Cache skipping of tests is handled via properties passed to Maven
# (not via module exclusion, since we still need to compile them)

# Fail strategy
# Fail strategy - fast fail by default
if [[ "${DX_FAIL_AT:-fast}" == "fast" ]]; then
    MVN_ARGS+=("--fail-fast")
else
    MVN_ARGS+=("--fail-at-end")
fi

# ── Color codes for enhanced output ─────────────────────────────────────────
readonly C_RESET='\033[0m'
readonly C_GREEN='\033[92m'
readonly C_RED='\033[91m'
readonly C_BLUE='\033[94m'
readonly C_YELLOW='\033[93m'
readonly C_CYAN='\033[96m'
readonly E_OK='✓'
readonly E_FAIL='✗'

# ── Load cache configuration ────────────────────────────────────────────────
# Cache is only effective for test phase with explicit module list
# (not for full builds where dependencies may have changed)
CACHE_ENABLED="false"
if [[ "$PHASE" == "test" || "$PHASE" == "compile-test" ]]; then
    if [[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" && "${DX_CACHE:-1}" == "1" ]]; then
        CACHE_ENABLED="true"
        source ".mvn/cache-config.sh"
    fi
fi

# ── Warm Module Cache Support ───────────────────────────────────────────────
# Warm cache reduces compilation time for hot modules (yawl-engine, yawl-elements)
# by reusing compiled bytecode if source hasn't changed.
WARM_CACHE_MODULES_SKIPPED=""
WARM_CACHE_SAVED_MODULES=""

warm_cache_attempt_load() {
    local module="$1"
    if [[ "$WARM_CACHE_ENABLED" != "1" ]]; then
        return 1
    fi

    # Only attempt for hot modules
    if [[ ! "$module" =~ ^(yawl-engine|yawl-elements)$ ]]; then
        return 1
    fi

    # Load from warm cache if valid
    if bash "${SCRIPT_DIR}/manage-warm-cache.sh" load "$module" 2>/dev/null; then
        return 0
    fi
    return 1
}

warm_cache_attempt_save() {
    local module="$1"
    if [[ "$WARM_CACHE_ENABLED" != "1" ]]; then
        return 0
    fi

    # Only save for hot modules
    if [[ ! "$module" =~ ^(yawl-engine|yawl-elements)$ ]]; then
        return 0
    fi

    # Save compiled classes to warm cache
    bash "${SCRIPT_DIR}/manage-warm-cache.sh" save "$module" 2>/dev/null || true
    return 0
}

# Pre-compile warm cache warmup: try to load cached modules before Maven runs
if [[ "$WARM_CACHE_ENABLED" == "1" && "$PHASE" == *"compile"* ]]; then
    printf "${C_CYAN}dx${C_RESET}: ${C_BLUE}Warm Cache: Checking for valid cached modules...${C_RESET}\n" >&2

    HOT_MODULES=("yawl-engine" "yawl-elements")
    for hot_module in "${HOT_MODULES[@]}"; do
        if warm_cache_attempt_load "$hot_module"; then
            WARM_CACHE_MODULES_SKIPPED+="$hot_module "
            printf "  ${C_GREEN}✓${C_RESET} %s loaded from warm cache\n" "$hot_module" >&2
        fi
    done

    if [[ -n "$WARM_CACHE_MODULES_SKIPPED" ]]; then
        printf "${C_CYAN}dx${C_RESET}: Warm cache: %d module(s) loaded, skipping compilation\n" \
            $(echo "$WARM_CACHE_MODULES_SKIPPED" | wc -w) >&2

        # Exclude warm-cached modules from Maven build using -pl
        if [[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" ]]; then
            # Filter out cached modules from explicit module list
            local -a filtered_modules
            IFS=',' read -ra original_modules <<< "$EXPLICIT_MODULES"
            for mod in "${original_modules[@]}"; do
                if [[ ! " $WARM_CACHE_MODULES_SKIPPED " =~ " $mod " ]]; then
                    filtered_modules+=("$mod")
                fi
            done

            # Rebuild EXPLICIT_MODULES without cached ones
            EXPLICIT_MODULES=$(IFS=','; echo "${filtered_modules[*]}")

            if [[ -z "$EXPLICIT_MODULES" ]]; then
                printf "${C_CYAN}dx${C_RESET}: All modules have valid warm cache, skipping Maven build\n" >&2
                # Still run tests if in compile-test phase
                if [[ "$PHASE" == "compile-test" ]]; then
                    PHASE="test"
                    printf "${C_CYAN}dx${C_RESET}: Proceeding to test phase only\n" >&2
                else
                    # Pure compile phase with all modules cached
                    printf "${C_GREEN}${E_OK} WARM CACHE SUCCESS${C_RESET} | All %d module(s) compiled from cache\n" \
                        $(echo "$WARM_CACHE_MODULES_SKIPPED" | wc -w)
                    exit 0
                fi
            fi
        fi
    fi
fi

# ── Class Data Sharing (CDS) configuration ──────────────────────────────────
# CDS archives improve startup time for hot modules (yawl-engine, yawl-elements)
# CDS flags are automatically injected if archives exist.
# Auto-generation runs after successful compile phase.
CDS_AUTO_GENERATE="${DX_CDS_GENERATE:-1}"
CDS_FLAGS=""
if [[ "${DX_CDS_GENERATE:-1}" == "1" && "$PHASE" != "test" ]]; then
    # Load CDS helper and check for auto-generation
    if [[ -f "${SCRIPT_DIR}/cds-helper.sh" ]]; then
        # Validate/generate CDS before build (don't require archives to exist)
        bash "${SCRIPT_DIR}/cds-helper.sh" generate 0 2>/dev/null || true
        # Get CDS flags if archives exist
        CDS_FLAGS=$(bash "${SCRIPT_DIR}/cds-helper.sh" flags 2>/dev/null || echo "")
        if [[ -n "$CDS_FLAGS" ]]; then
            # Add each flag as a separate argument
            for flag in $CDS_FLAGS; do
                MVN_ARGS+=("$flag")
            done
            printf "${C_CYAN}dx${C_RESET}: ${C_BLUE}CDS${C_RESET} archives available\n" >&2
        fi
    fi
fi

# ── Impact Graph Support ────────────────────────────────────────────────────
# When --impact-graph is used, identify changed source files and find affected tests
IMPACT_GRAPH_FILE="${REPO_ROOT}/.yawl/cache/test-impact-graph.json"
if [[ "$USE_IMPACT_GRAPH" == "1" && "$PHASE" == *"test"* ]]; then
    # First, ensure impact graph is built and fresh
    bash "${SCRIPT_DIR}/build-test-impact-graph.sh" 2>/dev/null || true

    if [[ -f "$IMPACT_GRAPH_FILE" ]]; then
        # Find changed source files
        local changed_sources=$(git diff --name-only HEAD 2>/dev/null | grep 'src/main/java.*\.java$' || true)

        if [[ -n "$changed_sources" ]]; then
            # Parse changed sources into class names
            local affected_tests=""
            while IFS= read -r file; do
                [[ -z "$file" ]] && continue
                # Convert path to class name: yawl-engine/src/main/java/org/yawl/engine/YEngine.java -> org.yawl.engine.YEngine
                local class_name=$(echo "$file" | sed 's|^.*src/main/java/||' | sed 's|\.java$||' | sed 's|/|.|g')

                # Look up this class in source_to_tests map
                local tests=$(jq -r ".source_to_tests[\"$class_name\"]? // .source_to_tests[\"${class_name}.*\"]? // []" "$IMPACT_GRAPH_FILE" 2>/dev/null || echo "[]")

                if [[ "$tests" != "[]" ]]; then
                    # Extract test method names and collect them
                    while IFS= read -r test_name; do
                        [[ -z "$test_name" || "$test_name" == "null" ]] && continue
                        affected_tests+="$test_name "
                    done < <(echo "$tests" | jq -r '.[]?' 2>/dev/null)
                fi
            done <<< "$changed_sources"

            if [[ -n "$affected_tests" ]]; then
                printf "${C_CYAN}Impact Graph: Found %d affected tests${C_RESET}\n" "$(echo "$affected_tests" | wc -w)"
                # TODO: Pass affected tests to Maven via system property
                # For now, we continue with normal test execution
            fi
        fi
    fi
fi

# ── Cache warmup check ──────────────────────────────────────────────────────
# Check which modules have valid cached results (only skip their tests)
CACHE_SKIPPED_MODULES=""
CACHE_HIT_COUNT=0
CACHE_MISS_COUNT=0

if [[ "$CACHE_ENABLED" == "true" ]]; then
    printf "${C_CYAN}Cache: Checking for valid cached results...${C_RESET}\n"

    # Split explicit modules
    IFS=',' read -ra MODULES_TO_CHECK <<< "$EXPLICIT_MODULES"
    for module in "${MODULES_TO_CHECK[@]}"; do
        if cache_is_valid "$module" 2>/dev/null; then
            # Cache hit — get result and log it
            cached_result=$(cache_get_result "$module" 2>/dev/null) || continue
            test_count=$(echo "$cached_result" | jq -r '.test_results.passed // 0')
            test_time=$(echo "$cached_result" | jq -r '.test_results.duration_ms // 0')

            if [[ -n "$CACHE_SKIPPED_MODULES" ]]; then
                CACHE_SKIPPED_MODULES+=","
            fi
            CACHE_SKIPPED_MODULES+="$module"
            ((CACHE_HIT_COUNT++))

            printf "  ${C_GREEN}✓${C_RESET} %s — %d tests (cached, %.1fs)\n" \
                "$module" "$test_count" "$(echo "scale=1; $test_time / 1000" | bc 2>/dev/null || echo '0.0')"
        else
            ((CACHE_MISS_COUNT++))
            printf "  ${C_YELLOW}◇${C_RESET} %s — will run tests\n" "$module"
        fi
    done

    if [[ -n "$CACHE_SKIPPED_MODULES" ]]; then
        printf "\n${C_CYAN}Cache: Skipping tests for %d module(s) (hit rate: %d/%d)${C_RESET}\n" \
            "$CACHE_HIT_COUNT" "$CACHE_HIT_COUNT" $((CACHE_HIT_COUNT + CACHE_MISS_COUNT))
    fi
fi

# ── Execute ──────────────────────────────────────────────────────────────
LABEL="$PHASE"
SCOPE_LABEL="all modules"
if [[ "$SCOPE" == "explicit" ]]; then
    LABEL+=" [${EXPLICIT_MODULES}]"
    SCOPE_LABEL="${EXPLICIT_MODULES}"
fi

# Use seconds for cross-platform compatibility
START_SEC=$(date +%s)

# Pretty header
echo ""
printf "${C_CYAN}dx${C_RESET}: ${C_BLUE}%s${C_RESET}\n" "${LABEL}"
printf "${C_CYAN}dx${C_RESET}: scope=%s | phase=%s | fail-strategy=%s\n" \
    "$SCOPE_LABEL" "$PHASE" "${DX_FAIL_AT:-fast}"

set +e
$MVN_CMD "${GOALS[@]}" "${MVN_ARGS[@]}" 2>&1 | tee /tmp/dx-build-log.txt
EXIT_CODE=$?
set -euo pipefail

END_SEC=$(date +%s)
ELAPSED_SEC=$((END_SEC - START_SEC))

# ── Tiered Test Execution (TEP) if fail-fast-tier is enabled ────────────────
# Run tests by tier with fail-fast semantics: stop at first tier failure
if [[ "$TEP_FAIL_FAST_TIER" -gt 0 && ($PHASE == "test" || $PHASE == "compile-test") && $EXIT_CODE -eq 0 ]]; then
    printf "\n${C_CYAN}TEP${C_RESET}: ${C_BLUE}Tiered Test Execution (fail-fast-tier=%d)${C_RESET}\n" "$TEP_FAIL_FAST_TIER"

    # Track overall test results across tiers
    declare -A TIER_RESULTS
    TOTAL_TESTS_RUN=0
    TOTAL_TESTS_FAILED=0
    TOTAL_ELAPSED_TEP=0

    # Execute tiers sequentially
    for tier_num in {1..4}; do
        # Stop if we've reached the fail-fast tier limit
        if [[ $tier_num -gt $TEP_FAIL_FAST_TIER ]]; then
            printf "\n${C_CYAN}TEP${C_RESET}: Stopping at tier %d (fail-fast-tier=%d)\n" $tier_num "$TEP_FAIL_FAST_TIER"
            break
        fi

        # Run tier
        TIER_START=$(date +%s)
        set +e
        bash "${SCRIPT_DIR}/run-test-tier.sh" "$tier_num" 2>&1 | tee /tmp/tep-tier-${tier_num}-output.txt
        TIER_EXIT=$?
        set -euo pipefail
        TIER_END=$(date +%s)
        TIER_ELAPSED=$((TIER_END - TIER_START))
        TOTAL_ELAPSED_TEP=$((TOTAL_ELAPSED_TEP + TIER_ELAPSED))

        # Parse tier results from output
        PASSED=0
        FAILED=0
        if [[ -f /tmp/tep-tier-${tier_num}-output.txt ]]; then
            PASSED=$(grep "^  ${C_GREEN}Passed${C_RESET}:" /tmp/tep-tier-${tier_num}-output.txt 2>/dev/null | grep -oE '[0-9]+$' || echo "0")
            FAILED=$(grep "^  ${C_RED}Failed${C_RESET}:" /tmp/tep-tier-${tier_num}-output.txt 2>/dev/null | grep -oE '[0-9]+$' || echo "0")
        fi
        [[ -z "$PASSED" ]] && PASSED=0
        [[ -z "$FAILED" ]] && FAILED=0

        TOTAL_TESTS_RUN=$((TOTAL_TESTS_RUN + PASSED + FAILED))
        TOTAL_TESTS_FAILED=$((TOTAL_TESTS_FAILED + FAILED))

        TIER_RESULTS["tier_${tier_num}_passed"]="$PASSED"
        TIER_RESULTS["tier_${tier_num}_failed"]="$FAILED"
        TIER_RESULTS["tier_${tier_num}_elapsed"]="$TIER_ELAPSED"
        TIER_RESULTS["tier_${tier_num}_exit"]="$TIER_EXIT"

        # Check for failure
        if [[ $TIER_EXIT -ne 0 ]]; then
            printf "\n${C_RED}${E_FAIL} Tier %d FAILED${C_RESET} — stopping at tier %d\n" "$tier_num" "$tier_num"
            EXIT_CODE=$TIER_EXIT

            if [[ "${TEP_CONTINUE_ON_FAILURE:-0}" != "1" ]]; then
                break  # Fail-fast: stop at first tier failure
            else
                printf "${C_YELLOW}→${C_RESET} Continuing despite tier failure (TEP_CONTINUE_ON_FAILURE=1)\n"
            fi
        fi
    done

    # Update elapsed time
    ELAPSED_SEC=$((ELAPSED_SEC + TOTAL_ELAPSED_TEP))
    TEST_COUNT=$TOTAL_TESTS_RUN
    TEST_FAILED=$TOTAL_TESTS_FAILED
fi

# ── Collect timing metrics (optional) ──────────────────────────────────────
TIMINGS_DIR="${REPO_ROOT}/.yawl/timings"
TIMINGS_FILE="${TIMINGS_DIR}/build-timings.json"
if [[ "${DX_TIMINGS:-0}" == "1" ]]; then
    mkdir -p "${TIMINGS_DIR}"

    # Extract module compile and test times from Maven log
    # Parse format: "[INFO] Building yawl-engine"
    declare -A module_times

    # Extract test execution times
    # Format: "Tests run: N, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.234 sec"
    while IFS= read -r line; do
        if [[ $line =~ Tests\ run:\ ([0-9]+),.*Time\ elapsed:\ ([0-9.]+) ]]; then
            test_count="${BASH_REMATCH[1]}"
            test_time="${BASH_REMATCH[2]}"
        fi
    done < /tmp/dx-build-log.txt

    # Create timestamped entry with execution metrics
    TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    ENTRY="{\"timestamp\":\"${TIMESTAMP}\",\"elapsed_sec\":${ELAPSED_SEC},\"test_count\":${TEST_COUNT},\"test_failed\":${TEST_FAILED},\"modules_count\":${MODULES_COUNT},\"success\":$([[ $EXIT_CODE -eq 0 ]] && echo true || echo false)}"

    # Append to timings file (append-only for trend analysis)
    echo "${ENTRY}" >> "${TIMINGS_FILE}"
fi

# Parse results from Maven log
# NOTE: grep -c exits 1 when 0 matches (still outputs "0"), so || must be outside
# the $() to avoid capturing both grep's "0" output AND the fallback "0" as "0\n0".
TEST_COUNT=$(grep -c "Running " /tmp/dx-build-log.txt 2>/dev/null) || TEST_COUNT=0
TEST_FAILED=$(grep -c "FAILURE" /tmp/dx-build-log.txt 2>/dev/null) || TEST_FAILED=0
if [[ "$SCOPE" == "all" ]]; then
    MODULES_COUNT=${#ALL_MODULES[@]}
else
    MODULES_COUNT=$(echo "$SCOPE_LABEL" | tr ',' '\n' | wc -l | tr -d ' ')
fi

# ── Post-compile warm cache storage ────────────────────────────────────────
# After successful compile, save newly compiled modules to warm cache
if [[ $EXIT_CODE -eq 0 && "$PHASE" == *"compile"* && "$WARM_CACHE_ENABLED" == "1" ]]; then
    if [[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" ]]; then
        printf "\n${C_CYAN}dx${C_RESET}: ${C_BLUE}Warm Cache: Saving compiled modules...${C_RESET}\n" >&2

        IFS=',' read -ra MODULES_TO_SAVE <<< "$EXPLICIT_MODULES"
        for module in "${MODULES_TO_SAVE[@]}"; do
            [[ -z "$module" ]] && continue
            if warm_cache_attempt_save "$module"; then
                WARM_CACHE_SAVED_MODULES+="$module "
                printf "  ${C_GREEN}✓${C_RESET} %s saved to warm cache\n" "$module" >&2
            fi
        done
    fi
fi

# ── Post-compile CDS regeneration ───────────────────────────────────────────
# After successful compile, regenerate CDS archives for hot modules
# if they are part of the compiled modules.
if [[ $EXIT_CODE -eq 0 && "$PHASE" == *"compile"* ]]; then
    if [[ -f "${SCRIPT_DIR}/cds-helper.sh" ]]; then
        # Check if any hot module was compiled
        HOT_MODULES=("yawl-engine" "yawl-elements")
        SHOULD_REGENERATE=0

        if [[ "$SCOPE" == "all" ]]; then
            SHOULD_REGENERATE=1
        elif [[ "$SCOPE" == "explicit" ]]; then
            for hot_module in "${HOT_MODULES[@]}"; do
                if [[ "$EXPLICIT_MODULES" == *"$hot_module"* ]]; then
                    SHOULD_REGENERATE=1
                    break
                fi
            done
        fi

        if [[ $SHOULD_REGENERATE -eq 1 ]]; then
            printf "\n${C_CYAN}dx${C_RESET}: ${C_BLUE}Regenerating CDS archives...${C_RESET}\n" >&2
            bash "${SCRIPT_DIR}/cds-helper.sh" generate 1 2>/dev/null || true
            printf "${C_CYAN}dx${C_RESET}: ${C_BLUE}CDS generation complete${C_RESET}\n" >&2
        fi
    fi
fi

# ── Semantic hash caching for change detection ─────────────────────────────
# Update semantic hashes after successful compile to enable efficient change detection
if [[ $EXIT_CODE -eq 0 && "$PHASE" == *"compile"* ]]; then
    if [[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" ]]; then
        printf "\n${C_CYAN}Semantic: Updating semantic hashes for efficient change detection...${C_RESET}\n"
        IFS=',' read -ra MODULES_TO_HASH <<< "$EXPLICIT_MODULES"
        for module in "${MODULES_TO_HASH[@]}"; do
            [[ -z "$module" ]] && continue
            # Compute and cache semantic hash
            bash "${SCRIPT_DIR}/compute-semantic-hash.sh" "$module" --cache >/dev/null 2>&1 || true
            [[ "${DX_VERBOSE:-0}" == "1" ]] && printf "  ${C_GREEN}✓${C_RESET} Cached semantic hash: %s\n" "$module" >&2
        done
    fi
fi

# ── Cache storage for successful test runs ────────────────────────────────
if [[ "$CACHE_ENABLED" == "true" && $EXIT_CODE -eq 0 && "$PHASE" == *"test"* ]]; then
    printf "\n${C_CYAN}Cache: Storing test results for warm builds...${C_RESET}\n"

    # Parse per-module test results from Surefire reports
    IFS=',' read -ra MODULES_TO_CACHE <<< "$EXPLICIT_MODULES"
    for module in "${MODULES_TO_CACHE[@]}"; do
        # Skip if it was already in cache (cache_skipped)
        if [[ -n "$CACHE_SKIPPED_MODULES" && "$CACHE_SKIPPED_MODULES" == *"$module"* ]]; then
            continue
        fi

        # Find this module's test results in Surefire reports
        local surefire_report="${module}/target/surefire-reports"
        if [[ ! -d "$surefire_report" ]]; then
            continue
        fi

        # Parse test summary (format: Tests run: N, Failures: F, Errors: E, Skipped: S, Time elapsed: T sec)
        local test_summary
        test_summary=$(find "$surefire_report" -name "TEST-*.xml" -exec grep -h "<testsuite" {} \; | head -1)

        if [[ -z "$test_summary" ]]; then
            continue
        fi

        # Extract counts from XML attributes: tests="N" failures="F" errors="E" skipped="S" time="T"
        local test_passed=0
        local test_failed=0
        local test_skipped=0
        local test_duration_ms=0

        # Parse from XML attributes
        if [[ $test_summary =~ tests=\"([0-9]+)\" ]]; then
            test_passed=$((${BASH_REMATCH[1]:-0} - ${test_failed:-0} - ${test_skipped:-0}))
        fi
        if [[ $test_summary =~ failures=\"([0-9]+)\" ]]; then
            test_failed=${BASH_REMATCH[1]:-0}
        fi
        if [[ $test_summary =~ skipped=\"([0-9]+)\" ]]; then
            test_skipped=${BASH_REMATCH[1]:-0}
        fi
        if [[ $test_summary =~ time=\"([0-9.]+)\" ]]; then
            test_duration_ms=$(echo "${BASH_REMATCH[1]:-0} * 1000" | bc 2>/dev/null || echo "0")
        fi

        # Build cache entry
        local test_results_json
        test_results_json=$(jq -n \
            --argjson passed "$test_passed" \
            --argjson failed "$test_failed" \
            --argjson skipped "$test_skipped" \
            --argjson duration_ms "$test_duration_ms" \
            '{passed: $passed, failed: $failed, skipped: $skipped, duration_ms: $duration_ms}')

        # Store in cache
        if cache_store_result "$module" "$test_results_json" 2>/dev/null; then
            printf "  ${C_GREEN}✓${C_RESET} %s — cached (%d tests, %.1fs)\n" \
                "$module" "$test_passed" "$(echo "scale=1; $test_duration_ms / 1000" | bc 2>/dev/null || echo '0.0')"
        fi
    done

    printf "${C_CYAN}Cache: All results stored${C_RESET}\n"
fi

# Extract slowest tests from Surefire reports (if available)
extract_slowest_tests() {
    local max_tests=3
    local count=0
    local surefire_dir="target/surefire-reports"

    if [[ ! -d "$surefire_dir" ]]; then
        return
    fi

    # Parse .txt reports for test durations
    # Format: "testMethodName(ClassName) Time elapsed: 0.123 sec"
    local -a slow_tests=()
    while IFS= read -r line; do
        if [[ $line =~ Time\ elapsed:\ ([0-9.]+)\ sec ]]; then
            duration="${BASH_REMATCH[1]}"
            test_name=$(echo "$line" | awk '{print $1}')
            slow_tests+=("${test_name} (${duration}s)")
        fi
    done < <(find "$surefire_dir" -name "*.txt" -exec grep "Time elapsed" {} + | sort -t: -k3 -nr | head -5)

    if [[ ${#slow_tests[@]} -gt 0 ]]; then
        printf "\n${C_CYAN}Slowest tests:${C_RESET}\n"
        for test in "${slow_tests[@]}"; do
            printf "  ${C_YELLOW}•${C_RESET} %s\n" "$test"
        done
    fi
}

# Enhanced status with metrics
echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
    # Build success message with warm cache info if applicable
    local success_msg="${C_GREEN}${E_OK} SUCCESS${C_RESET} | time: ${ELAPSED_SEC}s | modules: %d | tests: %d"
    if [[ -n "$WARM_CACHE_MODULES_SKIPPED" ]]; then
        success_msg+=" | warm cache: %d module(s)"
    fi
    if [[ -n "$WARM_CACHE_SAVED_MODULES" ]]; then
        success_msg+=" | saved: %d"
    fi

    if [[ -n "$WARM_CACHE_MODULES_SKIPPED" && -n "$WARM_CACHE_SAVED_MODULES" ]]; then
        printf "${success_msg}\n" "$MODULES_COUNT" "$TEST_COUNT" \
            $(echo "$WARM_CACHE_MODULES_SKIPPED" | wc -w) $(echo "$WARM_CACHE_SAVED_MODULES" | wc -w)
    elif [[ -n "$WARM_CACHE_MODULES_SKIPPED" ]]; then
        printf "${success_msg}\n" "$MODULES_COUNT" "$TEST_COUNT" $(echo "$WARM_CACHE_MODULES_SKIPPED" | wc -w)
    elif [[ -n "$WARM_CACHE_SAVED_MODULES" ]]; then
        printf "${success_msg}\n" "$MODULES_COUNT" "$TEST_COUNT" $(echo "$WARM_CACHE_SAVED_MODULES" | wc -w)
    else
        printf "${success_msg}\n" "$MODULES_COUNT" "$TEST_COUNT"
    fi

    # Show slowest tests if available
    extract_slowest_tests

    # Show timing metrics hint
    if [[ "${DX_TIMINGS:-0}" == "1" ]]; then
        printf "\n${C_CYAN}Timing metrics saved to:${C_RESET} .yawl/timings/build-timings.json\n"
    fi

    # Show warm cache statistics if enabled
    if [[ "$WARM_CACHE_ENABLED" == "1" ]]; then
        printf "\n${C_CYAN}Warm Cache Statistics:${C_RESET}\n"
        bash "${SCRIPT_DIR}/manage-warm-cache.sh" stats 2>/dev/null | tail -20 || true
    fi
else
    printf "${C_RED}${E_FAIL} FAILED${C_RESET} | time: ${ELAPSED_SEC}s (exit ${EXIT_CODE}) | failures: %d\n" \
        "$TEST_FAILED"
    printf "\n${C_YELLOW}→${C_RESET} Debug: ${C_CYAN}cat /tmp/dx-build-log.txt | tail -50${C_RESET}\n"
    printf "${C_YELLOW}→${C_RESET} Run again: ${C_CYAN}DX_VERBOSE=1 bash scripts/dx.sh${C_RESET}\n"
    printf "${C_YELLOW}→${C_RESET} With timing: ${C_CYAN}DX_TIMINGS=1 bash scripts/dx.sh${C_RESET}\n\n"
    exit $EXIT_CODE
fi
echo ""
