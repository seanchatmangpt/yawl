#!/usr/bin/env bash
# test-fast.sh — Run unit tests only via the "quick-test" Maven profile.
#
# Purpose:
#   Provides a fast local test loop that runs only tests annotated with
#   @Tag("unit"). Integration tests, Docker-container tests, and tests tagged
#   @Tag("slow") are excluded. JaCoCo instrumentation is disabled.
#
# Profile behaviour (quick-test in pom.xml):
#   - Groups: unit
#   - ExcludedGroups: integration, docker, containers, slow
#   - forkCount: 1 (single forked JVM reused across all classes)
#   - reuseForks: true (amortises H2 and classloader startup)
#   - skipAfterFailureCount: 1 (fail fast on first broken test)
#   - jacoco.skip: true (no bytecode instrumentation overhead)
#
# JUnit Platform parallelism:
#   Within the single forked JVM, JUnit Platform runs test methods and classes
#   concurrently according to test/resources/junit-platform.properties:
#     parallel.config.dynamic.factor=2.0
#   This saturates CPUs despite using only one JVM process.
#
# Marking tests for this profile:
#   Annotate unit test classes with @Tag("unit") at class level:
#     @Tag("unit")
#     class YNetRunnerTest { ... }
#
# Usage:
#   ./scripts/test-fast.sh                          # All modules, unit tests
#   ./scripts/test-fast.sh -pl yawl-engine          # Single module only
#   ./scripts/test-fast.sh -Dtest=YNetRunnerTest    # Single test class
#
# Expected runtime: ~30-60 seconds for the full suite (unit tests only).
#
# Exit codes: 0 = all tests pass, non-zero = test failure or build error

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"

if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
    echo "ERROR: pom.xml not found at ${PROJECT_ROOT}" >&2
    exit 1
fi

echo "[test-fast] Running unit tests (quick-test profile)..."
echo "[test-fast] Project root: ${PROJECT_ROOT}"
echo "[test-fast] Included tags: unit"
echo "[test-fast] Excluded tags: integration, docker, containers, slow"
echo "[test-fast] JaCoCo: disabled"
echo ""

mvn \
    --file "${PROJECT_ROOT}/pom.xml" \
    clean test \
    -P quick-test \
    -Djacoco.skip=true \
    "$@"

echo ""
echo "[test-fast] Unit tests complete."
