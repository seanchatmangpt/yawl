#!/usr/bin/env bash
# ==========================================================================
# dev-up.sh — Start YAWL Development Environment
#
# Builds and starts the development stack with hot-reload support.
#
# Usage:
#   bash docker/scripts/dev-up.sh              # Start dev environment
#   bash docker/scripts/dev-up.sh --build      # Force rebuild
#   bash docker/scripts/dev-up.sh --detach     # Run in background
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

BUILD=false
DETACH=false

for arg in "$@"; do
    case "$arg" in
        --build|-b)   BUILD=true ;;
        --detach|-d)  DETACH=true ;;
        --help|-h)    echo "Usage: dev-up.sh [--build] [--detach]"; exit 0 ;;
    esac
done

echo "=== YAWL Development Environment ==="
echo ""

# Build if requested or image doesn't exist
if [[ "$BUILD" == "true" ]] || ! docker image inspect yawl-dev:latest &>/dev/null; then
    echo "Building development image..."
    docker build \
        -t yawl-dev:latest \
        -f docker/development/Dockerfile.dev \
        .
fi

# Create development docker-compose if it doesn't exist
DEV_COMPOSE="${REPO_ROOT}/docker-compose.dev.yml"
if [[ ! -f "$DEV_COMPOSE" ]]; then
    cat > "$DEV_COMPOSE" << EOF
version: '3.8'

services:
  yawl-dev:
    image: yawl-dev:latest
    container_name: yawl-dev
    hostname: yawl-dev
    ports:
      - "8080:8080"
      - "9090:9090"
      - "5005:5005"  # Debug port
    environment:
      SPRING_PROFILES_ACTIVE: development
      JAVA_OPTS: >-
        -XX:+UseContainerSupport
        -XX:MaxRAMPercentage=75.0
        -XX:+UseZGC
        -XX:+ZGenerational
        -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
    volumes:
      - ./:/work:cached
      - ~/.m2:/root/.m2:cached
    working_dir: /work
    command: ./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

networks:
  default:
    name: yawl-dev-network
EOF
fi

echo "Starting development environment..."
if [[ "$DETACH" == "true" ]]; then
    docker compose -f docker-compose.dev.yml up -d
    echo ""
    echo "Development environment started in background"
    echo "Logs: docker compose -f docker-compose.dev.yml logs -f"
    echo "Stop: bash docker/scripts/dev-down.sh"
else
    docker compose -f docker-compose.dev.yml up
fi
