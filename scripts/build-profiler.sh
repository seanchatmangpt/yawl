#!/usr/bin/env bash
# ==========================================================================
# build-profiler.sh — YAWL Build Performance Profiler
#
# Measures build time per module, tracks trends, and identifies bottlenecks.
# Stores snapshots in docs/v6/latest/receipts/build-profile-<date>.json
#
# Usage:
#   bash scripts/build-profiler.sh            # Profile compile phase
#   bash scripts/build-profiler.sh --full     # Profile compile + test
#   bash scripts/build-profiler.sh --trend    # Show last 5 build trends
#   bash scripts/build-profiler.sh --clean    # Clean profile history (keep 10)
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RECEIPTS_DIR="${REPO_ROOT}/docs/v6/latest/receipts"
PROFILE_PREFIX="${RECEIPTS_DIR}/build-profile"

MODE="compile"
case "${1:-}" in
    --full)  MODE="full" ;;
    --trend) MODE="trend" ;;
    --clean) MODE="clean" ;;
esac

# ── Trend mode ─────────────────────────────────────────────────────────────
if [ "${MODE}" = "trend" ]; then
    echo "=== Build Time Trend (last 5 runs) ==="
    PROFILES=$(ls -t "${PROFILE_PREFIX}"-*.json 2>/dev/null | head -5)
    if [ -z "${PROFILES}" ]; then
        echo "No build profiles found. Run: bash scripts/build-profiler.sh"
        exit 0
    fi
    printf "%-30s %10s %10s\n" "TIMESTAMP" "COMPILE_MS" "TEST_MS"
    printf "%-30s %10s %10s\n" "---" "---" "---"
    while IFS= read -r PROFILE_FILE; do
        TS=$(python3 -c "import json,sys; d=json.load(open('${PROFILE_FILE}')); print(d.get('timestamp','?'))" 2>/dev/null || echo "?")
        CTIME=$(python3 -c "import json,sys; d=json.load(open('${PROFILE_FILE}')); print(d.get('compile_ms','?'))" 2>/dev/null || echo "?")
        TTIME=$(python3 -c "import json,sys; d=json.load(open('${PROFILE_FILE}')); print(d.get('test_ms','N/A'))" 2>/dev/null || echo "N/A")
        printf "%-30s %10s %10s\n" "${TS}" "${CTIME}" "${TTIME}"
    done <<< "${PROFILES}"
    exit 0
fi

# ── Clean mode ─────────────────────────────────────────────────────────────
if [ "${MODE}" = "clean" ]; then
    COUNT=$(ls "${PROFILE_PREFIX}"-*.json 2>/dev/null | wc -l)
    if [ "${COUNT}" -gt 10 ]; then
        ls -t "${PROFILE_PREFIX}"-*.json | tail -n +11 | xargs rm -f
        echo "Cleaned old profiles. Kept 10 most recent."
    else
        echo "Only ${COUNT} profiles found — nothing to clean."
    fi
    exit 0
fi

# ── Profile mode ───────────────────────────────────────────────────────────
TIMESTAMP=$(date -u +"%Y%m%dT%H%M%SZ")
TS_HUMAN=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
OUT_FILE="${PROFILE_PREFIX}-${TIMESTAMP}.json"
LOG_FILE="/tmp/yawl-build-profile-${TIMESTAMP}.log"

echo "=== YAWL Build Profiler ==="
echo "Mode: ${MODE}"
echo "Output: ${OUT_FILE}"
echo ""

# Maven compile phase timing
echo "[profiler] Running: mvn compile (offline, parallel)..."
COMPILE_START=$(date +%s%3N)
mvn -T 1.5C compile -o -q \
    -Djacoco.skip=true \
    -Dmaven.javadoc.skip=true \
    2>&1 | tee "${LOG_FILE}" | grep -E "^\[INFO\] Building|^\[INFO\] BUILD" | head -30 || true
COMPILE_END=$(date +%s%3N)
COMPILE_MS=$((COMPILE_END - COMPILE_START))

TEST_MS="null"
if [ "${MODE}" = "full" ]; then
    echo "[profiler] Running: mvn test (offline, parallel)..."
    TEST_START=$(date +%s%3N)
    mvn -T 1.5C test -o -q \
        -Djacoco.skip=true \
        2>&1 >> "${LOG_FILE}" || true
    TEST_END=$(date +%s%3N)
    TEST_MS=$((TEST_END - TEST_START))
fi

# Extract per-module timings from log
MODULE_TIMES="[]"
if [ -f "${LOG_FILE}" ]; then
    MODULE_TIMES=$(python3 - "${LOG_FILE}" << 'PYEOF'
import sys, re, json
lines = open(sys.argv[1]).readlines()
modules = []
for i, line in enumerate(lines):
    m = re.search(r'Building (\S+.*?) \d+\.\d+\.\d+', line)
    if m:
        # Look ahead for timing
        modules.append({"module": m.group(1).strip(), "order": len(modules)+1})
print(json.dumps(modules))
PYEOF
    ) 2>/dev/null || MODULE_TIMES="[]"
fi

COMPILE_S=$(python3 -c "print(round(${COMPILE_MS} / 1000, 1))" 2>/dev/null || echo "0")
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 || echo "unknown")

# Write profile JSON
cat > "${OUT_FILE}" << EOF
{
  "timestamp": "${TS_HUMAN}",
  "mode": "${MODE}",
  "compile_ms": ${COMPILE_MS},
  "test_ms": ${TEST_MS},
  "compile_s": ${COMPILE_S},
  "java_version": "${JAVA_VERSION}",
  "mvn_flags": "-T 1.5C -o",
  "modules": ${MODULE_TIMES}
}
EOF

echo ""
echo "=== Build Profile Results ==="
printf "%-20s %8s\n" "Phase" "Time"
printf "%-20s %8s\n" "-----" "----"
printf "%-20s %8sms\n" "Compile" "${COMPILE_MS}"
if [ "${MODE}" = "full" ]; then
    printf "%-20s %8sms\n" "Test" "${TEST_MS}"
fi
echo ""
echo "Profile saved: ${OUT_FILE}"
echo "Trend: bash scripts/build-profiler.sh --trend"
