#!/usr/bin/env bash
# Run unit tests inside container. Ensures build.properties then runs ant clean unitTest.
# Execute from repo root: docker compose run --rm yawl-dev bash -c "./scripts/run-unit-tests.sh"

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
# Ensure build.properties exists (script or direct copy; container may have broken symlink)
if [ -x "./scripts/ensure-build-properties.sh" ]; then
    ./scripts/ensure-build-properties.sh
else
    [ -f build/build.properties.remote ] && rm -f build/build.properties && cp build/build.properties.remote build/build.properties
fi
ant -f build/build.xml clean unitTest
