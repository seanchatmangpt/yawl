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
#
# Environment:
#   DX_OFFLINE=1       Force offline mode (default: auto-detect)
#   DX_OFFLINE=0       Force online mode
#   DX_FAIL_AT=end     Don't stop on first module failure (default: fast)
#   DX_VERBOSE=1       Show Maven output (default: quiet)
#   DX_CLEAN=1         Run clean phase (default: incremental)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Parse arguments ───────────────────────────────────────────────────────
PHASE="compile-test"
SCOPE="changed"
EXPLICIT_MODULES=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        compile)  PHASE="compile";      shift ;;
        test)     PHASE="test";         shift ;;
        all)      SCOPE="all";          shift ;;
        -pl)      EXPLICIT_MODULES="$2"; SCOPE="explicit"; shift 2 ;;
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
# NOTE: yawl-worklet removed - not in pom.xml
ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)

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

if [[ "$SCOPE" == "changed" ]]; then
    DETECTED=$(detect_changed_modules)
    if [[ "$DETECTED" == "all" || -z "$DETECTED" ]]; then
        SCOPE="all"
    else
        EXPLICIT_MODULES="$DETECTED"
        SCOPE="explicit"
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

# Offline
[[ -n "$OFFLINE_FLAG" ]] && MVN_ARGS+=("$OFFLINE_FLAG")

# Quiet unless verbose
[[ "${DX_VERBOSE:-0}" != "1" ]] && MVN_ARGS+=("-q")

# Clean phase (default: skip for incremental builds)
GOALS=()
[[ "${DX_CLEAN:-0}" == "1" ]] && GOALS+=("clean")

# Build phases based on mode
case "$PHASE" in
    compile)      GOALS+=("compile") ;;
    test)         GOALS+=("test") ;;
    compile-test) GOALS+=("compile" "test") ;;
esac

# Module targeting
if [[ "$SCOPE" == "explicit" && -n "$EXPLICIT_MODULES" ]]; then
    MVN_ARGS+=("-pl" "$EXPLICIT_MODULES" "-amd")
fi

# Fail strategy
# Fail strategy - fast fail by default
if [[ "${DX_FAIL_AT:-fast}" == "fast" ]]; then
    MVN_ARGS+=("--fail-fast")
else
    MVN_ARGS+=("--fail-at-end")
fi

# ── Execute ──────────────────────────────────────────────────────────────
LABEL="$PHASE"
if [[ "$SCOPE" == "explicit" ]]; then
    LABEL+=" [${EXPLICIT_MODULES}]"
else
    LABEL+=" [all modules]"
fi

# Use Python for cross-platform millisecond precision
START_MS=$(python3 -c "import time; print(int(time.time() * 1000))")

echo "dx: ${LABEL}"
echo "dx: mvn ${GOALS[*]} ${MVN_ARGS[*]}"

set +e
$MVN_CMD "${GOALS[@]}" "${MVN_ARGS[@]}"
EXIT_CODE=$?
set -e

END_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
ELAPSED_MS=$((END_MS - START_MS))
ELAPSED_S=$(python3 -c "print(f\"{${ELAPSED_MS}/1000:.1f}\")")

if [[ $EXIT_CODE -eq 0 ]]; then
    echo "dx: OK (${ELAPSED_S}s)"
else
    echo "dx: FAILED (${ELAPSED_S}s, exit ${EXIT_CODE})"
    exit $EXIT_CODE
fi
