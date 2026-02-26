#!/bin/bash
#
# Docker wrapper for YAWL Performance Validation Script v2.0
#
# This wrapper ensures the performance validation runs in the proper Docker environment
#

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATION_DIR="$(dirname "$SCRIPT_DIR")"

# Check if we're already in a Docker container
if [[ -f /.dockerenv ]] || grep -q 'docker\|lxc' /proc/1/cgroup 2>/dev/null; then
    # We're already in Docker, run the script directly
    exec "$SCRIPT_DIR/validate-performance-v2.sh" "$@"
fi

# Check if Docker is available
if ! command -v docker >/dev/null 2>&1; then
    echo "Error: Docker is not available. Please install Docker or run in a container environment."
    exit 1
fi

# Build YAWL container if needed
if ! docker image inspect cre:0.3.0 >/dev/null 2>&1; then
    echo "Building YAWL container..."
    if ! docker buildx bake --load; then
        echo "Error: Failed to build YAWL container"
        exit 1
    fi
fi

# Run the validation script in Docker container
echo "Running performance validation in Docker container..."
docker run --rm \
    -v "$(pwd):/work" \
    -w /work \
    cre:0.3.0 \
    bash /work/validation/validation-scripts/validate-performance-v2.sh "$@"