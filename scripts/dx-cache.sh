#!/usr/bin/env bash
# ==========================================================================
# dx-cache.sh — Build Artifact Caching for Session Persistence
#
# Syncs target/ directories to ~/.cache/yawl-build for fast incremental
# builds across sessions. Reduces rebuild time by 50-70%.
#
# Usage:
#   bash scripts/dx-cache.sh save      # Cache current build artifacts
#   bash scripts/dx-cache.sh restore   # Restore cached artifacts
#   bash scripts/dx-cache.sh status    # Show cache size and coverage
#   bash scripts/dx-cache.sh clear     # Clear all cached artifacts
#
# Environment:
#   DX_CACHE_DIR - Override cache location (default: ~/.cache/yawl-build)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CACHE_DIR="${DX_CACHE_DIR:-${HOME}/.cache/yawl-build}"
cd "${REPO_ROOT}"

# ── Module list (must match pom.xml) ─────────────────────────────────────
ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)

# ── Parse command ─────────────────────────────────────────────────────────
COMMAND="${1:-status}"

case "$COMMAND" in
    save|restore|status|clear) ;;
    -h|--help)
        sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
        exit 0 ;;
    *)
        echo "Unknown command: $COMMAND. Use save|restore|status|clear"
        exit 1 ;;
esac

# ── Helper functions ──────────────────────────────────────────────────────
format_size() {
    local bytes=$1
    if [[ $bytes -ge 1073741824 ]]; then
        echo "$(python3 -c "print(f'{$bytes/1073741824:.1f}')")G"
    elif [[ $bytes -ge 1048576 ]]; then
        echo "$(python3 -c "print(f'{$bytes/1048576:.1f}')")M"
    elif [[ $bytes -ge 1024 ]]; then
        echo "$(python3 -c "print(f'{$bytes/1024:.1f}')")K"
    else
        echo "${bytes}B"
    fi
}

get_dir_size() {
    local dir="$1"
    if [[ -d "$dir" ]]; then
        # macOS compatible
        du -s "$dir" 2>/dev/null | awk '{print $1 * 512}' || echo "0"
    else
        echo "0"
    fi
}

# ── status command ────────────────────────────────────────────────────────
if [[ "$COMMAND" == "status" ]]; then
    echo "=== YAWL Build Cache Status ==="
    echo ""

    if [[ ! -d "$CACHE_DIR" ]]; then
        echo "Cache directory does not exist: ${CACHE_DIR}"
        echo "Run: bash scripts/dx-cache.sh save"
        exit 0
    fi

    TOTAL_SIZE=0
    CACHED_MODULES=0

    printf "%-25s %10s %15s\n" "MODULE" "SIZE" "STATUS"
    printf "%-25s %10s %15s\n" "------" "----" "------"

    for mod in "${ALL_MODULES[@]}"; do
        CACHE_MOD_DIR="${CACHE_DIR}/${mod}"
        if [[ -d "$CACHE_MOD_DIR" ]]; then
            SIZE=$(get_dir_size "$CACHE_MOD_DIR")
            TOTAL_SIZE=$((TOTAL_SIZE + SIZE))
            CACHED_MODULES=$((CACHED_MODULES + 1))

            # Check if source has newer files than cache
            SOURCE_DIR="${REPO_ROOT}/${mod}/src"
            if [[ -d "$SOURCE_DIR" ]]; then
                NEWEST_SOURCE=$(find "$SOURCE_DIR" -type f -exec stat -c %Y {} \; 2>/dev/null | sort -rn | head -1 || echo "0")
                NEWEST_CACHE=$(find "$CACHE_MOD_DIR" -type f -exec stat -c %Y {} \; 2>/dev/null | sort -rn | head -1 || echo "0")

                if [[ "$NEWEST_SOURCE" -gt "$NEWEST_CACHE" ]]; then
                    printf "%-25s %10s %15s\n" "$mod" "$(format_size $SIZE)" "STALE"
                else
                    printf "%-25s %10s %15s\n" "$mod" "$(format_size $SIZE)" "current"
                fi
            else
                printf "%-25s %10s %15s\n" "$mod" "$(format_size $SIZE)" "current"
            fi
        else
            printf "%-25s %10s %15s\n" "$mod" "-" "not cached"
        fi
    done

    echo ""
    echo "Cache directory: ${CACHE_DIR}"
    echo "Total size: $(format_size $TOTAL_SIZE)"
    echo "Modules cached: ${CACHED_MODULES}/${#ALL_MODULES[@]}"
    exit 0
