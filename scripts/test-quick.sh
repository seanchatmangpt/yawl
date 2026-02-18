#!/usr/bin/env bash
# test-quick.sh — Run unit tests only with parallel JUnit 5 execution.
#
# Purpose:
#   Executes the unit test suite across all modules using JUnit 5 parallel
#   execution. Coverage instrumentation (JaCoCo) is disabled to minimize
#   overhead. Integration tests and Docker-backed tests are excluded.
#
# What is included:
#   - All tests NOT tagged @Tag("integration") or @Tag("docker")
#   - Untagged tests (most standard JUnit tests)
#   - Tests tagged @Tag("unit") or @Tag("fast")
#
# What is excluded:
#   - Tests tagged @Tag("integration") — require running services
#   - Tests tagged @Tag("docker")       — require Docker daemon
#   - Tests tagged @Tag("slow")         — use test-full.sh for those
#   - JaCoCo instrumentation            — adds 15-20% overhead
#
# Parallelization:
#   Maven modules compile and test in parallel via -T 1.5C.
#   Within each module, JUnit 5 runs test methods concurrently.
#   Any test class with shared mutable static state must be annotated
#   @Execution(ExecutionMode.SAME_THREAD) to opt out of concurrency.
#
# Usage:
#   ./scripts/test-quick.sh                       # All unit tests (~60s)
#   ./scripts/test-quick.sh -pl yawl-engine       # Engine tests only (~20s)
#   ./scripts/test-quick.sh -pl yawl-engine -am   # Engine + dependencies (~25s)
#   ./scripts/test-quick.sh -Dtest=YNetRunnerTest # Single test class (~8s)
#
# Estimated time: ~60s full suite, ~20s single module (warm Maven cache)
#
# See DEVELOPER-BUILD-GUIDE.md for full documentation.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"

if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
    echo "ERROR: pom.xml not found at ${PROJECT_ROOT}" >&2
    exit 1
fi

echo "[test-quick] Running unit tests (integration/docker tests excluded)..."
echo "[test-quick] Project root: ${PROJECT_ROOT}"
echo "[test-quick] Parallelism: -T 1.5C modules + JUnit 5 concurrent methods"
echo "[test-quick] JaCoCo: disabled (use test-full.sh for coverage)"
echo "[test-quick] Estimated time: ~60s full suite, ~20s single module"
echo ""

mvn \
    --file "${PROJECT_ROOT}/pom.xml" \
    clean test \
    -Djacoco.skip=true \
    -Dgroups="unit,fast," \
    -DexcludedGroups="integration,docker,slow,containers" \
    "$@"

echo ""
echo "[test-quick] Unit tests complete."
echo "[test-quick] For coverage reports, run: ./scripts/test-full.sh"
