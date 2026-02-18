#!/usr/bin/env bash
# build-fast.sh — Compile YAWL sources in parallel without running any tests.
#
# Purpose:
#   Provides the fastest possible feedback on whether the codebase compiles.
#   Suitable for use after editing Java sources to catch syntax and type errors
#   before committing, without the overhead of test execution.
#
# What this skips (and why):
#   -DskipTests          : Skips test compilation AND execution (~60% of build time)
#   -Djacoco.skip=true   : Prevents bytecode instrumentation (instrumentation runs
#                          even when tests are skipped if JaCoCo is configured)
#   -Dmaven.javadoc.skip : Skips Javadoc generation (irrelevant for compile checks)
#   -Dmaven.source.skip  : Skips source JAR attachment (irrelevant for compile checks)
#
# Parallelization:
#   -T 1.5C is set in .mvn/maven.config and applies automatically.
#   maven-compiler-plugin uses incremental compilation; only changed files recompile.
#
# Usage:
#   ./scripts/build-fast.sh                   # Compile all modules (~45s)
#   ./scripts/build-fast.sh -pl yawl-engine   # Compile single module only (~12s)
#   ./scripts/build-fast.sh -pl yawl-engine,yawl-elements  # Compile subset (~15s)
#
# Estimated time: ~45s full build, ~12s single module (warm Maven cache)
#
# See DEVELOPER-BUILD-GUIDE.md for full documentation.

set -euo pipefail

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(dirname "${SCRIPT_DIR}")"

# Validate working directory so Maven picks up the multi-module pom.xml.
if [[ ! -f "${PROJECT_ROOT}/pom.xml" ]]; then
    echo "ERROR: pom.xml not found at ${PROJECT_ROOT}" >&2
    exit 1
fi

echo "[build-fast] Compiling YAWL sources (tests skipped)..."
echo "[build-fast] Project root: ${PROJECT_ROOT}"
echo "[build-fast] Parallelism: -T 1.5C (set in .mvn/maven.config)"
echo "[build-fast] Estimated time: ~45s full build, ~12s single module"
echo ""

# Pass remaining arguments (e.g. -pl, -am) directly to Maven so this script
# supports partial builds of individual modules without modification.
mvn \
    --file "${PROJECT_ROOT}/pom.xml" \
    clean compile \
    -DskipTests \
    -Djacoco.skip=true \
    -Dmaven.javadoc.skip=true \
    -Dmaven.source.skip=true \
    "$@"

echo ""
echo "[build-fast] Compilation complete."
