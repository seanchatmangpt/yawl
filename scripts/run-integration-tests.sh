#!/bin/bash
#
# YAWL MCP Server Integration Test Runner
#
# Starts the MCP server, runs all tests, then stops the server.
# This is the main entry point for CI/CD testing.
#
# Usage: ./run-integration-tests.sh
#
# Environment Variables:
#   YAWL_ENGINE_URL  - YAWL engine URL
#   YAWL_USERNAME    - YAWL username
#   YAWL_PASSWORD    - YAWL password
#   MCP_PORT         - MCP server port (default: 3000)
#   SKIP_SERVER      - Skip server start if already running (default: false)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# Configuration
export MCP_PORT="${MCP_PORT:-3000}"
export MCP_SERVER_URL="http://localhost:${MCP_PORT}"
SKIP_SERVER="${SKIP_SERVER:-false}"
SERVER_PID=""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log() {
    echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $1"
}

log_error() {
    echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[$(date '+%H:%M:%S')] !${NC} $1"
}

# Cleanup function
cleanup() {
    if [ -n "$SERVER_PID" ] && kill -0 "$SERVER_PID" 2>/dev/null; then
        log "Stopping MCP server (PID: $SERVER_PID)..."
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
}

trap cleanup EXIT

# Check prerequisites
check_prerequisites() {
    log "Checking prerequisites..."

    # Check Java
    if ! command -v java &> /dev/null; then
        log_error "Java not found"
        exit 1
    fi
    log_success "Java found: $(java -version 2>&1 | head -1)"

    # Check curl
    if ! command -v curl &> /dev/null; then
        log_error "curl not found"
        exit 1
    fi
    log_success "curl found"

    # Check if YAWL engine is reachable
    local engine_url="${YAWL_ENGINE_URL:-http://localhost:8080/yawl/ib}"
    log "Checking YAWL engine at $engine_url..."

    if curl -s --connect-timeout 5 "$engine_url" > /dev/null 2>&1; then
        log_success "YAWL engine reachable"
    else
        log_warn "YAWL engine not reachable at $engine_url"
        log_warn "Tests may fail if engine is required"
    fi
}

# Start MCP server
start_server() {
    if [ "$SKIP_SERVER" = "true" ]; then
        log "SKIP_SERVER=true, checking if server is already running..."

        if curl -s --connect-timeout 2 "$MCP_SERVER_URL" > /dev/null 2>&1; then
            log_success "Server already running at $MCP_SERVER_URL"
            return 0
        else
            log_error "Server not running at $MCP_SERVER_URL"
            exit 1
        fi
    fi

    log "Starting MCP server on port $MCP_PORT..."

    # Start server in background
    "$SCRIPT_DIR/start-mcp-server.sh" "$MCP_PORT" &
    SERVER_PID=$!

    log "Server PID: $SERVER_PID"

    # Wait for server to be ready
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s --connect-timeout 2 "$MCP_SERVER_URL" > /dev/null 2>&1; then
            log_success "Server ready at $MCP_SERVER_URL"
            return 0
        fi

        log "Waiting for server... (attempt $attempt/$max_attempts)"
        sleep 1
        ((attempt++))
    done

    log_error "Server failed to start within timeout"
    exit 1
}

# Run HTTP endpoint tests
run_http_tests() {
    log "Running HTTP endpoint tests..."
    echo ""

    if "$SCRIPT_DIR/test-mcp-http.sh" "$MCP_SERVER_URL"; then
        log_success "HTTP endpoint tests passed"
        return 0
    else
        log_error "HTTP endpoint tests failed"
        return 1
    fi
}

# Run client tests
run_client_tests() {
    log "Running MCP client tests..."
    echo ""

    if "$SCRIPT_DIR/test-mcp-client.sh" "$MCP_SERVER_URL"; then
        log_success "MCP client tests passed"
        return 0
    else
        log_error "MCP client tests failed"
        return 1
    fi
}

# Print summary
print_summary() {
    local http_result=$1
    local client_result=$2

    echo ""
    echo "==========================================="
    echo "Integration Test Summary"
    echo "==========================================="
    echo ""

    if [ $http_result -eq 0 ]; then
        log_success "HTTP Tests: PASSED"
    else
        log_error "HTTP Tests: FAILED"
    fi

    if [ $client_result -eq 0 ]; then
        log_success "Client Tests: PASSED"
    else
        log_error "Client Tests: FAILED"
    fi

    echo ""
    echo "==========================================="

    if [ $http_result -eq 0 ] && [ $client_result -eq 0 ]; then
        log_success "ALL TESTS PASSED"
        echo "==========================================="
        return 0
    else
        log_error "SOME TESTS FAILED"
        echo "==========================================="
        return 1
    fi
}

# Main
main() {
    echo ""
    echo "==========================================="
    echo "YAWL MCP Server Integration Tests"
    echo "==========================================="
    echo ""
    echo "Configuration:"
    echo "  MCP Port:     $MCP_PORT"
    echo "  MCP URL:      $MCP_SERVER_URL"
    echo "  YAWL Engine:  ${YAWL_ENGINE_URL:-http://localhost:8080/yawl/ib}"
    echo "  Skip Server:  $SKIP_SERVER"
    echo ""
    echo "==========================================="
    echo ""

    check_prerequisites
    echo ""

    start_server
    echo ""

    # Run tests
    run_http_tests
    HTTP_RESULT=$?

    echo ""

    run_client_tests
    CLIENT_RESULT=$?

    echo ""

    # Print summary
    print_summary $HTTP_RESULT $CLIENT_RESULT
}

main "$@"
