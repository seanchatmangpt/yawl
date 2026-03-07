#!/usr/bin/env bash
set -euo pipefail

# Benchmark build-test loop with 2 decimal precision
# Usage: bash scripts/benchmark-build.sh

# Add mvnd to PATH if installed in ~/tools
if [[ -x "${HOME}/tools/maven-mvnd-1.0.2-darwin-aarch64/bin/mvnd" ]]; then
    export PATH="${HOME}/tools/maven-mvnd-1.0.2-darwin-aarch64/bin:${PATH}"
fi

START_NS=$(date +%s%N)

echo "=== BUILD-TEST BENCHMARK START ==="
echo "Start time: $(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
echo ""

# Run dx.sh all (compile + test + validation) - capture exit code properly
set +e
bash scripts/dx.sh all 2>&1 | tee /tmp/benchmark-log.txt
EXIT_CODE=${PIPESTATUS[0]}
set -e

END_NS=$(date +%s%N)

# Calculate elapsed with 2 decimal precision using Python (cross-platform)
ELAPSED=$(python3 -c "
start_ns = ${START_NS}
end_ns = ${END_NS}
elapsed_sec = (end_ns - start_ns) / 1_000_000_000
print(f'{elapsed_sec:.2f}')
")

echo ""
echo "=== BENCHMARK RESULT ==="
echo "Exit code: $EXIT_CODE"
echo "Total time: ${ELAPSED}s"
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"

# Parse test counts from log
TEST_COUNT=$(grep -c "Running " /tmp/benchmark-log.txt 2>/dev/null || echo "0")
TEST_FAILED=$(grep -c "FAILURE" /tmp/benchmark-log.txt 2>/dev/null || echo "0")
echo "Tests run: $TEST_COUNT"
echo "Tests failed: $TEST_FAILED"

exit $EXIT_CODE
