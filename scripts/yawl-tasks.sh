#!/usr/bin/env bash
# ==========================================================================
# yawl-tasks.sh — Developer Quick Tasks CLI for YAWL v6.0.0
#
# Purpose:
#   One-liner commands for the most common developer workflows.
#   Leverage dx.sh, validate-all.sh, Docker, and Maven for speed.
#
# Usage:
#   ./scripts/yawl-tasks.sh [COMMAND] [OPTIONS]
#
# Commands:
#   help                 Show this help message
#   test                 Run quick unit tests (changed modules)
#   test all             Run ALL unit tests
#   build                Compile changed modules only
#   build all            Compile ALL modules
#   clean                Reset to clean state (git clean, mvn clean)
#   docs                 Build docs + Observatory facts + serve locally
#   validate             Run static analysis (SpotBugs, PMD, Checkstyle)
#   health               Run system health checks
#   coverage             Show coverage dashboard (requires test run first)
#   observatory          Generate code facts, diagrams, and analysis
#   deploy-local         Start local Docker Compose (simple-test)
#   deploy-full          Full Docker stack (prod setup)
#   stop                 Stop running Docker services
#   status               Show current build/test status and recent changes
#   watch                Continuous build loop (compile on file changes)
#   fast                 Fast workflow: compile + test changed modules
#   all                  Full validation: compile + test + analyze + observatory
#
# Examples:
#   ./scripts/yawl-tasks.sh test              # Run tests for changed code
#   ./scripts/yawl-tasks.sh test all          # Run all tests
#   ./scripts/yawl-tasks.sh build             # Compile changed modules
#   ./scripts/yawl-tasks.sh build all         # Compile all modules
#   ./scripts/yawl-tasks.sh clean             # Clean build artifacts
#   ./scripts/yawl-tasks.sh docs              # Build docs + Observatory + serve
#   ./scripts/yawl-tasks.sh validate          # Run static analysis
#   ./scripts/yawl-tasks.sh health            # Check system health
#   ./scripts/yawl-tasks.sh coverage          # Show coverage dashboard
#   ./scripts/yawl-tasks.sh observatory       # Generate code facts/diagrams
#   ./scripts/yawl-tasks.sh deploy-local      # Start test Docker setup
#   ./scripts/yawl-tasks.sh watch             # Continuous build on file changes
#   ./scripts/yawl-tasks.sh fast              # Compile + test changed modules
#   ./scripts/yawl-tasks.sh all               # Full validation pipeline
#
# Environment variables:
#   YAWL_FAST=1                Disable colors, progress output
#   YAWL_OFFLINE=1             Force offline Maven builds
#   YAWL_SKIP_DOCKER_PRUNE=1   Skip Docker image/container cleanup
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# ── Colors & Formatting ───────────────────────────────────────────────
FAST_MODE="${YAWL_FAST:-0}"

