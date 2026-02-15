#!/bin/bash
# YAWL Build Skill - Claude Code 2026 Best Practices
# Usage: /yawl-build [target] [--verbose]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BUILD_FILE="${PROJECT_ROOT}/build/build.xml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Build Skill - Build WARs and JARs with Ant

Usage: /yawl-build [target] [options]

Targets:
  compile          Compile source code only
  buildWebApps     Build all WAR files (default)
  buildAll         Full build with all release material
  build_Standalone Build standalone JAR
  clean            Remove build artifacts
  javadoc          Generate Javadoc documentation
  unitTest         Run unit tests

Options:
  -v, --verbose    Enable verbose output
  -q, --quiet      Suppress most output
  -h, --help       Show this help message

Examples:
  /yawl-build                    # Build all WARs
  /yawl-build compile            # Compile only
  /yawl-build buildAll -v        # Full build with verbose output
EOF
}

# Parse arguments
TARGET="buildWebApps"
VERBOSE=""
QUIET=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        -v|--verbose)
            VERBOSE="-verbose"
            shift
            ;;
        -q|--quiet)
            QUIET="-quiet"
            shift
            ;;
        compile|buildWebApps|buildAll|build_Standalone|clean|javadoc|unitTest)
            TARGET="$1"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Verify Ant is available
if ! command -v ant &> /dev/null; then
    echo -e "${RED}Error: Ant is not installed or not in PATH${NC}"
    echo "Install with: brew install ant (macOS) or apt-get install ant (Linux)"
    exit 1
fi

# Verify build.xml exists
if [[ ! -f "${BUILD_FILE}" ]]; then
    echo -e "${RED}Error: Build file not found: ${BUILD_FILE}${NC}"
    exit 1
fi

# Execute build
echo -e "${BLUE}[yawl-build] Building YAWL with target: ${TARGET}${NC}"
echo -e "${BLUE}[yawl-build] Build file: ${BUILD_FILE}${NC}"
echo ""

cd "${PROJECT_ROOT}"

if ant -f "${BUILD_FILE}" ${VERBOSE} ${QUIET} "${TARGET}"; then
    echo ""
    echo -e "${GREEN}[yawl-build] Build successful: ${TARGET}${NC}"

    # Show output artifacts for relevant targets
    if [[ "${TARGET}" == "buildWebApps" || "${TARGET}" == "buildAll" ]]; then
        echo ""
        echo -e "${YELLOW}[yawl-build] Output artifacts:${NC}"
        if ls "${PROJECT_ROOT}/output/"*.war 1>/dev/null 2>&1; then
            ls -la "${PROJECT_ROOT}/output/"*.war
        fi
        if ls "${PROJECT_ROOT}/output/"*.jar 1>/dev/null 2>&1; then
            ls -la "${PROJECT_ROOT}/output/"*.jar
        fi
    fi
else
    echo ""
    echo -e "${RED}[yawl-build] Build failed: ${TARGET}${NC}"
    exit 1
fi
