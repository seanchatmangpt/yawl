#!/usr/bin/env bash
# =============================================================================
# verify-docker-setup.sh - Verify Docker infrastructure setup
# =============================================================================
# This script verifies that Docker infrastructure is properly configured
# without requiring fully working application JARs.
#
# Usage:
#   bash scripts/verify-docker-setup.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=== YAWL Docker Infrastructure Verification ==="
echo "Timestamp: $(date)"
echo "Project Root: $PROJECT_ROOT"
echo

# Check Docker
echo "[CHECK] Docker availability..."
if command -v docker &> /dev/null && docker info &> /dev/null; then
    echo "  [PASS] Docker is running"
else
    echo "  [FAIL] Docker is not available"
    exit 1
fi

# Check Docker Compose
echo "[CHECK] Docker Compose..."
if docker compose version &> /dev/null; then
    echo "  [PASS] Docker Compose is available: $(docker compose version --short)"
else
    echo "  [FAIL] Docker Compose is not available"
    exit 1
fi

# Check Dockerfile.engine exists
echo "[CHECK] Dockerfile.engine..."
if [[ -f "$PROJECT_ROOT/docker/production/Dockerfile.engine" ]]; then
    echo "  [PASS] Dockerfile.engine exists"
else
    echo "  [FAIL] Dockerfile.engine not found"
    exit 1
fi

# Check Dockerfile.mcp-a2a-app exists
echo "[CHECK] Dockerfile.mcp-a2a-app..."
if [[ -f "$PROJECT_ROOT/docker/production/Dockerfile.mcp-a2a-app" ]]; then
    echo "  [PASS] Dockerfile.mcp-a2a-app exists"
else
    echo "  [FAIL] Dockerfile.mcp-a2a-app not found"
    exit 1
fi

# Check docker-compose files
echo "[CHECK] Docker Compose files..."
for compose_file in docker-compose.yml docker-compose.a2a-mcp-test.yml; do
    if [[ -f "$PROJECT_ROOT/$compose_file" ]]; then
        echo "  [PASS] $compose_file exists"
    else
        echo "  [FAIL] $compose_file not found"
    fi
done

# Check test script
echo "[CHECK] Test scripts..."
for script in test-a2a-mcp-zai.sh run-docker-a2a-mcp-test.sh; do
    if [[ -f "$PROJECT_ROOT/scripts/$script" ]]; then
        echo "  [PASS] scripts/$script exists"
    else
        echo "  [FAIL] scripts/$script not found"
    fi
done

# Check Docker network
echo "[CHECK] Docker network..."
if docker network inspect yawl-network &> /dev/null; then
    echo "  [PASS] yawl-network exists"
else
    echo "  [WARN] yawl-network not found (will be created on first run)"
fi

# Check Docker images
echo "[CHECK] Docker images..."
for image in "yawl-engine:6.0.0-alpha" "yawl-mcp-a2a:6.0.0-alpha"; do
    if docker image inspect "$image" &> /dev/null; then
        size=$(docker image inspect "$image" --format='{{.Size}}' 2>/dev/null | awk '{printf "%.1f MB", $1/1024/1024}')
        echo "  [PASS] $image exists ($size)"
    else
        echo "  [WARN] $image not found (run with --build to create)"
    fi
done

# Check YAML configuration files (no duplicate keys)
echo "[CHECK] YAML configuration files..."
yaml_error=false
for yml in "$PROJECT_ROOT/yawl-mcp-a2a-app/src/main/resources/application"*.yml \
           "$PROJECT_ROOT/yawl-mcp-a2a-app/src/test/resources/application"*.yml; do
    if [[ -f "$yml" ]]; then
        # Check for duplicate top-level keys
        duplicates=$(grep -E "^[a-z].*:" "$yml" 2>/dev/null | cut -d: -f1 | sort | uniq -d)
        if [[ -n "$duplicates" ]]; then
            echo "  [FAIL] $(basename $yml) has duplicate keys: $duplicates"
            yaml_error=true
        else
            echo "  [PASS] $(basename $yml) has no duplicate keys"
        fi
    fi
done

if [[ "$yaml_error" == true ]]; then
    echo "  [INFO] Fix YAML files before building Docker images"
fi

echo
echo "=== Summary ==="
echo "Docker infrastructure verification complete."
echo
echo "To build and test:"
echo "  1. Ensure MCP SDK is installed locally:"
echo "     mvn install:install-file -Dfile=path/to/mcp-sdk.jar -DgroupId=io.modelcontextprotocol.sdk -DartifactId=mcp -Dversion=1.0.0-RC1 -Dpackaging=jar"
echo
echo "  2. Build images:"
echo "     bash scripts/run-docker-a2a-mcp-test.sh --build"
echo
echo "  3. Run tests:"
echo "     bash scripts/run-docker-a2a-mcp-test.sh"
