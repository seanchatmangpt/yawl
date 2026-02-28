#!/usr/bin/env bash
# YAWL CDS Archive Generator
# Generates Class Data Sharing archive for faster JVM startup

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
CDS_OUTPUT="/opt/yawl/classes.jsa"
LOG_FILE="/var/log/yawl/cds-generation.log"

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

# Check Java version (25+ required for compact headers)
check_java_version() {
    local java_version
    java_version=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)

    if [[ ${java_version} -lt 25 ]]; then
        log_error "Java 25+ required for CDS with compact headers. Found Java ${java_version}"
        exit 1
    fi
    log_info "Java version check passed: ${java_version}"
}

# Generate the base CDS archive
generate_base_archive() {
    log_info "Generating base CDS archive..."

    java -XX:ArchiveClassesAtExit="${CDS_OUTPUT}" \
         --enable-preview \
         -XX:+UseCompactObjectHeaders \
         -cp "${PROJECT_ROOT}/yawlbinder/target/classes" \
         org.yawlfoundation.yawl.engine.YEngine 2>/dev/null || true

    log_info "Base archive generated at ${CDS_OUTPUT}"
}

# Generate shared class list from common YAWL classes
generate_class_list() {
    log_info "Generating class list from YAWL modules..."

    local class_list="${PROJECT_ROOT}/target/yawl-classes.lst"

    find "${PROJECT_ROOT}" -path "*/target/classes/org/yawlfoundation/yawl/*.class" \
        | sed 's|.*/target/classes/||; s|\.class||; s|/|.|g' \
        > "${class_list}"

    log_info "Found $(wc -l < "${class_list}") classes"
    echo "${class_list}"
}

# Main execution
main() {
    log_info "Starting CDS archive generation for YAWL v6.0.0"

    mkdir -p "$(dirname "${CDS_OUTPUT}")"
    mkdir -p "$(dirname "${LOG_FILE}")"

    check_java_version
    generate_base_archive

    local archive_size
    archive_size=$(stat -f%z "${CDS_OUTPUT}" 2>/dev/null || stat -c%s "${CDS_OUTPUT}" 2>/dev/null || echo "unknown")

    log_info "CDS archive generation complete. Size: ${archive_size} bytes"
    log_info "Usage: java -XX:SharedArchiveFile=${CDS_OUTPUT} --enable-preview -jar yawl-engine.jar"
}

main "$@"