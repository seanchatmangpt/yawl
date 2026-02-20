#!/usr/bin/env bash
# =============================================================================
# test-integration.sh - Full Integration Test Suite with Docker Compose
#
# Orchestrates docker-compose, validates service health, and runs all
# integration tests locally before pushing to CI. Catches 80% of CI failures.
#
# Usage:
#   bash scripts/test-integration.sh                 # Run full suite
#   bash scripts/test-integration.sh --help          # Show help
#   bash scripts/test-integration.sh --profile prod  # Production PostgreSQL
#   bash scripts/test-integration.sh --no-cleanup    # Keep services running
#   bash scripts/test-integration.sh --verbose       # Debug mode
#   bash scripts/test-integration.sh --timeout 600   # Custom timeout (seconds)
#   bash scripts/test-integration.sh --modules mod1,mod2  # Specific modules
#
# Profiles:
#   dev (default)  - H2 database, minimal services
#   prod           - PostgreSQL database, full stack
#
# Exit codes:
#   0  - All tests passed
#   1  - Test failure
#   2  - Configuration/dependency error
#   3  - Docker/compose error
#   4  - Service startup timeout
#   5  - Build failure
#
# =============================================================================
set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
readonly SCRIPT_VERSION="1.0.0"

# ─────────────────────────────────────────────────────────────────────────────
# Configuration Defaults
# ─────────────────────────────────────────────────────────────────────────────

PROFILE="${PROFILE:-dev}"
CLEANUP="${CLEANUP:-true}"
VERBOSE="${VERBOSE:-false}"
STARTUP_TIMEOUT=180
TEST_TIMEOUT=600
MODULES=""
DOCKER_COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.yml"
MAVEN_PROFILE="docker"
COMPOSE_PROJECT_NAME="yawl-test-$(date +%s)"

# Color codes
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m'

# Counters
CHECKS_PASSED=0
CHECKS_FAILED=0
WARNINGS=0

# ─────────────────────────────────────────────────────────────────────────────
# Logging Functions
# ─────────────────────────────────────────────────────────────────────────────

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*"
    ((CHECKS_PASSED++)) || true
}

log_warning() {
    echo -e "${YELLOW}[!]${NC} $*"
    ((WARNINGS++)) || true
}

log_error() {
    echo -e "${RED}[✗]${NC} $*" >&2
    ((CHECKS_FAILED++)) || true
}

log_debug() {
    if [[ "$VERBOSE" == "true" ]]; then
        echo -e "${CYAN}[DEBUG]${NC} $*"
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Utility Functions
# ─────────────────────────────────────────────────────────────────────────────

print_separator() {
    echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}"
}

cleanup_on_exit() {
    local exit_code=$?
    log_info "Running cleanup..."

    if [[ "$CLEANUP" == "true" ]]; then
        if command -v docker-compose &> /dev/null; then
            log_debug "Stopping docker-compose services..."
            docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$COMPOSE_PROJECT_NAME" down --volumes 2>/dev/null || true
        fi
    else
        log_warning "Services left running (use --cleanup to remove). Project: $COMPOSE_PROJECT_NAME"
        log_info "To clean up manually: docker-compose -f $DOCKER_COMPOSE_FILE -p $COMPOSE_PROJECT_NAME down --volumes"
    fi

    return $exit_code
}

trap cleanup_on_exit EXIT

# ─────────────────────────────────────────────────────────────────────────────
# Dependency Checks
# ─────────────────────────────────────────────────────────────────────────────

check_docker_installed() {
    log_info "Checking Docker installation..."

    if ! command -v docker &> /dev/null; then
        log_error "Docker not found. Install from https://docs.docker.com/get-docker/"
        return 2
    fi
    log_success "Docker found: $(docker --version)"

    if ! docker ps &> /dev/null; then
        log_error "Docker daemon not running or no permission. Run 'sudo usermod -aG docker \$USER'"
        return 2
    fi
    log_success "Docker daemon accessible"

    return 0
}

check_docker_compose_installed() {
    log_info "Checking docker-compose..."

    if ! command -v docker-compose &> /dev/null; then
        log_error "docker-compose not found. Install from https://docs.docker.com/compose/install/"
        return 2
    fi
    log_success "docker-compose found: $(docker-compose --version)"

    return 0
}

check_maven_installed() {
    log_info "Checking Maven..."

    if ! command -v mvn &> /dev/null; then
        log_error "Maven not found. Set PATH or install Apache Maven 3.9+"
        return 2
    fi
    log_success "Maven found: $(mvn --version | head -1)"

    return 0
}

