#!/usr/bin/env bash
################################################################################
# turtle-to-yawl.sh — Orchestrates Turtle → YAWL conversion pipeline
#
# Main entry point for converting Turtle RDF workflow specifications to YAWL XML.
# Orchestrates the complete pipeline:
#   1. Validate Turtle RDF specification
#   2. Copy spec to ontology directory
#   3. Run ggen synchronization to generate YAWL
#   4. Validate generated YAWL output
#
# Usage:
#   bash scripts/turtle-to-yawl.sh <spec.ttl>
#   bash scripts/turtle-to-yawl.sh <spec.ttl> --verbose
#   bash scripts/turtle-to-yawl.sh --help
#
# Environment:
#   VERBOSE        Enable verbose output (0/1, default: 0)
#   TURTLE_SPEC    Input Turtle specification file
#   YAWL_OUTPUT    Output YAWL file (default: output/process.yawl)
#
# Exit codes:
#   0 = success (YAWL XML generated and validated)
#   1 = transient error (network/temporary issue, retry may help)
#   2 = fatal error (bad input, validation failure)
#
# Examples:
#   bash scripts/turtle-to-yawl.sh examples/example.ttl
#   bash scripts/turtle-to-yawl.sh specs/order-fulfillment.ttl --verbose
#   VERBOSE=1 bash scripts/turtle-to-yawl.sh my-workflow.ttl
#
################################################################################

set -euo pipefail

# ── Script constants ──────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
VERBOSE="${VERBOSE:-0}"
TURTLE_SPEC=""
YAWL_OUTPUT="${YAWL_OUTPUT:-${REPO_ROOT}/output/process.yawl}"
ONTOLOGY_DIR="${REPO_ROOT}/ontology"
OUTPUT_DIR="${REPO_ROOT}/output"

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

log_section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$*${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

show_help() {
    cat << 'EOF'
turtle-to-yawl.sh — Convert Turtle RDF workflows to YAWL XML

USAGE:
  bash scripts/turtle-to-yawl.sh <spec.ttl> [OPTIONS]

ARGUMENTS:
  <spec.ttl>          Path to input Turtle RDF specification file

OPTIONS:
  --verbose           Enable verbose output during execution
  --output <file>     Output YAWL file (default: output/process.yawl)
  --help              Show this help message

ENVIRONMENT:
  VERBOSE             Set to 1 for verbose output
  YAWL_OUTPUT         Override output file path
  GGEN_TEMPLATE       Override ggen template file
  GGEN_VERBOSE        Enable ggen verbose output

EXAMPLES:
  bash scripts/turtle-to-yawl.sh examples/simple.ttl
  bash scripts/turtle-to-yawl.sh specs/order-fulfillment.ttl --verbose
  bash scripts/turtle-to-yawl.sh workflow.ttl --output my-output.yawl
  VERBOSE=1 bash scripts/turtle-to-yawl.sh workflow.ttl

EXITS:
  0 = success (YAWL generated and validated)
  1 = transient error (retry may help)
  2 = fatal error (check inputs and fix)

PIPELINE STAGES:
  1. Validate input Turtle specification (syntax, structure, semantics)
  2. Copy specification to ontology directory for ggen
  3. Run ggen to generate YAWL from Turtle specification
  4. Validate generated YAWL output (well-formedness, structure)

For detailed validation reports, use --verbose flag.

EOF
}

cleanup() {
    # Clean up any temporary files
    log_debug "Cleaning up temporary files..."
    # Temporary files would be added here if created during execution
}

# Set up cleanup on exit
trap cleanup EXIT

# ── Parse command-line arguments ──────────────────────────────────────────────

parse_args() {
    local arg_count=0

    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help|-h)
                show_help
                exit 0
                ;;
            --verbose)
                VERBOSE=1
                shift
                ;;
            --output)
                YAWL_OUTPUT="$2"
                shift 2
                ;;
            -*)
                log_error "Unknown option: $1"
                show_help
                exit 2
                ;;
            *)
                # Positional argument (Turtle spec file)
                if [[ $arg_count -eq 0 ]]; then
                    TURTLE_SPEC="$1"
                    ((arg_count++))
                    shift
                else
                    log_error "Multiple input files not supported"
                    exit 2
                fi
                ;;
        esac
    done

    # Validate that input file was provided
    if [[ -z "$TURTLE_SPEC" ]]; then
        log_error "No input Turtle specification provided"
        echo ""
        show_help
        exit 2
    fi
}

# ── Pre-flight checks ────────────────────────────────────────────────────────

verify_prerequisites() {
    log_section "STEP 0: Verifying Prerequisites"

    log_info "Checking script dependencies..."

    # Check that required scripts exist
    local required_scripts=(
        "validate-turtle-spec.sh"
        "ggen-sync.sh"
        "validate-yawl-output.sh"
    )

    for script in "${required_scripts[@]}"; do
        if [[ ! -f "${SCRIPT_DIR}/${script}" ]]; then
            log_error "Required script not found: ${script}"
            log_error "Expected at: ${SCRIPT_DIR}/${script}"
            exit 2
        fi
        log_debug "Found: ${script}"
    done

    log_success "All required scripts found"

    # Check input file exists
    if [[ ! -f "$TURTLE_SPEC" ]]; then
        log_error "Input Turtle specification not found: ${TURTLE_SPEC}"
        exit 2
    fi

    log_success "Input file accessible: ${TURTLE_SPEC}"
}

# ── Step 1: Validate Turtle specification ────────────────────────────────────

