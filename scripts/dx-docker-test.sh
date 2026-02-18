#!/usr/bin/env bash
# ==========================================================================
# dx-docker-test.sh — Containerized Test Execution
#
# Builds test image from Dockerfile.java25 and runs tests in isolated
# container with optimized JVM settings.
#
# Usage:
#   bash scripts/dx-docker-test.sh              # Run all tests in container
#   bash scripts/dx-docker-test.sh --module yawl-engine  # Test specific module
#   bash scripts/dx-docker-test.sh --shell      # Interactive shell in container
#   bash scripts/dx-docker-test.sh --build      # Build image only
#
# Requirements:
#   Docker (or gVisor runsc for sandboxed execution)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

IMAGE_NAME="yawl-test:java25"
CONTAINER_NAME="yawl-test-$$"

# ── Parse arguments ───────────────────────────────────────────────────────
MODULE=""
SHELL_MODE=false
BUILD_ONLY=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --module)  MODULE="$2"; shift 2 ;;
        --shell)   SHELL_MODE=true; shift ;;
        --build)   BUILD_ONLY=true; shift ;;
        -h|--help)
            sed -n '2,/^# =====/p' "$0" | grep '^#' | sed 's/^# \?//'
            exit 0 ;;
        *) shift ;;
    esac
done

# ── Check Docker availability ─────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
    echo "Error: Docker not found. Install Docker to use this script."
    exit 1
fi

# ── Build test image ──────────────────────────────────────────────────────
build_image() {
    echo "=== Building YAWL Test Image ==="

    # Use Dockerfile.java25 if it exists, otherwise create a minimal one
    if [[ -f "${REPO_ROOT}/Dockerfile.java25" ]]; then
        DOCKERFILE="${REPO_ROOT}/Dockerfile.java25"
    else
        echo "Creating temporary Dockerfile for Java 25..."
        DOCKERFILE=$(mktemp)
        cat > "$DOCKERFILE" <<'EOF'
FROM eclipse-temurin:25-jdk-alpine

# Install build tools
RUN apk add --no-cache maven git bash

# Set working directory
WORKDIR /work

# JVM optimizations for Java 25
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseZGC \
    -XX:+ZGenerational \
    -XX:+UseCompactObjectHeaders \
    -XX:+UseStringDeduplication \
    -Djdk.virtualThreadScheduler.parallelism=200"

# Cache Maven dependencies
RUN mkdir -p /root/.m2 && \
    echo "<settings><localRepository>/root/.m2/repository</localRepository></settings>" > /root/.m2/settings.xml

ENTRYPOINT ["/bin/bash"]
EOF
    fi

    echo "Building from: ${DOCKERFILE}"
    docker build -t "$IMAGE_NAME" -f "$DOCKERFILE" "$(dirname "$DOCKERFILE")" 2>&1 | tail -5

    # Cleanup temp Dockerfile
    if [[ "$DOCKERFILE" != "${REPO_ROOT}/Dockerfile.java25" ]]; then
        rm -f "$DOCKERFILE"
    fi

    echo "Image built: ${IMAGE_NAME}"
}

# ── Build if image doesn't exist or --build specified ─────────────────────
if [[ "$BUILD_ONLY" == "true" ]] || ! docker image inspect "$IMAGE_NAME" &>/dev/null; then
    build_image
    if [[ "$BUILD_ONLY" == "true" ]]; then
        exit 0
    fi
fi

# ── Shell mode ────────────────────────────────────────────────────────────
if [[ "$SHELL_MODE" == "true" ]]; then
    echo "=== Starting Interactive Shell in Test Container ==="
    echo "Container: ${CONTAINER_NAME}"
    echo "Image: ${IMAGE_NAME}"
    echo ""

    # Run interactively with volume mount
    docker run --rm -it \
        --name "$CONTAINER_NAME" \
        -v "${REPO_ROOT}:/work" \
        -w /work \
        -e MAVEN_OPTS="-Xmx2g" \
        "$IMAGE_NAME" \
        -c "echo 'YAWL Test Environment (Java 25)' && echo 'Run: mvn test' && exec bash"

    exit 0
fi

# ── Run tests in container ────────────────────────────────────────────────
echo "=== Running Tests in Docker Container ==="
echo "Image: ${IMAGE_NAME}"
echo "Module: ${MODULE:-all}"
echo ""

START_MS=$(python3 -c "import time; print(int(time.time() * 1000))")

# Build the Maven command
MVN_CMD="mvn -P agent-dx test -q"
if [[ -n "$MODULE" ]]; then
    MVN_CMD="mvn -P agent-dx test -pl $MODULE -amd -q"
fi

# Run container with tests
set +e
docker run --rm \
    --name "$CONTAINER_NAME" \
    -v "${REPO_ROOT}:/work:cached" \
    -v "${HOME}/.m2:/root/.m2:cached" \
    -w /work \
    -e MAVEN_OPTS="-Xmx2g -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" \
    "$IMAGE_NAME" \
    -c "$MVN_CMD" 2>&1

EXIT_CODE=$?
set -e

END_MS=$(python3 -c "import time; print(int(time.time() * 1000))")
ELAPSED_MS=$((END_MS - START_MS))
ELAPSED_S=$(python3 -c "print(f'{${ELAPSED_MS}/1000:.1f}')")

# ── Summary ───────────────────────────────────────────────────────────────
echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
    echo "✓ Tests passed in ${ELAPSED_S}s"
else
    echo "✗ Tests failed in ${ELAPSED_S}s"
fi

echo ""
echo "Interactive debugging: bash scripts/dx-docker-test.sh --shell"

exit $EXIT_CODE
