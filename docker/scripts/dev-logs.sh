#!/usr/bin/env bash
# ==========================================================================
# dev-logs.sh — Tail YAWL Development Environment Logs
#
# Usage:
#   bash docker/scripts/dev-logs.sh              # Tail all logs
#   bash docker/scripts/dev-logs.sh yawl-dev     # Tail specific service
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

SERVICE="${1:-}"

echo "=== YAWL Development Logs ==="
echo "Press Ctrl+C to stop"
echo ""

if [[ -n "$SERVICE" ]]; then
    docker compose -f docker-compose.dev.yml logs -f "$SERVICE"
else
    docker compose -f docker-compose.dev.yml logs -f
fi
