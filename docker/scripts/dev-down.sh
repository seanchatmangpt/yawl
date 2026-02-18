#!/usr/bin/env bash
# ==========================================================================
# dev-down.sh — Stop YAWL Development Environment
#
# Usage:
#   bash docker/scripts/dev-down.sh              # Stop containers
#   bash docker/scripts/dev-down.sh --clean      # Stop and remove volumes
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --clean|-c) CLEAN=true ;;
        --help|-h)  echo "Usage: dev-down.sh [--clean]"; exit 0 ;;
    esac
done

echo "=== Stopping YAWL Development Environment ==="

if [[ "$CLEAN" == "true" ]]; then
    docker compose -f docker-compose.dev.yml down -v --remove-orphans
    echo "Containers and volumes removed"
else
    docker compose -f docker-compose.dev.yml down --remove-orphans
    echo "Containers stopped"
fi

echo ""
echo "Restart with: bash docker/scripts/dev-up.sh"
