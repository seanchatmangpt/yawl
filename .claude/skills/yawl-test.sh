#!/bin/bash
# YAWL Test Skill - Claude Code 2026 Best Practices
# Usage: /yawl-test [--module=name] [--coverage]

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
YAWL Test Skill - Run JUnit test suites

Usage: /yawl-test [options]

Options:
  --module=NAME    Run tests for specific module
  --coverage       Generate coverage report
  --verbose        Show detailed test output
  -h, --help       Show this help message

Available Modules:
  elements       YAWL element tests
  engine         Engine core tests
  state          State management tests
  exceptions     Exception handling tests
  logging        Logging tests
  schema         Schema validation tests
  unmarshaller   XML unmarshalling tests
  util           Utility tests
  worklist       Worklist tests
  authentication Authentication tests

Examples:
  /yawl-test                     # Run all tests
  /yawl-test --module=engine     # Run engine tests only
  /yawl-test --coverage          # Run with coverage
EOF
}

# Parse arguments
MODULE=""
COVERAGE=false
VERBOSE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            print_usage
            exit 0
            ;;
        --module=*)
            MODULE="${1#*=}"
            shift
            ;;
        --coverage)
            COVERAGE=true
            shift
            ;;
        --verbose)
            VERBOSE="-verbose"
            shift
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            print_usage
            exit 1
            ;;
    esac
done

# Check if compiled
if [[ ! -d "${PROJECT_ROOT}/classes" ]]; then
    echo -e "${YELLOW}[yawl-test] Classes not found, compiling first...${NC}"
    ant -f "${BUILD_FILE}" compile
fi

cd "${PROJECT_ROOT}"

# Module name to test class mapping
declare -A MODULE_MAP=(
    ["elements"]="org.yawlfoundation.yawl.elements.TestElementsSuite"
    ["engine"]="org.yawlfoundation.yawl.engine.TestEngineSuite"
    ["state"]="org.yawlfoundation.yawl.state.TestStateSuite"
    ["exceptions"]="org.yawlfoundation.yawl.exceptions.TestExceptionsSuite"
    ["logging"]="org.yawlfoundation.yawl.logging.TestLoggingSuite"
    ["schema"]="org.yawlfoundation.yawl.schema.TestSchemaSuite"
    ["unmarshaller"]="org.yawlfoundation.yawl.unmarshaller.TestUnmarshallerSuite"
    ["util"]="org.yawlfoundation.yawl.util.TestUtilSuite"
    ["worklist"]="org.yawlfoundation.yawl.worklist.TestWorklistSuite"
    ["authentication"]="org.yawlfoundation.yawl.authentication.TestAuthenticationSuite"
)

# Run tests based on module or all
if [[ -n "${MODULE}" ]]; then
    if [[ -z "${MODULE_MAP[$MODULE]:-}" ]]; then
        echo -e "${RED}[yawl-test] Unknown module: ${MODULE}${NC}"
        echo "Available modules: ${!MODULE_MAP[*]}"
        exit 1
    fi

    TEST_CLASS="${MODULE_MAP[$MODULE]}"
    echo -e "${BLUE}[yawl-test] Running tests for module: ${MODULE}${NC}"
    echo -e "${BLUE}[yawl-test] Test class: ${TEST_CLASS}${NC}"
    echo ""

    java -cp "classes:build/3rdParty/lib/*" junit.textui.TestRunner "${TEST_CLASS}"
else
    echo -e "${BLUE}[yawl-test] Running all test suites...${NC}"
    echo ""

    if [[ "${COVERAGE}" == "true" ]]; then
        echo -e "${YELLOW}[yawl-test] Running with coverage via Ant...${NC}"
        ant -f "${BUILD_FILE}" ${VERBOSE} unitTest
    else
        java -cp "classes:build/3rdParty/lib/*" junit.textui.TestRunner org.yawlfoundation.yawl.TestAllYAWLSuites
    fi
fi

EXIT_CODE=$?

echo ""
if [[ ${EXIT_CODE} -eq 0 ]]; then
    echo -e "${GREEN}[yawl-test] All tests passed${NC}"
else
    echo -e "${RED}[yawl-test] Some tests failed (exit code: ${EXIT_CODE})${NC}"
fi

exit ${EXIT_CODE}
