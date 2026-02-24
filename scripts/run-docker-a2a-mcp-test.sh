#!/usr/bin/env bash
# =============================================================================
# run-docker-a2a-mcp-test.sh — Docker A2A/MCP/Handoff Test Runner
# =============================================================================
#
# Comprehensive test runner for A2A, MCP, and Handoff integration tests.
# Builds Docker images, starts services, runs tests, and collects results.
#
# Usage:
#   bash scripts/run-docker-a2a-mcp-test.sh [options]
#
# Options:
#   --build        Force rebuild of Docker images
#   --no-clean     Don't cleanup after tests (useful for debugging)
#   --verbose      Show detailed output (container logs, health check details)
#   --ci           CI mode (non-interactive, structured output)
#   --timeout=N    Health check timeout in seconds (default: 180)
#   --help         Show this help message
#
# Exit Codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Configuration or setup error
#   3 - Service unavailable / health check timeout
#   4 - Docker not available
#
# Environment Variables:
#   YAWL_ENGINE_URL       YAWL engine URL (default: http://localhost:8080)
#   YAWL_MCP_URL          MCP server URL (default: http://localhost:18081/mcp)
#   YAWL_A2A_URL          A2A server URL (default: http://localhost:18082/a2a)
#   TEST_RESULTS_DIR      Directory for test results (default: test-results/)
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="docker-compose.a2a-mcp-test.yml"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Defaults
FORCE_BUILD=false
NO_CLEAN=false
VERBOSE=false
CI_MODE=false
HEALTH_TIMEOUT=180

# Test results directory
TEST_RESULTS_DIR="${TEST_RESULTS_DIR:-${PROJECT_ROOT}/test-results}"
RUN_RESULTS_DIR="${TEST_RESULTS_DIR}/run-${TIMESTAMP}"

# Service URLs (host-side ports from docker-compose)
YAWL_ENGINE_URL="${YAWL_ENGINE_URL:-http://localhost:8080}"
YAWL_MCP_URL="${YAWL_MCP_URL:-http://localhost:18081/mcp}"
YAWL_A2A_URL="${YAWL_A2A_URL:-http://localhost:18082/a2a}"

# Exit codes
EXIT_SUCCESS=0
EXIT_TEST_FAILED=1
EXIT_CONFIG_ERROR=2
EXIT_SERVICE_UNAVAILABLE=3
EXIT_DOCKER_ERROR=4

# Colors (disabled in CI mode)
if [[ -t 1 ]] && [[ "$CI_MODE" != true ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    BOLD=''
    NC=''
fi

# Logging functions
log_info() {
    if [[ "$CI_MODE" == true ]]; then
        echo "[INFO] $1"
    else
        echo -e "${BLUE}[INFO]${NC} $1"
    fi
}

log_success() {
    if [[ "$CI_MODE" == true ]]; then
        echo "[PASS] $1"
    else
        echo -e "${GREEN}[PASS]${NC} $1"
    fi
}

log_error() {
    if [[ "$CI_MODE" == true ]]; then
        echo "[ERROR] $1" >&2
    else
        echo -e "${RED}[ERROR]${NC} $1" >&2
    fi
}

log_warn() {
    if [[ "$CI_MODE" == true ]]; then
        echo "[WARN] $1"
    else
        echo -e "${YELLOW}[WARN]${NC} $1"
    fi
}

log_verbose() {
    if [[ "$VERBOSE" == true ]]; then
        if [[ "$CI_MODE" == true ]]; then
            echo "[VERBOSE] $1"
        else
            echo -e "${CYAN}[VERBOSE]${NC} $1"
        fi
    fi
}

log_section() {
    echo
    if [[ "$CI_MODE" == true ]]; then
        echo "=== $1 ==="
    else
        echo -e "\n${BOLD}${BLUE}=== $1 ===${NC}\n"
    fi
}

# Show help message
show_help() {
    cat << 'HELP_EOF'
YAWL A2A/MCP/Handoff Docker Test Runner
=======================================

Comprehensive test runner for A2A, MCP, and Handoff integration tests.
Builds Docker images, starts services, runs tests, and collects results.

Usage:
  bash scripts/run-docker-a2a-mcp-test.sh [options]

Options:
  --build        Force rebuild of Docker images
  --no-clean     Don't cleanup after tests (useful for debugging)
  --verbose      Show detailed output (container logs, health check details)
  --ci           CI mode (non-interactive, structured output)
  --timeout=N    Health check timeout in seconds (default: 180)
  --help         Show this help message

Exit Codes:
  0 - All tests passed
  1 - One or more tests failed
  2 - Configuration or setup error
  3 - Service unavailable / health check timeout
  4 - Docker not available

Environment Variables:
  YAWL_ENGINE_URL       YAWL engine URL (default: http://localhost:8080)
  YAWL_MCP_URL          MCP server URL (default: http://localhost:18081/mcp)
  YAWL_A2A_URL          A2A server URL (default: http://localhost:18082/a2a)
  TEST_RESULTS_DIR      Directory for test results (default: test-results/)
HELP_EOF
}

# Parse command line arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --build) FORCE_BUILD=true ;;
            --no-clean) NO_CLEAN=true ;;
            --verbose) VERBOSE=true ;;
            --ci) CI_MODE=true ;;
            --timeout=*) HEALTH_TIMEOUT="${1#*=}" ;;
            --help) show_help; exit 0 ;;
            *) log_error "Unknown option: $1"; show_help; exit $EXIT_CONFIG_ERROR ;;
        esac
        shift
    done

    # Propagate CI mode to environment
    if [[ "$CI_MODE" == true ]]; then
        export CI=true
    fi
}

