#!/bin/bash
#
# A2A Integration Test Script
#
# Tests the YAWL A2A Server and Client implementation.
# Usage: ./test-a2a-integration.sh [options]
#
# Options:
#   --skip-server    Skip server startup (assumes server is already running)
#   --port PORT      Use custom port (default: 18082)
#   --verbose        Enable verbose output
#   --help           Show this help
#
# Requirements:
#   - YAWL compiled (ant compile)
#   - YAWL engine running (optional, for full integration tests)
#   - Environment variables set for YAWL engine (optional)
#

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel 2>/dev/null || echo "$SCRIPT_DIR")"
BUILD_DIR="$PROJECT_DIR/build"
CLASSES_DIR="$PROJECT_DIR/classes"
LIB_DIR="$PROJECT_DIR/build/3rdParty/lib"

# Test configuration
A2A_PORT="${A2A_PORT:-18082}"
A2A_HOST="${A2A_HOST:-localhost}"
A2A_URL="http://$A2A_HOST:$A2A_PORT"
SKIP_SERVER=false
VERBOSE=false
SERVER_PID=""
TESTS_PASSED=0
TESTS_FAILED=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

log_failure() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_section() {
    echo ""
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "  $1"
    fi
}

# Show help
show_help() {
    head -20 "$0" | tail -18 | sed 's/^#//'
    exit 0
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --skip-server)
                SKIP_SERVER=true
                shift
                ;;
            --port)
                A2A_PORT="$2"
                A2A_URL="http://$A2A_HOST:$A2A_PORT"
                shift 2
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            --help|-h)
                show_help
                ;;
            *)
                log_warning "Unknown option: $1"
                shift
                ;;
        esac
    done
}

# Check prerequisites
check_prerequisites() {
    log_section "Checking Prerequisites"

    # Check if classes directory exists
    if [ ! -d "$CLASSES_DIR" ]; then
        log_failure "Classes directory not found: $CLASSES_DIR"
        log_info "Run 'ant compile' first to build the project"
        exit 1
    fi
    log_success "Classes directory found"

    # Check if A2A classes exist
    if [ ! -f "$CLASSES_DIR/org/yawlfoundation/yawl/integration/a2a/YawlA2AServer.class" ]; then
        log_failure "A2A Server class not compiled"
        log_info "Run 'ant compile' first"
        exit 1
    fi
    log_success "A2A classes compiled"

    # Check if libraries exist
    if [ ! -d "$LIB_DIR" ]; then
        log_warning "Library directory not found: $LIB_DIR"
        log_warning "Some tests may fail if required dependencies are missing"
    else
        log_success "Library directory found"
    fi

    # Check curl for HTTP tests
    if ! command -v curl &> /dev/null; then
        log_warning "curl not found - HTTP tests will be skipped"
        HAS_CURL=false
    else
        log_success "curl available"
        HAS_CURL=true
    fi

    # Check Java
    if ! command -v java &> /dev/null; then
        log_failure "Java not found"
        exit 1
    fi
    log_success "Java available: $(java -version 2>&1 | head -1)"
}

