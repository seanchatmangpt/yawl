#!/usr/bin/env bash
# ==========================================================================
# test-cds-performance.sh — Measure CDS Performance Impact
#
# Tests the startup time reduction provided by Class Data Sharing archives.
# Compares compilation times with and without CDS.
#
# Usage:
#   bash scripts/test-cds-performance.sh              # Full test
#   bash scripts/test-cds-performance.sh --quick      # Quick test (3 iterations)
#   bash scripts/test-cds-performance.sh --clean      # Clean + test
#
# Output:
#   - Test results with timing metrics
#   - Performance improvement percentage
#   - Summary to stdout and .yawl/performance/cds-test.json
#
# Exit codes:
#   0 = Tests passed with measurable improvement
#   1 = Tests passed but no improvement or inconclusive
#   2 = Tests failed
# ==========================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
CDS_DIR="${REPO_ROOT}/.yawl/cds"
PERF_DIR="${REPO_ROOT}/.yawl/performance"
RESULTS_FILE="${PERF_DIR}/cds-test.json"

# Test configuration
ITERATIONS="${1:-5}"
QUICK_MODE=0

if [[ "${2:-}" == "--quick" ]]; then
    QUICK_MODE=1
    ITERATIONS=3
fi

CLEAN_MODE=0
if [[ "${2:-}" == "--clean" || "${3:-}" == "--clean" ]]; then
    CLEAN_MODE=1
fi

# Color codes
readonly RED='\033[91m'
readonly GREEN='\033[92m'
readonly YELLOW='\033[93m'
readonly BLUE='\033[94m'
readonly CYAN='\033[96m'
readonly RESET='\033[0m'

# Helper functions
log() {
    echo -e "${CYAN}cds-perf${RESET}: $1" >&2
}

log_info() {
    echo -e "${CYAN}cds-perf${RESET}: ${GREEN}[INFO]${RESET} $1" >&2
}

log_warn() {
    echo -e "${CYAN}cds-perf${RESET}: ${YELLOW}[WARN]${RESET} $1" >&2
}

log_error() {
    echo -e "${CYAN}cds-perf${RESET}: ${RED}[ERROR]${RESET} $1" >&2
}

mkdir -p "${PERF_DIR}"

# ── Clean CDS archives if requested ────────────────────────────────────────
if [[ $CLEAN_MODE -eq 1 ]]; then
    log "Cleaning CDS archives..."
    bash "${SCRIPT_DIR}/generate-cds-archives.sh" clean
    log "CDS archives cleaned"
fi

# ── Test 1: Compile without CDS ───────────────────────────────────────────
log "Test 1: Compile WITHOUT CDS (${ITERATIONS} iterations)"
log "  Building with DX_CDS_GENERATE=0..."

# Make sure no CDS archives exist
bash "${SCRIPT_DIR}/generate-cds-archives.sh" clean 2>/dev/null || true

declare -a WITHOUT_CDS_TIMES=()

for i in $(seq 1 "$ITERATIONS"); do
    # Clean compile
    cd "${REPO_ROOT}"

    # Time the compile (compile only to exclude test time variance)
    START_NS=$(date +%s%N)
    DX_CDS_GENERATE=0 bash "${SCRIPT_DIR}/dx.sh" compile -pl yawl-elements 2>&1 | grep -q "SUCCESS" || {
        log_error "Compilation failed (iteration $i)"
        exit 2
    }
    END_NS=$(date +%s%N)

    ELAPSED_MS=$(((END_NS - START_NS) / 1000000))
    WITHOUT_CDS_TIMES+=("$ELAPSED_MS")

    log "  Iteration $i: ${ELAPSED_MS}ms"
done

# Calculate average for without-CDS
AVG_WITHOUT_CDS=0
for time in "${WITHOUT_CDS_TIMES[@]}"; do
    AVG_WITHOUT_CDS=$((AVG_WITHOUT_CDS + time))
done
AVG_WITHOUT_CDS=$((AVG_WITHOUT_CDS / ITERATIONS))

log_info "Average WITHOUT CDS: ${AVG_WITHOUT_CDS}ms"

# ── Test 2: Compile with CDS ──────────────────────────────────────────────
log "Test 2: Compile WITH CDS (${ITERATIONS} iterations)"
log "  Generating CDS archives..."

# Generate CDS archives
bash "${SCRIPT_DIR}/generate-cds-archives.sh" generate 2>/dev/null || {
    log_warn "CDS generation failed or not available, using fallback"
}