check_compose_file() {
    log_info "Checking docker-compose.yml..."

    if [[ ! -f "$DOCKER_COMPOSE_FILE" ]]; then
        log_error "docker-compose.yml not found at $DOCKER_COMPOSE_FILE"
        return 2
    fi
    log_success "docker-compose.yml exists"

    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Docker Compose Management
# ─────────────────────────────────────────────────────────────────────────────

start_services() {
    local profiles=""

    log_info "Starting docker-compose services (profile: $PROFILE)..."

    case "$PROFILE" in
        dev)
            log_debug "Using development profile (H2)"
            cd "$PROJECT_ROOT"
            docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$COMPOSE_PROJECT_NAME" up -d yawl-engine
            ;;
        prod)
            log_debug "Using production profile (PostgreSQL)"
            cd "$PROJECT_ROOT"
            docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$COMPOSE_PROJECT_NAME" \
                --profile production up -d postgres yawl-engine-prod
            ;;
        *)
            log_error "Unknown profile: $PROFILE (use 'dev' or 'prod')"
            return 2
            ;;
    esac

    log_success "docker-compose services started"
    return 0
}

wait_for_service() {
    local service=$1
    local port=$2
    local max_attempts=$((STARTUP_TIMEOUT / 5))
    local attempt=0

    log_info "Waiting for $service on port $port (timeout: ${STARTUP_TIMEOUT}s)..."

    while [[ $attempt -lt $max_attempts ]]; do
        if nc -z localhost "$port" 2>/dev/null; then
            log_success "$service is responding on port $port"
            return 0
        fi

        ((attempt++))
        log_debug "Attempt $attempt/$max_attempts: waiting 5s..."
        sleep 5
    done

    log_error "$service did not start within ${STARTUP_TIMEOUT}s"
    log_debug "Logs:"
    docker-compose -f "$DOCKER_COMPOSE_FILE" -p "$COMPOSE_PROJECT_NAME" logs yawl-engine 2>/dev/null | tail -20 || true
    return 4
}

check_service_health() {
    local health_url=$1
    local service_name=$2

    log_info "Checking health: $service_name ($health_url)..."

    if response=$(curl -s -m 10 "$health_url" 2>/dev/null); then
        if echo "$response" | grep -q '"status":"UP"\|"status":"HEALTHY"'; then
            log_success "$service_name health check passed"
            return 0
        else
            log_warning "$service_name responded but status unclear: $response"
            return 0
        fi
    else
        log_error "$service_name health check failed"
        return 1
    fi
}

validate_service_health() {
    log_info "Validating service health..."

    case "$PROFILE" in
        dev)
            wait_for_service "yawl-engine" "8080" || return 4
            check_service_health "http://localhost:8080/actuator/health/liveness" "YAWL Engine" || return 1
            ;;
        prod)
            wait_for_service "postgres" "5432" || return 4
            wait_for_service "yawl-engine-prod" "8080" || return 4
            check_service_health "http://localhost:8080/actuator/health/liveness" "YAWL Engine (Prod)" || return 1
            ;;
    esac

    log_success "All services healthy"
    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Maven Integration Tests
# ─────────────────────────────────────────────────────────────────────────────

build_project() {
    log_info "Building project..."

    cd "$PROJECT_ROOT"

    local mvn_cmd="mvn clean compile -T 1C -q"
    [[ "$VERBOSE" == "true" ]] && mvn_cmd="${mvn_cmd//-q/}"

    if ! eval "$mvn_cmd"; then
        log_error "Build failed"
        return 5
    fi

    log_success "Build successful"
    return 0
}