# Check Docker availability
check_docker() {
    log_section "Checking Prerequisites"

    if ! command -v docker &> /dev/null; then
        log_error "Docker is not installed"
        exit $EXIT_DOCKER_ERROR
    fi

    if ! docker info &> /dev/null; then
        log_error "Docker daemon is not running or not accessible"
        exit $EXIT_DOCKER_ERROR
    fi

    if ! command -v docker &> /dev/null || ! docker compose version &> /dev/null; then
        log_error "Docker Compose is not available"
        exit $EXIT_DOCKER_ERROR
    fi

    log_success "Docker is available"
    log_verbose "Docker version: $(docker --version)"
    log_verbose "Docker Compose version: $(docker compose version --short)"
}

# Check compose file exists
check_compose_file() {
    if [[ ! -f "${PROJECT_ROOT}/${COMPOSE_FILE}" ]]; then
        log_error "Docker Compose file not found: ${COMPOSE_FILE}"
        log_error "Please ensure the file exists in the project root"
        exit $EXIT_CONFIG_ERROR
    fi
    log_verbose "Found compose file: ${COMPOSE_FILE}"
}

# Ensure network exists
ensure_network() {
    log_info "Ensuring Docker network exists..."
    if docker network inspect yawl-network >/dev/null 2>&1; then
        log_verbose "Network yawl-network already exists"
    else
        docker network create yawl-network
        log_success "Created Docker network: yawl-network"
    fi
}

# Build images if needed
build_images() {
    log_section "Building Docker Images"

    local build_needed=false

    # Check if engine image exists
    if [[ "$FORCE_BUILD" == true ]]; then
        log_info "Force rebuild requested"
        build_needed=true
    elif ! docker image inspect yawl-engine:6.0.0-alpha >/dev/null 2>&1; then
        log_info "yawl-engine:6.0.0-alpha image not found"
        build_needed=true
    else
        log_verbose "yawl-engine:6.0.0-alpha image exists"
    fi

    # Check if MCP-A2A image exists
    if ! docker image inspect yawl-mcp-a2a:6.0.0-alpha >/dev/null 2>&1; then
        log_info "yawl-mcp-a2a:6.0.0-alpha image not found"
        build_needed=true
    else
        log_verbose "yawl-mcp-a2a:6.0.0-alpha image exists"
    fi

    if [[ "$build_needed" == true ]] || [[ "$FORCE_BUILD" == true ]]; then
        # Build engine image
        if [[ "$FORCE_BUILD" == true ]] || ! docker image inspect yawl-engine:6.0.0-alpha >/dev/null 2>&1; then
            log_info "Building yawl-engine image..."
            if [[ "$VERBOSE" == true ]]; then
                docker build -f docker/production/Dockerfile.engine -t yawl-engine:6.0.0-alpha . 2>&1 | tee -a "${RUN_RESULTS_DIR}/engine-build.log"
            else
                docker build -f docker/production/Dockerfile.engine -t yawl-engine:6.0.0-alpha . 2>&1 | tee -a "${RUN_RESULTS_DIR}/engine-build.log" | grep -E "^(Step|Successfully|ERROR|FAILED)" || true
            fi
            log_success "yawl-engine image built"
        fi

        # Build MCP-A2A image
        if [[ "$FORCE_BUILD" == true ]] || ! docker image inspect yawl-mcp-a2a:6.0.0-alpha >/dev/null 2>&1; then
            log_info "Building yawl-mcp-a2a image..."
            if [[ "$VERBOSE" == true ]]; then
                docker build -f docker/production/Dockerfile.mcp-a2a-app -t yawl-mcp-a2a:6.0.0-alpha . 2>&1 | tee -a "${RUN_RESULTS_DIR}/mcp-a2a-build.log"
            else
                docker build -f docker/production/Dockerfile.mcp-a2a-app -t yawl-mcp-a2a:6.0.0-alpha . 2>&1 | tee -a "${RUN_RESULTS_DIR}/mcp-a2a-build.log" | grep -E "^(Step|Successfully|ERROR|FAILED)" || true
            fi
            log_success "yawl-mcp-a2a image built"
        fi
    else
        log_info "All images already exist (use --build to force rebuild)"
    fi
}

