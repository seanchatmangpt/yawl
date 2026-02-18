#!/usr/bin/env bash
# watch-build.sh — Watch source files and trigger incremental compilation on change.
#
# Purpose:
#   Monitors Java source files under src/ and test/ directories. When a .java file
#   is saved, triggers an incremental Maven compile without running tests. This
#   gives sub-15-second feedback for syntax and type errors during active editing.
#
# Prerequisites:
#   Linux:  apt-get install inotify-tools  (provides inotifywait)
#   macOS:  brew install fswatch           (provides fswatch)
#   The script detects which tool is available and uses the appropriate one.
#
# What is watched:
#   - src/**/*.java   (production sources across all modules)
#   - test/**/*.java  (test sources, not in use for module subdirectories)
#   - yawl-*/src/**/*.java  (module-specific source trees)
#
# What runs on change:
#   - mvn compile -pl <module> -am (incremental, only changed files)
#   - No tests are run (use test-quick.sh or test-full.sh for that)
#
# Usage:
#   ./scripts/watch-build.sh               # Watch all module sources
#   ./scripts/watch-build.sh yawl-engine   # Watch a specific module only
#
# Stop watching: Ctrl+C
#
# Estimated rebuild time per change: ~5-15s (incremental, warm cache)
#
# See DEVELOPER-BUILD-GUIDE.md for full documentation.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"

MODULE="${1:-}"

if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
    echo "ERROR: pom.xml not found at ${PROJECT_ROOT}" >&2
    exit 1
fi

# Determine which watch tool is available
if command -v inotifywait &>/dev/null; then
    WATCH_TOOL="inotifywait"
elif command -v fswatch &>/dev/null; then
    WATCH_TOOL="fswatch"
else
    echo "ERROR: No file watch tool found." >&2
    echo "  Linux:  sudo apt-get install inotify-tools" >&2
    echo "  macOS:  brew install fswatch" >&2
    exit 1
fi

# Determine which directories to watch
declare -a WATCH_DIRS=()
if [[ -n "${MODULE}" ]]; then
    if [[ ! -d "${PROJECT_ROOT}/${MODULE}" ]]; then
        echo "ERROR: Module directory not found: ${PROJECT_ROOT}/${MODULE}" >&2
        exit 1
    fi
    WATCH_DIRS=("${PROJECT_ROOT}/${MODULE}/src")
    echo "[watch-build] Watching module: ${MODULE}"
else
    # Collect all src directories from module subdirectories
    while IFS= read -r -d '' src_dir; do
        WATCH_DIRS+=("${src_dir}")
    done < <(find "${PROJECT_ROOT}" -mindepth 2 -maxdepth 3 -name "src" -type d \
        -not -path "*/.git/*" \
        -not -path "*/target/*" \
        -print0)
    echo "[watch-build] Watching all module source directories"
fi

echo "[watch-build] Watch tool: ${WATCH_TOOL}"
echo "[watch-build] Project root: ${PROJECT_ROOT}"
echo "[watch-build] Estimated rebuild time: ~5-15s per change"
echo "[watch-build] Stop watching: Ctrl+C"
echo ""

# Function to trigger a compile
run_compile() {
    local changed_file="$1"
    echo ""
    echo "[watch-build] Change detected: ${changed_file}"
    echo "[watch-build] $(date '+%H:%M:%S') Starting incremental compile..."

    local mvn_args=(
        --file "${PROJECT_ROOT}/pom.xml"
        compile
        -DskipTests
        -Djacoco.skip=true
        -Dmaven.javadoc.skip=true
        -Dmaven.source.skip=true
        -q
    )

    if [[ -n "${MODULE}" ]]; then
        mvn_args+=(-pl "${MODULE}" -am)
    fi

    local start
    start=$(date +%s)

    if mvn "${mvn_args[@]}"; then
        local elapsed=$(( $(date +%s) - start ))
        echo "[watch-build] $(date '+%H:%M:%S') Compile OK in ${elapsed}s"
    else
        local elapsed=$(( $(date +%s) - start ))
        echo "[watch-build] $(date '+%H:%M:%S') Compile FAILED after ${elapsed}s"
        echo "[watch-build] Fix the error above, then save the file again."
    fi

    echo "[watch-build] Watching for next change..."
}

echo "[watch-build] Watching for .java file changes..."
echo "[watch-build] Directories:"
for d in "${WATCH_DIRS[@]}"; do
    echo "  ${d}"
done
echo ""

if [[ "${WATCH_TOOL}" == "inotifywait" ]]; then
    # inotifywait loop: re-trigger on CLOSE_WRITE and MOVED_TO events
    inotifywait \
        --recursive \
        --event close_write,moved_to \
        --format "%w%f" \
        --include '\.java$' \
        --monitor \
        "${WATCH_DIRS[@]}" \
    | while IFS= read -r changed_file; do
        run_compile "${changed_file}"
    done
else
    # fswatch loop (macOS / cross-platform fallback)
    fswatch \
        --recursive \
        --include='\.java$' \
        --exclude='.*' \
        --event=Updated \
        "${WATCH_DIRS[@]}" \
    | while IFS= read -r changed_file; do
        run_compile "${changed_file}"
    done
fi