# Build classpath
build_classpath() {
    local cp="$CLASSES_DIR"

    if [ -d "$LIB_DIR" ]; then
        for jar in "$LIB_DIR"/*.jar; do
            if [ -f "$jar" ]; then
                cp="$cp:$jar"
            fi
        done
    fi

    echo "$cp"
}

# Start A2A server
start_server() {
    if [ "$SKIP_SERVER" = true ]; then
        log_info "Skipping server startup (--skip-server)"
        return 0
    fi

    log_section "Starting A2A Server"

    local CLASSPATH=$(build_classpath)

    # Check if port is available
    if lsof -i :$A2A_PORT &> /dev/null; then
        log_failure "Port $A2A_PORT is already in use"
        log_info "Stop the existing process or use --port to specify a different port"
        exit 1
    fi

    log_info "Starting server on port $A2A_PORT..."

    # Set environment variables for the server
    export A2A_SERVER_PORT=$A2A_PORT
    export A2A_SERVER_URL=$A2A_URL

    # Start server in background
    java -cp "$CLASSPATH" \
         -DA2A_SERVER_PORT=$A2A_PORT \
         -DA2A_SERVER_URL=$A2A_URL \
         org.yawlfoundation.yawl.integration.a2a.YawlA2AServer \
         $A2A_PORT \
         > /tmp/a2a-server-$$.log 2>&1 &

    SERVER_PID=$!
    log_verbose "Server PID: $SERVER_PID"

    # Wait for server to start
    log_info "Waiting for server to start..."
    local max_wait=30
    local waited=0

    while [ $waited -lt $max_wait ]; do
        if curl -s "$A2A_URL/health" > /dev/null 2>&1; then
            log_success "Server started successfully"
            return 0
        fi

        # Check if process is still running
        if ! kill -0 $SERVER_PID 2>/dev/null; then
            log_failure "Server process died"
            log_info "Server log:"
            cat /tmp/a2a-server-$$.log
            exit 1
        fi

        sleep 1
        ((waited++))
        log_verbose "Waiting... ($waited/$max_wait)"
    done

    log_failure "Server failed to start within ${max_wait}s"
    log_info "Server log:"
    cat /tmp/a2a-server-$$.log
    exit 1
}

# Stop A2A server
stop_server() {
    if [ -z "$SERVER_PID" ] || [ "$SKIP_SERVER" = true ]; then
        return 0
    fi

    log_section "Stopping A2A Server"

    if kill -0 $SERVER_PID 2>/dev/null; then
        log_info "Stopping server (PID: $SERVER_PID)..."
        kill $SERVER_PID 2>/dev/null || true

        # Wait for graceful shutdown
        local waited=0
        while [ $waited -lt 10 ]; do
            if ! kill -0 $SERVER_PID 2>/dev/null; then
                break
            fi
            sleep 1
            ((waited++))
        done

        # Force kill if still running
        if kill -0 $SERVER_PID 2>/dev/null; then
            log_warning "Force killing server..."
            kill -9 $SERVER_PID 2>/dev/null || true
        fi

        log_success "Server stopped"
    else
        log_info "Server already stopped"
    fi

    # Cleanup log file
    rm -f /tmp/a2a-server-$$.log
}

# Test HTTP endpoints
test_http_endpoints() {
    log_section "Testing HTTP Endpoints"

    if [ "$HAS_CURL" = false ]; then
        log_warning "Skipping HTTP tests (curl not available)"
        return 0
    fi

    # Test health endpoint
    log_info "Testing /health endpoint..."
    local health_response
    health_response=$(curl -s -w "\n%{http_code}" "$A2A_URL/health" 2>/dev/null)
    local health_code=$(echo "$health_response" | tail -1)
    local health_body=$(echo "$health_response" | head -n -1)

    if [ "$health_code" = "200" ]; then
        log_success "Health endpoint returned 200"
        log_verbose "Response: $health_body"
    else
        log_failure "Health endpoint returned $health_code"
    fi

    # Test AgentCard endpoint
    log_info "Testing /.well-known/agent.json endpoint..."
    local card_response
    card_response=$(curl -s -w "\n%{http_code}" "$A2A_URL/.well-known/agent.json" 2>/dev/null)
    local card_code=$(echo "$card_response" | tail -1)
    local card_body=$(echo "$card_response" | head -n -1)

    if [ "$card_code" = "200" ]; then
        log_success "AgentCard endpoint returned 200"

        # Validate JSON structure
        if echo "$card_body" | grep -q '"name"' && echo "$card_body" | grep -q '"skills"'; then
            log_success "AgentCard contains required fields"
        else
            log_failure "AgentCard missing required fields"
        fi

        log_verbose "Response (truncated): $(echo "$card_body" | head -c 200)..."
    else
        log_failure "AgentCard endpoint returned $card_code"
    fi

    # Test invalid endpoint
    log_info "Testing invalid endpoint..."
    local invalid_response
    invalid_response=$(curl -s -w "\n%{http_code}" "$A2A_URL/invalid" 2>/dev/null)
    local invalid_code=$(echo "$invalid_response" | tail -1)

    if [ "$invalid_code" = "404" ] || [ "$invalid_code" = "500" ]; then
        log_success "Invalid endpoint handled correctly"
    else
        log_warning "Unexpected response code for invalid endpoint: $invalid_code"
    fi
}

# Test A2A client
test_a2a_client() {
    log_section "Testing A2A Client"

    local CLASSPATH=$(build_classpath)

    # Test 1: Client connection and AgentCard retrieval
    log_info "Test: Client connection and AgentCard retrieval..."

    local client_output
    client_output=$(java -cp "$CLASSPATH" org.yawlfoundation.yawl.integration.a2a.YawlA2AClient "$A2A_URL" 2>&1) || true

    if echo "$client_output" | grep -q "Successfully connected"; then
        log_success "Client connected to server"
    else
        log_failure "Client failed to connect"
        log_verbose "Output: $client_output"
        return 1
    fi

    if echo "$client_output" | grep -q "Skills:"; then
        log_success "Client retrieved skills from AgentCard"
    else
        log_failure "Client did not retrieve skills"
    fi

    log_verbose "Client output (first 500 chars):"
    log_verbose "$(echo "$client_output" | head -c 500)"
}

# Test skill invocation
test_skill_invocation() {
    log_section "Testing Skill Invocation"

    if [ "$HAS_CURL" = false ]; then
        log_warning "Skipping skill invocation tests (curl not available)"
        return 0
    fi

    # Test JSON-RPC request
    log_info "Testing JSON-RPC skill invocation..."

    local request='{"jsonrpc":"2.0","method":"agent/card","params":{},"id":"test-1"}'
    local response
    response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$request" \
        "$A2A_URL/a2a" 2>/dev/null)

    if [ -n "$response" ]; then
        log_success "JSON-RPC request received response"

        if echo "$response" | grep -q '"result"'; then
            log_success "Response contains result"
        elif echo "$response" | grep -q '"error"'; then
            log_warning "Response contains error: $(echo "$response" | grep -o '"error":[^}]*')"
        else
            log_warning "Unexpected response format"
        fi

        log_verbose "Response: $(echo "$response" | head -c 300)"
    else
        log_failure "No response from JSON-RPC endpoint"
    fi

    # Test invalid JSON-RPC request
    log_info "Testing invalid JSON-RPC request..."
    local invalid_request='{"jsonrpc":"2.0","method":"invalid/method","params":{},"id":"test-2"}'
    local invalid_response
    invalid_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$invalid_request" \
        "$A2A_URL/a2a" 2>/dev/null)

    if [ -n "$invalid_response" ]; then
        log_success "Invalid request handled (received response)"
        log_verbose "Response: $invalid_response"
    else
        log_warning "No response for invalid request"
    fi
}

# Test error handling
test_error_handling() {
    log_section "Testing Error Handling"

    if [ "$HAS_CURL" = false ]; then
        log_warning "Skipping error handling tests (curl not available)"
        return 0
    fi

    # Test method not allowed
    log_info "Testing method not allowed..."
    local method_response
    method_response=$(curl -s -w "\n%{http_code}" -X DELETE "$A2A_URL/.well-known/agent.json" 2>/dev/null)
    local method_code=$(echo "$method_response" | tail -1)

    if [ "$method_code" = "405" ]; then
        log_success "DELETE method correctly rejected (405)"
    else
        log_warning "Unexpected response for DELETE: $method_code"
    fi

    # Test malformed JSON
    log_info "Testing malformed JSON..."
    local malformed_response
    malformed_response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "not valid json" \
        "$A2A_URL/a2a" 2>/dev/null)

    if [ -n "$malformed_response" ]; then
        if echo "$malformed_response" | grep -q "error"; then
            log_success "Malformed JSON handled with error response"
        else
            log_warning "Malformed JSON response: $malformed_response"
        fi
    fi
}

# Print test summary
print_summary() {
    log_section "Test Summary"

    local total=$((TESTS_PASSED + TESTS_FAILED))

    echo ""
    echo "  Total tests:  $total"
    echo -e "  ${GREEN}Passed:       $TESTS_PASSED${NC}"
    echo -e "  ${RED}Failed:       $TESTS_FAILED${NC}"
    echo ""

    if [ $TESTS_FAILED -eq 0 ]; then
        echo -e "${GREEN}All tests passed!${NC}"
        return 0
    else
        echo -e "${RED}Some tests failed.${RED}"
        return 1
    fi
}

# Cleanup on exit
cleanup() {
    stop_server
}

# Main function
main() {
    parse_args "$@"

    trap cleanup EXIT

    echo ""
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║             YAWL A2A Integration Test Suite                  ║"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "Configuration:"
    echo "  A2A URL:    $A2A_URL"
    echo "  Port:       $A2A_PORT"
    echo "  Skip Server: $SKIP_SERVER"
    echo "  Verbose:    $VERBOSE"
    echo ""

    check_prerequisites
    start_server

    test_http_endpoints
    test_a2a_client
    test_skill_invocation
    test_error_handling

    print_summary
}

# Run main
main "$@"
