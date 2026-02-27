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
# Topological order: every module appears after all its YAWL dependencies.
# See docs/build-sequences.md and docs/v6/diagrams/facts/reactor.json for
# the full dependency graph. Order matters for detect_changed_modules().
ALL_MODULES=(
    # Layer 0 — Foundation (no YAWL deps, parallel)
    yawl-utilities yawl-security yawl-graalpy yawl-graaljs
    # Layer 1 — First consumers (parallel)
    yawl-elements yawl-ggen yawl-graalwasm
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
elif [[ "$SCOPE" == "all" && "${CLAUDE_CODE_REMOTE:-false}" == "true" ]]; then
    # In remote/CI mode ALL_MODULES may have been filtered (e.g. yawl-pi excluded
    # because onnxruntime:1.19.2 is 89MB and cannot be fetched via egress proxy).
    # Pass explicit -pl list so Maven reactor honours the filtered set.
    REMOTE_MODULES=$(IFS=','; echo "${ALL_MODULES[*]}")
    MVN_ARGS+=("-pl" "$REMOTE_MODULES")
fi

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

# ── Execute ──────────────────────────────────────────────────────────────
LABEL="$PHASE"
SCOPE_LABEL="all modules"
if [[ "$SCOPE" == "explicit" ]]; then
    LABEL+=" [${EXPLICIT_MODULES}]"
    SCOPE_LABEL="${EXPLICIT_MODULES}"
fi

# Use Python for cross-platform millisecond precision
START_MS=$(date +%s%3N)

# Pretty header
echo ""
printf "${C_CYAN}dx${C_RESET}: ${C_BLUE}%s${C_RESET}\n" "${LABEL}"
printf "${C_CYAN}dx${C_RESET}: scope=%s | phase=%s | fail-strategy=%s\n" \
    "$SCOPE_LABEL" "$PHASE" "${DX_FAIL_AT:-fast}"

set +e
$MVN_CMD "${GOALS[@]}" "${MVN_ARGS[@]}" 2>&1 | tee /tmp/dx-build-log.txt
EXIT_CODE=$?
set -euo pipefail

END_MS=$(date +%s%3N)
ELAPSED_MS=$((END_MS - START_MS))
ELAPSED_S=$(awk "BEGIN {printf \"%.1f\", $ELAPSED_MS/1000}")

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

# Enhanced status with metrics
echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
    printf "${C_GREEN}${E_OK} SUCCESS${C_RESET} | time: ${ELAPSED_S}s | modules: %d | tests: %d\n" \
        "$MODULES_COUNT" "$TEST_COUNT"
else
    printf "${C_RED}${E_FAIL} FAILED${C_RESET} | time: ${ELAPSED_S}s (exit ${EXIT_CODE}) | failures: %d\n" \
        "$TEST_FAILED"
    printf "\n${C_YELLOW}→${C_RESET} Debug: ${C_CYAN}cat /tmp/dx-build-log.txt | tail -50${C_RESET}\n"
    printf "${C_YELLOW}→${C_RESET} Run again: ${C_CYAN}DX_VERBOSE=1 bash scripts/dx.sh${C_RESET}\n\n"
    exit $EXIT_CODE
fi
echo ""
