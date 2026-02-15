#!/bin/bash
# Post-Edit Hook - Claude Code Native Memory
# Records modifications to session history

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Arguments
FILE_PATH="${1:-}"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
NC='\033[0m'

# Skip if no file path
if [[ -z "${FILE_PATH}" ]]; then
    exit 0
fi

echo -e "${BLUE}[post-edit] Recording: ${FILE_PATH}${NC}"

# Get relative path
RELATIVE_PATH="${FILE_PATH#${PROJECT_ROOT}/"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Log to session history
HISTORY_DIR="${PROJECT_ROOT}/.claude/memory"
mkdir -p "${HISTORY_DIR}"
echo "[${TIMESTAMP}] edit: ${RELATIVE_PATH}" >> "${HISTORY_DIR}/history.log" 2>/dev/null || true

echo -e "${GREEN}[post-edit] Recorded${NC}"
exit 0
