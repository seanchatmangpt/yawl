#!/usr/bin/env bash
# =============================================================================
# YAWL v6.0 - Build Script
#
# Usage:
#   ./build.sh               Compile all modules (fast, no tests)
#   ./build.sh --package     Full build: compile + test + JAR/WAR packaging
#   ./build.sh --clean       Clean build artifacts only (mvn clean)
#   ./build.sh --parallel    Enable parallel module builds (-T 1C)
#   ./build.sh --skip-tests  Compile and package, skipping test execution
#   ./build.sh --module=M    Build a single module (e.g. yawl-engine)
#   ./build.sh --spotbugs    Run SpotBugs static analysis after compile
#   ./build.sh --profile=P   Activate a Maven profile (e.g. java25, prod)
#
# Exit codes:
#   0  Build succeeded
#   1  Compilation failed
#   2  Test failure during --package
#   3  Packaging failed
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Terminal colours
# ---------------------------------------------------------------------------
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m'
else
    RED='' GREEN='' YELLOW='' CYAN='' BOLD='' NC=''
fi

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
success() { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
fatal()   { error "$*"; exit 1; }

header() {
    echo ""
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo -e "${BOLD}${CYAN}  $*${NC}"
    echo -e "${BOLD}${CYAN}==============================================================================${NC}"
    echo ""
}

print_module_summary() {
    local target_dir="$1"
    local count
    count="$(find "${target_dir}" -name "*.jar" -not -name "*sources*" -not -name "*javadoc*" 2>/dev/null | wc -l)"
    if [ "${count}" -gt 0 ]; then
        info "Build artifacts:"
        find "${target_dir}" -name "*.jar" -not -name "*sources*" -not -name "*javadoc*" \
            2>/dev/null | sort | while read -r jar; do
            SIZE="$(du -sh "${jar}" 2>/dev/null | cut -f1)"
            info "  ${jar##"${PROJECT_DIR}/"} (${SIZE})"
        done
    fi
}

# ---------------------------------------------------------------------------
# Parse arguments
# ---------------------------------------------------------------------------
MODE="compile"
PARALLEL=false
SKIP_TESTS=false
ACTIVE_MODULE=""
RUN_SPOTBUGS=false
MAVEN_PROFILE=""

for arg in "$@"; do
    case "$arg" in
        --package)      MODE="package" ;;
        --clean)        MODE="clean" ;;
        --parallel)     PARALLEL=true ;;
        --skip-tests)   SKIP_TESTS=true ;;
        --spotbugs)     RUN_SPOTBUGS=true ;;
        --module=*)     ACTIVE_MODULE="${arg#--module=}" ;;
        --profile=*)    MAVEN_PROFILE="${arg#--profile=}" ;;
        --help|-h)
            echo "Usage: $0 [--package|--clean|--parallel|--skip-tests|--module=M|--spotbugs|--profile=P|--help]"
            echo ""
            echo "  (no flags)      Compile all modules (mvn clean compile)"
            echo "  --package       Full build: compile + test + package"
            echo "  --clean         Clean build artifacts only"
            echo "  --parallel      Enable parallel module builds (-T 1C)"
            echo "  --skip-tests    Skip test execution during packaging"
            echo "  --module=M      Build only the specified Maven module"
            echo "  --spotbugs      Run SpotBugs static analysis"
            echo "  --profile=P     Activate Maven profile (e.g. java25, prod)"
            exit 0
            ;;
        *)
            fatal "Unknown argument: $arg. Use --help for usage."
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Build up the Maven command
# ---------------------------------------------------------------------------
MVN_CMD=( mvn --batch-mode --no-transfer-progress )

if $PARALLEL; then
    MVN_CMD+=( -T 1C )
fi

if $SKIP_TESTS; then
    MVN_CMD+=( -DskipTests=true )
fi

if [ -n "${MAVEN_PROFILE}" ]; then
    MVN_CMD+=( "-P${MAVEN_PROFILE}" )
fi

