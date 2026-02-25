#!/bin/bash
# =============================================================================
# Fallback Startup Script for YAWL Engine
# =============================================================================
# This script provides fallback startup options if the main JAR approach fails
#
# Usage:
#   ./docker/production/fallback-start.sh
# =============================================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# Check which JAR files are available
check_jars() {
    echo "Checking available JAR files..."

    local jars=()

    if [[ -f "./yawl-mcp-a2a-app/yawl-mcp-a2a-app/target/dependency/yawl-engine-6.0.0-Beta.jar" ]]; then
        jars+=("./yawl-mcp-a2a-app/yawl-mcp-a2a-app/target/dependency/yawl-engine-6.0.0-Beta.jar")
        success "Found: yawl-engine-6.0.0-Beta.jar"
    fi

    if [[ -f "./yawl-engine/target/yawl-engine-*.jar" ]]; then
        local jar_file=$(ls ./yawl-engine/target/yawl-engine-*.jar | head -1)
        jars+=("$jar_file")
        success "Found: $jar_file"
    fi

    if [[ ${#jars[@]} -eq 0 ]]; then
        error "No JAR files found!"
        echo ""
        echo "Options:"
        echo "1. Build the project first:"
        echo "   mvn clean package -pl yawl-engine,yawl-control-panel -DskipTests"
        echo ""
        echo "2. Or build and run in container:"
        echo "   docker build -t yawl-engine-build ."
        echo "   docker run --rm -v \$(pwd):/work yawl-engine-build mvn package"
        echo ""
        exit 1
    fi

    echo ""
    echo "Available JARs:"
    for jar in "${jars[@]}"; do
        echo "  - $jar"
    done
    echo ""

    # Use the first available JAR
    JAR_FILE="${jars[0]}"
    info "Using JAR: $JAR_FILE"
}

# Test JAR contents
test_jar() {
    echo "Testing JAR contents..."

    if ! command -v jar &> /dev/null; then
        warn "jar command not available, skipping JAR test"
        return 0
    fi

    # Check if Main-Class is present
    if jar tf "$JAR_FILE" | grep -q "META-INF/MANIFEST.MF"; then
        local main_class=$(unzip -p "$JAR_FILE" META-INF/MANIFEST.MF | grep -i "Main-Class" | cut -d' ' -f2-)
        if [[ -n "$main_class" ]]; then
            success "Main-Class found: $main_class"
        else
            warn "No Main-Class found in manifest"
        fi
    else
        warn "No MANIFEST.MF found"
    fi

    # Check for YAWL classes
    if jar tf "$JAR_FILE" | grep -q "org/yawlfoundation/yawl/"; then
        success "YAWL classes found in JAR"
    else
        warn "No YAWL classes found in JAR"
    fi
}

# Build Docker image with fallback options
build_docker() {
    echo "Building Docker image with fallback options..."

    # Create a temporary Dockerfile with multiple JAR sources
    cat > Dockerfile.fallback << 'EOF'
# Fallback Dockerfile for YAWL Engine
FROM eclipse-temurin:25-jre-alpine

# Create directories
RUN mkdir -p /app && chown -R 1000:1000 /app

# Copy multiple JAR locations
COPY --chown=1000:1000 \
    ./yawl-mcp-a2a-app/yawl-mcp-a2a-app/target/dependency/yawl-engine-*.jar \
    /app/ || true

COPY --chown=1000:1000 \
    ./yawl-engine/target/yawl-engine-*.jar \
    /app/ || true

# Use the first available JAR
RUN if [ -f /app/yawl-engine-6.0.0-Beta.jar ]; then \
    mv /app/yawl-engine-6.0.0-Beta.jar /app/yawl-engine.jar; \
    elif [ -f /app/yawl-engine-*.jar ]; then \
    mv /app/yawl-engine-*.jar /app/yawl-engine.jar; \
    else \
    echo "No JAR found!"; \
    exit 1; \
    fi

# Simple startup
CMD ["java", "-jar", "/app/yawl-engine.jar"]
EOF

    # Build the image
    if docker build -t yawl-engine:fallback -f Dockerfile.fallback .; then
        success "Docker image built successfully"
        rm Dockerfile.fallback
        return 0
    else
        error "Docker build failed"
        rm Dockerfile.fallback
        return 1
    fi
}

# Main function
main() {
    echo "=============================================================================="
    echo "  YAWL Engine Fallback Startup Script"
    echo "=============================================================================="
    echo ""

    check_jars
    test_jar

    echo ""
    read -p "Build Docker image with these JARs? (y/N): " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        if build_docker; then
            echo ""
            success "Docker image 'yawl-engine:fallback' built successfully!"
            echo ""
            echo "To run:"
            echo "  docker run -d -p 8080:8080 --name yawl-engine-fallback yawl-engine:fallback"
            echo ""
            echo "To check logs:"
            echo "  docker logs yawl-engine-fallback"
        fi
    else
        echo "Build cancelled."
    fi
}

# Run main function
main "$@"