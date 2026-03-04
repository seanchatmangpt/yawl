#!/bin/bash
#
# Observatory: Codebase Instrument & Fact Generator
# Generates JSON facts, Mermaid diagrams, and provenance receipts
#
# Usage:
#   bash scripts/observatory/observatory.sh              # Full run (facts + diagrams + receipt)
#   bash scripts/observatory/observatory.sh --facts      # Facts only
#   bash scripts/observatory/observatory.sh --diagrams   # Diagrams only
#

set -euo pipefail

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Output directories
OUTPUT_BASE="${ROOT_DIR}/docs/v6/latest"
FACTS_DIR="${OUTPUT_BASE}/facts"
DIAGRAMS_DIR="${OUTPUT_BASE}/diagrams"
RECEIPTS_DIR="${OUTPUT_BASE}/receipts"

# Ensure output directories exist
mkdir -p "$FACTS_DIR" "$DIAGRAMS_DIR" "$RECEIPTS_DIR"

# Logging (must be defined before dependency validation uses them)
LOG_LEVEL="${LOG_LEVEL:-INFO}"

log_info() {
    echo "[INFO] $*" >&2
}

log_debug() {
    if [[ "$LOG_LEVEL" == "DEBUG" ]]; then
        echo "[DEBUG] $*" >&2
    fi
}

log_error() {
    echo "[ERROR] $*" >&2
}

# ── Dependency validation (fail fast) ───────────────────────────────────────
DEPENDENCIES=("python3" "jq")
MISSING_DEPS=()

for dep in "${DEPENDENCIES[@]}"; do
    if ! command -v "$dep" &>/dev/null; then
        MISSING_DEPS+=("$dep")
    fi
done

# Special check for sha256sum with macOS fallback
if ! command -v sha256sum &>/dev/null; then
    if command -v shasum &>/dev/null; then
        # Create a function wrapper for macOS
        sha256sum() { shasum -a 256 "$@"; }
        export -f sha256sum
    else
        MISSING_DEPS+=("sha256sum")
    fi
fi

if [[ ${#MISSING_DEPS[@]} -gt 0 ]]; then
    log_error "Missing required dependencies: ${MISSING_DEPS[*]}"
    log_error "Install with:"
    for dep in "${MISSING_DEPS[@]}"; do
        case "$dep" in
            python3) log_error "  apt install python3 || brew install python3" ;;
            jq)      log_error "  apt install jq || brew install jq" ;;
            sha256sum) log_error "  apt install coreutils || brew install coreutils" ;;
        esac
    done
    exit 1
fi

# Source library files
source "${SCRIPT_DIR}/lib/emit-facts.sh"
source "${SCRIPT_DIR}/lib/emit-diagrams.sh"
source "${SCRIPT_DIR}/lib/emit-receipt.sh"
source "${SCRIPT_DIR}/lib/utils.sh"

# Parse arguments
RUN_FACTS=true
RUN_DIAGRAMS=true
RUN_RECEIPT=true

case "${1:-}" in
    --facts)
        RUN_DIAGRAMS=false
        RUN_RECEIPT=false
        ;;
    --diagrams)
        RUN_FACTS=false
        RUN_RECEIPT=false
        ;;
    --receipt)
        RUN_FACTS=false
        RUN_DIAGRAMS=false
        ;;
    *)
        if [[ -n "${1:-}" ]]; then
            log_error "Unknown option: $1"
            exit 1
        fi
        ;;
esac

# Start timer
START_TIME=$(date +%s%N)
TOTAL_DURATION=0  # Initialize for receipt phase reference

# Initialize receipt data
RECEIPT_DATA="{\"inputs\": {}, \"outputs\": {}, \"phases\": {}}"

# ==============================================================================
# FACTS PHASE
# ==============================================================================
if [[ "$RUN_FACTS" == "true" ]]; then
    log_info "Generating facts phase..."
    FACTS_START=$(date +%s%N)

    # Record POM hash for input provenance
    ROOT_POM_HASH=$(sha256sum "${ROOT_DIR}/pom.xml" | cut -d' ' -f1)

    # Run fact emitters
    run_facts "$ROOT_DIR" "$FACTS_DIR"

    FACTS_END=$(date +%s%N)
    FACTS_DURATION=$(( (FACTS_END - FACTS_START) / 1000000 ))
    log_info "Facts phase complete (${FACTS_DURATION}ms)"
fi

# ==============================================================================
# DIAGRAMS PHASE
# ==============================================================================
if [[ "$RUN_DIAGRAMS" == "true" ]]; then
    log_info "Generating diagrams phase..."
    DIAGRAMS_START=$(date +%s%N)

    run_diagrams "$ROOT_DIR" "$FACTS_DIR" "$DIAGRAMS_DIR"

    DIAGRAMS_END=$(date +%s%N)
    DIAGRAMS_DURATION=$(( (DIAGRAMS_END - DIAGRAMS_START) / 1000000 ))
    log_info "Diagrams phase complete (${DIAGRAMS_DURATION}ms)"
fi

# ==============================================================================
# RECEIPT PHASE
# ==============================================================================
if [[ "$RUN_RECEIPT" == "true" ]]; then
    log_info "Generating receipt..."
    RECEIPT_START=$(date +%s%N)

    # Calculate total duration so far (receipt generation time will be added by emit-receipt.sh)
    END_TIME=$(date +%s%N)
    TOTAL_DURATION=$(( (END_TIME - START_TIME) / 1000000 ))

    # Export timing values for emit-receipt.sh to include in JSON
    export FACTS_DURATION_MS="${FACTS_DURATION:-0}"
    export DIAGRAMS_DURATION_MS="${DIAGRAMS_DURATION:-0}"
    export TOTAL_DURATION_MS="${TOTAL_DURATION}"

    generate_receipt "$ROOT_DIR" "$FACTS_DIR" "$DIAGRAMS_DIR" "$RECEIPTS_DIR"

    RECEIPT_END=$(date +%s%N)
    RECEIPT_DURATION=$(( (RECEIPT_END - RECEIPT_START) / 1000000 ))
    log_info "Receipt phase complete (${RECEIPT_DURATION}ms)"
fi

# ==============================================================================
# COMPLETION
# ==============================================================================
log_info "Observatory run complete"
log_info "Output directory: ${OUTPUT_BASE}"
log_info "Total duration: ${TOTAL_DURATION}ms"
