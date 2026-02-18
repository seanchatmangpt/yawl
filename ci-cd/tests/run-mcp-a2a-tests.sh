#!/usr/bin/env bash
# =============================================================================
# YAWL MCP and A2A CI/CD Integration Test Runner
#
# Runs all MCP protocol and A2A agent communication tests via Maven.
# Designed for use in CI/CD pipelines (GitHub Actions, GitLab CI, etc.)
#
# Usage:
#   ./ci-cd/tests/run-mcp-a2a-tests.sh [--parallel] [--report-dir DIR]
#
# Environment variables:
#   YAWL_ENGINE_URL   - Engine URL for tests needing a live engine (optional)
#   REPORT_DIR        - Override JUnit report output directory
#   PARALLEL          - Set to "true" to run with parallel Maven builds
#
# Exit codes:
#   0 - All tests passed
#   1 - One or more tests failed
#   2 - Build/compile error
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

PARALLEL="${PARALLEL:-false}"
REPORT_DIR="${REPORT_DIR:-${PROJECT_ROOT}/test-results/mcp-a2a}"

# Maven parallel flag
if [ "${PARALLEL}" = "true" ]; then
    MVN_FLAGS="-T 1.5C"
else
    MVN_FLAGS=""
fi

echo "================================================================"
echo " YAWL MCP and A2A Integration Test Suite"
echo "================================================================"
echo " Project root : ${PROJECT_ROOT}"
echo " Report dir   : ${REPORT_DIR}"
echo " Parallel     : ${PARALLEL}"
echo " Engine URL   : ${YAWL_ENGINE_URL:-not set (unit tests only)}"
echo "================================================================"

mkdir -p "${REPORT_DIR}"

# Run the full MCP + A2A test suite
mvn ${MVN_FLAGS} -f "${PROJECT_ROOT}/pom.xml" \
    clean test \
    -Dtest="YawlMcpServerTest,McpProtocolTest,McpLoggingHandlerTest,McpPerformanceTest,YawlA2AServerTest,A2AAuthenticationTest,A2AProtocolTest,A2AClientTest" \
    -Dsurefire.reportsDirectory="${REPORT_DIR}" \
    2>&1 | tee "${REPORT_DIR}/test-run.log"

MAVEN_EXIT="${PIPESTATUS[0]}"

echo ""
echo "================================================================"
if [ "${MAVEN_EXIT}" -eq 0 ]; then
    echo " RESULT: ALL TESTS PASSED"
else
    echo " RESULT: TEST FAILURES DETECTED (exit code ${MAVEN_EXIT})"
fi
echo " Reports: ${REPORT_DIR}"
echo "================================================================"

exit "${MAVEN_EXIT}"
