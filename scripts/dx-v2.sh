#!/usr/bin/env bash
# YAWL dx.sh 2.0 - Developer Experience Workflow
# Optimized with O(1) change detection and parallel builds

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

# ── Java 25 enforcement ──────────────────────────────────────────────────────
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

# ── Module definitions with layers (order matters for dependencies) ────────
declare -A MODULE_LAYER=(
    [yawl-elements]=1
    [yawl-utilities]=1
    [yawl-security]=1
    [yawl-graalpy]=1
    [yawl-graaljs]=1
    [yawl-ggen]=2
    [yawl-graalwasm]=2
    [yawl-dmn]=2
    [yawl-data-modelling]=2
    [yawl-binder]=2
    [yawlbinder]=2
    [yawl-logging]=2
    [yawl-engine]=3
    [yawl-stateless]=3
    [yawl-authentication]=3
    [yawl-scheduling]=3
    [yawl-monitoring]=3
    [yawl-metrics]=3
    [yawl-worklet]=4
    [yawl-control-panel]=4
    [yawl-integration]=4
    [yawl-webapps]=4
    [yawl-pi]=4
    [yawl-resourcing]=4
    [yawl-mcp-a2a-app]=5
)

# In remote/CI environments, skip modules with heavy ML dependencies (>50MB JARs)
# that cannot be downloaded through the egress proxy (onnxruntime = 89MB).
if [[ "${CLAUDE_CODE_REMOTE:-false}" == "true" ]]; then
    for mod in yawl-pi yawl-mcp-a2a-app; do
        unset MODULE_LAYER["$mod"]
    done
fi

# ── O(1) change detection using single git call ─────────────────────────────
get_changed_modules() {
    local base_ref="${1:-HEAD~1}"
    local -A changed=()

    # Single git call for all changes (unstaged + staged + untracked)
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue

        # Handle special cases
        if [[ "$file" == "pom.xml" || "$file" == ".mvn/"* ]]; then
            echo "all"
            return
        fi

        # Extract module from file path using associative array lookup
        for module in "${!MODULE_LAYER[@]}"; do
            if [[ "$file" == "${module}/"* ]]; then
                changed["$module"]=1
                break
            fi
        done
    done < <(git diff --name-only HEAD 2>/dev/null || git diff --name-only --cached 2>/dev/null || git ls-files --others --exclude-standard 2>/dev/null || true)

    # Return unique modules sorted by layer
    local sorted_modules
    sorted_modules=$(
        for m in "${!changed[@]}"; do
            echo "${MODULE_LAYER[$m]}:$m"
        done | sort -t: -k1 -n | cut -d: -f2
    )

    echo "$sorted_modules"
}