if [[ "$FAST_MODE" != "1" ]] && [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    RESET='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    BOLD=''
    RESET=''
fi

# ── Logging Functions ────────────────────────────────────────────────
log_info() {
    echo -e "${BLUE}[INFO]${RESET} $*"
}

log_success() {
    echo -e "${GREEN}[OK]${RESET} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${RESET} $*" >&2
}

log_warn() {
    echo -e "${YELLOW}[WARN]${RESET} $*"
}

log_header() {
    echo ""
    echo -e "${BOLD}${CYAN}== $* ==${RESET}"
    echo ""
}

# ── Utility Functions ────────────────────────────────────────────────
timer_start() {
    echo "$(date +%s%N)"
}

timer_stop() {
    local start=$1
    local end=$(date +%s%N)
    local elapsed_ms=$(( (end - start) / 1000000 ))
    local elapsed_s=$(awk "BEGIN {printf \"%.1f\", $elapsed_ms/1000}")
    echo "$elapsed_s"
}

check_docker() {
    if ! command -v docker >/dev/null 2>&1; then
        log_error "Docker is not installed"
        return 1
    fi
    if ! docker info >/dev/null 2>&1; then
        log_error "Docker is not running. Please start Docker first."
        return 1
    fi
    return 0
}

check_java() {
    if ! command -v java >/dev/null 2>&1; then
        log_error "Java is not installed"
        return 1
    fi
    return 0
}

# ── Command: help ────────────────────────────────────────────────────
cmd_help() {
    sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
    exit 0
}

# ── Command: test ────────────────────────────────────────────────────
cmd_test() {
    local scope="${1:-changed}"
    log_header "Running Unit Tests (scope: $scope)"

    check_java || exit 1

    local timer=$(timer_start)

    if [[ "$scope" == "all" ]]; then
        bash "${SCRIPT_DIR}/dx.sh" test all
    else
        bash "${SCRIPT_DIR}/dx.sh" test
    fi

    local elapsed=$(timer_stop "$timer")
    log_success "Tests completed in ${elapsed}s"
}

# ── Command: build ───────────────────────────────────────────────────
cmd_build() {
    local scope="${1:-changed}"
    log_header "Compiling Modules (scope: $scope)"

    check_java || exit 1

    local timer=$(timer_start)

    if [[ "$scope" == "all" ]]; then
        bash "${SCRIPT_DIR}/dx.sh" compile all
    else
        bash "${SCRIPT_DIR}/dx.sh" compile
    fi

    local elapsed=$(timer_stop "$timer")
    log_success "Build completed in ${elapsed}s"
}

# ── Command: clean ───────────────────────────────────────────────────
cmd_clean() {
    log_header "Cleaning Build Artifacts"

    check_java || exit 1

    log_info "Running: mvn clean"
    mvn clean -q || true

    log_info "Removing: target/, .m2 local builds"
    find "${REPO_ROOT}" -name "target" -type d -exec rm -rf {} + 2>/dev/null || true

    log_success "Clean complete"
}

# ── Command: docs ────────────────────────────────────────────────────
cmd_docs() {
    log_header "Building & Serving Documentation"

    check_java || exit 1

    local docs_dir="${REPO_ROOT}/docs/v6/latest"

    log_info "Generating documentation (Observatory + Javadocs)..."
    local timer=$(timer_start)

    # Generate Observatory facts (code analysis)
    log_info "Running Observatory for code analysis..."
    bash "${SCRIPT_DIR}/observatory/observatory.sh" 2>/dev/null || log_warn "Observatory generation had warnings"

    # Check for mkdocs
    if [[ -f "${REPO_ROOT}/mkdocs.yml" ]] && command -v mkdocs &>/dev/null; then
        log_info "Building mkdocs site..."
        mkdocs build -q || log_warn "mkdocs build had issues"
    fi

    local elapsed=$(timer_stop "$timer")
    log_success "Documentation generated in ${elapsed}s"

    # Serve locally
    if [[ -d "$docs_dir" ]]; then
        log_info "Serving documentation at http://localhost:8000..."
        log_info "  Press Ctrl+C to stop"
        echo ""
        cd "$docs_dir"
        python3 -m http.server 8000 --bind 127.0.0.1
    else
        log_warn "Documentation directory not found at $docs_dir"
        log_info "To view facts, check: docs/v6/latest/INDEX.md"
    fi
}

# ── Command: validate ────────────────────────────────────────────────
cmd_validate() {
    log_header "Running Static Analysis (SpotBugs, PMD, Checkstyle)"

    check_java || exit 1

    local timer=$(timer_start)

    # Use validate-all.sh for comprehensive static analysis
    if [[ -f "${SCRIPT_DIR}/validate-all.sh" ]]; then
        log_info "Running: bash scripts/validate-all.sh --analysis"
        bash "${SCRIPT_DIR}/validate-all.sh" --analysis
    else
        log_warn "validate-all.sh not found, falling back to mvn verify"
        mvn clean verify -P analysis -q -DskipTests || {
            log_error "Static analysis failed"
            exit 1
        }
    fi

    local elapsed=$(timer_stop "$timer")
    log_success "Static analysis completed in ${elapsed}s"

    log_info "Reports available in target/site/"
}

# ── Command: deploy-local ────────────────────────────────────────────
cmd_deploy_local() {
    log_header "Starting Local Docker Stack (simple-test)"

    check_docker || exit 1

    local compose_file="${REPO_ROOT}/docker-compose-simple-test.yml"

    if [[ ! -f "$compose_file" ]]; then
        log_error "docker-compose-simple-test.yml not found at $compose_file"
        exit 1
    fi

    log_info "Pulling latest images..."
    docker compose -f "$compose_file" pull -q || true

    log_info "Starting services..."
    docker compose -f "$compose_file" up -d

    log_info "Waiting for services to be healthy (timeout: 2 min)..."
    timeout 120 bash -c "
        while ! docker compose -f '$compose_file' exec -T yawl-engine curl -sf http://localhost:8080/actuator/health/liveness >/dev/null 2>&1; do
            echo 'Waiting for yawl-engine...'
            sleep 5
        done
    " || {
        log_warn "Service health check timeout (services may still be starting)"
    }

    log_success "Local Docker stack started"
    echo ""
    log_info "Services:"
    docker compose -f "$compose_file" ps
    echo ""
    log_info "To view logs: docker compose -f $compose_file logs -f"
    log_info "To stop:      ./scripts/yawl-tasks.sh stop"
}

