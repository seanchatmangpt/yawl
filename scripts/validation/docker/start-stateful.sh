#!/bin/bash

# Start stateful YAWL engine with PostgreSQL
# Usage: bash scripts/validation/docker/start-stateful.sh

set -euo pipefail

# Source helpers
source "$(dirname "$0")/../lib/common.sh"
source "$(dirname "$0")/api-helpers.sh"

log_info "Starting stateful YAWL engine with PostgreSQL..."

# Start production services
log_info "Starting postgres and yawl-engine-prod containers..."
docker compose --profile production up -d postgres yawl-engine-prod

# Wait for PostgreSQL
log_info "Waiting for PostgreSQL to be ready..."
for i in {1..30}; do
    if docker exec yawl-postgres pg_isready -U yawl -d yawl; then
        log_info "PostgreSQL is ready"
        break
    fi
    sleep 2
    if [ $i -eq 30 ]; then
        log_error "PostgreSQL not ready after 30 seconds"
        exit 1
    fi
done

# Wait for engine
wait_for_engine "http://localhost:8080" 300

# Export URLs for other scripts
export ENGINE_URL="http://localhost:8080"
export ENGINE_IB_URL="http://localhost:8080/yawl/ib"
export DATABASE_URL="postgresql://yawl:yawl_password@localhost:5432/yawl"

log_info "Engine started successfully"
log_info "  - Health URL: ${ENGINE_URL}/actuator/health/liveness"
log_info "  - Interface B: ${ENGINE_IB_URL}"
log_info "  - Engine URL: ${ENGINE_URL}"
log_info "  - Database: ${DATABASE_URL}"