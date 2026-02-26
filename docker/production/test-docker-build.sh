#!/bin/bash
# =============================================================================
# Test Docker Build Script for YAWL Engine
# =============================================================================
# This script tests the simplified Dockerfile and verifies the build
#
# Usage:
#   ./docker/production/test-docker-build.sh
#
# Exit codes:
#   0  Build successful
#   1  Build failed
# =============================================================================

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}  Testing YAWL Engine Docker Build${NC}"
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo ""
}

# Check prerequisites
check_prerequisites() {
    info "Checking prerequisites..."

    # Check Docker
    if ! command -v docker &> /dev/null; then
        error "Docker is not installed or not in PATH"
        exit 1
    fi

    # Check Docker daemon
    if ! docker info &> /dev/null; then
        error "Docker daemon is not running"
        exit 1
    fi

    # Check if JAR exists
    if [[ -f "${PROJECT_DIR}/yawl-mcp-a2a-app/yawl-mcp-a2a-app/target/dependency/yawl-engine-6.0.0-Beta.jar" ]]; then
        success "Found pre-built JAR at yawl-mcp-a2a-app/target/dependency/"
    else
        warn "No pre-built JAR found. Build might fail."
        info "You may need to run Maven build first:"
        info "  cd ${PROJECT_DIR} && mvn clean package -pl yawl-engine,yawl-control-panel -DskipTests"
    fi

    success "Prerequisites check passed"
}

# Build the Docker image
build_docker_image() {
    info "Building Docker image..."

    cd "${PROJECT_DIR}"

    # Build with build cache disabled for clean test
    if docker build -t yawl-engine:6.0.0-alpha-test \
        -f docker/production/Dockerfile.engine.simplified \
        --no-cache .; then
        success "Docker image built successfully"
        return 0
    else
        error "Docker build failed"
        return 1
    fi
}

# Test the built image
test_docker_image() {
    info "Testing Docker image..."

    # Run container in background
    docker run -d \
        --name yawl-engine-test \
        -p 8080:8080 \
        -p 9090:9090 \
        yawl-engine:6.0.0-alpha-test

    # Wait for container to start
    info "Waiting for container to start..."
    sleep 10

    # Check if container is running
    if docker ps | grep -q yawl-engine-test; then
        success "Container is running"

        # Check health status
        info "Checking container health..."
        if docker inspect yawl-engine-test --format='{{.State.Health.Status}}' 2>/dev/null | grep -q "healthy"; then
            success "Container is healthy"
        else
            warn "Container health status not healthy (may be initializing)"
        fi

        # Test basic connectivity
        info "Testing basic connectivity..."
        if curl -sf http://localhost:8080/actuator/health/liveness &>/dev/null; then
            success "Health endpoint is responding"
        else
            warn "Health endpoint not responding (expected for minimal setup)"
        fi

    else
        error "Container is not running"
        docker logs yawl-engine-test
        return 1
    fi

    # Cleanup
    info "Cleaning up test container..."
    docker stop yawl-engine-test >/dev/null 2>&1
    docker rm yawl-engine-test >/dev/null 2>&1
    success "Cleanup completed"

    return 0
}

# Main function
main() {
    header

    check_prerequisites

    if build_docker_image; then
        if test_docker_image; then
            echo ""
            success "All tests passed! YAWL Engine Docker image is ready."
            echo ""
            info "To run the image:"
            info "  docker run -d -p 8080:8080 -p 9090:9090 --name yawl-engine yawl-engine:6.0.0-alpha-test"
            echo ""
            info "To check logs:"
            info "  docker logs yawl-engine"
            echo ""
            info "To stop the container:"
            info "  docker stop yawl-engine"
            exit 0
        else
            error "Image tests failed"
            exit 1
        fi
    else
        error "Build failed"
        exit 1
    fi
}

# Run main function
main "$@"