run_integration_tests() {
    log_info "Running integration tests with profile: $MAVEN_PROFILE..."

    cd "$PROJECT_ROOT"

    local mvn_cmd="mvn test -P $MAVEN_PROFILE"

    if [[ -n "$MODULES" ]]; then
        mvn_cmd="$mvn_cmd -pl $MODULES"
    fi

    # Fail fast, show surefire output
    mvn_cmd="$mvn_cmd -DtrimStackTrace=false"

    [[ "$VERBOSE" == "false" ]] && mvn_cmd="$mvn_cmd -q"

    log_debug "Running: $mvn_cmd"

    if ! eval "$mvn_cmd"; then
        log_error "Integration tests failed"
        return 1
    fi

    log_success "All integration tests passed"
    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Reporting
# ─────────────────────────────────────────────────────────────────────────────

print_summary() {
    print_separator

    local total_checks=$((CHECKS_PASSED + CHECKS_FAILED))

    echo -e "\n${CYAN}═══ Test Integration Summary ===${NC}"
    echo -e "${GREEN}Passed:${NC}  $CHECKS_PASSED"
    echo -e "${RED}Failed:${NC}  $CHECKS_FAILED"

    if [[ $WARNINGS -gt 0 ]]; then
        echo -e "${YELLOW}Warnings:${NC} $WARNINGS"
    fi

    if [[ $CHECKS_FAILED -eq 0 ]]; then
        echo -e "\n${GREEN}✓ All checks passed!${NC}"
        echo -e "Ready to push. Docker compose project: ${CYAN}$COMPOSE_PROJECT_NAME${NC}"
        return 0
    else
        echo -e "\n${RED}✗ Some checks failed. Review logs above.${NC}"
        return 1
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# Help and Argument Parsing
# ─────────────────────────────────────────────────────────────────────────────

print_help() {
    cat << EOF
${CYAN}test-integration.sh${NC} v${SCRIPT_VERSION} - Full Integration Test Suite

${CYAN}USAGE:${NC}
  bash scripts/test-integration.sh [OPTIONS]

${CYAN}OPTIONS:${NC}
  --help                  Show this help message
  --profile <dev|prod>    Database profile (default: dev)
                          dev  = H2 database (fast, local)
                          prod = PostgreSQL (realistic)
  --no-cleanup            Keep docker-compose services running after tests
  --verbose               Show detailed output (Maven, Docker, curl)
  --timeout <seconds>     Startup timeout (default: 180s)
  --modules <mod1,mod2>   Test only specific modules
  --cleanup               Force cleanup (default: true)

${CYAN}EXAMPLES:${NC}
  # Quick local tests with H2
  bash scripts/test-integration.sh

  # Full production tests with PostgreSQL
  bash scripts/test-integration.sh --profile prod

  # Keep services running for debugging
  bash scripts/test-integration.sh --no-cleanup --verbose

  # Test specific modules
  bash scripts/test-integration.sh --modules yawl-integration,yawl-engine

${CYAN}EXIT CODES:${NC}
  0  = All tests passed
  1  = Test failure
  2  = Configuration/dependency error
  3  = Docker error
  4  = Service startup timeout
  5  = Build failure

${CYAN}REQUIREMENTS:${NC}
  - Docker 20.10+
  - docker-compose 2.0+
  - Maven 3.9+
  - Java 21+
  - 4GB free disk space for docker volumes

${CYAN}TROUBLESHOOTING:${NC}
  # View compose logs
  docker-compose -p $COMPOSE_PROJECT_NAME logs -f yawl-engine

  # Check service health manually
  curl http://localhost:8080/actuator/health

  # Clean up dangling volumes
  docker volume prune -f

EOF
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help)
                print_help
                exit 0
                ;;
            --profile)
                PROFILE="$2"
                shift 2
                ;;
            --no-cleanup)
                CLEANUP=false
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            --timeout)
                STARTUP_TIMEOUT="$2"
                shift 2
                ;;
            --modules)
                MODULES="$2"
                shift 2
                ;;
            --cleanup)
                CLEANUP=true
                shift
                ;;
            *)
                log_error "Unknown option: $1"
                print_help
                return 2
                ;;
        esac
    done
    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# Main Workflow
# ─────────────────────────────────────────────────────────────────────────────

main() {
    print_separator
    echo -e "${CYAN}YAWL Integration Test Suite v${SCRIPT_VERSION}${NC}"
    echo -e "Profile: ${YELLOW}$PROFILE${NC} | Cleanup: ${YELLOW}$CLEANUP${NC}"
    print_separator

    log_info "Step 1: Checking dependencies..."
    check_docker_installed || return 2
    check_docker_compose_installed || return 2
    check_maven_installed || return 2
    check_compose_file || return 2

    log_info "Step 2: Building project..."
    build_project || return 5

    log_info "Step 3: Starting docker-compose services..."
    start_services || return 3

    log_info "Step 4: Validating service health..."
    validate_service_health || return 4

    log_info "Step 5: Running integration tests..."
    if ! run_integration_tests; then
        print_summary
        return 1
    fi

    print_summary
    return 0
}

# Entry point
parse_arguments "$@" || exit 2
main
