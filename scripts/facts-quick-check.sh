#!/usr/bin/env bash
# ==========================================================================
# facts-quick-check.sh — Check if Observatory Facts are Stale (80/20 Win)
#
# Quick check: are facts fresh or do you need to refresh? Saves time spent
# searching stale facts or running unnecessary observatory updates.
#
# Usage:
#   bash scripts/facts-quick-check.sh              # check staleness
#   bash scripts/facts-quick-check.sh --refresh    # refresh if stale
#   bash scripts/facts-quick-check.sh --details    # show all fact files
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

MODE="check"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --refresh)  MODE="refresh";    shift ;;
        --details)  MODE="details";    shift ;;
        -h|--help)  sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'; exit 0 ;;
        *)          echo "Unknown: $1"; exit 1 ;;
    esac
done

# ── Colors ────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_step() { echo -e "${BLUE}→${NC} $*"; }
log_ok()   { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_err()  { echo -e "${RED}✗${NC} $*"; }

FACTS_DIR="docs/v6/latest/facts"
MAX_AGE_HOURS=24

# ── Check Fact Files Age ──────────────────────────────────────────────────
check_facts() {
    if [ ! -d "$FACTS_DIR" ]; then
        log_err "Facts directory not found: $FACTS_DIR"
        return 1
    fi

    log_step "Checking fact files (max age: ${MAX_AGE_HOURS}h)"
    echo ""

    ALL_FRESH=1
    STALE_FILES=()

    for fact_file in "$FACTS_DIR"/*.json; do
        if [ ! -f "$fact_file" ]; then
            continue
        fi

        FILENAME=$(basename "$fact_file")
        FILE_AGE_SECONDS=$(($(date +%s) - $(stat -f%m "$fact_file" 2>/dev/null || stat -c%Y "$fact_file")))
        FILE_AGE_HOURS=$((FILE_AGE_SECONDS / 3600))

        if [ "$FILE_AGE_HOURS" -lt "$MAX_AGE_HOURS" ]; then
            log_ok "$FILENAME (${FILE_AGE_HOURS}h old)"
        else
            log_warn "$FILENAME (${FILE_AGE_HOURS}h old - STALE)"
            ALL_FRESH=0
            STALE_FILES+=("$FILENAME")
        fi
    done

    echo ""

    if [ $ALL_FRESH -eq 1 ]; then
        log_ok "All facts are fresh!"
        return 0
    else
        log_warn "${#STALE_FILES[@]} fact(s) are stale. Run with --refresh to update."
        return 1
    fi
}

# ── Refresh if Needed ─────────────────────────────────────────────────────
refresh_facts() {
    check_facts || {
        log_step "Refreshing observatory facts..."
        if [ -f "scripts/observatory/observatory.sh" ]; then
            bash scripts/observatory/observatory.sh
            log_ok "Facts refreshed"
        else
            log_err "observatory.sh not found"
            return 1
        fi
    }
}

# ── Show Details ──────────────────────────────────────────────────────────
show_details() {
    echo -e "${CYAN}=== FACT FILES ===${NC}"
    echo ""

    if [ ! -d "$FACTS_DIR" ]; then
        log_err "Facts directory not found: $FACTS_DIR"
        return 1
    fi

    ls -lh "$FACTS_DIR"/*.json 2>/dev/null | awk '{
        printf "  %-30s %8s  ", $9, $5;
        system("date -r " $9 " +%H:%M:%S 2>/dev/null || date -r " $9)
    }' | sed 's|.*/||'

    echo ""
    TOTAL_SIZE=$(du -sh "$FACTS_DIR" 2>/dev/null | awk '{print $1}')
    FACT_COUNT=$(ls "$FACTS_DIR"/*.json 2>/dev/null | wc -l)
    log_ok "Total: $FACT_COUNT fact files, $TOTAL_SIZE"
}

# ── Main ──────────────────────────────────────────────────────────────────
case "$MODE" in
    check)
        check_facts
        ;;
    refresh)
        refresh_facts
        ;;
    details)
        show_details
        ;;
esac