# Check if archives were created
if bash "${SCRIPT_DIR}/cds-helper.sh" should-use 2>/dev/null; then
    log "  CDS archives available"

    declare -a WITH_CDS_TIMES=()

    for i in $(seq 1 "$ITERATIONS"); do
        cd "${REPO_ROOT}"

        # Time the compile
        START_NS=$(date +%s%N)
        bash "${SCRIPT_DIR}/dx.sh" compile -pl yawl-elements 2>&1 | grep -q "SUCCESS" || {
            log_error "Compilation failed (iteration $i)"
            exit 2
        }
        END_NS=$(date +%s%N)

        ELAPSED_MS=$(((END_NS - START_NS) / 1000000))
        WITH_CDS_TIMES+=("$ELAPSED_MS")

        log "  Iteration $i: ${ELAPSED_MS}ms"
    done

    # Calculate average for with-CDS
    AVG_WITH_CDS=0
    for time in "${WITH_CDS_TIMES[@]}"; do
        AVG_WITH_CDS=$((AVG_WITH_CDS + time))
    done
    AVG_WITH_CDS=$((AVG_WITH_CDS / ITERATIONS))

    log_info "Average WITH CDS: ${AVG_WITH_CDS}ms"

    # Calculate improvement
    IMPROVEMENT_MS=$((AVG_WITHOUT_CDS - AVG_WITH_CDS))
    IMPROVEMENT_PCT=$(awk "BEGIN {printf \"%.1f\", ($IMPROVEMENT_MS / $AVG_WITHOUT_CDS) * 100}")

    # Determine if improvement is meaningful (>5% is significant)
    if (( $(echo "$IMPROVEMENT_PCT > 5" | bc -l) )); then
        log_info "Performance improvement: ${GREEN}${IMPROVEMENT_PCT}%${RESET} (${IMPROVEMENT_MS}ms faster)"
        RESULT="improved"
        EXIT_CODE=0
    elif (( $(echo "$IMPROVEMENT_PCT > 0" | bc -l) )); then
        log_warn "Performance improvement: ${YELLOW}${IMPROVEMENT_PCT}%${RESET} (marginal)"
        RESULT="marginal"
        EXIT_CODE=1
    else
        log_warn "Performance degradation: ${YELLOW}${IMPROVEMENT_PCT}%${RESET} (${IMPROVEMENT_MS}ms slower)"
        RESULT="degraded"
        EXIT_CODE=1
    fi

else
    log_warn "CDS archives not available or failed to generate"
    RESULT="no_cds"
    AVG_WITH_CDS=0
    IMPROVEMENT_PCT=0
    EXIT_CODE=2
fi

# ── Write results to JSON ──────────────────────────────────────────────────
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
JAVA_VERSION=$(java -version 2>&1 | grep 'version "' | cut -d'"' -f2)

RESULTS=$(jq -n \
    --arg timestamp "$TIMESTAMP" \
    --arg java_version "$JAVA_VERSION" \
    --arg test_mode "$([ $QUICK_MODE -eq 1 ] && echo 'quick' || echo 'full')" \
    --argjson iterations "$ITERATIONS" \
    --argjson avg_without_cds "$AVG_WITHOUT_CDS" \
    --argjson avg_with_cds "$AVG_WITH_CDS" \
    --arg improvement_pct "$IMPROVEMENT_PCT" \
    --arg result "$RESULT" \
    '{
        timestamp: $timestamp,
        java_version: $java_version,
        test_mode: $test_mode,
        iterations: $iterations,
        measurements: {
            avg_without_cds_ms: $avg_without_cds,
            avg_with_cds_ms: $avg_with_cds,
            improvement_pct: $improvement_pct
        },
        result: $result
    }')

echo "${RESULTS}" | jq '.' > "${RESULTS_FILE}"

# ── Print summary ──────────────────────────────────────────────────────────
echo ""
log "Test Complete"
log ""
log "Summary:"
log "  Iterations: ${ITERATIONS}"
log "  Without CDS: ${AVG_WITHOUT_CDS}ms (avg)"
log "  With CDS: ${AVG_WITH_CDS}ms (avg)"
log "  Improvement: ${IMPROVEMENT_PCT}%"
log "  Result: ${RESULT}"
log ""
log "Results saved to: ${RESULTS_FILE}"
log ""

exit "$EXIT_CODE"