# Wait for service health with timeout
wait_for_health() {
    local service_name="$1"
    local container_name="$2"
    local health_url="$3"
    local max_wait="$HEALTH_TIMEOUT"
    local interval=5
    local elapsed=0

    log_info "Waiting for $service_name to be healthy (timeout: ${max_wait}s)..."

    while [[ $elapsed -lt $max_wait ]]; do
        # Check container is running
        if ! docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
            log_error "Container $container_name is not running"
            if [[ "$VERBOSE" == true ]]; then
                docker compose -f "$COMPOSE_FILE" --profile test logs "$service_name" 2>&1 | tail -50
            fi
            return $EXIT_SERVICE_UNAVAILABLE
        fi

        # Check health endpoint
        if docker exec "$container_name" curl -sf --max-time 5 "$health_url" >/dev/null 2>&1; then
            log_success "$service_name is healthy"
            return 0
        fi

        log_verbose "Waiting for $service_name... (${elapsed}s/${max_wait}s)"
        sleep $interval
        elapsed=$((elapsed + interval))
    done

    log_error "$service_name health check timed out after ${max_wait}s"
    if [[ "$VERBOSE" == true ]]; then
        log_info "Container logs:"
        docker compose -f "$COMPOSE_FILE" --profile test logs "$service_name" 2>&1 | tail -100
    fi
    return $EXIT_SERVICE_UNAVAILABLE
}

# Run tests
run_tests() {
    log_section "Starting Test Services"

    # Create results directory
    mkdir -p "$RUN_RESULTS_DIR"
    log_info "Test results directory: $RUN_RESULTS_DIR"

    # Start MCP-A2A (no engine needed for this test)
    log_info "Starting MCP-A2A service..."
    docker compose -f "$COMPOSE_FILE" --profile test up -d yawl-mcp-a2a

    # Wait for MCP-A2A health
    wait_for_health "yawl-mcp-a2a" "yawl-mcp-a2a" "http://localhost:18080/actuator/health/liveness"
    local mcp_result=$?
    if [[ $mcp_result -ne 0 ]]; then
        return $mcp_result
    fi

    log_section "Running Tests"

    # Run test runner container
    log_info "Executing test suite..."
    local test_exit_code=0

    if [[ "$VERBOSE" == true ]]; then
        # Show live output
        docker compose -f "$COMPOSE_FILE" --profile test up --abort-on-container-exit test-runner 2>&1 | tee "${RUN_RESULTS_DIR}/test-output.log" || test_exit_code=$?
    else
        # Capture output, show summary
        docker compose -f "$COMPOSE_FILE" --profile test up --abort-on-container-exit test-runner 2>&1 | tee "${RUN_RESULTS_DIR}/test-output.log" | grep -E "^\[(INFO|PASS|FAIL|ERROR|TEST|SKIP)\]" || true
        test_exit_code=${PIPESTATUS[0]}
    fi

    # Collect results from container
    collect_results

    return $test_exit_code
}