step_validate_turtle() {
    log_section "STEP 1: Validate Turtle Specification"

    log_info "Input file: ${TURTLE_SPEC}"

    if ! bash "${SCRIPT_DIR}/validate-turtle-spec.sh" "$TURTLE_SPEC"; then
        VALIDATION_EXIT=$?
        log_error "Turtle validation failed with exit code: $VALIDATION_EXIT"
        exit 2
    fi

    log_success "Turtle specification validation passed"
}

# ── Step 2: Copy specification to ontology directory ──────────────────────────

step_copy_to_ontology() {
    log_section "STEP 2: Copy Specification to Ontology Directory"

    # Create ontology directory if it doesn't exist
    if [[ ! -d "$ONTOLOGY_DIR" ]]; then
        log_info "Creating ontology directory: ${ONTOLOGY_DIR}"
        mkdir -p "$ONTOLOGY_DIR"
        log_debug "Created: ${ONTOLOGY_DIR}"
    fi

    # Determine the ontology filename
    # Default to process.ttl but preserve input filename if provided
    local ontology_file="${ONTOLOGY_DIR}/process.ttl"

    log_info "Copying specification..."
    log_debug "Source: ${TURTLE_SPEC}"
    log_debug "Target: ${ontology_file}"

    if ! cp "$TURTLE_SPEC" "$ontology_file"; then
        log_error "Failed to copy specification to ontology directory"
        exit 2
    fi

    log_success "Specification copied to: ${ontology_file}"
}

# ── Step 3: Run ggen synchronization ─────────────────────────────────────────

step_ggen_sync() {
    log_section "STEP 3: Run ggen Synchronization"

    # Pass verbose flag to ggen if enabled
    local ggen_args=()
    if [[ "$VERBOSE" == "1" ]]; then
        ggen_args+=(--verbose)
    fi

    log_info "Generating YAWL from Turtle specification..."
    log_debug "Running: bash ${SCRIPT_DIR}/ggen-sync.sh ${ggen_args[*]}"

    if ! bash "${SCRIPT_DIR}/ggen-sync.sh" "${ggen_args[@]}"; then
        GGEN_EXIT=$?
        log_error "ggen synchronization failed with exit code: $GGEN_EXIT"

        # Determine if this is a transient or fatal error
        if [[ $GGEN_EXIT -eq 1 ]]; then
            log_warn "Transient error (may be retryable)"
            exit 1
        else
            log_error "Fatal error (check ggen configuration and inputs)"
            exit 2
        fi
    fi

    log_success "YAWL generation completed"
}

# ── Step 4: Validate YAWL output ─────────────────────────────────────────────

step_validate_yawl() {
    log_section "STEP 4: Validate YAWL Output"

    # Verify output file exists
    if [[ ! -f "$YAWL_OUTPUT" ]]; then
        log_error "YAWL output file not found: ${YAWL_OUTPUT}"
        exit 2
    fi

    log_info "Validating generated YAWL file..."
    log_debug "File: ${YAWL_OUTPUT}"

    if ! bash "${SCRIPT_DIR}/validate-yawl-output.sh" "$YAWL_OUTPUT"; then
        YAWL_VALIDATION_EXIT=$?
        log_error "YAWL validation failed with exit code: $YAWL_VALIDATION_EXIT"
        exit 2
    fi

    log_success "YAWL output validation passed"
}

# ── Final summary ────────────────────────────────────────────────────────────

print_summary() {
    log_section "✓ Pipeline Complete"

    log_success "Turtle → YAWL conversion successful"
    echo ""
    echo "Summary:"
    echo "  Input:  ${TURTLE_SPEC}"
    echo "  Output: ${YAWL_OUTPUT}"
    echo ""

    # Show output file stats
    if [[ -f "$YAWL_OUTPUT" ]]; then
        local file_size
        file_size=$(stat -f%z "$YAWL_OUTPUT" 2>/dev/null || stat -c%s "$YAWL_OUTPUT" 2>/dev/null || echo "unknown")
        echo "Output file size: ${file_size} bytes"
    fi

    echo ""
    log_info "Next steps:"
    echo "  • Review the generated YAWL at: ${YAWL_OUTPUT}"
    echo "  • Deploy using: bash scripts/deploy.sh ${YAWL_OUTPUT}"
    echo "  • Import into YAWL Editor for further refinement"
}

# ── Main execution pipeline ───────────────────────────────────────────────────

main() {
    log_section "YAWL Turtle-to-YAWL Conversion Pipeline"
    log_info "Start time: $(date)"

    # Parse arguments
    parse_args "$@"

    # Resolve paths (handle relative paths)
    TURTLE_SPEC=$(cd "$(dirname "$TURTLE_SPEC")" && pwd)/$(basename "$TURTLE_SPEC")
    YAWL_OUTPUT=$(cd "$(dirname "$YAWL_OUTPUT")" 2>/dev/null || mkdir -p "$(dirname "$YAWL_OUTPUT")" && pwd)/$(basename "$YAWL_OUTPUT")

    log_debug "Resolved TURTLE_SPEC: ${TURTLE_SPEC}"
    log_debug "Resolved YAWL_OUTPUT: ${YAWL_OUTPUT}"

    # Execute pipeline stages
    verify_prerequisites
    step_validate_turtle
    step_copy_to_ontology
    step_ggen_sync
    step_validate_yawl

    # Print summary
    print_summary

    log_info "End time: $(date)"
    exit 0
}

# Entry point
main "$@"
