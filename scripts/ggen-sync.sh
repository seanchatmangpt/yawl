#!/usr/bin/env bash
# ==========================================================================
# ggen-sync.sh — Synchronize YAWL Workflow Definitions via ggen
#
# Generates YAWL workflow XML from RDF/TTL ontology using ggen (Graph
# Generation CLI) and Tera templates. Processes SPARQL queries and merges
# results into workflow definitions.
#
# Usage:
#   bash scripts/ggen-sync.sh
#   bash scripts/ggen-sync.sh --template templates/workflow.yawl.tera
#   bash scripts/ggen-sync.sh --ontology ontology/custom.ttl
#   bash scripts/ggen-sync.sh --template <file> --ontology <file>
#   bash scripts/ggen-sync.sh --help
#
# Environment:
#   GGEN_TEMPLATE      Path to Tera template (default: templates/workflow.yawl.tera)
#   GGEN_ONTOLOGY      Path to RDF/TTL ontology (default: .specify/yawl-ontology.ttl)
#   GGEN_OUTPUT_DIR    Output directory (default: output/)
#   GGEN_VERBOSE       Enable verbose output (0/1, default: 0)
#
# Exit codes:
#   0 = success (workflow generated and validated)
#   1 = transient error (ggen invocation failed, retry may help)
#   2 = permanent error (bad input, unsupported format)
#
# ==========================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ── Default configuration ─────────────────────────────────────────────────

TEMPLATE_FILE="${GGEN_TEMPLATE:-${REPO_ROOT}/templates/workflow.yawl.tera}"
ONTOLOGY_FILE="${GGEN_ONTOLOGY:-${REPO_ROOT}/.specify/yawl-ontology.ttl}"
OUTPUT_DIR="${GGEN_OUTPUT_DIR:-${REPO_ROOT}/output}"
VERBOSE="${GGEN_VERBOSE:-0}"

# ── Helper functions ──────────────────────────────────────────────────────

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

show_help() {
    cat << 'EOF'
ggen-sync.sh — Generate YAWL workflows from RDF/TTL ontologies

USAGE:
  bash scripts/ggen-sync.sh [OPTIONS]

OPTIONS:
  --template <file>    Path to Tera template (default: templates/workflow.yawl.tera)
  --ontology <file>    Path to RDF/TTL ontology (default: .specify/yawl-ontology.ttl)
  --output <dir>       Output directory (default: output/)
  --verbose            Enable verbose output
  --help               Show this help message

ENVIRONMENT:
  GGEN_TEMPLATE        Override template file path
  GGEN_ONTOLOGY        Override ontology file path
  GGEN_OUTPUT_DIR      Override output directory
  GGEN_VERBOSE         Set to 1 for verbose output

EXAMPLES:
  bash scripts/ggen-sync.sh
  bash scripts/ggen-sync.sh --template templates/workflow.yawl.tera --ontology ontology/process.ttl
  GGEN_VERBOSE=1 bash scripts/ggen-sync.sh

EXITS:
  0 = success
  1 = transient error (retry may help)
  2 = permanent error (check inputs)

EOF
}

# ── Parse arguments ───────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
    case "$1" in
        --template)
            TEMPLATE_FILE="$2"
            shift 2
            ;;
        --ontology)
            ONTOLOGY_FILE="$2"
            shift 2
            ;;
        --output)
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=1
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            exit 2
            ;;
    esac
done

# ── Verify prerequisites ──────────────────────────────────────────────────

log_info "Verifying ggen installation..."

if ! command -v ggen &> /dev/null; then
    log_error "ggen CLI not found. Run: bash scripts/ggen-init.sh"
    exit 2
fi

GGEN_VERSION=$(ggen --version 2>&1)
log_debug "ggen version: ${GGEN_VERSION}"

# ── Validate input files ──────────────────────────────────────────────────

log_info "Validating input files..."

if [[ ! -f "$TEMPLATE_FILE" ]]; then
    log_error "Template file not found: ${TEMPLATE_FILE}"
    exit 2
