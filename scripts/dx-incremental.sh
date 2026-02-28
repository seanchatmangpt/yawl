#!/usr/bin/env bash
# ==========================================================================
# dx-incremental.sh — Enhanced incremental build with change detection
#
# Detects truly changed modules (not just uncommitted) and rebuilds only
# those modules plus their transitive dependents. Complements Maven's
# incremental compilation for multi-module builds.
#
# Usage:
#   bash scripts/dx-incremental.sh               # Changed modules only
#   bash scripts/dx-incremental.sh --verbose     # With output
#   bash scripts/dx-incremental.sh --debug       # Full tracing
#   bash scripts/dx-incremental.sh --all         # All modules (fallback)
#
# Environment:
#   DX_SHOW_GRAPH=1     Show dependency graph analysis
#   DX_AFFECTED_ONLY=1  Only rebuild affected modules (fast)
#
# Improvements over dx.sh:
#   - Detects actual file changes (git diff)
#   - Only builds changed + direct dependents (not all modules)
#   - Parallel where possible
#   - Reports rebuild scope and savings
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Java 25 enforcement
_TEMURIN25="/usr/lib/jvm/temurin-25-jdk-amd64"
if [ -d "${_TEMURIN25}" ]; then
    export JAVA_HOME="${_TEMURIN25}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi

# ── Parse arguments ───────────────────────────────────────────────────
VERBOSE="${DX_VERBOSE:-0}"
DEBUG="${DX_DEBUG:-0}"
SHOW_GRAPH="${DX_SHOW_GRAPH:-0}"
AFFECTED_ONLY="${DX_AFFECTED_ONLY:-1}"

for arg in "$@"; do
    case "$arg" in
        --verbose) VERBOSE=1 ;;
        --debug)   DEBUG=1; VERBOSE=1 ;;
        --graph)   SHOW_GRAPH=1 ;;
        --all)     AFFECTED_ONLY=0 ;;
        *)         echo "Unknown arg: $arg"; exit 1 ;;
    esac
done

# ── Module dependency map (manual, from pom.xml + reactor.json)
# Format: "module:dep1,dep2" (dependencies, not dependents)
declare -A MODULE_DEPS=(
    # Layer 0 (no YAWL deps)
    [yawl-utilities]=""
    [yawl-security]=""
    [yawl-graalpy]=""
    [yawl-graaljs]=""
    [yawl-benchmark]=""

    # Layer 1
    [yawl-elements]="yawl-utilities"
    [yawl-ggen]="yawl-utilities"
    [yawl-graalwasm]="yawl-utilities"
    [yawl-dmn]="yawl-utilities"
    [yawl-data-modelling]="yawl-utilities"

    # Layer 2
    [yawl-engine]="yawl-elements,yawl-utilities"

    # Layer 3
    [yawl-stateless]="yawl-engine"

    # Layer 4
    [yawl-authentication]="yawl-engine"
    [yawl-scheduling]="yawl-engine"
    [yawl-monitoring]="yawl-engine"
    [yawl-worklet]="yawl-engine"
    [yawl-control-panel]="yawl-engine"
    [yawl-integration]="yawl-engine"
    [yawl-webapps]="yawl-engine"

    # Layer 5
    [yawl-pi]="yawl-engine"
    [yawl-resourcing]="yawl-engine"

    # Layer 6
    [yawl-mcp-a2a-app]="yawl-pi,yawl-integration"
)

