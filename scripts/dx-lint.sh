#!/usr/bin/env bash
# ==========================================================================
# dx-lint.sh — Fast Lint Check on Changed Modules
#
# Runs compile + spotbugs on changed modules only. Catches bugs before
# the full analysis profile. Much faster than `mvn verify -P analysis`.
#
# Usage:
#   bash scripts/dx-lint.sh              # Lint changed modules
#   bash scripts/dx-lint.sh all          # Lint all modules
#   bash scripts/dx-lint.sh -pl mod      # Lint specific module
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

SCOPE="changed"
EXPLICIT_MODULES=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        all)  SCOPE="all"; shift ;;
        -pl)  EXPLICIT_MODULES="$2"; SCOPE="explicit"; shift 2 ;;
        -h|--help) echo "Usage: dx-lint.sh [all] [-pl module]"; exit 0 ;;
        *)    shift ;;
    esac
done

# NOTE: yawl-worklet removed - not in pom.xml
ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)

# Detect changed modules (same logic as dx.sh)
if [[ "$SCOPE" == "changed" ]]; then
    changed_files=$(git diff --name-only HEAD 2>/dev/null; git diff --name-only --cached 2>/dev/null)
    declare -A module_set
    while IFS= read -r file; do
        [[ -z "$file" ]] && continue
        for mod in "${ALL_MODULES[@]}"; do
            if [[ "$file" == "${mod}/"* ]]; then
                module_set["$mod"]=1
                break
            fi
        done
        if [[ "$file" == "pom.xml" || "$file" == ".mvn/"* ]]; then
            SCOPE="all"
            break
        fi
    done <<< "$changed_files"

    if [[ "$SCOPE" == "changed" ]]; then
        result=""
        for mod in "${!module_set[@]}"; do
            [[ -n "$result" ]] && result+=","
            result+="$mod"
        done
        if [[ -z "$result" ]]; then
            SCOPE="all"
        else
            EXPLICIT_MODULES="$result"
            SCOPE="explicit"
        fi
    fi
fi

MVN_ARGS=("-q")
[[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" ]] && MVN_ARGS+=("-pl" "$EXPLICIT_MODULES" "-amd")

OFFLINE_FLAG=""
[[ -d "${HOME}/.m2/repository/org/yawlfoundation/yawl-parent" ]] && OFFLINE_FLAG="-o"
[[ -n "$OFFLINE_FLAG" ]] && MVN_ARGS+=("$OFFLINE_FLAG")

LABEL="lint"
[[ "$SCOPE" == "explicit" ]] && LABEL+=" [${EXPLICIT_MODULES}]" || LABEL+=" [all]"

START_MS=$(date +%s%3N)
echo "dx-lint: ${LABEL}"

# Phase 1: Compile (incremental)
echo "dx-lint: compile..."
mvn compile "${MVN_ARGS[@]}" \
    -Djacoco.skip=true \
    -Dmaven.javadoc.skip=true \
    -Denforcer.skip=true

# Phase 2: SpotBugs check
echo "dx-lint: spotbugs..."
mvn spotbugs:check "${MVN_ARGS[@]}" \
    -Djacoco.skip=true \
    -Dmaven.javadoc.skip=true \
    -Denforcer.skip=true 2>&1 || {
    echo "dx-lint: SpotBugs found issues (see output above)"
    exit 1
}

END_MS=$(date +%s%3N)
ELAPSED_MS=$((END_MS - START_MS))
ELAPSED_S=$(awk "BEGIN{printf \"%.1f\", ${ELAPSED_MS}/1000}")

echo "dx-lint: OK (${ELAPSED_S}s)"
