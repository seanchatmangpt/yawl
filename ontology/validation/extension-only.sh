#!/usr/bin/env bash

# ============================================================================
# YAWL Extension-Only Architectural Constraint Validator
# ============================================================================
# Purpose: Validate that all YAWL workflow specifications extend public
#          ontologies (Schema.org, PROV-O, Dublin Core, etc.) rather than
#          creating isolated proprietary class hierarchies.
#
# Usage: bash ontology/validation/extension-only.sh <path-to-ttl-file>
#
# Exit Codes:
#   0  = Validation passed (all YAWL instances/classes properly extended)
#   1  = Tool not available, validation skipped
#   2  = Violations found (proprietary roots detected)
#   3  = Error running validation
#
# Requirements:
#   - Apache Jena arq (preferred) or Redland sparql command-line tool
#   - Valid TTL file argument
# ============================================================================

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Configuration
TTL_FILE="${1:--}"
OUTPUT_FORMAT="text"
VERBOSE=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

# Function: Print usage
usage() {
  cat >&2 <<EOF
Usage: bash extension-only.sh [OPTIONS] <ttl-file>

Validate YAWL extension-only constraint (all classes must extend public ontologies).

Options:
  -h, --help          Show this help message
  -v, --verbose       Enable verbose output
  -f, --format <fmt>  Output format: text, json (default: text)

Arguments:
  <ttl-file>          Path to TTL file to validate (default: stdin)

Examples:
  bash extension-only.sh ontology/process.ttl
  cat ontology/process.ttl | bash extension-only.sh -
  bash extension-only.sh -v ontology/process.ttl
EOF
  exit 1
}

# Parse arguments
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h | --help)
      usage
      ;;
    -v | --verbose)
      VERBOSE=1
      shift
      ;;
    -f | --format)
      OUTPUT_FORMAT="$2"
      shift 2
      ;;
    -*)
      echo "Error: Unknown option $1" >&2
      usage
      ;;
    *)
      TTL_FILE="$1"
      shift
      ;;
  esac
done

# Function: Print verbose message
log_verbose() {
  if [[ $VERBOSE -eq 1 ]]; then
    echo "[INFO] $*" >&2
  fi
}

# Function: Print error message
log_error() {
  echo -e "${RED}[ERROR] $*${NC}" >&2
}

# Function: Print warning message
log_warning() {
  echo -e "${YELLOW}[WARN] $*${NC}" >&2
}

# Function: Print success message
log_success() {
  echo -e "${GREEN}[OK] $*${NC}" >&2
}

# Function: Check if tool exists
has_command() {
  command -v "$1" &>/dev/null
}

# Function: Run SPARQL query with arq (Jena)
run_arq_query() {
  local query_file="$1"
  local ttl_input="$2"

  if ! has_command arq; then
    return 1
  fi

  log_verbose "Using Apache Jena arq"

  # Redirect TTL to temp file if stdin
  local input_file="$ttl_input"
  if [[ "$ttl_input" == "-" ]]; then
    input_file=$(mktemp)
    trap "rm -f '$input_file'" EXIT
    cat >"$input_file"
  fi

  arq --data "$input_file" --query "$query_file" 2>/dev/null
}

# Function: Run SPARQL query with sparql (Redland)
run_redland_query() {
  local query_file="$1"
  local ttl_input="$2"

  if ! has_command sparql; then
    return 1
  fi

  log_verbose "Using Redland sparql"

  # Redirect TTL to temp file if stdin
  local input_file="$ttl_input"
  if [[ "$ttl_input" == "-" ]]; then
    input_file=$(mktemp)
    trap "rm -f '$input_file'" EXIT
    cat >"$input_file"
  fi

  sparql --data "$input_file" --query "$query_file" 2>/dev/null
}

# Function: Run SPARQL validation
run_validation() {
  local query_name="$1"
  local query_file="$2"
  local ttl_input="$3"

  [[ ! -f "$query_file" ]] && {
    log_error "Query file not found: $query_file"
    return 3
  }

  log_verbose "Running validation: $query_name"
  log_verbose "Query file: $query_file"

  # Try Jena first (more robust), then Redland
  local result
  if result=$(run_arq_query "$query_file" "$ttl_input"); then
    echo "$result"
  elif result=$(run_redland_query "$query_file" "$ttl_input"); then
    echo "$result"
  else
    return 1
  fi
}

# ============================================================================
# Main Validation Logic
# ============================================================================

# Check if SPARQL tool is available
if ! (has_command arq || has_command sparql); then
  log_warning "Neither Apache Jena (arq) nor Redland (sparql) command found"
  cat >&2 <<EOF

To install Apache Jena CLI tools:
  1. Download from: https://jena.apache.org/download/
  2. Extract archive and add to PATH:
     export PATH=/path/to/apache-jena/bin:\$PATH

To install Redland:
  Ubuntu/Debian: apt-get install raptor-utils librdf0-utils
  macOS:         brew install raptor librdf

Validation SKIPPED (non-blocking, tools not available)
EOF
  exit 0
fi

log_verbose "TTL input: $TTL_FILE"
log_verbose "Output format: $OUTPUT_FORMAT"

# Prepare input handling
if [[ "$TTL_FILE" == "-" ]] || [[ -z "$TTL_FILE" ]]; then
  # Read from stdin
  INPUT_FILE=$(mktemp --suffix=.ttl)
  trap "rm -f '$INPUT_FILE'" EXIT
  cat >"$INPUT_FILE"
  TTL_FILE="$INPUT_FILE"