if [ -n "${ACTIVE_MODULE}" ]; then
    # Validate the module directory exists
    if [ ! -d "${PROJECT_DIR}/${ACTIVE_MODULE}" ]; then
        fatal "Module directory not found: ${PROJECT_DIR}/${ACTIVE_MODULE}"
    fi
    MVN_CMD+=( --projects "${ACTIVE_MODULE}" --also-make )
fi

# ---------------------------------------------------------------------------
# Mode: clean
# ---------------------------------------------------------------------------
do_clean() {
    header "Cleaning Build Artifacts"
    cd "${PROJECT_DIR}"
    "${MVN_CMD[@]}" clean && success "Clean complete." || { error "Clean failed."; exit 1; }
}

# ---------------------------------------------------------------------------
# Mode: compile
# ---------------------------------------------------------------------------
do_compile() {
    header "Compiling YAWL"

    if $PARALLEL; then info "Parallel build enabled (-T 1C)."; fi
    if [ -n "${ACTIVE_MODULE}" ]; then info "Building module: ${ACTIVE_MODULE}"; fi

    cd "${PROJECT_DIR}"

    if ! "${MVN_CMD[@]}" clean compile 2>&1; then
        error "Compilation failed."
        error ""
        error "Common causes:"
        error "  - Java version mismatch: need Java ${REQUIRED_JAVA_MAJOR:-21}+"
        error "  - Missing dependency: run ./setup-dev.sh first"
        error "  - Source error: check the output above for the first error"
        exit 1
    fi

    success "Compilation succeeded."

    # Show what was built
    if [ -z "${ACTIVE_MODULE}" ]; then
        print_module_summary "${PROJECT_DIR}"
    else
        print_module_summary "${PROJECT_DIR}/${ACTIVE_MODULE}/target"
    fi
}

# ---------------------------------------------------------------------------
# Mode: package
# ---------------------------------------------------------------------------
do_package() {
    header "Packaging YAWL (compile + test + package)"

    if $SKIP_TESTS; then warn "Test execution is SKIPPED (--skip-tests)."; fi

    cd "${PROJECT_DIR}"

    if ! "${MVN_CMD[@]}" clean package 2>&1; then
        # Determine specific failure
        if find . -path "*/surefire-reports/TEST-*.xml" -newer pom.xml 2>/dev/null | grep -q .; then
            error "Test failure during packaging."
            error "Review surefire reports: find . -path '*/surefire-reports/*.txt'"
            exit 2
        else
            error "Packaging failed (not a test failure — likely compilation or plugin error)."
            exit 3
        fi
    fi

    success "Packaging succeeded."
    print_module_summary "${PROJECT_DIR}"
}

# ---------------------------------------------------------------------------
# Optional: SpotBugs
# ---------------------------------------------------------------------------
do_spotbugs() {
    header "Running SpotBugs Static Analysis"

    cd "${PROJECT_DIR}"
    if mvn spotbugs:check \
        --batch-mode \
        --no-transfer-progress \
        -Dspotbugs.excludeFilterFile=spotbugs-exclude.xml \
        2>&1; then
        success "SpotBugs analysis passed."
    else
        error "SpotBugs found issues. Review the report in target/spotbugsXml.xml"
        exit 1
    fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
header "YAWL v6.0 Build"
info "Project root: ${PROJECT_DIR}"
SECONDS=0

case "${MODE}" in
    clean)   do_clean ;;
    compile) do_compile ;;
    package) do_package ;;
    *)       fatal "Internal error: unhandled mode '${MODE}'" ;;
esac

if $RUN_SPOTBUGS && [ "${MODE}" != "clean" ]; then
    do_spotbugs
fi

ELAPSED=$SECONDS
echo ""
echo -e "${BOLD}${GREEN}Build completed in ${ELAPSED}s.${NC}"
echo ""
echo -e "  ${BOLD}Next steps:${NC}"
echo -e "    ${CYAN}./test.sh${NC}              Run unit tests"
echo -e "    ${CYAN}./test.sh --coverage${NC}   Tests + JaCoCo coverage report"
echo -e "    ${CYAN}./build.sh --package${NC}   Full build with tests and packaging"
echo ""