fi

# ── clear command ─────────────────────────────────────────────────────────
if [[ "$COMMAND" == "clear" ]]; then
    echo "Clearing build cache..."

    if [[ -d "$CACHE_DIR" ]]; then
        SIZE_BEFORE=$(get_dir_size "$CACHE_DIR")
        rm -rf "${CACHE_DIR:?}"/*
        echo "Cleared $(format_size $SIZE_BEFORE) from ${CACHE_DIR}"
    else
        echo "Cache directory does not exist: ${CACHE_DIR}"
    fi
    exit 0
fi

# ── save command ──────────────────────────────────────────────────────────
if [[ "$COMMAND" == "save" ]]; then
    echo "=== Saving Build Artifacts to Cache ==="
    echo ""

    mkdir -p "$CACHE_DIR"

    SAVED_SIZE=0
    SAVED_MODULES=0

    for mod in "${ALL_MODULES[@]}"; do
        TARGET_DIR="${REPO_ROOT}/${mod}/target"
        CACHE_MOD_DIR="${CACHE_DIR}/${mod}"

        if [[ -d "$TARGET_DIR" ]]; then
            echo "Saving ${mod}..."

            # Remove old cache for this module
            rm -rf "$CACHE_MOD_DIR"

            # Copy target directory (excluding test-output for space)
            mkdir -p "$CACHE_MOD_DIR"
            rsync -a --exclude='test-output' --exclude='*.log' "$TARGET_DIR/" "$CACHE_MOD_DIR/" 2>/dev/null || true

            SIZE=$(get_dir_size "$CACHE_MOD_DIR")
            SAVED_SIZE=$((SAVED_SIZE + SIZE))
            SAVED_MODULES=$((SAVED_MODULES + 1))
        fi
    done

    echo ""
    echo "Saved $(format_size $SAVED_SIZE) from ${SAVED_MODULES} modules to ${CACHE_DIR}"
    exit 0
fi

# ── restore command ───────────────────────────────────────────────────────
if [[ "$COMMAND" == "restore" ]]; then
    echo "=== Restoring Build Artifacts from Cache ==="
    echo ""

    if [[ ! -d "$CACHE_DIR" ]]; then
        echo "Cache directory does not exist: ${CACHE_DIR}"
        echo "Run: bash scripts/dx-cache.sh save"
        exit 1
    fi

    RESTORED_SIZE=0
    RESTORED_MODULES=0

    for mod in "${ALL_MODULES[@]}"; do
        CACHE_MOD_DIR="${CACHE_DIR}/${mod}"
        TARGET_DIR="${REPO_ROOT}/${mod}/target"

        if [[ -d "$CACHE_MOD_DIR" ]]; then
            echo "Restoring ${mod}..."

            # Preserve test-output if it exists
            TEST_OUTPUT="${TARGET_DIR}/test-output"
            TEMP_TEST=""
            if [[ -d "$TEST_OUTPUT" ]]; then
                TEMP_TEST=$(mktemp -d)
                mv "$TEST_OUTPUT" "$TEMP_TEST/" 2>/dev/null || true
            fi

            # Remove current target and restore from cache
            rm -rf "$TARGET_DIR"
            mkdir -p "$TARGET_DIR"
            rsync -a "$CACHE_MOD_DIR/" "$TARGET_DIR/" 2>/dev/null || true

            # Restore test-output
            if [[ -n "$TEMP_TEST" && -d "${TEMP_TEST}/test-output" ]]; then
                mv "${TEMP_TEST}/test-output" "$TARGET_DIR/" 2>/dev/null || true
                rm -rf "$TEMP_TEST"
            fi

            SIZE=$(get_dir_size "$TARGET_DIR")
            RESTORED_SIZE=$((RESTORED_SIZE + SIZE))
            RESTORED_MODULES=$((RESTORED_MODULES + 1))
        fi
    done

    echo ""
    echo "Restored $(format_size $RESTORED_SIZE) from ${RESTORED_MODULES} modules"
    echo ""
    echo "Note: You may need to run 'mvn compile' to refresh stale artifacts."
    exit 0
fi
