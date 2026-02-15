#!/bin/bash
# Session-End Hook - Claude Code Native
# Generates session report

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}[session-end] Generating session report...${NC}"

# Generate session report
REPORT_FILE="${PROJECT_ROOT}/.claude/memory/session-report.txt"
MEMORY_DIR="$(dirname "${REPORT_FILE}")"
mkdir -p "${MEMORY_DIR}"

{
    echo "=== YAWL Session Report ==="
    echo "Timestamp: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "Project: YAWL ${PROJECT_ROOT##*/}"
    echo ""
    echo "Files Modified:"
    git -C "${PROJECT_ROOT}" diff --name-only 2>/dev/null || echo "No changes"
    echo ""
    echo "Session History:"
    if [[ -f "${PROJECT_ROOT}/.claude/memory/history.log" ]]; then
        tail -20 "${PROJECT_ROOT}/.claude/memory/history.log"
    fi
} > "${REPORT_FILE}" 2>/dev/null || true

# Cleanup temporary files
find "${PROJECT_ROOT}/.claude/memory" -name "*.tmp" -delete 2>/dev/null || true

echo -e "${GREEN}[session-end] Report saved to .claude/memory/session-report.txt${NC}"
exit 0
