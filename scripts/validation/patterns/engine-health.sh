#!/bin/bash

# YAWL Engine Health Check
# Validates engine readiness for pattern validation

set -euo pipefail

# Configuration
ENGINE_URL="http://localhost:8080"
ENGINE_MGMT_URL="http://localhost:9090"
TIMEOUT=${1:-120}
MAX_RETRIES=$((TIMEOUT / 5))
CONNECTION_ID="${CONNECTION_ID:-admin}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

error() {
    echo "${RED}ERROR:${NC} $1" >&2
    exit 1
}

success() {
    echo "${GREEN}SUCCESS:${NC} $1"
}

warn() {
    echo "${YELLOW}WARNING:${NC} $1"
}

# Check if port is available
check_port() {
    local port="$1"
    local host="${2:-localhost}"

    if nc -z "$host" "$port" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# Check liveness endpoint
check_liveness() {
    log "Checking liveness endpoint: ${ENGINE_MGMT_URL}/actuator/health/liveness"

    local response
    response=$(curl -s -w "%{http_code}" "${ENGINE_MGMT_URL}/actuator/health/liveness" 2>/dev/null || echo "000")

    local http_code="${response: -3}"
    local body="${response%???}"

    if [[ "$http_code" -eq 200 ]]; then
        if echo "$body" | grep -q '"status":"UP"' || echo "$body" | grep -q '"UP"'; then
            return 0
        fi
    fi

    return 1
}

# Check readiness endpoint
check_readiness() {
    log "Checking readiness endpoint: ${ENGINE_MGMT_URL}/actuator/health/readiness"

    local response
    response=$(curl -s -w "%{http_code}" "${ENGINE_MGMT_URL}/actuator/health/readiness" 2>/dev/null || echo "000")

    local http_code="${response: -3}"
    local body="${response%???}"

    if [[ "$http_code" -eq 200 ]]; then
        if echo "$body" | grep -q '"status":"UP"' || echo "$body" | grep -q '"UP"'; then
            return 0
        fi
    fi

    return 1
}

# Authenticate with YAWL engine
authenticate() {
    log "Authenticating with YAWL engine..."

    local response
    response=$(curl -s -X POST "${ENGINE_URL}/yawl/ib?action=connect&userid=${CONNECTION_ID}&password=YAWL")

    if echo "$response" | grep -q 'connectionID'; then
        # Extract connection ID
        local connection_id=$(echo "$response" | sed 's/.*connectionID:\([^,]*\).*/\1/' | tr -d ' ')
        log "Authentication successful: $connection_id"
        return 0
    else
        log "Authentication response: $response"
        return 1
    fi
}

# Check specification endpoint
check_specification_endpoint() {
    log "Checking specification endpoint..."

    local response
    response=$(curl -s -X POST "${ENGINE_URL}/yawl/ib?action=getSpecificationPrototypesList")

    if echo "$response" | grep -q 'specifications'; then
        return 0
    fi

    return 1
}

# Get engine info
get_engine_info() {
    log "Fetching engine information..."

    local response
    response=$(curl -s "${ENGINE_MGMT_URL}/actuator/info")

    if [[ -n "$response" ]]; then
        echo "$response" | jq '.' 2>/dev/null || echo "$response"
    fi
}

# Wait for engine to be ready
wait_for_engine() {
    log "Waiting for engine to be ready (timeout: ${TIMEOUT}s)..."

    local attempt=1
    while [[ $attempt -le $MAX_RETRIES ]]; do
        log "Attempt $attempt/$MAX_RETRIES"

        # Check port availability first
        if ! check_port 8080; then
            warn "Port 8080 not available"
            sleep 5
            ((attempt++))
            continue
        fi

        # Check liveness
        if check_liveness; then
            # Check readiness
            if check_readiness; then
                # Authenticate
                if authenticate; then
                    # Check specification endpoint
                    if check_specification_endpoint; then
                        success "Engine is fully ready"
                        return 0
                    else
                        warn "Specification endpoint not ready"
                    fi
                else
                    warn "Authentication failed"
                fi
            else
                warn "Readiness check failed"
            fi
        else
            warn "Liveness check failed"
        fi

        sleep 5
        ((attempt++))
    done

    error "Engine did not become ready after ${TIMEOUT}s"
}

# Check MCP-A2A services
check_mcp_a2a() {
    log "Checking MCP-A2A services..."

    # Check MCP endpoint
    if check_port 18081; then
        log "MCP service available on port 18081"
    else
        warn "MCP service not available on port 18081"
    fi

    # Check A2A endpoint
    if check_port 18082; then
        log "A2A service available on port 18082"
    else
        warn "A2A service not available on port 18082"
    fi

    # Check health endpoint
    if check_port 18080; then
        local response
        response=$(curl -s "${ENGINE_MGMT_URL}/actuator/health" 2>/dev/null || echo "")
        if [[ -n "$response" ]]; then
            echo "MCP-A2A Health: $response"
        fi
    fi
}

# Main execution
main() {
    log "=== YAWL Engine Health Check ==="

    # Check if required tools are available
    if ! command -v curl &> /dev/null; then
        error "curl is required but not installed"
    fi

    if ! command -v nc &> /dev/null; then
        error "netcat is required but not installed"
    fi

    if ! command -v jq &> /dev/null; then
        warn "jq not found - will use plain text output"
    fi

    # Get engine info
    get_engine_info
    echo ""

    # Check main engine
    if wait_for_engine; then
        log ""
        # Check additional services if available
        if check_port 18080; then
            check_mcp_a2a
        fi
        echo ""
        success "All services are ready for pattern validation"
        exit 0
    else
        error "Engine health check failed"
    fi
}

# Run main
main "$@"