# ── Command: deploy-full ─────────────────────────────────────────────
cmd_deploy_full() {
    log_header "Starting Full Docker Stack (production setup)"

    check_docker || exit 1

    local compose_file="${REPO_ROOT}/docker-compose.prod.yml"

    if [[ ! -f "$compose_file" ]]; then
        log_warn "docker-compose.prod.yml not found. Using docker-compose.yml instead"
        compose_file="${REPO_ROOT}/docker-compose.yml"
    fi

    if [[ ! -f "$compose_file" ]]; then
        log_error "No docker-compose file found"
        exit 1
    fi

    log_info "Pulling latest images..."
    docker compose -f "$compose_file" pull -q || true

    log_info "Starting services..."
    docker compose -f "$compose_file" up -d

    log_info "Waiting for services to stabilize (timeout: 3 min)..."
    sleep 10 # Give services time to start

    log_success "Full Docker stack started"
    echo ""
    log_info "Services:"
    docker compose -f "$compose_file" ps
    echo ""
    log_info "To view logs: docker compose -f $compose_file logs -f"
    log_info "To stop:      ./scripts/yawl-tasks.sh stop"
}

# ── Command: stop ────────────────────────────────────────────────────
cmd_stop() {
    log_header "Stopping Docker Services"

    check_docker || exit 1

    # Try each compose file in order
    local compose_files=(
        "docker-compose-simple-test.yml"
        "docker-compose.yml"
        "docker-compose.prod.yml"
    )

    local stopped=0
    for cf in "${compose_files[@]}"; do
        local full_path="${REPO_ROOT}/$cf"
        if [[ -f "$full_path" ]] && docker compose -f "$full_path" ps 2>/dev/null | grep -q "container"; then
            log_info "Stopping stack from: $cf"
            docker compose -f "$full_path" down -v 2>/dev/null || true
            stopped=1
        fi
    done

    if [[ $stopped -eq 0 ]]; then
        log_info "No running services found"
        return
    fi

    log_success "Docker services stopped"

    # Optional cleanup (skip if YAWL_SKIP_DOCKER_PRUNE=1)
    if [[ "${YAWL_SKIP_DOCKER_PRUNE:-0}" != "1" ]]; then
        log_info "Pruning dangling images and containers..."
        docker container prune -f -q 2>/dev/null || true
        docker image prune -f -q 2>/dev/null || true
    fi
}

# ── Command: status ──────────────────────────────────────────────────
cmd_status() {
    log_header "Current Build Status"

    check_java || exit 1

    echo ""
    log_info "Git Status:"
    git status --short || true

    echo ""
    log_info "Recent Commits:"
    git log --oneline -5 || true

    echo ""
    log_info "Changed Modules (uncommitted):"
    bash "${SCRIPT_DIR}/dx-status.sh" 2>/dev/null || log_warn "Status check skipped"

    echo ""
    if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
        log_info "Docker Services:"
        docker compose ps 2>/dev/null || log_info "  No Docker services running"
    fi

    echo ""
    log_info "Maven Modules:"
    mvn modules:list -q 2>/dev/null | head -15 || log_warn "Module list unavailable"
}

# ── Command: watch ──────────────────────────────────────────────────
cmd_watch() {
    log_header "Continuous Build Loop"

    check_java || exit 1

    if ! command -v inotifywait >/dev/null 2>&1; then
        log_error "inotifywait not found (install: apt-get install inotify-tools)"
        exit 1
    fi

    log_info "Watching for changes in src/... (Press Ctrl+C to stop)"
    log_info "Trigger: any .java file change"
    echo ""

    while true; do
        inotifywait -r -e modify \
            "${REPO_ROOT}/yawl-engine/src/" \
            "${REPO_ROOT}/yawl-elements/src/" \
            "${REPO_ROOT}/yawl-stateless/src/" \
            "${REPO_ROOT}/yawl-utilities/src/" \
            2>/dev/null || continue

        echo ""
        log_info "$(date '+%H:%M:%S') - Change detected, recompiling..."
        bash "${SCRIPT_DIR}/dx.sh" compile 2>&1 | tail -5
    done
}

