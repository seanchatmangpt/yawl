#!/usr/bin/env bash
# ==========================================================================
# session-info.sh — Developer Session Status (80/20 QOL Win)
#
# Shows: current branch, uncommitted changes, recent commits, build status.
# One command to understand your session state. Saves 5 separate commands.
#
# Usage:
#   bash scripts/session-info.sh        # show full status
#   bash scripts/session-info.sh --quick # compact view
#   bash scripts/session-info.sh --branch # show branch info only
#   bash scripts/session-info.sh --changes # show uncommitted changes only
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

MODE="full"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --quick)   MODE="quick";      shift ;;
        --branch)  MODE="branch";     shift ;;
        --changes) MODE="changes";    shift ;;
        -h|--help) sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'; exit 0 ;;
        *)         echo "Unknown: $1"; exit 1 ;;
    esac
done

# ── Colors ────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# ── Branch Info ───────────────────────────────────────────────────────────
if [[ "$MODE" =~ ^(full|quick|branch)$ ]]; then
    BRANCH=$(git rev-parse --abbrev-ref HEAD)
    COMMITS_AHEAD=$(git rev-list --count origin/main..HEAD 2>/dev/null || echo "?")
    COMMITS_BEHIND=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo "?")

    echo -e "${CYAN}═══ BRANCH ═══${NC}"
    echo -e "Current:  ${BLUE}$BRANCH${NC}"

    if [[ "$BRANCH" == claude/* ]]; then
        SESSION_ID="${BRANCH#claude/}"
        echo -e "Session:  ${GREEN}$SESSION_ID${NC}"
        echo -e "Status:   ${GREEN}claude/* branch${NC} (ready for push)"
    else
        echo -e "Status:   ${YELLOW}Not on claude/* branch${NC}"
    fi

    echo -e "Upstream: ${COMMITS_AHEAD} ahead, ${COMMITS_BEHIND} behind main"
    echo ""
fi

# ── Uncommitted Changes ───────────────────────────────────────────────────
if [[ "$MODE" =~ ^(full|quick|changes)$ ]]; then
    CHANGED_COUNT=$(git status --short | wc -l)

    if [ "$CHANGED_COUNT" -eq 0 ]; then
        echo -e "${CYAN}═══ CHANGES ═══${NC}"
        echo -e "${GREEN}✓${NC} Working tree clean"
        echo ""
    else
        echo -e "${CYAN}═══ CHANGES ═══${NC}"
        echo -e "${YELLOW}${CHANGED_COUNT}${NC} files modified/untracked:"

        if [ "$MODE" = "full" ]; then
            git status --short | head -15
            if [ "$CHANGED_COUNT" -gt 15 ]; then
                echo -e "${YELLOW}... and $((CHANGED_COUNT - 15)) more${NC}"
            fi
        fi
        echo ""
    fi
fi

# ── Recent Commits ────────────────────────────────────────────────────────
if [ "$MODE" = "full" ]; then
    LAST_COMMIT=$(git log -1 --pretty=format:"%h %s (%ar)" 2>/dev/null || echo "?")
    echo -e "${CYAN}═══ LAST COMMIT ═══${NC}"
    echo -e "$LAST_COMMIT"
    echo ""
fi

# ── Quick Summary ─────────────────────────────────────────────────────────
if [ "$MODE" = "quick" ]; then
    STATUS=$(git status --short | wc -l)
    echo -e "${CYAN}$BRANCH${NC} | ${GREEN}${COMMITS_AHEAD}${NC} ahead | ${YELLOW}${STATUS}${NC} changes"
fi