# Collect test results
collect_results() {
    log_section "Collecting Results"

    # Copy results from test-runner container volume
    if docker ps -a --format '{{.Names}}' | grep -q "^yawl-test-runner$"; then
        # Copy from container if it has results
        docker cp yawl-test-runner:/results/. "$RUN_RESULTS_DIR/" 2>/dev/null || log_verbose "No results to copy from container"
    fi

    # Copy container logs
    log_info "Saving container logs..."
    docker compose -f "$COMPOSE_FILE" --profile test logs yawl-mcp-a2a > "${RUN_RESULTS_DIR}/mcp-a2a.log" 2>&1 || true
    docker compose -f "$COMPOSE_FILE" --profile test logs test-runner > "${RUN_RESULTS_DIR}/test-runner.log" 2>&1 || true

    # Generate summary
    generate_summary

    log_success "Results saved to: $RUN_RESULTS_DIR"
}

# Generate test summary
generate_summary() {
    local summary_file="${RUN_RESULTS_DIR}/summary.txt"

    cat > "$summary_file" << SUMMARY_EOF
================================================================================
YAWL A2A/MCP/Handoff Test Run Summary
================================================================================

Timestamp: $(date -Iseconds)
Run ID: ${TIMESTAMP}

Configuration:
  Compose File: ${COMPOSE_FILE}
  Engine URL: ${YAWL_ENGINE_URL}
  MCP URL: ${YAWL_MCP_URL}
  A2A URL: ${YAWL_A2A_URL}
  Health Timeout: ${HEALTH_TIMEOUT}s
  CI Mode: ${CI_MODE}
  Verbose: ${VERBOSE}

Results Directory: ${RUN_RESULTS_DIR}

Files Generated:
  - test-output.log    : Full test execution output
  - engine.log         : YAWL engine container logs
  - mcp-a2a.log        : MCP-A2A container logs
  - test-runner.log    : Test runner container logs
  - summary.txt        : This summary file

================================================================================
SUMMARY_EOF

    # Check for test report JSON
    if [[ -f "${RUN_RESULTS_DIR}/test_report.json" ]]; then
        log_info "Found test report: test_report.json"
        echo "" >> "$summary_file"
        echo "Test Report Summary:" >> "$summary_file"
        if command -v jq &> /dev/null; then
            jq -r '.summary | "  Total: \(.total), Passed: \(.passed), Failed: \(.failed), Skipped: \(.skipped)"' "${RUN_RESULTS_DIR}/test_report.json" >> "$summary_file" 2>/dev/null || true
        fi
    fi

    log_verbose "Summary saved to: $summary_file"
}

# Cleanup
cleanup() {
    local exit_code=$?

    if [[ "$NO_CLEAN" == true ]]; then
        log_info "Skipping cleanup (--no-clean)"
        log_info "Containers still running. To clean up manually:"
        log_info "  docker compose -f $COMPOSE_FILE --profile test down -v"
        return
    fi

    log_info "Cleaning up..."
    docker compose -f "$COMPOSE_FILE" --profile test down -v 2>/dev/null || true
    log_verbose "Cleanup complete"
}

# Display final results
display_results() {
    local exit_code=$1

    log_section "Test Results"

    if [[ $exit_code -eq 0 ]]; then
        log_success "All tests passed!"
    else
        log_error "Tests failed with exit code: $exit_code"
    fi

    echo
    log_info "Results available at: $RUN_RESULTS_DIR"

    if [[ -f "${RUN_RESULTS_DIR}/summary.txt" ]]; then
        cat "${RUN_RESULTS_DIR}/summary.txt"
    fi
}

# Main
main() {
    # Parse arguments first (handles --help)
    parse_args "$@"

    # Setup cleanup trap
    trap cleanup EXIT

    cd "$PROJECT_ROOT"

    log_section "YAWL A2A/MCP/Handoff Docker Test Runner"
    echo "Timestamp: $(date)"
    echo "Project Root: $PROJECT_ROOT"
    echo "Results Directory: $RUN_RESULTS_DIR"
    echo

    # Prerequisite checks
    check_docker
    check_compose_file

    # Setup
    ensure_network

    # Create results directory early for build logs
    mkdir -p "$RUN_RESULTS_DIR"

    # Build images
    build_images

    # Run tests
    run_tests
    local test_result=$?

    # Display results
    display_results $test_result

    exit $test_result
}

main "$@"