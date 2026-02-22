#!/usr/bin/env bash
###############################################################################
# emit-rdf-facts.sh — Convert observatory facts.json to RDF/Turtle
#
# Orchestrates the complete Ψ (Observatory) → RDF pipeline:
#   1. Validate facts/*.json files exist
#   2. Run Java FactsToRDFConverter to convert facts → facts.ttl
#   3. Compute SHA256 hash for drift detection
#   4. Update hash file for next run
#   5. Emit facts.ttl + hash to .claude/receipts/
#
# Design:
#   - Input: facts/ (from observatory.sh --facts)
#   - Output: facts.ttl (RDF graph), facts.ttl.sha256 (hash for drift detection)
#   - Drift detection: on next run, compare current hash vs saved hash
#   - If different: trigger rebuild (Λ→H→Q→Ω circuit)
#
# Usage:
#   bash scripts/observatory/emit-rdf-facts.sh
#   bash scripts/observatory/emit-rdf-facts.sh --verbose
#   FACTS_DIR=docs/v6/latest/facts bash scripts/observatory/emit-rdf-facts.sh
#
# Exit codes:
#   0 = success
#   1 = validation error (missing facts files)
#   2 = conversion error (Java process failed)
#   3 = hash computation error
#
# Environment variables:
#   FACTS_DIR        Input facts directory (default: docs/v6/latest/facts)
#   OUTPUT_DIR       Output directory (default: docs/v6/latest)
#   HASH_DIR         Hash file directory (default: .claude/receipts)
#   VERBOSE          Enable verbose output (0/1)
#   SKIP_HASH        Skip hash computation (for testing)
#
###############################################################################

set -euo pipefail

# ── Script constants ──────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Default values
FACTS_DIR="${FACTS_DIR:-${REPO_ROOT}/docs/v6/latest/facts}"
OUTPUT_DIR="${OUTPUT_DIR:-${REPO_ROOT}/docs/v6/latest}"
HASH_DIR="${HASH_DIR:-${REPO_ROOT}/.claude/receipts}"
VERBOSE="${VERBOSE:-0}"
SKIP_HASH="${SKIP_HASH:-0}"

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ── Helper functions ──────────────────────────────────────────────────────────

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

log_debug() {
    if [[ "$VERBOSE" == "1" ]]; then
        echo -e "${BLUE}[DEBUG]${NC} $*"
    fi
}

# ── Validation ────────────────────────────────────────────────────────────────

validate_facts_directory() {
    log_info "Validating facts directory: $FACTS_DIR"

    if [[ ! -d "$FACTS_DIR" ]]; then
        log_error "Facts directory not found: $FACTS_DIR"
        return 1
    fi

    # Check for required fact files
    local required_files=(
        "modules.json"
        "reactor.json"
        "coverage.json"
        "tests.json"
        "gates.json"
        "shared-src.json"
        "integration-facts.json"
    )

    for file in "${required_files[@]}"; do
        if [[ ! -f "$FACTS_DIR/$file" ]]; then
            log_warn "Optional fact file missing: $file (continuing)"
        fi
    done

    log_success "Facts directory validated"
    return 0
}

# ── RDF Conversion ────────────────────────────────────────────────────────────

convert_facts_to_rdf() {
    log_info "Converting facts.json to RDF/Turtle"

    # Create output directory if needed
    mkdir -p "$OUTPUT_DIR"

    local facts_ttl="$OUTPUT_DIR/facts.ttl"
    local java_class="org.yawlfoundation.yawl.observatory.rdf.FactsToRDFConverter"

    # Build Java command
    local cmd=(
        "java"
        "-cp" "${REPO_ROOT}/target/yawl-engine.jar:${REPO_ROOT}/target/lib/*"
        "$java_class"
        "$FACTS_DIR"
        "$facts_ttl"
    )

    log_debug "Running: ${cmd[*]}"

    if ! "${cmd[@]}" 2>&1; then
        log_error "RDF conversion failed"
        return 2
    fi

    if [[ ! -f "$facts_ttl" ]]; then
        log_error "RDF output file not created: $facts_ttl"
        return 2
    fi

    local size=$(wc -c < "$facts_ttl")
    log_success "RDF conversion complete"
    log_info "  Output: $facts_ttl ($size bytes)"

    return 0
}

# ── Hash Computation ──────────────────────────────────────────────────────────

compute_hash() {
    log_info "Computing SHA256 hash for drift detection"

    if [[ "$SKIP_HASH" == "1" ]]; then
        log_warn "Skipping hash computation (SKIP_HASH=1)"
        return 0
    fi

    local facts_ttl="$OUTPUT_DIR/facts.ttl"

    if [[ ! -f "$facts_ttl" ]]; then
        log_error "RDF file not found: $facts_ttl"
        return 3
    fi

    # Compute hash (compatible with sha256sum)
    local hash=$(sha256sum "$facts_ttl" | awk '{print $1}')

    if [[ -z "$hash" ]]; then
        log_error "Failed to compute hash"
        return 3
    fi

    # Create hash directory
    mkdir -p "$HASH_DIR"
    local hash_file="$HASH_DIR/observatory-facts.sha256"

    # Write hash file in sha256sum format: "hash  filename"
    echo "$hash  facts.ttl" > "$hash_file"

    log_success "Hash computed and saved"
    log_info "  Hash: $hash"
    log_info "  File: $hash_file"

    return 0
}

# ── Drift Detection ───────────────────────────────────────────────────────────

check_drift() {
    log_info "Checking for codebase drift"

    if [[ "$SKIP_HASH" == "1" ]]; then
        log_warn "Skipping drift check (SKIP_HASH=1)"
        return 0
    fi

    local facts_ttl="$OUTPUT_DIR/facts.ttl"
    local hash_file="$HASH_DIR/observatory-facts.sha256"

    if [[ ! -f "$facts_ttl" ]]; then
        log_error "RDF file not found: $facts_ttl"
        return 3
    fi

    if [[ ! -f "$hash_file" ]]; then
        log_info "No previous hash found (first run or cache cleared)"
        return 0
    fi

    # Read previous hash
    local previous_hash=$(awk '{print $1}' "$hash_file" 2>/dev/null || true)

    if [[ -z "$previous_hash" ]]; then
        log_warn "Hash file is empty or unreadable"
        return 0
    fi

    # Compute current hash
    local current_hash=$(sha256sum "$facts_ttl" | awk '{print $1}')

    if [[ "$current_hash" == "$previous_hash" ]]; then
        log_success "NO DRIFT: Codebase facts unchanged"
        return 0
    else
        log_warn "DRIFT DETECTED: Codebase facts have changed"
        log_info "  Previous hash: $previous_hash"
        log_info "  Current hash:  $current_hash"
        log_info "  Action: Will trigger full rebuild (Λ→H→Q→Ω)"
        return 0
    fi
}

# ── Main ──────────────────────────────────────────────────────────────────────

main() {
    echo ""
    echo "=========================================================================="
    echo "  Observatory Facts → RDF Conversion (Ψ Phase)"
    echo "=========================================================================="
    echo ""

    # Validate input
    if ! validate_facts_directory; then
        return 1
    fi

    # Convert facts to RDF
    if ! convert_facts_to_rdf; then
        return 2
    fi

    # Compute hash for drift detection
    if ! compute_hash; then
        return 3
    fi

    # Check for drift
    if ! check_drift; then
        return 3
    fi

    echo ""
    echo "=========================================================================="
    log_success "Ψ (Observatory) phase complete"
    echo "=========================================================================="
    echo ""
    return 0
}

# ── Entry Point ───────────────────────────────────────────────────────────────

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
