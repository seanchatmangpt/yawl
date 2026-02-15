#!/bin/bash
# Notify Hook - Claude Code Native
# Broadcasts messages to session log

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Arguments
MESSAGE="${1:-}"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
NC='\033[0m'

# Skip if no message
if [[ -z "${MESSAGE}" ]]; then
    exit 0
fi

echo -e "${BLUE}[notify] ${MESSAGE}${NC}"

# Log to history
HISTORY_DIR="${PROJECT_ROOT}/.claude/memory"
mkdir -p "${HISTORY_DIR}"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
echo "[${TIMESTAMP}] notify: ${MESSAGE}" >> "${HISTORY_DIR}/history.log" 2>/dev/null || true

echo -e "${GREEN}[notify] Logged${NC}"
exit 0
