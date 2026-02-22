#!/usr/bin/env bash
################################################################################
# generate-yawl.sh — Generates YAWL XML from Turtle using ggen-wrapper
#
# Executes SPARQL extraction queries on Turtle spec and renders YAWL template.
#
# Usage:
#   bash scripts/generate-yawl.sh <input.ttl> <output.yawl> [--verbose]
#
# Exit codes:
#   0 = success
#   1 = transient error
#   2 = fatal error
################################################################################

set -euo pipefail

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERBOSE="${VERBOSE:-0}"

# Arguments
INPUT_TTL="${1:?Input Turtle file required}"
OUTPUT_YAWL="${2:?Output YAWL file required}"

# Parse flags
while [[ $# -gt 2 ]]; do
    case "$3" in
        --verbose) VERBOSE=1; shift ;;
        *) echo "Unknown option: $3"; exit 2 ;;
    esac
done

# Logging
log_info() { echo "[INFO] $*"; }
log_error() { echo "[ERROR] $*" >&2; }
log_debug() { [[ "$VERBOSE" == "1" ]] && echo "[DEBUG] $*"; }

# Verify input
if [[ ! -f "$INPUT_TTL" ]]; then
    log_error "Input file not found: $INPUT_TTL"
    exit 2
fi

log_info "Generating YAWL from Turtle specification"
log_debug "Input: $INPUT_TTL"
log_debug "Output: $OUTPUT_YAWL"

# Create output directory
mkdir -p "$(dirname "$OUTPUT_YAWL")"

# Use ggen-wrapper.py if available
if command -v python3 &>/dev/null; then
    log_info "Using ggen-wrapper.py for generation..."
    TEMPLATE="${REPO_ROOT}/templates/yawl-xml/workflow.yawl.tera"
    
    if python3 "${SCRIPT_DIR}/ggen-wrapper.py" generate \
        --template "$TEMPLATE" \
        --input "$INPUT_TTL" \
        --output "$OUTPUT_YAWL" \
        $([ "$VERBOSE" == "1" ] && echo "--verbose" || echo ""); then
        log_info "YAWL generation successful"
        exit 0
    else
        log_error "ggen-wrapper.py failed"
        exit 2
    fi
else
    log_error "python3 not available"
    exit 2
fi
