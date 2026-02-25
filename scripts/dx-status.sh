#!/usr/bin/env bash
# ==========================================================================
# dx-status.sh — Build Health Dashboard
#
# Quick overview of the build system state: what's compiled, what's stale,
# dependency cache health, and last build times.
#
# Usage:
#   bash scripts/dx-status.sh
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

M2_REPO="${HOME}/.m2/repository"

echo "=== YAWL DX Status Dashboard ==="
echo ""

# ── Git status ────────────────────────────────────────────────────────────
BRANCH=$(git branch --show-current 2>/dev/null || echo "unknown")
CHANGED=$(git diff --name-only HEAD 2>/dev/null | wc -l)
STAGED=$(git diff --name-only --cached 2>/dev/null | wc -l)
echo "Git: branch=${BRANCH} changed=${CHANGED} staged=${STAGED}"

# ── Changed modules ──────────────────────────────────────────────────────
# NOTE: yawl-worklet removed - not in pom.xml
ALL_MODULES=(
    yawl-utilities yawl-elements yawl-authentication yawl-engine
    yawl-stateless yawl-resourcing yawl-scheduling
    yawl-security yawl-integration yawl-monitoring yawl-webapps
    yawl-control-panel
)

changed_files=$(git diff --name-only HEAD 2>/dev/null; git diff --name-only --cached 2>/dev/null)
declare -A changed_modules=()
while IFS= read -r file; do
    [[ -z "$file" ]] && continue
    for mod in "${ALL_MODULES[@]}"; do
        if [[ "$file" == "${mod}/"* ]]; then
            changed_modules["$mod"]=1
            break
        fi
    done
done <<< "$changed_files"

CHANGED_LIST=""
for key in "${!changed_modules[@]}"; do
    [[ -n "$CHANGED_LIST" ]] && CHANGED_LIST+=", "
    CHANGED_LIST+="$key"
done

if [[ -n "$CHANGED_LIST" ]]; then
    echo "Changed modules: ${CHANGED_LIST}"
else
    echo "Changed modules: (none)"
fi
echo ""

# ── Compilation state ────────────────────────────────────────────────────
echo "Module compilation state:"
for mod in "${ALL_MODULES[@]}"; do
    TARGET_DIR="${REPO_ROOT}/${mod}/target"
    CLASSES_DIR="${TARGET_DIR}/classes"
    if [[ -d "$CLASSES_DIR" ]]; then
        CLASS_COUNT=$(find "$CLASSES_DIR" -name "*.class" 2>/dev/null | wc -l)
        # Find newest class file
        NEWEST=$(find "$CLASSES_DIR" -name "*.class" -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-)
        if [[ -n "$NEWEST" ]]; then
            AGE=$(stat -c %Y "$NEWEST" 2>/dev/null || echo "0")
            NOW=$(date +%s)
            MINS=$(( (NOW - AGE) / 60 ))
            MARKER=""
            [[ -n "${changed_modules[$mod]+x}" ]] && MARKER=" [STALE]"
            echo "  ${mod}: ${CLASS_COUNT} classes (${MINS}m ago)${MARKER}"
        else
            echo "  ${mod}: compiled (empty)"
        fi
    else
        echo "  ${mod}: NOT COMPILED"
    fi
done
echo ""

# ── Dependency cache ─────────────────────────────────────────────────────
if [[ -d "$M2_REPO" ]]; then
    CACHE_SIZE=$(du -sh "$M2_REPO" 2>/dev/null | cut -f1)
    JAR_COUNT=$(find "$M2_REPO" -name "*.jar" 2>/dev/null | wc -l)
    echo "Maven cache: ${CACHE_SIZE} (${JAR_COUNT} JARs)"

    # Test offline readiness
    if mvn validate -o -q 2>/dev/null; then
        echo "Offline mode: READY"
    else
        echo "Offline mode: NOT READY (missing dependencies)"
    fi
else
    echo "Maven cache: NOT FOUND"
fi
echo ""

# ── Build profiles ───────────────────────────────────────────────────────
echo "Active profiles:"
echo "  default: java25 + fast (JaCoCo off, no integration tests)"
echo "  agent-dx: 2C parallelism, fail-fast, zero overhead"
echo "  ci: JaCoCo + enforcer + SpotBugs"
echo "  analysis: full static analysis (SpotBugs + PMD + Checkstyle)"
echo ""

# ── tmpfs status ─────────────────────────────────────────────────────────
TMPFS_COUNT=0
for mod in "${ALL_MODULES[@]}"; do
    TARGET_DIR="${REPO_ROOT}/${mod}/target"
    if mountpoint -q "$TARGET_DIR" 2>/dev/null; then
        TMPFS_COUNT=$((TMPFS_COUNT + 1))
    fi
done
if [[ $TMPFS_COUNT -gt 0 ]]; then
    echo "tmpfs: ${TMPFS_COUNT}/${#ALL_MODULES[@]} modules on RAM disk"
else
    echo "tmpfs: not active (run: sudo bash scripts/dx-setup-tmpfs.sh)"
fi
echo ""

# ── Quick commands ───────────────────────────────────────────────────────
echo "Quick commands:"
echo "  bash scripts/dx.sh            # Compile + test changed modules"
echo "  bash scripts/dx.sh compile    # Compile only (fastest)"
echo "  bash scripts/dx.sh all        # All modules"
echo "  bash scripts/dx-test-single.sh TestClass module  # Single test"
echo "  bash scripts/dx-lint.sh       # Fast lint check"
echo "  bash scripts/dx-deps.sh       # Check dependency cache"
