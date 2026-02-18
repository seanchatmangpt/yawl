#!/usr/bin/env bash
# ==========================================================================
# dx-setup-tmpfs.sh — Mount RAM Disk for Build Output (Linux only)
#
# Creates tmpfs mounts for each module's target/ directory, eliminating
# disk I/O during compilation and testing. All build artifacts live in RAM.
#
# Usage:
#   sudo bash scripts/dx-setup-tmpfs.sh          # Mount tmpfs for all modules
#   sudo bash scripts/dx-setup-tmpfs.sh --undo   # Unmount all tmpfs
#   bash scripts/dx-setup-tmpfs.sh --status       # Show mount status
#
# NOTE: Requires root/sudo. Artifacts are lost on reboot (that's the point).
# Typical RAM usage: ~500MB-1GB for full build.
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

MODE="${1:-mount}"

MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-worklet yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)

TMPFS_SIZE="256m"  # Per-module tmpfs size

case "$MODE" in
    --status|status)
        echo "dx-tmpfs: Mount status"
        MOUNTED=0
        for mod in "${MODULES[@]}"; do
            TARGET_DIR="${REPO_ROOT}/${mod}/target"
            if mountpoint -q "$TARGET_DIR" 2>/dev/null; then
                SIZE=$(df -h "$TARGET_DIR" | tail -1 | awk '{print $2}')
                USED=$(df -h "$TARGET_DIR" | tail -1 | awk '{print $3}')
                echo "  ${mod}/target: MOUNTED (${USED}/${SIZE} used)"
                MOUNTED=$((MOUNTED + 1))
            else
                echo "  ${mod}/target: not mounted"
            fi
        done
        echo "dx-tmpfs: ${MOUNTED}/${#MODULES[@]} modules on tmpfs"
        ;;

    --undo|undo|unmount)
        echo "dx-tmpfs: Unmounting all tmpfs..."
        for mod in "${MODULES[@]}"; do
            TARGET_DIR="${REPO_ROOT}/${mod}/target"
            if mountpoint -q "$TARGET_DIR" 2>/dev/null; then
                umount "$TARGET_DIR"
                echo "  Unmounted: ${mod}/target"
            fi
        done
        echo "dx-tmpfs: Done. Build artifacts cleared."
        ;;

    mount|--mount)
        echo "dx-tmpfs: Mounting tmpfs for build output..."

        # Check available RAM
        AVAIL_MB=$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || echo "0")
        NEEDED_MB=$((${#MODULES[@]} * 256))
        echo "dx-tmpfs: Available RAM: ${AVAIL_MB}MB, reserved: ${NEEDED_MB}MB"

        if [[ "$AVAIL_MB" -lt "$NEEDED_MB" ]]; then
            echo "dx-tmpfs: WARNING: Low RAM. Reducing per-module size to 128m"
            TMPFS_SIZE="128m"
        fi

        for mod in "${MODULES[@]}"; do
            TARGET_DIR="${REPO_ROOT}/${mod}/target"
            mkdir -p "$TARGET_DIR"

            if mountpoint -q "$TARGET_DIR" 2>/dev/null; then
                echo "  ${mod}/target: already mounted"
            else
                mount -t tmpfs -o size="${TMPFS_SIZE}" tmpfs "$TARGET_DIR"
                echo "  ${mod}/target: MOUNTED (${TMPFS_SIZE})"
            fi
        done

        echo "dx-tmpfs: All modules on tmpfs. Build I/O now in RAM."
        echo "dx-tmpfs: Undo with: sudo bash scripts/dx-setup-tmpfs.sh --undo"
        ;;

    *)
        echo "Usage: dx-setup-tmpfs.sh [--mount|--undo|--status]"
        exit 1
        ;;
esac
