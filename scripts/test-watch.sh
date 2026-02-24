#!/usr/bin/env bash
# ==========================================================================
# test-watch.sh — Continuous Test Runner
#
# Watches for file changes and runs affected tests. Uses fswatch on macOS
# and inotifywait on Linux. Debounced execution (2s default).
#
# Usage:
#   bash scripts/test-watch.sh              # Watch all modules
#   bash scripts/test-watch.sh -m module    # Watch specific module
#   bash scripts/test-watch.sh -c class     # Watch specific test class
#   bash scripts/test-watch.sh --debounce 5 # Custom debounce (seconds)
#
# Requirements:
#   macOS: brew install fswatch
#   Linux: apt install inotify-tools
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
MODULE=""
TEST_CLASS=""
DEBOUNCE_SECONDS=2
WATCH_DIRS=()

# ── Parse arguments ───────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -m|--module)   MODULE="$2"; shift 2 ;;
        -c|--class)    TEST_CLASS="$2"; shift 2 ;;
        -d|--debounce) DEBOUNCE_SECONDS="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *) shift ;;
    esac
done

# ── Detect file watcher ───────────────────────────────────────────────────
if command -v fswatch &>/dev/null; then
    WATCHER="fswatch"
elif command -v inotifywait &>/dev/null; then
    WATCHER="inotifywait"
else
    echo "Error: No file watcher found."
    echo "  macOS: brew install fswatch"
    echo "  Linux: apt install inotify-tools"
    exit 1
fi

echo "=== YAWL Continuous Test Runner ==="
echo "Watcher: ${WATCHER}"
echo "Debounce: ${DEBOUNCE_SECONDS}s"
[[ -n "$MODULE" ]] && echo "Module: ${MODULE}"
[[ -n "$TEST_CLASS" ]] && echo "Test class: ${TEST_CLASS}"
echo ""

# ── Determine watch directories ───────────────────────────────────────────
if [[ -n "$MODULE" ]]; then
    WATCH_DIRS=("${REPO_ROOT}/${MODULE}/src")
else
    for mod in yawl-utilities yawl-elements yawl-authentication yawl-engine \
               yawl-stateless yawl-resourcing yawl-scheduling \
               yawl-security yawl-integration yawl-monitoring yawl-webapps \
               yawl-control-panel; do
        [[ -d "${REPO_ROOT}/${mod}/src" ]] && WATCH_DIRS+=("${REPO_ROOT}/${mod}/src")
    done
fi

echo "Watching directories:"
for dir in "${WATCH_DIRS[@]}"; do
    echo "  ${dir}"
done
echo ""
echo "Press Ctrl+C to stop"
echo ""

# ── Build command based on options ────────────────────────────────────────
build_test_command() {
    local changed_file="$1"
    local cmd="mvn -P agent-dx test -q"

    # Detect which module changed
    local changed_module=""
    for dir in "${WATCH_DIRS[@]}"; do
        if [[ "$changed_file" == "${dir}/"* ]]; then
            changed_module=$(echo "$dir" | sed "s|${REPO_ROOT}/||" | cut -d'/' -f1)
            break
        fi
    done

    if [[ -n "$changed_module" ]]; then
        cmd="mvn -P agent-dx test -pl $changed_module -amd -q"
    fi

    if [[ -n "$TEST_CLASS" ]]; then
        cmd="mvn -P agent-dx test -Dtest=${TEST_CLASS} -q"
    fi

    echo "$cmd"
}

# ── Debounce state ────────────────────────────────────────────────────────
LAST_RUN=0
PENDING_FILE=""

run_tests() {
    local file="$1"
    local now=$(date +%s)
    local elapsed=$((now - LAST_RUN))

    if [[ $elapsed -lt $DEBOUNCE_SECONDS ]]; then
        PENDING_FILE="$file"
        return
    fi

    LAST_RUN=$now
    PENDING_FILE=""

    echo ""
    echo "=== [$(date +%H:%M:%S)] Change detected: ${file} ==="

    local cmd
    cmd=$(build_test_command "$file")
    echo "Running: ${cmd}"

    START=$(date +%s%3N)

    set +e
    $cmd 2>&1 | while IFS= read -r line; do
        echo "  $line"
    done
    EXIT_CODE=$?
    set -e

    END=$(date +%s%3N)
    ELAPSED=$((END - START))
    ELAPSED_S=$(awk "BEGIN {printf \"%.1f\", $ELAPSED/1000}")

    if [[ $EXIT_CODE -eq 0 ]]; then
        echo "✓ Tests passed (${ELAPSED_S}s)"
    else
        echo "✗ Tests failed (${ELAPSED_S}s)"
    fi

    echo ""
    echo "Watching for changes..."
}

# ── Start watching ────────────────────────────────────────────────────────
case "$WATCHER" in
    fswatch)
        fswatch -r -e "target" -e ".git" -e "node_modules" "${WATCH_DIRS[@]}" | while read -r file; do
            run_tests "$file"
        done
        ;;
    inotifywait)
        inotifywait -r -m -e modify,create,delete --exclude "target|\.git|node_modules" "${WATCH_DIRS[@]}" | while read -r path action file; do
            run_tests "${path}${file}"
        done
        ;;
esac
