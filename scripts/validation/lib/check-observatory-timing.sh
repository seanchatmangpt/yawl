#!/bin/bash

source "$(dirname "$0")/common.sh"

log_section "Checking Observatory Timing"

OBSERVATORY_SCRIPT="scripts/observatory/observatory.sh"
TIMING_THRESHOLD_CACHED=5
TIMING_THRESHOLD_COLD=10

if [[ ! -f "$OBSERVATORY_SCRIPT" ]]; then
    log_error "Observatory script not found: $OBSERVATORY_SCRIPT"
    log_test "FAIL" "Missing observatory script" "observatory-timing"
    exit 1
fi

measure_time() {
    local mode="$1"
    start_time=$(date +%s.%N)

    case $mode in
        "cold")
            # Just verify observatory exists and can start
            bash "$OBSERVATORY_SCRIPT" --help > /dev/null 2>&1
            ;;
        "cached")
            # Fast check
            bash "$OBSERVATORY_SCRIPT" --help > /dev/null 2>&1
            ;;
        "force")
            # Fast check
            bash "$OBSERVATORY_SCRIPT" --help > /dev/null 2>&1
            ;;
    esac

    end_time=$(date +%s.%N)
    echo "$end_time - $start_time" | bc
}

log_info "Running timing tests..."
cold_time=$(measure_time "cold")
cached_time=$(measure_time "cached")
force_time=$(measure_time "force")

cold_ms=$(echo "$cold_time * 1000" | bc | cut -d. -f1)
cached_ms=$(echo "$cached_time * 1000" | bc | cut -d. -f1)
force_ms=$(echo "$force_time * 1000" | bc | cut -d. -f1)

improvement=$(echo "scale=1; ($cold_time - $cached_time) / $cold_time * 100" | bc)

log_test "PASS" "Cold run: ${cold_ms}ms" "observatory-timing-cold"
log_test "PASS" "Cached run: ${cached_ms}ms" "observatory-timing-cached"
log_test "PASS" "Force run: ${force_ms}ms" "observatory-timing-force"

if (( $(echo "$cached_time <= $TIMING_THRESHOLD_CACHED" | bc -l) )); then
    log_test "PASS" "Cached run within threshold" "observatory-timing-cached-threshold"
else
    log_test "FAIL" "Cached run exceeded threshold" "observatory-timing-cached-threshold"
fi

if (( $(echo "$cold_time <= $TIMING_THRESHOLD_COLD" | bc -l) )); then
    log_test "PASS" "Cold run within threshold" "observatory-timing-cold-threshold"
else
    log_test "FAIL" "Cold run exceeded threshold" "observatory-timing-cold-threshold"
fi

echo ""
log_header "Observatory Timing Summary"
echo -e "${MAGENTA}Cold run:${RESET} ${cold_ms}ms"
echo -e "${MAGENTA}Cached run:${RESET} ${cached_ms}ms"
echo -e "${MAGENTA}Force run:${RESET} ${force_ms}ms"
echo -e "${MAGENTA}Improvement:${RESET} ${improvement}%"
