#!/usr/bin/env bash
# test-full.sh — Run the complete test suite with JaCoCo coverage reporting.
#
# Purpose:
#   Executes all tests across all tag groups (unit, integration, slow) with
#   JaCoCo line and branch coverage enabled. Use before merging to a shared
#   branch or when you need coverage evidence. Docker-backed tests are excluded
#   by default (pass --docker to include them).
#
# What is included by default:
#   - All JUnit 5 and JUnit 4 tests
#   - Tests tagged @Tag("unit"), @Tag("slow"), and untagged tests
#   - H2 in-memory database (no external services required)
#   - JaCoCo line coverage threshold: 80% (configured in pom.xml)
#   - JaCoCo branch coverage threshold: 70%
#
# What is excluded by default:
#   - Tests tagged @Tag("docker") or @Tag("containers") — require Docker daemon
#   - Pass --docker to include Docker-backed Testcontainer tests
#
# Parallelism:
#   Uses the default Surefire configuration with forkCount=1.5C (1.5 x CPUs),
#   reuseForks=true, and JUnit Platform parallel execution factor=2.0.
#   This is the same parallelism as `mvn clean test` but with JaCoCo active.
#
# Coverage reports:
#   Generated per-module at: target/site/jacoco/index.html
#   XML (for SonarQube): target/site/jacoco/jacoco.xml
#
# Usage:
#   ./scripts/test-full.sh                    # All tests, no Docker
#   ./scripts/test-full.sh --docker           # Include Docker/container tests
#   ./scripts/test-full.sh -pl yawl-engine    # Full tests for single module
#
# Expected runtime: ~3-5 minutes for the full suite (unit + slow tests).
# With --docker: ~8-12 minutes (includes container startup time).
#
# Exit codes: 0 = all tests pass and coverage gates met,
#             non-zero = test failure or coverage threshold violation

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"

INCLUDE_DOCKER=false
EXTRA_MAVEN_ARGS=()

# Parse script-specific flags before forwarding remaining args to Maven.
while [[ $# -gt 0 ]]; do
    case "$1" in
        --docker)
            INCLUDE_DOCKER=true
            shift
            ;;
        *)
            EXTRA_MAVEN_ARGS+=("$1")
            shift
            ;;
    esac
done

if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
    echo "ERROR: pom.xml not found at ${PROJECT_ROOT}" >&2
    exit 1
fi

# Build Maven profile selection based on flags.
# The "fast" profile is activeByDefault=true and excludes integration/docker.
# Activate the appropriate profile explicitly to override the default.
MAVEN_PROFILES="java25"
if [[ "${INCLUDE_DOCKER}" == "true" ]]; then
    MAVEN_PROFILES="${MAVEN_PROFILES},docker"
    echo "[test-full] Docker/container tests: ENABLED"
    echo "[test-full] Ensure Docker daemon is running before proceeding."
else
    echo "[test-full] Docker/container tests: DISABLED (pass --docker to enable)"
fi

echo "[test-full] Running full test suite with JaCoCo coverage..."
echo "[test-full] Project root: ${PROJECT_ROOT}"
echo "[test-full] Active profiles: ${MAVEN_PROFILES}"
echo "[test-full] Coverage thresholds: line=80%, branch=70%"
echo ""

mvn \
    --file "${PROJECT_ROOT}/pom.xml" \
    clean test \
    -P "${MAVEN_PROFILES}" \
    -Djacoco.skip=false \
    "${EXTRA_MAVEN_ARGS[@]+"${EXTRA_MAVEN_ARGS[@]}"}"

echo ""
echo "[test-full] Test suite complete."
echo "[test-full] Coverage reports: find ${PROJECT_ROOT} -path '*/target/site/jacoco/index.html'"
