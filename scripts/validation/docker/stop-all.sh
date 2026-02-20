#!/bin/bash

# Stop all YAWL validation containers
# Usage: bash scripts/validation/docker/stop-all.sh [-v]

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"

# Parse flags
VOLUME_FLAG=""
while getopts "vh" opt; do
    case $opt in
        v) VOLUME_FLAG="-v" ;;
        h)
            echo "Usage: $0 [-v]"
            echo "  -v    Remove volumes (including database data)"
            exit 0
            ;;
        *) ;;
    esac
done

log_info "Stopping all YAWL validation containers..."

# Stop all services
docker compose --profile development --profile production down $VOLUME_FLAG

log_info "All containers stopped${VOLUME_FLAG:+ with volumes}"