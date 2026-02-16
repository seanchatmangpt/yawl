#!/bin/bash
# YAWL Build Skill - Claude Code 2026 Best Practices
# Usage: /yawl-build [target] [--verbose]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BUILD_FILE="${PROJECT_ROOT}/build/build.xml"
POM_FILE="${PROJECT_ROOT}/pom.xml"
BUILD_SYSTEM=""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_usage() {
    cat << 'EOF'
YAWL Build Skill - Build WARs and JARs with Maven (or legacy Ant)

Usage: /yawl-build [target] [options]

Maven Targets (default):
  compile          Compile source code only
  package          Build all WAR/JAR files (default)
  install          Install to local Maven repository
  clean            Remove build artifacts
  test             Run unit tests
  verify           Run integration tests

Ant Targets (deprecated - will be removed in v6.0):
  compile          Compile source code only
  buildWebApps     Build all WAR files
  buildAll         Full build with all release material
  build_Standalone Build standalone JAR
  clean            Remove build artifacts
  javadoc          Generate Javadoc documentation
  unitTest         Run unit tests

Options:
  -v, --verbose    Enable verbose output
  -q, --quiet      Suppress most output
  -s, --skip-tests Skip tests (Maven only)
  -h, --help       Show this help message

Examples:
  /yawl-build                    # Build all artifacts (Maven or Ant)
  /yawl-build compile            # Compile only
  /yawl-build package -s         # Build without tests
EOF
}

# Parse arguments
TARGET=""
VERBOSE=""
QUIET=""
SKIP_TESTS=false

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
        -s|--skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        compile|package|install|clean|test|verify|buildWebApps|buildAll|build_Standalone|javadoc|unitTest)
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

# Auto-detect build system (Maven preferred)
if [[ -f "${POM_FILE}" ]]; then
    BUILD_SYSTEM="maven"
    MVN_CMD=$(command -v mvnd 2>/dev/null || command -v mvn 2>/dev/null || echo "")
    if [[ -z "${MVN_CMD}" ]]; then
        echo -e "${RED}Error: Maven is not installed or not in PATH${NC}"
        echo "Install with: brew install maven (macOS) or apt-get install maven (Linux)"
        exit 1
    fi
    echo -e "${GREEN}[yawl-build] Using Maven build system${NC}"
elif [[ -f "${BUILD_FILE}" ]]; then
    BUILD_SYSTEM="ant"
    echo -e "${YELLOW}⚠️  WARNING: Ant build system is deprecated (will be removed in v6.0)${NC}"
    echo -e "${YELLOW}   Migrate to Maven: see ANT_TO_MAVEN_MIGRATION.md${NC}"
    echo ""
    if ! command -v ant &> /dev/null; then
        echo -e "${RED}Error: Ant is not installed or not in PATH${NC}"
        echo "Install with: brew install ant (macOS) or apt-get install ant (Linux)"
        exit 1
    fi
else
    echo -e "${RED}Error: No build file found (pom.xml or build/build.xml)${NC}"
    exit 1
fi

# Set default target based on build system
if [[ -z "${TARGET}" ]]; then
    if [[ "${BUILD_SYSTEM}" == "maven" ]]; then
        TARGET="package"
    else
        TARGET="buildWebApps"
    fi
fi

# Execute build
echo -e "${BLUE}[yawl-build] Building YAWL with target: ${TARGET}${NC}"
echo -e "${BLUE}[yawl-build] Build system: ${BUILD_SYSTEM}${NC}"
echo ""

cd "${PROJECT_ROOT}"

BUILD_SUCCESS=false

if [[ "${BUILD_SYSTEM}" == "maven" ]]; then
    # Maven build with Java preview features
    # Note: --enable-preview must be passed via MAVEN_OPTS, not as CLI argument
    # Only add incubator modules if Java 25+ is available
    JAVA_VERSION=$(${MVN_CMD} --version 2>/dev/null | grep "Java version" | sed -n 's/.*Java version: \([0-9]*\).*/\1/p' || echo "11")

    if [[ "${JAVA_VERSION}" -ge 25 ]]; then
        export MAVEN_OPTS="--enable-preview --add-modules jdk.incubator.concurrent -Xmx2g ${MAVEN_OPTS:-}"
    else
        export MAVEN_OPTS="--enable-preview -Xmx2g ${MAVEN_OPTS:-}"
    fi

    MVN_ARGS=("--batch-mode" "-T" "1C")

    # Add verbose/quiet flags
    if [[ -n "${VERBOSE}" ]]; then
        MVN_ARGS+=("-X")
    fi
    if [[ -n "${QUIET}" ]]; then
        MVN_ARGS+=("-q")
    fi

    # Skip tests if requested
    if [[ "${SKIP_TESTS}" == "true" ]]; then
        MVN_ARGS+=("-DskipTests")
    fi

    echo -e "${BLUE}[yawl-build] Maven command: ${MVN_CMD} ${TARGET} ${MVN_ARGS[*]}${NC}"
    echo ""

    if ${MVN_CMD} "${TARGET}" "${MVN_ARGS[@]}"; then
        BUILD_SUCCESS=true
    fi

elif [[ "${BUILD_SYSTEM}" == "ant" ]]; then
    # Ant build (deprecated)
    echo -e "${YELLOW}⚠️  WARNING: Using deprecated Ant build system${NC}"
    echo -e "${YELLOW}   This build method will be removed in YAWL v6.0${NC}"
    echo -e "${YELLOW}   Please migrate to Maven: see ANT_TO_MAVEN_MIGRATION.md${NC}"
    echo ""

    if ant -f "${BUILD_FILE}" ${VERBOSE} ${QUIET} "${TARGET}"; then
        BUILD_SUCCESS=true
    fi
fi

# Report results
if [[ "${BUILD_SUCCESS}" == "true" ]]; then
    echo ""
    echo -e "${GREEN}[yawl-build] Build successful: ${TARGET}${NC}"

    # Show output artifacts
    echo ""
    echo -e "${YELLOW}[yawl-build] Output artifacts:${NC}"

    if [[ "${BUILD_SYSTEM}" == "maven" ]]; then
        # Maven outputs to target/ directories
        if find "${PROJECT_ROOT}" -path "*/target/*.war" -o -path "*/target/*.jar" 2>/dev/null | head -10 | grep -q .; then
            find "${PROJECT_ROOT}" -path "*/target/*.war" -o -path "*/target/*.jar" 2>/dev/null | head -10 | xargs ls -lh
        fi
    else
        # Ant outputs to output/ directory
        if ls "${PROJECT_ROOT}/output/"*.{war,jar} 1>/dev/null 2>&1; then
            ls -lh "${PROJECT_ROOT}/output/"*.{war,jar} 2>/dev/null || true
        fi
    fi
else
    echo ""
    echo -e "${RED}[yawl-build] Build failed: ${TARGET}${NC}"
    exit 1
fi