# Reverse map: which modules depend on each module
declare -A MODULE_DEPENDENTS
for module in "${!MODULE_DEPS[@]}"; do
    deps="${MODULE_DEPS[$module]}"
    for dep in ${deps//,/ }; do
        MODULE_DEPENDENTS[$dep]="${MODULE_DEPENDENTS[$dep]:-} $module"
    done
done

# ── Detect changed modules (git-based) ────────────────────────────────
detect_changed_modules() {
    local changed_modules=()

    if ! git rev-parse --git-dir > /dev/null 2>&1; then
        [[ $VERBOSE -eq 1 ]] && echo "⚠ Not a git repo, detecting via git status..."
        return 0
    fi

    # Get list of changed files
    local changed_files=$(git diff --name-only HEAD 2>/dev/null || echo "")
    local untracked_files=$(git ls-files --others --exclude-standard 2>/dev/null || echo "")

    # If no changes, return empty
    if [ -z "${changed_files}" ] && [ -z "${untracked_files}" ]; then
        [[ $VERBOSE -eq 1 ]] && echo "No changes detected"
        return 0
    fi

    # Map file paths to modules
    for file in ${changed_files} ${untracked_files}; do
        # Extract module name from path (e.g., yawl-engine/src/... → yawl-engine)
        module=$(echo "$file" | cut -d'/' -f1)

        if [ -d "${REPO_ROOT}/${module}" ] && [ -f "${REPO_ROOT}/${module}/pom.xml" ]; then
            if [[ ! " ${changed_modules[@]} " =~ " ${module} " ]]; then
                changed_modules+=("$module")
            fi
        fi
    done

    printf '%s\n' "${changed_modules[@]}"
}

# ── Compute transitive dependents (modules that depend on changed ones)
compute_affected_modules() {
    local changed_modules=("$@")
    local affected_modules=()

    # Start with changed modules
    for module in "${changed_modules[@]}"; do
        if [[ ! " ${affected_modules[@]} " =~ " ${module} " ]]; then
            affected_modules+=("$module")
        fi
    done

    # Recursively add dependents
    local to_process=("${changed_modules[@]}")
    while [ ${#to_process[@]} -gt 0 ]; do
        local current="${to_process[0]}"
        to_process=("${to_process[@]:1}")

        # Get all modules that depend on current
        local dependents="${MODULE_DEPENDENTS[$current]:-}"
        for dependent in ${dependents}; do
            if [[ ! " ${affected_modules[@]} " =~ " ${dependent} " ]]; then
                affected_modules+=("$dependent")
                to_process+=("$dependent")
            fi
        done
    done

    printf '%s\n' "${affected_modules[@]}"
}

# ── Main logic ────────────────────────────────────────────────────────
[[ $DEBUG -eq 1 ]] && echo "Starting incremental build detection..."

CHANGED=$(detect_changed_modules)

if [ -z "$CHANGED" ]; then
    CHANGED_ARRAY=()
else
    IFS=$'\n' read -rd '' -a CHANGED_ARRAY <<<"$CHANGED" || true
fi

if [ ${#CHANGED_ARRAY[@]} -eq 0 ]; then
    echo "No changes detected. Use --all to build everything."
    exit 0
fi

echo "=========================================="
echo "Incremental Build Analysis"
echo "=========================================="
echo ""
echo "Changed modules: ${#CHANGED_ARRAY[@]}"
for module in "${CHANGED_ARRAY[@]}"; do
    echo "  - $module"
done
echo ""

if [ $AFFECTED_ONLY -eq 1 ]; then
    AFFECTED=$(compute_affected_modules "${CHANGED_ARRAY[@]}")

    if [ -z "$AFFECTED" ]; then
        AFFECTED_ARRAY=()
    else
        IFS=$'\n' read -rd '' -a AFFECTED_ARRAY <<<"$AFFECTED" || true
    fi

    echo "Affected modules (including dependents): ${#AFFECTED_ARRAY[@]}"
    for module in "${AFFECTED_ARRAY[@]}"; do
        echo "  - $module"
    done
    echo ""

    # Calculate savings
    TOTAL_MODULES=$(echo "${!MODULE_DEPS[@]}" | wc -w)
    SKIPPED=$((TOTAL_MODULES - ${#AFFECTED_ARRAY[@]}))
    SAVINGS=$((SKIPPED * 100 / TOTAL_MODULES))

    echo "Build Efficiency:"
    echo "  Total modules: ${TOTAL_MODULES}"
    echo "  Modules to rebuild: ${#AFFECTED_ARRAY[@]}"
    echo "  Modules skipped: ${SKIPPED} (${SAVINGS}% faster)"
    echo ""

    if [ $SHOW_GRAPH -eq 1 ]; then
        echo "Dependency Graph (affected only):"
        for module in "${AFFECTED_ARRAY[@]}"; do
            deps="${MODULE_DEPS[$module]:-}"
            if [ -z "$deps" ]; then
                echo "  $module (no deps)"
            else
                echo "  $module ← $deps"
            fi
        done
        echo ""
    fi

    # Build module list for mvn -pl
    MODULE_LIST=$(IFS=','; echo "${AFFECTED_ARRAY[*]}")
else
    MODULE_LIST=""
    echo "Building all modules (--all flag)"
    echo ""
fi

# ── Execute Maven build ────────────────────────────────────────────────
if [ $VERBOSE -eq 1 ]; then
    MAVEN_QUIET=""
else
    MAVEN_QUIET="-q"
fi

if [ -z "$MODULE_LIST" ]; then
    echo "Command: mvn ${MAVEN_QUIET} compile -DskipTests"
    mvn ${MAVEN_QUIET} compile -DskipTests
else
    echo "Command: mvn ${MAVEN_QUIET} compile -DskipTests -pl ${MODULE_LIST}"
    mvn ${MAVEN_QUIET} compile -DskipTests -pl "${MODULE_LIST}"
fi

echo ""
echo "=========================================="
echo "Build complete."
echo "=========================================="
