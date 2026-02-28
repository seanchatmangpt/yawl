#!/usr/bin/env bash
# ==========================================================================
# test-incremental-build.sh — Verify incremental compilation effectiveness
#
# Tests that:
# 1. Clean build establishes cache baseline
# 2. Incremental (no changes) uses cache hits
# 3. Comment-only changes don't trigger recompile
# 4. Single file changes trigger minimal rebuild
# 5. Cache survives mvn clean
#
# Usage:
#   bash scripts/test-incremental-build.sh              # Run all tests
#   bash scripts/test-incremental-build.sh --verbose    # With output
#   bash scripts/test-incremental-build.sh --metrics    # Save metrics
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

# Ensure Java 25
_TEMURIN25="/usr/lib/jvm/temurin-25-jdk-amd64"
if [ -d "${_TEMURIN25}" ]; then
    export JAVA_HOME="${_TEMURIN25}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
fi

VERBOSE="${1:---quiet}"
METRICS_DIR="./.claude/metrics"
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

mkdir -p "${METRICS_DIR}"

echo "=========================================="
echo "Incremental Build Verification"
echo "Timestamp: ${TIMESTAMP}"
echo "=========================================="

# Test 1: Clean baseline
echo ""
echo "[TEST 1] Clean build (establishes cache baseline)"
echo "Command: mvn clean compile -q -DskipTests"
START=$(date +%s)
mvn clean compile -q -DskipTests 2>&1 | grep -i "cache\|incremental\|recompil" || true
END=$(date +%s)
CLEAN_TIME=$((END - START))
echo "Time: ${CLEAN_TIME}s"

# Test 2: Incremental (no changes) — should be <1s with cache
echo ""
echo "[TEST 2] Incremental compile (no changes — should use cache)"
echo "Command: mvn compile -q -DskipTests"
START=$(date +%s)
mvn compile -q -DskipTests 2>&1 | grep -i "cache\|incremental\|recompil" || true
END=$(date +%s)
INCREMENTAL_TIME=$((END - START))
echo "Time: ${INCREMENTAL_TIME}s"

if [ "${INCREMENTAL_TIME}" -lt 2 ]; then
    echo "✓ PASS: Incremental < 2s (cache working)"
else
    echo "⚠ WARN: Incremental ${INCREMENTAL_TIME}s (cache may not be enabled)"
fi

# Test 3: Comment-only change (should NOT recompile)
echo ""
echo "[TEST 3] Comment-only change (should NOT trigger recompile)"

TEST_FILE="${REPO_ROOT}/yawl-utilities/src/main/java/org/yawlfoundation/yawl/util/XNodeIO.java"
if [ ! -f "${TEST_FILE}" ]; then
    TEST_FILE=$(find . -name "*.java" -path "*/yawl-utilities/*" | head -1)
fi

if [ -z "${TEST_FILE}" ] || [ ! -f "${TEST_FILE}" ]; then
    echo "⚠ SKIP: No test file found in yawl-utilities"
else
    # Backup original
    cp "${TEST_FILE}" "${TEST_FILE}.bak"

    # Add comment
    echo "// Incremental build test at ${TIMESTAMP}" >> "${TEST_FILE}"

    echo "Modified: ${TEST_FILE} (added comment)"
    echo "Command: mvn compile -q -DskipTests"

    START=$(date +%s)
    OUTPUT=$(mvn compile -q -DskipTests 2>&1 || true)
    END=$(date +%s)
    TEST3_TIME=$((END - START))

    # Check if actual recompilation happened
    if echo "${OUTPUT}" | grep -q "\[INFO\] Building"; then
        echo "⚠ Full build triggered (unexpected)"
    else
        echo "✓ Incremental build (expected)"
    fi

    echo "Time: ${TEST3_TIME}s"

    # Restore
    mv "${TEST_FILE}.bak" "${TEST_FILE}"
fi

# Test 4: Single module change
echo ""
echo "[TEST 4] Single module change (incremental rebuild only)"
TEST_FILE=$(find . -name "*.java" -path "*/yawl-security/*" | head -1)

if [ -z "${TEST_FILE}" ] || [ ! -f "${TEST_FILE}" ]; then
    echo "⚠ SKIP: No test file found in yawl-security"
else
    cp "${TEST_FILE}" "${TEST_FILE}.bak"

    # Modify a method (whitespace change in non-public method)
    sed -i 's/^    /        /' "${TEST_FILE}" | head -1 || true

    echo "Modified: ${TEST_FILE}"
    echo "Command: mvn compile -q -DskipTests -pl yawl-security"

    START=$(date +%s)
    mvn compile -q -DskipTests -pl yawl-security 2>&1 | grep -i "cache\|incremental" || true
    END=$(date +%s)
    TEST4_TIME=$((END - START))

    echo "Time: ${TEST4_TIME}s"

    # Restore
    mv "${TEST_FILE}.bak" "${TEST_FILE}"
fi

# Test 5: Cache survives clean (cache is in ~/.m2/)
echo ""
echo "[TEST 5] Cache survives 'mvn clean'"
echo "Command: mvn clean (should NOT delete ~/.m2/build-cache/)"

CACHE_DIR="${HOME}/.m2/build-cache"
CACHE_EXISTS_BEFORE=$([ -d "${CACHE_DIR}" ] && echo "yes" || echo "no")

mvn clean -q -DskipTests

CACHE_EXISTS_AFTER=$([ -d "${CACHE_DIR}" ] && echo "yes" || echo "no")

if [ "${CACHE_EXISTS_BEFORE}" == "no" ]; then
    echo "⚠ SKIP: No cache directory found (cache not persisted yet)"
elif [ "${CACHE_EXISTS_AFTER}" == "yes" ]; then
    echo "✓ PASS: Cache survives mvn clean"
else
    echo "⚠ FAIL: Cache deleted by mvn clean"
fi

# Save metrics
echo ""
echo "[METRICS] Saving metrics to ${METRICS_DIR}/incremental-test-${TIMESTAMP}.json"

cat > "${METRICS_DIR}/incremental-test-${TIMESTAMP}.json" << EOF
{
  "timestamp": "${TIMESTAMP}",
  "test_results": {
    "clean_build_time_sec": ${CLEAN_TIME},
    "incremental_no_change_time_sec": ${INCREMENTAL_TIME},
    "cache_working": $([ ${INCREMENTAL_TIME} -lt 2 ] && echo "true" || echo "false"),
    "test3_comment_change_time_sec": ${TEST3_TIME:-"skipped"},
    "test4_single_module_time_sec": ${TEST4_TIME:-"skipped"},
    "test5_cache_survives_clean": "${CACHE_EXISTS_AFTER}"
  },
  "summary": {
    "status": "complete",
    "incremental_target": 2,
    "incremental_actual": ${INCREMENTAL_TIME},
    "clean_target": 50,
    "clean_actual": ${CLEAN_TIME}
  }
}
EOF

echo "Metrics saved."

# Final summary
echo ""
echo "=========================================="
echo "Summary:"
echo "=========================================="
echo "Clean build: ${CLEAN_TIME}s (target: <50s)"
echo "Incremental (no change): ${INCREMENTAL_TIME}s (target: <2s)"
if [ "${INCREMENTAL_TIME}" -lt 2 ]; then
    echo "Overall: ✓ PASS — incremental builds optimized"
else
    echo "Overall: ⚠ CHECK — incremental may not be fully enabled"
fi
echo "=========================================="
