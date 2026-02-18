#!/usr/bin/env bash
# ==========================================================================
# check-staleness.sh — Verify observatory output freshness
#
# Compares SHA-256 hashes in receipts against current file hashes.
# If hashes differ, outputs are stale and need regeneration.
#
# Usage:
#   ./scripts/observatory/check-staleness.sh
#
# Exit codes:
#   0 = FRESH (all hashes match)
#   1 = STALE (hashes differ)
#   2 = ERROR (missing files)
# ==========================================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="${SCRIPT_DIR}/lib"

source "${LIB_DIR}/util.sh"

RECEIPT_FILE="docs/v6/latest/receipts/observatory.json"
INDEX_FILE="docs/v6/latest/INDEX.md"

# Check if receipt exists
if [[ ! -f "$RECEIPT_FILE" ]]; then
    log_error "Receipt not found: $RECEIPT_FILE"
    exit 2
fi

# Check if INDEX.md exists
if [[ ! -f "$INDEX_FILE" ]]; then
    log_error "INDEX.md not found: $INDEX_FILE"
    exit 2
fi

log_info "Checking observatory staleness..."

# Get hashes from receipt
INDEX_SHA256=$(jq -r '.outputs.index_sha256' "$RECEIPT_FILE")
ROOT_POM_SHA256=$(jq -r '.inputs.root_pom_sha256' "$RECEIPT_FILE")

# Calculate current hashes
CURRENT_INDEX_SHA256=$(sha256sum "$INDEX_FILE" | cut -d' ' -f1)
CURRENT_ROOT_POM_SHA256=$(sha256sum pom.xml | cut -d' ' -f1)

# Check freshness
FRESH=true

# Strip sha256: prefix for INDEX comparison
RECEIPT_INDEX_HASH="${INDEX_SHA256#sha256:}"
if [[ "$RECEIPT_INDEX_HASH" != "$CURRENT_INDEX_SHA256" ]]; then
    log_warn "INDEX.md is stale (receipt: $INDEX_SHA256, current: $CURRENT_INDEX_SHA256)"
    FRESH=false
fi

if [[ "$ROOT_POM_SHA256" != "null" && "$ROOT_POM_SHA256" != "$CURRENT_ROOT_POM_SHA256" ]]; then
    # Strip sha256: prefix for comparison
    RECEIPT_POM_HASH="${ROOT_POM_SHA256#sha256:}"
    if [[ "$RECEIPT_POM_HASH" != "$CURRENT_ROOT_POM_SHA256" ]]; then
        log_warn "pom.xml is stale (receipt: $ROOT_POM_SHA256, current: $CURRENT_ROOT_POM_SHA256)"
        FRESH=false
    fi
fi

# Check fact files
FACTS_DIR="docs/v6/latest/facts"
for fact_file in "$FACTS_DIR"/*.json; do
    if [[ -f "$fact_file" ]]; then
        filename=$(basename "$fact_file")
        expected_sha=$(jq -r --arg filename "$filename" '.outputs.facts_sha256[$filename]' "$RECEIPT_FILE")
        current_sha=$(sha256sum "$fact_file" | cut -d' ' -f1)

        if [[ "$expected_sha" != "null" && "$expected_sha" != "null" ]]; then
            # Strip sha256: prefix for comparison
            receipt_hash="${expected_sha#sha256:}"
            if [[ "$receipt_hash" != "$current_sha" ]]; then
                log_warn "$filename is stale (receipt: $expected_sha, current: $current_sha)"
                FRESH=false
            fi
        fi
    fi
done

# Check diagrams
DIAGRAMS_DIR="docs/v6/latest/diagrams"
for diagram_file in "$DIAGRAMS_DIR"/*.mmd; do
    if [[ -f "$diagram_file" ]]; then
        filename=$(basename "$diagram_file")
        expected_sha=$(jq -r --arg filename "$filename" '.outputs.diagrams_sha256[$filename]' "$RECEIPT_FILE")
        current_sha=$(sha256sum "$diagram_file" | cut -d' ' -f1)

        if [[ "$expected_sha" != "null" && "$expected_sha" != "null" ]]; then
            # Strip sha256: prefix for comparison
            receipt_hash="${expected_sha#sha256:}"
            if [[ "$receipt_hash" != "$current_sha" ]]; then
                log_warn "$filename is stale (receipt: $expected_sha, current: $current_sha)"
                FRESH=false
            fi
        fi
    fi
done

if [[ "$FRESH" == true ]]; then
    log_info "STATUS=FRESH - All outputs are up to date"
    exit 0
else
    log_info "STATUS=STALE - Some outputs need regeneration"
    exit 1
fi