fi
log_success "Template found: ${TEMPLATE_FILE}"

if [[ ! -f "$ONTOLOGY_FILE" ]]; then
    log_error "Ontology file not found: ${ONTOLOGY_FILE}"
    exit 2
fi
log_success "Ontology found: ${ONTOLOGY_FILE}"

# ── Create output directory ───────────────────────────────────────────────

log_info "Setting up output directory: ${OUTPUT_DIR}"

if ! mkdir -p "${OUTPUT_DIR}"; then
    log_error "Failed to create output directory: ${OUTPUT_DIR}"
    exit 1
fi
log_debug "Output directory ready: ${OUTPUT_DIR}"

# ── Run ggen with template ────────────────────────────────────────────────

log_info "Generating YAWL workflows from ontology..."
log_debug "Template: ${TEMPLATE_FILE}"
log_debug "Ontology: ${ONTOLOGY_FILE}"
log_debug "Output: ${OUTPUT_DIR}"

# Build ggen command
GGEN_CMD=(ggen \
    generate \
    --template "${TEMPLATE_FILE}" \
    --input "${ONTOLOGY_FILE}" \
    --output "${OUTPUT_DIR}/process.yawl"
)

# Add optional flags
if [[ "$VERBOSE" == "1" ]]; then
    GGEN_CMD+=(--verbose)
fi

log_debug "Running: ${GGEN_CMD[*]}"

# Execute ggen and capture output
GGEN_OUTPUT_FILE="/tmp/ggen-output-$$.log"
if ! "${GGEN_CMD[@]}" > "${GGEN_OUTPUT_FILE}" 2>&1; then
    GGEN_EXIT_CODE=$?
    log_error "ggen generation failed with exit code: ${GGEN_EXIT_CODE}"
    log_error "Output:"
    cat "${GGEN_OUTPUT_FILE}" >&2
    rm -f "${GGEN_OUTPUT_FILE}"

    # Determine exit code: 1 for transient (network), 2 for permanent (input)
    if grep -q -i "connection\|timeout\|network" "${GGEN_OUTPUT_FILE}" 2>/dev/null; then
        exit 1
    else
        exit 2
    fi
fi

# Show ggen output
if [[ "$VERBOSE" == "1" ]]; then
    log_debug "ggen output:"
    cat "${GGEN_OUTPUT_FILE}"
fi
rm -f "${GGEN_OUTPUT_FILE}"

log_success "ggen generation completed"

# ── Verify output file ────────────────────────────────────────────────────

OUTPUT_FILE="${OUTPUT_DIR}/process.yawl"

if [[ ! -f "$OUTPUT_FILE" ]]; then
    log_error "Output file not created: ${OUTPUT_FILE}"
    exit 2
fi

OUTPUT_SIZE=$(stat -f%z "${OUTPUT_FILE}" 2>/dev/null || stat -c%s "${OUTPUT_FILE}" 2>/dev/null || echo "0")
if [[ "$OUTPUT_SIZE" -lt 100 ]]; then
    log_warn "Output file suspiciously small: ${OUTPUT_SIZE} bytes"
    log_debug "Output file content:"
    cat "${OUTPUT_FILE}" >&2
fi

log_success "Output file created: ${OUTPUT_FILE} (${OUTPUT_SIZE} bytes)"

# ── Validate XML structure ────────────────────────────────────────────────

log_info "Validating generated YAWL XML..."

# Basic XML well-formedness check
if ! command -v xmllint &> /dev/null; then
    log_warn "xmllint not available, skipping XML validation"
else
    if ! xmllint --noout "${OUTPUT_FILE}" 2>&1 | head -10; then
        log_error "XML validation failed"
        exit 2
    fi
    log_success "XML structure validated"
fi

# ── Summary ───────────────────────────────────────────────────────────────

log_success "Workflow synchronization complete"
log_info "Output: ${OUTPUT_FILE}"
log_info "To deploy, use: bash scripts/deploy.sh ${OUTPUT_FILE}"

exit 0
