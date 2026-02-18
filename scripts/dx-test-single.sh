#!/usr/bin/env bash
# ==========================================================================
# dx-test-single.sh — Run a Single Test Class at Maximum Speed
#
# Compiles only the owning module (incremental), then runs exactly one test
# class with agent-dx profile. Fastest possible single-test feedback.
#
# Usage:
#   bash scripts/dx-test-single.sh YNetRunnerTest
#   bash scripts/dx-test-single.sh YNetRunnerTest yawl-engine
#   bash scripts/dx-test-single.sh org.yawlfoundation.yawl.engine.YNetRunnerTest
#   bash scripts/dx-test-single.sh YNetRunnerTest#testMethodName
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

TEST_PATTERN="${1:?Usage: dx-test-single.sh <TestClass> [module]}"
MODULE="${2:-}"

# If no module specified, find it from the test class name
if [[ -z "$MODULE" ]]; then
    # Strip method name if present (TestClass#method -> TestClass)
    CLASS_NAME="${TEST_PATTERN%%#*}"
    # Strip package prefix if present
    SHORT_NAME="${CLASS_NAME##*.}"

    # Search for the test file
    FOUND=$(find . -name "${SHORT_NAME}.java" -path "*/test/*" 2>/dev/null | head -1)
    if [[ -z "$FOUND" ]]; then
        echo "dx-test: Could not find test class: ${SHORT_NAME}"
        echo "dx-test: Specify module explicitly: dx-test-single.sh ${TEST_PATTERN} yawl-engine"
        exit 1
    fi

    # Extract module from path (e.g., ./yawl-engine/test/... -> yawl-engine)
    MODULE=$(echo "$FOUND" | cut -d'/' -f2)
    echo "dx-test: Found ${SHORT_NAME} in ${MODULE}"
fi

START_MS=$(date +%s%3N)

echo "dx-test: ${TEST_PATTERN} in ${MODULE}"

# Compile the module first (incremental, no clean)
mvn compile -P agent-dx -pl "${MODULE}" -am -q -o 2>/dev/null || \
    mvn compile -P agent-dx -pl "${MODULE}" -am -q

# Run the single test
mvn test -P agent-dx -pl "${MODULE}" -Dtest="${TEST_PATTERN}" -q -o 2>/dev/null || \
    mvn test -P agent-dx -pl "${MODULE}" -Dtest="${TEST_PATTERN}" -q

END_MS=$(date +%s%3N)
ELAPSED_MS=$((END_MS - START_MS))
ELAPSED_S=$(awk "BEGIN{printf \"%.1f\", ${ELAPSED_MS}/1000}")

echo "dx-test: OK (${ELAPSED_S}s)"