# ── Build changed modules in layer order with parallel support ────────────────
build_changed() {
    local changed_modules
    changed_modules=$(get_changed_modules "$1")

    if [[ -z "$changed_modules" ]]; then
        echo "✓ No changes detected"
        return 0
    fi

    echo "Building changed modules: ${changed_modules//$'\n'/ }"

    # Build in layer order (parallel within layers)
    local current_layer=0
    local layer_modules=()
    local maven_args=()

    # Common Maven arguments
    maven_args+=("-P" "agent-dx")
    if [[ "${CLAUDE_CODE_REMOTE:-false}" == "true" ]]; then
        maven_args+=("-o")  # Offline mode in CI
    fi

    if [[ "${DX_VERBOSE:-0}" != "1" ]]; then
        maven_args+=("-q")
    fi

    [[ "${DX_FAIL_AT:-fast}" == "fast" ]] && maven_args+=("--fail-fast")
    [[ "${DX_CLEAN:-0}" == "1" ]] && maven_args+=("clean")

    while IFS= read -r module; do
        local layer="${MODULE_LAYER[$module]}"

        if [[ $layer -ne $current_layer && ${#layer_modules[@]} -gt 0 ]]; then
            # Build previous layer's modules in parallel
            printf "Building layer $current_layer: %s\n" "${layer_modules[*]}"
            "${MVN_CMD:-mvn}" "${maven_args[@]}" install -pl "${layer_modules[*]}" -am -T 2C
            layer_modules=()
        fi

        layer_modules+=("$module")
        current_layer=$layer
    done <<< "$changed_modules"

    # Build remaining modules in the final layer
    if [[ ${#layer_modules[@]} -gt 0 ]]; then
        printf "Building layer $current_layer: %s\n" "${layer_modules[*]}"
        "${MVN_CMD:-mvn}" "${maven_args[@]}" install -pl "${layer_modules[*]}" -am -T 2C
    fi
}

# ── Execute with retry mechanism ───────────────────────────────────────────
execute_with_retry() {
    local max_retries=3
    local attempt=1

    while [[ $attempt -le $max_retries ]]; do
        set +e
        local output_file="/tmp/dx-v2-build-attempt-$attempt.txt"

        if [[ "${DX_VERBOSE:-0}" == "1" ]]; then
            echo "Attempt $attempt/$max_retries with verbose output:"
        else
            echo "Attempt $attempt/$max_retries..."
        fi

        "$@" > "$output_file" 2>&1
        local exit_code=$?
        set -euo pipefail

        if [[ $exit_code -eq 0 ]]; then
            if [[ "${DX_VERBOSE:-0}" != "1" ]]; then
                cat "$output_file"
            fi
            return 0
        fi

        echo "⚠️  Attempt $attempt failed"

        if [[ $attempt -lt $max_retries ]]; then
            echo "⏳ Waiting 2 seconds before retry..."
            sleep 2
        fi

        attempt=$((attempt + 1))
    done

    echo "❌ All $max_retries attempts failed"
    echo "📋 Build log (last attempt):"
    cat "$output_file"
    return $exit_code
}

# ── Main command dispatcher ──────────────────────────────────────────────────
case "${1:-help}" in
    compile)
        shift
        build_changed "${1:-HEAD~1}"
        ;;
    all)
        "${MVN_CMD:-mvn}" clean verify -T 2C -Pci
        ;;
    fast)
        "${MVN_CMD:-mvnd:-mvnd}" clean install -DskipTests -T 2C -Pfast
        ;;
    test)
        local changed_modules
        changed_modules=$(get_changed_modules "$2")
        if [[ -z "$changed_modules" ]]; then
            echo "✓ No changes detected to test"
            return 0
        fi
        "${MVN_CMD:-mvn}" test -pl "${changed_modules//$'\n'/,}" -am -T 2C
        ;;
    -h|--help)
        cat << 'EOF'
YAWL dx.sh 2.0 - Developer Experience Workflow

Usage: dx-v2.sh <command> [args]

Commands:
  compile [ref]  Build only changed modules (default: HEAD~1)
  all            Full build with tests (CI mode)
  fast           Quick build without tests
  test [ref]     Test only changed modules

Examples:
  dx-v2.sh compile            # Build changes from last commit
  dx-v2.sh compile HEAD~5     # Build changes from 5 commits ago
  dx-v2.sh all                # Full CI build
  dx-v2.sh fast               # Quick local build
  dx-v2.sh test               # Test changed modules

Environment:
  DX_VERBOSE=1     Show Maven output (default: quiet)
  DX_FAIL_AT=end   Don't stop on first failure (default: fast)
  DX_CLEAN=1       Run clean phase (default: incremental)
EOF
        ;;
    *)
        cat << 'EOF'
YAWL dx.sh 2.0 - Developer Experience Workflow

Usage: dx-v2.sh <command> [args]

Commands:
  compile [ref]  Build only changed modules (default: HEAD~1)
  all            Full build with tests (CI mode)
  fast           Quick build without tests
  test [ref]     Test only changed modules

Examples:
  dx-v2.sh compile            # Build changes from last commit
  dx-v2.sh compile HEAD~5     # Build changes from 5 commits ago
  dx-v2.sh all                # Full CI build
  dx-v2.sh fast               # Quick local build
  dx-v2.sh test               # Test changed modules

Environment:
  DX_VERBOSE=1     Show Maven output (default: quiet)
  DX_FAIL_AT=end   Don't stop on first failure (default: fast)
  DX_CLEAN=1       Run clean phase (default: incremental)
EOF
        exit 1
        ;;
esac