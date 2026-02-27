#!/usr/bin/env bash
# YAWL AOT Cache Generator
# Generates Ahead-of-Time cache for faster JVM warmup

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AOT_OUTPUT="/opt/yawl/aot.cache"
LOG_FILE="/var/log/yawl/aot-generation.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "${LOG_FILE}"
}

log_info() { log "${GREEN}[INFO]${NC} $1"; }
log_warn() { log "${YELLOW}[WARN]${NC} $1"; }
log_error() { log "${RED}[ERROR]${NC} $1"; }

# Check for GraalVM or JDK 25+ with AOT support
check_aot_support() {
    if java -XX:AOTCache 2>&1 | grep -q "Usage"; then
        log_info "AOT cache support detected"
        return 0
    fi

    log_warn "AOT cache not supported. Java 25+ required."
    return 1
}

# Collect common YAWL classes for AOT
collect_aot_classes() {
    log_info "Collecting classes for AOT cache..."

    local class_file="${PROJECT_ROOT}/target/aot-classes.txt"

    # Include frequently used engine classes
    cat > "${class_file}" << 'EOF'
org.yawlfoundation.yawl.engine.YEngine
org.yawlfoundation.yawl.engine.YNetRunner
org.yawlfoundation.yawl.engine.YWorkItem
org.yawlfoundation.yawl.elements.YNet
org.yawlfoundation.yawl.elements.YTask
org.yawlfoundation.yawl.elements.YCondition
org.yawlfoundation.yawl.stateless.YStatelessEngine
org.yawlfoundation.yawl.resourcing.WorkQueue
org.yawlfoundation.yawl.authentication.User
EOF

    echo "${class_file}"
}

# Generate AOT cache
generate_aot_cache() {
    local class_file="$1"

    log_info "Generating AOT cache..."

    java -XX:AOTCache="${AOT_OUTPUT}" \
         --enable-preview \
         -XX:+UseCompactObjectHeaders \
         -XX:+UseShenandoahGC \
         -cp "${PROJECT_ROOT}/yawl-engine/target/classes:${PROJECT_ROOT}/yawl-elements/target/classes" \
         @${class_file} 2>/dev/null || true

    log_info "AOT cache generated at ${AOT_OUTPUT}"
}

# Main execution
main() {
    log_info "Starting AOT cache generation for YAWL v6.0.0"

    mkdir -p "$(dirname "${AOT_OUTPUT}")"
    mkdir -p "$(dirname "${LOG_FILE}")"

    if ! check_aot_support; then
        log_warn "Skipping AOT cache generation - not supported on this JVM"
        exit 0
    fi

    local class_file
    class_file=$(collect_aot_classes)
    generate_aot_cache "${class_file}"

    if [[ -f "${AOT_OUTPUT}" ]]; then
        local cache_size
        cache_size=$(stat -f%z "${AOT_OUTPUT}" 2>/dev/null || stat -c%s "${AOT_OUTPUT}" 2>/dev/null || echo "unknown")
        log_info "AOT cache generation complete. Size: ${cache_size} bytes"
    else
        log_warn "AOT cache file not created"
    fi

    log_info "Usage: java -XX:AOTCache=${AOT_OUTPUT} --enable-preview -jar yawl-engine.jar"
}

main "$@"