#!/usr/bin/env bash
# ==========================================================================
# docker-validate.sh - Run YAWL Validations in Docker
# ==========================================================================
# Simple wrapper for running all validations inside Docker containers.
# Handles Docker build, volume mounts, and output collection.
#
# Usage:
#   ./scripts/docker-validate.sh              # Run all validations
#   ./scripts/docker-validate.sh build        # Build validation image only
#   ./scripts/docker-validate.sh fast         # Fast validation (no analysis)
#   ./scripts/docker-validate.sh compile      # Compile only
#   ./scripts/docker-validate.sh test         # Test only
#   ./scripts/docker-validate.sh analysis     # Static analysis only
#   ./scripts/docker-validate.sh observatory  # Observatory only
#   ./scripts/docker-validate.sh shell        # Interactive shell
#   ./scripts/docker-validate.sh ci           # Full CI pipeline
#   ./scripts/docker-validate.sh clean        # Clean up containers and volumes
#
# Environment:
#   YAWL_IMAGE_NAME  - Docker image name (default: yawl-validation)
#   YAWL_IMAGE_TAG   - Docker image tag (default: latest)
#   BUILD_DATE       - Build date label
#   VCS_REF          - Git commit reference
# ==========================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Configuration
IMAGE_NAME="${YAWL_IMAGE_NAME:-yawl-validation}"
IMAGE_TAG="${YAWL_IMAGE_TAG:-latest}"
FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
COMPOSE_FILE="docker-compose.validation.yml"

# Colors
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[0;33m'
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

# Helper functions
log_info() {
    echo -e "${CYAN}[docker-validate]${RESET} $*"
}

log_success() {
    echo -e "${GREEN}[docker-validate]${RESET} $*"
}

log_warning() {
    echo -e "${YELLOW}[docker-validate]${RESET} WARN: $*" >&2
}

log_error() {
    echo -e "${RED}[docker-validate]${RESET} ERROR: $*" >&2
}

# Check Docker availability
check_docker() {
    if ! command -v docker &>/dev/null; then
        log_error "Docker is not installed or not in PATH"
        exit 1
    fi

    if ! docker info &>/dev/null; then
        log_error "Docker daemon is not running or you don't have permission"
        log_error "Try: sudo docker-validate.sh or add your user to docker group"
        exit 1
    fi
}

# Build validation image
build_image() {
    log_info "Building validation image: ${FULL_IMAGE}"

    docker compose -f "${COMPOSE_FILE}" build --build-arg BUILD_DATE="${BUILD_DATE:-$(date -u +%Y-%m-%d)}" --build-arg VCS_REF="${VCS_REF:-$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')}" validation

    if [[ $? -eq 0 ]]; then
        log_success "Image built successfully"
    else
        log_error "Image build failed"
        exit 1
    fi
}

# Run validation with compose
run_compose() {
    local service="$1"
    shift

    log_info "Running: ${service}"

    docker compose -f "${COMPOSE_FILE}" run --rm "${service}" "$@"
}

# Run validation directly
run_validation() {
    local args="$*"

    log_info "Starting validation container..."

    # Ensure output directories exist
    mkdir -p "${REPO_ROOT}/docs/v6/latest"
    mkdir -p "${REPO_ROOT}/reports"
    mkdir -p "${REPO_ROOT}/target"

    # Run validation
    docker compose -f "${COMPOSE_FILE}" run --rm validation ${args}
    local exit_code=$?

    if [[ $exit_code -eq 0 ]]; then
        log_success "Validation completed"
    else
        log_error "Validation failed with exit code: ${exit_code}"
    fi

    return $exit_code
}

# Clean up
clean_up() {
    log_info "Cleaning up containers and volumes..."

    docker compose -f "${COMPOSE_FILE}" down -v 2>/dev/null || true

    # Optionally remove the image
    if [[ "${1:-}" == "--all" ]]; then
        log_info "Removing image: ${FULL_IMAGE}"
        docker rmi "${FULL_IMAGE}" 2>/dev/null || true
    fi

    log_success "Cleanup complete"
}

# Interactive shell
interactive_shell() {
    log_info "Starting interactive shell..."

    # Ensure output directories exist
    mkdir -p "${REPO_ROOT}/docs/v6/latest"
    mkdir -p "${REPO_ROOT}/reports"
    mkdir -p "${REPO_ROOT}/target"

    docker compose -f "${COMPOSE_FILE}" run --rm validation bash
}

# Show usage
show_usage() {
    cat << 'EOF'
YAWL Docker Validation

Usage:
  ./scripts/docker-validate.sh [command] [options]

Commands:
  (default)     Run all validations (compile, test, analysis, observatory)
  build         Build the validation Docker image
  fast          Fast validation (skip analysis and observatory)
  compile       Compile only
  test          Test only
  analysis      Static analysis only (SpotBugs, PMD, Checkstyle)
  observatory   Generate observatory facts and diagrams
  schema        Validate XML against XSD schemas
  ci            Run full CI pipeline
  shell         Start interactive shell in container
  clean         Remove containers and volumes
  clean --all   Remove containers, volumes, and image

Options:
  -h, --help    Show this help message

Environment:
  YAWL_IMAGE_NAME  Docker image name (default: yawl-validation)
  YAWL_IMAGE_TAG   Docker image tag (default: latest)
  BUILD_DATE       Build date for image labels
  VCS_REF          Git commit reference for image labels

Examples:
  # Run all validations
  ./scripts/docker-validate.sh

  # Fast validation (compile + test only)
  ./scripts/docker-validate.sh fast

  # Run observatory to generate docs/v6/latest/
  ./scripts/docker-validate.sh observatory

  # Interactive shell for debugging
  ./scripts/docker-validate.sh shell

  # Full CI pipeline
  ./scripts/docker-validate.sh ci

Generated Outputs:
  target/                    - Build artifacts
  docs/v6/latest/            - Observatory outputs (facts, diagrams, receipts)
  reports/                   - Static analysis reports

For more information, see:
  CLAUDE.md - Project configuration
  docs/BUILD.md - Build documentation
EOF
}

# Main
main() {
    check_docker

    local command="${1:-all}"
    shift || true

    case "$command" in
        -h|--help)
            show_usage
            exit 0
            ;;
        build)
            build_image
            ;;
        fast)
            build_image
            run_validation "./scripts/validate-all.sh --fast"
            ;;
        compile)
            build_image
            run_compose compile
            ;;
        test)
            build_image
            run_compose test
            ;;
        analysis)
            build_image
            run_compose analysis
            ;;
        observatory)
            build_image
            run_compose observatory
            ;;
        schema)
            build_image
            run_compose schema
            ;;
        ci)
            build_image
            run_compose ci
            ;;
        shell)
            build_image
            interactive_shell
            ;;
        clean)
            clean_up "$@"
            ;;
        all|"")
            build_image
            run_validation
            ;;
        *)
            # Pass through to validate-all.sh
            build_image
            run_validation "./scripts/validate-all.sh $command"
            ;;
    esac
}

main "$@"
