#!/usr/bin/env bash
# ==========================================================================
# dx-deps.sh — Dependency Cache Status & Offline Readiness
#
# Checks whether all project dependencies are cached in ~/.m2/repository
# for offline builds. Reports missing artifacts and optionally downloads them.
#
# Usage:
#   bash scripts/dx-deps.sh              # Check cache status
#   bash scripts/dx-deps.sh --download   # Download missing deps
#   bash scripts/dx-deps.sh --size       # Show cache size
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

MODE="${1:-check}"

M2_REPO="${HOME}/.m2/repository"

case "$MODE" in
    --size|size)
        if [[ -d "$M2_REPO" ]]; then
            SIZE=$(du -sh "$M2_REPO" 2>/dev/null | cut -f1)
            COUNT=$(find "$M2_REPO" -name "*.jar" 2>/dev/null | wc -l)
            echo "dx-deps: Cache at ${M2_REPO}"
            echo "dx-deps: Size: ${SIZE}"
            echo "dx-deps: JARs: ${COUNT}"
        else
            echo "dx-deps: No cache directory at ${M2_REPO}"
        fi
        ;;

    --download|download)
        echo "dx-deps: Downloading all dependencies..."
        mvn dependency:go-offline -q 2>&1 || {
            echo "dx-deps: Some dependencies could not be downloaded (network issue?)"
            exit 1
        }
        echo "dx-deps: All dependencies cached for offline use"
        ;;

    check|--check)
        echo "dx-deps: Checking offline readiness..."

        # Try an offline compile to see if deps are cached
        if mvn validate -o -q 2>/dev/null; then
            echo "dx-deps: READY for offline builds"

            # Check for YAWL parent in local repo
            if [[ -d "${M2_REPO}/org/yawlfoundation/yawl-parent" ]]; then
                echo "dx-deps: YAWL parent installed locally"
            else
                echo "dx-deps: WARNING: YAWL parent not in local repo"
                echo "dx-deps: Run: mvn install -DskipTests -q"
            fi
        else
            echo "dx-deps: NOT READY for offline builds"
            echo "dx-deps: Run: bash scripts/dx-deps.sh --download"
        fi

        # Report cache stats
        if [[ -d "$M2_REPO" ]]; then
            SIZE=$(du -sh "$M2_REPO" 2>/dev/null | cut -f1)
            echo "dx-deps: Cache size: ${SIZE}"
        fi
        ;;

    *)
        echo "Usage: dx-deps.sh [--check|--download|--size]"
        exit 1
        ;;
esac
