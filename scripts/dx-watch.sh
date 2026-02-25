#!/usr/bin/env bash
# ==========================================================================
# dx-watch.sh — Continuous Build-Test Watcher for Code Agents
#
# Watches for file changes and automatically runs dx.sh on changed modules.
# Uses inotifywait for Linux or fswatch for macOS.
#
# Usage:
#   bash scripts/dx-watch.sh               # Watch src/ and test/
#   bash scripts/dx-watch.sh module-dir   # Watch specific directory
#   bash scripts/dx-watch.sh -p java      # Watch only .java files
#   bash scripts/dx-watch.sh -t 5         # Trigger delay (seconds)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Configuration ─────────────────────────────────────────────────────────
WATCH_DIR="${1:-src}"
FILE_PATTERN=""
TRIGGER_DELAY=3
QUIET=0

# Parse arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        -p) FILE_PATTERN="*.$2"; shift 2 ;;
        -t) TRIGGER_DELAY="$2"; shift 2 ;;
        -q) QUIET=1; shift ;;
        -h|--help)
            echo "Usage: $0 [directory] [-p pattern] [-t delay] [-q]"
            echo "  directory   : Directory to watch (default: src/)"
            echo "  -p pattern  : File pattern (e.g., '*.java')"
            echo "  -t delay    : Trigger delay in seconds (default: 3)"
            echo "  -q          : Quiet mode"
            exit 0
            ;;
        *) WATCH_DIR="$1"; shift ;;
    esac
done

# Validate watch directory
if [[ ! -d "$WATCH_DIR" ]]; then
    echo "Error: Directory '$WATCH_DIR' does not exist"
    exit 1
fi

echo "dx-watch: Watching $WATCH_DIR ${FILE_PATTERN:+(pattern: $FILE_PATTERN)}"
echo "dx-watch: Trigger delay: ${TRIGGER_DELAY}s"
echo "dx-watch: Press Ctrl+C to stop"

# ── Dependency Check ──────────────────────────────────────────────────────
check_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Error: Required command '$1' not found"
        exit 1
    fi
}

# Check for available watcher
if command -v inotifywait >/dev/null 2>&1; then
    WATCHER="inotifywait"
    WATCHER_CMD="inotifywait -r -e modify,create,delete,move --format '%w%f %e'"
elif command -v fswatch >/dev/null 2>&1; then
    WATCHER="fswatch"
    WATCHER_CMD="fswatch --event=Created --event=Removed --event=Updated --event=Renamed --event=Moved -1 $WATCH_DIR"
else
    echo "Error: No file watcher found. Please install inotifywait (Linux) or fswatch (macOS)"
    echo "  Ubuntu: sudo apt install inotify-tools"
    echo "  macOS: brew install fswatch"
    exit 1
fi

check_command "$WATCHER"

# ── Build State Tracking ──────────────────────────────────────────────────
LAST_BUILD_TIME=0
BUILD_LOCK="/tmp/dx-watch.lock"

# Debounced build function
run_build() {
    local current_time=$(date +%s%3N)
    local time_since_last=$(( (current_time - LAST_BUILD_TIME) / 1000 ))

    # Skip if build happened too recently
    if [[ $time_since_last -lt $TRIGGER_DELAY ]]; then
        return
    fi

    # Acquire lock
    if [[ -f "$BUILD_LOCK" ]] && [[ $(cat "$BUILD_LOCK") == $$ ]]; then
        return
    fi

    echo $$ > "$BUILD_LOCK"

    # Run build
    echo "$(date): Changes detected, running dx.sh..."
    if [[ $QUIET -eq 1 ]]; then
        bash scripts/dx.sh >/dev/null 2>&1
    else
        bash scripts/dx.sh
    fi

    LAST_BUILD_TIME=$(date +%s%3N)
    rm -f "$BUILD_LOCK"
}

# ── Watch Loop ────────────────────────────────────────────────────────────
trap 'echo "dx-watch: Stopping..."; rm -f "$BUILD_LOCK"; exit 0' INT TERM

if [[ "$WATCHER" == "inotifywait" ]]; then
    # Linux with inotifywait
    while true; do
        if [[ -n "$FILE_PATTERN" ]]; then
            $WATCHER_CMD | while read file event; do
                if [[ "$file" =~ $FILE_PATTERN ]]; then
                    run_build
                    break
                fi
            done
        else
            $WATCHER_CMD | while read file event; do
                # Check if it's a file and not directory
                if [[ -f "$file" ]]; then
                    run_build
                    break
                fi
            done
        fi
        sleep 0.1
    done
else
    # macOS with fswatch
    while true; do
        $WATCHER_CMD | while read file; do
            if [[ -f "$file" ]]; then
                run_build
                break
            fi
        done
        sleep 0.1
    done
fi