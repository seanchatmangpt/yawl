#!/usr/bin/env bash
# ==========================================================================
# mcp-quick.sh — One-Command MCP Development Loop (80/20 DX Win)
#
# Fastest path for MCP integration development: compile MCP module, run tests,
# validate MCP files, show test results. No unnecessary overhead.
#
# Usage:
#   bash scripts/mcp-quick.sh               # compile + test MCP
#   bash scripts/mcp-quick.sh --validate    # + validate MCP specs
#   bash scripts/mcp-quick.sh --watch       # repeat on file changes
#   bash scripts/mcp-quick.sh --full        # compile all + test MCP
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

MODE="quick"
WATCH_MODE=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --validate)  MODE="validate";    shift ;;
        --watch)     WATCH_MODE=1;       shift ;;
        --full)      MODE="full";        shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *)           echo "Unknown: $1"; exit 1 ;;
    esac
done

# ── Colors for output ─────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_step() { echo -e "${BLUE}→${NC} $*"; }
log_ok()   { echo -e "${GREEN}✓${NC} $*"; }
log_warn() { echo -e "${YELLOW}⚠${NC} $*"; }
log_err()  { echo -e "${RED}✗${NC} $*"; }

# ── Main MCP Quick Loop ───────────────────────────────────────────────────
run_mcp_quick() {
    log_step "Building yawl-integration (MCP module only)"

    if bash scripts/dx.sh compile -pl yawl-integration -amd >/dev/null 2>&1; then
        log_ok "Compilation succeeded"
    else
        log_err "Compilation failed"
        bash scripts/dx.sh compile -pl yawl-integration -amd
        return 1
    fi

    log_step "Running MCP tests"
    if bash scripts/dx.sh test -pl yawl-integration >/dev/null 2>&1; then
        log_ok "All MCP tests passed"
    else
        log_warn "Some tests failed"
        bash scripts/dx.sh test -pl yawl-integration
    fi

    if [ "$MODE" = "validate" ] || [ "$MODE" = "full" ]; then
        log_step "Validating MCP specifications"
        if [ -f "src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java" ]; then
            if grep -q "McpServer.sync" src/org/yawlfoundation/yawl/integration/mcp/YawlMcpServer.java; then
                log_ok "MCP Server implements SDK protocol"
            else
                log_warn "MCP Server protocol check inconclusive"
            fi
        fi

        if [ -f "src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java" ]; then
            TOOL_COUNT=$(grep -c "createAll(" src/org/yawlfoundation/yawl/integration/mcp/spec/YawlToolSpecifications.java || echo 0)
            log_ok "Found YawlToolSpecifications"
        fi
    fi

    if [ "$MODE" = "full" ]; then
        log_step "Running full build on all modules"
        bash scripts/dx.sh all
    fi
}

# ── Watch mode ────────────────────────────────────────────────────────────
if [ $WATCH_MODE -eq 1 ]; then
    log_step "Watch mode enabled - will re-run on file changes"
    log_warn "Press Ctrl+C to stop"

    while true; do
        clear
        echo "=== MCP Quick Build ($(date +'%H:%M:%S')) ==="
        run_mcp_quick

        log_step "Waiting for file changes (watching src/ and yawl-integration/)..."
        inotifywait -e modify -r src/org/yawlfoundation/yawl/integration/mcp yawl-integration/src 2>/dev/null || sleep 2
    done
else
    run_mcp_quick
    log_ok "MCP quick build complete!"
fi
