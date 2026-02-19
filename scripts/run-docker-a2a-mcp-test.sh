#!/bin/bash
# =============================================================================
# run-docker-a2a-mcp-test.sh — Docker A2A/MCP/Handoff Test Runner
# =============================================================================
#
# Usage:
#   bash scripts/run-docker-a2a-mcp-test.sh [options]
#
# Options:
#   --build        Force rebuild of Docker images
#   --no-clean     Don't cleanup after tests
#   --verbose      Show detailed output
#   --ci           CI mode (non-interactive)
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="docker-compose.a2a-mcp-test.yml"

# Defaults
FORCE_BUILD=false
NO_CLEAN=false
VERBOSE=false
CI_MODE=false

# Parse args
while [[ $# -gt 0 ]]; do
    case $1 in
        --build) FORCE_BUILD=true ;;
        --no-clean) NO_CLEAN=true ;;
        --verbose) VERBOSE=true ;;
        --ci) CI_MODE=true ;;
        *) echo "Unknown option: $1"; exit 2 ;;
    esac
    shift
done

cd "$PROJECT_ROOT"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Ensure network exists
ensure_network() {
    docker network inspect yawl-network >/dev/null 2>&1 || \
        docker network create yawl-network
}

# Build images if needed
build_images() {
    log_info "Building Docker images..."

    # Build engine image
    if [[ "$FORCE_BUILD" == true ]] || ! docker image inspect yawl-engine:6.0.0-alpha >/dev/null 2>&1; then
        log_info "Building yawl-engine image..."
        docker build -f docker/production/Dockerfile.engine -t yawl-engine:6.0.0-alpha .
    fi

    # Build MCP-A2A image
    if [[ "$FORCE_BUILD" == true ]] || ! docker image inspect yawl-mcp-a2a:6.0.0-alpha >/dev/null 2>&1; then
        log_info "Building yawl-mcp-a2a image..."
        docker build -f docker/production/Dockerfile.mcp-a2a-app -t yawl-mcp-a2a:6.0.0-alpha .
    fi

    log_success "Images built"
}

# Run tests
run_tests() {
    log_info "Starting test services..."

    # Start with test profile
    docker compose -f "$COMPOSE_FILE" --profile test up -d yawl-engine

    log_info "Waiting for engine to be healthy..."
    until docker compose -f "$COMPOSE_FILE" exec -T yawl-engine curl -sf http://localhost:8080/actuator/health/liveness >/dev/null 2>&1; do
        sleep 5
    done
    log_success "Engine is healthy"

    # Start MCP-A2A
    docker compose -f "$COMPOSE_FILE" --profile test up -d yawl-mcp-a2a

    log_info "Waiting for MCP-A2A to be healthy..."
    until docker compose -f "$COMPOSE_FILE" exec -T yawl-mcp-a2a curl -sf http://localhost:8080/actuator/health/liveness >/dev/null 2>&1; do
        sleep 5
    done
    log_success "MCP-A2A is healthy"

    # Run test runner
    log_info "Running tests..."
    docker compose -f "$COMPOSE_FILE" --profile test up --abort-on-container-exit test-runner
    local exit_code=$?

    return $exit_code
}

# Cleanup
cleanup() {
    if [[ "$NO_CLEAN" == true ]]; then
        log_info "Skipping cleanup (--no-clean)"
        return
    fi

    log_info "Cleaning up..."
    docker compose -f "$COMPOSE_FILE" --profile test down -v 2>/dev/null || true
}

# Main
main() {
    trap cleanup EXIT

    echo
    echo "=== YAWL A2A/MCP/Handoff Docker Test ==="
    echo

    ensure_network
    build_images
    run_tests
    local result=$?

    if [[ $result -eq 0 ]]; then
        log_success "All tests passed!"
    else
        log_error "Tests failed with exit code: $result"
    fi

    exit $result
}

main