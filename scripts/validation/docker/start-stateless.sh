#!/bin/bash

# Start stateless YAWL engine (H2 database)
# Usage: bash scripts/validation/docker/start-stateless.sh

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/api-helpers.sh"

log_info "Starting stateless YAWL engine..."

# Start engine
log_info "Starting yawl-engine container..."
docker compose up -d yawl-engine

# Wait for health
wait_for_engine "http://localhost:8080" 300

# Export URLs for other scripts
export ENGINE_URL="http://localhost:8080"
export ENGINE_IB_URL="http://localhost:8080/yawl/ib"

log_info "Engine started successfully"
log_info "  - Health URL: ${ENGINE_URL}/actuator/health/liveness"
log_info "  - Interface B: ${ENGINE_IB_URL}"
log_info "  - Engine URL: ${ENGINE_URL}"