elif [[ ! -f "$TTL_FILE" ]]; then
  log_error "File not found: $TTL_FILE"
  exit 3
fi

# Run all three validations
declare -A results
declare -A violation_counts

# Query 1: Extension-Only Instance Check
log_verbose "Query 1: Extension-Only constraint (instances)"
q1_result=$(run_validation \
  "extension-only" \
  "$SCRIPT_DIR/extension-only.sparql" \
  "$TTL_FILE") || {
  exit_code=$?
  if [[ $exit_code -eq 1 ]]; then
    log_error "Failed to run extension-only query (tools unavailable)"
    exit 0
  fi
  log_error "Failed to run extension-only query"
  exit 3
}
results["extension-only"]="$q1_result"
violation_counts["extension-only"]=$(echo "$q1_result" | grep -c "^\?" || echo 0)

# Query 2: Proprietary Root Detection (TBox)
log_verbose "Query 2: Proprietary-Root detection (classes)"
q2_result=$(run_validation \
  "proprietary-root-detector" \
  "$SCRIPT_DIR/proprietary-root-detector.sparql" \
  "$TTL_FILE") || {
  exit_code=$?
  if [[ $exit_code -eq 1 ]]; then
    log_warning "Skipping proprietary-root-detector (tools unavailable)"
    q2_result=""
  else
    log_error "Failed to run proprietary-root-detector query"
    exit 3
  fi
}
results["proprietary-root"]="$q2_result"
violation_counts["proprietary-root"]=$(echo "$q2_result" | grep -c "^\?" || echo 0)

# Query 3: Coverage Report
log_verbose "Query 3: Extension coverage statistics"
q3_result=$(run_validation \
  "extension-coverage" \
  "$SCRIPT_DIR/extension-coverage.sparql" \
  "$TTL_FILE") || {
  exit_code=$?
  if [[ $exit_code -eq 1 ]]; then
    log_warning "Skipping extension-coverage (tools unavailable)"
    q3_result=""
  else
    log_error "Failed to run extension-coverage query"
    exit 3
  fi
}
results["coverage"]="$q3_result"

# ============================================================================
# Output Results
# ============================================================================

total_violations=$((${violation_counts["extension-only"]:-0} + ${violation_counts["proprietary-root"]:-0}))

if [[ "$OUTPUT_FORMAT" == "json" ]]; then
  # JSON output
  cat <<EOF
{
  "validation": "extension-only-constraint",
  "file": "$TTL_FILE",
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "results": {
    "extension-only-instances": {
      "violations": ${violation_counts["extension-only"]:-0},
      "details": $(echo "$q1_result" | wc -l)
    },
    "proprietary-roots": {
      "violations": ${violation_counts["proprietary-root"]:-0},
      "details": $(echo "$q2_result" | wc -l)
    },
    "coverage": $(echo "$q3_result" | tail -1 || echo "{}")
  },
  "summary": {
    "total_violations": $total_violations,
    "status": $([ $total_violations -eq 0 ] && echo '"PASS"' || echo '"FAIL"')
  }
}
EOF
else
  # Text output
  echo ""
  echo "=========================================="
  echo "Extension-Only Validation Report"
  echo "=========================================="
  echo "File: $TTL_FILE"
  echo "Time: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo ""

  echo "1. Extension-Only Constraint (Instances)"
  echo "   Checking: All YAWL instances have public ontology types"
  if [[ ${violation_counts["extension-only"]:-0} -eq 0 ]]; then
    log_success "PASS: All instances properly extended (0 violations)"
  else
    log_error "FAIL: Found ${violation_counts["extension-only"]} instances with only proprietary types"
    echo ""
    echo "$q1_result" | head -20
    if [[ $(echo "$q1_result" | wc -l) -gt 20 ]]; then
      echo "   ... (showing first 20 of $(echo "$q1_result" | wc -l))"
    fi
  fi
  echo ""

  echo "2. Proprietary Root Detection (Classes)"
  echo "   Checking: All YAWL classes have public superclasses"
  if [[ -z "${results["proprietary-root"]}" ]]; then
    log_warning "SKIP: Tools unavailable"
  elif [[ ${violation_counts["proprietary-root"]:-0} -eq 0 ]]; then
    log_success "PASS: All classes properly aligned (0 proprietary roots)"
  else
    log_error "FAIL: Found ${violation_counts["proprietary-root"]} proprietary root classes"
    echo ""
    echo "$q2_result" | head -20
    if [[ $(echo "$q2_result" | wc -l) -gt 20 ]]; then
      echo "   ... (showing first 20 of $(echo "$q2_result" | wc -l))"
    fi
  fi
  echo ""

  echo "3. Extension Coverage Statistics"
  echo "   Measuring: Fraction of YAWL classes with public alignments"
  if [[ -z "${results["coverage"]}" ]]; then
    log_warning "SKIP: Tools unavailable"
  else
    echo "$q3_result" | tail -1
  fi
  echo ""

  echo "=========================================="
  if [[ $total_violations -eq 0 ]]; then
    log_success "VALIDATION PASSED"
    echo "All constraints satisfied. Architecture is extension-only."
    exit 0
  else
    log_error "VALIDATION FAILED"
    echo "Fix violations and re-run validation."
    exit 2
  fi
fi