# ── Command: health ──────────────────────────────────────────────────
cmd_health() {
    log_header "System Health Check"

    if [[ ! -f "${SCRIPT_DIR}/health-check.sh" ]]; then
        log_error "health-check.sh not found"
        exit 1
    fi

    bash "${SCRIPT_DIR}/health-check.sh" --verbose
}

# ── Command: coverage ────────────────────────────────────────────────
cmd_coverage() {
    log_header "Coverage Dashboard"

    check_java || exit 1

    if [[ ! -f "docs/v6/latest/facts/coverage.json" ]]; then
        log_warn "Coverage facts not found. Run: mvn test && bash scripts/observatory/observatory.sh --facts"
        return 0
    fi

    if [[ ! -f "${SCRIPT_DIR}/coverage-report.sh" ]]; then
        log_error "coverage-report.sh not found"
        exit 1
    fi

    bash "${SCRIPT_DIR}/coverage-report.sh"
}

# ── Command: observatory ────────────────────────────────────────────
cmd_observatory() {
    log_header "Code Observatory (Facts, Diagrams, Analysis)"

    check_java || exit 1

    if [[ ! -f "${SCRIPT_DIR}/observatory/observatory.sh" ]]; then
        log_error "observatory.sh not found"
        exit 1
    fi

    log_info "Generating code facts, diagrams, and static analysis..."
    bash "${SCRIPT_DIR}/observatory/observatory.sh"

    local exit_code=$?
    if [[ $exit_code -eq 0 ]]; then
        log_success "Observatory facts generated"
        log_info "See: docs/v6/latest/INDEX.md"
    else
        log_warn "Observatory completed with warnings (exit code: $exit_code)"
    fi
    return $exit_code
}

# ── Command: fast ───────────────────────────────────────────────────
cmd_fast() {
    log_header "Fast Workflow (Compile + Test Changed Modules)"

    check_java || exit 1

    local timer=$(timer_start)
    bash "${SCRIPT_DIR}/dx.sh"
    local elapsed=$(timer_stop "$timer")

    log_success "Fast workflow completed in ${elapsed}s"
}

# ── Command: all ────────────────────────────────────────────────────
cmd_all() {
    log_header "Full Validation Pipeline (Compile, Test, Analysis, Observatory)"

    check_java || exit 1

    if [[ ! -f "${SCRIPT_DIR}/validate-all.sh" ]]; then
        log_error "validate-all.sh not found"
        exit 1
    fi

    local timer=$(timer_start)
    bash "${SCRIPT_DIR}/validate-all.sh"
    local exit_code=$?
    local elapsed=$(timer_stop "$timer")

    if [[ $exit_code -eq 0 ]]; then
        log_success "Full validation passed in ${elapsed}s"
    else
        log_error "Full validation failed with exit code: $exit_code"
    fi
    return $exit_code
}

# ── Main Command Router ────────────────────────────────────────────
main() {
    # Handle no args or help flags
    if [[ $# -eq 0 ]] || [[ "$1" == "-h" ]] || [[ "$1" == "--help" ]]; then
        cmd_help
    fi

    local cmd="$1"
    shift || true

    case "$cmd" in
        help)           cmd_help "$@" ;;
        test)           cmd_test "$@" ;;
        build)          cmd_build "$@" ;;
        clean)          cmd_clean "$@" ;;
        docs)           cmd_docs "$@" ;;
        validate)       cmd_validate "$@" ;;
        deploy-local)   cmd_deploy_local "$@" ;;
        deploy-full)    cmd_deploy_full "$@" ;;
        stop)           cmd_stop "$@" ;;
        status)         cmd_status "$@" ;;
        watch)          cmd_watch "$@" ;;
        health)         cmd_health "$@" ;;
        coverage)       cmd_coverage "$@" ;;
        observatory)    cmd_observatory "$@" ;;
        fast)           cmd_fast "$@" ;;
        all)            cmd_all "$@" ;;
        *)
            log_error "Unknown command: $cmd"
            echo ""
            cmd_help
            exit 1
            ;;
    esac
}

main "$@"
