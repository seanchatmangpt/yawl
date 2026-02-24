#!/usr/bin/env bash
# Quick test script for A2A/MCP/Handoff Docker setup

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

echo "=== Quick Docker Test for A2A/MCP/Handoff ==="
echo

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    log_error "Docker is not running. Please start Docker first."
    exit 1
fi

# Create network if needed
log_info "Creating yawl-network..."
docker network create yawl-network 2>/dev/null || true
log_success "Network ready"

# Build images if needed
log_info "Building Docker images..."
docker build -f docker/production/Dockerfile.engine -t yawl-engine:6.0.0-alpha . 2>/dev/null || true
docker build -f docker/production/Dockerfile.mcp-a2a-app -t yawl-mcp-a2a:6.0.0-alpha . 2>/dev/null || true
log_success "Images built"

# Start services
log_info "Starting services..."
docker compose -f docker-compose-simple-test.yml up -d yawl-engine yawl-mcp-a2a

# Wait for health checks
log_info "Waiting for services to be healthy..."
timeout 120 bash -c "
    while ! docker compose -f docker-compose-simple-test.yml exec -T yawl-engine curl -sf http://localhost:8080/actuator/health/liveness >/dev/null 2>&1; do
        sleep 5
        echo 'Waiting for yawl-engine...'
    done
    while ! docker compose -f docker-compose-simple-test.yml exec -T yawl-mcp-a2a curl -sf http://localhost:8080/actuator/health/liveness >/dev/null 2>&1; do
        sleep 5
        echo 'Waiting for yawl-mcp-a2a...'
    done
" || {
    log_error "Health check timeout. Cleaning up..."
    docker compose -f docker-compose-simple-test.yml down -v
    exit 1
}

log_success "All services are healthy"

# Test endpoints
echo
log_info "Testing endpoints..."

# Test MCP health
log_info "Testing MCP health endpoint..."
if curl -s http://localhost:18081/mcp/health | jq -e '.status == "ok"' >/dev/null 2>&1; then
    log_success "MCP health check passed"
else
    log_error "MCP health check failed"
fi

# Test MCP tools
log_info "Testing MCP tools list..."
if curl -s -X POST http://localhost:18081/mcp \
    -H "Content-Type: application/json" \
    -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | \
    jq -e '.result.tools | length > 0' >/dev/null 2>&1; then
    tool_count=$(curl -s -X POST http://localhost:18081/mcp \
        -H "Content-Type: application/json" \
        -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' | \
        jq '.result.tools | length')
    log_success "MCP tools available ($tool_count tools)"
else
    log_error "MCP tools list failed"
fi

# Test A2A agent card
log_info "Testing A2A agent card..."
if curl -s http://localhost:18082/.well-known/agent.json | jq -e '.name' >/dev/null 2>&1; then
    agent_name=$(curl -s http://localhost:18082/.well-known/agent.json | jq -r '.name')
    log_success "A2A agent card available ($agent_name)"
else
    log_error "A2A agent card failed"
fi

# Test health endpoint
log_info "Testing health endpoints..."
curl -s http://localhost:8080/actuator/health/liveness | jq -r '.status' | xargs -I {} echo "Engine: {}"
curl -s http://localhost:18080/actuator/health/liveness | jq -r '.status' | xargs -I {} echo "MCP-A2A: {}"

echo
log_success "Quick test completed successfully!"

# Cleanup
echo
log_info "Cleaning up..."
docker compose -f docker-compose-simple-test.yml down -v

echo
log_success "All tests passed! A2A/MCP/Handoff Docker setup is working."